;; Plans already contain compiled voices and effects. Move their children at
;; a beat boundary without compiling a fresh evalstr bridge on the audio thread.

opcode PanaeolusCompositionCurrent(entry:MalValue):i
  slot:MalValue init MalMapGet(entry, MalMkKeyword("slot"))
  generation:MalValue init MalMapGet(entry, MalMkKeyword("generation"))
  current:i = 0
  if (slot.type == $MAL_NUMBER_TYPE && slot.number >= 0 && slot.number < 128 && \
      slot.number == int(slot.number) && generation.type == $MAL_NUMBER_TYPE) ithen
    current = panaeolusGenerations[slot.number] == generation.number
  endif
  xout current
endop

opcode PanaeolusCompositionTick(entry:MalValue, eventMap:MalValue):i
  result:i = 0
  callback:MalValue init MalMapGet(entry, MalMkKeyword("callback"))
  plan:MalValue init MalMapGet(MalMeta(callback), MalMkKeyword("panaeolus/composition"))
  generated:MalValue init MalMapGet(plan, MalMkKeyword("callback"))
  ;; Copying metadata onto another function must not hide that function.
  if (plan.type != $MAL_HASH_MAP_TYPE || callback.type != $MAL_FUNCTION_TYPE || \
      generated.type != $MAL_FUNCTION_TYPE || callback.number != generated.number) ithen
    igoto COMPOSITION_DONE
  endif
  token:MalValue init MalMapGet(plan, MalMkKeyword("token"))
  performances:MalValue init panaeolusPerformancesAtom
  actions:MalValue init MalMapGet(MalAtomValue(performances), token)
  index:MalValue init MalMapGet(eventMap, MalMkKeyword("index"))
  beat:MalValue init MalMapGet(eventMap, MalMkKeyword("beat"))
  if (actions.type != $MAL_VECTOR_TYPE || index.type != $MAL_NUMBER_TYPE || \
      index.number < 0 || index.number >= actions.length || index.number != int(index.number) || \
      beat.type != $MAL_NUMBER_TYPE || beat.number - beat.number != 0) ithen
    result = PanaeolusRoutingFailure("composition: invalid prepared boundary")
    igoto COMPOSITION_DONE
  endif
  action:MalValue init MalAt(actions, index.number)
  finish:MalValue init MalMapGet(action, MalMkKeyword("finish"))
  request:MalValue init MalMkValue($MAL_HASH_MAP_TYPE)
  if (finish.type == $MAL_TRUE_TYPE) ithen
    request init MalMapAssoc(request, MalMkKeyword("id"), MalMapGet(entry, MalMkKeyword("id")))
    removed:MalValue init PanaeolusLoopStopValue(request)
    PanaeolusNotifyRemoval(removed)
  else
    stops:MalValue init MalMapGet(action, MalMkKeyword("stops"))
    removedEntries:MalValue init MalMkValue($MAL_VECTOR_TYPE)
    if (stops.length > 0) ithen
      for stopIndex in [0 ... stops.length - 1] do
        request init MalMapAssoc(request, MalMkKeyword("id"), MalAt(stops, stopIndex))
        removed init PanaeolusLoopStopValue(request)
        if (removed.type == $MAL_HASH_MAP_TYPE) ithen
          removedEntries init MalAppendValue(removedEntries, removed)
        endif
      od
    endif
    ;; Free all ending voices before any user hook can block or start a loop.
    current:i = 1
    if (removedEntries.length > 0) ithen
      for removedIndex in [0 ... removedEntries.length - 1] do
        removed init MalAt(removedEntries, removedIndex)
        PanaeolusNotifyRemoval(removed)
        ;; A user hook may stop or replace this conductor. Its old boundary
        ;; must not launch children after that change has taken ownership.
        if (PanaeolusCompositionCurrent(entry) == 0) ithen
          current = 0
        endif
      od
    endif
    if (current == 0) ithen
      result = 2
      igoto COMPOSITION_DONE
    endif
    leaves:MalValue init MalMapGet(action, MalMkKeyword("leaves"))
    if (leaves.length > 0) ithen
      playbacks:MalValue init panaeolusPlaybacksAtom
      ready:MalValue init MalAtomValue(playbacks)
      for leafIndex in [0 ... leaves.length - 1] do
        leaf:MalValue init MalAt(leaves, leafIndex)
        phase:MalValue init MalMapGet(leaf, MalMkKeyword("phase"))
        span:MalValue init MalMapGet(leaf, MalMkKeyword("span"))
        at:i = beat.number + phase.number
        limit:MalValue init MalMkValue($MAL_NIL_TYPE)
        if (span.type == $MAL_NUMBER_TYPE) ithen
          limit init MalMkNumber(beat.number + span.number)
        endif
        if (limit.type == $MAL_NIL_TYPE || at < limit.number) ithen
          playback:MalValue init MalMapGet(ready, MalMapGet(leaf, MalMkKeyword("id")))
          childCallback:MalValue init MalMapGet(playback, MalMkKeyword("callback"))
          metadata:MalValue init MalMeta(childCallback)
          child:MalValue init MalMkValue($MAL_HASH_MAP_TYPE)
          child init MalMapAssoc(child, MalMkKeyword("id"), MalMapGet(leaf, MalMkKeyword("id")))
          child init MalMapAssoc(child, MalMkKeyword("durations"), MalMapGet(playback, MalMkKeyword("durations")))
          child init MalMapAssoc(child, MalMkKeyword("callback"), childCallback)
          child init MalMapAssoc(child, MalMkKeyword("routing"), MalMapGet(metadata, MalMkKeyword("panaeolus/tick")))
          child init MalMapAssoc(child, MalMkKeyword("park-after-last"), MalMapGet(metadata, MalMkKeyword("panaeolus/park-after-last")))
          child init MalMapAssoc(child, MalMkKeyword("at"), MalMkNumber(at))
          child init MalMapAssoc(child, MalMkKeyword("until"), limit)
          committed:MalValue init PanaeolusLoopStartValue(child)
          if (committed.type == $MAL_ERROR_TYPE) ithen
            result = PanaeolusRoutingFailure(committed.string)
            igoto COMPOSITION_DONE
          endif
          ;; A fresh child normally has no previous entry. Keep the same hook
          ;; semantics as a direct loop call if its identity was already used.
          oldEntry:MalValue init MalAt(committed, 0)
          newEntry:MalValue init MalAt(committed, 1)
          oldMetadata:MalValue init MalMeta(MalMapGet(oldEntry, MalMkKeyword("callback")))
          newMetadata:MalValue init MalMeta(MalMapGet(newEntry, MalMkKeyword("callback")))
          owned:MalValue init MalMapGet(oldMetadata, MalMkKeyword("panaeolus/children"))
          if (oldEntry.type == $MAL_HASH_MAP_TYPE && (owned.type != $MAL_NIL_TYPE || \
              MalEquals(MalMapGet(oldMetadata, MalMkKeyword("panaeolus/route")), \
                        MalMapGet(newMetadata, MalMkKeyword("panaeolus/route"))) == 0)) ithen
            PanaeolusNotifyRemoval(oldEntry)
          endif
          if (PanaeolusCompositionCurrent(entry) == 0) ithen
            result = 2
            igoto COMPOSITION_DONE
          endif
        endif
      od
    endif
  endif
  result = 2
COMPOSITION_DONE:
  xout result
endop
