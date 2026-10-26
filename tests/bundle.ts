import assert from "node:assert/strict";
import { resolve } from "node:path";
import { appendCsoundSource } from "../tools/build";
import { createDemoSample, DEMO_SAMPLE_PATH } from "../playground/demo-sample";

Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
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
opcode BundleRead(path:S):i
  contents:MalValue init MalSlurpFile(path)
  chnset contents.string, "__bundle_file"
  status:i = 0
  xout status
endop
`), 0);
  assert.equal(api.csoundStart(engine), 0);
  // A short final WASI read must not report unused bytes from an earlier read.
  // Vary file length so a bundle layout change cannot hide a stale EOF tail.
  for (const length of [0, 1, 63, 1023, 1024, 1025, 4095, 4096, 4097, 8190, 8191, 8192, 8193, 17]) {
    const contents = "abcdefghijklmnopqrstuvwxyz\n".repeat(Math.ceil(length / 27)).slice(0, length);
    api.writeFile(engine, "reader-tail.txt", new TextEncoder().encode(contents));
    assert.equal(api.csoundEvalCode(engine, 'return BundleRead("reader-tail.txt")'), 0);
    assert.equal(api.csoundGetStringChannel(engine, "__bundle_file"), contents,
      `readfi changed file contents at ${length} bytes`);
    checks++;
  }
  api.csoundSetStringChannel(engine, "__bundle_file", "");
  // Force a macro name across chunks, then cover undefined names and escapes.
  // Both chunks must survive the intermediate compiler without expansion.
  const sourceChunks = ["$MAL_HASH_MAP_", 'TYPE $undefined \\$literal "quote"\nhljóð', "\\", "\"\\\n$undefined"];
  for (const chunk of sourceChunks) {
    evaluate(`(csound/eval ${JSON.stringify(appendCsoundSource(chunk, "__bundle_chunks"))})`);
  }
  assert.equal(api.csoundGetStringChannel(engine, "__bundle_chunks"), sourceChunks.join(""));
  api.csoundSetStringChannel(engine, "__bundle_chunks", "");
  api.writeFile(engine, "panaeolus.mal", new TextEncoder().encode(bundle));
  evaluate("(ns performance) (def notes [440 550 660]) (create-ns 'panaeolus.instrument) (load-file \"panaeolus.mal\")");
  assert.equal(api.csoundGetStringChannel(engine, "__panaeolus_loader_source"), "",
    "Loading left the assembled orchestra in its temporary channel");
  assert.equal(api.csoundGetStringChannel(engine, "__panaeolus_loader_owner"), "",
    "Loading left the owner marker in its temporary channel");
  check("(= 'performance *ns*)");
  check("(= [440 550 660] notes)");
  check("(= 1 (csound/eval \"return bundleEnv.id == panaeolusEnv.id ? 1 : 0\"))");
  evaluate("(require '[panaeolus.instrument :as p] '[panaeolus.fx :refer [flanger]])");
  check("(fn? p/stop-all!)");
  check("(fn? flanger)");
  evaluate("(require '[panaeolus.sampler :as sampler])");
  check("(fn? sampler/load)");
  evaluate("(require '[panaeolus.instruments.pluck :as plucks])");
  check("(fn? plucks/pluck)");
  check("(fn? plucks/fmpluck)");
  api.writeFile(engine, DEMO_SAMPLE_PATH, createDemoSample());
  evaluate(`(def sample (sampler/load "${DEMO_SAMPLE_PATH}" :id :bundle-sample))
    (sample :every [0.1] :dur 0.02 :amp 0.2)`);
  let samplePeak = 0;
  for (let block = 0; block < 60; block++) {
    assert.equal(api.csoundPerformKsmps(engine), 0);
    for (const value of new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), 128)) {
      assert.ok(Number.isFinite(value));
      samplePeak = Math.max(samplePeak, Math.abs(value));
    }
  }
  assert.ok(samplePeak > 0.001 && samplePeak <= 1, `MAL bundle sampler audio peak: ${samplePeak}`);
  evaluate("(p/stop! :bundle-sample)");
  for (let block = 0; block < 30; block++) assert.equal(api.csoundPerformKsmps(engine), 0);
  evaluate(`
    (def hits (atom []))
    (p/definst pulse [hz 440 amp 0.04] (csound/poscil amp hz))
    (pulse :every [0.25 0.5] :hz notes :fx (flanger :rate 0.5))
    (panaeolus.event-loop/start! :observer [0.25]
      (fn [event] (swap! hits conj (get event :step))))`);
  const clock = api.csoundEvalCode(engine, 'return nstrnum("PanaeolusClock")');
  const env = api.csoundEvalCode(engine, "return panaeolusEnv.id");
  evaluate('(load-file "panaeolus.mal")');
  assert.equal(api.csoundGetStringChannel(engine, "__panaeolus_loader_source"), "",
    "Repeated loading assembled the orchestra again");
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
  // Construct after using the loaded graph builders: an oversized loader
  // literal used to inflate their string buffers and exhaust Wasm memory here.
  // Another MAL root in this Csound engine must not reset the active runtime.
  assert.equal(api.csoundEvalCode(engine,
    'bundleEnv init MalMkCsoundEnv()\nreturn 0'), 0);
  evaluate('(load-file "panaeolus.mal")', -1);
  assert.equal(api.csoundEvalCode(engine, "return panaeolusEnv.id"), env);
  console.log(`PASS: ${checks} bundle checks; caller namespace, require, repeated load, shared session, clock, and audio.`);
} finally {
  api.csoundDestroy(engine);
}
