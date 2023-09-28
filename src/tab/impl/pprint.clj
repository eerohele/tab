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
  "A pretty-printer for Clojure data structures."
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

(defn ^:private -pprint
  [writer form
   & {:keys [level ^String indentation reserve]
      :or {level 0 indentation "" reserve 0}}]
  (let [s (print-linear form)]
    (cond
      (and (map-entry? form)
        (or (nil? *print-level*) (and (int? *print-level*) (< level *print-level*))))
      (let [k (key form)
            v (val form)]

        (-pprint writer k :level (inc level) :indentation indentation :reserve reserve)

        (if (>= (.length (print-linear v)) (- (remaining writer) reserve))
          (do
            (nl writer)
            (write writer indentation))
          (write writer " "))

        (-pprint writer v :level (inc level) :indentation indentation :reserve reserve))

      (coll? form)
      (if (and (int? *print-level*) (= level *print-level*))
        (write writer "#")
        (let [o (open-delim form)
              indentation (str indentation (.repeat " " (.length o)))
              mode (if (<= (.length s) (- (remaining writer) reserve)) :linear :miser)]

          (when (and *print-meta* *print-readably*)
            (when-some [m (meta form)]
              (when (seq m)
                (write writer "^")
                (if (and (= (count m) 1) (:tag m))
                  (-pprint writer (:tag m) :level level :indentation indentation :reserve reserve)
                  (-pprint writer m :level level :indentation indentation :reserve reserve))
                (case mode :miser (nl writer) (write writer " ")))))

          (write writer o)

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
                      (if (empty? n)
                        (-pprint writer f :level (inc level) :indentation indentation :reserve (inc reserve))
                        (do
                          (-pprint writer f :level (inc level) :indentation indentation :reserve (if (map-entry? f) 1 0))
                          (when (map-entry? f) (write writer ","))
                          (case mode :miser (nl writer) (write writer " "))
                          (recur n (inc index))))))))))

          (write writer (close-delim form))))

      :else
      (write writer s))))

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

