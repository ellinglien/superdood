# Sharp dry brushes (Vector Ink phase 3) — design (draft)

2026-11-04. Status: the pilot and step 1 built (branches `pencil-pilot`, `sharp-brushes`); the Stamp and the Bristle brush kept
sharp too (branch `sharp-bristle-stamp`): every one of the 53 Basic tools the rule takes; High Res PNG export (section 8, branch `png-sizes`); the PDFs' pictures at
2× sharp (section 8, branch `pdf-sharp-2x`). Screentone and screened prints, and Shapes, sharp ("Screentone as built", "Shapes as built", branch `sharp-more`). Builds on `2026-11-04-vector-ink-design.md` (phase 1 as built, its
"Phase 3 (maybe)") and `2026-10-27-kind-layers-design.md` (the hidden kind planes).

## Goal

Pencil, charcoal, crayon, pens and liners, hatch, stipple, stamps: sharp at any zoom and in big exports, drawn and
erased as today, with nothing new to choose. At 100% every page looks exactly as it does now.

Not goals: editable strokes, vector export of textured marks, a new tool, setting, layer kind or note. The owner's
decisions (2026-11-04): clean at zoom, not editable; no layer UI (layers stay a quiet thing); no new settings; smudge,
paint, wet media, water and blends that read the page stay pixels.

## The idea in one paragraph

Every brush stroke is already kept as its samples and brush code, in order, in the page's Replay log (`StrokeLog`,
`StrokeLog.java:10-27`), and the engine draws the same marks from the same samples (that is how Replay works). So:
keep the page's pixels exactly as now (they stay the truth at 100%, for older versions and for every pixel path),
and when zoomed in, draw the dry strokes of each part of the page again, finer, from the log. Before showing a
redrawn part, redraw it once at 100% and compare with the stored pixels: where they match, show the fine version;
where anything else touched the page (a smudge, a light chalk over dark, a guest's marks, a Lasso move), show the
pixels as today. That check makes every mistake fail safe (blocky, never wrong), and keeps the order the marks were
drawn in without any new layer.

## 1. Which brushes qualify

A pure predicate on the brush's parts (`ModularBrush`, `ResolvedBrush.of`, `ResolvedBrush.java:657`), worked out once
per stroke at pen-down. A stroke is kept sharp when its brush:

- draws on a pair's ink half: Medium Ink, Charcoal, Graphite or Crayon, not a wet wash or Water-Soluble
  (`ModularSizes.inks`, `ModularSizes.java:67`; `ModularStroke.layerOf`, `ModularStroke.java:405-408`);
- is not Resist, Screentone or a screened print (`r.screen == null`; `ResolvedBrush.java:626`, `647`): their print
  plane and threshold are fixed to page pixels;
- has Affects Page Off (no `carry`, Stay in Lines, Filled Shape, Scrape or Reprint);
- dries Instant, or Quick with no body (Quick's line window is laid again from distance alone,
  `LineStamper.java:44-49`; a Quick body is settled, a blur at page pixels);
- has After Off (Fading, Lemon Juice, Mold, Tarnish and the rest change by the clock);
- has its own Blend (not White, Invert or Behind), no Opaque cover or Thick Paint;
- has no page-tilt dials (downhill size, darkness, width, charcoal's dust: the page's tilt is not in the log);
- is not a Brayer that picks up (it prints what it rolls over, `Brayer.java:9-11`).

Vector Ink keeps its own lines (phase 1) and is not in this list.

Two things the predicate does **not** need to decide, because the 100% check (section 5) does it per pixel:
light over dark (`ResolvedBrush.lightOver`, `ResolvedBrush.java:471-478`: any pencil, charcoal or crayon line lighter
than black, any pen lighter than mid grey, lays its grey over darker marks), and charcoal's and crayon's contact rules,
which read the tone found under them (`ModularCompose.ink`, `ModularCompose.java:788-807`). Where such a stroke met
nothing it reads, it redraws the same and stays sharp; where it did, that spot shows pixels.

The Basic tools under it (a scratch probe over `ToolPresets.all()`, black grey):

| Style | Sharp | Pixels (why) |
|---|---|---|
| Fineliner | Steady line, Press liner, Quick sketch, Toothy line, Lively liner, Stipple pen, Hatch pen, Hand hatch, Neat hatch, Ballpoint, Gel pen, Fountain pen, Cheap ballpoint, Smooth fountain, Spirograph, Stamp | White gel pen (White), Vanishing ink (After); Vector ink is already sharp |
| Brush pen | Swell, Chisel, Tilt, Pooling, Springy brush | Wet brush, Ink wash brush (wash half) |
| Dip pen | Split nib, Fude pen, Flick nib, Chisel nib, Copperplate, Ruling pen, Twig pen | Lemon juice (After), Dip nib (Drag) |
| Marker | Chisel, Round, Felt marker | Bleed marker, Wet chisel (Quick body); the rest wash half |
| Pencil | 2H, HB, B, 2B, 5B, Sketch pencil, Loose hatch, Another pencil | Water pencil (Soluble), Metalpoint (Tarnish) |
| Charcoal | Messy stick, Compressed, Crisp, Soft, Dark stick, Long stick | Blend, Blur (Smear), Charcoal dust (tilt), White chalk (White) |
| Pastels | Chisel, Soft, Crumpled, Crisp, Side pastel, Wax crayon | Crayon scratch (White) |
| Dry brush | Bristle brush, Fan brush | the other five (wash half) |
| Print | none | all (screens, Screentone, Reprint, Letterpress, Brayer) |
| Watercolor, Ink wash, Gouache, Oils, Hake | none | wash half, Float, Mold, Smear |

53 of the Basic tools; everything a person sketches, writes or shades with.

## 2. What is kept per stroke, and is it enough

Already kept per stroke (`StrokeLog.Entry`, `StrokeLog.java:60-96`): brush code (seed included), size, grey, and per
sample x and y in half pixels, ms since the last, pressure (a byte) and tilt (degrees). Missing: which plane the stroke
went on (the log keeps no layer), and a Stamp's starting ink (it carries on from its holder's last stroke,
`ModularStroke.java:265-266`, `HeldPaint.java:128`). Those two are the only new things kept (section 7).

Determinism, checked in the code:
- Random draws are seeded from the brush seed and the samples: `r.pen.seed ^ pts.hash()` (`ModularStroke.java:1519`,
  `1748`), bristles and placement from the seed (`LineStamper.java:372`, `Placement.java:20`), a swarm and a stipple
  from the first sample's page position (`BristleSwarm.java:81`, `StippleStamper.java:91`), dust from distance
  (`LineStamper.java:114`). All in page units, so they do not change with the scale drawn at.
- Speed comes from the samples' ms, which the log keeps; work split across cores is the same on one core (`Cores`).
- **The catch:** the live stroke sees the pen's exact floats; the log rounds positions to half pixels, pressure to
  1/255 and pauses to 2 s (`StrokeLog.java:304-307`, `MOST_PAUSE`). So a redraw differs from the live stroke by a
  sub-pixel. Fix: round the samples **before** both the stroke and the log get them, so the stroke draws exactly what
  is kept. Replay becomes exact too. Risk: half-pixel steps showing as a wobble at 800% (measure in the pilot; if they
  show, a log version with eighth pixels, as the LINE section keeps).

## 3. Drawing at a finer scale

Today every stamper walks page pixels: `for (py = into.up(y0); py <= y1; py += step)`, each pixel's centre
`px + 0.5`, its edge a one-pixel ramp `clamp(0.5 - (dist - 1) / g)` (`LineStamper.java:286-325`). A big stroke
already works on a coarser grid, every other pixel, then stretches (`FloatTiles` lattice, `FloatTiles.java:10-16`;
`ModularStroke.java:263`). Phase 3 adds the other direction: a grid **finer** than the page.

- **One parameter, the grid:** scale k (2, 4 or 8) and an origin (the tile drawn). Every geometric quantity stays in
  page units: samples, size, dab spacing, tapers, hatch pitch, stipple cells, bristle lanes, the swarm's hairs, seeds.
  Only the raster changes: the loops walk grid points and read their page position as a double; the edge ramp is one
  grid point wide (`g / k`). Soft edges given in page px (a stick's fuzz, Tilt Shading) stay as soft as they are.
- **So at 1× nothing changes:** grid points are page pixels, the numbers are today's. At 4× the same dabs, dots, lines
  and hairs land in the same places, their edges four times finer. Not a "bigger brush on a bigger page": that would
  change the marks (`StrokeMath.inkBig`, the lattice threshold, dab spacing all depend on size in px).
- **Per-pixel noise** (crayon's specks and scratch, `Crayon.java:61`, `82`, hashed per page pixel): picked by the page
  pixel the grid point is in, so each speck becomes a crisp page-pixel square.
- **Where it reaches:** the stampers (`LineStamper`, `BodyStamper`, `Placement`, `BristleSwarm`, `StippleStamper`,
  `HatchStamper`, `HandHatchStamper`, `StampStamper`, `SpiroStamper`, `RulingPen`, `TwigStick`, `FanBrush`, `Crayon`)
  and the line rules of `ModularCompose` (`ink`, `height`). One stamper at a time, each behind a check that 1× is byte
  for byte as before.
- **Drawing into a tile:** a stroke's geometry is worked out whole (its state runs from its start: a bristle brush
  running dry, a stamp's ink) and laid only inside the tile (`stamp(..., clip, ...)` already clips,
  `LineStamper.java:28`). The compose writes through a `ModularCompose.Page` over the tile, as `StrokeTones` already
  stands in for the page (`ModularCompose.java:25-50`). A finer `FloatTiles` holds only the tiles touched.
- **Pure and separable:** all of it is engine (pure Java, no Android); the grid is one more argument the host passes
  (INTEGRATION.md section 3), the app's tile cache is the host's.

## 4. Paper grain at zoom

Every grain the strokes read is a table of page pixels:
- made from nothing: the plain paper (value noise on 4 and 2 px lattices, `PaperNoise.java:15-17`), cold press,
  laid, canvas, kraft and unryu (`SurfaceGrain.java:27-35`, 2048 px), charcoal's own grain (`StickGrain`, 1.6, 7 and
  36 px cells), the blooms and blobs;
- made from 256 px scans laid as patches: felt, crumpled, cotton, bumpy, library, vintage, craft, fine
  (`SurfaceGrain.scanned`; the scans `BrushTexture.java:21-25`).

**Proposal: read every grain bilinearly at the grid point's page position**, the same tables, nothing new made. At 1×
the point is the pixel's centre, so the value is today's. At 4× the grain is the same grain, smooth between its
pixels. That is what real paper does under a magnifier: the tooth gets bigger, not finer. And the marks stay crisp on
it, because most rules threshold the grain (graphite's `smoothstep` on the paper's height, charcoal's contact rule,
tooth, dry bristles): a smooth height cut at a threshold gives a clean edge. Grain that only shades (granulation, a
stick's grain factor) is soft, as it is on paper. Scans show their one-pixel speckle as soft bumps; no finer scans,
no finer noise octave (either would change 100% or break the match). The stroke meets the same grain at every zoom
because the grain is fixed to the page, as now.

## 5. Where sharp strokes live (hidden layers)

No new layer, plane or layer UI. A stroke goes where it goes today: its pair's ink half, its kind's hidden plane
(Dry under Wash under Pen under Opaque, `ToneDocument.java:43`, `stacked` at `68`). Its pixels are written there as
now. The plane also keeps a list of its sharp strokes, in the order drawn.

The stacking rule is today's, unchanged: between pairs, the pair order; within a pair, the kind order ("ink always
over wash", `InkWashLayers.java:5-8`); within one plane, the order drawn, which the stored pixels already hold.
Sharp strokes don't need a top layer (Vector Ink did, phase 1), because the 100% check decides per pixel:

- Zoomed, for each screen tile and each plane with sharp strokes in it: draw those strokes alone at 1× (a blank
  plane, white paper under) and compare with the plane's stored pixels. Where equal: nothing else touched that
  pixel, so the fine redraw is exactly that pixel, finer. Where not: show the stored pixel, as today.
- Then compose the tile's planes in the page's order, each from its fine version where it matched and its stored
  pixels elsewhere (`compositeTones`' over rule, `ToneDocument.java:1339-1356`).

How the rest behaves, with no special cases:
- **A pixel mark after (over) a sharp one** on the same plane (a smudge, White chalk, light over dark, a guest's mark):
  that spot no longer matches, so it shows pixels, in the right order. The rest of the stroke stays sharp.
- **A sharp mark after (over) a pixel one** on the same plane: where they overlap, the blank redraw lacks the pixel
  mark, so that spot shows pixels; elsewhere the sharp mark is sharp. Pixel marks on other planes (washes, other
  pairs) don't matter: the plane is compared alone.
- **Smudge** works as today (it is a pixel tool). Where it went, the zoomed view shows today's pixels, soft; the
  rest stays sharp. Nothing is refused. (Vector Ink still refuses smudge on its own lines.)
- **Eraser:** pilot: as today; where it took paint the redraw (which still has the stroke) no longer matches, so the
  stored pixels show: blank where erased, a blocky edge where it bit. Step 2: eraser strokes, already in
  the log with their size and kinds, are redrawn at scale in order with the strokes, so cut edges are sharp too. Not
  the Vector Ink cut: cutting a textured stroke would change its texture (`pts.hash()` changes).
- **Lasso:** what it lifts and puts down are pixels, so moved pieces show pixels. Later: carry the strokes with the
  piece and its pose, as `VectorLines.posed` does for Vector Ink.
- **Undo:** entries come and go with their steps (`StrokeLog`'s serials), pixels as now; they match again.
- **Mirrors, Merge, Draw Together guests, the log's oldest dropped past 100,000 samples:** all pixels, by the check.

## 6. Speed on the Manta

Measured (desktop, `SharpBench`, a scratch copy of `VectorInkBench`'s pattern, not committed): a Manta page
(1920 × 2560), 300 strokes of 170 samples each, drawn from samples at 100%, best of 3:

| Brush, px | ms, 300 strokes | a stroke |
|---|---|---|
| Steady line 5 | 200 | 0.7 |
| HB pencil 5 (cold press) | 200 | 0.7 |
| 2B pencil 5 (felt) | 1,726 | 5.8 |
| Messy stick 16 | 872 | 2.9 |
| Stipple pen 30 | 206 | 0.7 |
| Hatch pen 30 | 541 | 1.8 |
| Bristle brush 16 | 1,048 | 3.5 |
| Wax crayon 16 | 930 | 3.1 |

A full screen zoomed to 400% at k = 4 holds as many grid points as the whole page at 100%, so these are about one full
screen's redraw. The Manta at about 20 times the desktop (INTEGRATION.md:1579, "the Manta about 20 times these"): 4 s for a
screen of pens and pencil, 17-35 s for a screen dense with charcoal, bristle or 2B. Far too slow to redraw on a pan.
So:

- **Show today's pixels first,** then sharpen tile by tile as each is ready (e-ink refreshes a tile at a time anyway;
  phase 1's line also appears sharp after its lift).
- **Draw on the quiet cores** (`Cores.quiet`, lowest priority), pen input first; stop when the pen comes down.
- **Cache** the composed tiles per zoom step (a screen is about 5 MB of greys; keep about three), dropped where the
  page changes (`DirtyRegions`, the page's revision).
- **k = 2 up to 200%, 4 above;** 8 only if the pilot shows 800% needs it (four times the work of 4).
- Only strokes whose box meets the tile; each stroke's geometry once per tile set (cache it if the pilot shows it costs).
- Budget to hit in the pilot: a 256 × 256 screen tile of pencil under 100 ms on the Manta.

## 7. Storage and older versions

- The samples are the log's (`RPLY`, already in every page). One new section, `SHRP`, written only when a plane holds
  sharp strokes: for each such plane, its address (`ToneDocument.address`, layer and slot) and its strokes as their
  places in the log, plus a Stamp's starting ink. A few bytes a stroke. The plane keeps the log's entries themselves
  in memory, so it moves with its layer (as `Layer.lines` does for Vector Ink, `ToneDocument.java:144`).
- Older versions skip `SHRP` (unknown tags are skipped, `DocumentCodec.java:27`) and see the pixels, as drawn.
- Caps: the log's (100,000 samples, 1 MB, `StrokeLog.java:33-37`). Past it the oldest strokes are dropped from the
  log and show as pixels, by the check. No message.

## 8. Replay, Draw Together, export, web

- **Replay:** unchanged, and exact once samples are rounded before the stroke (section 2).
- **Draw Together:** guests' marks reach the host as pixels, so they stay pixels there; the host's own stay sharp on
  the host. Guests see the host's as pixels (phase 2's "send strokes as points" would cover both).
- **Export:** built (2026-11-05, branches `png-sizes`, `png-sizes-2`, the owner's three sizes). Export Page's PNG
  opens a second menu: **Social** (1080 on the page's short side, the page's greys shrunk by area), **Page** (the
  notebook's copy, as before) and **High Res** (twice the page, ", sharp"), each row with its size for the page (a
  Manta page: 1080 × 1444, 1785 × 2387, 3570 × 4774), each saved at once through `AutoExport.page` and `PageExport`
  (the size a parameter; `CHOICES` and a guest's `as=k` unchanged; a guest's PNG at Page until `export-sizes`). High Res is the 200% screen
  in greys (`PngRender.high`): the stack's composite without Vector Ink's sharp layers (`PagePicture.Composite.of`),
  each pixel laid 2 × 2 (the native zoom's nearest pixel), the tiles a tile row at a time (`SharpView.drawRow`, the
  screen's `render` and `over`, laid only where the page shows what the tile was drawn over), Vector Ink's lines
  (`VectorLines.over`), then calibrated; magnets and text boxes as the copy has them. Deflated as it is drawn
  (`PngRender.Png`, 8-bit grey, a strip of 128 rows at a time), past ToneDocument's 4096 a side: a Manta page holds
  about 13 MB besides the page (its composite twice, a byte a pixel, a strip of ints, the PNG's bytes; 17 MB more for a moment while magnets are stamped, as the copy's), never a
  whole picture of ints (68 MB). Desktop, a Manta page of handwriting (300 HB strokes over it): 2.0 s, so about 40 s
  on the Manta (about 20 times); a page shaded all over, minutes. Its tiles are drawn on one core. A kept stroke on a
  stack's upper page shows as pixels. Not checked on the Manta. Layers stayed 100% pixels until `export-sizes` (below). SVG: not possible
  for textured marks (they are millions of grey pixels, not shapes); no SVG for them.
- **PDF at 2× (2026-11-05, branch `pdf-sharp-2x`, the owner's decision):** every PDF (Export Page's, the notebook's
  through Export Now, the browser's both) draws a page holding kept dry strokes (`PngRender.sharp`) with High Res's
  renderer (`PngRender.high`): twice the page, 600 dpi, placed at the page's size, the kept strokes drawn sharp and
  the rest its pixels doubled; Vector Ink layers that go as vectors (`vectorLayers`) left out of it and drawn over it as
  before, their form still in page pixels. Its rows deflated as drawn into the PNG the PDF embeds as it is (no 2× ARGB
  picture held). Pages with no kept stroke (washes, Vector Ink alone, plain pen) keep their picture at 1×, the same
  bytes as before. The notebook's 2× picture is kept by its key with the lines' (`.lines.txt` now starts `pictures 2`,
  so a record from before reads each page once and writes the PDF once). Desktop, a Manta page of 300 HB strokes:
  1× PDF 622 KB in 0.8 s, 2× 1782 KB in 1.8 s; on the Manta about 40 s, held under the one-page lock for that page
  (released between pages, as before). The browser (wasm, Node): a whole notebook's PDF of such a page, 4.5 s, so 2×
  there too. Not checked on the Manta, nor in its PDF reader at 3570 × 4774.
- **Layers at the three sizes, and the browser's exports (2026-11-05, branch `export-sizes`, the owner's decisions):**
  Export Page's Layers opens the PNG's size menu, its High Res row "High Res, 3570 × 4774, sharp, large file"
  (`PageExport.sizeWords`; keys, `CHOICES` and `as=k` unchanged). Social: each layer shrunk by area, its coverage with it
  (`PngRender.shrunk`, the PNG's shrink a row at a time), the merged image Social's PNG. High Res: each layer twice the
  page a tile row at a time (`PageExport.Sharp`): its pixels doubled, except where a plane of it holds just what its kept
  dry strokes lay (`SharpView.holds`, the screen's rule) and the layer shows just those planes there: there the strokes
  drawn again at 2 × (`SharpView.drawn`); a Vector Ink pair's Pen drawn from its lines (`VectorLines.over`, its coverage
  kept), their pixels left out; Paper, Template and Magnets doubled; the merged image High Res's PNG (`PngRender.high`
  into a `Strips`), 600 dpi. Each layer packed as drawn and the file streamed (`PsdWriter.Channel`; on the Manta into the
  file, `PageExport.write`), so a layer is never held whole. Laid Normal, the layers come within 12 greys of the PNG
  except where partial marks of two layers meet: Social 0.2% of a Manta page's pixels (up to 61 greys, layers shrunk
  apart), High Res about 1 in 100 000. Where a magnet's soft edge lies over a kept stroke, the PNG keeps that pixel
  doubled and the layers keep the stroke sharp under it. Desktop, the Manta page of 300 HB strokes (lined): Layers Page
  1.7 MB in 0.2 s, Social 0.8 MB in 0.4 s, High Res 5.5 MB in 3.3 s (its strokes drawn twice: the layers and the merged
  image), so about a minute on the Manta; the least JVM heap that writes it is the High Res PNG's (121 MB with the page,
  33 MB the page alone; Layers Page 49 MB, as before). Not checked on the Manta, nor opened in Photoshop (read back by
  `PageExportChecks.read`). The browser: `/app`'s Export Page (Page Settings', Menu's) and a Draw Together guest's offer
  the same size menus (`WebBooks.sizeWords`; `pageCopy(place, choice, size, today)`; a guest's `books/page?…&size=`, the
  Manta's `LiveBooks`, Page without it); `/app`'s PDF was already 2 × sharp with Vector Ink vectors. The browser keeps the
  stroke logs (`WebEngine` records kept strokes as the Manta does), so its High Res is the Manta's, byte for byte
  (`m3-check`, `books-check`). Its copies are made in the tap, on the page's thread (an iPad's share sheet needs the
  tap): wasm in Node, the Manta page above, High Res PNG 4.1 s, Layers High Res 6.4 s, the tab still for that long.
- **Web:** the same Java; it shows 100% pixels at every zoom, as it does for Vector Ink, until its speed is measured.

## 9. Phasing

1. **Pilot, HB pencil only:** graphite on cold press, round tip; it darkens only (`ModularCompose.darkensBy`,
   `ModularCompose.java:166`), reads nothing under it, and cold press is made from nothing. The most-used dry tool,
   the one people shade with and zoom into. Steps: round samples before the stroke; the grid in `LineStamper` and
   graphite's rule; bilinear `PaperGrain`; the 100% check; the zoomed tile path; `SHRP`.
   Measure: (a) 1× bytes match on pages drawn only in HB pencil (expect all); (b) the half-pixel wobble at 400% and
   800%, by eye on the Manta; (c) ms per screen tile at 200/400/800% on the Manta; (d) the owner's look at the grain
   up close; (e) memory.
2. **Charcoal and pastels** (stick grain, contact rule reading under, specks), **then the pens** (Quick tails, pools,
   splatter, split nibs).
3. **Patterns:** stipple, hatch, hand hatch, stamp, spiro, bristle, fan, ruling and twig.
4. **The eraser at scale,** then the Lasso carrying strokes.

Risks: the engine's pixel loops touched in many files (each needs its 1× byte check; MonoPaint-facing, so the grid
goes in as one small generic change, INTEGRATION.md); the Manta's speed for dense pages; seams where pixels meet sharp;
rounding the pen's samples changes every live line by under half a pixel.

## 10. Questions for the owner

Answered 2026-11-04 (the owner: "1 yes 2 yes 3 fine 4 ok start the pencil pilot"): today's pixels first, then
sharpening piece by piece, yes; the grain bigger and smoother up close with crisp pencil edges, yes; smudged spots as
today up close, the rest sharp, fine; the eraser's bite blocky up close at first, ok. The pencil pilot goes ahead.


1. Zoomed in, a busy page shows as it does today for a moment, then sharpens piece by piece. Is that all right?
2. Paper up close: the paper's texture gets bigger and smoother as you zoom (like a magnifying glass), and the pencil's
   edges stay crisp on it. Is that the look you expect?
3. Smudging a sharp line (the Blend stick): where you smudged looks as it does today up close, soft and a bit blocky,
   and the rest stays sharp. Fine, or should smudging leave sharp lines alone?
4. In the first version, where the eraser bites into a pencil line, that edge looks blocky up close. All right to
   start with?

Nothing new on screen: no words for TEXT-STYLE.

## The pencil pilot as built (2026-11-04, branch `pencil-pilot`)

- **Which strokes:** the HB pencil Basic tool alone (`SharpStrokes.pilot`: its settings as `ToolPresets` has them; a
  Brush Lab copy changed is ordinary), drawn by the pen, at a size and grey `SharpStrokes.drawn` covers (one nib line of
  graphite, no lattice, no spring, gradient, re-taper, splatter or flick). The spec's rule (section 1) is
  `SharpStrokes.qualifies`, unused until the next step.
- **Fed as kept:** `StrokeLog.Recorder.keeps`; `DrawingPad.feed` gives such a stroke each sample as the log keeps it
  (`Recorder.kept`: half pixels, pressure in 255ths, whole degrees, the replay's clock), so drawing it again from its
  entry lays the same bytes. The entry keeps its plane (`ToneDocument.selectedAddress` at the lift) and zoom
  (`StrokeLog.Entry.plane`, `zoom`), in the file's `SHRP` section beside `RPLY` (`StrokeLog.encodeSharp`, `withSharp`).
- **Drawn finer:** `FineGrid` (2, 4 or 8 points a pixel over part of the page); `FloatTiles(grid, …)` holds its points;
  `LineStamper.stamp` works the dabs out on the page as ever and lays them at the grid's points (`toGrid`: place and
  ellipse in points, a soft edge as wide on the page, its one-pixel least one point); `StrokePaper.on(grid)` reads the
  page's grain between its pixels (bilinear), kept as read; `ModularCompose` composes onto `SharpStrokes.GridPage`
  unchanged. At 1 point a pixel every number is today's: the 140 Basic tools draw the same bytes as before (each page
  hashed, before and after), and `SharpChecks` shows a kept stroke drawn again lays the plane byte for byte.
- **The view:** `SharpView`: tiles of 64 page px, drawn on a thread of their own at the lowest priority (the quiet
  cores), the newest asked first; the match (section 5) per tile; the composite as the screen's (`visibleLayers`, the
  native arithmetic), left as the screen shows it where the screen differs (a Lasso piece, the masking fluid's
  hatching, guides). `ViewportBitmap.update(…, view, k)` lays the ready tiles under Vector Ink's lines, then the dots.
  `DrawingPad` asks for the tiles the view shows when it settles (not while a stroke or a pinch is under way), drops
  them where the page changes, and shows each as it is ready in the panel's usual update (no flash of its own).
  Grid: 2 to 200%, 4 to 400%, 8 above; none at 125% and under.
- **Desktop, `SharpBench`** (a Manta page, HB pencil 5 px on cold press, 300 strokes of 170 samples): handwriting (over
  the whole page) at 400%, a tile 2.3 ms on average (11.6 the slowest), a whole screen (80 tiles) 180 ms; at 800% a
  tile 5.4 ms, a screen (20 tiles) 108 ms. Shading (all 300 in one screen) at 400%, a tile 15.5 ms (32.9), a screen
  1.2 s; at 800% a tile 30 ms, a screen 0.6 s. At about 20 times on the Manta: a handwriting tile about 50 ms, its
  screen about 4 s, tile by tile; a shaded screen about 25 s.
- **The half pixels at 800%** (`SharpPictures`, a slow straight stroke drawn from the pen's exact samples and from the
  log's): their edges lie 1.9 screen px apart at the 95th percentile (a quarter page pixel), a faint ripple along a
  slow straight edge; not yet worth eighth pixels.
- **Not checked:** on the Manta (speed, how the tiles look arriving, memory: the tiles kept hold 24 MB at most, a screen at any zoom);
  a page whose layers were reordered or merged after (a kept stroke names its plane by place: such strokes show as
  pixels); bigger PNG exports (`ViewportBitmap.dithered`) stay the page's pixels scaled, as before; the browser shows
  100% pixels at every zoom (it keeps no kept strokes: its samples aren't fed as kept).

## Step 1 as built: every qualifying dry brush (2026-11-04, branch `sharp-brushes`)

The owner on the Manta: "looks good! i can even blur it?? it sharpens" (a smudge shows its pixels, the rest sharp, as
section 5 says). Then "go ahead with step 1" and "make sure it works in the brush studio pls too".
- **Which:** `SharpStrokes.takes`: the spec's rule (`qualifies`) and a tip and body the grid lays (`drawn`, `tipOnGrid`,
  `bodyOnGrid`), at the stroke's size and grey; any brush, a Basic tool's or Brush Lab's. 51 of the 53 Basic tools the
  rule takes: every pencil, charcoal and pastel stick, Wax crayon, the fineliners, ballpoints, fountain pens and markers,
  the brush pens, the dip pens (Split nib, Copperplate, Ruling pen, Twig pen), Stipple pen, the four hatches, Spirograph,
  Fan brush. Still pixels: Stamp (its ink carries on from the holder's last stroke, which the log doesn't keep) and the
  Bristle brush (its swarm's fields are the page's; next).
- **The page's own stroke on a grid:** `ModularStroke.onGrid` (its buffers a `FineGrid`'s, its rectangles in points,
  composed onto `SharpStrokes.GridPage`), fed the entry's samples, through its spring (`SteadyHand`), so Quick's
  re-tapered tails, splatter, the stick's dust, Placement's lines, a body's dabs and every tip lay as on the page. Each
  stamper lays its page-worked marks at the grid's points (`LineStamper.toGrid`, `Placement`, `BodyStamper`,
  `StippleStamper.dot`, `HatchStamper`, `SpiroStamper.segment`, `FanBrush.hair`, `RulingPen.blade`, `TwigStick.end`):
  their places and sizes in points, their soft edges as wide on the page, their one-pixel edge a point; per-pixel maps
  fixed to page pixels stay so (a crayon's, `StrokePaper.pageX`); grain read between pixels (`StrokePaper`: the stick's
  too, and the charcoal rule's). At one point a pixel nothing changes: each Basic tool's strokes hash the same before
  and after; `SharpChecks.families` holds each kept tool's page, drawn again, to the byte.
- **Desktop, `SharpBench`** (a Manta page on cold press, 300 strokes of 170 samples; a tile's mean over a 400% screen's
  80 tiles; handwriting: over the whole page, shading: all 300 in that screen), ms, the Manta about 20 times:

  | Family (tool, px) | Handwriting | Shading | Manta, handwriting |
  |---|---|---|---|
  | Pencil (HB 6, 2B 6) | 2.6, 4.2 | 19, 37 | 52, 83 |
  | Charcoal (Messy stick 6, Soft charcoal 6) | 7.0, 4.2 | 58, 32 | 140, 83 |
  | Pastel, crayon (Soft pastel 6, Wax crayon 6) | 4.1, 4.6 | 32, 36 | 82, 91 |
  | Pens (Steady line, Gel pen, Fountain pen, Ballpoint, 6) | 1.8, 1.6, 3.3, 4.1 | 16, 14, 30, 31 | 35, 33, 66, 81 |
  | Brush and dip pens (Swell brush, Split nib, Copperplate 6; Ruling, Twig 20) | 7.5, 7.2, 3.2, 4.5, 6.2 | 67, 66, 28, 39, 52 | 151, 145, 65, 90, 124 |
  | Patterns (Stipple, Hatch, Loose hatch, Spirograph, Fan brush 20) | 1.9, 5.1, 5.0, 0.9, 4.1 | 14, 44, 41, 10, 35 | 39, 103, 99, 19, 81 |

  Every family's handwriting tile is under 200 ms on the Manta (the slowest, Swell brush and Split nib, re-taper their
  Quick tails), so none is left pixels; a screen dense with strokes takes a second or more a tile there, the pixels
  shown meanwhile.
- **The browser:** `WebEngine` keeps a kept brush's strokes as the Manta does (`pen`, and `down` for Brush Studio's
  pictures: fed as kept, the plane at the lift) and draws tiles on asking, on the page's one thread (`sharpTile`,
  `sharpArea`; no view thread there: `SharpView.kept(page, laid)`). The browser app (`live.html`) draws the view's tiles
  over the page, one at a time when idle, a tile dropped where the page changes (`pull`), none new while a stroke is
  drawn, none with Show Kinds' steps. Brush Studio's close-up at 4× and 16× draws its part at 4 and 8 points a pixel
  (`render.js`: the job's `sharp`, `closeSharp`), the pixels until it is ready; a brush kept sharp ends its words with
  "Sharp at any zoom." (Vector Ink's card's words, `ModularWords.noMacro`). `sharp-check.cjs` checks the browser's.
- **Found on the way:** a page with no surface (the browser's, Studio's) reads each brush's own paper: the tiles now
  take each stroke's (`SharpView.drawn`), checked by `SharpChecks` too.

## The Stamp as built (2026-11-04, branch `sharp-bristle-stamp`)

- **Its starting ink kept:** a kept stroke's entry keeps the ink its Stamp started with (`StrokeLog.Entry.ink`: 1, or what
  its holder's last stroke left, `StampStamper.startLoad`, the stamper's own rule pulled out), worked out by its recorder
  from the holder's ink at pen-down (`SharpStrokes.keep`, `ModularStroke.inkedBy`: the stroke's own rule) and the first
  sample's time; drawn again it starts with that ink (`ModularStroke.stampInk`, `StampStamper.Ink.kept`). In the file,
  `SHRP` version 2 adds each stroke's ink (a double, exact) after its zoom, written only when a Stamp's isn't 1, so the
  version before reads every other page's (and shows such a page's strokes as pixels).
- **The clock fed as kept** now starts at the first sample's own time, not 1000 (`StrokeLog.Recorder`): a stroke reads
  only the times between its samples (each Basic tool's page hashes the same, and `SharpChecks.families` holds), but a
  Stamp's rest since its holder's last print reads the clock across strokes. Untimed samples still start at 1000.
- **The prints on a grid** were laid already (step 1, `StampStamper.print`); `SharpStrokes.tipOnGrid` now takes the tip.
- **Checked** (`SharpChecks.stamps`): six Stamp strokes 300 ms apart, the fifth after a two-second rest: the ink carries
  on (.67, .44, .28) and starts afresh after the rest; drawn again at 1 to a pixel, 0 of 8,058 pixels differ, and the
  same stroke drawn with fresh ink does differ; the ink reads back from the file; at 4 to a pixel 6,198 of 7,029 edge
  pixels hold points of more than one grey. Basic tools kept sharp: 52 of 53. The browser's: `sharp-check.cjs`.
- **Not checked:** on the Manta; a Stamp stroke after a pause of over two seconds within the stroke before (the kept
  clock leaves out a pause's time past two seconds, so its next stroke may start afresh where the pen would have carried
  the ink on).

## The Bristle brush as built (2026-11-04, branch `sharp-bristle-stamp`)

- **The swarm walks the page, its hairs laid on the grid** (`BristleSwarm.stamp`): its trail field is the page's size
  (`FineGrid.pageWidth`, `pageHeight`), so the hairs sense, crowd and run dry exactly as on the page; each hair's dab
  (and a big head's sweep from where the hair was) is laid at the grid's points, its edge a point wide, its head's lanes
  as wide on the page; a dab off the grid is passed over. The paper's tooth under a dry hair is read between pixels
  (`StrokePaper`). `SharpStrokes.tipOnGrid` takes the tip. A head over 44 px (Bristle brush) is laid on the lattice and
  stays pixels, as every brush's that big.
- **Checked:** each Basic tool's page hashes the same as before; `SharpChecks.bristles`: a 20 px head (a step a pixel)
  and a 40 px one (swept steps): 0 of 11,848 and 0 of 20,170 pixels differ drawn again; at 4 to a pixel 2,396 of 2,657
  and 2,659 of 3,048 edge pixels hold points of more than one grey. `SharpChecks.families`: 53 of 53.
- **Speed** (`SharpTools`, desktop, a 256 × 192 page of eight strokes and a tap): 16 to 18 ms a 400% tile, about
  330 ms on the Manta: each tile walks every stroke over it from its start (the swarm is a state). Slower than the pens
  (Fan brush 4.5, Stamp 4.3); the pixels show meanwhile. Not measured on the Manta.


## Screentone as built (2026-11-05, branch `sharp-more`)

The owner, 2026-11-05: "screentone dots are my top thing that i love, so zooming them as well would be cool".
- **Which:** section 1's rule no longer leaves Screentone and screened prints out (`SharpStrokes.qualifies`): a screen of
  its own Blend, not opaque, its tip and body on the grid as any stroke's, without Misregister (its second pass is an
  offset in page pixels on the stroke's buffers; `SharpStrokes.drawn`). Basic tools kept: Screentone's five (Dot, Line,
  Sand, Gradient, Hatched tone, up to Large: 160 px is laid on the lattice) and seven through Print Shading (Ben-Day
  brush, Rosette ink, Halftone brush, Mezzotint, Dither pencil, Engraving pen, Wavy-line pen; the ones with a body only
  at small sizes, a body's reach past 100 px is on the lattice). Still pixels: Riso (Misregister), Photocopier and
  Newsprint (Reprint, Affects Page), Brayer, Letterpress (Later). `SharpChecks.families`: 65 of the 140 kept.
- **The dots at the grid's points:** the compose asks the screen at each point's page position (`PrintScreen.ink(a, x,
  y, height)`, `StrokePaper.indexX`; a pixel's centre gives the pixel's own threshold, as before). A screen worked out
  from the position (`PrintScreen.between`: halftone dots, lines, waves, crosshatch, diamonds, rosette, Ben-Day,
  mezzotint, stipple, woodcut, scribble, hatching) reads its threshold there, so a dot is the same dot (pitch, angle,
  phase, its tone) with a round edge; dither, grain, sand and the stochastic tiles are the page's pixels and stay crisp
  page-pixel squares, as Ink gain's and Uneven's specks (the crayon's rule, section 3). Tone's dots (tone 1 at 254) and
  a print over prints' clearing between them go onto `GridPage` as tones over white (`coverTones`, `smearTones`); a
  coverage past a tone's own isn't kept there, so such a pixel fails the match. Print Shading's print tones are kept on
  the grid (`GridPage.setPrints`). A print over prints reads its neighbours' reach (`ModularCompose.edge`): its tiles are
  drawn a page pixel wider (`SharpView.drawn`), so its edge pixels match too.
- **The eraser's scrape:** not drawn finer; where it scraped dots the plane no longer matches and the page's pixels
  show (`SharpChecks.screentone`: no sharp dot where a dot was scraped; the rest stays sharp).
- **Checked:** each of the 140 Basic tools draws the same bytes as before from the same samples (every plane's tones,
  coverage, print tones and kinds hashed, at 6, 20 and 40 px, black and mid grey); `SharpChecks.screentone`: the five
  tones at 40 and 80 px and Halftone drawn again at 1 to a pixel byte for byte, at 4 to a pixel as dark (within 5%), each
  dot's edge pixels holding points of more than one grey (not Sand, whose grain is pixels).
- **Speed** (desktop, `SharpTools`' page of eight strokes and a tap, a 400% tile's mean, best of 3): Dot tone 40 px
  3.3 ms, 80 px 5.7; Halftone brush 6 px 6.2; HB pencil 6 px 5.8, 20 px 13.8. A screen of screentone costs about what
  one of pencil does. Not measured on the Manta.
- **Not done:** Misregister; a screen over 100 px; the eraser's cut drawn finer.

## Shapes as built; text and magnets not yet (2026-11-05, branch `sharp-more`)

The owner, 2026-11-05: "3 [shapes and text] seems like it might be quite easy".
- **Shapes** (Shapes' line, rectangle, square, oval, circle; filled or outlined; any grey): a shape is geometry laid as
  opaque spans in its grey (`ShapeStroke.lay`), not a brush stroke, so it is kept as its log entry (`StrokeLog.Shape`),
  now with its plane (`Entry.sharp`, the Manta's `DrawingPad.continueShape` and the browser's `shapeEnd`). Drawn again
  (`SharpStrokes.lay`): at 1 to a pixel the page's own spans, to the byte; finer, the same shape rastered at the grid's
  points (`ShapeGeometry.on`: positions and the outline's width `k` times, the dragged corner stopped at the page's edge
  as on the page), its edges a point wide. `GridPage` keeps each point's coverage once a shape lays opaque grey
  (`paintSpan`, `alpha`, `raw`), so the match compares raw tone and coverage (`SharpView.holds`) and the tile composes
  the shape's grey opaque over what lies under it. Strokes over a shape on its plane, and a shape over strokes, match as
  any kept strokes do. The file: `SHRP` version 3, written only when a shape is kept (each entry's ink too); the
  versions before read no `SHRP` from such a page and show its kept strokes as pixels, as they did a version 2 page.
- **Exports:** a page whose only kept marks are shapes or screentone now counts as holding kept strokes, so its PDF
  picture is High Res's (section 8).
- **Checked:** the shapes' rasters are the same as before (3,000 random shapes' spans hashed, before and after);
  `SharpChecks.shapes`: a line, boxes, an oval, a circle and a square, black and grey, filled and outlined, one past
  the page's edge, pencil under and over them: drawn again at 1 to a pixel byte for byte, at 4 to a pixel as dark
  (within 3%) with edges finer than a pixel, kept through the file. Desktop, that page: 3.3 ms a 400% tile.
- **Still pixels:** Filled Shape (a brush's Affects Page: its fill is worked out from the stroke's outline on the
  page's pixels, section 1); a shape on a layer with masking fluid where the fluid shields it (the match).
- **Text and magnets, not done:** they are not in the page's layers. The tablet draws their letters with Android's
  fonts at page pixels (`TextType`, `MagnetTiles`) into the page raster the zoomed view scales, over everything. Sharp
  letters need the zoomed view to leave them out of that raster and draw them at the view's scale over the tiles
  (`ViewportBitmap`, `TextMode`, `WordsMode`, and the browser's twins), with `TextBoxes.Type` and the magnets' tiles
  drawn at a scale. All Android drawing, none of it checkable on the desktop; left for a pass that can be tried on the
  Manta.
