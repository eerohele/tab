(ns tab.impl.html-test
  (:require [clojure.test :refer [deftest are]]
            [tab.impl.html :refer [$] :as html]))

(deftest html
  (are [input ret] (= ret (html/string input))
    ($ :html) "<html></html>"
    ($ :script {:src "tab.js"}) "<script src=\"tab.js\"></script>"
    ($ :meta {:charset "utf-8"}) "<meta charset=\"utf-8\"/>"
    ($ :html ($ :head) ($ :body)) "<html><head></head><body></body></html>"
    ($ :a {:href "#"}) "<a href=\"#\"></a>"
    ($ :a {:href "#"} "hello") "<a href=\"#\">hello</a>"
    ($ :span "hello") "<span>hello</span>"
    ($ :p ($ :span "hello")) "<p><span>hello</span></p>"

    ($ :p [($ :span "hello") ($ :span "world")])
    "<p><span>hello</span><span>world</span></p>"

    ($ :a {:href "#"} [($ :span "hello") ($ :span "world")])
    "<a href=\"#\"><span>hello</span><span>world</span></a>"

    ($ :a {:href "#"} ($ :span "hello") [($ :span "world")])
    "<a href=\"#\"><span>hello</span><span>world</span></a>"

    ($ :a {:href "#"} ($ :span "hello") ($ :span "world"))
    "<a href=\"#\"><span>hello</span><span>world</span></a>"

    ($ :tr ($ :th {:class "index"} "#")
      (map (fn [th] ($ :th (pr-str th))) [:foo :bar]))
    "<tr><th class=\"index\">#</th><th>:foo</th><th>:bar</th></tr>"

    ($ :p "&")
    "<p>&#38;</p>"))
