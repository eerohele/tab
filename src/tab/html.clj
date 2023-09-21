(ns tab.html
  (:require [clojure.spec.alpha :as spec])
  (:import (clojure.lang IPersistentMap)
           (java.io ByteArrayOutputStream PrintStream StringReader)
           (java.nio.charset StandardCharsets)))

(set! *warn-on-reflection* true)

(defrecord Element
  [tag attrs content])

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
  (emit [this print-stream]
    "Print an object into a java.io.PrintStream.

    Like clojure.xml/emit-element, but forgoes line breaks."))

(extend-protocol Node
  nil
  (emit [_ _])

  String
  (emit [this ^PrintStream ps]
    (with-open [reader (StringReader. this)]
      (loop []
        (let [n (.read reader)]
          (when (pos? n)
            ;; Escape
            (if (or (#{34 38 39 60 61 62} n) (> n 127))
              (do
                (.print ps \&)
                (.print ps \#)
                (.print ps n)
                (.print ps \;))
              (.print ps (char n)))
            (recur))))))

  IPersistentMap
  (emit [{:keys [tag attrs content]} ^PrintStream ps]
    (.print ps "<")
    (.print ps (name tag))

    (when attrs
      (run! #(.print ps (str " " (name (key %)) "=\"" (val %) "\"")) attrs))

    (cond
      (seq content)
      (do
        (.print ps ">")
        (run! #(emit % ps) content)
        (.print ps (str "</" (name tag) ">")))

      (#{:area :base :br :col :embed :hr :img :input :link :meta :source :track :wbr} tag)
      (.print ps "/>")

      :else
      (do
        (.print ps "></")
        (.print ps (name tag))
        (.print ps ">"))))

  Object
  (emit [this ^PrintStream writer] (emit (pr-str this) writer)))

(defn stream
  "Given a clojure.xml-compatible data structure describing an HTML document,
  print the HTML document into a java.io.OutputStream.

  The caller must close the output stream."
  [element]
  (let [baos (ByteArrayOutputStream. 8192)
        stream (PrintStream. baos false StandardCharsets/UTF_8)]
    (emit element stream)
    (.flush stream)
    baos))

(defn string
  "Given a clojure.xml-compatible data structure describing an HTML document,
  print the HTML document into a string."
  [element]
  (-> element stream str))

(defn page
  "Given a clojure.xml-compatible data structure describing an HTML document,
  print a HTML5 doctype followed by the HTML document into a
  java.io.OutputStream.

  The caller must close the output stream."
  [element]
  (let [baos (ByteArrayOutputStream. 8192)
        stream (PrintStream. baos false StandardCharsets/UTF_8)]
    (.print stream "<!DOCTYPE html>")
    (emit element stream)
    (.flush stream)
    baos))

(comment
  (string ($ :html))
  (string ($ :p "&"))
  (string ($ :p "Hello, world!"))
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
