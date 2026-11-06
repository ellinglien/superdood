package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

/**
 * Vector Ink ({@link VectorInk}, {@link VectorLines}): smoothing keeps a stroke's ends and softens the pen's jitter; the
 * outline's width follows pressure; a stroke drawn at 100% and at 4× covers the same area; the eraser's dab cuts a line
 * in two at its edge, ends cut flat, and takes what it covers; the lines' bytes and a page's LINE section read back as
 * written; on a page, the pen lays the smoothed line and keeps it, Undo and Redo take pixels and lines together, the
 * eraser cuts as one step; which marks are refused where; a replay's start (StrokeStart) draws Vector Ink again.
 * {@code older-write} writes a page for the reader before Vector Ink, {@code older-page} says how this one shows it (test_inkwash.sh).
 */
public final class VectorInkChecks {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length > 0 && args[0].equals("older-write")) { try (java.io.FileOutputStream out = new java.io.FileOutputStream(args[1])) { DocumentCodec.write(out, page().layerSnapshot()); } return; }
        if (args.length > 0 && args[0].equals("older-page")) { System.out.println(shown(DocumentCodec.read(new java.io.FileInputStream(args[1])))); return; }
        smoothing(); width(); scales(); cuts(); bytes(); onPage(); refusals(); medium(); lasso(); crumbs(); longErase(); guests();
        System.out.println("PASS: Vector Ink: smoothing keeps a stroke's ends, the pen's jitter softened, its points thinned within " + VectorLines.STRAY + " px of its curve; the outline's width follows each point's share, the pen's share Control's pressure to size; a stroke covers the same area at 100% and 4×; "
                + "a dab cuts a line in two at its edge (ends cut flat), takes an end or the whole, misses what it misses, cuts a segment it crosses between points; the lines and a page's LINE section read back as written; "
                + "on a page the pen keeps the smoothed line and its pixels, Undo and Redo take both together, the eraser cuts in one step; Vector Ink on a marked page or a lower pair goes on a pair put on top (one Undo step), another ink on its pair goes under it, the eraser cuts its lines from any pair, refused only with four layers or four layers or four full; a full layer gives the next stroke a new one, both sharp, both cut by the eraser, a stroke over the cap ending as any; a replay's start draws Vector Ink; the medium: its codes read and write, the first code reads as the Basic tool, a brush switched to it sets aside smudge, drying, After, texture and any tip but Round, Brush Lab shows no choices or dials there (Tip its Size, Control its Response), no roll, Anything Goes or variation picks it, and one with it locked rolls only what it draws; fast eraser strokes, and a long one of a large eraser, over close lines leave no crumbs and no pixels of what they cut; the Lasso lifts Vector Ink's lines with the pixels, cut at the loop, and puts them back moved, turned and scaled, sharp, Delete and Cut take them, Duplicate copies them, one Undo each; an eraser's dabs cut together as one at a time; a Draw Together guest's ink over Vector Ink goes on a pair under it and its eraser cuts the lines, one Undo step");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /** A wobbly line from (20, 50) to (180, 70), {@code n} samples. */
    static float[][] hand(int n, long seed) {
        Random r = new Random(seed);
        float[] x = new float[n], y = new float[n], p = new float[n];
        for (int i = 0; i < n; i++) { double t = i / (n - 1.0); x[i] = (float) (20 + 160 * t + (i > 0 && i < n - 1 ? r.nextGaussian() * .4 : 0)); y[i] = (float) (50 + 20 * t + 15 * Math.sin(t * 6) + (i > 0 && i < n - 1 ? r.nextGaussian() * .4 : 0)); p[i] = (float) (.3 + .6 * t); }
        return new float[][]{x, y, p};
    }
    /**
     * The owner's scribble (2026-11-04, "after a long vector ink stroke just now i am unable to make another mark"): one
     * stroke of {@code loops} overlapping loops 80 to 200 px across, wandering over a Manta page, a sample every 2.5 px
     * (a brisk hand at the pen's rate), its pressure rising and falling.
     */
    static float[][] scribble(int loops) {
        Random r = new Random(17);
        java.util.List<float[]> pts = new java.util.ArrayList<>();
        double cx = 900, cy = 1200;
        for (int l = 0; l < loops; l++) {
            double rad = 80 + 120 * r.nextDouble(), squash = .6 + .4 * r.nextDouble();
            int steps = (int) Math.ceil(2 * Math.PI * rad / 2.5);
            for (int i = 0; i < steps; i++) {
                double a = 2 * Math.PI * i / steps;
                cx += (r.nextDouble() - .5) * 1.5; cy += (r.nextDouble() - .5) * 1.5;
                pts.add(new float[]{(float) (cx + rad * Math.cos(a)), (float) (cy + rad * squash * Math.sin(a)), (float) (.4 + .3 * Math.sin(a * 3 + l))});
            }
        }
        float[][] h = new float[3][pts.size()];
        for (int i = 0; i < pts.size(); i++) for (int k = 0; k < 3; k++) h[k][i] = pts.get(i)[k];
        return h;
    }
    static double toSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy, t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / l2));
        return Math.hypot(px - ax - t * dx, py - ay - t * dy);
    }
    static double turning(double[] xs, double[] ys) {
        double sum = 0;
        for (int i = 1; i < xs.length - 1; i++) {
            double a = Math.atan2(ys[i] - ys[i - 1], xs[i] - xs[i - 1]), b = Math.atan2(ys[i + 1] - ys[i], xs[i + 1] - xs[i]), d = Math.abs(b - a);
            sum += Math.min(d, 2 * Math.PI - d);
        }
        return sum;
    }

    static void smoothing() {
        float[][] h = hand(60, 7);
        VectorLines.Stroke s = VectorLines.smoothed(0, 6, h[0], h[1], h[2], 60);
        check(s.x(0) == Math.round(h[0][0] * 8) / 8.0 && s.y(0) == Math.round(h[1][0] * 8) / 8.0, "smoothing keeps the first point");
        int last = s.points() - 1;
        check(s.x(last) == Math.round(h[0][59] * 8) / 8.0 && s.y(last) == Math.round(h[1][59] * 8) / 8.0, "smoothing keeps the last point");
        for (int i = 1; i <= last; i++) check(Math.hypot(s.x(i) - s.x(i - 1), s.y(i) - s.y(i - 1)) <= VectorLines.LONGEST + .2, "points at most LONGEST apart (" + i + ")");
        // Thinned, the line keeps to its curve: every point of the curve within STRAY of it (a third of a screen pixel at 800%).
        VectorLines.Stroke dense = VectorLines.curve(0, 6, h[0], h[1], h[2], 60);
        double far = 0;
        for (int i = 0; i < dense.points(); i++) {
            double near = Double.MAX_VALUE;
            for (int j = 0; j < last; j++) near = Math.min(near, toSegment(dense.x(i), dense.y(i), s.x(j), s.y(j), s.x(j + 1), s.y(j + 1)));
            far = Math.max(far, near);
        }
        check(far <= VectorLines.STRAY + 1e-9 && s.points() < dense.points(), "thinned within " + VectorLines.STRAY + " px of its curve (" + far + "), " + s.points() + " of " + dense.points() + " points");
        // Jitter softened: the smoothed line turns less, per unit length, than the samples.
        double[] rx = new double[60], ry = new double[60];
        for (int i = 0; i < 60; i++) { rx[i] = h[0][i]; ry[i] = h[1][i]; }
        double[] sx = new double[s.points()], sy = new double[s.points()];
        for (int i = 0; i <= last; i++) { sx[i] = s.x(i); sy[i] = s.y(i); }
        check(turning(sx, sy) < .6 * turning(rx, ry), "jitter softened (" + turning(sx, sy) + " vs " + turning(rx, ry) + ")");
        check(VectorLines.smoothed(0, 6, new float[]{5}, new float[]{5}, new float[]{.5f}, 1).points() == 1, "a dot is one point");
    }

    static VectorLines.Stroke line(double x0, double y0, double x1, double y1, float size, double p, int n) {
        float[] x = new float[n], y = new float[n], ps = new float[n];
        for (int i = 0; i < n; i++) { double t = i / (n - 1.0); x[i] = (float) (x0 + (x1 - x0) * t); y[i] = (float) (y0 + (y1 - y0) * t); ps[i] = (float) p; }
        return VectorLines.rough(0, size, x, y, ps, n);
    }
    /** Coverage of {@code s} on a {@code w × h} page px area at {@code scale}, in page px (sum of shares over scale²). */
    static double area(VectorLines.Stroke s, int w, int h, int scale) {
        byte[] out = new byte[w * scale * h * scale];
        VectorLines.cover(s, scale, 0, 0, 0, 0, w * scale, h * scale, out);
        double sum = 0;
        for (byte b : out) sum += (b & 255) / 255.0;
        return sum / (scale * scale);
    }
    static void width() {
        // The pen: Control's pressure to size makes each point's share (the Basic tool's: .8 of its size with none, 1.2 pressed hard).
        ToneDocument d = InkWashLayers.blank(200, 80);
        float[][] light = {new float[30], new float[30], new float[30]}, hard = {new float[30], new float[30], new float[30]};
        for (int i = 0; i < 30; i++) { light[0][i] = hard[0][i] = 20 + 5 * i; light[1][i] = 20; hard[1][i] = 60; light[2][i] = .05f; hard[2][i] = 1; }
        WatercolorSession ws = new WatercolorSession();
        draw(d, ws, vector(), 10, 1, light); draw(d, ws, vector(), 10, 1, hard);
        VectorLines.Stroke a = d.lines(InkWashLayers.INK).get(0), b = d.lines(InkWashLayers.INK).get(1);
        double wa = 2 * a.radius(a.points() / 2), wb = 2 * b.radius(b.points() / 2);
        check(Math.abs(wa - 8.1) < .4 && Math.abs(wb - 12) < .4, "pressed hard, wider: " + wa + " light, " + wb + " hard, at size 10");
        for (double p : new double[]{.1, .5, 1}) {
            VectorLines.Stroke s = line(10, 20, 90, 20, 10, p, 40);
            byte[] out = new byte[100 * 40];
            VectorLines.cover(s, 1, 0, 0, 0, 0, 100, 40, out);
            double across = 0;
            for (int y = 0; y < 40; y++) across += (out[y * 100 + 50] & 255) / 255.0;
            check(Math.abs(across - 2 * VectorLines.radius(10, p)) < .1, "width at share " + p + " is " + 2 * VectorLines.radius(10, p) + " (" + across + ")");
        }
        check(VectorLines.radius(10, 1) == 5 && VectorLines.radius(10, .5) == 2.5, "a share of the size");
    }
    static void scales() {
        float[][] h = hand(50, 3);
        VectorLines.Stroke s = VectorLines.smoothed(0, 5, h[0], h[1], h[2], 50);
        double one = area(s, 200, 110, 1), four = area(s, 200, 110, 4);
        check(Math.abs(one - four) / four < .02, "100% and 4× cover the same area (" + one + ", " + four + ")");
        VectorLines.Stroke cut = new VectorLines.Stroke(0, 5, VectorLines.START_CUT | VectorLines.END_CUT, s.xy, s.share);
        double c1 = area(cut, 200, 110, 1), c4 = area(cut, 200, 110, 4);
        check(Math.abs(c1 - c4) / c4 < .02 && c4 < four, "cut ends too, and less than round ones (" + c1 + ", " + c4 + ")");
    }
    /** Layer {@code layer}'s pixels are its lines drawn alone at 100%, in order. */
    static boolean drawn(ToneDocument d, int layer) {
        ToneDocument alone = InkWashLayers.blank(d.width, d.height);
        alone.begin(1, ToneDocument.KIND_PEN);
        VectorLines lines = d.lines(layer);
        for (int k = 0; k < lines.size(); k++) {
            VectorLines.Stroke line = lines.get(k);
            byte[] cover = new byte[d.width * d.height];
            VectorLines.cover(line, 1, 0, 0, 0, 0, d.width, d.height, cover);
            for (int i = 0; i < cover.length; i++) if (cover[i] != 0) alone.overTone(i % d.width, i / d.width, line.shade, cover[i] & 255);
        }
        alone.finish();
        ToneDocument only = new ToneDocument(d.layerSnapshot());
        for (int pair = 0; pair < InkWashLayers.pairs(only); pair++) InkWashLayers.show(only, pair, pair == InkWashLayers.pair(layer));
        only.setLayerVisible(InkWashLayers.washOf(only, InkWashLayers.pair(layer)), false);
        return shown(only) == shown(alone);
    }
    /**
     * The owner's crumbs (2026-11-04: "when i erase a bunch of vector ink it leaves a bit on the screen occasionally"):
     * fast eraser strokes (samples 40 px apart) over a page of close lines, some running along the eraser's way at its
     * edge: no line keeps a point well inside where the eraser swept, no crumb of a line is left, and the layer's pixels
     * are its lines' everywhere (none left where a cut line was).
     */
    static void crumbs() {
        WatercolorSession s = new WatercolorSession();
        ToneDocument d = InkWashLayers.blank(600, 400);
        // Lines 3 px apart, across and along the eraser's way.
        for (int k = 0; k < 40; k++) draw(d, s, vector(), 2, 1, new float[][]{{20, 300, 580}, {20 + 3 * k + .37f * k, 30 + 3 * k, 40 + 3 * k}, {.5f, .5f, .5f}});
        for (int k = 0; k < 30; k++) draw(d, s, vector(), 2, 1, new float[][]{{40 + 17 * k, 41 + 17 * k}, {200, 390}, {.5f, .5f}});
        int vi = VectorInk.lined(d);
        java.util.List<double[]> swept = new java.util.ArrayList<>();
        for (int pass = 0; pass < 3; pass++) {
            DrawingStroke e = StrokeStart.stroke(d, null, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 24, 0), s, 0);
            float[] at = null;
            for (int i = 0; i <= 14; i++) {
                float x = 10 + 40 * i, y = 60 + 30 * pass + 9 * (float) Math.sin(i + pass) + 70 * pass;
                e.sample(x, y, 1, 0, 0);
                float[] now = ((EraseBothStroke) e).at();
                if (at != null) swept.add(new double[]{at[0], at[1], at[2] / 2, now[0], now[1], now[2] / 2});
                at = now;
            }
            e.finish();
        }
        VectorLines left = d.lines(vi);
        int inside = 0, crumbs = 0;
        for (int k = 0; k < left.size(); k++) {
            VectorLines.Stroke st = left.get(k);
            double length = 0;
            for (int i = 1; i < st.points(); i++) length += Math.hypot(st.x(i) - st.x(i - 1), st.y(i) - st.y(i - 1));
            if (st.caps == (VectorLines.START_CUT | VectorLines.END_CUT) && length < 1) crumbs++;
            for (int i = 0; i < st.points(); i++) for (double[] c : swept) if (capsule(st.x(i), st.y(i), c) < -.5) inside++;
        }
        check(inside == 0 && crumbs == 0, "the eraser leaves no line inside where it swept (" + inside + " points) and no crumbs (" + crumbs + ")");
        check(drawn(d, vi), "and the layer's pixels are its lines' everywhere, none left of a cut line");
    }
    /**
     * The owner's dashes (2026-11-04: "it happened again... particularly long eraser stroke", "it was after the lift that i
     * saw the bits appear"): one long stroke of a large eraser (90 px, 3,600 samples 1.5 px apart, back and forth over the same lines, as a scrubbing hand) over a
     * page of close lines: at the lift nothing is left inside where it swept, lines or pixels.
     */
    static void longErase() {
        WatercolorSession s = new WatercolorSession();
        ToneDocument d = InkWashLayers.blank(800, 700);
        for (int k = 0; k < 60; k++) draw(d, s, vector(), 3, 1, new float[][]{{20, 400, 780}, {40 + 10 * k, 45 + 10 * k + 3 * (k % 3), 40 + 10 * k}, {.5f, .5f, .5f}});
        int vi = VectorInk.lined(d);
        java.util.List<double[]> swept = new java.util.ArrayList<>();
        DrawingStroke e = StrokeStart.stroke(d, null, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 90, 0), s, 0);
        float[] at = null;
        for (int i = 0; i < 3600; i++) {
            double u = i * 1.5, row = Math.floor(u / 700), along = u % 700;
            float x = (float) (50 + (row % 2 == 0 ? along : 700 - along)), y = (float) (60 + 10 * row + 5 * Math.sin(i / 40.0));
            e.sample(x, y, 1, 0, 0, i * 2L);
            float[] now = ((EraseBothStroke) e).at();
            if (at != null) swept.add(new double[]{at[0], at[1], at[2] / 2, now[0], now[1], now[2] / 2});
            at = now;
        }
        e.finish();
        VectorLines left = d.lines(vi);
        int inside = 0;
        for (int k = 0; k < left.size(); k++) {
            VectorLines.Stroke st = left.get(k);
            for (int i = 0; i < st.points(); i++) for (double[] c : swept) if (capsule(st.x(i), st.y(i), c) < -.5) { inside++; break; }
        }
        check(inside == 0, "a long, large eraser stroke leaves no line inside where it swept (" + inside + " points, " + left.size() + " pieces left)");
        check(drawn(d, vi), "and the layer's pixels are its lines' everywhere");
    }
    /**
     * A Draw Together guest's changes over Vector Ink, laid as the Manta lays them (LiveProbe: WebParity.lay's way; a set
     * change PictureImport.lay's lighter): its ink on a pair under the lines, as the host's own pen's; its eraser cutting
     * the lines as the host's eraser does. Before 2026-11-04 its ink went on the lines' layer, which the zoomed view and
     * the Lasso leave out for the lines, and its eraser left the lines whole, so the zoomed view still drew them there and
     * the host's next eraser stroke nearby drew their pixels back.
     */
    static void guests() {
        WatercolorSession s = new WatercolorSession();
        ToneDocument d = InkWashLayers.blank(200, 120);
        draw(d, s, vector(), 6, 1, new float[][]{{20, 100, 180}, {60, 60, 60}, {.5f, .5f, .5f}});
        int vi = VectorInk.lined(d);
        check(vi == InkWashLayers.INK && d.activeLayer() == vi, "a fresh page: Vector Ink on its only pair, still selected");
        WebParity.lay(d, 50, 20, 10, 10, new int[10 * 10]);
        check(d.compositeTone(55, 25) == 0, "a guest's ink shows");
        vi = VectorInk.lined(d);
        check(InkWashLayers.pairs(d) == 2 && vi == 3 && d.activeLayer() == InkWashLayers.INK && drawn(d, vi), "on a pair put under the lines, the lines' layer their pixels alone");
        int[] white = new int[20 * 40];
        java.util.Arrays.fill(white, 254);
        check(PictureImport.lay(d, 90, 40, 20, 40, white, false, true), "a guest's eraser across the line");
        check(d.compositeTone(100, 60) == 255 && d.lines(vi).size() == 2, "cuts it in two (" + d.lines(vi).size() + ")");
        VectorLines left = d.lines(vi);
        for (int k = 0; k < left.size(); k++) for (int i = 0; i < left.get(k).points(); i++) {
            double x = left.get(k).x(i);
            check(x <= 90.01 || x >= 109.99, "no line left where it erased (" + x + ")");
        }
        check(drawn(d, vi), "the lines' layer their pixels alone after");
        boolean undone = d.undo();
        check(undone && d.lines(vi).size() == 1 && d.compositeTone(100, 60) < 128, "one Undo takes the cut back with the pixels (" + undone + ", " + d.lines(vi).size() + ", " + d.compositeTone(100, 60) + ")");
    }
    /** How far page point {@code x, y} lies outside capsule {x0, y0, r0, x1, y1, r1} (negative: inside), its radius taken along it. */
    static double capsule(double x, double y, double[] c) {
        double dx = c[3] - c[0], dy = c[4] - c[1], l2 = dx * dx + dy * dy, t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - c[0]) * dx + (y - c[1]) * dy) / l2));
        return Math.hypot(x - c[0] - t * dx, y - c[1] - t * dy) - (c[2] + (c[5] - c[2]) * t);
    }
    static void lasso() {
        WatercolorSession s = new WatercolorSession();
        ToneDocument d = InkWashLayers.blank(200, 120);
        draw(d, s, preset("Steady line"), 4, 1, hand(20, 1));
        draw(d, s, vector(), 4, 1, new float[][]{{20, 60, 100, 140, 180}, {30, 30, 30, 30, 30}, {.5f, .5f, .5f, .5f, .5f}});
        draw(d, s, vector(), 4, 1, new float[][]{{60, 80, 100}, {80, 85, 90}, {.5f, .5f, .5f}});
        int vi = VectorInk.lined(d), before = shown(d);
        VectorLines had = d.lines(vi);
        // A box round the middle: the long line crossed twice, the short one inside whole.
        float[] loop = {50, 10, 120, 10, 120, 110, 50, 110};
        FloatingSelection piece = LassoPlanes.lift(d, loop, 4);
        check(piece != null && piece.planes != null, "the Lasso lifts Vector Ink");
        int inked = 0;
        for (int i = 0; i < piece.alpha.length; i++) if (piece.alpha[i] != 0) inked++;
        check(inked > 100, "the piece shows its lines (" + inked + " pixels)");
        piece.centerX += 30; piece.centerY += 5;
        check(piece.place(d), "placed");
        VectorLines now = d.lines(vi);
        check(now.size() == 4, "the long line in two outside, its middle and the short one moved (" + now.size() + ")");
        boolean moved = false;
        for (int k = 0; k < now.size(); k++) moved |= Math.abs(now.get(k).x(0) - 90) < .2 && Math.abs(now.get(k).y(0) - 85) < .2;
        check(moved, "the short line moved by the piece's move, sharp");
        check(drawn(d, vi), "the layer's pixels are its lines after the move");
        check(d.undo() && d.lines(vi) == had && shown(d) == before, "one Undo puts lines and pixels back");
        // Turned a quarter and twice the size: a line's end goes where the piece's pixel does.
        FloatingSelection turned = LassoPlanes.lift(d, loop, 4);
        turned.angle = Math.PI / 2; turned.scale = 2;
        turned.place(d);
        check(drawn(d, vi) && d.lines(vi).size() == 4, "turned and scaled, still the lines' pixels");
        VectorLines.Stroke small = null;
        for (int k = 0; k < d.lines(vi).size(); k++) if (d.lines(vi).get(k).size > 6) small = d.lines(vi).get(k);
        check(small != null && Math.abs(small.size - 2 * had.get(1).size) < .01, "its widths scaled too");
        d.undo();
        // Delete takes the lines in the loop; Duplicate copies them; Cut is Delete's step.
        FloatingSelection gone = LassoPlanes.lift(d, loop, 4);
        check(LassoPieces.erase(d, gone) && d.lines(vi).size() == 2 && drawn(d, vi), "Delete takes the lines in the loop (" + d.lines(vi).size() + ")");
        d.undo();
        FloatingSelection kept = LassoPlanes.lift(d, loop, 4);
        FloatingSelection copy = LassoPieces.duplicated(kept);
        check(copy.place(d) && d.lines(vi).size() == had.size() + 2 && drawn(d, vi), "Duplicate copies them (" + d.lines(vi).size() + ")");
        // Batched dabs cut as one at a time.
        VectorLines one = had, all = had;
        java.util.List<double[]> dabs = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) { double[] dab = {40 + 4 * i, 30 + 2 * Math.sin(i), 3}; dabs.add(dab); one = one.cut(dab[0], dab[1], dab[2]); }
        all = all.cut(dabs);
        check(same(one, all), "dabs cut together as one at a time (" + all.size() + " pieces)");
    }
    static void cuts() {
        VectorLines one = VectorLines.NONE.with(line(0, 50, 100, 50, 4, 1, 101));
        VectorLines two = one.cut(50, 50, 5);
        check(two.size() == 2, "a dab across the middle makes two (" + two.size() + ")");
        VectorLines.Stroke a = two.get(0), b = two.get(1);
        check(Math.abs(a.x(a.points() - 1) - 45) <= .125 && Math.abs(b.x(0) - 55) <= .125, "the new ends sit on the dab's edge (" + a.x(a.points() - 1) + ", " + b.x(0) + ")");
        check(a.caps == VectorLines.END_CUT && b.caps == VectorLines.START_CUT, "the new ends are cut, the old round");
        check(a.x(0) == 0 && b.x(b.points() - 1) == 100, "the old ends stay");
        check(one.cut(50, 80, 5) == one, "a dab that misses changes nothing");
        VectorLines end = one.cut(100, 50, 10);
        check(end.size() == 1 && Math.abs(end.get(0).x(end.get(0).points() - 1) - 90) <= .125 && end.get(0).caps == VectorLines.END_CUT, "a dab at the end takes it, cut there");
        check(one.cut(50, 50, 80).isEmpty(), "a dab over all of it takes it all");
        // Points 10 px apart, a dab of 2 between two of them: the segment is cut all the same.
        VectorLines sparse = VectorLines.NONE.with(line(0, 50, 100, 50, 2, 1, 11)).cut(55, 50, 2);
        check(sparse.size() == 2 && Math.abs(sparse.get(0).x(sparse.get(0).points() - 1) - 53) <= .125 && Math.abs(sparse.get(1).x(0) - 57) <= .125, "a dab between points cuts the segment");
        // Cutting the cut again, and a mid stroke's pieces keep its order.
        VectorLines three = two.cut(20, 50, 3);
        check(three.size() == 3 && three.get(2) == b, "a second dab cuts the first piece, the second untouched");
    }
    static boolean same(VectorLines a, VectorLines b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            VectorLines.Stroke x = a.get(i), y = b.get(i);
            if (x.shade != y.shade || x.size != y.size || x.caps != y.caps || !java.util.Arrays.equals(x.xy, y.xy) || !java.util.Arrays.equals(x.share, y.share)) return false;
        }
        return true;
    }
    static void bytes() throws Exception {
        float[][] h = hand(40, 9);
        VectorLines lines = VectorLines.NONE.with(VectorLines.smoothed(30, 4.5f, h[0], h[1], h[2], 40)).cut(100, 60, 6);
        check(same(VectorLines.decode(lines.encode()), lines), "the lines' bytes read back as written");
        check(VectorLines.decode(new byte[]{9, 0, 0, 0, 0}) == null, "another version's bytes don't read");
        // Version 1 (before the medium): a point's pressure, the width .8 to 1.2 of the size.
        byte[] one = {1, 0, 0, 0, 1, 0, 0, 64, (byte) 0xa0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 8, 0, 0, 0, 8, 0, 0, 0, 0, 80, 0, 0, 0, 8, (byte) 255};
        VectorLines old = VectorLines.decode(one);
        check(old != null && Math.abs(2 * old.get(0).radius(0) - 4) < .05 && Math.abs(2 * old.get(0).radius(1) - 6) < .05, "version 1's lines read at the widths they had");
        ToneDocument page = page();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocumentCodec.write(out, page.layerSnapshot());
        ToneDocument read = DocumentCodec.read(new ByteArrayInputStream(out.toByteArray()));
        check(same(read.lines(InkWashLayers.INK), page.lines(InkWashLayers.INK)) && read.lines(InkWashLayers.WASH) == null, "a page's LINE section reads back as written");
        check(shown(read) == shown(page), "and its pixels");
    }

    /** A page with a Vector Ink line, cut by the eraser, and a wash under it. */
    static ToneDocument page() {
        ToneDocument d = InkWashLayers.blank(200, 120);
        WatercolorSession s = new WatercolorSession();
        draw(d, s, preset("Soft wash"), 30, .5, hand(30, 5));
        draw(d, s, vector(), 6, 1, hand(50, 11));
        DrawingStroke e = StrokeStart.stroke(d, null, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 12, 0), s, 0);
        e.sample(100, 55, 1, 0, 0); e.sample(100, 75, 1, 0, 0); e.finish();
        return d;
    }
    static Brush preset(String name) { for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) return p.brush(); throw new AssertionError(name); }
    static Brush vector() { return preset("Vector ink"); }
    static boolean draw(ToneDocument d, WatercolorSession s, Brush b, double px, double dark, float[][] h) {
        DrawingStroke st = StrokeStart.stroke(d, b, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, px, dark), s, 0);
        for (int i = 0; i < h[0].length; i++) st.sample(h[0][i], h[1][i], h[2][i], 0, 0);
        boolean changed = st.finish();
        s.settleNow(d);
        return changed;
    }
    /** The page as shown, hashed. */
    static int shown(ToneDocument d) {
        int[] out = new int[d.width * d.height];
        d.compositeTones(0, 0, d.width, d.height, out, 0, d.width, 0);
        return java.util.Arrays.hashCode(out);
    }
    static void onPage() {
        ToneDocument d = InkWashLayers.blank(200, 120);
        WatercolorSession s = new WatercolorSession();
        int blank = shown(d);
        check(VectorInk.is(vector()) && !VectorInk.is(preset("Steady line")), "Vector ink is Vector Ink, Steady line is not");
        check(StrokeStart.stroke(d, vector(), new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 4, 1), s, 0) instanceof VectorInk.Pen, "a replay's start draws Vector Ink");
        d.cancel();
        float[][] h = hand(50, 11);
        check(draw(d, s, vector(), 6, 1, h), "the pen lays its line");
        VectorLines lines = d.lines(InkWashLayers.INK);
        check(lines != null && lines.size() == 1 && d.lines(InkWashLayers.WASH) == null, "the ink half keeps one line");
        // Its pixels are the smoothed line drawn alone at 100%.
        ToneDocument alone = InkWashLayers.blank(200, 120);
        alone.selectLayer(InkWashLayers.INK); alone.begin(1, ToneDocument.KIND_PEN);
        VectorLines.Stroke line = lines.get(0);
        byte[] cover = new byte[200 * 120];
        VectorLines.cover(line, 1, 0, 0, 0, 0, 200, 120, cover);
        for (int i = 0; i < cover.length; i++) if (cover[i] != 0) alone.overTone(i % 200, i / 200, line.shade, cover[i] & 255);
        alone.finish();
        int drawn = shown(d);
        check(drawn == shown(alone), "its pixels are the smoothed line at 100%, the rough one gone");
        check(d.undo() && d.lines(InkWashLayers.INK) == null && shown(d) == blank, "one Undo takes pixels and line, the layer plain again");
        check(d.redo() && d.lines(InkWashLayers.INK) == lines && shown(d) == drawn, "Redo brings both");
        DrawingStroke e = StrokeStart.stroke(d, null, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 10, 0), s, 0);
        e.sample(100, 40, 1, 0, 0); e.sample(100, 90, 1, 0, 0);
        check(e.finish() && d.lines(InkWashLayers.INK).size() == 2, "the eraser cuts the line in two (" + d.lines(InkWashLayers.INK).size() + ")");
        check(d.undo() && d.lines(InkWashLayers.INK) == lines && shown(d) == drawn, "one Undo takes the cut back with the pixels");
        // An eraser of Pencil alone leaves Vector Ink's lines.
        DrawingStroke pencil = StrokeStart.stroke(d, null, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 10, 0), s, ToneDocument.KIND_PENCIL);
        pencil.sample(100, 40, 1, 0, 0); pencil.sample(100, 90, 1, 0, 0); pencil.finish();
        check(d.lines(InkWashLayers.INK) == lines, "an eraser of another kind leaves the lines");
    }
    static void refusals() {
        WatercolorSession s = new WatercolorSession();
        // A page with pen marks (the owner's, 2026-11-04: "Top layer only"): a pair put on top for it, one Undo step.
        ToneDocument d = InkWashLayers.blank(200, 120);
        draw(d, s, preset("Steady line"), 4, 1, hand(20, 1));
        int marked = shown(d);
        check(VectorInk.refusal(d, vector()) == null && VectorInk.room(d) == VectorInk.ADD, "Vector Ink on a page with pen marks: a new layer for it");
        check(draw(d, s, vector(), 4, 1, hand(20, 2)), "and it draws");
        check(InkWashLayers.pairs(d) == 2 && d.lines(3) != null && d.lines(1) == null && d.activeLayer() == InkWashLayers.INK, "on a pair put on top, the pair drawn on selected again");
        check(d.undo() && InkWashLayers.pairs(d) == 1 && shown(d) == marked, "one Undo takes the line and the pair");
        // More Vector Ink goes on its pair; the pen on the pair below, as before.
        d.redo();
        int pairs = InkWashLayers.pairs(d);
        draw(d, s, vector(), 4, 1, hand(20, 3));
        check(InkWashLayers.pairs(d) == pairs && d.lines(3).size() == 2, "more Vector Ink on its own pair");
        draw(d, s, preset("Steady line"), 4, 1, hand(20, 4));
        check(d.lines(3).size() == 2 && d.lines(1) == null, "the pen on the pair below");
        // The eraser on the pair below cuts the lines on top too, their pixels drawn again.
        DrawingStroke e = StrokeStart.stroke(d, null, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 10, 0), s, 0);
        e.sample(100, 0, 1, 0, 0); e.sample(100, 119, 1, 0, 0);
        check(e.finish() && d.lines(3).size() > 2, "the eraser cuts Vector Ink's lines from the pair below (" + d.lines(3).size() + ")");
        ToneDocument alone = InkWashLayers.blank(200, 120);
        alone.begin(1, ToneDocument.KIND_PEN);
        for (int k = 0; k < d.lines(3).size(); k++) {
            VectorLines.Stroke line = d.lines(3).get(k);
            byte[] cover = new byte[200 * 120];
            VectorLines.cover(line, 1, 0, 0, 0, 0, 200, 120, cover);
            for (int i = 0; i < cover.length; i++) if (cover[i] != 0) alone.overTone(i % 200, i / 200, line.shade, cover[i] & 255);
        }
        alone.finish();
        ToneDocument top = new ToneDocument(d.layerSnapshot());
        InkWashLayers.show(top, 0, false);
        check(shown(top) == shown(alone), "its pixels are the cut lines'");
        // A fresh page: Vector Ink on its own pair; the pen then goes on a pair put under it.
        ToneDocument v = InkWashLayers.blank(200, 120);
        check(VectorInk.room(v) == InkWashLayers.INK, "a fresh page: no new layer");
        draw(v, s, vector(), 4, 1, hand(20, 1));
        check(InkWashLayers.pairs(v) == 1 && v.lines(InkWashLayers.INK) != null, "drawn on its only pair");
        check(VectorInk.refusal(v, preset("Steady line")) == null, "the pen isn't refused");
        draw(v, s, preset("Steady line"), 4, 1, hand(20, 5));
        check(InkWashLayers.pairs(v) == 2 && v.lines(3) != null && v.lines(1) == null && v.layerMarked(1), "the pen on a pair put under Vector Ink");
        check(!VectorInk.oldLayers(v) && !VectorInk.oldLayers(d), "pairs made for Vector Ink aren't old layers (no Merge Old)");
        // The pair put under it and the pen's stroke: one Undo step, one Redo.
        check(v.undo() && InkWashLayers.pairs(v) == 1 && v.lines(InkWashLayers.INK) != null && v.canRedo(), "one Undo takes the pen's stroke and the pair put under");
        check(v.redo() && InkWashLayers.pairs(v) == 2 && v.lines(3) != null && v.layerMarked(1) && !v.canRedo(), "one Redo brings both back");
        check(VectorInk.refusal(v, null) == null, "the eraser is never refused");
        check(VectorInk.NOT_IN.equals(VectorInk.shapeRefusal(v, vector())) && VectorInk.shapeRefusal(v, preset("Steady line")) == null, "no shapes of Vector Ink; another brush's go under it");
        // A lower pair selected: Vector Ink on the top one.
        ToneDocument two = InkWashLayers.blank(200, 120);
        InkWashLayers.add(two, true); InkWashLayers.select(two, 0);
        check(VectorInk.room(two) == 3 && VectorInk.room(two) != VectorInk.ADD, "a lower pair selected: Vector Ink on the empty top one");
        check(VectorInk.oldLayers(two), "two plain pairs are old layers (Merge Old)");
        // Four pairs, the top one marked: no room.
        ToneDocument four = InkWashLayers.blank(200, 120);
        for (int i = 0; i < 3; i++) InkWashLayers.add(four, true);
        draw(four, s, preset("Steady line"), 4, 1, hand(20, 7));
        check(VectorInk.LAYERS_FULL.equals(VectorInk.refusal(four, vector())), "four layers, the top one marked: Layers full");
        ToneDocument full = InkWashLayers.blank(200, 120);
        full.changeLines(InkWashLayers.INK, VectorLines.NONE.with(line(0, 10, 190, 10, 2, 1, VectorLines.MOST_POINTS)), full.top());
        check(VectorInk.refusal(full, vector()) == null && VectorInk.room(full) == VectorInk.ADD, "past the cap, a new layer for it");
        // The owner's scribble (2026-11-04: "unable to make another mark"): a layer filled, the next stroke on a new one on
        // top, both drawn sharp, the eraser cutting both; with four layers full, "Vector ink full".
        ToneDocument busy = InkWashLayers.blank(1920, 2560);
        VectorLines filled = VectorLines.NONE.with(line(0, 10, 1900, 10, 2, 1, VectorLines.MOST_POINTS));
        VectorInk.relay(busy, InkWashLayers.INK, filled, 0, 0, 1920, 2560, null); busy.finish();
        busy.changeLines(InkWashLayers.INK, filled, 0);
        float[][] scrib = scribble(40);
        check(draw(busy, s, vector(), 6, 1, scrib) && InkWashLayers.pairs(busy) == 2 && busy.lines(3) != null && busy.lines(3).size() == 1, "after a full layer, the scribble draws on a new one");
        check(VectorInk.sharpLayers(busy, 0, null) == (1 << 1 | 1 << 3) && VectorInk.shown(busy, VectorInk.sharpLayers(busy, 0, null), null).size() == 2, "both drawn sharp zoomed in");
        DrawingStroke er = StrokeStart.stroke(busy, null, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 30, 0), s, 0);
        for (int i = 0; i < 60; i++) er.sample(30 + 15 * i, 10 + 20 * i, 1, 0, 0);
        er.finish();
        // (The scribble crosses itself: its pieces laid apart where the eraser went darken their overlaps' edges a little.)
        check(busy.lines(1).size() > 1 && busy.lines(3).size() > 1 && drawn(busy, 1), "the eraser cuts both, their pixels the lines'");
        check(busy.undo() && busy.lines(1).size() == 1 && busy.lines(3).size() == 1, "one Undo takes both cuts back");
        ToneDocument four2 = InkWashLayers.blank(200, 120);
        for (int i = 0; i < 3; i++) InkWashLayers.add(four2, true);
        four2.changeLines(7, VectorLines.NONE.with(line(0, 10, 190, 10, 2, 1, VectorLines.MOST_POINTS)), four2.top());
        check(VectorInk.FULL.equals(VectorInk.refusal(four2, vector())), "four layers, the top one full: Vector ink full");
        // A stroke past the cap ends as any (no stuck state): the page draws on after it.
        ToneDocument edge = InkWashLayers.blank(1920, 2560);
        edge.changeLines(InkWashLayers.INK, VectorLines.NONE.with(line(0, 10, 1900, 10, 2, 1, VectorLines.MOST_POINTS - 100)), edge.top());
        check(draw(edge, s, vector(), 6, 1, scrib) && edge.lines(InkWashLayers.INK).size() == 2 && VectorInk.room(edge) == VectorInk.ADD
                && draw(edge, s, vector(), 6, 1, hand(30, 9)) && edge.lines(3) != null, "a stroke over the cap draws whole, the next on a new layer");
        ToneDocument pen = InkWashLayers.blank(200, 120);
        draw(pen, s, preset("Steady line"), 4, 1, hand(20, 1));
        check(VectorInk.sharpLayers(v, 0, null) == 1 << 3 && VectorInk.sharpLayers(pen, 0, null) == 0, "the zoomed view draws its lines sharp, no other layer's");
    }
    static void medium() {
        ModularBrush tool = ModularCode.decode(VectorInk.CODE);
        check(tool.choice(BrushPart.MEDIUM) == PartChoice.VECTOR && ModularCode.encode(tool).equals(VectorInk.CODE) && VectorInk.is(vector()), "the Basic tool is the Vector Ink medium; its code round-trips");
        ModularBrush first = ModularCode.decode(VectorInk.FIRST.replace("vector-ink.", "my-copy."));
        check(first.key().equals(tool.key()) && first.name.equals("my-copy"), "the first code (and a copy of it) reads as the Basic tool, its name kept");
        check(PartChoice.VECTOR.index() == 8 && ModularFeatures.offered(PartChoice.VECTOR) && BrushLab.chips(ModularCode.decode(preset("Steady line").write().split("\t")[1]), BrushPart.MEDIUM).contains(PartChoice.VECTOR), "Medium choice 8, offered in the Medium list");
        // A wet, smeary bristle wash switched to Vector Ink.
        ModularBrush b = preset("Dirty brush").parts.copy();
        b.set(BrushPart.TIP, new ModularBrush.Part(PartChoice.BRISTLES));
        BrushLab.switched(b, PartChoice.VECTOR);
        check(b.choice(BrushPart.TIP) == PartChoice.ROUND && b.choice(BrushPart.CARRY) == PartChoice.OFF && b.choice(BrushPart.LIFE) == PartChoice.NONE
                && b.choice(BrushPart.LATER) == PartChoice.LATER_OFF && b.part(BrushPart.PAPER).get(PartDial.TOOTH) == 0, "switched to Vector Ink: Round, no smudge, Instant, no After, no texture");
        for (BrushPart part : BrushPart.values()) if (part != BrushPart.MEDIUM && part != BrushPart.HAND) check(BrushLab.chips(b, part).isEmpty(), part + ": no choices");
        check(BrushLab.dials(b, BrushPart.TIP).equals(java.util.Arrays.asList(PartDial.WEIGHT)) && BrushLab.dials(b, BrushPart.MEDIUM).isEmpty() && BrushLab.dials(b, BrushPart.CARRY).isEmpty()
                && BrushLab.dials(b, BrushPart.LIFE).isEmpty() && BrushLab.handRows(b).equals(java.util.Arrays.asList(PartDial.GAIN)), "dials: Tip its Size, Medium none, Control its Response");
        BrushLab.switched(b, PartChoice.INK);
        check(!VectorInk.is(b) && !BrushLab.chips(b, BrushPart.TIP).isEmpty(), "back to Ink, every part again");
        // Rolls never pick it; locked, they roll only what it draws.
        java.util.Random r = new java.util.Random(5);
        for (ModularStyle style : ModularStyle.all()) for (int i = 0; i < 60; i++) check(style.roll(r).choice(BrushPart.MEDIUM) != PartChoice.VECTOR, style.name + " never rolls Vector Ink");
        ModularLocks locks = new ModularLocks();
        locks.parts.add(BrushPart.MEDIUM);
        for (ModularStyle style : ModularStyle.all()) for (int i = 0; i < 30; i++) {
            ModularBrush rolled = style.roll(r, tool, locks);
            ModularBrush tidy = rolled.copy(); VectorInk.tidy(tidy);
            check(VectorInk.is(rolled) && ModularCode.encode(tidy).equals(ModularCode.encode(rolled)), style.name + ": Medium locked at Vector Ink, the roll keeps the rest set aside");
        }
        for (int i = 0; i < 40; i++) {
            for (ModularVariations.Made m : ModularVariations.make(tool, new ModularLocks(), r)) { ModularBrush t = m.brush.copy(); VectorInk.tidy(t); check(VectorInk.is(m.brush) && ModularCode.encode(t).equals(ModularCode.encode(m.brush)), "a variation of Vector Ink keeps it, the rest set aside"); }
            for (ModularVariations.Made m : ModularVariations.make(preset("Steady line").parts, new ModularLocks(), r)) check(!VectorInk.is(m.brush), "no variation swaps to Vector Ink");
        }
    }
}
