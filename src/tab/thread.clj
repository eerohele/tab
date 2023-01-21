(ns tab.thread
  (:require [tab.log :as log])
  (:import (java.util.concurrent ThreadFactory)
           (java.util.concurrent.atomic AtomicInteger)))

(def convey-bindings @#'clojure.core/binding-conveyor-fn)

(defmacro exec
  "Given a ExecutorService thread pool and a body of forms, .execute the body
  (with binding conveyance) in the thread pool."
  [thread-pool & body]
  `(.execute ~thread-pool (convey-bindings (fn [] ~@body))))

(defn logging-exception-handler
  [level]
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread ex]
      (log/log level {:thread (.getName thread) :ex ex}))))

(defn make-factory
  [& {:keys [name-suffix daemon? ex-log-level] :or {daemon? true ex-log-level :severe}}]
  (let [no (AtomicInteger. 0)]
    (reify ThreadFactory
      (newThread [_ runnable]
        (doto (Thread. runnable (format "tab-pool-%s-%d" (name name-suffix) (.incrementAndGet no)))
          (.setUncaughtExceptionHandler (logging-exception-handler ex-log-level))
          (.setDaemon daemon?))))))
