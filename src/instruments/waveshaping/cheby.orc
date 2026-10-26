opcode PanaeolusChebyShape(hz:k, index:k, odd:k, even:k, tilt:k, motion:k, rate:k, width:k):(a,a)
  movement:k poscil motion * 0.45, rate
  amount:k = index * (1 - motion * 0.45 + movement)
  hzL:k = hz * cent(-width * 0.5)
  sourceL:a poscil amount, hzL
  c2L:k = even / pow(2, tilt) * limit((sr * 0.45 - hzL * 2) / (sr * 0.05), 0, 1)
  c3L:k = odd / pow(3, tilt) * limit((sr * 0.45 - hzL * 3) / (sr * 0.05), 0, 1)
  c4L:k = even / pow(4, tilt) * limit((sr * 0.45 - hzL * 4) / (sr * 0.05), 0, 1)
  c5L:k = odd / pow(5, tilt) * limit((sr * 0.45 - hzL * 5) / (sr * 0.05), 0, 1)
  c6L:k = even / pow(6, tilt) * limit((sr * 0.45 - hzL * 6) / (sr * 0.05), 0, 1)
  c7L:k = odd / pow(7, tilt) * limit((sr * 0.45 - hzL * 7) / (sr * 0.05), 0, 1)
  c8L:k = even / pow(8, tilt) * limit((sr * 0.45 - hzL * 8) / (sr * 0.05), 0, 1)
  shapedL:a chebyshevpoly sourceL, 0, 1, -c2L, -c3L, c4L, c5L, -c6L, -c7L, c8L
  outL:a = shapedL / (1 + c2L + c3L + c4L + c5L + c6L + c7L + c8L)
  hzR:k = hz * cent(width * 0.5)
  sourceR:a poscil amount, hzR
  c2R:k = even / pow(2, tilt) * limit((sr * 0.45 - hzR * 2) / (sr * 0.05), 0, 1)
  c3R:k = odd / pow(3, tilt) * limit((sr * 0.45 - hzR * 3) / (sr * 0.05), 0, 1)
  c4R:k = even / pow(4, tilt) * limit((sr * 0.45 - hzR * 4) / (sr * 0.05), 0, 1)
  c5R:k = odd / pow(5, tilt) * limit((sr * 0.45 - hzR * 5) / (sr * 0.05), 0, 1)
  c6R:k = even / pow(6, tilt) * limit((sr * 0.45 - hzR * 6) / (sr * 0.05), 0, 1)
  c7R:k = odd / pow(7, tilt) * limit((sr * 0.45 - hzR * 7) / (sr * 0.05), 0, 1)
  c8R:k = even / pow(8, tilt) * limit((sr * 0.45 - hzR * 8) / (sr * 0.05), 0, 1)
  shapedR:a chebyshevpoly sourceR, 0, 1, -c2R, -c3R, c4R, c5R, -c6R, -c7R, c8R
  outR:a = shapedR / (1 + c2R + c3R + c4R + c5R + c6R + c7R + c8R)
  xout outL, outR
endop

PanaeolusChebyTrim@global:i init -9.228

opcode PanaeolusCheby(note:k, db:k, index:k, odd:k, even:k, tilt:k, motion:k, rate:k, width:k, cutoff:k, attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a,a)
  hz:k = PanaeolusPitch(note, glide)
  index = PanaeolusSmooth(index, 0.02, 0)
  odd = PanaeolusSmooth(odd, 0.02, 0)
  even = PanaeolusSmooth(even, 0.02, 0)
  tilt = PanaeolusSmooth(tilt, 0.02, 0)
  motion = PanaeolusSmooth(motion, 0.02, 0)
  width = PanaeolusSmooth(width, 0.02, 0)
  rawL:a, rawR:a = PanaeolusChebyShape(hz, index, odd, even, tilt, motion, rate, width)
  cleanL:a dcblock2 rawL
  cleanR:a dcblock2 rawR
  tone:k = min(PanaeolusSmooth(cutoff, 0.02, 0), sr * 0.4)
  left:a butterlp cleanL, tone
  right:a butterlp cleanR, tone
  outputL:a, outputR:a = PanaeolusVoiceOut(left, right, db, amp, pan, attack, decay, sustain, releaseTime, PanaeolusChebyTrim)
  xout outputL, outputR
endop
