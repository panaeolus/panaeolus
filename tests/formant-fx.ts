import assert from "node:assert/strict";
import { api, support } from "../tools/catalog-audio";
import { peak } from "../tools/audio-level";
const source = await Bun.file(new URL("../src/effects/formant.orc", import.meta.url)).text();
function render(controls: string | null, rate = 48000) {
  const engine = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
    assert.equal(api.csoundCompileOrc(engine, `sr=${rate}\nksmps=64\nnchnls=2\n0dbfs=1\n${support}\n${source}
instr 1
  input:a buzz 0.1, 140, 40, panaeolusVocalSine
  age:k timeinsts
  gate:k = (age < 0.5 ? 1 : 0)
  input *= gate
  silent:a = 0
  ${controls === null ? "outs input, silent" : `left:a, right:a = PanaeolusFormantFX(input, silent, ${controls})\nouts left, right`}
endin
schedule(1, 0, 3)`), 0);
    assert.equal(api.csoundStart(engine), 0);
    const audio = new Float64Array(Math.ceil(3 * rate / 64) * 128);
    for (let offset = 0; offset < audio.length; offset += 128) {
      assert.equal(api.csoundPerformKsmps(engine), 0);
      audio.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), 128), offset);
    }
    assert.ok(audio.every(Number.isFinite));
    for (let i = 1; i < audio.length; i += 2) assert.equal(audio[i], 0, "Effect crossed stereo channels");
    return audio;
  } finally { api.csoundDestroy(engine); }
}
const dry = render(null);
assert.deepEqual(render("0, 0, 1, 1, 0"), dry, "Dry mix changed input");
const a = render("0, 0, 1, 1, 1"), u = render("4, 0, 1, 1, 1");
assert.notDeepEqual(a, dry); assert.notDeepEqual(a, u);
assert.ok(peak(a) > .001);
for (const control of ["0, -12, 0.25, 4, 1", "4, 12, 4, 0.25, 1", "2.5, 0, 1, 1, 0.5"]) {
  const audio = render(control, 44100);
  assert.ok(peak(audio) < 1, "Bounded effect settings clipped the test input");
  assert.ok(peak(audio.subarray(44100 * 2 * 2.5)) < 1e-8, "Tail exceeded two seconds");
}
console.log("PASS: formant FX: exact dry bypass, vowel controls, stereo isolation, control extremes, bounded tails.");
