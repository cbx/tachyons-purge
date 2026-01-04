(ns tachyons-purge.scan-test
  (:require [clojure.test :refer [deftest is testing]]
            [tachyons-purge.scan :as scan]
            [babashka.fs :as fs]))

(deftest find-files-test
  (testing "finds files with matching extensions"
    (let [tmp (fs/create-temp-dir)
          clj-file (fs/file tmp "foo.clj")
          html-file (fs/file tmp "bar.html")
          txt-file (fs/file tmp "ignore.txt")]
      (spit clj-file "(ns foo)")
      (spit html-file "<html>")
      (spit txt-file "ignore")
      (let [found (scan/find-files (str tmp) #{"clj" "html"})]
        (is (= (count found) 2))
        (is (every? #(or (.endsWith % ".clj") (.endsWith % ".html")) found)))
      (fs/delete-tree tmp))))
