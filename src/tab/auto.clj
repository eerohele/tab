(ns tab.auto
  "Load this namespace to run a Tab using sensible defaults."
  (:require [tab.api :as tab]))

(declare tab)

(alter-var-root #'tab
  (fn [_]
    (tab/run :print-length 8 :print-level 2)))

(defn halt
  []
  (alter-var-root #'tab (fn [_] (tab/halt tab) nil)))
