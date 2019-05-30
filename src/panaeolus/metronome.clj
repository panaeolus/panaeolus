(ns panaeolus.metronome
  (:require [overtone.ableton-link :as link]
            [panaeolus.config :refer [config]]))

(set! *warn-on-reflection* true)

(link/enable-link true)

(defn set-bpm [bpm]
  (link/set-bpm bpm))

(defn get-bpm []
  (link/get-bpm))

(set-bpm (or (:bpm config) 120))
