package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tool kits (docs/archive/superpowers/specs/2026-10-22-tool-kits-design.md): the file, loading and Undo, the notebook's rules, the sheet's layout, marbling's notes. */
public final class ToolKitsChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        data(); parsing(); tab(); loads(); bar(); fixed(); drawingUndo(); said(); notes(); layout();
        System.out.println("PASS: tool kits: " + ToolKits.all().size() + " kits from " + ToolKits.FILE + " (Default Tools, then Dollar Store, Sketchbook, Watercolour, Paint and Effects and Print Shop in the tray's order), every brush a Basic tool, at most nine (the slots), words in the text style; a kit fills the first slots, the first selected, and one Undo on the drawing screen puts every slot back; a bar too small shows more (Lock Settings cut); a fixed toolbar takes nothing; a slot picked says its step; clear water with nothing floating and a brush that prints the rings get their notes; the sheet and Load Kit fit both tablets in either turn");
    }

    static ToolKits.Kit kit(String name) {
        for (ToolKits.Kit k : ToolKits.all()) if (k.name.equals(name)) return k;
        for (ToolKits.Kit k : CUT) if (k.name.equals(name)) return k;
        throw new AssertionError("No kit " + name);
    }
    /** Kits cut in the release pare-down (2026-11-05), kept here as they were: the checks' kits of three and with an eraser step. */
    static final List<ToolKits.Kit> CUT = ToolKits.parse(Arrays.asList(
            "Kit: Marbling", "Intro: Drop ink and clear water in turn to grow rings, then comb them before they print.",
            "Sumi: Drop ink", "Clear water: Drop water", "Comb: Comb rings",
            "Kit: Sketch and Ink", "Intro: Sketch in pencil, ink over it, then erase the pencil and the ink stays.",
            "Sketch pencil: Sketch", "Steady line: Ink over", "Eraser Layer 3: Erase pencil",
            "Kit: Watercolour with Masking", "Intro: Mask the whites, wash over them, blot to lift paint, then rub the mask off.",
            "Masking fluid: Mask whites", "Soft wash: Wash over", "Water: Press to lift", "Eraser Everything: Rub off mask"));

    static void data() {
        List<ToolKits.Kit> kits = ToolKits.all();
        // The release pare-down (2026-11-05): Mixed Media dropped 2026-11-06 (the owner: "just make a well
        // rounded group of tools that are different from each other").
        check(kits.size() == 6 && kits.get(0).name.equals("Default Tools") && kits.get(1).name.equals("Dollar Store") && kits.get(2).name.equals("Sketchbook")
                && kits.get(3).name.equals("Watercolour") && kits.get(4).name.equals("Paint and Effects") && kits.get(5).name.equals("Print Shop"), "Default Tools, Dollar Store, Sketchbook, Watercolour, Paint and Effects, Print Shop: " + kits.size());
        // Every kit but Default Tools (the owner's bar, ToolSlots.FIRST) loads in the tray's order (the owner, 2026-11-06).
        for (ToolKits.Kit k : kits) {
            if (k.name.equals(ToolKits.KEEPS_ORDER)) continue;
            List<ToolPresets.Preset> got = new java.util.ArrayList<>();
            for (ToolKits.Role r : k.brushes) got.add(ToolPresets.named(r.preset));
            List<ToolPresets.Preset> sorted = new java.util.ArrayList<>(got);
            sorted.sort(DrawLayout::compareInTray);
            check(got.size() >= 1 && !got.contains(null) && got.equals(sorted), k.name + ": brushes in the tray's order: " + k.brushes.size());
        }
        List<String> tray = new java.util.ArrayList<>();
        for (List<ToolPresets.Preset> group : DrawLayout.pickerGroups()) for (ToolPresets.Preset p : group) tray.add(p.name);
        List<String> store = new java.util.ArrayList<>();
        for (ToolKits.Role r : kit("Dollar Store").brushes) store.add(r.preset);
        List<String> byTray = new java.util.ArrayList<>(store);
        byTray.sort(java.util.Comparator.comparingInt(tray::indexOf));
        check(store.size() == 9 && tray.containsAll(store) && store.equals(byTray), "Dollar Store: nine shown tools in the picker's order: " + store);
        Brush[] defaults = kit("Default Tools").tools();
        check(defaults.length == ToolSlots.FIRST.length, "Default Tools holds the bar's first-launch brushes, all of them: " + defaults.length);
        for (int i = 0; i < defaults.length; i++) check(defaults[i] != null && defaults[i].sameAs(ToolSlots.first(i)), "Default Tools' slot " + (i + 1) + " is the first launch's " + ToolSlots.FIRST[i][1]);
        Set<String> names = new HashSet<>();
        for (ToolKits.Kit k : kits) {
            check(names.add(k.name), "Kits have names of their own: " + k.name);
            check(k.size() >= 1 && k.size() <= ToolSlots.COUNT, k.name + " fills one to nine slots: " + k.size());
            for (Brush b : k.tools()) check(b != null, k.name + ": every brush is a Basic tool on the Tools page: " + k.brushes);
            for (ToolKits.Role r : k.brushes) check(ToolKits.preset(r.preset) == null || ToolPresets.find(ToolKits.preset(r.preset).brush()) != null, k.name + ": " + r.preset + " is offered, not retired");
            // This Brush's header has room for Cut Stamp or Load Kit, not both.
            for (Brush b : k.tools()) check(!b.parts.stamps(), k.name + ": no Stamp in a kit");
            VoiceChecks.readable(k.intro, k.name + "'s intro");
            check(ModularWords.sentences(k.intro).size() == 1 && k.intro.length() <= 100, k.name + "'s intro is one sentence: " + k.intro);
            check(!k.name.contains(";") && Character.isUpperCase(k.name.charAt(0)), k.name + ": a label");
            for (int i = 0; i < k.steps(); i++) {
                String said = k.said(i), job = said.substring(said.indexOf(' ') + 1);
                check(said.startsWith((i + 1) + " "), k.name + "'s steps are numbered in order: " + said);
                // A bar's words: three at most (docs/TEXT-STYLE.md rule 9), sentence case, no symbols.
                check(job.split(" ").length <= 3 && Character.isUpperCase(job.charAt(0)) && job.substring(1).equals(job.substring(1).toLowerCase()) && !job.matches(".*[;:→=].*"),
                        k.name + "'s step is a bar's words in sentence case: " + said);
            }
        }
        check(ToolKits.of(ToolKits.preset("Hatch pen").brush()) == kit("Sketchbook"), "A kit's brush offers its kit");
        check(ToolKits.of(ToolKits.preset("Gel pen").brush()) == null, "A brush in no kit offers none");
        ModularBrush renamed = ToolKits.preset("Hatch pen").brush().parts.copy();
        renamed.name = "My hatch pen";
        check(ToolKits.of(Brush.of(renamed)) == null, "A Basic tool changed is no longer the kit's");
    }

    static void parsing() {
        List<ToolKits.Kit> some = ToolKits.parse(Arrays.asList("# Kit: Notes", "stray", "Kit: A", "Intro: One.", "Sumi: Drop ink", "Eraser Layer 1: Erase pen",
                "Kit: Empty", "Intro: Nothing in it.", "Kit: B", "Comb: Comb rings", "Kit: C", "Intro: Two.", "Eraser Nonsense: x", "Comb: Comb"));
        check(some.size() == 2 && some.get(0).name.equals("A") && some.get(1).name.equals("C"), "Kits with an intro and a brush, notes and strays left out: " + some.size());
        check(some.get(0).eraser != null && EraseKind.layerOf(EraseKind.TARGETS[some.get(0).eraser.eraser]) == 1 && some.get(0).steps() == 2 && some.get(0).said(1).equals("2 Erase pen")
                && some.get(0).tool(1).equals("Eraser, Layer 1"), "An eraser step: its layer, last");
        check(some.get(1).eraser == null, "An eraser kind not known is left out");
    }

    /** The filter row's Kits, last: a card a kit, in the file's order, its first brush's stroke, "3 brushes". */
    static void tab() {
        ToolsPage page = ToolsPageChecks.open(ToolsPageChecks.fresh(), 0);
        page.layout(1000, 16);
        page.filter(ToolsPage.Chip.KITS);
        List<ToolsPage.Card> cards = page.all();
        ToolsPage.Chip[] chips = ToolsLayout.rowChips();
        check(chips[chips.length - 1] == ToolsPage.Chip.KITS && cards.size() == ToolKits.all().size() && page.pages() == 1, "Kits, the row's last chip: a card a kit: " + cards.size());
        for (int i = 0; i < cards.size(); i++) {
            ToolKits.Kit k = ToolKits.all().get(i);
            check(cards.get(i).kit == k && cards.get(i).name.equals(k.name) && cards.get(i).brush.sameAs(k.tools()[0]) && cards.get(i).line.equals(k.size() + " brushes") && !page.now(cards.get(i)),
                    "A kit's card: its name, its first brush, how many it puts on the bar: " + cards.get(i).line);
        }
    }

    static void loads() {
        ToolSlots slots = ToolsPageChecks.fresh();
        ToolsPage page = ToolsPageChecks.open(slots, 5);
        Brush[] opened = new Brush[ToolSlots.COUNT];
        for (int i = 0; i < opened.length; i++) opened[i] = page.current(i);
        ToolKits.Kit marbling = kit("Marbling");
        page.put(ToolKits.preset("Comb").brush());
        check(!page.together(), "A pick alone: an Undo step a slot on the drawing screen");
        check(page.loadKit(marbling), "The kit loads");
        Brush[] tools = marbling.tools();
        for (int i = 0; i < tools.length; i++) check(page.current(i).sameAs(tools[i]), "The kit fills the first slots in order: slot " + i);
        for (int i = tools.length; i < ToolSlots.COUNT; i++) check(page.current(i).sameAs(i == 5 ? ToolKits.preset("Comb").brush() : opened[i]), "The other slots keep theirs: slot " + i);
        check(page.slot() == 0 && page.together() && page.bar() == ToolSlots.COUNT && page.barResult() == -1 && page.eraser() == -1, "Its first slot selected, one Undo step on the drawing screen, the bar as it was");
        check(!page.loadKit(marbling), "Loaded again: nothing changes");
        ToolKits.Kit sketch = kit("Sketch and Ink");
        page = ToolsPageChecks.open(ToolsPageChecks.fresh(), 5);
        page.loadKit(sketch);
        check(EraseKind.layerOf(EraseKind.TARGETS[page.eraser()]) == 3, "A kit's eraser step sets the eraser to its layer");
        Brush[] results = page.results();
        check(results[0] != null && results[1] != null && results[2] == null, "Back takes the kit's slots their brushes, the rest unchanged");
    }

    static void bar() {
        ToolKits.Kit marbling = kit("Marbling");
        ToolsPage open = ToolsPageChecks.open(ToolsPageChecks.fresh(), 0);
        open.bar(2);
        check(open.raises(marbling), "A notebook's bar of two: the kit shows its third slot");
        open.loadKit(marbling);
        check(open.bar() == 3 && open.barResult() == 3, "Brushes on Bar goes to three");
        // Lock Settings is cut (2026-10-24): no bar refuses a kit, it raises the count.
        ToolsPage three = ToolsPageChecks.open(ToolsPageChecks.fresh(), 0);
        three.bar(3);
        check(!three.raises(marbling) && three.loadKit(marbling), "A bar of three takes it as it is");
    }

    /** A fixed toolbar (Each Page's): the page says why and loads nothing; the slots take nothing either. */
    static void fixed() {
        NotebookOptions each = NotebookOptions.DEFAULT.withTools(NotebookOptions.Tools.EACH_PAGE, ToolsPageChecks.fresh().toolbar().lines());
        check(NotebookRules.toolsFixed(each) != null, "Each Page: the Tools page's note, as for a single pick: " + NotebookRules.toolsFixed(each));
        ToolSlots slots = ToolsPageChecks.fresh();
        slots.frozen = true;
        Brush before = slots.brush(0);
        boolean[] used = slots.useAll(kit("Marbling").tools());
        check(!used[0] && !used[1] && !used[2] && slots.brush(0).sameAs(before) && !slots.canUndo(), "A frozen toolbar takes no kit");
    }

    static void drawingUndo() {
        ToolSlots slots = ToolsPageChecks.fresh();
        slots.use(6, ToolKits.preset("Gel pen").brush());
        Brush[] was = new Brush[ToolSlots.COUNT];
        for (int i = 0; i < was.length; i++) was[i] = slots.brush(i);
        Brush[] kit = Arrays.copyOf(kit("Watercolour with Masking").tools(), ToolSlots.COUNT);
        boolean[] used = slots.useAll(kit);
        check(used[0] && used[1] && used[2] && !used[3], "The kit's slots change on the drawing screen");
        check(slots.undo() == 0 && slots.undone().equals(Arrays.asList(0, 1, 2)), "One Undo there takes back the whole kit: " + slots.undone());
        for (int i = 0; i < ToolSlots.COUNT; i++) check(slots.brush(i).sameAs(was[i]), "Every slot as before: " + i);
        check(slots.undo() == 6 && slots.undone().equals(Arrays.asList(6)) && !slots.canUndo(), "Then the pick before it, alone");
        // Past the Undo limit the kit is still one step.
        ToolSlots full = ToolsPageChecks.fresh();
        for (int i = 0; i < ToolSlots.UNDOS + 4; i++) full.use(7, i % 2 == 0 ? ToolKits.preset("Gel pen").brush() : ToolKits.preset("Ballpoint").brush());
        full.useAll(kit);
        check(full.undo() == 0 && full.undone().size() == 3, "A kit after many picks is one step too");
    }

    static void said() {
        ToolSlots slots = ToolsPageChecks.fresh();
        slots.useAll(Arrays.copyOf(kit("Sketchbook").tools(), ToolSlots.COUNT));
        Brush[] bar = new Brush[ToolSlots.COUNT];
        for (int i = 0; i < bar.length; i++) bar[i] = slots.brush(i);
        ToolKits.Kit mixed = kit("Sketchbook");
        check(mixed.said(0).equals(ToolKits.said(bar, 0)) && mixed.said(2).equals(ToolKits.said(bar, 2)), "A slot of a loaded kit says its step: " + ToolKits.said(bar, 2));
        check(ToolKits.said(bar, 8) == null, "A slot after the kit says its name");
        bar[1] = ToolKits.preset("Gel pen").brush();
        check(ToolKits.said(bar, 0) == null, "A kit no longer whole says names");
    }

    static void notes() {
        ModularBrush sumi = ToolKits.preset("Sumi").brush().parts, water = ToolKits.preset("Clear water").brush().parts, comb = ToolKits.preset("Comb").brush().parts;
        ModularBrush pen = ToolKits.preset("Gel pen").brush().parts, waterBrush = ToolKits.preset("Water").brush().parts;
        check(ToolKits.NOTHING_FLOATING.equals(ToolKits.floatNote(water, water, false)) && ToolKits.NOTHING_FLOATING.equals(ToolKits.floatNote(comb, comb, false)), "Clear water or the comb with nothing floating: its note");
        check(ToolKits.floatNote(sumi, sumi, false) == null && ToolKits.floatNote(water, water, true) == null && ToolKits.floatNote(comb, comb, true) == null, "Sumi starts the bath; water and comb on it say nothing");
        check(ToolKits.PRINTED.equals(ToolKits.floatNote(pen, pen, true)) && ToolKits.PRINTED.equals(ToolKits.floatNote(null, null, true)), "Another brush, or the eraser, prints the rings: its note");
        check(ToolKits.floatNote(waterBrush, ClearWater.afloat(waterBrush, true), true) == null, "Clear Water's smudge works on the water: no note");
        check(ToolKits.floatNote(pen, pen, false) == null, "Nothing floating, another brush: nothing to say");
        for (String n : new String[]{ToolKits.NOTHING_FLOATING, ToolKits.PRINTED}) check(n.split(" ").length <= 3 && n.length() <= 30, "A note in about three words: " + n);
    }

    static void layout() {
        for (int[] s : DrawLayoutChecks.SCREENS) {
            ToolsLayout t = new ToolsLayout(s[0], s[1]);
            String at = s[0] + "x" + s[1];
            check(t.sheetWidth() <= s[0] - 2 * t.side, "The kit sheet inside the page's sides at " + at);
            for (ToolKits.Kit k : ToolKits.all()) {
                check(t.sheetHeight(k) <= s[1] - 2 * t.gap, k.name + "'s sheet fits the height at " + at + ": " + t.sheetHeight(k));
                for (int i = 0; i < k.steps(); i++) check(ToolsLayout.stepFits(k, i, t.sheetRoom()), k.name + ": step " + k.said(i) + " and " + k.tool(i) + " share a row at " + at);
                String where = ToolsLayout.kitWhere(k.size(), true);
                check(LabText.width(where, ToolsLayout.WORDS_SP, 0) <= t.sheetRoom(), "Where it goes, one line at " + at + ": " + where);
            }
            check(ToolsLayout.buttonWidth(ToolKits.CANCEL) + 10 + ToolsLayout.buttonWidth(ToolKits.LOAD) <= t.sheetRoom(), "Cancel and Load Kit side by side at " + at);
        }
        check(ToolsLayout.kitWhere(3, false).equals("Fills the first 3 slots") && ToolsLayout.kitWhere(1, false).equals("Fills the first slot")
                && ToolsLayout.kitWhere(3, true).equals("Fills and shows the first 3 slots"), "Where it goes, in words");
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
