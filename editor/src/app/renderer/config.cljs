(ns app.renderer.config)

(def electron (js/require "electron"))

(defn get-config []
  (.send (.-ipcRenderer electron) "config:get"))
