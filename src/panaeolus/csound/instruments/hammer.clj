(ns panaeolus.csound.instruments.hammer
  (:require [panaeolus.csound.macros :as c]))

(c/definst hammer
  :orc-internal-filepath
  "panaeolus/csound/instruments/orchestra/synth/hammer.orc"
  :instr-form
  [{:name :dur :default 1}
   {:name :nn :default 32}
   {:name :amp :default -12}]
  :instr-number 1
  :num-outs  2
  :release-time 2)
