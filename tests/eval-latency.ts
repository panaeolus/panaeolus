import assert from "node:assert/strict";
import { resolve } from "node:path";

// Profile the shipped runtime without changing it on disk. Compile markers
// cross Csound's synchronous message callback so JavaScript can time evalstr;
// the WASM rtclock opcode does not supply a useful clock for this measurement.
Object.assign(globalThis, { self: globalThis, window: globalThis });
const { libcsound } = await import("../dist/web/csound.js");
const root = resolve(import.meta.dir, "..");
const nativeLog = console.log.bind(console);
type Span = { kind: string; ms: number };
let spans: Span[] = [];
const starts: { kind: string; started: number }[] = [];
console.log = (...args) => {
  const message = args.map(String).join(" ");
  if (message.startsWith("__EVAL_LATENCY_BEGIN__ ")) {
    starts.push({ kind: message.slice(23), started: performance.now() });
  } else if (message === "__EVAL_LATENCY_END__") {
    const entry = starts.pop();
    assert.ok(entry, "Native profiling span ended without a start");
    spans.push({ kind: entry.kind, ms: performance.now() - entry.started });
  } else if (message.includes("error") || message.includes("Error")) {
    nativeLog(...args);
  }
};

const api = await libcsound();
const engine = api.csoundCreate();
const original = await Bun.file(resolve(root, "dist/make-a-lisp.orc")).text();
let mal = original.replace("    compiled:i evalstr source.string",
  '    prints "__EVAL_LATENCY_BEGIN__ instrument\\n"\n    compiled:i evalstr source.string\n    prints "__EVAL_LATENCY_END__\\n"');
mal = mal.replace("  evaluated:i evalstr source",
  '  prints "__EVAL_LATENCY_BEGIN__ bridge %s\\n", source\n  evaluated:i evalstr source\n  prints "__EVAL_LATENCY_END__\\n"');
assert.notEqual(mal, original, "No native profiling markers were installed");
const runtime = await Bun.file(resolve(root, "dist/panaeolus.orc")).text();
const starter = await Bun.file(resolve(root, "playground/playground.mal")).text();
const results: object[] = [];
const budgetMs = 2048 / 48000 * 1000;

function topForms(text: string) {
  const forms: string[] = [];
  let depth = 0, begin = -1, comment = false, string = false, escaped = false;
  for (let index = 0; index < text.length; index++) {
    const character = text[index];
    if (comment) { if (character === "\n") comment = false; continue; }
    if (string) {
      if (escaped) escaped = false;
      else if (character === "\\") escaped = true;
      else if (character === '"') string = false;
      continue;
    }
    if (character === ";") { comment = true; continue; }
    if (character === '"') { string = true; continue; }
    if ("([{".includes(character)) { if (depth++ === 0) begin = index; }
    else if (")]}".includes(character) && --depth === 0) forms.push(text.slice(begin, index + 1));
  }
  assert.equal(depth, 0);
  assert.equal(string, false);
  return forms;
}

function evaluate(label: string, code: string) {
  api.csoundSetStringChannel(engine, "__panaeolus_test", `${code}\nnil`);
  spans = [];
  const started = performance.now();
  api.csoundEvalCode(engine, 'return PanaeolusEval(chnget:S("__panaeolus_test"))\n');
  const elapsedMs = performance.now() - started;
  assert.equal(api.csoundGetControlChannel(engine, "__panaeolus_status"), 0, code);
  assert.equal(starts.length, 0, "Unclosed native compile span");
  const nativeMs = spans.reduce((sum, span) => sum + span.ms, 0);
  const row = { label, elapsedMs, nativeMs, otherMs: elapsedMs - nativeMs,
    exceedsBuffer: elapsedMs > budgetMs, nativeCalls: [...spans] };
  results.push(row);
  nativeLog(JSON.stringify(row));
}

function advance(seconds = 0.2) {
  for (let block = 0; block < Math.round(seconds * 48000 / 64); block++) {
    assert.equal(api.csoundPerformKsmps(engine), 0);
  }
}

try {
  for (const option of ["-n", "-d", "-m0", "--daemon"]) api.csoundSetOption(engine, option);
  assert.equal(api.csoundCompileOrc(engine,
    `sr = 48000\nksmps = 64\nnchnls = 2\n0dbfs = 1\n${mal}\n${runtime}`), 0);
  assert.equal(api.csoundStart(engine), 0);
  const forms = topForms(starter);
  for (let index = 0; index < forms.length; index++) {
    evaluate(`starter ${index + 1}: ${forms[index].split("\n")[0]}`, forms[index]);
  }
  advance();
  evaluate("full starter repeat", starter);
  advance();
  const wet = "(pulse :every [0.25 0.5] :hz [440 550 660] :fx (flanger :rate [0.2 0.5 0.8] :depth 0.003 :fback 0.35))";
  evaluate("wet loop same route", wet); advance();
  evaluate("wet loop changed values", wet.replace("440 550 660", "330 660 880").replace("0.2 0.5 0.8", "0.3 0.6 0.9")); advance();
  evaluate("wet to dry", "(pulse :every [0.25 0.5] :hz [440 550 660])"); advance();
  evaluate("dry same route", "(pulse :every [0.25 0.5] :hz [330 660 880])"); advance();
  evaluate("new dry loop", "(pulse :id :second :every [0.25 0.5] :hz [330 660 880])"); advance();
  evaluate("stop", "(p/stop-all!)"); advance(4.5);
  evaluate("phase setup", `(require '[panaeolus.routing :as r] '[panaeolus.fx :as fx])
    (def spec (dissoc (p/descriptor pulse) :signal))`);
  evaluate("pure flanger factory", "(def effect (flanger :rate [0.2 0.5 0.8] :depth 0.003 :fback 0.35))");
  evaluate("pure dry argument validation", "(def values (p/arguments spec '(:every [0.25 0.5] :hz [440 550 660]) true))");
  evaluate("pure wet argument validation", "(def values (p/arguments spec (list :every [0.25 0.5] :hz [440 550 660] :fx effect) true))");
  evaluate("open existing FX definition", "(def route (r/open! :phase [effect]))");
  evaluate("make callback", "(def callback (p/make-callback (get spec :compiled) 0.1 [[440 550 660] 0.08] route [effect]))");
  evaluate("register loop", "(loop/start! :phase [0.25 0.5] callback :offset 0)");
  evaluate("stop phase", "(p/stop-all!)"); advance(4.5);
  evaluate("simple assignment", "(def notes [440 550 660])");
  evaluate("empty eval", "nil");
  nativeLog(JSON.stringify({ budgetMs, results }, null, 2));
  if (process.env.EVAL_LATENCY_OUTPUT) {
    await Bun.write(process.env.EVAL_LATENCY_OUTPUT, JSON.stringify({ budgetMs, results }, null, 2));
  }
} finally {
  api.csoundStop(engine);
  api.csoundDestroy(engine);
  console.log = nativeLog;
}
