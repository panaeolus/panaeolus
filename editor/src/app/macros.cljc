(ns app.macros
  #?(:cljs (:require-macros [app.macros])))

(defmacro version []
  (slurp "../VERSION"))
