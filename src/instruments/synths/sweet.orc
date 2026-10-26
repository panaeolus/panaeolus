;; Redesigned from the legacy voice concept; no shared mutable voice tables.
PanaeolusSweetTrim@global:i init -7.529

opcode PanaeolusSweet(note:k, db:k, detune:k, pulse:k, motion:k, rate:k, cutoff:k, resonance:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  movement:k poscil motion, rate
  duty:k = limit(pulse + movement, 0.05, 0.95)
  ratio:k = 2 ^ (detune / 1200)
  a1:a vco2 0.5, min(hz * ratio, sr * 0.45), 2, duty, 0.1
  a2:a vco2 0.5, hz / ratio, 2, 1 - duty, 0.6
  left:a moogladder a1 + a2 * 0.2, min(PanaeolusSmooth(cutoff, 0.01, 0), sr * 0.4), resonance
  right:a moogladder a2 + a1 * 0.2, min(PanaeolusSmooth(cutoff, 0.01, 0), sr * 0.4), resonance
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusSweetTrim)
  xout outputL, outputR
endop
