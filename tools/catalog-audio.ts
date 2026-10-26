import assert from "node:assert/strict";
import { resolve } from "node:path";
export const root = resolve(import.meta.dir, "..");
export interface Voice {
  name: string; category: string; namespace: string; opcode: string; node: string;
  rate: string; count: number; source: string; mal: string; reference: number[];
  ranges: Record<string, number[]>; held: boolean;
}
export const catalog: Voice[] = await Bun.file(resolve(root, "src/instruments/catalog.json")).json();
export const support = (await Promise.all([
  "src/instruments/common/levels.orc", "src/instruments/plucks/voices.orc",
  "src/instruments/synths/voices.orc", "src/instruments/common/catalog.orc", "src/instruments/vocal/support.orc", "src/instruments/scanned/support.orc", "src/instruments/dubstep/support.orc",
].map(path => Bun.file(resolve(root, path)).text()))).join("\n");
Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
export const api = await libcsound();
export function notes(voice: Voice) {
  return voice.category === "drums" ? Array(6).fill(voice.reference[0]) : [48, 55, 60, 67, 72, 79];
}
export async function render(voice: Voice, controls = voice.reference, pitches = notes(voice), rate = 48000, scale = 1) {
  const engine = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
    const source = await Bun.file(resolve(root, voice.source)).text();
    assert.equal(api.csoundCompileOrc(engine, `sr=${rate}\nksmps=64\nnchnls=2\n0dbfs=${scale}\n${support}\n${source}
instr 1
  left:a, right:a = ${voice.opcode}(p4, ${controls.slice(1).join(", ")})
  outs left, right
endin
${pitches.map((note, index) => `schedule(1, ${index}, 1, ${note})`).join("\n")}`), 0, voice.name);
    assert.equal(api.csoundStart(engine), 0);
    const audio = new Float64Array(Math.ceil(rate * pitches.length / 64) * 128);
    for (let offset = 0; offset < audio.length; offset += 128) {
      assert.equal(api.csoundPerformKsmps(engine), 0);
      audio.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), 128), offset);
    }
    return audio;
  } finally { api.csoundDestroy(engine); }
}
