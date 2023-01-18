(ns tab.log
  "Log things.")

(defmacro log
  ([& args]
   `(binding [*out* *err*]
      (apply prn ~@args))))
