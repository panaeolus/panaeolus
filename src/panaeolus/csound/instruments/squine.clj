(ns panaeolus.csound.instruments.squine
  (:require [panaeolus.csound.macros :as c]))

(c/definst squine
  :orc-string "
instr 1
inn = p4
iamp = ampdbfs(p5) * 0.05
iexp = p10
afreq rspline cpsmidinn(inn - 0.5), cpsmidinn(inn + 0.5), 0.001, 0.01
asqareness  line  p6, p3, p7
asymmetry   line  p8, p3, p9
adry squinewave afreq, asqareness, asymmetry, 0
aenv1 expon 1, p3, iexp
aenv2 linseg 0, 0.02, 1, p3 - 0.05, 1, 0.02, 0, 0.01, 0
outs adry*iamp*aenv1*aenv2, adry*iamp*aenv1*aenv2
endin
"
  :instr-form [{:name :dur :default 2}
               {:name :nn :default 60}
               {:name :amp :default -6}
               {:name :s1 :default 0.2}
               {:name :s2 :default 0.4}
               {:name :a1 :default 0.2}
               {:name :a2 :default 0.4}
               {:name :exp :default 0.05}]
  :instr-number 1
  :num-outs 2
  :release-time 2)
