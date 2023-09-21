(ns tab.html
  (:require [clojure.spec.alpha :as spec]
            [clojure.string :as string])
  (:import (clojure.lang IPersistentMap)
           (java.io StringWriter Writer)))

(set! *warn-on-reflection* true)

(defrecord Element
  [tag attrs content])

(defn escape
  "Given a string, return a HTML-escaped version of that string."
  ^String [^String string]
  (string/join
    (map (fn [ch]
           (if (or (#{34 38 39 60 61 62} ch) (> ch 127))
             (format "&#%s;" ch)
             (String. (Character/toChars ch))))
      (some-> string .codePoints .iterator iterator-seq))))

(defn ^:private ->content
  [nodes]
  (let [xs (transient [])]
    (run!
      (fn [node]
        (if (sequential? node)
          (run! #(conj! xs %) node)
          (conj! xs node)))
      nodes)
    (persistent! xs)))

(defn $
  "Given a tag (a keyword), optionally an attribute map, and any number of child
  nodes, return a clojure.xml-compatible representation of an HTML element."
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
  (emit [this writer]
    "Print an object into an HTML string.

    Like clojure.xml/emit-element, but forgoes line breaks."))

(extend-protocol Node
  nil
  (emit [_ _])

  String
  (emit [this ^Writer writer] (.write writer (escape this)))

  IPersistentMap
  (emit [{:keys [tag attrs content]} ^Writer writer]
    (.write writer (str "<" (name tag)))

    (when attrs
      (run! #(.write writer (str " " (name (key %)) "=\"" (val %) "\"")) attrs))

    (cond
      (seq content)
      (do
        (.write writer ">")
        (run! #(emit % writer) content)
        (.write writer (str "</" (name tag) ">")))

      (#{:area :base :br :col :embed :hr :img :input :link :meta :source :track :wbr} tag)
      (.write writer "/>")

      :else
      (do
        (.write writer "></")
        (.write writer (name tag))
        (.write writer ">"))))

  Object
  (emit [this ^Writer writer] (emit (pr-str this) writer)))

(defn string
  "Given a clojure.xml-compatible data structure describing an HTML document,
  print the HTML document into a string."
  [element]
  (with-open [writer (StringWriter.)]
    (emit element writer)
    (.toString writer)))

(defn page
  "Given a clojure.xml-compatible data structure describing an HTML document,
  print the HTML document into a string, and prepend a HTML5 doctype."
  [element]
  (str "<!DOCTYPE html>" (string element)))

(comment
  (string ($ :html))
  (string ($ :p "&"))
  (string ($ :code {:class "ann"} ($ :span {:class "symbol"} "<=")))
  (string ($ :meta {:charset "utf-8"}))
  (string ($ :html ($ :head) ($ :body)))
  (string ($ :a {:href "#"}))
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
