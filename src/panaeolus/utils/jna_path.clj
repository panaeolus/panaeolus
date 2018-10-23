(ns panaeolus.utils.jna-path
  (:require [clojure.string :as string]))


(defn- os-name
  "Returns a string representing the current operating system. Useful
   for debugging, etc. Prefer get-os for os-specific logic."
  []
  (System/getProperty "os.name"))

(defn- get-os
  "Return the OS as a keyword. One of :windows :linux :mac"
  []
  (let [os (os-name)]
    (cond
      (re-find #"[Ww]indows" os) :windows
      (re-find #"[Ll]inux" os)   :linux
      (re-find #"[Mm]ac" os)     :mac)))

(def ^:private current-jna-path (System/getProperty "jna.library.path"))
(def ^:private ld-library-path (System/getenv "LD_LIBRARY_PATH"))
(def ^:private nixos-lib-path "/run/current-system/sw/lib")

(def ^:private linux-jna-library-path
  (->> [nixos-lib-path ld-library-path current-jna-path]
       (string/join ":")))

(def ^:private mac-jna-library-path
  (->> [ld-library-path current-jna-path]
       (string/join ":")))

(defonce ^:private __SET_JNA_PATH__
  (case (get-os)
    :linux   (System/setProperty "jna.library.path" linux-jna-library-path)
    :mac     (System/setProperty "jna.library.path" mac-jna-library-path)
    :windows (System/setProperty "jna.library.path" current-jna-path)))
