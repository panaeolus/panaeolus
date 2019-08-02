(ns panaeolus.csound.fx.vibrato
  (:require [panaeolus.csound.macros :as c]))

;; TODO
(c/define-fx vibrato
  :orc-internal-filepath
  "fx/vibrato.orc"
  :fx-form []
  :ctl-instr 2
  :num-outs  2
  :release-time 2)
