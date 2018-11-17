(ns panaeolus.overtone.pattern-control
  (:require [panaeolus.event-loop :refer [event-loop]]
            [panaeolus.control :as control]
            [panaeolus.sequence-parser :refer [sequence-parser]]
            [clojure.string :as string]))

(defn fill-missing-keys-for-ctl
  "Function that makes sure that calling inst
   and calling ctl is possible with exact same
   parameters producing same result."
  [args orig-arglists]
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
                 (conj out-args (first orig) (first args))))))))

(defn squeeze-in-minilang-pattern [args orig-arglists]
  (let [{:keys [time nn]} (sequence-parser (second args))
        args              (vec args)]
    (doall
     (concat (list (first args) (vec time) (vec nn))
             (if (some #(= :dur %) orig-arglists)
               [:dur (vec time)]
               '())
             (subvec args 2)))))

(defn pattern-control [i-name envelope-type orig-arglists instrument-instance]
  ;; (prn i-name envelope-type orig-arglists instrument-instance)
  (fn [& args]
    (let [args (if (string? (second args))
                 (squeeze-in-minilang-pattern args orig-arglists)
                 args)
          [pat-ctl pat-num]
          (if-not (keyword? (first args))
            [nil nil]
            (let [ctl     (name (first args))
                  pat-num (or (re-find #"[0-9]+" ctl) 0)
                  ctl-k   (keyword (first (string/split ctl #"-")))]
              [ctl-k pat-num]))
          args (case envelope-type
                 :inf   (fill-missing-keys-for-ctl args orig-arglists)
                 :gated (fill-missing-keys-for-ctl args orig-arglists)
                 args)]
      ;; (prn "ORIG: " orig-arglists)
      (case pat-ctl
        :loop (do
                (control/unsolo)
                (event-loop (str i-name "-" pat-num)
                            instrument-instance
                            args
                            :envelope-type envelope-type
                            :audio-backend :overtone))
        :stop (control/overtone-pattern-kill (str i-name "-" pat-num))
        :solo (do (control/solo! (str i-name "-" 0))
                  (event-loop (str i-name "-" pat-num)
                              instrument-instance
                              args
                              :envelope-type envelope-type
                              :audio-backend :overtone))
        ;; :solo (control/solo (str i-name "-" 0) (if (empty? pat-num) 0 (read-string pat-num)))
        :kill (control/overtone-pattern-kill (str i-name "-" pat-num))
        (apply instrument-instance (rest (rest args))))
      pat-ctl)))
