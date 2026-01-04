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

(deftest extract-keyword-classes-test
  (testing "extracts classes from keyword dot notation"
    (is (= (scan/extract-keyword-classes "[:div.pa3.bg-blue \"hello\"]")
           #{"pa3" "bg-blue"}))
    (is (= (scan/extract-keyword-classes "[:nav.flex.items-center.justify-between]")
           #{"flex" "items-center" "justify-between"}))
    (is (= (scan/extract-keyword-classes "[:div {:class \"pa3\"}]")
           #{}))))

(deftest extract-class-attrs-test
  (testing "extracts classes from :class attributes"
    (is (= (scan/extract-class-attrs "[:div {:class \"flex items-center\"}]")
           #{"flex" "items-center"}))
    (is (= (scan/extract-class-attrs "[:div {:class \"pa3\"} \"text\"]")
           #{"pa3"})))
  (testing "extracts classes from HTML class attributes"
    (is (= (scan/extract-class-attrs "<div class=\"bg-blue white\">")
           #{"bg-blue" "white"}))))

(deftest extract-string-literals-test
  (testing "extracts potential classes from string literals"
    (is (= (scan/extract-string-literals "(str \"pa3 \" (when x \"bg-blue\"))")
           #{"pa3" "bg-blue"}))
    (is (= (scan/extract-string-literals "(cond-> \"pa3\" x (str \" ma2\"))")
           #{"ma2" "pa3"}))))

(deftest extract-classes-test
  (testing "combines all extraction methods"
    (let [content "[:div.pa3.flex {:class \"items-center bg-blue\"}
                    (when active? \"bg-green\")]"]
      ;; Note: active? won't be extracted by extract-string-literals because
      ;; it contains '?' which doesn't match the CSS class pattern
      (is (= (scan/extract-classes content)
             #{"pa3" "flex" "items-center" "bg-blue" "bg-green"})))))
