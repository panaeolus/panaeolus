;; A held voice owns one route. Control edits keep its oscillator, envelopes,
;; and processors alive; a new definition or FX chain gets a fresh route.

opcode PanaeolusHeldStop(route:i, stopTime:i, stopLive:i):void
  if (stopLive == 1 && panaeolusHeldInstruments[route] > 0) ithen
    channel:S init PanaeolusVoiceChannel(route, -1)
    disabled:i = 0
    chnset disabled, channel
    turnoff2i panaeolusHeldInstruments[route] + route / 1024, 4, 0
    panaeolusHeldInstruments[route] = 0
  endif
  if (panaeolusRecording == 1 && panaeolusRecordedHeld[route] > 0) ithen
    line:S init sprintf("i 94 %.17g 0 %d -1 0\n", \
      max(0, stopTime - panaeolusRecordOrigin), route)
    PanaeolusRecordLine(line)
    line init sprintf("i 92 %.17g 0 %.17g\n", \
      max(0, stopTime - panaeolusRecordOrigin), panaeolusRecordedHeld[route])
    PanaeolusRecordLine(line)
    panaeolusRecordedHeld[route] = 0
    panaeolusRecordEnd = max(panaeolusRecordEnd, stopTime)
  endif
endop

opcode PanaeolusHeldTick(routeValue:MalValue, controls:MalValue, instrument:MalValue, \
                         values:MalValue, voiceDelay:i):i
  route:i = routeValue.number
  result:i = -1
  valid:i = routeValue.type == $MAL_NUMBER_TYPE && route > 0 && route < 256 && \
    route == int(route) && instrument.type == $MAL_CSOUND_INSTRUMENT_TYPE && \
    values.type == $MAL_VECTOR_TYPE
  if (valid == 1 && values.length > 0) ithen
    for index in [0 ... values.length - 1] do
      value:MalValue init MalAt(values, index)
      if (value.type != $MAL_NUMBER_TYPE || value.number - value.number != 0) ithen
        valid = 0
        break
      endif
    od
  endif
  if (valid == 0) ithen
    result = PanaeolusRoutingFailure("held voice: invalid route, instrument, or controls")
  elseif (panaeolusRouteActive[route] != 1) ithen
    result = PanaeolusRoutingFailure("held voice: route is no longer active")
  elseif (PanaeolusControlsValid(controls, panaeolusRouteCounts[route]) == 0) ithen
    result = PanaeolusRoutingFailure("held voice: effect controls do not match the route")
  else
    number:i = instrument.number
    if (number <= 0) ithen
      number = nstrnum(instrument.string)
    endif
    if (number < 1000 || number >= 9000 || number != int(number)) ithen
      result = PanaeolusRoutingFailure("held voice: instrument has no voice definition")
    elseif (panaeolusHeldInstruments[route] > 0 && panaeolusHeldInstruments[route] != number) ithen
      result = PanaeolusRoutingFailure("held voice: a new definition needs its own route")
    else
      now:i = times()
      PanaeolusRecordControls(route, controls, now)
      if (values.length > 0) ithen
        for index in [0 ... values.length - 1] do
          value:MalValue init MalAt(values, index)
          channel:S init PanaeolusVoiceChannel(route, index)
          chnset value.number, channel
          line:S init sprintf("i 94 %.17g 0 %d %d %.17g\n", \
            now - panaeolusRecordOrigin, route, index, value.number)
          PanaeolusRecordLine(line)
        od
      endif
      if (panaeolusHeldInstruments[route] == 0) ithen
        gate:S init PanaeolusVoiceChannel(route, -1)
        enabled:i = 1
        chnset enabled, gate
        line init sprintf("i 94 %.17g 0 %d -1 1\n", now - panaeolusRecordOrigin, route)
        PanaeolusRecordLine(line)
        instance:i = number + route / 1024
        liveLine:S init sprintf("i %.17g 0 -1 %d", instance, route)
        scoreline_i liveLine
        panaeolusHeldInstruments[route] = number
        noteStart:i = now + voiceDelay
        panaeolusRouteLastNote[route] = noteStart
        if (panaeolusRecording == 1) ithen
          panaeolusRecordedUsed[number] = 1
          panaeolusRecordedHeld[route] = instance
          line init sprintf("i %.17g %.17g -1 %d\n", instance, \
            noteStart - panaeolusRecordOrigin, route)
          PanaeolusRecordLine(line)
        endif
      endif
      ;; Tell the dispatcher that this tick already performed its action.
      result = 2
    endif
  endif
  xout result
endop
