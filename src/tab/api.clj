(ns tab.api
  "Tab is a tool for visualizing Clojure data structures."
  (:require [clojure.java.browse :as browse]
            [tab.annotate :as annotate]
            [tab.base64 :as base64]
            [tab.tabulator :as tabulator]
            [tab.ring :as ring]
            [tab.handler :as handler]
            [tab.html :refer [$] :as html]
            [tab.log :as log])
  (:import (java.io BufferedReader BufferedWriter InputStreamReader OutputStreamWriter)
           (java.net InetAddress ServerSocket SocketException)
           (java.nio.charset StandardCharsets)
           (java.time LocalDateTime)
           (java.util.concurrent BlockingQueue Executors ArrayBlockingQueue ThreadFactory TimeUnit)
           (java.util.concurrent.atomic AtomicInteger)))

(set! *warn-on-reflection* true)

(defn ^:private make-thread-factory
  [& {:keys [name-suffix daemon?] :or {daemon? true}}]
  (let [no (AtomicInteger. 0)]
    (reify ThreadFactory
      (newThread [_ runnable]
        (doto (Thread. runnable (format "tab-pool-%s-%d" (name name-suffix) (.incrementAndGet no)))
          (.setUncaughtExceptionHandler
            (reify Thread$UncaughtExceptionHandler
              (uncaughtException [_ thread ex]
                (log/log :severe {:thread (.getName thread) :ex ex}))))
          (.setDaemon daemon?))))))

(def ^:private convey-bindings @#'clojure.core/binding-conveyor-fn)

(defmacro ^:private exec
  "Given a ExecutorService thread pool and a body of forms, .execute the body
  (with binding conveyance) in the thread pool."
  [thread-pool & body]
  `(.execute ~thread-pool (convey-bindings (fn [] ~@body))))

(defprotocol HttpServer
  (tab> [this event] "Given a Tab and a value, send the value to the Tab.")
  (address [this] "Given a Tab, return the address it listens on.")
  (halt [this] "Halt a Tab."))

(def ^:private heartbeat-initial-delay 10)
(def ^:private heartbeat-frequency 10)

(defn run
  "Run a Tab.

  Options:

    :port (default: 0, to auto-assign an available port)
      The HTTP server port to listen on.

    :init-val
      The initial value to show in Tab.

    :max-vals (default: 16)
      The maximum number of values to retain in history.

    :add-tap? (default: true)
      Whether to wire up tap> to send vals to Tab.

    :browse? (default: true)
      Whether to automatically open your default browser to show Tab.

    :print-length (default: *print-length*)
      Maximum number of items of a seq to show. To show all items, click on the
      ellipsis in the UI.

    :print-level (default: *print-level*)
      Tab shows every nested object whose nesting level exceeds this
      value as collapsed.

      To expand a collapsed object, click on the plus sign."
  [& {:keys [port init-val max-vals add-tap? browse? print-length print-level]
      :or {port 0
           init-val '(tap> :hello-world)
           max-vals 16
           add-tap? true
           browse? true}}]
  (let [print-length (or print-length *print-length*)
        print-level (or print-level *print-level*)
        server-id (random-uuid)
        ^ServerSocket socket (ServerSocket. port 0 (InetAddress/getLoopbackAddress))
        request-thread-pool (Executors/newFixedThreadPool 4 (make-thread-factory :name-suffix :request))
        queue-thread-pool (Executors/newCachedThreadPool (make-thread-factory :name-suffix :queue))

        !queues (atom #{})
        !vals (atom [{:inst (LocalDateTime/now) :data init-val}])
        !watches (atom [])

        push-val
        (fn [v]
          (swap! !vals
            (fn [vals v]
              (if (>= (count vals) max-vals)
                (conj (subvec vals 1) v)
                (conj vals v)))
            v))

        send-event
        (fn send [x]
          (binding [*print-length* print-length
                    *print-level* print-level]
            (run!
              (fn [^BlockingQueue queue]
                (let [val {:inst (LocalDateTime/now) :data x}
                      data (->
                             val
                             (assoc :max-offset (count (push-val val)))
                             tabulator/tabulate
                             html/html
                             base64/encode)]
                  (.put queue (format "data: %s\n\n" data))))
              @!queues))

          (when (instance? clojure.lang.IRef x)
            (add-watch x :tab (fn [_ _ _ n] (send n)))
            (swap! !watches conj x)))

        _ (when add-tap? (doto send-event add-tap))

        address
        (let [^java.net.InetSocketAddress address (.getLocalSocketAddress socket)]
          (format "http://%s:%s" (.getHostString address) (.getLocalPort socket)))

        accept-loop
        (future
          (try
            (loop []
              (let [client (.accept socket)
                    remote-addr (str (.getRemoteSocketAddress client))]
                (log/log :fine {:event :accept-client :remote-addr remote-addr :no-event-queues (count @!queues)})

                (exec request-thread-pool
                  (let [reader (-> client .getInputStream (InputStreamReader. StandardCharsets/UTF_8) BufferedReader.)

                        lines (not-empty
                                (doall
                                  (take-while (fn [^String line] (and line (not (.isBlank line))))
                                    (repeatedly #(.readLine reader)))))

                        request (ring/parse-request lines)

                        writer (-> client .getOutputStream (OutputStreamWriter. StandardCharsets/UTF_8) BufferedWriter.)

                        finish (fn []
                                 (try
                                   (.close writer)
                                   (.close reader)
                                   (.close client)
                                   (catch SocketException _
                                     (log/log :fine {:event :socket-close-failed :remote-addr remote-addr}))))

                        response
                        (try
                          (binding [*print-length* print-length
                                    *print-level* print-level
                                    tabulator/*ann* (memoize annotate/annotate)]
                            (handler/handle
                              (assoc request :server-id server-id :vals @!vals)))
                          (catch Throwable ex
                            (log/log :severe {:event :write-response-failed :ex ex})
                            {:status 500
                             :headers {"Content-Type" "text/html"}
                             :body (html/page
                                     ($ :p "I messed up. Sorry. "
                                       ($ :a {:href "https://github.com/eerohele/tab/issues"} "File an issue?")))}))]

                    (ring/write-response writer response)

                    (if (= "text/event-stream" (get-in response [:headers "Content-Type"]))
                      (let [event-queue (ArrayBlockingQueue. 1024)]
                        (swap! !queues conj event-queue)

                        (let [eject-queue! (fn [] (swap! !queues disj event-queue))
                              heartbeat (Executors/newScheduledThreadPool 1 (make-thread-factory :name-suffix :heartbeat))]
                          (.scheduleAtFixedRate heartbeat
                            (fn []
                              (try
                                (log/log :fine {:event :send-heartbeat :remote-addr remote-addr})
                                (.write writer ":\n\n")
                                (.flush writer)
                                (catch SocketException _
                                  (log/log :fine {:event :heartbeat-failed :remote-addr remote-addr})
                                  (eject-queue!)
                                  (.shutdown heartbeat))))
                            heartbeat-initial-delay heartbeat-frequency TimeUnit/SECONDS)

                          (exec queue-thread-pool
                            (try
                              (loop []
                                (let [^String item (.take ^BlockingQueue event-queue)]
                                  (when-not (identical? item ::quit)
                                    (.write writer item)
                                    (.flush writer)
                                    (recur))))
                              (catch SocketException _
                                (log/log :fine {:event :eject-queue :remote-addr remote-addr}))
                              (finally
                                (eject-queue!)
                                (finish))))))
                      (finish)))))
              (recur))
            (catch SocketException ex
              (log/log :fine {:event :client-disappeared? :ex ex})
              :halted)))]

    (when browse? (browse/browse-url address))

    (reify HttpServer
      (tab> [_ event] (send-event event))
      (address [_] address)
      (halt [_]
        ;; Remove any assigned watches
        (run! (fn [x] (remove-watch x :tab)) @!watches)

        ;; Put poison pill into event queue loop
        (doseq [^BlockingQueue queue @!queues] (.put queue ::quit))

        (.shutdown request-thread-pool)
        (.shutdown queue-thread-pool)
        (future-cancel accept-loop)
        (remove-tap send-event)
        (.close socket)))))

(comment
  (def tab (run :port 8080))
  (tab> tab {:foo :bar})
  (tap> {:baz :quux})
  (halt tab)

  (def tab (run :port 8080 :add-tap? false))
  (tab> tab {:foo :bar})
  (tap> {:baz :quux})
  (halt tab)
  ,,,)
