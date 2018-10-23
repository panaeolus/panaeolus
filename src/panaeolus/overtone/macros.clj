(ns panaeolus.overtone.macros
  (:require
   [overtone.sc.synth :refer [synth-form]]
   [overtone.studio.inst :as sudio-inst]
   [panaeolus.overtone.pattern-control :refer [pattern-control]]
   [panaeolus.overtone.event-callback]))

(defmacro adapt-fx
  "Takes a normal fx from overtone and adapts it to africanus"
  [original-fx new-name default-args]
  (let [original-fx-name  `(keyword (:name ~original-fx))
        new-arglists      `(list (->> (:arglists (meta (var ~original-fx)))
                                      first
                                      (map #(symbol (name %)))
                                      rest
                                      vec)
                                 (or ~default-args []))
        new-name-and-meta (with-meta new-name
                            {:arglists new-arglists
                             :fx-name  original-fx-name})]
    `(def ~new-name-and-meta
       (fn [& args#]
         [~original-fx-name ~original-fx
          (--fill-missing-keys-for-ctl args# (mapv keyword (first ~new-arglists)))]))))

(defmacro definst+
  "Defines an instrument like definst does, but returns it
   with Panaeolus pattern controls."
  {:arglists '([name envelope-type params ugen-form])}
  [i-name envelope-type & inst-form]
  (let [[i-name params ugen-form]
        (synth-form i-name inst-form)
        i-name-str          (name i-name)
        orig-arglists       (:arglists (meta i-name))
        arglists-w-defaults (reduce (fn [i v] (if (symbol? v)
                                                (conj i (keyword v))
                                                (conj i v))) [] (first inst-form))
        i-name-new-meta     (assoc (meta i-name)
                                   :arglists (list 'quote
                                                   (list (conj (->> orig-arglists
                                                                    second first
                                                                    (map #(symbol (name %)))
                                                                    (cons 'beats)
                                                                    (cons 'pat-ctl) 
                                                                    (into []))
                                                               'fx)
                                                         arglists-w-defaults)))
        i-name              (with-meta i-name (merge i-name-new-meta {:type ::instrument}))
        inst                `(sudio-inst/inst ~i-name ~params ~ugen-form)]
    `(def ~(vary-meta i-name assoc :inst inst)
       (pattern-control ~i-name-str ~envelope-type (mapv keyword (first ~orig-arglists)) ~inst))))
