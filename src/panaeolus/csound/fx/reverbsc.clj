(ns panaeolus.csound.fx.reverbsc
  (:require [panaeolus.csound.macros :as c]))

(c/define-fx reverbsc
  "instr 1
  ain1, ain2 ins
  denorm ain1, ain2
  aFL, aFR  reverbsc ain1, ain2, 0.55, 17000, sr, 0.5, 1

    afader init 0
    if (p3 < 0) then
      printk 1, 1
      afader expseg 0.001, 0.1, 1, 99999999, 1
    else
       printk -1, 1
      afader expseg 1, p3, 0.001
    endif

  outs (aFL+0.5*ain1)*afader, (aFR+0.5*ain2)*afader
  clear aFL, aFR
endin
schedule(1, 0, -1)
"
  [] nil 2 2 {})
