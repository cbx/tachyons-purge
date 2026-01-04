(ns tachyons-purge.css-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [tachyons-purge.css :as css]))

(deftest parse-css-blocks-test
  (testing "parses simple CSS rules"
    (let [css ".pa0 { padding: 0; }\n.pa3 { padding: 1rem; }"
          blocks (css/parse-css-blocks css)]
      (is (= (count blocks) 2))
      (is (= (:class (first blocks)) "pa0"))
      (is (= (:class (second blocks)) "pa3")))))

(deftest parse-media-blocks-test
  (testing "parses @media blocks"
    (let [css "@media screen and (min-width: 30em) {\n  .pa3-ns { padding: 1rem; }\n}"
          blocks (css/parse-css-blocks css)]
      (is (= (count blocks) 1))
      (is (= (:class (first blocks)) "pa3-ns"))
      (is (str/includes? (:media (first blocks)) "min-width: 30em")))))

(deftest filter-css-test
  (testing "filters CSS to only used classes"
    (let [css ".pa0 { padding: 0; }\n.pa3 { padding: 1rem; }\n.ma3 { margin: 1rem; }"
          used #{"pa3"}
          result (css/filter-css css used)]
      (is (str/includes? result ".pa3"))
      (is (not (str/includes? result ".pa0")))
      (is (not (str/includes? result ".ma3"))))))

(deftest filter-css-with-media-test
  (testing "filters CSS and reconstructs @media blocks"
    (let [css ".pa0 { padding: 0; }\n@media screen and (min-width: 30em) {\n  .pa3-ns { padding: 1rem; }\n  .ma3-ns { margin: 1rem; }\n}"
          used #{"pa3-ns"}
          result (css/filter-css css used)]
      (is (str/includes? result "@media"))
      (is (str/includes? result ".pa3-ns"))
      (is (not (str/includes? result ".pa0")))
      (is (not (str/includes? result ".ma3-ns"))))))
