(ns panaeolus.jack2.jack-lib
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.core.async :as async]
   [panaeolus.utils.jna-path :as jna-path])
  (:import
   [org.jaudiolibs.jnajack.lowlevel JackLibraryDirect]
   [org.jaudiolibs.jnajack JackOptions JackStatus JackClient]
   [org.jaudiolibs.jnajack JackPortType]
   [org.jaudiolibs.jnajack JackPortFlags]
   [org.jaudiolibs.jnajack Jack JackException]
   [java.lang ProcessBuilder]
   [java.util List EnumSet]
   [java.nio FloatBuffer]
   [java.util Arrays]
   [com.sun.jna.ptr IntByReference]
   [com.sun.jna NativeLong]))

(set! *warn-on-reflection* true)

(def jack-server-atom (atom nil))

(defn spawn-jackd-windows! []
  (let [kill-callback (fn []
                        (reset! jack-server-atom nil)
                        (println "jackd.exe died"))]
    (async/thread
      (let [jackd-file (io/file jna-path/libcsound-cache-path "jackd.exe")
            jackd-loc (.getAbsolutePath jackd-file)
            pbuilder (ProcessBuilder. ^"[Ljava.lang.String;" (into-array String ["cmd.exe" "/c" (str "\"" jackd-loc "\"") "-d" "portaudio" "-p" "2048" "-r" "44100"]))
            process  (.start pbuilder)]
        (reset! jack-server-atom process)
        (try (with-open [error-stream (clojure.java.io/reader (.getErrorStream process))]
               (loop []
                 (when-let [line (.readLine ^java.io.BufferedReader error-stream)]
                   (println line)
                   (recur))))
             (catch java.io.IOException e kill-callback nil))
        (try (with-open [reader (clojure.java.io/reader (.getInputStream process))]
               (loop []
                 (when-let [line (.readLine ^java.io.BufferedReader reader)]
                   (println line)
                   (recur))))
             (catch java.io.IOException e kill-callback nil))))))

(defn- wait-until-jack-windows [ready-chan]
  (async/go-loop []
    (if @jack-server-atom
      (async/>! ready-chan true)
      (do (async/<! (async/timeout 1000))
          (recur)))))

(when (= :windows (jna-path/get-os))
  (spawn-jackd-windows!)
  (async/<!! (async/timeout 2000)))

(defn- jack-is-running?
  "Query the jack ports to see if it's running.
   This is useful to do before attemting external
   server connection on Linux, as not to fail silently"
  []
  (let [exit-code (:exit (shell/sh "jack_lsp"))]
    (zero? exit-code)))

(defn- wait-until-jack-connects [ready-chan]
  (async/go-loop []
    (if (jack-is-running?)
      (async/>! ready-chan true)
      (do (async/<! (async/timeout 1000))
          (recur)))))

(when (and (= :linux (jna-path/get-os))
           (not (jack-is-running?)))
  (println "[pae:jack:not-running]")
  (let [wait-chan (async/chan 1)]
    (wait-until-jack-connects wait-chan)
    (async/<!! wait-chan)
    (println "[pae:jack:started]")))

(def jack-server ^Jack (Jack/getInstance))

(defn open-client [client-name]
  (.openClient ^Jack jack-server client-name
               (EnumSet/of JackOptions/JackNoStartServer)
               nil))

(def jack-client (open-client "__jnajack__"))

(defn connect [from-out to-in]
  ;; (prn "CONNECT FROM OUT" from-out "TO IN" to-in)
  (try (.connect ^Jack jack-server from-out to-in)
       (catch JackException e nil)))

(defn disconnect [from-out to-in]
  ;; (prn "DISCONNECT FROM OUT" from-out "TO IN" to-in)
  (try (.disconnect ^Jack jack-server from-out to-in)
       (catch JackException e nil)))

(defn get-connections [port-name]
  (try
    (vec (sort (Arrays/asList (.getAllConnections
                               ^Jack jack-server
                               ^JackClient jack-client
                               port-name))))
    (catch Exception e nil)))

(defn query-connection
  "the string can be partial and will serve as regex in native env"
  [string]
  (vec (Arrays/asList (.getPorts ^Jack jack-server string  nil nil ))))
