(ns build
  (:require [badigeon.bundle :as bundle]
            [badigeon.classpath :as classpath]
            [badigeon.clean :as clean]
            [badigeon.compile :as compile]
            [badigeon.clean :as clean]
            [badigeon.jar :as jar]
            [clojure.java.io :as io]
            [clojure.tools.deps.alpha.reader :as deps-reader]
            [panaeolus.libcsound64 :as libcsound64]
            [overtone.ableton-link :as ableton-link]))

(def +version+ "0.4.0-SNAPSHOT")

(defn- get-os
  "Return the OS as a keyword. One of :windows :linux :mac"
  []
  (let [os (System/getProperty "os.name")]
    (cond
      (re-find #"[Ww]indows" os) :windows
      (re-find #"[Ll]inux" os)   :linux
      (re-find #"[Mm]ac" os)     :mac)))

(defn extract-jna-natives []
  (case (get-os)
    :windows (bundle/extract-native-dependencies
              "target/classes"
              {:deps-map (deps-reader/slurp-deps "deps.edn")
               :native-prefixes {'net.java.dev.jna/jna "com/sun/jna/win32-x86-64"}
               :native-path "com/sun/jna/win32-x86-64"})
    :linux (bundle/extract-native-dependencies
            "target/classes"
            {:deps-map (deps-reader/slurp-deps "deps.edn")
             :native-prefixes {'net.java.dev.jna/jna "com/sun/jna/linux-x86-64"}
             :native-path "com/sun/jna/linux-x86-64"})
    :mac (bundle/extract-native-dependencies
          "target/classes"
          {:deps-map (deps-reader/slurp-deps "deps.edn")
           :native-prefixes {'net.java.dev.jna/jna "com/sun/jna/darwin"}
           :native-path "com/sun/jna/darwin"})))

(defn extract-jline-resources []
  (doseq [file ["ansi.caps" "dumb.caps" "capabilities.txt"
                "screen.caps" "screen-256color.caps"
                "windows.caps" "xterm.caps" "xterm-256color.caps"]]
    (spit (.getPath (io/file "target/classes/org/jline/utils" file))
          (slurp (io/resource (str "org/jline/utils/" file))))))

(defn -main []
  (clean/clean "target")
  (compile/compile '[panaeolus.all clojure.core.specs.alpha]
                   {:compile-path "target/classes"
                    :compiler-options {:disable-locals-clearing false
                                       :direct-linking true}
                    :classpath (classpath/make-classpath {:aliases []})})
  (compile/extract-classes-from-dependencies
   {:deps-map (deps-reader/slurp-deps "deps.edn")})
  (spit "target/classes/clojure/version.properties"
        (slurp (io/resource "clojure/version.properties")))

  (libcsound64/spit-csound! "target/classes")
  (ableton-link/spit-abletonlink-lib! "target/classes")
  (extract-jna-natives)
  (extract-jline-resources)
  (jar/jar 'panaeolus {:mvn/version +version+}
           {:out-path (str "target/panaeolus-" +version+ ".jar")
            :main 'panaeolus.all
            :paths ["src" "target/classes"]
            :deps '{org.clojure/clojure {:mvn/version "1.10.0"}
                    net.java.dev.jna/jna {:mvn/version "5.3.1"}}
            :mvn/repos '{"central" {:url "https://repo1.maven.org/maven2/"}
                         "clojars" {:url "https://repo.clojars.org/"}
                         "bintray" {:url "http://jcenter.bintray.com"}}
            :allow-all-dependencies? true})
  (System/exit 0))
