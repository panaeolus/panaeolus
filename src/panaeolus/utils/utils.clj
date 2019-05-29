(ns panaeolus.utils.utils)

(set! *warn-on-reflection* true)

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

(defn seperate-fx-args
  "Seperate fx from rest of the arguments"
  [args]
  (loop [args            args
         args-without-fx []
         fx              []]
    (if (empty? args)
      [args-without-fx (if (sequential? fx) fx [fx])]
      (if (and (= :fx (first args)) (< 1 (count args)))
        (recur (rest (rest args))
               args-without-fx
               (second args))
        (recur (rest args)
               (conj args-without-fx (first args))
               fx)))))

(defn extract-beats [args]
  (let [beats (second args)]
    (if (number? beats)
      [beats]
      (if (sequential? beats)
        beats
        (if (fn? beats)
          beats
          (throw (AssertionError. beats " must be vector, list or number.")))))))

(defn index-position-of
  "returns the index of pred in a collection,
  otherwise nil"
  [pred coll]
  (first (keep-indexed
          (fn [idx x]
            (when (pred x)
              idx))
          coll))  )
