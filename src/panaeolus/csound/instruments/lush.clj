(ns panaeolus.csound.instruments.lush
  (:require
   [panaeolus.csound.macros :as c]))

(c/definst lush
  :orc-internal-filepath
  "instruments/synth/lush.orc"
  :instr-form  [{:name :dur :default 2}
                {:name :nn :default 48}
                {:name :amp :default -12}
                {:name :att :default 0.1}
                {:name :dec :default 0.2}
                {:name :sus :default 1}
                {:name :rel :default 0.5}]
  :instr-number  1
  :num-outs 2
  :release-time 2)

;; (lush :stop [0.25 0.25 0.25 0.25] :nn [42 39 43 44] :dur 0.2 :amp 12 :dec 1 :att [0.1 0.5 0.3] :rel 0.01)
