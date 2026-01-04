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
