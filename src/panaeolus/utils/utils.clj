(ns panaeolus.utils.utils)

(defn normalize-vector [arr low high]
  (let [min-val   (apply min arr)
        max-val   (apply max arr)
        old-delta (- max-val min-val)
        new-delta (- high low)]
    (reduce (fn [i v]
              (conj i (float
                       (+ (* new-delta
                             (/ (- v min-val) max-val))
                          low))))
            [] arr)))
