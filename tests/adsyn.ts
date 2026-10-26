import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve, join } from "node:path";
import { analysisModule, analysisPath, parseHetro } from "../tools/hetro-module";
import { peak, loudness } from "../tools/audio-level";
Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const api = await libcsound(), engine = api.csoundCreate();
const analysis = parseHetro(await Bun.file(new URL("fixtures/additive.het", import.meta.url)).text());
const temp = await mkdtemp(join(tmpdir(), "panaeolus-analysis-test-"));
function evaluate(source: string, expected = 0) {
  api.csoundSetStringChannel(engine, "__analysis_test", source + "\nnil");
  api.csoundEvalCode(engine, 'return PanaeolusEval(chnget:S("__analysis_test"))');
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), expected, source.slice(-300));
}
function advance(seconds: number, target = engine) {
  const audio = new Float64Array(Math.ceil(seconds * 48000 / 64) * 128);
  for (let offset = 0; offset < audio.length; offset += 128) {
    assert.equal(api.csoundPerformKsmps(target), 0);
    audio.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(target), 128), offset);
  }
  assert.ok(audio.every(Number.isFinite));
  return audio;
}
function replayTake(csd: string, parts: Float64Array[]) {
  const replay = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0"]) api.csoundSetOption(replay, option);
    assert.equal(api.csoundCompileCSD(replay, csd), 0);
    assert.equal(api.csoundStart(replay), 0);
    for (const part of parts) assert.deepEqual(advance(part.length / 96000, replay), part);
  } finally { api.csoundDestroy(replay); }
}
try {
  const generated = join(temp, "resynth.mal");
  const cli = Bun.spawn([process.execPath, resolve(import.meta.dir, "../tools/hetro.ts"),
    resolve(import.meta.dir, "fixtures/additive.het"), generated], { stdout: "inherit", stderr: "inherit" });
  assert.equal(await cli.exited, 0, "Analysis module CLI failed");
  const module = await Bun.file(generated).text();
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
  assert.equal(api.csoundCompileOrc(engine, "sr=48000\nksmps=64\nnchnls=2\n0dbfs=1\n" +
    await Bun.file(new URL("../dist/make-a-lisp.orc", import.meta.url)).text() + "\n" +
    await Bun.file(new URL("../dist/panaeolus.orc", import.meta.url)).text()), 0);
  assert.equal(api.csoundStart(engine), 0);
  evaluate("(require '[panaeolus.instrument :as p] '[panaeolus.fx :as fx])");
  evaluate(module);
  assert.equal(peak(advance(.1)), 0, "Import played audio");
  assert.equal(new TextDecoder().decode(api.readFile(engine, analysisPath(analysis))), analysis.text);
  // Reload must not duplicate the file or the take's asset registry.
  evaluate(module);
  assert.equal(api.csoundEvalCode(engine, "return panaeolusAnalysisCount"), 1);
  evaluate('(resynth :db 0)');
  const reference = advance(analysis.duration);
  advance(.1);
  assert.ok(peak(reference) > .02);
  assert.ok(Math.abs(loudness(reference, 48000) + 18) < .2, `Reference: ${loudness(reference, 48000)} LUFS`);
  evaluate('(resynth :db -6)');
  assert.ok(Math.abs(loudness(advance(analysis.duration), 48000) - loudness(reference, 48000) + 6) < .01);
  advance(.1);
  for (const call of ['(resynth :speed 0)', '(resynth :speed -1)', '(resynth :legato true)', '(resynth :note 120)'])
    evaluate(`(when-not (= :invalid (try* (do ${call} :valid) (catch* e :invalid))) (throw "Accepted invalid control"))`);
  evaluate('(p/record!) (resynth :every [0.25 0.5] :note [60 67 72] :speed [0.5 1 2] :dur 0.4 :fx (fx/flanger :rate 0.2))');
  const parts = [advance(.9)];
  evaluate('(p/stop! resynth)');
  parts.push(advance(4.5));
  evaluate('(p/finish!) (p/export!)');
  const csd = api.csoundGetStringChannel(engine, "__panaeolus_export");
  assert.ok(csd.includes('<CsFileB filename="__panaeolus_analysis_'));
  assert.ok(csd.includes(Buffer.from(analysis.text).toString("base64")));
  assert.ok(!csd.includes("MalValue"));
  // A fresh engine has no analysis registry. Its only input is the CSD.
  replayTake(csd, parts);
  // Finished takes stay unchanged when more analyses are loaded afterwards.
  const withoutExportNames = (text: string) => text.replace(/__panaeolus_analysis_\d+_\d+_\d+\.het/g, "analysis.het");
  const before = withoutExportNames(csd);
  const changed = analysis.text.replaceAll(",262,", ",260,");
  assert.notEqual(changed, analysis.text);
  evaluate(analysisModule(parseHetro(changed), "other", 60, -8.874));
  evaluate('(p/export!)');
  assert.equal(withoutExportNames(api.csoundGetStringChannel(engine, "__panaeolus_export")), before);
  api.writeFile(engine, "brass.mal", new Uint8Array(await Bun.file(new URL("../examples/analysed-brass.mal", import.meta.url)).arrayBuffer()));
  evaluate('(load-file "brass.mal") (p/record!) (resynth)');
  const brass = advance(3.2);
  assert.ok(peak(brass) > .01, "Bundled analysis example is silent");
  evaluate('(p/finish!) (p/export!)');
  const brassCsd = api.csoundGetStringChannel(engine, "__panaeolus_export");
  assert.equal((brassCsd.match(/<CsFileB /g) ?? []).length, 3);
  replayTake(brassCsd, [brass]);
  console.log("PASS: adsyn module import, levels, validation, patterns, FX, immutable assets, standalone CSD replay.");
} finally {
  api.csoundDestroy(engine);
  await rm(temp, { recursive: true, force: true });
}
