(ns app.renderer.nrepl
  (:require [app.renderer.globals :refer [app-state log-atom]]
            ["bencode" :as bencode]
            [clojure.core.async :as async]
            [clojure.string :as string]))

(def nrepl-connection (clojure.core/atom nil))

(defn nrepl-connect! [nrepl-port]
  (.connect @nrepl-connection nrepl-port "127.0.0.1" (fn [])))

(defn nrepl-handler [msg id callback]
  (when @nrepl-connection
    (swap! app-state assoc-in [:nrepl-callbacks id] callback)
    ;; timeout if something fails
    (async/go (async/<! (async/timeout (* 2 60 1000)))
              (when (contains? (:nrepl-callbacks @app-state) id)
                (swap! app-state update-in [:nrepl-callbacks] dissoc id)))
    (.write @nrepl-connection
            (bencode/encode #js {:op "eval"
                                 :id id
                                 :code (str (string/escape msg {"\\" "\\\\"}) "\n")}))))

(def stdout-buffer (atom nil))

(defn nrepl-register-receiver []
  (.on @nrepl-connection "data"
       (fn [data]
         (let [decoded-data (bencode/decode data)]
           (if (not (.-err decoded-data))
             (do
               (when (.-out decoded-data)
                 (let [stdout (.toString (.-out decoded-data))
                       needs-buffering? (.endsWith "\n" stdout)
                       stdout-chopped (.split stdout "\n")
                       stdout-chopped (if-let [buf @stdout-buffer]
                                        (do (assoc stdout-chopped 0 (str buf @stdout-buffer))
                                            (reset! stdout-buffer nil))
                                        stdout-chopped)
                       stdout-chopped (if needs-buffering?
                                        (do (reset! stdout-buffer (last stdout-chopped))
                                            (.slice stdout-chopped 0 -1))
                                        stdout-chopped)]
                   (doseq [out stdout-chopped]
                     (swap! log-atom conj out))))
               (let [return-value (.toString (or (.-value decoded-data) "nil"))
                     id (.toString (.-id decoded-data))]
                 (when-let [callback (get-in @app-state [:nrepl-callbacks id])]
                   (swap! app-state update-in [:nrepl-callbacks] dissoc id)
                   (callback return-value false))))
             (let [id (.toString (.-id decoded-data))
                   status (.-status decoded-data)]
               (cond
                 (.-err decoded-data)
                 (when-let [callback (get-in @app-state [:nrepl-callbacks id])]
                   (swap! app-state update-in [:nrepl-callbacks] dissoc id)
                   (callback (.toString (.-err decoded-data)) true))
                 :default (when-let [callback (get-in @app-state [:nrepl-callbacks id])]
                            (swap! app-state update-in [:nrepl-callbacks] dissoc id)
                            (callback "error" true)))))))))
