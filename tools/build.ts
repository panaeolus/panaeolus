import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";
import { csoundModule } from "./csound-module";

interface CatalogVoice {
  namespace: string; opcode: string; node: string; rate: "i" | "k";
  count: number; source: string; mal: string;
}

export const root = resolve(import.meta.dir, "..");

// A normal Csound string keeps Lisp map braces literal.
export function csoundString(source: string): string {
  return '"' + source.replaceAll("\\", "\\\\")
    .replaceAll('"', '\\"').replaceAll("\r", "\\r")
    .replaceAll("\n", "\\n").replaceAll("\t", "\\t") + '"';
}

export function appendCsoundSource(source: string, channel: string): string {
  // The preprocessor expands macros even inside strings. Escape dollars until
  // the assembled source reaches evalstr; a chunk may end midway through a name.
  const literal = csoundString(source).replaceAll("$", "\\$");
  return `chnset strcat(chnget:S(${csoundString(channel)}), ${literal}), ${csoundString(channel)}\nreturn 0\n`;
}

export async function build() {
  await mkdir(resolve(root, "dist"), { recursive: true });
  const malArtifact = process.env.MAKE_A_LISP_ORC;
  if (malArtifact) {
    await Bun.write(resolve(root, "dist/make-a-lisp.orc"), Bun.file(resolve(malArtifact)));
  } else if (process.env.MAKE_A_LISP_ROOT) {
    const malSource = resolve(root, process.env.MAKE_A_LISP_ROOT);
    const bundler = resolve(malSource, "tools/bundle.py");
    if (!await Bun.file(bundler).exists()) {
      throw new Error("Set MAKE_A_LISP_ROOT to the make-a-lisp checkout, or MAKE_A_LISP_ORC to its standalone bundle.");
    }
    const child = Bun.spawn(["python3", bundler, "--output", resolve(root, "dist/make-a-lisp.orc")], {
      stdout: "inherit", stderr: "inherit",
    });
    if (await child.exited !== 0) throw new Error("make-a-lisp build failed");
  } else {
    await Bun.write(resolve(root, "dist/make-a-lisp.orc"), Bun.file(resolve(root, "vendor/make-a-lisp.orc")));
  }
  const interpreterPath = resolve(root, "dist/make-a-lisp.orc");
  const interpreter = await Bun.file(interpreterPath).text();
  // Reserve the numeric range before MAL runs. Adding a higher numbered
  // instrument from evalstr otherwise moves active UDO IDs in this engine.
  // The empty instrument never runs and stays outside Panaeolus' output slot.
  const reservation = "\n;; Panaeolus startup instrument range reservation.\ninstr 30001\nendin\n";
  await Bun.write(interpreterPath, interpreter.includes(reservation) ? interpreter : interpreter + reservation);
  // Explicit order: add each new module here after its dependencies.
  const catalog: CatalogVoice[] = await Bun.file(resolve(root, "src/instruments/catalog.json")).json();
  const modules = ["src/panaeolus/event_loop.mal", "src/panaeolus/routing.mal",
    "src/panaeolus/fx.mal", "src/panaeolus/instrument.mal", "src/panaeolus/composition.mal",
    "src/panaeolus/level.mal", "src/instruments/common/catalog.mal", "src/instruments/samplers/flooper.mal", "src/instruments/samplers/spectral.mal", "src/instruments/additive/adsyn.mal"];
  const lispModules = await Promise.all(modules.map(path => Bun.file(resolve(root, path)).text()));
  // Load callable definitions after the native registry and Lisp environment.
  const builtinModules = ["src/instruments/plucks/voices.mal", "src/instruments/synths/voices.mal",
    ...catalog.map((voice: { mal: string }) => voice.mal)];
  const builtins = await Promise.all(builtinModules.map(path => Bun.file(resolve(root, path)).text()));
  // One call site reuses the environment-copy buffers while registering the
  // catalogue. A separate MalEnvSet instance per voice retains a copy of the
  // growing root environment in each Csound opcode instance.
  const strings = (key: "namespace" | "opcode" | "node") => catalog.map((voice) => csoundString(String(voice[key]))).join(", ");
  const registrations = `
  catalogNamespaces:S[] fillarray ${strings("namespace")}
  catalogOpcodes:S[] fillarray ${strings("opcode")}
  catalogNodes:S[] fillarray ${strings("node")}
  catalogSignatures:S[] fillarray ${catalog.map((voice) => csoundString(Array(voice.count).fill(voice.rate).join(","))).join(", ")}
  catalogCounts:i[] fillarray ${catalog.map((voice) => voice.count).join(", ")}
  for index in [0 ... ${catalog.length - 1}] do
    catalogNs:i = MalNsEnsure(env, catalogNamespaces[index], 1)
    catalogNode:MalValue init MalMkCsoundOpcode(catalogOpcodes[index], "a,a")
    catalogNode init MalAppendValue(catalogNode, MalCsoundSignature("a,a", catalogSignatures[index], catalogCounts[index], "", 0))
    env init MalEnvSet(env, strcat(strcat(catalogNamespaces[index], "/"), catalogNodes[index]), catalogNode)
  od`;
  const runtime = (await Bun.file(resolve(root, "src/runtime.orc")).text()).replace("@@PANAEO_CATALOG@@", registrations);
  const marker = "@@PANAEO_LISP@@";
  if (runtime.split(marker).length !== 2) throw new Error("Expected one Lisp source marker in src/runtime.orc");
  const envMarker = "@@PANAEO_ENV@@";
  if (runtime.split(envMarker).length !== 2) throw new Error("Expected one environment marker in src/runtime.orc");
  const support = await Bun.file(resolve(root, "src/routing-support.orc")).text();
  const flanger = await Bun.file(resolve(root, "src/effects/flanger.orc")).text();
  const routing = await Bun.file(resolve(root, "src/routing.orc")).text();
  const held = await Bun.file(resolve(root, "src/held.orc")).text();
  const composition = await Bun.file(resolve(root, "src/composition.orc")).text();
  const sampler = await Bun.file(resolve(root, "src/instruments/samplers/flooper.orc")).text();
  const sampleLevel = await Bun.file(resolve(root, "src/sample-level.orc")).text();
  const samples = await Bun.file(resolve(root, "src/samples.orc")).text();
  const analyses = await Bun.file(resolve(root, "src/analyses.orc")).text();
  const levels = await Bun.file(resolve(root, "src/instruments/common/levels.orc")).text();
  const plucks = await Bun.file(resolve(root, "src/instruments/plucks/voices.orc")).text();
  const synths = await Bun.file(resolve(root, "src/instruments/synths/voices.orc")).text();
  const bbcut = await Bun.file(resolve(root, "src/effects/bbcut.orc")).text();
  const catalogSource = (await Promise.all(["src/instruments/common/catalog.orc", "src/instruments/samplers/channels.orc",
    "src/instruments/samplers/spectral.orc", "src/instruments/additive/adsyn.orc", "src/instruments/vocal/support.orc", "src/instruments/scanned/support.orc", "src/instruments/common/bass-pulse.orc", "src/effects/formant.orc",
    ...catalog.map((voice: { source: string }) => voice.source)].map(path => Bun.file(resolve(root, path)).text()))).join("\n");
  const base = support + "\n" + bbcut + "\n" + flanger + "\n" + levels + "\n" + sampler + "\n" + plucks + "\n" + synths + "\n" + catalogSource;
  // Keep the large native source out of MAL values: Csound retains string
  // capacity when copying them, which would inflate later graph allocations.
  const registerBase = `panaeolusDefinitionNumbers[0] = 0\npanaeolusDefinitionSources[0] init ${csoundString(base)}\npanaeolusDefinitionCount = 1\n`;
  const native =
    ";; SPDX-License-Identifier: GPL-3.0-only\n" +
    ";; Generated from src/runtime.orc and " + [...modules, ...builtinModules].join(", ") + ".\n" +
    ";; Load make-a-lisp.orc first. Rebuild with bun run build.\n" +
    base + "\n" + runtime.replace(marker, () => lispModules.map(source => `
  result:MalValue, env = MalEvalFormsEnv(${csoundString(source)}, env)
  if (result.type == $MAL_ERROR_TYPE) ithen
    prints "Panaeolus bootstrap failed: %s\\n", result.string
    exitnow(1)
  endif`).join("\n")) + "\n" + sampleLevel + "\n" + samples + "\n" + analyses + "\n" + routing + "\n" + held + "\n" + composition +
    `\n${registerBase}` +
    builtins.map(source => `panaeolusBuiltins:i = PanaeolusEval(${csoundString(source)})\nif (panaeolusBuiltins < 0) ithen\n  exitnow(1)\nendif\n`).join("\n") +
    "schedule(90, 0, -1)\nschedule(30000, 0, -1, 0, 0)\n" +
    'chnset 1, "__panaeolus_loaded"\n';
  await Bun.write(resolve(root, "dist/panaeolus.orc"), native.replace(envMarker, "MalMkCsoundEnv()"));

  // A unique namespace ties the native bridge to the importing MAL session.
  // Looking up its owner avoids creating a second, disconnected Lisp environment.
  const loader = `opcode PanaeolusLoaderEnv(owner:S):MalEnv
  rootId:i = -1
  for index in [0 ... malNamespaceCount - 1] do
    if (strcmp(malNamespaces[index].name, owner) == 0) ithen
      rootId = malNamespaces[index].owner
      break
    endif
  od
  if (rootId < 0) ithen
    prints "Panaeolus: loader namespace not found\\n"
    exitnow(1)
  endif
  xout malEnvRegistry[rootId]
endop
`;
  // Csound copies string buffer capacity when copying MAL values. Passing one
  // large decoded orchestra through MAL can inflate later graph values. Keep
  // each literal small and assemble the complete source outside the evaluator.
  const sourceChannel = "__panaeolus_loader_source";
  const ownerChannel = "__panaeolus_loader_owner";
  const loadSource = loader + native.replace(envMarker,
    `PanaeolusLoaderEnv(chnget:S("${ownerChannel}"))`) + "\nreturn 0\n";
  const characters = Array.from(loadSource);
  const appendForms: string[] = [];
  for (let offset = 0; offset < characters.length; offset += 4096) {
    const chunk = characters.slice(offset, offset + 4096).join("");
    const append = appendCsoundSource(chunk, sourceChannel);
    appendForms.push(`    (when (< (csound/eval ${JSON.stringify(append)}) 0)\n` +
      '      (throw "Panaeolus: native source assembly failed"))\n');
  }
  const compile = `status:i evalstr chnget:S("${sourceChannel}")\n` +
    `chnset "", "${sourceChannel}"\nchnset "", "${ownerChannel}"\nreturn status\n`;
  await Bun.write(resolve(root, "dist/panaeolus.mal"),
    ";; SPDX-License-Identifier: GPL-3.0-only\n" +
    ";; Generated by bun run build. Load once before starting live evaluation.\n" +
    "(when-not (and (find-ns 'panaeolus.instrument)\n" +
    "               (get (ns-publics 'panaeolus.instrument) 'bundle-ready))\n" +
    "  (let [owner (gensym \"panaeolus.loader.\") previous *ns*]\n" +
    '    (when (> (csound/eval "return chnget:i(\\\"__panaeolus_loaded\\\")") 0)\n' +
    '      (throw "Panaeolus already belongs to another Lisp session in this engine"))\n' +
    "    (create-ns owner)\n" +
    `    (csound/eval (str "chnset \\\"" (name owner) "\\\", \\\"${ownerChannel}\\\"\\nchnset \\\"\\\", \\\"${sourceChannel}\\\"\\nreturn 0"))\n` +
    appendForms.join("") +
    `    (let [status (csound/eval ${JSON.stringify(compile)})]\n` +
    "      (in-ns previous)\n" +
    // evalstr may return zero after a compiler error. The marker proves native
    // initialization reached the end before the session becomes bundle-ready.
    '      (when (or (< status 0) (not= 1 (csound/eval "return chnget:i(\\\"__panaeolus_loaded\\\")")))\n' +
    '        (throw "Panaeolus: native bridge compilation failed"))\n' +
    "      (def panaeolus.instrument/bundle-ready true))))\n");

  const result = await Bun.build({
    entrypoints: [resolve(root, "playground/app.ts")],
    outdir: resolve(root, "dist/web"), target: "browser", format: "esm",
    // Csound ships a ready browser module with embedded worklet/WASM assets.
    external: ["@csound/browser"],
  });
  if (!result.success) throw new AggregateError(result.logs, "Playground build failed");
  const csoundRoot = resolve(root, "node_modules/@csound/browser");
  const csoundPackage = await Bun.file(resolve(csoundRoot, "package.json")).json();
  await Bun.write(resolve(root, "dist/web/csound.js"), csoundModule(
    await Bun.file(resolve(csoundRoot, "dist/csound.js")).text(), csoundPackage.version));
  console.log("Built dist/make-a-lisp.orc, dist/panaeolus.orc, dist/panaeolus.mal, and playground.");
}

if (import.meta.main) await build();
