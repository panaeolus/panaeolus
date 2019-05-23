(ns panaeolus.csound.csound-jna
  (:require [clojure.core.async :refer [go go-loop chan alts! <! >! close! timeout] :as async]
            panaeolus.utils.jna-path
            [panaeolus.globals :as globals]
            [panaeolus.csound.utils :as csound-utils]
            [panaeolus.jack2.jack-lib :as jack]
            [panaeolus.config :as config]
            [clojure.string :as string])
  (:import [com.kunstmusik.csoundjna Csound MessageCallback]))

(set! *warn-on-reflection* true)

(defn debounce
  "https://gist.github.com/scttnlsn/9744501"
  [in ms pattern-name]
  (let [out (chan)]
    (go-loop [last-val nil]
      (let [val   (if (nil? last-val) (<! in) last-val)
            timer (timeout ms)
            [new-val ch] (alts! [in timer])]
        (condp = ch
          timer (if (contains? @globals/pattern-registry pattern-name)
                  (recur ms)
                  (do (when-not (>! out val)
                        (close! in))
                      (recur nil)))
          in (if new-val
               (recur new-val)
               (if (contains? @globals/pattern-registry pattern-name)
                 (recur ms))))))
    out))

;; JNA hack
;; (doto (new Csound) (.cleanup))

(def csound-instances (atom {}))

(defn csound-create []
  (new Csound))

(defn cleanup [^Csound instance]
  (.cleanup instance))

(defn compile-csd-text [^Csound instance ^String csd-text]
  (.compileCsdText instance csd-text))

(defn compile-orc [^Csound instance ^String orc]
  (.compileOrc instance orc))

(defn compile-orc-async [^Csound instance ^String orc]
  (.compileOrcAsync instance orc))

(defn eval-code [^Csound instance ^String orc]
  (.evalCode instance orc))

(defn input-message [^Csound instance ^String sco]
  (.inputMessage instance sco))

(defn input-message-async [^Csound instance ^String sco]
  (.inputMessageAsync instance sco))

(defn read-score [^Csound instance ^String sco]
  (.readScore instance sco))

(defn read-score-async [^Csound instance ^String sco]
  (.readScoreAsync instance sco))

(defn perform-ksmps [^Csound instance]
  (.performKsmps instance))

(defn reset [^Csound instance]
  (.reset instance))

(def message-callback
  (reify MessageCallback
    (invoke [this inst
             attr msg]
      (print msg)
      (flush))))

(defn set-option [^Csound instance ^String option]
  (.setOption instance option))

(defn start [^Csound instance]
  (.start instance))

(defn stop [^Csound instance]
  (.stop instance))

(defn input-message-closure
  [synth-form csound-instrument-number release-time isFx?]
  (let [param-vector (reduce into [] (map #(vector (:name %) (:default %)) synth-form))]
    (fn [csnd debounce-channel]
      (fn [& args]
        (when csound-instrument-number
          (let [processed-args (csound-utils/process-arguments param-vector args)
                p-list (reduce (fn [i v]
                                 (conj i
                                       (get processed-args (:name v)
                                            (:default v))))
                               []
                               synth-form)]
            (input-message-async
             @csnd
             (clojure.string/join
              " " (into ["i" csound-instrument-number "0"] p-list)))
            (when debounce-channel
              (async/go (async/>! debounce-channel (+ (or (first p-list) 0)
                                                      release-time))))))))))

(defn spawn-csound-client
  [client-name inputs outputs config
   release-time isFx? input-msg-cb]
  (let [csnd   (atom (csound-create))
        status (atom :init)
        thread (agent nil)
        debounce-channel (when-not isFx? (chan (async/sliding-buffer 1)))
        release-channel (when-not isFx? (debounce debounce-channel release-time client-name))]
    (run! #(set-option @csnd %)
          ["-iadc:null" "-odac:null"
           (str "--messagelevel=" (or (:csound-messagelevel config) (:csound-messagelevel @config/config)))
           (str "-B " (or (:hardwarebufsamps config) (:hardwarebufsamps @config/config)))
           (str "-b " (or (:iobufsamps config) (:iobufsamps @config/config)))
           (str "--nchnls=" outputs)
           (str "--nchnls_i=" inputs)
           (str "--0dbfs=" (or (:zerodbfs config) 1))
           "-+rtaudio=jack"
           (str "--sample-rate=" (or (:sample-rate config) (:sample-rate @config/config)))
           (str "--ksmps=" (or (:ksmps config) (:ksmps @config/config)))
           (str "-+jack_client=" client-name)])
    (start @csnd)
    (.setMessageCallback ^Csound @csnd message-callback)
    {:instance csnd
     :client-name client-name
     :start    #(send-off thread
                          (fn [& r]
                            (reset! status :running)
                            (while (and (= :running @status) (zero? (perform-ksmps @csnd))))
                            (doto @csnd stop)
                            (doto @csnd cleanup)))
     :stop     #(when-not (= :stop @status)
                  (reset! status :stop))
     :send (fn [& args] (apply (input-msg-cb csnd debounce-channel) args))
     :compile (fn [orc] (compile-orc @csnd orc))
     :inputs (mapv #(hash-map :port-name (str client-name ":input" (inc %))
                              :connected-from-instances []
                              :connected-from-ports []
                              :channel-index %)
                   (range inputs))
     :outputs (mapv #(hash-map :port-name (str client-name ":output" (inc %))
                               :connected-to-instances []
                               :connected-to-ports []
                               :channel-index %)
                    (range outputs))
     :debounce-channel debounce-channel
     :release-channel release-channel
     :release-time release-time
     :scheduled-to-kill? false}))

(comment
  (def tezt3 (spawn-csound-client "csound#fx#1" 2 2 1 60 false (fn [& rest])))

  ;; ((:init test))
  (print "a")
  @(:status tezt2)

  ((:start tezt3))

  ((:stop tezt2))

  ((:kill tezt2))

  (jack/connect "csound-102:output1" "system:playback_1")
  (jack/connect "csound-1:output2" "system:playback_2")

  (jack/disconnect "csound-3:output1" "system:playback_1")
  (jack/disconnect "csound-3:output2" "system:playback_2")

  (compile-orc @(:instance tezt2) "print 0dbfs")
  ((:compile tezt2) "print 0dbfs")

  ((:compile tezt3) "instr 1
       asig = poscil:a(ampdb(p4), cpsmidinn(p5))
       outc asig*1000, asig*1000
       endin
        print 1
       schedule(1, 0, 3, -2, 60)
")

  ((:compile tezt2) "
  opcode binauralize, aa, akk

  ain,kcent,kdiff xin
  ifftsz = 1024
  ; determine pitches
  kp1 = kcent + (kdiff/2)
  kp2 = kcent - (kdiff/2)
  krat1 = kp1 / kcent
  krat2	= kp2 / kcent
  ; take it apart
  fsig pvsanal	ain, ifftsz, ifftsz/4, ifftsz, 1
  ; create derived streams
  fbinL	pvscale	fsig, krat1, 1
  fbinR	pvscale	fsig, krat2, 1
  ; put it back together
  abinL	pvsynth	fbinL
  abinR	pvsynth	fbinR
  ; send it out
  xout abinL, abinR
  endop
       instr 1
       asig = poscil:a(ampdb(p4), cpsmidinn(p5))
       outc asig, asig
       endin
       schedule(1, 0, 3, -21, 70)
")

  (perform-ksmps (:instance tezt))

  (perform-ksmps test3)

  (compile-orc (:instance tezt2) "schedule(1, 0, 1, -12, 60)")
  )
