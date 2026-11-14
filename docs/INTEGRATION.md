# Scribulor and MonoPaint: integration notes

For working with MonoPaint's developer (mpdairy) later: what Scribulor changed,
where the line between "engine" and "Scribulor app" runs, and what could go back
upstream. Docs only; nothing here is a commitment. Elling contacts the
developer himself.

Last checked: 2026-10-27, against `upstream/main` at `5f764ec` (2026-10-22; nothing new upstream since; merged on
branch `upstream-lasso`, 2026-10-23).

## 1. Purpose and status

- **Scribulor** is a fork of [MonoPaint](https://github.com/mpdairy/monopaint),
  a drawing app for the Supernote Manta and Nomad. MonoPaint is MIT-licensed
  (`LICENSE`, © 2026 mpdairy); Scribulor keeps that license and notice.
- **What it is:** a deliberately minimal pen-and-wash sketchbook (see
  `PHILOSOPHY.md`): ten toolbar slots plus an eraser, black/white/grey, nothing
  that keeps moving after the pen lifts but wet paint, for its drying time (and,
  with gravity, when the tablet tilts). On top of MonoPaint's fast e-ink pen
  path, documents and layers, it adds its own toolbar, a brush engine (the
  "six-part" modular brush) and Brush Lab, a screen for making brushes.
- **Identity:** installs beside MonoPaint (`applicationId ing.ell.mybaddrawings`, `app.helpyy.inkwash` until 2026-10-30,
  library folder `Superdood`, `My Bad Drawings` until 2026-11-04, own signing key). Java package is still
  `io.github.mpdairy.monopaint` (see section 6).
- **Owner's intent:** collaborate with MonoPaint, ideally by offering the
  brush engine as a shared module and sending generic fixes upstream.

### How far it has diverged (`upstream/main...inkwash-v2-lab`)

| Measure | Value |
|---|---|
| Scribulor commits not in upstream | 1119 (2026-10-13 to 2026-10-27) |
| Upstream commits not in Scribulor | 0 (`8743286` merged 2026-10-15, `cc40de2` to `5f764ec` 2026-10-23) |
| Files changed, whole repo | 924, +195,598 / −3,188 |
| of which app code (`app/src/main`) | 495 files, +64,835 / −1,992 |
| of which desktop checks (`app/src/test`) | 208 files, +35,567 / −12 |
| of which docs (specs, plans, prototypes; finished ones in `docs/archive/`) | 155 files, +89,251 / −67 |
| Java classes upstream has | 89 |
| upstream classes Scribulor modified | 44 (+ `viewport_bitmap.c`, `layers.h`, `CMakeLists.txt`) |
| upstream classes Scribulor deleted | 26 (+ `selection_preview.c`; listed in section 2) |
| upstream classes untouched | 19 |
| new Scribulor classes | 267 |

Section 2's lists were written class by class as each change landed; where they and these counts differ, the counts
are current. The class counts and section 2's (a) and deleted lists were rechecked 2026-11-05 (branch `tidy-upstream`,
against `upstream/main` at `5f764ec`); the other rows are from 2026-10-27.

The divergence is mostly *additive*: new classes beside upstream's. The edits
to upstream files are concentrated in `DrawingPad`, `PaintActivity` and
`ToneDocument`.

## 2. Map of the code

All classes are in `app/src/main/java/io/github/mpdairy/monopaint/`.
Categories: **(a)** upstream, untouched; **(b)** upstream, modified by Scribulor;
**(c)** Scribulor engine, pure Java (no `android.*`), candidate shared module;
**(d)** Scribulor UI/product layer, Scribulor-only; **(e)** tooling.

### (a) Upstream, untouched (18)

Display and e-ink: `DirtyRegions DotGray DotPattern QuarterTurnLayout CanvasGuide ProbeActivity` (the device checks'
probe). Input: `MultiFingerTap`. Controls: `ControlHost PageActionButton RotationSuggestion` (the turn's
hold; `PageActionButton` unused since the Rotate button went, 2026-11-10, "The app turns by itself" below). MonoPaint's tools, kept as the checks' references or for what they still hold: `ToneDabs`
(`ToolStroke`'s dabs; `StumpChecks` compares Soften with MonoPaint's stump), `PressureStroke` (`stump_pictures.sh`, the
device benches), `FloodFill`, `WatercolorWash` (only `test_document.sh`'s checks, whose source list no longer compiles),
`WetWorkBudget` (Wet paint's), `PaintState`, `ToolLibrary` (the colour, modes and presets still loaded and saved with
the preferences; nothing draws with them since the pen draws with Superdood's slots). Many of the classes this list
used to name are now modified (section (b) and the change entries below) or deleted:

### Upstream, deleted (26)

`AppSettingsDialog`, `PaletteEditor` (the refocus, 2026-10-24); `ImageImport`, `PictureFiles`, `PngExport` (Superdood's
own import and export); and, as dead code (2026-11-05, "Tidy: MonoPaint's unused code" below): the hidden toolbar and
its panels `Toolbar ToolRail ToolSettingsPanel ToolControls SidePanel LayersPanel ToolIcons PaletteSwatch`; the hidden
header and colour bar `ShadePicker WetnessBar WetDroplet DropletIcon MaskPeelTouch PaintRub PageLabel`; the edge
swipes and full-screen modes `EdgeBars EdgeSwipe FullscreenController`; `ToolStrokes` (MonoPaint's pen tools, never
reached), `SelectionPreview` (with `selection_preview.c`), `BristleTexture`, `SettingsScroller`.

Since Zoom came back (2026-10-19, below) Scribulor uses
`CanvasViewport`, `ZoomButtonTouch`, `MultiFingerTap` and `ViewportBitmap`'s zoomed raster again, unchanged; the first
three are in `scripts/test_inkwash.sh` as well as `test_document.sh`. `CanvasViewport` has since grown the fitted page,
the width filled sideways and Zen Mode's `cover`, and on the live branches the browser's `whole` (the whole page on any
screen, a pinch out back to it; 2026-11-01, `ZoomFitChecks.browser`) with its `wide` (a desktop's window filled across, 2026-11-05); all of it off on the Manta unless set.

### (b) Upstream, modified by Scribulor

| File | What changed, and why |
|---|---|
| `ToneDocument` | Block writes (`setTones`, `coverTones`, `smearTones`, `eraseTones`), reads (`strokeBaseTones`, `strokeBasePixels`, `strokeBaseKept`, `layerDarkness`, `compositeToneWithout`, `compositeToneKept`, `baseComposite`, `foundTiles`, `foundComposite`, `visibleLayers`, `touched`, `maskShield`, `maskLiftable`), `restack`/`setLayersVisible`/`setLayersShown` (visibility and opacity as one step, a layer's step, 2026-11-06), linked undo (`finishWithPrevious`), tile-at-a-time settle; one place for undo capture; Erasable's per-layer share (`Layer.share`, `begin(erasable)`, the one write rule), the masking fluid's too (`beginFluid`; `maskHatch` hatches only what lifts). The engine writes the page through these. |
| `DocumentCodec`, `BookCodec` | TSM5: tagged sections after TSM4's body (Erasable's shares, `ERAS`; wax's, `MSHR`; the kinds of mark, `KIND`; kind planes, `PLNS`, a layer's look in the layers' part; Show Kinds' steps, `KSHN`, a byte), written only when a page needs one; Second Ink's shares, `INKS`: per layer a mask byte (bit 0 its pixels, bit n+1 plane n **as memory holds it**, so Vector Ink's plane is bit 5 although `PLNS` keeps it in the Pen plane) then a byte a pixel per set bit, exact length, written only when a share is nonzero, applied after `linePlanes`, a bit for an absent plane fails the file, older versions skip it; the single-page fast paths open TSM4/TSM5 (see "Erasable", "Resist" and "Kind layers" below). |
| `DrawingPad` | Draws with Scribulor strokes on ink/wash layer pairs; the finger tool (erase, smudge or a slot's brush); the hovering nib's outline (`HoverNib`); erasing shows paper; pen speed; auto full-refresh at pauses; drying ticker; native area render; the hatch drawn by a static `drawMask` Brush Lab's test page shares; two fingers a tap until they move, then MonoPaint's pinch and pan (`PinchPan`), the page's map from `PageView`. Heaviest edit. |
| `PaintActivity` | Scribulor bar replaces MonoPaint's controls (upstream ones built but hidden, `53b444a`); automatic names, rename/delete, PNG copy on leaving; Brush Lab and Tools page launch (Tools → Brush Lab → Tools, the slots' start carried through); slots, swatches; the side bar in MonoPaint's tool column place. |
| `ViewportBitmap`, `viewport_bitmap.c` (+ new `compose.h`) | Native rendering of a changed area, not only whole pages (`84a568e`). |
| `WetWatercolor` | Wet paint (2026-10-22): hooks marked "Inkwash", upstream's behaviour unchanged without them: `gesture(erasable, kinds)` (its own slices begin as erasable and of the kind as the stroke that woke it), `setFrames`, `rest()`, `rewet(wake)` (take up what something else changed), `finishStroke(withPrevious)`, and in `paintMask`'s whole-canvas mixing `LIGHT_MIXES` (opaque paint lighter than the wet paint under it covers more than it mixes; light over dark, 2026-10-22). Now live engine code under the six-part engine's Wet choice. Generic: any host can drive it beside other tools. |
| `PaintLoad` | Wet paint: `left(share)` sets its own paint from Smudge's Paint Load (`LoadedPaint`) instead of its own run out. |
| `DrawingStroke` | Default `sample(..., long time)` so strokes can follow pen speed (`bcee59b`); default `present()`, called once an input event, so a stroke can compose once an event instead of once a sample (`bc7b014`); default `zoom(scale)`, so a stroke reads the pen's speed on the glass when zoomed. All backward compatible. |
| `BrushDirection` | `azimuth`/`tiltLean`: tilt read one way, in doubles, for the engine. |
| `BrushStamp` | Per-thread scratch paths (`ThreadLocal`) so Brush Lab samples on a background thread. |
| `PressureStroke` | None since the audit (2026-10-19): `headThickness`, for Scribulor's old pens, went back to upstream's. |
| `ToolSettings` | `travel` made visible, an inverse helper `pressure` (the checks use it; `pressureFor`, for the old pens, went at the audit). `travel` has a knee since 2026-10-29 (the old line to .30, then on to 1 at .78; firmware 3.31.45, `docs/TILT_INPUT.md`). |
| `ToolStroke`, `NativePen` | Read pen pressure through `ToolSettings.travel` instead of their own copies of the `.05–.45` line (2026-10-29). |
| `DocumentStore`, `DrawingFiles`, `DrawingBrowser` | Rename and delete drawings, following `AtomicFile` backups and, since 2026-11-14, the previous save and a damaged file set aside. |
| `Ui`, `ToolButton`, `PressOutline`, `PaletteSwatch` | Inverted selection, selected frame, pointer-indexed `outside` (palm fix), Brush Lab helpers. `ToolButton.faces`: `iconOnly` without asking Android for a frame, for a change the caller puts on the panel (2026-10-24). `ToolButton.slow`, `PressOutline.inverted` and `clicked`, `ControlRaster.draw(…, binary)`: a slow button's press inverted in one binary update, let go in the click's own frame (2026-10-25, see "Press feedback"). `markerArea` of a bar row (`SlotTile.Face`) is its side bar alone (2026-10-25, see "Side bars alone"). |
| `ShadePicker` | Its strip's numbers and pixels (`ShadeStrip`, pure) and its pen and marker feedback (`StripMarker`) moved out, unchanged, so Scribulor's grey bar draws and follows the pen with the same code (see "The grey bar" below). |
| `PaintPreferences` | Brush Lab Basic/Advanced flag. |
| `SelectionFeedback` | `cleanAfterClose` (a closed popup's patch cleaned, 2026-10-23) and captures on white. The several-patch `update` for the Tools page's slot blink (2026-10-19) went with the blink. Updates of views inside a view whose update is running gather into its one present (`gathering`, 2026-10-24, see "Fast pickers"). A nested `updateBinary` restores the flag it found (2026-10-25, see "Snappy UI"). A tool pick updates only the patches gathered inside it, ink and paper boxes in mode 5, and a menu is put on the panel before its window shows (`updateParts`, `presentAhead`, 2026-10-25, see "Snappy UI 2"). Otherwise the one-patch path is as upstream's. |
| `CanvasGravity` | Reused for Scribulor's gravity: also made from a `Context` with its own switch, `read()` gives a `PageTilt`, the display's rotation honoured (see "Gravity" below). |
| Build and identity | `app/build.gradle` (applicationId, version, signing, library folder), `AndroidManifest.xml` (Brush Lab activity; dropped `dev.tilesmile` alias), `strings.xml`, launcher icon, `README.md` (icon line only), `CLAUDE.md`, `CHANGELOG.md`, notices (Phosphor icons). |

#### Design pass (2026-10-15): upstream files touched

The look lives in Scribulor's `Theme`; MonoPaint's `Ui`, `ToolButton`,
`SettingsForm` and the screens only MonoPaint shows (`Toolbar`, `LayersPanel`,
`SidePanel`, `FullscreenController`; `PaletteEditor` and `AppSettingsDialog` deleted at the refocus, 2026-10-24) are
untouched. One seam: MonoPaint dialogs and the file menu that Scribulor shows are
restyled after MonoPaint builds them.

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | 4 | `Theme.fonts` at start; `Theme.restyle` after the file menu's `SettingsForm.style`; `Theme.dialog` at the end of `compactDialog` and `orientDialog`. |
| `DrawingBrowser` | 1 | `Theme.restyle` on each Open/Save row (built after the dialog). |
| `PaintPreferences` | comment | "Simple" reads "Basic". |
| `WildSample` (Scribulor engine, not upstream) | 1 | `reach` package-visible for `InkWash.tileCap`. |

#### One engine (2026-10-15): upstream files touched

None. Every brush became a six-part brush (plan `docs/archive/superpowers/plans/2026-10-15-one-engine.md`) and Scribulor's old
engines were removed; MonoPaint's own tools (`PressureStroke`, `ToolStrokes`, `WetWatercolor`, …) are untouched and
draw MonoPaint's mode as before. Two Scribulor edits to upstream files are now unused by Scribulor and could be undone
to shrink the divergence: `PressureStroke.headThickness` and `ToolSettings.pressureFor` (both undone at the audit, 2026-10-19).

#### Speed on the Manta (2026-10-15): upstream files touched

Branch `inkwash-perf`, rebased onto the one-engine work and fast-forwarded into `inkwash-v2-lab` (`2874178`…). The
engine work is in Scribulor's own classes (`ModularStroke`, `BodyStamper`, `BodySettle`, `FloatTiles`, `HakeRibbon`,
`LineStamper`, `Placement`, `StrokeMath`, `ModularCompose`, new `Prof`); upstream files:

| File | Lines | Why |
|---|---|---|
| `DrawingStroke` | 6 | Default `present()`: an input event's samples in, then one compose (`bc7b014`). |
| `DrawingPad` | 1 | `presentInput` calls `stroke.present()` before rendering (`bc7b014`). |
| `ToneDocument` | 2 | `touched(x, y)`: whether the gesture changed a tile, so a compose can pass over tiles it never touched (`4f64db4`). |
| `app/build.gradle` | 11 | A `bench` build type (`-Pbench`): release speed, own id `.bench`, own library folder (`2874178`). |
| `WidthInstrumentation` (androidTest) | 8 | `-e modularPerf true` runs `ModularPerfChecks`/`ModularPerfBench` (`2874178`). |
| `app/src/bench/AndroidManifest.xml` (new) | 10 | Bench build: profileable, no launcher entry (`cccaafb`). |

#### Brush pen tilt range (2026-10-15): upstream files touched

None. Engine files (Scribulor's own): `LineStamper` two lines (tilt width and flattening through
`ResolvedBrush.Pen.tiltWidth`/`tiltFlat`), `ModularStroke` two lines (what settles after lift comes from
`ResolvedBrush`); a new hand dial, Tilt range, that M codes write only when wide (engine spec, "Tilt range").

#### Settle pacing and wet indicator (2026-10-15): upstream files touched

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | 1 + 1 (−7) | `onTouchEvent` reports each pen event's handling time and age to the session's pace (`WatercolorSession.input`); `settleDrying` is now `DryingTicker.settleNow` (the same steps, moved so the wet indicator's tap and Brush Lab share them). |

Read, not edited: `DropletIcon.outline()` (the droplet's path) for Scribulor's `WetIndicator`. Engine (Scribulor's own):
new `SettlePacer`; `WatercolorSession` (`pace`, `input`, the paced `tick`, `wet`, `wetChanged`); `ModularStroke` (a
Fast-wet re-settle's box shows when the pace allows, at lift at the latest). UI (Scribulor's own): `DryingTicker`, new
`WetIndicator`, `InkWashBar`, `InkWash`, `BrushLabActivity`, `BrushTestPad`, `LabLayout`. See section 5.

#### Speed round 2: batched pen input, four cores (2026-10-15): upstream files touched

Branch `inkwash-perf2` (engine spec, "Round 2: what the pen waits for"). Engine (Scribulor's own): new `Cores` (the
helper pool, `BodySettle`'s moved onto it), `ModularCompose` (bands; `composeLattice`), `ModularStroke`, `LineStamper`,
`BodyStamper`, `HakeRibbon`, `StrokeMath.blur`, `FloatTiles.between`, `Prof`; glue: new `PenBatch`, `StrokeTiming`.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +40 −6 | `openBands`/`closeBands`: while open, writers on several threads may each write and read their own whole tile rows (Undo captures and the dirty area under a lock, the base-tile cache passed over); `TILE` package-visible; `coverTones` looks the gesture's base tile up once a tile. Generic: any tool could compose in bands (`4b80663`). |
| `ViewportBitmap` | +10 −2 | `compose(area)`: an area of 64k pixels or more rendered in row bands on several cores (`fdbc420`). |
| `DrawingPad` | +60 −5 | Scribulor pen strokes gather samples (`PenBatch`) and draw them from a posted runnable (`drawBatch`, `flushBatch` before finish); stroke presents spaced by 1.5 × their recent cost (8–100 ms, `presentGap`, now `PresentPace`); the present's parts into the stroke timing; `liveFastPlane` (off) begins the fast preview at pen-down for Scribulor strokes (`6193c27`). |
| `DirectEink`, `cpp/direct_eink.c` | +10 / +20 | The present's parts timed (turn, compare, copy, driver call; `presentParts`) (`6193c27`). |
| `WidthInstrumentation` (androidTest) | +10 | `-e liveStrokes true` runs `LiveStrokeBench` (`b38307e`). |

#### Gravity (2026-10-15): upstream files touched

Branch `inkwash-gravity` (plan `docs/archive/superpowers/plans/2026-10-15-gravity.md`; engine spec, "Gravity"). The page's
tilt comes from **MonoPaint's own `CanvasGravity`** (its accelerometer handling, low-pass filter, first reading used as
is, and its flat dead zone), reused rather than duplicated:

| File | Lines | Why |
|---|---|---|
| `CanvasGravity` | +20 −10 | Also made from a plain `Context` with its own `enabled` switch (MonoPaint's constructor passes its "Watercolor uses gravity" setting, as before); `read()` gives a `PageTilt` (its dead zone and strength, moved there so the engine shares them); the reading turned by the display's rotation (`PageTilt.fromDevice`; 0 while Android stays in portrait). `apply(WetWatercolor)` gives MonoPaint's wet paint the same pull as before. Generic: any tool could read the page's downhill this way. |

Engine (Scribulor's own): new `PageTilt`, `GravityFlow`; `DryingWater` (a flow beside its patch, made the first tilted
tick), `WatercolorSession` (`tilt`, `wantsTilt`, `tiltChanged`, `strokeReadsTilt`), `ResolvedBrush` (`Grav`, `dust`,
`wetMs`, the hand's page tilt), `StrokePoints.ga`, `StrokeMath.gSize/gDark/gWidth`, the stampers (`LineStamper`,
`BodyStamper`, `HakeRibbon`, `Placement`, a line or two each), `ModularStroke` (Quick's window, dust, the hand's
reading), `StrokePaper` (a constructor from a grain, for the checks), `Prof` (gravity's sections); the dials
(`PartDial`, `PartChoice`, `BrushPart`, `ModularCode.ADDED`, `ModularBrush`, `ModularStyle`, `ModularVariations`).
UI (Scribulor's own): new `TiltFeed`; `InkWashBar` and `BrushLabActivity` (one each), `BrushLab`, `HandGrid`, `LabLayout`,
`LabSettings`, `ModularWords`, `ToolPresets`. Not touched: `WetWatercolor` (MonoPaint's flowing watercolor) and its
gravity, which stay MonoPaint's own; `DrawingPad` and `PaintActivity`.

From MonoPaint (`monopaint-speed-techniques.md`'s ranking): measuring the present's parts (1); cost-aware present
spacing (3), and drawing from a posted runnable so queued input runs first; spare-core sharing (6, the `share()`
pattern of `f9d40db`/`281373d`, generalised as `Cores`); the fast binary plane for the live stroke (2) is wired from
upstream's `beginFastPreview` but off (the Manta has the plane only from firmware 3.30.45). Not taken: presenting from
another thread (4); batching made it unneeded so far.

#### Drawing screen C and the Tools page (2026-10-15): upstream files touched

Spec `docs/archive/superpowers/specs/2026-10-15-tools-page-design.md`. The left edge reuses MonoPaint's own place for a tool
column: `PaintActivity.body`, the row beside the pad where MonoPaint's `Toolbar.toolScroll` goes on the drawing hand's
side (hidden by Scribulor), so `CanvasViewport`'s "paper under the bars" and the pad's page ↔ view mapping, input and
e-ink connection work unchanged.

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | +11 −1 in `showInkWashBar` | Scribulor's arrangement in either turn: root vertical, its bar on top at `DrawLayout`'s height, the side bar (`SwatchBar`) in `body` where MonoPaint's tool column goes, the pad beside it (landscape no longer uses MonoPaint's turned header arrangement, which was hidden anyway). |
| `PaintActivity` | +25 −1 | `openTools`/`toolsReturned`: start `ToolsActivity` with the slot and the app's turn; put the chosen brush in the slot (`ToolSlots.use`, one Undo step), then Brush Lab if asked. |
| `DrawingPad` | 1 | The auto full refresh redraws both of Scribulor's bars (`inkWashBar.invalidate()`), not only the top one. |
| `AndroidManifest.xml` | 2 | `ToolsActivity` (portrait; turned by its own `QuarterTurnLayout`). |

Read, not edited: `QuarterTurnLayout` (turns the Tools page as the drawing is), `SelectionFeedback` (the side bar's
partial updates), `Toolbar`'s column placement in `applyToolboxSide`.

#### One side toolbar (2026-10-15): upstream files touched

Spec `docs/archive/superpowers/specs/2026-10-15-tools-page-design.md`, "One side toolbar". The top bar and design C's side bar
merge into one column (`InkWashBar`), still in MonoPaint's tool column place (`PaintActivity.body`), so the pad's page
↔ view mapping, input, zoom and e-ink connection are unchanged; `DrawingPad` and `CanvasViewport` untouched.

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | +4 −6 in `showInkWashBar` | No bar on top of `root`: the one column goes in `body` on the drawing hand's side, at `DrawLayout`'s width, then `arrange`. |
| `PaintActivity` | +4 −4 in `menu` | The file menu (its only caller is Scribulor's menu button) opens beside its anchor, toward the page, level with it, instead of below it. |

Scribulor's own: `InkWashBar` (the column; panels open sideways), `DrawLayout` (the fit along the height, folding the
layers), `SwatchBar` (now the faces' group inside the column), `SlotTile` (tiles in a column as well as a row),
`DrawLayoutChecks`.

#### Quick strokes (2026-10-15): upstream files touched

Branch `inkwash-quick` (engine spec, "Quick then render"). A stroke too heavy to keep up live on the Manta, decided at
pen-down from the brush and its size (`QuickStroke.keepsUpTo`), draws a flat preview of the brush's footprint under the
pen while a thread of its own draws the true stroke from the samples as they come (its own buffers only; with helpers
of its own at the lowest priority, so it takes only the cores the pen's thread leaves); at lift the preview is taken back and the true
stroke composed, while the wet indicator shows. Engine (Scribulor's own): new `QuickStroke`; the stampers' geometry as
methods (`LineStamper.nib`, `BodyStamper.shape`, `HakeRibbon.thickness`/`target`, `ResolvedBrush.Pen.tilts`: no pixel
changes), `ModularStroke` (`selectLayer`, `retapers`, `handTilt`/`readsTilt`, `render`: its own tilt source, unpaced,
its gesture `begin`s later), `ModularCompose` (`previewWash`, `flatTone`), `Cores.quiet`, `PenBatch.feed(stroke, i)`/
`moveTo`, `WatercolorSession.developing`, `Prof` (two sections). UI (Scribulor's own): new `QuickLift`, `BrushTestPad`,
`InkWash`.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +20 −2 | At pen-up a `QuickStroke` stays the pad's stroke while `QuickLift` looks in on its render (`liftQuick`); a pen-down, a finger erasing and the droplet's tap (`settleDrying`) finish it first (every other edit already calls `finishStroke`); `stroking()` is false meanwhile so the droplet shows. |

Not touched: `DrawingStroke` (a quick stroke is a `DrawingStroke`; the pad knows `QuickStroke` by `instanceof`, as it
knows `EraseBothStroke`), `ToneDocument` (the preview is a gesture cancelled at lift, the true stroke a gesture of its
own: one undo step), MonoPaint's tools. The eraser keeps its path: at its Large size (220 px) it costs about 0.14 ms a
sample on the Manta (desktop × 20), well within the pen.

#### Nomad pass (2026-10-15): upstream files touched

Branch `inkwash-nomad` (lab spec, "Nomad pass"). UI (Scribulor's own): `LabLayout`, `LabSettings`, `PartCard`, `HandGrid`
(Advanced at 12 sp), `ToolsLayout`, `ToolsActivity` (the frame's words never cut), `InkWashBar` (a layer thumbnail
put back at its old size draws its picture again). Engine (Scribulor's own): `PartDial.shown` says "10 mm" for the Wet
window's top (was "10.0 mm"). Tooling: new `ScreenShots` (androidTest) and `scripts/screens_device.sh`.

| File | Lines | Why |
|---|---|---|
| `WidthInstrumentation` (androidTest) | +9 | `-e screens true` runs `ScreenShots`: the lab, the Tools page and the drawing screen at the Manta's and the Nomad's sizes, in the bench build. |

#### The panel's side (2026-10-15): upstream files touched

Branch `inkwash-panel` (engine spec, "Quick then render", "The panel's side"). Counting the e-ink controller's frame
interrupts showed that on the Manta's firmware 2606141001 the gray update (mode 7) and MonoPaint's pen request (mode 9)
both keep the panel busy about 270 ms, and driver mode 5 about 145 ms; a pixel shows only an update's time after its last
change, so a live brush that keeps changing the pixels under a big nib shows late. Scribulor's own: new `StrokePanel`
(every pen stroke but the eraser goes to the panel through the fastest update while the pen is down), `QuickStroke`
(`PANEL`, `panelReach`: past 3 mm every mark that changes pixels under the pen goes quick), `ModularStroke.lineReach`,
`BrushTestPad` (the same for Brush Lab's pad); bench: `PanelFrames`, `LiveStrokeBench` (modes, `hold`).

| File | Lines | Why |
|---|---|---|
| `cpp/direct_eink.c` | +5 −4 | Mode 5 (flags 0, main plane) accepted for exact black/white pixels, checked as mode 9's are. |
| `Device` | +9 | `fastStrokeMode()`: the driver mode for a stroke's black/white pixels on this tablet and firmware (the Manta before 3.30.45: 5; else none known). Only `Device` names tablets. |
| `DrawingPad` | +31 −14 | An Scribulor pen stroke opens `StrokePanel`'s session at pen-down (the unused `liveFastPlane` switch goes); `endStrokePreview` ends it before the stroke's last render and sends what it showed plus that render as one forced gray update. |

Generic: any MonoPaint pen or preview could use mode 5 on that firmware; the Nomad and 3.30.45 are unmeasured, so
`fastStrokeMode` says none there and MonoPaint's mode 9 path is used where it exists. A note for mpdairy: on this
firmware a forced gray refresh of unchanged pixels returns 17 and drives nothing (the driver compares with what the
panel shows), so `refresh` does not clear ghosts there. Mode 1 runs 75–83 frames (about 1 s, a full flashing update) and does
redrive unchanged pixels, but afterwards the driver takes the area for white whatever its pixels (a gray update of white
over it then returns 17), so it is no drop-in refresh either. Mode 15 restarted the Manta: never send modes the firmware's own library doesn't.

#### Lift after the fast update (2026-10-16): upstream files touched

Branch `inkwash-liftbug` (engine spec, "Lift after the fast update"). The fast update left quick strokes ghosted after
lift. The driver drives only pixels asked to change from its record, and the fast update records what it showed while
leaving pixels part-driven, so the gray update at lift passed by every pixel whose final the preview had shown (99% of
a Gouache stroke). Strokes take the gray update again (`StrokePanel.wants`: the bench only). Wherever a fast session is
still used, its end goes through `DirectEink.endFast`, which has the driver drive all of its pixels again (at the price
of a blink). Scribulor's own: `StrokePanel` (`wants`, the bench's `redrive`/`endNear`), `BrushTestPad` (its fast session
ends through `endFast`, no `forceNext`); new `cpp/panel_end.h` (pure C) and its desktop check
`app/src/test/cpp/panel_end_check.c`; bench: `LiveStrokeBench -e cases` (either screen, per-case panel, bands, pressure).

| File | Lines | Why |
|---|---|---|
| `cpp/direct_eink.c` | +37 | `nativeEndFast`: `panel_end` over the two sessions' records and the gray session's plane, submitting gray updates (a busy display waits a ms before a submit again). |
| `DirectEink` | +26 | `endFast(fast, bitmap, dirty)`: through the buffer on a turned page as `present` is; a pair not sharing a plane or the bench's old end refreshes as before. |
| `DrawingPad` | +32 −12 | `endStrokePreview` keeps the stopped fast session (`endingFast`, `endingArea`) until `endFast` has shown it with everything pending, retrying while busy; `flush` does that first. |

Generic: any MonoPaint preview on a fast plane that ends with a gray update over the same plane has the same trap on
this firmware. Where the final keeps the preview's pixels, a forced refresh drives nothing.

#### Smudge (2026-10-16): upstream files touched

Branch `inkwash-smudge` (spec `docs/archive/superpowers/specs/2026-10-16-smudge-design.md`). Engine (Scribulor's own): new
`Carry` (the reservoir and its dabs, big ones on `Cores`) and `CarryPlane` (the stroke's own layer as found at pen-down,
premultiplied, tile by tile); `ResolvedBrush` (`Carry`, Own paint scaling cover, opaque Lift's tint), `ModularCompose`
(the stroke's paint over the plane, written through `smearTones`), `ModularStroke` (the pass after the stamps;
`reachCarry` for a quick render), `QuickStroke` (the pen thread fills the plane before the render takes a sample; a
smudge's dab in the quick rule, `CARRY`), `ModularFeatures` (`CARRY` on; `late`, the Bristles branch's late roll),
`ModularStyle`/`ModularVariations` (that late roll, copied unchanged from `inkwash-bristles`), `ModularWords`, `Prof`.
UI (Scribulor's own): `ToolPresets` (Stump), `WildSample` (a smudge's samples over a band on its own layer), `LabLayout`.
Not touched: `DrawingPad` (settling first is `InkWash.stroke`'s `settleNow`, as before), MonoPaint's tools.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +38 −6 | `strokeBasePixels`: the gesture's found tones with their coverages (premultiplied: what a smudge reads, from the gesture's captures, so a quick render reads the page as the pen found it). `smearTones`: `coverTones` with the whole coverage floor given (the smeared coverage, not the found one), so a smear can thin a layer as well as darken it without a light halo over the layer below. Same Undo capture, dirty area and masking-fluid shield. Generic: any smudge or blend tool could write this way. |

#### The wait after lift (2026-10-16): upstream files touched

Branch `inkwash-lift` (engine spec, "The wait after lift"). Engine (Scribulor's own): new `StrokeTones` (a stroke's tones
composed off the page, written in one pass) and `LiftingStroke` (a stroke whose lift's work runs off the pen's thread,
beside `DrawingStroke`, which stays untouched); `ModularCompose` (composes onto a `Page`: the document or `StrokeTones`;
a pixel without paint passes coverage −1, read as 0), `ModularStroke` (`develop`, the Drying lift split into its
buffers' part and its compose, `lift`/`develop(nanos)` for a live Drying stroke), `QuickStroke` (composes ahead, the
lift's work on its thread), `WatercolorSession.onlyRendering`. UI (Scribulor's own): `QuickLift` (either kind of
`LiftingStroke`), `BrushTestPad`, `WetIndicator` (a render's droplet only after a second), `InkWashBar`,
`BrushLabActivity`.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +30 −6 | `shareBase`/`baseTones`: while shared, another thread may read what the gesture's layer held when it began (a changed tile from its Undo capture, any other from the page), so a quick render composes ahead while its preview draws; captures, a gesture's begin and its cancel take the capture lock then. Generic: any tool rendering a stroke on another thread could read its base this way. |
| `DrawingPad` | +6 −5 | `liftQuick` hands any `LiftingStroke` that has work after lift to `QuickLift` (was: `QuickStroke` only). |

#### Float (2026-10-16): upstream files touched

Branch `inkwash-float` (spec `docs/archive/superpowers/specs/2026-10-16-float-design.md`): suminagashi, a fourth Drying choice.
Engine (Scribulor's own): new `FloatBath` (floating paint as outlines, its ops, log and snapshots), `FloatScan` (the
scanline film), `FloatTrace` (a stroke's films), `FloatPen` (a stroke on the water), `FloatWater` (the bath on its
layer, one undo step to its print); `ResolvedBrush` (`Float`, `Life.FLOAT`; no Smudge reservoir on water),
`PartChoice`/`PartDial` (`FLOAT`, `F_SPREAD`, `DRIFT`, appended), `ModularFeatures` (`FLOAT`, `varies`),
`ModularStroke` (stamps but composes nothing; frames under the pen; films at lift), `QuickStroke` (Float always quick,
the render thread's frames shown by the pen's thread), `WatercolorSession` (the bath beside the Slow patch:
`settleBefore`, ticks, `undo`), `ModularStyle` (`rollFloat`, from the seed after every part), `ModularVariations`,
`ModularWords`. UI (Scribulor's own): `InkWash` (`settleBefore`), `ToolPresets` (Sumi, Clear water), `WildSample`
(a Float tile's rings), `BrushLab` (Lay down greyed on water). Not touched: `DrawingPad`, `DryingTicker`, `WetIndicator`,
`TiltFeed`, MonoPaint's tools.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +2 | `undoSteps`: how many steps Undo holds, so a bath whose continuation emptied its step (it went back to the page as found) starts a new one next time. Generic. |
| `PaintActivity` | +2 −1 | `undo`: while paint floats, the session takes back its last stroke (`WatercolorSession.undo`), else the page's Undo as before. |

#### Settling off the UI thread (2026-10-16): upstream files touched

Branch `inkwash-settle` (engine spec, "Settling off the UI thread"). None: no upstream file changes. Engine (Scribulor's
own): new `SettleWorker` (the thread a session's wet paint works on); `WatercolorSession` (`worker`; `tick` starts a
tick there and writes it when done; what laying the wash or printing the bath now writes worked out there ahead, written
by `settleNow` without waiting for a tick); `DryingWater` (`work`/`apply`, `finishing`, `lift`, `build`, `stop`: a
step or the finish worked out off the page into a `StrokeTones`, the lift's read and the lay on `Cores`);
`GravityFlow.TonePage.flush` (into a `StrokeTones`); `FloatWater` (`work`, `printing`, `show`, `tones`: a drift tick
and the print worked out off the page); `StrokeTones` (`smeared`: a smear's writes, a bath's; workers may share a
tile); `WildBrush.Random` (`ahead`, `skip`: a draw known from its place). UI (Scribulor's own): `InkWash` and
`BrushLabActivity` give their sessions a worker. `DrawingPad`, `DryingTicker`, `BrushTestPad` and `WetIndicator` are
unchanged: the ticker calls `tick` as before, now answered every 5 ms while the work runs.

#### Erasable (2026-10-16): upstream files touched

Branch `inkwash-erasable` (spec `docs/archive/superpowers/specs/2026-10-16-erasable-design.md`): a Medium dial, Erasable, the
eraser and Smudge respect. Engine (Scribulor's own): `PartDial.ERASABLE`, last of every Medium choice (`PartChoice`, with
each medium's real value, `erasable()`), `ModularCode.ADDED`, `ModularStyle` (rolls set the real value without drawing
from the Random; Oils off), `ModularBrush.switchTo` (a medium chosen starts at it), `ModularVariations` (never nudged),
`ModularWords`, `ResolvedBrush.erase`; `ModularCompose` (beside each tone, the stroke's own darkness over white, `owns`,
through the `Page` writes), `StrokeTones` (keeps them), `ModularStroke` (`begin(e)`), `DryingWater` (its ticks as
erasable as the stroke), `FloatWater` (the print's film as its own paint, the least Erasable of the strokes floating),
`CarryPlane` (a pixel's kept part stays put; the plane holds the loose part), `EraseBothStroke` (its preview at the kept
coverage), `InkWashLayers.resized`. UI (Scribulor's own): `ToolPresets` (every Basic tool at its medium's value, Sketch
pencil), `ToolSlots` (`FIRST` at real values; once, a slot holding a Basic tool as it came takes its new code),
`LegacyBrushes` (comment only: old I/P conversions and stored built-ins stay at 1). Not touched: `DrawingPad` (its
eraser preview is `EraseBothStroke.erasedTones`), `PaintActivity`, MonoPaint's tools (they write as Erasable 1).

**Changed 2026-10-22** (branch `erasable-default`; the owner: "per-tool things should be erasable unless the user
specifies"): every medium starts at 1 (`PartChoice.erasable`, `ModularStyle.erasable`; Oils too), no roll changes it
(`ModularStyle.keepErasable`: Randomize, a style, the slot's dice, the Medium's die; the dial has no die or lock in
Brush Lab, `LabSettings`; `ModularBrush.switchTo` keeps it across media), every Basic tool and `ToolSlots.FIRST` at 1,
and `ToolSlots.erasable` (flag `tool_slots_erasable_1`) sets every stored brush below 1 to 1 once, Resist's wax apart.
Scribulor's own files only.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +232 −54 | `Layer.share`: per layer, a byte a pixel, the share of its paint the eraser takes (255 all; null until the layer first holds paint that isn't wholly erasable, then made at `begin(erasable)`), carried by `copy`, copy-on-write, `expand`, `restack`, Undo (tile captures hold it, `beforeShare`; a tile captured before the layer had one restores to 255), `cancel`, `restoreSpan`, `clear` (to 255), `clearAllLayers` (dropped). The one write rule in the block write and `store`: the kept darkness is the gesture's permanent share of `owns` (each pixel's own darkness over white, or the darkening since found) over what the pixel kept as found, exact for transparent paint; a pixel left lighter by wet paint loses its permanent share of what went. `eraseTone`/`eraseTones`/`eraseFromBase` leave the kept part; `compositeToneKept`, `strokeBaseKept`. Captures are also kept by tile key (`foundTiles`), so `captureOf` needs no map or lock (a band reads only its own tiles). Pages without shares, and gestures at Erasable 1 on them, write exactly as before (the bench's 210 page hashes unchanged). Generic: any eraser-proof layer of paint (a permanent ink, a fixative). |
| `DocumentCodec` | +56 −5 | TSM5: TSM4's layers, a masking-fluid flag and the fluid, then tagged sections (tag, length, bytes; an unknown tag skipped; a zero tag ends them), all inside the checksum; the first, `ERAS`, each layer's flag and share. Written only when some layer keeps a covered pixel's paint; otherwise TSM3/TSM4 byte for byte. Older readers refuse TSM5 ("Unknown painting format") and leave the file. Generic: the sections are a place for any later per-layer data without a new magic. |
| `BookCodec` | +2 −2 | The single-page fast path and `readFirstPage` take TSM1–TSM5 (TSM4 was missing). |
| `ModularPerfBench`, `ModularPerfChecks` (androidTest) | +86 −2 | `-e erasable`, `-e marksErasable`, `-e eraser true` (the eraser over pages with and without shares, and the heap a pair's shares take). |

#### Text pass (2026-10-16): upstream files touched

Branch `inkwash-text`, `docs/TEXT-STYLE.md`. Only words: the dialogs and notes MonoPaint shows inside Scribulor say
"drawing" (not "painting") and say less. No behaviour changes.

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | +15 −20 | Storage, save, recovery and rename dialogs reworded; "Saved" without its path; the untested-firmware note no longer points to Settings (Scribulor shows no Settings); the slot label after Undo is the brush's name alone. |
| `DrawingBrowser` | +7 −7 | Open and Save drawing: "Drawings", "Name", "Empty folder", "A drawing has this name." |

#### Stipple and Hatch (2026-10-16): upstream files touched

Branch `inkwash-hatch` (spec `docs/archive/superpowers/specs/2026-10-16-new-modules-design.md`, "As built"). Engine (Scribulor's
own): new `StippleStamper` and `HatchStamper` (the web lab's newmods.js stampers, stateful in `LineStamper.Meta`),
`ResolvedBrush.Pattern`, `PartChoice` STIPPLE and HATCH, `PartDial` (their dials, Build-up `ADDED`), `ModularStroke`
(their stamps, reach, every pixel never the lattice, Build-up's ticks), `QuickStroke` (their rows and preview),
`ModularStyle` (`rollPattern`, from the seed), `ModularFeatures.seedOnly`, `ModularWords`, `ToolPresets`; new
`HeldPen` (a quiet pen's last sample again). UI (Scribulor's own): `BrushTestPad` (HeldPen's tick), `LabLayout`,
`LabText`, `LoopCard`, `SwapStrip` (a Nomad swap tile's 4 dp inset). Their marks go through `ModularCompose` and
`ToneDocument`'s write path like any stroke's: Erasable, Undo and masking fluid apply.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +12 | `heldTick`: while the pen is down on a stroke that builds while held (`DrawingStroke.buildsWhileHeld`, Stipple's Build-up), every 30 ms of quiet the last sample again (`HeldPen`), flushed and shown as an input. Posted only for such a stroke. |
| `DrawingStroke` | +2 | `buildsWhileHeld()`, default false. |

#### Blend stump and combs (2026-10-18): no upstream files touched

Branch `inkwash-stump` (spec `docs/archive/superpowers/specs/2026-10-18-stump-design.md`). Engine (Scribulor's own): `PartDial`
(Smear's `SOFTEN`, `ADDED`), `PartChoice`, `ModularCode`, `ModularStyle` (never rolled), `ModularVariations`,
`ResolvedBrush` (`Carry.soften`, its tines from `tinesOf`, shared with Float), `Carry` (the stump pass, MonoPaint's
`ToneDabs.soften` rule ported to the plane), `ModularStroke.reachCarry` (its reach), `QuickStroke` (its cost),
`ModularWords`. UI: `ToolPresets` (Blend stump, Comb, Wash comb), `WildSample` (a clear-water comb's tile). Upstream's
`ToolStroke` and `ToneDabs` are only compiled into `scripts/test_inkwash.sh`, unchanged, so `StumpChecks` can draw
MonoPaint's own stump beside Scribulor's.

#### Blend (2026-10-16): upstream files touched

Branch `inkwash-blend` (spec `docs/archive/superpowers/specs/2026-10-16-new-modules-design.md`, "Blend in the app"): a Medium
dial, how a mark meets what is there (the medium's own way, White, Behind, Invert). Engine (Scribulor's own):
`PartDial.M_BLEND` (`ADDED`), `PartChoice` (last of every medium, `optionsOf`: Opaque's cover first), `ModularCode`,
`ResolvedBrush.BlendMode` (White and Invert: no settle, gravity or dust; on water its own way), `ModularCompose` (the
three rules from each pixel's mark over white; `Under`, the page under the stroke as its gesture found it), `StrokeTones`
(reads the coverages found too, for White's and Invert's coverage), `ModularStroke` (its `Under`, Behind's held-back
pigment and dust by how blank the page was), `DryingWater` (`lands`: where a settling may land), `QuickStroke` (the
preview over the page under it), `ModularStyle` (`rollBlend`, from the seed), `ModularWords`, `ToolPresets`,
`WildSample` (a White brush's samples on a dark ground). UI (Scribulor's own): `BrushLab` (greyed on Float), `LabLayout`
and `LabSettings` (a choice dial's options as its choice names them; the Nomad's menu button). Its marks go through
`ToneDocument`'s one write path: White and Invert as `coverTones` with no `owns` (the write rule's share for a pixel's
change), Behind with its scaled `owns`; Undo, masking fluid and the eraser apply as to any stroke.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +32 −4 | `baseTones(key, out, coverages)`: `baseTones` with the coverages found beside the tones (a quick stroke's render covers from them). `baseComposite(key, top, out)`: a tile's composite over white of every visible layer up to `top`, the gesture's layer as the gesture found it (its Undo capture), safe from another thread or a band as `baseTones` is. Generic: any tool that reads what is already on the page (a blend, a fill by colour) can read it this way. No change to the write rule. |

#### The smudge tool and the finger tool (2026-10-18): upstream files touched

Branch `inkwash-fingertool` (spec `docs/archive/superpowers/specs/2026-10-15-tools-page-design.md`, "The smudge tool and the
finger tool"): a smudge tool tile under the eraser's (its own brush, sizes only, a chooser of smudge brushes), and a
menu choice for what one finger does on the page. Scribulor's own: `ToolSlots` (`ERASER`, `SMUDGE`, `TOOLS`; the smudge
tool's brush, `smudges`, `smudgeChoices`, `useSmudge`), `InkWash` (`SMUDGE`, `stroke(document, tool)`, `finger()`),
`FingerTool` (new, pure: the choices and when a finger may draw), `DrawLayout` (eight tool tiles, their least 36 and the
faces' 35 dp; `Tap.SMUDGES`; the chooser's sizes), `InkWashBar` (the tile, the chooser), `ToolsPage` (`card(brush,
lineRoom)` shared with the chooser). Engine untouched.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +103 −86 | `fingerErase` becomes `fingerStroke`: one finger draws with `InkWash.finger()`'s tool, the eraser as before (sized by the fingertip), or a slot's or the smudge tool's brush at its size and grey (pressure a medium press, no tilt), lifted through `liftQuick` like the pen's. Its decisions (refused at landing, taken back, starts after 8 dp) are `FingerTool`'s; the palm thresholds moved there unchanged (90, 115 dp). A brush's stroke taken back (a second finger, the pen landing, a palm) is finished then undone (`finishedChanged`; the eraser's is still dropped). The pen's Scribulor pen-down moved into `beginInkStroke(tool, pointer, kind)`, shared by the finger. |
| `PaintActivity` | +25 −5 | The file menu's last row "Finger: …" opens the choices (`fingerMenu`), the chosen one ink-filled (`menu`'s new `chosen`). |

#### Flick and Fling (2026-10-19): no upstream files touched

Branch `inkwash-flick` (spec `docs/archive/superpowers/specs/2026-10-19-mischief-modules-design.md`, Stage 2;
`mischief-lab.html`'s section 2). Engine only (Scribulor's own, pure): `PartDial.FLICK` (last of every Tip choice) and
`FLING` (last of every medium with Blend), both `ModularCode.ADDED`; `FlickFling` (new: the pen's motion watched for a
moving lift or a hard stop; a flick's samples past the lift; a fling's seeded drops, laid as the samples' clock reaches
them); `StrokePoints` (`flickFrom`, `flickLen`, `flickWidth`, `flickDry`: what the stampers narrow and dry);
`LineStamper`, `Placement`, `BodyStamper`, `HakeRibbon` (a flick's taper, and the line's dry streaks); `ModularStroke`
(`flickFling`, `land`, `layDrop`; the flick's samples go through `add` at `lastSamples`, so every drying, quick render and
lift lays them; the drops go in the line, else the body, and again after a Slow lift's re-lay; `buildsWhileHeld`, so the
pad's `HeldPen` tells a resting pen's time); `ResolvedBrush` (`flick`, `fling`); `ModularStyle` (`rollFlick`,
`rollFling`, `flickRolls`, `flingRolls`); `ModularVariations` (an off dial stays off); `LabLayout.shows` (the Nomad's
Medium card shows Fling's row where it fits after Print Shading's); `ModularWords`. No UI or upstream file: `DrawingPad`
already ticks `HeldPen` for any stroke that `buildsWhileHeld`.

#### Oil paint: Paint Load and the Filbert (2026-10-19): no upstream files touched

Branch `inkwash-oil` (spec `docs/archive/superpowers/specs/2026-10-19-oil-paint-design.md`): upstream `fe6786d`'s oil paint mode
(in the tree, MonoPaint's own tools; its `PaintLoad`, `PaintRub`, `BrushStamp.crescent` and `ToolSettings.loadedBy` left
as they are) ported into the six-part engine. Engine (Scribulor's own, pure): `PartDial.PLOAD`, `PMIN` (Drag's and
Smear's last, `ModularCode.ADDED`), `PartChoice.FILBERT` (Tip 9, `ModularFeatures.seedOnly`), `BrushPart.keeps`,
`LoadedPaint` (new: a load's length, a stroke's `Run` spending it into `StrokePoints.own`, the rub's arithmetic),
`StrokePoints` (`own`), `ResolvedBrush` (`loaded`, `Pen.filbert`, `Body.filbert`, `Body.crescent`), the stampers
(`BodyStamper`'s filbert oval, crescent bite and spacing; each stamper's darkness times the paint left), `ModularStroke`
(the stroke's `Run`, the holder's load kept at finish), `QuickStroke` (its preview runs out too), `HeldPaint` (each
holder's load), `ModularStyle` (`rollFilbert`, `rollPaintLoad`, `oilRolls`), `ModularVariations`, `ModularWords`,
`BrushNames`, `ToolPresets` (Oil paint). UI (Scribulor's own): `SwatchFace.rubs`, `SwatchFaces.Host.rubbed`, `InkWash`
(`rub`, `paintLoad`), `SlotTile` (the gauge), `InkWashBar` (`loadChanged`, the faces' rub), `BrushLabActivity` (its
faces' rub). `DrawingPad` already calls `InkWashBar.heldChanged` after each stroke.

#### Wet smudges: Keeps Paint and Clear Water (2026-10-19): upstream files touched

Branch `inkwash-wetsmudge` (spec `docs/archive/superpowers/specs/2026-10-19-wet-smudge-design.md`): upstream `8743286`'s wet
brush pen, "Hold ink between strokes" on and off, ported into the six-part engine. Engine (Scribulor's own, pure):
`PartDial.KEEPS`, `PartChoice.CLEAR_WATER`, `ModularBrush.ownPaint`, `ResolvedBrush.Carry` (`keeps`, `water()`,
`lifts()`, `keepsPaint()`), `Carry` (held paint at first touch, `kept()`, Clear Water's wet marks), `CarryPlane`
(`water`, `wetTile`, `foundTile`, `rewet`), `HeldPaint` (new: the paint each holder's brush keeps), `ClearWater` (new:
the run-out at lift and the Float twin), `ModularStroke` (`heldBy`, `stillWet`, `runOut`), `WatercolorSession`
(`held`, `holding`, `stillWet`, `onWater`), `DryingWater` (`box`, `layer`), `ModularStyle.rollClearWater`,
`ModularWords`, `ToolPresets` (Dirty brush, Water brush). UI (Scribulor's own): `ToolSlots` (`SmudgeKind` WATER and
DIRTY, `keepsPaint`), `InkWash` (`stroke(document, tool, finger)`, `holder`, `smudgeDirty`, `cleanSmudge`),
`InkWashBar` (the smudge icon's choice and its dirty head, Clean Brush in the chooser), three drawables. The wet model
is `DryingWater`'s and `GravityFlow`'s, unchanged.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +4 −1 | A finger's stroke is the finger's (`InkWash.stroke(document, tool, finger)`, so its brush keeps paint of its own); after each stroke the bar's smudge icon follows whether its brush holds paint (`InkWashBar.heldChanged`). |

#### Resist (2026-10-18): upstream files touched

Branch `inkwash-resist` (spec `docs/archive/superpowers/specs/2026-10-16-new-modules-design.md`, "Resist in the app"): a Medium
choice, masking fluid or wax, laid on MonoPaint's own masking fluid (`ToneDocument`'s mask layer, its shield in every
write, `maskHatch`, Undo, `clearAllLayers` peeling it), not a parallel plane. Engine (Scribulor's own): `PartChoice.RESIST`
(Medium 5, Erasable only), `ResolvedBrush.resist` and `asInk` (laid as a crisp ink line, no smudge, never on water),
`ModularCompose` (the mark's darkness onto the mask, the more of what is there and it; wax's share only on the paper's
high points; wax's sheen, `WAX_SHEEN`, on the ink layer), `ModularStroke` (`fluid`, `beginFluid` for its gesture; wax's
sheen first, then the resist in a linked gesture, `layWax`; a render of one composes on the page at finish),
`QuickStroke` (masking fluid previews on the mask, hatched), `EraseBothStroke` (lifts fluid first: where fluid can lift,
that pass lifts it at lift, a linked mask gesture, and leaves the paint; previews again once no fluid is left),
`CarryPlane` (what the fluid shields stays put, as a kept share, for the trade and the stump alike), `WatercolorSession` (a bath prints before a resist on
Float), `ModularStyle` (`rollBlend`: Resist from the seed, Watercolor 1/60, fluid 7 in 10), `ModularFeatures.seedOnly`,
`ModularSizes.inks`, `ModularWords`, `ToolPresets` (Masking fluid, Wax resist), `WildSample` (a resist's samples washed
over), `InkWashLayers.resized` (Brush Lab's test page keeps the fluid). UI (Scribulor's own): `BrushTestPad` (draws the hatch through `DrawingPad.drawMask`). Shielding needed no new code:
settling, gravity, Float's print, Blend and a smudge's lay-down already write through the shield.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +23 −4 | `beginFluid(erasable)`: a gesture on the masking fluid whatever `paintMaskingFluid` says (a resist laid as any stroke is); the fluid may now keep a permanent share (`begin`'s `permanent` no longer forced to 0 for it; MonoPaint's own fluid tools begin at 1, so nothing changes for them), which is wax: the eraser's block write on the mask already leaves a layer's kept part. `maskHatch` hatches only the part that lifts (`liftable`: the fluid's darkness less its kept part), so wax shows only by what it keeps out. `maskShield(x, y)`, `maskLiftable(x, y)`: reads for the eraser and a smudge. Generic: a permanent resist is any tool's that wants one. |
| `DocumentCodec` | +25 −8 | TSM5's `MSHR` section: the masking fluid's shares, written only when it keeps some (wax); TSM5 is written for that too. Pages with fluid only write TSM4 as before. A TSM5 reader from before skips `MSHR` (unknown tag) and reads wax as masking fluid; older ones refuse TSM5 as before. |
| `DrawingPad` | +3 −1 | `drawMask(document, display, area)` made static, so Brush Lab's test page draws the hatch the same way; the instance method calls it. |

#### Print shading builds up; the owner's names (2026-10-19): upstream files touched

Branch `inkwash-screen2`, from the owner's first test ("screen tones always seem consistent, not lighter and darker or
cumulative like in the demo"). The flat tones: the threshold read the mark's own darkness over white, so a second stroke
laid the same dots again, never more, and a line at the default black swatch is solid (its coverage is 1). Now each
layer keeps an optional print tone (`Layer.print`): what its screened marks would show unscreened. A screened stroke
laid its medium's own way (`ResolvedBrush.prints`; not White, Invert or Tone) builds on it as transparent paint darkens
(`PrintScreen.printed`), its screen inks that, and the compose writes it back through the `Page` (`strokeBasePrints`,
`setPrints`; `StrokeTones` keeps it for a quick render). Two prints on one layer: each stroke inks only its own new dots
from the tone both built; the first's stay. Engine (Scribulor's own): `PrintScreen` (`printed`, `LABEL`, `ROW_LABEL`),
`ModularCompose`, `StrokeTones`, `ModularStroke.begin` (`printing`), `QuickStroke` (the preview builds on it, and a
transparent body previews at `SCREENED_BODY` of its glaze), `InkWashLayers.cropped`. Words and UI (Scribulor's own): the
owner's names, Print shading, engraving, Screentone, Darkness (`PartDial`, `PartChoice`, `ModularWords`: display only);
Control's darkness words for a dotted brush (`ModularWords.handWords`, `darkHead`, `HandGrid`); a choice dial's menu in
a panel beside its button (`LabSettings.Beside`, `BrushLabActivity.showMenu`, `besideColumn` shared with the swap
panel); `LabText` breaks a word at a soft hyphen (`SwapStrip`'s Screen-/tone on the Nomad). The drawing screen's side
bar files are untouched.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +157 −40 | `Layer.print`: per layer, a byte a pixel, the print tone (255 none; null until a screened gesture calls `printing()`), carried as `Layer.share` is: `copy`, copy-on-write, `expand`, Undo (`beforePrint`, `TileEdit.printBefore/After`; a tile captured before the layer had one restores to 255), `cancel`, `restoreSpan`, `clear`. `setPrints` (Undo capture, the masking fluid's shield), `strokeBasePrints`, `basePrints` (shared, as `baseTones`), `printTone`. `eraseTones`/`eraseTone` lower it with the coverage they take (a pixel with print tone and no paint is erased too). `copyShare` is `copyPlane`, `sameShare` `samePlane`. A gesture without a screen never touches it: pages and strokes without one write exactly as before. Generic: any hidden per-layer tone a tool builds on. |
| `DocumentCodec` | +42 −16 | TSM5's `PRNT` section: each layer's flag and print tone, written only when some pixel holds some (an all-255 plane, a screened stroke undone, writes nothing), so every other page is byte for byte; `ERAS` and `PRNT` read by one `perLayer`. A reader from before skips it and opens the drawing as it shows. |

#### Second Ink's ink share (2026-11-09, branch `second-ink`): upstream files touched

Spec `docs/superpowers/specs/2026-11-09-second-ink-design.md`, plan `docs/superpowers/plans/2026-11-09-second-ink.md`
(Tasks 1 to 4: the engine, the dial, every gesture's declaration and every carrier; the file and the copies don't keep
or show it yet). Beside the
share and the print tone a layer or plane keeps a third optional byte a pixel, `Layer.ink`: how much of the ink there
is second ink (0 none, 255 all; null: none anywhere). Unlike the other two, no stroke writes it as it draws and the
screen never reads it. A gesture says what its paint is after `begin` (`inking(share)`, or `inkFlows()` for paint that
moves by itself), and `finish` sets the shares once (`mixInk`), over the tiles the gesture captured, from the darkness
it added: `(d0·i0 + (d1 − d0)·g) / d1`. A pixel left with no darkness holds none; one left lighter keeps its ink
(erasing), but on the opaque plane a declared ink takes it over by what it lightened. Paint that flows takes the ink
round it (`inkAround`: the 5×5 pixels as found, by darkness, read from a summed table made once a tile, so the cost
a pixel doesn't grow with the area; then outward pass by pass over clear paper). A gesture with no second ink on a
plane without `ink` does no work per pixel, and first ink does none in a tile that held no second ink.

Who declares (Task 4): a brush's stroke, `ResolvedBrush.inks` after `begin` (`ModularStroke.begin`, `WetPaintStroke`):
its Second Ink dial's share, or `inkFlows()` for a smudge that lays none of its own paint (Own paint 0). A shape is
its slot's brush's ink: `StrokeStart.shape` leaves it (`ToneDocument.shapeInk`), `ShapeStroke` begins with
`beginShape`. Copy Strokes' reflected copies declare what their stroke did (`Darkening.ink`, `flows`; `layAgain`).
Paint that moves by itself declares `inkFlows()`: the wet canvas (`WetWatercolor.lay` and `advance`,
`WetCanvas.Drag`), drying (`DryingWater`), the Float bath (`FloatWater`), blooms (`HaloGrowth`), Blend
(`ModularStroke.blendOther`). Tools with no brush (fills, pictures, the erasers, Sharp ink, masking fluid) declare
nothing: first ink. A page with no second ink does none of the ink arithmetic (`layPixel`, `sinkUnder`, the Lasso's
lift, Merge). What moves whole
pixels carries each one's share and sets `inkCarried`: the Lasso (`copyPlanes` returns the ink block, `LassoPlanes.inks`,
`layPixel`'s mix by the darkness laid over the darkness that shows through), `sinkUnder`, the crop
(`InkWashLayers.croppedOne`), Merge (`InkWashLayers.flattened`, each layer's ink by the darkness it shows). Later's
media that grow a mark past its pixels (`finished`) give the paint grown the mean share of what the mark laid on
clear paper (of all of it when it laid none there).

Cost (desktop, a 1404×1872 page, a size-60 wash's whole drying, with against without second ink on the plane): Grey
wash 157 against 125 ms (41 ticks, 192 finishes of about 10 tiles), Wet gouache 76 against 47 ms, Soft wash on Slow
47 against 23 ms (16 finishes of 45 tiles). The finish passes unchanged pixels in a loop of its own (`unchanged`), and
in a tile whose paint is all one ink (`oneInk`) asks the sums only for paint new on clear paper.

| File | Why |
|---|---|
| `ToneDocument` | `Layer.ink`, `Layer.inked()`; carried as `Layer.print` is: `copy`, `release`, copy-on-write (`own`, `bind`), the constructor's size check, Undo (`capture`, `beforeInk`, `foundInks`, `TileEdit.inkBefore/After`, `restoreTile`; a tile captured before the layer had one restores to 0), the continuation's merge, `cancel`, `restoreSpan`, `clear`, Later's media and Fading (`finished`'s joined tiles, `stepFade`'s `patch`), the history's byte counts. `inking`, `inkFlows`, `inkFound`, `inkAround`/`around`, `mixInk` (the array made at the first share that isn't 0, after `writable()`, so no snapshot sees it). Live pages' Undo (`takeBack`, `takeBackClear`) puts each pixel's ink back itself and tells `finish` to leave it (`inkCarried`). A larger page (`expanded`) and Keep Reflection (`reflected`) move each pixel's ink with it, as its print. `onWhite` is open to the package's checks. Task 4: `copyPlanes` gives each pixel's ink too, `layPixel` takes one (`liftPixel` none), `sinkUnder` keeps the sunk paint's, `finished` gives a Later mark's grown paint its share; `mixInk`'s `unchanged` and `oneInk`. Not yet: the file (Task 5), copies (Task 6); a merge replayed from the stroke log (`StrokeLog.Merge`) keeps no ink. |
| `WetWatercolor` | `lay` and `advance`, where they open a gesture of their own, declare `inkFlows()` after `begin` (two lines, marked `// Inkwash:`). |
| `ShapeStroke` | Its two `begin()` are `beginShape()` (the shape's ink with the gesture). |

Checks: `SecondInkChecks`. Generic: a per-pixel property set from a gesture's before and after at its finish, at no
cost to the stroke's own writes.

#### Screen and Tone (2026-10-19): no upstream files touched

Branch `inkwash-screen` (spec `docs/archive/superpowers/specs/2026-10-19-mischief-modules-design.md`, "Stage 1"): a Medium dial,
Screen (none, halftone, line screen, dither, grain, with Cell, Angle, Misregister, Ink gain and Uneven), and a Medium
choice, Tone (a screentone: dots, lines, sand or a gradient at a Tone over the brush's fill). Engine (Scribulor's own): new
`PrintScreen` (the threshold at each page pixel, mischief-lab.html's `buildScreen` and `bit1`, the second pass's Ink
gain and Uneven worked out from the neighbours' thresholds; a Tone dot is tone 1 at coverage 254, black on any paper,
which no other brush lays); `PartDial` (`SCREEN`, `CELL`, `S_ANGLE`, `MIS`, `S_GAIN`, `UNEVEN`, `PATTERN`, `TTONE`, all
`ADDED`), `PartChoice` (Screen's six last of every medium with Blend; `TONE`, Medium 6), `ModularCode`, `ResolvedBrush`
(`screen`; Tone laid as Resist is, `asInk`; a screened mark doesn't settle, run, drip, shed dust or grow mold),
`ModularCompose` (the mark's darkness over white through the screen as each pixel is written: ink, or as the gesture
found it; Misregister composes each line and body buffer with its faint second pass at the offset, `ghost`; nothing new
runs without a screen but one null test a pixel), `QuickStroke` (the preview is the screen's own dots), `EraseBothStroke`
(Tone's dots scrape in streaks along the stroke; anything else erases as ever), `ModularStyle` (`rollTone`, `rollScreen`,
from the seed after every part), `ModularFeatures.seedOnly`, `ModularVariations` (never nudges Screen's dials or a Tone),
`ModularSizes` (Tone on the ink layer, 40 px in every style), `ModularWords`, `LiftingStroke.screened` (the bench's
fast panel path, `StrokePanel`'s "screen"). UI (Scribulor's own): `BrushLab` (Screen's own dials only with a screen, Cell
and Angle greyed where its print has none), `LabLayout` (`shows`: the Nomad's open card has no room for Screen's own
dials; `stacks`; five options are a menu button anywhere), `LabSettings`. Its marks go through `ToneDocument`'s one write
path: Undo, masking fluid, Erasable and a shaded page apply as to any stroke.

#### Riso, photocopy and soft dither (2026-10-31, branch `explore-screen`): no upstream files touched

Three Print Shading choices appended after Hatching (codes 15 to 17; an older version reads them as None, "Newer
version"). Engine (Scribulor's own): `PrintScreen` (`Kind.RISO`, `PHOTOCOPY`, `BLUE_NOISE` after Tone's, so no Kind moves,
`print` maps the option; `Stochastic`'s tiles, a 64-px void-and-cluster blue noise and two 128-px clumpy grains, each made
on its first brush's resolve; riso's and the photocopy's thresholds kept as floats, a threshold over 1 never inks),
`PartDial.SCREEN`'s options, `ModularWords`, `BrushLab` (no Angle for the three). Every older print's thresholds are as
before (`ScreenChecks.oldKinds`, by CRC). Brush Studio's web lab doesn't have them yet: `code-spec-check.cjs` lets their
test codes wait, as Later's do. Pictures: `scripts/screen_pictures.sh`.

#### Pixels and stitches (2026-11-06, branch `pixels`): no upstream files touched

Two Print Shading choices after Rings (codes 24 and 25, still one base-36 digit; the version before, e8fdf657, reads them
as None, "Newer version", `test_inkwash.sh`). Unlike every other screen they lay greys, not black dots: a grey a cell of a
page-fixed grid (`PrintScreen.cells`, `Kind.PIXELS`, `STITCHES`; Pattern Size the cell, turned Angle − 45 so square at the
default). `ModularCompose.composeCells` composes a rectangle grown to whole cells from the line and body read at each
cell's centre (`PrintScreen.centre`; the strongest within 2 px, if the centre holds half of it), a pixel whose cell reads as last composed skipped, so a cell
is laid whole or not at all; `PrintScreen.dark` gives its darkness: one of 8 greys spaced evenly in the panel's dots
(`DotPattern.pickerTone`), or one cross-stitch in it (threads shared with Later's Cross-Stitch: `LaterMedia.threadCover`,
`threadShade`, `threadUnder`, extracted, its bytes unchanged). A fill in strips composes `ModularCompose.cellReach` short of
what is laid and frees that much later (`ModularStroke`). Never lightens (shortcut: no light-over-dark), never kept sharp
(`SharpStrokes.drawn`), Dot Swell and Patchy Ink grey in Brush Lab. Tools Pixel pen and Stitch pen (`PresetPictures.texture`).
Checks: `ScreenChecks.cells`. The web lab waits for them as for riso's.

#### Maze, brick, basketweave, waves, honeycomb and rings (2026-11-06, branch `new-screens`): no upstream files touched

Six Print Shading choices appended after Soft Dither (codes 18 to 23, still one base-36 digit; an older version reads
them as None, "Newer version", checked against 47c1624d in `test_inkwash.sh`). Engine (Scribulor's own): `PrintScreen`
(`Kind.MAZE` to `RINGS` after `BLUE_NOISE`, so no Kind moves and `print` maps them as it does riso's; each a raw field,
twice the distance to its lines in half-periods, continuous across its tiles, evened like stipple's (`raw` families 4 to
9, `even`), so a line thickens with the grey and coverage is the grey; thresholds kept as floats), `PartDial.SCREEN`'s
options, `ModularWords`, `BrushLab` (they turn with Angle, unlike riso's three). Checks: `ScreenChecks.lined`. The web
lab waits for them as for riso's (`code-spec-check.cjs`); Brush Studio sets them through the app's model.

#### Halo, shown as Mold (2026-10-18): upstream files touched

Branch `inkwash-halo` (spec `docs/archive/superpowers/specs/2026-10-16-new-modules-design.md`, "Halo in the app"): a Drying
choice whose veins grow from a wet stroke to nearby marks, until another brush sets them. Engine (Scribulor's own): new
`HaloGrowth` (the lab's Halo: `Lift`, `Read`, build, step, apply); `PartChoice.HALO`, `PartDial.H_REACH` and `GROWTH`;
`ResolvedBrush` (`asSlow`, `Halo`, the owner's limits); `ModularStroke.finish` (hands the lift to the session, a sample's
grows at once); `WatercolorSession` (the halos beside the Slow wash and the bath: lifts queued, built and stepped on the
`SettleWorker`, set by anything else, joined by a Halo stroke on their layer); `ModularStyle.rollHalo`,
`ModularFeatures.seedOnly`, `ModularWords`, `ToolPresets` (Mold ink). UI untouched: the drying ticker, the wet indicator
and Brush Lab's test page already drive a session.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +16 −4 | `foundTiles()`: the gesture's Undo captures by tile key (references; never changed afterwards), so a stroke's page before it can be read after it's finished. `foundComposite(key, layer, tile, out)`: a tile's composite of every visible layer, one layer read from such a capture (or as it is); `baseComposite` now shares its loop. Generic: any tool that grows from a stroke after it (Halo) or compares a page before and after one. |

#### Fridge magnets (2026-10-18): upstream files touched

Branch `inkwash-magnets` (the owner's books-and-magnets mockup, screen 3; reference: the owner's Paintmageddon,
`magnetic-poetry.html`, `WordBin.tsx`, `WordLayer.tsx`, `renderWordsToCanvas.ts`): word tiles lying over the page,
never in a layer. Pure (Scribulor's own, `test_inkwash.sh`): new `Magnets` (the page's magnets, immutable; the tile's
geometry in page pixels: 5 mm iA Writer Duo 700 words, 7.3 mm tiles, a 2 dp border and a solid 2 dp offset shadow, a
tilt of ±2.5°, a quarter turn from the app's turn; `stamp`, the one way a picture of the page gets them; the WORD
section's bytes), `MagnetDrag` (a magnet under the pen: follow, lift, take off over the bin or off the page; rectangle
parts), `WordPacks` (the bundled kit, `assets/words/default.txt`, and `*.words.txt` packs beside the drawings, each
shuffled once a session), `WordBinLayout` (the bin's rows and pages). Android (Scribulor's own): `MagnetTiles` (a tile's
picture, antialiased and tilted once, kept), `WordBin` (the bin's view), `WordsMode` (the bin, the pen on magnets, the
drag's overlay, the canvas's hooks). `DrawLayout` counts one more icon (and rule) for the bar's Words button where the
column holds it at its least sizes with four layers (not the Nomad in landscape: there it is the menu's);
`InkWashBar` adds the button before the stretch (`words`, `wordsShown`; a tool chosen closes Words); `AutoExport` stamps
the PNG copy. Undo: a third kind of `ToneDocument` step (magnets before and after, by reference), so placing, moving
and taking off are each one step in the page's one history; its redraw is kept apart from the paint's dirty box
(`takeMagnetAreas`), so a magnet moved across the page redraws two tile-sized patches, not the page between. A drag
redraws only the tile's old and new patches: the raster holds the page without it and the tile is a `RasterOverlay`
over it, presented through the canvas's gray session like the eraser's ring; one full clean refresh (`autoRefresh`) at
lift. The canvas never presents under the bin (a non-focusable `PopupWindow` Android draws over it): `flush` and
`autoRefresh` skip what it covers. For the notebooks' PDF: give `Magnets.stamp` the page's ARGB pixels and a
`WordsMode.exportTiles()`.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +39 −1 | `magnets()`, `setMagnets` (one Undo step; no-op when equal), `placeMagnets` (from a file, no step), `takeMagnetAreas`; `Edit(Magnets, Magnets)` and its branch in `travel`; `Snapshot.magnets`; `ToneDocument(Snapshot)`. No gesture, layer or composite reads them. Generic: any per-page overlay kept by reference in the history. |
| `DocumentCodec` | +24 −6 | TSM5's `WORD` section (`Magnets.encode`: a version byte, a count, each word, centre, tilt and turn), written only when a page has magnets; TSM5 is written for that alone too. A TSM5 reader from before skips it (unknown tag) and opens the drawing without them (`MagnetOlderReaderCheck`, against `f34fed5`); a later version's section is skipped by this one; damage refused. Pages without magnets write TSM3/TSM4/TSM5 byte for byte as before. |
| `DrawingBook` | +1 −1 | `Snapshot.page` of the shown page carries its magnets (`new ToneDocument(activePage)`); other pages carry them in their bytes. |
| `DrawingPad` | +73 −26 | `words` (a `WordsMode` and its `Host`, lending the raster, `pagePoint`, `queue`, `render`, `flush`, `autoRefresh`); `render` draws the magnets last, `renderAll` all of them; `renderDirty` also renders `takeMagnetAreas` and takes the held magnet's overlay off and back like the ring's; with Words open the pen goes to `words.touch` (after `historyTap`, so two- and three-finger taps still undo and redo) and the eraser ring hides; `suspend` drops a drag; `flush` and `autoRefresh` present only outside `covered()` (the bin's panel area mapped like `refreshFastInk`). |
| `PaintActivity` | +8 −1 | `dismissPanels` and `requestQuarter` close Words; the file menu gets a Words row where the bar has no Words button. |
| `PngExport` | +6 −1 | The full-size export stamps the magnets (the dot-size export doesn't). |

Not done: `expand` (MonoPaint's canvas growth, not reachable in Scribulor) doesn't move magnets; MonoPaint's `clear` and
`clearAllLayers` leave them; page thumbnails (`LayerThumbnail`) show the paint only.

**2026-10-19, magnets that move (branch `inkwash-magnetfix`).** Only the open bin gave the pen to the magnets, and the
bin's own window took a stroke landing on a word and read only its lift: so on the tablet no magnet ever moved.
`MagnetDrag.landing` now decides who a landing belongs to (the pen on a magnet always picks it up; with the bin closed
everything else draws); `WordBin` hands a pen stroke off a tile to `WordsMode.carry`. A moving magnet stays put and
only its outline follows (`MagnetDrag.outline`, four 3 px bands), through the fast black-and-white preview where the
panel has one; at lift the area it crossed gets one clean refresh. Tiles are Paintmageddon's: Roboto Mono Bold
(`res/font/roboto_mono_bold.ttf`, `Theme.magnetFace`, the magnets' only), 2.7 mm words on 4.7 mm tiles, a fine grey
edge, a 2 px ink shadow, 2 px corners; the bin's four rows hold up to 48. Files store no size, so older pages just get
the new tiles. `DrawingPad` (+25 −8): `words.touch(event, mayPick)` on every touch (`mayPick`: no other pen, finger,
eyedropper, gradient, fill or shape gesture), the host's `fastPreview` (`beginFastPreview`/`endFastPreview`) and
`refresh(reason, area)` (`refreshArea`, a forced refresh of that area alone outside `covered()`), and `autoRefresh`
waits while a magnet moves; the host's `redraw` is gone.

**2026-10-19, speed and the stump (branch `inkwash-speed2`).** A Blend stump on the ink also blends the pair's wash
(the stump spec, "Own layer only"); paints start the lattice at 50 px (`ModularStroke.latticeFrom`); quick strokes
write their true tones behind the pen while drawing (`QuickStroke.trail`, `StrokeTones.writeTiles`: engine-only).

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +20 | `layerPixels(index, …)`, `layerKept(index, …)`: `strokeBasePixels` and `strokeBaseKept` of a layer no gesture is changing, as it is (a stump's other half). Reads only; no change to the write rule. Generic: any tool that reads a second layer while drawing on one. |

#### Text boxes and Make Ink (2026-11-01, branch `explore-text`): upstream files touched

Typed words over the page, the magnets' pattern. Pure (`test_inkwash.sh`, `TextBoxChecks`, `TextOlderReaderCheck`): new
`TextBoxes` (a page's boxes, immutable; the layout from iA Writer Duo's own advances, so every copy wraps alike; the
letters from a process-wide `TextBoxes.Type`; `Over`, the letters laid over a composite's rows; `stamp`; `ink`, Make
Ink's room; the TEXT section). Android (Scribulor's own): `TextType` (the letters, `Theme.textFace`), `TextMode` (Play's
Text row, the pen on boxes, the typing popup, the long press menu). Make Ink is a fill whose room carries coverage
(`FillRegion.Room.cover`), laid by `ModularStroke.print` through `FillTool.print`: the brush as a firm still press over
the letters, its medium, paper, Print Shading, grey, drying and Later applied, its marks not kept to the letters (a
wash's Bleed runs past them). It is a fill rather than a Stamp tip's print because a Stamp lays its nib as the line
only, with its own blotchy inking, while a fill lays line and body both, so a wash is a wash; the box comes off in a
structural step and the stroke's step is linked to it (`linkSince`), one Undo.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +28 −7 | `texts()`, `placeTexts`, `setTexts` (a structural step, as `setMirrors`: the whole page redrawn); `Snapshot.texts`, `StackState.texts`; Clear page and `unmarked` count them. Generic: any per-page overlay kept by reference. |
| `DocumentCodec` | +21 −6 | TSM5's `TEXT` section, written only when a page has boxes; an older reader skips it (`TextOlderReaderCheck`, against `9e447836`); a later version's is skipped here; damage refused. Pages without boxes write as before, byte for byte. |
| `ModularStroke` | +20 −7 | `print(room)`: `fill` with each lattice point's press times its coverage (`laid`), the room not kept (`keepToRoom` skips without one). Fills lay exactly as before. |
| `FillRegion` | +6 −1 | `Room.cover` and `share(o)`: a pixel's share of the press (null: all of it). |
| `FillTool` | +12 | `print`: Make Ink's stroke on the brush's own layer. |
| `PagePicture` | +6 −4 | `Composite` of a whole page lays its boxes' letters over its rows and marks (not a layer's composite), so PDF, PNG via `NotebookExport.greys`, PSD's merged image and page pictures show them. |
| `PngExport` | +2 −1 | Stamps the boxes before the magnets. |
| `DrawingPad` | +39 −5 | `text` (a `TextMode` on the magnets' host): drawn in `render` before the magnets, its touches after `historyTap`, its outline off and on with the magnets', `suspend` and `autoRefresh` wait for it; `makeInk`. |
| `PaintActivity` | +14 −7 | `TextBoxes.type` set at start; Play's Text row; `menuAt` (a menu beside a page rectangle); Text closed where Words is. |
| `InkWash`, `InkWashBar`, `DrawLayout`, `Theme`, `PicturePlacer` | small | `InkWash.ink`; Play marked while Text is on; `Theme.textFace`; `PicturePlacer.ring` shared. |

Not done: a Text layer in the PSD copy, the dot-size PNG copy,
live (shared) pages (not on this branch: a box travels only in the page file), more fonts, italics, rotation.

#### Palm taps (2026-11-03, branch `palm-taps`): upstream files touched

A hand resting as the pen draws, landing and lifting quickly, passed for a two- or three-finger tap (Undo, Redo).
`MultiFingerTap` is untouched; the tap it reports is ignored while the pen is down or near and for
`FingerTool.PEN_PALM_MS` (0.3 s; 1 s felt too long, the owner 2026-11-03) after it was last seen (`FingerTool.tapCounts`, pure, `FingerToolChecks`). No contact
size rule: pen proximity only.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +9 −7 | `penGuardUntil` becomes `penSeen` (when the pen was last seen: hovering, and now also every touch event with the pen among the pointers, so a lift counts); the 250 ms finger guard reads `penSeen + 250`, unchanged; `historyTap` asks `FingerTool.tapCounts` before Undo or Redo. |

### (c) Scribulor engine: pure Java, UI-free

Checked on the desktop with plain `javac` (`scripts/test_inkwash.sh`), which
also proves they need no Android.

| Group | Classes |
|---|---|
| Six-part brush model | `BrushPart PartDial PartChoice ModularBrush ModularStyle ModularLocks ModularFeatures ModularVariations ModularWords ModularSizes` |
| Share codes | `ModularCode` (M codes, the open format in `docs/BRUSH-CODE.md`: `newer` tells a newer version's code, read with stand-ins); `BrushCode`, `WildBrush`, `BrushSetting`, `BrushStyle` read older I/P codes, only to convert them |
| Resolve and stroke | `ResolvedBrush ModularStroke StrokePoints StrokeMath StrokePaper`; `QuickStroke` (a stroke too heavy to draw live: a flat preview, then the true stroke rendered from its samples); `FlickFling` (Flick's samples past a moving lift, Fling's drops on a hard stop, both in the stroke); `SteadyHand` (Steady Hand and Long Stick: the pen's samples through a tip on a spring, in front of the stroke) |
| Smudge | `Carry` (a dab-sized reservoir trading paint with the stroke's own layer: Lift, Drag, Smear, Clear Water; Smear's Soften, MonoPaint's blending stump and its combs; Smear's and Clear Water's Press, 2026-10-30, a firm press taking Soften or Lay Down Paint and the wet toward 0), `CarryPlane` (that layer as the stroke found it, premultiplied, as smudged so far), `HeldPaint` (the paint a brush with Keeps Paint holds between strokes, and a Paint Load brush's load, per holder), `ClearWater` (Clear Water's run-out at lift, a wash's wet again with Slow drying; its Float twin), `LoadedPaint` (Paint Load: MonoPaint's oil paint mode, a brush's own paint running out along the stroke; a rub's load) |
| Stampers and buffers | `FloatTiles LineStamper Placement BodyStamper HakeRibbon BristleSwarm StippleStamper HatchStamper StickGrain PaperGrain PaperNoise` (`FloatTiles` also holds a big stroke's half-resolution lattice and its bilinear stretch; `BristleSwarm` is the bristle tip's hairs, the web lab's swarm; `StippleStamper` and `HatchStamper` the Stipple and Hatch tips' dots and lines, newmods.js's) |
| Compose and drying | `ModularCompose PrintScreen BodySettle DryingWater WatercolorSession SettlePacer SettleWorker HaloGrowth` (`PrintScreen` is Screen's and Tone's threshold at each page pixel, which the compose writes through; `HaloGrowth` is Halo's veins, shown as Mold, the web lab's newmods.js Halo on 32 px tiles, read from the page apart from its build; `ModularCompose` also holds Blend's rules and `Under`, the page under a stroke as found; `SettlePacer` paces settling after lift and Fast-wet's re-settles, section 5; `SettleWorker` is the thread wet paint works on after lift) |
| Float | `FloatBath` (suminagashi: floating paint as outlines moved by drops, the pen's pushes and combs, and drift; its log, snapshots for Undo), `FloatScan` (its film at 16 greys), `FloatTrace` (a stroke's own paint traced into rings), `FloatPen` (a stroke on the water), `FloatWater` (the bath on its layer until it prints) |
| Gravity | `GravityFlow` (a wet wash moving with the page's tilt: runs, pools, drips, sinks, blends; the Gravity Lab's gravity.js), `PageTilt` (the page's downhill vector and dead zone, from MonoPaint's `CanvasGravity`) |
| Cores | `Cores`: three helper threads beside the caller's (and three quiet ones at the lowest priority for a background render, `quiet`); compose, stampers, settles, blurs, the lattice stretch and the render share bands of tile rows that share no tile, so the page is the same on one core |
| Timing | `Prof`: named timing sections, off unless the bench turns them on |
| Pen input | `PenBatch`: a stroke's samples gathered from input events, drawn together (Android glue in `DrawingPad`); `HeldPen`: a resting pen's last sample again, for a stroke that builds while held |
| Old brushes, converted | `LegacyBrushes`: I/P Wild brushes and the four old built-ins as six-part brushes, and the size and grey a converted slot keeps. The old engines (`InkStroke`, `WildInkStroke`, `WashStroke`, `WashLook`, `WatercolorSettle`) were removed on 2026-10-15: one engine draws every brush. |
| The eraser | `EraseBothStroke WashCoverage RoundFootprint PenSpeed` (ink and wash of the pair together, one undo step; it leaves what each layer keeps, Erasable; it lifts masking fluid first, Resist) |
| Brush values and storage | `Brush BrushTexture BrushNames BrushCaptions BrushLibrary` |
| Samples (preview images) | `WildSample` (`modular(...)` returns pixels for any size/shape) |
| Scribulor-specific but pure | `InkWashLayers` (ink over wash layer pairs), `InkWashSizes`, `InkWashShades`, `Assets`, `DrawingNames`, `LayerThumbnail` |
| Fridge magnets | `Magnets` (a page's word tiles, their geometry, `stamp`, the WORD section), `MagnetDrag`, `WordPacks`, `WordBinLayout` |
| Text boxes | `TextBoxes` (a page's typed boxes, their layout, the letters over a composite, Make Ink's room, the TEXT section) |
| Fill | `FillRegion` (the area a tap is in, shut in by the ink layer's crisp lines: the Mischief Lab 2 prototype's "Stay in the lines", in 2 px cells, then drawn at page resolution), `FillTool` (the fill's brush, its walls and its stroke, `ModularStroke.fill`; the bar's taps while it is on), `FilledShape` (Filled Shape: an outline closed at the lift and the area it encloses, non-zero winding, filled through `FillTool.start`) |
| Pictures in | `PictureImport` (Import Image and Import PDF, 2026-10-22: a picture's greys, laid as ink in one Undo step; the placing frame; a PDF's notebook of locked pages under clear layers, its cap and its file) |
| Mirrors | `Mirrors` (a page's mirrors: the group they make, the snapping, the cap, Show Guides, Real Mirror's `View`, the MIRR section), `MirrorStroke` (a stroke and its copies, laid as its change reflected or drawn again, one Undo step; Real Mirror's stroke folded into the kept side), `MirrorShow` (what the canvas shows: guides, the reflection, live copies, a placing stroke, an effect brush's trail) |

Reference implementation: the web prototype
`docs/brush-lab/prototypes/modular-lab.html` (sources in `modular-lab-src/`);
the Java port matches it bit for bit except where the engine spec's "As built"
lists a reason.

### (d) Scribulor UI/product layer (Scribulor-only)

- Toolbar: `InkWashBar` (the one side column), `InkWash` (selection, starts
  every stroke), `ToolSlots`, `ToolSwatches`, `SwatchFace(s)`, `SizeModal`,
  `SwapStrip`; `ZenFanView` (Zen Mode's fan, over the page; its geometry the pure `ZenFan`);
  `SwatchBar` (the selected tool's sizes and greys, in the
  column) and `DrawLayout` (pure: the column's order, `COLUMN`, which
  `InkWashBar` builds from, its fitted heights, folding the layers, the tool
  buttons two by two with Fill's cell measured before Fill comes
  (`InkWashBar.fillButton` puts its button there), what a tap on a tool does,
  the smudge chooser's sizes); `FingerTool` (pure: what one finger does, when
  it may draw). Four sizes and four greys since 2026-10-19: `ToolSwatches`
  keeps the older three where an older build reads them and the fourth under
  its own key; `InkWashSizes.Preset` and `InkWashShades.Shade` gained `LARGER`
  and `MID` in their places (saved by name). `WildSample.legibleTray` draws a
  thin, sparse tray stroke darker on the bar only.
- Tools page (tapping the selected slot again): `ToolsActivity`, `ToolsPage`
  (pure: the filter row, paging, picks, the look a tool is tried at; no Undo since 2026-11-03),
  `ToolsLayout` (pure: every size), `ToolPresets` (pure: 65 presets as M
  codes, five per style).
- Brush Lab: `BrushLabActivity`, `BrushLab` (pure model), `LabSettings`,
  `LabLibrary`, `LabLayout`, `LabText`, `LabPages`, `LabUi`, `LabButton`,
  `PartCard`, `HandGrid`, `BandSlider`, `SquareSwitch`, `LoopCard`,
  `BrushTestPad`, `SampleQueue`; `TestArea` (the try strip, `strip`: its head, Clear,
  Brush Lab's wet drop, the test page in its frame; the brush's loop beside its name: Brush Lab's and the Tools page's); `LabUi.PageBar` (both pages' bar: Back, the title over its job; the notebook screens' too since 2026-11-03, the shelf's without Back).
- Android glue: `StrokePanel` (a pen stroke's session on the panel's fastest black/white update, `Device.fastStrokeMode`; the bench only since 2026-10-16),
  `QuickLift` (after a quick stroke's lift, looks in on its render thread every 5 ms, then finishes
  it), `DryingTicker`, `PenSample`, `RasterOverlay`, `DisplayRecovery` (pure: the canvas's fast display through a busy
  or failing panel, reconnecting by itself), `RecoverySchedule` (pure: when the recovery save runs while drawing), `AutoExport`,
  `StrokeTiming` (debug log), `TiltFeed` (a session's page tilt from MonoPaint's
  `CanvasGravity`, listening only while something wet could move).
- The Mirrors button (the Fun button until 2026-10-23): `MirrorsMenu` (once `FunMenu`, the Mirrors list, `DrawLayout.MirrorRow`;
  what the pen's next stroke places, `DrawingPad.placing`).
- Fridge magnets: `WordsMode` (the Words bin and the pen on magnets, the drag's overlay; hooks in `DrawingPad`),
  `WordBin` (the bin's view: WORDS, the pack and page, More words, Done, a page of tiles), `MagnetTiles` (tile pictures).
- Pictures in (2026-10-22): `PicturePlacer` (Import Image's placing over the page: the outline and handle while it
  moves, the picture in dots at lift, Cancel and Place along the page's foot; hooks in `DrawingPad` beside the
  magnets', the same `WordsMode.Host`), `PdfImport` (Import PDF: `PdfRenderer` a page at a time into a cache file, then
  `DocumentStore.create` writes the notebook whole; `PdfImport.page`, one page as a picture, for This Page and the
  previews), `ImportPicker` (2026-10-26: the imports' own file list, with Cancel, in place of Android's picker, which
  has none; 2026-10-29: Import's one picker, pictures and PDFs a folder at a time with crumbs, as wide as the screen,
  then a PDF's New Notebook or This Page).
- `WetIndicator`: the droplet at the foot of the toolbar (and beside Brush Lab's
  Clear) while a Slow wash is still settling or a quick stroke still renders; a tap lays it now.
- Styling: `Theme` (the owner's Scribbler Style, `docs/DESIGN.md`: six greys,
  strokes, the typeface `face`, `restyle`/`dialog` for MonoPaint dialogs Scribulor
  shows), `TypeStyle` (the type scale, pure), `SlotTile` (the slot tile Brush Lab
  draws, and the bar's tray rows); Phosphor icons (`res/drawable/ic_ph_*`, `ic_part_*`; from 2026-10-26
  the owner's own drawings traced with potrace; since 2026-10-29 Iconoir's, MIT, or drawn for Scribulor in its style,
  same names, each file naming its source in a comment, `THIRD_PARTY_NOTICES.md`),
  launcher icon, iA Writer Duo in `res/font/` (SIL OFL 1.1, the license in
  `assets/fonts/OFL.txt`). The font is also the app theme's (`res/values/styles.xml`,
  `android:fontFamily`), which is how MonoPaint's and the system's menus, dialogs
  and popups get it. Upstream files touched for it (2026-10-16): `PaintActivity`
  (the page number, dialog buttons and `message`, a toast with its own view since
  the system draws plain text toasts in its own font), `LayersPanel`,
  `SettingsForm`, `FullscreenController`, `Footprint`, `PageOverview` and
  `ToolButton`: each `setTypeface(null, BOLD)` or default-face `Paint` now takes
  `Theme.face(400 or 700)`.

### (e) Tooling

- `scripts/test_inkwash.sh`: desktop checks for the engine and Scribulor
  classes, plus `compose_check.c` comparing native and Java rendering.
  `scripts/test_document.sh` (upstream's) runs them too. `scripts/carry_goldens.cjs` draws Smudge's golden pages
  with the web lab's engine in Node (`app/src/test/resources/carry`, read by `CarrySmudgeChecks`);
  `scripts/smudge_pictures.sh` draws Smudge with the app's engine as the Manta shows it, and each golden beside the lab's.
- New `*Checks.java` in `app/src/test`, including golden checks
  (`LegacyConversionChecks`: old codes and built-ins convert to fixed M codes and pages; an eraser golden in
  `EraseBothChecks`), `LatticeChecks` (a big stroke's half-resolution lattice against every pixel) and
  `ModularBenchChecks` (desktop timing budgets at about twice this Mac's slowest style, so a change that doubles a
  style's cost fails here before it lags the pen on the Manta). `ErasableChecks` (the pencil-ink-erase workflow pixel for
  pixel, part erasable, Smudge, wet paint, quick, Undo, codes and rolls, slots) and, in `scripts/test_document.sh`,
  `ErasableFileChecks` (TSM3/TSM4 byte for byte, TSM5 round trips, sections, damage, books, recovery, Undo) and
  `OlderReaderCheck` (the document classes from before Erasable, from git, refuse a TSM5 drawing). `LegacyGoldenChecks` and `WashGoldenChecks` pinned
  the old engines and went with them. The two goldens were re-recorded once for the speed work (paints' bodies
  changed on purpose); the eraser over the old page still gives its old sums.
- The timing bench: `ModularPerfBench` (androidTest; a 1.5 s, 480 Hz stroke on a full Manta page replayed in real
  time with Android's batching; `hashes` mode lists each case's page CRC, so a speed-up can show it changed no
  pixel), run on the device by `scripts/bench_device.sh` (the `bench` build, `ing.ell.mybaddrawings.bench`, no
  launcher icon, uninstalled after each run) and on the desktop by `scripts/bench_desktop.sh`. It reports each
  sample's wait from the pen to its render, paces settling as the app does, and feeds input as the Manta sends it
  (`SYNC`), batched (`BATCH`) or at 60 Hz (`VSYNC`). `LiveStrokeBench` (`LIVE=1 scripts/bench_device.sh`) sends
  real pen events into the bench build's own drawing screen and prints `DrawingPad`'s stroke timing lines.
- Pictures at both tablets' sizes: `ScreenShots` (androidTest), run by `scripts/screens_device.sh` in the bench build.
  On the Manta it shows the Nomad's layout at its physical size through `PanelPreviewLayout` (MonoPaint's own
  smaller-panel simulation, put round Brush Lab's and the Tools page's content; the drawing screen uses its own),
  never the tablet's display settings; the drawing screen is drawn from its views (its page goes straight to the
  panel, which screenshots miss).

- `web/engine/`: the engine compiled to WebAssembly (GC) by TeaVM 0.16.0 (`bash web/engine/build.sh`, its own Gradle
  build, nothing reaches the APK): test_inkwash.sh's sources, `WebEngine.java` (its exports), shims for the
  `java.util.concurrent` classes TeaVM lacks (run inline) and `Runtime.maxMemory`; `engine.js` its JS API. Brush
  Studio and the live probe draw with it; `scripts/web_parity.sh` compares it with the JVM (136 of 137 pixel for pixel).

## 3. Proposed engine-module boundary

### What a host (MonoPaint) would call

```java
// Brushes travel as strings.
ModularBrush brush = ModularCode.decode("brave-onion.M24p6...0nz0.1u5hvr4"); // null if invalid
boolean newer = ModularCode.newer(code);   // from a newer version: some parts stood in (docs/BRUSH-CODE.md)
String code = ModularCode.encode(brush);

// A stroke is MonoPaint's own DrawingStroke interface: live, or quick when live would lag the pen.
DrawingStroke s = Engine.start(surface, layer, brush, sizePx, darkness /*0-1*/, wetSession);
s.sample(x, y, pressure, tiltX, tiltY, timeMs);   // per pen event
s.finish();                                         // one undo step
// A quick stroke (instanceof QuickStroke) renders its true stroke on a thread of its own: lift() at pen-up, then
// develop(0) now and then until false (it composes the page then); finish() waits and does it at once.
// A host with one thread (the browser) sets QuickStroke.renderAtLift: the render runs in develop, at lift.

// While a slow-drying wash settles after lift (~10 Hz, paced), and before anything else edits the page:
long nextMs = wetSession.tick(surface, show);  wetSession.settleNow(surface);
wetSession.input(handleNanos, eventAgeMs);   // per pen event: lagging input backs settling off
boolean wet = wetSession.wet();              // for a "still wet" indicator; wetSession.wetChanged tells when
wetSession.tilt = () -> PageTilt.pull(gx, gy);  // gravity: the page's downhill (CanvasGravity.read() on Android)
boolean listen = wetSession.wantsTilt();     // run the sensor only then; wetSession.tiltChanged tells when

// Preview images for menus and pickers (any thread, own scratch page).
int[] pixels = Engine.sample(brush, darkness, Shape.LOOP, sizePx, width, height);
```

Today these are `QuickStroke.start(ToneDocument, ModularBrush, double size,
double darkness, WatercolorSession)` (live strokes are `ModularStroke`s), `WildSample.modular(...)` and
`WatercolorSession`; `Engine` is a facade still to write.

### What the engine needs from the host

- **A tile surface like `ToneDocument`:** read tones and the tones under the
  stroke (`tone`, `strokeBaseTones`, `layerDarkness`), write blocks
  (`setTones`, `coverTones`), pick a layer, and undo hooks (`begin`,
  `finish`, `finishWithPrevious`, `cancel`, `restoreSpan`, `revision`).
  About 20 methods; could become a `ToneSurface` interface that
  `ToneDocument` implements.
- **Paper textures:** thirteen 64 KB grain files (`assets/textures/*.bin`: five ambientCG scans, eight Unsplash photos) via
  `Assets`/`PaperGrain`; noise otherwise procedural.
- **A settle ticker:** something calling `tick` ten times a second while a
  Drying wash settles, waiting as long as it answers (`DryingTicker` is ~60
  lines of Android `Handler`), and the pen events' timing for the pace
  (`SettlePacer`, optional: without one nothing is paced). Given a
  `SettleWorker`, the session works its ticks out on that thread and `tick`
  answers 5 ms while one runs; without one everything runs on the caller's.
- **A quick stroke's lift:** something calling `QuickStroke.develop(0)` every few ms after lift, keeping the stroke as
  "still finishing" until it answers false (`QuickLift`, ~60 lines of Android `Handler`); without one, `finish` waits
  for the render at lift. The engine starts one thread per quick stroke.
- **A clock** (`LongSupplier`) for pen speed and wet windows.
- **Threading:** strokes run on the page's owner thread, sharing big pieces
  of work with `Cores`' helpers while it waits (the surface must allow
  writers in separate tile rows at once: `ToneDocument.openBands`); samples
  draw on their own scratch page, so they can run on a background thread.
  Wet paint's work after lift reads the page on the session's `SettleWorker`
  while the owner thread doesn't write it (the session sees to that: anything
  that writes first takes the work in or lets it go), and only the owner
  thread writes what it laid.
- **Batched input:** a host whose pen events carry one sample each should
  gather them and feed the stroke what has gathered (`PenBatch`), or a big
  brush falls behind.

### What would need adapting to MonoPaint

| Engine today | MonoPaint's equivalent | Adaptation |
|---|---|---|
| `ModularStroke.start` picks the layer via `InkWashLayers` (ink over wash pairs) | Free layers (`LayersPanel`, `MAX_LAYERS`) | Host passes the target layer; move the pair rule into Scribulor. |
| `darkness` 0–1, `size` px | `int gray` 0–255 (`GrayPalette`), `ToolSettings` min/max diameter + pressure response | Map gray → darkness; size from the tool's diameter. The brush's Control part handles pressure itself. |
| `WatercolorSession` / `DryingWater`: paint settles in place; with gravity dials a `GravityFlow` moves it with the tilt | `WetWatercolor`: water that runs with tilt (`CanvasGravity`), `BlowDryer` | Keep both; engine strokes ignore MonoPaint's wet layer at first. Both now read the tilt through `CanvasGravity`. Deciding how they meet is a conversation, not a port. |
| Brushes stored as `name<TAB>code` (`BrushLibrary.Store`) | `ToolLibrary` versioned binary, `ToolSettings.Tool` enum (append-only) | One new tool kind that holds an M code string. |
| Writes via new `ToneDocument` methods | `ToneDocument` without them | Upstream those methods first (or the `ToneSurface` interface). |
| Everything package-private in one package | Same package name | Easiest path is same-package files; a Gradle `java-library` module would need a public facade. |
| Masking fluid respected; Resist lays it (masking fluid, and wax as its permanent share) | Mask layer (`hasMask`), its own tool and Peel | Resist reuses the layer; MonoPaint's tool could lay wax through `beginFluid(erasable)`. |

### Share codes as a portable format

M codes (`name.M<style><six parts>.<seed>`, spec in
`docs/archive/superpowers/specs/2026-10-14-modular-engine-design.md`, "Share codes")
are plain ASCII, append-only (new dials go on the end), case-insensitive, and
decode identically in the web prototype and the Java engine. They are the
natural exchange format: a brush made in Scribulor's Brush Lab or the web lab
could be pasted into MonoPaint and draw the same.

## 4. Upstreamable now

Generic wins that help MonoPaint without the rest of Scribulor. Each would need
re-cutting as a small commit on top of `upstream/main` (some are tangled with
Scribulor code today).

| Priority | Commits | Pitch |
|---|---|---|
| High | `84a568e` | Render changed areas with the existing native compositor: a big settle went from 640 ms to a few ms on the Manta; desktop check proves pixel-identical output (`compose.h`, `compose_check.c`). |
| High | `1a0ef71` | Bug fix: with a palm already resting, the pen landing (`ACTION_POINTER_DOWN`) now closes an open panel (`Ui.outside` per pointer). |
| Medium | `29abb08`, `389897c`, `a9c7247` | Automatic full e-ink refresh at natural pauses (pen out of range 2 s, menu closed, big undo), never under the pen. Clears ghosting without a button. Hooks into `InkWashBar` need replacing. |
| Medium | `cefc8af`, `e4753c9`, `5daf4ba` | `ToneDocument`: linked undo steps (one Undo for a gesture spanning layers) and one place for tile undo capture. |
| Medium | `bcee59b` (interface part) | `DrawingStroke.sample(..., time)` default method: strokes can follow pen speed; existing strokes unchanged. |
| Medium | `0379ce9`, `d0b66aa`, `2066ef3` | Rename and delete drawings safely with `AtomicFile` backups, case-only renames, taken names. Only if MonoPaint wants in-app rename. |
| Low | `7307362` (`BrushStamp` part) | Per-thread scratch paths, if MonoPaint ever draws previews off the main thread. |
| Low | `03dbb85` | Thin outline of the eraser's size while erasing (`RasterOverlay`). |
| Low | `inkwash-erasable` (`DocumentCodec` part) | Tagged sections after a page's body (TSM5): later per-layer data without a new magic; an unknown section skipped. And `ToneDocument`'s captures by tile key, so a block write looks a capture up without a map or lock. |
| Medium | `bc7b014` (interface and `DrawingPad` part) | `DrawingStroke.present()`: a stroke stamps each sample but shows the page once an input event (a 480 Hz pen batches up to eight samples per 60 Hz event, more when the screen falls behind). Default does nothing, so existing strokes are unchanged; any MonoPaint stroke that composes per sample could use it. |
| Low | `2874178`, `cccaafb` | Device benchmark harness: a `bench` build type (release speed, own id, profileable, no launcher icon) and `bench_device.sh` timing strokes on the tablet, uninstalling itself after; MonoPaint has `BrushPerformanceChecks` to pair with. `ModularPerfBench` itself drives Scribulor's engine; the build type, manifest and script are generic. |

| Medium | `inkwash-zoom` (`PinchPan`, `DrawingPad.navigationGesture`) | With zoom unlocked, two fingers stay a two-finger tap until one moves past the tap's own slop, and only then pinch and pan, from where they landed: an Undo tap no longer nudges the view (a 103% zoom), a pinch never also undoes. Pure, with desktop checks. |
| Low | `inkwash-zoom` (`DrawingStroke.zoom`) | A stroke that reads the pen's speed reads it on the glass when zoomed, so a speed-sensitive tool behaves at 800% as at 100%. |

Not candidates: anything that assumes ink/wash layer pairs, the Scribulor bar,
slots or Brush Lab.

## 5. MonoPaint v0.98.3 watercolor (our fork point) and later upstream

Scribulor forked at `5212e95` "Release MonoPaint 0.98.3" (the merge base with
`upstream/main`), so all of MonoPaint's watercolor is already in Scribulor's tree,
untouched and unreachable from Scribulor's UI (its color bar and toolbar are
`GONE`). It arrived in 0.98; 0.98.3 only made the wet brush pen erase with the
Eraser color. Notes from reading it, and how each part relates to Scribulor's own
wash (`DryingWater`, which settles in place and never flows: `PHILOSOPHY.md` #4).

### Their flowing watercolor (`WetWatercolor`)

| MonoPaint | What it does | Scribulor |
|---|---|---|
| Tiles | A transient wet layer over the dry drawing in 16 × 16 tiles made only where paint lands; per pixel pigment, water freshness (`pull`, mobility = freshness³), reserve, stuck and soaked paint. | Own model: 2 × 2 cells kept only where the wash reached (`DryingWater`), one patch at a time. |
| Flow and diffusion | Trades along 4 rays a pixel (1, 4, 8 px), stopping at dry pixels, at the less fresh pixel's mobility, so ink is conserved; tiles joined by water trade their means 8 quick steps a frame (`planSpread`); freshness decays .985 a frame. | Pigment drifts toward the paper's valleys, the wash's middle and a slow curl, and is laid in place; nothing runs. |
| Gravity | `CanvasGravity` reads the accelerometer; pixels draw more from uphill, tile pairs pool downhill, wet reach grows downhill to 32 px. App setting **"Watercolor uses gravity"**. | **In the app** (2026-10-15, owner's decision after the Gravity Lab prototype): the tilt from MonoPaint's own `CanvasGravity` (credited, reused; its dead zone now `PageTilt.FLAT`), only while something wet could move; its own model, `GravityFlow` (a Slow wash's runs, pooled edge, drips, sinking grain, rocking; Quick's window; charcoal's dust), as dials per brush rather than a setting. Only wet paint moves, never once dry (`PHILOSOPHY.md` #4). |
| Wet brush pen | Lifts dry paint into a hidden reserve (2× the shade) that dissolves while the water is fresh; trades ink with the paper (`BrushInk.trade`) and carries it between strokes. | **Smudge** (2026-10-16): Lift, Drag, Smear trade paint with the page per dab-sized reservoir cell (`Carry`, from the web lab's Carry), wet or dry, on the stroke's own layer; every stroke starts with a clean brush unless Drag's or Smear's **Keeps Paint** is on (2026-10-19: Hold ink between strokes, the Dirty brush, `HeldPaint`), which the side bar shows and Clean Brush empties. Its Dry mode (clear water, no ink of its own, dragging what it passes by `pull`) is **Float's Clear water** (2026-10-16): Smudge's Own paint 0 on floating paint, Pick up as the pull with the same pressure curve; it moves outlines rather than trading pigment, so rings stay crisp. |
| Wet canvas | `WetnessBar`: above 0, existing paint near a stroke is rehydrated and bleeds. | Not planned: no modes. |
| Preview, then flow | Under the pen a flat tint glaze (`queueWater`), the dabs queued; the stroke holds its wet paint still (`holdsWetPaint`) and it starts to move once you lift. | Same shape: Slow (Drying) shows a preview under the pen and settles after lift; Fast-wet develops a window behind the nib. |
| Threads | Up to 3 "Wet paint" helper threads; each tile writes only itself, so the result doesn't depend on the split. | `Cores`: 3 helpers the same way, for compose, stampers, settles, blurs and the render (round 2). |
| Work budget | `WetWorkBudget`: 0.5–2 ms a slice while drawing, 5 ms idle; after a slow pen event or slice it backs off **100 ms**; the pen always wins (no wet work while pen pixels wait). | **Borrowed** (2026-10-15): `SettlePacer`, below. |
| Screen updates | Moving water shown at most every **120 ms** (`WET_SHOW_MS`); a slice keeps to a 96–192 px span so a far tile doesn't stretch the dirty rectangle. | **Borrowed**: settling shows at most every 120 ms, its areas gathered into one. |
| Undo | Each animation slice is a continuation of the stroke's gesture: motion is part of the stroke's undo step. | Same: each tick `finishContinuation`, one undo with the stroke. |
| Storage | Wet state is never saved; only the resulting tones. | Same. |
| Masking fluid | A page-wide mask layer (TSM4), hatched; shields every layer from paint, erasing and running water; Peel removes it. | Engine strokes respect it (compose); MonoPaint's UI to paint it is hidden; **Resist** (2026-10-18), a Medium choice, lays it as a brush: masking fluid the eraser lifts first, or wax, its permanent share, never hatched. No Peel button: Clear page peels it all. |

### The blow dryer

Tapping MonoPaint's rippling droplet runs `blowDry()`: `finishStroke`, then
`dryWet()`, which lays any queued dabs in full (the real result, not the glaze),
flushes the last frame and **freezes**: the wet state is dropped and the page
keeps its tones as they are. `BlowDryer` is only the animation: a hair dryer
and air lines in pure black lines (6 frames × 140 ms) so the button stays on
the panel's fast black-and-white refresh; a tap up to 1 s after the ripple
stops still counts.

Scribulor's answer is the **wet indicator** (`WetIndicator`). Until
2026-10-29 its tap was `settleNow`, a freeze of its own: everything still
suspended laid where it was. Since then (the owner: "looks like the hair dryer
just stops the image as it is instead of accellerate the drying processing")
it fast-forwards (`DryingTicker.hurry`, `WatercolorSession.hurry`): each wet
thing runs its remaining ticks back to back, unpaced, on the page's tilt as it
is, and the page shows once at the end, as left alone to dry, one undo step
with its stroke; MonoPaint's blow dryer blows round until it is done. Laying
where it is stays for a stroke, a pause, another page, and for what is still
wet after `WatercolorSession.HURRY_MS` (4 s). It is a droplet, dashed while
dry, in the side column and beside Brush Lab's Clear; it changes by a partial
update of its own square, never under the pen.

### Pacing borrowed (`SettlePacer`)

- **Back-off:** a pen event handled ≥ 24 ms late or taking ≥ 8 ms, a Drying
  tick with its show over 40 ms, or a Fast-wet re-settle over 8 ms backs
  settling off for 100 ms. Ticks wait it out (`WatercolorSession.tick` answers
  how long); a Fast-wet re-settle is still worked out when its rule says
  (its timing is the look) but its box is shown once the pace allows, and at
  lift at the latest; the pen's own dabs never wait. While the pen is down
  nothing ticks (as before).
- **Showing:** settling is shown at most every 120 ms; meanwhile the page's
  dirty area (ticks) or the stroke's pending box (Fast-wet) gathers. The last
  tick always shows.
- **Same result:** a tick steps by its patch's age, not the clock, so a paced
  Drying wash ends on the same layers, bit for bit; a Fast-wet stroke ends on
  the same page tones, and a layer's coverage can end lower at a few pixels (a
  layer keeps the most coverage any compose gave a pixel; the same caveat as
  composing once an input event). `SettlePacingChecks` checks both (10 Fast-wet
  strokes: under 600 pixels of 1.7 million with lower coverage, none higher).
- No pace (the desktop checks, samples, `ModularBenchChecks`): unchanged.

### Upstream since the fork: `8743286` (2026-10-14, unreleased)

"Bring back the clear-water wet brush pen and add opaque wet paint": a brush
pen setting **"Hold ink between strokes"** (off = clear water that only moves
what is there); **opaque wet paint** with a **Coverage** setting (50%); both
brush pens size with pressure; Strength 60%; a reworked tile-scale spread; the
preset format adds Coverage.

- MonoPaint's opaque wet pen is the reference for Scribulor's **Smudge** part
  (Lift / Drag / Smear: `PartChoice.LIFT`, `DRAG`, `SMUDGE`; built 2026-10-16) with
  Medium = Cover: opaque Lift's paint takes on the dark it covers (its `MIX`), as
  `BrushInk` does; the clear-water pen (Hold ink off) is Smear with Own paint 0, the
  Stump. Taken from them: a reservoir that trades with the page per dab, conserving
  what it moves (`BrushInk.trade`), and the work shared over cores in bands that
  never share a cell. Spec `docs/archive/superpowers/specs/2026-10-16-smudge-design.md`. Holding ink between strokes came
  later (2026-10-19) as Drag's and Smear's Keeps Paint (the Dirty brush), and Hold ink off as Smudge's Clear Water
  (the Water brush: lifts, and lets the lifted paint run out through its wet, a wash's again with Slow drying); spec
  `docs/archive/superpowers/specs/2026-10-19-wet-smudge-design.md`.
- Upstream `fe6786d` (oil paint brushes that run out of paint and smudge; in the tree, as MonoPaint's own tools) is the
  reference for **Paint Load** (2026-10-19): Drag's and Smear's Paint Load and Starting Load (MonoPaint's Minimum load),
  the paint left falling as `PaintLoad.travel`'s smoothstep, a wider dab spending faster; a rub on a grey face loading as
  `ToolSettings.loadedBy` (the travel, twice it here); the Filbert's crescent as `BrushStamp.crescent`, the bite set
  along the way the dab moves rather than its short axis. Spec `docs/archive/superpowers/specs/2026-10-19-oil-paint-design.md`.
- **Not merged.** A trial merge conflicts only in `CHANGELOG.md`
  (`DrawingPad`, `PaintActivity`, `PressureStroke`, `ToolSettings` merge
  cleanly); its code only matters if Scribulor shows MonoPaint's tools again.
  The owner decides whether to merge (conflict log, section 6).

## 6. Habits that keep integration possible

- **Keep the engine pure and separable.** No `android.*` in category (c);
  reach the page only through `ToneDocument` methods; keep new engine classes
  in `scripts/test_inkwash.sh` so `javac` proves it.
- **Keep Scribulor UI in its own layer.** Styling, the bar, slots and Brush Lab
  live in Scribulor classes. Edit an upstream file only when there is no other
  seam, and keep the edit small (a hook, not a rewrite).
- **Split generic wins into small, focused commits** with clear messages that
  say what and why, separate from Scribulor features, so they can be
  cherry-picked onto `upstream/main`.
- **Keep `upstream` fetched.** `git fetch upstream` now and then; check
  `git merge-tree --write-tree --name-only HEAD upstream/main` for conflicts
  and note them below. Never push to upstream (push is disabled).
- **Package name stays `io.github.mpdairy.monopaint` for now.** Pro: diffs and
  merges stay small; files move between the projects unchanged; everything
  package-private keeps working. Con: the package names mpdairy, which could
  confuse readers; renaming later touches every file. Revisit if the fork
  becomes permanently separate or is published.
- **Record decisions here** (and specs in `docs/superpowers/specs/`).

### Upstream conflict log

| Date | Upstream | Result |
|---|---|---|
| 2026-10-15 | `8743286` (wet brush pen, opaque wet paint) | Merged into inkwash-v2-lab. Conflict only in `CHANGELOG.md` (kept Scribulor's Unreleased; MonoPaint's own notes stay in its repo); `DrawingPad`, `PaintActivity`, `PressureStroke`, `ToolSettings` auto-merged. Checks 50/85 PASS. |
| 2026-10-23 | `cc40de2` (reconnect the fast display) | Merged commit by commit on `upstream-lasso` (one merge each, so the history says which was taken how). Taken: `SelectionFeedback`'s kept control session, closed at focus loss. Kept Scribulor's `DrawingPad` reconnect (`DisplayRecovery`), which already does upstream's retries. |
| 2026-10-23 | `ce7cde8` (fast display into the `fasteink` submodule) | Recorded only (`merge -s ours`): Scribulor's `DirectEink` does more than `fasteink` 7daef8f (the mode 5 stroke plane `StrokePanel`, `endFast` through `panel_end.h`, ENOMEM as busy, present timing), so no submodule and no `FastEink`/`Session`. Later upstream code written against `FastEink` needs porting back to `DirectEink`. Moving Scribulor's additions into fasteink is the way to converge. |
| 2026-10-23 | `ff743a2` (a finger closes the menus a finger opened) | Taken, `Ui.closesPanel` with Scribulor's palm rule: a contact wider than `FingerTool.PALM_DP` never closes a menu (`Ui.widestFingerDp`, which `DrawingPad` now shares). Brush Lab's swap panel stays pen-only. |
| 2026-10-23 | `5f764ec` (Lasso, image import) | Taken: `FloatingSelection`, `CanvasGuide` (now also the gradient guide, in place of Scribulor's `RasterOverlay` copy of it), `SelectionPreview` and `selection_preview.c`/`layers.h` (built, unused by Scribulor), `ToneDocument.addLayer(pixels)`/`copyLayer`/`overTone`/`beginLayer`, the Lasso in MonoPaint's hidden toolbar, Undo dropping a floating piece. Kept Scribulor's: `viewport_bitmap.c`, `DrawingBrowser`, the file menu and Import Image. Dropped: `ImageImport`, `PictureFiles` (request code 3 is Brush Lab's). Then the Lasso wired into Scribulor (below, "The Lasso"). Checks 125 RAN, none failed. |

## 7. Open questions for a first conversation (draft for Elling)

1. Is a shared brush engine interesting to you? As copied files in the same
   package, a Gradle module, or not at all?
2. Would you take a `ToneSurface`-style interface (or the new `ToneDocument`
   block methods) upstream, so an engine can sit outside `ToneDocument`?
3. Wet paint: MonoPaint's runs and flows; Scribulor's settles in place. Engine
   brushes now join `WetWatercolor` (the Wet Drying choice, 2026-10-22, below);
   would you take its small hooks (erasable and kind, rest, rewet) upstream?
4. Would M share codes work for MonoPaint's users (pasting brushes between
   apps, the web lab)?
5. Which of the section 4 fixes do you want, and how: PRs, patches, or issues
   first? Any code style or commit-message conventions?
6. Are you OK with Scribulor keeping the `io.github.mpdairy.monopaint` package,
   or would you prefer a rename?
7. How should Scribulor credit MonoPaint in the app and README?
8. Testing: are desktop checks plus a device bench a format you'd use?
9. Your roadmap: anything planned that would collide with Scribulor's edits to
   `DrawingPad`, `ToneDocument` or rendering?

## 8. Keeping this doc current

Update this file when:

- the engine's boundary changes (a class moves between (c) and (d), a new
  `ToneDocument` method, the facade or `ToneSurface` lands);
- a commit touches an upstream file (add it to the (b) table);
- a generic fix lands (add it to section 4 with its hash);
- `upstream` is fetched (update the date, divergence numbers and conflict log).

Numbers above come from `git log --oneline upstream/main..HEAD | wc -l` and
`git diff --stat upstream/main...HEAD`.

#### Brush import on resume (2026-10-18): upstream files touched

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | 1 | `onResume` calls `inkWash.importBrushes()` (Scribulor-owned: reads Document/Scribulor import files). |

#### Fast display recovery (2026-10-18): upstream files touched

The owner's note: "sometimes gets sluggish … could jog it back by clicking open project … FAST DISPLAY stopped … after
hovering with the eraser ring … autosave?". Upstream's `flush` gave up the gray session after 120 refused presents
counted across every caller (the retry every 8 ms and the eraser's ring at every frame together, so a busy spell of
about 0.65 s instead of 1 s), and on any present failure dropped the canvas to Android drawing (slow on e-ink) until
something else called `connectDisplay` (a dialog closing, the window's focus back). Its notice waited in `saveError`
and showed as "Autosave failed: Fast display stopped …" at the next `showSaveError`, which `openDrawing` calls first:
so the notice came when he tapped Open, and closing the file browser reconnected. New `DisplayRecovery` (pure,
Scribulor's own; `DisplayRecoveryChecks`): a busy panel is given up after 120 refusals and 2 s, however many callers;
after a failure the canvas connects again by itself after 1 s, three times in a row at most until a present goes
through; the notice is said at once.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +35 −7 | `flush`: refused presents counted by `DisplayRecovery` (timed), a failure marks the page owed a refresh and calls `reconnectLater` instead of setting `app.saveError`; `reconnect` connects again when no pen or pinch is on the page; `connectDisplay` restarts the busy count and, failing while recovering, tries again; `disconnectDisplay` drops a pending reconnect. Generic: any MonoPaint build on a firmware whose driver refuses (EAGAIN) or errors now and then. |

#### Notebooks (2026-10-18): upstream files touched

Branch `inkwash-books` (spec `docs/archive/superpowers/specs/2026-10-18-notebooks-design.md`): the app opens to a shelf of
notebooks; everything lives in MonoPaint's own multi-page books, which Scribulor had hidden. Scribulor's own, new: `PagePicture`
(a page's 480 × 600 picture at 16 greys, pure), `BookPictures` (the notebook block's format and its random-access read,
pure, beside `BookCodec`), `NotebookMigration` (the old drawings into notebooks, once, pure Java), `NotebookExport`
(the PDF and grey PNG copies, pure Java), `NotebookLayout` (the shelf's and the notebook's sizes, pure), `NotebookScreens`
(the two screens, over the drawing screen), `PageTurner` (the bar's ‹ page ›, Notebook). Changed, Scribulor's:
`DrawLayout` (`LAYERS` off: the layer controls hidden, the layer routing untouched; the turner's items, folded on the
Nomad in landscape), `InkWashBar` (the turner at the foot; the layers not added), `AutoExport` (the notebook's copies
instead of one page's PNG), `DrawingNames` (`notebook`, `shown`, `stored`), `InkWashLayers.resized` (with an offset).
Engine untouched.

| File | Lines | Why |
|---|---|---|
| `BookCodec` | +98 −24 | Writes its stored ZIP by hand (`ZipOutputStream` can't put bytes before its central directory): the manifest, the pages, then the notebook block (`BookPictures`), then the central directory, which lists the manifest and pages only, as before, and a fixed entry time (the same book, the same bytes). The whole file stays within what `read` accepted before (16 MB + 64 KB): pictures that don't fit are left out. `read` no longer inflates every page: each page's ZIP CRC-32 (checked by `ZipInputStream`) and header (`DocumentCodec.checkHeader`) still refuse a damaged file, and only the shown page decodes (24 pages: 812 to 56 ms on this Mac). `readPages`: the checks without decoding, for the migration. Generic: a faster open for any MonoPaint book; older readers (MonoPaint's, this repo's `580a326`, checked) read a notebook as a plain book. |
| `DocumentCodec` | +12 | `checkHeader`: a page's format and size without inflating it. |
| `DrawingBook` | +132 −11 | Each page keeps an opaque picture that moves with it through add, duplicate and delete and is dropped once the page changes (`picture`, `pictureKey`, `offerPicture`, `restorePictures`), and the book a cover page (`cover`, `setCover`) that follows its page. `deletePage(int)`: delete a page other than the current one without decoding anything. `cachedPage`, `packed`, `Snapshot.packed()`. The snapshot carries the pictures, the cover and `written`. |
| `DocumentStore` | +57 | `migrate` (on the IO queue, after the library moves and before anything reads it), `notebooks` (every notebook, read from its file's end), `page` (one page decoded, for a missing cover), `forgetRecovery`, `library`; `Snapshot.prepare` runs on the IO thread before a named save (Scribulor makes the drawn page's picture there). |
| `DrawingFiles` | +23 | `notebooks()`: every drawing in the library and its folders but `Originals`; `root()`. |
| `DrawingPad` | +1 −1 | `showPage` never presents straight to the panel while the notebook screens cover the canvas. |
| `PaintActivity` | +145 −23 | The notebook screens in a frame over `root`, inside the turning frame; at launch the migration, then the shelf; the turner's and the screens' actions (`turnPage`, `showNotebook`, `drawOn`, `addNotebookPage`, `deleteNotebookPage`, `leaveNotebook`, `openNotebook`, `newNotebook`); new notebooks named `DrawingNames.notebook`; the file menu Rename notebook, Delete notebook, Finger (New and Open drawing go: the shelf does both); copies when a notebook or the app is left (`exportDue`); Back from drawing opens the notebook, inside it goes to the shelf. |

#### The audit (2026-10-19, branch `inkwash-audit`): upstream files touched

Speed and clean code, nothing the user sees but speed (findings and numbers: `docs/archive/notes/2026-10-19-audit.md`). Every
page is as before: the bench's 210 hashes at 8, 1 and 1000 samples a present and with every stroke quick.

| File | Lines | Why |
|---|---|---|
| `DocumentStore` | +5 −1 | The file thread (`IO`) at the lowest priority: the recovery save after every stroke compresses the whole page (seconds on the Manta) and took a core from the next stroke. Generic. |
| `DrawingPad` | +5 −2 | `renderAll` renders the full page through `ViewportBitmap.compose(…, area)` (the page's own pixels, bands on four cores) instead of the snapshot path (one core, and the snapshot made the next stroke clone whole layers). Generic. |
| `PressureStroke` | −8 +1 | Back to upstream's: `headThickness` was for Scribulor's old pens. |
| `ToolSettings` | −7 | `pressureFor` (old pens only) removed. |

Scribulor's own: `ModularCompose` (a transparent Drying body's preview recomposes only where the body newly lies),
`StickGrain` (the grain's noise built 4× faster, the same floats), `Cores.rethrow` (one copy of "rethrow another
thread's failure", used by `ModularStroke`, `QuickStroke`, `SettleWorker`), dead methods removed (`FloatBath`,
`HaloGrowth`, `LabUi`), `ToolsLayout` uses `LabLayout.clamp`.


#### Notebook pages read from the file (2026-10-18): upstream files touched

Branch `inkwash-books2` (spec, "Pages read from the file"): a book no longer holds every page's packed bytes in memory,
so a notebook may hold 100 pages and 200 MB of them (it was 16 MB). New, generic: `PackedPage` (a page's packed bytes, in
memory or a stored entry of the book's file read on demand and CRC-checked every time; one lock for reads and for a save
moving pages to the new file).

| File | Lines | Why |
|---|---|---|
| `DrawingBook` | small | Pages are `PackedPage`s (a `byte[]` list still makes one); `MAX_PACKED_BYTES` 200 MB; `offerPacked` (the page a save packed is kept, so leaving it needn't pack it again); the limits' words say "notebook" (`FULL`, `FULL_PAGES`, `LAST_PAGE`). |
| `BookCodec` | rewritten | `open`/`openPages(File)`: random access, the structure and every page's header checked, no page read; `write` streams stored pages from their file and returns where each page's bytes start; `replaced`: the rename and the pages pointed into the new file under the lock; one manifest parser for both readers. |
| `RecoveryCodec` | +70 | TSR3: only the pages not in the notebook's file, the rest by length and CRC; `read(input, drawing)` finds them; TSR1 and TSR2 still read. |
| `DocumentStore` | +25 | `openBook` opens from the file (a damaged file set aside and its previous save put back, `DrawingFiles.repaired`; an older version's interrupted save's backup put back, as AtomicFile reads it); a named save streams to a fresh file, is read back whole (`DrawingFiles.whole`), then put in place keeping the previous save (`DrawingFiles.commit`) and `replaced`; `recover` and `page` read from files. |
| `BookChecks` | 2 | `packedActivePage` is a `PackedPage`. |

Still whole in memory: `DocumentStore.move` (moving the library from private to shared storage, once) copies each file
whole. Generic: any MonoPaint book of any size opens and saves without holding its pages.

#### Saving less while drawing (2026-10-19, branch `inkwash-saveless`): upstream files touched

Audit findings 6 and 7 (`docs/archive/notes/2026-10-19-audit.md`). The recovery save ran after every stroke and packed the whole
page each time (240 ms of a core here on the owner's heaviest pages, about 4.8 s on the Manta at most), and its
snapshot made the next stroke copy a whole layer on the drawing thread. Now it waits for a pause, packs the page quickly,
and the page is packed once per change: what the recovery packed is what a page turn and the next save start from. Files
are byte for byte as before. New, Scribulor's own: `RecoverySchedule` (pure; `SaveLessChecks`).

| File | Lines | Why |
|---|---|---|
| `DocumentCodec` | +11 −1 | `write(stream, snapshot, level)`, any deflate level; `QUICK` (`BEST_SPEED`), 30% of the default's time for about 18% more bytes. The two-argument `write` keeps the default level. Generic. |
| `ToneDocument` | +34 −5 | `Snapshot.release`: each layer counts the snapshots holding its arrays (`holds`, under the layer's lock; `shared` volatile); a released snapshot takes its count back and at 0 the next write needn't copy the layer (9–14 MB). One never released keeps the copy, as before. Generic. |
| `PackedPage` | +12 −5 | `quick`: bytes packed at `QUICK`, for memory and the recovery file only. |
| `DrawingBook` | +62 −8 | A page left is packed at `QUICK`; `encode(document, level)` releases its snapshot; `offerPacked` lets quick bytes give way to the file's (any decoded page, `savedPages`); `savedPages`: after a save, the pages it was made from read the file's bytes; `Snapshot.packed` packs the active page quickly (the recovery), `Snapshot.filed` packs every quick page at the default level (a book's file), `activePacked`, `release`. |
| `BookCodec` | +1 −1 | `write(output, snapshot)` writes `snapshot.filed()`. |
| `RecoveryCodec` | +2 −1 | A recovery's inline pages read back as quick, so a file made from them gets the default level. |
| `DocumentStore` | +16 −7 | `recoverLater` keeps each snapshot's own result (it used the first caller's for all); the recovery and named-save snapshots are released once written or dropped. |
| `PaintActivity` | +45 −17 | `recovery()` is "the page changed": its save waits on `RecoverySchedule` (2.5 s still, 5 s at most, never while drawing, wet paint until 5 s); `recoveryNow()` saves at once at a pause, page turn, add or delete, a notebook opened or new, a named save, a rename; the notebook view and the shelf save a change still waiting (`recoverySaved`). A recovery's packed page goes back to the book (`offerPacked`). `saved` takes the file's pages back (`savedPages`) before its recovery save, which then packs nothing. |
| `DrawingPad` | +2 | `drawingNow()`: a stroke, shape, fill or gradient under way or still finishing. |
| `BookChecks`, `NotebookPagesChecks` | +3 −1, +2 | A page left is packed quickly; the checks' save takes the save back as the app does. |

Not changed: the notebooks' streaming save (`BookCodec.write`/`replaced`), TSR3's format, the migration (it writes a
working book's pages as it holds them; only a recovery written by this version, read by a migration that failed and runs
again, could put quick bytes in a notebook, which read the same). The PNG copies key each page by its bytes' CRC, so a
page exported while packed quickly is drawn again once more at the next export after a save.


#### Page shade (2026-10-19, branch `inkwash-pageshade`): upstream files touched

The owner: "a way to choose the color of a page on start". On a page with no marks (no paint, masking fluid or
magnets), a grey picked on the side bar shows a Fill page button beside it for 4 s; it shades the page's paper that grey
(the lightest grey: White page, plain paper again), one Undo step. The shade is a page property, the **paper tone**, not
a layer: every composite starts from it instead of white, and nothing else changes. Transparent paint (raw tone black at
its coverage) then darkens the paper as tinted paper takes a glaze, opaque paint and White cover it, the eraser rubs back
to it, and the brushes, which read their own layers over white, find blank paper whatever its shade. No per-pixel work
is added: a composite's first value is a field instead of a constant. A white page (255) writes, draws, exports and
pictures byte for byte as before (`PageShadeChecks` pins a drawn page's bytes, pixels, picture and PNG from before; the
bench's hashes, goldens and `LegacyConversionChecks` unchanged). New, Scribulor's own: `PageShade` (the offer: which grey,
when, for how long, pure; `PageShadeChecks`).

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +49 −20 | `paperTone`, `placePaper` (from a file), `shadePaper` (one Undo step: `StackState` holds the paper), `unmarked`; `Snapshot.paper`; `compositeToneWithout`, `compositeToneKept` and the tile composites start from the paper (`baseComposite` takes the paper to read over: Behind reads the marks over white, White and Invert the page as shown; `foundComposite`, Halo's, over white). The one write rule on a shaded paper only: a pixel holding darkness alone (a black raw tone) takes its new tone's coverage, lighter too, so a Slow wash settling lighter stays a glaze rather than turning into light paint over the shade (over white the same tone; white pages untouched). Generic: a tinted paper for any MonoPaint page. |
| `DocumentCodec` | +23 −8 | TSM5's `PAPR` section: the paper tone, a byte (a later version may add more; the reader takes the first), written only when the paper isn't white, TSM5 written for that alone too. A TSM5 reader from before skips it and opens the page on white; MonoPaint and Scribulor before TSM5 refuse a TSM5 page ("Unknown painting format"), so a shaded page that would otherwise be TSM3 or TSM4 doesn't open there at all. |
| `ViewportBitmap`, `viewport_bitmap.c`, `compose.h` | +15 −14 | The native compose's paper tone (`nativeCompose(…, paper, …)`), hoisted: each row starts from it as it started from white. `compose_check.c` compares it with the Java renderer on white and on a shaded page. |

Scribulor's own: `PagePicture.Composite` (a snapshot's paper; the notebook pictures and `NotebookExport`'s PNGs and PDF),
`InkWashLayers.resized` (keeps the paper), `ModularStroke.reading` (the page a Blend reads, on the paper it needs; used by
`ModularStroke.under` and `QuickStroke`), `ModularCompose.Under` (its paper), `SwatchFaces.Host.greyPicked`,
`SwatchBar.grey`, `InkWashBar` (`offer`: a button in the label's place beside the column, taken away by the pen as the
label is; the grey flyout opening hides it). Brush Lab's test page is never shaded.

#### Naming notebooks and the blank page at the end (2026-10-19, branch `inkwash-booknames`): upstream files touched

The owner's feedback on notebooks: a name prompt for a new notebook (the name it would get, selected, and three
suggestions, one tap each), Rename and Delete by a long press on the shelf, a blank page deleted without asking, and
always one blank page at a notebook's end. New, Scribulor's own: `BlankPages` (what is blank, the page at the end, the
copies leaving out blank pages at the end, pure; `BlankPagesChecks`), `NotebookNames` (the prompt's names, pure;
`NotebookNamesChecks`), `NamePrompt` (the dialog). Changed, Scribulor's: `NotebookScreens` (a long press on a tile; the
open notebook's shelf count is its file's while unchanged), `NotebookExport.update` (blank pages at the end left out),
`DrawingNames` (`notebooks`: several names at once; `isoDate` package-wide), `PagePicture.white`.

| File | Lines | Why |
|---|---|---|
| `DrawingBook` | +11 | `appendUnchanged(page, picture)`: a packed page after the last, the current page staying current, not counted as a change (opening a notebook puts its blank page there without rewriting the file). Generic. |
| `PaintActivity` | +90 −70 | `newNotebook` asks the name first (`NamePrompt`); `renameNotebook(path)` and `deleteNotebook(path)` for any notebook (the menu's, the title's and the tile's long press, `notebookMenu`), replacing `renameDrawing` and `deleteDrawing`; a blank notebook is deleted without asking; `openNotebook` puts the blank page at the end (on the store's thread for another notebook); `recovery()` tells `BlankPages.Watch` of each change, `updatePages` of each page shown; Delete page asks only for a page that isn't blank (`deleteNotebookPage`, the page menu's). |

#### Clear page (2026-10-19, branch `inkwash-clearpage`): upstream files touched

The owner: "long press on eraser: option to clear page?". A long press on the side bar's eraser tile shows a Clear page
button beside it for 4 s (the label's place, as Fill page), only on a page with something on it (`unmarked`). It takes
off every layer's marks, hidden ones too, the masking fluid and the magnets as one Undo step, with no question; the
layers, the shade and the page's place stay. New, Scribulor's own: `PageClear` (the words and when it is offered, pure;
`PageClearChecks`: clear then Undo gives the page back byte for byte).

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +33 −9 | `clearPage`: `clearAllLayers` with the magnets too (both share `blankLayers`); `StackState` holds the magnets (immutable, by reference), so one structural step clears and restores them with the layers. A structural step's history bytes are the larger side's layers, not both sides': while undoable it holds the layers it took off (those it put on are the page's own), while redoable the reverse. Clearing a Manta page swaps every layer: the sum (about 49 MB with Erasable's shares) was over the whole 32 MB budget, so the next stroke dropped the clear from Undo; it is about 30 MB now, its old layers held by reference, never copied. Generic: any MonoPaint step that replaces layers. |

Scribulor's own: `InkWashBar` (the eraser tile's long press, `offerClear`: `layerChange`, so wet paint dries first and the
page has one clean refresh after; the note "Page cleared").


#### Finger button (2026-10-19, branch `inkwash-clearpage`): upstream files touched

The owner: "not sure where to set the finger control.. can we use an icon?". Under the smudge tool's tile, a Finger button
(Phosphor's `hand-pointing`, Bold, `@phosphor-icons/core@2.1.1`: `ic_ph_hand_pointing`) marked with what one finger does:
struck through for Nothing, a small eraser, the Smudge part's glyph (`ic_part_carry`) or the slot's number in its tile's
ink badge. A tap opens the same menu as the file menu's Finger row, beside it; both write `FingerTool.KEY`. It is in the
column exactly where Words is (`DrawLayout.finger`, counted with Words): not on the Nomad in landscape, where the menu has
it. One icon more takes the Manta's faces from 58 to 56 dp tall (squares 46 to 44) and the Nomad's squares from 27 to 25
(least 23); `DrawLayoutChecks` checks the fit on all four screens.

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | +10 −5 | `fingerMenu` package-visible, tells the bar when the choice changes; `menu` moved up where it would run off the foot (the Finger button sits low in the column), so it never scrolls. |

#### Notebook covers (2026-10-19, branch `inkwash-covers`): upstream files touched

Stage 1 of the notebook options (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md, "Covers"). Every notebook
has a front and a back cover, drawn on like pages, before the first page and after the last; their paper is a marble made
from the notebook's name (and on the front, a label with the name and, if chosen, a smaller copy of a page). The paper
is a page property again, now a picture: `ToneDocument.paperPixels`, a tone a pixel in place of the paper tone, never
saved with the page nor in Undo (the notebook makes it when the cover is shown). Every composite starts from it as from
the shade; the native compose takes it as one more, fully covering layer under the others, so its C code is unchanged
but for the count. New, Scribulor's own: `CoverMarble` (the marble, pure, seeded from the name, kept in memory and in the
app's cache folder), `NotebookCovers` (the label, the picture's place, the paper, shelf tiles, the PNG key; pure),
`BookOptions` (the options block "INO1"), `CoverLettering` (the label's letters on the device); checks `CoverChecks`,
`OlderCoversCheck` (against 2a352ed), compose_check on a cover.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +40 −8 | `paperPixels`, `placePaper(byte[])` (no Undo step, the page dirty), `PAGE_PAPER` for `baseComposite` (the page's own paper: its shade or its pixels), `visibleLayers` puts the paper pixels first as an opaque layer (`opaque(n)`, one all-255 array kept), the composites start from `paperAt(i)`; the glaze rule of a shaded paper applies on paper pixels too; `Snapshot.paperPixels`, `Snapshot.unmarked`, `Layer.covers`. Pages without paper pixels write, draw and compose as before. Generic: a patterned paper for any page. |
| `DrawingBook` | +170 −60 | `FRONT`/`BACK` places beside the pages (negative, so the page shifts never move them), `after`/`before`, `valid`; the covers' packed bytes (null with nothing drawn: nothing kept), their pictures, the book's `name` for their paper, made when a cover is decoded or turned to (`paperKey` says when to make it again); `cover` is now the cover picture's page (-1 none) and goes when its page is deleted; covers can't be copied or deleted; `Snapshot`: `fileIndex`, `packedCovers`, `filedCovers`, `writtenCovers`, `picture(i)`/`setPicture(i)`. |
| `BookCodec`, `BookPictures` | +60 −10, +100 −20 | The options block between the last page and the notebook block (`BookOptions`), written only when a cover is drawn on or a page is the cover picture; the notebook block's cover is the picture's page or the first page, as older readers expect. `openPages`/`readPages` find it (covers read from the file when needed), `replaced` points the covers into the new file; `readCover` reads the page size, the picture's page and the front cover's marks. Older readers (2a352ed, 580a326 and MonoPaint) never reach it: they stream to the last page or read the notebook block just before the directory, as before. |
| `RecoveryCodec` | +60 −40 | TSR3's covers section after the pictures (older readers stop before it); the header's page is the file's. |
| `ViewportBitmap`, `viewport_bitmap.c` | +6 −4, +5 −4 | Nine layers at most (eight and a cover's paper). |
| `ModularStroke` | +1 −1 | White and Invert read the page over its own paper (`PAGE_PAPER`). |
| `PaintActivity` | +70 −20 | The name to the book (`nameBook`), ‹ › through the covers (`turnPage`), the menu's cover picture rows, the marble made ahead from the first and last page, the lettering and cache folder set at start. |
| `DocumentStore` | +10 −4 | The shelf's `Notebook` keeps the page size and the front cover's marks; `page(path, FRONT)`. |

The PNG copies (`NotebookExport`, Scribulor's own) start with `00-cover.png` and end with `zz-back.png` (the PDF, since
2026-10-24, starts with the front cover and leaves the back out), each carrying `NotebookCovers.key` (marble version, name, label, size, the cover's and the picture page's CRCs) in place of
a page's CRC, so a cover is drawn again only when one of them changes.

#### Cover designs (2026-10-19, branch `inkwash-covers2`): upstream files touched

The covers become composition books (the approved prototype `docs/archive/brush-lab/prototypes/cover-designs.html`), each
notebook in one of nine designs, Classic Black Marble by default. New, Scribulor's own and pure: `CoverNoise` (the
prototype's seeds, hashes, gradient noise and cells, ported exactly), `CoverDesign` (the nine, their keys and fields),
`CoverPattern` (a design's sheet at a size: the lattice shared over `Cores`, the cut by slope, the tape; the cache, in
memory and the cache folder's `covers/`, and `prepare`, moved here from `CoverMarble`), `CoverChooser` (the Cover Design
dialog, UI); `CoverMarble` is now Swirl's suminagashi alone; `NotebookCovers` draws the new label (`Label`, `Words`;
`Lettering` draws words, `CoverLettering` on the device). Checks `CoverDesignChecks` (each design against the prototype's
output, to the bit), `OlderDesignCheck` (against 6e3bae8).

| File | Lines | Why |
|---|---|---|
| `DrawingBook` | +25 −12 | `design()`, `setDesign` (a change to save; the cover drawn on refreshed by its caller, as its pattern may take a while), `restoreDesign`; `paperKey` names the design; `name(String)` (the label is made from the stored name, so the name as shown went); `Snapshot.design`. |
| `BookCodec`, `BookPictures` | +3 −1, +6 −2 | The design into and out of the options block; `readCover` reads it for the shelf. |
| `RecoveryCodec` | +10 −1 | A DSGN section after the covers', only when the design isn't the default (older readers stop before it). |
| `DocumentStore` | +25 −3 | `Notebook.design`; `design(path, design)`: a saved notebook's file opened, its design set, saved again (the shelf's long press on a notebook not open). |
| `PaintActivity` | +60 −10 | Cover Design in the file menu and the shelf's long press (`chooseCoverDesign`, `setCoverDesign`: the pattern made ahead, then the cover drawn on refreshed); `CoverPattern`'s folder and the old `marbles/` folder emptied at start. |
| `DrawingFiles`, `DrawingNames` | +5, +1 −4 | `isoDate` moved to `DrawingFiles` (the label's date; `DrawingNames.isoDate` calls it). |

`BookOptions` (Scribulor's own) gains the `DSGN` record (the design's key in UTF-8), written only when the design isn't
Classic: an undrawn Classic notebook is byte for byte as before. The covers version (6e3bae8) skips the record and opens
such a notebook with its covers; 2a352ed never reads the block. `NotebookCovers.key` (the copies' PNG key) names the
design and `CoverPattern.VERSION` (2).

#### Covers made fast (2026-10-19, branch `inkwash-coverfast`): upstream files touched

The owner: "notebook covers seem to take a while to load..". Each design's pattern is now made once, ahead, by its own
generator (`CoverDesign.sheet`, `scripts/cover_textures.sh` and `CoverTextures`, a desktop maker) into a 1-bit PNG in
`app/src/main/assets/covers/` (1.85 MB for the nine), and a cover is a window of it chosen by the name (`CoverTexture`,
Scribulor's own and pure: the PNG read, averaged-down levels for small covers, the window turned or flipped where the
design allows). The generators stay, as the sheets' makers, the prototype's checks and the fallback for a design without
a sheet. The cache folder's `covers/` is gone (a window is made faster than such a file is read back) and emptied at
start like `marbles/`; `CoverPattern.VERSION` is 3. The shelf finds a tile by what its pictures hold
(`NotebookCovers.shownKey`), not by their arrays, and doesn't build itself again when the notebooks read are as shown.

| File | Lines | Why |
|---|---|---|
| `BookPictures` | +30 | `cover(File)`: `readCover` kept by path, length and time, so a return to the shelf reads no file unchanged and its pictures are the same arrays. |
| `DocumentStore` | +1 −1 | The shelf's listing reads covers through `BookPictures.cover`. |
| `PaintActivity` | +12 −10 | `forgetOldCovers`: the cache folder's `marbles/` and `covers/` emptied at start; no cover folder set. |

#### Zoom and its lock (2026-10-19, branch `inkwash-zoom`): upstream files touched

The owner: "can we add zoom and lock zoom like in monopaint?". MonoPaint's zoom (its TODO.md "Zoom and pan lock", "Faster
pinch and pan preview") was still in `DrawingPad`, built but switched off (`navigationLocked = true` at every start). It
is on again, in Scribulor's bar: a Zoom button under redo (`ZoomButton`: Phosphor's padlock over the percentage, through
the fast feedback path; MonoPaint's `ZoomButtonTouch` on it, so a second quick tap is 100%), locked at first, the choice
kept (`PaintPreferences.navigationLocked`, upstream's). Where the column has no room (`DrawLayout.zoom`, the Nomad in
landscape, which would need 31 dp more) the menu has a "Zoom 100%: Locked" row instead, as it has Words. Pinch and pan,
the 800% limit, pinch inward to fit, new pages and turns starting fitted, the zoomed raster dithered at screen pixels
and the gray presenter kept through a pinch are all MonoPaint's, unchanged; at 100% nothing takes the zoomed path, so
drawing is as fast as before.

What Scribulor adds is how two fingers share the page with its taps and the finger tool. MonoPaint starts navigating at
the second finger's landing, so a two-finger tap (Undo) could also nudge the view (a 103% zoom). Here two fingers are
a tap until one moves past the tap's own slop (18 dp, `MultiFingerTap`'s), and only then navigate, from where they
landed, the tap reset so no Undo fires; a third finger before they move makes a three-finger tap; the finger tool still
sees every touch first and takes its stroke back when the second finger lands (`FingerTool.cancels`), which no longer
keeps the two from navigating. Pure, `PinchPan` (`PinchPanChecks`, with `PageView`: the page's map at 100%, 200% and
800%, panned, all four turns, against MonoPaint's rotate-scale-move).

Zoomed in, the engine draws what it draws at 100%: sizes are page pixels, and every page path (the native area
render, the zoomed raster, the eraser's ring, magnets' outlines and hit tests, the finger tool, the drying ticker, Float,
Mold and gravity redraws, the page shade's paper) already maps through `pageToView`. Print Shading's page-fixed dots are
page pixels in the layers, so the zoomed raster's nearest sampling magnifies them like a printed page. One thing broke:
the pen's speed was read in page pixels, calibrated as screen pixels (`PenSpeed`), so at 800% a hand's usual speed
read as a crawl and every speed-thinned brush went fat (Brush pen +25% ink, Anything goes +78%), the eraser small. A
stroke now takes the zoom (`DrawingStroke.zoom`) and reads speed on the glass: `StrokePoints.zoom`, `PenSpeed.zoom`
(`ZoomStrokeChecks`: within 6% of 100% at 200% and 800%; at 100% byte for byte as before). The bar's size dots still
show the size at 100%, as MonoPaint's do.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +44 −36 | `navigationGesture`: two fingers through `PinchPan` (land, start past the slop, step), a finger's own stroke not blocking it, called after `fingerStroke` so the finger tool sees the second finger first; `historyTap`'s slop is `PinchPan.SLOP_DP`; `place` sets `PageView.map` (the same rotate, scale and move); `updateViewport` tells the bar's Zoom button; a stroke beginning gets `zoom(viewport.scale())`. Generic: the tap-or-pinch rule, for any MonoPaint whose two-finger tap undoes. |
| `PaintActivity` | +15 −17 | `navigationLocked` read from the preferences again (upstream's line); `toggleNavigationLock` through the bar's `ZoomButton` (MonoPaint's toolbar is hidden); the menu's Zoom row where the bar has no room (`fileMenu` builds its rows as lists now, Words' and Zoom's alike). |
| `DrawingStroke` | +6 | `zoom(scale)`, default nothing. Generic: any MonoPaint stroke that follows the pen's speed. |

Scribulor's own: `PinchPan`, `PageView`, `ZoomButton` (new); `DrawLayout` (`Part.ZOOM`, `zoom`; Zoom and Words are
"extras", an icon and a rule each, where they fit: `count`, `total`, `rules` take how many); `InkWashBar` (the button and
its rule in the column, shown where it fits); the engine's `StrokePoints`, `PenSpeed`, `ModularStroke`, `QuickStroke`,
`EraseBothStroke` (`zoom`); `SwatchFace` (its note).

#### Edit buttons, the six slots on the Tools page, no numbers (2026-10-19, branch `inkwash-baredit`): upstream files touched

The owner: "instead of a little tiny caret in the swatch and sizes, let's add a way to customize the set below? also
similarly have a way to load the brushes with brushes from the library.. maybe a big modal with the slots"; "no need to
have numbers by the brushes?"; "lab... 'create new brush' should be an option in there". Scribulor's own: `DrawLayout`
(the two Edit buttons, `Part.EDIT_TOOLS` and `EDIT_SIZES`, `Item.EDIT`; a tray row's second tap does nothing),
`InkWashBar` (the Edit buttons, a tray row's long press, the tray strokes the row's width, the Finger mark and choices
drawn from the tray strokes), `SwatchFace` (no ▾), `SwatchFaces.editAll` and `SizeModal.set` (the whole set, built from
the size modal's `SizeEditor` and the grey flyout's `GreyGrid`, which the two old pickers use too), `SlotTile` and
`LoopCard` (no number badges), `SlotRow` (the six slots' tiles, shared by Brush Lab's `LabLibrary` and the Tools page),
`ToolsPage` (six slots, one selected; Undo across them), `ToolsLayout` (the slots' row), `ToolsActivity` (the slots,
New Brush), `BrushLabActivity` (`FRESH`: opens on a new brush, returns once it is saved), `FingerTool` (a slot's choice
named by its brush). Engine untouched.

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | +72 −33 | `toolsReturned` takes the six slots' brushes the Tools page returns (`slotsReturned`, shared with `brushLabReturned`) and selects its slot, then opens Brush Lab, or Brush Lab on a new brush (`FRESH`), whose return opens the Tools page again; `fingerMenu` names a slot by its brush and gives each row its picture (`InkWashBar.fingerIcon`); `menu` takes drawables (the int overload maps to them) and marks the chosen row with the bar's right-edge bar in place of the ink fill; the file menu's Finger row is `InkWashBar.fingerRow`. |

#### The side bar's two branches together (2026-10-19, branch `inkwash-barmerge`)

`inkwash-zoom` and `inkwash-baredit` merged: one column list (`DrawLayout.COLUMN`) holds everything, top to bottom:
menu, Brush Lab, undo, redo, Zoom, the six pens and their Edit, the four pairs and their Edit, the tools two by two
(Smudge | Fill over Eraser | Finger, Fill's cell measured and hidden until `InkWashBar.fillButton`), Words, the wet
indicator, the page turner. Zoom's "extras" counting (`count`, `total`, `rules` take how many) and baredit's
`Item.EDIT` sit side by side. Where room is short what stays is the pens, the pairs, the tools, undo, redo and the
two Edit buttons; then the page turner (whole before the extras, else folded); then Words, then Zoom (Words is now
fitted first). The two Edit buttons fold the page turner on the Nomad in landscape even without the extras, so
Words and Zoom both fit beside it there (2 dp to spare at the least sizes): on all four screens both are in the bar,
and their menu rows show only on shorter screens. The Zoom button's padlock is `DrawLayout.zoomLock` (16 dp, 13 on
40 dp icon buttons) so it and the percentage keep 2 dp over and under. `DrawLayoutChecks` checks the combined fit
on all four screens and the order of what goes (`priority`). `PaintActivity.fileMenu` keeps zoom's list rows, the
cover rows and baredit's Finger row (`InkWashBar.fingerRow`).

| Screen (dp) | Items / room | Heights (icon, tray row, face, Edit) | Page turner | Words, Zoom |
|---|---|---|---|---|
| Manta 1024×1365 | 1126 / 1345, 219 spare | 56, 30, 34 (28 square), 32 | whole | bar, bar |
| Manta 1365×1024 | 990 / 1004, 14 spare | 48, 28, 33 (27 square), 29 | whole | bar, bar |
| Nomad 749×998 | 962 / 978, 16 spare | 47, 26, 32 (26 square), 27 | whole | bar, bar |
| Nomad 998×749 | 729 / 731, 2 spare | 40, 26, 32 (26 square), 26 | folded | bar, bar |

#### The grey bar (2026-10-19, branch `inkwash-greybar`): upstream files touched

The owner: "for selecting the swatches... can we have a gradient displayed like in monopaint in the sizes and greys
selector? right now it's just grays (and doesn't include black for some)". The Sizes and Greys window's and the grey
flyout's eight fixed greys (`GreyGrid`, `ToolSwatches.FLYOUT`) became MonoPaint's color bar: `GreyBar` (Scribulor's own),
black to white in the panel's dots, a marker under the grey in use, the grey big beside it (`SizeModal.GreyEditor`). It
snaps to the panel's 65 dot densities, MonoPaint's levels (`DotPattern.pickerTone`); a grey swatch keeps any darkness
0–1 as before (`ToolSwatches.darkness`/`tone` map a level to darkness and back). The missing black: a wash's swatch shows
its glaze (`ModularSizes.swatch`, `GLAZE` .34; the engine glazes at `0.34 × shade`), so every wash's eight greys topped
out at 34 % grey; the bar keeps black and white at its ends for every brush, its big grey and words ("Black glaze, 34%
grey") showing what one stroke lays. Engine untouched. The window's geometry is pure now (`SwatchSetLayout`, checked by
`GreyBarChecks` on the four screens: 589 dp tall, 300 wide); its px-and-mm line was 24 px tall, not 24 dp, and clipped
its text on the tablets.

| File | Lines | Why |
|---|---|---|
| `ShadePicker` | +26 −67 | The strip's end zones, tone at a position, marker place and pixels are `ShadeStrip`'s (new, pure); the pen's pointer, the deferred marker frame and the clean pass 250 ms after a lift are `StripMarker`'s (new). Same numbers and order; Scribulor's `GreyBar` uses both. |

#### Washes reach black (2026-10-19, branch `inkwash-washblack`): no upstream files touched

The owner chose to let washes reach black. The six-part engine (Scribulor's own, and its web twin in
`docs/brush-lab/prototypes/modular-lab-src`) glazed every body at `0.34 × shade`; a wash's now lays
`ModularSizes.WASH_BLACK` (.97) of its swatch's darkness where its body is full (`washStrength`), ink's wet body and
opaque paint's as before. Past `ModularSizes.KNEE` (.55) a body's strength eases toward black (`laid`) instead of
clipping at 1, so a black wash keeps its pooled edges, blooms and grain; every body laid before stays under the knee
(.34 × 1.6), so it lays byte for byte as it did. The same `laid` sets compose (`ModularCompose.tones`), Drying's held
back body and what settles (`ModularStroke.held`, `DryingWater`, which settles a cell to its own darkness past .95),
Halo's and Float's greys, the screened preview (`QuickStroke`), and gravity near black (`GravityFlow.deep`: its caps and
a drip's shading rise with the wash, 0 for every wash before). Drying's preview grey follows the glaze
(`ResolvedBrush.previewShade`). Greys kept before stay where they were (`inkwash_grey_`) and draw as they did
(`ModularSizes.sinceBlack`: a wash at .34 / .97 of them, an ink at them); a grey picked since is kept under
`inkwash_dark_` (`ToolSwatches.look`), so nothing is rewritten, an older build reads its keys, and a pen put in a slot
that held a wash still draws at the slot's grey as it would have. Samples and cards show a wash at the old black (`ModularSizes.sampleDarkness`). Codes and files
unchanged.

#### Fill (2026-10-19, branch `inkwash-fill`): upstream files touched

The owner: "fill button beside the smudger ... then it's four tools that would stack nicely". A paint bucket (Phosphor's
`paint-bucket`, Bold, `@phosphor-icons/core@2.1.1`: `ic_ph_paint_bucket`) in the bar's Fill place. While it is on, a pen
tap fills the area there, shut in by the selected pair's crisp ink lines, with the selected slot's brush, on that pair's
wash layer, as one Undo step. New, Scribulor's own and pure: `FillRegion` (the region: the web prototype's walls1 and room1,
section 1 of `docs/archive/brush-lab/prototypes/mischief-lab-2.html`, worked out in 2 × 2 page px cells, about the prototype's
pixel, then drawn at page resolution; a gap closes only where a line ends, so a wobbling wedge's pinch stays open;
pockets chain twice; `FillRegionChecks`, with a timing at the Manta's page size) and `FillTool` (`FillChecks`).

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +38 | `fillToolTap` (with Fill on, the pen's landing marks the spot, its lift fills there; the eraser end erases as ever) and `layFill` (the fill as a stroke: `liftQuick` runs a Slow fill's lift off the pen's thread, else `finishStroke`; the note "No closed area"). Scribulor's own pen path only. |

Scribulor's own: `ModularStroke.fill` (the brush laid over a region rather than along samples: its line's coverage and
its body's paint at a firm, still press; a Fast-wet body settled once as a Drying lift settles it; a Drying fill's lift
skips laying the line and body again; the settled body, relief and line kept to the region, and the settling after lift
lands only there (`DryingWater`'s lands) and doesn't run downhill; no dust), `InkWash` (`filling`, `fill`, and `before`,
what any stroke or fill does first), `InkWashBar` (the button, `tapFill`, Fill off at any other tool's tap).
#### Mirrors and the Fun button (2026-10-19, branch `inkwash-mirrors`): upstream files touched

The owner: "how about a button with the phosphor confetti for the fun options (mirror, magnetic poetry, and any future
weird stuff)"; Mischief Lab 2's section 6, "mirrors is really cool, i love it. perfect, no notes"; and "maybe there should
be a mode where the mirrors remove part of the screen if there was stuff there before? to be like a real mirror placed
on the page". The bar's Words button became **Fun** in the same place (`DrawLayout.Part.FUN`, `fun`; the fit is
Words', unchanged on all four screens), Phosphor's `confetti`, Bold (`@phosphor-icons/core@2.1.1`: `ic_ph_confetti`);
the Mirrors row's icon is Phosphor's `flip-horizontal`, Bold (same version: `ic_ph_flip_horizontal`). Its list
(`FunMenu`, through `PaintActivity.menu`) has a row a mode, Magnetic Poetry (the magnets' bin) and Mirrors (their own
list: Mirrors on or off, Copy Strokes, Real Mirror, Place Mirror, Kaleidoscope, Flip Side, Keep Reflection, Clear Mirrors (now Remove Mirrors, beside Show Guides: `inkwash-mirrorfast`, below), and the
caption "3 mirrors, 6 copies"). Scribulor's own, pure (`test_inkwash.sh`, `MirrorChecks`, `MirrorOlderReaderCheck`
against 2327d14): `Mirrors`, `MirrorStroke`, `MirrorShow`. The engine is untouched: copies are the engine's own
strokes (`ModularStroke.start`, `EraseBothStroke`), made in `InkWash`.

- **The maths** (`Mirrors.Group`): the prototype's, breadth first from each mirror's reflection, maps the same to 1e-4
  in their turn and half a pixel in their move, at most 24 counting the page. Snapping (`Mirrors.fit`): the stroke's
  principal axis, 15° steps within .055 rad, through a crossing of mirrors or the centre within 12 prototype pixels;
  under 24 is "Too short". Prototype pixels are CSS pixels (1/96 inch) scaled to the 300 ppi page (`PX`, 3.125).
- **Copy Strokes** (`MirrorStroke`): while the pen is down only the stroke itself is drawn, unchanged, as fast as
  before. The canvas shows the copies from it (`MirrorShow`): each time the page changes under the stroke, every map's
  image of the change is kept as tones (`ToneDocument.gestureTones`, or the eraser's `erasedTones`) and drawn over the
  render, at most `LIVE_PX` pixels a frame (the farther maps of a big wash wait for the lift; a pen's few pixels show
  all 23). At lift the stroke finishes (its own lift work first) and the copies are drawn one by one, each a live
  stroke of the same tool fed the mapped samples at once (tilt mirrored too), each on the page as the last left it, so
  overlaps lay over each other as two strokes do; `QuickLift` looks in between, one copy a look; a copy wholly off the
  page is culled (since `inkwash-mirrorfast`, below, most brushes' copies are the stroke's change reflected instead). `ToneDocument.linkSince` links every step after the stroke began into one Undo step. A copy's Slow
  wash lays the one before it settled (the session settles one wash at a time); the last keeps settling.
- **Real Mirror** (`Mirrors.View`): the kept point's images, one a map; a pixel nearest image k lies in cell k (each
  image's Voronoi cell, convex, so a row crosses each once and is a few runs found by halving) and shows the page at
  map k's inverse of it. The canvas draws covered cells over its native render (`MirrorShow.draw`, in bands on the
  cores: a whole Manta page's kaleidoscope 20 ms on this Mac, its guides 4 ms); a change under the pen re-renders its
  images too, so drawing shows in the reflection at once. A stroke draws on any side (2026-10-19, branch
  `inkwash-mirror2`; the owner: "allow drawing on either side after placement (and allow drawing 'through' the
  mirror"): `MirrorStroke` folds each sample into the kept cell (`View.fold`, the inverse of the map whose cell holds
  it, tilt turned too), and where the pen crosses into another cell it adds a sample at the crossing (`View.leaves`,
  the segment against each cell's half-planes), pressure, tilt and time between, so the mark meets the mirror and goes
  on as its reflection, one stroke. Across a group's mirror the two folds meet; past the cap (non-dividing angles) they
  may not, and the rest is a stretch of its own, drawn at lift as a copy is, linked into the same Undo step. Keep Reflection (`ToneDocument.keepReflection`) lays the view
  into every layer and the masking fluid, one structural step holding the old layers by reference. Default side: the cell
  with the most marks (`Mirrors.mostInk`); Flip Side (`Mirrors.flipped`) keeps the next cell round, of those whose
  image of the kept point is on the page, sorted round their middle (one mirror: the other side). The pen's stroke
  never picks it, so no stroke is lost to it.
- **Exports**: guides never; Real Mirror's reflection as the page shows it, in `ToneDocument.exportPixels` (the full-size
  PNG) and `PagePicture.Composite` (the notebook's PNG and PDF copies, the shelf's pictures). Not in the dot-size export
  (`ViewportBitmap.dithered`, native) nor the layer thumbnails.
- **Fill** (since `inkwash-fillmirror`, below): with Copy Strokes each image's area (`Mirrors.Group.points`), with Real Mirror the tap folded into the kept side.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +105 −5 | `mirrors()`, `placeMirrors` (from a file), `showMirrors` (on or off: no step), `setMirrors` (a structural step: `StackState` keeps the mirrors, `restore` keeps on or off as it is), `keepReflection`, `gestureTones`, `lastStep`/`linkSince`; `Snapshot.mirrors`, `unmarked` counts them; `exportPixels` shows Real Mirror's view. Generic but `keepReflection`: any per-page guide kept in the history, a gesture's changed pixels, steps linked after the fact. |
| `DocumentCodec` | +20 −5 | TSM5's `MIRR` section (`Mirrors.encode`: version, on, style, the kept point, the lines), written only when a page has mirrors; older readers skip it (checked against 2327d14); pages without mirrors write as before, byte for byte. |
| `DrawingPad` | +65 −6 | `mirrorShow`, its areas in `renderDirty` beside the magnets', `drawMirrors` in `render` and `renderAll` before the magnets; `placing` (the pen's next stroke a `MirrorShow.Place`); the eraser checks through `own(stroke)` (a `MirrorStroke`'s primary); the live copies begun with the stroke and ended at its finish. |
| `PaintActivity` | +30 −10 | `fun` (`FunMenu`); the menu's Fun row where the bar has no Fun button; `menu` marks any rows (`boolean[]`) and takes a caption under them. |
| `PagePicture` | +20 −5 | `Composite` reads Real Mirror's view (its source pixel a row). |

| Screen (dp) | Items / room | Page turner | Fun, Zoom |
|---|---|---|---|
| Manta 1024×1365 | 1126 / 1345, 219 spare | whole | bar, bar |
| Manta 1365×1024 | 990 / 1004, 14 spare | whole | bar, bar |
| Nomad 749×998 | 962 / 978, 16 spare | whole | bar, bar |
| Nomad 998×749 | 729 / 731, 2 spare | folded | bar, bar |

#### Mirrors fast, Show Guides, Remove Mirrors (2026-10-19, branch `inkwash-mirrorfast`): upstream files touched

The owner, on the Manta: "after drawing a long line in kaleidoscope mode it got really sluggish and the marks didn't
show for a few seconds... seems to happen also with just two mirrors. just one mirror is fine. we should add a way to
hide the mirror dotted lines." Each Copy Strokes copy was a real stroke drawn again at lift, one a look on the UI
thread: as long as the stroke itself, so a kaleidoscope's five cost five strokes.

- **Copies laid as the stroke's change reflected** (`ToneDocument.darkeningSince`, `layAgain`; `MirrorStroke`): for a
  brush whose compose only darkens what it finds by a factor of the mark's own (`ModularCompose.darkensBy`: its own
  Blend, no cover, relief, Screen, resist or smudge, not a charcoal stick) and that is final at lift with nothing that
  reads which way is down (`ModularStroke.laysAgain`: no settling after lift, Halo, Float, dust or gravity), the
  stroke's one Undo step is read as each pixel's factor (its tone over white after over before; NaN where it found
  black), and each copy is a gesture of its own that darkens what it finds by its source pixel's factor through
  `setTones`, the stroke's permanent share kept (`owns` from the factor), in bands of tile rows on the cores. Where a
  map carries pixel centres onto pixel centres (a mirror on the grid) a copy reads one pixel: the stroke reflected,
  exactly; where it turns (the kaleidoscope's 60° and 120°) it reads between pixels, Catmull-Rom at an edge (kept
  within the four nearest, so no ringing) and bilinear elsewhere. Only blocks (16 px) whose source holds a change are
  looked at. Over other marks it darkens them as a copy drawn again does (exact for a mark that multiplies). Not with
  masking fluid on the page (the stroke's write was shielded where it went), nor for opaque paint (Gouache), a smudge,
  the eraser, Halo, Float or a wash that settles after lift: those copies are drawn again as before. The default
  Fineliner, Brush pen, Pencil, Ink wash and Watercolor qualify. What is approximate: the copy's paper grain and the
  brush's noise are the stroke's, reflected, not the page's at the copy (Pencil's grain shows it: on the grid 9 % of
  its pixels differ by more than 8 greys from a copy drawn again); turned copies differ from copies drawn again by
  3.8 greys on average for the 6 px Fineliner (edge pixels up to about 50), 0.1 for a 120 px wash; what the eraser
  leaves of a reflected copy is the stroke's within a grey. Checked: `MirrorChecks.reflected`.
- **Lift, desktop, a 2033 px stroke, drawn again / reflected, ms** (the Manta about 20 times these), mirrors through
  the centre (three: the kaleidoscope): Fineliner 6 px 5/1.7, 13/3.1, 22/5.0, 27/4.8; Brush pen 30 px 6/1.9, 17/3.9,
  24/6.0, 33/6.7; Watercolor 120 px 14/7.9, 35/11.5, 52/16.2, 67/17.4. So a kaleidoscope's pen lifts in about 100 ms on
  the Manta (was 450), its 120 px wash in about 330 (was 1000). `MirrorChecks.timings` bounds the kaleidoscope's
  reflected lift at 60 ms for pens and 150 for the wash.
- **Rendered apart** (`MirrorStroke.areas`, `DrawingPad.renderDirty(also, apart)`): the reflected copies' boxes and
  the stroke's last part are rendered and refreshed each apart, not as one box round them all (a kaleidoscope's round
  the page's middle); a short stroke far out renders a tenth of the area. Only while they hold everything changed:
  anything past their box renders the one box as before.
- **Live copies** (`MirrorShow.take`): a copy looks only where the change's 16 px blocks that hold a mark land, and
  asks to render only the box it lit; the budget (`LIVE_PX`) counts what it looks at. A pen's frame now shows every
  copy (the cap's 23 too, where four of a kaleidoscope's five showed for a 200 px box before), at about the same cost.
- **Real Mirror** is unchanged: under the pen a frame costs 0.4 to 1.3 ms here with one to four mirrors, a whole page
  about 20 ms.
- **Show Guides** (`Mirrors.guides`, `ToneDocument.showGuides`, `DrawLayout.MirrorRow.GUIDES`): the dashes shown or
  not, the mirrors acting all the same; per page, in its MIRR section beside on or off (no Undo step, kept by Undo and
  Remove Mirrors). A page with its guides showing writes the section as before (version 1, byte for byte); hidden, it
  writes version 2 with the flag, which the version before skips (the page opens there without mirrors).
- **Remove Mirrors**: Clear Mirrors' new label (the row's key stays `CLEAR`); with Copy Strokes it keeps the drawing,
  with Real Mirror the page shows as it really is again, beside Keep Reflection, which lays what is seen.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +180 −2 | `Darkening`, `darkeningSince`, `layAgain` (a gesture's change as each pixel's factor, laid again under an affine map); `showGuides`; `StackState.restore` keeps the guides' setting as on or off. Generic: any stroke's change laid again elsewhere. |
| `DrawingPad` | +20 −3 | `renderDirty(also, apart)`: a finished `MirrorStroke`'s areas rendered apart. |

#### Fill in mirrors (2026-10-20, branch `inkwash-fillmirror`): no upstream files touched

The plan's note under Mirrors made real: a Fill tap with mirrors on fills as the mirrors treat a stroke, one Undo step.
Scribulor's own, pure: `FillTool.Areas` (where a tap fills, area by area), `FillTool.lay`, `FillTool.seen`,
`FillTool.keptSide`; `MirrorStroke`'s fill mode; `InkWash.fill` through them. `FillRegion` is untouched. Checked:
`FillMirrorChecks`.

- **Copy Strokes**: the area at the tap and at each of its images (`Mirrors.Group.points`), each a fill of its own on
  the page as the last left it, byte for byte the fills tapped one after another without mirrors (a stroke's copies are
  likewise each laid on the page as the last left it). The tap's area is found and laid at once, on the UI thread as
  before; the images' at lift, one a look (`MirrorStroke(document, primary, Copies)`: each `Copies.start` finds the next
  area and starts its fill, null when none is left), so `QuickLift` shows them in turn and `ToneDocument.linkSince`
  makes them one Undo step. Passed by: an image off the page, on a line (a pixel darker than `FillRegion.LINE`), or in
  an area already found, so an area that holds several images (a box across the mirror, a ring round a kaleidoscope's
  centre) is found once and filled once. A tap on open paper still fills its images' areas; "No closed area" only when
  none has one. Not reflected as a stroke's change is (`layAgain`): an image's area needn't be the tap's reflected.
- **Real Mirror**: no copies, as for a stroke; the tap folds into the kept side (`View.fold`, as a stroke's samples do)
  and its area is found in the page as shown: the walls are the kept side's ink through the view (`FillTool.seen`, a
  block's source pixels read as one box round them), so a shape the mirror closes (lines running up to it) holds the
  fill, and marks hidden under the mirror don't. The area is kept to the kept side (`FillTool.keptSide`; a box whose
  corners are all kept is left as it is), which the view shows on every side. Before, a tap on a covered side filled the
  hidden page there.
- **Timing** (`FillMirrorChecks.timing`, a 1404 × 1872 page, desktop): a kaleidoscope's six rings of radius 120 px in
  28 ms, one ring 5 ms, so each image costs what a tap there would (the Manta about 20×, spread over the lift's looks);
  bounded at six and a half rings' time.

#### The Tools page in four sections (2026-10-19, branch `inkwash-toolspage`): upstream files touched

The owner: "the slots should be the main focus... move the test area to the top and the current brush below that, then
the slots, then the library display. each section should be labeled clearly.. Remove "roll another". lets use the
toolbar stroke look for all of the previews. to show that a slot is active, have a line around it. it can be blinking
slowly?"; then "back button should be clearly visible.. sometimes it gets lost". Scribulor's own: `ToolsLayout` (the four
sections and their headers), `ToolsActivity` (the order, the headers, the tray previews, the blink), `ToolsPage` (no
Roll another; `slotCaption`), `SlotRow.cards` (the Tools page's six slots as cards), `LoopCard` (a name first in the
caption, wrapped; the outline's colour), `LabLibrary.traySampler`, `LabUi.header`, `LabButton.back` and
`LabLayout.backWidth` (Back with its word, on Brush Lab's bar too; Brush Lab's Save is its icon alone on the Nomad to
make room). Engine untouched.

| File | Lines | Why |
|---|---|---|
| `SelectionFeedback` | +23 −8 | `update(View, Rect[], Runnable)` and a `fast` flag on `present`: the selected slot's outline fades through the panel's partial update on its four sides only, in the grey mode, so no pen-plane ink is left behind when the slot is no longer selected. |

#### Fading and Lemon Juice (2026-10-19, branch `inkwash-fade`): upstream files touched

Spec `docs/archive/superpowers/specs/2026-10-19-mischief-modules-design.md`, Stage 3 (`mischief-lab.html`'s section 3). Engine
(Scribulor's own, pure): `PartChoice.FADE` and `LEMON` (Drying 5 and 6) with `PartDial.FADE_IN` and `DRY_IN`; `FadingInk`
(new: a mark's shares by the clock, the mottle, the e-ink steps, the file's section); `ResolvedBrush` (`fade`,
`asQuick`); `ModularStroke` (its Undo steps become the mark at `finish`; not `laysAgain`, so mirrored copies are strokes
that fade as their own); `ModularStyle` (`rollFade`, `fadeRolls`, `lemonRolls`); `ModularFeatures.seedOnly`;
`ModularWords`; `SurpriseTools.fancy`; `names/traits.txt` (fade, juice). UI (Scribulor's own): `DryingTicker` steps the
page's marks when due, each on its own partial refresh, beside a settling wash and never while the page is wet; nothing
is posted without them.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +204 −7 | `fadeSince` (the Undo steps since a mark become a `FadingInk.Mark`: its tiles before and as laid), `fade(now, shown)` and `stepFade` (each pixel still showing what the mark last showed shows its share now, and so does every Undo and Redo step's copy of it on that layer, so both give it as the clock has it; a step left changing nothing leaves the history, its link passed on), `rewet`, `fading`, `nextFade`, `fades`, `placeFades`, `Snapshot.fades`; `own(layer)` out of `writable` (any layer's arrays copied from a snapshot before a write). A page without marks writes, draws and undoes exactly as before. Generic: any mark that changes by the clock. |
| `DocumentCodec` | +24 −6 | TSM5's `FADE` section (the live marks, `FadingInk.encode`), written only while some; a reader without it opens the page as it showed. |
| `DrawingPad` | +13 −1 | `fadeToNow`: a page shown (turned to, a drawing opened) steps its marks to the clock before it is drawn, and starts the ticker; `pauseDrying`. |
| `PaintActivity` | +1 −1 | `onPause` stops the ticker (`pauseDrying`); `onResume`'s `resumeDrying` starts it again. |

## Notebook options (2026-10-19, branch `inkwash-nbopts`)

A template's guides under each page, a per-notebook toolbar and the options sheet
(`docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md`). The engine is untouched; the upstream files below take small,
generic additions, and a page or notebook with no options writes, draws and composes exactly as before.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +40 −4 | `guides`/`placeGuides` (a template's guides, a tone a pixel; no Undo step, the page dirty), `guided(guides, paper)` (the darker of guide and shade, one made and kept), `pixels()` (a cover's paper pixels, else the guides on the shade) in place of `paperPixels` in `paperAt` and `visibleLayers`, `Snapshot.guides`/`shownPaper()`. `paperPixels()` stays cover-only (Fill Page is still offered on a guided page); the glaze rule is unchanged. Pages without guides write, draw and compose as before. Generic: a printed ground under any page. |
| `PagePicture`, `ViewportBitmap` | +2, +2 −1 | `Composite.printed` (the copies' composite on the guided paper); the screen's compose lays `shownPaper()` under the layers. A page's picture stays marks and paper, with no guides. |
| `ToolSlots`, `ToolSwatches` | +85, +15 | `Toolbar` (the eight slots with their sizes and greys, as lines), `toolbar`/`load`/`enter`/`leave`/`appWide` (a notebook's own toolbar loaded over the app's, which waits under `tool_appwide`), `keepLook`. The slots, library and recent brushes are unchanged for a notebook with none. |
| `DrawingBook`, `BookCodec`, `BookPictures`, `DocumentStore`, `RecoveryCodec` | +60 | The notebook's `NotebookOptions` (kept as `TMPL`, `GPDF`, `TOOL` records of the options block, only what isn't the default; a recovery section; the shelf's reading), guides placed on each page decoded, `Snapshot.printed` for the copies. |


## Stamp (2026-10-19, branch `inkwash-stamp`)

A Tip whose nib is a piece of the page cut with a lasso (`docs/archive/brush-lab/prototypes/mischief-lab-2.html`, section 2). Engine
(Scribulor's own, pure): new `StampNib` (the cut, trimmed; the nib store over `BrushLibrary.Store`, keyed by a hash of the
nib's pixels) and `StampStamper` (the prints, through the stroke's own buffers and compose, so every medium, Print
Shading, Erasable and drying lay them); `PartChoice.STAMP` (Tip choice 10), `PartDial.STAMP_SPACING` and `INK_FADE`;
`ModularBrush.nib`, which `ModularCode` writes as a seventh segment only for a Stamp with a nib (every other code byte
for byte); `ResolvedBrush.Pattern` holds a Stamp as it holds Stipple and Hatch; `HeldPaint` keeps each holder's ink;
`MirrorShow.Place` gains the lasso (marching ants), which `StrokePanel` sends through the fast update; `BrushBackup`
keeps nibs beside the library's file. `LabLayout.swapColumns` gives three rows when two would make a tile too narrow
for a tip's name (the Nomad's eleven Tip tiles in Basic).

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +25 −12 | `Placing.CUT` and `cutting`: the pen's next stroke is the lasso (the placing stroke, dashed), shown through the fast update; its end goes to `PaintActivity.stampCut`. `drawShow`, the mirrors' overlay drawing made static so Brush Lab's test page draws its lasso the same way. |
| `PaintActivity` | +26 | `cutStamp` (from the Tools page's Cut Stamp) and `stampCut`: the cut nib kept and the slot's brush given it, or "Nothing to cut" beside the slot. |

## Crayon (2026-10-20, branch `inkwash-crayon`)

A Medium of wax on toothy paper (`docs/archive/brush-lab/prototypes/mischief-lab-2.html`, section 4). Engine (Scribulor's own,
pure): new `Crayon` (the page-fixed tooth and ragged edge, the line's press, the wax over what is there, its share of
the masking fluid, Scratch's blade); `PartChoice.CRAYON` (Medium choice 7), `PartDial.WAX` and `SCRATCH`;
`ResolvedBrush.Rule.CRAYON`, `wax`, `scratch`, `waxes()`, `stick()` (the stick test `QuickStroke` and `ModularCompose`
shared), `Pen.crayon`; `LineStamper` lays a crayon's ragged edge and press; `ModularCompose.ink` its wax, and
`waxOnMask` composes it onto the masking fluid as `layWax` already does for a wax resist (`ModularStroke`, one Undo
step). Scratch is the crayon as a thin blade laid with Blend's White. `StickGrain.tileNoise` is shared. Every older code
is byte for byte; no roll picks it.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +9 −1 | `takesOnWax`: for the gesture, only the masking fluid the eraser can lift shields its paint, so a crayon builds up over its own wax (kept fluid) and Scratch cuts through it. Off at every `begin`. Generic: any paint that takes on wax. |
## Spiro (2026-10-20, branch `inkwash-spiro`)

A Tip drawing the spirograph toy's curves (`docs/archive/brush-lab/prototypes/mischief-lab-2.html`, section 3). Engine
(Scribulor's own, pure): new `SpiroStamper` (both modes, every curve, a fine antialiased line through the stroke's own
buffers and compose, so every medium, Print Shading, Erasable and drying lay it); `PartChoice.SPIRO` (Tip choice 11) and
its dials (`SPIRO_MODE`, `CURVE`, `LOOPS`, `TEETH`, `WHEEL`, `HOLE`, `PETALS`, `RATIO`, `PHASE`, `FADES`); the hand's last
three dials, `P_DRIVE`, `S_DRIVE`, `T_DRIVE` (`ModularCode.ADDED`: written only when changed, so every other code is
byte for byte; kept when a Control preset is swapped); `ResolvedBrush.Pattern` holds a Spiro as it holds a Stamp;
`ModularStroke.stampNew` (split from `add`) stamps again at the lift for a Spiro's ring; `QuickStroke` never sends a
Spiro quick (its preview would follow the pen, not the ring). Scribulor UI only: `BrushLab.dials` hides the dials a curve
doesn't use, `LabSettings` adds the drives to the Control card for a Spiro (`LabLayout.handCell(rows)`, `handRow`). No
upstream file touched.

## Spiro's ring guide (2026-10-20, branch `inkwash-spiroguide`)

In Ring, the pinned ring as a light grey outline over the page from the touch-down to the lift, never in the page
(mischief-lab-2.html's guide, minimal: no wheel, no tap animation). Engine (pure): `SpiroStamper.guide` (the ring's
radius at the touch-down's pressure and tilt) behind `ModularStroke.ringGuide`. The outline is `MirrorShow.ring`, an
overlay drawn once into the same tiles as Copy Strokes' live copies and the placing stroke, rendered with the page's
dirty areas and taken away by the `MirrorShow.end()` every lift already calls; PNG, PDF and Undo never see it. Brush
Lab's test page draws it through its own `MirrorShow` (`BrushTestPad`, Scribulor's).

| File | Lines | Why |
|---|---|---|
| `DrawingStroke` | +6 | Default `ringGuide(pressure, tiltX, tiltY)`, 0: only a Spiro in Ring answers (`MirrorStroke` and `QuickStroke` pass it on). |
| `DrawingPad` | +37 | `ringGuide`: after a pen's or finger's first sample, a Spiro's ring into `mirrorShow` round `samplePoint`, shown at once through a fast session of its own (`ringDirect`, `StrokePanel.open`, as the lasso's), which the stroke's gray updates leave be; `endStrokePreview` ends it at the lift as a fast preview (`endFast`: the driver drives its pixels again); `disconnectDisplay` closes it. |

Brush Lab's test page (`BrushTestPad`, Scribulor's) does the same with `ringFast`.

## Spiro's ring pull (2026-10-23, branch `spiro-ring`)

In Ring the pen's first move out from the pin sizes the ring (the owner: "could it be possible to set the size of the
circle at the beginning somehow?"). Engine (pure): `SpiroStamper.Pull` follows the samples (the farthest distance once
past `PIN`, until the pen has gone `PULL_TURN`, 25°, round or come back in by `REV`); the stamper inks nothing while it
lasts, then sets the ring's radius from it (`ringShare`: the toy's ring, the epitrochoid's inside its wheel) and starts
the wheel the way the pull went. A tap, and a move that never leaves the pin, still draw the whole figure at the
brush's size; Line is unchanged. The guide is no longer drawn into `MirrorShow`'s overlay tiles: `MirrorShow` keeps the
ring (centre, radius, its own `Pull` fed by `ringTo`) and draws it dashed per pixel, asking to render again only boxes
round its arcs as it grows. Web twins: `newmods.js` (`spiroPull`, `spiroShare`), `ui.js` (the guide follows, dashed),
`voice.js`, `modular-lab.html` patched, studio rebuilt.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +12 −2 | `sampleEvent` and `fingerSample` feed each sample to `mirrorShow.ringTo`; `presentInput` shows a pulled ring through `ringDirect` (`presentRing`, split from `ringGuide`). |

`BrushTestPad` (Scribulor's) does the same in `sample` and `presentRing`.

## Spiro's ring set up as a shape (2026-10-26, branch `spiro-taps`)

Replaces the pull above (the owner: the shape tool's way). On the drawing screen the pen's first stroke with a Spiro in
Ring sets the ring up: its touch-down pins the centre, the drag sizes the ring (the guide's radius the pen's distance,
out and back in; within `PIN` the brush's own), nothing is inked, and the lift sets it, the guide staying. The pen's
next stroke with that tool draws, and goes in with the setup first: the centre, then the rim with no pressure, two and
one `StrokeLog.MOST_PAUSE` before its first sample, so the stroke log replays it unchanged. Engine (pure):
`SpiroStamper.Pull` and `PULL_TURN` are gone; `rim` (the radius a rim sets, 0 within `PIN`) serves the stamper and
`MirrorShow.ringTo`; the stamper takes a second sample of no pressure, `PIN` or more out, as the rim, else the ring is
the brush's size (a finger's stroke, Brush Lab's test page, a stroke logged before). A setup that never leaves the pin
is a tap: the whole figure at the brush's size. Any other stroke, a tool or page change, a panel, or Undo (which takes
back only the setup) drops it. Web twins not changed.

| File | Lines | Why |
|---|---|---|
| `DrawingPad` | +80 −8 | `RingSetup` (a `DrawingStroke` that inks nothing, as `MirrorShow.Place`), `ringSet`, `ringPrefix`, `feed` (split from `sampleEvent`), `tapRing`, `dropRing`; `beginInkStroke` starts or consumes a setup; `finishStroke` keeps its guide. |
| `MirrorShow` | +8 −6 | `ringTo` follows the distance; `beginCopies` keeps a ring set up. |
| `InkWashBar`, `PaintActivity` | +4 | A tool change and Undo drop the setup. |
| `WildSample` | +4 | A Ring's loop tile is a tap: the whole rosette mid-tile. |

`BrushTestPad` no longer follows the pen: its ring is the brush's size, pinned where the pen lands.

Speed (2026-10-26, branch `spiro-speed`): `MirrorShow.ringTo` only moves the radius; `take` queues the ring's arcs, as
last shown and as now, once a render (`ringShown`, `ringArcs`). Before, each sample queued both rings' arcs (some 100
boxes, each rendered on its own), so a batch's samples multiplied the work and a setup's drag left input 20 s behind on
the Manta. No upstream file touched.

Ghosts (2026-10-26, branch `spiro-ghost`): `MirrorShow.end` says whether a ring went. `DrawingPad` (+3 −2): the guide
gone at a figure's lift (`finishStroke`) or dropped (`dropRing`, with `refreshSoon`) marks the page `ghosted`, so the
next auto-refresh is the clean flash; the guide's redraws through the fast session left its old radii on the panel.

Effect trail ghost (2026-11-03, branch `effect-trail-ghost`): `MirrorShow.end` says a trail went too, so an effect
brush's stroke end marks the page `ghosted`, as the eraser's does. The owner, Newsprint and Photocopier: "a little grey
area on where the white was ... when i flash it it's gone". The end already rendered and presented the trail's whole
box again (white in the framebuffer); the panel's grey update leaves the trail's dots taken off over paper part-driven,
and the driver passes a pixel asked for the value it records, so only the clean flash clears them. No upstream file
touched. Check: `AreaChecks.trailEnds`.
## Spiro's oval and bar tracks (2026-10-26, branch `spiro-tracks`)

The owner: "on oval and rectangle etc would be cool". `PartDial.SPIRO_MODE` gains Oval and Bar (values 2 and 3; Line
and Ring, and every code, as they were). Engine (pure): `SpiroStamper.Track`, the toy's oval and bar rings: an ellipse,
or a rectangle with its corners rounded to a quarter of its shorter side, sampled along its line (2048 points: where,
how far along, which way it faces, its angle round the centre). The setup goes corner to corner as a shape's does
(`box`, the stamper's rim sample and the guide both); within the pin, the brush's own size, 0.6 as tall. The pen's
angle round the centre finds the point on the track; the wheel's centre keeps its radius in from it, and its spin is
how far it has rolled less the line's turning (`spin`; the epitrochoid rolls outside). Wheels are sized to the track's
tightest bend as the ring's are to its radius (radius ≤ that bend, so a Bar's corners never stop it), and the track
has a whole 24 of teeth, so a figure closes after the wheel's teeth over their common factor with the track's laps.
The other curves are stretched to the track's box. Ring is byte for byte as before (`ringShare` stays the Ring's).

| File | Lines | Why |
|---|---|---|
| `DrawingStroke` | +2 | Default `ringMode()`, 1: the shape a `ringGuide` takes (`MirrorStroke`, `QuickStroke`, `FilledShape` pass it on; `ModularStroke` answers). |
| `MirrorShow` | +55 −30 | `ring(…, mode)`; the guide is a track (`ringNow`, `ringShown`: centre and halves), `ringTo` follows `SpiroStamper.box`, `arcs` and `ringAt` draw an oval or a Bar's rounded rectangle (`SpiroStamper.edge`); the ring's arithmetic unchanged. |
| `DrawingPad` | +3 −2 | `RingSetup.ringMode`; `ringGuide` passes the mode. |

`BrushTestPad` (Scribulor's) passes the mode too. Web twins not changed.
## Grey Shift (2026-10-20, branch `inkwash-swatches`)

A Control option picking a brush's grey at each touch-down (`docs/archive/brush-lab/prototypes/mischief-lab-2.html`, section 5,
Shifting swatches). Engine (Scribulor's own, pure): new `SwatchPicker` (the rules Random, From a Set and Pattern, In Order
or Ping-Pong; Toolbar's greys, the bar's `ToolSwatches`, or Any, the panel's 16; each palette's set as the greys left
out); the hand's last six dials, `SHIFT`, `SHIFT_ORDER`, `SHIFT_GREYS`, `SHIFT_BAR`, `SHIFT_ANY`, `SHIFT_ANY2`
(`PartDial.SHIFTS`, after a Spiro's drives; `ModularCode.ADDED`: written only when on, so every other code is byte for
byte; kept when a Control preset is swapped); `HeldPaint.shifting` keeps each holder's turn while the app runs;
`ModularWords.plainWords` says it. Scribulor UI only: `InkWash.stroke` and `fill` pick at the touch-down (a toolbar grey
becomes the slot's, `ToolSwatches.chooseGrey`), `InkWashBar.heldChanged` marks it after the stroke (`SwatchBar.show`,
only when it changed); `TestArea` picks for Brush Lab's and the Tools page's test page (`Host.swatches`);
`BrushLab.handRows`, `LabLayout.handRows` and `menu(PartDial, String[])` (a Control row's options beside it or in a
menu), `LabSettings` and the new `GreySet` view (the set's swatches), `BrushLab.toggleGrey`. No upstream file touched.
## Stay in Lines (2026-10-20, branch `inkwash-stayin`)

A brush whose wet paint keeps inside the crisp ink lines (`docs/archive/brush-lab/prototypes/mischief-lab-2.html`, section 1).
Engine (Scribulor's own, pure): `PartChoice.STAY_IN` (Smudge choice 5) and `PartDial.FLOOD`; `ResolvedBrush.stayIn` (no
smudge then); new `StayIn`, the stroke's shape and flood: the shape is Fill's `FillRegion` (a new bounded `find`, its
open cells within 640 page px of pen-down, so Fill's own find is as it was), the flood the prototype's bucket queue at
page resolution, at most 2000 pixels a sample. `ModularStroke` finds the shape at its first sample (off the pen's thread
when it has a session and doesn't draw on the ink layer itself; waited for at the lift), keeps its line, body and relief
to it at every compose as a fill's room (`filled` now tells a fill from it), paints the flood into its body as composed
(Fast-wet's shown body, else the body as laid; laid again with a Slow lift's body), and runs the flood to its end at
the lift. `QuickStroke` never sends it quick. No upstream file touched.

## Gradient (2026-10-20, branch `inkwash-gradient`, on `inkwash-swatches`)

A Control option changing a brush's grey along each stroke, by distance drawn (the owner's "fading color (or gradient
shift)"). Engine (Scribulor's own, pure): new `GreyGradient` (Fade to paper, To Grey to an End Grey of the panel's 16,
Cycle through Grey Shift's set from the stroke's grey, round and round, smoothstep between stops; a Length of whole cm
at `SpiroStamper.PX_CM`); the hand's three dials after Grey Shift's, `GRADIENT`, `GRAD_TO`, `GRAD_LEN`
(`PartDial.GRADIENTS`; `ModularCode.ADDED`: written only when on, every other code byte for byte; kept when a Control
preset is swapped; never rolled). How it reaches the dabs: `ModularStroke` resolves the brush at the darkest grey the
stroke reaches (`GreyGradient.ref`) and each kept sample's share of it goes into `StrokePoints.own` (`apply`, after
`LoadedPaint.Run.spend`, multiplying its share), which every stamper already multiplies its dabs' darkness by (line,
body, placed, hake, bristles, stipple, hatch, stamp, spiro); a wash's share is the ratio of `ModularSizes.washStrength`,
any other mark's the ratio of greys, so the compose (and `compose.h`) is untouched. `QuickStroke`'s preview re-shades
its sweeps a panel grey at a time (`Sweep.shade`, a `ResolvedBrush` per grey it passes, made once). Ink, Wash, Charcoal,
Graphite and Crayon (`GreyGradient.takes`); not Opaque (it covers at its own grey), Resist, Screentone or Float.
`FillTool.brush` turns it off. `WatercolorSession.swatches` carries the toolbar's greys to a Cycle on Toolbar (set by
`InkWash` and `TestArea`). Scribulor UI only: `BrushLab.handRows(b, gradient)` (Gradient, a To Grey's End Grey, Length,
a Cycle's Greys and set when Grey Shift doesn't show them), `LabLayout.handRows` (where there's no room: Gradient's own
rows first, then its row, then Grey Shift's), `ModularWords.plainWords`. `GradientChecks`. No upstream file touched.

## Record and Replay (2026-10-20, branch `inkwash-replay2`): upstream files touched

`mischief-lab-3.html` section 1 (owner: "GREAT, build"). The pen's brush strokes are logged per page and the page can
draw itself again. New, Scribulor's own: `StrokeLog` (pure: the log, its bytes, the player that feeds strokes back a chunk
of pen time at a time), `PageReplay` (the Replay page action: plays on a blank copy of the page, a step per refresh,
then shows the page as it was; any touch ends it), `StrokeLogChecks`. The log is not an Undo step: each entry is tied to
its step by `ToneDocument.Edit.serial`, so Undo and Redo hide and show strokes, a new stroke drops the undone ones, and
Clear page leaves a marker. Not logged: eraser, smudge, fill, shapes, finger, a pen held still (Stipple's Build-up),
mirror copies; a page past 60,000 samples stops logging.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +25 | `log`/`replay()`/`logStroke`/`placeLog`; `Edit.serial`; `Snapshot.log` (the visible strokes); the log is cut where `redo` is cleared; `clearPage` and `clearAllLayers` leave a marker. |
| `DocumentCodec` | +16 −3 | TSM5's `RPLY` section (`StrokeLog.encode`), written only when the page has strokes; an older reader skips it (unknown tag), a log that doesn't read is left out. |
| `DrawingPad` | +10 | Records the pen's samples for an ink stroke (`recording`), logs it at the lift if it changed the page; any touch during a replay stops it. |
| `PaintActivity` | +6 −2 | `replay`, busy during a replay, stopped in `onPause`, a Replay page item in the page menu. |
| `InkWash` | +14 | `recorder` (brush code, size, grey of the stroke just made) and `replay(...)`, the stroke a replay draws. |
## Daily Prompt (2026-10-20, branch `inkwash-prompt`, on `inkwash-v2-lab`)
Owner: "good, build" (Mischief Lab 3, section 3). Engine (Scribulor's own, pure): new `DailyPrompt` (32 cards of our own, the
day number picks today's, `another` picks a different one, `heading`) and a fifth `NotebookTemplate`, `DIARY_PAGE` ("diarypage",
Diary Page: three boxes down the left and a big drawing box, as guides; no labels drawn). Scribulor UI only: one file-menu row,
Daily Prompt (`PaintActivity.fileMenu`, `dailyPrompt`), a dialog with Deal Another; Diary Page shows in the existing Template
list of Notebook Options. No countdown yet. `DailyPromptChecks`. Upstream: `PaintActivity` (a menu row and one method); no file
format change beyond a new template key (an older version reads it as Blank).

### Record and Replay, follow-up (2026-10-20, branch `inkwash-replay3`)

`StrokeLog.added` drops the oldest strokes at `MOST_SAMPLES` instead of refusing the new one (a stroke past the cap alone
is still not kept); undone strokes are already gone by then, so Undo, Redo and Clear page behave as before. Speed chips:
`PageReplay.choose` shows 1×, 2×, 4× through `InkWashBar.chips` (the label's place, level with the eraser) and a tap
calls `start(speed)`, which scales the pen time per step. `PaintActivity`'s page menu item calls `choose`. No other
upstream file touched. Exporting a replay is not built.
### Replay fix (2026-10-25, branch `replay-fix`)

`DrawingPad.onTouchEvent` ended a running replay on any event, a resting palm's moves too, so it stopped as it began.
It now asks `PageReplay.ends` (a press, `StrokeLog.endsReplay`: the pen, or a finger no broader than
`FingerTool.PALM_DP`). `PageReplay.choose(anchor)` shows the speeds through `PaintActivity.menu` beside the menu's
anchor (as Play's Mirrors); `InkWashBar.chips` is gone. `StrokeLogChecks.ends` checks the rule. On the bench app the
replay itself ran and reached the panel (read back through `DirectEink.readGray`); `adb screencap` doesn't show a
direct e-ink present, so screenshots there look unchanged.
### Replay's veil (2026-10-26, branch `replay-blob`)

The owner saw "a random blob (a previous drawing)" during a replay. The log keeps strokes whose marks the eraser or the
Lasso (not logged) has since taken away or moved; the replay laid them again where drawn (the owner's notebook, page 48:
21 filled shapes lassoed elsewhere, 411k px of blobs). `GhostPlane.offMarks` builds an over plane of the paper wherever
the page shows no mark; `PageReplay` keeps it for its copy and `Ghosts.plane` hands it to the pad for that copy (the
slot Tracing's plane takes for the book's page), so the replay shows only what the page holds. `StrokeLogChecks.veil`.
No upstream file touched.
### Daily Prompt follow-up (2026-10-20, branch `inkwash-prompt2`)
`DailyPrompt.clock` and the card's Start/Stop/Time in `PaintActivity.dailyPrompt` (a postDelayed tick of 1 s on the pad; not
`TimedPages`, which locks pages). A started Diary Page card calls `setNotebookOptions` with the Diary Page template (notebook-wide,
guides are paper). `NotebookTemplate.DIARY_PAGE` draws DID, SAW, HEARD with a 3 x 5 dot font. `DailyPromptChecks` extended.
### Six more Print Shading choices (2026-10-20, branch `inkwash-prints`)
`PartDial.SCREEN` gains Rosette, Ben-Day, Crosshatch, Wavy Lines, Mezzotint and Diamond Dots as options 5 to 10 (still one base-36
digit, so old codes read and write as before), and `PartDial.PATTERN` (Screentone) gains Crosshatch, Wavy Lines and Mezzotint as 4 to 6.
`PrintScreen.Kind` takes the new kinds in the same order (the screen kinds before `DOTS`, Tone's after `GRADIENT`, so
`values()[screen - 1]` and `DOTS.ordinal() + pattern` stay right) and `threshold` the six formulas; Screentone's twins share them.
Still a threshold at each page pixel, so the compose, the print tone, the eraser and the quick path are untouched; the native
renderer (`compose.h`) has no print path (the screen is decided in Java before it), so there is nothing to keep equal.
Ben-Day evens its threshold through a table measured at class load. `ModularWords` (names, tags), `BrushLab.active` (Angle greyed only
for Dither and Grain), `ModularStyle.rollScreen` (one Screen roll in `NEW_SCREEN_ODDS` = 6 takes an appended print, own salt, so a
Screen's rate and every other roll are unchanged; Tone's roll still picks the first four patterns). `ScreenChecks` runs every loop over
the ten, plus `newPrints` (coverage, nesting, Angle, difference, Screentone twins, page-fixed). Web lab (`modular-lab-src/newmods.js`,
`modular-lab.html` patched with the same hunks) and Brush Studio (rebuilt) carry the same names, codes, thresholds and roll.

### An eraser that takes one kind of mark (2026-10-20, branch `inkwash-erasekind`)
Spec `docs/archive/superpowers/specs/2026-10-20-erase-kind-design.md`. Upstream files touched: `ToneDocument` (+153 −53: `Layer.kind`/`kinds`/`kindBase`, a third
plane beside `share` and `print` with the same plumbing: `copy`, copy-on-write, `bind`, `expand`, `reflected`, tile captures `beforeKind`/`foundKinds`,
`TileEdit`, `restoreTile`, `restoreSpan`, Undo bytes; `begin(erasable, kinds)`; the two write sinks `tones` and `store` add the gesture's kind bit
(`laid`); `eraseKinds`, `takes`, `overrides` in `eraseTone`/`eraseTones`; `compositeToneKept(…, kinds)`), `DocumentCodec` (`KIND` section, +47), `InkWashLayers.cropped`.
Scribulor-side: `ResolvedBrush.kind` (by Medium), `ModularStroke.begin`, `DryingWater.lay`, `HaloGrowth`, `FloatWater.floats` (the wet continuations
begin with the stroke's kind), `EraseBothStroke` (limited by `kinds`, leaves masking fluid), `EraseKind` (the four choices), `InkWash.eraseKinds`,
`InkWashBar.eraserMenu` (the long press). MonoPaint's tools begin with kind 0 and tag nothing. The native renderer only shows the page: nothing to
keep equal. Checks: `EraseKindChecks` (56) and `OlderKindCheck` (the document classes from `e288fe9` skip the section); `PageShadeChecks` pins the
white page's file crc again (the section is new in it; pixels, picture and PNG unchanged).

### Kind layers: each kind of mark on a plane of its own (2026-10-27, branch `kind-layers`)
Spec `docs/superpowers/specs/2026-10-27-kind-layers-design.md` (step 1, the engine; step 2's show/hide row is UI only). Upstream files
touched: `ToneDocument` (`Layer.planes`, each plane a `Layer`, made lazily by `begin(erasable, kinds)` through `planeOf`; `KIND_OPAQUE`;
`stacked`/`stackSize`/`stackOwner`/`stackOpacity`, the one composite order every composite walks: per pair the bases, then the planes kind
by kind; `beginPlane`, `hasPlane`, `selectKind`/`selectedKinds` and the `selected` read target; `setKindOpacity`; Undo finds a plane's layer
(`ownerOf`, `address`/`at`); `copy`/`release`/`held`/`each` carry planes; `layerDarkness`, `layerTone`, `layerPixels` read a layer's look,
`layerPixels/layerKept(index, kinds, …)` one plane; `sheets`/`flattened`), `DocumentCodec` (`PLNS` section, the look in the layers' part),
`ViewportBitmap` and `viewport_bitmap.c` (43 arrays, was 11), `ShapePreview` and `layers.h` (40), `GhostPlane`, `PagePicture`,
`InkWashLayers` (`merge`, `resized`), `NotebookMigration`, `FadingInk` (a plane's address in `FADE`). Scribulor-side: `ResolvedBrush.medium`
(Cover lays `PAINT|OPAQUE`), `ModularStroke.kinds`/`prepare` (a smudge with no paint of its own works on its Medium's plane), `QuickStroke`
(the preview on the stroke's plane), `EraseBothStroke` (plane by plane, the Pen plane live), `LassoPlanes` (base and four planes a half),
`WetCanvas` (one plane), `WetPaintStroke`, `WatercolorSession.joiningKinds`, `FloatWater` (the bath's first kind), `HaloGrowth`
(`foundComposite(…, kinds, …)`), `CarryPlane` (a plane of the other half). The native renderer composites more arrays, by the same rule.
Checks: `KindLayersChecks` (60: order, exact erase where pen crosses pencil, Undo, an old file byte for byte, a new one's planes, the Lasso,
a stump, kind opacity); `EraseKindChecks` rewritten for planes; checks that read a layer's pixels read its look (`Layer.flattened`,
`layerPixels`) and lay their setups on the plane the brush works on; `PageShadeChecks` pins the white page's file crc again (pixels,
picture and PNG unchanged); `FillChecks`' strip goldens taken again from the fill laid in one piece.

### Export Page: a page's PDF, PNG or Layers (2026-10-28, branch `page-export`)
Upstream files touched: `PaintActivity` (+11, `exportPage`, as `exportNow`). New, Scribulor's own and pure: `PageExport` (one page's copy, named "<notebook> p5.psd") and
`PsdWriter` (a Grayscale PSD of grey layers, PackBits, with its merged image); `PageExportChecks` reads the PSD back.
Scribulor-side: `NotebookExport` (`greys` split out of `png`, unchanged output; `flatten`, and `layers`, `hidden`, `said`,
`stackKey` opened to the package), `NotebookPdf.write(…, from)` (the first page's number; the notebook's PDF unchanged),
`PagePicture.Composite.printed(page, from, to)` (a run of the stack's places alone), `Magnets.stamp` (over a clear
pixel too, for the Magnets layer), `AutoExport.page`, `NotebookScreens` (Page Settings'
foot), `LibraryCard.EXPORT_PAGE`.

### Tracing paper, Stage 1: the page bar's previews (2026-10-20, branch `inkwash-paper1`)
Spec `docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md`, §12 Stage 1. On the drawing screen the page bar's ‹ › go:
`PageTurner` now holds the Notebook button, the page and its count (padlock and blink as before) and a strip of tiny live
previews of the front cover, the pages and the back cover, a window round the page drawn on (`PageTurner.Strip`). Tap goes,
a quick drag riffles (the page's picture over the page area, `PaintActivity.showPanelAt`, nothing decoded; the window slides
one page every 150 ms at an end), a hold then a drag moves the page, a hold released opens its options (stage 1: its
number). Inside a notebook (`NotebookScreens`) the bar keeps ‹ ›. New, Scribulor's own and pure: `StripGesture` (the
thresholds, tiles, landing); `StripGestureChecks`. Scribulor-side: `PageTurner`, `DrawLayout` (preview sizes,
`stripPlaces`, `previewAt`, `previews`; `pageBarUsed`/`pageBarPlaces` went), `NotebookLayout.window` (shared with the
notebook's strip), `NotebookScreens.drawPlace`/`drawPreview` (previews from pictures in the page's dots, made on its
"page pictures" worker; the page drawn on is never copied for one), `PagePicture.dots`, `LayerThumbnail.renderPage` (the
drawing page's preview, sampled after a stroke settles, `InkWashBar.THUMBNAILS_MS`), `InkWashBar.pageChanged`.
`NotebookFileChecks.moved`, `DrawLayoutChecks.pageBar` rewritten.

| File | Lines | Why |
|---|---|---|
| `DrawingBook` | +30 | `move(from, to)`: the packed page, picture and canvas choice move; the decoded pages and the three revision maps are re-keyed (`moved(i, from, to)`, as `shiftPages` does); the cover picture's page and `index` follow; one book edit. Page bytes untouched, so the PNG copies are kept by CRC. Generic. |
| `PaintActivity` | +22 −6 | `movePage` (busy-guarded, `updatePages`, `recoveryNow`); `showPanelAt` split out of `showPagePanel` (a panel at given bounds); `updatePanel`; `closePagePanel(false)` hides it without its own update when a page turn follows. |

### Tracing paper, Stage 2: Tracing ghosts (2026-10-20, branch `inkwash-paper2`)
Spec `docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md`, §12 Stage 2. A notebook's **Paper** (Plain, Tracing; Glass
is read and kept but shows as Plain until stage 3), each page's step and a pinned ghost. In Tracing the page before (or the
ghost) shows Half (44 %) and the page after Faint (18 %) under the page drawn on, in the page's own dots. New, Scribulor's own
and pure: `TracingPaper` (the Paper, ghost, steps; which pages ghost and how strong; the records' bytes) and `GhostPlane`
(the ghosts flattened, a byte a pixel: how much a black layer laid over the paper covers; built on the cores); `TracingChecks`.
Android-side: `Ghosts` (the plane kept by `DrawingBook.ghostKey`, built on its own worker on the quiet cores; a page turn
waits up to 300 ms for it, else it follows in one more full update; a Paper/step/ghost/order change rebuilds 300 ms after the
last tap; never under the pen), `PageTurner` (the Paper button: tap cycles, hold shows the Papers; the options popover's
steps and Ghost; a ghost's grey tab, a Hidden page dimmed), `DrawLayout.STRIP_BUTTONS`/`GHOST_TAB`, `PaintActivity.setTracing`
(a book edit, free under the lock and the rules), `ic_ph_file`. The ghosts are the screen's alone: the page, its file, PNG/PDF,
`PagePicture`, Fill, the eyedropper and Replay (another document shown, so no plane) never see them. Measured on this Mac at
1920 × 2560: the Tracing plane 53 ms (decoding the page after is most of it); the Manta's time is for the device.

| File | Lines | Why |
|---|---|---|
| `DrawingBook` | +60 | `paper`, `ghost`, `steps` (kept beside `pictures`; moved, copied and removed with their pages; `ghost` follows its page as `cover` does), `tracing()`/`setTracing` (one book edit)/`restoreTracing`, `ghostKey`, `ghostSources`, `contentKey` (shared with the cover's `paperKey`); `Snapshot.tracing`. |
| `BookOptions`, `BookCodec` | +20, +2 | `PAPM`, `GHST`, `STEP` records, each only when not the default (a Plain notebook writes byte for byte as before); `Covers.tracing`. Older versions skip them and open the notebook Plain (`OlderOptionsCheck` against af06002). |
| `RecoveryCodec` | +17 | A trailing `PAPER` section (TSRP) after RULES, only when not the default; older readers stop before it. |
| `ToneDocument`, `ViewportBitmap` | +7, +6 | `visibleLayers(…, ghost)`: the plane as a black layer right over the paper (guides and a cover's paper under it); the area compose takes it. Arrays one longer. |
| `viewport_bitmap.c` | +4 −4 | `nativeCompose` takes 10 layers (8, the paper pixels, the ghost). |
| `DrawingPad`, `MirrorShow` | +20, +4 | `ghost` (taken at `showPage`, cleared by `replace`), `showGhost`; the eraser's preview and Real Mirror's reflection on the ghost (`GhostPlane.over`). |
### Tracing paper, Stage 3b: layers are pages (2026-10-21, branch `inkwash-layerpages`)
Spec `docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md`, "Stage 3b". `StackPanel`/`StackLayout` become the
Layers panel (a row a page: ≡, preview, eye, hold menu; New Layer at the foot); `StepIcon`, `InkWashLayers.movedFrom`/
`marked` and `ToneDocument.layerCovers` are deleted. New: `InkWashLayers.merge` (every pair, hidden ones too, flattened
into one ink layer), `TracingPaper.strength`/`withStrength` and the `PSTR` record (`BookOptions`) and `TSRS` recovery
section (`RecoveryCodec`), Notebook Options' Layers Show Through row (`NotebookLayout`'s chips 32 dp and gap 3 dp so eight
rows fit the Nomad turned), `PaintActivity.newLayer`/`duplicateLayer`/`deleteLayer`/`mergeLayers`. Fix:
`NotebookScreens.drawPreview` keys its cache by size, so the strip and the panel stop remaking each other's pictures.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +8 −2 | `replaceLayers` (Merge Old Layers as one structural undo step, as `restack`); `layerCovers` gone. |
| `DrawingBook` | +3 −3 | Tracing paper's default strength kept beside the Paper. |
| `RecoveryCodec` | +7 −3 | The `TSRS` section: the default strength when Faint; older readers stop before it. |

### Tracing paper, Stage 3: Glass, the Stack panel, layer steps (2026-10-20, branch `inkwash-stack`)
Spec `docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md`, §12 Stage 3. **Glass**: every other page in stack order under
the page drawn on, each at its step (Half unset), Hidden left out, flattened into the same one plane (`GhostPlane`, a page at
a time from its bytes). **Layer steps**: Half and Faint per pair, view-only bits beside the page step (`STEP`'s second byte,
already written and validated by stage 2); Hidden stays the layer's own undoable visible flag. On the drawing page each pair
but the one drawn on shows at its step; on a ghost page, page × layer. New, Scribulor's own: `StackLayout` (pure: sizes, rows,
the window, words; `StackLayoutChecks`), `StackPanel` (the "Pages and Layers" panel above the page bar: page and layer
rows, ≡ drag, steps, ✎, New Layer, a layer's hold menu, Paper chips), `StepIcon` (a step as a grey swatch), `ic_ghost`,
`ic_ph_stack_simple`; `LabButton.iconChip` (an icon chip whose word is its content description and its hold's tooltip).
`InkWashLayers.moveTo` (any place, one `restack`, one undo step), `movedFrom`, `marked`; `TracingPaper` Glass, `shownStep`,
`layerBits`/`layerStep`/`withLayerStep`/`withLayers` (steps follow a pair moved, added or deleted in the panel);
`PaintActivity.layerEdit` (shared by the panel and Clear page), `layerSteps`. `InkWashBar`'s layer column (`layerGroup`,
`folded`, `sizeLayers`, `LayerThumb`, the layer panel) and `DrawLayout.LAYERS`/`Item.THUMB`/`collapsed`/the layer count are
deleted; `DrawLayout(width, height)`. MonoPaint's `LayersPanel` stays untouched: only its `Toolbar` rail reaches it, never
shown in Scribulor (`showInkWashBar`). Measured on this Mac (`NotebookBench`, 1920 × 2400 pages from their bytes): the Glass plane 717 ms for 20
pages, 3.7 s for 100 (Tracing 74 ms). At the Manta's usual ×20 that misses the spec's 1 s for 20 pages, so the per-page
flat cache (§4) is the next step, for the owner to approve; until then a Glass page shows first and its plane follows.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +12 −2 | `visibleLayers(…, ghost, layerSteps)`: each pair's opacity times its step but the pair drawn on's; `layerCovers` (Delete Layer under No Erasing). |
| `ViewportBitmap` | +2 −2 | The area compose passes the layer steps. |
| `DrawingBook` | +4 −2 | `layerSteps(page)`; a ghost's layer bits in `ghostKey` and its `GhostPlane.Source`. |
| `DrawingPad` | +14 −4 | `layerSteps` (taken at `showPage`, cleared by `replace`), `showSteps`; `showGhost` and it share `showAgain`. |
### Rubbing a cover's design off (2026-10-20, branch `inkwash-covererase`)
Spec `docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md`, "Rubbing the design off". The Everything eraser takes a
notebook cover's printed pattern where it goes; the tape, label and cover picture stay. New, Scribulor's own and pure:
`CoverWear` (where it is rubbed, a bit a pixel; `with`, `scaled`, `apply`, `encode`/`decode`); `CoverWearChecks`, and
`OlderWearCheck` (the classes from `ca133f7` skip the section and the picture's tail). Scribulor-side: `NotebookCovers.paper(…,
bare)` (white under the tape, picture and label), `NotebookCovers.once` (the cover picture read once), `shown` rubs the
tile, `NotebookExport.cover` and `DrawingBook.placePaper` hand the bare paper's maker over. A page that isn't a cover, and
a cover never rubbed, writes, draws, composes and undoes exactly as before.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +57 −11 | `wear`/`placeWear`, `placePaper(pixels, bare)` (the bare paper made when first needed), `rubs`, `rubDesign` (an Undo step joined to the stroke's, only its area redrawn), `repaper` (`paperPixels` is the made paper with the bare one where rubbed, a new array each time), `StackState.wear` (Undo, Redo; `Edit` counts its bytes), `Snapshot.wear`, `unmarked` (a rubbed cover is not), `clearPage` (brings the design back), `compositeToneKept` (the Everything eraser's preview on the bare paper). Generic: a ground under the layers that an eraser can wear. |
| `DocumentCodec` | +19 −5 | TSM5's `WORN` section, written only when rubbed; a reader without it skips it. |
| `EraseBothStroke` | +6 −1 | `finish`: with no kinds, on a page that `rubs`, the stroke's coverage rubs the design (after the layer is restored, so Undo restores it too). |
| `PagePicture` | +22 −10 | A cover's marks picture keeps the wear at its size, after its deflated greys (`decode` finds where they end); `white` is false when rubbed. |

### One grey strip in the side bar (2026-10-21, branch `inkwash-greystrip`)
The owner's request: the side bar's four grey faces become one thin strip beside the four sizes. All Scribulor's own, no
upstream file touched. New: `GreyStrip` (the view: `ShadeStrip`'s pixels stood up, four marks, the one in use bolder; a
tap snaps to a mark within `ShadeStrip.SNAP_MM`, else sets the nearest mark's grey; a long press opens Sizes and Greys;
a rub loads), `GreyRub` (the rub-to-load gesture, moved out of `SwatchFace` so the face and the strip share it).
`ShadeStrip.nearest`, `SNAP_MM`, `SIDE_WIDTH`/`SIDE_REACH` (pure), checked in `GreyBarChecks.sideStrip` on the four
screens. `SwatchBar` lays out the sizes' column and the strip (same widths and heights as the pairs, so `DrawLayout` is
unchanged) and redraws only when what they show changes; `SwatchFaces(…, sizesOnly, …)` gives it no grey faces. Same
storage (`ToolSwatches.chooseGrey`/`setGrey`). `InkWashBar` unchanged: `SwatchBar.grey(i)` is the strip.

### Bar pass: Undo and Redo in a row, Zoom on the page bar, eyes for steps, Phosphor icons (2026-10-21, branch `inkwash-barpass`)
The owner's requests; all Scribulor's own, no upstream engine file touched. `DrawLayout`: the column loses `LAB`, `REDO`
and `ZOOM` (`UNDO` is undo and redo side by side, each `half()` wide), one grey rule, Fun the only extra (`zoom`,
`zoomLock`, `ZOOM_ROOM` gone); the page bar's parts are Notebook, Layers, Zoom (`zoomButton()`, its padlock beside
800% in captions), the number and the previews (`stripWidths`, `stripPlaces`). `PageTurner` owns the `ZoomButton`
(same `ZoomButtonTouch`; `InkWashBar.zoom` is it) and loses the Paper button and its hold (`PAPER_SAID`,
`PAPER_ICONS`, `papers`); `STEP_ICONS` (Phosphor eye Fill, Duotone, Light, eye-closed) are used by the strip's page
options, `StackPanel`'s eye and Notebook Options' Faint chip. The file menu's Zoom row (`PinchPan.row`) is gone: the
button is always on the bar. Brush Lab opens from the Tools page only. `SwatchFace.pair`'s bar is at the face's
left; `GreyStrip` marks the grey in use with a dot right of the strip (`ShadeStrip.SIDE_DOT`, `sideStrip`, checked in
`GreyBarChecks`). Drawables: MonoPaint's own icons replaced by Phosphor Bold ones (`ic_ph_*`) where Phosphor has the
same thing, and unused ones deleted; brush, tip, smudge, part, gradient-fill, oval and cover-picture drawings kept.
### A crisp page and the library card inside a notebook (2026-10-21, branch `inkwash-libcard`)
The owner's notebook-screen design pass (option C, "Library card"; section 1, "From the page, at screen size"). All
Scribulor's own, no upstream file touched. `PagePicture.shown(page, tiles, w, h)`: the page box-averaged straight to its box's
size through `Composite.printed` (the guides under the marks), the private `of` given the size; `NotebookScreens` makes it
off the UI thread for the page shown big and its neighbours (`crisps`, an LRU of about three, emptied on leaving the
screen), draws it 1:1 and shows the small picture until it is ready; `mask(PagePicture)` is shared with the small
pictures' masks. New pure `LibraryCard` (rows, values, defaults, which taps step, layout sizes); `NotebookScreens` draws it
(the page's box when `viewing == CARD`, a tile after the front cover in the strip, never a `DrawingBook` place) and
routes taps to the existing setters (`setNotebookOptions`, `setTracing`, `setCoverDesign`) and long presses to
`notebookOptions` / `chooseCoverDesign`. No file records. `NotebookLayout.DRAW` is `OPEN` ("Open"); a tap on the strip's
selected preview opens it. Checked in `CrispPageChecks` and `LibraryCardChecks`. `PageTurner` unchanged: on the card it is
shown the front cover.

### Templates, Random per page, Brushes, editable prompts, the sheet's design pass (2026-10-21, branch `inkwash-notebookopts`)
All Scribulor's own; no upstream file touched beyond `DrawingBook` and `BookCodec`, which already carry Scribulor's notebook
fields. `NotebookTemplate` is a class now (keys unchanged; a key it doesn't know is Blank, as before): nine more drawn
templates, own templates keyed `my:<file name>` read from Supernote's MyStyle through `NotebookTemplate.images` (set by
`PaintActivity`: `BitmapFactory`, scaled to the page; `tones` turns ARGB into the panel's greys), and Random keyed
`random[/key...]`. `shared` keeps the last four guides made (a Random notebook's pages take turns), an own template's only
while its file is there. `DrawingBook` keeps a template key a page beside the tracing steps (`templates`: moved, copied and
deleted with its page, filled under Random by `dice`; `template(i)`, `restorePageTemplates`, `Snapshot.pageTemplates`),
written as the options block's `PTPL` record and the recovery's `TSRT` section, each only when a page has one;
`NotebookExport.update` takes the keys so a page's PNG is keyed and drawn by its own template. `NotebookOptions.oneBrush`
is gone: `brushes` (record `BRSH`; `ONEB` is still read, as a Library toolbar of the one brush with Brushes 1) and
`Tools.EACH_PAGE` (`ToolSlots.forPage` rolls a page's own Surprise Me, seeded by the first page's lines and the page
number; `PaintActivity.syncToolbar` on every page change). `DailyPrompt` reads `Scribulor prompts.txt` beside the notebooks
(`DrawingStorage.shared()`), writing `DEFAULTS` when it is missing or holds no card. `NotebookOptionsSheet` and its sizes in
`NotebookLayout` redone: sections, a label column, one line of chips a row. Checked in `TemplateChecks` (new),
`NotebookRulesChecks`, `NotebookOptionsChecks`, `LibraryCardChecks`, `NotebookNamesChecks` and `DailyPromptChecks`.
Older versions skip `PTPL` and `BRSH` and read Random and own templates as Blank, Each Page as My Toolbar.

### The stamp tool (2026-10-21, branch `inkwash-stamptool`)
All Scribulor's own; no upstream file touched beyond `DrawingPad` and `PaintActivity` (comments only: `cutting` and
`cutStamp` take the stamp tool's index as they take a slot's). No new engine: the tool holds a Stamp-tipped brush drawn by
`StampStamper`, and Cut Stamp is the drawing screen's existing lasso (`DrawingPad.Placing.CUT`, `PaintActivity.stampCut`,
`StampNib.cut`, the one cut Brush Lab's test page also calls). `ToolSlots.STAMP` (after the smudge tool; `TOOLS` is 11),
`STAMP_BRUSH` (`tool_stamp`), `STAMP_KEY`, `stamp()`, `firstStamp()` (the first slot's Fineliner with a Stamp tip): `brush`,
`key`, `set` and `change` take it as a slot, so `use` and Undo work unchanged; it is outside the notebook `Toolbar` and a
frozen toolbar, like the smudge tool. `NotebookRules.refuses` and `smudgeOff` treat it as the smudge tool (off under
Brushes). `DrawLayout.GRID_ROWS` is 3 (the stamp tool alone in the third row) and `greys` includes it. `InkWashBar`: the
row, `ic_ph_stamp` (Phosphor Bold), and the long press's `stampMenu` (a `LoopCard`, Cut Stamp, chips for `HEAD_TURN`,
`STAMP_SPACING`, `INK_FADE` in `PartChoice.STAMP`'s labels and `PartDial.shown`). Checked in `ToolSlotsChecks`,
`NotebookRulesChecks` and `DrawLayoutChecks`.

### The library card as the one settings place; Fun beside the stamp tool; Random per page (2026-10-21, branch `inkwash-cardonly`)
`NotebookOptionsSheet` and `CoverChooser` are gone, with `NotebookLayout`'s sheet and chooser sizes, `NamePrompt`'s Options
button (`More`, `roomForMore`), `NotebookNames.field(options)` / `suggestion(i, options)` / `follow`, `DocumentStore.options`
and `PaintActivity.notebookOptions` / `chooseCoverDesign` / `toolsAfterOpen`. Settings change only on the open notebook, so
nothing rewrites a closed notebook's file for them. New Android `CardSelector` (a row's own screen over the notebook
screen, its pictures on `NotebookScreens`' worker; grid from the pure `NotebookLayout.pick`); pure `LibraryCard` now says
what a tap does (`tap`: toggle, choose, show, fixed), `toggled`, each screen's `choices` / `chosen`, `showThrough`, and its
notes. `NotebookScreens.showCard`; `tapCard` routes to toggles, `CardSelector` or a note. Upstream files touched:
`PaintActivity` (`chooseTools`, moved from the sheet; `showSettings` and `leaveDrawing`; `openNotebook(path, card)`; the
file menu's and the shelf menu's Settings row in place of Cover Design and Notebook Options; `newNotebook` opens the card;
`describeName` says a new notebook's options in its generated name at the first save, `NotebookNames.named`; the menu's Fun
row gone), `DrawingBook` (`newTemplate(at)` avoids its neighbours' templates through `NotebookTemplate.pick(dice, beside...)`)
and `DocumentStore` (one method less). `DrawLayout`: Fun in the stamp tool's row, so `Part.FUN`, `fun`, the extras
(`EXTRA_ICONS`, `EXTRA_RULES`, `extras()`, `count(extras)`, `rules(extras)`) are gone. Checked in `LibraryCardChecks`,
`TemplateChecks`, `DrawLayoutChecks`, `NotebookRulesChecks`, `NotebookNamesChecks`.

### Timed Pages in seconds, its countdown; Brushes and Timed Pages as sliders; a page's prompt; Notebook Explorer (2026-10-21, branch `inkwash-timerpass`)
`NotebookOptions.minutes` / `MINUTES` / `withMinutes` are now `seconds` / `SECONDS` (Off, 30 s steps to 5 min, minutes to
an hour) / `withSeconds` (snapped to the nearest stop). `BookOptions` adds `TSEC` (`TIMED_SECONDS`: two bytes), read over
`TIMD`, which is still written as the nearest old minute choice (`minutesBytes`) for builds before; `readMinutes` reads any
1 to 60. `RecoveryCodec`'s rules keep the two-byte seconds in TIMD's slot (`readMinutes` then `readSeconds`, each by its
length). `TimedPages` and `NotebookRules` (blink window, `timeLabel` in place of `minutesLabel`) take seconds;
`NotebookRules.countdown` / `nextCountdown` give the side bar's m:ss and its next change, which `TimedPages.nextLook` now
includes. `DrawLayout.Part.TIMER` (24 dp, `DrawLayout.TIMER`, kept on every screen) heads the column; `InkWashBar.countdown`
updates it through `SelectionFeedback` and waits for the pen, as the page bar's blink does. A page's Daily Prompt card is a
page section, `PRMT` in `DocumentCodec` (UTF-8 `DailyPrompt.line`, skipped by readers before it), held by
`ToneDocument.prompt` / `setPrompt` (a change to save, not Undo; a page with one isn't `unmarked`). `LibraryCard.slides`
picks `CardSelector`'s slider screen (the existing `BandSlider`, set on Back) for Brushes and Timed Pages. Upstream files
touched: `PaintActivity` (`timedLook` shows the countdown and `promptLook`; `dailyPrompt` gives the page its card,
`promptCard` is the window; the file menu's Notebook Explorer, `NotebookLayout.OPEN_NOTEBOOK`), `ToneDocument`, `DocumentCodec`;
`InkWashBar`'s menu, Undo and Redo have no button background, the menu's icon `ic_ph_dots_three_circle` (Phosphor Bold's
ring, three dots). Checked in `NotebookRulesChecks` (seconds round-trip, a TIMD-only file, the recovery, the countdown),
`DailyPromptChecks` (the PRMT section), `DrawLayoutChecks`, `LibraryCardChecks`, `NotebookNamesChecks`.

### The library card's look; its screens; a page's own template; the day last added to (2026-10-21, branch `inkwash-carddesign`)
The owner's "could look nicer" pass. New pure `CardArt`: a `Pen` interface in dp (fills, dot greys from `DotPattern`,
frames, rules, the pocket's notch, text in four faces measured as `LabText` measures them, icons, pictures, turns), and the
shared pieces (the stamped double rule, a rubber stamp inked or faint, the check, a choice card, a slider). New Android
`CanvasPen` draws a `Pen` on a `Canvas` (shapes without smoothing, `DotGray` tiles, `Theme.face`/monospace, the
`ic_ph_*` icons; its `ArtView` draws an art in dp). `LibraryCard` is rewritten: `Layout` (the largest text that fits,
the name in two lines or one, the borrowers' lines in one column or two, the stamps in a row or two by two, the slack to
the swatch first), `draw`, `at` (the part tapped), `tap`/`screen`; the rows, `choices`, `toggled` and `rowAt` are gone.
New pure `CardSheet` holds each screen's title, line, choices, notes, icons, sliders and layout (`NotebookLayout.pick`,
`pickWidth`, `pickHeight`, `PICK_*`, `NOTE_LINES`, `PRINT`, `LEAVE_OUT` are gone). `CardSelector` places a `Card` view a
choice (said to a screen reader) and a `Slider` view on the sheet's boxes; it no longer uses `BandSlider` (Brush Lab
still does). `NotebookScreens`: the card through `CanvasPen`, its template swatch made on the worker; the strip's
`GestureDetector` (a hold on the front cover: Cover Design; on a page: `PAGE_TEMPLATE`).
A page's own template is a `PTPL` line `"="` + key (`NotebookOptions.OWN`, `ownKey`; `pageTemplate` reads it first);
older builds read it as Blank under Random and pass it by otherwise. `DrawingBook.setPageTemplate`; the notebook's own
template (or Random) puts a page back. The day last added to: `BookOptions` `LAST` (an int of epoch days, skipped by
older builds), `RecoveryCodec` `TSRL`; `DrawingBook` counts page edits (`pageEdits`; covers don't count),
`pagesChanged`, `lastAdded` (today while a page is unsaved), `restoreLastDay`, `today` (a check's clock; -1 keeps none).
Not the file's mtime: the one-time passes (`randomCoversOnce`, `plainPagesOnce`) rewrote every notebook, and a setting
saves the file too. A file from before it says the made day alone until a page changes. `NotebookTemplate.fill` clips
x to the row (a small Hex picture ran off the array). Upstream files touched: `PaintActivity` (`setPageTemplate`; the
file menu's Guides in PDF row, marked while printing), `DrawingBook`, `BookOptions`, `BookCodec`, `RecoveryCodec`,
`NotebookTemplate`. Display renames: `TracingPaper.TRANSPARENCY` "Page Transparency", `NotebookRules.LOCK` "Lock
Settings". New icon `ic_ph_check` (Phosphor Bold). Checked in `LibraryCardChecks` (fit on both tablets in both turns,
every word 12 sp or more inside its box, the in-use fill and check, parts hit where drawn, `LAST` and the recovery, a
page's own template), `NotebookRulesChecks`; `CoverChecks.book` keeps no day, `SaveLessChecks` expects the `LAST` record.

### Timed Sections on the Diary Page (2026-10-21, branch `inkwash-timedsections`)
`TimedSections` (pure, immutable) holds a page's sections: each one's seconds and wall-clock end, `start`, `skip` (the
running one ends now, the rest follow on in full), `active`, `remaining`, `locked` (a day, `LOCK_MS`, after its end),
`nextChange`, and `draw`, the screen's overlay (the running box outlined `band` px in ink, the rest dimmed to `DIM`, a
flashing box's outline inverted). `NotebookTemplate` gains `sections` / `sectionSeconds` / `timed()` / `sectionRects`
(the Diary Page's four boxes; its guides now drawn from them). `DailyPrompt.sectionTimes` reads "Diary Page times | Did
2:30 | ..." from the prompts file (a times line is never a card; the defaults gain one). Page record: `SECT` in
`DocumentCodec` (`TimedSections.encode`, only once started; skipped by readers before it), held by
`ToneDocument.sections` / `setSections` (a change to save, not Undo; a page with them started isn't `unmarked`;
`DrawingBook.duplicatePage` clears them, as the clock). Upstream files touched: `ToneDocument` gains `fence` / `fenced` /
`inkless`, and `finish` puts a fenced box back as the gesture found it (`restoreSpan` over each captured tile), so a
locked section takes no marks from any tool, its Undo step holding nothing there; `DrawingPad` refuses a pen landing in a
fenced box (`TimedSections.LOCKED`), draws the overlay in `render` / `renderAll` (`drawSections`, never in the page) and
gains `showSections` / `flashSection`; `PaintActivity.timedLook` hands a Diary Page to `sectionLook` (Timed Pages
configured 0 there), `allowStroke` starts a fresh page's sections, `countdownTapped` starts or skips. `DrawLayout.TIMER`
is 40 dp (two lines); `InkWashBar`'s countdown is tappable and autosizes. Not fenced: Undo of a stroke from before the
lock, and the whole-layer edits (Clear Page, Clear Layer, Delete Layer). Checked in `TimedSectionsChecks` (and
`OlderSectionsCheck` against a1328a8), `DrawLayoutChecks`.

## Print style and everyday pens (2026-10-21, branch `inkwash-presets`)

A fifteenth style, **Print** (`ModularStyle` index 14, code digit `e`, after Anything goes so every older code reads as
before; `ModularStyle.shown()` lists it before Anything goes, for the Tools page's chips, `ToolsLayout.chipLabels`,
`ToolPresets.styles` and Brush Lab's style table and flyout, which is now 2 × 8 at 25 dp rows, `LabLayout.styleRows`).
Its roll: round or chisel, ink, wash, charcoal or graphite, pressure darkening; `rollScreen` gives every Print roll a
Print Shading (any of the ten alike) and `rollTone` one in five Screentone, both from the seed as before, so no other
style's rolls change. Marker gains a size for Wash (40 px), for the Highlighter. Twelve Print Basic tools and four
everyday pens (Ballpoint, Gel pen, Fountain pen under Fineliner; Highlighter under Marker, Wash with Blend Mode Behind)
are built dial by dial in `PresetPictures` (test sources), which prints their `ToolPresets` rows and draws a contact
sheet (S-curve, scribble and card loop each); `ToolPresetsChecks` checks the rows are its output. The web lab
(`modular-lab-src/parts.js`) and Brush Studio don't have Print: a Print code doesn't read there. No upstream file touched.
### Hand Hatch and four tonal prints (2026-10-21, branch `inkwash-handhatch`)
No upstream files touched. Engine (Scribulor's own, pure): new `HandHatchStamper` (hatch strokes laid whole, once each, keyed
by set, row and place along it in a `LongSet`, seeded by the brush's seed and the stroke's first sample; sets added by
pressure, by a return to a place (a coarse grid of last distances and passes) and by pen time on a place through Stipple's
Build-up ticks (`StippleStamper.Holds`, so `ModularStroke.holds` is made for it too and the pad's held ticks reach it);
strongest coverage kept, so batching never changes it); `PartChoice.HAND_HATCH` (Tip choice 12, key `handhatch`, Hatch's
dials but Layers, and `PartDial.HLEN`, `HWOB`, `HENDS`); `ResolvedBrush` (`LineTip`/`BodyTip.HAND_HATCH`, `Pattern.hLen`,
`hWob`, `hEnds`, `handHatch()`), `LineStamper.Meta.handHatch`, `ModularStroke` (reach, dispatch), `QuickStroke` (kept up
with as a stipple: its strokes are laid once), `ModularSizes.HATCHING` (60 px in every style), `ModularFeatures.seedOnly`
(never rolled), `ModularWords`, `DrawingNames`. `PrintScreen.Kind` gains `STIPPLE`, `WOODCUT`, `SCRIBBLE`, `HATCHING` before
`DOTS` and their `TONE_` twins after `TONE_MEZZO` (`PartDial.SCREEN` options 11 to 14, `PATTERN` 7 to 10, still one
base-36 digit): a raw field per family (`raw`), evened at class load (`EVEN`, from a page at the default Pattern Size and
45 degrees) so the share inked is the darkness, still a threshold at each page pixel; the older kinds' thresholds are the
bit-for-bit same (`HandHatchChecks.old`). Scribulor UI only: `BrushLab.chips` shows Hand Hatch in Advanced only (Basic's
thirteenth Tip tile would not fit the Nomad's swap strip). Rolls unchanged (new prints never rolled). Checks:
`HandHatchChecks` (determinism live and batched, building by pressure, passes and holding, reach, bounds, dials, old codes
and prints, words, a bench line); `scripts/handhatch_pictures.sh` draws the contact sheet. Ben-Day redrawn (the owner):
`PrintScreen.BENDAY` is now a round dot at every site of a hexagonal lattice (1.5 cells apart, in the angle's frame), a
pixel's threshold the tint (`TINTS`: 10, 20, 30, 50, 70%, solid) whose dots first reach it (`BENDAY_TINT`, measured at
class load); same Kind and option, so codes read as before and only its look changes. `ScreenChecks` takes its steps. Web lab and Brush Studio have
no twins yet. Since 2026-10-22 (branch `print-tissue`, the owner: "it seems to jump between shades") a pixel's threshold is the
share itself (`BENDAY_SHARE`, solid at .997), not snapped to a tint, so the dots grow smoothly with pressure;
`ScreenChecks` takes a step every twentieth of the grey. `WildSample.TYPICAL_PRESS`: a print whose shade
follows pressure draws its loop and tray stroke at travel .6 at most (since branch `previews-typical`, every brush:
`pressShades` is gone; see below). Damp tissue is Clear Water, Lay Down 0
(`ToolPresetsChecks.tissue`).

### Tilt Shading: pencils shade laid low (2026-10-22, branch `pencil-tilt`)
The owner: "all of the pencil presets... let's use the tilt so when the pencil is tilted it shades like an angled
pencil". Engine change, mirrored in the web lab and Brush Studio. `PartDial.SIDE_SHADE` (key `tshade`, "Tilt
Shading", 0–1, 0), the hand's last dial after Gradient's, in `ModularCode.ADDED` and Hand's `KEEPS`, so every older
code reads it off and every brush without it writes as before. `ResolvedBrush.Pen.side` (any line; 0 under a Screen);
`Pen.shade(lean)` = side × smoothstep(.5, 1, lean) on `BrushDirection.tiltLean` (5°→0, 65°→1): nothing through 35°
(the Manta's degrees, `docs/TILT_INPUT.md`; the natural hold, 30–45°, stays the crisp line, byte for byte), all of it at
65° and lower. `LineStamper.nib` widens the line by 1 + `SIDE_WIDTH` (5) × shade, lifts `SIDE_LIGHT` (.25) × shade of its
darkness (so graphite's contact rule reaches only the paper's high points: grainy) and gives the dab an edge soft over
1 + `SIDE_SOFT` (.5) × shade × its radius (`Dab.soft`); a shaded segment is stepped as its widened size (cost: 4 px
pencils at 70° 0.02–0.04 ms a sample on the desktop, upright 0.006–0.014). `ModularStroke.lineReach` grows by the same
width. Control card: `BrushLab.handRows` shows it after Fast for a Round, Chisel, Flat or Filbert line of an ink-layer
medium (`tiltShades`). Presets: 2H, HB, B, 2B, 5B and Sketch pencil at 1 (their Tilt Size and Tilt Dark cells, which
widened and lightened from 5°, set to 0; seeds, names and captions kept; Erasable unwritten); Loose hatch (a Hand
hatch) unchanged. Lab: `engine.js` `sideShade`/`SIDE` in `stampLine`, `parts.js` `pen.side`, `newmods.js` the dial,
`dialShown`, `tiltShadeChecks`; `modular-lab.html` patched with the same hunks; Studio rebuilt (check.cjs 117 of 117;
java-codes 621 of 621, the 2H preset and it half shaded among them). `LineStamperChecks.tiltShades`.

### Tilt Shading lighter and more diffused, charcoal and pastels too (2026-10-23, branch `tilt-shading`)
The owner: "on an angled pencil the shade is usually a little lighter than a straight mark... and it's a bit more
diffused... for charcoal etc as well too". Engine change in `LineStamper` and `ResolvedBrush`, mirrored in the web lab
and Brush Studio. A shaded dab (`Nib.shade`, `Dab.side`) now lifts `SIDE_LIGHT` (.05, was .25) of its darkness, since
graphite's and charcoal's rules amplify a lower coverage into a washed-out band, and instead keeps its coverage where the
side touches: `LineStamper.sideTooth` drops it below `SIDE_SKIP` (.35) × shade of the paper's fine grain (eased over
`SIDE_BAND` .05), nearly all or nothing so the medium's own rule keeps its grain on what is left, and
`LineStamper.sideStreak` lays faint streaks along the stroke (the brush's own draws, 9 across, at most `SIDE_STREAK` .1).
Its edge is soft over `SIDE_SOFT` (.8, was .5) of the radius, and a stick's fuzzy edge is `SIDE_FEATHER` (.5) wider
again. At 70 degrees and travel .3 to .9 the band's middle is 52 to 83% of the upright mark's darkness for 2B pencil and
61 to 73% for Soft charcoal (was 0 to 52% and 49 to 60%). Speed: a shaded dab is laid every `SIDE_STEP` (.2) of its
width (a pixel was laid about ten times over), and a pixel already at its most is left before the dab's work, so a 4 px
pencil at 70 degrees costs what it did (0.04 ms a sample on the desktop). `ModularStroke.lineReach` no longer grows
with Tilt Shading: the lattice and quick-or-live choices go by the upright line again, so an upright mark is drawn as
before the brush had Tilt Shading (pencils of 16 px and up are live and full resolution again, as before 2026-10-22),
and `sideReach` (the widened line) only pads `trailReach`. Presets: the Charcoal and Pastels sticks (not Blend stump,
Stump, Wax crayon, Crayon scratch) at Tilt Shading 1 with their Tilt Size and Tilt Dark cells 0, as the pencils, and
`ToolSlots.FIRST`'s Soft charcoal with them. A crayon's line takes the tooth and streaks but not the soft edge (no
preset has it). `LineStamperChecks.sideTone`: the 2B pencil and Soft charcoal presets at 70 degrees are 55 to 80% of
upright's darkness at the same pressure, with more of the band soft edge.

### Charcoal's quick preview reads its grain (2026-10-22, branch `pencil-tilt`)
The owner: "charcoal preview is also darker than the resulting mark..". Scribulor-only classes; finals byte for byte (Golden
over Charcoal and Pastels: 90 rows, every final hash the same). `ModularCompose.Contact`: charcoal's contact rule with
its brush's terms worked out once (the same arithmetic, so the compose is unchanged); `QuickStroke` fill `GRAIN`: a
charcoal or pastel line's quick preview (a nib, not placed lines; not White, Invert or a Screen) writes, at each pixel,
the tone the rule lays at the stick's core: `LineStamper`'s coverage (its press `lerp(1, .3 + .7 pr², fuzz)` × the
hand's darkness × the stick's low and mid grain) through `Contact.preview` (the rule without its divisions) over the
tone found, instead of the flat `STICK_SHARE` grey. A stick past `ModularStroke.INK_LATTICE_PX` reads it once each
2 × 2 pixels. Measured, quick preview against final, 16/60/120 px at travel .4/.6/.9 (the session's scratchpad
`pencil/`): dark pixels 0.88–1.30 times the final's (were 0.84–3.51: Messy stick 3.51 → 0.99 at 60 px, .4), 76–100% of
the final's own. Desktop pen-thread cost about twice the flat tone's (0.02–0.035 ms a sample at 60–120 px, 0.07 at
240); `QuickStrokeChecks.previewIsCheap` asks a Charcoal preview for under half of drawing live (a third elsewhere).
Cards are unchanged (already at `TYPICAL_PRESS`: 0.80–1.06 of a real stroke at .6).

### Previews at a usual pressure; crayon's previews (2026-10-22, branch `previews-typical`)
The owner: "the preview marks are much darker than the typical line left at normal pressure", "ie. crayon", "the
preview for crayon is pretty poor". Scribulor-only classes; no engine change, finals byte for byte (the speed plan's
Golden: 702 rows, every final and live hash the same). `WildSample.typical` caps every loop and tray stroke, and a part
card's Swell wave (not Hand's ramp or Drying's Lift), at `TYPICAL_PRESS` (.6). `ModularSizes.sampleSize`: Crayon a fifth
of the reach (was a ninth, Charcoal's). `WildSample.waxGround`: a crayon's Scratch samples scrape a ground of the same
crayon, Scratch off, not the White brushes' flat dark band. `QuickStroke` fill `TOOTH`: a crayon line's quick preview
(Wax crayon past ~40 px) writes the line's whole ink where `Crayon.tooth` ≥ `CRAYON_PEAKS` (1.1) − the newest sample's
travel, nothing between, instead of the flat STICK_SHARE grey. `WetPaintChecks` (Wax crayon's mid-stroke hashes) and
`LegacyConversionChecks` (the loop samples) re-recorded; their pages unchanged.

### Tracing paper, Stage 3c: layers are stacked pages (2026-10-21, branch `inkwash-stacks`)
Spec `docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md`, "Stage 3c". A layer is a clear page stacked on the page
below: `TracingPaper.LINKED` (bit 16 of a page's step bits, so it moves, copies and goes with its page wherever `steps`
does), the `STAK` record (`BookOptions`, only when a page is stacked; a build without it sees each layer a page,
`OlderOptionsCheck` against 2ddb2a4) and the `TSRK` recovery section. New, Scribulor's own and pure: `PageStacks` (the
stacks the links make; `flatten`, a stack's layers as one page for the copies and the crisp page), `LayerEdits` (Delete
and Merge Down as one Undo step: `DrawingBook.take`/`restore`, ready while the page's last Undo step and the book's edits
are unchanged), `PageStacksChecks`. Android-side: `LayerColumn` (the column's + above, thumbnails, + below, folded
button and hold menu), `InkWashBar` (its LAYERS part; `beside`/`openPanel` shared, `drawTab` back), `DrawLayout` (the
layers' budget, folded when a full stack doesn't fit; the strip without a Layers button; the menu's words and sizes),
`PageTurner` and `NotebookScreens` (a stack one preview, number, tile, turn, move; `drawStackPreview`, a stack's crisp
page), `PaintActivity` (`newLayer(above)`, `duplicateLayer`, `deleteLayer`, `mergeDown`, `deleteStack`, `moveStack`,
`drawOnStack`, stack turns, the layer Undo queue, the blank page at the end on every `updatePages`). Deleted:
`StackPanel`, `StackLayout`, `StackLayoutChecks`, `TracingPaper.shownStep`.

| File | Lines | Why |
|---|---|---|
| `DrawingBook` | +95 −8 | `stacks`, `linked`, `addLayer`, `moveStack`, `take`/`restore`/`Taken`, `changes`; a copy is stacked on its original; deleting a stack's bottom makes the layer on it the bottom (and moves the page drawn on to the layer below, for a layer); `template(i)` is the stack's bottom page's; step bits masked to their byte. |
| `BookOptions`, `BookPictures` | +12, +6 | `STAK`, `stacks(found, …)`; the shelf's `Cover.stacks` (the count it shows). |
| `RecoveryCodec` | +9 | The trailing `TSRK` section; older readers stop before it. |
| `NotebookExport`, `AutoExport` | +40, +3 | A PNG and a PDF page a stack (`update(…, tracing)`), its layers flattened under the bottom page's guides, a Hidden layer left out; a page alone keeps its key. |
| `PagePicture` | +14 | `stacked`: layers' pictures multiplied, the strip's picture of a stack. |
| `InkWashLayers` | +8 −4 | `merge(document, above)`: Merge Down through Merge Old Layers' flattening. |
| `BlankPages` | +25 −10 | `end`/`lastBlank`: the last stack, every layer; `Watch` on the last stack. |

### Stage 3c follow-up: layers above cover, landing layer, flattened neighbours (2026-10-21, branch `inkwash-stacks2`)
`GhostPlane.build` returns `Planes` (under: as before; over: the layers above the page drawn on, tones and cover),
groups a neighbouring stack (`Source.group`) to flatten it before its strength; `TracingPaper.ghostLayers` (page, layer
%, stack %, stack), `MOST_DECODES`; `DrawingBook.pageOf` (a memory-only bit beside the step bits, masked out of
`tracing()`). Checks in `PageStacksChecks` (over, lands, neighbours, decodes).

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +7 | `visibleLayers(…, overTones, overAlpha)`: the layers above, flattened, as a last layer; arrays `MAX_LAYERS` + 3. |
| `ViewportBitmap`, `viewport_bitmap.c` | +6, +4 −4 | The planes passed through; `nativeCompose` takes 11 layers. |
| `DrawingPad`, `MirrorShow` | +2 −2 | The ghost is `GhostPlane.Planes`. |

### Layers and pages bug hunt (2026-10-21, branch `inkwash-bh-layers`)
| File | Lines | Why |
|---|---|---|
| `DrawingBook` | +8 −2 | `addLayer` below the bottom gives the new bottom the stack's own template; `unstack` (a stack's bottom deleted) passes its template, ghost pin and cover picture to the layer on it. |
| `PaintActivity` | +6 −3 | Use This Page on the Cover (file menu) uses the stack's bottom page, as the notebook screen does; `deleteStack` on the stack drawn on lands on the next stack's layer drawn on last (`pageOf`). |

### Spray hatch and screened fills (2026-10-21, branch `inkwash-sprayhatch`)
No upstream files touched. Engine (Scribulor's own, pure). `HandHatchStamper` is now a spray (the owner: "like stipple/spray
with lines"): the moving pen's steps (`passDose`, by pressure) and the held pen's Build-up ticks (`holdRate`) dose cells of
a fixed page grid round the pen (`State.dose`, falling off `1 − q²` toward the band's edge), taken in one order by distance
so batching never changes them; each cell's candidate strokes (`Spray.lay`: place, turn, bow, weight from the seed) are
laid once its dose passes their thresholds, the first third in one direction, then the crossing one, then the third,
up to `full` (Spacing's pitch, `FILL`); a darker dose lays a cell's strokes again as dark. Its `LongSet`, keys and row
sets are gone; Tip choice, dials and codes unchanged. Screened marks (Print Shading, Screentone): `ResolvedBrush` resolves
a screened brush's tip crisp and even (`Body.crisp`, `BodyStamper`'s cover-sharp edge; no soft edge, dry brush, streaks,
tooth, stick fuzz, wet edges, line bled into the wash or diffusion) and `ResolvedBrush.screenTone` is the darkness the
screen inks: the line's ink, the body's at `SCREENED_BODY` of the swatch, the more of the two, so a stroke prints one tone
over its whole footprint (the cause of "a squiggly line in the middle": the screen thresholded the body's soft falloff
and its light glaze, only the line's core passing). `ModularCompose` inks a screened line without its stick grain and
from `screenTone`; `PrintScreen.printed` keeps the darker of the print tone and the mark (was: built up as transparent
paint), and the compose inks from the print tone as kept, so strokes over strokes at one tone lay the same dots and a
filled area is even; `QuickStroke`'s preview follows. Checks: `ScreenFillChecks` (each Print preset: a scribbled square's
evenness against the screen's own, a stroke's edge, live, batched and again the same), `ScreenChecks` (union of two
strokes, the print tone the stroke's, the darker of two in either order), `HandHatchChecks` (spray: slow drag, held dab,
cap; bench at its busiest). `SprayHatchPictures` (test side, not a check) draws the before and after sheet.

### Import Image, Import PDF, Export Now (2026-10-22, branch `import-images-pdf`)
The file menu's three rows (docs/archive/superpowers/specs/2026-10-22-import-design.md). Pure and checked: `PictureImport`
(`PictureImportChecks`); `NotebookTemplate.tones` reads a picture's brightness through `PictureImport.grey` (unchanged);
`LayerEdits.added` (Undo takes a layer just added away, with what was laid on it). 2026-10-26, at the owner's request
(branch `erase-image`): Place lays the picture on the layer drawn on (`PictureImport.place`), so the eraser takes it and
the marks under it alike; the new layer and `LayerEdits.added` are gone, and its Undo is the ordinary one.
2026-10-29 (branch `import-merge`, the owner: "can we merge the pdf and image import flow? ... so it'd be just one import
thing"; "more like supernote file explorer"): one **Import** row (`importFile`): one picker for both, browsing by folder
(`PictureImport.entries`, `start`, `path`; Android's fallback picker takes `image/*` and PDFs); a picture goes to
`importPicture` as before; a PDF to `ImportPicker.pdf`, New Notebook (`importPdf(uri)`, unchanged) or This Page (the
page drawn by `PdfImport.page`, placed through `importPicture`). Upstream `PaintPreferences.importFolder` (unused
since `ImageImport` went) remembers the picker's folder, its default now Document (was Download): one line.
2026-11-01 (branch `live-import`): the placing bar's **Lighter** and **Darker**, a step a tap, three each way
(`PictureImport.Placing.shade`, `PictureImport.shaded`, `greys(argb, shade)`; paper still the picture's own grey's to
say), shown by `PicturePlacer` and laid with it (`PicturePlacer.Place.at` takes the shade; `PaintActivity.lay`, one
argument); the browser's stamp.js the same through `WebEngine.placeShade`, `placeCanShade`, `pictureGreys`, `placeLay`.
2026-11-02 (branch `live-picture-contrast`): **More Contrast** and **Less Contrast** beside them, three steps each way
about the middle grey (`PictureImport.toned`: contrast first, then `shaded`, so Lighter still keeps white, Darker
black). The bar's four steps are one list, `PictureImport.STEPS`, and `Placing.step(i)`/`canStep(i)` (was `shade(by)`,
`canShade(by)`) move its two dials; `greys(argb, shade, contrast)`, `Place.at` and `PaintActivity.lay` take both (one
more argument); the browser's through `WebEngine.placeStep`, `placeCanStep` (were `placeShade`, `placeCanShade`) and
`stampWords().steps`.

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | +110 −5 | The rows; `pickDocument` (ACTION_OPEN_DOCUMENT) and its result (since 2026-10-26 only where the app may not list files: Scribulor's `ImportPicker`, with Cancel; one picker by folder since 2026-10-29, `importFile`); Import Image's placing and `lay` (on the layer drawn on since 2026-10-26, one Undo step); Import PDF's box with Cancel, then the new notebook opened; `exportCopy(now)` for Export Now and its note. |
| `DrawingPad` | +14 −4 | The placer's hooks beside the magnets' (touch first, overlay off and on, the raster composed again, suspend, no auto-refresh mid-drag); the magnets' host shared, with `dots()`. |
| `DocumentStore` | +19 | `create`: a new notebook's file written whole on the store's thread, or not at all. |
| `AutoExport` | +8 −3 | `notebook(…, done)`: run once the copies are written; whether they were queued. |

### Wet paint (2026-10-22, branch `wet-paint`)
MonoPaint's wet canvas (`WetWatercolor`, upstream `8743286`, MIT, © mpdairy) under the six-part engine as Drying's Wet
(docs/archive/superpowers/specs/2026-10-22-wet-paint-design.md; the owner's Option A of
docs/archive/superpowers/specs/2026-10-22-monopaint-paint-proposal.md). `WetWatercolor` goes from dormant to live engine code, driven by Scribulor's own
`WetCanvas` (the session's fourth wet thing) and `WetPaintStroke` (the footprint along the pen laid into it). Its
upstream hooks are split into their own commits (generic: erasable/kind for its own gestures, wake frames, rest, rewet,
finishing with the previous edit). `BrushInk`, `PaintLoad` (one setter) and `WetWorkBudget` are reused as they are;
`CanvasGravity` is not: the tilt comes from the session's `PageTilt` source, one sensor path. MonoPaint's own wet canvas
path in `DrawingPad` (`wet`, `advanceWet`, `dryWet`, the droplet and wetness bar) is untouched and still unreachable
from Scribulor's bar.

| File | Lines | Why |
|---|---|---|
| `WetWatercolor` | +60 −5 | The hooks above. |
| `PaintLoad` | +5 | `left(share)`. |
| `QuickStroke` | +37 −208 | Its preview's footprint sweep moves to `FootprintSweep` (byte for byte); `start` routes Wet strokes to `WetPaintStroke` and Pastels/Dry brush over wet paint to its layer; Draw once (the experiment). |
| `ModularStroke` | +2 | Tells the session a stroke ended (Pastels and Dry brush joining wet paint). |
| `WatercolorSession`, `DryingTicker` | +119 −10 | The wet canvas: kept across strokes (`settleBefore` leaves it, `settleNow` dries it), ticked after the others, rests at Undo; the ticker's droplet tap dries it; fading marks wait only while something moves. |
| `PaintActivity`, `PaintPreferences` | +11 | Draw once from the launch extra `drawOnce`. |
| `InkWash` | +2 −1 | The finger eraser settles as the pen's does (wet paint stays wet). |

New, pure: `FootprintSweep`, `WetCanvas`, `WetPaintStroke`; checks `WetPaintChecks`. Engine: `PartChoice.WET_PAINT`,
`PartDial.STAYS_WET`, `ResolvedBrush.Wet`/`asInstant`, `ModularFeatures`, `ModularWords`, `ToolPresets` (Gouache and Oils
replaced, `RETIRED`), `ToolSlots` (the Erasable migration knows the retired ones). Not mirrored in the web lab (see the
spec).

### Everything erases everything (2026-10-22, branch `erase-template`)
The owner: "Pen isn't erased by eraser (everything)". The Eraser on Everything takes every mark whole, permanent paint
too: it erases as an eraser limited to every kind (`KIND_ALL`). Engine only; no upstream file but `ToneDocument`'s comments.

| File | Lines | Why |
|---|---|---|
| `EraseBothStroke` | +14 −8 | `takes`: every kind for Everything; the fluid's lift still by the Erasable rule (its wax stays). |
| `ToneDocument` | +9 −4 | `compositeToneKept`: `KIND_ALL` rubs a cover's design as Everything did; `eraseKinds`' words. |

### Template guides on the screen: Full, Half, Faint, Hidden (2026-10-22, branch `erase-template`)
The owner: "A way to hide/show/adjust transparency of template". Pure and checked: `NotebookOptions.guidesShown` (a
`GSHN` record in `BookOptions`, a `TSRG` section in `RecoveryCodec`), `NotebookTemplate.shown` (the guides lightened
as ghosts are, cached beside `shared`), the card's eye (`LibraryCard`, `CardSheet.Screen.GUIDES`, `CardArt.Icon.EYE`).

| File | Lines | Why |
|---|---|---|
| `DrawingBook` | +1 −1 | `guided`: the page drawn on gets the guides at the notebook's step (the copies' `printed` unchanged). |
| `PaintActivity` | +7 −1 | The file menu's Template row; the page shown again when the step changes. |
| `NotebookScreens` | +3 −2 | A page shown big takes the step's guides, keyed by it. |
| `CardSelector`, `CanvasPen` | +2 | The Template Guides screen's choice; the full eye's icon. |

### Side column like MonoPaint's (2026-10-22, branch `sidebar-monopaint`)

The owner (2026-10-22): "i want users familiar with the monopaint interface to feel like they know what everything does
in ours." Where MonoPaint 0.98.3 has an equivalent we follow its icon, order and behaviour: its sidebar palette
(`Toolbar.addSidebarPalette`: four 22 dp squares two by two, default 0, 162, 225, 255; a tap selects the shade; set
from the palette editor) becomes `SwatchBar.grid`, the shared `SwatchFaces` grey faces paired two by two under the sizes
and `GreyStrip` (tap or drag picks where the pen lifts, its square following the pen through `StripMarker` as the color bar's marker does; hold opens the Sizes and Greys window, which is our palette editor; rub picks and loads, as the color bar's `PaintRub`). The four
greys are still `ToolSwatches`' shared ones with their defaults (black, dark, mid, light grey: not changed to
MonoPaint's white, which a wash can't lay). `GreyStrip`'s tap now sets the tool's own darkness alone
(`ToolSwatches.useGrey`, `greyInUse`: no swatch marked unless it is that grey; "the color picker is too hard to use");
its four snap marks and `ShadeStrip.nearest`/`SNAP_MM` are gone. The wet indicator sits under the swatches
(`DrawLayout.COLUMN`: FACES, SWATCHES, WET), 44 dp on the Manta (48 before) so the Manta in landscape stays at its
preferred heights; the Nomad in landscape now fits under its preferred heights (fit 0.36, icons 42 dp). Zen Mode's
button is beside Menu, half its row each ("zen mode button, can you place it on the toolbar always"); the floating
filled hands (`ic_ph_hands_praying_fill`, Phosphor Fill 2.1.1) only in Zen Mode; the outline stays Bold, as every bar
icon (DESIGN.md). The brush line (`InkWashBar.brushLine`, `DrawLayout.BRUSH_LINE` 18 dp, `brushWords`) sits in the
page area over the page bar; `DrawLayout.pageHeight` (the notebook screens' page proportion) is unchanged, the canvas
`canvasHeight` 18 dp shorter, so older pages open at 100% panned (`CanvasViewport`), never shrunk. A slot's tap says
its name (`say`, `DrawLayout.NAME_MS`). Checked: `DrawLayoutChecks` (order, budgets, brushLine), `GreyBarChecks.sideStrip`.
Not checked on a device (the owner was using it).

| File | Lines | Why |
|---|---|---|
| `BlowDryer` | +15 −9 | Its frame drawing is a static `draw`, `FRAMES`/`FRAME_MS` package-visible, so `WetIndicator` runs MonoPaint's blow dryer after its tap ("monopaint when drying has a brief hair dryer animation"); the button overlay draws as before. |
| `DrawingPad` | +8 −2 | `uncovered`: a closed popup's area is blanked white then drawn again (`eraseFastInk`, as the Layers panel's close), so the eraser's kinds and the other flyouts leave no ghost ("popout menu for erase often leaves a ghost"). |
| `PaintActivity` | +3 −3 | The brush line placed over the page bar, hidden in Zen; the floating Zen button shows the filled hands; the menu's Zen row gone. |
| `ShadeStrip` | −13 | The strip's snap helpers, no longer used. |

### Zen Mode (2026-10-22, branch `erase-template`)
The owner: "a zen mode without toolbars would be nice. floating icon hands-praying in bottom left". The file menu's
**Zen Mode** row hides the column and the page bar (`showInkWashBar`), so the pad, and the page fitted to it, take the
whole screen; a plain hands-praying icon (`ic_ph_hands_praying`, Phosphor Bold) over the stage's bottom-left corner as
turned, 44 dp, 12 dp in, leaves it (Back too). A session's only. Trade-offs: the page is fitted again to the larger pad
(it grows and moves; keeping it still would waste the space Zen gives), a Timed page's countdown (on the column) isn't
shown while in Zen (Time is up's window still is), and a fast e-ink stroke crossing the icon may draw over it until the
next refresh redraws it (`invalidateZen`). Checked: `DrawLayoutChecks.zen` (its size, place, words).

| File | Lines | Why |
|---|---|---|
| `PaintActivity` | +40 | The row, `setZen`, the button over the stage, Back leaves Zen. |
| `DrawingPad` | +1 −1 | A full refresh draws the Zen button again. |
| `DrawLayout` | +9 | Its words and sizes. |

### Wet brush: MonoPaint's wet brush pen on the wet canvas (2026-10-22, branch `wet-brush`)
Brush pen's **Wet brush** rebuilt on Wet (the owner, 2026-10-22: "yep go for it"; the wet paint spec, section 8): Wet on
Ink or Wash is MonoPaint 0.98.3's wet brush pen, untinted `BrushInk` in flowing water (`WetWatercolor.waterMask`),
held per holder between strokes (`HeldPaint.wetInk`), dipped by a tap on a grey. The old Wet brush (Fast-wet line and
wash) is retired (`ToolPresets.RETIRED`), its code and engine path unchanged.

| Upstream file | Lines | Why |
|---|---|---|
| `BrushInk` | +6 −6 | Hook: a brush's own capacity (`BrushInk(dryPaintSticks, capacity)`); `CAPACITY` upstream, so its behaviour is unchanged. Generic: a bigger brush holds more. |
| `WetWatercolor` | +12 −3 | Hook: `waterMask(..., pull, ink, trade)`, flowing water whose new pixels trade `trade` dabs' worth of ink in one go (a footprint laid once a pixel, as the pen's overlapping dabs would rub it); `layWater` takes the factor, 1 everywhere upstream. |
| `InkWashBar`, `BrushLabActivity`, `InkWash` | +10 | A grey's tap or rub dips the selected tool's brush (`InkWash.dip`, `HeldPaint.dip`). |

Pure: `ResolvedBrush.Wet.inks`, `WetPaintStroke` (ink mode, `INK_PULL`, `inkTrade` by pressure, `INK_LOAD`, `inkCapacity`), `HeldPaint` (wet ink kept in the preferences, `saveWetInkIn`),
`ModularWords` ("Holds ink between strokes. Tap a grey to dip."), `ToolPresets`; checks `WetPaintChecks.wetBrush`,
`ToolPresetsChecks`. The bench's `WET_NAMES` gains it.

### Speed plan F6, F9, F7, F5 (2026-10-22, branch `speed-f6-f9-f7`)

Engine-internal speed work (the speed plan's §5.2): quick previews (`QuickStroke`: placed lines sweep each line's own
nib through the new `Placement.Geometry`; washes and sticks flat at every pixel), Trail for Drying and Halo
(`ModularStroke.trail`), a render's deferred re-tapers (`ModularStroke.retaperNow`), a big Drying wash laid in blocks
over the cores (`DryingWater.finishing`). `FootprintSweep` shapes may have up to `SIDES + 2` corners or none. Final
pages byte for byte as before.

| Upstream file | Change | Why |
|---|---|---|
| `ToneDocument` | −1 | `logStroke` no longer makes a new revision: the stroke's own step made one, and the wet state its lift left (a Slow wash, a halo, a floating bath) holds that one, so the extra revision dropped it as "page changed". |


### Wet watercolour: Watercolor's washes on the wet canvas (2026-10-22, branch `watercolour-wet`)

The owner's choice ("i think maybe 1 ... we can port over the dark edge, bleed and bloom"; the wet paint spec, section
9): Wet on a Watercolor style Wash is a transparent wash laid into MonoPaint's wet canvas (`WetWatercolor`, upstream
`8743286`, MIT, © mpdairy), merging wet into wet and drying back with Scribulor's look (dark edge, blooms, granulation,
tide lines). Eight Watercolor presets made Wet; the old ones retired (`ToolPresets.RETIRED`), their codes unchanged.

| Upstream file | Lines | Why |
|---|---|---|
| `WetWatercolor` | +326 −12 | Hooks marked "Inkwash": `wash(Wash)` (a wash's dials and noises; null upstream: nothing below runs), `washMask` (a glaze of fresh water, each pixel once), washes as numbered water (`Tile.wave`, `join`: wet into wet is one wash), `edgeness` (a pass before the frame's flow), `capillary` (pigment from fresher to drier water, damp water flooded: blooms), `bleedInto`, `look` (how a wash's pigment shows as it dries back: rim, grain, mottle, tide lines; 1 for every other pixel), `wakeBeside`, tile-scale evening off and fall at .4 where washes are. Large for an upstream file: the wash's physics needs the tile's private state; it is one switch (`wash`), with upstream's frames, slices and sharing unchanged. Not generic yet; could be offered upstream as "watercolour edges" if Matt wants it. |
| `ModularPerfBench` (androidTest) | +7 −4 | The wet bench takes names on the desktop and gains the washes; a brush that runs is benched tilted. |

Pure: `ResolvedBrush.Wet.wash` (`Wet.wash(b, stays)`: Dark Edge, Bloom, Backruns, Bleed, Tooth, Stays Wet; style
Watercolor), `WetCanvas.brush` (keeps the wash), `WetPaintStroke.washStrength`, `ModularWords` ("Merges while wet, blooms
when damp."), `ToolPresets`; checks `WetPaintChecks.wash*`, `ToolPresetsChecks`, `GreyBarChecks`; pictures
`scripts/watercolour_pictures.sh` (`WatercolourPictures`).

### Wet ink wash: Ink wash's washes on the wet canvas (2026-10-22, branch `inkwash-wet`)

The owner: "wet ink wash brush blending .. similar issue as the watercolor where there should be a bit more mixing, less
dark where they overlap, especially when wet". Wet on an Ink wash style Wash, or Ink with a body, is a wet wash too
(`ResolvedBrush.washes`), on the pair's wash half (`ModularStroke.layerOf`), with a pen line laid beneath its water.

| Upstream file | Lines | Why |
|---|---|---|
| `WetWatercolor` | +45 −6 | Hooks marked "Inkwash": `lineMask` (a wash's pen line, dry ink held stuck beneath the water: never moved, glazed over), `washMask(..., rows, line)` (reads each mask row between its first and last new column; lays a line as wide as the wash in its pass), `Wash.feather` (how far its water bleeds into paper; .85, as before, for watercolour). |
| `ModularPerfBench` (androidTest) | +1 | The wet bench gains the five Ink wash washes. |

Pure: `ResolvedBrush.washes`, `Wet.pull`, `Wet.line`, `wetBody` (an ink wash's wash under the pen at most 40 px past its
line, 180 px across), `WetCanvas.Footprint.take` (each row's first and last new column, `rows`; the mask cleared span by
span: the same masks, read faster), `WetPaintStroke` (`lineSweep`, `lineInWash`, `body`), `ToolPresets`; checks
`WetPaintChecks.inkWash`, `ToolPresetsChecks`; pictures `scripts/watercolour_pictures.sh <folder> "Ink wash"`.

### No bucket, Fill in and Page Colour (2026-10-23, branch `no-bucket`)

The owner: "maybe this app doesn't use a bucket, just a brush with fill in the lines" and "a notebook setting: default
page color? that'd be cool". The bar's paint bucket (Fill, 2026-10-19, above) is taken out: `InkWashBar`'s button and
`tapFill`, `InkWash.filling` and `fill`, `FillTool`'s tap rules and notes (`LABEL`, `HOLD`, `needsHold`, the heap
guard, `slot`, `tapWhileFilling`) and `DrawLayout.labSlot`; its cell stays, empty. `FillTool`'s engine (`region`,
`Areas`, `lay`, `start`, `brush`, `walls`) and `FillRegion` stay: Stay in Lines reads them, `FillChecks` keeps them
working. A Basic tool, Fill in (`ToolPresets`, Stay in Lines at Flood 1), and a kit, Colouring In, take its place.
Page Colour is `NotebookOptions.paper`, a `PCOL` record (`BookOptions`) and a recovery section (`TSRB`), only when not
white; a page added starts on it as its own paper tone (`ToneDocument.paperTone`, the page shade's), so the eraser,
pictures and copies already show it; `BlankPages` counts a page blank on the notebook's colour.

| Upstream file | Lines | Why |
|---|---|---|
| `DrawingPad` | +1 −70 | The 2026-10-19 Fill's `fillToolTap`, `layFill` and `previewFill` taken back out. |
| `DrawingBook` | +30 −4 | `blankPage` (a new page on the notebook's Page Colour, for `addPage` and a page not packed yet), `repaperBlank` (Page Colour changed: a stack all blank takes it; a page put away only when it is the blank page's own bytes), `addLayer` (a layer on its stack's paper). |
| `ToneDocument` | +2 | `setPaper`: the paper as a change to save, not an Undo step. |
| `PaintActivity` | +2 −2 | The page shown again when Page Colour changes; the blank page made ahead on the notebook's colour. |

### Filled Shape (2026-10-23, branch `filled-shape`)

The owner: "draw a filled shape, could we do that possibly?", as a brush, not a tool button. Smudge's seventh choice,
`PartChoice.SHAPE` (key `shape`, no dials; older codes never hold it, never rolled or swapped to), beside Stay in
Lines: both decide where a stroke's paint goes and neither moves paint along (`ResolvedBrush.carry` null). New, pure:
`FilledShape`, a `LiftingStroke` round the outline's stroke (the brush's own, or its `MirrorStroke`): it keeps the pen's
samples, and at the lift finds the area they enclose (`room`: each row's crossings, non-zero winding, a byte a pixel of
its box; Copy Strokes every image of it, Real Mirror each cell's share folded into the kept cell, `FillTool.keptSide`),
closes the outline with a sample at its start, lets the outline finish its own work after lift, then lays the area
through `FillTool.start(..., shape)` (the retired bucket's fill in strips, but on the outline's own layer,
`ModularStroke.fillShape`: a stick's or crayon's line as its press lays it, `LineStamper.press`, and the compose keeping
the darker of the outline and the fill, `ModularCompose.keepDarker`, so the two are one tone at any grey) and links the steps into one Undo
step. A dot or a short line (`tiny`: under 144 px or the brush's square) stays a stroke. The bucket's heap guard comes
back here (`PEAK_BYTES_PX`, older Undo steps let go, else the outline alone). `InkWash.stroke` (drawing screen and Brush
Lab's test page) and `replay` wrap strokes with `FilledShape.of`; `WildSample` fills loops, tray strokes and the Smudge
card's blob (`blob`). `PartCard` shows a sample for it though it has no dial. Web twins: `newmods.js` (choice, words,
code, checks; the pictures draw the outline alone, as `FillTool` has no twin there), `voice.js`, `codes.cjs`.

| Upstream file | Lines | Why |
|---|---|---|
| `DrawingPad` | +30 −4 | The retired bucket's `previewFill` back for a big shape (`FilledShape.PREVIEW_PX`); a shape's mirrored outline found for the live copies (`outline`). |

### Scribbler Style, phase A: the shared pieces (2026-10-23, branch `design-base`)

The owner's design pass (`docs/archive/design-pass-2026-10-23.md`, the component rules in `docs/DESIGN.md`, "Components").
Scribulor's own pieces carry it: `Theme` (`box` is grey-200 with no line, `menu` and `menuRow` for every menu and popup
over the page, `dialog` grey-200 with the act ink-filled), `TypeStyle.MENU` (12 sp), `LabButton` (primary ink-filled,
every other plain words, no `outlined` flag), `LabUi` (one header style, the grid a grey-200 band), `LoopCard` (words on
grey-200, the ink tab for the one in use, a toolbar slot ink-filled with `slot`), `SlotTile` (the slot in use
ink-filled, its mark on-ink), `PartCard` (no frame or strip), `SwapStrip`, `SizeModal`, `InkWashBar`'s
menus, `CardArt` (no ✓). The Tools page's blinking outline is gone with `LoopCard.outline` and `setNowColor`.

| Upstream file | Lines | Why |
|---|---|---|
| `PaintActivity` | +27 −32 | `menu` built from `Theme.menu`/`menuRow` (no row frames, 12 sp), closing through `DrawingPad.uncovered`; the page panel's close cleans its area (`cleanArea`); the rotate button square, in the menu's look; the last `Color.*` and bare text sizes as tokens. |
| `DrawingPad` | +24 | `cleanArea` (`uncovered` without the page's full refresh) and `cleanOffPage`: what a closed popup covered beside the page gets `SelectionFeedback.clean` from the window, so no menu leaves a ghost on the bar or the page bar either. |
| `SelectionFeedback` | +13 −15 | `cleanAfterClose` (the clean pass once Android has drawn under a closed popup); captures start on white, since the panel takes a pixel's red alone and a clear control showed black; the several-patch `update` (the Tools page's blink) removed with the blink. |
| `SettingsForm`, `Toolbar`, `ToolSettingsPanel` | +4 −4 | Their off-palette greys (`0xffeeeeee`, `0xffaaaaaa`, `0xffcccccc`) and rounded corners as `Theme`'s greys, square. MonoPaint's screens, still built and hidden. |

### Scribbler Style, phase D: the drawing screen's layout (2026-10-23, branch `design-canvas`)

The owner's design pass (`docs/archive/design-pass-2026-10-23.md`, "Drawing screen"): "moving the pages bar to the top. keep the
zen in the bottom left (align a bit better pls) and you can put the selected brush indicator to the right along the
bottom". `DrawLayout` holds the geometry. The page bar (`PageTurner`) sits in `pageArea` over the pad, the page's width.
The column runs from the top to Zen Mode's cell (`columnHeight` is the screen less `ZEN_CELL`, its view padded clear of
it). The foot row holds Zen Mode's corner (`lineStart` wide) and the brush line (`BRUSH_LINE`). `zenCell` is mirrored
exactly for a column on the right. The canvas is the same size as before (`canvasHeight`), so no page changes shape.
The strip's previews start `STRIP_EDGE` from the screen's top with their tab along the bar's foot, and a page's options
drop down from the strip. Inside a notebook the bar stays at the foot (`PageTurner`'s padding goes on the page's side).
The brush line's name takes a tap only over its words (`InkWashBar.onWords`), and the line is mirrored with the column
(`setLayoutDirection`). A stroke that starts on the brush line doesn't draw, as before: the line is outside the pad.
The rule over the layers and other layers' thumbnail edges are grey-400. Checked: `DrawLayoutChecks.screen` (every
screen, either hand: bar, canvas, column, Zen, line, no overlaps), `zen`, `fits`, `pageBar`, and on the device
`NomadUiChecks.layout` (each turn and hand).

| Upstream file | Lines | Why |
|---|---|---|
| `PaintActivity` | +15 −15 in `showInkWashBar`, `placeZen` | The page bar in `pageArea` over the pad. The foot row holds Zen Mode's corner and the brush line. Zen Mode's cell from `DrawLayout.ZEN_CELL`. |

### Scribbler Style: the side column pass (2026-10-23, branch `design-canvas`)

The owner: "some things are a bit off with it.. layers section is a bit janky.. inconsistencies". Scribulor classes only,
no upstream file. `DrawLayout` gains one inner margin (`edge`, the column view's padding), so `inner` and `half` are the
content's and every row sits on one axis. Faces are a grid of two cells (`PAIR_PAD`, `FACE_GAP` the same across and
down, `faceSide`), the grey strip spans the sizes' squares (`GreyStrip.rows`), and the size and swatch in use have a tab
under them (`SwatchFace.pair(leftCell)`). The tool buttons in use are ink-filled as a slot is, and rows are symmetric
(`SlotTile.row`, `draw`). The shared `SlotTile` changes touch only rows, which the drawing screen alone uses. `SwatchFace`
changes touch only paired faces. Groups are parted by `GAPS` gaps (no `RULES`), which share the spare a full stack
leaves, up to `GAP_MOST`. `LayerColumn` has a label (`LAYERS_LABEL`, `LAYER_LABEL`), one + (New Layer Above), and New
Layer Below in the layer drawn on's menu, the layer drawn on ink-filled. A blurring smudge brush shows the stump
(`smudgeIcon`). Checked: `DrawLayoutChecks.column`, `tray`, `order`, `layers`, `GreyBarChecks`. Desktop pictures:
`scripts/bar_pictures.sh` (4 layers).

### Page Settings (2026-10-23, branch `page-settings`)

The owner: "long press on a page will bring up a panel to adjust the settings for that page (similar to notebook
settings, with constraints, etc.)". The screen is Scribulor's (`NotebookScreens.fillPageSettings`, the rows
`LibraryCard.pageRows`, the rules `NotebookRules.forPage`). A page's own No Erasing and Timed Pages are kept in its bits
beside its steps (`DrawingBook.RULE_SHIFT`), so they move with the page wherever its steps do. Files gain `PRUL`
(`BookOptions`), the recovery `TSRR`, each only when a page has one, so other notebooks write byte for byte as before.
Checked: `NotebookRulesChecks.pageRules`, `LibraryCardChecks.pageSettings`.

A page's own time (2026-10-23, branch `page-layers`: "ideally it should be a slider"): the stop of
`NotebookOptions.SECONDS` in its bits beside its rules (`DrawingBook.TIME_SHIFT`, the rules now 4 bits, as
`validOwn` always allowed), kept with the page as the rules are (`OWN`). Files gain `PTIM` (the page count, two bytes a
page, its seconds), the recovery `TSRM` after `TSRR`, each only when a page has one, so every other notebook, one with
`PRUL` too, writes byte for byte as before. Older builds skip `PTIM`, and their recovery reader stops before `TSRM`. Such
a page then takes the notebook's time, or 5 min. Checked: `NotebookRulesChecks.pageSeconds`.

| Upstream file | Lines | Why |
|---|---|---|
| `DrawingBook` | +48 −4 | `pageRules`, `setPageRules`, `rules(page)`, `restorePageRules`, `ownTemplate`, `paperTone`. The rule bits are masked out of `tracing()` and kept by `restoreTracing`, and go to the layer that becomes a stack's bottom (as its template). `Snapshot.pageRules`. |
| `BookCodec` | +2 −1 | The snapshot's page rules to `BookOptions`, read back with `restorePageRules`. |
| `RecoveryCodec` | +12 −1 | The `TSRR` section, and `TSRM` (a page's own time) after it. |
| `PaintActivity` | +41 −8 | `rules()` is the page drawn on's (`rules(page)` another's, for Delete Page and Delete layer). `setPageRules`, `shadeStack` (Page Colour, through `history` as Fill page). `clearPage` package-private. The Pages grid's long press. Brushes on Bar's write-back uses the notebook's options, not the page's. |
### The side column: side bars, the Fun button's two, Menu level with the page bar (2026-10-23, branch `column-tweaks`)

The owner, on the Manta: side lines instead of underlines, more room between the brushes, the swatches as wide as the
grey strip and lined up with it, the eraser beside the smudge tool, Magnetic Poetry and Mirrors as buttons with no Fun
button, Menu level with the page previews, the brush line clear of Zen, Zen dotted while off, the wet drop not always on,
no LAYERS label. Scribulor classes only, but for two upstream-file edits. `SlotTile.row` keeps `DrawLayout.barRoom` on
the side its bar is on (`Face.barLeft`), the bar drawn there (`SIDE_BAR`, `BAR_GAP`). Tray rows `TRAY_TOP` over and
under, 4 dp taller. `SwatchFace.pair` and `GreyStrip` keep `faceMarkRoom` beside the square, the strip a square wide.
`FunMenu` became `MirrorsMenu` (`app.mirrors`), `InkWashBar.poetry` and `mirrors` are tool cells (`modesChanged`).
`DrawLayout.menuRow` centres Menu on the previews (Menu out of the `ICON` count), `LINE_GAP` parts the brush line from
Zen. `WetIndicator` draws its dry drop dashed (Phosphor's drop path, Bold width, `DRY_DASHES`), so `ic_ph_drop` is gone.
`DrawLayout.MARK` stays for the Tools page and Brush Lab. Checked: `DrawLayoutChecks` (`column`, `tray`, `order`, `fun`,
`screen`), `GreyBarChecks`, on the device `NomadUiChecks.layout` (Menu level with the previews, the line's gap).

| Upstream file | Lines | Why |
|---|---|---|
| `PaintActivity` | +4 −4 | `mirrors` (`MirrorsMenu`) in place of `fun`. Zen Mode's icons swapped. |
| `DrawingPad` | +2 −2 | `app.mirrors.placed`. |

### The side column's extras: Left-Handed, Template Visibility, a dirty brush shown (2026-10-23, branch `column-extras`)

Scribulor UI only. `DrawLayout.GRID_ROWS` is 4: the fourth row is `InkWashBar.lassoCell` (an empty `View`, kept for the
Lasso, branch `upstream-lasso`, which puts its button there in its place) and `InkWashBar.template` (Template
Visibility, `DrawLayout.TEMPLATE`, `showTemplate`, `templateMenu`), placed and sized with the other cells in `place`
and `showModes`. `TIGHT`'s icon rows went from 33 to 28 dp so the folded column (the Nomad in landscape) still holds
it. `PaintActivity.showGuides` is the one way the guides' step is set (the menu's Template row, the button, its menu).
The drawing hand: `DrawLayout.LEFT_HANDED`, a file menu row toggling `PaintPreferences.toolboxRight` then
`applyToolboxSide`. A dirty brush: `InkWash.dirty(tool)`, `heldGrey(tool)`, `clean(tool)` for any slot and the smudge
tool (were `smudgeDirty` and `cleanSmudge`), `HeldPaint.Reservoir.darkness` (engine, pure: the paint's own darkness),
`DrawLayout.heldGrey` (snapped to the six greys, never paper), `SlotTile.dab` and `Face.held`, the smudge icon's head
tinted (`InkWashBar.heldBrush`, `ic_dirty_brush_paint` under `ic_dirty_brush`, `ic_dirty_brush_held` gone), a dirty
slot's long press `cleanMenu`. Checked: `DrawLayoutChecks.extras` and `order`, `WetSmudgeChecks.dirty`, on the device
`ScreenShots` (Template Visibility, its menu, Left-Handed).

| Upstream file | Lines | Why |
|---|---|---|
| `PaintActivity` | +15 −1 | The menu's Left-Handed row, `showGuides`. |

### The Lasso: cut and paste on Scribulor's pages (2026-10-23, branch `upstream-lasso`)

MonoPaint's Lasso (`5f764ec`) on Scribulor's model. `LassoPlanes` (engine, pure Java, in `scripts/test_inkwash.sh`) lifts
what the selected pair's wash and ink (`InkWashLayers`) and the masking fluid hold inside the loop, by role, every
plane: tones, coverage, `Layer.share`, `Layer.print` and kinds (`kindsAt` with `permanentAt`), so a moved piece looks
and erases as it did. Placing is one Undo step of linked gestures (`finishWithPrevious`): the lift cleared where any
plane had coverage, then each role laid on the destination pair's layer as a sheet over what is there (nearest pixels,
as the screen shows it). `FloatingSelection` keeps the pose; its `tones`/`alpha` are the piece's look (ink over wash),
and `planes` holds the rest. The screen shows it through `DrawingPad.render` (`LassoPlanes.shown`: the pair bare where
lifted, the look over that), not the native `SelectionPreview`, which knows none of Scribulor's compositing.

- **Scribulor UI.** `InkWashBar.lasso` in the fourth row's left cell (column-extras' `lassoCell`), Phosphor `lasso`
  (`ic_ph_lasso`). `DrawingPad.lassoOn`: the pen's strokes are loops in Cut Stamp's ants (`MirrorShow.Place`,
  `Placing`), lifted at the end (`liftLasso`). Copy (`copySelection`), Delete (`deleteSelection`) in `PaintActivity.menu`;
  Cut, Paste and the way that deletes since 2026-10-25 (below, "Lasso options").
- **Not at every `finishStroke`.** Upstream puts the piece down in `finishStroke`, which Scribulor calls from menus,
  focus loss and most buttons; in Scribulor only the pen off the piece, another tool (`InkWashBar.choose`,
  `selectTool`), a notebook change (`replace`), No Erasing and Undo end it. The page is untouched while it floats, so
  saves and a killed app lose nothing.
- **Another page.** `carrySelection`: a page turn or layer switch (layers are pages) takes the piece along as a copy
  (`FloatingSelection.copy`, lifted from nowhere). The page left is never cut: its Undo may be gone once it is packed
  (`DrawingBook` keeps two pages decoded), so cutting there would risk the paint. A page of another size drops it.
- **Import Image stays Scribulor's** (Android's picker, laid on the layer drawn on, `PicturePlacer` with
  Cancel and Place, the panel's fast outline while it moves). Upstream's import (its own picture browser, fit or own
  size and quarter turns in a dialog, floating as a Lasso piece onto an in-page layer) would lose the picker and the
  placing frame. PDF import unchanged.
- **Upstream files touched:** `ToneDocument` (+75: `copyPlanes`, `layKinds`, `layPixel`, `liftPixel`, `lay`, the
  `sheet` formula `overTone` now shares), `FloatingSelection` (+60 −25: `lift` split into `inside`, `found`, `crop` and
  `piece`, which `LassoPlanes` shares; `copy`, `samePose`, `at`, `planes`; `place` hands Scribulor pieces to their
  planes), `DrawingPad` (the lines above, `showErased` taking any `MirrorShow.Marks`), `Ui` (`closesPanel`'s palm),
  `PaintActivity` (`undo` drops a floating piece first). Generic: `LassoPlanes` could move every plane of a MonoPaint
  layer the same way.
- **Checked:** `SelectionChecks` (upstream's, unchanged) and `LassoChecks` (every plane of both layers and the fluid
  carried, a loop's shape, quarter turns, Undo and Redo, Delete, Copy, another page and a one-layer page, the preview
  matching the placed page, a file round trip). Not tried on a device: dragging a large piece re-renders its area each
  frame.

#### The PDF in Documents (2026-10-24, branch `better-pdf`)

The owner: "a cool pdf that lives in the documents section and might be searchable". No upstream file's logic touched:
`PaintActivity.EXPORTED` (Inkwash's note) says the new folder.

- **Where.** `NotebookPdf` (new, pure Java) writes `Document/<LIBRARY_FOLDER>/<title>.pdf`, beside the notebooks
  (`DrawingStorage.shared()`, so `AppRename` carries it over with the library on a rename). The title is the stored name
  without a title's hidden date. EXPORT keeps the PNGs (Drive's pictures, and the PDF's page cache), no PDF: the old
  `EXPORT/<app>/<name>.pdf` is removed once the Documents one is written (left if a `.mark` is beside it). Export Now
  writes the same file. `AutoExport.hasCopies` now means "has `.pdf.txt`", so a notebook from before is copied once on
  opening.
- **What it holds.** Each page at its pixels at 300 ppi (1:1 on the tablet), its PNG's image data as is (Flate with the
  PNG predictor). `NotebookExport.png` now writes a palette of the page's own greys at 1, 2 or 4 bits when a page has 16
  or fewer, else 8-bit grey as before: lossless either way (`[/Indexed /DeviceGray]` in the PDF). Brush strokes are
  antialiased, so most drawn pages stay 8-bit. Covers and plain pages shrink. An invisible text layer (`3 Tr`, Courier,
  WinAnsi, not embedded: Courier's 0.6 em advance is Roboto Mono's, so a magnet's word spans its tile): title and page,
  days, template, Daily Prompt card, each magnet's word on its tile's baseline, turned with it. Outline: Cover, then a
  day at a time (closed) with its pages. Page labels, Info (Title, Author and Producer from `AppName`, Subject the
  template, Keywords, days at noon), and `/Notebook` (the stored name) in the Info, written first, so the app can tell
  its own PDF from its first 4 KB.
- **Days.** Pages carry no dates, so the PDF's record (`EXPORT/<app>/<name>/.pdf.txt`, `NotebookPdf.State`) keeps each
  page's key, made and changed days and words. A kept PNG keeps its days. A redrawn page is changed today and keeps the
  made day of the page that was at its place if that one is gone. A PNG from before the record takes its file's day.
- **Handwriting.** The Supernote keeps a PDF's ink in `<name>.pdf.mark` beside it, by file name and page number, and
  makes one on opening. A PDF is rewritten in place only while pages keep their numbers (`keepsNumbers`: changed in
  place or added at the end). Otherwise, with a mark there, a new `<title> 2.pdf`, the marked one left. A file is the app's
  only when the record names it at the length it wrote (or, with no record, its info says so). Other files and lone
  marks are passed over (`free`). Rename moves the PDF and its mark. Delete removes the PDF only without a mark. An
  unchanged book isn't rewritten (sync sees nothing).
- **Speed and memory.** Only changed pages are drawn (as before, and now pages alike, such as blank pages between drawn
  ones, keep a PNG each instead of being drawn every time). The PDF reads one PNG at a time.
- **Checked:** `NotebookPdfChecks` (its own small PDF reader: the table, pages, labels, outline, info, words and their
  positions, greys decoded at 1, 2, 4 and 8 bits, incremental drawing, days, handwriting, other files, rename, delete,
  250 pages in a 40 MB JVM). By hand on the desktop: `qpdf --check`, `pdfinfo`, `pdftotext -bbox`, Ghostscript (greys
  within 1 of the PNGs, its colour rounding), PDFKit's search and outline. `PdfSample` (test helper) writes a sample
  Manta notebook. Not tried on the tablet yet: whether Documents lists it at once, search, and the mark policy there.

#### Three owner bugs (2026-10-24, branch `bugs-1024`): upstream files touched

- **The tray's pull turned the page.** The Supernote's tray is a finger pulled down from the screen's top (Android's
  start band on the Manta: 69 px, 37 dp, logcat's `SystemGestures`). The page bar sits there, and a pull past 8 dp on a
  preview was a riffle that turned to the page under the finger on lift. `PaintActivity.dispatchTouchEvent` now follows
  a finger landing within `StripGesture.SYSTEM_EDGE_DP` (40, the bar's height): on the move that decides it
  (`StripGesture.edgePull`, the riffle's own 8 dp), a pull mostly down is cancelled for every view and swallowed to its
  lift. A tap, or a drag along the bar, goes on as before. The notebook screen's swipe already needs a horizontal fling.
- **A Brush Lab ghost through the system's refresh.** `DirectEink` writes into the panel's shared plane, which the
  firmware's full refresh shows again, and a session sends only pixels changed against its own seed. So pixels another
  screen left there stayed over an unchanged page. `DrawingPad.returned` (every `PaintActivity.onResume`): the next
  `connectDisplay` blanks the fast plane where there is one (`eraseFastInk`, now skipping mode 9 where the firmware has
  none, which logged a stack trace on every popup close on the Manta) and shows the whole window again, forced (the page
  through `direct`, the rest through `cleanOffPage`). `BrushTestPad.disconnect` likewise marks the pad for a forced full
  refresh on its next connect (the Tools page after the Lab). `ToolsActivity.onPause` closes its grey bar's kept
  `SelectionFeedback` session.
- **The Lasso with the eraser selected.** `DrawingPad.beginInkStroke` took any eraser stroke as the pen's eraser end, so
  with the Eraser the tool in use the Lasso's loop never started. It now takes the eraser end itself
  (`DrawLayout.lassoLoop`), and `InkWashBar.tapLasso` reads `DrawLayout.lassoTap`.
- **Upstream files touched:** `PaintActivity` (the edge guard, `returned` on resume), `DrawingPad` (`returned`,
  `eraseFastInk`'s mode 9 test, `beginInkStroke`'s eraser end). **Checked:** `StripGestureChecks.edge`,
  `DrawLayoutChecks.lasso` (from every tool, twice, after Undo and a page turn, another tool).

#### The size slider and the Dropper (2026-10-24, branch `size-slider`): upstream files touched

- **The size slider.** The side column's four size faces and four grey swatches go (the owner: "change the four size
  options to a quick size slider ... grey swatches can go"). `SizeSlider` (new, Scribulor UI) stands in the left cell,
  six rows of faces long, on `ToolSwatches.position`'s log scale. Its thumb moves through `StripMarker`, as the grey
  strip's square does. It sets the slot's own px (`ToolSwatches.useSize`), the eraser one of its engine's four
  (`ToolSwatches.eraserAt`). `SwatchBar` holds the slider, the strip and the Dropper, and `SwatchFaces` stays for the Tools
  page and Brush Lab, marking no size when a slot draws at a size of its own (`ToolSwatches.sizeInUse`). Nothing in the
  stored keys changed: a slot kept as faces reads as it drew.
- **The preview.** `DrawingPad.sizePreview` draws `HoverNib`'s ring beside the column at the page's scale through the
  hover ring's overlay (`RasterOverlay`, the fast path), with the size in words in a paper box upright on the screen,
  and on lift takes it off and cleans its patch (`cleanArea`).
- **The Dropper.** `DrawingPad.dropperOn` reuses MonoPaint's eyedropper gesture (`sampleShadeGesture`, `pickPointer`):
  the pen picks `ToneDocument.shownTone` (new, read only: `visibleLayers`' layers with the stack's planes on the paper,
  the guides left out), a disc of it drawn over the pen by the same overlay (`drawDrop`), hovering too. The lift hands
  it to `InkWashBar.dropped`, which sets the tool's grey (`ToolSwatches.drop`, `ModularSizes.laying`: `swatch`'s
  inverse) and puts the Dropper down. The tool in use is never changed.
- **Upstream files touched:** `DrawingPad` (+120: the preview, the Dropper's ring and gesture branch, the outline's tone
  and words), `ToneDocument` (+15: `shownTone`). **Checked:** `ToolSwatchesChecks.columnSlider`, `sliderSlots`,
  `dropper`, `DrawLayoutChecks.tray` and `order`. `ScreenShots` draws the slider's preview and the Dropper and picks a grey.
- **The column's polish (2026-10-24, branch `column-polish`).** The owner: "the redraw is blinky and slow", "the
  selector is offset", "put the size selected and the current shade below ... it can be one thing", "move eyedropper to
  beside wet indicator". `SizeSlider` is now a static right triangle with the strip's tab right of it, and its
  `StripMarker` area is the tab's strip alone (`DrawLayout.sliderMarkRoom`), ink and paper, so `SelectionFeedback`
  sends it in the fast binary mode. The thumb across the grey wedge had made each move a grey update of the whole cell,
  and the preview added a patch of the page. `DrawingPad.sizePreview`, the outline's words and `DROPPER_LIFT` are gone.
  `drawDrop` draws the Dropper's ring round the tip, a hole in its middle (`DROPPER_HOLE`). `SwatchReadout` (new,
  Scribulor UI) is one dot under the slider and the strip, the size in the grey, drawn only when they change. The
  Dropper moved to the wet indicator's row in `InkWashBar`. **Upstream files touched:** `DrawingPad` (about −60: the
  preview and its words out, the ring centred). **Checked:** `DrawLayoutChecks.tray` (the tab's room, a drag's panel
  area against the old one, the readout, the Dropper's cell), `ToolSwatchesChecks.columnSlider`,
  `NomadUiChecks.layout` (the readout under the slider and the strip, the Dropper beside the wet indicator).

#### The refocus: keep, tuck, cut (2026-10-24, branch `removal-pass`): upstream files touched

The owner's marks (`docs/archive/refocus-marks-2026-10-24.md`): a paper notebook first, a little mischief second.

- **Deleted, upstream files:** `AppSettingsDialog`, `PaletteEditor` (MonoPaint's settings window and palette editor, never
  reachable in Scribulor). Their hooks went from `PaintActivity` (`appSettings`, `paletteSettings`, `savePalette`, the
  editor's touch and colour hooks), `DrawingPad` (one guard), `SidePanel` (one close) and the hidden `Toolbar` (its
  palette button, `paletteAnchor`, `showPaletteShades`). An upstream merge touching those files will conflict there:
  keep them deleted. The device suites that drove them (`SettingsUiChecks`, `PaletteUiChecks`) are deleted, and
  `NomadUiChecks` turns the simulation on directly.
- **Deleted, Scribulor:** `LayerColumn` (the side column's layers; Page Settings' Layers row now), the Lock (`NotebookOptions.Lock`,
  its screen, the seal mark), Guides in PDF (`printGuides`), the slots' dab and gauge on the column, the Finger button.
- **Engine (c), small:** `ModularCode.decode` reads a Hand Hatch tip as Hatch (`asHatch`) and `ModularFeatures.offered`
  never offers it. The engine's own Hand Hatch path (`HandHatchStamper`, its branches in `ResolvedBrush`,
  `ModularStroke`, `QuickStroke`) is untouched, so as not to cross the white-ink work, and is now dead: remove it in a
  pass of its own.
- **File formats:** unchanged writers but for what is no longer written. `BookOptions` passes `GPDF` and `LOCK` by;
  `RecoveryCodec` still writes its rules' fourth array (empty) and its options' second (`{1}`) so the layout stays,
  and reads past both. Checked: `NotebookRulesChecks.lockCut` (files and a recovery as older versions wrote them).
- **Upstream files touched:** `PaintActivity` (the menu in groups with `divider`, `playMenu`, `playBrush`, Export Now
  the one export path, `leaving` in place of `exportCopy`, the lock's guards gone), `DrawingPad` (one line).

## One grey (2026-10-24, branch `one-colour`)

The owner's "procreate method": one current grey for every brush. Scribulor's own classes only, no engine change.
- **Model:** `ToolSwatches.CURRENT` (`inkwash_current_grey`, a darkness as `inkwash_dark_` keeps it) is what `look`
  gives every key but the smudge tool's (its own, as before) and the eraser's (none). `useGrey(darkness)` and
  `drop(brush, tone)` set it. `ToolSlots`' constructor runs `startCurrent` once: the selected slot's grey (the stamp's,
  else the first slot's) by the old per-key rule (`own`). The per-key greys (`inkwash_shade_`, `inkwash_grey_`,
  `inkwash_dark_`) and a notebook toolbar's darkness column are still written and read as before, painted at by
  nothing. `Toolbar.sameAs` compares brushes and sizes only. Gone: `chooseGrey`, `setGrey`, `greyInUse`,
  `InkWash.swatch`, `InkWash.tile`, the grey faces of `SwatchFaces`/`SwatchFace` and `SizeModal`'s grey editor.
  Grey Shift no longer writes the slot's grey (`InkWash.shifted`).
- **Previews:** `WildSample.slotTray` (was `InkWash.tray`) draws at `WildSample.PREVIEW` whatever the look's grey.
  `WildSample.darkGround` (Blend's White) is the one dark ground. `LabLibrary.sampler` takes no darkness.
- **Upstream files touched:** none. The stroke engine is untouched, so a stroke at a given grey draws as before.
- **Checks:** `ToolSlotsChecks.oneGrey` and `greyMigration`, `WildSampleChecks.neutral`. The per-slot grey checks in
  `ToolSwatchesChecks`, `GreyBarChecks`, `ToolsPageChecks`, `BrushLabChecks`, `NotebookOptionsChecks` moved over.

## One size (2026-10-25, branch `one-setting`)

The owner: "stop remembering per tool size and shade and use only the current setting". The grey was one already
(above); now the size is too, for every slot, the stamp tool, the smudge tool and the eraser. Scribulor's own classes,
no engine change.
- **Model:** `ToolSwatches.CURRENT_PX` (`inkwash_current_px`) is the px `look` gives every key; the eraser's is kept no
  finer than `ERASER_LEAST` when read, so a fine brush size never makes a 1.5 px eraser. `useSize(px)` (the slider),
  `chooseSize(i)` and `setSize(i, px)` (the Tools page's and Brush Lab's four faces) set it. `startCurrent` takes the
  selected slot's old size (by `own`, the old per-key rule) once, as it does the grey. A first launch writes 44 px
  (`ToolSlots.FIRST_PX`, a number now; the owner's toolbar was 44/44/44/44/4.5/360/44/182 and the eraser 24).
- **Eraser:** shares the size (the owner's words cover every tool). This undoes 2026-10-24's "eraser size stays same
  size for eraser" (`ERASER_PX`); the earlier problem it fixed, the slider snapping the eraser to its four, stays fixed.
- **Gone:** each key's size writes (`inkwash_size_`, `inkwash_px_`, `inkwash_eraser_px`) and the last grey writes
  (`inkwash_shade_`, `inkwash_dark_`, `inkwash_grey_`, from `keep`, `keepValues`, `keepLook`), so `brushChanged`
  (a brush of the other family took its own nearest size), `lookAfter`, `keptSize`/`restoreSize` (Undo's size),
  `keptLook`/`restoreLook` (Back to the start's size and grey, `ToolSlots.Start.looks`, `ToolsPage.Step.looks`,
  `BrushLab.Step.look`), `BrushLab.fitSize`, `useEraserSize`, `eraserAt`, `px(brush, i)`, `sizeIndex`, `greyIndex`,
  `ToolSlots.use`'s and `useAll`'s `keepSize` and `PaintActivity.slotsReturned`'s `sized`. The old keys are still read
  by `own`, never written, so a launch reads them once and an older build still reads what it wrote.
- **Notebook toolbars:** the file format is unchanged: `Toolbar.lines` still writes each slot's px and darkness (now the
  current ones) and `parse` reads them, but `load` takes the brushes only and `sameAs` compares brushes. A notebook's
  toolbar no longer brings its sizes. `SurpriseTools` still fills those columns.
- **Old installs:** the six-part conversion (`ToolSlots.convert`) converts brushes only; a converted slot no longer gets
  its old px and darkness written, so `startCurrent` reads the selected slot's old swatches with its new brush.
- **Toolbar backup** (`ToolBackup`, never read back): the slot lines lose their size column; one `size` line takes the
  place of the eraser's.
- **Shown:** `SwatchFaces.show()` and `SwatchFace.show` take no tool; the eraser's faces are the shared four, and can be
  set. `SwatchBar`'s slider sets `useSize` for any tool. Share codes, brush keys and names are untouched.
- **Brush Lab:** it had no size or grey of its own, only the slots' (`ToolSwatches`); it follows the same rule.
- **Upstream files touched:** `PaintActivity` (`slotsReturned` without `sized`, two `use` calls without `true`).
- **Checks:** `ToolSwatchesChecks` (`choose`, `eraser`, `picks`, `sliderSlots`, `everyTool` rewritten for one size;
  `keep` and `eraserAt`'s gone), `ToolSlotsChecks` (`start`, `grown`, `converted`, `interrupted`, `oneGrey`),
  `ToolBackupChecks`, `ToolsPageChecks`, `BrushLabChecks`, `NotebookOptionsChecks.toolbar`, `ToolKitsChecks`.
  `ScreenShots` and `LiveStrokeBench` (androidTest) set the one size or grey.
- **Then, the eraser's and the smudge tool's own size again** (2026-10-25, branch `eraser-size`; the owner: "eraser keeps
  its own size i think.. whatever the procreate convention is"). Procreate keeps the eraser's and the smudge tool's
  sizes apart from painting's, so `look` gives them `own`'s px: the eraser's `ERASER_PX` (no finer than
  `ERASER_LEAST`), the smudge tool's `inkwash_px_SMUDGE`. `useSize(key, px)` (the slider, `SwatchBar`) writes theirs while
  they are picked, the one size for any other key. A first launch sets the eraser to 24 px (`ToolSlots.FIRST_ERASER_PX`).
  Neither key was ever deleted, so a tablet that ran the one-size build gets back the sizes they had. The toolbar
  backup has its `eraser` line again. The Tools page's and Brush Lab's faces are the brushes' only. Checks:
  `ToolSwatchesChecks.eraser`, `choose`, `everyTool`, `ToolSlotsChecks.start`, `ToolBackupChecks`.

## Fast pickers (2026-10-24, branch `fast-pickers`)

The owner, on the Manta: "the size thing is still blinking when i change it", and "monopaint's shade selector is
TOTALLY fast ... both the shade selector and size selector are slow". Then "maybe also speed up the stroke selector".

**How MonoPaint's colour bar marker is fast** (`upstream/main`: `ShadePicker`, `SelectionFeedback`, `PaintActivity.updateColorBar`,
`78e7673`). Every move of the pen calls `selectionFeedback.update(shadePicker, markerArea(), () -> paint.gray = value)`.
That draws the picker into a bitmap before and after the change (only its 8 dp marker gutter), finds the changed
pixels and hands that patch straight to the panel driver through a kept session (`DirectEink` here, `fasteink` upstream).
The change itself only sets a field: no `invalidate`, so Android draws no frame. The patch is kept in `pending` and
invalidated at Android's next frame for some other reason (`syncBeforeDraw`), so Android's surface catches up later.
Which panel mode: the fast binary pen plane where `Device.fastBinaryPlane` names one (the Nomad, and firmware from
2610091001). On the owner's Manta firmware (Chauvet 2606141001) there is none, so every patch is a mode 7 grey update.
With the fast plane a clean grey pass follows 250 ms after the lift. Without it, `saveToolState` invalidates the gutter
on the lift.

**What Scribulor did on top, found with `PickerBench` on the Manta** (2026-10-24, before is `77c10b83`):
- The grey strip never picked. With no long press its own `View.onTouchEvent` refused the pen's down, so the moves and
  the lift never came: the square jumped to the pen and stayed, the grey in use and the dot never changed (the owner's
  "the dot color doesn't reflect the shade selected"). It keeps the whole gesture now.
- Every control in the column was clipped by one pixel: the cells (`tile`, 2 × 60 px) were wider than the column's
  inside (135 − 8 − 8 = 119 px at 1.875 px a dp). A clipped control fails `PanelCoordinates.fullyVisible`, so every
  `update` fell back to an Android invalidate. The strip's moves, the slots' marks, everything in the column went
  through Android. The far margin now takes the rounding (`InkWashBar.arrange`).
- An Android redraw of a column view, even of unchanged pixels, drove the panel 34 frames, about 500 ms: the firmware's
  default update for an app's frame is two passes, the blink. The column and the brush line now ask the firmware for
  partial grey (`View.setEinkUpdateMode(3, 7)`, as `NativePen` does for the page, `InkWashBar.partialGrey`, left at
  the firmware's default under Settings' Standard Android drawing). An unchanged redraw then drives nothing, a change
  one 21 frame grey pass. So the redraw `syncBeforeDraw` does later costs nothing. The bar's auto refresh, which leaned
  on that two-pass redraw, now gets `SelectionFeedback.clean` (forced mode 7) for the column and the line.
- On a lift `SwatchBar.show` presented the whole bar and `GreyStrip.show`, `SizeSlider.show` and `SwatchReadout.show`
  each called `invalidate()`, an Android frame at once. They only set fields now, and the bar's change is one present
  of what changed (the tab, the dot).
- A slot's stroke, drawn again after a size change, called `iconOnly` (an invalidate). It goes through `update` with
  `ToolButton.faces` now. The brush line's name (`Caption`, quiet while its update runs) and preview (drawn into the
  bitmap it shows, cached by what it shows) do the same.
- A slot tap: the Button's Material ripple (Android frames while it ran), the press outline (a grey update on the down
  and another 200 ms after), then the marks, the size and the grey as separate presents. The panel runs grey updates one
  at a time, so each was about 270 ms more. Now no background and no press outline on the tray's rows and tool cells
  (the mark is the answer), and the marks, the tab and the dot gather into one present over the column
  (`SelectionFeedback.gathering`), the brush line one more.
- The slot's name label (and the strip's page shade offer) closing called `DrawingPad.uncovered`: a forced mode 7
  refresh of the whole page and an Android redraw of the whole column, 1.5 s after every slot tap. It cleans what it
  covered alone now (`cleanArea`).

**Measured** (`PickerBench`, the checks build on the Manta, Chauvet 2606141001, 24 moves a drag, panel frames
from `fdec0000.ebc` in `/proc/interrupts`, about 15 ms each, "quiet" is the lift to the panel's last frame):

| Gesture | Before (`77c10b83`) | After |
|---|---|---|
| Strip drag | no grey picked, each move an Android redraw of the whole 60 × 224 px strip | 1 mode 7 present a move, 8 × 29 px, 0 Android frames, lift: tab and dot, 36 frames, quiet 390 ms |
| Size drag | 1 mode 7 present a move, 10 × 28 px, lift: Android redraws (slot row, whole column after the label), 70 frames, quiet 790 ms | the same a move, 0 Android frames, lift 37 frames, quiet 415 ms |
| Size tap | 70 frames, quiet 800 ms | 37 frames, quiet 420 ms |
| Slot tap | 3 presents + 6 Android redraws (5 rows), 65 frames, quiet 740 ms, then a whole page refresh 1.5 s later | 2 presents (column, line), 69 frames with the name label opening, quiet 790 ms, no page refresh |
| Unchanged Android redraw of a column view | 34 frames, 510 ms | 0 frames |

The slot tap's remaining frames are mostly its name label, an Android window the firmware draws (the owner asked for
it, 2026-10-22). MonoPaint on this device was not measured (it was not used during the run). Its code path on this
firmware is the same as the strip's now: one mode 7 patch a move, nothing through Android until the lift.

- **Upstream files touched:** `SelectionFeedback` (`gathering`), `ToolButton` (`faces`), `PaintActivity`
  (`setAndroidDrawing` sets the column's firmware mode again). `DrawingPad` untouched.
- **Layout (the owner, same day):** the strip left and the slider right, both flipped (white and broad at the top), the
  wet indicator on the brush line after the name (`DrawLayout.LINE_NAME`), the Dropper and the Lasso in its old row,
  Play and Template Visibility one row (`GRID_ROWS` 2).
- **Checks:** `DrawLayoutChecks` (the rows, the drop after the name, the column's heights), `ToolSwatchesChecks` (the
  flipped slider), `NomadUiChecks.layout` (the cells, the drop on the line, nothing clipped). Bench: `PickerBench`
  (`-e pickerBench true`, the checks build only).


## Binary size slider and the owner's notes (2026-10-24, branch `notes-1024`)

The owner's handwritten notes on the Manta, the same day: "size picker is still blinking. make it an outline shape
instead? why is it redrawing?" with a sketch ("DO IT THIS WAY": the dot on top, the triangle pointing down on the left
with its tab on its left, the strip on the right with its tab on its right), then "shade picker is fine, it's just the
size one". Also: the eraser on its own row, no lines between the tray's strokes, Zen as an unfilled and a filled circle,
one frame weight on the page bar, the eraser's size its own, a subtler hover ring, the slot's name as words alone.

**Why it still blinked** (`PickerBench` on the Manta, Chauvet 2606141001, before is `8342f2ca`). "Fast pickers"
took every Android frame off the pick, but what was left still went to the panel as grey updates (mode 7): each of the
24 moves of a drag one 10 × 28 px mode 7 update, and the lift two or three more, the column's (the tab and the dot,
103 × 41 px), then the slot's row (its stroke drawn again at the new size, in the background) and the brush line's
preview (the same). On this firmware a grey update runs 18 to 21 frames and drives a changed pixel through its grey
waveform, which shows as a flash, and the panel runs them one after another: the lift was 31 to 32 frames, quiet
about 350 ms after it. The strip's drag is the same mode 7 a move (the owner: "fine").

**What changed.**
- `SelectionFeedback.updateBinary` (upstream file, +12): an update whose changed patch is all ink and paper goes through
  the panel's binary update, MonoPaint's fast plane (mode 9) where `Device.fastBinaryPlane` names one, else
  `Device.fastStrokeMode` (driver mode 5 on this Manta firmware, 8 frames, the pen stroke's), else mode 7 as before. A
  patch with any grey in it stays mode 7. `StripMarker.binary` (upstream file, +2) sends its marker's moves that way.
- `SizeSlider`: an ink outline of a triangle pointing down, no fill, no anti-aliasing, its tab on its left. Every move is
  a mode 5 update of the tab's strip.
- `SwatchReadout`: no anti-aliasing, so the dot (its grey as the panel's dots, its ink edge) is ink and paper. The bar's
  change on a lift (`SwatchBar.show`) is `updateBinary`: the tab and the dot in one mode 5 update.
- `InkWashBar.swatchChanged`: a size or grey picked changes the slider, the strip and the dot alone. The slot's row and
  the brush line's preview show the brush at its Medium size whatever the slot's size (`ToolSwatches.picture`), so
  neither is drawn again.

**Measured** (`PickerBench`, the checks build, 24 moves a drag, two rounds, panel frames from `fdec0000.ebc`):

| Gesture | Before (`8342f2ca`) | After |
|---|---|---|
| Size drag, a move | 1 mode 7 present, 10 × 28 px | 1 mode 5 present, 8 × 28 px |
| Size drag, the lift | 2 to 3 mode 7 presents (column 103 × 41, row, line), 32 frames, quiet 340 to 350 ms | 1 mode 5 present (the dot, 44 × 44), 13 frames, quiet 127 to 147 ms |
| Size tap | 31 to 32 frames, quiet 358 to 384 ms | 11 frames, quiet 138 to 149 ms, all mode 5 |
| Strip drag, a move | 1 mode 7 present, 8 × 29 px | the same (unchanged by the owner's word) |
| Strip drag, the lift | 1 mode 7 present, 29 to 31 frames, quiet 338 to 340 ms | 1 mode 5 present (the dot), 16 frames, quiet 179 to 196 ms |
| Android frames during and after either drag | 0 | 0 |

No grey update is left on the size slider's path, the dot included. Not measured: residue from mode 5 over many moves
(the panel is the judge, a forced mode 7 clean of unchanged pixels drives nothing on this firmware). Device time about 15
minutes, the checks app and its folder removed after.

**The other notes.**
- The eraser's own size (`ToolSwatches.useEraserSize`, `ERASER_PX`): the slider snapped the eraser to its four sizes,
  so the lower two thirds of it all meant S and the eraser "stayed the same size". It now keeps the px the slider sets,
  4 to 360, under its own key (its old `PX` key held a brush swatch's size and was never read), whatever the brushes'
  sizes and the tool picked. `EraseBothStroke.pen` and `InkWashSizes.diameter(float most, …)` take that size,
  `HoverNib.eraser(most)` its ring. Choosing one of its four on the Tools page sets that size.
- The eraser on its own row (`DrawLayout.GRID_ROWS` 3): the eraser, the smudge tool, Play and Template Visibility.
- No hairline between tray rows (`DrawLayout.SLOT_LINE` gone, 24acb8e's lines undone).
- Zen Mode's button: `ic_ph_circle` while off, new `ic_ph_circle_fill` (Phosphor circle fill) while on (`PaintActivity`,
  upstream file, 2 lines).
- The page bar's previews all in a 2 dp frame (`PageTurner`), the page drawn on told by its ink frame and its tab.
- The hover ring (`DrawingPad`, upstream file, about 30 lines): eight 2 px ink dots on the nib's outline, each its own
  saved patch, no white dashes and no firmest ring (`HoverNib.firm` and `FIRM_SHOWN` gone).
- The slot's name label: bold words on paper, no `Theme.menu` frame (`InkWashBar.say`).
- **Checks:** `DrawLayoutChecks` (three tool rows, the eraser's own row, no hairline, Zen's icons, the column's
  heights), `ToolSwatchesChecks` (the eraser keeps its own size), `HoverNibChecks` (no firmest ring, the eraser's own
  size), `NomadUiChecks.layout` (the dot over the slider and the strip, the eraser alone on its row), `ScreenShots` (a
  slot's name, the eraser picked), `DrawScreenPictures`. `PickerBench` reports the presents after the lift.


## The brush that would not switch, and the owner's notes (2026-10-24, branch `slot-bugs`)

The owner's handwritten notes on the Manta, after `90bc4236`: "won't switch to another brush, kept jumping to liftith
with wrong size as well", "artifacting of side size indicator", "triangle shape should be right triangle", "switching
to eraser, brush selector blinked", "strokes around pages on top toolbar should be hairline". Answers: tray rows at
Medium are fine, the name label fully transparent.

**Why it jumped back.** Not the model. `ToolSwatchesChecks.everyTool` picks every slot in turn with sizes set between,
after the eraser's, the smudge tool's and the grey's, and a notebook toolbar coming and going: each tool keeps its own
size and nothing is written under another's key. The selection (`InkWash.select`) is set at once on the tap. The jump
was the screen. Since "Fast pickers" (`8342f2ca`) a tool pick is one `SelectionFeedback.update` of the whole column,
and the updates of the views inside it (`ToolButton.mark` of the old and new slot, `SwatchBar.show`'s tab and dot) run
inside it, gathered, with no frame asked of Android. Afterwards only the column's own drawing was invalidated
(`retainForNextDraw`, then `owner.invalidate`). Android keeps each view's recorded drawing until that view is
invalidated, so its next frame of the column still had the marks, the slider's tab and the dot as Android last drew
them, at launch: the slot in use then, at its size. The brush line's name is the same (`Caption.quiet`). On the Manta
the column is under partial grey (`setEinkUpdateMode(3, 7)`), and the driver drives any pixel the frame asks for that
differs from what it holds, which after the fast update is the new mark. So any later frame of the column (`PickerBench`
saw two after every slot tap) put the old slot's mark, size and name back on the panel.

**What changed.**
- `SelectionFeedback.invalidateWithin` (upstream file, about 20 lines): the owner and every view inside it over the
  patch are invalidated, at the next frame as before (`retainForNextDraw`, `finishUpdate`) and in the fallbacks that
  draw through Android. A turned child (a popup's `QuarterTurnLayout`) is invalidated whole.
- `SwatchBar.show`: the grey strip and the Dropper shown or hidden (the eraser, the smudge tool) inside the same fast
  update, gathered into the tool pick's one update of the column. They were set visible or not and invalidated through
  Android, the whole column drawn again with the stale rows: the eraser's blink.
- `SelectionFeedback.redrive` (upstream file, about 35 lines) and `StripMarker` (upstream file, 4 lines): a binary
  marker on the Manta (the size slider's tab, driver mode 5) gets one clean 250 ms after the lift. The driver passes by a
  pixel asked for the value it holds, forced or not, so `clean` of unchanged paper drives nothing there. `redrive` asks
  for the tab's strip with its paper at level 14, then as it is, two mode 7 updates back to back (`panel_end.h`'s
  "near", in Java over a session of the strip alone). Ink stays as it is. On the fast plane (the Nomad) the clean pass
  stays as it was.
- `SizeSlider`: a right triangle, its straight side on the left by the tab, the right angle at the top.
- `PageTurner`: every page preview's frame 1 px, the page drawn on in ink with its tab.
- `InkWashBar.say`: the label's words with no background, its popup transparent (the light rows keep paper).

**Checks.** `ToolSwatchesChecks.everyTool` (desktop). `PickerBench.stale` (the checks build, `-e pickerBench true`):
after each slot tap and an eraser tap, the column alone asked for a frame, the window read back (`PixelCopy`) against the
column drawn now, the ink of each tray row, the eraser's cell and the dot compared. A row whose ink differs by more than
60 px prints FAILED. Not run on the tablet yet (it was not connected), so the redrive's look and the bench's numbers are
still to measure.

#### The page bar's + instead of a blank page at the end (2026-10-24, branch `add-page`): upstream files touched

The owner, 2026-10-24: "instead of always a one white page, add a + icon on the top toolbar". A notebook no longer keeps
a blank page at its end. The page bar's + tile (`PageTurner.Strip`, tile `count + 1` in `StripGesture`, pinned in the last slot when out of the window) adds a page at the end
(`DrawingBook.insertPage` is `addPage`). `BlankPages.opened` now takes an old notebook's blank page at its end away when it
is opened (`DrawingBook.dropUnchanged`, not as a change), only a page alone, blank on the Page Colour, with nothing of
its own and not the page drawn on. `BlankPages.Watch`, `end`, `lastBlank` and `append` are gone.

| File | Lines | What |
| --- | --- | --- |
| `PaintActivity` | +15 −20 | `replaceBook` calls `BlankPages.opened` (every way a notebook opens: the shelf, the recovery, Open). `updatePages`, `recovery()` and `openNotebook` no longer put a blank page at the end. `newPage` is the +, with a note past 250 pages. Timed Pages' Next Page on the last page is `newPage`. |
| `DrawingPad` | +9 | `redrawAsks`: while `PickerBench` asks, each `invalidate` of the page keeps its caller, so the bench names who asks Android to draw the page again after a slot tap. |
| `LayerEdits` | −3 | Undo of a layer deleted in a full notebook no longer makes room by the blank page at the end (none comes). |

**Checks.** `BlankPagesChecks.opened` and `plus`, `PageStacksChecks.blankEnd`, `PageColourChecks.pages`, `CoverChecks.order`,
`PictureImportChecks`, `DrawLayoutChecks` (desktop). `PageNavigationChecks` (the checks build): + after the page drawn on, an
old notebook's blank page at its end gone on opening. `PickerBench` prints "page redraw asked by" each tap.
## Big brushes under the pen (2026-10-24, branch `big-brushes`)

The owner: "larger brushes, they seem a little sluggish" and "i kind of enjoy how the monopaint paints go on quick but
evolve over time, sinking into paper". Engine and glue only.

**Measured on the Manta** (`LIVE=1 scripts/bench_device.sh -e styles "@Steady line,@HB pencil,@Soft charcoal,@Round
marker,@Soft wash,@Grey wash,@Opaque round,@Wet gouache,@Hog bristle,@Wet oils,@Soft pastel,@Ben-Day brush" -e lives
none -e sizes 60,120,240 -e modes gray`, `framelog` beside it). Every brush but the Wet ones goes quick at these sizes.

- Under the pen (a batch's wait from its oldest sample to presented, p50/p95 ms): 4–12 / 8–55 for every quick brush,
  Soft charcoal and Soft pastel 240 at 25–35 / 52–71. Wet paint (Soft wash, Grey wash, Wet gouache, Wet oils) 5–9 /
  7–16 up to 120 px, 17–38 / 33–162 at 240. **Ben-Day brush 25/114, 90/261 and 940/1350 at 60/120/240**: the outlier.
- Why Ben-Day: its body is 4.3 times its size (628 px at 240) and the quick preview worked out its screen at every new
  pixel on the pen's thread (`PrintScreen.ink`, its thresholds and Ink gain's four neighbours).
- Pen-down to the first present: 75–160 ms for every brush (Ben-Day 236–406). In it: the injected down's age when
  handled 30–44 ms, the stroke's creation 30–50 ms (about 20 of it `ModularStroke.begin` on the bench's fresh page:
  `ToneDocument.begin` scans a blank layer's alpha with `Layer.covers` once per layer), the first present 2–6 ms. Then
  the panel's first frame came about 100 ms after the first present.
- After lift (quick): 20–70 ms to final for most, 100–220 for big sticks and pastel, 624 Hog bristle 240, 362 Ben-Day
  240. Panel busy after lift 0.55–0.85 s for quick strokes, 0.75–4.4 s for Wet paint (it moves after the lift by design).
- Big sticks, pastel, pencil 240 and Hog bristle 240 never trail (`trail_tiles=0`): the render never catches up, so
  their texture lands at lift. Tried: composing every 250 ms while behind. Finals the same, but nothing was written
  behind the pen (the trail keeps clear of a big brush's reach from the frontier) and Hog bristle 240's lift to final
  went 0.62 → 1.72 s. Reverted.

**What changed.**
- `QuickStroke.Sweep` (`flatScreen`): an ink screen's preview (Print's screens but the tones) is black dots where the
  panel's own pattern for the screen's mean grey is black (`DotPattern.pixel`), so it stays black or nothing as the
  screen's dots were. Desktop, pen-thread ms a batch of 8 at 240 px: Ben-Day 1.29 → 0.31, Rosette 0.88 → 0.34, Halftone
  0.99 → 0.33.
- `QuickStroke.Sweep.tones`: a tone screen's pixel its dot can't darken skips the screen (speed plan §5.1's next step).
- `StrokeTiming` and `DrawingPad` (upstream file, 1 line: `timing.down` at the pen's down): the stroke timing line
  gains `lag_p50_p95` and `first_ms` (the down's event time to the first present) with `first_parts` (the down's age
  when handled, the creation, the first present).

**Manta after:** Ben-Day under the pen 11/31, 20/97, 47/128 ms at 60/120/240 (two runs: 10–11/31–54, 20–26/97–112,
42–47/128–131), first present 95–260, 133–137, 189–209 ms. Its render still lags: lift to final 84–430, 185–1019,
667–690 ms (by lift it has 300–720 of the 719 samples, varying run to run). Every other row within noise of before.

**Final pages unchanged:** every layer's tones, alpha, share, print and kind planes after settling, hashed for 19
presets (all 12 Print presets, the measured ones) at 16/60/240 px, the app's rule, every stroke quick with the render
paced, and every stroke live: identical to `2425373f`. `WetPaintChecks.BEFORE`'s two mid-stroke Halftone hashes (the
preview) updated, their ends unchanged. `ScreenChecks.quick` still holds (black dots or nothing).

**Not done:** the covers scan at a blank layer's first stroke (about 20 ms of pen-down on the Manta, once a layer a
page). Faster renders for big sticks, pastel and bristles (their texture at lift). Wet paint's 240 px lay.

#### A clean window on return (2026-10-24, branch `resume-clean`): upstream files touched

The owner, 2026-10-24: "noticed lots of eink ghosts on testing", switching back from Brush Lab or the Tools page.

**Why.** The return's clean (`262b6bc`) was a forced mode 7 refresh of the page and of the window (`refreshFastInk`,
`SelectionFeedback.clean`). On the Manta the driver passes by any pixel asked for the value it holds, forced or not, so
that refresh drives only what changed and cleans nothing. Then `8342f2ca` put the column and the brush line under
partial grey (`setEinkUpdateMode(3, 7)`, as the page already was), so their redraw on return is one grey pass from the
other screen's black text and frames, which ghosts. The only update the app knows that drives unchanged pixels again is
the firmware's default for Android frames (PickerBench, 2026-10-24: the column's unchanged redraw drove the panel twice,
about 500 ms with the blink). Mode 1 also redrives, but the driver then takes the area for white, and the JNI does not
accept it.

**What changed.**
- `Ui.cleanWindow(root, reason, restore)`: every view in the window back to the firmware's default
  (`resetEinkUpdateMode`), the whole tree invalidated, and `restore` 600 ms after that frame is drawn. Logs "Clean window
  (reason)".
- `DrawingPad`: the connect after a return (`returned`) blanks the fast plane as before, then runs `cleanWindow` over the
  window. The restore puts the page (`NativePen.partialGrey`, if still connected) and the column and brush line
  (`InkWashBar.partialGrey`) back under partial grey. The forced page refresh owed at that connect is dropped, since the
  clean redrew the page and its redraw of the bar would blink again. `cleanOffPage` is no longer run there.
- `DrawingPad.suspend`: leaving the drawing screen blanks the fast pen plane (mode 9, where there is one) over the whole
  window first, so no fast ink stays over the next screen.
- `NotebookScreens.hide` marks the return too.
- `BrushTestPad.opened`, called from `BrushLabActivity.onResume` and `ToolsActivity.onResume`: the next connect runs
  `cleanWindow` and restores the pad's partial grey.

| File | Lines | Why |
|---|---|---|
| `Ui` | +24 | `cleanWindow`, `CLEAN_MS`. |
| `NativePen` | +6 | `partialGrey`: the host back to (3, 7). |
| `DrawingPad` | +23 −9 | The return's clean, the fast plane blanked on suspend, `windowArea`. |
| `BrushTestPad` | +10 | `opened`. |
| `BrushLabActivity`, `ToolsActivity`, `NotebookScreens` | 1 each | Mark the open or the return. |

Generic: any MonoPaint screen coming back over another on this firmware has the same trap, wherever a view is under
partial grey or a forced refresh is relied on to clean.

**Not checked on the Manta** (it was not connected): the flash itself, and whether 600 ms is long enough before partial
grey comes back. The firmware may read a view's mode when the frame is posted, then any delay works. Logcat shows the
"Clean window" line at each open and return.


## Snappy UI (2026-10-25, branch `snappy-ui`)

The owner: "MonoPaint's UI feels particularly fast", make Scribulor's as snappy. A tap on the drawing screen should be
one small panel update of what changed, sent as MonoPaint's selection feedback is, with no Android redraw of its
neighbours and no grey pass more.

**What changed.**
- The slot's name label is gone (`InkWashBar.choose`, `stamp`, `DrawLayout.NAME_MS`). Every slot tap opened a popup
  window beside the column and closed it 1.5 s later (the firmware drawing it, then `cleanArea` refreshing what it
  covered), about 30 of the slot tap's 69 frames in "Fast pickers". The brush line still names the brush. The label stays
  for a slot's brush changed, a grey the Dropper picked, the mirrors and Cut Stamp.
- A tool tap's column update is `SelectionFeedback.updateBinary`: when what changed is ink and paper alone (the old and
  new marks, no new size) it is the panel's binary update (mode 5 on this Manta firmware, 8 frames), else mode 7 as
  before. `updateBinary` now restores the flag it found (upstream file, +2), so the dot's update gathered inside the
  column's no longer clears it.
- The brush line and the eraser's note are one update of the line (`updateLine` over both). A preview shown or gone (a
  slot to or from the eraser) moved the name through a layout after the update, so Android drew the line again with the
  name elsewhere, one more grey pass: the line is laid out inside the update now.
- Nothing about the brush is worked out on the tap. The line's words (`ModularWords.caption`, `BrushVoice.about`, which
  resolve the brush and read its textures the first time, 34 ms for the first on the desktop) and its preview are made
  in the background for each slot as its brush is shown (`InkWashBar.warmLine`, kept by `Brush.identity`).
- The page bar (`PageTurner`'s view) is under partial grey as the column and the brush line are (`InkWashBar.partialGrey`).
  Its arrows, number and previews are drawn through Android, and each redraw was the firmware's two passes with the
  blink, the unchanged arrows too. The bar's auto refresh cleans it (`SelectionFeedback.clean`) as it does the column.
- Menus (`PaintActivity.menu`, upstream file, 3 lines) and the side panels (`InkWashBar.beside`: the smudge chooser, the
  stamp menu) open under partial grey, one grey pass, and on closing clean only what they covered
  (`DrawingPad.cleanArea`). `DrawingPad.uncovered` also refreshed the whole page and drew the column, the page bar and
  the brush line again through Android after every close. Upstream MonoPaint's menu closes with nothing at all.

**The DrawingPad redraw on a slot tap** is not named yet: the tablet was not connected (`adb devices` empty), so
`PickerBench` did not run. From the code, the candidates are `SelectionFeedback`'s pending patches (`retainForNextDraw`
keeps the whole page after a page turn or a pan until Android's next frame for any reason, then `invalidateWithin`
draws the page again) and the label's close (gone). The bench's "page redraw asked by" names the caller on the next run.

**Not changed:** Zen Mode lays the whole screen out again (the page fitted to it), so its tap draws the page. Undo and
Redo draw what they change on the page and refresh it after a large change, their buttons draw nothing. The size slider,
the grey strip and the wet drop were fast already.

**Not measured** (no tablet): MonoPaint beside Scribulor, and every number above but the desktop's. `PickerBench` now
also opens and closes the menu and times Undo and Redo of a stroke. Risk to watch: mode 5 for a slot's old mark (ink
to paper) may leave a faint trail, as the size slider's tab did over many moves.

| File | Lines | What |
| --- | --- | --- |
| `SelectionFeedback` | +2 | `updateBinary` restores the flag it found. |
| `PaintActivity` | +3 −1 | `menu`: partial grey content, `cleanArea` on close. |

**Checks.** `DrawLayoutChecks` (the name's timing check gone), desktop RAN 127. `PickerBench` (the checks build):
menu open, menu close, undo tap, redo tap.

## Device timing (2026-10-25, branch `device-timing`)

`PickerBench` on the Manta (Chauvet 2606141001) at `2a7ada35`, and MonoPaint (`dev.tilesmile.supernote.paint`)
timed beside it the same way: a pen tap injected from the shell (`InputManager.injectInputEvent` in a small dex, its
down stamped on the boot clock), the panel's frames read from `fdec0000.ebc` in `/proc/interrupts` by a loop on the
device, about 10 ms a read. "First" is ms from the down to the first panel frame, "settle" from the down to the last.

| Action | MonoPaint: first, frames, settle | Scribulor: first, frames, settle, mode |
|---|---|---|
| Tool pick (a tool button, a slot) | 104 to 114 ms, 36 to 37, 504 to 519 ms | 206 to 251 ms, 37, 606 to 661 ms, two mode 7 presents |
| Eraser pick (bench) | not timed | about 230 ms, 73 to 83, 830 to 960 ms, two mode 7 presents and two Android frames of the whole column |
| Grey (colour bar tap, the strip) | 79 to 86 ms, 37 to 42, 491 to 549 ms | 81 to 83 ms, 21, 310 to 312 ms, mode 7 then mode 5 |
| Size (slider tap, bench) | not timed | about 135 ms, 44 to 45, about 630 ms, mode 5 |
| Menu open | 79 to 89 ms, 127 to 161, 2.4 to 2.8 s | 413 to 763 ms, 36 to 57, 0.8 to 2.3 s |
| Menu close (a tap off it) | 127 to 148 ms, 68, 917 to 937 ms | 133 to 149 ms, 69, 932 to 939 ms |
| Undo, Redo (bench) | not timed | 37 to 40 frames, quiet about 364 ms (Undo), 21 frames, about 300 ms (Redo) |

Since then (`PickerBench`, not this injector, which reads about 15 to 25 ms later): a slot pick's first frame 185 to 207
ms after "Snappy UI 2", 110 to 146 ms after "The pick's bar on the down" (below), where MonoPaint's is its press outline.

**The whole page drawn again.** No slot tap draws `DrawingPad` again now (24 taps over three runs, android frames 0,
page redraw asked by none). The name label was the one that did (gone in "Snappy UI"). The menu's opening still does,
asked by none. Traced on the main looper: the invalidate of the whole page (`ViewRootImpl` message 2, an invalidate rect
of `DrawingPad` 1785 × 2401) is posted while the window's focus change (message 6) is handled. The menu's popup is
focusable, so the drawing window loses its focus and the firmware has the page drawn again. It is not
`disconnectDisplay` or any of `NativePen.disable`'s three firmware calls, each tried alone with nothing dirtied. An
Android redraw of the unchanged page drives no panel frame, so it costs time on the main thread, not on the panel.
Upstream MonoPaint's menu loses the focus the same way. Not changed: a menu that keeps the window's focus is a
non-focusable popup, which changes how Back and a tap off it work.

**The slot's old mark and mode 5.** A slot tap's column update is mode 7 every time on the Manta: the changed patch
runs from the old mark's row to the new one and takes in the grey strokes of the rows between, and `opaqueBinary` checks
the whole patch. So the mode 5 path in `selectTool` does not run for a slot tap and leaves no trail to fix. A
screencap cannot judge it anyway: the fast update goes to the panel, not to Android's surface, so a screencap shows the
old mark until Android's next frame of the column.

**What MonoPaint does that Scribulor does not, as next steps.**
- A tool pick shows in about 105 ms, Scribulor's slot in about 230 ms, the grey strip in 81 ms. The slot's update
  captures and compares the whole column twice (135 × 2401) and opens a new panel session for it. Next: update the old
  and new rows and the swatch bar alone, and check only the changed pixels for ink and paper, so a slot pick can be mode 5.
- Its menu's first frame comes at 80 to 90 ms, Scribulor's at 400 to 760 ms (the rows built, the popup measured, the
  page drawn again on the focus change). Scribulor's menu drives far fewer frames after. Next: time the menu's build.

**Bench.** `PickerBench.pen` checks the window's focus on the down alone. A tap off the menu closes it on the down,
and the window has its focus back only later, so the lift threw and the bench stopped at "menu close". Device time about
40 minutes (the tablet was off USB for ten), the checks app, its test APK and its folder removed after. MonoPaint was
opened on its unsaved painting, so no new painting was started (its new painting asks to save or drop that one). Its
tool, colour bar and menu were timed, its pencil put back and its grey set back at the black end. Size and Undo were
not timed there, an Undo would change that painting.
## The eraser takes crayon, the scratch pads keep up (2026-10-25, branch `crayon-lab`): upstream files touched

The owner, on the Manta, with the Wax crayon large: "Crayon didn't erase with eraser. Base erase should erase
everything." And "it was sluggish in brush studio but not in main".

**Why the crayon stayed.** A crayon lays its wax on the page's masking fluid as a kept share (`ModularStroke.layWax`,
`beginFluid(0)`), so washes slide off it. Every write to a paint layer goes through the fluid's shield (`setPixel`), and
the eraser's writes did too, so wax shielded the crayon's own marks from the eraser, Everything included. Nothing about
the eraser's kind: it starts on Everything (`InkWash.eraseKinds` is 0, never saved, set only by a tool kit's step), so
there was no default to change or migrate. Large crayons lay wax over most of what they cover, so they showed it most.
Wax resist had the same rule by design (2026-10-18, "the eraser never lifts wax").

**What changed.**
- `ToneDocument.shield`: for Scribulor's eraser (`eraseKinds` not 0) only the fluid the eraser can lift shields, as
  `takesOnWax` already did for a crayon's own build-up. Wax keeps paint out, not the eraser off the paint under it. A
  kind-limited eraser (Pen, Paint, Dry Media) reaches paint under wax too and leaves the wax (no kind of mark) and
  masking fluid as before. MonoPaint's erasers (0) are unchanged.
- `EraseBothStroke.finish`: on Everything, the fluid gesture at lift now also takes the wax wherever the eraser went
  but where it lifted fluid, with `KIND_ALL` (the mask has no kinds, so every pixel is taken whole). Where it lifted
  fluid, the paint and any wax under it stay for the next pass, as before. One Undo step with the ink and wash.
- Tone's scrape is unchanged: Everything still scrapes a share of Tone's dots each pass (a black pixel at coverage 254
  reads as one, so a pen's edge pixel can scrape too).

**Why the scratch pads were slow.** `BrushTestPad` (Brush Lab's test area and the Tools page's Scratch Pad, both
`TestArea`) drew each pen event alone: sample, compose, the hatch, and a present, 480 times a second on the Manta. The
drawing screen gathers samples (`PenBatch`, drawn once the input queue is empty) and spaces a stroke's presents by 1.5
times their recent cost (8 to 100 ms) since "Speed round 2". The strokes themselves were the same (`InkWash.stroke`,
`QuickStroke`'s rule, the fast plane through `StrokePanel`, mode 7 from `prepareDocumentCanvas`), and nothing else in the
Lab redraws under the pen.

**What changed.**
- New `PresentPace` (pure): the present spacing taken out of `DrawingPad` (`presentMs`, `lastPresent`, `presentGap`),
  the same formula. `DrawingPad` and `BrushTestPad` both use it.
- `BrushTestPad`: samples go to a `PenBatch`. The first event since a draw posts `drawBatch`, which feeds them all,
  presents the stroke, renders and presents once. The down, the lift, a held pen's tick and `finishStroke` draw what is
  gathered first. Its presents while the pen is down wait out `PresentPace`.

**Measured on the desktop** (a 1300 × 900 pad, a 480-sample second of pen, the page's render and hatch for the dirty
area in Java standing in for the native compose). Per draw, desktop alone: Wax crayon 120 px 0.05 ms, 240 px 0.15 ms,
360 px 0.30 ms, Fineliner 360 px 0.21 ms, so on the desktop every draw keeps up either way. With a model of the Manta
(desktop ms × 15, 4 ms a present), the pen's wait from a sample to its draw, p50/p95 ms, per event (before) and gathered
(after): Wax crayon 120 px 643/1212 → 2/7, 240 px 948/1859 → 19/30, 360 px 1395/2792 → 38/63, Fineliner 240 px
836/1557 → 8/15. Presents a second 480 → 41 to 80. The model's factor is a guess, not measured. Timing on the Manta is
still to do (`LIVE=1 scripts/bench_device.sh` covers the drawing screen only).

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +6 −3 | `shield`: liftable fluid only for Scribulor's eraser. Generic: any eraser that wants wax kept off paint, not off itself. |
| `DrawingPad` | +8 −16 | The present spacing through `PresentPace`. |
| `EraseBothStroke`, `BrushTestPad`, `PresentPace` | Scribulor's | The wax taken on Everything, the pad's batches and spacing, the spacing shared. |

**Checks.** New `EraseAllChecks`: the owner's Wax crayon code at 44, 120 and 360 px, dark then light over a black
ground, and every Basic tool (121 in all), drawn as the drawing screen draws them and dried, then two passes of the
eraser as it starts: white paper but Tone's dots, and nothing on the masking fluid. Dry Media takes a crayon and leaves
its wax. `ResistChecks` now expects the eraser to take wax and the paint under it, and the sheen with it. Golden hashes
unchanged (no drawing changed). Desktop RAN 128.

## Snappy UI 2 (2026-10-25, branch `snappy-2`)

The next steps from "Device timing": a slot pick, the eraser pick and the menu's opening. Found by timing each step on
the main thread on the Manta (logcat stamps, taken out again), then `PickerBench` before and after.

**Where the time went.** From the click to the panel's first update, a slot pick spent about 95 ms on the main thread:
`InkWash.select` 36 ms (the toolbar backup's text, every slot's share code, built on every pick, twice when the
preference listener fired too), the whole column captured twice (10 to 15 ms each), compared (20 ms), a session opened
(2 to 3 ms). The panel's first frame then comes about 95 ms after the update is handed over, whatever the app does. The
eraser pick's two Android frames came from the brush line, not the strip: its preview went `GONE` for the eraser and
`VISIBLE` again for a slot, a layout, so Android drew the line and the column again (the strip's `setVisibility` was a
second cause). The menu was not slow in the app: built, measured and shown in 60 to 120 ms, drawn by Android 150 to 170
ms after the down. The firmware put the new window on the panel about 350 ms later. Neither the menu's partial grey
mode nor its focus changes that (tried: without `partialGrey` first frame 531 ms, a non-focusable popup 532 ms).

**What MonoPaint's first frame is.** MonoPaint's menu button and tool buttons draw a press outline through
`SelectionFeedback` on the pen's down (`PageActionButton`, `PressOutline` with a feedback session), so its 80 to 110 ms
"first frame" in "Device timing" is that box, not its menu or its marks. Scribulor's column has no press outline since
"Fast pickers" (a box on and off is two more grey updates the panel runs one after another), so its first frame is the
change itself.

**What changed.**
- `SelectionFeedback.updateParts` (upstream file): a tool pick captures only the patches of the updates gathered inside
  it (the old row, the new row, the swatch bar, the Dropper, Play), each before its change and after them all, not the
  135 × 2401 column twice. `differences` splits each patch's changed pixels into boxes wherever more than 5 unchanged
  rows or columns part them, and each box is checked for ink and paper on its own. Every ink and paper box goes out in
  mode 5 (mode 9 where the firmware has the fast plane), the rest in one grey update over their union, drawn again from
  the column. A slot's mark is ink on the paper of the bar's room, so a pick stays mode 5 whatever grey strokes the rows
  between hold. One session a mode a pick, over the union of the patches (open measured at 2 to 3 ms, so not kept).
- `InkWash.backUpToolbar`: the backup is written 2 s after the last change, not on the tap (`select` 36 ms to 2 to 6).
- `SwatchBar.show`: the strip and the Dropper are drawn as paper for the eraser and the smudge tool, no
  `setVisibility`. `GreyStrip.hidden` (paper, no touch), the Dropper a paper `ToolButton.overlay` in its own update
  gathered into the pick's, `InkWashBar.tapDropper` does nothing while it is hidden.
- `InkWashBar.showBrushLine`: for the eraser and the smudge tool the preview's bitmap is paper, kept in its place, so the
  line is not laid out again. The tool's name now stays where a brush's name is, after the preview's room.
- `PaintActivity.menu` (upstream file): the menu is built, placed and drawn into a bitmap in `built`, and the file menu's
  is kept (keyed by its rows, marks, anchor, size, turn and finger or pen) and made ahead at the first idle moment
  after the drawing screen gets its focus (`prepareFileMenu`). On a tap it is put on the panel at once through
  `SelectionFeedback.presentAhead` (one grey update where the window will be), then shown as before.
- `PickerBench`: a self check of `differences` (two marks apart, two boxes), and a pause before each menu opening so
  Redo's frames don't run into it.

**Measured** (`PickerBench`, the checks build with `-PfastChecks`, the Manta, Chauvet 2606141001, two rounds,
before is `c445dfc4`). "First" is ms from the bench's pen down to the panel's first frame. The bench waits for the app to
be idle between the down and the lift, so its click comes 45 to 65 ms after the down, later than a real tap's.

| Gesture | Before: first, frames, quiet after lift | After |
|---|---|---|
| Slot pick | 223 to 278 ms, 37, 423 to 431 ms | 185 to 207 ms, 38, 427 to 440 ms, 0 Android frames |
| Eraser pick | 230 to 258 ms, 73, 828 to 830 ms, 2 Android frames of the column and line | 176 to 188 ms, 38, 428 to 442 ms, 0 Android frames |
| Slot pick after the eraser | 262 to 270 ms, 73, 843 to 844 ms, 2 Android frames | 161 to 185 ms, 38, 423 to 438 ms, 0 Android frames |
| Menu open | 504 to 588 ms, 36, 597 to 621 ms | 158 to 189 ms, 72 to 75, 621 to 643 ms |
| Menu close (a tap off it) | 249 to 279 ms, 51 to 105, 0.6 to 1.3 s | 192 to 235 ms, 51 to 112, 0.6 to 1.3 s (unchanged) |

Main thread from the click to the first update: slot pick about 95 ms before, 35 to 45 after. Menu about 120 ms plus
the firmware's 350 before, 30 to 40 ms after (`presentAhead`, mostly the driver taking a 450 × 1136 grey update).

**Not changed, and what is left.**
- The menu's frames went up: our grey update (16 to 18 frames), then the firmware still drives its own update of the
  new window when it shows it (about 36 frames, as before). The settle is about the same. A firmware that passes by the
  pixels already shown would make it one pass.
- The brush line is still its own mode 7 update after the column's (its preview is grey), 21 of a pick's 38 frames.
- The first frame is still the change itself, about 95 ms after it reaches the driver. A press outline like MonoPaint's
  would show sooner but adds two grey updates the panel queues before the mark.
- In "Device timing" the slot pick's column was noted as mode 7 every time. On the bench's toolbar it was already mode
  5 (the rows between were ink and paper). Not measured on a toolbar whose rows hold grey, where the old path went mode
  7 and the new one stays mode 5.
- Not checked by eye: mode 5 for the marks over many picks (a faint trail, as the size slider's tab once left).

| File | Lines | What |
|---|---|---|
| `SelectionFeedback` | +165 −1 | `updateParts`, `presentAll`, `part`, `differences`, `presentAhead`, gathered updates register their patch. |
| `PaintActivity` | +58 −9 | `menu` split into `built` (kept for the file menu) and its showing, `prepareFileMenu`, made ahead on focus. |

**Checks.** Desktop RAN 127 (ModularBenchChecks failed its timing under load, as it does). `PickerBench` (the checks build, `-e pickerBench true`): the `differences` self check, every
pick's stale check (the Android frame after it matches what the panel was given). Device time about 22 minutes, the
checks app, its test APK and its folder removed after.

#### A clean page after a pinch, a pan or the eraser (2026-10-25, branch `display-ghosts`): upstream files touched

The owner, 2026-10-25 (10:15 to 12:50, fill brush, Fineliner, eraser, much zooming): ghosts while zooming and panning
that took several of the Supernote's swipe refreshes, and erased marks showing again until a refresh. A pinch or pan
puts a frame on the panel every 16 ms through the page's grey session, each a mode 7 update over pixels the one before
is still driving; the eraser's moves and its ring do the same over a small area (469 presents in one 2.5 s stroke).
Pixels changed mid-update are left part-driven, and the driver records them as shown, so the auto-refresh's forced
mode 7 passes them by (the panel's side, above: it drives nothing on this firmware). Nothing else cleaned the page after
a pinch: `endNavigation` ended it without any refresh, and a firmware ACTION_CANCEL (about 145 two-finger ones in the
session) ends it the same way as a lift.

Now `endNavigation` (lift or cancel) and the eraser's end or take-back mark the page `ghosted`, and the next auto-refresh
(1 s after a pinch or pan ends, else the usual pen-away pause) draws the page alone again in the firmware's default
update (`Ui.cleanWindow` with the page as root, partial grey back after it) instead of the forced mode 7 pass. One blink
of the page, never under the pen or mid-pinch; other pauses are as before. The plain pen-away pause no longer
refreshes at all (the owner, 2026-10-25: "do away with the penaway"): only a ghosted page is cleaned then, and Brush
Lab's pad drops its pen-away pass too. That forced pass, with the bar's captures,
held the main thread 0.5 to 1.2 s in the log ("Skipped 32 to 74 frames" right after 19 auto-refreshes).

| File | Lines | What |
|---|---|---|
| `DrawingPad` | +26 −6 | `ghosted`, `NAVIGATED_MS`, `refreshSoon(reason, delay)`; `endNavigation`, `finishStroke` (an eraser), `cancelFingerStroke` set it; `autoRefresh` cleans the page when it is set. |
| `Ui` | +2 −1 | `cleanWindow`'s log names the view cleaned. |

Not checked on the Manta (no device in this change). Generic: any MonoPaint build on this firmware leaves the same
ghosts after a pinch.

## Press feedback (2026-10-25, branch `press-feedback`): upstream files touched

The owner liked that MonoPaint feels snappy, partly because its first frame on a press is an outline ("Snappy UI 2").
Scribulor's slow buttons showed nothing until their answer: a menu (the firmware adds about 350 ms for a new window), a
page turn, the notebook. Now a slow button's press is its box inverted (ink, the icon paper) on the pen's or finger's
down, in one binary update, and the click lets it go with no update of its own (KOReader's `Button`: the unhighlight is
not fenced, it blends into what the callback draws). The tool slots, the strip, the slider, Undo and Redo keep no press:
their answer is already quick, and a press would queue in front of it.

**What changed.**
- `ToolButton.slow()`: the press for Menu and Play (`InkWashBar`), and the page bar's arrows and Notebook (`PageTurner`).
  One helper, the house `PressOutline` given the feedback session and `inverted`.
- `PressOutline.set`: an inverted press goes through `SelectionFeedback.updateBinary` (mode 5 on the Manta, 8 frames,
  about 145 ms). `ToolButton` draws it with `selectionInverted`'s look (`fillBox`, the icon white) through
  `ControlRaster` as ink and paper (its dots for the soft edges), so the patch passes `opaqueBinary`.
- `PressOutline.clicked` (from `ToolButton.performClick`, before the action): unpressed and the button invalidated, so
  Android's next frame, the one the action asks for, draws it again. The firmware drives the box back to paper with that
  frame (it drives what differs from what it holds, "The brush that would not switch"). A press let go off the button
  (cancel, a move off it) is upstream's one update. On the Nomad's fast plane, which Android's frames don't clear, the
  click lets go in an update as upstream's.

**Measured** (`PickerBench`, `-e pickerBench press`: Menu and Play opened and closed, three rounds; the checks build
with `-PfastChecks`, the Manta, Chauvet 2606141001; before is `e2b3d4d6`, the bench alone). "First" is ms from the
bench's pen down to the panel's first frame.

| Button | Before: first, quiet after lift | After: first, quiet after lift, the press |
|---|---|---|
| Menu | 192 to 220 ms, 612 to 620 ms | 139 to 169 ms, 503 to 569 ms, one mode 5 present 52 × 48 |
| Play | 197 to 237 ms, 600 to 645 ms | 113 to 146 ms, 532 to 559 ms, one mode 5 present 43 × 43 |

The button's own Android frame (the let-go) comes with the click, 140 to 190 ms after the down, before the page's redraw
on the focus change. Closing a menu is unchanged. Page arrows and Notebook not timed (the bench's drawing has one page).

**Not done.** Idea 12 (build a menu while the pen hovers over its button): the file menu is already made ahead at the
first idle moment (`prepareFileMenu`), and keeping Play's or any other menu would mean generalising that one kept menu
and its key, not a small change. Brush Lab's way in (a tap on the brush line) and the menu's own rows (Tools, Library)
have no press: they are not `ToolButton`s.

| File | Lines | What |
|---|---|---|
| `PressOutline` | +21 −1 | `inverted`, its press binary, `clicked`. |
| `ToolButton` | +6 −4 | `slow()`, the inverted look while pressed, `clicked` before the action. |
| `ControlRaster` | +4 −2 | `draw(…, binary)`: ink and paper on any panel when asked. |

**Checks.** Desktop RAN 128. `PickerBench` press mode (above). Not checked by eye on the Manta: the box's look, and
whether the let-go's grey pass shows as a flash of the box. Generic: any MonoPaint button could use `slow()`.

## Side bars alone (2026-10-25, branch `slot-redraw`): upstream files touched

The owner: "the brush slots.. they dont need to update so often, just the line indicator beside", then "most of the
things now use the bar to indicate it's active so buttons and selections should be pretty quick". Every control whose
choice is a side bar (the eight slots, the eraser, the smudge and stamp tools, Play, the Lasso, the Dropper) is a
`ToolButton` with two `SlotTile.Face`s that differ only by that bar.

**What a pick did before** (`PickerBench -e pickerBench slots`, below). The panel already got only the bars:
`SelectionFeedback.differences` cut each patch down to its changed pixels. But `ToolButton.markerArea` was the whole
button for a two-face button, so every pick captured each tile whole before and after (its stroke or icon painted
again), the union kept for Android's next frame spanned the rows between, and the Lasso and the Dropper marked
themselves and the tool they give way to in separate updates, each a mode 7 grey pass. A size or grey picked updated
the Dropper (two captures) though nothing about it changed, and any `showBrush` (a Tools page or Brush Lab return,
a notebook's toolbar entered or left, a tool kit's Undo) drew every slot's stroke again in the background and updated
each row on the panel.

**What changed.**
- `SlotTile.sideBar` (the one geometry `draw` uses too) and `Face.sideBar`: where a row's bar goes. `ToolButton.markerArea`
  (upstream file, +10 −4 with `iconBounds`, which `onDraw` shares) returns it for a button whose faces are bar rows, so
  `mark` captures, compares and keeps for Android's next frame that strip alone. One place, every bar control.
- `InkWashBar.lassoChanged`: the Lasso's, the Dropper's and the tool's marks (and Play's) in one `updateParts` of the
  column, as a tool pick's, so they go out together in the binary update.
- `InkWashBar.showBrush`: a row is drawn again only when its brush (`Brush.identity`) or its size changed (`trayShown`,
  in place of `looped`). The picture follows the brush, not its size or grey (`ToolSwatches.picture`).
- `SwatchBar.show`: the Dropper is updated only when it is shown or hidden.
- `SlotTile.Face.painted` counts row paints for the bench, which now has a `slots` mode: picks of slots, the eraser, the
  Lasso and the Dropper, a stroke, a size and a grey, and a self check that the marker area is the bar's width.

**Measured** (the checks build with `-PfastChecks`, the Manta, Chauvet 2606141001, two rounds; before is `115332ab`
with the bench and a temporary log of each present). Bars are 9 px wide (5 dp), "quiet" is from the lift.

| Gesture | Before: presents of the column, row paints, panel frames, quiet | After |
|---|---|---|
| Slot pick | mode 5, the two bars 11 × 64 (cut from two whole 120 × 64 tiles, the swatch bar and the Dropper), 14 row paints, 38 to 39 frames, 419 to 437 ms | mode 5, the two bars 9 × 64 (strips captured), 10 paints, 32 to 34 frames, 372 to 383 ms |
| Eraser pick and back | 10 to 11 paints, 38 frames, 419 to 447 ms | 10 to 11 paints, 33 to 34 frames, 360 to 383 ms |
| Lasso or Dropper on or off | two separate mode 7 updates (11 × 47, 11 × 64), 4 paints, 22 frames, 252 to 275 ms | one update, two mode 5 bars (9 × 43, 9 × 64), 6 paints, 9 frames, 105 to 123 ms |
| Size or grey tap | 2 paints (the Dropper) | 0 |
| A stroke on the page | 0 | 0 |

First frame after the down is about the same (slot 185 to 207 ms, Lasso 148 to 182 ms): the column's update takes 80 to
125 ms on the main thread with the pick's own work, not the captures. The stale check (Android's next frame of the
column against what the panel was given) matched every row after each round. Not measured: a return from the Tools
page or Brush Lab (the bench does not open them), where unchanged slots no longer draw at all.

| File | Lines | What |
|---|---|---|
| `ToolButton` | +10 −4 | `markerArea` of a bar row is its side bar; `iconBounds`, shared with `onDraw`. |

**Checks.** Desktop RAN 128. `PickerBench -e pickerBench slots`. Not checked by eye on the Manta. Generic: none (Scribulor's
faces only).

## The pick's bar on the down (2026-10-25, branch `pick-speed`): upstream files touched

Goal: a pick's first frame as soon as MonoPaint's (104 to 114 ms, "Device timing"). Timed on the Manta with SystemClock
stamps from the pen's down through the pick (taken out again), `PickerBench -e pickerBench slots`.

**Where the time went** (a slot pick, ms after the bench's down; 2 rounds):
- The down reaches the button 15 to 30 ms after the bench sends it; the lift, and with it the click, 45 to 60 ms after
  (the bench waits for the app to be idle in between). Nothing was drawn on the down: the pick ran on the click.
- The click's main thread until the first box went to the driver: 30 to 60 ms. `swatches.show` 8 to 11 (its whole
  120 × 336 bar captured as a part), the parts captured again and compared 13 to 25 (the swatch bar's `differences`
  alone 5 to 16), the before and after bitmaps over the union of the parts (120 × 923 to 1141) 3 to 5, the two marks 4
  to 9, `iconButton`'s click to `selectTool` 1 to 7, a session 2 to 3.
- The driver: the panel's first frame 85 to 100 ms after the first box goes, whatever the app does.
- After the first box, the second box's `ioctl` blocked the main thread 65 to 70 ms every pick (the driver holds a
  second update sent right after the first), then the brush line's update 15 to 37 ms.

So the click alone, however lean, left the first frame at the lift plus about 95 ms. MonoPaint's first frame is its
press outline on the down ("Snappy UI 2").

**What changed.** A bar row's press is its side bar (`ToolButton.bar`, `PressOutline.bar`): on the pen's or finger's
down the new bar goes out in the binary update over the bar alone (two captures of 9 × 64 and one session, about 5 to
14 ms after the down reaches the button). The click's `mark(true)` ends the press in the mark (`press.pressed` cleared
inside the mark's change), so its part shows no change and the pick's update sends only the old bar, the swatch bar
and the line. A press slid off (or cancelled) takes its bar back in a binary update; a long press does too, unless it
took the tool up (the smudge tool's). The slots, the eraser, the smudge tool, the Lasso and the Dropper; not Play (its
press stays the inverted box). The old bar no longer queues behind the new one, so the 65 to 70 ms block went with it.

**Measured** (the checks build with `-PfastChecks`, the Manta, Chauvet 2606141001; before is `cdad8878` with the
timing stamps, after without; first frame from the bench's down).

| Gesture | Before: first frame | After: first frame | Presents of the column |
|---|---|---|---|
| Slot pick | 181 to 218 ms | 110 to 146 ms (180 once, the first pick of the run) | the new bar on the down, mode 5 9 × 64; the old bar and the swatch bar at the click |
| Eraser pick | 184 to 188 ms | 132 to 146 ms | as a slot's, 9 × 43 on the down |
| Lasso, the Dropper taken up | 146 to 181 ms | 111 to 130 ms | 9 × 43 on the down, the tool's 9 × 64 at the click |
| The Dropper put down | 146 to 181 ms | 155 to 171 ms (unchanged) | its bar was shown already: nothing new to show on the down |

Quiet after the lift, panel frames and Android frames as before (slot 415 to 440 ms, 38 frames, 0 Android frames). The
stale check matched every row in both rounds. New in the bench: a press slid off slot 2 leaves it unpressed and
unmarked and slot 1 picked.

**Not changed.** The click's own work (the swatch bar's capture and compare, the line's update) is no longer before the
first frame, so it stays as it was. The driver's 85 to 100 ms from the hand-over is the panel's. MonoPaint's 104 to 114
ms came from the shell injector, which was not run here (the bench reads 15 to 25 ms later).

**Risks.** A pick now shows two bars for the time between the down and the lift (the old one goes at the click). A
press that slides off shows a bar on and off again, two binary updates. Not checked by eye on the Manta.

| File | Lines | What |
|---|---|---|
| `PressOutline` | +8 −1 | `bar`: the press as the bar, in a binary update over `markerArea`. |
| `ToolButton` | +12 −2 | `bar()`, the bar drawn while pressed, `mark` ends the press, `performLongClick` lets it go. |

**Checks.** Desktop RAN 129. `PickerBench -e pickerBench slots` (the slid-off check). Release build EXIT 0. Device time
about 15 minutes, the checks app and its test APK removed after. Generic: any MonoPaint button whose selection is a
small mark could show it on the down.
#### Pinch and pan in one binary frame at a time (2026-10-25, branch `eink-frames`): upstream files touched

Research idea 2 (binary while the image moves, grey once still). A pinch or pan sent the whole view in mode 7 at every
drawn frame, over pixels the frame before was still driving, so the page needed its clean blink after every gesture
("A clean page after a pinch", above). Measured first (`LiveStrokeBench -e display true -e probe true`, the Manta,
2606141001): a full-screen mode 7 update runs its 21 frames slowly, done about 1 s after the hand-over (310 to 365 ms
up to a quarter of the screen); mode 5 is done 180 to 240 ms after it at any size; the first frame comes 80 to 140 ms
after the hand-over either way. So each grey pinch frame (one every 100 to 120 ms, the render's pace) went over
several still running.

**What changed.**
- `PresentPace.sent`/`busyFor` (pure): the areas the panel still drives, in the driver's pixels, and until when
  (`panelMs`: mode 5 250 ms; grey 370 ms, a whole screen about 1 s). `DrawingPad.flush` records every present that
  went (`result > 0`) and, asked to `hold`, keeps a pending patch over a busy area for the retry at its end (newest
  pixels then, merged by `DirtyRegions` meanwhile) instead of sending it over pixels mid-update. The retry holds as the
  flush that posted it did (`holding`). `DirtyRegions` gives patches by index so held ones stay.
- `DrawingPad.startNavigation` (both starts, two fingers and No Erasing's pan): opens a mode 5 session
  (`Device.fastStrokeMode`) on the view's raster, the page's dots, so every frame goes in mode 5. `drawNavigationFrame`
  draws the next frame only once the panel is done with the view (rescheduled at the end otherwise), so frames never
  overlap. `endNavigation` (lift or cancel) shows the last view in grey through the page's session, held until the last
  mode 5 frame is done. Where there is no mode 5 (the Nomad, 3.30.45) or the fast session fails (a grey pixel),
  frames go grey as before but are paced the same way, and only then is the page marked `ghosted` for its clean.
- `DirectEink.mode`: the session's driver mode, for the pace.

| File | Lines | What |
|---|---|---|
| `DrawingPad` | +66 −20 | `flush(force, hold)`, `busyFor`, `startNavigation`, `drawNavigationFrame(last)`, `endNavigation`'s grey last frame; the clean only after grey frames. |
| `DirectEink` | +3 | `mode`. |
| `DirtyRegions` | +3 −2 | `size`, `get(i)`, `remove(i)` for `first`/`removeFirst`. |
| `PresentPace` | Scribulor's | `panelMs`, `sent`, `busyFor`. |

**Measured** (`LIVE=1 scripts/bench_device.sh -e display true -e rounds 3`, the bench's 0.5 s pinch, a page of Brush
pen strokes). Before → after, per gesture:

| Gesture | Frames sent | Panel frames | Last frame after the lift | Clean blink |
|---|---|---|---|---|
| Pinch in with a pan | 4 to 5 grey → 2 binary + 1 grey | 140 to 165 → 72 | 2.09 to 2.15 s → 0.57 to 0.60 s | one (about 105 frames) → none |
| Pinch out to the fitted page | 4 to 6 grey → 2 to 3 binary | 145 to 190 → 108 to 176 | 2.47 to 2.72 s → 0.95 to 1.78 s | one → none |

The fitted page's end is still drawn through Android (the unscaled raster, `invalidate`), the tail after 0.9 s.

**Not checked by eye:** whether mode 5 frames over many pinches leave a faint ghost the grey last frame doesn't clear
(it drives only pixels that changed since the last binary frame). If they do, put `ghosted` back for binary frames too.
The bench: `PanelFrames` reads the frames through the shell where the app may not read /proc/interrupts (the Manta):
`scripts/bench_device.sh` puts the loop on the device for the run.

Generic: any MonoPaint build on this firmware pans the same way, and its grey frames stack the same.

#### The eraser, its ring and the sliders wait for the panel (2026-10-25, branch `eink-frames`): upstream files touched

Research idea 3. The eraser sent 469 presents in one 2.5 s stroke (the bench's 1.5 s stroke: 517 to 578), its moves and
its ring every few ms over pixels the last grey update still drove; a slider's tab one mode 5 present per pen move. The
pinch's rule (above) now covers them: a present over an area the panel still drives is held, the newest pixels sent
once it is done, merged with what changed meanwhile. The pen's own ink is never held.

**What changed.**
- `DrawingPad`: `erasing()`; the eraser's presents (`presentInput`), its last ones at the lift (`endStrokePreview(then,
  hold)`), a finger eraser taken back (`cancelFingerStroke`) and the ring's frames (hover, or the eraser's) flush with
  `hold`. The eraser no longer marks the page `ghosted`: its updates no longer overlap, so no blink after erasing.
- `SelectionFeedback` (upstream): its own `PresentPace`. A kept session's present (the same patch again: a slider's tab,
  the grey strip's marker, the droplet) over an area still driven is held (`heldOwner`, `heldArea`, `heldDirty`,
  `heldMode`); `sendHeld` at its end captures the patch as it is then, through the kept session or one of its own if
  another view's present replaced it. `clean` and `redrive` (a marker's end) wait for the area too (`waits`), dropping a
  held present there, and are recorded (`sent`). The present's bookkeeping is `shown`.

| File | Lines | What |
|---|---|---|
| `DrawingPad` | +14 −10 | `erasing()`, `hold` at the eraser's and the ring's flushes; no `ghosted` for the eraser. |
| `SelectionFeedback` | +97 −12 | `pace`, the held present and `sendHeld`, `busyFor`, `waits`, `sent`, `shown`. |

**Measured** (the Manta, 2606141001). `LiveStrokeBench -e display true -e rounds 3`, the eraser's 1.5 s stroke over Brush
pen strokes, before → after: presents 517 to 578 → 14 to 17; panel frames while erasing about 190 → about 115, in
separate runs of 19 (one update each, never two at once); last frame 240 to 270 ms → 220 to 290 ms after the lift; the
clean blink about 2.1 s after (64 to 74 frames) → none. `PickerBench` (checks build), a 24-move drag: slider 26 presents
→ 6, panel frames during 48 to 51 → 28, quiet 607 to 637 ms → 659 to 707 ms after the lift; grey strip 26 → 5 to 6,
panel frames 52 to 54 → 38 to 40, quiet 174 → 518 to 603 ms (its clean now waits for the marker's last update).

**Risks.** The eraser's trail and the ring follow in steps of about 0.37 s (one grey update), the tab about 0.25 s. The
panel times are fixed (`PresentPace.panelMs`): on a cold panel an update may still run when the next goes, and the ghosts
would come back with no blink after erasing to clear them. Not checked by eye.

Ghost budget per area (research idea 7): not done. With the pinch's and the eraser's updates no longer overlapping,
`ghosted` is left only for pinch frames sent in grey (no mode 5, or it failed), where the whole view changed anyway, so
a per-area budget has nothing to bound. Worth doing if the owner still sees ghosts after these two.

### Reverted 2026-10-25 evening

The owner found big ghosts the Supernote swipe refresh no longer cleared and an unusably slow eraser after the "eink-frames" (mode 5 pinch frames, held patches) and "eink-adapt" (learned panel times) merges; both are reverted (the sections above describing them no longer apply). Next: small A/B experiments per section before any wide change.

## Lasso options: Cut, Copy, Paste, the loop that deletes (2026-10-25, branch `lasso-options`): upstream files touched

The owner: "i was thinking the lasso, there should be options for lasso. in supernote there is a fast delete that
might be cool. or cut. and then the usual copy." Extended the owner of the selection, `DrawingPad`'s floating piece and
`LassoPlanes`; no new display path.

**What changed.**
- **The menu** (`InkWashBar.tapLasso`, rows from `DrawLayout.lassoRows`): Cut, Copy, Delete with a piece floating, Paste
  once one is kept; in use with nothing floating and one kept, Paste alone (`DrawLayout.lassoTap(on, floating, kept)`,
  which was `NOTHING` there). `app.menu`, so it opens and closes as every menu (`presentAhead`, the closed patch cleaned).
- **Kept** (`DrawingPad.kept`): a `FloatingSelection.copy()` (lifted from nowhere, its planes shared, nothing writes
  them). `cutSelection` is `deleteSelection` (now boolean) and keeps the piece; `copySelection` keeps it and leaves the
  page alone (it laid a copy and floated another until now: Copy then Paste does that). `pasteSelection` puts any piece
  down (`placeSelection`), then `floatSelection(kept.copy())` where it was kept, so it drags and goes down as a moved
  piece does, one Undo step, cutting nothing. A page of another size takes none (`FloatingSelection.fits`, which
  `carrySelection` now shares). In memory only: lost when the activity is recreated.
- **The loop that deletes** (`DrawingPad.lassoDeletes`, set by the Lasso's hold, `InkWashBar.lassoWays`, a `Theme.menu`
  of Select and Delete, the one in use marked): `liftLasso` lifts as before, then `erase` (Delete's body, shared) takes
  it off at once, wet paint dried first: the same `LassoPlanes.place(…, false, true)`, one Undo step, the selected pair
  and the fluid, permanent paint too, as Delete and the eraser's Everything; refused on a locked page; the Lasso is off
  under No Erasing as before. Shown by `renderArea` over the cut, `renderDirty`, `flush`, Delete's path. The Lasso's
  icon is Phosphor's trash in that way (`lassoFaces`, through `selectionFeedback.update` as the eraser's mark), its
  screen-reader name "Lasso: Delete" (`DrawLayout.lassoSaid`). Not saved: every start is Select.
  Since the same day (branch `trash-tool`) the hold, `lassoWays`, `lassoFaces` and `lassoSaid` are gone: the **Trash**
  (`InkWashBar.trash`, `tapTrash`, the eraser row's right cell) takes the loop up with `lassoDeletes` set, the Lasso
  with it cleared (`takeUpLasso(deletes)`); `lassoChanged` marks the Lasso or the Trash. Upstream: `DrawingPad.lassoDeletes`'s comment alone.
- **Icons:** Phosphor Bold `scissors` (Cut) and `clipboard` (Paste), `ic_ph_scissors`, `ic_ph_clipboard`.

| File | Lines | What |
|---|---|---|
| `DrawingPad` | +41 −27 | `lassoDeletes`, `kept`, `hasKept`, `cutSelection`, `copySelection` (keeps), `pasteSelection`, `deleteSelection` boolean, `erase` (shared), `liftLasso`'s Delete way, `carrySelection` through `fits`. |
| `FloatingSelection` | +2 | `fits(document)`: the page it came from is that size. |

Scribulor's: `InkWashBar` (+49 −8: `tapLasso`'s rows, `takeUpLasso`, `lassoFaces`, `lassoWays`, the hold), `DrawLayout`
(+15 −2: `lassoTap`'s `kept`, `lassoRows`, `lassoSaid`, the words).

**Checks.** `LassoChecks.pasted` (Copy leaves the page, Cut and its Undo, each Paste a fresh copy where it was kept,
moved or not, its own Undo, another page of the size, not another size), `LassoChecks.fastDelete` (the ringed piece
gone unmoved, permanent Pen ink and wash too, the rest kept, one Undo), `DrawLayoutChecks.lasso` (rows, the tap with
something kept, the said name). Not checked on the Manta by eye.

## Page turns move the marks alone (2026-10-25, branch `pagebar-underline`): upstream files touched

The owner: "i think we can avoid a refresh of the top bar as well unless there is a new page added, just move the
underline". The side bars' way ("Side bars alone") for the page bar: a turn puts on the panel what it changes, and the
bar is drawn again whole only when the pages change.

**What a turn did before** (from the code; not measured on the Manta, which was not connected). Every `updatePages`,
each turn too, went to `PageTurner.show` and `Strip.shown`: every preview invalidated, Android's frame of the whole strip.
In that frame:
- the page turned to drew blank: the page drawn on is never drawn from its picture (`except`), and the sample of the page
  left did not count for it. `redrawLiveWhenIdle` 800 ms later sampled it and put it back (a second update);
- the page left drew blank too when it had been drawn on (its picture is made as it is left, `leavingPage`), then
  `picturesArrived` twice (the picture, then its preview's dots): blank, blank, its picture;
- in a notebook with more pages than slots the window was round the page drawn on (`NotebookLayout.window`), so every
  turn slid it a tile and every preview changed.

**What changed.**
- `PageTurner.show(index, count, turned)`: `PaintActivity.changePage` (every turn: ‹ ›, a tap or riffle on the strip,
  Timed Pages' Next Page) says `turned`. With the same count and the page turned to in the window, the arrows, the number
  and the strip are captured, the turn applied, and the changed pixels go out in one `SelectionFeedback.updateParts` of
  the bar (ink and paper boxes binary, the rest one grey update), with no Android frame asked of the strip. Anything else
  (a page added, deleted or moved, a layer, another notebook, a page turned to off the window) is `Strip.shown` as before.
- `Strip.drawnOn`: the previews' marks (ink frame, underline, ghost tabs) follow the page the bar last showed, not the
  book's, so the capture before the turn shows what the panel shows.
- The window is kept while the page drawn on shows in it (`StripGesture.shows`, pure), and goes round that page only when
  it leaves it or the pages change.
- A preview is drawn from the sample (`LayerThumbnail.renderPage`) only for the place and layer it was taken of
  (`livePlace`, `liveLayer`), the page left too, until its picture is ready (`NotebookScreens.drawStackPreview` now says
  whether it drew everything). A page turned to with no sample shows its picture, as it showed before the turn;
  `redrawLiveWhenIdle` no longer samples it until it changes. When the sample moves to another page, that page goes back
  to its picture in the same update (the strip's).

| File | Lines | What |
|---|---|---|
| `PaintActivity` | +5 −3 | `updatePages(turned)`; `changePage` says a turn. |
| `WidthInstrumentation` | +2 −2 | `-e pickerBench pages`. |

Scribulor's: `PageTurner` (above), `StripGesture.shows`, `NotebookScreens.drawStackPreview`/`drawPicturePreview` return
whether they drew, `InkWashBar.pagesChanged(…, turned)`, `PickerBench` (`pages`: a notebook of four pages in the checks
app's folder, ‹ and › three times each, a stroke then a turn off and back, two rounds; the page bar's previews listed
among the views Android draws again).

**Triggers of the page bar, after.** A turn: one `updateParts` of the bar (above). A stroke: the page's preview
800 ms after it settles (`redrawLiveWhenIdle`, one preview; the strip when the sample moves off another page). A picture
ready (`picturesArrived`, the strip's fast update of what changed). Tracing paper changed, a riffle's window sliding at an
end and a riffle or lift let go off its place (`Strip.update`). A timed page's blink (the number alone). The +, a page
deleted, moved or added as a layer, another notebook: `Strip.shown`, all of it. The auto-refresh after a pinch
(`InkWashBar.invalidate`): the bar redrawn and cleaned, unchanged. A save offers the page's picture but draws nothing.

**Checks.** Desktop RAN 128 (`StripGestureChecks.tiles`: the kept window). Not measured on the Manta and not checked by
eye: the Manta was not connected. To measure: the checks build (`-PisolatedChecks -PfastChecks`), `-e pickerBench pages`,
before is `7f92804b` with the bench's `pages` mode alone.

**Risks.** The window no longer centres on each page turned to: it slides only when the page leaves it, then goes round
it. A capture that differs from the panel (a turn within 800 ms of a stroke on a page turned to) leaves pixels for
Android's next frame of the bar, which the firmware drives where they differ. Generic: none (Scribulor's bar).

## The side bar rearranged (2026-10-26, branch `bar-layout`): upstream files touched

- **Upstream files touched:** `PaintActivity` alone: the menu's Finger row removed, and `fingerMenu`'s pick tells the
  bar (`InkWashBar.showFinger`); the menu is now opened from the column's Finger button. Everything else is Scribulor's
  (`DrawLayout.COLUMN` and its parts, `InkWashBar`'s rows, `DrawLayout.brushWords` with a Basic tool's line).
- **Layout:** the smudge tool and Finger, then the eraser and the Trash, straight under the tray (`Part.TOOLS`, no gap);
  the Dropper and the Lasso (`Part.WET`), then Play and the wet indicator (`Part.PLAY`); Template Visibility in Menu's
  row. The same rows as before, two gaps not three (`GAPS`), so the Nomad in landscape still fits at its least
  (`DrawLayoutChecks.NOW`: 613 dp and 0 to spare). The brush line's name room is 310 dp (280 and the drop's 30).
- **Checks:** `DrawLayoutChecks` (order, rows, heights, the menu without Finger, the Finger button, the brush line's
  description), `NomadUiChecks.layout` (the rows on the device; not run here). Generic: none.

## The page fitted whole and centred (2026-10-26, branch `zoom-fit`): upstream files touched

- **Why:** the owner, "when switching to zoom mode or back.. will some part of the image be obscured? let's make it
  fit neatly in both". The pad is laid out beside the bars, not under them, so its size is the free area; a page of
  that size filled it at 100% already. Any other page (made the other way up, made in Zen Mode, from an older layout)
  opened at 100% in MonoPaint's home placement, an edge hidden past the pad, and Zen Mode left a smaller page in the
  top corner.
- **Upstream files touched:** `CanvasViewport` (+10 −14): while untouched since a reset, `configure` fits the page
  (`fitScale`, at most 100%) and centres it on whole pixels, so a change of room (Zen Mode, a turn) fits it again;
  `opening`/`openActualSize` removed. The pinch's floor, `minZoom`, is unchanged (100%). `DrawingPad` (+2 −9): the
  100%-at-home opening and `homeOnScreen` removed; `actualSize` kept for MonoPaint's Expand canvas. `ZoomButtonTouch` (+2 −2): the double tap fits instead of going to 100%. Generic
  in part: a centred fit suits MonoPaint too, but its own "open expanded pages at 100%" choice is reversed here.
- **Cost:** a page not the pad's size is now drawn through the zoomed raster (`ViewportBitmap`) while fitted, as when
  zoomed; a pad-sized page is not (identity, as before).
- **Checks:** `ZoomFitChecks` (Manta and Nomad, either way up, pages from either turn and from Zen Mode: whole,
  centred, the fitted scale; the double tap and Zen Mode back to exactly that view; pinch floor 100%), `ViewportChecks`
  updated; `ZoomButtonChecks` (device, not run here) updated.

## Layout v3 (2026-10-26, branch `layout-v3`): upstream files touched

- **Upstream files touched:** none. `PaintActivity` already sizes the foot row from `DrawLayout.footHeight` and
  `BRUSH_LINE`, so the taller brush line needed no edit there; the pad is laid out above it, so the zoom fit
  (`CanvasViewport.configure`) sees the smaller free area.
- **Layout** (the owner's mockup): `DrawLayout.COLUMN` is Menu, the tray, `Part.TOOLS` (the smudge tool and the eraser,
  a tray row each, `TOOL_ROWS` counted with the slots), `Part.BLOCK` (undo and redo, the Dropper and the Lasso, the
  Trash: `BLOCK_ROWS` icon rows), the faces, `Part.PLAY` (the wet indicator and Finger, Template Visibility and Play:
  `PLAY_ROWS` small rows), the countdown, five gaps (`GAPS`). Icon rows 44/40/30 dp (were 56/48/40). `GRID_ROWS`,
  `Part.UNDO` and `Part.WET` are gone. The smudge tool's and the eraser's rows show the owner's drawings
  (`InkWashBar.drawing`, `res/drawable/tool_smudge.xml`, `tool_eraser.xml`: traced vectors in px since 2026-10-26, drawn as Drawables); `heldBrush` and
  `ic_dirty_brush_paint` went (a dirty smudge brush shows `SlotTile`'s dab). The brush line is 32 dp, its preview 80 x
  32 dp (were 18, 40 x 14).
- **Checks:** `DrawLayoutChecks` (order, counts, heights, `NOW`: 924 and 291, 874 and 0, 830 and 32, 613 and 0; the
  drawings' greys; the preview's size), `ZoomFitChecks` (the Manta's pad 1785 x 2387 px, 2401 before), `NomadUiChecks.layout`
  (device; not run here), `DrawScreenPictures`. A page made at the old pad size now shows fitted, about 99%, through the
  zoomed raster (see the zoom fit above). Generic: none.

## Layout v4 (2026-10-26, branch `layout-v4`): upstream files touched

- **Upstream files touched:** none. `DrawLayout.COLUMN` puts `Part.FACES` before `Part.BLOCK` (the owner: "move undo
  redo eye drop lasso and recycle to below picker"); the same parts, rows and five gaps, so the heights and `NOW` are
  unchanged. **Checks:** `DrawLayoutChecks.order`, `NomadUiChecks.layout` (device; not run here).

## Slot drawings (2026-10-26, branch `slot-drawings`): upstream files touched

- **Upstream files touched:** none. A tray row shows its brush's picker drawing (`DrawLayout.drawing`: the brush's
  Basic tool, `ToolPresets.find`, looked up in `PICKER`), cut `TIP_TRIM` at its handle end and flush left, through
  `InkWashBar.drawing`, the smudge tool's and the eraser's path, which now draws any `Drawable` (PNG or vector).
  Undrawn brushes keep `WildSample.slotTray`'s stroke; Finger's menu keeps the stroke for every slot (`trays`, drawn in
  the background). **Checks:** `DrawLayoutChecks.slotDrawings`, `DrawScreenPictures` (the mockup's tray). Generic: none.

## Slot drawings matched by origin (2026-10-26, branch `slot-drawings-match`): upstream files touched

- **Upstream files touched:** none. `ToolPresets.origin`: the Basic tool a brush came from (a brush keeps no "from"):
  `find`, else the one preset of its style and seed (a rename and Brush Lab's dials keep both), its name less a copy's
  number choosing among presets sharing one, else the preset so named. `DrawLayout.drawing` uses it, and a tool not in
  `PICKER` takes its style's one drawing (Pencil: HB pencil), none where a style has several or none (Print since Ben-Day brush and Engraving pen joined
  `PICKER`, 2026-10-26, branch `drawn-round2`).
  **Checks:** `DrawLayoutChecks.slotDrawings` (the owner's three slot codes, a rename, a brush of its own). Generic: none.

## Rotate turns the page (2026-10-27, branch `rotate-canvas`): upstream files touched

- **Upstream files touched:** `DrawingPad` (`place` gives `PageView.map` no turn of its own, so the page lies upright in
  the turned canvas and `CanvasViewport` fits it unswapped, afresh on each turn (`fittedTurn`); the pen's signed tilt
  turned from the panel's axes by `PageView.turn`), `CanvasGravity` (the app's turn added to the display's, from the
  drawing screen's constructor only; Brush Lab's stays 0), `PngExport`, `PageOverview`, `DrawingBrowser` (no more
  turning a copy to match the app: the page is shown upright), `FullscreenController` (Expand canvas's margins with no
  turn), `PaintActivity` (the Rotate button 72 dp, its icon 36 dp). `WordsMode`: a new magnet's turn is 0. The page's
  tones are never turned; MonoPaint's counter-turn (`PageView.map`'s quarter) is kept for its checks. Fast e-ink: the
  turned page goes through the screen-sized viewport raster and `DirectEink`'s quarter-turned copy, the path landscape
  already took whenever the page was scaled. **Checks:** `PinchPanChecks.rotateTurnsThePage` (upright, whole, centred,
  largest fit; page → canvas → page at the fit and zoomed; tilt turned as gravity), `RotationPromptChecks` and
  `OrientationChecks` (device; not run here). Generic: none.

## Opacity on the shade strip (2026-10-27, branch `shade-opacity`): upstream files touched

- **Upstream files touched:** `PaintActivity` (`toolbarFixed` no longer private, so the strip's Opacity refuses in a
  fixed toolbar with its note). Scribulor's own: `GreyStrip` (`mirrored`), `SwatchBar` (the strip's long press),
  `InkWashBar.opacityMenu` (the panel, through `openPanel`), `DrawLayout.opacity` (Opaque only; the slot's brush with its
  Medium's Opacity dial set, an Undo step as Fling's). No engine change: every stroke resolves the slot's brush, so
  `ResolvedBrush` reads the dial as it was. **Checks:** `DrawLayoutChecks` (which brushes, kept per slot, read by the
  stroke, Undo). Generic: none.

## The owner's notes page (2026-10-27, branch `notes-1027`): upstream files touched

- **Upstream files touched:** `viewport_bitmap.c` (`nativeRender` takes `lines`: the view pixels beyond the page are the
  owner's fine lines, `backdrop()`, instead of white; one test per outside pixel, no new pass or layer),
  `ViewportBitmap` (`backdrop`, false unless the drawing screen sets it, so `dithered` and every copy stay white),
  `CanvasViewport` (`backdrop()`: under 100% as the readout rounds it), `DrawingPad` (`updateViewport` sets the raster's
  `backdrop` on every view change, before it is drawn), `PaintActivity` (Play's menu without Diary Page and the ready
  brushes; `playBrush(Brush)` removed). Scribulor's own: `WetIndicator` (the dry drop is the owner's
  `ic_drop_dashed`, drawn as the wet drops are; its dashed Phosphor path gone), `DrawLayout` (`DIARY`, `PLAY_BRUSHES`
  removed), `res/drawable/tool_eraser.xml` (the owner's new block eraser). MonoPaint's `WetDroplet` and `DropletIcon`
  (its hidden toolbar's rippling droplet) are untouched. **Checks:** `ZoomFitChecks.backdrop` (lines only under 100%,
  only the drawing screen asks), `DrawLayoutChecks` (Play's rows, the dry drop). Generic: none.

## Size tab as the shade tab (2026-10-27, branch `size-strip-ghost`): no upstream file touched

- The owner: "weird ghosting glitch with the size selector indicator. i move it and it blinks and sometimes isn't all
  displayed ... the shade picker doesn't have this same problem." Both tabs move through `StripMarker` over a narrow
  column of their own, and a lift of either is the same `SwatchBar.show` (one `updateBinary` of the tab and the dot); no
  relayout, brush line or slot row on either. The one difference: `SizeSlider` set `StripMarker.binary`, so its moves
  were the Manta's stroke mode 5 (which skips pixels it takes as unchanged and leaves a trail) and, 250 ms after the
  lift, `SelectionFeedback.redrive` drove the tab's whole column near-white then white, two grey updates: the blink.
- `SizeSlider` (Scribulor's own): no longer sets `binary`, so its moves are the strip's mode 7 and nothing runs after
  the lift on the Manta; its lift then puts the tab back where the size is (`follow(inUse)`, as `GreyStrip`), for a lift
  that changed nothing. `StripMarker.binary` and `SelectionFeedback.redrive` are left as they are, unused, so the
  binary path is one line to try again. **Checks:** `DrawLayoutChecks.extras` (the slider's marker as the strip's, no
  `pen.binary`, no relayout or Android redraw in either). Not tried on the Manta here.

## Sand timer (2026-10-27, branch `sand-timer`): upstream files touched

- The owner: "the per page timer, i'd like to put it in the toolbar ... just have a sand timer icon, press it to have it
  open a menu and you can set a timer", then a plain drawing timer, not Timed Pages.
- **Upstream files touched:** `PaintActivity` (`sand`, a `SandTimer`; `timedLook` counts it while resumed, shows its
  countdown or icon where no Timed Pages countdown or timed sections take the row, and its popup at zero once the pen
  is up; `countdownTapped` opens its menu there; `setSandTimer`; `onPause` pauses it; `dispatchTouchEvent` closes its
  popup on any touch). Scribulor's own: `SandTimer` (pure: choices, count, pause, zero once), `InkWashBar` (the row's
  icon, `timerMenu`, `timeUp`, `countdown` takes `sand`), `NotebookOptions.MINUTES` (was `OLD_MINUTES`, now shared),
  `DrawLayout` (comments: the TIMER row's room was already kept on every screen), `res/drawable/ic_sand_timer.xml`
  (a placeholder, listed in `docs/icons/TO-DRAW.md`). Timed Pages, `TimedPages`, the page record and the lock are
  untouched. **Checks:** `SandTimerChecks` (choices, countdown, pause, zero once, no page held), `DrawLayoutChecks`
  (the icon's cell fits the row on all four screens). Generic: none.

## Timed Pages a guide, Lock Pages a setting (2026-10-27, branch `timed-guide`): upstream files touched

- The owner: "i would like to be able to edit after the timer runs out, it's just a guide", then "could the lock be a
  setting in the notebook? like, timers lock pages?". At zero `TimedPages.tick` still runs the page's clock out
  (`PageClock.lock`, kept in the page's `TIME` record, unchanged), now `State.TIME_UP`; whether that locks the page is the
  notebook's **Lock Pages** (`NotebookOptions.lockPages`, off by default; `TLCK` in `BookOptions`, `TSRX` in the recovery).
  `PageClock.locks(timerLocks)`: a clock run out with time spent is a timer's and locks only under Lock Pages; one with
  none spent is a PDF's page (`PictureImport.pdfPage`, `lock(0)`) and always locks. Timers always spend 30 s or more, so
  old files tell them apart with no format change.
- **Upstream files touched:** `ToneDocument` (`timerLocks`, set by `DrawingBook.current`; `locked()` is
  `clock.locks(timerLocks)`, and `canUndo` / `canRedo` / the undo step use it; `unmarked` still counts any run-out clock
  as a mark), `PaintActivity` (`timedLook`: at `TIME_UP` the sand timer's popup with Next Page, `InkWashBar.timeUp(next)`,
  in place of the AlertDialog; the countdown row shows as an untimed page's once run out, unless locked; `sectionLook`
  fences a finished section only under Lock Pages), `DrawingBook` (`current` tells the page; `setOptions` /
  `restoreOptions` refresh it). Scribulor's own: `TimedPages` (reads the raw run-out flag, so a page run out never counts
  again), `LayerEdits.mergeDown`, `LibraryCard` / `NotebookScreens` (the Lock Pages row, Off and On, greyed unless
  `locksApply`), `NotebookRules` (`LOCK_PAGES`; `TIME_UP`, `TIME_UP_SAID`, `STAY` gone), `CardSheet` (Timed Pages' line).
  **Checks:** `NotebookRulesChecks` (off: editable at zero; on: locked; the PDF page locked either way; an older notebook
  reads Lock Pages off; file and recovery keep it on), `LibraryCardChecks` (ten rows, the row set in place),
  `TracingChecks`, `PageStacksChecks`, `NotebookOptionsChecks`. Generic: none.

## Housekeeping (2026-10-27, branch `cleanup-1027`): upstream files touched

No behaviour changes. Ten unused drawables deleted (`ic_blur_drop` and nine Phosphor icons); dead members gone
(`InkWashBar.drawTab`, `InkWash.selectPair`, `StayIn.pocketAt`, `ModularFeatures.FILL_PLANE`, `NotebookCovers.USE_PAGE`);
`NotebookLibraryBench` deleted (no script ran it); finished specs, plans, notes, mocks, dated reports and the web
prototypes but `modular-lab.html` moved to `docs/archive/`, sub-paths kept. Stale comments and docs brought up to date.
- **Upstream files touched:** `PaintActivity` (`playBrush` renamed `pickBrush`, the tool picker its only caller; comments).
  Generic: none.

## More room in the side column (2026-10-28, branch `toolbar-room`): upstream files touched

Undo and redo, Shapes, Play and Refresh left the column (`InkWashBar`, `DrawLayout`: two icon-block rows, two small
rows); the sand timer beside the wet indicator; Show Kinds a column button; Refresh on the page bar (`PageTurner.ghost`,
`DrawLayout.barParts`); Export Now and Delete Notebook at Notebook Settings' foot (`NotebookScreens`,
`LibraryCard.settingsLayout`). Undo and redo stay on MonoPaint's `MultiFingerTap`, unchanged.
- **Upstream files touched:** `PaintActivity` (the menu's rows: Play in, Export Now, Rename, Delete Notebook, Show
  Kinds, Daily Prompt and Replay Page out; Play's menu: Shapes, Daily Prompt, Replay Page in, Stamp, Surprise Me and
  Fling out, `toggleFling` gone; `exportNow` package-private for Notebook Settings). Generic: none.
- **Checks:** `DrawLayoutChecks` (the menu's rows, the column's rows, the page bar's parts), `LibraryCardChecks`,
  `NotebookExportChecks`.

## One Lasso, Refresh back in the column (2026-10-28, branch `lasso-merge`): upstream files touched

The Trash's button left the column: the Lasso's bar over a floating piece (`InkWashBar.lassoBar`, the label's popup,
`showLabel` split so it can take a place; `DrawLayout.LASSO_BAR`) holds Cut, Copy and Delete, and the Lasso's tap
opens Paste alone (`DrawLayout.lassoTap`; `lassoRows` gone). The edge strip's hold keeps `lassoDeletes`. Refresh left
the page bar (`PageTurner.ghost` gone, `DrawLayout.GHOST`, `GHOST_GAP` gone, `barParts` Zoom, the number, the gap) for
the Trash's row, the whole row its target (`InkWashBar.ghost`). The sand timer drawn as the eyes (`showModes`).
`Popups.bounds(root, view, area)` maps a part of a view. `ic_ph_recycle` and `ic_ph_scissors` deleted, unused.
- **Upstream files touched:** `DrawingPad` (+22 −7: `lassoBar()`, called where a piece floats, is grabbed, released,
  ended or carried to a page; `lassoDeletes`'s comment). Generic: none.
- **Checks:** `DrawLayoutChecks` (the Lasso's tap and bar, the column's rows, the page bar's parts), `NomadUiChecks`
  (Refresh's row).

## The blow dryer fast-forwards (2026-10-29, branch `dryer-fastforward`): upstream files touched

The wet indicator's tap runs what is wet on to its end instead of laying it where it is (the owner: "looks like the hair
dryer just stops the image as it is instead of accellerate the drying processing"): `WatercolorSession.hurry` (a Slow
wash's ticks, halos', a drifting bath's, wet paint's slices back to back with no pace and no work ahead; a bath that
can't drift prints at once; shown once; capped at `HURRY_MS`, then laid where it is), `DryingTicker.hurry`,
`WetIndicator.Host.dry`/`hurrying` (the dryer blows round while it runs), `BrushTestPad.dry`, `InkWashBar`, `TestArea`.
- **Upstream files touched:** `DrawingPad` (+2: `dryNow`, the bar's tap; `settleDrying` no longer the tap). Generic: none.
- **Checks:** `SettleWorkerChecks.hurried` (tapped equals left to dry: Slow washes, gravity, baths, halos exactly; wet
  paint within a tone). Bench: `ModularPerfBench dryer`.

## A prompt not acted on leaves the page (2026-10-29, branch `prompt-drop`): upstream files touched

The owner: "for prompts, if it's closed without doing it, remove it from the page". `ToneDocument.dropUnusedPrompt`
(nothing on the page but the card, `unmarkedButPrompt`, which `unmarked` now builds on; or no Undo step done since it was
given, `promptTop`, in memory only) and `PaintActivity.leavePrompt`, run before the page is kept: `changePage`,
`pageEdit`, `leaveNotebook`, `onPause`; `promptLook` drops a card found on opening with nothing else on the page.
- **Upstream files touched:** `ToneDocument` (+16 −3), `PaintActivity` (+17 −3). Generic: none.
- **Checks:** `DailyPromptChecks` (dealt then left blank, drawn on, drawn then undone, drawn on before, read from a file).
## Nib Preview, a notebook setting (2026-10-29, branch `nib-preview-setting`): upstream files touched

The owner: "for the dot preview.. have it enabled by default but able to be disabled in the notebook settings?". The
hover ring's dots are the notebook's **Nib Preview** (`NotebookOptions.nibPreview`, on by default; `NIBP` in
`BookOptions` and `TSRN` in the recovery, each written only when off, so every older notebook reads on). Built as Lock
Pages is: `LibraryCard.Part.NIB_PREVIEW` (the page group, after See-Through Pages), `NotebookScreens` (Off and On chips).
The Dropper's ring and the eraser's live ring are untouched.
- **Upstream files touched:** `DrawingPad` (+1: `hoverOutline` hides the ring while `app.rules().nibPreview` is off; no
  notebook, `NotebookOptions.DEFAULT`, shows it). Generic: none.
- **Checks:** `NotebookOptionsChecks` (its record alone, the file and recovery keep it off, absent reads on),
  `LibraryCardChecks` (eleven rows, the row set in place, the hover check in `DrawingPad`).
## Pressure range with a knee (2026-10-29, branch `pressure-range`): upstream files touched

Firmware 3.31.45 widened the Manta pen's pressure (hard strokes .63–.83, `docs/TILT_INPUT.md`), so MonoPaint's
`.05–.45` line read the whole firm half of the hand as one full press. `ToolSettings.travel` keeps that line up to .30
(travel .625) and runs on to 1 at .78; `pressure` is its inverse. The pen eraser (`InkWashSizes.diameter`, Scribulor's)
no longer broadens with lean: the owner's natural ~33° hold had it at full size from a usual press. `BodyStamper.diameter`
keeps the same lean boost for bodies with a Tilt cell (full from about .28 at that hold): unchanged, a brush's look.
- **Upstream files touched:** `ToolSettings` (`travel`, `pressure`, `KNEE`/`KNEE_TRAVEL`/`FULL`), `ToolStroke` (+1 −1)
  and `NativePen` (+1 −2): their own copies of the old line now call `travel`. Generic: any MonoPaint pen on that
  firmware wants the knee.
- **Checks:** `InkWashSizesChecks` (identical below .30, continuous there, 1 at .78, round trips); `ToolChecks`,
  `AirbrushChecks`, `EraseChecks`, `BrushTextureChecks` press .78 where they meant a full press; `EraseBothChecks` and
  `MirrorChecks` give their hands as travel (their pages unchanged); `WetPaintChecks` re-pins eight settled pages whose
  travel comes back through the knee 1 ulp apart (every mid-stroke page unchanged).
## Brush Lab cleanup and New, Open, Save (2026-10-29, branch `brushlab-cleanup`): no upstream files touched

The owner's Brush Lab edits: no toolbar band (the lab works on the slot it opened on), the drawing screen's picker box in
place of its sizes and grey bar, no Basic mode, no Tweak It, Open's sheet closes without a code; then option A, New,
Open, Save and Save as New with an Edited mark and a suggested name. All Scribulor's own classes.
- **Shared, not forked:** `PickerBox` is `SwatchBar`'s box (the grey strip and size slider over each other, the touch
  split on the diagonal) pulled out whole; `SwatchBar` builds it, Brush Lab builds another inside a `QuarterTurnLayout`
  turned 90°. `PickerBox.show` marks both and sets their screen-reader words for either screen.
- **Gone:** `SlotRow`, `LabLibrary`'s slots and variations (it keeps `sampler` and `traySampler`), `LoopCard.slot` and
  `setFixed`, `LabText.changes` and `at`, `BrushLab`'s variations, `setAdvanced`, `BACK_ROOM` and the Basic lock bypass,
  `PaintPreferences.brushLabAdvanced` (its stored flag is left unread). `ModularVariations` stays: the checks use it to
  roll brushes, and it mirrors the web lab's Tweak It.
- **Library:** `BrushLibrary.update(id, brush)` writes one entry over in its place (its id kept, stamped as used);
  `ToolSwatches.moveLook` moves a brush's own size and grey (`brush_look_<code>`) to its new code.
- **Upstream files touched:** none. Generic: none.
- **Checks:** `LabLayoutChecks` (one mode, no band, the picker box's squares, the Open sheet, the new budget table),
  `BrushLabChecks` (`openSave`, `saveAsNew`, `suggests`, `update`, `newBrush`), `LabSlotsChecks`, `ModularLabChecks`
  (every back-room choice offered), `HandHatchChecks`, `PartSampleChecks`, `BlendChecks`, `ScreenChecks`; the device's
  `ScreenShots` shoots the one mode and the Open sheet.
## Smooth body rims (2026-10-29, branch `springy-edge`): no upstream files touched

The owner, testing a chisel wash on canvas: "i see a repeated pattern along the edge instead of a smooth line". The
body's dabs scalloped its rim into a row of beads: `BodyStamper.spacing` kept the scallops under a quarter of the soft
edge, and for a round dab only, where a chisel's or flat nib's oval bends much tighter at its ends. Scribulor's own
engine class.
- **`BodyStamper`:** `spacing(D, softK, aspect)` holds the scallops under `RIM_DIP` (a sixteenth) of the soft edge at
  the oval's sharpest bend (`r · aspect²`, the rule the Filbert alone had); `filbertSpacing` is that plus the crescent's
  bound. Ovals, and big crisp rounds, lay closer dabs (desktop bench: Gouache's body 13 to 24 ms a 240 px stroke, the
  pen's wait unchanged; untimed on the Manta). 12 of 126 presets' pictures change, at their rims: the dry brushes'
  sawtooth edges smooth out; Print's screens keep their dots.
- **Not done:** a dab still lands only where a sample step falls, up to a sample gap past its spacing; stepping a
  quarter spacing removed the last faint repeats but cost more (Gouache's body 13 to 28 ms) and left an Instant stroke's
  end up to a spacing short of the pen (an Instant stroke's lift never lays the last point; `BlendChecks`' paint-behind
  golden caught it).
- **Upstream files touched:** none. Generic: none.
- **Checks:** `EdgeRippleChecks` (two chisel washes and a crisp opaque round, 40 and 98 px: no single repeat along the
  rim stronger than 8 of 255; the chisels 14 and 22 before).

## Smooth line edges (2026-10-29, branch `line-edge`): no upstream files touched

The owner, of the Springy brush's faint scallops found while fixing the body's beads: "yes fix that too". The same cause
as the body's, in the line's own grid. Scribulor's own engine class.
- **`LineStamper`:** the big nib's distance grid is `BodyStamper.spacing` of the nib (capped at the old 0.08 of its
  size): its sharpest bend, under `RIM_DIP` of its edge, a crisp nib's pixel or a stick's fuzz (about 1.5 spreads of its
  half width), so sticks keep their old grid and dust. Its old bound let the scallops dip half a pixel. A crisp nib now
  steps a grid at most, so every grid line gets its dab; it stepped a tenth of the nib, past the grid, so a quick stroke
  (samples 12 px apart) laid a dab a step. A quarter grid (each dab nearer its line) was smoother still but left five
  fringe pixels a tone off in `BlendChecks`' Invert twice and four masked pixels unerased in `ResistChecks`. The Springy
  brush at 98 and 150 px: the strongest repeat along the edge 7 to 17 of 255 before, 1 to 2.5 after.
- **Web lab:** `prototypes/modular-lab-src/engine.js`'s `stampLine` has the same grid and step (Brush Studio and the
  match lab read it), so the app still draws the lab's pages; `node scripts/resist_goldens.cjs` drew three resist
  goldens again (`fluid-ink`, `fluid-light`, `wax-ink`); the blend goldens came out the same.
- **Cost** (desktop bench, a 1.5 s stroke, line ms): Fineliner 5 to 10 at 240 px, Brush pen 5 to 9, Marker 5 to 9, Dip
  pen (a flat nib, tight ends) 12 to 22, Pencil 10 to 23; Charcoal and Pastels as before; the pen's p95 wait unchanged.
  Untimed on the Manta: ink lines draw live at any size (`QuickStroke.ANY`), graphite past 260 px goes quick.
- **Pictures:** 113 of 126 presets' pictures are byte for byte (most draw at 16 px or under, a dab every step); a few
  sticks, crayons and tones move a speck or two.
- **Upstream files touched:** none. Generic: none.
- **Checks:** `EdgeRippleChecks` also draws the Springy brush at 98 and 150 px, samples 3 and 12 px apart (before: up to
  17 of 255). `WetPaintChecks` and `LegacyConversionChecks` re-pin the settled pages of lines past 16 px (every page
  mid-stroke unchanged).

## Crayon's lift (2026-10-29, branch `crayon-lift`)

The owner: "this crayon i've been using has a long delay between drawing and the line showing up on screen". The
Manta's stroke timing showed a Wax crayon keeping up under the pen like Ballpoint but its lift costing about 170 ms
(finish 105, final render 50, flush 15; Ballpoint's 5 / 0 / 0.2). The page shown never changed at the lift: it composed
the whole stroke again onto the masking fluid (`layWax`, its wax that washes slide off) and the fluid's write marked the
stroke's whole box to render again, though the fluid shows only as its hatching.
- **`ModularStroke`:** a live crayon (not a render, Drying or a fill) composes its wax beside each compose into a
  `StrokeTones` over the masking fluid as it is (`waxTones`), so `layWax` only writes it. Byte for byte as before
  (`CrayonChecks.lift`: against a quick stroke, whose render still composes at the lift, on paper and over fluid).
- **Cost** (desktop, a 1.5 s stroke, 12 and 24 px): the lift 2.5–3.2 ms to 1.0–1.4 (what is left is the write and
  Undo's copy); the render after it 1.2 M px to none; a sample 0.023 / 0.039 ms to 0.028 / 0.052. Untimed on the Manta.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +18 −2 | `maskTones`: the masking fluid's tile as it is (paper for none), read while a gesture on a layer is in progress. The block write on the masking fluid grows the dirty area only where its hatching (`liftable` ≥ 32) comes or goes, all the fluid shows. Generic: any tool writing the fluid. |
| `StrokeTones` | +13 −6 | `onMask`: tones composed over the masking fluid rather than the gesture's layer. Scribulor's own class. |

## A new application id and the handover (2026-10-30, branch `public-prep`): upstream files touched

For the public release the id became `ing.ell.mybaddrawings` (`app.helpyy.inkwash` named the owner's private project;
`ing.ell` is the owner's domain). Another id is another app, which can't read the old one's private files, so the
old id gets one last build (`-PlegacyId`, signed with the old key from `keystore.properties`' `legacy.*` entries)
that leaves its preferences and private drawings folder in `Document/My Bad Drawings/.handover` (a dot name, so the
shelf never lists it), and the new app takes them once (`HandOver`, pure; `DrawingStorage.leaveHandOver`,
`takeHandOver`, `handOverWaiting`). Release signing now reads a local, gitignored `keystore.properties`, else
Android's debug key, so anyone can build.
- **Upstream files touched:** `app/build.gradle` (the id, `-PlegacyId`, signing). `PaintActivity` (+12: takes the
  handover before the preferences are read; `loadDrawing` starts the app again if file access was just allowed and a
  handover waits, so nothing this launch holds is saved over it; one note once the page is up; `onResume` and
  `onPause` leave it, in the old id's build only). `DrawingStorage` (+45: `allowed` split from `sharedAllowed`, the
  handover's three calls). Generic: none.
- **Checks:** `HandOverChecks` (typed round trip, content:// left out, drawings copied without overwriting the new
  app's, taken once, the old app stops writing once taken).

## Shapes and Play back in the column; the Self-Portrait Prompt, a notebook setting (2026-10-30, branch `inkwash-play-row`): upstream files touched

The owner: "move shapes button to below visibility and timer, and move fun button beside it"; "('daily prompt', maybe
rename it as 'self portrait prompt'? ...) actually in that case put it in notebook settings as an optional thing, new for
every new page". `InkWashBar` gets `shapes` and `play` back (as before 2026-10-28: `tapShapes`, `modesChanged`, Shapes'
side bar in `lassoChanged`) in a small row under Show Kinds and the sand timer (`DrawLayout.PLAY_ROWS` 3); `MirrorsMenu`
and `WordsMode` call `modesChanged` again. The Self-Portrait Prompt is `NotebookOptions.selfPortrait` (off by default;
`SELF` in `BookOptions` and `TSRQ` in the recovery, each written only when on), built as Nib Preview is
(`LibraryCard.Part.SELF_PORTRAIT`, `NotebookScreens` chips). `DailyPrompt` keeps its name and file; its `TITLE` and
heading say Self-Portrait Prompt, `forDay` went.
- **Upstream files touched:** `PaintActivity` (+31 −32: the main menu's Play row and Play's Shapes and Daily Prompt rows
  out; `addPage(BookEdit)` deals a new page a card while the notebook's option is on, `addPage` and `newPage` through
  it; `dailyPrompt` without `today`). `RecoveryCodec` (+6 −3: `TSRQ`). Generic: none.
- **Checks:** `DrawLayoutChecks` (the row's order and cells, Play's and Shapes' marks, the menus' rows, the column's
  sums on the four screens), `ToolSwatchesChecks` and `GreyBarChecks` (turned screens under 5 % a dp and 2.5 px a grey:
  the box grows only into spare room), `NotebookOptionsChecks` (its record alone, file and recovery, absent reads off),
  `LibraryCardChecks` (twelve rows, set in its row), `DailyPromptChecks` (headings, where cards are dealt);
  `NomadUiChecks` (device) for the row's place.

## Fill in fills loops, Filled shape merged into it (2026-10-31, branch `fill-merge`): no upstream files touched

The owner: "if you draw a closed shape with that one it should fill in"; asked whether to merge Fill in and Filled
shape, "i think merge!". `FilledShape.of` now also wraps a Stay in Lines brush at Flood 1 (`fillsLoops`: "Fills",
`StayIn.reach` endless; Colouring pen's partway floods and Flood 0 never fill a loop); at the lift `close` asks the
outline `LiftingStroke.inShape` (new default false; `ModularStroke` waits for its shape found off the pen's thread and
says whether it found one, `MirrorStroke` its primary's) and, in a shape, leaves the stroke as drawn, its flood the
fill. On open paper it closes and fills as Filled Shape does. `fills` (Filled Shape alone) is unchanged, so the Smudge
card's blob and `PartChoice.SHAPE` codes are as they were. The Basic tool Filled shape moves to `ToolPresets.RETIRED`
(its code known, a slot or library holding it keeps it) and leaves `DrawLayout.PICKER`; `tip_filled_shape` went with it.
- **Upstream files touched:** none.
- **Checks:** `FilledShapeChecks.fillIn` (a loop on open paper fills in one Undo step; inside an inked box byte for byte
  the stroke alone; a short line the stroke alone), `ToolPresetsChecks` (127 presets, 28 retired, Marker's engine two),
  `DrawLayoutChecks` (39 picker tools), `FillChecks` (`STRIPS` without Filled shape's), match lab `fit.js` categories.

#### Live pages, step 4: Undo of your own changes (2026-10-31, branch `live-undo`, probe only)

- **Upstream files touched:** `ToneDocument` (+45): `stepsAfter(serial)` (the Undo steps made since) and `takeBack(steps,
  changed)`: steps taken back as one new Undo step from their own stored tiles, each pixel only where its layer still
  holds what the step left there. `PictureImport.lay(..., lighter)` sets greys lighter too (an Undo's change from
  elsewhere). `PaintActivity.undo/redo` and `DrawingPad` (pen lift time) call `LiveProbe` only while it listens.
- **Checks:** `LiveUndoChecks`, `LiveChangeChecks.set`.

#### Live pages: measured by an autotest (2026-10-31, branch `live-auto`, probe only)

- **Upstream files touched:** `ToneDocument` (+40): the dirty box's writers all go through `grow`, which also marks
  16 px blocks once `takeLive` has been asked (Live pages' changes: exactly where the page was written); the magnets'
  areas too. `DrawingPad`: `isPen` takes a stylus event with no tool type (`adb shell input stylus`, Android 11), the
  render hook for `LiveProbe` gone. `Magnets.stamp` with an origin.
- **Checks:** `LiveChangeChecks.live`, `LiveRevealChecks` (200 to 600 ms).

#### Live pages: the guest's drawing screen, the browser app's M1 (2026-11-01, branch `live-m1`, live only)

- **Upstream files touched:** `DrawingPad` (2 lines): the hover ring's timing and dot size read from `HoverNib`
  (`SHOWN_MS`, `AWAY_MS`, `TICK_SIDE`), which the browser shares. No engine change.
- **Moved into pure classes, for the browser too:** `StrokeStart` (a tool's stroke as `InkWash` started it: drawn,
  mirrored, Filled Shape), `DrawLayout.tops`/`partHeight`/`gapHeight`/`gapIndex` (from `InkWashBar`), `lineWords` and
  `toolName` (from `InkWashBar`), `ToolSwatches.ownPx`, `FingerTool.Choice.of`. New `LiveTools` (the host's tools for
  a guest), sent by `LiveProbe.shown` before the page and skipped as a change. `web/engine`: `WebEngine` exports the
  layout, tools, tray strokes, nib, picker box, gestures and view; `engine.js` `page.pen` and `E.T`.
- **Checks:** `LiveToolsChecks`, `DrawLayoutChecks.tops`, `HoverNibChecks`; `scripts/live-probe/live-check.cjs`.

#### Live pages: long presses, the Tools page and Brush Lab for a guest, the browser app's M2 (2026-11-01, branch `live-m2`, live only)

- **Upstream files touched:** none. No engine change.
- **Moved into pure classes, for the browser too:** `DrawLayout.Picker` (the tool picker's rows, from
  `PaintActivity.toolPicker`), `DrawLayout.LONG_PRESS_MS`/`LONG_PRESS_SLOP` (Android 11's, checked on the tablet by
  `ToolPickerChecks`), `DrawLayout.pickerColumns(width, height)`; `ToolSlots.pick` and `take` (from
  `PaintActivity.pickBrush` and `slotsReturned`); `LabLayout.moduleWords` (from `PartCard`), `openDials`, `rowKind`,
  `rolls` (from `LabSettings`), `scrolled` and `fits` (the browser's); `BrushLab.Swap.caption` (from `SwapStrip`),
  `BrushLab.cellText` (from `HandGrid`), `BrushLab.saveWords` (from `BrushLabActivity`). `web/engine`: `WebEngine`
  keeps the guest's own store (`keep`, `kept`) and exports the picker, the Tools page (`ToolsPage`) and Brush Lab
  (`BrushLab`) as JSON with their taps; `build.sh` packs the kits, personalities and names too; `engine.js`
  `page.scratch`, `page.pen`'s eraser kinds.
- **Checks:** `DrawLayoutChecks.pickerRows`, `ToolSlotsChecks.pickAndTake`, `LabLayoutChecks.browser`;
  `scripts/live-probe/m2-check.cjs`.

#### Live pages: the rest of the drawing screen for a guest, the browser app's M4 (2026-11-01, branch `live-m4`, live only)

- **Upstream files touched:** `DrawingPad` (Duplicate's offset and Delete's erase through `LassoPieces.duplicated` and
  `erase`; `ZEN_TAP_MS` from `MultiFingerTap`), `PaintActivity` (the menu, Play and Shapes read their rows from
  `DrawLayout`; a `menu` taking `DrawLayout.Row`s; the icons by name, as the drawings are). Behaviour unchanged. No
  engine change. `ShapeStroke` (upstream, untouched, unused on the Manta) joins the desktop sources with
  `ShapeGeometry` and `DirtyRegions`: it draws a guest's shapes.
- **Moved into pure classes, for the browser too:** `DrawLayout.Row`, `fileRows`, `playRows`, `shapeRows`,
  `shapePicked`, `shapeIcon`, `TEXT`, `REPLAY_PAGE`; `DrawLayout.MirrorRow.rows`, `icon`, `change`, `stopsPlacing`,
  `kept` (from `MirrorsMenu`); `TextBoxes.menu`, `restyled`, `dragged` and the box menu's words (from `TextMode`);
  new `LassoPieces` (the floating piece, its grab, Cut, Duplicate, Delete, Paste; from `DrawingPad`);
  `ToolSlots.toolKey`, `holder`, `dirty`, `clean` (from `InkWash`); `MultiFingerTap.ZEN_TAP_MS`; `Mirrors.TOO_SHORT`.
  `LiveProbe.toolsChanged`: the tools sent again mid-session when they differ (from `InkWashBar.selectTool`,
  `brushChanged`, `swatchChanged`, `MirrorsMenu.changed`). `web/engine`: `WebEngine`'s M4 exports (the menus, Mirrors,
  Shapes, the smudge chooser, the Dropper, the Lasso, Text and Make Ink, Hold to Snap, Zen Mode's cell); `pen` takes
  the finger and starts a stroke as `InkWash.stroke` does (held paint, Grey Shift); `engine.js` `page.changed()`.
- **Checks:** `DrawMenusChecks`; `scripts/live-probe/m4-check.cjs`.
#### Notebooks in the browser, the browser app's M3 (2026-11-01, branch `live-m3`, live only)

**Cut 2026-11-05 (release pare-down):** `/app` (notebooks.js, pages.js, their checks, the relay's and
LiveHost's `/app`) is gone; `WebBooks` keeps only what `books.js` (the Manta's shelf on a live page) reads: `shelf`,
`sizeWords`, `menuWords`. The rest of this section and the browser-app sections after it are history (tag `pre-pare-down`).

- **Upstream files touched:** `DrawingFiles.validName` tells a name's signs with `nameSign` (letters, numbers, space,
  - and _) in place of the regex `[\p{L}\p{N} _-]+`, the same set (`NotebookStripChecks` compares them over every
  code point to U+1FFFF): TeaVM's regexes have no Unicode classes. `signsAsSpaces` beside it, used by
  `NotebookMigration.safeName`. No engine change.
- **Moved into pure classes, for the browser too:** `NotebookStrip` (new; the strip's slots, the turns, the card's
  place, Delete Page on a stack and a shelf tile's "24 pages · today", from `NotebookScreens` and
  `PaintActivity.deleteStack`); `CardSheet.offered`, `chosen(screen, book, …)`, `picture` (from `CardSelector.open`);
  `PageExport.bytes` (a page's copy as bytes, `write` now writes them), `PageExport.FAILED`; `NotebookExport.Plan` (what
  `update` and the new `pdf` copy: sheets, templates, keys) and `NotebookExport.pdf` (the PDF `update` writes into a
  folder with no copies yet, straight to a stream: the browser's Export Now); `NotebookPdf.title` (from
  `AutoExport.title`); `DrawLayout.NEW_PAGE` (from `PageTurner`); `DrawingNames.unused` opened.
- **`web/engine`:** `WebBooks` (the notebooks' JSON and taps, exported as an object through `WebEngine.books()`: TeaVM
  exports static functions from the main class only), `WebEngine.adopt`/`settle` (a notebook's page drawn on as a
  page), `WebEngine.J` package-wide; `engine.js` `E.wrap`. `scripts/live-probe/notebooks.js` draws them; `live.html` at
  `/app` runs on its own.
- **Checks:** `NotebookStripChecks`; `scripts/live-probe/m3-check.cjs` (with `M3Fixture.java`: the desktop's notebook,
  and its copies compared byte for byte).

#### A notebook's settings and its page's tools in the browser (2026-11-01, branch `live-time`, live only)

- **Upstream files touched:** `PaintActivity` (`allowStroke` through `NotebookRules.refusal`, `timedLook`'s countdown
  through `NotebookRules.shownCountdown`, `chooseTools` through `ToolSlots.withTools`). Behaviour unchanged. No engine
  change.
- **Moved into pure classes, for the browser too:** new `ReplayRun` (a replay's copy, veil, player and strokes, from
  `PageReplay` and `InkWash.replay`); `ToolSlots.withTools`; `StripGesture.plusSlot`/`round`, `NotebookStrip.ghosting`/
  `hidden` (from `PageTurner`'s Strip); `NotebookLayout.swipe` (from `NotebookScreens.PageView`).
- **`web/engine`:** `WebBooks` sets every settings row, the sheets' ticks and sliders, a page's rules, and gives the
  drawing screen its rules, clock (`TimedPages`), cards (`DailyPrompt`), ghosts (`GhostPlane`), previews and the
  notebook's own toolbar; `WebEngine` records the pen's strokes (`StrokeLog.Recorder`), shows a page's planes
  (`ToneDocument.shownTone`) and plays `ReplayRun`. `scripts/live-probe/pages.js` (its own module) draws them; a few
  lines in `live.html`, `notebooks.js` and `m4.js`; `relay.py` serves `pages.js`.
- **Checks:** `NotebookRulesChecks`, `StripGestureChecks`, `StrokeLogChecks` (`replayRun`); `scripts/live-probe/pages-check.cjs`.

#### The Manta's notebooks from a browser: download, and editing the Manta's copy (2026-11-01, branch `live-books`, live only)

- **Upstream files touched:** `PaintActivity`: `openNotebook(path, card)` goes through a new `openNotebook(path, then)`
  (the same steps; the notebook is named before `replaceBook` now, so the page shown goes to live guests with its
  path); `guestAsked` (a guest's open or turn, `LivePage`); `autosaveNow` package-wide (`LiveShare` saves before a
  browser reads). `DocumentStore.liveLibrary` (reads on its IO thread, after queued saves). No engine change.
- **Moved into pure classes, shared with the browser app:** `NotebookCovers.of`/`picture` (a cover as the shelf draws
  it, from `WebBooks`); `NotebookExport.pdf(out, book, name, tiles, today)` and `PageExport.bytes(book, name, place,
  choice, tiles, today)` (a whole notebook's Export Now and Export Page; `WebBooks.pdf`/`pageCopy` call them).
- **New, pure:** `LiveBooks` (`GET /books…` behind `LiveHost`'s key: shelf, cover, file, PDF, page copy), `LivePage`
  (the room's notebook page: shown, open, turn). `LiveHost` takes an optional `LiveBooks` and names downloads
  (`Content-Disposition`). `LiveProbe` sends `LivePage` before each page shown and lays guests' asks in the relay's
  order with their changes. Web: `scripts/live-probe/books.js` (its own module), `live.js` `page`/`pageAsk`, a few
  lines in `live.html` and `m4.js` (Menu's Notebook Explorer and Export Page for a guest), `relay.py` serves `books.js`.
- **Checks:** `LiveHostChecks` (key, downloads byte for byte against the writers, `LivePage`; `manta`: a stand-in
  Manta); `scripts/live-probe/books-check.cjs`.

#### Brush Lab's Open, picker box and Grey Shift's set; Show Kinds, the sand timer and the wet indicator in the browser (2026-11-01, branch `live-lab`)

- **Upstream files touched:** `ToneDocument` (+6: `compositeTones` takes Show Kinds' steps, each kind plane at its
  step's strength as `visibleLayers` gives the screen; the old form passes 0, unchanged). No engine change.
- **Moved into pure classes, shared with the browser:** `WetLook` (new: what the wet indicator shows and when it looks
  again, its words and drawings, the blow dryer's frames; `WetIndicator` uses it); `BrushLab.greys`/`Grey` (Grey Shift's
  set, from `LabSettings.greySet`; `GreySet` takes it, its sizes `LabLayout.GREY_*`); Open's sheet's words and sizes
  (`LabLayout.PASTE_CODE`… `SHEET_*`, `sheetLabel`, from `BrushLabActivity`); `DrawLayout.kindsFirst`/`kindsName`/
  `kindsStep`/`kindsSaid`/`KINDS_EYES` (Show Kinds' popup, from `InkWashBar.kindsPopup`); `EraseKind.hiddenNote`/
  `takenUp` (from `PaintActivity.hiddenNote`/`sayKind`); `SandTimer.labels`/`chosen`/`waits`/`countdown` (from
  `InkWashBar.timerMenu`, `PaintActivity.timedLook`); `DrawLayout.boxSide` (a picker box's squares at any size, the
  column's `faceSide` through it); `DrawLayout.NOTE_AGAIN_MS` (from `PaintActivity.note`).
- **`web/engine`:** `WebEngine` `labSheet`/`labOpenCard`/`labLoad`/`labCardTray`/`labGrey`/`dip`, the lab's look and
  picker sizes in `lab()`, Grey Shift's row in the open card, `LAB` (the lab's brush as a tool), `kinds`/`shown`/
  `kindsMenu`/`kindsTap`/`hiddenNote`/`takenUp` (the eraser's kinds less the hidden ones in `pen`), `sandMenu`/`sandSet`/
  `sandLook`, `wetLook`/`wetWords`/`hurry`/`hurrying`/`hurryTick`, `columnWords`; `WebBooks.onCover`. Web:
  `scripts/live-probe/extras.js` (its own module); in `live.html` the picker box's drawing and input take a box (the
  column's or the lab's), a few hooks, and the swap panel's tap outside no longer throws; `relay.py` serves
  `extras.js` and three drawings.
- **Checks:** `LabColumnChecks`; `scripts/live-probe/extras-check.cjs`.

#### Files sent up to the Manta (2026-11-01, branch `live-upload`, live only)

- **Upstream files touched:** `DocumentStore`: `create`'s work in `created` (unchanged), used by `liveLibrary`'s new
  `add`. `PdfImport.run` takes a name and a picture too (a notebook of one page), and its pages' cache file is its
  own each run (`createTempFile`, so an upload and the Manta's Import can run at once). No engine change.
- **Pure:** `LiveBooks.add` (`POST /books/add?name=`: type by name and signature, `PictureImport.importable`; a
  notebook read whole by `BookCodec.read` before anything is written) and `Library.add`/`imported`;
  `NotebookNames.imported` (an imported file's name, moved from `WebBooks.importName`, which calls it). `LiveHost`
  answers 413 over `LiveBooks.MOST_ADDED`, 415 for a file that isn't one or doesn't read. `LiveShare` makes pictures
  and PDFs notebooks through `PdfImport` and reads the Manta's shelf again when it shows.
- **Web:** `books.js` (the shelf's Import, `sender` for `/app`), one row in `notebooks.js`'s Export Now, one argument
  in `live.html`. **Checks:** `LiveHostChecks.added`/`imports`; `books-check.cjs` (Import, Send to My Tablet).

#### The Manta's Clear Page, eraser and hidden layers reach guests (2026-11-02, branch `live-clear-sync`, live only)

The owner: "i cleared the page but the desktop version kept the art". Every change the Manta sent went as ink, which a
receiver lays only where it darkens, so nothing the Manta lightened (Clear Page, the eraser, a layer hidden or made
fainter, a magnet moved) reached a guest; and a set change (an Undo's, a guest's eraser) was set on the layer drawn on
only, so a mark on another layer (a wash under ink) stayed.
- **Upstream files touched:** none. No engine change.
- **Pure:** `LiveChange.since` (only the pixels that differ from what was last sent, a byte a page pixel; set when one
  is lighter, as the browser's `Live.changed` decides) and `into` (a change laid by everyone, taken in).
  `PictureImport.lay`'s `lighter` makes the page show the greys: every shown layer's base and planes erased there, then
  the grey on the layer drawn on, white leaving the paper; `WebEngine.lay(set)` and `picture` (the browser's own Undo)
  call it instead of their own copies. `LiveProbe` keeps the room (`room`), sends every change through `since` and
  takes in each it lays; the steps a set change makes are all counted as laid.
- **Checks:** `LiveChangeChecks.since`, `LiveUndoChecks.lighter`; `books-check.cjs` (the stand-in Manta's `draw`,
  `erase`, `hide`, `show`, `clear` over a guest's wash and pen, the browser as the Manta after each).
- **Open:** Undo of Clear Page while live does nothing (`ToneDocument.takeBack` passes stack steps by). Done 2026-11-03
  (`live-clear-undo`).

#### A guest reconnects after a drop (2026-11-02, branch `live-reconnect`, live only)

The owner: "when it drops it's unlikely that it is able to reconnect", "when the manta sleeps … it says it will be able
to reconnect but it doesn't". Found (live-check's new drops, failing before): a connection gone silent (a Wi-Fi or a
Manta gone without closing it) left a guest Connected forever, as the browser never notices one and the relay's
heartbeat was a comment, which EventSource never shows; a guest back on a relay started again (the Manta awake) kept
marks the host erased meanwhile (the Manta's page whole only lays its marks); a guest's mark that didn't reach the
relay was dropped ("Couldn't send a mark"). Chrome itself keeps retrying refused and unreachable addresses every 3 s.
- **Upstream files touched:** `PaintActivity` (+1: `share.restore()` in `onCreate`). No engine change.
- **Pure:** `LivePresence.STILL`/`STILL_LINE` (the heartbeat, an event of its own with an empty data field, every
  `STILL_MS`; `LiveHost` and `relay.py` send it), `SILENT_MS` (a guest hearing nothing that long listens again),
  `cutNote` (Connection lost's note says it waits: "Waiting for the Manta, maybe asleep. Back within a minute or two of waking."; Host gone's as before),
  `guests`. `LiveHost` binds with `SO_REUSEADDR` (the same port at once after a stop). `WebEngine` `LIVE_STILL`,
  `LIVE_SILENT_MS`, `liveCut` through `cutNote`.
- **Android:** `LiveShare` starts and stops its server one at a time, in order (a quick away and back could bind before
  the last let go, and move the port), asks for the same port each start, and keeps sharing (key, port, when) in the
  app's preferences: the app stopped with it on (an install, a crash) and started within `KEPT_MS` (2 h) shares again
  with the same key; Stop Sharing and the drawing screen closing forget it. `LiveProbe` keeps the Manta from sleeping
  by itself while a guest is on the page and drawing (`FLAG_KEEP_SCREEN_ON`; its power button still sleeps it): `AWAKE_MS`
  (5 min) after a guest came or last drew it sleeps as ever, and guests wait for it to wake.
- **Web (`live.html`):** a watchdog listens again when the browser gave up or nothing came for `LIVE_SILENT_MS`, while
  the page is open. Connected again, the page starts over (blank, the relay's history laid again in its order, the
  guest's own too) and the guest's Undo list stays. Its own changes not taken by a relay wait and go again, in order,
  once the relay says who listens and the host is on it, and are laid as they come back (echoes, by their sealed bytes).
- **Checks:** `LiveHostChecks.again` and `words`; `live-check.cjs` (one connection while the relay is there, a relay
  gone silent, a relay gone and started again empty with the host's erasing and a guest's unsent stroke). The
  stand-in Manta (`LiveHostChecks.BooksManta`, books-check) names itself on the relay as `LiveProbe` does: a guest's
  marks now wait while no host is there (Host gone), as the real Manta would never hear them.
- **Untried on the Manta:** whether its firmware honours `FLAG_KEEP_SCREEN_ON` for its own sleep, and its Wi-Fi's
  return on waking (logcat `LiveShare`, `LiveProbe`).

#### Live Undo while paint is still wet (2026-11-02, branch `live-undo-oil`)

The owner: "i used the oil … it was set to the wrong size and so i tried to undo it, but it only undid the mark"; a
dark ridge stayed round the drawing's hair. What stays wet after the lift (wet oils and gouache, a Slow wash settling,
a Float bath, halos) adds to its stroke's Undo step through `finishContinuation`, which made the step anew: a new
object and serial. `LiveProbe` holds the step the lift made, so a live Undo pressed while the paint still moved took
back that stale step, only where the page still matched it: the paint moved since stayed (10k pixels replaying the owner's stroke on the saved page, 3 ticks in).
- **Upstream files touched:** `ToneDocument.finish` (+6): a continuation keeps the step it continues, its tiles
  replaced in place, its serial kept (as fading marks already patch a step's tiles). Undo and Redo unchanged.
- **Checks:** `LiveUndoChecks.wet` (oil at the owner's size, an ink smear and a Slow wash over ink, Water brush over
  Water pencil, each undone live and the page's own way before any tick, mid-wet and at rest: the page as before
  the stroke; failing before mid-wet for the oil, the Slow wash and the Water brush).

#### Live Redo of your own Undo; a stale probe file no longer makes the Manta live (2026-11-02, branch `live-redo`)

The owner: "i can't get three finger redo to work". Live, Redo did nothing (`PaintActivity.redo`); and a USB probe's
`live-probe.txt` (Oct 31, `http://127.0.0.1:8766`) left on the Manta kept `LiveProbe` retrying a dead address every
second, which counted as live: Undo own-only, Redo off, with Draw Together off.
- **Upstream files touched:** `PaintActivity.redo` (live: `LiveProbe.redo`, else the page's as before). No engine change:
  Redo is `ToneDocument.takeBack` of the live Undo's own steps, so it puts back each pixel only where the page still
  shows what the Undo left (a guest's mark laid since stays), one Undo step, as Undo's rule.
- **Pure:** new `LiveOwn` (this tablet's own changes and its live Undos, ten each; `made` clears Redo, others' changes
  don't), out of `LiveProbe`. `LiveProbe.redo` sends what the page shows where it changed as ink, set when one is lighter
  (`LiveChange.since`); an Undo's goes set as before.
- **Live means connected:** `LiveProbe.listening()` (own-only Undo and Redo) only while Draw Together is on or the
  relay's line is open, not while a `live-probe.txt` address is merely retried; its Wi-Fi lock only while connected.
  The file is still read (USB probes, `autotest.cjs`, run against the release build), so no debug-only gate.
- **Web (`live.html`):** a guest's Redo the same way (`takeBack`: the Undo's before and after, `Live.takeBack`), kept
  across a reconnect's start over; a new own change (`keep`) clears it. Shortcut: the browser's rule is still `live.js`'s
  greys-over-a-rectangle copy (as its Undo since step 4), not `LiveOwn` through TeaVM: its own changes outlive the
  engine page a reconnect starts again, which steps would not.
- **Checks:** `LiveUndoChecks.redo` (exact, around a guest's mark, kept by a guest's change, cleared by your own) and
  `wet` (live Redo as the Undo found the page); `live-check.cjs` (three fingers redo on both, two take it back again).

#### What the page does after the lift reaches guests as it goes (2026-11-02, branch `live-wet-sync`)

The owner: Rob boss (Oils, wet, Drag, keeps paint) on the Manta with a browser guest; the Manta's grey looked darker and
longer, with faint dotted patches the browser lacked. Found: the Manta sent what the page did on its own after the lift
(`DryingTicker`'s ticks: wet paint moving, a Slow wash settling, a bath, halos; Later's fading marks stepping) only when
it ended (`settled` → `PaintActivity.recovery` → `LiveProbe.changed`), so a guest saw a big oil as it was at the lift for
as long as it moved (15,496 px off 40 ticks in, books-check), and a Fading or Lemon juice mark only once it ended; Cracks
is one step at its end, so it looked right. The screenshots' other differences are the displays: the Manta shows greys as
`DotPattern` dots (a grey of 170 half black), the browser as 16 plain greys (`q16`), so the same tones look lighter
there, and the dotted patches are greys of about 246 to 253, white in 16 greys.
- **Upstream files touched:** `DrawingPad` (+1: the ticker's `show` tells `LiveProbe.ticked`). No engine change.
- **Pure:** `LiveChange.TICKS_MS` (400): what ticks change goes at most that often, the ticks since as one change
  (`LiveProbe.ticked`, never while a stroke is down: its lift sends it all), the rest at once when they end, as before.
  The browser's own wet paint already went so (`live.html`'s `wetTick`), now on the same number (`WebEngine` `LIVE_TICKS_MS`).
- **Checks:** `LiveChangeChecks.ticksReach` (the owner's oil at 360 px and a Fading mark: the guest's page the host's in
  16 greys at every send and at rest); `books-check.cjs` (the stand-in Manta's `oil`, held 40 ticks in and at rest, and
  the browser's own oil reaching the Manta; `LiveHostChecks.BooksManta.oil`, `go on`, `tones`).
- **Open:** the browser never steps a guest's own Later marks (Fading, Lemon juice, Cracks…): they stay as laid there
  and on the Manta.

#### The Manta's own stroke reaches guests while it is drawn (2026-11-05, branch `host-live`)

The owner: "strokes stream mid-stroke from the browser, so i can see them clearly as they're being made... but FROM the
manta, it's still just on pen lift." The browser sent its strokes in parts (`live.html`'s `sendPart`, every 80 ms); the
Manta only at the lift, drawn on along its path. Now `LiveProbe.drawing` (after each drawn batch) sends, every
`LiveChange.PART_MS` (120) and never while the part before is on its way, what the page shows where it was written since
(`ToneDocument.takeLive`'s blocks, as every change): a `LiveChange.part` (no path, laid at once; later parts join, one
Undo step), of the stroke's kind (`LiveChange.kinds`, from the stroke being recorded: `StrokeLog.Recorder.medium`), so a
guest lays every part on that kind's plane as it lays the lift's. Only that box is read on the main thread (0.16 ms median, 1.1 ms worst on the desktop); packed, sealed and
POSTed on `sending`. The lift's rest goes from `changed` as the stroke's last part, without a path; a stroke that wrote
nothing to the page before its lift goes as before, drawn on along its path. What the page does after the lift goes as before (`ticked`).
- **Upstream files touched:** `DrawingPad` (+4: `drawBatch` and the unbatched input call `LiveProbe.drawing`, after the
  timing that paces settling; `recording()`). No engine change.
- **Pure:** `LiveChange.PART_MS`, `part(later)` (its kind kept); `StrokeLog.medium` (a brush's kind of mark, for an
  entry and `Recorder.medium` alike; `LiveProbe.kindOf` went into it).
- **Checks:** `LiveChangeChecks.hostParts` (a pencil, a felt, a watercolour wash and an oil drawn in parts as the Manta
  sends them: the guest's page the host's after every part and after the lift, to the pixel, and laid on the kind's
  plane the host's with each kind hidden; prints the main thread's cost); `live-check.cjs` (the JVM's felt stroke's parts posted 120 ms apart: laid on the phone before the lift, its page
  and the iPad's the host's after; also `--host`).

#### A notebook that wouldn't open: a Lasso-moved picture's kinds (2026-11-02, branch `kindbase-zero`)

The owner's notebook stopped opening ("Invalid kind section"); `e8731cdc` reads and writes a base kind of 0 as all. The
cause, pages 14 and 15: a placed picture (paint of no kind) moved by the Lasso is laid back as one piece of every kind
(`LassoPlanes.place` → `ToneDocument.layKinds`, all pixels alike, so no plane), so the layer held three kinds, no
`Layer.kind` plane and no `kindBase` (set only when a plane is made); the file wrote that base, 0. Merge
(`InkWashLayers.merge`) leaves its layer so too (Pen and Paint, no plane), which reopened as all three kinds.
- **Upstream files touched:** `DocumentCodec` (+2: a layer of more than one kind without a plane writes its kinds as its
  base, every pixel holding them), `ToneDocument` (`Layer.kind`'s comment: null while every pixel holds the same kinds).
- **Checks:** `EraseKindChecks.planeless` (the Lasso's picture with a Pen smile: the notebook's layer, written with base
  7 and reopened as it was; a merged wash and pen line reopened Pen and Paint, failing before).

#### An effect brush's trail drawn cheaply (2026-11-02, branch `newsprint-speed`)

The owner: "the newsprint effect was very sluggish ... took seconds for even the initial grey stroke to show" (Newsprint
at 90 px: 20 s of drawing, first pixels after 27 s). The darker trail (`bc3a76a3`) read the page under its band a pixel
at a time (`ToneDocument.compositeTone`, the layer stack walked per pixel), and asked for a box round every disc (each
band pixel drawn about 16 times). Now `MirrorShow.draw` reads the page a run of 64 at a time where an overlay needs it
(`Mirrors.Tones.tones`, `MirrorShow.tones`: `compositeTones`), and the trail asks for one box a render, round the
pixels newly shaded (`MirrorShow.fresh`). The same pixels shown; desktop draw 0.07 to 0.009 ms a sample.
- **Upstream files touched:** `DrawingPad.drawShow` (+1 −1): passes `MirrorShow.tones(page)` for `page::compositeTone`.
- **Checks:** `AreaChecks.trailCost` (Newsprint at 90 px over marks: no pixel read alone, the same drawn as read a pixel
  at a time, boxes under 8 times the band, drawing within 1.5 times the stroke's own time; 16 times and 1.4 M pixels
  read alone before).

#### A stroke's start makes no surface (2026-11-02, branch `reprint-start`)

The owner: "it doesn't show the grey preview until a split second before the thing is converted". The Manta's log: a
later Newsprint stroke spent 3.4 s in its start (`create_ms`), the first 6.6 ms. Resolving a Reprint brush (its
`Carry.paper`) or a wash (`Wet.wash`) made its Paper's whole surface (`BrushTexture.grain`, about 80 ms on this Mac,
seconds on the Manta), and only the last two are kept: the drawing screen resolves the brush as named for its ring and
trail at every pen-down (Newsprint names the Library paper; the stroke itself is on the page's surface), and the slots'
warm-up, hover rings and readouts resolve other brushes in between, so the Library paper was made again each time. Now
`StrokePaper` makes its grain the first time a mark reads it: resolving a brush makes none, drawing reads the page's.
The same pixels. Desktop: that resolve 78 to 0.1 ms.
- **Upstream files touched:** none.
- **Checks:** `AreaChecks.startCost` (two Newsprint strokes, two other surfaces made before each: the start reads no
  surface scan; drawing reads none on plain, its own once on laid).

#### Paper beside a reprinted line stays white (2026-11-02, branch `newsprint-white`)

The owner: "newsprint effect.. it turns empty canvas slightly grey". Page 15 of the notebook he sent: a halo of halftone
dots up to 12 px each side of every line, in the saved page. Not new: as old as Reprint (`c27ab365`); the same before
the quieter grain (`9c558e48`), `newsprint-speed` and `reprint-start`. Paper that is white as found never printed; what
did was the faint grey a pencil leaves beside its line (its soft shoulder, Blur's tail, grain specks at tone 254: they
read as paper), which `Carry.inks` printed two to eight times as dark (the dot gain, the grain lifting a near-white
pixel by up to .2, Dot Swell), as stray black dots. Now a reprint prints a grey lighter than `Carry.LIGHTEST` (.06,
tone 240) as paper, as a print's finest highlights drop out. Every Reprint print shares it (the Photocopier's toner
never inked so light a grey; its pixels are the same). A blurred pencil line under Newsprint at 90 px, rows 7 to 14 px
from it: mean 249.2 with 88 of 3840 pixels under 250, now 255 and none; the line's own dots the same (352 of 1200). On
page 15's untouched soft lines: new dots 8 px or more from a mark's core 23, now 1. Pictures:
`docs/archive/pictures/explore/newsprint/` (`newsprint-before-after.png`, `owner-page-before-after.png`).
- **Upstream files touched:** none.
- **Checks:** `ReprintChecks.paperStaysWhite` (Newsprint and the Photocopier over a pen line and a pencil line
  softened by Blur: paper farther than 8 px stays white, white as found stays white, the line still prints, Newsprint's
  in dots; 74 px dotted before).

#### The browser shows greys as the Manta does (2026-11-02, branch `web-greys`)

The owner: "yes show greys like the manta" (after `live-wet-sync` found the browser showed tones as 16 plain greys,
`q16`, so a grey of 170 to 187, half black dots on the Manta, looked light, and 246 to 253, sparse dots there, white).
Now the browser shows each page tone as the Manta's dot density for it as a smooth grey: `DotPattern.exportGray`, the
PNG copy's curve, which the notebooks' pictures, covers and page-bar previews already used (`WebBooks.argb`,
`LiveBooks` `/books/cover`). Smooth, not the panel's dots: the page is drawn scaled (zoom, the window's pixels), where
an 8 × 8 dot pattern would shimmer and moiré, and the smooth grey is the dots' mean, so it reads as dark. Tone → shown:
170 → 108 (was 170), 180 → 124 (187), 200 → 151 (204), 230 → 209 (238), 250 → 246 (255), 253 → 251 (255).
Display only: `S`, what a change sends (`q16` still: `shownRect`, `area`), Undo, exports unchanged.
- **Web:** `WebEngine.shade(tone)` (`DotPattern.exportGray`); `live.html` `screenGrey` (a 256 table of it) where the
  page's tones are painted: `pull` (the page, so its zoom and the page bar's current preview), the scratch pad, the
  readout's dot; `m4.js` (the Lasso's piece, the Dropper's ring), `stamp.js` (Import's picture as it will lie),
  `extras.js` (Grey Shift's set). The tool trays, cards and the grey strip were already the panel's own dots
  (`ToneDocument.render`, `ShadeStrip.pixels`), as on the Manta. Pictures:
  `docs/archive/pictures/explore/web-greys/` (`before.png`, `after.png`: Rob boss, Watercolour washy, Ink wash).
- **Upstream files touched:** none.
- **Checks:** `live-check.cjs` (a host change of 180 and 250: the page keeps the tones, shows 124 and 246, the
  engine's curve); `stamp-check.cjs` (the page shows as it is again: against `WebEngine.shade`; Less Contrast's grey, a few
  white dots in 64, shown only a little lighter).

#### A guest's change that lightens no longer flattens the Manta's layers; a lost send goes again (2026-11-02, branch `live-guest-fixes`)

A review (2026-11-02, findings 1 and 2). **1 (data loss):** a change is set (`LiveChange.set`) when any pixel of it is
lighter (`live.js` `changed`, `LiveChange.since`), and `PictureImport.lay`'s `lighter` then erased every shown layer's
base and planes at every pixel the change carried, the darker ones too, and laid its greys flat on the layer drawn on:
a guest's smudge or wet oil tick (every 400 ms) flattened the owner's layers, kinds and tones under the whole of it,
saved, out of live Undo's reach. **2:** `LiveProbe.send` counts a change as sent (`room`) before its POST; a failed one
was only logged, so later changes left those pixels out and guests kept stale marks until a page turn or reconnect.
- **Upstream files touched:** none. No engine change.
- **Pure:** `PictureImport.lay(..., lighter)` erases only where a grey is lighter than the page shows there
  (`compositeTones`; 254, white as a change sends it, counts as white); a darker one goes on as ink
  (`PictureImport.inked`, out of `LiveProbe.onLayer` and `WebParity.lay`, which call it now), the other layers left as
  they are. One place, so the Manta laying a guest's change and the browser laying the Manta's (`WebEngine.lay(set)`)
  both follow it. The browser's own live Undo (`WebEngine.picture`) passes `exact`: a darker grey is erased and laid too,
  as a plane over the base would hide ink (`m4-check.cjs`' smudge Undo); a grey the page shows is left as it is (before,
  the Undo's whole box was flattened).
  `LiveChange.since`: a pixel not sent yet (255 in the room) goes set too, as what a guest shows there isn't known;
  `forget` puts a lost change's pixels back to not sent.
- **Android:** `LiveProbe.send` on a failed or non-200 POST forgets the change's pixels in the room (on the main thread)
  and keeps its box (`lost`); `changed` sends the lost boxes with the next change, asked within `TICKS_MS` (`ticked`),
  so a relay that stays away is tried no faster than the page's ticks.
- **Shortcut:** a guest's darker pixels over the owner's opaque paint lie under its plane, so the Manta shows them a
  little lighter there (near, as any guest's ink there; `books-check.cjs`: 2046 px over the owner's oil, 0 elsewhere).
  Upgrade with live pages proper (layers of their own).
- **Checks:** `LiveUndoChecks.lighter` (a set change's darker and same pixels keep another layer's wash, its lighter
  ones erase it, one Undo step; exact: the darker erased and laid); `LiveChangeChecks.since` (not sent yet goes set; a forgotten change sent again, set);
  `books-check.cjs` (the browser's own oil exact where the Manta's page was paper, near over its oil).

#### The relay keeps the page from its last whole; a refused guest send let go (2026-11-03, branch `live-bounds`, live only)

A review (2026-11-02, finding 6). **1:** `LiveHost` (and `relay.py`) kept every change since it started, and a guest
replays all of it at each listen (joining, and again after `LIVE_SILENT_MS` of silence): wet and fade ticks every
400 ms, a page whole at every page turn and probe connect, without limit on the Manta's heap and its Wi-Fi time.
**2:** a guest's send that meets a network failure waits and goes again (live-reconnect); one the relay refuses (a 4xx)
was already let go with "Couldn't send a mark" (`postNow`'s `!a.ok`), unchecked until now.
- **Upstream files touched:** none. No engine change.
- **Relay:** a send with `start=1` empties the history before it is kept (`LiveHost.request`, `relay.py` `do_POST`),
  behind the key as any send.
- **Android:** `LiveProbe.shown` sends its page message (`LivePage.SHOWN`) with `start=1` (`post(..., start)`), then its
  tools always (`sendTools(page, again)`; before, `toolsSent = null` let a tools send still queued make these look the
  same and skip, which would leave no tools after the start), then the page whole, set over every pixel, in order on
  `sending`. Everything before the start is drawn over by the page whole or said again (the page, the tools); a guest's
  ask (open, turn) is the Manta's alone. With no notebook (`app.book` null) there is no page message and the history
  is kept whole, as before. A start that fails to send leaves the history whole; a page whole that fails goes again
  (`lost`, live-guest-fixes), after the start.
- **Shortcut:** while the Manta stays on one page the history still grows (its strokes and ticks) until its next page
  whole (a turn, an open, the probe connecting again). Upgrade if a long session on one page shows it.
- **Checks:** `LiveHostChecks.checks` (history from the last start only), `test_relay.py`
  `test_history_from_the_last_start`; `live-check.cjs` (the stand-in host's tools with `start=1`, then its page whole: the
  relay keeps those 2, and a guest joining then shows the page as the others; the phone's sends answered by CDP Fetch:
  a refused one let go with the note and the next mark goes, one meeting a network failure goes again). The stand-in
  Manta (`LiveHostChecks.BooksManta`, books-check) marks its page message as `LiveProbe` does.

#### Live Undo of your Clear Page (2026-11-03, branch `live-clear-undo`, live only)

The owner: "yes undo of a clear page should work". Live, Undo is own-only (`LiveOwn`), and a Clear page is a structural
step (its layers swapped for blank ones), which `ToneDocument.takeBack` passed by: the clear was dropped from `LiveOwn`'s
list and nothing came back. A guest has no Clear page (`live.html`), so only the Manta's.
- **Upstream files touched:** `ToneDocument` (+75 −27): `Edit.cleared` (set by `clearPage` and `clearAllLayers`;
  `stackEdit` returns its step); `takeBack`'s tile loop is its own `takeBack(index, target, tiles, …)`, which the new
  `takeBackClear` calls with each old layer's base and kind planes as tiles over the blank ones (made into planes where
  missing, with a share, print and kinds where the old had them): each pixel only where the blank layer is still blank,
  so a mark laid since stays; the layer selected stays. Its steps are tile steps, so live Redo takes them back the same
  way (clears again around marks laid since) and the page's Undo travels them as one step. `copyTile(tones, alpha, key)`.
- **Shortcut:** the masking fluid, magnets, text boxes and a rubbed cover the clear took stay off; a layer drawn on with
  more than one kind since keeps its own kind base. Upgrade if live pages use them.
- **Unchanged:** `LiveProbe` (the clear was already an own change; its Undo goes set through `sendTakenBack`), the web.
- **Checks:** `LiveUndoChecks.clear` (Clear page and the page's Clear: wash with a permanent share, ink, a pen plane, a
  guest's marks laid after; live Undo puts back the rest exactly, theirs kept, the planes and the wash's share back;
  one page Undo step; live Redo clears again around theirs; failing before: nothing taken back).

#### Draw Together through ell.ing's relay, on any network (2026-11-03, branch `live-relay`, live only)

Many Wi-Fis keep devices apart, so a guest often couldn't reach the Manta's own relay (`LiveHost`). The owner approved a
relay on ell.ing: the Manta and its guests both connect out to https://ell.ing/mbd/live/.
- **Upstream files touched:** none. No engine change.
- **Relay:** `relay.py <port> public <dir>` (deployed as `mbd-live.service` behind nginx's `mbd-live.nginx`, both in
  superdood.org's server code (not in this repo), its README's deploy steps): rooms by the key's access hash (64 hex, else 403),
  only `relay.py pack`'s files served (`packed`; `/` is `live.html`), and limits: `MOST_ROOMS` 50, `MOST_ROOMS_EACH` 5
  a client, `MOST_LISTENERS` 16 a room, `MOST_KEPT` 8 MB of history a room (shortcut: past it changes pass on but aren't
  kept), `IDLE_S` 2 h for a room no one listens to, `MOST_TRIES` 20 rooms that weren't there a client and
  `MOST_TRIES_ALL` 1000 in all in 10 minutes (429), the client nginx's `X-Real-IP` hashed with a per-start salt.
  An empty room a guess made goes when its listener does. Nothing printed in public. Without `public` it is the Mac's
  relay as before: one room, no limits, the key ignored; it no longer reads `res/` when it isn't there (the server).
  `STILL_S` names its heartbeat; `X-Accel-Buffering: no` on listens.
- **Pure:** `LiveHost.RELAY` (the address, the one place in the app), `RELAY_KEY` (10 letters: the relay sees the access
  hash, `LiveCrypt.ROUNDS`'s weighing), `relayLink`, `reachable` (its page answers 200 within 4 s), `newKey(letters)`.
- **Android:** `LiveShare.startHost` picks the way at the first start, off the main thread: the relay when `reachable`,
  with a 10-letter key and its cipher (`relayed`), else `LiveHost` with a 4-letter key as before; kept till sharing
  stops, and through `restore` (`live_share_relay`). `LiveProbe.start` connects to `RELAY` with `relayed` when there is
  no host. The window through the relay: ell.ing/mbd's code, the QR code (the relay's link), "Online, while the app
  is open."; no address or key to type.
- **Server:** `server.py`'s `TO_RE` takes `https://ell.ing/mbd/live/#` and 10 to 40 letters as well.
- **Checks:** `test_relay.py` `PublicTest` (a room a key, pack's files only, each limit, nothing printed);
  `test_server.py` (the relay's links taken, a short key or another host refused); `LiveHostChecks` (`reachable`, the
  10-letter key, its link a version 3 QR code; `relayed`: the stand-in Manta through a relay at an address);
  `relay-check.cjs` (relay.py public behind a stand-in nginx at /mbd/live/, the stand-in Manta, a phone guest in
  headless Chrome: the page from the prefix, the Manta's page on joining, its mark to the guest and the guest's
  stroke to it, 9 of 9).
- **Open:** the Manta's notebooks (`/books`, Notebook Explorer, downloads, Import) are `LiveHost`'s only: through the relay
  Notebook Explorer says "Not connected". A Wi-Fi with a captive page that answers 200 for ell.ing would pick the relay
  and not connect. Untried on the Manta and on ell.ing.

#### Draw Together's relays see no names (2026-11-03, branch `live-names`, live only)

The owner approved: what a relay learns of who is in a drawing is an opaque id. Before, `from=` was `manta-` and the
Manta's `System.nanoTime` in hex, or `browser-` and 8 random letters.
- **Upstream files touched:** none. No engine change.
- **Pure:** `LivePresence.ID` (16 hex digits, the only `from=` a relay takes; `HOST`, the `manta-` start, gone);
  `LiveCrypt.hostId(random)`/`hostId()`/`isHost(id)`: the host's id is 8 random hex digits and 8 of SHA-256 of them
  keyed with a hash of the cipher's key ("MBD live host\n"); `LivePresence.guest(..., crypt)` counts a host by
  `isHost`. `LiveHost.name` and relay.py's `name` take only `ID`, else `-`.
- **Android:** `LiveProbe.me` is `crypt.hostId()`, made at each `start`.
- **Web:** `live.html` (and `probe.html`) `me` is 8 bytes of `crypto.getRandomValues` in hex; `WebEngine.liveSaid`
  takes the crypt's number (`engine.js`'s `c.n`).
- **Old pages:** an old guest tab's `browser-…` is `-` on a new relay: its own changes come back laid again, and it sees
  no host (Host gone) till reloaded; nothing throws on the relay or the Manta. An old Manta on a new relay counts itself
  a guest: the app and the server's relay.py go out together.
- **Checks:** `LiveCryptChecks` (a host's id by the JDK's SHA-256; another key's differs); `LiveHostChecks` (ids, an
  old page's name `-`; the host told only with the key, not another key's, not an old name); `test_relay.py` (a name,
  an old page's, upper case, one digit more or less: `-`); `relay-check.cjs` (every send and listen the stand-in
  nginx passes carries a 16-hex id and no name, two ids, only the key tells the host's, 11 of 11); `live-crypt.cjs`
  `hostId` (the check scripts' stand-in Mantas, which a guest must see as its host).

## The older smudges retired, Filled Shape hidden, one word a dial, chips by what a brush does (2026-10-31, branch `brush-tidy`): no upstream files touched

The design review's three tidy-ups (the owner approved them).
- **Retired:** the Blend stump, Stump, Water brush and Damp tissue move to `ToolPresets.RETIRED` (Blend and Water
  replaced them on the smudge tool, 2026-10-30). Water takes the Water brush's row in `DrawLayout.PICKER`, its drawing
  too; the Watercolour with Masking kit lifts with Water. New `ToolPresets.retired(Brush)` (the retired preset a brush
  is, by identity, as `find`): `ToolSlots.smudgeTwo` moves a smudge tool still holding one of the four, and
  `BrushVoice.personality` keeps a retired tool's own words (personalities.txt keeps their lines), so a slot, the
  smudge tool or the library holding one draws and reads as before.
- **Filled Shape hidden:** `ModularFeatures.offered` leaves out `PartChoice.SHAPE` as it does Hand Hatch, so no roll,
  die, swap, variation or Brush Lab chip picks it; `BrushLab.chips` still shows the current choice, so a brush holding
  it shows its chip. Its codes read and draw as before (`FilledShape.fills` unchanged); `app-features.json` (Studio's
  Guide) written again without it, and Brush Studio's own chips, Anything Goes and Mix (`studio.js`) leave it out
  but on a brush holding it. Shapes' **Filled** (MonoPaint's geometric shapes, `ToolSettings.filled`) is
  another tool and stays.
- **One word a dial:** `PartDial.REACH` and `PITCH` (a Rake's and Hatch's line spacing) say **Line Spacing**, a
  Stamp's `STAMP_SPACING` keeps **Spacing**; labels only, keys and codes as they were. The other shared labels are one
  concept each (Dry Brush, Paint Load; Hand Hatch's Wobble and Taper Out, never shown; Grey Shift's Any Greys).
- **Chips by what a brush does:** `ToolsPage.Chip.of(ModularBrush)`: Odd Ones for the tool picker's odd ones' choices
  (Spiro, Stamp, Stay in Lines, Filled Shape, Float, Mold, Fading, Lemon Juice), Print for Screentone or a Print
  Shading, else the style's kind (`Chip.of(ModularStyle)`, kept for stored chips); `has`, the grid's groups, the
  search's chip word and the chip a first visit opens on use it.
- **Upstream files touched:** none.
- **Checks:** `ToolsPageChecks.chips` (the picker's tools under what they do, a pen through a Print Shading under Print), `DrawLayoutChecks` (Stamp under Odd Ones), `FilledShapeChecks.codes` (not offered, no chip, a brush holding it shows its chip), `PartModelChecks.oneTerm`
  (a label two dials share is one concept, listed), `HandHatchChecks`, `ToolPresetsChecks.pinned` (every code ROWS and
  RETIRED held on 2026-10-31 is still one of them and draws its loop pixel for pixel as then), `ToolPresetsChecks` (123 presets, 32 retired), `ToolSlotsChecks` (the one-time
  move from the retired four), `DrawLayoutChecks` (Water in the picker, the retired Water brush keeps its drawing),
  `FillChecks.STRIPS` (the four's hashes out, every other as it was), `GreyBarChecks`, `VoiceChecks` (a retired tool's
  line), and the checks that draw the four find them among the retired (`FloatChecks.find`).

## Later, the seventh part (2026-10-31, branch `brush-later`): no upstream files touched

Roadmap step 7 (`docs/superpowers/specs/2026-10-31-brush-system-roadmap.md`, decided): the mischief out of Drying.
- **Model:** `BrushPart.LATER` (seventh, after the six every code holds: `BrushPart.SIX`); `PartChoice.LATER_OFF`,
  `LATER_FLOAT`, `LATER_MOLD`, `LATER_FADE`, `LATER_LEMON` (keys the retired ones': off, float, halo, fade, lemon),
  `goesWith` (Float only Quick, Mold only Slow, Fading and Lemon Juice Instant, Quick or Slow, nothing Wet), `drying`,
  `later`/`retired` (the old Drying choice and back). Float's, Mold's, Fading's and Lemon Juice's dials move to Later
  (`PartDial.part`), with `FLOAT_TIME` (Float's floating time, key `drying`); Mold's Drying Time is Slow's.
  Drying's `FLOAT`, `HALO`, `FADE`, `LEMON` stay, retired (`ModularFeatures.offered` false), so their codes read.
- **`ModularBrush`:** `split` (a retired Drying mischief as Drying + Later, as the engine laid it: Quick fresh, Mold on
  Slow with Flow While Wet 0), run by `normalize`, which also gives a Later that doesn't go with Drying its own drying;
  `retired`, `joined` (the old form when splitting it gives the brush back exactly, else null); `later`, `floats`,
  `mischief` (also on an unsplit brush the checks made).
- **Codes (`ModularCode`):** written via `joined` (old form, byte for byte, whenever it can); otherwise the 8th
  segment after the nib (`0` without one). Read: the eighth as Later, a ninth is newer. `docs/BRUSH-CODE.md`,
  `brush-code.json` (now with `later`), `app-features.json` (Studio's Guide, `later` after the six).
- **Engine:** `ResolvedBrush.laid` (Float and Mold back in Drying as they were always laid; Drying's own dials do
  nothing under them but Mold's Drying Time), the fade spec from Later, `asQuick` gone (the split Quick is it);
  `ModularStroke.finish`: a fading or lemon mark on a settling Slow wash settles at lift (`finishNow`) before the clock
  takes it; `QuickStroke`'s draw-once experiment leaves a Later's Quick as it is. The rest of the engine asks
  `floats()`/`mischief()` where it asked Drying.
- **Rolls and names:** the mischief rolls still lay the retired Drying choice, split by the roll's `normalize`, so
  every roll is what it was (neither Drying nor Later locked); Later's die rolls a mischief that goes with Drying;
  `ModularVariations` varies the `joined` form; `BrushNames` keys Later as `life.<key>`. Words say drying then Later
  ("Dries at once. Fades away in 20 s.").
- **Brush Lab:** `LabLayout(width, height, rows)` and `later()`: four rows of modules, Later alone in the last, only on a
  brush with a Later (the Nomad's slim modules one line then, for the open card's room); `LabSettings` picks it;
  `BrushLab.switched` (a Drying that Later doesn't go with drops Later; a Later brings its drying); Drying's chips end
  with Later's four, to add one; Later's chips are Off and what goes with Drying; `ic_part_later` (an hourglass).
- **Upstream files touched:** none.
- **Checks:** `LaterChecks` (the part, its combinations, every Basic and retired code with a mischief reading as Drying +
  Later and back byte for byte, its eighth-segment form the same brush and the same loop and stroke, combinations that
  fade, darken and settle, round trips, Brush Lab's adding and dropping, rolls as rare and all old-form), with
  `OlderLaterCheck` compiled against ae4caeb8 in `test_inkwash.sh` (an older reader: old forms in full, new ones less
  Later, "newer"); `code-spec-check.cjs` waits (a `shortcut:`) for the web lab's Later.
## The engine in the browser (2026-10-31, branch `web-wasm`): one engine file touched

Roadmap step 10 (`docs/superpowers/specs/2026-10-31-brush-system-roadmap.md`): Brush Studio and the live probe draw
with the app's own engine compiled to WebAssembly (`web/engine/`, from the TeaVM pilot, `docs/brush-lab/teavm-pilot/`),
the lab's JS copy kept for browsers without WebAssembly GC.
- **Engine:** `QuickStroke.renderAtLift` (static, off on the Manta): no render thread is started; `develop` renders
  the true stroke from every sample on its caller's thread at lift, the preview showing while the pen is down. Three
  lines; the Manta's path is unchanged.
- **Upstream files touched:** none.
- **Checks:** `QuickStrokeChecks.sameAsLive` (rendered at lift on the pen's thread, every layer the same as on its own
  thread, every style, Instant, Fast-wet and Drying, 60 and 200 px); `scripts/web_parity.sh` (wasm against the JVM
  through `engine.js`, the API Studio and the probe use); `WebParity.lay` uses what this branch has (it had the live
  branches' `compositeTones` and `PictureImport.lay` overload, so the harness didn't compile here).

## Later's media (2026-10-31, branch `later-media`): Salt, Plastic Wrap, Feathering, Foxing

Roadmap, "Added, 2026-10-31: Later's media". Later choices 5 to 8, appended (an older reader sees a Later past its list:
Off). Each changes a mark after the lift as the physical technique does, by Fading's and Lemon Juice's machinery.
- **Engine (pure):** `LaterMedia` (new): a technique's finished state over a region of one layer, from the brush's seed
  and the page position (Salt's starbursts, Plastic Wrap's creases (ridges of gradient noise stretched the way the film pulls), Feathering's halo and hairlines creeping along the valleys of the brush's own paper grain (`PaperGrain`, Paper's Texture and a drink by paper; plain paper gently, along stand-in fibres),
  Foxing's spots near the mark). `FadingInk`: a mark's `kind` (0 Fading, 1 Lemon Juice, 2 to 5 the media) in place of
  the lemon flag, `Spec` with its macro dial and seed, the media's timing (each pixel from as laid to finished, later by
  the mottle and the less it changes), `encode`/`decode` by section. `PartChoice` (`LATER_SALT` to `LATER_FOX`,
  `clocked`, `goesWith`, `drying`), `PartDial` (`GRAINS`, `CRINKLE`, `THREADS`, `SPOTS`, `SPREAD_IN`), `ResolvedBrush.fade`
  for any clocked Later, `ModularBrush.joined` (null for a Later with no retired Drying choice), `ModularWords`,
  `BrushVoice` (and `voice.js`, row for row).
- **`ToneDocument`:** `fadeSince` hands a media mark to `finished` (its tiles as laid and finished, as far past the
  stroke as the technique reaches; a tile past the stroke's own joins its last Undo step as it was, so Undo takes back
  what grew there); `stepFade` patches only the after side of the stroke's own steps and nothing older, and while that
  step is undone (`Edit.undone`, set in `travel`) changes nothing on the page, so an undone mark never comes back by
  the clock. `Edit.bytes` is no longer final. Fading and Lemon Juice step exactly as before. The work runs on
  `laterWork` (at once on the desktop; `DryingTicker` gives the app a worker thread): the lift copies the region and
  hands it off (about 0.3 ms on the desktop for a 300 px wash), the stroke shows as laid, `fade` takes the mark in
  once ready (`nextFade` looks again every `ADOPT_MS`), and a `Snapshot` waits for any still out so a save keeps it.
- **`DocumentCodec`:** a `LATR` section beside `FADE`, the same layout, only while the page has media marks; a reader
  before it skips it and opens the page as saved. `FADE` is written byte for byte as before.
- **Brush Lab:** nothing new: Drying's chips end with Later's eight; Later's card shows what goes with the drying.
- **Upstream files touched:** `ToneDocument`, `DocumentCodec` (as above).
- **Checks:** `LaterMediaChecks` (tables, codes, words, as laid then finished, determinism, reach, Undo at once, mid-way
  and at the end, reopened mid-way, a brush without them, the finished state's time); `OlderMediaCheck` and
  `OlderLaterCheck` compiled against c360ed8e in `test_inkwash.sh` (its codes read as Later Off, newer; a page with a
  Feathering mark opens as saved; a Fading and Lemon Juice page written byte for byte as that version wrote it).
  Pictures: `scripts/later_media_pictures.sh`.

## Media (2026-11-01, branch `explore-media`): White chalk, Dip nib, Water pencil; Dissolves

A test branch (the owner's overnight go-ahead). No upstream files touched; engine only, pure Java.
- **Dissolves** (`PartDial.SOLUBLE`, key `soluble`, Off/On): last of Ink's, Charcoal's and Graphite's dials, in
  `ModularCode.ADDED` (written only when on, so every older code is byte for byte), never rolled (`ModularStyle`) or
  varied (option dials never are). On, `ResolvedBrush.soluble`: the mark is `KIND_PAINT` and `ModularStroke.layerOf` puts
  it on the pair's wash half, where Clear Water's plane is, so the Water brush lifts it and lets it run as a dried wash.
  The eraser's Paint kind takes it, not Dry Media. `LabLayout.shows`: on the Nomad Dissolves is a spare row after
  Screen and Fling, shown where it fits.
- **Paint Load on a line** (`LineStamper`, `LoadedPaint.of(carry, size, nib)`, `StrokePoints.dry`): a line that runs
  out of its own paint fades as before and runs dry into the dry brush's streaks too (as a flick past the lift does);
  `dry` is Paint Load's share alone, so Gradient's fade (which shares `own`) never streaks. A line's load goes as much further as it is narrower than `REF_PX` (half to six times), a dip's worth of
  writing. Bodies (Oil paint, Loaded oils) are unchanged; no Basic tool before had Paint Load on a line.
- **Presets** (`ToolPresets`, built in `PresetPictures`): White chalk (Charcoal at Blend White), Dip nib (Ink, Drag's
  Paint Load), Water pencil (4B, Dissolves). Pictures: `scripts/media_pictures.sh` (`MediaPictures`) into
  `docs/archive/pictures/explore/media`, with what was dropped under `dropped/`.
## The nibs (2026-11-01, branch `explore-nibs`): no upstream files touched

An overnight exploration: four tips that are each another object in the hand, Tip choices 13 to 16, appended (an older
reader sees a tip past its list: Round, "Newer version"). Never rolled, never a variation's swap
(`ModularFeatures.seedOnly`), offered in Brush Lab's Tip chips.
- **Engine (pure):** `RulingPen` (a blade, its length from the pen's lean, lying along the azimuth, ink spent by the area
  it inks, dry on the tooth and the blade's nicks, blots where it lands and slows into a turn), `TwigStick` (a knob with a
  seeded outline turning in the fingers, a blob to start, its rim drying before its core, catches), `FanBrush` (hair tips
  in an arc across the lean, pressure bringing the outer ones down and splaying it), `Brayer` (a band square to its
  heading, its ink on a surface round its circumference, `ModularCompose.Under` read ahead as the stroke found the page,
  Pick Up Paint's share printed again a turn later). Each lays from its distance alone, state in `LineStamper.Meta`, so
  any batching lays the same marks; `ResolvedBrush` resolves them as patterns (`ResolvedBrush.nib`, new `LineTip` and
  `BodyTip` values), `ModularStroke` dispatches line and body and their reach; `PartChoice` (`RULING`, `TWIG`, `FAN`,
  `ROLLER`), `PartDial.PICKUP` (Brayer's), `ModularSizes` (each nib's Medium in every style, its card's size),
  `ModularWords`, `BrushVoice` and `voice.js` row for row.
- **Presets:** Ruling pen, Twig pen (Dip pen), Fan brush (Dry brush), Brayer (Print), built in `PresetPictures`.
- **Not done:** the web lab has no nibs yet (`code-spec-check.cjs` waits for them, as it does for Later); QuickStroke
  previewed them as it does any pattern (their own footprints since `live-nib-fixes`, below); a ruling pen's or twig's ink doesn't carry to the next stroke (a fresh dip each).
- **Checks:** `NibChecks` (batching, live, each nib's pressure and tilt, running dry, blots, the brayer's reprint, codes,
  presets, words, cost a sample beside Round). Pictures: `scripts/nib_pictures.sh`, in `docs/archive/pictures/explore/nibs`.

### Where a nib lands, and previews as wide as the mark (2026-11-02, branch `live-nib-fixes`): no upstream files touched

The owner: the twig pen "leaves a blob often at the beginning of the mark.. also the preview mark is much bigger than
the mark that is left". Engine and Scribulor classes only (`HoverNib`, `FootprintSweep` are Scribulor's).
- **The blob.** `TwigStick` dropped a blot (`RulingPen.blot`: a drop of 1.1 to 1.7 times the knob's radius and two or
  three runs off it) at every touchdown; `RulingPen` blotted at touchdown with a chance of .6 to 1, and its slow-turn
  blot fired while a stroke's speed was still rising from 0 (`StrokePoints.v` starts at 0). Now a touchdown blob follows
  Paint Load past its middle (`RulingPen.flooded`, 0 at the presets' .5): the twig lands a dot of 1.1 times its knob as far
  out as it inks (`inks`: a light touch's drier end, its core), a round start, runs and size growing to the old blob at Paint Load 1 (`blot(..., runs)`); the ruling pen's touchdown
  blot likewise, and its slow-turn blots wait a blot's spacing from the touchdown (`State.lastBlot` starts at 0).
  Fan and Brayer had no start blob. `NibChecks.starts`: each preset at its Medium, landing firm at .3/.6/.9, upright and
  leaning 40°, eight strokes each: its first stretch at most 1.25 times the line's width after it (worst now 1.22) and
  1.35 times its ink; an overloaded ruling pen still blots where it lands.
- **The previews.** Both previews read a nib's size from `ModularStroke.lineReach` (the redraw bound: a twig's is
  2.4 × size + 4, its blob's reach) or a pattern's band (size + 1): the quick stroke's preview (`FootprintSweep.ellipseAt`,
  every nib is quick at its Medium as its reach passes `QuickStroke.PANEL`), the side column's readout (`atRest`) and the
  hover ring (`HoverNib.nib`). Each nib now gives its own footprint from the stamper's own geometry (`RulingPen.footprint`:
  the blade along its azimuth; `TwigStick.footprint`: the knob stretched along the lean, as far as its ink reaches,
  `inks`; `FanBrush.footprint`: the hairs down far enough to ink, across the lean; the Brayer's band is its size, kept),
  the stampers reading the same helpers (`RulingPen.hold`, the nibs' tilt read once, `blade`, `thick`, `knob`, `stretch`, `ink`, `half`, `depth`, `hair`;
  their marks byte for byte but for the starts above). `HoverNib` rings any tip it has no shape of its own for (Hatch,
  Stamp, Hand Hatch, Spiro, the nibs, the hake's line) with the quick preview's footprint at the natural hold
  (`FootprintSweep.atRest(r, isLine, p, NATURAL)`), placed lines as far across as `Placement.Geometry` lays them
  (`FootprintSweep.placedAcross`), not their reach. Unchanged: bristles ring where they land, Stipple as far as it
  scatters, the quick rule (`QuickStroke.quick`, still from reach).
- **Measured** (`PreviewWidthChecks`, every Tip at 24 px or a nib's Medium, a light and a usual touch, leaning 40°;
  preview / laid px): Ruling Pen quick 31/15 → 16/15, ring 46/28 → 28/28; Twig quick 25/10 → 6/10 (light), 26/25 → 20/25
  (usual), ring 62/12 → 9/12; Fan quick 61/15 → 18/15, 62/36 → 36/36, ring 83 → 26/22; Brayer ring 83/72 → 73/72; Stamp
  ring 37/24 → 25/24; Hatch ring 27/24 → 25/24; Hand Hatch ring 42 → 25; Split Nib ring 53/28 → 24/28; Rake ring 149/70
  → 68/70; the rest as they were. The check holds every quick preview and ring within 0.7 to 1.35 times the mark (2 px
  either way), bar placed lines' quick preview (line by line, `QuickStrokeChecks.placedPreview`), Stipple and Spiro, and
  bristles' ring. Pictures: `docs/archive/pictures/explore/nibs/nib-starts-before.png`, `nib-starts-after.png`.

## Scrape and Wears Down (2026-10-31, branch `explore-knife`, an overnight experiment)

The module gaps' top-10 items 4 and 6 (`docs/superpowers/specs/2026-10-31-module-gaps.md`). Not merged: a test branch.
- **Scrape (Smudge choice 7, appended; the Basic tool Palette knife, Watercolor):** a flat blade through the paint there,
  laying none of its own (`ModularBrush.ownPaint` 0). Not already covered: Lift and Clear Water lift alike wet or dry,
  Crayon's Scratch is a white line whatever lies under it; none reads how wet the paint is. `Carry.scrape`: the blade
  across the way it moves, its length from the pen's lean (upright, the knife's tip, a fine line; laid over, the flat),
  its depth from pressure, a tent along the way so the dabs add up to one even pass; each pixel by how wet it is: wet
  paint gives up about half, most of it piled in ridges just past the blade's ends (pigment over pigment as a glaze, so
  a ridge darkens without going black), damp paint nearly all, carried off (sgraffito's light line), a dry mark a 5%
  burnish. Wetness: `CarryPlane.wetFrom`/`wetnessTile` read it as each tile fills, from
  `WatercolorSession.wetness`: the wet paint's water by its freshness (`WetWatercolor.wetAt`: `DAMP` and under dry,
  water at rest damp, gouache and oils wet), and a Slow wash still wet a moment ago (`stillWet`) by how much of its
  drying was to come (`DryingWater.wetLeft`, `wetMs`); a knife stroke leaves that wash wet for the next
  (`keepWet`, spent by the stroke's own ms, not the time between strokes).
- **Wears Down (Round's and Chisel's last dial, `ADDED`):** `StrokePoints.wear`, `fx`/`fy`: the facet, a vector along
  the lean it was worn at, grown each sample toward the lean held now by `1 − exp(−px × press × wear / 250)`;
  `LineStamper.nib`: worn, the nib is an oval up to 3.2 times as long along the lean, its point's side where the point
  was (`Nib.ox`, `oy`), 15% lighter; turning the pen (the facet no longer along the lean) gives back the point. Line
  media only (a body's paint doesn't wear). **Within a stroke only:** `StrokeLog` replays a page from each stroke's
  code and samples alone ("the same samples, brush and grey make the same marks"), so wear carried from stroke to
  stroke would replay differently (a replay starts from whatever the session holds, and other brushes' strokes come
  between); live pages send pixels, so they need nothing. So a new stroke starts sharp.
- **Web lab:** `newmods.js` carries Scrape and Wears Down in its tables (codes, words) but draws neither.
- **Upstream files touched:** `WetWatercolor` (+15: `wetAt`, read only, marked Inkwash).
- **Checks:** `KnifeChecks` (codes, seed-only, older codes byte for byte; wet, damp and dry under the knife; a worn
  tip broader across its lean and sharp again once turned; both the same twice; a worn stroke the same after another).
  Pictures: `scripts/knife_pictures.sh`, kept in `docs/archive/pictures/explore/knife`.

## Steady Hand and Long Stick (2026-10-31, branch `explore-steady`): an experiment

The module gaps spec's top-10 item 2. Control's last two dials, appended (`smooth`, `stick`; 0 off, written only when
on, so every older code reads them as off and writes byte for byte).
- **Engine (pure):** `SteadyHand` (new): a stroke in front of the brush's stroke whose samples go through a mass-spring
  follower: Steady Hand critically damped, its lag time longest when the pen is slow and its lag on the glass held to
  14 px at full; Long Stick underdamped, its swing under a hand's tremor; at the lift the tip glides onto the pen's last
  sample. `PartDial` (`STEADY_HAND`, `LONG_STICK`, `follows`), `PartChoice.handDials`, `ModularCode.ADDED`,
  `BrushPart.keeps` (a Control swap keeps them), `ModularVariations` (off stays off). `QuickStroke.start` wraps every
  stroke the drawing screen, Brush Lab and a replay start; `InkWash`'s mirror copies and `WildSample`'s samples the same.
  Never a Spiro's (its ring is its first two samples).
- **Brush Lab:** the Control card's two rows after Tilt Shading (`BrushLab.handRows`).
- **Presets:** Long stick (Charcoal): Soft charcoal at Long Stick .8.
- **Upstream files touched:** none. `DrawingPad` already ticks `HeldPen` for a stroke that `buildsWhileHeld`, which
  `SteadyHand` says it does so the tip settles onto a resting pen.
- **Checks:** `SteadyHandChecks` (lands at the lift, determinism, off is the stroke itself, codes, mirrored, quick as
  live, speed, swing, the lag printed). Pictures: `scripts/steady_hand_pictures.sh` into
  `docs/archive/pictures/explore/steady/`.
## Later's print, crafts and ageing (2026-11-01, branch `explore-later2`): Letterpress, Torn Paper, Cross-Stitch, Cracks, Tarnish

An overnight exploration from the module gaps survey (`docs/superpowers/specs/2026-10-31-module-gaps.md`). Later choices
9 to 13, appended (keys `letterpress`, `torn`, `stitch`, `cracks`, `tarnish`), on Later's media's machinery unchanged:
worked out off the drawing thread at the lift, shown by the clock, Undo takes the whole stroke, seed-only.
- **Engine (pure):** `LaterMedia`: `press` (the ink evened to a solid, a squeezed darker rim by a chamfer distance from
  the edge, a mottled middle, starved where the brush's paper grain dips, as Feathering reads it), `torn` (a ragged
  edge wandering in and out of the mark's at three scales, an off-white fibrous band inside it, fibres, a contour and a
  shadow lit from the top left), `stitch` (a grid fixed to the page, 6 to 10 px a cell: a covered cell gets two shaded
  threads in its mark's grey; the rest of the mark goes), `cracks` (two warped Voronoi networks, the finer only where
  the paint is darker; cached cell points), `tarnish` (darkens a pale line toward `TARNISH_DARKEST`, unevenly, tone
  before coverage; written straight into the region). Shared: `inward` (chamfer distance), `widest`. `FadingInk`: kinds
  6 to 10 (`NEWEST`), their timing rows; `decode` keeps kinds up to `NEWEST` (9a57ae48 keeps up to `FOX`, so it reads
  past them). `PartChoice` (`LATER_PRESS` to `LATER_TARNISH`, `drying`: Slow for Cracks, Instant for Tarnish, Quick
  for the rest; `goesWith`: Instant, Quick or Slow), `PartDial` (`IMPRESSION`, `TEAR`, `STITCH` 6 to 10 px, `CRACKS`,
  `TARNISH`; their times reuse `SPREAD_IN` and `DRY_IN` with their own labels), `ModularWords`, `BrushVoice`.
- **Brush Lab:** Later's chips in `PartChoice.LATER_SHOWN`'s order (Off and the mischief, then paint media, print, age,
  craft), not the enum's; Later's swap strip pages at `LabLayout.LATER_MOST` (8): on Quick or Slow, Off, the mischief
  and the paint media on page one, print, age and craft on page two. Thirteen to a page didn't fit the Nomad's tiles.
  Drying's chips (four and Later's thirteen) page at 13 as before: two pages of nine.
- **Presets:** **Metalpoint** (Pencil): Graphite at its hardest, a fine Round, Cold Press at .4, Press Dark at .2, Later
  Tarnish (`PresetPictures` builds it).
- **Upstream files touched:** none past Later's media's.
- **Checks:** `LaterMediaChecks` runs every check over all nine media, and `crafts` (Letterpress pale where the paper
  dips, Cross-Stitch on an 8 px page grid, Cracks crack more of the darker paint, Tarnish darkens the Metalpoint and
  stops at `TARNISH_DARKEST`). `test_inkwash.sh` compiles `OlderLaterCheck` and `OlderMediaCheck` against 9a57ae48:
  the new codes read as Later Off, newer; a page with a Cross-Stitch mark mid-way opens as saved. Pictures:
  `scripts/later_media_pictures.sh docs/archive/pictures/explore/later2 later2`.
## Hold to Snap (2026-10-31, branch `explore-snap`, exploration): upstream files touched

A stroke held still 550 ms at its end, pen down, is drawn again by the same brush along the clean shape it was roughly
drawn as (`docs/superpowers/specs/2026-10-31-hold-to-snap-explore.md`). New: `HoldSnap` (engine side, pure Java: the
pen's rest, recognition, the rough samples carried along the clean path). The six-part engine is untouched: the pad
finishes the rough stroke, takes it back and starts the slot's stroke again with the clean samples.
- **Upstream files touched:** `DrawingPad` (+50: `holdSnap` fed in `sampleEvent`, `snapTick` beside `heldTick`,
  `snapTo`; `quietPanel` holds `flush` and `beginStrokePreview` back so the snap shows as one update), `WetWatercolor`
  (+30: `rewet(wake, forget)` and `markDry`; `rewet(wake)`, every existing caller's, is unchanged), `WetCanvas`
  (`forget`), `WatercolorSession` (`takeBack`).
- **Checks:** `HoldSnapChecks` (recognition on 1600 rough shapes and 1400 negatives, pauses, the samples carried,
  the redraw byte for byte against the clean stroke alone, one Undo, wet paint beside it). Pictures:
  `scripts/snap_pictures.sh`.
## Undo's tiles in a fixed order (2026-11-01, branch `fix-stroke-race`): upstream files touched

`LaterMediaChecks fading-bytes` failed about half the time on several cores (always in the full script): a page with
Fading and Lemon Juice marks wrote its `FADE` section in another order from run to run. The pixels were always the same.
The cause was `ToneDocument.capture`: while bands are open (`openBands`), each band's thread puts the tiles it first
writes into `before`, a `LinkedHashMap`, under its lock, so the order of the tiles in Undo's step, and in a fading mark
made from it (`fadeSince`), followed the threads' timing.
- **`ToneDocument`** (+15): `closeBands` puts the tiles captured while the bands were open back in tile-row order, each
  row's in its own band's order (a stable sort; every band is one tile row). That is the order one thread running the
  bands in turn gives, so Undo's steps and a fading mark's file section are the same bytes on any number of cores, and
  the same as the versions before wrote with one core. Under `before`'s lock, for `sharing` readers. Generic.
- **Checks:** `LaterMediaChecks.sameBytes` (the fading page drawn twelve times on several cores against once in turn,
  byte for byte), and in `test_inkwash.sh` its `fading-hash` in two JVMs. `OlderMediaCheck fading` draws in turn
  (`Cores.shared = false`), since c360ed8e still has the race.

## A smudge's plane seen whole by a quick render (2026-11-02, branch `carry-race`)

`TrailChecks` crashed about once in three full runs (an NPE in `CarryPlane.takeFresh` on a core helper). A quick
smudge's render composes while the pen is down (`QuickStroke.render`, a trailing smudge whenever it has caught up), and
its compose asks `CarryPlane.has` at every pixel of its rectangle, while the pen's thread fills tiles further on
(`reachCarry`). `fillTile` stored a tile's marks, darkness and coverage as plain array stores, so a reader on another
core could see the coverage (`has` true) before the marks or darkness (null): the JIT and ARM cores (the Mac's and the
Manta's) reorder them. Since quick strokes composed ahead of the lift with a smudge's plane (a3049ef5, 9beb9076,
2026-10-16), much likelier once a smudge composed whenever caught up (56aed9d8, 2026-10-23).
- **`CarryPlane`** (Scribulor's own): a tile is published as filled once all of it is written (`filled`, an
  `AtomicReferenceArray` of the tiles' coverage, as `PrintScreen` uses, which TeaVM and Android 8 have: a volatile store
  after the fill, a volatile read in `has`), so a reader that sees it filled sees
  all of it, or sees it not yet filled (a tile no dab of the render's has reached: the page as found). No lock on the
  compose's path. The pen's thread's handover (`incoming`'s lock) already orders the tiles a render's dabs reach.
- **Checks:** `CarrySmudgeChecks.fillRace` (three readers on the tile being filled, 400 planes; failed every run before).

## Reprint, and Smudge shown as Rework (2026-11-01, branch `explore-reprint`, an experiment)

The owner's "every look lives in a brush": a brush meets the page drawing, making ink, and brushing over marks already
there. Not merged: a test branch.
- **Reprint (Smudge choice 8, appended, no dials, seed-only; the Basic tools Photocopier and Newsprint, Print):** lays no
  paint of its own (`ModularBrush.ownPaint` 0). `ResolvedBrush` hands its Medium's Print Shading to the carry
  (`Carry.print`, with the Paper's `StrokePaper` and Texture) and keeps none for the stroke (`screen` null, so nothing of
  its own is printed and it isn't Paint to the eraser). `Carry.reprint`: each dab (a quarter radius apart) takes every
  pixel within its radius, soft at its edge, `reprintAt(pressure)` of the way (smoothstep .02 to .5 of the travel: a
  light hand part way, a usual one all the way) from its loose paint **as the stroke found it** (`CarryPlane.reprint`:
  `foundA`, `foundP`) to that paint printed: black where `PrintScreen.ink` inks its darkness, after a dot gain
  (`1 − (1 − d)^1.6`) and the paper's grain at its Texture (`StrokePaper.height`, moved out of `ModularCompose`), paper
  where it doesn't. Without a Print Shading: toner, black past .45 ± .06 grit. Each pixel keeps the most any dab took it
  (`reachedTile`), so back and forth reprints once and the same strokes print the same. Paper stays paper.
- **Every plane of the pair:** a gesture binds one layer's one plane, so `ModularStroke.alsoWorks` generalises the
  stump's other half (`alsoBlends`) into `others`: for Reprint every other shown plane of the selected pair's two
  layers, bases too (pen, dry media, wash, opaque), each a `CarryPlane` and `Carry` of its own, composed at lift in a
  gesture each linked to the stroke's (`blendOther`, `finishWithPrevious`): one Undo step. Only the stroke's own plane
  (its Medium's: Pen for an ink) shows under the pen; the others change at the lift, as the eraser's wash does.
- **Wet paint:** a Slow wash and a Float bath are laid before the stroke as for any other brush; wet paint (the wet
  canvas) takes the reprint as it takes any other stroke (`WetCanvas.catchUp`): it stays wet, rests as reprinted, and a
  later Wet stroke moves it, dots and all. Kept (permanent, shielded) paint is left as it is, as the smudges leave it.
- **Rework:** `BrushPart.CARRY`'s label is Rework (the owner's choice); keys, enum names and codes stay. The web lab
  and Studio sources still say Smudge (`parts.js`, `newmods.js`, the built pages); `app-features.json` is written from
  the app, so Studio's Guide says Rework.
- **Checks:** `ReprintChecks` (codes, the same twice, pen, pencil and wash changed only in its band, back and forth as
  once, pressure, Undo and Redo, toner, over wet paint, its cost); `OlderLaterCheck` compiled against 9e447836 in
  `test_inkwash.sh` (Reprint codes read as Smudge Off, newer). Pictures: `scripts/reprint_pictures.sh`.

## Light over dark across kinds (2026-11-01, branch `explore-chalk`): upstream files touched

The owner: "white chalk i tried to draw on black paint but it didnt". Kind planes stack by kind (dry media, washes, pen,
opaque; each kind's wash half under its ink half), so a light mark on its own plane lay under every darker mark of a
plane above it: White chalk showed over charcoal and pastel only, not over pen, marker, Fill in, gouache or washes.
- **The rule:** a gesture that lays over what is there (`ResolvedBrush.laysOver`: Blend's White and Invert, and
  `laysLight`, the light-over-dark media; not on water, a resist or Later's fading media) says so after it begins
  (`ModularStroke.begin` → `ToneDocument.layingOver`). At its finish, at every pixel it changed where a plane above its
  own in the pair holds paint, that paint sinks into the pair's top shown base (`sinkUnder`), composed in the page's
  order, with each pixel's kinds (`Layer.kind`, kind and permanent bits), Erasable share and print tone; the planes
  above let it go. The page shows as before with the new marks now over it: a time order where planes have none. Paint
  drawn later goes on its own plane as ever, so pen over the chalk shows. A plane of a hidden layer, at another opacity
  (Show Kinds, a Half layer) or holding a mark that fades by the clock stays put, and a mark on the wash half sinks only
  that half's planes (the pair's ink is never covered by its own wash: `BlendChecks`' white wash under a pen line). One Undo step (`finishWithPrevious`,
  a gesture a plane), exact.
- **Erasing:** the chalk stays on its plane, so the Dry Media eraser takes it and the black shows again; the black, now
  in the base, goes with its own kind's eraser by the base's bitmask rules (where two kinds sank into one pixel, the
  Erasable rule decides, as on pages from before planes), leaving the chalk.
- **Not changed:** the composite order, the file and the native renderer; pages drawn before open as they were.
  Transparent washes only darken (not `laysOver`). The chalk shows over pen once the pen lifts (the sink is at finish).
- **Upstream files:** `ToneDocument` (`layingOver`, `sinkUnder`, `put`, `fadesOn`; `finish` calls it). Scribulor-side:
  `ResolvedBrush.laysOver`, `ModularStroke.begin`. Checks: `LightOverDarkChecks.acrossKinds` (White chalk over Fill in,
  Steady line, Permanent marker, Wet gouache, Grey wash and Soft charcoal; erased both ways; Undo, Redo; pen over it
  after; a white wash stays black; a white gel pen over gouache); `FillChecks.STRIPS`' three light fills over a pen
  fill taken again (they now cover it; strips still write what one piece does); `PageExportChecks`' sample page now
  exports an Old Paint layer (the base: what its light marks covered); a save and open of chalk over pen and wash.
  Picture:
  `docs/archive/pictures/explore/chalk/chalk-over-black.png` (`java LightOverDarkChecks chalk <png>`: before on
  `origin/explore`, after on this branch).

## Surface: the paper belongs to the page (2026-11-01, branch `explore-surface`)

The owner's "Surface moves to the notebook" (docs/superpowers/specs/2026-11-01-surface-design.md): a notebook's
Surface and a page's own, which every brush meets.

- **Engine (pure Java, desktop-checked):** `BrushTexture` is the surfaces, with `absorbency` (Feathering's table moved
  here), `fibreLength`, `fibreAngle`; its `grain()` is now made whole by the new `SurfaceGrain` (2048 px, seamless, no
  repeat across a page: Cold Press, Laid, Canvas, Kraft and Unryu made from nothing, the other scans laid as patches),
  two kept besides plain, `warm()` making one out of the way. `PaperGrain` takes a grain of any power-of-two side and an
  optional fibre map; `scan()` reads a 256 px scan as before (Surface tiles, `swatch()`). `PaperNoise.octave` has a
  rows-only form, for sharing the work. `ModularBrush.onSurface(surface)`: a stroke's copy of its brush on the page's
  surface at its own Texture; `QuickStroke.begin`/`quickly` and `ModularStroke` call it with `ToneDocument.surface`
  (null: the brush's own paper, as before). `ResolvedBrush.Wet.wash` reads the surface's absorbency (rim, Bleed,
  feather, `wets`). A host that never sets a surface draws exactly as before but for the new grains.
- **Upstream files:** `ToneDocument` (+8: the `surface` field, carried by `Snapshot`; not in the page's bytes) and
  `WetWatercolor` (+10 −4: `Wash.wets`, the freshness at which a wash's water wets blank paper, upstream's `BLEEDS`
  constant by default, so `bleedInto` reads the wash's; marked Inkwash). Generic: any host could set a page's paper,
  and any wash its wetting.
- **Scribulor-side:** `NotebookOptions.surface` (`SURF`), `DrawingBook` page surfaces (`PSRF`, `BookOptions`,
  `BookCodec`, `RecoveryCodec`), `LibraryCard`/`CardSheet`/`CardSelector`/`NotebookScreens` (the Surface rows and
  screens), `PaintActivity.setPageSurface`, `DrawingPad.showPage` → `WildSample.show` (samples on the page's surface,
  kept per surface), `BrushTestPad`, `BrushLab.chips` (no Paper chips) and `BrushLabActivity` (no swap panel for Paper).
  No Paper card (the owner, later that day): `BrushPart.SHOWN`/`SHOWN_LATER` drop it and its label reads Texture,
  `BrushLab.dials(MEDIUM)` adds `TOOTH` (its slider sets the PAPER part, codes byte for byte), `LabLayout` is three rows
  again (counting the Texture row in Medium's card, `shows`), `ModularWords.INSIDE` (Medium "ink · paint · pencil · texture ·
  fling", After "fades · spreads · ages"); `docs/brush-lab/app-features.json` regenerated (no Paper part).
- **Web engine:** TeaVM has no `SplittableRandom`: `SurfaceGrain.Dice` (SplitMix64) makes the same grains in Brush
  Studio's WebAssembly.
- **Checks:** `SurfaceChecks`; golden hashes re-pinned where a brush's paper was a scan or its wash pools on Smooth
  (`LegacyConversionChecks` 5, `ToolPresetsChecks.PINNED` 49, `WetPaintChecks.BEFORE` 24 rows, `FillChecks.STRIPS` 40);
  thresholds moved where the new grains change a look (`DryingWaterChecks` midway ±3 %, `OilChecks` ±5 px,
  `LightOverDarkChecks` Dry round, `WildSampleChecks` White chalk and the faint pencil, `GreyBarChecks` a wet wash's
  median 80 %); `LineStamperChecks`' tilt check at Texture 0, `EdgeRippleChecks` unchanged (Canvas's weave made less
  regular to keep it).

## Blend reaches oils again; Blur (2026-11-01, branch `explore-smudge`): no upstream files touched

The owner: "blend doesn't seem to work as a smudge tool", "it only seems to work on certain things?". Since kind layers
(2026-10-27) a stump's other half, the pair's wash, was worked on its Wash plane only (else its base), so Blend never
reached the Opaque plane where Cover lies (gouache, Rob boss's oils): 0 px moved. `ModularStroke.alsoWorks` now gives
the stump each paint plane the wash half has (Wash and Opaque), its base when it has neither, as the one base held them
all before planes. Unchanged, as the kind layers spec says: pen ink and printed dots (Ink on the ink half's Pen and
Paint planes) stay crisp under Blend; Water lifts the Wash plane only.

**Blur** (the owner: "a simple blur might be good as a third option?"): the smudge tool's third Basic tool
(`ToolSlots.SMUDGE_TOOLS`, `ToolPresets` under Charcoal, `personalities.txt`), the Blend stump with an Ink medium and no
Press. No new part or dial: `ModularStroke.blursAll`, a stump (`stump`, the test `alsoBlends` had) of Ink, works every
plane of the pair as Reprint does (`alsoWorks`, one walk for both), so it blurs pen, pencil, printed dots and paint
alike. No Basic tool before was a stump of Ink. Checks: `SmudgeReachChecks` (each `ToolSlots.FIRST` brush, 2.5 s, then
Blend, Water or Blur at a pen's pressure: moved or left, as intended), `ToolSlotsChecks` (the chooser's three),
`ToolPresetsChecks` (139).

## The paper's grain a little quieter (2026-11-02, branch `live-texture-softer`): no upstream files touched

The owner: "there's a lot of the paper coming through on the base ink wash … we could tone texture down systemwide a
tiny bit". `StrokePaper` is where every six-part brush's stroke reads the paper's grain (its `fine` and `mid` noises:
the stampers, the line rules, washes' granulation, Reprint, the wet washes' grain); `StrokePaper.SHOWS` (.85) draws both
toward their middle, so every brush on every surface keeps its look with the grain 15 % quieter. No dial, part or code
changes; washes' absorbency (Cold Press as tuned) is untouched. The paper's blooms and blobs (`low`, `blob`), a stick's own
grain (`StickGrain`, `Crayon`), Later's Feathering (`LaterMedia.height`) and the Surface screen's swatches read as before.
Grain contrast of a flat fill (std-dev with the strokes' own unevenness, measured at no grain, taken out), Smooth / Cold
Press / Felt: Ink wash brush 6.5 / 8.2 / 5.6 → 5.5 / 7.0 / 4.7; Soft charcoal's paper share 15.2 → 13.4 (Smooth); HB pencil
37.4 → 36.1 (its graphite meets the paper's high points by a threshold, so less changes); Grey wash none (no granulation).
Picture: docs/archive/pictures/explore/texture/grain-before-after.png. Checks re-pinned: `ToolPresetsChecks.PINNED` 61,
`WetPaintChecks.BEFORE` 36 rows, `FillChecks.STRIPS` 49, `LegacyConversionChecks` 13 pages; `StrokeBasicsChecks` reads
`SHOWS`; `GravityChecks`' drips never closer than .95 of the mean width (0.995 now); `ErasableChecks` and `MirrorChecks`
leave out a pen's pixel black at coverage 254, which the eraser reads as a Tone dot (`MirrorChecks.blankButDots`, the rule
MirrorChecks already used): Everything scrapes those by chance, so a Steady line can leave one (a known collision of
`PrintScreen.DOT_COVERAGE` with ink, open).

## Brush names: paint colours, more deadpan, a part and a name (2026-11-02, branch `live-brush-names`): no upstream files touched

The owner, of the auto names: "it's ok but seems to repeat quite often"; chosen: all three of more deadpan variety,
paint-colour names (the main style) and a part with a name. Scribulor's own, pure (`BrushNames`):

- **Kinds**, by the seed (`name`): about 55 in 100 a paint colour's (`coloured`: a [colour] and a [noun] word, about
  one in four with another [colour] word first; the first word of the first line the brush has there, the rest of the first two, but "any": "Harbour Fog",
  "Burnt Toast", "Rainy Harbour Fog"); about 30 a deadpan form (`deadpan`: 24 `FORMS`, their words from [deadpan]'s
  19 lines, 316 words where there were 54); the rest the part and a name of the new `names/folks.txt` (`folk`: "Wash
  McGrath"). Then the other kinds, the words, the plain words, the first that fits two lines of a card, as before.
- **No repeats**: `name(b, taken, recent)` passes by a name sharing a deadpan form (`form`, known by its words: no
  word is in two [deadpan] lines) or a word (`marks`: but the part's and describer's, which alike brushes share) with
  the library's ten latest (`RECENT`), else takes the first not taken. `BrushLab.suggestion` and New and Randomize
  (`rollNew`: `described` again with the library; the roll itself unchanged, drawing nothing more) pass the library's
  newest. 500 rolled saves: before, 10 shapes, one up to 6 times in ten saves in a row, a first word up to 4 times,
  481 distinct; now 24 forms plus the two kinds, no form twice in ten, a first word at most twice, 500 distinct.
- **Web:** the browser app's Brush Lab is the Java (TeaVM), so it names this way. Brush Studio asks the engine too
  (`WebEngine.brushName(code, library)`, `engine.js` `brushName`; `studio.js` `appName` for the suggestion and
  Randomize); without WebAssembly GC, and in the web lab (`modular-lab-src/parts.js`), the JS copy still names in the
  older eight forms: a legacy copy, as the lab's Print and Rework words. Its `NAMES_TRAITS` keeps the older list;
  `BrushNamesChecks.sameAsLab` now holds its [part], [describer] and [fun] to the app's, and pins 19 app codes' new
  names (`LAB_NAMED`, `SEEDS`) rather than the lab's.
- **Text:** `docs/TEXT-STYLE.md`'s new brush name. Notebook titles ([title]) unchanged.
- **Checks:** `BrushNamesChecks` (`traitList`: the new sections, no word in two [deadpan] lines, kind words; `folkList`;
  `deadpan`: every form made and known; `rolls`: the mix, colours hint by their words, the rest say the part;
  `variety`: 500 saves as above, deterministic, all fit, none rude); `scripts/live-probe/m2-check.cjs` (an edited
  brush's suggested name); Studio's `check.cjs` compares with `appName`.

## Make Stamp: on the Lasso's bar, and a hint until the first print (2026-11-02, branch `stamp-flow`): upstream files touched

The owner tried the Stamp and wasn't sure how to use it. Approved: Cut Stamp's button reads **Make Stamp** (a display
label: `StampNib.CUT`'s words, Brush Lab, the Tools page, the stamp menu, the browser), and the Lasso's bar has it too.

- **One cut.** `ToolSlots.cutStamp(slot, page, …)` and the new `ToolSlots.makeStamp(piece, selected, shown)` end in the
  one `cutStamp(slot, stamp, nib)` (the nib kept, an Undo step). The Lasso's nib is `LassoPieces.nib(piece)`: the
  floating piece's own pixels (`tones` over white by `alpha`) through `StampNib.cut`, so a moved, turned or pasted
  piece cuts as it was lifted. Its slot: the Stamp in use, else the bar's first, else the slot in use becomes
  the Basic tool Stamp (`basicStamp`, so its row shows the owner's drawing; the eraser's or smudge tool's turn: the
  first slot).
- **The bar.** `DrawLayout.LASSO_BAR` is Cut, Duplicate, Make Stamp, Delete. `InkWashBar.lassoBar`'s third word calls
  `DrawingPad.stampSelection` → `PaintActivity.makeStamp`, which takes the toolbar's fix as Stamp ▾'s rows do, then
  `stampMade` (shared with `stampCut`): the slot in use (`selectTool` puts the Lasso away and the piece down where it
  lies), "Stamp cut" beside it; "Nothing to cut" leaves the piece up.
- **The hint.** `DrawLayout.stampHint(brush, placed)`: while a Stamp is in use and none has been printed
  (`PaintPreferences.stampPlaced`, key `stamp_placed`), `StampNib.MAKE` ("Lasso a shape, then Make Stamp") for a Stamp
  with no nib, else `StampNib.PRINT` ("Draw to print the stamp"). `InkWashBar.showBrushLine` sets it; it shows in the
  foot's corner over the lasting status (`statusCorner`, which keeps Draw Together's words for after). A stroke laid
  while it shows (`DrawingPad`'s lift → `InkWashBar.laid`) sets the flag, and the hint goes for good. Not in the
  browser: its guests aren't the owner.
- **Web.** `WebEngine.lassoWords` adds the cut's words; `lassoStamp(p, selected, shown)` is `makeStamp`, the slot the
  guest's own, the piece put down; `lassoAct`'s Delete is 3 and Paste 4 (`m4.js`, which gets `ownTools`). Found on
  the way: `live.html`'s `showRow` drew a slot's stroke into a row 0 wide while Zen Mode hid the column, and the engine
  threw ("requested new array is too large") for any slot without a drawing; now it draws none there.

| File | Change | Why |
|---|---|---|
| `DrawingPad` | +3 −1 | `stampSelection` for the bar's Make Stamp; the lift tells `InkWashBar.laid`. |
| `PaintActivity` | +18 −2 | `makeStamp`; `stampMade` shared with `stampCut`, which now puts its slot in use. |
| `PaintPreferences` | +3 | `stampPlaced`, the one-time hint's flag. |

Checks: `StampChecks.slots` (the piece's nib as Cut Stamp's, a Stamp on the bar takes it, none: the slot in use
becomes one, an Undo step, the eraser's turn the first slot, the hint's two words and none after), `DrawLayoutChecks.lasso`
(the bar's four words, the bar's source, the row fits the Nomad's page), `LabLayoutChecks.fits` (Brush Lab's Back to at
least 95 dp on the Nomad, 100 before: Make Stamp is a letter longer), `scripts/live-probe/stamp-check.cjs` (Make Stamp),
`m4-check.cjs` (the bar's four words; Make Stamp on slot 8: a Stamp in use, the Lasso put away, the mark kept; Zen Mode after it).

## Brush Studio's codes and tiles through the app's engine (2026-11-02, branch `studio-java`): no upstream files touched

Nine Basic tools (Ruling Pen, Twig Pen, Fan Brush, Brayer: newer tips; Metalpoint, Cracks, Letterpress: After;
Photocopier, Riso: newer prints) drew wrong in Brush Studio: its brush model is still the lab's JS copy, which drops
what it doesn't know, and the code it wrote is what the WebAssembly engine drew.
- **`web/engine`:** `WebEngine.keptCode(app, before, after)`: an edit made in a model that can't hold all of `app`
  (`before` its own code for it, `after` that edited) keeps what the edit left alone (style, seed, nib, each part's
  choice and each dial) as `app` has it; `WebEngine.studioSample(code, role, w, h, dark)`: a tile as the app's cards
  draw it (`WildSample.modular` LOOP, `WildSample.part`).
- **Studio:** `render.js` `appRead`/`appCode`, every code Studio reads or writes (`studio.js`, `community.js`): a code
  the lab's model can't hold is kept on its brush (`app`, `app0`), written as is while unedited, through `keptCode`
  once edited. Plain loops and part waves are `studioSample`'s; the sweep's tilt and drops tiles, paths and the test
  page as before. Not yet: editing a newer tip, After or print in Studio (the UI is still the lab model's; no After
  card); without WebAssembly GC an edited one drops them, as before.
- **Upstream files touched:** none. No app Java changed.
- **Checks:** `presets-check.cjs` (139 of 139 written back as the app writes them, was 130; the 9 edited keep the rest
  of their code; Node needs `--liftoff-only` to exit promptly after loading the module); `check.cjs` (Metalpoint loads
  as written, edited and saved keeps its After across a reload).

## Brush Studio's After card, newer tips and prints, from the app's model (2026-11-03, branch `studio-after`): no upstream files touched

Roadmap step 10: Studio edits what the lab's JS model can't hold, through the app's own model (WebAssembly), not a JS copy.
- **App (pure, ours):** `BrushLab.chips`, `dials`, `slide` and `settle` (the brush settled after a slide) have static
  forms on any `ModularBrush`; the lab's own calls go through them, unchanged.
- **`web/engine`:** `WebEngine.studioParts(app, own)`: each part (`BrushPart.SHOWN_LATER`) as Brush Lab shows it, its
  choices (`BrushLab.chips`), dials (`BrushLab.dials`: label, value, range, default, options, words) and `held` (Studio's
  model's code `own` differs there); `WebEngine.studioEdit(app, part, choice, dial, value)`: a choice switched as Brush
  Lab's chip does (`BrushLab.switched`), a dial as its slider does.
- **Studio:** `render.js` `appParts`/`appEdit`; the After card always, and any card whose part the lab's model can't hold
  (a Ruling Pen, a Riso print), is built from `appParts` and edited through `appEdit`; the lab's cards add the app's newer
  choices and options (Ruling Pen, Twig, Fan, Brayer; Risograph, Photocopy, Soft Dither) as swatches and chips edited the
  same way. `commit` counts a change the lab's key doesn't see. Not yet (a `shortcut:`): the app's cards have no lock,
  dice or sweep; Randomize, Mix and Random are the lab's model's (After off). Display text says My Bad Drawings
  (`Document/My Bad Drawings`, `My Bad Drawings.brushset`); codes unchanged.
- **Upstream files touched:** none.
- **Checks:** `presets-check.cjs` (the 9 edited on the app's cards: each part the lab's model can't hold changed, back,
  a slider moved, written and read back the same, the other parts kept); `check.cjs` (159: seven cards in the app's
  order; After's choices, a choice and a slider, Undo; Ruling Pen and back; Risograph saved and reopened).

## Brush Studio's ⓘ buttons to the Guide; the app's cards lock and roll (2026-11-03, branch `studio-info`): no upstream files touched

The owner, 2026-11-03: "in the brush studio, can we add i info buttons for the different sections and sliders that links
to the page on the visual demo?"; then: the app's cards (newer tips, After, the prints) need the lock and dice too.
- **Guide anchors (`dial-demo-src`, shared with Brush Lab Dials):** `demoTiles` gives each tile a stable `anchor`
  (`tip-round`, `tip.weight`, a dial with a second label on other choices `carry.press.<first choice>`) and dial tiles
  their choices (`on`); `view.js` sets it as the tile's id (`g-` in Studio). Brush Lab Dials unchanged otherwise.
- **Studio, ⓘ (`guide.js`, `studio.js` `infoBtn`):** a small `<button class="icn info">` ("About Tip", "About Size") by
  each card's name and each dial's name; it opens the Guide at the dial's tile (`guideId`: part, dial and the card's
  choice), else the part's section (Texture is Medium's), scrolled to, focused and outlined for 1.6 s. After has no
  Guide section, so its card shows none. **Back to Size** (the Guide's bar) returns to Adjust, on that ⓘ; it shows only
  after an ⓘ. The tiles are made once (`guideTiles`) for both.
- **Studio, the app's cards:** a lock and a die on the part and every dial, as the lab's cards (the same `ST.locks`);
  the dice through the app's model (`appPartAs`, `appRollV`: `studioEdit`, no JS copy). Randomize, Style and New
  (`rollBrush`) then put back through the app's model what the lab's roll can't (`appRolled`): a locked part the app's
  model holds (After, a Brayer, a print) as it was, a locked dial on the same choice, After on its choice with its
  unlocked dials rolled; Random's Anything goes (`wildRoll`) also picks among the app's choices (a Twig, a Riso print,
  After half the time). Random's locks include After. Mix already kept After. Not yet (the `shortcut:` left): no sweep or
  before/after for the app's dials (the focus is the lab's model's).
- **Upstream files touched:** none.
- **Checks:** `check.cjs` (174, was 159: every card but After and every dial has an ⓘ by its name; each lands on an entry
  that exists; every dial of every choice the app offers has one, its own tile where the Guide has it; a Tip dial's ⓘ
  opens its tile in view, marked, with Back to; Back to returns on the ⓘ; Medium's opens its section; a Brayer and an
  After dial rolled by their dice, the rest kept; a locked dial's die off; a locked Brayer and After's locked dial, then
  a locked After, survive Randomize; unlocked, After keeps its choice).

## Record and Replay logs every edit (2026-11-03, branch `log-everything`): upstream files touched

The owner: record eraser and smudge strokes and Lasso edits, then "anything that a brush does to affect other stuff on
the page", so a page can be redrawn from its log (towards resolution-free pages). `StrokeLog` entries now have a kind:
`PEN`, `SMUDGE`, `FINGER` (brush strokes; the smudge tool's and a finger's as a slot's), `ERASE` (the Eraser, the pen's
eraser end, the browser's finger: size at its broadest and Erase Kind's kinds), `ERASE_FINGER` (the Manta's finger
eraser, one size), `LIFT` (a Lasso loop, on half pixels) and `PLACE` (a lifted piece put down where it lies or deleted:
its lift as entries back, drop and cut, the pose: centre, scale, angle), `CLEAR`. Each entry also keeps its brush's
paint holder (`ToolSlots.holder`, Keeps Paint) and the drying ticks (`WatercolorSession.tickDrying` while something
settled) the page took since the entry before.
- **Recording (shared):** `StrokeLog.Recorder.of(brush, look, eraseKinds, holder, smudge, finger)` for the Manta's
  `InkWash.stroke` and the browser's `WebEngine.pen`; `Recorder.fingerEraser` for `InkWash.fingerEraser`. A stroke
  that changed nothing is logged too (it may have laid what was settling or picked up paint; the owner's 3x run saw
  effect strokes with nothing under them), tied to the step before (`Entry.stepped` false; `ToneDocument.newestStroke`,
  Live pages' probe, still names only the pen stroke that made the newest step, so Draw Together sends as before).
  The Lasso logs in `LassoPlanes` (both screens' only way to lift and lay an Inkwash piece): `lift` takes its loop on
  half pixels and logs it (`ToneDocument.logLift`); `place` logs what it did (`logPlace`) when the page changed and the
  lift is in this page's log (a piece carried from another page isn't logged). Ticks: `tickDrying` counts on the page
  (`ToneDocument.ticked`), each logged entry takes the count.
- **Replay (shared, `ReplayRun`):** each entry's ticks first, then a stroke as `StrokeStart.stroke` starts it unmirrored
  (the eraser through it too; the brush's holder first), a lift through `LassoPlanes.lift`, a placing through
  `LassoPlanes.place` on the lifted piece (cut) or a copy of it (Duplicate's, Paste's), posed as logged. A replay runs
  with a session of its own (`ReplayRun.session`), the Manta's now as the browser's: the drawing screen's held paint,
  settling wash and tilt stay as they are. `InkWash.replay` (unused) is gone.
- **Determinism fix:** `EraseBothStroke`'s Tone-dot scrape salt was the page's revision and the first sample's float
  bits; now which eraser pass it is on the page object (`ToneDocument.erasePasses`, not kept in the file) and the first
  sample on half pixels. A replay's copy starts its count where the log's first eraser stroke began (the page's count
  less the log's eraser entries), so it scrapes the same dots; a cancelled finger eraser or an eraser stroke undone and
  dropped shifts the count for the strokes after it (their dots scrape otherwise; nothing else changes).
- **File format:** `RPLY` version 2 (each entry its kind and ticks; sizes and greys as doubles, so a replay draws at the
  size drawn); version 1 reads as before; a version or kind this reader doesn't know reads as no log, and
  `DocumentCodec` now skips an `RPLY` section longer than `MOST_BYTES` instead of refusing the page. `encode` writes
  the newest entries that fit `MOST_BYTES` (1 MB: the reader before this one refuses a longer section, so this can't
  grow until every build in use has the skip); a placing whose lift didn't fit is written with none. `MOST_SAMPLES`
  60,000 to 100,000 (eraser strokes and loops count).
- **Not logged:** fill and gradients, shapes, Make Ink, text boxes, magnets, placed pictures (`FloatingSelection`
  without planes), layer changes (a replay draws on the copy's selected pair), Clear layer, Clean Brush and paint held
  from before the page's first stroke, mirror copies, a pen held still (Stipple's Build-up), tilt for Runs Downhill,
  Fading and Lemon Juice's clock (a replay's marks start fresh), what settled after the newest entry. A Stamp's prints
  are brush strokes, their code naming the nib by id (`StampNib`): not checked once the nib is gone.
- **Upstream files touched:**

| File | Change | Why |
|---|---|---|
| `ToneDocument` | +30 −4 | `logLift`, `logPlace`, `logStroke(recorder, changed)`, `ticked`, `erasePasses`; `newestStroke` skips what didn't make its step. |
| `DocumentCodec` | +3 −1 | An `RPLY` section past `MOST_BYTES` is skipped, not refused. |
| `DrawingPad` | +6 −3 | Records a finger's strokes and eraser too; logs a stroke that changed nothing. |

Checks: `StrokeLogChecks` (`replaysAll`: pen, finger, both erasers, one kind, the smudge tool's three, Wet gouache
worked by a knife and a dirty brush, Newsprint, Photocopier, Letterpress, Cracks, Clear water, washes left 4 ticks and
to the end, the Lasso's move and turn, Duplicate, Delete, Cut and Paste: replayed on a blank copy, every plane of both
layers and the fluid byte-equal, from the page and from its file; Undo and Redo hide and show the Paste; `versionOne`,
`fits`, a stroke that changed nothing); `OlderReplayCheck` in `scripts/test_inkwash.sh` (56dad77f opens such a page as
saved, without a replay, and saves it again). Gap: samples are fed in one batch per step, so a Fast-wet brush's layer
coverage can differ where the pen's input was batched otherwise (`QuickStroke`'s note); with the original presented
every 8 samples, 478 of 648,000 plane bytes differ. Samples off the log's grid (half pixels, 255ths, whole degrees,
2 s pauses) replay rounded.

### Replay fixes (2026-11-03, branch `log-fixes`): upstream files touched

The owner tried the logged replay on the Manta (`Letters to a Toad`, page 3: a pen stroke, two erasers, 16 Blur
smudges, a Lasso move, stamps, Grey washes, Newsprint, Ben-Day) and hit two bugs.
- **Crash** (`IllegalStateException: Finish the current gesture first`, `ToneDocument.fade` from `DryingTicker.fade`):
  the pad's ticker ran on the page shown, the replay's copy, while a replayed stroke was open between steps (and the
  copy carried the page's fading marks). The ticker's host is now busy while `app.replay.running()` (as `LiveProbe`
  and autosave already wait on `app.busy()`), and `ReplayRun`'s copy starts without the page's fades
  (`placeFades(empty)`). A replay never writes the page, its log or its file (it plays on its own copy, which no save
  reaches; `onPause` stops it first): `StrokeLogChecks.untouched`, part-way and played out.
- **"The initial stroke ... showed up with eraser marks":** the veil (`GhostPlane.offMarks`, paper wherever the page
  shows no mark, from Replay's veil of 2026-10-26) hid, from the first step, every pixel the eraser would take later
  (646 of the first stroke's 3,085 samples at step 1 on the owner's page). `ReplayRun.veiled`: only a log of pen
  strokes alone (one from before the eraser and Lasso were logged) plays under it. `StrokeLogChecks.veiledOnlyPens`.
- **Washes replayed unsettled:** the Manta settles on `SettleWorker` and in wet-paint slices (`WatercolorSession.tick`),
  never `tickDrying`, so the owner's page logged 0 ticks everywhere. Both now note a natural end on the page
  (`ToneDocument.settled`): the next entry's ticks are `MOST_TICKS`, "all of it", and a replay ticks until nothing
  settles. A stroke drawn while a wash still settles there still replays it laid unsettled (partial worker ticks aren't
  counted). The paced and unpaced ticks settle a Slow wash a few pixels apart (23 bytes of the planes in the check).
  `StrokeLogChecks.settledThere`.
- **Left as is:** the owner's page replays with 4% of its pixels apart from the page, chiefly its washes (logged before
  this fix, 0 ticks), its Later marks (their clock: a replay's start fresh) and, on the desktop, its Stamp prints (the
  nib isn't there).

| File | Change | Why |
|---|---|---|
| `DrawingPad` | +3 −1 | The drying ticker's host is busy while a replay plays. |
| `ToneDocument` | +3 −1 | `settled`; `ticked` capped. |

### Replay logs shapes, pictures and merges (2026-11-03, branch `log-more`): upstream files touched

The owner asked for Replay to cover fill, shapes, placed pictures and layer changes as well. What the app has of each:
- **Fill**: no fill tool since the bucket left the bar (2026-10-23); fill is a brush's (Fill in: Stay in Lines and
  Filled Shape, `FillTool.start` at the lift), so it was logged as a pen stroke already and replays through
  `StrokeStart`. Now checked (`StrokeLogChecks.more`: a box Fill in starts in, a loop on open paper).
- **Shapes** (`StrokeLog.SHAPE`): the slot's brush code (its layer), the shape's name, filled, outline width, grey and
  the drag's two points as floats (`StrokeLog.Shape`, made by `ShapeGeometry.logged`). Logged at the lift by
  `DrawingPad.continueShape` (`ShapePreview`) and the browser's `shapeEnd` (`ShapeStroke`). What comes before a shape is
  one helper now, `StrokeStart.shape` (`InkWash.shape`, `WebEngine.shapeDown`, a replay), and the shape is laid by
  one, `ShapeStroke.lay` (`ShapePreview.finish`, a replay).
- **Pictures** (`StrokeLog.PICTURE`): `PictureImport.place(page, …)` (the Manta's Place and the browser's `placeLay`)
  logs the greys (255 for clear: a grey of 255 never darkens a layer, so it is lossless), their place and the layer
  drawn on. Pixels (`StrokeLog.Pixels`) are kept deflated within `MOST_PIXELS` (a quarter of `MOST_BYTES`, so the
  entries before still fit); a picture whose bytes don't fit is kept every 2nd pixel each way, then every 4th, and
  so on, each kept pixel replayed for its square (the check: 1000 × 800 of noise, every 2nd). Its
  bytes count against `MOST_SAMPLES` at 6 a sample. Replay lays it through `PictureImport.lay`.
- **Layers** (`StrokeLog.MERGE`): a layer is a page of its own (Stage 3c), so adding, deleting and Show Through
  steps change no page's pixels and aren't logged. Merge Old Layers (and Merge Down, which no screen offers since the
  refocus) logs inside `InkWashLayers.mergeAbove`, the one merge both go through: the page's layers just before
  (opacity, hidden, the one selected) and, for Merge Down, the layer above flattened (tones and coverage, a 2-plane
  `Pixels`). A replay starts on blank layers like those before the first merge (`Merge.before`), then merges again
  through `mergeAbove`. The flattening is shared (`InkWashLayers.flattened`). Merge Down replays within rounding (83 of
  2,376,000 plane bytes in the check: the layer above is merged flattened, a byte a pixel); the rest replays byte for byte.
- **Format**: version 2 still; kinds 8 to 10 are new, and a version 2 reader without them reads no log (as an unknown
  version): `OlderReplayCheck` against `1886cebf` opens such a page as saved, without a replay. A page with none of
  them writes as before.
- **Not logged**: a live page's changes from another tablet (`LiveProbe`'s `PictureImport.lay`), Make Ink and text.

| File | Change | Why |
|---|---|---|
| `ShapeStroke` | +12 | `lay(document, spans, shade)`: a shape's commit, shared with `ShapePreview` and Replay; `logged()`. |
| `ShapeGeometry` | +2 | `logged(shade, x, y)`: the shape as `StrokeLog.Shape`. |
| `ShapePreview` | +3 −6 | `finish` through `ShapeStroke.lay`; `logged(x, y)`. |
| `DrawingPad` | +3 | Logs a slot's shape at its lift. |
| `ToneDocument` | +5 | `logMade`: a shape, picture or merge as the newest step's entry. |

## System Settings: Automatic Refresh and the touch controls (2026-11-04, branch `system-settings`): upstream files touched

The owner: "i find the frequency of screen flashes to be a bit much.. i think the goal should be to prevent having to
flash more than relying on it ... can we add system settings? ... one thing it would have is the ability to disable the
automatic flashes ... also that's where we could allow people to customize the touch controls (2 finger tap, etc)".
A new screen, `SystemSettingsActivity` (the Tools page's `LabUi.PageBar`, `LabButton.chip` rows), from the menu's app
group (`DrawLayout.fileRows`' `system` row, after the hand). Its words and the flash gate are the pure `SystemSettings`
(in the desktop sources and the web engine's, `SystemSettingsChecks`). Saved in `PaintPreferences` under new keys
only: `auto_refresh` (on), `two_finger_tap` / `three_finger_tap` (`FingerTool.Tap` keys: `undo`, `redo`, `refresh`,
`nothing`; Undo and Redo at first), `edge_hold` (`EdgeHold.Choice`: `trash`, `lasso`, `nothing`; the Trash at first).

The app's own flashes (the firmware's full refresh, `Ui.flashWindow`'s `screenRefresh`) all ask
`SystemSettings.flashes(asked, prefs.autoRefresh())`: `DrawingPad.autoRefresh` for a ghosted page (a page turned,
added or opened, Zen Mode, a pinch or pan ended, the eraser, a Spiro ring or an effect's trail) falls back to its
forced grey pass; its pen-away pause skips the flash; `DrawingPad.connectDisplay` back from another screen and
`NotebookScreens.cover` give the window the clean update alone (`Ui.flashWindow(…, flash)`). Refresh (the column's
button, or a tap set to it) is asked for (`DrawingPad.refreshAsked`) and always flashes.

| File | Change | Why |
|---|---|---|
| `DrawingPad` | +12 −5 | The flash gate (`asked`, `flashes()`, `refreshAsked`); a finger tap does its chosen `FingerTool.Tap` (the palm gate, `tapCounts`, first, as before). |
| `PaintPreferences` | +13 | `autoRefresh`, `twoFingerTap`/`threeFingerTap`, `edgeHold` and their setters. |
| `PaintActivity` | +2 | The menu's `system` row opens the screen. |
| `Ui` | +7 | `flashWindow(root, reason, restore, flash)`: the flash or the clean update alone. |
## A snapped line's end follows the pen (2026-11-04, branch `snap-drag`): upstream files touched

The owner: "i'd love to be able to reposition the final point, so the line can change angle from the original point,
then on pen lift it's fixed". After Hold to Snap makes a line, the pen moving off its rest (`HoldSnap.moved`, Fling's
`STILL_PX`) takes the line's end along. `HoldSnap` (engine side, pure) keeps the last snap's shape and samples:
`line()`, `to(x, y)` (the line from its start to a new end, each rough sample the same share of the way, through
`along`). The pad takes the brush's line back as a snap takes the rough stroke back, shows the line through the Shapes
tool's own preview (`ShapePreview`, a `LINE` at the slot's size and grey, the fast binary update, frames coalesced by
`schedulePreviewFrame`), and at the lift draws it once with the brush along `to(lift)`. Redrawing the brush on every move
was not tried: a wet brush's whole stroke finished, taken back and drawn again at each frame is too slow for the panel.
Replay logs the lifted stroke (as a snap's); Draw Together sends the lifted line alone (`drawn` waits while a snap draws
again, so the rough stroke and the line before the drag are no longer sent first). The browser (`WebEngine`, `m4.js`)
is unchanged: it snaps as before, without the drag.
- **Upstream files touched:** `DrawingPad` (+60: `takeBackStroke` and `drawAgain` out of `snapTo`, shared with
  `dragSnap` and `liftSnapLine`; `sampleEvent` starts the drag; `continueShape`'s lift draws a dragged line; `beginShape`
  takes the shape's start; `drawn` waits under `quietPanel`; the drying ticker and `stroking` (Live's laying) wait
  while a shape is shown, for the Shapes tool's drag too).
- **Checks:** `HoldSnapChecks.dragged` (the start kept, the end at the pen, each sample's share, pressure, tilt and time
  kept, the points on the new line; a circle doesn't move).
## Every snapped shape follows the pen (2026-11-06, branch `snap-follow`): upstream files touched

The owner: "lets make them similarly repositional". The line's drag above now takes every kind (`HoldSnap.follow`,
used by `to`): a polyline's last point at the pen; a line or an arc turned and scaled about its start, its end at the
pen (an arc's bulge in proportion); a circle, oval, polygon or box turned and scaled about its box's centre, the pen's
rest at the pen (a circle stays round). The pad's drag, lift, Undo, Replay and Draw Together are the line's, unchanged.
The preview is no longer the Shapes tool's `LINE`: `ShapeGeometry.path` rasters the followed path at the slot's size,
round ends and joins, six intervals a row (`PATH_ROW`; the narrowest gaps closed past six), through `ShapePreview`'s
new `Raster` constructor. The browser now follows too (it never dragged the line): `WebEngine.snapTo` and m4.js's
`follow` draw the brush again at each move (fast enough there), the lift sending the whole box as a snap's.
- **Upstream files touched:** `ShapePreview` (+8: `Raster`, a preview of any ints a row; the Shapes tool's is the
  `ShapeGeometry` at 4, as before), `shape_preview.c` (the ints a row read from the arrays' length, not fixed at 4),
  `ShapeGeometry` (+60: `path`, `capsule`), `DrawingPad` (`beginShape` takes the preview; `snapLine`/`liftSnapLine` are
  `snapDrag`/`liftSnapDrag`; any snap drags).
- **Checks:** `HoldSnapChecks.followed` (circle: centre kept, round, scaled and turned so the rest is at the pen;
  pentagon: the same about its box's centre; arc: start kept, end at the pen, bulge over chord kept; polyline: the
  corners kept, the last at the pen) and `rastered` (a circle's middle row two intervals the width; a zigzag's eight
  crossings kept to six, in order).
## Zen Mode's fan (2026-11-04, branch `zen-fan`)

The owner's sketch and mockup: in Zen Mode, the corner button shows the brush's dot (its size and grey, the column's
`SwatchReadout`); a tap opens a quarter-circle fan from that corner, 3 cm at most: the size wedge along the side edge,
the grey wedge along the foot (the column's steps, `SwatchBar.setSize` / `setGrey`, shared with the picker box), a
white gap between them as wide as the white band under a thin arc, and outside the arc the column's tools in its order
(the slots, Smudge, Eraser), each its row's picture turned outward. The X where the dot was leaves Zen Mode; only a
touch outside closes the fan (picking a tool keeps it open); a long press on the button turns Zen Mode on or off. Its
geometry is pure (`ZenFan`, checked by `ZenFanChecks`, in the engine's sources so the browser can use it); the
browser's Zen button is unchanged for now. No upstream file is touched.

| File | Change | Why |
|---|---|---|
| `ZenFan` | new, pure | The fan's geometry in dp: what a point hits, size and grey from a position and their markers' places, the tools' angles, mirroring. |
| `ZenFanView` | new | Draws the fan over the page (Android's partial grey, no flash of its own; markers through `SelectionFeedback`), takes its touches. |
| `PaintActivity` | +60 | Zen's button: the dot, the fan, the X, the long press; a touch outside closes the fan; Back closes it first. |
| `InkWashBar` | +25 −10 | `pick`, `hold` (one long-press path for the rows and the fan), `zenAnchor` so the tools' menus open beside the fan. |
| `SwatchBar` | +20 −13 | `setSize`, `setGrey`: the picker box's lifts as methods the fan calls too. |
| `ToolButton` | +2 | `plainIcon`: a row's picture without its mark. |
| `DrawLayout` | +1 | `ZEN_TOOLS`, the button's words while the fan is closed; `ZEN_HOLD`, the tap's hint while off (2026-11-05: Zen Mode on and off by a hold only, the fan's X now the circle). |

Follow-up (2026-11-04, branch `zen-fan-2`, after the owner tried it): the fan tucked in toward the corner (its point
the largest dot's radius and the band from the X, `ZenFan.corner`, `dotRadius`), one stroke width (`ZenFan.STROKE`),
and its redraws small: a drag only its marker's strip in the binary update, a pick or a lift only the changed boxes.

| File | Change | Why |
|---|---|---|
| `SelectionFeedback` | +3 −3 | `updateParts` takes a single view too (one patch, its changed pixels split into boxes as a group's parts are), so the fan's picks go out as the column's do; a group's path unchanged. |

Follow-up (2026-11-04, branch `zen-fan-3`): Zen's corner is the brush's dot in every state, the fan's size marker true
to the line's width, and the nib ring off over the open fan.

| File | Change | Why |
|---|---|---|
| `DrawingPad` | +2 −2 | `hoverOutline`: no nib ring while Zen Mode's fan is open (`zenFanOpen`). Upstream-touching: one condition. |
| `SwatchReadout` | +15 | `ring` (NONE, FAINT, DASHED): Zen's corner draws no paper and its circle; the column's readout unchanged. |
| `SwatchBar` | +2 | `show` tells the drawing screen (`showZenDot`) whenever the size or grey shown changes. |
| `PaintActivity` | +20 −6 | The dot is Zen's button in every state, its square the column's width; the icon empty but for the X. |
| `ZenFan` | +25 −5 | `width`, `sizeUp`, `truest`, `PAGE_PX`: the size marker where the wedge is as wide as the line. |

Follow-up (2026-11-04, branch `zen-fan-4`): the brush dot gone from Zen's button again (`SwatchReadout` back as it was
before the fan, `SwatchBar.show` no longer calls the drawing screen, `PaintActivity` without `zenDot`), the button's
dashed circle, circle and X; the fan's tuck a named 34 dp (`ZenFan.TUCK`); the fan a quarter larger (3.75 cm);
paper under the open fan, a disc round the X (`ZenFan.backdrop`, the view's bounds with it).

Follow-up (2026-11-04, branch `zen-fan-5`): `DrawingPad.cleanArea` (upstream-touching, one call) asks the drawing
screen to put Zen's button back (`PaintActivity.redrawZen`) after cleaning a closed fan's or popup's area: the clean
writes the page's pixels through the fast display, over the button, which Android doesn't know to draw again. The
window's pixels there (page and button) go back through `SelectionFeedback.clean`, the grey mode, as `cleanOffPage` does.

Follow-up (2026-11-04, branch `zen-fan-6`): the fan a quarter larger again, 4.7 cm (`ZenFan`'s sizes only).

Follow-up (2026-11-06, branch `zen-fan-tools`): `DrawingPad.hoverOutline` (upstream-touching, two conditions) shows the
nib's and the Dropper's rings with the fan open again, the fan now staying open while drawing; the fast display's mask
(`covered`) keeps them off it. `covered` is now a short list of rectangles (Words' bin, the fan's stepped disc and its
line of buttons, `ZenFan.mask`), cut from each write by `MagnetDrag.minus(int[], List)`, so strokes beside the open fan
show. (`ZoomButton.draw` stacked its padlock over the percentage for the fan's side strip; back as it was with `zen-fan-2`, the strip gone.)
## Vector Ink, phase 1 (2026-11-04, branch `vector-ink`): upstream files touched

The owner: "i just want to zoom in and have it be clean", with erasing that cuts lines; spec
`docs/superpowers/specs/2026-11-04-vector-ink-design.md` ("Phase 1 as built"). Vector Ink is a Basic tool (`ToolPresets`,
`VectorInk.CODE`, known by its settings' key, `VectorInk.is`) that draws on the top pair's ink half as lines kept on the
layer (`ToneDocument.Layer.lines`, an immutable `VectorLines`) and as their pixels at 100%, so every pixel reader is
unchanged. New, pure, Scribulor's own: `VectorLines` (the stroke model, smoothing, the variable-width anti-aliased
outline at any scale, the eraser's cut, the bytes) and `VectorInk` (the pen stroke, the eraser's cut, what is refused,
which layer the zoomed view draws sharp), checked by `VectorInkChecks` (and `OlderVectorCheck` against 04c30e6f, the
version before, which opens such a page as drawn). Both start through `StrokeStart` (the Manta, the browser, Replay):
`StrokeStart.drawn` starts `VectorInk.Pen` for its brush, unmirrored; `EraseBothStroke` cuts the pair's lines
(`VectorInk.Cut`) along its path and keeps the cut with its Undo step. The zoomed view leaves the layer out of the page
it scales and draws the lines over it at its scale, in plain greys, then the dots (`ViewportBitmap.sharpen`): no second
composite. The browser (`WebBooks.refusal`) refuses other inks on the layer as the Manta does and shows the 100% pixels at
every zoom. `VectorInkPictures` (test side, not a check) draws Steady line's pixels and Vector ink at 400% side by side.

| File | Change | Why |
|---|---|---|
| `ToneDocument` | +32 −2 | `Layer.lines` (copied with the layer); `lines`, `changeLines` (the lines with the stroke's Undo step, or a step of their own); `Edit.linesLayer/Before/After`, applied by Undo and Redo; `visibleLayers(…, without)`; `layerMarked`. |
| `DocumentCodec` | +33 −1 | The `LINE` section (a layer's index, length and `VectorLines.encode`), written only when a layer holds lines; an older reader skips it. |
| `ViewportBitmap` | +54 −6 | `compose(…, without)`; `update(…, lines)`: plain greys where lines are, the lines (`VectorLines.over`), then the dots; `dithered` draws them sharp at a bigger size. |
| `DrawingPad` | +42 −7 | `sharp()`/`sharpLines()`: the layer the zoomed view leaves out and the lines it draws (the stroke in progress's; none while a pinch or pan moves the view, sharp again at its end); a change renders the whole page once; `layersAbove`; Shapes, Make Ink, the Lasso, Paste and a carried piece refused on Vector Ink's pair. |
| `PaintActivity` | +5 | `allowStroke` asks `VectorInk.refusal`; a placed picture is refused on Vector Ink's pair. |

2026-11-04, later (branch `vector-ink-medium`, the owner: "Top layer only" on a page with pen marks): Vector Ink finds its
own pair (`VectorInk.room`: the top pair's ink, or one put on top in the same Undo step, `InkWashLayers.add`, linked with
`linkSince`), selects the pair it found again after; other inks on its pair go under it (`VectorInk.under`, from
`StrokeStart.drawn`, `StrokeStart.shape` and Make Ink); the eraser's cut is the page's lines' (`VectorInk.lined`) and
draws their pixels again where it went. No layers-above check (tracing paper): `DrawingPad.layersAbove` went. Its notes
show beside the tool in use (`PaintActivity.sayVector`, the label `InkWashBar.sayAt`), never at the brush line's right.

2026-11-04, `vi-autolayer-fixes`: the pair `VectorInk.under` puts in goes with the stroke's step, one Undo and one Redo
(`ToneDocument.linkToNext`: the last step joined by the next one made while it is still last; +12 −2, generic). Merge
Old in Page Settings asks `VectorInk.oldLayers` (pairs besides Vector Ink's and the one under them), not `pairs > 1`, so
the pairs made for Vector Ink stay hidden (`NotebookScreens`, +1 −1).

Vector Ink as a medium (same branch, the owner: "yes add it to the medium list", "in all brush studio locations"; spec
"Vector Ink as a medium"): `PartChoice.VECTOR` (Medium choice 8, no dials, seed-only); `VectorInk.is` reads the medium;
`VectorInk.tidy` sets aside what it can't draw sharp, run by `ModularBrush.normalize`; `BrushLab.chips`/`dials`/`handRows`
show nothing of those parts; `ModularVariations` varies its Size and pressure to size alone; `ResolvedBrush.of` draws it
in pixels as a plain Ink line (`VectorInk.drawnAs`); `ModularCode` reads the Basic tool's first code as the new one;
`VectorInk.Pen` sizes each point by its Ink line's pen width (`VectorLines` version 2: width shares). The web lab's
engine (newmods.js) knows `vector` (drawn as ink, never rolled or swapped to), planes.js puts it on the ink layer, Studio
leaves it out of its own rolls and the Mix grid, and `WebEngine.studioParts` marks the parts it sets aside as the app's.
`docs/brush-code.json` and `docs/brush-lab/app-features.json` written again from the enums.

The Lasso and a quicker eraser (branch `vector-ink-2`, spec's section of that name): `VectorLines` parts lines by a region
(`split`: a grid of an eraser's dabs, or of the Lasso's loop's edges), poses them (`posed`); `VectorInk.Cut` keeps its
dabs and cuts them together, `VectorInk.relay` draws a layer's pixels again from its lines tile by tile; `LassoPlanes`
lifts and lays the lines with the pixels; `ToneDocument.compositeToneKept`/`compositeToneWithout` take one more layer
to leave out; `DrawingPad` no longer refuses the Lasso, Paste or a carried piece on Vector Ink's pair and draws a
floating piece's lines sharp. `VectorInkBench` (test side, not a check) times the eraser on a busy page.

Never full (branch `vector-ink-full`, spec's section of that name): `VectorLines.thinned` (fewer points), `MOST_POINTS`
200,000, `Stroke.runs` (an outline passes by runs out of the area); `VectorInk.linedLayers`/`linedMask`/`sharpLayers`
(several Vector Ink layers, a full one gives way to a new one on top, `room`); `ToneDocument.visibleLayers`' and
`compositeToneKept`/`compositeToneWithout`'s last argument a set of layers as bits (was one layer, -1 none: now 0
none), as `ViewportBitmap.compose`'s, `DrawingPad.sharp()`'s and SharpView's `without`; `VectorInk.Cut` and
`LassoPlanes` over every Vector Ink layer.

| File | Change | Why |
|---|---|---|
| `PartChoice` | +6 | `VECTOR`, appended to Medium. |
| `ModularBrush` | +2 | `normalize` ends with `VectorInk.tidy`. |
| `ModularCode` | +2 | The Basic tool's first code reads as its new one. |
| `ResolvedBrush` | +2 | A Vector Ink brush resolves as its Ink line. |
| `BrushLab` | +12 −1 | `chips`, `dials` and `handRows` for Vector Ink. |
| `ModularVariations`, `ModularFeatures`, `ModularSizes`, `ModularWords` | small | Never swapped to, seed-only, on the ink layer, sample size, "Sharp at any zoom.". |

#### A stump's other half shown under the pen (2026-11-04, branch `smudge-live-2`)

The owner: a big Blur "doesn't seem to have any effect until i lift the pen". A stump's other places (`ModularStroke.others`:
Blend's pair wash, Blur's every other plane of the pair) are laid only at the finish (`blendOther`, a gesture binds one
plane), so the page held none of their smudge while the pen was down. Now the screen shows it, display only: the page,
Undo, Replay, Draw Together and the finish are as before. `ModularStroke.unwritten` (Scribulor's own) gives each pixel a
dab changed there its plane as smudged so far (`CarryPlane.tone`/`coverage`, what the finish composes) over the page's
other layers; `DrawingStroke.unwritten` passed through `SteadyHand`, `MirrorStroke`,
`FilledShape` and `QuickStroke`. A quick smudge shown under the pen renders at the usual priority (its helpers still quiet).

| File | Change | Why |
|---|---|---|
| `ToneDocument` | +27 | `compositeTonesShown`: the composite over a block with given planes standing in. Generic: any preview of planes not yet written. |
| `DrawingPad` | +80 −6 | Renders the stroke's unwritten area (`renderDirty`), shows it as the eraser's preview shows (`showErased`), under an effect's trail (`drawMirrors` reads it, `drawShow` takes the tones); draws it again from the page at lift. |

#### No effect trail (2026-11-04, branch `band-rule`)

The owner: "if the effect is instant no need to use the grey band". Every effect brush (`WildSample.reworks`) changes
the page under the pen as it goes (a stump's other half shown by `ModularStroke.unwritten`; Float's bath by its quick
show), so the effect trail is gone: `MirrorShow.trail` and its shade (`SHADE`, `shaded`), `HoverNib.trail`, the drawing
screen's trail, `AreaChecks`' trail checks and `TrailPictures`. An effect brush's hover ring is the eraser's
(`HoverNib.eraser` at its size). Upstream file touched: `DrawingPad` (−12, the trail; the ring's one line).

## Sharp dry brushes, the HB pencil pilot (2026-11-04, branch `pencil-pilot`): upstream files touched

The owner: "start the pencil pilot"; spec `docs/superpowers/specs/2026-11-04-vector-dry-brushes-design.md` ("The pencil
pilot as built"). A kept stroke is its Replay entry, drawn again finer when zoomed; the page's pixels stay the truth.
Engine, pure, Scribulor's own and separable: `FineGrid` (a raster finer than the page over part of it), `SharpStrokes`
(which strokes; one drawn again from its entry onto `SharpStrokes.GridPage`, a `ModularCompose.Page`) and `SharpView`
(tiles, the match, a worker thread at the lowest priority on the quiet cores). In the engine: `FloatTiles` can hold a
grid's points; `LineStamper.stamp` lays its page-worked dabs at a grid's points (`toGrid`) and passes over steps that
can't reach it (`Meta.cull`); `StrokePaper.on(grid)` reads the grain between pixels; the stick grain goes through the
paper (`StrokePaper.stick`); `QuickStroke.laidAs` and `SteadyHand.steadies` are pulled out for its rule; `StrokeLog`
entries keep a plane and zoom (`Recorder.keeps`, `kept`, `encodeSharp`, `withSharp`). At one point a pixel every number
is as before: each of the 140 Basic tools' strokes hashed the same before and after; `SharpChecks` proves a kept
stroke drawn again lays the plane's bytes. A host asks `SharpView.want` for what its view shows, hears of each tile
ready, and lays them (`ViewportBitmap`). `SharpBench` and `SharpPictures` (test side, not checks) time the tiles and
draw 400% and 800%. MonoPaint would take the grid as one generic change to its stampers.

| File | Change | Why |
|---|---|---|
| `DrawingPad` | +49 −2 | `feed` gives a kept stroke its samples as the log keeps them; the stroke's zoom and plane to its recorder; `sharpTiles` (`SharpView`), `sharpStep`, `wantSharp` (after what is under way, `wantSharpSoon`), `sharpReady` (the tile's area queued, the usual update, no flash); tiles dropped where the page renders again, all of them at `renderAll`. |
| `ViewportBitmap` | +34 −10 | `update(…, view, k)`: plain greys where tiles are ready, the tiles (`tiles`: only where the page's pixel still shows what the tile was drawn over), Vector Ink's lines, then the dots. |
| `DocumentCodec` | +17 −2 | The `SHRP` section beside `RPLY`, written only when the log holds kept strokes; read whatever the order, applied to the log at the end; an older reader skips it. |
| `ToneDocument` | +4 | `selectedAddress` (the plane a stroke drew on), `addressed`. |
| `InkWash` | +2 | The pen's stroke of a kept brush is recorded to be fed as kept. |

## Sharp dry brushes, step 1: every qualifying dry brush, and the browser (2026-11-04, branch `sharp-brushes`): upstream files touched

Spec "Step 1 as built". A kept stroke is now drawn again by the page's own stroke on a grid (`ModularStroke.onGrid`:
its buffers a `FineGrid`'s, composed onto `SharpStrokes.GridPage`), so every tip and body whose stamper lays on a grid
is kept (`SharpStrokes.takes`: the spec's rule and `drawn`). Each stamper below lays its page-worked marks at a grid's
points when its buffer has one (`FloatTiles.grid`) and is unchanged without: at one point a pixel each Basic tool's
strokes hash the same before and after; `SharpChecks.families` checks each kept tool. The browser's engine keeps and
draws them as the Manta does, on its one thread (`WebEngine.sharpTile`, `sharpArea`; `engine.js` `page.sharp`,
`E.staysSharp`); the browser app and Brush Studio show them. `SharpTools` (test side, not a check) reports each tool.
For MonoPaint: the grid stays one generic change to its stampers, the stroke's own code laying on it.

| File | Change | Why |
|---|---|---|
| `ModularStroke` | +40 −10 | `onGrid`, `cull`, `widest`; its buffers and rectangles in the grid's points (`bw`, `bh`, `boxOf`), `finish` on a grid. |
| `LineStamper` | +15 −5 | Its clip in the buffer's points; a drop (splatter) and a crayon's maps on a grid. |
| `Placement`, `BodyStamper` | +12 −3 | Lines and dabs laid at a grid's points. |
| `StippleStamper`, `HatchStamper`, `SpiroStamper`, `StampStamper`, `FanBrush`, `RulingPen`, `TwigStick` | small | The same for dots, hatching, segments, prints, hairs, blades and ends. |
| `ModularCompose` | +3 −3 | Charcoal's stick grain and a crayon's maps read through the paper. |
| `WebEngine` (web) | +60 | `staysSharp`, `sharpStep`, `sharpTile`, `sharpArea`; strokes kept as the Manta's. |

## Sharp dry brushes, the Stamp (2026-11-04, branch `sharp-bristle-stamp`): upstream files touched

Spec "The Stamp as built". A kept Stamp stroke's entry keeps the ink it started with (`StrokeLog.Entry.ink`, the file's
`SHRP` version 2, written only when one isn't 1), and drawn again starts with it. The kept clock starts at the first
sample's own time (a stroke reads only the times between). At one point a pixel each Basic tool's strokes hash the
same before and after; `SharpChecks.stamps` checks the Stamp.

| File | Change | Why |
|---|---|---|
| `StampStamper` | +9 −3 | `startLoad` (the rested rule, pulled out), `Ink.kept`. |
| `ModularStroke` | +5 −3 | `inkedBy` (pulled out), `stampInk(load)` for a stroke on a grid. |
| `InkWash` | +1 −1 | A kept stroke's recorder through `SharpStrokes.keep`. |
| `WebEngine` (web) | +2 −2 | The same, the browser's and Brush Studio's. |

## Sharp dry brushes, the Bristle brush (2026-11-04, branch `sharp-bristle-stamp`): upstream files touched

Spec "The Bristle brush as built". At one point a pixel each Basic tool's strokes hash the same before and after;
`SharpChecks.bristles` checks it.

| File | Change | Why |
|---|---|---|
| `BristleSwarm` | +11 −6 | The swarm's trail field the page's on a grid; each hair's dab laid at the grid's points, its lanes as wide on the page. |

## Export Page's PNG sizes, High Res drawn sharp (2026-11-05, branches `png-sizes`, `png-sizes-2`): upstream files touched

The owner's three: Export Page's PNG opens a menu of **Social** (1080 on the short side), **Page** (the copy as
before) and **High Res** (2×, sharp), each saved at once. Spec section 8. `PngRender` (Scribulor's own, pure): Social's
area shrink of the copy's greys; High Res drawn a tile row at a time through `SharpView` (`drawRow`, and `over`, the
screen's tile laying moved out of `ViewportBitmap`) and `VectorLines.over`, deflated as it goes (`PngRender.Png`, 8-bit
grey), past `ToneDocument.validSize`. `PageExport.CHOICES` and the browser's `as=k` unchanged (a guest's PNG at Page).
Upstream's Export PNG window (`PngExport`), which nothing opened, is gone with its folder picker, prefs and dithered
export. `PngRenderChecks`, `ExportSizeChecks`.

| File | Change | Why |
|---|---|---|
| `PngExport` | −375 | Unreachable (`exportPng` had no caller): gone. `SUPERNOTE_FOLDER` moved to `AutoExport`. |
| `PaintActivity` | +23 −30 | `pngMenu` after Export Page's PNG, `exportPage(…, size)`; `exportPng`, `pickFolder` and its result gone. |
| `PaintPreferences` | −9 | The export folder, dithered and size prefs (only `PngExport` read them). |
| `ViewportBitmap` | +1 −39 | `dithered` gone (only `PngExport` used it); `tiles` moved to `SharpView.over`. |
| `ExportSize` | +20 −26 | Social, Page, High Res, each's menu words; the Supernote-screen and custom sizes gone. |
| `PageExport`, `AutoExport` | +11 −2, +6 −4 | A PNG's size as a parameter (the old signatures: Page). |
| `NotebookExport` | +17 −13 | `png(greys, w, h, crc, dpi)`, `greys(composite, …)`, `about` (pHYs, tEXt), `chunk` and `SIGNATURE` shared. |
| `PagePicture` | +13 −2 | `Composite.of(page, guides, without)`: the copy's composite without Vector Ink's sharp layers. |

2026-11-05, branch `pdf-sharp-2x`: the PDFs' picture of a page with kept dry strokes is High Res's (`PngRender.high(page,
lines, …, vectors)`, its Vector Ink layers hidden first and drawn as vectors, `PngRender.sharp`), 600 dpi, placed at the
page's size (`NotebookExport.Png.dpi` from pHYs, `pageWidth`; `NotebookPdf.write` sizes the page, the words and the
lines' form in page pixels). Scribulor's own files only (`NotebookExport`, `NotebookPdf`, `PngRender`); no upstream file.

2026-11-05, branch `export-sizes`: Layers at the PNG's three sizes, and the browser's (spec section 8). Engine side, all
Scribulor's own and pure: `PageExport` (`write(OutputStream, …)` streams every copy, the Manta's into the file; `psd(…,
size)`, `Sharp` for High Res's layers, `sized`, `sizeWords`, `sized(choice)`), `PsdWriter` (`Channel` packed as rows
come, `write` streams the packed layers and the merged channel), `PngRender` (`shrunk` row by row, `high` into
`Strips`), `SharpView` (`holds`, `drawn` over a page rectangle), `VectorLines.over` (coverage kept too),
`PagePicture.Composite.printed(page, from, to, without)`. Browser side: `WebBooks.pageCopy(place, choice, size, today)`
and `sizeWords`; `LiveBooks`' `books/page` takes `size=` (Page without it).

| File | Change | Why |
|---|---|---|
| `PaintActivity` | +9 −9 | `pngMenu` became `sizeMenu(anchor, place, choice)`, for the PNG and Layers. |
| `PagePicture` | +4 −2 | `Composite.printed(page, from, to, without)`: a Layers layer without Vector Ink's sharp layers. |

## Tidy: MonoPaint's unused code (2026-11-05, branch `tidy-upstream`): upstream files touched

The owner: "just to tidy things up". No behaviour change. A reachability pass (from the manifest's activities, the web
engine's and the TeaVM pilots' sources, and the desktop checks) found MonoPaint's toolbar, header and colour bar built
and kept GONE, and DrawingPad's MonoPaint pen branch unreachable (`inkWash` is set before the pad exists). Deleted: the
22 classes named in section 2's "Upstream, deleted" under this date, `selection_preview.c`, and 24 drawables only they
used (MonoPaint's tool icons, masking fluid and peel, the header's eraser and page-menu icons; `ic_import`,
`ic_turn_left`, `ic_turn_right` unused already). Device suites that drove only those controls are deleted
(`ToolbarChecks`, `ToolPickerChecks`, `ZoomButtonChecks`, `LayerUiChecks`, `AirbrushUiChecks`, `EraseUiChecks`,
`GradientUiChecks`, `ColorBarChecks`, `ControlFeedbackChecks`, `PaintChecks`, `EdgeBarChecks`, `FullscreenChecks`;
`layersOnly`, `paintOnly` and `brushOnly` report retired), `ShapeUiChecks` keeps only the pages and Nomad suites'
harness, and the other suites lose their retired checks of those controls. Desktop: `BristleChecks`, `EdgeSwipeChecks`
(both only in `test_document.sh`).

| File | Change | Why |
|---|---|---|
| `PaintActivity` | +38 −565 | The header, colour bar, page panels and overview, the toolbar's and edge bars' hooks, the eyedropper's and gradient's state gone; the rotate prompt's button built on its own (`rotateButton()`); `saveToolState` still saves the colour and presets. |
| `DrawingPad` | +52 −434 | MonoPaint's pen branch (its tools through `ToolStrokes`), its lasso, flood fill, gradients, eyedropper picking and wet canvas (`dryWet`, `wet`, `advanceWet`) gone; the Dropper keeps the eyedropper's gesture. |
| `SettingsForm` | +1 −183 | Only `style`, `HINT` and `CLOSE` left: nothing builds a form any more. |
| `Ui` | −36 | `selectedFrame`, `emptyDashes`, `setDimmed`, `onProgress` (the swatch's, the header's, the forms'). |
| `PageReplay`, `LiveProbe` | −1 each | No `dryWet` to call. |
| `CMakeLists.txt` | −1 file | `selection_preview.c` out of the native library. |

Left in place, as the checks or the preferences still use them: `ToolStroke`, `ToneDabs`, `PressureStroke`,
`FloodFill`, `WatercolorWash`, `PaintState`, `ToolLibrary`, `PageOverview` (its thumbnail, `DrawingBrowser`'s);
`ToolButton`'s `alwaysDot`, `markerBelow` and `headerIcon` and `ControlHost.toolbarTurn` (now never set, kept to leave
`ToolButton` and `ControlHost` as they were). Generic: none.

### The eraser takes what is under it on every pair (2026-11-05, branch `erase-all`): upstream files touched

`EraseBothStroke` (Scribulor's own) erases every shown layer at lift, not only the selected pair's two; its preview
(`erasedTones`) composites every shown layer as the eraser leaves it. A hidden layer keeps its marks; Vector Ink's
layers are cut and drawn again from their lines as before (`VectorInk.Cut`), not erased pixel by pixel. `VectorInk.eraserPair`
(erase-hb) is removed: with every shown pair erased at lift it had nothing left to do.

| File | Change | Why |
|---|---|---|
| `DrawingPad` | comments only | The eraser's preview shows the page as the eraser leaves it, not what lies beneath the selected pair. |

Generic: none. Engine boundary unchanged (`ToneDocument` untouched).

### Send: a page's copies to a phone on the Wi-Fi (2026-11-05, branch `send-phone`)

The owner: "a lightweight quick share option that doesn't require server space ... export a page or a notebook to a
computer or a phone easily". Export Page's menu's last row, **Send**.
- **Upstream files touched:** `PaintActivity` (+14: `exportMenu`'s Send row, `sendPage`). No engine change.
- **Pure:** `LiveSend` (new): a 128-bit token (base64url, 22 letters), ten minutes (`LIFE_MS`), the page's copies as
  Export Page offers them (`PageExport.bytes` at each `ExportSize`) and the notebook's PDF (`NotebookExport.pdf`), from
  one snapshot; `serve` answers `GET /get/<token>` (a plain page, superdood.org's look, no script) and `/get/<token>/<n>`
  (made then, one at a time, streamed chunked: a maker failing first is a 500, cut off after has no last chunk).
  `LiveHost.send` (volatile): checked before the key, any other path as before (the key's 403).
- **Android:** `LiveShare.send`: the Draw Together host when it runs on the Wi-Fi, else a `LiveHost` of its own with no
  web app and no notebooks; its window (`ways`, split out of Draw Together's: the pickup's code and the QR code)
  closes at ten minutes (wall time) and closing it ends the link and stops that host; `startHost` gives a host started
  again (back from sleep) the open Send's files; `off` closes it with the drawing screen.
- **Server:** `server.py`'s `TO_RE` takes a private address's `/get/` and 22 letters; the Found page says "Opening the files".
- **Checks:** `LiveSendChecks` (each file the writers' bytes; wrong, short, late tokens, other numbers and paths 404; the
  host's other routes still keyed; failures; escaping); `test_server.py` (Send's link taken, its variants refused, Found's words).
- **Open:** off the Wi-Fi (a phone on cell data) needs the file relayed (superdood.org's relay, sealed with a key in the
  link's #, size-capped); not done. No notebook-level Send (Export Now is a button, not a menu). Untried on the Manta.
### A quicker eraser preview (2026-11-05, branch `erase-speed`): upstream files touched

The owner: "a bit sluggish with a long erase stroke". The Manta's stroke timing put it in the preview's render: about
2.3 µs a pixel against HB pencil's 0.05, renders up to 0.66 s. `ToneDocument.compositeToneKept` went over every place of
the page's stack per pixel (every pair since erase-all), looking each layer up again. `compositeTonesKept` does a row:
the shown layers gathered once, then layer by layer along the row, passing by a pixel a layer holds nothing at (which
leaves the result as it was). The same tones (`EraseBench`'s hash of every frame's preview, before and after).
`EraseBench` (test side, not a check): 0.17 to 0.02 µs a pixel on the desktop. The big single renders are one box round
an event's samples, every pixel the stroke has been over inside it shown again: now about a ninth of the time.

| File | Change | Why |
|---|---|---|
| `ToneDocument` | +29 −8 | `compositeTonesKept`, a row at a time; `compositeToneKept` is one pixel of it. |

Generic: none. Engine boundary unchanged.

## Sharp dry brushes, Screentone (2026-11-05, branch `sharp-more`): upstream files touched

Spec "Screentone as built". Screentone and screened prints are kept sharp; `PrintScreen.ink` (Scribulor's own) takes a
page position, its analytic screens read between pixels (`between`). At one point a pixel each Basic tool's strokes
hash the same before and after; `SharpChecks.screentone` checks the dots.

| File | Change | Why |
|---|---|---|
| `ModularCompose` | +2 −1 | The screen asked at the point's page position (`StrokePaper.indexX`, `pageHeight`): itself off a grid. |

## Sharp dry brushes, Shapes (2026-11-05, branch `sharp-more`): upstream files touched

Spec "Shapes as built". A shape's log entry keeps its plane and is drawn again at the grid's points
(`SharpStrokes.lay`; `GridPage` keeps coverage once a shape lays opaque grey; `SharpView.drawn` returns the
`GridPage`, `holds` compares raw tone and coverage). `StrokeLog`'s `SHRP` version 3 when a shape is kept. At one point
a pixel nothing changes: 3,000 random shapes' spans hash the same before and after; `SharpChecks.shapes`.

| File | Change | Why |
|---|---|---|
| `ShapeGeometry` | +13 −4 | `on(grid, …)`: the same shape at a fine grid's points (scaled, shifted, the corner stopped at the page's edge); on the page, unchanged. |
| `ToneDocument` | +1 −1 | `rawTone` package-visible, for the match's raw tone. |
| `DrawingPad` | +2 −1 | A shape's log entry keeps its plane (`sharp(selectedAddress(), 1, 1)`). |
| `WebEngine` (web) | +2 −1 | The same, the browser's `shapeEnd`. |

Text boxes and magnets stay pixels when zoomed (Android-drawn letters at page pixels; spec "Shapes as built").

## Prints darken only again (2026-11-06, branch `texture-no-lighten`): upstream files touched

Undoes 212af5da's "print over prints replaces them" (and its twins e6b3bc5d, 4649308f's padding): a Print Shading or
Screentone stroke darkens only, as before 2026-10-29. Textures as Paint (same commit) stay. `ScreenChecks` checks a
light pass over a hard one never lightens it.

| File | Change | Why |
|---|---|---|
| `ModularCompose` | −35 | `replaces`, `edge`, `nowLine`/`nowBody` gone: prints use `PrintScreen.printed` (the darker). |
| `ModularStroke` | +2 −3 | No smear tones for prints. |
| `ResolvedBrush` | +3 −8 | `replaces()` gone; `lightOver` for a screen: only an opaque print. |
| `SharpView`, `SharpStrokes` | −15 | No one-pixel padding for a print's edge; `GridPage.smearTones` throws again. |
| `WebEngine` (web), `WebParity` | +1 −1 each | Parity scene marks on the brush's own plane. |

Generic: none. Engine boundary unchanged.
## Wet opaque paint's spread covers (2026-11-06, branch `oil-spread-cover`): upstream files touched

The owner: "for the oil.. yes, spread cover like the stroke". A wet opaque stroke covers in full (`paintTone`), but its
spread was written with `setTones`, so over blank paper it covered only by its darkness and marks beneath showed through
a pale spread at full darkness. `WetPaintChecks.spreadCovers` checks it, and that a wash's coverage is as before.

| File | Change | Why |
|---|---|---|
| `WetWatercolor` | +22 −1 | Hook marked "Inkwash": with `paintOnly`, `paintMask`'s opaque paint records the most pigment laid (`coverInk`); a frame's tones are then written with `coverTones`, each non-water pixel covering by its pigment's share of `coverInk` (at least what it covers now); water pixels keep their coverage, as `setTones`. Upstream (no `paintOnly`) and a canvas with no opaque paint write as before. Generic: any opaque wet canvas could cover this way. |
## Favourites (2026-11-06, branch `favourites`): upstream files touched

A page's star in Notebook Explorer copies its stack to the end of the Favourites notebook (`NotebookStrip.FAVOURITES`);
the page and its copy carry the same int mark, a new options record `PFAV` (and recovery section `TSRA`), passed by
older readers. Superdood's own: `NotebookStrip` (the name, unstar), `NotebookScreens` (the star, Settings' word),
`BookOptions`, `RecoveryCodec`, `PaintActivity.star`.

| File | Change | Why |
|---|---|---|
| `DrawingBook` | +~55 −10 | Each page's mark (`faves`, beside `surfaces`, moving with its page through add, delete, move, layers, `take`/`restore`); `copyStack` and `append` (a stack into another book, its bytes its own); `Taken.fave`; the snapshot's `pageFaves`. |
| `BookCodec` | +2 −1 | Writes and reads the marks (`Covers.pageFaves`). |
| `DocumentStore` | +40 −10 | `rewrite` takes a change that may delete the notebook instead; `favourite` (made at the first star) and `unfavourite`, on the IO queue. |

## Draw Together on three layers (2026-11-06, branch `live-layers`): upstream files touched

A live change says the layer it was drawn on (`LiveChange.layer`, flags bits 5 and 6, 0 for layer 2, so a layer 2 change
is byte for byte an older app's); the receiver lays it there (`InkWashLayers.drawOn`: a one-pair page made three first by
the first switch's `make`, every layer shown meanwhile, its own layer picked again after). Everything live compares and
sends the page as every screen shares it (`ToneDocument.liveTones`: each layer Full), so a layer's step is never sent.
A lightening change erases the layer drawn on first, the others only where that is not enough (`PictureImport.lay`).
The whole page for a guest joining goes a layer and a kind at a time (`LiveChange.whole`); `LiveChange.since` cuts a
change to the box round what differs. Browser: `WebEngine.lay`/`picture` take the layer, `layer`, `layerSaid`,
`layerAt`, `pickLayer`; live.html's layer button (res/drawable/layer_*.xml, relay.py serves them). Checks:
`LiveChangeChecks.layers`, live-check.cjs's layer button and layer 3 stroke.

| File | Change | Why |
|---|---|---|
| `ToneDocument` | +25 −4 | `compositeTones` gains a private core with `liveTones` (every layer Full; pairs above one left out, its kinds at steps); `withEveryLayer` (a hidden layer takes a guest's change, no step); `stepLayer` (the layer an Undo step drew on). |
| `PictureImport` | +35 −15 | `lay`'s `lighter`: `liveTones`, the layer drawn on erased first, the others where still too dark; `erase`, `liveTones` helpers. |

Generic: none (live only). Engine boundary unchanged. Shortcut: the greys are all layers together, so a mark laid under
a darker one of a higher layer reaches others only on joining (`LiveChange.whole`), not live.

## Vector Ink on the layer in use (2026-11-06, branch `vector-layers`): upstream files touched

The owner: "Vector Ink follows the chosen layer". Vector Ink draws on the layer in use's ink half, beside the other
pens' marks; its pixels go on a kind plane of their own, `ToneDocument.PLANE_LINES` (Pen to the eraser, index 4, so
`PLANES` is 5; stacked right over the Pen plane, `STACK_PLANES`, under opaque paint), drawn again from the lines
(`VectorInk.relay`). Whatever left out "Vector Ink's layers" now leaves out that plane alone (`ToneDocument.linesPlace`).
The zoomed view and High Res draw each layer's lines in their place in the page's order (`VectorInk.Stacked`: where a
place over the lines holds a mark, the target pixel is composed again from the places, the lines in theirs; elsewhere
the lines over the target as before). No pair is added and nothing moved under (`room`, `under`, `ADD`, `linkToNext`
gone). File: `PLNS` keeps four planes, the Pen plane holding Vector Ink's pixels over the pen's (every version reads it
as drawn); a new section `LPEN` keeps the two apart where a lined layer holds both (an older reader passes it by). A
lined layer without it reads its Pen plane as Vector Ink's alone: old pages open unchanged.

| File | Change | Why |
|---|---|---|
| `ToneDocument` | +46 −42 | `PLANE_LINES`, `STACK_PLANES`, `slotPlane`, `planeSlot`, `stackPlane`, `linesPlace`; `stacked`, `sheets`, `sinkUnder` in the stack's order; `visibleLayers(…, without, at)`; `compositeToneWithout`/`Kept` lose `also`; `linkToNext` gone. |
| `DocumentCodec` | +127 −24 | `filePlanes` (Pen with Vector Ink's over it), `LPEN` written and read, `linePlanes` at the end tag. |
| `viewport_bitmap.c` | +1 −1 | `MAX_COMPOSED` for six places a layer. |
| `ViewportBitmap`, `DrawingPad` | +9 −7, +9 −5 | `update` takes `VectorInk.Stacked`; `sharpLines` makes it (tracing paper above stays under the lines). |
| `EraseBothStroke` | +5 −8 | Erases every plane of a shown layer, Vector Ink's too; the cut draws what is left again. |
| `LassoPlanes` | +37 −63 | Lifts the selected pair's lines and pixels together (Vector Ink's plane a role for the look only); lays them on the pair it is put down on. |
| `PictureImport`, `LiveProbe`, `StrokeStart`, `PaintActivity` | small | Guest and own inks no longer moved under Vector Ink; the live cut per layer. |
| `ModularStroke` | +2 −1 | A blur or reprint leaves Vector Ink's plane (its lines draw it). |
| `PagePicture`, `PageExport`, `PngRender`, `NotebookExport` | small | `without` the plane; PSD's "Vector Ink" layer; the PDF's vector rule by the plane's place. |

Generic: none. Engine boundary unchanged (pure Java, the browser's the same code). Check against the version before
(`1f0d16e8`, `OlderLayersCheck`): both read the other's pages as drawn.

## The eraser by layer (2026-11-08)

The owner: "rename them just layers 1 etc instead of pen paint etc". The eraser's hold offers Everything, Layer 1, Layer 2,
Layer 3 (`EraseKind.CHOICES`, `TARGETS`); Pen, Paint and Dry Media stay Show Kinds' columns (`EraseKind.LABELS`). A target
is the old kinds int with the layer above its bits (`EraseKind.layerOf`), so every path that carried `eraseKinds` (the
pen, a finger, mirrors, the browser's `pen`) carries it unchanged; Show Kinds still limits its kinds (`EraseKind.takes`
keeps the layer). `EraseBothStroke` erases engine layers `from` to `to`, the layer's pair (none where the page hasn't made
it), every kind there; the fluid and a cover's design are Everything's alone. The stroke log keeps the kinds only (a
layer's eraser replays as every layer's, as Replay doesn't follow layers), so an older reader reads every log.

| File | Change | Why |
|---|---|---|
| `ToneDocument` | +1 −1 | `compositeTonesKept` previews a cover's rub only when every layer is erased. |
| `EraseBothStroke` | +14 −7 | `from`/`to`, `everything`; the live layer the target's pair's ink. |
| `VectorInk` | +4 −2 | `Cut.of` cuts the lines of the layers erased. |
| `EraseKind`, `InkWash`, `InkWashBar`, `PaintActivity`, `ToolKits`, `WebEngine` | small | The choices, the log's kinds, the menu and mark, a hidden target layer refused, kits' "Eraser Layer 1", the browser's menu. |

Generic: none. Engine boundary unchanged.

## Lasso to Superdood, the Supernote plugin (2026-11-08)

A Supernote NOTE plugin (`supernote-plugin/`, React Native 0.79.2 from Ratta's template, `sn-plugin-lib` 0.2.3) puts a
**Superdood** entry in the lasso toolbar's More menu. It reads the lassoed pen strokes (points in page px, pressures
0 to 4096; no times, no tilt), and its native module (`SuperdoodModule`, run by PluginHost as the system) calls the
app's `LassoProvider` (`content://ing.ell.mybaddrawings.lasso`, method `draw`; system, shell and the app only). The app
draws the strokes with the toolbar's selected brush at its size and grey on a blank page (`LassoRender`: the web
engine's stroke path, samples untimed), crops the marks and returns a PNG, paper clear. The plugin inserts it
(`insertImage` puts it at the page's middle) and sets its lasso box onto the strokes. The strokes stay.

Measured on the Manta: 3 strokes with Slow bloom drawn in about 2.3 s, 2.9 s from tap to picture. Building the plugin:
`cd supernote-plugin && npm install && JAVA_HOME=… ANDROID_HOME=~/Library/Android/sdk bash buildPlugin.sh`, then put
`build/outputs/LassoSuperdood.snplg` in MyStyle and Settings → Apps → Plugins → Select Plugin Package.

| File | Change | Why |
|---|---|---|
| `WatercolorSession` | +9 | `dryAll`: settle and run fading ink out, the web engine's Dry, shared by `LassoRender` and `WebParity`. |
| `InkWash` | +0 −0 | `parse` package-private: the provider reads the selected tool. |
| `WebEngine` | −3 | Dry calls `dryAll`. |
| `LassoRender`, `LassoProvider`, manifest | new | The drawing and the app's entry for the plugin. |

Generic: `dryAll`. Engine boundary unchanged (the provider is Android, `LassoRender` pure).

**Superdood fill (2026-11-09, the owner: "like a 'highlighter fill'").** A second More entry, method `fill`: the
lasso's inside (the lasso's own path, pen type 4; a plugin-made lasso has none, so the strokes' box, 30 px round)
scrubbed with the brush in rows, back and forth, clipped to the lasso, and the lassoed strokes left clear (half their
width, thickness/100 px, and 2 more) so the note's writing shows through. A lasso over 100,000 px is painted smaller
and enlarged (`LassoRender.WORK`): a Slow wash's drying is the cost and can't be cut short (its blooms form in the last
ticks). Measured on the Manta, Slow bloom at 6 px, a 960 × 820 px lasso: 36 s at full size, 10.4 s painted a third the
size. The picture now goes over as a file (`LassoProvider.openFile`), not in the call's Bundle (its 1 MB). A permission
dialog doesn't show over a lasso, so a refused insert says to allow it in Settings.

## Read Handwriting (2026-11-09)

An opt-in System Settings row that reads the pen writing on every notebook page on the tablet (Google's ML Kit Digital
Ink) so the shelf's Search can find it. Spec: `docs/superpowers/specs/2026-11-09-read-handwriting-design.md`. Reading is
app code; **the engine is untouched** and the boundary unchanged.

**Dependency:** `com.google.mlkit:digital-ink-recognition:18.1.0`, the app's first Maven dependency (so
`android.useAndroidX=true`). The release APK grew from about 12.5 to 19.4 MB. ML Kit pulls in WorkManager, so the
manifest gains ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED and FOREGROUND_SERVICE. Its auto-start provider is removed
from the manifest: nothing of ML Kit starts, and nothing downloads, until Read Handwriting is switched on.

| File | Change | Why |
|---|---|---|
| `DocumentCodec` | +`log`, `PageLog` | A page's stroke log, its pixels skipped. The generic change. |
| `PageWriting`, `WritingLines`, `Transcript` | new, pure | The strokes still on a page (Lasso moves applied, erased and wide strokes left out); strokes to lines; a notebook's lines by page CRC, the file, search matching. Desktop-checked (`PageWritingChecks`, `WritingLinesChecks`, `TranscriptChecks`). |
| `HandwritingReader` | new | The only class that knows ML Kit. Never downloads on its own; switching off cancels a stored download job and deletes the model. |
| `HandwritingQueue` | new | One lowest-priority thread per activity. Reads changed pages (by CRC); urgent work (search, renames, deletes) goes first and reading gives way between pages; a cut-short download resumes at the next start. |
| `DocumentStore.notebookFile` | new | Read-only file access, safe off the save thread. |
| `PaintActivity`, `PaintPreferences` | hooks | The queue made, fed from `saved`, delete and rename; `readHandwriting`. |
| `SystemSettings`, `SystemSettingsPanel` | row | "Read Handwriting", Off by default; first-time words, Turn On / Not Now, not cancelable; "Download failed". |
| `ShelfSearch`, `NotebookScreens` | new, changed | Search on the shelf: results paged, "Nothing found", "<n> pages unread"; a find opens by its page's CRC with its stack's matching lines boxed until the next touch. `pageButtons` made static and shared. |
| `LabUi.SearchField` | moved | The Tools page library's search field (from `ToolsActivity`), shared with the shelf. |
| `TracingPaper.stacks` | extracted | A file's stacks in one place; Search needs it too. |
| `NotebookPdf`, `NotebookExport`, `AutoExport` | changed | The notebook PDF's invisible text gains each read line's best guess on its box (`Sheet.hand`, `Documents.transcript`, read by `AutoExport` only while the pref is on): Auto Export and Export Now. Not in Live sends (`LiveBooks`, `LiveSend`) or Export Page's single-page PDF, which don't reach the transcript. |

An older build reading a PDF state record with the new ninth field takes it as unreadable (a fresh PDF, or "Name 2.pdf"; nothing is lost).

Generic: `DocumentCodec.log`.

## Second Ink's brush switch (2026-11-09, branch `second-ink-notebook`)

- `PartDial.SECOND` (key `second`, "Second Ink", Off/On): last of Ink's, Wash's, Cover's, Charcoal's and Graphite's dials (after Dissolves),
  last in `ModularCode.ADDED` (written only when on, so every older code is byte for byte; a version before it reads an On code as the plain
  brush with "Newer version", pinned in `scripts/test_inkwash.sh` against c7ebd5bb). Never rolled (`ModularStyle`). `ResolvedBrush.second(b)`
  is the switch; nothing in the engine reads it yet. `LabLayout.shows`: on the Nomad a spare row after Dissolves. The web lab (`newmods.js`) reads and writes it.

## Second Ink in the copies (2026-11-10, branch `second-ink-notebook`): upstream files touched

Plan Task 6. The screen, thumbnails, the live page and Draw Together never read `Layer.ink`; only a copy does.

- `PagePicture.Composite.row(y, out, share, from)`: beside each pixel's tone, second ink's share of its darkness (a mark's
  darkness times its `Layer.ink`, laid over what is under as its tone is; text boxes' letters over it as black). `inked()`:
  whether a shown mark holds any. Vector Ink's planes count as first ink (the PDF draws its lines as grey vectors).
- `NotebookExport`: `flatten(layers, hidden, ink)` and `cover(…, ink)` set `Snapshot.ink` (the notebook's, `NotebookOptions.ink`).
  `inks`/`greys(…, inks)` fill the shares, under the magnets as much as shows (the shares ride in the stamped picture's red).
  A page with some is an 8-bit RGB PNG (colour type 2), each channel `grey + ink × share × (255 − grey) / 255²`
  (`channel`, the grey calibrated first); `read` takes it (`Png.colour`) and `imageDictionary` writes `/DeviceRGB`,
  `/Colors 3`. A page without any takes the grey paths untouched: byte for byte (`SecondInkCopyChecks.BEFORE`, from 583462fc).
- The key: an RGB PNG carries its page's key mixed with its ink (`inkKey`; the first ink's is the page's own), so
  `update` finds it only under the ink it was drawn in (`Png.key`) and another ink draws those pages again. The PDF's
  record keeps the plain keys (a page's days don't move); the ink its pictures are in goes in `.lines.txt`, a last line
  "ink 1", unless it is the first (so every record there is stays as it was): another ink writes the PDF again, the next
  time too if its writing failed. `inked()` counts only marks that can show (their layer shown, at some opacity).
- `PngRender`: Social shrinks the shares weighed by darkness; High Res carries each pixel's share beside its grey
  (`Strips`: bits 8 to 15) into an RGB `Png`. shortcut: a Vector Ink line over second ink counts as black ink, and a
  sharp dry stroke takes its page pixel's share. The Layers copy (`PsdWriter`) reads the grey alone.
- Fridge: its PNG is `PageExport.bytes`, so it follows; the server (`png_size`) doesn't look at the colour type.

| File | Lines | Why |
|---|---|---|
| `ToneDocument` | +2 | `Snapshot.ink`: the notebook's ink for a copy of the page; the page keeps none. |

Checks: `SecondInkCopyChecks`.

## The app turns by itself (2026-11-10)

The owner: "strange hangover from the rotate button: it's lingering on the screen and affecting pen marks .. can we
make rotation happen without the button?", then "we can make the zoom lock a dual use lock? since it's already on the
screen". MonoPaint's Rotate button over the page (and the shake that recalled it) is gone; the app turns once the
tablet is held a new way. **The engine is untouched.**

| File | Change | Why |
|---|---|---|
| `RotationPrompt` (upstream, now modified) | rewritten | No button: the sensor's held angle (`RotationSuggestion`, untouched) turns the app (`PaintActivity.requestQuarter`). The turn waits while a menu or panel is open, a page loads, fingers pinch, or the pen was at the screen within 1.5 s (`DrawingPad.penWithin`). Never while the padlock is closed (`navigationLocked`); opening it looks again. |
| `PaintActivity` | hooks removed | `rotateButton`, its builder, `pageAction`, the tap-outside dismiss; `toggleNavigationLock` asks for another look when unlocking. |
| `PanelPreviewLayout` | −rotation hint | The button's place in the simulated panel. |
| `DrawingPad` | `penWithin` | One reader of `penSeen` for the turn's wait; the page-turn snapshot no longer asks about the button. |
| `PinchPan.describe` | words | The padlock said: "Zoom 200%, zoom and rotation locked". |
| `NotebookScreens.shelf` | gear | System Settings from the shelf's bar (`PaintActivity.systemSettings`, the menu's row's own popup). |

**Checks:** `RotationSuggestionChecks`, `PinchPanChecks` (desktop). `OrientationChecks` rewritten for the turn by itself
and the lock (device; compiled, not run here); `RotationPromptChecks` deleted with the prompt. Generic: none.

## Play menu lag (2026-11-12)

A Reddit user: Kaleidoscope "worked great, but the others in that tab lagged pretty significantly". Measured on the
Manta with `simpleperf` and the stroke timing log, with a simulated pen in a scratch notebook. **The engine is
untouched.**

| File | Change | Why |
|---|---|---|
| `ToneDocument` (upstream) | +19 `compositeTonesAt` | `compositeTone` at a list of page indices, the layers looked up once: Real Mirror read each reflected pixel with `compositeTone`, walking the layer list per pixel (62% of a stroke's time; pen lag 176 ms median, 1.4 s worst; after: 6 and 8 ms, as Copy Strokes). Generic: any scattered read of the page. |
| `Mirrors.Tones`, `MirrorShow` | +2, +8 | `tonesAt` (a default that reads per pixel); `drawRows` reads a row's reflected sources in one call. |
| `DrawingPad` (upstream) | +30 `blackWhite` | A fast preview's patch the fast plane refused for a grey (`direct_eink.c`, untouched) is sent again as its nearer of black and white, not the slow gray mode for the rest of the preview: Magnetic Poetry's tiles have soft grey edges, so every drag's first frame failed and the outline trailed the pen. The gray session shows the real greys when the preview ends, as before. Only what was refused before changes. |
| `PaintActivity` | +2 | `--ez nomad_mode true/false` on launch: the Nomad preview, its menu row gone since the refocus. |

Replay Page holds the UI thread 110–160 ms a step (every 600 ms; the brush engine replaying), but any touch ends a
replay, so it blocks nothing; left as it is. **Checks:** `MirrorChecks` (row reads match per-pixel reads over two
layers).
