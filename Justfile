test:
  clojure -A:dev -X cognitect.test-runner.api/test
  clojure -X:dev user/check! :path "test"

spec:
	clojure -X:dev user/check! :path "spec"
