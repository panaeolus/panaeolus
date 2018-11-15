(ns panaeolus.csound.csound-jna
  (:require [clj-native.direct :refer [defclib loadlib]]
            [clj-native.structs :refer [byref]]
            [clj-native.callbacks :refer [callback]]
            panaeolus.utils.jna-path
            [panaeolus.jack2.jack-lib :as jack]
            [panaeolus.config :refer [config]]
            [clojure.string :as string])
  (:import [com.kunstmusik.csoundjna Csound MessageCallback]
           [org.jaudiolibs.audioservers AudioClient]
           [java.util List]
           [java.nio FloatBuffer]
           [java.nio DoubleBuffer]))

(defn csound-create []
  (new Csound))

(def get-version (memfn getVersion))

(defn cleanup [^Csound instance]
  (.cleanup instance))

(defn compile-csd-text [^Csound instance ^String csd-text]
  (.compileCsdText instance csd-text))

(defn compile-orc [^Csound instance ^String orc]
  (.compileOrc instance orc))

(defn compile-orc-async [^Csound instance ^String orc]
  (.compileOrcAsync instance orc))

(defn perform-ksmps [^Csound instance]
  (.performKsmps instance))

(defn reset [^Csound instance]
  (.reset instance))

(defn set-message-callback [^Csound instance callback]
  (let [msg-cb ^MessageCallback
        (reify MessageCallback
          (invoke [this inst
                   attr msg]
            (callback attr msg)))]
    (.setMessageCallback instance msg-cb)))

(defn set-option [^Csound instance ^String option]
  (.setOption instance option))

(defn start [^Csound instance]
  (.start instance))

(defn stop [^Csound instance]
  (.stop instance))

(require '[panaeolus.sequence-parser :refer [sequence-parser]]
         '[panaeolus.event-loop :refer [event-loop]])

#_(defn squeeze-in-minilang-pattern [args orig-arglists]
    (let [{:keys [time nn]} (sequence-parser (second args))
          args              (vec args)]
      (doall
       (concat (list (first args) (vec time) (vec nn))
               (if (some #(= :dur %) orig-arglists)
                 [:dur (vec time)]
                 '())
               (subvec args 2)))))

(def params [{:amp {:default -12}}
             {:nn {:default 60}}])

(defn pattern-control [i-name envelope-type original-parameters csound-instance]
  (fn [& args]
    (let [orig-params-keys
          ;; args (if (string? (second args))
          ;;        (squeeze-in-minilang-pattern args original-parameters)
          ;;        args)
          ;; [pat-ctl pat-num]
          ;; (if-not (keyword? (first args))
          ;;   [nil nil]
          ;;   (let [ctl     (name (first args))
          ;;         pat-num (or (re-find #"[0-9]+" ctl) 0)
          ;;         ctl-k   (keyword (first (string/split ctl #"-")))]
          ;;     [ctl-k pat-num]))
          ]
      #_(case pat-ctl
          :loop (do
                  ;; (control/unsolo)
                  (event-loop (str i-name "-" pat-num)
                              instrument-instance
                              args
                              :envelope-type envelope-type
                              :audio-backend :csound))
          ;; :stop (control/overtone-pattern-kill (str i-name "-" pat-num))
          ;; :solo (do (control/solo! (str i-name "-" 0))
          ;;           (event-loop (str i-name "-" pat-num)
          ;;                       instrument-instance
          ;;                       args
          ;;                       :envelope-type envelope-type
          ;;                       :audio-backend :overtone))
          ;; :kill (control/overtone-pattern-kill (str i-name "-: pat-num))
          ;; (apply instrument-instance (rest (rest args)))
          )
      ;; pat-ctl
      )))


(defn spawn-csound-client [client-name inputs outputs ksmps]
  (let [csnd   (csound-create)
        status (atom :init)
        thread (agent csnd)]
    (run! #(set-option csnd %)
          ["-iadc:null" "-odac:null"
           "--messagelevel=35"
           "-B 4096"
           "-b 512"
           (str "--nchnls=" inputs)
           (str "--nchnls_i=" outputs)
           "--0dbfs=1"
           "-+rtaudio=jack"
           "--sample-rate=48000"
           (str "--ksmps=" ksmps)
           (str "-+jack_client=" client-name)])
    (start csnd)
    (set-message-callback
     csnd (fn [attr msg] (println msg)))
    {:instance csnd
     :start    #(send-off thread
                          (fn [instance]
                            (reset! status :running)
                            (while (and (= :running @status) (zero? (perform-ksmps instance))))
                            (doto instance stop)
                            (doto instance cleanup)))
     :stop     #(when-not (= :stop @status)
                  (reset! status :stop))}))

(comment
  (def tezt (spawn-csound-client "csound-2" 2 2 1))

  ;; ((:init test))

  @(:status tezt)

  ((:start tezt))

  ((:stop tezt))

  ((:kill tezt))

  (jack/connect "csound-2:output1" "system:playback_1")
  (jack/connect "csound-2:output2" "system:playback_2")

  (jack/disconnect "csound-3:output1" "system:playback_1")
  (jack/disconnect "csound-3:output2" "system:playback_2")

  (compile-orc (:instance test) "print 2")

  (compile-orc (:instance tezt) "
       instr 1
       asig = poscil:a(0.01, 2389)
       print 666
       outc asig, asig
       endin
       schedule(1, 0, 30)
")

  (perform-ksmps (:instance tezt))

  (perform-ksmps test3)

  (compile-orc test2 "schedule(1, 0, 1)")
  )
