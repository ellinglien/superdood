package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Random;

/**
 * Sharp dry brushes, the HB pencil pilot ({@link SharpStrokes}, {@link SharpView}): which tools it keeps; a kept stroke
 * drawn again at 1 to a pixel lays the page's pixels byte for byte (tilted, pressed, quick, slow, a tap, a grey, other
 * planes beside it, Undo, a file read back); a smudge over it shows as pixels there alone; drawn at 4 to a pixel it lays
 * the same marks (the same darkness, sixteen points a pixel); the SHRP section reads back as written.
 */
public final class SharpChecks {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        tools(); matches(); finer(); file(); families(); vectorLayers(); stamps(); bristles(); screentone(); shapes();
        System.out.println("PASS: Sharp dry brushes: the spec's rule takes pens, pencils and sticks and leaves smudges, white and washes; "
                + "all 66 Basic tools it takes kept sharp (Riso's misregistered print stays pixels), each tool's page drawn again at 1 to a pixel byte for byte; "
                + "Screentone's five at 40 and 80 px and Halftone's print drawn again byte for byte, at 4 to a pixel the same dots (as dark) with edges finer than a pixel, the eraser's scrape on them pixels; "
                + "Shapes (a line, boxes, ovals, a circle and a square, black and grey, filled and outlined, one past the page's edge, pencil over them) drawn again byte for byte, at 4 to a pixel finer and as dark, kept through the file; "
                + "Stamp strokes carrying their holder's ink on, and one rested, drawn again byte for byte, their kept ink read back from SHRP, at 4 to a pixel their edges finer than a pixel; "
                + "the Bristle brush, a small head and a big one, drawn again byte for byte, at 4 to a pixel its hairs' edges finer than a pixel; "
                + "beside two Vector Ink layers (a full one and the next) left out zoomed, the pencil's plane kept in, matched and drawn finer; "
                + "an HB pencil stroke fed as the log keeps it, drawn again at 1 to a pixel, lays the page's plane byte for byte (tilted, pressed, quick, drawn zoomed, a tap, a grey, a pen line on its own plane beside it, Undo); "
                + "a light pencil over darker marks and a smudge over them show as pixels, the smudge only where it went; at 4 to a pixel the same marks, as dark, sixteen points a pixel; the SHRP section reads back as written, the match holding on the page read back");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    static Brush preset(String name) { return VectorInkChecks.preset(name); }

    static void tools() {
        Brush hb = preset("HB pencil");
        check(SharpStrokes.takes(hb.parts, 5, 1) && SharpStrokes.takes(hb.parts, 5, .4), "the HB pencil is kept sharp");
        for (String yes : new String[]{"Steady line", "2B pencil", "Messy stick", "Stipple pen", "Wax crayon", "Dot tone", "Halftone brush"}) check(SharpStrokes.qualifies(preset(yes).parts), "the spec's rule takes " + yes);
        for (String no : new String[]{"White gel pen", "Blend", "White chalk", "Soft wash", "Vanishing ink", "Vector ink", "Brayer", "Newsprint"}) {
            check(!SharpStrokes.qualifies(preset(no).parts), "the spec's rule leaves " + no);
            check(!SharpStrokes.takes(preset(no).parts, 6, 1), no + " stays pixels");
        }
    }
    /** A stroke as the drawing screen draws it (DrawingPad.feed): recorded, and fed as kept when it is kept sharp; logged with its plane. */
    static void draw(ToneDocument d, WatercolorSession s, Brush b, double px, double dark, float[][] h, float zoom) {
        ToolSwatches.Look look = new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, px, dark);
        StrokeLog.Recorder rec = StrokeLog.Recorder.of(b, look, 0, null, false, false);
        if (SharpStrokes.takes(b.parts, px, dark)) SharpStrokes.keep(rec, b.parts, px, dark, s);
        rec.zoom = zoom;
        DrawingStroke st = StrokeStart.start(d, b, look, s, 0);
        st.zoom(zoom);
        float[] kept = new float[5]; long[] time = new long[1];
        for (int i = 0; i < h[0].length; i++) {
            long t = (long) h[5][i];
            rec.add(h[0][i], h[1][i], h[2][i], h[3][i], h[4][i], t);
            if (rec.keeps) { rec.kept(kept, time); st.sample(kept[0], kept[1], kept[2], kept[3], kept[4], time[0]); }
            else st.sample(h[0][i], h[1][i], h[2][i], h[3][i], h[4][i], t);
        }
        boolean changed = st.finish();
        if (rec.keeps) rec.plane = d.selectedAddress();
        d.logStroke(rec, changed);
        s.settleNow(d);
    }
    /** A hand's stroke: x, y, pressure, tilts, time; {@code n} samples from ({@code x}, {@code y}), {@code ms} apart (jittered), tilted or not. */
    static float[][] hand(Random r, int n, double x, double y, int ms, boolean tilted) {
        float[][] h = new float[6][n];
        double a = r.nextDouble() * 6.3;
        long t = 5000 + r.nextInt(1000);
        for (int i = 0; i < n; i++) {
            a += (r.nextDouble() - .5) * .5; x += (1 + 2 * r.nextDouble()) * Math.cos(a); y += (1 + 2 * r.nextDouble()) * Math.sin(a);
            t += ms + r.nextInt(5);
            h[0][i] = (float) x; h[1][i] = (float) y; h[2][i] = (float) (.15 + .8 * r.nextDouble());
            h[3][i] = tilted ? (float) (-60 + 120 * r.nextDouble()) : Float.NaN; h[4][i] = tilted ? (float) (-60 + 120 * r.nextDouble()) : Float.NaN;
            h[5][i] = t;
        }
        return h;
    }
    /**
     * A page of HB pencil: a grey stroke on the blank page (a light pencil over darker marks lays its grey over them: those
     * show as pixels, {@link #matches}), then strokes over it tilted and upright, slow and quick, one drawn at 250%, a tap,
     * and a pen line across them, last (Undo takes it and the tap).
     */
    static ToneDocument page(BrushTexture surface) {
        ToneDocument d = InkWashLayers.blank(300, 200);
        d.surface = surface;
        WatercolorSession s = new WatercolorSession();
        Random r = new Random(11);
        Brush hb = preset("HB pencil");
        draw(d, s, hb, 6, .45, hand(r, 50, 60, 150, 10, true), 1);
        for (int k = 0; k < 10; k++) draw(d, s, hb, 3 + r.nextInt(8), 1, hand(r, 30 + r.nextInt(40), 40 + r.nextInt(220), 30 + r.nextInt(140), k % 3 == 0 ? 3 : 12, k % 2 == 0), k == 4 ? 2.5f : 1);
        draw(d, s, hb, 6, 1, hand(r, 1, 150, 100, 10, false), 1);
        draw(d, s, preset("Steady line"), 3, 1, hand(r, 60, 20, 100, 8, false), 1);
        return d;
    }
    /** Mismatched page pixels where kept strokes lie, over the page's tiles at grid {@code k}; {@code inside}: those within {@code box} (null: none counted apart). */
    static int[] mismatched(ToneDocument d, int k, int[] box) { return mismatched(d, k, box, 0); }
    /** As above, the layers {@code without} (a bitset, {@link ToneDocument#visibleLayers}) left out as the zoomed view leaves Vector Ink's. */
    static int[] mismatched(ToneDocument d, int k, int[] box, int without) {
        SharpView view = new SharpView(rect -> { });
        List<SharpView.Kept> kept = view.kept(d);
        int out = 0, in = 0, laid = 0;
        for (int y = 0; y < d.height; y += SharpView.TILE) for (int x = 0; x < d.width; x += SharpView.TILE) {
            int w = Math.min(SharpView.TILE, d.width - x), h = Math.min(SharpView.TILE, d.height - y);
            SharpView.Area a = SharpView.Area.of(d, k, x, y, w, h, kept, null, 0, without, null);
            for (int i = 0; i < a.count; i++) {
                List<SharpView.Kept> on = a.strokes.get(i);
                if (on == null) continue;
                SharpStrokes.GridPage once = SharpView.drawn(a, on, 1);
                for (int p = 0; p < w * h; p++) {
                    if (once.alpha(p) > 0) laid++;
                    boolean same = SharpView.holds(once, p, a.tones[i][p] & 255, a.alpha[i][p] & 255);
                    if (!same) { if (box != null && x + p % w >= box[0] && x + p % w < box[2] && y + p / w >= box[1] && y + p / w < box[3]) in++; else out++; }
                }
            }
        }
        return new int[]{out, in, laid};
    }
    static void matches() {
        // A page with no surface (the browser's, Brush Studio's): each brush on its own paper.
        for (BrushTexture surface : new BrushTexture[]{BrushTexture.COLD_PRESS, BrushTexture.PLAIN, null}) {
            ToneDocument d = page(surface);
            int[] m = mismatched(d, 4, null);
            String on = surface == null ? "no surface" : surface.label;
            check(m[2] > 1000, "the HB pencil laid what the check reads on " + on + ": " + m[2]);
            check(m[0] == 0, m[0] + " page pixels of " + m[2] + " differ from the kept strokes drawn again on " + on);
            // Undo the pen line and the tap: the log hides the tap, the plane loses it, they still match.
            d.undo(); d.undo();
            check(mismatched(d, 4, null)[0] == 0, "after Undo the kept strokes still match");
        }
        // A light pencil over darker marks lays its grey over them (ResolvedBrush.lightOver): pixels there.
        ToneDocument d = page(BrushTexture.COLD_PRESS);
        WatercolorSession s = new WatercolorSession();
        draw(d, s, preset("HB pencil"), 8, .2, hand(new Random(5), 60, 150, 100, 10, false), 1);
        check(mismatched(d, 4, null)[0] > 0, "a light pencil over darker marks shows as pixels where it covered them");
        // A smudge (Charcoal's Blend) over part of the page: pixels there alone.
        d = page(BrushTexture.COLD_PRESS);
        float[][] h = hand(new Random(3), 40, 120, 80, 10, false);
        VectorInkChecks.draw(d, s, preset("Blend"), 20, 1, new float[][]{h[0], h[1], h[2]});
        int[] box = {100 - 60, 60 - 60, 260 + 60, 200};
        int[] m = mismatched(d, 4, null);
        check(m[0] > 0, "a smudge over kept strokes shows as pixels");
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int i = 0; i < h[0].length; i++) { x0 = Math.min(x0, h[0][i]); x1 = Math.max(x1, h[0][i]); y0 = Math.min(y0, h[1][i]); y1 = Math.max(y1, h[1][i]); }
        int[] near = {(int) x0 - 24, (int) y0 - 24, (int) x1 + 25, (int) y1 + 25};
        int[] m2 = mismatched(d, 4, near);
        check(m2[0] == 0 && m2[1] > 0, "a smudge's pixels show only where it went: " + m2[0] + " outside, " + m2[1] + " inside");
    }
    /**
     * Step 1: every Basic tool the spec's rule takes and the grid draws, a page of its strokes ({@link SharpTools#page}):
     * drawn again at 1 to a pixel, its pixels to the byte; the families the grid doesn't draw yet stay pixels.
     */
    static void families() {
        int kept = 0;
        java.util.List<String> pixels = new java.util.ArrayList<>();
        for (ToolPresets.Preset p : ToolPresets.all()) {
            Brush b = p.brush();
            if (!SharpStrokes.qualifies(b.parts)) continue;
            double px = SharpTools.size(b);
            if (!SharpStrokes.takes(b.parts, px, 1)) { pixels.add(p.name); continue; }
            int[] m = SharpTools.mismatched(SharpTools.page(b, px, 11), 4);
            check(m[1] > 200 && m[0] == 0, p.name + ": " + m[0] + " of " + m[1] + " page pixels differ drawn again");
            kept++;
        }
        // 66 since Soft pencil (2026-11-06); 75 with the nine texture markers the same day. Pixel pen and Stitch pen stay pixels (their cells read at the centre, SharpStrokes.drawn).
        check(kept == 75 && pixels.equals(java.util.List.of("Riso", "Pixel pen", "Stitch pen")), kept + " Basic tools kept sharp; pixels: " + pixels);
    }
    /**
     * A page with Vector Ink on two layers (layer 2's full, layer 3's) and HB pencil on each, under and over the lines:
     * the zoomed view leaves the Vector Ink planes out (their layers' bitset, VectorInk.sharpLayers), the pencil's planes
     * stay in, its kept strokes match and draw finer there.
     */
    static void vectorLayers() {
        ToneDocument d = InkWashLayers.blank(1920, 2560);
        d.surface = BrushTexture.COLD_PRESS;
        WatercolorSession s = new WatercolorSession();
        Random r = new Random(21);
        Brush hb = preset("HB pencil");
        InkWashLayers.pickLayer(d, 1); InkWashLayers.pickLayer(d, 2);
        for (int k = 0; k < 4; k++) draw(d, s, hb, 6, 1, hand(r, 50, 100 + 60 * k, 100, 10, false), 1);
        // Vector ink over the pencil on layer 2; that one filled (its lines at the cap), the next stroke on layer 3.
        VectorInkChecks.draw(d, s, VectorInkChecks.vector(), 6, 1, VectorInkChecks.scribble(10));
        int ink = VectorInk.layer(d);
        VectorLines filled = VectorLines.NONE.with(VectorInkChecks.line(0, 30, 1900, 30, 2, 1, VectorLines.MOST_POINTS));
        VectorInk.relay(d, ink, filled, 0, 0, 1920, 2560, null); d.finish();
        d.changeLines(ink, filled, 0);
        InkWashLayers.pickLayer(d, 3);
        VectorInkChecks.draw(d, s, VectorInkChecks.vector(), 6, 1, VectorInkChecks.scribble(10));
        for (int k = 0; k < 4; k++) draw(d, s, hb, 6, 1, hand(r, 50, 100 + 60 * k, 160, 10, k % 2 == 0), 1);
        int without = VectorInk.sharpLayers(d, 0, null);
        check(Integer.bitCount(without) == 2, "two Vector Ink layers' planes left out zoomed: " + Integer.toBinaryString(without));
        int pencils = 0;
        for (StrokeLog.Entry e : d.replay()) if (e.sharp()) { pencils++; check((e.plane >> 4) != 1 + ToneDocument.PLANE_LINES, "a pencil's plane stays in the zoomed view"); }
        check(pencils == 8, "the pencil strokes kept: " + pencils);
        int[] m = mismatched(d, 4, null, without);
        check(m[2] > 1000 && m[0] == 0, m[0] + " of " + m[2] + " pencil pixels differ beside two Vector Ink layers");
        SharpView view = new SharpView(rect -> { });
        List<SharpView.Kept> kept = view.kept(d);
        int sharp = 0;
        for (int y = 64; y < 256; y += SharpView.TILE) for (int x = 64; x < 384; x += SharpView.TILE) {
            short[] t = SharpView.render(SharpView.Area.of(d, 4, x, y, SharpView.TILE, SharpView.TILE, kept, null, 0, without, null));
            if (t != SharpView.NONE) for (int p = 0; p < SharpView.whole(4, 0, 0); p++) if (t[p] >= 0) sharp++;
        }
        check(sharp > 16 * 500, "the pencil drawn finer beside them: " + sharp + " points");
    }
    /**
     * Stamp: its ink carries on from its holder's last stroke unless the pen rested a second ({@link StampStamper#startLoad}),
     * kept with the stroke ({@link StrokeLog.Entry#ink}): strokes in quick turn and one after a rest, drawn again at 1 to a
     * pixel, lay the page's bytes (and not with fresh ink); the SHRP section keeps the ink; at 4 to a pixel the tiles match
     * and the prints' edges are finer than a page pixel (not blocky: an edge pixel's sixteen points differ).
     */
    static void stamps() throws Exception {
        ToneDocument d = InkWashLayers.blank(256, 192);
        d.surface = BrushTexture.COLD_PRESS;
        WatercolorSession s = new WatercolorSession();
        s.holding("stamp");
        Brush stamp = preset("Stamp");
        double px = SharpTools.size(stamp);
        Random r = new Random(13);
        long at = 5000;
        for (int k = 0; k < 6; k++) {
            float[][] h = hand(r, 30 + r.nextInt(30), 40 + r.nextInt(170), 30 + r.nextInt(130), 12, k % 2 == 0);
            // Each stroke 300 ms after the last; the fifth after a rest of two seconds.
            long shift = at + (k == 4 ? 2000 : 300) - (long) h[5][0];
            for (int i = 0; i < h[5].length; i++) h[5][i] += shift;
            at = (long) h[5][h[5].length - 1];
            draw(d, s, stamp, px, 1, h, 1);
        }
        List<StrokeLog.Entry> log = d.replay();
        check(log.size() == 6, "six Stamp strokes logged: " + log.size());
        int carried = 0;
        for (StrokeLog.Entry e : log) { check(e.sharp(), "a Stamp stroke is kept sharp"); if (e.ink < 1) carried++; }
        check(log.get(0).ink == 1 && log.get(4).ink == 1 && carried >= 3, "the ink carries on from stroke to stroke, fresh after a rest: " + carried);
        int[] m = mismatched(d, 4, null);
        check(m[2] > 1000 && m[0] == 0, m[0] + " of " + m[2] + " Stamp pixels differ drawn again");
        // Drawn again with fresh ink, a stroke that carried ink on lays other bytes: the kept ink is what matches.
        StrokeLog.Entry e = log.get(2);
        SharpStrokes.Laid laid = SharpStrokes.Laid.of(e, d.surface);
        FineGrid one = FineGrid.over(1, 0, 0, d.width, d.height, d.width, d.height);
        SharpStrokes.GridPage kept = new SharpStrokes.GridPage(d.width, d.height), fresh = new SharpStrokes.GridPage(d.width, d.height);
        SharpStrokes.draw(e, laid, new StrokePaper(BrushTexture.COLD_PRESS), one, kept, Double.NaN);
        SharpStrokes.draw(e.sharp(e.plane, e.zoom, 1), laid, new StrokePaper(BrushTexture.COLD_PRESS), one, fresh, Double.NaN);
        check(e.ink < 1 && !java.util.Arrays.equals(kept.tones, fresh.tones), "a carried-on stroke drawn with fresh ink differs");
        // The file keeps the ink.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocumentCodec.write(out, d.layerSnapshot());
        ToneDocument back = DocumentCodec.read(new ByteArrayInputStream(out.toByteArray()));
        back.surface = d.surface;
        for (int i = 0; i < log.size(); i++) check(back.replay().get(i).ink == log.get(i).ink && back.replay().get(i).sharp(), "Stamp stroke " + i + " reads back with its ink");
        check(mismatched(back, 4, null)[0] == 0, "the Stamp page read back matches its kept strokes");
        int[] f = finerEdges(d);
        check(f[1] > 500 && f[0] > f[1] / 4, "at 4 to a pixel a print's edge pixels are finer than a pixel: " + f[0] + " of " + f[1]);
    }
    /**
     * The Bristle brush, at a small head (its hairs a step a pixel) and a big one (swept steps, wider lanes): drawn again at
     * 1 to a pixel byte for byte; at 4 to a pixel its hairs' edges finer than a pixel.
     */
    static void bristles() {
        Brush b = preset("Bristle brush");
        for (double px : new double[]{20, 40}) {
            ToneDocument d = SharpTools.page(b, px, 17);
            int[] m = SharpTools.mismatched(d, 4);
            check(m[1] > 2000 && m[0] == 0, "Bristle brush " + px + ": " + m[0] + " of " + m[1] + " pixels differ drawn again");
            int[] f = finerEdges(d);
            check(f[1] > 500 && f[0] > f[1] / 4, "Bristle brush " + px + ": at 4 to a pixel its edge pixels are finer than a pixel: " + f[0] + " of " + f[1]);
        }
    }
    /**
     * Screentone (the owner, 2026-11-05: "screentone dots are my top thing that i love"): its five patterns at their
     * Medium and Large sizes and Halftone's print, drawn again at 1 to a pixel byte for byte; at 4 to a pixel the same
     * dots, as dark, their edges finer than a pixel ({@link PrintScreen#ink} read between pixels); where the eraser
     * scraped them the page's pixels show (nothing sharp over the scraped half), the rest stays sharp.
     */
    static void screentone() {
        for (String name : new String[]{"Dot tone", "Line tone", "Sand tone", "Gradient tone", "Hatched tone", "Halftone brush"}) {
            Brush b = preset(name);
            for (double px : name.equals("Halftone brush") ? new double[]{6} : new double[]{40, 80}) {
                ToneDocument d = SharpTools.page(b, px, 19);
                int[] m = SharpTools.mismatched(d, 4);
                check(m[1] > 1000 && m[0] == 0, name + " " + px + ": " + m[0] + " of " + m[1] + " pixels differ drawn again");
                double[] dark = darkness(d);
                check(Math.abs(dark[1] / 16 / dark[0] - 1) < .05, name + " " + px + ": at 4 to a pixel as dark: " + dark[1] / 16 / dark[0]);
                int[] f = finerEdges(d);
                // Sand's grain is the page's pixels (PrintScreen.between): only its stroke's edge is finer.
                if (!name.equals("Sand tone")) check(f[1] > 300 && f[0] > f[1] / 4, name + " " + px + ": at 4 to a pixel its dots' edges finer than a pixel: " + f[0] + " of " + f[1]);
                if (!name.equals("Dot tone") || px != 40) continue;
                // The eraser over the left half: where it scraped a dot, the page's pixels (no sharp point there); the right half's stay sharp.
                int[] before = new int[d.width * d.height];
                for (int p = 0; p < before.length; p++) before[p] = d.compositeTone(p % d.width, p / d.width);
                EraseSharpChecks.erase(d, new WatercolorSession(), 0, false, 100);
                List<SharpView.Kept> kept = new SharpView(rect -> { }).kept(d);
                int scraped = 0, shown = 0, side = SharpView.TILE * 4, paper = d.paperTone();
                for (int y = 0; y < d.height; y += SharpView.TILE) for (int x = 0; x < d.width; x += SharpView.TILE) {
                    short[] t = SharpView.render(SharpView.Area.of(d, 4, x, y, SharpView.TILE, SharpView.TILE, kept, null, 0, 0, null));
                    for (int py = 0; py < SharpView.TILE; py++) for (int q = 0; q < SharpView.TILE; q++) {
                        if (before[(y + py) * d.width + x + q] >= paper || d.compositeTone(x + q, y + py) < paper) continue;
                        scraped++;
                        if (t != SharpView.NONE) for (int e = 0; e < 16; e++) { int v = t[(py * 4 + e / 4) * side + q * 4 + e % 4]; if (v >= 0 && v < paper) { shown++; break; } }
                    }
                }
                check(scraped > 500 && shown == 0, "Dot tone: where the eraser scraped dots, no sharp dot drawn: " + shown + " of " + scraped);
                check(EraseSharpChecks.points(d, 4, new int[]{128, 0, d.width, d.height}) > 16 * 500, "Dot tone: beside the eraser, its dots still drawn finer");
            }
        }
    }
    /**
     * Shapes (Shapes' line, box, oval, circle and square, laid as {@code DrawingPad} lays them, kept on their plane):
     * drawn again at 1 to a pixel byte for byte, a grey one opaque over the paper and pencil over it; at 4 to a pixel as
     * dark and their edges finer than a pixel; the SHRP section (version 3) keeps them, the match holding read back.
     */
    static void shapes() throws Exception {
        ToneDocument d = InkWashLayers.blank(320, 192);
        d.surface = BrushTexture.COLD_PRESS;
        WatercolorSession s = new WatercolorSession();
        Brush hb = preset("HB pencil");
        Random r = new Random(23);
        draw(d, s, hb, 6, 1, hand(r, 60, 40, 40, 10, false), 1);
        shape(d, s, hb, ToolSettings.Shape.LINE, false, 5, 0, 20.3f, 30.7f, 270.2f, 170.9f);
        shape(d, s, hb, ToolSettings.Shape.RECTANGLE, false, 7, 128, 40.4f, 50.2f, 150.6f, 120.8f);
        shape(d, s, hb, ToolSettings.Shape.OVAL, true, 1, 0, 160.5f, 20.1f, 230.3f, 90.6f);
        shape(d, s, hb, ToolSettings.Shape.CIRCLE, false, 3, 200, 200.2f, 140.7f, 230.9f, 158.4f);
        shape(d, s, hb, ToolSettings.Shape.SQUARE, true, 1, 90, 20.6f, 140.3f, 60.1f, 180.2f);
        // Past the page's right edge: its corner stops at the edge, at every grid.
        shape(d, s, hb, ToolSettings.Shape.RECTANGLE, false, 4, 0, 250.5f, 100.5f, 360f, 180.5f);
        draw(d, s, hb, 6, 1, hand(r, 60, 60, 80, 10, true), 1);
        int shapes = 0;
        for (StrokeLog.Entry e : d.replay()) if (e.kind == StrokeLog.SHAPE) { check(e.sharp(), "a shape is kept"); shapes++; }
        check(shapes == 6, "six shapes logged: " + shapes);
        int[] m = mismatched(d, 4, null);
        check(m[2] > 5000 && m[0] == 0, "shapes: " + m[0] + " of " + m[2] + " pixels differ drawn again");
        double[] dark = darkness(d);
        check(Math.abs(dark[1] / 16 / dark[0] - 1) < .03, "shapes at 4 to a pixel as dark: " + dark[1] / 16 / dark[0]);
        int[] f = finerEdges(d);
        check(f[1] > 500 && f[0] > f[1] / 4, "shapes at 4 to a pixel: their edges finer than a pixel: " + f[0] + " of " + f[1]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocumentCodec.write(out, d.layerSnapshot());
        ToneDocument back = DocumentCodec.read(new ByteArrayInputStream(out.toByteArray()));
        back.surface = d.surface;
        int kept = 0;
        for (StrokeLog.Entry e : back.replay()) if (e.kind == StrokeLog.SHAPE && e.sharp()) kept++;
        check(kept == 6 && mismatched(back, 4, null)[0] == 0, "the shapes read back kept and matching: " + kept);
    }
    /** A shape as the drawing screen lays it ({@code DrawingPad.continueShape}): one Undo step on its brush's layer, logged kept. */
    static void shape(ToneDocument d, WatercolorSession s, Brush b, ToolSettings.Shape kind, boolean filled, int width, int shade, float x0, float y0, float x1, float y1) {
        StrokeStart.shape(d, b.parts, s);
        StrokeLog.Shape sh = new StrokeLog.Shape(kind, filled, width, shade, x0, y0, x1, y1);
        boolean changed = ShapeStroke.lay(d, sh.spans(d.width, d.height), shade);
        d.logMade(StrokeLog.Entry.shape(0, ModularCode.encode(b.parts), sh).sharp(d.selectedAddress(), 1, 1), changed);
    }
    /** {at 1, at 4} to a pixel: the darkness the kept strokes lay over the page's tiles. */
    static double[] darkness(ToneDocument d) {
        List<SharpView.Kept> kept = new SharpView(rect -> { }).kept(d);
        double once = 0, four = 0;
        for (int y = 0; y < d.height; y += SharpView.TILE) for (int x = 0; x < d.width; x += SharpView.TILE) {
            SharpView.Area a = SharpView.Area.of(d, 4, x, y, Math.min(SharpView.TILE, d.width - x), Math.min(SharpView.TILE, d.height - y), kept, null, 0, 0, null);
            for (int i = 0; i < a.count; i++) {
                List<SharpView.Kept> on = a.strokes.get(i);
                if (on == null) continue;
                for (int v : SharpView.drawn(a, on, 1).tones) once += 255 - v;
                for (int v : SharpView.drawn(a, on, 4).tones) four += 255 - v;
            }
        }
        return new double[]{once, four};
    }
    /** {finer, edges}: page pixels drawn finer at 4 to a pixel that are part covered, and of those, how many hold points of more than one grey (not blocky). */
    static int[] finerEdges(ToneDocument d) {
        SharpView view = new SharpView(rect -> { });
        List<SharpView.Kept> all = view.kept(d);
        int finer = 0, edges = 0, side = SharpView.TILE * 4;
        for (int y = 0; y < d.height; y += SharpView.TILE) for (int x = 0; x < d.width; x += SharpView.TILE) {
            short[] t = SharpView.render(SharpView.Area.of(d, 4, x, y, SharpView.TILE, SharpView.TILE, all, null, 0, 0, null));
            if (t == SharpView.NONE) continue;
            for (int py = 0; py < SharpView.TILE; py++) for (int pxl = 0; pxl < SharpView.TILE; pxl++) {
                int lo = 255, hi = 0;
                boolean shown = true;
                for (int q = 0; q < 16 && shown; q++) {
                    int v = t[(py * 4 + q / 4) * side + pxl * 4 + q % 4];
                    if (v < 0) shown = false; else { lo = Math.min(lo, v); hi = Math.max(hi, v); }
                }
                if (!shown || lo == 255 || hi == lo && lo < 64) continue;
                edges++;
                if (hi - lo > 32) finer++;
            }
        }
        return new int[]{finer, edges};
    }
    static void finer() {
        ToneDocument d = page(BrushTexture.COLD_PRESS);
        SharpView view = new SharpView(rect -> { });
        List<SharpView.Kept> kept = view.kept(d);
        double once = 0, four = 0;
        int sharp = 0;
        for (int y = 0; y < d.height; y += SharpView.TILE) for (int x = 0; x < d.width; x += SharpView.TILE) {
            int w = Math.min(SharpView.TILE, d.width - x), h = Math.min(SharpView.TILE, d.height - y);
            SharpView.Area a = SharpView.Area.of(d, 4, x, y, w, h, kept, null, 0, 0, null);
            for (int i = 0; i < a.count; i++) {
                List<SharpView.Kept> on = a.strokes.get(i);
                if (on == null) continue;
                for (int v : SharpView.drawn(a, on, 1).tones) once += 255 - v;
                for (int v : SharpView.drawn(a, on, 4).tones) four += 255 - v;
            }
            short[] t = SharpView.render(a);
            if (t != SharpView.NONE) for (int p = 0; p < SharpView.whole(4, 0, 0); p++) if (t[p] >= 0) sharp++;
        }
        double ratio = four / 16 / once;
        check(Math.abs(ratio - 1) < .06, "at 4 to a pixel the kept strokes lay as much darkness: " + ratio);
        check(sharp > 16 * 1000, "the tiles draw the kept strokes finer: " + sharp + " points");
    }
    static void file() throws Exception {
        ToneDocument d = page(BrushTexture.COLD_PRESS);
        byte[] sharp = d.layerSnapshot().log.encodeSharp();
        check(sharp != null, "a page with kept strokes writes a SHRP section");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocumentCodec.write(out, d.layerSnapshot());
        ToneDocument back = DocumentCodec.read(new ByteArrayInputStream(out.toByteArray()));
        back.surface = d.surface;
        int kept = 0, read = 0;
        for (StrokeLog.Entry e : d.replay()) if (e.sharp()) kept++;
        List<StrokeLog.Entry> again = back.replay();
        for (int i = 0; i < again.size(); i++) {
            StrokeLog.Entry e = again.get(i), was = d.replay().get(i);
            check(e.plane == was.plane && e.zoom == was.zoom, "entry " + i + " keeps its plane and zoom");
            if (e.sharp()) read++;
        }
        check(kept == 13 && read == kept, "the kept strokes read back (twelve pencil, one pen): " + read + " of " + kept);
        check(mismatched(back, 4, null)[0] == 0, "the page read back matches its kept strokes");
        check(StrokeLog.NONE.withSharp(sharp) == StrokeLog.NONE && d.replay().size() == back.replay().size(), "a SHRP section without its log names nothing");
    }
}
