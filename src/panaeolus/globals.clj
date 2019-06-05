(ns panaeolus.globals
  (:require [expound.alpha :as expound]
            [clojure.spec.alpha :as s]))

(set! *warn-on-reflection* true)

(s/check-asserts true)

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(def pattern-registry (atom {}))

(defmacro playing? [instrument]
  `(or (some  #(= % (:inst (meta (var ~instrument))))
              (keys @pattern-registry)) false))

;; (def a 1)

;; (defn isa [somet]
;;   (prn (eval `(var ~somet))))

;; (isa a)
