;; Independent partial banks; each note owns its oscillator phases.
PanaeolusPrismTrim@global:i init -16.467

opcode PanaeolusPrismBank(hz:k, stretch:k, tilt:k, spacing:k, motion:k, depth:k, width:k, partial:i):(a,a)
  scan:k phasor motion
  weight:k = (1 - depth * 0.5 + depth * 0.5 * cos(2 * $M_PI * (partial / spacing - scan))) / pow(partial, tilt)
  hzL:k = hz * pow(partial, stretch) * cent(-width * (partial % 3))
  hzR:k = hz * pow(partial, stretch) * cent(width * (partial % 3))
  ;; Fade partials before Nyquist instead of folding high notes into the bass.
  gainL:k = weight * limit((sr * 0.45 - hzL) / (sr * 0.05), 0, 1)
  gainR:k = weight * limit((sr * 0.45 - hzR) / (sr * 0.05), 0, 1)
  left:a poscil gainL, min(hzL, sr * 0.45), -1, frac(partial * 0.61803398875)
  right:a poscil gainR, min(hzR, sr * 0.45), -1, frac(partial * 0.61803398875)
  xout left, right
endop

opcode PanaeolusPrism(note:k, db:k, stretch:k, tilt:k, spacing:k, motion:k, depth:k, width:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  stretch = PanaeolusSmooth(stretch, 0.02, 0)
  tilt = PanaeolusSmooth(tilt, 0.02, 0)
  spacing = PanaeolusSmooth(spacing, 0.02, 0)
  motion = PanaeolusSmooth(motion, 0.02, 0)
  depth = PanaeolusSmooth(depth, 0.02, 0)
  width = PanaeolusSmooth(width, 0.02, 0)
  part1L:a, part1R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 1)
  part2L:a, part2R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 2)
  part3L:a, part3R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 3)
  part4L:a, part4R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 4)
  part5L:a, part5R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 5)
  part6L:a, part6R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 6)
  part7L:a, part7R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 7)
  part8L:a, part8R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 8)
  part9L:a, part9R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 9)
  part10L:a, part10R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 10)
  part11L:a, part11R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 11)
  part12L:a, part12R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 12)
  part13L:a, part13R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 13)
  part14L:a, part14R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 14)
  part15L:a, part15R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 15)
  part16L:a, part16R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 16)
  part17L:a, part17R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 17)
  part18L:a, part18R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 18)
  part19L:a, part19R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 19)
  part20L:a, part20R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 20)
  part21L:a, part21R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 21)
  part22L:a, part22R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 22)
  part23L:a, part23R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 23)
  part24L:a, part24R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 24)
  part25L:a, part25R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 25)
  part26L:a, part26R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 26)
  part27L:a, part27R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 27)
  part28L:a, part28R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 28)
  part29L:a, part29R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 29)
  part30L:a, part30R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 30)
  part31L:a, part31R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 31)
  part32L:a, part32R:a = PanaeolusPrismBank(hz, stretch, tilt, spacing, motion, depth, width, 32)
  left:a = part1L + part2L + part3L + part4L + part5L + part6L + part7L + part8L + part9L + part10L + part11L + part12L + part13L + part14L + part15L + part16L + part17L + part18L + part19L + part20L + part21L + part22L + part23L + part24L + part25L + part26L + part27L + part28L + part29L + part30L + part31L + part32L
  right:a = part1R + part2R + part3R + part4R + part5R + part6R + part7R + part8R + part9R + part10R + part11R + part12R + part13R + part14R + part15R + part16R + part17R + part18R + part19R + part20R + part21R + part22R + part23R + part24R + part25R + part26R + part27R + part28R + part29R + part30R + part31R + part32R
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusPrismTrim)
  xout outputL, outputR
endop
