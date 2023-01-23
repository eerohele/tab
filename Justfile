test:
  clojure -A:dev -X cognitect.test-runner.api/test
  clojure -X:dev user/check! '{:path "test"}'

spec:
  clojure -J-Dtab.e2e.browser=webkit -X:dev user/check! '{:path "spec"}'
  clojure -J-Dtab.e2e.browser=firefox -X:dev user/check! '{:path "spec"}'
  clojure -J-Dtab.e2e.browser=chromium -X:dev user/check! '{:path "spec"}'

bump-sha:
  clojure -A:dev -X user/bump-sha
