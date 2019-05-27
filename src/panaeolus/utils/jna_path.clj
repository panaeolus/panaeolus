(ns panaeolus.utils.jna-path
  (:require
   [badigeon.bundle :as bundle]
   [clojure.string :as string]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.tools.deps.alpha.reader :as deps-reader]
   [clojure.walk :as walk]
   [panaeolus.libcsound64 :as libcsound64]))

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

#_(defn- slurp-deps-edn []
    (if (.exists (io/file "deps.edn"))
      (deps-reader/slurp-deps "deps.edn")
      (-> "deps.edn"
          io/resource
          slurp
          edn/read-string
          canonicalize-all-syms)))

;; extract the native dependencies with badigeon
#_(bundle/extract-native-dependencies
   (System/getProperty "user.dir")
   {:deps-map (slurp-deps-edn)
    :allow-unstable-deps? true
    :native-path "native"
    :native-prefixes {'overtone/ableton-link ""}})

(defn- os-name
  "Returns a string representing the current operating system. Useful
   for debugging, etc. Prefer get-os for os-specific logic."
  []
  (System/getProperty "os.name"))

(defn- get-os
  "Return the OS as a keyword. One of :windows :linux :mac"
  []
  (let [os (os-name)]
    (cond
      (re-find #"[Ww]indows" os) :windows
      (re-find #"[Ll]inux" os)   :linux
      (re-find #"[Mm]ac" os)     :mac)))


(defonce ^:private libcsound-cache-path
  (System/setProperty "jna.library.path"
                      (libcsound64/cache-csound!)))

(def ^:private current-jna-path (System/getProperty "jna.library.path"))
(def ^:private ld-library-path (System/getenv "LD_LIBRARY_PATH"))
(def ^:private nixos-lib-path "/run/current-system/sw/lib")

(def ^:private linux-jna-library-path
  (->> [nixos-lib-path ld-library-path current-jna-path]
       (string/join ":")))

(def ^:private mac-jna-library-path
  (->> [ld-library-path current-jna-path]
       (string/join ":")))

(defonce ^:private __SET_JNA_PATH__
  (case (get-os)
    :linux   (System/setProperty "jna.library.path" linux-jna-library-path)
    :mac     (System/setProperty "jna.library.path" mac-jna-library-path)
    :windows (System/setProperty "jna.library.path" current-jna-path)))

;; JNI Path for CsoundJNI

(def ^:private current-jni-path
  (System/getProperty "java.library.path"))

(defonce ^:private __SET_JNI_PATH__
  (case (get-os)
    :linux   (System/setProperty
              "java.library.path"
              (str current-jni-path ":" linux-jna-library-path))
    :mac     (System/setProperty
              "java.library.path"
              (str current-jni-path ":" mac-jna-library-path))
    :windows (System/setProperty
              "java.library.path"
              (str current-jni-path ":" current-jna-path))))
