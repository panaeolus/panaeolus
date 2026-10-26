import assert from "node:assert/strict";
import { resolve } from "node:path";

Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const writeLog = console.log.bind(console);
const messages: string[] = [];
console.log = (...args: unknown[]) => {
  messages.push(args.map(String).join(" "));
  writeLog(...args);
};
const api = await libcsound();
const engine = api.csoundCreate();
const root = resolve(import.meta.dir, "..");
const blockSize = 64;
const sampleRate = 48000;
const monoPath = "sampler-mono-24k.wav";
const stereoPath = "sampler-stereo-24k.wav";
let checks = 0;

function wave(channels: number, signal: (frame: number, channel: number) => number, bits: 16 | 64 = 16) {
  const rate = 24000;
  const frames = 6000;
  const width = bits / 8;
  const bytes = new Uint8Array(44 + frames * channels * width);
  const view = new DataView(bytes.buffer);
  const text = (offset: number, value: string) => {
    for (let index = 0; index < value.length; index++) view.setUint8(offset + index, value.charCodeAt(index));
  };
  text(0, "RIFF"); view.setUint32(4, bytes.length - 8, true); text(8, "WAVE");
  text(12, "fmt "); view.setUint32(16, 16, true); view.setUint16(20, bits === 16 ? 1 : 3, true);
  view.setUint16(22, channels, true); view.setUint32(24, rate, true);
  view.setUint32(28, rate * channels * width, true); view.setUint16(32, channels * width, true);
  view.setUint16(34, bits, true); text(36, "data"); view.setUint32(40, frames * channels * width, true);
  for (let frame = 0; frame < frames; frame++) {
    for (let channel = 0; channel < channels; channel++) {
      const offset = 44 + (frame * channels + channel) * width;
      const value = signal(frame, channel);
      if (bits === 16) view.setInt16(offset, Math.round(value * 32767), true);
      else view.setFloat64(offset, value, true);
    }
  }
  return bytes;
}

function evaluate(source: string, expected = 0, malformedFile = false) {
  const firstMessage = messages.length;
  api.csoundSetControlChannel(engine, "__panaeolus_status", -999);
  api.csoundSetStringChannel(engine, "__panaeolus_test", `${source}\nnil`);
  api.csoundEvalCode(engine, 'return PanaeolusEval(chnget:S("__panaeolus_test"))\n');
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), expected, source);
  const emitted = messages.slice(firstMessage).join("\n");
  if (malformedFile) {
    // Csound's file metadata opcodes report malformed files as init errors.
    // The Lisp bridge must keep its own error and leave the live state intact.
    assert.match(emitted, /error reading input file/i);
    assert.match(emitted, /sample: could not read audio file/);
    assert.doesNotMatch(emitted, /ftgen error|no matching signature|PERF ERROR|PerfError/);
  } else {
    assert.doesNotMatch(emitted, /\b(?:INIT ERROR|PERF ERROR|InitError|PerfError)\b/,
      `Lisp evaluation escaped into a native error: ${source}`);
  }
  checks++;
}

function check(form: string) {
  evaluate(`(when-not ${form} (throw (str "Failed: " (pr-str '${form}))))`);
}

function native(expression: string) {
  return api.csoundEvalCode(engine, `return ${expression}`);
}

function number(form: string) {
  evaluate(`(def __test_number ${form})`);
  return api.csoundEvalCode(engine,
    'value:MalValue init MalResolveSymbol(panaeolusEnv, "test.sampler/__test_number")\nreturn value.number');
}

function advance(seconds: number) {
  const result = new Float64Array(Math.round(seconds * sampleRate / blockSize) * blockSize * 2);
  for (let block = 0; block < result.length / (blockSize * 2); block++) {
    assert.equal(api.csoundPerformKsmps(engine), 0, "Live sampler engine stopped");
    result.set(new Float64Array(api.getMemory().buffer,
      api.csoundGetSpout(engine), blockSize * 2), block * blockSize * 2);
  }
  assert.ok(result.every(Number.isFinite), "Sampler produced non-finite audio");
  return result;
}

function peak(audio: Float64Array, channel?: number) {
  let result = 0;
  for (let index = channel ?? 0; index < audio.length; index += channel === undefined ? 1 : 2) {
    result = Math.max(result, Math.abs(audio[index]));
  }
  return result;
}

function mean(audio: Float64Array, from: number, to: number, channel = 0) {
  const start = Math.round(from * sampleRate);
  const end = Math.round(to * sampleRate);
  let sum = 0;
  for (let frame = start; frame < end; frame++) sum += audio[frame * 2 + channel];
  return sum / (end - start);
}

function near(actual: number, expected: number, tolerance: number, message: string) {
  assert.ok(Math.abs(actual - expected) <= tolerance, `${message}: ${actual} != ${expected}`);
  checks++;
}

function slope(audio: Float64Array) {
  return (mean(audio, 0.045, 0.05) - mean(audio, 0.02, 0.025)) / 0.025;
}

function clean() {
  evaluate("(p/stop-all!)");
  advance(0.4);
  assert.equal(peak(advance(0.01)), 0, "Stopped sampler left audio playing");
}

try {
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
  api.writeFile(engine, monoPath, wave(1, frame => 0.05 + 0.6 * frame / 5999));
  api.writeFile(engine, stereoPath, wave(2, (_frame, channel) => channel === 0 ? 0.2 : -0.4));
  api.writeFile(engine, "not-a-sample.wav", new TextEncoder().encode("not an audio file"));
  api.writeFile(engine, "unsafe-magnitude.wav", wave(1,
    frame => frame % 2 ? -Number.MAX_VALUE : Number.MAX_VALUE, 64));
  const mal = await Bun.file(resolve(root, "dist/make-a-lisp.orc")).text();
  const panaeolus = await Bun.file(resolve(root, "dist/panaeolus.orc")).text();
  assert.equal(api.csoundCompileOrc(engine,
    `sr = ${sampleRate}\nksmps = ${blockSize}\nnchnls = 2\n0dbfs = 1\n${mal}\n${panaeolus}`), 0);
  assert.equal(api.csoundStart(engine), 0);
  evaluate(`(ns test.sampler (:require [panaeolus.instrument :as p]
    [panaeolus.sampler :as sampler] [panaeolus.routing :as routing]
    [panaeolus.fx :as fx] [panaeolus.event-loop :as loop]))
    (p/tempo! 120)`);

  for (const form of [
    '(sampler/load "missing.wav")',
    '(sampler/load "")', '(sampler/load 1)',
    `(sampler/load "${monoPath}" :normalize false :mode :random)`,
    `(sampler/load "${monoPath}" :normalize false :id 123)`,
    `(sampler/load "${monoPath}" :normalize false :mode :forward :mode :reverse)`,
  ]) evaluate(form, -1);
  evaluate(`(def mono (sampler/load "${monoPath}" :normalize false))
    (def stereo (sampler/load "${stereoPath}" :normalize false))
    (def reverse (sampler/load "${monoPath}" :normalize false :mode :reverse :id :reverse))
    (def pingpong (sampler/load "${monoPath}" :normalize false :mode :pingpong :id :pingpong))`);
  check("(fn? mono)");
  check("(= [6000 1 24000 48000 0.25] (mapv (fn [key] (get (get (p/descriptor mono) :sample) key)) [:frames :channels :sample-rate :engine-rate :duration]))");
  check("(= 2 (get (get (p/descriptor stereo) :sample) :channels))");
  check("(= 0.25 (get (p/descriptor mono) :duration))");
  // A high-rate source has a fractional safe speed limit (3.675 here).
  // MAL division truncates, so both the accepted and rejected sides matter.
  evaluate("(sampler/validate {:frames 6000 :sample-rate 192000 :engine-rate 44100} {:db 0 :amp 0.5 :rate 3.5 :start 0 :end 1 :cross 0.005 :pan 0})");
  evaluate("(sampler/validate {:frames 6000 :sample-rate 192000 :engine-rate 44100} {:db 0 :amp 0.5 :rate 3.7 :start 0 :end 1 :cross 0.005 :pan 0})", -1);
  assert.equal(peak(advance(0.02)), 0, "Loading an instrument started playback");
  const table = number("(get (get (p/descriptor mono) :sample) :table)");
  // Replacing the file on disk must not change a loaded instrument's sample.
  api.writeFile(engine, monoPath, wave(1, () => -0.8));
  evaluate(`(def cached (sampler/load "${monoPath}" :normalize false))`);
  assert.equal(number("(get (get (p/descriptor cached) :sample) :table)"), table);

  evaluate("(mono)");
  const defaults = advance(0.1);
  near(slope(defaults), 1.2, 0.015, "24kHz mono played at the wrong speed or default amplitude");
  for (let index = 0; index < defaults.length; index += 2) {
    assert.equal(defaults[index], defaults[index + 1], "Mono sample was not copied to both outputs");
  }
  advance(0.17);
  assert.equal(peak(advance(0.01)), 0, "Default duration did not end at the file length");
  evaluate("(mono 1 2 :dur 0.08 :cross 0)");
  near(slope(advance(0.07)), 4.8, 0.025, "Positional speed ratio did not change playback speed");
  clean();
  evaluate("(reverse 1 :dur 0.08 :cross 0)");
  const backward = advance(0.07);
  near(slope(backward), -2.4, 0.025, "Reverse playback did not move backwards");
  assert.ok(mean(backward, 0.02, 0.025) > 0.5, "Reverse playback did not start near the loop end");
  clean();
  evaluate("(mono 1 :start 0.5 :end 1 :dur 0.08 :cross 0)");
  assert.ok(mean(advance(0.07), 0.02, 0.025) > 0.38, "Start position did not select the second half");
  clean();
  evaluate("(mono 1 :start 0.25 :end 0.5 :dur 0.2 :cross 0 :pan -1)");
  const slice = advance(0.18);
  assert.equal(peak(slice, 1), 0, "Hard left pan still played the right output");
  assert.ok(peak(slice, 0) > 0.32 && peak(slice, 0) < 0.36, "Slice read beyond its requested end");
  clean();
  evaluate("(stereo)");
  const pair = advance(0.06);
  near(mean(pair, 0.02, 0.05, 0), 0.1, 0.0001, "Stereo left was normalized or folded");
  near(mean(pair, 0.02, 0.05, 1), -0.2, 0.0001, "Stereo right was normalized or folded");
  clean();
  evaluate("(pingpong 1 :start 0.25 :end 0.5 :dur 0.2)");
  const alternating = advance(0.18);
  assert.ok(peak(alternating) > 0.3 && peak(alternating) < 0.36, `Ping-pong escaped its sample region: peak ${peak(alternating)}`);
  const differences: number[] = [];
  for (let frame = 600; frame < alternating.length / 2 - 100; frame += 100) {
    differences.push(alternating[(frame + 100) * 2] - alternating[frame * 2]);
  }
  assert.ok(differences.some(value => value > 0.003) && differences.some(value => value < -0.003),
    "Ping-pong playback never changed direction");
  clean();
  evaluate("(pingpong 1 :start 0.25 :end 0.5 :dur 0.16 :cross 0)");
  assert.ok(peak(advance(0.15)) < 1, "Zero-crossfade ping-pong produced excessive output");
  clean();

  // Held parameter edits keep the reading position, while FX topology changes
  // must retire the old route and voice.
  evaluate("(mono :id :held :legato true :amp 0.5 :cross 0)");
  advance(0.06);
  const heldRoute = number("(get (routing/current-route :held) :number)");
  const heldVoice = native(`panaeolusHeldInstruments[${heldRoute}]`);
  evaluate("(mono :id :held :legato true :amp 1 :cross 0)");
  const continued = advance(0.02);
  assert.equal(number("(get (routing/current-route :held) :number)"), heldRoute);
  assert.equal(native(`active:i(${heldVoice})`), 1);
  assert.ok(mean(continued, 0.01, 0.02) > 0.2, "Held amplitude edit restarted the sample");
  const sampleCount = native("panaeolusSampleCount");
  evaluate('(sampler/load "not-a-sample.wav")', -1, true);
  assert.equal(native("panaeolusSampleCount"), sampleCount, "A failed load registered an invalid sample");
  assert.ok(mean(advance(0.01), 0.005, 0.01) > 0.2, "A malformed sample interrupted the held voice");
  const unsafeMessage = messages.length;
  evaluate('(sampler/load "unsafe-magnitude.wav")', -1);
  assert.match(messages.slice(unsafeMessage).join("\n"), /sample: unsafe audio magnitude/);
  assert.equal(native("panaeolusSampleCount"), sampleCount, "An unsafe sample entered the file cache");
  assert.ok(mean(advance(0.01), 0.005, 0.01) > 0.2, "An unsafe sample interrupted the held voice");
  evaluate("(def quiet (p/with-defaults mono :amp 0.2))");
  evaluate("(quiet :start 0.5 :end 0.5)", -1);
  evaluate("(p/with-defaults mono :start 0.5 :end 0.5)", -1);
  for (const form of [
    "(mono :id :held :legato true :rate -1)",
    "(mono :id :held :legato true :rate 17)",
    "(mono :id :held :legato true :start 0.5 :end 0.5)",
    "(mono :id :held :legato true :start 0.999 :end 1)",
    "(mono :id :held :every [0.1] :start [0 0.8] :end [0.5 1 0.9])",
    "(mono :id :held :legato true :amp -1)",
    "(mono :id :held :legato true :pan 2)",
    "(mono :id :held :legato true :cross -1)",
    "(mono :id :held :legato true :dur 0.1)",
  ]) evaluate(form, -1);
  assert.equal(number("(get (routing/current-route :held) :number)"), heldRoute,
    "An invalid sampler edit replaced the playing route");
  assert.equal(native(`active:i(${heldVoice})`), 1);
  evaluate("(fx/deffx mute [gain 0] [left right] [(* gain left) (* gain right)]) (mono :id :held :legato true :fx (mute))");
  advance(0.03);
  assert.equal(peak(advance(0.02)), 0, "Muted sampler route still played dry audio");
  const wetRoute = number("(get (routing/current-route :held) :number)");
  evaluate("(mono :id :held :legato true)");
  const dryRoute = number("(get (routing/current-route :held) :number)");
  assert.notEqual(dryRoute, wetRoute);
  assert.ok(peak(advance(0.05)) > 0.04, "Removing FX did not restore sample audio");
  assert.equal(native(`panaeolusRouteCounts[${dryRoute}]`), 0);
  clean();

  evaluate(`(def plan (p/then
      (p/for-beats 0.2 (p/pattern mono :every [0.05] :amp [0.2 0.4] :dur 0.02
        :start [0 0.25 0.5 0.75] :end [0.25 0.5 0.75 1]))
      (p/for-beats 0.2 (p/pattern stereo :every [0.1] :legato true :pan [0 1]))
      (p/for-beats 0.2 (p/pattern mono :every [0.05] :dur 0.02
        :start [0 0.5] :end [0.25 0.75 0.3 0.8]))))`);
  check("(empty? (get @loop/state :names))");
  assert.equal(peak(advance(0.02)), 0, "Building a sampler composition made sound");
  evaluate("(plan :id :sampler-song)");
  assert.ok(peak(advance(0.35)) > 0.03, "Sampler composition did not play");
  check("(empty? (get @loop/state :names))");
  assert.equal(native("sumarray(panaeolusHeldInstruments)"), 0, "Finite composition left a held sample alive");
  clean();

  for (const form of ["(fx/bbcut :bpm 0)", "(fx/bbcut :subdiv 2.5)", "(fx/bbcut :chance 2)",
    "(fx/bbcut :repeats -1)", "(fx/bbcut :envelope 0.5)"]) evaluate(form, -1);
  evaluate("(stereo :id :break :every [1] :legato true :fx (fx/bbcut :bpm [120 180] :subdiv 8 :repeats 2 :stutter 4 :chance 0.5))");
  const cut = advance(0.6);
  assert.ok(peak(cut) > 0.03 && peak(cut) < 1, "bbcuts did not produce usable audio");
  clean();

  evaluate("(p/record!) (mono :id :recorded :legato true :amp 0.5 :cross 0)");
  const chunks = [advance(0.08)];
  evaluate("(mono :id :recorded :legato true :amp 0.8 :rate 0.5 :cross 0)");
  chunks.push(advance(0.08));
  evaluate("(p/finish!) (p/export!)");
  const document = api.csoundGetStringChannel(engine, "__panaeolus_export");
  assert.ok(document.includes("<CsFileB"), "Export did not embed its sample files");
  assert.ok(!document.includes("MalValue") && !document.includes("PanaeolusEval"), "Sample export requires the Lisp runtime");
  const embedded = [...document.matchAll(/<CsFileB\s+filename="([^"]+)">\s*([\s\S]*?)<\/CsFileB>/g)];
  assert.ok(embedded.length > 0, "Export has no readable embedded sample");
  for (const [, , encoded] of embedded) {
    const bytes = Buffer.from(encoded.replace(/\s/g, ""), "base64");
    assert.equal(bytes.toString("ascii", 0, 4), "RIFF");
    assert.equal(bytes.toString("ascii", 8, 12), "WAVE");
    assert.equal(bytes.readUInt16LE(20), 3, "Embedded sample is not an IEEE float WAV");
    assert.equal(bytes.readUInt16LE(34), 64, "Embedded sample lost table precision");
  }
  // A separate Wasm instance has no original virtual files or function tables.
  const replayApi = await libcsound();
  const replay = replayApi.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0"]) replayApi.csoundSetOption(replay, option);
    assert.equal(replayApi.csoundCompileCSD(replay, document), 0, "Embedded sample CSD did not compile in a fresh engine");
    assert.equal(replayApi.csoundStart(replay), 0);
    const live = new Float64Array(chunks.reduce((total, chunk) => total + chunk.length, 0));
    let offset = 0;
    for (const chunk of chunks) { live.set(chunk, offset); offset += chunk.length; }
    const rendered = new Float64Array(live.length);
    for (let block = 0; block < rendered.length / (blockSize * 2); block++) {
      assert.equal(replayApi.csoundPerformKsmps(replay), 0, "Embedded sample CSD ended early");
      rendered.set(new Float64Array(replayApi.getMemory().buffer,
        replayApi.csoundGetSpout(replay), blockSize * 2), block * blockSize * 2);
    }
    assert.deepEqual(rendered, live, "Embedded float sample replay differs from live playback");
    let ended = false;
    for (let block = 0; block < 10; block++) {
      if (replayApi.csoundPerformKsmps(replay) !== 0) { ended = true; break; }
    }
    assert.ok(ended, "Exported held sampler did not end");
  } finally { replayApi.csoundDestroy(replay); }
  clean();
  console.log(`PASS: ${checks} sampler API checks; mono/stereo audio, speed, slicing, direction, held controls, compositions, bbcuts, and exact embedded CSD replay.`);
} finally {
  api.csoundDestroy(engine);
  console.log = writeLog;
}
