(ns tab.impl.pprint
  "A pretty-printer for Clojure data structures.

  Loosely based on the algorithm described in \"Pretty-Printing,
  Converting List to Linear Structure\" by Ira Goldstein (Artificial
  Intelligence, Memo No. 279 in Massachusetts Institute of Technology
  A.I. Laboratory, February 1973)."
  {:author "Eero Helenius"}
  (:import (java.io StringWriter Writer)))

(set! *warn-on-reflection* true)

(defn ^:private strip-ns
  "Given a (presumably qualified) ident, return an unqualified version
  of the ident."
  [x]
  (cond
    (keyword? x) (keyword nil (name x))
    (symbol? x) (symbol nil (name x))))

(defn ^:private extract-map-ns
  "Given a map, iff every key in the map is a qualified ident and they
  share a namespace, return a tuple where the first item is the
  namespace name (a string) and the second item is a copy of the
  original map but with unqualified idents."
  [m]
  (when (seq m)
    (loop [m m ns nil nm {}]
      (if-some [[k v] (first m)]
        (when (qualified-ident? k)
          (let [k-ns (namespace k)]
            (when (or (nil? ns) (= ns k-ns))
              (recur (rest m) k-ns (assoc nm (strip-ns k) v)))))
        [ns nm]))))

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
    "Return the number of characters available on the current line.")

  (nl [this]
    "Write a newline into the underlying java.io.Writer.

    Resets the number of characters allotted to the current line to
    zero. Writing a string with a newline via the write method does
    not."))

(defn ^:private count-keeping-writer
  "Wrap a java.io.Writer into a CountKeepingWriter: a writer that keeps
  count of the length of the strings written into each line."
  [^Writer writer max-width]
  (let [c (volatile! 0)]
    (reify CountKeepingWriter
      (write [_ s]
        (.write writer ^String s)
        (vswap! c #(+ % (.length ^String s)))
        nil)
      (remaining [_]
        (- max-width @c))
      (nl [_]
        (.write writer "\n")
        (vreset! c 0)
        nil))))

(def ^:private reader-macros
  {`quote "'" `deref "@" `var "#'" `unquote "~"})

(defn ^:private open-delim+form
  "Given a coll, return a tuple where the first item is the coll's
  opening delimiter and the second item is the coll.

  If *print-namespace-maps* is true, the coll is a map, and the map is
  amenable to the map namespace syntax, the open delimiter includes
  the map namespace prefix and the map keys are unqualified."
  [coll]
  (if (record? coll)
    [(str "#" (-> coll class .getName) "{") coll]
    (let [[ns ns-map]
          (when (and *print-namespace-maps* (map? coll))
            (extract-map-ns coll))

          coll (if ns ns-map coll)

          o (if ns (str "#:" ns "{") (open-delim coll))]
      [o coll])))

(defn ^:private default-coll?
  [x]
  (or (seq? x) (vector? x) (map? x) (set? x)))

(defn ^:private print-linear
  "Given a form, print it into a string without regard to how much
  horizontal space the string takes."
  ([form]
   (print-linear form nil))
  (^String [form opts]
   (with-open [writer (StringWriter.)]
     (print-linear writer form opts)
     (str writer)))
  ([^Writer writer form {:keys [level] :or {level 0} :as opts}]
   (cond
     (and (default-coll? form) (= level *print-level*))
     (.write writer "#")

     (instance? clojure.lang.PersistentQueue form)
     (do
       (.write writer "<-")
       (print-linear writer (or (seq form) '()) opts)
       (.write writer "-<"))

     ;; Reader macros
     (and
       (seq? form)
       (contains? reader-macros (first form)))
     (do
       (.write writer ^String (reader-macros (first form)))
       (print-method (second form) writer))

     (map-entry? form)
     (do
       (print-linear writer (key form) {:level (inc level)})
       (.write writer " ")
       (print-linear writer (val form) {:level (inc level)}))

     (default-coll? form)
     (let [[^String o form] (open-delim+form form)]
       (.write writer o)

       (when (seq form)
         (loop [form form index 0]
           (if (= index *print-length*)
             (.write writer "...")
             (let [f (first form)
                   n (next form)]
               (print-linear writer f {:level (inc level)})
               (when-not (empty? n)
                 (when (map-entry? f) (.write writer ","))
                 (.write writer " ")
                 (recur n (inc index)))))))

       (.write writer (close-delim form)))

     :else
     (print-method form writer))))

(defn ^:private print-mode
  "Given a java.io.Writer, a string representation of a form, and a
  number of characters to reserve for closing delimiters, return a
  keyword indicating a printing mode (:linear or :miser)."
  [writer ^String s reserve-chars]
  (if (<= (.length s) (- (remaining writer) reserve-chars))
    :linear
    :miser))

(defn ^:private write-sep
  "Given a java.io.Writer and a printing mode, print a separator (a
  space or a newline) into the writer."
  [writer mode]
  (case mode
    :miser (nl writer)
    (write writer " ")))

(defn ^:private meets-print-level?
  "Given a level (a long), return true if the level is the same as
  *print-level*."
  [level]
  (and (int? *print-level*) (= level *print-level*)))

(defn ^:private -pprint
  "Given a CountKeepingWriter and a form, pretty-print the form into the
  writer.

  Keyword args:

    :level (long)
      The current nesting level. For example, in [[:a 1]], the outer
      vector is nested at level 0, and the inner vector is nested at
      level 1.

    :indentation (String)
      The string that represents the current indentation level.

    :reserve-chars (long)
      The number of characters reserved for closing delimiters of
      S-expressions above the current nesting level."
  ([writer form
    {:keys [level indentation reserve-chars]
     :as opts}]
   (cond
     (and (default-coll? form) (meets-print-level? level))
     (write writer "#")

     ;; Reader macros
     (and
       (seq? form)
       (contains? reader-macros (first form)))
     (write writer (print-linear form opts))

     ;; We have to special-case map entries because they normally print
     ;; like vectors (e.g. [:a 1]), but we don't want to print those
     ;; square brackets.
     ;;
     ;; Additionally, we want to keep the key and the value on the same
     ;; line whenever we can.
     (map-entry? form)
     (let [k (key form)
           opts (update opts :level inc)]
       (-pprint writer k opts)

       (let [v (val form)
             s (print-linear v opts)
             ;; If, after writing the map entry key, there's enough space
             ;; to write the val on the same line, do so. Otherwise,
             ;; write indentation followed by val on the following line.
             mode (print-mode writer s (inc reserve-chars))]
         (write-sep writer mode)
         (when (= :miser mode) (write writer indentation))
         (-pprint writer v opts)))

     (default-coll? form)
     (let [s (print-linear form opts)

           ;; If all keys in the map share a namespace and *print-
           ;; namespace-maps* is true, print the map using map namespace
           ;; syntax (e.g. #:a{:b 1} instead of {:a/b 1}).
           [^String o form] (open-delim+form form)

           ;; The indentation level is the indentation level of the
           ;; parent S-expression plus a number of spaces equal to the
           ;; length of the open delimiter (e.g. one for "(", two for
           ;; "#{").
           indentation (str indentation (.repeat " " (.length o)))

           ;; If, after (possibly) reserving space for any closing
           ;; delimiters of ancestor S-expressions, there's enough space
           ;; to print the entire form in linear style on this line, do
           ;; so.
           ;;
           ;; Otherwise, print the form in miser style.
           mode (print-mode writer s reserve-chars)

           opts (-> opts
                  (assoc :indentation indentation)
                  (update :level inc))]

       ;; Print possible meta
       (when (and *print-meta* *print-readably*)
         (when-some [m (meta form)]
           (when (seq m)
             (write writer "^")
             ;; As per https://github.com/clojure/clojure/blob/6975553804b0f8da9e196e6fb97838ea4e153564/src/clj/clojure/core_print.clj#L78-L80
             (let [m (if (and (= (count m) 1) (:tag m)) (:tag m) m)]
               (-pprint writer m opts))
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
                 ;; In miser mode, prepend indentation to every form
                 ;; except the first one. We don't want to prepend
                 ;; indentation for the first form, because it
                 ;; immediately follows the open delimiter.
                 (when (and (= mode :miser) (pos? index))
                   (write writer indentation))

                 (let [f (first form)
                       n (next form)]
                   (cond
                     (empty? n)
                     ;; This is the last child, so reserve an additional
                     ;; slot for the closing delimiter of the parent
                     ;; S-expression.
                     (-pprint writer f (update opts :reserve-chars inc))

                     (map-entry? f)
                     (do
                       ;; Reserve a slot for the comma trailing the
                       ;; map entry.
                       (-pprint writer f (assoc opts :reserve-chars 1))
                       (write writer ",")
                       (write-sep writer mode)
                       (recur n (inc index)))

                     :else
                     (do
                       (-pprint writer f (assoc opts :reserve-chars 0))
                       (write-sep writer mode)
                       (recur n (inc index))))))))))

       ;; Print close delimiter
       (write writer (close-delim form)))

     :else
     (write writer (print-linear form opts)))))

(defn pprint
  "Pretty-print a form.

  Given one arg (a form), pretty-prints the form into *out* using the
  default options.

  Given two args (a form and an options map), pretty-prints the form
  into *out* using the given options.

  Given three args (a java.io.Writer, a form, and an options map),
  pretty-prints the form into the writer using the given options.

  Options:

    :max-width (long)
      Avoid printing anything beyond the column indicated by this
      value."
  ([x]
   (pprint *out* x nil))
  ([x opts]
   (pprint *out* x opts))
  ([writer x {:keys [max-width]
              :or {max-width 72}
              :as opts}]
   (let [writer (count-keeping-writer writer max-width)]
     (-pprint writer x
       (assoc opts :level 0 :indentation "" :reserve-chars 0))
     (nl writer))))

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
