package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;

/**
 * Ruling Pen (explore-nibs): a draughtsman's ruling pen used for lettering, two steel blades with ink held between
 * them. Upright only the point touches, a hairline; laid over, the side of the blade drags along the page, a band as
 * long as the blade is low, lying along the pen's lean (its azimuth), so a stroke across the blade is broad and one
 * along it thin. The ink is a reservoir: the stroke spends it by the area it inks (a broad stroke soon, a hairline
 * hardly), pressure squeezes more out (darker, solider, sooner spent), and as it runs low the band breaks up, the
 * blade's end toward the hand first, on the paper's tooth and the blade's own nicks (streaks along the stroke). Where
 * it slows into a turn with ink to spare, and where it touches down overloaded, the reservoir drops a blot at the point.
 * Laid every {@link #STEP} along the stroke from its distance alone, its state carried in order, so however the
 * samples are batched the same marks land. Keeps the strongest coverage per pixel. Pure, checked on the desktop
 * ({@code NibChecks}).
 */
final class RulingPen {
    private RulingPen() { }

    /** Distance between two of the blade's dabs, px: under its thickness, so the band has no gaps. */
    static final double STEP = .75;
    /** Without a tilt reading the pen leans this far, its blade at 40° (a broad, even mark). */
    static final double NO_TILT_LEAN = .55, NO_TILT_AZ = 40 * Math.PI / 180;

    /**
     * One stroke's pen: the next dab's distance, the ink left (1 full), where it last blotted (its touchdown, so a stroke
     * starting slow, its pace still unknown, doesn't blot as a slow turn would), its seed, dabs laid.
     */
    static final class State { double next, load = 1, lastBlot; int seed, dabs; }

    /** The reservoir, px² of ink: Paint Load from a few broad letters to a page of them; a hairline spends little of it. */
    static double capacity(ResolvedBrush.Pattern P, double size) { return size * size * lerp(4, 40, P.load) + 400; }
    /**
     * How far past a usual load {@code P}'s Paint Load is, 0 at its middle and under to 1 full: only an overloaded pen or
     * twig drops a blob where it lands (the owner, 2026-11-02: the twig "leaves a blob often at the beginning of the mark").
     */
    static double flooded(ResolvedBrush.Pattern P) { return clamp((P.load - .5) * 2); }
    /**
     * A nib's hold at a sample (this pen's, {@link TwigStick}'s, {@link FanBrush}'s): its lean ({@link
     * BrushDirection#tiltLean}) and azimuth into {@code out}; {@code noLean} and {@code noAz} without a tilt reading, and
     * {@code noAz} where the lean has no direction.
     */
    static void hold(double tx, double ty, double noLean, double noAz, double[] out) {
        out[0] = noLean; out[1] = noAz;
        if (Double.isNaN(tx) || Double.isNaN(ty)) return;
        out[0] = BrushDirection.tiltLean(tx, ty);
        double a = BrushDirection.azimuth(tx, ty);
        if (!Double.isNaN(a)) out[1] = a;
    }
    /** The blade's half-length laid on the page at {@code lean}, px: nothing upright, half the size laid over. */
    static double blade(double size, double lean) { return size * .5 * StrokeMath.smoothstep(.04, .85, lean); }
    /** The blade's half-thickness at pressure {@code pr}, px. */
    static double thick(double size, double pr) { return Math.max(.55, size * .03) * (.75 + .6 * pr); }
    /**
     * The mark at one sample, full of ink, as an ellipse round the pen ({@link FootprintSweep}, the hover ring): the
     * blade's capsule along its azimuth, {a, b, cos, sin} into {@code e}.
     */
    static void footprint(double size, double pr, double tx, double ty, double flick, double[] e) {
        hold(tx, ty, NO_TILT_LEAN, NO_TILT_AZ, e);
        double az = e[1], th = thick(size, pr) * flick;
        e[0] = blade(size, e[0]) * flick + th; e[1] = th; e[2] = Math.cos(az); e[3] = Math.sin(az);
    }

    /** How far it reaches across, page px: the blade's length laid flat and a blot past it. */
    static double reach(double size) { return size * 1.4 + 4; }

    static int[] stamp(FloatTiles into, FloatTiles wetD, StrokePaper paper, ResolvedBrush.Pattern P, StrokePoints pts, double size, int from, LineStamper.Meta meta) {
        int[] rect = StrokeMath.emptyRect(into.width, into.height);
        if (pts.count < 2) return rect;
        if (meta.ruling == null) {
            meta.ruling = new State();
            meta.ruling.seed = StippleStamper.hashInts((int) P.seed ^ 0x5b1e, Math.round(pts.x[0] * 4), Math.round(pts.y[0] * 4));
        }
        State M = meta.ruling;
        double cap = capacity(P, size);
        StrokeMath.Span span = new StrokeMath.Span();
        double[] held = new double[2];
        for (int k = Math.max(1, from); k < pts.count; k++) {
            int j = k - 1;
            double seg = (double) pts.d[k] - pts.d[j];
            double ux = seg > 0 ? (pts.x[k] - (double) pts.x[j]) / seg : 1, uy = seg > 0 ? (pts.y[k] - (double) pts.y[j]) / seg : 0;
            // How sharply the line turns here: the smoothed heading's change, 0 straight to 1 a right angle.
            double turn = Float.isNaN(pts.ux[j]) ? 0 : clamp((1 - (pts.ux[j] * pts.ux[k] + pts.uy[j] * pts.uy[k])) * 6);
            while (M.next <= pts.d[k]) {
                double t = seg > 0 ? clamp((M.next - pts.d[j]) / seg) : 1;
                double x = lerp(pts.x[j], pts.x[k], t), y = lerp(pts.y[j], pts.y[k], t), pr = lerp(pts.p[j], pts.p[k], t), v = lerp(pts.v[j], pts.v[k], t);
                hold(lerp(pts.tx[j], pts.tx[k], t), lerp(pts.ty[j], pts.ty[k], t), NO_TILT_LEAN, NO_TILT_AZ, held);
                double lean = held[0], az = held[1], flick = pts.flickWidth(M.next), bx = Math.cos(az), by = Math.sin(az);
                // Upright a hairline; laid over, the side of the blade, as long as the pen is low.
                double hl = blade(size, lean) * flick, th = thick(size, pr) * flick;
                // Running low, the blade skips: stretches where only the point keeps the page, more as it dries.
                double skip = clamp(((1 - M.load) * .85 - .25 * pr - StrokeMath.noise1(M.seed ^ 0x5c1, M.next / (size * .5 + 4))) * 6);
                hl *= 1 - skip;
                double ink = M.load * (.5 + .55 * pr) * (1 - .5 * skip) * pts.own(j, k, t), dk = P.dark(pr, v);
                blade(into, wetD, paper, M.seed, span, x, y, bx, by, hl, th, ink, dk, M.next, rect);
                // The ink spent: the band's width across the line of travel, squeezed out harder by pressure.
                double spent = (2 * hl * Math.abs(bx * uy - by * ux) + 2 * th) * STEP * (.6 + .8 * pr);
                // A blot where it slows into a turn with ink to spare, at the point; where it touches down only overloaded.
                double slow = 1 - clamp(v / .2), want = M.dabs == 0 ? (.6 + .4 * pr) * flooded(P) : slow * (.25 + turn) * (.6 + .6 * pr);
                want *= M.load;
                int cell = (int) Math.floor(M.next / (size * .5 + 2));
                if (want > .18 && (M.dabs == 0 || M.next - M.lastBlot > size * .6 + 3) && StrokeMath.hash01(M.seed ^ 0xb107, cell) < want) {
                    double rb = th + size * (.03 + .09 * Math.min(1, want)) * (.6 + .6 * pr);
                    blot(into, wetD, M.seed ^ cell, x - bx * hl * .8, y - by * hl * .8, rb, dk, M.next, rect);
                    spent += Math.PI * rb * rb;
                    M.lastBlot = M.next;
                }
                M.load = Math.max(0, M.load - spent / cap);
                M.next += STEP; M.dabs++;
            }
        }
        return rect;
    }

    /** The page's tooth at a pixel, 0 valley to 1 peak: the paper's grain, and a fine grain of its own so smooth paper skips too. */
    static double tooth(StrokePaper paper, int px, int py) {
        // On a fine grid: its own grain read at the point's place on the page.
        double ix = paper == null ? px : paper.indexX(px), iy = paper == null ? py : paper.indexY(py);
        double own = StampStamper.valueNoise(ix * .6, iy * .6, 0x70074);
        return paper == null ? own : .5 * own + .5 * paper.fine(px, py);
    }
    /** How much of ink {@code ink} (1 full) a pixel of tooth-and-nicks {@code n} takes: solid when full, the peaks only as it runs dry. */
    static double takes(double ink, double n) { return clamp((ink * 1.25 - (1 - n)) * 3.5 + .15); }

    /** The blade at {@code x, y}: a capsule half {@code hl} long along {@code bx, by}, half {@code th} thick, inked by {@code ink}. */
    private static void blade(FloatTiles into, FloatTiles wetD, StrokePaper paper, int seed, StrokeMath.Span span, double x, double y, double bx, double by,
            double hl, double th, double ink, double dk, double d, int[] rect) {
        if (!(dk > 0) || !(ink > 0)) return;
        int w = into.width, h = into.height;
        // On a fine grid (sharp dry brushes): the blade in the grid's points, its edge a point wide; its nicks as far apart on the page.
        FineGrid fine = into.grid;
        double nick = Math.max(1.2, th * 1.6), half = hl;
        if (fine != null) { int k = fine.k; x = fine.gridX(x); y = fine.gridY(y); hl *= k; th *= k; nick *= k; }
        double A = hl + th + 1, B = th + 1.5, R = A * 1.42;
        int x0 = Math.max(0, (int) Math.floor(x - R)), x1 = Math.min(w - 1, (int) Math.ceil(x + R));
        int y0 = Math.max(0, (int) Math.floor(y - R)), y1 = Math.min(h - 1, (int) Math.ceil(y + R));
        if (x1 < x0 || y1 < y0) return;
        // The ellipse round the capsule's box: rows visit only its span.
        span.set(A * 1.42, B * 1.42, bx, by, x, y);
        for (int py = y0; py <= y1; py++) {
            if (!span.row(py, x0, x1)) continue;
            for (int px = span.left; px <= span.right; px++) {
                double dx = px + .5 - x, dy = py + .5 - y, u = dx * bx + dy * by, q = -dx * by + dy * bx;
                double cu = clamp(u, -hl, hl), g = clamp(th + .5 - Math.hypot(u - cu, q));
                if (g <= 0) continue;
                // Most ink at the point (u = −hl), least toward the hand; the blade's nicks streak it along the stroke.
                double along = half > .5 ? (u + hl) / (2 * hl) : 0;
                double n = .5 * tooth(paper, px, py) + .5 * StrokeMath.noise1(seed, (u + hl) / nick);
                float c = (float) (g * takes(ink * (1.12 - .5 * along), n) * dk);
                if (c > 0 && into.raise(px, py, c) && wetD != null) wetD.set(px, py, (float) d);
            }
        }
        StrokeMath.grow(rect, x0, y0, x1, y1);
    }

    /** A blot of radius {@code r}: a round drop and two or three smaller ones run off it. */
    static void blot(FloatTiles into, FloatTiles wetD, int seed, double cx, double cy, double r, double dk, double d, int[] rect) { blot(into, wetD, seed, cx, cy, r, dk, d, rect, 1); }
    /** {@link #blot}, its runs {@code runs} as big (0: a round drop alone). */
    static void blot(FloatTiles into, FloatTiles wetD, int seed, double cx, double cy, double r, double dk, double d, int[] rect, double runs) {
        StippleStamper.dot(into, wetD, cx, cy, r, dk, d, rect);
        if (runs <= 0) return;
        r *= runs;
        for (int m = 0, n = 2 + (int) (StrokeMath.hash01(seed, 0) * 2); m < n; m++) {
            double a = 2 * Math.PI * StrokeMath.hash01(seed, 1 + m), off = r * lerp(.5, 1.1, StrokeMath.hash01(seed, 5 + m));
            StippleStamper.dot(into, wetD, cx + off * Math.cos(a), cy + off * Math.sin(a), r * lerp(.3, .6, StrokeMath.hash01(seed, 9 + m)), dk, d, rect);
        }
    }
}
