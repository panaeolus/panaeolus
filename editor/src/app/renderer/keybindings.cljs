(ns app.renderer.keybindings
  (:require [app.renderer.editor :as editor]
            [app.renderer.globals :refer [app-state]]
            [reagent.core :as reagent :refer [atom]]
            ["/js/applyPareditChanges" :as applyPareditChanges]
            ["vex-js" :as vex]
            ["paredit.js" :as paredit-js]))

(def electron (js/require "electron"))

(when-not (aget vex "dialog")
  (.registerPlugin vex (js/require "vex-dialog"))
  (set! (.-className (.-defaultOptions vex)) "vex-theme-os")
  (set! (.-text (.-YES (.-buttons (.-dialog vex)))) "Okiedokie")
  (set! (.-text (.-NO (.-buttons (.-dialog vex)))) "Aahw hell no"))

(def global-commands
  [{:name "execute"
    :bindKey {:win "ctrl+enter"
              :mac "ctrl+enter"}
    :exec editor/evaluate-outer-sexp
    :readOnly true}
   {:name "devtools"
    :bindKey {:win "F12"
              :mac "F12"}
    :exec #(.toggleDevTools (.getCurrentWebContents (.-remote electron)))}
   {:name "quit"
    :bindKey {:win "ctrl+q" :mac "Cmd+q"}
    :exec #(.confirm (.-dialog vex)
                     #js {:message (str "You are NOT standing in front of an audience, "
                                        "performing music, and you really mean to quit?")
                          :callback (fn [true?]
                                      (when true?
                                        (.send (.-ipcRenderer electron) "quit" nil)))})}])

(defn prepare-paredit [^js editor ^js args]
  (let [selection-range (.getSelectionRange editor)
        pos-index (.positionToIndex (.-doc (.-session editor)) (.getCursorPosition editor))
        curr-val (.getValue editor)
        ast (paredit-js/parse curr-val)]
    (.pushEmacsMark editor (.getCursorPosition editor))
    (when ast
      {:pos pos-index
       :sel-start (.positionToIndex (.-doc (.-session editor)) (.-start selection-range))
       :sel-end (.positionToIndex (.-doc (.-session editor)) (.-end selection-range))
       :selecting? (when (and (.-$emacsMark (.-session editor))
                              (and args (.-shifted args)))
                     true)
       :ast ast
       :source curr-val
       :parent-sexps (.containingSexpsAt (.-walk paredit-js) ast pos-index (.-hasChildren (.-walk paredit-js)))})))

(defn paredit-delete [backward?]
  (fn [^js editor ^js args]
    (js/console.log "DEL")
    (let [data (prepare-paredit editor args)]
      (when data
        (let [end-index (if
                            (:sel-end data)
                          (.-endIdx args))]
          (when (not= (:sel-end data) (:sel-start data))
            (set! (.-endIdx args) (:sel-end data)))
          (set! (.-backward args) backward?)
          (when-let [result (.delete paredit-js/editor
                                     (:ast data)
                                     (:soure data)
                                     (if (.-endIdx args) (:sel-start data) (:pos data))
                                     args)]
            (applyPareditChanges editor (.-changes result) (.-newIndex result) false)))))))

(def paredit-mode
  [{:name "backspace"
    :bindKey "backspace|F1"
    :exec (paredit-delete true)}
   {:name "del"
    :bindKey {:win "Ctrl+d"
              :mac "Ctrl+d"}
    :exec (paredit-delete false)}])

#_(defn init-paredit-mode [^js editor]
    (let [current-bindings (-> editor
                               (.-keyBinding)
                               (.-$defaultHandler)
                               (.-commandKeyBinding))]
      (js/console.log current-bindings)))

(def dev-commands
  [{:name "refresh"
    :bindKey "f5"
    :exec #(.reload js/location)
    :readOnly true}])

;; (def all-commands)

#_(defn bind-keys! [^js ace-editor ^js ace-ref]
    (.addCommands (.-commands ace-ref)
                  (clj->js all-commands)))
