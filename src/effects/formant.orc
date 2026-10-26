;; The wet gain leaves room for the filter resonances; this is a timbre effect,
;; not a loudness normalizer. Both channels retain their own filter state.
opcode PanaeolusFormantFX(left:a, right:a, vowel:k, shift:k, bandwidth:k, ring:k, mix:k):(a,a)
  position:k = PanaeolusSmooth(vowel, 0.02, 0)
  transpose:k = PanaeolusSmooth(shift, 0.02, 24)
  width:k = PanaeolusSmooth(bandwidth, 0.02, 0)
  decay:k = PanaeolusSmooth(ring, 0.02, 0)
  blend:k = PanaeolusSmooth(mix, 0.02, 0)
  filteredL:a = PanaeolusVocalFilter(left, position, transpose, width, decay)
  filteredR:a = PanaeolusVocalFilter(right, position, transpose, width, decay)
  wetL:a dcblock2 filteredL
  wetR:a dcblock2 filteredR
  xout left * (1 - blend) + wetL * blend * 0.2, right * (1 - blend) + wetR * blend * 0.2
endop
