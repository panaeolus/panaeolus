import assert from "node:assert/strict";
import { resolve } from "node:path";

// Use the exact engine shipped to the browser, with an offline audio clock.
Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../node_modules/@csound/browser/dist/csound.js");
const api = await libcsound();
const csound = api.csoundCreate();
const root = resolve(import.meta.dir, "..");
const mal = await Bun.file(resolve(root, "dist/make-a-lisp.orc")).text();
const panaeolus = await Bun.file(resolve(root, "dist/panaeolus.orc")).text();
let checks = 0;

function evaluate(source: string, expected = 0) {
  api.csoundSetControlChannel(csound, "__panaeolus_status", -999);
  api.csoundSetStringChannel(csound, "__panaeolus_test", source);
  api.csoundEvalCode(csound, 'return PanaeolusEval(chnget:S("__panaeolus_test"))\n');
  assert.equal(api.csoundGetControlChannel(csound, "__panaeolus_status"), expected, source);
  checks++;
}

function check(form: string) {
  evaluate(`(if ${form} true (throw (str "Failed: " (pr-str '${form}))))`);
}

function advance(seconds: number) {
  for (let n = 0; n < Math.round(seconds * 48000 / 64); n++) {
    assert.equal(api.csoundPerformKsmps(csound), 0, "Engine stopped early");
  }
}

function beat() {
  return api.csoundEvalCode(csound, "return i(panaeolusBeat)");
}

// At 120 BPM whole beats fall exactly on this engine's control blocks.
function advanceToBeat(target: number) {
  advance((target - beat()) / 2);
  assert.ok(Math.abs(beat() - target) < 1e-9, "Clock missed the requested beat");
}

try {
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(csound, option);
  assert.equal(api.csoundCompileOrc(csound,
    `sr = 48000\nksmps = 64\nnchnls = 2\n0dbfs = 1\n${mal}\n${panaeolus}`), 0);
  assert.equal(api.csoundStart(csound), 0);
  const coreTests = Bun.file(resolve(root, "tests/core.mal"));
  if (await coreTests.exists()) evaluate(await coreTests.text());
  evaluate(await Bun.file(resolve(root, "tests/instruments.mal")).text());
  advance(2.3);
  check("(= 13 (count @played))");
  check("(= [440 550 660 440 550 660 440 550 660 440 550 660 440] (mapv (fn [e] (nth e 4)) @played))");
  check("(= [0.01 0.02 0.01 0.02 0.01 0.02 0.01 0.02 0.01 0.02 0.01 0.02 0.01] (mapv (fn [e] (nth e 5)) @played))");
  check("(= [0.1 0.2 0.1 0.2 0.1 0.2 0.1 0.2 0.1 0.2 0.1 0.2 0.1] (mapv (fn [e] (nth e 3)) @played))");
  advanceToBeat(Math.ceil(beat()) + 1);
  evaluate("(pulse :every [0.25] :hz [770 880]) (reset! played [])");
  advance(0.3);
  check("(= [770 880 770] (mapv (fn [e] (nth e 4)) @played))");
  evaluate("(pulse :every [0.25] :hz [])", -1);
  advance(0.125);
  check("(= [770 880 770 880] (mapv (fn [e] (nth e 4)) @played))");
  check("(p/stop! pulse)");
  advance(0.3);
  check("(= 4 (count @played))");
  advanceToBeat(Math.ceil(beat()) + 1);
  evaluate("(reset! played []) (soft :every [0.25]) (pulse :every [0.5] :id :second)");
  advance(0.6);
  check("(= 5 (count (filter (fn [e] (= 220 (nth e 4))) @played)))");
  check("(= 3 (count (filter (fn [e] (= 440 (nth e 4))) @played)))");
  check("(p/stop! soft)");
  check("(p/stop! :second)");
  // Functions created by a pattern must not accumulate on each event.
  evaluate("(reset! played []) (pulse :every [0.25] :hz [440 550 660])");
  advance(0.5);
  const patternFunctions = api.csoundEvalCode(csound, "return malFunctionCount");
  const patternEnvironments = api.csoundEvalCode(csound, "return malEnvCount");
  advance(1);
  assert.equal(api.csoundEvalCode(csound, "return malFunctionCount"), patternFunctions);
  assert.equal(api.csoundEvalCode(csound, "return malEnvCount"), patternEnvironments);
  evaluate("(p/stop-all!) (reset! played [])");
  advanceToBeat(Math.ceil(beat() / 2) * 2 + 2);
  evaluate("(pulse :id :a :every [0.25 1.75] :hz [11 12])");
  advance(0.35);
  check("(= [11 12] (mapv (fn [e] (nth e 4)) @played))");
  // Define B later, but place it at beat 1 of A's same two-beat grid.
  evaluate("(def later (p/with-defaults pulse :id :b :offset 1)) (later :every [0.25 1.75] :hz [21 22])");
  advance(0.4);
  check("(= [11 12 21 22] (mapv (fn [e] (nth e 4)) @played))");
  evaluate("(later :every [0.25 1.75] :hz [31 32])");
  advance(0.5);
  check("(= [11 12 21 22 11 12] (mapv (fn [e] (nth e 4)) @played))");
  advance(0.5);
  check("(= [11 12 21 22 11 12 31 32] (mapv (fn [e] (nth e 4)) @played))");
  evaluate("(later :every [0.25 1.75] :offset 0 :hz [41 42]) (p/stop! later)");
  advance(0.5);
  check("(= [11 12 21 22 11 12 31 32 11 12] (mapv (fn [e] (nth e 4)) @played))");
  evaluate("(p/stop-all!) (ns csound) (def event test.instruments/real-event) (ns user)");
  evaluate("(ns test.live (:require [panaeolus.event-loop :as loop])) (def hits (atom []))");
  evaluate("(loop/tempo! 120) (loop/start! :pulse [0.25 0.25 0.25 0.25] (fn [e] (swap! hits conj e)))");
  advance(0.6);
  check("(= 5 (count @hits))");
  check("(= [0 1 2 3 0] (mapv (fn [e] (get e :index)) @hits))");
  check("(= [0 0 0 0 1] (mapv (fn [e] (get e :cycle)) @hits))");
  check("(every? (fn [e] (= :pulse (get e :id))) @hits)");
  check("(every? (fn [e] (= 0.25 (get e :duration))) @hits)");
  check("(= 1 (- (get (last @hits) :beat) (get (first @hits) :beat)))");
  evaluate("(loop/stop! :pulse) (def stopped (count @hits))");
  advance(0.3);
  check("(= stopped (count @hits))");

  // The fourth third lands exactly one beat after the first, every cycle.
  advanceToBeat(Math.ceil(beat()) + 1);
  evaluate("(reset! hits []) (def start-beat (loop/now)) (loop/start! :thirds [1/3 1/3 1/3] (fn [e] (swap! hits conj e)) :offset 0)");
  advance(5.1);
  check("(= 31 (count @hits))");
  check("(= 1 (- (get (nth @hits 3) :beat) start-beat))");
  check("(= 10 (- (get (nth @hits 30) :beat) start-beat))");
  check("(every? (fn [e] (= (get e :cycle) (- (get e :beat) start-beat))) (filter (fn [e] (= 0 (get e :index))) @hits))");
  // Exercise the same native target calculation at a distant cycle boundary.
  evaluate('(def third-slot (get (get @loop/state :names) :thirds))');
  check('(= (+ start-beat 1000000) (csound/eval (str "return PanaeolusTarget(" third-slot ",0,1000000)")))');
  evaluate("(loop/stop-all!)");
  advanceToBeat(Math.ceil(beat() / 2) * 2 + 2);
  evaluate("(reset! hits []) (def start-beat (loop/now))");
  advance(0.35);
  evaluate("(loop/start! :phase [0.25 1.75] (fn [e] (swap! hits conj e)) :offset 5)");
  advance(0.4);
  check("(= [1 1.25] (mapv (fn [e] (- (get e :beat) start-beat)) @hits))");
  // Tempo changes alter the wall time, not the chosen target beat.
  evaluate("(loop/tempo! 240)");
  advance(0.4);
  check("(= [1 1.25 3] (mapv (fn [e] (- (get e :beat) start-beat)) @hits))");
  evaluate("(loop/stop-all!) (loop/tempo! 120)");

  // Three notes and four durations must each keep their own phase.
  evaluate(`(def notes [540 550 7550]) (reset! hits [])
    (loop/start! :polyrhythm [0.25 0.25 0.5 0.5]
      (fn [e] (swap! hits conj
        (assoc e :note (nth notes (mod (get e :step) (count notes)))))))`);
  advance(2.3);
  check("(= 13 (count @hits))");
  check("(= [0 1 2 3 4 5 6 7 8 9 10 11 12] (mapv (fn [e] (get e :step)) @hits))");
  check("(= [0 1 2 3 0 1 2 3 0 1 2 3 0] (mapv (fn [e] (get e :index)) @hits))");
  check("(= [540 550 7550 540 550 7550 540 550 7550 540 550 7550 540] (mapv (fn [e] (get e :note)) @hits))");
  check("(= [0.25 0.25 0.5 0.5 0.25 0.25 0.5 0.5 0.25 0.25 0.5 0.5 0.25] (mapv (fn [e] (get e :duration)) @hits))");
  check(`(every? identity
    (mapv (fn [e expected]
      (let [difference (- (- (get e :beat) (get (first @hits) :beat)) expected)]
        (and (> difference -0.000000001) (< difference 0.000000001))))
      @hits [0 0.25 0.5 1 1.5 1.75 2 2.5 3 3.25 3.5 4 4.5]))`);
  // Live note edits use the current vector length without restarting the loop.
  evaluate("(def notes [10 20 30 40 50])");
  advance(0.6);
  check("(= [13 14 15] (mapv (fn [e] (get e :step)) (drop 13 @hits)))");
  check("(= [40 50 10] (mapv (fn [e] (get e :note)) (drop 13 @hits)))");
  evaluate("(loop/stop! :polyrhythm)");

  evaluate("(reset! hits []) (loop/start! :pulse [1] (fn [e] (swap! hits conj :old)))");
  advance(0.02);
  evaluate("(loop/start! :pulse [0.25] (fn [e] (swap! hits conj :new)))");
  advance(0.6);
  check("(= 1 (count (filter (fn [x] (= :old x)) @hits)))");
  check("(= 5 (count (filter (fn [x] (= :new x)) @hits)))");
  evaluate("(loop/stop-all!)");

  evaluate("(reset! hits []) (loop/start! :tempo [1] (fn [e] (swap! hits conj e)))");
  advance(0.2);
  check("(= 1 (count @hits))");
  evaluate("(loop/tempo! 240)");
  advance(0.2);
  check("(= 2 (count @hits))");
  evaluate("(loop/stop-all!) (loop/tempo! 120)");

  // A failing callback must not stop another loop or break later evaluation.
  evaluate('(def good (atom 0)) (def bad (atom 0)) (loop/start! :bad [0.25] (fn [e] (swap! bad inc) (throw "expected test error"))) (loop/start! :good [0.25] (fn [e] (swap! good inc)))');
  advance(0.4);
  check("(and (= 1 @bad) (= 4 @good))");
  evaluate("(loop/stop-all!)");
  // Independent held instruments must not tie together when started at once.
  evaluate("(def a (atom 0)) (def b (atom 0)) (loop/start! :a [0.25] (fn [e] (swap! a inc))) (loop/start! :b [0.5] (fn [e] (swap! b inc)))");
  advance(0.6);
  check("(and (= 5 @a) (= 3 @b))");
  evaluate("(loop/stop-all!)");
  evaluate("(def self-hits (atom 0)) (loop/start! :self [0.25] (fn [e] (swap! self-hits inc) (loop/stop! :self)))");
  advance(0.4);
  check("(= 1 @self-hits)");
  evaluate("(def old-hits (atom 0)) (def new-hits (atom 0)) (defn next-callback [e] (swap! new-hits inc)) (loop/start! :self [1] (fn [e] (swap! old-hits inc) (loop/start! :self [0.25] next-callback)))");
  advance(0.4);
  check("(and (= 1 @old-hits) (= 4 @new-hits))");
  evaluate("(loop/stop-all!)");
  evaluate("(def silent (fn [e] nil)) (loop/start! :memory [0.25] silent)");
  advance(0.5);
  const functionCount = api.csoundEvalCode(csound, "return malFunctionCount");
  const environmentCount = api.csoundEvalCode(csound, "return malEnvCount");
  advance(2);
  assert.equal(api.csoundEvalCode(csound, "return malFunctionCount"), functionCount, "Function closures grow per tick");
  assert.equal(api.csoundEvalCode(csound, "return malEnvCount"), environmentCount, "Environment storage grows per tick");
  evaluate("(loop/stop-all!)");
  evaluate('(def literal "hélló $MACRO {{ }} \\\"quoted\\\"")');
  evaluate(") (reset! good 99)", -1);
  check("(= 4 @good)");

  // Load the actual playground file and verify its notes reach the audio output.
  evaluate(await Bun.file(resolve(root, "playground/playground.mal")).text());
  let peak = 0;
  const dispatchTimes: number[] = [];
  for (let n = 0; n < 1800; n++) {
    const started = performance.now();
    assert.equal(api.csoundPerformKsmps(csound), 0);
    const elapsed = performance.now() - started;
    if (elapsed > 1) dispatchTimes.push(elapsed);
    const pointer = api.csoundGetSpout(csound);
    const samples = new Float64Array(api.getMemory().buffer, pointer, 64 * 2);
    for (const sample of samples) {
      assert.ok(Number.isFinite(sample), "Non-finite audio sample");
      peak = Math.max(peak, Math.abs(sample));
    }
  }
  assert.ok(peak > 0, "Playground did not produce audio");
  assert.ok(peak <= 1, `Playground clips: ${peak}`);
  evaluate("(panaeolus.event-loop/stop-all!)");
  console.log(`PASS: ${checks} WASM checks; instruments, timing, wrapping, stop, replacement, tempo, parallel loops, callback edits, memory, errors, and audio (peak ${peak.toFixed(4)}).`);
  console.log(`Audio blocks over 1 ms: ${dispatchTimes.map(ms => ms.toFixed(1)).join(", ")} ms.`);
} finally {
  api.csoundDestroy(csound);
}
