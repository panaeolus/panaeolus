(ns panaeolus.csound.instruments.wobble
  (:require [clojure.java.io :as io]
            [panaeolus.csound.macros :as c]))

(c/definst wobble
  (slurp (io/resource "panaeolus/csound/instruments/orchestra/dubstep/wobble.orc"))
  [{:name :dur :default 2}
   {:name :nn :default 48}
   {:name :amp :default -12}
   {:name :div :default 1}
   {:name :res :default 0.3}]
  1 2 4 {})
