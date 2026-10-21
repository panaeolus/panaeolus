import { expect, test } from "bun:test";
import { bpm, duration, sampleRate, sourceSamples, timeline, wave } from "../../examples/disco-demo/demo";

test("disco cue times fit sixteen bars and finish their typing before evaluation", () => {
  expect(duration * bpm / 60).toBe(64);
  expect(sampleRate * duration).toBe(1440000);
  let previous = 0;
  for (const cue of timeline.cues) {
    expect(cue.start).toBeGreaterThanOrEqual(previous);
    expect(cue.eval).toBeGreaterThan(cue.start);
    expect(cue.eval).toBeLessThan(duration);
    expect(cue.code.split("\n").every(line => line.length <= 62)).toBe(true);
    previous = cue.eval;
  }
  expect(timeline.cues.at(-1)?.code).toContain("(p/stop-all!)");
});

test("demo samples are deterministic finite stereo WAVs with silent edges", () => {
  const first = sourceSamples();
  const second = sourceSamples();
  expect(Object.keys(first)).toEqual(["kick", "clap", "hat", "bass", "chord", "lead"]);
  for (const [name, audio] of Object.entries(first)) {
    expect(audio).toEqual(second[name]);
    expect(audio[0]).toBe(0);
    expect(audio.at(-1)).toBe(0);
    const bytes = wave(audio);
    const view = new DataView(bytes.buffer);
    expect(view.getUint32(24, true)).toBe(sampleRate);
    expect(view.getUint16(22, true)).toBe(2);
    expect(view.getUint32(40, true)).toBe(audio.length * 2);
  }
});

test("bundled demo keeps its cue source, original samples, and thirty-second audio together", async () => {
  const directory = new URL("../../examples/disco-demo/", import.meta.url);
  expect(await Bun.file(new URL("timeline.json", directory)).json()).toEqual(timeline);
  for (const [name, audio] of Object.entries(sourceSamples())) {
    const bytes = new Uint8Array(await Bun.file(new URL(`samples/${name}.wav`, directory)).arrayBuffer());
    expect(bytes).toEqual(wave(audio));
  }
  const bytes = await Bun.file(new URL("disco-demo.wav", directory)).arrayBuffer();
  const view = new DataView(bytes);
  expect(bytes.byteLength).toBe(44 + sampleRate * duration * 4);
  expect(view.getUint16(20, true)).toBe(1);
  expect(view.getUint16(22, true)).toBe(2);
  expect(view.getUint32(24, true)).toBe(sampleRate);
  expect(view.getUint16(34, true)).toBe(16);
  let peak = 0;
  let energy = 0;
  let first = -1;
  for (let index = 44; index < bytes.byteLength; index += 2) {
    const value = view.getInt16(index, true) / 32767;
    if (first < 0 && value !== 0) first = Math.floor((index - 44) / 4);
    peak = Math.max(peak, Math.abs(value));
    energy += value * value;
  }
  expect(first / sampleRate).toBeGreaterThanOrEqual(timeline.cues[0].eval);
  expect(first / sampleRate).toBeLessThan(timeline.cues[0].eval + 0.02);
  expect(peak).toBeGreaterThan(0.87);
  expect(peak).toBeLessThan(0.89);
  expect(Math.sqrt(energy / (sampleRate * duration * 2))).toBeGreaterThan(0.08);
  expect(view.getInt16(bytes.byteLength - 4, true)).toBe(0);
  expect(view.getInt16(bytes.byteLength - 2, true)).toBe(0);
});
