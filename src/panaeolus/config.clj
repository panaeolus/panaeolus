(ns panaeolus.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
			[panaeolus.utils.jna-path :refer [get-os]]))


(s/def ::zerodbfs (s/and integer? pos?))

(s/def ::ksmps (s/and integer? #(>= % 1)))

(set! *warn-on-reflection* true)

(def ^:private home-directory
  (System/getProperty "user.home"))

(def ^:private panaeolus-config-directory
  (io/file home-directory ".panaeolus"))

(def ^:private panaeolus-user-config
  (io/file panaeolus-config-directory "config.edn"))

(def ^:private default-config
  {:bpm                  120
   :nchnls               2
   :jack-system-out      "system:playback_"
   :sample-rate          (if (= :windows (get-os)) 44100 48000)
   :ksmps                256
   :iobufsamps           (if (= :windows (get-os)) 2048 1024)
   :hardwarebufsamps     4096
   :samples-directory    (.getAbsolutePath (io/file home-directory "samples"))
   :csound-messagelevel  35
   :csound-instruments   []})

(def ^:private user-config
  (if (.exists ^java.io.File panaeolus-user-config)
    (edn/read-string (slurp panaeolus-user-config))
    {}))

(def config (atom (merge default-config user-config)))
