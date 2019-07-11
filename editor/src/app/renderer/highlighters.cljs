(ns app.renderer.highlighters
  (:require [app.renderer.editor :as editor]
            [app.renderer.globals :refer [app-state]]
            [app.renderer.nrepl :refer [nrepl-handler]]
            [cljs.reader :refer [read-string]]
            [clojure.string :as string]
            ["paredit.js" :as paredit-js]))

(defn get-all-instruments []
  (let [msg-id (str (gensym))
        callback (fn [ret error?]
                   (swap! app-state assoc :all-instruments
                          (reduce
                           (fn [i v]
                             (let [[ns sym] (string/split v #"/")]
                               (conj i sym))) #{} (read-string ret))))]
    (nrepl-handler
     "(vec (vals @panaeolus.globals/loaded-instr-symbols))"
     msg-id callback)))

(defn get-active-instruments []
  (let [msg-id (str (gensym))
        callback (fn [ret error?]
                   (let [active-instruments (reduce
                                             (fn [i v]
                                               (let [[ns sym] (string/split v #"/")]
                                                 (conj i sym))) #{} (read-string ret))
                         highlighters (reduce (fn [i ^js v]
                                                (conj i (editor/marker-range
                                                         {:start-row (.-startRow v)
                                                          :start-col (.-startCol v)
                                                          :end-row (.-endRow v)
                                                          :end-col (.-endCol v)
                                                          :class-name (if (active-instruments (.-symbol v))
                                                                        "instr-active" "instr-inactive")
                                                          :type (.-type v)
                                                          :symbol (.-symbol v)})))
                                              []
                                              (:highlighters @app-state))]
                     (swap! app-state assoc
                            :active-instruments active-instruments
                            :highlighters highlighters)))]
    (nrepl-handler "@panaeolus.globals/active-instr-symbols" msg-id callback)))

(defn- seperator? [char]
  (re-matches #"\n|\(|\)|\s|\r|\[|\]|\t|\{|\}|#\{" char))

(defn tokenize [editor-val]
  (loop [chars (seq editor-val)
         index 0
         symbols []
         cur-symbol ""
         cur-symbol-start nil]
    (if (empty? chars)
      symbols
      (let [char (first chars)]
        (if (seperator? char)
          (if (empty? cur-symbol)
            (recur (rest chars)
                   (inc index)
                   symbols
                   cur-symbol
                   (inc index))
            (recur (rest chars)
                   (inc index)
                   (conj symbols {:symbol cur-symbol :start cur-symbol-start :end index})
                   ""
                   nil))
          (recur (rest chars)
                 (inc index)
                 symbols
                 (str cur-symbol char)
                 cur-symbol-start))))))

(defn highlight [editor-val]
  (let [ace-ref (:ace-ref @app-state)
        instr-symbols (:all-instruments @app-state)
        tokens (tokenize editor-val)]
    (doseq [token tokens]
      (when (some #(= (:symbol token) %) instr-symbols)
        (let [pointACoord (.indexToPosition (.-doc (.-session ace-ref)) (:start token))
              pointBCoord (.indexToPosition (.-doc (.-session ace-ref)) (:end token))]
          (swap! app-state update :highlighters conj
                 (editor/marker-range
                  {:start-row (.-row pointACoord)
                   :start-col (.-column pointACoord)
                   :end-row (.-row pointBCoord)
                   :end-col (.-column pointBCoord)
                   :symbol (:symbol token)
                   :class-name (if ((:active-instruments @app-state) (:symbol token)) "instr-active" "instr-inactive")
                   :type "background"})))))))
