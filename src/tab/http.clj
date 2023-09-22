(ns tab.http
  (:require [tab.ring :as ring]
            [tab.html :refer [$] :as html]
            [tab.log :as log]
            [tab.template :as template]
            [tab.thread :as thread])
  (:import (java.io BufferedOutputStream BufferedReader InputStreamReader)
           (java.net InetAddress ServerSocket SocketException)
           (java.nio.charset StandardCharsets)
           (java.util.concurrent Executors TimeUnit)
           (java.util UUID)))

(set! *warn-on-reflection* true)

(defprotocol HttpServer
  (address [this] "Given a HttpServer, return the address it listens on.")
  (sse-clients [this] "A set of server-sent event (SSE) clients connected to this server.")
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
        accept-loop-thread-pool (Executors/newSingleThreadExecutor (thread/make-factory :name-suffix :accept-loop))
        thread-pool-size (-> (Runtime/getRuntime) .availableProcessors inc)
        request-thread-pool (Executors/newFixedThreadPool thread-pool-size (thread/make-factory :name-suffix :request))
        heartbeat-thread-pool (Executors/newScheduledThreadPool 1 (thread/make-factory :name-suffix :heartbeat :ex-log-level :fine))
        !sse-clients (atom #{})
        count-sse-clients #(count @!sse-clients)

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
                    (let [sse-client {:socket client :remote-addr remote-addr :output-stream output-stream}
                          evict-client! (fn [] (swap! !sse-clients disj sse-client))]
                      (swap! !sse-clients conj sse-client)

                      (log/log :fine
                        {:event :establish-sse-connection
                         :remote-addr remote-addr
                         :connected-clients (count-sse-clients)})

                      (.scheduleAtFixedRate heartbeat-thread-pool
                        (fn []
                          (try
                            (log/log :fine
                              {:event :send-sse-heartbeat
                               :remote-addr remote-addr
                               :connected-clients (count-sse-clients)})
                            (.write output-stream (.getBytes ":\n\n" StandardCharsets/UTF_8))
                            (.flush output-stream)
                            (catch SocketException ex
                              (evict-client!)
                              (log/log :fine
                                {:event :send-sse-heartbeat-failed
                                 :remote-addr remote-addr
                                 :connected-clients (count-sse-clients)})
                              (close)
                              ;; rethrow to cancel scheduled task
                              (throw ex))))
                        sse-heartbeat-initial-delay-secs
                        sse-heartbeat-frequency-secs
                        TimeUnit/SECONDS))
                    (close))
                  (catch SocketException ex
                    (log/log :fine {:event :sse-heartbeat-write-response-failed :ex ex})))))))]

    (thread/exec accept-loop-thread-pool
      (try
        (loop []
          (accept-connection)
          (recur))
        (catch SocketException ex
          (log/log :fine {:event :client-disappeared? :ex ex})
          :halted)))

    (reify HttpServer
      (address [_] address)

      (sse-clients [_]
        (deref !sse-clients))

      (halt [_]
        (.shutdown request-thread-pool)
        (.shutdown heartbeat-thread-pool)
        (.shutdown accept-loop-thread-pool)
        (.close socket)))))
