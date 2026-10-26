;; Shared control smoothing and envelopes for the optional catalogue.
opcode PanaeolusSmooth(value:k, half:k, offset:i):k
  ready:k init 0
  if (ready == 0) then
    reinit INITIAL
    ready = 1
  endif
  igoto READY
INITIAL:
  filtered:k portk value + offset, max(half, 0.0001), i(value) + offset
  rireturn
READY:
  xout (half <= 0 ? value : filtered - offset)
endop

opcode PanaeolusPitch(note:k, glide:k):k
  pitch:k = PanaeolusSmooth(note, glide, 128)
  xout min(cpsmidinn(pitch), sr * 0.4)
endop

opcode PanaeolusVoiceOut(left:a, right:a, db:k, amp:k, pan:k, attack:k, decay:k, sustain:k, releaseTime:k, trim:i):(a,a)
  volume:k = PanaeolusSmooth(amp, 0.005, 0)
  decibels:k = PanaeolusSmooth(db, 0.005, 128)
  balance:k = limit(PanaeolusSmooth(pan, 0.005, 1), -1, 1)
  gate:a = PanaeolusLushEnvelope(attack, decay, sustain, releaseTime)
  gain:k = PanaeolusLevel(decibels, volume, trim)
  xout left * gate * gain * sqrt(1 - balance), right * gate * gain * sqrt(1 + balance)
endop

opcode PanaeolusDrumOut(signal:a, db:i, decay:i, pan:i, amp:i, trim:i):(a,a)
  edge:i = min(0.001, p3 / 2)
  gate:a linen 1, edge, p3, min(0.02, p3 / 2)
  fall:a expseg 1, decay, 0.001, 1, 0.000001
  gain:k = PanaeolusLevel(db, amp, trim)
  out:a = signal * gate * fall * gain
  xout out * sqrt((1 - pan) / 2), out * sqrt((1 + pan) / 2)
endop
