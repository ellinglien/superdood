package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Vector Ink's lines on one layer (docs/superpowers/specs/2026-11-04-vector-ink-design.md): its strokes, each a shade, a
 * width, how its two ends end (round, or cut flat by the eraser) and its points (x, y in eighths of a page pixel, and
 * each one's width, a share of the stroke's), drawn as a variable-width outline at any scale, anti-aliased. Immutable: a change makes a new one. The
 * geometry (smoothing, the outline's coverage, the eraser's cut) and the file's bytes ({@link #encode}) are here. Pure,
 * checked on the desktop ({@code VectorInkChecks}).
 */
final class VectorLines {
    /** Positions are kept in eighths of a page pixel. */
    static final int SUB = 8;
    /**
     * A Vector Ink layer holds at most this many points over its lines, and a stroke at most this many samples; one more
     * layer starts on top when it is full ({@code VectorInk.room}). 50,000 until 2026-11-04 (the owner: "after a long
     * vector ink stroke just now i am unable to make another mark"; "store maybe a fewer points, but yes, increase the
     * limit"): with lines {@link #thinned}, some four times the lines it held.
     */
    static final int MOST_POINTS = 200_000;
    /** Smoothed points are at most this far apart, page px: at 800% a facet is 12 screen pixels and turns by a hair. */
    static final double STEP = 1.5;
    /** The thinnest half-width drawn, page px, so a light hairline still shows. */
    static final double THINNEST = .35;
    /** Points a stroke's run holds ({@link Stroke#runs}): what an outline passes by at once. */
    static final int RUN = 32;
    /** {@link Stroke#caps}' bits: its first end cut flat, its last end cut flat (else round). */
    static final int START_CUT = 1, END_CUT = 2;

    static final VectorLines NONE = new VectorLines(new Stroke[0]);

    /** One line: never changed once made. */
    static final class Stroke {
        /** Its grey, 0 black to 255 white. */
        final int shade;
        /** Its widest, page px ({@link #radius(double, double)}). */
        final float size;
        /** {@link #START_CUT}, {@link #END_CUT}. */
        final int caps;
        /** x, y pairs in eighths of a page pixel. */
        final int[] xy;
        /** Each point's width, a share of {@link #size}, 0 to 255 (the brush's pressure to size made it: {@code VectorInk.widest}). */
        final byte[] share;
        private double[] box;
        Stroke(int shade, float size, int caps, int[] xy, byte[] share) {
            this.shade = shade; this.size = size; this.caps = caps; this.xy = xy; this.share = share;
        }
        int points() { return share.length; }
        double x(int i) { return xy[2 * i] / (double) SUB; }
        double y(int i) { return xy[2 * i + 1] / (double) SUB; }
        double p(int i) { return (share[i] & 255) / 255.0; }
        /** Half its width at point {@code i}, page px. */
        double radius(int i) { return VectorLines.radius(size, p(i)); }
        /** {left, top, right, bottom} in page px, its width included. */
        private double[] runs;
        /** Each {@link #RUN} points' box (from a run's first point to the next run's first), page px, widths included: {l, t, r, b} a run. */
        double[] runs() {
            if (runs != null) return runs;
            int n = points(), count = (n + RUN - 1) / RUN;
            double[] out = new double[4 * Math.max(1, count)];
            for (int k = 0; k < count; k++) {
                double l = Double.MAX_VALUE, t = Double.MAX_VALUE, r = -Double.MAX_VALUE, b = -Double.MAX_VALUE;
                for (int i = k * RUN; i <= Math.min(n - 1, (k + 1) * RUN); i++) {
                    double rad = radius(i);
                    l = Math.min(l, x(i) - rad); t = Math.min(t, y(i) - rad); r = Math.max(r, x(i) + rad); b = Math.max(b, y(i) + rad);
                }
                out[4 * k] = l; out[4 * k + 1] = t; out[4 * k + 2] = r; out[4 * k + 3] = b;
            }
            return runs = out;
        }
        double[] bounds() {
            if (box != null) return box;
            double l = Double.MAX_VALUE, t = Double.MAX_VALUE, r = -Double.MAX_VALUE, b = -Double.MAX_VALUE;
            for (int i = 0; i < points(); i++) {
                double rad = radius(i);
                l = Math.min(l, x(i) - rad); t = Math.min(t, y(i) - rad); r = Math.max(r, x(i) + rad); b = Math.max(b, y(i) + rad);
            }
            return box = new double[]{l, t, r, b};
        }
    }

    private final Stroke[] strokes;
    private final int points;
    private VectorLines(Stroke[] strokes) {
        this.strokes = strokes;
        int n = 0;
        for (Stroke s : strokes) n += s.points();
        points = n;
    }
    int size() { return strokes.length; }
    Stroke get(int i) { return strokes[i]; }
    boolean isEmpty() { return strokes.length == 0; }
    /** Its points, against {@link #MOST_POINTS}. */
    int points() { return points; }
    /** These lines and {@code stroke} over them. */
    VectorLines with(Stroke stroke) {
        Stroke[] next = java.util.Arrays.copyOf(strokes, strokes.length + 1);
        next[strokes.length] = stroke;
        return new VectorLines(next);
    }
    /** As {@link #with(Stroke)} for a stroke still being drawn (a new one each frame): {@link #alone} draws it over this one's kept tiles, nothing of its own kept. */
    VectorLines withLive(Stroke stroke) { VectorLines out = with(stroke); out.base = this; return out; }
    /** What {@link #withLive} built this on, or null. */
    private VectorLines base;
    /**
     * As {@link #with(Stroke)} for a finished stroke: its tiles begin as this one's kept tiles with the stroke drawn over,
     * so a lift draws the one stroke, not every line under it again (a size 250 lift took ~600 ms on the Manta, 1.4 s zoomed).
     */
    VectorLines withKept(Stroke stroke) {
        VectorLines out = with(stroke);
        synchronized (tiles) { out.seed = new java.util.HashMap<>(tiles); out.seedFrom = strokes.length; out.tileScale = tileScale; out.tileOx = tileOx; out.tileOy = tileOy; }
        return out;
    }
    /** Tiles of the lines this was made from by {@link #withKept}, strokes {@code seedFrom} on not yet in them; null once the zoom changes. */
    private java.util.Map<Long, byte[][]> seed;
    private int seedFrom;
    /** Half the width of a line of {@code size} at {@code share} of it (0 to 1), page px; a hairline at least. */
    static double radius(double size, double share) { return Math.max(THINNEST, size / 2 * Math.max(0, Math.min(1, share))); }

    // ---- smoothing ----

    /**
     * A pen's samples ({@code xs}, {@code ys} page px, {@code ps} each one's width, a share of {@code size}, 0 to 1, {@code n} of them) as a line: points
     * closer than a quarter pixel dropped, the pen's jitter softened (each inner point moved toward its neighbours, a
     * [1 2 1] pass, twice), then a centripetal Catmull-Rom curve through them, a point every {@link #STEP} px at most
     * ({@link #curve}), then the points its straight runs don't need left out ({@link #thinned}). The first and last
     * samples stay where they are. Null for none.
     */
    static Stroke smoothed(int shade, float size, float[] xs, float[] ys, float[] ps, int n) {
        Stroke dense = curve(shade, size, xs, ys, ps, n);
        return dense == null ? null : thinned(dense);
    }
    /** {@link #smoothed}'s curve before it is {@link #thinned}: a point every {@link #STEP} px at most. */
    static Stroke curve(int shade, float size, float[] xs, float[] ys, float[] ps, int n) {
        if (n <= 0) return null;
        double[] x = new double[n], y = new double[n], p = new double[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (!Float.isFinite(xs[i]) || !Float.isFinite(ys[i])) continue;
            boolean last = i == n - 1;
            if (m > 0 && Math.hypot(xs[i] - x[m - 1], ys[i] - y[m - 1]) < .25) { if (last && m > 1) { x[m - 1] = xs[i]; y[m - 1] = ys[i]; p[m - 1] = ps[i]; } continue; }
            x[m] = xs[i]; y[m] = ys[i]; p[m] = Float.isFinite(ps[i]) ? ps[i] : 0; m++;
        }
        if (m == 0) return null;
        for (int pass = 0; pass < 2; pass++) { soften(x, m); soften(y, m); }
        soften(p, m);
        // The curve: from each point to the next, as many steps as STEP asks.
        int[] xy = new int[64]; byte[] pr = new byte[32]; int k = 0;
        for (int i = 0; i < m; i++) {
            int steps = i == m - 1 ? 1 : Math.max(1, (int) Math.ceil(Math.hypot(x[i + 1] - x[i], y[i + 1] - y[i]) / STEP));
            for (int s = 0; s < steps; s++) {
                double t = s / (double) steps, px, py;
                if (s == 0) { px = x[i]; py = y[i]; }
                else {
                    double[] c = catmullRom(x, y, m, i, t);
                    px = c[0]; py = c[1];
                }
                if (k == pr.length) { pr = java.util.Arrays.copyOf(pr, 2 * k); xy = java.util.Arrays.copyOf(xy, 4 * k); }
                xy[2 * k] = (int) Math.round(px * SUB); xy[2 * k + 1] = (int) Math.round(py * SUB);
                pr[k] = (byte) Math.round(255 * Math.max(0, Math.min(1, i == m - 1 ? p[i] : p[i] + (p[i + 1] - p[i]) * t)));
                if (k > 0 && xy[2 * k] == xy[2 * k - 2] && xy[2 * k + 1] == xy[2 * k - 1]) continue;
                k++;
            }
        }
        return new Stroke(shade, size, 0, java.util.Arrays.copyOf(xy, 2 * k), java.util.Arrays.copyOf(pr, k));
    }
    /** How far a thinned line's points may stray from the curve, page px: a third of a screen pixel at 800%. */
    static final double STRAY = .04;
    /** How far a thinned point's width may stray, a share of the size (2 in 255); and its points are at most {@link #LONGEST} px apart. */
    private static final int STRAY_SHARE = 2;
    static final double LONGEST = 12;
    /**
     * {@code s} with the points a straight run doesn't need left out (the owner, 2026-11-04: "store maybe a fewer points,
     * but yes, increase the limit"): each kept segment within {@link #STRAY} px and {@link #STRAY_SHARE} of the width
     * of every point it skips (Douglas–Peucker), none longer than {@link #LONGEST}; its ends and curves as they were.
     */
    static Stroke thinned(Stroke s) {
        int n = s.points();
        if (n < 3) return s;
        boolean[] keep = new boolean[n];
        keep[0] = keep[n - 1] = true;
        int[] stack = new int[2 * n]; int top = 0;
        stack[top++] = 0; stack[top++] = n - 1;
        while (top > 0) {
            int b = stack[--top], a = stack[--top];
            if (b - a < 2) continue;
            double ax = s.x(a), ay = s.y(a), dx = s.x(b) - ax, dy = s.y(b) - ay, len = Math.hypot(dx, dy);
            // The point that strays most, in units of what it may stray; a run past LONGEST splits at its middle anyway.
            int far = -1; double most = 1;
            for (int i = a + 1; i < b; i++) {
                double px = s.x(i) - ax, py = s.y(i) - ay, t = len < 1e-9 ? 0 : (px * dx + py * dy) / (len * len);
                double d = t <= 0 || len < 1e-9 ? Math.hypot(px, py) : t >= 1 ? Math.hypot(s.x(i) - s.x(b), s.y(i) - s.y(b)) : Math.abs(px * dy - py * dx) / len;
                double share = (s.share[a] & 255) + ((s.share[b] & 255) - (s.share[a] & 255)) * Math.max(0, Math.min(1, t));
                double stray = Math.max(d / STRAY, Math.abs((s.share[i] & 255) - share) / STRAY_SHARE);
                if (stray > most) { most = stray; far = i; }
            }
            if (far < 0 && len > LONGEST) far = (a + b) / 2;
            if (far < 0) continue;
            keep[far] = true;
            stack[top++] = a; stack[top++] = far; stack[top++] = far; stack[top++] = b;
        }
        int m = 0;
        for (boolean k : keep) if (k) m++;
        if (m == n) return s;
        int[] xy = new int[2 * m]; byte[] share = new byte[m];
        for (int i = 0, j = 0; i < n; i++) if (keep[i]) { xy[2 * j] = s.xy[2 * i]; xy[2 * j + 1] = s.xy[2 * i + 1]; share[j++] = s.share[i]; }
        return new Stroke(s.shade, s.size, s.caps, xy, share);
    }
    /** One [1 2 1] pass over {@code v}'s inner values; the ends stay. */
    private static void soften(double[] v, int n) {
        if (n < 3) return;
        double before = v[0];
        for (int i = 1; i < n - 1; i++) { double was = v[i]; v[i] = (before + 2 * was + v[i + 1]) / 4; before = was; }
    }
    /** The centripetal Catmull-Rom curve (Barry and Goldman's) from point {@code i} to {@code i + 1} at {@code t}, the ends' outer neighbours mirrored. */
    private static double[] catmullRom(double[] x, double[] y, int n, int i, double t) {
        double x1 = x[i], y1 = y[i], x2 = x[i + 1], y2 = y[i + 1];
        double x0 = i > 0 ? x[i - 1] : 2 * x1 - x2, y0 = i > 0 ? y[i - 1] : 2 * y1 - y2;
        double x3 = i + 2 < n ? x[i + 2] : 2 * x2 - x1, y3 = i + 2 < n ? y[i + 2] : 2 * y2 - y1;
        double t0 = 0, t1 = t0 + knot(x0, y0, x1, y1), t2 = t1 + knot(x1, y1, x2, y2), t3 = t2 + knot(x2, y2, x3, y3);
        double u = t1 + (t2 - t1) * t;
        double ax = lerp(x0, x1, t0, t1, u), ay = lerp(y0, y1, t0, t1, u);
        double bx = lerp(x1, x2, t1, t2, u), by = lerp(y1, y2, t1, t2, u);
        double cx = lerp(x2, x3, t2, t3, u), cy = lerp(y2, y3, t2, t3, u);
        double dx = lerp(ax, bx, t0, t2, u), dy = lerp(ay, by, t0, t2, u);
        double ex = lerp(bx, cx, t1, t3, u), ey = lerp(by, cy, t1, t3, u);
        return new double[]{lerp(dx, ex, t1, t2, u), lerp(dy, ey, t1, t2, u)};
    }
    private static double knot(double ax, double ay, double bx, double by) { return Math.max(1e-6, Math.sqrt(Math.hypot(bx - ax, by - ay))); }
    private static double lerp(double a, double b, double ta, double tb, double u) { return (tb - u) / (tb - ta) * a + (u - ta) / (tb - ta) * b; }

    /** A pen's samples as they came, unsmoothed (the line while the pen is still down). */
    static Stroke rough(int shade, float size, float[] xs, float[] ys, float[] ps, int n) {
        int[] xy = new int[2 * n]; byte[] pr = new byte[n];
        for (int i = 0; i < n; i++) { xy[2 * i] = (int) Math.round(xs[i] * SUB); xy[2 * i + 1] = (int) Math.round(ys[i] * SUB); pr[i] = (byte) Math.round(255 * Math.max(0, Math.min(1, ps[i]))); }
        return new Stroke(shade, size, 0, xy, pr);
    }

    // ---- the outline ----

    /**
     * {@code stroke}'s bounds where page point (x, y) lands at ({@code x * scale + ox}, {@code y * scale + oy}): {left,
     * top, right, bottom} target pixels, right and bottom exclusive.
     */
    static int[] bounds(Stroke stroke, double scale, double ox, double oy) {
        double[] b = stroke.bounds();
        return new int[]{(int) Math.floor(b[0] * scale + ox) - 1, (int) Math.floor(b[1] * scale + oy) - 1, (int) Math.ceil(b[2] * scale + ox) + 1, (int) Math.ceil(b[3] * scale + oy) + 1};
    }
    /** As {@link #bounds(Stroke, double, double, double)}, of points {@code from} to {@code to} (inclusive) alone. */
    static int[] bounds(Stroke stroke, int from, int to, double scale, double ox, double oy) {
        double l = Double.MAX_VALUE, t = Double.MAX_VALUE, r = -Double.MAX_VALUE, b = -Double.MAX_VALUE;
        for (int i = from; i <= to; i++) {
            double rad = stroke.radius(i);
            l = Math.min(l, stroke.x(i) - rad); t = Math.min(t, stroke.y(i) - rad); r = Math.max(r, stroke.x(i) + rad); b = Math.max(b, stroke.y(i) + rad);
        }
        return new int[]{(int) Math.floor(l * scale + ox) - 1, (int) Math.floor(t * scale + oy) - 1, (int) Math.ceil(r * scale + ox) + 1, (int) Math.ceil(b * scale + oy) + 1};
    }
    /**
     * {@code stroke}'s points {@code from} to {@code to} (inclusive) as a filled outline, each target pixel's share of it (0
     * to 255) kept where more than {@code out} holds: the {@code w} by {@code h} target pixels at {@code left, top}, row by
     * row. A target pixel's centre is page point ((X + ½ − ox) / scale, (Y + ½ − oy) / scale). Its width follows each
     * point's share; a cut end ({@link #START_CUT}, {@link #END_CUT}) is flat across the line, a round one round.
     */
    static void cover(Stroke stroke, int from, int to, double scale, double ox, double oy, int left, int top, int w, int h, byte[] out) {
        int n = stroke.points();
        if (n == 0 || from > to) return;
        if (from == to) { disc(stroke.x(from) * scale + ox, stroke.y(from) * scale + oy, stroke.radius(from) * scale, left, top, w, h, out); }
        // A run of points at a time, passed by when its box misses the area (a long line through a small tile).
        double[] runs = stroke.runs(), span = new double[2], whole = new double[2];
        double l = (left - ox) / scale, t = (top - oy) / scale, r0 = (left + w - ox) / scale, b0 = (top + h - oy) / scale, pad = 2 / scale;
        for (int i = from, checked = -1; i < to; ) {
            int run = i / RUN;
            if (run != checked) {
                checked = run;
                double[] box = {runs[4 * run], runs[4 * run + 1], runs[4 * run + 2], runs[4 * run + 3]};
                if (box[0] > r0 + pad || box[2] < l - pad || box[1] > b0 + pad || box[3] < t - pad) { i = Math.min(to, (run + 1) * RUN); continue; }
            }
            int j = reach(stroke, i, to, scale);
            double ax = stroke.x(i) * scale + ox, ay = stroke.y(i) * scale + oy, bx = stroke.x(j) * scale + ox, by = stroke.y(j) * scale + oy;
            double ra = stroke.radius(i) * scale, rb = stroke.radius(j) * scale, r = Math.max(ra, rb);
            i = j;
            int x0 = Math.max(left, (int) Math.floor(Math.min(ax, bx) - r - 1)), x1 = Math.min(left + w, (int) Math.ceil(Math.max(ax, bx) + r + 1));
            int y0 = Math.max(top, (int) Math.floor(Math.min(ay, by) - r - 1)), y1 = Math.min(top + h, (int) Math.ceil(Math.max(ay, by) + r + 1));
            double dx = bx - ax, dy = by - ay, len = Math.hypot(dx, dy);
            if (len < 1e-9) { disc(ax, ay, r, left, top, w, h, out); continue; }
            double ux = dx / len, uy = dy / len;
            for (int y = y0; y < y1; y++) {
                double py = y + .5 - ay;
                // The row's reach: its x span of the two end discs and the band between, past which nothing covers (a large line's box is mostly corners).
                if (!span(y + .5, ax, ay, bx, by, r + 1, ux, uy, len, span)) continue;
                int sx0 = Math.max(x0, (int) Math.floor(span[0]) - 1), sx1 = Math.min(x1, (int) Math.ceil(span[1]) + 1), row = (y - top) * w - left;
                // Its inside, a pixel or more within the thinner end's edge, whole: filled at once, the edge's pixels alone worked out.
                int fx0 = sx1, fx1 = sx1;
                if (Math.min(ra, rb) > 2 && span(y + .5, ax, ay, bx, by, Math.min(ra, rb) - 1, ux, uy, len, whole)) {
                    fx0 = Math.max(sx0, (int) Math.ceil(whole[0] - .5)); fx1 = Math.min(sx1, (int) Math.floor(whole[1] - .5) + 1);
                    if (fx0 < fx1) java.util.Arrays.fill(out, row + fx0, row + fx1, (byte) 255); else fx0 = fx1 = sx1;
                }
                for (int x = sx0, o = row + sx0; x < sx1; x++, o++) {
                    if (x == fx0) { x = fx1 - 1; o = row + x; continue; }
                    // Already whole (laid by the piece before): nothing to add.
                    if (out[o] == (byte) 255) continue;
                    double px = x + .5 - ax, along = px * ux + py * uy, across = Math.abs(px * uy - py * ux), d, rad;
                    if (along < 0) { d = Math.sqrt(px * px + py * py); rad = ra; }
                    else if (along > len) { double qx = x + .5 - bx, qy = y + .5 - by; d = Math.sqrt(qx * qx + qy * qy); rad = rb; }
                    else { d = across; rad = ra + (rb - ra) * along / len; }
                    keep(out, o, rad - d + .5);
                }
            }
        }
        // A cut end: what lies past it, near it, goes, the line ending flat across its way.
        if (from == 0 && (stroke.caps & START_CUT) != 0 && n > 1) flat(stroke, 0, 1, scale, ox, oy, left, top, w, h, out);
        if (to == n - 1 && (stroke.caps & END_CUT) != 0 && n > 1) flat(stroke, n - 1, n - 2, scale, ox, oy, left, top, w, h, out);
    }
    /** Row {@code cy}'s x span ({from, to}, target px; null: none) of the capsule from (ax, ay) to (bx, by) of radius {@code r}: the end discs' spans and the band's between. */
    private static boolean span(double cy, double ax, double ay, double bx, double by, double r, double ux, double uy, double len, double[] into) {
        // Nothing allocated: it runs for every row of every piece (a lift's arrays here were most of its time on the Manta).
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE, da = cy - ay, db = cy - by;
        if (Math.abs(da) < r) { double h = Math.sqrt(r * r - da * da); lo = ax - h; hi = ax + h; }
        if (Math.abs(db) < r) { double h = Math.sqrt(r * r - db * db); lo = Math.min(lo, bx - h); hi = Math.max(hi, bx + h); }
        // The band: points whose distance across is under r and along is within the piece; on this row, x where |across| < r.
        if (Math.abs(uy) > 1e-9) {
            // across = (x - ax) * uy - (cy - ay) * ux, so |across| < r is an x interval; along within [0, len] another.
            double k = (cy - ay) * ux, a1 = (k - r) / uy + ax, a2 = (k + r) / uy + ax;
            double p = (cy - ay) * uy, b1 = (0 - p) / ux, b2 = (len - p) / ux;
            double xlo = Math.min(a1, a2), xhi = Math.max(a1, a2);
            if (Math.abs(ux) > 1e-9) { double blo = Math.min(b1, b2) + ax, bhi = Math.max(b1, b2) + ax; xlo = Math.max(xlo, blo); xhi = Math.min(xhi, bhi); }
            else if (p < 0 || p > len) xhi = xlo - 1;
            if (xlo < xhi) { lo = Math.min(lo, xlo); hi = Math.max(hi, xhi); }
        } else if (Math.abs(cy - ay) < r) { lo = Math.min(lo, Math.min(ax, bx)); hi = Math.max(hi, Math.max(ax, bx)); }
        if (lo > hi) return false;
        into[0] = lo; into[1] = hi;
        return true;
    }
    /**
     * The farthest point past {@code i} (up to {@code to}, within its run, so every tile merges alike) that {@link #cover} draws {@code i}
     * to as one piece: each point between within {@link #TOL} target px of the straight piece, its width within TOL of the
     * blend. A piece fills its whole box, so a large line's close points drew each pixel hundreds of times (the owner,
     * 2026-11-07: "vector ink is unusable past a certain size").
     */
    private static int reach(Stroke s, int i, int to, double scale) {
        int best = i + 1;
        // At 100% (the page's own pixels) every point its own piece: an eraser's cut renumbers a line's pieces, and its tiles laid again must match the rest exactly.
        if (scale == 1) return best;
        double ax = s.x(i), ay = s.y(i), ar = s.radius(i), tol = TOL / scale;
        for (int b = i + 2; b <= Math.min(to, (i / RUN + 1) * RUN); b++) {
            double dx = s.x(b) - ax, dy = s.y(b) - ay, len = Math.hypot(dx, dy), br = s.radius(b);
            if (len < 1e-9) return best;
            for (int m = i + 1; m < b; m++) {
                double px = s.x(m) - ax, py = s.y(m) - ay, along = (px * dx + py * dy) / len;
                if (along < 0 || along > len || Math.abs(px * dy - py * dx) / len > tol || Math.abs(s.radius(m) - (ar + (br - ar) * along / len)) > tol) return best;
            }
            best = b;
        }
        return best;
    }
    /** Target px a merged piece may stray from the points it stands for. */
    private static final double TOL = .25;
    /** The whole of {@code stroke}, as {@link #cover(Stroke, int, int, double, double, double, int, int, int, int, byte[])}. */
    static void cover(Stroke stroke, double scale, double ox, double oy, int left, int top, int w, int h, byte[] out) {
        cover(stroke, 0, stroke.points() - 1, scale, ox, oy, left, top, w, h, out);
    }
    private static void disc(double cx, double cy, double r, int left, int top, int w, int h, byte[] out) {
        int x0 = Math.max(left, (int) Math.floor(cx - r - 1)), x1 = Math.min(left + w, (int) Math.ceil(cx + r + 1));
        int y0 = Math.max(top, (int) Math.floor(cy - r - 1)), y1 = Math.min(top + h, (int) Math.ceil(cy + r + 1));
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) keep(out, (y - top) * w + x - left, r - Math.hypot(x + .5 - cx, y + .5 - cy) + .5);
    }
    private static void keep(byte[] out, int i, double share) {
        if (share <= 0) return;
        int c = share >= 1 ? 255 : (int) Math.round(share * 255);
        if (c > (out[i] & 255)) out[i] = (byte) c;
    }
    /**
     * End {@code end} cut flat: near it (within its width and a pixel), each target pixel keeps only its share on the
     * line's side of the cut, the cut square to the way from {@code end} toward the first point at least half a pixel off.
     */
    private static void flat(Stroke stroke, int end, int toward, double scale, double ox, double oy, int left, int top, int w, int h, byte[] out) {
        double ex = stroke.x(end) * scale + ox, ey = stroke.y(end) * scale + oy;
        double[] way = way(stroke, end, toward);
        if (way == null) return;
        double ux = way[0], uy = way[1], reach = stroke.radius(end) * scale + 1.5;
        int x0 = Math.max(left, (int) Math.floor(ex - reach)), x1 = Math.min(left + w, (int) Math.ceil(ex + reach));
        int y0 = Math.max(top, (int) Math.floor(ey - reach)), y1 = Math.min(top + h, (int) Math.ceil(ey + reach));
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            double px = x + .5 - ex, py = y + .5 - ey;
            if (Math.hypot(px, py) > reach) continue;
            // Inward along the line: kept; behind the cut: gone, half a pixel to fade.
            double keep = Math.max(0, Math.min(1, .5 + px * ux + py * uy));
            int i = (y - top) * w + x - left;
            out[i] = (byte) Math.round((out[i] & 255) * keep);
        }
    }
    /** The way from end {@code end} into its line, a unit vector toward the first point from {@code toward} on at least half a pixel off it; null when none is off. */
    private static double[] way(Stroke stroke, int end, int toward) {
        int step = toward > end ? 1 : -1, k = toward;
        while (k + step >= 0 && k + step < stroke.points() && Math.hypot(stroke.x(k) - stroke.x(end), stroke.y(k) - stroke.y(end)) < .5) k += step;
        double dx = stroke.x(k) - stroke.x(end), dy = stroke.y(k) - stroke.y(end), len = Math.hypot(dx, dy);
        return len < 1e-9 ? null : new double[]{dx / len, dy / len};
    }

    // ---- as vectors ----

    /** The most a stroke's half width changes along one piece of its PDF line ({@link #pdf}), page px: its edge stays within half of it of the outline's. */
    static final double PDF_TAPER = .5;
    /**
     * These lines as PDF path operators, page px with y down (the caller maps them onto its page), each stroke in turn in
     * its grey ({@code grey}: a shade's PDF grey, 0 to 255): the outline {@link #cover} fills, as round-capped, round-joined
     * lines through its points, each piece as wide as the outline is there (a segment cut in pieces where its width
     * changes by more than {@link #PDF_TAPER}, a width a quarter pixel at most off), a run of one width one line; a cut end
     * ({@link #START_CUT}, {@link #END_CUT}) clipped flat as {@link #flat} cuts it, within its width and a pixel and a half.
     */
    void pdf(StringBuilder out, java.util.function.IntUnaryOperator grey) {
        out.append("1 J 1 j\n");
        for (Stroke s : strokes) {
            int n = s.points();
            if (n == 0) continue;
            num(out, grey.applyAsInt(s.shade) / 255.0).append(" G\n");
            boolean cutStart = n > 1 && (s.caps & START_CUT) != 0, cutEnd = n > 1 && (s.caps & END_CUT) != 0;
            if (cutStart || cutEnd) out.append("q\n");
            if (cutStart) clipFlat(out, s, 0, 1);
            if (cutEnd) clipFlat(out, s, n - 1, n - 2);
            double width = -1;
            if (n == 1) {
                // A dot: a round cap on a line of no length.
                num(out, wide(s.radius(0))).append(" w ");
                point(out, s.x(0), s.y(0)).append(" m ");
                point(out, s.x(0), s.y(0)).append(" l\n");
            }
            for (int i = 0; i + 1 < n; i++) {
                double ax = s.x(i), ay = s.y(i), bx = s.x(i + 1), by = s.y(i + 1), ra = s.radius(i), rb = s.radius(i + 1);
                int pieces = Math.max(1, (int) Math.ceil(Math.abs(rb - ra) / PDF_TAPER));
                for (int k = 0; k < pieces; k++) {
                    double t0 = k / (double) pieces, t1 = (k + 1) / (double) pieces, w = wide(ra + (rb - ra) * (t0 + t1) / 2);
                    if (w != width) {
                        if (width >= 0) out.append("S\n");
                        num(out, w).append(" w ");
                        point(out, ax + (bx - ax) * t0, ay + (by - ay) * t0).append(" m\n");
                        width = w;
                    }
                    point(out, ax + (bx - ax) * t1, ay + (by - ay) * t1).append(" l\n");
                }
            }
            out.append("S\n");
            if (cutStart || cutEnd) out.append("Q\n");
        }
    }
    /** A half width {@code r} as a PDF line's width, to a quarter pixel. */
    private static double wide(double r) { return Math.round(8 * r) / 4.0; }
    /**
     * End {@code end} cut flat ({@link #flat}) as a PDF clip: everything but the half disc behind the cut, of its half
     * width and a pixel and a half (even-odd: a box round it all, then the half disc).
     */
    private static void clipFlat(StringBuilder out, Stroke s, int end, int toward) {
        double[] u = way(s, end, toward);
        if (u == null) return;
        double ex = s.x(end), ey = s.y(end), r = s.radius(end) + 1.5, nx = -u[1], ny = u[0], k = .5523 * r;
        double[] b = s.bounds();
        double l = Math.min(b[0], ex - r) - 1, t = Math.min(b[1], ey - r) - 1;
        point(out, l, t).append(' ');
        point(out, Math.max(b[2], ex + r) + 1 - l, Math.max(b[3], ey + r) + 1 - t).append(" re\n");
        // From the cut's one side round behind it to the other (two quarter circles), back along the cut.
        double ax = ex + r * nx, ay = ey + r * ny, bx = ex - r * u[0], by = ey - r * u[1], cx = ex - r * nx, cy = ey - r * ny;
        point(out, ax, ay).append(" m ");
        point(out, ax - k * u[0], ay - k * u[1]).append(' '); point(out, bx + k * nx, by + k * ny).append(' '); point(out, bx, by).append(" c ");
        point(out, bx - k * nx, by - k * ny).append(' '); point(out, cx - k * u[0], cy - k * u[1]).append(' '); point(out, cx, cy).append(" c h W* n\n");
    }
    private static StringBuilder point(StringBuilder out, double x, double y) { return num(num(out, x).append(' '), y); }
    /** {@code v} to three places, no trailing zeros: as short as a PDF number gets. */
    static StringBuilder num(StringBuilder out, double v) {
        long m = Math.round(v * 1000);
        if (m < 0) { out.append('-'); m = -m; }
        out.append(m / 1000);
        int f = (int) (m % 1000);
        if (f == 0) return out;
        out.append('.');
        if (f < 100) out.append('0');
        if (f < 10) out.append('0');
        while (f % 10 == 0) f /= 10;
        return out.append(f);
    }
    /**
     * These lines over {@code greys} (the {@code w} by {@code h} target pixels at {@code left, top}, each a grey 0 to 255 in
     * its low byte), each stroke in turn, page point (x, y) at ({@code x * scale + ox}, {@code y * scale + oy}): the
     * zoomed view's sharp lines and a bigger copy's. {@code coverage} is scratch, at least {@code w * h}, any content.
     */
    void over(double scale, double ox, double oy, int left, int top, int w, int h, int[] greys, byte[] coverage) { over(scale, ox, oy, left, top, w, h, greys, null, coverage); }
    /** As above, each pixel's coverage laid over {@code covered} too (null: not kept): over {@code greys} of 0, the lines' marks alone (High Res's Layers copy). */
    void over(double scale, double ox, double oy, int left, int top, int w, int h, int[] greys, int[] covered, byte[] coverage) { over(0, scale, ox, oy, left, top, w, h, greys, covered, coverage); }
    /** As above, strokes {@code first} on alone. */
    private void over(int first, double scale, double ox, double oy, int left, int top, int w, int h, int[] greys, int[] covered, byte[] coverage) {
        for (int k = first; k < strokes.length; k++) {
            Stroke s = strokes[k];
            int[] b = bounds(s, scale, ox, oy);
            int x0 = Math.max(left, b[0]), y0 = Math.max(top, b[1]), x1 = Math.min(left + w, b[2]), y1 = Math.min(top + h, b[3]);
            if (x0 >= x1 || y0 >= y1) continue;
            for (int y = y0; y < y1; y++) java.util.Arrays.fill(coverage, (y - top) * w + x0 - left, (y - top) * w + x1 - left, (byte) 0);
            cover(s, scale, ox, oy, left, top, w, h, coverage);
            for (int y = y0; y < y1; y++) for (int x = x0, i = (y - top) * w + x0 - left; x < x1; x++, i++) {
                int c = coverage[i] & 255;
                if (c == 0) continue;
                int g = greys[i] & 255;
                greys[i] = greys[i] & ~255 | (g * (255 - c) + s.shade * c + 127) / 255;
                if (covered != null) covered[i] = c + (covered[i] * (255 - c) + 127) / 255;
            }
        }
    }
    /**
     * As {@link #over(double, double, double, int, int, int, int, int[], int[], byte[])} over {@code greys} and {@code
     * covered} of 0 (the lines alone: premultiplied grey and coverage), from tiles kept for this zoom and offset: drying
     * paint redraws the zoomed view every frame, and each redraw drew every line again (the owner, 2026-11-07: "everything's
     * deathly slow", large Vector Ink lines on the page). These lines never change (a change is a new VectorLines).
     */
    void alone(double scale, double ox, double oy, int left, int top, int w, int h, int[] greys, int[] covered) {
        if (base != null) { base.alone(scale, ox, oy, left, top, w, h, greys, covered); over(base.strokes.length, scale, ox, oy, left, top, w, h, greys, covered, new byte[w * h]); return; }
        synchronized (tiles) {
            if (scale != tileScale || ox != tileOx || oy != tileOy) { tiles.clear(); seed = null; tileScale = scale; tileOx = ox; tileOy = oy; }
            for (int ty = Math.floorDiv(top, TILE); ty * TILE < top + h; ty++) for (int tx = Math.floorDiv(left, TILE); tx * TILE < left + w; tx++) {
                byte[][] tile = tile(tx, ty, scale, ox, oy);
                if (tile == null) continue;
                int x0 = Math.max(left, tx * TILE), x1 = Math.min(left + w, (tx + 1) * TILE), y0 = Math.max(top, ty * TILE), y1 = Math.min(top + h, (ty + 1) * TILE);
                for (int y = y0; y < y1; y++) for (int x = x0, i = (y - top) * w + x0 - left, j = (y - ty * TILE) * TILE + x0 - tx * TILE; x < x1; x++, i++, j++) {
                    greys[i] = tile[0][j] & 255; covered[i] = tile[1][j] & 255;
                }
            }
        }
    }
    /** Tile ({@code tx}, {@code ty})'s lines alone, {grey, coverage} a byte a pixel, or null where no line meets it. */
    private byte[][] tile(int tx, int ty, double scale, double ox, double oy) {
        long key = (long) tx << 32 | (ty & 0xffffffffL);
        if (tiles.containsKey(key)) return tiles.get(key);
        int n = TILE * TILE;
        int[] g = new int[n], c = new int[n];
        if (seed != null && seed.containsKey(key)) {
            byte[][] was = seed.remove(key);
            if (was != null) for (int i = 0; i < n; i++) { g[i] = was[0][i] & 255; c[i] = was[1][i] & 255; }
            over(seedFrom, scale, ox, oy, tx * TILE, ty * TILE, TILE, TILE, g, c, new byte[n]);
        } else over(scale, ox, oy, tx * TILE, ty * TILE, TILE, TILE, g, c, new byte[n]);
        byte[][] tile = null;
        for (int i = 0; i < n; i++) if (c[i] != 0) { tile = new byte[2][n]; break; }
        if (tile != null) for (int i = 0; i < n; i++) { tile[0][i] = (byte) g[i]; tile[1][i] = (byte) c[i]; }
        tiles.put(key, tile);
        return tile;
    }
    /** Target px a tile's side; the most tiles kept (a Manta screen's 300 and more), least recently used let go. */
    private static final int TILE = 128, TILES = 400;
    private double tileScale = Double.NaN, tileOx, tileOy;
    private final java.util.LinkedHashMap<Long, byte[][]> tiles = new java.util.LinkedHashMap<Long, byte[][]>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<Long, byte[][]> eldest) { return size() > TILES; }
    };
    /** Whether some stroke's bounds meet page area {@code l, t, r, b}. */
    boolean meets(double l, double t, double r, double b) {
        for (Stroke s : strokes) { double[] x = s.bounds(); if (x[0] < r && x[2] > l && x[1] < b && x[3] > t) return true; }
        return false;
    }

    // ---- the eraser's cut and the Lasso's ----

    /**
     * These lines with a round dab of radius {@code r} at page point {@code cx, cy} taken out: each point inside goes; a
     * stroke crossed becomes two, each new end where the line meets the dab's edge, cut flat; a stroke wholly inside goes.
     * Itself when the dab meets none.
     */
    VectorLines cut(double cx, double cy, double r) { return cut(java.util.Collections.singletonList(new double[]{cx, cy, r})); }
    /**
     * These lines with the swept path of an eraser taken out: each of {@code capsules} ({x0, y0, r0, x1, y1, r1}, page px,
     * from one sample to the next) as the straight band of the smaller radius between round ends: the lift's last pass,
     * so nothing is left where the eraser went whatever its dabs missed ({@code VectorInk.Cut#done}).
     */
    VectorLines cutSwept(java.util.List<double[]> capsules) { return capsules.isEmpty() ? this : split(new Capsules(capsules), false)[0]; }
    /** These lines with every dab ({x, y, radius}, page px) of {@code dabs} taken out at once, as {@link #cut(double, double, double)}: an eraser's stroke. */
    VectorLines cut(java.util.List<double[]> dabs) { return dabs.isEmpty() ? this : split(new Dabs(dabs), false)[0]; }
    /**
     * These lines parted by the closed loop through {@code count} points ({@code x, y} pairs, page px): what lies outside
     * it, then what lies inside it, each line crossed by the loop cut flat where it crosses ({@link #START_CUT}, {@link
     * #END_CUT}): the Lasso's lift. Each is this itself when nothing lies inside, {@link #NONE} when nothing outside.
     */
    VectorLines[] split(float[] points, int count) { return split(new Loop(points, count), true); }

    /** Where a region lies along a line ({@link #split}). */
    private interface Region {
        /** {left, top, right, bottom}, page px. */
        double[] box();
        /** Whether page point {@code x, y} lies inside. */
        boolean contains(double x, double y);
        /**
         * The parts of the segment from {@code ax, ay} to {@code bx, by} inside, as {t0, t1} pairs in {@code out} (t along
         * it, 0 to 1, sorted, apart), {@code in} whether its start is inside; how many pairs.
         */
        int inside(double ax, double ay, double bx, double by, boolean in, double[][] out);
    }
    /** A uniform grid over boxes ({@code l, t, r, b} each), for the items near a box. */
    private static final class Grid {
        final double cell;
        final java.util.HashMap<Long, int[]> cells = new java.util.HashMap<>();
        Grid(double cell, double[][] boxes) {
            this.cell = cell;
            for (int k = 0; k < boxes.length; k++) {
                double[] b = boxes[k];
                for (long y = (long) Math.floor(b[1] / cell); y <= (long) Math.floor(b[3] / cell); y++)
                    for (long x = (long) Math.floor(b[0] / cell); x <= (long) Math.floor(b[2] / cell); x++) {
                        long key = x << 32 ^ (y & 0xffffffffL);
                        int[] at = cells.get(key);
                        if (at == null) at = new int[]{0, 0, 0, 0, 0};
                        if (at[0] + 1 == at.length) at = java.util.Arrays.copyOf(at, 2 * at.length);
                        at[++at[0]] = k;
                        cells.put(key, at);
                    }
            }
        }
        /** The items whose boxes may meet {@code l, t, r, b}, each once, into {@code seen} (a mark per item, {@code stamp}) and {@code out}; how many. */
        int near(double l, double t, double r, double b, int[] seen, int stamp, int[] out) {
            int n = 0;
            for (long y = (long) Math.floor(t / cell); y <= (long) Math.floor(b / cell); y++)
                for (long x = (long) Math.floor(l / cell); x <= (long) Math.floor(r / cell); x++) {
                    int[] at = cells.get(x << 32 ^ (y & 0xffffffffL));
                    if (at != null) for (int i = 1; i <= at[0]; i++) if (seen[at[i]] != stamp) { seen[at[i]] = stamp; out[n++] = at[i]; }
                }
            return n;
        }
    }
    /** An eraser's dabs, found through a grid of their boxes. */
    private static final class Dabs implements Region {
        final double[][] dabs;
        final Grid grid;
        final double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        final int[] seen, near;
        int stamp;
        Dabs(java.util.List<double[]> list) {
            dabs = list.toArray(new double[0][]);
            double[][] boxes = new double[dabs.length][];
            double most = 0;
            for (int k = 0; k < dabs.length; k++) {
                double[] d = dabs[k];
                boxes[k] = new double[]{d[0] - d[2], d[1] - d[2], d[0] + d[2], d[1] + d[2]};
                box[0] = Math.min(box[0], boxes[k][0]); box[1] = Math.min(box[1], boxes[k][1]); box[2] = Math.max(box[2], boxes[k][2]); box[3] = Math.max(box[3], boxes[k][3]);
                most = Math.max(most, d[2]);
            }
            grid = new Grid(Math.max(4, 2 * most), boxes);
            seen = new int[dabs.length]; near = new int[dabs.length];
        }
        @Override public double[] box() { return box; }
        @Override public boolean contains(double x, double y) {
            int n = grid.near(x, y, x, y, seen, ++stamp, near);
            for (int i = 0; i < n; i++) { double[] d = dabs[near[i]]; double dx = x - d[0], dy = y - d[1]; if (dx * dx + dy * dy < d[2] * d[2]) return true; }
            return false;
        }
        @Override public int inside(double ax, double ay, double bx, double by, boolean in, double[][] out) {
            int n = grid.near(Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx), Math.max(ay, by), seen, ++stamp, near), k = 0;
            double dx = bx - ax, dy = by - ay, qa = dx * dx + dy * dy;
            for (int i = 0; i < n; i++) {
                double[] d = dabs[near[i]];
                // Where the segment is inside the dab: |a + t d − c|² < r², t in [0, 1].
                double fx = ax - d[0], fy = ay - d[1], qb = 2 * (fx * dx + fy * dy), qc = fx * fx + fy * fy - d[2] * d[2];
                double disc = qb * qb - 4 * qa * qc, t0, t1;
                if (qa > 1e-12 && disc > 0) { double sq = Math.sqrt(disc); t0 = (-qb - sq) / (2 * qa); t1 = (-qb + sq) / (2 * qa); }
                else if (qa <= 1e-12 && qc < 0) { t0 = 0; t1 = 1; }
                else continue;
                if (t1 <= 0 || t0 >= 1) continue;
                // Full (a scrubbing hand lays dozens of dabs over one segment): joined so far, then on. Until 2026-11-04 the
                // pairs past 64 were returned unsorted and unjoined, so a long stroke's lift kept dashes of the lines.
                if (k == out.length) { k = merge(out, k); if (k == out.length) return k; }
                out[k][0] = Math.max(0, t0); out[k][1] = Math.min(1, t1); k++;
            }
            return merge(out, k);
        }
    }
    /** An eraser's swept path, capsule by capsule ({@link #cutSwept}), found through a grid of their boxes. */
    private static final class Capsules implements Region {
        final double[][] caps;
        final Grid grid;
        final double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        final int[] seen, near;
        int stamp;
        Capsules(java.util.List<double[]> list) {
            caps = new double[list.size()][];
            double[][] boxes = new double[caps.length][];
            double most = 0;
            for (int k = 0; k < caps.length; k++) {
                double[] c = list.get(k), q = {c[0], c[1], c[3], c[4], Math.min(c[2], c[5])};
                caps[k] = q;
                boxes[k] = new double[]{Math.min(q[0], q[2]) - q[4], Math.min(q[1], q[3]) - q[4], Math.max(q[0], q[2]) + q[4], Math.max(q[1], q[3]) + q[4]};
                box[0] = Math.min(box[0], boxes[k][0]); box[1] = Math.min(box[1], boxes[k][1]); box[2] = Math.max(box[2], boxes[k][2]); box[3] = Math.max(box[3], boxes[k][3]);
                most = Math.max(most, q[4]);
            }
            grid = new Grid(Math.max(8, 2 * most), boxes);
            seen = new int[caps.length]; near = new int[caps.length];
        }
        @Override public double[] box() { return box; }
        @Override public boolean contains(double x, double y) {
            int n = grid.near(x, y, x, y, seen, ++stamp, near);
            for (int i = 0; i < n; i++) { double[] c = caps[near[i]]; if (toSegment(x, y, c[0], c[1], c[2], c[3]) < c[4]) return true; }
            return false;
        }
        @Override public int inside(double ax, double ay, double bx, double by, boolean in, double[][] out) {
            int n = grid.near(Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx), Math.max(ay, by), seen, ++stamp, near), k = 0;
            for (int i = 0; i < n; i++) {
                double[] c = caps[near[i]];
                // A capsule is convex: the segment's part inside it is one stretch, its round ends' and its band's together.
                double lo = 2, hi = -1;
                double[] t = disc(ax, ay, bx, by, c[0], c[1], c[4]);
                if (t != null) { lo = Math.min(lo, t[0]); hi = Math.max(hi, t[1]); }
                t = disc(ax, ay, bx, by, c[2], c[3], c[4]);
                if (t != null) { lo = Math.min(lo, t[0]); hi = Math.max(hi, t[1]); }
                t = band(ax, ay, bx, by, c);
                if (t != null) { lo = Math.min(lo, t[0]); hi = Math.max(hi, t[1]); }
                lo = Math.max(0, lo); hi = Math.min(1, hi);
                if (hi <= lo) continue;
                if (k == out.length) { k = merge(out, k); if (k == out.length) return k; }
                out[k][0] = lo; out[k][1] = hi; k++;
            }
            return merge(out, k);
        }
        private static double toSegment(double x, double y, double ax, double ay, double bx, double by) {
            double dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy, t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / l2));
            double ex = x - ax - t * dx, ey = y - ay - t * dy;
            return Math.sqrt(ex * ex + ey * ey);
        }
        /** Where the segment from {@code a} to {@code b} is inside the disc at {@code c} of radius {@code r}: {t0, t1}, or null. */
        private static double[] disc(double ax, double ay, double bx, double by, double cx, double cy, double r) {
            double dx = bx - ax, dy = by - ay, fx = ax - cx, fy = ay - cy, qa = dx * dx + dy * dy, qb = 2 * (fx * dx + fy * dy), qc = fx * fx + fy * fy - r * r;
            if (qa < 1e-12) return qc < 0 ? new double[]{0, 1} : null;
            double disc = qb * qb - 4 * qa * qc;
            if (disc <= 0) return null;
            double sq = Math.sqrt(disc);
            return new double[]{(-qb - sq) / (2 * qa), (-qb + sq) / (2 * qa)};
        }
        /** Where the segment is inside capsule {@code c}'s straight band (its axis' length, its radius each side): {t0, t1}, or null. */
        private static double[] band(double ax, double ay, double bx, double by, double[] c) {
            double ux = c[2] - c[0], uy = c[3] - c[1], len = Math.hypot(ux, uy);
            if (len < 1e-9) return null;
            ux /= len; uy /= len;
            double dx = bx - ax, dy = by - ay, pu = (ax - c[0]) * ux + (ay - c[1]) * uy, pv = -(ax - c[0]) * uy + (ay - c[1]) * ux;
            double du = dx * ux + dy * uy, dv = -dx * uy + dy * ux, t0 = -Double.MAX_VALUE, t1 = Double.MAX_VALUE;
            // Liang–Barsky, each side p t <= q: 0 <= u, u <= len, -r <= v, v <= r.
            double[][] sides = {{-du, pu}, {du, len - pu}, {-dv, pv + c[4]}, {dv, c[4] - pv}};
            for (double[] side : sides) {
                double p = side[0], q = side[1];
                if (Math.abs(p) < 1e-12) { if (q < 0) return null; continue; }
                double t = q / p;
                if (p < 0) t0 = Math.max(t0, t); else t1 = Math.min(t1, t);
            }
            return t0 < t1 ? new double[]{t0, t1} : null;
        }
    }
    /** The pairs {@code out[0..k)} sorted and joined where they overlap; how many are left. */
    private static int merge(double[][] out, int k) {
        if (k < 2) return k;
        java.util.Arrays.sort(out, 0, k, (p, q) -> Double.compare(p[0], q[0]));
        int m = 0;
        for (int i = 1; i < k; i++) {
            if (out[i][0] <= out[m][1]) out[m][1] = Math.max(out[m][1], out[i][1]);
            else { m++; out[m][0] = out[i][0]; out[m][1] = out[i][1]; }
        }
        return m + 1;
    }
    /** The Lasso's closed loop, its edges found through a grid. */
    private static final class Loop implements Region {
        final double[] xs, ys;
        final Grid grid;
        final double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        final int[] seen, near;
        int stamp;
        final double[] ts;
        Loop(float[] points, int count) {
            xs = new double[count]; ys = new double[count];
            double[][] boxes = new double[count][];
            for (int i = 0; i < count; i++) {
                xs[i] = points[2 * i]; ys[i] = points[2 * i + 1];
                box[0] = Math.min(box[0], xs[i]); box[1] = Math.min(box[1], ys[i]); box[2] = Math.max(box[2], xs[i]); box[3] = Math.max(box[3], ys[i]);
            }
            for (int i = 0; i < count; i++) { int j = (i + 1) % count; boxes[i] = new double[]{Math.min(xs[i], xs[j]), Math.min(ys[i], ys[j]), Math.max(xs[i], xs[j]), Math.max(ys[i], ys[j])}; }
            grid = new Grid(16, boxes);
            seen = new int[count]; near = new int[count]; ts = new double[count + 1];
        }
        @Override public double[] box() { return box; }
        @Override public boolean contains(double x, double y) {
            // Crossings of the row to the right, as FloatingSelection.inside counts a pixel's centre.
            boolean in = false;
            for (int i = 0, j = xs.length - 1; i < xs.length; j = i++)
                if ((ys[j] <= y) != (ys[i] <= y) && x < xs[j] + (y - ys[j]) / (ys[i] - ys[j]) * (xs[i] - xs[j])) in = !in;
            return in;
        }
        @Override public int inside(double ax, double ay, double bx, double by, boolean in, double[][] out) {
            int n = grid.near(Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx), Math.max(ay, by), seen, ++stamp, near), m = 0;
            double dx = bx - ax, dy = by - ay;
            for (int i = 0; i < n; i++) {
                int e = near[i], f = (e + 1) % xs.length;
                double ex = xs[f] - xs[e], ey = ys[f] - ys[e], den = dx * ey - dy * ex;
                if (Math.abs(den) < 1e-12) continue;
                double cx = xs[e] - ax, cy = ys[e] - ay, t = (cx * ey - cy * ex) / den, u = (cx * dy - cy * dx) / den;
                if (t >= 0 && t < 1 && u >= 0 && u < 1) ts[m++] = t;
            }
            java.util.Arrays.sort(ts, 0, m);
            int k = 0;
            double start = 0;
            for (int i = 0; i < m && k < out.length; i++) {
                if (in) { out[k][0] = start; out[k][1] = ts[i]; k++; } else start = ts[i];
                in = !in;
            }
            if (in && k < out.length) { out[k][0] = start; out[k][1] = 1; k++; }
            return k;
        }
    }
    /** These lines parted by {@code region}: {outside, inside} ({@code inside} kept only when asked for, else {@link #NONE}). */
    private VectorLines[] split(Region region, boolean keepInside) {
        double[] box = region.box();
        java.util.ArrayList<Stroke> outs = new java.util.ArrayList<>(), ins = new java.util.ArrayList<>();
        double[][] spans = new double[256][2];
        boolean changed = false;
        for (Stroke s : strokes) {
            double[] b = s.bounds();
            if (b[0] > box[2] || b[2] < box[0] || b[1] > box[3] || b[3] < box[1]) { outs.add(s); continue; }
            changed |= split(s, region, outs, keepInside ? ins : null, spans);
        }
        if (!changed) return new VectorLines[]{this, NONE};
        return new VectorLines[]{new VectorLines(outs.toArray(new Stroke[0])), new VectorLines(ins.toArray(new Stroke[0]))};
    }
    /** {@code s} parted by {@code region} into {@code outs} and {@code ins} (null: dropped); whether any of it lay inside. */
    private static boolean split(Stroke s, Region region, java.util.List<Stroke> outs, java.util.List<Stroke> ins, double[][] spans) {
        int n = s.points();
        boolean in = region.contains(s.x(0), s.y(0));
        if (n == 1) { if (!in) outs.add(s); else if (ins != null) ins.add(s); return in; }
        java.util.List<Stroke> outPieces = new java.util.ArrayList<>(), inPieces = new java.util.ArrayList<>();
        Piece run = new Piece(s, (s.caps & START_CUT) != 0);
        run.add(s.xy[0], s.xy[1], s.share[0]);
        boolean any = in, crossed = false;
        for (int i = 0; i < n - 1; i++) {
            int k = region.inside(s.x(i), s.y(i), s.x(i + 1), s.y(i + 1), in, spans);
            // Inside, yet the segment starts outside: it left at its start, the region's very edge.
            if (in && (k == 0 || spans[0][0] > 0)) {
                keep(run.done(true), inPieces);
                run = new Piece(s, true);
                run.add(s.xy[2 * i], s.xy[2 * i + 1], s.share[i]);
                in = false; crossed = true;
            }
            for (int j = 0; j < k; j++) {
                double t0 = spans[j][0], t1 = spans[j][1];
                if (!in) {
                    // In at t0 (at the segment's start: from its very edge).
                    if (t0 > 0) run.at(s, i, t0);
                    keep(run.done(true), outPieces);
                    run = new Piece(s, true);
                    if (t0 > 0) run.at(s, i, t0); else run.add(s.xy[2 * i], s.xy[2 * i + 1], s.share[i]);
                    in = any = crossed = true;
                }
                if (t1 < 1) {
                    run.at(s, i, t1);
                    keep(run.done(true), inPieces);
                    run = new Piece(s, true);
                    run.at(s, i, t1);
                    in = false; crossed = true;
                }
            }
            run.add(s.xy[2 * i + 2], s.xy[2 * i + 3], s.share[i + 1]);
        }
        if (!crossed) { if (!in) outs.add(s); else if (ins != null) ins.add(s); return in; }
        keep(run.done((s.caps & END_CUT) != 0), in ? inPieces : outPieces);
        outs.addAll(outPieces);
        if (ins != null) ins.addAll(inPieces);
        return any;
    }
    private static void keep(Stroke piece, java.util.List<Stroke> into) { if (piece != null) into.add(piece); }

    /**
     * These lines as a Lasso piece lifted at {@code cutX, cutY}, {@code w × h} px, lies now (FloatingSelection's pose:
     * page point = centre + R(angle) · scale · (q − size / 2)): moved, turned and scaled, widths too.
     */
    VectorLines posed(double cutX, double cutY, double w, double h, double centerX, double centerY, double scale, double angle) {
        if (strokes.length == 0) return this;
        double cos = Math.cos(angle) * scale, sin = Math.sin(angle) * scale;
        Stroke[] out = new Stroke[strokes.length];
        for (int k = 0; k < strokes.length; k++) {
            Stroke s = strokes[k];
            int[] xy = new int[s.xy.length];
            for (int i = 0; i < s.points(); i++) {
                double qx = s.x(i) - cutX - w / 2, qy = s.y(i) - cutY - h / 2;
                xy[2 * i] = (int) Math.round((centerX + cos * qx - sin * qy) * SUB);
                xy[2 * i + 1] = (int) Math.round((centerY + sin * qx + cos * qy) * SUB);
            }
            out[k] = new Stroke(s.shade, (float) (s.size * scale), s.caps, xy, s.share);
        }
        return new VectorLines(out);
    }
    /** {left, top, right, bottom} of every stroke, page px, widths included; an empty box for none. */
    double[] bounds() {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Stroke s : strokes) { double[] x = s.bounds(); b[0] = Math.min(b[0], x[0]); b[1] = Math.min(b[1], x[1]); b[2] = Math.max(b[2], x[2]); b[3] = Math.max(b[3], x[3]); }
        return strokes.length == 0 ? new double[]{0, 0, 0, 0} : b;
    }
    /** These lines, then {@code more}'s over them. */
    VectorLines with(VectorLines more) {
        if (more.strokes.length == 0) return this;
        Stroke[] next = java.util.Arrays.copyOf(strokes, strokes.length + more.strokes.length);
        System.arraycopy(more.strokes, 0, next, strokes.length, more.strokes.length);
        return new VectorLines(next);
    }
    /** A stroke's piece being gathered. */
    private static final class Piece {
        final Stroke of; final boolean startCut;
        int[] xy = new int[32]; byte[] pr = new byte[16]; int n;
        Piece(Stroke of, boolean startCut) { this.of = of; this.startCut = startCut; }
        void add(int x, int y, byte p) {
            if (n > 0 && xy[2 * n - 2] == x && xy[2 * n - 1] == y) return;
            if (n == pr.length) { pr = java.util.Arrays.copyOf(pr, 2 * n); xy = java.util.Arrays.copyOf(xy, 4 * n); }
            xy[2 * n] = x; xy[2 * n + 1] = y; pr[n++] = p;
        }
        /** The point {@code t} of the way from point {@code i} of {@code s} to the next. */
        void at(Stroke s, int i, double t) {
            add((int) Math.round((s.x(i) + (s.x(i + 1) - s.x(i)) * t) * SUB), (int) Math.round((s.y(i) + (s.y(i + 1) - s.y(i)) * t) * SUB),
                    (byte) Math.round(255 * (s.p(i) + (s.p(i + 1) - s.p(i)) * t)));
        }
        /** The piece, its last end cut or not; null when it is a crumb (one point left of a line cut at it). */
        Stroke done(boolean endCut) {
            if (n < 2 && (startCut || endCut)) return null;
            // A crumb: cut at both ends and shorter than a pixel or half its width (a speck the screen shows as dots).
            if (startCut && endCut) {
                double length = 0, least = Math.max(1, of.size / 2);
                for (int i = 1; i < n && length < least; i++) length += Math.hypot(xy[2 * i] - xy[2 * i - 2], xy[2 * i + 1] - xy[2 * i - 1]) / SUB;
                if (length < least) return null;
            }
            return new Stroke(of.shade, of.size, (startCut ? START_CUT : 0) | (endCut ? END_CUT : 0), java.util.Arrays.copyOf(xy, 2 * n), java.util.Arrays.copyOf(pr, n));
        }
    }

    // ---- the file ----

    /** 2 since the medium (2026-11-04): a point's byte is its width's share of the size; 1's was its pressure, the width 0.8 to 1.2 of the size. */
    private static final int VERSION = 2;
    /** Its bytes: a version, the count of strokes, then each stroke's shade, caps, size (a float), count of points and points (x, y as ints, width's share a byte). */
    byte[] encode() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(16 + 9 * points);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION); out.writeInt(strokes.length);
            for (Stroke s : strokes) {
                out.writeByte(s.shade); out.writeByte(s.caps); out.writeFloat(s.size); out.writeInt(s.points());
                for (int i = 0; i < s.points(); i++) { out.writeInt(s.xy[2 * i]); out.writeInt(s.xy[2 * i + 1]); out.writeByte(s.share[i]); }
            }
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return bytes.toByteArray();
    }
    /** The lines {@link #encode} wrote; null for bytes this version doesn't read (the page opens with its pixels alone). */
    static VectorLines decode(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(bytes))) {
            int version = in.readUnsignedByte();
            if (version != VERSION && version != 1) return null;
            int count = in.readInt(), total = 0;
            if (count < 0 || count > MOST_POINTS) return null;
            Stroke[] strokes = new Stroke[count];
            for (int k = 0; k < count; k++) {
                int shade = in.readUnsignedByte(), caps = in.readUnsignedByte(); float size = in.readFloat(); int n = in.readInt();
                if (caps > 3 || !(size > 0 && size < 4096) || n < 1 || (total += n) > 2 * MOST_POINTS) return null;
                int[] xy = new int[2 * n]; byte[] pr = new byte[n];
                for (int i = 0; i < n; i++) { xy[2 * i] = in.readInt(); xy[2 * i + 1] = in.readInt(); pr[i] = in.readByte(); }
                // Version 1: a pressure, the width 0.8 to 1.2 of the size; as a share of the widest.
                if (version == 1) { for (int i = 0; i < n; i++) pr[i] = (byte) Math.round(255 * (.8 + .4 * (pr[i] & 255) / 255.0) / 1.2); size *= 1.2f; }
                strokes[k] = new Stroke(shade, size, caps, xy, pr);
            }
            return in.read() == -1 ? new VectorLines(strokes) : null;
        } catch (IOException bad) { return null; }
    }
}
