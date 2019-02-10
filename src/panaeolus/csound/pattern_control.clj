(ns panaeolus.csound.pattern-control
  (:require [panaeolus.event-loop :refer [event-loop]]
            [panaeolus.control :as control]
            [panaeolus.sequence-parser :refer [sequence-parser]]
            [panaeolus.csound.csound-jna :refer [csound-instances]]
            [clojure.string :as string]
            [clojure.core.async :refer [<! >! timeout go go-loop chan put! poll!] :as async]))


(defn fill-missing-keys
  "The keywords need to be squeezed in, along
   with calling `panaeolus.csound.utils/process-arguments`
   to resolve the arguments correctly."
  [args orig-arglists]
  (let [orig-arglists (if (some #(= :dur %) args)
                        orig-arglists (rest orig-arglists))]
    (letfn [(advance-to-arg [arg orig]
              (let [idx (.indexOf orig arg)]
                (if (neg? idx)
                  orig
                  (vec (subvec orig (inc idx))))))]
      (loop [args     args
             orig     orig-arglists
             out-args []]
        (if (or (empty? args)
                ;; ignore tangling keyword
                (and (= 1 (count args)) (keyword? (first args))))
          out-args
          (if (keyword? (first args))
            (recur (rest (rest args))
                   ;; (rest orig)
                   (advance-to-arg (first args) orig)
                   (conj out-args (first args) (second args)))
            (recur (rest args)
                   (vec (rest orig))
                   (conj out-args (first orig) (first args)))))))))

(defn squeeze-in-minilang-pattern [args orig-arglists]
  (let [{:keys [time nn]} (sequence-parser (second args))
        args              (vec args)]
    (doall
     (concat (list (first args))
             (list (vec time) (vec nn))
             (into [] (subvec args 2))))))

(defn csound-pattern-stop [k-name]
  (swap! control/pattern-registry dissoc k-name))

#_(defn csound-pattern-kill [k-name]
    (let [csound-instance ]
      (prn "k-name" k-name)
      (swap! control/pattern-registry dissoc k-name)))

#_(defn csound-pattern-kill [k-name]
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
          (get csound-instances )
          (swap! pattern-registry dissoc k-name))))


(defn csound-pattern-control [i-name envelope-type orig-arglists instrument-instance]
  (fn [& args]
    (let [args (if (string? (second args))
                 (squeeze-in-minilang-pattern args orig-arglists)
                 args)
          args (fill-missing-keys args orig-arglists)
          [pat-ctl pat-num]
          (if-not (keyword? (first args))
            [nil nil]
            (let [ctl     (name (first args))
                  pat-num (or (re-find #"[0-9]+" ctl) 0)
                  ctl-k   (keyword (first (string/split ctl #"-")))]
              [ctl-k pat-num]))]
      (case pat-ctl
        :loop (do
                (control/unsolo)
                (event-loop (str i-name "-" pat-num)
                            instrument-instance
                            args
                            :envelope-type :perc
                            :audio-backend :csound
                            :csound-instance-name i-name))
        :stop (csound-pattern-stop (str i-name "-" pat-num))
        :solo nil #_(do (control/solo! (str i-name "-" 0))
                        (event-loop (str i-name "-" pat-num)
                                    instrument-instance
                                    args
                                    :envelope-type :perc
                                    :audio-backend :csound
                                    :csound-instance-name i-name))
        ;; :solo (control/solo (str i-name "-" 0) (if (empty? pat-num) 0 (read-string pat-num)))
        :kill (csound-pattern-stop (str i-name "-" pat-num))
        (apply instrument-instance (rest (rest args))))
      pat-ctl)))
