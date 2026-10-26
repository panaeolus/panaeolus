import assert from "node:assert/strict";
import { api, support } from "../tools/catalog-audio";
import { parseHetro, analysisPath } from "../tools/hetro-module";
import { peak } from "../tools/audio-level";
const analysis = parseHetro(await Bun.file(new URL("fixtures/additive.het", import.meta.url)).text());
const source = await Bun.file(new URL("../src/instruments/additive/adsyn.orc", import.meta.url)).text();
function render(speed: number, rate = 48000, block = 64, scale = 1, seconds = 1.5) {
  const engine = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
    api.writeFile(engine, analysisPath(analysis), new TextEncoder().encode(analysis.text));
    assert.equal(api.csoundCompileOrc(engine, `sr=${rate}\nksmps=${block}\nnchnls=2\n0dbfs=${scale}\n${support}\n${source}
instr 1
left:a, right:a = PanaeolusAdsyn("${analysisPath(analysis)}", 60, -8.874, ${analysis.top}, 60, 0, ${speed}, .005, .01, 1, .05, 0, 1)
outs left,right
endin
schedule(1,0,${seconds})`), 0);
    assert.equal(api.csoundStart(engine), 0);
    const audio = new Float64Array(Math.ceil(seconds * rate / block) * block * 2);
    for (let offset = 0; offset < audio.length; offset += block * 2) {
      assert.equal(api.csoundPerformKsmps(engine), 0);
      audio.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), block * 2), offset);
    }
    assert.ok(audio.every(Number.isFinite));
    return audio;
  } finally { api.csoundDestroy(engine); }
}
const normal = render(1);
const halfScale = render(1, 48000, 64, .5);
assert.deepEqual(halfScale, normal.map(x => x * .5), "0dbfs scaling");
assert.ok(peak(render(.5).subarray(48000 * 2)) > .01, "Slow tracks ended early");
assert.equal(peak(render(2).subarray(48000 * 2)), 0, "Fast tracks did not finish");
for (const rate of [44100, 96000]) assert.ok(peak(render(1, rate)) > .01);
// Even a direct native call with zero speed must not divide by zero, including
// a sample-by-sample control period where 0.01 would round to zero ticks.
assert.ok(peak(render(0, 192000, 1, 1, .1)) > 0);
console.log("PASS: adsyn speed, finite track endings, 0dbfs scaling, 44.1/48/96/192 kHz, one-sample blocks.");
