(ns tab.impl.http
  (:require [tab.impl.ring :as ring]
            [tab.impl.html :refer [$] :as html]
            [tab.impl.log :as log]
            [tab.impl.template :as template]
            [tab.impl.thread :as thread])
  (:import (java.io BufferedOutputStream BufferedReader InputStreamReader)
           (java.net InetAddress ServerSocket SocketException)
           (java.nio.charset StandardCharsets)
           (java.util.concurrent BlockingQueue Executors ExecutorService TimeUnit)
           (java.util UUID)))

(set! *warn-on-reflection* true)

(defprotocol HttpServer
  (address [this] "Given a HttpServer, return the address it listens on.")
  (broadcast [this event] "Send a server-sent event (SSE) to all connected clients.")
  (halt [this] "Halt a HttpServer."))

(defn ^:private make-request-thread-pool
  "Return a thread pool for handling HTTP requests."
  []
  (try
    (eval '(Executors/newVirtualThreadPerTaskExecutor))
    (catch Exception _
      (let [thread-pool-size (-> (Runtime/getRuntime) .availableProcessors inc)]
        (Executors/newFixedThreadPool thread-pool-size (thread/make-factory :name-suffix :request))))))

(defn serve
  "Start a HTTP server.

  Options:

    :port
      The HTTP server port to listen on.

    :sse-heartbeat-initial-delay-secs
      The time to wait (in seconds) before sending a client the first SSE heartbeat message.

    :sse-heartbeat-frequency-secs
      The frequency (in seconds) in which to send clients SSE heartbeat messages."
  [handler
   {:keys [port sse-heartbeat-initial-delay-secs sse-heartbeat-frequency-secs]
    :or {port 0
         sse-heartbeat-initial-delay-secs 10
         sse-heartbeat-frequency-secs 10}}]
  (let [server-id (UUID/randomUUID)
        ^ServerSocket socket (ServerSocket. port 0 (InetAddress/getLoopbackAddress))
        accept-loop-thread-pool (Executors/newSingleThreadExecutor (thread/make-factory :name-suffix :accept-loop))
        ^ExecutorService request-thread-pool (make-request-thread-pool)
        heartbeat-thread-pool (Executors/newScheduledThreadPool 1 (thread/make-factory :name-suffix :heartbeat :ex-log-level :fine))
        queue-thread-pool (Executors/newCachedThreadPool (thread/make-factory :name-suffix :queue))

        !queues (atom #{})
        count-queues #(count @!queues)

        address
        (let [^java.net.InetSocketAddress address (.getLocalSocketAddress socket)]
          (format "http://%s:%s" (.getHostString address) (.getLocalPort socket)))

        accept-connection
        (fn []
          (let [client (.accept socket)
                remote-addr (str (.getRemoteSocketAddress client))]
            (log/log :fine {:event :accept-client :remote-addr remote-addr})

            (thread/exec request-thread-pool
              (let [reader (-> client .getInputStream (InputStreamReader. StandardCharsets/UTF_8) BufferedReader.)

                    lines (not-empty
                            (doall
                              (take-while (fn [^String line] (and line (not (.isBlank line))))
                                (repeatedly #(.readLine reader)))))

                    request (ring/parse-request lines)

                    output-stream (-> client .getOutputStream BufferedOutputStream.)

                    close (fn []
                            (try
                              (.close client)
                              (log/log :fine {:event :socket-close-ok :remote-addr remote-addr})
                              (catch SocketException _
                                (log/log :fine {:event :socket-close-failed :remote-addr remote-addr}))))

                    response
                    (try
                      (handler (assoc request :remote-addr remote-addr :server-id server-id))
                      (catch Throwable ex
                        (log/log :severe {:event :write-response-failed :ex ex})
                        {:status 500
                         :headers {"Content-Type" "text/html"}
                         :body (html/page
                                 (template/error-page request
                                   ($ :h1 "I messed up.")
                                   ($ :p "Sorry. "
                                     ($ :a {:href "https://github.com/eerohele/tab/issues"} "File an issue?"))))}))]

                (try
                  (ring/write-response output-stream response)

                  (if (= (get-in response [:headers "Content-Type"]) "text/event-stream")
                    (let [event-queue (:body response)
                          _ (swap! !queues conj event-queue)
                          evict-queue! (fn [] (swap! !queues disj event-queue))]
                      (log/log :fine
                        {:event :accept-queue
                         :remote-addr remote-addr
                         :connected-clients (count-queues)})

                      (.scheduleAtFixedRate heartbeat-thread-pool
                        (fn []
                          (try
                            (log/log :fine
                              {:event :send-heartbeat
                               :remote-addr remote-addr
                               :connected-clients (count-queues)})
                            (.write output-stream (.getBytes ":\n\n" StandardCharsets/UTF_8))
                            (.flush output-stream)
                            (catch SocketException ex
                              (evict-queue!)
                              (log/log :fine
                                {:event :send-heartbeat-failed
                                 :remote-addr remote-addr
                                 :connected-clients (count-queues)})
                              ;; rethrow to cancel scheduled task
                              (throw ex))))
                        sse-heartbeat-initial-delay-secs
                        sse-heartbeat-frequency-secs
                        TimeUnit/SECONDS)

                      (thread/exec queue-thread-pool
                        (try
                          (loop []
                            (let [^String item (.take ^BlockingQueue event-queue)]
                              (when-not (identical? item ::quit)
                                (.write output-stream (.getBytes item StandardCharsets/UTF_8))
                                (.flush output-stream)
                                (recur))))
                          (catch SocketException _
                            (log/log :fine
                              {:event :evict-queue-write-failed
                               :remote-addr remote-addr
                               :connected-clients (count-queues)}))
                          (catch InterruptedException _
                            (log/log :fine
                              {:event :evict-queue-interrupted
                               :remote-addr remote-addr
                               :connected-clients (count-queues)}))
                          (finally
                            (evict-queue!)
                            (close)))))
                    (close))
                  (catch SocketException ex
                    (log/log :fine {:event :write-response-failed :ex ex})))))))]

    (thread/exec accept-loop-thread-pool
      (try
        (loop []
          (accept-connection)
          (recur))
        (catch SocketException ex
          (log/log :fine {:event :server-halted :ex ex})
          :halted)))

    (reify HttpServer
      (address [_] address)

      (broadcast [_ event]
        (run! (fn [^BlockingQueue queue] (.put queue event)) @!queues))

      (halt [_]
        ;; Put poison pill into event queue loop
        (doseq [^BlockingQueue queue @!queues] (.put queue ::quit))

        (.shutdown request-thread-pool)
        (.shutdown heartbeat-thread-pool)
        (.shutdownNow queue-thread-pool)
        (.shutdown accept-loop-thread-pool)
        (.close socket)))))
