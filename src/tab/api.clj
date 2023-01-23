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
            [tab.http :as http]))

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
  [& {:keys [init-val add-tap? browse? print-length print-level]
      :as opts
      :or {init-val '(tap> :hello-world)
           add-tap? true
           browse? true}}]
  (let [print-length (or print-length *print-length* 8)
        print-level (or print-level *print-level* 2)

        db (doto (db/pristine) (db/put! (datafy/datafy init-val) {:latest? true}))

        !watches (atom [])

        http-server
        (http/serve
          (fn [request]
            (binding [*print-length* print-length
                      *print-level* print-level
                      tabulator/*ann* (memoize annotate/annotate)]
              (handler/handle (assoc request :db db))))
          opts)

        send-event
        (fn send [x]
          (binding [*print-length* print-length
                    *print-level* print-level]
            (let [[id data] (db/put! db (datafy/datafy x) {:latest? true})]
              (http/broadcast http-server
                (format "id: %s\nevent: tab\ndata: %s\n\n" id
                  (base64/encode (html/html (tabulator/tabulate data db)))))))

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
