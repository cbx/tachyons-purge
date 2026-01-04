(ns tachyons-purge.css
  (:require [clojure.string :as str]))

(defn parse-css-blocks
  "Parse CSS into blocks with class names and rules"
  [css-content]
  (let [pattern #"\.([a-zA-Z0-9_-]+)\s*\{[^}]+\}"
        matches (re-seq pattern css-content)]
    (mapv (fn [[rule class-name]]
            {:class class-name
             :rule rule
             :media nil})
          matches)))
