(ns panaeolus.jack2.jack-routing
  (:require [panaeolus.config :refer [config]]
            [panaeolus.jack2.jack-lib :as jack])
  (:import [org.jaudiolibs.jnajack.lowlevel
            JackLibrary$JackProcessCallback]
           [com.sun.jna Pointer]))

(set! *warn-on-reflection* true)

(def master-client (atom nil))

(def ^:private __master_letru_no_gc__ (atom nil))

(defonce ^:private __open_master_client__
  (let [jack-client (jack/open-client "panaeolus")
        system-ports (jack/get-physical-input-ports jack-client)
        jack-ports-in (mapv #(jack/create-input-port jack-client %)
                             (range 1 (inc (count system-ports))))
        jack-ports-out (mapv #(jack/create-output-port jack-client %)
                             (range 1 (inc (count system-ports))))
        master-client-letthru
        (reify JackLibrary$JackProcessCallback
          (^int invoke [_ ^int nframes]
           (let [buffers (mapv (fn [_] (float-array nframes))
                               (range (count system-ports)))]
            (dotimes [idx (count system-ports)]
              (.read ^Pointer (jack/get-buffer
                               (nth jack-ports-in idx)
                               nframes)
                     (long 0)
                     ^"[F" (nth buffers idx)
                     0
                     nframes)
              (.write ^Pointer
                      (jack/get-buffer
                       (nth jack-ports-out idx)
                       nframes)
                      (long 0)
                      ^"[F" (nth buffers idx)
                      0
                      nframes)))
           0))]
    (reset! __master_letru_no_gc__ master-client-letthru)
    (jack/set-process-callback jack-client master-client-letthru)
    (jack/activate-thread jack-client)
    (dotimes [idx (count system-ports)]
      (jack/connect jack-client
                    (jack/get-port-name (nth jack-ports-out idx))
                    (nth system-ports idx)))
    (reset! master-client {:client-name (jack/get-client-name jack-client)
                           :jack-client jack-client
                           :jack-ports-in jack-ports-in
                           :jack-ports-out jack-ports-out})))
