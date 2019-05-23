(ns panaeolus.all
  (:gen-class)
  (:require
   nrepl.server
   panaeolus.csound.csound-jna
   panaeolus.metronome
   panaeolus.chords
   panaeolus.csound.macros
   panaeolus.csound.csound-jna
   rebel-readline.clojure.main)
  (:import [clojure.lang Var]
           [org.objectweb.asm Type]))

(set! *warn-on-reflection* true)

(def ignoreme Type)

(defn require-and-immigrate
  "Create a public var in this namespace for each public var in the
  namespaces named by ns-names. The created vars have the same name, value
  and metadata as the original except that their :ns metadata value is this
  namespace."
  [& ns-names]
  (doseq [ns ns-names]
    (do (require ns)
        (doseq [[sym var] (ns-publics ns)]
          (let [sym (with-meta sym (assoc (meta var) :orig-ns ns))]
            (if (.isBound ^Var var)
              (intern *ns* sym (if (fn? (var-get var))
                                 var
                                 (var-get var)))
              (intern *ns* sym)))))))

(require-and-immigrate
 'panaeolus.csound.macros
 'panaeolus.metronome
 'panaeolus.chords
 'panaeolus.pitches
 ;; built-in fx
 'panaeolus.csound.fx.binauralize
 'panaeolus.csound.fx.exciter
 'panaeolus.csound.fx.flanger
 'panaeolus.csound.fx.reverbsc
 'panaeolus.csound.fx.shred
 ;; built-in instruments
 'panaeolus.csound.instruments.metallic-casio
 'panaeolus.csound.instruments.fmpluck
 'panaeolus.csound.instruments.pluck
 'panaeolus.csound.instruments.priest
 'panaeolus.csound.instruments.taffy
 'panaeolus.csound.instruments.wobble
 )

(defn -main [& args]
  (if (or *compile-files* (System/getenv "COMPILING_PANAEOLUS"))
    (System/exit 0)
    (do (nrepl.server/start-server :bind "127.0.0.1" :port 7888)
        (apply rebel-readline.clojure.main/-main args))))
