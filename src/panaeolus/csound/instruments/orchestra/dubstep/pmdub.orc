;; sent to me by the legendary Rory McCurdy (Hlöðver May 2019)

opcode PMOp,a,aiiiiijo
  aphs init 0
  acc, iamp,ifr,isemi,irecur,iratio,ifn,indx xin
  aph phasor ifr

  itab ftgentmp 0, 0, 4097, 9, 1, 1, 1, 1.5, 0 + indx * 0.1, 1
  ienvTab  ftgentmp 0, 0, 4096, -25, 0, 0.0001, 100, iamp/(indx + 1), 101 + (3000 * (1/(indx + 1))), iamp/(indx + 1), 4096, 0.01

  if ( indx == ( irecur - 1) || ifr >= sr/2 ) goto skip
  acc PMOp acc, iamp, ifr, isemi, irecur, iratio, itab, indx + 1
  skip:
    kEnvPhs = phasor(1/abs(p3))
    kEnv tablei kEnvPhs * ftlen(ienvTab), ienvTab, 0, 0, 0
    kfactor = semitone(isemi)
    kfreqMod poscil kfactor, ifr*(iratio),-1, 0
    kfreqMod abs kfreqMod
    aphs = aph * kfreqMod
    a1 tablei aphs+acc/(2*$M_PI),itab,1,0,1
    acc += a1
    xout acc * kEnv
endop

instr 1
  ifreq      = cpsmidinn(p4)
  iamp       = ampdbfs(p5)
  idepth     = p6
  iratio     = p7
  acc init 0
  aSig PMOp acc, 1, p4, idepth, 5, iratio
  aSig *= (iamp * 0.05 )
  outs aSig, aSig
endin
