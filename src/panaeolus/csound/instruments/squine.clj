(ns panaeolus.csound.instruments.squine
  (:require [panaeolus.csound.macros :as c]))

(c/definst squine
  :orc-string "
instr 1
inn = p4
iamp = ampdbfs(p5)
ienv = p10
afreq rspline cpsmidinn(inn - 0.5), cpsmidinn(inn + 0.5), 0.001, 0.01
asqareness  line  p6, p3, p7
asymmetry   line  p8, p3, p9
adry squinewave afreq, asqareness, asymmetry, 0
if (ienv == 0) then
 aenv linseg 0, 0.02, 1, p3 - 0.05, 1, 0.02, 0, 0.01, 0
else
 aenv expon 1, p3, ienv
endif
outs adry*iamp*aenv, adry*iamp*aenv
endin
"
  :instr-form [{:name :dur :default 2}
               {:name :nn :default 60}
               {:name :amp :default -18}
               {:name :s1 :default 0.2}
               {:name :s2 :default 0.4}
               {:name :a1 :default 0.2}
               {:name :a2 :default 0.4}
               {:name :env :default 0.1}]
  :instr-number 1
  :num-outs 2
  :release-time 2)
