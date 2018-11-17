(ns panaeolus.control
  (:require [overtone.ableton-link :as link]
            [overtone.sc.node :as sc-node]
            [clojure.core.async :refer [<! >! timeout go go-loop chan put! poll!] :as async])
  (:use overtone.live))

(def pattern-registry (atom {}))

(def --dozed-patterns
  "When 1 pattern is solo,
  restart functions from the
  other patterns are kept here."
  (atom {}))

(defn synth-node? [v]
  (= overtone.sc.node.SynthNode (type v)))

(defonce pattern-watcher
  (go-loop []
    (let [active-insts
          (map #(or (get-in % [:instrument-instance :name])
                    (str (:envelope-type %)))
               (map val @pattern-registry))]
      (when-not (empty? active-insts)
        (println (str "Acive patterns: " (vec active-insts))))
      (<! (timeout 30000))
      (recur))))

(defn pkill [k-name]
  (letfn [(safe-node-kill [node]
            (future
              (try
                (node-free* node)
                (kill node)
                (catch Exception e nil))))]
    (if (= :all k-name)
      (do (when-let [keyz (keys @pattern-registry)]
            (run! (fn [k] (let [v (get @pattern-registry k)]
                            (when (sequential? v)
                              (run! safe-node-kill (filter synth-node? v)))))
                  keyz))
          (reset! pattern-registry {}))
      (do (let [v (get @pattern-registry k-name)]
            (when (sequential? v)
              (run! safe-node-kill (filter synth-node? v))))
          (swap! pattern-registry dissoc k-name)))))

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
            (go
              (<! (timeout 4999))
              (try
                (sc-node/node-free* node)
                (sc-node/kill node)
                (catch Exception e nil))))]
    (do (let [v (get @pattern-registry k-name)]
          (when (= :inf (:envelope-type v))
            (safe-node-kill (:instrument-instance v)))
          (run! safe-node-kill (or (flatten (vals (:current-fx v))) [])))
        (swap! pattern-registry dissoc k-name))))

(defn unsolo []
  (when-not (empty? @--dozed-patterns)
    (swap! pattern-registry merge @--dozed-patterns)
    (run! (fn [m] ((get m :undoze-callback))) (vals @--dozed-patterns))
    (reset! --dozed-patterns {})))

(defn solo [pat-name & [duration]]
  (let [dozed-state   (dissoc @pattern-registry pat-name)
        inf-instances (filter #(= :inf (:envelope-type (val %))) @pattern-registry)
        inf-instances (when-not (empty? inf-instances)
                        (apply merge inf-instances))]
    (reset! --dozed-patterns dozed-state)
    (reset! pattern-registry  (merge {pat-name (get @pattern-registry pat-name)} inf-instances))
    (when (and (number? duration) (< 0 duration))
      (link/at (+ (Math/ceil (link/get-beat)) duration)
               #(unsolo)))))

(defn solo! [pat-name & [duration]]
  (let [soloed-map    (select-keys @pattern-registry pat-name)
        inf-instances (filter #(= :inf (:envelope-type (val %))) @pattern-registry)
        inf-instances (when-not (empty? inf-instances)
                        (apply merge inf-instances))]
    (when-not (empty? (dissoc @pattern-registry pat-name))
      (reset! pattern-registry (merge soloed-map inf-instances)))))
