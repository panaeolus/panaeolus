import { expect, test } from "bun:test";
import { loudness, pcmWave } from "../../tools/audio-level";

function sine(amplitude: number, rate = 48000, frames = rate * 2) {
  return Float64Array.from({ length: frames * 2 }, (_, index) =>
    amplitude * Math.sin(2 * Math.PI * 1000 * Math.floor(index / 2) / rate));
}

test("stereo 1 kHz calibration, gain steps, and channel energy", () => {
  for (const rate of [44100, 48000, 96000]) {
    const audio = sine(0.1, rate);
    expect(Math.abs(loudness(audio, rate) + 20)).toBeLessThan(0.05);
    expect(Math.abs(loudness(audio.map(x => x * 10 ** (-6 / 20)), rate) + 26)).toBeLessThan(0.05);
    const left = audio.map((value, index) => index % 2 ? 0 : value);
    expect(loudness(left, rate) - loudness(audio, rate)).toBeCloseTo(-3.0103, 3);
  }
});

test("silence gates out; short one-shots use a finite measurement window", () => {
  expect(loudness(new Float64Array(48000), 48000)).toBe(-Infinity);
  expect(loudness(sine(0.000001), 48000)).toBe(-Infinity);
  expect(Number.isFinite(loudness(sine(0.1, 48000, 4800), 48000))).toBe(true);
  const active = sine(0.1);
  const padded = new Float64Array(active.length * 3);
  padded.set(active);
  expect(Math.abs(loudness(active, 48000) - loudness(padded, 48000))).toBeLessThan(0.4);
});

test("rejects invalid audio and refuses to clip a preview", () => {
  expect(() => loudness(new Float64Array([NaN, 0]), 48000)).toThrow("Non-finite");
  expect(() => pcmWave(new Float64Array([1.01, 0]), 48000)).toThrow("clip");
});
