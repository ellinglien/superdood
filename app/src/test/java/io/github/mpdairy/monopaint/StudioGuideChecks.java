package io.github.mpdairy.monopaint;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Brush Studio's Guide tab shows only what the app's Brush Lab offers (the owner, 2026-10-30: "limited to what's in the
 * app currently"): docs/brush-lab/app-features.json, every part in {@link BrushPart#SHOWN_LATER}'s order with its label, the
 * choices its chips offer ({@link ModularFeatures#offered}: Hand Hatch, cut, is left out) and each one's dials as its
 * card shows them ({@link ModularFeatures#shown}), with the choice's own labels. Checks the file still matches the enums;
 * {@code write} writes it (docs/brush-lab/studio-src/app-features.sh).
 */
public class StudioGuideChecks {
    static final Path FILE = Paths.get("docs", "brush-lab", "app-features.json");
    /** Control's dials that are no row of the lab's Control card ({@link BrushLab#handRows}): Tilt Range is Tilt Size's fourth step, the swatch sets are swatches. */
    static final Set<PartDial> NOT_ROWS = EnumSet.of(PartDial.TILT_RANGE, PartDial.SHIFT_BAR, PartDial.SHIFT_ANY, PartDial.SHIFT_ANY2);

    static String features() {
        StringBuilder s = new StringBuilder("{\n");
        String ps = "";
        for (BrushPart p : BrushPart.SHOWN_LATER) {
            s.append(ps).append(q(p.name().toLowerCase(Locale.ROOT))).append(": { \"label\": ").append(q(p.label)).append(", \"choices\": {");
            String cs = "\n";
            for (PartChoice c : p.choices()) {
                if (!ModularFeatures.offered(c)) continue;
                s.append(cs).append("  ").append(q(c.key)).append(": { \"label\": ").append(q(c.label)).append(", \"dials\": {");
                String ds = " ";
                for (PartDial d : c.dials) {
                    if (!ModularFeatures.shown(d) || NOT_ROWS.contains(d)) continue;
                    s.append(ds).append(q(d.key)).append(": ").append(q(c.labelOf(d)));
                    ds = ", ";
                }
                s.append(" } }");
                cs = ",\n";
            }
            s.append("\n} }");
            ps = ",\n";
        }
        return s.append("\n}\n").toString();
    }
    private static String q(String t) { return "\"" + t.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    public static void main(String[] args) throws Exception {
        String want = features();
        if (args.length > 0 && args[0].equals("write")) {
            Files.write(FILE, want.getBytes(StandardCharsets.UTF_8));
            System.out.println("wrote " + FILE);
            return;
        }
        String have = new String(Files.readAllBytes(FILE), StandardCharsets.UTF_8);
        check(have.equals(want), FILE + " no longer matches the app's choices and dials: run docs/brush-lab/studio-src/app-features.sh and rebuild Studio");
        check(!want.contains("\"handhatch\""), "Hand Hatch, cut, is left out");
        check(want.contains("\"press\": \"Press to Push\"") && want.contains("\"press\": \"Press to Lift\""), "Smudge's Press reads Press to Push on Smear and Press to Lift on Clear Water");
        check(!want.contains("\"takes\"") && !want.contains("\"plume\""), "Takes Ink and Plume, which the lab hides, are left out");
        System.out.println("PASS: " + FILE + " matches the app's Brush Lab: its parts, offered choices and shown dials, with their labels");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
