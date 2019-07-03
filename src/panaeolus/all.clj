(ns panaeolus.all
  (:gen-class)
  (:require
   ;; non-immigrants
   nrepl.server
   panaeolus.csound.csound-jna
   panaeolus.csound.pattern-control
   ;; immigrants
   panaeolus.csound.macros
   panaeolus.metronome
   panaeolus.chords
   panaeolus.pitches
   panaeolus.functions
   ;; built-in fx
   panaeolus.csound.fx.binauralize
   panaeolus.csound.fx.dubflang
   panaeolus.csound.fx.exciter
   panaeolus.csound.fx.flanger
   panaeolus.csound.fx.reverbsc
   panaeolus.csound.fx.shred
   ;; built-in instruments
   panaeolus.csound.instruments.fmpluck
   panaeolus.csound.instruments.hammer
   panaeolus.csound.instruments.metallic-casio
   panaeolus.csound.instruments.pluck
   panaeolus.csound.instruments.priest
   panaeolus.csound.instruments.sruti
   panaeolus.csound.instruments.taffy
   panaeolus.csound.instruments.wobble)
  (:import [clojure.lang Var]
           [org.objectweb.asm Type]))

(def __is_windows__ (re-find #"[Ww]indows" (System/getProperty "os.name")))

(set! *warn-on-reflection* true)

(defn immigrate
  "Create a public var in this namespace for each public var in the
  namespaces named by ns-names. The created vars have the same name, value
  and metadata as the original except that their :ns metadata value is this
  namespace."
  [& ns-names]
  (doseq [ns ns-names]
    (doseq [[sym var] (ns-publics ns)]
      (let [sym (with-meta sym (assoc (meta var) :orig-ns ns))]
        (if (.isBound ^Var var)
          (intern *ns* sym (if (fn? (var-get var))
                             var
                             (var-get var)))
          (intern *ns* sym))))))

(immigrate
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
 'panaeolus.csound.instruments.fmpluck
 'panaeolus.csound.instruments.hammer
 'panaeolus.csound.instruments.metallic-casio
 'panaeolus.csound.instruments.pluck
 'panaeolus.csound.instruments.priest
 'panaeolus.csound.instruments.sruti
 'panaeolus.csound.instruments.taffy
 'panaeolus.csound.instruments.wobble
 )

#_(defmacro require-rebel-readline [args]
    (when-not `__is_windows__
      `(do
         (require 'rebel-readline.clojure.main)
         (apply rebel-readline.clojure.main/-main ~args))))

(defn -main [& args]
  (if (or *compile-files* (System/getenv "COMPILING_PANAEOLUS"))
    (System/exit 0)
    (if (and (not (empty? args)) (= "stdin" (first args)))
      (loop []
        (flush)
        (print (-> (read-line) read-string eval))
        (recur))
      (let [nrepl-server (nrepl.server/start-server :bind "127.0.0.1" :port (Integer/parseInt (or (second args) 4445)))]
        (println (format "[nrepl:%s]" (second args)))
        (.addShutdownHook (Runtime/getRuntime) (Thread. #(nrepl.server/stop-server nrepl-server))))
      #_(if (or __is_windows__ (and (not (empty? args)) (= "nrepl" (first args))))
          (
           #_(loop []
               (flush)
               (print (-> (read-line) read-string eval))
               (recur)))
          (require-rebel-readline args)))))
