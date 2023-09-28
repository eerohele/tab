(ns tab.impl.pprint-test
  (:require [clojure.pprint :as cpp]
            [clojure.test :refer [deftest is]]
            [tab.impl.pprint :as sut]))

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

  ;; Max width
  ($ {:a 1 :b 2 :c 3 :d 4} :max-width 0)

  ;; Meta
  ($ (with-meta {:a 1} {:b 2}) :print-meta true)
  ($ (with-meta {:a 1} {:b 2}) :print-meta true {:max-width 2})

  ;; Print level
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
  ($ {:a 1 :b 2} :print-level 1 :print-length 1))

(deftest pprint-meta
  ;; clojure.pprint prints this incorrectly with meta
  (binding [*print-meta* true *print-readably* false]
    (is (= "{:a 1}\n" (pp-str (with-meta {:a 1} {:b 2}))))))
