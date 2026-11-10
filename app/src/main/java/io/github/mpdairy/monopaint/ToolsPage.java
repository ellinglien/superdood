package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;

/**
 * The Tools page's state (spec docs/archive/superpowers/specs/2026-10-15-tools-page-design.md; the redesign, 2026-11-03,
 * the owner: "it's not clear what to do once you're there"): the nine slots, one of them selected ({@link #select}; tap a
 * slot, then a card: "click a slot and then click an option from library"), each slot's tool as it will go back (a card
 * changes the selected one here, not yet in the slot; the try strip draws with it, {@link #look}), what each slot held
 * when the page opened ({@link #start}, carried through Brush Lab: the dot under a changed slot), the unsaved brush a
 * change lets go kept in Recent ({@link ToolSlots#keep}, its note {@link #note}), and one grid of cards, paged: the
 * {@link Chip} chosen in the one filter row (Recent, Saved, the kinds, Kits; tabs over chips until 2026-11-03). No Undo
 * here: a brush let go is kept in Recent, a Basic tool is always under its kind, a saved one in Saved, and the drawing
 * screen's Undo takes back each slot's change once the page is left. The chip is kept between visits (the first time,
 * the kind of the selected slot's tool); the grid opens on the page holding the selected slot's tool. A search ({@link
 * #search}) looks through the Basic tools, Recent and Saved at once. The selected slot tapped again is picked up, and
 * a tap on another swaps the two ({@link #tap}). What the drawing screen gets on Back is {@link #results} and {@link
 * #moved}. Pure, checked on the desktop.
 */
final class ToolsPage {
    /**
     * The filter row (the owner's mockup, 2026-10-28; one row, 2026-11-03): Recent (the brushes put in slots lately,
     * {@link ToolSlots#recent}), Saved (the library's brushes that aren't Basic tools), the kinds of tool (each the shown
     * tools of a few styles, {@link ToolPresets#shown}, {@link ModularStyle#name}s; Odd Ones the wild ones), More Brushes
     * (the shelf, {@link ToolPresets#shelf}: every other Basic tool, kind by kind; its chip beside Search, {@link
     * ToolsLayout#chipLabels}), then Kits ({@link ToolKits}). Every style
     * is in one kind (checked). A brush goes under what it does, as the tool picker groups ({@link DrawLayout#PICKER}; the
     * design review, 2026-10-31): an effect under Effects (the last kind, 2026-11-06), an odd one under Odd Ones, a print under Print, the rest under their style's kind
     * ({@link #of(ModularBrush)}); the style isn't shown.
     */
    enum Chip {
        RECENT("Recent"), SAVED("Saved"),
        PENS("Pens", "Fineliner", "Dip pen"), MARKERS("Markers", "Marker"), BRUSHES("Brushes", "Brush pen", "Dry brush", "Hake"),
        WASHES("Washes", "Watercolor", "Ink wash"), PAINT("Paint", "Gouache", "Oils"), DRY_MEDIA("Dry Media", "Pencil", "Charcoal", "Pastels"),
        PRINT("Print", "Print"), ODD_ONES("Odd Ones"), EFFECTS("Effects"), KITS(ToolKits.TAB),
        /** The shelf, beside Search, not in the filter row (which stays one row on the Manta upright): last, so the row's chips keep their places. */
        MORE("More Brushes");
        final String label;
        private final List<String> styles;
        Chip(String label, String... styles) { this.label = label; this.styles = java.util.Arrays.asList(styles); }
        /** Whether it is a kind of tool (its cards the shown tools of that kind), not Recent, Saved, More Brushes or Kits. */
        boolean kind() { return this != RECENT && this != SAVED && this != MORE && this != KITS; }
        /** Whether {@code b} goes under this kind ({@link #of(ModularBrush)}). */
        boolean has(ModularBrush b) { return of(b) == this; }
        /** The kind {@code style} is (Odd Ones for one no kind names: the wild ones). */
        static Chip of(ModularStyle style) {
            for (Chip c : values()) if (c.kind() && c.styles.contains(style.name)) return c;
            return ODD_ONES;
        }
        /**
         * The kind {@code b} goes under, by what it does: Effects for one that only reworks the marks there ({@link
         * WildSample#reworks}, the tools named "… Effect"; the owner, 2026-11-06: "effect brushes should move to their own
         * section"), Odd Ones for the picker's odd ones' choices (a Spiro or a Stamp, Stay in Lines or Filled Shape, Float,
         * Mold, Fading or Lemon Juice; a Later's media, Cracks among them, goes with its medium: "cracks... it's more of a
         * paint"), Print for a print (Screentone, Print Shading) or a texture tip ({@link #PRINT_TIPS}), else its style's
         * kind ({@link #of(ModularStyle)}).
         */
        static Chip of(ModularBrush b) {
            if (WildSample.reworks(b)) return EFFECTS;
            PartChoice tip = b.choice(BrushPart.TIP), carry = b.choice(BrushPart.CARRY), later = b.mischief();
            if (tip == PartChoice.SPIRO || tip == PartChoice.STAMP || carry == PartChoice.STAY_IN || carry == PartChoice.SHAPE
                    || later == PartChoice.LATER_FLOAT || later == PartChoice.LATER_MOLD || later == PartChoice.LATER_FADE
                    || later == PartChoice.LATER_LEMON) return ODD_ONES;
            ModularBrush.Part m = b.part(BrushPart.MEDIUM);
            if (m.choice == PartChoice.TONE || m.get(PartDial.SCREEN, 0) > 0 || PRINT_TIPS.contains(tip)) return PRINT;
            return of(b.style);
        }
        /** The kind a brush goes under: its preset's ({@link #of(ToolPresets.Preset)}) if it is a Basic tool as it is, else {@link #of(ModularBrush)}. */
        static Chip of(Brush b) {
            ToolPresets.Preset p = ToolPresets.find(b);
            return p != null ? of(p) : of(b.parts);
        }
        /** The kind a preset is filed under: its row's kind in {@link ToolPresets#KINDS} if it has one, else {@link #of(ModularBrush)}. */
        static Chip of(ToolPresets.Preset p) {
            for (String[] k : ToolPresets.KINDS) if (k[0].equals(p.name)) return valueOf(k[1]);
            return of(p.brush().parts);
        }
        /**
         * The tips that lay a texture of their own, dots or lines, so go under Print with the screens (the owner, 2026-11-06:
         * "move stipple pen and hatch pen to the texture brush area").
         */
        static final java.util.Set<PartChoice> PRINT_TIPS = java.util.EnumSet.of(PartChoice.STIPPLE, PartChoice.HATCH, PartChoice.HAND_HATCH);
        /** A kept chip: its name, or (kept before the chips, 2026-10-28) a style's name, its kind; null for nothing known (All, before 2026-11-03). */
        static Chip kept(String stored) {
            for (Chip c : values()) if (c.name().equals(stored)) return c;
            ModularStyle style = stored == null ? null : ModularStyle.named(stored);
            return style == null ? null : of(style);
        }
    }

    /** A card: the brush it puts in the slot, its name and its one-line description; or, under Kits, a kit (its first brush shown). */
    static final class Card {
        final Brush brush;
        final String name, line;
        /** The kit the card stands for (Kits), else null. */
        final ToolKits.Kit kit;
        Card(Brush brush, String name, String line) { this(brush, name, line, null); }
        Card(Brush brush, String name, String line, ToolKits.Kit kit) { this.brush = brush; this.name = name; this.line = line; this.kit = kit; }
    }

    /** Where the chip chosen is kept (its name; a style's name before 2026-10-28, empty for All before 2026-11-03). */
    static final String STYLE = "tools_style";

    final ToolSlots slots;
    /** The slot selected: the one a card changes. */
    private int slot;
    private final BrushLibrary.Store store;
    /** Each slot's tool when the page opened, and as it is now. */
    private final Brush[] opened = new Brush[ToolSlots.COUNT], current = new Brush[ToolSlots.COUNT];
    /** What each slot held at the start: when the page opened, or when the Tools page before Brush Lab did. */
    final ToolSlots.Start start;
    /** What the latest change says once (the brush it kept in Recent), or null. */
    private String note;
    /** Whether a kit was loaded: the drawing screen takes the page's changes back with one Undo. */
    private boolean kit;
    /** The chip chosen. */
    private Chip filter;
    /**
     * How many slots the bar shows ({@link NotebookOptions#slotsShown}) as the page opened; the count a kit made it (the
     * same: unchanged), and the eraser's kind a kit set (-1: untouched).
     */
    private int barOpened = ToolSlots.COUNT, bar = ToolSlots.COUNT, eraser = -1;
    /** The grid's page, and how many cards a page holds. */
    private int page, perPage = 1;
    /** How wide a card's description may be, dp ({@link ToolsLayout#cardWidth}), at 13 sp. */
    private float lineRoom = LabText.width("x", LabText.CAPTION_SP, 0) * BrushCaptions.LINE;
    /** The chip's cards (every source's while searching), read once a chip; null when the chip, the search or the cards changed. */
    private List<Card> cached;
    /** What the search looks for, trimmed; empty for none (the chip's cards). Not kept. */
    private String search = "";
    /** The slot picked up to move ({@link #tap}), or -1. */
    private int lifted = -1;
    /** Each slot's size and grey a swap brought into it; null: the slot's own ({@link #look(int)}). */
    private final ToolSwatches.Look[] moved = new ToolSwatches.Look[ToolSlots.COUNT];
    /** Whether the notebook's toolbar can't change ({@link ToolsActivity#FIXED}): no slot is picked up. */
    boolean fixed;

    /**
     * Opens on slot {@code slot}, and on the chip last chosen ({@code store}); the first time, on the kind of that slot's
     * tool: the cards most like it, the slot's own among them.
     */
    ToolsPage(ToolSlots slots, BrushLibrary.Store store, int slot) { this(slots, store, slot, null); }
    /** As above, the slots' start {@code start} ({@link ToolSlots.Start#write}'s lines; null: as they are now). */
    ToolsPage(ToolSlots slots, BrushLibrary.Store store, int slot, String[] start) {
        this.slots = slots; this.store = store; this.slot = slot;
        this.start = ToolSlots.Start.read(start, slots);
        for (int i = 0; i < ToolSlots.COUNT; i++) { opened[i] = slots.brush(i); current[i] = opened[i]; }
        Chip kept = Chip.kept(store.get(STYLE));
        filter = kept != null && java.util.Arrays.asList(ToolsLayout.rowChips()).contains(kept) || kept == Chip.MORE ? kept : Chip.of(opened[slot]);
    }
    /** The bar shows {@code shown} slots (1 to 8), which a kit may raise. */
    void bar(int shown) { barOpened = bar = Math.max(1, Math.min(ToolSlots.COUNT, shown)); }
    /** How many slots the bar shows now. */
    int bar() { return bar; }
    /** The slots the bar shows now, if a kit changed it; else -1. */
    int barResult() { return bar == barOpened ? -1 : bar; }
    /** The eraser's kind a kit set ({@link EraseKind#LABELS} index), else -1. */
    int eraser() { return eraser; }
    /** Whether a kit was loaded: the drawing screen takes the page's changes back with one Undo. */
    boolean together() { return kit; }

    /** The slot selected. */
    int slot() { return slot; }
    /** The page now changes slot {@code i}; the grid turns to the page holding its tool. */
    void select(int i) {
        if (i < 0 || i >= ToolSlots.COUNT || i == slot) return;
        slot = i;
        show(current[i]);
    }
    /**
     * A tap on slot {@code i} (the owner, 2026-11-09: "can we add a way to reorder the toolbars?"): another slot is
     * selected; the selected one again is picked up ({@link #lifted}), unless the toolbar is fixed. With one picked up, a
     * tap on another swaps the two, their brushes, sizes and greys, the moved one selected where it lands; on itself, it
     * is put back.
     */
    void tap(int i) {
        if (i < 0 || i >= ToolSlots.COUNT) return;
        if (lifted >= 0) { if (i != lifted) swap(lifted, i); lifted = -1; }
        else if (i != slot) select(i);
        else if (!fixed) lifted = i;
    }
    /** The slot picked up, or -1. */
    int lifted() { return lifted; }
    /** Slot {@code from}'s brush, size and grey go to slot {@code to}, and {@code to}'s to {@code from}; {@code to} is selected. */
    private void swap(int from, int to) {
        Brush brush = current[from];
        ToolSwatches.Look look = look(from);
        moved[from] = look(to); current[from] = current[to];
        moved[to] = look; current[to] = brush;
        slot = to;
        show(brush);
    }
    /** The selected slot's tool as it stands on the page. */
    Brush current() { return current[slot]; }
    /** Slot {@code i}'s tool as it stands on the page. */
    Brush current(int i) { return current[i]; }
    /** What the drawing screen puts in the selected slot: its tool as it stands, or null when it is the one the slot has. */
    Brush result() { return result(slot); }
    private Brush result(int i) { return current[i].sameAs(opened[i]) ? null : current[i]; }
    /** What the drawing screen puts in each slot: its tool as it stands, or null when it is the one the slot has. */
    Brush[] results() {
        Brush[] results = new Brush[ToolSlots.COUNT];
        for (int i = 0; i < results.length; i++) results[i] = result(i);
        return results;
    }
    /** What the try strip draws the selected slot's tool at: the slot's own size and grey, as Brush Lab's does. */
    ToolSwatches.Look look() { return look(slot); }
    /** As {@link #look()}, for slot {@code i} (its tile among the nine). */
    ToolSwatches.Look look(int i) { return moved[i] != null ? moved[i] : own(i); }
    /** Slot {@code i}'s size and grey as Back leaves it with no swap: a pick's own where it brings them ({@link ToolSwatches#brought}), else the slot's. */
    private ToolSwatches.Look own(int i) {
        String key = ToolSlots.key(i);
        return result(i) != null ? slots.swatches.brought(key, current[i]) : slots.swatches.look(key, current[i]);
    }
    /**
     * What the drawing screen sets each slot's size and grey to once its brush is in ({@link ToolSlots#take}): px, then
     * darkness, for each slot; NaN where no swap brought one, or brought back its own (swapped there and back).
     */
    double[] moved() {
        double[] looks = new double[2 * ToolSlots.COUNT];
        for (int i = 0; i < ToolSlots.COUNT; i++) {
            ToolSwatches.Look own = moved[i] == null ? null : own(i);
            boolean none = own == null || own.px == moved[i].px && own.darkness == moved[i].darkness;
            looks[2 * i] = none ? Double.NaN : moved[i].px;
            looks[2 * i + 1] = none ? Double.NaN : moved[i].darkness;
        }
        return looks;
    }

    /** Lays the grid out: {@code perPage} cards a page, descriptions up to {@code lineRoom} dp wide; opens on the page holding the slot's tool. */
    void layout(int perPage, float lineRoom) {
        this.perPage = Math.max(1, perPage);
        this.lineRoom = lineRoom;
        cached = null;
        show(current[slot]);
    }

    // The filter row

    Chip filter() { return filter; }
    /** The cards under {@code chip}, on the page holding the slot's tool (the first, when it isn't there); kept. */
    void filter(Chip chip) {
        if (chip == filter) return;
        filter = chip;
        store.put(STYLE, chip.name());
        cached = null;
        page = 0;
        show(current[slot]);
    }

    // The search (the owner, 2026-10-28: "it's difficult to find things in the tools library")

    String search() { return search; }
    /** Whether a search is on: the grid shows what it finds, not the chip's cards. */
    boolean searching() { return !search.isEmpty(); }
    /**
     * Searches the shown tools, Recent and Saved together for {@code words} ({@link #matches}), from the first page;
     * empty (or null) shows the chip's cards again, on the page holding the tool.
     */
    void search(String words) {
        String now = words == null ? "" : words.trim();
        if (now.equals(search)) return;
        search = now;
        cached = null;
        page = 0;
        if (!searching()) show(current[slot]);
    }
    /** Whether every word of {@code words} is in {@code card}'s name, its style's, its kind's or its line, any case, any order. */
    static boolean matches(Card card, String words) {
        ModularStyle style = card.brush.parts.style;
        String in = (card.name + " " + style.name + " " + style.label + " " + Chip.of(card.brush).label + " " + card.line).toLowerCase(java.util.Locale.ROOT);
        for (String word : words.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+")) if (!in.contains(word)) return false;
        return true;
    }

    // The grid

    int page() { return page; }
    int pages() { return LabPages.pages(all().size(), perPage); }
    /** Turns the grid by {@code delta} pages, kept inside the pages there are. */
    void turn(int delta) { page = LabPages.clamp(page + delta, all().size(), perPage); }
    /** The grid's page's cards. */
    List<Card> cards() {
        List<Card> all = all();
        page = LabPages.clamp(page, all.size(), perPage);
        return new ArrayList<>(all.subList(page * perPage, Math.min(all.size(), page * perPage + perPage)));
    }
    /**
     * Every card under the chip: Recent, latest first; Saved, the library's brushes that aren't Basic tools, newest first;
     * a kind's shown tools in their order; the shelf's, kind by kind; the kits. Searching: what it finds among the shown
     * tools, Recent and Saved,
     * each brush once.
     */
    List<Card> all() {
        if (cached != null) return cached;
        List<Card> cards = cached = new ArrayList<>();
        if (searching()) {
            List<Card> every = new ArrayList<>();
            for (ToolPresets.Preset p : ToolPresets.shown()) every.add(card(p.brush(), lineRoom));
            for (Brush b : slots.recent()) every.add(card(b, lineRoom));
            every.addAll(saved());
            for (Card c : every) {
                if (!matches(c, search)) continue;
                boolean twice = false;
                for (int i = 0; i < cards.size() && !twice; i++) twice = cards.get(i).brush.sameAs(c.brush);
                if (!twice) cards.add(c);
            }
            return cards;
        }
        switch (filter) {
            case RECENT:
                for (Brush b : slots.recent()) cards.add(card(b, lineRoom));
                break;
            case SAVED:
                cards.addAll(saved());
                break;
            case MORE:
                // The shelf (the release pare-down, 2026-11-05: "a shelf in the app"): every other Basic tool, kind by kind.
                for (Chip c : Chip.values()) if (c.kind()) for (ToolPresets.Preset p : ToolPresets.shelf()) if (Chip.of(p) == c) cards.add(card(p));
                break;
            case KITS:
                for (ToolKits.Kit k : ToolKits.all()) { Brush[] tools = k.tools(); if (tools.length > 0 && tools[0] != null) cards.add(new Card(tools[0], k.name, kitLine(k), k)); }
                break;
            default:
                for (ToolPresets.Preset p : ToolPresets.shown()) if (Chip.of(p) == filter) cards.add(card(p.brush(), lineRoom));
        }
        return cards;
    }
    /** The library's brushes that aren't Basic tools, newest first. */
    private List<Card> saved() {
        List<Card> cards = new ArrayList<>();
        for (BrushLibrary.Entry e : slots.library.entries()) if (ToolPresets.find(e.brush) == null) cards.add(card(e.brush, lineRoom));
        return cards;
    }
    private static Card card(ToolPresets.Preset p) { Brush b = p.brush(); return new Card(b, b.shownName(), p.line); }
    /** {@code brush}'s card: a Basic tool's own name and line; any other's name and caption, one line {@code lineRoom} dp wide (the smudge tool's chooser too). */
    static Card card(Brush brush, float lineRoom) {
        ToolPresets.Preset p = ToolPresets.find(brush);
        return p != null ? card(p) : new Card(brush, brush.shownName(), BrushCaptions.oneLine(ModularWords.caption(brush.parts), LabText.face(LabText.CAPTION_SP, 0), lineRoom));
    }
    /** {@code brush}'s card's name alone ({@link #card(Brush, float)}'s, without its line): a Basic tool's own, else the brush's. */
    static String name(Brush brush) {
        ToolPresets.Preset p = ToolPresets.find(brush);
        return WildSample.shownName(p != null ? p.name : brush.name(), brush.parts);
    }
    /** What the grid says when the chip has nothing: "Nothing in Recent yet", "Nothing found". */
    String empty() { return searching() ? "Nothing found" : filter == Chip.KITS ? "No kits" : "Nothing in " + filter.label + " yet"; }
    /** Whether {@code card} holds the slot's tool as it stands (it is underlined). */
    boolean now(Card card) { return card != null && card.kit == null && card.brush.sameAs(current[slot]); }
    /** A kit's card words: how many brushes it puts on the bar ("3 brushes"). */
    static String kitLine(ToolKits.Kit k) { return k.size() == 1 ? "1 brush" : k.size() + " brushes"; }
    /** Turns the grid to the page holding {@code brush}, where it is in it. */
    private void show(Brush brush) {
        List<Card> all = all();
        for (int i = 0; i < all.size(); i++) if (all.get(i).kit == null && all.get(i).brush.sameAs(brush)) { page = LabPages.of(i, perPage); return; }
    }

    // Changes

    /**
     * A card's brush is the selected slot's tool now (the page stays, to try it); the unsaved brush it replaces is kept in
     * Recent; the slot takes its own size and grey again. The tool already: nothing. A slot picked up is put back first.
     */
    void put(Brush brush) {
        lifted = -1;
        if (brush.sameAs(current[slot])) return;
        replace(slot, brush);
        current[slot] = brush;
        moved[slot] = null;
    }
    /**
     * The Basic tool the selected slot's tool was before Brush Lab changed it, or null: what Reset to Default puts back,
     * shown beside Edit in Brush Lab exactly when the tool picker shows its top row ({@link DrawLayout#edited}; the owner,
     * 2026-11-03: "reset to default on the toolbar page would be good too"). A tap puts it as a card does ({@link #put}:
     * the edited brush kept in Recent).
     */
    ToolPresets.Preset edited() { return DrawLayout.edited(current[slot]); }
    /** Slot {@code i} lets its tool go for {@code next}:an unsaved one is kept in Recent, and the note says so. */
    private boolean replace(int i, Brush next) {
        if (current[i].sameAs(next) || !slots.keep(current[i])) return false;
        note = ToolSlots.keptNote(current[i]);
        if (filter == Chip.RECENT || searching()) cached = null;
        return true;
    }
    /** What the latest change said (the brush kept in Recent), once; else null. */
    String note() { String said = note; note = null; return said; }
    /** Whether slot {@code i} holds something else than at the start (the dot under it). */
    boolean changed(int i) { return start.changed(i, current[i]); }

    // Deleting a saved brush

    /** Whether {@code card} is a saved brush that can be deleted (a library brush, not a Basic tool or a kit). */
    boolean deletable(Card card) { return card.kit == null && ToolPresets.find(card.brush) == null && slots.library.contains(card.brush); }
    /** Deletes {@code card}'s brush from the library (a long press, confirmed); false when it isn't deletable. */
    boolean delete(Card card) {
        if (!deletable(card)) return false;
        slots.library.delete(slots.library.find(card.brush).id);
        cached = null;
        return true;
    }

    // Kits

    /** Whether loading {@code kit} raises the bar's count (Brushes on Bar) to show it all. */
    boolean raises(ToolKits.Kit kit) { return kit.size() > bar; }
    /**
     * Loads {@code kit}: its brushes fill the bar's first slots in order (the bar shows the first slots whatever its count,
     * so a kit there is always seen, and its numbers read along the bar), the first selected; the bar shows at least
     * that many; its eraser step sets the eraser's kind. The drawing screen takes it all back with one Undo.
     * Whether anything changed.
     */
    boolean loadKit(ToolKits.Kit kit) {
        if (kit == null || kit.size() > ToolSlots.COUNT) return false;
        lifted = -1;
        Brush[] tools = kit.tools();
        int newBar = Math.max(bar, kit.size()), newEraser = kit.eraser != null ? kit.eraser.eraser : eraser;
        boolean changes = newBar != bar || newEraser != eraser;
        for (int i = 0; i < tools.length; i++) {
            if (tools[i] == null) return false;
            changes |= !tools[i].sameAs(current[i]);
        }
        if (!changes) { select(0); return false; }
        this.kit = true;
        int kept = 0;
        for (int i = 0; i < tools.length; i++) {
            if (replace(i, tools[i]) && ++kept > 1) note = "Kept " + kept + " brushes in Recent";
            current[i] = tools[i];
            moved[i] = null;
        }
        bar = newBar; eraser = newEraser;
        slot = 0;
        show(current[0]);
        return true;
    }
}
