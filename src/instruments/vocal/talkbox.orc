PanaeolusTalkboxTrim@global:i init -15.853

opcode PanaeolusTalkbox(note:k, db:k, vowel:k, shift:k, bandwidth:k, edge:k, breath:k, ring:k, vibrato:k, vibratoRate:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  vowel = PanaeolusSmooth(vowel, 0.02, 0)
  shift = PanaeolusSmooth(shift, 0.02, 24)
  hz:k = PanaeolusVocalPitch(note, glide, vibrato, vibratoRate)
  bandwidth = PanaeolusSmooth(bandwidth, 0.02, 0)
  count:k = max(1, min(4 + edge * 92, int(sr * 0.4 / max(hz, 1))))
  pulse:a buzz 1, hz, count, panaeolusVocalSine
  air:a rnd31 1, 0, 317
  source:a = pulse * (1 - breath * 0.7) + air * breath * 0.08
  sum:a = PanaeolusVocalFilter(source, vowel, shift, bandwidth, ring)
  signal:a dcblock2 sum
  outputL:a, outputR:a = PanaeolusVoiceOut(signal, signal, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusTalkboxTrim)
  xout outputL, outputR
endop
