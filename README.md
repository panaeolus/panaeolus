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

## Playground

Requires [Bun](https://bun.sh) 1.3.13 or later.

```sh
bun install --frozen-lockfile
bun run dev
```

Open <http://127.0.0.1:3000>, click **Start engine**, then evaluate the starter
text. Select forms to change them while playing. Use `PORT=3001 bun run dev`
for another port. The server enables SharedArrayBuffer on localhost.

## Build and test

```sh
bun run build
bun run test
```

The build creates `dist/panaeolus.mal`, `dist/panaeolus.orc`, and
`dist/make-a-lisp.orc`. Use the supplied interpreter, then load the `.mal` bundle
with `(load-file "panaeolus.mal")` before starting patterns. This interpreter
build reserves the routing instrument range at startup. The playground uses
the two `.orc` files.
Tests cover timing, composition, held voices, effects, CSD replay, live edits,
bundle loading, and server isolation headers.

The repo includes a fixed make-a-lisp interpreter snapshot, its license, and
source changes under `vendor/`; no sibling checkout is needed. Set
`MAKE_A_LISP_ROOT` to test another source checkout, or `MAKE_A_LISP_ORC` to use
another interpreter bundle. Source builds also need Python 3.9 or later.

CI runs on pushes and pull requests. Push a `v<package.json version>` tag to
run the release tests and publish versioned `.mal` and `.orc` files with SHA-256
checksums. The release workflow can also run manually for an existing tag.

GPL-3.0-only. The flanger derives from the earlier Panaeolus effect credited to
Iain McCurdy. The bundled make-a-lisp interpreter keeps its MPL-2.0 license.
