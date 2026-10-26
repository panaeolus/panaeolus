;; Split retained interleaved tables once, at load time. The export repeats this
;; from its embedded WAV, so spectral voices keep exactly the same table IDs.
opcode PanaeolusSampleChannel(source:i, target:i, frames:i, channels:i, side:i):i
  number:i ftgen target, 0, -frames, -2, 0
  for index in [0 ... frames - 1] do
    tableiw table:i(index * channels + side, source), index, number
  od
  xout number
endop

