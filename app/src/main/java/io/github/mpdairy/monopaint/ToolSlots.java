package io.github.mpdairy.monopaint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The bar's nine tool slots (six until 2026-10-19, eight until 2026-10-27, ten until 2026-11-06). Any slot holds any library brush (a six-part brush, see {@link Brush}), whose medium, not
 * the slot, decides where it draws. Each slot's dice rolls a new six-part brush in its brush's style; a pick puts a
 * library brush in it. Undo takes back rolls and picks made since the page last changed, latest first, before it undoes
 * the page. Kept between launches. A first launch starts on {@link #FIRST}; slots stored before the six-part engine (a
 * built-in, an old Wild brush) are converted once, keeping what they drew ({@link LegacyBrushes}); a toolbar kept with
 * fewer slots (six before 2026-10-19, eight before 2026-10-27) keeps them as they are and gains the rest as a first launch has them ({@link
 * #grow}); one kept with ten (before 2026-11-06) loses one ({@link #shrink}). Pure, checked on the desktop.
 */
final class ToolSlots {
    static final int COUNT = 9;
    /**
     * The bar's tools after the slots: the eraser, then the smudge tool (a smudge brush of its own, sizes only:
     * {@link #smudge}), then the stamp tool (a Stamp brush of its own: {@link #stamp}), then Fill in (the Basic tool
     * {@link #FILL_TOOL}, always: {@link #fill}; a slot until 2026-11-06, the owner: "the fill in pen should be grouped
     * with the stump and eraser"); how many tools the bar has in all. A tool's index is its place in the bar.
     */
    static final int ERASER = COUNT, SMUDGE = COUNT + 1, STAMP = COUNT + 2, FILL = COUNT + 3, TOOLS = COUNT + 4;
    /** How many rolls and picks Undo can take back. */
    static final int UNDOS = 32;
    /**
     * Each slot's (or the eraser's) size and shade swatch, as older builds kept them under these plus {@link #key} (read
     * once, {@link ToolSwatches#own}); the selection under {@link #SELECTED}.
     */
    static final String SIZE = "inkwash_size_", SHADE = "inkwash_shade_", SELECTED = "inkwash_tool";
    /**
     * What a first launch's slots and library hold, in slot order, each at its own size and grey: the owner's own toolbar
     * (2026-10-28, "I adjusted all of those settings to how I like them so save those as default"), as their tablet
     * backed it up, in the order they asked for. The defaults before it (Fineliner, Halftone brush and the rest) stay
     * Basic tools. Key, name, share code, size (px) and grey (darkness, 0 white to 1 black); change a size or grey here.
     * The keys are only labels; stored built-ins convert through {@link LegacyBrushes#BUILTINS}, not these.
     */
    static final String[][] FIRST = {
            {"PENCIL", "Another pencil", "another-pencil.M40i000000000at_4ip2c_0_di0_1jz_800000000zzzz000000zz000000000530000005000c099zz.inw", "6", "1.0"},
            {"STEADY", "Steady line", "steady-line.M00il00000000at_000zz0000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud", "3", "1.0"},
            {"FELT", "Verve felt", "verve-felt.M30i0776o0q00u1_000zz0000920000000_0_d00_0_8i00000zz0000400000nz0.jjds6x", "4", "1.0"},
            {"FUDE", "Fude fountain", "fude-fountain.M20ss007700003m_000zzat00000000000_0_000_0_8at0000i000ll000000nz01.1s13pn4", "11", "1.0"},
            {"WASH", "Ink wash", "ink-wash.M60i0000000007q_1wva200000000zz008bi0000000001_0_0fk_7_8zz0000000000000000nz0.t4mf73", "85", "0.541"},
            {"WATERCOLOUR", "Watercolour wash", "watercolour-washy.M60i04o4b0000ji_1zz8t00ag9600zz008bi0000000001_1zzex_000_7js_8zz0000000000000000zz0.1s13pn4", "275", "0.227"},
            {"OIL", "Rob boss", "rob-boss.M99vleei0000000005e_2zg00_2llydzzn7_000_7ne_1ss0000000000000000nz0.2fu", "114", "0.424"},
            {"BENDAY", "Ben-Day brush", "ben-day-brush.Me1zzeezz00000000ll_0i0000000000000000zz065ji000at_0_000_0_25ess00000000000000nz0.2ef", "17", "1.0"},
            {"HIGHLIGHTER", "High lighter", "high-lighter.M31e1zzp60000000000_1007700000000zz2_0_000_0_0000000000000000000nz0.2f2", "34", "0.102"}};
    /** The stamp tool's size at first, px, and its grey, darkness 0 white to 1 black; the eraser's size, px; Fill in's size and grey (its slot's in FIRST until 2026-11-06). */
    static final double FIRST_STAMP_PX = 44, FIRST_STAMP_GREY = .216, FIRST_ERASER_PX = 360 /* the owner's on the Manta (2026-11-09: "initial eraser size needs to be as big as it is on the manta") */, FIRST_FILL_PX = 2.5, FIRST_FILL_GREY = 1;
    /** Slot {@code slot}'s size on a first launch, px ({@link #FIRST}). */
    static double firstPx(int slot) { return Double.parseDouble(FIRST[slot][3]); }
    /** Slot {@code slot}'s grey on a first launch, darkness ({@link #FIRST}). */
    static double firstGrey(int slot) { return Double.parseDouble(FIRST[slot][4]); }
    /** The slots' stored brushes, one a line, slot by slot (a toolbar kept with six has six lines: {@link #grow}). */
    private static final String SLOTS = "tool_slots";
    /**
     * Kept once every stored brush is at Erasable 1 ({@link #erasable}, 2026-10-22). Its own key: the one before
     * ("tool_slots_erasable", 2026-10-16, Basic tools in slots to their real Erasable) is passed by, done or not.
     */
    static final String ERASABLE = "tool_slots_erasable_1";
    /** Kept once a toolbar holding {@link #FIRST}'s brushes in another order has taken FIRST's ({@link #reorder}, 2026-10-28). */
    static final String REORDERED = "tool_slots_first_order";
    /** Kept once slots holding the HB pencil as it was before 2026-11-05 hold today's ({@link #hbDark}). */
    static final String HB_DARK = "tool_slots_hb_dark";
    /**
     * The brushes put in slots, and the unsaved ones a slot let go ({@link #keep}), latest first, one a line ({@link
     * #recent}); how many are kept (the owner's 30, 2026-10-23; was 48).
     */
    static final String RECENT = "tool_recent";
    static final int RECENT_MOST = 30;
    /**
     * The smudge tool's brush, as a line; the key its size is kept under; the Basic tool it starts with (Blend, the
     * paper stump that pushes when pressed; the Blend stump until 2026-10-30).
     */
    static final String SMUDGE_BRUSH = "tool_smudge", SMUDGE_KEY = "SMUDGE", SMUDGE_FIRST = "Blend";
    /**
     * The smudge tool's Basic tools, its chooser's (the owner, 2026-10-30: "just TWO tools, each using pen pressure the
     * way real media does"; and Blur, 2026-11-01: "a simple blur might be good as a third option?"); and the older ones
     * Blend and Water each take the place of, once ({@link #smudgeTwo}).
     */
    static final String[] SMUDGE_TOOLS = {SMUDGE_FIRST, "Water", "Blur"};
    static final String[][] SMUDGE_MOVES = {{"Blend stump", "Blend"}, {"Stump", "Blend"}, {"Water brush", "Water"}, {"Damp tissue", "Water"}};
    /** Kept once the smudge tool's brush has been moved to one of {@link #SMUDGE_TOOLS} ({@link #smudgeTwo}), done or not. */
    static final String SMUDGE_TWO = "tool_smudge_two";
    /** The stamp tool's brush, as a line, and the key its size, grey and selection are kept under. */
    static final String STAMP_BRUSH = "tool_stamp", STAMP_KEY = "STAMP";
    /** Fill in's Basic tool ({@link #FILL}) and the key its size, grey and selection are kept under. */
    static final String FILL_TOOL = "Fill in", FILL_KEY = "FILL";

    final BrushLibrary library;
    /** The shared size and grey swatches, and each tool's own size and grey. */
    final ToolSwatches swatches;
    private final BrushLibrary.Store store;
    private final Random dice;
    /** The rolls and picks since the page last changed, latest last: each slot, and its brush before. Not kept between launches. */
    private final ArrayDeque<Change> changes = new ArrayDeque<>();
    private static final class Change {
        final int slot;
        final String before;
        /** Changes made with it, taken back with it ({@link #useAll}: a tool kit's slots), first slot first; empty for one. */
        final List<Change> with = new ArrayList<>();
        Change(int slot, String before) { this.slot = slot; this.before = before; }
        /** Whether a Tools page swap set the slot's size and grey ({@link #take}); then those before, as kept (null: none). */
        boolean look;
        String px, dark;
    }

    ToolSlots(BrushLibrary.Store store, Random dice) {
        this.store = store; this.dice = dice; library = new BrushLibrary(store); swatches = new ToolSwatches(store);
        // A library that starts here holds only the first brushes: the owner's cleanup is for one saved before.
        if (store.get(BrushLibrary.LIBRARY) == null) store.put(LibraryCleanup.DONE, "1");
        library.start(firsts());
        if (store.get(SLOTS) == null) { start(); store.put(ERASABLE, "1"); store.put(REORDERED, "1"); store.put(HB_DARK, "1"); } else { grow(); shrink(); convert(); erasable(); reorder(); hbDark(); }
        if (store.get(ToolSwatches.PX + FILL_KEY) == null) { swatches.useSize(FILL_KEY, FIRST_FILL_PX); swatches.useGrey(FILL_KEY, FIRST_FILL_GREY); }
        LibraryCleanup.run(library, swatches, store);
        LibraryCleanup.sameNames(library, store);
        smudgeTwo();
        swatches.spreadCurrent(brushKeys());
    }

    /** The key a slot's (or the stamp tool's) selection, size and grey are kept under. */
    static String key(int slot) { return slot == STAMP ? STAMP_KEY : slot == FILL ? FILL_KEY : "slot" + slot; }
    /** What tool {@code tool}'s size, grey and selection are kept under: a slot's ({@link #key}), the eraser's as before slots, the smudge tool's. */
    static String toolKey(int tool) { return tool == ERASER ? ToolSwatches.ERASER_KEY : tool == SMUDGE ? SMUDGE_KEY : key(tool); }
    /** Whose brush a stroke of tool {@code tool} is, its paint kept apart ({@link HeldPaint}): the tool's, or the finger's with it. */
    static String holder(int tool, boolean finger) { return toolKey(tool) + (finger ? "/finger" : ""); }
    /**
     * Whether tool {@code tool}'s {@code brush} (a slot's or the smudge tool's) keeps paint ({@link #keepsPaint}) and holds
     * some now in {@code held}: its own, or the finger's when one finger draws with it ({@code fingerTool}). The bar shows
     * it in that paint's grey, and the tool's long press offers Clean Brush ({@link DrawLayout#CLEAN}).
     */
    static boolean dirty(HeldPaint held, Brush brush, int tool, int fingerTool) {
        if (brush == null || !keepsPaint(brush)) return false;
        return held.dirty(holder(tool, false)) || fingerTool == tool && held.dirty(holder(tool, true));
    }
    /** Clean Brush: tool {@code tool}'s brush, and the finger's with it, start their next strokes clean. Whether either held paint. */
    static boolean clean(HeldPaint held, int tool) {
        boolean pen = held.clean(holder(tool, false)), finger = held.clean(holder(tool, true));
        return pen || finger;
    }
    /** The slots' keys and the stamp tool's: the tools that drew at the one size and grey of 2026-10-24 to 27. */
    static List<String> brushKeys() {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) keys.add(key(i));
        keys.add(STAMP_KEY);
        return keys;
    }
    /** What slot {@code slot} holds on a first launch, and when what it holds doesn't read. */
    static Brush first(int slot) { return Brush.of(LegacyBrushes.named(FIRST[slot])); }
    /** Slot {@code slot}'s brush (or the stamp tool's, {@link #STAMP}, or Fill in's, {@link #FILL}); one that doesn't read is what the slot starts with. */
    Brush brush(int slot) {
        if (slot == FILL) return fill();
        Brush brush = Brush.read(slot == STAMP ? store.get(STAMP_BRUSH) : stored()[slot]);
        return brush != null ? brush : slot == STAMP ? firstStamp() : first(slot);
    }
    /** The dice: a new six-part brush in the slot brush's style, for this slot only. */
    Brush roll(int slot) {
        if (frozen) return brush(slot);
        Brush rolled = rolled(brush(slot), dice);
        change(slot, rolled);
        return rolled;
    }
    /** The dice's roll for a slot holding {@code brush}: a new six-part brush in its style, at {@code brush}'s Erasable ({@link ModularStyle#keepErasable}). */
    static Brush rolled(Brush brush, Random dice) {
        ModularBrush b = brush.parts.style.roll(dice);
        ModularStyle.keepErasable(b, brush.parts);
        return Brush.of(b);
    }
    /**
     * Puts {@code brush} in slot {@code slot}; a library brush becomes the most recently used. The brush the slot holds
     * already changes nothing: false, no Undo step, no stamp. A brush with its own size and grey brings them
     * into the slot ({@link ToolSwatches#bring}); Undo takes back the brush, not them.
     */
    boolean use(int slot, Brush brush) {
        if (!change(slot, brush)) return false;
        swatches.bring(key(slot), brush);
        library.used(brush);
        remember(brush);
        return true;
    }
    /**
     * A pick from the tool picker: {@code brush} in slot {@code slot} ({@link #use}), the unsaved brush it lets go kept in
     * Recent first ({@link #keep}). Whether the slot changed.
     */
    boolean pick(int slot, Brush brush) {
        Brush was = brush(slot);
        if (was != null && !was.sameAs(brush)) keep(was);
        return use(slot, brush);
    }
    /**
     * The Tools page's or Brush Lab's slots coming back ({@code brushes}, null: unchanged): each put in its slot, an Undo
     * step each ({@link #use}), or every change one step, {@code together} (a kit's, {@link #useAll}). Whether each changed.
     */
    boolean[] take(Brush[] brushes, boolean together) { return take(brushes, together, null); }
    /**
     * As {@link #take(Brush[], boolean)}, then the Tools page's swaps ({@code looks}, {@link ToolsPage#moved}: each slot's
     * px, then darkness; NaN unchanged; null none): each slot draws at the size and grey its brush brought from the slot
     * it left, never a brush's own ({@link ToolSwatches#bring}); with any, every change one Undo step, which puts those
     * sizes and greys back too. A fixed toolbar takes none.
     */
    boolean[] take(Brush[] brushes, boolean together, double[] looks) {
        boolean moves = false;
        for (int i = 0; looks != null && !frozen && i < COUNT && 2 * i < looks.length; i++) moves |= !Double.isNaN(looks[2 * i]);
        if (together || moves) return useAll(brushes, moves ? looks : null);
        boolean[] used = new boolean[COUNT];
        for (int i = 0; i < COUNT && i < brushes.length; i++) used[i] = brushes[i] != null && use(i, brushes[i]);
        return used;
    }
    /**
     * Puts each of {@code brushes} (null: unchanged) in its slot, as {@link #use} does, but as one Undo step (a tool kit's
     * slots, {@link ToolKits}). Whether each slot changed.
     */
    boolean[] useAll(Brush[] brushes) { return useAll(brushes, null); }
    /** As {@link #useAll(Brush[])}, then {@code looks} set as {@link #take(Brush[], boolean, double[])} says, in the same step. */
    private boolean[] useAll(Brush[] brushes, double[] looks) {
        boolean[] used = new boolean[COUNT];
        int made = 0;
        for (int i = 0; i < COUNT && i < brushes.length; i++) {
            boolean look = looks != null && 2 * i + 1 < looks.length && !Double.isNaN(looks[2 * i]);
            String key = key(i), px = store.get(ToolSwatches.PX + key), dark = store.get(ToolSwatches.DARK + key);
            if (used[i] = brushes[i] != null && use(i, brushes[i])) made++;
            if (!look) continue;
            // Only the size and grey moved (two slots holding the same brush): a step of its own, its brush as it is.
            if (!used[i]) { record(new Change(i, brush(i).write())); made++; }
            Change c = changes.peekLast();
            c.look = true; c.px = px; c.dark = dark;
            swatches.useSize(key, looks[2 * i]);
            swatches.useGrey(key, looks[2 * i + 1]);
        }
        // The changes just made (the latest; the oldest steps give way past UNDOS) fold into the first of them.
        if (made > 1) {
            List<Change> mine = new ArrayList<>();
            for (int i = 0; i < made; i++) mine.add(0, changes.pollLast());
            Change first = mine.get(0);
            first.with.addAll(mine.subList(1, mine.size()));
            changes.addLast(first);
        }
        return used;
    }
    /**
     * The brushes put in slots, latest first, each once ({@link #RECENT_MOST} at most): library brushes, Basic tools and
     * rolls alike. Before any was kept, the library's, most recently used first.
     */
    List<Brush> recent() {
        String text = store.get(RECENT);
        List<Brush> recent = new ArrayList<>();
        if (text == null) {
            for (BrushLibrary.Entry e : library.recent(RECENT_MOST)) recent.add(e.brush);
            return recent;
        }
        for (String line : text.split("\n")) {
            Brush brush = line.isEmpty() ? null : Brush.read(line);
            if (brush != null) recent.add(brush);
        }
        return recent;
    }
    /**
     * A slot let {@code brush} go (the Tools page's pick, a kit, Brush Lab's Randomize, a style, Load): one that would be
     * lost (not in the library, not a Basic tool, not in Recent already) is kept first in {@link #recent} (the owner,
     * 2026-10-23: "so it isn't lost forever"). Whether it was, for the screen's note ({@link #keptNote}).
     */
    boolean keep(Brush brush) {
        if (brush == null || library.contains(brush) || ToolPresets.find(brush) != null) return false;
        for (Brush b : recent()) if (b.sameAs(brush)) return false;
        remember(brush);
        return true;
    }
    /** What a screen says when {@link #keep} kept {@code brush}: "Kept Fineliner in Recent". */
    static String keptNote(Brush brush) { return "Kept " + brush.name() + " in Recent"; }
    /** {@code brush} is the latest put in a slot: first in {@link #recent}, once. */
    private void remember(Brush brush) {
        List<String> lines = new ArrayList<>();
        lines.add(brush.write());
        for (Brush b : recent()) if (!b.sameAs(brush) && lines.size() < RECENT_MOST) lines.add(b.write());
        store.put(RECENT, String.join("\n", lines));
    }
    // The smudge tool

    /** A smudge brush's Own paint is under this (Smudge's Lay down and Pick up aside). */
    static final double SMUDGE_PAINT = .5;
    /** Whether {@code brush} is a smudge brush: its Smudge part isn't Off and its own paint is low (under {@link #SMUDGE_PAINT}). */
    static boolean smudges(Brush brush) {
        ModularBrush.Part smudge = brush.parts.part(BrushPart.CARRY);
        return smudge.choice != PartChoice.OFF && ModularBrush.ownPaint(smudge) < SMUDGE_PAINT;
    }
    /**
     * What a smudge brush does, for its icon on the bar (the owner, 2026-10-19, after Photoshop's Blur and Smudge tools):
     * BLUR softens marks in place, MonoPaint's blending stump (a Smear that is mostly stump, {@link ModularWords#stump},
     * without tines); PUSH pushes paint along (a Smear under that, or Drag); STUMP the rest (Lift, a comb). The wet smudge
     * spec's two: WATER lays clear water (Clear Water), DIRTY keeps paint between strokes (a Drag or Smear with Keeps Paint).
     * The smudge tool's two (2026-10-30) show their light touch: Blend the blending stump (BLUR), Water the water brush.
     */
    enum SmudgeKind { STUMP, BLUR, PUSH, WATER, DIRTY }
    /** What {@code brush} does as the smudge tool's ({@link SmudgeKind}). */
    static SmudgeKind smudgeKind(Brush brush) {
        ModularBrush b = brush.parts;
        PartChoice carry = b.choice(BrushPart.CARRY);
        if (carry == PartChoice.CLEAR_WATER) return SmudgeKind.WATER;
        if (keepsPaint(brush)) return SmudgeKind.DIRTY;
        if (carry == PartChoice.SMUDGE) return !ModularWords.stump(b) ? SmudgeKind.PUSH : ModularWords.combs(b) ? SmudgeKind.STUMP : SmudgeKind.BLUR;
        return carry == PartChoice.DRAG ? SmudgeKind.PUSH : SmudgeKind.STUMP;
    }
    /** Whether {@code brush} keeps paint between strokes, a dirty brush (Drag's or Smear's Keeps Paint, not all stump: {@link ResolvedBrush.Carry#keepsPaint}). */
    static boolean keepsPaint(Brush brush) {
        ModularBrush.Part smudge = brush.parts.part(BrushPart.CARRY);
        return smudge.get(PartDial.KEEPS, 0) > 0 && smudge.get(PartDial.SOFTEN, 0) < 1;
    }
    /** The smudge tool's brush: as kept, else {@link #SMUDGE_FIRST}. */
    Brush smudge() {
        Brush brush = Brush.read(store.get(SMUDGE_BRUSH));
        return brush != null ? brush : firstSmudge();
    }
    /** What the smudge tool holds at first: the Basic tool {@link #SMUDGE_FIRST}, else the first smudge Basic tool. */
    static Brush firstSmudge() {
        Brush first = null;
        for (ToolPresets.Preset p : ToolPresets.all()) {
            if (p.name.equals(SMUDGE_FIRST)) return p.brush();
            if (first == null && smudges(p.brush())) first = p.brush();
        }
        return first;
    }
    /**
     * What the smudge tool's chooser offers: its Basic tools ({@link #SMUDGE_TOOLS}, Blend, Water and Blur); the tool's own
     * brush first when it is none of them (an older Basic tool, a saved brush: the library keeps them all).
     */
    List<Brush> smudgeChoices() {
        List<Brush> choices = new ArrayList<>();
        for (String name : SMUDGE_TOOLS) choices.add(ToolKits.preset(name).brush());
        Brush now = smudge();
        boolean held = false;
        for (Brush b : choices) held |= b.sameAs(now);
        if (!held) choices.add(0, now);
        return choices;
    }
    /**
     * Once (2026-10-30, {@link #SMUDGE_TWO}): a smudge tool holding the Blend stump or the Stump (Basic tools until
     * 2026-10-31) holds Blend, one holding the Water brush or the Damp tissue holds Water ({@link #SMUDGE_MOVES}); any
     * other brush stays.
     */
    private void smudgeTwo() {
        if (store.get(SMUDGE_TWO) != null) return;
        store.put(SMUDGE_TWO, "1");
        Brush held = Brush.read(store.get(SMUDGE_BRUSH));
        // The four older ones are retired (2026-10-31), no longer Basic tools.
        ToolPresets.Preset was = held == null ? null : ToolPresets.retired(held);
        if (was != null) for (String[] move : SMUDGE_MOVES) if (was.name.equals(move[0])) store.put(SMUDGE_BRUSH, ToolKits.preset(move[1]).brush().write());
    }
    /** The smudge tool takes {@code brush}: a library brush becomes the most recently used. The brush it holds already changes nothing: false. */
    boolean useSmudge(Brush brush) {
        Brush before = smudge();
        if (before.sameAs(brush)) return false;
        store.put(SMUDGE_BRUSH, brush.write());
        library.used(brush);
        return true;
    }

    // Fill in

    /** Fill in's brush ({@link #FILL}): the Basic tool {@link #FILL_TOOL}, never changed. */
    static Brush fill() { return ToolKits.preset(FILL_TOOL).brush(); }
    /** Whether {@code brush} is Fill in, by its settings alone (a copy under another name too). */
    static boolean fills(Brush brush) { return brush != null && brush.parts.key().equals(fill().parts.key()); }

    // The stamp tool

    /**
     * The stamp tool's brush ({@link #STAMP}): a Stamp, its nib cut from the page (Cut Stamp, {@link StampNib}). Changed
     * as a slot's is ({@link #use}, an Undo step), but app-wide like the smudge tool's: never in a notebook's toolbar,
     * so a frozen toolbar doesn't hold it.
     */
    Brush stamp() { return brush(STAMP); }
    /**
     * Cut Stamp's lasso lifted ({@code xs, ys}, its first {@code n} points, page px): the ink inside it on {@code page},
     * trimmed ({@link StampNib#cut}), is slot {@code slot}'s Stamp's nib from now on, kept in the nib store, an Undo step
     * as any change of the slot's brush ({@link #use}). Its words: {@link StampNib#DONE}, or {@link StampNib#NOTHING}
     * when the slot holds no Stamp or the lasso no ink. The Manta's and the browser's.
     */
    String cutStamp(int slot, ToneDocument page, float[] xs, float[] ys, int n) {
        Brush brush = brush(slot);
        if (page == null || brush == null || !brush.parts.stamps()) return StampNib.NOTHING;
        return cutStamp(slot, brush, StampNib.cut(page::compositeTone, page.paperTone(), xs, ys, n, page.width, page.height)) ? StampNib.DONE : StampNib.NOTHING;
    }
    /**
     * Make Stamp on the Lasso's bar (2026-11-02): the floating {@code piece}'s ink ({@link LassoPieces#nib}) is a Stamp's
     * nib, as Cut Stamp's lasso's is: the Stamp in use (tool {@code selected}), else the first of the {@code shown} slots
     * holding one, else the slot in use (the first, for the eraser or the smudge tool) becomes the Basic tool Stamp, an
     * Undo step. The slot, or −1 for no ink. The Manta's and the browser's.
     */
    int makeStamp(FloatingSelection piece, int selected, int shown) {
        int slot = selected < COUNT ? selected : 0;
        if (!brush(slot).parts.stamps()) for (int i = 0; i < Math.min(shown, COUNT); i++) if (brush(i).parts.stamps()) { slot = i; break; }
        Brush brush = brush(slot);
        return cutStamp(slot, brush.parts.stamps() ? brush : basicStamp(), LassoPieces.nib(piece)) ? slot : -1;
    }
    /** The Basic tool Stamp as the tool picker gives it, so its slot shows the owner's drawing of it ({@link DrawLayout#drawing}). */
    private static Brush basicStamp() {
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.brush().parts.stamps()) return p.brush();
        return firstStamp();
    }
    /** Slot {@code slot} takes {@code stamp} printing {@code nib}, kept in the nib store; false for no nib. */
    private boolean cutStamp(int slot, Brush stamp, StampNib nib) {
        if (nib == null) return false;
        use(slot, Brush.of(stamp.parts.withNib(StampNib.keep(nib))));
        return true;
    }
    /** What the stamp tool holds at first: the built-in Fineliner with a Stamp tip (a round stamp until a nib is cut). */
    static Brush firstStamp() {
        ModularBrush b = LegacyBrushes.builtin("INK");
        b.set(BrushPart.TIP, ModularBrush.switchTo(b.part(BrushPart.TIP), PartChoice.STAMP));
        b.name = "Stamp";
        return Brush.of(b);
    }

    /**
     * The slots as a notebook keeps its own toolbar (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md,
     * "Tools"): each brush with the size and darkness it draws at, a line each: its stored brush, the px and the darkness,
     * tab-separated. The swatches (the four sizes and greys) stay the app's. A toolbar written from 2026-10-24 to 27 has
     * the one size and grey of then on every line.
     */
    static final class Toolbar {
        final Brush[] brushes = new Brush[COUNT];
        final double[] px = new double[COUNT], darkness = new double[COUNT];
        /** One line a slot. */
        List<String> lines() {
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < COUNT; i++) lines.add(brushes[i].write() + "\t" + Double.toString(px[i]) + "\t" + Double.toString(darkness[i]));
            return lines;
        }
        /**
         * The toolbar {@code lines} keep; null when there are fewer than {@link NotebookOptions#FEWEST_SLOTS} or more than
         * {@link NotebookOptions#MOST_SLOTS}, or one doesn't read. One kept with eight (before 2026-10-27) keeps them, the
         * slots after them as a first launch has them; one kept with ten (before 2026-11-06) gives one up ({@link #dropAt}).
         */
        static Toolbar parse(List<String> lines) {
            if (lines == null || lines.size() < NotebookOptions.FEWEST_SLOTS || lines.size() > NotebookOptions.MOST_SLOTS) return null;
            lines = new ArrayList<>(lines);
            while (lines.size() > COUNT) lines.remove(dropAt(lineBrushes(lines)));
            Toolbar t = new Toolbar();
            for (int i = lines.size(); i < COUNT; i++) { t.brushes[i] = first(i); t.px[i] = firstPx(i); t.darkness[i] = firstGrey(i); }
            for (int i = 0; i < lines.size(); i++) {
                String[] part = lines.get(i).split("\t", -1);
                if (part.length != 4) return null;
                t.brushes[i] = Brush.read(part[0] + "\t" + part[1]);
                try { t.px[i] = Double.parseDouble(part[2]); t.darkness[i] = Double.parseDouble(part[3]); }
                catch (NumberFormatException e) { return null; }
                if (t.brushes[i] == null || Double.isNaN(t.px[i]) || Double.isNaN(t.darkness[i]) || Double.isInfinite(t.px[i]) || Double.isInfinite(t.darkness[i])) return null;
            }
            return t;
        }
        /** Whether {@code other} holds the same brushes at the same sizes and greys. */
        boolean sameAs(Toolbar other) { return lines().equals(other.lines()); }
    }
    /**
     * What each slot held when the Tools page or Brush Lab opened (docs/archive/design-pass-2026-10-23.md,
     * "Getting back to where you started"; the owner: "a way to go back to the original tool that was open when it was
     * opened, an easy way"): the screen offers it back ({@link #changed}, the slot's dot and "Back to Fineliner"). Carried
     * Tools page → Brush Lab → Tools page as {@link #write}'s lines, so the start is the one from before either.
     */
    static final class Start {
        final Brush[] brushes = new Brush[COUNT];
        /** The slots as they are now. */
        static Start of(ToolSlots slots) {
            Start s = new Start();
            for (int i = 0; i < COUNT; i++) s.brushes[i] = slots.brush(i);
            return s;
        }
        /** {@code lines} as {@link #write} wrote them, else the slots as they are now (opened from the drawing screen). */
        static Start read(String[] lines, ToolSlots slots) {
            if (lines == null || lines.length != COUNT) return of(slots);
            Start s = new Start();
            for (int i = 0; i < COUNT; i++) if ((s.brushes[i] = Brush.read(lines[i])) == null) return of(slots);
            return s;
        }
        /** The brushes, for an Intent's extra. */
        String[] write() {
            String[] lines = new String[COUNT];
            for (int i = 0; i < COUNT; i++) lines[i] = brushes[i].write();
            return lines;
        }
        /** Whether slot {@code i} holds {@code now}, not what it held at the start (its dot, and Back to its first brush). */
        boolean changed(int i, Brush now) { return !brushes[i].sameAs(now); }
    }

    /** The slots as they are now: their brushes, each at its slot's size and grey. */
    Toolbar toolbar() {
        Toolbar t = new Toolbar();
        for (int i = 0; i < COUNT; i++) {
            t.brushes[i] = brush(i);
            ToolSwatches.Look look = swatches.look(key(i), t.brushes[i]);
            t.px[i] = look.px; t.darkness[i] = look.darkness;
        }
        return t;
    }
    /**
     * The slots take {@code toolbar}'s brushes, sizes and greys, no step for Undo (rolls and picks before are forgotten).
     * The library and the recent brushes are the app's, unchanged.
     */
    void load(Toolbar toolbar) {
        for (int i = 0; i < COUNT; i++) { swatches.useSize(key(i), toolbar.px[i]); swatches.useGrey(key(i), toolbar.darkness[i]); }
        String[] lines = new String[COUNT];
        for (int i = 0; i < COUNT; i++) lines[i] = toolbar.brushes[i].write();
        store.put(SLOTS, String.join("\n", lines));
        changes.clear();
    }

    /** Where the app's own toolbar waits, as its lines, while a notebook with a toolbar of its own is open. */
    static final String APPWIDE = "tool_appwide";
    /** The app's own toolbar: the slots' while no notebook's is loaded, else the one waiting for it to be left. */
    Toolbar appWide() {
        String kept = store.get(APPWIDE);
        Toolbar app = kept == null ? null : Toolbar.parse(java.util.Arrays.asList(kept.split("\n", -1)));
        return app != null ? app : toolbar();
    }
    /** A notebook's own toolbar, {@code options}' (null: it has none, or the lines don't read). */
    static Toolbar of(NotebookOptions options) {
        if (options == null) return null;
        return options.ownsTools() ? Toolbar.parse(options.toolbar) : null;
    }
    /**
     * The notebook's toolbar on page {@code page}: {@link #of}, but under Each Page a page after the first gets its own
     * Surprise Me roll, seeded by the first page's toolbar and the page's number, so it is the same each time it is turned
     * to (while the library stays as it is).
     */
    Toolbar forPage(NotebookOptions options, int page) {
        Toolbar own = of(options);
        if (own == null || options.tools != NotebookOptions.Tools.EACH_PAGE || page <= 0) return own;
        java.util.List<Brush> brushes = new java.util.ArrayList<>();
        for (BrushLibrary.Entry e : library.entries()) brushes.add(e.brush);
        return SurpriseTools.toolbar(SurpriseTools.roll(brushes, swatches, new java.util.Random(String.join("\n", options.toolbar).hashCode() * 31L + page)));
    }
    /**
     * A notebook's options {@code o} with its Tools made {@code tools} (the settings' Tools screen): My Toolbar the app's;
     * Surprise Me or Each Page a roll of the library (between them the roll kept); Library the notebook's own toolbar,
     * else the app's to start it, which the Tools page then fills.
     */
    NotebookOptions withTools(NotebookOptions o, NotebookOptions.Tools tools, Random random) {
        boolean rolled = tools == NotebookOptions.Tools.SURPRISE || tools == NotebookOptions.Tools.EACH_PAGE;
        boolean wasRolled = o.tools == NotebookOptions.Tools.SURPRISE || o.tools == NotebookOptions.Tools.EACH_PAGE;
        if (tools == NotebookOptions.Tools.MINE) return o.withTools(tools, null);
        if (rolled && wasRolled && tools != o.tools) return o.withTools(tools, o.toolbar);
        if (rolled) {
            List<Brush> brushes = new ArrayList<>();
            for (BrushLibrary.Entry e : library.entries()) brushes.add(e.brush);
            return o.withTools(tools, SurpriseTools.toolbar(SurpriseTools.roll(brushes, swatches, random)).lines());
        }
        Toolbar own = of(o);
        return o.withTools(tools, (own != null ? own : appWide()).lines());
    }
    /**
     * The slots can't be changed while drawing (a notebook's Each Page, or its own toolbar Locked or Sealed,
     * {@link NotebookOptions#toolbarFrozen}): rolls and picks change nothing. {@link #load} (the notebook's own toolbar
     * entering) is not a change. Set by the screen when the notebook opens or its options change.
     */
    boolean frozen;
    /** Whether a notebook's own toolbar is loaded ({@link #enter}) and the app's is waiting. */
    boolean inNotebook() { return store.get(APPWIDE) != null; }
    /**
     * A notebook with a toolbar of its own is open: the slots take {@code own}, the app's toolbar kept (once) to go back to,
     * so a launch cut short is put right at the next {@link #leave}. Whether the slots changed.
     */
    boolean enter(Toolbar own) {
        Toolbar now = toolbar();
        if (!inNotebook()) store.put(APPWIDE, String.join("\n", now.lines()));
        if (now.sameAs(own)) return false;
        load(own);
        return true;
    }
    /** The notebook is left, or has none of its own: the app's toolbar is back, if it was waiting. Whether the slots changed. */
    boolean leave() {
        if (!inNotebook()) return false;
        Toolbar app = appWide();
        store.put(APPWIDE, null);
        if (app.sameAs(toolbar())) return false;
        load(app);
        return true;
    }

    BrushLibrary.Saved save(int slot) { return library.save(brush(slot)); }
    boolean isSaved(int slot) { return library.contains(brush(slot)); }
    /** Whether Undo would take back a roll or a pick rather than undo the page. */
    boolean canUndo() { return !changes.isEmpty(); }
    /**
     * Takes back the latest roll or pick: its slot goes back to the brush it had. Returns that slot, or
     * -1 if there is none.
     */
    int undo() {
        Change last = changes.pollLast();
        if (last == null) return -1;
        undone.clear();
        for (int i = last.with.size() - 1; i >= -1; i--) {
            Change c = i < 0 ? last : last.with.get(i);
            set(c.slot, c.before);
            if (c.look) { store.put(ToolSwatches.PX + key(c.slot), c.px); store.put(ToolSwatches.DARK + key(c.slot), c.dark); }
            undone.add(0, c.slot);
        }
        return last.slot;
    }
    /** The slots the latest {@link #undo} changed, first slot first (more than one for a tool kit's step). */
    List<Integer> undone() { return new ArrayList<>(undone); }
    private final List<Integer> undone = new ArrayList<>();
    /** The page changed, or is about to (a stroke starts, Undo or Redo changes it): Undo is the page's again. */
    void pageChanged() { changes.clear(); }

    /** Puts {@code brush} in slot {@code slot}, for Undo to take back; false if the slot holds it already. */
    private boolean change(int slot, Brush brush) {
        if (frozen && slot != STAMP) return false;
        Brush before = brush(slot);
        if (before.sameAs(brush)) return false;
        record(new Change(slot, before.write()));
        set(slot, brush.write());
        return true;
    }
    /** {@code c} is the latest Undo step; the oldest gives way past {@link #UNDOS}. */
    private void record(Change c) {
        changes.addLast(c);
        if (changes.size() > UNDOS) changes.removeFirst();
    }
    private String[] stored() {
        String[] lines = new String[COUNT];
        String text = store.get(SLOTS);
        if (text != null) {
            String[] parts = text.split("\n", -1);
            System.arraycopy(parts, 0, lines, 0, Math.min(COUNT, parts.length));
        }
        return lines;
    }
    /** Stores slot {@code slot} as {@code text}, and every other slot as it reads. */
    private void set(int slot, String text) {
        if (slot == STAMP) { store.put(STAMP_BRUSH, text); return; }
        String[] lines = new String[COUNT];
        for (int i = 0; i < COUNT; i++) lines[i] = i == slot ? text : brush(i).write();
        store.put(SLOTS, String.join("\n", lines));
    }
    private static List<Brush> firsts() {
        List<Brush> all = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) all.add(first(i));
        return all;
    }
    /** A first launch: the {@link #FIRST} brushes, each slot at its size and grey there, the stamp tool at its own, the slots written last. */
    private void start() {
        for (int i = 0; i < COUNT; i++) { swatches.useSize(key(i), firstPx(i)); swatches.useGrey(key(i), firstGrey(i)); }
        swatches.useSize(STAMP_KEY, FIRST_STAMP_PX); swatches.useGrey(STAMP_KEY, FIRST_STAMP_GREY);
        swatches.useSize(ToolSwatches.ERASER_KEY, FIRST_ERASER_PX);
        startFrom(0);
    }
    /**
     * Slots {@code from} on take the {@link #FIRST} brushes, and every slot is written, the ones before {@code from} as
     * they are stored, line for line.
     */
    private void startFrom(int from) {
        String[] lines = stored();
        for (int i = from; i < COUNT; i++) lines[i] = first(i).write();
        store.put(SLOTS, String.join("\n", lines));
    }
    /** The brush each of {@code lines} starts with (a slot's line, or a {@link Toolbar}'s), null where none reads. */
    private static List<Brush> lineBrushes(List<String> lines) {
        List<Brush> brushes = new ArrayList<>();
        for (String line : lines) {
            String[] f = line.split("\t", -1);
            brushes.add(Brush.read(f.length > 2 ? f[0] + "\t" + f[1] : line));
        }
        return brushes;
    }
    /**
     * Which of a toolbar kept with ten slots (before 2026-11-06) gives way for nine: the first holding Fill in, which has
     * its own place now ({@link #FILL}); with none, the last.
     */
    static int dropAt(List<Brush> brushes) {
        for (int i = 0; i < brushes.size(); i++) if (fills(brushes.get(i))) return i;
        return brushes.size() - 1;
    }
    /**
     * Once a toolbar kept with ten slots (before 2026-11-06) is read: one gives way ({@link #dropAt}), the slots after it
     * moving up, each with its own size, grey and held wet ink, the selection and the finger staying on the same brush. A
     * Fill in that goes leaves its size, grey and selection to Fill in's own place; any other brush (the tenth) is kept
     * in Recent ({@link #keep}), so it isn't lost. The app's own toolbar waiting while a notebook's is in the slots too. No
     * Undo step. Done before {@link #convert} and the rest, which read {@link #COUNT} slots. An older build reads the nine
     * as eight before 2026-10-27, gaining a tenth.
     */
    private void shrink() {
        String waiting = store.get(APPWIDE);
        if (waiting != null) {
            List<String> lines = new ArrayList<>(java.util.Arrays.asList(waiting.split("\n", -1)));
            while (lines.size() > COUNT) {
                List<Brush> brushes = lineBrushes(lines);
                int drop = dropAt(brushes);
                if (!fills(brushes.get(drop))) keep(brushes.get(drop));
                lines.remove(drop);
            }
            store.put(APPWIDE, String.join("\n", lines));
        }
        List<String> lines = new ArrayList<>(java.util.Arrays.asList(store.get(SLOTS).split("\n", -1)));
        while (lines.size() > COUNT) {
            List<Brush> brushes = lineBrushes(lines);
            int drop = dropAt(brushes), last = lines.size() - 1;
            boolean fill = fills(brushes.get(drop));
            String[] owns = {SIZE, SHADE, ToolSwatches.PX, ToolSwatches.GREY, ToolSwatches.DARK, HeldPaint.WET_INK};
            for (String own : owns) {
                for (String finger : new String[]{"", "/finger"}) {
                    if (fill) store.put(own + FILL_KEY + finger, store.get(own + key(drop) + finger));
                    for (int i = drop; i < last; i++) store.put(own + key(i) + finger, store.get(own + key(i + 1) + finger));
                    store.put(own + key(last) + finger, null);
                }
            }
            String selected = store.get(SELECTED), finger = store.get(FingerTool.KEY);
            for (int i = drop; i <= last; i++) {
                if (key(i).equals(selected)) store.put(SELECTED, i > drop ? key(i - 1) : fill ? FILL_KEY : key(0));
                // FingerTool's keys count slots from 1; an unread one is the eraser.
                if (("slot" + (i + 1)).equals(finger)) store.put(FingerTool.KEY, i > drop ? "slot" + i : fill ? FingerTool.Choice.FILL.key : null);
            }
            if (!fill) keep(brushes.get(drop));
            lines.remove(drop);
            store.put(SLOTS, String.join("\n", lines));
        }
    }
    /**
     * A toolbar kept with fewer slots than {@link #COUNT} (six before 2026-10-19, eight before 2026-10-27): its slots stay
     * exactly as they are, line for line, the new ones take what a first launch has there, at their style's size. Done
     * first, before {@link
     * #erasable} and {@link #convert} (which write every slot), so they find the old slots as they were.
     */
    private void grow() {
        int kept = store.get(SLOTS).split("\n", -1).length;
        if (kept < COUNT) startFrom(kept);
    }
    /**
     * Once, when every medium came to start at Erasable 1 (2026-10-22; the owner: "per-tool things should be erasable
     * unless the user specifies"): every brush kept here (the slots, the app's own toolbar while a notebook's is open,
     * the smudge and stamp tools, the recent brushes and the library) whose medium is below 1 goes to 1, Resist's (wax or
     * fluid) left as it is: the low values came from the defaults and rolls before, not a choice. Only the stored copies:
     * a share code pasted later reads as written. Done after {@link #convert}, so the slots it converts are six-part.
     */
    private void erasable() {
        if (store.get(ERASABLE) != null) return;
        for (String key : new String[]{SLOTS, APPWIDE, SMUDGE_BRUSH, STAMP_BRUSH, RECENT, BrushLibrary.LIBRARY}) {
            String text = store.get(key);
            if (text == null) continue;
            String now = erasableLines(text);
            if (!now.equals(text)) store.put(key, now);
        }
        store.put(ERASABLE, "1");
    }
    /** {@code text}'s lines, each tab-separated field that is a share code ({@link ModularCode#decode}) at Erasable 1 ({@link #erasableCode}). */
    static String erasableLines(String text) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String[] fields = lines[i].split("\t", -1);
            for (int f = 0; f < fields.length; f++) fields[f] = erasableCode(fields[f]);
            lines[i] = String.join("\t", fields);
        }
        return String.join("\n", lines);
    }
    /** {@code field} as it is, unless it is a share code whose medium (not Resist) is below Erasable 1: then that code at 1. */
    static String erasableCode(String field) {
        if (field.indexOf(".M") < 0 && field.indexOf(".m") < 0) return field;
        ModularBrush b = ModularCode.decode(field);
        if (b == null) return field;
        ModularBrush.Part m = b.part(BrushPart.MEDIUM);
        if (!m.has(PartDial.ERASABLE) || m.choice == PartChoice.RESIST || m.get(PartDial.ERASABLE) >= 1) return field;
        m.set(PartDial.ERASABLE, 1);
        return ModularCode.encode(b);
    }
    /**
     * Once (2026-10-28, when the owner's own toolbar became {@link #FIRST} in a new order): slots holding exactly FIRST's
     * ten brushes, matched by their settings alone (not name, size or grey), in any order, take FIRST's order, each brush
     * with its own size, grey and held wet ink, the selection and the finger staying on the same brush. No Undo step.
     * Waits while a notebook's own toolbar is in the slots.
     * shortcut: only a toolbar of exactly FIRST's ten moves, any other is left as it is; upgrade if others want FIRST's order.
     */
    private void reorder() {
        if (store.get(REORDERED) != null || inNotebook()) return;
        int[] from = new int[COUNT];
        java.util.Arrays.fill(from, -1);
        for (int i = 0; i < COUNT; i++) {
            String settings = brush(i).parts.key();
            for (int j = 0; j < COUNT; j++) if (from[j] < 0 && first(j).parts.key().equals(settings)) { from[j] = i; break; }
        }
        boolean all = true;
        for (int f : from) all &= f >= 0;
        if (all) {
            String[] lines = stored(), moved = new String[COUNT];
            for (int j = 0; j < COUNT; j++) moved[j] = lines[from[j]];
            for (String own : new String[]{SIZE, SHADE, ToolSwatches.PX, ToolSwatches.GREY, ToolSwatches.DARK, HeldPaint.WET_INK}) {
                for (String finger : new String[]{"", "/finger"}) {
                    String[] values = new String[COUNT];
                    for (int i = 0; i < COUNT; i++) values[i] = store.get(own + key(i) + finger);
                    for (int j = 0; j < COUNT; j++) store.put(own + key(j) + finger, values[from[j]]);
                }
            }
            String selected = store.get(SELECTED);
            FingerTool.Choice finger = FingerTool.Choice.saved(store.get(FingerTool.KEY));
            for (int j = 0; j < COUNT; j++) {
                if (key(from[j]).equals(selected)) store.put(SELECTED, key(j));
                if (finger.slot() && finger.tool() == from[j]) store.put(FingerTool.KEY, FingerTool.Choice.values()[FingerTool.Choice.SLOT_1.ordinal() + j].key);
            }
            store.put(SLOTS, String.join("\n", moved));
        }
        store.put(REORDERED, "1");
    }
    /**
     * Once (2026-11-05, {@link #HB_DARK}; the owner: "HB pencil is not usable... need to remove the pressure - dark
     * adjustment"): a slot holding the HB pencil as it was (Pressure Dark .8, now RETIRED), the app's own toolbar's
     * while a notebook's is open too, holds today's, at its own size and grey. Any other brush, the recent brushes and a
     * notebook's own toolbar stay as they are; a share code pasted later reads as written.
     */
    private void hbDark() {
        if (store.get(HB_DARK) != null) return;
        for (String key : new String[]{SLOTS, APPWIDE}) {
            String text = store.get(key);
            if (text != null) store.put(key, hbDarkLines(text));
        }
        store.put(HB_DARK, "1");
    }
    /** {@code text}'s lines (slots', or a {@link Toolbar}'s), each that starts with the HB pencil as it was starting with today's. */
    static String hbDarkLines(String text) {
        Brush was = null;
        for (ToolPresets.Preset p : ToolPresets.retired()) if (p.name.equals("HB pencil")) was = p.brush();
        String now = ToolKits.preset("HB pencil").brush().write();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String[] f = lines[i].split("\t", -1);
            if (f.length >= 2 && was.sameAs(Brush.read(f[0] + "\t" + f[1]))) lines[i] = now + lines[i].substring(f[0].length() + 1 + f[1].length());
        }
        return String.join("\n", lines);
    }
    /**
     * Slots stored before the six-part engine become six-part brushes ({@link LegacyBrushes}), each at the size and grey
     * its slot keeps ({@link ToolSwatches#own}).
     */
    private void convert() {
        String[] lines = stored();
        boolean any = false;
        for (int i = 0; i < COUNT; i++) {
            LegacyBrushes.Old old = LegacyBrushes.read(lines[i]);
            if (old == null) continue;
            lines[i] = Brush.of(old.brush).write();
            any = true;
        }
        if (!any) return;
        for (int i = 0; i < COUNT; i++) if (lines[i] == null) lines[i] = brush(i).write();
        store.put(SLOTS, String.join("\n", lines));
    }
}
