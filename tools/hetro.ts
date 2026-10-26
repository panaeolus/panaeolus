import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve, join, extname } from "node:path";
import { parseArgs } from "node:util";
import { parseHetro, analysisModule, analysisPath } from "./hetro-module";
import { loudness, peak } from "./audio-level";
import { csoundString } from "./build";

const { values, positionals } = parseArgs({ args: process.argv.slice(2), allowPositionals: true, options: {
  name: { type: "string", default: "resynth" }, root: { type: "string", default: "60" },
  partials: { type: "string", default: "24" }, points: { type: "string", default: "128" },
  channel: { type: "string", default: "1" }, begin: { type: "string", default: "0" },
  duration: { type: "string", default: "4" }, help: { type: "boolean" },
} });
if (values.help || positionals.length !== 2) {
  console.log("Usage: bun run hetro input.wav output.mal --name resynth --root 60 [--partials 24 --points 128 --channel 1 --begin 0 --duration 4]\nRequires native csound on PATH (or CSOUND_BIN), plus bun run build. Duration: 0.05–30 seconds. Input should have a steady pitch matching --root. An existing text .het file skips native analysis.");
  process.exit(values.help ? 0 : 1);
}
const root = Number(values.root), partials = Number(values.partials), points = Number(values.points);
const channel = Number(values.channel), begin = Number(values.begin), duration = Number(values.duration);
for (const [name, value, lo, hi, integer] of [
  ["root", root, 24, 96, false], ["partials", partials, 1, 64, true],
  ["points", points, 16, 512, true], ["channel", channel, 1, 64, true],
  ["begin", begin, 0, 86400, false], ["duration", duration, .05, 30, false],
] as const) if (!Number.isFinite(value) || value < lo || value > hi || (integer && !Number.isInteger(value)))
  throw new Error(`--${name} must be ${integer ? "an integer " : ""}from ${lo} to ${hi}`);
if (points > duration * 1000) throw new Error("Too many breakpoints for millisecond resolution; lower --points");
if (!/^[a-z][a-z0-9-]*$/.test(values.name!) || ["nil", "true", "false"].includes(values.name!))
  throw new Error("Invalid --name (use a lowercase symbol, excluding nil, true, and false)");
const output = resolve(positionals[1]);
if (await Bun.file(output).exists()) throw new Error("Output already exists; choose a new module path");
const temp = await mkdtemp(join(tmpdir(), "panaeolus-hetro-"));
try {
  const target = join(temp, "analysis.het");
  const input = resolve(positionals[0]);
  const existing = extname(input).toLowerCase() === ".het";
  if (!existing) {
    const hz = 440 * 2 ** ((root - 69) / 12);
    if (hz * partials >= 19845)
      throw new Error(`Use at most ${Math.floor(19845 / hz)} partials at MIDI ${root} to stay within the 44.1 kHz playback band`);
    const command = Bun.spawn([process.env.CSOUND_BIN || "csound", "-U", "hetro", "-X", `-f${hz}`, `-h${partials}`,
      `-n${points}`, `-c${channel}`, `-b${begin}`, `-d${duration}`, input, target],
      { stdout: "inherit", stderr: "inherit" });
    if (await command.exited !== 0) throw new Error("HETRO analysis failed");
  }
  const file = Bun.file(existing ? input : target);
  if (file.size > 262144) throw new Error("Analysis exceeds 256 KiB; reduce --partials or --points");
  const analysis = parseHetro(await file.text());
  if (analysis.top >= 19845) throw new Error("Analysis reaches the 44.1 kHz playback limit; use fewer --partials or a lower source pitch");
  // Measure the resynthesis, not its input WAV: HETRO changes the spectrum and
  // envelope. Fix this gain in the module; no meter runs during performance.
  Object.assign(globalThis, { self: globalThis, window: globalThis });
  const { libcsound } = await import("../dist/web/csound.js");
  const api = await libcsound(), engine = api.csoundCreate();
  let trim: number;
  try {
    for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
    const path = analysisPath(analysis);
    api.writeFile(engine, path, new TextEncoder().encode(analysis.text));
    const support = (await Promise.all(["common/levels", "synths/voices", "common/catalog", "additive/adsyn"].map(
      file => Bun.file(new URL(`../src/instruments/${file}.orc`, import.meta.url)).text()))).join("\n");
    const code = `sr=48000\nksmps=64\nnchnls=2\n0dbfs=1\n${support}\ninstr 1\n` +
      `left:a, right:a = PanaeolusAdsyn(${csoundString(path)}, ${root}, 0, ${analysis.top}, ${root}, 0, 1, .005, .01, 1, .05, 0, 1)\nouts left, right\nendin\nschedule(1, 0, ${analysis.duration})`;
    if (api.csoundCompileOrc(engine, code) !== 0 || api.csoundStart(engine) !== 0) throw new Error("Could not render analysis");
    const audio = new Float64Array(Math.ceil(analysis.duration * 48000 / 64) * 128);
    for (let offset = 0; offset < audio.length; offset += 128) {
      if (api.csoundPerformKsmps(engine) !== 0) throw new Error("Analysis render failed");
      audio.set(new Float64Array(api.getMemory().buffer, api.csoundGetSpout(engine), 128), offset);
    }
    const level = loudness(audio, 48000), maximum = peak(audio);
    if (!Number.isFinite(level) || maximum <= 0) throw new Error("Analysis rendered silence");
    trim = Math.min(24, -18 - level, -3 - 20 * Math.log10(maximum));
    console.log(`Resynthesis: ${(level + trim).toFixed(2)} LUFS at :db 0; fixed trim ${trim.toFixed(3)} dB.`);
  } finally { api.csoundDestroy(engine); }
  await Bun.write(output, analysisModule(analysis, values.name!, root, Number(trim.toFixed(3))));
  console.log(`Wrote ${output}\nLoad it before playback, then call (${values.name}) or (${values.name} :every [1] :note [${root} ${root + 7}]).`);
} finally { await rm(temp, { recursive: true, force: true }); }
