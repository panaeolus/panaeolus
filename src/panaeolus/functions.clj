(ns panaeolus.functions
  (:require [panaeolus.globals :as globals]))

(defn stop []
  (do (reset! globals/pattern-registry {})
      :stop-all))
