;; Redesigned from the legacy voice concept; no shared mutable voice tables.
PanaeolusWobblyTrim@global:i init -6.614

opcode PanaeolusWobbly(note:k, db:k, bpm:k, division:k, depth:k, shape:k, cutoff:k, resonance:k, sub:k, drive:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  phase:k phasor bpm / (60 * division)
  sine:k = 0.5 - 0.5 * cos(2 * $M_PI * phase)
  triangle:k = 1 - abs(2 * phase - 1)
  motion:k = sine * (1 - shape) + triangle * shape
  saw:a vco2 0.6, hz, 0
  low:a vco2 sub * 0.5, hz / 2, 10
  filtered:a moogladder saw + low, min(cutoff * 2 ^ (depth * motion), sr * 0.4), resonance
  left:a = tanh(filtered * drive) / tanh(drive)
  right:a = left
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusWobblyTrim)
  xout outputL, outputR
endop
