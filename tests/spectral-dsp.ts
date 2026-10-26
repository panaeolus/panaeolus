import assert from "node:assert/strict";
import { api, support } from "../tools/catalog-audio";
import { peak } from "../tools/audio-level";
const source = support + "\n" + await Bun.file(new URL("../src/instruments/samplers/spectral.orc", import.meta.url)).text();
function frequency(audio: Float64Array, channel: number) {
  const crossings: number[] = [];
  for (let frame = 12000; frame < 36000; frame++) {
    if (audio[frame * 2 + channel] <= 0 && audio[(frame + 1) * 2 + channel] > 0) crossings.push(frame);
  }
  return (crossings.length - 1) * 48000 / (crossings.at(-1)! - crossings[0]);
}
for (const sourceRate of [24000, 44100, 48000]) for (const speed of [1, -1, 0]) {
  const engine = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
    // GEN02 has the same layout/rate metadata as the cached split tables.
    assert.equal(api.csoundCompileOrc(engine, `sr=48000\nksmps=64\nnchnls=2\n0dbfs=1\n${source}
leftTable@global:i ftgen 0, 0, -${sourceRate}, -2, 0
rightTable@global:i ftgen 0, 0, -${sourceRate}, -2, 0
for index in [0 ... ${sourceRate - 1}] do
  tableiw sin(2 * $M_PI * 440 * index / ${sourceRate}) * 0.2, index, leftTable
  tableiw sin(2 * $M_PI * 660 * index / ${sourceRate}) * 0.2, index, rightTable
od
instr 1
  left:a, right:a = PanaeolusSpectral(leftTable, rightTable, ${sourceRate}, ${sourceRate}, 2048, 1, 1, 0, p4, 60, 4, ${speed}, 0, 1, 0.5, 1, 0.005, 0.01, 1, 0.05, 0)
  outs left, right
endin`), 0);
    assert.equal(api.csoundStart(engine), 0);
    for (const note of [60, 72]) {
      api.csoundInputMessage(engine, `i 1 0 1 ${note}`);
      const audio = new Float64Array(48000 * 2);
      for (let offset = 0; offset < audio.length; offset += 128) {
        assert.equal(api.csoundPerformKsmps(engine), 0);
        audio.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), 128), offset);
      }
      assert.ok(peak(audio) > 0.05 && peak(audio) < 0.5);
      const ratio = note === 60 ? 1 : 2;
      for (const channel of [0, 1]) {
        const hz = [440, 660][channel] * ratio;
        assert.ok(Math.abs(frequency(audio, channel) - hz) < 5,
          JSON.stringify({ sourceRate, speed, note, channel, expected: hz, actual: frequency(audio, channel) }));
      }
    }
  } finally { api.csoundDestroy(engine); }
}
console.log("PASS: spectral stereo pitch, stretch, reverse and freeze at 24/44.1/48 kHz source rates.");

// A UDO instance can move between parent instruments and FFT sizes.
function reused(warm: boolean) {
  const engine = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
    assert.equal(api.csoundCompileOrc(engine, `sr=48000\nksmps=64\nnchnls=2\n0dbfs=1\n${source}
ft@global:i ftgen 0, 0, -24000, -2, 0
for index in [0 ... 23999] do
  tableiw sin(2 * $M_PI * 440 * index / 24000) * 0.2, index, ft
od
instr 1
  left:a, right:a = PanaeolusSpectral(ft, ft, 24000, 24000, 2048, 1, 1, 0, 60, 60, 1, 1, 0, 1, 0, 1, 0.005, 0.01, 1, 0.05, 0)
  outs left, right
endin
instr 2
  left:a, right:a = PanaeolusSpectral(ft, ft, 24000, 24000, 1024, 1, 1, 0, 60, 60, 4, 1, 0, 1, 0, 1, 0.005, 0.01, 1, 0.05, 0)
  outs left, right
endin`), 0);
    assert.equal(api.csoundStart(engine), 0);
    if (warm) {
      api.csoundInputMessage(engine, "i 1 0 0.5");
      for (let block = 0; block < 450; block++) assert.equal(api.csoundPerformKsmps(engine), 0);
    }
    api.csoundInputMessage(engine, "i 2 0 1");
    const audio = new Float64Array(96000);
    for (let block = 0; block < 750; block++) {
      assert.equal(api.csoundPerformKsmps(engine), 0);
      audio.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), 128), block * 128);
    }
    return audio;
  } finally { api.csoundDestroy(engine); }
}

assert.deepEqual(reused(false), reused(true), "Reused spectral player retained phase history");
console.log("PASS: fresh and reused spectral players produce identical audio across FFT sizes.");
