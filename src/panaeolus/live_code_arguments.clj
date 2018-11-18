(ns panaeolus.live-code-arguments)

(defn resolve-arg-indicies [args index a-index next-timestamp]
  ;; (prn "resolve arg indicies" args index)
  (reduce (fn [init val]
            (if (fn? val)
              (conj init (val {:index     index          :a-index a-index
                               :timestamp next-timestamp :args    args}))
              (if-not (sequential? val)
                (conj init val)
                ;; (prn (nth val (mod a-index (count val))) val a-index)
                (conj init (nth val (mod a-index (count val)))))))
          []
          args))

(defn expand-nested-vectors-to-multiarg [args]
  (let [longest-vec (->> args
                         (filter sequential?)
                         (map count)
                         (apply max))]
    (vec (for [n (range longest-vec)]
           (reduce (fn [i v]
                     (if (sequential? v)
                       (if (<= (count v) n)
                         (conj i (last v))
                         (conj i (nth v n)))
                       (conj i v))) [] args)))))
