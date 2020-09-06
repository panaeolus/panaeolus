(ns panaeolus.jack2.jack-lib
  (:require
    [clojure.java.io :as io]
    [clojure.java.shell :as shell]
    [clojure.core.async :as async]
    [clojure.string :as string]
    [panaeolus.config :refer [config update-config!]]
    [panaeolus.utils.jna-path :as jna-path])
  (:import
   [org.jaudiolibs.jnajack
    Jack
    JackClient
    JackClientRegistrationCallback
    JackException
    JackPortFlags
    JackPortRegistrationCallback
    JackPortType
    JackStatus
    JackOptions]
   [org.jaudiolibs.jnajack.lowlevel
    JackLibrary
    JackLibrary$_jack_client
    JackLibrary$_jack_port
    JackLibrary$JackClientRegistrationCallback
    JackLibrary$JackThreadCallback
    JackLibrary$JackPortFlags
    JackLibrary$JackProcessCallback
    JackLibraryDirect]
   [java.lang ProcessBuilder]
   [java.util List EnumSet]
   [java.nio FloatBuffer]
   [java.util Arrays]
   [com.sun.jna.ptr ByteByReference IntByReference]
   [com.sun.jna NativeLong Pointer]))

(set! *warn-on-reflection* true)

(def ^JackLibraryDirect jack-lib
  (new JackLibraryDirect))

(defn new-jack-thread-callback
  [^JackLibrary$_jack_client jack-client callback]
  (reify JackLibrary$JackThreadCallback
    (^Pointer invoke [_ ^Pointer arg]
     (let [res (callback)]
      (.jack_cycle_signal jack-lib jack-client res))
     (let [nframes (.jack_cycle_wait jack-lib jack-client)
           status (callback nframes)]
       (.jack_cycle_signal jack-lib jack-client (or status -1))))))

(defn set-thread-callback
  [^JackLibrary$_jack_client jack-client callback]
  (let [^JackLibrary$JackThreadCallback
        cb (new-jack-thread-callback
            jack-client callback)]
    (.jack_set_process_thread ^JackLibraryDirect jack-lib jack-client cb nil)))

(defn set-process-callback
  [^JackLibrary$_jack_client jack-client
   ^JackLibrary$JackProcessCallback callback]
  (.jack_set_process_callback ^JackLibraryDirect jack-lib jack-client callback nil))

(defn activate-thread
  [^JackLibrary$_jack_client jack-client]
  (.jack_activate jack-lib jack-client))

(defn deactivate-thread
  [^JackLibrary$_jack_client jack-client]
  (.jack_deactivate jack-lib jack-client))

(defn create-input-port
  [^JackLibrary$_jack_client jack-client channel-index]
  (.jack_port_register
   jack-lib jack-client (str "input_" channel-index)
   JackLibrary/JACK_DEFAULT_AUDIO_TYPE
   (NativeLong. JackLibrary$JackPortFlags/JackPortIsInput)
   (NativeLong. 0)))

(defn create-output-port
  [^JackLibrary$_jack_client jack-client channel-index]
  (.jack_port_register
   jack-lib jack-client (str "output_" channel-index)
   JackLibrary/JACK_DEFAULT_AUDIO_TYPE
   (NativeLong. JackLibrary$JackPortFlags/JackPortIsOutput)
   (NativeLong. 0)))

(defn get-buffer
  [^JackLibrary$_jack_port port nframes]
  (.jack_port_get_buffer jack-lib port nframes))

(defn get-buffer-size
  [^JackLibrary$_jack_client jack-client]
  (.jack_get_buffer_size jack-lib jack-client))

(defn open-client
  [^String client-name]
  (.jack_client_open
   jack-lib client-name
   (int 0) ^IntByReference (IntByReference.)))

(defn get-client-name
  [^JackLibrary$_jack_client jack-client]
  (.jack_get_client_name jack-lib jack-client))

(defn close-client
  [^JackLibrary$_jack_client jack-client]
  (.jack_client_close jack-lib jack-client))

(defn set-registration-callback
  [^JackLibrary$_jack_client client
   ^JackLibrary$JackClientRegistrationCallback reg-cb]
  (.jack_set_client_registration_callback jack-lib client reg-cb Pointer/NULL))

(defn make-client-registration-callback
  [{:keys [on-registration on-unregistration]}]
  (reify JackLibrary$JackClientRegistrationCallback
    (^void invoke [_ ^ByteByReference clientName ^int register ^Pointer arg]
     (if (zero? register)
       (on-unregistration (.toString clientName))
       (on-registration (.toString clientName))))))

(defn get-port-name
  [^JackLibrary$_jack_port port]
  (.jack_port_name jack-lib port))

(defn connect
  [^JackLibrary$_jack_client client source-port destination-port]
  (.jack_connect jack-lib client  source-port destination-port))

(defn disconnect
  [^JackLibrary$_jack_client client source-port destination-port]
  (.jack_disconnect jack-lib client  source-port destination-port))

(def jack-server-atom (atom nil))

(defn kill-jackd-windows!
  []
  (->
    (ProcessBuilder.
      ^"[Ljava.lang.String;"
      (into-array String ["cmd.exe" "/c" "taskkill" "/F" "/IM" "jackd.exe"]))
    (.start)
    (.waitFor)))

(defn spawn-jackd-windows!
  [interface-name]
  (let [kill-callback
          (fn [] (reset! jack-server-atom nil) (println "jackd.exe died"))]
    (async/thread
      (let [jackd-file (io/file jna-path/libcsound-cache-path "jackd.exe")
            jackd-loc (.getAbsolutePath jackd-file)
            pbuilder
              (ProcessBuilder.
                ^"[Ljava.lang.String;"
                (into-array
                  String
                  (into
                    ["cmd.exe" "/c" (str jackd-loc) "-R" "-d" "portaudio" "-p"
                     (str (or (get-in @config [:jack :period]) 2048)) "-r"
                     (str (or (:sample-rate @config) 44100))]
                    (if interface-name ["-d" interface-name] []))))
            _ (.redirectErrorStream pbuilder true)
            process (.start pbuilder)]
        (reset! jack-server-atom process)
        (try
          (with-open [reader (clojure.java.io/reader (.getInputStream process))]
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
  (let [exit-code (:exit (shell/sh "jack_lsp"))] (zero? exit-code)))

(defn get-jackd-interfaces-windows
  []
  (kill-jackd-windows!)
  (let [jackd-file (io/file jna-path/libcsound-cache-path "jackd.exe")
        jackd-loc (.getAbsolutePath jackd-file)
        return
          (shell/sh
            "cmd.exe"
            "/c"
            (str "\"" jackd-loc "\"")
            "-d"
            "portaudio"
            "-l")]
    (loop [lines (string/split (:out return) #"\n")
           interfaces []]
      (if (empty? lines)
        interfaces
        (recur
          (rest lines)
          (if-let [device (re-find #"Name\s+=\s(.*)" (first lines))]
            (conj interfaces (second device))
            interfaces))))))

(defn ensure-connected-windows
  [chan]
  (async/go-loop
    [retry 0]
    (async/<! (async/timeout 1000))
    (if (= 5 retry)
      (async/>! chan true)
      ;; in this case, exit code 1 is true and 0 false
      (let [connected?
              (=
                1
                (:exit
                  (shell/sh
                    "cmd.exe" "/c"
                    "tasklist.exe" "/fi"
                    "imagename eq jackd.exe" "|"
                    "find.exe" "\":\""
                    ">" "nul")))]
        (flush)
        (if-not connected? (async/>! chan false) (recur (inc retry)))))))

(when (= :windows (jna-path/get-os))
  (let [ready-chan (async/chan 1)]
    (async/go-loop
      []
      (let [interfaces (into ["(auto)"] (get-jackd-interfaces-windows))]
        (println "[pae:jack:choose-interface]")
        (println
          (apply str
            (map-indexed
              (fn [idx interface] (str " > " idx " " interface "\n"))
              interfaces)))
        (println
          "Choose your audio-output device (type number from 0 to"
          (dec (count interfaces))
          ")")
        (flush)
        (let [choice (read-line)
              choice
                (if (re-matches #"[0-9]+$" choice)
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
            (do
              (update-config!
                (fn [current-config]
                  (assoc-in current-config
                    [:jack :interface]
                    (if (zero? choice) nil (nth interfaces choice)))))
              (async/>! ready-chan true))
            (recur)))))
    (async/<!! ready-chan)))

(defn- wait-until-jack-connects
  [ready-chan]
  (async/go-loop
    []
    (if (jack-is-running?)
      (async/>! ready-chan true)
      (do (async/<! (async/timeout 1000)) (recur)))))

(when (and (= :linux (jna-path/get-os)) (not (jack-is-running?)))
  (println "[pae:jack:not-running]")
  (let [wait-chan (async/chan 1)]
    (wait-until-jack-connects wait-chan)
    (async/<!! wait-chan)
    (println "[pae:jack:started]")))

#_(def jack-server
  ^Jack (Jack/getInstance))

#_(defn open-client
    [client-name]
    (.openClient
      ^Jack jack-server
      client-name
      (EnumSet/of JackOptions/JackNoStartServer)
      nil))

#_(defn open-client
  [^String client-name]
  (.jack_client_open
   jack-lib client-name 0
   (IntByReference. 0)))

#_(def jack-client
  (open-client "__jnajack__"))

#_(defn connect
    [from-out to-in]
    ;; (prn "CONNECT FROM OUT" from-out "TO IN" to-in)
    (.connect ^Jack jack-server from-out to-in))

#_(defn disconnect
    [from-out to-in]
    ;; (prn "DISCONNECT FROM OUT" from-out "TO IN" to-in)
    (.disconnect ^Jack jack-server from-out to-in))

#_(defn get-connections
    [^String port-name]
    (vec
     (sort
      (Arrays/asList
       (.getAllConnections
        ^Jack jack-server
        ^JackClient jack-client
        port-name)))))

#_(defn query-connection
  "the string can be partial and will serve as regex in native env"
  [^String qstring]
  (vec (Arrays/asList (.getPorts ^Jack jack-server qstring  nil nil ))))
