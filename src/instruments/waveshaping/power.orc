opcode PanaeolusPowerShape(hz:k, curve:k, bias:k, rectify:k, body:k, motion:k, rate:k, width:k):(a,a)
  ;; Use the same linear converter on native and browser engines. Filtering
  ;; before downsampling is explicit; some builds have no sinc converter.
  oversample 4, 4, 4
  movement:k poscil motion, rate
  exponent:k = limit(curve * pow(2, movement), 0.25, 4)
  hzL:k = hz * cent(-width * 0.5)
  sourceL:a poscil 1, hzL
  inputL:a = (sourceL + bias) / (1 + abs(bias))
  powerL:a powershape inputL, exponent, 1
  shapedL:a = powerL * (1 - rectify) + abs(powerL) * rectify
  outL:a = shapedL * (1 - body) + sourceL * body
  hzR:k = hz * cent(width * 0.5)
  sourceR:a poscil 1, hzR
  inputR:a = (sourceR + bias) / (1 + abs(bias))
  powerR:a powershape inputR, exponent, 1
  shapedR:a = powerR * (1 - rectify) + abs(powerR) * rectify
  outR:a = shapedR * (1 - body) + sourceR * body
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

PanaeolusPowerTrim@global:i init -13.656

opcode PanaeolusPower(note:k, db:k, curve:k, bias:k, rectify:k, body:k, motion:k, rate:k, width:k, cutoff:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  curve = PanaeolusSmooth(curve, 0.02, 0)
  bias = PanaeolusSmooth(bias, 0.02, 2)
  rectify = PanaeolusSmooth(rectify, 0.02, 0)
  body = PanaeolusSmooth(body, 0.02, 0)
  motion = PanaeolusSmooth(motion, 0.02, 0)
  width = PanaeolusSmooth(width, 0.02, 0)
  rawL:a, rawR:a = PanaeolusPowerShape(hz, curve, bias, rectify, body, motion, rate, width)
  cleanL:a dcblock2 rawL
  cleanR:a dcblock2 rawR
  tone:k = min(PanaeolusSmooth(cutoff, 0.02, 0), sr * 0.4)
  left:a butterlp cleanL, tone
  right:a butterlp cleanR, tone
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusPowerTrim)
  xout outputL, outputR
endop
