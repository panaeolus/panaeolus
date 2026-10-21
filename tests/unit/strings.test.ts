import { describe, expect, test } from "bun:test";
import { appendCsoundSource, csoundString } from "../../tools/build";

describe("Csound string literals", () => {
  test.each([
    ["", '""'],
    ["plain text", '"plain text"'],
    ['(println "hello")', '"(println \\"hello\\")"'],
    [String.raw`C:\samples\kick.wav`, String.raw`"C:\\samples\\kick.wav"`],
    ["first\r\nsecond\tvalue", '"first\\r\\nsecond\\tvalue"'],
    [String.raw`\n`, String.raw`"\\n"`],
  ])("encodes %j as one literal", (source, literal) => {
    expect(csoundString(source)).toBe(literal);
  });

  test("keeps Lisp maps, Csound macro characters, and Unicode literal", () => {
    const source = '(def options {:label "hljóð $signal {{}}" :hz [440 550]})';
    expect(csoundString(source)).toBe(
      '"(def options {:label \\"hljóð $signal {{}}\\" :hz [440 550]})"',
    );
  });

  test("does not let quotes or newlines escape into orchestra code", () => {
    expect(csoundString('"\nendin\ninstr 1\n;')).toBe(
      '"\\"\\nendin\\ninstr 1\\n;"',
    );
  });

  test("protects macro names split between source chunks", () => {
    expect(appendCsoundSource("$MAL_HASH_MAP_", "source")).toBe(
      'chnset strcat(chnget:S("source"), "\\$MAL_HASH_MAP_"), "source"\nreturn 0\n',
    );
    expect(appendCsoundSource(String.raw`TYPE \$literal`, "source")).toBe(
      'chnset strcat(chnget:S("source"), "TYPE \\\\\\$literal"), "source"\nreturn 0\n',
    );
  });
});
