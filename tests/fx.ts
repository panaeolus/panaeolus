import assert from "node:assert/strict";
import { resolve } from "node:path";

// Exercise the shipped engine and the public Lisp API, including real audio.
Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const api = await libcsound();
const engine = api.csoundCreate();
const root = resolve(import.meta.dir, "..");
const blockSize = 64;
const sampleRate = 48000;
let checks = 0;

function evaluate(source: string, expected = 0) {
  api.csoundSetControlChannel(engine, "__panaeolus_status", -999);
  api.csoundSetStringChannel(engine, "__panaeolus_test", `${source}\nnil`);
  api.csoundEvalCode(engine, 'return PanaeolusEval(chnget:S("__panaeolus_test"))\n');
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), expected, source);
  checks++;
}

function check(form: string) {
  evaluate(`(if ${form} true (throw (str "Failed: " (pr-str '${form}))))`);
}

function native(expression: string) {
  return api.csoundEvalCode(engine, `return ${expression}`);
}

function number(form: string) {
  evaluate(`(def __test_number ${form})`);
  return api.csoundEvalCode(engine,
    'value:MalValue init MalResolveSymbol(panaeolusEnv, "test.fx/__test_number")\nreturn value.number');
}

function samples(target = engine) {
  return new Float64Array(api.getMemory().buffer, api.csoundGetSpout(target), blockSize * 2);
}

function advance(seconds: number) {
  const result = new Float64Array(Math.round(seconds * sampleRate / blockSize) * blockSize * 2);
  for (let block = 0; block < result.length / (blockSize * 2); block++) {
    assert.equal(api.csoundPerformKsmps(engine), 0, "Live engine stopped early");
    result.set(samples(), block * blockSize * 2);
  }
  for (const sample of result) assert.ok(Number.isFinite(sample), "Non-finite live audio");
  return result;
}

function peak(audio: Float64Array) {
  let result = 0;
  for (const sample of audio) result = Math.max(result, Math.abs(sample));
  return result;
}

function mean(audio: Float64Array, side: number) {
  let sum = 0;
  for (let sample = side; sample < audio.length; sample += 2) sum += audio[sample];
  return sum / (audio.length / 2);
}

function near(actual: number, expected: number, message: string, tolerance = 1e-8) {
  assert.ok(Math.abs(actual - expected) <= tolerance, `${message}: ${actual} != ${expected}`);
  checks++;
}

function align(period = 1) {
  const beat = native("i(panaeolusBeat)");
  advance(((Math.ceil(beat / period) + 1) * period - beat) / 2);
}

function clean() {
  evaluate("(p/stop-all!)");
  advance(4.3);
  const active = api.csoundEvalCode(engine,
    "active:i = 0\nfor route in [1 ... 255] do\nactive += panaeolusRouteActive[route]\nod\nreturn active");
  assert.equal(active, 0, "Routes remained active after notes and tails ended");
  assert.ok(peak(advance(0.02)) < 1e-12, "Audio remained after all notes and tails ended");
  checks++;
}

async function verifyExport(chunks: Float64Array[], name: string) {
  const document = api.csoundGetStringChannel(engine, "__panaeolus_export");
  assert.ok(document.includes("<CsScore>"));
  assert.ok(document.includes("PanaeolusFlanger"));
  assert.ok(!document.includes("MalValue") && !document.includes("PanaeolusEval"), "Export still depends on make-a-lisp");
  assert.ok(document.includes("test.fx/tone"), "Export lost the voice identity");
  assert.ok(document.includes("; route "), "Export lost its route identities");
  await Bun.write(resolve(root, `dist/${name}.csd`), document);
  const replay = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0"]) api.csoundSetOption(replay, option);
    assert.equal(api.csoundCompileCSD(replay, document), 0, "Standalone CSD failed to compile");
    assert.equal(api.csoundStart(replay), 0);
    const live = new Float64Array(chunks.reduce((total, chunk) => total + chunk.length, 0));
    let offset = 0;
    for (const chunk of chunks) { live.set(chunk, offset); offset += chunk.length; }
    const recorded = new Float64Array(live.length + blockSize * 4);
    for (let block = 0; block < recorded.length / (blockSize * 2); block++) {
      assert.equal(api.csoundPerformKsmps(replay), 0, "Replay ended before its recorded notes");
      recorded.set(samples(replay), block * blockSize * 2);
    }
    assert.ok(peak(recorded) > 0 && peak(recorded) < 1, "Export has no usable audio");
    for (const value of recorded) assert.ok(Number.isFinite(value), "Export contains non-finite audio");
    // Compare the exact samples. Search nearby block offsets only to give a
    // useful diagnostic if score scheduling ever shifts from live scheduling.
    let smallestError = Infinity;
    let chosenShift = 0;
    for (let shift = -blockSize * 4; shift <= blockSize * 4; shift += blockSize * 2) {
      let error = 0;
      let energy = 0;
      for (let sample = blockSize * 4; sample < live.length - blockSize * 4; sample++) {
        const difference = live[sample] - recorded[sample + shift];
        error += difference * difference;
        energy += live[sample] * live[sample];
      }
      const normalized = Math.sqrt(error / energy);
      if (normalized < smallestError) { smallestError = normalized; chosenShift = shift / 2; }
    }
    assert.equal(chosenShift, 0, `Export shifted ${chosenShift} samples from live audio`);
    assert.equal(smallestError, 0, `Export differs from live audio: relative RMS ${smallestError}`);
    let largestDifference = 0;
    for (let index = 0; index < live.length; index++) {
      largestDifference = Math.max(largestDifference, Math.abs(live[index] - recorded[index]));
    }
    assert.equal(largestDifference, 0, "Export changed samples at the capture boundaries");
    checks++;
    let replayBlocks = recorded.length / (blockSize * 2);
    let replayEnded = false;
    while (replayBlocks++ < sampleRate * 8 / blockSize) {
      if (api.csoundPerformKsmps(replay) !== 0) { replayEnded = true; break; }
    }
    assert.ok(replayEnded, "Export never reached its score end");
    console.log(`${name}: standalone CSD replay relative RMS error ${smallestError.toExponential(2)}, alignment ${chosenShift} samples.`);
  } finally {
    api.csoundDestroy(replay);
  }
}

try {
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
  const mal = await Bun.file(resolve(root, "dist/make-a-lisp.orc")).text();
  const panaeolus = await Bun.file(resolve(root, "dist/panaeolus.orc")).text();
  assert.equal(api.csoundCompileOrc(engine,
    `sr = ${sampleRate}\nksmps = ${blockSize}\nnchnls = 2\n0dbfs = 1\n${mal}\n${panaeolus}`), 0);
  assert.equal(api.csoundStart(engine), 0);
  evaluate(`(ns test.fx (:require [panaeolus.instrument :as p]
    [panaeolus.fx :as fx] [panaeolus.routing :as route] [panaeolus.event-loop :as loop]))
    (p/tempo! 120)
    (p/definst tone [hz 440 amp 0.05]
      (* (csound/oscili amp hz) (csound/linseg 0 0.005 1 (- p3 0.01) 1 0.005 0)))
    (p/definst flat [amp 0.1] (csound/linseg amp 1 amp))
    (fx/deffx gain [amp 1] [left right] [(* left amp) (* right amp)])
    (fx/deffx saturate [] [left right] [(csound/tanh left) (csound/tanh right)])`);

  // Building effect values is silent and does not allocate a processor.
  evaluate("(def before-fx (get @route/definitions :effect-count)) (def flange (fx/flanger :rate [1 2 3]))");
  check("(= before-fx (get @route/definitions :effect-count))");
  check("(empty? (get @loop/state :names))");
  near(peak(advance(0.02)), 0, "Effect construction made sound");

  // Repeating the same definition reuses its immutable Csound instrument.
  evaluate("(def before-voices (get @route/definitions :voice-count)) (def original-flat flat)");
  evaluate("(p/definst flat [amp 0.1] (csound/linseg amp 1 amp))");
  check("(= before-voices (get @route/definitions :voice-count))");
  check("(= (get (p/descriptor original-flat) :compiled) (get (p/descriptor flat) :compiled))");
  evaluate("(p/definst flat [amp 0.2] (csound/linseg amp 1 amp))");
  check("(= (inc before-voices) (get @route/definitions :voice-count))");
  check("(not= (get (p/descriptor original-flat) :compiled) (get (p/descriptor flat) :compiled))");

  align(1.5);
  evaluate("(tone :id :pulse :every [0.25 0.25 0.5 0.5] :fx (fx/flanger :rate [1 2 3] :depth [0.001 0.002]))");
  const routed = number("(get (route/current-route :pulse) :number)");
  const effectCount = number("(get @route/definitions :effect-count)");
  const durations = [0.25, 0.25, 0.5, 0.5];
  let wetPeak = peak(advance(0.02));
  for (let step = 0; step < 9; step++) {
    near(api.csoundGetControlChannel(engine, `panaeolus/r${routed}/f0/p0`), [1, 2, 3][step % 3], "Flanger rate lost its independent cycle");
    near(api.csoundGetControlChannel(engine, `panaeolus/r${routed}/f0/p1`), [0.001, 0.002][step % 2], "Flanger depth lost its independent cycle");
    wetPeak = Math.max(wetPeak, peak(advance(durations[step % 4] / 2)));
  }
  assert.ok(wetPeak > 0 && wetPeak < 1, `Wet audio peak ${wetPeak}`);
  evaluate("(tone :id :pulse :every [0.25] :fx (fx/flanger :rate 7 :fback 0.2))");
  near(number("(get (route/current-route :pulse) :number)"), routed, "Parameter edit restarted the route");
  near(number("(get @route/definitions :effect-count)"), effectCount, "Parameter edit recompiled the effect");
  advance(0.15);
  near(api.csoundGetControlChannel(engine, `panaeolus/r${routed}/f0/p0`), 7, "New controls did not reach the retained effect");

  // Invalid input must leave the playing loop and route intact.
  for (const form of [
    "(fx/flanger :rate [])", "(fx/flanger :shape 1.5)", "(fx/flanger :fback 1)",
    "(fx/flanger :depth -1)", "(fx/flanger :dur 0)", "(fx/flanger :unknown 1)",
    "(tone :id :pulse :every [0.25] :fx [nil])",
    "(tone :id :pulse :every [0.25] :fx [(gain) (gain) (gain) (gain) (gain) (gain) (gain) (gain) (gain)])",
  ]) evaluate(form, -1);
  near(number("(get (route/current-route :pulse) :number)"), routed, "Bad input replaced the playing route");
  near(number("(get @route/definitions :effect-count)"), effectCount, "Bad input compiled effects");
  check("(contains? (get @loop/state :names) :pulse)");

  // The complete new call removes FX. The old path gets no new notes.
  evaluate("(tone :id :pulse :every [0.25])");
  const dry = number("(get (route/current-route :pulse) :number)");
  assert.notEqual(dry, routed);
  assert.equal(native(`panaeolusRouteActive[${routed}]`), 2, "Old effect did not drain");
  assert.equal(native(`panaeolusRouteCounts[${dry}]`), 0, "Dry route retained an effect");
  const oldLastNote = native(`panaeolusRouteLastNote[${routed}]`);
  assert.ok(peak(advance(0.4)) > 0, "Dry replacement made no sound");
  near(native(`panaeolusRouteLastNote[${routed}]`), oldLastNote, "Removed effect received new notes");
  evaluate("(p/record!)", -1);
  clean();

  // The order of a chain changes the result, even for repeated effect types.
  align();
  evaluate("(flat 0.1 :id :ordered :every [1] :dur 0.3 :fx [(gain 4) (saturate)])");
  advance(0.02);
  near(mean(advance(0.05), 0), Math.tanh(0.4), "Gain then saturation");
  evaluate("(p/stop-all!)"); advance(0.4); align();
  evaluate("(flat 0.1 :id :ordered :every [1] :dur 0.3 :fx [(saturate) (gain 4)])");
  advance(0.02);
  near(mean(advance(0.05), 0), Math.tanh(0.1) * 4, "Saturation then gain");
  evaluate("(p/stop-all!)"); advance(0.4); align();
  evaluate("(flat 0.1 :id :ordered :every [1] :dur 0.3 :fx [(gain 0.5) (gain 0.5) (gain 0.5) (gain 0.5) (gain 0.5) (gain 0.5) (gain 0.5) (gain 0.5)])");
  advance(0.02);
  near(mean(advance(0.05), 0), 0.1 / 256, "Eight serial effects");
  clean();

  // An effect definition captures its graph once, including global values.
  evaluate("(def factor 2) (fx/deffx captured [] [left right] [(* left factor) (* right factor)]) (def factor 4)");
  align();
  evaluate("(flat 0.1 :id :frozen :every [1] :dur 0.3 :fx [(gain) (captured)])");
  advance(0.02);
  near(mean(advance(0.05), 0), 0.2, "Effect first used at stage1 reread a changed global");
  clean();

  // Old factories and effect values retain their graph after the name changes,
  // including when they first compile at a different position in a chain.
  evaluate(`(def old-captured captured) (def old-captured-value (captured))
    (fx/deffx captured [] [left right] [(* left factor) (* right factor)])
    (def factor 8)`);
  check("(not= (get (fx/descriptor old-captured) :revision) (get (fx/descriptor captured) :revision))");
  align();
  evaluate("(flat 0.1 :id :old-value :every [1] :dur 0.3 :fx [(gain) (gain) old-captured-value])");
  advance(0.02);
  near(mean(advance(0.05), 0), 0.2, "Old effect value used the redefined graph at a new stage");
  clean();
  align();
  evaluate("(flat 0.1 :id :new-value :every [1] :dur 0.3 :fx [(gain) (gain) (captured)])");
  advance(0.02);
  near(mean(advance(0.05), 0), 0.4, "New effect failed to retain its own definition-time graph");
  clean();
  align();
  evaluate("(flat 0.1 :id :old-factory :every [1] :dur 0.3 :fx (old-captured))");
  advance(0.02);
  near(mean(advance(0.05), 0), 0.2, "Old factory used the redefined graph");
  clean();

  // Shared definitions must not share the audio or controls of separate loops.
  evaluate(`(p/definst left-tone [amp 0.1] (let [signal (csound/linseg amp 1 amp)] [signal (* signal 0)]))
    (p/definst right-tone [amp 0.1] (let [signal (csound/linseg amp 1 amp)] [(* signal 0) signal]))`);
  align();
  evaluate("(left-tone :id :left :every [1] :dur 0.3 :fx (gain 0.2)) (right-tone :id :right :every [1] :dur 0.3 :fx (gain 0.7))");
  check("(not= (get (route/current-route :left) :number) (get (route/current-route :right) :number))");
  advance(0.02);
  const stereo = advance(0.05);
  near(mean(stereo, 0), 0.02, "Left loop leaked or shared controls");
  near(mean(stereo, 1), 0.07, "Right loop leaked or shared controls");
  const functions = native("malFunctionCount");
  const environments = native("malEnvCount");
  advance(0.8);
  near(native("malFunctionCount"), functions, "Functions grew while effects ticked");
  near(native("malEnvCount"), environments, "Environments grew while effects ticked");
  clean();

  // The lower-level loop API shares route cleanup on replacement and errors.
  align();
  evaluate("(tone :id :replace :every [0.25] :fx (gain))");
  const replaced = number("(get (route/current-route :replace) :number)");
  advance(0.02);
  evaluate("(loop/start! :replace [0.25] (fn [event] nil))");
  assert.equal(native(`panaeolusRouteActive[${replaced}]`), 2);
  advance(0.2);
  assert.equal(native(`panaeolusRouteActive[${replaced}]`), 0);
  evaluate("(p/stop-all!)");
  align();
  evaluate("(tone :id :broken :every [0.25] :fx (gain))");
  const broken = number("(get (route/current-route :broken) :number)");
  evaluate(`(def broken-entry (get (get @loop/state :slots) (get (get @loop/state :names) :broken)))
    (loop/start! :broken [0.25] (with-meta (fn [event] (throw "expected callback failure"))
      (meta (get broken-entry :callback))))`);
  advance(0.2);
  check("(not (contains? (get @loop/state :names) :broken))");
  assert.equal(native(`panaeolusRouteActive[${broken}]`), 0, "Callback failure left its route alive");
  clean();

  // A finished take must render with no Lisp runtime and no browser scheduler.
  align();
  evaluate("(p/record!) (tone :id :take :every [0.25] :amp [0.03 0.05] :fx (fx/flanger :rate 2 :shape [1 2 3 4 5] :fback 0.2))");
  const liveA = advance(0.7);
  evaluate("(tone :id :take :every [0.25] :amp 0.04 :fx (fx/flanger :rate 5 :shape [1 2 3 4 5] :fback 0.2))");
  const liveB = advance(0.4);
  evaluate("(tone :id :take :every [0.25] :amp 0.02)");
  const liveC = advance(0.4);
  evaluate("(p/finish!) (p/export!)");
  await verifyExport([liveA, liveB, liveC], "test-take");
  clean();
  // One-shots submit outside the scheduler and must not get its block delay.
  evaluate("(p/record!) (tone :amp 0.05 :fx (fx/flanger :shape 4 :fback 0.2))");
  const single = advance(0.4);
  evaluate("(p/finish!) (p/export!)");
  await verifyExport([single], "test-one-shot");
  clean();
  console.log(`PASS: ${checks} WASM FX checks; identity, controls, ordering, isolation, lifecycle, errors, audio, and standalone export.`);
} finally {
  api.csoundDestroy(engine);
}
