package io.github.mpdairy.monopaint;

import java.util.List;
import java.util.Random;

/**
 * Not a check: sharp tiles timed on the desktop ({@link SharpView#render}), a brush family at a time. A Manta page
 * (1920 × 2560) on cold press: 300 strokes of 170 samples over the whole page (handwriting), or the same 300 inside one
 * screen at 400% (shading). One screen tile (64 page px, 256 × 256 points at 400%) on average over the 80 tiles of a
 * zoomed screen (the Manta's 1920 × 2560 at 400%: 480 × 640 page px), best of {@code [runs]}; {@code [tools...]} by
 * name (default: one of each family).
 */
public final class SharpBench {
    static final String[] FAMILIES = {"HB pencil", "2B pencil", "Messy stick", "Soft charcoal", "Soft pastel", "Wax crayon", "Steady line", "Gel pen", "Fountain pen",
            "Ballpoint", "Swell brush", "Split nib", "Copperplate", "Ruling pen", "Twig pen", "Stipple pen", "Hatch pen", "Loose hatch", "Spirograph", "Fan brush"};
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        String[] names = args.length > 1 ? java.util.Arrays.copyOfRange(args, 1, args.length) : FAMILIES;
        for (String name : names) {
            Brush brush = VectorInkChecks.preset(name);
            double px = SharpTools.size(brush);
            if (!SharpStrokes.takes(brush.parts, px, 1)) { System.out.println(name + ": pixels"); continue; }
            double[] ms = new double[2];
            for (int dense = 0; dense < 2; dense++) {
                ToneDocument d = page(brush, px, dense == 1);
                ms[dense] = screen(d, runs);
            }
            System.out.printf("%-14s %4.0f px: a tile %.1f ms handwriting, %.1f ms shading (Manta about %.0f / %.0f ms)%n", name, px, ms[0], ms[1], 20 * ms[0], 20 * ms[1]);
        }
    }
    static final int SX = 640, SY = 960, SW = 480, SH = 640;
    static ToneDocument page(Brush brush, double px, boolean dense) {
        ToneDocument d = InkWashLayers.blank(1920, 2560);
        d.surface = BrushTexture.COLD_PRESS;
        WatercolorSession s = new WatercolorSession();
        Random r = new Random(4);
        for (int k = 0; k < 300; k++) {
            float[][] h = new float[6][170];
            double x = dense ? SX + r.nextDouble() * SW : 100 + r.nextDouble() * 1720, y = dense ? SY + r.nextDouble() * SH : 100 + r.nextDouble() * 2360, a = r.nextDouble() * 6.3;
            long t = 1000;
            for (int i = 0; i < 170; i++) {
                a += (r.nextDouble() - .5) * .3; x += 1.5 * Math.cos(a); y += 1.5 * Math.sin(a); t += 8;
                h[0][i] = (float) x; h[1][i] = (float) y; h[2][i] = .6f; h[3][i] = Float.NaN; h[4][i] = Float.NaN; h[5][i] = t;
            }
            SharpChecks.draw(d, s, brush, px, 1, h, 1);
        }
        return d;
    }
    /** A tile's mean ms over a zoomed screen's 80 tiles at 400%, best of {@code runs}. */
    static double screen(ToneDocument d, int runs) {
        SharpView view = new SharpView(rect -> { });
        List<SharpView.Kept> kept = view.kept(d);
        double best = Double.MAX_VALUE;
        for (int run = 0; run < runs; run++) {
            long t = 0; int n = 0;
            for (int y = SY; y < SY + SH; y += SharpView.TILE) for (int x = SX; x < SX + SW; x += SharpView.TILE) {
                long t0 = System.nanoTime();
                List<SharpView.Kept> here = new java.util.ArrayList<>();
                for (SharpView.Kept e : kept) if (e.box[0] < x + SharpView.TILE && e.box[2] > x && e.box[1] < y + SharpView.TILE && e.box[3] > y) here.add(e);
                SharpView.render(SharpView.Area.of(d, 4, x, y, SharpView.TILE, SharpView.TILE, here, null, 0, 0, null));
                t += System.nanoTime() - t0; n++;
            }
            best = Math.min(best, t / 1e6 / n);
        }
        return best;
    }
}
