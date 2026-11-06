package io.github.mpdairy.monopaint;

/**
 * Scrape (Smudge's eighth choice, the Palette knife) and Wears Down (a Round or Chisel tip's dial), branch explore-knife:
 * codes round-trip and older codes stay byte for byte; the knife pushes wet paint into ridges, scrapes damp paint pale and
 * barely touches a dry mark; a worn tip broadens across its lean and is sharp again once turned; both draw the same
 * twice, and a worn stroke draws the same whatever came before it (replay needs no state but the stroke's own).
 * Brushes without them draw as before: ToolPresetsChecks' drawn hashes of every Basic tool.
 */
public final class KnifeChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        codes();
        String knife = knife();
        String wear = wear();
        System.out.println("PASS: Scrape is Smudge's eighth choice (_7), seed-only, and Wears Down is written only when on, last of Round's and Chisel's dials; every older code byte for byte, both round-trip; "
                + "the Palette knife " + knife + "; Wears Down " + wear + "; both the same twice, and a worn stroke the same after another (wear is the stroke's own)");
    }

    static void codes() {
        check(PartChoice.SCRAPE.index() == 7 && PartChoice.of(BrushPart.CARRY, 7) == PartChoice.SCRAPE, "Scrape is Smudge's choice 7");
        check(ModularFeatures.seedOnly(PartChoice.SCRAPE) && !ModularFeatures.varies(PartChoice.SCRAPE), "Scrape is never rolled nor swapped to");
        check(PartChoice.ROUND.dials.get(PartChoice.ROUND.dials.size() - 1) == PartDial.WEAR && PartChoice.CHISEL.dials.get(PartChoice.CHISEL.dials.size() - 1) == PartDial.WEAR, "Wears Down is last of Round's and Chisel's");
        for (ToolPresets.Preset p : ToolPresets.withRetired()) {
            ModularBrush b = ModularCode.decode(p.code);
            check(b.part(BrushPart.TIP).get(PartDial.WEAR, 0) == 0 && b.choice(BrushPart.CARRY) != PartChoice.SCRAPE || p.name.equals("Palette knife"), p.name + " neither wears nor scrapes");
            check(ModularCode.encode(b).equals(p.code) || HandHatchChecks.handHatch(p.code), p.name + "'s code reads and writes byte for byte");
        }
        String pencil = "hb-pencil.M40i000000000at_4e551_0_1v5_1jz_8arsr00000000000000nz000000000530000005000c099zz.pg5zxt";
        ModularBrush b = ModularCode.decode(pencil);
        b.part(BrushPart.TIP).set(PartDial.WEAR, .5);
        String worn = ModularCode.encode(b);
        check(!worn.equals(pencil) && ModularCode.encode(ModularCode.decode(worn)).equals(worn) && Math.abs(ModularCode.decode(worn).part(BrushPart.TIP).get(PartDial.WEAR) - .5) < 1e-3, "Wears Down round-trips: " + worn);
        b.part(BrushPart.TIP).set(PartDial.WEAR, 0);
        check(ModularCode.encode(b).equals(pencil), "Wears Down off writes the code as before");
        ModularBrush k = FloatChecks.preset("Palette knife");
        check(k.choice(BrushPart.CARRY) == PartChoice.SCRAPE && ModularBrush.ownPaint(k.part(BrushPart.CARRY)) == 0, "the Palette knife scrapes and lays no paint");
    }

    /** One knife stroke down x = 260 at a 40 degree lean over the wash as {@code page} leaves it; the page. */
    static ToneDocument knifed(int state) {
        WatercolorSession s = new WatercolorSession();
        ToneDocument d = state == 1 ? KnifePictures.restWash(s) : KnifePictures.washed(s, KnifePictures.wetWash(), state == 0 ? 0 : -1);
        KnifePictures.stroke(d, s, "KNIFE", KnifePictures.knife(), 60, .6, KnifePictures.path(260, 30, 260, 310, .75, 40, 0, 40, 0));
        return d;
    }
    static ToneDocument unknifed(int state) {
        WatercolorSession s = new WatercolorSession();
        return state == 1 ? KnifePictures.restWash(s) : KnifePictures.washed(s, KnifePictures.wetWash(), state == 0 ? 0 : -1);
    }
    static String knife() {
        StringBuilder say = new StringBuilder();
        String[] names = {"wet", "damp", "dry"};
        double[] lighter = new double[3], darker = new double[3];
        for (int state = 0; state < 3; state++) {
            ToneDocument d = knifed(state), before = unknifed(state);
            double wash = mean(before, 245, 275, 100, 240), track = mean(d, 252, 268, 100, 240);
            // The darkest column within a few px past either end of the blade (14.5 px from the middle at this lean).
            double ridge = 255;
            for (int x = 270; x <= 282; x++) ridge = Math.min(ridge, Math.min(mean(d, x, x, 100, 240), mean(d, 520 - x, 520 - x, 100, 240)));
            lighter[state] = track - wash;
            darker[state] = wash - ridge;
            say.append(names[state]).append(" track ").append(fixed(track)).append(" (wash ").append(fixed(wash)).append(") ridge ").append(fixed(ridge)).append(state < 2 ? ", " : "");
            if (state == 0) check(lighter[0] > 15 && darker[0] > 15, "wet paint: a lighter track and darker ridges (track " + track + ", ridge " + ridge + ", wash " + wash + ")");
            if (state == 1) check(lighter[1] > 40, "damp paint: scraped pale (track " + track + ", ridge " + ridge + ", wash " + wash + ")");
            if (state == 2) check(Math.abs(lighter[2]) < 8, "a dry mark barely changes: a faint burnish (track " + track + ", wash " + wash + ")");
            check(WetPaintChecks.crc(d).equals(WetPaintChecks.crc(knifed(state))), names[state] + ": the same twice");
        }
        check(lighter[1] > lighter[0], "damp paint scrapes paler than wet");
        check(darker[1] < darker[0], "and piles less of it: a smaller ridge (" + darker[1] + ", wet " + darker[0] + ")");
        return say.toString();
    }

    /** A straight pencil line along y = 110, leaning down the page (across it), turned to lean along it from {@code turnAt} (past 1: never). */
    static double[][] line(double y, double turnAt) {
        int n = 340;
        double[][] p = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double u = (double) i / n, ang = u < turnAt ? Math.PI / 2 : 0;
            p[i] = new double[]{40 + 820 * u, y, .7, i * 4, 45 * Math.cos(ang), 45 * Math.sin(ang)};
        }
        return p;
    }
    static ToneDocument drawn(double wear, double turnAt, boolean before) {
        ModularBrush b = FloatChecks.preset("HB pencil");
        b.part(BrushPart.TIP).set(PartDial.WEAR, wear);
        ToneDocument d = InkWashLayers.blank(900, 260);
        WatercolorSession s = new WatercolorSession();
        if (before) KnifePictures.stroke(d, s, "PENCIL", b, 7, 1, line(220, 2));
        KnifePictures.stroke(d, s, "PENCIL", b, 7, 1, line(110, turnAt));
        return d;
    }
    /** How many rows of column {@code x} (and its neighbours) the line darkens. */
    static double width(ToneDocument d, int x) {
        double n = 0;
        for (int c = x - 3; c <= x + 3; c++) for (int y = 40; y < 200; y++) if (d.compositeTone(c, y) < 235) n++;
        return n / 7;
    }
    static String wear() {
        ToneDocument off = drawn(0, 2, false), worn = drawn(1, 2, false), turned = drawn(1, .6, false);
        double early = width(worn, 50), late = width(worn, 800), plain = width(off, 800), sharp = width(turned, 800);
        check(early < 1.15 * width(off, 50), "a worn tip starts as sharp as an unworn one (" + early + ", " + width(off, 50) + ")");
        check(late > 1.8 * plain, "worn down, the line is broader across the lean (" + late + " to " + plain + ")");
        check(sharp < 1.3 * plain, "turned, a sharp edge again (" + sharp + " to " + plain + ")");
        check(WetPaintChecks.crc(worn).equals(WetPaintChecks.crc(drawn(1, 2, false))), "a worn line is the same twice");
        // The same stroke after another with the same brush: its own rows the same (no wear carried from stroke to stroke).
        ToneDocument after = drawn(1, 2, true);
        for (int y = 40; y < 180; y++) for (int x = 0; x < 900; x++) check(after.compositeTone(x, y) == worn.compositeTone(x, y), "a worn stroke draws the same after another, at " + x + ", " + y);
        return "rows darkened " + fixed(early) + " at the start, " + fixed(late) + " worn (unworn " + fixed(plain) + "), " + fixed(sharp) + " turned";
    }

    static double mean(ToneDocument d, int x0, int x1, int y0, int y1) { return CarrySmudgeChecks.mean(d, x0, x1, y0, y1); }
    static String fixed(double v) { return PartDial.fixed(v, 1); }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
