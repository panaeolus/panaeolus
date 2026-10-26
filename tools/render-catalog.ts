import { resolve } from "node:path";
import { catalog, render, root } from "./catalog-audio";
import { loudness, peak, pcmWave } from "./audio-level";
for (const voice of catalog) {
  const audio = await render(voice);
  console.log(`${voice.name}: ${loudness(audio, 48000).toFixed(2)} LUFS, peak ${peak(audio).toFixed(4)} at :db 0`);
  if (process.argv.includes("--calibrate")) {
    const path = resolve(root, voice.source);
    const source = await Bun.file(path).text();
    const current = Number(source.match(/Trim@global:i init ([-\d.]+)/)![1]);
    const adjustment = -18 - loudness(audio, 48000);
    if (!Number.isFinite(adjustment) || peak(audio) * 10 ** (adjustment / 20) >= 1)
      throw new Error(`${voice.name}: adjust the default sound before calibrating; it cannot reach the reference without clipping`);
    const trim = (current + adjustment).toFixed(3);
    await Bun.write(path, source.replace(/(Trim@global:i init )[-\d.]+/, `$1${trim}`));
  } else {
    await Bun.write(resolve(root, `dist/${voice.name}-preview.wav`), pcmWave(audio, 48000));
  }
}
