import assert from "node:assert/strict";
import { csoundModule } from "../tools/csound-module";

const packageRoot = new URL("../node_modules/@csound/browser/", import.meta.url);
const { version } = await Bun.file(new URL("package.json", packageRoot)).json();
const source = await Bun.file(new URL("dist/csound.js", packageRoot)).text();
const patched = csoundModule(source, version);

// Exercise all three generated copies without starting their workers or Wasm.
// These functions only need a file descriptor and a view of caller memory.
const readers = [...patched.matchAll(
  /\.prototype\.\w+=(function\(fd,iovs,iovsLength,nread\)\{[\s\S]*?return 0\});/g,
)];
const reducers = [...patched.matchAll(
  /\.reduce\((function\(e,([fg])\)\{return e\+\(\2==null\?0:\2\.byteLength\)\}),0\)/g,
)];
assert.equal(readers.length, 3, "The main module and both workers need the read fix");
assert.equal(reducers.length, 3, "The main module and both workers need the size fix");

type FileEntry = { buffers: Uint8Array[]; seekPos: bigint };
type FileContext = { fd: Record<number, FileEntry>; getMemory(): DataView };
type Read = (this: FileContext, fd: number, iovs: number, count: number, nread: number) => number;
const encode = (text: string) => new TextEncoder().encode(text);
let checks = 0;

for (const [copy, match] of readers.entries()) {
  const read: Read = new Function(`return (${match[1]})`)();
  function fixture(chunks: string[], position = 0n) {
    const bytes = new Uint8Array(1024).fill(0xa5);
    const memory = new DataView(bytes.buffer);
    const entry = { buffers: chunks.map(encode), seekPos: position };
    const context: FileContext = { fd: { 4: entry }, getMemory: () => memory };
    function run(lengths: number[], fd = 4) {
      lengths.forEach((length, index) => {
        memory.setUint32(32 + index * 8, 128 + index * 128, true);
        memory.setUint32(36 + index * 8, length, true);
      });
      const status = read.call(context, fd, 32, lengths.length, 8);
      return { status, count: memory.getUint32(8, true) };
    }
    function output(index: number, expected: string, capacity: number) {
      const target = 128 + index * 128;
      assert.deepEqual(bytes.slice(target, target + expected.length), encode(expected), `copy ${copy}`);
      assert.ok(bytes.slice(target + expected.length, target + capacity).every((byte) => byte === 0xa5),
        `copy ${copy}: read changed memory beyond its returned byte count`);
    }
    return { bytes, memory, entry, context, run, output };
  }

  // A short final read must not expose bytes left in the libc read buffer.
  {
    const file = fixture(["abc"]);
    assert.deepEqual(file.run([8]), { status: 0, count: 3 });
    file.output(0, "abc", 8);
    assert.equal(file.entry.seekPos, 3n);
    const before = file.bytes.slice(128, 136);
    assert.deepEqual(file.run([8]), { status: 0, count: 0 });
    assert.deepEqual(file.bytes.slice(128, 136), before);
    assert.equal(file.entry.seekPos, 3n);
    checks++;
  }
  // The old reader searched forever when a later iovec started exactly at EOF.
  {
    const file = fixture(["abc"]);
    assert.deepEqual(file.run([3, 4]), { status: 0, count: 3 });
    file.output(0, "abc", 3);
    file.output(1, "", 4);
    assert.equal(file.entry.seekPos, 3n);
    checks++;
  }
  {
    const file = fixture(["ab", "cde", "fg"], 4n);
    assert.deepEqual(file.run([8]), { status: 0, count: 3 });
    file.output(0, "efg", 8);
    assert.equal(file.entry.seekPos, 7n);
    checks++;
  }
  {
    const file = fixture(["", "ab", "", "c", ""]);
    assert.deepEqual(file.run([6]), { status: 0, count: 3 });
    file.output(0, "abc", 6);
    assert.equal(file.entry.seekPos, 3n);
    checks++;
  }
  {
    const file = fixture(["ab", "cdef", "gh"], 1n);
    assert.deepEqual(file.run([2, 3, 5]), { status: 0, count: 7 });
    file.output(0, "bc", 2);
    file.output(1, "def", 3);
    file.output(2, "gh", 5);
    assert.equal(file.entry.seekPos, 8n);
    checks++;
  }
  {
    const file = fixture(["ab"]);
    assert.deepEqual(file.run([0, 3]), { status: 0, count: 2 });
    file.output(0, "", 4);
    file.output(1, "ab", 3);
    assert.equal(file.entry.seekPos, 2n);
    checks++;
  }
  for (const chunks of [[], [""], ["abc"]]) {
    const file = fixture(chunks, 12n);
    assert.deepEqual(file.run([5]), { status: 0, count: 0 });
    file.output(0, "", 5);
    assert.equal(file.entry.seekPos, 12n, "EOF must preserve the seek position");
    checks++;
  }
  for (const position of [-1n, 9007199254740992n, 18446744073709551615n]) {
    const file = fixture(["abc"], position);
    assert.deepEqual(file.run([5]), { status: 28, count: 0 });
    file.output(0, "", 5);
    assert.equal(file.entry.seekPos, position);
    checks++;
  }
  {
    const file = fixture(["abc"]);
    Reflect.deleteProperty(file.entry, "seekPos");
    assert.deepEqual(file.run([5]), { status: 28, count: 0 });
    file.output(0, "", 5);
    assert.equal(Object.hasOwn(file.entry, "seekPos"), false);
    checks++;
  }
  {
    const file = fixture(["abc"]);
    // Zero iovecs must not dereference the iovec pointer, even at EOF.
    assert.equal(read.call(file.context, 4, 2048, 0, 8), 0);
    assert.equal(file.memory.getUint32(8, true), 0);
    assert.equal(file.entry.seekPos, 0n);
    file.entry.seekPos = 3n;
    assert.equal(read.call(file.context, 4, 2048, 0, 8), 0);
    assert.equal(file.memory.getUint32(8, true), 0);
    assert.equal(file.entry.seekPos, 3n);
    file.output(0, "", 8);
    checks++;
  }
  {
    const file = fixture(["abc"]);
    assert.deepEqual(file.run([8], 999), { status: 8, count: 0 });
    file.output(0, "", 8);
    assert.equal(file.entry.seekPos, 0n);
    checks++;
  }
}

for (const match of reducers) {
  const sum: (total: number, chunk: Uint8Array | undefined) => number = new Function(`return (${match[1]})`)();
  assert.equal([encode("abc"), new Uint8Array(), encode("defgh"), undefined].reduce(sum, 0), 8);
  assert.equal([].reduce(sum, 0), 0);
  checks++;
}

assert.throws(() => csoundModule(source, "next"), /Review the Csound file patches/);
assert.throws(() => csoundModule(patched, version), /Expected three Csound file-size reducers/);
console.log(`PASS: ${checks} Csound file checks across the main module and both workers.`);
