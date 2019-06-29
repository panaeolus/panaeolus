(ns panaeolus.utils.utils-test
  (:require [clojure.test :refer :all]
            [panaeolus.utils.utils :as utils]))

(deftest process-arguments
  (let [res1 (utils/process-arguments [:a 1 :b 2 :c 6] [1 2 :a 3])
        res2 (utils/process-arguments [:dur 1 :nn 60 :amp -12]
                                      [:nn 28 :dur 2 :amp -22])
        res3 (utils/process-arguments
              [:dur 2 :g -1 :a 2 :b 3 :c 4 :d 5]
              [:dur 2 1 3 2 :c 6 8 :ignored])]
    (is (= res1 {:a 3 :b 2}))
    (is (= res2 {:nn 28 :dur 2 :amp -22}))
    (is (= res3 {:dur 2, :g 1, :a 3, :b 2, :c 6, :d 8, nil :ignored}))))

(deftest fill-missing-keys
  (let [result (utils/fill-missing-keys
                [:dur 2 1 3 2 :c 6 8 :ignored]
                [:dur 2 :g -1 :a 2 :b 3 :c 4 :d 5])]
    (testing "is every second value a keyword"
      (is (every? keyword? (take-nth 2 result))))
    (is (= result [:dur 2 :g 1 :a 3 :b 2 :c 6 :c 8]))))

(deftest hash-jack-client-to-32
  (let [result (utils/hash-jack-client-to-32 "panaeolus.csound.instr/super-saw")]
    (is (= result "pae/1166867396/super-saw"))
    (is (< (count result) 32))))
