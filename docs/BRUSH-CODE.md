# The brush code

A Superdood brush travels as one short line of text, its **code**: pasted in Brush Lab, sent from Brush Studio,
kept in the library and the community library. This page is enough to read and write one in another app (another
e-ink tablet, the web). Drawing the same marks as the app needs its engine as well (Java, `ModularStroke`): the code
says *which* brush, the engine says what its strokes look like.

The tables (each part's choices and dials) and 68 test codes with what they read as are in
[`brush-code.json`](brush-code.json), written from the app's own enums (`bash scripts/brush-code-json.sh`). The app's
`CodeSpecChecks` and the web's `docs/brush-lab/studio-src/code-spec-check.cjs` both check against it, in
`scripts/test_inkwash.sh`.

## Grammar

```
code     = name "." shape rest
M code   = name ".M" style part "_" part "_" part "_" part "_" part "_" part [ "_" nib [ "_" later ] ] { "_" segment } "." seed
name     = letter { letter | digit } { "-" ( letter | digit ) { letter | digit } }      a-z, 0-9
style    = one base-36 digit                 index into brush-code.json "styles"
part     = choice { dial }                   choice: one base-36 digit, index into that part's "choices"
dial     = option | step                     in the choice's "dials" order
option   = one base-36 digit                 a dial with "options": its option's index
step     = two base-36 digits, 00 to zz      any other dial: 0 to 1295 across min to max
later    = part                             After's: index into brush-code.json's "later" choices
nib, seed = 1 to 7 base-36 digits, at most 2^32 - 1
```

- **Case.** Codes are written in lower case but for the shape letter. A reader lower-cases the whole code, then
  upper-cases the letter after the first dot, and trims spaces around it.
- **Name.** A slug of the brush's name: lower case, every run of other characters a hyphen, starting at its first
  letter ("Polite Smear 2" is `polite-smear-2`, "2H pencil" is `h-pencil`; nothing left is `brush`). Before
  2026-10-31 a slug held letters only (`polite-smear`); those codes still match. The full name travels beside the
  code, not in it.
- **Shape letter.** `M` is the six-part brush (and its optional seventh, After), the only shape written today. `I` and `P` were the old pen and paint
  engines (Superdood converts them; another reader may skip them) and `B` a lab prototype. A new letter means a
  new shape.

## The six parts, in code order

| # | Key | Shown as | Owns |
|---|---|---|---|
| 1 | `tip` | Tip | the shape that touches the page |
| 2 | `medium` | Medium | what it lays down |
| 3 | `carry` | Rework (Smudge until 2026-11-01) | what it does to marks already there, and the fills |
| 4 | `paper` | Texture, on Medium's card (Paper, then Surface, until 2026-11-01) | how much of the page's surface shows through (its choice, a paper, read but drawn on the page's) |
| 5 | `life` | Drying | how long it stays wet (Instant, Quick, Slow, Wet); choices 3 to 6 are retired (below) |
| 6 | `hand` | Control | what pressure, tilt and speed change |
| (8th segment) | `later` | After (Later until 2026-11-01) | optional: what changes the page after the pen lifts (Float, Mold, Fading, Lemon Juice, Salt, Plastic Wrap, Feathering, Foxing, Letterpress, Torn Paper, Cross-Stitch, Cracks, Tarnish) |

Paper's choice is still written and read (since 2026-11-01, the surface spec, the app draws every brush on the page's
surface at the code's Texture, and Brush Lab picks no paper): a code keeps its paper, a reader that has no page may use it.

Brush Lab shows them in another order, the order a mark is made (Control, Tip, Medium, Rework, Drying, After;
2026-11-01), Paper's Texture a dial on Medium's card (the brush's Surface card until the paper went to the page); the
code's never changes. Keys
(parts, choices, dials, style names) are the code's and never change; labels are display words and may.

## Each part

The segment starts with the choice's index, then the choice's dials in its order, as many as the segment holds:

- **A step** reads as `min + step / 1295 × (max − min)`, written as `round((v − min) / (max − min) × 1295)` after
  clamping `v` to min..max. Then a dial with `"clamp"` is clamped to the choice's narrower range, and an `"integer"`
  one rounds.
- **An option** is its index, 0 to `options − 1`.
- **A segment that stops early** leaves the rest at their `"fresh"` values (a Control preset's are its own). So
  `a.Md0_0_0_0_0_0.1` is a whole brush.
- **Writers** leave off trailing `"trimmed"` dials at their fresh values, so a brush that doesn't use the newer dials
  keeps its older, shorter code. Readers don't need to know which are trimmed.
- **Hand Hatch** (tip choice 12, retired 2026-10-24) reads as Hatch with the dials the two share.

Example, `rake.Md5nzssi000000000_0llp6000000000at77_0_000_0_4weatg7000000ee0000nz0.b`: style `d`, 13, Anything goes.
Tip `5`, multi: Lines `nz` (863 of 1295 across 2 to 5, an integer dial) is 4, Spacing `ss` .8, Size `i0` 1.0004, then
four tapers and textures at `00`. Medium `0`, ink: Wet `ll` .6, and so on. Rework `0`, Off; surface `000`, Smooth at
Texture 0; drying `0`, Instant; Control `4`, the Brush preset, its cells written out. Seed `b`, 11. The tests in
brush-code.json list, for each code, every dial that isn't at its fresh value.

## After the six

- **7th, the nib.** A Stamp tip's image id (the nib store's, `StampNib`), written only by a Stamp that has one. `0` or
  none is the round stamp. Other tips ignore it.
- **8th, After** (2026-10-31, shown as Later until 2026-11-01): its choice and dials, as a part. Off (0), or none, is no
  After. A writer that needs it and has no nib writes the 7th as `0`. Which After goes with which Drying, and the After's own Drying a reader sets when
  the code's doesn't go with it (keeping the dials both share):

  | # | After | Instant | Quick | Slow | Wet | Its own |
  |---|---|---|---|---|---|---|
  | 0 | Off | yes | yes | yes | yes | |
  | 1 | Float | | yes | | | Quick |
  | 2 | Mold | | | yes | | Slow |
  | 3 | Fading | yes | yes | yes | | Quick |
  | 4 | Lemon Juice | yes | yes | yes | | Quick |
  | 5 | Salt | | yes | yes | | Slow |
  | 6 | Plastic Wrap | | yes | yes | | Slow |
  | 7 | Feathering | yes | yes | yes | | Quick |
  | 8 | Foxing | yes | yes | yes | | Quick |
  | 9 | Letterpress | yes | yes | yes | | Quick |
  | 10 | Torn Paper | yes | yes | yes | | Quick |
  | 11 | Cross-Stitch | yes | yes | yes | | Quick |
  | 12 | Cracks | yes | yes | yes | | Slow |
  | 13 | Tarnish | yes | yes | yes | | Instant |

  Choices 5 to 8, After's media (2026-10-31), each a macro dial then its time: Salt's Grains and Dries In (`grains`,
  `dryIn`), Plastic Wrap's Crinkle and Dries In (`crinkle`, `dryIn`), Feathering's Thread Length and Spreads In
  (`threads`, `spreadIn`, 1 to 20 s), Foxing's Spots and Ages In (`spots`, `dryIn`). Choices 9 to 13 (2026-11-01), the
  same way: Letterpress's Impression and Prints In (`impression`, `spreadIn`), Torn Paper's Torn Edge and Tears In
  (`tear`, `spreadIn`), Cross-Stitch's Stitch Size and Stitches In (`stitch`, 6 to 10 px, an integer, 8 fresh; `dryIn`),
  Cracks' Cracks and Ages In (`cracks`, `dryIn`), Tarnish's Tarnish and Tarnishes In (`tarnish`, `dryIn`). Every After's
  time is fresh at its least (2026-11-06): 1 s, Foxing's 10 s, Float's Floating Time 0 (0.75 s). Since then `dryIn` (Dries In) and
  `fadeIn` (Fades In) run from 1 s, not 10 s (1 to 600 s, 1 to 120 s); a step written before reads over the new range,
  a few seconds shorter at the low end (Fades In 20 s reads 11.8 s, Dries In 30 s 21.4 s, 2 min 112.5 s, 3 min 173.5 s, 5 min 295.6 s). They
  were never Drying choices, so an older version reads one as an After past its list: Off, and says it is newer.
- **Drying's retired mischief.** Before the 8th segment, Float, Mold, Fading and Lemon Juice were Drying's choices 3 to
  6, and a code may still hold one there. It reads as Drying and After: Float, Fading and Lemon Juice on a fresh Quick, Mold on
  Slow with that choice's Drying Time, Flow While Wet 0 and the rest fresh; the other dials are After's (Float's Drying
  Time its Floating Time). An 8th segment after one (not Off) is the newer word on After. Brush Lab offers them on After only (2026-11-01), never Drying.
- **Writers write the old way when they can**: a brush whose Drying is exactly what a retired choice implied is written
  with that choice in Drying and no 8th segment, as before it. So every brush an older version had keeps its code,
  byte for byte (the library, shared codes, the community library), and an older version reads it in full. Only a
  combination an older version can't hold (Fading on Slow, Lemon Juice on Instant, a Float over a Quick of its own) gets
  the 8th segment.
- **9th and on** are a newer version's.

## Seed

The brush's own randomness: which grain, which wobble, its dice. Two codes that differ only in seed are two brushes.
It is a 32-bit number in base 36.

## How the code grows

Every change to the M shape is on the end of something, so an older reader always reads what it knows:

| Change | How it's written | An older reader |
|---|---|---|
| A new dial | last in its choice's list, trimmed when fresh | reads the code; the extra characters are left over |
| A new choice | last in its part's list | sees a choice past its list |
| A new option | last in its dial's options | sees an option past its dial's |
| A new style | last in the styles | sees a style past its list |
| A new optional part (After, the 8th) | a new segment after the ones before it | sees more segments than it knows |
| Parts reordered, a dial's meaning changed | a new shape letter, never in M | sees a letter it doesn't know |

Never remove or reorder a choice, dial, option or style: retire it the way Hand Hatch was (it still reads).

## What a reader does with what it doesn't know

1. **A code it doesn't recognise at all** (no name, no dot, a bad seed, fewer than six parts, an empty part): not a
   brush code.
2. **A newer version's M code** reads what the reader knows, standing in the rest:
   - a style past its list: Anything goes;
   - a choice past its list: the part's first choice, fresh (Round, Ink, Off, Smooth, Instant, Steady);
   - an option past its dial's: that dial's fresh value;
   - an After choice past its list: Off;
   - characters left over in a part, or segments past After: ignored.

   It draws, and says so where it was opened: **"Newer version, some parts differ"**.
3. **A letter it doesn't know** (not B, I, M or P): it can't read it, and says **"Needs a newer version"**.

The app does this in `ModularCode.decode` and `ModularCode.newer`; Brush Lab's Open sheet and an import from Brush
Studio (a `*.inkwash.txt` file) show the note. Saving such a brush writes what was read, stand-ins and all.

**No version number.** The shape letter already is one, and every other change shows itself as more than the reader
knows (the table above), so detecting it tells exactly what a version field would, without a single byte more in
every code and QR. An older reader can't miss a newer code: there is no way to change an M code that leaves it
looking like a code it knows. If that ever stops being true, it's a new letter. (The community library keeps the app
version that made each code, for the record.)

## Not here

- Pictures each test code should draw: the parity pictures (roadmap step 2) will add them.
- How rolls and Randomize pick parts from a seed (`ModularStyle`, `ModularFeatures.late`, `seedOnly`): that makes new
  brushes, and reading a code doesn't need it.
