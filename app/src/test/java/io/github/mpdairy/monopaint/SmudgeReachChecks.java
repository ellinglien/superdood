package io.github.mpdairy.monopaint;

/**
 * What the smudge tool's two move (the owner, 2026-11-01: "blend doesn't seem to work as a smudge tool... water did work
 * however", "it only seems to work on certain things?"): each first toolbar brush ({@link ToolSlots#FIRST}) draws a line
 * at its own size and grey as the app starts a stroke ({@link WetSmudgeChecks#stroke}), 2.5 s of drying pass, and Blend,
 * Water or Blur crosses it with a pen's pressure (rising, held, let go); the pixels it changed, counted against the same page
 * left alone, say whether it moved the mark. Blend moves dry media, washes and opaque paint (a stump's other half,
 * {@link ModularStroke#alsoWorks}); pen ink and printed dots stay crisp. Water lifts washes only. Blur, a stump of Ink ({@link ModularStroke#blursAll}),
 * blurs every mark (the owner: "a simple blur might be good as a third option?").
 */
public class SmudgeReachChecks {
    static final int W = 480, H = 300;
    static final double SMUDGE_PX = 40, WAIT_S = 2.5;
    /** More changed pixels than this is moved; none at all is left alone. */
    static final int MOVED = 300;
    /** The smudge tool's choices ({@link ToolSlots#SMUDGE_TOOLS}), in {@link #MOVES}' order. */
    static final String[] SMUDGES = {"Blend", "Water", "Blur"};
    /** Each first brush's key, and whether Blend, Water and Blur move its mark. */
    static final Object[][] MOVES = {
            {"PENCIL", true, false, true}, {"STEADY", false, false, true}, {"FELT", false, false, true}, {"FUDE", false, false, true},
            {"WASH", true, true, true}, {"WATERCOLOUR", true, true, true}, {"OIL", true, false, true},
            {"BENDAY", false, false, true}, {"HIGHLIGHTER", true, true, true}};

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        check(MOVES.length == ToolSlots.FIRST.length && java.util.Arrays.equals(SMUDGES, ToolSlots.SMUDGE_TOOLS), "a row for each first brush, a column for each smudge");
        StringBuilder table = new StringBuilder();
        for (String[] t : ToolSlots.FIRST) {
            Object[] want = null;
            for (Object[] m : MOVES) if (m[0].equals(t[0])) want = m;
            check(want != null, "a row for " + t[0]);
            table.append(t[1]);
            for (int i = 0; i < SMUDGES.length; i++) {
                int n = moved(t, SMUDGES[i]);
                table.append(' ').append(SMUDGES[i]).append(' ').append(n);
                check((boolean) want[i + 1] ? n > MOVED : n == 0, t[1] + ": " + SMUDGES[i] + (n > 0 ? " moves it, " : " leaves it, ") + n + " px");
            }
            table.append("; ");
        }
        System.out.println("PASS: Blend moves pencil, washes and oils, not pen ink or printed dots; Water lifts washes only; Blur moves every mark (" + table.toString().trim() + ")");
    }

    /** How many pixels {@code smudge} changes across first brush {@code t}'s line, against the page left alone. */
    static int moved(String[] t, String smudge) {
        ToneDocument alone = page(t, null), smudged = page(t, smudge);
        int n = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (Math.abs(alone.compositeTone(x, y) - smudged.compositeTone(x, y)) > 2) n++;
        return n;
    }
    /** {@code t}'s line, 2.5 s of drying, then {@code smudge} across it (none: as long without), then drying to the end. */
    static ToneDocument page(String[] t, String smudge) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        WetSmudgeChecks.stroke(d, s, t[0], LegacyBrushes.named(t), Double.parseDouble(t[3]), Double.parseDouble(t[4]), pen(60, 150, 420, 150));
        for (int i = 0; i < WAIT_S * DryingWater.TPS && s.tickDrying(d); i++) { }
        if (smudge != null) WetSmudgeChecks.stroke(d, s, ToolSlots.SMUDGE_KEY, ToolKits.preset(smudge).brush().parts, SMUDGE_PX, .5, pen(240, 40, 240, 260));
        for (int i = 0; i < 600 && s.tickDrying(d); i++) { }
        return d;
    }
    /** A pen's line: pressure travel rising over its first 30 px to a usual press, held with a little waver, let go over its last 30; a sample every 2.5 px, 4 ms apart. */
    static double[][] pen(double x0, double y0, double x1, double y1) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        int n = (int) Math.ceil(len / 2.5);
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double u = (double) i / n, along = u * len, ramp = Math.min(1, Math.min(along, len - along) / 30);
            o[i] = new double[]{x0 + (x1 - x0) * u, y0 + (y1 - y0) * u, (.08 + .5 * ramp) * (1 + .06 * Math.sin(along / 17)), i * 4};
        }
        return o;
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
