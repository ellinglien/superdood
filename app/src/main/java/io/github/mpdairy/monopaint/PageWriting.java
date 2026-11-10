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

    /** The page's tone at page px {@code x, y}: 0 black to 255 paper; any x, y in page px, off the page 255. */
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
        List<List<Stroke>> lifted = new ArrayList<>(); // per LIFT: the strokes inside its loop, as lifted (poses always start from these)
        List<List<Stroke>> current = new ArrayList<>(); // per LIFT: what it last put on the page (a later cut takes these)
        List<Integer> liftAt = new ArrayList<>();
        long clock = 0;
        for (int i = 0; i < entries.size(); i++) {
            StrokeLog.Entry e = entries.get(i);
            // stepped: ToneDocument keeps only strokes that made their step; entries read from a file are all stepped.
            if (e.kind == StrokeLog.PEN && e.stepped && e.px <= WIDEST && e.size() > 0) {
                int n = e.size();
                float[] x = new float[n], y = new float[n];
                long[] t = new long[n];
                for (int k = 0; k < n; k++) { clock += k == 0 ? BETWEEN : e.dt[k]; x[k] = e.px(k); y[k] = e.py(k); t[k] = clock; }
                on.add(new Stroke(x, y, t));
            } else if (e.kind == StrokeLog.LIFT) {
                float[] loop = e.loop();
                float[] lb = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
                for (int k = 0; k < loop.length; k += 2) { lb[0] = Math.min(lb[0], loop[k]); lb[1] = Math.min(lb[1], loop[k + 1]); lb[2] = Math.max(lb[2], loop[k]); lb[3] = Math.max(lb[3], loop[k + 1]); }
                List<Stroke> inside = new ArrayList<>();
                for (Stroke s : on) {
                    float[] sb = box(java.util.Collections.singletonList(s));
                    if (sb[2] < lb[0] || sb[0] > lb[2] || sb[3] < lb[1] || sb[1] > lb[3]) continue;
                    if (mostlyInside(s, loop)) inside.add(s);
                }
                lifted.add(inside);
                current.add(new ArrayList<>(inside));
                liftAt.add(i);
            } else if (e.kind == StrokeLog.PLACE && e.place != null && e.place.back > 0) {
                int from = liftAt.indexOf(i - e.place.back);
                if (from < 0) continue;
                List<Stroke> piece = lifted.get(from), now = current.get(from);
                if (!e.place.drop || piece.isEmpty()) { if (e.place.cut) { on.removeAll(now); now.clear(); } continue; }
                // shortcut: the lifted pixels' box (every plane of the selected pair: washes, other brushes, shapes) isn't logged;
                // the pen points' box stands in, so a piece holding other marks lands off by scale x their offset;
                // upgrade: log the cut box in PLACE.
                float[] box = box(piece);
                float cx = (box[0] + box[2]) / 2, cy = (box[1] + box[3]) / 2;
                List<Stroke> placed = new ArrayList<>();
                for (Stroke s : piece) placed.add(pose(s, cx, cy, e.place.centerX, e.place.centerY, e.place.scale, e.place.angle));
                if (e.place.cut) {
                    // Each posed stroke takes its original's place in the list (writing order); one already gone is appended.
                    List<Stroke> old = new ArrayList<>(now);
                    for (int k = 0; k < placed.size(); k++) {
                        int at = k < old.size() ? on.indexOf(old.get(k)) : -1;
                        if (at >= 0) on.set(at, placed.get(k)); else on.add(placed.get(k));
                    }
                    now.clear(); now.addAll(placed);
                } else on.addAll(placed); // a copy lays; the lift's own piece stays where it was
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
    /** Must stay the same rule as FloatingSelection.inside and VectorLines.Loop.contains (even-odd at the point). */
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
    /** The stroke as the Lasso puts its piece down: its centre {@code fromX, fromY} to {@code toX, toY}, scaled, turned as VectorLines.posed turns (radians; y points down, so a positive angle turns clockwise on screen). */
    static Stroke pose(Stroke s, float fromX, float fromY, float toX, float toY, float scale, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle); // as VectorLines.posed
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
