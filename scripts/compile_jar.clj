(ns compile-jar
  (:require [badigeon.bundle :as bundle]
            [badigeon.classpath :as classpath]
            [badigeon.clean :as clean]
            [badigeon.compile :as compile]
            [badigeon.jar :as jar]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.deps.alpha.reader :as deps-reader]
            [panaeolus.all :as panaeolus]
            [panaeolus.libcsound64 :as libcsound64]
            [overtone.ableton-link :refer [spit-abletonlink-lib!]]
            [clojure.java.shell :refer [sh]])
  (:import [net.lingala.zip4j.core ZipFile]
           [net.lingala.zip4j.exception ZipException]))

(def +version+ "0.4.0-SNAPSHOT")

(def windows?
  (re-find #"[Ww]indows"
           (System/getProperty "os.name")))

(defn unzip-file
  [zip dest]
  (try
    (-> (ZipFile. zip)
        (.extractAll dest))
    (catch ZipException e
      (.printStackTrace e)
      e)))

(def jars
  (clojure.string/split
   (classpath/make-classpath {:aliases []})
   (if windows? #";" #":")))

(def clojure-jar
  (first (filter #(re-find #"clojure-1.10.0" %) jars)))

(def csound-jar
  (first (filter #(re-find #"Csound" %) jars)))

(def clojure-spec-jar
  (first (filter #(re-find #"spec\.alpha" %) jars)))

(def asm-jar
  (first (filter #(re-find #"asm-all" %) jars)))

(def jack-jar
  (first (filter #(re-find #"jnajack" %) jars)))

(def jgit-jar
  (first (filter #(re-find #"jgit" %) jars)))

(def tech-jar
  (first (filter #(re-find #"techascent" %) jars)))

(def commons-jar
  (first (filter #(re-find #"commons-io" %) jars)))

(def nrepl-jar
  (first (filter #(re-find #"nrepl" %) jars)))

(def cljfmt-jar
  (first (filter #(re-find #"cljfmt" %) jars)))

(def resolvers-jars
  (filter #(re-find #"apache" %) jars))

(def plexus-jars
  (filter #(re-find #"plexus" %) jars))

(def google-jars
  (filter #(re-find #"google" %) jars))

(def jcraft-jars
  (filter #(re-find #"jcraft" %) jars))

(def jna-jars
  (if windows?
    (filter #(re-find #"jna\\jna" %) jars)
    (filter #(re-find #"jna/jna" %) jars)))

(def jline-jars
  (filter #(re-find #"jline" %) jars))

(def slf4j-jars
  (filter #(re-find #"slf4j" %) jars))

(defn map-keys
  "Apply f to each key in m"
  [m f]
  (reduce
   (fn [acc [k v]] (assoc acc (f k) v))
   {} m))

(defn- canonicalize-sym [s]
  (if (and (symbol? s) (nil? (namespace s)))
    (as-> (name s) n (symbol n n))
    s))

(defn- canonicalize-all-syms
  [deps-map]
  (walk/postwalk
   #(cond-> % (map? %) (map-keys canonicalize-sym))
   deps-map))

(defn- slurp-deps-edn []
  (if (.exists (io/file "deps.edn"))
    (deps-reader/slurp-deps "deps.edn")
    (-> "deps.edn"
        io/resource
        slurp
        edn/read-string
        canonicalize-all-syms)))

(libcsound64/spit-csound! "native")
(spit-abletonlink-lib! "native")

(defn -main []
  (println "cleaning target...")
  (clean/clean "target" {:allow-outside-target? false})
  (println "compiling namespaces...")
  (compile/compile '[panaeolus.all]
                   {:compile-path "target/classes"
                    :compiler-options {:disable-locals-clearing false
                                       :direct-linking true}
                    :classpath (classpath/make-classpath {:aliases []})})
  (compile/extract-classes-from-dependencies
   {:deps-map (assoc (deps-reader/slurp-deps "deps.edn")
                     :deps '{org.clojure/clojure {:mvn/version "1.10.0"}})
    })

  (unzip-file clojure-jar "target/classes")
  (unzip-file csound-jar "target/classes")
  (unzip-file clojure-spec-jar "target/classes")
  (unzip-file asm-jar "target/classes")
  (unzip-file jack-jar "target/classes")
  (unzip-file jgit-jar "target/classes")
  (unzip-file tech-jar "target/classes")
  (unzip-file commons-jar "target/classes")
  (unzip-file nrepl-jar "target/classes")
  (unzip-file cljfmt-jar "target/classes")
  (run! #(unzip-file % "target/classes") resolvers-jars)
  (run! #(unzip-file % "target/classes") plexus-jars)
  (run! #(unzip-file % "target/classes") google-jars)
  (run! #(unzip-file % "target/classes") jcraft-jars)
  (run! #(unzip-file % "target/classes") jna-jars)
  (run! #(unzip-file % "target/classes") jline-jars)
  (run! #(unzip-file % "target/classes") slf4j-jars)
  (println "making a jar")
  (if windows?
    (sh "cmd" "/c" "rmdir" "/s" "/q" "target\\classes\\META-INF")
    (sh "rm" "-rf" "target/classes/META-INF"))

  (jar/jar 'panaeolus {:mvn/version +version+}
           {:out-path (str "target/panaeolus-" +version+ ".jar")
            :main 'panaeolus.all
            :paths ["src" "target/classes" "native" "windows"]
            :deps '{org.clojure/clojure {:mvn/version "1.10.0"}}
            :mvn/repos '{"clojars" {:url "https://repo.clojars.org/"}
                         "bintray" {:url "http://jcenter.bintray.com"}}
            :allow-all-dependencies? true
            ;; :inclusion-path (partial badigeon.jar/default-inclusion-path "clojure" "clojure")
            })
  (println "Bundle panaeolus..")

  #_(bundle/bundle
     (bundle/make-out-path 'panaeolus/panaeolus +version+)
     {:deps-map (deps-reader/slurp-deps "deps.edn")
      ;; :excluded-libs #{'org.clojure/clojure}
      :allow-unstable-deps? true
      :libs-path "lib"
      })
  (System/exit 0))
