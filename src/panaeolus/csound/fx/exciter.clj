(ns panaeolus.csound.fx.exciter
  (:require [panaeolus.csound.macros :as c]))

(c/define-fx exciter
  (str (slurp "src/panaeolus/csound/fx/udo/exciter.udo")
       "
gkport init 1
gkfreq init 100
gkceil init 1
gkharmonics init 5
gkblend init 1

instr 1
  kfreq portk gkfreq, gkport
  kceil portk gkceil, gkport
  kharmonics portk gkharmonics, gkport
  kblend portk gkblend, gkport

  aInL, aInR ins

    afader init 0
    if (p3 < 0) then
      printk 1, 1
      afader expseg 0.001, 0.1, 1, 99999999, 1
    else
       printk -1, 1
      afader expseg 1, p3, 0.001
    endif

  aL, aR exciter aInL, aInR, kfreq, gkport, kceil, kharmonics, kblend

  outs aL*afader, aR*afader
endin

instr 2
  gkport = p3
  gkfreq = p4
  gkceil = p5
  gkharmonics = p6
  gkblend = p7
endin

alwayson(1, 0, -1)
")
  [{:name :dur :default 1}
   {:name :freq :default 100}
   {:name :ceil :default 1}
   {:name :harmonics :default 5}
   {:name :blend :default 1}]
  2 2 2 {}
  )
