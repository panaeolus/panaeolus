import { expect, test } from "bun:test";
import { analysisModule, analysisPath, parseHetro } from "../../tools/hetro-module";
const source = "HETRO 1\n-1,0,0,10,10000,800,0,32767\n-2,0,261,800,262,32767\n";
test("validates and canonicalizes portable HETRO tracks", () => {
  const a = parseHetro(source);
  expect(a).toEqual({ text: source, partials: 1, duration: .8, top: 262 });
  expect(parseHetro(source.replaceAll("\n", "\r\n\r\n"))).toEqual(a);
  expect(analysisPath(a)).toMatch(/^panaeolus-hetro-[a-f0-9]{64}\.het$/);
  expect(analysisPath(parseHetro(source.replace("10000", "12000")))).not.toBe(analysisPath(a));
});
test("drops HETRO's silent one-point tracks", () => {
  const a = parseHetro(source.replace("HETRO 1", "HETRO 2") + "-1,0,0,32767\n-2,0,0,32767\n");
  expect(a.partials).toBe(1);
  expect(a.text).toBe(source);
});
test("rejects malformed tracks before they reach the native loader", () => {
  for (const bad of [source.repeat(5000), source.replace("HETRO 1", "HETRO 100"),
    source.replace("10,10000", "0,10000"), source.replace("800,0", "800,1"),
    source.replace("800,262", "700,262"), source.replace("10000", "40000"),
    source.replace("10,10000", "-1,10000"), source.replace("32767", "32766"),
    source.replace("800,0", "30001,0"), source.replace("10000", "0"),
    source.replace("-2,0,261,800,262", "-2,0,261"), source.replace("10000", "NaN")])
    expect(() => parseHetro(bad)).toThrow();
});
test("module contains its asset and rejects code in names or metadata", () => {
  const a = parseHetro(source), module = analysisModule(a, "brass", 60, -8);
  expect(module).toContain("(def brass (panaeolus.instruments.additive/analysis-voice");
  expect(module).toContain("PanaeolusInstallAnalysis");
  expect(module).toContain(Buffer.from(source).toString("base64"));
  expect(() => analysisModule(a, 'x) (throw "bad")', 60, 0)).toThrow();
  for (const name of ["nil", "true", "false"]) expect(() => analysisModule(a, name, 60, 0)).toThrow();
  expect(() => analysisModule(a, "x", NaN, 0)).toThrow();
  expect(() => analysisModule(a, "x", 60, Infinity)).toThrow();
});
