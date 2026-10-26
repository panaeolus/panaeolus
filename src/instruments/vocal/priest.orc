PanaeolusPriestTrim@global:i init -15.371

opcode PanaeolusPriest(note:k, db:k, vowel:k, shift:k, bandwidth:k, octave:k, vibrato:k, vibratoRate:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  vowel = PanaeolusSmooth(vowel, 0.02, 0)
  shift = PanaeolusSmooth(shift, 0.02, 24)
  hz:k = PanaeolusVocalPitch(note, glide, vibrato, vibratoRate)
  bandwidth = PanaeolusSmooth(bandwidth, 0.02, 0)
  freq0:k, band0:k, gain0:k = PanaeolusVowel(vowel, shift, bandwidth, 0)
  part0:a = PanaeolusVocalGrain(hz, freq0, band0, gain0, octave, 0.025)
  freq1:k, band1:k, gain1:k = PanaeolusVowel(vowel, shift, bandwidth, 1)
  part1:a = PanaeolusVocalGrain(hz, freq1, band1, gain1, octave, 0.025)
  freq2:k, band2:k, gain2:k = PanaeolusVowel(vowel, shift, bandwidth, 2)
  part2:a = PanaeolusVocalGrain(hz, freq2, band2, gain2, octave, 0.025)
  freq3:k, band3:k, gain3:k = PanaeolusVowel(vowel, shift, bandwidth, 3)
  part3:a = PanaeolusVocalGrain(hz, freq3, band3, gain3, octave, 0.025)
  freq4:k, band4:k, gain4:k = PanaeolusVowel(vowel, shift, bandwidth, 4)
  part4:a = PanaeolusVocalGrain(hz, freq4, band4, gain4, octave, 0.025)
  sum:a = part0 + part1 + part2 + part3 + part4
  signal:a dcblock2 sum
  outputL:a, outputR:a = PanaeolusVoiceOut(signal, signal, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusPriestTrim)
  xout outputL, outputR
endop
