(ns panaeolus.live-code-arguments)

(set! *warn-on-reflection* true)

(defn resolve-arg-indicies
  [{:keys [args index a-index next-timestamp timestamp-after-next every]}]
  (let [dur (if timestamp-after-next
              (- timestamp-after-next next-timestamp)
              every)]
    (reduce (fn [init val]
              (let [maybe-derefed (if (instance? clojure.lang.Atom val) (deref val) val)
                    maybe-called (if (fn? maybe-derefed)
                                   (maybe-derefed
                                    {:dur       dur
                                     :index     index          :a-index a-index
                                     :timestamp next-timestamp :args    args})
                                   maybe-derefed)]
                (if-not (sequential? maybe-called)
                  (conj init maybe-called)
                  ;; (prn (nth val (mod a-index (count val))) val a-index)
                  (conj init (nth maybe-called (mod a-index (count maybe-called)))))))
            (if every [:dur (float dur)] [])
            args)))

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
