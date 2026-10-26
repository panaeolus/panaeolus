;; Redesigned from the legacy voice concept; no shared mutable voice tables.
PanaeolusLowCongaTrim@global:i init -0.946

opcode PanaeolusLowConga(note:i, db:i, decay:i, tone:i, bend:i, slap:i, pan:i, amp:i):(a,a)
  hz:i = cpsmidinn(note)
  drop:k expseg 1, 0.025, 0.0001, 1, 0.000001
  body:a poscil 1, hz * 2 ^ (bend * drop / 12)
  ring:a poscil 0.45 * tone, min(hz * 1.51, sr * 0.4)
  high:a poscil 0.2 * tone, min(hz * 2.32, sr * 0.4)
  snap:a expseg 1, 0.012, 0.0001, 1, 0.000001
  noise:a rnd31 1, 0, 31
  signal:a = (body + ring + high) / 1.3 + noise * snap * slap
  outputL:a, outputR:a = PanaeolusDrumOut(signal, db, decay, pan, amp, PanaeolusLowCongaTrim)
  xout outputL, outputR
endop
