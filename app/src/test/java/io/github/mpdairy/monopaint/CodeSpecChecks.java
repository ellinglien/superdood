package io.github.mpdairy.monopaint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * docs/brush-code.json, the open brush code's tables and test codes (docs/BRUSH-CODE.md): the styles, each part's
 * choices in code order with their dials (key, range or options, fresh value), and {@link #TESTS} with what each reads
 * as. Checks the file still matches what this version reads, and each test code reads as its row says; {@code write}
 * writes the file. Brush Studio's code-spec-check.cjs reads the same file.
 */
public class CodeSpecChecks {
    static final Path FILE = Paths.get("docs", "brush-code.json");
    /**
     * {code, what it is, how it reads}: "same" reads and writes back byte for byte; "reads" reads, written back
     * otherwise; "newer" is a newer version's, read with stand-ins; "needs newer" is a newer shape, not read; "no" doesn't read.
     */
    static final String[][] TESTS = {
            {"fineliner.M00gx00000000at_000zz0000000000000_0_000_0_8cl0000000000ee0000nz0.b", "Fineliner, the first built-in", "same"},
            {"watercolour.M60bi000000005e_1zzcl00000000_0_000_29b000js_8zzcl003m0000000000nz0.e", "Watercolour, the built-in: a wet wash drying slowly", "same"},
            {ModularCodeChecks.BRAVE_ONION, "The prototype's: a split nib in ink on ribbed paper, Dip pen style, a seed past 31 bits", "same"},
            {"wet-wash.M60i000000000ee_1wei000ee7700_0_1at_2i0ll0js_1ss0000000000000000nz0.2r", "The prototype's wash on cold press", "same"},
            {"wide-hake.Mc3i01zzcl77_1eeat00770000_0_000_0_8770000000000000000nz0.5", "A hake, its head turning with the page (a choice dial)", "same"},
            {"rake.Md5nzssi000000000_0llp6000000000at77_0_000_0_4weatg7000000ee0000nz0.b", "Multi-line, four lines, a Control preset", "same"},
            {"thick-oil.M925aeei00000p600at_2zzll_0_5i0_1rz_5ee0000ssi000000000nz0.7", "Cover (gouache) on canvas", "same"},
            {"h-pencil.M40i000000000at_48n00_0_2s8_1jz_87nge00000000000000nz000000000530000005000c099zz.51j8ud", "2H pencil: graphite, Control written to its last dial (Tilt Shading)", "same"},
            {"stipple-pen.M07i0clcli0_000zz0000000000000_0_000_1jz_0000000000000000000nz0.h6q", "Stipple tip", "same"},
            {"hatch-pen.M08at7x901zzi0_000zz0000000000000_0_000_1jz_0000000000000000000nz0.x3", "Hatch tip", "same"},
            {"spirograph.M0bi010gj08huu801i08r_000zz0000000000000_0_000_0_0000000000000000000nz0.2fb", "Spiro tip", "same"},
            {"stamp.M0agx1r0qe_000zz0000000000000_0_000_0_8cl0000000000ee0000nz0.2gd", "Stamp tip, no nib: a round stamp", "same"},
            {"potato.M0ai0b60qe_000zz0000000000000_0_000_0_0000000000000000000nz0_12lvawr.h", "Stamp tip with a nib's id in the seventh segment", "same"},
            {"vanishing-ink.M00i000000000at_000zz0000000000000_0_000_53a_0000000000000000000nz0.2fe", "Fading drying", "same"},
            {"lemon-juice.M20i000at0000at_000zz0000000000000_0_000_6hp_1ss0000000000000000nz0.2ff", "Lemon Juice drying", "same"},
            {"soft-wash.M60i0000000007q_1wva200000000_0_0fk_7_8la0000000000000000nz0.t4mf73", "Wet drying, at its defaults (the choice alone)", "same"},
            {"wax-crayon.Ma1okeei000000000at_7771_0_1i0_1jz_0000000000000000000nz0.oju", "Crayon, chisel, on cold press", "same"},
            {"oil-paint.M99vleei0000090005e_2zza300_2llp6zz00js_5at_1jz_1ss0000000000000000nz0.20l", "Filbert, Cover with Drag (paint load)", "same"},
            {"blend.M50i00000000yat_3eqlj_3ssll00zz000090zz_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", "Charcoal with Smear, Press to Push", "same"},
            {"water.M60i000000000at_1ulat00770000_4i0llzz_000_2g7ll0jsee00at_0000000000000000000nz0.20k", "Clear Water", "same"},
            {"fill-in.M30wd00000000at_1atat00000000_5zz_000_0_0000000000000000000nz0.2fh", "Stay in Lines, flooding", "same"},
            {"filled-shape.M30wd00000000at_1atat00000000_6_000_0_0000000000000000000nz0.2fi", "Filled Shape", "same"},
            {"masking-fluid.M60i000000000at_5_0_000_1jz_0000000000000000000nz0.px", "Resist medium", "same"},
            {"swell-brush.M10i0q6f40000at_000t7008h000000000_0_000_0_8wgft00dr0000ey0000nz0_0_33a.1s13pn4", "Later (8th segment): Fading on Instant, no nib (7th 0)", "same"},
            {"soft-wash.M60i0000000007q_1wva200000000_0_0fk_2clll0js_8la0000000000000000nz0_0_4hp.t4mf73", "Later: Lemon Juice on a settling Slow wash", "same"},
            {"stamp.M0agx1r0qe_000zz0000000000000_0_000_0_8cl0000000000ee0000nz0_9ix_4hp.2gd", "Later: Lemon Juice on a Stamp with a nib", "same"},
            {"soft-wash.M60i0000000007q_1wva200000000_0_0fk_2clll0js_8la0000000000000000nz0_0_5p618.t4mf73", "Later's media: Salt on a settling Slow wash, more grains", "same"},
            {"soft-wash.M60i0000000007q_1wva200000000_0_0fk_1jz_8la0000000000000000nz0_0_6ee25.t4mf73", "Later's media: Plastic Wrap on a Quick wash, Dries In 45 s", "same"},
            {"fountain-pen.M00i000at00003m_03mzzwe00000000000_0_000_0_8qz0000000000000000nz0_0_7cl5o.2f1", "Later's media: Feathering on an Instant pen, as it comes", "same"},
            {"swell-brush.M10i0q6f40000at_000t7008h000000000_0_000_1jz_8wgft00dr0000ey0000nz0_0_8ssad.1s13pn4", "Later's media: Foxing on a Quick brush pen, more spots", "same"},
            {"water-pencil.M40i000000000at_4p600zz008bi0000000001_0_000_0_25ess00000000000000nz000000000530000005000c099zz.2gp", "Water-Soluble on (Graphite's last dial): Water pencil", "same"},
            {"h-pencil.M40i000000000at_48n00zz008bi00000000001_0_2s8_1jz_87nge00000000000000nz000000000530000005000c099zz.51j8ud", "Second Ink on (Graphite's last dial): an H pencil", "same"},
            {"dip-nib.M20i000770000at_000zzat00000000000_21t3mzz00i0cl_000_0_1ss0000000000000000nz0.2go", "Paint Load on an ink line: Dip nib, which runs dry", "same"},
            {"palette-knife.M60i000000000at_1ulat00770000_7p6_000_0_0000000000000000000nz0.2gn", "Scrape (Smudge's choice 7): the Palette knife, Depth .7", "same"},
            {"hb-pencil.M40i000000000at00ll_4e551_0_1v5_1jz_8arsr00000000000000nz000000000530000005000c099zz.pg5zxt", "Wears Down .6, last of Round's dials, on the HB pencil", "same"},
            {"photocopier.Me0i000000000at_000zz0000000000000zz0g_8_000_0_0000000000000000000nz0.2h7", "Reprint (Smudge's choice 8, no dials): the Photocopier, through Photocopy", "same"},
            {"newsprint.Me0i000000000at_000zz0000000000000zz015ji000i0cl_8_9ss_0_0000000000000000000nz0.2h8", "Reprint: Newsprint, Halftone on library paper", "same"},
            {"riso.Me1vleezz00000000i0_000zz0000000000000zz0f8b009000ee_0_000_0_0000000000000000000nz0.2h9", "Risograph (Print Shading 15) on a chisel: the Riso (2026-11-01)", "same"},
            {"letterpress.Me0ss0000000000_000zz0000000000000_0_6ss_0_1ss0000000000000000nz0_0_95e1w.2ha", "Later's Letterpress, Impression .15, on cotton paper: the Letterpress", "same"},
            {"cracks.M80zz0000000077_2zzll_0_000_2clcl0js_3p6ll00000000000000nz0_0_cll6p.2hh", "Later's Cracks on Slow thick Opaque paint: Cracks (Gouache)", "same"},
            {"metalpoint.M403l000000003m_40000_0_1ee_0_85e7700000000000000nz0_0_di0hp.2gn", "Later's ageing: Tarnish on the Metalpoint, as it comes (2026-11-01)", "same"},
            {"swell-brush.M10i0q6f40000at_000t7008h000000000_0_000_0_8wgft00dr0000ey0000nz0_0_b0018.1s13pn4", "Later's crafts: Cross-Stitch on an Instant brush pen, 6 px stitches", "same"},
            {"polite-smear-2.M00gx00000000at_000zz0000000000000_0_000_0_8cl0000000000ee0000nz0.b", "A name with a digit (2026-10-31)", "same"},
            {"plain-ink.M00i000000000at_000zz0000000000000000f_0_000_1jz_0000000000000000000nz0.1y9", "Print Shading's Risograph, the first after Hatching (2026-10-31)", "same"},
            {"plain-ink.M00i000000000at_000zz0000000000000000ggm_0_000_1jz_0000000000000000000nz0.1y9", "Print Shading's Photocopy, Pattern Size 9", "same"},
            {"plain-ink.M00i000000000at_000zz0000000000000000h_0_000_1jz_0000000000000000000nz0.1y9", "Print Shading's Soft Dither (blue noise)", "same"},
            {"soft-wash.M60i0000000007q_1wva200000000ee0f_0_0fk_1jz_8la0000000000000000nz0.t4mf73", "A Risograph wash", "same"},
            {"plain-ink.M00i000000000at_000zz0000000000000000i_0_000_1jz_0000000000000000000nz0.1y9", "Print Shading's Maze, the first after Soft Dither (2026-11-06)", "same"},
            {"plain-ink.M00i000000000at_000zz0000000000000000n8b80_0_000_1jz_0000000000000000000nz0.1y9", "Print Shading's Rings, the last, at Angle 20", "same"},
            {"soft-wash.M60i0000000007q_1wva200000000ee0lgm_0_0fk_1jz_8la0000000000000000nz0.t4mf73", "A Waves wash, Pattern Size 9", "same"},
            {"plain-ink.M00i000000000at_000zz0000000000000000o_0_000_1jz_0000000000000000000nz0.1y9", "Print Shading's Pixels, after Rings (2026-11-06)", "same"},
            {"soft-wash.M60i0000000007q_1wva200000000ee0p_0_0fk_1jz_8la0000000000000000nz0.t4mf73", "A Stitches wash, the last", "same"},
            {"vanishing-ink.M00i000000000at_000zz0000000000000_0_000_1_0000000000000000000nz0_0_33a.2fe", "Fading on a fresh Quick in the 8th segment: Vanishing ink, written back the old way", "reads"},
            {"a.Md0_0_0_0_0_0.1", "A code that stops early: every dial at its fresh value", "reads"},
            {"  A.MD0_0_0_0_0_0.1\n", "Capitals and spaces around it", "reads"},
            {"a.Md0_0_0_0_0_0_5.1", "A seventh segment on a tip that isn't a Stamp: no nib, ignored", "reads"},
            {"hand-hatch-pen.M0ceeat901i0i0p6i0_000zz0000000000000_0_000_1jz_0000000000000000000nz0.x3", "Hand Hatch, retired: reads as Hatch", "reads"},
            {"brave-onion.Mz4p6okati000ee00_0atzz9000000020000_0_277_1e0_6ee00ee0000we000000nz0.1u5hvr4", "A newer style: Anything goes", "newer"},
            {"brave-onion.M2zp6okati000ee00_0atzz9000000020000_0_277_1e0_6ee00ee0000we000000nz0.1u5hvr4", "A newer tip: the part's first choice, fresh", "newer"},
            {"brave-onion.M24p6okati000ee00_0atzz90000000z0000_0_277_1e0_6ee00ee0000we000000nz0.1u5hvr4", "A newer option of Ink's Line Style: its fresh value", "newer"},
            {"wet-wash.M60i000000000ee_1wei000ee7700_0_1atzz_2i0ll0js_1ss0000000000000000nz0.2r", "A newer dial on Cold Press, past Texture: ignored", "newer"},
            {"fineliner.M00gx00000000at_000zz0000000000000_0_000_0_8cl0000000000ee0000nz0_0_z.b", "A Later choice past its list: Off", "newer"},
            {"fineliner.M00gx00000000at_000zz0000000000000_0_000_0_8cl0000000000ee0000nz0_0_3at_1.b", "A newer ninth segment: ignored", "newer"},
            {"brave-onion.N2ab_c.1x", "A newer shape (letter N): not read", "needs newer"},
            {"a.Md0_0_0_0_0.1", "Five segments", "no"},
            {"a.Md0__0_0_0_0.1", "An empty segment", "no"},
            {"a.Md0_0_0_0_0_0.zzzzzzz", "A seed past 32 bits", "no"},
            {"2a.Md0_0_0_0_0_0.1", "A name that starts with a digit", "no"},
    };

    static String spec() {
        StringBuilder s = new StringBuilder("{\n\"steps\": ").append(ModularCode.STEPS).append(",\n\"styles\": [");
        for (ModularStyle st : ModularStyle.all()) s.append(st.index > 0 ? ", " : "").append(q(st.name));
        s.append("],\n\"parts\": [");
        for (BrushPart p : BrushPart.values()) {
            s.append(p.ordinal() > 0 ? "," : "").append("\n{ \"part\": ").append(q(key(p))).append(", \"choices\": [");
            for (PartChoice c : p.choices()) {
                ModularBrush.Part fresh = new ModularBrush.Part(c);
                s.append(c.index() > 0 ? "," : "").append("\n  { \"key\": ").append(q(c.key)).append(", \"dials\": [");
                for (int i = 0; i < c.dials.size(); i++) {
                    PartDial d = c.dials.get(i);
                    double[] r = c.range(d);
                    s.append(i > 0 ? ", " : "").append("{ \"key\": ").append(q(d.key));
                    if (d.choice()) s.append(", \"options\": ").append(d.choices.length);
                    else {
                        s.append(", \"min\": ").append(n(d.min)).append(", \"max\": ").append(n(d.max));
                        if (r[0] != d.min || r[1] != d.max) s.append(", \"clamp\": [").append(n(r[0])).append(", ").append(n(r[1])).append("]");
                        if (d.integer) s.append(", \"integer\": true");
                    }
                    s.append(", \"fresh\": ").append(n(fresh.get(d)));
                    if (ModularCode.ADDED.contains(d)) s.append(", \"trimmed\": true");
                    s.append(" }");
                }
                s.append("] }");
            }
            s.append("\n] }");
        }
        s.append("\n],\n\"tests\": [");
        for (int t = 0; t < TESTS.length; t++) {
            String code = TESTS[t][0];
            ModularBrush b = ModularCode.decode(code);
            s.append(t > 0 ? "," : "").append("\n{ \"code\": ").append(q(code)).append(", \"about\": ").append(q(TESTS[t][1]))
                    .append(", \"newer\": ").append(ModularCode.newer(code));
            if (b == null) { s.append(", \"reads\": false }"); continue; }
            s.append(", \"reads\": true, \"writes\": ").append(q(ModularCode.encode(b))).append(",\n  \"name\": ").append(q(b.name))
                    .append(", \"style\": ").append(q(b.style.name)).append(", \"seed\": ").append(b.seed);
            if (b.nib != 0) s.append(", \"nib\": ").append(b.nib & 0xffffffffL);
            s.append(",\n  \"parts\": {");
            for (BrushPart p : BrushPart.values()) {
                ModularBrush.Part part = b.part(p), fresh = new ModularBrush.Part(part.choice).normalize();
                s.append(p.ordinal() > 0 ? ", " : " ").append(q(key(p))).append(": { \"choice\": ").append(q(part.choice.key)).append(", \"dials\": {");
                String sep = " ";
                for (PartDial d : part.choice.dials) {
                    if (part.get(d) == fresh.get(d)) continue;
                    s.append(sep).append(q(d.key)).append(": ").append(n(part.get(d)));
                    sep = ", ";
                }
                s.append(" } }");
            }
            s.append(" } }");
        }
        return s.append("\n]\n}\n").toString();
    }
    private static String key(BrushPart p) { return p.name().toLowerCase(Locale.ROOT); }
    /** A number as the file writes it: at most 6 decimals, no trailing zeros. */
    private static String n(double v) { return new BigDecimal(v).setScale(6, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString(); }
    private static String q(String t) { return "\"" + t.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""; }

    public static void main(String[] args) throws Exception {
        String want = spec();
        if (args.length > 0 && args[0].equals("write")) {
            Files.write(FILE, want.getBytes(StandardCharsets.UTF_8));
            System.out.println("wrote " + FILE);
            return;
        }
        for (String[] t : TESTS) {
            ModularBrush b = ModularCode.decode(t[0]);
            boolean newer = ModularCode.newer(t[0]);
            String how = b == null ? (newer ? "needs newer" : "no") : newer ? "newer" : ModularCode.encode(b).equals(t[0]) ? "same" : "reads";
            check(how.equals(t[2]), t[1] + " reads as " + t[2] + ", not " + how + ": " + t[0]);
        }
        // Old codes read as before: no Basic tool, retired one or prototype fixture is a newer version's.
        for (ToolPresets.Preset p : ToolPresets.withRetired()) check(!ModularCode.newer(p.code), p.name + "'s code is this version's");
        for (String c : ModularCodeChecks.FIXTURES) check(!ModularCode.newer(c), "The prototype's code is this version's: " + c);
        check(ModularCode.slug("Polite Smear 2").equals("polite-smear-2") && ModularCode.slug("2H pencil").equals("h-pencil") && ModularCode.slug(" ??3 ").equals("brush")
                && BrushCode.slug("Polite Smear 2").equals("polite-smear"), "A name keeps its digits after its first letter; an old I/P code's slug doesn't");
        String have = new String(Files.readAllBytes(FILE), StandardCharsets.UTF_8);
        check(have.equals(want), FILE + " no longer matches what the app reads: run bash scripts/brush-code-json.sh, then check docs/BRUSH-CODE.md");
        System.out.println("PASS: " + FILE + " matches the app's codes (styles, parts, choices, dials) and its " + TESTS.length
                + " test codes read as written, a newer version's with stand-ins; no Basic tool's code reads as newer; names keep their digits");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
