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
