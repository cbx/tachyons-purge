(ns tachyons-purge.scan
  (:require [babashka.fs :as fs]
            [clojure.set]
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

(defn extract-string-literals
  "Extract potential CSS class names from string literals.
   Finds all quoted strings and extracts whitespace-separated tokens
   that look like CSS class names (alphanumeric with hyphens)."
  [content]
  (let [;; Match quoted strings
        string-pattern #"\"([^\"]*)\""
        matches (re-seq string-pattern content)
        ;; CSS class pattern - alphanumeric with hyphens, typical for Tachyons
        class-pattern #"^[a-z][a-z0-9-]*$"]
    (->> matches
         (mapcat (fn [[_ string-content]]
                   (str/split string-content #"\s+")))
         (map str/trim)
         (remove str/blank?)
         (filter #(re-matches class-pattern %))
         set)))

(defn extract-classes
  "Extract all potential class names from content using all methods"
  [content]
  (clojure.set/union
    (extract-keyword-classes content)
    (extract-class-attrs content)
    (extract-string-literals content)))
