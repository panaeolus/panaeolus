(ns panaeolus.csound.instruments.priest
  (:require [panaeolus.csound.macros :as c]))

;; From the legendary piece "The Electric Priest" from Tobias Enhus
(c/definst priest
  :orc-internal-filepath
  "panaeolus/csound/instruments/orchestra/fof/priest.orc"
  :instr-form
  [{:name :dur :default 1}
   {:name :nn :default 32}
   {:name :amp :default -12}
   {:name :env :default 70}
   {:name :morph :default 3.5}
   {:name :att :default 5}
   {:name :mode :default 0}]
  :instr-number 4
  :num-outs  2
  :release-time 2
  :config {:zerodbfs 32767})


;; (priest :loop [4 4 4 4] :nn [60 65 67 [52 54 58]] :att [5] :morph [0 1 2 3] :mode [0 1 2] :dur 4 :amp -90)
;; (priest :stop)
