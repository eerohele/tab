(ns tab.handler
  "HTTP request handler functions."
  (:require [clojure.datafy :as datafy]
            [clojure.java.io :as io]
            [tab.clip :as clip]
            [tab.db :as db]
            [tab.html :refer [$] :as html]
            [tab.tabulator :as tabulator]
            [tab.template :as template])
  (:import (java.time LocalDateTime)
           (java.util UUID)
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
  [{:keys [vals db] :as request}]
  (html-response request
    (tabulator/tabulate (assoc (peek vals) :db (db/evacuate! db) :max-offset (count vals)))))

(defn ^:private js-asset
  [_]
  {:status 200
   :headers {"Content-Type" "text/javascript; charset=utf-8"
             "Cache-Control" "private, max-age=31536000"}
   :body (io/input-stream (io/resource "tab.js"))})

(defn ^:private css-asset
  [_]
  {:status 200
   :headers {"Content-Type" "text/css; charset=utf-8"
             "Cache-Control" "private, max-age=31536000"}
   :body (io/input-stream (io/resource "tab.css"))})

(defn ^:private a-val
  [{:keys [db matches vals] :as request}]
  (let [offset (-> matches first Long/parseLong)]
    (if (>= offset (count vals))
      {:status 302
       :headers {"Location" "/"}}
      (let [item (nth vals (- (dec (count vals)) offset))
            main (tabulator/tabulate (assoc item :db (db/evacuate! db) :offset offset :max-offset (count vals)))]
        (html-response request main)))))

(defn ^:private event-source
  [_]
  {:status 200
   :headers {"Content-Type" "text/event-stream"
             "Cache-Control" "no-cache, must-revalidate, max-age=0"
             "Connection" "keep-alive"}
   :body (ArrayBlockingQueue. 1024)})

(defn ^:private a-namespace
  [{[ns-str] :matches db :db vals :vals :as request}]
  (html-response request
    (tabulator/tabulate {:db db
                         :max-offset (count vals)
                         :inst (LocalDateTime/now)
                         :data (-> ns-str read-string find-ns datafy/datafy)})))

(defn ^:private a-var
  [{[ns-str var-str]:matches db :db vals :vals :as request}]
  (html-response request
    (tabulator/tabulate {:db db
                         :max-offset (count vals)
                         :inst (LocalDateTime/now)
                         :data (datafy/datafy (ns-resolve (read-string ns-str) (read-string var-str)))})))

(defn ^:private a-class
  [{[class-str]:matches db :db vals :vals :as request}]
  (html-response request
    (tabulator/tabulate {:db db
                         :max-offset (count vals)
                         :inst (LocalDateTime/now)
                         :data (-> class-str read-string resolve datafy/datafy)})))

(defn ^:private item
  [{db :db [uuid] :matches headers :headers :as request}]
  (try
    (let [uuid (UUID/fromString uuid)
          data (db/pull db uuid)]
      (if data
        {:status 200
         :headers {"Content-Type" "text/html; charset=utf-8"
                   "Cache-Control" "no-cache"
                   "Expires" "0"}
         :body (let [main (tabulator/-tabulate data db 0)]
                 (if (contains? headers "bx-request")
                   (html/html main)
                   (html/page (template/page request main))))}
        {:status 410
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :body (html/page
                 (template/error-page request
                   ($ :h1 "This value is no longer available.")
                   ($ :p "Resend the value to Tab to inspect it again.")))}))
    (catch IllegalArgumentException ex
      {:status 400
       :headers {"Content-Type" "text/html; charset=utf-8"}
       :body (html/page
               (template/error-page request
                 ($ :h1 "You messed up.")
                 ($ :p "That doesn't look like a UUID to me.")))})))

(defn ^:private db
  [{db :db}]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (pr-str (count @db))})

(defn ^:private clip
  [{db :db [uuid] :matches}]
  (let [uuid (UUID/fromString uuid)]
    (case (clip/copy (db/pull db uuid))
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
      [:get #"^/assets/css/(.+)$"] :>> css-asset
      [:get #"^/assets/js/(.+)$"] :>> js-asset
      [:get #"^/event-source$"] :>> event-source
      [:get #"^/val/-(\d+)$"] :>> a-val
      [:get #"^/ns/(.+)$"] :>> a-namespace
      [:get #"^/var/(.+?)/(.+)$"] :>> a-var
      [:get #"^/class/(.+?)$"] :>> a-class
      [:post #"^/clip/(.+?)$"] :>> clip
      [:get #"^/db$"] :>> db
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

  (handle {:method :get :uri "/val/-1"
           :vals [{:inst (LocalDateTime/now) :data 1}
                  {:inst (LocalDateTime/now) :data 2}]})
  ,,,)
