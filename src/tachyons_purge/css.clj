(ns tachyons-purge.css
  (:require [clojure.string :as str]))

(defn- parse-rules-with-media
  "Parse CSS rules from content and associate with media query (or nil)"
  [content media]
  (let [pattern #"\.([a-zA-Z0-9_-]+)\s*\{[^}]+\}"
        matches (re-seq pattern content)]
    (mapv (fn [[rule class-name]]
            {:class class-name
             :rule rule
             :media media})
          matches)))

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
