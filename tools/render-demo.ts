import assert from "node:assert/strict";
import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";
import { duration, sampleRate, setup, sourceSamples, timeline, wave } from "../examples/disco-demo/demo";

const root = resolve(import.meta.dir, "..");
const output = resolve(root, "examples/disco-demo");
await mkdir(resolve(output, "samples"), { recursive: true });
Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const api = await libcsound();
const engine = api.csoundCreate();
const blockSize = 64;
const samples = sourceSamples();

function evaluate(source: string) {
  api.csoundSetControlChannel(engine, "__panaeolus_status", -999);
  api.csoundSetStringChannel(engine, "__demo_source", `${source}\nnil`);
  api.csoundEvalCode(engine, 'return PanaeolusEval(chnget:S("__demo_source"))');
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), 0, source);
}

try {
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
  api.mkdir(engine, "samples");
  for (const [name, audio] of Object.entries(samples)) {
    const bytes = wave(audio);
    api.writeFile(engine, `samples/${name}.wav`, bytes);
    await Bun.write(resolve(output, "samples", `${name}.wav`), bytes);
  }
  const interpreter = await Bun.file(resolve(root, "dist/make-a-lisp.orc")).text();
  const panaeolus = await Bun.file(resolve(root, "dist/panaeolus.orc")).text();
  assert.equal(api.csoundCompileOrc(engine,
    `sr = ${sampleRate}\nksmps = ${blockSize}\nnchnls = 2\n0dbfs = 1\nseed 21102026\n${interpreter}\n${panaeolus}`), 0);
  assert.equal(api.csoundStart(engine), 0);
  evaluate(setup);
  // Set the score tempo before time advances, as well as in the visible cue.
  evaluate(`(p/tempo! ${timeline.bpm})`);
  const frames = sampleRate * duration;
  const audio = new Float64Array(frames * 2);
  let cue = 0;
  let peak = 0;
  for (let frame = 0; frame < frames; frame += blockSize) {
    while (cue < timeline.cues.length && frame >= Math.floor(timeline.cues[cue].eval * sampleRate / blockSize) * blockSize) {
      const event = timeline.cues[cue++];
      console.log(`DEMO ${event.eval.toFixed(4)}s: ${event.label}`);
      evaluate(event.code);
    }
    assert.equal(api.csoundPerformKsmps(engine), 0, `Engine stopped at frame ${frame}`);
    const block = new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), blockSize * 2);
    const count = Math.min(block.length, audio.length - frame * 2);
    for (let index = 0; index < count; index++) {
      const value = block[index];
      assert.ok(Number.isFinite(value), `Non-finite output at frame ${frame}`);
      audio[frame * 2 + index] = value;
      peak = Math.max(peak, Math.abs(value));
    }
  }
  assert.equal(cue, timeline.cues.length);
  assert.ok(peak > 0.1 && peak < 8, `Unexpected raw mix peak: ${peak}`);
  // One fixed gain keeps the arrangement's dynamics; no per-section gain rides.
  const gain = 0.88 / peak;
  let energy = 0;
  for (let frame = 0; frame < frames; frame++) {
    const tail = Math.min(1, (frames - 1 - frame) / (0.12 * sampleRate));
    for (let channel = 0; channel < 2; channel++) {
      const index = frame * 2 + channel;
      audio[index] *= gain * tail;
      energy += audio[index] * audio[index];
    }
  }
  const rms = Math.sqrt(energy / audio.length);
  assert.ok(rms > 0.03 && rms < 0.4, `Unexpected mix RMS: ${rms}`);
  await Bun.write(resolve(output, "disco-demo.wav"), wave(audio));
  await Bun.write(resolve(output, "timeline.json"), JSON.stringify(timeline, null, 2) + "\n");
  const source = `;; 128 BPM, sixteen bars. See timeline.json for evaluation times.\n` +
    `;; Run from this folder, or import the samples/ directory into the engine.\n` +
    `;; Evaluate the setup first, then select each cue at its marked time.\n${setup}\n\n` +
    timeline.cues.map(cue => `;; ${cue.eval.toFixed(4)} s — ${cue.label}\n${cue.code}`).join("\n\n") + "\n";
  await Bun.write(resolve(output, "performance.mal"), source);
  console.log(`Rendered ${duration}s, ${frames} stereo frames, peak 0.88, RMS ${rms.toFixed(4)}.`);
  console.log(resolve(output, "disco-demo.wav"));
} finally {
  api.csoundDestroy(engine);
}
