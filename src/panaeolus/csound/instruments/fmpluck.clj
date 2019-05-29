(ns panaeolus.csound.instruments.fmpluck
  (:require
   [clojure.java.io :as io]
   [panaeolus.csound.macros :as c]))

(c/definst fmpluck
  :orc-internal-filepath
  "panaeolus/csound/instruments/orchestra/fm/fmpluck.orc"
  :instr-form [{:name :dur :default 2}
               {:name :nn :default 48}
               {:name :amp :default -12}
               {:name :phase :default 4}]
  :instr-number 1
  :num-outs 2
  :release-time 2)

(fmpluck :loop [1 1 1 1])
