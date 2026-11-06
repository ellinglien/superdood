package io.github.mpdairy.monopaint;

import java.io.File;
import java.util.Random;

/**
 * Steady Hand and Long Stick drawn by the app's engine through the drawing screen's stroke ({@link QuickStroke#start})
 * (scripts/steady_hand_pictures.sh): synthetic shaky handwriting (a cursive word, a hand's 8 to 11 Hz tremor and the
 * pen's jitter, 480 samples a second at ms times, slower round tight turns), spirals and a star, slow and fast. Each
 * picture's panels: as the pen drew it (raw), Steady Hand .5 and 1, Long Stick .5 and 1; the smoothed panels over the
 * raw line in pale grey, so the lag and what it smoothed show. Then the Long stick preset (Charcoal) beside Soft
 * charcoal. Page pixels, the Manta's 300 dpi.
 */
public class SteadyHandPictures {
    static final int W = 420, H = 240;
    static final String LONG_STICK = "Long stick";

    public static void main(String[] a) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (a.length > 0 && a[0].equals("code")) { ModularBrush b = with(preset("Soft charcoal"), Double.parseDouble(a[1]), Double.parseDouble(a[2])).copy(Long.parseLong(a[3])); b.name = LONG_STICK; System.out.println(ModularCode.encode(b)); return; }
        File out = new File(a.length > 0 ? a[0] : "/tmp/steady-pictures");
        out.mkdirs();
        ModularBrush pen = preset("Steady line");
        set(out, "handwriting-slow", pen, 5, handwriting(.45, 1));
        set(out, "handwriting-fast", pen, 5, handwriting(1.4, 1));
        set(out, "handwriting-shaky", pen, 5, handwriting(.3, 2.2));
        set(out, "spiral-slow", pen, 5, spiral(W / 2.0, H / 2.0, 100, .25, 1.4));
        set(out, "spiral-fast", pen, 5, spiral(W / 2.0, H / 2.0, 100, 2, 1));
        set(out, "star-fast", pen, 5, star(1.8));
        ModularBrush soft = preset("Soft charcoal"), stick = preset(LONG_STICK);
        WetSmudgePictures.save(new File(out, "long-stick-charcoal.png"),
                draw(soft, 18, handwriting(1.2, 1), null), draw(stick, 18, handwriting(1.2, 1), null),
                draw(soft, 18, star(1.6), null), draw(stick, 18, star(1.6), null));
        System.out.println("wrote " + out);
    }

    /** {@code name}.png: raw, Steady Hand .5 and 1, Long Stick .5 and 1, each smoothed one over the raw line in pale grey. */
    static void set(File out, String name, ModularBrush b, double size, double[][] path) throws Exception {
        int[][] raw = draw(b, size, path, null);
        WetSmudgePictures.save(new File(out, name + ".png"), raw,
                draw(with(b, .5, 0), size, path, path), draw(with(b, 1, 0), size, path, path),
                draw(with(b, 0, .5), size, path, path), draw(with(b, 0, 1), size, path, path));
    }
    /** {@code path} drawn with {@code b}, over {@code under} drawn raw in pale grey (null for none). */
    static int[][] draw(ModularBrush b, double size, double[][] path, double[][] under) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        if (under != null) CarrySmudgeChecks.feed(QuickStroke.start(d, with(b, 0, 0), size, .25, s), under);
        CarrySmudgeChecks.feed(QuickStroke.start(d, b, size, 1, s), path);
        s.settleNow(d);
        return WetSmudgePictures.grab(d);
    }

    static ModularBrush preset(String name) {
        for (ToolPresets.Preset p : ToolPresets.withRetired()) if (p.name.equals(name)) return p.brush().parts;
        throw new AssertionError("no preset " + name);
    }
    /** {@code b} with Steady Hand {@code steady} and Long Stick {@code stick}. */
    static ModularBrush with(ModularBrush b, double steady, double stick) {
        ModularBrush c = b.copy();
        c.part(BrushPart.HAND).set(PartDial.STEADY_HAND, steady);
        c.part(BrushPart.HAND).set(PartDial.LONG_STICK, stick);
        return c.normalize();
    }

    /**
     * A cursive word ("lelle" and an arcade), its x-height 34 px, as a hand writes it: {@code speed} px/ms on its
     * straights, slower round tight turns (the two-thirds power law), a tremor of {@code shake} px (8.7 and 11.3 Hz) and
     * the pen's jitter; 480 samples a second at ms times, its pressure rising off the mark and falling at the lift.
     */
    static double[][] handwriting(double speed, double shake) {
        double[][] keys = {{0, 0}, {14, -12}, {30, -60}, {28, -96}, {18, -100}, {14, -84}, {20, -40}, {28, -6}, {40, 0},
                {54, -16}, {56, -32}, {46, -36}, {40, -22}, {48, -4}, {62, 0}, {78, -40}, {82, -92}, {72, -100}, {66, -84},
                {72, -36}, {82, -4}, {94, 0}, {110, -54}, {112, -94}, {102, -98}, {98, -80}, {104, -30}, {114, -4}, {126, 0},
                {140, -16}, {142, -32}, {132, -36}, {126, -22}, {134, -4}, {150, 0}, {156, -24}, {160, -34}, {170, -32},
                {172, -10}, {172, 0}, {176, -26}, {182, -34}, {192, -32}, {194, -10}, {194, 0}, {198, -26}, {206, -34},
                {216, -32}, {218, -8}, {226, 0}, {240, -6}};
        double[][] pts = new double[keys.length][];
        for (int i = 0; i < keys.length; i++) pts[i] = new double[]{60 + keys[i][0] * 1.3, 180 + keys[i][1] * 1.3};
        return walk(spline(pts), speed, shake, 11);
    }
    /** An Archimedean spiral of four turns out from ({@code cx}, {@code cy}) to radius {@code r}, at {@code speed}, with a tremor of {@code shake} px. */
    static double[][] spiral(double cx, double cy, double r, double speed, double shake) {
        int n = 4000;
        double[][] pts = new double[n + 1][];
        for (int i = 0; i <= n; i++) { double u = (double) i / n, a = u * 4 * 2 * Math.PI; pts[i] = new double[]{cx + r * u * Math.cos(a), cy + r * u * Math.sin(a)}; }
        return walk(pts, speed, shake, 0);
    }
    /** A five-pointed star drawn in one line, its sharp corners where a hand stops and turns. */
    static double[][] star(double speed) {
        double[][] keys = new double[6][];
        for (int i = 0; i <= 5; i++) { double a = -Math.PI / 2 + i * 4 * Math.PI / 5; keys[i] = new double[]{W / 2.0 + 100 * Math.cos(a), H / 2.0 + 15 + 100 * Math.sin(a)}; }
        double[][] pts = new double[5 * 200 + 1][];
        for (int i = 0; i <= 1000; i++) { int k = Math.min(4, i / 200); double u = (i - 200 * k) / 200.0; pts[i] = new double[]{keys[k][0] + (keys[k + 1][0] - keys[k][0]) * u, keys[k][1] + (keys[k + 1][1] - keys[k][1]) * u}; }
        return walk(pts, speed, .6, 0);
    }

    /** Catmull-Rom through {@code keys}, dense (40 points a span). */
    static double[][] spline(double[][] keys) {
        int n = keys.length, per = 40;
        double[][] o = new double[(n - 1) * per + 1][];
        for (int i = 0; i < n - 1; i++) {
            double[] p0 = keys[Math.max(0, i - 1)], p1 = keys[i], p2 = keys[i + 1], p3 = keys[Math.min(n - 1, i + 2)];
            for (int j = 0; j < per; j++) {
                double t = (double) j / per, t2 = t * t, t3 = t2 * t;
                double[] q = new double[2];
                for (int c = 0; c < 2; c++) q[c] = .5 * (2 * p1[c] + (-p0[c] + p2[c]) * t + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * t2 + (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * t3);
                o[i * per + j] = q;
            }
        }
        o[o.length - 1] = keys[n - 1].clone();
        return o;
    }
    /**
     * The pen along {@code pts}: {@code speed} px/ms, slowed round tight turns ({@code turns} > 0: the power law, radius
     * to the third, {@code turns} px the radius at full speed), sampled 480 a second at ms times, with a tremor and jitter.
     */
    static double[][] walk(double[][] pts, double speed, double shake, double turns) {
        int n = pts.length;
        double[] s = new double[n], t = new double[n];
        for (int i = 1; i < n; i++) s[i] = s[i - 1] + Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]);
        for (int i = 1; i < n; i++) {
            double seg = s[i] - s[i - 1], v = speed;
            if (turns > 0 && i < n - 1) {
                double ax = pts[i][0] - pts[i - 1][0], ay = pts[i][1] - pts[i - 1][1], bx = pts[i + 1][0] - pts[i][0], by = pts[i + 1][1] - pts[i][1];
                double turn = Math.abs(Math.atan2(ax * by - ay * bx, ax * bx + ay * by)), radius = seg / Math.max(1e-6, turn);
                v = speed * Math.max(.3, Math.min(1.5, Math.cbrt(radius / turns)));
            }
            // Slow off the mark and into the lift, as a hand starts and stops.
            v *= .35 + .65 * Math.min(1, Math.min(s[i] / 25, (s[n - 1] - s[i]) / 15));
            t[i] = t[i - 1] + seg / v;
        }
        Random r = new Random(7);
        double ph1 = r.nextDouble() * 6.28, ph2 = r.nextDouble() * 6.28, ph3 = r.nextDouble() * 6.28, ph4 = r.nextDouble() * 6.28;
        double total = t[n - 1], step = 1000 / 480.0;
        int m = (int) Math.floor(total / step);
        double[][] o = new double[m + 1][];
        int j = 0;
        for (int k = 0; k <= m; k++) {
            double at = Math.min(total, k * step);
            while (j < n - 2 && t[j + 1] < at) j++;
            double f = t[j + 1] > t[j] ? (at - t[j]) / (t[j + 1] - t[j]) : 0;
            double x = pts[j][0] + (pts[j + 1][0] - pts[j][0]) * f, y = pts[j][1] + (pts[j + 1][1] - pts[j][1]) * f;
            double sec = at / 1000;
            x += shake * (.7 * Math.sin(2 * Math.PI * 8.7 * sec + ph1) + .3 * Math.sin(2 * Math.PI * 11.3 * sec + ph2)) + .3 * r.nextGaussian();
            y += shake * (.7 * Math.sin(2 * Math.PI * 8.7 * sec + ph3) + .3 * Math.sin(2 * Math.PI * 11.3 * sec + ph4)) + .3 * r.nextGaussian();
            double p = .62 + .08 * Math.sin(sec * 7);
            p *= Math.min(1, .3 + at / 40) * Math.min(1, .25 + (total - at) / 30);
            o[k] = new double[]{x, y, p, 1000 + Math.floor(at)};
        }
        // The pen lifts at the path's end.
        o[m] = new double[]{pts[n - 1][0], pts[n - 1][1], o[m][2], 1000 + Math.floor(total)};
        return o;
    }
}
