import assert from "node:assert/strict";
import { api } from "../tools/catalog-audio";
import { peak, pcmWave } from "../tools/audio-level";
const engine = api.csoundCreate();
const rate = 48000;
function evaluate(form: string, expected = 0) {
  api.csoundSetStringChannel(engine, "__spectral_test", form + "\nnil");
  api.csoundEvalCode(engine, 'return PanaeolusEval(chnget:S("__spectral_test"))');
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), expected, form);
}
function check(form: string) { evaluate(`(when-not ${form} (throw "spectral assertion failed"))`); }
function native(form: string) { return api.csoundEvalCode(engine, `return ${form}`); }
function advance(seconds: number, target = engine) {
  const result = new Float64Array(Math.round(seconds * rate / 64) * 128);
  for (let offset = 0; offset < result.length; offset += 128) {
    assert.equal(api.csoundPerformKsmps(target), 0);
    result.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(target), 128), offset);
  }
  return result;
}
try {
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
  assert.equal(api.csoundCompileOrc(engine, `sr=${rate}\nksmps=64\nnchnls=2\n0dbfs=1\n` +
    await Bun.file(new URL("../dist/make-a-lisp.orc", import.meta.url)).text() + "\n" +
    await Bun.file(new URL("../dist/panaeolus.orc", import.meta.url)).text()), 0);
  assert.equal(api.csoundStart(engine), 0);
  const sample = new Float64Array(24000 * 2);
  for (let frame = 0; frame < 24000; frame++) for (const channel of [0, 1]) {
    sample[frame * 2 + channel] = Math.sin(2 * Math.PI * [440, 660][channel] * frame / 24000) * 0.2;
  }
  api.writeFile(engine, "spectral.wav", pcmWave(sample, 24000));
  evaluate(`(ns test.spectral (:require [panaeolus.sampler :as sampler]
    [panaeolus.instrument :as p] [panaeolus.routing :as routing] [panaeolus.fx :as fx]))
    (def pitched (sampler/pitched "spectral.wav"))
    (def stretched (sampler/stretch "spectral.wav" :fft 1024))`);
  check('(= 1 (get (get (p/descriptor pitched) :defaults) :stretch))');
  check('(= 4 (get (get (p/descriptor stretched) :defaults) :stretch))');
  assert.equal(native("panaeolusSampleCount"), 1);
  assert.equal(native("panaeolusSampleValues"), 96000, "Shared split tables allocated twice");
  evaluate('(pitched :dur 0.5)');
  assert.ok(peak(advance(0.6)) > 0.02);
  const bad = [
    '(sampler/stretch "spectral.wav" :fft 123)', '(sampler/pitched "spectral.wav" :normalize 1)',
    '(sampler/stretch "spectral.wav" :fft 1024 :fft 2048)', '(stretched :stretch 0)',
    '(stretched :speed 5)', '(stretched :lock 0.5)', '(stretched :start 0.9 :end 0.1)',
    '(stretched :every [1] :start [0 0.8] :end [0.5 1 0.7])', '(pitched :note 97)',
  ];
  evaluate(bad.map(form => `(when-not (= :bad (try* (do ${form} :ok) (catch* e :bad)))
    (throw ${JSON.stringify(`Accepted ${form}`)}))`).join("\n"));
  evaluate('(p/record!) (stretched :id :pad :legato true :every [0.25 0.5] :note [60 67 72] :release 0.08 :fx (fx/flanger :rate 0.2))');
  const parts = [advance(0.6)];
  evaluate('(def route (get (routing/current-route :pad) :number)) (stretched :id :pad :legato true :speed 0 :position 0.5 :release 0.08 :fx (fx/flanger :rate 0.2))');
  check('(= route (get (routing/current-route :pad) :number))');
  parts.push(advance(0.2));
  evaluate('(p/stop! :pad)'); parts.push(advance(4.2));
  evaluate('(p/finish!) (p/export!)');
  const csd = api.csoundGetStringChannel(engine, "__panaeolus_export");
  assert.ok(csd.includes("PanaeolusSampleChannel("));
  api.unlink(engine, "spectral.wav");
  const replay = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0"]) api.csoundSetOption(replay, option);
    assert.equal(api.csoundCompileCSD(replay, csd), 0);
    assert.equal(api.csoundStart(replay), 0);
    for (const key of ["Tables", "Left", "Right"]) {
      const table = native(`panaeolusSample${key}[0]`);
      for (const meta of ["ftsr", "ftchnls", "nsamp", "ftlen"]) {
        assert.equal(api.csoundEvalCode(replay, `return ${meta}(${table})`), native(`${meta}(${table})`), key + " " + meta);
      }
      assert.deepEqual(api.csoundTableCopyOut(replay, table), api.csoundTableCopyOut(engine, table), `Export changed ${key}`);
    }
    for (const audio of parts) {
      const copy = advance(audio.length / 96000, replay);
      const mismatch = copy.findIndex((x, i) => x !== audio[i]);
      assert.equal(mismatch, -1, `Spectral differs at ${mismatch}: ${copy[mismatch]} vs ${audio[mismatch]}`);
    };
  } finally { api.csoundDestroy(replay); }
  assert.equal(peak(advance(0.1)), 0);
  // Mono imports share one derived table; splitting must obey the same memory
  // budget as loading, and a rejected split must leave the cache retryable.
  const mono = new Uint8Array(44 + 24000 * 2);
  mono.set(pcmWave(sample, 24000).slice(0, 44));
  const header = new DataView(mono.buffer);
  header.setUint32(4, mono.length - 8, true);
  header.setUint16(22, 1, true);
  header.setUint32(28, 48000, true);
  header.setUint16(32, 2, true);
  header.setUint32(40, 48000, true);
  for (let frame = 0; frame < 24000; frame++) header.setInt16(44 + frame * 2, Math.round(sample[frame * 2] * 32767), true);
  api.writeFile(engine, "mono.wav", mono);
  evaluate('(def mono-info (panaeolus.event-loop/native-value "PanaeolusSampleLoad" {:path "mono.wav"}))');
  const stored = native("panaeolusSampleValues");
  api.csoundEvalCode(engine, "panaeolusSampleValues = 9999999\nreturn 0");
  evaluate('(sampler/pitched "mono.wav")', -1);
  assert.equal(native("panaeolusSampleLeft[1]"), 0);
  api.csoundEvalCode(engine, `panaeolusSampleValues = ${stored}\nreturn 0`);
  evaluate('(def mono (sampler/pitched "mono.wav" :normalize false)) (mono :dur 0.2)');
  assert.ok(peak(advance(0.3)) > 0.05);
  assert.equal(native("panaeolusSampleLeft[1]"), native("panaeolusSampleRight[1]"));
  assert.equal(native("panaeolusSampleValues"), stored + 24000);
  console.log("PASS: spectral loaders, cached stereo tables, bounds, held edits, FX, exact standalone CSD replay.");
} finally { api.csoundDestroy(engine); }
