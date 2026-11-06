package io.github.mpdairy.monopaint;

/**
 * Reprint (Smudge's ninth choice, branch explore-reprint), with its two Basic tools, the Photocopier and Newsprint: codes
 * round-trip and every older code stays byte for byte; a reprint over a page of pen, pencil and wash is the same every
 * time, changes all three, and only where it passed; back and forth over the same place it reprints once; a light touch
 * reprints part way, a usual one all the way; Undo takes the whole stroke back in one step, every layer; without a
 * Print Shading it prints toner's black and white; over wet paint the paint stays wet and takes the reprint. And what a
 * big one costs. {@code older} lists codes for the version before it (OlderLaterCheck, scripts/test_inkwash.sh).
 */
public final class ReprintChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length > 0 && args[0].equals("older")) { for (String[] t : older()) System.out.println(String.join("\t", t)); return; }
        codes();
        String same = page();
        String once = backAndForth();
        String press = pressure();
        undo();
        String toner = toner();
        String white = paperStaysWhite();
        String wet = wet();
        String cost = cost();
        System.out.println("PASS: Reprint is Smudge's ninth choice (_8), no dials, seed-only, lays no paint of its own; every older code byte for byte, its codes round-trip; "
                + "over pen, pencil and a wash, both layers, " + same + "; " + once + "; " + press + "; one Undo step takes it all back; without a Print Shading " + toner
                + "; " + white + "; " + wet + "; " + cost);
    }

    static ModularBrush photocopier() { return FloatChecks.preset("Photocopier"); }

    static void codes() {
        check(PartChoice.REPRINT.index() == 8 && PartChoice.of(BrushPart.CARRY, 8) == PartChoice.REPRINT && PartChoice.REPRINT.dials.isEmpty(), "Reprint is Smudge's choice 8, no dials");
        check(ModularFeatures.seedOnly(PartChoice.REPRINT) && !ModularFeatures.varies(PartChoice.REPRINT), "Reprint is never rolled nor swapped to");
        for (ToolPresets.Preset p : ToolPresets.withRetired()) {
            ModularBrush b = ModularCode.decode(p.code);
            check(b.choice(BrushPart.CARRY) != PartChoice.REPRINT || p.name.equals("Photocopier") || p.name.equals("Newsprint"), p.name + " doesn't reprint");
            check(ModularCode.encode(b).equals(p.code) || HandHatchChecks.handHatch(p.code), p.name + "'s code reads and writes byte for byte");
        }
        for (String name : new String[]{"Photocopier", "Newsprint"}) {
            ModularBrush b = FloatChecks.preset(name);
            check(b.choice(BrushPart.CARRY) == PartChoice.REPRINT && ModularBrush.ownPaint(b.part(BrushPart.CARRY)) == 0, name + " reprints and lays no paint");
            ResolvedBrush r = ResolvedBrush.of(b, 120, 1);
            check(r.screen == null && r.carry.reprint() && r.carry.print != null && !r.line && r.body == null, name + ": its Print Shading is what it reprints in, not a mark of its own");
            String code = ModularCode.encode(b);
            check(code.contains("_8_") && ModularCode.encode(ModularCode.decode(code)).equals(code) && !ModularCode.newer(code), name + " round-trips: " + code);
        }
        // Any brush made to reprint round-trips, and set back to Off writes the code it had.
        ModularBrush pen = FloatChecks.preset("Steady line");
        String before = ModularCode.encode(pen);
        pen.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.REPRINT));
        String code = ModularCode.encode(pen);
        check(!code.equals(before) && ModularCode.encode(ModularCode.decode(code)).equals(code), "a pen made to reprint round-trips: " + code);
        pen.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
        check(ModularCode.encode(pen).equals(before), "and set back to Off, its code as before");
    }

    /** Codes with Reprint, for the version before it: {code, what it writes back, "newer"}: Smudge at its fresh Off, the rest as written. */
    static java.util.List<String[]> older() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        ModularBrush pen = FloatChecks.preset("Steady line");
        pen.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.REPRINT));
        for (ModularBrush b : new ModularBrush[]{photocopier(), FloatChecks.preset("Newsprint"), pen}) {
            ModularBrush off = b.copy();
            off.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
            out.add(new String[]{ModularCode.encode(b), ModularCode.encode(off), "newer"});
        }
        return out;
    }

    /** ReprintPictures' page, reprinted with {@code b} down a band at x 245 to 415 at pressure {@code p}. */
    static ToneDocument band(ModularBrush b, double p) {
        WatercolorSession s = new WatercolorSession();
        ToneDocument d = ReprintPictures.page(s);
        KnifePictures.stroke(d, s, "REPRINT", b, ReprintPictures.SIZE, 1, ReprintPictures.band(245, 170, p, ReprintPictures.SIZE));
        return d;
    }
    static ToneDocument before() { return ReprintPictures.page(new WatercolorSession()); }
    /** How many pixels of layer {@code l}'s plane for {@code kinds} differ in columns {@code x0..x1}. */
    static int changed(ToneDocument a, ToneDocument b, int l, int kinds, int x0, int x1) {
        int[] ta = new int[a.width], ca = new int[a.width], tb = new int[a.width], cb = new int[a.width];
        int n = 0;
        for (int y = 0; y < a.height; y++) {
            a.layerPixels(l, kinds, 0, y, a.width, 1, ta, ca); b.layerPixels(l, kinds, 0, y, a.width, 1, tb, cb);
            for (int x = x0; x <= x1; x++) if (ta[x] != tb[x] || ca[x] != cb[x]) n++;
        }
        return n;
    }
    static String page() {
        ToneDocument was = before(), d = band(photocopier(), .75);
        check(WetPaintChecks.crc(d).equals(WetPaintChecks.crc(band(photocopier(), .75))), "a reprint is the same twice");
        int pen = changed(was, d, InkWashLayers.INK, ToneDocument.KIND_PEN, 245, 415), pencil = changed(was, d, InkWashLayers.INK, ToneDocument.KIND_PENCIL, 245, 415),
                wash = changed(was, d, InkWashLayers.WASH, ToneDocument.KIND_PAINT, 245, 415);
        check(pen > 50 && pencil > 500 && wash > 10000, "it reprints the pen (" + pen + " px), the pencil (" + pencil + ") and the wash (" + wash + ")");
        int outside = 0;
        for (int l = 0; l < d.layerCount(); l++) for (int k : new int[]{0, ToneDocument.KIND_PEN, ToneDocument.KIND_PENCIL, ToneDocument.KIND_PAINT})
            outside += changed(was, d, l, k, 0, 240) + changed(was, d, l, k, 420, d.width - 1);
        check(outside == 0, "and nothing outside its band (" + outside + " px)");
        // The wash's greys go to black or paper; a photocopy keeps about as much dark as the wash had, and darker.
        int grey = 0, all = 0;
        for (int y = 440; y < 600; y++) for (int x = 290; x < 370; x++) { int t = d.compositeTone(x, y); all++; if (t > 30 && t < 225) grey++; }
        check(grey < all / 20, "the wash printed black and white (" + grey + " of " + all + " px grey)");
        double dark = CarrySmudgeChecks.mean(d, 290, 370, 440, 600), wasDark = CarrySmudgeChecks.mean(was, 290, 370, 440, 600);
        check(dark < 235 && Math.abs(dark - wasDark) < 70, "and keeps it (mean " + fixed(dark) + ", was " + fixed(wasDark) + ")");
        return "the same twice, pen " + pen + " px, pencil " + pencil + " and wash " + wash + " changed in its band, none outside; the wash black and white, mean " + fixed(dark) + " (was " + fixed(wasDark) + ")";
    }
    /** Back and forth four times along one line is one pass along it, where its whole width passed. */
    static String backAndForth() {
        WatercolorSession s = new WatercolorSession();
        ToneDocument once = ReprintPictures.page(s);
        KnifePictures.stroke(once, s, "REPRINT", photocopier(), 80, 1, KnifePictures.path(100, 520, 1000, 520, .75, 0, 0, 0, 0));
        s = new WatercolorSession();
        ToneDocument four = ReprintPictures.page(s);
        java.util.List<double[]> p = new java.util.ArrayList<>();
        int t = 0;
        for (int pass = 0; pass < 4; pass++) for (double[] q : KnifePictures.path(pass % 2 == 0 ? 100 : 1000, 520, pass % 2 == 0 ? 1000 : 100, 520, .75, 0, 0, 0, 0)) {
            double[] c = q.clone(); c[3] = t += 4; p.add(c);
        }
        KnifePictures.stroke(four, s, "REPRINT", photocopier(), 80, 1, p.toArray(new double[0][]));
        // The core: within the dab's whole-strength middle (its radius less its soft edge), clear of the ends.
        int diff = 0, n = 0;
        for (int y = 520 - 25; y <= 520 + 25; y++) for (int x = 150; x < 950; x++) { n++; if (once.compositeTone(x, y) != four.compositeTone(x, y)) diff++; }
        check(diff == 0, "back and forth four times reprints as once (" + diff + " of " + n + " px differ)");
        return "four times back and forth the same as once over its middle (" + n + " px)";
    }
    /** How far {@code d}'s wash in the band moved from {@code was}'s: mean absolute tone change. */
    static double moved(ToneDocument was, ToneDocument d) {
        double sum = 0; int n = 0;
        for (int y = 440; y < 600; y++) for (int x = 290; x < 370; x++) { sum += Math.abs(d.compositeTone(x, y) - was.compositeTone(x, y)); n++; }
        return sum / n;
    }
    static String pressure() {
        ToneDocument was = before();
        double light = moved(was, band(photocopier(), .12)), usual = moved(was, band(photocopier(), .75)), firm = moved(was, band(photocopier(), 1));
        check(light > 5 && light < .7 * usual, "a light touch reprints part way (" + fixed(light) + " against " + fixed(usual) + ")");
        check(Math.abs(firm - usual) < 1, "a usual press reprints all the way, as a firm one (" + fixed(usual) + ", " + fixed(firm) + ")");
        return "a light touch moves the wash " + fixed(light) + " grey levels on average, a usual or firm one " + fixed(usual);
    }
    static void undo() {
        WatercolorSession s = new WatercolorSession();
        ToneDocument d = ReprintPictures.page(s);
        String was = WetPaintChecks.crc(d);
        KnifePictures.stroke(d, s, "REPRINT", photocopier(), ReprintPictures.SIZE, 1, ReprintPictures.band(245, 170, .75, ReprintPictures.SIZE));
        String reprinted = WetPaintChecks.crc(d);
        check(!reprinted.equals(was), "it reprints");
        check(d.undo() && WetPaintChecks.crc(d).equals(was), "one Undo takes the whole reprint back, every layer and plane");
        check(d.redo() && WetPaintChecks.crc(d).equals(reprinted), "and Redo brings it all back");
    }
    /**
     * Paper stays paper (the owner, 2026-11-02: "newsprint effect.. it turns empty canvas slightly grey"): over a pen line
     * and a pencil line softened by Blur (a shoulder of faint grey 12 px each side, tone 240 to 254) on white paper,
     * Newsprint and the Photocopier at 90 px leave every pixel farther than 8 px from the line white, and every pixel
     * white as found; the line itself still prints, Newsprint's in dots (toner drops the pale pencil). Before, Newsprint
     * dotted the shoulder out to 12 px.
     */
    static String paperStaysWhite() {
        String[] pencil = null;
        for (String[] t : ToolSlots.FIRST) if (t[0].equals("PENCIL")) pencil = t;
        StringBuilder say = new StringBuilder();
        for (boolean soft : new boolean[]{false, true}) for (String name : new String[]{"Newsprint", "Photocopier"}) {
            ToneDocument was = null, d = null;
            for (int copy = 0; copy < 2; copy++) {
                ToneDocument page = InkWashLayers.blank(600, 300);
                WatercolorSession s = new WatercolorSession();
                if (soft) {
                    WetSmudgeChecks.stroke(page, s, pencil[0], LegacyBrushes.named(pencil), Double.parseDouble(pencil[3]), Double.parseDouble(pencil[4]), SmudgeReachChecks.pen(60, 150, 540, 150));
                    WetSmudgeChecks.stroke(page, s, ToolSlots.SMUDGE_KEY, ToolKits.preset("Blur").brush().parts, 40, .5, SmudgeReachChecks.pen(80, 150, 520, 150));
                } else KnifePictures.stroke(page, s, "PEN", FloatChecks.preset("Steady line"), 4, 1, KnifePictures.path(60, 150, 540, 150, .7, 0, 0, 0, 0));
                for (int i = 0; i < 600 && s.tickDrying(page); i++) { }
                if (copy == 1) KnifePictures.stroke(page, s, "REPRINT", FloatChecks.preset(name), 90, 1, KnifePictures.path(120, 154, 480, 146, .75, 0, 0, 0, 0));
                if (copy == 0) was = page; else d = page;
            }
            int far = 0, white = 0, line = 0, inked = 0, grey = 0;
            for (int y = 0; y < 300; y++) for (int x = 150; x < 450; x++) {
                int t0 = was.compositeTone(x, y), t = d.compositeTone(x, y);
                if (Math.abs(y - 150) > 8 && t != 255) far++;
                if (t0 == 255 && t != 255) white++;
                if (Math.abs(y - 150) <= 2) { line++; if (t < 128) inked++; if (t > 0 && t < 255) grey++; }
            }
            String what = name + " over a " + (soft ? "blurred pencil" : "pen") + " line";
            check(far == 0 && white == 0, what + ": paper farther than 8 px stays white (" + far + " px not), and white as found stays white (" + white + " px not)");
            // Toner drops a pale grey (toner()), so the Photocopier need only print the pen line.
            boolean news = name.equals("Newsprint");
            check((!news && soft || inked > line / 20) && (!news || grey == 0), what + ": the line still prints (" + inked + " of " + line + " px inked, " + grey + " grey)");
            say.append(say.length() == 0 ? "" : ", ").append(what).append(" ").append(100 * inked / line).append("% inked");
        }
        return "paper beside a line stays white: " + say;
    }
    /** Without a Print Shading: toner's black and white, a mid grey one or the other, a pale one gone. */
    static String toner() {
        ModularBrush b = photocopier().copy();
        b.part(BrushPart.MEDIUM).set(PartDial.SCREEN, 0);
        b.normalize();
        check(ResolvedBrush.of(b, 120, 1).carry.print == null && ModularWords.plainWords(b).contains("as toner"), "without a Print Shading it says it prints as toner: " + ModularWords.plainWords(b));
        ToneDocument was = before(), d = band(b, .75);
        int grey = 0, black = 0, all = 0;
        for (int y = 440; y < 600; y++) for (int x = 290; x < 370; x++) { int t = d.compositeTone(x, y); all++; if (t > 30 && t < 225) grey++; if (t <= 30) black++; }
        check(grey < all / 20 && black > all / 20, "toner: black and white (" + grey + " grey, " + black + " black of " + all + ")");
        check(!WetPaintChecks.crc(d).equals(WetPaintChecks.crc(band(photocopier(), .75))), "and not the photocopy");
        return "it prints toner, " + (100 * black / all) + "% of the wash black, the rest paper";
    }
    /** Over wet paint (Gouache, the wet canvas): it stays wet, takes the reprint, and the next Wet stroke mixes into it. */
    static String wet() {
        ToneDocument d = WetPaintChecks.page();
        WatercolorSession s = new WatercolorSession();
        WetPaintChecks.stroke(d, s, WetPaintChecks.gouache(), 70, .5, WetPaintChecks.line(40, 160, 440, 160));
        WetPaintChecks.settle(s, d);
        WetCanvas canvas = s.canvas();
        String was = WetPaintChecks.crc(d);
        KnifePictures.stroke(d, s, "REPRINT", photocopier(), 80, 1, KnifePictures.path(240, 60, 240, 260, .75, 0, 0, 0, 0));
        String reprinted = WetPaintChecks.crc(d);
        check(!reprinted.equals(was) && s.wet() && s.canvas() == canvas, "over wet paint it reprints, the paint still wet");
        WetPaintChecks.settle(s, d);
        check(WetPaintChecks.crc(d).equals(reprinted), "and the paint rests as reprinted until a Wet stroke wakes it");
        WetPaintChecks.stroke(d, s, WetPaintChecks.gouache(), 40, .1, WetPaintChecks.line(100, 60, 100, 260));
        check(s.canvas() == canvas, "the next Wet stroke lays into the same wet paint");
        int slices = WetPaintChecks.settle(s, d);
        check(slices < 2000, "and settles");
        return "over wet paint the paint stays wet, rests as reprinted, and the next Wet stroke mixes into it";
    }
    /** One reprint stroke 900 px long over ReprintPictures' page, start to finish (its five planes): best of three, ms here, on every core and on one. */
    static String cost() {
        StringBuilder say = new StringBuilder("a stroke 900 px long takes");
        boolean shared = Cores.shared;
        try {
            for (double size : new double[]{120, 360}) for (boolean cores : new boolean[]{true, false}) {
                Cores.shared = cores;
                long best = Long.MAX_VALUE;
                for (int run = 0; run < 3; run++) {
                    WatercolorSession s = new WatercolorSession();
                    ToneDocument d = ReprintPictures.page(s);
                    long t0 = System.nanoTime();
                    KnifePictures.stroke(d, s, "REPRINT", photocopier(), size, 1, KnifePictures.path(100, 400, 1000, 400, .75, 0, 0, 0, 0));
                    best = Math.min(best, System.nanoTime() - t0);
                }
                say.append(cores ? (size == 120 ? " " : "; ") : ", ").append(Math.round(best / 1e6)).append(" ms").append(cores ? "" : " on one core").append(cores ? "" : " at " + (int) size + " px");
            }
        } finally { Cores.shared = shared; }
        return say.append(" here (the Manta about 20 times that)").toString();
    }

    static String fixed(double v) { return PartDial.fixed(v, 1); }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
