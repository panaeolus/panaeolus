(ns panaeolus.overtone.examples.fof
  (:use overtone.live panaeolus.overtone.macros))

;; FOF
(def ^:private contratenor-formants
  [;; A
   {:freq [660 1120 2750 3000 3350]
    :amp  [0 -6 -23 -24 -38]
    :bw   [80 90 120 130 140]}
   ;; E
   {:freq [440 1800 2700 3000 3300]
    :amp  [0 -14 -18 -20 -20]
    :bw   [70 80 100 120 120]}
   ;; I
   {:freq [270 1850 2900 3350 3590]
    :amp  [0 -24 -24 -36 -36]
    :bw   [40 90 100 120 120]}
   ;; O
   {:freq [430 820 2700 3000 3300]
    :amp  [0 -10 -26 -22 -34]
    :bw   [40 80 100 120 120]}
   ;; U
   {:freq [370 630 2750 3000 3400]
    :amp  [0 -20 -23 -30 -34]
    :bw   [40 60 100 120 120]}])

(defn vowel-spec-to-buffer [spec]
  (let [freq-buffer  (buffer (count (:freq spec)))
        amp-buffer   (buffer (count (:amp spec)))
        bw-buffer    (buffer (count (:bw spec)))
        vowel-buffer (buffer 3)]
    ;; (println "vowelbuf-id" (buffer-id vowel-buffer))
    (dotimes [i (buffer-size freq-buffer)]
      (buffer-set! freq-buffer i (nth (:freq spec) i)))
    (dotimes [i (buffer-size amp-buffer)]
      (buffer-set! amp-buffer i (nth (:amp spec) i)))
    (dotimes [i (buffer-size bw-buffer)]
      (buffer-set! bw-buffer i (nth (:bw spec) i)))
    (buffer-set! vowel-buffer 0 (buffer-id freq-buffer))
    (buffer-set! vowel-buffer 1 (buffer-id amp-buffer))
    (buffer-set! vowel-buffer 2 (buffer-id bw-buffer))
    vowel-buffer))

(def ^:private contratenor-buffer (buffer 5))

(defn voice-type-to-buffer [voice-vector]
  (dotimes [i (buffer-size contratenor-buffer)]
    (let [buf-id (buffer-id
                  (vowel-spec-to-buffer
                   (nth voice-vector i)))]
      (buffer-set! contratenor-buffer i buf-id))))

(defonce ^:private  __load_voices__
  (voice-type-to-buffer contratenor-formants))

(definst+ tenor :perc
  [nn 40 dur 1 amp -12 vowel1 0 vowel2 1 trans 0.4]
  (let [base-freq        (midicps nn)
        amp              (dbamp amp)
        vowel1           (index:kr contratenor-buffer vowel1)
        vowel2           (index:kr contratenor-buffer vowel2)
        get-sig          (fn [idx vowel1 vowel2]
                           (let [freq     (index:kr (index:kr vowel1 0) idx)
                                 amp1     (index:kr (index:kr vowel1 1) idx)
                                 bw       (index:kr (index:kr vowel1 2) idx)
                                 freq2    (index:kr (index:kr vowel2 0) idx)
                                 amp2     (index:kr (index:kr vowel2 1) idx)
                                 bw2      (index:kr (index:kr vowel2 2) idx)                          
                                 freq-env (line freq2 freq trans)
                                 amp-env  (line amp2 amp trans)
                                 bw-env   (line bw2 bw trans)]
                             (* (formant base-freq freq-env bw-env)
                                (dbamp amp1))))
        [a1 a2 a3 a4 a5] (mapv #(get-sig % vowel1 vowel2) (range 5))
        asum             (/ (+ a1 a2 a3 a4 a5) 5)
        env              (env-gen:ar (lin (/ dur 4) (/ dur 2) (/ dur 4) 1 -4) 1 :action FREE)]
    (* amp asum env)))

(comment
  (tenor :loop [1 1 1 1/2 1/2] [40 43 47 52 47 43] :dur 0.2  :vowel2 [1 2 4] :trans [0.3 0.2 0.5])
  (tenor :kill)
  )
