(ns tab.annotate
  "Given a string of Clojure code, return a Hiccup vector that annotates the
  given code for syntax highlighting."
  (:require [clojure.string :as string]
            [tab.html :refer [$] :as html])
  (:import (java.io BufferedReader PushbackReader StringReader)))

(set! *warn-on-reflection* true)

(defn ^:private annotate-comment
  [^PushbackReader reader]
  (let [comment (loop [chars []]
                  (let [i (.read reader)]
                    (cond
                      (neg? i)
                      chars

                      (= 10 i) ; newline
                      (do
                        (.unread reader i)
                        chars)

                      :else
                      (recur (conj chars (char i))))))]
    ($ :span {:class "comment"} (string/join comment))))

(def ^:private macros
  (set (map int #{\" \; \^ \( \) \[ \] \{ \} \\ \#})))

(def ^:private terminating-macros
  (disj macros (map int #{\# \'})))

(defn ^:private annotate-keyword
  [^PushbackReader reader]
  (let [keyword (loop [chars []]
                  (let [i (.read reader)]
                    (cond
                      (neg? i)
                      chars

                      (or (terminating-macros i) (#{32 44 10} i))
                      (do
                        (.unread reader i)
                        chars)

                      :else
                      (recur (conj chars (char i))))))
        [colons sym] (split-with (partial = \:) keyword)]
    ($ :span {:class "keyword"}
      ($ :span {:class "punctuation"} (string/join colons))
      (string/join sym))))

(comment (annotate-keyword (StringReader. ":foo")) ,,,)

(defn ^:private annotate-char
  [^PushbackReader reader]
  (let [char (loop [chars []]
               (let [i (.read reader)]
                 (cond
                   (neg? i) chars
                   (#{32 44} i) ; space or comma

                   (do
                     (.unread reader i)
                     chars)

                   :else
                   (recur (conj chars (char i))))))]
    ($ :span {:class "character"}
     ($ :span {:class "punctuation"} "\\")
     (string/join char))))

(comment (annotate-char (StringReader. "x")) ,,,)

(defn ^:private annotate-string
  [form]
  (let [s (pr-str form)
        len (count s)]
    ($ :span {:class "string"}
      ($ :span {:class "punctuation"} "\"")
      (string/replace (subs s 1 (dec len)) "\\n" "\n")
      ($ :span {:class "punctuation"} "\""))))

(comment (annotate-string "x") ,,,)

(defn regex?
  [x]
  (instance? java.util.regex.Pattern x))

(defn ^:private annotate-regex
  [form]
  (let [s (pr-str form)
        len (count s)]
    ($ :span {:class "regex"}
      ($ :span {:class "dispatch"} "#")
      ($ :span {:class "punctuation"} "\"")
      (string/replace (subs s 2 (dec len)) "\\n" "\n")
      ($ :span {:class "punctuation"} "\""))))

(comment (annotate-regex #"x") ,,,)

(defn ^:private annotate-form
  [form]
  (cond
    (string? form)
    (annotate-string form)

    (regex? form)
    (annotate-regex form)

    :else
    ($ :span {:class (cond
                       (special-symbol? form) "symbol special-symbol"
                       (number? form) "number"
                       (and (symbol? form) (= form '&)) "ampersand"
                       (symbol? form) "symbol"
                       :else "unknown")}
      (pr-str form))))

(comment
  (annotate-form 'x)
  ,,,)

(def ^:private def-syms
  #{'deftype
    'defstruct
    'definline
    'definterface
    'defn
    'defmethod
    'defonce
    'defn-
    'defprotocol
    'defmacro
    'defmulti
    'defrecord})

(defn ^:private make-reader
  [string]
  (-> string StringReader. BufferedReader. (PushbackReader. 2)))

(defn annotate
  "Given a string of Clojure code, return a clojure.xml-compatible data
  structure that annotates the code for syntax highlighting."
  [string]
  (if (string/blank? string)
    ($ :code {:class "ann"})
    (with-open [^PushbackReader reader (make-reader string)]
      (letfn [(aux [ctx element]
                (let [i (.read reader)]
                  (if (neg? i)
                    element
                    (let [s (-> i char str)]
                      (cond
                        (#{" " "\n" "\t"} s)
                        (recur ctx (conj element s))

                        (= "," s)
                        (recur ctx (conj element ($ :span {:class "comma"} s)))

                        (= "'" s)
                        (recur ctx (conj element ($ :span {:class "quote"} s)))

                        (= "`" s)
                        (recur ctx (conj element ($ :span {:class "syntax-quote"} s)))

                        (= "~" s)
                        (recur ctx (conj element ($ :span {:class "unquote"} s)))

                        (= "@" s)
                        (recur ctx (conj element ($ :span {:class "deref"} s)))

                        (= "^" s)
                        (recur ctx (conj element ($ :span {:class "metadata"} s)))

                        (= "\\" s)
                        (recur ctx (conj element (annotate-char reader)))

                        (= "#" s)
                        (let [i2 (.read reader)]
                          (.unread reader i2)
                          (if (= (char i2) \") ; regex
                            (do
                              (.unread reader i)
                              (let [form (read reader)]
                                (recur ctx (conj element (annotate-form form)))))
                            (recur ctx (conj element ($ :span {:class "dispatch"} s)))))

                        (= ";" s)
                        (do (.unread reader i)
                          (recur ctx (conj element (annotate-comment reader))))

                        (= ":" s)
                        (do (.unread reader i)
                          (recur ctx (conj element (annotate-keyword reader))))

                        (#{"(" "[" "{"} s)
                        (recur ctx
                          (conj element ($ :span {:class "sexp"}
                                          ($ :span {:class "punctuation"} s)
                                          (aux [] []))))

                        (#{")" "]" "}"} s)
                        (conj element ($ :span {:class "punctuation"} s))

                        :else
                        (do
                          (.unread reader i)

                          (let [form (read reader)]
                            (cond
                              (nil? form)
                              (recur ctx (conj element ($ :span {:class "nil"} "nil")))

                              (boolean? form)
                              (recur ctx (conj element ($ :span {:class "boolean"} (pr-str form))))

                              (and (symbol? form) (contains? def-syms form))
                              (recur
                                (conj ctx :def)
                                (conj element ($ :span {:class "symbol"} (pr-str form))))

                              (and (symbol? form) (= form '&))
                              (recur ctx (conj element ($ :span {:class "ampersand"} "&")))

                              (and (symbol? form) (= :def (peek ctx)))
                              (recur
                                (pop ctx)
                                (conj element ($ :span {:class "symbol var"} (pr-str form))))

                              (symbol? form)
                              (recur ctx (conj element ($ :span {:class "symbol"} (pr-str form))))

                              (regex? form)
                              (recur ctx (conj element (annotate-regex form)))

                              (string? form)
                              (recur ctx (conj element (annotate-string form)))

                              (number? form)
                              (recur ctx (conj element ($ :span {:class "number"} (pr-str form))))

                              :else
                              (recur ctx (conj element ($ :span {:class "unknown"} (pr-str form))))))))))))]
        ($ :code {:class "ann"} (aux [] []))))))

#_{:clj-kondo/ignore [:unresolved-namespace]}
(comment
  (annotate (clojure.repl/source-fn 'doseq))
  (annotate (pr-str '(tap> :hello-world)))
  ,,,)
