# Surface: the paper belongs to the page (2026-11-01, branch `explore-surface`)

The owner's decision, 2026-11-01: "Surface moves to the notebook". The paper a mark meets is the page's, not the brush's.

## The model

- **A surface** is one of `BrushTexture`'s fourteen, in their order (never reordered; share codes and notebooks carry
  the index): Smooth, Cold Press, Ribbed (laid), Felt, Crumpled, Canvas, Cotton, Bumpy, Unryu, Library, Kraft, Vintage,
  Craft, Fine. Its words are the brushes' Paper choice labels (`LibraryCard.surface`).
- **The notebook's Surface** (`NotebookOptions.surface`, record `SURF`, one byte, written only when not Smooth). An
  older notebook, which has none, is Smooth: no legacy mode. Its drawings are pixels and stay as they are.
- **A page's own Surface** (`DrawingBook.surfaces`, record `PSRF`: the page count, then a byte a page, 0 for the
  notebook's, else the index plus one). A stack's is its bottom page's, as its template is; it goes with the page when
  moved, taken, deleted from under a layer. Choosing the notebook's own puts the page back to the notebook's. The
  recovery keeps both (`TSRF`, `TSRV`).
- **The page drawn on knows it**: `ToneDocument.surface`, placed by `DrawingBook` whenever a page is decoded or the
  options change (never in the page's bytes). Null means each brush on its own paper, as the engine drew before: the
  desktop checks and the web lab draw so unless they set one.
- **Every brush follows the page's surface**: a stroke starts by `brush.onSurface(document.surface)` (`QuickStroke`,
  `ModularStroke`): a copy whose Paper part is that surface at the brush's own Texture, how much of the surface shows
  through this brush. The brush itself, and its code, are never changed: an old Cotton brush loads, saves and shares as
  Cotton, and on a Cold Press page draws as a Cold Press one. Everything that reads paper grain reads the copy, so it
  reads the page's surface: `StrokePaper` and every stamper, Later's media (Feathering, Letterpress: `FadingInk.Spec`),
  Reprint (`Carry`), Make Ink (a fill's stroke), the wet washes (`ResolvedBrush.Wet.wash`).
- **Brush Lab** offers no paper choices and has no Paper card (the owner, later that day): its one dial, **Texture**, is a
  row of Medium's card (`BrushLab.dials`; the PAPER part stays in the model and every code, byte for byte), so the lab
  shows six cards, three rows (`BrushPart.SHOWN_LATER`: Control, Tip, Medium, Rework, Drying, After; `LabLayout`'s
  samples back to 117/80 dp from the seven cards' 33/28). After's line is "fades · spreads · ages".
  Brush Lab's samples, its test page and the Tools page's samples draw on the open page's surface
  (`WildSample.surface`, set as a page is shown; samples are kept per surface). Brush words ("This brush", card words)
  still name a brush's own paper: a later pass, with the web lab's `parts.js`/`voice.js`, which say the same.

## Surface properties

Each surface carries physical properties every effect reads, rather than each effect tuning itself paper by paper:

- **grain** (`grain()`: fine and mid noise, as before),
- **absorbency** (moved from Feathering's table: Smooth .35, Cold Press .9, Unryu 1.45; Cotton raised from 1.0 to 1.25,
  the owner's "soft bleed on cotton/high"),
- **fibre**: about how long (px) and which way (radians, NaN any way), and a map where the grain was made with fibres
  (`PaperGrain.fibre`: Kraft, Unryu; 0 none, else 1 + 254 × angle / π).

This pass: **washes follow absorbency** (`ResolvedBrush.Wet.wash`). Against cold press, the paper the washes were tuned
on: paper that drinks less pools to a darker rim and bleeds less (its Bleed dial scaled down, its water wetting blank
paper only when fresher); paper that drinks more soaks the water in, a softer rim, extra Bleed, its water feathering
further and wetting paper sooner (`WetWatercolor.Wash.wets`, `feather`). An Ink wash is ink made for paper that drinks:
on paper that drinks less it keeps the feathering it was tuned with. Feathering reads `absorbency` as before.
Later passes: Torn Paper, Letterpress, Salt, Foxing, Mold, Reprint's dot gain and Cross-stitch's weave reading
absorbency and fibre; Feathering's stand-in fibres reading the fibre map.

## Better textures

The scans were 256 px tiles, repeating under an inch (Cold Press and Felt with seams, Laid and Crumpled repeating
plainly). Now each surface is made whole by `SurfaceGrain`, 2048 px square (wider than a page: no repeat across it),
tiling without a seam, the same every time (its own SplitMix dice, so Brush Studio's WebAssembly gets the same):

- **Made from nothing**: Cold Press (rounded bumps of two sizes in a cellular pattern, a fine tooth, a slow
  undulation), Ribbed (faint wavering laid lines about 1 mm apart, chain lines about 25 mm apart), Canvas (a plain
  weave, threads uneven in spacing and thickness, wavering), Kraft (a quiet pulp, many short fibres lying mostly
  across) and Unryu (long, gently bending fibres): the last two map their fibres.
- **From their scans**: Felt, Crumpled, Cotton, Bumpy, Library, Vintage, Craft, Fine: the scan's own shading taken out
  (its bands and tile seams go), then laid as patches, each from a place in the scan by chance and never turned (a
  photo's light keeps its side), blended into its neighbours (a tent's weights squared, kept to the scan's spread).
- Fine is the surface; mid its height smoothed a few px (a scan's: the scan at three times the scale).
- Smooth is the app's paper as before (`PaperNoise`), unchanged.

Making one takes 70 to 250 ms on the desktop with four cores (rows shared out, `Cores`), so about 1.5 to 5 s on the
Manta: the drawing screen makes the page's surface out of the way when the page is shown (`BrushTexture.warm`), and two
are kept besides Smooth (about 8 MB each, 12 with a fibre map). A stroke costs what it did. The Surface screen's tiles
show each surface's scan (`BrushTexture.swatch`), not the whole surface: fourteen at once would take a minute.

## Where it is chosen

- **Notebook settings**: a **Surface** row after Page Colour, "Smooth ›", opening the **Surface** screen ("The paper
  every page is on"): fourteen pictured tiles, a pencil shading over each surface's tooth, light to dark.
- **Page Settings**: a **Surface** row after Page Colour, "Notebook's (Smooth)" or the page's own, opening the screen
  for the page ("The paper page 5 is on alone"), the notebook's tile named "Notebook's (Smooth)".

## Checks and pictures

`SurfaceChecks` (in `scripts/test_inkwash.sh`): the records and the recovery, old notebooks Smooth and byte for byte,
a page's own winning and moving with it, an old Cotton brush drawing on a Cold Press page as a Cold Press one with its
code unchanged, a wash's bleed past its rim on Smooth against Cotton, every surface made the same every time, no repeat
within a page's width (a 256 px window's correlation with every shift up to 1920 px under .6; laid and canvas leaving
out shifts straight along their lines; the old tiled scan scores 1.0) and no seam (the tile's edge changes no more than
any column inside), samples kept per surface. `java SurfaceChecks timing` prints the costs. Pictures:
docs/archive/pictures/explore/surface (`scripts/surface_pictures.sh`).
