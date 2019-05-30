(ns panaeolus.csound.pattern-control
  (:require [panaeolus.event-loop :refer [csound-event-loop-thread beats-to-queue]]
            [panaeolus.config :as config]
            [panaeolus.globals :as globals]
            [panaeolus.sequence-parser :as sequence-parser]
            [panaeolus.csound.csound-jna :as csound-jna]
            [panaeolus.jack2.jack-lib :as jack]
            [panaeolus.utils.utils :as utils]
            [overtone.ableton-link :as link]
            [clojure.data :refer [diff]]
            [clojure.string :as string]
            [clojure.spec.alpha :as s]
            [clojure.core.async :as async]))

(defn csound-pattern-stop [k-name]
  (do (swap! globals/pattern-registry dissoc k-name)
      :stop))

(defn csound-pattern-solo [k-name]
  (let [pattern (select-keys @globals/pattern-registry [k-name])]
    (when-not (empty? pattern)
      (reset! globals/pattern-registry pattern)))
  :solo)

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
          (swap! csound-jna/csound-instances assoc-in [(:client-name graph-node ) :scheduled-to-kill?] false)
          (when-let [outputs (:outputs graph-node)]
            (run! (fn [{:keys [port-name channel-index
                               connected-to-ports
                               connected-to-instances] :as env}]
                    (let [current-connected-to-ports (jack/get-connections port-name)]
                      (assert (= current-connected-to-ports (vec (sort (or connected-to-ports []))))
                              (str "State mismatch between JACK graph and Panaeolus\n"
                                   current-connected-to-ports " != " (or connected-to-instances "nil")))
                      (when (empty? current-connected-to-ports)
                        (if next-node
                          (when (< channel-index (count (:inputs next-node)))
                            (let [next-node-inputs (:inputs next-node)
                                  next-port-name (:port-name (nth next-node-inputs channel-index))]
                              (async/go-loop [retry 0]
                                (let [query-result (jack/query-connection (:i-name graph-node))]
                                  (if (or (not (:scheduled-to-kill? graph-node))
                                          (and (some #(= port-name %) query-result)
                                               (some #(= next-port-name %) query-result)))
                                    (jack/connect port-name next-port-name)
                                    (do (async/<! (async/timeout 25))
                                        (if (>= retry 10)
                                          (throw (Exception. (str "Error in starting csound instrument, "
                                                                  (:i-name graph-node) " did not start.")))
                                          (recur (inc retry)))))))
                              (swap! csound-jna/csound-instances update-in
                                     [(:client-name graph-node) :outputs channel-index]
                                     #(-> %
                                          (update :connected-to-ports conj next-port-name)
                                          (update :connected-to-instances conj (:client-name next-node))))
                              (swap! csound-jna/csound-instances update-in
                                     [(:client-name next-node) :inputs channel-index]
                                     #(-> %
                                          (update :connected-from-ports conj next-port-name)
                                          (update :connected-from-instances conj (:client-name graph-node))))))
                          (when (< channel-index (:nchnls @config/config))
                            (let [system-out-base (:jack-system-out @config/config)
                                  system-port-name (str system-out-base (inc channel-index))]
                              (async/go-loop [retry 0]
                                (let [query-result (jack/query-connection (:i-name graph-node))]
                                  (if (some #(= port-name %) query-result)
                                    (jack/connect port-name system-port-name)
                                    (if (>= retry 10)
                                      (throw (Exception. (str "Error in starting csound instrument, "
                                                              (:i-name graph-node) " did not start.")))
                                      (and (async/<! (async/timeout 25))
                                           (recur (inc retry)))))))
                              (swap! csound-jna/csound-instances update-in
                                     [(:client-name graph-node) :outputs channel-index]
                                     #(-> %
                                          (update :connected-to-ports conj system-port-name)
                                          (update :connected-to-instances conj "system")))))))))
                  outputs))
          (recur (rest graph)))))))

(defn csound-make-instance
  "Gets already spawned instance or creates new"
  [i-name csound-instrument-number orc-string num-outputs synth-form
   release-time-ms init-hook config isFx?]
  (if-let [instrument-instance (get @csound-jna/csound-instances i-name)]
    instrument-instance
    (let [input-msg-cb (csound-jna/input-message-closure
                        synth-form csound-instrument-number release-time-ms isFx?)
          new-instance (csound-jna/spawn-csound-client
                        i-name (if isFx? num-outputs 0) num-outputs
                        config release-time-ms isFx? input-msg-cb)]
      ((:start new-instance))
      (async/go
        (async/<! (async/timeout 5))
        ((:compile new-instance) (str orc-string "\n" init-hook)))
      (swap! csound-jna/csound-instances assoc i-name new-instance)
      new-instance)))

(s/def ::valid-live-code-modifier?
  (s/and keyword? #(contains? #{:stop :solo :kill} %)))

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
  (s/alt :modifier    (s/cat :modifier ::valid-live-code-modifier? :rest (s/* any?))
         :initializer (s/cat
                       :valid-live-code-action valid-live-code-action?
                       :valid-live-code-pattern valid-live-code-pattern?
                       :opt-args (s/* valid-parameters?))))

;; NOTE, you can also get the old-fx-instances
;; from the csound-instances atom at this point.
(defn csound-reroute-jack
  [instrument-instance old-fx-instances new-fx-instances]
  (let [new-fx-instances-names (mapv :client-name new-fx-instances)
        old-fx-instances-names (mapv :client-name old-fx-instances)
        [kill-chain _ survivor-chain]
        (diff old-fx-instances-names
              new-fx-instances-names)
        kill-chain (remove nil? (or kill-chain []))
        kill-chain (if (empty? kill-chain)
                     []
                     (->> old-fx-instances
                          (split-at (.indexOf ^clojure.lang.PersistentVector old-fx-instances (first kill-chain)))
                          second vec))
        survivor-chain (or survivor-chain [])
        linear-survivor-chain (vec (take-while #(not (nil? %)) survivor-chain))
        last-survivor (last linear-survivor-chain)
        linear-new-fx (if-not last-survivor
                        new-fx-instances-names
                        (-> (split-at (.indexOf ^clojure.lang.PersistentVector new-fx-instances-names last-survivor) new-fx-instances-names)
                            second vec))]
    (fn []
      (doseq [killable-node kill-chain]
        ;; Take rare case into account where fx with same index
        ;; exists within a kill chain, example
        ;; (diff [:src :fx1 :fx2 :fx3 :fx4 :fx5] [:src :fx1 :fx2 :fx6 :fx4 :fx5])
        ;; where :fx5 needs to re-routing but at the same time survive
        (let [killable-node-survives? (some #(= % killable-node) survivor-chain)]
          (when killable-node-survives?
            (doseq [output-port (:outputs killable-node)]
              (apply jack/disconnect (:port-name output-port) (:connected-to-ports output-port)))
            (doseq [input-port (:inputs killable-node)]
              (run! #(jack/disconnect % (:port-name input-port))  (:connected-from-ports input-port))))
          (doseq [output-port (:outputs killable-node)]
            (swap! csound-jna/csound-instances update-in
                   [(:client-name killable-node) :outputs (:channel-index output-port)]
                   assoc :connected-to-ports [] :connected-to-instances []))
          (doseq [input-port (:inputs killable-node)]
            (swap! csound-jna/csound-instances update-in
                   [(:client-name killable-node) :inputs (:channel-index input-port)]
                   assoc :connected-from-ports [] :connected-from-instances []))
          (when (not killable-node-survives?)
            (swap! csound-jna/csound-instances assoc-in [(:client-name killable-node) :scheduled-to-kill?] true)
            (async/go (async/<! (async/timeout (:release-time instrument-instance)))
                      (let [killable-node-post-release
                            (get @csound-jna/csound-instances (:client-name killable-node))]
                        (when killable-node-post-release
                          (if (:scheduled-to-kill? killable-node-post-release)
                            (do ((:stop killable-node-post-release))
                                (swap! csound-jna/csound-instances dissoc (:client-name killable-node))))
                          ;; Something has connected itself to this node
                          ;; while it was in release (very rare case!)
                          ;; only disconnect non-current connections
                          (let [;;current-connections (set (jack/query-connection killable-node-name))
                                current-inputs (set (mapv :port-name (:inputs killable-node-post-release)))
                                previous-inputs (set (mapv :port-name (:inputs killable-node)))
                                current-outputs (set (mapv :port-name (:outputs killable-node-post-release)))
                                previous-outputs (set (mapv :port-name (:outputs killable-node)))]
                            (doseq [output-port (:outputs killable-node-post-release)]
                              (when (and (contains? previous-outputs (:port-name output-port))
                                         (not (contains? current-outputs (:port-name output-port))))
                                (apply jack/disconnect output-port (or (jack/get-connections (:port-name output-port)) []))))
                            (doseq [input-port (:inputs killable-node-post-release)]
                              (when (and (contains? previous-inputs (:port-name input-port))
                                         (not (contains? current-inputs (:port-name input-port))))
                                (run! #(jack/disconnect % input-port) (or (jack/get-connections (:port-name input-port)) [])))))))))))
      (when (or (empty? linear-new-fx)
                (not= (first old-fx-instances-names)
                      (first new-fx-instances-names)))
        ;; this means root needs re-routing
        (if (empty? new-fx-instances) ;; connect to system?
          (let [current-outputs (:outputs instrument-instance)]
            (doseq [output current-outputs]
              (let [connected-to-port (first (:connected-to-ports output))
                    system-out-base (:jack-system-out @config/config)
                    system-port-name (str system-out-base (inc (:channel-index output)))]
                (when connected-to-port
                  (jack/disconnect (:port-name output) connected-to-port))
                (when (< (:channel-index output) (:nchnls @config/config))
                  (jack/connect (:port-name output) system-port-name))
                (swap! csound-jna/csound-instances update-in
                       [(:client-name instrument-instance)
                        :outputs
                        (:channel-index output)]
                       assoc
                       :connected-to-ports (if (< (:channel-index output) (:nchnls @config/config))
                                             [system-port-name] [])
                       :connected-to-instances (if (< (:channel-index output) (:nchnls @config/config))
                                                 ["system"] [])))))
          ;; connect to first node
          (let [current-outputs (:outputs instrument-instance)
                next-node (first new-fx-instances)]
            (doseq [output current-outputs]
              (let [connected-to-port (first (:connected-to-ports output))
                    next-port-name (:port-name (nth (:inputs next-node) (:channel-index output)))]
                (when connected-to-port
                  (jack/disconnect (:port-name output) connected-to-port))
                (when next-port-name
                  (jack/connect (:port-name output) next-port-name)
                  (swap! csound-jna/csound-instances update-in
                         [(:client-name next-node) :inputs (:channel-index output)]
                         #(-> %
                              (update :connected-from-ports conj (:port-name output))
                              (update :connected-from-instances conj (:client-name instrument-instance)))))
                (swap! csound-jna/csound-instances update-in
                       [(:client-name instrument-instance) :outputs (:channel-index output)]
                       assoc
                       :connected-to-ports (if next-port-name [next-port-name] [])
                       :connected-to-instances (if next-port-name [(:client-name next-node)] [])))))))
      (loop [graph-node-names linear-new-fx]
        (if (empty? graph-node-names)
          nil
          (let [graph-node (get @csound-jna/csound-instances (first graph-node-names))
                next-node (and (second graph-node-names) (get @csound-jna/csound-instances (second graph-node-names)))]
            (swap! csound-jna/csound-instances assoc-in [(first graph-node-names) :scheduled-to-kill?] false)
            (when-let [outputs (:outputs graph-node)]
              (run! (fn [{:keys [port-name channel-index
                                 connected-to-ports
                                 connected-to-instances] :as env}]
                      (let [current-connected-to-ports (jack/get-connections port-name)]
                        ;; Debugging
                        (when-not (= current-connected-to-ports (vec (sort connected-to-ports)))
                          (println
                           (str "WARNING: " "Possible state mismatch between JACK graph and Panaeolus\n"
                                current-connected-to-ports " != " (or connected-to-instances "nil"))))
                        (if next-node
                          (when (< channel-index (count (:inputs next-node)))
                            (let [next-node-inputs (:inputs next-node)
                                  next-port-name (:port-name (nth next-node-inputs channel-index))]
                              (async/go-loop [retry 0]
                                (let [query-result (jack/query-connection (:client-name graph-node))]
                                  (if (and (some #(= port-name %) query-result)
                                           (some #(= next-port-name %) query-result))
                                    (jack/connect port-name next-port-name)
                                    (do (async/<! (async/timeout 25))
                                        (if (>= retry 10)
                                          (jack/connect port-name next-port-name)
                                          #_(throw (Exception. (str "Error in starting csound instrument, "
                                                                    (:client-name graph-node) " did not start.")))
                                          (recur (inc retry)))))))
                              (swap! csound-jna/csound-instances update-in
                                     [(:client-name graph-node) :outputs channel-index]
                                     #(-> %
                                          (update :connected-to-ports conj next-port-name)
                                          (update :connected-to-instances conj (:client-name next-node))))
                              (swap! csound-jna/csound-instances update-in
                                     [(:client-name next-node) :inputs channel-index]
                                     #(-> %
                                          (update :connected-from-ports conj next-port-name)
                                          (update :connected-from-instances conj (:client-name graph-node))))))
                          (when (< channel-index (:nchnls @config/config))
                            (let [system-out-base (:jack-system-out @config/config)
                                  system-port-name (str system-out-base (inc channel-index))]
                              (async/go-loop [retry 0]
                                (let [query-result (jack/query-connection (:client-name graph-node))]
                                  (if (some #(= port-name %) query-result)
                                    (jack/connect port-name system-port-name)
                                    (if (>= retry 10)
                                      (throw (Exception. (str "Error in starting csound instrument, "
                                                              (:client-name graph-node) " did not start.")))
                                      (and (async/<! (async/timeout 25))
                                           (recur (inc retry)))))))
                              (swap! csound-jna/csound-instances update-in
                                     [(:client-name graph-node) :outputs channel-index]
                                     #(-> %
                                          (update :connected-to-ports conj system-port-name)
                                          (update :connected-to-instances conj "system"))))))))
                    outputs))
            (recur (rest graph-node-names))))))))

;; (update-in {:a {:b [nil {:c [] :d []}]}} [:a :b 1] #( -> % (update :c conj 1) (update :d conj 2)))

(defn csound-register-pattern
  "Register a new pattern or upadte an existing one."
  [instrument-instance i-name pat-exists? args fx-instances needs-reroute? isFx?]
  (let [beats (utils/extract-beats args)]
    (swap! globals/pattern-registry assoc i-name
           {:i-name               i-name
            :event-queue-fn       (fn [& [last-beat]]
                                    (beats-to-queue (or last-beat (link/get-beat)) beats))
            :instrument-instance  instrument-instance
            :args                 (rest (rest args))
            :fx-instances         (if isFx? [] fx-instances)
            :needs-reroute?       (if isFx? false
                                      (if needs-reroute?
                                        csound-reroute-jack false))
            :isFx?                isFx?})
    (when-not pat-exists?
      (csound-event-loop-thread
       (fn [] (get @globals/pattern-registry i-name))))))

(defn calculate-debounce-time
  "calculate the debounce time for
   all the threads in a chain based
   on the maximum release-time value"
  [instrument-release-time fx-instances]
  (apply max
         (conj (map :release-time fx-instances)
               instrument-release-time)))

(defn csound-kill-jack-chain [root-name]
  (loop [cur-nodes [(get @csound-jna/csound-instances root-name)]]
    (when-let [cur-node (first cur-nodes)]
      ((:stop cur-node))
      ;; in case these are self-looping fx's, we dissoc pattern-reg too
      (swap! globals/pattern-registry dissoc (:client-name cur-node))
      (swap! csound-jna/csound-instances dissoc (:client-name cur-node))
      (let [next-instances (-> cur-node :outputs first :connected-to-instances)
            next-nodes (mapv #(get @csound-jna/csound-instances %) next-instances)
            cur-nodes (into (rest cur-nodes) next-nodes)]
        (when-not (empty? cur-nodes)
          (recur cur-nodes)))))
  ;; kill zombies
  (run! (fn [[key val]]
          (when (clojure.string/includes?
                 (str key) root-name)
            (and (fn? (:stop val)) ((:stop val)))
            (swap! globals/pattern-registry dissoc key)
            (swap! csound-jna/csound-instances dissoc key))) @csound-jna/csound-instances))

;; TODO: destructure params
(defn csound-pattern-control
  [i-name csound-instrument-number orc-string
   instr-form num-outputs
   release-time-secs init-hook release-hook
   config isFx?]
  (fn [& args]
    {:pre [(s/valid? ::live-code-arguments args)]}
    (if (= :stop (first args))
      (csound-pattern-stop i-name)
      (let [current-state    (get @globals/pattern-registry i-name)
            pat-exists?      (some? current-state)
            argv-positions (mapv :name instr-form)
            args (-> (if (string? (second args))
                       (sequence-parser/process-parseable-pattern args argv-positions)
                       args)
                     (utils/fill-missing-keys argv-positions))
            pat-ctl (first args)
            [args fx-args]   (utils/seperate-fx-args args)
            fx-instances     (when-not isFx?
                               (vec
                                (map-indexed
                                 (fn [idx fx-cb] (fx-cb i-name idx)) fx-args)))
            release-time-ms   (* 1000 release-time-secs)
            debounce-time       (calculate-debounce-time release-time-ms fx-instances)
            instrument-instance (csound-make-instance
                                 i-name csound-instrument-number
                                 orc-string num-outputs instr-form debounce-time
                                 init-hook config isFx?)

            needs-reroute?   (cond
                               (not pat-exists?) false
                               (= (mapv #(:client-name %) (:fx-instances current-state))
                                  (mapv #(:client-name %) fx-instances))
                               false
                               :else true)
            ]
        ;; (csound-initialize-jack-graph instrument-instance fx-instances)
        (when (and (not pat-exists?)
                   (not isFx?))
          (async/go
            (async/<! (async/timeout 10))
            (csound-initialize-jack-graph instrument-instance fx-instances)
            (async/<! (:release-channel instrument-instance))
            (println "Killing " i-name)
            (csound-kill-jack-chain i-name)))
        (case pat-ctl
          :loop (csound-register-pattern
                 (get @csound-jna/csound-instances i-name)
                 i-name
                 pat-exists?
                 args
                 fx-instances
                 needs-reroute?
                 isFx?)
          :solo (csound-pattern-solo i-name))
        pat-ctl))))

(defn csound-fx-control-data
  [host-pattern-name fx-name fx-controller-instr-number
   orc-string fx-form num-outputs release-time-secs
   init-hook release-hook config loop-self?]
  (fn [& args]
    (let [fx-instance (csound-make-instance
                       fx-name fx-controller-instr-number
                       orc-string num-outputs fx-form
                       (* 1000 release-time-secs) init-hook config true)]
      (merge fx-instance
             {:i-name  fx-name
              :args     args
              :kill-fx
              (fn [] (async/go
                       (when release-hook ((:compile fx-instance) release-hook))
                       (async/timeout release-time-secs)
                       (when-not (empty? (-> fx-instance :inputs first :connected-from-ports))
                         ((:stop fx-instance))
                         (swap! globals/pattern-registry dissoc fx-name)
                         (swap! csound-jna/csound-instances dissoc fx-name))))
              :fx-form  fx-form
              :loop-self? loop-self?}))))

(comment
  ((csound-pattern-control
    "BAAP27"
    1
    "instr 1
   asig = poscil:a(ampdb(p5), cpsmidinn(p4))
   aenv linseg 0, 0.02, 1, p3 - 0.05, 1, 0.02, 0, 0.01, 0
   asig *= aenv
   outc asig, asig
   endin"
    [{:name :dur :default 1}
     {:name :nn :default 60}
     {:name :amp :default -4}]
    2 10 {} false)
   :loop [4 4 4 4] :nn [79 78 77 76] :dur 4 :amp -34
   ;; :fx (panaeolus.csound.examples.fx/binauralize21
   ;;      :loop [0.25 0.25 0.5 0.5 0.5]
   ;;      :cent [0.11 0.5 0.25 0.125] :diff 2000)
   )

  ((:stop (get @csound-jna/csound-instances "BAAP27")))
  )
