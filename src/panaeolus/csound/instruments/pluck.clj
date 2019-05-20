(ns panaeolus.csound.instruments.pluck
  (:require
   [clojure.java.io :as io]
   [panaeolus.csound.macros :as c]))

(c/definst pluck
  (slurp (io/resource "panaeolus/csound/instruments/orchestra/fm/pluck.orc"))
  [{:name :dur :default 2}
   {:name :nn :default 48}
   {:name :amp :default -12}
   {:name :noise :default 0.01}]
  1 2 6 {})
