(ns panaeolus.csound.fx.reverbsc
  (:require [panaeolus.csound.macros :as c]))

(c/define-fx reverbsc
  :orc-string
  "instr 1
   ain1, ain2 ins
   outs ain2, ain1
  endin
"
  :num-outs 2
  :release-time 6
  :init-hook "event_i( \"i\", 1, 0, -1 )"
  :release-hook "event_i( \"i\", 1, 0, 4 )"
  )
