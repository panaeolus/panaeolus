(ns panaeolus.csound.csound-jna
  (:require [clj-native.direct :refer [defclib loadlib]]
            [clj-native.structs :refer [byref]]
            [clj-native.callbacks :refer [callback]]
            panaeolus.utils.jna-path
            [panaeolus.jack2.jack-lib :as jack]
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


(defn jack-processor [csnd nchnls ksmps shutdown?]
  (fn [^AudioClient this ^List inputs ^List outputs ^Long nframes]
    (when (zero? (perform-ksmps csnd))
      (loop [csnd-phase 0
             jack-phase 0]
        (when  (< jack-phase nframes)
          (if (< csnd-phase ksmps)
            (do
              (doseq [chanl (range (.size inputs))]
                (.put ^FloatBuffer (.get inputs chanl) jack-phase
                      (.get ^DoubleBuffer (.getSpin ^Csound csnd)
                            (+ csnd-phase chanl))))
              (doseq [chanl (range (.size outputs))]
                (.put ^FloatBuffer (.get outputs chanl) jack-phase
                      (.get ^DoubleBuffer (.getSpout ^Csound csnd)
                            (+ csnd-phase chanl))))
              (recur (+ nchnls csnd-phase)
                     (inc jack-phase)))
            (when (zero? (perform-ksmps csnd))
              (recur 0 jack-phase)))))
      (if @shutdown?
        (.shutdown this)
        true))))

(defn spawn-csound-client [client-name inputs outputs ksmps]
  (let [csnd (csound-create)]
    (run! #(set-option csnd %)
          ["-iadc:null"
           "-odac:null"
           (str "--nchnls=" inputs)
           (str "--nchnls_i=" outputs)
           "--0dbfs=1"
           "-+rtaudio=jack"
           "--sample-rate=48000"
           (str "--ksmps=" ksmps)
           (str "-+jack_client=" client-name)]
          (start csnd)
          csnd)))

(comment 

  (def csnd (csound-create))

  (set-message-callback csnd (fn [attr msg] (println msg)))
  (set-option csnd "-odac")
  (set-option csnd "--0dbfs=1")
  (set-option csnd "--ksmps=1")
  (set-option csnd "--sample-rate=48000")
  (set-option csnd "--nchnls=2")
  (set-option csnd "-+rtaudio=null")
  ;; (set-option csnd "-b 2048")
  ;; (set-option csnd "-B 4096")
  (start csnd)
  (cleanup csnd)
  (compile-orc csnd "print 4")

  (def nchnls (.getNchnls csnd))
  (def shutdown? (volatile! false))
  (def ksmps (.getKsmps csnd))
  (def zerodbfs (.get0dBFS csnd))
  
  
  
  (vreset! shutdown? true)
  
  (compile-orc csnd "instr 1\n asig1 poscil 0.4, 1201 \n asig2 poscil 0.4, 1200 \n outs asig1, asig2 \nendin\n")
  (compile-orc-async csnd "schedule(1, 0, 3)" )

  (.get (.getSpout csnd) 0)
  (.get (.getSpin csnd) 0)
  
  (def jack-client (jack/new-jack-client "prufa2" 0 2 processor))

  (compile-orc csnd "print 4")
  
  (prn "aa")
  (.stop jack-client)

  
  (defn processor [^long time ^List inputs ^List outputs nframes]
    (prn "ONCE?")
    (do (prn time inputs outputs nframes)
        (perform-ksmps csnd)
        (rand-nth [true true true true false])))
  
  (def jack-client (jack/new-jack-client "prufa1" 0 2 processor))
  

  
  (def test-client (jack/client-create "prufa"))
  (def input1 (jack/client-register-port test-client "input1" :audio :input))
  (def input2 (jack/client-register-port test-client "input2" :audio :input))
  (def output1 (jack/client-register-port test-client "output1" :audio :output))
  (def output2 (jack/client-register-port test-client "output2" :audio :output))
  (.jack_activate jack/jackLib test-client)
  (.jack_client_close jack/jackLib test-client )
  (.jack_get_client_name jack/jackLib test-client)

  
  (use 'clojure.reflect)

  (defn all-methods [x]
    (->> x reflect 
         :members 
         (filter :return-type)  
         (map :name) 
         sort 
         (map #(str "." %) )
         distinct
         println)))
#_(.toString csnd)
#_(all-methods csnd)


#_(defclib Csound
    (:libname "libcsound64")
    (:callbacks (invoke [void* void* void*] void))
    (:functions
     (csound-create csoundCreate [] void*)
     (csound-start csoundStart [void*] void)
     (csound-stop csoundStop [void*] void)
     (csound-get-version csoundGetVersion [void*] int)
     (csound-set-option csoundSetOption [void* constchar*] int)
     (csound-compile-orc csoundCompileOrc [void* constchar*] int)
     (csound-compile-orc-async csoundCompileOrcAsync [void* constchar*] int)
     (csound-compile-csd-text csoundCompileCsdText [void* constchar*] int)
     (csound-perform-ksmps csoundPerformKsmps [void*] int)
     (csound-cleanup csoundCleanup [void*] int)
     (csound-reset csoundReset [void*] void)
     (csound-set-message-callback csoundSetMessageCallback [void* invoke] void)))

#_(loadlib Csound)

(comment 
  (def csnd (csound-create))
  (set-option csnd "-odac")
  (csound-set-message-callback csnd (callback invoke (fn [arg1 arg2 arg3]
                                                       (prn (.getString arg3 0)))))
  (csound-start csnd)
  (csound-compile-orc csnd  "print 222"))

