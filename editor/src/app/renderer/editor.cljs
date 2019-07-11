(ns app.renderer.editor
  (:require [app.renderer.globals :refer [app-state] :as globals]
            [app.renderer.nrepl :refer [nrepl-handler]]
            [clojure.string :as string]
            ["/js/sexpAtPoint" :as sexp-at-point]))

(defn marker-range [{:keys [start-row start-col end-row end-col
                            class-name type symbol]}]
  #js {"startRow" start-row "startCol" start-col
       "endRow" end-row "endCol" end-col
       "className" class-name "type" type "symbol" (or symbol "")})

(defn flash-region [ace-ref sexp-positions error?]
  (when (and ace-ref (exists? (.-startIndex sexp-positions)))
    (let [pointACoord (.indexToPosition (.-doc (.-session ace-ref)) (.-startIndex sexp-positions))
          pointBCoord (.indexToPosition (.-doc (.-session ace-ref)) (.-endIndex sexp-positions))
          flash-range (marker-range
                       {:start-row (.-row pointACoord)
                        :start-col (.-column pointACoord)
                        :end-row (.-row pointBCoord)
                        :end-col (.-column pointBCoord)
                        :class-name (if error? "flashEvalError" "flashEval")
                        :type "background"})
          id (str (gensym))]
      (swap! app-state assoc-in [:markers id] flash-range)
      (js/setTimeout #(swap! app-state update :markers dissoc id) 300))))

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
                               (swap! app-state assoc :echo-buffer (if error? res (str "=> " res)))
                               (flash-region ace-ref sexp-positions error?)
                               #_(let [pointBCoord (.indexToPosition (.-doc session) (.-endIndex sexp-positions))
                                       flash-range                                      ]
                                   (flash-region ace-ref sexp-positions error?)
                                   (let [range-remove (new (.-Range (.acequire js/ace "ace/range"))
                                                           (.-row pointBCoord)
                                                           (.-column pointBCoord)
                                                           (.-row pointBCoord)
                                                           (count (.getLine (.-doc session) (.-row pointBCoord))))]
                                     (.remove (.-doc session) range-remove))
                                   (.insert session #js {:row (.-row pointBCoord) :column (inc (.-column pointBCoord))} (str " ;; => " res))
                                   (println "=> " res)
                                   (swap! app-state update :inline-ranges conj range)))))))))))

#_(when (and ace-ref (exists? (.-startIndex sexp-positions)))
    (let [session (.getSession ace-ref)
          pointBCoord (.indexToPosition (.-doc session) (.-endIndex sexp-positions))
          range (new (.-Range (.acequire js/ace "ace/range"))
                     (.-row pointBCoord)
                     (inc (.-column pointBCoord))
                     (.-row pointBCoord)
                     (+ (.-column pointBCoord) 7 (count res)))]
      (flash-region ace-ref sexp-positions error?)
      (let [range-remove (new (.-Range (.acequire js/ace "ace/range"))
                              (.-row pointBCoord)
                              (.-column pointBCoord)
                              (.-row pointBCoord)
                              (count (.getLine (.-doc session) (.-row pointBCoord))))]
        (.remove (.-doc session) range-remove))
      (.insert session #js {:row (.-row pointBCoord) :column (inc (.-column pointBCoord))} (str " ;; => " res))
      (println "=> " res)
      (swap! app-state update :inline-ranges conj range)))
