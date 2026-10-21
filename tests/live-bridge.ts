import assert from "node:assert/strict";
import { resolve } from "node:path";

// A real loop callback enters routing while a live edit owns a pending request.
// Force that overlap so the test does not depend on a lucky event time.
Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const api = await libcsound();
const engine = api.csoundCreate();
const root = resolve(import.meta.dir, "..");

function native(code: string) {
  return api.csoundEvalCode(engine, code);
}

function evaluate(source: string) {
  api.csoundSetStringChannel(engine, "__test_source", `${source}\nnil`);
  native('return PanaeolusEval(chnget:S("__test_source"))\n');
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), 0, source);
}

function atom(name: string, expression: string) {
  return native(`value:MalValue init MalAtomValue(MalResolveSymbol(panaeolusEnv, "${name}"))
    ${expression}`);
}

function block() {
  assert.equal(api.csoundPerformKsmps(engine), 0, "Engine stopped during bridge overlap");
  const samples = new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), 128);
  assert.ok(samples.every(Number.isFinite), "Bridge overlap produced invalid audio");
  return Math.max(...samples);
}

try {
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
  const mal = await Bun.file(resolve(root, "dist/make-a-lisp.orc")).text();
  const runtime = await Bun.file(resolve(root, "dist/panaeolus.orc")).text();
  assert.equal(api.csoundCompileOrc(engine,
    `sr = 48000\nksmps = 64\nnchnls = 2\n0dbfs = 1\n${mal}\n${runtime}`), 0);
  assert.equal(api.csoundStart(engine), 0);
  evaluate(`(ns bridge.test (:require [panaeolus.instrument :as p]
    [panaeolus.fx :as fx] [panaeolus.routing :as routing] [panaeolus.event-loop :as loop]))
    (p/definst flat [amp 0.1] (csound/linseg amp 1 amp))
    (fx/deffx gain [amount 1] [left right] [(* left amount) (* right amount)])
    (flat :dur 0.01 :fx (gain 0.1))
    (def played (atom []))
    (def event! csound/event)
    (def csound/event (fn [& fields] (swap! played conj (vec fields)) (apply event! fields)))
    (def callback-count (atom 0))
    (loop/start! :interrupt [1001]
      (fn [event]
        (flat :amp 0.03 :dur 0.07 :fx (gain 0.25))
        (swap! callback-count inc)
        (loop/stop! :interrupt))
      :offset 1000)
    (def callback-slot (get (get @loop/state :names) :interrupt))
    (def callback-generation
      (get (get (get @loop/state :slots) callback-slot) :generation))`);
  for (let index = 0; index < 100; index++) block();

  api.csoundSetStringChannel(engine, "__test_source", "(flat :amp 0.06 :dur 0.11 :fx (gain 0.5))");
  native('return PanaeolusEvalStart(chnget:S("__test_source"))\n');
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), 1);
  let foundPending = false;
  for (let index = 0; index < 10000; index++) {
    block();
    if (atom("panaeolus.routing/pending", "return value.length") > 0) {
      foundPending = true;
      break;
    }
    assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), 1,
      "Edit finished without exposing a pending routing request");
  }
  assert.ok(foundPending, "Edit did not reach its routing bridge");
  const token = atom("panaeolus.routing/pending",
    "key:MalValue init MalAt(MalMapKeys(value), 0)\nreturn key.number");
  const job = native("return i(panaeolusEvalJob)");
  assert.ok(job >= 0);
  // Hold the edit at its yield point for one block. Dispatch the registered
  // callback through the same Csound instrument that handles normal loop ticks.
  native(`panaeolusEvalJob init -1
    slot:MalValue init MalResolveSymbol(panaeolusEnv, "bridge.test/callback-slot")
    generation:MalValue init MalResolveSymbol(panaeolusEnv, "bridge.test/callback-generation")
    schedule("PanaeolusDispatch", 0, 0, slot.number, generation.number)
    result:i = 0
    return result`);
  let callbackPeak = block();
  assert.equal(atom("bridge.test/callback-count", "return value.number"), 1,
    "The registered callback did not run during the edit");
  assert.equal(atom("panaeolus.routing/pending", "return value.length"), 2,
    "The callback changed the number of pending edit requests");
  assert.equal(atom("panaeolus.routing/pending",
    `request:MalValue init MalMapGet(value, MalMkNumber(${token}))
     controls:MalValue init MalMapGet(request, MalMkKeyword("controls"))
     parameter:MalValue init MalAt(MalAt(controls, 0), 0)
     return parameter.number`), 0.5, "The callback overwrote the edit's request");
  assert.equal(atom("panaeolus.event-loop/requests", "return value.length"), 0);
  assert.equal(atom("panaeolus.event-loop/responses", "return value.length"), 0);
  // Let this note finish before resuming, so each request's gain can be heard
  // and checked separately even when the interpreter gets faster.
  for (let index = 0; index < 100; index++) callbackPeak = Math.max(callbackPeak, block());
  assert.ok(Math.abs(callbackPeak - 0.0075) < 1e-12, `Wrong callback gain: ${callbackPeak}`);

  native(`panaeolusEvalJob init ${job}\nreturn i(panaeolusEvalJob)`);
  let editPeak = 0;
  for (let index = 0; index < 10000; index++) {
    editPeak = Math.max(editPeak, block());
    if (api.csoundGetControlChannel(engine, "__panaeolus_status") !== 1) break;
  }
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), 0,
    "The edit failed after its callback entered the same routing bridge");
  for (let index = 0; index < 150; index++) editPeak = Math.max(editPeak, block());
  assert.ok(Math.abs(editPeak - 0.03) < 1e-12, `Wrong edit gain: ${editPeak}`);
  evaluate(`(if (= [0.03 0.06] (mapv (fn [event] (nth event 4)) @played)) true
      (throw "Overlapping bridge calls changed note parameters"))
    (if (= [0.07 0.11] (mapv (fn [event] (nth event 3)) @played)) true
      (throw "Overlapping bridge calls changed note durations"))
    (if (empty? @routing/pending) true (throw "Routing request leaked"))
    (if (and (empty? @loop/requests) (empty? @loop/responses)) true
      (throw "Loop request or response leaked"))
    (if (empty? (get @loop/state :names)) true (throw "Callback did not stop itself"))`);
  assert.equal(block(), 0, "Finished one-shots left audible routes");
  console.log("PASS: a user callback can trigger a one-shot and stop itself while a live edit owns a routing request.");
} finally {
  api.csoundStop(engine);
  api.csoundDestroy(engine);
}
