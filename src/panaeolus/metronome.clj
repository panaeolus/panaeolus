(ns panaeolus.metronome
  (:require [overtone.ableton-link :as link])
  (:use overtone.live))

(defonce ^:private bpm-buffer (buffer 1))

(link/enable-link true)

(defn set-bpm [bpm]
  (set-buf bpm-buffer bpm 0)
  (link/set-bpm bpm))

(defn get-bpm [& [as-buffer?]]
  (if as-buffer?
    bpm-buffer
    (link/get-bpm)))

(set-bpm 134)
