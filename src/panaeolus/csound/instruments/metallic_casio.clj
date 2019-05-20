(ns panaeolus.csound.instruments.metallic-casio
  (:require [panaeolus.csound.macros :as c]))

(c/definst metallic-casio
  (slurp "src/panaeolus/csound/instruments/orchestra/additive/metallic-casio.orc")
  [{:name :dur :default 1}
   {:name :nn :default 60}
   {:name :amp :default -12}
   {:name :dt :default 1}
   {:name :mode :default 0}]
  1 2 10 {})

;; (metallic-casio :loop [0.25 0.25 0.25] :nn [[40 42 47] [80 81 62]] :dur 10 :dt 200 :mode 3 )
;; (metallic-casio :stop [0])
