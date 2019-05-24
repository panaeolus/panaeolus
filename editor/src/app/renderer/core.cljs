(ns app.renderer.core
  (:require [reagent.core :as reagent :refer [atom]]
            ["react" :refer [createRef]]
            ["ace-builds" :as ace-editor]
            ["/js/sexpAtPoint" :as sexp-at-point]
            ["/js/nrepl-client" :as nrepl-client]
            ["bencode" :as bencode]
            ["net" :as net]
            [clojure.core.async :as async]
            [clojure.string :as string :refer [split-lines]]))

;; (def nrepl-port 8912)

(enable-console-print!)

(defonce state (atom {:current-row 0 :current-column 0 :ace-ref nil :current-text ""
                      :flash-queue []}))

(def async-channels (atom {}))

(def electron (js/require "electron"))
(def process (js/require "process"))

(def cwd (.cwd process))

(def nrepl-connection (atom nil))

(defn nrepl-connect! [nrepl-port]
  (.connect @nrepl-connection nrepl-port "127.0.0.1" (fn [])))

(defn nrepl-handler [msg callback]
  (js/console.log   @nrepl-connection)
  (when @nrepl-connection
    (.write @nrepl-connection
            (bencode/encode #{:op "eval"
                              :id "100"
                              :code (str (clojure.string/escape msg {"\\" "\\\\"}) "\n")})
            #_(fn [res err]
                (prn "RESPONSE" res "error" err)
                (callback {:stdout res :stderr err})))
    ))

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

(defn register-nrepl-receiver []
  (.on @nrepl-connection "data" (fn [data] (let [status (.-status (bencode/decode data))
                                                 status1 (.toString (aget status 0))
                                                 status2 (.toString (aget status 1))
                                                 status3 (.toString (aget status 2))]
                                             (js/console.log  "DATA RECEIVE" status1 status2 status3)))))
(defonce nrepl-status-handler
  (.on (.-ipcRenderer electron) "nrepl"
       (fn [resp] (case (aget resp 0)
                    "started" (fn []
                                (reset! nrepl-connection (new (.-Socket (.require js/window "net"))))
                                (nrepl-connect! (js/parseInt (aget resp 1)))
                                (register-nrepl-receiver))
                    nil))))

#_(defn nrepl-async [data]
    (let [response-chan (async/chan 1)
          symb (str (gensym))]
      (swap! async-channels assoc symb response-chan)
      (.send  (.-ipcRenderer electron) "eval" #js [symb data])
      (async/go (async/<! (async/timeout (* 2 60 1000)))
                (when (contains? @async-channels symb)
                  (swap! async-channels dissoc symb)))
      response-chan))


(defn flash-region [ace-ref sexp-positions]
  (when (and ace-ref (exists? (.-startIndex sexp-positions)))
    (let [pointACoord (.indexToPosition (.-doc (.-session ace-ref)) (.-startIndex sexp-positions))
          pointBCoord (.indexToPosition (.-doc (.-session ace-ref)) (.-endIndex sexp-positions))
          range (new (.-Range js/ace)
                     (.-row pointACoord)
                     (.-column pointACoord)
                     (.-row pointBCoord)
                     (.-column pointBCoord))]
      (set! (.-id range) (.addMarker (.-session ^js ace-ref) range "flashEval" "text"))
      (js/setTimeout #(.removeMarker (.-session ace-ref) (.-id range)) 300))))

(defn evaluate-outer-sexp []
  (when-let [ace-ref (:ace-ref @state)]
    (let [current-text (.getValue ace-ref)
          sexp-positions (sexp-at-point current-text  (.positionToIndex (.-doc (.-session ace-ref)) (.getCursorPosition ace-ref)))]
      (when sexp-positions
        (let [trimmed-bundle (clojure.string/trim (subs current-text
                                                        (.-startIndex sexp-positions)
                                                        (.-endIndex sexp-positions)))]
          (flash-region ace-ref sexp-positions)
          (when-not (empty? trimmed-bundle)
            (nrepl-handler trimmed-bundle (fn [res] (prn "SUCCESS" res)))
            #_(async/go (when-let [ret (async/<! (ipc-async trimmed-bundle))]
                          (prn "RETURN VAL" ret)))
            #_(.send  (.-ipcRenderer electron) "eval" trimmed-bundle)))))))

(def ctrl-down? (atom false))

(def eval-throttle? (atom false))

(defn keydown-listener [evt]
  (let [key-code (.-keyCode evt)]
    (cond
      (= key-code 17) (reset! ctrl-down? true)
      (and (= key-code 13) @ctrl-down? (not @eval-throttle?))
      (do
        (evaluate-outer-sexp)
        (reset! eval-throttle? true)
        (js/setTimeout #(reset! eval-throttle? false) 5))
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
     [:a {:href "#"} "177 "]
     [:a {:href "#"} "*scratch*"]]
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

(defn get-public-ns []
  (.send  (.-ipcRenderer electron) "eval" "(ns-publics 'panaeolus.all)"))

(defn root-component []
  (reagent/create-class
   {:componentWillUnmount
    (fn []
      (.removeEventListener js/document "keydown" keydown-listener)
      (.removeEventListener js/document "keyup" keyup-listener))
    :componentDidMount
    (fn [this]
      (let [ace-ref (.edit ace-editor "ace")
            editor-session (.getSession ace-ref)]
        (.set (.-config js/ace) "basePath" "./")
        (.setTheme ace-ref "ace/theme/cyberpunk")
        (.setMode editor-session "ace/mode/clojure")
        (.setFontSize ace-ref 23)
        (.addEventListener js/document "keydown" keydown-listener)
        (.addEventListener js/document "keyup" keyup-listener)
        (js/console.log editor-session)
        (swap! state assoc :ace-ref ace-ref)
        (.focus ace-ref)
        #_(js/setTimeout
           (fn []
             )
           #_(fn [] (let [nrepl (.connect nrepl-client #js {:port nrepl-port :host "127.0.0.1"})]
                      (.once nrepl "connect" #(reset! nrepl-connection nrepl))))
           2000)))
    :reagent-render
    (fn []
      [:div
       [:div {:id "ace"}]
       #_[:> Ace {:mode "clojure"
                  :ref ace-ref
                  ;; (fn [ref] (when-not (:ace-ref @state) (prn "NOOOO") (swap! state assoc :ace-ref ref)))
                  :theme "cyberpunk"
                  :style {:font-family "Space Mono" :font-size "22px"}
                  :maxLines js/Infinity
                  :indentedSoftWrap true
                  :cursorStyle "wide"
                  :showPrintMargin false
                  ;; :markers flash-queue
                  :markers (:flash-queue @state)
                  ;; :markers (if (empty? (:flash-queue @state)) #js [] (clj->js (:flash-queue @state)))
                  ;; :editorProps {:$blockScrolling js/Infinity}
                  :onCursorChange (fn [evt]
                                    (let [current-row (.-row (.-selectionLead evt))
                                          current-row (if (and (string? current-row) (not (empty? current-row)))
                                                        (js/parseInt current-row) current-row)
                                          current-column (.-column (.-selectionLead evt))
                                          current-column (if (and (string? current-column) (not (empty? current-column)))
                                                           (js/parseInt current-column) current-column)]
                                      (swap! state assoc :current-row current-row)
                                      (swap! state assoc :current-column current-column)))
                  :onChange (fn [evt] (swap! state assoc :current-text evt))}]
       [powerline]])}))


(defn start! []
  (reagent/render
   [root-component]
   (js/document.getElementById "app-container")))

(start!)
