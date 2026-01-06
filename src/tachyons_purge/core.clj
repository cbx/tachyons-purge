(ns tachyons-purge.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [tachyons-purge.scan :as scan]
            [tachyons-purge.css :as css]
            [tachyons-purge.emit :as emit]))

(def cli-options
  [["-c" "--css PATH" "Path to tachyons.css (default: bundled)"]
   ["-o" "--out PATH" "Output to file instead of stdout"]
   ["-m" "--minify" "Minify output"]
   ["-e" "--extensions EXTS" "File extensions to scan"
    :default "clj,cljs,cljc,html"]
   ["-v" "--verbose" "Show detailed stats"]
   ["-h" "--help" "Show help"]])

(defn get-css-content
  "Get CSS content from path or bundled resource"
  [css-path]
  (if css-path
    (slurp css-path)
    (slurp (io/resource "tachyons.min.css"))))

(defn parse-extensions
  "Parse comma-separated extensions into set"
  [s]
  (set (str/split s #",")))

(defn purge
  "Main purge logic"
  [{:keys [dir css-path extensions minify? verbose? out-path]}]
  (let [exts (parse-extensions extensions)
        files (scan/find-files dir exts)
        classes (scan/scan-directory dir exts)
        original-css (get-css-content css-path)
        filtered-css (css/filter-css original-css classes)
        final-css (if minify? (emit/minify filtered-css) filtered-css)
        stats (emit/calc-stats (count original-css) (count final-css) (count classes))]
    (when verbose?
      (emit/print-stats stats (count files)))
    (if out-path
      (spit out-path final-css)
      (println final-css))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options)
      (println (str "tachyons-purge [options] <directory>\n\nOptions:\n" summary))

      errors
      (do (doseq [e errors] (println e)) (System/exit 1))

      (empty? arguments)
      (println (str "tachyons-purge [options] <directory>\n\nOptions:\n" summary))

      :else
      (let [dir (first arguments)]
        (if-not (.isDirectory (io/file dir))
          (do (println "Error: Directory not found:" dir) (System/exit 1))
          (purge {:dir dir
                  :css-path (:css options)
                  :extensions (:extensions options)
                  :minify? (:minify options)
                  :verbose? (:verbose options)
                  :out-path (:out options)}))))))
