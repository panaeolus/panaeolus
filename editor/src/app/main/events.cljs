(ns app.main.events
  (:require [app.main.globals :as globals]
            ["electron" :refer [app ipcMain]]))

(defn safe-jre-kill []
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
  (.on ipcMain "poll-logs" (fn [^js event _]
                             (when-not (empty? @globals/log-queue)
                               (.reply event "logs-from-backend" (clj->js @globals/log-queue))
                               (reset! globals/log-queue []))))
  (.on ipcMain "quit" (fn [_ _]
                        (safe-jre-kill)
                        (.quit app))))
