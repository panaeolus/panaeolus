panaeolusSpectralSilence@global:i ftgen 0, 0, 8192, -2, 0

opcode PanaeolusSpectral(leftTable:i, rightTable:i, frames:i, sourceRate:i, fft:i, level:i, amp:k, db:k, note:k, root:k, stretch:k, speed:k, start:k, end:k, position:k, lock:k, attack:k, decay:k, sustain:k, releaseTime:k, pan:k):(a,a)
  span:k = max(1, (end - start) * frames)
  ;; mincer retains its previous FFT phases when Csound reuses a UDO
  ;; instance. Feed silence on the first block to clear that history, including
  ;; when the prior note used another FFT size. Both live and replay do this.
  ready:k init 0
  leftSource:k = (ready == 0 ? panaeolusSpectralSilence : leftTable)
  rightSource:k = (ready == 0 ? panaeolusSpectralSilence : rightTable)
  travel:a phasor speed * sourceRate / (max(stretch, 0.125) * span)
  pointer:a = (start * frames + frac(travel + position) * span) / sr
  ;; GEN02 tables have no source-rate metadata. Express both the pointer and
  ;; pitch in engine frames; this also preserves pitch for 44.1/48 kHz imports.
  pitch:k = 2 ^ ((note - root) / 12) * sourceRate / sr
  left:a mincer pointer, 1, pitch, leftSource, lock, fft
  right:a mincer pointer, 1, pitch, rightSource, lock, fft
  ready = 1
  gate:a = PanaeolusLushEnvelope(attack, decay, sustain, releaseTime)
  gain:k = level * amp * ampdb(db)
  balance:k = limit(pan, -1, 1)
  xout left * gate * gain * min(1, 1 - balance), right * gate * gain * min(1, 1 + balance)
endop
