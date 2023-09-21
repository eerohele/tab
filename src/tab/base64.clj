(ns tab.base64
  "Base64 utilities."
  (:import (java.io OutputStream)
           (java.nio.charset StandardCharsets)
           (java.util Base64 Base64$Encoder)))

(set! *warn-on-reflection* true)

(def ^:private ^Base64$Encoder encoder (Base64/getEncoder))

(defn encode
  "Given a string, return a Base64-encoded version of that string."
  [^String s]
  (.encodeToString encoder (.getBytes s StandardCharsets/UTF_8)))

(defn wrap
  "Given a java.io.OutputStream, wrap the OutputStream for encoding byte data."
  ^OutputStream [output-stream]
  (.wrap encoder output-stream))
