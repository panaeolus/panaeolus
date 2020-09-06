(ns panaeolus.globals
  (:require [expound.alpha :as expound]
            [panaeolus.config :as config]
            [clojure.core.async :as async]
            [clojure.spec.alpha :as s]))

(set! *warn-on-reflection* true)

(s/check-asserts true)

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(def loaded-instr-symbols (atom {}))

(def pattern-registry (atom {}))

(def active-instr-symbols (atom []))

(defmacro playing? [instrument]
  `(or (some  #(= % (:inst (meta (var ~instrument))))
              (keys @pattern-registry)) false))

#_(async/go-loop []
  (async/<! (async/timeout 5000))
  (when (get-in @config/config [:printer :active-instruments?] nil)
    (println (keys @pattern-registry)))
  (loop [syms (keys @pattern-registry)
         active []]
    (if (empty? syms)
      (reset! active-instr-symbols active)
      (recur (rest syms)
             (conj active (get @loaded-instr-symbols (first syms))))))
  (recur))
