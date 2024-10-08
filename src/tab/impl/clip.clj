(ns tab.impl.clip
  (:require [clojure.string :as string]
            [tab.impl.pprint :as pprint])
  (:import (java.awt Toolkit)
           (java.awt.datatransfer StringSelection)))

(defn copy
  "Given a Clojure form, copy it into the system clipboard."
  [form]
  (try
    (let [toolkit (Toolkit/getDefaultToolkit)
          clipboard (.getSystemClipboard toolkit)
          string (->
                   (binding [*print-length* nil *print-level* nil]
                     (pprint/pprint form {:map-entry-separator ""}))
                   (with-out-str)
                   (string/trim))]
      (.setContents clipboard (StringSelection. string) nil)
      :ok)
    (catch Exception _ :nok)))

#_{:clj-kondo/ignore [:unresolved-namespace]}
(comment
  (copy {:a 1})
  (copy (clojure.datafy/datafy BigInteger))
  ,,,)
