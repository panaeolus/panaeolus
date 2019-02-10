(ns panaeolus.jack2.jack-lib
  (:require panaeolus.utils.jna-path)
  (:import
   [org.jaudiolibs.jnajack.lowlevel JackLibraryDirect]
   [org.jaudiolibs.jnajack JackOptions JackStatus]
   [org.jaudiolibs.jnajack JackPortType]
   [org.jaudiolibs.jnajack JackPortFlags]
   ;; [org.jaudiolibs.audioservers.jack JackAudioServer]
   ;; [org.jaudiolibs.audioservers AudioConfiguration AudioClient]
   [org.jaudiolibs.jnajack Jack]
   [java.util List EnumSet]
   [java.nio FloatBuffer]
   [com.sun.jna.ptr IntByReference]
   [com.sun.jna NativeLong]))

(def jack-server (Jack/getInstance))

(defn open-client [client-name]
  (.openClient jack-server client-name
               (EnumSet/of JackOptions/JackNoStartServer)
               nil))

(defn connect [from-out to-in]
  (prn "CONNECT FROM OUT" from-out "TO IN" to-in)
  (.connect jack-server from-out to-in))

(defn disconnect [from-out to-in]
  (prn "DISCONNECT FROM" from-out "TO IN" to-in)
  (.disconnect jack-server from-out to-in))

#_(defn config-create [inputs outputs]
    (let [sample-rate        44100 ;; ignored: taken from jack
          buffer-size        1024  ;; ignored: taken from jack
          fixed-buffer-size? true  ;; always true
          ]
      (new AudioConfiguration sample-rate inputs outputs buffer-size fixed-buffer-size? nil)))

;; (def JackConfig (config-create 2 2 true))
;; (def JackClient )

#_(defn new-jack-client [name inputs outputs processor]
    (let [audio-config (config-create inputs outputs)
          audio-client (reify AudioClient
                         (process [this time inputs outputs nframes]
                           (processor this inputs outputs nframes))
                         (configure [this context]
                           audio-config)
                         (shutdown [this]))
          instance     (JackAudioServer/create name audio-config false audio-client)
          thread       (Thread. (fn [] (.run instance)))
          ]
      (.start thread)
      thread))


;; NOT USING THIS BELOW
(comment
  (def jackLib (new JackLibraryDirect))


  (def ^:private AUDIO-PORT-TYPE JackPortType/AUDIO)
  (def ^:private AUDIO-PORT-BUFFER-SIZE (.getBufferSize AUDIO-PORT-TYPE))
  (def ^:private AUDIO-PORT-BUFFER-SIZE-NATIVE-LONG (new NativeLong AUDIO-PORT-BUFFER-SIZE))
  (def ^:private AUDIO-PORT-TYPE-STRING (.getTypeString AUDIO-PORT-TYPE))
  (def ^:private MIDI-PORT-TYPE JackPortType/MIDI)
  (def ^:private MIDI-PORT-BUFFER-SIZE (.getBufferSize MIDI-PORT-TYPE))
  (def ^:private MIDI-PORT-BUFFER-SIZE-NATIVE-LONG (new NativeLong MIDI-PORT-BUFFER-SIZE))
  (def ^:private MIDI-PORT-TYPE-STRING (.getTypeString MIDI-PORT-TYPE))
  (def ^:private JACK-PORT-IS-INPUT JackPortFlags/JackPortIsInput)
  (def ^:private JACK-PORT-IS-INPUT-INT (.getIntValue JACK-PORT-IS-INPUT))
  (def ^:private JACK-PORT-IS-INPUT-NATIVE-LONG (new NativeLong JACK-PORT-IS-INPUT-INT))
  (def ^:private JACK-PORT-IS-OUTPUT JackPortFlags/JackPortIsOutput)
  (def ^:private JACK-PORT-IS-OUTPUT-INT (.getIntValue JACK-PORT-IS-OUTPUT))
  (def ^:private JACK-PORT-IS-OUTPUT-NATIVE-LONG (new NativeLong JACK-PORT-IS-OUTPUT-INT))


  (defn client-create [client-name]
    (let [stat-ref (new IntByReference 0)
          no-start ^JackOptions (.getIntValue JackOptions/JackNoStartServer)
          instance (.jack_client_open ^JackLibraryDirect jackLib ^String client-name
                                      no-start  ^IntByReference stat-ref)]
      (when-not instance
        (throw (AssertionError. "jack server is not running!" )))
      instance))

  (defn client-register-port
    [^JackLibraryDirect client-instance ^String port-name port-type port-direction]
    (.jack_port_register
     ^JackLibraryDirect jackLib
     client-instance port-name
     ^String (case port-type
               :audio AUDIO-PORT-TYPE-STRING
               :midi  MIDI-PORT-TYPE-STRING)
     ^NativeLong (case port-direction
                   :input  JACK-PORT-IS-INPUT-NATIVE-LONG
                   :output JACK-PORT-IS-OUTPUT-NATIVE-LONG)
     ^NativeLong (case port-type
                   :audio AUDIO-PORT-BUFFER-SIZE-NATIVE-LONG
                   :midi  MIDI-PORT-BUFFER-SIZE-NATIVE-LONG) )))


(comment
  (def test3 (client-create "ppp"))
  (def p1 (client-register-port test3 "inpX" :audio :input))
  (.jack_activate jackLib test1)
  (.jack_client_close jackLib test1 )
  (.jack_get_client_name jackLib test1))
