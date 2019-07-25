gi_ ftgen 1, 0, 32768, 10, 1
gi_ ftgen 10, 0, 65536, 10, 1, 0, 0, 0, 0, 0, 0, 0, 0, .05

gkactive init 0
gkPolyLimit init 1

instr    1    ; NOTE TRIGGERING INSTRUMENT
  gkactive init i(gkactive) + 1    ;INCREMENT NOTE COUNTER

  if gkactive > i(gkPolyLimit) then
    turnoff
  endif

  krel release            ;IF NOTE HELD = 0, IF NOTE RELEASED = 1
  ktrig trigger krel,0.5,0    ;WHEN RELEASE FLAG CROSSES 0.5 UPWARDS, I.E. NOTE HAS BEEN RELEASED...
  if ktrig==1 then
    gkactive = gkactive - 1    ;...DECREMENT ACTIVE NOTES COUNTER
  endif

  icps = cpsmidinn(p4)
  iamp = ampdbfs(p5)
  iatt = p6
  idec = p7
  isus = p8
  irel = p9

  aenv madsr iatt, idec, isus, irel
  kfatt = iatt
  ;; kfsus ntrpol gksus, iamp, 0.8
  kfco madsr iatt,  idec, isus,  irel

  asig    vco2    0.3*iamp, icps, 0
  ispread init 0.25 ; Hz
  ichaos init 0.6
  asaw1 vco2 iamp / 4, icps - ispread, 0
  asaw2 vco2 iamp / 4, icps + ispread, 0
  klfo1 lfo 0.4, 0.05, 0
  klfo2 lfo 0.3, 0.15, 0
  anoise1 noise ichaos, 0
  anoise2 noise ichaos, 0
  asaw3 vco (iamp / 4) * anoise1, icps + klfo1, 0
  asaw4 vco (iamp / 4) * anoise2, icps + klfo2, 0
  asub vco2 iamp / 2, icps / 2, 12
  kfoldmod1 lfo 3, 0.3
  kfoldmod2 lfo 4, 0.2
  asub1 fold asub, 9.5 + kfoldmod1
  asub2 fold asub, 10.5 + kfoldmod2
  aL = asig + asub1 + asaw1 + asaw3
  aR = asig + asub2 + asaw2 + asaw4
  kfiltmod1 lfo 500, 0.2
  kfiltmod2 lfo 400, 0.1
  aL moogladder aL, 7000 + kfiltmod1, 0.3, 1
  aR moogladder aR, 7000 + kfiltmod2, 0.3, 1
  aoutL clip aL * aenv, 0, 0.7
  aoutR clip aR * aenv, 0, 0.7
  outs aoutL, aoutR
endin
