import assert from "node:assert/strict";
import { peak } from "../tools/audio-level";
Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const api = await libcsound();
const source = await Bun.file(new URL("../src/instruments/common/levels.orc", import.meta.url)).text() + "\n" +
  await Bun.file(new URL("../src/instruments/synths/voices.orc", import.meta.url)).text();
let count = 0;
for (const rate of [44100, 48000]) for (const scale of [1, 0.5]) {
  const engine = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
    assert.equal(api.csoundCompileOrc(engine, `sr=${rate}\nksmps=64\nnchnls=2\n0dbfs=${scale}\n${source}
instr 1
  left:a, right:a = PanaeolusHammer(p4, 0, p5, p6, p7, 0, 1)
  outs left, right
endin
instr 2
  left:a, right:a = PanaeolusLush(p4, 0, p5, p6, p7, p8, 0.01, 0.1, 1, 0.05, 0, 0, 1)
  outs left, right
endin`), 0);
    assert.equal(api.csoundStart(engine), 0);
    for (const voice of [1, 2]) {
      const controls = voice === 1
        ? [[0.02, 0, 0], [8, 0, 1], [0.02, 1, 1], [8, 1, 0]]
        : [[20, 0, 0, 0], [20000, 0, 40, 1], [20000, 0.8, 0, 0], [1000, 0.8, 40, 1]];
      for (const note of [24, 60, 96]) for (const settings of controls) {
        api.csoundInputMessage(engine, `i ${voice} 0 0.3 ${note} ${settings.join(" ")}`);
        let top = 0;
        for (let block = 0; block < Math.ceil(0.35 * rate / 64); block++) {
          assert.equal(api.csoundPerformKsmps(engine), 0);
          top = Math.max(top, peak(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), 128)) / scale);
        }
        assert.ok(top > 0 && top < 1, JSON.stringify({ rate, scale, voice, note, settings, top }));
        count++;
      }
    }
  } finally { api.csoundDestroy(engine); }
}
console.log(`PASS: ${count} synth DSP cases; pitch/control extremes, engine rates, full-scale values, finite unclipped output.`);

// Csound reuses voice instances. Signed portk controls must start from each
// note's own values, including after a previous held note released.
function reusedHeldVoice(warm: boolean) {
  const engine = api.csoundCreate();
  function advance(blocks: number) {
    const audio = new Float64Array(blocks * 128);
    for (let block = 0; block < blocks; block++) {
      assert.equal(api.csoundPerformKsmps(engine), 0);
      audio.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), 128), block * 128);
    }
    return audio;
  }
  try {
    for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
    assert.equal(api.csoundCompileOrc(engine, `sr=48000\nksmps=64\nnchnls=2\n0dbfs=1\n${source}
instr 1
  note:k chnget "note"
  db:k chnget "db"
  pan:k chnget "pan"
  left:a, right:a = PanaeolusLush(note, db, 3500, 0.2, 8, 0.7, 0.04, 0.2, 0.75, 0.08, 0.02, pan, 1)
  outs left, right
endin`), 0);
    assert.equal(api.csoundStart(engine), 0);
    if (warm) {
      api.csoundSetControlChannel(engine, "note", 67);
      api.csoundSetControlChannel(engine, "db", -3);
      api.csoundSetControlChannel(engine, "pan", 0.6);
      api.csoundInputMessage(engine, "i 1 0 -1"); advance(90);
      api.csoundEvalCode(engine, "turnoff2i 1, 0, 1\nreturn 0"); advance(100);
      assert.equal(api.csoundEvalCode(engine, "return active:i(1)"), 0,
        "A silent lush voice survived its declared release");
    }
    api.csoundSetControlChannel(engine, "note", 60);
    api.csoundSetControlChannel(engine, "db", -6);
    api.csoundSetControlChannel(engine, "pan", -0.6);
    api.csoundInputMessage(engine, "i 1 0 -1");
    return advance(180);
  } finally { api.csoundDestroy(engine); }
}
assert.deepEqual(reusedHeldVoice(true), reusedHeldVoice(false), "Reused lush voice changed its attack, gain, or pan");
console.log("PASS: fresh and reused lush voices produce identical audio with negative dB and pan.");
