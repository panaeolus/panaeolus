(ns panaeolus.csound.instruments.fmpluck
  (:require
   [clojure.java.io :as io]
   [panaeolus.csound.macros :as c]))

(c/definst fmpluck
  (slurp (io/resource "panaeolus/csound/instruments/orchestra/fm/fmpluck.orc"))
  [{:name :dur :default 2}
   {:name :nn :default 48}
   {:name :amp :default -12}
   {:name :phase :default 4}]
  1 2 6 {})
