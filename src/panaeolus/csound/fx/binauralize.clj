(ns panaeolus.csound.fx.binauralize
  (:require [panaeolus.csound.macros :as c]))

(c/define-fx binauralize
  :orc-internal-filepath
  "panaeolus/csound/fx/orc/binauralize.orc"
  :fx-form    [{:name :dur  :default 0.1}
               {:name :cent :default 0.6}
               {:name :diff :default 0.8}]
  :ctl-instr 2
  :num-outs  2
  :release-time 2
  )
