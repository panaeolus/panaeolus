(ns panaeolus.csound.instruments.metallic-casio
  (:require [clojure.java.io :as io]
            [panaeolus.csound.macros :as c]))

(c/definst metallic-casio
  :orc-internal-filepath
  "panaeolus/csound/instruments/orchestra/additive/metallic-casio.orc"
  :instr-form
  [{:name :dur :default 1}
   {:name :nn :default 60}
   {:name :amp :default -12}
   {:name :dt :default 1}
   {:name :mode :default 0}]
  :instr-number 1
  :num-outs 2
  :release-time 2)

;; (metallic-casio :loop [0.25 0.25 0.25] :nn [[40 42 47] [80 81 62]] :dur 10 :dt 200 :mode 3 )
;; (metallic-casio :stop)
