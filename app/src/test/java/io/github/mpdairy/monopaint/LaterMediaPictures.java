package io.github.mpdairy.monopaint;

import java.io.File;

/**
 * Later's media drawn by the app's engine (scripts/later_media_pictures.sh): for each, a typical brush as laid and
 * finished side by side, its macro dial low, as it comes and high, and a close look (2×) at the finished mark. Salt and
 * Plastic Wrap on a Slow watercolour wash; Feathering on an Instant fountain pen and a Quick brush pen; Foxing on a pen
 * line and a wash. Composites at full grey, at the page's own pixels (the Manta's 300 dpi).
 */
public class LaterMediaPictures {
    static final int W = 420, H = 300;
    public static void main(String[] a) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(a.length > 0 ? a[0] : "/tmp/later-media-pictures");
        out.mkdirs();
        if (a.length > 1 && a[1].equals("later2")) { later2(out); System.out.println("wrote " + out); return; }
        ModularBrush wash = slow("Soft wash"), fountain = FloatChecks.preset("Fountain pen"), brushPen = FloatChecks.preset("Swell brush");
        set(out, "salt", wash, PartChoice.LATER_SALT, 60, wash());
        set(out, "wrap", wash, PartChoice.LATER_WRAP, 60, wash());
        set(out, "feather-pen", fountain, PartChoice.LATER_FEATHER, 10, writing());
        set(out, "feather-brush", brushPen, PartChoice.LATER_FEATHER, 14, writing());
        set(out, "fox-pen", fountain, PartChoice.LATER_FOX, 10, writing());
        set(out, "fox-wash", wash, PartChoice.LATER_FOX, 60, wash());
        papers(out, fountain);
        System.out.println("wrote " + out);
    }
    /**
     * Feathering on the fountain pen on contrasting papers (Smooth; Cold Press, Unryu; Laid and Canvas, which have a
     * direction): feather-paper-{name}.png as laid and finished, and -close.png a close look (3×) at the finished
     * mark with the paper's grain shown faintly under it (the page itself shows no grain), so the threads can be seen
     * running in its valleys.
     */
    static void papers(File out, ModularBrush pen) throws Exception {
        Object[][] papers = {{"smooth", BrushTexture.PLAIN, 0.0}, {"cold-press", BrushTexture.COLD_PRESS, .6}, {"unryu", BrushTexture.UNRYU, .6},
                {"laid", BrushTexture.LAID, .6}, {"canvas", BrushTexture.CANVAS, .6}};
        for (Object[] paper : papers) {
            BrushTexture texture = (BrushTexture) paper[1];
            ModularBrush b = with(onPaper(pen, texture, (Double) paper[2]), PartChoice.LATER_FEATHER, -1);
            int[][] laid = draw(b, 10, writing(), false), done = draw(b, 10, writing(), true);
            WetSmudgePictures.save(new File(out, "feather-paper-" + paper[0] + ".png"), laid, done);
            int x0 = 150, y0 = 95, w = 140, h = 100;
            int[][] o = new int[3 * h][3 * w];
            for (int y = 0; y < 3 * h; y++) for (int x = 0; x < 3 * w; x++) {
                int px = x0 + x / 3, py = y0 + y / 3;
                float shade = texture == BrushTexture.PLAIN ? 1 : .84f + .16f * LaterMedia.height(texture.grain(), px, py);
                o[y][x] = Math.round(done[py][px] * shade);
            }
            WetSmudgePictures.save(new File(out, "feather-paper-" + paper[0] + "-close.png"), o);
        }
    }
    /**
     * Later's print, crafts and ageing (2026-11-01; docs/archive/pictures/explore/later2): Letterpress on a bold brush
     * pen's writing, smooth and on cold press; Torn Paper on a wash and a marker; Cross-Stitch on a wash and on writing;
     * Cracks on thick gouache; Tarnish on the Metalpoint preset's hatching and writing.
     */
    static void later2(File out) throws Exception {
        ModularBrush brushPen = FloatChecks.preset("Swell brush"), wash = slow("Soft wash"), marker = FloatChecks.preset("Round marker");
        ModularBrush gouache = slow("Opaque round"), metal = FloatChecks.preset("Metalpoint");
        set(out, "letterpress", brushPen, PartChoice.LATER_PRESS, 26, writing());
        set(out, "letterpress-cold-press", onPaper(brushPen, BrushTexture.COLD_PRESS, .7), PartChoice.LATER_PRESS, 26, writing());
        set(out, "torn-wash", wash, PartChoice.LATER_TORN, 60, wash());
        set(out, "torn-marker", marker, PartChoice.LATER_TORN, 30, writing());
        set(out, "stitch-wash", wash, PartChoice.LATER_STITCH, 60, wash());
        set(out, "stitch-writing", marker, PartChoice.LATER_STITCH, 10, writing());
        set(out, "cracks", gouache, PartChoice.LATER_CRACKS, 60, wash());
        set(out, "cracks-brush", brushPen, PartChoice.LATER_CRACKS, 30, writing());
        set(out, "tarnish", metal, PartChoice.LATER_TARNISH, 6, hatching());
        set(out, "tarnish-writing", metal, PartChoice.LATER_TARNISH, 6, writing());
    }
    /** Hatching as a silverpoint drawing is shaded, one zigzag stroke: close slanting lines down and up across the panel. */
    static double[][] hatching() {
        int lines = 30, per = 14;
        double[][] o = new double[lines * per][];
        double t = 1000;
        for (int k = 0; k < lines; k++) for (int i = 0; i < per; i++) {
            double u = i / (per - 1.0), v = k % 2 == 0 ? u : 1 - u, len = 110 + 40 * Math.sin(k * .4);
            o[k * per + i] = new double[]{90 + k * 8 + 45 * v, 150 - len / 2 + len * v, .35 + .35 * Math.sin(Math.PI * u), t};
            t += 9;
        }
        return o;
    }
    static ModularBrush onPaper(ModularBrush b, BrushTexture texture, double tooth) { return LaterMediaChecks.onPaper(b, texture, tooth); }
    /** {@code name}.png as laid and finished; {@code name}-dial.png its macro dial at 0.1, as it comes, 0.9; {@code name}-close.png the finished mark at 2×. */
    static void set(File out, String name, ModularBrush b, PartChoice later, double size, double[][] path) throws Exception {
        ModularBrush with = with(b, later, -1);
        int[][] laid = draw(with, size, path, false), done = draw(with, size, path, true);
        WetSmudgePictures.save(new File(out, name + ".png"), laid, done);
        WetSmudgePictures.save(new File(out, name + "-dial.png"), draw(with(b, later, .1), size, path, true), done, draw(with(b, later, .9), size, path, true));
        WetSmudgePictures.save(new File(out, name + "-close.png"), close(done));
    }
    /** {@code b} with Later {@code later}, its macro dial {@code v} of the way across its range (below 0: as it comes). */
    static ModularBrush with(ModularBrush b, PartChoice later, double v) {
        ModularBrush c = b.copy();
        ModularBrush.Part p = new ModularBrush.Part(later);
        if (v >= 0) p.set(later.macro, later.macro.min + v * (later.macro.max - later.macro.min));
        c.set(BrushPart.LATER, p);
        return c.normalize();
    }
    static ModularBrush slow(String preset) {
        ModularBrush b = FloatChecks.preset(preset).copy();
        b.set(BrushPart.LIFE, ModularBrush.switchTo(b.part(BrushPart.LIFE), PartChoice.DRYING));
        return b.normalize();
    }
    /** {@code path} drawn with {@code b}; {@code finished}: the clock run on past its Later's time. */
    static int[][] draw(ModularBrush b, double size, double[][] path, boolean finished) {
        long t0 = 50_000_000;
        FadingInk.clock = () -> t0;
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        // A wash in a mid grey, as one is mostly laid; a pen in black.
        CarrySmudgeChecks.feed(ModularStroke.start(d, b, size, size > 30 ? .55 : 1, s), path);
        s.settleNow(d);
        if (finished) d.fade(t0 + FadingInk.MOST_MS * 2L, null);
        FadingInk.clock = System::currentTimeMillis;
        return WetSmudgePictures.grab(d);
    }
    /** The middle of {@code g}, at 2×. */
    static int[][] close(int[][] g) {
        int w = W / 2, h = H / 2, x0 = W / 4, y0 = H / 4;
        int[][] o = new int[2 * h][2 * w];
        for (int y = 0; y < 2 * h; y++) for (int x = 0; x < 2 * w; x++) o[y][x] = g[y0 + y / 2][x0 + x / 2];
        return o;
    }
    static double[][] wash() { return LaterMediaChecks.wash(); }
    /** Handwriting-like loops across the panel, as a pen writes. */
    static double[][] writing() {
        int n = 260;
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double u = i / (double) n, a = u * Math.PI * 2 * 6;
            o[i] = new double[]{60 + 300 * u + 18 * Math.cos(a), 150 - 40 * Math.sin(a) * (.8 + .2 * Math.sin(u * 9)) + 25 * Math.sin(u * 3), .55 + .3 * Math.sin(a * .5), 1000 + 6 * i};
        }
        return o;
    }
}
