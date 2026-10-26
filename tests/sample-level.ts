import assert from "node:assert/strict";
import { loudness } from "../tools/audio-level";
Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const api = await libcsound();
const source = await Bun.file(new URL("../src/sample-level.orc", import.meta.url)).text();
function wave(audio: Float64Array, channels: number, rate: number) {
  const bytes = Buffer.alloc(44 + audio.length * 8);
  bytes.write("RIFF"); bytes.writeUInt32LE(bytes.length - 8, 4); bytes.write("WAVEfmt ", 8);
  bytes.writeUInt32LE(16, 16); bytes.writeUInt16LE(3, 20); bytes.writeUInt16LE(channels, 22);
  bytes.writeUInt32LE(rate, 24); bytes.writeUInt32LE(rate * channels * 8, 28);
  bytes.writeUInt16LE(channels * 8, 32); bytes.writeUInt16LE(64, 34);
  bytes.write("data", 36); bytes.writeUInt32LE(audio.length * 8, 40);
  audio.forEach((value, index) => bytes.writeDoubleLE(value, 44 + index * 8));
  return bytes;
}
let checks = 0;
for (const fullScale of [1, 0.5]) {
  const engine = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0"]) api.csoundSetOption(engine, option);
    assert.equal(api.csoundCompileOrc(engine, `sr=48000\nksmps=64\nnchnls=2\n0dbfs=${fullScale}\n${source}`), 0);
    assert.equal(api.csoundStart(engine), 0);
    for (const [name, rate, channels, seconds, amplitude] of [
      ["mono", 24000, 1, 0.5, 0.1], ["stereo", 44100, 2, 0.5, 0.1],
      ["high-rate", 96000, 2, 0.4, 0.1], ["short", 48000, 1, 0.02, 0.1],
      ["silence", 48000, 2, 0.02, 0], ["quiet", 48000, 1, 0.1, 0.001],
      ["below-gate", 48000, 1, 0.1, 0.0001],
      ["transient", 48000, 1, 0.4, 1],
    ] as const) {
      const frames = Math.round(seconds * rate);
      const data = Float64Array.from({ length: frames * channels }, (_, index) => {
        const frame = Math.floor(index / channels);
        if (name === "transient") return frame === 50 ? 1 : 0;
        return amplitude * Math.sin(2 * Math.PI * 1000 * frame / rate);
      });
      api.writeFile(engine, "level.wav", wave(data, channels, rate));
      assert.equal(api.csoundEvalCode(engine, `sample:i ftgen 0, 0, 0, -1, "level.wav", 0, 0, 0
        gain:i, measured:i = PanaeolusSampleLevel(sample, ${frames}, ${channels}, ${rate})
        chnset gain, "gain"
        chnset measured, "loudness"
        ftfree sample, 0
        return 0`), 0);
      const gain = api.csoundGetControlChannel(engine, "gain");
      const measured = api.csoundGetControlChannel(engine, "loudness");
      const stereo = channels === 2 ? data : Float64Array.from({ length: frames * 2 }, (_, index) => data[Math.floor(index / 2)]);
      const expected = loudness(stereo, rate);
      assert.ok(Number.isFinite(gain) && gain > 0 && gain <= 10 ** (24 / 20), `${name}: gain ${gain}`);
      if (!Number.isFinite(expected)) { assert.equal(gain, 1); assert.equal(measured, -120); }
      else {
        assert.ok(Math.abs(measured - expected) < 0.001, `${name}: native ${measured}, reference ${expected}`);
        const targetGain = Math.min(10 ** ((-18 - expected) / 20), 10 ** (24 / 20), 10 ** (-3 / 20) / amplitude);
        assert.ok(Math.abs(gain - targetGain) < 0.001, `${name}: target/headroom gain ${gain}, expected ${targetGain}`);
      }
      checks++;
    }
  } finally { api.csoundDestroy(engine); }
}
console.log(`PASS: ${checks} native level measurements; mono/stereo, sample rates, full-scale values, short files, silence, boost and peak limits.`);
