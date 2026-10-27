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

## Additive voices

```clojure
(require '[panaeolus.instruments.additive :refer [harmonic formant glass prism]])
(harmonic :every [0.25 0.5] :note [36 43 48] :tilt [0.8 1.5] :dur 0.2)
(formant :legato true :note 48 :formant 900 :bandwidth 450 :attack 0.4)
(glass :every [0.5 0.75] :note [60 67 72] :stretch 1.4 :dur 1.5)
(prism :every [2] :note [48 55] :stretch [1 1.04] :motion 0.3 :dur 1.8)
```

| Voice | Sound and controls |
| --- | --- |
| `harmonic` | 24 partials; spectral `tilt`, `odd` balance, stereo `detune` in cents, `width`. |
| `formant` | 32 partials; `formant` and `bandwidth` in Hz, low harmonic `body`, `vibrato` in cents, `rate` in Hz, `width`. |
| `glass` | 16 struck partials; frequency `stretch`, upper-partial `damping`, spectral `color`, `beating` in Hz, `width`. |
| `prism` | 32 partials; frequency `stretch`, spectral `tilt`, comb `spacing` in partials, `motion` in cycles/second, `depth`, `width`. |

All take MIDI `note`, `db`, ADSR in seconds (`sustain` is a level), `glide`,
`pan`, and `amp`. Width runs from 0 to 1. Each partial fades out before Nyquist.
`harmonic`, `formant`, and `prism` support held edits; `glass` retriggers its
decay. The declarations in `src/instruments/additive` list positional order,
defaults, and ranges. See `examples/additive.mal` for FX and composition.
`bun run additive:render` writes four preview WAVs to `dist/`.

Analyze a steady pitched recording with native Csound's
[HETRO utility](https://csound.com/docs/manual/hetro.html), then play it with
[adsyn](https://csound.com/docs/manual/adsyn.html) in the browser:

```sh
bun run build
bun run hetro brass.wav brass.mal --name brass --root 60 --duration 3 --partials 24
```

Use a source whose pitch matches MIDI `--root`. Options also include `--begin`
in seconds, `--channel` (one-based), and `--points` (breakpoints per track).
Analysis takes 0.05–30 seconds of one channel. Set `CSOUND_BIN` to choose a
native Csound executable. The local September 22 Csound 7 build has a HETRO
writer bounds bug; an affected build must be fixed before using this command.
An existing text `.het` input skips native analysis and still measures gain.
`examples/analysed-brass.mal` is ready to import without running the analyzer;
it defines `resynth` from an original synthesized source.

Import the generated `.mal` with the playground's **Import file**, then
evaluate the printed `load-file` form before starting loops:

```clojure
(brass) ; one note at the source pitch and duration
(brass :every [1] :note [60 67 72] :speed [0.5 1 2] :dur 0.8)
```

The module includes its analysis and measured gain. Pitch and positive
`speed` are independent; `:dur` stays in seconds. These finite tracks
retrigger, so they reject legato, reverse, and zero speed. Pitch is limited
by the highest analyzed frequency and engine sample rate. Very small control
blocks also raise the minimum speed to fit adsyn's time resolution. ADSR, `pan`, `amp`,
FX, and composition work as usual. Gain targets -18 LUFS at `db 0`, capped at
24 dB of boost and 3 dB of peak headroom. Imports are silent and cached;
recorded CSDs embed analyses loaded before the take finishes, including
unused ones. Limits: 64 analyses, 256 KiB per analysis, 8 MiB of embedded text.

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
parameter declarations in `src/instruments/plucks/voices.mal`. These are
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

## Synths

```clojure
(require '[panaeolus.instruments.synth :refer [hammer lush]])
(hammer) ; one struck note
(hammer :every [0.5 0.25] :note [48 55 60] :db -6 :metal 0.4)
(lush :every [2] :note [60 67 64] :db -6 :dur 1.5 :cutoff 2500)
(lush :legato true :note 48 :attack 0.4)
(lush :legato true :note 55 :cutoff 1600) ; edit without a new attack
(p/stop! lush) ; fade out, then let the effects finish
```

`hammer` uses six decaying resonances. `hardness` (0..1) brightens the strike;
`metal` (0..1) stretches the partials away from whole-number harmonics.
Defaults: note 48, db -6, decay 1.2 seconds to -60 dB, hardness 0.5, metal 0.3.
It retriggers each note and does not support legato.

`lush` uses band-limited saws, a triangle sub, and a stereo low-pass filter.
Defaults: note 60, db -6, cutoff 3500 Hz (20..20000), resonance 0.2 (0..0.8),
detune ±8 cents (0..40), width 0.7 (0..1), attack 0.04 s, decay 0.2 s,
sustain 0.75 (0..1), release 0.2 s, and glide 0.02 s (portamento half-time).
Attack/decay accept 0.001..8 s, release 0.001..4 s, glide 0..2 s.
Held edits preserve the voice and smooth pitch, filter, width, pan, and gain.
Timed notes fade out within `:dur`; short notes may end during the attack.
Stopping a held note starts its latest release; the route stays open for that fade and FX.

Both use MIDI notes 24..96, pan 0 (-1..1), linear amp 1 (0..4), and duration
1 second by default. Their dB reference matches the plucks and sampler.
Run `bun run synths:render` for `dist/synths-preview.wav`: hammer, then lush.
Custom held voices can opt in with `(p/with-release voice :release)` when
their DSP honors Csound's release flag and finishes within that control's
duration. Other held voices keep their immediate stop behavior.

## Instrument library

Sources live in `src/instruments/{drums,plucks,synths,brass,samplers}`;
shared controls live in `common`, and effects in `src/effects`. Built-in
instruments compile on first use. Calling one without arguments plays its
default note; `p/pattern` prepares it without playing. All accept `:every`,
independent parameter vectors, named/positional arguments, composition, and FX.

| Namespace suffix | Instruments | Main sound controls |
| --- | --- | --- |
| `instruments.drums` | `kick` | sweep, sweep-time, click, drive, decay |
| | `clap-808` | tone, width, spacing, tail, seed |
| | `high-conga`, `mid-conga`, `low-conga` | tone, bend, slap, decay |
| | `maraca` | tone, bandwidth, roughness, seed, decay |
| `instruments.synth` | `asfm` | ratio, index, asymmetry, cutoff |
| | `nuclear` | detune, cutoff, resonance, sub, width, drive |
| | `organ` | six drawbars, percussion, rotary, rotary-rate |
| | `pm` | three ratios and indices, brightness |
| | `pulser` | harmonics, cutoff, resonance, env, env-decay |
| | `sweet` | detune, pulse, motion, rate, cutoff, resonance |
| | `tb303` | wave, cutoff, resonance, env, env-decay, accent, drive |
| | `wobbly` | bpm, division, depth, shape, cutoff, resonance, sub, drive |
| | `pmdub` | ratio, index, spread, motion, depth, sub, drive |
| `instruments.brass` | `bass-trombone` | breath, bite, formant, brilliance, vibrato, vibrato-rate, growl |

These are new designs based on the old instruments' roles. The brass voice
needs no external tables. Noise uses fixed seeds so recordings replay exactly.
Each `.mal` file lists the parameter order, defaults, and bounds.
The new synths and brass support `:legato true`, ADSR controls, and pitch glide;
drums retrigger. A held edit keeps its oscillators and envelope running.
For `wobbly`, `:division` is beats per wobble at its explicit `:bpm` setting;
set that BPM when changing the session tempo.

All default to `:db -6`, `:amp 1`, `:pan 0`, and a one-second note.
The new voices use the same −18 LUFS reference at `:db 0`: six one-second
notes at MIDI 48, 55, 60, 67, 72, 79; drums repeat their default pitch.
Run `bun run catalog:render` for a WAV per voice. Changes to timbre, envelope,
register, or overlapping notes can change loudness and peak level.

```clojure
(require '[panaeolus.instruments.drums :as drums])
(require '[panaeolus.instruments.synth :as synth])
(drums/kick :every [1])
(drums/clap-808 :every [2] :offset 1)
(synth/tb303 :every [0.25] :note [36 36 43 48] :dur 0.12
  :cutoff [350 700 1400] :accent [0 0.8])
```

`sampler/pitched` and `sampler/stretch` load stereo or mono files for independent
pitch and time control with `mincer`. `pitched` starts at normal speed;
`stretch` starts four times slower. Both return the same callable instrument:

```clojure
(def texture (sampler/stretch "texture.wav" :fft 2048))
(texture :legato true :note 60 :root 60 :stretch 8)
(texture :legato true :speed 0 :position 0.4)
(p/stop! texture)
```

`note` and `root` are MIDI pitches (24..96). `stretch` is 0.125..64;
`speed` is −4..4, with negative values reversing and zero freezing playback.
`start`, `end`, and `position` are fractions (0..1); position is an offset
within the selected region. FFT windows can include audio just outside that
region. `lock` is phase locking (0 or 1).
ADSR and `:release` work as for synths. `:fft` is a load option:
512, 1024, 2048, 4096, or 8192; larger windows soften transients and add latency.
The player briefly feeds silence at startup to clear old phase history.
A file must contain one full window. Normalization follows the sampler's
shared reference; spectral edits can change the result. Split channel tables
are cached once, count toward the sample memory limit, and export with the CSD.

See `examples/catalog.mal` for a small arrangement and individual calls.

## Vocal instruments

`panaeolus.instruments.vocal` contains six voices. All use `:vowel 0..4` for
A, E, I, O, U; fractions blend adjacent vowels. These are timbre controls,
not a text-to-speech system. Each voice supports patterns, composition,
FX, and `:legato true` for held notes and live edits.

| Voice | Csound source | Sound controls beyond vowel and pitch |
| --- | --- | --- |
| `priest` | [fof](https://csound.com/manual/opcodes/fof/) | shift, bandwidth, octave: a five-formant singer |
| `cantor` | [fof2](https://csound.com/manual/opcodes/fof2/) | shift, bandwidth, grain, gliss, phase, octave: sliding vocal grains |
| `talkbox` | [fofilter](https://csound.com/manual/opcodes/fofilter/) | shift, bandwidth, edge, breath, ring: filtered buzz and noise |
| `vosim` | [vosim](https://csound.com/manual/opcodes/vosim/) | shift, pulses, damping, bend: pulse trains with five formants |
| `fm-voice` | [fmvoice](https://csound.com/manual/opcodes/fmvoice/) | register, tilt: the built-in FMVoices model; no plugin needed |
| `growl` | FM and vowel filters | index, ratio, depth, shift, bpm, division: moving formants with a sine sub |

`shift` moves the formants by −12..12 semitones without changing the note.
`bandwidth` (0.25..4) widens the resonances; `octave` (0..2, fractional)
thins grain pulses by octaves. Cantor's `grain` is 0.01..0.08 seconds,
`gliss` is −12..12 semitones per grain, and `phase` is 0..1.
Talkbox's `edge` and `breath` are 0..1; `ring` (0.25..4) scales filter decay.
VOSIM's `pulses` is an integer 1..8, `damping` is 0..1, and `bend`
(0.5..2) multiplies each pulse's frequency. FM voice's `register` is an
integer 0..3 and `tilt` is 0..99. Its vowel shapes differ from the other voices.

All have ADSR, glide, pan, and amp. The first five also have `vibrato` in
cents (0..50) and `vibrato-rate` in Hz (0.1..12). Growl uses the tempo controls
described below. Defaults use the same six-note −18 LUFS reference at
`:db 0` as the other instruments; `:db -6` lowers it by 6 dB. Timbre edits,
effects, and overlapping notes can change loudness. Run `bun run build` then
`bun run vocal:render` for previews.

```clojure
(require '[panaeolus.instruments.vocal :as vocal])
(require '[panaeolus.fx :as fx])
(vocal/priest) ; one note with defaults
(vocal/cantor :every [0.25 0.5] :note [48 55 60]
  :vowel [0 1.5 4] :gliss [-3 3 7] :dur 0.3)
(vocal/talkbox :legato true :note 48 :vowel 0 :attack 0.4)
(vocal/talkbox :legato true :note 55 :vowel 3.5 :breath 0.2)
(p/stop! vocal/talkbox)
```

`fx/formant` applies the same five-vowel filter to any instrument or sample.
It takes `vowel`, `shift`, `bandwidth`, `ring`, and `mix` (dry 0, wet 1).
For example, add `:fx (fx/formant :vowel [0 2 4] :mix 0.7)` to a loop.
Removing `:fx` on the next call removes the filter; the old route can ring
for up to two seconds. See `examples/vocal.mal` for more calls.

## Scanned instruments

`panaeolus.instruments.scanned` contains `scanned`, a smooth moving-string
voice, and `ripple`, a brighter voice with uneven masses and springs.
Both use Csound's [scantable](https://csound.com/manual/opcodes/scantable/)
with private tables per note and cubic audio readers. They need no external
matrix files. Patterns, composition, held edits, FX, and standalone CSD export
work as for the other synths.

| Control | Range | Effect |
| --- | --- | --- |
| `speed` | 0.1..4 | Speed of the model and its excitation, separate from note pitch |
| `mass` | 0.5..4 | Heavier masses move more slowly |
| `damping` | 0.1..8 | Velocity loss per second; higher values settle sooner |
| `shape` | 0..1 | Harmonic content of the initial and driven shapes |
| `drive` | 0..1 | Continuous excitation; zero lets the initial motion decay |
| `warp` | 0..0.8 | Bends the audio scan path |
| `spread` | 0..1 | Distance between stereo pickups; zero gives mono |
| `cutoff` | 200..18000 Hz | Output low-pass filter |

Both also take MIDI `note`, `db`, ADSR, glide, pan, and amp. They share the
−18 LUFS default-sound reference at `:db 0`; `:db -6` lowers it by 6 dB.
Changing the model or overlapping notes can change loudness. Large control
blocks cap spring speed to keep the model stable. Held edits keep the moving
state; a new note starts with fresh tables, which Csound frees when it ends.

```clojure
(require '[panaeolus.instruments.scanned :as scan])
(scan/scanned)
(scan/ripple :every [0.25 0.5] :note [48 55 60]
  :shape [0.2 0.8] :warp [0 0.4 0.7] :dur 0.4 :db -9)
(scan/scanned :legato true :note 48 :speed 0.7 :attack 0.4)
(scan/scanned :legato true :note 55 :mass 2 :shape 0.9)
(p/stop! scan/scanned)
```

See `examples/scanned.mal`. Run `bun run build` then `bun run scanned:render`
for a six-note WAV preview of each voice.

## Waveshaping instruments

`panaeolus.instruments.waveshaping` adds four voices. All support patterns,
held edits, composition, FX, and CSD export, with the same −18 LUFS reference
at `:db 0` and a default of `:db -6`.

| Voice | Shaping method | Main controls |
| --- | --- | --- |
| `cheby` | [Chebyshev polynomials](https://csound.com/manual/opcodes/chebyshevpoly/), up to eight harmonics | index 0.1..1, odd/even 0..1, tilt 0..3 |
| `wavefold` | Sine folding | folds 0..8, bias −0.8..0.8, body 0..1 |
| `power` | [Signed power curve](https://csound.com/manual/opcodes/powershape/) | curve 0.25..4, bias −0.8..0.8, rectify/body 0..1 |
| `phasewarp` | [Phase distortion](https://csound.com/manual/opcodes/pdhalf/) | bend −0.8..0.8, skew −0.6..0.6, sub 0..1 |

`motion` (0..1) moves each voice's main shape control at `rate` Hz (0.05..12).
`width` (0..30 cents) detunes the two channels; zero gives mono. Each has
`cutoff` (200..18000 Hz), MIDI note, ADSR, glide, pan, and amp.
`body` blends in the original sine; `rectify` blends towards full-wave
rectification. `sub` adds a sine one octave below. Higher `tilt` softens Cheby's
upper harmonics; changing `index` changes its spectrum nonlinearly.

Cheby fades harmonics before Nyquist. The other three shape at four times the
host sample rate and filter before downsampling, even when Csound has no sinc
converter. This reduces aliasing; it does not remove every alias at extreme
settings. Gain follows the shaper, so `db` and `amp` do not change its tone.
Shape changes and overlapping notes can change perceived loudness.

```clojure
(require '[panaeolus.instruments.waveshaping :as ws])
(ws/cheby)
(ws/wavefold :every [0.25 0.5] :note [36 43 48]
  :folds [0.5 2 4] :bias [0 0.3] :dur 0.3 :db -9)
(ws/phasewarp :legato true :note 48 :bend -0.6 :attack 0.4)
(ws/phasewarp :legato true :note 55 :bend 0.4 :skew -0.3)
(p/stop! ws/phasewarp)
```

See `examples/waveshaping.mal`. Run `bun run build` then
`bun run waveshaping:render` for a six-note WAV preview per voice.

## Layered, FM, and resonator voices

These voices live under their synthesis methods. Their names, controls, and
sound stay the same; imports from `instruments.drone` and `instruments.dubstep`
now use the namespaces below.

| Namespace (`panaeolus.instruments.*`) | Voice | Sound | Main controls |
| --- | --- | --- | --- |
| `subtractive` | `horizon` | Detuned saw/triangle layers with slow drift | detune 0..40 cents, drift 0..20 cents, color/motion/width 0..1, cutoff 200..14000 Hz |
| `subtractive` | `reese` | Detuned saw/pulse layers with moving stereo notches | detune 0..60 cents, color/depth/motion/width 0..1, notch 100..6000 Hz, rate 0.05..8 Hz, drive 1..5, cutoff 150..14000 Hz |
| `subtractive` | `wub` | Rhythmic ladder filtering with amplitude movement | cutoff 60..8000 Hz, sweep 0..6 octaves, resonance 0..0.85, drive 1..5, pulse 0.05..0.95, gate 0..1, detune 0..25 cents |
| `fm` | `undertow` | Slowly changing FM with a clean sub | index 0..6, ratio 0.25..5, motion/sub 0..1, detune 0..30 cents, cutoff 200..16000 Hz |
| `resonant` | `aether` | Seeded noise and sines through pitched resonators | focus/air/motion 0..1, stretch 0.8..1.3, bandwidth 0.01..0.5, detune 0..30 cents |
| `vocal` | `growl` | FM through moving vowel filters | vowel/depth 0..4, index 0..8, ratio 0.5..6, shift −12..12 semitones, detune 0..30 cents, cutoff 200..14000 Hz |

Horizon, Undertow, and Aether use `rate` in Hz (0.02..2). Horizon's `color`
blends triangle to saw. Aether's `focus` raises the upper resonators,
`stretch` changes their spacing, and `bandwidth` is a fraction of each
resonator's frequency. These three default to MIDI note 48, a 0.25-second
attack, and one-second release.

Undertow, Reese, Growl, and Wub filter their output before downsampling from
four times the host rate. Reese, Growl, and Wub add a centered sine at the
note's fundamental frequency; Undertow's sub sits one octave below.
`sub` (0..1) sets its amount. Reese, Growl, and Wub default to MIDI note 36,
a 10 ms attack, and 150 ms release.

Growl and Wub use `bpm` (20..400), `division` (0.125..8 beats per cycle),
`phase` (0..1 cycle offset), and `sharpness` (0.25..4). Higher sharpness gives
shorter openings. Their native LFO starts with each voice; held edits keep it
running, and changing BPM or division preserves its phase. Set `:bpm` explicitly
to match `p/tempo!`; these controls do not read the session tempo automatically.
Growl sweeps from `vowel` upwards by `depth`, capped at vowel 4. Wub sweeps
upwards from `cutoff`; `gate` adds amplitude movement. Reese uses its free-rate
motion control instead.

All six accept note, db, ADSR, glide, pan, and amp, with patterns, held edits,
composition, FX, and CSD export. A plain call plays a one-second note;
`:legato true` holds it. Held edits retain oscillator phases and filter state.
The six-note reference remains −18 LUFS at `:db 0`, with `:db -6` as the default.
Long sustains and timbre changes can change loudness.

```clojure
(require '[panaeolus.instruments.subtractive :as sub])
(require '[panaeolus.instruments.vocal :as vocal])
(sub/horizon :legato true :note 36 :attack 2 :db -12)
(sub/horizon :legato true :note 43 :color 0.8 :drift 12 :db -12)
(p/stop! sub/horizon)
(p/tempo! 140)
(sub/reese :every [0.5 0.25] :note [36 36 43 38] :dur 0.3 :db -9)
(vocal/growl :legato true :note 36 :bpm 140 :division 0.5 :db -9)
(p/stop! vocal/growl)
```

See `examples/held-textures.mal` and `examples/modulated-voices.mal`.
Run `bun run build` then `bun run subtractive:render`, `bun run fm:render`,
`bun run resonant:render`, or `bun run vocal:render` for previews by category.

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
Tests cover timing, composition, held voices, samplers, plucks, synths, loudness,
effects, CSD replay, live edits, bundle loading, and server isolation headers.
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
