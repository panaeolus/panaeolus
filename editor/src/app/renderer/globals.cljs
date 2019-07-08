(ns app.renderer.globals
  (:require-macros [app.macros])
  (:require [reagent.core :as reagent :refer [atom]]))

(def +version+ (app.macros/version))

(def app-state (atom {:ace-ref nil :nrepl-callbacks {} :markers {}
                      :nrepl-port nil :editor-value "" :echo-buffer ""}))

(def log-atom (atom []))

(defn ^:export global_logger [log]
  (swap! log-atom conj log))

(def AceRange (atom nil))
