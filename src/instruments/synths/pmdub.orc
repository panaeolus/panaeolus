;; Redesigned from the legacy voice concept; no shared mutable voice tables.
PanaeolusPMDubTrim@global:i init -15.233

opcode PanaeolusPMDub(note:k, db:k, ratio:k, index:k, spread:k, motion:k, depth:k, sub:k, drive:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  wave:k poscil 0.5, motion
  amount:k = index * (1 - depth * (wave + 0.5))
  safe:k = min(amount, max(0, sr * 0.4 / (hz * (ratio + spread * 3)) - 1))
  a1:a poscil 1, min(hz * (ratio + spread * 3), sr * 0.4)
  phase2:a phasor min(hz * (ratio + spread * 2), sr * 0.4)
  a2:a = sin(2 * $M_PI * phase2 + a1 * safe)
  p3phase:a phasor min(hz * (ratio + spread), sr * 0.4)
  a3:a = sin(2 * $M_PI * p3phase + a2 * safe)
  carrier:a phasor hz
  low:a poscil sub, hz / 2
  left:a = tanh((sin(2 * $M_PI * carrier + a3 * safe) + low) * drive) / (tanh(drive) * (1 + sub))
  right:a = left
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusPMDubTrim)
  xout outputL, outputR
endop
