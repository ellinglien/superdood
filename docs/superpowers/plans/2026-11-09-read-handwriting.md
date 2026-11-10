# Read Handwriting (step 1: reading and Search) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An opt-in System Settings row, Read Handwriting, that reads the pen writing on every notebook page on the tablet (Google's ML Kit Digital Ink) and lets the shelf search it.

**Architecture:** Pure Java does the work that can be checked on the desktop:
- `PageWriting`: a page's stroke log to the strokes still on the page.
- `WritingLines`: strokes to lines.
- `Transcript`: the per-notebook file, and search matching.

Two Android classes wrap ML Kit and the background work: `HandwritingReader` and `HandwritingQueue`. System Settings, the save hook, the delete and rename hooks, and the shelf's Search connect them.

**Tech Stack:** Java 8 (the app's `compileOptions`), Android API 27–34, `com.google.mlkit:digital-ink-recognition:18.1.0`. Desktop checks are run by `scripts/test_inkwash.sh`.

**Spec:** `docs/superpowers/specs/2026-11-09-read-handwriting-design.md` (sections: Read Handwriting, What is read, The transcripts, Search on the shelf). Make Text and Typeset are later plans.

**Before starting, know this:**
- Every Java path below is relative to `app/src/main/java/io/github/mpdairy/monopaint/` (main) or `app/src/test/java/io/github/mpdairy/monopaint/` (checks). Everything is in one package, `io.github.mpdairy.monopaint`.
- Build env:
  ```bash
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" ANDROID_HOME="$HOME/Library/Android/sdk"
  ```
- **Running one desktop check quickly:**
  ```bash
  c=$(mktemp -d); eval "$(sed -n '/^main=/p;/^test=/p;/^sources=/p' scripts/test_inkwash.sh)"; javac -nowarn -d "$c" $(for s in "${sources[@]}"; do echo "$main/$s.java"; done) $test/XxxChecks.java && java -cp "$c" io.github.mpdairy.monopaint.XxxChecks; rm -rf "$c"
  ```
- **Check style:** a `public static void main`, `check(boolean, String)` throwing `AssertionError("FAIL: ...")`, and one `PASS:` line. `SystemSettingsChecks.java` is the model.
- **Text the app shows** follows `docs/TEXT-STYLE.md`. **Product rules** are in `PHILOSOPHY.md`. **Engine boundary rules** are in `docs/INTEGRATION.md`; reading is app code, not engine code.
- **Never** `git add -A`. Add the files you changed by name. End each commit message with the two attribution lines this session uses (Co-Authored-By and Claude-Session).
- **Agents never use adb or the Manta.** The Manta check (Task 12) is the controller's.

## File structure

| File | New or changed | Responsibility |
|---|---|---|
| `DocumentCodec.java` | changed | `log(byte[])`: a page's stroke log, its pixels skipped. |
| `PageWriting.java` | new, pure | The pen strokes still on a page: Lasso moves applied, erased and wide strokes left out. |
| `WritingLines.java` | new, pure | Strokes to lines (the probe's rule). |
| `Transcript.java` | new, pure | A notebook's lines by page CRC; the file format; folding; search matching. |
| `HandwritingReader.java` | new, Android | The only class that knows ML Kit: download, delete, read a line. |
| `HandwritingQueue.java` | new, Android | One low-priority thread: read changed pages, count unread pages, delete everything. |
| `PaintPreferences.java` | changed | `readHandwriting()` / `setReadHandwriting(boolean)`. |
| `SystemSettings.java`, `SystemSettingsPanel.java` | changed | The row, and its first-time words. |
| `PaintActivity.java` | changed | The queue made, fed from `saved`, delete and rename; open a notebook at a page with marks. |
| `LabUi.java`, `ToolsActivity.java` | changed | The library's search field moved into `LabUi.SearchField`, shared. |
| `NotebookScreens.java` | changed | Search on the shelf: results in the grid's place, marks over the page shown. |
| `app/build.gradle` | changed | The ML Kit dependency. |
| `scripts/test_inkwash.sh` | changed | The new pure classes and checks. |
| `PageWritingChecks.java`, `WritingLinesChecks.java`, `TranscriptChecks.java` | new | Desktop checks. |

---

### Task 1: A page's stroke log without its pixels

**Files:**
- Modify: `DocumentCodec.java`. Two edits: `read(InputStream,boolean,int,int)` at about line 688, and `readLayers(...)` at line 329 and its `if(!retain) return null;` at about line 371.
- Test: `PageWritingChecks.java`, created here and grown in Task 2.

- [ ] **Step 1: Write the failing check**

```java
package io.github.mpdairy.monopaint;

/** Read Handwriting: a page's pen strokes read from its log (docs/superpowers/specs/2026-11-09-read-handwriting-design.md). */
public final class PageWritingChecks {
    public static void main(String[] args) throws Exception {
        logRead();
        System.out.println("PASS: a page's stroke log reads without its pixels");
    }

    /** A page with one logged pen stroke, written and read back through DocumentCodec.log. */
    static void logRead() throws Exception {
        ToneDocument d = InkWashLayers.blank(400, 300);
        StrokeLog.Recorder r = StrokeLog.Recorder.of(false, false, "pen-code", 4, 1);
        for (int i = 0; i < 10; i++) r.add(10 + i * 5, 20, .5f, Float.NaN, Float.NaN, i * 8);
        d.placeLog(StrokeLog.NONE.added(r.entry(1), Long.MAX_VALUE));
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        DocumentCodec.write(out, d);
        DocumentCodec.PageLog read = DocumentCodec.log(out.toByteArray());
        check(read.width == 400 && read.height == 300, "the page's size: " + read.width + "x" + read.height);
        check(read.log.strokes().size() == 1, "one stroke in the log: " + read.log.strokes().size());
        check(read.log.strokes().get(0).size() == 10, "its ten samples");
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
```

**Note to the implementer:** before running anything, read `StrokeLog.java` around lines 320–410 for the exact `Recorder` and `added` API, and `DocumentCodec.java` for the exact `write` signature. Adjust the three setup lines (`Recorder.of`, `r.add`, `r.entry`/`added`, `DocumentCodec.write`) to the real names. **Keep what the check asserts.** If recording through the API is awkward, build the page through `QuickStroke` on a `ToneDocument` that records, as `StrokeLogChecks.java` does. Copy its setup.

- [ ] **Step 2: Add it to the test script and run it. It should fail to compile: no `DocumentCodec.log`.**

In `scripts/test_inkwash.sh`, add `PageWriting` to `sources=(` and `PageWritingChecks` to `checks=(`. (`PageWriting.java` arrives in Task 2; until then, add only the check and leave `PageWriting` out of `sources`.)

Run the one-check command for `PageWritingChecks`. Expected: `cannot find symbol ... PageLog` / `log`.

- [ ] **Step 3: Implement**

In `DocumentCodec.java`, add next to `validate`:

```java
    /** A page's size and stroke log (Read Handwriting), its pixels inflated past but never kept. */
    static final class PageLog {
        final int width, height;
        final StrokeLog log;
        PageLog(int width, int height, StrokeLog log) { this.width = width; this.height = height; this.log = log; }
    }
    static PageLog log(byte[] page) throws IOException {
        java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(page);
        header.getInt();
        int width = header.getInt(), height = header.getInt();
        StrokeLog[] log = {StrokeLog.NONE};
        read(new java.io.ByteArrayInputStream(page), false, 0, 0, log);
        return new PageLog(width, height, log[0]);
    }
```

Change the private `read(InputStream stream, boolean retain, int expectedWidth, int expectedHeight)` to take a fifth parameter, `StrokeLog[] logOut` (null: not wanted). Pass it to `readLayers`. Make the two existing callers pass `null`:

```java
    static ToneDocument read(InputStream stream) throws IOException { return read(stream,true,0,0,null); }
    static void validate(InputStream stream,int width,int height) throws IOException { read(stream,false,width,height,null); }
```

In `read`'s body:

```java
            return readLayers(stream,width,height,retain,magic!=LAYER_MAGIC,magic==MASK_MAGIC,magic==SECTIONS_MAGIC,logOut);
```

`readLayers` takes `StrokeLog[] logOut` as its last parameter. Just before `if(!retain) return null;`, add:

```java
            if(logOut!=null) logOut[0]=log[0];
```

An older page without layers has no log; `logOut` keeps `StrokeLog.NONE`.

- [ ] **Step 4: Run the check. Expected: `PASS: a page's stroke log reads without its pixels`.**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/mpdairy/monopaint/DocumentCodec.java app/src/test/java/io/github/mpdairy/monopaint/PageWritingChecks.java scripts/test_inkwash.sh
git commit -m "Read Handwriting: a page's stroke log read without keeping its pixels (DocumentCodec.log)"
```

---

### Task 2: PageWriting, the pen strokes still on a page

**Files:**
- Create: `PageWriting.java`
- Modify: `PageWritingChecks.java`, `scripts/test_inkwash.sh` (add `PageWriting` to `sources`)

Read first: `StrokeLog.Entry` (`StrokeLog.java:61-175`): `kind`, `px`, `size()`, `px(i)`/`py(i)` (page px), `dt[i]` (ms since the sample before), `loop()` (a LIFT's loop, x,y pairs in page px), and `place` (`Place`: `back`, `drop`, `cut`, `centerX`, `centerY`, `scale`, `angle`). Read how `VectorLines.posed` (about `VectorLines.java:871`) turns `angle` into a rotation (radians or degrees, and its sign), and use the same in `pose` below.

- [ ] **Step 1: Write the failing checks**

Add to `PageWritingChecks.main` before the PASS line: `strokes();`. Change the PASS text to `"PASS: a page's stroke log reads without its pixels; its pen strokes still on the page come out, moved where the Lasso put them, erased and wide ones left out"`. Add:

```java
    /** Builds entries straight from StrokeLog's constructors (see StrokeLogChecks for how it makes them). */
    static void strokes() {
        // Two pen strokes, one wash, one finger stroke.
        StrokeLog.Entry pen1 = penEntry(StrokeLog.PEN, 4, new float[]{100, 100, 140, 100});
        StrokeLog.Entry pen2 = penEntry(StrokeLog.PEN, 4, new float[]{100, 200, 140, 200});
        StrokeLog.Entry wash = penEntry(StrokeLog.PEN, 60, new float[]{50, 50, 300, 50});
        StrokeLog.Entry finger = penEntry(StrokeLog.FINGER, 4, new float[]{10, 10, 30, 10});
        java.util.List<StrokeLog.Entry> entries = java.util.Arrays.asList(pen1, pen2, wash, finger);
        java.util.List<PageWriting.Stroke> s = PageWriting.strokes(entries, null);
        check(s.size() == 2, "the two pen strokes, not the wash or the finger: " + s.size());
        check(s.get(0).x[0] == 100 && s.get(0).y[0] == 100, "the first stroke where it was drawn");
        check(s.get(1).t[0] > s.get(0).t[s.get(0).t.length - 1], "times run on from stroke to stroke");

        // Erased: a tone function saying the second stroke's row is blank paper.
        PageWriting.Tones tones = (x, y) -> y > 150 ? 255 : 0;
        check(PageWriting.strokes(entries, tones).size() == 1, "an erased stroke is left out");

        // Moved: lift a loop round the first stroke, put it down 100 px lower, unscaled and unturned.
        StrokeLog.Entry lift = liftEntry(new float[]{90, 90, 150, 90, 150, 110, 90, 110});
        StrokeLog.Entry place = placeEntry(1, true, true, 120, 200, 1, 0); // back 1: the lift just before it
        java.util.List<PageWriting.Stroke> moved = PageWriting.strokes(java.util.Arrays.asList(pen1, lift, place), null);
        check(moved.size() == 1, "the moved stroke stays one stroke");
        check(Math.abs(moved.get(0).y[0] - 200) < 1 && Math.abs(moved.get(0).x[0] - 100) < 1, "moved to its new place: " + moved.get(0).x[0] + "," + moved.get(0).y[0]);

        // Cut without a drop: deleted.
        StrokeLog.Entry cut = placeEntry(1, true, false, 0, 0, 1, 0);
        check(PageWriting.strokes(java.util.Arrays.asList(pen1, lift, cut), null).isEmpty(), "a lifted piece deleted takes its stroke");
    }
```

Write the three builders, `penEntry(kind, px, float[] xy)` (samples at those points, 8 ms apart), `liftEntry(float[] loop)` and `placeEntry(back, cut, drop, cx, cy, scale, angle)`, using `StrokeLog.Entry`'s constructors or factories. Copy how `StrokeLogChecks.java` makes entries. Keep them in `PageWritingChecks`.

- [ ] **Step 2: Run. Expected: compile failure, no `PageWriting`.**

- [ ] **Step 3: Implement `PageWriting.java`**

```java
package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;

/**
 * Read Handwriting (docs/superpowers/specs/2026-11-09-read-handwriting-design.md, "What is read"): the pen strokes a
 * page's log says are on it now. Pen strokes only (no finger, smudge, shape, picture or merge); no brush wider than
 * writing ({@link #WIDEST}); a Lasso's move applied to the strokes inside its loop; and, given the page's tones, none
 * whose points are mostly blank paper now (erased). Each stroke's points keep their times, run on from stroke to
 * stroke. Pure; checked on the desktop ({@code PageWritingChecks}).
 */
final class PageWriting {
    /** shortcut: a fixed width past which a brush is a wash, not writing; set it from real pages when they show otherwise. */
    static final double WIDEST = 24;
    /** ms between one stroke's last point and the next one's first, where the log keeps none. */
    static final long BETWEEN = 200;

    /** The page's tone at page px {@code x, y}: 0 black to 255 paper. */
    interface Tones { int tone(float x, float y); }

    static final class Stroke {
        final float[] x, y;
        final long[] t;
        Stroke(float[] x, float[] y, long[] t) { this.x = x; this.y = y; this.t = t; }
        int size() { return x.length; }
    }

    /** {@code entries}: a page's log as shown ({@code StrokeLog.strokes()}); {@code tones}: the page now, null to skip the erase check. */
    static List<Stroke> strokes(List<StrokeLog.Entry> entries, Tones tones) {
        List<Stroke> on = new ArrayList<>();
        List<List<Stroke>> lifted = new ArrayList<>(); // by entry index of each LIFT: the strokes inside its loop
        List<Integer> liftAt = new ArrayList<>();
        long clock = 0;
        for (int i = 0; i < entries.size(); i++) {
            StrokeLog.Entry e = entries.get(i);
            if (e.kind == StrokeLog.PEN && e.px <= WIDEST && e.size() > 0) {
                int n = e.size();
                float[] x = new float[n], y = new float[n];
                long[] t = new long[n];
                for (int k = 0; k < n; k++) { clock += k == 0 ? BETWEEN : e.dt[k]; x[k] = e.px(k); y[k] = e.py(k); t[k] = clock; }
                on.add(new Stroke(x, y, t));
            } else if (e.kind == StrokeLog.LIFT) {
                float[] loop = e.loop();
                List<Stroke> inside = new ArrayList<>();
                for (Stroke s : on) if (mostlyInside(s, loop)) inside.add(s);
                lifted.add(inside);
                liftAt.add(i);
            } else if (e.kind == StrokeLog.PLACE && e.place != null && e.place.back > 0) {
                int from = liftAt.indexOf(i - e.place.back);
                if (from < 0) continue;
                List<Stroke> piece = lifted.get(from);
                if (e.place.cut) on.removeAll(piece);
                if (!e.place.drop || piece.isEmpty()) continue;
                // shortcut: the lifted pixels' box isn't logged; the piece's points' box stands in for it (off by up to half a brush).
                float[] box = box(piece);
                float cx = (box[0] + box[2]) / 2, cy = (box[1] + box[3]) / 2;
                List<Stroke> placed = new ArrayList<>();
                for (Stroke s : piece) placed.add(pose(s, cx, cy, e.place.centerX, e.place.centerY, e.place.scale, e.place.angle));
                on.addAll(placed);
                lifted.set(from, placed);
            }
        }
        if (tones == null) return on;
        List<Stroke> kept = new ArrayList<>();
        for (Stroke s : on) if (stillThere(s, tones)) kept.add(s);
        return kept;
    }

    /** At least half the stroke's points inside {@code loop} (x,y pairs; even-odd). */
    static boolean mostlyInside(Stroke s, float[] loop) {
        int in = 0;
        for (int k = 0; k < s.size(); k++) if (inside(s.x[k], s.y[k], loop)) in++;
        return in * 2 >= s.size();
    }
    static boolean inside(float x, float y, float[] loop) {
        boolean in = false;
        for (int i = 0, j = loop.length / 2 - 1; i < loop.length / 2; j = i++) {
            float xi = loop[2 * i], yi = loop[2 * i + 1], xj = loop[2 * j], yj = loop[2 * j + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }
    static float[] box(List<Stroke> strokes) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Stroke s : strokes) for (int k = 0; k < s.size(); k++) { b[0] = Math.min(b[0], s.x[k]); b[1] = Math.min(b[1], s.y[k]); b[2] = Math.max(b[2], s.x[k]); b[3] = Math.max(b[3], s.y[k]); }
        return b;
    }
    /** The stroke as the Lasso puts its piece down: its centre {@code fromX, fromY} to {@code toX, toY}, scaled, turned as VectorLines.posed turns. */
    static Stroke pose(Stroke s, float fromX, float fromY, float toX, float toY, float scale, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle); // match VectorLines.posed's units and sign
        float[] x = new float[s.size()], y = new float[s.size()];
        for (int k = 0; k < s.size(); k++) {
            double dx = (s.x[k] - fromX) * scale, dy = (s.y[k] - fromY) * scale;
            x[k] = (float) (toX + dx * cos - dy * sin);
            y[k] = (float) (toY + dx * sin + dy * cos);
        }
        return new Stroke(x, y, s.t);
    }
    /** Most of the stroke's points still have ink near them: the darkest tone within a px of each point is darker than paper by some margin. */
    static boolean stillThere(Stroke s, Tones tones) {
        int inked = 0;
        for (int k = 0; k < s.size(); k++) {
            int darkest = 255;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) darkest = Math.min(darkest, tones.tone(s.x[k] + dx, s.y[k] + dy));
            if (darkest < PAPER_MARGIN) inked++;
        }
        return inked * 2 >= s.size();
    }
    /** A tone this far under paper (255 after {@link #onPaper}) counts as ink. */
    static final int PAPER_MARGIN = 239;
    /** {@code tones} with the page's paper tone ({@code paper}, Page Shade's) lifted to 255, so {@link #PAPER_MARGIN} works on any shade. */
    static Tones onPaper(Tones tones, int paper) {
        return (x, y) -> Math.min(255, tones.tone(x, y) + (255 - paper));
    }

    private PageWriting() {}
}
```

If `VectorLines.posed` uses degrees or the opposite sign, convert in `pose` and change the comment to say which. The check's move has `angle = 0`, so it passes either way. Add one more check with a 90° turn once you know the convention:

```java
        StrokeLog.Entry turn = placeEntry(1, true, true, 120, 100, 1, /* 90° in posed's units */ Math.PI / 2);
        PageWriting.Stroke t = PageWriting.strokes(java.util.Arrays.asList(pen1, lift, turn), null).get(0);
        check(Math.abs(t.x[0] - t.x[t.size() - 1]) < 1, "a quarter turn stands the stroke upright");
```

- [ ] **Step 4: Add `PageWriting` to `sources` in `scripts/test_inkwash.sh`, run the check. Expected: PASS.**

- [ ] **Step 5: Commit** `PageWriting.java`, `PageWritingChecks.java` and `scripts/test_inkwash.sh`. Message: `Read Handwriting: a page's pen strokes still on it (PageWriting): Lasso moves, erasing and washes`.

---

### Task 3: WritingLines, strokes into lines

**Files:**
- Create: `WritingLines.java`, `WritingLinesChecks.java`
- Modify: `scripts/test_inkwash.sh` (`WritingLines` in `sources`, `WritingLinesChecks` in `checks`)

The rule is the probe's (`~/Claudecode/Supernote/inkprobe/app/src/main/java/ing/ell/inkprobe/MainActivity.java`, `recognizeLines`), which read the owner's pages right.

- [ ] **Step 1: Write the failing checks**

```java
package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;

/** Read Handwriting: strokes grouped into lines as the probe grouped the owner's pages (2026-11-09). */
public final class WritingLinesChecks {
    public static void main(String[] args) {
        List<PageWriting.Stroke> s = new ArrayList<>();
        // Line 1 at y≈100: three letters, a tall one (an l) sitting higher.
        s.add(letter(100, 100, 30)); s.add(letter(140, 100, 30)); s.add(letter(180, 85, 60));
        // Line 2 at y≈200, written next.
        s.add(letter(100, 200, 30)); s.add(letter(140, 200, 30));
        // A dot for line 1 added after line 2 was written.
        s.add(dot(150, 70));
        // An arrow across both lines, drawn last.
        s.add(line(300, 60, 320, 240));
        List<WritingLines.Line> lines = WritingLines.of(s);
        check(lines.size() == 3, "two lines of writing and the arrow: " + lines.size());
        check(lines.get(0).strokes.size() == 4, "line 1 keeps its tall letter and its late dot: " + lines.get(0).strokes.size());
        check(lines.get(1).strokes.size() == 2, "line 2: " + lines.get(1).strokes.size());
        check(lines.get(0).box[1] < lines.get(1).box[1], "top to bottom");
        check(WritingLines.of(new ArrayList<>()).isEmpty(), "no strokes, no lines");
        System.out.println("PASS: strokes group into lines: a tall letter, a late dot and a stray arrow don't break them");
    }
    /** A small loop of height h whose middle is at y. */
    static PageWriting.Stroke letter(float x, float y, float h) {
        int n = 12; float[] xs = new float[n], ys = new float[n]; long[] t = new long[n];
        for (int i = 0; i < n; i++) { double a = 2 * Math.PI * i / n; xs[i] = (float) (x + 10 * Math.cos(a)); ys[i] = (float) (y + h / 2 * Math.sin(a)); t[i] = i * 8; }
        return new PageWriting.Stroke(xs, ys, t);
    }
    static PageWriting.Stroke dot(float x, float y) { return new PageWriting.Stroke(new float[]{x, x + 1}, new float[]{y, y}, new long[]{0, 8}); }
    static PageWriting.Stroke line(float x0, float y0, float x1, float y1) {
        int n = 20; float[] xs = new float[n], ys = new float[n]; long[] t = new long[n];
        for (int i = 0; i < n; i++) { xs[i] = x0 + (x1 - x0) * i / (n - 1); ys[i] = y0 + (y1 - y0) * i / (n - 1); t[i] = i * 8; }
        return new PageWriting.Stroke(xs, ys, t);
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
```

- [ ] **Step 2: Run. Expected: compile failure, no `WritingLines`.**

- [ ] **Step 3: Implement `WritingLines.java`**

```java
package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read Handwriting: a page's strokes as lines of writing, each read on its own (the probe, 2026-11-09: a page read as one
 * block gave "Hellman"; line by line, the owner's seven lines came out nearly word for word). Follows the order of
 * writing: a stroke starts a new piece when it begins more than {@link #BACK} letters left of the stroke before's right
 * edge, or its middle is more than {@link #JUMP} letters above or below that stroke's; then a piece joins an earlier line
 * whose middle is within a letter (a dot or crossing added later, a line interrupted). A letter is the median height of
 * the strokes of more than two points. (Height alone split a line whose small letters were 31 px and wandered more.)
 * Pure; checked on the desktop ({@code WritingLinesChecks}).
 */
final class WritingLines {
    static final float BACK = 4, JUMP = 2;
    /** Without strokes to measure, a letter's height in page px. */
    static final float LETTER = 50;

    static final class Line {
        final List<PageWriting.Stroke> strokes = new ArrayList<>();
        /** left, top, right, bottom, page px. */
        final float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        float middle;
        void add(List<PageWriting.Stroke> more) {
            strokes.addAll(more);
            for (PageWriting.Stroke s : more) { float[] b = box(s); box[0] = Math.min(box[0], b[0]); box[1] = Math.min(box[1], b[1]); box[2] = Math.max(box[2], b[2]); box[3] = Math.max(box[3], b[3]); }
            middle = middle(strokes);
        }
    }

    static List<Line> of(List<PageWriting.Stroke> strokes) {
        List<Float> heights = new ArrayList<>();
        for (PageWriting.Stroke s : strokes) if (s.size() > 2) { float[] b = box(s); heights.add(b[3] - b[1]); }
        float letter = heights.isEmpty() ? LETTER : median(heights);
        List<List<PageWriting.Stroke>> pieces = new ArrayList<>();
        float[] last = null;
        float lastMiddle = 0;
        for (PageWriting.Stroke s : strokes) {
            float[] b = box(s);
            float m = middle(Collections.singletonList(s));
            if (last == null || b[0] < last[2] - BACK * letter || Math.abs(m - lastMiddle) > JUMP * letter) pieces.add(new ArrayList<>());
            pieces.get(pieces.size() - 1).add(s);
            last = b;
            lastMiddle = m;
        }
        List<Line> lines = new ArrayList<>();
        for (List<PageWriting.Stroke> piece : pieces) {
            float m = middle(piece);
            Line home = null;
            for (Line l : lines) if (Math.abs(m - l.middle) < letter) home = l;
            if (home == null) { home = new Line(); lines.add(home); }
            home.add(piece);
        }
        Collections.sort(lines, (a, b) -> Float.compare(a.middle, b.middle));
        return lines;
    }

    static float[] box(PageWriting.Stroke s) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int k = 0; k < s.size(); k++) { b[0] = Math.min(b[0], s.x[k]); b[1] = Math.min(b[1], s.y[k]); b[2] = Math.max(b[2], s.x[k]); b[3] = Math.max(b[3], s.y[k]); }
        return b;
    }
    /** The median height of the strokes' points. */
    static float middle(List<PageWriting.Stroke> strokes) {
        List<Float> ys = new ArrayList<>();
        for (PageWriting.Stroke s : strokes) for (float y : s.y) ys.add(y);
        return median(ys);
    }
    static float median(List<Float> v) { List<Float> c = new ArrayList<>(v); Collections.sort(c); return c.get(c.size() / 2); }

    private WritingLines() {}
}
```

- [ ] **Step 4: Run. Expected: PASS.** If the late dot lands in its own line, the dot's middle (70) is 30 from line 1's middle. That is within a letter (30 here), so `<` must hold. Leave the code as written; if it fails, raise the dot to y 75 in the check rather than loosening the rule.

- [ ] **Step 5: Commit.** Message: `Read Handwriting: strokes into lines (WritingLines), the probe's rule`.

---

### Task 4: Transcript, the per-notebook file and search matching

**Files:**
- Create: `Transcript.java`, `TranscriptChecks.java`
- Modify: `scripts/test_inkwash.sh`

- [ ] **Step 1: Write the failing checks**

```java
package io.github.mpdairy.monopaint;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/** Read Handwriting: a notebook's transcript written and read back, and search over it. */
public final class TranscriptChecks {
    public static void main(String[] args) throws Exception {
        Transcript t = new Transcript();
        t.put(0xABCL, Arrays.asList(
            new Transcript.Line(new float[]{10, 20, 300, 60}, new String[]{"When I was a the bed I would", "When I was a the kid I would", "When I was a thee kid I would"}),
            new Transcript.Line(new float[]{10, 80, 300, 120}, new String[]{"Café\tnoir", "", ""})));
        t.put(0xDEFL, Arrays.asList(new Transcript.Line(new float[]{0, 0, 5, 5}, new String[]{"potatoes", "", ""})));
        File f = File.createTempFile("transcript", ".txt");
        t.write(f);
        Transcript back = Transcript.read(f);
        check(back.pages().size() == 2, "two pages back");
        check(back.lines(0xABCL).get(1).guesses[0].equals("Café noir"), "a tab in a guess is a space: " + back.lines(0xABCL).get(1).guesses[0]);
        check(back.lines(0xABCL).get(0).box[3] == 60, "a line's box back");
        check(Transcript.read(new File(f.getPath() + ".none")).pages().isEmpty(), "no file, an empty transcript");

        check(Transcript.fold("  Café  NOIR ").equals("cafe noir"), "folding: " + Transcript.fold("  Café  NOIR "));
        List<Transcript.Line> kid = back.find(0xABCL, "kid");
        check(kid.size() == 1, "the second guess is searched too");
        check(back.find(0xABCL, "cafe").size() == 1, "accents and capitals ignored");
        check(back.find(0xABCL, "when would").size() == 1, "every word found, in any order");
        check(back.find(0xABCL, "astronaut").isEmpty(), "Nothing found");
        check(back.find(0x999L, "kid").isEmpty(), "a page not read finds nothing");

        back.keepOnly(java.util.Collections.singleton(0xDEFL));
        check(back.pages().size() == 1, "pages no longer in the notebook are dropped");

        File dir = java.nio.file.Files.createTempDirectory("transcripts").toFile();
        File a = Transcript.file(dir, "Notebooks/Journal 1");
        check(a.getParentFile().equals(dir) && !a.getName().contains("/"), "one file a notebook, named safely: " + a.getName());
        check(!Transcript.file(dir, "Notebooks/Journal 2").equals(a), "two notebooks, two files");
        System.out.println("PASS: a notebook's transcript writes and reads back; search ignores case and accents and checks every guess");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
```

- [ ] **Step 2: Run. Expected: compile failure.**

- [ ] **Step 3: Implement `Transcript.java`**

```java
package io.github.mpdairy.monopaint;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read Handwriting's transcript of one notebook (docs/superpowers/specs/2026-11-09-read-handwriting-design.md, "The
 * transcripts"): each page's lines of writing, a line's box (page px) and the reader's three guesses, by the page's CRC
 * in the notebook file (pages have no other lasting identity: a page moved keeps its CRC, a page changed gets a new one
 * and is read again). A file of its own in the app's storage, never inside the notebook. Search matches every word of
 * the query in any one guess of a line, capitals and accents ignored. Pure; checked on the desktop ({@code TranscriptChecks}).
 *
 * <p>The file: a first line {@code superdood-transcript 1}; then {@code page <crc hex>} and, under it, a
 * {@code line <l> <t> <r> <b>} line followed by its three guesses, each a tab after it.
 */
final class Transcript {
    static final String HEAD = "superdood-transcript 1";

    static final class Line {
        final float[] box;
        final String[] guesses;
        Line(float[] box, String[] guesses) { this.box = box; this.guesses = guesses; }
    }

    private final Map<Long, List<Line>> pages = new LinkedHashMap<>();

    Collection<Long> pages() { return Collections.unmodifiableSet(pages.keySet()); }
    boolean has(long crc) { return pages.containsKey(crc); }
    List<Line> lines(long crc) { List<Line> l = pages.get(crc); return l == null ? Collections.<Line>emptyList() : l; }
    void put(long crc, List<Line> lines) { pages.put(crc, new ArrayList<>(lines)); }
    /** Drops pages whose CRC is no longer one of the notebook's. */
    void keepOnly(Collection<Long> crcs) { pages.keySet().retainAll(crcs); }

    /** Page {@code crc}'s lines matching {@code query}: every word of it in one guess, folded. */
    List<Line> find(long crc, String query) {
        String[] words = fold(query).split(" ");
        List<Line> found = new ArrayList<>();
        if (words.length == 0 || words[0].isEmpty()) return found;
        for (Line l : lines(crc)) {
            for (String g : l.guesses) {
                String folded = fold(g);
                boolean all = true;
                for (String w : words) if (!folded.contains(w)) { all = false; break; }
                if (all) { found.add(l); break; }
            }
        }
        return found;
    }

    /** Lower case, accents dropped, runs of space one space, trimmed. */
    static String fold(String s) {
        String plain = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    void write(File file) throws IOException {
        StringBuilder s = new StringBuilder(HEAD).append('\n');
        for (Map.Entry<Long, List<Line>> p : pages.entrySet()) {
            s.append("page ").append(Long.toHexString(p.getKey())).append('\n');
            for (Line l : p.getValue()) {
                s.append("line ").append(l.box[0]).append(' ').append(l.box[1]).append(' ').append(l.box[2]).append(' ').append(l.box[3]);
                for (int i = 0; i < 3; i++) s.append('\t').append(i < l.guesses.length ? clean(l.guesses[i]) : "");
                s.append('\n');
            }
        }
        File temp = new File(file.getPath() + ".new");
        java.nio.file.Files.write(temp.toPath(), s.toString().getBytes(StandardCharsets.UTF_8));
        if (!temp.renameTo(file)) { file.delete(); if (!temp.renameTo(file)) throw new IOException("Couldn't keep the transcript"); }
    }
    /** The file's transcript; empty when there is none or it doesn't read (it is made again). */
    static Transcript read(File file) {
        Transcript t = new Transcript();
        if (!file.isFile()) return t;
        try {
            List<String> rows = java.nio.file.Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            if (rows.isEmpty() || !rows.get(0).equals(HEAD)) return new Transcript();
            List<Line> page = null;
            for (String row : rows.subList(1, rows.size())) {
                if (row.startsWith("page ")) { page = new ArrayList<>(); t.pages.put(Long.parseLong(row.substring(5), 16), page); }
                else if (row.startsWith("line ") && page != null) {
                    String[] parts = row.split("\t", -1);
                    String[] b = parts[0].substring(5).split(" ");
                    float[] box = {Float.parseFloat(b[0]), Float.parseFloat(b[1]), Float.parseFloat(b[2]), Float.parseFloat(b[3])};
                    String[] guesses = new String[3];
                    for (int i = 0; i < 3; i++) guesses[i] = i + 1 < parts.length ? parts[i + 1] : "";
                    page.add(new Line(box, guesses));
                }
            }
        } catch (IOException | RuntimeException e) { return new Transcript(); }
        return t;
    }
    private static String clean(String s) { return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' '); }

    /** Notebook {@code path}'s transcript file in {@code dir}: its path, every character a file name can't hold a code. */
    static File file(File dir, String path) {
        StringBuilder name = new StringBuilder();
        for (char c : path.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_') name.append(c);
            else name.append('%').append(String.format(Locale.ROOT, "%04x", (int) c));
        }
        return new File(dir, name + ".txt");
    }
}
```

- [ ] **Step 4: Add `Transcript` to `sources` and `TranscriptChecks` to `checks`. Run. Expected: PASS.**

- [ ] **Step 5: Commit.** Message: `Read Handwriting: a notebook's transcript and search matching (Transcript)`.

---

### Task 5: The ML Kit dependency and HandwritingReader

**Files:**
- Modify: `app/build.gradle`
- Create: `HandwritingReader.java`

The app's `app/build.gradle` has no `dependencies` block. `ndk { abiFilters 'arm64-v8a' }` already keeps only the Manta's processor type, so ML Kit's native code comes in for arm64 alone.

- [ ] **Step 1: Add the dependency** at the end of `app/build.gradle`:

```groovy
// Read Handwriting (docs/superpowers/specs/2026-11-09-read-handwriting-design.md): Google's on-device handwriting reader.
// Closed source; its model is downloaded from Google only once Read Handwriting is switched on.
dependencies {
    implementation 'com.google.mlkit:digital-ink-recognition:18.1.0'
}
```

- [ ] **Step 2: Create `HandwritingReader.java`**

```java
package io.github.mpdairy.monopaint;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.vision.digitalink.DigitalInkRecognition;
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel;
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier;
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer;
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions;
import com.google.mlkit.vision.digitalink.Ink;
import com.google.mlkit.vision.digitalink.RecognitionCandidate;
import com.google.mlkit.vision.digitalink.RecognitionContext;
import com.google.mlkit.vision.digitalink.WritingArea;
import java.util.List;

/**
 * Read Handwriting's reader: Google's ML Kit Digital Ink, the one class that knows it (probe 2026-11-09: it runs on
 * the Manta, which has no Google services; English downloaded in 26 s, about 0.25 s a line). Downloading, deleting and
 * reading block their thread: call them on {@link HandwritingQueue}'s, never the main one. What Google gets: see the
 * spec's "What Google gets" (usage stats, never the ink or its text).
 */
final class HandwritingReader {
    /** shortcut: English only; more languages as a few chips on the row if they're wanted. */
    static final String LANGUAGE = "en-US";
    private final DigitalInkRecognitionModel model;
    private DigitalInkRecognizer recognizer;

    HandwritingReader() {
        DigitalInkRecognitionModelIdentifier id;
        try { id = DigitalInkRecognitionModelIdentifier.fromLanguageTag(LANGUAGE); }
        catch (Exception e) { throw new IllegalStateException(e); }
        model = DigitalInkRecognitionModel.builder(id).build();
    }

    /** Downloads the reader if it isn't here yet (about 20 MB), and gets it ready. */
    void download() throws Exception {
        Tasks.await(RemoteModelManager.getInstance().download(model, new DownloadConditions.Builder().build()));
        if (recognizer == null) recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build());
    }
    boolean downloaded() throws Exception { return Tasks.await(RemoteModelManager.getInstance().isModelDownloaded(model)); }

    /** The downloaded reader deleted (Read Handwriting switched off). */
    void delete() throws Exception {
        if (recognizer != null) { recognizer.close(); recognizer = null; }
        if (downloaded()) Tasks.await(RemoteModelManager.getInstance().deleteDownloadedModel(model));
    }

    /** {@code line}'s three best guesses ("" where fewer), its box the writing area. */
    String[] read(WritingLines.Line line) throws Exception {
        if (recognizer == null) download();
        Ink.Builder ink = Ink.builder();
        for (PageWriting.Stroke s : line.strokes) {
            Ink.Stroke.Builder b = Ink.Stroke.builder();
            for (int k = 0; k < s.size(); k++) b.addPoint(Ink.Point.create(s.x[k], s.y[k], s.t[k]));
            ink.addStroke(b.build());
        }
        RecognitionContext context = RecognitionContext.builder().setPreContext("")
            .setWritingArea(new WritingArea(Math.max(1, line.box[2] - line.box[0]), Math.max(1, line.box[3] - line.box[1]))).build();
        List<RecognitionCandidate> c = Tasks.await(recognizer.recognize(ink.build(), context)).getCandidates();
        String[] out = {"", "", ""};
        for (int i = 0; i < Math.min(3, c.size()); i++) out[i] = c.get(i).getText();
        return out;
    }
}
```

- [ ] **Step 3: Build.** `./gradlew -q :app:assembleRelease`. Expected: it builds. If `com.google.android.gms.tasks.Tasks` can't be found, add `implementation 'com.google.android.gms:play-services-tasks:18.1.0'` to the block. It is the tasks library only; it doesn't need Google services on the device.

- [ ] **Step 4: Commit** `app/build.gradle` and `HandwritingReader.java`. Message: `Read Handwriting: ML Kit Digital Ink and its one reader class`.

---

### Task 6: HandwritingQueue, the background reading

**Files:**
- Create: `HandwritingQueue.java`
- Modify: `DocumentStore.java`: add `File file(String path) throws IOException { return readable(files.drawing(path)); }` next to `page(...)` (about line 270), with the comment `/** Notebook {@code path}'s file, to read (Read Handwriting's queue reads it on its own thread). */`.

Patterns to follow: `NotebookScreens.worker` (about lines 68–73) runs one thread at `THREAD_PRIORITY_LOWEST` with `Cores.quiet(true)`, and decodes inside `synchronized (DocumentCodec.ONE_PAGE)` (about line 555). Do not use `DocumentStore.IO`: it is the save queue.

- [ ] **Step 1: Create `HandwritingQueue.java`**

```java
package io.github.mpdairy.monopaint;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Read Handwriting's reading (docs/superpowers/specs/2026-11-09-read-handwriting-design.md, "When"): one thread at the
 * lowest priority reads, notebook by notebook, the pages whose CRC its transcript hasn't got, drops the pages the
 * notebook no longer has, and keeps the transcript (one file a notebook in {@link #folder}). A saved notebook is queued
 * ({@link #saved}); switching Read Handwriting on queues them all ({@link #readAll}); switching it off deletes every
 * transcript and the downloaded reader ({@link #off}). Results come back on the main thread.
 */
final class HandwritingQueue {
    private static final String TAG = "ReadHandwriting";
    private final DocumentStore store;
    private final File folder;
    private final HandwritingReader reader = new HandwritingReader();
    private final Handler work, main = new Handler(Looper.getMainLooper());
    private final Set<String> waiting = new LinkedHashSet<>();
    private volatile boolean on;

    HandwritingQueue(Context context, DocumentStore store, boolean on) {
        this.store = store;
        this.folder = new File(context.getFilesDir(), "transcripts");
        this.on = on;
        HandlerThread thread = new HandlerThread("Read Handwriting", Process.THREAD_PRIORITY_LOWEST);
        thread.start();
        work = new Handler(thread.getLooper());
    }

    File transcriptFile(String path) { return Transcript.file(folder, path); }

    /** Switched on: the reader downloaded, then every notebook read, newest first; {@code failed} on the main thread if the download fails. */
    void on(Consumer<Exception> failed) {
        on = true;
        work.post(() -> {
            try { reader.download(); }
            catch (Exception e) { Log.w(TAG, "download", e); main.post(() -> failed.accept(e)); return; }
            readAll();
        });
    }
    /** Switched off: nothing more read; every transcript and the downloaded reader deleted. */
    void off() {
        on = false;
        work.removeCallbacksAndMessages(null);
        synchronized (waiting) { waiting.clear(); }
        work.post(() -> {
            File[] files = folder.listFiles();
            if (files != null) for (File f : files) f.delete();
            try { reader.delete(); } catch (Exception e) { Log.w(TAG, "delete", e); }
        });
    }
    /** Notebook {@code path} was saved: read what changed. */
    void saved(String path) { if (on) queue(path); }
    void readAll() {
        store.notebooks((list, error) -> {
            if (list == null) return;
            List<DocumentStore.Notebook> newest = new ArrayList<>(list);
            java.util.Collections.sort(newest, (a, b) -> Long.compare(b.modified, a.modified));
            for (DocumentStore.Notebook n : newest) queue(n.path);
        });
    }
    /** A notebook deleted or renamed: its transcript goes or follows. */
    void deleted(String path) { work.post(() -> transcriptFile(path).delete()); }
    void renamed(String from, String to) { work.post(() -> transcriptFile(from).renameTo(transcriptFile(to))); }

    /** How many pages of {@code paths} aren't read yet, on the main thread. */
    void unread(List<String> paths, Consumer<Integer> answer) {
        work.post(() -> {
            int count = 0;
            for (String path : paths) {
                try {
                    BookCodec.Pages pages = BookCodec.openPages(store.file(path));
                    Transcript t = Transcript.read(transcriptFile(path));
                    for (PackedPage p : pages.pages) if (!t.has(p.crc)) count++;
                } catch (Exception e) { Log.w(TAG, "count " + path, e); }
            }
            int n = count;
            main.post(() -> answer.accept(n));
        });
    }

    private void queue(String path) {
        synchronized (waiting) { if (!waiting.add(path)) return; }
        work.post(() -> {
            synchronized (waiting) { waiting.remove(path); }
            if (on) read(path);
        });
    }

    /** Runs on the queue's thread. */
    private void read(String path) {
        Cores.quiet(true);
        try {
            BookCodec.Pages pages = BookCodec.openPages(store.file(path));
            File file = transcriptFile(path);
            Transcript t = Transcript.read(file);
            Set<Long> crcs = new HashSet<>();
            boolean changed = false;
            for (int i = 0; i < pages.pages.size() && on; i++) {
                PackedPage page = pages.pages.get(i);
                crcs.add(page.crc);
                if (t.has(page.crc)) continue;
                DocumentCodec.PageLog log;
                synchronized (DocumentCodec.ONE_PAGE) { log = DocumentCodec.log(page.bytes()); }
                PageWriting.Tones tones = tones(pages, i, log);
                List<Transcript.Line> lines = new ArrayList<>();
                for (WritingLines.Line line : WritingLines.of(PageWriting.strokes(log.log.strokes(), tones)))
                    lines.add(new Transcript.Line(line.box, reader.read(line)));
                t.put(page.crc, lines);
                changed = true;
            }
            if (!on) return;
            int before = t.pages().size();
            t.keepOnly(crcs);
            if (changed || t.pages().size() != before) { folder.mkdirs(); t.write(file); }
        } catch (Exception | OutOfMemoryError e) {
            Log.w(TAG, "read " + path, e);
        } finally {
            Cores.quiet(false);
        }
    }

    /** Page {@code i}'s picture as tones in page px, its paper lifted to 255; null (no erase check) when the file has none for it. */
    private static PageWriting.Tones tones(BookCodec.Pages pages, int i, DocumentCodec.PageLog log) {
        // shortcut: without a page picture the erase check is skipped (an erased word stays findable until the page changes again).
        if (pages.found == null || i >= pages.found.pictures.length || pages.found.pictures[i] == null) return null;
        try {
            PagePicture p = PagePicture.decode(pages.found.pictures[i]);
            float sx = p.width / (float) log.width, sy = p.height / (float) log.height;
            int[] counts = new int[256];
            for (int y = 0; y < p.height; y++) for (int x = 0; x < p.width; x++) counts[p.tone(x, y)]++;
            int paper = 0;
            for (int g = 1; g < 256; g++) if (counts[g] > counts[paper]) paper = g;
            PageWriting.Tones raw = (x, y) -> {
                int px = Math.max(0, Math.min(p.width - 1, (int) (x * sx))), py = Math.max(0, Math.min(p.height - 1, (int) (y * sy)));
                return p.tone(px, py);
            };
            return PageWriting.onPaper(raw, paper);
        } catch (java.io.IOException e) { return null; }
    }
}
```

Check against the code: `PagePicture.width`/`height` are its fields (`PagePicture.java:40`) and `tone(x, y)` its method (`:307`). `BookCodec.Pages.found.pictures` is `byte[][]` by page index, null where the CRC doesn't match (`BookPictures.java:76`). `DocumentStore.notebooks(Result<List<Notebook>>)` is at `DocumentStore.java:254`. Confirm `Result`'s callback shape there and adjust the lambda. `Cores.quiet(boolean)`: confirm the name where `NotebookScreens.worker` uses it.

- [ ] **Step 2: Build.** `./gradlew -q :app:assembleRelease`. Expected: it builds.

- [ ] **Step 3: Commit** `HandwritingQueue.java` and `DocumentStore.java`. Message: `Read Handwriting: the background reading of changed pages (HandwritingQueue)`.

---

### Task 7: The System Settings row and its first-time words

**Files:**
- Modify: `PaintPreferences.java`, `SystemSettings.java`, `SystemSettingsPanel.java`, `PaintActivity.java`
- Modify: `app/src/test/java/io/github/mpdairy/monopaint/SystemSettingsChecks.java` (its `words()` checks labels)
- Modify: `docs/TEXT-STYLE.md` (word list: **Read Handwriting**)

- [ ] **Step 1: Preferences.** In `PaintPreferences.java`, next to `fridgeAgreed`:

```java
    /** Read Handwriting (System Settings): On only once its first-time words are agreed to. */
    boolean readHandwriting() { return flag("read_handwriting", false); }
    void setReadHandwriting(boolean value) { setFlag("read_handwriting", value); }
```

- [ ] **Step 2: Words.** In `SystemSettings.java`:

```java
    /** Read Handwriting (docs/superpowers/specs/2026-11-09-read-handwriting-design.md): its row, note and first-time words. */
    static final String READ_HANDWRITING = "Read Handwriting", READ_HANDWRITING_NOTE = "For Search on the shelf";
    static final String READ_HANDWRITING_ABOUT = "Handwriting is read on this tablet so the shelf can search it. "
        + "Google's reader is downloaded once, about 20 MB. The writing stays on the tablet; "
        + "Google gets usage stats: device and app details, timings and errors.";
    static final String TURN_ON = "Turn On";
```

`NOT_NOW` is the Fridge's (`Fridge.NOT_NOW`); use it. Extend `SystemSettingsChecks.words()` to include `READ_HANDWRITING`, and check that `READ_HANDWRITING_NOTE.length() <= 30`.

- [ ] **Step 3: The row.** In `SystemSettingsPanel.rows`, after the Fridge's row:

```java
        row(app, list, SystemSettings.READ_HANDWRITING, SystemSettings.READ_HANDWRITING_NOTE, onOff, prefs.readHandwriting() ? 0 : 1, 2, i -> app.readHandwriting(i == 0));
```

- [ ] **Step 4: PaintActivity.** Make the queue in `onCreate`, after `store` is made (about line 165):

```java
        handwriting = new HandwritingQueue(this, store, prefs.readHandwriting());
```

Add the field `HandwritingQueue handwriting;`, then:

```java
    /**
     * Read Handwriting switched on or off from System Settings. On asks first (its first-time words, as the Fridge's),
     * then downloads the reader and reads every notebook; Off deletes every transcript and the reader. Not Now leaves it Off.
     */
    void readHandwriting(boolean on) {
        if (!on) { prefs.setReadHandwriting(false); handwriting.off(); notebooks.searchChanged(); return; }
        if (prefs.readHandwriting()) return;
        showDialog(new AlertDialog.Builder(this).setTitle(SystemSettings.READ_HANDWRITING).setMessage(SystemSettings.READ_HANDWRITING_ABOUT)
            .setPositiveButton(SystemSettings.TURN_ON, (d, w) -> {
                prefs.setReadHandwriting(true);
                notebooks.searchChanged();
                handwriting.on(e -> { prefs.setReadHandwriting(false); notebooks.searchChanged(); message("Couldn't download the reader"); });
            })
            .setNegativeButton(Fridge.NOT_NOW, (d, w) -> systemSettingsAgain()));
    }
```

`systemSettingsAgain()` closes and reopens the System Settings popup, so its chip shows Off again. Look at `systemSettings(View)` (about `PaintActivity.java:1378-1391`): keep the popup in a field, dismiss it, and call `systemSettings(anchor)` again with the same anchor. If the popup closes on any chip tap anyway, `systemSettingsAgain()` can be empty; check by reading `row(...)`. `notebooks.searchChanged()` is added in Task 10; until then, leave it as an empty method in `NotebookScreens`.

- [ ] **Step 5: Run `SystemSettingsChecks` and build. Expected: PASS, and it builds.**

- [ ] **Step 6: Commit** the five files and `docs/TEXT-STYLE.md`. Message: `Read Handwriting: its System Settings row, asking first as the Fridge does`.

---

### Task 8: Fed from saves; deletes and renames followed

**Files:**
- Modify: `PaintActivity.java`

- [ ] **Step 1: Saves.** In `saved(DrawingBook savedBook, DocumentStore.Snapshot snapshot, String path)` (about line 2056), as its last line:

```java
        handwriting.saved(path);
```

- [ ] **Step 2: Deletes.** In `deleteNotebook`'s store callback, next to `AutoExport.delete(path);` (about line 2433):

```java
                handwriting.deleted(path);
```

Page deletes need nothing: the next save of that notebook drops pages whose CRC is gone (`keepOnly`).

- [ ] **Step 3: Renames.** In `renameNotebook`'s callback, next to `AutoExport.rename(from, renamed);` (about line 2410):

```java
                handwriting.renamed(from, renamed);
```

- [ ] **Step 4: Build. Commit.** Message: `Read Handwriting: a saved notebook read again; a deleted or renamed one's transcript follows`.

---

### Task 9: The search field shared (LabUi.SearchField)

**Files:**
- Modify: `LabUi.java`, `ToolsActivity.java` (`library()` about lines 353–393, `openSearch`/`closeSearch`/`keyboard` about lines 612–634)

The rule (CLAUDE.md, Architecture rules 4–5): the shelf's search must not copy the library's. Move it into one class first; the library keeps its behaviour exactly.

- [ ] **Step 1: Create `LabUi.SearchField`** as a static nested class in `LabUi.java`. Move the field and its close button out of `ToolsActivity.library()`:

```java
    /**
     * A search's field (the Tools page library's, 2026-10-28; the shelf's, Read Handwriting 2026-11-09): one line in a
     * chip's rounded edge, then × Close search. The finds come {@code ms} after typing stops, or at once on the
     * keyboard's Search key (the keyboard put down to see them).
     */
    static final class SearchField {
        final LinearLayout row;
        final android.widget.EditText field;
        private final Runnable now;
        SearchField(Context context, int chip, int pad, int ms, java.util.function.Consumer<String> search, Runnable close) {
            row = row(context);
            field = Theme.style(new android.widget.EditText(context), TypeStyle.CAPTION);
            field.setSingleLine(true);
            field.setHint(ToolsLayout.SEARCH);
            field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
            now = () -> search.accept(field.getText().toString());
            field.setOnEditorActionListener((v, action, event) -> { field.removeCallbacks(now); now.run(); keyboard(false); return true; });
            field.setPadding(dp(context, pad), 0, dp(context, pad), 0);
            field.setBackground(Theme.rounded(context, Theme.PAPER, Theme.FINE, Theme.INK, chip / 2f));
            field.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(android.text.Editable s) { field.removeCallbacks(now); field.postDelayed(now, ms); }
            });
            row.addView(field, new LinearLayout.LayoutParams(0, dp(context, chip), 1));
            LabButton x = LabButton.icon(context, R.drawable.ic_ph_x, "Close search");
            x.setOnClickListener(v -> close.run());
            row.addView(x, after(context, chip, chip, 6));
        }
        void open() { field.requestFocus(); keyboard(true); }
        void clear() { keyboard(false); field.removeCallbacks(now); field.setText(""); }
        void keyboard(boolean up) {
            android.view.inputmethod.InputMethodManager k = (android.view.inputmethod.InputMethodManager) field.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (k == null) return;
            if (up) k.showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            else k.hideSoftInputFromWindow(field.getWindowToken(), 0);
        }
    }
```

- [ ] **Step 2: Use it in `ToolsActivity`.** Replace the inline field-building in `library()` with:

```java
        searchBox = new LabUi.SearchField(this, ToolsLayout.CHIP, layout.chipPad(), SEARCH_MS, query -> { page.search(query); render(); }, () -> { closeSearch(); render(); });
        searchBox.field.setText(page.search());
        searchRow = searchBox.row;
        searchField = searchBox.field;
        swap.addView(searchRow, new FrameLayout.LayoutParams(-1, -1));
```

Make `openSearch()` call `searchBox.open()` in place of `searchField.requestFocus(); keyboard(true);`, and `closeSearch()` call `searchBox.clear()` in place of `keyboard(false); searchField.setText(""); root.removeCallbacks(searchNow);`. Remove `keyboard(boolean)` and `searchNow` if nothing else uses them; grep `ToolsActivity.java` for both before deleting. The field still sets `page.search(...)` once more on clear, as `closeSearch()` did.

- [ ] **Step 3: Run the checks that cover the Tools page** (`ToolsPageChecks`, `ToolsLayoutChecks`, if present in `checks=`), then the full suite: `bash scripts/test_inkwash.sh`. Expected: all PASS. Build.

- [ ] **Step 4: Commit.** Message: `The Tools page library's search field as LabUi.SearchField, to share with the shelf`.

---

### Task 10: Search on the shelf, and marks on the page shown

**Files:**
- Modify: `NotebookScreens.java`, `PaintActivity.java`, `NotebookLayout.java` (only if a new size constant is needed)

Behaviour (spec, "Search on the shelf"):
- **Search button:** only while Read Handwriting is On, a Search button (magnifying glass, `ToolsLayout.SEARCH`) at the shelf bar's right.
- **Tapping it:** the bar's place shows the `LabUi.SearchField`; the grid's place shows results.
- **Results:** one row a matching line, `notebook name · page` (the page's number as the strip numbers it), and the matching guess under it in the CAPTION style. Newest notebooks first, as many rows as fit, with the shelf's page buttons for more.
- **Nothing found** (`ToolsPage.empty()`'s words): shown when nothing matches.
- **Unread pages:** a note under the results, `<n> pages unread`, when any are.
- **Tapping a result:** opens that notebook at that page in the notebook screen, with the matching lines boxed. The boxes go at the next tap on the page.
- **Closing search:** × gives the shelf back.

- [ ] **Step 1: The search state and results** in `NotebookScreens`:

```java
    /** Read Handwriting's search (spec "Search on the shelf"): open, its query, and its rows. */
    private boolean searching;
    private String query = "";
    private final List<Found> found = new ArrayList<>();
    private int unread;
    /** A matching line: its notebook, its page index in the book, the guess that matched and its box. */
    static final class Found {
        final String path; final int page; final String words; final float[] box;
        Found(String path, int page, String words, float[] box) { this.path = path; this.page = page; this.words = words; this.box = box; }
    }
    /** Read Handwriting switched on or off: the shelf's Search shown or not. */
    void searchChanged() { if (!app.prefs.readHandwriting()) { searching = false; found.clear(); } if (mode == Mode.SHELF) build(); }
```

Use the real names for the screen's mode and rebuild (`cover(Mode.PAGES)` and `build()` appear in `showPages`). Find the shelf's mode name in `NotebookScreens` and use it.

- [ ] **Step 2: Searching** runs on `HandwritingQueue`'s thread through a new method there, so no transcript is read on the main thread. Add to `HandwritingQueue`:

```java
    /** Every line of the notebooks in {@code paths} (newest first) matching {@code query}, by page index, on the main thread. */
    void search(List<String> paths, String query, Consumer<List<NotebookScreens.Found>> answer) {
        work.post(() -> {
            List<NotebookScreens.Found> out = new ArrayList<>();
            for (String path : paths) {
                try {
                    BookCodec.Pages pages = BookCodec.openPages(store.file(path));
                    Transcript t = Transcript.read(transcriptFile(path));
                    for (int i = 0; i < pages.pages.size(); i++)
                        for (Transcript.Line l : t.find(pages.pages.get(i).crc, query))
                            out.add(new NotebookScreens.Found(path, i, matched(l, query), l.box));
                } catch (Exception e) { Log.w(TAG, "search " + path, e); }
            }
            main.post(() -> answer.accept(out));
        });
    }
    private static String matched(Transcript.Line l, String query) {
        String[] words = Transcript.fold(query).split(" ");
        for (String g : l.guesses) { String f = Transcript.fold(g); boolean all = true; for (String w : words) if (!f.contains(w)) { all = false; break; } if (all) return g; }
        return l.guesses[0];
    }
```

`paths` is the shelf's notebooks in the shelf's order: the `shelf` list `NotebookScreens` already holds (its `path`s). Results that land on a layer of a stack open its stack, because `showPages(int)` already turns a page index into its stack.

- [ ] **Step 3: The bar's Search, the field, and the rows** in `shelf()`:
  - After the `shelfBar` is made: if `app.prefs.readHandwriting()` and not `searching`, add a `LabButton` with `R.drawable.ic_ph_magnifying_glass` and `ToolsLayout.SEARCH` to `shelfBar.view`'s end. Size it as the library's Search button. Tapping it sets `searching = true`, rebuilds, and calls `searchField.open()`.
  - While `searching`: put a `LabUi.SearchField` in the bar's place. Its search callback stores `query`, then calls `app.handwriting.search(paths, query, list -> { found.clear(); found.addAll(list); build(); })` and `app.handwriting.unread(paths, n -> { unread = n; build(); })`. Its close sets `searching = false`, clears `query` and `found`, and rebuilds.
  - In the grid's place, while `searching`: a column of result rows, each a `LabUi.text(...)` pair (`notebook name · page`, BODY; the matched words, CAPTION, `Theme.GREY_800`). Each row is tappable and calls `app.openFound(f)`. Keep the field when rebuilding, so typing isn't interrupted: build the `SearchField` once per open and re-add the same instance.
  - Page the rows as the shelf pages its tiles: `NotebookLayout` gives how many fit, and the existing page buttons turn them.
  - `ToolsPage.empty()`'s "Nothing found" when the query isn't empty and nothing matched. `unread + " pages unread"` under the rows when `unread > 0`.

Show the notebook's display name as the shelf's tiles show it. Find how `tile(...)` turns a path into its name and use the same.

- [ ] **Step 4: Opening a result with marks.** In `PaintActivity`, add next to the public `openNotebook(String)` (about line 2588):

```java
    /** A search's find (Read Handwriting): its notebook open at its page in the notebook screen, the matching line boxed until the next tap. */
    void openFound(NotebookScreens.Found f) {
        openNotebook(f.path, () -> { notebooks.mark(f.page, f.box); notebooks.showPages(f.page); });
    }
```

In `NotebookScreens`:

```java
    /** A search's boxes over page {@code markPage} (page px), until the next tap on the page. */
    private int markPage = DrawingBook.NONE;
    private final List<float[]> marks = new ArrayList<>();
    void mark(int page, float[] box) { markPage = page; marks.clear(); marks.add(box); }
```

At the end of `drawBig(Canvas canvas, DrawingBook book, RectF inside)`, add:

```java
        if (!marks.isEmpty() && book.valid(markPage) && book.stacks().of(markPage) == viewing) {
            float sx = to.width() / book.width(), sy = to.height() / book.height(), pad = dp(4);
            Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
            box.setStyle(Paint.Style.STROKE); box.setStrokeWidth(dp(Theme.BASE)); box.setColor(Theme.INK);
            for (float[] m : marks) canvas.drawRect(to.left + m[0] * sx - pad, to.top + m[1] * sy - pad, to.left + m[2] * sx + pad, to.top + m[3] * sy + pad, box);
        }
```

Use the book's real page-size accessors (search `DrawingBook` for its width and height) and the real `valid` and `stacks().of` names, which `showPages` already uses. In `PageView.onTouchEvent`, on the first `ACTION_DOWN`, clear the marks (`marks.clear(); markPage = DrawingBook.NONE; invalidate();`) before the gestures run.

- [ ] **Step 5: Build. Commit** `NotebookScreens.java`, `PaintActivity.java` and `HandwritingQueue.java`. Message: `Read Handwriting: Search on the shelf; a find opens its page with the line boxed`.

---

### Task 11: Docs and the full suite

**Files:**
- Modify: `docs/INTEGRATION.md`, `docs/TEXT-STYLE.md`, `README.md` (and the About text, if the app shows one: grep for "About")

- [ ] **Step 1: INTEGRATION.md.** Add a section "Read Handwriting (2026-11-09)":
  - **Files:** the new classes; `DocumentCodec.log`; `DocumentStore.file`; the `PaintActivity` hooks; `LabUi.SearchField` (moved out of `ToolsActivity`).
  - **Dependency:** ML Kit is the app's first Maven dependency.
  - **Engine boundary:** unchanged. Reading is app code; the engine is untouched.
  - **Generic change:** `DocumentCodec.log`.

- [ ] **Step 2: TEXT-STYLE.md.** Add **Read Handwriting**, **Turn On** and "pages unread" to the word list. Note that the shelf's search uses the library's words.

- [ ] **Step 3: README / About.** One line each: Read Handwriting uses Google's ML Kit (closed source), downloaded only when switched on, and Google gets usage stats, never the writing.

- [ ] **Step 4: Run the full suite.** `bash scripts/test_inkwash.sh`. Expected: every check PASS (222 before this plan, plus `PageWritingChecks`, `WritingLinesChecks` and `TranscriptChecks`). Build `./gradlew -q :app:assembleRelease`.

- [ ] **Step 5: Commit** the three docs. Message: `Read Handwriting: docs (INTEGRATION, TEXT-STYLE, README)`.

---

### Task 12: On the Manta (the controller only: agents never use adb)

- [ ] Install the release build, then `cmd package compile -m speed -f ing.ell.mybaddrawings`. Tell the owner the app restarted.
- [ ] In a **scratch notebook** made for the test (never the owner's notebooks), write three lines and a stray mark, and leave the page.
- [ ] Switch Read Handwriting On in System Settings. Expected: the first-time words, then Turn On. Watch `adb logcat -s ReadHandwriting`: the download, then reading.
- [ ] Open Search on the shelf and search a word from the scratch notebook. Expected: its row; a tap opens the page with the line boxed; a tap on the page clears the box.
- [ ] Move a line with the Lasso, erase a word, save. Search again. Expected: the moved line found where it lies now, the erased word not found (when the page's picture is fresh).
- [ ] Switch Read Handwriting Off. Expected: Search gone from the shelf; `run-as` isn't available on the release build, so check from the log that `off()` ran and the transcripts folder emptied (add a log line in `off()` if needed).
- [ ] Report to the owner: what worked, timings, and anything the owner's real pages need to show (accuracy on messy pages).
