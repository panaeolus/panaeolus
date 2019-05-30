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
 'panaeolus.functions
 ;; built-in fx
 'panaeolus.csound.fx.binauralize
 'panaeolus.csound.fx.dubflang
 'panaeolus.csound.fx.exciter
 'panaeolus.csound.fx.flanger
 'panaeolus.csound.fx.reverbsc
 'panaeolus.csound.fx.shred
 ;; built-in instruments
 'panaeolus.csound.instruments.metallic-casio
 'panaeolus.csound.instruments.fmpluck
 'panaeolus.csound.instruments.hammer
 'panaeolus.csound.instruments.pluck
 'panaeolus.csound.instruments.priest
 'panaeolus.csound.instruments.sruti
 'panaeolus.csound.instruments.taffy
 'panaeolus.csound.instruments.wobble
 )

(defn -main [& args]
  (if (or *compile-files* (System/getenv "COMPILING_PANAEOLUS"))
    (System/exit 0)
    (if (and (not (empty? args)) (= "stdin" (first args)))
      (loop []
        (flush)
        (print (-> (read-line) read-string eval))
        (recur))
      (if (and (not (empty? args)) (= "nrepl" (first args)))
        (let [nrepl-server (nrepl.server/start-server :bind "127.0.0.1" :port (Integer/parseInt (second args)))]
          (println (format "[nrepl:%s]" (second args)))
          (.addShutdownHook (Runtime/getRuntime) (Thread. #(nrepl.server/stop-server nrepl-server)))
          (loop []
            (flush)
            (print (-> (read-line) read-string eval))
            (recur)))
        (do
          (apply rebel-readline.clojure.main/-main args))))))
