package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A six-part brush: a name, a 32-bit seed (read unsigned), a style, and per part one choice and its dials
 * (parts.js 134–168, 386–390), Later's Off when it has none. Treat a Brush's as a value; change a copy. Pure, checked on the desktop.
 */
final class ModularBrush {
    /** One part: a choice and its dials' values, in the choice's dial order. */
    static final class Part {
        final PartChoice choice;
        private final double[] values;

        /**
         * Every dial at the choice's default; a hand preset sets its cells (parts.js freshPart 135–140). Not clamped,
         * as freshPart: a fresh hake's angle is 75, past the dial's 70, until normalized or written.
         */
        Part(PartChoice choice) {
            this.choice = choice;
            List<PartDial> dials = choice.dials;
            values = new double[dials.size()];
            for (int i = 0; i < values.length; i++) values[i] = choice.defaultOf(dials.get(i));
            if (choice.preset()) for (PartDial cell : PartDial.HAND_CELLS) values[dials.indexOf(cell)] = choice.cell(cell);
        }
        private Part(PartChoice choice, double[] values) { this.choice = choice; this.values = values; }

        boolean has(PartDial d) { return choice.has(d); }
        /** The value; 0 for a dial the choice doesn't have (parts.js val). */
        double get(PartDial d) { return get(d, 0); }
        /** The value; {@code absent} for a dial the choice doesn't have (the prototype's {@code ?? fallback}). */
        double get(PartDial d, double absent) { int i = choice.dials.indexOf(d); return i < 0 ? absent : values[i]; }
        /** Sets a dial the choice has. */
        void set(PartDial d, double v) {
            int i = choice.dials.indexOf(d);
            if (i < 0) throw new IllegalArgumentException(choice + " has no " + d);
            values[i] = v;
        }
        /**
         * parts.js normPart (150–163): a value that isn't a finite number takes the default; a choice dial rounds
         * into its options; others clamp to the choice's range; integer dials round. Returns this.
         */
        Part normalize() {
            for (int i = 0; i < values.length; i++) {
                PartDial d = choice.dials.get(i);
                double[] r = choice.range(d);
                double v = values[i];
                if (Double.isNaN(v) || Double.isInfinite(v)) v = choice.defaultOf(d);
                v = d.choice() ? StrokeMath.clamp(Math.round(v), 0, d.choices.length - 1) : StrokeMath.clamp(v, r[0], r[1]);
                if (d.integer) v = Math.round(v);
                values[i] = v;
            }
            return this;
        }
        Part copy() { return new Part(choice, values.clone()); }
        /** The same choice and every value equal. */
        boolean sameAs(Part other) { return other != null && choice == other.choice && Arrays.equals(values, other.values); }
    }

    String name;
    final long seed;
    /**
     * A Stamp tip's nib ({@link StampNib#id}, kept in the nib store), or 0 for none: a round stamp. The brush's, so a
     * tip swapped away and back keeps it; written in its code only with a Stamp tip ({@link ModularCode}).
     */
    int nib;
    /** Never null: its code begins with it (see {@link ModularCode#encode}). */
    final ModularStyle style;
    private final Part[] parts = new Part[BrushPart.values().length];

    /** Every part its first choice, fresh. */
    ModularBrush(String name, long seed, ModularStyle style) {
        this.name = name; this.seed = seed & 0xffffffffL; this.style = Objects.requireNonNull(style, "a six-part brush's style");
        for (BrushPart p : BrushPart.values()) parts[p.ordinal()] = new Part(p.choices().get(0));
    }

    Part part(BrushPart p) { return parts[p.ordinal()]; }
    PartChoice choice(BrushPart p) { return parts[p.ordinal()].choice; }
    /** Puts {@code part} in its own part's place. */
    void set(BrushPart p, Part part) {
        if (part.choice.part != p) throw new IllegalArgumentException(part.choice + " isn't a " + p + " choice");
        parts[p.ordinal()] = part;
    }
    /**
     * This brush as it draws on {@code surface} (the surface spec, 2026-11-01: "every brush follows the page's surface"):
     * its Paper part that surface, at its own Texture, how much of it shows through; the rest as it is. Its own code,
     * paper and all, is kept unchanged ({@code this} is never changed). Null, or the paper it has: this brush.
     */
    ModularBrush onSurface(BrushTexture surface) {
        Part paper = part(BrushPart.PAPER);
        if (surface == null || paper.choice.texture() == surface) return this;
        Part on = new Part(PartChoice.of(BrushPart.PAPER, surface.ordinal()));
        on.set(PartDial.TOOTH, paper.get(PartDial.TOOTH));
        ModularBrush b = copy();
        b.set(BrushPart.PAPER, on);
        return b;
    }

    /**
     * Whether {@code life} moves with the page's tilt while wet: Slow with a gravity dial up. Such a brush dries over the
     * Gravity Lab's longer range ({@link PartDial#wetMs(double, boolean)}).
     */
    static boolean runs(Part life) {
        if (life.choice != PartChoice.DRYING) return false;
        for (PartDial d : PartDial.GRAVITY) if (life.get(d) > 0) return true;
        return false;
    }
    /** How long its Slow paint stays wet, ms ({@link PartDial#wetMs(double, boolean)}). */
    long dryingMs() { Part life = part(BrushPart.LIFE); return PartDial.wetMs(life.get(PartDial.DRYING, .35), runs(life)); }

    /** parts.js handCell: the hand's cell in use, its amount times the response, at most 1. */
    double handCell(PartDial cell) { Part hand = part(BrushPart.HAND); return Math.min(1, or0(hand.get(cell)) * hand.get(PartDial.GAIN, 1)); }

    /** Whether its tip is a Stamp, which prints its {@link #nib}. */
    boolean stamps() { return choice(BrushPart.TIP) == PartChoice.STAMP; }
    /** A copy whose Stamp prints {@code nib} ({@link StampNib#keep}). */
    ModularBrush withNib(int nib) { ModularBrush b = copy(); b.nib = nib; return b; }

    /** What makes two brushes draw alike: its parts, seed and a Stamp's nib, not its name or style (parts.js brushKey). */
    String key() { return ModularCode.settings(this); }

    /**
     * Smudge's Own paint ({@code carry}, the brush's Smudge part): the share of its own paint the brush lays. 1 for Off,
     * 0 for Clear Water, which lays only water, Scrape, a blade, and Reprint, which lays marks there again; else the dial (parts.js ownPaint).
     */
    static double ownPaint(Part carry) {
        return carry.choice == PartChoice.OFF ? 1 : carry.choice == PartChoice.CLEAR_WATER || carry.choice == PartChoice.SCRAPE || carry.choice == PartChoice.REPRINT ? 0 : carry.get(PartDial.LOAD, 1);
    }
    /**
     * parts.js normBrush: a retired Drying mischief split ({@link #split}), every part normalized, and a Later that doesn't
     * go with Drying ({@link PartChoice#goesWith}: a part locked or rolled apart) given its own drying, keeping what it
     * can. Returns this.
     */
    ModularBrush normalize() {
        split();
        for (Part p : parts) p.normalize();
        PartChoice later = choice(BrushPart.LATER);
        if (!later.goesWith(choice(BrushPart.LIFE))) set(BrushPart.LIFE, switchTo(part(BrushPart.LIFE), later.drying()).normalize());
        // Vector Ink: what it can't draw sharp set aside, whatever made the brush (a roll, a swap, a code).
        VectorInk.tidy(this);
        return this;
    }

    /** Whether it has a Later: anything but Off. */
    boolean later() { return choice(BrushPart.LATER) != PartChoice.LATER_OFF; }
    /** Whether its paint floats (Float), its Later's or (not yet split, {@link #split}) its retired Drying's. */
    boolean floats() { return choice(BrushPart.LATER) == PartChoice.LATER_FLOAT || choice(BrushPart.LIFE) == PartChoice.FLOAT; }
    /** Its Later, or a retired Drying mischief's (not yet split): what changes the page after the pen lifts. */
    PartChoice mischief() { PartChoice retired = choice(BrushPart.LIFE).later(); return retired != null ? retired : choice(BrushPart.LATER); }
    /**
     * A retired Drying mischief (an old code's, a roll's) as Drying and Later, as the engine laid it: Float, Fading and
     * Lemon Juice on a fresh Quick, Mold on Slow at its drying time with no settling of its own; their other dials
     * Later's (Float's floating time its Floating Time). Returns this.
     */
    ModularBrush split() {
        Part life = part(BrushPart.LIFE);
        PartChoice later = life.choice.later();
        if (later == null) return this;
        Part l = new Part(later), dry = new Part(later.drying());
        for (PartDial d : later.dials) if (life.has(d)) l.set(d, life.get(d));
        if (later == PartChoice.LATER_FLOAT) l.set(PartDial.FLOAT_TIME, life.get(PartDial.DRYING));
        if (later == PartChoice.LATER_MOLD) { dry.set(PartDial.DRYING, life.get(PartDial.DRYING)); dry.set(PartDial.SETTLE, 0); }
        set(BrushPart.LIFE, dry);
        set(BrushPart.LATER, l);
        return this;
    }
    /**
     * Its Later as the retired Drying choice that holds it (Float, Mold on Slow's drying time, Fading, Lemon Juice), its
     * dials Later's; null without a Later. What the engine lays for Float and Mold, which are ways of drying.
     */
    Part retired() {
        Part later = part(BrushPart.LATER);
        PartChoice old = later.choice.retired();
        if (old == null) return null;
        Part j = new Part(old);
        for (PartDial d : old.dials) if (later.has(d)) j.set(d, later.get(d));
        if (old == PartChoice.FLOAT) j.set(PartDial.DRYING, later.get(PartDial.FLOAT_TIME));
        if (old == PartChoice.HALO) j.set(PartDial.DRYING, part(BrushPart.LIFE).get(PartDial.DRYING, old.defaultOf(PartDial.DRYING)));
        return j;
    }
    /**
     * The brush as a version before Later holds it, on a copy: its Later in Drying as the retired choice ({@link
     * #retired}), when splitting that gives this brush back exactly; itself with no Later, and null when a version before
     * Later can't hold it (a Fading on Slow, a Quick with its own Wet Edge under Float). Codes are written so
     * ({@link ModularCode#settings}), so every brush older versions had keeps its code and an older version reads it.
     * Null for Later's media too, never Drying's.
     */
    ModularBrush joined() {
        if (!later()) return this;
        Part retired = retired();
        if (retired == null) return null;
        ModularBrush j = copy();
        j.set(BrushPart.LIFE, retired);
        j.set(BrushPart.LATER, new Part(PartChoice.LATER_OFF));
        ModularBrush back = j.copy().split();
        return back.part(BrushPart.LIFE).sameAs(part(BrushPart.LIFE)) && back.part(BrushPart.LATER).sameAs(part(BrushPart.LATER)) ? j : null;
    }
    ModularBrush copy() { return copy(seed); }
    /** A copy with another seed. */
    ModularBrush copy(long seed) {
        ModularBrush b = new ModularBrush(name, seed, style);
        for (int i = 0; i < parts.length; i++) b.parts[i] = parts[i].copy();
        b.nib = nib;
        return b;
    }

    /**
     * parts.js switchChoice (143–149): {@code to}'s fresh part, keeping {@code old}'s values for the dials that mean
     * the same in both ({@code to.part.keeps()}; a paper's tooth not when the old paper was plain); a chisel's,
     * flat nib's or split nib's aspect when it is inside the new range; every value for a Custom hand.
     */
    static Part switchTo(Part old, PartChoice to) {
        Part out = new Part(to);
        if (old == null) return out;
        if (old.choice.part != to.part) throw new IllegalArgumentException(old.choice + " and " + to + " are different parts");
        BrushPart part = to.part;
        for (PartDial d : part.keeps())
            if (out.has(d) && old.has(d) && !(part == BrushPart.PAPER && old.choice == PartChoice.PLAIN)) out.set(d, old.get(d));
        if (to == PartChoice.CHISEL || to == PartChoice.FLAT_NIB || to == PartChoice.SPLIT_NIB) {
            double[] r = to.range(PartDial.ASPECT);
            if (old.has(PartDial.ASPECT) && old.get(PartDial.ASPECT) >= r[0] && old.get(PartDial.ASPECT) <= r[1]) out.set(PartDial.ASPECT, old.get(PartDial.ASPECT));
        }
        if (to == PartChoice.CUSTOM) for (PartDial d : old.choice.dials) if (out.has(d)) out.set(d, old.get(d));
        // A medium chosen keeps the Erasable it had (parts.js switchChoice), 1 from or to Resist (whose dial is wax or fluid).
        if (out.has(PartDial.ERASABLE)) out.set(PartDial.ERASABLE, old.has(PartDial.ERASABLE) && old.choice != PartChoice.RESIST && to != PartChoice.RESIST ? old.get(PartDial.ERASABLE) : to.erasable());
        return out;
    }

    /** parts.js matchHand: the first preset whose cells (the twelve, page tilt's off in every preset) are all within .005 of {@code hand}'s, else Custom. */
    static PartChoice matchHand(Part hand) {
        for (PartChoice c : BrushPart.HAND.choices()) {
            if (!c.preset()) continue;
            boolean all = true;
            for (PartDial cell : PartDial.GRID_CELLS) if (!(Math.abs(or0(hand.get(cell)) - c.cell(cell)) < .005)) { all = false; break; }
            if (all) return c;
        }
        return PartChoice.CUSTOM;
    }
    /**
     * The prototype's {@code P.k = matchHand(P)}: {@code hand}'s own values under the name of the preset it matches
     * (or Custom), on a copy. Every hand choice has the same dials, so the values carry over as they are.
     */
    static Part namedHand(Part hand) {
        if (hand.choice.part != BrushPart.HAND) throw new IllegalArgumentException(hand.choice + " isn't a hand");
        return new Part(matchHand(hand), hand.values.clone());
    }

    /** The prototype's {@code v || 0}. */
    private static double or0(double v) { return Double.isNaN(v) ? 0 : v; }
}
