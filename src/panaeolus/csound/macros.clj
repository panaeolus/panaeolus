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



#_(defn spawn-csound-instance [i-name orc-string synth-form csound-instrument-number num-outputs fx? config]
    (let [i-name-str (if fx? (str i-name) (name i-name))
          instance   (if-let [inst# (get @csound-jna/csound-instances i-name-str#)]
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
                             ~csound-instrument-number ~fx?))
    (alter-meta! (var ~i-name) merge (meta (var ~i-name))
                 {:arglists      (list (mapv (comp name :name) ~synth-form)
                                       (mapv #(str (name (:name %)) "(" (:default %) ")")
                                             ~synth-form))
                  :audio-enginge :csound
                  :inst          (str ~i-name)
                  :type          ::instrument})
    ~i-name)

#_(defn definst* [i-name orc-string synth-form csound-instrument-number num-outputs fx? config]
    (definst i-name orc-string synth-form csound-instrument-number num-outputs fx? config))

;;(str "-" pat-name# "-" ~(name fx-name))

(defmacro define-csound-fx
  "Defines an effect, by spawning instruments that
   expects equal amount of outputs as inputs.
   To prevent clicking effect on reverbs etc, set release-time
   high enough so that for node destruction, it's certain no
   audio is being processed."
  [fx-name orc-string fx-form fx-controller-instr-number num-outputs release-time-secs config]
  `(do
     (def ~fx-name
       (fn [& args#]
         (fn [host-pattern-name#]
           (let [fx-name# (str host-pattern-name# "$" ~(name fx-name))
                 loops-self?# (= :loop (first args#))]
             (when loops-self?#
               (apply (pat-ctl/csound-pattern-control
                       fx-name# ~orc-string ~fx-form
                       ~fx-controller-instr-number ~num-outputs ~config true) args#))
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

(define-csound-fx )

(defmacro definst-csound
  "Defines an instrument like definst does, but returns it
   with Panaeolus pattern controls."
  [i-name orc-string synth-form csound-instrument-number num-outputs config]
  `(do (def ~i-name
         (pat-ctl/csound-pattern-control
          ~(str *ns* "/" i-name) ~orc-string ~synth-form
          ~csound-instrument-number ~num-outputs ~config false))
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
