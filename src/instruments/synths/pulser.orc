;; Redesigned from the legacy voice concept; no shared mutable voice tables.
PanaeolusPulserTrim@global:i init 4.396

opcode PanaeolusPulser(note:k, db:k, harmonics:k, cutoff:k, resonance:k, env:k, envDecay:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  count:k = min(harmonics, max(1, int(sr * 0.4 / hz)))
  pulse:a buzz 1, hz, count, panaeolusPluckSine
  age:k timeinsts
  filter:k = min(cutoff * 2 ^ (env * exp(-age / envDecay)), sr * 0.4)
  left:a moogladder pulse, PanaeolusSmooth(filter, 0.005, 0), resonance
  right:a = left
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusPulserTrim)
  xout outputL, outputR
endop
