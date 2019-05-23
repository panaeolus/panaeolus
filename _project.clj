(defproject panaeolus "2.0.0-alpha1"
  :description "Pattern bindings for live-coding in Overtone."
  :url "https://github.com/panaeolus/africanus"

  :license {:name "GNU Affero General Public License v3.0"
            :url  "https://www.gnu.org/licenses/gpl-3.0.en.html"}

  :plugins [[lein-tools-deps "0.4.5"]]

  :native-path "native"

  :lein-tools-deps/config {:config-files [:install :user :project]}

  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]

  :main panaeolus.all

  :aot [panaeolus.all]

  :target-path "target"

  )
