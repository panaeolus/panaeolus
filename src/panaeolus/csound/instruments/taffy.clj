(ns panaeolus.csound.instruments.taffy
  (:require [panaeolus.csound.macros :as c]))

(c/definst taffy
  :orc-internal-filepath
  "instruments/dubstep/taffy.orc"
  :instr-form
  [{:name :dur :default 1}
   {:name :nn :default 48}
   {:name :amp :default -12}
   {:name :type :default 0}
   {:name :wobble :default 1}
   {:name :lpf :default 1000}
   ;; {:name :div :default 2}
   ;; {:name :depth :default 0.1}

   ;; {:name :res :default 0.5}
   ;; {:name :type :default 0}
   ]
  :instr-number 8
  :num-outs 2
  :release-time 2.5
  )

;; (taffy :loop 4 :nn [48] :dur 4 :type [0 3] :wobble [[1 4]] :lpf 1600)

;; (taffy :stop)
