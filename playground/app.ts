import Csound, { type CsoundObj } from "@csound/browser";
import { createDemoSample, DEMO_SAMPLE_PATH, importedSamplePath, sampleLoadForm } from "./demo-sample";

type EngineState = "stopped" | "loading" | "running" | "stopping" | "error";
type LogKind = "output" | "info" | "input" | "error";

const element = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const source = element<HTMLTextAreaElement>("source");
const output = element<HTMLDivElement>("console");
const startButton = element<HTMLButtonElement>("start");
const stopButton = element<HTMLButtonElement>("stop");
const evaluateButton = element<HTMLButtonElement>("evaluate");
const evaluateAllButton = element<HTMLButtonElement>("evaluate-all");
const tempoButton = element<HTMLButtonElement>("set-tempo");
const stopLoopsButton = element<HTMLButtonElement>("stop-loops");
const tempo = element<HTMLInputElement>("tempo");
const sampleInput = element<HTMLInputElement>("sample-file");
const importButton = element<HTMLButtonElement>("import-sample");
const draftKey = "panaeolus.playground.source.v1";

let recording = false;
let hasTake = false;
let evaluating = false;
let importing = false;
let sampleNumber = 0;
let stopRequested = false;
let state: EngineState = "stopped";
let engine: CsoundObj | undefined;
let audioContext: AudioContext | undefined;
let queue: Promise<unknown> = Promise.resolve();
let activeMessageListener: ((message: unknown) => void) | undefined;
let activeEndedListener: (() => void) | undefined;

function log(message: unknown, kind: LogKind = "output") {
  const follow = output.scrollHeight - output.scrollTop - output.clientHeight < 40;
  const text = String(message).replace(/\u001b\[[0-9;]*m/g, "").trimEnd();
  if (!text) return;
  for (const line of text.split("\n")) {
    const row = document.createElement("div");
    row.className = `log-entry ${kind}`;
    row.textContent = line;
    output.append(row);
  }
  while (output.children.length > 300) output.firstElementChild?.remove();
  if (follow) output.scrollTop = output.scrollHeight;
}

function updateControls() {
  const running = state === "running";
  const ready = running && !evaluating && !importing && !stopRequested;
  startButton.disabled = state === "loading" || state === "stopping" || running;
  stopButton.disabled = !running;
  evaluateButton.disabled = !ready || source.selectionStart === source.selectionEnd;
  evaluateAllButton.disabled = !ready;
  tempoButton.disabled = !ready;
  stopLoopsButton.disabled = !ready;
  sampleInput.disabled = !ready;
  importButton.disabled = !ready || !sampleInput.files?.length;
  element<HTMLButtonElement>("record").disabled = !ready || recording;
  element<HTMLButtonElement>("finish").disabled = !ready || !recording;
  element<HTMLButtonElement>("export").disabled = !ready || recording || !hasTake;
}

function setState(next: EngineState) {
  state = next;
  const labels: Record<EngineState, string> = {
    stopped: "Engine stopped",
    loading: "Starting engine…",
    running: evaluating ? "Evaluating… audio running" : importing ? "Importing sample… audio running" : "Engine running",
    stopping: "Stopping engine…",
    error: "Engine error",
  };
  element("status").textContent = labels[next];
  element("status-dot").dataset.state = next;
  updateControls();
}

function serialize<T>(operation: () => Promise<T>): Promise<T> {
  const next = queue.then(operation);
  queue = next.catch(() => undefined);
  return next;
}

function report(error: unknown) {
  log(error instanceof Error ? error.message : String(error), "error");
}

async function readText(path: string) {
  const response = await fetch(path, { cache: "no-store" });
  if (!response.ok) throw new Error(`Could not load ${path} (${response.status}). Run bun run build and restart the server.`);
  return response.text();
}

async function dispose(force = false) {
  element("audio-mode").textContent = "Audio stopped · SharedArrayBuffer required";
  recording = false;
  hasTake = false;
  element("take-status").textContent = "Start a take before starting loops.";
  const previous = engine;
  engine = undefined;
  if (previous) {
    if (activeMessageListener) previous.off("message", activeMessageListener);
    if (activeEndedListener) previous.off("realtimePerformanceEnded", activeEndedListener);
    try {
      if (!force) await previous.stop();
    } catch (error) {
      log(`Engine stop: ${error instanceof Error ? error.message : String(error)}`, "info");
    } finally {
      await previous.terminateInstance();
    }
  }
  activeMessageListener = undefined;
  activeEndedListener = undefined;
  if (audioContext && audioContext.state !== "closed") await audioContext.close();
  audioContext = undefined;
}

function start(): Promise<void> {
  return serialize(async () => {
    if (state === "running") return;
    setState("loading");
    try {
      await dispose();
      stopRequested = false;
      if (!crossOriginIsolated || typeof SharedArrayBuffer === "undefined") {
        throw new Error("SharedArrayBuffer is unavailable. Restart bun run dev, then reload this page from localhost or 127.0.0.1.");
      }
      // Create and resume the audio context from the user's Start action.
      audioContext = new AudioContext({ sampleRate: 48000 });
      await audioContext.resume();
      // One worker runs the shared Csound engine and Lisp callbacks. Its buffered
      // output feeds an AudioWorklet, so callbacks need not finish in one quantum.
      engine = await Csound({ audioContext, autoConnect: true, useWorker: true, useSAB: true });
      if (!engine) throw new Error("Csound could not create its worker and audio output in this browser.");
      if (engine.name !== "Csound: Audio Worklet, Shared-Array Buffer") {
        throw new Error(`Csound selected an unexpected audio transport: ${engine.name}. SharedArrayBuffer is required.`);
      }
      const currentEngine = engine;
      let performanceEnded = false;
      activeMessageListener = (message) => log(message);
      activeEndedListener = () => {
        if (engine !== currentEngine) return;
        performanceEnded = true;
        if (state === "running") {
          log("Csound performance ended.", "info");
          void stop().catch(report);
        }
      };
      engine.removeListener("message", console.log);
      engine.on("message", activeMessageListener);
      engine.on("realtimePerformanceEnded", activeEndedListener);
      await engine.fs.writeFile(DEMO_SAMPLE_PATH, createDemoSample());
      log("Loading make-a-lisp.orc + panaeolus.orc…", "info");
      const [lisp, panaeolus] = await Promise.all([
        readText("/dist/make-a-lisp.orc"),
        readText("/dist/panaeolus.orc"),
      ]);
      const csd = `<CsoundSynthesizer>
<CsOptions>
-odac -d -m0
</CsOptions>
<CsInstruments>
sr = 48000
ksmps = 64
nchnls = 2
0dbfs = 1
${lisp}
${panaeolus}
</CsInstruments>
<CsScore>
f 0 z
</CsScore>
</CsoundSynthesizer>`;
      const compiled = await engine.compileCSD(csd);
      if (compiled !== 0) throw new Error(`Csound could not compile the runtime (code ${compiled}). See the output above.`);
      // This library's start() can resolve after its own two-second timeout.
      // Wait for the worklet's event before allowing live evaluations.
      let audioStarted = false;
      const onStarted = () => { audioStarted = true; };
      engine.on("realtimePerformanceStarted", onStarted);
      try {
        const started = await engine.start();
        if (started !== 0) throw new Error(`Csound could not start audio (code ${started}).`);
        const deadline = performance.now() + 15000;
        while (!audioStarted && !performanceEnded && performance.now() < deadline) {
          await new Promise(resolve => setTimeout(resolve, 25));
        }
        if (performanceEnded) throw new Error("Csound ended before its audio output was ready. See the output above.");
        if (!audioStarted) throw new Error("Csound did not finish starting its audio worklet. Try starting the engine again.");
      } finally {
        engine.off("realtimePerformanceStarted", onStarted);
      }
      if (audioContext.state !== "running") throw new Error("The browser suspended audio. Start the engine again to resume it.");
      setState("running");
      element("audio-mode").textContent = "SharedArrayBuffer · AudioWorklet · 48 kHz";
      log(`Audio transport: ${engine.name} (cross-origin isolated).`, "info");
      log(`Demo sample ready: ${DEMO_SAMPLE_PATH} (two seconds, four beats at 120 BPM).`, "info");
      log("Engine ready. Evaluate the file to start the example loop.", "info");
    } catch (error) {
      try { await dispose(); } catch (cleanupError) { report(cleanupError); }
      setState("error");
      throw error;
    }
  });
}

function stop(): Promise<void> {
  // Release a pending evaluation before joining the queue. Even a Lisp loop
  // that never returns must leave the engine's Stop button usable.
  stopRequested = true;
  return serialize(async () => {
    setState("stopping");
    try {
      await dispose();
      setState("stopped");
      log("Engine stopped. Starting again creates a fresh Lisp session.", "info");
    } catch (error) {
      setState("error");
      throw error;
    }
  });
}

function evaluate(code: string): Promise<void> {
  return serialize(async () => {
    if (stopRequested) return;
    if (!engine || state !== "running") throw new Error("Start the engine before evaluating Lisp.");
    if (!code.trim()) return;
    const preview = code.trim().replace(/\s+/g, " ");
    log(`› ${preview.slice(0, 120)}${preview.length > 120 ? "…" : ""}`, "input");
    const currentEngine = engine;
    evaluating = true;
    setState("running");
    try {
      await currentEngine.setControlChannel("__panaeolus_status", -999);
      // A string channel keeps user Lisp out of the compiled Csound source.
      await currentEngine.setStringChannel("__panaeolus_source", code);
      await currentEngine.evalCode('return PanaeolusEvalStart(chnget:S("__panaeolus_source"))\n');
      // Csound advances the evaluation between audio blocks. Polling here only
      // updates the UI; browser timers never drive evaluation or musical time.
      let result = await currentEngine.getControlChannel("__panaeolus_status");
      while (result === 1) {
        if (stopRequested) return;
        await new Promise(resolve => setTimeout(resolve, 30));
        result = await currentEngine.getControlChannel("__panaeolus_status");
      }
      if (stopRequested) return;
      await refreshTake();
      if (result !== 0) throw new Error("Lisp evaluation failed. See the output above; you can fix the form and try again.");
    } catch (error) {
      if (error instanceof Error && error.message.startsWith("Worker timed out")) {
        // A stuck native operation cannot be cancelled by rejecting its promise.
        await dispose(true);
        setState("error");
        throw new Error("The audio worker stopped responding. Start the engine again and evaluate smaller forms.");
      }
      throw error;
    } finally {
      evaluating = false;
      if (engine === currentEngine && state === "running") setState("running");
    }
  });
}

function importSample(file: File): Promise<void> {
  return serialize(async () => {
    if (stopRequested) return;
    if (!engine || state !== "running") throw new Error("Start the engine before importing a file.");
    const currentEngine = engine;
    const module = file.name.toLowerCase().endsWith(".mal");
    if (module && (file.size <= 0 || file.size > 2 * 1024 * 1024))
      throw new Error("Choose a nonempty MAL module up to 2 MiB.");
    const path = module ? `panaeolus-module-${crypto.randomUUID()}.mal`
      : importedSamplePath(file.name, file.size, crypto.randomUUID());
    importing = true;
    setState("running");
    try {
      const bytes = new Uint8Array(await file.arrayBuffer());
      if (stopRequested) return;
      await currentEngine.fs.writeFile(path, bytes);
      if (stopRequested) return;
      log(`Imported ${JSON.stringify(file.name)}. This file stays in the current engine session.`, "info");
      if (module) {
        log(`(load-file ${JSON.stringify(path)})`, "input");
        log("Evaluate this form before starting loops, then call the instrument named in the module.", "info");
      } else {
        const name = `sample-${++sampleNumber}`;
        log(`(require '[panaeolus.sampler :as sampler])\n(def ${name} ${sampleLoadForm(path)})`, "input");
        log(`Evaluate these forms, then (${name}) to play.`, "info");
      }
      sampleInput.value = "";
    } finally {
      importing = false;
      if (engine === currentEngine && state === "running") setState("running");
    }
  });
}

async function refreshTake() {
  if (!engine) return;
  recording = (await engine.getControlChannel("__panaeolus_recording")) === 1;
  hasTake = (await engine.getControlChannel("__panaeolus_take_ready")) === 1;
  element("take-status").textContent = recording
    ? "Recording notes, controls, and routes…"
    : hasTake
      ? "Take ready. Download includes note endings and effect tails."
      : "Start a take before starting loops.";
  updateControls();
}

async function recordTake() {
  await evaluate("(panaeolus.instrument/record!)");
}

async function finishTake() {
  await evaluate("(panaeolus.instrument/finish!)");
}

async function downloadTake() {
  await evaluate("(panaeolus.instrument/export!)");
  await serialize(async () => {
    if (!engine) throw new Error("Start the engine first.");
    const csd = await engine.getStringChannel("__panaeolus_export");
    if (!csd.includes("<CsoundSynthesizer>")) throw new Error("No completed take to export.");
    const url = URL.createObjectURL(new Blob([csd], { type: "text/plain;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = "panaeolus-take.csd";
    link.click();
    URL.revokeObjectURL(url);
    log("Downloaded standalone orchestra + score.", "info");
  });
}

function evaluateSelection() {
  const selection = source.value.slice(source.selectionStart, source.selectionEnd);
  if (!selection.trim()) {
    log("Select one or more complete forms, or use Evaluate all.", "info");
    return;
  }
  void evaluate(selection).catch(report);
}

function setTempo() {
  const value = Number(tempo.value);
  if (!Number.isFinite(value) || value < 1 || value > 999) {
    log("Enter a tempo between 1 and 999 BPM.", "error");
    tempo.focus();
    return;
  }
  void evaluate(`(panaeolus.event-loop/tempo! ${value})`).catch(report);
}

function saveDraft() {
  try {
    localStorage.setItem(draftKey, source.value);
    element("saved").textContent = "Saved in browser";
  } catch {
    element("saved").textContent = "Draft not saved";
  }
}

async function loadFile(useDraft: boolean) {
  if (useDraft) {
    try {
      const saved = localStorage.getItem(draftKey);
      if (saved !== null) {
        source.value = saved;
        element("saved").textContent = "Restored local draft";
        return;
      }
    } catch { /* The editor also works without local storage. */ }
  }
  source.value = await readText("/playground/playground.mal");
  saveDraft();
  updateControls();
}

element("record").addEventListener("click", () => { void recordTake().catch(report); });
element("finish").addEventListener("click", () => { void finishTake().catch(report); });
element("export").addEventListener("click", () => { void downloadTake().catch(report); });
startButton.addEventListener("click", () => { void start().catch(report); });
stopButton.addEventListener("click", () => { void stop().catch(report); });
evaluateButton.addEventListener("click", evaluateSelection);
evaluateAllButton.addEventListener("click", () => { void evaluate(source.value).catch(report); });
tempoButton.addEventListener("click", setTempo);
tempo.addEventListener("keydown", (event) => { if (event.key === "Enter") setTempo(); });
stopLoopsButton.addEventListener("click", () => { void evaluate("(panaeolus.event-loop/stop-all!)").catch(report); });
sampleInput.addEventListener("change", updateControls);
importButton.addEventListener("click", () => {
  const file = sampleInput.files?.[0];
  if (file) void importSample(file).catch(report);
});
element("clear").addEventListener("click", () => output.replaceChildren());
element("reset").addEventListener("click", () => { void loadFile(false).catch(report); });
source.addEventListener("input", saveDraft);
source.addEventListener("select", updateControls);
source.addEventListener("keyup", updateControls);
source.addEventListener("click", updateControls);
source.addEventListener("keydown", (event) => {
  if ((event.metaKey || event.ctrlKey) && (event.key === "Enter" || event.key.toLowerCase() === "e")) {
    event.preventDefault();
    if (event.shiftKey) void evaluate(source.value).catch(report);
    else evaluateSelection();
  } else if (event.key === "Tab") {
    event.preventDefault();
    source.setRangeText("  ", source.selectionStart, source.selectionEnd, "end");
    saveDraft();
  }
});

if (!/Mac|iPhone|iPad/.test(navigator.platform)) {
  element("shortcut").textContent = "Ctrl Enter";
  element("shortcut-all").textContent = "Ctrl Shift Enter";
}

Object.assign(window, {
  panaeolus: {
    start, stop, evaluate,
    get state() { return state; },
    get engine() { return engine; },
  },
});

setState("stopped");
log("Panaeolus / local playground", "info");
log("Start the engine, then evaluate playground.mal.\nThe clock and callbacks run inside Csound.", "info");
void loadFile(true).catch(report);
