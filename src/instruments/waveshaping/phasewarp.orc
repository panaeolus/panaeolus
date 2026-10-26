opcode PanaeolusPhasewarpShape(hz:k, bend:k, skew:k, sub:k, motion:k, rate:k, width:k):(a,a)
  ;; Use the same linear converter on native and browser engines. Filtering
  ;; before downsampling is explicit; some builds have no sinc converter.
  oversample 4, 4, 4
  movement:k poscil motion, rate
  pivot:k = limit(bend + movement * 0.25, -0.8, 0.8)
  hzL:k = hz * cent(-width * 0.5)
  phaseL:a phasor hzL
  bentL:a pdhalf phaseL, pivot, 0, 1
  shiftedL:a pdhalfy bentL, skew, 0, 1
  shapedL:a = cos(shiftedL * 6.283185307179586)
  subL:a poscil 1, hzL * 0.5
  outL:a = (shapedL + subL * sub) / (1 + sub)
  hzR:k = hz * cent(width * 0.5)
  phaseR:a phasor hzR
  bentR:a pdhalf phaseR, pivot, 0, 1
  shiftedR:a pdhalfy bentR, skew, 0, 1
  shapedR:a = cos(shiftedR * 6.283185307179586)
  subR:a poscil 1, hzR * 0.5
  outR:a = (shapedR + subR * sub) / (1 + sub)
  ;; sr is local: this cutoff is 0.4 times the host sample rate.
  filteredL1:a butterlp outL, sr * 0.1
  filteredL2:a butterlp filteredL1, sr * 0.1
  filteredL3:a butterlp filteredL2, sr * 0.1
  filteredL4:a butterlp filteredL3, sr * 0.1
  filteredR1:a butterlp outR, sr * 0.1
  filteredR2:a butterlp filteredR1, sr * 0.1
  filteredR3:a butterlp filteredR2, sr * 0.1
  filteredR4:a butterlp filteredR3, sr * 0.1
  xout filteredL4, filteredR4
endop

PanaeolusPhasewarpTrim@global:i init -12.687

opcode PanaeolusPhasewarp(note:k, db:k, bend:k, skew:k, sub:k, motion:k, rate:k, width:k, cutoff:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  bend = PanaeolusSmooth(bend, 0.02, 2)
  skew = PanaeolusSmooth(skew, 0.02, 2)
  sub = PanaeolusSmooth(sub, 0.02, 0)
  motion = PanaeolusSmooth(motion, 0.02, 0)
  width = PanaeolusSmooth(width, 0.02, 0)
  rawL:a, rawR:a = PanaeolusPhasewarpShape(hz, bend, skew, sub, motion, rate, width)
  cleanL:a dcblock2 rawL
  cleanR:a dcblock2 rawR
  tone:k = min(PanaeolusSmooth(cutoff, 0.02, 0), sr * 0.4)
  left:a butterlp cleanL, tone
  right:a butterlp cleanR, tone
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusPhasewarpTrim)
  xout outputL, outputR
endop
