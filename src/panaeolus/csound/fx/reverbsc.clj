(ns panaeolus.csound.fx.reverbsc
  (:require [panaeolus.csound.macros :as c]))

(c/define-fx reverbsc
  "instr 1
  ain1, ain2 ins
  denorm ain1, ain2
  aFL, aFR  reverbsc ain1, ain2, 0.55, 17000, sr, 0.5, 1
  outs aFL+0.5*ain1, aFR+0.5*ain2
  clear aFL, aFR
endin
schedule(1, 0, -1)"
  [] nil 2 15 {})
