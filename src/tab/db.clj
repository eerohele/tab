(ns tab.db
  (:import (java.util UUID)))

(defn uuid
  []
  (UUID/randomUUID))

(defn pristine
  []
  (atom {}))

(defn evacuate!
  [db]
  (reset! db {})
  db)

(defn put!
  [db val]
  (let [id (uuid)]
    (swap! db assoc id val)
    id))

(defn evict!
  [db id]
  (swap! db dissoc id))

(defn pull
  [db id]
  (get @db id))

(defn extract!
  [db id]
  (let [data (pull db id)]
    (evict! db id)
    data))

(comment
  (def db (pristine))
  (def uuid (put! db {:a 1}))
  (deref db)
  (extract! db uuid)
  (deref db)
  ,,,)