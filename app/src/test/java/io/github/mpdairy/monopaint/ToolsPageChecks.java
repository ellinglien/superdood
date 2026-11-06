package io.github.mpdairy.monopaint;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class ToolsPageChecks {
    static Map<String, String> prefs = new HashMap<>();
    static BrushLibrary.Store store() {
        return new BrushLibrary.Store() {
            public String get(String key) { return prefs.get(key); }
            public void put(String key, String value) { if (value == null) prefs.remove(key); else prefs.put(key, value); }
        };
    }
    static final ModularStyle PENCIL = ModularStyle.named("Pencil"), WATERCOLOR = ModularStyle.named("Watercolor"), WILD = ModularStyle.named(ModularStyle.ANYTHING_GOES);

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        chips(); refocus(); opens(); remembers(); sources(); recent(); paging(); changes(); six(); tries(); start(); keeps(); resets(); deletes(); lines(); layout(); table(); search();
        System.out.println("PASS: the Tools page (the redesign, 2026-11-03): its bar says its job (Toolbar, \"" + ToolsLayout.JOB + "\"), the ten slots first, a tap choosing the one a card goes to, the selected slot's tool named beside Edit in Brush Lab, the try strip under it (Brush Lab's too); no Undo, Load Kit or Cut Stamp on the page, Tools First only while on; Back taking each slot its pick (the drawing screen's Undo takes each back); one filter row: Recent (put in a slot lately, latest first, each once), Saved (the library but the Basic tools), the kinds (Pens, Markers, Brushes, Washes, Paint, Dry Media, Print, Odd Ones, Effects: every style in one, a Basic tool under what it does as the tool picker groups; a kept style's name opens on its kind) and Kits, paged; opening on the chip last chosen (the first time the slot's tool's kind) and the page holding the slot's tool; a slot changed on this visit marked, the start carried through Brush Lab; an unsaved brush a pick or a kit lets go is kept in Recent, said once; Reset to Default beside Edit in Brush Lab for an edited Basic tool alone, as the tool picker's; a saved card deleted by a long press; a card changes the tool on the page, not the slot, the page staying to try it at the one size and grey; every drawing cut at its handle and one width; every budget fitting both tablets in either turn");
    }

    /** Whether {@code b} is one of the shown tools (ToolPresets.shown). */
    static boolean shown(Brush b) { for (ToolPresets.Preset p : ToolPresets.shown()) if (p.brush().sameAs(b)) return true; return false; }
    static ToolSlots fresh() { prefs = new HashMap<>(); return ToolSlotsChecks.old(store(), new Random(1)); }
    static ToolsPage open(ToolSlots slots, int slot) { return new ToolsPage(slots, store(), slot); }
    static Brush rolled(ModularStyle style, int seed) { return Brush.of(style.roll(new Random(seed))); }

    /** The filter row (the owner, 2026-10-28: "instead of dropdown show chips for the categories"; one row, 2026-11-03): every style in exactly one kind. */
    static void chips() {
        check(String.join(",", ToolsLayout.chipLabels()).equals("Recent,Saved,Pens,Markers,Brushes,Washes,Paint,Dry Media,Print,Effects,Kits"),
                "The row: Recent, Saved, the kinds, Kits: " + String.join(",", ToolsLayout.chipLabels()));
        ToolsPage.Chip[] every = ToolsPage.Chip.values();
        check(every[every.length - 1] == ToolsPage.Chip.MORE && ToolsPage.Chip.MORE.label.equals("More Brushes") && ToolsLayout.rowChips().length == every.length - 2,
                "More Brushes beside Search, out of the row, last so the row's chips keep their places");
        for (ToolsPage.Chip c : ToolsPage.Chip.values()) check(c.kind() == (c != ToolsPage.Chip.RECENT && c != ToolsPage.Chip.SAVED && c != ToolsPage.Chip.MORE && c != ToolsPage.Chip.KITS), c + " is a kind or not");
        String[][] kinds = {{"Fineliner", "PENS"}, {"Dip pen", "PENS"}, {"Marker", "MARKERS"}, {"Brush pen", "BRUSHES"}, {"Dry brush", "BRUSHES"}, {"Hake", "BRUSHES"},
                {"Watercolor", "WASHES"}, {"Ink wash", "WASHES"}, {"Gouache", "PAINT"}, {"Oils", "PAINT"}, {"Pencil", "DRY_MEDIA"}, {"Charcoal", "DRY_MEDIA"},
                {"Pastels", "DRY_MEDIA"}, {"Print", "PRINT"}, {ModularStyle.ANYTHING_GOES, "ODD_ONES"}};
        for (String[] k : kinds) check(ToolsPage.Chip.of(ModularStyle.named(k[0])).name().equals(k[1]), k[0] + " under " + k[1]);
        check(kinds.length == ModularStyle.shown().size(), "Every style has its kind");
        // A brush goes under what it does, as the tool picker groups (the design review, 2026-10-31): its odd ones under Odd
        // Ones (Dot tone and Ben-Day brush, prints, under Print), an effect under Effects (2026-11-06), a Later's media with
        // its medium (Cracks with the paints: "it's more of a paint"), the rest under their style's kind.
        String[][] does = {{"Sumi", "ODD_ONES"}, {"Mold ink", "ODD_ONES"}, {"Colouring pen", "ODD_ONES"}, {"Blend", "EFFECTS"}, {"Water", "EFFECTS"}, {"Photocopier", "EFFECTS"}, {"Cracking paint", "PAINT"}, {"Stipple pen", "PRINT"}, {"Hatch pen", "PRINT"}, {"Stipple sponge", "PRINT"}, {"Letterpress", "PRINT"}, {"Halftone brush", "PRINT"}, {"Masking fluid", "WASHES"},
                {"Spirograph", "ODD_ONES"}, {"Stamp", "ODD_ONES"}, {"Fill in", "ODD_ONES"}, {"Lemon juice", "ODD_ONES"}, {"Vanishing ink", "ODD_ONES"}, {"Dot tone", "PRINT"}, {"Ben-Day brush", "PRINT"}};
        for (String[] d : does) check(ToolsPage.Chip.of(ToolKits.preset(d[0]).brush().parts).name().equals(d[1]), d[0] + " under " + d[1]);
        ModularBrush printed = ToolKits.preset("Steady line").brush().parts.copy();
        printed.part(BrushPart.MEDIUM).set(PartDial.SCREEN, 1);
        check(ToolsPage.Chip.of(printed) == ToolsPage.Chip.PRINT && ToolsPage.Chip.PRINT.has(printed) && !ToolsPage.Chip.PENS.has(printed), "A pen through a Print Shading prints: under Print");
        // Kept: the chip's name; a style's name, kept before the chips, its kind; anything else (All's "") nothing.
        check(ToolsPage.Chip.kept("PENS") == ToolsPage.Chip.PENS && ToolsPage.Chip.kept("Pencil") == ToolsPage.Chip.DRY_MEDIA && ToolsPage.Chip.kept("Print") == ToolsPage.Chip.PRINT
                && ToolsPage.Chip.kept("Wild") == ToolsPage.Chip.ODD_ONES && ToolsPage.Chip.kept("KITS") == ToolsPage.Chip.KITS && ToolsPage.Chip.kept("") == null
                && ToolsPage.Chip.kept("ALL") == null && ToolsPage.Chip.kept("Nonsense") == null && ToolsPage.Chip.kept(null) == null,
                "A kept chip, or a kept style's kind");
    }
    /** What the page shows and what it left (the redesign, 2026-11-03); opening fast; no New Brush; Brush Lab's Anything Goes secondary. */
    static void refocus() {
        try {
            String tools = DrawLayoutChecks.source("ToolsActivity"), lab = DrawLayoutChecks.source("LabSettings"), card = DrawLayoutChecks.source("ToolCard");
            String labPage = DrawLayoutChecks.source("BrushLabActivity"), area = DrawLayoutChecks.source("TestArea");
            // Opening fast (the owner, 2026-10-28: "can it load faster?"): built before the first frame, the art boxes' pictures
            // kept across openings in a bounded cache and drawn in the background, the cards' screen-reader words made when asked.
            check(tools.contains("addOnPreDrawListener(") && !tools.contains("addOnLayoutChangeListener(") && tools.contains("\"Tools page shown in \""),
                    "The page laid out before its first frame, its time logged");
            // The owner, 2026-10-28: "make the non-selected tools a little grey": a solid grey; 2026-11-03, "maybe invert it so
            // it's dark? to make it obvious it's selected": the selected slot an ink tile, its drawing in paper, no underline.
            check(card.contains("static final int GREY = Theme.GREY_600;") && card.contains("fill.setColor(bold ? Theme.ON_INK : grey ? GREY : Theme.INK);")
                    && card.contains("canvas.drawRoundRect(0, 0, getWidth(), tall, r, r, fill);") && tools.contains("card.setChosen(i == selected);")
                    && tools.contains("new ToolCard(this, samples).tile()") && !tools.contains("card.setNow(i == selected)") && !card.contains("setAlpha("),
                    "The slots not selected drawn in solid grey-600, the selected one inverted, not underlined");
            check(card.contains("new LruCache<String, Bitmap>(4 << 20)") && card.contains("samples.request(this, () -> mask(") && !card.contains("picture.recycle()")
                    && !card.contains("drawing.draw(canvas)") && tools.contains("view.describe(() ->"), "Pictures kept (at most 4 MB) and drawn off the main thread; words made when asked");
            // The first opening too (the owner: slow "just on the first load"): drawn ahead once the drawing screen settles, by
            // the page's own tiles (showSlot, showCard: the same keys), never while the pen is down; let go when it leaves.
            String app = DrawLayoutChecks.source("PaintActivity");
            check(tools.contains("showSlot(new ToolCard(context, samples), layout, page, i)") && tools.contains("showSlot(card, layout, page, i)")
                    && tools.contains("showCard(new ToolCard(context, samples), layout, card)") && tools.contains("showCard(view, layout, card)")
                    && app.contains("if (busy() || pad.stroking()) { pad.postDelayed(this.warmTools, WARM_TOOLS_MS); return; }")
                    && app.contains("if (toolPictures != null) { toolPictures.close(); toolPictures = null; }"), "The page's first pictures drawn ahead, as the page shows them, never under the pen");
            check(!tools.contains("ToolsLayout.NEW") && !tools.contains("openStyles") && !tools.contains("PopupWindow"), "No New Brush, no Style menu");
            check(tools.contains("chips = new LabUi.Wrap(") && tools.contains("Theme.rounded(this, on ? Theme.INK : Theme.PAPER, Theme.FINE, Theme.INK"), "The chips on the page, wrapping, rounded, the chosen one filled");
            check(lab.contains("new LabButton(context, R.drawable.ic_ph_shuffle, \"Anything Goes\", false)") && lab.contains("inkButton(\"Randomize\"") && !lab.contains("inkButton(\"Anything Goes\""),
                    "Randomize the big ink button, Anything Goes a smaller plain one beside it");
            // The redesign (2026-11-03, the owner: "undo.. what would undo do in this context? also the tools first selector.. huh").
            check(!tools.contains("ic_ph_undo") && !tools.contains("kitButton") && !tools.contains("StampNib.CUT") && !tools.contains("test.wet()")
                    && !app.contains("ToolsActivity.CUT"), "No Undo, Load Kit, Cut Stamp or wet drop on the page (Tools First only while on: DrawLayoutChecks)");
            check(tools.contains("new LabUi.PageBar(this, layout.side, \"Back\"") && labPage.contains("new LabUi.PageBar(this, layout.side, fromTools ? ToolsLayout.TITLE : \"Back\", LabLayout.TITLE, null)"),
                    "One bar for both pages: Back, the title (over the Tools page's job; Brush Lab's alone since 2026-11-06)");
            check(tools.contains("test.strip(ToolsLayout.TRY_HEAD, 0, 8)") && labPage.contains("test.strip(layout.headRow, layout.gap, layout.clearPad)") && !area.contains("WetIndicator")
                    && tools.contains("test.label(card.name)") && labPage.contains("test.label(lab.brush().shownName())") && !labPage.contains("\"Scratch Pad\"")
                    && area.contains("new TiltFeed(pad, session)"), "One try strip for both pages, named after the brush; the tilt read through its page");
            // 2026-11-03, the owner: "move the preview area to the bottom": in portrait the slots' row, the name row, the Library, the try strip.
            int portrait = tools.indexOf("} else {", tools.indexOf("if (layout.wide) {", tools.indexOf("private boolean build()")));
            check(tools.indexOf("column.addView(slots(),") < tools.indexOf("main.addView(named,", portrait) && tools.indexOf("main.addView(named,", portrait) < tools.indexOf("main.addView(library(),", portrait)
                    && tools.indexOf("main.addView(library(),", portrait) < tools.indexOf("main.addView(strip,", portrait),
                    "The slots first, then the tool's name and Edit in Brush Lab, the Library, and the try strip at the foot");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    /**
     * The chip the page opens on: the one last chosen; the first time, the selected slot's tool's kind (faster than All:
     * the cards most like it, its own among them, one tap from the rest).
     */
    static void opens() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 7);
        check(page.filter() == ToolsPage.Chip.WASHES, "The first time: the slot brush's kind");
        slots.use(2, rolled(WILD, 3));
        check(open(slots, 2).filter() == ToolsPage.Chip.of(slots.brush(2)), "An Anything goes brush opens on what it does: " + open(slots, 2).filter());
        for (int i = 0; i < 40; i++) slots.library.save(rolled(PENCIL, 100 + i));
        ToolsPage library = open(slots, 0);
        library.filter(ToolsPage.Chip.SAVED);
        library.layout(15, 14);
        check(library.page() == 2, "The grid opens on the page holding the slot's brush (Fineliner, after the 40 newer)");
        slots.use(3, ToolPresets.of(ModularStyle.named("Ink wash")).get(3).brush());
        ToolsPage basic = open(slots, 3);
        basic.filter(ToolsPage.Chip.WASHES);
        basic.layout(2, 14);
        int at = 0;
        while (!basic.all().get(at).brush.sameAs(slots.brush(3))) at++;
        check(basic.page() == at / 2 && basic.now(basic.cards().get(at % 2)), "A kind opens on the page holding the one the slot has");
    }
    static void remembers() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 0);
        page.filter(ToolsPage.Chip.SAVED);
        ToolsPage again = open(slots, 4);
        check(again.filter() == ToolsPage.Chip.SAVED && "SAVED".equals(prefs.get(ToolsPage.STYLE)), "The chip last chosen, on any slot");
        again.filter(ToolsPage.Chip.KITS);
        check(open(slots, 7).filter() == ToolsPage.Chip.KITS, "Kits remembered too (not the slot's kind again)");
        // A style kept by the Style menu (before 2026-10-28) opens on its kind; All (before 2026-11-03) on the slot's kind.
        prefs.put(ToolsPage.STYLE, "Charcoal");
        check(open(slots, 4).filter() == ToolsPage.Chip.DRY_MEDIA, "A kept style opens on its kind");
        prefs.put(ToolsPage.STYLE, "");
        check(open(slots, 7).filter() == ToolsPage.Chip.WASHES, "A kept All opens on the slot's kind");
    }
    /** Each chip's cards: a kind its Basic tools, every Basic tool under one; Saved the library's own but the Basic tools. */
    static void sources() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 0);
        page.layout(1000, 16);
        int basic = 0;
        for (ToolsPage.Chip c : ToolsPage.Chip.values()) {
            if (!c.kind()) continue;
            page.filter(c);
            check(!page.all().isEmpty() || c == ToolsPage.Chip.ODD_ONES, c.label + " has tools (Odd Ones may be empty, 2026-11-06: Opaque flat left it)");
            for (ToolsPage.Card card : page.all()) check(shown(card.brush) && ToolPresets.shown().stream().anyMatch(p -> p.shownName().equals(card.name) && ToolsPage.Chip.of(p) == c), c.label + ": " + card.name + " a shown tool of its kind");
            basic += page.all().size();
        }
        check(basic == ToolPresets.shown().size(), "Every shown tool under one kind (" + basic + ")");
        // More Brushes (the release pare-down, 2026-11-05): every other Basic tool, kind by kind, in the same cards.
        page.filter(ToolsPage.Chip.MORE);
        java.util.List<ToolsPage.Card> shelf = page.all();
        check(shelf.size() == ToolPresets.shelf().size(), "More Brushes holds the shelf: " + shelf.size());
        for (int i = 0; i < shelf.size(); i++) {
            ToolsPage.Card card = shelf.get(i);
            ToolPresets.Preset p = ToolPresets.find(card.brush);
            check(p != null && ToolPresets.shelf().contains(p) && !shown(card.brush) && card.line.equals(p.line), "More Brushes: " + card.name + " a Basic tool off the list, its own line");
            if (i > 0) check(ToolsPage.Chip.of(shelf.get(i - 1).brush.parts).ordinal() <= ToolsPage.Chip.of(card.brush.parts).ordinal(), "More Brushes kind by kind: " + card.name);
        }
        page.search("sumi");
        check(page.all().isEmpty(), "A search looks through the shown tools, not the shelf");
        page.search("");
        // A brush off the shelf goes in a slot as a shown one does.
        page.filter(ToolsPage.Chip.MORE);
        page.put(shelf.get(0).brush);
        check(page.current().sameAs(shelf.get(0).brush) && page.result().sameAs(shelf.get(0).brush), "A shelf card puts its brush in the slot");
        page = open(slots, 0);
        page.layout(1000, 16);
        page.filter(ToolsPage.Chip.SAVED);
        check(page.all().size() == firstSaved() && firstSaved() == 6, "Saved: the library's own but the Basic tools (a first launch's six: two of the owner's eight are Basic tools as they are)");
        // A Basic tool saved to the library is still a Basic tool: not in Saved, under its kind.
        Brush preset = ToolPresets.of(WATERCOLOR).get(1).brush();
        slots.library.save(preset);
        Brush mine = rolled(PENCIL, 9);
        slots.library.save(mine);
        page = open(slots, 0);
        page.layout(1000, 16);
        check(page.filter() == ToolsPage.Chip.SAVED && page.all().size() == firstSaved() + 1 && page.all().get(0).brush.sameAs(mine), "Saved: newest first, the saved Basic tool not among them");
        for (ToolsPage.Card c : page.all()) check(ToolPresets.find(c.brush) == null, c.name + " in Saved is no Basic tool");
        check(page.empty().equals("Nothing in Saved yet"), "An empty chip says so: " + page.empty());
    }
    /** The first launch's brushes that aren't Basic tools: six of the owner's eight (2026-10-24); the other two are Basic tools as they are. */
    static int firstSaved() {
        int n = 0;
        for (int i = 0; i < ToolSlots.COUNT; i++) if (ToolPresets.find(ToolSlotsChecks.old(i)) == null) n++;
        return n;
    }
    static void recent() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 0);
        page.filter(ToolsPage.Chip.RECENT);
        page.layout(1000, 16);
        check(page.all().size() == ToolSlots.COUNT, "Recent before anything is kept: the library, most recently used first");
        Brush wash = ToolPresets.of(WATERCOLOR).get(0).brush(), pencil = ToolPresets.of(PENCIL).get(2).brush(), roll = rolled(WILD, 4);
        slots.use(1, wash); slots.use(2, pencil); slots.use(3, roll); slots.use(4, wash);
        page = open(slots, 0);
        page.layout(1000, 16);
        List<ToolsPage.Card> recent = page.all();
        check(recent.size() == 3 + ToolSlots.COUNT && recent.get(0).brush.sameAs(wash) && recent.get(1).brush.sameAs(roll) && recent.get(2).brush.sameAs(pencil),
                "Recent: what went in a slot, latest first, each once: Basic tools and rolls too; then the library's, as before");
        check(recent.get(0).name.equals(ToolPresets.of(WATERCOLOR).get(0).shownName()) && recent.get(0).line.equals(ToolPresets.of(WATERCOLOR).get(0).line), "a Basic tool with its own description");
        for (int i = 0; i < 60; i++) slots.use(5, rolled(PENCIL, 300 + i));
        check(slots.recent().size() == ToolSlots.RECENT_MOST, "At most " + ToolSlots.RECENT_MOST + " kept");
    }
    static void paging() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 0);
        ToolsPage.Chip most = ToolsPage.Chip.PENS;
        page.layout(1000, 16);
        for (ToolsPage.Chip c : ToolsPage.Chip.values()) if (c.kind() || c == ToolsPage.Chip.MORE) { page.filter(c); int n = page.all().size(); page.filter(most); if (n > page.all().size()) most = c; }
        page.filter(most);
        int all = page.all().size(), per = 4, pages = (all + per - 1) / per;
        page.layout(per, 16);
        page.turn(-99);
        check(pages >= 3 && page.pages() == pages && page.cards().size() == per && page.page() == 0, most.label + ": " + all + " in pages of " + per + ", from the first");
        page.turn(1);
        check(page.page() == 1 && page.cards().get(0) == page.all().get(per), "Turning shows the next");
        page.turn(99);
        check(page.page() == pages - 1 && page.cards().size() == all - per * (pages - 1), "never past the last page");
        page.turn(-99);
        check(page.page() == 0, "nor before the first");
        for (int i = 0; i < 40; i++) slots.library.save(rolled(PENCIL, 100 + i));
        page.layout(30, 16);
        page.filter(ToolsPage.Chip.SAVED);
        check(page.page() == 1, "Another chip: the page holding the slot's tool (Fineliner, after 40 newer)");
        page.turn(-1);
        page.filter(ToolsPage.Chip.MARKERS);
        check(page.page() == 0, "a kind without it: the first page");
    }
    static void changes() {
        ToolSlots slots = fresh();
        String stored = prefs.get("tool_slots");
        ToolsPage page = open(slots, 4);
        page.layout(30, 16);
        check(page.result() == null, "Nothing changed: nothing goes back");
        Brush other = rolled(PENCIL, 21);
        page.put(other);
        check(page.current().sameAs(other) && page.result() != null && page.result().sameAs(other), "A brush put in the slot here goes back on Back");
        check(prefs.get("tool_slots").equals(stored) && !slots.canUndo(), "The page never writes the slot: the drawing screen does, one Undo step");
        ToolsPage.Card card = page.cards().get(0);
        page.put(card.brush);
        check(page.result().sameAs(card.brush) && page.now(card), "A card's brush is the tool now");
        page.put(slots.brush(4));
        check(page.result() == null, "The slot's own brush again: nothing goes back");
    }
    /** The slots on the page (the owner, 2026-10-19): "click a slot and then click an option from library". */
    static void six() {
        ToolSlots slots = fresh();
        String stored = prefs.get("tool_slots");
        ToolsPage page = open(slots, 1);
        page.layout(30, 16);
        check(page.slot() == 1 && page.current().sameAs(slots.brush(1)), "Opens on the slot tapped, holding its tool");
        for (Brush b : page.results()) check(b == null, "Nothing changed: nothing goes back for any slot");
        Brush wash = rolled(WATERCOLOR, 4), pencil = rolled(PENCIL, 5);
        page.put(wash);
        page.select(3);
        check(page.slot() == 3 && page.current().sameAs(slots.brush(3)) && page.current(1).sameAs(wash), "Another slot: the page changes it now; the first keeps its pick");
        page.put(pencil);
        Brush[] back = page.results();
        for (int i = 0; i < ToolSlots.COUNT; i++)
            check(i == 1 ? back[i].sameAs(wash) : i == 3 ? back[i].sameAs(pencil) : back[i] == null, "Back takes each slot's pick to it, and only those (slot " + i + ")");
        check(page.look(1).px == slots.swatches.look(ToolSlots.key(1), slots.brush(1)).px, "Each slot's tile at the one size, its pick or not");
        check(prefs.get("tool_slots").equals(stored) && !page.together(), "The page never writes a slot: the drawing screen does, an Undo step each");
    }
    static void tries() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 0);
        page.layout(12, 16);
        Brush opened = page.current();
        Brush wash = ToolPresets.of(WATERCOLOR).get(0).brush(), pencil = ToolPresets.of(PENCIL).get(1).brush();
        page.put(wash);
        page.put(pencil);
        check(page.current().sameAs(pencil), "Two picks: the second is the tool");
        page.put(opened);
        check(page.current().sameAs(opened) && page.result() == null, "The slot's own tool picked again: nothing goes back");
        // The try strip draws at what the slot will draw the tool at: the one size and grey; the slot itself is written
        // only by the drawing screen.
        ToolSwatches.Look line = slots.swatches.look(ToolSlots.key(0), opened);
        check(page.look().px == line.px, "The one size");
        String slotsBefore = prefs.get("tool_slots");
        page.put(wash);
        String stored = slotsAndSwatches();
        ToolSwatches.Look look = page.look();
        check(slotsAndSwatches().equals(stored) && prefs.get("tool_slots").equals(slotsBefore), "Trying a tool writes no slot");
        // The one grey (2026-10-24): the Watercolor draws at the grey the Fineliner did, the first launch's .216.
        check(look.darkness == line.darkness && line.darkness == .216, "The one grey");
        slots.use(0, page.result());
        ToolSwatches.Look after = slots.swatches.look(ToolSlots.key(0), slots.brush(0));
        check(look.px == after.px && look.px == line.px && look.preset == after.preset && look.darkness == after.darkness,
                "and the one size, the slot's with it (a Watercolor in the Fineliner's slot: " + look.px + " px, as the slot: " + after.px + ")");
    }
    static String slotsAndSwatches() {
        Map<String, String> some = new java.util.TreeMap<>(prefs);
        some.remove(ToolsPage.STYLE);
        return some.toString();
    }
    static void table() {
        ToolsLayout mp = new ToolsLayout(1024, 1365), ml = new ToolsLayout(1365, 1024), np = new ToolsLayout(749, 998), nl = new ToolsLayout(998, 749);
        ToolsLayout[] all = {mp, ml, np, nl};
        StringBuilder said = new StringBuilder();
        for (ToolsLayout l : all) said.append(l.width).append('x').append(l.height).append(": pad ").append(l.testWidth).append('x').append(l.testHeight)
                .append(", slots ").append(l.slotWidth).append('x').append(l.slotCard).append(", cards ").append(l.cardWidth).append('x').append(l.cardHeight)
                .append(" in ").append(l.columns).append('x').append(l.rows).append(", chips ").append(l.chipRows).append(" row(s); ");
        // The slots in one row, the try strip at the foot (2026-11-03). Before (the redesign that morning, two rows of slots): try strips 976 x 235 / 647 x 680, 725 x 161 / 528 x 423;
        // cards a page 24 / 36 / 16 / 18 (landscape loses a row to the slots spanning the page). Before that (the owner's mockup, 2026-10-28): 976 x 237 / 647 x 722, 725 x 191 / 528 x 477.
        check(mp.testWidth == TABLE[0] && mp.testHeight == TABLE[1] && ml.testWidth == TABLE[2] && ml.testHeight == TABLE[3]
                && np.testWidth == TABLE[4] && np.testHeight == TABLE[5] && nl.testWidth == TABLE[6] && nl.testHeight == TABLE[7], "Try strips: " + said);
        check(mp.chipRows == 1, "The filter row one row on the Manta upright: " + said);
        check(mp.perPage() == TABLE[8] && ml.perPage() == TABLE[9] && np.perPage() == TABLE[10] && nl.perPage() == TABLE[11], "Cards a page: " + said);
    }
    /** The try strips' test pages (Manta portrait, landscape, Nomad portrait, landscape, width and height), then the cards a page, as laid out. */
    // Nine slots (2026-11-06): the Manta's wider, so its try strips a little shorter (976 x 162 and 647 x 772 with ten).
    static final int[] TABLE = {976, 157, 647, 766, 725, 118, 528, 521, 32, 32, 24, 15};
    static void lines() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 0);
        page.filter(ToolsPage.Chip.SAVED);
        float room = LabText.width("x", LabText.CAPTION_SP, 0) * 16;
        page.layout(30, room);
        for (ToolsPage.Card c : page.cards()) {
            check(LabText.width(c.line, LabText.CAPTION_SP, 0) <= room && c.line.startsWith(c.brush.parts.style.label), c.name + ": one line of its caption, style first (" + c.line + ")");
            check(c.name.equals(c.brush.shownName()), "named as the brush");
            check(ToolsPage.name(c.brush).equals(c.name), "A slot's name alone is its card's: " + c.name);
        }
        for (ToolPresets.Preset p : ToolPresets.all()) check(ToolsPage.name(p.brush()).equals(ToolsPage.card(p.brush(), room).name), "A Basic tool's slot name is its card's: " + p.name);
        check(BrushCaptions.oneLine(java.util.Arrays.asList("Watercolor", "wash", "wet", "cold press"), String::length, 16).equals("Watercolor, wash"), "Items while they fit");
        check(BrushCaptions.oneLine(java.util.Arrays.asList("Charcoal and more", "x"), String::length, 10).equals("Charcoal and more"), "the first always");
    }
    static void layout() {
        // The bar (both pages'): the title over its job's line, within the bar's height.
        check(Math.ceil(LabText.line(TypeStyle.HEADING.sp)) + Math.ceil(LabText.line(TypeStyle.CAPTION.sp)) <= ToolsLayout.BAR, "The title and its line fit the bar");
        for (int[] s : DrawLayoutChecks.SCREENS) {
            ToolsLayout t = new ToolsLayout(s[0], s[1]);
            String at = s[0] + "x" + s[1];
            int main = s[1] - ToolsLayout.BAR;
            check(t.wide == (s[0] > s[1]) && t.spare >= 0 && t.rows >= 2, "The page fits " + at + " (cards " + t.cardWidth + "x" + t.cardHeight + ", rows " + t.rows + ")");
            check(LabText.width(ToolsLayout.TITLE, TypeStyle.HEADING.sp, 0) <= t.titleRoom() && LabText.width(ToolsLayout.JOB, TypeStyle.CAPTION.sp, 0) <= t.titleRoom(),
                    "The title and its job whole, centred, beside Back at " + at + ": " + t.titleRoom());
            LabLayout lab = new LabLayout(s[0], s[1]);
            check(LabLayout.bar(lab.compact) <= s[0] - 2 * lab.side, "Brush Lab's title whole (no job line since 2026-11-06), centred between Back and Undo, at " + at);
            int below = main - t.slotsRow(), strip = ToolsLayout.TRY_HEAD + t.testHeight;
            int library = t.libraryFixed() + t.grid(t.rows) + t.spare;
            if (t.wide) {
                check(ToolsLayout.NAME_ROW + strip + t.side == below && library + t.side == below, "Each column adds up to the height under the slots at " + at);
                check(2 * t.side + t.top + t.apart + t.sections == s[0], "The two columns and the paper add up to the width at " + at);
            } else {
                check(ToolsLayout.NAME_ROW + t.apart + library + t.gap + strip + t.side == below && t.top == s[0] - 2 * t.side && t.sections == t.top, "Everything stacked adds up at " + at);
                check(t.testHeight >= t.testLeast() && t.testHeight < t.testLeast() + t.cardHeight + ToolsLayout.ROW_GAP, "The try strip at least its least, the rows the rest, at " + at + ": " + t.testHeight);
            }
            check(t.testWidth == t.top && t.testWidth >= 330, "The try strip " + t.testWidth + " wide at " + at);
            // The name row: Edit in Brush Lab whole, a short name and its line beside it; the strip's head: what it is for and Clear.
            check(t.nameRoom() >= LabText.width("Steady line · even and crisp", TypeStyle.CAPTION.sp, 0), "The name row holds a tool's name and line beside Edit in Brush Lab at " + at + ": " + t.nameRoom());
            check(t.nameRoom(true) == t.nameRoom() - (int) Math.ceil(quiet(DrawLayout.RESET)) && t.nameRoom(false) == t.nameRoom()
                    && t.nameRoom(true) >= LabText.width("Steady line", TypeStyle.CAPTION.sp, 0), "Reset to Default beside Edit in Brush Lab leaves a tool's name its room at " + at + ": " + t.nameRoom(true));
            check(LabText.width(ToolsLayout.TRY + "Steady line", TypeStyle.CAPTION.sp, 0) + quiet("Clear") <= t.testWidth, "The try strip's head whole at " + at);
            // The Library: four across at most (the mockup), three at least, a card at least CARD_LEAST wide.
            check(t.columns >= 3 && t.columns <= ToolsLayout.COLUMNS && t.cardWidth >= ToolsLayout.CARD_LEAST && t.columns * t.cardWidth <= t.sections, "The cards across at " + at);
            // The slots in one row across the page (the owner, 2026-11-03: "all on one line"): every one the bar can show
            // (ToolSlots.COUNT at most; a kit raises the bar's count no higher), each drawing still big enough to know.
            check(t.slotCount == (t.compact ? 8 : ToolSlots.COUNT) && t.slotCount <= ToolSlots.COUNT
                    && t.slotCount * t.slotWidth + (t.slotCount - 1) * t.slotGap <= s[0] - 2 * t.side, "The side column's slots, one row of " + t.slotCount + " across, at " + at);
            check(t.slotCard == t.slotTile() + ToolsLayout.DOT_ROW && t.slotTile() == t.slotArtHeight() + 2 * ToolsLayout.SLOT_PAD && t.slotArt() == t.slotWidth - 2 * ToolsLayout.SLOT_PAD
                    && t.cardHeight == t.cardArtHeight() + ToolsLayout.NAME_GAP + ToolsLayout.nameLine() + DrawLayout.MARK,
                    "A slot: its art in its tile's padding, the dot's row; a card: art, a line for the name, the underline's room at " + at);
            check(t.slotArt() >= 70 && t.cardArt() + ToolsLayout.NAME_END <= t.cardWidth && t.slotArtHeight() >= 30 && t.cardArtHeight() >= 30, "The art inside its card at " + at + ": a slot's " + t.slotArt() + "x" + t.slotArtHeight());
            // Search and the pages share a row; the chips wrap within the Library's width, each whole.
            check(ToolsLayout.buttonWidth(ToolsLayout.SEARCH) + ToolsLayout.pagerWidth() <= t.sections, "Search and the pages across at " + at);
            for (String label : ToolsLayout.chipLabels()) check(ToolsLayout.chipWidth(label, t.chipPad()) <= t.sections, label + "'s chip fits at " + at);
            check(t.chipRows >= 1 && t.chipRows <= 3, "The chips in " + t.chipRows + " row(s) at " + at);
        }
        check(new ToolsLayout(1024, 1365).columns == 4 && new ToolsLayout(749, 998).columns == 4 && new ToolsLayout(1365, 1024).columns == 4 && new ToolsLayout(998, 749).columns == 3,
                "4 columns, 3 on the Nomad in landscape beside the try strip");
        // Every drawing cut at its handle end and scaled to the box's width: they all end one length, whatever their canvas.
        for (String[] tool : DrawLayout.PICKER) {
            if (tool[1] == null) continue;
            float[] size = drawingSize(tool[1]), art = ToolsLayout.art(size[0], size[1], 160, 67);
            check(Math.abs(art[0] + art[2] - 160) < 1e-3 && Math.abs(-art[0] - DrawLayout.TIP_TRIM * art[2]) < 1e-3 && Math.abs(2 * art[1] + art[3] - 67) < 1e-3, tool[0] + ": trimmed, the box's width, centred");
        }
        float[] wide = ToolsLayout.art(352, 116, 160, 67), narrow = ToolsLayout.art(240, 116, 160, 67);
        check(Math.abs(wide[0] + wide[2] - (narrow[0] + narrow[2])) < 1e-3 && wide[3] < narrow[3], "A wider canvas ends at the same length, drawn smaller");
    }
    /**
     * The search (the owner, 2026-10-28: "it'd be good to have a search function for the library"): every word in the
     * name, the style, the kind or the line, any case, any order; the Basic tools, Recent and Saved at once, each brush once.
     */
    static void search() {
        ToolPresets.Preset pencil = null;
        for (ToolPresets.Preset p : ToolPresets.all()) if (pencil == null && p.brush().parts.style == PENCIL) pencil = p;
        check(pencil != null, "A Basic pencil");
        ToolsPage.Card card = new ToolsPage.Card(pencil.brush(), pencil.name, pencil.line);
        String[] lineWords = pencil.line.split("[\\s,·]+");
        String word = lineWords[lineWords.length - 1];
        for (String q : new String[]{pencil.name, pencil.name.toUpperCase(), "pencil", "PENCIL", "dry media", "Media DRY", "dry " + pencil.name.toLowerCase(), word, " " + word + "  pencil ", ""})
            check(ToolsPage.matches(card, q), "\"" + q + "\" finds " + pencil.name + " (" + pencil.line + ")");
        for (String q : new String[]{"fineliner", "pencil fineliner", "washes", "zzz"}) check(!ToolsPage.matches(card, q), "\"" + q + "\" doesn't find " + pencil.name);
        ToolsPage.Card wash = new ToolsPage.Card(rolled(WATERCOLOR, 2), "Pond", "x");
        check(ToolsPage.matches(wash, "watercolour pond") && ToolsPage.matches(wash, "Watercolor") && ToolsPage.matches(wash, "washes"), "The style's name and its label, the kind's");

        ToolSlots slots = fresh();
        Brush mine = rolled(PENCIL, 77);
        slots.library.save(mine);
        ToolsPage page = open(slots, 0);
        page.filter(ToolsPage.Chip.KITS);
        page.layout(8, 140);
        int kits = page.all().size();
        check(!page.searching() && page.search().isEmpty() && kits > 0, "No search at first");
        page.search("  " + mine.name().toLowerCase() + " ");
        check(page.searching() && page.filter() == ToolsPage.Chip.KITS && page.page() == 0, "Searching from the first page, the chip kept");
        boolean found = false;
        for (ToolsPage.Card c : page.all()) found |= c.brush.sameAs(mine);
        check(found, "A saved brush found from Kits");
        page.search("pencil");
        List<ToolsPage.Card> finds = page.all();
        boolean basic = false;
        for (ToolsPage.Card c : finds) { check(c.brush.parts.style == PENCIL || ToolsPage.matches(c, "pencil"), c.name + " matches"); basic |= ToolPresets.find(c.brush) != null; }
        check(basic && finds.size() >= 2, "The Basic tools and Saved together: " + finds.size());
        for (int i = 0; i < finds.size(); i++) for (int k = i + 1; k < finds.size(); k++) check(!finds.get(i).brush.sameAs(finds.get(k).brush), "Each brush once");
        page.turn(1);
        page.search("pencil b");
        check(page.page() == 0, "Back to the first page as the words change");
        page.search("zzzz");
        check(page.all().isEmpty() && page.empty().equals("Nothing found"), "Nothing found");
        page.search("");
        check(!page.searching() && page.all().size() == kits && page.all().get(0).kit != null, "An empty search: the chip's cards again");
    }
    /** The owner's drawing {@code name}'s size, px: a vector's (res/drawable) or a PNG's (res/drawable-nodpi). */
    static float[] drawingSize(String name) {
        try {
            java.io.File vector = new java.io.File("app/src/main/res/drawable/" + name + ".xml");
            if (vector.isFile()) {
                String xml = new String(java.nio.file.Files.readAllBytes(vector.toPath()));
                java.util.regex.Matcher w = java.util.regex.Pattern.compile("android:width=\"(\\d+)px").matcher(xml), h = java.util.regex.Pattern.compile("android:height=\"(\\d+)px").matcher(xml);
                check(w.find() && h.find(), name + ": its size in px");
                return new float[]{Float.parseFloat(w.group(1)), Float.parseFloat(h.group(1))};
            }
            java.awt.image.BufferedImage png = javax.imageio.ImageIO.read(new java.io.File("app/src/main/res/drawable-nodpi/" + name + ".png"));
            return new float[]{png.getWidth(), png.getHeight()};
        } catch (java.io.IOException e) { throw new AssertionError(name + ": " + e); }
    }
    /** A plain button's word: 13 sp and its 8 dp either side ({@link LabButton#quiet}). */
    static float quiet(String words) { return LabText.width(words, TypeStyle.CAPTION.sp, 0) + 16; }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /** Where each slot started (docs/archive/design-pass-2026-10-23.md, "Getting back to where you started"): the dot, carried through Brush Lab. */
    static void start() {
        ToolSlots slots = fresh();
        ToolsPage page = open(slots, 4);
        page.layout(30, 16);
        Brush watercolour = slots.brush(4);
        check(!page.changed(4), "Nothing changed: no dot");
        page.put(ToolPresets.of(PENCIL).get(0).brush());
        page.select(2);
        page.put(rolled(PENCIL, 7));
        page.select(4);
        check(page.changed(4) && page.changed(2) && !page.changed(1), "Changed slots are marked");
        // Tools page → Brush Lab → Tools page: the start is the one from before either.
        String[] start = page.start.write();
        slots.use(4, page.current());
        ToolsPage again = new ToolsPage(slots, store(), 4, start);
        check(again.changed(4) && again.start.brushes[4].sameAs(watercolour) && again.result() == null, "The start carried through Brush Lab");
        again.put(watercolour);
        check(!again.changed(4) && again.result().sameAs(watercolour), "The first brush picked again: no dot, and it goes back for the drawing screen to take");
    }
    /**
     * Nothing unsaved is lost when a slot is replaced: kept in Recent (docs/archive/design-pass-2026-10-23.md), so a
     * change needs no Undo here: a Basic tool is under its kind, a saved one in Saved, any other in Recent.
     */
    static void keeps() {
        ToolSlots slots = fresh();
        Brush mine = rolled(PENCIL, 31);
        slots.use(5, mine);
        // Taken out of Recent, as an unsaved brush made in Brush Lab is until the slot lets it go.
        prefs.remove(ToolSlots.RECENT);
        ToolsPage page = open(slots, 5);
        page.layout(30, 16);
        page.put(ToolPresets.of(PENCIL).get(0).brush());
        check(("Kept " + mine.name() + " in Recent").equals(page.note()) && page.note() == null, "A pick lets an unsaved brush go: kept in Recent, said once");
        check(slots.recent().get(0).sameAs(mine), "first in Recent");
        page.filter(ToolsPage.Chip.RECENT);
        check(page.all().get(0).brush.sameAs(mine), "and there in Recent at once, to pick again");
        page.put(ToolPresets.of(PENCIL).get(1).brush());
        check(page.note() == null, "A Basic tool let go isn't kept: it's never lost");
        page.put(mine);
        page.put(ToolPresets.of(PENCIL).get(0).brush());
        check(page.note() == null, "A brush in Recent already isn't kept twice");
        page.select(1);
        page.put(rolled(WATERCOLOR, 2));
        check(page.note() == null, "A saved brush (a first launch's Brush pen) isn't kept: it's in the library");
        // A kit's load too: every unsaved brush it lets go is kept.
        ToolSlots kitSlots = fresh();
        Brush a = rolled(PENCIL, 61), b = rolled(PENCIL, 62);
        kitSlots.use(0, a); kitSlots.use(1, b);
        prefs.remove(ToolSlots.RECENT);
        ToolsPage kitPage = open(kitSlots, 0);
        kitPage.loadKit(ToolKits.all().get(0));
        check("Kept 2 brushes in Recent".equals(kitPage.note()) && kitSlots.recent().size() >= 2, "A kit lets two unsaved brushes go: both kept in Recent");
        for (int i = 0; i < 40; i++) { slots.keep(rolled(PENCIL, 500 + i)); }
        check(slots.recent().size() == ToolSlots.RECENT_MOST && ToolSlots.RECENT_MOST == 30, "Recent keeps the latest 30, the oldest dropped");
        Set<String> seen = new HashSet<>();
        for (Brush r : slots.recent()) check(seen.add(r.identity()), "Recent holds each brush once");
    }
    /**
     * Reset to Default on the Tools page (the owner, 2026-11-03: "reset to default on the toolbar page would be good too"):
     * shown exactly when the tool picker shows it (an edited Basic tool), a tap putting the Basic tool back as a card does.
     */
    static void resets() {
        ToolSlots slots = fresh();
        ToolPresets.Preset wax = ToolKits.preset("Wax crayon");
        ModularBrush changed = wax.brush().parts.copy(); changed.part(BrushPart.TIP).set(PartDial.WEIGHT, 1.3);
        Brush edited = Brush.of(changed);
        slots.library.save(rolled(PENCIL, 31));
        Brush mine = slots.library.entries().get(0).brush;
        slots.use(2, edited); slots.use(3, wax.brush()); slots.use(4, mine);
        prefs.remove(ToolSlots.RECENT);
        ToolsPage page = open(slots, 2);
        page.layout(30, 16);
        for (int i : new int[]{2, 3, 4}) {
            page.select(i);
            check((page.edited() == null) == (new DrawLayout.Picker(page.current(), false).reset == null), "Reset to Default on the page as on the tool picker, slot " + i);
        }
        page.select(3);
        check(page.edited() == null, "No reset for an unchanged Basic tool");
        page.select(4);
        check(page.edited() == null, "No reset for a library brush");
        page.select(2);
        check(page.edited() == wax, "Reset to Default for a Basic tool changed in Brush Lab");
        page.put(page.edited().brush());
        check(page.current().sameAs(wax.brush()) && page.result().sameAs(wax.brush()) && page.edited() == null, "A tap: Wax crayon back as the slot's tool, the reset gone");
        check(("Kept " + edited.name() + " in Recent").equals(page.note()) && slots.recent().get(0).sameAs(edited), "The edited brush let go is kept in Recent, said once");
    }
    /** A saved card's long press deletes it (Brush Lab's library went, 2026-10-23: the Tools page keeps its delete). */
    static void deletes() {
        ToolSlots slots = fresh();
        Brush mine = rolled(PENCIL, 41);
        slots.library.save(mine);
        ToolsPage page = open(slots, 0);
        page.filter(ToolsPage.Chip.SAVED);
        page.layout(30, 16);
        ToolsPage.Card card = page.cards().get(0);
        check(card.brush.sameAs(mine) && page.deletable(card), "A saved brush's card can be deleted");
        check(page.delete(card) && !slots.library.contains(mine) && !page.cards().get(0).brush.sameAs(mine), "deleted, the grid without it");
        page.filter(ToolsPage.Chip.PENS);
        check(!page.deletable(page.cards().get(0)) && !page.delete(page.cards().get(0)), "A Basic tool can't be");
    }
}
