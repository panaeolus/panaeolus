(ns panaeolus.csound.pattern-control
  (:require
   [panaeolus.event-loop :refer [event-loop-instrument]]
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

(defn csound-pattern-stop
  [k-name]
  (when-let [pattern-state (get @globals/pattern-registry k-name)]
    (let [{:keys [instrument-instance fx-instances]} pattern-state]
      (swap! globals/pattern-registry dissoc k-name)
      (loop [[graph-node & rest] (cons instrument-instance fx-instances)]
        (when graph-node
          ((:stop graph-node))
          (recur rest))))
    :stop))

(defn csound-pattern-solo
  [k-name]
  (let [pattern (select-keys @globals/pattern-registry [k-name])]
    (when-not (empty? pattern) (reset! globals/pattern-registry pattern)))
  :solo)

(defn csound-initialize-jack-graph
  "Connect new pattern jack ports, if jack nodes
   already exits, then ensure that the connections
   match up."
  [instrument-instance fx-instances]
  (let [linear-graph (cons instrument-instance fx-instances)]
    (loop [graph linear-graph]
      (when-not (empty? graph)
        (let [graph-node (first graph)
              next-node (second graph)]
          (if (or (not next-node) (empty? next-node))
            (dotimes [output-index (count (:jack-ports-out graph-node))]
              (let [out-port (nth (:jack-ports-out graph-node) output-index)
                    out-port-name (jack/get-port-name out-port)
                    system-out-base (get-in @config/config [:jack :system-out])
                    system-port-name (str system-out-base (inc output-index))]
                (jack/connect (:jack-client instrument-instance)
                              out-port-name
                              system-port-name)))
            (let [next-ports (:jack-ports-in next-node)]
              (dotimes [output-index (count (:jack-ports-out graph-node))]
                (let [out-port (nth (:jack-ports-out graph-node) output-index)
                      out-port-name (jack/get-port-name out-port)
                      in-port (nth next-ports output-index)
                      in-port-name (jack/get-port-name in-port)]
                  (jack/connect (:jack-client instrument-instance)
                                out-port-name
                                in-port-name)))
              (recur (rest graph)))))))))

(defn csound-make-instance
  [{:keys [i-name instr-form instr-number release-time
           config num-outs orc-string init-hook
           isFx? fx-form ctl-instr]}]
  (let [input-msg-cb (when-not (and isFx? (not ctl-instr))
                      (csound-jna/input-message-closure
                       (if isFx? fx-form instr-form)
                       (if isFx? ctl-instr instr-number)))
        new-instance (csound-jna/spawn-csound-client
                      {:requested-client-name i-name
                       :inputs (if isFx? num-outs 0)
                       :outputs num-outs
                       :config config
                       :release-time release-time
                       :input-msg-cb input-msg-cb})
        {:keys [start compile]} new-instance]
    (compile (str orc-string "\n" init-hook))
    (start)
    new-instance))

(s/def ::valid-live-code-modifier?
  (s/and keyword? #(contains? #{:stop :solo :kill} %)))

(def valid-live-code-action? #(contains? #{:loop :stop :solo :kill} %))

(def valid-live-code-pattern?
  (s/or
   :single-note-loop number?
   :minilang string?
   :sequence
   (fn [second-argument]
     (and (sequential? second-argument) (not (empty? second-argument))))))

(def valid-parameters?
  (s/cat
   :parameter keyword?
   :value (fn [parameter-value] (not (nil? parameter-value)))))

(s/def ::live-code-arguments
  (s/alt
   :modifier
   (s/cat
    :modifier ::valid-live-code-modifier?
    :rest (s/* any?))
   :initializer
   (s/cat
    :valid-live-code-action valid-live-code-action?
    :valid-live-code-pattern valid-live-code-pattern?
    :opt-args (s/* valid-parameters?))))

;; NOTE, you can also get the old-fx-instances
;; from the csound-instances atom at this point.
#_(defn csound-reroute-jack
  [instrument-instance old-fx-instances new-fx-instances]
  (let [new-fx-instances-names (mapv :client-name new-fx-instances)
        old-fx-instances-names (mapv :client-name old-fx-instances)
        [kill-chain _ survivor-chain]
        (diff old-fx-instances-names new-fx-instances-names)
        kill-chain (remove nil? (or kill-chain []))
        kill-chain
        (if (empty? kill-chain)
          []
          (->>
           old-fx-instances
           (split-at
            (.indexOf
             ^clojure.lang.PersistentVector old-fx-instances
             (first kill-chain)))
           second
           vec))
        survivor-chain (or survivor-chain [])
        linear-survivor-chain (vec (take-while #(not (nil? %)) survivor-chain))
        last-survivor (last linear-survivor-chain)
        linear-new-fx
        (if-not last-survivor
          new-fx-instances-names
          (->
           (split-at
            (.indexOf
             ^clojure.lang.PersistentVector new-fx-instances-names
             last-survivor)
            new-fx-instances-names)
           second
           vec))]
    (fn []
      (doseq [killable-node kill-chain]
        ;; Take rare case into account where fx with same index
        ;; exists within a kill chain, example
        ;; (diff [:src :fx1 :fx2 :fx3 :fx4 :fx5] [:src :fx1 :fx2 :fx6 :fx4
        ;; :fx5])
        ;; where :fx5 needs to re-routing but at the same time survive
        (let [killable-node-survives?
              (some #(= % killable-node) survivor-chain)]
          (when killable-node-survives?
            (doseq [output-port (:outputs killable-node)]
              (apply jack/disconnect
                     (:port-name output-port)
                     (:connected-to-ports output-port)))
            (doseq [input-port (:inputs killable-node)]
              (run!
               #(jack/disconnect % (:port-name input-port))
               (:connected-from-ports input-port))))
          (doseq [output-port (:outputs killable-node)]
            (swap! csound-jna/csound-instances update-in
                   [(:client-name killable-node) :outputs
                    (:channel-index output-port)]
                   assoc
                   :connected-to-ports []
                   :connected-to-instances []))
          (doseq [input-port (:inputs killable-node)]
            (swap! csound-jna/csound-instances update-in
                   [(:client-name killable-node) :inputs (:channel-index input-port)]
                   assoc
                   :connected-from-ports []
                   :connected-from-instances []))
          (when (not killable-node-survives?)
            (swap! csound-jna/csound-instances assoc-in
                   [(:client-name killable-node) :scheduled-to-kill?]
                   true)
            (async/go
              (async/<! (async/timeout (:release-time instrument-instance)))
              (let [killable-node-post-release
                    (get
                     @csound-jna/csound-instances
                     (:client-name killable-node))]
                (when killable-node-post-release
                  (if (:scheduled-to-kill? killable-node-post-release)
                    (do
                      ((:stop killable-node-post-release))
                      (swap! csound-jna/csound-instances dissoc
                             (:client-name killable-node))))
                  ;; Something has connected itself to this node
                  ;; while it was in release (very rare case!)
                  ;; only disconnect non-current connections
                  (let [;;current-connections (set (jack/query-connection
                        ;;killable-node-name))
                        current-inputs
                        (set
                         (mapv :port-name
                               (:inputs killable-node-post-release)))
                        previous-inputs
                        (set (mapv :port-name (:inputs killable-node)))
                        current-outputs
                        (set
                         (mapv :port-name
                               (:outputs killable-node-post-release)))
                        previous-outputs
                        (set (mapv :port-name (:outputs killable-node)))]
                    (doseq [output-port (:outputs killable-node-post-release)]
                      (when
                          (and
                           (contains? previous-outputs (:port-name output-port))
                           (not
                            (contains?
                             current-outputs
                             (:port-name output-port))))
                        (apply jack/disconnect
                               output-port
                               (or
                                (jack/query-connection (:port-name output-port))
                                []))))
                    (doseq [input-port (:inputs killable-node-post-release)]
                      (when
                          (and
                           (contains? previous-inputs (:port-name input-port))
                           (not
                            (contains? current-inputs (:port-name input-port))))
                        (run!
                         #(jack/disconnect % input-port)
                         (or
                          (jack/query-connection (:port-name input-port))
                          [])))))))))))
      (when
          (or
           (empty? linear-new-fx)
           (not= (first old-fx-instances-names) (first new-fx-instances-names)))
        ;; this means root needs re-routing
        (if (empty? new-fx-instances) ;; connect to system?
          (let [current-outputs (:outputs instrument-instance)]
            (doseq [output current-outputs]
              (let [connected-to-port (first (:connected-to-ports output))
                    system-out-base (get-in @config/config [:jack :system-out])
                    system-port-name
                    (str system-out-base (inc (:channel-index output)))]
                (when connected-to-port
                  (jack/disconnect (:port-name output) connected-to-port))
                (when
                    (<
                     (:channel-index output)
                     (get-in @config/config [:csound :nchnls]))
                  (jack/connect (:port-name output) system-port-name))
                (swap! csound-jna/csound-instances update-in
                       [(:client-name instrument-instance) :outputs
                        (:channel-index output)]
                       assoc
                       :connected-to-ports
                       (if
                           (<
                            (:channel-index output)
                            (get-in @config/config [:csound :nchnls]))
                         [system-port-name]
                         [])
                       :connected-to-instances
                       (if
                           (<
                            (:channel-index output)
                            (get-in @config/config [:csound :nchnls]))
                         ["system"]
                         [])))))
          ;; connect to first node
          (let [current-outputs (:outputs instrument-instance)
                next-node (first new-fx-instances)]
            (doseq [output current-outputs]
              (let [connected-to-port (first (:connected-to-ports output))
                    next-port-name
                    (:port-name
                     (nth (:inputs next-node) (:channel-index output)))]
                (when connected-to-port
                  (jack/disconnect (:port-name output) connected-to-port))
                (when next-port-name
                  (jack/connect (:port-name output) next-port-name)
                  (swap! csound-jna/csound-instances update-in
                         [(:client-name next-node) :inputs (:channel-index output)]
                         #(->
                           %
                           (update :connected-from-ports conj (:port-name output))
                           (update
                            :connected-from-instances
                            conj
                            (:client-name instrument-instance)))))
                (swap! csound-jna/csound-instances update-in
                       [(:client-name instrument-instance) :outputs
                        (:channel-index output)]
                       assoc
                       :connected-to-ports (if next-port-name [next-port-name] [])
                       :connected-to-instances
                       (if next-port-name [(:client-name next-node)] [])))))))
      (loop [graph-node-names linear-new-fx]
        (if (empty? graph-node-names)
          nil
          (let [graph-node
                (get @csound-jna/csound-instances (first graph-node-names))
                next-node
                (and
                 (second graph-node-names)
                 (get
                  @csound-jna/csound-instances
                  (second graph-node-names)))]
            (swap! csound-jna/csound-instances assoc-in
                   [(first graph-node-names) :scheduled-to-kill?]
                   false)
            (when-let [outputs (:outputs graph-node)]
              (run!
               (fn
                 [{:keys
                   [port-name channel-index connected-to-ports
                    connected-to-instances],
                   :as env}]
                 ;; Debugging
                 (if next-node
                   (when (< channel-index (count (:inputs next-node)))
                     (let [next-node-inputs (:inputs next-node)
                           next-port-name
                           (:port-name (nth next-node-inputs channel-index))]
                       (async/go-loop
                           [retry 0]
                         (let [query-result
                               (jack/query-connection
                                (:client-name graph-node))]
                           (if
                               (and
                                query-result
                                (some #(= port-name %) query-result)
                                (some #(= next-port-name %) query-result))
                             (jack/connect port-name next-port-name)
                             (do
                               (async/<! (async/timeout 25))
                               (if (>= retry 10)
                                 (jack/connect port-name next-port-name)
                                 #_(throw
                                    (Exception.
                                     (str
                                      "Error in starting csound instrument, "
                                      (:client-name graph-node)
                                      " did not start.")))
                                 (recur (inc retry)))))))
                       (swap! csound-jna/csound-instances update-in
                              [(:client-name graph-node) :outputs channel-index]
                              #(->
                                %
                                (update :connected-to-ports conj next-port-name)
                                (update
                                 :connected-to-instances
                                 conj
                                 (:client-name next-node))))
                       (swap! csound-jna/csound-instances update-in
                              [(:client-name next-node) :inputs channel-index]
                              #(->
                                %
                                (update :connected-from-ports conj next-port-name)
                                (update
                                 :connected-from-instances
                                 conj
                                 (:client-name graph-node))))))
                   (when
                       (<
                        channel-index
                        (get-in @config/config [:csound :nchnls]))
                     (let [system-out-base
                           (get-in @config/config [:jack :system-out])
                           system-port-name
                           (str system-out-base (inc channel-index))]
                       (async/go-loop
                           [retry 0]
                         (let [query-result
                               (jack/query-connection
                                (:client-name graph-node))]
                           (if
                               (and
                                query-result
                                (some #(= port-name %) query-result))
                             (jack/connect port-name system-port-name)
                             (if (>= retry 10)
                               (throw
                                (Exception.
                                 (str
                                  "Error in starting csound instrument, "
                                  (:client-name graph-node)
                                  " did not start.")))
                               (and
                                (async/<! (async/timeout 25))
                                (recur (inc retry)))))))
                       (swap! csound-jna/csound-instances update-in
                              [(:client-name graph-node) :outputs channel-index]
                              #(->
                                %
                                (update :connected-to-ports conj system-port-name)
                                (update
                                 :connected-to-instances
                                 conj
                                 "system")))))))
               outputs))
            (recur (rest graph-node-names))))))))

;; (update-in {:a {:b [nil {:c [] :d []}]}} [:a :b 1] #( -> % (update :c conj
;; 1)
;; (update :d conj 2)))

(defn csound-register-pattern
  "Register a new pattern or upadte an existing one."
  [i-name instrument-instance fx-instances isFx? args]
  (let [beats (utils/extract-beats args)]
    (swap! globals/pattern-registry assoc
           i-name
           {:i-name i-name
            :instrument-instance instrument-instance
            :args (rest (rest args))
            :beats beats
            :fx-instances (if isFx? [] fx-instances)
            :isFx? isFx?})))

(defn csound-update-pattern [{:keys [args i-name fx-instances]}]
  (let [updated-args (rest (rest args))
        updated-beats (utils/extract-beats args)]
    (swap! globals/pattern-registry
           update i-name merge
           {:args updated-args
            :beats updated-beats
            :fx-instances fx-instances})))

#_(defn calculate-debounce-time
    "calculate the debounce time for
   all the threads in a chain based
   on the maximum release-time value"
    [instrument-release-time fx-instances]
    (apply max (conj (map :release-time fx-instances) instrument-release-time)))

#_(defn csound-kill-jack-chain
  [root-name]
  (loop [cur-nodes [(get @csound-jna/csound-instances root-name)]]
    (when-let [cur-node (first cur-nodes)]
      (when (fn? (:stop cur-node)) ((:stop cur-node)))
      ;; in case these are self-looping fx's, we dissoc pattern-reg too
      (swap! globals/pattern-registry dissoc (:client-name cur-node))
      (swap! csound-jna/csound-instances dissoc (:client-name cur-node))
      (let [next-instances
            (->
             cur-node
             :outputs
             first
             :connected-to-instances)
            next-nodes
            (mapv #(get @csound-jna/csound-instances %) next-instances)
            cur-nodes (into (rest cur-nodes) next-nodes)]
        (when-not (empty? cur-nodes) (recur cur-nodes)))))
  ;; kill zombies
  (run!
   (fn [[key val]]
     (when (clojure.string/includes? (str key) root-name)
       (and (fn? (:stop val)) ((:stop val)))
       (swap! globals/pattern-registry dissoc key)
       (swap! csound-jna/csound-instances dissoc key)))
   @csound-jna/csound-instances))

(defn args->args [{:keys [instr-form]} args]
  (let [argv-positions (mapv :name instr-form)
        args-parsed (if (string? (second args))
                      (sequence-parser/process-parseable-pattern
                       args
                       argv-positions)
                      args)]
    (utils/fill-missing-keys
     args-parsed argv-positions)))

(defmulti csound-pattern-control
  (fn [env & args]
    (first args)))

(defmethod csound-pattern-control :stop
  [{:keys [i-name]} & args]
  (csound-pattern-stop i-name))

(defmethod csound-pattern-control :loop
  [{:keys [i-name] :as env} & args]
  {:pre [(s/valid? ::live-code-arguments args)]}
  (let [args (args->args env args)
        [args fx-args] (utils/seperate-fx-args args)
        fx-instances (reduce (fn [acc fx-cb] (conj acc (fx-cb i-name))) [] fx-args)]
    (if (get @globals/pattern-registry i-name)
      (csound-update-pattern
       {:i-name i-name :args args :fx-instances fx-instances})
      (let [{:keys [i-name]} env
            instrument-instance (csound-make-instance env)]
        (csound-initialize-jack-graph instrument-instance fx-instances)
        (csound-register-pattern
         i-name instrument-instance fx-instances false args)
        (event-loop-instrument
         (fn [] (get @globals/pattern-registry i-name)))))
    :loop))

(defmethod csound-pattern-control nil [_ & r] :error)

(defn make-pattern-control
  [env]
  (fn [& args]
    (apply csound-pattern-control env args)))

;; host-pattern-name fx-name fx-controller-instr-number orc-string fx-form
;;    num-outputs release-time-secs init-hook release-hook config loop-self?

(defn ^:private find-fx
  [fx-name fx-instances]
  (loop [[i & r] fx-instances]
    (when i
      (if (= (:fx-name i) fx-name)
        i (recur r)))))

(defn make-fx-control
  [{:keys [fx-name host-pattern-name] :as env} & args]
  (let [current-host-pattern (get @globals/pattern-registry host-pattern-name)
        current-fx-instance (and current-host-pattern
                                 (find-fx fx-name (:fx-instances current-host-pattern)))
        fx-instance (or current-fx-instance
                        (csound-make-instance
                         (assoc env :i-name fx-name)))]
    (merge
     env fx-instance
     {:i-name fx-name
      :args (or args [])
      ;; disabled feature for now
      :loop-self? false})))

(comment
  (def xxx
    (make-pattern-control
     {:i-name "BAAP4"
      :instr-number 1
      :orc-string
      "instr 1
   asig = poscil:a(ampdb(p5), cpsmidinn(p4))
   aenv linseg 0, 0.02, 1, p3 - 0.05, 1, 0.02, 0, 0.01, 0
   asig *= aenv
   outc asig, asig
   endin"
      :release-time 1
      :num-outs 2
      :instr-form
      [{:name :dur :default 2}
       {:name :nn :default 48}
       {:name :amp :default -12}]}))

  (xxx :stop [0.25] :dur 0.2 :nn [[60 64 67] 60 62 72])
  #_((csound-pattern-control
      "BAAP27"
      1
      "instr 1
   asig = poscil:a(ampdb(p5), cpsmidinn(p4))
   aenv linseg 0, 0.02, 1, p3 - 0.05, 1, 0.02, 0, 0.01, 0
   asig *= aenv
   outc asig, asig
   endin"
      [{:name :dur, :default 1} {:name :nn, :default 60}
       {:name :amp, :default -4}]
      2
      10
      {}
      false)
     :loop
     [4 4 4 4]
     :nn
     [79 78 77 76]
     :dur
     4
     :amp
     -34
     ;; :fx (panaeolus.csound.examples.fx/binauralize21
     ;;      :loop [0.25 0.25 0.5 0.5 0.5]
     ;;      :cent [0.11 0.5 0.25 0.125] :diff 2000)
     )
  ((:stop (get @csound-jna/csound-instances "BAAP27"))))
