import assert from "node:assert/strict";
import { resolve } from "node:path";

// Real audio keeps running while the Csound-owned evaluator resumes jobs.
// Optional timing gates are for a quiet machine, not a shared CI worker.
Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const api = await libcsound();
const root = resolve(import.meta.dir, "..");
const mal = await Bun.file(resolve(root, "dist/make-a-lisp.orc")).text();
const runtime = await Bun.file(resolve(root, "dist/panaeolus.orc")).text();
assert.ok(runtime.includes("opcode PanaeolusEvalStart("), "Rebuild the incremental evaluator first");
const sr = 48000;
const blockSize = 64;
const blockSeconds = blockSize / sr;
const maxJobBlocks = Math.ceil(120 / blockSeconds);
const reports: object[] = [];

function createEngine() {
  const engine = api.csoundCreate();
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
  assert.equal(api.csoundCompileOrc(engine,
    `sr = ${sr}\nksmps = ${blockSize}\nnchnls = 2\n0dbfs = 1\n${mal}\n${runtime}`), 0);
  assert.equal(api.csoundStart(engine), 0);
  return engine;
}

function evaluate(engine: number, source: string) {
  api.csoundSetStringChannel(engine, "__panaeolus_test", `${source}\nnil`);
  api.csoundEvalCode(engine, 'return PanaeolusEval(chnget:S("__panaeolus_test"))\n');
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), 0, source);
}

function startJob(engine: number, source: string) {
  api.csoundSetStringChannel(engine, "__panaeolus_test", source);
  const started = performance.now();
  api.csoundEvalCode(engine, 'return PanaeolusEvalStart(chnget:S("__panaeolus_test"))\n');
  const elapsed = performance.now() - started;
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), 1, "Job did not become pending");
  return elapsed;
}

function perform(engine: number) {
  const started = performance.now();
  assert.equal(api.csoundPerformKsmps(engine), 0, "Audio engine stopped during evaluation");
  return performance.now() - started;
}

function samples(engine: number) {
  return new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), blockSize * 2);
}

function advance(engine: number, seconds: number) {
  for (let block = 0; block < Math.round(seconds / blockSeconds); block++) perform(engine);
}

function native(engine: number, expression: string) {
  return api.csoundEvalCode(engine, `return ${expression}`);
}

function report(label: string, startMs: number, times: number[]) {
  const sorted = [...times].sort((a, b) => a - b);
  const cpuMs = times.reduce((total, time) => total + time, 0);
  const audioMs = times.length * blockSeconds * 1000;
  const maximum = sorted.at(-1) ?? 0;
  const cpuRatio = cpuMs / audioMs;
  let debtMs = startMs;
  let maxBufferDebtMs = debtMs;
  for (const elapsed of times) {
    debtMs = Math.max(0, debtMs + elapsed - blockSeconds * 1000);
    maxBufferDebtMs = Math.max(maxBufferDebtMs, debtMs);
  }
  const result = { label, startMs, blocks: times.length, audioMs, cpuMs, cpuRatio,
    maxBufferDebtMs,
    maxBlockMs: maximum, medianBlockMs: sorted[Math.floor(sorted.length / 2)] ?? 0,
    p99BlockMs: sorted[Math.floor(sorted.length * 0.99)] ?? 0 };
  reports.push(result);
  console.log(JSON.stringify(result));
  if (process.env.LIVE_EVAL_MAX_BLOCK_MS) {
    const limit = Number(process.env.LIVE_EVAL_MAX_BLOCK_MS);
    assert.ok(startMs <= limit, `${label}: job submission ${startMs} ms exceeds ${limit} ms`);
    assert.ok(maximum <= limit, `${label}: block ${maximum} ms exceeds ${limit} ms`);
    assert.ok(maxBufferDebtMs <= limit, `${label}: accumulated buffer debt ${maxBufferDebtMs} ms exceeds ${limit} ms`);
  }
  if (process.env.LIVE_EVAL_MAX_CPU_RATIO && audioMs > 2048 / sr * 1000) {
    const limit = Number(process.env.LIVE_EVAL_MAX_CPU_RATIO);
    assert.ok(cpuRatio <= limit, `${label}: CPU/audio ratio ${cpuRatio} exceeds ${limit}`);
  }
}

const namespaces = `(ns live.test (:require [panaeolus.instrument :as p]
  [panaeolus.event-loop :as loop] [panaeolus.routing :as routing] [panaeolus.fx :as fx]))
  (defn sum-job [remaining total]
    (if (= remaining 0) total (sum-job (- remaining 1) (+ total remaining))))`;
const baseline = createEngine();
const live = createEngine();

try {
  const setup = `${namespaces}
    (p/definst pulse [hz 440 amp 0.04]
      (* (csound/poscil amp hz) (csound/linseg 0 0.005 1 (- p3 0.005) 0)))
    (pulse :every [0.25 0.5] :hz [440 550 660] :dur [0.08 0.1])`;
  evaluate(baseline, setup);
  evaluate(live, setup);
  advance(baseline, 0.2);
  advance(live, 0.2);

  function compareJob(label: string, source: string, expectedStatus = 0) {
    const startMs = startJob(live, source);
    const times: number[] = [];
    let status = 1;
    while (status === 1 && times.length < maxJobBlocks) {
      times.push(perform(live));
      // Copy before advancing the second Csound instance: WASM memory may grow.
      const actual = Float64Array.from(samples(live));
      perform(baseline);
      assert.deepEqual(actual, samples(baseline), `${label}: evaluation changed the running loop's audio`);
      assert.equal(api.csoundGetCurrentTimeSamples(live), api.csoundGetCurrentTimeSamples(baseline),
        `${label}: evaluation changed audio time`);
      status = api.csoundGetControlChannel(live, "__panaeolus_status");
    }
    assert.equal(status, expectedStatus, `${label}: job did not complete as expected`);
    if (expectedStatus === 0) assert.ok(times.length > 1, `${label}: job did not yield across audio blocks`);
    assert.equal(native(live, "i(panaeolusBeat)"), native(baseline, "i(panaeolusBeat)"),
      `${label}: beat clock changed during evaluation`);
    report(label, startMs, times);
  }

  compareJob("pure computation while pulse plays", "(def live-result (sum-job 120 0))");
  evaluate(live, '(if (= live-result 7260) true (throw "Wrong incremental result"))');
  compareJob("failure while pulse plays", `(do (def before-error 9)
    (throw "expected live job error") (def after-error 10))`, -1);
  evaluate(live, `(if (= before-error 9) true (throw "Lost pre-error binding"))
    (if (= :missing (try* after-error (catch* error :missing))) true
      (throw "Evaluation continued after its error"))`);
  compareJob("recovery after failure", "(def live-result (sum-job 20 0))");
  evaluate(live, '(if (= live-result 210) true (throw "Recovery job failed"))');
} finally {
  api.csoundStop(baseline); api.csoundDestroy(baseline);
  api.csoundStop(live); api.csoundDestroy(live);
}

const routed = createEngine();
try {
  evaluate(routed, `${namespaces}
    (p/definst flat [amp 0.1] (csound/linseg amp 1 amp))
    (fx/deffx gain [amount 1] [left right] [(* left amount) (* right amount)])
    (def old-gain gain)
    (flat :id :line :every [1] :dur 0.6 :fx (gain 0.25))
    (def old-route (get (routing/current-route :line) :number))`);
  advance(routed, 0.2);
  const oldRoute = api.csoundEvalCode(routed,
    'value:MalValue init MalResolveSymbol(panaeolusEnv, "live.test/old-route")\nreturn value.number');
  const startMs = startJob(routed, `(def replacement-result (sum-job 120 0))
    (fx/deffx gain [amount 1] [left right]
      [(* left (* amount 2)) (* right (* amount 2))])
    (flat :id :line :every [1] :dur 0.6 :fx (gain 0.25))`);
  const times: number[] = [];
  let status = 1;
  while (status === 1 && times.length < maxJobBlocks) {
    times.push(perform(routed));
    for (const sample of samples(routed)) {
      assert.ok(Number.isFinite(sample) && sample > 0.024,
        "The sustained loop dropped out while its FX replacement evaluated");
    }
    status = api.csoundGetControlChannel(routed, "__panaeolus_status");
  }
  assert.equal(status, 0, "FX replacement job failed or did not finish");
  report("FX definition and loop replacement", startMs, times);
  evaluate(routed, `(if (= replacement-result 7260) true (throw "Lost job result"))
    (if (not= old-route (get (routing/current-route :line) :number)) true
      (throw "FX replacement retained the old route"))
    (if (not= (get (fx/descriptor old-gain) :revision) (get (fx/descriptor gain) :revision)) true
      (throw "FX replacement changed the saved definition"))`);
  advance(routed, 1.3);
  assert.equal(native(routed, `panaeolusRouteActive[${oldRoute}]`), 0, "Old route did not drain");
  for (const sample of samples(routed)) {
    assert.ok(sample >= 0.049 && sample <= 0.101, `Replacement FX has the wrong gain: ${sample}`);
  }
  evaluate(routed, "(p/stop-all!)");
  advance(routed, 0.8);
  assert.ok(samples(routed).every(sample => sample === 0), "Stopping left audible routes");
} finally {
  api.csoundStop(routed); api.csoundDestroy(routed);
}

console.log(JSON.stringify(reports, null, 2));
console.log("PASS: live jobs preserve audio and beat time, handle failures, and replace FX while loops play.");
