(ns tab.tabulator
  "Make tables."
  (:require [clojure.datafy :as datafy]
            [clojure.pprint :as pprint]
            [tab.annotate :as annotate]
            [tab.base64 :as base64]
            [tab.db :as db]
            [tab.html :refer [$] :as html])
  (:import (clojure.lang Named IPersistentMap Seqable)
           (java.time.format DateTimeFormatter)))

(set! *warn-on-reflection* true)

(def ^:dynamic *ann* annotate/annotate)

(defn ^:private seq-label
  [this]
  (cond
    (vector? this) "vector"
    (list? this) "list"
    (set? this) "set"
    (lazy-seq this) "lazy seq"
    (instance? clojure.lang.Range this) "range"
    :else "seq"))

(defn ^:private map-label
  [this]
  (condp = (class this)
    clojure.lang.PersistentArrayMap "array map"
    clojure.lang.PersistentHashMap "hash map"
    clojure.lang.PersistentTreeMap "sorted map"
    "map"))

(defn ^:private meets-print-level?
  [level]
  (and (int? *print-level*) (>= level *print-level*)))

(defn ^:private state-for
  [level]
  (if (meets-print-level? level)
    :collapsed
    :expanded))

(defn ^:private toggle-icon
  [state]
  (case state :collapsed "＋" :expanded "－"))

(def ^:private sorted-map-with-fallback
  (sorted-map-by
    (fn [k1 k2]
      (try
        (compare k1 k2)
        (catch ClassCastException _
          (compare (str k1) (str k2)))))))

(defn sort-map-by-keys
  "Given a map, return a map sorted by its keys.

  If keys are not comparable or are of different types, compare using the string
  representation of each key."
  [m]
  (into sorted-map-with-fallback m))

(defprotocol Tabulable
  (-tabulate [this db level]))

(extend-protocol Tabulable
  nil
  (-tabulate [_ _ _]
    (*ann* "nil"))

  Object
  (-tabulate [this db _]
    (let [[uuid _] (db/put! db (datafy/datafy this))]
      ($ :a {:href (format "/id/%s" uuid)} (*ann* (pr-str this)))))

  Named
  (-tabulate [this _ _]
    ($ :pre (*ann* (pr-str this))))

  Number
  (-tabulate [this _ _]
    ($ :pre (*ann* (pr-str this))))

  String
  (-tabulate [this _ _]
    ($ :pre (*ann* (pr-str this))))

  IPersistentMap
  (-tabulate [this db level]
    (cond
      (empty? this)
      (*ann* (pr-str this))

      (meets-print-level? level)
      (let [[uuid _] (db/put! db this)]
        ($ :table {:id (str uuid) :data-state "collapsed"}
          ($ :thead
            ($ :tr
              ($ :th
                (let [href (format "/table/%s" uuid)]
                  {:data-action "toggle-level"
                   :bx-dispatch "click"
                   :bx-request "get"
                   :bx-uri href
                   :bx-target "table"
                   :bx-swap "outerHTML"
                   :href href})
                "＋")
              ($ :th {:class "count"} (count this))))))

      :else
      (let [[uuid _] (db/put! db this)
            state (state-for level)]
        ($ :table {:id (str uuid) :data-state "expanded"}
          ($ :thead
            ($ :tr
              ($ :th {:data-action "toggle-level"} (toggle-icon state))
              ($ :th {:class "count"} (count this))
              ($ :th {:colspan "2" :class "value-type"}
                (let [href (format "/id/%s" uuid)]
                  ($ :a {:href href
                         :bx-dispatch "click"
                         :bx-request "get"
                         :bx-uri href
                         :bx-target "main"
                         :bx-swap "innerHTML"
                         :bx-push-url href}
                    (map-label this))))))
          ($ :tbody
            (map
              (fn [[k v]]
                ($ :tr
                  ($ :td {:class "filler"})
                  ($ :th (-tabulate k db (inc level)))
                  ($ :td (-tabulate v db (inc level)))))
              (into sorted-map-with-fallback this)))))))

  Seqable
  (-tabulate [this db level]
    (cond
      (empty? this)
      (*ann* (pr-str this))

      (and (or (every? map? this) (every? sequential? this)) (meets-print-level? level))
      (let [[uuid _] (db/put! db this)]
        ($ :table {:id (str uuid) :data-state "collapsed"}
          ($ :thead
            ($ :tr
              ($ :th
                (let [href (format "/table/%s" uuid)]
                  {:data-action "toggle-level"
                   :bx-dispatch "click"
                   :bx-request "get"
                   :bx-uri href
                   :bx-target "table"
                   :bx-swap "outerHTML"
                   :href href}) "＋")
              ($ :th {:class "count"} (count this))))))

      (every? map? this)
      (let [[uuid _] (db/put! db this)
            state (state-for level)
            ks (sequence (comp (mapcat keys) (distinct)) this)
            num-items (count this)]
        ($ :table {:id (str uuid) :data-state "expanded"}
          ($ :thead
            ($ :tr
              ($ :th {:data-action "toggle-level"} (toggle-icon state))
              ($ :th {:class "count"} num-items)
              ($ :th {:colspan (pr-str (count ks)) :class "value-type"}
                (let [href (format "/id/%s" uuid)]
                  ($ :a {:href href
                         :bx-dispatch "click"
                         :bx-request "get"
                         :bx-uri href
                         :bx-target "main"
                         :bx-swap "innerHTML"
                         :bx-push-url href}
                    (seq-label this)))))
            ($ :tr {:class "sticky"}
              ($ :th {:title "Total number of items in this collection."
                      :class "count"} num-items)
              (map (fn [th]
                     ($ :th (*ann* (pr-str th)))) ks)))
          ($ :tbody
            (map-indexed
              (fn [i m]
                ($ :tr
                  ($ :td {:class "index"} (pr-str i))
                  (map (fn [k]
                         (let [v (get m k)]
                           ($ :td
                             (when (some? v)
                               (-tabulate v db (inc level))))))
                    ks)))
              this))))

      (every? sequential? this)
      (let [[uuid _] (db/put! db this)
            state (state-for level)]
        ($ :table {:id (str uuid) :data-state (name state)}
          ($ :thead
            ($ :tr
              ($ :th {:data-action "toggle-level"} (toggle-icon state))
              ($ :th {:class "count"} (count this))
              ($ :th {:class "value-type"}
                (let [href (format "/id/%s" uuid)]
                  ($ :a {:href href
                         :bx-dispatch "click"
                         :bx-request "get"
                         :bx-uri href
                         :bx-target "main"
                         :bx-swap "innerHTML"
                         :bx-push-url href} (seq-label this))))))
          ($ :tbody
            (map-indexed
              (fn [i seq]
                ($ :tr
                  ($ :td {:class "index"} (pr-str i))
                  ($ :td (-tabulate seq db level))))
              this))))

      :else
      (let [full-value (*ann*
                         (binding [*print-level* nil
                                   *print-length* nil
                                   pprint/*print-right-margin* 80]
                           (with-out-str (pprint/pprint this))))]
        (if (and (int? *print-length*) (> (count this) *print-length*))
          ($ :pre {:data-action "toggle-length"
                   :data-state "collapsed"
                   :data-value (base64/encode (html/html full-value))}
            (*ann*
              (binding [*print-level* nil
                        pprint/*print-right-margin* 80]
                (with-out-str (pprint/pprint this)))))
          ($ :pre full-value))))))

(def ^:private ^DateTimeFormatter iso-8601-formatter
  (DateTimeFormatter/ISO_INSTANT))

(def ^:private left-icon "❮")
(def ^:private right-icon "❯")

(defn tabulate
  [{:keys [inst val offset] :or {offset 0}} db]
  (let [max-offset (db/history-size db)]
    ($ :main
      (-tabulate val db 0)
      ($ :footer
        ($ :form {:action "/db/empty" :method "POST"}
          ($ :button {:accesskey "x"
                      :type "submit"
                      :title "The number of values Tab currently has stored in its in-memory database. Click to empty the database and allow all values to be garbage-collected."}
            (let [db-size (db/size db)]
              ($ :span
                ($ :span {:class (cond
                                   (> db-size 10000) "num-vals-warning-heavy"
                                   (> db-size 5000) "num-vals-warning-soft"
                                   :else "")}
                  (pprint/cl-format nil "~,,' :D" db-size))
                ($ :span " vals")))))

        ($ :nav
          (if (< offset (dec max-offset))
            ($ :a {:data-testid "prev"
                   :href (format "/val/-%d" (inc offset))
                   :accesskey "h"
                   :title "Go to previous value in history"} left-icon)
            ($ :span {:data-testid "prev" :class "noop"} left-icon))

          (if (> max-offset 1)
            ($ :a {:href "/"
                   :title "Go back to index"} "○")
            ($ :span {:class "noop"} "○"))

          (cond
            (= 1 offset)
            ($ :a {:data-testid "next"
                   :href "/"
                   :accesskey "l"} right-icon)
            (pos? offset)
            ($ :a {:data-testid "next"
                   :href (format "/val/-%d" (dec offset))
                   :accesskey "l"
                   :title "Go to next value in history"} right-icon)
            :else
            ($ :span {:data-testid "next" :class "noop"} right-icon)))

        ($ :div {:class "time"}
          (when inst
            ($ :time {:datetime (.format iso-8601-formatter inst) :title (str inst)})))))))
