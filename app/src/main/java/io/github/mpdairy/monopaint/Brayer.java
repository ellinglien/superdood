package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;

/**
 * Brayer (explore-nibs; the module gaps spec's Roller): a printmaker's rubber roller. It lays a band as wide as the tip,
 * square to the way it rolls (it turns slowly, as a roller does, never sideways), with square ends and a ridge of ink
 * at each. Its ink is on the roller, an uneven rolled-out film fixed to the roller's surface, so the same mottle comes
 * round again every {@link #ROUND} of its width, each turn a little paler as the ink runs off. Light pressure prints
 * only the paper's high points, pressing harder prints solid; leaning the handle presses one end harder. It picks up
 * what it rolls over (the page as the stroke found it, {@link ModularCompose.Under}), Pick Up Paint's share, and prints
 * it again one turn later, fainter each turn: ghosts of the marks it crossed, repeating down the band. Laid every
 * pixel along the stroke from its distance alone, its roller carried in order, so however the samples are batched the
 * same marks land. Keeps the strongest coverage per pixel. Pure, checked on the desktop ({@code NibChecks}).
 */
final class Brayer {
    private Brayer() { }

    /** The roller's circumference, a share of its width: a brayer a third as thick as it is wide. */
    static final double ROUND = 1.05;
    /** Distance between two prints of the roller's contact line, px. */
    static final double STEP = 1;

    /** One stroke's roller: the next print's distance, its heading, its seed, and its surface's ink and what it picked up, around by across. */
    static final class State { double next, hx = Double.NaN, hy; int seed, around, across; float[] own, picked; }

    /** How far it reaches across, page px: its width turned any way, its ends' ridges. */
    static double reach(double size) { return size * 1.1 + 4; }

    static int[] stamp(FloatTiles into, FloatTiles wetD, StrokePaper paper, ModularCompose.Under under, ResolvedBrush.Pattern P, StrokePoints pts, double size, int from,
            LineStamper.Meta meta) {
        int[] rect = StrokeMath.emptyRect(into.width, into.height);
        if (pts.count < 2) return rect;
        if (meta.roller == null) meta.roller = start(P, pts, size);
        State M = meta.roller;
        double W = M.across, wear = .3 * (1.1 - P.load);
        StrokeMath.Span span = new StrokeMath.Span();
        float[] page = new float[M.across];
        for (int k = Math.max(1, from); k < pts.count; k++) {
            int j = k - 1;
            double seg = (double) pts.d[k] - pts.d[j];
            if (seg <= 0) continue;
            double ux = (pts.x[k] - (double) pts.x[j]) / seg, uy = (pts.y[k] - (double) pts.y[j]) / seg;
            while (M.next <= pts.d[k]) {
                double t = clamp((M.next - pts.d[j]) / seg);
                double x = lerp(pts.x[j], pts.x[k], t), y = lerp(pts.y[j], pts.y[k], t), pr = lerp(pts.p[j], pts.p[k], t), v = lerp(pts.v[j], pts.v[k], t);
                // A roller turns slowly toward where it is pushed.
                double was = Double.isNaN(M.hx) ? Math.atan2(uy, ux) : Math.atan2(M.hy, M.hx);
                if (Double.isNaN(M.hx)) { M.hx = ux; M.hy = uy; }
                else { double sx = M.hx + (ux - M.hx) * .12, sy = M.hy + (uy - M.hy) * .12, m = Math.hypot(sx, sy); if (m > 1e-6) { M.hx = sx / m; M.hy = sy / m; } }
                double ax = -M.hy, ay = M.hx, turned = Math.atan2(M.hy, M.hx) - was;
                if (turned > Math.PI) turned -= 2 * Math.PI; else if (turned < -Math.PI) turned += 2 * Math.PI;
                // Turning, its outer end runs ahead: the contact line is thicker there so no gap opens.
                double thick = 1.2 + W / 2 * Math.abs(turned);
                // Leaning the handle presses the end it leans toward harder.
                double tx = lerp(pts.tx[j], pts.tx[k], t), ty = lerp(pts.ty[j], pts.ty[k], t), tip = 0;
                if (!Double.isNaN(tx) && !Double.isNaN(ty)) {
                    double az = BrushDirection.azimuth(tx, ty);
                    if (!Double.isNaN(az)) tip = BrushDirection.tiltLean(tx, ty) * (Math.cos(az) * ax + Math.sin(az) * ay);
                }
                int row = (int) (Math.floor(M.next) % M.around);
                double dk = P.dark(pr, v) * pts.own(j, k, t) * pts.flickWidth(M.next), transfer = lerp(.45, 1, pr);
                // What it is about to roll onto, as the stroke found it (read before the roll prints there).
                for (int w = 0; w < M.across; w++) {
                    if (under == null) { page[w] = 0; continue; }
                    double off = w + .5 - W / 2;
                    int qx = (int) Math.floor(x + ax * off + M.hx * 2), qy = (int) Math.floor(y + ay * off + M.hy * 2);
                    page[w] = qx < 0 || qy < 0 || qx >= into.width || qy >= into.height ? 0 : 1 - under.tone(qx, qy) / 255f;
                }
                print(into, wetD, paper, M, span, row, x, y, ax, ay, thick, pr, tip, transfer, dk, M.next, rect);
                // The roller gives up some of its ink and of what it picked up, then picks up a share of what it rolled onto.
                for (int w = 0, at = row * M.across; w < M.across; w++, at++) {
                    double press = pressAt(pr, tip, w, W);
                    M.own[at] *= (float) (1 - wear * transfer * press);
                    M.picked[at] = (float) Math.min(1, M.picked[at] * (1 - .55 * transfer) + P.pick * .8 * press * page[w]);
                }
                M.next += STEP;
            }
        }
        return rect;
    }

    /** A stroke's roller: as wide as {@code size}, round as {@link #ROUND} of it, inked by Paint Load in an uneven film. */
    private static State start(ResolvedBrush.Pattern P, StrokePoints pts, double size) {
        State M = new State();
        M.seed = StippleStamper.hashInts((int) P.seed ^ 0xb7a7, Math.round(pts.x[0] * 4), Math.round(pts.y[0] * 4));
        M.across = Math.max(2, (int) Math.round(size));
        M.around = Math.max(4, (int) Math.round(size * ROUND));
        M.own = new float[M.around * M.across]; M.picked = new float[M.around * M.across];
        double ink = lerp(.8, 1.35, P.load);
        for (int a = 0; a < M.around; a++) for (int w = 0; w < M.across; w++) {
            // The film's mottle, joined round the roller: noise on a ring.
            // Its fine orange peel, and streaks round it where the film lies thinner.
            double ang = 2 * Math.PI * a / M.around, rr = M.around / (2 * Math.PI) / 1.6;
            double n = StampStamper.valueNoise(rr * Math.cos(ang) + 50, rr * Math.sin(ang) + w / 1.6, M.seed);
            double streak = StrokeMath.noise1(M.seed ^ 0x57e, w / 4.0);
            M.own[a * M.across + w] = (float) (ink * (.72 + .2 * n + .22 * streak));
        }
        return M;
    }

    /** Pressure at across place {@code w} of {@code W}: the pen's, more toward the end the handle leans to. */
    private static double pressAt(double pr, double tip, int w, double W) { return clamp(pr * (1 + .6 * tip * ((w + .5) / W * 2 - 1))); }

    /** The roller's contact line at {@code x, y} (axle {@code ax, ay}), its surface row {@code row}, into {@code into}. */
    private static void print(FloatTiles into, FloatTiles wetD, StrokePaper paper, State M, StrokeMath.Span span, int row, double x, double y, double ax, double ay,
            double thick, double pr, double tip, double transfer, double dk, double d, int[] rect) {
        if (!(dk > 0)) return;
        int w = into.width, h = into.height;
        double W = M.across, A = W / 2 + 1, B = thick + .5, R = Math.max(A, B) * 1.42;
        int x0 = Math.max(0, (int) Math.floor(x - R)), x1 = Math.min(w - 1, (int) Math.ceil(x + R));
        int y0 = Math.max(0, (int) Math.floor(y - R)), y1 = Math.min(h - 1, (int) Math.ceil(y + R));
        if (x1 < x0 || y1 < y0) return;
        span.set(A * 1.42, B * 1.42, ax, ay, x, y);
        int base = row * M.across;
        for (int py = y0; py <= y1; py++) {
            if (!span.row(py, x0, x1)) continue;
            for (int px = span.left; px <= span.right; px++) {
                double dx = px + .5 - x, dy = py + .5 - y, u = dx * ax + dy * ay, q = -dx * ay + dy * ax;
                double g = clamp(W / 2 + .5 - Math.abs(u)) * clamp(thick - Math.abs(q));
                if (g <= 0) continue;
                int wi = (int) clamp(Math.floor(u + W / 2), 0, M.across - 1);
                double press = pressAt(pr, tip, wi, W);
                // Its ends carry a ridge of ink.
                double ridge = W / 2 - Math.abs(u) < 1.6 ? 1.25 : 1;
                double ink = (M.own[base + wi] * ridge + M.picked[base + wi]) * transfer;
                // A light roll prints only the paper's high points.
                double tooth = StrokeMath.smoothstep(.45 - .9 * press, .75 - .9 * press, RulingPen.tooth(paper, px, py));
                float c = (float) (g * clamp(ink) * tooth * dk);
                if (c > 0 && into.raise(px, py, c) && wetD != null) wetD.set(px, py, (float) d);
            }
        }
        StrokeMath.grow(rect, x0, y0, x1, y1);
    }
}
