;; Adapted from Panaeolus resources/fx/flanger.orc.
;; The original stereo UDO credits Iain McCurdy.
;; Source: https://github.com/panaeolus/panaeolus/blob/master/resources/fx/flanger.orc
;; Upstream license: https://github.com/panaeolus/panaeolus/blob/master/LICENSE (GPL v3).
;; Changes: private tables, local controls, and stereo output without routing.

;; Csound allocates the table numbers; no fixed IDs can collide with a voice.
giPanaeolusFlangerParabola ftgen 0, 0, 131072, 19, 0.5, 1, 180, 1
giPanaeolusFlangerSine ftgen 0, 0, 65537, 10, 1
giPanaeolusFlangerTriangle ftgen 0, 0, 131072, 7, 0, 65536, 1, 65536, 0

;; rate is Hz; depth and smooth are seconds. The caller validates controls.
;; Keep one instance alive between notes so its LFO and delay retain their state.
opcode PanaeolusFlanger(left:a, right:a, rate:k, depth:k, fback:k, shape:k, smooth:k):(a,a)
  smoothedRate:k = portk(rate, smooth)
  smoothedDepth:k = portk(depth, smooth)
  smoothedFeedback:k = portk(fback, smooth)

  if shape == 1 then
    modulation:a = oscili(smoothedDepth, smoothedRate, giPanaeolusFlangerParabola)
  elseif shape == 2 then
    modulation = abs:a(oscili(smoothedDepth, smoothedRate, giPanaeolusFlangerSine))
  elseif shape == 3 then
    modulation = oscili(smoothedDepth, smoothedRate, giPanaeolusFlangerTriangle)
  elseif shape == 4 then
    modulation = randomi:a(0, smoothedDepth, smoothedRate, 1)
  else
    modulation = randomh:a(0, smoothedDepth, smoothedRate, 1)
  endif

  wetLeft:a = dcblock(flanger(left, modulation, smoothedFeedback, 1.2))
  wetRight:a = dcblock(flanger(right, modulation, smoothedFeedback, 1.2))
  xout wetLeft, wetRight
endop
