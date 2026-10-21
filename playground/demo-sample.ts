export const DEMO_SAMPLE_PATH = "panaeolus-demo.wav";
export const MAX_SAMPLE_BYTES = 20 * 1024 * 1024;
const extensions = new Set(["wav", "wave", "aif", "aiff", "aifc", "flac"]);

export function sampleExtension(name: string, size: number): string {
  const extension = name.split(".").pop()?.toLowerCase() ?? "";
  if (!extensions.has(extension)) throw new Error("Choose a WAV, AIFF, or FLAC sample.");
  if (!Number.isSafeInteger(size) || size <= 0 || size > MAX_SAMPLE_BYTES) {
    throw new Error("Choose a nonempty sample up to 20 MiB.");
  }
  return extension;
}

export function importedSamplePath(name: string, size: number, token: string): string {
  const extension = sampleExtension(name, size);
  if (!/^[a-zA-Z0-9-]+$/.test(token)) throw new Error("Invalid sample identifier.");
  // File names never become paths in the engine, and each import keeps its own copy.
  return `panaeolus-import-${token}.${extension}`;
}

export function sampleLoadForm(path: string): string {
  return `(sampler/load ${JSON.stringify(path)})`;
}

export function createDemoSample(): Uint8Array {
  const rate = 48000;
  const frames = rate * 2;
  const channels = 2;
  const bytes = new Uint8Array(44 + frames * channels * 2);
  const view = new DataView(bytes.buffer);
  const text = (offset: number, value: string) => {
    for (let index = 0; index < value.length; index++) view.setUint8(offset + index, value.charCodeAt(index));
  };
  text(0, "RIFF");
  view.setUint32(4, bytes.length - 8, true);
  text(8, "WAVE");
  text(12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, channels, true);
  view.setUint32(24, rate, true);
  view.setUint32(28, rate * channels * 2, true);
  view.setUint16(32, channels * 2, true);
  view.setUint16(34, 16, true);
  text(36, "data");
  view.setUint32(40, frames * channels * 2, true);
  const pitches = [110, 220, 165, 330];
  for (let frame = 0; frame < frames; frame++) {
    const beat = Math.floor(frame / (rate / 2));
    const time = (frame % (rate / 2)) / rate;
    // Four distinct hits leave silence between slices and at the loop boundary.
    const envelope = Math.min(1, time / 0.003) * Math.exp(-time * 18) * Math.max(0, 1 - time / 0.4);
    const tone = 0.45 * envelope * Math.sin(2 * Math.PI * pitches[beat] * time);
    const pan = beat % 2 ? 0.65 : 0.35;
    view.setInt16(44 + frame * 4, Math.round(tone * Math.sqrt(1 - pan) * 32767), true);
    view.setInt16(46 + frame * 4, Math.round(tone * Math.sqrt(pan) * 32767), true);
  }
  return bytes;
}
