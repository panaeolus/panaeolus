(ns app.main.events
  (:require [app.main.globals :as globals]
            [app.main.config :as config]
			["child_process" :as child-process]
            ["electron" :refer [app ipcMain]]))


(defn windows-postinstall-kill []
  (js/setTimeout #(child-process/spawn "cmd.exe" #js ["/c" "taskkill" "/F" "/IM" "PanaeolusEditor.exe"]) 100))

(defn safe-jre-kill []
  (when (= (.-platform js/process) "win32")
   (child-process/spawn "cmd.exe" #js ["/c" "taskkill" "/F" "/IM" "jackd.exe"]))
  (when @globals/jre-connection
    (.pause (.-stdin ^js @globals/jre-connection))
    (.kill ^js @globals/jre-connection)
    (reset! globals/jre-connection nil)))

(defn register-events []
  (.on ipcMain "get-nrepl-port"
       (fn [event arg]
         (.reply ^js event "nrepl" #js ["started" globals/nrepl-port])))
  (.on ipcMain "dev-reload" (fn [^js event arg]
                              (.reply event "nrepl" #js ["started" globals/nrepl-port])))
  (.on ipcMain "jre:stdin" (fn [^js event arg] (.write (.-stdin ^js @globals/jre-connection) (str arg "\n"))))
  (.on ipcMain "config:get" (fn [^js event arg] (.reply event "config:get" (clj->js (config/read-config)))))
  (.on ipcMain "poll-logs" (fn [^js event _]
                             (when-not (empty? @globals/log-queue)
                               (.reply event "logs-from-backend" (clj->js @globals/log-queue))
                               (reset! globals/log-queue []))))
  (.on ipcMain "quit" (fn [_ _]
                        (println "QUIT!")
                        (safe-jre-kill)
                        (.quit app))))
