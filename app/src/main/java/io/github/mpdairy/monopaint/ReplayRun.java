package io.github.mpdairy.monopaint;

/**
 * One Replay page ({@link StrokeLog}) as it plays, for the Manta's ({@link PageReplay}) and the browser's (web/engine's
 * WebEngine): the page's recorded strokes, Lasso edits, shapes, pictures and merges done again on a blank copy of it, {@link #PEN_MS} of pen time a step at the
 * first speed, for a log of pen strokes alone under a veil of paper wherever the page shows no mark ({@link #veiled}). The page itself is
 * never touched. The caller paces the steps ({@link #STEP_MS}, then {@link #HOLD_MS} to look) and shows the page again
 * at the end. Pure, driven by its caller.
 */
final class ReplayRun {
    /** Wall time between steps, ms; and the pen time each step draws at the first speed, ms (about 5 times as fast as drawn). */
    static final int STEP_MS = 600, PEN_MS = 3000;
    /** The speeds: 1×, 2× and 4× of {@link #PEN_MS}; their menu's width, dp. */
    static final int[] SPEEDS = {1, 2, 4};
    static final int MENU_DP = 120;
    /** How long the finished replay stays, ms. */
    static final long HOLD_MS = 3000;
    static final String NOTHING = "Nothing to replay";
    /** A speed's words: "2×". */
    static String speedWords(int speed) { return speed + "×"; }

    final ToneDocument original, copy;
    /** Over the copy while it plays: the paper wherever the page shows no mark. */
    final GhostPlane.Planes veil;
    private final int speed;
    private final StrokeLog.Player player;
    private final WatercolorSession session;
    private DrawingStroke stroke;

    private ReplayRun(ToneDocument page, int speed, WatercolorSession session) {
        ToneDocument.Snapshot shot = page.layerSnapshot();
        original = page; copy = new ToneDocument(shot); copy.clearPage();
        // The page's marks changing by the clock are its own: the copy's change only as its replayed strokes lay theirs.
        copy.placeFades(java.util.Collections.emptyList());
        // A log with a merge starts on the layers as they were before the first (Merge Old Layers made several one).
        for (StrokeLog.Entry e : page.replay()) if (e.kind == StrokeLog.MERGE) { e.merge().before(copy); break; }
        player = new StrokeLog.Player(page.replay());
        veil = veiled(page.replay()) ? GhostPlane.offMarks(shot) : null;
        // Each eraser pass scrapes Tone's dots by its count on the page: the copy's passes start where the log's first did.
        int erasers = 0;
        for (StrokeLog.Entry e : page.replay()) if (e.kind == StrokeLog.ERASE || e.kind == StrokeLog.ERASE_FINGER) erasers++;
        copy.erasePasses = Math.max(0, page.erasePasses - erasers);
        this.speed = speed; this.session = session;
    }
    /**
     * A replay's own session, its toolbar's swatches {@code swatches}: the drawing screen's (paint held, a wash settling,
     * the tilt) stays as it is, and every replay starts the same.
     */
    static WatercolorSession session(ToolSwatches swatches) { WatercolorSession session = new WatercolorSession(); session.swatches = swatches; return session; }
    /**
     * Whether a replay of {@code entries} shows under the veil: only a log of pen strokes alone, as logs were before the
     * eraser, smudge and Lasso were (2026-11-03), whose marks since erased or moved would come back as blobs. A log with
     * any of those plays every mark as it was, even one the eraser takes later (the owner, 2026-11-03: under the veil "the
     * initial stroke i drew, it showed up with eraser marks").
     */
    static boolean veiled(java.util.List<StrokeLog.Entry> entries) {
        for (StrokeLog.Entry e : entries) if (e.kind != StrokeLog.PEN) return false;
        return true;
    }
    /** {@code page}'s replay at {@code speed}, its strokes drawn with {@code session}; null when it has nothing to replay. */
    static ReplayRun of(ToneDocument page, int speed, WatercolorSession session) { return page == null || page.replay().isEmpty() ? null : new ReplayRun(page, speed, session); }

    /**
     * A replayed stroke ({@link StrokeLog.Entry#brushed} or the eraser's) on {@code document}, as the drawing screen starts
     * it unmirrored ({@link StrokeStart#stroke}; a finger's eraser as {@code InkWash.fingerEraser}); null if its code doesn't read.
     */
    static DrawingStroke stroke(ToneDocument document, StrokeLog.Entry e, WatercolorSession session) {
        if (e.kind == StrokeLog.ERASE_FINGER) { session.settleBefore(document, null); return new EraseBothStroke(document, (float) e.px, e.kinds); }
        if (e.code == null) return StrokeStart.stroke(document, null, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, e.px, 0), session, e.kinds);
        // Keeps Paint: the paint this brush (the tool's or the finger's) held, as the drawing screen picks it.
        if (e.holder != null) session.holding(e.holder);
        return stroke(document, e.code, e.px, e.darkness, session);
    }
    /** Brush {@code code}'s stroke at {@code px} and {@code darkness}, as the drawing screen starts it unmirrored ({@link StrokeStart#stroke}); null if the code doesn't read. */
    static DrawingStroke stroke(ToneDocument document, String code, double px, double darkness, WatercolorSession session) {
        ModularBrush parts = ModularCode.decode(code);
        return parts == null ? null : StrokeStart.stroke(document, Brush.of(parts), new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, px, darkness), session, 0);
    }

    /** The pieces the Lasso's lifts took, by entry played (null for the rest), for their placings ({@link StrokeLog.Place#back}). */
    private final java.util.List<FloatingSelection> pieces = new java.util.ArrayList<>();
    private final StrokeLog.Sink sink = new StrokeLog.Sink() {
        @Override public void begin(StrokeLog.Entry e) {
            // What settled on the page (a wash, a bath, halos, wet paint) between the entry before and this one: so many
            // ticks, or (MOST_TICKS) all of it to its end.
            int ticks = e.ticks == StrokeLog.MOST_TICKS ? 50 * StrokeLog.MOST_TICKS : e.ticks;
            for (int k = 0; k < ticks && session.tickDrying(copy); k++) { }
            FloatingSelection piece = null;
            if (e.kind == StrokeLog.LIFT || e.kind == StrokeLog.PLACE || e.kind == StrokeLog.PICTURE || e.kind == StrokeLog.MERGE) {
                // As the drawing screens lift and put down, place a picture and merge layers: what is still settling laid first.
                session.settleNow(copy);
                if (e.kind == StrokeLog.LIFT) piece = LassoPlanes.lift(copy, e.loop(), e.size());
                else if (e.kind == StrokeLog.PLACE) place(e.place, pieces.size() - e.place.back);
                else if (e.kind == StrokeLog.PICTURE) picture(e.picture());
                else merge(e.merge());
            } else if (e.kind == StrokeLog.SHAPE) shape(e);
            else if (e.kind != StrokeLog.CLEAR) stroke = stroke(copy, e, session);
            pieces.add(piece);
        }
        @Override public void sample(StrokeLog.Entry e, int i, long time) {
            if (stroke != null) stroke.sample(e.px(i), e.py(i), e.pressure(i), e.tiltX(i), e.tiltY(i), time);
        }
        @Override public void end(StrokeLog.Entry e) { if (stroke != null) { stroke.finish(); stroke = null; } }
    };
    /** {@code p} done with the piece lifted by entry {@code from} (none: nothing), through the Lasso's own placing ({@link LassoPlanes#place}). */
    private void place(StrokeLog.Place p, int from) {
        FloatingSelection lifted = p.back > 0 && from >= 0 ? pieces.get(from) : null;
        if (lifted == null) return;
        // The lifted piece itself when it is cut from where it was; a copy (Duplicate's, Paste's) only lays.
        FloatingSelection piece = p.cut ? lifted : lifted.copy();
        piece.centerX = p.centerX; piece.centerY = p.centerY; piece.scale = p.scale; piece.angle = p.angle;
        piece.planes.place(copy, piece, p.drop, p.cut);
    }
    /** A shape laid again as the drawing screens lay it ({@link StrokeStart#shape}, {@link ShapeStroke#lay}); nothing if its brush's code doesn't read. */
    private void shape(StrokeLog.Entry e) {
        ModularBrush parts = e.code == null ? null : ModularCode.decode(e.code);
        if (parts == null) return;
        StrokeStart.shape(copy, parts, session);
        ShapeStroke.lay(copy, e.shape().spans(copy.width, copy.height), e.shape().shade);
    }
    /** A picture placed again ({@link PictureImport#lay}) on the layer it went on; nothing if its greys don't read. */
    private void picture(StrokeLog.Picture p) {
        int[] greys = p.greys();
        if (greys == null || p.layer >= copy.layerCount()) return;
        copy.selectLayer(p.layer);
        PictureImport.lay(copy, p.x, p.y, p.w, p.h, greys);
    }
    /** The layers merged again ({@link InkWashLayers#merge}), with the layer above as kept for Merge Down; nothing if it doesn't read. */
    private void merge(StrokeLog.Merge m) {
        ToneDocument.Layer above = m.above(copy.width, copy.height);
        if (m.above != null && above == null) return;
        InkWashLayers.mergeAbove(copy, above == null ? null : java.util.Collections.singletonList(above));
        session.dry("layers changed");
    }
    /** One step drawn onto the copy; whether every stroke is done. */
    boolean step() {
        player.step((long) PEN_MS * speed, sink);
        if (stroke != null) stroke.present();
        return player.done();
    }
    /** A stroke still being drawn finished (the replay ends). */
    void finish() { if (stroke != null) { stroke.finish(); stroke = null; } }
}
