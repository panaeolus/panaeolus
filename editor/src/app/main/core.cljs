(ns app.main.core
  (:require ["electron" :refer [app BrowserWindow crashReporter ipcMain ]]
            ["node-jre" :as jre]
            ["path" :as path]))

(def nrepl-port (+ 1025 (rand-int 2049)))

(def main-window (atom nil))
(def jre-connection (atom nil))
(def nrepl-connection (atom nil))

(defn init-browser []
  (reset! main-window (BrowserWindow.
                       (clj->js {:width 800
                                 :height 600
                                 :webPreferences {:nodeIntegration true}
                                 })))
  (.loadURL @main-window (str "file://" js/__dirname "/public/index.html"))
  (.on @main-window "closed" #(reset! main-window nil)))

(defn boot-jre! []
  (let [jre-conn (jre/spawn #js ["java"] "-jar" #js [(path/join js/__dirname "panaeolus-0.4.0-SNAPSHOT.jar") "nrepl" (str nrepl-port)])]
    (.on (.-stdout jre-conn) "data"
         (fn [data] (let [data (.toString data)]
                      (when (re-find #"[nrepl]" data)
                        (prn "GOT THE SIGNAL, sending from ipcMain")
                        (.send ipcMain "nrepl" #js ["started" nrepl-port]))
                      (print data))))
    (.on (.-stderr jre-conn) "data" (fn [data] (println "error: " (.toString data))))
    (reset! jre-connection jre-conn)
    (.on jre-conn "close" #(do (reset! jre-connection nil) (println "JRE closed!")))
    (.write (.-stdin jre-conn) "(use 'panaeolus.all)(in-ns 'panaeolus.all)\n")))

#_(defn boot-nrepl! []
    (let [nrepl (nrepl-client/connect #js {:port nrepl-port})]
      (.once "connect" nrepl #(reset! nrepl-connection nrepl))))

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
  #_(.on ipcMain "eval"
         (fn [event id arg]
           (when @jre-connection
             (.write (.-stdin @jre-connection)
                     (str (clojure.string/escape arg {"\\" "\\\\"}) "\n"))))))
