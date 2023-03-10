(ns tab.http
  (:require [tab.ring :as ring]
            [tab.html :refer [$] :as html]
            [tab.log :as log]
            [tab.template :as template]
            [tab.thread :as thread])
  (:import (java.io BufferedOutputStream BufferedReader InputStreamReader)
           (java.net InetAddress ServerSocket SocketException)
           (java.nio.charset StandardCharsets)
           (java.util.concurrent BlockingQueue Executors TimeUnit)
           (java.util UUID)))

(set! *warn-on-reflection* true)

(defprotocol HttpServer
  (address [this] "Given a HttpServer, return the address it listens on.")
  (broadcast [this event] "Send a server-sent event (SSE) to all connected clients.")
  (halt [this] "Halt a HttpServer."))

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
        request-thread-pool (Executors/newFixedThreadPool 4 (thread/make-factory :name-suffix :request))
        heartbeat-thread-pool (Executors/newScheduledThreadPool 1 (thread/make-factory :name-suffix :heartbeat :ex-log-level :fine))
        queue-thread-pool (Executors/newCachedThreadPool (thread/make-factory :name-suffix :queue))

        !queues (atom #{})
        count-queues #(count @!queues)

        address
        (let [^java.net.InetSocketAddress address (.getLocalSocketAddress socket)]
          (format "http://%s:%s" (.getHostString address) (.getLocalPort socket)))

        accept-loop
        (future
          (try
            (loop []
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

                        finish (fn []
                                 (try
                                   (.close output-stream)
                                   (.close reader)
                                   (.close client)
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
                          (log/log :fine {:event :accept-queue :remote-addr remote-addr :connected-clients (count-queues)})

                          (.scheduleAtFixedRate heartbeat-thread-pool
                            (fn []
                              (try
                                (log/log :fine {:event :send-heartbeat :remote-addr remote-addr :connected-clients (count-queues)})
                                (.write output-stream (.getBytes ":\n\n" StandardCharsets/UTF_8))
                                (.flush output-stream)
                                (catch SocketException ex
                                  (evict-queue!)
                                  (log/log :fine {:event :evict-queue/send-heartbeat-failed :remote-addr remote-addr :connected-clients (count-queues)})
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
                                (log/log :fine {:event :evict-queue/queue-write-failed :remote-addr remote-addr :connected-clients (count-queues)}))
                              (finally
                                (evict-queue!)
                                (finish)))))
                        (finish))
                      (catch SocketException ex
                        (log/log :fine {:event :write-response-failed :ex ex}))))))
              (recur))
            (catch SocketException ex
              (log/log :fine {:event :client-disappeared? :ex ex})
              :halted)))]

    (reify HttpServer
      (address [_] address)

      (broadcast [_ event]
        (run! (fn [^BlockingQueue queue] (.put queue event)) @!queues))

      (halt [_]
        ;; Put poison pill into event queue loop
        (doseq [^BlockingQueue queue @!queues] (.put queue ::quit))

        (.shutdown request-thread-pool)
        (.shutdown heartbeat-thread-pool)
        (.shutdown queue-thread-pool)
        (future-cancel accept-loop)
        (.close socket)))))
