(ns panaeolus.overtone.event-callback
  (:require panaeolus.utils.jna-path
            [panaeolus.control :as control]
            [panaeolus.live-code-arguments :refer
             [resolve-arg-indicies expand-nested-vectors-to-multiarg]]
            [overtone.sc.node :as sc-node]
            [overtone.studio.inst :as studio-inst]
            [clojure.core.async :refer [<! >! timeout go go-loop chan put! poll!] :as async]
            [overtone.studio.inst :as overtone-studio]
            [overtone.ableton-link :as link])
  (:use overtone.live))

(defn synth-node? [v]
  (= overtone.sc.node.SynthNode (type v)))


(defn overtone-event-callback [wait-chn inst args index a-index next-timestamp envelope-type fx]
  (let [args-processed (resolve-arg-indicies args index a-index next-timestamp)
        fx-ctl-cb      (fn [] (when-not (empty? fx)
                                #_(run! #(apply sc-node/ctl (last %)
                                                (resolve-arg-indicies
                                                 (second %) index a-index
                                                 next-timestamp))
                                        (vals fx))
                                (run! #(try (apply sc-node/ctl (last %)
                                                   (resolve-arg-indicies
                                                    (second %) index a-index
                                                    next-timestamp))
                                            (catch Exception e nil))
                                      (vals fx))))]
    (if (some sequential? args-processed)
      (let [multiargs-processed
            (expand-nested-vectors-to-multiarg args-processed)]
        (fn []
          (go (fx-ctl-cb))
          (when (overtone-studio/instrument? inst)
            (run! #(apply inst %) multiargs-processed))
          (put! wait-chn true)))
      (fn []
        (go (fx-ctl-cb))
        (if (overtone-studio/instrument? inst)
          (apply inst args-processed)
          (apply sc-node/ctl inst
                 (if (= :gated envelope-type)
                   (into args-processed [:gate 1])
                   args-processed)))
        (put! wait-chn true)))))

(defn overtone-fx-callback [k-name instrument-instance rem-fx next-fx curr-fx old-fx new-fx]
  (when (or (not (empty? rem-fx)) (not (empty? next-fx)))
    (fn []
      (when-not (empty? rem-fx)
        ;; (prn "FOUND FROM OLD: " (keys old-fx-at-event))
        (run! #(let [fx-node (last %)
                     stereo? (vector? fx-node)]
                 (if stereo?
                   (when (sc-node/node-active? (first fx-node))
                     (run! (fn [n] (node-free n)) fx-node))
                   (when (sc-node/node-active? (last %)) (node-free (last %)))))
              (vals (select-keys old-fx (vec rem-fx))))
        (swap! control/pattern-registry update-in [k-name :old-fx] #(apply dissoc % (vec rem-fx))))
      (when-not (empty? next-fx)
        (swap! control/pattern-registry assoc k-name
               (assoc (get @control/pattern-registry k-name) :current-fx
                      (reduce (fn [old next]
                                (let [new-v   (get new-fx next)
                                      fx-node (studio-inst/inst-fx! instrument-instance (first new-v))
                                      indx0   (resolve-arg-indicies
                                               (second new-v)
                                               0 0 (link/get-beat))]
                                  (apply ctl fx-node indx0)
                                  (assoc old next (conj new-v fx-node))))
                              (get-in @control/pattern-registry [k-name :current-fx]) next-fx)))))))
