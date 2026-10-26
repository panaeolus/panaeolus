;; BS.1770 K-weighted, gated stereo energy. Measure once while loading, never
;; in a clock callback. Mono files count twice because the player sends them
;; to both outputs. Short one-shots get zero padding to one 400 ms block.
opcode PanaeolusSampleLevel(number:i, frames:i, channels:i, rate:i):(i, i)
  shelfK:i = tan($M_PI * min(1681.974450955533, rate * 0.4) / rate)
  shelfQ:i = 0.7071752369554196
  high:i = 10 ^ (3.999843853973347 / 20)
  band:i = high ^ 0.4996667741545416
  denominator:i = 1 + shelfK / shelfQ + shelfK * shelfK
  b0:i = (high + band * shelfK / shelfQ + shelfK * shelfK) / denominator
  b1:i = 2 * (shelfK * shelfK - high) / denominator
  b2:i = (high - band * shelfK / shelfQ + shelfK * shelfK) / denominator
  a1:i = 2 * (shelfK * shelfK - 1) / denominator
  a2:i = (1 - shelfK / shelfQ + shelfK * shelfK) / denominator
  highK:i = tan($M_PI * min(38.13547087602444, rate * 0.1) / rate)
  highQ:i = 0.5003270373238773
  denominator = 1 + highK / highQ + highK * highK
  c1:i = 2 * (highK * highK - 1) / denominator
  c2:i = (1 - highK / highQ + highK * highK) / denominator
  state:i[] init 8
  hops:i[] init 4
  hop:i = max(1, round(rate * 0.1))
  length:i = max(frames, hop * 4)
  windows:i[] init int(length / hop)
  count:i = 0
  sum:i = 0
  peak:i = 0
  for frame in [0 ... length - 1] do
    energy:i = 0
    for channel in [0 ... channels - 1] do
      value:i = 0
      if (frame < frames) ithen
        value = table:i(frame * channels + channel, number) / 0dbfs
        peak = max(peak, abs(value))
      endif
      offset:i = channel * 4
      shelf:i = b0 * value + state[offset]
      state[offset] = b1 * value - a1 * shelf + state[offset + 1]
      state[offset + 1] = b2 * value - a2 * shelf
      weighted:i = shelf + state[offset + 2]
      state[offset + 2] = -2 * shelf - c1 * weighted + state[offset + 3]
      state[offset + 3] = shelf - c2 * weighted
      energy += weighted * weighted
    od
    sum += energy * (channels == 1 ? 2 : 1)
    if ((frame + 1) % hop == 0) ithen
      slot:i = (int((frame + 1) / hop) - 1) % 4
      hops[slot] = sum
      sum = 0
      if (frame + 1 >= hop * 4) ithen
        windows[count] = (hops[0] + hops[1] + hops[2] + hops[3]) / (4 * hop)
        count += 1
      endif
    endif
  od
  absolute:i = 10 ^ ((-70 + 0.691) / 10)
  sum = 0
  accepted:i = 0
  for window in [0 ... count - 1] do
    if (windows[window] >= absolute) ithen
      sum += windows[window]
      accepted += 1
    endif
  od
  loudness:i = -120
  gain:i = 1
  if (accepted > 0) ithen
    threshold:i = max(absolute, (sum / accepted) * 0.1)
    sum = 0
    accepted = 0
    for window in [0 ... count - 1] do
      if (windows[window] >= threshold) ithen
        sum += windows[window]
        accepted += 1
      endif
    od
    loudness = -0.691 + 10 * log10(sum / accepted)
    ;; Limit boosts to 24 dB and leave at least 3 dB sample-peak headroom.
    ;; Very spiky files may remain quieter than the target; never squash them.
    gain = min(ampdb(-18 - loudness), ampdb(24), ampdb(-3) / max(peak, 1e-20))
  endif
  xout gain, loudness
endop
