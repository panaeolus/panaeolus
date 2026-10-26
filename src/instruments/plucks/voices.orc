;; A deterministic modal string: upper partials fade faster, and pick position
;; changes their strengths. No global random seed or hidden sub-bass layer.
opcode PanaeolusStringPartial(hz:i, decay:i, tone:i, position:i, harmonic:i):a
  frequency:i = hz * harmonic
  strength:i = sin($M_PI * harmonic * position) / (harmonic ^ (1.4 - 0.6 * tone))
  strength *= (frequency < sr * 0.45 ? 1 : 0)
  damping:i = 1 + (harmonic * harmonic - 1) * (0.008 + 0.04 * (1 - tone))
  envelope:a expseg 1, decay / damping, 0.001, 1, 0.000001
  xout poscil:a(strength * envelope, min(frequency, sr * 0.45))
endop

opcode PanaeolusPluck(note:i, db:i, decay:i, tone:i, position:i, pan:i, amp:i):(a, a)
  hz:i = cpsmidinn(note)
  a1:a = PanaeolusStringPartial(hz, decay, tone, position, 1)
  a2:a = PanaeolusStringPartial(hz, decay, tone, position, 2)
  a3:a = PanaeolusStringPartial(hz, decay, tone, position, 3)
  a4:a = PanaeolusStringPartial(hz, decay, tone, position, 4)
  a5:a = PanaeolusStringPartial(hz, decay, tone, position, 5)
  a6:a = PanaeolusStringPartial(hz, decay, tone, position, 6)
  a7:a = PanaeolusStringPartial(hz, decay, tone, position, 7)
  a8:a = PanaeolusStringPartial(hz, decay, tone, position, 8)
  power:i = 0
  for harmonic in [1 ... 8] do
    if (hz * harmonic < sr * 0.45) ithen
      strength:i = sin($M_PI * harmonic * position) / (harmonic ^ (1.4 - 0.6 * tone))
      power += strength * strength
    endif
  od
  edge:i = min(0.003, p3 / 2)
  gate:a linen 1, edge, p3, min(0.02, p3 / 2)
  ;; Calibrated with tools/render-plucks.ts: six pitches, one note per second.
  gain:k = PanaeolusLevel(db, amp, -4.78)
  signal:a = (a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8) * gate * gain / sqrt(max(power, 1e-12))
  xout signal * sqrt((1 - pan) / 2), signal * sqrt((1 + pan) / 2)
endop

panaeolusPluckSine@global:i ftgen 0, 0, 16385, 10, 1

;; A bright two-operator pluck whose modulation settles into a rounder tail.
;; Ratio is explicit, so patterns and exported takes repeat the same sound.
opcode PanaeolusFMPluck(note:i, db:i, decay:i, ratio:i, index:i, pan:i, amp:i):(a, a)
  hz:i = cpsmidinn(note)
  ;; Reduce the index near Nyquist rather than folding strong sidebands back.
  safeIndex:i = min(index, max(0, (sr * 0.45 / hz - 1) / ratio - 1))
  brightness:k expseg 1, decay * 0.35, 0.001, 1, 0.000001
  envelope:a = PanaeolusPluckEnvelope(decay)
  carrier:a foscili 1, hz, 1, ratio, safeIndex * brightness, panaeolusPluckSine
  ;; Calibrated with tools/render-plucks.ts: six pitches, one note per second.
  gain:k = PanaeolusLevel(db, amp, -4.96)
  signal:a = carrier * envelope * gain
  xout signal * sqrt((1 - pan) / 2), signal * sqrt((1 + pan) / 2)
endop
