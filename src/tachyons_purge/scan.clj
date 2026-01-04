(ns tachyons-purge.scan
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(defn find-files
  "Find all files in directory matching the given extensions."
  [dir extensions]
  (->> (fs/glob dir "**")
       (filter fs/regular-file?)
       (map str)
       (filter (fn [path]
                 (some #(str/ends-with? path (str "." %)) extensions)))
       vec))

(defn extract-keyword-classes
  "Extract CSS classes from Hiccup keyword dot notation.
   E.g., :div.pa3.bg-blue extracts #{\"pa3\" \"bg-blue\"}"
  [content]
  (let [;; Match keywords like :div.class1.class2 or :span.flex.items-center
        keyword-pattern #":[\w-]+\.([\w.-]+)"
        matches (re-seq keyword-pattern content)]
    (->> matches
         (mapcat (fn [[_ classes-str]]
                   (str/split classes-str #"\.")))
         (remove str/blank?)
         set)))

(defn extract-class-attrs
  "Extract classes from :class or class= attributes"
  [content]
  (let [pattern #"(?::class|class=)\s*\"([^\"]+)\""
        matches (re-seq pattern content)]
    (->> matches
         (mapcat (fn [[_ classes]] (str/split classes #"\s+")))
         (remove str/blank?)
         set)))
