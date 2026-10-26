import assert from "node:assert/strict";
import { catalog, render } from "../tools/catalog-audio";
import { loudness, peak } from "../tools/audio-level";
for (const voice of catalog) {
  const reference = await render(voice);
  assert.ok(Math.abs(loudness(reference, 48000) + 18) < 0.1, voice.name + " level reference");
  assert.ok(peak(reference) < 1, voice.name + " reference clips");
  const quiet = [...voice.reference]; quiet[1] = -6;
  assert.ok(Math.abs(loudness(await render(voice, quiet), 48000) - loudness(reference, 48000) + 6) < 0.01);
  const mute = [...voice.reference]; mute[mute.length - 1] = 0;
  assert.equal(peak(await render(voice, mute, [voice.reference[0]])), 0);
  // Exercise each sound control independently at its documented boundaries.
  // Exclude intentional gain boosts and silence settings from headroom checks.
  const keys = Object.keys(voice.ranges);
  for (const [index, key] of keys.entries()) {
    if (["db", "amp", "pan"].includes(key)) continue;
    for (const value of voice.ranges[key].slice(0, 2)) {
      const controls = [...voice.reference]; controls[index] = value;
      const audio = await render(voice, controls, [controls[0]], 44100, 0.5);
      assert.ok(peak(audio) < 8, `${voice.name} :${key} ${value} unstable output`);
    }
  }
  console.log(`PASS: ${voice.name}: levels, mute, control extremes, 44.1/48 kHz.`);
}
