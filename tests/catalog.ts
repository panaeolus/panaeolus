import assert from "node:assert/strict";
import { catalog } from "../tools/catalog-audio";
import { peak } from "../tools/audio-level";
// A fresh engine per voice keeps failures local and checks first-use loading.
const name = process.argv[2];
if (!name) {
  for (const voice of catalog.filter(v => !process.env.CATALOG_CATEGORY || v.category === process.env.CATALOG_CATEGORY)) {
    const child = Bun.spawn([process.execPath, import.meta.path, voice.name], { stdout: "inherit", stderr: "inherit" });
    assert.equal(await child.exited, 0, voice.name);
  }
  process.exit(0);
}
const voice = catalog.find(voice => voice.name === name)!;
const { api } = await import("../tools/catalog-audio");
const engine = api.csoundCreate();
function evaluate(form: string, expected = 0) {
  api.csoundSetStringChannel(engine, "__catalog_test", form + "\nnil");
  api.csoundEvalCode(engine, 'return PanaeolusEval(chnget:S("__catalog_test"))');
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), expected, form);
}
function check(form: string) { evaluate(`(when-not ${form} (throw "catalog assertion failed"))`); }
function advance(seconds: number, target = engine) {
  const audio = new Float64Array(Math.round(seconds * 48000 / 64) * 128);
  for (let offset = 0; offset < audio.length; offset += 128) {
    assert.equal(api.csoundPerformKsmps(target), 0);
    audio.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(target), 128), offset);
  }
  assert.ok(audio.every(Number.isFinite));
  return audio;
}
try {
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
  assert.equal(api.csoundCompileOrc(engine, "sr=48000\nksmps=64\nnchnls=2\n0dbfs=1\n" +
    await Bun.file(new URL("../dist/make-a-lisp.orc", import.meta.url)).text() + "\n" +
    await Bun.file(new URL("../dist/panaeolus.orc", import.meta.url)).text()), 0);
  assert.equal(api.csoundStart(engine), 0);
  evaluate(`(ns test.catalog (:require [${voice.namespace} :refer [${name}]]
    [panaeolus.instrument :as p] [panaeolus.routing :as routing] [panaeolus.fx :as fx]))`);
  check(`(= 0 (get @routing/definitions :voice-count))`);
  evaluate(`(p/stop! ${name})`);
  check(`(= 0 (get @routing/definitions :voice-count))`);
  evaluate(`(def plan (p/pattern ${name} :every [0.25 0.5] :note [48 55 60]))`);
  assert.equal(peak(advance(0.1)), 0, "Constructing a pattern played audio");
  check('(= 1 (get @routing/definitions :voice-count))');
  const controls = Object.keys(voice.ranges);
  const defaults = controls.map((key, index) => `:${key} ${key === "db" ? -6 : voice.reference[index]}`).join(" ");
  check(`(= {${defaults}} (get (p/descriptor ${name}) :defaults))`);
  check(`(= [${controls.map(key => `:${key}`).join(" ")}] (get (p/descriptor ${name}) :params))`);
  evaluate(`(${name})`);
  assert.ok(peak(advance(1.1)) > 0.005, `${name} default is silent`);
  const invalid = Object.entries(voice.ranges).map(([key, bounds]) => `(${name} :${key} ${bounds[1] + 1})`);
  if (voice.category === "vocal") {
    invalid.push(`(${name} :vowel -0.1)`);
    for (const [key, bounds] of Object.entries(voice.ranges))
      if (bounds.length === 3) invalid.push(`(${name} :${key} ${bounds[0] + 0.5})`);
    evaluate(`(${name} :vowel 1.5 :dur 0.2)`);
    advance(0.3);
  }
  if (!voice.held) invalid.push(`(${name} :legato true)`);
  if (name === "priest")
    for (const args of [":vowel 4.1", ":shift -13", ":bandwidth 0", ":ring 0", ":mix 2"])
      invalid.push(`(fx/formant ${args})`);
  evaluate(invalid.map(form => `(when-not (= :invalid (try* (do ${form} :valid) (catch* e :invalid)))
    (throw ${JSON.stringify(`Accepted ${form}`)}))`).join("\n"));
  // Record loops with independent parameter lengths, FX, and held edits. A
  // replay must use the same noise seeds and retained source definitions.
  evaluate('(p/record!)');
  const effect = voice.category === "vocal" ? "(fx/formant :vowel [0 1.5 4] :mix 0.7)" : "(fx/flanger :rate 0.15)";
  evaluate(`(${name} :id :test :every [0.25 0.5] :note [48 55 60]${voice.held ? "" : " :dur 0.2"} :fx ${effect}${voice.held ? " :legato true :release 0.05" : ""})`);
  const parts = [advance(0.6)];
  if (voice.held) {
    evaluate('(def route (get (routing/current-route :test) :number))');
    const heldEffect = voice.category === "vocal" ? "(fx/formant :vowel 2.5 :mix 0.7)" : effect;
    evaluate(`(${name} :id :test :legato true :note 62 :release 0.05 :fx ${heldEffect})`);
    check('(= route (get (routing/current-route :test) :number))');
  }
  parts.push(advance(0.3));
  evaluate('(p/stop! :test)');
  parts.push(advance(4.2));
  evaluate('(p/finish!) (p/export!)');
  const csd = api.csoundGetStringChannel(engine, "__panaeolus_export");
  assert.ok(!csd.includes("MalValue"));
  const replay = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0"]) api.csoundSetOption(replay, option);
    assert.equal(api.csoundCompileCSD(replay, csd), 0);
    assert.equal(api.csoundStart(replay), 0);
    for (const audio of parts) assert.deepEqual(advance(audio.length / 96000, replay), audio, name + " replay");
  } finally { api.csoundDestroy(replay); }
  assert.equal(peak(advance(0.05)), 0, "Stopped voice still audible");
  console.log(`PASS: ${name}: lazy loading, defaults, validation, patterns, FX, held controls, exact CSD replay.`);
} finally { api.csoundDestroy(engine); }
