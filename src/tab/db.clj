(ns tab.db
  "Tab's in-memory database."
  (:import (java.util UUID)
           (java.time LocalDateTime)))

(defn uuid
  "Return a UUID."
  []
  (UUID/randomUUID))

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

(defn put!
  "Given a database and a value, put the value into the database."
  ([db val]
   (put! db (uuid) val {:latest? false}))
  ([db val opts]
   (put! db (uuid) val opts))
  ([db id val {:keys [latest?] :or {latest? false}}]
   (when id
     (let [data {:inst (LocalDateTime/now) :val val}]
       (swap! db (fn [db data]
                   (->
                     db
                     (assoc-in [:vals id] data)
                     (cond-> latest? (assoc :latest-id id))))
         data)
       [id data]))))

(defn pull
  "Given a database and an ID, pull the value with the given ID from the
  database."
  [db id]
  (get-in @db [:vals id]))

(defn latest-id
  "Given a database, get the ID of the latest val in the database."
  [db]
  (get @db :latest-id))

(defn size
  [db]
  (count (:vals @db)))

(comment
  (def db (pristine))
  (def a (put! db {:a 1}))
  (pull db (first a))
  (deref db)
  (def b (put! db {:b 2}))
  (pull db (first b))
  (deref db)
  (latest-id db)
  (def c (put! db {:c 3} :latest? true))
  ,,,)
