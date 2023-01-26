(ns tab.handler
  "HTTP request handler functions."
  (:require [clojure.java.io :as io]
            [tab.clip :as clip]
            [tab.db :as db]
            [tab.html :refer [$] :as html]
            [tab.tabulator :as tabulator]
            [tab.template :as template])
  (:import (java.util UUID)
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
  [{:keys [db] :as request}]
  (if-some [data (db/peek db)]
    (html-response request (tabulator/tabulate data db))
    (let [[_ val] (db/put! db '(tap> :hello-world) {:history? true})]
      (html-response request (tabulator/tabulate val db)))))

(defn ^:private a-val
  [{:keys [db matches] :as request}]
  (let [offset (-> matches first Long/parseLong)
        data (db/nthlast db offset)]
    (if (nil? data)
      {:status 302
       :headers {"Location" "/"}}
      (let [main (tabulator/tabulate (assoc data :offset offset) db)]
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

(defn ^:private item
  [{db :db [uuid] :matches headers :headers :as request}]
  (try
    (let [uuid (UUID/fromString uuid)]
      (if-some [data (db/pull db uuid)]
        {:status 200
         :headers {"Content-Type" "text/html; charset=utf-8"
                   "Cache-Control" "max-age=86400, immutable"}
         :body (if (contains? headers "bx-request")
                 (html/html
                   (tabulator/tabulate data db))
                 (html/page
                   (template/page request
                     (tabulator/tabulate data db))))}
        {:status 410
         :headers {"Content-Type" "text/html; charset=utf-8"
                   "Cache-Control" "max-age=86400"}
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
                 ($ :p "That doesn't look like a UUID to me.")))})))

(defn ^:private table
  [{db :db [uuid] :matches :as request}]
  (try
    (let [uuid (UUID/fromString uuid)]
      (if-some [{:keys [val]} (db/pull db uuid)]
        {:status 200
         :headers {"Content-Type" "text/html; charset=utf-8"
                   "Cache-Control" "max-age=86400, immutable"}
         :body (html/html (tabulator/-tabulate val db 0))}
        {:status 404}))
    (catch IllegalArgumentException _
      {:status 400
       :headers {"Content-Type" "text/html; charset=utf-8"}
       :body (html/page
               (template/error-page request
                 ($ :h1 "You messed up.")
                 ($ :p "That doesn't look like a UUID to me.")))})))

(defn ^:private empty-db
  [{db :db }]
  (db/evacuate! db)
  {:status 303
   :headers {"Location" "/"}})

(defn ^:private clip
  [{db :db [uuid] :matches}]
  (let [uuid (UUID/fromString uuid)]
    (case (some-> (db/pull db uuid) :val (clip/copy))
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
        (let [matches (some->> uri (re-matches route-pattern))]
          (and (= route-method method)
            matches
            (assoc request :matches (rest matches)))))
      request

      [:get #"^/$"] :>> index
      [:get #"^/id/(.+)$"] :>> item
      [:get #"^/table/(.+)$"] :>> table
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
