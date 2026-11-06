package io.github.mpdairy.monopaint;

import java.util.Arrays;

/**
 * Steady Hand and Long Stick ({@link SteadyHand}): the tip lands where the pen lifted (no lost tail), the same samples
 * give the same tip and pixels, off is the stroke itself (every brush without them draws as before), the dials are
 * Control's last and written only when on (every older code byte for byte), a mirror's copy is the tip's path moved,
 * a quick stroke ends as the live one, and the lag: how far and how long the line trails the pen, printed.
 */
public final class SteadyHandChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        landsAtLift(); deterministic(); offIsTheStroke(); codes(); mirrored(); quickAsLive(); speedDecides(); stickSwings(); lag();
        System.out.println("PASS: Steady Hand and Long Stick: the line ends where the pen lifted, a tap stays a dot; the same samples draw the same tip and pixels; off, the brush's own stroke; Control's last two dials, written only when on, every older code byte for byte, round-trip; a mirror's copy is the tip's path mirrored; quick ends as live; Steady Hand smooths a slow hand's wobble more than a fast one's and trails it at most 14 screen px; Long Stick overshoots a stop, Steady Hand never");
    }

    /** Pen samples {x, y, pressure, time ms} at 480 a second, ms times: a line at {@code speed} px/ms from (x0, y0) to (x1, y1). */
    static double[][] line(double x0, double y0, double x1, double y1, double speed) {
        double len = Math.hypot(x1 - x0, y1 - y0), ms = len / speed;
        int n = (int) Math.ceil(ms / (1000 / 480.0));
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) { double u = (double) i / n; o[i] = new double[]{x0 + (x1 - x0) * u, y0 + (y1 - y0) * u, .6, Math.floor(1000 + u * ms)}; }
        return o;
    }
    /** {@code path}'s samples through a tip of {@code steady} and {@code stick}, then its landing: {x, y, pressure, time}. */
    static double[][] tip(double steady, double stick, double[][] path) { return tip(steady, stick, path, true); }
    static double[][] tip(double steady, double stick, double[][] path, boolean land) {
        SteadyHand.Follower f = new SteadyHand.Follower(steady, stick);
        PenBatch out = new PenBatch();
        for (double[] p : path) f.feed((float) p[0], (float) p[1], (float) p[2], Float.NaN, Float.NaN, p.length > 3 ? (long) p[3] : PenSpeed.UNTIMED, out);
        if (land) f.land(out);
        double[][] o = new double[out.size()][];
        for (int i = 0; i < out.size(); i++) o[i] = new double[]{out.x(i), out.y(i), out.pressure(i), out.time(i)};
        return o;
    }

    static void landsAtLift() {
        double[][][] paths = {line(40, 40, 400, 300, .25), line(40, 40, 400, 300, 3), spiral(.3), spiral(2), SteadyHandPictures.handwriting(.5, 1), SteadyHandPictures.handwriting(1.6, 1)};
        for (double[] dials : new double[][]{{.5, 0}, {1, 0}, {0, .5}, {0, 1}, {.6, 1}})
            for (double[][] path : paths) {
                double[][] t = tip(dials[0], dials[1], path);
                double[] end = t[t.length - 1], pen = path[path.length - 1];
                check((float) end[0] == (float) pen[0] && (float) end[1] == (float) pen[1] && (float) end[2] == (float) pen[2],
                        "the tip lands exactly where the pen lifted, at its pressure: " + Arrays.toString(dials));
                for (int i = 1; i < t.length; i++) check(t[i][3] >= t[i - 1][3], "its times never run backward");
                check(t[0][0] == (float) path[0][0] && t[0][1] == (float) path[0][1], "and starts where the pen did");
            }
        // Drawn: the ink reaches the pen's last sample (a fineliner, whose line ends there).
        for (double[] dials : new double[][]{{0, 0}, {1, 0}, {0, 1}}) {
            ToneDocument d = InkWashLayers.blank(480, 360);
            CarrySmudgeChecks.feed(QuickStroke.start(d, with(fineliner(), dials[0], dials[1]), 8, 1, new WatercolorSession()), line(40, 180, 300, 180, 1.5));
            check(d.compositeTone(300, 180) < 128 && d.compositeTone(296, 180) < 128, "the drawn line reaches the lift: " + Arrays.toString(dials));
        }
        // A tap is a dot where the pen touched.
        double[][] tap = tip(1, 1, new double[][]{{100, 100, .5, 1000}});
        check(tap.length >= 1 && tap[tap.length - 1][0] == 100 && tap[tap.length - 1][1] == 100, "a tap stays where it touched");
    }

    static void deterministic() {
        double[][] w = SteadyHandPictures.handwriting(.6, 1);
        for (double[] dials : new double[][]{{.5, 0}, {0, 1}}) {
            check(Arrays.deepEquals(tip(dials[0], dials[1], w), tip(dials[0], dials[1], w)), "the same samples, the same tip");
            byte[][] a = drawn(with(fineliner(), dials[0], dials[1]), w), b = drawn(with(fineliner(), dials[0], dials[1]), w);
            check(ModularStrokeChecks.same(a, b), "and the same pixels");
        }
        check(!ModularStrokeChecks.same(drawn(with(fineliner(), 0, 1), w), drawn(fineliner(), w)), "Long Stick changes the line");
    }

    /** Off: the brush's stroke is the stroke itself, so its pixels are as before; every preset is off. */
    static void offIsTheStroke() {
        for (ToolPresets.Preset p : ToolPresets.withRetired()) {
            ModularBrush b = p.brush().parts;
            if (p.name.equals(SteadyHandPictures.LONG_STICK)) continue;
            ModularBrush.Part hand = b.part(BrushPart.HAND);
            check(hand.get(PartDial.STEADY_HAND, 0) == 0 && hand.get(PartDial.LONG_STICK, 0) == 0, "every other preset has neither: " + p.name);
            DrawingStroke s = ModularStroke.start(InkWashLayers.blank(64, 64), b, 10, 1, null);
            check(SteadyHand.of(b, s) == s, "so its stroke is its own: " + p.name);
        }
        check(QuickStroke.start(InkWashLayers.blank(64, 64), fineliner(), 10, 1, null) instanceof ModularStroke, "a fineliner's stroke starts as before");
        check(QuickStroke.start(InkWashLayers.blank(64, 64), with(fineliner(), .5, 0), 10, 1, null) instanceof SteadyHand, "with Steady Hand, through its tip");
        ModularBrush spiro = SteadyHandPictures.preset("Spirograph");
        DrawingStroke s = ModularStroke.start(InkWashLayers.blank(64, 64), with(spiro, 1, 1), 10, 1, null);
        check(SteadyHand.of(with(spiro, 1, 1), s) == s, "never a Spiro's, whose ring is set where the pen is");
    }

    static void codes() {
        for (ToolPresets.Preset p : ToolPresets.withRetired()) {
            if (p.name.equals(SteadyHandPictures.LONG_STICK)) continue;
            // Hand Hatch (retired, tip choice 12) reads as Hatch, so its codes never wrote back as they were.
            if (ModularCode.decode(p.code).choice(BrushPart.TIP) == PartChoice.HATCH && p.code.matches("[^.]*\\.M.c.*")) continue;
            check(ModularCode.encode(ModularCode.decode(p.code)).equals(p.code), "every older code reads and writes byte for byte: " + p.name);
        }
        ModularBrush b = with(fineliner(), .5, .75);
        String code = ModularCode.encode(b);
        ModularBrush back = ModularCode.decode(code);
        ModularBrush.Part hand = back.part(BrushPart.HAND);
        check(Math.abs(hand.get(PartDial.STEADY_HAND) - .5) < 1e-3 && Math.abs(hand.get(PartDial.LONG_STICK) - .75) < 1e-3 && ModularCode.encode(back).equals(code) && !ModularCode.newer(code),
                "Steady Hand and Long Stick round-trip: " + code);
        String old = ModularCode.encode(fineliner());
        check(code.length() > old.length() && ModularCode.decode(old).part(BrushPart.HAND).get(PartDial.STEADY_HAND) == 0, "an older code reads them as off");
        ModularBrush stick = with(fineliner(), 0, .75);
        check(ModularCode.encode(stick).length() > old.length() && ModularCode.decode(ModularCode.encode(stick)).part(BrushPart.HAND).get(PartDial.STEADY_HAND) == 0, "Long Stick alone writes Steady Hand as off");
        ModularBrush swapped = fineliner().copy();
        swapped.set(BrushPart.HAND, ModularBrush.switchTo(with(fineliner(), .5, .75).part(BrushPart.HAND), PartChoice.PRESS_BOTH));
        check(swapped.part(BrushPart.HAND).get(PartDial.LONG_STICK) == .75, "a Control swap keeps them: " + swapped.part(BrushPart.HAND).get(PartDial.LONG_STICK));
    }

    /** The tip is isotropic: a copy's samples mirrored and turned give the tip's path mirrored and turned. */
    static void mirrored() {
        double[][] w = SteadyHandPictures.handwriting(1, 1);
        for (double[] dials : new double[][]{{1, 0}, {0, 1}}) {
            double[][] a = tip(dials[0], dials[1], w), m = new double[w.length][];
            for (int i = 0; i < w.length; i++) m[i] = new double[]{600 - w[i][1], 600 - w[i][0], w[i][2], w[i][3]};
            double[][] b = tip(dials[0], dials[1], m);
            check(a.length == b.length, "as many samples mirrored");
            double worst = 0;
            for (int i = 0; i < a.length; i++) worst = Math.max(worst, Math.hypot(600 - a[i][1] - b[i][0], 600 - a[i][0] - b[i][1]));
            check(worst < .01, "the tip's path mirrored: " + worst);
        }
    }

    /** A quick stroke (preview, render at lift) ends as the live one, the tip's samples to both. */
    static void quickAsLive() {
        double[][] w = SteadyHandPictures.handwriting(.8, 1);
        ModularBrush b = with(SteadyHandPictures.preset("Soft charcoal"), .3, 1);
        byte[][][] got = new byte[2][][];
        for (int pass = 0; pass < 2; pass++) {
            QuickStroke.benchAlways = pass == 1;
            try {
                ToneDocument d = InkWashLayers.blank(480, 360);
                DrawingStroke s = QuickStroke.start(d, b, 30, 1, new WatercolorSession());
                check(s instanceof SteadyHand && (((SteadyHand) s).inner instanceof QuickStroke) == (pass == 1), "the tip in front of " + (pass == 1 ? "a quick stroke" : "a live one"));
                CarrySmudgeChecks.feed(s, w);
                got[pass] = ModularStrokeChecks.layers(d);
            } finally { QuickStroke.benchAlways = null; }
        }
        check(ModularStrokeChecks.same(got[0], got[1]), "quick ends as live");
    }

    /** Hand tremor (9 Hz, 1.5 px) on a line at {@code speed}: how much of it the tip keeps, 0 to 1. */
    static double kept(double steady, double speed) {
        double[][] pen = line(0, 200, 4000 * speed / 3, 200, speed);
        for (double[] p : pen) p[1] += 1.5 * Math.sin(2 * Math.PI * 9 * (p[3] - 1000) / 1000);
        double[][] t = tip(steady, 0, pen, false);
        double most = 0;
        for (int i = t.length / 3; i < t.length; i++) most = Math.max(most, Math.abs(t[i][1] - 200));
        return most / 1.5;
    }
    /** How far the tip trails a pen moving at {@code speed} in a straight line, page px, once settled. */
    static double trails(double steady, double stick, double speed) {
        double[][] pen = line(0, 0, Math.max(400, 3000 * speed), 0, speed);
        double[][] t = tip(steady, stick, pen, false);
        double[] p = pen[pen.length - 1], q = t[t.length - 1];
        return Math.hypot(p[0] - q[0], p[1] - q[1]);
    }
    static void speedDecides() {
        for (double s : new double[]{.5, 1}) {
            check(kept(s, .2) < kept(s, 2), "a slow hand's wobble is smoothed more than a fast one's: " + kept(s, .2) + " " + kept(s, 2));
            for (double v : new double[]{.1, .3, 1, 3, 8}) check(trails(s, 0, v) < SteadyHand.MOST_PX * s + .5, "Steady Hand trails under " + SteadyHand.MOST_PX * s + " px at " + v + " px/ms: " + trails(s, 0, v));
            check(trails(s, 0, .2) / .2 > trails(s, 0, 3) / 3, "its lag time is longer slow than fast");
        }
    }
    /** A fast line that stops dead: Long Stick's tip runs on past the stop, Steady Hand's never. */
    static void stickSwings() {
        double[][] pen = line(0, 0, 300, 0, 2);
        double[][] hold = new double[pen.length + 200][];
        System.arraycopy(pen, 0, hold, 0, pen.length);
        for (int i = 0; i < 200; i++) hold[pen.length + i] = new double[]{300, 0, .6, pen[pen.length - 1][3] + 2 * (i + 1)};
        for (double[] dials : new double[][]{{1, 0}, {.5, 0}, {0, .5}, {0, 1}}) {
            double past = 0;
            for (double[] q : tip(dials[0], dials[1], hold)) past = Math.max(past, q[0] - 300);
            if (dials[1] == 0) check(past < .05, "Steady Hand never overshoots: " + past);
            else check(past > 10 * dials[1], "Long Stick swings past a stop: " + past + " px at " + dials[1]);
        }
    }
    /** The lag, printed: how far (page px, the Manta's screen px at 100%) and how long the line trails the pen. */
    static void lag() {
        StringBuilder s = new StringBuilder("Steady Hand lag, px (ms) at 0.1 / 0.3 / 1 / 3 px/ms:");
        for (double[] dials : new double[][]{{.5, 0}, {1, 0}, {0, .5}, {0, 1}}) {
            s.append(dials[1] > 0 ? "\n  Long Stick " + dials[1] + ":" : "\n  Steady " + dials[0] + ":");
            for (double v : new double[]{.1, .3, 1, 3}) { double px = trails(dials[0], dials[1], v); s.append(String.format(java.util.Locale.ROOT, " %.1f (%.0f)", px, px / v)); }
        }
        s.append(String.format(java.util.Locale.ROOT, "\n  tremor kept (9 Hz): Steady .5 slow %.2f fast %.2f, Steady 1 slow %.2f fast %.2f", kept(.5, .2), kept(.5, 2), kept(1, .2), kept(1, 2)));
        System.out.println(s);
    }

    static double[][] spiral(double speed) { return SteadyHandPictures.spiral(240, 180, 150, speed, 0); }
    static ModularBrush fineliner() { return SteadyHandPictures.preset("Steady line"); }
    static ModularBrush with(ModularBrush b, double steady, double stick) { return SteadyHandPictures.with(b, steady, stick); }
    static byte[][] drawn(ModularBrush b, double[][] path) {
        ToneDocument d = InkWashLayers.blank(480, 360);
        CarrySmudgeChecks.feed(QuickStroke.start(d, b, 8, 1, new WatercolorSession()), path);
        return ModularStrokeChecks.layers(d);
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
