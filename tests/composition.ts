import assert from "node:assert/strict";
import { resolve } from "node:path";

Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const api = await libcsound();
const csound = api.csoundCreate();
const root = resolve(import.meta.dir, "..");
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

function advance(beats: number, bpm = 120) {
  let peak = 0;
  for (let n = 0; n < Math.round(beats * 60 / bpm * 48000 / 64); n++) {
    assert.equal(api.csoundPerformKsmps(csound), 0, "Engine stopped early");
    for (const sample of new Float64Array(api.getMemory().buffer, api.csoundGetSpout(csound), 128)) {
      assert.ok(Number.isFinite(sample), "Composition produced non-finite audio");
      peak = Math.max(peak, Math.abs(sample));
    }
  }
  return peak;
}

function native(expression: string) {
  return api.csoundEvalCode(csound, `return ${expression}`);
}

function number(form: string) {
  evaluate(`(def __test_number ${form})`);
  return api.csoundEvalCode(csound,
    'value:MalValue init MalResolveSymbol(panaeolusEnv, "test.composition/__test_number")\nreturn value.number');
}

try {
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(csound, option);
  const mal = await Bun.file(resolve(root, "dist/make-a-lisp.orc")).text();
  const panaeolus = await Bun.file(resolve(root, "dist/panaeolus.orc")).text();
  assert.equal(api.csoundCompileOrc(csound,
    `sr = 48000\nksmps = 64\nnchnls = 2\n0dbfs = 1\n${mal}\n${panaeolus}`), 0);
  assert.equal(api.csoundStart(csound), 0);
  evaluate(`(ns test.composition
    (:require [panaeolus.instrument :as p]
              [panaeolus.composition :as composition]
              [panaeolus.event-loop :as loop]
              [panaeolus.fx :as fx]))
    (def played (atom []))
    (defn record-event [& args] (swap! played conj (vec args)) nil)
    (def real-event csound/event)
    (p/definst pulse [hz 440] (csound/poscil 0.01 hz))
    (ns csound) (def event test.composition/record-event) (ns test.composition)
    (defn notes [] (mapv (fn [event] (nth event 4)) @played))
    (def a (p/pattern pulse :every [0.25] :hz [110 220]))
    (def b (p/pattern pulse :every [0.25] :hz [330 440 550]))
    (def effect-count (get @panaeolus.routing/definitions :effect-count))
    (def wet (p/pattern pulse :every [0.25] :fx (fx/flanger :rate [1 2])))
    (def song (apply p/then (map (partial p/for-beats 1) [a b])))`);
  check("(and (fn? song) (empty? @played) (empty? (get @loop/state :names)))");
  check("(= effect-count (get @panaeolus.routing/definitions :effect-count))");
  check("(= 2 (get (composition/descriptor song) :span))");
  evaluate("(p/pattern pulse :hz 123)", -1);
  evaluate("(p/pattern pulse :every [1] :hz [])", -1);
  evaluate("(p/then a b)", -1);
  evaluate("(p/for-beats 0 a)", -1);
  evaluate("(p/repeat-times 1.5 (p/for-beats 1 a))", -1);
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");

  // Start off the global grid; both segments still use the same local origin.
  advance(0.376);
  evaluate("(song :id :song)");
  advance(2.1);
  check("(= [110 220 110 220 330 440 550 330] (notes))");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");

  // Parallel phrases may reach the same beat through different sums. Their
  // shared boundary must launch both children and still end the parent.
  evaluate(`(reset! played [])
    (def decimal-a (p/pattern pulse :every [1] :hz 110))
    (def decimal-b (p/pattern pulse :every [1] :hz 220))
    (def decimal-c (p/pattern pulse :every [1] :hz 330))
    (def decimal-left (p/then (p/for-beats 0.1 decimal-a)
      (p/for-beats 0.2 decimal-b) (p/for-beats 0.1 decimal-c)))
    (def decimal-right (p/then (p/for-beats 0.3 decimal-a)
      (p/for-beats 0.1 decimal-c)))
    ((p/together decimal-left decimal-right) :id :decimal)`);
  advance(0.5);
  check("(= [110 110 220 330 330] (notes))");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");
  check("(get (second (composition/actions-for [0 0.3] -1 [] (+ 0.1 0.2))) :finish)");
  evaluate(`(reset! played [])
    ((p/for-beats (+ 0.1 0.2) (p/then (p/for-beats 0.3 decimal-a)
      (p/for-beats 0.1 decimal-b))) :id :decimal-cut)`);
  advance(0.4);
  check("(= [110] (notes))");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");
  // Coalescing roundoff must not hide unsupported, genuinely distinct gaps.
  evaluate(`((p/together decimal-right
    (p/then (p/for-beats 0.3000001 decimal-a) (p/for-beats 0.1 decimal-b))))`, -1);
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");
  check("(not (composition/same-beat? 999999999 999999999.000001))");

  evaluate("(reset! played []) (a :id :reusable)");
  advance(2.1);
  check("(= [110 220 110 220 110 220 110 220 110] (notes))");
  check("(= 2 (count (get @loop/state :names)))");
  evaluate("(b :id :reusable) (reset! played [])");
  advance(0.8);
  check("(= [330 440 550 330] (notes))");
  check("(= 2 (count (get @loop/state :names)))");
  evaluate("(song :unknown 2)", -1);
  advance(0.25);
  check("(= [330 440 550 330 440] (notes))");
  check("(p/stop! :reusable)");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");

  // A callable plan and a direct instrument loop share the instrument's name.
  // Replacing either form must stop every child from the old form.
  evaluate("(reset! played []) (a)");
  advance(0.1);
  evaluate("(pulse :every [0.25] :hz 880) (reset! played [])");
  advance(0.6);
  check("(every? (fn [hz] (= hz 880)) (notes))");
  check("(= 1 (count (get @loop/state :names)))");
  evaluate("(a) (reset! played [])");
  advance(0.6);
  check("(= [110 220 110] (notes))");
  check("(= 2 (count (get @loop/state :names)))");
  check("(p/stop! pulse)");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");

  evaluate(`(reset! played [])
    ((p/repeat-times 2 (p/for-beats 0.75 b)) :id :repeat)`);
  advance(1.6);
  check("(= [330 440 550 330 440 550] (notes))");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");

  evaluate(`(reset! played [])
    ((p/for-beats 1 (p/pattern pulse :every [0.25 0.25 0.25 0.25]
                       :hz [660 770] :offset 0.5)) :id :offset)`);
  advance(1.1);
  check("(= [660 770] (notes))");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");

  // A short child ends while an indefinite sibling continues. Stopping the
  // parent still owns the sibling after the conductor has parked.
  evaluate(`(reset! played [])
    (def pair (p/together (p/for-beats 0.5 a) b)) (pair)`);
  advance(1.1);
  check("(= 2 (count (filter (fn [hz] (< hz 300)) (notes))))");
  check("(= 5 (count (filter (fn [hz] (> hz 300)) (notes))))");
  check("(= 2 (count (get @loop/state :names)))");
  check("(p/stop! pair)");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");

  evaluate("(reset! played []) ((p/for-beats 1.25 song) :id :trim)");
  advance(1.4);
  check("(= [110 220 110 220 330] (notes))");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");

  // Tempo changes move wall time, while segment boundaries stay in beats.
  evaluate("(reset! played []) (song :id :tempo)");
  advance(0.5);
  evaluate("(p/tempo! 240)");
  advance(1.7, 240);
  check("(= [110 220 110 220 330 440 550 330] (notes))");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");
  evaluate("(p/tempo! 120) (a :id :stop-all) (b :id :also-stop)");
  advance(0.1);
  evaluate("(p/stop-all!)");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");

  // A removal hook may stop its parent during a boundary. The old conductor
  // must not resume and launch the next section after that stop.
  evaluate(`(reset! played []) (def stopped-by-hook (atom false))
    (reset! loop/on-remove (fn [entry]
      (when (and (not @stopped-by-hook) (not= :hooked (get entry :id)))
        (reset! stopped-by-hook true) (p/stop! :hooked))))
    (song :id :hooked)`);
  advance(1.2);
  evaluate("(reset! loop/on-remove nil)");
  check("@stopped-by-hook");
  check("(= [110 220 110 220] (notes))");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");

  // Direct edits commit before old removal hooks run. A hook failure must
  // leave the new plan's playback records intact so it can keep playing.
  evaluate("(a :id :throwing)");
  advance(0.1);
  evaluate(`(def hook-failed (atom false))
    (reset! loop/on-remove (fn [entry]
      (when-not @hook-failed (reset! hook-failed true) (throw "expected hook error"))))`);
  evaluate("(b :id :throwing)", -1);
  evaluate("(reset! loop/on-remove nil) (reset! played [])");
  advance(0.3);
  check("(= [330 440] (notes))");
  evaluate("(p/stop! :throwing)");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");

  // Two full sections meet on the same beat. The conductor runs before its
  // children's end dispatchers, so it must release their slots itself.
  // Reserve the unrelated registry slots without creating 125 idle waiters;
  // this test exercises section replacement, not interpreter heap capacity.
  api.csoundEvalCode(csound, `
    stateAtom:MalValue init MalResolveSymbol(panaeolusEnv, "panaeolus.event-loop/state")
    state:MalValue init MalAtomValue(stateAtom)
    names:MalValue init MalMapGet(state, MalMkKeyword("names"))
    slots:MalValue init MalMapGet(state, MalMkKeyword("slots"))
    for index in [0 ... 124] do
      names init MalMapAssoc(names, MalMkString(sprintf("outside-%d", index)), MalMkNumber(index))
      slots init MalMapAssoc(slots, MalMkNumber(index), MalMkNumber(1))
    od
    state init MalMapAssoc(state, MalMkKeyword("names"), names)
    state init MalMapAssoc(state, MalMkKeyword("slots"), slots)
    stored:MalValue init MalAtomSetValue(stateAtom, state)
    return 0`);
  evaluate(`(def section (p/for-beats 0.5 (p/together a b)))
    (reset! played []) ((p/then section section) :id :full)`);
  evaluate("((p/for-beats 0.5 (p/together a b a)) :id :full)", -1);
  advance(1.1);
  check("(= 8 (count @played))");
  check("(= 125 (count (get @loop/state :names)))");
  evaluate('(swap! loop/state assoc :names {} :slots {})');

  // Run real held voices through two sections and an effect with a tail.
  evaluate(`(ns csound) (def event test.composition/real-event) (ns test.composition)
    (p/definst other [hz 660] (csound/oscili 0.01 hz))
    (fx/deffx gain [amount 0.5] [left right] [(* left amount) (* right amount)])
    (def tail-gain (fx/with-tail gain 0.2))
    (def held-a (p/pattern pulse :legato true :every [0.25] :hz [110 220]
                   :fx (tail-gain)))
    (def held-b (p/pattern other :legato true :every [0.25] :hz [660 770]))
    (def held-song (p/then (p/for-beats 1 held-a) (p/for-beats 1 held-b)))
    (held-song :id :held-song)
    (defn child-route [index]
      (let [slot (get (get @loop/state :names) :held-song)
            entry (get (get @loop/state :slots) slot)
            id (nth (get (meta (get entry :callback)) :panaeolus/children) index)]
        (get (panaeolus.routing/current-route id) :number)))`);
  assert.ok(advance(0.5) > 0, "Held composition did not produce audio");
  const firstHeldRoute = number("(child-route 0)");
  const firstHeldVoice = native(`panaeolusHeldInstruments[${firstHeldRoute}]`);
  assert.equal(native(`active:i(${firstHeldVoice})`), 1);
  assert.ok(advance(0.6) > 0, "Second held section did not produce audio");
  const secondHeldRoute = number("(child-route 1)");
  const secondHeldVoice = native(`panaeolusHeldInstruments[${secondHeldRoute}]`);
  assert.equal(native(`active:i(${firstHeldVoice})`), 0, "First section left its held voice running");
  assert.equal(native(`active:i(${secondHeldVoice})`), 1);
  assert.equal(native(`panaeolusRouteActive[${firstHeldRoute}]`), 2, "FX tail ended at the section boundary");
  advance(1);
  assert.equal(native(`active:i(${secondHeldVoice})`), 0, "Final section left its held voice running");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");
  assert.equal(advance(0.1), 0, "Finished composition left held audio running");

  evaluate("(held-song :id :held-song)");
  assert.ok(advance(0.2) > 0);
  evaluate("(p/stop! :held-song)");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");
  advance(0.5);
  assert.equal(native("sumarray(panaeolusHeldInstruments)"), 0, "Parent stop left a held child running");
  assert.equal(advance(0.1), 0, "Parent stop left audio after FX tails ended");

  // Once prepared, section changes and end deadlines must not compile bridge
  // code on the render thread. The native conductor owns those operations.
  evaluate(`(def saved-eval csound/eval)
    (def blocked-evals (atom 0))
    (defn reject-eval [& args]
      (swap! blocked-evals inc) (throw "render-time csound/eval"))
    (held-song :id :without-eval)
    (ns csound) (def eval test.composition/reject-eval) (ns test.composition)`);
  const withoutEvalPeak = advance(2.2);
  evaluate("(ns csound) (def eval test.composition/saved-eval) (ns test.composition)");
  check("(= 0 @blocked-evals)");
  check("(and (empty? (get @loop/state :names)) (empty? @composition/performances) (empty? @composition/playbacks))");
  assert.ok(withoutEvalPeak > 0, "Native conductor did not play without csound/eval");
  assert.equal(native("sumarray(panaeolusHeldInstruments)"), 0);
  assert.equal(advance(0.1), 0);
  console.log(`PASS: ${checks} composition checks; silent higher-order plans, sequence, parallel, repeat, offsets, clipping, tempo, replacement, and owned stop.`);
} finally {
  api.csoundDestroy(csound);
}
