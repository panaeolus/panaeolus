opcode PanaeolusAdsyn(path:S, root:i, trim:i, top:i, note:k, db:k, speed:k, attack:k, decay:k, sustain:k, releaseTime:k, pan:k, amp:k):(a,a)
  ;; adsyn's breakpoint walker divides by its integer time step. Never pass
  ;; zero, including the first block while a smoothed control initializes.
  playback:k = max(kr / 512000, PanaeolusSmooth(speed, 0.02, 0))
  ratio:k = min(pow(2, (note - root) / 12), sr * 0.45 / top)
  file:S chnget path
  if (strlen(file) == 0) ithen
    file init path
  endif
  raw:a adsyn 1 / 0dbfs, ratio, playback, file
  outputL:a, outputR:a = PanaeolusVoiceOut(raw, raw, db, amp, pan, attack, decay, sustain, releaseTime, trim)
  xout outputL, outputR
endop
