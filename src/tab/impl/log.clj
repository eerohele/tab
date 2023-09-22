(ns tab.impl.log
  "Log things."
  (:require [clojure.edn :as edn]
            [clojure.set :as set])
  (:import (java.util Date)
           (java.util.logging ConsoleHandler Formatter Handler Logger Level LogRecord)))

(set! *warn-on-reflection* true)

(def ^Level keyword->level
  {:finest Level/FINEST
   :finer Level/FINER
   :fine Level/FINE
   :info Level/INFO
   :warning Level/WARNING
   :severe Level/SEVERE})

(def level->keyword
  (set/map-invert keyword->level))

(def ^Handler handler
  (doto (ConsoleHandler.)
    (.setFormatter
      (proxy [Formatter] []
        (format [^LogRecord record]
          (let [message (edn/read-string {:default tagged-literal} (.getMessage record))]
            (str
              (pr-str
                (assoc message
                  :inst (-> record .getMillis Date.)
                  :level (-> record .getLevel level->keyword)))
              \newline)))))))

(def ^Logger logger
  (let [this (Logger/getLogger "me.flowthing/tab")]
    (run! (fn [handler] (.removeHandler this handler)) (.getHandlers this))
    (.addHandler this handler)
    (.setUseParentHandlers this false)
    this))

(defmacro log
  ([message]
   (log :finest message))
  ([level message]
   (let [{:keys [line column]} (meta &form)]
     `(binding [*print-length* nil
                *print-level* nil]
        (.log logger (keyword->level ~level)
          (pr-str {:ns (ns-name *ns*)
                   :line ~line
                   :column ~column
                   :message ~message}))))))

(defn set-level!
  [level]
  (.setLevel logger (keyword->level level))
  (.setLevel handler (keyword->level level))
  level)

(comment
  (log :info {:foo :bar})
  (log :severe {:foo :bar})
  (log :fine {:foo :bar})

  (log :severe (Throwable.))

  (set-level! :fine)
  (set-level! :info)
  (set-level! :severe)
  ,,,)
