(ns tachyons-purge.emit
  (:require [clojure.string :as str]))

(defn calc-stats
  "Calculate size reduction statistics"
  [original-bytes purged-bytes class-count]
  {:original-bytes original-bytes
   :purged-bytes purged-bytes
   :class-count class-count
   :reduction-pct (Math/round (* 100.0 (/ (- original-bytes purged-bytes) original-bytes)))})

(defn minify
  "Minify CSS by removing whitespace"
  [css]
  (-> css
      (str/replace #"\s*\{\s*" "{")
      (str/replace #"\s*\}\s*" "}")
      (str/replace #"\s*:\s*" ":")
      (str/replace #"\s*;\s*" ";")
      (str/replace #";\}" "}")
      (str/replace #"\n+" "")))

(defn format-bytes
  "Format bytes as human readable"
  [bytes]
  (cond
    (>= bytes 1024) (format "%.1fKB" (/ bytes 1024.0))
    :else (str bytes "B")))

(defn print-stats
  "Print statistics to stderr"
  [stats file-count]
  (binding [*out* *err*]
    (println (format "Scanned %d files" file-count))
    (println (format "Found %d unique Tachyons classes" (:class-count stats)))
    (println (format "Original: %s -> Purged: %s (%d%% reduction)"
                     (format-bytes (:original-bytes stats))
                     (format-bytes (:purged-bytes stats))
                     (:reduction-pct stats)))))
