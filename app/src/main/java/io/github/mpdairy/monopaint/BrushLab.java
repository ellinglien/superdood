package io.github.mpdairy.monopaint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Brush Lab's state, as the prototype's: copies of the nine slots' brushes and the selected one it works on (the slot it
 * opened on: the lab shows no other since 2026-10-29), the settings locked, the lab's own Undo, what it last did ({@link
 * #status}) and the library's cards. A slot holds a six-part brush, with its parts and dials. The library is written at
 * once (Save, Save as new, delete), as are each slot's size and grey (the drawing screen's); the slots only take their
 * brushes when the drawing screen does, on Back. What each slot held at the start ({@link #start}) goes back to the
 * Tools page; the brush a change lets go is Undo's, and kept in Recent when unsaved ({@link ToolSlots#keep}, its note
 * {@link #note}; Back to went 2026-11-03).
 * <p>
 * New, Open and Save (the owner, 2026-10-29, option A: "once it's edited, maybe suggest a new name?"): the lab knows the
 * library entry the brush came from ({@link #savedId}: opened from the library, or saved here; never the slot's own
 * brush), and the brush as it was then ({@link #edited}). {@link #save} writes that entry over in
 * place, keeping its name unless one was given ({@link #rename}); {@link #saveAsNew} always adds a copy, under the
 * suggested name ({@link #suggestion}) unless one was given ({@link #save(boolean)}: Save, its As New on or not);
 * {@link #newBrush} (New ▾'s styles) and {@link #load} (a pasted code) start a brush no entry holds. Pure, checked on the desktop.
 */
final class BrushLab {
    /** How many changes Undo can take back. */
    static final int HISTORY = 40;

    /** A library card: a brush, the slots holding it (0-based, in order), and the library entry it stands for. */
    static final class Card {
        final Brush brush;
        final List<Integer> slots;
        final BrushLibrary.Entry entry;
        Card(Brush brush, List<Integer> slots, BrushLibrary.Entry entry) { this.brush = brush; this.slots = slots; this.entry = entry; }
        List<String> caption() { return BrushCaptions.describe(brush); }
        /** A library card can be deleted. */
        boolean deletable() { return entry != null; }
    }

    /** A swap strip tile: the lab's six-part brush with only one part switched to {@code choice}; {@code now} when it is the part's choice already. */
    static final class Swap {
        final PartChoice choice;
        final Brush brush;
        final boolean now;
        Swap(PartChoice choice, Brush brush, boolean now) { this.choice = choice; this.brush = brush; this.now = now; }
        /** Its tile's caption (the swap panel's, {@link SwapStrip}, the browser's): the choice's label, then its note, if any. */
        List<String> caption() {
            List<String> caption = new ArrayList<>();
            caption.add(ModularWords.tileLabel(choice));
            String note = ModularWords.swapNote(brush.parts, choice.part);
            if (!note.isEmpty()) caption.add(note);
            return caption;
        }
    }

    /**
     * What a change to the six-part brush draws again (e-ink: every region drawn again can blink). {@code all}: a new
     * brush (another seed or style, or none shown yet), so every part's module and the swap strip's tiles; else only
     * the {@code parts} whose own choice or dials changed: their modules' choice and sample, nothing of the others,
     * though a part's sample shows the whole brush. A rename draws none.
     */
    static final class Redraw {
        static final Redraw NONE = new Redraw(false, EnumSet.noneOf(BrushPart.class)), ALL = new Redraw(true, EnumSet.allOf(BrushPart.class));
        final boolean all;
        final Set<BrushPart> parts;
        private Redraw(boolean all, Set<BrushPart> parts) { this.all = all; this.parts = Collections.unmodifiableSet(parts); }
        boolean part(BrushPart part) { return parts.contains(part); }
        boolean nothing() { return parts.isEmpty(); }
    }
    /**
     * The part whose choices the swap panel shows after the pen lands on {@code hit}'s module (null: on no module) while
     * {@code open}'s are open: another part's, in one step (the owner, 2026-10-23: "if medium picker is open and i tap
     * paper, open the paper panel"); null, the panel closing, on its own module or anywhere else.
     */
    static BrushPart swapTo(BrushPart open, BrushPart hit) { return hit != open ? hit : null; }
    /** What going from {@code before} (null: nothing shown yet) to {@code after} draws again. Neither changes. */
    static Redraw redraw(ModularBrush before, ModularBrush after) {
        if (after == null) return Redraw.NONE;
        if (before == null || before.seed != after.seed || before.style != after.style) return Redraw.ALL;
        EnumSet<BrushPart> changed = EnumSet.noneOf(BrushPart.class);
        for (BrushPart part : BrushPart.values()) if (!before.part(part).sameAs(after.part(part))) changed.add(part);
        // A Stamp's nib is its tip's.
        if (before.nib != after.nib) changed.add(BrushPart.TIP);
        return changed.isEmpty() ? Redraw.NONE : new Redraw(false, changed);
    }

    final ToolSlots slots;
    /** The locked parts and dials. */
    final ModularLocks partLocks = new ModularLocks();
    /**
     * What the last change did, in three words or so (docs/TEXT-STYLE.md R7). The screen has no status line (owner's
     * design): each change shows where it happens, and only a code that won't load is said, in the Load sheet; the
     * checks read it all.
     */
    String status;
    private final Random random;
    /** Each change, latest last: its slot, that slot's brush before (copied before it changes) and where it stood with the library. */
    private final ArrayDeque<Step> history = new ArrayDeque<>();
    private static final class Step {
        final int slot;
        final Brush before, opened;
        final Long savedId;
        final boolean named;
        Step(int slot, Brush before, Long savedId, Brush opened, boolean named) { this.slot = slot; this.before = before; this.savedId = savedId; this.opened = opened; this.named = named; }
    }
    /** The library entry the brush came from or was saved to, or null (a slot's own brush, New, a pasted code). */
    private Long savedId;
    /** The brush as it was opened or saved: anything else is {@link #edited}. */
    private Brush opened;
    /** Whether a name was given since (typed, or the suggestion taken): Save keeps it, Save as New uses it. */
    private boolean named;
    /** What each slot held at the start: when the lab opened, or when the Tools page before it did. */
    final ToolSlots.Start start;
    /** What the latest change says once (the brush it kept in Recent), or null. */
    private String note;
    /** The lab's copies of the nine slots' brushes, which Back returns; and the one it works on. */
    private final Brush[] brushes = new Brush[ToolSlots.COUNT];
    private int selected;
    /** {@link #brush()}'s parts: the lab's own, which it changes in place. */
    private ModularBrush parts;

    BrushLab(ToolSlots slots, int selected, Random random) { this(slots, selected, random, null); }
    /** As above, the slots' start {@code start} ({@link ToolSlots.Start#write}'s lines; null: as they are now). */
    BrushLab(ToolSlots slots, int selected, Random random, String[] start) {
        this.slots = slots; this.selected = selected; this.random = random;
        this.start = ToolSlots.Start.read(start, slots);
        for (int i = 0; i < ToolSlots.COUNT; i++) brushes[i] = copyOf(slots.brush(i));
        show(brushes[selected]);
        track();
        status = openedSaid();
    }

    /** The selected slot's brush, the one the lab works on. */
    Brush brush() { return brushes[selected]; }
    int selected() { return selected; }
    /** Slot {@code i}'s brush in the lab. */
    Brush slot(int i) { return brushes[i]; }
    /** The brush's parts, which the lab changes in place. */
    ModularBrush parts() { return parts; }
    boolean canUndo() { return !history.isEmpty(); }
    /** The brush's share code (an M code). */
    String code() { return ModularCode.encode(parts); }
    /** What Back returns: the nine slots' brushes. */
    Brush[] results() {
        Brush[] results = new Brush[ToolSlots.COUNT];
        for (int i = 0; i < ToolSlots.COUNT; i++) results[i] = copyOf(brushes[i]);
        return results;
    }
    /** Whether {@code card} is the brush in the lab. */
    boolean now(Card card) { return card.brush.sameAs(brush()); }

    /** The lab now works on slot {@code i}; not a change Undo takes back. */
    void select(int i) {
        selected = i;
        show(brushes[i]);
        track();
        status = "Slot " + (i + 1);
    }
    /** A library card goes in the selected slot; the brush it holds already changes nothing (false). */
    boolean put(Brush picked) {
        if (picked.sameAs(brush())) { status = "Already in slot " + (selected + 1); return false; }
        letGo();
        remember();
        show(copyOf(picked));
        status = picked.name() + " in slot " + (selected + 1);
        return true;
    }
    /**
     * A random library brush, not the one the selected slot holds, goes in it. Returns its card's index in
     * {@link #library}, or -1 if there is no other.
     */
    int surprise() {
        List<Card> cards = library();
        List<Brush> brushes = new ArrayList<>();
        for (Card card : cards) brushes.add(card.brush);
        int index = surprise(brushes, brush(), random);
        if (index < 0) { status = "Library empty"; return -1; }
        Brush picked = cards.get(index).brush;
        put(picked);
        status = picked.name() + " in slot " + (selected + 1);
        return index;
    }
    /** A random one of {@code library}'s brushes that is not {@code current}: its index, or -1 if there is no other (Brush Lab's Surprise me). */
    static int surprise(List<Brush> library, Brush current, Random random) {
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < library.size(); i++) if (!library.get(i).sameAs(current)) pool.add(i);
        return pool.isEmpty() ? -1 : pool.get(random.nextInt(pool.size()));
    }
    /** A new six-part brush in the brush's style, hand and all, keeping what's locked. */
    void randomize() {
        ModularStyle style = parts.style;
        rollNew(style);
        status = "Randomized " + parts.name;
    }
    /** Anything Goes: a new brush in Anything goes, even when the brush is one already; locks kept, one Undo step. */
    void anythingGoes() {
        rollNew(ModularStyle.named(ModularStyle.ANYTHING_GOES));
        status = "Randomized " + parts.name;
    }
    /** Cut Stamp ({@link StampNib}): the brush's Stamp prints nib {@code nib} from now on, a change Undo takes back. */
    void cutNib(int nib) {
        if (parts.nib == nib) return;
        remember();
        show(Brush.of(parts.withNib(nib)));
        status = StampNib.DONE;
    }
    /**
     * A part's choice chip: {@code choice}'s part switches to it, keeping the dials both share ({@link #switched}).
     */
    void choose(BrushPart part, PartChoice choice) {
        if (parts.choice(choice.part) == choice) return;
        remember();
        switched(parts, choice);
        status = choice.part.label + ": " + choice.label;
    }
    /**
     * {@code b}'s part switched to {@code c}, keeping the dials both share: a drying that a Later doesn't go with drops
     * the Later ({@link PartChoice#goesWith}); a Later that doesn't go with the drying brings its own ({@link
     * ModularBrush#normalize}).
     */
    static void switched(ModularBrush b, PartChoice c) {
        if (c.part == BrushPart.LIFE && !b.choice(BrushPart.LATER).goesWith(c)) b.set(BrushPart.LATER, new ModularBrush.Part(PartChoice.LATER_OFF));
        b.set(c.part, ModularBrush.switchTo(b.part(c.part), c).normalize());
        b.normalize();
    }
    /** A slider is pressed: remembers the brush for Undo. */
    void startSlide() { remember(); }
    /** A slider moves: the dial follows; a hand cell makes the hand Custom meanwhile. */
    void slide(PartDial dial, double value) { slide(parts, dial, value); }
    /** {@link #slide(PartDial, double)} on {@code parts} (Brush Studio's too, WebEngine.studioEdit). */
    static void slide(ModularBrush parts, PartDial dial, double value) {
        ModularBrush.Part p = parts.part(dial.part);
        if (!p.has(dial)) return;
        p.set(dial, value);
        if (Arrays.asList(PartDial.GRID_CELLS).contains(dial) && p.choice != PartChoice.CUSTOM) parts.set(BrushPart.HAND, ModularBrush.switchTo(p, PartChoice.CUSTOM));
    }
    /**
     * A slider is let go: the hand is named for its cells. A slide draws again only its own part ({@link #redraw}).
     */
    void endSlide(PartDial dial) {
        settle(parts, dial);
        status = label(dial) + " " + dial.shown(parts.part(dial.part).get(dial), parts.part(dial.part));
    }
    /** {@link #endSlide}'s brush on {@code parts}: normalized, the hand named for its cells. */
    static void settle(ModularBrush parts, PartDial dial) {
        parts.normalize();
        if (dial.part == BrushPart.HAND) parts.set(BrushPart.HAND, ModularBrush.namedHand(parts.part(BrushPart.HAND)));
    }
    /**
     * A double tap on a slider: the dial goes back to its choice's default (what a fresh part of that choice gets,
     * {@link PartChoice#defaultOf}), one Undo step. The first tap's own jump is taken back first.
     */
    void resetDial(PartDial dial) {
        Step first = history.pollLast();
        if (first != null) show(first.before);
        startSlide(); slide(dial, parts.part(dial.part).choice.defaultOf(dial)); endSlide(dial);
        note = "Reset " + label(dial);
    }
    /** A dial's own dice, within the style. */
    void roll(PartDial dial) {
        remember();
        showParts(parts.style.rollOneDial(parts, dial, random));
        status = label(dial) + " " + dial.shown(parts.part(dial.part).get(dial), parts.part(dial.part));
    }
    /** A part's own dice: a new choice and dials in the style, keeping its locked dials. */
    void rollPart(BrushPart part) {
        remember();
        showParts(parts.style.rollOnePart(parts, part, partLocks, random));
        status = part.label + ": " + ModularWords.choiceWords(parts, part);
    }
    /** A choice dial's chip (Head, Line placement, Fast is): set and let go, as a slider is. */
    void choose(PartDial dial, int option) {
        if ((int) parts.part(dial.part).get(dial) == option) return;
        startSlide(); slide(dial, option); endSlide(dial);
    }
    /**
     * A part's choice chips: those rolls may pick (a hand's presets), and the current choice when it isn't one
     * (a decoded Bristles or Drag, a Custom hand).
     */
    List<PartChoice> chips(BrushPart part) { return chips(parts, part); }
    /** {@link #chips(BrushPart)} for {@code parts} (Brush Studio's too, WebEngine.studioParts). */
    static List<PartChoice> chips(ModularBrush parts, BrushPart part) {
        List<PartChoice> chips = new ArrayList<>();
        // Paper picks no paper (the surface spec, 2026-11-01): the page's surface is the notebook's; Paper keeps its Texture alone.
        if (part == BrushPart.PAPER) return chips;
        // Vector Ink sets parts aside (VectorInk.tidy): its tip is Round.
        if (VectorInk.is(parts) && (VectorInk.setAside(part) || part == BrushPart.TIP)) return chips;
        // Later's in the order the lab shows them (PartChoice.LATER_SHOWN: grouped by kind), not the code's.
        // Drying offers only how long the mark stays wet; After every Later, one that doesn't go with the drying bringing its own (switched).
        for (PartChoice c : part == BrushPart.LATER ? PartChoice.LATER_SHOWN : part.choices()) if (c == parts.choice(part) || ModularFeatures.offered(c) && c != PartChoice.CUSTOM) chips.add(c);
        return chips;
    }
    /**
     * The swap strip's tiles for {@code part} (ui.js swapList, 425–440): one per chip ({@link #chips}), each a copy of
     * the six-part brush with only that part switched as {@link #choose} would switch it (a hand to a fresh preset
     * keeping its Response and Fast is); the current choice is the brush itself.
     */
    List<Swap> swaps(BrushPart part) {
        List<Swap> swaps = new ArrayList<>();
        for (PartChoice c : chips(part)) {
            boolean now = c == parts.choice(c.part);
            ModularBrush b = parts.copy();
            if (!now) switched(b, c);
            swaps.add(new Swap(c, Brush.of(b), now));
        }
        return swaps;
    }
    /**
     * A part's dials as the lab shows them: its choice's, in order, but those that don't draw yet, Screen's own dials
     * while it is none (newmods.js dialShown), and those a Spiro's curve doesn't use.
     */
    List<PartDial> dials(BrushPart part) { return dials(parts, part); }
    /** {@link #dials(BrushPart)} for {@code parts} (Brush Studio's too, WebEngine.studioParts). */
    static List<PartDial> dials(ModularBrush parts, BrushPart part) {
        List<PartDial> dials = new ArrayList<>();
        ModularBrush.Part p = parts.part(part);
        // Vector Ink: its tip's Size alone, no Texture, nothing of the parts it sets aside.
        if (VectorInk.is(parts)) { if (part == BrushPart.TIP) dials.add(PartDial.WEIGHT); return part == BrushPart.HAND ? dials(parts, part, p) : dials; }
        return dials(parts, part, p);
    }
    private static List<PartDial> dials(ModularBrush parts, BrushPart part, ModularBrush.Part p) {
        List<PartDial> dials = new ArrayList<>();
        boolean screened = (int) p.get(PartDial.SCREEN, 0) > 0;
        for (PartDial d : p.choice.dials)
            if (ModularFeatures.shown(d) && (screened || !d.underScreen() || p.choice == PartChoice.TONE) && SpiroStamper.shows(p, d)) dials.add(d);
        // Medium's card holds Paper's one dial, Texture: how much of the page's surface shows through (the surface spec, 2026-11-01).
        if (part == BrushPart.MEDIUM) dials.add(PartDial.TOOTH);
        return dials;
    }
    void toggleLock(PartDial dial) { if (!partLocks.dials.remove(dial)) partLocks.dials.add(dial); }
    void toggleLock(BrushPart part) { if (!partLocks.parts.remove(part)) partLocks.parts.add(part); }
    /** Whether rolls keep {@code part}: locked. */
    boolean locked(BrushPart part) { return partLocks.parts.contains(part); }
    /** Whether rolls keep {@code dial}: it or its part locked. */
    boolean locked(PartDial dial) { return locked(dial.part) || partLocks.dials.contains(dial); }
    /**
     * Whether {@code dial} changes the six-part brush's mark now (ui.js dialActive, 246–258; Takes ink and Plume are
     * hidden, so their lines go): the lab greys one that doesn't, rather than hiding it.
     */
    boolean active(PartDial dial) {
        ModularBrush.Part medium = parts.part(BrushPart.MEDIUM), tip = parts.part(BrushPart.TIP);
        PartChoice m = medium.choice, t = tip.choice;
        boolean ink = m == PartChoice.INK, wash = m == PartChoice.WASH;
        double wet = medium.get(PartDial.WET);
        if (ink && wet < .02 && (dial == PartDial.LINE || dial == PartDial.BLEED || dial == PartDial.BLOOM)) return false;
        if (ink && dial == PartDial.LINES && (t == PartChoice.SPLIT_NIB || t == PartChoice.MULTI_LINE || t == PartChoice.HAKE || t == PartChoice.BRISTLES)) return false;
        if (wash && wet < .5 && (dial == PartDial.BLOOM || dial == PartDial.BACKRUN || dial == PartDial.POOL || dial == PartDial.BLEED)) return false;
        if (dial == PartDial.SOFT && (ink && wet < .1 || m == PartChoice.CHARCOAL || m == PartChoice.GRAPHITE)) return false;
        // Gravity moves only a transparent body of wet paint: none for opaque Cover, or a line without a wash.
        if (dial.gravity() && dial.part == BrushPart.LIFE) { ResolvedBrush r = ResolvedBrush.of(parts, 8, 1); if (r.body == null || r.cover > 0) return false; }
        // On water (Float) Smudge's Lay down does nothing: Drag and Smear comb what floats by Pick up, laying Own paint.
        if (dial == PartDial.LAY && parts.floats()) return false;
        // On water every medium floats as a darkening film: Blend does nothing there (newmods.js dialActive), nor Screen.
        if ((dial == PartDial.M_BLEND || dial == PartDial.SCREEN || dial.underScreen()) && m != PartChoice.TONE && parts.floats()) return false;
        // Under Float and Mold, ways of drying of their own, Drying's dials do nothing but Mold's Drying Time.
        PartChoice later = parts.choice(BrushPart.LATER);
        if (dial.part == BrushPart.LIFE && (later == PartChoice.LATER_FLOAT || later == PartChoice.LATER_MOLD && dial != PartDial.DRYING)) return false;
        // A dither's pattern is its own, a grain's has no angle (the new patterns all turn, but riso, photocopy and soft dither, grains too; maze to rings turn); Tone's sand neither (newmods.js dialActive).
        int screen = (int) medium.get(PartDial.SCREEN, 0), pattern = (int) medium.get(PartDial.PATTERN, 0);
        if (dial == PartDial.CELL && (m == PartChoice.TONE ? pattern == 2 : screen == 3)) return false;
        if (dial == PartDial.S_ANGLE && (m == PartChoice.TONE ? pattern == 2 : screen == 3 || screen == 4 || screen >= 15 && screen <= 17)) return false;
        // Pixels and Stitches lay a grey a cell, no dots to swell or speckle (PrintScreen.cells).
        if ((dial == PartDial.S_GAIN || dial == PartDial.UNEVEN) && m != PartChoice.TONE && screen >= 24) return false;
        return !(dial == PartDial.ANGLE && t == PartChoice.HAKE && (int) tip.get(PartDial.HEAD_TURN) == 0);
    }

    /**
     * The Control card's rows under its grid, in order: Response and Fast is; Tilt Shading for a nib's line; Steady Hand and Long Stick (not a Spiro's); a Spiro's drives; Grey Shift, and
     * while it is on its Order (a Pattern's), its Greys and then its set's swatches ({@link GreySet}), as null; then
     * Gradient, for a medium that takes it ({@link GreyGradient#takes}), and while it is on a To Grey's End Grey, its
     * Length, and a Cycle's Greys and set when Grey Shift doesn't show them already.
     */
    static List<PartDial> handRows(ModularBrush b) { return handRows(b, true); }
    /** {@link #handRows(ModularBrush)}, without Gradient's rows unless {@code gradient}. */
    static List<PartDial> handRows(ModularBrush b, boolean gradient) {
        // Vector Ink: Response alone (its pressure to size is the grid's; its width doesn't follow speed).
        if (VectorInk.is(b)) return new ArrayList<>(Arrays.asList(PartDial.GAIN));
        List<PartDial> rows = new ArrayList<>(Arrays.asList(PartDial.GAIN, PartDial.DIR));
        if (tiltShades(b)) rows.add(PartDial.SIDE_SHADE);
        // Steady Hand and Long Stick: every brush's but a Spiro's, whose ring is set where the pen is (SteadyHand.of).
        if (b.choice(BrushPart.TIP) != PartChoice.SPIRO) { rows.add(PartDial.STEADY_HAND); rows.add(PartDial.LONG_STICK); }
        if (b.choice(BrushPart.TIP) == PartChoice.SPIRO) rows.addAll(Arrays.asList(PartDial.DRIVES));
        rows.add(PartDial.SHIFT);
        if (SwatchPicker.rule(b) == SwatchPicker.PATTERN) rows.add(PartDial.SHIFT_ORDER);
        if (SwatchPicker.shifts(b)) { rows.add(PartDial.SHIFT_GREYS); rows.add(null); }
        if (!gradient || !GreyGradient.takes(b)) return rows;
        rows.add(PartDial.GRADIENT);
        int rule = GreyGradient.rule(b);
        if (rule == GreyGradient.TO_GREY) rows.add(PartDial.GRAD_TO);
        if (rule != GreyGradient.OFF) rows.add(PartDial.GRAD_LEN);
        if (rule == GreyGradient.CYCLE && !SwatchPicker.shifts(b)) { rows.add(PartDial.SHIFT_GREYS); rows.add(null); }
        return rows;
    }
    /** Whether Tilt Shading changes {@code b}: a line of an ink-layer medium laid by its nib (Round, Chisel, Flat, Filbert). */
    static boolean tiltShades(ModularBrush b) {
        PartChoice t = b.choice(BrushPart.TIP);
        return ModularSizes.inks(b) && (t == PartChoice.ROUND || t == PartChoice.CHISEL || t == PartChoice.FLAT_NIB || t == PartChoice.FILBERT);
    }
    /** The hand grid's rows (ui.js HAND_ROWS): pressure, tilt, speed and the page's tilt (Gravity), each driving size, darkness and the nib. */
    static final String[] HAND_ROWS = {"Pressure", "Tilt", "Speed", "Page Tilt"};
    /** Row {@code row}'s three cells: to size, dark and width or angle ({@link PartDial#GRID_CELLS} in threes). */
    static PartDial[] handRow(int row) { return Arrays.copyOfRange(PartDial.GRID_CELLS, row * 3, row * 3 + 3); }
    /** A cell's level as the grid writes it: off (under .17), some (under .75) or full (ui.js cellLevel). */
    static String level(double v) { return v < .17 ? "off" : v < .75 ? "some" : "full"; }
    /** {@code hand}'s {@code cell} as the grid writes it: its {@link #level}, and a full Tilt size whose Tilt range is wide "max". */
    static String level(ModularBrush.Part hand, PartDial cell) {
        String level = level(hand.get(cell));
        return cell == PartDial.TS && level.equals("full") && (int) hand.get(PartDial.TILT_RANGE) == 1 ? "max" : level;
    }
    /** A cell's words in the grid ({@link HandGrid}, the browser's): its {@link #level} in caps, the speed-size cell's with "+" or "−" for Fast is once it acts. */
    static String cellText(ModularBrush.Part hand, PartDial cell) {
        String level = level(hand, cell).toUpperCase(Locale.ROOT);
        return cell == PartDial.SS && hand.get(cell) > .1 ? ((int) hand.get(PartDial.DIR) == 1 ? "+" : "−") + level : level;
    }
    /**
     * A hand cell's tap (ui.js 697): off to some (.5), some to full (1), full to off; the hand is named for its cells.
     * Tilt → size has a fourth level after full, full+: its Tilt range wide, the old Fude pen's reach; any other tap
     * leaves the range normal.
     */
    void cycleCell(PartDial cell) {
        if (locked(cell)) return;
        ModularBrush.Part hand = parts.part(BrushPart.HAND);
        double v = hand.get(cell);
        boolean plus = cell == PartDial.TS && level(v).equals("full") && !level(hand, cell).equals("max");
        double next = plus ? 1 : v < .17 ? .5 : v < .75 ? 1 : 0;
        startSlide(); slide(cell, next);
        if (cell == PartDial.TS) slide(PartDial.TILT_RANGE, plus ? 1 : 0);
        endSlide(cell);
        status = cell.label + ": " + level(parts.part(BrushPart.HAND), cell);
    }
    /** Whether rolls keep every cell of hand row {@code row}. */
    boolean rowLocked(int row) {
        for (PartDial cell : handRow(row)) if (!locked(cell)) return false;
        return true;
    }
    /** A hand row's lock: locks its three cells, or, when all are locked, unlocks them (ui.js 703). */
    void toggleRowLock(int row) {
        boolean all = true;
        for (PartDial cell : handRow(row)) all &= partLocks.dials.contains(cell);
        for (PartDial cell : handRow(row)) if (all) partLocks.dials.remove(cell); else partLocks.dials.add(cell);
    }
    /** A hand row's die (ui.js 706–709): each of its cells not locked rolled again, the hand named for its cells. */
    void rollRow(int row) {
        if (rowLocked(row)) return;
        remember();
        ModularBrush b = parts;
        for (PartDial cell : handRow(row)) if (!locked(cell)) b = parts.style.rollOneDial(b, cell, random);
        showParts(b);
        status = HAND_ROWS[row] + ": " + ModularWords.choiceWords(parts, BrushPart.HAND);
    }
    /** The Fast switch: a six-part hand's Fast is, thinner or thicker. */
    void flipFast() {
        int to = 1 - (int) parts.part(BrushPart.HAND).get(PartDial.DIR);
        choose(PartDial.DIR, to);
        status = "Fast strokes " + PartDial.DIR.choices[to].toLowerCase(Locale.ROOT);
    }
    /**
     * Grey Shift's set ({@link SwatchPicker}): grey {@code k} of the brush's greys taken in, or left out; the last one
     * stays. Whether it changed.
     */
    /** One of Grey Shift's set's greys ({@link #greys}): as the brush lays it (0–255), whether it is in the set, its words. */
    static final class Grey {
        final int tone;
        final boolean in;
        final String said;
        Grey(int tone, double darkness, boolean in) { this.tone = tone; this.in = in; said = ToolSwatches.greyWords(darkness, tone) + (in ? "" : ", left out"); }
    }
    /** Grey Shift's set as it stands: the brush's greys ({@link SwatchPicker#palette}), each laid as the brush lays it, in or out. */
    List<Grey> greys() {
        int n = SwatchPicker.size(SwatchPicker.palette(parts));
        List<Grey> greys = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            double darkness = SwatchPicker.darkness(parts, slots.swatches, k);
            greys.add(new Grey(ModularSizes.swatch(parts, darkness), darkness, SwatchPicker.in(parts, k)));
        }
        return greys;
    }
    boolean toggleGrey(int k) {
        ModularBrush.Part hand = ModularBrush.namedHand(parts.part(BrushPart.HAND));
        int palette = SwatchPicker.palette(parts);
        if (!SwatchPicker.toggle(hand, palette, k)) return false;
        remember();
        parts.set(BrushPart.HAND, hand);
        status = SwatchPicker.set(parts).length + " of " + SwatchPicker.size(palette) + " greys";
        return true;
    }
    /** Renames the brush, a name given ({@link #named}); false, changing nothing, for an empty or unchanged name. */
    boolean rename(String name) {
        String clean = Brush.clean(name);
        if (clean.isEmpty() || clean.equals(brush().name())) return false;
        remember();
        parts.name = clean;
        named = true;
        status = "Renamed";
        return true;
    }
    /** Takes back the latest change to any slot (a card, settings, randomize, rename, New, Open), selecting that slot again. */
    void undo() {
        Step step = history.pollLast();
        if (step == null) return;
        selected = step.slot;
        show(step.before);
        savedId = step.savedId; opened = step.opened; named = step.named;
        status = "Undone";
    }
    /** Whether the brush in the lab is in the library (Save reads Saved): the lab's own, not the slot's as stored. */
    boolean saved() { return slots.library.contains(brush()); }
    /** Save's words in About This Brush's header ({@code BrushLabActivity}, the browser's lab): "Saved" once saved, else Save. */
    String saveWords() { return saved() ? LabLayout.SAVED : LabLayout.SAVE; }
    /**
     * Save, its As New beside it ({@code asNew}): {@link #saveAsNew} when on, or for a brush from no library entry (a
     * Basic tool, a New one, a pasted code: nothing to write over, so As New isn't offered, as Save as New was its only
     * save until 2026-11-03); else {@link #save()}, the entry written over.
     */
    void save(boolean asNew) { if (asNew || savedEntry() == null) saveAsNew(); else save(); }
    /** Whether the brush changed since it was opened or saved (the "Edited" mark). */
    boolean edited() { return !brush().sameAs(opened); }
    /** The library entry Save writes over, or null: Save adds the brush. */
    BrushLibrary.Entry savedEntry() {
        if (savedId == null) return null;
        for (BrushLibrary.Entry e : slots.library.entries()) if (e.id == savedId) return e;
        return null;
    }
    /**
     * A new name for an edited brush, said by what it now is ({@link BrushNames#name}: deadpan, Title Case, its part's
     * word in it), the owner's "once it's edited, maybe suggest a new name?" (2026-10-29); null when it isn't edited, a
     * name was given, or the name it would get is its own. A name another library brush has is passed by for the next
     * (2026-10-29: Save as New adds no second brush of a name), and one repeating the library's latest names
     * (2026-11-02: "seems to repeat quite often").
     */
    String suggestion() {
        if (!edited() || named) return null;
        String name = BrushNames.name(parts, slots.library::has, latestNames());
        return name == null || name.equals(brush().name()) ? null : Brush.clean(name);
    }
    /** The library's latest names, newest first, that a new name keeps from repeating ({@link BrushNames#RECENT}). */
    private List<String> latestNames() {
        List<String> out = new ArrayList<>();
        for (BrushLibrary.Entry e : slots.library.entries()) if (out.size() < BrushNames.RECENT) out.add(e.brush.name());
        return out;
    }
    /** Takes the {@link #suggestion} as the brush's name, a name given; false with none. */
    boolean takeSuggestion() { String name = suggestion(); return name != null && rename(name); }
    /**
     * Save: writes the entry the brush came from over in place ({@link BrushLibrary#update}: its place kept, its own size
     * and grey moved to the new code), keeping the entry's name unless a name was given; with none, adds the brush under
     * its name. A brush saved before is there already.
     */
    void save() {
        BrushLibrary.Entry entry = savedEntry();
        if (entry == null) { add(brush().name()); return; }
        if (!edited()) { status = LabLayout.ALREADY; return; }
        String name = named ? brush().name() : entry.brush.name();
        parts.name = name;
        // Another entry's name given: numbered, as an added one is.
        if (!name.equals(entry.brush.name())) parts.name = slots.library.freeName(brush());
        Brush brush = copyOf(brush());
        slots.library.update(entry.id, brush);
        slots.swatches.moveLook(entry.brush, brush);
        saved(entry.id);
        status = LabLayout.SAVED;
    }
    /** Save as New: a copy added under the name given, else the {@link #suggestion}, else its own; the entry it came from left alone. */
    void saveAsNew() {
        String suggested = suggestion();
        add(named || suggested == null ? brush().name() : suggested);
    }
    /** Adds the brush as {@code name} (numbered when another brush has it), now the entry Save writes over. */
    private void add(String name) {
        parts.name = name;
        String free = slots.library.freeName(brush());
        if (!free.equals(name)) parts.name = free;
        BrushLibrary.Saved saved = slots.library.save(copyOf(brush()));
        BrushLibrary.Entry entry = slots.library.find(brush());
        if (entry != null) saved(entry.id);
        status = saved == BrushLibrary.Saved.ADDED ? LabLayout.SAVED : LabLayout.ALREADY;
    }
    /** The brush is now entry {@code id}'s, as saved: not edited, no name given. */
    private void saved(long id) { savedId = id; opened = copyOf(brush()); named = false; }
    /** Open: library entry {@code entry}'s brush in the selected slot, the entry Save writes over; an Undo step. */
    void open(BrushLibrary.Entry entry) {
        letGo();
        remember();
        show(copyOf(entry.brush));
        saved(entry.id);
        status = "Opened " + brush().name();
    }
    /**
     * New ▾ (Start from ▾ until 2026-11-03): a new brush in {@code style}, the brush's own too, keeping what's locked,
     * that no library entry holds; an Undo step.
     */
    void newBrush(ModularStyle style) {
        rollNew(style);
        savedId = null; opened = copyOf(brush()); named = false;
        status = "New " + style.label + " brush";
    }
    /**
     * Puts the brush a pasted share code describes in the selected slot: an M code, or an old I or P code as its six
     * parts ({@link LegacyBrushes}), a brush no entry holds; false, changing nothing, if it doesn't read.
     */
    boolean load(String code) {
        ModularBrush m = BrushImport.decode(code);
        String newer = BrushImport.newerNote(code);
        if (m == null) { status = newer != null ? newer : "Not a brush code. Paste all of it."; return false; }
        letGo();
        remember();
        show(Brush.of(m));
        savedId = null; opened = copyOf(brush()); named = false;
        status = "Loaded " + brush().name();
        if (newer != null) note = newer;
        return true;
    }
    /**
     * The library's cards in one order that never depends on the slots: newest first; each with the lab's slots
     * holding it.
     */
    List<Card> library() {
        List<Card> cards = new ArrayList<>();
        for (BrushLibrary.Entry e : slots.library.entries()) {
            List<Integer> in = new ArrayList<>();
            for (int i = 0; i < ToolSlots.COUNT; i++) if (brushes[i].sameAs(e.brush)) in.add(i);
            cards.add(new Card(e.brush, in, e));
        }
        return cards;
    }
    /** The index of {@code b}'s card in {@link #library}, or -1. */
    int indexOf(Brush b) {
        List<Card> cards = library();
        for (int i = 0; i < cards.size(); i++) if (cards.get(i).brush.sameAs(b)) return i;
        return -1;
    }
    /** Deletes a card's brush from the library; false for one that isn't in it. */
    boolean delete(Card card) {
        if (!card.deletable()) return false;
        slots.library.delete(card.entry.id);
        status = "Deleted";
        return true;
    }

    private void remember() {
        history.addLast(new Step(selected, copyOf(brush()), savedId, opened, named));
        if (history.size() > HISTORY) history.removeFirst();
    }
    /** The selected slot lets its brush go: an unsaved one is kept in Recent, and the note says so. */
    private void letGo() { if (slots.keep(brush())) note = ToolSlots.keptNote(brush()); }
    /** What the latest change said (the brush kept in Recent), once; else null. */
    String note() { String said = note; note = null; return said; }

    /** Shows {@code b}, a brush of the lab's own (see {@link #copyOf}), in the selected slot: its parts are what the lab changes. */
    private void show(Brush b) { brushes[selected] = b; parts = b.parts; }
    /** Shows a new six-part brush made from the lab's. */
    private void showParts(ModularBrush b) { show(Brush.of(b)); }
    /** Randomize and the styles: a new six-part brush in {@code style}, keeping the locked parts and dials. */
    private void rollNew(ModularStyle style) {
        letGo();
        remember();
        ModularBrush b = style.roll(random, parts, partLocks);
        // Named again by the library: passing by its names and repeats of its latest (the roll knows no library).
        b.name = BrushNames.described(b, b.name, slots.library::has, latestNames());
        showParts(b);
    }
    /**
     * The selected slot's brush, on opening or selecting it: the slot's own, not opened from the library, so Save adds it
     * (option A, 2026-10-29); not edited, no name given.
     */
    private void track() { savedId = null; opened = copyOf(brush()); named = false; }
    /** A dial's label on the six-part brush's current choice ("Dark edge" for a wash's Pooling). */
    private String label(PartDial dial) { return parts.choice(dial.part).labelOf(dial); }
    private String openedSaid() { return
 parts.name.equalsIgnoreCase(parts.style.label) ? parts.name : parts.name + " · " + parts.style.label; }
    /** A copy of {@code b} of its own: for the lab to change, or for a sample drawn in the background to read while the lab changes its own. */
    static Brush copyOf(Brush b) { return Brush.of(b.parts.copy()); }
}
