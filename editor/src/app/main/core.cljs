(ns app.main.core
  (:require ["electron" :refer [app BrowserWindow crashReporter ipcMain Menu]]
            ["async-exit-hook" :as exit-hook]
            ["node-jre" :as jre]
            ["path" :as path]
            ;; packagin-debug
            ["uri-js"]
            ))


(def nrepl-port (+ 1025 (rand-int (- 65535 1025))))

(def main-window (atom nil))
(def jre-connection (atom nil))
(def nrepl-connection (atom nil))

(.setApplicationMenu Menu nil)

(defn init-browser []
  (reset! main-window (BrowserWindow.
                       (clj->js {:width 800
                                 :height 600
                                 :webPreferences {:nodeIntegration true}
                                 :icon (str "file://" js/__dirname  "/public/icons/AppIcon.icns")
                                 })))
  (.loadURL ^js @main-window (str "file://" js/__dirname "/public/index.html"))
  (.on ^js @main-window "closed" #(reset! main-window nil)))

(defn boot-jre! [resolve reject]
  (when-not @jre-connection
    (let [jre-conn (jre/spawn #js ["java" "-Xms1g" "-Xmx2g"
                                   "-XX:+CMSConcurrentMTEnabled"
                                   "-XX:MaxGCPauseMillis=20"
                                   "-XX:MaxNewSize=257m"
                                   "-XX:NewSize=256m"
                                   "-XX:+UseTLAB"
                                   "-XX:MaxTenuringThreshold=0"]
                              "-jar" #js [(path/join js/__dirname "panaeolus.jar") "nrepl" (str nrepl-port)])]
      (exit-hook (fn [] (.pause (.-stdin jre-conn)) (.kill jre-conn)))
      (.on (.-stdout jre-conn) "data"
           (fn [data] (let [data (.toString data)]
                        (if (= (str "[nrepl:" nrepl-port "]\n") data)
                          (js/setTimeout #(resolve #js ["started" nrepl-port]) 100)
                          (print data)))))
      (.on (.-stderr jre-conn) "data" (fn [data] (println "error: " (.toString data))))
      (reset! jre-connection jre-conn)
      (.on jre-conn "close" #(do (reset! jre-connection nil) (println "JRE closed!"))))))

(defn main []
  (.on ipcMain "boot-jre"
       (fn [event arg]
         (-> (new js/Promise (fn [resolve reject] (boot-jre! resolve reject)))
             (.then (fn [data] (.reply ^js event "nrepl" data))))))
  (.on ipcMain "dev-reload" (fn [^js event arg] (prn "dev Reload")
                              (.reply event "nrepl" #js ["started" nrepl-port])))
  (.on ipcMain "quit" (fn [_ _] (.quit app)))
  (.disableHardwareAcceleration app)
  (.on app "ready" init-browser)
  (.on app "window-all-closed" #(when-not (= js/process.platform "darwin") (prn "quit2") (.quit app))))



#_(defn boot-nrepl! []
    (let [nrepl (nrepl-client/connect #js {:port nrepl-port})]
      (.once "connect" nrepl #(reset! nrepl-connection nrepl))))

#_(.on ipcMain "eval"
       (fn [event id-code]
         (let [id (aget id-code 0)
               code (aget id-code 1)]
           (when @nrepl-connection
             (.eval @nrepl-connection
                    (str (clojure.string/escape code {"\\" "\\\\"}) "\n")
                    (fn [res err]
                      (prn "RESPONSE" res "error" err)
                      (.send ipcMain "response" {:stdout res :stderr err :id id})))
             #_(.write (.-stdin @jre-connection)
                       (str  (clojure.string/escape arg {"\\" "\\\\"})  "\n"))))))

#_(.start crashReporter
          (clj->js
           {:companyName "MyAwesomeCompany"
            :productName "MyAwesomeApp"
            :submitURL "https://example.com/submit-url"
            :autoSubmit false}))

#_(.on ipcMain "eval"
       (fn [event id arg]
         (when @jre-connection
           (.write (.-stdin @jre-connection)
                   (str (clojure.string/escape arg {"\\" "\\\\"}) "\n")))))
