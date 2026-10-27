opcode PanaeolusReeseBank(hz:k, detune:k, color:k, notch:k, depth:k, motion:k, rate:k, drive:k, cutoff:k):(a,a)
  oversample 4, 4, 4
  ;; The linear control converter starts at zero on the first sub-block.
  gain:k = max(1, drive)
  movement:k poscil motion, rate
  sawL:a vco2 0.5, hz * cent(-detune * 0.5), 0, 0.5, 0
  sawR:a vco2 0.5, hz * cent(detune * 0.5), 0, 0.5, 0
  pulseL:a vco2 0.5, hz * cent(detune * 0.17), 2, 0.37, 0.2
  pulseR:a vco2 0.5, hz * cent(-detune * 0.17), 2, 0.63, 0.2
  sourceL:a = tanh((sawL * (1 - color) + pulseL * color) * gain) / tanh(gain)
  sourceR:a = tanh((sawR * (1 - color) + pulseR * color) * gain) / tanh(gain)
  rejectL:a butterbr sourceL, min(notch * pow(2, movement), sr * 0.1), max(40, notch * 0.35)
  rejectR:a butterbr sourceR, min(notch * pow(2, -movement), sr * 0.1), max(40, notch * 0.35)
  upperL:a butterhp sourceL * (1 - depth) + rejectL * depth, 120
  upperR:a butterhp sourceR * (1 - depth) + rejectR * depth, 120
  rawL:a butterlp upperL, min(cutoff, sr * 0.1)
  rawR:a butterlp upperR, min(cutoff, sr * 0.1)
  ;; Filter at the high rate before the linear output converter decimates.
  aaL1:a butterlp rawL, sr * 0.1
  aaL2:a butterlp aaL1, sr * 0.1
  aaL3:a butterlp aaL2, sr * 0.1
  aaL4:a butterlp aaL3, sr * 0.1
  aaR1:a butterlp rawR, sr * 0.1
  aaR2:a butterlp aaR1, sr * 0.1
  aaR3:a butterlp aaR2, sr * 0.1
  aaR4:a butterlp aaR3, sr * 0.1
  xout aaL4, aaR4
endop

PanaeolusReeseTrim@global:i init -9.182

opcode PanaeolusReese(note:k, db:k, detune:k, color:k, notch:k, depth:k, motion:k, rate:k, drive:k, cutoff:k, sub:k, width:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  detune = PanaeolusSmooth(detune, 0.03, 0)
  color = PanaeolusSmooth(color, 0.03, 0)
  notch = PanaeolusSmooth(notch, 0.03, 0)
  depth = PanaeolusSmooth(depth, 0.03, 0)
  motion = PanaeolusSmooth(motion, 0.03, 0)
  drive = PanaeolusSmooth(drive, 0.03, 0)
  cutoff = PanaeolusSmooth(cutoff, 0.03, 0)
  sub = PanaeolusSmooth(sub, 0.03, 0)
  width = PanaeolusSmooth(width, 0.03, 0)

  rawL:a, rawR:a = PanaeolusReeseBank(hz, detune, color, notch, depth, motion, rate, drive, cutoff)
  cleanL:a dcblock2 rawL
  cleanR:a dcblock2 rawR
  low:a poscil 0.5 * sub, hz
  mid:a = (cleanL + cleanR) * 0.5
  side:a = (cleanL - cleanR) * width * 0.5
  left:a = (mid + side + low) / (1 + sub * 0.5)
  right:a = (mid - side + low) / (1 + sub * 0.5)
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusReeseTrim)
  xout outputL, outputR
endop
