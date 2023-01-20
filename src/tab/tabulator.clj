(ns tab.tabulator
  "Make tables."
  (:require [clojure.pprint :as pprint]
            [tab.annotate :as annotate]
            [tab.base64 :as base64]
            [tab.db :as db]
            [tab.html :refer [$] :as html])
  (:import (clojure.lang IPersistentMap Namespace Seqable Var)
           (java.net URLEncoder)
           (java.time.format DateTimeFormatter)))

(set! *warn-on-reflection* true)

(def ^:dynamic *ann* annotate/annotate)

(defn ^:private encode-uri
  [v]
  (-> v pr-str URLEncoder/encode))

(defn ^:private link
  [href text & {:keys [access-key]}]
  ($ :a (cond-> {:href href} access-key (assoc :accesskey access-key))
    text))

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

(defn ^:private exceeds-print-level?
  [level]
  (and (int? *print-level*) (>= level *print-level*)))

(defn ^:private state-for
  [level]
  (if (exceeds-print-level? level)
    :collapsed
    :expanded))

(defn ^:private toggle
  [state]
  (case state :collapsed "＋" :expanded "－"))

(defprotocol Tabulable
  (-tabulate [this db level]))

(extend-protocol Tabulable
  nil
  (-tabulate [_ _ _]
    (*ann* "nil"))

  Object
  (-tabulate [this _ _]
    (*ann* (pr-str this)))

  Class
  (-tabulate [this _ _]
    (link (str "/class/" (.getName this)) (*ann* (pr-str this))))

  String
  (-tabulate [this _ _]
    ($ :pre (*ann* (pr-str this))))

  Namespace
  (-tabulate [this _ _]
    (link (str "/ns/" (encode-uri (ns-name this))) (*ann* (pr-str this))))

  Var
  (-tabulate [this _ _]
    (link (str "/var/" (encode-uri (ns-name (.ns this))) "/" (encode-uri (.sym this))) (*ann* (pr-str this))))

  IPersistentMap
  (-tabulate [this db level]
    (cond
      (empty? this)
      (*ann* (pr-str this))

      (exceeds-print-level? level)
      (let [uuid (db/put! db this)]
        ($ :table {:data-state "collapsed"}
          ($ :thead
            ($ :tr
              ($ :th
                (let [href (format "/id/%s" uuid)]
                  {:data-action "toggle-level"
                   :bx-dispatch "click"
                   :bx-request "get"
                   :bx-uri href
                   :bx-target "table"
                   :href href})
                "＋")
              ($ :th {:class "count"} (count this))))))

      :else
      (let [state (state-for level)]
        ($ :table {:data-state "expanded"}
          ($ :thead
            ($ :tr
              ($ :th {:data-action "toggle-level"} (toggle state))
              ($ :th {:class "count"} (count this))
              ($ :th {:colspan "2" :class "type"} (map-label this))))
          ($ :tbody
            (map
              (fn [[k v]]
                ($ :tr
                  ($ :td {:class "filler"})
                  ($ :th (-tabulate k db (inc level)))
                  ($ :td (-tabulate v db (inc level)))))
              this))))))

  Seqable
  (-tabulate [this db level]
    (cond
      (empty? this)
      (*ann* (pr-str this))

      (and (or (every? map? this) (every? sequential? this)) (exceeds-print-level? level))
      (let [uuid (db/put! db this)]
        ($ :table {:data-state "collapsed"}
          ($ :thead
            ($ :tr
              ($ :th
                (let [href (format "/id/%s" uuid)]
                  {:data-action "toggle-level"
                   :bx-dispatch "click"
                   :bx-request "get"
                   :bx-uri href
                   :bx-target "table"
                   :href href}) "＋")
              ($ :th {:class "count"} (count this))))))

      (every? map? this)
      (let [state (state-for level)
            ks (sequence (comp (mapcat keys) (distinct)) this)
            num-items (count this)]
        ($ :table {:data-state "expanded"}
          ($ :thead
            ($ :tr
              ($ :th {:data-action "toggle-level"} (toggle state))
              ($ :th {:class "count"} num-items)
              ($ :th {:colspan (pr-str (count ks)) :class "type"}
                (seq-label this)))
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
      (let [state (state-for level)]
        ($ :table {:data-state (name state)}
          ($ :thead
            ($ :tr
              ($ :th {:data-action "toggle-level"} (toggle state))
              ($ :th {:class "count"} (count this))
              ($ :th {:class "type"} (seq-label this))))
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

(def ^:private ^DateTimeFormatter date-time-formatter
  (DateTimeFormatter/ofPattern "E d. MMM HH:mm:ss"))

(defn tabulate
  [{:keys [db data offset max-offset inst] :or {offset 0 max-offset 0}}]
  ($ :main
    (-tabulate data db 0)
    ($ :nav
      ($ :div {:class "left"}
        (if (< offset (dec max-offset))
          (link (format "/val/-%d" (inc offset)) "❮" :access-key "h")
          ($ :span "❮"))
        (cond
          (= 1 offset)
          (link "/" "❯" :access-key "l")
          (pos? offset)
          (link (format "/val/-%d" (dec offset)) "❯" :access-key "l")
          :else
          ($ :span "❯")))
      ($ :div {:class "right"}
        (when inst
          ($ :time {:datetime (str inst) :title (str inst)}
            (.format date-time-formatter inst)))))))
