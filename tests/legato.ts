import assert from "node:assert/strict";
import { resolve } from "node:path";

Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const api = await libcsound();
const engine = api.csoundCreate();
const root = resolve(import.meta.dir, "..");
const blockSize = 64;
const sampleRate = 48000;

function evaluate(source: string, expected = 0) {
  api.csoundSetControlChannel(engine, "__panaeolus_status", -999);
  api.csoundSetStringChannel(engine, "__panaeolus_test", `${source}\nnil`);
  api.csoundEvalCode(engine, 'return PanaeolusEval(chnget:S("__panaeolus_test"))\n');
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), expected, source);
}

function native(expression: string) {
  return api.csoundEvalCode(engine, `return ${expression}`);
}

function number(form: string) {
  evaluate(`(def __test_number ${form})`);
  return api.csoundEvalCode(engine,
    'value:MalValue init MalResolveSymbol(panaeolusEnv, "test.legato/__test_number")\nreturn value.number');
}

function advance(seconds: number) {
  const result = new Float64Array(Math.round(seconds * sampleRate / blockSize) * blockSize * 2);
  for (let block = 0; block < result.length / (blockSize * 2); block++) {
    assert.equal(api.csoundPerformKsmps(engine), 0);
    result.set(new Float64Array(api.getMemory().buffer,
      api.csoundGetSpout(engine), blockSize * 2), block * blockSize * 2);
  }
  assert.ok(result.every(Number.isFinite), "Held voice produced non-finite audio");
  return result;
}

function peak(audio: Float64Array) {
  return audio.reduce((peak, sample) => Math.max(peak, Math.abs(sample)), 0);
}

function route(id = ":held") {
  return number(`(get (routing/current-route ${id}) :number)`);
}

try {
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
  const mal = await Bun.file(resolve(root, "dist/make-a-lisp.orc")).text();
  const panaeolus = await Bun.file(resolve(root, "dist/panaeolus.orc")).text();
  assert.equal(api.csoundCompileOrc(engine,
    `sr = ${sampleRate}\nksmps = ${blockSize}\nnchnls = 2\n0dbfs = 1\n${mal}\n${panaeolus}`), 0);
  assert.equal(api.csoundStart(engine), 0);
  evaluate(`(ns test.legato (:require [panaeolus.instrument :as p]
    [panaeolus.routing :as routing] [panaeolus.fx :as fx] [panaeolus.event-loop :as loop]))
    (p/tempo! 120)
    (p/definst tone [hz 220 amp 0.1] (csound/oscili amp hz))
    (p/definst fixed [] (csound/oscili 0.05 220))
    (p/definst ramp [amp 0.1] (* amp (csound/linseg 0 1 1)))
    (fx/deffx gain [amount 1] [left right] [(* left amount) (* right amount)])`);

  evaluate("(ramp :id :held :legato true)");
  assert.ok(peak(advance(0.15)) > 0.01, "Held voice did not start");
  const firstRoute = route();
  const firstVoice = native(`panaeolusHeldInstruments[${firstRoute}]`);
  assert.ok(firstRoute > 0 && firstVoice >= 1000);
  assert.equal(native(`active:i(${firstVoice})`), 1);
  evaluate("(ramp :id :held :legato true :amp 0.2)");
  const edited = advance(0.02);
  assert.equal(route(), firstRoute, "Control edit replaced the route");
  assert.equal(native(`active:i(${firstVoice})`), 1, "Control edit retriggered the voice");
  assert.ok(peak(edited) > 0.03, "Control edit restarted the running envelope");
  assert.equal(api.csoundGetControlChannel(engine, `panaeolus/r${firstRoute}/v/p0`), 0.2);

  // Switching from a parked voice to a pattern retains the same DSP instance.
  evaluate("(ramp :id :held :legato true :every [0.1] :amp [0.1 0.2 0.3])");
  advance(0.02);
  assert.equal(route(), firstRoute);
  assert.equal(native(`active:i(${firstVoice})`), 1);
  const seen = new Set<number>();
  for (let index = 0; index < 20; index++) {
    advance(0.01);
    seen.add(api.csoundGetControlChannel(engine, `panaeolus/r${firstRoute}/v/p0`));
  }
  assert.deepEqual([...seen].sort(), [0.1, 0.2, 0.3], "Held controls did not cycle");

  // FX edits keep processors alive; changing the chain retires the old voice.
  evaluate("(ramp :id :held :legato true :fx (gain 0.5))");
  advance(0.02);
  const wetRoute = route();
  assert.notEqual(wetRoute, firstRoute);
  assert.equal(native(`panaeolusHeldInstruments[${firstRoute}]`), 0);
  evaluate("(ramp :id :held :legato true :fx (gain 0.25))");
  advance(0.02);
  assert.equal(route(), wetRoute);
  assert.equal(api.csoundGetControlChannel(engine, `panaeolus/r${wetRoute}/f0/p0`), 0.25);
  evaluate("(ramp :id :held :legato true)");
  advance(0.02);
  const dryRoute = route();
  assert.notEqual(dryRoute, wetRoute);
  evaluate("(p/definst ramp [amp 0.1] (* amp (csound/linseg 0 2 1))) (ramp :id :held :legato true)");
  advance(0.02);
  assert.notEqual(route(), dryRoute, "Changed instrument reused the old DSP definition");

  evaluate("(p/stop-all!)");
  advance(0.05);
  assert.equal(peak(advance(0.02)), 0, "Stopped held voice still plays");
  assert.equal(native("sumarray(panaeolusHeldInstruments)"), 0);

  // A stop between queuing the voice and its init pass must cancel that start.
  for (let blocks = 0; blocks <= 4; blocks++) {
    evaluate("(tone :id :pending :legato true)");
    const pendingVoice = number("(get (routing/current-route :pending) :voice)");
    advance(blocks * blockSize / sampleRate);
    evaluate("(p/stop! :pending)");
    advance(0.02);
    assert.equal(native(`active:i(${pendingVoice})`), 0, `Stop left a pending voice after ${blocks} blocks`);
  }
  evaluate("(fixed :id :fixed :legato true)");
  assert.ok(peak(advance(0.02)) > 0, "A held instrument without parameters did not start");
  evaluate("(p/stop! :fixed)");
  advance(0.02);

  evaluate("(def quiet (p/with-defaults tone :id :quiet :legato true :amp 0.01)) (quiet)");
  assert.ok(peak(advance(0.02)) > 0);
  const quietRoute = route(":quiet");
  assert.ok(native(`panaeolusHeldInstruments[${quietRoute}]`) > 0);
  evaluate("(quiet :dur 0.1)", -1);
  evaluate("(quiet :legato :yes)", -1);
  assert.equal(route(":quiet"), quietRoute, "Invalid edit replaced the held voice");
  evaluate("(p/stop! :quiet)");
  advance(0.02);
  evaluate("(p/definst init-only [amp 0.1] (csound/linseg amp 1 amp))");
  evaluate("(init-only :legato true)", -1);
  evaluate("(init-only)");
  assert.ok(peak(advance(0.02)) > 0, "An i-rate body lost ordinary note support");
  advance(0.15);

  // Neither the old callable nor a plan may resolve its DSP through the new
  // variable binding, even when the old held graph has never been compiled.
  evaluate(`(p/definst remembered [amp 0.03] (* amp (csound/linseg 1 1 1)))
    (def old-remembered remembered)
    (def old-plan (p/for-beats 0.1 (p/pattern remembered :every [0.025] :legato true)))
    (p/definst remembered [amp 0.03] (* (* 2 amp) (csound/linseg 1 1 1)))
    (old-remembered :id :captured :legato true)`);
  assert.ok(Math.abs(peak(advance(0.02)) - 0.03) < 1e-12,
    "A captured instrument compiled the new definition's held body");
  const oldHeldVoice = number("(get (routing/current-route :captured) :voice)");
  evaluate("(p/stop! :captured)");
  advance(0.02);
  evaluate("(remembered :id :new-definition :legato true)");
  assert.ok(Math.abs(peak(advance(0.02)) - 0.06) < 1e-12,
    "Redefined instrument did not use its new held body");
  assert.notEqual(number("(get (routing/current-route :new-definition) :voice)"), oldHeldVoice);
  evaluate("(p/stop! :new-definition)");
  advance(0.02);
  evaluate("(old-plan :id :captured-plan)");
  assert.ok(Math.abs(peak(advance(0.1)) - 0.03) < 1e-12,
    "A captured pattern lost the instrument's frozen held body");
  assert.equal(native(`active:i(${oldHeldVoice})`), 0,
    "The captured plan left its held voice running at its end");
  assert.equal(peak(advance(0.02)), 0);

  evaluate(`(def cleanup-plan (p/together
      (p/pattern tone :legato true :every [0.1])
      (p/pattern fixed :legato true :every [0.1])))
    (def removal-counts (atom []))
    (cleanup-plan :id :cleanup)`);
  assert.ok(peak(advance(0.03)) > 0);
  evaluate(`(reset! loop/on-remove (fn [entry]
      (swap! removal-counts conj (count (get @loop/state :names)))))
    (p/stop! :cleanup)
    (if (and (= 3 (count @removal-counts)) (every? zero? @removal-counts))
      true (throw (str "Removal hooks saw owned siblings: " @removal-counts)))
    (reset! loop/on-remove nil)`);
  assert.equal(native("sumarray(panaeolusHeldInstruments)"), 0);
  advance(0.02);

  // A paused user hook must not hold the remaining voices or the performance
  // registry open. The host can still cancel that Lisp job after native stop.
  evaluate(`(def hook-calls (atom 0))
    (defn wait-forever [] (wait-forever))
    (cleanup-plan :id :cleanup)`);
  assert.ok(peak(advance(0.03)) > 0);
  evaluate(`(reset! loop/on-remove (fn [entry]
    (when (not= :cleanup (get entry :id))
      (swap! hook-calls inc) (wait-forever))))`);
  api.csoundSetStringChannel(engine, "__panaeolus_test", "(p/stop! :cleanup)");
  api.csoundEvalCode(engine, 'return PanaeolusEvalStart(chnget:S("__panaeolus_test"))');
  let reachedHook = false;
  for (let block = 0; block < 30000; block++) {
    assert.equal(api.csoundPerformKsmps(engine), 0);
    const calls = api.csoundEvalCode(engine,
      'value:MalValue init MalAtomValue(MalResolveSymbol(panaeolusEnv, "test.legato/hook-calls"))\nreturn value.number');
    if (calls === 1) { reachedHook = true; break; }
    assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), 1,
      "Stop job returned before entering its user hook");
  }
  assert.ok(reachedHook, "Stop job never entered its child removal hook");
  assert.equal(native("sumarray(panaeolusHeldInstruments)"), 0,
    "A waiting child removal hook left its siblings playing");
  advance(0.02);
  assert.equal(peak(advance(0.02)), 0);
  api.csoundEvalCode(engine, `job:i = i(panaeolusEvalJob)
    MalEvalJobCancel(job)
    panaeolusEvalJob init -1
    chnset 0, "__panaeolus_status"
    return 0`);
  evaluate(`(reset! loop/on-remove nil)
    (if (and (empty? (get @loop/state :names))
             (empty? @panaeolus.composition/performances)) true
      (throw "Cancelled removal hook left composition ownership behind"))`);

  // Finishing a take bounds its held notes without stopping the live session.
  evaluate("(p/record!) (tone :id :recorded :legato true :hz 220)");
  const chunks = [advance(0.1)];
  const recordedRoute = route(":recorded");
  const recordedVoice = native(`panaeolusHeldInstruments[${recordedRoute}]`);
  evaluate("(tone :id :recorded :legato true :hz 330)");
  chunks.push(advance(0.1));
  evaluate("(p/finish!) (p/export!)");
  assert.equal(native(`active:i(${recordedVoice})`), 1, "Finishing a take stopped live playback");
  const document = api.csoundGetStringChannel(engine, "__panaeolus_export");
  assert.ok(document.includes("i 94 "), "Export omitted held parameter changes");
  assert.ok(document.includes("i 92 "), "Export omitted held voice stop");
  assert.ok(!document.includes("MalValue"), "Export depends on the Lisp runtime");
  const replay = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0"]) api.csoundSetOption(replay, option);
    assert.equal(api.csoundCompileCSD(replay, document), 0);
    assert.equal(api.csoundStart(replay), 0);
    const live = new Float64Array(chunks.reduce((length, chunk) => length + chunk.length, 0));
    let offset = 0;
    for (const chunk of chunks) { live.set(chunk, offset); offset += chunk.length; }
    const rendered = new Float64Array(live.length);
    for (let block = 0; block < rendered.length / (blockSize * 2); block++) {
      assert.equal(api.csoundPerformKsmps(replay), 0);
      rendered.set(new Float64Array(api.getMemory().buffer,
        api.csoundGetSpout(replay), blockSize * 2), block * blockSize * 2);
    }
    assert.deepEqual(rendered, live, "Held voice replay differs from the live take");
    let ended = false;
    for (let block = 0; block < 10; block++) {
      if (api.csoundPerformKsmps(replay) !== 0) { ended = true; break; }
    }
    assert.ok(ended, "Exported held voice did not end");
  } finally { api.csoundDestroy(replay); }
  evaluate("(p/stop-all!)");
  advance(0.02);
  assert.equal(peak(advance(0.02)), 0);
  for (let blocks = 0; blocks <= 2; blocks++) {
    evaluate("(p/record!) (tone :id :short-take :legato true)");
    assert.equal(peak(advance(blocks * blockSize / sampleRate)), 0);
    evaluate("(p/finish!) (p/export!)");
    const shortTake = api.csoundCreate();
    try {
      for (const option of ["-n", "-d", "-m0"]) api.csoundSetOption(shortTake, option);
      assert.equal(api.csoundCompileCSD(shortTake,
        api.csoundGetStringChannel(engine, "__panaeolus_export")), 0);
      assert.equal(api.csoundStart(shortTake), 0);
      let ended = false;
      for (let block = 0; block < 10; block++) {
        if (api.csoundPerformKsmps(shortTake) !== 0) { ended = true; break; }
        assert.equal(peak(new Float64Array(api.getMemory().buffer,
          api.csoundGetSpout(shortTake), blockSize * 2)), 0,
        `Finishing after ${blocks} blocks allowed a pending note into the export`);
      }
      assert.ok(ended, "A take finished before voice init did not end");
    } finally { api.csoundDestroy(shortTake); }
    evaluate("(p/stop! :short-take)");
    advance(0.02);
  }
  console.log("Held voices: control edits, envelope continuity, patterns, FX, stop, and exact CSD replay passed.");
} finally { api.csoundDestroy(engine); }
