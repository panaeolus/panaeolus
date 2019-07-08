(ns app.renderer.config)

(def electron (js/require "electron"))

(defn get-config []
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
