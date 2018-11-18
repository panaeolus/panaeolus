(ns panaeolus.csound.macros
  (:require clojure.string
            [panaeolus.config :as config]
            [panaeolus.csound.utils :as csound-utils]
            [panaeolus.csound.csound-jna :as csound-jna]
            [panaeolus.csound.pattern-control :as pat-ctl]
            [panaeolus.jack2.jack-lib :as jack]))

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
                             csound-instrument-number]
  (fn [& args]
    (let [processed-args (csound-utils/process-arguments param-vector args)]
      (csound-jna/input-message-async
       instance
       (clojure.string/join
        " " (into ["i" csound-instrument-number "0"]
                  (reduce (fn [i v]
                            (conj i
                                  (get processed-args (:name v)
                                       (:default v))))
                          []
                          synth-form)))))))

(defmacro definst [i-name orc-string synth-form csound-instrument-number num-outputs]
  (let [param-vector `(generate-param-vector-form ~synth-form)
        default-args `(generate-default-arg-form ~synth-form)]
    `(do (def ~i-name
           (let [i-name-str# ~(str i-name)
                 instance#   (if-let [inst# (get @csound-jna/csound-instances i-name-str#)]
                               inst#
                               (let [new-inst# (csound-jna/spawn-csound-client
                                                i-name-str# 0 ~num-outputs
                                                (:ksmps @config/config))]
                                 ((:start new-inst#))
                                 (doseq [chn# (range ~num-outputs)]
                                   (jack/connect (str i-name-str# ":output" (inc chn#))
                                                 (str (:jack-system-out @config/config) (inc chn#))))
                                 new-inst#))]
             (csound-jna/compile-orc (:instance instance#) ~orc-string)
             (swap! csound-jna/csound-instances assoc i-name-str# instance#)
             (input-message-closure (:instance instance#) ~param-vector ~synth-form
                                    ~csound-instrument-number)))
         (alter-meta! (var ~i-name) merge (meta (var ~i-name))
                      {:arglists      (list (mapv (comp name :name) ~synth-form)
                                            (mapv #(str (name (:name %)) "(" (:default %) ")")
                                                  ~synth-form))
                       :audio-enginge :csound
                       :inst          ~(str i-name)
                       :type          ::instrument})
         ~i-name)))

(defmacro definst+
  "Defines an instrument like definst does, but returns it
   with Panaeolus pattern controls."
  [i-name orc-string synth-form csound-instrument-number num-outputs]
  `(do (def ~i-name
         (let [instance-name# ~(str "-" (name i-name))
               inst#          (definst ~(symbol (str "-" (name i-name)))
                                ~orc-string ~synth-form ~csound-instrument-number ~num-outputs)
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

;; (beep1 :stop "^42 6/4 0x2e0ef r*1" :amp -22)

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
