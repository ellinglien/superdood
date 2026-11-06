package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;

/**
 * Twig (explore-nibs): a stick dipped in ink. Its end is a lumpy, splintered knob (its outline fixed per stroke, as
 * gnarled as Wobble says), turning a little in the fingers as it goes, so the line's width comes and goes whichever way
 * it runs. It holds little ink: a round start where it touches down (a blob when overloaded), then the line soon breaks into dry, scratchy streaks
 * along the grain of its splinters and on the paper's tooth, and now and then it catches and drops a blob. Pressure
 * squashes the end wider and squeezes out more ink; tilt lays it on its side, a longer, drier scrape along the lean.
 * Laid every so often along the stroke from its distance alone, its state carried in order, so however the samples
 * are batched the same marks land. Keeps the strongest coverage per pixel. Pure, checked on the desktop ({@code NibChecks}).
 */
final class TwigStick {
    private TwigStick() { }

    /** The end's outline: this many radii round it. */
    static final int ROUND = 48;

    /** One stroke's stick: the next dab's distance, the ink left, its seed, dabs laid, the end's outline (1 its mean radius). */
    static final class State { double next, load = 1, most; int seed, dabs; final double[] rim = new double[ROUND + 1]; final StrokeMath.Span span = new StrokeMath.Span(); double[] tracks = new double[0]; }

    /** The ink it holds, px²: a word or two of writing, a few strokes of a broad scrape. */
    static double capacity(ResolvedBrush.Pattern P, double size) { return size * size * lerp(2.5, 25, P.load) + 400; }
    /** The end's mean radius at pressure {@code pr}, px: pressure squashes it wider. */
    static double knob(double size, double pr) { return size * .5 * (.22 + .5 * pr); }
    /** The pen's lean from a sample's tilt, and the azimuth it lies along (NaN, and no lean, without one): {lean, az} into {@code out}. */
    static void hold(double tx, double ty, double[] out) {
        RulingPen.hold(tx, ty, 0, Double.NaN, out);
        if (Double.isNaN(out[1])) out[0] = 0;
    }
    /** How far laid on its side ({@code lean}) stretches the end along the lean. */
    static double stretch(double lean) { return 1 + 1.8 * lean; }
    /** The ink the end lays with {@code load} left (1 full): pressure squeezes out more, laid on its side it scrapes drier. */
    static double ink(double load, double pr, double lean) { return load * (.5 + .7 * pr) * (1 - .35 * lean); }
    /**
     * How far out the end inks at least half its pixels with {@code ink}, a share of its outline: where {@link
     * RulingPen#takes} passes .5 on the average tooth and track ({@code end}'s n, .35 + .4 × the share left to the rim).
     * A light touch's drier end lays its core only.
     */
    static double inks(double ink) { return clamp((1.25 * ink - .35) / .4); }
    /**
     * The mark at one sample, full of ink, as an ellipse round the pen ({@link FootprintSweep}, the hover ring): the end as
     * far out as it inks ({@link #inks}, {@link #TYPICAL} of its mean radius, as it turns), stretched along the lean, {a,
     * b, cos, sin} into {@code e}.
     */
    static void footprint(double size, double pr, double tx, double ty, double flick, double[] e) {
        hold(tx, ty, e);
        double lean = e[0], az = e[1], r = knob(size, pr) * flick * TYPICAL * inks(ink(1, pr, lean));
        e[0] = r * stretch(lean); e[1] = r; e[2] = Double.isNaN(az) ? 1 : Math.cos(az); e[3] = Double.isNaN(az) ? 0 : Math.sin(az);
    }
    /** How far the end's lumpy outline usually reaches as it turns, a share of its mean radius (measured: {@code PreviewWidthChecks}). */
    static final double TYPICAL = .95;

    /** How far it reaches across, page px: its end pressed and splayed, laid on its side, and a blob. */
    static double reach(double size) { return size * 2.4 + 4; }

    static int[] stamp(FloatTiles into, FloatTiles wetD, StrokePaper paper, ResolvedBrush.Pattern P, StrokePoints pts, double size, int from, LineStamper.Meta meta) {
        int[] rect = StrokeMath.emptyRect(into.width, into.height);
        if (pts.count < 2) return rect;
        if (meta.twig == null) meta.twig = start(P, pts);
        State M = meta.twig;
        double cap = capacity(P, size);
        double[] held = new double[2];
        for (int k = Math.max(1, from); k < pts.count; k++) {
            int j = k - 1;
            double seg = (double) pts.d[k] - pts.d[j];
            double ux = seg > 0 ? (pts.x[k] - (double) pts.x[j]) / seg : 1, uy = seg > 0 ? (pts.y[k] - (double) pts.y[j]) / seg : 0;
            while (M.next <= pts.d[k]) {
                double t = seg > 0 ? clamp((M.next - pts.d[j]) / seg) : 1;
                double x = lerp(pts.x[j], pts.x[k], t), y = lerp(pts.y[j], pts.y[k], t), pr = lerp(pts.p[j], pts.p[k], t), v = lerp(pts.v[j], pts.v[k], t);
                double tx = lerp(pts.tx[j], pts.tx[k], t), ty = lerp(pts.ty[j], pts.ty[k], t);
                hold(tx, ty, held);
                double lean = held[0], az = held[1], r0 = knob(size, pr) * pts.flickWidth(M.next);
                // It turns in the fingers: slowly, by noise along the stroke.
                double phi = 2.6 * (StrokeMath.noise1(M.seed ^ 0x7a1, M.next / (size * 2.2 + 8)) - .5);
                double ink = ink(M.load, pr, lean) * pts.own(j, k, t), dk = P.dark(pr, v);
                if (M.dabs == 0) {
                    // Touching down: the ink on the end lands a little heavier, a round start as far out as the end inks
                    // (a light touch's drier end, its core); overloaded (Paint Load past its middle) a blob, runs and all.
                    double flood = RulingPen.flooded(P);
                    RulingPen.blot(into, wetD, M.seed ^ 0xd0b, x, y, r0 * (1.1 * inks(ink) + .6 * pr * flood), dk, M.next, rect, flood);
                    M.load -= Math.PI * r0 * r0 * .6 / cap;
                }
                end(into, wetD, paper, M, x, y, ux, uy, r0, phi, lean, az, ink, dk, M.next, rect);
                double step = Math.max(1, r0 * .25);
                M.load = Math.max(0, M.load - 2 * r0 * step * (.6 + .8 * pr) / cap);
                // Now and then it catches on the paper and drops a blob off its edge.
                int cell = (int) Math.floor(M.next / (size * 1.6 + 6));
                if (cell != (int) Math.floor((M.next - step) / (size * 1.6 + 6)) && StrokeMath.hash01(M.seed ^ 0xca7, cell) < .22 * (M.load + .15)) {
                    double side = (StrokeMath.hash01(M.seed ^ 0xca8, cell) - .5) * 1.4 * r0;
                    RulingPen.blot(into, wetD, M.seed ^ cell, x - uy * side, y + ux * side, r0 * lerp(.3, .6, StrokeMath.hash01(M.seed ^ 0xca9, cell)), dk, M.next, rect);
                }
                M.next += step; M.dabs++;
            }
        }
        return rect;
    }

    /** A stroke's stick: its seed, and its end's outline from it, lumps and a few splinters, as gnarled as Wobble. */
    private static State start(ResolvedBrush.Pattern P, StrokePoints pts) {
        State M = new State();
        M.seed = StippleStamper.hashInts((int) P.seed ^ 0x7e19, Math.round(pts.x[0] * 4), Math.round(pts.y[0] * 4));
        double wob = .25 + .75 * P.wobble;
        int spikes = 3 + (int) (StrokeMath.hash01(M.seed, 50) * 4);
        for (int i = 0; i <= ROUND; i++) {
            double a = (double) (i % ROUND) / ROUND, r = 1 + wob * (.7 * (StrokeMath.noise1(M.seed, a * 5) - .5) + .5 * (StrokeMath.noise1(M.seed ^ 0x51, a * 11) - .5));
            for (int s = 0; s < spikes; s++) {
                double at = StrokeMath.hash01(M.seed, 60 + s), da = Math.abs(a - at);
                da = Math.min(da, 1 - da);
                r += wob * .45 * StrokeMath.hash01(M.seed, 70 + s) * Math.exp(-da * da * 1600);
            }
            M.rim[i] = Math.max(.35, r);
            M.most = Math.max(M.most, M.rim[i]);
        }
        return M;
    }

    /** The angle of {@code u, q} as a share of a turn, 0 to 1: a quick arctangent (within a thousandth of a radian), enough for an outline. */
    static double turnOf(double u, double q) {
        double au = Math.abs(u), aq = Math.abs(q), lo = Math.min(au, aq), hi = Math.max(au, aq);
        if (hi == 0) return 0;
        double z = lo / hi, z2 = z * z, a = ((-.0464964749 * z2 + .15931422) * z2 - .327622764) * z2 * z + z;
        if (aq > au) a = Math.PI / 2 - a;
        if (u < 0) a = Math.PI - a;
        if (q < 0) a = 2 * Math.PI - a;
        return Math.min(a / (2 * Math.PI), .999999);
    }
    /** {@code tracks} at {@code at}, eased between its whole places. */
    private static double track(double[] tracks, double at) {
        int i = (int) Math.max(0, Math.min(tracks.length - 2, Math.floor(at)));
        return tracks[i] + (tracks[i + 1] - tracks[i]) * clamp(at - i);
    }
    /** The end at {@code x, y}, radius {@code r0}, turned {@code phi}, laid out along {@code az} as far as {@code lean}. */
    private static void end(FloatTiles into, FloatTiles wetD, StrokePaper paper, State M, double x, double y, double ux, double uy, double r0, double phi, double lean,
            double az, double ink, double dk, double d, int[] rect) {
        if (!(dk > 0) || !(ink > 0) || !(r0 > 0)) return;
        int w = into.width, h = into.height;
        double stretch = stretch(lean), ax = lean > 0 ? Math.cos(az) : 1, ay = lean > 0 ? Math.sin(az) : 0;
        double R = r0 * M.most * stretch + 1.5;
        // Laid on its side the scrape runs back toward the hand.
        double cx = x + ax * r0 * (stretch - 1) * .6, cy = y + ay * r0 * (stretch - 1) * .6;
        // On a fine grid (sharp dry brushes): the end in the grid's points (its splinters' tracks still a page pixel apart), its edge a point wide.
        FineGrid fine = into.grid;
        int k = fine == null ? 1 : fine.k;
        double r1 = r0;
        if (fine != null) { cx = fine.gridX(cx); cy = fine.gridY(cy); R *= k; r1 = r0 * k; }
        int x0 = Math.max(0, (int) Math.floor(cx - R)), x1 = Math.min(w - 1, (int) Math.ceil(cx + R));
        int y0 = Math.max(0, (int) Math.floor(cy - R)), y1 = Math.min(h - 1, (int) Math.ceil(cy + R));
        if (x1 < x0 || y1 < y0) return;
        double cp = Math.cos(phi), sp = Math.sin(phi), grain = Math.max(1.3, r0 * .35);
        // The splinters' tracks across the stroke, worked out once a dab a pixel apart (eased between).
        int half = (int) Math.ceil(fine == null ? R : R / k) + 2;
        if (M.tracks.length < 2 * half + 2) M.tracks = new double[2 * half + 2];
        for (int t = 0; t <= 2 * half + 1; t++) M.tracks[t] = StrokeMath.noise1(M.seed ^ 0x9a1, (t - half) / grain);
        // Rows visit only the span of an ellipse round the stretched end.
        M.span.set(r1 * M.most * stretch + 1.5, r1 * M.most + 1.5, ax, ay, cx, cy);
        for (int py = y0; py <= y1; py++) {
          if (!M.span.row(py, x0, x1)) continue;
          for (int px = M.span.left; px <= M.span.right; px++) {
            // A pixel already as dark as this dab can lay is left before any work.
            if (into.get(px, py) >= dk) continue;
            double dx = px + .5 - cx, dy = py + .5 - cy;
            // Undo the lean's stretch, then the turn, to the end's own outline.
            double along = dx * ax + dy * ay, across = -dx * ay + dy * ax;
            along /= stretch;
            double ex = along * ax - across * ay, ey = along * ay + across * ax;
            double u = ex * cp + ey * sp, q = -ex * sp + ey * cp, rho = Math.hypot(u, q);
            if (rho > r1 * M.most + 1) continue;
            double f = turnOf(u, q) * ROUND;
            int i = (int) f;
            double edge = r1 * lerp(M.rim[i], M.rim[i + 1], f - i), g = clamp(edge - rho + .5);
            if (g <= 0) continue;
            // Dry: a few tracks along the stroke from its splinters, the paper's tooth, and its rim before its core, so a
            // drying line thins and scratches rather than fading.
            double side = -dx * uy + dy * ux;
            double n = .35 * RulingPen.tooth(paper, px, py) + .35 * track(M.tracks, half + (fine == null ? side : side / k)) + .4 * (1 - rho / edge);
            float c = (float) (g * RulingPen.takes(ink, n) * dk);
            if (c > 0 && into.raise(px, py, c) && wetD != null) wetD.set(px, py, (float) d);
          }
        }
        StrokeMath.grow(rect, x0, y0, x1, y1);
    }
}
