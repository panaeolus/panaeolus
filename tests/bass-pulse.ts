import assert from "node:assert/strict";
import { api, support } from "../tools/catalog-audio";

function render(bpm: number, division: number, changing = false) {
  const engine = api.csoundCreate();
  try {
    for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
    assert.equal(api.csoundCompileOrc(engine, `sr=48000\nksmps=64\nnchnls=2\n0dbfs=1\n${support}
instr 1
  age:k timeinsts
  tempo:k = ${changing ? "(age < 0.3 ? 120 : 240)" : bpm}
  wave:k = PanaeolusBassPulse(tempo, ${division}, 0.13, 1)
  signal:a upsamp wave
  outs signal, signal
endin
schedule(1, 0, 2)`), 0);
    assert.equal(api.csoundStart(engine), 0);
    const values: number[] = [];
    for (let block = 0; block < 1500; block++) {
      assert.equal(api.csoundPerformKsmps(engine), 0);
      values.push(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), 128)[0]);
    }
    return values;
  } finally { api.csoundDestroy(engine); }
}
const straight = render(120, 1);
assert.deepEqual(straight, render(60, .5), "Division is not beats per cycle");
assert.ok(straight.every(x => Number.isFinite(x) && x >= 0 && x <= 1));
function crossings(values: number[]) {
  const times: number[] = [];
  for (let i = 1; i < values.length; i++)
    if (values[i - 1] < .5 && values[i] >= .5) times.push(i / 750);
  return times;
}
const steady = crossings(straight);
assert.ok(steady.length >= 3);
for (let i = 1; i < steady.length; i++) assert.ok(Math.abs(steady[i] - steady[i - 1] - .5) <= 1 / 750);
const changed = render(120, 1, true);
let maxStep = 0;
for (let i = 1; i < changed.length; i++) maxStep = Math.max(maxStep, Math.abs(changed[i] - changed[i - 1]));
assert.ok(maxStep < .03, "Tempo edit reset the phasor");
const later = crossings(changed).filter(t => t > .5);
assert.ok(later.length >= 4);
for (let i = 1; i < later.length; i++) assert.ok(Math.abs(later[i] - later[i - 1] - .25) <= 1 / 750 + 1e-12);
console.log("PASS: bass pulse: beats per cycle, exact equivalent tempos, continuous tempo edits, doubled speed.");
