(ns tab.auto
  "Load this namespace to run a Tab using sensible defaults.

  See tab.api for the API proper."
  (:require [tab.api :as tab]))

(def tab "A Tab." nil)

(alter-var-root #'tab
  (fn [_]
    (let [tab (tab/run :print-length 8 :print-level 2)]
      (printf "Tab is listening on %s\n" (tab/address tab))
      tab)))

(defn halt
  []
  (alter-var-root #'tab (fn [_] (tab/halt tab) nil)))
