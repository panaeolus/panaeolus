(ns panaeolus.csound.fx.exciter
  (:require [clojure.java.io :as io]
            [panaeolus.csound.macros :as c]))

(c/define-fx exciter
  :orc-internal-filepath "panaeolus/csound/fx/orc/exciter.orc"
  :fx-form [{:name :dur :default 1}
            {:name :freq :default 100}
            {:name :ceil :default 1}
            {:name :harmonics :default 5}
            {:name :blend :default 1}]
  :ctl-instr 2
  :num-outs 2
  :release-time 2)
