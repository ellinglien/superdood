package io.github.mpdairy.monopaint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Later's media (2026-10-31; {@link LaterMedia}, {@link FadingInk}): Salt, Plastic Wrap, Feathering and Foxing are Later's
 * choices 5 to 8, seed-only, going with the Drying the technique needs; their codes round-trip; words; each lays as its
 * Drying does and the clock carries it to a finished state that comes from the seed and the page position alone; Salt
 * and Plastic Wrap change only the wash, Feathering and Foxing reach past it; Undo (at once, mid-way, at the end) gives
 * the page before the stroke and Redo the mark as the clock has it; a page saved mid-way opens as saved and goes on;
 * Fading's and Lemon Juice's file section is as before; nothing changes for a brush without them; the finished state is
 * worked out quickly. {@code older-write} and {@code older-fading} write pages for {@code OlderMediaCheck}, compiled
 * against the version before these (scripts/test_inkwash.sh); {@code fading-bytes} compares that version's fading page.
 */
public final class LaterMediaChecks {
    static final PartChoice[] MEDIA = {PartChoice.LATER_SALT, PartChoice.LATER_WRAP, PartChoice.LATER_FEATHER, PartChoice.LATER_FOX,
            PartChoice.LATER_PRESS, PartChoice.LATER_TORN, PartChoice.LATER_STITCH, PartChoice.LATER_CRACKS, PartChoice.LATER_TARNISH};
    /** Those since 2026-11-01 (choices 9 to 13), which the version before them (9a57ae48) reads as Off. */
    static final PartChoice[] LATER2 = Arrays.copyOfRange(MEDIA, 4, MEDIA.length);

    public static void main(String[] args) throws IOException {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length == 2 && args[0].equals("older-write")) { Files.write(Paths.get(args[1]), FadingChecks.file(midway(PartChoice.LATER_FEATHER))); return; }
        if (args.length == 2 && args[0].equals("older-page")) { System.out.println(Arrays.hashCode(FadingChecks.page(midway(PartChoice.LATER_FEATHER)))); return; }
        if (args.length == 2 && args[0].equals("fading-bytes")) { fadingBytes(Files.readAllBytes(Paths.get(args[1]))); return; }
        if (args.length == 1 && args[0].equals("fading-hash")) { System.out.println(Arrays.hashCode(sameBytes())); return; }
        if (args.length == 2 && args[0].equals("older-write2")) { Files.write(Paths.get(args[1]), FadingChecks.file(midway(PartChoice.LATER_STITCH))); return; }
        if (args.length == 2 && args[0].equals("older-page2")) { System.out.println(Arrays.hashCode(FadingChecks.page(midway(PartChoice.LATER_STITCH)))); return; }
        tables(); codes(); words(); laid(); determined(); reach(); paper(); crafts(); undo(); undoMidway(); reopened(); untouched(); worker(); speed(); sameBytes();
        System.out.println("PASS: Later's media are Later's choices 5 to 13 (Salt and Plastic Wrap on Quick or Slow, the rest on Instant, Quick or Slow, none on Wet), seed-only, codes round-trip in the eighth segment; "
                + "each lays as its Drying does, then the clock carries it to a finished state from the seed and the page alone (the same twice, another seed another); Salt, Plastic Wrap, Cracks and Tarnish change only the mark, the rest reach past it; "
                + "Letterpress starves in the paper's valleys, Cross-Stitch keeps to the page's grid, Cracks crack more where the paint is darker, Tarnish only darkens and never past a mid-dark grey; "
                + "Undo at once, mid-way and at the end gives the page before the stroke, Redo the mark as the clock has it; a page saved mid-way opens as saved, saves byte for byte and goes on by the clock; a brush without them changes nothing; "
                + "the same strokes write the same bytes on several cores as on one");
    }

    /** A Soft wash on Slow for Salt, Plastic Wrap and Cracks; the Metalpoint for Tarnish; else an Instant Swell brush. */
    static ModularBrush brush(PartChoice later) {
        if (later == PartChoice.LATER_TARNISH) return LaterChecks.with("Metalpoint", PartChoice.NONE, later, 0);
        boolean wash = wash(later);
        return LaterChecks.with(wash ? "Soft wash" : "Swell brush", wash ? PartChoice.DRYING : PartChoice.NONE, later, 0);
    }
    static boolean wash(PartChoice later) { return later == PartChoice.LATER_SALT || later == PartChoice.LATER_WRAP || later == PartChoice.LATER_CRACKS; }
    static double size(PartChoice later) { return wash(later) ? 60 : 14; }
    static ToneDocument drawn(ModularBrush b, double size) {
        ToneDocument d = InkWashLayers.blank(FadingChecks.W, FadingChecks.H);
        CarrySmudgeChecks.feed(ModularStroke.start(d, b, size, .8, new WatercolorSession()), FadingChecks.line(40, 360, 120));
        return d;
    }
    static ToneDocument drawn(PartChoice later) { return drawn(brush(later), size(later)); }
    static long time(PartChoice later) { return Math.round(1000 * brush(later).part(BrushPart.LATER).get(later.dials.get(1))); }
    /** {@code later}'s stroke half way through its time, at clock 30,000,000. */
    static ToneDocument midway(PartChoice later) {
        FadingChecks.clock = 30_000_000;
        ToneDocument d = drawn(later);
        d.fade(FadingChecks.clock + time(later) / 2, null);
        return d;
    }

    static void tables() {
        check(PartModelChecks.keys(BrushPart.LATER).equals("off,float,halo,fade,lemon,salt,wrap,feather,fox,letterpress,torn,stitch,cracks,tarnish"), "Later's media appended, keyed salt, wrap, feather, fox, then letterpress, torn, stitch, cracks, tarnish: " + PartModelChecks.keys(BrushPart.LATER));
        check(PartModelChecks.dials(PartChoice.LATER_SALT).equals("grains dryIn") && PartModelChecks.dials(PartChoice.LATER_WRAP).equals("crinkle dryIn")
                && PartModelChecks.dials(PartChoice.LATER_FEATHER).equals("threads spreadIn") && PartModelChecks.dials(PartChoice.LATER_FOX).equals("spots dryIn")
                && PartModelChecks.dials(PartChoice.LATER_PRESS).equals("impression spreadIn") && PartModelChecks.dials(PartChoice.LATER_TORN).equals("tear spreadIn")
                && PartModelChecks.dials(PartChoice.LATER_STITCH).equals("stitch dryIn") && PartModelChecks.dials(PartChoice.LATER_CRACKS).equals("cracks dryIn")
                && PartModelChecks.dials(PartChoice.LATER_TARNISH).equals("tarnish dryIn"), "each its macro dial, then its time");
        check(PartChoice.LATER_PRESS.defaultOf(PartDial.SPREAD_IN) == 1 && PartChoice.LATER_PRESS.labelOf(PartDial.SPREAD_IN).equals("Prints In")
                && PartChoice.LATER_TORN.defaultOf(PartDial.SPREAD_IN) == 1 && PartChoice.LATER_TORN.labelOf(PartDial.SPREAD_IN).equals("Tears In")
                && PartChoice.LATER_STITCH.defaultOf(PartDial.DRY_IN) == 1 && PartChoice.LATER_STITCH.labelOf(PartDial.DRY_IN).equals("Stitches In")
                && PartChoice.LATER_CRACKS.defaultOf(PartDial.DRY_IN) == 1 && PartChoice.LATER_CRACKS.labelOf(PartDial.DRY_IN).equals("Ages In")
                && PartChoice.LATER_TARNISH.defaultOf(PartDial.DRY_IN) == 1 && PartChoice.LATER_TARNISH.labelOf(PartDial.DRY_IN).equals("Tarnishes In")
                && PartDial.STITCH.min == 6 && PartDial.STITCH.max == 10 && PartDial.STITCH.shown(8).equals("8 px"),
                "the new ones' times, fresh at 1 s (2026-11-06), and their labels; Cross-Stitch's cells 6 to 10 px");
        check(PartChoice.LATER_SALT.defaultOf(PartDial.DRY_IN) == 1 && PartChoice.LATER_WRAP.defaultOf(PartDial.DRY_IN) == 1 && PartChoice.LATER_FEATHER.defaultOf(PartDial.SPREAD_IN) == 1
                && PartChoice.LATER_FOX.defaultOf(PartDial.DRY_IN) == 10 && PartChoice.LATER_FOX.labelOf(PartDial.DRY_IN).equals("Ages In") && PartChoice.LATER_LEMON.defaultOf(PartDial.DRY_IN) == 1,
                "their times fresh at 1 s, Foxing's 10 s (Ages In) (2026-11-06)");
        for (PartChoice c : MEDIA) {
            check(ModularFeatures.seedOnly(c) && !ModularFeatures.varies(c) && ModularFeatures.offered(c) && c.retired() == null, c + ": offered in Brush Lab, never by a roll or a variation, no retired Drying choice");
            check(c.macro == c.dials.get(0) && c.words != null, c + ": one macro dial");
        }
        check(PartChoice.LATER_SALT.drying() == PartChoice.DRYING && PartChoice.LATER_FEATHER.drying() == PartChoice.FAST_WET && PartChoice.LATER_CRACKS.drying() == PartChoice.DRYING
                && PartChoice.LATER_TARNISH.drying() == PartChoice.NONE && PartChoice.LATER_STITCH.drying() == PartChoice.FAST_WET,
                "a mismatched Drying becomes Slow for Salt, Plastic Wrap and Cracks, Instant for Tarnish, Quick for the rest");
        ModularBrush instant = LaterChecks.with("Swell brush", PartChoice.NONE, PartChoice.LATER_SALT, 0);
        check(instant.choice(BrushPart.LIFE) == PartChoice.DRYING, "Salt on Instant brings Slow: " + instant.choice(BrushPart.LIFE));
        // Brush Lab (2026-11-01): After's card offers every one, whatever the drying (Salt brings its Slow); Drying's none.
        java.util.Map<String, String> store = new java.util.HashMap<>();
        BrushLibrary.Store st = new BrushLibrary.Store() { public String get(String k) { return store.get(k); } public void put(String k, String v) { if (v == null) store.remove(k); else store.put(k, v); } };
        BrushLab lab = new BrushLab(new ToolSlots(st, new java.util.Random(1)), 2, new java.util.Random(7));
        lab.put(Brush.of(brush(PartChoice.LATER_FOX)));
        check(lab.chips(BrushPart.LATER).containsAll(Arrays.asList(MEDIA)) && java.util.Collections.disjoint(lab.chips(BrushPart.LIFE), Arrays.asList(MEDIA)),
                "Brush Lab's After card on Instant: every one, Salt too; Drying's chips none: " + lab.chips(BrushPart.LATER) + " / " + lab.chips(BrushPart.LIFE));
        check(lab.dials(BrushPart.LATER).equals(Arrays.asList(PartDial.SPOTS, PartDial.DRY_IN)), "Foxing's card shows Spots and Ages In: " + lab.dials(BrushPart.LATER));
        // After's card in its shown order, grouped: Off and the mischief, paint media, print, age, craft.
        java.util.List<PartChoice> shown = new java.util.ArrayList<>();
        for (PartChoice c : PartChoice.LATER_SHOWN) if (ModularFeatures.offered(c)) shown.add(c);
        check(lab.chips(BrushPart.LATER).equals(shown), "After's card on Instant, grouped: " + lab.chips(BrushPart.LATER));
        lab.choose(BrushPart.LATER, PartChoice.LATER_SALT);
        check(lab.parts().choice(BrushPart.LATER) == PartChoice.LATER_SALT && lab.parts().choice(BrushPart.LIFE) == PartChoice.DRYING, "Salt's chip on Instant brings Slow: " + lab.parts().choice(BrushPart.LIFE));
        check(PartChoice.LATER_SHOWN.size() == BrushPart.LATER.choices().size() && PartChoice.LATER_SHOWN.containsAll(BrushPart.LATER.choices()), "Later's shown order holds each choice once");
        lab.put(Brush.of(LaterChecks.with("Swell brush", PartChoice.FAST_WET, PartChoice.LATER_FOX, 0)));
        int quick = lab.chips(BrushPart.LATER).size(), per = LabLayout.swapPerPage(BrushPart.LATER, quick);
        check(quick == 14 && per == 14 && lab.chips(BrushPart.LATER).get(6) == PartChoice.LATER_WRAP, "on Quick, fourteen in the tall tray (two pages until 2026-11-06): Off, the mischief, Salt and Plastic Wrap, then Feathering, print, age and craft: " + lab.chips(BrushPart.LATER));
    }

    static void codes() {
        for (PartChoice c : MEDIA) {
            ModularBrush b = brush(c);
            double v = c.macro.min + .75 * (c.macro.max - c.macro.min);
            b.part(BrushPart.LATER).set(c.macro, v);
            String code = ModularCode.encode(b);
            ModularBrush back = ModularCode.decode(code);
            check(back != null && !ModularCode.newer(code) && ModularCode.encode(back).equals(code) && back.choice(BrushPart.LATER) == c && Math.abs(back.part(BrushPart.LATER).get(c.macro) - v) < .001 * (c.macro.max - c.macro.min),
                    c + " round-trips in the eighth segment: " + code);
            check(b.joined() == null && code.split("\\.")[1].split("_").length == 8 && code.split("\\.")[1].split("_")[7].charAt(0) == Character.forDigit(c.index(), 36), c + " is written as Later's choice " + c.index());
        }
    }

    static void words() {
        String[][] want = {{"Salt opens pale starbursts as it dries.", "salt", "Salt", "salt 1 s"}, {"Plastic wrap leaves crinkled veins as it dries.", "plastic wrap", "Plastic wrap", "crinkles 1 s"},
                {"Feathers into the paper's fibres.", "feathering", "Feathering", "wicks 1 s"}, {"Foxes like an old book.", "foxing", "Foxing", "ages 10 s"},
                {"Prints like letterpress, darker at the edges.", "letterpress", "Letterpress", "prints 1 s"}, {"Turns into torn paper, glued down.", "torn paper", "Torn paper", "tears 1 s"},
                {"Turns into cross-stitch on a fabric grid.", "cross-stitch", "Cross-stitch", "stitches 1 s"}, {"Cracks finely as it ages.", "cracks", "Cracks", "cracks 1 s"},
                {"Tarnishes darker, like silverpoint.", "tarnish", "Tarnish", "tarnishes 1 s"}};
        for (int k = 0; k < MEDIA.length; k++) {
            ModularBrush b = brush(MEDIA[k]);
            String plain = ModularWords.plainWords(b), about = BrushVoice.about(b);
            check(plain.contains(want[k][0]) && ModularWords.caption(b).contains(want[k][1]) && ModularWords.choiceWords(b, BrushPart.LATER).equals(want[k][2])
                    && ModularWords.swapNote(b, BrushPart.LATER).equals(want[k][3]), MEDIA[k] + "'s words: \"" + plain + "\", " + ModularWords.caption(b) + ", " + ModularWords.choiceWords(b, BrushPart.LATER) + ", " + ModularWords.swapNote(b, BrushPart.LATER));
            check(!about.contains(want[k][0]) && about.length() <= BrushVoice.ABOUT_MOST && !about.contains(";"), MEDIA[k] + " in a human voice: " + about);
        }
    }

    /** Each lays as its Drying does without it, and by the end of its time it is finished: changed, and done changing. */
    static void laid() throws IOException {
        for (PartChoice c : MEDIA) {
            FadingChecks.clock = 10_000_000;
            ToneDocument d = drawn(c), plain = drawn(LaterChecks.withoutLater(brush(c)), size(c));
            check(d.fading() && Arrays.equals(FadingChecks.page(d), FadingChecks.page(plain)), c + " lays as its Drying does, then changes by the clock");
            int[] at = FadingChecks.page(d);
            d.fade(FadingChecks.clock + time(c) / 2, null);
            int[] mid = FadingChecks.page(d);
            d.fade(FadingChecks.clock + time(c), null);
            int[] end = FadingChecks.page(d);
            int midDiff = differ(at, mid), endDiff = differ(at, end);
            check(!d.fading() && endDiff > 100 && midDiff > 0 && midDiff < endDiff + 1, c + " changes over its time and is done at its end (" + midDiff + " px half way, " + endDiff + " at the end)");
            ToneDocument back = FadingChecks.read(FadingChecks.file(d));
            check(!back.fading() && Arrays.equals(FadingChecks.page(back), end), c + ": done, its file holds an ordinary mark");
        }
    }

    /** The finished state comes from the seed and the page position: the same twice, another seed another. */
    static void determined() {
        for (PartChoice c : MEDIA) {
            FadingChecks.clock = 11_000_000;
            ToneDocument a = drawn(c), b = drawn(c);
            FadingChecks.clock = 99_000_000;
            ToneDocument late = drawn(c), other = drawn(brush(c).copy(brush(c).seed + 1), size(c));
            for (ToneDocument d : new ToneDocument[]{a, b, late, other}) d.fade(FadingChecks.clock + 10 * FadingInk.MOST_MS, null);
            a.fade(99_000_000 + 10L * FadingInk.MOST_MS, null); b.fade(99_000_000 + 10L * FadingInk.MOST_MS, null);
            check(Arrays.equals(FadingChecks.page(a), FadingChecks.page(b)) && Arrays.equals(FadingChecks.page(a), FadingChecks.page(late)), c + ": the same stroke finishes the same, whenever it was drawn");
            check(!Arrays.equals(FadingChecks.page(a), FadingChecks.page(other)), c + ": another seed finishes otherwise");
        }
    }

    /** Salt, Plastic Wrap, Cracks and Tarnish change only the mark; the rest reach past it (threads, spots, a squeeze, a torn edge's shadow, stitches). */
    static void reach() {
        for (PartChoice c : MEDIA) {
            FadingChecks.clock = 12_000_000;
            ToneDocument d = drawn(c);
            int[] at = FadingChecks.page(d);
            d.fade(FadingChecks.clock + 10L * FadingInk.MOST_MS, null);
            int[] end = FadingChecks.page(d);
            int outside = 0;
            for (int i = 0; i < at.length; i++) if (at[i] == 255 && end[i] != 255) outside++;
            boolean reaches = !wash(c) && c != PartChoice.LATER_TARNISH;
            check(reaches ? outside > 30 : outside == 0, c + (reaches ? " reaches past the mark: " : " stays in the mark: ") + outside + " px");
        }
        // Salt and Cracks lighten, Plastic Wrap darkens its folds and lightens between, Feathering, Foxing and Tarnish only
        // darken; Letterpress lightens its middle and squeezes its rim out, Torn Paper whitens its edge and shadows it,
        // Cross-Stitch leaves fabric between its stitches and stitches past the mark.
        for (PartChoice c : MEDIA) {
            FadingChecks.clock = 12_500_000;
            ToneDocument d = drawn(c);
            int[] at = FadingChecks.page(d);
            d.fade(FadingChecks.clock + 10L * FadingInk.MOST_MS, null);
            int[] end = FadingChecks.page(d);
            int lighter = 0, darker = 0;
            for (int i = 0; i < at.length; i++) { if (end[i] > at[i] + 8) lighter++; if (end[i] < at[i] - 8) darker++; }
            boolean ok = c == PartChoice.LATER_SALT || c == PartChoice.LATER_CRACKS ? lighter > 100 && lighter > darker : c == PartChoice.LATER_WRAP ? lighter > 100 && darker > 100
                    : c == PartChoice.LATER_PRESS || c == PartChoice.LATER_TORN || c == PartChoice.LATER_STITCH ? lighter > 100 && darker > 30 : darker > 30 && lighter == 0;
            check(ok, c + ": " + lighter + " px lighter, " + darker + " darker");
        }
    }

    /** {@code b} on the paper {@code texture}, its Texture at {@code tooth}. */
    static ModularBrush onPaper(ModularBrush b, BrushTexture texture, double tooth) {
        ModularBrush c = b.copy();
        for (PartChoice p : BrushPart.PAPER.choices()) if (p.texture() == texture) {
            ModularBrush.Part part = new ModularBrush.Part(p);
            part.set(PartDial.TOOTH, tooth);
            c.set(BrushPart.PAPER, part);
        }
        return c.normalize();
    }
    /**
     * Feathering follows the brush's own paper: smooth paper barely feathers, cold press more, unryu most; what it
     * lays past the mark sits in the paper's valleys; on laid paper it runs the way the laid lines do.
     */
    static void paper() {
        long[] past = new long[3];
        BrushTexture[] papers = {BrushTexture.PLAIN, BrushTexture.COLD_PRESS, BrushTexture.UNRYU};
        for (int k = 0; k < papers.length; k++) past[k] = feathered(papers[k], papers[k] == BrushTexture.PLAIN ? 0 : .6, null);
        check(past[0] * 2 < past[1] && past[1] < past[2], "Feathering wicks more into rougher, softer paper: smooth " + past[0] + ", cold press " + past[1] + ", unryu " + past[2] + " (darkness past the mark)");
        double[] valley = new double[2];
        feathered(BrushTexture.COLD_PRESS, .6, valley);
        check(valley[0] < valley[1] - .03, "on cold press it lies in the valleys: the paper's height under it " + String.format("%.3f", valley[0]) + ", round it " + String.format("%.3f", valley[1]));
        // Laid paper's lines run one way: the feathering's darkness changes across them more than along them, as the paper's does.
        double[] laid = new double[4];
        feathered(BrushTexture.LAID, .6, laid);
        boolean paperAcross = laid[2] > laid[3];
        check(paperAcross == laid[0] > laid[1] && Math.max(laid[0], laid[1]) > 1.25 * Math.min(laid[0], laid[1]),
                "on laid paper it runs along the lines: its darkness changes " + String.format("%.0f / %.0f", laid[0], laid[1]) + " (down / across), the paper's " + String.format("%.0f / %.0f", laid[2], laid[3]));
    }
    /**
     * A Swell brush line on {@code texture} with Feathering, finished: the darkness it laid past the mark. {@code out}:
     * with 2, the paper's mean height under that darkness and round the mark; with 4, the darkness's and the paper's
     * change down and across the page there.
     */
    static long feathered(BrushTexture texture, double tooth, double[] out) {
        FadingChecks.clock = 12_700_000;
        ModularBrush b = onPaper(brush(PartChoice.LATER_FEATHER), texture, tooth);
        ToneDocument d = drawn(b, 14);
        int[] at = FadingChecks.page(d);
        d.fade(FadingChecks.clock + 10L * FadingInk.MOST_MS, null);
        int[] end = FadingChecks.page(d);
        int w = FadingChecks.W, h = FadingChecks.H;
        long dark = 0;
        double under = 0, weight = 0, near = 0, nearN = 0, dy = 0, dx = 0, py = 0, pxx = 0;
        PaperGrain g = texture.grain();
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            int i = y * w + x;
            if (at[i] != 255) continue;
            int add = at[i] - end[i];
            dark += add;
            float ht = LaterMedia.height(g, x, y);
            boolean close = false;
            for (int r = -6; r <= 6 && !close; r += 3) for (int c = -6; c <= 6; c += 3) { int j = Math.min(h - 1, Math.max(0, y + r)) * w + Math.min(w - 1, Math.max(0, x + c)); if (at[j] < 200) { close = true; break; } }
            if (close) { near += ht; nearN++; }
            if (add > 0) {
                under += ht * add; weight += add;
                dy += Math.abs((at[i + w] - end[i + w]) - add); dx += Math.abs((at[i + 1] - end[i + 1]) - add);
                py += Math.abs(LaterMedia.height(g, x, y + 1) - ht); pxx += Math.abs(LaterMedia.height(g, x + 1, y) - ht);
            }
        }
        if (out != null && out.length == 2) { out[0] = under / Math.max(1, weight); out[1] = near / Math.max(1, nearN); }
        if (out != null && out.length == 4) { out[0] = dy; out[1] = dx; out[2] = py; out[3] = pxx; }
        return dark;
    }

    /** {@code b} drawn along {@link FadingChecks#line} at {@code size}: the page as laid and finished. */
    static int[][] laidAndDone(ModularBrush b, double size) {
        FadingChecks.clock = 12_800_000;
        ToneDocument d = drawn(b, size);
        int[] at = FadingChecks.page(d);
        d.fade(FadingChecks.clock + 10L * FadingInk.MOST_MS, null);
        return new int[][]{at, FadingChecks.page(d)};
    }
    /**
     * What each of the print, crafts and ageing does that only it does: Letterpress starves where its paper dips; Cross-Stitch's
     * stitches keep to a grid fixed to the page, one cell across; Cracks crack more of the darker paint; Tarnish darkens a
     * pale line a good deal, never past a mid-dark grey.
     */
    static void crafts() {
        PaperGrain g = BrushTexture.COLD_PRESS.grain();
        int[][] press = laidAndDone(onPaper(brush(PartChoice.LATER_PRESS), BrushTexture.COLD_PRESS, .7), 26);
        double paleH = 0, allH = 0; int pale = 0, all = 0;
        for (int i = 0; i < press[0].length; i++) {
            if (press[0][i] > 80) continue;
            float ht = LaterMedia.height(g, i % FadingChecks.W, i / FadingChecks.W);
            allH += ht; all++;
            if (press[1][i] > press[0][i] + 40) { paleH += ht; pale++; }
        }
        check(pale > 30 && paleH / pale < allH / all - .05, "Letterpress starves in the paper's valleys: the paper's height under its pale pixels " + String.format("%.3f", paleH / Math.max(1, pale)) + ", under the mark " + String.format("%.3f", allH / Math.max(1, all)) + " (" + pale + " px)");
        // Cross-Stitch: across a wash, the page repeats a stitch (8 px) along, not half one.
        int[][] stitch = laidAndDone(LaterChecks.with("Soft wash", PartChoice.DRYING, PartChoice.LATER_STITCH, 0), 60);
        double whole = 0, half = 0;
        for (int y = 100; y < 140; y++) for (int x = 120; x < 280; x++) {
            int i = y * FadingChecks.W + x;
            whole += Math.abs(stitch[1][i] - stitch[1][i + 8]); half += Math.abs(stitch[1][i] - stitch[1][i + 4]);
        }
        check(whole * 1.5 < half, "Cross-Stitch's stitches keep to the page's 8 px grid: " + Math.round(whole) + " off a cell along, " + Math.round(half) + " off half a cell");
        // Cracks: the Swell brush lays darker as it presses; more of its darker paint cracks.
        int[][] cracks = laidAndDone(LaterChecks.with("Swell brush", PartChoice.NONE, PartChoice.LATER_CRACKS, 0), 24);
        int dark = 0, darkCracked = 0, mid = 0, midCracked = 0, most = 255;
        for (int a : cracks[0]) most = Math.min(most, a);
        for (int i = 0; i < cracks[0].length; i++) {
            int a = cracks[0][i];
            boolean cracked = cracks[1][i] > a + 12;
            if (a < most + 25) { dark++; if (cracked) darkCracked++; } else if (a > most + 50 && a < most + 110) { mid++; if (cracked) midCracked++; }
        }
        check(dark > 100 && mid > 100 && darkCracked / (double) dark > 1.2 * midCracked / (double) mid, "Cracks crack more of the darker paint: " + darkCracked + " of " + dark + " dark px, " + midCracked + " of " + mid + " mid grey");
        // Tarnish: the Metalpoint's pale line darkens by a good deal, never past its darkest.
        int[][] tarnish = laidAndDone(brush(PartChoice.LATER_TARNISH), 14);
        int laidMost = 255, doneMost = 255;
        for (int i = 0; i < tarnish[0].length; i++) { laidMost = Math.min(laidMost, tarnish[0][i]); doneMost = Math.min(doneMost, tarnish[1][i]); }
        check(laidMost > 150 && doneMost < laidMost - 40 && doneMost >= LaterMedia.TARNISH_DARKEST - 1, "Tarnish darkens the Metalpoint's pale line from " + laidMost + " to " + doneMost + ", never past " + LaterMedia.TARNISH_DARKEST);
    }

    /** Undo at once and at the end gives the page before the stroke, tiles past it too; Redo the mark as the clock has it. */
    static void undo() {
        for (PartChoice c : MEDIA) {
            FadingChecks.clock = 13_000_000;
            int[] blank = FadingChecks.page(InkWashLayers.blank(FadingChecks.W, FadingChecks.H));
            ToneDocument d = drawn(c), same = drawn(c);
            check(d.undo() && Arrays.equals(FadingChecks.page(d), blank), c + ": Undo at once gives the page before it");
            check(d.redo() && Arrays.equals(FadingChecks.page(d), FadingChecks.page(same)), c + ": Redo lays it again");
            long end = FadingChecks.clock + 10L * FadingInk.MOST_MS;
            d.fade(end, null); same.fade(end, null);
            check(Arrays.equals(FadingChecks.page(d), FadingChecks.page(same)) && d.undo() && Arrays.equals(FadingChecks.page(d), blank), c + ": finished, Undo still gives the page before it, what grew past it too");
            check(d.redo() && Arrays.equals(FadingChecks.page(d), FadingChecks.page(same)), c + ": Redo gives it finished");
        }
    }
    /** Undone mid-way, nothing of it comes back by the clock (not past the mark either); Redo gives it as the clock has it. */
    static void undoMidway() {
        for (PartChoice c : MEDIA) {
            FadingChecks.clock = 14_000_000;
            int[] blank = FadingChecks.page(InkWashLayers.blank(FadingChecks.W, FadingChecks.H));
            ToneDocument d = drawn(c), same = drawn(c);
            long t = time(c);
            d.fade(FadingChecks.clock + t / 3, null); same.fade(FadingChecks.clock + t / 3, null);
            check(d.undo() && Arrays.equals(FadingChecks.page(d), blank), c + ": Undo mid-way gives the page before it");
            d.fade(FadingChecks.clock + 2 * t / 3, null); same.fade(FadingChecks.clock + 2 * t / 3, null);
            check(Arrays.equals(FadingChecks.page(d), blank), c + ": undone, the clock brings none of it back");
            check(d.redo() && Arrays.equals(FadingChecks.page(d), FadingChecks.page(same)), c + ": Redo gives it as the clock has it");
            // Undone and drawn over: the old mark never shows again.
            d.undo();
            FadingChecks.draw(d, FadingChecks.brush(PartChoice.FAST_WET, 0), new double[][]{{20, 20, .8, 1000}, {30, 30, .8, 1010}});
            ToneDocument dot = InkWashLayers.blank(FadingChecks.W, FadingChecks.H);
            FadingChecks.draw(dot, FadingChecks.brush(PartChoice.FAST_WET, 0), new double[][]{{20, 20, .8, 1000}, {30, 30, .8, 1010}});
            d.fade(FadingChecks.clock + 10L * FadingInk.MOST_MS, null);
            check(Arrays.equals(FadingChecks.page(d), FadingChecks.page(dot)) && !d.fading(), c + ": undone and drawn past, it is gone for good");
        }
    }
    /** Saved mid-way it opens as saved, saves again byte for byte, and goes on by the clock as one kept open. */
    static void reopened() throws IOException {
        for (PartChoice c : MEDIA) {
            ToneDocument d = midway(c), same = midway(c);
            byte[] saved = FadingChecks.file(d);
            ToneDocument opened = FadingChecks.read(saved);
            check(opened.fading() && opened.fades().get(0).kind == c.clocked() && Arrays.equals(FadingChecks.page(opened), FadingChecks.page(d)) && Arrays.equals(FadingChecks.file(opened), saved),
                    c + ": saved mid-way it opens as saved and saves again byte for byte");
            long end = 30_000_000L + 10L * FadingInk.MOST_MS;
            opened.fade(end, null); same.fade(end, null);
            check(!opened.fading() && Arrays.equals(FadingChecks.page(opened), FadingChecks.page(same)), c + ": opened later it has gone on by the clock, as one kept open");
        }
        // Fading's and Lemon Juice's section is as before: no LATR section beside it, and a page with both reads back both.
        FadingChecks.clock = 31_000_000;
        ToneDocument both = FadingChecks.drawn(FadingChecks.brush(PartChoice.FADE, 60));
        FadingChecks.draw(both, brush(PartChoice.LATER_FOX), FadingChecks.line(40, 360, 60));
        byte[] saved = FadingChecks.file(both);
        ToneDocument opened = FadingChecks.read(saved);
        check(opened.fades().size() == 2 && Arrays.equals(FadingChecks.file(opened), saved), "a page with a fading mark and Foxing reads back both, byte for byte");
    }
    /** A brush without them: no mark, nothing by the clock, its file as before. */
    static void untouched() {
        FadingChecks.clock = 32_000_000;
        ToneDocument d = drawn(LaterChecks.withoutLater(brush(PartChoice.LATER_SALT)), 60);
        check(!d.fading() && d.fade(FadingChecks.clock + FadingInk.MOST_MS, null) == 0, "a brush without them lays nothing that changes by the clock");
        // A sample (no session) is laid as it ends: nothing to tick.
        ToneDocument sample = InkWashLayers.blank(FadingChecks.W, FadingChecks.H);
        CarrySmudgeChecks.feed(ModularStroke.start(sample, brush(PartChoice.LATER_FEATHER), 14, 1, null), FadingChecks.line(40, 360, 120));
        check(!sample.fading(), "a sample is laid as it ends");
    }
    /** A version before them wrote this page with a Fading mark: this version writes the same stroke byte for byte. */
    static void fadingBytes(byte[] older) throws IOException {
        check(Arrays.equals(older, FadingChecks.file(fadingPage())), "a page with Fading and Lemon Juice marks writes as the version before Later's media wrote it, byte for byte");
        ToneDocument read = FadingChecks.read(older);
        check(read.fades().size() == 2 && Arrays.equals(FadingChecks.file(read), older), "and reads it back as it was");
        System.out.println("PASS: Fading's and Lemon Juice's page section is written and read as the version before Later's media did, byte for byte");
    }
    /**
     * The same strokes write the same file, byte for byte, however the cores share the work: {@link #fadingPage} drawn
     * again and again on several cores and once on one (its marks' tiles in the order the stroke laid them); its bytes,
     * for scripts/test_inkwash.sh to compare across runs ({@code fading-hash}).
     */
    static byte[] sameBytes() throws IOException {
        boolean was = Cores.shared;
        Cores.shared = false;
        byte[] alone;
        try { alone = FadingChecks.file(fadingPage()); } finally { Cores.shared = was; }
        for (int i = 0; i < 12; i++) check(Arrays.equals(alone, FadingChecks.file(fadingPage())), "a page with Fading and Lemon Juice marks writes the same bytes on several cores as on one (draw " + (i + 1) + ")");
        return alone;
    }
    /** The page {@code OlderMediaCheck fading} draws too: a fading stroke and a lemon juice one, half way. */
    static ToneDocument fadingPage() {
        FadingChecks.clock = 40_000_000;
        ToneDocument d = FadingChecks.drawn(FadingChecks.brush(PartChoice.FADE, 60));
        FadingChecks.draw(d, FadingChecks.brush(PartChoice.LEMON, 120), FadingChecks.line(40, 360, 60));
        d.fade(FadingChecks.clock + 20_000, null);
        return d;
    }

    /**
     * With a worker (the drawing screen's, {@link ToneDocument#laterWork}) the lift doesn't wait for the work: the stroke
     * shows as laid and stays so until the work is in, the ticker looking again soon; a save waits for it; once in, the
     * mark goes on exactly as worked out at once. And the lift itself stays quick.
     */
    static void worker() throws IOException {
        java.util.List<Runnable> queued = new java.util.ArrayList<>();
        for (PartChoice c : MEDIA) {
            FadingChecks.clock = 15_000_000;
            long end = FadingChecks.clock + 10L * FadingInk.MOST_MS;
            ToneDocument at = drawn(c), plain = drawn(LaterChecks.withoutLater(brush(c)), size(c));
            at.fade(end, null);
            java.util.concurrent.Executor was = ToneDocument.laterWork;
            ToneDocument d;
            ToneDocument.laterWork = queued::add;
            try { d = drawn(c); } finally { ToneDocument.laterWork = was; }
            check(queued.size() == 1 && d.fading() && d.fades().isEmpty() && Arrays.equals(FadingChecks.page(d), FadingChecks.page(plain))
                    && d.nextFade() == FadingChecks.clock + ToneDocument.ADOPT_MS, c + ": the lift leaves the work to the worker, the stroke as laid, the ticker looking again in " + ToneDocument.ADOPT_MS + " ms");
            d.fade(FadingChecks.clock + time(c) / 2, null);
            check(Arrays.equals(FadingChecks.page(d), FadingChecks.page(plain)) && d.fading(), c + ": until the work is in, nothing changes");
            check(FadingChecks.read(FadingChecks.file(d)).fades().size() == 1, c + ": a save waits for the work and keeps the mark");
            queued.get(0).run(); queued.clear();
            d.fade(end, null);
            check(Arrays.equals(FadingChecks.page(d), FadingChecks.page(at)) && !d.fading(), c + ": once in, it goes on as worked out at once");
        }
        // The lift's own share: a 300 px wash's stroke ends no slower with the work handed to a worker.
        long extra = 0;
        ToneDocument.laterWork = queued::add;
        try {
            for (PartChoice c : MEDIA) extra = Math.max(extra, lift(brush(c)) - lift(LaterChecks.withoutLater(brush(c))));
        } finally { ToneDocument.laterWork = Runnable::run; queued.clear(); }
        System.out.println("Later's media: a 300 px wash's lift takes at most " + Math.max(0, extra) / 1000 + " µs more with the work handed off, on this machine");
    }
    /** A wash laid back and forth on a 420 × 300 page, as a sky or a patch of ground is: about 300 × 170 px (LaterMediaPictures' too). */
    static double[][] wash() {
        int rows = 4, n = 40;
        double[][] o = new double[rows * n][];
        double t = 1000;
        for (int r = 0; r < rows; r++) for (int i = 0; i < n; i++) {
            double u = i / (n - 1.0), x = r % 2 == 0 ? 70 + 280 * u : 350 - 280 * u;
            o[r * n + i] = new double[]{x, 85 + 40 * r + 6 * Math.sin(u * 5 + r), .55 + .35 * Math.sin(Math.PI * u), t};
            t += 9;
        }
        return o;
    }
    /** The best of five lifts of a 300 × 170 px wash with {@code b}, ns: the stroke's finish alone. */
    static long lift(ModularBrush b) {
        long best = Long.MAX_VALUE;
        for (int rep = 0; rep < 5; rep++) {
            ToneDocument d = InkWashLayers.blank(420, 300);
            DrawingStroke s = ModularStroke.start(d, b, 60, .55, new WatercolorSession());
            double[][] path = wash();
            for (int i = 0; i < path.length; i++) { s.sample((float) path[i][0], (float) path[i][1], ToolSettings.pressure((float) path[i][2]), 0, 0, (long) path[i][3]); if (i % 3 == 2) s.present(); }
            s.present();
            long t0 = System.nanoTime();
            s.finish();
            best = Math.min(best, System.nanoTime() - t0);
        }
        return best;
    }

    /**
     * Working out the finished state is quick: a 300 × 300 px mark, each technique (the Manta is about 20 times slower),
     * within 12 ms here (Cracks' two Voronoi networks, about 11 ms: a quarter second on the Manta, off the drawing thread).
     */
    static void speed() {
        int w = 300 + 2 * 40, h = w, n = w * h;
        byte[] tones = new byte[n], alpha = new byte[n];
        boolean[] mark = new boolean[n];
        long most = 0; String slowest = "", each = "";
        for (PartChoice c : MEDIA) {
            long best = Long.MAX_VALUE;
            for (int rep = 0; rep < 12; rep++) {
                for (int i = 0; i < n; i++) {
                    int x = i % w - 40, y = i / w - 40;
                    boolean in = x >= 0 && y >= 0 && x < 300 && y < 300 && (x - 150) * (x - 150) + (y - 150) * (y - 150) < 150 * 150;
                    mark[i] = in; tones[i] = (byte) (in ? 90 : 255); alpha[i] = (byte) (in ? 200 : 0);
                }
                long t0 = System.nanoTime();
                LaterMedia.finish(new FadingInk.Spec(c.clocked(), 1000, c.defaultOf(c.macro), 7, BrushTexture.COLD_PRESS, .5), 100, 100, w, h, tones, alpha, mark);
                best = Math.min(best, System.nanoTime() - t0);
            }
            if (best > most) { most = best; slowest = c.label; }
            each += (each.isEmpty() ? "" : ", ") + c.label + " " + best / 1000 + " µs";
        }
        Timing.limit(most < 12_000_000L, "Later's media's slowest finished state for a 300 px mark: " + most / 1000 + " µs (" + slowest + ")");
        System.out.println("Later's media: a 300 px mark's finished state on this machine: " + each);
    }

    static int differ(int[] a, int[] b) { int n = 0; for (int i = 0; i < a.length; i++) if (a[i] != b[i]) n++; return n; }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
