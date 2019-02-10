(ns panaeolus.event-loop
  (:require [panaeolus.overtone.event-callback
             :refer [overtone-event-callback overtone-fx-callback synth-node?]]
            [panaeolus.jack2.jack-lib :as jack]
            [panaeolus.live-code-arguments
             :refer [resolve-arg-indicies expand-nested-vectors-to-multiarg]]
            [panaeolus.control :as control]
            [panaeolus.csound.csound-jna :as csound]
            [panaeolus.config :as config]
            [clojure.data :refer [diff]]
            [clojure.core.async :refer [<! >! timeout go go-loop chan put! poll!] :as async]
            [overtone.ableton-link :as link]
            panaeolus.metronome))

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


(defn event-loop-thread [get-current-state]
  (let [{:keys [event-queue-fn instrument-instance
                live-code-arguments current-fx
                envelope-type audio-backend]
         :as   env} (get-current-state)]
    (go-loop [[queue mod-div] (event-queue-fn)
              instrument-instance instrument-instance
              args live-code-arguments
              fx current-fx
              index 0
              a-index 0]
      (if-let [next-timestamp (first queue)]
        (let [wait-chn (chan)]
          (link/at next-timestamp (case audio-backend
                                    :overtone (let [{:keys [current-fx]} (get-current-state)]
                                                (overtone-event-callback
                                                 wait-chn instrument-instance
                                                 args index a-index next-timestamp
                                                 envelope-type current-fx))
                                    :csound   (fn []
                                                (let [{:keys [current-fx]} (get-current-state)
                                                      args-processed       (resolve-arg-indicies args index a-index next-timestamp)]

                                                  (when-not (empty? current-fx)
                                                    (run! (fn [[inst args]]
                                                            (prn "apply" inst (resolve-arg-indicies args index a-index next-timestamp))
                                                            (apply inst (resolve-arg-indicies args index a-index next-timestamp)))
                                                          (vals current-fx)))
                                                  (if (some sequential? args-processed)
                                                    (run! #(apply instrument-instance %)
                                                          (expand-nested-vectors-to-multiarg args-processed))
                                                    (apply
                                                     instrument-instance
                                                     (resolve-arg-indicies args index a-index next-timestamp)))
                                                  (put! wait-chn true)))))
          (<! wait-chn)
          (recur [(rest queue) mod-div]
                 instrument-instance
                 args
                 fx
                 (inc index)
                 (inc a-index)))
        (when-let [event-form (get-current-state)]
          (let [{:keys [event-queue-fn instrument-instance
                        live-code-arguments current-fx]}
                event-form
                [queue new-mod-div] (event-queue-fn mod-div)]
            (recur [queue new-mod-div]
                   instrument-instance
                   live-code-arguments
                   current-fx
                   0
                   a-index
                   )))))))

(defn --filter-fx [args]
  (loop [args         args
         fx-free-args []
         fx           []]
    (if (empty? args)
      [fx-free-args (if (sequential? fx) fx [fx])]
      ;; QUICK FIX, REPAIR!
      (if (and (= :fx (first args)) (< 1 (count args)))
        (recur (rest (rest args))
               fx-free-args
               (second args))
        (recur (rest args)
               (conj fx-free-args (first args))
               fx)))))

(defn --replace-args-in-fx [old-fx new-fx]
  (reduce (fn [init old-k]
            (if (contains? new-fx old-k)
              (assoc init old-k (assoc (get old-fx old-k) 1 (nth (get new-fx old-k) 1)))
              init))
          old-fx
          (keys old-fx)))

(defn event-loop [k-name instrument-instance args
                  & {:keys [envelope-type audio-backend csound-instance-name]}]
  (let [pat-exists?              (contains? @control/pattern-registry k-name)
        old-state                (get @control/pattern-registry k-name)
        beats                    (second args)
        beats                    (if (number? beats)
                                   [beats]
                                   (if (sequential? beats)
                                     beats
                                     (if (fn? beats)
                                       beats
                                       (throw (AssertionError. beats " must be vector, list or number.")))))
        [args fx-vector]         (--filter-fx args)
        ;; extra-atom               (atom {})
        fx-handle-atom           (if pat-exists?
                                   (get old-state :fx-handle-atom)
                                   (atom nil))
        new-fx                   (case audio-backend
                                   :overtone (reduce (fn [i v] (assoc i (first v) (vec (rest v)))) {} fx-vector)
                                   :csound
                                   (->> (mapv (fn [fx-closure]
                                                (fx-closure csound-instance-name fx-handle-atom))
                                              fx-vector)
                                        (reduce (fn [i v]
                                                  (assoc i (:fx-name v)
                                                         [(:instance v)
                                                          (or (:args v) [])])) {})))
        ;; _                        (prn "NEW FX" new-fx)
        old-fx                   (get old-state :current-fx)
        [rem-fx next-fx curr-fx] (diff (set (keys old-fx)) (set (keys new-fx)))
        ;; _                        (prn "rem-fx" rem-fx "next-fx" next-fx "curr-fx" curr-fx old-fx next-fx)
        new-fx-merged            (if (= :csound audio-backend)
                                   new-fx
                                   (if pat-exists? (--replace-args-in-fx (select-keys old-fx curr-fx)
                                                                         (select-keys new-fx curr-fx)) {}))
        fx-handle-callback       (case audio-backend
                                   :overtone (overtone-fx-callback k-name instrument-instance
                                                                   rem-fx next-fx curr-fx old-fx new-fx)
                                   :csound   (when (or (not (empty? rem-fx)) (not (empty? next-fx))
                                                       (not (empty? (:current-fx @fx-handle-atom))))
                                               (prn "FX CHANGE" "rem-fx" rem-fx "next-fx" next-fx "curr-fx" curr-fx old-fx next-fx)
                                               (fn []
                                                 ;; disconnect everything
                                                 (doseq [chn (range 2)]
                                                   (try
                                                     (let [old-fx (into [:self] (vec (keys old-fx)))]
                                                       (if old-fx
                                                         (run!
                                                          #(let [port      (nth old-fx %)
                                                                 from-port (if (= :self port)
                                                                             (str "-" csound-instance-name ":output" (inc chn))
                                                                             (str  port ":output" (inc chn)))
                                                                 to-port   (if (= % (dec (count old-fx)))
                                                                             (str (:jack-system-out @config/config) (inc chn))
                                                                             (str (nth old-fx (dec %)) ":input" (inc chn)))]
                                                             (jack/disconnect from-port to-port))
                                                          (reverse (range (count old-fx))))
                                                         (jack/disconnect (str "-" csound-instance-name ":output" (inc chn))
                                                                          (str (:jack-system-out @config/config) (inc chn)))))
                                                     (catch Exception e nil)))
                                                 ;; connect everything
                                                 (doseq [chn (range 2)]
                                                   (try (if-not (empty? next-fx)
                                                          ;;(vec (distinct (into (or next-fx []) curr-fx)))
                                                          (let [next-fx (vec (reverse (into [:self] next-fx)))]
                                                            (prn "NEXXT FX" next-fx (range (count next-fx)))
                                                            (run! #(let [port      (nth next-fx %)
                                                                         ;; _         (prn "PORT" port)
                                                                         from-port (if (= :self port)
                                                                                     (str "-" csound-instance-name ":output" (inc chn))
                                                                                     (str  port ":output" (inc chn))) ;; "-" csound-instance-name "-"
                                                                         ;; _         (prn "FROM PORT" from-port next-fx (dec %))
                                                                         to-port   (if (zero? %)
                                                                                     (str (:jack-system-out @config/config) (inc chn))
                                                                                     (str (nth next-fx (dec %)) ":input" (inc chn)))
                                                                         ;; _         (prn "TO PORT" to-port)
                                                                         ]
                                                                     ;; (prn "NOW" port)
                                                                     (jack/connect from-port to-port))
                                                                  (range (count next-fx))))
                                                          (jack/connect (str "-" csound-instance-name ":output" (inc chn))
                                                                        (str (:jack-system-out @config/config) (inc chn))))
                                                        (catch Exception e nil))))))
        get-cur-state-fn         (fn []
                                   (let [cur-state (get @control/pattern-registry k-name)]
                                     (when cur-state
                                       (when-let [fx-handle-cb @(get cur-state :fx-handle-atom)]
                                         (fx-handle-cb)
                                         (reset! (get cur-state :fx-handle-atom) nil))))
                                   (get @control/pattern-registry k-name))
        live-code-arguments      (rest (rest args))]
    (reset! fx-handle-atom fx-handle-callback)
    (swap! control/pattern-registry assoc k-name
           {:event-queue-fn      (fn [& [last-beat]]
                                   (beats-to-queue (or last-beat (link/get-beat)) beats))
            :instrument-instance (case audio-backend
                                   :overtone
                                   (if (and (= :inf envelope-type) (not pat-exists?))
                                     (apply instrument-instance
                                            (resolve-arg-indicies live-code-arguments 0 0 (link/get-beat)))
                                     (if (and pat-exists? (synth-node? (get old-state :instrument-instance)))
                                       (get old-state :instrument-instance)
                                       instrument-instance))
                                   :csound instrument-instance)
            :live-code-arguments live-code-arguments
            :current-fx          new-fx-merged
            :fx-handle-atom      fx-handle-atom
            :undoze-callback     (fn [] (event-loop get-cur-state-fn))
            :audio-backend       audio-backend
            :envelope-type       envelope-type})
    (when-not pat-exists?
      ;; (clear-fx inst)
      (event-loop-thread get-cur-state-fn))))

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

  (compile-orc (:instance test) "print 2")

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
