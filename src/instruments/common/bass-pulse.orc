;; division is beats per cycle. Edits change speed without resetting the
;; native phasor; phase is an explicit offset, not a shared/global LFO.
opcode PanaeolusBassPulse(bpm:k, division:k, phase:k, sharpness:k):k
  clock:k phasor bpm / (60 * division)
  position:k = frac(clock + phase)
  rounded:k = 0.5 - 0.5 * cos(position * 6.283185307179586)
  xout pow(max(0, rounded), sharpness)
endop
