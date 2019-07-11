(ns app.renderer.keybindings
  (:require [app.renderer.editor :as editor]
            [app.renderer.globals :refer [app-state]]
            [reagent.core :as reagent :refer [atom]]
            ["/js/applyPareditChanges" :refer (applyPareditChanges
                                               clojureSexpMovement
                                               newlineAndIndent
                                               pareditIndent)]
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
    :bindKey "ctrl+enter|Ctrl+Alt+x"
    :exec editor/evaluate-outer-sexp
    :readOnly true}
   {:name "devtools"
    :bindKey "F12"
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
       :parent-sexps (.containingSexpsAt (.-walk paredit-js) ast pos-index
                                         (.-hasChildren (.-walk paredit-js)))})))

(defn paredit-delete [backward?]
  (fn [^js editor ^js args]
    #_(js/console.log  (-> editor
                           (.-keyBinding)
                           (.-$defaultHandler)
                           (.-commandKeyBinding)))
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

(defn paredit-open-list [open close]
  (fn [^js editor ^js args]
    (let [data (prepare-paredit editor args)]
      (if-not data
        (.insert editor open)
        (do (when (not= (:sel-end data) (:sel-start data))
              (set! (.-endIdx args) (:sel-end data)))
            (set! (.-open args) open)
            (set! (.-close args) close)
            (when-let [result (.openList paredit-js/editor
                                         (:ast data)
                                         (:soure data)
                                         (if (.-endIdx args) (:sel-start data) (:pos data))
                                         args)]
              (applyPareditChanges editor (.-changes result) (.-newIndex result) true)))))))

(defn paredit-close-list [open close]
  (fn [^js editor ^js args]
    (let [data (prepare-paredit editor args)]
      (set! (.-open args) open)
      (set! (.-close args) close)
      (when (or (not (:ast data))
                js/paredit.freeEdits
                (not (empty? (:errors (:ast data))))
                (not (clojureSexpMovement editor "closeList" args)))
        (applyPareditChanges editor #js [ #js ["insert" (:pos data) (.-close args)]]
                             (+ (:pos data) (.-length (.-close args))))))))

(defn paredit-splice-sexp [^js editor ^js args]
  (when-let [data (prepare-paredit editor args)]
    (when-let [result (.spliceSexp
                       paredit-js/editor
                       (:ast data)
                       (:source data)
                       (:pos data)
                       args)]
      (applyPareditChanges editor (.-changes result) (.-newIndex result) false))))

(defn paredit-splice-sexp-kill [backward?]
  (fn [^js editor ^js args]
    (when-let [data (prepare-paredit editor args)]
      (set! (.-backward args) backward?)
      (when-let [result (.spliceSexpKill
                         paredit-js/editor
                         (:ast data)
                         (:source data)
                         (:pos data)
                         args)]
        (applyPareditChanges editor (.-changes result) (.-newIndex result) false)))))

(defn paredit-forward-sexp [^js editor ^js args]
  ;; (js/console.log "FORWARD")
  (clojureSexpMovement editor "forwardSexp" args))

(defn paredit-backward-sexp [^js editor ^js args]
  ;; (js/console.log "BACKWARD")
  (clojureSexpMovement editor "backwardSexp" args))

(defn paredit-forward-down-sexp [^js editor ^js args]
  (clojureSexpMovement editor "forwardDownSexp" args))

(defn paredit-backward-up-sexp [^js editor ^js args]
  (clojureSexpMovement editor "backwardUpSexp" args))

(def paredit-mode
  [
   ;; {:name "paredit-forward-sexp"
   ;;  :bindKey "Ctrl+Right"
   ;;  :readOnly true
   ;;  :exec paredit-forward-sexp}
   ;; {:name "gotolineend"
   ;;  :bindKey "End"
   ;;  :readOnly true
   ;;  :exec paredit-forward-sexp}
   ;; {:name "gotowordleft"
   ;;  :bindKey "Ctrl+Left"
   ;;  :readOnly true
   ;;  :exec paredit-backward-sexp}
   ;; {:name "gotolinestart"
   ;;  :bindKey "Home"
   ;;  :readOnly true
   ;;  :exec paredit-backward-sexp}
   ;; {:name "scrolldown"
   ;;  :bindKey "Ctrl+Down"
   ;;  :readOnly true
   ;;  :exec paredit-forward-down-sexp}
   ;; {:name "gotopagedown"
   ;;  :bindKey "PageDown"
   ;;  :readOnly true
   ;;  :exec paredit-forward-down-sexp}
   ;; {:name "scrollup"
   ;;  :bindKey "Ctrl+Up"
   ;;  :readOnly true
   ;;  :exec paredit-backward-up-sexp}
   ;; {:name "gotopageup"
   ;;  :bindKey "PageUp"
   ;;  :readOnly true
   ;;  :exec paredit-backward-up-sexp}
   {:name "paredit-splice-sexp"
    :bindKey "Alt+s|Alt+Shift+S"
    :exec paredit-splice-sexp}
   {:name "paredit-splice-sexp-kill-backwards"
    :bindKey "Alt+Up|Alt+Shift+Up"
    :exec (paredit-splice-sexp-kill true)}
   {:name "paredit-splice-sexp-kill-forwards"
    :bindKey "Alt+Down|Alt+Shift+Down"
    :exec (paredit-splice-sexp-kill false)}
   {:name "indent"
    :bindKey "Tab"
    :exec pareditIndent}
   {:name "Enter"
    :bindKey "Enter"
    :exec newlineAndIndent}
   {:name "backspace"
    :bindKey "backspace"
    :exec (paredit-delete true)}
   {:name "del"
    :bindKey {:win "Ctrl+d"
              :mac "Ctrl+d"}
    :exec (paredit-delete false)}
   {:name "("
    :bindKey "("
    :exec (paredit-open-list "(" ")")}
   {:name "["
    :bindKey "["
    :exec (paredit-open-list "[" "]")}
   {:name "{"
    :bindKey "{"
    :exec (paredit-open-list "{" "}")}
   {:name "\""
    :bindKey "\""
    :exec (paredit-open-list "\"" "\"")}
   {:name ")"
    :bindKey ")"
    :exec (paredit-close-list "(" ")")}
   {:name "]"
    :bindKey "]"
    :exec (paredit-close-list "[" "]")}
   {:name "}"
    :bindKey "}"
    :exec (paredit-close-list "{" "}")}])

;; (def paredit-lite-mode)

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

(defn get-commands []
  (cond-> global-commands
    js/goog.DEBUG
    (into dev-commands)
    (get-in @app-state [:config :editor :paredit])
    (into paredit-mode)))

#_(defn bind-keys! [^js ace-editor ^js ace-ref]
    (.addCommands (.-commands ace-ref)
                  (clj->js all-commands)))
