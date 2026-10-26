opcode PanaeolusUndertowBank(hz:k, index:k, ratio:k, motion:k, rate:k, detune:k, sub:k):(a,a)
  oversample 4, 4, 4
  movement:k poscil motion * 0.45, rate
  amount:k = index * (1 - motion * 0.45 + movement)
  safe:k = min(amount, max(0, sr * 0.35 / max(1, hz * ratio * cent(detune * 0.5)) - 1))
  hzL:k = hz * cent(-detune * 0.5)
  hzR:k = hz * cent(detune * 0.5)
  modL:a poscil 1, hzL * ratio
  modR:a poscil 1, hzR * ratio
  phaseL:a phasor hzL
  phaseR:a phasor hzR
  low:a poscil sub, hz * 0.5
  rawL:a = (sin(phaseL * 6.283185307179586 + modL * safe) + low) / (1 + sub)
  rawR:a = (sin(phaseR * 6.283185307179586 + modR * safe) + low) / (1 + sub)
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

PanaeolusUndertowTrim@global:i init -11.706

opcode PanaeolusUndertow(note:k, db:k, index:k, ratio:k, motion:k, rate:k, detune:k, sub:k, cutoff:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  index = PanaeolusSmooth(index, 0.03, 0)
  ratio = PanaeolusSmooth(ratio, 0.03, 0)
  motion = PanaeolusSmooth(motion, 0.03, 0)
  detune = PanaeolusSmooth(detune, 0.03, 0)
  sub = PanaeolusSmooth(sub, 0.03, 0)
  cutoff = PanaeolusSmooth(cutoff, 0.03, 0)

  rawL:a, rawR:a = PanaeolusUndertowBank(hz, index, ratio, motion, rate, detune, sub)
  cleanL:a dcblock2 rawL
  cleanR:a dcblock2 rawR
  left:a butterlp cleanL, min(cutoff, sr * 0.4)
  right:a butterlp cleanR, min(cutoff, sr * 0.4)
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusUndertowTrim)
  xout outputL, outputR
endop
