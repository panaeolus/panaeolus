;; Redesigned from the legacy voice concept; no shared mutable voice tables.
PanaeolusClap808Trim@global:i init 6.815

opcode PanaeolusClap808(note:i, db:i, decay:i, tone:i, width:i, spacing:i, tail:i, seed:i, pan:i, amp:i):(a,a)
  noise:a rnd31 1, 0, seed
  band:a reson noise, min(tone * 2 ^ ((note - 60) / 12), sr * 0.3), min(tone * width, sr * 0.3), 1
  high:a butterhp band, 400
  age:a linseg 0, 1, 1
  burst:a = exp(-age / 0.003)
  secondBurst:a delay burst, spacing
  thirdBurst:a delay burst, 2 * spacing
  bursts:a = burst + secondBurst + thirdBurst
  signal:a = tanh(high * 4) * (bursts * 0.3 + tail * (1 - exp(-age / 0.01)))
  outputL:a, outputR:a = PanaeolusDrumOut(signal, db, decay, pan, amp, PanaeolusClap808Trim)
  xout outputL, outputR
endop
