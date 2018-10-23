(ns panaeolus.live-code-arguments)

(defn resolve-arg-indicies [args index a-index next-timestamp]
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
