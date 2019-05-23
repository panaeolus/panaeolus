(ns app.main.core
  (:require ["electron" :refer [app BrowserWindow crashReporter ipcMain ]]
            ["node-jre" :as jre]
            ["path" :as path]))

(def main-window (atom nil))
(def jre-connection (atom nil))

(defn init-browser []
  (reset! main-window (BrowserWindow.
                       (clj->js {:width 800
                                 :height 600
                                 :webPreferences {:nodeIntegration true}
                                 })))
  (.loadURL @main-window (str "file://" js/__dirname "/public/index.html"))
  (.on @main-window "closed" #(reset! main-window nil)))

(defn boot-jre! []
  (let [jre-conn (jre/spawn #js ["java"] "-jar" #js [(path/join js/__dirname "panaeolus-0.4.0-SNAPSHOT.jar") "stdin"])]
    (.on (.-stdout jre-conn) "data" (fn [data] (println "stdout: " (.toString data))))
    (.on (.-stderr jre-conn) "data" (fn [data] (println "error: " (.toString data))))
    (reset! jre-connection jre-conn)
    (.on jre-conn "close" #(do (reset! jre-connection nil) (println "JRE closed!")))
    (.write (.-stdin jre-conn) "(use 'panaeolus.all)(in-ns 'panaeolus.all)\n")))

(defn main []
  (boot-jre!)
  (.start crashReporter
          (clj->js
           {:companyName "MyAwesomeCompany"
            :productName "MyAwesomeApp"
            :submitURL "https://example.com/submit-url"
            :autoSubmit false}))
  (.on app "window-all-closed" #(when-not (= js/process.platform "darwin") (.quit app)))
  (.on app "ready" init-browser)
  (.on ipcMain "eval" (fn [event arg]
                        (when @jre-connection
                          (.write (.-stdin @jre-connection) (str (clojure.string/escape arg {"\\" "\\\\"}) "\n")))))  )
