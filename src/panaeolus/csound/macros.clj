(ns panaeolus.csound.macros
  (:require clojure.string
            [panaeolus.csound.csound-jna :as csound-jna]
            [panaeolus.config :as config]
            [panaeolus.utils.utils :as utils]
            [panaeolus.csound.pattern-control :as pat-ctl]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]))

(set! *warn-on-reflection* true)

(defn generate-param-vector-form
  "Prepare data to be passed to `process-arguments`"
  [synth-form]
  (reduce #(-> %1 (conj (:name %2)) (conj (:default %2))) [] synth-form))

(defn generate-default-arg-form
  "Make a form so that it can be merged with input that
   would overwrite the default args."
  [synth-form]
  (reduce #(-> %1 (assoc (:name %2) (:default %2))) {} synth-form))

(s/def :parameter/name keyword?)

(s/def :parameter/default number?)

(s/def :parameter/transformer fn?)

(s/def ::parameter-spec (s/keys :req-un [:parameter/name :parameter/default]
                                :opt [:parameter/transformer]))

(s/def ::fx-form (s/* ::parameter-spec))

(s/def ::fx-name symbol?)

(s/def ::orc-string string?)

(s/def ::ctl-instr (s/and integer? #(not (neg? %))))

(s/def ::num-outs (s/and integer? #(not (neg? %))))

(s/def ::release-time (s/and number? #(not (neg? %))))

(s/def ::instance-config (s/keys :opt [:config/zerodbfs :config/ksmps]))

(s/def ::init-hook string?)

(s/def ::release-hook string?)

(s/def ::orc-filepath (s/and string? #(.exists (io/file %))))

(s/def ::orc-internal-filepath (s/and string? #(not (nil? (io/resource %)))))

(s/fdef define-fx*
  :args (s/cat :fx-name string?
               :define-fx-params
               (s/keys* :req-un [(or ::orc-string ::orc-filepath ::orc-internal-filepath)]
                        :opt [::fx-form ::ctl-instr ::num-outs
                              ::init-hook ::release-hook
                              ::release-time ::config]))
  :ret fn?)

(defn define-fx*
  [fx-name {:keys [orc-string fx-form ctl-instr num-outs release-time
                   init-hook release-hook instance-config] :as env}]
  (fn [& args]
    (fn [host-pattern-name chain-index]
      (let [orc-string (or orc-string
                           (and (:orc-filepath env)
                                (slurp (clojure.java.io/file (:orc-filepath env))))
                           (and (:orc-internal-filepath env)
                                (slurp (clojure.java.io/resource (:orc-internal-filepath env)))))
            fx-form (or fx-form [])
            num-outs (or num-outs 2)
            fx-name (utils/hash-jack-client-to-32 (str (name fx-name) "#" chain-index))
            loops-self? (= :loop (first args))
            release-time (or release-time 2)
            instance-config (or instance-config {})]
        (when loops-self?
          (apply (pat-ctl/csound-pattern-control
                  fx-name ctl-instr orc-string fx-form
                  num-outs release-time
                  init-hook release-hook instance-config true) args))
        (apply (pat-ctl/csound-fx-control-data
                host-pattern-name fx-name ctl-instr
                orc-string fx-form num-outs release-time
                init-hook release-hook instance-config loops-self?) args)))))

(s/fdef define-fx
  :args (s/cat :fx-name ::fx-name
               :define-fx-params (s/* any?)))

(defmacro define-fx
  "Defines an effect, by spawning instruments that
   expects equal amount of outputs as inputs.
   To prevent clicking effect on reverbs etc, set release-time
   high enough so that for node destruction, it's certain no
   audio is being processed."
  [fx-name & {:keys [orc-string fx-form ctl-instr num-outs release-time
                     init-hook release-hook instance-config] :as env}]
  `(do
     (def ~fx-name
       (define-fx* ~(str *ns* "/" fx-name) ~env))
     (alter-meta!
      (var ~fx-name) merge
      (meta (var ~fx-name))
      {:arglists      (list (mapv (comp name :name) ~fx-form)
                            (mapv #(str (name (:name %)) "(" (:default %) ")")
                                  ~fx-form))
       :audio-enginge :csound
       :type          ::fx})
     ~fx-name))

(s/def ::instr-name symbol?)

(s/def ::instr-number (s/and integer? #(not (neg? %))))

(s/def ::instr-form (s/+ ::parameter-spec))

(s/fdef definst*
  :args (s/cat :instr-name string?
               :definst-params
               (s/keys* :req-un [::instr-form ::instr-number
                                 (or ::orc-string ::orc-filepath ::orc-internal-filepath)]
                        :opt [::num-outs ::init-hook ::release-hook
                              ::release-time ::config]))
  :ret fn?)

(defn definst*
  [instr-name {:keys [orc-string instr-form instr-number num-outs release-time config] :as env}]
  (let [orc-string (or orc-string
                       (and (:orc-filepath env)
                            (slurp (io/file (:orc-filepath env))))
                       (and (:orc-internal-filepath env)
                            (slurp (io/resource (:orc-internal-filepath env)))))
        num-outs (or num-outs 2)
        release-time (or release-time 2)
        config (or config {})]
    (pat-ctl/csound-pattern-control
     (utils/hash-jack-client-to-32 instr-name)
     instr-number
     orc-string
     instr-form
     num-outs
     release-time
     nil nil
     config
     false)))

(s/fdef definst
  :args (s/cat :instr-name ::instr-name
               :definst-params (s/* any?)))

(defmacro definst
  "Defines an instrument like definst does, but returns it
   with Panaeolus pattern controls."
  [instr-name & {:keys [instr-form] :as env}]
  `(do
     (def ~instr-name
       (definst* ~(str *ns* "/" instr-name) ~env))
     (alter-meta! (var ~instr-name) merge (meta (var ~instr-name))
                  {:arglists      (list (mapv (comp name :name) ~instr-form)
                                        (mapv #(str (name (:name %)) "(" (:default %) ")")
                                              ~instr-form))
                   :audio-enginge :csound
                   :inst          ~(str *ns* "/" instr-name)
                   :type          ::instrument})
     ~instr-name))
