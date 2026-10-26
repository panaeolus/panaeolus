import assert from "node:assert/strict";
import { resolve } from "node:path";

Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const api = await libcsound();
const source = await Bun.file(resolve(import.meta.dir, "../src/samples.orc")).text();
// sampler.ts covers the MAL loader. Isolate the native exporter here so the
// extreme floating-point fixtures never become instrument voices or audio.
const loader = source.indexOf("opcode PanaeolusSampleInfo(");
const exporter = source.indexOf("opcode PanaeolusSampleExportFailure(");
assert.ok(loader >= 0 && exporter > loader);
const nativeSource = await Bun.file(new URL("../src/instruments/samplers/channels.orc", import.meta.url)).text() +
  source.slice(0, loader) + source.slice(exporter);

function wave(data: number[], channels: number, rate: number, bits: 32 | 64) {
  const bytes = Buffer.alloc(44 + data.length * bits / 8);
  bytes.write("RIFF");
  bytes.writeUInt32LE(bytes.length - 8, 4);
  bytes.write("WAVEfmt ", 8);
  bytes.writeUInt32LE(16, 16);
  bytes.writeUInt16LE(3, 20);
  bytes.writeUInt16LE(channels, 22);
  bytes.writeUInt32LE(rate, 24);
  bytes.writeUInt32LE(rate * channels * bits / 8, 28);
  bytes.writeUInt16LE(channels * bits / 8, 32);
  bytes.writeUInt16LE(bits, 34);
  bytes.write("data", 36);
  bytes.writeUInt32LE(bytes.length - 44, 40);
  data.forEach((value, index) => bits === 64
    ? bytes.writeDoubleLE(value, 44 + index * 8)
    : bytes.writeFloatLE(value, 44 + index * 4));
  return bytes;
}

const fixtures = [
  { channels: 1, rate: 24000, bits: 64 as const, data: [
    0, -0, Number.MAX_VALUE, -Number.MAX_VALUE, Number.MIN_VALUE, -Number.MIN_VALUE,
    2.2250738585072014e-308, -2.2250738585072014e-308, Math.PI, -Math.PI, 0.25, -0.75,
  ] },
  { channels: 2, rate: 32000, bits: 32 as const,
    data: Array.from({ length: 16000 }, (_, index) => Math.sin(index * 0.199) * 0.88) },
];

for (const scale of [1, 0.5]) {
  const engine = api.csoundCreate();
  const evaluate = (code: string) => api.csoundEvalCode(engine, code);
  const header = `sr = 48000\nksmps = 64\nnchnls = 2\n0dbfs = ${scale}\n`;
  try {
    for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
    assert.equal(api.csoundCompileOrc(engine, header + nativeSource), 0);
    assert.equal(api.csoundStart(engine), 0);
    const tables = fixtures.map((fixture, index) => {
      const path = `sample-asset-${scale}-${index}.wav`;
      api.writeFile(engine, path, wave(fixture.data, fixture.channels, fixture.rate, fixture.bits));
      const number = evaluate(`sample:i ftgen 0, 0, 0, -1, "${path}", 0, 0, 0
        panaeolusSampleTables[${index}] = sample
        panaeolusSampleFrames[${index}] = nsamp(sample)
        panaeolusSampleChannels[${index}] = ftchnls(sample)
        panaeolusSampleRates[${index}] = ftsr(sample)
        return sample`);
      assert.ok(number > 0);
      api.unlink(engine, path);
      return { number, data: api.csoundTableCopyOut(engine, number).slice(0, fixture.data.length) };
    });
    // The take's snapshot must exclude samples loaded after recording ended.
    evaluate("panaeolusSampleCount = 3\npanaeolusTakeSamples = 2\nreturn 0");
    const exportFiles = () => {
      assert.equal(evaluate('files:S init PanaeolusSampleFiles()\nchnset files, "files"\nreturn 0'), 0);
      return api.csoundGetStringChannel(engine, "files");
    };
    const previous = exportFiles();
    const files = exportFiles();
    assert.notEqual(files, previous, "Each export needs fresh embedded filenames");
    assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_sample_export_status"), 0);
    const assets = [...files.matchAll(/<CsFileB filename="([^"]+)">\s*([A-Za-z0-9+/=]+)\s*<\/CsFileB>/g)];
    assert.equal(assets.length, fixtures.length);
    assets.forEach((asset, index) => {
      const decoded = Buffer.from(asset[2], "base64");
      const fixture = fixtures[index];
      assert.equal(decoded.length, 44 + fixture.data.length * 8);
      assert.equal(decoded.readUInt16LE(22), fixture.channels);
      assert.equal(decoded.readUInt32LE(24), fixture.rate);
      assert.equal(decoded.readUInt16LE(34), 64);
      for (let sample = 0; sample < fixture.data.length; sample++) {
        assert.equal(decoded.readDoubleLE(44 + sample * 8), tables[index].data[sample] / scale,
          `Embedded sample ${index}:${sample}, 0dbfs=${scale}`);
      }
    });
    assert.equal(evaluate('orc:S init PanaeolusSampleOrchestra()\nchnset orc, "orc"\nreturn 0'), 0);
    const orchestra = api.csoundGetStringChannel(engine, "orc");
    const replay = api.csoundCreate();
    try {
      assert.equal(api.csoundCompileCSD(replay, `<CsoundSynthesizer>
<CsOptions>
-n -d -m0
</CsOptions>
${files}<CsInstruments>
${header}${orchestra}</CsInstruments>
<CsScore>
f 0 0.01
</CsScore>
</CsoundSynthesizer>`), 0);
      // Let Csound extract and read CsFileB itself. Rewriting its files through
      // the JS API would hide the WASI multi-buffer file-size regression.
      assert.equal(api.csoundStart(replay), 0);
      tables.forEach((table, index) => {
        assert.equal(api.csoundEvalCode(replay, `return ftsr(${table.number})`), fixtures[index].rate);
        assert.equal(api.csoundEvalCode(replay, `return ftchnls(${table.number})`), fixtures[index].channels);
        assert.equal(api.csoundEvalCode(replay, `return nsamp(${table.number})`),
          fixtures[index].data.length / fixtures[index].channels);
        assert.deepEqual(api.csoundTableCopyOut(replay, table.number).slice(0, table.data.length), table.data);
      });
    } finally {
      api.csoundDestroy(replay);
    }
    evaluate("panaeolusSampleFrames[0] = 10000000\nreturn 0");
    assert.equal(exportFiles(), "", "An oversized export must fail before allocating its text");
    assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_sample_export_status"), -1);
    assert.match(api.csoundGetStringChannel(engine, "__panaeolus_error"), /64 MiB/);
  } finally {
    api.csoundDestroy(engine);
  }
}
console.log("PASS: sample asset precision, signed zero, source rates/channels, chunk boundaries, standalone CSD replay, and export limit.");
