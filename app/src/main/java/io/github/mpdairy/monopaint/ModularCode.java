package io.github.mpdairy.monopaint;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Six-part share codes, also how six-part brushes are stored, as the prototype's encodeM and its M reading (parts.js
 * 544–597), byte for byte: {@code name.M<style><tip>_<medium>_<carry>_<paper>_<life>_<hand>.<seed>}; a Stamp tip with
 * a nib adds {@code _<nib>} after the hand, the nib's id in base 36 ({@link StampNib}); a Later an older version can't
 * hold in Drying adds {@code _<later>} after that ({@code _0} for no nib; {@link #settings}). Each part is its
 * choice's index as one base-36 digit, then its choice's dials in order: a choice dial one digit, any other two base-36
 * digits over the dial's range (not the choice's narrower one). New dials go on the end of a choice's list; a code
 * that stops early reads them as defaults. Dials the prototype doesn't have ({@link #ADDED}) are written only when
 * one of them is off its default, so a brush that doesn't use them keeps its code, the prototype's. The open spec is
 * docs/BRUSH-CODE.md: a code from a newer version reads what this one knows ({@link #newer}). Pure, checked on the
 * desktop.
 */
final class ModularCode {
    /**
     * Dials added after the prototype's, last in their choices' lists: Tilt range; gravity's (Runs, Drips, Pool, Sink,
     * Blend, Dust and the hand's page tilt); Bristles' Ink load; every medium's Erasable and Blend; Stipple's Build-up;
     * Smear's Soften; Screen and its dials (the mischief modules spec), and Tone's; Drag's and Smear's Keeps Paint;
     * every tip's Flick and every Blend medium's Fling (Stage 2); Drag's and Smear's Paint Load and Starting Load (the
     * oil paint spec); the hand's Spiro drives, Grey Shift and Gradient; Wet's Stays Wet (the wet paint spec); the hand's
     * Tilt Shading; Smear's and Clear Water's Press (Press to Push, Press to Lift); Round's and Chisel's Wears Down; Ink's, Charcoal's and
     * Graphite's Dissolves (Water-Soluble); Ink's, Wash's, Cover's, Charcoal's and Graphite's Second Ink.
     */
    static final Set<PartDial> ADDED = Collections.unmodifiableSet(EnumSet.of(PartDial.TILT_RANGE, PartDial.RUNS, PartDial.DRIPS,
            PartDial.G_POOL, PartDial.SINK, PartDial.BLEND, PartDial.DUST, PartDial.GS, PartDial.GD, PartDial.GW, PartDial.INK_LOAD, PartDial.ERASABLE, PartDial.M_BLEND, PartDial.BUILD_UP, PartDial.SOFTEN, PartDial.KEEPS,
            PartDial.SCREEN, PartDial.CELL, PartDial.S_ANGLE, PartDial.MIS, PartDial.S_GAIN, PartDial.UNEVEN, PartDial.PATTERN, PartDial.TTONE,
            PartDial.FLICK, PartDial.FLING, PartDial.PLOAD, PartDial.PMIN, PartDial.P_DRIVE, PartDial.S_DRIVE, PartDial.T_DRIVE,
            PartDial.SHIFT, PartDial.SHIFT_ORDER, PartDial.SHIFT_GREYS, PartDial.SHIFT_BAR, PartDial.SHIFT_ANY, PartDial.SHIFT_ANY2,
            PartDial.GRADIENT, PartDial.GRAD_TO, PartDial.GRAD_LEN, PartDial.STAYS_WET, PartDial.SIDE_SHADE, PartDial.PRESS, PartDial.WEAR,
            PartDial.STEADY_HAND, PartDial.LONG_STICK, PartDial.SOLUBLE, PartDial.SECOND));
    /** Steps across a dial's range: the most two base-36 digits hold. */
    static final int STEPS = 1295;
    /** A name's slug may hold digits after its first letter (2026-10-31); older codes, letters only, still match. */
    private static final String NAME = "[a-z][a-z0-9]*(?:-[a-z0-9]+)*";
    private static final Pattern CODE = Pattern.compile("(" + NAME + ")\\.M([0-9a-z])([0-9a-z_]+)\\.([0-9a-z]{1,7})");
    /** A code of a shape this version doesn't know: a name, then a capital other than the old engines' I and P, the lab's B and M. */
    private static final Pattern OTHER_SHAPE = Pattern.compile(NAME + "\\.[A-Z&&[^BIMP]][0-9a-z_.]*");

    private ModularCode() { }

    static String encode(ModularBrush b) {
        return slug(b.name) + ".M" + Integer.toString(b.style.index, 36) + settings(b);
    }
    /** A name as an M code carries it: lowercase letters and digits in hyphenated words, from its first letter; "brush" if nothing is left. */
    static String slug(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^[^a-z]+|-$", "");
        return slug.isEmpty() ? "brush" : slug;
    }
    /** parts.js brushKey: what makes two brushes draw alike, the parts and the seed without name or style. See {@link ModularBrush#key}. */
    static String settings(ModularBrush b) {
        // A Later that a version before Later holds in Drying (Fading on Quick, as every one was) is written so: its code
        // is the one it always had, and an older version reads all of it.
        ModularBrush joined = b.joined();
        ModularBrush w = joined != null ? joined : b;
        StringBuilder out = new StringBuilder();
        for (BrushPart p : BrushPart.values()) {
            if (p.ordinal() >= BrushPart.SIX) break;
            if (p.ordinal() > 0) out.append('_');
            writePart(out, w.part(p));
        }
        // A Stamp's nib, after the six: only a Stamp with one writes it, so every other code is as before; a Later that
        // needs its own segment (8th) writes the 7th as 0 when there's no nib.
        boolean nib = w.stamps() && w.nib != 0;
        if (nib || w.later()) out.append('_').append(nib ? Long.toString(w.nib & 0xffffffffL, 36) : "0");
        if (w.later()) { out.append('_'); writePart(out, w.part(BrushPart.LATER)); }
        return out.append('.').append(Long.toString(w.seed & 0xffffffffL, 36)).toString();
    }

    /**
     * The brush an M code describes, its name the code's slug, normalized; null if it isn't an M code that reads. Case
     * doesn't matter. One from a newer version ({@link #newer}) reads what this one knows: a style or choice it doesn't
     * know as Anything goes or the part's first choice, fresh; an option past a dial's as its default; dials and
     * segments past the ones it knows not at all.
     */
    static ModularBrush decode(String code) { return read(code, new boolean[1]); }
    /**
     * Whether {@code code} is from a newer version: an M code holding a style, choice, option, dial or segment this
     * version doesn't know ({@link #decode} stands them in), or a code of a shape it doesn't know (which doesn't read).
     */
    static boolean newer(String code) {
        boolean[] newer = new boolean[1];
        return read(code, newer) != null && newer[0] || code != null && OTHER_SHAPE.matcher(BrushCode.written(code.trim())).matches();
    }
    /** {@link #decode}, {@code newer[0]} set when the code holds something this version doesn't know. */
    private static ModularBrush read(String code, boolean[] newer) {
        if (code == null) return null;
        Matcher m = CODE.matcher(BrushCode.written(code.trim()));
        if (!m.matches()) return null;
        ModularStyle style = ModularStyle.at(Character.digit(m.group(2).charAt(0), 36));
        long seed = Long.parseLong(m.group(4), 36);
        String[] segments = m.group(3).split("_", -1);
        int six = BrushPart.SIX;
        if (seed > 0xffffffffL || segments.length < six) return null;
        if (style == null) { newer[0] = true; style = ModularStyle.named(ModularStyle.ANYTHING_GOES); }
        // The six, then the Stamp's nib, then Later: a segment past those is a newer version's.
        if (segments.length > six + 2) newer[0] = true;
        ModularBrush b = new ModularBrush(m.group(1), seed, style);
        for (BrushPart p : BrushPart.values()) {
            if (p.ordinal() >= six) break;
            ModularBrush.Part part = readPart(p, segments[p.ordinal()], newer);
            if (part == null) return null;
            b.set(p, part);
        }
        // Hand Hatch, cut (the refocus, 2026-10-24): its codes still read, as Hatch, the nearest tip.
        if (b.choice(BrushPart.TIP) == PartChoice.HAND_HATCH) b.set(BrushPart.TIP, asHatch(b.part(BrushPart.TIP)));
        // Vector ink's first code (2026-11-04, before its medium): the Basic tool's now, under its own name.
        if (VectorInk.FIRST.endsWith("." + m.group(4)) && VectorInk.FIRST.contains(".M" + m.group(2) + m.group(3) + ".")) { ModularBrush v = read(VectorInk.CODE, newer); v.name = b.name; return v; }
        // A Stamp's nib: a seventh segment (one that doesn't read is no nib: the round stamp).
        if (b.stamps() && segments.length > six) {
            String nib = segments[six];
            if (nib.matches("[0-9a-z]{1,7}")) { long id = Long.parseLong(nib, 36); if (id <= 0xffffffffL) b.nib = (int) id; }
        }
        // Later, the eighth: Drying's retired mischief splits into it as normalize does ({@link ModularBrush#split}), so an
        // eighth segment that has one is the newer word on it.
        if (segments.length > six + 1) {
            ModularBrush.Part later = readPart(BrushPart.LATER, segments[six + 1], newer);
            if (later == null) return null;
            b.split();
            if (later.choice != PartChoice.LATER_OFF) b.set(BrushPart.LATER, later);
        }
        return b.normalize();
    }

    /** A Hand Hatch tip as Hatch: the dials the two share (Spacing, Line Weight, Line Angle, the head's turn, Size, Flick) kept, the rest Hatch's defaults. */
    static ModularBrush.Part asHatch(ModularBrush.Part hand) {
        ModularBrush.Part hatch = new ModularBrush.Part(PartChoice.HATCH);
        for (PartDial d : PartChoice.HATCH.dials) if (hand.has(d)) hatch.set(d, hand.get(d));
        return hatch;
    }

    /** parts.js writePart, up to the last dial that isn't an {@link #ADDED} one at its default. */
    private static void writePart(StringBuilder out, ModularBrush.Part part) {
        out.append(Integer.toString(part.choice.index(), 36));
        List<PartDial> dials = part.choice.dials;
        int end = dials.size();
        while (end > 0 && ADDED.contains(dials.get(end - 1)) && part.get(dials.get(end - 1)) == part.choice.defaultOf(dials.get(end - 1))) end--;
        for (PartDial d : dials.subList(0, end)) {
            double v = part.get(d);
            if (d.choice()) { out.append(Integer.toString((int) v, 36)); continue; }
            int step = (int) Math.round((StrokeMath.clamp(v, d.min, d.max) - d.min) / (d.max - d.min) * STEPS);
            if (step < 36) out.append('0');
            out.append(Integer.toString(step, 36));
        }
    }
    /**
     * parts.js readPart (562–577): the choice, then its dials while the segment lasts (the rest stay at defaults);
     * null for an empty segment. Sets {@code newer[0]} for a newer version's: a choice past the list (the part's first choice, fresh), an option
     * past a choice dial's (its default) or characters left over (dials this version doesn't have).
     */
    private static ModularBrush.Part readPart(BrushPart p, String segment, boolean[] newer) {
        if (segment.isEmpty()) return null;
        PartChoice choice = PartChoice.of(p, Character.digit(segment.charAt(0), 36));
        if (choice == null) { newer[0] = true; return new ModularBrush.Part(p.choices().get(0)); }
        ModularBrush.Part part = new ModularBrush.Part(choice);
        int at = 1;
        for (PartDial d : choice.dials) {
            if (d.choice()) {
                if (at >= segment.length()) break;
                int option = Character.digit(segment.charAt(at++), 36);
                if (option < d.choices.length) part.set(d, option); else newer[0] = true;
                continue;
            }
            if (at + 2 > segment.length()) break;
            // Two base-36 digits hold at most STEPS.
            int step = Integer.parseInt(segment.substring(at, at + 2), 36);
            at += 2;
            part.set(d, d.min + step / (double) STEPS * (d.max - d.min));
        }
        if (at < segment.length()) newer[0] = true;
        return part;
    }
}
