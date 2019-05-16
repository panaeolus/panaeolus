(ns panaeolus.sequence-parser
  (:require [instaparse.core :as insta :refer [defparser]]
            [clojure.string :as string]
            [panaeolus.pitches :refer [midi->freq note->midi freq->midi-noround]]))

(def ^:private process-nname
  (comp note->midi keyword))

(def ^:private process-hertz
  (fn [nnum]
    (freq->midi-noround nnum)))

(defparser sequence-parser*
  "<list> = token (<whitespace> token)*
   <token> = nname | hertz | nnum | rest | hexadecimal | simile | generator | shift
   whitespace = #'\\s+'
   nname = (letter digit)+ | (letter digit)+ time | (letter digit)+ generator | (letter digit)+ time generator
   hertz = digit+ <hz> | digit+ <hz> time | digit+ <hz> generator | digit+ <hz> time generator
   nnum  = signed+ | signed+ time | signed+ generator | signed+ time generator
   shift = (<shif> signed)
   time = ( ext | div )*
   generator = trem
   div = <divided> digit+
   ext = <extended> digit+
   trem = <tremol> digit+
   rest = <('_' | 'r' | 'R')>+ time?
   hexadecimal = #'0x[0-9a-f]*'
   simile = <('/')>+
   <dotted> = '.'
   <divided> = '/'
   <extended> = '*'
   <tremol> = ':'
   <hz> = <( 'hz' | 'Hz' )>
   <shif> = '^'
   <octave> = ',' | '\\''
   <letter> = #'[a-zA-Z]+'
   <digit> = #'[0-9]+\\.?[0-9]*'
   <signed> = #'-?[0-9]+\\.?[0-9]*'
"
  :output-format :hiccup)


;; (sequence-parser "20hz*2 30hz f3:2 0x023 r*2")

(defn- sequence-transformer [pat-string]
  (insta/transform
   {:nname       (fn [& [nname octave time]]
                   (let [freq (process-nname (str nname octave))]
                     [[:note freq] time]))
    :nnum        (fn [& [nnum time generator]]
                   (let [;;freq (process-nnum (Integer/parseInt nnum))
                         time (or time [:time nil])]
                     (if generator
                       [[:note (read-string nnum)] time generator]
                       [[:note (read-string nnum)] time])))
    :hertz       (fn [& [hz time]]
                   [[:note (process-hertz (read-string hz))]
                    (or time [:time nil])])
    :hexadecimal (fn [& [hex]]
                   (let [hex   (subs hex 2)
                         hex   (str (Integer/parseInt hex 16))
                         nnums (mapv #(vector :note (read-string (str %))) (seq hex))]
                     nnums))
    :shift       (fn [& [shift ha]]
                   ;; (prn shift ha)
                   [:shift shift])

    :rest (fn [& [time]]
            (if-not time
              [[:rest]]
              [:rest time]))}
   (sequence-parser* (string/trim pat-string))))

(defn- clean-vectors [sek]
  (reduce
   (fn [i v]
     (if (keyword? (first v))
       (conj i v)
       (apply conj i v)))
   [] sek))

(defn sequence-parser [pat-string]
  (let [parsed-sekuenze (sequence-transformer pat-string)]
    ;; (prn parsed-sekuenze)
    ;; (prn (clean-vectors (sequence-transformer pat-string)))
    (when (insta/failure? parsed-sekuenze)
      (throw (Exception. (str (seq parsed-sekuenze)))))
    (loop [[head & tail]   (clean-vectors (sequence-transformer pat-string))
           last-head       nil
           shift           0
           propogated-time 1
           data            {:nn [] :time []}]
      (if (empty? head)
        data
        (let [data  (if (= :note (first head))
                      (-> data
                          (update :nn conj (+ shift (second head)))
                          (update :time conj propogated-time))
                      data)
              [data propogated-time]
              (if (= :time (first head))
                (let [appl (second head)
                      time (case (first appl)
                             :ext (read-string (second appl))
                             :div (float (/ 1 (read-string (second appl))))
                             propogated-time)
                      data (assoc data :time (conj (vec (or (butlast (:time data)) [])) time))]
                  [data time])
                [data propogated-time])
              data  (if (= :rest (first head))
                      (if (< 1 (count head))
                        (let [[appl val] (second (second head))
                              mul        (case appl
                                           :ext (read-string val)
                                           :div (float (/ 1 (read-string val))))]
                          (assoc data :time (conj (:time data) (* -1 mul))))
                        (assoc data :time (conj (:time data) (* -1 propogated-time))))
                      data)
              data  (if (= :generator (first head))
                      (let [[appl val] (second head)
                            val        (int (read-string val))]
                        ;; (prn "REP" (repeat val (last (:nn data))))
                        (case appl
                          :trem (-> data
                                    (assoc :nn (into (vec (butlast (:nn data)))
                                                     #_(if (= :note last-head)
                                                         (vec (butlast (:nn data)))
                                                         (vec (:nn data)))
                                                     (vec (repeat val (last (:nn data))))))
                                    (assoc :time (into (vec (or (butlast (:time data)) []))
                                                       (repeat val (float (/ propogated-time val))))))))
                      data)
              shift (if (= :shift (first head))
                      (read-string (second head))
                      shift)]
          ;; (prn data)
          (recur tail (first head) shift propogated-time data))))))


;; (sequence-parser "r*10 c5 c5/8 ^53 0x0fee0")
;; (sequence-parser "^2 2*0.25 0xffef9 4 2*2:2")
;; (map count (vals (sequence-parser "2*8:2")))
;; (prn (reduce conj (sequence-transformer "20hz*2 30hz f3:2 0x023 r*2")))
