;; Also included in exported CSDs; keep these opcodes free of Lisp state.

opcode PanaeolusSampler(table:i, amp:k, rate:k, start:k, end:k, cross:k, mode:i, pan:k):(a, a)
  sourceRate:i = ftsr(table)
  channels:i = ftchnls(table)
  ;; GEN01 may append a guard value; nsamp counts the file's actual frames.
  frames:i = nsamp(table)
  ;; Reverse playback interpolates one frame beyond its loop end.
  lastFrame:i = max(4, frames - 2)
  minimumSpan:i = min(64, lastFrame)
  first:k = limit(start * frames, 0, lastFrame - minimumSpan)
  last:k = limit(end * frames, first + minimumSpan, lastFrame)
  fade:k = limit(cross * sourceRate, 1, (last - first) / 2 - 1)
  ;; flooper2 keeps the old region until it wraps. Bound the advance using
  ;; the minimum span, so a simultaneous rate/region edit is safe there too.
  speed:k = limit(rate, 0, minimumSpan * sr / (4 * sourceRate))
  loopStart:k = first / sourceRate
  loopEnd:k = last / sourceRate
  loopFade:k = fade / sourceRate
  left:a init 0
  right:a init 0
  ready:k init 0
  if (ready == 0) then
    reinit PLAYER
    ready = 1
  endif
  ;; Held controls arrive through k-rate channels. Read the initial position
  ;; on the first block, then leave flooper2 running across control edits.
  igoto PLAYER_READY
PLAYER:
  if (channels == 1) then
    left flooper2 1, speed, loopStart, loopEnd, loopFade, table, i(loopStart), mode
    right = left
  else
    left, right flooper2 1, speed, loopStart, loopEnd, loopFade, table, i(loopStart), mode
  endif
  rireturn
PLAYER_READY:
  envelope:a init 0
  if (p3 > 0) then
    edge:i = min(0.005, p3 / 2)
    envelope linen 1, edge, p3, edge
  else
    envelope linseg 0, 0.005, 1
  endif
  balance:k = limit(pan, -1, 1)
  gain:a = envelope * amp
  xout left * gain * min(1, 1 - balance), right * gain * min(1, 1 + balance)
endop

