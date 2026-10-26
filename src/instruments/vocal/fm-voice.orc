PanaeolusFMVoiceTrim@global:i init -3.980

opcode PanaeolusFMVoice(note:k, db:k, vowel:k, register:k, tilt:k, vibrato:k, vibratoRate:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  vowel = PanaeolusSmooth(vowel, 0.02, 0)
  hz:k = max(1, PanaeolusPitch(note, glide))
  depth:k = 10 * (cent(vibrato) - 1)
  ;; Keep all five models running. Swapping two model slots at integer vowel
  ;; boundaries would replace the audible phase and click during slow morphs.
  ;; Fixed excitation keeps dB/amp changes from altering the model's spectrum.
  vowel0:a fmvoice 0.7 * 0dbfs, hz, 4 + register * 32, tilt, depth, vibratoRate, \
    panaeolusVocalSine, panaeolusVocalSine, panaeolusVocalSine, panaeolusVocalSine, panaeolusVocalSine
  vowel1:a fmvoice 0.7 * 0dbfs, hz, 2 + register * 32, tilt, depth, vibratoRate, \
    panaeolusVocalSine, panaeolusVocalSine, panaeolusVocalSine, panaeolusVocalSine, panaeolusVocalSine
  vowel2:a fmvoice 0.7 * 0dbfs, hz, 0 + register * 32, tilt, depth, vibratoRate, \
    panaeolusVocalSine, panaeolusVocalSine, panaeolusVocalSine, panaeolusVocalSine, panaeolusVocalSine
  vowel3:a fmvoice 0.7 * 0dbfs, hz, 6 + register * 32, tilt, depth, vibratoRate, \
    panaeolusVocalSine, panaeolusVocalSine, panaeolusVocalSine, panaeolusVocalSine, panaeolusVocalSine
  vowel4:a fmvoice 0.7 * 0dbfs, hz, 9 + register * 32, tilt, depth, vibratoRate, \
    panaeolusVocalSine, panaeolusVocalSine, panaeolusVocalSine, panaeolusVocalSine, panaeolusVocalSine
  raw:a = (vowel0 * max(0, 1 - abs(vowel - 0)) + vowel1 * max(0, 1 - abs(vowel - 1)) + vowel2 * max(0, 1 - abs(vowel - 2)) + vowel3 * max(0, 1 - abs(vowel - 3)) + vowel4 * max(0, 1 - abs(vowel - 4))) / 0dbfs
  signal:a dcblock2 raw
  outputL:a, outputR:a = PanaeolusVoiceOut(signal, signal, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusFMVoiceTrim)
  xout outputL, outputR
endop
