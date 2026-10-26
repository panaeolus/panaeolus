import assert from "node:assert/strict";
import { api, catalog, render, root, support } from "../tools/catalog-audio";
import { peak } from "../tools/audio-level";
import { resolve } from "node:path";

const voices = catalog.filter(v => v.category === "vocal");
async function held(name: string, refresh = true, warm = false, sweep = true, rate = 48000, scale = 1) {
  const voice = voices.find(v => v.name === name)!;
  const source = await Bun.file(resolve(root, voice.source)).text();
  const engine = api.csoundCreate();
  try {
    for (const flag of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, flag);
    const helpers = refresh ? support : support.replaceAll("nextRefresh:k init 1", "nextRefresh:k init 10000")
      .replaceAll("panaeolusVocalRise, 2,", "panaeolusVocalRise, 20,");
    const values = voice.reference.map((value, index) => index === 2 ? "vowel" : String(value)).join(", ");
    assert.equal(api.csoundCompileOrc(engine, `sr=${rate}\nksmps=64\nnchnls=2\n0dbfs=${scale}\n${helpers}\n${source}
instr 1
  vowel:k linseg 0, 0.25, 0, 2.5, ${sweep ? 4 : 0}, 1, ${sweep ? 4 : 0}
  left:a, right:a = ${voice.opcode}(${values})
  outs left, right
endin
instr 2
  turnoff2i 1, 4, 0
endin
schedule(1, 0, -1)
schedule(2, 3.5, 0)
${warm ? "schedule(1, 4, -1)\nschedule(2, 7.5, 0)" : ""}`), 0, name);
    assert.equal(api.csoundStart(engine), 0);
    const seconds = warm ? 8 : 4;
    const audio = new Float64Array(Math.ceil(seconds * rate / 64) * 128);
    for (let offset = 0; offset < audio.length; offset += 128) {
      assert.equal(api.csoundPerformKsmps(engine), 0);
      audio.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), 128), offset);
    }
    assert.ok(audio.every(Number.isFinite), name + " nonfinite output");
    return audio;
  } finally { api.csoundDestroy(engine); }
}
for (const voice of voices) {
  const changing = await held(voice.name);
  const fixed = await held(voice.name, true, false, false);
  assert.notDeepEqual(changing, fixed, voice.name + " ignored vowel changes");
  assert.ok(peak(changing.subarray(48000 * 2 * 3, 48000 * 2 * 3.25)) > .001, voice.name + " stopped sustaining");
  assert.ok(peak(changing) < 1, voice.name + " vowel sweep clips at reference level");
  const reused = await held(voice.name, true, true);
  assert.deepEqual(reused.subarray(48000 * 2 * 4), changing, voice.name + " reused stale state");
  // Both traces have identical envelopes and vowel changes. Only the finite
  // FOF counter differs, so refreshing it must not change a single sample.
  if (["priest", "cantor"].includes(voice.name))
    assert.deepEqual(await held(voice.name, false), changing, voice.name + " refresh changes grains/phases");
  const half = await held(voice.name, true, false, true, 48000, .5);
  assert.deepEqual(half, changing.map(x => x * .5), voice.name + " inconsistent 0dbfs scaling");
  for (const rate of [44100, 96000]) assert.ok(peak(await held(voice.name, true, false, true, rate)) < 1);
  // Combined timbre extremes catch failures that one-control sweeps miss,
  // such as FOF preamplification with both high shift and broad bandwidth.
  for (const high of [false, true]) {
    const controls = voice.reference.map((value, index) => {
      const key = Object.keys(voice.ranges)[index];
      return ["note", "vowel", "shift", "bandwidth", "grain", "gliss", "phase", "octave",
        "vibrato", "vibrato-rate", "edge", "breath", "ring", "pulses", "damping", "bend",
        "register", "tilt"].includes(key) ? voice.ranges[key][high ? 1 : 0] : value;
    });
    const audio = await render(voice, controls, [controls[0]], 44100);
    assert.ok(audio.every(Number.isFinite), voice.name + " nonfinite combined controls");
    assert.ok(peak(audio) < 8, voice.name + " unstable combined controls");
  }
  console.log(`PASS: ${voice.name}: vowel morph, held duration, warm reuse, reference headroom, sample rates, 0dbfs.`);
}
