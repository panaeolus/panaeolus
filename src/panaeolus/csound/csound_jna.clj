(ns panaeolus.csound.csound-jna
  (:require
   [clojure.core.async :as async]
   [clojure.string :as string]
   [panaeolus.utils.jna-path :as jna-path]
   [panaeolus.globals :as globals]
   [panaeolus.metronome :as metro]
   [panaeolus.utils.utils :as utils]
   [panaeolus.jack2.jack-lib :as jack]
   [panaeolus.config :as config]
   [tech.jna :as jna])
  (:import
   [java.util HashMap]
   [java.lang.ref SoftReference]
   [com.kunstmusik.csoundjna Csound CsoundLib MessageCallback]
   [org.jaudiolibs.jnajack.lowlevel
    JackLibrary$JackProcessCallback
    JackLibrary$JackClientRegistrationCallback]
   [com.sun.jna Native NativeLong Pointer]
   [com.sun.jna.ptr ByteByReference IntByReference]
   [com.sun.jna.platform.win32 Kernel32]
   [java.nio DoubleBuffer FloatBuffer]))

(set! *warn-on-reflection* true)

(def ^:private __WINDOWS_NATIVE_DEPS__
  (when (= :windows (jna-path/get-os))
    (jna/load-library "libstdc++-6")
    (jna/load-library "libgcc_s_seh-1")
    (jna/load-library "libgnurx-0")
    (jna/load-library "rtjack")
    (.SetEnvironmentVariable
     Kernel32/INSTANCE
     "OPCODE6DIR64"
     jna-path/libcsound-cache-path)))

(def ^:private __MAC_NATIVE_DEPS__
  (when (= :mac (jna-path/get-os))
    (jna/load-library "ogg.0")
    (jna/load-library "vorbis.0")
    (jna/load-library "vorbisenc.2")
    (jna/load-library "FLAC.8")
    (jna/load-library "sndfile.1")))

(defn csound-create []
  (CsoundLib/csoundCreate 0))

(defn cleanup
  [^Pointer instance]
  (CsoundLib/csoundCleanup instance))

(defn compile-csd-text
  [^Pointer instance ^String csd-text]
  (CsoundLib/csoundCompileCsdText instance csd-text))

(defn compile-orc
  [^Pointer instance ^String orc]
  (CsoundLib/csoundCompileOrc instance orc))

(defn compile-orc-async
  [^Pointer instance ^String orc]
  (CsoundLib/csoundCompileOrcAsync instance orc))

(defn eval-code
  [^Pointer instance ^String orc]
  (CsoundLib/csoundEvalCode instance orc))

(defn input-message
  [^Pointer instance ^String sco]
  (CsoundLib/csoundInputMessage instance sco))

(defn input-message-async
  [^Pointer instance ^String sco]
  (CsoundLib/csoundInputMessageAsync instance sco))

(defn read-score
  [^Pointer instance ^String sco]
  (CsoundLib/csoundReadScore instance sco))

(defn read-score-async
  [^Pointer instance ^String sco]
  (CsoundLib/csoundReadScoreAsync instance sco))

(defn perform-ksmps
  [^Pointer instance]
  (CsoundLib/csoundPerformKsmps instance))

(defn reset
  [^Pointer instance]
  (CsoundLib/csoundReset instance))

#_(def message-callback
    (reify
      MessageCallback
      (invoke [this inst attr msg] (print msg))))

(defn set-option
  [^Pointer instance ^String option]
  (CsoundLib/csoundSetOption instance option))

(defn start
  [^Pointer instance]
  (CsoundLib/csoundStart instance))

(defn stop
  [^Pointer instance]
  (CsoundLib/csoundStop instance))

(defn get-spin [^Pointer instance]
  (CsoundLib/csoundGetSpin instance))

(defn get-spout [^Pointer instance]
  (CsoundLib/csoundGetSpout instance))

(defn set-host-implemented-io [^Pointer instance buffer-size]
  (CsoundLib/csoundSetHostImplementedAudioIO instance 1 buffer-size))

(defn perform-buffer [^Pointer instance]
  (CsoundLib/csoundPerformBuffer instance))

(defn get-input-buffer [^Pointer instance]
  (CsoundLib/csoundGetInputBuffer instance))

(defn get-output-buffer [^Pointer instance]
  (CsoundLib/csoundGetOutputBuffer instance))

(defn input-message-closure
  [instr-form csound-instrument-number]
  (let [param-vector
        (reduce into [] (map #(vector (:name %) (:default %)) instr-form))]
    (fn [csnd]
      (fn [& args]
        (when csound-instrument-number
          (let [processed-args (utils/process-arguments param-vector args)
                p-list
                (reduce
                 (fn [i v]
                   (conj i (get processed-args (:name v) (:default v))))
                 []
                 instr-form)
                p-list (if-let [xdur (:xdur processed-args)]
                         (assoc p-list 0 (* xdur (first p-list)))
                         p-list)]
            (input-message-async
             @csnd
             (clojure.string/join
              " "
              (into ["i" csound-instrument-number "0"] p-list)))))))))

#_(defn ptr->float-buffer [^Pointer p length]
    (.asFloatBuffer
     (.getByteBuffer
      p (long 0)
      (* length Float/BYTES))))

(defn to-float [x]
  (if (> x Float/MAX_VALUE) Float/MAX_VALUE
      (if (< x Float/MIN_VALUE) Float/MIN_VALUE
          (float x))))

(defn handle-messages [csnd requested-client-name]
  (let [msg-cnt (or (CsoundLib/csoundGetMessageCnt csnd) 0)]
    (when (< 0 msg-cnt)
      (loop [msg-cnt msg-cnt
             out ""]
        (if (zero? msg-cnt)
          (println out) ;;(when-not (< -1 (.indexOf out "rtevent")) (println out))
          (let [head (CsoundLib/csoundGetFirstMessage csnd)]
            (CsoundLib/csoundPopFirstMessage csnd)
            (recur (dec msg-cnt)
                   (str out head))))))))

(def __nogc_callbacks__ (atom #{}))

(defn spawn-csound-client
  [{:keys [requested-client-name
           inputs outputs
           config release-time
           input-msg-cb]}]
  (let [csnd (atom (csound-create))
        jack-client (atom (jack/open-client requested-client-name))
        client-name (jack/get-client-name @jack-client)
        jack-buffer-size (jack/get-buffer-size @jack-client)
        jack-ports-in  (mapv #(jack/create-input-port @jack-client %) (range 1 (inc inputs)))
        jack-ports-out (mapv #(jack/create-output-port @jack-client %) (range 1 (inc outputs)))
        ;; makes sense
        ksmps-rate (or (:ksmps config)
                       (get-in @config/config [:csound :ksmps]))
        buffer-size (max
                     jack-buffer-size
                     (or (:iobufsamps config)
                         (get-in @config/config [:csound :iobufsamps])))
        status (atom :init)
        audio-callback (reify JackLibrary$JackProcessCallback
                         (^int invoke [_ ^int nframes]
                          (loop [return (perform-ksmps @csnd)
                                 w-index  0
                                 jack-input-pointers (mapv #(jack/get-buffer % nframes) jack-ports-in)
                                 memcpy-buffer-outputs (float-array (* nframes outputs))]
                            (if (zero? return)
                              (let [spin  ^Pointer (get-spin ^Pointer @csnd)
                                    spout ^Pointer (get-spout ^Pointer @csnd)]
                                (dotimes [sampl ksmps-rate]
                                  (dotimes [idx inputs]
                                    (.setDouble ^Pointer spin
                                                (long (* Double/BYTES (+ idx (* inputs sampl))))
                                                (double (.getFloat ^Pointer (nth jack-input-pointers idx)
                                                                   (long (* Float/BYTES (+ w-index sampl)))))))
                                  (dotimes [idx outputs]
                                    (aset memcpy-buffer-outputs
                                          (+ w-index sampl (* nframes idx))
                                          ^float
                                          (float
                                           (.getDouble
                                            spout
                                            (long (* Double/BYTES (+ idx (* outputs sampl)))))))))
                                (if (<= nframes (+ w-index ksmps-rate))
                                  (do
                                    (dotimes [idx outputs]
                                      (let [^Pointer buffer (jack/get-buffer (nth jack-ports-out idx) nframes)]
                                        (.write ^Pointer buffer
                                                0
                                                ^"[F" memcpy-buffer-outputs
                                                (int (* idx nframes))
                                                (int nframes))))
                                    (handle-messages @csnd requested-client-name)
                                    0)
                                  (recur (perform-ksmps @csnd)
                                         (long (+ w-index ksmps-rate))
                                         jack-input-pointers
                                         memcpy-buffer-outputs)))
                              (do
                                (when (= @status :running)
                                  (reset! csnd nil)
                                  (binding [*out* *err*]
                                    (println "FATAL:" requested-client-name
                                             "crashed <:S")))
                                -1)))))
        ;; client-reg-cb (reify JackLibrary$JackClientRegistrationCallback
        ;;                 (^void invoke [_ ^ByteByReference clientName ^int register ^Pointer arg]
        ;;                  (if (zero? register)
        ;;                    (reset! status :running))))
        ;; message-callback (reify MessageCallback
        ;;                    (^void invoke [_ ^int attr ^String msg ^Pointer args]
        ;;                     (print msg)))
        ]
    (CsoundLib/csoundCreateMessageBuffer @csnd -1)
    (run!
     #(set-option @csnd %)
     ["-odac" "-iadc" "-+rtaudio=null" "-+rtmidi=null" "--daemon" ;; "-m0"
      "--format=double"
      (str "--tempo=" (metro/get-bpm))
      ;; (str "--messagelevel=" (or (:messagelevel config) (get-in
      ;; @config/config [:csound :messagelevel]) 0))
      (str "-B " (* 2 jack-buffer-size))
      (str "-b " jack-buffer-size)
      (str "--nchnls=" outputs) (str "--nchnls_i=" inputs)
      (str "--0dbfs=" (or (:zerodbfs config) 1))
      (str "--sample-rate=" (or (:sample-rate config) (:sample-rate @config/config)))
      (str "--ksmps=" ksmps-rate)
      ;; (str "--env:OPCODE6DIR64+=" jna-path/libcsound-cache-path)
      ])
    (set-host-implemented-io ^Pointer @csnd 0)
    ;; (CsoundLib/csoundSetMessageStringCallback ^Pointer @csnd message-callback)
    (swap! __nogc_callbacks__ conj audio-callback)
    ;; (swap! __nogc_callbacks__ conj message-callback)
    ;; (swap! __nogc_client_reg_callbacks__ assoc client-name client-reg-cb)
    ;; (jack/set-registration-callback @jack-client client-reg-cb)
    (jack/set-process-callback @jack-client audio-callback)
    (start @csnd)
    {:instance csnd,
     :jack-client @jack-client
     :jack-ports-in jack-ports-in
     :jack-ports-out jack-ports-out
     :client-name client-name
     :start (fn []
              (when @csnd
                (jack/activate-thread @jack-client)
                (reset! status :running)))
     :stop (fn []
             (reset! status :stop)
             (async/go
               (async/<! (async/timeout (* 1000 release-time)))
               (when @jack-client
                 (jack/deactivate-thread @jack-client)
                 (jack/close-client @jack-client)
                 (swap! __nogc_callbacks__ disj audio-callback)
                 (reset! jack-client nil))
               (async/<! (async/timeout 100))
               (when @csnd
                 (handle-messages @csnd requested-client-name)
                 (stop @csnd)
                 (CsoundLib/csoundDestroyMessageBuffer @csnd)
                 (cleanup @csnd)
                 (reset! csnd nil))
               ;; (swap! __nogc_callbacks__ disj message-callback)
               )
             )
     :send (fn [& args]
             (when (and (= :running @status) @csnd)
               (apply (input-msg-cb csnd) args)))
     :compile (fn [orc]
                (when @csnd
                  (let [result (compile-orc @csnd orc)]
                    (when-not (zero? result)
                      (binding [*out* *err*]
                        (println "csound error: failed to evaluate orchestra for"
                                 requested-client-name))))))}))

(comment
  (def qtest
    (spawn-csound-client
     {:requested-client-name "quicktest"
      :inputs 2 :outputs 2
      :config (:csound @config/config)
      :release-time 1
      :input-msg-cb (fn [])}))

  ((:compile qtest) "
instr 1
  aSig     poscil   0.8, 440
  aSig2     poscil   0.8, 665
  printk 1, times()
  outs      aSig, aSig2
endin
event_i(\"i\",1,0,100)
")

  (def port (first (:jack-ports-out qtest)))
  (into [] (jack/get-port-connections port ))


  (def qtest-start
    (:start qtest))

  (qtest-start)

  (def qtest-stop
    (:stop qtest))

  (qtest-stop)

  (def inst (:instance qtest))
  (perform-buffer @inst)
  )
