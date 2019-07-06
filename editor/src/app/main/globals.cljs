(ns app.main.globals)

(def jre-connection (atom nil))

(def log-queue (atom []))

(def nrepl-port (+ 1025 (rand-int (- 65535 1025))))
