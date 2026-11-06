package io.github.mpdairy.monopaint;

import java.util.Random;

/**
 * Hold to Snap ({@link HoldSnap}): rough lines, arcs, circles, ovals, triangles, boxes, pentagons and open runs of
 * lines, drawn by a wobbly synthetic hand, snap to their kind; handwriting, scribbles, spirals, figure eights, waves,
 * hatching and S curves don't; a pause shorter than the hold never asks, and a long one over a scribble lets the
 * stroke draw on; the clean stroke keeps the rough one's pressure, tilt and time, in order; drawn again the way the pad
 * does it (the rough stroke finished, then undone as Undo does, the same brush started again and fed the clean
 * samples), the page is byte for byte the clean stroke drawn alone, one Undo leaves it blank, and nothing is left to
 * redo; wet paint laid just before dries on as with no snap, but where the rough stroke crossed it (measured).
 */
public final class HoldSnapChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        boolean verbose = args.length > 0 && args[0].equals("verbose");
        accuracy(verbose);
        pause(); carried(); dragged();
        redrawn("Swell brush", 14); redrawn("Compressed", 24); redrawn("Soft wash", 70); redrawn("Wet blooms", 60); redrawn("Wet gouache", 40); redrawn("2B pencil", 10);
        // Wet paint laid just before, apart from the snap, moves and dries on as with no snap (WetCanvas.forget); where
        // the rough stroke crossed it, the Undo took its water there (measured, not held).
        StringBuilder crossing = new StringBuilder();
        for (String b : new String[]{"Soft wash", "Wet blooms", "Wet gouache"}) {
            double apart = intoWet(b, 50, false);
            check(apart < .05, b + ": a wet stroke apart from the snap dries as with none (" + apart + " grey a pixel off)");
            crossing.append(String.format("%s%s %.2f", crossing.length() == 0 ? "" : ", ", b, intoWet(b, 50, true)));
        }
        System.out.println("PASS: Hold to Snap " + summary + "; a pause under the hold never asks, a long one over a scribble draws on; the clean samples keep the rough ones' pressure, tilt and time, in order; a snapped line's end moved keeps its start and each sample's share of the way, pressure, tilt and time (a circle doesn't move); "
                + "drawn again as the pad does it (Swell brush, Compressed, the wet watercolours Soft wash and Wet blooms, Wet gouache, 2B pencil), the page is the clean stroke alone (byte for byte for dry media, wet ones within a twentieth of a grey a pixel), one Undo leaves it blank, nothing to redo; "
                + "wet paint laid just before dries on as with no snap; where the rough stroke crossed it, its water there is gone (mean grey a pixel off: " + crossing + ")");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    // ---- the synthetic hand ----

    /** A rough sample run: {x, y, pressure, tiltX, tiltY, time} rows, along {@code ideal} (dense, page px) with a hand's wobble. */
    static double[][] hand(double[][] ideal, double size, Random rnd, boolean closed) {
        double[] ix = ideal[0], iy = ideal[1];
        int m = ix.length;
        // Corners rounded a little, as a hand rounds them: a moving average over about 2% of the size.
        double[] cum = new double[m];
        for (int i = 1; i < m; i++) cum[i] = cum[i - 1] + Math.hypot(ix[i] - ix[i - 1], iy[i] - iy[i - 1]);
        double L = cum[m - 1];
        int win = Math.max(1, (int) (.02 * size / (L / m)));
        double[] sx = new double[m], sy = new double[m];
        for (int i = 0; i < m; i++) {
            double ax = 0, ay = 0; int c = 0;
            for (int k = -win; k <= win; k++) {
                int j = i + k;
                if (closed) j = ((j % (m - 1)) + (m - 1)) % (m - 1); else if (j < 0 || j >= m) continue;
                ax += ix[j]; ay += iy[j]; c++;
            }
            sx[i] = ax / c; sy[i] = ay / c;
        }
        // Wobble across the path: three slow waves, up to ~2% of the size, and a hair of jitter.
        double amp = size * (.006 + .012 * rnd.nextDouble());
        double[] f = {1 + rnd.nextInt(2), 2 + rnd.nextInt(3), 4 + rnd.nextInt(4)}, ph = {rnd.nextDouble() * 7, rnd.nextDouble() * 7, rnd.nextDouble() * 7};
        // Closed: start anywhere, end a little short of the start or a little past it.
        double start = closed ? rnd.nextDouble() * L : 0, total = closed ? L * (1 + (-.05 + .13 * rnd.nextDouble())) : L;
        double speed = .5 + rnd.nextDouble(), t = 1000, s = 0;
        java.util.List<double[]> out = new java.util.ArrayList<>();
        double tilt = -20 + 40 * rnd.nextDouble();
        while (s <= total) {
            double g = closed ? (start + s) % L : s;
            int k = java.util.Arrays.binarySearch(cum, g);
            if (k < 0) k = Math.max(1, -k - 1); if (k >= m) k = m - 1; if (k == 0) k = 1;
            double w = cum[k] == cum[k - 1] ? 0 : (g - cum[k - 1]) / (cum[k] - cum[k - 1]);
            double px = sx[k - 1] + (sx[k] - sx[k - 1]) * w, py = sy[k - 1] + (sy[k] - sy[k - 1]) * w;
            double nx = -(iy[k] - iy[k - 1]), ny = ix[k] - ix[k - 1], nl = Math.hypot(nx, ny);
            if (nl > 0) { nx /= nl; ny /= nl; }
            double u = s / L, off = 0;
            for (int j = 0; j < 3; j++) off += Math.sin(2 * Math.PI * f[j] * u + ph[j]) / (j + 1);
            off = off * amp + rnd.nextGaussian() * .6;
            double pressure = Math.min(1, .25 + .6 * Math.sin(Math.PI * Math.min(1, s / total)) + .05 * rnd.nextGaussian());
            out.add(new double[]{px + nx * off, py + ny * off, Math.max(.05, pressure), tilt, 10, t});
            double v = speed * (.6 + .8 * Math.sin(Math.PI * Math.min(1, s / total)) + .2);
            s += v * 4; t += 4;
        }
        return out.toArray(new double[0][]);
    }
    /** {@code k} points on a polyline through {@code px, py} (closed: back to the first). */
    static double[][] poly(double[] px, double[] py, boolean closed) {
        int sides = closed ? px.length : px.length - 1, per = 200;
        double[] x = new double[sides * per + 1], y = new double[sides * per + 1];
        for (int s = 0; s < sides; s++) for (int i = 0; i < per; i++) {
            int a = s, b = (s + 1) % px.length; double w = i / (double) per;
            x[s * per + i] = px[a] + (px[b] - px[a]) * w; y[s * per + i] = py[a] + (py[b] - py[a]) * w;
        }
        x[sides * per] = closed ? px[0] : px[px.length - 1]; y[sides * per] = closed ? py[0] : py[py.length - 1];
        return new double[][]{x, y};
    }
    interface Curve { double[] at(double u); }
    static double[][] curve(Curve c, int n) {
        double[] x = new double[n + 1], y = new double[n + 1];
        for (int i = 0; i <= n; i++) { double[] p = c.at(i / (double) n); x[i] = p[0]; y[i] = p[1]; }
        return new double[][]{x, y};
    }
    static double[][] rotate(double[][] path, double a, double cx, double cy) {
        double[] x = path[0].clone(), y = path[1].clone(), c = {Math.cos(a), Math.sin(a)};
        for (int i = 0; i < x.length; i++) { double dx = x[i], dy = y[i]; x[i] = cx + dx * c[0] - dy * c[1]; y[i] = cy + dx * c[1] + dy * c[0]; }
        return new double[][]{x, y};
    }

    /** A rough shape of {@code kind} (or a negative), its ideal size; centred about (500, 400). */
    static double[][] shape(String kind, Random r) {
        double size = 150 + 350 * r.nextDouble(), a = r.nextDouble() * 2 * Math.PI, h = size / 2;
        switch (kind) {
            case "line": return hand(rotate(poly(new double[]{-h, h}, new double[]{0, 0}, false), a, 500, 400), size, r, false);
            case "arc": {
                double sweep = Math.toRadians(45 + 215 * r.nextDouble()), rad = h * (.6 + .4 * r.nextDouble()), dir = r.nextBoolean() ? 1 : -1;
                return hand(curve(u -> new double[]{500 + rad * Math.cos(a + dir * sweep * u), 400 + rad * Math.sin(a + dir * sweep * u)}, 400), 2 * rad, r, false);
            }
            case "circle": { int dir = r.nextBoolean() ? 1 : -1; return hand(curve(u -> new double[]{500 + h * Math.cos(dir * 2 * Math.PI * u), 400 + h * Math.sin(dir * 2 * Math.PI * u)}, 600), size, r, true); }
            case "oval": {
                double b = h / (1.4 + 1.1 * r.nextDouble()); int dir = r.nextBoolean() ? 1 : -1;
                return hand(rotate(curve(u -> new double[]{h * Math.cos(dir * 2 * Math.PI * u), b * Math.sin(dir * 2 * Math.PI * u)}, 600), a, 500, 400), size, r, true);
            }
            case "triangle": {
                double[] px = new double[3], py = new double[3];
                for (int i = 0; i < 3; i++) { double t = a + i * 2 * Math.PI / 3 + (r.nextDouble() - .5) * .5; px[i] = 500 + h * Math.cos(t); py[i] = 400 + h * Math.sin(t); }
                if (r.nextBoolean()) { double t = px[1]; px[1] = px[2]; px[2] = t; t = py[1]; py[1] = py[2]; py[2] = t; }
                return hand(poly(px, py, true), size, r, true);
            }
            case "box": {
                double w = h, q = h / (1 + 1.5 * r.nextDouble());
                double[][] p = rotate(poly(new double[]{-w, w, w, -w}, new double[]{-q, -q, q, q}, true), a, 500, 400);
                return hand(p, size, r, true);
            }
            case "pentagon": {
                double[] px = new double[5], py = new double[5];
                for (int i = 0; i < 5; i++) { double t = a + i * 2 * Math.PI / 5 + (r.nextDouble() - .5) * .2; px[i] = 500 + h * Math.cos(t); py[i] = 400 + h * Math.sin(t); }
                return hand(poly(px, py, true), size, r, true);
            }
            case "polyline": {
                int k = 2 + r.nextInt(2);
                double[] px = new double[k + 1], py = new double[k + 1];
                double heading = a, x = 500 - h / 2, y = 400;
                for (int i = 0; i <= k; i++) {
                    px[i] = x; py[i] = y;
                    double seg = size * (.4 + .4 * r.nextDouble());
                    x += seg * Math.cos(heading); y += seg * Math.sin(heading);
                    heading += (r.nextBoolean() ? 1 : -1) * Math.toRadians(60 + 80 * r.nextDouble());
                }
                return hand(poly(px, py, false), size, r, false);
            }
            // ---- negatives ----
            case "handwriting": {
                int letters = 4 + r.nextInt(5); double lh = 40 + 50 * r.nextDouble(), adv = lh * (.7 + .3 * r.nextDouble());
                double[] tall = new double[letters];
                for (int i = 0; i < letters; i++) tall[i] = r.nextDouble() < .35 ? 2.2 : 1;
                return hand(curve(u -> {
                    double t = u * letters, k = Math.floor(Math.min(letters - 1, t)), f = t - k;
                    double loop = tall[(int) k] * lh;
                    return new double[]{300 + adv * t - .45 * adv * Math.sin(2 * Math.PI * f), 400 - loop * (.5 - .5 * Math.cos(2 * Math.PI * f))};
                }, 200 * letters), letters * adv, r, false);
            }
            case "scribble": {
                double[] fx = {1 + r.nextInt(3), 2 + r.nextInt(4), 3 + r.nextInt(5)}, fy = {1 + r.nextInt(3), 2 + r.nextInt(4), 3 + r.nextInt(5)}, p = new double[6];
                for (int i = 0; i < 6; i++) p[i] = r.nextDouble() * 7;
                return hand(curve(u -> new double[]{500 + h * (Math.sin(2 * Math.PI * fx[0] * u + p[0]) + .6 * Math.sin(2 * Math.PI * fx[1] * u + p[1]) + .4 * Math.sin(2 * Math.PI * fx[2] * u + p[2])) / 2,
                        400 + h * (Math.sin(2 * Math.PI * fy[0] * u + p[3]) + .6 * Math.sin(2 * Math.PI * fy[1] * u + p[4]) + .4 * Math.sin(2 * Math.PI * fy[2] * u + p[5])) / 2}, 1200), size, r, false);
            }
            case "spiral": {
                double turns = 2 + 1.5 * r.nextDouble();
                return hand(curve(u -> new double[]{500 + h * (.15 + .85 * u) * Math.cos(a + turns * 2 * Math.PI * u), 400 + h * (.15 + .85 * u) * Math.sin(a + turns * 2 * Math.PI * u)}, 1200), size, r, false);
            }
            case "eight": return hand(rotate(curve(u -> new double[]{h * Math.sin(2 * Math.PI * u), h * .5 * Math.sin(4 * Math.PI * u)}, 800), a, 500, 400), size, r, false);
            case "wave": {
                double periods = 1.5 + 2 * r.nextDouble();
                return hand(rotate(curve(u -> new double[]{-h + 2 * h * u, h * .25 * Math.sin(2 * Math.PI * periods * u)}, 800), a, 500, 400), size, r, false);
            }
            case "hatching": {
                int k = 6 + r.nextInt(6);
                double[] px = new double[k], py = new double[k];
                for (int i = 0; i < k; i++) { px[i] = -h + (i % 2) * 2 * h; py[i] = -h + i * 2 * h / k; }
                return hand(rotate(poly(px, py, false), a, 500, 400), size, r, false);
            }
            case "s-curve": return hand(rotate(curve(u -> new double[]{h * .5 * Math.sin(2 * Math.PI * u), -h + 2 * h * u}, 600), a, 500, 400), size, r, false);
            default: throw new IllegalArgumentException(kind);
        }
    }
    static final String[] POSITIVE = {"line", "arc", "circle", "oval", "triangle", "box", "pentagon", "polyline"};
    static final HoldSnap.Kind[] EXPECTED = {HoldSnap.Kind.LINE, HoldSnap.Kind.ARC, HoldSnap.Kind.CIRCLE, HoldSnap.Kind.OVAL, HoldSnap.Kind.POLYGON, HoldSnap.Kind.BOX, HoldSnap.Kind.POLYGON, HoldSnap.Kind.POLYLINE};
    static final String[] NEGATIVE = {"handwriting", "scribble", "spiral", "eight", "wave", "hatching", "s-curve"};
    static String summary;

    static HoldSnap.Shape recognize(double[][] rows) {
        double[] x = new double[rows.length], y = new double[rows.length];
        for (int i = 0; i < rows.length; i++) { x[i] = rows[i][0]; y[i] = rows[i][1]; }
        return HoldSnap.recognize(x, y, rows.length, 1);
    }

    /** 200 rough shapes of each kind: how many snap to their kind, and how many negatives snap at all. */
    static void accuracy(boolean verbose) {
        int per = 200, right = 0, wrong = 0, all = 0, falseSnaps = 0, negs = 0;
        StringBuilder rows = new StringBuilder(), failed = new StringBuilder();
        for (int c = 0; c < POSITIVE.length; c++) {
            Random r = new Random(7 + c);
            int ok = 0, other = 0;
            java.util.Map<HoldSnap.Kind, Integer> as = new java.util.TreeMap<>();
            for (int i = 0; i < per; i++) {
                HoldSnap.Shape s = recognize(shape(POSITIVE[c], r));
                if (s != null) as.merge(s.kind, 1, Integer::sum);
                if (s != null && s.kind == EXPECTED[c]) ok++; else if (s != null) other++;
            }
            right += ok; wrong += other; all += per;
            rows.append(String.format("  %-11s %3d%% right, %3d%% another shape, %3d%% none %s%n", POSITIVE[c], 100 * ok / per, 100 * other / per, 100 * (per - ok - other) / per, as));
            if (ok < .85 * per) failed.append(POSITIVE[c] + ": only " + ok + " of " + per + " snap to " + EXPECTED[c] + " " + as + "; ");
        }
        for (int c = 0; c < NEGATIVE.length; c++) {
            Random r = new Random(101 + c);
            int snaps = 0;
            java.util.Map<HoldSnap.Kind, Integer> as = new java.util.TreeMap<>();
            for (int i = 0; i < per; i++) { HoldSnap.Shape s = recognize(shape(NEGATIVE[c], r)); if (s != null) { snaps++; as.merge(s.kind, 1, Integer::sum); } }
            falseSnaps += snaps; negs += per;
            rows.append(String.format("  %-11s %3d%% snap %s%n", NEGATIVE[c], 100 * snaps / per, as));
            if (snaps > .05 * per) failed.append(NEGATIVE[c] + ": " + snaps + " of " + per + " snap " + as + "; ");
        }
        summary = String.format("recognises %.1f%% of %d rough shapes as their kind (%.1f%% as another), snaps %.1f%% of %d handwriting, scribbles and the like",
                100. * right / all, all, 100. * wrong / all, 100. * falseSnaps / negs, negs);
        if (verbose || failed.length() > 0) System.out.print(rows);
        check(failed.length() == 0, failed.toString());
    }

    /** A rough line fed to {@link HoldSnap} with a pause in the middle, then held at the end. */
    static void pause() {
        Random r = new Random(3);
        double[][] line = shape("line", r);
        HoldSnap h = new HoldSnap(1);
        int half = line.length / 2;
        long shift = 0;
        for (int i = 0; i < line.length; i++) {
            // A 400 ms pause halfway, the pen still: ticks every 40 ms all through it never find it due.
            if (i == half) for (long w = 0; w <= 400; w += HoldSnap.TICK_MS) { check(!h.due((long) line[i - 1][5] + w), "a pause under the hold is never due"); h.sample(line[i - 1][0], line[i - 1][1], line[i - 1][2], 0, 0, (long) line[i - 1][5] + w); shift = w; }
            h.sample(line[i][0], line[i][1], line[i][2], 0, 0, (long) line[i][5] + shift);
            check(!h.due((long) line[i][5] + shift), "no snap while the pen moves");
        }
        long end = (long) line[line.length - 1][5] + shift;
        check(!h.due(end), "not yet: the pen just stopped");
        check(h.due(end + HoldSnap.HOLD_MS), "held: due");
        double[][] clean = h.snap();
        check(clean != null, "a held line snaps");
        check(!h.due(end + 2 * HoldSnap.HOLD_MS), "a rest is asked about once");
        // A scribble held long mid-way: asked, not a shape, so it draws on and nothing snaps.
        double[][] s = shape("scribble", new Random(5));
        HoldSnap g = new HoldSnap(1);
        for (int i = 0; i < s.length; i++) g.sample(s[i][0], s[i][1], s[i][2], 0, 0, (long) s[i][5] + (i >= s.length / 2 ? 800 : 0));
        int mid = s.length / 2 - 1;
        HoldSnap q = new HoldSnap(1);
        for (int i = 0; i <= mid; i++) q.sample(s[i][0], s[i][1], s[i][2], 0, 0, (long) s[i][5]);
        check(q.due((long) s[mid][5] + 700) && q.snap() == null, "a scribble held mid-way is no shape");
    }

    /** The clean samples keep every rough sample's pressure, tilt and time, in order, and end at the rest. */
    static void carried() {
        for (String kind : new String[]{"line", "circle", "box", "arc", "polyline"}) {
            double[][] rough = shape(kind, new Random(11));
            HoldSnap h = new HoldSnap(1);
            for (double[] p : rough) h.sample(p[0], p[1], p[2], p[3], p[4], (long) p[5]);
            check(h.due((long) rough[rough.length - 1][5] + HoldSnap.HOLD_MS), kind + " due");
            double[][] clean = h.snap();
            check(clean != null, kind + " snaps");
            int k = 0;
            for (int i = 0; i < clean.length; i++) {
                if (i > 0) check(clean[i][5] >= clean[i - 1][5], kind + ": times in order");
                if (k < rough.length && clean[i][2] == rough[k][2] && clean[i][5] == rough[k][5] && clean[i][3] == rough[k][3]) k++;
            }
            // Up to the rest: the samples after it are the pen held still.
            check(k >= rough.length - 8, kind + ": every rough sample's pressure, tilt and time carried (" + k + " of " + rough.length + ")");
            check(clean.length - k <= 6, kind + ": only corners added (" + (clean.length - k) + ")");
        }
    }

    /**
     * A snapped line's end moved ({@link HoldSnap#to}, the pad's drag): the start kept, the end where the pen is, and each
     * sample the same share of the way along as on the snapped line, its pressure, tilt and time kept. Only a line moves.
     */
    static void dragged() {
        double[][] rough = shape("line", new Random(11));
        HoldSnap h = new HoldSnap(1);
        for (double[] p : rough) h.sample(p[0], p[1], p[2], p[3], p[4], (long) p[5]);
        double[] last = rough[rough.length - 1];
        check(h.due((long) last[5] + HoldSnap.HOLD_MS), "line due");
        double[][] clean = h.snap();
        check(h.line(), "a snapped line moves");
        check(!h.moved(last[0], last[1]) && h.moved(last[0] + HoldSnap.STILL_PX + 1, last[1]), "the pen at its rest is still, a few px off it moves");
        double[] a = clean[0], b = clean[clean.length - 1];
        double ex = a[0] + .4 * (b[1] - a[1]), ey = a[1] - .7 * (b[0] - a[0]);
        double[][] moved = h.to(ex, ey);
        check(moved.length == clean.length, "as many samples");
        check(moved[0][0] == a[0] && moved[0][1] == a[1], "the start kept");
        check(Math.abs(moved[moved.length - 1][0] - ex) < 1e-9 && Math.abs(moved[moved.length - 1][1] - ey) < 1e-9, "the end at the pen");
        double was = Math.hypot(b[0] - a[0], b[1] - a[1]), now = Math.hypot(ex - a[0], ey - a[1]);
        for (int i = 0; i < clean.length; i++) {
            for (int c = 2; c < 6; c++) check(moved[i][c] == clean[i][c], "pressure, tilt and time kept");
            double share = Math.hypot(clean[i][0] - a[0], clean[i][1] - a[1]) / was, moving = Math.hypot(moved[i][0] - a[0], moved[i][1] - a[1]) / now;
            check(Math.abs(share - moving) < 1e-9, "the same share of the way (" + share + ", " + moving + ")");
            check(Math.abs((moved[i][0] - a[0]) * (ey - a[1]) - (moved[i][1] - a[1]) * (ex - a[0])) / now < 1e-6, "on the moved line");
        }
        HoldSnap o = new HoldSnap(1);
        double[][] circle = shape("circle", new Random(11));
        for (double[] p : circle) o.sample(p[0], p[1], p[2], p[3], p[4], (long) p[5]);
        check(o.due((long) circle[circle.length - 1][5] + HoldSnap.HOLD_MS) && o.snap() != null && !o.line(), "a circle doesn't move");
    }

    /**
     * The pad's redraw ({@code DrawingPad.snapTo}): the rough stroke finished then undone (Undo's own path), the same
     * brush started again and fed the clean samples. The page then is the clean stroke drawn alone, byte for byte; one
     * Undo leaves the page blank, and nothing is left to redo.
     */
    static void redrawn(String preset, double size) {
        Brush brush = current(preset);
        double[][] rough = shape("oval", new Random(13));
        HoldSnap h = new HoldSnap(1);
        for (double[] p : rough) h.sample(p[0], p[1], p[2], p[3], p[4], (long) p[5]);
        double[][] clean = h.snap();
        check(clean != null, preset + ": the oval snaps");
        ToneDocument blank = InkWashLayers.blank(W, H);
        String empty = WetPaintChecks.crc(blank);

        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        draw(d, s, brush, size, rough);
        check(!WetPaintChecks.crc(d).equals(empty), preset + ": the rough stroke drew");
        check(s.takeBack(d), preset + ": the rough stroke taken back");
        draw(d, s, brush, size, clean);
        settle(s, d);

        ToneDocument alone = InkWashLayers.blank(W, H);
        WatercolorSession t = new WatercolorSession();
        draw(alone, t, brush, size, clean);
        settle(t, alone);
        check(WetPaintChecks.crc(d).equals(WetPaintChecks.crc(alone)) || off(d, alone) < .05, preset + ": the snapped page is the clean stroke alone (" + off(d, alone) + " grey a pixel off)");
        check(!d.canRedo(), preset + ": the rough stroke is not left to redo");
        check(s.undo(d) && WetPaintChecks.crc(d).equals(empty), preset + ": one Undo leaves the page blank");
        check(!d.canUndo() || !s.undo(d) || WetPaintChecks.crc(d).equals(empty), preset + ": nothing more");
    }
    /**
     * Snapped beside or across paint still wet from the stroke before ({@code crossing}): how far the page is from the
     * clean stroke laid into it alone, mean grey levels a pixel (every other pixel read).
     */
    static double intoWet(String preset, double size, boolean crossing) {
        Brush brush = current(preset);
        double[][] rough = shape("oval", new Random(13));
        HoldSnap h = new HoldSnap(1);
        for (double[] p : rough) h.sample(p[0], p[1], p[2], p[3], p[4], (long) p[5]);
        double[][] clean = h.snap(), before = crossing ? FloatChecks.line(200, 380, 800, 420, .8) : FloatChecks.line(100, 40, 900, 40, .8);
        ToneDocument d = InkWashLayers.blank(W, H), alone = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession(), t = new WatercolorSession();
        draw(d, s, brush, size, rows(before)); draw(alone, t, brush, size, rows(before));
        draw(d, s, brush, size, rough); check(s.takeBack(d), preset + ": taken back");
        draw(d, s, brush, size, clean); draw(alone, t, brush, size, clean);
        settle(s, d); settle(t, alone);
        return off(d, alone);
    }
    /** How far page {@code d} is from {@code e}: mean grey levels a pixel, every other pixel read. */
    static double off(ToneDocument d, ToneDocument e) {
        double diff = 0;
        for (int y = 0; y < H; y += 2) for (int x = 0; x < W; x += 2) diff += Math.abs(d.compositeTone(x, y) - e.compositeTone(x, y));
        return diff / (W * H / 4);
    }
    /** {x, y, pressure, time} rows as {x, y, pressure, tiltX, tiltY, time}. */
    static double[][] rows(double[][] path) { double[][] o = new double[path.length][]; for (int i = 0; i < path.length; i++) o[i] = new double[]{path[i][0], path[i][1], path[i][2], 0, 0, path[i][3]}; return o; }
    static final int W = 1000, H = 800;
    /** The Tools page's preset {@code name} as it is now (FloatChecks' finds the retired Slow wash for Soft wash). */
    static Brush current(String name) {
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) return p.brush();
        throw new IllegalArgumentException(name);
    }
    static void draw(ToneDocument d, WatercolorSession s, Brush brush, double size, double[][] rows) { draw(d, s, brush, size, .8, rows); }
    static void draw(ToneDocument d, WatercolorSession s, Brush brush, double size, double darkness, double[][] rows) {
        // As InkWash.stroke starts a slot's stroke (it is Android's): what settles first, then the stroke, a Filled Shape's fill after.
        ModularBrush parts = s.onWater(d, brush.parts);
        s.settleBefore(d, parts); s.dry("stroke");
        DrawingStroke st = FilledShape.of(d, brush.parts, QuickStroke.start(d, parts, size, darkness, s), size, darkness, s);
        for (int i = 0; i < rows.length; i++) {
            double[] p = rows[i];
            st.sample((float) p[0], (float) p[1], (float) p[2], (float) p[3], (float) p[4], (long) p[5]);
            if (i % 4 == 3) st.present();
        }
        st.present();
        st.finish();
    }
    /** Wet paint left to move and dry as the drawing screen's ticks let it, then anything still settling laid. */
    static void settle(WatercolorSession s, ToneDocument d) { int n = 0; while (s.tickDrying(d) && n < 4000) n++; s.settleNow(d); }
}
