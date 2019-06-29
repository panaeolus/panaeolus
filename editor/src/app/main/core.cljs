(ns app.main.core
  (:require [clojure.string :as string]
            ["electron" :refer [app BrowserWindow crashReporter ipcMain Menu]]
            ["async-exit-hook" :as exit-hook]
            ["node-jre" :as jre]
            ["path" :as path]
            ["env-paths" :as env-paths]
            ["command-exists" :as command-exists]
            ["child_process" :as child-process]
            ["fs" :as fs]
            ;; packagin-debug
            ["uri-js"]
            ))

(def panaeolus-cache-dir (.-cache (env-paths "panaeolus" #js {:suffix ""})))

(jre/setJreDir panaeolus-cache-dir)

(def nrepl-port (+ 1025 (rand-int (- 65535 1025))))

(def main-window (atom nil))
(def jre-connection (atom nil))
(def nrepl-connection (atom nil))
(def log-queue (atom []))

(def windows? (= (.-platform js/process) "win32"))

(def file-prefix
  (if windows?
    "file:/" "file://"))

(.setApplicationMenu Menu nil)

(defn init-browser []
  (reset! main-window (BrowserWindow.
                       (clj->js {:width 800
                                 :height 600
                                 :webPreferences {:nodeIntegration true}
                                 :icon (str file-prefix js/__dirname "/public/icons/AppIcon.icns")
                                 })))
  (.loadURL ^js @main-window (str file-prefix js/__dirname "/public/index.html"))
  (.on ^js @main-window "closed" #(reset! main-window nil)))


(defn boot-jre! [system-wide? resolve reject]
  (when-not @jre-connection
    (let [jvm-opts ["-Xms512M"
                    "-Xmx4G"
                    "-XX:+CMSConcurrentMTEnabled"
                    "-XX:MaxGCPauseMillis=20"
                    "-XX:MaxNewSize=257M"
                    "-XX:NewSize=256M"
                    "-XX:+UseTLAB"
                    "-XX:MaxTenuringThreshold=0"
                    ;; "-Djna.debug_load=true"
                    ]
          process-options (if windows?
		                   #js {:encoding "utf8"
						        :env #js {:OPCODE6DIR64 (path/join js/__dirname "panaeolus" "libcsound64" "windows" "x86_64")}
                                :cwd (path/join js/__dirname "panaeolus")
							    :shell true
							    :detached false}
				           #js {:encoding "utf8"
                                :cwd (str js/__dirname)
                                :env #js {:OPCODE6DIR64 (path/join panaeolus-cache-dir "csound-6.13" "Opcodes64")}}
							   )
          jre-conn (if system-wide?
                     (child-process/spawn "java"
                                          (clj->js (into jvm-opts
										                 (if windows?
														   ["panaeolus.all" "nrepl" (str nrepl-port)]
                                                           ["-jar" (path/join js/__dirname "panaeolus.jar") "nrepl" (str nrepl-port)])))
                                          process-options)
                     (do
                       (jre/spawn #js [(string/join " " jvm-opts)]
                                  (if windows? "panaeolus.all" "-jar")
                                  (if windows?
                                    #js ["nrepl" (str nrepl-port)]
				                    #js [(path/join js/__dirname "panaeolus.jar") "nrepl" (str nrepl-port)])
                                  process-options)))]
      (exit-hook (fn [] (.pause (.-stdin jre-conn)) (.kill jre-conn)))
      (.on (.-stdout jre-conn) "data"
           (fn [data] (let [data (.toString data)] (prn "DATA" data)
                        (if (or (= (str "[nrepl:" nrepl-port "]\n") data)
						        (= (str "[nrepl:" nrepl-port "]\r\n") data))
                          (js/setTimeout #(resolve #js ["started" nrepl-port]) 100)
                          (print data))
                        (swap! log-queue conj data))))
      (.on (.-stderr jre-conn) "data" (fn [data] (println "error: " (.toString data))))
      (reset! jre-connection jre-conn)
      (.on jre-conn "close" #(do (reset! jre-connection nil) (println "JRE closed!"))))))

(defn main []
  (.on ipcMain "boot-jre"
       (fn [event arg]
         (-> (new js/Promise
                  (fn [resolve reject]
                    (if-not ((.-sync command-exists)
                             (if (= (.-platform js/process) "darwin")
                               "jdb -version"
                               "java -version"))
                      (do (jre/setJreDir (path/join panaeolus-cache-dir jre/jreDirName))
					    (if (fs/existsSync (path/join panaeolus-cache-dir jre/jreDirName))
                          (boot-jre! false resolve reject)
                         (jre/install (fn [result]
						               (prn "RESULT" result)
                                       (boot-jre! false resolve reject)))))
                      (boot-jre! true resolve reject))))
             (.then (fn [data] (.reply ^js event "nrepl" data))))))
  (.on ipcMain "dev-reload" (fn [^js event arg] (prn "dev Reload")
                              (.reply event "nrepl" #js ["started" nrepl-port])))
  (.on ipcMain "poll-logs" (fn [^js event _]
                             (when-not (empty? @log-queue)
                               (.reply event "logs-from-backend" (clj->js @log-queue))
                               (reset! log-queue []))))
  (.on ipcMain "quit" (fn [_ _] (.quit app)))
  (.disableHardwareAcceleration app)
  (.on app "ready" init-browser)
  (.on app "window-all-closed" #(when-not (= js/process.platform "darwin") (.quit app))))



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
