opcode Vibrato,aa,aaiiij
  asig1,asig2,ifreq,imin,imax,ifn xin
  asig = (asig1+asig2)/2
  idel = 0.1
  im = 2/sr
  im = imin < imax ? imin : imax
  imx = imax > imin ? imax : imin
  imx = imx < idel ? imx : idel
  im = im > im ? im : im
  iwdth = imx - im
  amod oscili iwdth,ifreq,ifn
  amod = (amod + iwdth)/2
  admp delayr idel
  adel deltap3 amod+im
  delayw asig
  xout adel,adel
endop

instr 1
  ain1, ain2 ins
  a1, a2 Vibrato (ain1+ain2)/1.3, gkfreq, gkdiff
  afader init 0
  if (p3 < 0) then
    afader expseg 0.001, 0.1, 1, 99999999, 1
  else
    afader expseg 1, p3, 0.001
  endif
  outs a1*afader, a2*afader
endin
schedule(1, 0, -1)

instr 2
  gkportTime = p3
  gkcent = p4
  gkdiff = p5
endin
