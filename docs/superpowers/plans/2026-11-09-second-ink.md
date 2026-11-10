# Second Ink Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A brush switch, "Second Ink", whose marks show in one colour (chosen per notebook) in PDF and PNG copies and on the Fridge, while the tablet stays grey.

**Architecture:** Each plane gains an optional byte per pixel, `Layer.ink`: the share of the ink there that is second ink. The engine sets it once per gesture, at `finish`, from how much darkness the gesture added, so no stroke's hot loop changes and the screen never reads it. Copies composite a second-ink fraction beside the unchanged grey pipeline and write RGB only when a page holds second ink.

**Tech Stack:** Pure Java engine (`app/src/main/java/io/github/mpdairy/monopaint`, "P/" below), desktop checks (`bash scripts/test_inkwash.sh`), Brush Studio web (`docs/brush-lab/studio-src`, `web/engine`).

**Spec:** `docs/superpowers/specs/2026-11-09-second-ink-design.md`. Branch `second-ink` off `explore-live` (c7ebd5bb).

## Rules for every task

- Read `CLAUDE.md`, and `docs/INTEGRATION.md` before touching the engine or an upstream file; `PHILOSOPHY.md` and `docs/TEXT-STYLE.md` before any word the app shows.
- Never use adb, ssh or a device. Never `pkill`; stop only processes you started. Never print or commit `keystore.properties` or `local.properties`. Never bare `git stash`.
- `applicationId` and the Java package never change.
- Wherever `Layer.print` rides, `Layer.ink` rides: find print's sites with `grep -n "\.print\b\|print\b" P/<file>` and give ink the same treatment (null means none; a missing ink reads 0 where print reads 255).
- Checks: a class in `app/src/test/java/io/github/mpdairy/monopaint/*Checks.java` with `static void check(boolean ok, String what)` and a `main` printing `PASS: …`; add it to `checks=(...)` (line 14) and any new source to `sources=(...)` (line 13) of `scripts/test_inkwash.sh`. Finish with `bash scripts/test_inkwash.sh` passing in full.
- Docs in the same commit: `docs/INTEGRATION.md` (engine boundary, upstream files touched), `CHANGELOG.md` Unreleased (one line, only in the task that makes it user-visible), `docs/TOOLS.md` where a tool's behaviour is described.
- Commit messages end with:
  ```
  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- Report in under 150 words: status (DONE / DONE_WITH_CONCERNS / NEEDS_CONTEXT / BLOCKED), commits, what the checks printed, anything left out.

## Names every task shares

| Name | Where | Meaning |
|---|---|---|
| `byte[] ink` | `ToneDocument.Layer` | second ink's share of each pixel's ink, 0–255; null: none |
| `boolean inked()` | `ToneDocument.Layer` | the layer or one of its planes holds a nonzero ink share |
| `void inking(int share)` | `ToneDocument` | called after `begin`: this gesture's paint is `share` (0–255) second ink. Not called: 0 |
| `void inkFlows()` | `ToneDocument` | called after `begin`: this gesture moves paint that is already there; darkened pixels take the ink around them |
| `int inkFound(int x,int y)` | `ToneDocument` (private) | the ink share at the gesture's start, like `shareFound` |
| `PartDial.SECOND` | `PartDial` | the Medium dial "Second Ink", key `second`, Off (0, default) / On |
| `ResolvedBrush.second(b)` | `ResolvedBrush` | true when the brush's Second Ink is On, like `soluble(b)` |
| `SecondInk` | new `P/SecondInk.java` | the notebook's inks: `NAMES`, `rgb(int index)`, `DEFAULT = 0` |
| `NotebookOptions.ink` / `withInk(int)` | `NotebookOptions` | index into `SecondInk`, default 0 (Blue) |

---

### Task 1: The engine's ink share (worktree `second-ink`)

**Files:** Modify `P/ToneDocument.java`; create `app/src/test/java/io/github/mpdairy/monopaint/SecondInkChecks.java`; modify `scripts/test_inkwash.sh`, `docs/INTEGRATION.md`.

- [x] **Step 1: the field.** `Layer.ink` beside `print`, with a doc comment in the file's style. `copy()`, `release()`, `writable()`/bind, `flattened()` (drops it, as it drops print) and `each(...)` treat it as they treat `print`. `Layer.inked()` like `prints()` (any nonzero byte, on the layer or a plane).
- [x] **Step 2: capture.** `capture(key)` also keeps the ink tile (`foundInks`, `beforeInk`), as it keeps print's; `inkFound(x,y)` beside `shareFound`.
- [x] **Step 3: declare.** `inking(int share)` and `inkFlows()` beside `printing()`; `begin` resets both.
- [x] **Step 4: the rule at `finish`**, before the `TileEdit`s are built, for each captured tile's pixel. With darkness `d = 255 − onWhite(tone, alpha)` before (`d0`) and after (`d1`), and the found ink `i0`:
  - **Skip everything** (no loop) when the gesture's ink is 0, it doesn't flow, and the target has no `ink`; and when it flows and the target has no `ink`.
  - `d1 == 0`: ink 0.
  - `d1 > d0`: `ink = (d0·i0 + (d1 − d0)·g + d1/2) / d1`, where `g` is the declared share, or for a flowing gesture the flow ink below.
  - `d1 < d0` on `PLANE_OPAQUE` with a declared share: `ink = i0 + (g − i0)·(d0 − d1)/d0` (lighter cover paint over darker takes the ink over).
  - otherwise the ink stays `i0` (erasing and lifting leave what stays its ink).
  - **Flow ink:** the mean of `inkFound` over the 5×5 pixels round it, each weighted by its darkness at the gesture's start. Where that weight is 0 (paint that ran onto clear paper), further passes take the mean of the inks already set this finish, weighted by `d1`, until a pass sets none (at most 64 passes); what is left gets 0.
  - The `ink` array is made on the first nonzero share, through the same copy-on-write path the stroke's writes use (`writable`, `shared`, `holds`): a snapshot taken before the gesture must not see the new shares.
- [x] **Step 5: Undo.** `TileEdit` gains `inkBefore`/`inkAfter` (`samePlane`, `restoreTile`, the continuation merge keeps the older `inkBefore` and the newer `inkAfter`), and undo, redo, replay's `patch`, `clear`, validation and `bytes` carry them as they carry print's.
- [x] **Step 6: checks** (`SecondInkChecks`), each on a small `ToneDocument`, writing with `begin`/`inking`/`store`/`finish` on a plane target as `PrintScreenChecks` or `PlaneChecks` do:
  - a stroke with `inking(255)` on clear paper: ink 255 where it drew, the layer `inked()`; without `inking`: `ink` stays null
  - first ink over second at equal darkness added: ink near 128; more darkness pulls it further down
  - an erase after it: ink unchanged where paint stays, 0 where none stays
  - Undo then Redo: ink back to before, then after, byte for byte; a snapshot taken before the stroke still has no ink
  - a flowing gesture that darkens clear paper beside second-ink paint: those pixels take a nonzero ink; beside first-ink paint: 0; between the two: a share between
  - the screen's composite (`PagePicture` or the document's own flatten the checks already use) is byte for byte the same with and without the ink shares
- [x] **Step 7:** `bash scripts/test_inkwash.sh`; INTEGRATION.md gets a short "ink share" paragraph where it describes share and print. Commit `Second Ink: the engine's ink share per pixel, set at finish`.

### Task 2: The notebook's ink (worktree `second-ink-notebook`)

**Files:** Create `P/SecondInk.java`; modify `P/NotebookOptions.java`, `P/BookOptions.java`, `P/NotebookScreens.java` (fillSettings), `LibraryCard` only if settings rows need it; checks in `NotebookOptionsChecks.java` (or a new `SecondInkOptionChecks`); `scripts/test_inkwash.sh`; `CHANGELOG.md`.

- [x] **Step 1:** `SecondInk`: `static final int DEFAULT = 0; static final String[] NAMES = {"Blue","Sepia","Red","Green","Violet"}; static int rgb(int index)` returning `0x234fa8, 0x704214, 0xb82a2a, 0x2e6b3e, 0x5b3a9a`; an index out of range reads as `DEFAULT`.
- [x] **Step 2:** `NotebookOptions.ink` with `withInk(int)`, in the constructor, `equals`, `hashCode`, the way the page colour is.
- [x] **Step 3:** `BookOptions`: a 4-char record `SINK` written only when `ink != SecondInk.DEFAULT`, read with a range check; follow `PCOL` (write ~283, read ~319). An older reader skips unknown records: extend the existing older-options check (`options_ref` in `scripts/test_inkwash.sh`, `OlderOptionsCheck`) or assert the skip the way PCOL's check does.
- [x] **Step 4:** Notebook Settings row "Second Ink" with one chip per ink, following the nib preview row (`NotebookScreens.fillSettings` ~1086, `settingRow` 1155, `chips` 1253) and `PaintActivity.setNotebookOptions`. Words per `docs/TEXT-STYLE.md`; the row's note: "Second Ink brushes show in this colour in PDF and PNG copies." (adjust to the style rules).
- [x] **Step 5:** checks: options round-trip each ink through `BookOptions`; the default writes no record (bytes equal to today's); an out-of-range index reads as Blue. `bash scripts/test_inkwash.sh`. Commit `Second Ink: the notebook's ink, chosen in Notebook Settings`.

### Task 3: The brush switch (worktree `second-ink-notebook`, after Task 2)

The Dissolves dial is the model: `git show 920bcbcb --stat` lists every file it reached; `PartDial.SOLUBLE` (P/PartDial.java:178), `ModularCode.ADDED` (39), `ResolvedBrush.soluble` (607).

**Files:** `P/PartDial.java`, `P/PartChoice.java`, `P/ModularCode.java`, `P/ResolvedBrush.java`, `P/ModularWords.java`, `P/BrushCaptions.java`, `P/BrushImport.java` (NEWER), LabLayout/LabSettings only if a dial needs listing; `docs/BRUSH-CODE.md`, `docs/brush-code.json` (via `scripts/brush-code-json.sh`); `docs/brush-lab/studio-src` (parts.js ~171, newmods.js ~4109) then `node docs/brush-lab/studio-src/build.cjs`; `web/engine/build.sh` and `scripts/web_parity.sh` where they apply; checks `PartModelChecks`, `ModularCodeChecks`, `BrushCodeChecks`, `CodeSpecChecks`, `StudioGuideChecks`; `scripts/test_inkwash.sh` (a pinned older-reader check as `later_ref`'s, ref `c7ebd5bb`); `CHANGELOG.md`, `docs/TOOLS.md`.

- [x] **Step 1:** `PartDial.SECOND(BrushPart.MEDIUM, "second", "Second Ink", 0, "Off", "On")`, appended last in the Medium lists of INK, WASH, COVER, CHARCOAL and GRAPHITE (not RESIST, not VECTOR), and last in `ModularCode.ADDED`, per "How the code grows" in `docs/BRUSH-CODE.md` (a new dial: last in its choice's list, trimmed when fresh).
- [x] **Step 2:** `ResolvedBrush.second(b)`; the dial's words and "This brush" caption ("Shows in the notebook's second ink in copies." adjusted to TEXT-STYLE).
- [x] **Step 3:** docs and web: BRUSH-CODE.md's dial tables, brush-code.json regenerated, Studio's parts and dial row, Studio rebuilt.
- [x] **Step 4:** checks: a code with Second Ink On round-trips; a fresh brush's code is unchanged from before this task; the reader at c7ebd5bb reads an On code as the plain brush with the "Newer version" note; the web reader agrees (`code-spec-check.cjs`). `bash scripts/test_inkwash.sh`. Commit `Second Ink: the brush's switch, in Brush Lab, the share code and Brush Studio`.

### Task 4: Strokes declare their ink; the ink rides with its pixels (worktree `second-ink`, after 1 and 3 are merged)

**Files:** `P/ToneDocument.java` (transforms, Lasso `layPixel`/`lay`, merge `put`, `sinkUnder`, erase), `P/Carry.java`, `P/FillTool.java`, `P/FloatWater.java`, `P/InkWashLayers.java`, `P/InkWash.java`, `P/LassoPlanes.java`, `P/ModularCompose.java`, `P/ModularStroke.java`, `P/QuickStroke.java`, `P/WetPaintStroke.java`, `P/WetCanvas.java`, `P/WetWatercolor.java` (upstream: the smallest hook, marked Inkwash, noted in INTEGRATION.md), `P/DryingWater.java`, `P/HaloGrowth.java`, `P/WatercolorSession.java`, `P/NotebookScreens.java`; `SecondInkChecks.java`.

- [x] **Step 1: live strokes.** Every gesture that draws with a brush calls `document.inking(ResolvedBrush.second(brush) ? 255 : 0)` after its `begin` (ModularStroke 375 and 1608, WetPaintStroke 107, QuickStroke 366, WetCanvas 193, FillTool where it lays a brush's paint). Vector Ink and tools without a brush declare nothing.
- [x] **Step 2: paint that moves by itself** calls `document.inkFlows()` after its `begin`: WetWatercolor 675 and 1147 (inside `if (!strokeActive)`), DryingWater 428, FloatWater 358 and 377, HaloGrowth 685, and a smudge that lays no paint of its own.
- [x] **Step 3: carriers.** Each remaining site that copies, moves, transforms or merges `print` does the same for `ink`: page transforms (flip, rotate, move), the Lasso's lift and lay (`layPixel`, `lay`, `LassoPlanes`), `Carry`, layer merge (`put`: the merged pixel's ink is the two inks weighted by their darkness), `sinkUnder`, `InkWashLayers`, `ModularCompose`.
- [x] **Step 4: checks** added to `SecondInkChecks`: a real second-ink brush stroke (a `ModularStroke` as other stroke checks build one) leaves ink; the same brush with the switch Off leaves none and the page's bytes equal today's; a wet second-ink wash beside a wet first-ink one, dried: the ink share grades between them where paint moved; the Lasso moves a second-ink mark and its ink arrives with it, Undo brings both back; flip and rotate carry it; merging a second-ink layer onto a first-ink one mixes by darkness.
- [x] **Step 5:** `bash scripts/test_inkwash.sh`. Commit `Second Ink: strokes declare their ink and it rides with its pixels`.

### Task 5: The page file (worktree `second-ink-notebook`, after 1 is merged in)

**Files:** `P/DocumentCodec.java`; `SecondInkChecks.java` or a new `SecondInkFileChecks.java` plus an older-reader helper (as `OlderMediaCheck`); `scripts/test_inkwash.sh`; `docs/INTEGRATION.md` or the file-format doc that lists the sections.

- [x] **Step 1: write.** A new tagged section `INK_TAG` ("INKS"), written only when some layer is `inked()`: for each layer a mask byte (bit 0 the layer's own pixels, bit n+1 plane n in memory order), then for each set bit `pixels` bytes. A layer's all-zero ink counts as none.
- [x] **Step 2: read.** Buffered while the sections are read and applied at `END_TAG` after `linePlanes`, so plane order matches memory; counts toward `most`; a repeated or wrong-sized section fails as the others do ("Repeated painting section" / "Invalid painting section"); a set bit for a plane the layer lacks is invalid.
- [x] **Step 3: checks.** A page with ink on a plane and on a base layer round-trips byte for byte in memory; a page without ink writes the same bytes as before this task; the reader at c7ebd5bb opens a page with the section and sees the same tones (pinned-ref check in `scripts/test_inkwash.sh`, modelled on `media_ref`'s); a truncated or oversized section is refused.
- [x] **Step 4:** `bash scripts/test_inkwash.sh`. Commit `Second Ink: the page file keeps the ink shares in a section older versions skip`.

### Task 6: Copies in colour (worktree `second-ink`, after 2, 4 and 5 are merged)

**Files:** `P/PagePicture.java` (Composite), `P/NotebookExport.java`, `P/PngRender.java`, `P/PageExport.java`, `P/PageStacks.java`, `P/Fridge.java`, callers of `NotebookExport.flatten` (NotebookExport 441, 450, 544; PageExport 111; NotebookScreens 771); new `SecondInkCopyChecks.java`; `scripts/test_inkwash.sh`; `CHANGELOG.md`, `docs/TOOLS.md`.

The maths: a mark of tone `t`, coverage `a` and ink share `s` shows the colour `t + ink_c·(255−t)·s/255/255` per channel, so source-over gives exactly `grey + ink_c·B/255`, where `B` composites like the grey: `B = ((255−t)·s/255·a + B·(255−a))/255`, 0 on paper. The grey pipeline stays as it is; beside it rides one byte a pixel, `F = 255·B/(255−G)` (0 where `G` is 255), the second ink's fraction of the pixel's darkness. The copy's pixel is `G' + ink_c·F·(255−G')/255/255` per channel, `G'` the final grey after `DotPattern.exportGray`.

- [x] **Step 1:** `PagePicture.Composite` keeps each place's `ink` (null for most) and gains `inkRow(int y, byte[] out)` giving `F` for the row, sharing `row`'s loop rather than copying it; `boolean inked()` when any visible place has ink. Thumbnails, the live page and Draw Together never call it.
- [x] **Step 2:** `NotebookExport.inks(composite, …)` beside `greys(…)`: null when the composite isn't `inked()`. Overlays that replace pixels (magnets, text boxes) zero `F` where they changed the grey. `shrink` reuses `shrunk` for `F`, weighted by darkness. High Res (`PngRender.high`) doubles `F`, zero where `shown` differs from the raw page. The Vector Ink overlay and PSD layers stay grey.
- [x] **Step 3:** PNG: `NotebookExport.png` and `PngRender.Png` write 8-bit RGB (colour type 2, filter bpp 3) when `F` is present, else exactly today's bytes. `NotebookExport.read` accepts those RGB PNGs; `imageDictionary` writes `/DeviceRGB` and `/Colors 3` for them.
- [x] **Step 4:** the ink's colour reaches every copy: `NotebookExport.flatten` and the `Snapshot` it returns carry the notebook's `SecondInk.rgb(options.ink)` (a signature change, so no caller forgets); `pageKey`/`stackKey` mix the ink index in only when the page is inked and the index isn't the default, so today's exports are not rewritten. `Fridge.png` goes through `PageExport.bytes`, so it follows.
- [x] **Step 5: checks** (`SecondInkCopyChecks`): a page without ink exports PNG and PDF byte for byte as before this task (compare with bytes from the same build with `ink` nulled, and a hash recorded before the change); a black second-ink mark on white paper exports the ink's RGB, a 50% one halfway to white; first ink over second on another plane is black; grey paper under a second-ink mark stays grey round it; changing the notebook's ink changes the copy and its page key, and only for inked pages; the RGB PNG reads back and its PDF image dictionary says `/DeviceRGB`; a shrunk size and High Res both keep the colour.
- [x] **Step 6:** `bash scripts/test_inkwash.sh`. Commit `Second Ink: PDF, PNG and Fridge copies show the notebook's ink in colour`.

### Task 7: The whole (controller)

- [x] Full `bash scripts/test_inkwash.sh`, `scripts/web_parity.sh` if Studio's engine changed, a release build.
- [x] One review of the whole branch against the spec (duplicated logic, missed print sites, the fast path).
- [ ] Install on the Manta (controller only), tell the owner the app restarted; the owner draws a second-ink wash beside black lines and grey washes, Export Now, and opens the PDF on a colour screen.

## As built (2026-11-10)

- **Branch:** `second-ink`; tasks 2, 3, 5 and 6 came in from `second-ink-notebook`. Not on explore-live.
- **Departures:**
  - Task 1: a lightening opaque stroke takes the ink over only when the gesture declares a share, not when its ink flows.
  - Task 4: a smudge flows only when it lays no paint (Own paint 0); shapes take their slot's brush's ink; Later paint takes the mean ink of the mark's pixels that were clear before it.
  - Task 5: an ink bit for a plane the page lacks is dropped, the page still opens.
  - Task 6: `.lines.txt` ends with `ink N` only when the notebook's ink isn't Blue, so such a notebook's PDF is written once more.
- **Left:**
  - Replay's copy of a Merge Down loses its ink (needs a stroke-log format change).
  - A grey PNG an older version exported counts as current until its page changes.
  - The older-reader checks SKIP without commit c7ebd5bb (the public repo).
  - At High Res, a Vector Ink line or a kept dry stroke over second ink has edge pixels off (`shortcut:` in `PngRender`).
  - Drying with second ink on the page costs about 1 ms a tick more on the desktop; unmeasured on the Manta.
- **`scripts/web_parity.sh`:** both reports are written, then Node hangs at exit (the same on explore-live c7ebd5bb).

## Not in this plan

Draw Together and the live page in colour, more than one extra ink, an ink per page, tinted paper, PSD layers in colour, Vector Ink in second ink.
