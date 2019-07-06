(ns panaeolus.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [panaeolus.utils.jna-path :refer [get-os]]
            [zprint.core :as zp]))

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
  {:csound {:messagelevel     0
            :hardwarebufsamps 4096
            :iobufsamps       (if (= :windows (get-os)) 2048 1024)
            :ksmps            256
            :nchnls           2}
   :jack {:system-out "system:playback_"
          :period     (if (= :windows (get-os)) 2048 1024)
          :interface  (if (= :windows (get-os)) nil "hw:0")}
   :sample-rate       (if (= :windows (get-os)) 44100 48000)
   :samples-directory (.getAbsolutePath (io/file home-directory "samples"))
   :bpm               120})

(defn read-config []
(if (.exists ^java.io.File panaeolus-user-config)
    (edn/read-string (slurp panaeolus-user-config))
    (do (.mkdirs ^java.io.File panaeolus-config-directory) {})))

(def ^:private non-reconciled-config (read-config))

(defn reconciled-config []
  (loop [dc  (keys default-config)
         rc  {}]
    (if (empty? dc)
      (do
        (spit (.getAbsolutePath ^java.io.File panaeolus-user-config) (zp/zprint-str rc))
        rc)
      (let [fdc (first dc)
            nrc (if (contains? non-reconciled-config fdc)
                  rc
                  (assoc rc fdc (get default-config fdc)))]
        (recur (rest dc)
               nrc)))))

(def config (atom (reconciled-config)))

(defn update-config!
  "The callback gets passed the current config, the return value is the new value" 
  [callback]
  (let [new-config (callback @config)]
    (spit (.getAbsolutePath ^java.io.File panaeolus-user-config) (zp/zprint-str new-config))
	(reset! config new-config)))
	