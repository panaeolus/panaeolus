import assert from "node:assert/strict";
import { api, catalog, root, support } from "../tools/catalog-audio";
import { peak } from "../tools/audio-level";
import { resolve } from "node:path";

function sameAudio(actual: Float64Array, expected: Float64Array, message = "Audio differs") {
  assert.equal(actual.length, expected.length);
  let difference = 0, first = -1;
  for (let i = 0; i < actual.length; i++) {
    if (actual[i] !== expected[i] && first < 0) first = i;
    difference = Math.max(difference, Math.abs(actual[i] - expected[i]));
  }
  // Oversampled filter arithmetic may differ by a few double-precision ULPs.
  assert.ok(difference < 1e-12, `${message}: first sample ${first}, max difference ${difference}`);
}
const voices = catalog.filter(v => ["drone", "dubstep"].includes(v.category) && (!process.env.CATALOG_CATEGORY || v.category === process.env.CATALOG_CATEGORY));
async function render(name: string, options: {
  rate?: number; block?: number; scale?: number; seconds?: number; schedule?: string;
  changing?: boolean; fixed?: boolean; extreme?: boolean; other?: boolean;
} = {}) {
  const voice = voices.find(v => v.name === name)!;
  const rate = options.rate ?? 48000, block = options.block ?? 64;
  const engine = api.csoundCreate();
  const source = await Bun.file(resolve(root, voice.source)).text();
  const controls = voice.reference.map((value, index) => {
    const key = Object.keys(voice.ranges)[index], bounds = voice.ranges[key];
    if (index === 2 && (options.changing || options.fixed))
      return options.changing ? "shape" : String(bounds[0]);
    if (options.extreme && !["db", "amp", "pan", "attack", "decay", "sustain", "release", "glide"].includes(key))
      return String(bounds[["sub", "division"].includes(key) ? 0 : 1]);
    return String(value);
  }).join(", ");
  const [low, high] = Object.values(voice.ranges)[2];
  try {
    for (const flag of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, flag);
    assert.equal(api.csoundCompileOrc(engine, `sr=${rate}\nksmps=${block}\nnchnls=2\n0dbfs=${options.scale ?? 1}\n${support}\n${source}
instr 1, 3
  shape:k linseg ${low}, 0.3, ${low}, 0.5, ${high}, 1, ${high}
  left:a, right:a = ${voice.opcode}(${controls})
  if (p1 == 1) then
    outs left, right
  endif
endin
instr 2
  turnoff2i 1, 4, 0
endin
${options.schedule ?? "schedule(1, 0, 1.5)"}
${options.other ? "schedule(3, 0.25, 0.75)" : ""}`), 0, name);
    assert.equal(api.csoundStart(engine), 0);
    const audio = new Float64Array(Math.ceil((options.seconds ?? 2) * rate / block) * block * 2);
    for (let offset = 0; offset < audio.length; offset += block * 2) {
      assert.equal(api.csoundPerformKsmps(engine), 0);
      audio.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), block * 2), offset);
    }
    assert.ok(audio.every(Number.isFinite));
    return audio;
  } finally { api.csoundDestroy(engine); }
}
for (const voice of voices) {
  const fresh = await render(voice.name);
  assert.ok(peak(fresh) > .01 && peak(fresh) < 1);
  sameAudio(await render(voice.name, { other: true }), fresh, voice.name + " shared state");
  const warm = await render(voice.name, { seconds: 4, schedule: "schedule(1, 0, 1.5)\nschedule(1, 2, 1.5)" });
  sameAudio(warm.subarray(48000 * 4), fresh, voice.name + " stale oscillator/resampler history");
  sameAudio(await render(voice.name, { scale: .5 }), fresh.map(x => x * .5), voice.name + " 0dbfs scaling");
  const fixed = await render(voice.name, { fixed: true });
  const changed = await render(voice.name, { changing: true });
  sameAudio(changed.subarray(0, 48000 * .4), fixed.subarray(0, 48000 * .4));
  assert.notDeepEqual(changed, fixed, voice.name + " ignored live shape edits");
  for (const [rate, block] of [[44100, 32], [48000, 64], [96000, 128]]) {
    const held = await render(voice.name, { rate, block, seconds: 16, extreme: true,
      schedule: "schedule(1, 0, -1)\nschedule(2, 12, 0)" });
    assert.ok(peak(held) < 2, voice.name + " unstable combined controls");
    assert.ok(peak(held.subarray(rate * 20, rate * 21)) > .0001, voice.name + " held voice died");
    assert.equal(peak(held.subarray(Math.ceil(rate * 31))), 0, voice.name + " release did not end");
  }
  console.log(`PASS: ${voice.name}: overlap, reuse, live edits, gain scaling, held extremes, rates/blocks, release.`);
}
