# Module gaps: what other apps have that might fit (2026-10-31)

The owner asked: "are there tools or things that other apps use that we aren't now that might fit? just generally
thinking of expanding all of the modules as far as they can go", and earlier for "not gimmicks but useful creative
tools, possibly replicating physical media".

This report compares the app (branch `inkwash-v2-lab`) with the brush engines and tools of Procreate, Photoshop,
Krita, MyPaint, Rebelle, ArtRage, Clip Studio, Fresco, Concepts, Sketchbook, Infinite Painter, Paper, Tayasui and the
notebook apps (GoodNotes, Notability, the Supernote's own). Each idea is checked against PHILOSOPHY.md: few clear
tools, presets over sliders, physical media, the hand decides, grey only, and nothing asks for a decision while you
draw. Colour-only features are left out.

The inventory comes from `PartChoice.java`, `PartDial.java`, `PrintScreen.java`, `docs/brush-code.json`,
`ToolPresets.java` (157 presets), `NotebookTemplate.java`, CHANGELOG.md and the roadmap
(`2026-10-31-brush-system-roadmap.md`).

How to read an entry: **Name** (size), then *What / From*, *Physical*, *Fits* (part; before, during or after the
stroke), *Why*, and *E-ink* when it matters. Sizes: S is a dial or preset on existing machinery, M is a new choice or
mechanism, L is a new subsystem.

---

## Already have: don't rebuild

Popular features from other apps, and what the app calls them.

| Their feature (app) | Ours |
|---|---|
| Taper, pressure taper (Procreate, PS) | Tip: Taper In, Taper Out |
| Elliptical dab, ratio and angle (MyPaint) | Chisel, Flat Nib, Filbert: Nib Width, Nib Angle |
| Drawing-angle sensor (Krita) | a flat nib's fixed angle; Head Follows Stroke or Page |
| Scatter, count (PS, Procreate) | Stipple: Scatter, Uneven Dots |
| Airbrush build-up (PS), spray engine (Krita, ArtRage) | Stipple with Build-Up (Slow, Fast); Spatter; Charcoal dust |
| Bristle tips (PS), hairy and bristle engines (Krita) | Bristle (BristleSwarm): Hairs, Clump, Splay, Paint Load |
| Hatching engine (Krita) | Hatch and Hand Hatch tips; Hatching and Crosshatch screens |
| Grid engine (Krita) | Print Shading screens |
| Chalk (Krita), pastel | Charcoal, Crayon, the Pastel presets |
| Color smudge (Krita), mixer brush (PS), wet mix's dilution, charge and pull (Procreate) | Smudge: Drag, Smear, Pick Up Paint, Lay Down Paint, Own Paint, Paint Load, Starting Load |
| Dirty brush, clean or load after each stroke (PS, ArtRage) | Keeps Paint (HeldPaint); rubbing a grey loads paint (PaintRub, GreyRub) |
| Smudge length (MyPaint) | Keeps Paint, Pick Up Paint |
| Offset by speed (MyPaint) | Flick (Tip), Fling (Medium) |
| Grain, texturized (Procreate); texture, protect texture (PS) | Paper: 14 papers fixed to the page, Texture |
| Moving grain (Procreate) | Dry Brush streaks, Bristle |
| Wet edges (PS, Procreate) | Pooling; Quick's Wet Edge |
| Glaze rendering (Procreate) | washes glaze darker over dried paint (CHANGELOG 783, 978) |
| Transfer: opacity or flow by pressure (PS) | Control: Press Dark, Pressure Dark |
| Jitter and fuzzy sensors (Procreate, Krita) | Wobble, Uneven Dots, Patchy Ink; Grey Shift Random |
| Distance and fade sensors (Krita) | Gradient (Fade, To Grey, Cycle) over Length; tapers |
| Time sensor (Krita) | not yet: planned as roadmap (e)'s time row |
| Live watercolour and oils (Fresco, Rebelle) | Wash, Ink, Opaque; Drying Quick, Slow, Wet |
| Bleed, bloom, backruns, granulation (Rebelle) | Medium: Bleed, Bloom, Backruns; Drying: Grainy, Takes Ink (Drift, Vein), Plume |
| Drips, tilt the canvas (Rebelle) | Drying: Runs Downhill, Drips, Pools Downhill, Rock to Blend (gravity, PageTilt) |
| Blow, dry button, show wet (Rebelle) | the hair dryer on the wet drop (the owner lists blow as existing), WetIndicator |
| Wet-in-wet on a pre-wetted page | Clear Water marks how wet it leaves the page |
| Masking fluid (Rebelle) | Resist (masking fluid, peel with MaskPeelTouch) |
| Wax resist | Resist at Erasable 0; Crayon's wax repels washes |
| Indented "white line" stylus | Resist at wax: nothing takes on it. A preset "Embossing stylus" at most |
| Impasto, paint tube, gloop pen (ArtRage) | Opaque: Thick Paint; Impasto preset |
| Insta-dry, auto-clean (ArtRage) | the hair dryer; Keeps Paint off |
| Screentones (CSP) | Screentone medium (11 patterns), Print Shading (14 screens) |
| Halftone, Bayer dither, grain, Ben-Day, mezzotint, stipple, woodcut, engraving | Print Shading screens |
| Misregistration, dot gain, uneven ink (Riso and photocopy look) | Double Print, Dot Swell, Patchy Ink |
| Tone scrape (CSP) | the eraser scrapes Tone in streaks |
| Decoration and pattern brushes, stickers (CSP, Supernote, GoodNotes Elements) | Stamp: a nib cut from the page with the lasso (StampNib) |
| Ribbon brush (CSP) | Hake (HakeRibbon) |
| Lasso fill (Procreate, Tayasui, CSP) | Smudge: Filled Shape |
| Fill with close-gap (CSP) | Stay in Lines (about a 4 px gap holds), Fill in |
| Symmetry rulers, multibrush, kaleidoscope (CSP, Krita, Procreate) | Mirrors (reflection group, kaleidoscope) |
| Comic frame tool (CSP) | Comics and Storyboard templates |
| Grids: dot, iso, hex (Concepts, notebooks) | Dot Grid, Grid, Isometric, Hex, Lined, Music templates |
| Onion skin, tracing (Procreate Animation, ArtRage) | Tracing paper, See-Through Pages (Ghosts) |
| Shapes (all notebook apps) | Shapes: Line, Rectangle, Square, Oval, Circle, Filled (crisp, in the slot's grey) |
| Highlighter behind ink (GoodNotes) | Highlighter preset; Blend Mode Behind |
| Eraser by kind, stroke vs pixel (CSP, GoodNotes) | EraseKind: Everything, Pen, Paint, Dry Media |
| Eyedropper | the Dropper |
| Record, replay, timelapse (ArtRage, Procreate) | Replay Page (StrokeLog) |
| Rewind gesture (Paper) | two-finger undo, three-finger redo |
| Hover cursor (Procreate) | Nib Preview (HoverNib) |
| Marbling, suminagashi | Float (rings, drift); Comb, Wash comb |
| Twig and ink, toothbrush spatter, sponge, cotton bud | Messy stick, Spatter, Stipple sponge, Blend stump presets |

---

## Tip

**Wears Down: an erodible tip** (M)
- *What / From:* Photoshop's erodible tips (pencil, charcoal, pastel). The tip grows a flat facet as you draw, so a
  sharp point turns broad.
- *Physical:* a pencil going blunt, or a chalk or charcoal stick wearing a facet on the side you hold it.
- *Fits:* Tip dial (Round, Chisel), during the stroke. The facet forms on the tilt side: a sharp tip draws thin, and
  the worn facet draws broad when you tilt onto it. It sharpens again on its own (a new page, or after a rest), so
  nothing asks you to sharpen.
- *Why:* the hand decides: rolling onto the facet versus the point is how pencil artists switch between line and
  shade. It replaces "Sketch pencil vs Side pastel" pairs.

**Brayer: a roller that reprints what it rolls over** (M)
- *What / From:* ArtRage's paint roller; printmaking.
- *Physical:* a brayer, or a texture roller. It picks up ink it rolls over and lays it down again one roll later,
  fainter each turn.
- *Fits:* Tip "Roller" (a wide band, edges square to the stroke) with the Smudge pickup carried one circumference
  later. During the stroke.
- *Why:* real printmaking, and useful for backgrounds and textures. The behaviour is "useful but delightfully
  unpredictable". Press harder for more ink.

**Sketch: lines that knit to nearby lines** (M)
- *What / From:* Krita's sketch engine (Harmony, "web" and "sketchy" by Mr.doob).
- *Physical:* none exactly. It reads like a loose, hairy shading of a contour.
- *Fits:* Tip choice, during the stroke. Fine lines join the pen to recent points of the stroke and to nearby ink,
  within a reach.
- *Why:* a popular doodling tool, and "the page" as an input (roadmap e). Odd Ones group only. It fails the physical
  test, so it ranks low.

**Stamp scatter and turn** (S)
- *What / From:* PS and Procreate shape dynamics (scatter, angle jitter).
- *Physical:* a rubber stamp or a leaf dabbed by hand, never quite straight.
- *Fits:* Stamp dial "Loose" (one dial for scatter and turn), during the stroke.
- *Why:* foliage, crowds and borders from your own lasso nib. One dial, not three.

**Ruling pen** (S, preset only)
- *Physical:* a calligrapher's ruling pen: broad and wet when flat, a hairline on its edge, with breaks and spatter.
- *Fits:* Split Nib with Breaks, Spatter, and Tilt Nib.

**Fan brush** (S, preset only)
- *Physical:* a fan brush for grass, foliage and scumbling.
- *Fits:* Rake with Bristle-like dry brush.

**Dashed pen** (S, preset only)
- *Physical:* a dashed line for maps, cut lines and bullet journals.
- *Fits:* Stamp with a dash nib and Spacing. Breaks (random) already exists.

---

## Medium

**Masking tape** (S–M) *(see also Lasso to mask, whole app)*
- *What / From:* Rebelle and ArtRage stencils.
- *Physical:* painter's tape. Lay it, paint over it, peel it, and you get a crisp straight edge (horizons, borders,
  panel gutters).
- *Fits:* a Resist preset whose Tip lays a straight band from pen-down to lift, with slightly torn ends. Before the
  paint (it's laid first), and peeled after, as masking fluid is (MaskPeelTouch).
- *Why:* mostly existing machinery, real technique, no settings.

**Washi tape** (S–M)
- *What / From:* the journal apps' stickers and tapes.
- *Physical:* journal tape: a straight, slightly see-through grey strip with torn ends and a pattern.
- *Fits:* Medium Opaque at partial Opacity, a straight band, a Screentone pattern in it. During the stroke.
- *Why:* journal-useful. Could share masking tape's tip, but it is a different tool because it stays and doesn't
  peel.

**Torn paper (collage)** (S–M)
- *What / From:* collage in Procreate and Paper; junk journaling.
- *Physical:* a scrap of grey or textured paper, torn, glued down.
- *Fits:* Filled Shape with a deckle (torn) edge, filled with the page's own Paper grain at the grey picked. After the
  lift, as Filled Shape fills now.
- *Why:* collage is a real journaling medium. One brush, no dialog.

**Metalpoint (silverpoint)** (S)
- *Physical:* a silver stylus on a prepared ground. It makes a pale, even grey line that won't erase, and it darkens
  (tarnishes) over hours.
- *Fits:* Graphite hard, Erasable 0, capped darkness; the darkening is the Later idea Tarnish (below).
- *Why:* grey-only by nature. A whole medium from existing dials.

**Ink that runs out (dip pen)** (S)
- *Physical:* a dip pen: dark and blobby right after dipping, dry and scratchy before the next dip.
- *Fits:* Smudge's Paint Load, opened to Ink. Re-dip by rubbing a grey (PaintRub), as oils load now. During the
  stroke.
- *Why:* the hand decides when to dip, and there is no setting.
- *Check first:* whether Paint Load already works with Ink.

**Blue-noise screen** (S)
- *What / From:* stochastic (FM) screening; the error-diffusion look of classic Mac (Atkinson) and Floyd-Steinberg
  dithering.
- *Physical:* modern print screening.
- *Fits:* Print Shading screen, fixed to the page. A precomputed threshold tile (void-and-cluster) slots into
  PrintScreen's per-pixel threshold as Bayer does.
- *Why:* smoother than Bayer (ordered) and Grain (white noise), and it has the 1-bit "old Mac" charm.
- *E-ink:* 1-bit, so it shows exactly as drawn.

**Focus lines and speed lines** (S–M)
- *What / From:* Clip Studio's focus-line and speed-line tools.
- *Physical:* manga inked with a ruler toward a point.
- *Fits:* Screentone patterns "Rays" (radial lines toward the stroke's start) and "Speed" (parallel lines along the
  first heading). Unlike the other screens, these are fixed to the stroke, not the page. During the stroke.
- *Why:* comics use them (Comics and Storyboard templates, the Daily Prompt's four panels). The stroke places them,
  so there are no handles.

**Guilloché screen** (S, low)
- *Physical:* banknote engraving: concentric wavy rings.
- *Fits:* a Print Shading screen; shares Spiro's curve maths.

**Correction tape** (preset only)
- *Fits:* Opaque White, a straight band. It needs the straight-band tip from masking tape.

---

## Smudge

**Palette knife / Scrape** (M)
- *What / From:* Rebelle and ArtRage palette knives; sgraffito in wet paint.
- *Physical:* a knife or brush handle scraped through paint. Into wet watercolour it bruises the paper, and the line
  goes dark. Into damp paint it lifts a light line, with pushed paint ridged at its edges. On opaque paint, the flat
  blade smears in hard-edged planes with skips on the tooth.
- *Fits:* a Smudge choice "Scrape", during the stroke, on paint already there.
- *Why:* the hand and the page decide: wetness at the moment decides dark or light, and pressure decides how deep, so
  it is one tool. Crayon's Scratch does the wax case, and Comb does the rake case. This fills the gap between them.

**Kneaded eraser** (S, check first)
- *Physical:* a putty eraser that lifts charcoal or graphite partly, by pressure. You dab or drag it for highlights.
- *Fits:* Smudge Lift on dry media, during the stroke.
- *Check first:* Lift takes "the loose paint only". If Charcoal and Graphite count as loose, this exists as Damp
  tissue or Lift and needs only a preset ("Putty eraser").

**Fingerprint in finger smudges** (S)
- *Physical:* rubbing charcoal with a finger leaves faint ridge marks.
- *Fits:* the finger set to Smudge (FingerTool) lays a fingerprint texture into its Smear.
- *Why:* small, real and mischievous. No setting, and only the finger does it.

---

## Paper

**Rubbing plate (frottage)** (M, after roadmap d)
- *What / From:* Max Ernst's frottage; the ArtRage and Procreate grain image.
- *Physical:* a leaf, a coin, wood grain or a brick under the page. Only the side of a crayon or pencil, rubbed,
  shows it.
- *Fits:* a Paper choice whose grain is a large picture (images by id, roadmap d) that only tilted or light pressure
  reveals. Before the stroke (it's the ground).
- *Why:* physical, and the hand decides (tilt reveals it). Your own scans make it personal.

**Paper that wears** (M, low)
- *Physical:* scrubbing or heavy erasing roughens paper, and later washes go darker and blotchier there.
- *Fits:* Paper, during the stroke. A per-page wear map raised by the eraser and by Clear Water scrubbing; Paper's
  tooth reads it.
- *Why:* real, but subtle. Try it only if erasing feels too clean.

**Own scanned paper** is already planned (roadmap d).

---

## Drying

Drying already covers most of what other apps have (Quick, Slow, Wet, gravity, the hair dryer, granulation, blooms,
backruns). There are two small gaps.

**Dries lighter** (S, low)
- *Physical:* watercolour dries about 10 to 20% lighter, and painters plan for it.
- *Fits:* a Slow dial, after the stroke.
- *Why:* real, but it may read as a bug. It needs a hand trial.

**Cockling** (M, low)
- *Physical:* big wet washes buckle the paper, and the paint pools in the valleys.
- *Fits:* Slow's Pools Downhill, but toward a low wave map of the page instead of the tablet's tilt. After the stroke.

---

## Control

**Steady Hand: smoothing** (M)
- *What / From:* Procreate StreamLine and Stabilization, Photoshop smoothing (pulled string), Krita stabiliser,
  MyPaint slow tracking, Clip Studio post-correction.
- *Physical:* a slow, deliberate inking hand, or a mahl stick.
- *Fits:* a Control preset (Off, Some), during the stroke. Smoothing scales with speed: fast strokes come out as
  clean curves, and slow ones keep their detail.
- *Why:* inking and lettering are the most-asked tools in every app, and the app has none. It is one preset, not a
  slider (Procreate's three sliders are the trap).
- *E-ink:* panel latency already trails the pen, and smoothing adds to it. Keep it short. Catch up at the lift (PS's
  "catch-up on stroke end") so the end of the line is never missing.

**Long Stick: a brush with weight** (S, once Steady Hand exists)
- *What / From:* Krita's dyna engine (mass, drag).
- *Physical:* Matisse drawing with charcoal tied to a long bamboo stick, or a heavy, loaded brush. Lines lag, swing
  and overshoot.
- *Fits:* the same pulled-string engine with mass, as a Control preset. During the stroke.
- *Why:* physical, and loosening. Delightfully unpredictable, but it says what it does.

**Your Hand: pressure calibration** (S)
- *What / From:* Clip Studio's "adjust pressure by drawing"; Procreate and Supernote pressure curves.
- *Fits:* a once-only setting, in the menu rather than while drawing: draw three strokes light, normal and hard. It
  scales every brush's Response.
- *Why:* heavy and light hands get the same brushes. Presets elsewhere stay tuned. It is a setting outside drawing,
  not per brush.

**Time and page rows** are already planned (roadmap e).

---

## Later

**Through the Page: set-off and bleed-through** (M)
- *Physical:* close a notebook on wet ink, and a faint mirrored print lands on the facing page. A heavy marker bleeds
  a ghost onto the next page.
- *Fits:* a Later choice, after the stroke. When you turn the page while the mark is still wet, a faint mirrored copy
  goes onto the facing or next page. Wetness and pressure decide how strong, and nothing happens once the mark is dry.
- *Why:* the most journal-specific physical effect there is, and gently mischievous. It fits rule 4 (the tool says
  what it does).
- *E-ink:* it lands on a page not in view, so there's no refresh cost.

**Cracks (craquelure)** (M)
- *Physical:* gouache or oil laid thick cracks as it dries, in a fine network that is wider where the paint is
  thicker.
- *Fits:* a Later choice for Opaque, Slow, Thick Paint. After the stroke. It reuses Plastic Wrap's network and
  LaterMedia's staged reveal.
- *Why:* real, and the thickness the hand laid decides where it cracks.
- *E-ink:* the coarse stepped timing already suits the panel.

**Tarnish** (S)
- *Physical:* metalpoint tarnishing, or iron-gall ink, which goes on pale grey and darkens to black.
- *Fits:* Lemon Juice's machinery starting visible (pale) instead of invisible. A Later variant, or a "Starts at" on
  Lemon. After the stroke.
- *Why:* turns Metalpoint and "Iron gall ink" into real media at almost no cost.

**Reticulation (litho tusche wash)** (M, low)
- *Physical:* a lithographic tusche wash dries in a wrinkled, reticulated grain.
- *Fits:* close to Plastic Wrap. Only if printmaking presets grow.

---

## Whole-app tools

**Hold to Snap** (M)
- *What / From:* Procreate QuickShape, Sketchbook predictive stroke, Paper's Diagram, and the GoodNotes, Notability
  and Supernote shape tools.
- *Fits:* during the stroke. Draw a line, oval, box or arrow and hold still at the end: it becomes the clean shape,
  drawn with the same brush (its texture, grain and taper kept).
- *Why:* the hand decides (holding still picks it). It can replace the Shapes menu and its mode, which pays with a
  removal: Shapes today are crisp shapes in the slot's grey, not the brush.
- *E-ink:* one region redraw at the snap.

**Ruler (and French curve)** (M)
- *What / From:* ArtRage and Tayasui stencils and rulers, Sketchbook's French curve and ellipse guides, Krita's
  assistants.
- *Physical:* a straightedge laid on the page. Any pen drawn near its edge runs along it.
- *Fits:* Play, placed and moved the way Mirrors' lines are (reuse that UI). The pen keeps its pressure and texture.
  During the stroke.
- *Why:* tables, panels and borders in a journal, with any brush. One tool, no snapping settings.

**Lasso to Mask (stencil)** (S)
- *What / From:* Rebelle selection masks, ArtRage stencils.
- *Physical:* a cut stencil: spray or wash over it, then peel.
- *Fits:* a lasso action that turns its loop into masking fluid (the Resist plane exists). Before the paint.
- *Why:* existing pieces joined. Stipple with Build-Up through a stencil is real stencil spray.

**Perspective grid template** (S)
- *What / From:* Procreate, Krita and Clip Studio perspective guides, without the snapping.
- *Fits:* a NotebookTemplate (one-point and two-point), like Isometric.
- *Why:* the useful 80% for no UI.

**Flipbook** (S–M)
- *What / From:* Procreate Animation Assist, Sketchbook FlipBook, FlipaClip.
- *Fits:* Play: a notebook's pages played in turn. Tracing paper ghosts are already the onion skin.
- *Why:* delightful, and it reuses ghosts and page turns.
- *E-ink:* about 2 to 5 frames a second in a fast mode, which suits a flipbook.

**Star a page** (S)
- *What / From:* Supernote's stars, headings and keywords; GoodNotes' outline.
- *Fits:* page previews show starred pages first or marked.
- *Why:* a journal needs a way back to the good page. One tap, no tags.

**Pinwheel mirrors** (S, low)
- *What / From:* Procreate's rotational symmetry, mandala apps.
- *Fits:* Mirrors with turns only, no reflections (a cyclic group beside today's dihedral one).

**Erasing leaves a ghost** (S, low)
- *Physical:* real erasing never comes clean. A faint trace stays, and the scuffed paper takes the next mark darker.
- *Fits:* only as an eraser kind or a notebook rule, never in the plain eraser (rule 4).

**Zoom writing box** (M–L, low)
- *What / From:* GoodNotes, Notability and Noteshelf.
- *Fits:* write big in a strip, and it lands small on the page.
- *Why:* useful for small handwriting, but the Manta's screen is big and Zoom exists. Wait until it's asked for.

---

## Top 10

| # | Addition | Part | When | Size |
|---|---|---|---|---|
| 1 | Hold to Snap (replaces the Shapes mode) | whole app | during | M |
| 2 | Steady Hand smoothing, with Long Stick as its sibling | Control | during | M (+S) |
| 3 | Masking tape and Lasso to Mask | Medium (Resist), lasso | before | S–M |
| 4 | Palette knife / Scrape (wet makes it dark, damp makes it light) | Smudge | during | M |
| 5 | Ruler and French curve | whole app (Play) | during | M |
| 6 | Wears Down (erodible tip) | Tip | during | M |
| 7 | Through the Page (set-off and bleed-through) | Later | after | M |
| 8 | Your Hand pressure calibration | Control (setting) | before | S |
| 9 | Metalpoint, with Tarnish | Medium preset, Later | after | S |
| 10 | Brayer | Tip and Smudge | during | M |

Next, all S: blue-noise screen, perspective template, Cracks, washi tape, torn paper, focus lines, flipbook, stamp
scatter.

## Skip: popular features that don't fit

- **Liquify, deform, swirl and pinch** (Procreate, Krita deform, Fresco): digital warps with no physical medium. Smear
  and Press to Push cover pushing paint.
- **Dual brush** (PS, Procreate): a second tip masking the first is a settings stack. Dry Brush and Paper already give
  broken marks.
- **Per-dab jitter sliders for everything** (Procreate, PS): a slider per wobble is decision overload. One "Loose"
  dial where it matters (Stamp).
- **Colour dynamics, spectral mixing, Copic libraries, gradient maps, recolour**: colour only. Grey Shift and
  Gradient are the grey equivalents and exist.
- **Clone stamp, history brush, art history brush** (PS, Krita): no physical medium. Stamp from the lasso covers
  "copy this bit".
- **Dodge, burn, blur, sharpen, filter brushes, adjustment layers**: photo editing, not drawing.
- **Perspective rulers with vanishing-point snapping** (CSP, Krita): setup-heavy handles. The template gives most of
  it.
- **Vector eraser, erase to intersection, stroke eraser** (CSP, GoodNotes): the pages are raster. Large to add, and
  EraseKind already targets marks by kind.
- **Scribble to erase** (GoodNotes, Boox): a plain pen that erases when you zigzag is a surprise (rule 4), and it
  clashes with Scribble and hatching.
- **Barrel rotation and tangential-pressure sensors**: the Manta's pen doesn't report them. Tilt Turn uses tilt
  instead.
- **Text tool, laser pointer, audio synced to ink** (notebook apps): not drawing. Magnets already give words.
- **Glitter, metallics, burnt edges** (ArtRage, Procreate): lighting and colour effects that e-ink grey can't carry.
- **Full animation timeline**: large. The flipbook is the journal-sized version.
- **Wrap-around tiling mode** (Krita): Mirrors already copies; two parallel mirrors give a frieze up to the copy limit.
- **Aquatint screen**: reads the same as Grain and Mezzotint, which exist.
- **Separate "wet-in-wet", "blotter" and "gloop pen" tools**: they exist as Clear Water, Damp tissue and Impasto.
