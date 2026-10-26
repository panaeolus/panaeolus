;; A struck resonator with a controllable inharmonic spectrum. Each note owns
;; its partials; no mutable scan tables are shared between simultaneous notes.
opcode PanaeolusHammerPartial(hz:i, decay:i, hardness:i, metal:i, mode:i):a
  ratio:i = mode * sqrt((1 + metal * 0.025 * mode * mode) / (1 + metal * 0.025))
  frequency:i = hz * ratio
  strength:i = (1 / (mode ^ (1.8 - hardness))) * (frequency < sr * 0.45 ? 1 : 0)
  fall:a expseg 1, decay / (1 + (mode - 1) * (0.3 - 0.2 * hardness)), 0.001, 1, 0.000001
  xout poscil:a(strength * fall, min(frequency, sr * 0.45))
endop

opcode PanaeolusHammer(note:i, db:i, decay:i, hardness:i, metal:i, pan:i, amp:i):(a, a)
  hz:i = cpsmidinn(note)
  a1:a = PanaeolusHammerPartial(hz, decay, hardness, metal, 1)
  a2:a = PanaeolusHammerPartial(hz, decay, hardness, metal, 2)
  a3:a = PanaeolusHammerPartial(hz, decay, hardness, metal, 3)
  a4:a = PanaeolusHammerPartial(hz, decay, hardness, metal, 4)
  a5:a = PanaeolusHammerPartial(hz, decay, hardness, metal, 5)
  a6:a = PanaeolusHammerPartial(hz, decay, hardness, metal, 6)
  edge:i = min(0.0015 + (1 - hardness) * 0.004, p3 / 2)
  gate:a linen 1, edge, p3, min(0.025, p3 / 2)
  ;; Fixed trim from the shared six-pitch reference phrase, at -18 LUFS.
  gain:k = PanaeolusLevel(db, amp, -6.64)
  signal:a = (a1 + a2 + a3 + a4 + a5 + a6) * gate * gain
  xout signal * sqrt((1 - pan) / 2), signal * sqrt((1 + pan) / 2)
endop

opcode PanaeolusLushEnvelope(attack:k, decay:k, sustain:k, releaseTime:k):a
  held:i = (p3 < 0 ? 1 : 0)
  if (held == 1) ithen
    ;; The route retains the actual release duration. Four seconds is only
    ;; Csound's upper bound; turnoff below ends the voice at its chosen release.
    xtratim 4
  endif
  age:k timeinsts
  releasing:k release
  start:k init -1
  from:k init 0
  duration:k init 0.2
  level:k init 0
  if (held == 1 && releasing == 1) then
    if (start < 0) then
      start = age
      from = level
      duration = releaseTime
    endif
    level = from * max(0, 1 - (age - start) / duration)
    if (age >= start + duration) then
      ;; turnoff honors the four-second xtratim allowance. End this exact
      ;; held instance now that its chosen fade has reached zero.
      turnoff2 p1, 4, 0
    endif
  else
    if (age < attack) then
      level = age / attack
    elseif (age < attack + decay) then
      level = 1 + (sustain - 1) * (age - attack) / decay
    else
      level = sustain
    endif
    if (held == 0) then
      fade:k = min(releaseTime, p3 / 2)
      level *= limit((p3 - age) / fade, 0, 1)
    endif
  endif
  xout interp:a(level)
endop

;; Band-limited saws and a quiet triangle sub, with fixed starting phases.
;; Edits smooth the controls while leaving oscillator and envelope state alive.
opcode PanaeolusLush(note:k, db:k, cutoff:k, resonance:k, detune:k, width:k, \
                      attack:k, decay:k, sustain:k, releaseTime:k, glide:k, pan:k, amp:k):(a, a)
  ready:k init 0
  if (ready == 0) then
    reinit CONTROLS
    ready = 1
  endif
  igoto CONTROLS_READY
CONTROLS:
  pitch:k portk note, max(glide, 0.0001), i(note)
  frequency:k portk cutoff, 0.01, i(cutoff)
  res:k portk resonance, 0.01, i(resonance)
  spread:k portk detune, 0.01, i(detune)
  stereo:k portk width, 0.01, i(width)
  ;; A negative portk initial value preserves its old state. Offset signed
  ;; controls so a reused voice starts at its own pan and dB values.
  balance:k portk pan + 1, 0.005, i(pan) + 1
  volume:k portk amp, 0.005, i(amp)
  decibels:k portk db + 96, 0.005, i(db) + 96
  rireturn
CONTROLS_READY:
  hz:k = min(cpsmidinn(pitch), sr * 0.4)
  ratio:k = 2 ^ (spread / 1200)
  center:a vco2 0.35, hz, 0, 0.5, 0
  sideL:a vco2 0.45, min(hz * ratio, sr * 0.45), 0, 0.5, 0.1
  sideR:a vco2 0.45, hz / ratio, 0, 0.5, 0.6
  sub:a vco2 0.2, hz / 2, 12, 0.5, 0.25
  left:a moogladder center + sideL + sub, limit(frequency, 20, sr * 0.4), res
  right:a moogladder center + sideR + sub, limit(frequency, 20, sr * 0.4), res
  mid:a = (left + right) * 0.5
  side:a = (left - right) * 0.5 * stereo
  envelope:a = PanaeolusLushEnvelope(attack, decay, sustain, releaseTime)
  ;; Fixed trim from the shared six-pitch reference phrase, at -18 LUFS.
  gain:k = PanaeolusLevel(decibels - 96, volume, -2.15)
  xout (mid + side) * envelope * gain * sqrt(2 - balance), \
       (mid - side) * envelope * gain * sqrt(balance)
endop
