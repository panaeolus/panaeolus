(ns panaeolus.csound.examples.synths
  (:require [panaeolus.csound.macros :as c]))

(c/definst additive
  (slurp "src/panaeolus/csound/examples/additive.orc")
  [{:name :dur :default 1}
   {:name :nn :default 60}
   {:name :amp :default -12}
   {:name :dt :default 1}
   {:name :mode :default 0}]
  1 2 10 {})

;; (additive :stop [0.25 0.25 0.25] :nn [[40 42 47] [80 81 62]] :dur 2 :mode 2 )
