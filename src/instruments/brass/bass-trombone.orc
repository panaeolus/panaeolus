;; Redesigned from the legacy voice concept; no shared mutable voice tables.
PanaeolusBassTromboneTrim@global:i init -11.801

opcode PanaeolusBassTrombone(note:k, db:k, breath:k, bite:k, formant:k, brilliance:k, vibrato:k, vibratoRate:k, growl:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  wobble:k poscil vibrato, vibratoRate
  pitch:k = hz * 2 ^ (wobble / 1200)
  buzz:a vco2 0.7, min(pitch, sr * 0.4), 0
  noise:a rnd31 1, 0, 211
  rasp:a poscil growl * 0.3, 31
  source:a = tanh(buzz * (1 + bite * 3)) * (1 + rasp) + noise * breath * 0.12
  vowel:a reson source, min(formant, sr * 0.35), formant * 0.45, 1
  left:a tone source * 0.6 + vowel * 0.4, min(brilliance, sr * 0.4)
  right:a = left
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusBassTromboneTrim)
  xout outputL, outputR
endop
