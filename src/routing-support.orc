;; This file also ships in recorded CSDs. Keep it free of Lisp/runtime code.
;; Voices run below 10000, effects in stage bands from 10000, output at 30000.

panaeolusRouteActive@global:i[] init 256
panaeolusRouteEnds@global:i[] init 256
panaeolusRouteLastNote@global:i[] init 256
panaeolusRouteTails@global:i[] init 256
panaeolusRouteCounts@global:i[] init 256
panaeolusRouteStages@global:i[][] init 256, 8
panaeolusLastVoiceEnd@global:i init 0
panaeolusHeldInstruments@global:i[] init 256
panaeolusHeldReleases@global:i[] init 256

opcode PanaeolusAudioChannel(route:i, stage:i, side:S):S
  xout sprintf("panaeolus/r%d/s%d/%s", route, stage, side)
endop

opcode PanaeolusControlChannel(route:i, stage:i, parameter:i):S
  xout sprintf("panaeolus/r%d/f%d/p%d", route, stage, parameter)
endop

opcode PanaeolusVoiceChannel(route:i, parameter:i):S
  xout sprintf("panaeolus/r%d/v/p%d", route, parameter)
endop

opcode PanaeolusHeldGuard(route:i):void
  channel:S init PanaeolusVoiceChannel(route, -1)
  enabled:i chnget channel
  ;; A stop can arrive after a voice was queued but before its init pass.
  ;; turnoff2i cannot see that pending note, so reject it at its own start.
  if (enabled == 0) ithen
    turnoff2i p1, 4, 0
  endif
endop

;; The live manager runs before all voices and processors. A retiring route
;; cannot be reused until its last note and the declared effect tails finish.
instr 90
  now:k = times()
  route:k = 1
  while (route < 256) do
    if (panaeolusRouteActive[route] == 2 && now >= panaeolusRouteEnds[route]) then
      event "i", 93, 0, ksmps / sr, route
    endif
    route += 1
  od
endin

;; Recorded score actions run before voices and effects at each timestamp.
instr 91
  channel:S = PanaeolusControlChannel(p4, p5, p6)
  chnset p7, channel
endin

instr 92
  turnoff2i p4, 4, (p5 > 0 ? 1 : 0)
endin

instr 93
  route:i = p4
  now:i = times()
  if (panaeolusRouteActive[route] == 2 && now >= panaeolusRouteEnds[route]) ithen
    if (panaeolusRouteCounts[route] > 0) ithen
      for stage in [0 ... panaeolusRouteCounts[route] - 1] do
        turnoff2i panaeolusRouteStages[route][stage] + route / 1024, 4, 0
      od
    endif
    turnoff2i 30000 + route / 1024, 4, 0
    panaeolusRouteActive[route] = 0
  endif
endin

instr 94
  channel:S = PanaeolusVoiceChannel(p4, p5)
  chnset p6, channel
endin

instr 30000
  left:S = PanaeolusAudioChannel(p4, p5, "L")
  right:S = PanaeolusAudioChannel(p4, p5, "R")
  audioLeft:a chnget left
  audioRight:a chnget right
  outs audioLeft, audioRight
  chnclear left, right
endin
