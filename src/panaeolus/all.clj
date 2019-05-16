(ns panaeolus.all
  (:gen-class)
  ;; (:use overtone.live)
  (:require
   ;; core imports
   panaeolus.csound.csound-jna
   panaeolus.metronome
   panaeolus.chords
   panaeolus.csound.macros
   panaeolus.csound.csound-jna
   ;; built-in fx
   panaeolus.csound.fx.binauralize
   panaeolus.csound.fx.flanger
   panaeolus.csound.fx.reverbsc
   panaeolus.csound.fx.shred
   ;; built-in instruments
   panaeolus.csound.instruments.atmo
   panaeolus.csound.instruments.wobble

   ))


;; (use 'overtone.live)

(defn immigrate
  "Create a public var in this namespace for each public var in the
  namespaces named by ns-names. The created vars have the same name, value
  and metadata as the original except that their :ns metadata value is this
  namespace."
  [& ns-names]
  (doseq [ns ns-names]
    (doseq [[sym var] (ns-publics ns)]
      (let [sym (with-meta sym (assoc (meta var) :orig-ns ns))]
        (if (.isBound var)
          (intern *ns* sym (if (fn? (var-get var))
                             var
                             (var-get var)))
          (intern *ns* sym))))))

(immigrate ;;'panaeolus.overtone.macros
 'panaeolus.metronome
 'panaeolus.chords
 ;; 'panaeolus.control
 ;; 'panaeolus.overtone.examples.synths
 ;; 'panaeolus.overtone.examples.fof
 'panaeolus.csound.examples.synths
 'panaeolus.csound.examples.fx
 )
