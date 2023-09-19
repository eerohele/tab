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
  (tab> [this x] "Broadcast a value to all clients the Tab serves.")
  (address [this] "Return the address the given Tab listens on.")
  (halt [this] "Halt the Tab."))

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
      Maximum number of items of a non-map coll to show. Use UI controls to
      show the rest of the items.

    :print-level (default: *print-level* or 2)
      Tab shows every nested object whose nesting level exceeds this
      value as collapsed.

      To expand a collapsed object, click on the plus sign."
  [& {:keys [init-val add-tap? browse? print-length print-level]
      :as opts
      :or {init-val '(tap> :hello-world)
           add-tap? true
           browse? true
           print-length 8
           print-level 2}}]
  (let [print-length (or print-length *print-length*)
        print-level (or print-level *print-level*)

        db (doto (db/pristine) (db/merge! (datafy/datafy init-val) {:history? true}))

        !watches (atom [])

        http-server
        (http/serve
          (fn [request]
            (binding [*print-length* print-length
                      *print-level* print-level]
              (handler/handle (assoc request :tab/db db))))
          opts)

        send-event
        (fn send
          ([x]
           (send x {:history? true}))
          ([x {:keys [history?]}]
           (binding [*print-length* print-length
                     *print-level* print-level]
             (let [[id data] (db/merge! db (datafy/datafy x) {:history? history?})]
               (http/broadcast http-server
                 (format "id: %s\nevent: tab\ndata: {\"history\": %s, \"html\": \"%s\"}\n\n" id
                   history?
                   (base64/encode (html/html (tabulator/tabulation data db)))))))

           (when (instance? clojure.lang.IRef x)
             (add-watch x :tab (fn [_ _ _ n] (send n {:history? false})))
             (swap! !watches conj x))))]

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
