// Render an original steady-pitch source for examples/analysed-brass.mal.
import assert from "node:assert/strict";
import { resolve } from "node:path";
import { api, root, support } from "./catalog-audio";
import { pcmWave } from "./audio-level";

const engine = api.csoundCreate();
try {
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
  const source = await Bun.file(resolve(root, "src/instruments/additive/formant.orc")).text();
  assert.equal(api.csoundCompileOrc(engine, `sr=48000\nksmps=64\nnchnls=2\n0dbfs=1\n${support}\n${source}
instr 1
  center:k linseg 700, 0.2, 1700, 1.8, 900, 1, 500
  left:a, right:a = PanaeolusFormant(60, 0, center, 550, .5, 4, 4.8, 0, .03, .4, .8, .25, 0, 0, 1)
  outs left, right
endin
schedule(1, 0, 3)`), 0);
  assert.equal(api.csoundStart(engine), 0);
  const audio = new Float64Array(48000 * 3 * 2);
  for (let offset = 0; offset < audio.length; offset += 128) {
    assert.equal(api.csoundPerformKsmps(engine), 0);
    audio.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), 128), offset);
  }
  await Bun.write(resolve(root, "dist/hetro-brass-source.wav"), pcmWave(audio, 48000));
} finally { api.csoundDestroy(engine); }
