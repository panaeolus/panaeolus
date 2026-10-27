PanaeolusHorizonTrim@global:i init -2.187

opcode PanaeolusHorizon(note:k, db:k, detune:k, drift:k, color:k, motion:k, rate:k, cutoff:k, width:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  detune = PanaeolusSmooth(detune, 0.03, 0)
  drift = PanaeolusSmooth(drift, 0.03, 0)
  color = PanaeolusSmooth(color, 0.03, 0)
  motion = PanaeolusSmooth(motion, 0.03, 0)
  cutoff = PanaeolusSmooth(cutoff, 0.03, 0)
  width = PanaeolusSmooth(width, 0.03, 0)

  driftL:k poscil drift, rate
  driftR:k poscil drift, rate * 1.173, -1, 0.25
  sway:k poscil motion, rate * 0.713
  sawL:a vco2 0.4, hz * cent(-detune * 0.5 + driftL), 0, 0.5, 0.1
  sawR:a vco2 0.4, hz * cent(detune * 0.5 + driftR), 0, 0.5, 0.1
  triL:a vco2 0.4, hz * cent(detune * 0.23 - driftL), 12, 0.5, 0.3
  triR:a vco2 0.4, hz * cent(-detune * 0.23 - driftR), 12, 0.5, 0.3
  root:a poscil 0.2, hz
  sourceL:a = sawL * color + triL * (1 - color) + root
  sourceR:a = sawR * color + triR * (1 - color) + root
  filteredL:a butterlp sourceL, min(cutoff * pow(2, sway), sr * 0.4)
  filteredR:a butterlp sourceR, min(cutoff * pow(2, -sway), sr * 0.4)
  mid:a = (filteredL + filteredR) * 0.5
  side:a = (filteredL - filteredR) * width * 0.5
  left:a = mid + side
  right:a = mid - side
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusHorizonTrim)
  xout outputL, outputR
endop
