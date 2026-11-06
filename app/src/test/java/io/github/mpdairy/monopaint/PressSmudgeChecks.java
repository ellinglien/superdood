package io.github.mpdairy.monopaint;

import java.util.Random;

/**
 * The smudge tool's two (the owner, 2026-10-30: "just TWO tools, each using pen pressure the way real media does"):
 * Smudge's Press ({@link PartDial#PRESS}), Smear's Press to Push and Clear Water's Press to Lift, written only when on
 * and never rolled, every older code byte for byte; Blend (the Blend stump with Press to Push) softening in place at a
 * light touch and pushing paint along at a firm one, Water (the Water brush with Press to Lift) re-wetting paint at a
 * light touch, so it runs, and blotting it off at a firm one; their words.
 */
public class PressSmudgeChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        codes(); rolls(); words(); blend(); water();
        System.out.println("PASS: Press is written only when on and never rolled, every older code byte for byte, round-trips; Blend softens at a light touch as the Blend stump, pushes at a firm one as a Smear "
                + "(" + blendSays + "); Water re-wets at a light touch, so what it lifts runs, and blots at a firm one (" + waterSays + ")");
    }
    static final int W = 480, H = 300;
    /** Pressure travel: the owner's light strokes (HoverNib.LIGHT), a hard press (docs/TILT_INPUT.md). */
    static final double LIGHT = .12, FIRM = .95;
    static ModularBrush preset(String name) { return FloatChecks.preset(name); }
    static String blendSays = "", waterSays = "";

    static void codes() {
        // The older smudges' codes read Press off and write as they were; Blend and Water are them with Press 1, its digits last.
        for (String name : new String[]{"Blend stump", "Stump", "Wash comb", "Dirty brush", "Water brush", "Damp tissue", "Gouache water", "Clear water", "Comb"}) {
            ToolPresets.Preset p = FloatChecks.find(name);
            ModularBrush b = ModularCode.decode(p.code);
            check(b.part(BrushPart.CARRY).get(PartDial.PRESS, 0) == 0 && ModularCode.encode(b).equals(p.code), name + " reads Press off and writes byte for byte");
        }
        String stump = FloatChecks.find("Blend stump").code, brush = FloatChecks.find("Water brush").code;
        String blend = ToolKits.preset("Blend").code, water = ToolKits.preset("Water").code;
        check(blend.equals(stump.replace("blend-stump.", "blend.").replace("_3ssll00zz_", "_3ssll00zz000090zz_")), "Blend is the Blend stump with Press to Push 1, written after Starting Load: " + blend);
        check(water.equals(brush.replace("water-brush.", "water.").replace("_4i0ll_", "_4i0llzz_")), "Water is the Water brush with Press to Lift 1: " + water);
        // Any value round-trips, on Smear and on Clear Water; at 0 it isn't written.
        for (String code : new String[]{stump, brush}) for (double v : new double[]{0, .37, 1}) {
            ModularBrush b = ModularCode.decode(code);
            b.part(BrushPart.CARRY).set(PartDial.PRESS, v);
            String c = ModularCode.encode(b);
            ModularBrush back = ModularCode.decode(c);
            check(Math.abs(back.part(BrushPart.CARRY).get(PartDial.PRESS) - v) < 1e-3 && ModularCode.encode(back).equals(c) && (v > 0 || c.equals(code)), "Press " + v + " round-trips: " + c);
        }
        // Drag and Lift have no Press.
        check(!PartChoice.DRAG.has(PartDial.PRESS) && !PartChoice.LIFT.has(PartDial.PRESS), "only Smear and Clear Water have Press");
    }
    static void rolls() {
        for (ModularStyle s : ModularStyle.all()) for (int i = 0; i < 300; i++) {
            ModularBrush b = s.roll(new Random(4400L * (s.index + 1) + i));
            check(b.part(BrushPart.CARRY).get(PartDial.PRESS, 0) == 0, s.name + " never rolls Press");
        }
    }
    static void words() {
        ModularBrush blend = preset("Blend"), water = preset("Water");
        String b = ModularWords.plainWords(blend), w = ModularWords.plainWords(water);
        check(b.contains("Softens, pushes when pressed.") && w.contains("Re-wets paint, lifts it when pressed.") && !ModularWords.plainWords(preset("Blend stump")).contains("pressed"), "the facts: " + b + " / " + w);
        String sb = BrushVoice.about(blend), sw = BrushVoice.about(water);
        check(sb.contains("pressing harder pushes them along") && sw.contains("pressing harder lifts it off") && BrushVoice.MISSED.isEmpty(), "This brush: " + sb + " / " + sw);
        check(PartChoice.SMUDGE.labelOf(PartDial.PRESS).equals("Press to Push") && PartChoice.CLEAR_WATER.labelOf(PartDial.PRESS).equals("Press to Lift"), "the dial's labels");
    }

    /** A page with {@code tone} on {@code layer}, and {@code b} across it left to right at {@code travel}, at 40 px. */
    static ToneDocument stroked(int layer, java.util.function.IntBinaryOperator tone, ModularBrush b, double travel, double x0, double x1) {
        ToneDocument d = CarrySmudgeChecks.page(layer, tone);
        WatercolorSession s = new WatercolorSession();
        WetSmudgeChecks.stroke(d, s, "SMUDGE", b, 40, .5, FloatChecks.line(x0, 150, x1, 150, travel));
        wetTicks = 0;
        while (s.tickDrying(d)) wetTicks++;
        return d;
    }
    /** How many drying ticks the last {@link #stroked} page stayed wet for after its lift. */
    static int wetTicks;
    static boolean same(ToneDocument a, ToneDocument b) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (a.compositeTone(x, y) != b.compositeTone(x, y)) return false;
        return true;
    }
    static double mean(ToneDocument d, int x0, int x1, int y0, int y1) { return CarrySmudgeChecks.mean(d, x0, x1, y0, y1); }
    /** How much the tone steps from pixel to pixel along row band {@code y0..y1}, {@code x0..x1}: the hatching's edges. */
    static double edges(ToneDocument d, int x0, int x1, int y0, int y1) {
        double s = 0; int n = 0;
        for (int y = y0; y <= y1; y++) for (int x = x0; x < x1; x++) { s += Math.abs(d.compositeTone(x + 1, y) - d.compositeTone(x, y)); n++; }
        return s / n;
    }

    /**
     * Blend over ink hatching (x 100 to 300) that ends on paper: light, it is the Blend stump to the bit (soften in place,
     * the darkness kept where it was); firm, a Smear (pushing what it crosses on past the hatching onto the paper).
     */
    static void blend() {
        java.util.function.IntBinaryOperator hatch = (x, y) -> x >= 100 && x < 300 && Math.floorMod(x, 12) < 3 ? 60 : 255;
        ModularBrush blend = preset("Blend"), stump = preset("Blend stump"), smear = stump.copy();
        smear.part(BrushPart.CARRY).set(PartDial.SOFTEN, 0);
        ToneDocument before = CarrySmudgeChecks.page(InkWashLayers.INK, hatch);
        ToneDocument light = stroked(InkWashLayers.INK, hatch, blend, LIGHT, 60, 360), firm = stroked(InkWashLayers.INK, hatch, blend, FIRM, 60, 360);
        check(same(light, stroked(InkWashLayers.INK, hatch, stump, LIGHT, 60, 360)), "Blend at a light touch draws the Blend stump's, pixel for pixel");
        check(same(firm, stroked(InkWashLayers.INK, hatch, smear.normalize(), FIRM, 60, 360)), "Blend at a firm press draws a Smear's (the Blend stump at Soften 0), pixel for pixel");
        // Softened in place: the hatching's edges fall, its darkness stays in the hatching; pushed: carried past its end.
        double e0 = edges(before, 120, 280, 145, 155), eL = edges(light, 120, 280, 145, 155), eF = edges(firm, 120, 280, 145, 155);
        double m0 = mean(before, 120, 280, 145, 155), mL = mean(light, 120, 280, 145, 155);
        double pL = 255 - mean(light, 310, 350, 140, 160), pF = 255 - mean(firm, 310, 350, 140, 160);
        blendSays = String.format("edges %.1f, light %.1f, firm %.1f; light keeps the hatching's tone %.1f of %.1f; past its end light %.1f, firm %.1f darker", e0, eL, eF, mL, m0, pL, pF);
        check(eL < .8 * e0 && Math.abs(mL - m0) < 6, "a light Blend softens the hatching in place: " + blendSays);
        check(pF > 4 * Math.max(1, pL) && pF > 8, "a firm Blend pushes the hatching on past its end: " + blendSays);
    }

    /**
     * Water out of a dried wash (x under 240) onto paper: light, it re-wets, so what it lifts runs out onto the paper
     * past the wash; firm, it blots, the track paler and nothing laid past the wash.
     */
    static void water() {
        java.util.function.IntBinaryOperator wash = (x, y) -> x < 240 ? 130 : 255;
        ModularBrush water = preset("Water"), brush = preset("Water brush");
        ToneDocument light = stroked(InkWashLayers.WASH, wash, water, LIGHT, 120, 380);
        int wetLight = wetTicks;
        ToneDocument firm = stroked(InkWashLayers.WASH, wash, water, FIRM, 120, 380);
        int wetFirm = wetTicks;
        check(wetLight > 0 && wetFirm < wetLight, "a light Water leaves the paint wet (" + wetLight + " ticks), a firm one blots it (" + wetFirm + ")");
        ToneDocument old = stroked(InkWashLayers.WASH, wash, brush, LIGHT, 120, 380);
        double tL = mean(light, 150, 225, 145, 155), tF = mean(firm, 150, 225, 145, 155), tO = mean(old, 150, 225, 145, 155);
        double rL = 255 - mean(light, 244, 290, 140, 160), rF = 255 - mean(firm, 244, 290, 140, 160), rO = 255 - mean(old, 244, 290, 140, 160);
        waterSays = String.format("track %.1f light, %.1f firm (wash 130); run out past it %.1f light, %.1f firm darker; the Water brush light %.1f, %.1f", tL, tF, rL, rF, tO, rO);
        check(rL > 2 && rF < .25 * rL, "a light Water re-wets, what it lifts running out past the wash; a firm one blots: " + waterSays);
        check(tF > tL + 15 && tF > 200, "a firm Water lifts the track paler: " + waterSays);
        check(Math.abs(tL - tO) < 6 && Math.abs(rL - rO) < .35 * rO + 1, "a light Water is near the Water brush: " + waterSays);
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
