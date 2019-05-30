(ns panaeolus.csound.fx.dubflang
  (:require [panaeolus.csound.macros :as c]))


(c/define-fx dubflang
  :orc-internal-filepath
  "panaeolus/csound/fx/orc/dubflang.orc"
  :fx-form
  [{:name :dur :default 0.1}
   {:name :rate :default 0.1}
   {:name :dt :default 0.001}]
  :ctl-instr 5
  :num-outs 2
  :release-time 3
  :config {:csound-messagelevel 0}
  ;; :init-hook "event_i \"i\", 4, 0, -1"
  :release-hook "event_i \"i\", 4, 0, 3"
  )
