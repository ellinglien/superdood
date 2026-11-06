package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;

/**
 * Fan (explore-nibs): a fan brush, its hairs' tips spread in an arc, the fan's breadth lying across the pen's lean
 * (the way a filbert's does), so a stroke across the fan combs a band of separate hair lines and one along it lays them
 * over each other in one dense line. The arc decides which hairs touch: a light touch only the middle ones, pressing
 * harder brings the outer hairs down and splays the fan wider; tilt lays it flatter, more hairs touching and each a
 * little broader. Each hair wavers on its own and runs dry on its own (Dry Brush, and more when fast), on the paper's
 * tooth, so the lines break. A lift's falling pressure lifts the outer hairs first, so a quick flick ends in fine
 * points: grass, fur, foliage. Laid every {@link #STEP} along the stroke from its distance alone, so however the
 * samples are batched the same marks land. Keeps the strongest coverage per pixel. Pure, checked on the desktop
 * ({@code NibChecks}).
 */
final class FanBrush {
    private FanBrush() { }

    /** Distance between two of the fan's places, px; each hair joins its places with a line. */
    static final double STEP = 1.5;
    /** Without a tilt reading the fan lies flat across the page, its lean this far. */
    static final double NO_TILT_LEAN = .3;

    /** One stroke's fan: the next place's distance, its seed, and each hair's last place (NaN: it wasn't touching). */
    static final class State { double next; int seed, n; double[] hx, hy; }

    /** The azimuth the fan lies across without a reading: flat across the page. */
    static final double NO_TILT_AZ = Math.PI / 2;
    /** Half the fan's breadth at pressure {@code pr}, px: pressing splays it. */
    static double half(ResolvedBrush.Pattern P, double size, double pr) { return size * .5 * lerp(.65, 1.2, pr) * (.8 + .6 * P.splay); }
    /** How deep the arc presses: a hair a share {@code s} out from the middle touches while this is past {@code 1.1 s²}. */
    static double depth(double pr, double lean) { return .04 + .95 * pr + .3 * lean; }
    /** A hair's half-width, px: laid flatter, broader. */
    static double hair(double size, double lean) { return (.3 + size * .004) * (1 + .6 * lean); }
    /**
     * The mark at one sample as an ellipse round the pen ({@link FootprintSweep}, the hover ring): the hairs down far
     * enough to ink (a touch of .2: the arc .08 past their drop), across the lean, a hair thick, {a, b, cos, sin} into {@code e}.
     */
    static void footprint(ResolvedBrush.Pattern P, double size, double pr, double tx, double ty, double flick, double[] e) {
        RulingPen.hold(tx, ty, NO_TILT_LEAN, NO_TILT_AZ, e);
        double lean = e[0], az = e[1], hr = hair(size, lean) * 1.1;
        e[0] = half(P, size, pr) * flick * Math.min(1, Math.sqrt(Math.max(0, depth(pr, lean) - .08) / 1.1)) + hr; e[1] = hr; e[2] = -Math.sin(az); e[3] = Math.cos(az);
    }

    /** How far it reaches across, page px: the fan splayed at full pressure. */
    static double reach(ResolvedBrush.Pattern P, double size) { return size * 1.2 * (.8 + .6 * P.splay) + 6; }

    static int[] stamp(FloatTiles into, FloatTiles wetD, StrokePaper paper, ResolvedBrush.Pattern P, StrokePoints pts, double size, int from, LineStamper.Meta meta) {
        int[] rect = StrokeMath.emptyRect(into.width, into.height);
        if (pts.count < 2) return rect;
        if (meta.fan == null) {
            State M = meta.fan = new State();
            M.seed = StippleStamper.hashInts((int) P.seed ^ 0xfa4, Math.round(pts.x[0] * 4), Math.round(pts.y[0] * 4));
            M.n = (int) Math.round(clamp(P.hairs, 6, 32));
            M.hx = new double[M.n]; M.hy = new double[M.n];
            java.util.Arrays.fill(M.hx, Double.NaN);
        }
        State M = meta.fan;
        int n = M.n;
        double[] held = new double[2];
        for (int k = Math.max(1, from); k < pts.count; k++) {
            int j = k - 1;
            double seg = (double) pts.d[k] - pts.d[j];
            while (M.next <= pts.d[k]) {
                double t = seg > 0 ? clamp((M.next - pts.d[j]) / seg) : 1;
                double x = lerp(pts.x[j], pts.x[k], t), y = lerp(pts.y[j], pts.y[k], t), pr = lerp(pts.p[j], pts.p[k], t), v = lerp(pts.v[j], pts.v[k], t);
                RulingPen.hold(lerp(pts.tx[j], pts.tx[k], t), lerp(pts.ty[j], pts.ty[k], t), NO_TILT_LEAN, NO_TILT_AZ, held);
                double lean = held[0], az = held[1];
                // The fan's breadth lies across the lean.
                double fx = -Math.sin(az), fy = Math.cos(az), flick = pts.flickWidth(M.next);
                double half = half(P, size, pr) * flick, gap = 2 * half / n;
                // How deep the arc presses: past a hair's drop it touches.
                double depth = depth(pr, lean), dk = P.dark(pr, v) * pts.own(j, k, t);
                double dry = clamp(P.dryness + .3 * clamp((v - .6) / 2));
                double hr = hair(size, lean);
                for (int i = 0; i < n; i++) {
                    double s = -1 + 2 * (i + .5) / n + (StrokeMath.hash01(M.seed, i) - .5) * .6 / n;
                    double touch = clamp((depth - 1.1 * s * s) * 2.5) * flick;
                    if (touch <= 0) { M.hx[i] = Double.NaN; continue; }
                    // Each hair wavers on its own.
                    double off = s * half + (StrokeMath.noise1(M.seed ^ i * 0x3b, M.next / (size * .9 + 6)) - .5) * gap * .7;
                    double hx = x + fx * off, hy = y + fy * off;
                    if (!Double.isNaN(M.hx[i])) {
                        double ink = (1 - dry * (.4 + .6 * StrokeMath.hash01(M.seed ^ 0x77, i))) * (.45 + .45 * touch);
                        hair(into, wetD, paper, M.seed ^ i * 0x9e37, M.hx[i], M.hy[i], hx, hy, hr * (.6 + .5 * touch), ink, dk, M.next, rect);
                    }
                    M.hx[i] = hx; M.hy[i] = hy;
                }
                M.next += STEP;
            }
        }
        return rect;
    }

    /** One hair's line from {@code ax, ay} to {@code bx, by}, half {@code r} wide, inked by {@code ink}, breaking on the tooth and its own dryness. */
    private static void hair(FloatTiles into, FloatTiles wetD, StrokePaper paper, int seed, double ax, double ay, double bx, double by, double r, double ink, double dk,
            double d, int[] rect) {
        if (!(dk > 0)) return;
        // On a fine grid (sharp dry brushes): the hair in the grid's points, its edge a point wide.
        FineGrid fine = into.grid;
        if (fine != null) { ax = fine.gridX(ax); ay = fine.gridY(ay); bx = fine.gridX(bx); by = fine.gridY(by); r *= fine.k; }
        int w = into.width, h = into.height;
        int x0 = Math.max(0, (int) Math.floor(Math.min(ax, bx) - r - 1)), x1 = Math.min(w - 1, (int) Math.ceil(Math.max(ax, bx) + r + 1));
        int y0 = Math.max(0, (int) Math.floor(Math.min(ay, by) - r - 1)), y1 = Math.min(h - 1, (int) Math.ceil(Math.max(ay, by) + r + 1));
        if (x1 < x0 || y1 < y0) return;
        double ex = bx - ax, ey = by - ay, len2 = Math.max(1e-9, ex * ex + ey * ey);
        // The hair's own skips along it: noise by distance, a few px a feature.
        double take = RulingPen.takes(ink, .7 * StrokeMath.noise1(seed, d / 7) + .1);
        for (int py = y0; py <= y1; py++) for (int px = x0; px <= x1; px++) {
            double dx = px + .5 - ax, dy = py + .5 - ay, f = clamp((dx * ex + dy * ey) / len2);
            double g = clamp(r + .5 - Math.hypot(dx - f * ex, dy - f * ey));
            if (g <= 0) continue;
            float c = (float) (g * Math.min(take, RulingPen.takes(ink, RulingPen.tooth(paper, px, py) * .6 + .4)) * dk);
            if (c > 0 && into.raise(px, py, c) && wetD != null) wetD.set(px, py, (float) d);
        }
        StrokeMath.grow(rect, x0, y0, x1, y1);
    }
}
