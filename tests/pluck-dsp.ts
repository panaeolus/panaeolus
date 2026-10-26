import assert from "node:assert/strict";
import { peak } from "../tools/audio-level";

Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const api = await libcsound();
const source = await Bun.file(new URL("../src/instruments/common/levels.orc", import.meta.url)).text() + "\n" +
  await Bun.file(new URL("../src/instruments/plucks/voices.orc", import.meta.url)).text();
let count = 0;
// Exercise parameter extremes below and above the reference register, at both
// common engine rates and a non-unit 0dbfs. No Lisp state is needed for DSP.
for (const rate of [44100, 48000]) for (const scale of [1, 0.5]) {
  const engine = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
    assert.equal(api.csoundCompileOrc(engine, `sr=${rate}\nksmps=64\nnchnls=2\n0dbfs=${scale}\n${source}
instr 1
  left:a, right:a = PanaeolusPluck(p4, 0, p5, p6, p7, 0, 1)
  outs left, right
endin
instr 2
  left:a, right:a = PanaeolusFMPluck(p4, 0, p5, p6, p7, 0, 1)
  outs left, right
endin`), 0);
    assert.equal(api.csoundStart(engine), 0);
    for (const voice of [1, 2]) {
      const controls = voice === 1
        ? [[0, 0.05], [0, 0.95], [1, 0.05], [1, 0.5], [1, 0.95]]
        : [[0.25, 0], [0.25, 8], [8, 0], [8, 8]];
      for (const note of [24, 60, 96]) for (const decay of [0.02, 8]) for (const settings of controls) {
        api.csoundInputMessage(engine, `i ${voice} 0 0.3 ${note} ${decay} ${settings.join(" ")}`);
        let top = 0;
        for (let block = 0; block < Math.ceil(0.35 * rate / 64); block++) {
          assert.equal(api.csoundPerformKsmps(engine), 0);
          top = Math.max(top, peak(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), 128)) / scale);
        }
        assert.ok(top > 0 && top < 1, JSON.stringify({ rate, scale, voice, note, decay, settings, top }));
        count++;
      }
    }
  } finally { api.csoundDestroy(engine); }
}
console.log(`PASS: ${count} pluck DSP cases; pitch/timbre/decay extremes, engine rates, full-scale values, finite unclipped output.`);
