(ns panaeolus.csound.fx.flanger
  (:require [panaeolus.csound.macros :as c]))

(c/define-fx flanger
  :orc-internal-filepath
  "panaeolus/csound/fx/orc/flanger.orc"
  :fx-forms [{:name :dur   :default 0.1}
             {:name :rate  :default 5.0}
             {:name :depth :default 0.001}
             {:name :fback :default 0}
             {:name :shape :default 1}]
  :ctl-instr 2
  :num-outs 2
  :release-time 4
  :init-hook "event_i(1, 0, -1)"
  :release-hook "event_i(1, 0, 4)")
