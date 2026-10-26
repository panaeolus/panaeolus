;; Redesigned from the legacy voice concept; no shared mutable voice tables.
PanaeolusPMTrim@global:i init -15.711

opcode PanaeolusPM(note:k, db:k, ratio1:k, ratio2:k, ratio3:k, index1:k, index2:k, index3:k, brightness:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  a1:a poscil 1, min(hz * ratio1, sr * 0.4)
  ph2:a phasor min(hz * ratio2, sr * 0.4)
  a2:a = sin(2 * $M_PI * ph2 + a1 * index1)
  ph3:a phasor min(hz * ratio3, sr * 0.4)
  a3:a = sin(2 * $M_PI * ph3 + a2 * index2)
  phase:a phasor hz
  safe:k = min(index3, max(0, sr * 0.4 / (hz * max(ratio1, max(ratio2, ratio3))) - 1))
  signal:a = sin(2 * $M_PI * phase + a3 * safe)
  left:a tone signal, min(800 + brightness * 12000, sr * 0.4)
  right:a = left
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusPMTrim)
  xout outputL, outputR
endop
