opcode PanaeolusWubBank(hz:k, cutoff:k, resonance:k, drive:k, pulse:k, detune:k):(a,a)
  oversample 4, 4, 4
  ;; The linear control converter starts at zero on the first sub-block.
  gain:k = max(1, drive)
  sawL:a vco2 0.35, hz * cent(-detune * 0.5), 0
  sawR:a vco2 0.35, hz * cent(detune * 0.5), 0
  squareL:a vco2 0.25, hz * cent(detune * 0.17), 2, pulse
  squareR:a vco2 0.25, hz * cent(-detune * 0.17), 2, pulse
  sourceL:a = tanh((sawL + squareL) * gain) / tanh(gain)
  sourceR:a = tanh((sawR + squareR) * gain) / tanh(gain)
  ;; moogladder's nonlinear model uses 0dbfs internally. Normalize its input
  ;; and output here so the final dB control remains a pure gain change.
  filterL:a moogladder sourceL * 0dbfs, min(cutoff, sr * 0.1), resonance
  filterR:a moogladder sourceR * 0dbfs, min(cutoff, sr * 0.1), resonance
  rawL:a butterhp filterL / 0dbfs, 100
  rawR:a butterhp filterR / 0dbfs, 100
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

PanaeolusWubTrim@global:i init -3.179

opcode PanaeolusWub(note:k, db:k, cutoff:k, sweep:k, resonance:k, drive:k, pulse:k, bpm:k, division:k, phase:k, sharpness:k, gate:k, detune:k, sub:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  cutoff = PanaeolusSmooth(cutoff, 0.03, 0)
  sweep = PanaeolusSmooth(sweep, 0.03, 0)
  resonance = PanaeolusSmooth(resonance, 0.03, 0)
  drive = PanaeolusSmooth(drive, 0.03, 0)
  pulse = PanaeolusSmooth(pulse, 0.03, 0)
  sharpness = PanaeolusSmooth(sharpness, 0.03, 0)
  gate = PanaeolusSmooth(gate, 0.03, 0)
  detune = PanaeolusSmooth(detune, 0.03, 0)
  sub = PanaeolusSmooth(sub, 0.03, 0)

  movement:k = PanaeolusBassPulse(bpm, division, phase, sharpness)
  frequency:k = PanaeolusSmooth(min(cutoff * pow(2, sweep * movement), sr * 0.4), 0.006, 0)
  pump:k = PanaeolusSmooth(1 - gate + gate * movement, 0.004, 0)
  rawL:a, rawR:a = PanaeolusWubBank(hz, frequency, resonance, drive, pulse, detune)
  cleanL:a dcblock2 rawL
  cleanR:a dcblock2 rawR
  low:a poscil 0.5 * sub, hz
  left:a = (cleanL + low) * pump / (1 + sub * 0.5)
  right:a = (cleanR + low) * pump / (1 + sub * 0.5)
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusWubTrim)
  xout outputL, outputR
endop
