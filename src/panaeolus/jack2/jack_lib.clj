(ns panaeolus.jack2.jack-lib
  (:require
   [clojure.java.io :as io]
   [clojure.core.async :as async]
   [panaeolus.config :refer [config]]
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

(defn kill-jackd-windows! []
  (-> (ProcessBuilder. ^"[Ljava.lang.String;" (into-array String ["cmd.exe" "/c" "taskkill" "/F" "/IM" "jackd.exe"]))
       (.start)
	(.waitFor)))

(defn spawn-jackd-windows! [interface-name] (prn "interface-name" interface-name)
  (let [kill-callback (fn [] (println "jackd.exe died"))]
    (async/thread
      (let [jackd-file (io/file jna-path/libcsound-cache-path "jackd.exe")
            jackd-loc (.getAbsolutePath jackd-file)
			_ (prn "DEBUG" (into ["cmd.exe" "/c" (str "\"" jackd-loc "\"") "-R" 
					                             "-d" "portaudio" 
												 "-p" (get-in @config [:jack :period])
												 "-r" (:sample-rate @config)]
					                            (if interface-name ["-d" interface-name] [])))
            pbuilder (ProcessBuilder. ^"[Ljava.lang.String;" 
			           (into-array String (into ["cmd.exe" "/c" (str "\"" jackd-loc "\"") "-R" 
					                             "-d" "portaudio" 
												 "-p" (get-in @config [:jack :period])
												 "-r" (:sample-rate @config)]
					                            (if interface-name ["-d" interface-name] []))))
            process  (.start pbuilder)]
        (reset! jack-server-atom process)
        (try (with-open [error-stream (clojure.java.io/reader (.getErrorStream process))]
               (loop []
                 (when-let [line (.readLine ^java.io.BufferedReader error-stream)]
                   (println "ERROR" line)
                   (recur))))
             (catch java.io.IOException e kill-callback nil))
        (try (with-open [reader (clojure.java.io/reader (.getInputStream process))]
               (loop []
                 (when-let [line (.readLine ^java.io.BufferedReader reader)]
                   (println "STDOUT" line)
                   (recur))))
             (catch java.io.IOException e kill-callback nil))))))

(defn get-jackd-interfaces-windows []
  (kill-jackd-windows!)
  (let [interfaces (atom [])
        jackd-file (io/file jna-path/libcsound-cache-path "jackd.exe")
        jackd-loc (.getAbsolutePath jackd-file)
        process (-> (ProcessBuilder. ^"[Ljava.lang.String;" 
                        (into-array String ["cmd.exe" "/c" (str "\"" jackd-loc "\"") "-d" "portaudio" "-l"]))
                  (.start))]
	(with-open [reader (clojure.java.io/reader (.getInputStream process))]
	  (loop []
        (when-let [line (.readLine ^java.io.BufferedReader reader)]
		  (when-let [device (re-find #"Name\s+=\s(.*)" line)]
            (swap! interfaces conj (second device))
			)
          (recur))))
     (.waitFor process)
	 @interfaces))

(when (= :windows (jna-path/get-os))
  (let [ready-chan (async/chan 1)]
    (async/go-loop []
      (let [interfaces (into ["(auto)"] (get-jackd-interfaces-windows))]
        (println "[pae:jack:choose-interface]")
		(println (apply str (map-indexed (fn [idx interface] (str " > " idx " " interface "\n")) interfaces)))
		(println "Choose your audio-output device (type number from 0 to" (inc (count interfaces)) ")" )
		(flush)
          (let [choice (read-line)
                choice (if (re-matches #"[1-9]+$" choice) 
                         (Integer/parseInt choice) 
                         false)]
            (when-not (false? choice)
              (if (zero? choice)
                (spawn-jackd-windows! nil)
                (spawn-jackd-windows! (nth interfaces choice)))))))
    (async/<!! ready-chan)))

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
