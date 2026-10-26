PanaeolusVosimTrim@global:i init -10.616

opcode PanaeolusVosim(note:k, db:k, vowel:k, shift:k, pulses:k, damping:k, bend:k, vibrato:k, vibratoRate:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  vowel = PanaeolusSmooth(vowel, 0.02, 0)
  shift = PanaeolusSmooth(shift, 0.02, 24)
  hz:k = PanaeolusVocalPitch(note, glide, vibrato, vibratoRate)
  freq0:k, band0:k, gain0:k = PanaeolusVowel(vowel, shift, 1, 0)
  safe0:k = min(freq0, sr * 0.35 / max(1, pow(bend, pulses - 1)))
  part0:a vosim gain0, hz, safe0, gain0 * damping / max(1, pulses - 1), pulses, bend, panaeolusVocalPulse, 0, 1
  freq1:k, band1:k, gain1:k = PanaeolusVowel(vowel, shift, 1, 1)
  safe1:k = min(freq1, sr * 0.35 / max(1, pow(bend, pulses - 1)))
  part1:a vosim gain1, hz, safe1, gain1 * damping / max(1, pulses - 1), pulses, bend, panaeolusVocalPulse, 0, 1
  freq2:k, band2:k, gain2:k = PanaeolusVowel(vowel, shift, 1, 2)
  safe2:k = min(freq2, sr * 0.35 / max(1, pow(bend, pulses - 1)))
  part2:a vosim gain2, hz, safe2, gain2 * damping / max(1, pulses - 1), pulses, bend, panaeolusVocalPulse, 0, 1
  freq3:k, band3:k, gain3:k = PanaeolusVowel(vowel, shift, 1, 3)
  safe3:k = min(freq3, sr * 0.35 / max(1, pow(bend, pulses - 1)))
  part3:a vosim gain3, hz, safe3, gain3 * damping / max(1, pulses - 1), pulses, bend, panaeolusVocalPulse, 0, 1
  freq4:k, band4:k, gain4:k = PanaeolusVowel(vowel, shift, 1, 4)
  safe4:k = min(freq4, sr * 0.35 / max(1, pow(bend, pulses - 1)))
  part4:a vosim gain4, hz, safe4, gain4 * damping / max(1, pulses - 1), pulses, bend, panaeolusVocalPulse, 0, 1
  sum:a = part0 + part1 + part2 + part3 + part4
  signal:a dcblock2 sum
  outputL:a, outputR:a = PanaeolusVoiceOut(signal, signal, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusVosimTrim)
  xout outputL, outputR
endop
