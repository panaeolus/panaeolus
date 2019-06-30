giParabola ftgen 0, 0, 131072, 19, 0.5, 1, 180, 1
giSine ftgen 0, 0, 65537, 10, 1
giTriangle ftgen 0, 0, 131072, 7, 0,131072/2,1,131072/2,0

gkportTime init 1.0
gkrate init 5.15
gkdepth init 0.001
gkfback init 0
gkshape init 1

opcode Flanger_stereo,aa,aakkkk ;MADE BY IAIN MCCURDY
  aL,aR,krate,kdepth,kfback,klfoshape xin
  if klfoshape==1 then
    amod oscili kdepth, krate, giParabola
  elseif klfoshape==2 then
    amod oscili kdepth, krate, giSine
  amod = abs:a(amod)
  elseif klfoshape==3 then
    amod oscili kdepth, krate, giTriangle
  elseif klfoshape==4 then
    amod randomi 0, kdepth, krate,1
  else
    amod randomh 0,kdepth,krate,1
  endif

  adelsigL flanger aL, amod, kfback , 1.2
  adelsigL dcblock adelsigL
  adelsigR flanger aR, amod, kfback , 1.2
  adelsigR dcblock adelsigR

  xout adelsigL,adelsigR
endop

instr 1
  ;; quadraphonic
  ;; ain1, ain2, ain3, ain4 inq
  ;; aFL, aFR Flanger_stereo ain1, ain2, gkrate, gkdepth, gkfback, gkshape
  ;; aRL, aRR Flanger_stereo ain3, ain4, gkrate, gkdepth, gkfback, gkshape
  ;; outc aFL, aFR, aRL, aRR

  ;; stereo
  gkrate portk gkrate, gkportTime
  gkdepth portk gkdepth, gkportTime
  gkfback portk gkfback, gkportTime

  ain1, ain2 ins

  aFL, aFR Flanger_stereo ain1, ain2, gkrate, gkdepth, gkfback, gkshape

  if (p3 < 0) then
    kenv linseg	0, .05, 1,  .2, 1 ;; Fade in on initialization
  else
    kenv linseg	1, p3 - .05, 1, .05, 0 ;; Fade out on release
  endif

  outs aFL*kenv, aFR*kenv

endin

instr 2
  gkportTime = p3
  gkrate = p4
  gkdepth = p5
  gkfback = p6
  gkshape = p7
endin
