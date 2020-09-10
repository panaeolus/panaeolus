(ns panaeolus.jack2.jack-plugins
  (:require
   [clojure.core.async :as async]
   [clojure.java.shell :as shell]
   [panaeolus.jack2.jack-lib :as jack]
   [panaeolus.jack2.jack-routing :as jack-routing]))

(def ^:private ebumeter-ps (atom nil))

(defn toggle-ebumeter! []
  (if @ebumeter-ps
    (future-cancel (prn @ebumeter-ps))
    (async/go
      (reset! ebumeter-ps (future (shell/sh "ebumeter")))
      (async/<! (async/timeout 1000))
      (let [ebumeter-port-names (jack/query-input-ports
                                 (:jack-client @jack-routing/master-client)
                                 "ebumeter")
            master-port-names (map jack/get-port-name
                                   (:jack-ports-out @jack-routing/master-client))]
        (dotimes [idx (Math/min (count ebumeter-port-names)
                                (count master-port-names))]
          (jack/connect (get master-port-names idx)
                        (get ebumeter-port-names idx)))))))

;; (toggle-ebumeter!)
