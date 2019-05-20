(ns panaeolus.csound.instruments.priest
  (:require [panaeolus.csound.macros :as c]))

#_(c/definst priest
    (slurp "src/panaeolus/csound/instruments/orchestra/additive/atmo.orc")
    [{:name :dur :default 1}
     {:name :nn :default 60}
     {:name :amp :default -12}
     {:name :dt :default 1}
     {:name :mode :default 0}]
    1 2 10 {})
