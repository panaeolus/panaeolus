(ns app.renderer.config
  (:require [app.renderer.nrepl :as nrepl]))

(defn get-config []
  (new js/Promise
       (fn [resolve reject]
         (nrepl/nrepl-handler
          "@panaeolus.config/config"
          (str (gensym))
          (fn [res] (js/console.log "RES" res) (prn "RES2" res) (resolve res))))))

#_(def electron (js/require "electron"))

#_(defn get-config []
    (let [promise
          (new js/Promise
               (fn [resolve reject]
                 (.once (.-ipcRenderer electron) "config:get"
                        (fn [^js event resp]
                          (if (= "error" resp)
                            (reject resp)
                            (resolve (js->clj resp :keywordize-keys true)))))))]
      (.send (.-ipcRenderer electron) "config:get")
      promise))
