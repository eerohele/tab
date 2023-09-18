(ns tab.db-test
  (:require [clojure.spec.alpha :as spec]
            [clojure.test :refer [deftest are is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [tab.db :as db]))

(defn ^:private hash->map
  [xs]
  (into {} (map (juxt hash identity)) xs))

(comment (hash->map [{:a 1} {:b 2}]) ,,,)

(deftest collect
  (are [input ret] (= (hash->map ret) (db/collect input))
    {} #{{}}
    [] #{[]}
    '() #{'()}
    #{} #{#{}}
    1 #{1}
    "foo" #{"foo"}

    ;; TODO: Char array hashes are not consistent; not sure what to do with
    ;; these.
    #_ #_ (char-array "foo") #{(char-array "foo")}

    '[[a b] [c d]] #{'[[a b] [c d]] '[a b] '[c d] 'a 'b 'c 'd}

    {:a (lazy-seq '({:b {:c 3}}))}
    #{{:a '({:b {:c 3}})}
      :a
      '({:b {:c 3}})
      {:b {:c 3}}
      :b
      {:c 3}
      :c 3})

  (are [print-level print-length input ret]
    (= (hash->map ret) (binding [*print-length* print-length *print-level* print-level] (db/collect input)))

    ;; Returns hashes through (inc *print-level*)
    0 nil {} #{{}}
    0 nil {:a 1} #{{:a 1} :a 1}
    1 nil {:a 1} #{{:a 1} :a 1}
    0 nil {:a {:b 2}} #{{:a {:b 2}} :a}
    1 nil {:a {:b 2}} #{{:a {:b 2}} :a {:b 2} :b 2}
    2 nil {:a {:b 2}} #{{:a {:b 2}} :a {:b 2} :b 2}
    nil nil {:a {:b 2}} #{{:a {:b 2}} :a {:b 2} :b 2}

    ;; Returns hashes up to *print-length*
    0 0 [] #{[]}
    0 0 [{:a 1}] #{[{:a 1}]}
    0 0 [{:a 1} {:b 2}] #{[{:a 1} {:b 2}]}
    0 1 [{:a 1} {:b 2}] #{[{:a 1} {:b 2}] {:a 1} :a 1}
    0 1 [{:a {:b 2}}] #{[{:a {:b 2}}] {:a {:b 2}} :a}
    1 1 [{:a {:b 2}}] #{[{:a {:b 2}}] {:a {:b 2}} :a {:b 2} :b 2}
    0 0 [[:a :b]] #{[[:a :b]]}
    nil nil [:a :b :c] #{[:a :b :c] :a :b :c}))

(spec/def ::ret-map
  (spec/map-of int? any? :min 1))

(defspec collect-always-returns-map
  (prop/for-all [x (gen/one-of
                     [(gen/map gen/any gen/any)
                      (gen/list gen/any)
                      (gen/vector gen/any)
                      (gen/set gen/any)
                      gen/string
                      gen/int
                      gen/keyword
                      gen/symbol])]
    (spec/valid? ::ret-map (db/collect x))))

(defn ^:private pull-by-hash
  [db x]
  (:val (db/pull db (hash x))))

(deftest merge!
  (let [db (db/pristine)]
    (db/merge! db {:a 1})
    (is (= {:a 1} (pull-by-hash db {:a 1}))))

  (testing "Respects *print-level* (via collect)"
    (let [db (db/pristine)]
      (binding [*print-level* 1] (db/merge! db {:a {:b {:c 3}}}))
      (is (= {:b {:c 3}} (pull-by-hash db {:b {:c 3}})))
      (is (nil? (pull-by-hash db {:c 3})))
      (is (nil? (pull-by-hash db :c)))
      (is (nil? (pull-by-hash db 3)))))

  (testing "Respects *print-length* (via collect)"
    (let [db (db/pristine)]
      (binding [*print-length* 1] (db/merge! db [:a :b]))
      (is (= [:a :b] (pull-by-hash db [:a :b])))
      (is (= :a (pull-by-hash db :a)))
      (is (nil? (pull-by-hash db :b)))))

  (testing "History"
    (let [db (db/pristine)]
      (db/merge! db {:a 1} {:history? true})
      (is (= {:a 1} (:val (db/peek db)))))))
