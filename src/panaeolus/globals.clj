(ns panaeolus.globals)

(set! *warn-on-reflection* true)

(def pattern-registry (atom {}))

;; (defn playing? [instrument]
;;   (some  #(= % (:inst (meta (var instrument)))) (keys @pattern-registry)))

;; (def a 1)

;; (defn isa [somet]
;;   (prn (eval `(var ~somet))))

;; (isa a)
