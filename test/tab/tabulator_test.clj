(ns tab.tabulator-test
  (:require [clojure.test :refer [deftest are]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [tab.tabulator :as tabulator]))

(deftest sort-map
  (are [input ret] (= ret (tabulator/sort-map-by-keys input))
    {} {}

    {1.1 :b
     1 :a}
    {1 :a
     1.1 :b}

    {"2.0a" :b
     "2b" :a}
    {"2b" :a
     "2.0a" :b}

    {:b 2
     :a 1}
    {:a 1
     :b 2}

    {{:b 3} 4
     {:a 1} 2}
    {{:a 1} 2
     {:b 3} 4}

    {java.util.List :list
     java.net.URI :uri}
    {java.net.URI :uri
     java.util.List :list}

    {java.net.URI :uri
     :uri java.net.URI}
    {:uri java.net.URI
     java.net.URI :uri}))

(defspec sorted-map-never-throws
  (prop/for-all [m (gen/map gen/any gen/any)]
    (let [ret (tabulator/sort-map-by-keys m)]
      (and (sorted? ret) (map? ret)))))
