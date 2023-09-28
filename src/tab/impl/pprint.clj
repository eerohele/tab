;; Copyright (c) Eero Helenius. All rights reserved.
;;
;; The use and distribution terms for this software are covered by the
;; MIT License (https://mit-license.org), which can be found in the file
;; named LICENSE at the root of this distribution.
;;
;; By using this software in any fashion, you are agreeing to be bound
;; by the terms of this license.
;;
;; You must not remove this notice, or any other, from this software.

(ns tab.impl.pprint
  "A pretty-printer for Clojure data structures.

  Loosely based on the algorithm described in \"Pretty-Printing,
  Converting List to Linear Structure\" by Ira Goldstein (Artificial
  Intelligence, Memo No. 279 in Massachusetts Institute of Technology
  A.I. Laboratory, February 1973)."
  {:author "Eero Helenius"}
  (:import (java.io StringWriter Writer)))

(set! *warn-on-reflection* true)

(defn ^:private open-delim
  "Return the open delimiter (a string) of coll."
  ^String [coll]
  (cond
    (map? coll) "{"
    (vector? coll) "["
    (set? coll) "#{"
    :else "("))

(defn ^:private close-delim
  "Return the close delimiter (a string) of coll."
  ^String [coll]
  (cond
    (map? coll) "}"
    (vector? coll) "]"
    (set? coll) "}"
    :else ")"))

(defprotocol ^:private CountKeepingWriter
  (write [this s]
    "Write a string into the underlying java.io.Writer.")

  (remaining [this]
    "Return the number of available characters on the current line.")

  (nl [this]
    "Write a newline into the underlying java.io.Writer.

    Resets the number of characters allocated to the current line to
    zero. Writing a string with a newline via the write method does
    not."))

(defn ^:private count-keeping-writer
  "Wrap a java.io.Writer such that it keeps count of the length of the
  strings written into it."
  [^Writer writer max-width]
  (let [c (volatile! 0)]
    (reify CountKeepingWriter
      (write [_ s]
        (.write writer ^String s)
        (vswap! c #(+ % (.length ^String s))))
      (remaining [_]
        (- max-width @c))
      (nl [_]
        (.write writer "\n")
        (vreset! c 0)))))

(defn ^:private print-linear
  "Given a form, print it into a string without regard to how much
  horizontal space the string takes."
  ^String [form]
  (with-open [writer (StringWriter.)]
    (print-method form writer)
    (str writer)))

(defn ^:private write-sep
  [writer mode]
  (case mode
    :miser (nl writer)
    (write writer " ")))

(defn ^:private print-mode
  [writer ^String s reserve-chars]
  (if (<= (.length s) (- (remaining writer) reserve-chars))
    :linear
    :miser))

(defn ^:private -pprint
  "Given a java.io.Writer, a form, and an options map, pretty-print the
  form into the writer.

  Options:

    :level
      The level the form is nested at."
  [writer form
   & {:keys [level ^String indentation reserve-chars]
      :or {level 0 indentation "" reserve-chars 0}}]
  (cond
    (and (map-entry? form)
      (or (nil? *print-level*)
        (and (int? *print-level*) (< level *print-level*))))
    (let [k (key form)
          v (val form)
          s (print-linear v)]

      (-pprint writer k
        :level (inc level)
        :indentation indentation
        :reserve-chars reserve-chars)

      (case (print-mode writer s reserve-chars)
        :linear
        (write writer " ")

        :miser
        (do
          (nl writer)
          (write writer indentation)))

      (-pprint writer v
        :level (inc level)
        :indentation indentation
        :reserve-chars reserve-chars))

    (coll? form)
    (if (and (int? *print-level*) (= level *print-level*))
      (write writer "#")
      (let [s (print-linear form)
            o (open-delim form)
            indentation (str indentation (.repeat " " (.length o)))

            ;; If, after possibly reserving space to print any close
            ;; delimiters from wrapping S-expressions, there's enough
            ;; space to print the entire form in linear style on this
            ;; line, do so.
            ;;
            ;; Otherwise, print the form in miser style.
            mode (print-mode writer s reserve-chars)]

        ;; Print possible meta
        (when (and *print-meta* *print-readably*)
          (when-some [m (meta form)]
            (when (seq m)
              (write writer "^")
              ;; As per https://github.com/clojure/clojure/blob/6975553804b0f8da9e196e6fb97838ea4e153564/src/clj/clojure/core_print.clj#L78-L80
              (let [m (if (and (= (count m) 1) (:tag m)) (:tag m) m)]
                (-pprint writer m
                  :level level
                  :indentation indentation
                  :reserve-chars reserve-chars))
              (write-sep writer mode))))

        ;; Print open delimiter
        (write writer o)

        ;; Print S-expression content
        (if (= *print-length* 0)
          (write writer "...")
          (when (seq form)
            (loop [form form
                   index 0]
              (if (= index *print-length*)
                (do
                  (when (= mode :miser) (write writer indentation))
                  (write writer "..."))

                (do
                  (when (and (= mode :miser) (pos? index))
                    (write writer indentation))

                  (let [f (first form)
                        n (next form)]
                    (cond
                      (empty? n)
                      (-pprint writer f
                        :level (inc level)
                        :indentation indentation
                        :reserve-chars (inc reserve-chars))

                      (map-entry? f)
                      (do
                        (-pprint writer f
                          :level (inc level)
                          :indentation indentation
                          :reserve-chars 1)

                        (write writer ",")

                        (write-sep writer mode)

                        (recur n (inc index)))

                      :else
                      (do
                        (-pprint writer f
                          :level (inc level)
                          :indentation indentation
                          :reserve-chars 0)

                        (write-sep writer mode)

                        (recur n (inc index))))))))))

        ;; Print close delimiter
        (write writer (close-delim form))))

    :else
    (write writer (print-linear form))))

(defn pprint
  ([x]
   (pprint *out* x nil))
  ([x opts]
   (pprint *out* x opts))
  ([writer x {:keys [max-width] :or {max-width 72}}]
   (assert (nat-int? max-width)
     ":max-width must be a natural int")

   (assert (instance? Writer writer)
     "first arg to pprint must be a java.io.Writer")

   (let [writer (count-keeping-writer writer max-width)]
     (-pprint writer x)
     (nl writer)
     nil)))

(comment
  ;; Bad input
  (pprint nil {:a 1} {:max-width 1})
  (pprint {:a 1} {:max-width -1})

  (pprint
    {:a 1
     :b 2
     :c 3
     :d 4
     :e {:a 1 :b 2 :c 3 :d 4 :e {:f 6 :g 7 :h 8 :i 9 :j 10}}}
    {:max-width 24})

  (require '[clojure.pprint :as cpp])

  (binding [*print-meta* true *print-readably* false] (pprint (with-meta {:a 1} {:b 2})))

  ;; clojure.pprint incorrectly prints this with meta
  (binding [*print-meta* true *print-readably* false] (cpp/pprint (with-meta {:a 1} {:b 2})))

  ;; clojure.pprint incorrectly prints empty meta
  (binding [*print-meta* true *print-readably* false] (cpp/pprint (with-meta {:a 1} {})))

  ;; inconsistency between prn and clojure.pprint
  (binding [*print-level* 0 *print-length* 11]
    (prn
      (into (sorted-map)
        (zipmap (map (comp keyword str char) (range 97 123))
          (range 1 18)))))

  (binding [*print-level* 1 *print-length* 11]
    (pprint
      (into (sorted-map)
        (zipmap (map (comp keyword str char) (range 97 123))
          (range 1 18)))))

  (binding [*print-level* 1 *print-length* 11]
    (cpp/pprint
      (into (sorted-map)
        (zipmap (map (comp keyword str char) (range 97 123))
          (range 1 18)))))
  ,,,)

