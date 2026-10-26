;; Redesigned from the legacy voice concept; no shared mutable voice tables.
PanaeolusNuclearTrim@global:i init -2.929

opcode PanaeolusNuclear(note:k, db:k, detune:k, cutoff:k, resonance:k, sub:k, width:k, drive:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  spread:k = PanaeolusSmooth(detune, 0.01, 0)
  ratio:k = 2 ^ (spread / 1200)
  a1:a vco2 0.35, hz, 0, 0.5, 0
  a2:a vco2 0.25, min(hz * ratio, sr * 0.45), 0, 0.5, 0.2
  a3:a vco2 0.25, hz / ratio, 0, 0.5, 0.6
  low:a vco2 sub * 0.4, hz / 2, 12, 0.5, 0.1
  l:a moogladder tanh((a1 + a2 + low) * drive) / tanh(drive), min(PanaeolusSmooth(cutoff, 0.01, 0), sr * 0.4), PanaeolusSmooth(resonance, 0.01, 0)
  r:a moogladder tanh((a1 + a3 + low) * drive) / tanh(drive), min(PanaeolusSmooth(cutoff, 0.01, 0), sr * 0.4), PanaeolusSmooth(resonance, 0.01, 0)
  left:a = (l + r) * 0.5 + (l - r) * width * 0.5
  right:a = (l + r) * 0.5 - (l - r) * width * 0.5
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusNuclearTrim)
  xout outputL, outputR
endop
