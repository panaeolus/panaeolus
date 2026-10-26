PanaeolusAetherTrim@global:i init 2.484

opcode PanaeolusAether(note:k, db:k, focus:k, stretch:k, bandwidth:k, air:k, motion:k, rate:k, detune:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  focus = PanaeolusSmooth(focus, 0.03, 0)
  stretch = PanaeolusSmooth(stretch, 0.03, 0)
  bandwidth = PanaeolusSmooth(bandwidth, 0.03, 0)
  air = PanaeolusSmooth(air, 0.03, 0)
  motion = PanaeolusSmooth(motion, 0.03, 0)
  detune = PanaeolusSmooth(detune, 0.03, 0)
  noise:a rnd31 0.4, 0, 491
  sweep:k poscil motion, rate
  frequencyL1:k = hz * pow(1, stretch) * cent(-detune * 0.5 + sweep * 3)
  weightL1:k = (1 + focus * 0) / 1 * limit((sr * 0.45 - frequencyL1) / (sr * 0.05), 0, 1)
  bandL1:a reson noise, min(frequencyL1, sr * 0.45), max(8, min(frequencyL1 * bandwidth, sr * 0.1)), 2
  toneL1:a poscil 0.4, min(frequencyL1, sr * 0.45)
  partL1:a = (bandL1 * air + toneL1 * (1 - air)) * weightL1
  frequencyL2:k = hz * pow(2, stretch) * cent(-detune * 0.5 + sweep * 6)
  weightL2:k = (1 + focus * 1) / 4 * limit((sr * 0.45 - frequencyL2) / (sr * 0.05), 0, 1)
  bandL2:a reson noise, min(frequencyL2, sr * 0.45), max(8, min(frequencyL2 * bandwidth, sr * 0.1)), 2
  toneL2:a poscil 0.4, min(frequencyL2, sr * 0.45)
  partL2:a = (bandL2 * air + toneL2 * (1 - air)) * weightL2
  frequencyL3:k = hz * pow(3, stretch) * cent(-detune * 0.5 + sweep * 9)
  weightL3:k = (1 + focus * 2) / 9 * limit((sr * 0.45 - frequencyL3) / (sr * 0.05), 0, 1)
  bandL3:a reson noise, min(frequencyL3, sr * 0.45), max(8, min(frequencyL3 * bandwidth, sr * 0.1)), 2
  toneL3:a poscil 0.4, min(frequencyL3, sr * 0.45)
  partL3:a = (bandL3 * air + toneL3 * (1 - air)) * weightL3
  frequencyL4:k = hz * pow(4, stretch) * cent(-detune * 0.5 + sweep * 12)
  weightL4:k = (1 + focus * 3) / 16 * limit((sr * 0.45 - frequencyL4) / (sr * 0.05), 0, 1)
  bandL4:a reson noise, min(frequencyL4, sr * 0.45), max(8, min(frequencyL4 * bandwidth, sr * 0.1)), 2
  toneL4:a poscil 0.4, min(frequencyL4, sr * 0.45)
  partL4:a = (bandL4 * air + toneL4 * (1 - air)) * weightL4
  rawL:a = (partL1 + partL2 + partL3 + partL4) / (1.423611111111 + focus * 0.659722222222)
  frequencyR1:k = hz * pow(1, stretch) * cent(detune * 0.5 + sweep * 3)
  weightR1:k = (1 + focus * 0) / 1 * limit((sr * 0.45 - frequencyR1) / (sr * 0.05), 0, 1)
  bandR1:a reson noise, min(frequencyR1, sr * 0.45), max(8, min(frequencyR1 * bandwidth, sr * 0.1)), 2
  toneR1:a poscil 0.4, min(frequencyR1, sr * 0.45)
  partR1:a = (bandR1 * air + toneR1 * (1 - air)) * weightR1
  frequencyR2:k = hz * pow(2, stretch) * cent(detune * 0.5 + sweep * 6)
  weightR2:k = (1 + focus * 1) / 4 * limit((sr * 0.45 - frequencyR2) / (sr * 0.05), 0, 1)
  bandR2:a reson noise, min(frequencyR2, sr * 0.45), max(8, min(frequencyR2 * bandwidth, sr * 0.1)), 2
  toneR2:a poscil 0.4, min(frequencyR2, sr * 0.45)
  partR2:a = (bandR2 * air + toneR2 * (1 - air)) * weightR2
  frequencyR3:k = hz * pow(3, stretch) * cent(detune * 0.5 + sweep * 9)
  weightR3:k = (1 + focus * 2) / 9 * limit((sr * 0.45 - frequencyR3) / (sr * 0.05), 0, 1)
  bandR3:a reson noise, min(frequencyR3, sr * 0.45), max(8, min(frequencyR3 * bandwidth, sr * 0.1)), 2
  toneR3:a poscil 0.4, min(frequencyR3, sr * 0.45)
  partR3:a = (bandR3 * air + toneR3 * (1 - air)) * weightR3
  frequencyR4:k = hz * pow(4, stretch) * cent(detune * 0.5 + sweep * 12)
  weightR4:k = (1 + focus * 3) / 16 * limit((sr * 0.45 - frequencyR4) / (sr * 0.05), 0, 1)
  bandR4:a reson noise, min(frequencyR4, sr * 0.45), max(8, min(frequencyR4 * bandwidth, sr * 0.1)), 2
  toneR4:a poscil 0.4, min(frequencyR4, sr * 0.45)
  partR4:a = (bandR4 * air + toneR4 * (1 - air)) * weightR4
  rawR:a = (partR1 + partR2 + partR3 + partR4) / (1.423611111111 + focus * 0.659722222222)
  left:a dcblock2 rawL
  right:a dcblock2 rawR
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusAetherTrim)
  xout outputL, outputR
endop
