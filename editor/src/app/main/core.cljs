(ns app.main.core
  (:require [app.main.events :as events]
            [app.main.globals :as globals]
            [clojure.string :as string]
            ["electron" :refer [app BrowserWindow crashReporter ipcMain Menu]]
            ["/js/splash_screen" :refer (initDynamicSplashScreen)]
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

(def main-window (atom nil))

(def nrepl-connection (atom nil))

(def windows? (= (.-platform js/process) "win32"))

(def darwin? (= js/process.platform "darwin"))

(def file-prefix
  (if windows?
    "file:/" "file://"))

(def icon-loc
  (if darwin?
    (str file-prefix js/__dirname "/public/icons/AppIcon.icns")
    (str file-prefix js/__dirname "/public/icons/panaeolus.ico")))

(def index-html-loc (str file-prefix js/__dirname "/public/index.html"))

(def splash-html-loc (str file-prefix js/__dirname "/public/splash.html"))

(.setApplicationMenu Menu nil)

(defn boot-jre! [system-wide? resolve reject]
  (when-not @globals/jre-connection
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
                           windows? (path/join (string/replace panaeolus-cache-dir "\\Local\\" "\\Roaming\\") "csound-6.13")
                           :default (path/join panaeolus-cache-dir
                                               "csound-6.13" "csound"
                                               "plugins64-6.0"))}}
          jre-conn (if system-wide?
                     (child-process/spawn
                      "java"
                      (clj->js (into jvm-opts ["-jar"
                                               (path/join js/__dirname "panaeolus.jar")
                                               "nrepl"
                                               (str globals/nrepl-port)]))
                      process-options)
                     (jre/spawn #js [(string/join " " jvm-opts)]
                                "-jar"
                                #js [(path/join js/__dirname "panaeolus.jar") "nrepl"
                                     (str globals/nrepl-port)]
                                process-options))]
      (exit-hook events/safe-jre-kill)
      (.on (.-stdout jre-conn) "data"
           (fn [data] (let [data (.toString data)]
                        (swap! globals/log-queue conj data)
                        (when (or (= (str "[nrepl:" globals/nrepl-port "]\n") data)
                                  (= (str "[nrepl:" globals/nrepl-port "]\r\n") data))
                          (js/setTimeout #(resolve #js ["started" globals/nrepl-port]) 100)))))
      (.on (.-stderr jre-conn) "data"
           (fn [data]
             (swap! globals/log-queue conj (.toString data))))
      (reset! globals/jre-connection jre-conn)
      (.on jre-conn "close" #(reset! globals/jre-connection nil)))))

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
                BrowserWindow
                (clj->js {:windowOpts main-window-opts
                          :templateUrl splash-html-loc
                          :delay 0
                          :show false
                          :splashScreenOpts {:height 400
                                             :width 400
                                             :webPreferences {:nodeIntegration true}
                                             :backgroundColor "black"}}))]
    (reset! main-window (.-main splash))
    (-> (boot-jre-promise) (.then (fn [res] (.loadURL ^js @main-window index-html-loc))))))

(defn main []
  (events/register-events)
  (.disableHardwareAcceleration app)
  (.on app "ready" init-browser)
  (.on app "window-all-closed"
       #(when-not darwin?
          (events/safe-jre-kill)
          (.quit app))))
