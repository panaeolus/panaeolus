(ns app.renderer.core
  (:require [app.renderer.config]
            [app.renderer.contextmenu :refer [contextmenu]]
            [app.renderer.editor]
            [app.renderer.globals :refer [app-state log-atom]]
            [app.renderer.keybindings :as keybindings]
            [app.renderer.nrepl :refer [nrepl-handler
                                        nrepl-connect!
                                        nrepl-connection
                                        nrepl-register-receiver]]
            [reagent.core :as reagent :refer [atom]]
            ["react" :refer [createRef]]
            ["react-highlight" :default Highlight]
            ["ace-builds" :as ace-editor]
            ["brace/index" :as brace]
            ["/js/nrepl-client" :as nrepl-client]
            ["/js/ace_paredit" :as ace-paredit]
            ["/js/ace_ext_keys" :as ace-keys]
            ["/js/ace_improved" :as ace-improved]
            ["react-split-pane" :as SplitPane]
            ["react-virtualized/dist/commonjs/AutoSizer" :refer (AutoSizer)]
            ["react-virtualized/dist/commonjs/List" :refer (List)]
            ["net" :as net]
            [clojure.core.async :as async]
            [clojure.string :as string :refer [split-lines]]))

(enable-console-print!)

(js/require "brace/ext/language_tools")

(def electron (js/require "electron"))

(def process (js/require "process"))

(def cwd (.cwd process))

#_(defn nrepl-handler [msg callback]
    (.eval @nrepl-connection
           (str (clojure.string/escape msg {"\\" "\\\\"}) "\n")
           (fn [res err]
             (prn "RESPONSE" res "error" err)
             (callback {:stdout res :stderr err}))))

#_(defonce eval-response-handler
    (.on (.-ipcRenderer electron) "response"
         (fn [resp] (when-let [response-chan (get @async-channels (:id resp))]
                      (async/go (async/>! response-chan resp))))))


#_(defn register-public-symbols []
    (nrepl-handler "(ns-publics 'panaeolus.all)\n" (str (gensym))
                   (fn [public-symbols]
                     #_(.apply (.-push (.-$keywordList (.-$highlightRules (.-$mode (.getSession (:ace-ref @state))))))
                               (.-$keywordList (.-$highlightRules (.-$mode (.getSession (:ace-ref @state)))))
                               (->> (filter #(< -0 (.indexOf % "panaeolus.all"))
                                            (clojure.string/split public-symbols " "))
                                    (map #(.replace % "#'panaeolus.all/" ""))
                                    clj->js))
                     ;; var mode = session.$mode
                     ;; mode.$highlightRules.addRules({...})
                     ;; mode.$tokenizer = new Tokenizer(mode.$highlightRules.getRules());
                     ;; session.bgTokenizer.setTokenizer(mode.$tokenizer);
                     (.setKeywords ^js (.-$highlightRules ^js (.-$mode ^js (.getSession ^js (:ace-ref @state)))) #js {"keyword" "hlolli|sig"})
                     ;; (js/console.log (.-$highlightRules (.-$mode (.getSession (:ace-ref @state)))))
                     (.resetCaches (.getSession (:ace-ref @state)))
                     (.start (.-bgTokenizer (.getSession (:ace-ref @state))) 0)
                     (.start (.-bgTokenizer (.getSession (:ace-ref @state))) 100)
                     (.start (.-bgTokenizer (.getSession (:ace-ref @state))) 1000)
                     (.start (.-bgTokenizer (.getSession (:ace-ref @state))) 3000)
                     (.start (.-bgTokenizer (.getSession (:ace-ref @state))) 6000)
                     ;; (js/console.log (.-bgTokenizer (.getSession (:ace-ref @state))))
                     ;; editor.session.bgTokenizer.start(0)
                     #_(let [session (.getSession (:ace-ref @state))
                             mode (.-$mode session)
                             newTokenizer (new (.-Tokenizer (.require js/ace "ace/tokenizer")) (.getRules (.-$highlightRules mode)))]
                         (js/console.log (.getRules (.-$highlightRules mode)))
                         (.setTokenizer (.-bgTokenizer session) newTokenizer)
                         )
                     ;; editor.session.bgTokenizer.start(0);
                     #_(.start (.-bgTokenizer (.getSession (:ace-ref @state))) 0)
                     ;; (js/console.log (.-$keywordList (.-$highlightRules (.-$mode (.getSession (:ace-ref @state))))) )
                     )))

(defn initialize-namespace []
  (nrepl-handler "(use 'panaeolus.all)(in-ns 'panaeolus.all)\n" (str (gensym))
                 (fn [_] #_(js/setTimeout register-public-symbols 1000))))

(defn nrepl-initialize [port]
  (do
    (reset! nrepl-connection (new (.-Socket (.require js/window "net"))))
    (nrepl-connect! port)
    (nrepl-register-receiver)
    (initialize-namespace)))

(def nrepl-status-handler
  (.on (.-ipcRenderer electron) "nrepl"
       (fn [event resp]
         (case (aget resp 0)
           "started" (nrepl-initialize (js/parseInt (aget resp 1)))
           nil))))

(def backend-log
  (.on (.-ipcRenderer electron) "logs-from-backend"
       (fn [_ resp]
         (swap! log-atom into (js->clj resp)))))


;; "FireCode-Medium"  "Space Mono"
(defn powerline []
  [:ul {:className "powerline"}
   [:li {:className "left"}
    [:div
     [:a {:href "#"} "1777 "]
     [:a {:href "#"} "*live*"]]
    [:div {:className "endsection"}]
    [:div [:a {:href "#"} "Clojure"]]
    [:div {:className "shrinkable"} [:a {:href "#"} "Panaeolus version 0.4.0-alpha"]]
    [:div {:className "endsection"}]]
   [:div {:className "center"}
    [:a {:href "#"} " 8e4c32f32ec869fe521fb4d3c0a69406830b4178"]]
   [:li {:className "right"}
    [:div {:className "endsection"}]
    [:div [:a {:href "#"}
           (let [current-row (str (:current-row @app-state))
                 current-col (str (:current-column @app-state))
                 empty-str-row (apply str (repeat (max 0(- 4 (count current-row))) " "))
                 empty-str-col (apply str (repeat (max 0(- 3 (count current-col))) " "))]
             (str "" empty-str-row current-row ":" empty-str-col current-col))]]
    [:div {:className "endsection"}]
    [:div [:a {:href "#"} "Top"]]]])

(defn get-nrepl-port []
  (.send (.-ipcRenderer electron) "get-nrepl-port" nil))

(defn on-edit-handler [event]
  (let [edit-event? (not (.-readOnly (.-command event)))]
    (if edit-event?
      nil nil)
    #_(js/console.log event))
  nil)



;;   <List
;;     width={300}
;;     height={300}
;;     rowCount={list.length}
;;     rowHeight={20}
;;     rowRenderer={rowRenderer}
;;   />,


;; function rowRenderer ({
;;   key,         // Unique key within array of rows
;;   index,       // Index of row within collection
;;   isScrolling, // The List is currently being scrolled
;;   isVisible,   // This row is visible within the List (eg it is not an overscanned row)
;;   style        // Style object to be applied to row (to position it)
;; }) {
;;   return (
;;     <div
;;       key={key}
;;       style={style}
;;     >
;;       {list[index]}
;;     </div>
;;   )
;; }


(defn log-row-renderer [^js env]
  (reagent/as-element
   [:> Highlight {:class-name "clojure"
                  :key (.-key env)
                  :style (.-style env)}
    (nth @log-atom (.-index env))]))

(defn logger-component-list [height width]
  [:> List {:rowCount (count @log-atom)
            :id "log-area"
            :scrollToAlignment "start"
            :scrollToIndex (dec (count @log-atom))
            :height (or height 0)
            :width (or width 0)
            :row-height 30
            :row-renderer log-row-renderer}] )

(defn logger-component []
  [:> AutoSizer
   (fn [^js size]
     (reagent/as-element
      [logger-component-list (.-height size) (.-width size)]))])

(defn root-component []
  (let [log-poller    (js/setInterval #(.send (.-ipcRenderer electron) "poll-logs" nil) 1000)]
    (reagent/create-class
     {:componentWillUnmount
      (fn []
        (set! js/window.oncontextmenu nil)
        (js/clearInterval log-poller))
      :componentDidMount
      (fn [this]
        (get-nrepl-port)
        (let [ace-ref (.edit ace-editor "ace")
              editor-session (.getSession ace-ref)]
          (keybindings/bind-keys! ace-editor ace-ref)
          (.on (.-commands ace-ref) "exec" on-edit-handler)
          (.set (.-config js/ace) "basePath" "./ace")
          (.require ace-editor "ace/mode/clojure"
                    (fn [] (.setMode editor-session "ace/mode/clojure")))
          (ace-keys (.-define js/ace))
          (ace-improved (.-define js/ace))
          (.require ace-editor "ace/ext/keys"
                    (fn []
                      (.require ace-editor "ace/improved"
                                (fn [] (ace-paredit (.-define js/ace))))))
          #_(.loadModule (.-config js/ace) "ace/mode/clojure"
                         (fn [^js clojure-mode]
                           (.loadModule (.-config js/ace) "ace/mode/dynamic"
                                        (fn [^js highlight-rules]
                                          (let [dynamic-mode (new (.-Mode clojure-mode))]
                                            (set! ^js (.-HighlightRules dynamic-mode) (.-DynHighlightRules highlight-rules))
                                            (.setMode editor-session dynamic-mode))))))
          (.require ace-editor "ace/ext/lang/paredit")
          (.require ace-editor "ace/ext/language_tools"
                    (fn []
                      #_(.setOption ace-ref "enableSnippets" true)
                      (.setOption ace-ref "enableLiveAutocompletion" true)
                      (.setOption ace-ref "enableBasicAutocompletion" true)))
          (.setTheme ace-ref "ace/theme/cyberpunk")
          (.setOption ace-ref "displayIndentGuides" false)
          (.setFontSize ace-ref 23)
          (.setShowPrintMargin ace-ref false)
          (set! js/window.oncontextmenu contextmenu)
          (swap! app-state assoc :ace-ref ace-ref)
          (.focus ace-ref)))
      :reagent-render
      (fn []
        [:div [:> SplitPane {:split "horizontal" :min-size "95%" :default-size "80%"}
               [:div {:id "ace"}]
               [logger-component]]
         [powerline]])})))

(defn reload! []
  (.send (.-ipcRenderer electron) "dev-reload"))

(defn start!
  {:dev/autoload false}
  []
  (reagent/render
   [root-component]
   (js/document.getElementById "app-container")))

(start!)



;; marker-fn (fn [html marker-layer session config]
;;             ;; (str " ;; => " res)
;;             (let [dom-node (aget (.-childNodes (.-element marker-layer)) 1)
;;                   _ (js/console.log dom-node)
;;                   class-name (and dom-node (.-className dom-node))]
;;               (when (and class-name (.includes class-name id))
;;                 (when @react-node (reagent/unmount-component-at-node dom-node))
;;                 (reset! react-node
;;                         (reagent/render
;;                          (let [class (reagent/create-class
;;                                       {:component-will-unmount (fn [] (prn "UNMO" res))
;;                                        :component-will-mount (fn [] (prn "MOUNT" res))
;;                                        :render
;;                                        (fn [] [:p {:style {:margin 0 :padding 0 :margin-left "12px"}} (do (prn "RENDER" res) (str " ;; => " res))])})]
;;                            [class])
;;                          dom-node))
;;                 (js/setTimeout #(let [session (.getSession ace-ref)
;;                                       string-builder #js []
;;                                       start (.createAnchor (.-doc (.getSession ace-ref)) (.-start range))
;;                                       end (.createAnchor (.-doc (.getSession ace-ref)) (.-end range))]
;;                                   (set! (.-start range) start)
;;                                   (set! (.-end range) end)
;;                                   (.drawSingleLineMarker marker-layer string-builder range id config)
;;                                   ) 0)))
;;             ;; (js/console.log html marker-layer session config)
;;             ;; (js/console.log (ace-editor/getMarkerHTML html marker-layer session config range "inlineEval"))
;;             )
