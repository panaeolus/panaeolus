(ns panaeolus.csound.fx.weirdverb
  (:require
    [panaeolus.csound.macros :as c]))

(c/define-fx
  weirdverb1
  :orc-string
    "instr 1
  ain1, ain2 ins
  aFL, aFR  MVerb ain1, ain2, \"Weird 1\"
  afader init 0
  if (p3 < 0) then
    kenv linseg 0, .05, 1,  .2, 1 ;; Fade in on initialization
  else
    kenv linseg 1, p3 - .05, 1, .05, 0 ;; Fade out on release
  endif
  outs (aFL*1.5)*kenv, (aFR*1.5)*kenv
  clear aFL, aFR
endin
"
  :num-outs 2
  :release-time 20
  :init-hook "event_i \"i\", 1, 0, -1"
  :release-hook "event_i \"i\", 1, 0, 4")

(c/define-fx
  weirdverb2
  :orc-string
    "instr 1
  ain1, ain2 ins
  aFL, aFR  MVerb ain1, ain2, \"Weird 1\"
  afader init 0
  if (p3 < 0) then
    kenv linseg 0, .05, 1,  .2, 1 ;; Fade in on initialization
  else
    kenv linseg 1, p3 - .05, 1, .05, 0 ;; Fade out on release
  endif
  outs (aFL*1.5)*kenv, (aFR*1.5)*kenv
  clear aFL, aFR
endin
"
  :num-outs 2
  :release-time 20
  :init-hook "event_i \"i\", 1, 0, -1"
  :release-hook "event_i \"i\", 1, 0, 4")


(c/define-fx
  weirdverb3
  :orc-string
    "instr 1
  ain1, ain2 ins
  aFL, aFR  MVerb ain1, ain2, \"Weird 3\"
  afader init 0
  if (p3 < 0) then
    kenv linseg 0, .05, 1,  .2, 1 ;; Fade in on initialization
  else
    kenv linseg 1, p3 - .05, 1, .05, 0 ;; Fade out on release
  endif
  outs (aFL*1.5)*kenv, (aFR*1.5)*kenv
  clear aFL, aFR
endin
"
  :num-outs 2
  :release-time 20
  :init-hook "event_i \"i\", 1, 0, -1"
  :release-hook "event_i \"i\", 1, 0, 4")
