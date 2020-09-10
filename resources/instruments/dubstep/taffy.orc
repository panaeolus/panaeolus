; from Giampaolo Guiducci @gosub

; port of «wavetable bass» by snappizz
; which in turn is inspired by
; the formula parser of Serum
; http://sccode.org/1-57b


seed 0

#define PI #3.14159265#
giframes = 256
gisamples = 2048

giTaffyBuffers[] init giframes
giTriangleBuffers[] init giframes
giHarmonicBuffers[] init giframes
giBrassyBuffers[] init giframes
giSawSineBuffers[] init giframes
giKickDrumBuffers[] init giframes
giMidpointBuffers[] init giframes

; lo-fi triangle
; { |x, z| round((z * 14 + 2) * x.abs) / (z * 7 + 1) - 1 }
opcode lofiTriangle,i,ii
  ix,iz xin
  ival = round((iz*14+2) * abs(ix)) / (iz*7+1) -1
  xout ival
endop

; harmonic sync
; { |x, z| var w = (x + 1) / 2; sin(w * pi) * sin(w * pi * (62 * z * z * z + 2)) }
opcode harmonicSync,i,ii
  ix,iz xin
  iw = (ix+1)/2
  ival = sin(iw*$PI) * sin(iw * $PI * (62*iz*iz*iz+2))
  xout ival
endop

; brassy
; { |x, z| sin(pi * x.sign * x.abs.pow((1 - z + 0.1) * pi * pi)) }
opcode brassy,i,ii
  ix,iz xin
  ival = sin($PI * signum(ix) * pow(abs(ix), (1-iz+0.1) * $PI * $PI))
  xout ival
endop

; saw/sine reveal
; { |x, z| if(x + 1 > (z * 2), x, sin(x * pi)) }
opcode sawSineReveal,i,ii
  ix,iz xin
  if ix + 1 > iz * 2 then
    ival = ix
  else
    ival = sin(ix*$PI)
  endif
  xout ival
endop

; i can has kickdrum
; { |x, z| sin(pi * z * z * 32 * log10(x + 1.1)) }
opcode iCanHasKickdrum,i,ii
  ix,iz xin
  ival = sin($PI * iz * iz * 32 * log10(ix + 1.1))
  xout ival
endop

; midpoint mischief
; { |x, z| 0.5 * cos(x * 0.5pi) * (x.sin * pi + ((1 - z) * sin(z * pow(x * x, z) * 32pi))) }
opcode midpointMischief,i,ii
  ix,iz xin
  if ix==0 && iz==0 then
    ip = 0
  else
    ip = pow(ix*ix,iz)
  endif
  ival = 0.5 * cos(ix * 0.5 * $PI) * (sin(ix) * $PI  + ((1 - iz) * sin(iz * ip * 32 * $PI)))
  xout ival
endop

; taffy
; {|x, z| sin(x*2pi) * cos(x*pi) * cos(z*pi*pi*(abs(pow(x*2, 3)-1)))}
opcode taffy,i,ii
  ix,iz xin
  ival = sin(ix*2*$PI) * cos(ix*$PI) * cos(iz * $PI * $PI * (abs(pow:i(ix*2, 3) - 1)))
  xout ival
endop


opcode midiratio,k,k
  kmidi xin
  kratio = pow:k(pow:k(2, 1/12), kmidi)
  xout kratio
endop


opcode clippin,k,kii
  kval, imin, imax xin
  if kval < imin then
    kval = imin
  elseif kval > imax then
    kval = imax
  endif
  xout kval
endop


; pow(d/c, (x-a)/(b-a)) * c
opcode linexp,k,kkkkk
  kx, ka, kb, kc, kd xin
  kres = pow(kd/kc, (kx-ka)/(kb-ka)) * kc
  xout kres
endop


instr 1
  iframe = 0
  until iframe == giframes do
  isample = 0
  giTaffyBuffers[iframe] = ftgen(0, 0, gisamples, 10, 0)
  until isample == gisamples do
  iz = iframe/(giframes-1)
  ix = isample/(gisamples-1)
  ival taffy ix, iz ; try different formulas: lofiTriangle,harmonicSync, brassy, sawSineReveal, iCanHasKickdrum, midpointMischief
    tablew ival, isample, giTaffyBuffers[iframe], 0
    isample += 1
  od
  iframe += 1
  od
endin

instr 2
  iframe = 0
  until iframe == giframes do
  isample = 0
  giTriangleBuffers[iframe] = ftgen(0, 0, gisamples, 10, 0)
  until isample == gisamples do
  iz = iframe/(giframes-1)
  ix = isample/(gisamples-1)
  ival lofiTriangle ix, iz ; try different formulas: lofiTriangle,harmonicSync, brassy, sawSineReveal, iCanHasKickdrum, midpointMischief
    tablew ival, isample, giTriangleBuffers[iframe], 0
    isample += 1
  od
  iframe += 1
  od
endin

instr 3
  iframe = 0
  until iframe == giframes do
  isample = 0
  giHarmonicBuffers[iframe] = ftgen(0, 0, gisamples, 10, 0)
  until isample == gisamples do
  iz = iframe/(giframes-1)
  ix = isample/(gisamples-1)
  ival harmonicSync ix, iz ; try different formulas: lofiTriangle,harmonicSync, brassy, sawSineReveal, iCanHasKickdrum, midpointMischief
    tablew ival, isample, giHarmonicBuffers[iframe], 0
    isample += 1
  od
  iframe += 1
  od
endin

instr 4
  iframe = 0
  until iframe == giframes do
  isample = 0
  giBrassyBuffers[iframe] = ftgen(0, 0, gisamples, 10, 0)
  until isample == gisamples do
  iz = iframe/(giframes-1)
  ix = isample/(gisamples-1)
  ival brassy ix, iz ; try different formulas: lofiTriangle,harmonicSync, brassy, sawSineReveal, iCanHasKickdrum, midpointMischief
    tablew ival, isample, giBrassyBuffers[iframe], 0
    isample += 1
  od
  iframe += 1
  od
endin

instr 5
  iframe = 0
  until iframe == giframes do
  isample = 0
  giSawSineBuffers[iframe] = ftgen(0, 0, gisamples, 10, 0)
  until isample == gisamples do
  iz = iframe/(giframes-1)
  ix = isample/(gisamples-1)
  ival sawSineReveal ix, iz ; try different formulas: lofiTriangle,harmonicSync, brassy, sawSineReveal, iCanHasKickdrum, midpointMischief
    tablew ival, isample, giSawSineBuffers[iframe], 0
    isample += 1
  od
  iframe += 1
  od
endin

instr 6
  iframe = 0
  until iframe == giframes do
  isample = 0
  giKickDrumBuffers[iframe] = ftgen(0, 0, gisamples, 10, 0)
  until isample == gisamples do
  iz = iframe/(giframes-1)
  ix = isample/(gisamples-1)
  ival iCanHasKickdrum ix, iz ; try different formulas: lofiTriangle,harmonicSync, brassy, sawSineReveal, iCanHasKickdrum, midpointMischief
    tablew ival, isample, giKickDrumBuffers[iframe], 0
    isample += 1
  od
  iframe += 1
  od
endin

instr 7
  iframe = 0
  until iframe == giframes do
  isample = 0
  giMidpointBuffers[iframe] = ftgen(0, 0, gisamples, 10, 0)
  until isample == gisamples do
  iz = iframe/(giframes-1)
  ix = isample/(gisamples-1)
  ival midpointMischief ix, iz ; try different formulas: lofiTriangle,harmonicSync, brassy, sawSineReveal, iCanHasKickdrum, midpointMischief
    tablew ival, isample, giMidpointBuffers[iframe], 0
    isample += 1
  od
  iframe += 1
  od
endin


;; opcode taffyTable,0,iiio
;;   isize,istartnum,iamount,inum xin
;;   if (inum < iamount) then
;;     gitbl ftgen inum+istartnum, 0, isize, -2, 0
;;     iz = inum / iamount
  ;;     indx = 0
  ;;     while (indx < isize) do
  ;;       ix = indx / isize
  ;;     tablew sin:i(ix * (2*$M_PI)) * cos:i(ix * $M_PI) * \
  ;;                     cos:i(iz * $M_PI * $M_PI * (abs:i(pow:i(ix * 2, 3) - 1))), \
  ;;                     indx, gitbl, 0, 0, 1
  ;;       indx += 1
  ;;     od
  ;;     taffyTable isize, istartnum, iamount, inum + 1
  ;;   endif
;; endop

;; instr 2
  ;;   isize = 1024
  ;;   istartnum = 1000
  ;;   iamount = 513
  ;;   taffyTable isize, istartnum, iamount
;; endin

event_i("i", 1, 0, 0)
event_i("i", 2, 0, 0)
event_i("i", 3, 0, 0)
event_i("i", 4, 0, 0)
event_i("i", 5, 0, 0)
event_i("i", 6, 0, 0)
event_i("i", 7, 0, 0)

;; giTaffyBuffers[] init giframes
;; giTriangleBuffers[] init giframes
;; giHarmonicBuffers[] init giframes
;; giBrassyBuffers[] init giframes
;; giSawSineBuffers[] init giframes
;; giKickDrumBuffers[] init giframes
;; giMidpointBuffers[] init giframes


instr 8
  ;; ioffset = 1000
  ;; iamount = 512
  ;; idepth = p7
  ;; iwobbles = p6
  ;; ires = p9

  itype = p6
  iwobbles = p7
  ilpf = p8

  itab[] init giframes

  if (itype == 1) then
    itab = giTriangleBuffers
  elseif (itype == 2) then
    itab = giTriangleBuffers
  elseif (itype == 3) then
    itab = giHarmonicBuffers
  elseif (itype == 4) then
    itab = giBrassyBuffers
  elseif (itype == 5) then
    itab = giSawSineBuffers
  elseif (itype == 6) then
    itab = giKickDrumBuffers
  elseif (itype == 7) then
    itab = giMidpointBuffers
  else
    itab = giTaffyBuffers
  endif

  ;; ktable poscil idepth * giframes, (iwobbles/p3) * 0.5 , -1
  ;; ktable abs ktable
  ;; afreq  phasor cpsmidinn(p4)
  ;; asig tablexkt afreq, itab[ktable], 0, 32, 1, 0, 0
  ;; asub poscil ampdbfs(p5), cpsmidinn(p4)/2, -1
  ;; asum sum asig, asub
  ;; afiltered moogvcf2 asum, ilpf, ires
  ;; aenv madsr 0.1, p3-0.2, 1, 0.1
  ;; aout = aenv * afiltered * ampdbfs(p5)
  ;; outs aout, aout

  iamp = ampdbfs(p5) * 0.075
  kfreq cpsmidinn p4
  ktable phasor iwobbles/p3
  ;; ktable rspline 0, 1.0, 11, 11
  ;; ktable = pow(ktable, 3)
  ;; ktable = ktable * (giframes - 1)
  ;; ktable clippin ktable, 0, giframes-1
  ktable = ktable * (giframes-1)
  aphs1 phasor kfreq
  aphs2 phasor kfreq*midiratio:k(0.1)
  aphs3 phasor kfreq*midiratio:k(-0.1)
  aosc1 tableikt aphs1, itab[ktable], 1
  aosc2 tableikt aphs2, itab[ktable], 1
  aosc3 tableikt aphs3, itab[ktable], 1
  asig = aosc1 + aosc2 + aosc3
  asub poscil db(-3), kfreq/2
  asig = tanh((asig +asub)*1.4)
  ;; kfiltfreq rspline 0.0, 1.0, 6.3, 6.3
  ;; kfiltfreq linexp kfiltfreq, 0, 1, 400, 8000
  asig lowpass2 asig, ilpf, 1/0.8
  aenv madsr 0.1, 0.3, 0.7, 0.1
  asig = asig * aenv
  asig = asig * iamp
  outs asig, asig
endin
