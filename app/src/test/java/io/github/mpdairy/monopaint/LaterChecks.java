package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Later (roadmap step 7, 2026-10-31): the mischief as its own optional seventh part. Its choices and what Drying each
 * goes with; every Basic tool's and retired one's code that held a mischief in Drying reads as Drying and Later,
 * writes back byte for byte, and the same brush written the new way (an eighth segment) reads and draws the same;
 * combinations an older version couldn't hold draw and settle (Fading on Instant and on a settling Slow wash, Lemon
 * Juice on Slow, Mold on a Slow with its own Flow While Wet) and round-trip in the eighth segment; {@code older} lists
 * codes and how the version before Later must read them (OlderLaterCheck, run by scripts/test_inkwash.sh).
 */
public final class LaterChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length > 0 && args[0].equals("older")) { for (String[] t : older()) System.out.println(String.join("\t", t)); return; }
        if (args.length > 0 && args[0].equals("media")) { for (String[] t : media(LaterMediaChecks.MEDIA)) System.out.println(String.join("\t", t)); return; }
        if (args.length > 0 && args[0].equals("second")) { for (String[] t : second()) System.out.println(String.join("\t", t)); return; }
        if (args.length > 0 && args[0].equals("later2")) { for (String[] t : media(LaterMediaChecks.LATER2)) System.out.println(String.join("\t", t)); return; }
        tables(); oldCodes(); combos(); roundTrips(); lab();
        System.out.println("PASS: Later is the seventh part (Off, Float, Mold, Fading, Lemon Juice; Float on Quick, Mold on Slow, Fading and Lemon Juice on Instant, Quick or Slow, none on Wet); "
                + "every Basic and retired code with a mischief in Drying reads as Drying + Later, writes back byte for byte and draws as its new form does; "
                + "Fading on Instant and on a settling Slow wash fades to nothing, Lemon Juice on Slow ends as the Slow wash settled, Mold ignores Slow's Flow While Wet; new combinations round-trip in an eighth segment; Brush Lab adds and drops Later as Drying allows");
    }

    static void tables() {
        check(Arrays.toString(BrushPart.values()).equals("[TIP, MEDIUM, CARRY, PAPER, LIFE, HAND, LATER]") && BrushPart.SIX == 6, "Later is the seventh part, after the six every code holds");
        check(PartModelChecks.keys(BrushPart.LATER).startsWith("off,float,halo,fade,lemon,"), "Later's choices keep the retired Drying choices' keys, Later's media after (LaterMediaChecks): " + PartModelChecks.keys(BrushPart.LATER));
        check(PartModelChecks.dials(PartChoice.LATER_FLOAT).equals("drying spread drift") && PartModelChecks.dials(PartChoice.LATER_MOLD).equals("reach growth")
                && PartModelChecks.dials(PartChoice.LATER_FADE).equals("fadeIn") && PartModelChecks.dials(PartChoice.LATER_LEMON).equals("dryIn"), "Later's dials: Float's floating time, Mold's without its Drying Time (Slow's)");
        PartChoice[] dryings = {PartChoice.NONE, PartChoice.FAST_WET, PartChoice.DRYING, PartChoice.WET_PAINT};
        String want = "off:YYYY float:-Y-- halo:--Y- fade:YYY- lemon:YYY- salt:-YY- wrap:-YY- feather:YYY- fox:YYY- letterpress:YYY- torn:YYY- stitch:YYY- cracks:YYY- tarnish:YYY-";
        StringBuilder got = new StringBuilder();
        for (PartChoice l : BrushPart.LATER.choices()) {
            got.append(got.length() > 0 ? " " : "").append(l.key).append(':');
            for (PartChoice d : dryings) got.append(l.goesWith(d) ? 'Y' : '-');
        }
        check(got.toString().equals(want), "which Later goes with which Drying: " + got);
        for (PartChoice c : BrushPart.LIFE.choices()) check(ModularFeatures.offered(c) == (c.later() == null), c + ": Drying offers only how paint dries");
        // Every Later's time (Float's Floating Time, a clocked one's last dial, as ResolvedBrush reads it) is fresh at its least, 1 s or less; Foxing's 10 s (the owner, 2026-11-06). Mold's is Slow's Drying Time.
        for (PartChoice l : BrushPart.LATER.choices()) {
            if (l == PartChoice.LATER_OFF || l == PartChoice.LATER_MOLD) continue;
            PartDial time = l == PartChoice.LATER_FLOAT ? PartDial.FLOAT_TIME : l.dials.get(l.dials.size() - 1);
            check(l.clocked() >= 0 || l == PartChoice.LATER_FLOAT, l + " has a time");
            double least = l.range(time)[0], fresh = l.defaultOf(time);
            check(time == PartDial.FLOAT_TIME ? least == 0 : least == 1, l + "'s " + l.labelOf(time) + " goes down to 1 s: " + least);
            check(fresh == (l == PartChoice.LATER_FOX ? 10 : least), l + "'s " + l.labelOf(time) + " is fresh at its least: " + fresh);
        }
    }

    /** Every code a Basic tool has or had whose Drying is a retired mischief. */
    static List<ToolPresets.Preset> mischief() {
        List<ToolPresets.Preset> out = new ArrayList<>();
        for (ToolPresets.Preset p : ToolPresets.withRetired()) {
            String life = p.code.substring(p.code.indexOf(".M") + 3).split("_")[4];
            if (PartChoice.of(BrushPart.LIFE, Character.digit(life.charAt(0), 36)).later() != null) out.add(p);
        }
        return out;
    }

    /**
     * {@code code} (its Drying a retired mischief) written the new way, by the spec alone (docs/BRUSH-CODE.md): Drying the
     * drying it implied (Quick, or Slow at its Drying Time with Flow While Wet 0, the rest fresh), the nib or 0, then
     * Later's segment with the old choice's other dials.
     */
    static String newForm(String code) {
        String[] dot = code.split("\\."), seg = dot[1].split("_");
        String life = seg[4], dials = life.substring(1), dry = "1", later;
        switch (life.charAt(0)) {
            case '3': later = "1" + dials; break;
            case '4': check(dials.length() == 6, "a Mold code holds its three dials: " + code); dry = "2" + dials.substring(0, 2) + "00"; later = "2" + dials.substring(2); break;
            case '5': later = "3" + dials; break;
            default: later = "4" + dials;
        }
        seg[4] = dry;
        return dot[0] + "." + String.join("_", Arrays.copyOf(seg, 6)) + "_" + (seg.length > 6 ? seg[6] : "0") + "_" + later + "." + dot[2];
    }
    static ModularBrush withoutLater(ModularBrush b) { ModularBrush c = b.copy(); c.set(BrushPart.LATER, new ModularBrush.Part(PartChoice.LATER_OFF)); return c; }

    static void oldCodes() {
        List<ToolPresets.Preset> all = mischief();
        check(all.size() >= 5, "the Basic tools with a mischief: " + all.size());
        FadingChecks.clock = 7_000_000;
        for (ToolPresets.Preset p : all) {
            ModularBrush b = ModularCode.decode(p.code);
            PartChoice later = b.choice(BrushPart.LATER);
            check(later != PartChoice.LATER_OFF && b.choice(BrushPart.LIFE) == later.drying() && !ModularCode.newer(p.code), p.name + " reads as " + b.choice(BrushPart.LIFE) + " + " + later);
            check(ModularCode.encode(b).equals(p.code), p.name + " writes back as it was: " + ModularCode.encode(b));
            String fresh = newForm(p.code);
            ModularBrush n = ModularCode.decode(fresh);
            check(n != null && !ModularCode.newer(fresh), p.name + "'s new form reads: " + fresh);
            for (BrushPart part : BrushPart.values()) check(n.part(part).sameAs(b.part(part)), p.name + "'s new form is the same brush: " + part);
            check(ModularCode.encode(n).equals(p.code), p.name + "'s new form writes the old one");
            check(ToolPresetsChecks.drawn(fresh).equals(ToolPresetsChecks.drawn(p.code)), p.name + "'s new form draws its loop as the old");
            ToneDocument a = FadingChecks.drawn(b), c = FadingChecks.drawn(n);
            check(Arrays.equals(FadingChecks.page(a), FadingChecks.page(c)) && a.fades().size() == c.fades().size(), p.name + ": the two forms draw the same stroke");
        }
    }

    static ModularBrush with(String preset, PartChoice drying, PartChoice later, double settle) {
        ModularBrush b = FloatChecks.preset(preset).copy();
        ModularBrush.Part d = ModularBrush.switchTo(b.part(BrushPart.LIFE), drying);
        if (d.has(PartDial.SETTLE)) d.set(PartDial.SETTLE, settle);
        b.set(BrushPart.LIFE, d);
        b.set(BrushPart.LATER, new ModularBrush.Part(later));
        return b.normalize();
    }

    static void combos() {
        ToneDocument blank = InkWashLayers.blank(FadingChecks.W, FadingChecks.H);
        // Fading on Instant and on a settling Slow wash: it fades to nothing.
        for (ModularBrush b : new ModularBrush[]{with("Swell brush", PartChoice.NONE, PartChoice.LATER_FADE, 0), with("Soft wash", PartChoice.DRYING, PartChoice.LATER_FADE, .6)}) {
            check(b.choice(BrushPart.LATER) == PartChoice.LATER_FADE && b.joined() == null, "a new combination: " + ModularCode.encode(b));
            FadingChecks.clock = 8_000_000;
            ToneDocument d = FadingChecks.drawn(b);
            check(d.fading() && FadingChecks.inked(d) > 500, b.choice(BrushPart.LIFE) + " + Fading lays a mark that changes by the clock");
            for (long t = FadingChecks.clock; t <= FadingChecks.clock + 30_000; t += 1000) d.fade(t, null);
            check(!d.fading() && Arrays.equals(FadingChecks.page(d), FadingChecks.page(blank)), b.choice(BrushPart.LIFE) + " + Fading fades to nothing");
        }
        // Lemon Juice on a settling Slow wash: it ends as the wash ends, settled.
        ModularBrush lemon = with("Soft wash", PartChoice.DRYING, PartChoice.LATER_LEMON, .6), plain = withoutLater(lemon);
        FadingChecks.clock = 9_000_000;
        ToneDocument d = FadingChecks.drawn(lemon), settled = InkWashLayers.blank(FadingChecks.W, FadingChecks.H);
        CarrySmudgeChecks.feed(ModularStroke.start(settled, plain, 14, 1, null), FadingChecks.line(40, 360, 120));
        check(d.fading() && FadingChecks.dark(d) < FadingChecks.dark(settled) * .2, "Slow + Lemon Juice goes on faint");
        d.fade(FadingChecks.clock + 400_000, null);
        check(!d.fading() && Arrays.equals(FadingChecks.page(d), FadingChecks.page(settled)), "Slow + Lemon Juice ends as the Slow wash does, settled");
        // Mold on a Slow with its own Flow While Wet grows as the Mold it always was: only Slow's Drying Time counts.
        ModularBrush mold = ModularCode.decode(FloatChecks.find("Mold ink").code), flowing = mold.copy();
        flowing.part(BrushPart.LIFE).set(PartDial.SETTLE, .5);
        check(flowing.joined() == null && ToolPresetsChecks.drawn(ModularCode.encode(flowing)).equals(ToolPresetsChecks.drawn(ModularCode.encode(mold))), "Mold under Slow's own Flow While Wet draws as Mold");
        // Wet goes with no Later: a code that says so loses it (another app's), and Brush Lab never offers it.
        ModularBrush wet = with("Soft wash", PartChoice.WET_PAINT, PartChoice.LATER_FADE, 0);
        check(wet.choice(BrushPart.LIFE) == PartChoice.FAST_WET && wet.choice(BrushPart.LATER) == PartChoice.LATER_FADE, "a Later on Wet brings its own drying: " + wet.choice(BrushPart.LIFE));
    }

    static void roundTrips() {
        String[] codes = {
                ModularCode.encode(with("Swell brush", PartChoice.NONE, PartChoice.LATER_FADE, 0)),
                ModularCode.encode(with("Soft wash", PartChoice.DRYING, PartChoice.LATER_LEMON, .6)),
                ModularCode.encode(with("Stamp", PartChoice.NONE, PartChoice.LATER_LEMON, 0).withNib(12345)),
        };
        for (String code : codes) {
            String[] segments = code.split("\\.")[1].split("_");
            ModularBrush b = ModularCode.decode(code);
            check(segments.length == 8 && b != null && b.later() && !ModularCode.newer(code) && ModularCode.encode(b).equals(code), "a new combination round-trips in an eighth segment: " + code);
        }
        check(codes[2].split("\\.")[1].split("_")[6].equals(Long.toString(12345, 36)) && codes[0].split("\\.")[1].split("_")[6].equals("0"), "the seventh is the nib, or 0");
        // An eighth segment on a retired Drying choice is the newer word on it; a ninth, a newer version's.
        String old = FloatChecks.find("Vanishing ink").code, both = old.replace(".2fe", "_0_4.2fe");
        ModularBrush b = ModularCode.decode(both);
        check(b.choice(BrushPart.LATER) == PartChoice.LATER_LEMON && b.choice(BrushPart.LIFE) == PartChoice.FAST_WET, "a Later segment over a retired Drying mischief: " + both);
        check(ModularCode.newer(old.replace(".2fe", "_0_4_1.2fe")) && ModularCode.newer(old.replace(".2fe", "_0_z.2fe")), "a ninth segment, or a Later past the list, is a newer version's");
        // Rolls: as many with a mischief as before, every one written as before (no roll needs an eighth segment).
        int later = 0;
        for (int i = 0; i < 4000; i++) {
            ModularBrush r = ModularStyle.named(ModularStyle.ANYTHING_GOES).roll(new java.util.Random(91000L + i));
            if (r.later()) { later++; check(r.joined() != null, "a roll's Later is one an older version holds: " + ModularCode.encode(r)); }
            check(r.choice(BrushPart.LIFE).later() == null, "a roll's mischief is on After, never Drying: " + ModularCode.encode(r));
            ModularBrush die = ModularStyle.named(ModularStyle.ANYTHING_GOES).rollOnePart(r, BrushPart.LIFE, null, new java.util.Random(i));
            check(die.choice(BrushPart.LIFE).later() == null, "Drying's die never picks a retired mischief: " + ModularCode.encode(die));
        }
        check(later > 4000 / 40 && later < 4000 / 8, "Anything goes rolls a Later as rarely as before: " + later + " of 4000");
    }

    static void lab() {
        ModularBrush b = FloatChecks.preset("Swell brush").copy().normalize();
        BrushLab.switched(b, PartChoice.LATER_FADE);
        check(b.choice(BrushPart.LATER) == PartChoice.LATER_FADE && b.choice(BrushPart.LIFE) == PartChoice.FAST_WET, "After's Fading chip adds Later, keeping the drying");
        BrushLab.switched(b, PartChoice.NONE);
        check(b.choice(BrushPart.LATER) == PartChoice.LATER_FADE && b.choice(BrushPart.LIFE) == PartChoice.NONE, "Instant keeps Fading");
        BrushLab.switched(b, PartChoice.LATER_MOLD);
        check(b.choice(BrushPart.LATER) == PartChoice.LATER_MOLD && b.choice(BrushPart.LIFE) == PartChoice.DRYING, "Mold brings Slow");
        BrushLab.switched(b, PartChoice.WET_PAINT);
        check(!b.later() && b.choice(BrushPart.LIFE) == PartChoice.WET_PAINT, "Wet drops Later");
        String words = ModularWords.plainWords(with("Swell brush", PartChoice.NONE, PartChoice.LATER_FADE, 0));
        check(words.contains("Dries at once. Fades away in 1 s."), "This brush says the combination: " + words);
    }

    /** {code, how the version before Later must write it, whether it must say it's newer}: old-form codes in full, new ones without Later. */
    static List<String[]> older() {
        List<String[]> out = new ArrayList<>();
        for (ToolPresets.Preset p : mischief()) out.add(new String[]{newForm(p.code), ModularCode.encode(withoutLater(ModularCode.decode(p.code))), "newer"});
        for (ModularBrush b : new ModularBrush[]{with("Swell brush", PartChoice.NONE, PartChoice.LATER_FADE, 0), with("Soft wash", PartChoice.DRYING, PartChoice.LATER_LEMON, .6),
                with("Stamp", PartChoice.NONE, PartChoice.LATER_LEMON, 0).withNib(12345)})
            out.add(new String[]{ModularCode.encode(b), ModularCode.encode(withoutLater(b)), "newer"});
        out.addAll(media(LaterMediaChecks.MEDIA));
        for (ToolPresets.Preset p : mischief()) out.add(new String[]{ModularCode.encode(ModularCode.decode(p.code)), p.code, "same"});
        return out;
    }
    /** Second Ink On, one brush for each medium that has it: {code, the plain brush's code, newer}, which any version before it reads as the plain brush with the "Newer version" note. */
    static List<String[]> second() {
        List<String[]> out = new ArrayList<>();
        java.util.Set<PartChoice> seen = new java.util.HashSet<>();
        for (ToolPresets.Preset p : ToolPresets.all()) {
            ModularBrush plain = ModularCode.decode(p.code), on = ModularCode.decode(p.code);
            PartChoice m = plain.choice(BrushPart.MEDIUM);
            if (!m.has(PartDial.SECOND) || !seen.add(m)) continue;
            on.part(BrushPart.MEDIUM).set(PartDial.SECOND, 1);
            // The plain brush as the older reader has it: the On code's dials read and written back with the switch Off (Angle's
            // 45 comes back as its base-36 step, so this is not always the code the brush had before the switch was touched).
            ModularBrush read = ModularCode.decode(ModularCode.encode(on));
            read.part(BrushPart.MEDIUM).set(PartDial.SECOND, 0);
            out.add(new String[]{ModularCode.encode(on), ModularCode.encode(read), "newer"});
        }
        check(out.size() == 5, "Second Ink is on five media: " + out.size());
        return out;
    }
    /** Later's media's codes, which any version before them (before Later, or with it) reads as their six parts and nib, Later Off, newer. */
    static List<String[]> media(PartChoice[] media) {
        List<String[]> out = new ArrayList<>();
        for (PartChoice c : media) { ModularBrush b = LaterMediaChecks.brush(c); out.add(new String[]{ModularCode.encode(b), ModularCode.encode(withoutLater(b)), "newer"}); }
        return out;
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
