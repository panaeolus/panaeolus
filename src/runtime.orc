;; Load make-a-lisp.orc first. All Lisp source is embedded by the build.
;; The clock counts beats in Csound's audio thread; JS never schedules ticks.

declare PanaeolusPrepareTick(entry:MalValue, eventMap:MalValue):i
declare PanaeolusBindRoute(entry:MalValue, previous:MalValue):MalValue
declare PanaeolusRouteClose(route:i):i
declare PanaeolusLoopStopValue(request:MalValue):MalValue
declare PanaeolusStopChildren(entry:MalValue):MalValue
declare PanaeolusNotifyRemoval(entry:MalValue):void
declare PanaeolusCompositionTick(entry:MalValue, eventMap:MalValue):i

panaeolusBeat@global:k init 0
panaeolusClockError@global:k init 0
panaeolusBpm@global:k init 120
panaeolusGenerations@global:i[] init 128
panaeolusEntries@global:MalValue[] init 128
panaeolusIndices@global:i[] init 128
panaeolusCycles@global:i[] init 128
panaeolusTargets@global:i[] init 128
panaeolusOrigins@global:i[] init 128
panaeolusPeriods@global:i[] init 128
panaeolusPositions@global:MalValue[] init 128
panaeolusEvalJob@global:k init -1

opcode PanaeolusTempo(bpm:i):i
  panaeolusBpm init bpm
  xout bpm
endop

opcode PanaeolusBoot():MalEnv
  env:MalEnv init @@PANAEO_ENV@@
  fxNs:i = MalNsEnsure(env, "panaeolus.fx", 1)
  flanger:MalValue init MalMkCsoundOpcode("PanaeolusFlanger", "a,a")
  flanger init MalAppendValue(flanger, MalCsoundSignature("a,a", "a,a,k,k,k,k,k", 7, "", 0))
  env init MalEnvSet(env, "panaeolus.fx/flanger-node", flanger)
  cutter:MalValue init MalMkCsoundOpcode("PanaeolusBBCut", "a,a")
  cutter init MalAppendValue(cutter, MalCsoundSignature("a,a", "a,a,k,k,k,k,k,k,k,k", 10, "", 0))
  env init MalEnvSet(env, "panaeolus.fx/bbcut-node", cutter)
  samplerNs:i = MalNsEnsure(env, "panaeolus.sampler", 1)
  sampler:MalValue init MalMkCsoundOpcode("PanaeolusSampler", "a,a")
  sampler init MalAppendValue(sampler, MalCsoundSignature("a,a", "i,k,k,k,k,k,i,k", 8, "", 0))
  env init MalEnvSet(env, "panaeolus.sampler/player-node", sampler)
  pluckNs:i = MalNsEnsure(env, "panaeolus.instruments.pluck", 1)
  pluck:MalValue init MalMkCsoundOpcode("PanaeolusPluck", "a,a")
  pluck init MalAppendValue(pluck, MalCsoundSignature("a,a", "i,i,i,i,i,i,i", 7, "", 0))
  env init MalEnvSet(env, "panaeolus.instruments.pluck/string-node", pluck)
  fm:MalValue init MalMkCsoundOpcode("PanaeolusFMPluck", "a,a")
  fm init MalAppendValue(fm, MalCsoundSignature("a,a", "i,i,i,i,i,i,i", 7, "", 0))
  env init MalEnvSet(env, "panaeolus.instruments.pluck/fm-node", fm)
  synthNs:i = MalNsEnsure(env, "panaeolus.instruments.synth", 1)
  hammer:MalValue init MalMkCsoundOpcode("PanaeolusHammer", "a,a")
  hammer init MalAppendValue(hammer, MalCsoundSignature("a,a", "i,i,i,i,i,i,i", 7, "", 0))
  env init MalEnvSet(env, "panaeolus.instruments.synth/hammer-node", hammer)
  lush:MalValue init MalMkCsoundOpcode("PanaeolusLush", "a,a")
  lush init MalAppendValue(lush, MalCsoundSignature("a,a", "k,k,k,k,k,k,k,k,k,k,k,k,k", 13, "", 0))
  env init MalEnvSet(env, "panaeolus.instruments.synth/lush-node", lush)
  routingNs:i = MalNsEnsure(env, "panaeolus.routing", 1)
  heldGuard:MalValue init MalMkCsoundOpcode("PanaeolusHeldGuard", "")
  heldGuard init MalAppendValue(heldGuard, MalCsoundSignature("", "i", 1, "", 0))
  env init MalEnvSet(env, "panaeolus.routing/held-guard-node", heldGuard)
  source:S init @@PANAEO_LISP@@
  result:MalValue, env = MalEvalFormsEnv(source, env)
  if (result.type == $MAL_ERROR_TYPE) ithen
    prints "Panaeolus bootstrap failed: %s\n", result.string
    exitnow(1)
  endif
  xout env
endop

panaeolusEnv@global:MalEnv init PanaeolusBoot()
;; These private atoms keep their identity for the engine's lifetime. Resolve
;; them once instead of walking Lisp bindings for each audio event.
panaeolusLoopStateAtom@global:MalValue init MalResolveSymbol(panaeolusEnv, "panaeolus.event-loop/state")
panaeolusRemoveHookAtom@global:MalValue init MalResolveSymbol(panaeolusEnv, "panaeolus.event-loop/on-remove")
panaeolusPerformancesAtom@global:MalValue init MalResolveSymbol(panaeolusEnv, "panaeolus.composition/performances")
panaeolusPlaybacksAtom@global:MalValue init MalResolveSymbol(panaeolusEnv, "panaeolus.composition/playbacks")

opcode PanaeolusTarget(slot:i, index:i, cycle:i):i
  position:MalValue init MalAt(panaeolusPositions[slot], index)
  xout panaeolusOrigins[slot] + cycle * panaeolusPeriods[slot] + position.number
endop

opcode PanaeolusQueue(slot:i, generation:i):i
  ;; Lisp owns validation and named loops. Snapshot the registered callback
  ;; once so the audio thread need only advance numbers and call the function.
  stateAtom:MalValue init panaeolusLoopStateAtom
  state:MalValue init MalAtomValue(stateAtom)
  slots:MalValue init MalMapGet(state, MalMkKeyword("slots"))
  panaeolusEntries[slot] init MalMapGet(slots, MalMkNumber(slot))
  durations:MalValue init MalMapGet(panaeolusEntries[slot], MalMkKeyword("durations"))
  positions:MalValue init MalMkValue($MAL_VECTOR_TYPE)
  period:i = 0
  correction:i = 0
  ;; Compensated summation retains small durations when building the cycle.
  for index in [0 ... durations.length - 1] do
    positions init MalAppendValue(positions, MalMkNumber(period))
    duration:MalValue init MalAt(durations, index)
    increment:i = duration.number - correction
    total:i = period + increment
    correction = (total - period) - increment
    period = total
  od
  panaeolusPositions[slot] init positions
  panaeolusPeriods[slot] = period
  ;; Read the clock here: printing it through Lisp str would lose precision.
  beat:i = i(panaeolusBeat)
  offset:MalValue init MalMapGet(panaeolusEntries[slot], MalMkKeyword("offset"))
  at:MalValue init MalMapGet(panaeolusEntries[slot], MalMkKeyword("at"))
  if (at.type == $MAL_NUMBER_TYPE) ithen
    beat = at.number
  elseif (offset.type == $MAL_NUMBER_TYPE) ithen
    phase:i = offset.number % period
    ;; Allow only floating-point noise at a boundary, not a late musical step.
    tolerance:i = 8 * 2.220446049250313e-16 * max(1, abs(beat), abs(phase))
    beat = phase + ceil((beat - phase - tolerance) / period) * period
  endif
  panaeolusOrigins[slot] = beat
  panaeolusIndices[slot] = 0
  panaeolusCycles[slot] = 0
  panaeolusTargets[slot] = beat
  panaeolusGenerations[slot] = generation
  ;; Held notes with the same p1 tie together. Each slot needs its own p1.
  waiter:i = nstrnum("PanaeolusWait") + (slot + 1) / 1024
  limit:MalValue init MalMapGet(panaeolusEntries[slot], MalMkKeyword("until"))
  wake:i = limit.type == $MAL_NUMBER_TYPE ? min(beat, limit.number) : beat
  schedule(waiter, 0, -1, slot, generation, wake)
  queued:i = 0
  ;; A literal xout aliases Csound's constant storage when a caller reuses
  ;; its result variable. Keep this return in writable local storage.
  xout queued
endop

opcode PanaeolusLoopRequest(token:i):MalValue
  requests:MalValue init MalResolveSymbol(panaeolusEnv, "panaeolus.event-loop/requests")
  values:MalValue init MalAtomValue(requests)
  key:MalValue init MalMkNumber(token)
  value:MalValue init MalMapGet(values, key)
  updated:MalValue init MalAtomSetValue(requests, MalMapDissoc(values, key))
  xout value
endop

opcode PanaeolusLoopReply(token:i, value:MalValue):void
  responses:MalValue init MalResolveSymbol(panaeolusEnv, "panaeolus.event-loop/responses")
  updated:MalValue init MalAtomSetValue(responses, \
    MalMapAssoc(MalAtomValue(responses), MalMkNumber(token), value))
endop

opcode PanaeolusCloseEntryRoute(entry:MalValue, replacement:MalValue):void
  callback:MalValue init MalMapGet(entry, MalMkKeyword("callback"))
  route:MalValue init MalMapGet(MalMeta(callback), MalMkKeyword("panaeolus/route"))
  number:MalValue init MalMapGet(route, MalMkKeyword("number"))
  nextCallback:MalValue init MalMapGet(replacement, MalMkKeyword("callback"))
  nextRoute:MalValue init MalMapGet(MalMeta(nextCallback), MalMkKeyword("panaeolus/route"))
  nextNumber:MalValue init MalMapGet(nextRoute, MalMkKeyword("number"))
  if (number.type == $MAL_NUMBER_TYPE) ithen
    if (nextNumber.type != $MAL_NUMBER_TYPE || nextNumber.number != number.number) ithen
      ;; Route cleanup belongs to the commit; a later Lisp hook can yield or fail.
      closed:i = PanaeolusRouteClose(number.number)
    endif
  endif
endop

opcode PanaeolusLoopStartValue(request:MalValue):MalValue
  ;; Choose the slot, store its new generation, and activate it in one step.
  ;; A callback can run while Lisp prepares the entry, but cannot see a half edit.
  entry:MalValue init request
  stateAtom:MalValue init panaeolusLoopStateAtom
  state:MalValue init MalAtomValue(stateAtom)
  names:MalValue init MalMapGet(state, MalMkKeyword("names"))
  slots:MalValue init MalMapGet(state, MalMkKeyword("slots"))
  id:MalValue init MalMapGet(entry, MalMkKeyword("id"))
  previous:MalValue init MalMapGet(names, id)
  slot:i = -1
  if (previous.type == $MAL_NUMBER_TYPE) ithen
    slot = previous.number
  else
    for candidate in [0 ... 127] do
      value:MalValue init MalMapGet(slots, MalMkNumber(candidate))
      if (value.type == $MAL_NIL_TYPE) ithen
        slot = candidate
        break
      endif
    od
  endif
  result:MalValue init MalMkError("start!: at most 128 loops can run at once")
  if (slot < 0) ithen
    prints "Panaeolus: start!: at most 128 loops can run at once\n"
  else
    key:MalValue init MalMkNumber(slot)
    oldEntry:MalValue init MalMapGet(slots, key)
    entry init PanaeolusBindRoute(entry, oldEntry)
    if (entry.type == $MAL_ERROR_TYPE) ithen
      result init entry
      igoto LOOP_START_DONE
    endif
    serial:MalValue init MalMapGet(state, MalMkKeyword("serial"))
    generation:i = serial.number + 1
    entry init MalMapAssoc(entry, MalMkKeyword("generation"), MalMkNumber(generation))
    entry init MalMapAssoc(entry, MalMkKeyword("slot"), key)
    updated:MalValue init MalMapAssoc(state, MalMkKeyword("serial"), MalMkNumber(generation))
    updated init MalMapAssoc(updated, MalMkKeyword("names"), MalMapAssoc(names, id, key))
    updated init MalMapAssoc(updated, MalMkKeyword("slots"), MalMapAssoc(slots, key, entry))
    stored:MalValue init MalAtomSetValue(stateAtom, updated)
    queued:i = PanaeolusQueue(slot, generation)
    if (queued < 0) ithen
      stored init MalAtomSetValue(stateAtom, state)
      PanaeolusCloseEntryRoute(entry, oldEntry)
      result init MalMkError("start!: could not schedule the loop")
    else
      PanaeolusCloseEntryRoute(oldEntry, entry)
      removedChildren:MalValue init PanaeolusStopChildren(oldEntry)
      if (removedChildren.length > 0) ithen
        oldEntry init MalMapAssoc(oldEntry, MalMkKeyword("removed-children"), removedChildren)
      endif
      result init MalMkList2(oldEntry, entry)
    endif
  endif
LOOP_START_DONE:
  xout result
endop

opcode PanaeolusLoopStart(token:i):i
  result:MalValue init PanaeolusLoopStartValue(PanaeolusLoopRequest(token))
  PanaeolusLoopReply(token, result)
  status:i = result.type == $MAL_ERROR_TYPE ? -1 : 0
  xout status
endop

opcode PanaeolusLoopStopValue(request:MalValue):MalValue
  stateAtom:MalValue init panaeolusLoopStateAtom
  state:MalValue init MalAtomValue(stateAtom)
  names:MalValue init MalMapGet(state, MalMkKeyword("names"))
  slots:MalValue init MalMapGet(state, MalMkKeyword("slots"))
  key:MalValue init MalMapGet(request, MalMkKeyword("slot"))
  if (key.type == $MAL_NIL_TYPE) ithen
    key init MalMapGet(names, MalMapGet(request, MalMkKeyword("id")))
  endif
  removed:MalValue init MalMkValue($MAL_NIL_TYPE)
  if (key.type == $MAL_NUMBER_TYPE) ithen
    entry:MalValue init MalMapGet(slots, key)
    if (entry.type == $MAL_HASH_MAP_TYPE) ithen
      expected:MalValue init MalMapGet(request, MalMkKeyword("generation"))
      generation:MalValue init MalMapGet(entry, MalMkKeyword("generation"))
      if (expected.type == $MAL_NIL_TYPE || expected.number == generation.number) ithen
        id:MalValue init MalMapGet(entry, MalMkKeyword("id"))
        updated:MalValue init MalMapAssoc(state, MalMkKeyword("slots"), MalMapDissoc(slots, key))
        updated init MalMapAssoc(updated, MalMkKeyword("names"), MalMapDissoc(names, id))
        stored:MalValue init MalAtomSetValue(stateAtom, updated)
        panaeolusGenerations[key.number] = 0
        PanaeolusCloseEntryRoute(entry, MalMkValue($MAL_NIL_TYPE))
        removed init entry
        removedChildren:MalValue init PanaeolusStopChildren(entry)
        if (removedChildren.length > 0) ithen
          removed init MalMapAssoc(removed, MalMkKeyword("removed-children"), removedChildren)
        endif
      endif
    endif
  endif
  xout removed
endop

opcode PanaeolusLoopStop(token:i):i
  removed:MalValue init PanaeolusLoopStopValue(PanaeolusLoopRequest(token))
  PanaeolusLoopReply(token, removed)
  result:i = 0
  xout result
endop

opcode PanaeolusNotifyRemoval(entry:MalValue):void
  if (entry.type == $MAL_HASH_MAP_TYPE) ithen
    children:MalValue init MalMapGet(entry, MalMkKeyword("removed-children"))
    if (children.length > 0) ithen
      for index in [0 ... children.length - 1] do
        PanaeolusNotifyRemoval(MalAt(children, index))
      od
    endif
    hookAtom:MalValue init panaeolusRemoveHookAtom
    hook:MalValue init MalAtomValue(hookAtom)
    if (hook.type != $MAL_NIL_TYPE) ithen
      result:MalValue init MalApply(hook, MalMkList1(entry))
      if (result.type == $MAL_ERROR_TYPE) ithen
        prints "Panaeolus: loop removal hook failed: %s\n", result.string
      endif
    endif
  endif
endop

opcode PanaeolusStopChildren(entry:MalValue):MalValue
  callback:MalValue init MalMapGet(entry, MalMkKeyword("callback"))
  children:MalValue init MalMapGet(MalMeta(callback), MalMkKeyword("panaeolus/children"))
  removedChildren:MalValue init MalMkValue($MAL_VECTOR_TYPE)
  if (children.length > 0) ithen
    for index in [0 ... children.length - 1] do
      request:MalValue init MalMapAssoc(MalMkValue($MAL_HASH_MAP_TYPE), \
        MalMkKeyword("id"), MalAt(children, index))
      removed:MalValue init PanaeolusLoopStopValue(request)
      if (removed.type == $MAL_HASH_MAP_TYPE) ithen
        removedChildren init MalAppendValue(removedChildren, removed)
      endif
    od
    playbacks:MalValue init panaeolusPlaybacksAtom
    ready:MalValue init MalAtomValue(playbacks)
    for index in [0 ... children.length - 1] do
      ready init MalMapDissoc(ready, MalAt(children, index))
    od
    stored:MalValue init MalAtomSetValue(playbacks, ready)
  endif
  ;; Keep large action lists out of loop entries, which are copied on commits.
  ;; Their registry entry belongs to this one playback and ends with its owner.
  plan:MalValue init MalMapGet(MalMeta(callback), MalMkKeyword("panaeolus/composition"))
  token:MalValue init MalMapGet(plan, MalMkKeyword("token"))
  if (token.type != $MAL_NIL_TYPE) ithen
    plans:MalValue init panaeolusPerformancesAtom
    stored:MalValue init MalAtomSetValue(plans, MalMapDissoc(MalAtomValue(plans), token))
  endif
  ;; User hooks run only after all owned routes and slots have been released.
  ;; Return their entries so interactive evaluation can notify cooperatively.
  xout removedChildren
endop

opcode PanaeolusEval(source:S):i
  result:MalValue, updated:MalEnv = MalEvalFormsEnv(source, panaeolusEnv)
  panaeolusEnv init updated
  status:i = 0
  if (result.type == $MAL_ERROR_TYPE) ithen
    prints "Panaeolus error: %s\n", result.string
    status = -1
  else
    prints "%s\n", pr_str(result)
  endif
  ;; Nested csound/eval calls share Csound's evalstr return slot. Hosts read
  ;; this channel after evaluation instead of relying on that shared slot.
  chnset status, "__panaeolus_status"
  xout status
endop

opcode PanaeolusEvalStart(source:S):i
  status:i = -1
  if (i(panaeolusEvalJob) >= 0) ithen
    prints "Panaeolus: an evaluation is already running.\n"
  else
    ;; Read the region before starting: read-string may reuse token storage.
    ast:MalValue init MalMkList1(MalMkSymbol("do"))
    tokens:MalTokens init tokenize(source)
    if (tokens.length > 0) ithen
      reader:MalReader init MalMkReader(tokens)
      while (reader.done == 0 && ast.type != $MAL_ERROR_TYPE) do
        form:MalReadResult init read_form(reader)
        value:MalValue init MalReadResultValue(form)
        reader init MalReadResultReader(form)
        if (value.type == $MAL_ERROR_TYPE) ithen
          ast init value
        else
          ast init MalAppendValue(ast, value)
        endif
      od
    endif
    if (ast.type == $MAL_ERROR_TYPE) ithen
      prints "Panaeolus error: %s\n", ast.string
      chnset -1, "__panaeolus_status"
    else
      job:i = MalEvalJobStart(ast, panaeolusEnv)
      if (job < 0) ithen
        prints "Panaeolus: no evaluation slot is available.\n"
        chnset -1, "__panaeolus_status"
      else
        panaeolusEvalJob init job
        chnset 1, "__panaeolus_status"
        status = 1
      endif
    endif
  endif
  xout status
endop

instr PanaeolusEvalRunner
  ;; Resume a few evaluator transitions between audio blocks. JS only submits
  ;; source and reads completion; it does not advance the job or the beat clock.
  igoto EVAL_DONE
  if (panaeolusEvalJob >= 0) then
    reinit EVAL_STEP
  endif
  kgoto EVAL_DONE
EVAL_STEP:
  job:i = i(panaeolusEvalJob)
  complete:i = MalEvalJobStep(job, 4)
  if (complete == 1) ithen
    result:MalValue init MalEvalJobResult(job)
    panaeolusEnv init MalEnvResolve(panaeolusEnv)
    if (result.type == $MAL_ERROR_TYPE) ithen
      prints "Panaeolus error: %s\n", result.string
      chnset -1, "__panaeolus_status"
    else
      prints "%s\n", pr_str(result)
      chnset 0, "__panaeolus_status"
    endif
    MalEvalJobCancel(job)
    panaeolusEvalJob init -1
  endif
  rireturn
EVAL_DONE:
endin

instr PanaeolusClock
  increment:k = (panaeolusBpm / 60) * (ksmps / sr) - panaeolusClockError
  total:k = panaeolusBeat + increment
  panaeolusClockError = (total - panaeolusBeat) - increment
  panaeolusBeat = total
endin

instr PanaeolusWait
  ;; A k-rate index reads the shared i-array again on every control period.
  slot:k init p4
  generation:i = p5
  target:i = p6
  if (panaeolusGenerations[slot] != generation) then
    turnoff
  elseif (panaeolusBeat >= target) then
    event "i", "PanaeolusDispatch", 0, 0, slot, generation
    turnoff
  endif
endin

instr PanaeolusDispatch
  slot:i = p4
  generation:i = p5
  if (panaeolusGenerations[slot] == generation) ithen
    entry:MalValue init panaeolusEntries[slot]
    limit:MalValue init MalMapGet(entry, MalMkKeyword("until"))
    if (limit.type == $MAL_NUMBER_TYPE && i(panaeolusBeat) >= limit.number) ithen
      ;; A segment owns [start, end). End it even when its next note falls
      ;; beyond the boundary, before another section starts at this beat.
      request:MalValue init MalMapAssoc(MalMkValue($MAL_HASH_MAP_TYPE), MalMkKeyword("slot"), MalMkNumber(slot))
      request init MalMapAssoc(request, MalMkKeyword("generation"), MalMkNumber(generation))
      stopped:MalValue init PanaeolusLoopStopValue(request)
      PanaeolusNotifyRemoval(stopped)
      igoto DISPATCH_DONE
    endif
    id:MalValue init MalMapGet(entry, MalMkKeyword("id"))
    callback:MalValue init MalMapGet(entry, MalMkKeyword("callback"))
    durations:MalValue init MalMapGet(entry, MalMkKeyword("durations"))
    index:i = panaeolusIndices[slot]
    cycle:i = panaeolusCycles[slot]
    beat:i = panaeolusTargets[slot]
    duration:MalValue init MalAt(durations, index)
    fields:MalValue[] init 14
    fields[0] init MalMkKeyword("id")
    fields[1] init id
    fields[2] init MalMkKeyword("index")
    fields[3] init MalMkNumber(index)
    fields[4] init MalMkKeyword("cycle")
    fields[5] init MalMkNumber(cycle)
    fields[6] init MalMkKeyword("beat")
    fields[7] init MalMkNumber(beat)
    fields[8] init MalMkKeyword("duration")
    fields[9] init duration
    fields[10] init MalMkKeyword("step")
    fields[11] init MalMkNumber(cycle * durations.length + index)
    fields[12] init MalMkKeyword("route")
    routing:MalValue init MalMapGet(entry, MalMkKeyword("routing"))
    fields[13] init MalMapGet(routing, MalMkKeyword("route"))
    ;; Keep a fresh map for retained callback data.
    fieldCount:i = routing.type == $MAL_HASH_MAP_TYPE ? 14 : 12
    eventMap:MalValue init $MAL_HASH_MAP_TYPE, 0, "", fields, malEmptyEnvs, malEmptyValues, fieldCount, 0, 0
    prepared:i = PanaeolusCompositionTick(entry, eventMap)
    if (prepared == 0) ithen
      prepared = PanaeolusPrepareTick(entry, eventMap)
    endif
    if (prepared < 0) ithen
      result:MalValue init MalMkError("scheduled action failed; see Csound output")
    elseif (prepared == 2) ithen
      ;; Generated instrument callbacks submit their sampled note natively.
      result init MalMkValue($MAL_NIL_TYPE)
    else
      result init MalApply(callback, MalMkList1(eventMap))
    endif
    if (result.type == $MAL_ERROR_TYPE) ithen
      request:MalValue init MalMapAssoc(MalMkValue($MAL_HASH_MAP_TYPE), MalMkKeyword("slot"), MalMkNumber(slot))
      request init MalMapAssoc(request, MalMkKeyword("generation"), MalMkNumber(generation))
      stopped:MalValue init PanaeolusLoopStopValue(request)
      PanaeolusNotifyRemoval(stopped)
      prints "Panaeolus: stopped %s: %s\n", pr_str(id), result.string
    elseif (panaeolusGenerations[slot] == generation) ithen
      nextIndex:i = index + 1
      once:MalValue init MalMapGet(routing, MalMkKeyword("once"))
      park:MalValue init MalMapGet(entry, MalMkKeyword("park-after-last"))
      if (once.type == $MAL_TRUE_TYPE || \
          (park.type == $MAL_TRUE_TYPE && nextIndex == durations.length)) ithen
        ;; A drone or conductor remains owned by its ID while idle. No fake
        ;; billion-beat event is needed; a finite plan still wakes to stop.
        if (limit.type == $MAL_NUMBER_TYPE) ithen
          waiter:i = nstrnum("PanaeolusWait") + (slot + 1) / 1024
          schedule(waiter, 0, -1, slot, generation, limit.number)
        endif
        igoto DISPATCH_DONE
      endif
      if (nextIndex == durations.length) ithen
        nextIndex = 0
        panaeolusCycles[slot] = cycle + 1
      endif
      panaeolusIndices[slot] = nextIndex
      ;; Recompute from the origin instead of accumulating every prior step.
      target:i = PanaeolusTarget(slot, nextIndex, panaeolusCycles[slot])
      panaeolusTargets[slot] = target
      waiter:i = nstrnum("PanaeolusWait") + (slot + 1) / 1024
      wake:i = limit.type == $MAL_NUMBER_TYPE ? min(target, limit.number) : target
      schedule(waiter, 0, -1, slot, generation, wake)
    endif
  endif
DISPATCH_DONE:
endin

schedule("PanaeolusClock", 0, -1)
schedule("PanaeolusEvalRunner", 0, -1)
