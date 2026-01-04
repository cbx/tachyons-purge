(ns tachyons-purge.css-test
  (:require [clojure.test :refer [deftest is testing]]
            [tachyons-purge.css :as css]))

(deftest parse-css-blocks-test
  (testing "parses simple CSS rules"
    (let [css ".pa0 { padding: 0; }\n.pa3 { padding: 1rem; }"
          blocks (css/parse-css-blocks css)]
      (is (= (count blocks) 2))
      (is (= (:class (first blocks)) "pa0"))
      (is (= (:class (second blocks)) "pa3")))))
