(ns app.renderer.globals
  (:require [reagent.core :as reagent :refer [atom]]))

(def app-state (atom {:ace-ref nil :nrepl-callbacks {} :inline-ranges []}))

(def log-atom (atom []))
