package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Print Shading's stochastic three (riso, photocopy, soft dither) drawn by the app's engine beside the nearest older
 * kind (Grain, Halftone, Dither), scripts/screen_pictures.sh: {@code {kind}.png} a grid, a row each for a pen line, a
 * pressure ramp (Pressure dark on), greys (.15 to .95) and a wash, the new kind left, the older right; {@code
 * {kind}-close.png} the greys and the wash at 3×; {@code {kind}-sheet.png} a sheet of strokes, light grey to black. Then how
 * long the tiles take to make, and each kind's time a pixel, on this machine.
 */
public class ScreenPictures {
    static final int W = 480, H = 170;
    public static void main(String[] a) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(a.length > 0 ? a[0] : "/tmp/screen-pictures");
        out.mkdirs();
        long t0 = System.nanoTime();
        PrintScreen.Stochastic.blue();
        long t1 = System.nanoTime();
        PrintScreen.Stochastic.grain();
        long t2 = System.nanoTime();
        System.out.println(String.format(java.util.Locale.ROOT, "tiles made in: soft dither's %.0f ms, a grain's %.0f ms", (t1 - t0) / 1e6, (t2 - t1) / 1e6));
        String[] names = {"riso", "photocopy", "soft-dither"};
        int[][] pairs = {{15, 4}, {16, 1}, {17, 3}};
        for (int k = 0; k < names.length; k++) {
            int[][][] mine = rows(pairs[k][0]), theirs = rows(pairs[k][1]);
            grid(new File(out, names[k] + ".png"), mine, theirs);
            grid(new File(out, names[k] + "-close.png"), new int[][][]{close(mine[2]), close(mine[3])}, new int[][][]{close(theirs[2]), close(theirs[3])});
            grid(new File(out, names[k] + "-sheet.png"), new int[][][]{sheet(pairs[k][0])}, new int[][][]{sheet(pairs[k][1])});
        }
        for (int s = 1; s < PartDial.SCREEN.choices.length; s++) System.out.println(String.format(java.util.Locale.ROOT, "%-12s %5.1f ns a pixel", PartDial.SCREEN.choices[s], ns(s)));
        System.out.println("wrote " + out);
    }
    static ModularBrush screened(ModularBrush b, int s) { return ScreenChecks.screened(b, s); }
    /** Print {@code s}'s four rows: a pen line, a pressure ramp, greys, a wash. */
    static int[][][] rows(int s) {
        ModularBrush pen = screened(ScreenChecks.ink(), s), pressing = pen.copy(), wash = screened(ScreenChecks.wash(), s);
        pressing.part(BrushPart.HAND).set(PartDial.PD, 1); pressing.normalize();
        ToneDocument line = InkWashLayers.blank(W, H), ramp = InkWashLayers.blank(W, H), greys = InkWashLayers.blank(W, H), washed = InkWashLayers.blank(W, H);
        ScreenChecks.live(line, pen, 7, 1, writing());
        double[][] p = CarrySmudgeChecks.across(30, 450, 85);
        for (int i = 0; i < p.length; i++) p[i][2] = .05 + .95 * i / (p.length - 1);
        ScreenChecks.live(ramp, pressing, 60, 1, p);
        for (int g = 0; g < 5; g++) ScreenChecks.live(greys, pen, 30, .15 + .2 * g, CarrySmudgeChecks.across(30, 450, 25 + 30 * g));
        double[][] wv = new double[121][];
        for (int i = 0; i <= 120; i++) { double t = i / 120.0; wv[i] = new double[]{50 + 380 * t, 85 + 30 * Math.sin(2 * Math.PI * t), .3 + .6 * Math.sin(Math.PI * t), i * 5}; }
        ScreenChecks.live(washed, wash, 110, .55, wv);
        return new int[][][]{WetSmudgePictures.grab(line), WetSmudgePictures.grab(ramp), WetSmudgePictures.grab(greys), WetSmudgePictures.grab(washed)};
    }
    /** A sheet filled with Print {@code s}, a light grey at the top to black at the foot, in overlapping wide strokes: the streaks, mottle and drift over a page. */
    static int[][] sheet(int s) {
        ModularBrush pen = screened(ScreenChecks.ink(), s);
        ToneDocument d = InkWashLayers.blank(W, 4 * H);
        for (int y = 30, i = 0; y < 4 * H - 20; y += 36, i++) ScreenChecks.live(d, pen, 44, Math.min(1, .25 + .055 * i * 1.4), CarrySmudgeChecks.across(10, W - 10, y));
        return WetSmudgePictures.grab(d);
    }
    /** The middle ninth of {@code g} at 3×. */
    static int[][] close(int[][] g) {
        int[][] o = new int[H][W];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) o[y][x] = g[H / 3 + y / 3][W / 3 + x / 3];
        return o;
    }
    /** Handwriting-like loops across the panel. */
    static double[][] writing() {
        int n = 260;
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double u = i / (double) n, t = u * Math.PI * 2 * 7;
            o[i] = new double[]{40 + 400 * u + 16 * Math.cos(t), 85 - 34 * Math.sin(t) * (.8 + .2 * Math.sin(u * 9)) + 14 * Math.sin(u * 3), .55 + .3 * Math.sin(t * .5), 1000 + 6 * i};
        }
        return o;
    }
    /** Rows of panels, {@code left[i]} beside {@code right[i]}, a grey gap between. */
    static void grid(File f, int[][][] left, int[][][] right) throws Exception {
        int h = left[0].length, w = left[0][0].length, gap = 10;
        BufferedImage img = new BufferedImage(2 * w + gap, left.length * (h + gap) - gap, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) img.getRaster().setSample(x, y, 0, 100);
        for (int r = 0; r < left.length; r++) for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            img.getRaster().setSample(x, r * (h + gap) + y, 0, left[r][y][x]);
            img.getRaster().setSample(w + gap + x, r * (h + gap) + y, 0, right[r][y][x]);
        }
        javax.imageio.ImageIO.write(img, "png", f);
    }
    /** Print {@code s}'s {@link PrintScreen#ink} a pixel, warm, over a fresh 512 px square at half grey (thresholds worked out once, then read). */
    static double ns(int s) {
        double best = 1e9;
        for (int rep = 0; rep < 5; rep++) {
            PrintScreen p = PrintScreen.of(screened(ScreenChecks.ink(), s).part(BrushPart.MEDIUM));
            int n = 0;
            long t0 = System.nanoTime();
            for (int pass = 0; pass < 4; pass++) for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++) if (p.ink(.5, x + 1000 * rep, y, 4096)) n++;
            best = Math.min(best, (System.nanoTime() - t0) / (4.0 * 512 * 512));
            if (n < 0) System.out.println();
        }
        return best;
    }
}
