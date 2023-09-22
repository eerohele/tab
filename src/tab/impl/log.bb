(ns tab.impl.log
  "Log things."
  (:require [clojure.tools.logging :as log]
            [taoensso.timbre :as timbre]))

(def ^:private levels
  {:finest :trace
   :fine :debug
   :info :info
   :warning :warn
   :severe :error})

(defmacro log
  ([message]
   `(log :finest ~message))
  ([level message]
   `(log/log (levels ~level) ~message)))

(defn set-level!
  [level]
  (timbre/set-level! (levels level)))

(set-level! :info)

(comment
  (log {:hello :world})
  (log :info {:hello :world})
  (log :severe {:hello :world})
  ,,,)
