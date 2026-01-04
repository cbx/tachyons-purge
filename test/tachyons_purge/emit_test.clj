(ns tachyons-purge.emit-test
  (:require [clojure.test :refer [deftest is testing]]
            [tachyons-purge.emit :as emit]))

(deftest format-stats-test
  (testing "formats reduction stats"
    (let [stats (emit/calc-stats 84000 2800 83)]
      (is (= (:original-bytes stats) 84000))
      (is (= (:purged-bytes stats) 2800))
      (is (= (:class-count stats) 83))
      (is (= (:reduction-pct stats) 97)))))

(deftest minify-test
  (testing "minifies CSS"
    (is (= (emit/minify ".pa3 { padding: 1rem; }\n.ma3 { margin: 1rem; }")
           ".pa3{padding:1rem}.ma3{margin:1rem}"))))
