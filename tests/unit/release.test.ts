import { afterEach, expect, test } from "bun:test";
import { createHash } from "node:crypto";
import { mkdtemp, mkdir, readdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { basename, resolve } from "node:path";
import { prepareRelease, releaseVersion } from "../../tools/release";

const roots: string[] = [];

afterEach(async () => {
  await Promise.all(roots.splice(0).map(root => rm(root, { recursive: true, force: true })));
});

async function fixture(): Promise<string> {
  const root = await mkdtemp(resolve(tmpdir(), "panaeolus-release-"));
  roots.push(root);
  await mkdir(resolve(root, "dist"));
  await mkdir(resolve(root, "vendor/make-a-lisp"), { recursive: true });
  await writeFile(resolve(root, "package.json"), JSON.stringify({ version: "0.1.0" }));
  await writeFile(resolve(root, "dist/panaeolus.mal"), "(ns panaeolus)\n");
  await writeFile(resolve(root, "dist/panaeolus.orc"), "; orchestra\n");
  await writeFile(resolve(root, "dist/make-a-lisp.orc"), "; interpreter\n");
  await writeFile(resolve(root, "LICENSE"), "GPL license\n");
  await writeFile(resolve(root, "vendor/make-a-lisp/LICENSE"), "MPL license\n");
  await writeFile(resolve(root, "NOTICE"), "Copyright notices\n");
  return root;
}

test("release tags must match the package version exactly", () => {
  expect(releaseVersion("v0.1.0", "0.1.0")).toBe("0.1.0");
  expect(releaseVersion("v0.2.0-rc.1", "0.2.0-rc.1")).toBe("0.2.0-rc.1");
  expect(releaseVersion("v0.2.0-0", "0.2.0-0")).toBe("0.2.0-0");
  expect(releaseVersion("v0.2.0-01alpha+build.001", "0.2.0-01alpha+build.001"))
    .toBe("0.2.0-01alpha+build.001");
  expect(releaseVersion("v0.2.0+build.001", "0.2.0+build.001")).toBe("0.2.0+build.001");
  for (const tag of ["0.1.0", "v0.2.0", "v0.1.0/other", "v0.1.0\n"]) {
    expect(() => releaseVersion(tag, "0.1.0")).toThrow("must match");
  }
  for (const version of [undefined, 1, "../0.1.0", "0.1.0\n", "next",
    "01.0.0", "1.01.0", "1.0.01", "1.0.0-01", "1.0.0-rc.01",
    "1.0.0-", "1.0.0-rc..1", "1.0.0-rc.", "1.0.0-.rc", "1.0.0+build..1"]) {
    expect(() => releaseVersion(`v${version}`, version)).toThrow("must have a version");
  }
});

test("release assets contain the built bytes and matching SHA-256 checksums", async () => {
  const root = await fixture();
  const paths = await prepareRelease(root, "v0.1.0");
  expect(paths.map(path => basename(path))).toEqual([
    "make-a-lisp-for-panaeolus-0.1.0.orc",
    "panaeolus-0.1.0.mal",
    "panaeolus-0.1.0.orc",
    "LICENSE-GPL-3.0.txt",
    "LICENSE-MPL-2.0.txt",
    "NOTICE.txt",
    "SHA256SUMS",
  ]);
  expect(await readFile(paths[1], "utf8")).toBe("(ns panaeolus)\n");
  expect(await readFile(paths[2], "utf8")).toBe("; orchestra\n");
  expect(await readFile(paths[0], "utf8")).toBe("; interpreter\n");
  expect(await readFile(paths[3], "utf8")).toBe("GPL license\n");
  expect(await readFile(paths[4], "utf8")).toBe("MPL license\n");
  expect(await readFile(paths[5], "utf8")).toBe("Copyright notices\n");
  const expected = await Promise.all(paths.slice(0, -1).map(async path =>
    `${createHash("sha256").update(await readFile(path)).digest("hex")}  ${basename(path)}\n`));
  expect(await readFile(paths.at(-1)!, "utf8")).toBe(expected.join(""));
});

test("a missing bundle prevents release packaging", async () => {
  const root = await fixture();
  await rm(resolve(root, "dist/panaeolus.mal"));
  await expect(prepareRelease(root, "v0.1.0")).rejects.toThrow();
  expect(await Bun.file(resolve(root, "dist/release/SHA256SUMS")).exists()).toBe(false);
});

test("an empty bundle prevents release packaging", async () => {
  const root = await fixture();
  await writeFile(resolve(root, "dist/panaeolus.orc"), "");
  await expect(prepareRelease(root, "v0.1.0")).rejects.toThrow("empty");
});

test("a tag mismatch prevents release packaging", async () => {
  const root = await fixture();
  await expect(prepareRelease(root, "v0.2.0")).rejects.toThrow("must match");
  expect(await Bun.file(resolve(root, "dist/release/SHA256SUMS")).exists()).toBe(false);
});

test("repeated packaging removes stale release files and keeps other build outputs", async () => {
  const root = await fixture();
  const paths = await prepareRelease(root, "v0.1.0");
  await writeFile(resolve(root, "dist/release/panaeolus-0.0.1.mal"), "old bundle");
  await writeFile(resolve(root, "dist/keep.txt"), "other build output");
  await prepareRelease(root, "v0.1.0");
  expect((await readdir(resolve(root, "dist/release"))).sort())
    .toEqual(paths.map(path => basename(path)).sort());
  expect(await readFile(resolve(root, "dist/keep.txt"), "utf8")).toBe("other build output");
});

test("invalid release inputs leave the previous package intact", async () => {
  const root = await fixture();
  await prepareRelease(root, "v0.1.0");
  const sums = await readFile(resolve(root, "dist/release/SHA256SUMS"), "utf8");
  await rm(resolve(root, "vendor/make-a-lisp/LICENSE"));
  await expect(prepareRelease(root, "v0.1.0")).rejects.toThrow();
  expect(await readFile(resolve(root, "dist/release/SHA256SUMS"), "utf8")).toBe(sums);
});
