(ns app.renderer.contextmenu
  (:require [app.renderer.globals :refer [app-state log-atom]]))

(def electron (js/require "electron"))

(defn contextmenu [evt]
  (let [remote (.-remote electron)
        menu (new (.-Menu (.-remote electron)))
        MenuItem (.-MenuItem (.-remote electron))]
    (.append menu (new MenuItem
                       (clj->js {:label "Edit"
                                 :submenu [{:label "Undo"
                                            :accelerator "CmdOrCtrl+Z"
                                            :selector "undo:"}
                                           {:label "Reado"
                                            :accelerator "Shift+CmdOrCtrl+Z"
                                            :selector "redo:"}
                                           {:label "Cut"
                                            :accelerator "CmdOrCtrl+X"
                                            :selector "cut:"}
                                           {:label "Copy"
                                            :accelerator "CmdOrCtrl+C"
                                            :selector "copy:"}
                                           {:label "Paste"
                                            :accelerator "CmdOrCtrl+V"
                                            :selector "paste:"}]})))
    (.append menu (new MenuItem (clj->js {:label "kbd mode"
                                          :submenu [{:label "default"}
                                                    {:label "emacs"
                                                     :click (fn [] (when-let [ace-ref (:ace-ref @app-state)]
                                                                     (.setKeyboardHandler ace-ref "ace/keyboard/emacs")))}
                                                    {:label "vim"}]})))
    (when js/goog.DEBUG
      (.append menu (new MenuItem (clj->js {:label "DevTools"
                                            :click #(.toggleDevTools (.getCurrentWebContents (.-remote electron)))}))))
    (.popup ^js menu #js {:window (.getCurrentWindow remote)})))
