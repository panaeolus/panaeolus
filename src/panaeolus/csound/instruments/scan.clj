(ns panaeolus.csound.instruments.scan
  (:require [panaeolus.csound.macros :as c]))

(c/definst scan
  :orc-internal-filepath
  "instruments/synth/scan.orc"
  :instr-form
  [{:name :dur :default 2.5}
   {:name :nn :default 36}
   {:name :amp :default -9}
   {:name :rate :default 0.01}
   {:name :lpf :default 1200}
   {:name :res :default 0.4}
   {:name :lfo1 :default 0}
   {:name :lfo2 :default 0}
   {:name :lfo3 :default 0}
   {:name :lfo4 :default 0}
   {:name :type :default 1}
   {:name :mass :default 3}
   {:name :stif :default 0.01}
   {:name :center :default 0.1}
   {:name :damp :default -0.005}
   {:name :pos :default 0}
   {:name :y :default 0}]
  :instr-number 1
  :num-outs  2
  :release-time 2)

#_(scan :stop [4 4 4 4]
        :amp 12
        :rate 10
        :dur [1 1 1 1]
        :lpf 1500
        :type 1
        :nn [38 32]
        :lfo1 300 :lfo2 500
        )
