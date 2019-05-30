(ns panaeolus.csound.instruments.sruti
  (:require [panaeolus.csound.macros :as c]))

(c/definst sruti
  :orc-string
  "
giDrone ftgen 0, 0, 256, 9,  1,1,0,   1.732050807568877,.5773502691896259,0,   2.449489742783178,.408248290463863,0,   3.162277660168379,.3162277660168379,0,   3.872983346207417,.2581988897471611,0,   4.58257569495584,.2182178902359924,0,   5.291502622129182,.1889822365046136,0, 6,.1666666666666667,0,   6.70820393249937,.1490711984999859,0,   7.416198487095663,.1348399724926484,0,   8.124038404635961,.1230914909793327,0,   9.539392014169456,.1048284836721918,0,  10.2469507659596,.0975900072948533,0,  10.95445115010332,.0912870929175277,0,   11.6619037896906,.0857492925712544,0


instr 1
gkampSruti = ampdb(p5)/9
gkbaseSruti = cpsmidinn(p4)

gknumSruti = max:i(p6, 1)
gkdenSruti = p7
gkrissetSruti = p8

;; determine pitch
gkfracSruti = gknumSruti/gkdenSruti
gkfreqSruti = gkbaseSruti*gkfracSruti

itbl = giDrone
koff = gkrissetSruti
koff0 = ((gkdenSruti*2)/gknumSruti)*koff
koff1	= koff0
koff2	= 2*koff
koff3	= 3*koff
koff4	= 4*koff

;; envelope
kenv linenr gkampSruti, 2, 3, 0.01
;; generate primary tone
a1 poscil3	kenv, gkfreqSruti, itbl
;; generate Risset tones
a2 poscil3	kenv, gkfreqSruti+koff1, itbl
a3 poscil3	kenv, gkfreqSruti+koff2, itbl
a4 poscil3	kenv, gkfreqSruti+koff3, itbl
a5 poscil3	kenv, gkfreqSruti+koff4, itbl
a6 poscil3	kenv, gkfreqSruti-koff1, itbl
a7 poscil3	kenv, gkfreqSruti-koff2, itbl
a8 poscil3	kenv, gkfreqSruti-koff3, itbl
a9 poscil3	kenv, gkfreqSruti-koff4, itbl

aout sum a2, a3, a4, a5, a6, a7, a8, a9
aenv linseg 0, 0.02, 1, p3 - 0.05, 1, 0.02, 0, 0.01, 0
aout *= aenv
outs aout, aout

endin"
  :instr-form  [{:name :dur :default 4}
                {:name :nn :default 32}
                {:name :amp :default -16}
                {:name :num :default 1}
                {:name :dens :default 1}
                {:name :risset :default 1}]
  :instr-number 1
  :num-outs 2
  :release-time 3)
