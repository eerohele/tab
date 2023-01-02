(ns tab.ring
  "A very bad Ring implementation.

  See https://github.com/ring-clojure/ring for the original."
  (:require [clojure.string :as string]
            [tab.log :as log])
  (:import (java.io Writer)
           (java.net SocketException)
           (java.nio.charset StandardCharsets)
           (java.time Instant ZoneId)
           (java.time.format DateTimeFormatter)
           (java.util Locale)))

(set! *warn-on-reflection* true)

(defn parse-request
  "Given a seq of strings of lines representing the lines of a HTTP request,
  return a HTTP request map (á là Ring)."
  [[start-line & rest]]
  (when start-line
    (let [[method uri] (string/split start-line #" ")
          headers (into {}
                    (keep (fn [line]
                            (when-some [colon (some-> line (string/index-of \:))]
                              (let [name (some-> line (subs 0 colon) string/lower-case)
                                    value (some-> line (subs (inc colon)) string/trim)]
                                (when (and name value) [name value])))))
                    rest)]
      {:method (-> method string/lower-case keyword)
       :uri uri
       :headers headers})))

(comment
  (parse-request
    ["GET /favicon.ico HTTP/1.1"
     "Host: localhost:8080"
     "Connection: keep-alive"
     "Accept: */*"
     "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.2 Safari/605.1.15"
     "Accept-Language: en-US,en;q=0.9"
     "Referer: http://localhost:8080/"
     "Accept-Encoding: gzip, deflate"])
  ,,,)

(def ^:private status-text
  {200 "OK"
   404 "Not Found"
   500 "Internal Server Error"})

(def ^:private ^DateTimeFormatter date-time-formatter
  (.withZone
    (DateTimeFormatter/ofPattern "EEE, dd MMM yyyy HH:mm:ss z" Locale/ENGLISH)
    (ZoneId/of "GMT")))

(defn ^:private content-length
  [^String body]
  (pr-str (+ (alength (.getBytes body StandardCharsets/UTF_8)) 2)))

(defn write-response
  [^Writer writer {:keys [status headers ^String body]
                   :or {status 500 headers {} body ""}}]
  (try
    ;; Status line
    (.write writer "HTTP/1.1")
    (.write writer " ")
    (.write writer (str status))
    (.write writer " ")
    (.write writer ^String (status-text status ""))
    (.write writer "\r\n")

    ;; Headers
    (let [headers (cond-> (assoc headers "Date" (.format date-time-formatter (Instant/now)))
                    (and (string? body) (seq body) (= 200 status))
                    ;; Body length + \r\n
                    (assoc "Content-Length" (content-length body)))]
      (when (seq headers)
        (doseq [[^String name ^String value] headers]
          (.write writer name)
          (.write writer ": ")
          (.write writer value)
          (.write writer "\r\n"))

        (.write writer "\r\n")))

    ;; Body
    (when (and (string? body) (seq body))
      (.write writer body)
      (.write writer "\r\n"))

    (.flush writer)

    (catch SocketException ex
      (log/log :fine ex))))

(comment
  (with-open [writer (java.io.StringWriter.)]
    (write-response writer {})
    (.toString writer))

  (with-open [writer (java.io.StringWriter.)]
    (write-response writer {:status 200
                            :headers {"Content-Type" "text/html"}
                            :body "<p>Hello, world!</p>"})
    (.toString writer))

  (println
    (with-open [writer (java.io.StringWriter.)]
      (write-response writer {:status 200
                              :headers {"Content-Type" "text/event-source; charset=utf-8"
                                        "Cache-Control" "no-store"
                                        "Connection" "keep-alive"}})
      (.toString writer)))
  ,,,)
