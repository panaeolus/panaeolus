(ns panaeolus.csound.samplers)

(defn- sample-directory->csound [directory-path]
  (let [dir-contents (sort
                      (mapv #(.getPath %)
                            (remove #(.isDirectory %)
                                    (file-seq (io/file directory-path)))))]
    (map-indexed #(str "gi_ ftgen " (inc %1) ",0,0,1,\"" %2 "\",0,0,0") dir-contents)))

(defn- produce-slice-sampler-orchestra [directory-path]
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
  p3 = ((ilen/isr)*(1/ifreq))
  ichannels = ftchnls(isample)

  imode = p7 ;; 1 or 2
  islice = p8
  iwidth = p9
  iwidth = ilen * min:i(islice, 1)
  ia = (ilen * islice) %% ilen
  ib = ((ilen * islice) + (iwidth * islice)) %% ilen

  if (ichannels == 1) then
    aL loscil iamp, ifreq, isample, 1, imode, ia, ib
    aR = aL
  elseif (ichannels == 2) then
    aL, aR loscil iamp, ifreq, isample, 1, imode, ia, ib
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
