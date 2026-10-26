;; Redesigned from the legacy voice concept; no shared mutable voice tables.
PanaeolusOrganTrim@global:i init -14.755

opcode PanaeolusOrgan(note:k, db:k, sub:k, fundamental:k, fifth:k, octave:k, third:k, upper:k, percussion:k, rotary:k, rotaryRate:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  low:a poscil sub, hz / 2
  root:a poscil fundamental, hz
  fifthTone:a poscil fifth, min(hz * 1.5, sr * 0.4)
  octaveTone:a poscil octave, min(hz * 2, sr * 0.4)
  thirdTone:a poscil third, min(hz * 3, sr * 0.4)
  upperTone:a poscil upper, min(hz * 4, sr * 0.4)
  strike:k expseg 1, 0.18, 0.001, 1, 0.000001
  bell:a poscil percussion * strike, min(hz * 3, sr * 0.4)
  signal:a = (low + root + fifthTone + octaveTone + thirdTone + upperTone + bell) / sqrt(max(1, sub * sub + fundamental * fundamental + fifth * fifth + octave * octave + third * third + upper * upper))
  motion:k poscil rotary * 0.4, rotaryRate
  left:a = signal * sqrt(1 - motion)
  right:a = signal * sqrt(1 + motion)
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusOrganTrim)
  xout outputL, outputR
endop
