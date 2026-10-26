;; Redesigned from the legacy voice concept; no shared mutable voice tables.
PanaeolusTB303Trim@global:i init -7.969

opcode PanaeolusTB303(note:k, db:k, wave:k, cutoff:k, resonance:k, env:k, envDecay:k, accent:k, drive:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  saw:a vco2 0.7, hz, 0
  square:a vco2 0.7, hz, 10
  age:k timeinsts
  fall:k = exp(-age / envDecay)
  frequency:k = min(cutoff * 2 ^ (env * fall * (1 + accent * 0.5)), sr * 0.4)
  filtered:a moogladder saw * (1 - wave) + square * wave, PanaeolusSmooth(frequency, 0.003, 0), resonance
  left:a = tanh(filtered * drive * (1 + accent * fall)) / tanh(drive)
  right:a = left
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusTB303Trim)
  xout outputL, outputR
endop
