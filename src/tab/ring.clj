(ns tab.ring
  "A very bad Ring implementation.

  See https://github.com/ring-clojure/ring for the original."
  (:require [clojure.string :as string])
  (:import (java.io InputStream OutputStream)
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
  "A date-time formatter that yields date-times for the Date HTTP header."
  (.withZone
    (DateTimeFormatter/ofPattern "EEE, dd MMM yyyy HH:mm:ss z" Locale/ENGLISH)
    (ZoneId/of "GMT")))

(defn ^:private content-length
  "Given a string, return the length of the string as a string, for the Content-
  Type HTTP header."
  [^String s]
  (pr-str (+ (alength (.getBytes s StandardCharsets/UTF_8)) 2)))

(defprotocol Writable
  (writes [this stream]))

(extend-protocol Writable
  nil (writes [_ _])

  String
  (writes [this ^OutputStream stream]
    (.write stream (.getBytes this StandardCharsets/UTF_8)))

  InputStream
  (writes [this ^OutputStream stream]
    (.transferTo this stream))

  Object
  (writes [_ _]))

(defn write-response
  "Given a java.io.OutputStream and a HTTP response map (á là Ring), write the
  response map into the writer and flush the writer."
  [^OutputStream out
   {:keys [status headers body]
    :or {status 500 headers {}}}]
  (let [write (fn [x] (writes x out))]
    ;; Status line
    (write "HTTP/1.1")
    (write " ")
    (write (str status))
    (write " ")
    (write ^String (status-text status ""))
    (write "\r\n")

    (.flush out)

    ;; Headers
    (let [headers (cond-> (assoc headers "Date" (.format date-time-formatter (Instant/now)))
                    (and (string? body) (seq body) (= 200 status))
                    ;; Body length + \r\n
                    (assoc "Content-Length" (content-length body)))]
      (when (seq headers)
        (doseq [[^String name ^String value] headers]
          (write name)
          (write ": ")
          (write value)
          (write "\r\n"))

        (write "\r\n")))

    (.flush out)

    ;; Body
    (when body
      (write body)
      (write "\r\n"))

    (.flush out)))

(comment
  (with-open [writer (java.io.ByteArrayOutputStream.)]
    (write-response writer {})
    (.toString writer))

  (with-open [writer (java.io.ByteArrayOutputStream.)]
    (write-response writer {:status 200
                            :headers {"Content-Type" "text/html"}
                            :body "<p>Hello, world!</p>"})
    (.toString writer))

  (println
    (with-open [writer (java.io.ByteArrayOutputStream.)]
      (write-response writer {:status 200
                              :headers {"Content-Type" "text/event-source; charset=utf-8"
                                        "Cache-Control" "no-store"
                                        "Connection" "keep-alive"}})
      (.toString writer)))
  ,,,)
