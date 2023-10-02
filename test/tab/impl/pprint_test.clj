(ns tab.impl.pprint-test
  (:require [clojure.pprint :as cpp]
            [clojure.test :refer [deftest is]]
            [time-literals.read-write :as time-literals]
            [tab.impl.pprint :as sut]))

(time-literals/print-time-literals-clj!)

(defn pp-str
  [x]
  (with-out-str (sut/pprint x)))

(defmacro $
  "Given an input and printing options, check that the SUT prints the
  input the same way as clojure.pprint/pprint."
  [input &
   {:keys [print-length print-level print-meta print-readably max-width]
    :or {print-length nil
         print-level nil
         print-meta false
         print-readably true
         max-width 72}}]
  `(is (= (binding [cpp/*print-right-margin* ~max-width
                    *print-length* ~print-length
                    *print-level* ~print-level
                    *print-meta* ~print-meta
                    *print-readably* ~print-readably]
            (with-out-str (cpp/pprint ~input)))
         (binding [*print-length* ~print-length
                   *print-level* ~print-level
                   *print-meta* ~print-meta
                   *print-readably* ~print-readably]
           (with-out-str (sut/pprint ~input {:max-width ~max-width}))))))

(comment ($ {:a 1}) ,,,)

(deftest pprint
  ;; Basic
  ($ {})
  ($ [nil nil])
  ($ {:a 1})
  ($ '(1 nil))
  ($ {:a 1 :b 2 :c 3 :d 4} :max-width 24)
  ($ {:args [{:op :var :assignable? true}]} :max-width 24)
  ($ {:a 1 :b 2 :c 3 :d 4 :e 5} :max-width 24)
  ($ {:a 1 :b 2 :c 3 :d 4} :max-width 0)
  ($ {:a 1 :b 2 :c 3 :d 4 :e {:f 6}} :max-width 24)
  ($ {:a 1
      :b 2
      :c 3
      :d 4
      :e {:a 1 :b 2 :c 3 :d 4 :e {:f 6 :g 7 :h 8 :i 9 :j 10}}}
    :max-width 24)

  ($ (clojure.lang.PersistentQueue/EMPTY))
  ($ (conj (clojure.lang.PersistentQueue/EMPTY) 1))

  ;; Max width
  ($ {:a 1 :b 2 :c 3 :d 4} :max-width 0)

  ;; Meta
  ($ (with-meta {:a 1} {:b 2}) :print-meta true)
  ($ (with-meta {:a 1} {:b 2}) :print-meta true :max-width 2)

  ;; Print level
  ($ {:a 1} :print-level 0)
  ($ {:a {:b 2}} :print-level 1)
  ($ {:a {:b 2}} :print-level 2)
  ($ {:a {:b 2}} :print-level 3)
  ($ {{:a 1} :b} :print-level 1)
  ($ {{:a 1} :b} :print-level 2)
  ($ {{:a 1} :b} :print-level 3)

  ;; Print length
  ($ (range) :print-length 0)
  ($ (range) :print-length 1)
  ($ '(1 2 3) :print-length 0)
  ($ '(1 2 3) :print-length 1)
  ($ '(1 2 3) :print-length 2)
  ($ '(1 2 3) :print-length 3)
  ($ '(1 2 3) :print-length 4)

  ;; Print level and print length
  ($ {} :print-level 0 :print-length 0)
  ($ {} :print-level 1 :print-length 0)
  ($ {} :print-level 0 :print-length 1)
  ($ {} :print-level 1 :print-length 1)

  ($ {:a 1 :b 2} :print-level 0 :print-length 0)
  ($ {:a 1 :b 2} :print-level 1 :print-length 0)
  ($ {:a 1 :b 2} :print-level 0 :print-length 1)
  ($ {:a 1 :b 2} :print-level 1 :print-length 1)

  ;; Width
  ($ {[]
      [-1000000000000000000000000000000000000000000000000000000000000000N]}
    :max-width 72)

  ;; Reader macros
  ($ #'map)
  ($ '(#'map))
  ($ '#{#'map #'mapcat})
  ($ '{:arglists (quote ([xform* coll])) :added "1.7"})
  ($ '@(foo))
  ($ ''foo)
  ($ '~foo)

  ;; Namespace maps
  (binding [*print-namespace-maps* true] ($ {:a/b 1}))
  (binding [*print-namespace-maps* true] ($ {:a/b 1 :a/c 2}))
  (binding [*print-namespace-maps* true] ($ {:a/b 1 :c/d 2}))
  (binding [*print-namespace-maps* true] ($ {:a/b {:a/b 1}}))
  (binding [*print-namespace-maps* true] ($ {'a/b 1}))
  (binding [*print-namespace-maps* true] ($ {'a/b 1 'a/c 3}))
  (binding [*print-namespace-maps* true] ($ {'a/b 1 'c/d 2}))
  (binding [*print-namespace-maps* true] ($ {'a/b {'a/b 1}}))

  (binding [*print-namespace-maps* true] ($ #:a{:b 1 :c 2} :max-width 14))
  (binding [*print-namespace-maps* true] ($ #{'a/b 1 'a/c 2} :max-width 14))

  ;; Custom tagged literals
  ($ #time/date "2023-10-02"))

(deftest pprint-meta
  ;; clojure.pprint prints this incorrectly with meta
  (binding [*print-meta* true *print-readably* false]
    (is (= "{:a 1}\n" (pp-str (with-meta {:a 1} {:b 2})))))

  (binding [*print-meta* true]
    (is (= "{:a 1}\n" (pp-str (with-meta {:a 1} {}))))))

(defrecord R [x])

(deftest pprint-record
  (is (= (with-out-str (prn (->R 1)))
        (with-out-str (sut/pprint (->R 1))))))

(deftype T
  [xs]
  clojure.lang.Associative
  (assoc [_ k v]
    (T. (.assoc xs k v))))

(def obj-re
  #"#object\[tab.impl.pprint_test.T 0[xX][0-9a-fA-F]+ \"tab.impl.pprint_test.T@[0-9a-fA-F]+\"\]\n")

(deftest pprint-custom-type
  (is (re-matches obj-re (with-out-str (prn (T. {:a 1})))))
  (is (re-matches obj-re (with-out-str (cpp/pprint (T. {:a 1})))))
  (is (re-matches obj-re (with-out-str (sut/pprint (T. {:a 1})))))

  (binding [*print-level* 0]
    (is (re-matches obj-re (with-out-str (sut/pprint (T. {:a 1})))))))
