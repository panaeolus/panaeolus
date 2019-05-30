(ns panaeolus.csound.instruments.pluck
  (:require
   [clojure.java.io :as io]
   [panaeolus.csound.macros :as c]))

(c/definst pluck
  :orc-internal-filepath
  "panaeolus/csound/instruments/orchestra/fm/pluck.orc"
  :instr-form
  [{:name :dur :default 2}
   {:name :nn :default 48}
   {:name :amp :default -12}
   {:name :noise :default 0.01}]
  :instr-number 1
  :num-outs 2
  :release-time 2
  )

;; (pluck :loop [1 1 1 1] :amp -2 :fx [(panaeolus.csound.fx.dubflang/dubflang :dur 1 :rate 20 :dt 1)])
;; (pluck :stop)
