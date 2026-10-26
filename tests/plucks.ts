import assert from "node:assert/strict";
import { loudness, peak, pcmWave } from "../tools/audio-level";

Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const api = await libcsound();
const engine = api.csoundCreate();
const sampleRate = 48000;
const blockSize = 64;
const logs: string[] = [];
const log = console.log.bind(console);
console.log = (...args: unknown[]) => { logs.push(args.join(" ")); log(...args); };
function evaluate(source: string, expected = 0) {
  api.csoundSetControlChannel(engine, "__panaeolus_status", -999);
  api.csoundSetStringChannel(engine, "__plucks_test", source + "\nnil");
  api.csoundEvalCode(engine, 'return PanaeolusEval(chnget:S("__plucks_test"))');
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), expected, source);
}
function advance(seconds: number) {
  const count = Math.round(seconds * sampleRate / blockSize);
  const result = new Float64Array(count * blockSize * 2);
  for (let block = 0; block < count; block++) {
    assert.equal(api.csoundPerformKsmps(engine), 0, "Engine stopped");
    result.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), blockSize * 2), block * blockSize * 2);
  }
  assert.ok(result.every(Number.isFinite));
  return result;
}
function near(value: number, target: number, tolerance: number, label: string) {
  assert.ok(Math.abs(value - target) <= tolerance, `${label}: ${value} expected ${target} ± ${tolerance}`);
}
function join(parts: Float64Array[]) {
  const result = new Float64Array(parts.reduce((sum, part) => sum + part.length, 0));
  let offset = 0;
  for (const part of parts) { result.set(part, offset); offset += part.length; }
  return result;
}
try {
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
  const mal = await Bun.file(new URL("../dist/make-a-lisp.orc", import.meta.url)).text();
  const panaeolus = await Bun.file(new URL("../dist/panaeolus.orc", import.meta.url)).text();
  assert.equal(api.csoundCompileOrc(engine,
    `sr=${sampleRate}\nksmps=${blockSize}\nnchnls=2\n0dbfs=1\n${mal}\n${panaeolus}`), 0);
  assert.equal(api.csoundStart(engine), 0);
  evaluate(`(ns test.plucks (:require [panaeolus.instrument :as p]
    [panaeolus.instruments.pluck :refer [pluck fmpluck]]
    [panaeolus.fx :as fx] [panaeolus.sampler :as sampler]))`);
  const reference: Float64Array[] = [];
  for (const name of ["pluck", "fmpluck"]) {
    evaluate(`(${name})`);
    const defaults = advance(1);
    assert.ok(peak(defaults) > 0.05 && peak(defaults) < 1, `${name} default one-shot`);
    evaluate(`(${name} :db 0)`);
    const unity = advance(1);
    near(loudness(defaults, sampleRate) - loudness(unity, sampleRate), -6, 0.01, `${name} dB scale`);
    evaluate(`(${name} :db 0 :amp 0.5)`);
    near(loudness(advance(1), sampleRate) - loudness(unity, sampleRate), 20 * Math.log10(0.5), 0.01, `${name} linear amp`);
    evaluate(`(${name} :amp 0)`);
    assert.equal(peak(advance(1)), 0);
    evaluate(`(${name} 60 0 :pan -1)`);
    const left = advance(1);
    assert.equal(peak(left.filter((_, index) => index % 2 === 1)), 0);
    near(loudness(left, sampleRate), loudness(unity, sampleRate), 0.01, `${name} constant-power pan`);
    const parts: Float64Array[] = [];
    for (const note of [48, 55, 60, 67, 72, 79]) {
      evaluate(`(${name} :note ${note} :db 0)`);
      parts.push(advance(1));
    }
    const phrase = join(parts);
    reference.push(phrase);
    const measured = loudness(phrase, sampleRate);
    console.log(`${name}: ${measured.toFixed(2)} LUFS at :db 0`);
    near(measured, -18, 0.3, `${name} reference loudness`);
    assert.ok(peak(phrase) < 0.71, `${name} reference has less than 3 dB headroom`);
    for (const form of [
      `(${name} :db 13)`, `(${name} :db -97)`, `(${name} :amp -1)`,
      `(${name} :pan 2)`, `(${name} :note 23)`, `(${name} :note 97)`,
      `(${name} :decay 0)`, `(${name} :legato true)`,
      `(${name} :db [-6 0])`, `(${name} :every [1] :db [])`,
    ]) evaluate(form, -1);
    evaluate(`(${name} :dur 0.001)`);
    assert.ok(peak(advance(0.02)) < 1, "Very short note failed");
  }
  for (const form of ["(pluck :tone 2)", "(pluck :position 0)", "(fmpluck :ratio 0)", "(fmpluck :index 9)"])
    evaluate(form, -1);
  // Equal input material at very different file levels must reach one target.
  for (const [name, scale] of [["quiet", 0.1], ["loud", 1]] as const) {
    api.writeFile(engine, `${name}.wav`, pcmWave(reference[0].map(value => value * scale), sampleRate));
    evaluate(`(def ${name} (sampler/load "${name}.wav" :id :${name})) (${name} :db 0 :cross 0)`);
    const sample = advance(6);
    near(loudness(sample, sampleRate), -18, 0.15, `${name} sample normalization`);
    evaluate(`(${name} :db -6 :cross 0)`);
    near(loudness(advance(6), sampleRate), -24, 0.15, `${name} sample dB scale`);
  }
  api.writeFile(engine, "silent.wav", pcmWave(new Float64Array(4800), sampleRate));
  evaluate('(def silent (sampler/load "silent.wav")) (silent :db 0)');
  assert.equal(peak(advance(0.1)), 0, "Normalization amplified silence");
  evaluate('(sampler/load "silent.wav" :normalize :yes)', -1);
  evaluate('(quiet :db 13)', -1);
  evaluate('(quiet :every [1] :db [0 -97])', -1);
  // Plans remain silent until called, and each dB vector wraps independently.
  evaluate(`(p/tempo! 120)
    (def phrase (p/for-beats 4 (p/pattern pluck :every [1] :dur 0.45 :db [0 -6])))`);
  assert.equal(peak(advance(0.02)), 0);
  evaluate('(phrase :id :phrase)');
  const pattern = advance(2.1);
  const windows: number[] = [];
  for (let index = 0; index < 4; index++) {
    // Measure the complete attack: clock dispatch may differ by one block.
    const start = Math.round(index * 0.5 * sampleRate) * 2;
    windows.push(20 * Math.log10(peak(pattern.slice(start, start + 0.5 * sampleRate * 2))));
  }
  near(windows[0] - windows[1], 6, 0.15, "Pattern dB alternation");
  near(windows[0], windows[2], 0.15, "Pattern dB wrap");
  evaluate('(p/stop-all!)'); advance(0.1);
  // Freeze a take with both redesigned voices, a normalized sample, and FX.
  evaluate(`(p/record!)
    (pluck :every [0.5] :dur 0.2 :note [55 60 67] :db [-6 0] :fx (fx/flanger :rate 0.2))
    (fmpluck :every [1] :dur 0.35 :note [60 72] :db -6)
    (quiet :every [2] :dur 0.2 :db -12 :cross 0)`);
  const live = advance(1.2);
  evaluate('(p/finish!) (p/export!)');
  const csd = api.csoundGetStringChannel(engine, "__panaeolus_export");
  assert.ok(csd.includes("PanaeolusPluck") && csd.includes("PanaeolusFMPluck"));
  assert.ok(!csd.includes("MalValue"), "Export needs the Lisp interpreter");
  const replay = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0"]) api.csoundSetOption(replay, option);
    assert.equal(api.csoundCompileCSD(replay, csd), 0);
    assert.equal(api.csoundStart(replay), 0);
    const rendered = new Float64Array(live.length);
    for (let offset = 0; offset < rendered.length; offset += blockSize * 2) {
      assert.equal(api.csoundPerformKsmps(replay), 0);
      rendered.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(replay), blockSize * 2), offset);
    }
    assert.deepEqual(rendered, live, "Pluck/FX/normalized-sample replay differs");
  } finally { api.csoundDestroy(replay); }
  assert.doesNotMatch(logs.join("\n"), /\b(?:INIT ERROR|PERF ERROR|InitError|PerfError)\b/);
  console.log("PASS: pluck defaults, controls, levels, patterns, normalized samples, FX, and exact CSD replay.");
} finally { api.csoundDestroy(engine); }
