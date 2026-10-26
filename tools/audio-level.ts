// BS.1770 stereo K weighting, 400 ms blocks, 100 ms hops, -70 LUFS absolute
// and -10 LU relative gates. Short one-shots are padded to one block.
// This measures reference renders; it does not alter playback dynamics.
export function loudness(audio: Float64Array, sampleRate: number): number {
  const k = Math.tan(Math.PI * 1681.974450955533 / sampleRate);
  const q = 0.7071752369554196;
  const vh = 10 ** (3.999843853973347 / 20);
  const vb = vh ** 0.4996667741545416;
  const a0 = 1 + k / q + k * k;
  const shelf = [(vh + vb * k / q + k * k) / a0, 2 * (k * k - vh) / a0,
    (vh - vb * k / q + k * k) / a0, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0];
  const kh = Math.tan(Math.PI * 38.13547087602444 / sampleRate);
  const qh = 0.5003270373238773;
  const ah = 1 + kh / qh + kh * kh;
  const highpass = [1, -2, 1, 2 * (kh * kh - 1) / ah, (1 - kh / qh + kh * kh) / ah];
  const states = Array.from({ length: 2 }, () => [0, 0, 0, 0]);
  function filter(x: number, coefficients: number[], state: number[], offset: number) {
    const [b0, b1, b2, a1, a2] = coefficients;
    const y = b0 * x + state[offset];
    state[offset] = b1 * x - a1 * y + state[offset + 1];
    state[offset + 1] = b2 * x - a2 * y;
    return y;
  }
  const hop = Math.round(sampleRate * 0.1);
  const frames = Math.max(audio.length / 2, hop * 4);
  const hops = [0, 0, 0, 0];
  const powers: number[] = [];
  let sum = 0;
  for (let frame = 0; frame < frames; frame++) {
    for (let channel = 0; channel < 2; channel++) {
      const value = audio[frame * 2 + channel] ?? 0;
      if (!Number.isFinite(value)) throw new Error("Non-finite audio in loudness measurement");
      const filtered = filter(filter(value, shelf, states[channel], 0), highpass, states[channel], 2);
      sum += filtered * filtered;
    }
    if ((frame + 1) % hop === 0) {
      hops[((frame + 1) / hop - 1) % 4] = sum;
      sum = 0;
      if (frame + 1 >= hop * 4) powers.push(hops.reduce((a, b) => a + b) / (hop * 4));
    }
  }
  const average = (values: number[]) => values.reduce((a, b) => a + b, 0) / values.length;
  const absolute = 10 ** ((-70 + 0.691) / 10);
  const first = powers.filter(power => power >= absolute);
  if (!first.length) return -Infinity;
  const threshold = Math.max(absolute, average(first) * 0.1);
  return -0.691 + 10 * Math.log10(average(first.filter(power => power >= threshold)));
}

export function peak(audio: Float64Array): number {
  let value = 0;
  for (const sample of audio) {
    if (!Number.isFinite(sample)) throw new Error("Non-finite audio");
    value = Math.max(value, Math.abs(sample));
  }
  return value;
}

export function pcmWave(audio: Float64Array, sampleRate: number): Uint8Array {
  if (peak(audio) > 1) throw new Error("PCM render would clip");
  const result = new Uint8Array(44 + audio.length * 2);
  const view = new DataView(result.buffer);
  const tag = (offset: number, value: string) => result.set(new TextEncoder().encode(value), offset);
  tag(0, "RIFF"); view.setUint32(4, result.length - 8, true); tag(8, "WAVE");
  tag(12, "fmt "); view.setUint32(16, 16, true); view.setUint16(20, 1, true);
  view.setUint16(22, 2, true); view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 4, true); view.setUint16(32, 4, true);
  view.setUint16(34, 16, true); tag(36, "data"); view.setUint32(40, audio.length * 2, true);
  audio.forEach((sample, index) => view.setInt16(44 + index * 2, Math.round(sample * 32767), true));
  return result;
}
