(ns panaeolus.jack2.jack-lib
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.core.async :as async]
<<<<<<< HEAD
   [panaeolus.config :refer [config]]
=======
   [clojure.string :as string]
   [panaeolus.config :refer [config update-config!]]
>>>>>>> prompt
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

(defn spawn-jackd-windows! [interface-name]
  (let [kill-callback (fn [] (reset! jack-server-atom nil) (println "jackd.exe died"))]
    (async/thread
      (let [jackd-file (io/file jna-path/libcsound-cache-path "jackd.exe")
            jackd-loc (.getAbsolutePath jackd-file)
            pbuilder (ProcessBuilder. ^"[Ljava.lang.String;" 
			           (into-array String (into ["cmd.exe" "/c" (str jackd-loc) "-R" 
					                             "-d" "portaudio" 
												 "-p" (str (or (get-in @config [:jack :period]) 2048))
												 "-r" (str (or (:sample-rate @config) 44100))]
					                            (if interface-name ["-d" interface-name] []))))
			_         (.redirectErrorStream pbuilder true)
            process  (.start pbuilder)]
        (reset! jack-server-atom process)
        #_(try (with-open [error-stream (clojure.java.io/reader (.getErrorStream process))]
               (loop []
                 (when-let [line (.readLine ^java.io.BufferedReader error-stream)]
                   (binding [*out* *err*]
				     (println line))
                   (recur))))
             (catch java.io.IOException e kill-callback nil))
        (try (with-open [reader (clojure.java.io/reader (.getInputStream process))]
               (loop []
                 (when-let [line (.readLine ^java.io.BufferedReader reader)]
				   (println line)
                   (recur))))
             (catch java.io.IOException e kill-callback nil))
		(.waitFor process)))))

(defn- jack-is-running?
  "Query the jack ports to see if it's running.
   This is useful to do before attemting external
   server connection on Linux, as not to fail silently"
  []
  (let [exit-code (:exit (shell/sh "jack_lsp"))]
    (zero? exit-code)))

(defn get-jackd-interfaces-windows []
  (kill-jackd-windows!)
  (let [;; interfaces (atom [])
        jackd-file (io/file jna-path/libcsound-cache-path "jackd.exe")
        jackd-loc (.getAbsolutePath jackd-file)
        ;; process (-> (ProcessBuilder. ^"[Ljava.lang.String;" 
        ;;                 (into-array String ["cmd.exe" "/c" (str "\"" jackd-loc "\"") "-d" "portaudio" "-l"]))
        ;;          (.start))
		return (shell/sh "cmd.exe" "/c" (str "\"" jackd-loc "\"") "-d" "portaudio" "-l")
	    ]
	(loop [lines (string/split (:out return) #"\n")
	       interfaces []]
	  (if (empty? lines)
	     interfaces
		 (recur (rest lines)
		         (if-let [device (re-find #"Name\s+=\s(.*)" (first lines))]
				   (conj interfaces (second device))
				   interfaces))))
	 ;; @interfaces
	 ))

#_(with-open [reader (clojure.java.io/reader (.getInputStream process))]
	  (loop []
        (if-let [line (.readLine ^java.io.BufferedReader reader)]
		  (do (when-let [device (re-find #"Name\s+=\s(.*)" line)]
            (swap! interfaces conj (second device)))
          (recur))
		  (.close reader))))

(defn ensure-connected-windows [chan]
  (async/go-loop [retry 0]
    (async/<! (async/timeout 1000))
	(if (= 5 retry)
	   (async/>! chan true)
	;; in this case, exit code 1 is true and 0 false
    (let [connected? (= 1 (:exit (shell/sh "cmd.exe" "/c" "tasklist.exe" "/fi" "imagename eq jackd.exe"  "|" 
	                                       "find.exe" "\":\"" ">" "nul")))]
		(flush)
		(if-not connected?
		  (async/>! chan false)
		  (recur (inc retry)))))))

(when (= :windows (jna-path/get-os))
  (let [ready-chan (async/chan 1)]
    (async/go-loop []
      (let [interfaces (into ["(auto)"] (get-jackd-interfaces-windows))]
        (println "[pae:jack:choose-interface]")
		(println (apply str (map-indexed (fn [idx interface] (str " > " idx " " interface "\n")) interfaces)))
		(println "Choose your audio-output device (type number from 0 to" (dec (count interfaces)) ")" )
		(flush)
          (let [choice (read-line)
                choice (if (re-matches #"[0-9]+$" choice) 
                         (Integer/parseInt choice)
                         false)
				wait-chan (async/chan 1)]
            (when-not (false? choice)
              (if (zero? choice)
                (spawn-jackd-windows! nil)
                (spawn-jackd-windows! (nth interfaces choice))))
			(ensure-connected-windows wait-chan)
			(flush)
			(if (async/<! wait-chan)
			  (do (update-config! (fn [current-config] (assoc-in current-config [:jack :interface] (if (zero? choice) nil (nth interfaces choice)))))
			    (async/>! ready-chan true))
			  (recur)))))
    (async/<!! ready-chan)))

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
