(ns panaeolus.csound.fx.reverbsc
  (:require [panaeolus.csound.macros :as c]))

(c/define-fx reverbsc
  :orc-string
  "instr 1
    ain1, ain2 ins
    denorm ain1, ain2
    aFL, aFR  reverbsc ain1, ain2, 0.55, 17000, sr, 0.5, 1
    afader init 0
    if (p3 < 0) then
      kenv linseg 0, .05, 1,  .2, 1 ;; Fade in on initialization
    else
      kenv linseg 1, p3 - .05, 1, .05, 0 ;; Fade out on release
  endif
  outs (aFL*1.5)*kenv, (aFR*1.5)*kenv
  endin
"
  :num-outs 2
  :release-time 2
  :init-hook "event_i( \"i\", 1, 0, -1 )"
  :release-hook "event_i( \"i\", 1, 0, 2 )"
  )
