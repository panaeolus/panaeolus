(ns app.renderer.keybindings
  (:require [app.renderer.editor :as editor]
            [reagent.core :as reagent :refer [atom]]
            ["vex-js" :as vex]))

(def electron (js/require "electron"))

(when-not (aget vex "dialog")
  (.registerPlugin vex (js/require "vex-dialog"))
  (set! (.-className (.-defaultOptions vex)) "vex-theme-os")
  (set! (.-text (.-YES (.-buttons (.-dialog vex)))) "Okiedokie")
  (set! (.-text (.-NO (.-buttons (.-dialog vex)))) "Aahw hell no"))

(def global-commands
  [{:name "execute"
    :bindKey "ctrl+enter"
    :exec editor/evaluate-outer-sexp
    :readOnly true}
   {:name "devtools"
    :bindKey "F12"
    :exec #(.toggleDevTools (.getCurrentWebContents (.-remote electron)))}
   {:name "quit"
    :bindKey "ctrl+q"
    :exec #(.confirm (.-dialog vex)
                     #js {:message (str "You are NOT standing in front of an audience, "
                                        "performing music, and you really mean to quit?")
                          :callback (fn [true?]
                                      (when true?
                                        (.send (.-ipcRenderer electron) "quit" nil)))})}])

(def dev-commands
  [{:name "refresh"
    :bindKey "f5"
    :exec #(.reload js/location)
    :readOnly true}])

(def all-commands
  (if js/goog.DEBUG
    (into global-commands dev-commands)
    global-commands))

(defn bind-keys! [^js ace-editor ^js ace-ref]
  (.addCommands (.-commands ace-ref)
                (clj->js all-commands)))
