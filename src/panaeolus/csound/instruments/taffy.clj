(ns panaeolus.csound.instruments.taffy
  (:require [panaeolus.csound.macros :as c]))

(c/definst taffy
  :orc-internal-filepath
  "instruments/dubstep/taffy.orc"
  :instr-form
  [{:name :dur :default 1}
   {:name :nn :default 48}
   {:name :amp :default -12}
   {:name :div :default 2}
   {:name :depth :default 0.1}
   {:name :lpf :default 1000}
   {:name :res :default 0.5}]
  :instr-number 3
  :num-outs 2
  :release-time 2.5
  )

;; (taffy :loop [1 1 1 1] :nn 50 :dur [1 0.5] :div [0.1 2000 1200] :lpf 12000  :res 0.1 :depth 1)
;; (taffy :stop)
