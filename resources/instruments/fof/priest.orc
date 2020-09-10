giSine ftgen 1, 0, 65537, 10, 1
giSigmoid ftgen  2, 0, 1024, 19, 0.5, 0.5, 270, 0.5
;;Electric priest from Tobias Enhus
;   strt 	dur 	form	pitch	p6;formant attack p7; Amp attack

giZeroDB = 32767

;-------------------------------------------------
; ********** The Electric Priest ***" Talking "***
;-------------------------------------------------
instr 1
  inote = cpsmidinn(p4)
  iamp = ampdb(p5)
  ienv = p6
  imorph = p7
  iattack = p8
  print p3
  k2 	linseg  	0, p3*.9, 0, p3*.1, 1  			; octaviation coefficient
  a1	oscili 	7, .15,1					;Rubato for vibrato
  a3	linen		a1, (p3-p3*.05), p3, .2			;delay for vibrato
  a2	oscili 	a3, 5,1					;vibrato
  a5	linen 1250/giZeroDB, iattack, p3, (p3*.1)			;amp envelope

  a21 line 456, imorph, 1030					; imorph morph-time,0=instant aah
  a5 linen 10000/giZeroDB, iattack, p3, (p3*.1)			;amp envelope
  a11 fof	a5,inote+a2, a21*(ienv/100), k2, 200, .003, .017, .005, 10, 1,2, inote, 0, 1

  a31	line	4000, imorph, 6845
  a32	line	2471/giZeroDB, imorph, 1370/giZeroDB
  a6	linen		a31, iattack, p3, (p3*.1)			;amp envelope
  a12 fof      a6,inote+a2, a32*(ienv/100), k2, 200, .003, .017, .005, 20, 1,2, inote, 0, 1

  a41	line	2813, imorph, 3170
  a42	line	1650/giZeroDB, imorph, 1845/giZeroDB
  a7	linen		a42, iattack, p3, (p3*.1)			;amp envelope
  a13 fof a7,inote+a2, a41*(ienv/100), k2, 200, .003, .017, .005, 20, 1,2, inote, 0, 1

  a71	line	1347/giZeroDB, imorph, 1726/giZeroDB 	;amp line
  a72	line	3839, imorph, 3797	; form line
  a8	linen		a71, iattack, p3, (p3*.1)			;amp envelope
  a14 fof      a8,inote+a2, a72*(ienv/100), k2, 200, .003, .017, .005, 30, 1,2, inote, 0, 1

  a51	line	1, imorph, 1250/giZeroDB
  a9	linen		a51, iattack, p3, (p3*.1)			;amp envelope
  a15 fof      a5,inote+a2, 4177*(ienv/100), k2, 200, .003, .017, .005, 30, 1,2, inote, 0, 1

  ;; a61	line	1, imorph, 5833
  a10	linen		a51, iattack, p3, (p3*.1)			;amp envelope
  a16 fof      a10,inote+a2,  428*(ienv/100), k2, 200, .003, .017, .005, 10, 1,2, inote, 0, 1
  a7 =        (a11 + a12 + a13 + a14 + a15 + a16) * iamp
  outs  a7*.09,a7*.06
endin

;------------------------------------------
; ********** The Electric Priest ***Aaah**
;------------------------------------------
instr 2
  inote = cpsmidinn(p4)
  iamp = ampdb(p5)
  ienv = p6
  imorph = p7
  iattack = p8

  k2 linseg 0, p3*.9, 0, p3*.1, 1  			; octaviation coefficient
  a1 oscili 5, .12,1					;Rubato for vibrato
  a3 linen a1, (p3-(p3*.02)), (p3-(p3*.78)), .2*p3      ;delay for vibrato
  a2 oscili a3, 4,1					;vibrato
  a4 linen (ienv*.4), imorph, p3, (p3*.05)		;format env shape
  a5 linen 1250/giZeroDB, iattack, p3, (p3*.15)			;amp envelope

  a21 oscili (2/giZeroDB), 10,1
  a5 linen (9998/giZeroDB)+a21, iattack, p3, (p3*.1)			;amp envelope
  a11 fof a5,inote+a2*.5, a4+1030*(ienv/100), k2, 200, .003, .017, .005, 10, 1,2, inote, 0, 1

  a22 oscili (2/giZeroDB), 2,1
  a6 linen (6843/giZeroDB)+a22, iattack, p3, (p3*.1)			;amp envelope
  a12 fof a6,inote+a2*.5, a4+1370*(ienv/100), k2, 200, .003, .017, .005, 20, 1,2, inote, 0, 1

  ;;a23	oscili 	1, 12,1
  a7	linen (1845/giZeroDB), iattack, p3, (p3*.1)			;amp envelope
  a13 fof a7,inote+a2*.5, a4+3170*(ienv/100), k2, 200, .003, .017, .005, 20, 1,2, inote, 0, 1

  a24 oscili (2/giZeroDB), 5,1
  a8 linen (1726/giZeroDB)+a24, iattack, p3, (p3*.1)			;amp envelope
  a14 fof a8,inote+a2*.5, a4+3797*(ienv/100), k2, 200, .003, .017, .005, 30, 1,2, inote, 0, 1

  a25 oscili (3/giZeroDB), 4,1
  a9 linen (1250/giZeroDB)+a25, iattack, p3, (p3*.1)			;amp envelope
  a15 fof a5,inote+a2*.5, a4+4177*(ienv/100), k2, 200, .003, .017, .005, 30, 1,2, inote, 0, 1


  a26	oscili 	(3/giZeroDB), 6,1
  a10	linen		(5833/giZeroDB)+a26, iattack, p3, (p3*.1)			;amp envelope
  a16 fof      a10,inote+a2*.5,  a4+428*(ienv/100), k2, 200, .003, .017, .005, 10, 1,2, inote, 0, 1
  a7 =        (a11 + a12 + a13 + a14 + a15 + a16) * iamp
  outs  a7*imorph*0.1,a7*(1-imorph)*0.1
endin


;-------------------------------------------------
; ********** The Electric Priest ***" gliss "***
;-------------------------------------------------
instr 3
  inote = cpsmidinn(p4)
  iamp = ampdb(p5)
  ienv = p6
  imorph = p7
  iattack = p8

  k2 	linseg  	0, p3*.9, 0, p3*.1, 1  			; octaviation coefficient
  a1	oscili 	7, .15,1					;Rubato for vibrato
  a3	linen		a1, (p3-p3*.05), p3, .2			;delay for vibrato
  a2	oscili 	a3, 5,1					;vibrato
  a5	linen		1250/giZeroDB, iattack, p3, (p3*.1)			;amp envelope
  a90	oscili 	30, .15625,1					;gliss: 1 cycle per 4 bars if 0.4sec =1 quarternote

  a21	line	456, imorph, 1030					; imorph: morph-time,0=instant aah
    a5	linen		10000/giZeroDB, iattack, p3, (p3*.1)			;amp envelope
    a11 fof	a5,inote+a2+a90, a21*(ienv/100), k2, 200, .003, .017, .005, 10, 1,2, inote, 0, 1

    a31	line	4000/giZeroDB, imorph, 6845/giZeroDB
    a32	line	2471, imorph, 1370
    a6	linen		a31, iattack, p3, (p3*.1)			;amp envelope
    a12 fof      a6,inote+a2+a90, a32*(ienv/100), k2, 200, .003, .017, .005, 20, 1,2, inote, 0, 1

    a41	line	2813, imorph, 3170
    a42	line	1650/giZeroDB, imorph, 1845/giZeroDB
    a7	linen		a42, iattack, p3, (p3*.1)			;amp envelope
    a13 fof      a7,inote+a2+a90, a41*(ienv/100), k2, 200, .003, .017, .005, 20, 1,2, inote, 0, 1

    a71	line	1347/giZeroDB, imorph, 1726/giZeroDB 	;amp line
    a72	line	3839, imorph, 3797	; form line
    a8	linen		a71, iattack, p3, (p3*.1)			;amp envelope
    a14 fof      a8,inote+a2+a90, a72*(ienv/100), k2, 200, .003, .017, .005, 30, 1,2, inote, 0, 1

    a51	line	1/giZeroDB, imorph, 1250/giZeroDB
    a9	linen		a51, iattack, p3, (p3*.1)			;amp envelope
    a15 fof      a5,inote+a2+a90, 4177*(ienv/100), k2, 200, .003, .017, .005, 30, 1,2, inote, 0, 1

    ;; a61	line	1, imorph, 5833
    a10	linen		a51, iattack, p3, (p3*.1)			;amp envelope
    a16 fof      a10,inote+a2+a90,  428*(ienv/100), k2, 200, .003, .017, .005, 10, 1,2, inote, 0, 1
    a7 =        (a11 + a12 + a13 + a14 + a15 + a16) * iamp

    outs  a7*.09,a7*.06
endin

instr 4
  imode = p9
  instrNumber init 1
  if (imode <= 0) then
    instrNumber = 1
  elseif (imode == 1) then
    instrNumber = 2
  elseif (imode >= 2) then
    instrNumber = 3
  endif

  ;; Simple event forwarding based on a given mode value
  event_i("i", instrNumber, 0, p3, p4, p5, p6, p7, p8)
endin
