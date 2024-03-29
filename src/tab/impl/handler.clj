(ns tab.impl.handler
  "HTTP request handler functions."
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [tab.impl.clip :as clip]
            [tab.impl.db :as db]
            [tab.impl.html :refer [$] :as html]
            [tab.impl.tabulator :as tabulator]
            [tab.impl.template :as template])
  (:import (java.net URI)
           (java.util.concurrent ArrayBlockingQueue)))

(set! *warn-on-reflection* true)

(defn ^:private html-response
  [request body]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (html/page (template/page request body))})

(defn ^:private not-found
  [_]
  {:status 404
   :body "Not found"})

(defn ^:private index
  [{db :tab/db :as request}]
  (if-some [data (db/peek db)]
    (html-response request (tabulator/tabulation data db))
    (let [[_ data] (db/merge! db '(tap> :hello-world) {:history? true})]
      (html-response request (tabulator/tabulation data db)))))

(defn ^:private a-val
  [{db :tab/db [offset] :matches :as request}]
  (let [offset (Long/parseLong offset)
        data (db/nthlast db offset)]
    (if (nil? data)
      {:status 302
       :headers {"Location" "/"}}
      (let [main (tabulator/tabulation (assoc data :offset offset) db)]
        (html-response request main)))))

(defn ^:private js-asset
  [_]
  {:status 200
   :headers {"Content-Type" "text/javascript; charset=utf-8"
             "Cache-Control" "max-age=31536000"}
   :body (io/input-stream (io/resource "tab.js"))})

(defn ^:private css-asset
  [_]
  {:status 200
   :headers {"Content-Type" "text/css; charset=utf-8"
             "Cache-Control" "max-age=31536000"}
   :body (io/input-stream (io/resource "tab.css"))})

(defn ^:private image-asset
  [_]
  {:status 200
   :headers {"Content-Type" "text/css; charset=utf-8"
             "Cache-Control" "max-age=31536000"}
   :body (io/input-stream (io/resource "favicon.png"))})

(defn ^:private event-source
  [_]
  {:status 200
   :headers {"Content-Type" "text/event-stream"
             "Cache-Control" "no-cache, must-revalidate, max-age=0"
             "Connection" "keep-alive"}
   :body (ArrayBlockingQueue. 1024)})

(defn ^:private parse-query-params
  [uri]
  (into {}
    (map #(string/split % #"="))
    (some->
      (URI. uri)
      (.getQuery)
      (string/split #"\?"))))

(defn ^:private parse-print-length
  "Given a string URI, return the value specified by the print-length query parameter."
  [uri]
  (let [print-length (some-> (parse-query-params uri) (get "print-length") read-string)]
    (if (= '*print-length* print-length) *print-length* print-length)))

(defn ^:private item
  [{db :tab/db [hash-code] :matches uri :uri headers :headers :as request}]
  (try
    (let [hash-code (Integer/parseInt hash-code)]
      (if-some [data (db/pull db hash-code)]
        {:status 200
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :body (binding [*print-length* (parse-print-length uri)]
                 (if (contains? headers "bx-request")
                   (html/string
                     (tabulator/tabulation data db))
                   (html/page
                     (template/page request
                       (tabulator/tabulation data db)))))}
        {:status 410
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :body (html/page
                 (template/error-page request
                   ($ :h1 "This value is no longer available.")
                   ($ :p "Resend the value to Tab to inspect it again.")
                   ($ :p ($ :a {:href "/"} "Go back to the start."))))}))
    (catch IllegalArgumentException _
      {:status 400
       :headers {"Content-Type" "text/html; charset=utf-8"}
       :body (html/page
               (template/error-page request
                 ($ :h1 "You messed up.")
                 ($ :p "That doesn't look like a hash code to me.")))})))

(defn ^:private toggle
  [{db :tab/db [hash-code] :matches uri :uri :as request}]
  (try
    (let [hash-code (Integer/parseInt hash-code)]
      (if-some [{:keys [val]} (db/pull db hash-code)]
        (binding [*print-length* (parse-print-length uri)]
          (db/merge! db val)
          {:status 200
           :headers {"Content-Type" "text/html; charset=utf-8"
                     "BX-Replace-Url" (format "/id/%d?print-length=%s" hash-code (if (nil? *print-length*) "nil" *print-length*))}
           :body (html/string (tabulator/-tabulate val 0))})
        {:status 404}))
    (catch IllegalArgumentException _
      {:status 400
       :headers {"Content-Type" "text/html; charset=utf-8"}
       :body (html/page
               (template/error-page request
                 ($ :h1 "You messed up.")
                 ($ :p "That doesn't look like a hash code to me.")))})))

(defn ^:private empty-db
  [{db :tab/db}]
  (db/evacuate! db)
  {:status 303
   :headers {"Location" "/"}})

(defn ^:private clip
  [{db :tab/db [hash-code] :matches}]
  (let [hash-code (Integer/parseInt hash-code)]
    (case (some-> (db/pull db hash-code) :val (clip/copy))
      :ok {:status 200 :body "OK"}
      {:status "400"})))

(defn handle
  "Given an ersatz Ring request map, pass it off to its designated handler, and
  return the result."
  [request]
  (if-not request
    (not-found request)
    (condp
      (fn [[route-method route-pattern] {:keys [method uri]}]
        (let [matches (some->> uri URI. .getPath (re-matches route-pattern))]
          (and (= route-method method)
            matches
            (assoc request :matches (rest matches)))))
      request

      [:get #"^/$"] :>> index
      [:get #"^/id/(.+)$"] :>> item
      [:get #"^/toggle/(-?\d+)$"] :>> toggle
      [:get #"^/assets/images/(.+)$"] :>> image-asset
      [:get #"^/assets/css/(.+)$"] :>> css-asset
      [:get #"^/assets/js/(.+)$"] :>> js-asset
      [:get #"^/event-source$"] :>> event-source
      [:get #"^/val/-(\d+)$"] :>> a-val
      [:post #"^/clip/(.+?)$"] :>> clip
      [:post #"^/db/empty$"] :>> empty-db
      [:get #".*"] :>> not-found
      not-found)))

(comment
  (handle {:method :get :uri "/"})
  (handle {:method :get :uri "/event-source"})
  (handle {:method :get :uri "/foo"})
  (handle {:method :get :uri "/ns/clojure.core"})
  (handle {:method :get :uri "/var/clojure.core/mapcat"})
  (handle {:method :get :uri "/class/java.lang.String"})
  (handle {:method :get :uri "/nope"})
  (handle {:method :get :uri "/assets/css/tab.css"})
  (handle {:method :get :uri "/assets/js/tab.js"})
  (handle {:method :get :uri "/assets/images/favicon.png"})
  ,,,)
