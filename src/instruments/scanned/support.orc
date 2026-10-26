;; Each note owns its moving tables. scantable updates them once per control
;; block; the audio readers below interpolate the result and share no scan ID.
opcode PanaeolusScannedModel(hz:k, speed:k, mass:k, damping:k, shape:k, drive:k, warp:k, spread:k, profile:i):(a,a)
  positions:i ftgentmp 0, 0, 64, -2, 0
  velocities:i ftgentmp 0, 0, 64, -2, 0
  masses:i ftgentmp 0, 0, 64, -2, 1
  springs:i ftgentmp 0, 0, 64, -2, 1
  losses:i ftgentmp 0, 0, 64, -2, 1
  ;; The first performance pass has the real held-channel values. Do not take
  ;; i(shape) here: those channels may still contain zero during initialization.
  first:k init 1
  movement:k phasor speed * 0.37
  ;; Limit the fastest spring mode relative to the integration rate, including
  ;; the lightest mass and stiffest spring. Large host blocks stay stable.
  stiffness:k = min(pow(64 * speed, 2), pow(kr * 0.8, 2) * mass * 0.65 / 1.35)
  loss:k = exp(-damping / kr)
  pull:k = 1 - exp(-drive * 3 / kr)
  index:k = 0
  while (index < 64) do
    angle:k = 6.283185307179586 * index / 64
    if (profile == 0) then
      weight:k = 1 + 0.25 * cos(angle * 2)
      spring:k = 1 + 0.15 * sin(angle * 3)
      seed:k = (sin(angle) + shape * (sin(angle * 2) * 0.4 + sin(angle * 3) * 0.25)) / (1 + shape * 0.65)
      target:k = (sin(angle + movement * 6.283185307179586) + shape * sin(angle * 3 - movement * 6.283185307179586) * 0.4) / (1 + shape * 0.4)
    else
      weight = 1 + 0.35 * cos(angle * 5)
      spring = 1 + 0.35 * sin(angle * 7)
      seed = (sin(angle) + shape * (sin(angle * 3) * 0.6 + sin(angle * 7) * 0.3)) / (1 + shape * 0.9)
      target = (sin(angle * 2 + movement * 6.283185307179586) + shape * sin(angle * 7 - movement * 12.566370614359172) * 0.6) / (1 + shape * 0.6)
    endif
    tablew mass * weight, index, masses
    tablew stiffness * spring, index, springs
    tablew loss, index, losses
    if (first == 1) then
      tablew seed, index, positions
      tablew 0, index, velocities
    else
      position:k table index, positions
      velocity:k table index, velocities
      ;; Continuous excitation pulls towards a moving shape. The bounds are
      ;; physical stops for abrupt parameter edits, before the next integration.
      tablew limit(position + pull * (target - position), -4, 4), index, positions
      tablew limit(velocity, -kr, kr), index, velocities
    endif
    index += 1
  od
  first = 0
  unused:a scantable 0, 0, positions, masses, springs, losses, velocities
  ;; scantable's own reader is nearest-neighbour. Refresh the guard point and
  ;; use cubic table readers for the audio and independently placed pickups.
  edge:k table 0, positions
  tablew edge, 64, positions
  phase:a phasor hz
  curve:a = phase + warp * sin(phase * 6.283185307179586) / 6.283185307179586
  left:a table3 frac(curve), positions, 1, 0, 1
  right:a table3 frac(curve + spread * 0.25), positions, 1, 0, 1
  xout left, right
endop
