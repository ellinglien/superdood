package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Random;

/**
 * Not a check: the HB pencil zoomed. {@code <dir>}: {@code hb-400.png}, a hand's strokes at 400% as the zoomed view shows
 * them today (the page's pixels scaled up, then the screen's dots) and sharp ({@link SharpView}'s tiles); and
 * {@code hb-800-wobble.png}, a slow straight stroke at 800% in plain greys, drawn from the pen's exact samples (left) and
 * from the half pixels the log keeps (right), with how far apart their edges lie.
 */
public final class SharpPictures {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File dir = new File(args[0]);
        dir.mkdirs();
        four(new File(dir, "hb-400.png"), "HB pencil", 0);
        for (String[] t : new String[][]{{"Messy stick", "charcoal-400.png", "6"}, {"Split nib", "split-nib-400.png", "6"}, {"Stipple pen", "stipple-400.png", "16"}, {"Hatch pen", "hatch-400.png", "20"}})
            four(new File(dir, t[1]), t[0], Double.parseDouble(t[2]));
        wobble(new File(dir, "hb-800-wobble.png"));
    }

    /** {@code name}'s strokes at 400%, today and sharp; {@code px}: its size (0: 3 to 7 px, stroke by stroke). */
    static void four(File out, String name, double px) throws Exception {
        int W = 200, H = 140, X = 30, Y = 30, CW = 128, CH = 64, Z = 4;
        ToneDocument d = InkWashLayers.blank(W, H);
        d.surface = BrushTexture.COLD_PRESS;
        WatercolorSession s = new WatercolorSession();
        Brush hb = VectorInkChecks.preset(name);
        Random r = new Random(9);
        for (int k = 0; k < 5; k++) SharpChecks.draw(d, s, hb, px > 0 ? px : 3 + k, 1, SharpChecks.hand(r, 60, 40 + 20 * k, 50 + 8 * k, 10, false), 1);
        int[] tones = new int[W * H];
        d.compositeTones(0, 0, W, H, tones, 0, W, 0);
        SharpView view = new SharpView(rect -> { });
        List<SharpView.Kept> kept = view.kept(d);
        int cw = CW * Z, ch = CH * Z, gap = 16;
        int[] sharp = new int[cw * ch];
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) sharp[y * cw + x] = tones[(Y + y / Z) * W + X + x / Z] & 255;
        for (int ty = 0; ty < H; ty += SharpView.TILE) for (int tx = 0; tx < W; tx += SharpView.TILE) {
            int w = Math.min(SharpView.TILE, W - tx), h = Math.min(SharpView.TILE, H - ty);
            short[] t = SharpView.render(SharpView.Area.of(d, Z, tx, ty, w, h, kept, null, 0, 0, null));
            if (t == SharpView.NONE) continue;
            for (int fy = 0; fy < h * Z; fy++) for (int fx = 0; fx < w * Z; fx++) {
                int v = t[fy * SharpView.TILE * Z + fx], ox = (tx - X) * Z + fx, oy = (ty - Y) * Z + fy;
                if (v >= 0 && ox >= 0 && oy >= 0 && ox < cw && oy < ch) sharp[oy * cw + ox] = v;
            }
        }
        BufferedImage img = new BufferedImage(2 * cw + gap, ch, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < ch; y++) for (int x = 0; x < img.getWidth(); x++) img.setRGB(x, y, 0x808080);
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) {
            img.setRGB(x, y, DotPattern.pixel(tones[(Y + y / Z) * W + X + x / Z] & 255, x, y) & 0xffffff);
            img.setRGB(cw + gap + x, y, DotPattern.pixel(sharp[y * cw + x], x, y) & 0xffffff);
        }
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println("Wrote " + out + ": " + name + " at 400%, today (left) and sharp (right), the screen's dots");
    }

    static void wobble(File out) throws Exception {
        int W = 120, H = 60, Z = 8;
        ToneDocument d = InkWashLayers.blank(W, H);
        // Smooth paper: the line's edges are its own, not the grain's.
        d.surface = BrushTexture.PLAIN;
        WatercolorSession s = new WatercolorSession();
        Brush hb = VectorInkChecks.preset("HB pencil");
        // Slow and straight: a sample every 0.8 px, a bit of hand jitter, along a shallow slope (where half pixels step most).
        int n = 90;
        float[][] h = new float[6][n];
        Random r = new Random(2);
        for (int i = 0; i < n; i++) {
            h[0][i] = (float) (20 + .8 * i + r.nextGaussian() * .05); h[1][i] = (float) (25 + .8 * i * .13 + r.nextGaussian() * .05);
            h[2][i] = .55f; h[3][i] = Float.NaN; h[4][i] = Float.NaN; h[5][i] = 1000 + 16 * i;
        }
        SharpChecks.draw(d, s, hb, 4, 1, h, 1);
        StrokeLog.Entry e = d.replay().get(0);
        SharpStrokes.Laid laid = SharpStrokes.Laid.of(e, d.surface);
        FineGrid grid = FineGrid.over(Z, 0, 0, W, H, W, H);
        StrokePaper paper = new StrokePaper(d.surface);
        SharpStrokes.GridPage exact = new SharpStrokes.GridPage(grid.width, grid.height), keptPage = new SharpStrokes.GridPage(grid.width, grid.height);
        ModularStroke pen = ModularStroke.onGrid(grid, exact, paper, laid.brush, e.px, e.darkness);
        exact.begin();
        for (int i = 0; i < n; i++) pen.sample(h[0][i], h[1][i], h[2][i], h[3][i], h[4][i], (long) h[5][i]);
        pen.finish();
        SharpStrokes.draw(e, laid, paper, grid, keptPage, Double.NaN);
        int x0 = 24 * Z, x1 = 88 * Z, y0 = 18 * Z, y1 = 42 * Z, cw = x1 - x0, ch = y1 - y0, gap = 16;
        BufferedImage img = new BufferedImage(2 * cw + gap, ch, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < ch; y++) for (int x = 0; x < img.getWidth(); x++) img.setRGB(x, y, 0x808080);
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) {
            int a = exact.tones[(y0 + y) * grid.width + x0 + x], b = keptPage.tones[(y0 + y) * grid.width + x0 + x];
            img.setRGB(x, y, a * 0x010101); img.setRGB(cw + gap + x, y, b * 0x010101);
        }
        javax.imageio.ImageIO.write(img, "png", out);
        double[] apart = apart(exact.tones, keptPage.tones, grid.width, x0, x1, y0, y1);
        System.out.printf("Wrote %s: HB pencil at 800%%, plain greys: the pen's exact samples (left), the log's half pixels (right); their top edges %.2f screen px apart (95th percentile), %.2f at most%n", out, apart[0], apart[1]);
    }
    /**
     * How far apart two drawings' top edges lie (each column's first crossing of a light grey from above, between points),
     * points (screen px at the grid's zoom): the 95th percentile and the most, over the columns where both have one.
     */
    static double[] apart(int[] a, int[] b, int w, int x0, int x1, int y0, int y1) {
        java.util.List<Double> d = new java.util.ArrayList<>();
        for (int x = x0; x < x1; x++) {
            double ea = edge(a, w, x, y0, y1), eb = edge(b, w, x, y0, y1);
            if (ea == ea && eb == eb) d.add(Math.abs(ea - eb));
        }
        java.util.Collections.sort(d);
        return new double[]{d.get((int) (.95 * (d.size() - 1))), d.get(d.size() - 1)};
    }
    private static double edge(int[] t, int w, int x, int y0, int y1) {
        for (int y = y0 + 1; y < y1; y++) { int a = t[(y - 1) * w + x], b = t[y * w + x]; if (a > 230 && b <= 230) return y - 1 + (a - 230.0) / (a - b); }
        return Double.NaN;
    }
}
