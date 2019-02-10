(ns panaeolus.csound.macros
  (:require clojure.string
            [panaeolus.config :as config]
            [panaeolus.csound.utils :as csound-utils]
            [panaeolus.csound.csound-jna :as csound-jna]
            [panaeolus.csound.pattern-control :as pat-ctl]
            [panaeolus.jack2.jack-lib :as jack]
            [clojure.core.async :refer [<! >! timeout go go-loop chan put! poll!] :as async]))

(defn generate-param-vector-form
  "Prepare data to be passed to `process-arguments`"
  [synth-form]
  (reduce #(-> %1 (conj (:name %2)) (conj (:default %2))) [] synth-form))

(defn generate-default-arg-form
  "Make a form so that it can be merged with input that
   would overwrite the default args."
  [synth-form]
  (reduce #(-> %1 (assoc (:name %2) (:default %2))) {} synth-form))


(defn input-message-closure [instance param-vector synth-form
                             csound-instrument-number fx?]
  (fn [& args]
    (let [processed-args (csound-utils/process-arguments param-vector args)]
      (csound-jna/input-message-async
       instance
       (clojure.string/join
        " " (into ["i" csound-instrument-number "0" (if fx? "0.1" "")]
                  (reduce (fn [i v]
                            (conj i
                                  (get processed-args (:name v)
                                       (:default v))))
                          []
                          synth-form)))))))

(defmacro definst [i-name orc-string synth-form csound-instrument-number num-outputs fx? config]
  (let [param-vector `(generate-param-vector-form ~synth-form)
        default-args `(generate-default-arg-form ~synth-form)]
    `(do (def ~i-name
           (let [i-name-str# (if ~fx? (str ~i-name) ~(name i-name))
                 instance#   (if-let [inst# (get @csound-jna/csound-instances i-name-str#)]
                               (:instance inst#)
                               (let [new-inst#
                                     (csound-jna/spawn-csound-client
                                      i-name-str# (if ~fx? ~num-outputs 0) ~num-outputs
                                      (or ~(:ksmps config) (:ksmps @config/config)))]
                                 ((:start new-inst#))
                                 (when-not ~fx?
                                   (doseq [chn# (range ~num-outputs)]
                                     (try
                                       (jack/connect (str i-name-str# ":output" (inc chn#))
                                                     (str (:jack-system-out @config/config) (inc chn#)))
                                       (catch Exception e# nil))))
                                 new-inst#))]
             (csound-jna/compile-orc (:instance instance#) ~orc-string)
             (swap! csound-jna/csound-instances assoc
                    i-name-str# {:instance     instance#
                                 :fx-instances []})
             (input-message-closure (:instance instance#) ~param-vector ~synth-form
                                    ~csound-instrument-number ~fx?)))
         (alter-meta! (var ~i-name) merge (meta (var ~i-name))
                      {:arglists      (list (mapv (comp name :name) ~synth-form)
                                            (mapv #(str (name (:name %)) "(" (:default %) ")")
                                                  ~synth-form))
                       :audio-enginge :csound
                       :inst          (str ~i-name)
                       :type          ::instrument})
         ~i-name)))

(defn definst* [i-name orc-string synth-form csound-instrument-number num-outputs fx? config]
  (definst i-name orc-string synth-form csound-instrument-number num-outputs fx? config))

;;(str "-" pat-name# "-" ~(name fx-name))

(defmacro define-fx
  "Defines an effect, by spawning instruments that
   expects equal amount of outputs as inputs."
  [fx-name orc-string fx-form fx-controller-instr-number num-outputs config]
  `(do (def ~fx-name
         (fn [& args#]
           (fn [pat-name# fx-handle-atom#]
             (let [fx-name#  (str "-" pat-name# "-" ~(name fx-name))
                   instance# (or (get (deref fx-handle-atom#) fx-name#)
                                 ;; i-name orc-string synth-form csound-instrument-number num-outputs fx? config
                                 (definst* (symbol fx-name#)
                                   ~orc-string ~fx-form ~fx-controller-instr-number
                                   ~num-outputs true ~config))]
               {:fx-name  fx-name#
                :instance instance#
                :args     args#
                :kill-fx  (fn [] (go (<! (timeout 5))
                                     (csound-jna/stop instance#)
                                     (<! (timeout 5))
                                     (csound-jna/cleanup instance#)
                                     (swap! csound-jna/csound-instances dissoc fx-name#)))
                :fx-form  ~fx-form}))))
       (alter-meta!
        (var ~fx-name) merge
        (meta (var ~fx-name))
        {:arglists      (list (mapv (comp name :name) ~fx-form)
                              (mapv #(str (name (:name %)) "(" (:default %) ")")
                                    ~fx-form))
         :audio-enginge :csound
         :type          ::fx})
       ~fx-name))

(defmacro definst+
  "Defines an instrument like definst does, but returns it
   with Panaeolus pattern controls."
  [i-name orc-string synth-form csound-instrument-number num-outputs config]
  `(do (def ~i-name
         (let [instance-name# (str "-" ~(name i-name))
               inst#          (definst ~(symbol (str "-" (name i-name)))
                                ~orc-string ~synth-form ~csound-instrument-number ~num-outputs false
                                ~config)
               ;;instance#      (get @csound-jna/csound-instances instance-name#)
               ]
           (pat-ctl/csound-pattern-control
            ~(name i-name) :perc (mapv :name ~synth-form) inst#)))
       (alter-meta!
        (var ~i-name) merge
        (meta (var ~(symbol (str "-" (name i-name)))))
        (meta (var ~i-name))
        {:arglists (list (into
                          ["pat-ctl" "beats"]
                          (rest
                           (conj (first (:arglists (meta  (var ~(symbol (str "-" (name i-name)))))))
                                 "fx")))
                         (vec
                          (rest
                           (second (:arglists (meta (var ~(symbol (str "-" (name i-name))))))))))})
       ~i-name))

;; (beep2 :stop "^42 6/4 0x2e0ef r*1" :amp -22)

(comment

  (def params [{:name :dur :default 1}
               {:name :nn :default 60}
               {:name :amp :default -12}])

  (clojure.pprint/pprint
   (macroexpand-1
    '(definst+ beep31
       "instr 1
   asig = poscil:a(ampdb(p4), cpsmidinn(p5))
   outc asig, asig
   endin"
       params
       1 2 :perc)))

  (definst+ beep1
    "instr 1
   asig = poscil:a(ampdb(p5), cpsmidinn(p4))
   aenv linseg 0, 0.02, 1, p3 - 0.05, 1, 0.02, 0, 0.01, 0
   asig *= aenv
   outc asig, asig
   endin"
    params
    1 2)

  (definst beep31
    "instr 1
   asig = poscil:a(ampdb(p4), cpsmidinn(p5))
   outc asig, asig
   endin"
    params
    1 2 )

  (meta #'beep26)

  (beep30 1 -12 58)

  (def wrong-meta
    (-> (fn [some thing else]
          (+ some thing else))
        (with-meta {:arglists '['bull 'crap]})))

  (wrong-meta 1 2 3)

  (def changeme
    (fn [some thing else]
      (+ some thing else)))

  (alter-meta! (var changeme) merge
               (meta (var changeme))
               {:arglists '([test1 test2])})

  (changeme 1 2 3)
  )
