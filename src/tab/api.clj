(ns tab.api
  "Tab is a tool for visualizing Clojure data structures."
  (:require [clojure.datafy :as datafy]
            [clojure.java.browse :as browse]
            [tab.annotate :as annotate]
            [tab.base64 :as base64]
            [tab.db :as db]
            [tab.tabulator :as tabulator]
            [tab.handler :as handler]
            [tab.html :as html]
            [tab.http :as http])
  (:import (java.time LocalDateTime)))

(set! *warn-on-reflection* true)

(defprotocol Tab
  (tab> [this event])
  (address [this])
  (halt [this]))

(defn run
  "Run a Tab.

  Options:

    :port (default: 0, to auto-assign an available port)
      The HTTP server port to listen on.

    :init-val
      The initial value to show in Tab.

    :max-vals (default: 16)
      The maximum number of values to retain in history.

    :add-tap? (default: true)
      Whether to wire up tap> to send vals to Tab.

    :browse? (default: true)
      Whether to automatically open your default browser to show Tab.

      Reportedly does not work on Windows Subsystem for Linux.

    :print-length (default: *print-length* or 8)
      Maximum number of items of a seq to show. To show all items, click on the
      ellipsis in the UI.

    :print-level (default: *print-level* or 2)
      Tab shows every nested object whose nesting level exceeds this
      value as collapsed.

      To expand a collapsed object, click on the plus sign."
  [& {:keys [init-val max-vals add-tap? browse? print-length print-level]
      :as opts
      :or {init-val '(tap> :hello-world)
           max-vals 16
           add-tap? true
           browse? true}}]
  (let [print-length (or print-length *print-length* 8)
        print-level (or print-level *print-level* 2)

        db (db/pristine)

        !vals (atom [{:inst (LocalDateTime/now) :data (datafy/datafy init-val)}])
        !watches (atom [])

        http-server
        (http/serve
          (fn [request]
            (binding [*print-length* print-length
                      *print-level* print-level
                      tabulator/*ann* (memoize annotate/annotate)]
              (handler/handle (assoc request :db db :vals @!vals))))
          opts)

        push-val
        (fn [v]
          (swap! !vals
            (fn [vals v]
              (if (>= (count vals) max-vals)
                (conj (subvec vals 1) v)
                (conj vals v)))
            v))

        send-event
        (fn send [x]
          (binding [*print-length* print-length
                    *print-level* print-level]
            (let [val {:inst (LocalDateTime/now) :data (datafy/datafy x)}
                  data (->
                         val
                         (assoc :db db :max-offset (count (push-val val)))
                         tabulator/tabulate
                         html/html
                         base64/encode)]
              (http/broadcast http-server (format "event: tab\ndata: %s\n\n" data))))

          (when (instance? clojure.lang.IRef x)
            (add-watch x :tab (fn [_ _ _ n] (send n)))
            (swap! !watches conj x)))]

    (when add-tap? (doto send-event add-tap))
    (when browse? (browse/browse-url (http/address http-server)))

    (reify Tab
      (tab> [_ x] (send-event x))
      (address [_] (http/address http-server))
      (halt [_]
        ;; Remove any assigned watches
        (run! (fn [x] (remove-watch x :tab)) @!watches)
        (http/halt http-server)))))

(comment
  (def tab (run :port 8080))
  (tab> tab {:foo :bar})
  (tap> {:baz :quux})
  (halt tab)

  (def tab (run :port 8080 :add-tap? false))
  (tab> tab {:foo :bar})
  (tap> {:baz :quux})
  (halt tab)
  ,,,)
