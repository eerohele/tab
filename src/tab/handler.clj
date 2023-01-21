(ns tab.handler
  "HTTP request handler functions."
  (:require [clojure.datafy :as datafy]
            [clojure.java.io :as io]
            [tab.clip :as clip]
            [tab.db :as db]
            [tab.html :refer [$] :as html]
            [tab.tabulator :as tabulator])
  (:import (java.time LocalDateTime)
           (java.util UUID)
           (java.util.concurrent ArrayBlockingQueue)))

(set! *warn-on-reflection* true)

(def ^:private favicon
  "data:image/png;base64,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")

(defn ^:private wrap-page
  [{:keys [server-id]} main]
  ($ :html {:lang "en"}
    ($ :head
      ($ :title "Tab")
      ($ :meta {:charset "utf-8"})
      ($ :link {:rel "icon" :href favicon})
      ($ :link {:rel "stylesheet" :href (format "/assets/css/tab.%s.css" server-id)}))
    ($ :body
      ($ :script "0")
      ($ :main main)
      ($ :div {:class "event-source-error"}
        ($ :p ($ :a {:href "/"} "disconnected from server, click to reload")))
      ($ :div {:class "ok"} "✓")
      ($ :script {:src (format "/assets/js/tab.%s.js" server-id) :defer "true"}))))

(comment
  (html/html (wrap-page {:server-id "foo"} "hello"))
  ,,,)

(defn ^:private html-response
  [request body]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (html/page (wrap-page request body))})

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
                 (html/page (wrap-page request main))))}
      {:status 200
       :headers {"Content-Type" "text/html; charset=utf-8"}
       :body "<span></span>"})))

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
