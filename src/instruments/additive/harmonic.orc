;; Independent partial banks; each note owns its oscillator phases.
PanaeolusHarmonicTrim@global:i init -16.027

opcode PanaeolusHarmonicBank(hz:k, tilt:k, odd:k, detune:k, width:k, partial:i):(a,a)
  weight:k = pow(partial, -tilt) * (1 - odd * (1 - (partial % 2)))
  hzL:k = hz * partial * cent(-detune * width / 2)
  hzR:k = hz * partial * cent(detune * width / 2)
  ;; Fade partials before Nyquist instead of folding high notes into the bass.
  gainL:k = weight * limit((sr * 0.45 - hzL) / (sr * 0.05), 0, 1)
  gainR:k = weight * limit((sr * 0.45 - hzR) / (sr * 0.05), 0, 1)
  left:a poscil gainL, min(hzL, sr * 0.45), -1, frac(partial * 0.61803398875)
  right:a poscil gainR, min(hzR, sr * 0.45), -1, frac(partial * 0.61803398875)
  xout left, right
endop

opcode PanaeolusHarmonic(note:k, db:k, tilt:k, odd:k, detune:k, width:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  tilt = PanaeolusSmooth(tilt, 0.02, 0)
  odd = PanaeolusSmooth(odd, 0.02, 0)
  detune = PanaeolusSmooth(detune, 0.02, 0)
  width = PanaeolusSmooth(width, 0.02, 0)
  part1L:a, part1R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 1)
  part2L:a, part2R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 2)
  part3L:a, part3R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 3)
  part4L:a, part4R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 4)
  part5L:a, part5R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 5)
  part6L:a, part6R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 6)
  part7L:a, part7R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 7)
  part8L:a, part8R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 8)
  part9L:a, part9R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 9)
  part10L:a, part10R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 10)
  part11L:a, part11R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 11)
  part12L:a, part12R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 12)
  part13L:a, part13R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 13)
  part14L:a, part14R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 14)
  part15L:a, part15R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 15)
  part16L:a, part16R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 16)
  part17L:a, part17R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 17)
  part18L:a, part18R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 18)
  part19L:a, part19R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 19)
  part20L:a, part20R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 20)
  part21L:a, part21R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 21)
  part22L:a, part22R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 22)
  part23L:a, part23R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 23)
  part24L:a, part24R:a = PanaeolusHarmonicBank(hz, tilt, odd, detune, width, 24)
  left:a = part1L + part2L + part3L + part4L + part5L + part6L + part7L + part8L + part9L + part10L + part11L + part12L + part13L + part14L + part15L + part16L + part17L + part18L + part19L + part20L + part21L + part22L + part23L + part24L
  right:a = part1R + part2R + part3R + part4R + part5R + part6R + part7R + part8R + part9R + part10R + part11R + part12R + part13R + part14R + part15R + part16R + part17R + part18R + part19R + part20R + part21R + part22R + part23R + part24R
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusHarmonicTrim)
  xout outputL, outputR
endop
