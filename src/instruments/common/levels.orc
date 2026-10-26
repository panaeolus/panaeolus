;; Built-in instruments share a calibrated reference level. Keep gain after
;; the sound generator so changing dB never changes its timbre or decay.
opcode PanaeolusLevel(db:k, amp:k, trim:i):k
  xout 0dbfs * amp * ampdb(db + trim)
endop

opcode PanaeolusPluckEnvelope(decay:i):a
  ;; Decay is the time to -60 dB. The short edges also cover very short notes.
  edge:i = min(0.003, p3 / 2)
  gate:a linen 1, edge, p3, min(0.02, p3 / 2)
  fall:a expseg 1, decay, 0.001, 1, 0.000001
  xout gate * fall
endop
