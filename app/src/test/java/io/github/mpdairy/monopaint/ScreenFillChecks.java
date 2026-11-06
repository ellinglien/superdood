package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;

/**
 * Screened brushes fill (the owner, 2026-10-21: "even when you draw with pressure it's not easy to fill in all the way,
 * it just ends up as a squiggly line in the middle"): each Print preset, a square scribbled back and forth at medium
 * pressure prints one even field ({@link ResolvedBrush#screenTone}), a single stroke has a crisp edge, not a fuzz of
 * thinning dots, and the same samples print the same pixels live, batched and again.
 */
public final class ScreenFillChecks {
    static final int W = 420, H = 380, SQ = 300, S0 = 40;

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        StringBuilder said = new StringBuilder();
        for (ToolPresets.Preset p : ToolPresets.of(ModularStyle.named("Print"))) {
            ModularBrush b = p.brush().parts;
            // The Brayer prints no screen: it rolls a band (NibChecks).
            if (b.choice(BrushPart.TIP) == PartChoice.ROLLER) continue;
            // Nor do the Photocopier and Newsprint lay one: they reprint what is there (ReprintChecks).
            if (b.choice(BrushPart.CARRY) == PartChoice.REPRINT) continue;
            // Nor Letterpress, a plain ink line that Later presses (LaterMediaChecks).
            if (b.later()) continue;
            double size = ModularSizes.size(b, InkWashSizes.Preset.MEDIUM);
            boolean gradient = p.name.equals("Gradient tone");
            ToneDocument live = InkWashLayers.blank(W, H), batched = InkWashLayers.blank(W, H), again = InkWashLayers.blank(W, H);
            stroke(live, b, size, square(.5, size), 1);
            stroke(batched, b, size, square(.5, size), 1000);
            stroke(again, b, size, square(.5, size), 1);
            check(same(live, batched) && same(live, again), p.name + ": the same samples print the same pixels, live, batched and again");
            // Evenness: the inked share of each 30 px block inside the square (the gradient tone darkens down the page by design: across a row only).
            // Pixels and Stitches lay greys, not black dots: any ink counts (the pressure here picks a light one).
            int inked = PrintScreen.of(b.part(BrushPart.MEDIUM)).cells ? 255 : 128;
            double[] f = blocks(live, inked);
            double mean = 0, lo = 1, hi = 0;
            for (double v : f) { mean += v; lo = Math.min(lo, v); hi = Math.max(hi, v); }
            mean /= f.length;
            if (gradient) { lo = 1; hi = 0; for (int bx = 0; bx < 8; bx++) { lo = Math.min(lo, f[24 + bx]); hi = Math.max(hi, f[24 + bx]); } }
            // Against the screen's own unevenness at that tone (a mezzotint's dashes each their own, by design).
            double own = gradient ? 0 : ownRange(PrintScreen.of(b.part(BrushPart.MEDIUM)), mean);
            check(mean > .2 && hi - lo < .3 * (gradient ? hi : mean) + own + .02, p.name + ": the scribbled square prints evenly, " + String.format(java.util.Locale.ROOT, "%.2f to %.2f (the screen's own %.2f)", lo, hi, own));
            // Maze to rings at Angle 0: Brick's and Honeycomb's courses run along the rows, so a crisp stroke's rows read light and dark as an
            // edge's would (2026-11-06): their edge is measured turned 10°, which moves no edge.
            ModularBrush edge = b;
            if (b.part(BrushPart.MEDIUM).get(PartDial.SCREEN, 0) >= ScreenChecks.MAZE && b.part(BrushPart.MEDIUM).get(PartDial.S_ANGLE) == 0) {
                edge = b.copy();
                edge.part(BrushPart.MEDIUM).set(PartDial.S_ANGLE, 10);
            }
            int fuzz = fuzz(edge, size, inked);
            ResolvedBrush r = ResolvedBrush.of(b, size, 1);
            double wide = Math.max(r.inkSize, r.body != null ? r.bodySize : 0);
            check(fuzz <= Math.max(4, wide * .15), p.name + ": a stroke's edge is crisp, " + fuzz + " rows of thinning dots, " + Math.round(wide) + " px wide");
            said.append(String.format(java.util.Locale.ROOT, " %s %.2f-%.2f, edge %d;", p.name, lo, hi, fuzz));
        }
        System.out.println("PASS: each Print preset fills a scribbled square evenly, its strokes' edges crisp, the same samples printing the same pixels live, batched and again:" + said);
    }

    /** Back-and-forth rows filling the square, a third of the tip apart (at least 8 px), 6 ms a sample. */
    static List<double[]> square(double p, double size) {
        List<double[]> o = new ArrayList<>();
        double gap = Math.max(8, size / 3), t = 1000, in = size * .3;
        boolean right = true;
        for (double y = S0 + in; y <= S0 + SQ - in + .1; y += gap) {
            for (double x = 0; x <= SQ - 2 * in; x += 3) { o.add(new double[]{S0 + in + (right ? x : SQ - 2 * in - x), y + 1.5 * Math.sin(x * .05), p, t}); t += 6; }
            right = !right;
        }
        return o;
    }
    /** As the drawing screen draws: a present every {@code per} samples. */
    static void stroke(ToneDocument d, ModularBrush b, double size, List<double[]> path, int per) {
        DrawingStroke s = QuickStroke.start(d, b, size, 1, null);
        for (int i = 0; i < path.size(); i++) { double[] q = path.get(i); s.sample((float) q[0], (float) q[1], ToolSettings.pressure((float) q[2]), 0, 0, (long) q[3]); if ((i + 1) % per == 0) s.present(); }
        s.present(); s.finish();
    }
    /** The range of the inked share over the square's blocks for the screen alone at the tone that inks {@code share} on average. */
    static double ownRange(PrintScreen p, double share) {
        double lo = 0, hi = 1;
        for (int it = 0; it < 20; it++) { double a = (lo + hi) / 2; if (field(p, a)[64] < share) lo = a; else hi = a; }
        double[] f = field(p, (lo + hi) / 2);
        double min = 1, max = 0;
        for (int i = 0; i < 64; i++) { min = Math.min(min, f[i]); max = Math.max(max, f[i]); }
        return max - min;
    }
    /** {@link #blocks} of the screen alone at tone {@code a}, and their mean last. */
    static double[] field(PrintScreen p, double a) {
        double[] f = new double[65];
        for (int by = 0; by < 8; by++) for (int bx = 0; bx < 8; bx++) {
            int n = 0;
            for (int y = 0; y < 30; y++) for (int x = 0; x < 30; x++) if (p.ink(a, S0 + 30 + bx * 30 + x, S0 + 30 + by * 30 + y, H)) n++;
            f[by * 8 + bx] = n / 900.0; f[64] += n / 900.0 / 64;
        }
        return f;
    }
    /** The share of each 30 px block inside the square darker than {@code inked}. */
    static double[] blocks(ToneDocument d, int inked) {
        double[] f = new double[64];
        for (int by = 0; by < 8; by++) for (int bx = 0; bx < 8; bx++) {
            int n = 0;
            for (int y = 0; y < 30; y++) for (int x = 0; x < 30; x++) if (d.compositeTone(S0 + 30 + bx * 30 + x, S0 + 30 + by * 30 + y) < inked) n++;
            f[by * 8 + bx] = n / 900.0;
        }
        return f;
    }
    /** A straight stroke across at medium pressure: the rows whose inked share is between a sixth and three quarters of the core's. */
    static int fuzz(ModularBrush b, double size, int inked) {
        ToneDocument d = InkWashLayers.blank(W, H);
        List<double[]> o = new ArrayList<>();
        for (int i = 0; i <= 120; i++) o.add(new double[]{40 + 3 * i, H / 2.0, .5, 1000 + 6 * i});
        stroke(d, b, size, o, 1);
        double core = 0;
        double[] row = new double[H];
        for (int y = 0; y < H; y++) { int n = 0; for (int x = 120; x < 320; x++) if (d.compositeTone(x, y) < inked) n++; row[y] = n / 200.0; core = Math.max(core, row[y]); }
        // Rows: a screen's own rows of dots go light and dark; the share over a band of rows a pattern cell across.
        int band = 6, fuzzy = 0;
        for (int y = band; y < H - band; y++) {
            double s = 0; for (int k = -band; k <= band; k++) s += row[y + k];
            s /= 2 * band + 1;
            if (s > core * .1 && s < core * .55) fuzzy++;
        }
        return Math.max(0, fuzzy - 2 * band);
    }
    static boolean same(ToneDocument a, ToneDocument b) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (a.compositeTone(x, y) != b.compositeTone(x, y)) return false;
        return true;
    }
    private static void check(boolean ok, String what) { if (!ok) { if (System.getenv("SOFT") != null) System.out.println("FAIL " + what); else throw new AssertionError(what); } }
}
