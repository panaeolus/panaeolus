(ns app.main.config
  (:require  ["fs" :as fs]
             ["os" :as os]
             ["path" :as path]
             [cljs.reader :as reader]))

(def panaeolus-config-dir (path/join (.homedir os) ".panaeolus"))

(def panaeolus-config-loc (path/join panaeolus-config-dir "config.edn"))

(defn read-config []
  (when-not (fs/existsSync panaeolus-config-loc)
    (js/mkdirSync panaeolus-config-dir #js {:recursive true})
    (fs/writeFileSync panaeolus-config-loc "{}"))
  (try (reader/read-string
        (.toString (fs/readFileSync panaeolus-config-loc)))
       (catch :default e :error)))
