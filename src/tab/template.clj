(ns tab.template
  (:require [tab.html :refer [$] :as html]))

(defn page
  [{:keys [server-id]} main]
  ($ :html {:lang "en"}
    ($ :head
      ($ :title "Tab")
      ($ :meta {:charset "utf-8"})
      ($ :link {:rel "icon" :href "/assets/images/favicon.png"})
      ($ :link {:rel "stylesheet" :href (format "/assets/css/tab.%s.css" server-id)}))
    ($ :body
      ($ :script "0")
      main
      ($ :div {:class "event-source-error"}
        ($ :p ($ :a {:href "/"} "disconnected from server, click to reload")))
      ($ :div {:class "ok"} "✓")
      ($ :script {:src (format "/assets/js/tab.%s.js" server-id) :defer "true"}))))

(comment
  (html/string (page {:server-id "foo"} "hello"))
  ,,,)

(defn error-page
  [request & content]
  (page request
    ($ :div {:class "error-page"} content)))
