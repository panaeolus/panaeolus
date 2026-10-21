export const sampleRate = 48000;
export const bpm = 128;
export const duration = 30;
export const beat = 60 / bpm;

export const setup = `(require '[panaeolus.instrument :as p]
         '[panaeolus.sampler :as sampler]
         '[panaeolus.fx :refer [flanger bbcut]])
(defn shot [file id]
  (p/with-defaults (sampler/load file :id id) :cross 0))
(def kick (shot "samples/kick.wav" :kick))
(def clap (shot "samples/clap.wav" :clap))
(def hat (shot "samples/hat.wav" :hat))
(def bass (shot "samples/bass.wav" :bass))
(def chord (shot "samples/chord.wav" :chord))
(def lead (shot "samples/lead.wav" :lead))`;

export const timeline = {
  title: "Final demo Panaeolus", bpm, duration, sampleRate,
  setup,
  setupDisplay: ";; samples ready: kick, clap, hat, bass, chord, lead",
  cues: [
    { start: 0.12, eval: 2 * beat, label: "Kick",
      code: `(p/tempo! 128)
(kick :every [1] :amp 0.85 :offset 0)`,
      log: "128 BPM / kick loop started" },
    { start: 2.05, eval: 8 * beat, label: "Percussion",
      code: `(clap :every [2] :offset 1 :amp 0.42)
(hat :every [1] :offset 0.5 :amp 0.27)`,
      log: "clap on 2 + 4 / hats between beats" },
    { start: 5.3, eval: 16 * beat, label: "Bass",
      code: `(bass :every [0.75 0.25 0.5 0.5] :offset 0
  :dur 0.19 :rate [1 2 1 1.4983] :amp 0.52)`,
      log: "bass / 4 pitches over 4 durations" },
    { start: 9.0, eval: 24 * beat, label: "Chords",
      code: `(chord :every [0.5 1 0.5 2] :offset 0.5
  :dur 0.3 :rate [1 1 1.3348 1.4983] :amp 0.32)`,
      log: "minor seventh stabs / half-beat offset" },
    { start: 12.75, eval: 32 * beat, label: "Melody",
      code: `(hat :every [0.5] :amp [0.13 0.28] :offset 0)
(lead :every [0.75 0.75 0.5] :offset 0
  :dur 0.15 :rate [2 2.3784 2.9966 2.6697]
  :amp [0.2 0.14 0.16])`,
      log: "lead / 4 pitches against 3 accents" },
    { start: 16.1, eval: 40 * beat, label: "Breakdown",
      code: `(p/stop! kick)
(p/stop! bass)
(chord :every [1] :dur 0.35 :amp 0.24
  :fx (flanger :rate 0.35 :depth 0.003))`,
      log: "kick + bass stopped / flanger on chords" },
    { start: 19.7, eval: 48 * beat, label: "Drop",
      code: `(kick :every [1] :amp 0.85 :offset 0)
(bass :every [0.5 0.25 0.25 1] :offset 0
  :dur 0.18 :rate [1 2 1.4983 1.1892] :amp 0.55)
(chord :every [0.5 1 0.5 2] :offset 0.5
  :dur 0.3 :rate [1 1 1.3348 1.4983] :amp 0.32)`,
      log: "drums + bass return / chords dry again" },
    { start: 23.6, eval: 56 * beat, label: "Cut",
      code: `(hat :every [0.25] :amp [0.1 0.18 0.1 0.28]
  :fx (bbcut :bpm 128 :subdiv 8 :repeats 2))`,
      log: "hat loop replaced / stereo breakbeat cuts" },
    { start: 27.25, eval: 62 * beat, label: "Finish",
      code: `(p/stop-all!)
(chord :dur 0.8 :rate 0.5 :amp 0.45)`,
      log: "loops stopped / final chord" },
  ],
};

function noise(seed: number) {
  let state = seed >>> 0;
  return () => {
    state = (Math.imul(state, 1664525) + 1013904223) >>> 0;
    return state / 2147483648 - 1;
  };
}

const tau = 2 * Math.PI;
const fade = (time: number, length: number, attack = 0.001) =>
  Math.min(1, time / attack) * Math.min(1, Math.max(0, (length - time) / 0.012));

// The source samples are original sounds made here, before Panaeolus plays
// anything. The performance itself uses only sampler instruments.
export function sourceSamples(): Record<string, Float64Array> {
  const result: Record<string, Float64Array> = {};
  function sample(name: string, length: number, make: (time: number, frame: number, channel: number) => number) {
    const frames = Math.round(length * sampleRate);
    const audio = new Float64Array(frames * 2);
    for (let frame = 0; frame < frames; frame++) {
      const time = frame / sampleRate;
      for (let channel = 0; channel < 2; channel++) {
        audio[frame * 2 + channel] = make(time, frame, channel) * fade(time, length);
      }
    }
    let peak = 0;
    for (const value of audio) peak = Math.max(peak, Math.abs(value));
    if (!Number.isFinite(peak) || peak <= 0) throw new Error(`Invalid source sample: ${name}`);
    for (let index = 0; index < audio.length; index++) audio[index] *= 0.88 / peak;
    // Both ends are zero so one-shot envelopes and table guards stay quiet.
    audio[0] = audio[1] = audio[audio.length - 1] = audio[audio.length - 2] = 0;
    result[name] = audio;
  }
  const kickNoise = noise(17);
  let kickRandom = 0;
  sample("kick", 0.43, (t, _frame, channel) => {
    if (!channel) kickRandom = kickNoise();
    const phase = tau * (47 * t + 95 * 0.023 * (1 - Math.exp(-t / 0.023)));
    return Math.tanh(1.35 * Math.sin(phase)) * Math.exp(-t * 9)
      + 0.18 * kickRandom * Math.exp(-t * 350);
  });
  const clapNoise = [noise(90), noise(91)];
  const clapLow = [0, 0];
  sample("clap", 0.24, (t, _frame, channel) => {
    const random = clapNoise[channel]();
    clapLow[channel] += 0.09 * (random - clapLow[channel]);
    const burst = [0, 0.011, 0.024].reduce((sum, start) => sum + (t >= start ? Math.exp(-(t - start) * 190) : 0), 0);
    return (random - clapLow[channel]) * (0.6 * burst + 0.25 * Math.exp(-t * 23));
  });
  const hatNoise = [noise(400), noise(401)];
  const hatLow = [0, 0];
  sample("hat", 0.16, (t, _frame, channel) => {
    const random = hatNoise[channel]();
    hatLow[channel] += 0.2 * (random - hatLow[channel]);
    const metal = [3531, 4717, 6209, 8243].reduce((sum, hz) => sum + Math.sin(tau * hz * t), 0) / 4;
    return ((random - hatLow[channel]) * 0.75 + metal * 0.25) * Math.exp(-t * 37);
  });
  sample("bass", 0.4, (t) => {
    const hz = 43.65352892912549;
    const brightness = 3 + 10 * Math.exp(-t * 28);
    let tone = 0;
    for (let harmonic = 1; harmonic <= 20; harmonic++) {
      tone += Math.sin(tau * hz * harmonic * t) * Math.exp(-harmonic / brightness) / harmonic;
    }
    return Math.tanh(tone * 1.8) * Math.exp(-t * 5.5) * Math.min(1, t / 0.003);
  });
  sample("chord", 0.55, (t, _frame, channel) => {
    let tone = 0;
    const detune = channel ? 1.0017 : 0.9983;
    for (const hz of [174.614116, 207.652349, 261.625565, 311.126984]) {
      const brightness = 2 + 5 * Math.exp(-t * 18);
      for (let harmonic = 1; harmonic <= 14; harmonic++) {
        tone += Math.cos(tau * hz * detune * harmonic * t + harmonic * 0.7)
          * Math.exp(-harmonic / brightness) / harmonic;
      }
    }
    return tone * Math.exp(-t * 8.5) * Math.min(1, t / 0.004);
  });
  sample("lead", 0.55, (t, _frame, channel) => {
    const hz = 174.614116 * (channel ? 1.0005 : 0.9995);
    const phase = tau * hz * t;
    const tone = Math.sin(phase + 1.5 * Math.sin(2 * phase) * Math.exp(-t * 22))
      + 0.23 * Math.sin(3 * phase) * Math.exp(-t * 10);
    return tone * Math.exp(-t * 10) * Math.min(1, t / 0.002);
  });
  return result;
}

export function wave(audio: Float64Array): Uint8Array {
  const bytes = new Uint8Array(44 + audio.length * 2);
  const view = new DataView(bytes.buffer);
  const word = (offset: number, value: string) => {
    for (let index = 0; index < value.length; index++) view.setUint8(offset + index, value.charCodeAt(index));
  };
  word(0, "RIFF"); view.setUint32(4, bytes.length - 8, true); word(8, "WAVE");
  word(12, "fmt "); view.setUint32(16, 16, true); view.setUint16(20, 1, true);
  view.setUint16(22, 2, true); view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 4, true); view.setUint16(32, 4, true);
  view.setUint16(34, 16, true); word(36, "data"); view.setUint32(40, audio.length * 2, true);
  for (let index = 0; index < audio.length; index++) {
    const value = audio[index];
    if (!Number.isFinite(value) || Math.abs(value) > 1) throw new Error(`Invalid WAV amplitude at ${index}: ${value}`);
    view.setInt16(44 + index * 2, Math.round(value * 32767), true);
  }
  return bytes;
}
