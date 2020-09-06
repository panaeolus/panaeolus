(ns panaeolus.csound.instruments.soundcheck
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]
    [panaeolus.csound.macros :as c]))

(defn sample-directory->csound
  [directory-path]
  (let [dir-contents
          (sort
            (mapv #(.getPath ^java.io.File %)
              (remove #(.isDirectory ^java.io.File %)
                (file-seq (io/file directory-path)))))]
    (map-indexed
      #(str "gi_ ftgen " (inc %1) ",0,0,1,\"" %2 "\",0,0,0")
      dir-contents)))

(defn produce-soundcheck
  []
  (let [csound-tables (sample-directory->csound "/home/hlolli/samples/test")]
    (str
      (string/join "\n" csound-tables)
      "
instr 1
  idur = p3
  iamp = ampdbfs(p4)
  isample = p5 + 1
  ichannel = p6
  ilen ftlen isample
  isr ftsr isample
  p3 = ilen/isr

  if (ichannel == 0) then
    aL,aR loscil iamp, 1, isample, 1, 0
    aR *= 0
  elseif (ichannel == 1) then
    aL,aR loscil iamp, 1, isample, 1, 0
    aR = aL
    aL *= 0
  else
    aM = 0
    aL = 0
    aR = 0
  endif

  outs aL, aR
endin
  ")))

(c/definst
  soundcheck
  :orc-string (produce-soundcheck)
  :instr-form
    [{:name :noop1, :default 2} {:name :amp, :default -12}
     {:name :sample, :default 0} {:name :channel, :default 0}]
  :instr-number 1
  :num-outs 2
  :release-time 1)
