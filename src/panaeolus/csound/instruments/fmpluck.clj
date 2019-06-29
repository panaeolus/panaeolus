(ns panaeolus.csound.instruments.fmpluck
  (:require
   [panaeolus.csound.macros :as c]))

(c/definst fmpluck
  :orc-internal-filepath
  "instruments/fm/fmpluck.orc"
  :instr-form  [{:name :dur :default 2}
                {:name :nn :default 48}
                {:name :amp :default -12}
                {:name :phase :default 4}]
  :instr-number  1
  :num-outs 2
  :release-time 2)
