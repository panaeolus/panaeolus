opcode PanaeolusGrowlBank(hz:k, ratio:k, index:k, detune:k):(a,a)
  oversample 4, 4, 4
  safe:k = min(index, max(0, sr * 0.35 / max(1, hz * ratio * cent(detune * 0.5)) - 1))
  hzL:k = hz * cent(-detune * 0.5)
  hzR:k = hz * cent(detune * 0.5)
  modL:a poscil 1, hzL * ratio
  modR:a poscil 1, hzR * ratio
  phaseL:a phasor hzL
  phaseR:a phasor hzR
  rawL:a = sin(phaseL * 6.283185307179586 + modL * safe)
  rawR:a = sin(phaseR * 6.283185307179586 + modR * safe)
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

PanaeolusGrowlTrim@global:i init -23.258

opcode PanaeolusGrowl(note:k, db:k, vowel:k, index:k, ratio:k, bpm:k, division:k, phase:k, depth:k, sharpness:k, shift:k, detune:k, sub:k, cutoff:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  vowel = PanaeolusSmooth(vowel, 0.03, 0)
  index = PanaeolusSmooth(index, 0.03, 0)
  ratio = PanaeolusSmooth(ratio, 0.03, 0)
  depth = PanaeolusSmooth(depth, 0.03, 0)
  sharpness = PanaeolusSmooth(sharpness, 0.03, 0)
  shift = PanaeolusSmooth(shift, 0.03, 128)
  detune = PanaeolusSmooth(detune, 0.03, 0)
  sub = PanaeolusSmooth(sub, 0.03, 0)
  cutoff = PanaeolusSmooth(cutoff, 0.03, 0)

  pulse:k = PanaeolusBassPulse(bpm, division, phase, sharpness)
  mouth:k = PanaeolusSmooth(limit(vowel + depth * pulse, 0, 4), 0.008, 0)
  amount:k = index * (0.35 + 0.65 * pulse)
  rawL:a, rawR:a = PanaeolusGrowlBank(hz, ratio, amount, detune)
  vowelL:a = PanaeolusVocalFilter(rawL, mouth, shift, 1, 1)
  vowelR:a = PanaeolusVocalFilter(rawR, mouth, shift, 1, 1)
  upperL:a butterhp rawL * 0.2 + vowelL * 0.16, 120
  upperR:a butterhp rawR * 0.2 + vowelR * 0.16, 120
  toneL:a butterlp upperL, min(cutoff, sr * 0.4)
  toneR:a butterlp upperR, min(cutoff, sr * 0.4)
  cleanL:a dcblock2 toneL
  cleanR:a dcblock2 toneR
  low:a poscil 0.5 * sub, hz
  left:a = (cleanL + low) / (1 + sub * 0.5)
  right:a = (cleanR + low) / (1 + sub * 0.5)
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusGrowlTrim)
  xout outputL, outputR
endop
