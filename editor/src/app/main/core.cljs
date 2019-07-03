(ns app.main.core
  (:require [clojure.string :as string]
            ["electron" :refer [app BrowserWindow crashReporter ipcMain Menu]]
            ["@trodi/electron-splashscreen" :refer (initDynamicSplashScreen)]
            ["async-exit-hook" :as exit-hook]
            ["node-jre" :as jre]
            ["path" :as path]
            ["env-paths" :as env-paths]
            ["command-exists" :as command-exists]
            ["child_process" :as child-process]
            ["fs" :as fs]
            ;; ;; packagin-debug
            ["uri-js" ]
            ))

(def panaeolus-cache-dir (.-cache (env-paths "panaeolus" #js {:suffix ""})))

(jre/setJreDir panaeolus-cache-dir)

(def nrepl-port (+ 1025 (rand-int (- 65535 1025))))

(def main-window (atom nil))
(def jre-connection (atom nil))
(def nrepl-connection (atom nil))
(def log-queue (atom []))

(def windows? (= (.-platform js/process) "win32"))

(def darwin? (= js/process.platform "darwin"))

(def file-prefix
  (if windows?
    "file:/" "file://"))

(def icon-loc (str file-prefix js/__dirname "/public/icons/AppIcon.icns") )

(def index-html-loc (str file-prefix js/__dirname "/public/index.html"))

(def splash-html-loc (str file-prefix js/__dirname "/public/splash.html"))

(.setApplicationMenu Menu nil)

(defn safe-jre-kill []
  (when @jre-connection
    (.pause (.-stdin ^js @jre-connection))
    (.kill ^js @jre-connection)
    (reset! jre-connection nil)))

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
          process-options 
                           #js {:encoding "utf8"
                                 :cwd (str js/__dirname)
                                 :env #js {:OPCODE6DIR64
                                           (cond
                                             darwin? (path/join panaeolus-cache-dir "csound-6.13" "Opcodes64")
											 windows? (path/join js/__dirname "panaeolus" "libcsound64" "windows" "x86_64")
                                             :default (path/join panaeolus-cache-dir
                                                                 "csound-6.13" "csound"
                                                                 "plugins64-6.0"))}}
          jre-conn (if system-wide?
                     (child-process/spawn
                      "java"
                      (clj->js (into jvm-opts ["-jar" (path/join js/__dirname "panaeolus.jar") "nrepl" (str nrepl-port)]))
                      process-options)
                     (jre/spawn #js [(string/join " " jvm-opts)]
                                "-jar"
                                  #js [(path/join js/__dirname "panaeolus.jar") "nrepl" (str nrepl-port)]
                                process-options))]
      (exit-hook safe-jre-kill)
      (.on (.-stdout jre-conn) "data"
           (fn [data] (let [data (.toString data)]
                        (if (or (= (str "[nrepl:" nrepl-port "]\n") data)
                                (= (str "[nrepl:" nrepl-port "]\r\n") data))
                          (js/setTimeout #(resolve #js ["started" nrepl-port]) 100)
                          (print data))
                        (swap! log-queue conj data))))
      (.on (.-stderr jre-conn) "data" (fn [data] (println "error: " (.toString data))))
      (reset! jre-connection jre-conn)
      (.on jre-conn "close" #(reset! jre-connection nil)))))

(defn boot-jre-promise []
  (new js/Promise
       (fn [resolve reject]
         (if-not ((.-sync command-exists) "java")
           (do (jre/setJreDir (path/join panaeolus-cache-dir jre/jreDirName))
               (if (fs/existsSync (path/join panaeolus-cache-dir jre/jreDirName))
                 (boot-jre! false resolve reject)
                 (jre/install (fn [result] (boot-jre! false resolve reject)))))
           (boot-jre! true resolve reject)))))

(defn init-browser []
  (let [main-window-opts (clj->js {:width 800
                                   :height 600
                                   :show false
                                   :webPreferences {:nodeIntegration true}
                                   :icon icon-loc})
        splash (initDynamicSplashScreen
                (clj->js {:windowOpts main-window-opts
                          :templateUrl splash-html-loc
                          :delay 0
                          :splashScreenOpts {:height 500
                                             :width 500
                                             :backgroundColor "white"}}))]
    (reset! main-window (.-main splash))
    (-> (boot-jre-promise) (.then (fn [res] (.loadURL ^js @main-window index-html-loc))))))

(defn main []
  (.on ipcMain "get-nrepl-port"
       (fn [event arg]
         (.reply ^js event "nrepl" #js ["started" nrepl-port])))
  (.on ipcMain "dev-reload" (fn [^js event arg]
                              (.reply event "nrepl" #js ["started" nrepl-port])))
  (.on ipcMain "poll-logs" (fn [^js event _]
                             (when-not (empty? @log-queue)
                               (.reply event "logs-from-backend" (clj->js @log-queue))
                               (reset! log-queue []))))
  (.on ipcMain "quit" (fn [_ _]
                        (try (safe-jre-kill))
                        (.quit app)))
  (.disableHardwareAcceleration app)
  (.on app "ready" init-browser)
  (.on app "window-all-closed"
       #(when-not darwin?
          (safe-jre-kill)
          (.quit app))))
