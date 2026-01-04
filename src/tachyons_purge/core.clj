(ns tachyons-purge.core
  (:require [clojure.tools.cli :refer [parse-opts]]))

(def cli-options
  [["-c" "--css PATH" "Path to tachyons.css (default: bundled)"]
   ["-o" "--out PATH" "Output to file instead of stdout"]
   ["-m" "--minify" "Minify output"]
   ["-e" "--extensions EXTS" "File extensions to scan"
    :default "clj,cljs,cljc,html"]
   ["-v" "--verbose" "Show detailed stats"]
   ["-h" "--help" "Show help"]])

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) (println "tachyons-purge [options] <directory>\n\nOptions:\n" summary)
      errors (do (doseq [e errors] (println e)) (System/exit 1))
      (empty? arguments) (do (println "Error: directory required") (System/exit 1))
      :else (println "TODO: purge" (first arguments) options))))
