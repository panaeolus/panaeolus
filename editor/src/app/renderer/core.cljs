(ns app.renderer.core
  (:require [reagent.core :as reagent :refer [atom]]
            ["react-ace" :default Ace]
            ["brace/mode/clojure"]
            ["brace/theme/github"]
            ["/js/sexpAtPoint" :as sexp-at-point]
            [clojure.string :as string :refer [split-lines]]))

(enable-console-print!)

(defonce state (atom {:current-row 0 :current-column 0 :ace-ref nil}))

(def electron (js/require "electron"))
(def process (js/require "process"))

(def cwd (.cwd process))

;; function evaluateExpression()
;; {
;; var selectedText = editor.getSession().doc.getTextRange(editor.selection.getRange());

;; var sexpIndx = sexpAtPoint(editor.getValue(),
;;                                           editor.session.doc.positionToIndex(editor.getCursorPosition()));

;; if (selectedText.length === 0) {
;; if (sexpIndx) {
;; var start = editor.session.doc.indexToPosition(sexpIndx[0]);
;; var end = editor.session.doc.indexToPosition(sexpIndx[1]);

;; // blink Expression
;; var _range = new Range(start['row'], 0, end['row'], 1);
;; _range.id = editor.session.addMarker(_range, "flashEval", "fullLine");
;; setTimeout(function(){
;;                    editor.session.removeMarker(_range.id);
;;                    }, 90);

;; // send expression to lumo
;; var sexp = editor.getSession().doc.getTextRange(
;;                                                 {start: start,
;;                                                 end: end});
;; ipcRenderer.send('cljs-command', sexp);
;; // console.log(sexp, "SEXP");
;; }
;; } else {
;; ipcRenderer.send('cljs-command', selectedText);
;; }
;; // console.log(sexp, selectedText);
;; }

(defn evaluate-outer-sexp []
  (prn (:ace-ref @state) #_(sexp-at-point)))

(def ctrl-down? (atom false))

(defn keydown-listener [evt]
  (let [key-code (.-keyCode evt)]
    (cond
      (= key-code 17) (reset! ctrl-down? true)
      (and (= key-code 13) @ctrl-down?)
      (evaluate-outer-sexp)
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
             (str "" empty-str-row current-row ":" empty-str-col current-col)
             #_"    1 :  0")]]
    [:div {:className "endsection"}]
    [:div [:a {:href "#"} "Top"]]]])

(defn root-component []
  {reagent/create-class
   {:componentDidMount
    (fn [] (set! (.-onkeydown js/window) keydown-listener)
      (set! (.-onkeyup js/window) keyup-listener))
    :render
    (fn []
      [:div
       [:> Ace {:mode "clojure"
                :ref (fn [ref] (swap! state assoc :ace-ref ref))
                :theme "cyberpunk"
                :style {:font-family "Space Mono" :font-size "22px"}
                :maxLines js/Infinity
                :indentedSoftWrap true
                :cursorStyle "wide"
                :showPrintMargin false
                :blockScrolling js/Infinity
                :onCursorChange (fn [evt]
                                  (swap! state assoc :current-row (.-row (.-selectionLead evt)))
                                  (swap! state assoc :current-column (.-column (.-selectionLead evt))))
                :onChange (fn [evt])}]
       [powerline]])}})


(defn start! []
  (reagent/render
   [root-component]
   (js/document.getElementById "app-container")))

(start!)
