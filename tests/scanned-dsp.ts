import assert from "node:assert/strict";
import { api, catalog, root, support } from "../tools/catalog-audio";
import { peak } from "../tools/audio-level";
import { resolve } from "node:path";

const voices = catalog.filter(v => v.category === "scanned");
const tables = ["positions", "velocities", "masses", "springs", "losses"];
async function render(name: string, options: {
  rate?: number; block?: number; scale?: number; seconds?: number;
  schedule?: string; changing?: boolean; shape?: number; other?: boolean; extreme?: boolean;
} = {}) {
  const voice = voices.find(v => v.name === name)!;
  const rate = options.rate ?? 48000, block = options.block ?? 64, seconds = options.seconds ?? 2;
  const engine = api.csoundCreate();
  // Observe ownership without adding debug channels to the shipped orchestra.
  const helpers = support.replace(/(  (\w+):i ftgentmp[^\n]+)\n/g,
    (_, line, name) => `${line}\n  chnset ${name}, "__scan_${name}"\n`);
  const controls = voice.reference.map((value, index) => {
    const key = Object.keys(voice.ranges)[index];
    if (options.changing && key === "shape") return "shape";
    if (key === "shape" && options.shape !== undefined) return String(options.shape);
    if (options.extreme && !["db", "amp", "pan", "attack", "decay", "sustain", "release", "glide"].includes(key))
      return String(voice.ranges[key][key === "mass" || key === "damping" ? 0 : 1]);
    return String(value);
  }).join(", ");
  const source = await Bun.file(resolve(root, voice.source)).text();
  try {
    for (const flag of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, flag);
    assert.equal(api.csoundCompileOrc(engine, `sr=${rate}\nksmps=${block}\nnchnls=2\n0dbfs=${options.scale ?? 1}\n${helpers}\n${source}
instr 1, 3
  shape:k linseg 0, 0.4, 0, 0.4, 1, 1, 1
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
    const audio = new Float64Array(Math.ceil(seconds * rate / block) * block * 2);
    const ids = new Set<number>();
    let previous = -1;
    for (let offset = 0; offset < audio.length; offset += block * 2) {
      assert.equal(api.csoundPerformKsmps(engine), 0);
      audio.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), block * 2), offset);
      const current = api.csoundGetControlChannel(engine, "__scan_positions");
      if (current !== previous) {
        for (const name of tables) ids.add(api.csoundGetControlChannel(engine, `__scan_${name}`));
        previous = current;
      }
    }
    assert.ok(audio.every(Number.isFinite), name + " nonfinite output");
    // The API returns -1 for a missing table. This also catches tables leaked
    // by UDO deinit, which short sound checks would not expose.
    for (const id of ids) assert.equal(api.csoundTableLength(engine, id), -1, name + " leaked temporary table " + id);
    return audio;
  } finally { api.csoundDestroy(engine); }
}
for (const voice of voices) {
  const fresh = await render(voice.name);
  assert.ok(peak(fresh) > .01 && peak(fresh) < 1, voice.name + " reference output");
  assert.deepEqual(await render(voice.name, { other: true }), fresh, voice.name + " overlapping notes share state");
  const reused = await render(voice.name, { seconds: 4, schedule: "schedule(1, 0, 1.5)\nschedule(1, 2, 1.5)" });
  assert.deepEqual(reused.subarray(48000 * 4), fresh, voice.name + " warm reuse differs");
  const half = await render(voice.name, { scale: .5 });
  assert.deepEqual(half, fresh.map(x => x * .5), voice.name + " inconsistent 0dbfs");
  const fixed = await render(voice.name, { shape: 0 });
  const changing = await render(voice.name, { changing: true });
  assert.deepEqual(changing.subarray(0, 48000 * 2 * .3), fixed.subarray(0, 48000 * 2 * .3));
  assert.notDeepEqual(changing, fixed, voice.name + " ignored live shape edits");
  const drone = await render(voice.name, { seconds: 16,
    schedule: "schedule(1, 0, -1)\nschedule(2, 15, 0)" });
  assert.ok(peak(drone) < 1);
  assert.ok(peak(drone.subarray(48000 * 28, 48000 * 29)) > .001, voice.name + " default held voice died");
  for (const [rate, block] of [[44100, 32], [96000, 128], [48000, 256]]) {
    const held = await render(voice.name, { rate, block, seconds: 16, extreme: true,
      schedule: "schedule(1, 0, -1)\nschedule(2, 15, 0)" });
    assert.ok(peak(held) < 4, voice.name + " unbounded motion");
    assert.ok(peak(held.subarray(rate * 2 * 14, rate * 2 * 14.5)) > .0001, voice.name + " held voice died");
  }
  // Repeated activation must release every set of moving tables; the final
  // note must still sound and end without stale state or runaway output.
  const repeated = await render(voice.name, { seconds: 32.5,
    schedule: Array.from({length: 80}, (_, i) => `schedule(1, ${i * .4}, .1)`).join("\n") });
  assert.ok(peak(repeated.subarray(48000 * 60)) > .01);
  console.log(`PASS: ${voice.name}: private tables, cleanup, reuse, held edits, long holds, rates/blocks, gain scaling.`);
}
