(ns panaeolus.sequence-parser
  (:require [instaparse.core :as insta :refer [defparser]]
            [panaeolus.pitches :refer [midi->freq note->midi freq->midi-noround]]))

(def ^:private process-nname
  (comp note->midi keyword))

(def ^:private process-hertz
  (fn [nnum]
    (freq->midi-noround nnum)))

(defparser sequence-parser*
  "<list> = token (<whitespace> token)*
   <token> = nname | hertz | nnum | rest | hexadecimal | simile | generator
   whitespace = #'\\s+'
   nname = (letter digit)+ time? generator?
   hertz = digit+ <hz> time? generator?
   nnum  = digit+ time? generator?
   time = ( ext | div )*
   generator = ( trem )*
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
   <octave> = ',' | '\\''    
   <letter> = #'[a-zA-Z]+'
   <digit> = #'[0-9]+\\.?[0-9]*'"
  :output-format :hiccup)


;; (sequence-parser "20hz*2 30hz f3:2 0x023 r*2")

(defn- sequence-transformer [pat-string]
  (insta/transform
   {:nname       (fn [& [nname octave time]]
                   (let [freq (process-nname (str nname octave))]
                     [[:note freq] time]))
    :nnum        (fn [& [nnum time]]
                   (let [;;freq (process-nnum (Integer/parseInt nnum))
                         time (or time [:time nil])]
                     [[:note (Integer/parseInt nnum)] time]))
    :hertz       (fn [& [hz time]]
                   [[:note (process-hertz (Integer/parseInt hz))]
                    (or time [:time nil])])
    :hexadecimal (fn [hex]
                   (let [hex   (subs hex 2)
                         hex   (str (Integer/parseInt hex 16))
                         nnums (mapv #(vector :note (Integer/parseInt (str %))) (seq hex))]
                     nnums))
    
    :rest (fn [& [time wtf]]
            (if-not time
              [[:rest 1]]
              [[:rest (Integer/parseInt (second (second time)))]]))}
   (sequence-parser* pat-string)))

(defn- clean-vectors [sek]
  (reduce
   (fn [i v]
     (if (keyword? (first v))
       (conj i v)
       (apply conj i v)))
   [] sek))

(defn sequence-parser [pat-string]
  (let [parsed-sekuenze (sequence-transformer pat-string)]
    (when (insta/failure? parsed-sekuenze)
      (throw (Exception. (str (seq parsed-sekuenze)))))
    (loop [[head & tail]   (clean-vectors (sequence-transformer pat-string))
           propogated-time 1
           data            {}]
      (if (empty? head)
        data
        (let [data (if (= :note (first head))
                     (-> data
                         (update :nn conj (second head))
                         (update :time conj propogated-time))
                     data)
              [data propogated-time]
              (if (= :time (first head))
                (let [appl (second head)
                      time (case (first appl)
                             :ext (read-string (second appl))
                             :div (float (/ 1 (read-string (second appl))))
                             propogated-time)
                      data (assoc data :time (conj (or (butlast (:time data)) []) time))]
                  [data time])
                [data propogated-time])]
          (recur tail propogated-time data))))))

;; (sequence-parser "2*0.25 0xffef9 4 2")
;; (map count (vals (sequence-parser "2*0.25 0xffef9 4 2")))

;; (prn (reduce conj (sequence-transformer "20hz*2 30hz f3:2 0x023 r*2")))


