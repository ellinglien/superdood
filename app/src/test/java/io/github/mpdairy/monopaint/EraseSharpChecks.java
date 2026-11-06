package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Random;

/**
 * The owner, 2026-11-05: "I'm not able to erase the HB pencil." HB pencil strokes kept sharp ({@link SharpStrokes}), the
 * eraser over them as the drawing screen starts it ({@link StrokeStart}, logged), on Everything and on Dry Media: the
 * page's pixels go, the zoomed view's tiles at 2 and 4 to a pixel draw nothing of them again, and so after the file is
 * written and read back. The owner, 2026-11-05: "proceed with the erase everything under it change": pencil on a lower
 * pair, Vector Ink over it, a wash on each pair, the upper pair selected: the eraser takes what is under it on every pair,
 * one Undo step, after the file is read back and in a replay too.
 */
public final class EraseSharpChecks {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        for (int kinds : new int[]{0, ToneDocument.KIND_PENCIL}) for (boolean finger : new boolean[]{false, true}) { erased(kinds, finger); beside(kinds, finger); undone(kinds, finger); allPairs(kinds, finger); }
        System.out.println("PASS: Erase sharp pencil: HB pencil kept sharp, erased by the pen and by a finger on Everything and on Dry Media, leaves no pixels, "
                + "no sharp tile points at 2, 4 and 8 to a pixel, and stays erased after the file is read back; "
                + "with a pen line under it and Vector Ink over it, its plane is emptied too; "
                + "after Undo of a Vector Ink stroke drawn after it (Undo selects Vector Ink's pair) it is erased too, on the page and reopened; "
                + "on every pair (pencil and a wash below, Vector Ink and a wash above, the upper selected), as one Undo step, read back and replayed alike");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static void erased(int kinds, boolean finger) throws Exception {
        ToneDocument d = InkWashLayers.blank(300, 200);
        d.surface = BrushTexture.COLD_PRESS;
        WatercolorSession s = new WatercolorSession();
        Random r = new Random(7);
        Brush hb = SharpChecks.preset("HB pencil");
        for (int k = 0; k < 6; k++) SharpChecks.draw(d, s, hb, 6, 1, SharpChecks.hand(r, 40, 80 + 25 * k, 100, 10, k % 2 == 0), 1);
        String on = (kinds == 0 ? "Everything" : "Dry Media") + (finger ? ", a finger" : ", the pen");
        check(dark(d) > 500, "the pencil laid marks");
        check(points(d, 4) > 1000, "the pencil is drawn finer before erasing");
        erase(d, s, kinds, finger);
        check(dark(d) == 0, on + ": pencil pixels left after erasing: " + dark(d));
        for (int k : new int[]{2, 4, 8}) check(points(d, k) == 0, on + ": sharp tile points at " + k + " to a pixel after erasing: " + points(d, k));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocumentCodec.write(out, d.layerSnapshot());
        ToneDocument back = DocumentCodec.read(new ByteArrayInputStream(out.toByteArray()));
        back.surface = d.surface;
        check(dark(back) == 0, on + ": pencil pixels back after reading the file: " + dark(back));
        for (int k : new int[]{2, 4, 8}) check(points(back, k) == 0, on + ": sharp tile points at " + k + " after reading the file: " + points(back, k));
    }
    /** A pen line on the pencil's layer and a Vector Ink stroke over it: the eraser still empties the pencil's plane. */
    static void beside(int kinds, boolean finger) {
        ToneDocument d = InkWashLayers.blank(300, 200);
        d.surface = BrushTexture.COLD_PRESS;
        WatercolorSession s = new WatercolorSession();
        Random r = new Random(7);
        SharpChecks.draw(d, s, SharpChecks.preset("Steady line"), 3, 1, SharpChecks.hand(r, 60, 20, 100, 8, false), 1);
        for (int k = 0; k < 4; k++) SharpChecks.draw(d, s, SharpChecks.preset("HB pencil"), 6, 1, SharpChecks.hand(r, 40, 80 + 25 * k, 100, 10, false), 1);
        VectorInkChecks.draw(d, s, VectorInkChecks.vector(), 6, 1, VectorInkChecks.scribble(3));
        check(dry(d) > 500, "the pencil laid marks beside the pen and Vector Ink");
        erase(d, s, kinds, finger);
        check(dry(d) == 0, (kinds == 0 ? "Everything" : "Dry Media") + (finger ? ", a finger" : ", the pen") + ": pencil left beside the pen and Vector Ink: " + dry(d));
    }
    /**
     * The owner's page (2026-11-05, "I'm not able to erase the HB pencil", on Everything, a page drawn on before): Vector
     * Ink, the pencil (on a pair under it), Vector Ink again, then Undo, which selects Vector Ink's pair; the page left and
     * reopened keeps it so. The eraser still takes the pencil.
     */
    static void undone(int kinds, boolean finger) throws Exception {
        for (boolean reopened : new boolean[]{false, true}) {
            ToneDocument d = InkWashLayers.blank(300, 200);
            d.surface = BrushTexture.COLD_PRESS;
            WatercolorSession s = new WatercolorSession();
            Random r = new Random(7);
            VectorInkChecks.draw(d, s, VectorInkChecks.vector(), 6, 1, VectorInkChecks.scribble(3));
            for (int k = 0; k < 3; k++) SharpChecks.draw(d, s, SharpChecks.preset("HB pencil"), 6, 1, SharpChecks.hand(r, 40, 80 + 25 * k, 100, 10, false), 1);
            VectorInkChecks.draw(d, s, VectorInkChecks.vector(), 6, 1, VectorInkChecks.scribble(4));
            d.undo();
            check(VectorInk.layer(d) >= 0, "Undo of Vector Ink selects its pair");
            if (reopened) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                DocumentCodec.write(out, d.layerSnapshot());
                d = DocumentCodec.read(new ByteArrayInputStream(out.toByteArray()));
                d.surface = BrushTexture.COLD_PRESS;
                s = new WatercolorSession();
            }
            int active = d.activeLayer();
            check(dry(d) > 500, "the pencil laid marks under Vector Ink");
            erase(d, s, kinds, finger);
            String on = (kinds == 0 ? "Everything" : "Dry Media") + (finger ? ", a finger" : ", the pen") + (reopened ? ", reopened" : "");
            check(dry(d) == 0, on + ": pencil left after Undo of Vector Ink: " + dry(d));
            check(dark(d) == 0, on + ": pixels left after Undo of Vector Ink: " + dark(d));
            check(d.activeLayer() == active, on + ": the eraser leaves the selection as it was");
        }
    }
    /**
     * Pencil and a wash on the lower pair, Vector Ink and a wash on the upper (added for it, selected by its Redo), as the drawing
     * screen lays and logs them; the eraser over the left of the page. Everything leaves paper there on every pair;
     * Dry Media the pencil alone. The right keeps all. One Undo puts every pair back; the file and a replay agree.
     */
    static void allPairs(int kinds, boolean finger) throws Exception {
        ToneDocument d = InkWashLayers.blank(300, 200);
        d.surface = BrushTexture.COLD_PRESS;
        WatercolorSession s = new WatercolorSession();
        for (int k = 0; k < 4; k++) SharpChecks.draw(d, s, SharpChecks.preset("HB pencil"), 6, 1, line(40 + 40 * k), 1);
        SharpChecks.draw(d, s, SharpChecks.preset("Soft wash"), 30, .5, line(60), 1);
        SharpChecks.draw(d, s, VectorInkChecks.vector(), 6, 1, line(100), 1);
        // Undo and Redo of Vector Ink select its pair (as after 8b42d83c's report): the next wash goes on it.
        check(d.undo() && d.redo(), "Vector Ink's stroke undone and redone");
        SharpChecks.draw(d, s, SharpChecks.preset("Soft wash"), 30, .5, line(140), 1);
        String on = "every pair, " + (kinds == 0 ? "Everything" : "Dry Media") + (finger ? ", a finger" : ", the pen");
        check(InkWashLayers.pairs(d) == 2 && InkWashLayers.current(d) == 1 && d.lines(3) != null, on + ": Vector Ink on a pair of its own, selected: " + InkWashLayers.pairs(d) + " pairs, " + d.activeLayer() + " active");
        int[] left = {0, 0, 100, 200}, right = {200, 0, 300, 200};
        for (int[] a : new int[][]{left, right}) check(covered(d, 1, ToneDocument.PLANE_DRY, a) > 100 && covered(d, 0, -2, a) > 100 && covered(d, 2, -2, a) > 100 && covered(d, 3, -2, a) > 100,
                on + ": pencil below, a wash on each pair and Vector Ink above, left and right");
        java.util.List<byte[]> before = planes(d);
        int lines = d.lines(3).size();
        erase(d, s, kinds, finger, 120);
        check(covered(d, 1, ToneDocument.PLANE_DRY, left) == 0, on + ": pencil left on the lower pair");
        if (kinds == 0) check(dark(d, left) == 0, on + ": marks left under the eraser: " + dark(d, left));
        else check(covered(d, 0, -2, left) > 100 && covered(d, 2, -2, left) > 100 && covered(d, 3, -2, left) > 100 && d.lines(3).size() == lines, on + ": the washes and Vector Ink stay");
        check(covered(d, 1, ToneDocument.PLANE_DRY, right) > 100 && covered(d, 0, -2, right) > 100 && covered(d, 2, -2, right) > 100 && covered(d, 3, -2, right) > 100, on + ": the right keeps every mark");
        for (int k : new int[]{2, 4, 8}) check(points(d, k, left) == 0, on + ": sharp tile points at " + k + " to a pixel after erasing");
        // The file, then a replay of the page's log on a blank copy: the same page.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocumentCodec.write(out, d.layerSnapshot());
        ToneDocument back = DocumentCodec.read(new ByteArrayInputStream(out.toByteArray()));
        back.surface = d.surface;
        check(shown(back).equals(shown(d)), on + ": the page read back shows as it did");
        for (int k : new int[]{2, 4, 8}) check(points(back, k, left) == 0, on + ": sharp tile points at " + k + " after reading the file");
        ReplayRun run = ReplayRun.of(d, 1, ReplayRun.session(null));
        for (int k = 0; k < 10000 && !run.step(); k++) { }
        run.finish();
        check(covered(run.copy, -1, ToneDocument.PLANE_DRY, left) == 0 && (kinds != 0 || dark(run.copy, left) == 0) && dark(run.copy, right) > 1000, on + ": the replay erases as the page did");
        check(d.undo() && java.util.Arrays.deepEquals(planes(d).toArray(), before.toArray()), on + ": one Undo puts every pair back");
    }
    /** A straight stroke across the page at {@code y}: x, y, pressure, tilts, time. */
    static float[][] line(float y) {
        int n = 140;
        float[][] h = new float[6][n];
        for (int i = 0; i < n; i++) { h[0][i] = 10 + 2 * i; h[1][i] = y; h[2][i] = .7f; h[3][i] = h[4][i] = Float.NaN; h[5][i] = 5000 + 12 * i; }
        return h;
    }
    /** Covered pixels in {@code a} ({left, top, right, bottom}) of layer {@code layer}'s plane {@code p} (-1 its base, -2 base and planes; layer -1 every layer). */
    static int covered(ToneDocument d, int layer, int p, int[] a) {
        int n = 0;
        java.util.List<ToneDocument.Layer> all = d.layerSnapshot().layers;
        for (int l = 0; l < all.size(); l++) {
            if (layer >= 0 && l != layer) continue;
            for (int q = -1; q < ToneDocument.PLANES; q++) {
                if (p != -2 && q != p) continue;
                ToneDocument.Layer x = q < 0 ? all.get(l) : all.get(l).plane(q);
                if (x != null) for (int y = a[1]; y < a[3]; y++) for (int c = a[0]; c < a[2]; c++) if (x.alpha[y * d.width + c] != 0) n++;
            }
        }
        return n;
    }
    /** Every layer's base and planes, tones then coverage. */
    static java.util.List<byte[]> planes(ToneDocument d) {
        java.util.List<byte[]> out = new java.util.ArrayList<>();
        for (ToneDocument.Layer l : d.layerSnapshot().layers) for (int q = -1; q < ToneDocument.PLANES; q++) {
            ToneDocument.Layer x = q < 0 ? l : l.plane(q);
            if (x != null) { out.add(x.tones.clone()); out.add(x.alpha.clone()); }
        }
        return out;
    }
    /** The page as shown, every pixel. */
    static java.util.List<Integer> shown(ToneDocument d) {
        int[] out = new int[d.width * d.height];
        d.compositeTones(0, 0, d.width, d.height, out, 0, d.width, 0);
        java.util.List<Integer> l = new java.util.ArrayList<>();
        for (int v : out) l.add(v);
        return l;
    }
    /** Covered pixels on every layer's Dry Media plane. */
    static int dry(ToneDocument d) { return covered(d, -1, ToneDocument.PLANE_DRY, new int[]{0, 0, d.width, d.height}); }
    /** The eraser as the drawing screen starts and logs it (the pen's, InkWash.stroke; a finger's, InkWash.fingerEraser), broad, row after row over the whole page. */
    static void erase(ToneDocument d, WatercolorSession s, int kinds, boolean finger) { erase(d, s, kinds, finger, d.width + 20); }
    /** As above, its samples left of {@code right}. */
    static void erase(ToneDocument d, WatercolorSession s, int kinds, boolean finger, int right) {
        ToolSwatches.Look look = new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 60, 1);
        StrokeLog.Recorder rec = finger ? StrokeLog.Recorder.fingerEraser(60, kinds) : StrokeLog.Recorder.of(null, look, kinds, null, false, false);
        DrawingStroke st = finger ? new EraseBothStroke(d, 60f, kinds) : StrokeStart.start(d, null, look, s, kinds);
        long t = 5000;
        for (int y = -20; y <= d.height + 20; y += 15) for (int x = -20; x <= right; x += 10) {
            t += 8; rec.add(x, y, 1, Float.NaN, Float.NaN, t); st.sample(x, y, 1, Float.NaN, Float.NaN, t);
        }
        d.logStroke(rec, st.finish());
        s.settleNow(d);
    }
    /** Page pixels darker than the paper. */
    static int dark(ToneDocument d) { return dark(d, new int[]{0, 0, d.width, d.height}); }
    static int dark(ToneDocument d, int[] a) {
        int n = 0, paper = d.paperTone();
        for (int y = a[1]; y < a[3]; y++) for (int x = a[0]; x < a[2]; x++) if (d.compositeTone(x, y) < paper) n++;
        return n;
    }
    /** The zoomed view's tile points at grid {@code k} drawn darker than the paper over the whole page. */
    static int points(ToneDocument d, int k) { return points(d, k, new int[]{0, 0, d.width, d.height}); }
    /** As above over the tiles in {@code a} ({left, top, right, bottom}, on tile edges). */
    static int points(ToneDocument d, int k, int[] a) {
        SharpView view = new SharpView(rect -> { });
        List<SharpView.Kept> kept = view.kept(d);
        int n = 0, paper = d.paperTone();
        for (int y = a[1]; y < a[3]; y += SharpView.TILE) for (int x = a[0]; x < a[2]; x += SharpView.TILE) {
            int w = Math.min(SharpView.TILE, a[2] - x), h = Math.min(SharpView.TILE, a[3] - y);
            short[] t = SharpView.render(SharpView.Area.of(d, k, x, y, w, h, kept, null, 0, 0, null));
            if (t != SharpView.NONE) for (int p = 0; p < SharpView.whole(k, 0, 0); p++) if (t[p] >= 0 && t[p] < paper) n++;
        }
        return n;
    }
}
