import { describe, expect, test } from "bun:test";
import { createDemoSample, importedSamplePath, MAX_SAMPLE_BYTES, sampleExtension, sampleLoadForm } from "../../playground/demo-sample";

describe("sample imports", () => {
  test("keeps user paths out of engine paths and gives repeated imports distinct names", () => {
    const name = '../../strange "sample"\n.WAV';
    expect(importedSamplePath(name, 100, "first-id")).toBe("panaeolus-import-first-id.wav");
    expect(importedSamplePath(name, 100, "second-id")).not.toBe(importedSamplePath(name, 100, "first-id"));
    expect(() => importedSamplePath(name, 100, "../outside")).toThrow("Invalid sample identifier");
  });

  test.each(["wav", "wave", "aif", "aiff", "aifc", "flac"])("accepts %s with a bounded nonempty size", extension => {
    expect(sampleExtension(`sample.${extension.toUpperCase()}`, MAX_SAMPLE_BYTES)).toBe(extension);
    expect(() => sampleExtension(`sample.${extension}`, 0)).toThrow("nonempty");
    expect(() => sampleExtension(`sample.${extension}`, MAX_SAMPLE_BYTES + 1)).toThrow("20 MiB");
  });

  test("rejects unsupported formats and escapes the printed Lisp path", () => {
    expect(() => sampleExtension("recording.mp3", 123)).toThrow("WAV, AIFF, or FLAC");
    expect(sampleLoadForm('folder/a "quote"\\sample\n.wav')).toBe('(sampler/load "folder/a \\"quote\\"\\\\sample\\n.wav")');
  });
});

describe("demo sample", () => {
  test("writes a deterministic stereo PCM16 WAV with valid lengths and silent loop endpoints", () => {
    const bytes = createDemoSample();
    const view = new DataView(bytes.buffer);
    const text = (offset: number, length: number) => new TextDecoder().decode(bytes.subarray(offset, offset + length));
    expect(bytes).toEqual(createDemoSample());
    expect(text(0, 4)).toBe("RIFF");
    expect(text(8, 4)).toBe("WAVE");
    expect(text(12, 4)).toBe("fmt ");
    expect(text(36, 4)).toBe("data");
    expect(view.getUint32(4, true)).toBe(bytes.length - 8);
    expect(view.getUint16(20, true)).toBe(1);
    expect(view.getUint16(22, true)).toBe(2);
    expect(view.getUint32(24, true)).toBe(48000);
    expect(view.getUint32(28, true)).toBe(48000 * 4);
    expect(view.getUint16(32, true)).toBe(4);
    expect(view.getUint16(34, true)).toBe(16);
    expect(view.getUint32(40, true)).toBe(48000 * 2 * 4);
    expect(view.getInt16(44, true)).toBe(0);
    expect(view.getInt16(bytes.length - 2, true)).toBe(0);
    for (let beat = 0; beat < 4; beat++) {
      let peak = 0;
      let stereoDifference = 0;
      for (let frame = 0; frame < 24000; frame++) {
        const offset = 44 + (beat * 24000 + frame) * 4;
        const left = view.getInt16(offset, true);
        const right = view.getInt16(offset + 2, true);
        peak = Math.max(peak, Math.abs(left), Math.abs(right));
        stereoDifference += Math.abs(left - right);
        if (frame >= 19200 && (left !== 0 || right !== 0)) throw new Error("Demo hit has no silence before the next slice");
      }
      expect(peak).toBeGreaterThan(4000);
      expect(peak).toBeLessThan(16384);
      expect(stereoDifference).toBeGreaterThan(0);
    }
  });
});
