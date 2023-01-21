(ns tab.db
  "Tab's in-memory database."
  (:import (java.util UUID)))

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
  [db val]
  (let [id (uuid)]
    (swap! db assoc id val)
    id))

(defn pull
  "Given a database and an ID, pull the value with the given ID from the
  database."
  [db id]
  (get @db id))

(comment
  (def db (pristine))
  (def uuid (put! db {:a 1}))
  (deref db)
  (pull db uuid)
  ,,,)
