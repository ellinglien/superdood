# Brush system roadmap (2026-10-31)

Where the brush system goes next, as decisions to make. Each one has a recommended option and its alternatives.
This follows the brush review of the same day. The small tidy-up from that review (finish the merges, one word a dial,
Tools page by kind) is on `brush-tidy` and isn't repeated here. Java files are under
`app/src/main/java/io/github/mpdairy/monopaint/`.

Today: six parts, 125 dials (`BrushPart.java:20`, `PartDial.java`); a positional code
`name.M<style><six parts>.<seed>` (`ModularCode.java:10-18`); two engines, Java and the web lab's JS
(`docs/brush-lab/prototypes/modular-lab-src/`), whose codes are checked to match (`docs/brush-lab/studio-src/java-codes.sh`)
but whose pictures aren't.

---

## (a) The shape of a brush

**What's mixed today**
- Smudge holds the fills (Stay in Lines, Filled Shape; `PartChoice.java:135,143`).
- Drying holds both how paint dries (Instant, Quick, Slow, Wet) and the mischief (Float, Mold, Fading, Lemon juice).
  There's one choice per part, so you can't have a vanishing watercolour (`PartChoice.java:156-196`).
- Control holds Grey Shift and Gradient (`PartDial.java:285-301`). Those are about which grey you paint with, not
  how the hand moves the brush.

**Recommended: six parts that each own one question, plus one optional part for mischief.**

| Part | Owns | Moves in |
|---|---|---|
| Tip | the shape that touches the page | (as now) |
| Medium | what it lays down | (as now) |
| Paper | the grain it meets | (as now) |
| Drying | how long it stays wet and what wet paint does | Instant, Quick, Slow, Wet, gravity |
| Control | what moves the brush (see e) | pressure, tilt, speed… |
| Smudge | what it does to marks already there | Lift, Drag, Smear, Clear Water, **and the fills** |
| **Later** (optional) | what happens after the pen lifts | Float, Mold, Fading, Lemon juice; Grey Shift and Gradient too, or they go to Medium |

- **Smudge keeps the fills, under a wider label.** A fill does act on marks already there: it reads the page's
  lines. Relabel the part on screen to something like "Touch", and leave the key alone. Moving the fills to Medium
  would cost a code change for little gain.
- **Later is optional in the code**, written as an extra segment the way the Stamp nib is (`ModularCode.java:52`).
  Old codes read unchanged: their mischief stays in Drying and still draws. A brush with no Later shows no card.
  - Alternative: leave Drying as it is (no cost, and the combinations stay impossible).
  - Alternative: make the mischief a toggle on any brush, as Play's Fling is (DESIGN.md:175). That breaks PHILOSOPHY 1
    (no toggles that change what a tool does).

**The rule that replaces near-twin brushes.** When two brushes differ only by one dial, and a real material would do
both, the hand or the page picks:
- **pressure:** Blend's Press to Push, Water's Press to Lift (`PartChoice.java:272`);
- **where you start:** Fill in (`FilledShape.java:13`);
- **tilt**, then **time held**.

Write it into PHILOSOPHY.md as rule 5's corollary: *"A different behavior is a different tool, unless the hand
already chooses between them on real media."* The candidates the tidy-up leaves:
- Colouring pen and Fill in (press to flood);
- Wax crayon and Crayon scratch (press to scratch);
- Clear water and Comb.

## (b) The code as an open, versioned format

**Recommended: a short spec, test codes, and a version only when the shape changes.**
- **Version marker.** The letter after the dot already is one: `I`/`P` were the old engines and `M` is six parts
  (`BrushCode.java`, `LegacyBrushes.java`). Keep `M` for as long as only new choices, dials or optional segments are
  added. A new letter (`N`) only if parts are reordered or dials stop being positional.
  - Alternative: a `v2` field now. It adds length to every code and buys nothing yet.
- **"Needs a newer app".** Today a choice past the list makes `decode` return null and the code just doesn't read
  (`ModularCode.java:111`). Read what's known and stand the rest in with the nearest choice, as Hand Hatch becomes
  Hatch (`ModularCode.java:83`). Show "Made with a newer version: some parts look different."
  The community library stores the app version that made each code.
- **Names with digits.** The slug drops them ("Polite Smear 2" becomes `polite-smear`; `BrushCode.java:26`). Allow
  digits after the first letter, which old codes still match.
- **Spec + test codes.** Write `docs/BRUSH-CODE.md` (about 2 pages):
  - the grammar;
  - each part's choices and dials in order, built from `app-features.json` the way the Studio guide is;
  - the roll rules a seed depends on (`ModularFeatures.late`, `seedOnly`), because a seed's look is part of the format;
  - 30 test codes, each with the picture it should draw.

  The spec plus the test codes are what another app (reMarkable, Boox, Kindle Scribe, the web) needs to draw our
  brushes.

## (c) One engine for the Supernote and the web

Today it is a Java port of the JS prototype, kept "byte for byte" by hand. It drifts: the web caught up in one go
(commit 6eff802d), and in the Live pages tests Watercolour washy drew no wet paint in the browser.

**Recommended now: parity pictures.** The same strokes (a loop, a press ramp, a tilt sweep) go through both engines
for every preset and test code, and the pictures are compared within a tolerance. This fails the build on drift. It
catches what code round-trips can't. Size M. No cost on the Manta.

**Someday: one source.** The Manta is where speed matters, so Java stays the source.

| Option | How | Cost / risk |
|---|---|---|
| **Java to JS with TeaVM** (recommended to pilot) | compiles the pure Java engine (`docs/INTEGRATION.md:55`: no `android.*`) to JS or WebAssembly | Nothing changes on the Manta. Risks: web speed with doubles, and the build. Pilot on code reading and "This brush" words first, then one stamper. |
| Java to JS with J2CL | Google's compiler | Heavy toolchain (Bazel). Not worth it at our size. |
| JS on Android (QuickJS, WebView) | the app runs the web engine | Slower per sample on the Manta, where stroke latency is everything. No. |
| A third source (Rust/C to WebAssembly + NDK) | rewrite once for both | Rewrites an engine that works. Only for a third platform needing native speed. |

## (d) Images as assets

The Stamp nib already works the right way: the code carries a hash of the image, a store beside the library holds
it, and a missing one falls back to a round stamp (`StampNib.java:5-11`).

**Recommended:** generalize that store to "images by id" for nibs, own paper scans and tip shapes. The community
library hosts the images beside the codes, and Send to My Tablet carries both. A shared brush always draws something.
- Alternative: images inside codes (base64). That kills short codes and QR sharing.
- Size M. Risk: the word filter can't moderate pictures, so only the owner uploads images at first.

## (e) What moves the brush: one family of inputs

Control is already a small grid of inputs and what they change:
- pressure, tilt, speed and page tilt, each changing size, darkness or the nib (`PartDial.java:245-268`);
- Spiro's "Pressure sets…" rows (`PartDial.java:274-276`).

**Recommended:** make that grid *the* way inputs reach a brush. Each new input is a row, not a new module:

| Input | Example | Notes |
|---|---|---|
| pressure, tilt, speed, page tilt | today | |
| **time** | held still, the ink spreads; Fading and Lemon juice become "time sets darkness" | folds some mischief in |
| **the page** | darker where marks already are; Stay in Lines and Mold already read the page | |
| **sound** | the room's loudness sets size or scatter: a brush that draws the music | needs the microphone; check the Manta has one |
| **other people** (Live pages) | a brush that leans toward, or grows from, the other person's lines | Mold's bridges to nearby marks are the start |

- Few rows are on for any one brush. Each new input arrives as one Basic tool that says what it does
  (PHILOSOPHY 4), never as a setting.
- Alternative: one-off modules per idea (a Sound tip, a Live medium). Faster for the first one, and a mess by the
  third.

## (f) Live pages: brush and path, not only pixels

The Live pages spec sends the pixels a change made, on purpose (Drawpile's lesson,
`docs/superpowers/specs/2026-10-31-live-pages-design.md:24-32`). Keep that as the truth.

**Recommended, once (c)'s parity pictures pass:**
- Send the brush code and the pen's path *as well*.
- The receiver draws the real stroke for the draw-on effect and for Replay, then lays the sent pixels over it, so any
  mismatch heals.
- Later, on a slow link, the path alone can go first and the pixels after.
- Alternative: path only. Wet paint depends on timing and tilt, so two screens would differ. No.

## (g) How it stays few clear tools

- **Parts stay inside Brush Lab and Studio.** The drawing screen, the picker and the Tools page show brushes only. A
  new part or input is at most one card, shown only on brushes that use it.
- **Every new power arrives as one Basic tool first** (a sound pen, a Live mold ink), and becomes a dial only after
  you've drawn with it.
- **Each addition pays with a removal:** the "hand decides" rule should take away more brushes than (e) adds.

---

## Build sequence

| # | Step | Size | Unlocks | Try on the Manta? |
|---|---|---|---|---|
| 1 | Decide (a): the Later part yes or no; Smudge's new label; the "hand decides" rule in PHILOSOPHY.md | S | 4, 9 | no |
| 2 | Parity pictures, Java against JS, in the checks | M | 6, 10 | no |
| 3 | BRUSH-CODE.md spec + test codes; digits in names; read-what's-known with the newer-app note | S-M | 5, 7 | yes: a newer code opens on an older build |
| 4 | Near-twin merges by the rule (Colouring pen, Crayon scratch, Comb), one at a time | S each | | yes, each one |
| 5 | Community library stores the app version; the in-app Community tab and Send to My Tablet | M | 8 | yes |
| 6 | TeaVM pilot: code reading and "This brush" words compiled for Studio | M | 10 | no |
| 7 | Later part (if chosen in 1): the extra segment, one card in Brush Lab, Studio | L | 9 | yes: vanishing watercolour, lemon wash |
| 8 | Images by id: nib store generalized, own-scan paper, library hosting | M | | yes: own scan from a photo |
| 9 | Control as the input grid; time and page rows first (re-express Fading and Lemon); sound as the first new Basic tool | M then S each | 11 | yes, every row |
| 10 | Engine through TeaVM for Studio's drawing; the JS copy retired | L | 11 | no (web), yes (nothing changed on device) |
| 11 | Live pages send brush + path beside pixels; the other-people input row | M | | yes, Manta with a browser |

Steps 1-3 can start now and don't depend on each other. Step 2 is the one that makes everything after it safe.

## Decided, 2026-10-31

Elling chose the recommended option in all three: (1) mischief gets its own optional seventh part ("Later");
(2) "the hand decides" is a written rule (PHILOSOPHY.md, rule 5), near-twins merge one at a time, each tried on the
Manta; (3) Java is the master engine: parity pictures now, a TeaVM trial later.

## Added, 2026-10-31: Later's media

The owner asked for Later choices that are "not gimmicks but useful creative tools, possibly replicating physical
media", and chose four, each changing a mark after the lift the way the real technique does: **Salt** on a wet wash
(pale starbursts with a crisp darker rim, more in the darker, wetter parts), **Plastic Wrap** pressed on a wet wash (a
crinkled network of dark folds, paler facets), **Feathering** of ink on soft paper (fine threads wicking out along the valleys of the brush's own paper grain, more on rough or soft paper and barely on smooth,
the owner's "can it follow the paper grain?"), **Foxing** of an old book's page (small soft grey spots near and on
the mark over minutes). Each has one macro dial (Grains, Crinkle, Thread Length, Spots) and its time (Dries In,
Spreads In, Ages In); Salt and Plastic Wrap go with Quick or Slow, the other two with Instant, Quick or Slow, none
with Wet.

Why this shape: it reuses Fading's and Lemon Juice's machinery rather than a parallel one. At the lift the technique's
finished state is worked out once (`LaterMedia`, from the brush's seed and the page position) over the stroke's tiles
and as far past them as it reaches; the clock then carries each pixel from as laid to finished with FadingInk's
uneven, coarsely stepped timing, and they end as ordinary pixels. A mark's kind byte (was Lemon Juice's flag) names
which. Undo of the stroke gives the page before it, what grew past it too (those tiles join the stroke's Undo step).
The page file keeps them in a section of their own (LATR) so a version before them opens the page as saved, the marks
staying so, and Fading's section is written byte for byte as before. They are appended Later choices 5 to 8: an older
version reads one as Off and says the code is newer. Brush Studio (web) doesn't draw them yet; its code check waits on
them with the other Laters. Pictures: `scripts/later_media_pictures.sh` (docs/archive/pictures/later-media).
