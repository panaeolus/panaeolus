# Panaeolus

Live coding in Csound MAL. One Csound engine runs the beat clock, instruments,
and effects. Patterns use ordinary Lisp vectors.

```clojure
(require '[panaeolus.instrument :as p] '[panaeolus.fx :refer [flanger]])

(p/definst pulse [hz 440 amp 0.08]
  (* (csound/poscil amp hz) (csound/linseg 0 0.005 1 0.095 0)))

(pulse) ; one note
(pulse :every [0.25 0.5] :offset 0 :hz [440 550 660]
       :fx (flanger :rate [0.2 0.5]))
(p/stop! pulse)
```

Each parameter vector cycles on its own. Repeating a call replaces its loop;
omitting `:fx` makes new notes dry. `:offset` places notes on the shared beat grid.

Build reusable patterns without playing them. These are ordinary function
values, so `map`, `apply`, and your own functions can arrange them:

```clojure
(defn phrase [notes]
  (p/for-beats 4 (p/pattern pulse :every [0.25 0.5] :hz notes)))

(def song (apply p/then (map phrase [[440 550 660] [330 440]])))
(song) ; two four-beat phrases, once
(p/stop! song)
```

`p/together` layers plans; `p/repeat-times` repeats a finite plan. Bound a pattern
with `p/for-beats` before passing it to `p/then`. Calling an unbounded pattern
loops it. Calls accept `:id` to give playback a name; stop that name when used.
Each segment starts its vectors afresh and measures `:offset` from its start.
Its end stops new notes; notes already playing and effect tails can finish.
The 16-part stress case renders correctly offline but exceeds real-time audio
deadlines at section changes; large live arrangements still need performance work.

For drones, use a sustaining body and `:legato true`:

```clojure
(p/definst tone [hz 110 amp 0.04] (csound/poscil amp hz))
(def drone (p/with-defaults tone :id :drone :legato true))
(drone)
(drone :hz 165) ; update the same voice
(drone :every [1 2] :hz [110 165 220] :fx (flanger :rate 0.2))
(p/stop! drone)
```

Legato updates k-rate controls without restarting the voice or its effects.
A changed instrument body or FX chain starts a new voice. `:dur` applies to
timed notes only. Keep `definst` bodies free of side effects: they build both
the timed and held DSP graphs. An i-rate-only body still supports timed notes,
but rejects legato. Recordings include held controls and stops.

Load a mono or stereo sample to get another callable instrument:

```clojure
(require '[panaeolus.sampler :as sampler] '[panaeolus.fx :refer [bbcut]])
(def drums (sampler/load "panaeolus-demo.wav"))
(drums) ; one note, lasting the file's duration
(drums :every [1] :dur 0.45 :start [0 0.25 0.5 0.75] :rate [1 0.75])
(drums :every [4] :dur 2 :fx (bbcut :bpm 120 :subdiv 8 :repeats [1 2]))
```

The sampler uses `flooper2`. Positional controls are `amp`, `rate`, `start`,
`end`, `cross`, `pan`, `db`, with defaults `1 1 0 1 0.005 0 -6`. Start/end are file
fractions, crossfade is seconds, and pan runs from -1 to 1. Rate changes pitch
and speed; zero holds the read position. Set `:mode :reverse` or
`:mode :pingpong` when loading.
Loop bounds and crossfade change at the next wrap; crossfades shorten the loop
period. `:legato true` keeps playback running across control edits.

`bbcut` uses stereo `bbcuts` and works with any instrument. Its controls are
`bpm`, `subdiv`, `bar`, `phrase`, `repeats`, `stutter`, `chance`, `envelope`;
defaults are `120 8 4 1 2 4 0 1`. These accept vectors. Edits restart the cut
phrase. Set its BPM explicitly; it does not stretch the source to match tempo.

Loading is silent and caches each path for the engine's lifetime. Use a new
path to load changed audio. The path also sets the default loop name; load with
`:id :drums` to keep the same loop when switching files, or choose separate IDs
to play two instances of one file.
Load large files before starting loops; decoding and level measurement run
synchronously in Csound. File sample rates above 384 kHz are rejected.
Samples loaded before finishing a take are embedded in the exported CSD,
including unused samples. Exports allow up to 64 MiB of embedded sample text.

## Plucks and levels

```clojure
(require '[panaeolus.instruments.pluck :refer [pluck fmpluck]])
(pluck) ; one note
(pluck 60 -6 :tone 0.7) ; MIDI note, dB, then named controls
(pluck :every [0.25 0.5] :note [60 67 72] :db [-6 -9])
(fmpluck :every [0.5] :note [48 60 55] :db -6 :ratio 1.5 :index 3
         :fx (flanger :rate 0.2))
```

`pluck` is a modal string with `tone` (0..1) and pick `position` (0.05..0.95).
`fmpluck` uses a fading FM index, with `ratio` (0.25..8) and `index` (0..8).
Both take `note` (MIDI 24..96), `db` (-96..12), `decay` (0.02..8 seconds to
-60 dB), `pan` (-1..1), and linear `amp` (0..4). Defaults are note 60, db -6,
decay 0.8, pan 0, amp 1, duration 1 second. String defaults: tone 0.5,
position 0.23; FM defaults: ratio 1.5, index 2.5. Positional order follows the
parameter declarations in `src/panaeolus/instruments/pluck.mal`. These are
retriggered voices, so `:legato true` is rejected. Patterns, composition, and
FX use the same calls as other instruments.

Built-ins use **0 dB = about -18 LUFS** on a reference phrase, with unity `amp`.
The default -6 dB gives about -24 LUFS. This is a shared mixing reference,
not full scale or a promise for every pitch, tone, decay, or note density.
Custom `definst` bodies still own their gain. New built-ins should use the
shared level helpers and the six-pitch calibration in `bun run plucks:render`.
That command also writes `dist/plucks-preview.wav`: string first, FM second.
The meter uses [BS.1770 K weighting and gates](https://tech.ebu.ch/docs/tech/tech3341.pdf);
no compressor runs on each voice.

Samples normalize to the same reference when loaded. The gain stays fixed
across playback and export; slicing, pitch changes, effects, and overlapping
notes can change the result. Normalization caps boosts at 24 dB and leaves
3 dB sample-peak headroom at db 0, amp 1. Very spiky or quiet files may stay
below the target. Silence stays silent. Use `:normalize false` at load time
to retain the old raw level and defaults (amp 0.5, db 0). The recorded disco
example opts out so its existing mix stays the same.

## Playground

Requires [Bun](https://bun.sh) 1.3.13 or later.

```sh
bun install --frozen-lockfile
bun run dev
```

Open <http://127.0.0.1:3000>, click **Start engine**, then evaluate the starter
text. Select forms to change them while playing. Use `PORT=3001 bun run dev`
for another port. The server enables SharedArrayBuffer on localhost.
The starter includes sampler examples and a generated demo sample. **Import
sample** accepts local WAV, AIFF, and FLAC files up to 20 MiB and prints a load
form. Files stay local and must be imported again after restarting the engine.

## Build and test

For a recorded 30-second disco session, run `bun run demo:render`. The render
uses six original samples and evaluates real Panaeolus forms at the times in
`examples/disco-demo/timeline.json`. That folder includes the WAV, sample files,
source generator, and `performance.mal`. Evaluate its forms at their marked
times; evaluating the whole file at once will replace the loops immediately.
`prompt.txt` gives another model the supported calls and timing rules for
making a new version. The source samples and render use this repo's license.

```sh
bun run build
bun run test
```

The build creates `dist/panaeolus.mal`, `dist/panaeolus.orc`, and
`dist/make-a-lisp.orc`. Use the supplied interpreter, then load the `.mal` bundle
with `(load-file "panaeolus.mal")` before starting patterns. This interpreter
build reserves the routing instrument range at startup. The playground uses
the two `.orc` files.
Tests cover timing, composition, held voices, samplers, plucks, loudness, effects, CSD replay, live edits,
bundle loading, and server isolation headers.
The build fixes Csound browser beta36's file sizes and reads in its copied
module so embedded samples and MAL bundles load correctly; it leaves the
installed package untouched.

The repo includes a fixed make-a-lisp interpreter snapshot, its license, and
source changes under `vendor/`; no sibling checkout is needed. Set
`MAKE_A_LISP_ROOT` to test another source checkout, or `MAKE_A_LISP_ORC` to use
another interpreter bundle. Source builds also need Python 3.9 or later.

CI runs on pushes and pull requests. Push a `v<package.json version>` tag to
run the release tests and publish versioned `.mal` and `.orc` files with SHA-256
checksums. The release workflow can also run manually for an existing tag.

GPL-3.0-only. The flanger derives from the earlier Panaeolus effect credited to
Iain McCurdy. The bundled make-a-lisp interpreter keeps its MPL-2.0 license.
