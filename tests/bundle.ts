import assert from "node:assert/strict";
import { resolve } from "node:path";

Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../node_modules/@csound/browser/dist/csound.js");
const api = await libcsound();
const engine = api.csoundCreate();
const root = resolve(import.meta.dir, "..");
const interpreter = await Bun.file(resolve(root, "dist/make-a-lisp.orc")).text();
const bundle = await Bun.file(resolve(root, "dist/panaeolus.mal")).text();
let checks = 0;

function evaluate(source: string, expected = 0) {
  api.csoundSetControlChannel(engine, "__bundle_status", -999);
  api.csoundSetStringChannel(engine, "__bundle_source", source);
  api.csoundEvalCode(engine, 'return BundleEval(chnget:S("__bundle_source"))');
  assert.equal(api.csoundGetControlChannel(engine, "__bundle_status"), expected, source);
  checks++;
}

function check(form: string) {
  evaluate(`(when-not ${form} (throw (str "Failed: " (pr-str '${form}))))`);
}

try {
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
  // The prepared interpreter reserves instrument numbers at startup, but
  // contains no Panaeolus bridge. The release MAL file must supply that code.
  assert.equal(api.csoundCompileOrc(engine, `
sr = 48000
ksmps = 64
nchnls = 2
0dbfs = 1
${interpreter}
bundleEnv@global:MalEnv init MalMkCsoundEnv()
opcode BundleEval(source:S):i
  result:MalValue, updated:MalEnv = MalEvalFormsEnv(source, bundleEnv)
  bundleEnv init updated
  status:i = 0
  if (result.type == $MAL_ERROR_TYPE) ithen
    prints "Bundle test error: %s\\n", result.string
    status = -1
  endif
  chnset status, "__bundle_status"
  xout status
endop
`), 0);
  assert.equal(api.csoundStart(engine), 0);
  api.writeFile(engine, "panaeolus.mal", new TextEncoder().encode(bundle));
  evaluate("(ns performance) (def notes [440 550 660]) (create-ns 'panaeolus.instrument) (load-file \"panaeolus.mal\")");
  check("(= 'performance *ns*)");
  check("(= [440 550 660] notes)");
  check("(= 1 (csound/eval \"return bundleEnv.id == panaeolusEnv.id ? 1 : 0\"))");
  evaluate("(require '[panaeolus.instrument :as p] '[panaeolus.fx :refer [flanger]])");
  check("(fn? p/stop-all!)");
  check("(fn? flanger)");
  evaluate(`
    (def hits (atom []))
    (p/definst pulse [hz 440 amp 0.04] (csound/poscil amp hz))
    (pulse :every [0.25 0.5] :hz notes :fx (flanger :rate 0.5))
    (panaeolus.event-loop/start! :observer [0.25]
      (fn [event] (swap! hits conj (get event :step))))`);
  const clock = api.csoundEvalCode(engine, 'return nstrnum("PanaeolusClock")');
  const env = api.csoundEvalCode(engine, "return panaeolusEnv.id");
  evaluate('(load-file "panaeolus.mal")');
  assert.equal(api.csoundEvalCode(engine, 'return nstrnum("PanaeolusClock")'), clock);
  assert.equal(api.csoundEvalCode(engine, "return panaeolusEnv.id"), env);
  check("(= 2 (count (get @panaeolus.event-loop/state :slots)))");
  let peak = 0;
  for (let block = 0; block < 500; block++) {
    assert.equal(api.csoundPerformKsmps(engine), 0);
    const pointer = api.csoundGetSpout(engine);
    const samples = new Float64Array(api.getMemory().buffer, pointer, 128);
    for (const sample of samples) {
      assert.ok(Number.isFinite(sample));
      peak = Math.max(peak, Math.abs(sample));
    }
  }
  assert.ok(peak > 0 && peak <= 1, `Invalid audio peak: ${peak}`);
  check("(= [0 1 2 3 4 5] @hits)");
  evaluate("(p/stop-all!)");
  // Another MAL root in this Csound engine must not reset the active runtime.
  assert.equal(api.csoundEvalCode(engine,
    'bundleEnv init MalMkCsoundEnv()\nreturn 0'), 0);
  evaluate('(load-file "panaeolus.mal")', -1);
  assert.equal(api.csoundEvalCode(engine, "return panaeolusEnv.id"), env);
  console.log(`PASS: ${checks} bundle checks; caller namespace, require, repeated load, shared session, clock, and audio.`);
} finally {
  api.csoundDestroy(engine);
}
