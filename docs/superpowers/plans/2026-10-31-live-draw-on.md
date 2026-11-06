# Live pages, step 2c: marks draw themselves on; the page sent at once — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** (1) A mark from someone else draws itself on along the stroke that made it, at about the speed it was
drawn, instead of appearing at once (Elling: "for effect could it follow the stroke direction"); the final page is
exactly as now. (2) The whole page goes out when the session connects and when a page is shown, not at the first
change (the few seconds' wait Elling saw).

**Architecture:** Branch `live-probe`. A change carries its stroke's path (samples and their times; none for an eraser
sweep, a fill, the whole page: those still appear at once). The receiver gives each changed pixel a time, the time
of the nearest path sample (`LiveReveal`, pure Java; its JS twin in the browser), and shows the change in time order.
The browser animates it per frame. The Manta lays it in about 6 slices, each an ordinary partial update, all one
Undo step (`ToneDocument.finishWithPrevious` joins slices 2..n to slice 1); a pen coming down lays what's left at
once first, so the user's stroke is never joined to it.

## The format (both tasks must match)

`LiveChange` bytes, deflated (zlib): x, y, w, h (big-endian uint16); w·h grey bytes (255 clear); then **n**
(uint16, 0 = no path) and n samples, each **x, y, ms** (uint16 each: page pixels, and ms since the stroke's first
sample, capped at 65535). A change ending right after the greys (step 1–2a's form) reads as n = 0.

## Reveal time

Each changed pixel (grey ≠ 255) gets the `ms` of the path sample nearest to it (squared Euclidean; ties: the earlier
sample). Shown in order of that time over the path's duration scaled to **300–1000 ms** (shorter strokes stretched
to 300, longer squeezed to 1000). No path: shown at once.

---

### Task 1 (Java): format, reveal, the Manta's side

**Files:** `LiveChange.java`, new `LiveReveal.java`, `LiveChangeChecks.java` (or a new `LiveRevealChecks`, listed in
`scripts/test_inkwash.sh`), `LiveProbe.java`, `DrawingPad.java`, maybe `PaintActivity.java`.

- [ ] **LiveChange**: an `int[] path` field (x, y, ms triples; empty = none); a constructor overload keeping the old
  one (empty path); `write` appends n and the samples; `read` accepts both forms (end of stream after the greys → no
  path), refuses n > 20000, a cut-short path, bytes after it. Checks: round trip with a path; the old form reads as
  no path; refusals.
- [ ] **LiveReveal** (pure): `static int[] times(LiveChange c)`: per pixel of the rect its reveal ms (−1 for clear
  pixels); nearest sample, as above. Must be fast enough on the Manta for a 1000×1000 change with 500 samples: use a
  multi-source chamfer/BFS distance transform seeded at the samples carrying their ms, not pixels × samples brute
  force (say which, and the time for that case on the desktop). `static int[][] slices(LiveChange c, int n)`: the
  change split into n changes by reveal time (each a LiveChange over the same rect, greys only where its pixels fall
  in that slice, the rest clear); `static long[] at(LiveChange c, int n)`: each slice's ms after the start, scaled to
  300–1000 ms. Checks: a straight stroke's left end before its right end when drawn left to right, and the reverse;
  slices together hold exactly the change's pixels, each once; no path → one slice at 0.
- [ ] **Manta sends its path**: when a change came from a pen stroke, its samples go with it. The stroke log
  (`StrokeLog`, kept by `ToneDocument`, entries per Undo step with `serial`, samples in half pixels with ms between)
  is the source: the newest entry whose serial is the step the change made. Add the smallest accessor needed. Eraser,
  fill, Undo, whole page: no path.
- [ ] **Manta lays in slices**: in `LiveProbe.drain`, a change with a path is laid as `LiveReveal.slices(c, 6)`: slice 1
  as now (`layerChange(() -> PictureImport.place(...))`), then each next one at its time with `postDelayed`, laid with
  `ToneDocument.finishWithPrevious` instead of `finish` so all are one Undo step (find how `PictureImport.lay` ends its
  gesture and add a joined variant; don't duplicate its darkening rule). While a change is animating, the next one
  waits. **Pen down** (where `DrawingPad` already flushes a waiting Undo/Redo, `redoNow()`; also before Undo/Redo
  and a page change): the slices left are laid at once, joined, before anything else happens. `laying` stays true
  for each slice so nothing is sent back. Logs: slices, total ms.
- [ ] **The page at once**: `LiveProbe` sends the whole page when it starts listening (once connected, on the main
  thread) and when another page is shown (`DrawingPad.showPage`, a hook like `rendered`), so `changed()` no longer
  sends it at the first stroke (keep its "another page → whole" fallback).
- [ ] `bash scripts/test_inkwash.sh` all PASS; `./gradlew -q :app:assembleRelease :app:compileDebugAndroidTestJavaWithJavac`.

### Task 2 (browser): `scripts/live-probe/probe.html`

- [ ] **Send the path**: record each pointer sample (page pixels, ms since pointerdown) during the stroke; append n and
  the samples after the greys (format above; cap 20000 samples, keep every k-th if longer).
- [ ] **Reveal**: a received change with a path: compute each changed pixel's nearest-sample ms (brute force is fine
  on a laptop/phone up to ~1e7 pixel·samples; past that, sample the path down), sort or bucket by time, and with
  `requestAnimationFrame` write the pixels whose time has come over the scaled 300–1000 ms. The next change waits for
  the animation (the existing `incoming` promise chain). No path: at once, as now.
- [ ] Check without a browser what you can (relay + Python: a change with a path is passed through intact; `node --check`
  on the script); say the page is untried in a browser.

### Task 3: Try it with Elling (as step 2a: install, `adb reverse`, `live-probe.txt`, relay with `lan` if Elling asks)

- [ ] Note: how a Manta stroke draws on in the browser; how a phone stroke draws on the Manta (6 slices: charming or
  flickery?); the page showing at once in the browser on connect; Undo after a drawn-on mark (one step).
