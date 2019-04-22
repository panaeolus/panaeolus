(defproject panaeolus "2.0.0-alpha1"
  :description "Pattern bindings for live-coding in Overtone."
  :url "https://github.com/panaeolus/africanus"

  :license {:name "GNU Affero General Public License v3.0"
            :url  "https://www.gnu.org/licenses/gpl-3.0.en.html"}

  :plugins [[lein-tools-deps "0.4.3"]]

  ;; :resource-paths ["lib/CsoundJNA-1.0-SNAPSHOT.jar" "lib/audioservers-jack-1.2.0-SNAPSHOT.jar"]

  ;; :jvm-opts ^:replace ["-Xms512m" "-Xmx1g"
  ;;                      "-XX:+UseParNewGC"
  ;;                      "-XX:+UseConcMarkSweepGC"
  ;;                      "-XX:+CMSConcurrentMTEnabled"
  ;;                      "-XX:MaxGCPauseMillis=20"
  ;;                      "-XX:MaxNewSize=257m"
  ;;                      "-XX:NewSize=256m"
  ;;                      "-XX:+UseTLAB"
  ;;                      "-XX:MaxTenuringThreshold=0"]

  :native-path "native"

  ;; :source-paths ["src" "resources" "native" "dev"]

  :lein-tools-deps/config {:config-files [:install :user :project]}

  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]

  :main panaeolus.all
  ;; :aot [panaeolus.all]
  :target-path "target"

  )
