(ns panaeolus.utils.utils
  (:require [clojure.string :as string]))

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
          coll)))

(defn process-arguments
  "Process the given arguments so that
   this function can be given (f 1 2 3 :somekey 4)
   where all values besides keywords are processed
   sequenceially. Keyword represent a parameter name.
   This function is called after fill-missing-keys but
   just before giving it csound's input-message-fn."
  [param-vector arg-env]
  (let [default-arg-map (apply hash-map param-vector)]
    (loop [default-params (partition 2 param-vector)
           given-params   (or arg-env [])
           param-map      {}]
      (if (empty? given-params)
        param-map
        (recur (rest default-params)
               (if (keyword? (first given-params))
                 (rest (rest given-params))
                 (rest given-params))
               (if (and (keyword? (first given-params))
                        (<= 2 (count given-params)))
                 (assoc param-map (first given-params) (second given-params))
                 (assoc param-map (ffirst default-params) (first given-params))))))))

;; (process-arguments [:a 1 :b 2 :c 6] [1 2 :a 3])
;; (process-arguments [:dur 1 :nn 60 :amp -12]  [:nn 28 :dur 2 :amp -22])

(defn fill-missing-keys
  "The keywords need to be squeezed in, along
   with calling `process-arguments`
   to resolve the arguments correctly."
  [args orig-arglists]
  (let [orig-arglists (rest (rest orig-arglists)) ; remove :dur
        ]
    (letfn [(advance-to-arg [arg orig]
              (if (= :dur arg)
                orig
                (if-let [idx (index-position-of #(= arg %) orig)]
                  (vec (subvec (into [] orig) idx))
                  orig)))]
      (loop [args     args
             orig     orig-arglists
             out-args []]
        (if (or (empty? args) ; ignore tangling keyword/value
                (and (= 1 (count args)) (keyword? (first args))))
          out-args
          (if (keyword? (first args))
            (recur (rest (rest args))
                   (advance-to-arg (first args) orig)
                   (conj out-args (first args) (second args)))
            (recur (rest args)
                   (vec (rest (rest orig)))
                   (conj out-args (first orig) (first args)))))))))

#_(defn hash-jack-client-to-32 [original-name]
  (let [hash (clojure.string/replace
              (str (.hashCode ^java.lang.String original-name)) "-" "")
        [ns & rest] (string/split original-name #"/")
        simple-name (apply str rest)
        ]
    (str "pae/" (subs hash 0 (min 10 (count hash)))
         "/"
         (subs original-name 0 (min 13 (count simple-name))))))

(defn jack-client-name-fit [names]
  (str "pae/"
       (string/join "/"
                    (map #(let [simple-name (apply str (rest (string/split % #"/")))]
                            (subs simple-name
                                  0 (min (int (/ 24 (count names)))
                                         (count simple-name))))
                         names))))

(defn deep-merge [& maps]
  (apply merge-with (fn [& args]
                      (if (every? map? args)
                        (apply deep-merge args)
                        (last args)))
         maps))
