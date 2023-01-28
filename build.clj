(ns build
  (:require [clojure.string :as string]
            [clojure.tools.build.api :as build])
  (:import (java.time LocalDate)))

(defn tag
  [_]
  (let [version (format "%s.%s" (LocalDate/now) (build/git-count-revs nil))]
    (build/git-process {:git-args ["tag" "--message" version "--annotate" version]})
    {:git/tag version :git/sha (build/git-process {:git-args ["rev-parse" "--short" version]})}))

(comment
  (tag nil)
  ,,,)

(defn bump-coords
  [coords]
  (spit "README.md"
    (string/replace (slurp "README.md") #"(?im)(\{:git/tag \".+?\", :git/sha \".+?\"\})"
      (binding [*print-namespace-maps* false]
        (pr-str coords)))))

(defn release
  [_]
  (build/git-process {:git-args ["commit" "README.md" "--message" "Update README\n\n[skip ci]"]})

  (let [coords (tag nil)]
    (bump-coords coords)
    #_(build/git-process {:git-args ["push" "origin/main" "--tags"]})))

(comment
  (release nil)
  ,,,)