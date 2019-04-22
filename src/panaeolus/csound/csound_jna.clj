(ns panaeolus.csound.csound-jna
  (:require [clj-native.direct :refer [defclib loadlib]]
            [clj-native.structs :refer [byref]]
            [clj-native.callbacks :refer [callback]]
            [clojure.core.async :refer [go go-loop chan alts! <! >! close! timeout] :as async]
            panaeolus.utils.jna-path
            [panaeolus.csound.utils :as csound-utils]
            [panaeolus.jack2.jack-lib :as jack]
            [panaeolus.config :refer [config]]
            [clojure.string :as string])
  (:import [com.kunstmusik.csoundjna Csound MessageCallback]
           ;; [java.util List]
           ;; [java.nio FloatBuffer]
           ;; [java.nio DoubleBuffer]
           ))

(defn debounce
  "https://gist.github.com/scttnlsn/9744501"
  [in ms]
  (let [out (chan)]
    (go-loop [last-val nil]
      (let [val   (if (nil? last-val) (<! in) last-val)
            timer (timeout ms)
            [new-val ch] (alts! [in timer])]
        (condp = ch
          timer (do (when-not
                        (>! out val)
                      (close! in))
                    (recur nil))
          in (if new-val (recur new-val)))))
    out))

(go (let [in  (chan)
          out (debounce in 9000)]
      (prn "pre1")
      (<! (timeout 2000))
      (prn "pre2")
      (>! in 1000)
      ;; (>! in 300)
      (prn "VAL!" (<! out))
      (println "HÆ")))

;; JNA hack
;; (doto (new Csound) (.cleanup))

(def csound-instances (atom {}))

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

(defn input-message-closure
  [csound-thread synth-form csound-instrument-number debounce-channel release-time isFx?]
  (let [param-vector (reduce into [] (map #(vector (:name %) (:default %)) synth-form))]
    (fn [& args]
      (let [processed-args (csound-utils/process-arguments param-vector args)
            p-list (reduce (fn [i v]
                             (conj i
                                   (get processed-args (:name v)
                                        (:default v))))
                           []
                           synth-form)]
        (input-message
         csound-thread
         (clojure.string/join
          " " (into ["i" csound-instrument-number "0" (if isFx? "0.1" "")] p-list)))
        (when debounce-channel (async/>!! debounce-channel (+ (first p-list) release-time)))))))


#_(require '[panaeolus.sequence-parser :refer [sequence-parser]]
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

;; (instrument-instance )

;; (event-loop "prufa" tezt '(:nn [36 38 40] :amp -20) :envelope-type :perc :audio-backend :csound)

(defn spawn-csound-client
  [client-name inputs outputs ksmps
   synth-form csound-instrument-number
   release-time isFx?]
  (let [csnd   (csound-create)
        status (atom :init)
        thread (agent csnd)
        debounce-channel (when-not isFx? (chan (async/sliding-buffer 1)))
        release-channel (when-not isFx? (debounce debounce-channel release-time))]
    (run! #(set-option csnd %)
          ["-iadc:null" "-odac:null"
           "--messagelevel=35"
           "-B 4096"
           "-b 512"
           (str "--nchnls=" outputs)
           (str "--nchnls_i=" inputs)
           "--0dbfs=1"
           "-+rtaudio=jack"
           "--sample-rate=48000"
           (str "--ksmps=" ksmps)
           (str "-+jack_client=" client-name)])
    (start csnd)
    (set-message-callback
     csnd (fn [attr msg] (print msg)))
    {:instance csnd
     :client-name client-name
     :start    #(send-off thread
                          (fn [instance]
                            (reset! status :running)
                            (while (and (= :running @status) (zero? (perform-ksmps instance))))
                            (doto instance stop)
                            (doto instance cleanup)))
     :stop     #(when-not (= :stop @status)
                  (reset! status :stop))
     :send (input-message-closure
            csnd synth-form
            csound-instrument-number debounce-channel release-time isFx?)
     :compile (fn [orc] (compile-orc csnd orc))
     :inputs (mapv #(hash-map :port-name (str client-name ":input" (inc %))
                              :connected-from-instance nil
                              :connected-from-port nil
                              :channel-index %)
                   (range inputs))
     :outputs (mapv #(hash-map :port-name (str client-name ":output" (inc %))
                               :connected-to-instance nil
                               :connected-to-port nil
                               :channel-index %)
                    (range outputs))
     :debounce-channel debounce-channel
     :release-channel release-channel
     :release-time release-time}))

(comment
  (def tezt2 (spawn-csound-client "csound-101" 2 2 1))

  ;; ((:init test))
  (print "a")
  @(:status tezt2)

  ((:start tezt2))

  ((:stop tezt))

  ((:kill tezt))

  (jack/connect "csound-101:output1" "system:playback_1")
  (jack/connect "csound-1:output2" "system:playback_2")

  (jack/disconnect "csound-3:output1" "system:playback_1")
  (jack/disconnect "csound-3:output2" "system:playback_2")

  (compile-orc (:instance tezt2) "print 0dbfs")

  (compile-orc (:instance tezt2) "
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
