import assert from "node:assert/strict";
import { resolve } from "node:path";

// Compare generated native note dispatch with the ordinary Lisp callback path.
// Audio must match exactly; timings are reported, not tied to one machine.
Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../node_modules/@csound/browser/dist/csound.js");
const api = await libcsound();
const root = resolve(import.meta.dir, "..");
const mal = await Bun.file(resolve(root, "dist/make-a-lisp.orc")).text();
const runtime = await Bun.file(resolve(root, "dist/panaeolus.orc")).text();
const sr = 48000;
const blockSize = 64;
const seconds = 3;

function evaluate(engine: number, code: string) {
  api.csoundSetStringChannel(engine, "__panaeolus_test", `${code}\nnil`);
  const started = performance.now();
  api.csoundEvalCode(engine, 'return PanaeolusEval(chnget:S("__panaeolus_test"))\n');
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), 0, code);
  return performance.now() - started;
}

function render(interpreted: boolean) {
  const engine = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
    assert.equal(api.csoundCompileOrc(engine,
      `sr = ${sr}\nksmps = ${blockSize}\nnchnls = 2\n0dbfs = 1\n${mal}\n${runtime}`), 0);
    assert.equal(api.csoundStart(engine), 0);
    evaluate(engine, `(ns timing (:require [panaeolus.instrument :as p]
      [panaeolus.fx :refer [flanger]]))
      (def event csound/event)
      (p/definst pulse [hz 440 amp 0.04]
        (* (csound/poscil amp hz)
           (csound/linseg 0 (* p3 0.05) 1 (* p3 0.95) 0)))`);
    if (interpreted) {
      // A user replacement of csound/event must still receive every note.
      evaluate(engine, `(def submitted (atom [])) (ns csound)
        (def event (fn [& args]
          (swap! timing/submitted conj (vec (drop 2 args)))
          (apply timing/event args))) (ns timing)`);
    }
    const invocationMs = evaluate(engine, `
      (pulse :id :a :every [0.25 0.25 0.5] :hz [330 440 550]
        :amp [0.03 0.05] :dur [0.03 0.06]
        :fx (flanger :rate [0.2 0.5 0.8] :depth 0.003 :fback 0.35))
      (pulse :id :b :every [0.25] :hz [660 770]
        :amp [0.04 0.02 0.03] :dur [0.04 0.08 0.05])`);
    const blockCount = Math.round(seconds * sr / blockSize);
    const audio = new Float64Array(blockCount * blockSize * 2);
    const elapsed: number[] = [];
    for (let block = 0; block < blockCount; block++) {
      const started = performance.now();
      assert.equal(api.csoundPerformKsmps(engine), 0);
      elapsed.push(performance.now() - started);
      const samples = new Float64Array(api.getMemory().buffer,
        api.csoundGetSpout(engine), blockSize * 2);
      audio.set(samples, block * blockSize * 2);
    }
    assert.ok(audio.some(sample => Math.abs(sample) > 0.01), "No audio reached the output");
    assert.ok(audio.every(Number.isFinite), "Non-finite audio");
    if (interpreted) {
      evaluate(engine, `(if (= 42 (count @submitted)) true
        (throw (str "Expected 42 events, got " (count @submitted))))
        (if (= [0 0.03 330 0.03 1] (first @submitted)) true
          (throw (str "Wrong p-fields: " (first @submitted))))`);
    }
    evaluate(engine, "(p/stop-all!)");
    for (let block = 0; block < Math.ceil(4.3 * sr / blockSize); block++) {
      assert.equal(api.csoundPerformKsmps(engine), 0);
    }
    assert.equal(api.csoundEvalCode(engine, `active:i = 0
      for route in [1 ... 255] do
        active += panaeolusRouteActive[route]
      od
      return active`), 0, "Stopped routes did not drain");
    const sorted = [...elapsed].sort((a, b) => a - b);
    const dispatch = sorted.filter(ms => ms > 0.5);
    return { audio, timings: { path: interpreted ? "Lisp callback with event recorder" : "native generated note",
      invocationMs, audioSeconds: seconds, totalCpuMs: elapsed.reduce((a, b) => a + b, 0),
      maxBlockMs: sorted.at(-1), dispatchMedianMs: dispatch[Math.floor(dispatch.length / 2)] ?? 0,
      blocksOverWorkerBuffer: elapsed.filter(ms => ms > 2048 / sr * 1000).length } };
  } finally {
    api.csoundStop(engine);
    api.csoundDestroy(engine);
  }
}

const native = render(false);
const interpreted = render(true);
assert.deepEqual(native.audio, interpreted.audio,
  "Native dispatch changed note timing, duration, p-fields, or FX controls");
console.log(JSON.stringify([native.timings, interpreted.timings], null, 2));
console.log("Native and Lisp dispatch produced identical audio; event overrides and route cleanup passed.");
