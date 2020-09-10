(ns panaeolus.functions
  (:require [panaeolus.globals :as globals]
            [panaeolus.csound.pattern-control :as pctl]))

(defn stop []
  (let [all-patterns (keys @globals/pattern-registry)]
    (doseq [pat all-patterns]
      (pctl/csound-pattern-stop pat))
      :stop-all))
