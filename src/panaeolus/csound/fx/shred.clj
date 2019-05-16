(ns panaeolus.csound.fx.shred
  (:require [panaeolus.csound.macros :as c]))

(c/define-fx shred
  (str (slurp "src/panaeolus/csound/fx/udo/shred.udo")
       "
gkTransPose init 1
gkTransRand init 0.1
gkDepth init 1
gkRate init 5.5
gkMode init 0
gkPort init 1

instr 1
  kTransPose portk gkTransPose, gkPort
  kTransRand portk gkTransRand, gkPort
  kDepth portk gkDepth, gkPort
  kRate portk gkRate, gkPort
  kMode = gkMode
  ;; stereo
  iMaxDelay = 1
  iFeedback = 0.1
  iWidth = 1.2
  iwet = 0.95
  iGain = 2
  kMode = 1

  aInL, aInR ins

  aL, aR shred aInL, aInR, iMaxDelay,kTransPose,kTransRand,kDepth,kRate,iFeedback,iWidth,iwet,iGain,kMode

  outs aL, aR
endin

instr 2
  gkPort = p3
  gkTransPose = p4
  gkTransRand = p5
  gkDepth = p6
  gkRate = p7
  gkMode = p8
endin

alwayson(1, 0, -1)
")

  [{:name :dur        :default 1}
   {:name :transpose  :default 1}
   {:name :random :default 0.1}
   {:name :depth :default 1}
   {:name :rate :default 5.5}
   {:name :mode :default 0}]
  2 2 5 {}
  )
