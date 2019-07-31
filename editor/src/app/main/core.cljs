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

(def splash-window (atom nil))

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

(defn boot-clojure-dev []
  (child-process/spawn
   "clojure"
   (clj->js ["-m" "panaeolus.all" "nrepl" (str globals/nrepl-port)])
   (clj->js {:cwd (path/dirname (path/dirname js/__dirname))})))

(def collecting-selection? (atom false))

(def startup-phase (atom :electron))

(defn process-stdout [resolve]
  (fn [data]
    (let [data (.toString data)
          lines (string/split-lines data)]
      (js/console.log data)
      (doseq [line lines]
        ;; windows accumilate devices for device selection
        (when @startup-phase
          (when-let [splash-screen ^js @splash-window]
            (case @startup-phase
              :electron (.send (.-webContents splash-screen) "update" "Booting Java Runtime..."))))
        (cond @collecting-selection?
              (do (when (.startsWith "Choose" line)
                    (reset! collecting-selection? false)
                    (.send (.-webContents ^js @splash-window) "select" "done"))
                  (when-let [match (re-find #"\s>\s[0-9]+\s(.*)$" line)]
                    (.send (.-webContents ^js @splash-window) "select" (second match))))
              ;; windows promte device selection
              (.startsWith line "[pae:jack:choose-interface]")
              (when-let [splash-screen ^js @splash-window]
                (.send (.-webContents splash-screen) "select" "init")
                (reset! collecting-selection? true))
              ;; Jack not running
              (.startsWith data "[pae:jack:not-running]")
              (when-let [splash-screen ^js @splash-window]
                (if windows?
                  (.send (.-webContents splash-screen) "select" "init")
                  (.send (.-webContents splash-screen) "update" "Jack isn't running, please start a jack server!")))
              ;; Nrepl connection established
              (.startsWith data (str "[nrepl:" globals/nrepl-port "]"))
              (do (reset! startup-phase nil)
			      (.removeAllListeners ^js @splash-window "window-all-closed")
                  (js/setTimeout #(do (.end (.-stdin ^js @globals/jre-connection))
                                      (resolve #js ["started" globals/nrepl-port])) 100))
              :default (swap! globals/log-queue conj data))))))

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
                           windows? (path/join panaeolus-cache-dir "csound-6.13")
                           :default (path/join panaeolus-cache-dir
                                               "csound-6.13" "csound"
                                               "plugins64-6.0"))}}
          jre-conn (if (and js/goog.DEBUG (= (.-platform js/process) "linux"))
                     (boot-clojure-dev)
                     (if system-wide?
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
                                  process-options)))]
      (exit-hook events/safe-jre-kill)
      (.on (.-stdout jre-conn) "data" (process-stdout resolve))
      (.on (.-stderr jre-conn) "data"
           (fn [data]
             (let [data (.toString data)]
               (println data)
               (swap! globals/log-queue conj data))))
      (reset! globals/jre-connection jre-conn)
      (.on jre-conn "close" #(reset! globals/jre-connection nil)))))

(defn java-exists? []
  (if darwin?
    (if (and (fs/existsSync "/usr/bin/java")
             (fs/existsSync "/Library/Java/JavaVirtualMachines"))
      true false)
    ((.-sync command-exists) "java")))

(defn boot-jre-promise []
  (new js/Promise
       (fn [resolve reject]
         (if (java-exists?)
	   (boot-jre! true resolve reject)
           (do (jre/setJreDir (path/join panaeolus-cache-dir jre/jreDirName))
               (if (fs/existsSync (path/join panaeolus-cache-dir jre/jreDirName "java"))
                 (boot-jre! false resolve reject)
                 (jre/install (fn [result] (boot-jre! false resolve reject)))))))))

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
	(.on (.-main splash) "window-all-closed"
	  #(when-not darwin?
          (events/safe-jre-kill)
          (.quit app)))
    (.on (.-splashScreen splash) "window-all-closed"
	  #(when-not darwin?
          (events/safe-jre-kill)
          (.quit app)))
    (reset! main-window (.-main splash))
	(reset! splash-window (.-splashScreen splash))
    (-> (boot-jre-promise) (.then (fn [res] (.loadURL ^js @main-window index-html-loc))))))

(defn main []
    (events/register-events)
    (.disableHardwareAcceleration app)
    (.on app "ready" init-browser)
	(.on app "before-quit" (fn [] (.removeAllListeners ^js @main-window "close")
	                               (events/safe-jre-kill)
                                  (.close @main-window)))
    (.on app "window-all-closed"
         #(when-not darwin?
            (events/safe-jre-kill)
            (.quit app))))
