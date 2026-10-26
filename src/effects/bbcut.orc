opcode PanaeolusBBCut(left:a, right:a, bpm:k, subdiv:k, bar:k, phrase:k, repeats:k, stutter:k, chance:k, envelope:k):(a, a)
  outputLeft:a init 0
  outputRight:a init 0
  ready:k init 0
  updated:k changed2 bpm, subdiv, bar, phrase, repeats, stutter, chance, envelope
  if (ready == 0 || updated == 1) then
    reinit CUTTER
    ready = 1
  endif
  ;; bbcuts takes i-rate controls. Skip its ordinary init pass, then initialize
  ;; once with the first k-rate values; later edits start a fresh cut phrase.
  igoto CUTTER_READY
CUTTER:
  outputLeft, outputRight bbcuts left, right, i(bpm) / 60, i(subdiv), i(bar), \
    i(phrase), i(repeats), i(stutter), i(chance), i(envelope)
  rireturn
CUTTER_READY:
  xout outputLeft, outputRight
endop
