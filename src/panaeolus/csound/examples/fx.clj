(ns panaeolus.csound.examples.fx
  (:use [panaeolus.csound.macros :as c]))

(c/define-csound-fx binauralize11
  "opcode binauralize, aa, akk
  ; collect inputs
  ain,kcent,kdiff	xin
  ifftsz = 1024
  ; determine pitches
  kp1 = kcent + (kdiff/2)
  kp2 = kcent - (kdiff/2)
  krat1 = kp1 / kcent
  krat2	= kp2 / kcent

  ; take it apart
  fsig pvsanal	ain, ifftsz, ifftsz/4, ifftsz, 1
  ; create derived streams
  fbinL	pvscale	fsig, krat1, 1
  fbinR	pvscale	fsig, krat2, 1
  ; put it back together
  abinL	pvsynth	fbinL
  abinR	pvsynth	fbinR
  ; send it out

  xout abinL, abinR
  endop

  gkcent init 0.6
  gkdiff init 0.8

  instr 1
    ain1, ain2 ins
    a1, a2 binauralize (ain1+ain2)/1.3, gkcent, gkdiff
    outs a1, a2
  endin
  schedule(1, 0, -1)

  instr 2
    gkcent = p4
    gkdiff = p5
  endin
  "
  [{:name :cent :default 0.6}
   {:name :diff :default 0.8}]
  2 2 30 {})
