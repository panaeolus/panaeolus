import assert from "node:assert/strict";
import { resolve } from "node:path";
import { loudness, peak, pcmWave } from "./audio-level";

// Render the same reference phrase for each voice. Future instruments should
// use this meter and phrase before choosing their fixed output trim.
Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const api = await libcsound();
const root = resolve(import.meta.dir, "..");
const rate = 48000;
const support = await Bun.file(resolve(root, "src/instruments/common/levels.orc")).text();
const plucks = await Bun.file(resolve(root, "src/instruments/plucks/voices.orc")).text();
const notes = [48, 55, 60, 67, 72, 79];
const renders: Float64Array[] = [];
for (const [name, opcode, controls] of [
  ["pluck", "PanaeolusPluck", "0.5, 0.23"],
  ["fmpluck", "PanaeolusFMPluck", "1.5, 2.5"],
]) {
  const engine = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
    assert.equal(api.csoundCompileOrc(engine, `sr=${rate}\nksmps=64\nnchnls=2\n0dbfs=1\n${support}\n${plucks}
instr 1
  left:a, right:a = ${opcode}(p4, p5, 0.8, ${controls}, 0, 1)
  outs left, right
endin
${notes.map((note, index) => `schedule(1, ${index}, 1, ${note}, 0)`).join("\n")}`), 0);
    assert.equal(api.csoundStart(engine), 0);
    const audio = new Float64Array(rate * notes.length * 2);
    for (let offset = 0; offset < audio.length; offset += 128) {
      assert.equal(api.csoundPerformKsmps(engine), 0);
      audio.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), 128), offset);
    }
    renders.push(audio);
    console.log(`${name}: ${loudness(audio, rate).toFixed(2)} LUFS, peak ${peak(audio).toFixed(4)} at :db 0`);
  } finally { api.csoundDestroy(engine); }
}
const combined = new Float64Array(renders.reduce((sum, audio) => sum + audio.length, 0));
let offset = 0;
for (const audio of renders) { combined.set(audio, offset); offset += audio.length; }
const path = process.argv[2] ?? resolve(root, "dist/plucks-preview.wav");
await Bun.write(path, pcmWave(combined, rate));
console.log(path);
