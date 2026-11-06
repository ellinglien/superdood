package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Record and Replay ({@link StrokeLog}): a stroke's samples keep through the file's bytes (rounded to half pixels), bytes
 * that don't read give no log, the player feeds every sample once, in order, a chunk of pen time a step; and on a page
 * Undo hides a stroke and Redo shows it, a new stroke drops the undone ones, Clear page hides those before it (Undo
 * brings them back), and the page's file keeps the strokes and reads them again.
 */
public final class StrokeLogChecks {
    static final int W = PageShadeChecks.W, H = PageShadeChecks.H;
    static final String CODE = "ink-pen.M0abc.1";

    public static void main(String[] args) throws IOException {
        Assets.install(Assets.folder(Assets.FOLDER));
        // scripts/test_inkwash.sh: a page with every kind of entry for the version before them (OlderReplayCheck), and its pixels' hash.
        // "older-write-more": a page whose log holds shapes, a picture and a merge, for the version before they were logged.
        if (args.length == 2 && args[0].startsWith("older-write")) {
            ToneDocument d = args[0].equals("older-write-more") ? more(true) : everything();
            java.nio.file.Files.write(java.nio.file.Paths.get(args[1]), file(d));
            int[] tones = new int[W * H];
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) tones[y * W + x] = d.compositeTone(x, y);
            System.out.println(java.util.Arrays.hashCode(tones));
            return;
        }
        roundTrip(); garbage(); versionOne(); fits(); player(); ends(); history(); veil(); file(); keepsWet(); replayRun(); replaysAll(); untouched(); veiledOnlyPens(); settledThere(); replaysMore(); mergedOld(); pixels();
        System.out.println("PASS: Record and Replay: a stroke's brush, size, grey and samples (half pixels, pressure, tilt, timing) keep through the log's bytes; "
                + "bytes that are cut, wrong or too big give no log; at the cap the oldest strokes go, not the new one; the player feeds every sample once, in order, a chunk of pen time a step, each stroke begun and ended once; the pen or a finger landing ends a replay, a resting hand (a palm landing, any contact moving or lifting) doesn't; "
                + "on a page Undo hides a stroke and Redo shows it, a new stroke drops the undone ones, Clear page hides the strokes before it and Undo brings them back; "
                + "a replay shows only what the page still shows, a stroke since erased or moved staying paper; "
                + "the page's file keeps the visible strokes and reads them again, a page without any writes as before; "
                + "logging a stroke leaves what its lift left wet (a Slow wash, a halo, a floating bath) to settle as unlogged; "
                + "Replay page's run draws the strokes again on a blank copy, the page untouched; "
                + "a version 1 log reads as before, one of a version this reader doesn't know reads as none, and the bytes never pass the section's cap (the newest kept); "
                + "pen, finger, eraser (the pen's and a finger's, every kind and one), the smudge tool's, effect brushes and prints over marks, a dirty brush, washes settling between strokes and the Lasso's move, turn, Duplicate, Delete, Cut and Paste replay to the same page, every plane, "
                + "from the page and from its file, Undo hiding the Lasso's entries and Redo showing them; "
                + "a replay, ended part-way or played out, leaves the page, its log and its file as they were, and its copy starts without the page's marks changing by the clock; "
                + "only a log of pen strokes alone plays under the veil, so a stroke erased later shows whole until the eraser comes; "
                + "washes the Manta settles on its worker to their end replay settled to their end; "
                + "Fill in (a shape it starts in, a loop on open paper), shapes (outline, filled, a line, on the ink and on the wash), a placed picture and Merge Down replay to the same page, every plane (Merge Down within rounding), from the page and from its file, Undo hiding the merge; "
                + "Merge Old Layers replays from the layers as they were, opacity too, to the same page; "
                + "a picture too big for its share of the section is kept smaller, an earlier entry still kept, and replays at its place and size");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /**
     * Logging the stroke just finished (DrawingPad does, after its finish) leaves what its lift left wet to settle: a
     * Slow wash, a halo, a floating bath, each held with the page's revision then. The page ends as unlogged.
     */
    static void keepsWet() {
        for (String name : new String[]{"Slow ink wash", "Mold ink", "Sumi"}) {
            ModularBrush b = null;
            for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) b = ModularCode.decode(p.code);
            byte[][][] ends = new byte[2][][];
            for (int logged = 0; logged < 2; logged++) {
                ToneDocument d = InkWashLayers.blank(W, H);
                WatercolorSession session = new WatercolorSession();
                String[] stopped = {null};
                session.log = m -> stopped[0] = m;
                DrawingStroke s = QuickStroke.start(d, b.copy(), 40, 1, session);
                StrokeLog.Recorder rec = new StrokeLog.Recorder(CODE, 40, 1);
                for (int i = 0; i < 60; i++) { float x = 40 + i * 3, y = 60 + (i % 9); s.sample(x, y, .6f, 0, 30, 1000 + 2L * i); rec.add(x, y, .6f, 0, 30, 1000 + 2L * i); if (i % 6 == 5) s.present(); }
                check(s.finish() && session.settling(), name + ": wet after lift");
                if (logged == 1) d.logStroke(rec);
                int ticks = 0;
                while (session.tickDrying(d)) ticks++;
                check(ticks > 2 && stopped[0] == null, name + ": settles on, logged or not: " + ticks + " ticks, " + stopped[0]);
                ends[logged] = ModularStrokeChecks.layers(d);
            }
            check(ModularStrokeChecks.same(ends[0], ends[1]), name + ": the page ends as unlogged");
        }
    }

    /** A page written before version 2 (pen strokes, float size and grey) reads as it did. */
    static void versionOne() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] code = CODE.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.write(1); out.write(1); out.write(code.length); out.write(code, 0, code.length);
        for (float f : new float[]{6.5f, .75f}) { int b = Float.floatToIntBits(f); for (int s = 24; s >= 0; s -= 8) out.write(b >>> s); }
        out.write(2);
        out.write(20); out.write(40); out.write(0); out.write(128); out.write(StrokeLog.NO_TILT); out.write(5);
        out.write(2); out.write(1); out.write(8); out.write(255); out.write(-11); out.write(5);
        StrokeLog log = StrokeLog.decode(out.toByteArray());
        check(log != null && log.strokes().size() == 1, "a version 1 log reads");
        StrokeLog.Entry e = log.strokes().get(0);
        check(e.kind == StrokeLog.PEN && e.code.equals(CODE) && e.px == 6.5 && e.darkness == .75 && e.size() == 2 && e.px(1) == 5.5f && e.py(1) == 9.5f
                && e.dt[1] == 8 && Float.isNaN(e.tiltX(0)) && e.tiltX(1) == -11 && e.pressure(1) == 1, "as a pen stroke, its samples as written");
        check(StrokeLog.decode(new byte[]{3, 0}) == null && StrokeLog.decode(new byte[]{2, 1, 99}) == null, "a version or a kind this reader doesn't know reads as none");
    }
    /** The bytes never pass {@link StrokeLog#MOST_BYTES} (an older reader refuses a longer section): the newest entries that fit. */
    static void fits() {
        StrokeLog log = StrokeLog.NONE;
        String code = CODE + ".".repeat(60);
        for (int k = 1; k <= 20000; k++) { StrokeLog.Recorder r = new StrokeLog.Recorder(code, 4, 1); r.add(k % 200, k / 200, .5f, 0, 0, 0); r.add(k % 200 + 1, k / 200, .5f, 0, 0, 1); log = log.added(r.done(k), k); }
        byte[] bytes = log.encode();
        StrokeLog back = StrokeLog.decode(bytes);
        java.util.List<StrokeLog.Entry> all = log.strokes(), kept = back.strokes();
        check(bytes.length <= StrokeLog.MOST_BYTES && kept.size() > 1000 && kept.size() < all.size(), "past the cap the oldest go: " + bytes.length + " bytes, " + kept.size() + " of " + all.size());
        check(same(kept.get(kept.size() - 1), all.get(all.size() - 1)), "the newest kept");
    }

    static StrokeLog.Entry stroke(long serial, int n, float from) {
        StrokeLog.Recorder r = new StrokeLog.Recorder(CODE, 6.5, .75);
        for (int i = 0; i < n; i++) r.add(from + i * 1.25f, 20 + (i % 7) * .5f, i / (float) n, i % 3 == 0 ? Float.NaN : -12.4f + i, 5, 1000 + i * 8L);
        return r.done(serial);
    }
    static boolean same(StrokeLog.Entry a, StrokeLog.Entry b) {
        return a.kind == b.kind && java.util.Objects.equals(a.code, b.code) && a.kinds == b.kinds && a.px == b.px && a.darkness == b.darkness && java.util.Arrays.equals(a.x, b.x) && java.util.Arrays.equals(a.y, b.y)
                && java.util.Arrays.equals(a.dt, b.dt) && java.util.Arrays.equals(a.pressure, b.pressure) && java.util.Arrays.equals(a.tiltX, b.tiltX) && java.util.Arrays.equals(a.tiltY, b.tiltY);
    }

    static void roundTrip() {
        StrokeLog log = StrokeLog.NONE.added(stroke(1, 40, 3), 1).added(stroke(2, 5, 100.5f), 2);
        check(log.samples() == 45 && log.strokes().size() == 2, "two strokes");
        StrokeLog.Entry first = log.strokes().get(0);
        check(first.dt[0] == 0 && first.dt[1] == 8 && first.x[1] == 9 && first.px(1) == 4.5f, "half pixels, ms since the one before");
        check(Float.isNaN(first.tiltX(0)) && first.tiltX(1) == -11 && first.tiltY(1) == 5 && first.pressure(39) > .97f, "tilt and pressure");
        StrokeLog back = StrokeLog.decode(log.encode());
        check(back != null && back.strokes().size() == 2 && same(first, back.strokes().get(0)) && same(log.strokes().get(1), back.strokes().get(1)), "bytes give the strokes back");
        check(StrokeLog.decode(StrokeLog.NONE.encode()) == StrokeLog.NONE, "an empty log reads empty");
    }

    static void garbage() {
        byte[] good = StrokeLog.NONE.added(stroke(1, 30, 0), 1).encode();
        check(StrokeLog.decode(new byte[0]) == null && StrokeLog.decode(new byte[]{9, 0}) == null, "empty and wrong version");
        for (int cut = 1; cut < good.length; cut += 7) check(StrokeLog.decode(java.util.Arrays.copyOf(good, cut)) == null, "cut at " + cut);
        check(StrokeLog.decode(java.util.Arrays.copyOf(good, good.length + 1)) == null, "extra byte");
        byte[] huge = {2, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x0f};
        check(StrokeLog.decode(huge) == null, "a count too big");
        // At the cap the oldest strokes go, not the new one; a stroke bigger than the cap alone is not kept.
        int half = StrokeLog.MOST_SAMPLES / 2;
        StrokeLog full = StrokeLog.NONE.added(stroke(1, half, 0), 1).added(stroke(2, half, 100), 2);
        check(full.samples() == StrokeLog.MOST_SAMPLES && full.strokes().size() == 2, "a full log");
        StrokeLog next = full.added(stroke(3, 10, 200), 3);
        check(next.strokes().size() == 2 && next.samples() == half + 10 && next.strokes().get(0).serial == 2 && next.strokes().get(1).serial == 3, "the oldest goes for the new");
        check(next.added(stroke(4, StrokeLog.MOST_SAMPLES + 1, 0), 4).samples() == next.samples(), "too big alone");
        // Undone strokes were already dropped, so they don't count against the cap; a marker stays harmless.
        StrokeLog cleared = full.added(StrokeLog.Entry.marker(3), 3).added(stroke(4, 5, 0), 4);
        check(cleared.strokes().size() == 1 && cleared.samples() <= StrokeLog.MOST_SAMPLES, "a clear then a stroke at the cap");
        check(full.upTo(1).strokes().size() == 1 && full.added(stroke(3, 5, 0), 1).strokes().size() == 2, "undone strokes leave first");
    }

    static void player() {
        List<StrokeLog.Entry> list = new ArrayList<>();
        list.add(stroke(1, 40, 0)); list.add(stroke(2, 1, 50)); list.add(stroke(3, 200, 70));
        StrokeLog.Player player = new StrokeLog.Player(list);
        List<String> seen = new ArrayList<>();
        StrokeLog.Sink sink = new StrokeLog.Sink() {
            @Override public void begin(StrokeLog.Entry e) { seen.add("b"); }
            @Override public void sample(StrokeLog.Entry e, int i, long time) { seen.add("s" + list.indexOf(e) + ":" + i + "@" + time); }
            @Override public void end(StrokeLog.Entry e) { seen.add("e"); }
        };
        int steps = 0;
        while (!player.done()) { player.step(100, sink); steps++; check(steps < 100, "ends"); }
        int samples = 0, begins = 0, ends = 0;
        for (String s : seen) { if (s.equals("b")) begins++; else if (s.equals("e")) ends++; else samples++; }
        check(samples == 241 && begins == 3 && ends == 3, "every sample once, every stroke begun and ended once");
        check(seen.get(0).equals("b") && seen.get(1).equals("s0:0@1000") && seen.get(2).equals("s0:1@1008"), "in order, from 1000 ms");
        check(steps >= 15 && steps <= 25, "a chunk of pen time a step: " + steps);
    }

    /** 2026-10-25: a palm resting as the replay began ended it on its next move, so it never showed. */
    static void ends() {
        check(StrokeLog.endsReplay(true, true, 0) && StrokeLog.endsReplay(true, false, 8), "the pen or a finger landing ends it");
        check(!StrokeLog.endsReplay(false, false, 8) && !StrokeLog.endsReplay(false, false, 110) && !StrokeLog.endsReplay(false, true, 0), "a contact moving or lifting doesn't");
        check(!StrokeLog.endsReplay(true, false, FingerTool.PALM_DP + 1), "a palm landing doesn't");
    }

    static StrokeLog.Recorder rec(float x) {
        StrokeLog.Recorder r = new StrokeLog.Recorder(CODE, 6, .5);
        for (int i = 0; i < 10; i++) r.add(x + i, 40, .5f, Float.NaN, Float.NaN, 100 + i * 5L);
        return r;
    }
    static void stroke(ToneDocument d, int x0) {
        d.begin(0); for (int x = x0; x < x0 + 30; x++) d.setTone(x, 40, 20); check(d.finish(), "marks");
        d.logStroke(rec(x0));
    }

    static void history() {
        ToneDocument d = InkWashLayers.blank(W, H);
        check(d.replay().isEmpty(), "nothing yet");
        stroke(d, 10); stroke(d, 50);
        check(d.replay().size() == 2, "two strokes");
        check(d.undo() && d.replay().size() == 1 && d.replay().get(0).x[0] == 20, "Undo hides the last");
        check(d.redo() && d.replay().size() == 2, "Redo shows it");
        check(d.undo(), "undo again");
        stroke(d, 90);
        check(d.replay().size() == 2 && d.replay().get(1).x[0] == 180, "a new stroke drops the undone one");
        check(!d.redo() && d.replay().size() == 2, "no Redo left");
        check(d.clearPage() && d.replay().isEmpty(), "Clear page hides them");
        stroke(d, 120);
        check(d.replay().size() == 1, "a stroke after the clear");
        check(d.undo() && d.undo() && d.replay().size() == 2, "Undo of the stroke and the clear brings the earlier two back");
        check(d.redo() && d.redo() && d.replay().size() == 1, "Redo clears and draws again");
        // A stroke that changed nothing is logged, tied to the step before; Live pages' probe still names the stroke that made it.
        d.logStroke(rec(5), false);
        check(d.replay().size() == 2 && !d.replay().get(1).stepped && d.newestStroke() == d.replay().get(0), "a stroke that changed nothing logged, not the newest step's");
    }

    /**
     * 2026-10-26, the owner: "Replay was cool but there was a random blob (a previous drawing) overtop that wasn't from
     * that page". The log kept a stroke whose marks the eraser or the Lasso (not logged then) took away or moved; the
     * replay laid it again where it was drawn. Shown through {@link GhostPlane#offMarks}, the replay shows the paper
     * wherever the page shows no mark, and the page's own marks as they are drawn.
     */
    static void veil() {
        ToneDocument d = InkWashLayers.blank(W, H);
        stroke(d, 10); stroke(d, 50);
        // The eraser takes the first stroke away: the page changes, the log doesn't.
        d.begin(0); d.paintSpan(10, 40, 40, ToneDocument.ERASE); check(d.finish(), "erased");
        check(d.replay().size() == 2, "the log still holds the erased stroke");
        ToneDocument.Snapshot page = d.layerSnapshot();
        ToneDocument copy = new ToneDocument(page);
        check(copy.clearPage(), "a blank copy");
        copy.begin(0);
        for (StrokeLog.Entry e : d.replay()) for (int i = 0; i < e.size(); i++) copy.setTone(Math.round(e.px(i)), Math.round(e.py(i)), 20);
        check(copy.finish(), "replayed");
        GhostPlane.Planes veil = GhostPlane.offMarks(page);
        java.util.function.IntBinaryOperator shown = (x, y) -> copy.shownTone(x, y, null, 0, veil == null ? null : veil.overTones, veil == null ? null : veil.overAlpha);
        for (int x = 10; x < 20; x++) check(shown.applyAsInt(x, 40) == 255, "the erased stroke doesn't show again at " + x + ": " + shown.applyAsInt(x, 40));
        for (int x = 50; x < 60; x++) check(shown.applyAsInt(x, 40) == d.shownTone(x, 40, null, 0, null, null) && shown.applyAsInt(x, 40) < 128, "the page's stroke shows as drawn at " + x);
        check(shown.applyAsInt(70, 40) == 255 && shown.applyAsInt(5, 5) == 255, "paper elsewhere");
    }

    /** Replay page's run ({@link ReplayRun}, the Manta's and the browser's): a blank copy, the strokes drawn again on it a step at a time, the page untouched. */
    static void replayRun() {
        check(ReplayRun.of(InkWashLayers.blank(W, H), 1, new WatercolorSession()) == null, "a page with no strokes has nothing to replay");
        ToneDocument d = InkWashLayers.blank(W, H);
        d.begin(0); for (int x = 10; x < 40; x++) d.setTone(x, 40, 20); check(d.finish(), "marks");
        StrokeLog.Recorder r = new StrokeLog.Recorder(ModularCode.encode(ToolSlotsChecks.old(0).parts), 6, 1);
        for (int i = 0; i < 30; i++) r.add(10 + i, 40, .8f, Float.NaN, Float.NaN, 100 + i * 20L);
        d.logStroke(r);
        long revision = d.revision();
        ReplayRun run = ReplayRun.of(d, 1, new WatercolorSession());
        check(run.copy != d && run.copy.compositeTone(25, 40) == 255, "the copy starts blank");
        boolean done = false;
        for (int k = 0; k < 10 && !done; k++) done = run.step();
        check(done && run.copy.compositeTone(25, 40) < 200 && d.revision() == revision && run.original == d, "the stroke drawn again on the copy, the page untouched");
    }

    static void file() throws IOException {
        ToneDocument d = InkWashLayers.blank(W, H);
        byte[] plain = DrawingBook.encode(d);
        stroke(d, 10); stroke(d, 50);
        d.undo();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocumentCodec.write(out, d.layerSnapshot());
        ToneDocument back = DocumentCodec.read(new ByteArrayInputStream(out.toByteArray()));
        check(back.replay().size() == 1 && same(d.replay().get(0), back.replay().get(0)), "the visible stroke is in the file");
        // After a read the strokes are the page's own, before any step: new strokes follow them, Undo takes only those.
        stroke(back, 90);
        check(back.replay().size() == 2 && back.undo() && back.replay().size() == 1, "new strokes follow the loaded ones");
        check(java.util.Arrays.equals(plain, DrawingBook.encode(InkWashLayers.blank(W, H))), "a page without strokes writes as before");
    }

    /**
     * The owner, 2026-11-03: a page fully redrawn from its log, "anything that a brush does to affect other stuff on the
     * page" too. Strokes of the pen, a finger, the eraser (the pen's, a finger's, one kind), the smudge tool's three,
     * effect brushes and prints that work the marks already there (a knife, Newsprint, Photocopier, Letterpress, Cracks,
     * Clear water, a dirty brush twice), washes left to settle a few ticks or to the end, and the Lasso's move, turn,
     * Duplicate, Delete, Cut and Paste, each made as the drawing screens make them ({@link StrokeStart#start},
     * {@link LassoPlanes}, {@link LassoPieces}), replayed on a blank copy ({@link ReplayRun}): the same page, every
     * plane; from the page's file too. Samples on the log's own grid (half pixels, pressure in 255ths, whole degrees,
     * whole ms from 1000), so rounding takes nothing away.
     */
    static void replaysAll() throws IOException {
        ToneDocument d = everything();
        java.util.List<StrokeLog.Entry> log = d.replay();
        int lifts = 0, places = 0, ticked = 0;
        for (StrokeLog.Entry e : log) { if (e.kind == StrokeLog.LIFT) lifts++; if (e.kind == StrokeLog.PLACE) places++; if (e.ticks > 0) ticked++; }
        check(log.size() == 24 + 4 + 5 && lifts == 4 && places == 5, "every stroke, lift and placing logged: " + log.size() + ", " + lifts + " lifts, " + places + " placings");
        check(ticked >= 2, "drying ticks between entries kept: " + ticked);
        byte[][] page = LassoChecks.whole(d);
        check(Arrays.deepEquals(page, replayed(d)), "the page replays to the same page, every plane: " + differing(page, replayed(d)) + " bytes differ");
        // Undo hides the Paste's placing, Redo shows it.
        check(d.undo() && d.replay().size() == log.size() - 1 && d.redo() && d.replay().size() == log.size(), "Undo and Redo hide and show the Lasso's entries");
        ToneDocument back = DocumentCodec.read(new ByteArrayInputStream(file(d)));
        check(back.replay().size() == log.size(), "the file keeps every entry");
        for (int i = 0; i < log.size(); i++) check(same(log.get(i), back.replay().get(i)), "entry " + i + " as kept");
        check(Arrays.deepEquals(page, replayed(back)), "the page read from its file replays to the same page");
    }
    static byte[] file(ToneDocument d) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); DocumentCodec.write(out, d.layerSnapshot()); return out.toByteArray(); }
    /** {@link #replaysAll}'s page: every kind of entry, as the drawing screens make them. */
    static ToneDocument everything() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession session = new WatercolorSession();
        Brush pen = ToolSlotsChecks.old(0);
        // Marks to work: lines, charcoal, washes (one left to settle a few ticks, one to the end).
        draw(d, session, pen, 6, 1, 0, "1", false, false, 30, 10, 40, 170);
        draw(d, session, pen, 10, .8, 0, "1", false, true, 60, 10, 70, 170);
        draw(d, session, preset("Soft charcoal"), 14, 1, 0, "2", false, false, 100, 10, 95, 170);
        draw(d, session, preset("Slow ink wash"), 18, .7, 0, "3", false, false, 140, 10, 150, 170);
        for (int k = 0; k < 4; k++) session.tickDrying(d);
        draw(d, session, preset("Grey wash"), 18, .6, 0, "4", false, false, 180, 10, 175, 170);
        while (session.tickDrying(d)) { }
        draw(d, session, preset("Dot tone"), 20, .5, 0, "5", false, false, 200, 10, 215, 170);
        // What works the marks there: the eraser, the smudge tool's three, effect brushes and prints, a dirty brush twice.
        draw(d, session, null, 12, 0, 0, null, false, false, 10, 20, 230, 25);
        draw(d, session, null, 8, 0, ToneDocument.KIND_PEN, null, false, false, 10, 35, 230, 40);
        float y = 50;
        for (String name : ToolSlots.SMUDGE_TOOLS) { draw(d, session, preset(name), 16, 1, 0, ToolSlots.SMUDGE_KEY, true, false, 10, y, 230, y + 4); y += 8; }
        // Wet paint worked while wet: a knife and a dirty brush through it.
        draw(d, session, preset("Wet gouache"), 16, .5, 0, "7", false, false, 120, 70, 230, 75);
        draw(d, session, preset("Palette knife"), 14, .8, 0, "6", false, false, 110, 68, 230, 78);
        draw(d, session, preset("Dirty brush"), 14, .8, 0, "8", false, false, 110, 74, 230, 70);
        y = 80;
        for (String name : new String[]{"Palette knife", "Newsprint", "Photocopier", "Letterpress", "Cracking paint", "Clear water", "Dirty brush", "Dirty brush"}) {
            draw(d, session, preset(name), 14, .8, 0, "6", false, false, 10, y, 230, y + 6);
            y += 9;
        }
        // A finger's eraser, as InkWash.fingerEraser starts it (unmirrored).
        session.settleBefore(d, null);
        feed(new EraseBothStroke(d, 9, 0), StrokeLog.Recorder.fingerEraser(9, 0), d, 10, 165, 230, 160);
        LassoPieces lasso = new LassoPieces();
        // Moved and turned.
        lasso.floatPiece(LassoPlanes.lift(d, new float[]{10.3f, 10, 90, 12.7f, 85, 70, 12, 66}, 4));
        lasso.floating.grab(FloatingSelection.MOVE); lasso.floating.drag(FloatingSelection.MOVE, 0, 0, 100, 80); lasso.floating.release();
        lasso.floating.angle = .3; lasso.floating.scale = 1.25f;
        check(lasso.place(d), "moved and turned");
        // Duplicate, the copy dragged.
        lasso.floatPiece(LassoPlanes.lift(d, new float[]{150, 100, 230, 100, 230, 170, 150, 170}, 4));
        FloatingSelection copy = lasso.duplicate(d);
        copy.grab(FloatingSelection.MOVE); copy.drag(FloatingSelection.MOVE, 0, 0, -120, -60); copy.release();
        check(lasso.place(d), "duplicated");
        // Delete.
        lasso.floatPiece(LassoPlanes.lift(d, new float[]{0, 0, 60, 0, 60, 50, 0, 50}, 4));
        check(lasso.delete(d), "deleted");
        // Cut, a stroke, then Paste.
        lasso.floatPiece(LassoPlanes.lift(d, new float[]{100, 100, 180, 100, 180, 180, 100, 180}, 4));
        check(lasso.cut(d), "cut");
        draw(d, session, pen, 6, 1, 0, "1", false, false, 10, 170, 230, 175);
        lasso.floatPiece(lasso.pasted(d));
        lasso.floating.grab(FloatingSelection.MOVE); lasso.floating.drag(FloatingSelection.MOVE, 0, 0, -90, -95); lasso.floating.release();
        check(lasso.place(d), "pasted");
        while (session.tickDrying(d)) { }
        return d;
    }
    /**
     * 2026-11-03, a crash mid-replay (a fade's tick on the replay's copy, a stroke of it open): a replay never writes the
     * page, its log or its file, ended part-way or played out; its copy starts without the page's marks changing by the
     * clock (they are the page's: the copy's are its replayed strokes' own).
     */
    static void untouched() throws IOException {
        ToneDocument d = everything();
        FadingChecks.draw(d, FadingChecks.brush(PartChoice.FADE, 60), FadingChecks.line(20, 200, 90));
        check(d.fading(), "the page has a mark fading by the clock");
        byte[][] planes = LassoChecks.whole(d);
        byte[] bytes = file(d);
        long revision = d.revision();
        java.util.List<StrokeLog.Entry> log = new ArrayList<>(d.replay());
        for (int stop = 1; stop <= 2; stop++) {
            ReplayRun run = ReplayRun.of(d, 1, ReplayRun.session(null));
            check(!run.copy.fading(), "the copy starts without the page's fading marks");
            if (stop == 1) run.step(); else for (int k = 0; k < 10000 && !run.step(); k++) { }
            run.finish();
            check(Arrays.deepEquals(planes, LassoChecks.whole(d)) && d.revision() == revision && d.replay().equals(log) && Arrays.equals(bytes, file(d)),
                    "a replay " + (stop == 1 ? "ended part-way" : "played out") + " leaves the page, its log and its file as they were");
        }
    }
    /**
     * The owner, 2026-11-03, on Replay with the eraser logged: "the initial stroke i drew, it showed up with eraser marks".
     * The veil (paper wherever the page shows no mark) hid where the eraser would take the stroke later. Only a log of
     * pen strokes alone, whose eraser wasn't logged, plays under it.
     */
    static void veiledOnlyPens() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession session = new WatercolorSession();
        draw(d, session, ToolSlotsChecks.old(0), 8, 1, 0, "1", false, false, 20, 90, 220, 90);
        ReplayRun pens = ReplayRun.of(d, 1, ReplayRun.session(null));
        check(pens.veil != null, "a log of pen strokes alone plays under the veil");
        draw(d, session, null, 20, 0, 0, null, false, false, 120, 60, 120, 120);
        ReplayRun all = ReplayRun.of(d, 1, ReplayRun.session(null));
        check(all.veil == null, "a log with the eraser plays without it");
        // The first stroke alone (one entry played), as a step of a long log shows it: whole, the eraser still to come.
        ToneDocument copy = all.copy;
        StrokeLog.Entry first = d.replay().get(0);
        DrawingStroke s = ReplayRun.stroke(copy, first, ReplayRun.session(null));
        for (int i = 0; i < first.size(); i++) s.sample(first.px(i), first.py(i), first.pressure(i), first.tiltX(i), first.tiltY(i), 1000 + i);
        s.finish();
        check(copy.compositeTone(120, 90) < 128 && d.compositeTone(120, 90) > 200, "where the eraser takes it later, the first stroke shows whole: " + copy.compositeTone(120, 90));
    }
    /**
     * The Manta settles on a worker ({@link SettleWorker}; {@link WatercolorSession#tick}), whose ticks aren't
     * {@link WatercolorSession#tickDrying}'s: the owner's page of 2026-11-03 logged no ticks at all, so its washes replayed
     * laid unsettled. A wash settled there to its end is logged as such ({@link StrokeLog#MOST_TICKS}) and replays settled.
     */
    static void settledThere() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession session = SettleWorkerChecks.session(true, false);
        for (String name : new String[]{"Slow ink wash", "Grey wash"}) {
            draw(d, session, preset(name), 18, .7, 0, "3", false, false, 30, 40, 200, 120);
            check(session.settling(), name + " settles after its lift");
            SettleWorkerChecks.run(session, d, Integer.MAX_VALUE);
        }
        draw(d, session, ToolSlotsChecks.old(0), 6, 1, 0, "1", false, false, 20, 80, 220, 90);
        java.util.List<StrokeLog.Entry> log = d.replay();
        check(log.get(1).ticks == StrokeLog.MOST_TICKS && log.get(2).ticks == StrokeLog.MOST_TICKS, "settled to its end: " + log.get(1).ticks + ", " + log.get(2).ticks);
        // The paced ticks (WatercolorSession.tick) settle a Slow wash a few pixels otherwise than tickDrying's: within 1 in 10,000.
        byte[][] page = LassoChecks.whole(d);
        int differ = differing(page, replayed(d)), all = 0;
        for (byte[] plane : page) all += plane.length;
        check(differ * 10000L < all, "replays to the page settled: " + differ + " of " + all + " bytes differ");
    }
    /**
     * Fill in (a stroke, logged as any), shapes ({@link StrokeStart#shape} and {@link ShapeStroke} as the browser draws
     * one), a placed picture ({@link PictureImport#place}) and Merge Down ({@link InkWashLayers#merge} with the layer
     * above), replayed on a blank copy: without Merge Down the same page, every plane, byte for byte; with it within
     * rounding (the layer above is kept flattened, a byte a pixel, and merged again over the replayed layers). From the
     * page's file too; Undo hides the merge and Redo shows it.
     */
    static void replaysMore() throws IOException {
        ToneDocument exact = more(false);
        check(Arrays.deepEquals(LassoChecks.whole(exact), replayed(exact)), "Fill in, shapes and a picture replay to the same page, every plane: " + differing(LassoChecks.whole(exact), replayed(exact)) + " bytes differ");
        ToneDocument d = more(true);
        List<StrokeLog.Entry> log = d.replay();
        int shapes = 0, pictures = 0, merges = 0;
        for (StrokeLog.Entry e : log) { if (e.kind == StrokeLog.SHAPE) shapes++; if (e.kind == StrokeLog.PICTURE) pictures++; if (e.kind == StrokeLog.MERGE) merges++; }
        check(shapes == 5 && pictures == 1 && merges == 1, "every shape, picture and merge logged: " + shapes + ", " + pictures + ", " + merges);
        byte[][] page = LassoChecks.whole(d);
        int differ = differing(page, replayed(d)), all = 0;
        for (byte[] plane : page) all += plane.length;
        check(differ * 1000L < all, "with Merge Down, the same page within rounding: " + differ + " of " + all + " bytes differ");
        check(d.undo() && d.undo() && d.replay().size() == log.size() - 2 && d.redo() && d.redo() && d.replay().size() == log.size(), "Undo and Redo hide and show the merge");
        ToneDocument back = DocumentCodec.read(new ByteArrayInputStream(file(d)));
        check(Arrays.equals(d.layerSnapshot().log.encode(), back.layerSnapshot().log.encode()), "the file keeps every entry as it was");
        check(differing(page, replayed(back)) == differ, "the page read from its file replays the same");
    }
    /** {@link #replaysMore}'s page: Fill in, shapes and a picture as the drawing screens make them, then ({@code down}) Merge Down and a stroke after. */
    static ToneDocument more(boolean down) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession session = new WatercolorSession();
        Brush pen = ToolSlotsChecks.old(0), fill = preset("Fill in");
        // A box of ink lines and Fill in started inside it; a loop Fill in draws on open paper.
        for (float[] l : new float[][]{{20, 20, 100, 20}, {100, 20, 100, 90}, {100, 90, 20, 90}, {20, 90, 20, 20}}) draw(d, session, pen, 4, 1, 0, "1", false, false, l[0], l[1], l[2], l[3]);
        draw(d, session, fill, 10, .6, 0, "2", false, false, 50, 50, 60, 55);
        float[] loop = new float[96];
        for (int i = 0; i < 48; i++) { loop[2 * i] = 170 + 25 * (float) Math.cos(i * Math.PI / 23); loop[2 * i + 1] = 50 + 25 * (float) Math.sin(i * Math.PI / 23); }
        path(d, session, fill, 8, .4, loop);
        // Shapes: an outline, filled ones, a line, and one of a wash brush (on the wash, as its strokes go).
        shape(d, session, pen, ToolSettings.Shape.RECTANGLE, false, 5, .9, 30, 110, 90, 160);
        shape(d, session, pen, ToolSettings.Shape.OVAL, true, 3, .5, 60, 100, 120, 175);
        shape(d, session, pen, ToolSettings.Shape.LINE, false, 7, 1, 10, 175, 230, 95);
        shape(d, session, pen, ToolSettings.Shape.CIRCLE, true, 3, .3, 200, 150, 215, 160);
        shape(d, session, preset("Grey wash"), ToolSettings.Shape.SQUARE, true, 4, .6, 120, 30, 150, 70);
        // A picture placed (some of it clear), a stroke over it.
        int[] greys = new int[60 * 40];
        for (int i = 0; i < greys.length; i++) greys[i] = (i % 60 + i / 60) % 7 == 0 ? -1 : (i % 60) * 4;
        session.settleNow(d);
        check(PictureImport.place(d, 150, 100, 60, 40, greys), "a picture placed");
        draw(d, session, pen, 6, 1, 0, "1", false, false, 140, 110, 230, 150);
        if (down) {
            ToneDocument up = InkWashLayers.blank(W, H);
            WatercolorSession upper = new WatercolorSession();
            draw(up, upper, preset("Grey wash"), 18, .5, 0, "3", false, false, 10, 170, 230, 120);
            draw(up, upper, pen, 8, .7, 0, "1", false, false, 10, 120, 230, 170);
            while (upper.tickDrying(up)) { }
            // As the drawing screen merges (PaintActivity.layerChange): what settles laid first.
            session.settleNow(d);
            check(InkWashLayers.merge(d, up.layerSnapshot()), "merged down");
            draw(d, session, pen, 6, 1, 0, "1", false, false, 10, 100, 230, 60);
        }
        while (session.tickDrying(d)) { }
        return d;
    }
    /** A shape as the browser draws one ({@code WebEngine.shapeDown}, {@code shapeMove}, {@code shapeEnd}) with {@code brush}'s slot at {@code px} and {@code dark}, dragged about and let go at {@code x1, y1}. */
    static void shape(ToneDocument d, WatercolorSession session, Brush brush, ToolSettings.Shape kind, boolean filled, double px, double dark, float x0, float y0, float x1, float y1) {
        ToolSettings shapes = ToolSettings.defaults(ToolSettings.Tool.SHAPES).shape(kind).filled(filled);
        StrokeStart.shape(d, brush.parts, session);
        ShapeStroke s = new ShapeStroke(d, DrawLayout.shapeOutline(shapes, px), ToolSwatches.tone(dark), x0, y0);
        for (int i = 1; i <= 5; i++) s.preview(x0 + (x1 - x0) * i / 5 + (i < 5 ? 9 : 0), y0 + (y1 - y0) * i / 5);
        StrokeLog.Shape logged = s.logged();
        d.logMade(StrokeLog.Entry.shape(0, ModularCode.encode(brush.parts), logged), s.finish());
    }
    /** A stroke of {@code brush} through {@code xy} (page px pairs), as {@link #draw} draws a line. */
    static void path(ToneDocument d, WatercolorSession session, Brush brush, double px, double dark, float[] xy) {
        ToolSwatches.Look look = new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, px, dark);
        session.holding("2");
        StrokeLog.Recorder rec = StrokeLog.Recorder.of(brush, look, 0, "2", false, false);
        DrawingStroke s = StrokeStart.start(d, brush, look, session, 0);
        for (int i = 0; i < xy.length / 2; i++) {
            float x = Math.round(xy[2 * i] * 2) / 2f, y = Math.round(xy[2 * i + 1] * 2) / 2f, p = 150 / 255f;
            s.sample(x, y, p, Float.NaN, Float.NaN, 1000 + 9L * i); rec.add(x, y, p, Float.NaN, Float.NaN, 1000 + 9L * i);
        }
        d.logStroke(rec, s.finish());
    }
    /**
     * Merge Old Layers (a page with two pairs, one layer at half opacity) replays from those layers as they were before it,
     * though the page has one pair now: the same page, every plane.
     */
    static void mergedOld() {
        ToneDocument d = InkWashLayers.blank(W, H);
        check(InkWashLayers.add(d, true), "a second pair");
        d.setLayerOpacity(2, 50);
        WatercolorSession session = new WatercolorSession();
        draw(d, session, preset("Grey wash"), 18, .7, 0, "3", false, false, 20, 40, 220, 120);
        while (session.tickDrying(d)) { }
        draw(d, session, ToolSlotsChecks.old(0), 6, 1, 0, "1", false, false, 20, 120, 220, 40);
        session.settleNow(d);
        check(InkWashLayers.merge(d) && d.layerCount() == 2, "Merge Old Layers");
        draw(d, session, ToolSlotsChecks.old(0), 4, .8, 0, "1", false, false, 20, 80, 220, 80);
        byte[][] page = LassoChecks.whole(d), again = replayed(d);
        check(Arrays.deepEquals(page, again), "Merge Old Layers replays to the same page: " + differing(page, again) + " bytes differ");
    }
    /**
     * A picture too big for {@link StrokeLog#MOST_PIXELS} (a page of noise) is kept every second pixel; the stroke before it
     * is kept too, through the page's file; it replays at its place and size, each kept pixel as it was.
     */
    static void pixels() throws IOException {
        int w = 1000, h = 800;
        java.util.Random dice = new java.util.Random(7);
        int[] greys = new int[w * h];
        for (int i = 0; i < greys.length; i++) greys[i] = dice.nextInt(250);
        ToneDocument d = InkWashLayers.blank(w, h);
        draw(d, new WatercolorSession(), ToolSlotsChecks.old(0), 6, 1, 0, "1", false, false, 10, 10, 900, 700);
        PictureImport.place(d, 0, 0, w, h, greys.clone());
        StrokeLog.Entry e = d.replay().get(1);
        check(e.kind == StrokeLog.PICTURE && e.picture().greys.step == 2 && e.picture().greys.packed.length <= StrokeLog.MOST_PIXELS, "kept every second pixel: step " + e.picture().greys.step + ", " + e.picture().greys.packed.length + " bytes");
        int[] kept = e.picture().greys();
        check(kept[0] == greys[0] && kept[1] == greys[0] && kept[w + 1] == greys[0] && kept[2 * w + 2] == greys[2 * w + 2], "each kept pixel stands for its square");
        ToneDocument back = DocumentCodec.read(new ByteArrayInputStream(file(d)));
        check(back.replay().size() == 2, "the stroke before it kept too: " + back.replay().size());
        ReplayRun run = ReplayRun.of(back, 4, ReplayRun.session(null));
        for (int k = 0; k < 100 && !run.step(); k++) { }
        check(run.copy.compositeTone(500, 2) == d.compositeTone(500, 2) && run.copy.compositeTone(501, 3) == d.compositeTone(500, 2), "replayed at its place and size");
    }
    static int differing(byte[][] a, byte[][] b) { int n = 0; for (int i = 0; i < a.length; i++) for (int j = 0; j < a[i].length; j++) if (a[i][j] != b[i][j]) n++; return n; }
    static Brush preset(String name) { for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) return p.brush(); throw new AssertionError("no preset " + name); }
    /** {@code page} replayed on a blank copy to its end, with a session of its own, what still settles left to its end: its planes. */
    static byte[][] replayed(ToneDocument page) {
        WatercolorSession session = ReplayRun.session(null);
        ReplayRun run = ReplayRun.of(page, 4, session);
        for (int k = 0; k < 10000 && !run.step(); k++) { }
        while (session.tickDrying(run.copy)) { }
        return LassoChecks.whole(run.copy);
    }
    /**
     * A stroke of {@code brush} (null: the eraser, taking {@code kinds}) holding {@code holder}'s paint, as the drawing
     * screen starts it and logs it (one that changes nothing too), from x0, y0 to x1, y1.
     */
    static void draw(ToneDocument d, WatercolorSession session, Brush brush, double px, double dark, int kinds, String holder, boolean smudge, boolean finger, float x0, float y0, float x1, float y1) {
        ToolSwatches.Look look = new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, px, dark);
        if (holder != null) session.holding(holder);
        StrokeLog.Recorder rec = StrokeLog.Recorder.of(brush, look, kinds, holder, smudge, finger);
        feed(StrokeStart.start(d, brush, look, session, kinds), rec, d, x0, y0, x1, y1);
    }
    static void feed(DrawingStroke s, StrokeLog.Recorder rec, ToneDocument d, float x0, float y0, float x1, float y1) {
        int n = 40;
        for (int i = 0; i < n; i++) {
            float x = Math.round((x0 + (x1 - x0) * i / (n - 1)) * 2) / 2f, y = Math.round((y0 + (y1 - y0) * i / (n - 1)) * 2) / 2f, p = (60 + 4 * i) / 255f;
            float tx = i % 5 == 0 ? Float.NaN : 10 - i, ty = 20;
            long t = 1000 + 9L * i;
            s.sample(x, y, p, tx, ty, t); rec.add(x, y, p, tx, ty, t);
        }
        d.logStroke(rec, s.finish());
    }
}
