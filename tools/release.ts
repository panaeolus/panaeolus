import { createHash } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

export function releaseVersion(tag: string, version: unknown): string {
  const match = typeof version === "string" && version === version.trim()
    ? /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$/.exec(version)
    : null;
  if (!match || match[4]?.split(".").some(part => /^0\d+$/.test(part))) {
    throw new Error("package.json must have a version such as 0.1.0.");
  }
  if (tag !== `v${version}`) {
    throw new Error(`Release tag ${tag} must match package.json version v${version}.`);
  }
  return match[0];
}

export async function prepareRelease(root: string, tag: string): Promise<string[]> {
  const pkg = JSON.parse(await readFile(resolve(root, "package.json"), "utf8"));
  const version = releaseVersion(tag, pkg.version);
  const assets = [
    ["dist/make-a-lisp.orc", `make-a-lisp-for-panaeolus-${version}.orc`],
    ["dist/panaeolus.mal", `panaeolus-${version}.mal`],
    ["dist/panaeolus.orc", `panaeolus-${version}.orc`],
    ["LICENSE", "LICENSE-GPL-3.0.txt"],
    ["vendor/make-a-lisp/LICENSE", "LICENSE-MPL-2.0.txt"],
    ["NOTICE", "NOTICE.txt"],
  ] as const;
  // Validate all inputs before replacing the last generated release directory.
  const files = await Promise.all(assets.map(async ([source, name]) => {
    const content = await readFile(resolve(root, source));
    if (content.byteLength === 0) throw new Error(`Release input ${source} is empty.`);
    return { name, content };
  }));
  const output = resolve(root, "dist/release");
  await rm(output, { recursive: true, force: true });
  await mkdir(output, { recursive: true });
  const sums = files.map(({ name, content }) =>
    `${createHash("sha256").update(content).digest("hex")}  ${name}\n`).join("");
  for (const { name, content } of files) await writeFile(resolve(output, name), content);
  await writeFile(resolve(output, "SHA256SUMS"), sums);
  return [...files.map(({ name }) => resolve(output, name)), resolve(output, "SHA256SUMS")];
}

if (import.meta.main) {
  if (process.argv.length !== 3) throw new Error("Usage: bun tools/release.ts v0.1.0");
  const files = await prepareRelease(resolve(import.meta.dir, ".."), process.argv[2]);
  console.log(files.join("\n"));
}
