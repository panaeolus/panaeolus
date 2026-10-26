import assert from "node:assert/strict";
import { loudness, peak } from "../tools/audio-level";

Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const api = await libcsound();
const engine = api.csoundCreate();
const rate = 48000;
const blockSize = 64;
const messages: string[] = [];
const print = console.log.bind(console);
console.log = (...args: unknown[]) => { messages.push(args.join(" ")); print(...args); };
function evaluate(source: string, expected = 0) {
  api.csoundSetControlChannel(engine, "__panaeolus_status", -999);
  api.csoundSetStringChannel(engine, "__synth_test", source + "\nnil");
  api.csoundEvalCode(engine, 'return PanaeolusEval(chnget:S("__synth_test"))');
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), expected, source);
}
function advance(seconds: number, target = engine) {
  const blocks = Math.round(seconds * rate / blockSize);
  const result = new Float64Array(blocks * blockSize * 2);
  for (let block = 0; block < blocks; block++) {
    const status = api.csoundPerformKsmps(target);
    assert.equal(status, 0, "Engine ended early");
    result.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(target), blockSize * 2), block * blockSize * 2);
  }
  assert.ok(result.every(Number.isFinite));
  return result;
}
function near(actual: number, expected: number, tolerance: number, label: string) {
  assert.ok(Math.abs(actual - expected) < tolerance, `${label}: ${actual} expected ${expected} ± ${tolerance}`);
}
function native(expression: string) { return api.csoundEvalCode(engine, `return ${expression}`); }
function check(form: string) { evaluate(`(when-not ${form} (throw "synth assertion failed: ${form.replaceAll('"', '\\"')}"))`); }
function route(id: string) {
  evaluate(`(def __route (get (routing/current-route ${id}) :number))`);
  return api.csoundEvalCode(engine, 'value:MalValue init MalResolveSymbol(panaeolusEnv, "test.synths/__route")\nreturn value.number');
}
function join(parts: Float64Array[]) {
  const audio = new Float64Array(parts.reduce((sum, part) => sum + part.length, 0));
  let offset = 0;
  for (const part of parts) { audio.set(part, offset); offset += part.length; }
  return audio;
}
function rms(audio: Float64Array) { return Math.sqrt(audio.reduce((sum, x) => sum + x * x, 0) / audio.length); }
try {
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
  const mal = await Bun.file(new URL("../dist/make-a-lisp.orc", import.meta.url)).text();
  const panaeolus = await Bun.file(new URL("../dist/panaeolus.orc", import.meta.url)).text();
  assert.equal(api.csoundCompileOrc(engine, `sr=${rate}\nksmps=${blockSize}\nnchnls=2\n0dbfs=1\n${mal}\n${panaeolus}`), 0);
  assert.equal(api.csoundStart(engine), 0);
  evaluate(`(ns test.synths (:require [panaeolus.instrument :as p]
    [panaeolus.instruments.synth :refer [hammer lush]]
    [panaeolus.routing :as routing] [panaeolus.fx :as fx] [panaeolus.event-loop :as loop]))`);
  const invalid: string[] = [];
  for (const name of ["hammer", "lush"]) {
    evaluate(`(${name})`);
    assert.ok(peak(advance(1.1)) > 0.02, `${name} default is silent`);
    const parts: Float64Array[] = [];
    for (const note of [48, 55, 60, 67, 72, 79]) {
      evaluate(`(${name} :note ${note} :db 0)`);
      parts.push(advance(1));
    }
    const audio = join(parts);
    near(loudness(audio, rate), -18, 0.3, `${name} reference loudness`);
    assert.ok(peak(audio) < 0.71, `${name} reference headroom`);
    evaluate(`(${name} :note 60 :db 0)`);
    const unity = advance(1);
    evaluate(`(${name} 60 -6)`);
    near(loudness(advance(1), rate) - loudness(unity, rate), -6, 0.01, `${name} dB scale`);
    evaluate(`(${name} :note 60 :db 0 :amp 0.5)`);
    near(loudness(advance(1), rate) - loudness(unity, rate), 20 * Math.log10(0.5), 0.01, `${name} linear amp`);
    evaluate(`(${name} :amp 0)`);
    assert.ok(peak(advance(1)) < 1e-15, `${name} mute left audible output`);
    evaluate(`(${name} :dur 0.001)`);
    assert.ok(peak(advance(0.02)) < 1);
    for (const args of [":db 13", ":note 23", ":note 97", ":amp -1", ":pan 2", ":decay 0"])
      invalid.push(`(${name} ${args})`);
  }
  for (const form of ["(hammer :hardness 2)", "(hammer :metal -1)", "(hammer :legato true)",
    "(lush :cutoff 0)", "(lush :resonance 0.9)", "(lush :detune 41)", "(lush :width 2)",
    "(lush :attack 0)", "(lush :sustain 2)", "(lush :release 5)", "(lush :glide -1)",
    "(lush :every [1] :release [0.1 5])", "(p/with-release hammer :decay)", "(p/with-release lush :unknown)"])
    invalid.push(form);
  // Batch independent validation checks in one evaluator invocation.
  evaluate(invalid.map(form => `(when-not (= :invalid
    (try* (do ${form} :valid) (catch* error :invalid)))
    (throw ${JSON.stringify(`Accepted invalid synth call: ${form}`)}))`).join("\n"));

  evaluate('(lush :id :pad :legato true :attack 0.4 :sustain 1 :release 0.12)');
  advance(0.5);
  const before = advance(0.05);
  const originalRoute = route(":pad");
  const originalVoice = native(`panaeolusHeldInstruments[${originalRoute}]`);
  evaluate('(lush :id :pad :legato true :note 67 :attack 0.4 :sustain 1 :release 0.12)');
  assert.ok(rms(advance(0.05)) > rms(before) * 0.5, "A held edit restarted the attack");
  assert.equal(route(":pad"), originalRoute);
  assert.equal(native(`panaeolusHeldInstruments[${originalRoute}]`), originalVoice);
  evaluate('(lush :id :pad :legato true :release 9)', -1);
  assert.equal(route(":pad"), originalRoute, "Invalid edit replaced a live voice");
  evaluate('(p/stop! :pad)');
  assert.equal(native(`panaeolusRouteActive[${originalRoute}]`), 2);
  assert.ok(peak(advance(0.04)) > 0, "Stop cut off the release");
  advance(0.15);
  assert.equal(peak(advance(0.02)), 0, "Held release never ended");
  assert.equal(native(`panaeolusRouteActive[${originalRoute}]`), 0, "Release leaked its route");

  // A release tail must not revive a voice stopped before its queued init.
  evaluate('(lush :id :cancel :legato true :release 0.02) (p/stop! :cancel)');
  assert.equal(peak(advance(0.08)), 0, "Stopping a queued voice still sounded a note");

  // FX replacement gets a new route while the previous voice and FX drain.
  evaluate('(lush :id :pad :legato true :release 0.06 :fx (fx/flanger :rate 0.2))');
  advance(0.1);
  const wet = route(":pad");
  evaluate('(lush :id :pad :legato true :release 0.06)');
  advance(0.1);
  assert.notEqual(route(":pad"), wet);
  assert.equal(native(`panaeolusRouteActive[${wet}]`), 2);
  evaluate('(p/stop! :pad)'); advance(4.2);
  assert.equal(peak(advance(0.02)), 0);
  assert.equal(native(`panaeolusRouteActive[${wet}]`), 0);

  // A finite higher-order plan releases a sustaining pad at its end.
  evaluate(`(p/tempo! 120)
    (def song (p/for-beats 0.4 (p/together
      (p/pattern lush :legato true :every [0.1] :note [60 67] :release [0.03 0.08])
      (p/pattern hammer :every [0.1] :note [48 55] :dur 0.08))))`);
  assert.equal(peak(advance(0.02)), 0, "Building a plan started audio");
  evaluate('(song)'); assert.ok(peak(advance(0.1)) > 0);
  advance(0.25);
  assert.equal(peak(advance(0.02)), 0, "Finite composition left a sustaining voice");
  check('(empty? (get @loop/state :names))');

  evaluate('(p/record!) (lush :id :take :legato true :release 0.08 :fx (fx/flanger :rate 0.2))');
  const chunks = [advance(0.12)];
  evaluate('(lush :id :take :legato true :note 67 :cutoff 1800 :release 0.16 :fx (fx/flanger :rate 0.2))');
  chunks.push(advance(0.12));
  evaluate('(hammer :note 48 :dur 0.12) (p/stop! :take)');
  chunks.push(advance(4.22));
  evaluate('(p/finish!) (p/export!)');
  const document = api.csoundGetStringChannel(engine, "__panaeolus_export");
  assert.ok(!document.includes("MalValue"));
  for (const line of document.split("\n").filter(line => line.startsWith("i 92 "))) {
    const fields = line.split(/\s+/);
    assert.equal(fields.length, 6, "Stop score omitted p5 and could inherit a prior release flag");
    if (Number(fields[4]) >= 10000) assert.equal(Number(fields[5]), 0, "Drained processor stop allowed another release");
  }
  const replay = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0"]) api.csoundSetOption(replay, option);
    assert.equal(api.csoundCompileCSD(replay, document), 0);
    assert.equal(api.csoundStart(replay), 0);
    const live = join(chunks);
    const rendered = advance(live.length / (rate * 2), replay);
    const mismatch = rendered.findIndex((sample, index) => !Object.is(sample, live[index]));
    assert.equal(mismatch, -1,
      `Synth replay differs at sample ${mismatch}: ${rendered[mismatch]} vs ${live[mismatch]}`);
  } finally { api.csoundDestroy(replay); }

  // Finishing a take releases only its replay; the live pad keeps sounding.
  evaluate('(p/record!) (lush :id :finish :legato true :release 0.1)');
  advance(0.15);
  evaluate('(p/finish!) (p/export!)');
  assert.ok(peak(advance(0.2)) > 0, "Finishing a take stopped the live pad");
  const finish = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0"]) api.csoundSetOption(finish, option);
    assert.equal(api.csoundCompileCSD(finish, api.csoundGetStringChannel(engine, "__panaeolus_export")), 0);
    assert.equal(api.csoundStart(finish), 0);
    assert.ok(peak(advance(0.18, finish)) > 0);
    let ended = false;
    for (let block = 0; block < Math.ceil(0.2 * rate / blockSize); block++) {
      if (api.csoundPerformKsmps(finish) !== 0) { ended = true; break; }
    }
    assert.ok(ended, "Finished take has an endless release");
  } finally { api.csoundDestroy(finish); }
  evaluate('(p/stop-all!)'); advance(0.2);
  assert.equal(peak(advance(0.02)), 0);
  assert.doesNotMatch(messages.join("\n"), /\b(?:INIT ERROR|PERF ERROR|InitError|PerfError)\b/);
  console.log("PASS: hammer/lush levels, controls, held edits, release/FX cleanup, composition, and exact CSD replay.");
} finally { api.csoundDestroy(engine); }
