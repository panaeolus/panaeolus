(ns build2
  (:require [badigeon.clean :as clean]
            [badigeon.compile :as compile]
            [badigeon.jar :as jar]
            [badigeon.zip :as zip]
            [badigeon.uberjar :as uberjar]
            [clojure.string :as string]
            [clojure.tools.deps.alpha.reader :as deps-reader]))

(def +version+ "0.4.0-SNAPSHOT")

(defn -main []
  (clean/clean "target")
  (compile/compile '[panaeolus.all clojure.core.specs.alpha]
                   {:compile-path "target/classes"
                    :allow-unstable-deps? true
                    :compiler-options {:disable-locals-clearing true
                                       :direct-linking false}})
  (uberjar/bundle (str "target/panaeolus-" +version+)
                  {:deps-map (-> (deps-reader/slurp-deps "deps.edn")
                                 (assoc :paths ["target/classes" "resources"])
                                 (update :deps dissoc 'badigeon/badigeon))})
  (spit (str "target/panaeolus-" +version+ "/META-INF/MANIFEST.MF")
        (jar/make-manifest 'panaeolus.all))
  (zip/zip (str "target/panaeolus-" +version+) (str "target/panaeolus-" +version+ ".jar"))
  (System/exit 0))
