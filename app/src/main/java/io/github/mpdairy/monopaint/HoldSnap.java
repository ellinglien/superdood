package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hold to Snap (the module gaps spec's first, Procreate's QuickShape): a pen stroke held still at its end for
 * {@link #HOLD_MS}, the pen still down, becomes the clean shape it was roughly drawn as: a line, an arc, a circle or
 * oval, a polygon (a box squared), or an open run of straight lines. The pad draws it again with the same brush along
 * the clean path ({@link #along}): each rough sample's pressure, tilt and time go to the point the same share of the
 * way along, so a charcoal line is still charcoal and a watercolour oval still a watercolour oval. When unsure, nothing
 * snaps. A pause shorter than the hold never asks. The shape then follows the pen until it lifts ({@link #to}), as
 * QuickShape's: a line's or an arc's end, a polyline's last point; a closed shape scaled and turned about its centre.
 * Pure: {@link DrawingPad} feeds it the pen's samples and asks at its ticks; checked on the desktop ({@code HoldSnapChecks}).
 */
final class HoldSnap {
    /**
     * Held still this long, ms, the stroke snaps: past Android's long press (400–500 ms), well past Fling's stop (45)
     * and Float's drop (150), so neither of those holds snaps on its own; the brief's 500 to 600.
     */
    static final long HOLD_MS = 550;
    /** How often the pad asks while the pen is down, ms (a still pen may send no samples at all, as {@link HeldPen}). */
    static final long TICK_MS = 40;
    /** Still: within this many glass px of where the pen stopped, as Fling's. */
    static final double STILL_PX = FlickFling.STILL_PX;
    /** The smallest shape that snaps, its box's diagonal in glass px (about 8 mm on the Manta): letters don't. */
    static final double SMALLEST = 90;

    // Recognition, each a share of the shape's box diagonal unless said. Tuned on HoldSnapChecks' rough shapes.
    /** A line: no point further off the chord than this share of it, and the path at most this much longer. */
    static final double LINE_DEV = .06, LINE_LONGER = 1.12;
    /** Closed: the end comes back within this of the path's first quarter. */
    static final double CLOSE = .16;
    /** Polygon and polyline corners: the simplifying tolerance, the least turn a corner makes (radians), the least side. */
    static final double CORNER_TOL = .045, CORNER_TURN = Math.toRadians(32), SIDE = .1;
    /** A corner is sharp when within {@link #SHARP_NEAR} of the {@link #N} points either side it turns this share of its turn. */
    static final double SHARP = .6;
    static final int SHARP_NEAR = 4;
    /** Fits: the root mean square distance from the fitted shape, at most. */
    static final double POLY_FIT = .022, ELLIPSE_FIT = .028, ARC_FIT = .022;
    /** Straight sides must fit this much better than a curve to win. */
    static final double STRAIGHTER = .7;
    /** An arc turns at least this and at most this (radians). */
    static final double ARC_LEAST = Math.toRadians(25), ARC_MOST = Math.toRadians(330);
    /** An oval this round or rounder is a circle (long axis over short). */
    static final double ROUND = 1.12;
    /** A box: every corner within this of square (radians). */
    static final double SQUARE = Math.toRadians(20);
    /** Points the path is resampled to, evenly along it, for the fits. */
    static final int N = 128;

    enum Kind { LINE, ARC, CIRCLE, OVAL, POLYGON, BOX, POLYLINE }

    /** A clean shape: its kind and its path, a closed one ending where it began; {@code sharp}: its points are corners. */
    static final class Shape {
        final Kind kind; final double[] x, y; final boolean sharp;
        Shape(Kind kind, double[] x, double[] y, boolean sharp) { this.kind = kind; this.x = x; this.y = y; this.sharp = sharp; }
    }

    // ---- the pen ----

    private double[] x = new double[256], y = new double[256], p = new double[256], tx = new double[256], ty = new double[256];
    private long[] t = new long[256];
    private int n;
    private final double zoom;
    /** Where the pen came to rest, since when, the sample then; the rest already asked about. */
    double ax, ay;
    private long anchorT = PenSpeed.UNTIMED, asked = PenSpeed.UNTIMED;
    private int anchorK;
    /** The last snap's shape, null for none, and how many samples it carried: it may follow the pen yet ({@link #to}). */
    Shape snapped;
    private int carried;

    /** {@code zoom}: glass px a page px, as {@link DrawingStroke#zoom}. */
    HoldSnap(double zoom) { this.zoom = zoom > 0 ? zoom : 1; }

    /** Every pen sample, page px, as the stroke takes it. */
    void sample(double x, double y, double pressure, double tiltX, double tiltY, long time) {
        if (n == this.x.length) {
            int m = 2 * n;
            this.x = Arrays.copyOf(this.x, m); this.y = Arrays.copyOf(this.y, m); p = Arrays.copyOf(p, m);
            tx = Arrays.copyOf(tx, m); ty = Arrays.copyOf(ty, m); t = Arrays.copyOf(t, m);
        }
        this.x[n] = x; this.y[n] = y; p[n] = pressure; tx[n] = tiltX; ty[n] = tiltY; t[n] = time; n++;
        if (time == PenSpeed.UNTIMED) { anchorT = PenSpeed.UNTIMED; return; }
        if (anchorT == PenSpeed.UNTIMED || moved(x, y)) { ax = x; ay = y; anchorT = time; anchorK = n - 1; }
    }
    /** Whether the pen has rested {@link #HOLD_MS} at {@code now} (the samples' clock) and this rest is not yet asked about. */
    boolean due(long now) { return anchorT != PenSpeed.UNTIMED && now - anchorT >= HOLD_MS && asked != anchorT; }
    /**
     * This rest asked about: the clean shape's samples to draw again, rows {x, y, pressure, tiltX, tiltY, time}, the
     * samples up to the rest carried along it; null when the stroke is no clean shape (the pen draws on, and a later
     * rest asks again).
     */
    double[][] snap() {
        asked = anchorT;
        int k = anchorK + 1;
        Shape s = recognize(x, y, k, zoom);
        snapped = s; carried = k;
        return s == null ? null : along(s, x, y, p, tx, ty, t, k);
    }
    /** Whether {@code x, y} (page px) is off the pen's rest, as {@link #sample} tells a moving pen from a still one. */
    boolean moved(double x, double y) { return Math.hypot(x - ax, y - ay) * zoom > STILL_PX; }
    /**
     * The snapped shape following the pen to {@code x, y} ({@link #follow}): the same samples carried along it, each the
     * same share of the way ({@link #along}), so the brush's pressure, tilt and time go with the shape wherever it turns.
     */
    double[][] to(double x, double y) { return along(follow(x, y), this.x, this.y, p, tx, ty, t, carried); }
    /**
     * The snapped shape with the pen at {@code x, y}: a polyline's last point there; a line or an arc turned and scaled
     * about its start so its end is there (an arc's bulge kept in proportion); a closed shape turned and scaled about its
     * centre (its box's) so the point the pen rested on is there, a circle still a circle.
     */
    Shape follow(double x, double y) {
        Shape s = snapped;
        int m = s.x.length;
        double[] fx = s.x.clone(), fy = s.y.clone();
        if (s.kind == Kind.POLYLINE) { fx[m - 1] = x; fy[m - 1] = y; return new Shape(s.kind, fx, fy, s.sharp); }
        // Fixed point o, and the point f that goes to the pen.
        double ox, oy, f0, f1;
        if (s.kind == Kind.LINE || s.kind == Kind.ARC) { ox = fx[0]; oy = fy[0]; f0 = fx[m - 1]; f1 = fy[m - 1]; }
        else {
            double x0 = fx[0], x1 = fx[0], y0 = fy[0], y1 = fy[0];
            for (int i = 1; i < m; i++) { x0 = Math.min(x0, fx[i]); x1 = Math.max(x1, fx[i]); y0 = Math.min(y0, fy[i]); y1 = Math.max(y1, fy[i]); }
            ox = (x0 + x1) / 2; oy = (y0 + y1) / 2; f0 = ax; f1 = ay;
        }
        // Each point z goes to o + (z - o)(pen - o)/(f - o), as complex numbers: one turn and one scale.
        double ux = f0 - ox, uy = f1 - oy, d = ux * ux + uy * uy, vx = x - ox, vy = y - oy;
        if (d == 0) return s;
        double re = (vx * ux + vy * uy) / d, im = (vy * ux - vx * uy) / d;
        for (int i = 0; i < m; i++) {
            double dx = s.x[i] - ox, dy = s.y[i] - oy;
            fx[i] = ox + dx * re - dy * im; fy[i] = oy + dx * im + dy * re;
        }
        return new Shape(s.kind, fx, fy, s.sharp);
    }

    // ---- recognition ----

    /** The clean shape the first {@code n} points make, or null when unsure. {@code zoom}: glass px a page px. */
    static Shape recognize(double[] x, double[] y, int n, double zoom) {
        if (n < 6) return null;
        double x0 = x[0], x1 = x[0], y0 = y[0], y1 = y[0];
        for (int i = 1; i < n; i++) { x0 = Math.min(x0, x[i]); x1 = Math.max(x1, x[i]); y0 = Math.min(y0, y[i]); y1 = Math.max(y1, y[i]); }
        double d = Math.hypot(x1 - x0, y1 - y0), len = length(x, y, n);
        if (d * zoom < SMALLEST || len <= 0) return null;
        double[][] r = resample(x, y, n, N);
        double[] rx = r[0], ry = r[1];
        Shape line = line(rx, ry, len, d);
        if (line != null) return line;
        // Closed: the end within reach of the path's first quarter (a gap, or an overshoot past the start).
        int from = -1;
        double best = CLOSE * d;
        for (int i = 0; i <= N / 4; i++) {
            double g = Math.hypot(rx[i] - rx[N - 1], ry[i] - ry[N - 1]);
            if (g < best) { best = g; from = i; }
        }
        return from >= 0 && len > 1.8 * d ? closed(rx, ry, from, d) : open(rx, ry, d);
    }

    /** A line from the first point to the last, if the path keeps to it. */
    private static Shape line(double[] x, double[] y, double len, double d) {
        int m = x.length - 1;
        double cx = x[m] - x[0], cy = y[m] - y[0], c = Math.hypot(cx, cy);
        if (c < .7 * d || len > LINE_LONGER * c) return null;
        for (int i = 0; i <= m; i++) if (Math.abs((x[i] - x[0]) * cy - (y[i] - y[0]) * cx) / c > LINE_DEV * c) return null;
        return new Shape(Kind.LINE, new double[]{x[0], x[m]}, new double[]{y[0], y[m]}, true);
    }

    /** A closed path, its loop from point {@code from} to the end: a polygon or an oval, starting where the stroke did. */
    private static Shape closed(double[] rx, double[] ry, int from, double d) {
        double[][] loop = resample(Arrays.copyOfRange(rx, from, rx.length), Arrays.copyOfRange(ry, from, ry.length), rx.length - from, N);
        double[] lx = loop[0], ly = loop[1];
        // Once round, no back and forth: a scribble or a figure eight turns far more, or nets less.
        // Turning read over 32 points, coarse enough that a hand's jitter adds nothing.
        double[][] coarse = resample(lx, ly, N, 32);
        double[] turns = turning(coarse[0], coarse[1], true);
        if (Math.abs(turns[0]) < 1.6 * Math.PI || Math.abs(turns[0]) > 2.5 * Math.PI) return null;
        int[] corners = corners(lx, ly, true, d);
        double polyErr = corners == null ? Double.POSITIVE_INFINITY : fitPolygon(lx, ly, corners, true) / d;
        double[] e = ellipse(lx, ly);
        double ovalErr = e == null ? Double.POSITIVE_INFINITY : e[5] / d;
        if (corners != null && corners.length >= 3 && corners.length <= 6 && polyErr < POLY_FIT && polyErr < STRAIGHTER * ovalErr) {
            double[] vx = new double[corners.length], vy = new double[corners.length];
            for (int i = 0; i < corners.length; i++) { vx[i] = lx[corners[i]]; vy[i] = ly[corners[i]]; }
            boolean box = corners.length == 4 && square(vx, vy);
            return startAt(box ? Kind.BOX : Kind.POLYGON, vx, vy, rx[0], ry[0]);
        }
        if (e == null || ovalErr >= ELLIPSE_FIT || turns[1] > 3 * Math.PI) return null;
        return oval(e, rx[0], ry[0], turns[0] > 0 ? 1 : -1);
    }

    /** An open path that is no line: a run of straight lines or an arc. */
    private static Shape open(double[] x, double[] y, double d) {
        int[] corners = corners(x, y, false, d);
        double polyErr = corners == null ? Double.POSITIVE_INFINITY : fitPolygon(x, y, corners, false) / d;
        double[] c = circle(x, y);
        double arcErr = Double.POSITIVE_INFINITY, sweep = 0;
        if (c != null) {
            double sum = 0, abs = 0, last = Math.atan2(y[0] - c[1], x[0] - c[0]);
            for (int i = 1; i < x.length; i++) {
                double a = Math.atan2(y[i] - c[1], x[i] - c[0]), da = wrap(a - last);
                sum += da; abs += Math.abs(da); last = a;
            }
            // One way round only.
            if (abs < 1.15 * Math.abs(sum)) { sweep = sum; arcErr = c[3] / d; }
        }
        if (corners != null && corners.length >= 3 && corners.length <= 5 && polyErr < POLY_FIT && polyErr < STRAIGHTER * arcErr) {
            double[] vx = new double[corners.length], vy = new double[corners.length];
            for (int i = 0; i < corners.length; i++) { vx[i] = x[corners[i]]; vy[i] = y[corners[i]]; }
            return new Shape(Kind.POLYLINE, vx, vy, true);
        }
        if (arcErr >= ARC_FIT || Math.abs(sweep) < ARC_LEAST || Math.abs(sweep) > ARC_MOST || c[2] > 3 * d) return null;
        double a0 = Math.atan2(y[0] - c[1], x[0] - c[0]);
        int m = Math.max(8, (int) Math.ceil(Math.abs(sweep) / Math.toRadians(3)));
        double[] ox = new double[m + 1], oy = new double[m + 1];
        for (int i = 0; i <= m; i++) { double a = a0 + sweep * i / m; ox[i] = c[0] + c[2] * Math.cos(a); oy[i] = c[1] + c[2] * Math.sin(a); }
        return new Shape(Kind.ARC, ox, oy, false);
    }

    /** Total signed turn and total turn either way, radians, along the path (closed: round the join too). */
    private static double[] turning(double[] x, double[] y, boolean closed) {
        int m = closed ? x.length : x.length - 1;
        double sum = 0, abs = 0, last = Double.NaN, first = Double.NaN, least = .25 * length(x, y, x.length) / (x.length - 1);
        for (int i = 0; i < m; i++) {
            int j = (i + 1) % x.length;
            // A join where the end meets the start has no heading of its own.
            if (Math.hypot(x[j] - x[i], y[j] - y[i]) < least) continue;
            double a = Math.atan2(y[j] - y[i], x[j] - x[i]);
            if (Double.isNaN(first)) first = a;
            if (!Double.isNaN(last)) { double da = wrap(a - last); sum += da; abs += Math.abs(da); }
            last = a;
        }
        if (closed && !Double.isNaN(first)) { double da = wrap(first - last); sum += da; abs += Math.abs(da); }
        return new double[]{sum, abs};
    }

    /**
     * The corners (indices, in order) of the path simplified (Douglas and Peucker's split at the farthest point), corners
     * that barely turn dropped; null when a side is too short. Closed: round the loop, its start no corner unless it
     * turns there; an open path keeps its ends.
     */
    private static int[] corners(double[] x, double[] y, boolean closed, double d) {
        int m = x.length;
        List<Integer> v = new ArrayList<>();
        double tol = CORNER_TOL * d;
        if (closed) {
            // Split at the point farthest from the first, simplify each half.
            int far = 0; double fd = -1;
            for (int i = 1; i < m; i++) { double g = Math.hypot(x[i] - x[0], y[i] - y[0]); if (g > fd) { fd = g; far = i; } }
            double[] cx = Arrays.copyOf(x, m + 1), cy = Arrays.copyOf(y, m + 1);
            cx[m] = x[0]; cy[m] = y[0];
            v.add(0); simplify(cx, cy, 0, far, tol, v); v.add(far); simplify(cx, cy, far, m, tol, v);
        } else { v.add(0); simplify(x, y, 0, m - 1, tol, v); v.add(m - 1); }
        // Drop the corner that turns least, again and again, while one turns less than a corner does; then, of two
        // corners a short side apart (a closed path's start beside a corner), the one that turns less.
        while (v.size() > (closed ? 3 : 2)) {
            int worst = -1; double least = CORNER_TURN;
            for (int i = closed ? 0 : 1; i < (closed ? v.size() : v.size() - 1); i++) {
                double turn = turn(x, y, v, i);
                if (turn < least) { least = turn; worst = i; }
            }
            if (worst < 0) {
                for (int i = 0; i < (closed ? v.size() : v.size() - 1) && worst < 0; i++) {
                    int j = (i + 1) % v.size(), a = v.get(i), b = v.get(j);
                    if (Math.hypot(x[b] - x[a], y[b] - y[a]) >= SIDE * d) continue;
                    boolean endI = !closed && i == 0, endJ = !closed && j == v.size() - 1;
                    worst = endI ? j : endJ ? i : turn(x, y, v, i) < turn(x, y, v, j) ? i : j;
                }
                if (worst < 0) break;
            }
            v.remove(worst);
        }
        int[] out = new int[v.size()];
        for (int i = 0; i < out.length; i++) out[i] = v.get(i);
        // Each corner sharp: most of its turn made close by it, where a smooth curve spreads its turn along the way.
        for (int i = closed ? 0 : 1; i < (closed ? out.length : out.length - 1); i++) {
            int a = out[(i - 1 + out.length) % out.length], b = out[i], c = out[(i + 1) % out.length];
            double turn = Math.abs(wrap(Math.atan2(y[c] - y[b], x[c] - x[b]) - Math.atan2(y[b] - y[a], x[b] - x[a])));
            // Round about the corner found, which a hand's wobble may put a point or two off.
            double near = 0;
            for (int o = -SHARP_NEAR / 2; o <= SHARP_NEAR / 2; o++) {
                int at = closed ? (b + o + m) % m : Math.max(1, Math.min(m - 2, b + o));
                int before = closed ? (at - SHARP_NEAR + m) % m : Math.max(0, at - SHARP_NEAR), after = closed ? (at + SHARP_NEAR) % m : Math.min(m - 1, at + SHARP_NEAR);
                near = Math.max(near, Math.abs(wrap(Math.atan2(y[after] - y[at], x[after] - x[at]) - Math.atan2(y[at] - y[before], x[at] - x[before]))));
            }
            if (near < SHARP * turn) return null;
        }
        for (int i = 0; i < (closed ? out.length : out.length - 1); i++) {
            int a = out[i], b = out[(i + 1) % out.length];
            if (Math.hypot(x[b] - x[a], y[b] - y[a]) < SIDE * d) return null;
        }
        return out;
    }
    /** How far corner {@code i} of {@code v} turns, radians. */
    private static double turn(double[] x, double[] y, List<Integer> v, int i) {
        int a = v.get((i - 1 + v.size()) % v.size()), b = v.get(i), c = v.get((i + 1) % v.size());
        return Math.abs(wrap(Math.atan2(y[c] - y[b], x[c] - x[b]) - Math.atan2(y[b] - y[a], x[b] - x[a])));
    }
    private static void simplify(double[] x, double[] y, int a, int b, double tol, List<Integer> out) {
        if (b - a < 2) return;
        double dx = x[b] - x[a], dy = y[b] - y[a], c = Math.hypot(dx, dy), far = -1;
        int k = -1;
        for (int i = a + 1; i < b; i++) {
            double g = c == 0 ? Math.hypot(x[i] - x[a], y[i] - y[a]) : Math.abs((x[i] - x[a]) * dy - (y[i] - y[a]) * dx) / c;
            if (g > far) { far = g; k = i; }
        }
        if (far <= tol) return;
        simplify(x, y, a, k, tol, out); out.add(k); simplify(x, y, k, b, tol, out);
    }
    /** Root mean square distance of the path's points from the straight sides through its corners. */
    private static double fitPolygon(double[] x, double[] y, int[] corners, boolean closed) {
        double sum = 0;
        int sides = closed ? corners.length : corners.length - 1;
        for (int s = 0; s < sides; s++) {
            int a = corners[s], b = closed && s == corners.length - 1 ? corners[0] + x.length : corners[s + 1];
            for (int i = a; i < b; i++) {
                int k = i % x.length, ka = a % x.length, kb = b % x.length;
                double g = segment(x[k], y[k], x[ka], y[ka], x[kb], y[kb]);
                sum += g * g;
            }
        }
        return Math.sqrt(sum / x.length);
    }
    private static double segment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, l = dx * dx + dy * dy, u = l == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / l));
        return Math.hypot(px - ax - u * dx, py - ay - u * dy);
    }
    /** Every corner within {@link #SQUARE} of a right angle. */
    private static boolean square(double[] x, double[] y) {
        for (int i = 0; i < 4; i++) {
            int a = (i + 3) % 4, c = (i + 1) % 4;
            double turn = Math.abs(wrap(Math.atan2(y[c] - y[i], x[c] - x[i]) - Math.atan2(y[i] - y[a], x[i] - x[a])));
            if (Math.abs(turn - Math.PI / 2) > SQUARE) return false;
        }
        // Squared up: the sides' mean slope (four times each angle, averaged), each side at its corners' mean.
        double sx = 0, sy = 0;
        for (int i = 0; i < 4; i++) { double a = 4 * Math.atan2(y[(i + 1) % 4] - y[i], x[(i + 1) % 4] - x[i]); sx += Math.cos(a); sy += Math.sin(a); }
        double th = Math.atan2(sy, sx) / 4, cos = Math.cos(th), sin = Math.sin(th);
        double[] u = new double[4], w = new double[4], level = new double[4];
        boolean[] across = new boolean[4];
        for (int i = 0; i < 4; i++) { u[i] = x[i] * cos + y[i] * sin; w[i] = -x[i] * sin + y[i] * cos; }
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            across[i] = Math.abs(u[j] - u[i]) > Math.abs(w[j] - w[i]);
            level[i] = across[i] ? (w[i] + w[j]) / 2 : (u[i] + u[j]) / 2;
        }
        for (int i = 0; i < 4; i++) if (across[i] == across[(i + 1) % 4]) return false;
        // Corner i is where side i-1 meets side i.
        for (int i = 0; i < 4; i++) {
            int before = (i + 3) % 4;
            double cu = across[i] ? level[before] : level[i], cw = across[i] ? level[i] : level[before];
            x[i] = cu * cos - cw * sin; y[i] = cu * sin + cw * cos;
        }
        return true;
    }
    /** Polygon {@code vx, vy} closed, starting and ending at the point on it nearest ({@code sx}, {@code sy}). */
    private static Shape startAt(Kind kind, double[] vx, double[] vy, double sx, double sy) {
        int k = vx.length, side = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < k; i++) { double g = segment(sx, sy, vx[i], vy[i], vx[(i + 1) % k], vy[(i + 1) % k]); if (g < best) { best = g; side = i; } }
        int j = (side + 1) % k;
        double dx = vx[j] - vx[side], dy = vy[j] - vy[side], l = dx * dx + dy * dy;
        double u = l == 0 ? 0 : Math.max(0, Math.min(1, ((sx - vx[side]) * dx + (sy - vy[side]) * dy) / l));
        double qx = vx[side] + u * dx, qy = vy[side] + u * dy;
        double[] ox = new double[k + 2], oy = new double[k + 2];
        ox[0] = qx; oy[0] = qy;
        for (int i = 1; i <= k; i++) { ox[i] = vx[(side + i) % k]; oy[i] = vy[(side + i) % k]; }
        ox[k + 1] = qx; oy[k + 1] = qy;
        return new Shape(kind, ox, oy, true);
    }

    /**
     * An ellipse fitted to a closed path: {cx, cy, a, b, angle, rms distance}. The centre the points' mean, the axes
     * theirs (principal), the radii by least squares; the distance along each point's ray from the centre. Null if none.
     */
    static double[] ellipse(double[] x, double[] y) {
        int m = x.length;
        double cx = 0, cy = 0;
        for (int i = 0; i < m; i++) { cx += x[i]; cy += y[i]; }
        cx /= m; cy /= m;
        double sxx = 0, sxy = 0, syy = 0;
        for (int i = 0; i < m; i++) { double dx = x[i] - cx, dy = y[i] - cy; sxx += dx * dx; sxy += dx * dy; syy += dy * dy; }
        double th = .5 * Math.atan2(2 * sxy, sxx - syy), cos = Math.cos(th), sin = Math.sin(th);
        double s40 = 0, s22 = 0, s04 = 0, s20 = 0, s02 = 0;
        double[] u = new double[m], v = new double[m];
        for (int i = 0; i < m; i++) {
            double dx = x[i] - cx, dy = y[i] - cy;
            u[i] = dx * cos + dy * sin; v[i] = -dx * sin + dy * cos;
            double u2 = u[i] * u[i], v2 = v[i] * v[i];
            s40 += u2 * u2; s22 += u2 * v2; s04 += v2 * v2; s20 += u2; s02 += v2;
        }
        double det = s40 * s04 - s22 * s22;
        if (det <= 0) return null;
        double A = (s20 * s04 - s02 * s22) / det, B = (s02 * s40 - s20 * s22) / det;
        if (A <= 0 || B <= 0) return null;
        double err = 0;
        for (int i = 0; i < m; i++) {
            double rho = Math.hypot(u[i], v[i]), k = Math.sqrt(A * u[i] * u[i] + B * v[i] * v[i]);
            double g = k == 0 ? 0 : rho * Math.abs(1 - 1 / k);
            err += g * g;
        }
        return new double[]{cx, cy, 1 / Math.sqrt(A), 1 / Math.sqrt(B), th, Math.sqrt(err / m)};
    }
    /** The oval (a circle when round) of fit {@code e}, once round in direction {@code dir}, from the point nearest ({@code sx}, {@code sy}). */
    private static Shape oval(double[] e, double sx, double sy, int dir) {
        double a = e[2], b = e[3], th = e[4];
        boolean round = Math.max(a, b) / Math.min(a, b) < ROUND;
        if (round) a = b = Math.sqrt(a * b);
        double cos = Math.cos(th), sin = Math.sin(th), dx = sx - e[0], dy = sy - e[1];
        double phi = Math.atan2((-dx * sin + dy * cos) / b, (dx * cos + dy * sin) / a);
        int m = 96;
        double[] ox = new double[m + 1], oy = new double[m + 1];
        for (int i = 0; i <= m; i++) {
            double f = phi + dir * 2 * Math.PI * (i % m) / m, u = a * Math.cos(f), v = b * Math.sin(f);
            ox[i] = e[0] + u * cos - v * sin; oy[i] = e[1] + u * sin + v * cos;
        }
        return new Shape(round ? Kind.CIRCLE : Kind.OVAL, ox, oy, false);
    }
    /** A circle fitted to an open path (Kåsa's least squares): {cx, cy, r, rms distance}; null if the points are in a line. */
    static double[] circle(double[] x, double[] y) {
        int m = x.length;
        // Shifted to the mean, for the sums' precision.
        double mx = 0, my = 0;
        for (int i = 0; i < m; i++) { mx += x[i]; my += y[i]; }
        mx /= m; my /= m;
        double suu = 0, suv = 0, svv = 0, suuu = 0, svvv = 0, suvv = 0, svuu = 0;
        for (int i = 0; i < m; i++) {
            double u = x[i] - mx, v = y[i] - my;
            suu += u * u; suv += u * v; svv += v * v; suuu += u * u * u; svvv += v * v * v; suvv += u * v * v; svuu += v * u * u;
        }
        double det = suu * svv - suv * suv;
        if (Math.abs(det) < 1e-9) return null;
        double ru = .5 * (suuu + suvv), rv = .5 * (svvv + svuu);
        double uc = (ru * svv - rv * suv) / det, vc = (rv * suu - ru * suv) / det;
        double r = Math.sqrt(uc * uc + vc * vc + (suu + svv) / m), cx = mx + uc, cy = my + vc, err = 0;
        for (int i = 0; i < m; i++) { double g = Math.hypot(x[i] - cx, y[i] - cy) - r; err += g * g; }
        return new double[]{cx, cy, r, Math.sqrt(err / m)};
    }

    // ---- along the clean path ----

    /**
     * The first {@code n} samples carried onto {@code s}: each to the point the same share of the way along the clean
     * path as it was along the rough one, its pressure, tilt and time kept; a sharp shape's corners added between, each
     * with what the samples either side of it had there. Rows {x, y, pressure, tiltX, tiltY, time}.
     */
    static double[][] along(Shape s, double[] x, double[] y, double[] p, double[] tx, double[] ty, long[] t, int n) {
        double[] rough = new double[n];
        for (int i = 1; i < n; i++) rough[i] = rough[i - 1] + Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]);
        int m = s.x.length;
        double[] clean = new double[m];
        for (int j = 1; j < m; j++) clean[j] = clean[j - 1] + Math.hypot(s.x[j] - s.x[j - 1], s.y[j] - s.y[j - 1]);
        double R = rough[n - 1], C = clean[m - 1];
        List<double[]> out = new ArrayList<>(n + m);
        int j = 1;
        for (int i = 0; i < n; i++) {
            double f = R == 0 ? 0 : rough[i] / R;
            // A sharp shape's corners before this sample, each with what the rough path had at that share.
            while (s.sharp && j < m - 1 && clean[j] / C < f) {
                double g = clean[j] / C * R;
                int k = Math.max(1, i);
                double w = rough[k] == rough[k - 1] ? 0 : (g - rough[k - 1]) / (rough[k] - rough[k - 1]);
                w = Math.max(0, Math.min(1, w));
                out.add(new double[]{s.x[j], s.y[j], lerp(p[k - 1], p[k], w), lerp(tx[k - 1], tx[k], w), lerp(ty[k - 1], ty[k], w),
                        Math.round(lerp(t[k - 1], t[k], w))});
                j++;
            }
            double[] at = at(s, clean, f * C);
            out.add(new double[]{at[0], at[1], p[i], tx[i], ty[i], t[i]});
        }
        return out.toArray(new double[0][]);
    }
    private static double[] at(Shape s, double[] clean, double g) {
        int m = clean.length;
        if (g <= 0) return new double[]{s.x[0], s.y[0]};
        if (g >= clean[m - 1]) return new double[]{s.x[m - 1], s.y[m - 1]};
        int j = Arrays.binarySearch(clean, g);
        if (j >= 0) return new double[]{s.x[j], s.y[j]};
        j = -j - 1;
        double w = (g - clean[j - 1]) / (clean[j] - clean[j - 1]);
        return new double[]{lerp(s.x[j - 1], s.x[j], w), lerp(s.y[j - 1], s.y[j], w)};
    }

    // ---- helpers ----

    static double length(double[] x, double[] y, int n) { double l = 0; for (int i = 1; i < n; i++) l += Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]); return l; }
    /** The first {@code n} points resampled to {@code m}, evenly along the path, both ends kept. */
    static double[][] resample(double[] x, double[] y, int n, int m) {
        double[] cum = new double[n];
        for (int i = 1; i < n; i++) cum[i] = cum[i - 1] + Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]);
        double[] ox = new double[m], oy = new double[m];
        int k = 1;
        for (int i = 0; i < m; i++) {
            double g = cum[n - 1] * i / (m - 1);
            while (k < n - 1 && cum[k] < g) k++;
            double span = cum[k] - cum[k - 1], w = span == 0 ? 0 : Math.max(0, Math.min(1, (g - cum[k - 1]) / span));
            ox[i] = lerp(x[k - 1], x[k], w); oy[i] = lerp(y[k - 1], y[k], w);
        }
        return new double[][]{ox, oy};
    }
    private static double wrap(double a) { while (a > Math.PI) a -= 2 * Math.PI; while (a < -Math.PI) a += 2 * Math.PI; return a; }
    private static double lerp(double a, double b, double w) { return a + (b - a) * w; }
}
