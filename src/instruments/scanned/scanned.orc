PanaeolusScannedTrim@global:i init -9.604

opcode PanaeolusScanned(note:k, db:k, speed:k, mass:k, damping:k, shape:k, drive:k, warp:k, spread:k, cutoff:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  speed = PanaeolusSmooth(speed, 0.05, 0)
  mass = PanaeolusSmooth(mass, 0.05, 0)
  damping = PanaeolusSmooth(damping, 0.05, 0)
  shape = PanaeolusSmooth(shape, 0.03, 0)
  drive = PanaeolusSmooth(drive, 0.03, 0)
  warp = PanaeolusSmooth(warp, 0.03, 0)
  spread = PanaeolusSmooth(spread, 0.03, 0)
  rawL:a, rawR:a = PanaeolusScannedModel(hz, speed, mass, damping, shape, drive, warp, spread, 0)
  cleanL:a dcblock2 rawL
  cleanR:a dcblock2 rawR
  tone:k = min(PanaeolusSmooth(cutoff, 0.02, 0), sr * 0.4)
  left:a butterlp cleanL, tone
  right:a butterlp cleanR, tone
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusScannedTrim)
  xout outputL, outputR
endop
