package io.github.mpdairy.monopaint;

import java.util.List;
import java.util.Random;

/**
 * Not a check: each Basic tool the spec's rule takes ({@link SharpStrokes#qualifies}), a page of its strokes (tilted
 * and upright, slow and quick, a tap), whether it is kept sharp ({@link SharpStrokes#takes}), how many of its pixels
 * don't match drawn again at 1 to a pixel, and its tiles' time at 400% (desktop, best of {@code [runs]}).
 * {@code SharpChecks} holds every kept tool to no mismatch ({@link #page}, {@link #mismatched}).
 */
public final class SharpTools {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        for (ToolPresets.Preset p : ToolPresets.all()) {
            Brush b = p.brush();
            if (!SharpStrokes.qualifies(b.parts)) continue;
            double px = size(b);
            boolean kept = SharpStrokes.takes(b.parts, px, 1);
            String what = p.style.name + " | " + p.name + " | " + (kept ? "sharp" : "pixels");
            if (!kept) { System.out.println(what); continue; }
            ToneDocument d = page(b, px, 11);
            int[] m = mismatched(d, 4);
            double ms = time(d, 4, runs);
            System.out.printf("%s | %d of %d differ | %.1f ms a tile at 400%%%n", what, m[0], m[1], ms);
        }
    }
    /** A size each tool reads at: its marks a few px across, a pattern's or a bristle brush's wider. */
    static double size(Brush b) {
        ResolvedBrush r = ResolvedBrush.of(b.parts, 8, 1);
        return r.pattern != null || r.lineSwarm != null ? 20 : 6;
    }
    /** A page of {@code b}'s strokes at {@code px}: eight strokes tilted and upright, slow and quick, and a tap. */
    static ToneDocument page(Brush b, double px, long seed) {
        ToneDocument d = InkWashLayers.blank(256, 192);
        d.surface = BrushTexture.COLD_PRESS;
        WatercolorSession s = new WatercolorSession();
        Random r = new Random(seed);
        for (int k = 0; k < 8; k++) SharpChecks.draw(d, s, b, px, 1, SharpChecks.hand(r, 30 + r.nextInt(40), 40 + r.nextInt(170), 30 + r.nextInt(130), k % 3 == 0 ? 3 : 12, k % 2 == 0), 1);
        SharpChecks.draw(d, s, b, px, 1, SharpChecks.hand(r, 1, 128, 96, 10, false), 1);
        return d;
    }
    /** {mismatched, laid} page pixels over the page's tiles at grid {@code k}. */
    static int[] mismatched(ToneDocument d, int k) { int[] m = SharpChecks.mismatched(d, k, null); return new int[]{m[0], m[2]}; }
    /** The mean ms a tile takes at grid {@code k} over the page's tiles with kept strokes, best of {@code runs}. */
    static double time(ToneDocument d, int k, int runs) {
        SharpView view = new SharpView(rect -> { });
        List<SharpView.Kept> kept = view.kept(d);
        double best = Double.MAX_VALUE;
        for (int run = 0; run < runs; run++) {
            long t = 0; int n = 0;
            for (int y = 0; y < d.height; y += SharpView.TILE) for (int x = 0; x < d.width; x += SharpView.TILE) {
                long t0 = System.nanoTime();
                SharpView.Area a = SharpView.Area.of(d, k, x, y, SharpView.TILE, SharpView.TILE, kept, null, 0, 0, null);
                short[] tile = SharpView.render(a);
                t += System.nanoTime() - t0;
                if (tile != SharpView.NONE) n++;
            }
            if (n > 0) best = Math.min(best, t / 1e6 / n);
        }
        return best;
    }
}
