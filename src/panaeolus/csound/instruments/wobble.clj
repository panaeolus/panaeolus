(ns panaeolus.csound.instruments.wobble
  (:require [panaeolus.csound.macros :as c]))

(c/definst wobble
  :orc-internal-filepath
  "instruments/dubstep/wobble.orc"
  :instr-form
  [{:name :dur :default 2}
   {:name :nn :default 48}
   {:name :amp :default -12}
   {:name :div :default 1}
   {:name :res :default 0.3}]
  :instr-number 1
  :num-outs 2
  :release-time 3)

;; (wobble :loop [1 1 1 1] :amp -14)
;; (wobble :stop)
