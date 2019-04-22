(ns panaeolus.event-loop
  (:require
   [panaeolus.jack2.jack-lib :as jack]
   [panaeolus.live-code-arguments
    :refer [resolve-arg-indicies expand-nested-vectors-to-multiarg]]
   [panaeolus.utils.utils :as utils]
   [panaeolus.csound.csound-jna :as csound]
   [panaeolus.config :as config]
   [clojure.data :refer [diff]]
   [clojure.core.async :refer [<! >! timeout go go-loop chan put! poll!] :as async]
   [overtone.ableton-link :as link]
   panaeolus.metronome
   ))

(defn fractional-abs [num]
  (if (pos? num) num (* -1 num)))

(defn calc-mod-div
  [durations]
  (let [meter       0
        bar-length  meter
        summed-durs (apply + (map fractional-abs durations))]
    (if (pos? meter)
      (* bar-length
         (inc (quot (dec summed-durs) bar-length)))
      summed-durs)))

(defn beats-to-queue
  [last-tick beats]
  (let [last-tick (Math/ceil last-tick)
        beats     (if (fn? beats) (beats {:last-tick last-tick}) beats)
        mod-div   (calc-mod-div beats)
        ;; CHANGEME, make configureable
        ]
    (loop [beats     (remove zero? beats)
           silence   0
           last-beat 0
           at        []]
      (if (empty? beats)
        [at (+ last-tick mod-div)]
        (let [fbeat (first beats)]
          (recur (rest beats)
                 (if (neg? fbeat)
                   (+ silence (fractional-abs fbeat))
                   0)
                 (if (neg? fbeat)
                   last-beat
                   fbeat)
                 (if (neg? fbeat)
                   at
                   (conj at (+ last-beat
                               silence
                               (if (empty? at)
                                 last-tick (last at)))))))))))

(defn --replace-args-in-fx [old-fx new-fx]
  (reduce (fn [init old-k]
            (if (contains? new-fx old-k)
              (assoc init old-k (assoc (get old-fx old-k) 1 (nth (get new-fx old-k) 1)))
              init))
          old-fx
          (keys old-fx)))

(defn csound-event-loop-thread [get-current-state]
  (let [{:keys [i-name
                event-queue-fn
                instrument-instance
                args
                fx-instances
                isFx?]} (get-current-state)]
    (go-loop [[queue mod-div] (event-queue-fn)
              instrument-instance instrument-instance
              args args
              fx-instances fx-instances
              needs-reroute? false
              index 0
              a-index 0]
      (if-let [next-timestamp (first queue)]
        (let [wait-chn (chan)]
          (link/at next-timestamp
                   (fn []
                     (let [args-processed (resolve-arg-indicies args index a-index next-timestamp)]
                       (when-not (empty? fx-instances)
                         (run! (fn [inst]
                                 (apply (:send inst)
                                        (resolve-arg-indicies (:args inst) index a-index next-timestamp)))
                               (remove :loop-self? fx-instances)))
                       (if (some sequential? args-processed)
                         (run! #(apply (:send instrument-instance) %)
                               (expand-nested-vectors-to-multiarg args-processed))
                         (apply (:send instrument-instance)
                                (resolve-arg-indicies args index a-index next-timestamp)))
                       (put! wait-chn true))))
          (<! wait-chn)
          (recur [(rest queue) mod-div]
                 instrument-instance
                 args
                 fx-instances
                 needs-reroute?
                 (inc index)
                 (inc a-index)))
        (when-let [event-form (get-current-state)]
          (when needs-reroute? (prn "REROUTE"))
          (let [{:keys [event-queue-fn instrument-instance
                        args fx-instances needs-reroute?]} event-form
                [queue new-mod-div] (event-queue-fn mod-div)]
            (recur [queue new-mod-div]
                   instrument-instance
                   args
                   fx-instances
                   needs-reroute?
                   0
                   a-index
                   )))))))



(comment

  (require 'panaeolus.metronome)

  (def params [{:amp {:default -12}}
               {:nn {:default 60}}])

  (event-loop "prufa" tezt '(prufa [1 1 0.5 0.5] :nn [36 38 40] :amp -20)
              :envelope-type :perc :audio-backend :csound)

  (def tezt (csound/spawn-csound-client "csound-2" 2 2 1))

  ;; ((:init test))

  @(:status tezt)

  ((:start tezt))

  ((:stop tezt))

  ((:kill tezt))

  (jack/connect "csound-2:output1" "system:playback_1")
  (jack/connect "csound-2:output2" "system:playback_2")

  (jack/disconnect "csound-3:output1" "system:playback_1")
  (jack/disconnect "csound-3:output2" "system:playback_2")

  (csound/compile-orc (:instance tezt) "print 2")

  (csound/compile-orc (:instance tezt) "
       instr 1
       asig = poscil:a(ampdb(p4), cpsmidinn(p5))
       outc asig, asig
       endin
")

  (panaeolus.overtone.macros/definst+ ding20 :perc
    [note 60 amp 1 gate 1]
    (let [freq (midicps note)
          snd  (sin-osc freq)
          env  (env-gen (lin 0.01 0.1 0.2 0.3) gate :action FREE)]
      (* amp env snd)))

  (ding20 :stop
          [1 1 1 1/3 1/3 1/3]
          :note
          [[59 61]   [63 65 66]]
          :amp
          0.9
          ;; :fx [(tubescreamer :gain 0.1)]
          )

  )
