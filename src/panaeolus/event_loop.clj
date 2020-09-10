(ns panaeolus.event-loop
  (:require
   [panaeolus.jack2.jack-lib :as jack]
   [panaeolus.live-code-arguments
    :refer [resolve-arg-indicies expand-nested-vectors-to-multiarg]]
   [panaeolus.utils.utils :as utils]
   [panaeolus.csound.csound-jna :as csound]
   [panaeolus.config :as config]
   [panaeolus.globals :as globals]
   [clojure.data :refer [diff]]
   [clojure.core.async :refer [<! >! timeout go go-loop chan put! poll!] :as async]
   [overtone.ableton-link :as link]
   panaeolus.metronome
   ))

;; (set! *warn-on-reflection* true)

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
        queue-end (+ last-tick mod-div) ;; to calculate the last absolute dur
        ]
    (loop [beats     (remove zero? beats)
           silence   0
           last-beat 0
           at        []]
      (if (empty? beats)
        [at (+ last-tick mod-div) queue-end]
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

(defn get-next-callback-on
  [wait-chn queue queue-end index a-index local-state]
  (fn []
    (let [next-timestamp (first queue)
          timestamp-after-next (if (< 1 (count queue)) (second queue) queue-end)
          fx-instances (:fx-instances @local-state)
          send-event (get-in @local-state [:instrument-instance :send])
          args-processed (resolve-arg-indicies
                          {:args (:args @local-state)
                           :index index
                           :a-index a-index
                           :next-timestamp next-timestamp
                           :timestamp-after-next timestamp-after-next})]
      (when-not (empty? fx-instances)
        (run! (fn [inst]
                (apply (:send inst)
                       (resolve-arg-indicies
                        {:args (:args inst)
                         :index index
                         :a-index a-index
                         :next-timestamp next-timestamp
                         :timestamp-after-next timestamp-after-next})))
              (filter #(and (not (empty? (:args %)))
                            (get % :send)) fx-instances)))
      (if (some sequential? args-processed)
        (run! #(apply send-event %)
              (expand-nested-vectors-to-multiarg args-processed))
        (apply send-event args-processed))
      (put! wait-chn true))))

(defmulti event-loop
  (fn [get-current-state]
    (get-in (get-current-state) [:beats :operator])))

(defmethod event-loop :on
  [get-current-state]
  (let [local-state (atom (get-current-state))]
    (go-loop [[queue mod-div queue-end]
              (beats-to-queue
               (link/get-beat)
               (get-in @local-state [:beats :on]))
              index 0
              a-index 0]
      (if-let [next-timestamp (first queue)]
        (let [wait-chn (chan 1)]
          (link/at next-timestamp
                   (get-next-callback-on
                    wait-chn
                    queue
                    queue-end
                    index
                    a-index
                    local-state))
          (<! wait-chn)
          (recur [(rest queue) mod-div queue-end]
                 (inc index)
                 (inc a-index)))
        (when-let [next-state (get-current-state)]
          (if-not (= (get-in next-state [:beats :operator]) :on)
            (event-loop get-current-state)
            (do
              (reset! local-state next-state)
              (recur (beats-to-queue queue-end (get-in next-state [:beats :on]))
                     0
                     a-index))))))))

(defn get-next-callback-every
  [wait-chn index a-index every next-timestamp local-state]
  (fn []
    (let [fx-instances (:fx-instances @local-state)
          send-event (get-in @local-state [:instrument-instance :send])
          args-processed (resolve-arg-indicies
                          {:args (:args @local-state)
                           :index index
                           :a-index a-index
                           :next-timestamp next-timestamp
                           :every every})]
      (when-not (empty? fx-instances)
        (run! (fn [inst]
                (apply (:send inst)
                       (resolve-arg-indicies
                        {:args (:args inst)
                         :index index
                         :a-index a-index
                         :next-timestamp next-timestamp
                         :every every})))
              (filter #(and (not (empty? (:args %)))
                            (get % :send)) fx-instances)))
      (if (some sequential? args-processed)
        (run! #(apply send-event %)
              (expand-nested-vectors-to-multiarg args-processed))
        (apply send-event args-processed))
      (put! wait-chn true))))

(defmethod event-loop :every
  [get-current-state]
  (let [local-state (atom (get-current-state))
        initial-every (get-in @local-state [:beats :every])
        beat-at-start (link/get-beat)
        as-if-last-timestamp (* (quot beat-at-start initial-every)
                                initial-every)]
    (go-loop [every initial-every
              last-timestamp as-if-last-timestamp
              index 0
              a-index 0]
      (let [next-timestamp (+ last-timestamp every)
            wait-chn (chan 1)]
        (link/at next-timestamp
                 (get-next-callback-every
                  wait-chn
                  index
                  a-index
                  every
                  next-timestamp
                  local-state))
        (<! wait-chn)
        (when-let [next-state (get-current-state)]
          (if-not (= (get-in next-state [:beats :operator]) :every)
            (event-loop get-current-state)
            (do (reset! local-state next-state)
                (let [next-every (get-in next-state [:beats :every])]
                  (recur next-every
                         (if (not= every next-every)
                           (* (quot (link/get-beat) next-every)
                              next-every)
                           next-timestamp)
                         (inc index)
                         (inc a-index))))))))))


(comment
  (beats-to-queue 609.0 [0.25 0.25 0.25 0.25])
  (beats-to-queue 1836.25 [0.25 0.25 0.25 0.25] )
  )
