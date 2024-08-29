(ns tab.impl.tabulator
  "Make tables."
  (:require [clojure.pprint :as pprint]
            [tab.impl.annotate :refer [annotate]]
            [tab.impl.db :as db]
            [tab.impl.html :refer [$] :as html]
            [tab.impl.pprint :as pp])
  (:import (clojure.lang Named IPersistentMap Seqable)
           (java.time.format DateTimeFormatter)))

(set! *warn-on-reflection* true)

(def ^:dynamic *initial-print-length* nil)

(defn ^:private seq-label
  [this]
  (cond
    (vector? this) "vector"
    (list? this) "list"
    (and (sorted? this) (set? this)) "sorted set"
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

(defn ^:private compare-with-fallback
  "A compare fn that falls back to comparing by string value if args
  are not comparable."
  [k1 k2]
  (try
    (compare k1 k2)
    (catch ClassCastException _
      (compare (str k1) (str k2)))))

(defn sort-map-by-keys
  "Given a map, return a map sorted by its keys.

  If keys are not comparable or are of different types, compare using the string
  representation of each key."
  [m]
  (into (sorted-map-by compare-with-fallback) m))

(defprotocol Tabulable
  (-tabulate [this level]))

(extend-protocol Tabulable
  nil
  (-tabulate [_ _]
    (annotate "nil"))

  Object
  (-tabulate [this _]
    ($ :a {:href (format "/id/%s" (hash this))} (annotate (pr-str this))))

  Named
  (-tabulate [this _]
    ($ :pre (annotate (pr-str this))))

  Number
  (-tabulate [this _]
    ($ :pre (annotate (pr-str this))))

  String
  (-tabulate [this _]
    ($ :pre (annotate (pr-str this))))

  IPersistentMap
  (-tabulate [this level]
    (cond
      (empty? this)
      (annotate (pr-str this))

      (meets-print-level? level)
      (let [id (hash this)]
        ($ :table {:id id :data-state "collapsed"}
          ($ :thead
            ($ :tr
              ($ :th
                (let [href (format "/toggle/%s?print-length=%s" id *print-length*)]
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
      (let [id (hash this)
            state (state-for level)]
        ($ :table {:id (str id) :data-state "expanded"}
          ($ :thead
            ($ :tr
              ($ :th {:data-action "toggle-level"} (toggle-icon state))
              ($ :th {:class "count"} (count this))
              ($ :th {:colspan "2" :class "value-type"}
                (let [href (format "/id/%s" id)]
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
                  ($ :th (-tabulate k (inc level)))
                  ($ :td (-tabulate v (inc level)))))
              (cond-> this (not (sorted? this)) (sort-map-by-keys))))))))

  Seqable
  (-tabulate [this level]
    (cond
      (empty? this)
      (annotate (pr-str this))

      (and (or (every? map? this) (every? sequential? this)) (meets-print-level? level))
      (let [id (hash this)]
        ($ :table {:id (str id) :data-state "collapsed"}
          ($ :thead
            ($ :tr
              ($ :th
                (let [href (format "/toggle/%s?print-length=%s" id *print-length*)]
                  {:data-action "toggle-level"
                   :bx-dispatch "click"
                   :bx-request "get"
                   :bx-uri href
                   :bx-target "table"
                   :bx-swap "outerHTML"
                   :href href}) "＋")
              ($ :th {:class "count"} (count this))))))

      (every? map? this)
      (let [id (hash this)
            state (state-for level)
            ks (sort-by identity compare-with-fallback
                 (eduction (comp (mapcat keys) (distinct)) this))
            num-items (count this)]
        ($ :table {:id (str id) :data-state "expanded"}
          ($ :thead
            ($ :tr
              ($ :th {:data-action "toggle-level"} (toggle-icon state))
              ($ :th {:class "count"} num-items)
              ($ :th {:colspan (pr-str (count ks)) :class "value-type"}
                (let [href (format "/id/%s" id)]
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
                     ($ :th (annotate (pr-str th)))) ks)))
          (let [cnt (count this)]
            ($ :tbody
              (eduction
                (take (if (int? *print-length*) *print-length* cnt))
                (map-indexed
                  (fn [i m]
                    ($ :tr
                      ($ :td {:class "index"} (pr-str i))
                      (map (fn [k]
                             (let [v (get m k)]
                               ($ :td
                                 (when (some? v)
                                   (-tabulate v (inc level))))))
                        ks))))
                this)

              (cond
                (nil? *initial-print-length*) nil

                (and (int? *print-length*) (> cnt *print-length*))
                ($ :tfoot
                  ($ :tr
                    ($ :th {:colspan (-> ks count inc)}
                      (let [href (format "/toggle/%s?print-length=nil" id)]
                        (list
                          ($ :span "⇣ ")
                          ($ :a {:data-action "toggle-length"
                                 :href href
                                 :bx-dispatch "click"
                                 :bx-request "get"
                                 :bx-uri href
                                 :bx-target "table"
                                 :bx-swap "outerHTML"}
                            (format "%d of %d" *print-length* cnt)))))))

                (and (some? *initial-print-length*) (nil? *print-length*) (> cnt *initial-print-length*))
                ($ :tfoot
                  ($ :tr
                    ($ :th {:colspan (-> ks count inc)}
                      (let [href (format "/toggle/%s?print-length=*print-length*" id)]
                        (list
                          ($ :span "⇡ ")
                          ($ :a {:data-action "toggle-length"
                                 :href href
                                 :bx-dispatch "click"
                                 :bx-request "get"
                                 :bx-uri href
                                 :bx-target "table"
                                 :bx-swap "outerHTML"}
                            (format "%d of %d" cnt cnt))))))))))))

      (every? sequential? this)
      (let [id (hash this)
            state (state-for level)]
        ($ :table {:id (str id) :data-state (name state)}
          ($ :thead
            ($ :tr
              ($ :th {:data-action "toggle-level"} (toggle-icon state))
              ($ :th {:class "count"} (count this))
              ($ :th {:class "value-type"}
                (let [href (format "/id/%s" id)]
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
                  ($ :td (-tabulate seq level))))
              this))))

      :else
      (let [id (hash this)]
        (if (and (int? *print-length*) (> (count this) *print-length*))
          ($ :pre {:data-action "toggle-length"
                   :bx-dispatch "click"
                   :bx-request "get"
                   :bx-uri (format "/toggle/%s?print-length=nil" id)
                   :bx-swap "outerHTML"}
            (annotate
              (binding [*print-level* nil]
                (with-out-str (pp/pprint this)))))
          ($ :pre {:data-action "toggle-length"
                   :bx-dispatch "click"
                   :bx-request "get"
                   :bx-uri (format "/toggle/%s?print-length=*print-length*" id)
                   :bx-swap "outerHTML"}
            (annotate
              (binding [*print-level* nil
                        *print-length* nil]
                (with-out-str (pp/pprint this))))))))))

(def ^:private ^DateTimeFormatter iso-8601-formatter
  (DateTimeFormatter/ISO_INSTANT))

(def ^:private left-icon "❮")
(def ^:private right-icon "❯")

(defn tabulation
  [{:keys [inst val offset] :or {offset 0}} db]
  (let [max-offset (db/history-size db)]
    ($ :main
      (-tabulate val 0)
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

          ($ :a {:href "#"
                 :accesskey "p"
                 :id "pause"
                 :title "Disconnect from server"}
            "⚡")

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
