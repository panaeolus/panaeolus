(ns app.renderer.core
  (:require [app.renderer.config :as config]
            [app.renderer.contextmenu :refer [contextmenu]]
            [app.renderer.editor]
            [app.renderer.eldoc :as eldoc]
            [app.renderer.globals :refer [app-state log-atom +version+] :as globals]
            [app.renderer.highlighters :as highlighters]
            [app.renderer.keybindings :as keybindings]
            [app.renderer.nrepl :refer [nrepl-handler
                                        nrepl-connect!
                                        nrepl-connection
                                        nrepl-register-receiver]]
            [reagent.core :as reagent :refer [atom]]
            ["react" :refer [createRef]]
            ["react-highlight" :default Highlight]
            ["react-ace" :default AceEditor]
            ["ace-builds" :as ace-editor]
            ["/js/clojureMode"]
            ["brace/ext/language_tools"]
            ["/js/nrepl-client" :as nrepl-client]
            ["react-split-pane" :as SplitPane]
            ["net" :as net]
            [clojure.core.async :as async]
            [clojure.string :as string :refer [split-lines]]))

(enable-console-print!)

(js/require "brace/ext/language_tools")

(def electron (js/require "electron"))

(def process (js/require "process"))

(def cwd (.cwd process))

(defn initialize-namespace []
  (nrepl-handler "(use 'panaeolus.all)(in-ns 'panaeolus.all)\n" (str (gensym))
                 (fn [_] #_(js/setTimeout register-public-symbols 1000))))

(defn nrepl-initialize [port]
  (when-not @nrepl-connection
    (swap! app-state assoc :nrepl-port port)
    (reset! nrepl-connection (new (.-Socket (.require js/window "net"))))
    (nrepl-connect! port)
    (nrepl-register-receiver)
    (initialize-namespace)))

(defn nrepl-status-handler-init []
  (when-not @nrepl-connection
    (.on (.-ipcRenderer electron) "nrepl"
         (fn [event resp]
           (case (aget resp 0)
             "started" (nrepl-initialize (js/parseInt (aget resp 1)))
             nil)))))

(def backend-log
  (.on (.-ipcRenderer electron) "logs-from-backend"
       (fn [_ resp]
         (swap! log-atom into (js->clj resp)))))

(defn powerline []
  (reagent/create-class
   {:render
    (fn [this]
      (let [nrepl-port (:nrepl-port @app-state)]
        [:ul {:className "powerline"}
         [:li {:className "left"}
          [:div {:style {:background-color "inherit"}}
           [:a {:href "#"} (str nrepl-port " ")]
           [:a {:href "#"} (if nrepl-port "*nrepl*" "*disconnected*")]]
          [:div {:className "endsection"}]
          [:div [:a {:href "#"} "Clojure"]]
          [:div {:className "shrinkable"} [:a {:href "#"} (str "Panaeolus " +version+)]]
          [:div {:className "endsection" :style {:background-color "inherit"}}]]
         [:div {:className "center"}
          [:a {:href "#"} " untitled"]]
         [:li {:className "right"}
          [:div {:className "endsection"}]
          [:div [:a {:href "#"}
                 (let [current-row (str (:current-row @app-state))
                       current-col (str (:current-column @app-state))
                       empty-str-row (apply str (repeat (max 0(- 4 (count current-row))) " "))
                       empty-str-col (apply str (repeat (max 0(- 3 (count current-col))) " "))]
                   (str "" empty-str-row current-row ":" empty-str-col current-col))]]
          [:div {:className "endsection"}]
          [:div [:a {:href "#"} (str (get-in @app-state [:config :bpm]) "BPM")]]]]))}))

(defn get-nrepl-port []
  (.send (.-ipcRenderer electron) "get-nrepl-port" nil))

(defn on-edit-handler [event]
  (let [edit-event? (not (.-readOnly (.-command event)))]
    (if edit-event?
      nil nil)
    #_(js/console.log event))
  nil)


#_(defn log-row-renderer [^js env]
    (reagent/as-element
     [:> Highlight {:class-name "clojure"
                    :key (.-key env)
                    :style (.-style env)}
      (nth @log-atom (.-index env))]))

#_(defn logger-component-list [height width]
    [:> List {:rowCount (count @log-atom)
              :id "log-area"
              :scrollToAlignment "start"
              :scrollToIndex (dec (count @log-atom))
              :height (or height 0)
              :width (or width 0)
              :row-height 30
              :row-renderer log-row-renderer}] )

(defn logger-component []
  (reagent/create-class
   {:component-will-update
    (fn [this]
      (let [logger-container (js/document.getElementsByClassName "Pane2")]
        (when-not (zero? (.-length logger-container))
          (let [logger-container (aget logger-container 0)
                scroll-pos (max (or (.-scrollTop logger-container)) (.-clientHeight logger-container))
                scroll-height (or (.-scrollHeight logger-container))]
            ;; only scroll to bottom if the scroll position is at the bottom
            (when (< (- scroll-height scroll-pos) 300)
              (js/setTimeout #(set! (.-scrollTop logger-container)
                                    (.-scrollHeight logger-container)) 1))))))
    :component-did-mount
    (fn [this]
      (let [logger-container (js/document.getElementsByClassName "Pane2")]
        (when-not (zero? (.-length logger-container))
          (let [logger-container (aget logger-container 0)
                scroll-height (.-scrollHeight logger-container)]
            (set! (.-scrollTop logger-container) scroll-height)))))
    :render
    (fn [this]
      (let [_ @log-atom]
        (into [:> Highlight
               {:class-name "clojure"
                :style {:height "100%"}
                :id "log-area"}]
              (map #(str % "\n") @log-atom))))}))

(defn echo-buffer []
  [:div {:id "echo-buffer"}
   [:code (str (:echo-buffer @app-state))]])

(defn set-config
  "apply when ref changes"
  [ace-ref]
  (when ace-ref
    (-> (config/get-config)
        (.then (fn [config]
                 (let [editor-session ^js (.getSession ace-ref)]
                   (.set (.-config js/ace) "basePath" "./ace")
                   (swap! app-state assoc :config config)
                   (.focus ace-ref)
                   (doseq [command (keybindings/get-commands)]
                     (.addCommand (.-commands ace-ref) (clj->js command)))))))))

(defn root-component []
  (let [state-poller (atom nil)]
    (reagent/create-class
     {;;:componentDidUpdate (fn [] (set-config (:ace-ref @app-state)))
      :componentWillUnmount
      (fn []
        (swap! app-state assoc :ace-ref nil :react-ace-ref nil)
        (set! js/window.oncontextmenu nil)
        (when-let [state-poller-val @state-poller]
          (js/clearInterval state-poller-val)
          (reset! state-poller nil)))
      :componentDidMount
      (fn [this]
        (set! js/window.oncontextmenu contextmenu)
        (reset! state-poller (js/setInterval
                              (fn []
                                (highlighters/get-active-instruments)
                                (.send (.-ipcRenderer electron) "poll:state" nil)) 1000))
        (reset! globals/AceRange (.-Range (.acequire js/ace "ace/range")))
        (nrepl-status-handler-init)
        (get-nrepl-port)
        (set-config (:ace-ref @app-state))
        (js/setTimeout highlighters/get-all-instruments 100))
      :render
      (fn []
        [:div {:style {:position "relative" :height "100vh"}}
         [:> SplitPane {:split "horizontal" :min-size "95%" :default-size "80%"}
          [:> AceEditor
           {:ref (fn [^js ref]
                   (when (and ref (.-editor ref))
                     (let [ace-ref (.-editor ref)]
                       (swap! app-state assoc :react-ace-ref ref :ace-ref ace-ref)
                       (set! (.-$blockScrolling ace-ref) ##Inf))))
            :markers (reduce (fn [^js i v] (.push i v) i) #js []
                             (into (:highlighters @app-state)
                                   (vals (:markers @app-state))))
            :mode "clojure"
            :theme "cyberpunk"
            :commands (keybindings/get-commands)
            :keyboardHandler (get-in @app-state [:config :editor :keyboard-handler])
            :value (:editor-value @app-state)
            :on-cursor-change (fn [selection ^js event]
                                (swap! app-state assoc :echo-buffer "")
                                (eldoc/eldoc-calc))
            :on-change (fn [val ^js event]
                         (swap! app-state assoc :editor-value val :highlighters [])
                         (highlighters/highlight val))
            :editor-props {:$blockScrolling ##Inf}
            :set-options {:enableBasicAutocompletion true
                          :enableLiveAutocompletion true
                          :displayIndentGuides false
                          :showPrintMargin false
                          :fontSize 23}}]
          [logger-component]]
         [:div {:style {:position "absolute"
                        :min-height (if (empty? (:echo-buffer @app-state)) "24px" "48px")
                        :bottom 0 :width "100%"
                        :display "flex" :align-items "flex-end" :flex-direction "column"
                        :justify-content "start"}}
          [powerline]
          (when-not (empty? (:echo-buffer @app-state))
            [echo-buffer])]])})))

(defn reload! []
  (.send (.-ipcRenderer electron) "dev-reload"))

(defn start!
  {:dev/autoload false}
  []
  (reagent/render
   [root-component]
   (js/document.getElementById "app-container")))

(start!)
