(ns panaeolus.live-code-arguments
  (:require [panaeolus.metronome :as metronome]))

(set! *warn-on-reflection* true)

(defn resolve-arg-indicies [args index a-index next-timestamp timestamp-after-next]
  (let [bpm (metronome/get-bpm)
        dur (* (- timestamp-after-next next-timestamp)
               (/ 60 bpm))]
    (reduce (fn [init val]
              (if (fn? val)
                (conj init (val {:dur       dur
                                 :index     index          :a-index a-index
                                 :timestamp next-timestamp :args    args}))
                (if-not (sequential? val)
                  (conj init val)
                  ;; (prn (nth val (mod a-index (count val))) val a-index)
                  (conj init (nth val (mod a-index (count val)))))))
            []
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
