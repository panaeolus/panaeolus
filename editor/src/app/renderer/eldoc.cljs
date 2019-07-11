(ns app.renderer.eldoc
  (:require [app.renderer.globals :refer [app-state]]
            [app.renderer.nrepl :refer [nrepl-handler]]
            ["paredit.js" :as paredit-js]))

(defn get-arglists [symbol msg-id callback]
  (nrepl-handler
   (str
    "(try
     (:arglists
      (clojure.core/meta
       (clojure.core/resolve
        (clojure.core/read-string \""
    symbol
    "\"))))
     (catch Throwable e nil))"
    symbol)
   msg-id callback))

(defn eldoc-calc []
  (let [editor (:ace-ref @app-state)
        editor-value (:editor-value @app-state)
        position-index (-> editor .-session .-doc
                           (.positionToIndex (.getCursorPosition editor)))
        ast (paredit-js/parse editor-value)
        sexps-at-point (paredit-js/walk.containingSexpsAt ast position-index paredit-js/walk.hasChildren)]
    (when (< 1 (.-length sexps-at-point))
      (let [sexp-at-point (last sexps-at-point)
            operator (first (.-children sexp-at-point))]
        (when (and operator (= "symbol" (.-type operator)))
          (let [symbol (.-source operator)
                msg-id (str (gensym))]
            (get-arglists symbol msg-id
                          (fn [ret-val error?]
                            (when-not (or (= "nil" ret-val)
                                          (empty? ret-val))
                              (swap! app-state assoc :echo-buffer ret-val))))))))))
