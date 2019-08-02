(ns panaeolus.csound.samplers
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [panaeolus.csound.macros :as c]))

(defn sample-directory->csound [directory-path]
  (let [dir-contents (sort
                      (mapv #(.getPath ^java.io.File %)
                            (remove #(.isDirectory ^java.io.File %)
                                    (file-seq (io/file directory-path)))))]
    (map-indexed #(str "gi_ ftgen " (inc %1) ",0,0,1,\"" %2 "\",0,0,0") dir-contents)))

(defn produce-slice-sampler-orchestra [directory-path]
  (let [csound-tables (sample-directory->csound directory-path)]
    (str (string/join
          "\n" csound-tables)
         (format "
  instr 1
  iamp = ampdbfs(p5)
  idur = p3
  ifreq = p4
  ifreq limit ifreq, 0.00001, sr/2
  isample = (p6 %% %s) + 1
  ilen nsamp isample
  isr ftsr isample
  ;; p3 = ((ilen/isr)*(1/ifreq))
  ichannels = ftchnls(isample)

  imode = p7 ;; 1 or 2
  islice = p8
  ;; iwidth = p9
  ;; iwidth = ilen * min:i(islice, 1)
  ia = (ilen * islice) %% ilen
  ib = (ia + (ilen * islice)) %% ilen

  if (ichannels == 1) then
    aL loscilx iamp, ifreq, isample, 4, 1, 0, imode, ia, ib
    aR = aL
  elseif (ichannels == 2) then
    aL, aR loscilx iamp, ifreq, isample, 4, 1, 0, imode, ia, ib
  else
    aM = 0
    a1 = 0
    a2 = 0
  endif

  aenv    linseg 0, 0.02, 1, p3 - 0.05, 1, 0.02, 0, 0.01, 0

  aL =  aL*aenv/2
  aR =  aR*aenv/2

  outs aL, aR
  endin
  " (count csound-tables)))))

(defn produce-grain-sampler-orchestra [directory-path]
  (let [csound-tables (sample-directory->csound directory-path)]
    (str (string/join
          "\n" csound-tables)
         (format "
  instr 1
  iamp = ampdbfs(p5 - 12)
  idur = p3
  ifreq = p4
  ifreq limit ifreq, 0.00001, sr/2
  isample = (p6 %% %s) + 1
  isize nsamp isample
  isr ftsr isample
  ilen = isize / isr
  ichannels = ftchnls(isample)

  ivoice = p7 ;; 2000
  iratio = p8 ;; 1
  imode = p9 ;; -1 backwards, 0 random, 1 forwards
  iskip = p10
  igskip_os = p11
  ilength = ilen * p12
  kgap = p13
  igap_os = p14 * 100 ;; random offset; 0 none
  kgrainsize = p15
  igrainsize_os = p16 ;; 0 -> 1 (percentage)
  igrainatt = p17 * 100 ;; 0 -> 1 (percentage)
  igraindec = p18 * 100 ;; 0 -> 1 (percentage)
  ipitch1 = p19
  ipitch2 = p20
  ipitch3 = p21
  ipitch4 = p22

  if (ichannels == 1) then
    aL granule iamp, ivoice, iratio, imode, 0, isample, ifreq, iskip, \\
        igskip_os, ilength, kgap, igap_os, kgrainsize, igrainsize_os, igrainatt, \\
        igraindec, 0.39, ipitch1 , ipitch2 , ipitch3 , ipitch4
    aR = aL
  elseif (ichannels == 2) then
    aL granule iamp, ivoice, iratio, imode, 0, isample, ifreq, iskip, \\
        igskip_os, ilength, kgap, igap_os, kgrainsize, igrainsize_os, igrainatt, \\
        igraindec, 0.39, ipitch1 , ipitch2 , ipitch3 , ipitch4
    aR granule iamp, ivoice, iratio, imode, 0, isample, ifreq, iskip, \\
        igskip_os, ilength, kgap, igap_os, kgrainsize, igrainsize_os, igrainatt, \\
        igraindec, 0.39, ipitch1 , ipitch2 , ipitch3 , ipitch4
  else
    aL = 0
    aR = 0
  endif

  ;; aenv    linseg 0, 0.02, 1, p3 - 0.05, 1, 0.02, 0, 0.01, 0

  ;; aL =  aL*aenv/2
  ;; aR =  aR*aenv/2

  outs aL, aR
  endin
  " (count csound-tables)))))


(defmacro define-sampler [sampler-name directory-path]
  `(do (c/definst ~sampler-name
         :orc-string (produce-slice-sampler-orchestra ~directory-path)
         :instr-form [{:name :dur :default 2}
                      {:name :nn :default 1}
                      {:name :amp :default -18}
                      {:name :sample :default 0}
                      {:name :mode :default 2}
                      {:name :slice :default 0}
                      ;; {:name :width :default 1}
                      ]
         :instr-number 1
         :num-outs 2
         :release-time 2)
       (c/definst ~(symbol (str (name sampler-name) "-grain"))
         :orc-string (produce-grain-sampler-orchestra ~directory-path)
         :instr-form [{:name :dur :default 2}
                      {:name :nn :default 1}
                      {:name :amp :default -18}
                      {:name :sample :default 0}
                      {:name :voice :default 64}
                      {:name :ratio :default 1}
                      {:name :mode :default 1}
                      {:name :skip :default 0}
                      {:name :skip-os :default 0}
                      {:name :len :default 1}
                      {:name :gap :default 0.01}
                      {:name :gap-os :default 0.1}
                      {:name :grain :default 0.02}
                      {:name :grain-os :default 0.1}
                      {:name :grain-att :default 0.3}
                      {:name :grain-dec :default 0.3}
                      {:name :picth1 :default 1}
                      {:name :pitch2 :default 1}
                      {:name :pitch3 :default 1}
                      {:name :pitch4 :default 1}]
         :instr-number 1
         :num-outs 2
         :release-time 2)))

(comment
  (define-sampler riff-128 "/home/hlolli/samples/sim_riff_128")
  (riff-128-grain :loop [1 1 1 1]
                  :sample [2 [1 2] 20 3]
                  :ratio 1
                  :gap 0.02
                  :pitch4 10
                  )
  (riff-128 :loop [0.25 0.25 0.25 0.25] :amp -2 :dur 0.3 :sample [3 12 11] :slice [0.23 0.12 1] )
  )
