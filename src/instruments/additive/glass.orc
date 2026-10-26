;; Independent partial banks; each note owns its oscillator phases.
PanaeolusGlassTrim@global:i init -16.318

opcode PanaeolusGlassBank(hz:k, stretch:k, damping:k, color:k, beating:k, width:k, partial:i):(a,a)
  elapsed:k timeinsts
  weight:k = exp(-elapsed * damping * sqrt(partial)) / pow(partial, color)
  hzL:k = hz * pow(partial, stretch)
  hzR:k = hzL + beating * width * sqrt(partial)
  ;; Fade partials before Nyquist instead of folding high notes into the bass.
  gainL:k = weight * limit((sr * 0.45 - hzL) / (sr * 0.05), 0, 1)
  gainR:k = weight * limit((sr * 0.45 - hzR) / (sr * 0.05), 0, 1)
  left:a poscil gainL, min(hzL, sr * 0.45), -1, frac(partial * 0.61803398875)
  right:a poscil gainR, min(hzR, sr * 0.45), -1, frac(partial * 0.61803398875)
  xout left, right
endop

opcode PanaeolusGlass(note:k, db:k, stretch:k, damping:k, color:k, beating:k, width:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  stretch = PanaeolusSmooth(stretch, 0.02, 0)
  damping = PanaeolusSmooth(damping, 0.02, 0)
  color = PanaeolusSmooth(color, 0.02, 0)
  beating = PanaeolusSmooth(beating, 0.02, 0)
  width = PanaeolusSmooth(width, 0.02, 0)
  part1L:a, part1R:a = PanaeolusGlassBank(hz, stretch, damping, color, beating, width, 1)
  part2L:a, part2R:a = PanaeolusGlassBank(hz, stretch, damping, color, beating, width, 2)
  part3L:a, part3R:a = PanaeolusGlassBank(hz, stretch, damping, color, beating, width, 3)
  part4L:a, part4R:a = PanaeolusGlassBank(hz, stretch, damping, color, beating, width, 4)
  part5L:a, part5R:a = PanaeolusGlassBank(hz, stretch, damping, color, beating, width, 5)
  part6L:a, part6R:a = PanaeolusGlassBank(hz, stretch, damping, color, beating, width, 6)
  part7L:a, part7R:a = PanaeolusGlassBank(hz, stretch, damping, color, beating, width, 7)
  part8L:a, part8R:a = PanaeolusGlassBank(hz, stretch, damping, color, beating, width, 8)
  part9L:a, part9R:a = PanaeolusGlassBank(hz, stretch, damping, color, beating, width, 9)
  part10L:a, part10R:a = PanaeolusGlassBank(hz, stretch, damping, color, beating, width, 10)
  part11L:a, part11R:a = PanaeolusGlassBank(hz, stretch, damping, color, beating, width, 11)
  part12L:a, part12R:a = PanaeolusGlassBank(hz, stretch, damping, color, beating, width, 12)
  part13L:a, part13R:a = PanaeolusGlassBank(hz, stretch, damping, color, beating, width, 13)
  part14L:a, part14R:a = PanaeolusGlassBank(hz, stretch, damping, color, beating, width, 14)
  part15L:a, part15R:a = PanaeolusGlassBank(hz, stretch, damping, color, beating, width, 15)
  part16L:a, part16R:a = PanaeolusGlassBank(hz, stretch, damping, color, beating, width, 16)
  left:a = part1L + part2L + part3L + part4L + part5L + part6L + part7L + part8L + part9L + part10L + part11L + part12L + part13L + part14L + part15L + part16L
  right:a = part1R + part2R + part3R + part4R + part5R + part6R + part7R + part8R + part9R + part10R + part11R + part12R + part13R + part14R + part15R + part16R
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusGlassTrim)
  xout outputL, outputR
endop
