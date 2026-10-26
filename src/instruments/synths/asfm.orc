;; Redesigned from the legacy voice concept; no shared mutable voice tables.
PanaeolusASFMTrim@global:i init -10.846

opcode PanaeolusASFM(note:k, db:k, ratio:k, index:k, asymmetry:k, cutoff:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  modulator:a poscil 1, min(hz * ratio, sr * 0.4)
  phase:a phasor hz
  safe:k = min(index, max(0, (sr * 0.42 / hz - 1) / ratio - 1))
  carrier:a = sin(2 * $M_PI * phase + safe * modulator) * exp(asymmetry * 2 * (modulator - 1))
  left:a tone carrier, min(PanaeolusSmooth(cutoff, 0.01, 0), sr * 0.4)
  right:a = left
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusASFMTrim)
  xout outputL, outputR
endop
