package io.github.mpdairy.monopaint;

/**
 * explore-media's (2026-11-01): Dissolves puts a mark where the Water brush dissolves it, and only then; a line with a
 * Paint Load (the Dip nib) runs dry along a long stroke, and starts dark again after a rub on a grey; the three presets
 * read back byte for byte.
 */
public final class MediaChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        dissolves();
        runsDry();
        System.out.println("PASS: Dissolves lays with the washes, so Water melts it and nothing else of a pencil's; a dip nib's line runs dry; White chalk, Dip nib and Water pencil read back as written");
    }
    static void dissolves() {
        ModularBrush pencil = FloatChecks.preset("Water pencil"), plain = FloatChecks.preset("Sketch pencil");
        check(ResolvedBrush.soluble(pencil) && !ResolvedBrush.soluble(plain) && ResolvedBrush.of(pencil, 10, 1).kind == ToneDocument.KIND_PAINT, "the Water pencil dissolves, as paint; the Sketch pencil doesn't");
        double[] wet = watered(pencil), dry = watered(plain);
        check(Math.abs(wet[1] - wet[0]) > 2, "water changes the Water pencil's hatching: " + wet[0] + " to " + wet[1]);
        check(dry[1] == dry[0], "water leaves the Sketch pencil's as it was: " + dry[0] + " to " + dry[1]);
        for (String name : new String[]{"White chalk", "Dip nib", "Water pencil"}) {
            String code = FloatChecks.find(name).code;
            check(ModularCode.encode(ModularCode.decode(code)).equals(code), name + " reads back as written");
        }
    }
    /** The mean tone over the hatching before and after the Water brush crosses it. */
    static double[] watered(ModularBrush b) {
        ToneDocument d = InkWashLayers.blank(300, 200);
        WatercolorSession s = new WatercolorSession();
        double[][] z = new double[801][];
        for (int i = 0; i <= 800; i++) { double u = i / 800.0; z[i] = new double[]{20 + 260 * u, 30 + 140 * Math.abs(((u * 40) % 2) - 1), .8, i * 2}; }
        PresetPictures.stroke(d, s, b, 10, 1, z);
        s.settleNow(d);
        double before = CarrySmudgeChecks.mean(d, 20, 280, 30, 170);
        double[][] w = FloatChecks.line(30, 100, 270, 100, .25);
        PresetPictures.stroke(d, s, FloatChecks.preset("Water"), 40, 1, w);
        s.settleNow(d);
        return new double[]{before, CarrySmudgeChecks.mean(d, 20, 280, 30, 170)};
    }
    static void runsDry() {
        ModularBrush nib = FloatChecks.preset("Dip nib");
        check(ModularWords.runsDry(nib) && ModularWords.plainWords(nib).contains("Runs dry. Rub a grey to dip.") && !ModularWords.runsDry(FloatChecks.preset("Loaded oils")), "the Dip nib runs dry; Loaded oils runs out");
        // One long line back and forth across the page, from the top right: dark where it starts, bare by its last pass.
        ToneDocument d = InkWashLayers.blank(1200, 200);
        WatercolorSession s = new WatercolorSession();
        int n = 4000;
        double[][] p = new double[n + 1][];
        for (int i = 0; i <= n; i++) { double u = i / (double) n; p[i] = new double[]{20 + 1160 * Math.abs(((u * 6) % 2) - 1), 40 + 120 * u, .6, i * 3}; }
        PresetPictures.stroke(d, s, nib, 8, 1, p);
        s.settleNow(d);
        double start = CarrySmudgeChecks.mean(d, 880, 1180, 34, 52), end = CarrySmudgeChecks.mean(d, 20, 1180, 146, 168);
        check(start < 250 && end > 254, "a long line from a dip runs dry: " + start + " at its start, " + end + " at its end");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
