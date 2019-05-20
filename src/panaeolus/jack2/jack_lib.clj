(ns panaeolus.jack2.jack-lib
  (:require panaeolus.utils.jna-path)
  (:import
   [org.jaudiolibs.jnajack.lowlevel JackLibraryDirect]
   [org.jaudiolibs.jnajack JackOptions JackStatus]
   [org.jaudiolibs.jnajack JackPortType]
   [org.jaudiolibs.jnajack JackPortFlags]
   [org.jaudiolibs.jnajack Jack JackException]
   [java.util List EnumSet]
   [java.nio FloatBuffer]
   [java.util Arrays]
   [com.sun.jna.ptr IntByReference]
   [com.sun.jna NativeLong]))

(def jack-server (Jack/getInstance))

(defn open-client [client-name]
  (.openClient jack-server client-name
               (EnumSet/of JackOptions/JackNoStartServer)
               nil))

(def jack-client (open-client "__jnajack__"))

(defn connect [from-out to-in]
  ;; (prn "CONNECT FROM OUT" from-out "TO IN" to-in)
  (try (.connect jack-server from-out to-in)
       (catch JackException e nil)))

(defn disconnect [from-out to-in]
  ;; (prn "DISCONNECT FROM OUT" from-out "TO IN" to-in)
  (try (.disconnect jack-server from-out to-in)
       (catch JackException e nil)))

(defn get-connections [port-name]
  (try
    (vec (sort (Arrays/asList (.getAllConnections jack-server jack-client port-name))))
    (catch Exception e nil)))

(defn query-connection
  "the string can be partial and will serve as regex in native env"
  [string]
  (vec (Arrays/asList (.getPorts jack-server string  nil nil ))))


