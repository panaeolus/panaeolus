(ns panaeolus.config
  (:require [clojure.edn :as edn]))

(def ^:private home-directory
  (System/getProperty "user.home"))

(def ^:private panaeolus-config-directory
  (str home-directory "/.panaeolus"))

(def ^:private panaeolus-user-config
  (str panaeolus-config-directory "/config.edn"))

(def ^:private default-config
  {:bpm                  120
   :nchnls               2
   :jack-system-out      "system:playback_"
   :sample-rate          48000
   :ksmps                256
   :iobufsamps           256
   :hardwarebufsamps     4096
   :samples-directory    (str home-directory "/samples")
   :csound-messagelevel  35
   :csound-instruments   []})

(def ^:private user-config
  (if (.exists (clojure.java.io/as-file panaeolus-user-config))
    (edn/read-string (slurp panaeolus-user-config))
    {}))

(def config (atom (merge default-config user-config)))
