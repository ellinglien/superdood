# Vector Ink — design (draft)

2026-11-04. Status: phase 1 built (branch `vector-ink`); see "Phase 1 as built" for where it differs from the draft.
Phase 2: the Lasso built (`vector-ink-2`), vector lines in the PDFs built (`pdf-vector-ink`, 2026-11-05; over a 2× sharp
picture on pages with kept dry strokes, `pdf-sharp-2x`, the dry brushes spec's section 8); SVG, Draw
Together's lines and the browser's sharp zoom not yet.

## What the owner asked

- "could we introduce a vector line brush? or medium?"
- "not necessarily editable lines in the app. i just want to zoom in and have it be clean."
- "erasing part should be part of it."
- "clean ink to start, but possibly expanding to other ones."

## Goal

Lines that stay crisp at every zoom (up to the app's 800%) and in big exports, drawn and erased like any other ink. Nobody edits points or handles: it is still drawing, it just doesn't go blocky.

Not goals: editing a line's points, bezier handles, an illustration mode, more settings.

Against PHILOSOPHY.md (few clear tools, no settings): this adds one layer kind and one medium, no options. The owner asked for it; it earns its place only if a Line layer reads as plainly as "this layer stays sharp", with nothing to set.

## Why lines go blocky today

- A page is pixels at the screen's own size (at most 1920×2560, `ToneDocument.MAX_PIXELS`); 100% is one page pixel per screen pixel.
- Zooming in scales those pixels up with nearest-neighbour sampling (`viewport_bitmap.c`), so a 400% view shows 4×4 blocks.
- Bigger dithered PNG exports upscale the same pixels (bilinear), so they are bigger, not sharper. There is no SVG writer.

## How other apps do it (for later reference)

- **Separate layer kinds** (Adobe Fresco, Krita, Affinity): pixel layers and vector layers side by side, each with its own tools. Simple to understand.
- **Stroke + recipe, drawn again at any zoom** (Clip Studio's vector layers, Concepts, reMarkable, the Supernote's own notes): keep the pen samples and the brush's settings, and draw the brush along them again at the zoom in use. Textured brushes can work this way if the brush draws the same every time.
- **Two erasers** (GoodNotes, Notability, Clip Studio): one removes whole strokes, one cuts strokes into pieces.

This design starts with the first and leaves the door open to the second.

## The design, phase 1: Vector Ink on a Line layer

### What the owner sees

- A new kind of layer, a **Line layer** (working name), made from the layers panel like any other.
- On it, one medium: **Vector Ink** (the owner's name, 2026-11-04): a smooth pen line, its size and shade from the toolbar as now, a little thicker with pressure.
- The eraser on a Line layer cuts lines where it passes (the cut ends are cut, not rounded).
- Zoom in: Line-layer lines are drawn sharp at that zoom. Everything else looks as it does today.
- Tools that can't make sharp lines (smudge, wet paint, water, fill, textured brushes) don't draw on a Line layer; picking one there asks for a pixel layer, the way a locked page refuses today.
- Hold to Snap and Shapes on a Line layer make sharp lines and shapes.

### What is kept

- Per Line layer, a list of strokes. A stroke: its shade, its size, cap flags for each end (round, or cut by the eraser), and its points (x, y in 1/8 page pixel, pressure).
- Points are lightly smoothed when the pen lifts (a centripetal Catmull–Rom pass), which also removes the pen's jitter.
- Saved in a new page section `LINE` (the same way `RPLY` and `SURF` were added: written only when a page has Line layers; older app versions skip it).
- The Line layer's pixels are still saved as an ordinary layer drawn at 100%. So older versions, thumbnails, Replay, Draw Together, PSD and every pixel path keep working unchanged: they see a normal layer.

### Drawing

- At 100%: the layer's pixels, drawn from the strokes, as any layer.
- Zoomed: the zoomed view (`ViewportBitmap`) draws the layers under the Line layer as now, then the strokes as filled shapes (a variable-width outline per stroke, anti-aliased, then turned into the app's greys and dither at screen resolution), then any layers above it.
  - Needs: the raster layers below and above the Line layer composited apart (`display` is one composite today). If that costs too much memory on the Manta, phase 1 can require the Line layer to be the top layer.
- The drawing while the pen is down uses the fast pen path as now; the sharp version appears when the stroke is done (as Hold to Snap's redraw).

### Erasing (cut)

- The eraser's path is a chain of round dabs. Each stroke's points inside it are removed; a stroke crossed in the middle becomes two strokes. Where it was cut, the new end sits on the eraser's edge and is marked "cut" (flat).
- One Undo step per eraser stroke, as now (Undo keeps the stroke list before; it is small).
- A whole-line eraser can come later if wanted; not in phase 1.

### Export

- PNG at a bigger size: raster layers upscaled as now, Line layers drawn sharp at that size.
- PDF: the page picture as now (vector PDF is phase 2; built 2026-11-05, see "Vector lines in the PDFs").

### Limits

- A page holds at most so many points across its Line layers (to be set from the Manta's speed; Replay's cap is 100k samples).
- A Line layer counts toward the 8 layers a page can have.

## Phase 2 (later)

- Lasso on a Line layer works on the lines: Cut/Delete cut like the eraser; Move and Duplicate move or copy the pieces.
- Export Line layers as real vector: SVG, and vector paths in the notebook PDF (good for print and plotters). The PDF
  is built (see "Vector lines in the PDFs"); SVG is not.
- Draw Together sends Line strokes as points so guests draw them sharp too (today guests get pixels).
- The browser version draws Line layers sharp when zoomed (it runs the same Java).

## Phase 3 (maybe): dry brushes on Line layers

- The engine already draws the same marks from the same samples, brush and seed (`StrokeLog`), so a dry brush's stroke can be kept as its samples plus brush code and drawn again at the zoom in use, as Clip Studio does.
- Fits: pen, liner, pencil, charcoal, crayon, hatch, stipple, stamps — brushes that don't read the page under them.
- Never fits: smudge and carry, wet paint and float, water, washes grooved by the ink layer, Behind and Invert blends, light-over-dark sticks, screened prints. They read what's already there, so they stay pixel-only.
- The hard parts:
  - The engine draws in page pixels; drawing a stroke at 4× means its stamps, spacing and texture at 4×.
  - Paper grain: the noise grains can be made finer; the scanned ones (256² scans) can't gain detail and would blur. A stroke drawn at 4× must also meet the same grain it met at 100%.
  - Speed on the Manta: redrawing textured strokes on every pan needs a tile cache.

## Open questions for the owner

Answered 2026-11-04: the medium is **Vector Ink** (the owner: "seems clear"); it thickens with pressure (yes); phase 1 may need the Line layer on top (sure, for now).

Answered 2026-11-04, the wash/ink pairs (each layer in the panel is a wash below and an ink above, `InkWashLayers`): **A**. Vector Ink is a pair's ink half; its wash half stays an ordinary watercolour layer underneath, so one layer gives crisp lines over soft washes, and washes still groove along the lines (they read the ink half's 100% pixels, which are kept). On that pair, pixel pens don't draw on the ink half. Phase 1's "on top" means the top pair.

Wording: where this spec says "Line layer", read "a pair whose ink is Vector Ink"; there is no separate layer kind in the panel.

## Phase 1 as built (2026-11-04, branch `vector-ink`)

- **Where it is picked:** one Basic tool, **Vector ink** (Pens, Fineliner, "stays sharp"; `ToolPresets`, `VectorInk.CODE`): Steady line's
  pen at a seed of its own, which the Tools page's sample draws. A brush is Vector Ink when its settings are that tool's
  (`VectorInk.is`); a copy changed in Brush Lab is an ordinary pen. No new setting, mode or layer kind in the panel.
- **How a pair becomes Vector Ink's:** the first Vector ink stroke on the top pair's empty ink half; its Undo makes it plain
  again. Changed the same day (the owner hit "Top layer only" on a page with pen marks and tracing paper over it; "make
  it just work"): when the top pair's ink holds other marks, or the page is an odd old one, a pair is put on top for it
  (said nothing: the owner keeps the brush line's right for Draw Together's status; Vector Ink's other notes show beside
  the tool in use), the stroke and the pair one Undo step, and the pair that was selected is selected again.
  A lower pair selected draws on the top one. Layers shown over the page (tracing paper) don't matter: the sharp lines
  draw over them when zoomed. Refused only with four pairs and no room ("Layers full") or past the cap ("Vector ink
  full"). Another ink, Shapes and Make Ink on Vector Ink's pair go on the pair under it, a new one when there is none (an
  Undo step of its own); the eraser cuts the page's lines whichever pair it erases, and draws their pixels again where it
  went (`VectorInk.room`, `under`, `Cut.done`).
- **Width:** the toolbar's size at half pressure (as the other pens draw it), 0.8 of it at none, 1.2 pressed hard.
- **Smoothing:** a [1 2 1] pass, twice, then a centripetal Catmull-Rom curve, a point every 1.5 px at most; the ends stay.
  The smoothed points are what is kept (the curve is not worked out again at each zoom).
- **While the pen is down:** not the brush engine's fast path but the line's own outline laid on the layer as the samples
  come, through the same page updates (the panel's fast update shows it as any stroke). Simpler, and the line before and
  after the lift differs only by the smoothing. Zoomed in, the line in progress is drawn sharp already.
- **Zoomed view (decided):** the page raster (`display`) is composed **without** the Vector Ink layer while zoomed
  (`ToneDocument.visibleLayers(…, without)`), so its 100% pixels never show blocky; `ViewportBitmap.update` renders the
  zoomed tones plain where lines are, draws the lines over them in Java (`VectorLines.over`), then the dots. No second
  page-sized composite (no extra memory); the costs are one extra pass over the updated area in Java and the lines'
  outlines. When what is left out changes (zoom, a first stroke, Undo of it) the whole page renders again once.
- **Eraser:** the eraser's own pixel erasing is unchanged; its path, as dabs every half radius, cuts the lines
  (`VectorInk.Cut`), kept with the eraser's Undo step (`ToneDocument.changeLines`). An eraser limited to Paint or Dry Media
  leaves them. The pixels and the lines differ by under a pixel at a cut end (round bite against a flat cut).
- **Refused on its layer** ("Vector ink only"): any other brush that lays on the ink half (pens, charcoal, graphite, the
  smudge tool's Blend), the Lasso on that pair, Paste, a carried Lasso piece (let go), placed pictures. Washes still go on
  the wash half under it. Shapes and Make Ink with Vector ink: "Not in Vector ink". Hold to
  Snap and its drag work (the snapped line is drawn again by Vector ink). Mirrors don't copy a Vector ink stroke.
- **Cap:** 50,000 points a page (about a page of handwriting at 1.5 px spacing).
- **Export:** the app has no bigger PNG today (PngExport's sized dots aren't reachable from any menu); `ViewportBitmap.dithered`,
  its renderer, draws the lines sharp at the bigger size. Export Page's PNG and Layers are the 100% pixels (the PDF
  too until 2026-11-05: "Vector lines in the PDFs").
- **Web:** the browser draws and erases Vector ink as on the Manta (the same Java), keeps the lines in the page, and shows
  the 100% pixels at every zoom.

## Vector Ink as a medium (2026-11-04, branch `vector-ink-medium`)

The owner looked for it in Brush Lab's Medium list: "yes add it to the medium list", "in all brush studio locations".
- **The medium:** `PartChoice.VECTOR`, key `vector`, label Vector Ink, Medium choice 8 (after Crayon), no dials; never
  rolled, never a variation's swap (`ModularFeatures.seedOnly`). A brush is Vector Ink when its medium is
  (`VectorInk.is`), so a Brush Lab copy of the Basic tool stays Vector Ink. Older versions read its code's medium as Ink
  ("Newer version").
- **The Basic tool:** `vector-ink.M00il00000000at_8_0_000_0_8at0000000000000000nz0.2h0`: the medium on Steady line's tip
  and hand, pressure to size .3. Its first code (`VectorInk.FIRST`, Steady line's settings at seed 2h0) reads as it
  (`ModularCode`), a copy's name kept.
- **What it draws:** its size (the slot's size times Tip Size), its grey, and Control's pressure to size and Response
  (each point's width is its Ink line's pen width, `ResolvedBrush.Pen.width`, speed left out). The lines' file version 2
  keeps each point's width as a share of the widest (version 1's pressure reads at the widths it had).
- **Set aside** while it is the medium (`VectorInk.tidy`, every brush's last step in `ModularBrush.normalize`, so a
  roll, a swap, a slide or a pasted code never mixes them): Tip any but Round and every tip dial but Size; Affects Page;
  Drying (Instant); After; the paper's Texture; the Medium's own dials (it has none). Brush Lab shows those parts with
  no choices and no dials (`BrushLab.chips`, `dials`), Control's rows Response alone (`handRows`); Control's grid stays
  (only Pressure Size draws). Brush Studio shows those cards as the app's (`WebEngine.studioParts`' `held`).
- **Rolls:** Randomize and Anything Goes never pick it; with Medium locked on it, the rest rolls and is set aside again.
  Variations of it nudge Tip Size and pressure to size alone and swap nothing.
- **Pictures:** the pixel engine draws it as a plain Ink line of its tip and hand (`VectorInk.drawnAs`, `ResolvedBrush.of`):
  the Medium card, the Tools page, Brush Studio's samples and Guide (the lab's JS: newmods.js).

## The Lasso and a quicker eraser (2026-11-04, branch `vector-ink-2`)

The owner: "lasso not recognizing that vector ink is an image"; "eraser of vector ink is very slow". Phase 2's Lasso, now:
- **Lift:** the loop parts the page's Vector Ink lines (`VectorLines.split`: each line it crosses cut flat there, a grid of
  the loop's edges); the piece carries the lines inside, as lifted, and their pixels in its look (`LassoPlanes`); none of
  the Vector Ink layer is lifted as pixels. A piece with lines alone is a piece.
- **Put down:** the lines inside go where the piece lies (`VectorLines.posed`: moved, turned and scaled, widths too) on
  the page's Vector Ink layer (one made on top as Vector ink makes one), their pixels drawn from them; with Cut or a lift
  from this page, the lines inside go from where they were. One Undo step with the pixels (`linkSince`). Delete, Cut,
  Duplicate, Paste and a piece carried to another page all go through it; Replay too. While a piece floats, the zoomed
  view draws the lines left and the piece's where it lies (`LassoPlanes.shownLines`); its look shows their pixels.
- **Not done:** the Lasso has no flip, so neither do the lines. A piece put down on a page of four layers, none with room
  for Vector Ink, lays no lines.
- **The eraser:** its dabs are kept and cut together (`VectorLines.cut(List)`, a grid of the dabs) when the zoomed view
  asks for the lines or at the lift, where each dab cut every line at once; at the lift only the tiles it went over are
  cleared and drawn again from the lines (`VectorInk.relay`), where the whole box round it was redrawn, each stroke over
  the whole box; meanwhile the screen shows the lines' pixels gone where it went (`compositeToneKept(…, also)`).
  Desktop, a Manta page (300 strokes, 50,000 points), a 400-sample stroke 40 px wide (`VectorInkBench`): its samples 26
  to 17 ms, zoomed 28 to 22 ms, its lift 2,371 to 48 ms.

## Never full (2026-11-04, branch `vector-ink-full`)

The owner: "after a long vector ink stroke just now i am unable to make another mark" ("Vector ink full" after one big
scribble); "store maybe a fewer points, but yes, increase the limit".
- **Fewer points:** a line's curve (a point every 1.5 px) is thinned (`VectorLines.thinned`, Douglas–Peucker): a point
  goes only where the line through its neighbours stays within 0.04 px of it (a third of a screen pixel at 800%) and
  its width within 2 in 255, no segment over 12 px. A 40-loop scribble (13,689 samples): 27,861 points before, 14,563
  after; the eraser bench's 300 strokes: 50,000 (the cap) before, 28,511 after.
- **A higher cap:** 200,000 points a layer (was 50,000), and a stroke's own samples at most that. The zoomed view's
  outline passes by a stroke's runs of 32 points whose box misses the area (`Stroke.runs`). Desktop, the whole screen
  (1920 × 2560) drawn with dense scribbles in view: 50k points 29 ms at 125%, 20 ms at 400%; 100k 58 / 40; 200k 113 /
  72; 400k 221 / 108 (the Manta several times slower: a full page's zoom change at 200k, about half a second, only
  when every line is in view).
- **A new layer when one fills:** `VectorInk.room` gives a full top layer's next stroke a new pair on top; the Vector
  Ink layers are the inks with lines of the pairs on top (`linedLayers`), all drawn sharp (`sharpLayers`, a bit a
  layer), all cut by the eraser (`Cut`, one Undo step), all lifted by the Lasso (a piece's lines laid on the top one).
  Other inks go to the first pair under them without lines. Refused only with four pairs and no room: "Vector ink
  full" when the top is full, "Layers full" else, beside the tool.
- **A stroke past the cap** draws whole and ends as any (it no longer stops drawing mid-way).

## No crumbs (2026-11-04, branch `vector-erase-crumbs`)

The owner: "when i erase a bunch of vector ink it leaves a bit on the screen occasionally" (dotted specks along where
lines were). The cause: the eraser cut with round dabs every half radius, whose union's edge is scalloped (dips of 3 % of
the radius between dabs); a line running along that edge was cut into short pieces lying in the dips, kept as lines and
drawn again as a dotted row. Now: dabs every eighth of a radius (the edge within 0.2 % of it); a piece cut at both ends
shorter than a pixel or half its width is dropped (`VectorLines`' crumbs); and the tiles drawn again at the lift are
those the pixel eraser went over and those a dab reached with the lines' widest half width round it, so no pixels of a
cut line stay where the footprint and the dabs differ. `VectorInkChecks.crumbs`: fast strokes (samples 40 px apart) over
lines 3 px apart, some along the eraser's way: no line inside the swept path, no crumb, the pixels the lines'
everywhere (it found 4 crumbs before).

## No dashes (2026-11-04, branch `vector-erase-crumbs-2`)

The owner: "it happened again... particularly long eraser stroke" (a large eraser), "it was after the lift that i saw the
bits appear": dashed runs along the lines, over the whole erased area. The cause: the lift cuts every dab at once, and
for each segment of a line it gathered the stretches inside the dabs near it into a list of 64; past 64 it returned the
list as it was, unsorted and unjoined. One stroke of a big eraser scrubbing over the same lines puts many more dabs than
that over one segment (a dab every eighth of a radius since the crumbs fix, the passes over each other), so the lines
were cut as though the eraser had touched them here and there: dashes, kept as lines and drawn again at the lift. The
live view erases pixels, so it looked clean until then. Now the list joins what it holds when full and goes on (256 a
segment); and the lift makes a last pass with the stroke's swept path itself, sample to sample (`VectorLines.cutSwept`:
capsules of the smaller radius), so nothing is left where it went whatever the dabs missed. `VectorInkChecks.longErase`
(a 90 px eraser, 3,600 samples back and forth 10 px apart over lines 10 px apart): 470 points of 299 pieces were left
inside its path before, none now.

## Not checked yet

- On the Manta: how the zoomed view's Java pass (plain tones, lines, dots) feels while panning a page full of Vector ink;
  how the line in progress looks through the panel's fast update.
- Draw Together: a guest's marks reach the host as pixels; on a host's Vector Ink layer they would sit apart from the
  lines (a live Undo there leaves the lines). Not refused yet.
- Memory of keeping two composites on the Manta: not needed (see "Zoomed view").
- The web render path: pixels at 100% for now (phase 2).

## Vector lines in the PDFs (2026-11-05, branch `pdf-vector-ink`)

The owner asked for it: PDF exports draw Vector Ink as true vector lines, sharp at any zoom or print size; everything
else on the page stays its pixels.
- **Every PDF:** Export Page's PDF, Export Now (the notebook's PDF in Documents) and the browser's both (`PageExport`,
  `NotebookExport.update` and `pdf`). A page's picture leaves out its Vector Ink layers (their 100% pixels would show
  jagged round the lines zoomed in), and its lines go over it as a form (`/Lines`, deflated), in page pixels mapped at
  300 ppi. The PNG copies and Layers keep the pixels as before.
- **The paths (`VectorLines.pdf`):** the same outline the zoomed view fills (`cover`): each stroke round-capped,
  round-joined lines through its points, a piece's width the outline's there (a segment cut in pieces where the width
  changes by more than half a pixel, widths to a quarter pixel), so the edge is within a third of a pixel of the screen's;
  a cut end clipped flat as `flat` cuts it (even-odd clip: the half disc behind the cut, its half width and 1.5 px);
  the grey the copies' (`DotPattern.exportGray` of its shade). `VectorPdfChecks`: a line with pressure, a thick one cut
  flat and a dot cover their outline at 4× but at its edge, within a page pixel.
- **Stacking (`NotebookExport.vectorLayers`):** a Vector Ink layer is drawn as vectors only when shown at full strength
  with no mark of another layer over it in the page's order (opaque paint on its wash half, a page of the stack over
  it); else it, and any under it, stay pixels. A page with magnets, text boxes or Real Mirror's reflection keeps its
  lines as pixels (`shortcut:`: those are drawn over the whole picture). Covers stay pixels.
- **Pages without lines:** the same PDF bytes as before (checked against `explore-live`'s classes for Export Page,
  Export Now and the notebook's PDF).
- **Kept for the notebook's PDF:** a page's picture without its lines, carrying their paths in a private PNG chunk
  (`veCt`), is kept beside its PNG copy by its key (`.1a2b3c4d.pdf.png`, gone with the page), so an unchanged page
  isn't drawn again. Which pages have one is kept beside them (`.lines.txt`, a page's key and 0 or 1; the PDF's own
  record unchanged, so other versions still read it); without it (a notebook from before) each page is read once and
  the PDF written again once.
- **Size:** a Manta page of 300 strokes (28,511 points): 149 KB with vectors against 370 KB as pixels (the picture
  under them nearly blank).
- Not checked yet: how the Supernote's own PDF reader draws the round caps and the clips, and its speed on a page of
  many lines.
