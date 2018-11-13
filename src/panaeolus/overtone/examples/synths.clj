(ns panaeolus.overtone.examples.synths
  (:require [panaeolus.utils.utils :as utils]
            [overtone.ableton-link :refer [get-bpm]])
  (:use overtone.live panaeolus.overtone.macros))

(definst+ prophet :perc
  [nn 60 dur 1 amp -12 lpf 12000 rq 0.3]
  (let [amp  (dbamp amp)
        freq (midicps nn)
        snd  (pan2 (mix [(pulse freq (* 0.1 (/ (+ 1.2 (sin-osc:kr 1)) )))
                         (pulse freq (* 0.8 (/ (+ 1.2 (sin-osc:kr 0.3) 0.7) 2)))
                         (pulse freq (* 0.8 (/ (+ 1.2 (lf-tri:kr 0.4 )) 2)))
                         (pulse freq (* 0.8 (/ (+ 1.2 (lf-tri:kr 0.4 0.19)) 2)))
                         (* 0.5 (pulse (/ freq 2) (* 0.8 (/ (+ 1.2 (lf-tri:kr (+ 2 (lf-noise2:kr 0.2))))
                                                            2))))]))
        snd  (normalizer snd)
        env  (env-gen (perc (* 0.2 dur) (* 0.8 dur)) :action FREE)
        snd  (rlpf (* env snd snd) lpf rq)]
    (* amp snd)))

(comment
  (prophet :loop [0.25 0.25 0.25 0.25] 0.3 (fn [& env] (+ 32 (rand 20))) :amp -2 :lpf 400)
  (prophet :kill)
  )

(definst+ simple-flute :perc
  [nn 60 amp -12 att 0.4 rel 0.2]
  (let [gate 1
        freq (midicps nn)
        amp  (dbamp amp)
        env  (env-gen (lin att (* 0.25 amp) rel -4) gate :action FREE)
        mod1 (lin-lin:kr (sin-osc:kr (lin-rand 5 7)) -1 (lin-rand 0.7 1) (* freq 0.99) (* freq 1.01))
        mod2 (lin-lin:kr (lf-noise2:kr 1) -1 1 (lin-rand 0.1 0.2) 1)
        mod3 (lin-lin:kr (sin-osc:kr (ranged-rand 4 6)) -1 1 0.5 1)
        sig  (distort (* env (sin-osc [freq mod1])))
        sig  (* amp sig mod2 mod3 0.25)]
    sig))

(definst+ bell :perc
  [nn 60 amp -12 dur 2
   h0 1 h1 0.6 h2 0.4 h3 0.25 h4 0.2 h5 0.15]
  (let [amp         (dbamp amp)
        harmonics   [ 1  2  3  4.2  5.4 6.8]
        proportions [h0 h1 h2   h3   h4  h5]
        proportional-partial
        (fn [harmonic proportion]
          (let [envelope
                (* amp 1/5 (env-gen (perc 0.01 (* proportion dur))))
                overtone
                (* harmonic (midicps nn))]
            (* 1/2 proportion envelope (sin-osc overtone))))
        partials
        (map proportional-partial harmonics proportions)
        whole       (mix partials)]
    (detect-silence whole :action FREE)
    whole))
