(ns panaeolus.globals)

(set! *warn-on-reflection* true)

(def pattern-registry (atom {}))

(defmacro playing? [instrument]
  `(or (some  #(= % (:inst (meta (var ~instrument))))
              (keys @pattern-registry)) false))

;; (def a 1)

;; (defn isa [somet]
;;   (prn (eval `(var ~somet))))

;; (isa a)
