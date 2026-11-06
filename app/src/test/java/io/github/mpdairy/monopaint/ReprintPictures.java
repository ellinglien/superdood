package io.github.mpdairy.monopaint;

import java.io.File;

/**
 * Reprint (Smudge's ninth choice, branch explore-reprint), drawn by the app's engine: a journal page with handwriting, a
 * pencil sketch and a grey wash, with bands brushed over it by the Photocopier, Newsprint, a Riso and a Soft dither
 * reprint, and the Photocopier at a light touch. scripts/reprint_pictures.sh.
 */
public class ReprintPictures {
    static final int W = 1100, H = 640;

    /** The page: a dried grey wash on the wash layer, then a pencil sketch and handwriting on the ink layer. */
    static ToneDocument page(WatercolorSession s) {
        ToneDocument d = InkWashLayers.blank(W, H);
        // A grey wash, two broad overlapping strokes, settled dry.
        ModularBrush wash = FloatChecks.preset("Soft wash");
        KnifePictures.stroke(d, s, HeldPaint.ANY, wash, 200, .45, KnifePictures.path(60, 500, 1040, 490, .7, 0, 0, 0, 0));
        KnifePictures.stroke(d, s, HeldPaint.ANY, wash, 140, .3, KnifePictures.path(100, 585, 1000, 595, .6, 0, 0, 0, 0));
        WetPaintChecks.settle(s, d); s.settleNow(d); s.dry("dried");
        // A pencil sketch: a hill line, a sun and some shading strokes.
        ModularBrush pencil = FloatChecks.preset("HB pencil");
        KnifePictures.stroke(d, s, "PENCIL", pencil, 7, 1, curve(t -> 40 + 1020 * t, t -> 340 - 50 * Math.sin(t * 5.5) - 20 * Math.sin(t * 13), .6));
        KnifePictures.stroke(d, s, "PENCIL", pencil, 7, 1, curve(t -> 880 + 60 * Math.cos(t * 2 * Math.PI), t -> 300 + 60 * Math.sin(t * 2 * Math.PI), .55));
        for (int k = 0; k < 26; k++) {
            double x = 60 + k * 38;
            KnifePictures.stroke(d, s, "PENCIL", pencil, 7, 1, KnifePictures.path(x, 420, x + 34, 370, .35 + .02 * (k % 5), 40, 0, 40, 0));
        }
        // Handwriting: four lines of looped cursive with the Fineliner.
        ModularBrush pen = FloatChecks.preset("Steady line");
        for (int line = 0; line < 4; line++) {
            double base = 70 + line * 52;
            for (int w = 0; w < 6; w++) {
                final int word = w;
                double x0 = 50 + word * 120 + (line % 2) * 30, len = 70 + 30 * ((word * 7 + line * 3) % 4) / 3.0;
                int loops = 3 + (word + line) % 4;
                KnifePictures.stroke(d, s, "PEN", pen, 4, 1, curve(t -> x0 + len * t + 9 * Math.sin(t * loops * 2 * Math.PI),
                        t -> base - 14 * Math.abs(Math.sin(t * loops * Math.PI)) * (1 + .8 * Math.sin(t * 17 + word)) + 8 * Math.cos(t * loops * 2 * Math.PI), .7));
            }
        }
        return d;
    }
    interface F { double at(double t); }
    /** A sample path along {@code x(t), y(t)}, t 0 to 1, about 2 px apart, at pressure {@code p}, the pen upright. */
    static double[][] curve(F x, F y, double p) {
        int n = 600;
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) { double t = (double) i / n; o[i] = new double[]{x.at(t), y.at(t), p, i * 3, 0, 0}; }
        return o;
    }
    /** A band down the page at {@code x}, {@code width} wide: the brush back and forth across it, top to bottom, at pressure {@code p}. */
    static double[][] band(double x, double width, double p, double size) {
        java.util.List<double[]> o = new java.util.ArrayList<>();
        int t = 0;
        double step = size * .45, left = x + size / 2, right = x + width - size / 2;
        for (double y = 10 + size / 2; y < H - 10; y += step) {
            boolean back = o.size() / 2 % 2 == 1;
            for (int i = 0; i <= 30; i++) {
                double u = i / 30.0;
                o.add(new double[]{back ? right + (left - right) * u : left + (right - left) * u, y + step * u, p, t += 3, 0, 0});
            }
        }
        return o.toArray(new double[0][]);
    }

    /** {@code name}'s preset, or the Photocopier through Print Shading {@code screen} on {@code paper}. */
    static ModularBrush reprint(int screen, PartChoice paper, double tooth) {
        ModularBrush b = FloatChecks.preset("Photocopier").copy();
        b.part(BrushPart.MEDIUM).set(PartDial.SCREEN, screen);
        ModularBrush.Part p = new ModularBrush.Part(paper);
        p.set(PartDial.TOOTH, tooth);
        b.set(BrushPart.PAPER, p);
        return b.normalize();
    }
    static final double SIZE = 120;
    /** The page with its five bands. */
    static ToneDocument reprinted() {
        WatercolorSession s = new WatercolorSession();
        ToneDocument d = page(s);
        ModularBrush[] brushes = {FloatChecks.preset("Photocopier"), FloatChecks.preset("Newsprint"), reprint(ScreenChecks.RISO, PartChoice.PLAIN, 0), reprint(ScreenChecks.SOFT_DITHER, PartChoice.PLAIN, 0), FloatChecks.preset("Photocopier")};
        double[] press = {.75, .75, .75, .75, .18};
        for (int k = 0; k < brushes.length; k++) KnifePictures.stroke(d, s, "REPRINT" + k, brushes[k], SIZE, 1, band(30 + k * 215, 170, press[k], SIZE));
        return d;
    }
    static final String[] HEADS = {"Photocopier", "Newsprint", "Riso", "Soft dither", "Photocopier, light"};

    public static void main(String[] a) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(a.length > 0 ? a[0] : "/tmp/reprint-pictures");
        out.mkdirs();
        ToneDocument before = page(new WatercolorSession()), after = reprinted();
        int[][][][] page = {{KnifePictures.grab(before)}, {KnifePictures.grab(after)}};
        KnifePictures.sheet(new File(out, "reprint-page.png"), "Reprint: a journal page, then five bands brushed over it (" + String.join(", ", HEADS) + ", left to right)",
                new String[]{""}, new String[]{"Before", "After"}, page, W, H);
        // Close-ups of each band, before and after, where it crosses the handwriting, the sketch and the wash, twice the size.
        int[][] g = KnifePictures.grab(after), b = KnifePictures.grab(before);
        int[][][][] close = new int[HEADS.length][6][][];
        int[] tops = {40, 300, 470};
        for (int k = 0; k < HEADS.length; k++) for (int r = 0; r < 3; r++) {
            close[k][2 * r] = KnifePictures.zoom(b, 30 + k * 215 + 40, tops[r], 90, 110);
            close[k][2 * r + 1] = KnifePictures.zoom(g, 30 + k * 215 + 40, tops[r], 90, 110);
        }
        String[] rows = HEADS.clone();
        rows[4] = "Photocopier,\nlight touch";
        KnifePictures.sheet(new File(out, "reprint-close.png"), "Reprint close up, twice the size: before and after each",
                new String[]{"Handwriting", "after", "Pencil", "after", "Wash", "after"}, rows, close, 180, 220);
        System.out.println("wrote " + out);
    }
}
