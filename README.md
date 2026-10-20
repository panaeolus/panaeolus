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
Tests cover loop timing, independent patterns, arguments, effects, recording,
live edits, bundle loading, and server isolation headers.

The repo includes a fixed make-a-lisp interpreter snapshot, its license, and
source changes under `vendor/`; no sibling checkout is needed. Set
`MAKE_A_LISP_ROOT` to test another source checkout, or `MAKE_A_LISP_ORC` to use
another interpreter bundle. Source builds also need Python 3.9 or later.

CI runs on pushes and pull requests. Push a `v<package.json version>` tag to
run the release tests and publish versioned `.mal` and `.orc` files with SHA-256
checksums. The release workflow can also run manually for an existing tag.

GPL-3.0-only. The flanger derives from the earlier Panaeolus effect credited to
Iain McCurdy. The bundled make-a-lisp interpreter keeps its MPL-2.0 license.
