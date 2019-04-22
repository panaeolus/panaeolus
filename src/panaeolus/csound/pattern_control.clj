(ns panaeolus.csound.pattern-control
  (:require [panaeolus.event-loop :refer [csound-event-loop-thread beats-to-queue]]
            [panaeolus.config :as config]
            [panaeolus.control :as control]
            [panaeolus.sequence-parser :refer [sequence-parser]]
            [panaeolus.csound.csound-jna :as csound-jna]
            [panaeolus.jack2.jack-lib :as jack]
            [panaeolus.utils.utils :as utils]
            [overtone.ableton-link :as link]
            [clojure.string :as string]
            [clojure.spec.alpha :as s]
            [clojure.core.async :as async]))


(defn fill-missing-keys
  "The keywords need to be squeezed in, along
   with calling `panaeolus.csound.utils/process-arguments`
   to resolve the arguments correctly."
  [args orig-arglists]
  (let [orig-arglists (if (some #(= :dur %) args)
                        orig-arglists (rest orig-arglists))]
    (letfn [(advance-to-arg [arg orig]
              (let [idx (.indexOf orig arg)]
                (if (neg? idx)
                  orig
                  (vec (subvec orig (inc idx))))))]
      (loop [args     args
             orig     orig-arglists
             out-args []]
        (if (or (empty? args)
                ;; ignore tangling keyword
                (and (= 1 (count args)) (keyword? (first args))))
          out-args
          (if (keyword? (first args))
            (recur (rest (rest args))
                   ;; (rest orig)
                   (advance-to-arg (first args) orig)
                   (conj out-args (first args) (second args)))
            (recur (rest args)
                   (vec (rest orig))
                   (conj out-args (first orig) (first args)))))))))

(defn squeeze-in-minilang-pattern [args orig-arglists]
  (let [{:keys [time nn]} (sequence-parser (second args))
        args              (vec args)]
    (doall
     (concat (list (first args))
             (list (vec time) (vec nn))
             (into [] (subvec args 2))))))

(defn csound-pattern-stop [k-name]
  (swap! control/pattern-registry dissoc k-name))

#_(defn csound-pattern-kill [k-name]
    (let [csound-instance ]
      (prn "k-name" k-name)
      (swap! control/pattern-registry dissoc k-name)))

#_(defn csound-pattern-kill [k-name]
    (letfn [(safe-node-kill [node]
              (go
                (<! (timeout 4999))
                (try
                  (sc-node/node-free* node)
                  (sc-node/kill node)
                  (catch Exception e nil))))]
      (do (let [v (get @pattern-registry k-name)]
            (when (= :inf (:envelope-type v))
              (safe-node-kill (:instrument-instance v)))
            (run! safe-node-kill (or (flatten (vals (:current-fx v))) [])))
          (get csound-instances )
          (swap! pattern-registry dissoc k-name))))


(defn csound-initialize-jack-graph
  "Connect new pattern jack ports, if jack nodes
   already exits, then ensure that the connections
   match up."
  [instrument-instance fx-instances]
  (let [linear-graph (cons instrument-instance fx-instances)]
    (loop [graph linear-graph]
      (if (empty? graph)
        nil
        (let [graph-node (first graph)
              next-node (second graph)]
          (when-let [outputs (:outputs graph-node)]
            (run! (fn [{:keys [port-name channel-index
                               connected-to-port
                               connected-to-instance] :as env}]
                    (prn [port-name channel-index
                          connected-to-port
                          connected-to-instance])
                    (let [current-connected-to-port (jack/get-connections port-name)]
                      (assert (= current-connected-to-port connected-to-port)
                              (str "State mismatch between JACK graph and Panaeolus\n"
                                   current-connected-to-port " != " (or connected-to-instance "nil")))
                      (when-not current-connected-to-port
                        (if next-node
                          (when (< channel-index (count (:inputs next-node)))
                            (let [next-node-inputs (:inputs next-node)
                                  next-port-name (:port-name (nth next-node-inputs channel-index))]
                              (jack/connect port-name next-port-name)
                              (swap! csound-jna/csound-instances update-in
                                     [(:client-name graph-node) :outputs channel-index]
                                     assoc
                                     :connected-to-port next-port-name
                                     :connected-to-instance (:client-name instrument-instance))
                              (swap! csound-jna/csound-instances update-in
                                     [(:client-name next-node) :inputs channel-index]
                                     assoc
                                     :connected-from-port next-port-name)))
                          (when (< channel-index (:nchnls @config/config))
                            (let [system-out-base (:jack-system-out @config/config)
                                  system-port-name (str system-out-base (inc channel-index))]
                              (jack/connect port-name system-port-name)
                              (swap! csound-jna/csound-instances update-in
                                     [(:client-name graph-node) :outputs channel-index]
                                     assoc
                                     :connected-to-port system-port-name
                                     :connected-to-instance (:client-name instrument-instance))))))))
                  outputs))
          (recur (rest graph)))))))


(defn csound-make-instance
  "Gets already spawned instance or creates new"
  [i-name csound-instrument-number orc-string num-outputs synth-form release-time-ms config isFx?]
  (if-let [instrument-instance (get @csound-jna/csound-instances i-name)]
    instrument-instance
    (let [{:keys [compile] :as new-instance} (csound-jna/spawn-csound-client
                                              i-name (if isFx? num-outputs 0) num-outputs
                                              (or (:ksmps config) (:ksmps @config/config))
                                              synth-form csound-instrument-number
                                              release-time-ms isFx?)]
      (compile orc-string)
      (swap! csound-jna/csound-instances assoc i-name new-instance)
      new-instance)))

(def valid-live-code-action?
  #(contains? #{:loop :stop :solo :kill} %))

(def valid-live-code-pattern?
  (s/or :single-note-loop number?
        :minilang string?
        :sequence (fn [second-argument]
                    (and (sequential? second-argument)
                         (not (empty? second-argument))))))

(def valid-parameters?
  (s/cat :parameter keyword?
         :value (fn [parameter-value] (not (nil? parameter-value)))))

(s/def ::live-code-arguments
  (s/cat :required-args (s/cat :valid-live-code-action valid-live-code-action?
                               :valid-live-code-pattern valid-live-code-pattern?)
         :opt-args (s/* valid-parameters?)))

;; (s/explain ::live-code-arguments [:loop "0xfff" :dur 2 :nn 50 :amp -10])

(defn csound-register-pattern
  "Register a new pattern or upadte an existing one."
  [instrument-instance i-name pat-exists? args fx-instances needs-reroute? isFx?]
  (let [beats (utils/extract-beats args)]
    (swap! control/pattern-registry assoc i-name
           {:i-name               i-name
            :event-queue-fn       (fn [& [last-beat]]
                                    (beats-to-queue (or last-beat (link/get-beat)) beats))
            :instrument-instance  instrument-instance
            :args                 (rest (rest args))
            :fx-instances         fx-instances
            :needs-reroute?       needs-reroute?
            :isFx?                isFx?})
    (when-not pat-exists?
      (csound-event-loop-thread (fn [] (get @control/pattern-registry i-name))))))

(defn calculate-debounce-time
  "calculate the debounce time for
   all the threads in a chain based
   on the maximum release-time value"
  [instrument-instance fx-instances]
  (apply max
         (conj (map :release-time fx-instances)
               (:release-time instrument-instance))))

(defn csound-pattern-control
  [i-name csound-instrument-number orc-string
   synth-form num-outputs release-time-secs config isFx?]
  (fn [& args]
    {:pre [(s/valid? ::live-code-arguments args)]}
    (let [argv-positions (mapv :name synth-form)
          args (-> (if (string? (second args))
                     (squeeze-in-minilang-pattern args argv-positions)
                     args)
                   (fill-missing-keys argv-positions))
          pat-ctl (first args)
          [args fx-args]   (utils/filter-fx args)
          fx-instances     (mapv #(% i-name) fx-args)
          release-time-ms   (* 1000 release-time-secs)

          instrument-instance (csound-make-instance
                               i-name csound-instrument-number
                               orc-string num-outputs synth-form release-time-ms config isFx?)
          debounce-time       (calculate-debounce-time instrument-instance fx-instances)
          _                (async/>!! (:debounce-channel instrument-instance) debounce-time)
          current-state    (get @control/pattern-registry i-name)
          pat-exists?      (some? current-state)
          needs-reroute?   false ;; CHANGEME
          ]
      (when-not pat-exists?
        (csound-initialize-jack-graph instrument-instance fx-args))
      (case pat-ctl
        :loop (do
                (csound-register-pattern
                 (get @csound-jna/csound-instances i-name)
                 i-name
                 pat-exists?
                 args
                 fx-instances
                 needs-reroute?
                 isFx?))
        ;; :stop (csound-pattern-stop i-name)
        :solo nil #_(do (control/solo! (str i-name "-" 0))
                        (event-loop (str i-name "-" pat-num)
                                    instrument-instance
                                    args
                                    :envelope-type :perc
                                    :audio-backend :csound
                                    :csound-instance-name i-name))
        ;; :solo (control/solo (str i-name "-" 0) (if (empty? pat-num) 0 (read-string pat-num)))
        ;; :kill (csound-pattern-stop i-name)
        ;; (apply instrument-instance (rest (rest args)))
        )
      pat-ctl)))

(defn csound-fx-control-data
  [host-pattern-name fx-name fx-controller-instr-number
   orc-string fx-form num-outputs release-time-secs config loop-self?]
  (fn [& args]
    (let [fx-instance (csound-make-instance
                       fx-name fx-controller-instr-number
                       orc-string num-outputs fx-form
                       (* 1000 release-time-secs) config true)]
      (merge fx-instance
             {:fx-name  fx-name
              :args     args
              :kill-fx  (fn [] (prn "IMPLEMENT KILL FX"))
              :fx-form  fx-form
              :loop-self? loop-self?}))))


(comment
  ((csound-pattern-control
    "BAAP11"
    1
    "instr 1
   asig = poscil:a(ampdb(p5), cpsmidinn(p4))
   aenv linseg 0, 0.02, 1, p3 - 0.05, 1, 0.02, 0, 0.01, 0
   asig *= aenv
   outc asig, asig
   endin"
    [{:name :dur :default 1}
     {:name :nn :default 60}
     {:name :amp :default -12}]
    2 10 {} false)
   :loop "0xfff" :dur 2 :nn 50 :amp -10  :fx (panaeolus.csound.examples.fx/binauralize11))
  )
