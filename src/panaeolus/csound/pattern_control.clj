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

(defn fx-reroute-diffs
  [old-fx-instances new-fx-instances]
  (let [new-fx-instances-names (set (mapv :client-name new-fx-instances))
        old-fx-instances-names (set (mapv :client-name old-fx-instances))
        [killable* spawnable* survivable*]
        (diff old-fx-instances-names new-fx-instances-names)
        killable (clojure.set/difference
                  old-fx-instances-names
                  new-fx-instances-names)
        spawnable (set (remove nil? (or spawnable* [])))
        survivable (set (remove nil? (or survivable* [])))]
    [killable spawnable survivable]))

(defn disconnect-all-outputs [graph-node]
  (doseq [out-port (or (:jack-ports-out graph-node) [])]
    (doseq [into-port (jack/get-port-connections out-port)]
      (jack/disconnect
       (:jack-client graph-node)
       (jack/get-port-name out-port)
       into-port))))

(defn csound-reroute-jack
  [instrument-instance old-fx-instances new-fx-instances]
  (let [[killable spawnable survivable]
        (fx-reroute-diffs old-fx-instances new-fx-instances)
        killable-instances (filter #(contains? killable (:client-name %)) old-fx-instances)]
    (doseq [{:keys [stop]} killable-instances] (stop))
    (loop [graph (cons instrument-instance new-fx-instances)]
      (let [graph-node (first graph)
            next-node (second graph)
            survivable? (and graph-node
                             (or (contains? survivable (:client-name graph-node))
                                 (= (:client-name graph-node)
                                    (:client-name instrument-instance))))
            spawnable? (and graph-node
                            (contains? spawnable (:client-name graph-node)))]
        (if (or (not next-node) (empty? next-node))
          (do (when survivable? (disconnect-all-outputs graph-node))
           (dotimes [output-index (count (:jack-ports-out graph-node))]
             (let [out-port (nth (:jack-ports-out graph-node) output-index)
                   out-port-name (jack/get-port-name out-port)
                   system-out-base (get-in @config/config [:jack :system-out])
                   system-port-name (str system-out-base (inc output-index))]
               (jack/connect (:jack-client instrument-instance)
                             out-port-name
                             system-port-name))))
          (let [next-ports (:jack-ports-in next-node)]
            (when survivable? (disconnect-all-outputs graph-node))
            (dotimes [output-index (count (:jack-ports-out graph-node))]
              (let [out-port (nth (:jack-ports-out graph-node) output-index)
                    out-port-name (jack/get-port-name out-port)
                    in-port (nth next-ports output-index)
                    in-port-name (jack/get-port-name in-port)]
                (jack/connect (:jack-client instrument-instance)
                              out-port-name
                              in-port-name)))
            (recur (rest graph))))))))

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
    (if-let [current-state (get @globals/pattern-registry i-name)]
      (let [current-order (mapv :fx-name (:fx-instances current-state))
            new-order (mapv :fx-name fx-instances)
            needs-reroute? (not= current-order new-order)]
        (when needs-reroute?
          (csound-reroute-jack
           (:instrument-instance current-state)
           (:fx-instances current-state)
           fx-instances))
        (csound-update-pattern
         {:i-name i-name :args args :fx-instances fx-instances}))
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
