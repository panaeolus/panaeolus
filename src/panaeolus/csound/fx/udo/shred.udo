opcode shred,aa,aaikkkkiiiik
  aL,aR,\
  iMaxDelay,kTransPose,kTransRand,\
  kDepth,kRate,iFeedback,iWidth,iwet,\
  iGain,kMode xin
  ;; iFFTsizes[] fillarray 128,256,512,1024,2048,4096 ;arrayofFFTsizevalues
  iFFTsize = 4*ksmps

  fsigInL pvsanal aL,iFFTsize,iFFTsize/4,iFFTsize,1 ;FFTanalyseaudio
  fsigInR pvsanal aR,iFFTsize,iFFTsize/4,iFFTsize,1 ;FFTanalyseaudio
  fsigFB pvsinit iFFTsize ;initialisefeedbacksignal
  fsigMixL pvsmix fsigInL,fsigFB ;mixfeedbackwithinput
  fsigMixR pvsmix fsigInR,fsigFB ;mixfeedbackwithinput

  iHandle1,kTime pvsbuffer fsigMixL,i(iMaxDelay) ;createacircularfsigbuffer
  kDly1 randomh 0,iMaxDelay*kDepth,kRate,1 ;delaytime
  kTranspose1_2 random kTransPose-(2*kTransPose*kTransRand),kTransPose
  fsigOut pvsbufread kTime-kDly1,iHandle1 ;readfrombuffer
  fsigGran pvsgain fsigOut,1-iGain
  fScale pvscale fsigGran,semitone(kTranspose1_2)
  fsigFB pvsgain fScale,iFeedback ;createfeedbacksignalfornextpass
  if kMode == 1 then
    aDly pvsynth fsigGran ;resynthesisereadbufferoutput
  else
    aDly pvsynth fScale ;resynthesisereadbufferoutput
  endif
  aMix1 ntrpol aL,aDly,iwet ;dry/wetaudiomix

  iHandle2,kTime pvsbuffer fsigMixR,iMaxDelay ;createacircularfsigbuffer
  kDly2 randomh 0,iMaxDelay*kDepth,kRate,1 ;delaytime
  kTranspose2_2 random kTransPose-(2*kTransPose*kTransRand),kTransPose
  fsigOut pvsbufread kTime-kDly2,iHandle2 ;readfrombuffer
  fsigGran pvsgain fsigOut,1-iGain
  fScale pvscale fsigGran,semitone(kTranspose2_2)
  fsigFB pvsgain fScale,iFeedback ;createfeedbacksignalfornextpass
  if kMode == 1 then
    aDly pvsynth fsigGran ;resynthesisereadbufferoutput
  else
    aDly pvsynth fScale ;resynthesisereadbufferoutput
  endif
  aMix2 ntrpol aR,aDly,iwet ;dry/wetaudiomix
  xout aMix2+aMix1*(1-iWidth),aMix2*(1-iWidth)+aMix1
endop

