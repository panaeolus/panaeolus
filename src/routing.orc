;; The Lisp bridge owns no DSP. Recorded CSDs include only the registered
;; immutable definitions and routing-support.orc, never this file.

declare PanaeolusHeldTick(route:MalValue, controls:MalValue, instrument:MalValue, values:MalValue, voiceDelay:i, releaseTime:i):i
declare PanaeolusHeldStop(route:i, stopTime:i, stopLive:i):void

panaeolusDefinitionNumbers@global:i[] init 16384
panaeolusDefinitionSources@global:S[] init 16384
panaeolusDefinitionCount@global:i init 0
panaeolusRecording@global:i init 0
panaeolusTakeReady@global:i init 0
panaeolusTakeDefinitions@global:i init 0
panaeolusRecordFailed@global:i init 0
panaeolusRecordOrigin@global:i init 0
panaeolusRecordEnd@global:i init 0
panaeolusRecordSeed@global:i init 1
panaeolusRecordedScore@global:S init ""
panaeolusRecordedChunks@global:S[] init 4096
panaeolusRecordedChunkCount@global:i init 0
panaeolusRecordedRoutes@global:i[] init 256
panaeolusRecordedEnds@global:i[] init 256
panaeolusRecordedUsed@global:i[] init 32768
panaeolusRecordedHeld@global:i[] init 256

opcode PanaeolusRoutingFailure(message:S):i
  chnset message, "__panaeolus_error"
  prints "Panaeolus: %s\n", message
  status:i = -1
  xout status
endop

opcode PanaeolusRoutingPending(token:i):MalValue
  pending:MalValue init MalResolveSymbol(panaeolusEnv, "panaeolus.routing/pending")
  values:MalValue init MalAtomValue(pending)
  key:MalValue init MalMkNumber(token)
  value:MalValue init MalMapGet(values, key)
  updated:MalValue init MalAtomSetValue(pending, MalMapDissoc(values, key))
  xout value
endop

opcode PanaeolusRecordLine(line:S):void
  if (panaeolusRecording == 1) ithen
    ;; Bound live string copying to one small chunk. Joining the complete take
    ;; belongs to export, after recording stops, not to the audio callback.
    if (strlen(panaeolusRecordedScore) + strlen(line) > 16384) ithen
      if (panaeolusRecordedChunkCount >= lenarray(panaeolusRecordedChunks)) ithen
        panaeolusRecordFailed = 1
        panaeolusRecording = 0
        panaeolusTakeReady = 0
        chnset 0, "__panaeolus_recording"
        chnset 0, "__panaeolus_take_ready"
        failed:i = PanaeolusRoutingFailure("record: score buffer is full; recording stopped and the incomplete take cannot be exported")
      else
        panaeolusRecordedChunks[panaeolusRecordedChunkCount] init panaeolusRecordedScore
        panaeolusRecordedChunkCount += 1
        panaeolusRecordedScore init ""
      endif
    endif
    if (panaeolusRecording == 1) ithen
      panaeolusRecordedScore strcat panaeolusRecordedScore, line
    endif
  endif
endop

opcode PanaeolusComment(text:S):S
  result:S init ""
  if (strlen(text) > 0) ithen
    for index in [0 ... strlen(text) - 1] do
      character:i = strchar:i(text, index)
      if (character == 10) ithen
        result strcat result, "\\n"
      elseif (character == 13) ithen
        result strcat result, "\\r"
      else
        result strcat result, strsub(text, index, index + 1)
      endif
    od
  endif
  xout result
endop

opcode PanaeolusControlsValid(controls:MalValue, count:i):i
  valid:i = controls.type == $MAL_VECTOR_TYPE && controls.length == count
  if (valid == 1 && controls.length > 0) ithen
    for stage in [0 ... controls.length - 1] do
      parameters:MalValue init MalAt(controls, stage)
      if (parameters.type != $MAL_VECTOR_TYPE) ithen
        valid = 0
        break
      elseif (parameters.length > 0) ithen
        for parameter in [0 ... parameters.length - 1] do
          value:MalValue init MalAt(parameters, parameter)
          if (value.type != $MAL_NUMBER_TYPE || value.number - value.number != 0) ithen
            valid = 0
            break
          endif
        od
      endif
    od
  endif
  xout valid
endop

opcode PanaeolusRecordControls(route:i, controls:MalValue, now:i):void
  if (controls.length > 0) ithen
    for stage in [0 ... controls.length - 1] do
      parameters:MalValue init MalAt(controls, stage)
      if (parameters.length > 0) ithen
        for parameter in [0 ... parameters.length - 1] do
          value:MalValue init MalAt(parameters, parameter)
          channel:S init PanaeolusControlChannel(route, stage, parameter)
          chnset value.number, channel
          if (panaeolusRecording == 1) ithen
            line:S init sprintf("i 91 %.17g 0 %d %d %d %.17g\n", \
              now - panaeolusRecordOrigin, route, stage, parameter, value.number)
            PanaeolusRecordLine(line)
          endif
        od
      endif
    od
  endif
endop

opcode PanaeolusRecordRouteStop(route:i, endTime:i):void
  if (panaeolusRecording == 1 && panaeolusRecordedRoutes[route] == 1) ithen
    relative:i = max(0, endTime - panaeolusRecordOrigin)
    if (panaeolusRouteCounts[route] > 0) ithen
      for stage in [0 ... panaeolusRouteCounts[route] - 1] do
        ;; Spell out p5: omitted score fields can carry forward a prior
        ;; held voice's release flag. Processors have already drained here.
        line:S init sprintf("i 92 %.17g 0 %.17g 0\n", relative, \
          panaeolusRouteStages[route][stage] + route / 1024)
        PanaeolusRecordLine(line)
      od
    endif
    line init sprintf("i 92 %.17g 0 %.17g 0\n", relative, 30000 + route / 1024)
    PanaeolusRecordLine(line)
    panaeolusRecordEnd = max(panaeolusRecordEnd, endTime)
    panaeolusRecordedRoutes[route] = 0
  endif
endop

opcode PanaeolusRegister(token:i):i
  pending:MalValue init PanaeolusRoutingPending(token)
  number:MalValue init MalMapGet(pending, MalMkKeyword("number"))
  source:MalValue init MalMapGet(pending, MalMkKeyword("source"))
  result:i = -1
  if (number.type != $MAL_NUMBER_TYPE || number.number < 0 || number.number >= 32768 || \
      number.number != int(number.number) || source.type != $MAL_STRING_TYPE) ithen
    result = PanaeolusRoutingFailure("register: expected an instrument number from 0 to 32767 and a source string")
  else
    code:S init source.string
    ;; csound/source shares its output with evalstr's compile return value.
    sentinel:S init "return 1\n"
    length:i = strlen(code)
    if (length >= strlen(sentinel)) ithen
      suffix:S init strsub(code, length - strlen(sentinel))
      if (strcmp(suffix, sentinel) == 0) ithen
        code init strsub(code, 0, length - strlen(sentinel))
      endif
    endif
    existing:i = -1
    if (panaeolusDefinitionCount > 0) ithen
      for index in [0 ... panaeolusDefinitionCount - 1] do
        if (panaeolusDefinitionNumbers[index] == number.number) ithen
          existing = index
          break
        endif
      od
    endif
    if (existing >= 0) ithen
      if (strcmp(panaeolusDefinitionSources[existing], code) == 0) ithen
        result = number.number
      else
        result = PanaeolusRoutingFailure("register: a definition number cannot change its source")
      endif
    elseif (panaeolusDefinitionCount == lenarray(panaeolusDefinitionNumbers)) ithen
      result = PanaeolusRoutingFailure("register: definition registry is full")
    else
      panaeolusDefinitionNumbers[panaeolusDefinitionCount] = number.number
      panaeolusDefinitionSources[panaeolusDefinitionCount] init code
      panaeolusDefinitionCount += 1
      result = number.number
    endif
  endif
  xout result
endop

opcode PanaeolusRouteOpenData(pending:MalValue):i
  stages:MalValue init MalMapGet(pending, MalMkKeyword("stages"))
  controls:MalValue init MalMapGet(pending, MalMkKeyword("controls"))
  tail:MalValue init MalMapGet(pending, MalMkKeyword("tail"))
  id:MalValue init MalMapGet(pending, MalMkKeyword("id"))
  route:i = -1
  valid:i = stages.type == $MAL_VECTOR_TYPE && stages.length <= 8 && \
    PanaeolusControlsValid(controls, stages.length) == 1 && \
    tail.type == $MAL_NUMBER_TYPE && tail.number >= 0 && tail.number - tail.number == 0
  if (valid == 1 && stages.length > 0) ithen
    for stage in [0 ... stages.length - 1] do
      definition:MalValue init MalAt(stages, stage)
      if (definition.type != $MAL_NUMBER_TYPE || definition.number != int(definition.number) || \
          definition.number < 10000 + stage * 1000 || definition.number >= 11000 + stage * 1000) ithen
        valid = 0
        break
      endif
    od
  endif
  if (valid == 0) ithen
    route = PanaeolusRoutingFailure("route: invalid stages, controls, or tail")
  else
    for candidate in [1 ... 255] do
      if (panaeolusRouteActive[candidate] == 0) ithen
        route = candidate
        break
      endif
    od
    if (route < 0) ithen
      route = PanaeolusRoutingFailure("route: all 255 routes are active or draining")
    else
      now:i = times()
      panaeolusRouteCounts[route] = stages.length
      panaeolusRouteTails[route] = tail.number
      panaeolusRouteLastNote[route] = now
      panaeolusRouteEnds[route] init 0
      panaeolusRouteActive[route] init 1
      if (panaeolusRecording == 1) ithen
        panaeolusRecordedRoutes[route] = 1
        panaeolusRecordedEnds[route] = now
        ;; Readable printing escapes newlines in IDs so comments stay comments.
        identity:S init PanaeolusComment(pr_str_with_readability(id, 1))
        comment:S init sprintf("; route %d, loop %s, start %.17g, stages", route, \
          identity, now - panaeolusRecordOrigin)
        if (stages.length > 0) ithen
          for stage in [0 ... stages.length - 1] do
            definition:MalValue init MalAt(stages, stage)
            comment strcat comment, sprintf(" %d", definition.number)
          od
        endif
        comment strcat comment, "\n"
        PanaeolusRecordLine(comment)
      endif
      PanaeolusRecordControls(route, controls, now)
      if (stages.length > 0) ithen
        for stage in [0 ... stages.length - 1] do
          definition:MalValue init MalAt(stages, stage)
          panaeolusRouteStages[route][stage] = definition.number
          if (panaeolusRecording == 1) ithen
            panaeolusRecordedUsed[definition.number] = 1
          endif
          instance:i = definition.number + route / 1024
          liveLine:S init sprintf("i %.17g 0 -1 %d %d", instance, route, stage)
          scoreline_i liveLine
          line:S init sprintf("i %.17g %.17g -1 %d %d\n", instance, \
            now - panaeolusRecordOrigin, route, stage)
          PanaeolusRecordLine(line)
        od
      endif
      instance = 30000 + route / 1024
      liveLine init sprintf("i %.17g 0 -1 %d %d", instance, route, stages.length)
      scoreline_i liveLine
      line init sprintf("i %.17g %.17g -1 %d %d\n", instance, \
        now - panaeolusRecordOrigin, route, stages.length)
      PanaeolusRecordLine(line)
    endif
  endif
  xout route
endop

opcode PanaeolusRouteOpen(token:i):i
  xout PanaeolusRouteOpenData(PanaeolusRoutingPending(token))
endop

opcode PanaeolusBindRoute(entry:MalValue, previous:MalValue):MalValue
  callback:MalValue init MalMapGet(entry, MalMkKeyword("callback"))
  metadata:MalValue init MalMeta(callback)
  plan:MalValue init MalMapGet(metadata, MalMkKeyword("panaeolus/route-plan"))
  result:MalValue init entry
  if (plan.type == $MAL_HASH_MAP_TYPE) ithen
    oldCallback:MalValue init MalMapGet(previous, MalMkKeyword("callback"))
    oldRoute:MalValue init MalMapGet(MalMeta(oldCallback), MalMkKeyword("panaeolus/route"))
    topology:MalValue init MalMapGet(plan, MalMkKeyword("topology"))
    voice:MalValue init MalMapGet(plan, MalMkKeyword("voice"))
    number:MalValue init MalMapGet(oldRoute, MalMkKeyword("number"))
    route:i = -1
    if (number.type == $MAL_NUMBER_TYPE && number.number > 0 && number.number < 256) ithen
      if (panaeolusRouteActive[number.number] == 1 && \
          MalEquals(topology, MalMapGet(oldRoute, MalMkKeyword("topology"))) == 1 && \
          MalEquals(voice, MalMapGet(oldRoute, MalMkKeyword("voice"))) == 1) ithen
        route = number.number
      endif
    endif
    if (route < 0) ithen
      route = PanaeolusRouteOpenData(plan)
    endif
    if (route < 0) ithen
      result init MalMkError("routing: could not activate the prepared effect chain")
    else
      descriptor:MalValue init MalMkValue($MAL_HASH_MAP_TYPE)
      descriptor init MalMapAssoc(descriptor, MalMkKeyword("number"), MalMkNumber(route))
      descriptor init MalMapAssoc(descriptor, MalMkKeyword("topology"), topology)
      descriptor init MalMapAssoc(descriptor, MalMkKeyword("voice"), voice)
      tick:MalValue init MalMapGet(metadata, MalMkKeyword("panaeolus/tick"))
      tick init MalMapAssoc(tick, MalMkKeyword("route"), MalMkNumber(route))
      metadata init MalMapAssoc(metadata, MalMkKeyword("panaeolus/route"), descriptor)
      metadata init MalMapAssoc(metadata, MalMkKeyword("panaeolus/tick"), tick)
      metadata init MalMapDissoc(metadata, MalMkKeyword("panaeolus/route-plan"))
      result init MalMapAssoc(entry, MalMkKeyword("routing"), tick)
      result init MalMapAssoc(result, MalMkKeyword("callback"), MalWithMeta(callback, metadata))
    endif
  endif
  xout result
endop

opcode PanaeolusTick(routeValue:MalValue, controls:MalValue, instrument:MalValue, \
                      duration:MalValue, values:MalValue, voiceDelay:i):i
  route:i = routeValue.number
  result:i = 1
  if (routeValue.type != $MAL_NUMBER_TYPE || route < 0 || route > 255 || route != int(route) || \
      duration.type != $MAL_NUMBER_TYPE || duration.number <= 0 || duration.number - duration.number != 0 || \
      instrument.type != $MAL_CSOUND_INSTRUMENT_TYPE) ithen
    result = PanaeolusRoutingFailure("route tick: invalid route, duration, or instrument")
  elseif (route > 0 && panaeolusRouteActive[route] != 1) ithen
    result = PanaeolusRoutingFailure("route tick: route is no longer active")
  elseif (PanaeolusControlsValid(controls, panaeolusRouteCounts[route]) == 0) ithen
    result = PanaeolusRoutingFailure("route tick: controls do not match the route")
  else
    now:i = times()
    noteStart:i = now + voiceDelay
    PanaeolusRecordControls(route, controls, now)
    panaeolusRouteLastNote[route] = max(panaeolusRouteLastNote[route], noteStart + duration.number)
    panaeolusLastVoiceEnd = max(panaeolusLastVoiceEnd, noteStart + duration.number)
    if (panaeolusRecording == 1) ithen
      number:i = instrument.number
      if (number <= 0) ithen
        number = nstrnum(instrument.string)
      endif
      if (number <= 0 || number >= 32768 || number != int(number)) ithen
        result = PanaeolusRoutingFailure("record: instrument has no Csound number")
      else
        panaeolusRecordedUsed[number] = 1
        line:S init sprintf("i %d %.17g %.17g", number, \
          noteStart - panaeolusRecordOrigin, duration.number)
        if (values.length > 0) ithen
          for index in [0 ... values.length - 1] do
            value:MalValue init MalAt(values, index)
            line strcat line, sprintf(" %.17g", value.number)
          od
        endif
        line strcat line, sprintf(" %d\n", route)
        PanaeolusRecordLine(line)
        panaeolusRecordedEnds[route] = max(panaeolusRecordedEnds[route], noteStart + duration.number)
        panaeolusRecordEnd = max(panaeolusRecordEnd, noteStart + duration.number)
      endif
    endif
  endif
  xout result
endop

opcode PanaeolusRouteTick(token:i):i
  pending:MalValue init PanaeolusRoutingPending(token)
  route:MalValue init MalMapGet(pending, MalMkKeyword("route"))
  controls:MalValue init MalMapGet(pending, MalMkKeyword("controls"))
  instrument:MalValue init MalMapGet(pending, MalMkKeyword("instrument"))
  duration:MalValue init MalMapGet(pending, MalMkKeyword("duration"))
  values:MalValue init MalMapGet(pending, MalMkKeyword("values"))
  result:i = PanaeolusTick(route, controls, instrument, duration, values, 0)
  xout result
endop

opcode PanaeolusSample(value:MalValue, step:i):MalValue
  result:MalValue init value
  if (value.type == $MAL_VECTOR_TYPE && value.length > 0) ithen
    result init MalAt(value, step % value.length)
  endif
  xout result
endop

opcode PanaeolusSampleValues(values:MalValue, step:i):MalValue
  result:MalValue init MalMkValue($MAL_VECTOR_TYPE)
  if (values.length > 0) ithen
    for index in [0 ... values.length - 1] do
      value:MalValue init PanaeolusSample(MalAt(values, index), step)
      result init MalAppendValue(result, value)
    od
  endif
  xout result
endop

;; Sample each pattern once. Generated callbacks can submit their note here;
;; ordinary callbacks and replacements of csound/event still run in Lisp.
opcode PanaeolusPrepareTick(entry:MalValue, eventMap:MalValue):i
  routing:MalValue init MalMapGet(entry, MalMkKeyword("routing"))
  result:i = 0
  if (routing.type == $MAL_HASH_MAP_TYPE) ithen
    stepValue:MalValue init MalMapGet(eventMap, MalMkKeyword("step"))
    step:i = stepValue.number
    route:MalValue init MalMapGet(routing, MalMkKeyword("route"))
    instrument:MalValue init MalMapGet(routing, MalMkKeyword("instrument"))
    duration:MalValue init PanaeolusSample(MalMapGet(routing, MalMkKeyword("duration")), step)
    values:MalValue init PanaeolusSampleValues(MalMapGet(routing, MalMkKeyword("values")), step)
    patterns:MalValue init MalMapGet(routing, MalMkKeyword("controls"))
    controls:MalValue init MalMkValue($MAL_VECTOR_TYPE)
    if (patterns.length > 0) ithen
      for stage in [0 ... patterns.length - 1] do
        sampled:MalValue init PanaeolusSampleValues(MalAt(patterns, stage), step)
        controls init MalAppendValue(controls, sampled)
      od
    endif
    ;; Dispatch runs after the audio graph. Its queued voice starts in the
    ;; following control block; controls already change in this block.
    legato:MalValue init MalMapGet(routing, MalMkKeyword("legato"))
    if (legato.type == $MAL_TRUE_TYPE) ithen
      releaseValue:MalValue init PanaeolusSample(MalMapGet(routing, MalMkKeyword("release")), step)
      releaseTime:i = (releaseValue.type == $MAL_NUMBER_TYPE ? releaseValue.number : 0)
      result = PanaeolusHeldTick(route, controls, instrument, values, ksmps / sr, releaseTime)
      heldCallback:MalValue init MalMapGet(entry, MalMkKeyword("callback"))
      heldGenerated:MalValue init MalMapGet(routing, MalMkKeyword("callback"))
      if (result == 2 && (heldCallback.type != $MAL_FUNCTION_TYPE || \
          heldGenerated.type != $MAL_FUNCTION_TYPE || heldCallback.number != heldGenerated.number)) ithen
        result = 1
      endif
      igoto PREPARE_TICK_DONE
    endif
    result = PanaeolusTick(route, controls, instrument, duration, values, ksmps / sr)
    if (result >= 0) ithen
      callback:MalValue init MalMapGet(entry, MalMkKeyword("callback"))
      generated:MalValue init MalMapGet(routing, MalMkKeyword("callback"))
      ;; Metadata can be copied onto a different function. Never skip that
      ;; function merely because it carries an instrument's route metadata.
      if (callback.type == $MAL_FUNCTION_TYPE && generated.type == $MAL_FUNCTION_TYPE && \
          callback.number == generated.number && lenarray(callback.env) > 0) ithen
        eventFn:MalValue init MalResolveSymbol(callback.env[0], "csound/event")
        if (eventFn.type == $MAL_BUILTIN_TYPE && strcmp(eventFn.string, "csound/event") == 0) ithen
          arguments:MalValue init MalAppendValue(values, route)
          submitted:MalValue init MalScheduleCsoundInstrument(instrument, arguments, 0, duration.number)
          if (submitted.type == $MAL_ERROR_TYPE) ithen
            result = PanaeolusRoutingFailure(submitted.string)
          else
            result = 2
          endif
        endif
      endif
    endif
  endif
PREPARE_TICK_DONE:
  xout result
endop

opcode PanaeolusRouteClose(route:i):i
  result:i = 0
  if (route > 0 && route < 256 && route == int(route)) ithen
    if (panaeolusRouteActive[route] == 1) ithen
      now:i = times()
      PanaeolusHeldStop(route, now, 1)
      deadline:i = max(now, panaeolusRouteLastNote[route]) + panaeolusRouteTails[route]
      panaeolusRouteEnds[route] init deadline
      panaeolusRouteActive[route] init 2
      PanaeolusRecordRouteStop(route, deadline)
      result = 1
    endif
  endif
  xout result
endop

opcode PanaeolusRecordStart():i
  result:i = 1
  now:i = times()
  if (panaeolusRecording == 1) ithen
    result = PanaeolusRoutingFailure("record: a take is already running")
  elseif (panaeolusLastVoiceEnd > now) ithen
    result = PanaeolusRoutingFailure("record: wait for existing notes to finish")
  else
    for route in [1 ... 255] do
      if (panaeolusRouteActive[route] != 0) ithen
        result = PanaeolusRoutingFailure("record: stop loops and wait for their tails before starting")
        break
      endif
    od
    if (result == 1) ithen
      ;; Reset Csound's seedable generator for this clean-start take. This
      ;; covers randomi/randomh in the flanger, not every random opcode family.
      seed panaeolusRecordSeed
      panaeolusRecordOrigin = now
      panaeolusRecordEnd = now
      panaeolusRecordedScore init ""
      panaeolusRecordedChunks init 4096
      panaeolusRecordedChunkCount = 0
      panaeolusRecordedRoutes init 256
      panaeolusRecordedEnds init 256
      panaeolusRecordedUsed init 32768
      panaeolusRecordedHeld init 256
      panaeolusTakeReady = 0
      panaeolusRecordFailed = 0
      panaeolusRecording = 1
      chnset 1, "__panaeolus_recording"
      chnset 0, "__panaeolus_take_ready"
      chnset "", "__panaeolus_error"
    endif
  endif
  xout result
endop

opcode PanaeolusRecordStop():i
  result:i = 1
  if (panaeolusRecordFailed == 1) ithen
    result = PanaeolusRoutingFailure("record: recording stopped after an error; the incomplete take cannot be exported")
  elseif (panaeolusRecording != 1) ithen
    result = PanaeolusRoutingFailure("record: no take is running")
  else
    now:i = times()
    panaeolusRecordEnd = max(panaeolusRecordEnd, now)
    for route in [1 ... 255] do
      if (panaeolusRecordedRoutes[route] == 1) ithen
        PanaeolusHeldStop(route, now, 0)
        deadline:i = max(now, panaeolusRecordedEnds[route]) + panaeolusRouteTails[route]
        PanaeolusRecordRouteStop(route, deadline)
      endif
    od
    panaeolusRecording = 0
    if (panaeolusRecordFailed == 0) ithen
      panaeolusTakeReady = 1
      panaeolusTakeDefinitions = panaeolusDefinitionCount
      panaeolusTakeSamples = panaeolusSampleCount
      panaeolusTakeAnalyses = panaeolusAnalysisCount
      chnset 1, "__panaeolus_take_ready"
    else
      result = PanaeolusRoutingFailure("record: recording stopped after an error; the incomplete take cannot be exported")
    endif
    chnset 0, "__panaeolus_recording"
  endif
  xout result
endop

opcode PanaeolusExport():i
  result:i = 1
  if (panaeolusRecordFailed == 1) ithen
    result = PanaeolusRoutingFailure("export: the recorded take is incomplete and cannot be exported")
  elseif (panaeolusRecording == 1 || panaeolusTakeReady == 0) ithen
    result = PanaeolusRoutingFailure("export: stop a recorded take first")
  else
    base:i = -1
    if (panaeolusTakeDefinitions > 0) ithen
      for index in [0 ... panaeolusTakeDefinitions - 1] do
        if (panaeolusDefinitionNumbers[index] == 0) ithen
          base = index
          break
        endif
      od
    endif
    if (base < 0) ithen
      result = PanaeolusRoutingFailure("export: routing support has not been registered")
    else
      sampleFiles:S init PanaeolusSampleFiles()
      analysisFiles:S init PanaeolusAnalysisExport()
      sampleStatus:i chnget "__panaeolus_sample_export_status"
      if (sampleStatus < 0) ithen
        chnset "", "__panaeolus_export"
        result = PanaeolusRoutingFailure(chnget:S("__panaeolus_error"))
        igoto EXPORT_DONE
      endif
      document:S init sprintf("<CsoundSynthesizer>\n<CsOptions>\n-d -m0\n</CsOptions>\n<CsInstruments>\nsr = %.17g\nksmps = %d\nnchnls = %d\n0dbfs = %.17g\nseed %.17g\n", sr, ksmps, nchnls, 0dbfs, panaeolusRecordSeed)
      document strcat document, panaeolusDefinitionSources[base]
      document strcat document, "\n"
      document strcat document, PanaeolusSampleOrchestra()
      document strcat document, chnget:S("__panaeolus_analysis_orchestra")
      if (panaeolusTakeDefinitions > 0) ithen
        for index in [0 ... panaeolusTakeDefinitions - 1] do
          if (index != base && panaeolusRecordedUsed[panaeolusDefinitionNumbers[index]] == 1) ithen
            document strcat document, panaeolusDefinitionSources[index]
            document strcat document, "\n"
          endif
        od
      endif
      document strcat document, "</CsInstruments>\n<CsScore>\ni 30000 0 -1 0 0\n"
      if (panaeolusRecordedChunkCount > 0) ithen
        for index in [0 ... panaeolusRecordedChunkCount - 1] do
          document strcat document, panaeolusRecordedChunks[index]
        od
      endif
      document strcat document, panaeolusRecordedScore
      duration:i = max(ksmps / sr, panaeolusRecordEnd - panaeolusRecordOrigin + ksmps / sr)
      document strcat document, sprintf("f 0 %.17g\ne\n</CsScore>\n", duration)
      document strcat document, sampleFiles
      document strcat document, analysisFiles
      document strcat document, "</CsoundSynthesizer>\n"
      chnset document, "__panaeolus_export"
    endif
  endif
EXPORT_DONE:
  xout result
endop
