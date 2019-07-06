(ns app.renderer.editor
  (:require [app.renderer.globals :refer [app-state]]
            [app.renderer.nrepl :refer [nrepl-handler]]
            [clojure.string :as string]
            ["/js/sexpAtPoint" :as sexp-at-point]))

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
  (when-let [ace-ref (:ace-ref @app-state)]
    (let [current-text (.getValue ace-ref)
          sexp-positions (sexp-at-point current-text
                                        (.positionToIndex (.-doc (.-session ace-ref))
                                                          (.getCursorPosition ace-ref)))]
      (when sexp-positions
        (let [trimmed-bundle (string/trim (subs current-text
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
                                   (swap! app-state update :inline-ranges conj range))))))))))))
