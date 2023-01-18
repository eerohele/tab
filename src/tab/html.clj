(ns tab.html
  (:require [clojure.spec.alpha :as spec]
            [clojure.string :as string])
  (:import (clojure.lang IPersistentMap)
           (java.io StringWriter)))

(set! *warn-on-reflection* true)

(defrecord Element
  [tag attrs content])

(defn escape
  "Given a string, return a HTML-escaped version of that string."
  [^String string]
  (string/join
    (map (fn [ch]
           (if (or (#{34 38 39 60 61 62} ch) (> ch 127))
             (format "&#%s;" ch)
             (String. (Character/toChars ch))))
      (some-> string .codePoints .iterator iterator-seq))))

(defn ^:private ->content
  [nodes]
  (reduce
    (fn [nodes node]
      (if (sequential? node)
        (into nodes node)
        (conj nodes node)))
    []
    nodes))

(defn $
  "Given a tag (a keyword) and any number of nodes, return a clojure.xml-
  compatible representation of an XML or HTML element."
  [tag & nodes]
  (let [node (first nodes)]
    (map->Element
      (cond
        (empty? nodes)
        {:tag tag}
        
        (and (map? node) (not (instance? Element node)))
        (let [children (next nodes)]
          (cond-> {:tag tag :attrs node}
            (seq children) (assoc :content (->content children))))

        :else
        (cond-> {:tag tag}
          (seq nodes) (assoc :content (->content nodes)))))))

(comment
  ($ :html)
  ($ :a {:href "https://github.com/eerohele/tab"})
  ($ :ul ($ :li "1") ($ :li "2") ($ :li "3"))
  ($ :ul (map #($ :li (pr-str %)) [1 2 3]))
  ,,,)

(defprotocol Node
  "A node that can be emitted into an HTML string."
  (emit [this]
    "Print an object into an HTML string.

    Like clojure.xml/emit-element, but forgoes line breaks."))

(extend-protocol Node
  nil
  (emit [_])

  String
  (emit [this] (print (escape this)))

  IPersistentMap
  (emit [{:keys [tag attrs content]}]
    (print (str "<" (name tag)))

    (when attrs
      (run! #(print (str " " (name (key %)) "=\"" (val %) "\"")) attrs))

    (cond
      (seq content)
      (do
        (print ">")
        (run! emit content)
        (print (str "</" (name tag) ">")))

      (#{:area :base :br :col :embed :hr :img :input :link :meta :source :track :wbr} tag)
      (print "/>")

      :else
      (printf "></%s>" (name tag))))

  Object
  (emit [this] (emit (pr-str this))))

(defn html
  "Given a clojure.xml-compatible data structure describing a HTML document,
  print the HTML document into a string."
  [element]
  (with-open [writer (StringWriter.)]
    (binding [*out* writer]
      (emit element))
    (.toString writer)))

(defn page
  "Given a clojure.xml-compatible data structure describing a HTML document,
  print the HTML document into a string, and prepend a HTML5 doctype."
  [element]
  (str "<!DOCTYPE html>" (html element)))

(comment
  (html ($ :html))
  (html ($ :p "&"))
  (html ($ :code {:class "ann"} ($ :span {:class "symbol"} "<=")))
  (html ($ :meta {:charset "utf-8"}))
  (html ($ :html ($ :head) ($ :body)))
  (html ($ :a {:href "#"}))
  ,,,)

(spec/def ::tag simple-keyword?)

(spec/def ::attrs
  (spec/map-of simple-keyword? string?))

(spec/def ::content
  (spec/coll-of (spec/or :node ::node :string string?)))

(spec/def ::node
  (spec/keys :req-un [::tag]
    :opt-un [::attrs ::content]))

(comment
  (spec/exercise ::node)
  ,,,)
