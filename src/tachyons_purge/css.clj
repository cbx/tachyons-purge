(ns tachyons-purge.css
  (:require [clojure.string :as str]))

(defn- extract-classes-from-selector
  "Extract all class names from a CSS selector (handles comma-separated)"
  [selector]
  (let [pattern #"\.([a-zA-Z0-9_-]+)"
        matches (re-seq pattern selector)]
    (mapv second matches)))

(defn- parse-rules-with-media
  "Parse CSS rules from content and associate with media query (or nil).
   Handles combined selectors like .center,.mr-auto{...} by creating
   a block entry for each class in the selector."
  [content media]
  (let [;; Match full rule including combined selectors
        pattern #"([.a-zA-Z0-9_,\s-]+)\{([^}]+)\}"
        matches (re-seq pattern content)]
    (->> matches
         (mapcat (fn [[full-rule selector body]]
                   (let [classes (extract-classes-from-selector selector)]
                     (for [class-name classes]
                       {:class class-name
                        :rule (str "." class-name "{" body "}")
                        :media media}))))
         vec)))

(defn- extract-media-blocks
  "Extract @media blocks from CSS, returning pairs of [media-query content]"
  [css-content]
  (let [pattern #"@media\s*([^{]+)\s*\{((?:[^{}]|\{[^{}]*\})*)\}"
        matches (re-seq pattern css-content)]
    (mapv (fn [[_ query content]]
            [(str "@media " (str/trim query)) content])
          matches)))

(defn- remove-media-blocks
  "Remove @media blocks from CSS content"
  [css-content]
  (str/replace css-content #"@media\s*[^{]+\s*\{(?:[^{}]|\{[^{}]*\})*\}" ""))

(defn parse-css-blocks
  "Parse CSS into blocks with class names and rules"
  [css-content]
  (let [;; Parse rules outside @media blocks
        non-media-content (remove-media-blocks css-content)
        non-media-rules (parse-rules-with-media non-media-content nil)
        ;; Parse rules inside @media blocks
        media-blocks (extract-media-blocks css-content)
        media-rules (mapcat (fn [[media content]]
                              (parse-rules-with-media content media))
                            media-blocks)]
    (vec (concat non-media-rules media-rules))))

(defn filter-css
  "Filter CSS content to only include rules for used classes"
  [css-content used-classes]
  (let [blocks (parse-css-blocks css-content)
        filtered (filter #(contains? used-classes (:class %)) blocks)
        grouped (group-by :media filtered)]
    (str/join "\n"
      (concat
        ;; Non-media rules first
        (map :rule (get grouped nil))
        ;; Then media rules grouped (note: :media already includes "@media ")
        (for [[media rules] (dissoc grouped nil)]
          (str media " {\n"
               (str/join "\n" (map #(str "  " (:rule %)) rules))
               "\n}"))))))
