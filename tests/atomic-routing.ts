import assert from "node:assert/strict";
import { resolve } from "node:path";

// Route cleanup must finish even when a later user hook never returns.
Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const api = await libcsound();
const engine = api.csoundCreate();
const root = resolve(import.meta.dir, "..");

function native(code: string) {
  return api.csoundEvalCode(engine, code);
}

function evaluate(source: string) {
  api.csoundSetStringChannel(engine, "__test_source", `${source}\nnil`);
  native('return PanaeolusEval(chnget:S("__test_source"))\n');
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), 0, source);
}

function value(name: string, atom = false) {
  return native(`value:MalValue init MalResolveSymbol(panaeolusEnv, "commit.test/${name}")
    ${atom ? "value init MalAtomValue(value)" : ""}
    return value.number`);
}

function runUntilHook(source: string, expectedCalls: number) {
  api.csoundSetStringChannel(engine, "__test_source", source);
  native('return PanaeolusEvalStart(chnget:S("__test_source"))\n');
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), 1);
  for (let block = 0; block < 30000; block++) {
    assert.equal(api.csoundPerformKsmps(engine), 0, "Audio engine stopped during the edit");
    if (value("hook-calls", true) === expectedCalls) return;
    assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), 1,
      "Edit returned before reaching its removal hook");
  }
  assert.fail("Edit did not reach its removal hook");
}

function cancelJob() {
  native(`job:i = i(panaeolusEvalJob)
    MalEvalJobCancel(job)
    panaeolusEvalJob init -1
    chnset 0, "__panaeolus_status"
    return 0`);
}

try {
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
  const mal = await Bun.file(resolve(root, "dist/make-a-lisp.orc")).text();
  const runtime = await Bun.file(resolve(root, "dist/panaeolus.orc")).text();
  assert.equal(api.csoundCompileOrc(engine,
    `sr = 48000\nksmps = 64\nnchnls = 2\n0dbfs = 1\n${mal}\n${runtime}`), 0);
  assert.equal(api.csoundStart(engine), 0);
  evaluate(`(ns commit.test (:require [panaeolus.instrument :as p]
    [panaeolus.fx :as fx] [panaeolus.routing :as routing] [panaeolus.event-loop :as loop]))
    (p/definst flat [amp 0.1] (csound/linseg amp 1 amp))
    (fx/deffx gain [amount 1] [left right] [(* left amount) (* right amount)])
    (def gain (fx/with-tail gain 60))
    (def hook-calls (atom 0))
    (defn wait-forever [] (wait-forever))
    (reset! loop/on-remove (fn [entry] (do (swap! hook-calls inc) (wait-forever))))
    (flat :id :line :every [1] :dur 0.1 :fx (gain 0.25))
    (def wet-route (get (routing/current-route :line) :number))`);
  const wet = value("wet-route");

  // A control edit keeps the same processors and does not call the hook.
  evaluate("(flat :id :line :every [1] :dur 0.1 :fx (gain 0.5))");
  assert.equal(native(`return panaeolusRouteActive[${wet}]`), 1);
  assert.equal(value("hook-calls", true), 0);

  runUntilHook("(flat :id :line :every [1] :dur 0.1)", 1);
  assert.equal(native(`return panaeolusRouteActive[${wet}]`), 2,
    "Replacing the loop left its old FX active while the removal hook waited");
  const deadline = native(`return panaeolusRouteEnds[${wet}]`);
  assert.ok(deadline > 0, "Removing the route did not set its tail deadline");
  cancelJob();
  assert.equal(native(`return panaeolusRouteEnds[${wet}]`), deadline,
    "Cancelling the remaining edit changed the FX tail deadline");

  evaluate("(def dry-route (get (routing/current-route :line) :number))");
  const dry = value("dry-route");
  assert.notEqual(dry, wet);
  assert.equal(native(`return panaeolusRouteActive[${dry}]`), 1);
  runUntilHook("(p/stop! :line)", 2);
  assert.notEqual(native(`return panaeolusRouteActive[${dry}]`), 1,
    "Stopping the loop left its route active while the removal hook waited");
  cancelJob();
  evaluate('(if (empty? (get @loop/state :names)) true (throw "Stopped loop remains registered"))');
  console.log("PASS: native commits close removed routes, preserve reused routes, and keep user hooks cancellable.");
} finally {
  api.csoundStop(engine);
  api.csoundDestroy(engine);
}
