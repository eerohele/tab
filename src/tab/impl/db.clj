(ns tab.impl.db
  "Tab's in-memory database."
  (:refer-clojure :exclude [peek])
  (:require [clojure.core :as core])
  (:import (java.time ZonedDateTime)))

(defn pristine
  "Return an empty database."
  []
  (atom {}))

(defn evacuate!
  "Given a database, remove everything in the database.

  Return the database."
  [db]
  (reset! db {})
  db)

(defn pull
  "Given a database and an ID, pull the value with the given ID from the
  database."
  [db id]
  (get-in @db [:k->v id]))

(defn now
  []
  (ZonedDateTime/now))

(defn collect
  ([this]
   (collect this 0))
  ([this level]
   (collect {} this level))
  ([hash->obj this level]
   (cond
     (and
       (map? this)
       (int? *print-level*) (> level *print-level*))
     {}

     (map? this)
     (reduce-kv (fn [hash->obj k v]
                  (merge hash->obj
                    (collect hash->obj k (inc level))
                    (collect hash->obj v (inc level))))
       (assoc hash->obj (hash this) this)
       this)

     (coll? this) ; non-map coll
     (loop [hash->obj (assoc hash->obj (hash this) this)
            xs this
            len 0]
       (if (and (int? *print-length*) (= len *print-length*))
         hash->obj
         (if-some [x (first xs)]
           (recur (collect hash->obj x level) (rest xs) (inc len))
           hash->obj)))

     :else (assoc hash->obj (hash this) this))))

(defn merge!
  ([db val]
   (merge! db val {}))
  ([db val {:keys [history?] :or {history? false}}]
   (let [id (hash val)
         val* (collect (hash-map id val) val 0)
         now (now)
         data (reduce-kv
                (fn [m k v]
                  (-> m
                    (assoc k {:inst now :val v})))
                {}
                val*)]
     (swap! db
       (fn [db]
         (-> db
           (update :k->v merge data)
           (cond-> history?
             (update :history (fn [history id]
                                (cond
                                  (nil? history)
                                  [id]

                                  (= id (core/peek history))
                                  history

                                  :else (conj history id))) id)))))

     [id {:inst now :val val}])))

(comment
  (def db (pristine))
  (merge! db {:a {:b 1}})
  (merge! db {:c {:d 2}})
  (merge! db {:c {:d 2}} {:history? true})
  ,,,)

(defn peek
  "Given a database, get the ID of the latest val in the database."
  [db]
  (let [db @db]
    (get-in db [:k->v (core/peek (get db :history []))])))

(defn nthlast
  [db n]
  (let [db @db
        history (get db :history [])
        cnt (count history)]
    (when (< n cnt)
      (let [index (- (dec cnt) n)
            id (nth history index)]
        (get-in db [:k->v id])))))

(defn size
  [db]
  (count (:k->v @db)))

(defn history-size
  [db]
  (count (:history @db)))

(comment
  (def db (pristine))
  (merge! db {:a 1} {:history? true})
  (merge! db {:b 2} {:history? true})
  (deref db)

  (nthlast db 0)
  (nthlast db 1)
  (nthlast db 2)

  (def db (pristine))
  (def a (merge! db (hash {:a 1}) {:a 1}))
  (pull db (first a))
  (deref db)
  (def b (merge! db {:b 2}))
  (pull db (first b))
  (deref db)
  (def c (merge! db {:c 3} {:history? true}))
  ,,,)
