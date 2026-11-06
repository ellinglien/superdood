package io.github.mpdairy.monopaint;

import java.util.List;

/**
 * Previews as wide as the mark (the owner, 2026-11-02: the twig's "preview mark is much bigger than the mark that is
 * left... can the preview be closer to the actual size?"): for every Tip, the quick stroke's preview ({@link
 * FootprintSweep}, as {@link QuickStroke} sweeps it) against the stroke laid from the same samples, and the hover ring
 * ({@link HoverNib}) against the stroke a light touch lays across it at the natural hold. Widths are a band's extent
 * across the stroke, the rows holding at least a fifth of its fullest row's ink over a stretch of it.
 */
public final class PreviewWidthChecks {
    static final int W = 480, H = 320;

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        StringBuilder out = new StringBuilder();
        for (PartChoice tip : BrushPart.TIP.choices()) out.append(tip(tip));
        System.out.println("PASS: every Tip's quick preview and hover ring are within 0.7 to 1.35 times the mark it lays (2 px either way), bar those whose preview is their own (placed lines' quick preview, line by line; Stipple's scatter; bristles' ring where they land; Spiro's drives). Preview/ring against laid, px:" + out);
    }

    /** Placed lines (Split Nib, Rake) preview line by line ({@code QuickStrokeChecks.placedPreview}) and ring as far as they spread; Stipple's band is where its dots may land, drawn live at every usual size; Spiro is never quick, its loops sized by its drives. */
    static boolean ownQuick(PartChoice tip) { return tip == PartChoice.SPLIT_NIB || tip == PartChoice.MULTI_LINE || tip == PartChoice.STIPPLE || tip == PartChoice.SPIRO; }
    /** Stipple and Spiro, as above, and bristles, which ring where the swarm lands, before it opens ({@link BristleSwarm#landing}). */
    static boolean ownRing(PartChoice tip) { return tip == PartChoice.STIPPLE || tip == PartChoice.SPIRO || tip == PartChoice.BRISTLES; }

    static String tip(PartChoice tip) {
        ModularBrush b = StippleChecks.mk(tip, PartChoice.STEADY);
        double size = ModularSizes.nib(tip) > 0 ? ModularSizes.nib(tip) : 24;
        ResolvedBrush r = ResolvedBrush.of(b, size, 1);
        StringBuilder s = new StringBuilder(" " + tip.label + " ");
        // The quick preview: a writing hand's hold, across and down the page, a light and a usual touch.
        if (!ownQuick(tip)) for (double p : new double[]{HoverNib.LIGHT, HoverNib.TYPICAL}) for (int dir = 0; dir < 2; dir++) {
            double ux = dir == 0 ? 1 : 0, uy = dir == 0 ? 0 : 1;
            List<double[]> samples = NibChecks.line(240 - 190 * ux, 160 - 150 * uy, 240 + 190 * ux, 160 + 150 * uy, 120, p, 40, 45);
            int laid = across(page(b, size, samples), ux, uy), quick = across(preview(r, samples), ux, uy);
            if (laid == 0) continue;
            s.append(quick).append('/').append(laid).append(' ');
            check(near(quick, laid), tip.label + " at " + p + (dir == 0 ? ", across" : ", down") + " the page: the quick preview " + quick + " px wide, the mark " + laid);
        }
        // The hover ring: its long side against a light touch's stroke laid square to it, the pen held as hovering reads it.
        if (!ownRing(tip)) {
            HoverNib ring = HoverNib.of(r);
            double ux = -Math.sin(ring.angle), uy = Math.cos(ring.angle);
            List<double[]> samples = NibChecks.line(240 - 140 * ux, 160 - 140 * uy, 240 + 140 * ux, 160 + 140 * uy, 100, HoverNib.LIGHT, ResolvedBrush.NATURAL, 0);
            int laid = across(page(b, size, samples), ux, uy);
            if (laid > 0) {
                s.append("ring ").append(Math.round(ring.width)).append('/').append(laid).append(';');
                check(near(ring.width, laid), tip.label + ": the hover ring " + Math.round(ring.width) + " px across, a light touch lays " + laid);
            }
        }
        return s.toString();
    }
    static boolean near(double preview, double laid) { return preview <= 1.35 * laid + 2 && preview >= .7 * laid - 2; }

    static double[][] page(ModularBrush b, double size, List<double[]> s) {
        ToneDocument d = NibChecks.page(b, size, s, 4);
        double[][] ink = new double[H][W];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) ink[y][x] = 255 - d.compositeTone(x, y);
        return ink;
    }
    /** The quick preview's footprint over the samples, 1 where it writes. */
    static double[][] preview(ResolvedBrush r, List<double[]> s) {
        double[][] g = new double[H][W];
        StrokePoints pts = new StrokePoints();
        FootprintSweep sweep = new FootprintSweep(r, pts, W, H, r.line || r.body == null, new StrokePaper(BrushTexture.PLAIN), false) {
            @Override void write(int y) {
                for (int x = Math.max(0, lo[y]); x <= Math.min(W - 1, hi[y]); x++) g[y][x] = 1;
                for (int x = Math.max(0, lo2[y]); x <= Math.min(W - 1, hi2[y]); x++) g[y][x] = 1;
                lo[y] = lo2[y] = Integer.MAX_VALUE; hi[y] = hi2[y] = -1;
            }
        };
        for (double[] q : s) { pts.add((float) q[0], (float) q[1], (float) q[2], (long) q[3], (float) q[4], (float) q[5]); sweep.to(pts.count - 1); }
        sweep.flush();
        return g;
    }
    /**
     * How wide a stroke through the page's middle along {@code ux, uy} is: its ink summed along the middle 80 px at each
     * distance across, then the extent of the distances holding at least a fifth of the fullest's.
     */
    static int across(double[][] ink, double ux, double uy) {
        int R = 140;
        double[] prof = new double[2 * R + 1];
        double most = 0;
        for (int q = -R; q <= R; q++) {
            for (int t = -40; t < 40; t++) {
                int x = (int) Math.floor(240 + t * ux - q * uy), y = (int) Math.floor(160 + t * uy + q * ux);
                if (x >= 0 && y >= 0 && x < W && y < H) prof[q + R] += ink[y][x];
            }
            most = Math.max(most, prof[q + R]);
        }
        int lo = -1, hi = -2;
        for (int q = 0; q < prof.length; q++) if (most > 0 && prof[q] >= .2 * most) { if (lo < 0) lo = q; hi = q; }
        return hi - lo + 1;
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
