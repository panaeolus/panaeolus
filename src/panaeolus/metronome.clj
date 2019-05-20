(ns panaeolus.metronome
  (:require [overtone.ableton-link :as link]
            [panaeolus.config :refer [config]]))

(link/enable-link true)

(defn set-bpm [bpm]
  (link/set-bpm bpm))

(defn get-bpm [& [as-buffer?]]
  (link/get-bpm))

(set-bpm (or (:bpm config) 120))
