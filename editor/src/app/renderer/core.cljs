(ns app.renderer.core
  (:require [reagent.core :as reagent :refer [atom]]
            ["react" :refer [createRef]]
            ["react-highlight" :default Highlight]
            ["ace-builds" :as ace-editor]
            ["/js/sexpAtPoint" :as sexp-at-point]
            ["/js/nrepl-client" :as nrepl-client]
            ["react-split-pane" :as SplitPane]
            ["react-virtualized/dist/commonjs/AutoSizer" :refer (AutoSizer)]
            ["react-virtualized/dist/commonjs/List" :refer (List)]
            ;; ["/js/dynamic-mode"]
            ["bencode" :as bencode]
            ["net" :as net]
            ;; ["simplebar-react" :default SimpleBar]
            ["vex-js" :as vex]
            [clojure.core.async :as async]
            [clojure.string :as string :refer [split-lines]]))

;; (def nrepl-port 8912)

(enable-console-print!)

;; (js/require "simplebar/dist/simplebar.min.css")

(when-not (aget vex "dialog")
  (.registerPlugin vex (js/require "vex-dialog"))
  (set! (.-className (.-defaultOptions vex)) "vex-theme-os")
  (set! (.-text (.-YES (.-buttons (.-dialog vex)))) "Okiedokie")
  (set! (.-text (.-NO (.-buttons (.-dialog vex)))) "Aahw hell no"))

(defonce state (atom {:ace-ref nil :nrepl-callbacks {} :inline-ranges []}))

(def electron (js/require "electron"))

(def process (js/require "process"))

(def cwd (.cwd process))

(def nrepl-connection (clojure.core/atom nil))

(defn nrepl-connect! [nrepl-port]
  (.connect @nrepl-connection nrepl-port "127.0.0.1" (fn [])))

(defn nrepl-handler [msg id callback]
  (when @nrepl-connection
    (swap! state assoc-in [:nrepl-callbacks id] callback)
    ;; timeout if something fails
    (async/go (async/<! (async/timeout (* 2 60 1000)))
              (when (contains? (:nrepl-callbacks @state) id)
                (swap! state update-in [:nrepl-callbacks] dissoc id)))
    (.write @nrepl-connection
            (bencode/encode #js {:op "eval"
                                 :id id
                                 :code (str (clojure.string/escape msg {"\\" "\\\\"}) "\n")}))))

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

(def log-atom (atom []))

(defn register-nrepl-receiver []
  (.on @nrepl-connection "data"
       (fn [data]
         (let [decoded-data (bencode/decode data)]
           ;; (js/console.log decoded-data)
           (if-not (exists? (.-status decoded-data)) ;; status indicates a failure?
             (if-not (exists? (.-value decoded-data))
               (if (exists? (.-out decoded-data))
                 (swap! log-atom conj (.toString (.-out decoded-data)))
                 (swap! log-atom conj (.toString (.-err decoded-data))))
               (let [return-value (.toString (.-value decoded-data))
                     id (.toString (.-id decoded-data))]
                 (when-let [callback (get-in @state [:nrepl-callbacks id])]
                   (callback return-value false)
                   (swap! state update-in [:nrepl-callbacks] dissoc id))))
             (let [id (.toString (.-id decoded-data))
                   status (.-status (bencode/decode data))
                   status1 (.toString (aget status 0))
                   status2 (when (< 1 (.-length status))
                             (.toString (aget status 1)))
                   status3 (when (< 2 (.-length status))
                             (.toString (aget status 2)))]
               (when-let [callback (get-in @state [:nrepl-callbacks id])]
                 (callback "error" true)
                 (swap! state update-in [:nrepl-callbacks] dissoc id))
               (when-not (= "done" status1)
                 (js/console.error  "REPL FAILURE: " status1 status2 status3))))))))

(defn register-public-symbols []
  (nrepl-handler "(ns-publics 'panaeolus.all)\n" (str (gensym))
                 (fn [public-symbols]
                   #_(.apply (.-push (.-$keywordList (.-$highlightRules (.-$mode (.getSession (:ace-ref @state))))))
                             (.-$keywordList (.-$highlightRules (.-$mode (.getSession (:ace-ref @state)))))
                             (->> (filter #(< -1 (.indexOf % "panaeolus.all"))
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
                 (fn [_] (js/setTimeout register-public-symbols 1000))))

(defn nrepl-initialize [port]
  (do
    (reset! nrepl-connection (new (.-Socket (.require js/window "net"))))
    (nrepl-connect! port)
    (register-nrepl-receiver)
    (initialize-namespace)))

(defonce nrepl-status-handler
  (.on (.-ipcRenderer electron) "nrepl"
       (fn [event resp]
         (case (aget resp 0)
           "started" (nrepl-initialize (js/parseInt (aget resp 1)))
           nil))))


(defn flash-region [ace-ref sexp-positions error?]
  (when (and ace-ref (exists? (.-startIndex sexp-positions)))
    (let [pointACoord (.indexToPosition (.-doc (.-session ace-ref)) (.-startIndex sexp-positions))
          pointBCoord (.indexToPosition (.-doc (.-session ace-ref)) (.-endIndex sexp-positions))
          range (new (.-Range js/ace)
                     (.-row pointACoord)
                     (.-column pointACoord)
                     (.-row pointBCoord)
                     (.-column pointBCoord))]
      (set! (.-id range) (.addMarker (.-session ^js ace-ref) range
                                     (if error? "flashEvalError" "flashEval") "text"))
      (js/setTimeout #(.removeMarker (.-session ^js ace-ref) (.-id range)) 300))))

(defn evaluate-outer-sexp []
  (when-let [ace-ref (:ace-ref @state)]
    (let [current-text (.getValue ace-ref)
          sexp-positions (sexp-at-point current-text  (.positionToIndex (.-doc (.-session ace-ref)) (.getCursorPosition ace-ref)))]
      (when sexp-positions
        (let [trimmed-bundle (clojure.string/trim (subs current-text
                                                        (.-startIndex sexp-positions)
                                                        (.-endIndex sexp-positions)))]
          (when-not (empty? trimmed-bundle)
            (let [id (str (gensym))
                  react-node (atom nil)]
              (nrepl-handler trimmed-bundle id
                             (fn [res error?]
                               (when (and ace-ref (exists? (.-startIndex sexp-positions)))
                                 (let [session (.getSession ace-ref)
                                       pointBCoord (.indexToPosition (.-doc session) (.-endIndex sexp-positions))
                                       range (new (.-Range js/ace)
                                                  (.-row pointBCoord)
                                                  (inc (.-column pointBCoord))
                                                  (.-row pointBCoord)
                                                  (+ (.-column pointBCoord) 7 (count res)))]
                                   (flash-region ace-ref sexp-positions error?)
                                   (.remove (.-doc session)
                                            (new (.-Range js/ace)
                                                 (.-row pointBCoord)
                                                 (.-column pointBCoord)
                                                 (.-row pointBCoord)
                                                 (count (.getLine (.-doc session) (.-row pointBCoord)))))
                                   (.insert session #js {:row (.-row pointBCoord) :column (inc (.-column pointBCoord))} (str " ;; => " res))
                                   (println "=> " res)
                                   (swap! state update :inline-ranges conj range)
                                   #_(set! (.-id range) (.addMarker (.-session ^js ace-ref) range "inlineEval" "text")))))))
            #_(async/go (when-let [ret (async/<! (ipc-async trimmed-bundle))]
                          (prn "RETURN VAL" ret)))
            #_(.send  (.-ipcRenderer electron) "eval" trimmed-bundle)))))))

(def ctrl-down? (atom false))

(def eval-throttle? (atom false))

(defn keydown-listener [evt]
  (let [key-code (.-keyCode evt)]
    (cond
      (= key-code 17) (reset! ctrl-down? true)
      (and @ctrl-down? (= key-code 13) (not @eval-throttle?))
      (do
        (evaluate-outer-sexp)
        (reset! eval-throttle? true)
        (js/setTimeout #(reset! eval-throttle? false) 5))
      (and @ctrl-down? (= key-code 81))
      (.confirm (.-dialog vex)
                #js {:message "You are NOT standing in front of an audience, performing music, and you really mean to quit?"
                     :callback (fn [true?] (when true? (.send (.-ipcRenderer electron) "quit" nil)))})
      (= key-code 123)
      (.toggleDevTools (.getCurrentWebContents (.-remote electron)))
      :else nil)))

(defn keyup-listener [evt]
  (let [key-code (.-keyCode evt)]
    (cond
      (= key-code 17) (reset! ctrl-down? false)
      :else nil)))

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
           (let [current-row (str (:current-row @state))
                 current-col (str (:current-column @state))
                 empty-str-row (apply str (repeat (max 0(- 4 (count current-row))) " "))
                 empty-str-col (apply str (repeat (max 0(- 3 (count current-col))) " "))]
             (str "" empty-str-row current-row ":" empty-str-col current-col))]]
    [:div {:className "endsection"}]
    [:div [:a {:href "#"} "Top"]]]])


(defn request-jre-boot []
  (.send  (.-ipcRenderer electron) "boot-jre" nil))

(defn on-edit-handler [event]
  (let [edit-event? (not (.-readOnly (.-command event)))]
    (if edit-event?
      nil nil)
    #_(js/console.log event))
  nil)


(defn right-click-menu [evt]
  (let [remote (.-remote electron)
        menu (new (.-Menu (.-remote electron)))
        MenuItem (.-MenuItem (.-remote electron))]
    (.append menu (new MenuItem (clj->js {:label "kbd mode"
                                          :submenu [{:label "default"}
                                                    {:label "emacs"
                                                     :click (fn [] (when-let [ace-ref (:ace-ref @state)]
                                                                     (.setKeyboardHandler ace-ref "ace/keyboard/emacs")))}
                                                    {:label "vim"}]})))
    (.popup ^js menu #js {:window (.getCurrentWindow remote)})))


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
  (js/console.log (count @log-atom))
  [:> List {:rowCount (count @log-atom)
            :id "log-area"
            ;; :key (count @log-atom)
            :scrollToAlignment "start"
            ;; :overscanRowCount 200
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
  (reagent/create-class
   {:componentWillUnmount
    (fn []
      (.removeEventListener js/document "keydown" keydown-listener)
      (.removeEventListener js/document "keyup" keyup-listener)
      (set! js/window.oncontextmenu nil))
    :componentDidMount
    (fn [this]
      (.addEventListener js/document "keydown" keydown-listener)
      (.addEventListener js/document "keyup" keyup-listener)
      (let [ace-ref (.edit ace-editor "ace")
            editor-session (.getSession ace-ref)]
        (.on (.-commands ace-ref) "exec" on-edit-handler)
        (.set (.-config js/ace) "basePath" "./ace")
        (.loadModule (.-config js/ace) "ace/mode/clojure"
                     (fn [^js clojure-mode]
                       (.loadModule (.-config js/ace) "ace/mode/dynamic"
                                    (fn [^js highlight-rules]
                                      (let [dynamic-mode (new (.-Mode clojure-mode))]
                                        (set! ^js (.-HighlightRules dynamic-mode) (.-DynHighlightRules highlight-rules))
                                        (.setMode editor-session dynamic-mode))))))
        (.setTheme ace-ref "ace/theme/cyberpunk")
        (.setOption ace-ref "displayIndentGuides" false)
        (.setFontSize ace-ref 23)
        (.setShowPrintMargin ace-ref false)
        (set! js/window.oncontextmenu right-click-menu)
        (swap! state assoc :ace-ref ace-ref)
        (.focus ace-ref)))
    :reagent-render
    (fn []
      [:> SplitPane {:split "horizontal" :min-size "95%" :default-size "80%"}
       [:div {:id "ace"}]
       [logger-component]
       #_[:div {:id "log-area-container"}
          [:div {:id "shadow-layer"}]
          [logger-component]
          [powerline]]])}))

;; import React from 'react';
;; import ReactDOM from 'react-dom';
;; import { List } from 'react-virtualized';

;; // List data as an array of strings
;; const list = [
;;   'Brian Vaughn'
;;   // And so on...
;; ];

#_(defn row-renderer [^js env]
    (r/as-element []))


;; // Render your list
;; ReactDOM.render(
;;   <List
;;     width={300}
;;     height={300}
;;     rowCount={list.length}
;;     rowHeight={20}
;;     rowRenderer={rowRenderer}
;;   />,
;;   document.getElementById('example')
;; );

(defn reload! []
  (.send (.-ipcRenderer electron) "dev-reload"))

(defn start!
  {:dev/autoload false}
  []
  (request-jre-boot)
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
