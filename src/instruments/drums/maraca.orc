;; Redesigned from the legacy voice concept; no shared mutable voice tables.
PanaeolusMaracaTrim@global:i init 4.558

opcode PanaeolusMaraca(note:i, db:i, decay:i, tone:i, bandwidth:i, roughness:i, seed:i, pan:i, amp:i):(a,a)
  noise:a rnd31 1, 0, seed
  band:a reson noise, min(tone * 2 ^ ((note - 60) / 12), sr * 0.4), min(bandwidth, sr * 0.4), 1
  flutter:a poscil 1, 83
  signal:a = band * (1 - roughness * 0.5 + roughness * 0.5 * flutter)
  outputL:a, outputR:a = PanaeolusDrumOut(signal, db, decay, pan, amp, PanaeolusMaracaTrim)
  xout outputL, outputR
endop
