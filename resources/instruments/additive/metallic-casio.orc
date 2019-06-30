giTab1 ftgen 0, 0, 4096, 10, .28, 1, .74, .66, .78, .48, .05, .33, .12, \
.08, .01, .54, .19, .08, .05, .16, .01, .11, .3, .02, .2

giTab2 ftgen 0, 0, 4096, 10, .86, .9, .32, .2,  0, 0, 0, 0, 0, \
             0, 0, 0, 0, .3, .5

giTab3 ftgen 0, 0, 4096, 10, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0
giTab4 ftgen 0, 0, 4096, 10, .6, .4, 1, .22, .09, .24, .02, .06, .05


instr 1
  idb       =  ampdbfs(p5)
  isc       =  idb*.333
  ifreq = cpsmidinn(p4)
  ifreq2 = ifreq * p6

  imode init giTab1
  if (p7 == 1) then
    imode = giTab2
  elseif (p7 == 2) then
    imode = giTab3
  elseif (p7 == 3) then
    imode = giTab4
  else
    imode = giTab1
  endif

  k1        line      100,p3,1000
  k2        line      1000,p3,100
  ;; k3        linen     isc,iattack,p3,idecay
  k4        line      1000,p3,50
  k5        line      50,p3,1000
  ;; k6        linen     isc,iattack,max:i(0, p3 - (iattack + idecay)),idecay
  adeclick linseg 0, 0.02, idb, p3 - 0.05, idb, 0.02, 0, 0.01, 0
  aenv expon 1, p3/10, 0.01
  adeclick *= aenv

  a5        poscil     adeclick,ifreq,imode
  a6        poscil     adeclick,ifreq*.999,imode
  a7        poscil     adeclick,ifreq*1.001,imode
  a1        =  a5+a6+a7
  a8        poscil     adeclick,ifreq2,imode
  a9        poscil     adeclick,ifreq2*.999,imode
  a10       poscil     adeclick,ifreq2*1.001,imode
  a11       =  a8+a9+a10
  a2        butterbp  a1,k1,50
  a3        butterbr  a2,k4,25
  a4        balance   a3,a1
  a12       butterbp  a11,k2,50
  a13       butterbr  a12,k5,25
  a14       balance   a13,a11
  outs      a4/10, a14/10
endin
