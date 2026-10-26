;; Five deliberately broad vowel targets: A, E, I, O, U. Rows hold five
;; resonances. Keeping this numeric map separate leaves room for phoneme plans.
panaeolusVowelHz@global:i ftgen 0, 0, -25, -2, \
  680, 1120, 2700, 3150, 3900, \
  430, 1830, 2550, 3350, 4100, \
  280, 2200, 2950, 3600, 4300, \
  480, 850, 2500, 3100, 3800, \
  330, 690, 2350, 3000, 3700
panaeolusVowelDb@global:i ftgen 0, 0, -25, -2, \
  0, -5, -16, -22, -30, \
  0, -12, -16, -24, -32, \
  0, -16, -19, -27, -35, \
  0, -8, -20, -26, -34, \
  0, -12, -24, -30, -38
panaeolusVowelBw@global:i ftgen 0, 0, -25, -2, \
  80, 95, 140, 180, 240, \
  65, 105, 140, 180, 240, \
  55, 110, 150, 200, 260, \
  70, 85, 130, 180, 240, \
  55, 80, 130, 180, 240
panaeolusVocalSine@global:i ftgen 0, 0, 4097, 10, 1
panaeolusVocalRise@global:i ftgen 0, 0, 1025, 19, 0.5, 0.5, 270, 0.5
;; One sin-squared pulse, with zero value and slope at both ends.
panaeolusVocalPulse@global:i ftgen 0, 0, 4097, 19, 1, -0.5, 90, 0.5

opcode PanaeolusVowel(vowel:k, shift:k, width:k, partial:i):(k,k,k)
  position:k = limit(vowel, 0, 4)
  lower:k = int(position) * 5 + partial
  upper:k = min(4, int(position) + 1) * 5 + partial
  blend:k = frac(position)
  f0:k table lower, panaeolusVowelHz
  f1:k table upper, panaeolusVowelHz
  d0:k table lower, panaeolusVowelDb
  d1:k table upper, panaeolusVowelDb
  b0:k table lower, panaeolusVowelBw
  b1:k table upper, panaeolusVowelBw
  ratio:k = pow(2, shift / 12)
  frequency:k = min((f0 + (f1 - f0) * blend) * ratio, sr * 0.35)
  bandwidth:k = limit((b0 + (b1 - b0) * blend) * width * ratio, 10, sr * 0.1)
  gain:k = ampdb(d0 + (d1 - d0) * blend)
  xout frequency, bandwidth, gain
endop

opcode PanaeolusVocalPitch(note:k, glide:k, vibrato:k, rate:k):k
  hz:k = PanaeolusPitch(note, glide)
  movement:k poscil PanaeolusSmooth(vibrato, 0.02, 0), rate
  xout min(hz * cent(movement), sr * 0.2)
endop

opcode PanaeolusVocalGrain(hz:k, frequency:k, bandwidth:k, gain:k, octave:k, grain:k):a
  ;; Refresh the finite sample counter, retaining active grains and phases.
  ;; A huge itotdur overflows fof's int32 counter; always starting with iskip=1
  ;; would instead retain stale grains when Csound reuses a finished voice.
  retain:i init 0
  nextRefresh:k init 1
  age:k timeinsts
  if (age >= nextRefresh) then
    nextRefresh = age + 1
    reinit REFRESH
  endif
  igoto GRAINS
REFRESH:
  retain = 1
GRAINS:
  ;; 512 overlaps cover the maximum pitch and 80 ms grain at supported rates.
  rise:k = min(0.002, 0.5 / max(bandwidth, 1))
  signal:a fof gain, hz, frequency, octave, bandwidth, rise, grain, 0.006, \
    512, panaeolusVocalSine, panaeolusVocalRise, 2, 0, 1, retain
  rireturn
  xout signal
endop

opcode PanaeolusVocalGliss(hz:k, frequency:k, bandwidth:k, gain:k, octave:k, grain:k, sweep:k, phase:k):a
  ;; Refresh the finite sample counter, retaining active grains and phases.
  ;; A huge itotdur overflows fof's int32 counter; always starting with iskip=1
  ;; would instead retain stale grains when Csound reuses a finished voice.
  retain:i init 0
  nextRefresh:k init 1
  age:k timeinsts
  if (age >= nextRefresh) then
    nextRefresh = age + 1
    reinit REFRESH
  endif
  igoto GRAINS
REFRESH:
  retain = 1
GRAINS:
  ;; 512 overlaps cover the maximum pitch and 80 ms grain at supported rates.
  center:k = min(frequency, sr * 0.35 / max(1, pow(2, sweep / 12)))
  rise:k = min(0.002, 0.5 / max(bandwidth, 1))
  signal:a fof2 gain, hz, center, octave, bandwidth, rise, grain, 0.006, \
    512, panaeolusVocalSine, panaeolusVocalRise, 2, phase, sweep / 12, retain
  rireturn
  xout signal
endop

opcode PanaeolusVocalFilter(source:a, vowel:k, shift:k, width:k, ring:k):a
  freq0:k, band0:k, gain0:k = PanaeolusVowel(vowel, shift, width, 0)
  part0:a fofilter source * gain0, freq0, 0.001 * ring, ring / (3.141592653589793 * band0)
  freq1:k, band1:k, gain1:k = PanaeolusVowel(vowel, shift, width, 1)
  part1:a fofilter source * gain1, freq1, 0.001 * ring, ring / (3.141592653589793 * band1)
  freq2:k, band2:k, gain2:k = PanaeolusVowel(vowel, shift, width, 2)
  part2:a fofilter source * gain2, freq2, 0.001 * ring, ring / (3.141592653589793 * band2)
  freq3:k, band3:k, gain3:k = PanaeolusVowel(vowel, shift, width, 3)
  part3:a fofilter source * gain3, freq3, 0.001 * ring, ring / (3.141592653589793 * band3)
  freq4:k, band4:k, gain4:k = PanaeolusVowel(vowel, shift, width, 4)
  part4:a fofilter source * gain4, freq4, 0.001 * ring, ring / (3.141592653589793 * band4)
  sum:a = part0 + part1 + part2 + part3 + part4
  xout sum
endop
