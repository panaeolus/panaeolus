;; Redesigned from the legacy voice concept; no shared mutable voice tables.
PanaeolusKickTrim@global:i init -2.752

opcode PanaeolusKick(note:i, db:i, decay:i, sweep:i, sweepTime:i, click:i, drive:i, pan:i, amp:i):(a,a)
  hz:i = cpsmidinn(note)
  bend:k expseg 1, sweepTime, 0.0001, 1, 0.000001
  body:a poscil 1, min(hz * 2 ^ (sweep * bend / 12), sr * 0.4)
  noise:a rnd31 1, 0, 17
  snap:a expseg 1, 0.006, 0.0001, 1, 0.000001
  tick:a butterhp noise, min(3500, sr * 0.35)
  signal:a = tanh((body + tick * snap * click) * drive) / tanh(drive)
  outputL:a, outputR:a = PanaeolusDrumOut(signal, db, decay, pan, amp, PanaeolusKickTrim)
  xout outputL, outputR
endop
