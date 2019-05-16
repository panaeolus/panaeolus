(ns panaeolus.csound.macros
  (:require clojure.string
            ;; [panaeolus.config :as config]
            [panaeolus.csound.csound-jna :as csound-jna]
            [panaeolus.csound.pattern-control :as pat-ctl]
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


(defmacro define-fx
  "Defines an effect, by spawning instruments that
   expects equal amount of outputs as inputs.
   To prevent clicking effect on reverbs etc, set release-time
   high enough so that for node destruction, it's certain no
   audio is being processed."
  [fx-name orc-string fx-form fx-controller-instr-number num-outputs release-time-secs config]
  `(do
     (def ~fx-name
       (fn [& args#]
         (fn [host-pattern-name# chain-index#]
           (let [fx-name# (str host-pattern-name# "#" ~(name fx-name) "#" chain-index#)
                 loops-self?# (= :loop (first args#))]
             (when loops-self?#
               (apply (pat-ctl/csound-pattern-control
                       fx-name# ~fx-controller-instr-number ~orc-string ~fx-form
                       ~num-outputs ~release-time-secs ~config true) args#))
             (apply (pat-ctl/csound-fx-control-data
                     host-pattern-name# fx-name# ~fx-controller-instr-number
                     ~orc-string ~fx-form  ~num-outputs ~release-time-secs
                     ~config loops-self?#) args#)))))
     (alter-meta!
      (var ~fx-name) merge
      (meta (var ~fx-name))
      {:arglists      (list (mapv (comp name :name) ~fx-form)
                            (mapv #(str (name (:name %)) "(" (:default %) ")")
                                  ~fx-form))
       :audio-enginge :csound
       :type          ::fx})
     ~fx-name))


(defmacro definst
  "Defines an instrument like definst does, but returns it
   with Panaeolus pattern controls."
  [i-name orc-string synth-form csound-instrument-number num-outputs release-time-secs config]
  `(do (def ~i-name
         (pat-ctl/csound-pattern-control
          ~(str *ns* "/" i-name) ~csound-instrument-number
          ~orc-string ~synth-form
          ~num-outputs ~release-time-secs ~config false))
       (alter-meta! (var ~i-name) merge (meta (var ~i-name))
                    {:arglists      (list (mapv (comp name :name) ~synth-form)
                                          (mapv #(str (name (:name %)) "(" (:default %) ")")
                                                ~synth-form))
                     :audio-enginge :csound
                     :inst          (str ~i-name)
                     :type          ::instrument})
       ~i-name))

(defmacro kill! [pname]
  `(let [pname# (str '~`pname)]
     (run! (fn [[key# val#]]
             (when (clojure.string/includes?
                    (str key#) pname#)
               (and (fn? (:stop val#)) ((:stop val#)))
               (swap! panaeolus.globals/pattern-registry dissoc key)
               (swap! csound-jna/csound-instances dissoc key)))
           @csound-jna/csound-instances)))

(comment

  (def params [{:name :dur :default 1}
               {:name :nn :default 60}
               {:name :amp :default -12}])

  (clojure.pprint/pprint
   (macroexpand-1
    (definst-csound beep31
      "instr 1
   asig = poscil:a(ampdb(p4), cpsmidinn(p5))
   outc asig, asig
   endin"
      params
      1 2 {})))

  (beep31 1 -12 58)

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

  (meta #'beep31)


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
