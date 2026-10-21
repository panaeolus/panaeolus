import assert from "node:assert/strict";
import { resolve } from "node:path";

Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../node_modules/@csound/browser/dist/csound.js");
const api = await libcsound();
const csound = api.csoundCreate();
const root = resolve(import.meta.dir, "..");

function evaluate(source: string) {
  api.csoundSetControlChannel(csound, "__panaeolus_status", -999);
  api.csoundSetStringChannel(csound, "__panaeolus_test", `${source}\nnil`);
  api.csoundEvalCode(csound, 'return PanaeolusEval(chnget:S("__panaeolus_test"))\n');
  assert.equal(api.csoundGetControlChannel(csound, "__panaeolus_status"), 0, source);
}

function check(form: string) {
  evaluate(`(when-not ${form} (throw (str "Failed: " (pr-str '${form}))))`);
}

function advance(blocks: number) {
  let peak = 0;
  for (let block = 0; block < blocks; block++) {
    assert.equal(api.csoundPerformKsmps(csound), 0);
    for (const sample of new Float64Array(api.getMemory().buffer, api.csoundGetSpout(csound), 128)) {
      assert.ok(Number.isFinite(sample));
      peak = Math.max(peak, Math.abs(sample));
    }
  }
  return peak;
}

try {
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(csound, option);
  const mal = await Bun.file(resolve(root, "dist/make-a-lisp.orc")).text();
  const panaeolus = await Bun.file(resolve(root, "dist/panaeolus.orc")).text();
  assert.equal(api.csoundCompileOrc(csound,
    `sr = 48000\nksmps = 64\nnchnls = 2\n0dbfs = 1\n${mal}\n${panaeolus}`), 0);
  assert.equal(api.csoundStart(csound), 0);
  // Full specs in callable metadata or prepared actions used to exhaust the
  // browser engine's 1 GiB memory before this ordinary 64-segment score played.
  evaluate(`(ns test.scale (:require [panaeolus.instrument :as p]
    [panaeolus.event-loop :as loop] [panaeolus.composition :as composition]))
    (p/definst tone [hz 440] (csound/poscil 0.001 hz))
    (def a (p/pattern tone :every [0.05]))
    (def section (p/for-beats 0.2 (p/together a a a a a a a a a a a a a a a a)))
    (def song (p/repeat-times 4 section))
    (song)`);
  let peak = advance(40);
  check("(= 17 (count (get @loop/state :names)))");
  for (let section = 0; section < 3; section++) {
    peak = Math.max(peak, advance(75));
    check("(= 17 (count (get @loop/state :names)))");
  }
  peak = Math.max(peak, advance(140));
  assert.ok(peak > 0.01 && peak <= 1, `Parallel composition audio peak ${peak}`);
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");
  assert.equal(advance(20), 0, "Finished score left audio running");
  // A manual stop must also release ready records for sections not yet played.
  evaluate("(song :id :early-stop)");
  assert.ok(advance(10) > 0);
  evaluate("(p/stop! :early-stop)");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");
  advance(100);
  assert.equal(advance(20), 0, "Stopped score left audio running");
  console.log("PASS: 64-segment composition, 16 parallel voices, finite completion, early stop, and registry cleanup.");
} finally {
  api.csoundDestroy(csound);
}
