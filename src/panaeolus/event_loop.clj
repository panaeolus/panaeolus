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

#_(defn --replace-args-in-fx [old-fx new-fx]
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
    (go-loop [[queue mod-div queue-end] (event-queue-fn)
              instrument-instance instrument-instance
              args args
              fx-instances fx-instances
              needs-reroute? false
              index 0
              a-index 0]
      (if-let [next-timestamp (first queue)]
        (let [wait-chn (chan)
              ;; STEP 2, run the callback from step1 just before new cycle
              needs-reroute? (if (and (fn? needs-reroute?) (= 0 index))
                               (do (swap! globals/pattern-registry assoc-in [i-name :needs-reroute?] false)
                                   (needs-reroute?)
                                   false)
                               needs-reroute?)]
          (link/at next-timestamp
                   (fn []
                     (let [timestamp-after-next (if (< 1 (count queue))
                                                  (second queue)
                                                  queue-end)
                           args-processed (resolve-arg-indicies args index a-index next-timestamp timestamp-after-next)]
                       (when-not (empty? fx-instances)
                         (run! (fn [inst]
                                 (apply (:send inst)
                                        (resolve-arg-indicies (:args inst) index a-index next-timestamp timestamp-after-next)))
                               (remove :loop-self? fx-instances)))
                       (if (some sequential? args-processed)
                         (run! #(apply (:send instrument-instance) %)
                               (expand-nested-vectors-to-multiarg args-processed))
                         (apply (:send instrument-instance)
                                (resolve-arg-indicies args index a-index next-timestamp)))
                       (put! wait-chn true))))
          (<! wait-chn)
          (recur [(rest queue) mod-div queue-end]
                 instrument-instance
                 args
                 fx-instances
                 needs-reroute?
                 (inc index)
                 (inc a-index)))
        (when-let [event-form (get-current-state)]
          ;; STEP 1, provide old and new fx-instances to a closure
          (let [needs-reroute?
                (if (:needs-reroute? event-form)
                  (let [stage2 ((:needs-reroute? event-form) (:instrument-instance event-form)
                                fx-instances (:fx-instances event-form))]
                    stage2)
                  needs-reroute?)]
            (let [{:keys [event-queue-fn instrument-instance
                          args fx-instances]} event-form
                  [queue new-mod-div queue-end] (event-queue-fn mod-div)]
              (recur [queue new-mod-div queue-end]
                     instrument-instance
                     args
                     fx-instances
                     needs-reroute?
                     0
                     a-index
                     ))))))))
