(ns panaeolus.control
  (:require [overtone.ableton-link :as link]
            [overtone.sc.node :as sc-node])
  (:use overtone.live))

(def pattern-registry (atom {}))

(def --dozed-patterns
  "When 1 pattern is solo,
  restart functions from the
  other patterns are kept here."
  (atom {}))

;; kill :all
#_(do (when-let [keyz (keys @pattern-registry)]
        (run! (fn [k] (let [v (get @pattern-registry k)]
                        (when (a-seq? v)
                          (run! safe-node-kill (filter synth-node? v)))))
              keyz))
      (reset! pattern-registry {}))

(defn synth-node? [v]
  (= overtone.sc.node.SynthNode (type v)))

(defn overtone-pattern-kill [k-name]
  (letfn [(safe-node-kill [node]
            (future
              (try
                (sc-node/node-free* node)
                (sc-node/kill node)
                (catch Exception e nil))))]
    (do (let [v (get @pattern-registry k-name)]
          (when (sequential? v)
            (run! safe-node-kill (filter synth-node? v))))
        (swap! pattern-registry dissoc k-name))))

(defn unsolo []
  (when-not (empty? @--dozed-patterns)
    (swap! pattern-registry merge @--dozed-patterns)
    (run! (fn [v] ((nth v 5))) (vals @--dozed-patterns))
    (reset! --dozed-patterns {})))

(defn solo [pat-name & [duration]]
  (let [dozed-state (dissoc @pattern-registry pat-name)]
    (reset! --dozed-patterns dozed-state)
    (reset! pattern-registry  {pat-name (get @pattern-registry pat-name)})
    (when (and (number? duration) (< 0 duration))
      (link/at (+ (Math/ceil (link/get-beat)) duration)
               #(unsolo)))))

(defn solo! [pat-name & [duration]]
  (let [soloed-map (select-keys @pattern-registry pat-name)]
    (when-not (empty? (dissoc @pattern-registry pat-name))
      (reset! pattern-registry soloed-map))))
