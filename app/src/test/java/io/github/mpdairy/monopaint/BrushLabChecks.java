package io.github.mpdairy.monopaint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class BrushLabChecks {
    static Map<String, String> prefs = new HashMap<>();
    static BrushLibrary.Store store() {
        return new BrushLibrary.Store() {
            public String get(String key) { return prefs.get(key); }
            public void put(String key, String value) { if (value == null) prefs.remove(key); else prefs.put(key, value); }
        };
    }
    static ToolSlots slots() { prefs = new HashMap<>(); return ToolSlotsChecks.old(store(), new Random(1)); }
    /** A variation's name is its parent's kin: the same produce or family name. */
    static boolean kin(String parent, String child) {
        if (parent.contains(" ")) return child.endsWith(parent.substring(parent.indexOf(' ')));
        // A name led by a trait's word keeps the word under a new produce (BrushNames.kin).
        String first = parent.substring(0, parent.lastIndexOf('-') + 1);
        if (BrushNames.traitWords().contains(first.replace("-", ""))) return child.startsWith(first);
        return child.endsWith(parent.substring(parent.lastIndexOf('-')));
    }

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        opens(); randomize(); undo(); resets(); save(); load(); library(); result(); startReachable(); keepsInRecent(); freshStays(); swapTo();
        openSave(); saveAsNew(); suggests(); update(); newBrush(); uniqueNames();
        System.out.println("PASS: a part's choices open switch to another part's module tapped, in one step, and close on their own module or elsewhere; the lab opens on its slot's brush; New ▾, Open, and Save with its As New: Open takes a library brush, Save writes over that entry in place (its id, its place, its size and grey moved to the new code, its name kept unless one was given), with As New adds a copy under the suggested name, the entry left alone, a slot's own brush or a New one is added whether or not; an edited brush is marked and offered a name that says its part; Randomize and styles keep what's locked, and any style can be picked from any brush; Undo takes back the lab's own changes, latest first; Save, Load (M codes, and old I/P codes as six parts) and delete keep the library; the library lists every brush newest first; Back returns the brush; with no Back to, the slot's first brush is Undo's and in Recent, the start carried from the Tools page; an unsaved brush Randomize, a style or Load lets go is kept in Recent, said once; a New Brush stays in its slot unsaved; a suggested name passes by the library's names for the generator's next, so Save with As New adds no second brush of a name; the lab's header has no label and its readout shows the picker's size and grey in the brush's nib");
    }
    static void swapTo() {
        check(BrushLab.swapTo(BrushPart.MEDIUM, BrushPart.PAPER) == BrushPart.PAPER, "Medium's choices open, Paper's module tapped: Paper's open");
        check(BrushLab.swapTo(BrushPart.MEDIUM, BrushPart.MEDIUM) == null && BrushLab.swapTo(BrushPart.MEDIUM, null) == null, "Medium's own module, or anywhere else, closes them");
    }
    static void opens() {
        ToolSlots slots = slots();
        BrushLab lab = new BrushLab(slots, 2, new Random(2));
        check(lab.brush().sameAs(slots.brush(2)) && lab.parts().name.equals("Dither pencil"), "The lab opens on its slot's brush");
        check(!lab.canUndo() && !lab.edited() && lab.suggestion() == null && lab.savedEntry() == null, "nothing to undo, not edited, no entry to write over");
        check(lab.status.equals("Dither pencil · Print"), "The status names it, its style once: " + lab.status);
        check(lab.code().equals(ModularCode.encode(lab.parts())) && lab.code().startsWith("dither-pencil.M"), "Its share code");
    }
    /** Open a library brush, edit it, Save: that entry written over in place, its name kept, its own size and grey moved to the new code. */
    static void openSave() {
        ToolSlots slots = slots();
        Brush marker = Brush.of(ModularStyle.named("Marker").roll(new Random(42))), oils = Brush.of(ModularStyle.named("Oils").roll(new Random(43)));
        slots.library.save(marker);
        slots.library.save(oils);
        slots.swatches.setLook(marker, 9, .5);
        BrushLab lab = new BrushLab(slots, 2, new Random(12));
        BrushLibrary.Entry entry = slots.library.find(marker);
        List<Long> ids = ids(slots);
        lab.open(entry);
        check(lab.brush().sameAs(marker) && lab.savedEntry().id == entry.id && !lab.edited() && lab.canUndo() && lab.status.equals("Opened " + marker.name()), "Open puts a library brush in the slot: " + lab.status);
        lab.roll(PartDial.TOOTH);
        lab.randomize();
        check(lab.edited() && !lab.brush().name().equals(marker.name()), "Edited, under a rolled name");
        lab.save();
        BrushLibrary.Entry now = slots.library.find(lab.brush());
        check(now != null && now.id == entry.id && lab.brush().name().equals(marker.name()) && !lab.edited() && lab.saved() && lab.status.equals(LabLayout.SAVED),
                "Save writes the entry over in place, keeping its name: " + lab.brush().name() + ", " + lab.status);
        check(ids(slots).equals(ids) && slots.library.entries().size() == ids.size() && slots.library.find(oils) != null && slots.library.find(marker) == null, "no entry added, the others as they were");
        check(prefs.get(ToolSwatches.LOOK + ModularCode.encode(lab.parts())) != null
                && prefs.get(ToolSwatches.LOOK + ModularCode.encode(marker.parts)) == null, "its own size and grey moved to the new code");
        lab.rename("Marked Up");
        lab.save();
        check(slots.library.find(lab.brush()).id == entry.id && lab.brush().name().equals("Marked Up"), "A name given is kept");
        lab.undo();
        check(lab.savedEntry().id == entry.id, "Undo keeps where it stands with the library");
    }
    /** Save with As New (Save as New until 2026-11-03): a copy added, under the suggested name, the entry it came from left alone. */
    static void saveAsNew() {
        ToolSlots slots = slots();
        Brush marker = Brush.of(ModularStyle.named("Marker").roll(new Random(42)));
        slots.library.save(marker);
        BrushLab lab = new BrushLab(slots, 2, new Random(13));
        BrushLibrary.Entry entry = slots.library.find(marker);
        lab.open(entry);
        lab.randomize();
        String suggested = lab.suggestion();
        int before = slots.library.entries().size();
        lab.save(true);
        check(slots.library.entries().size() == before + 1 && slots.library.find(marker) != null && slots.library.find(marker).id == entry.id, "Save with As New adds a copy, the entry left alone");
        check(suggested == null || lab.brush().name().equals(slots.library.freeName(lab.brush())) && lab.brush().name().startsWith(suggested.replaceAll(" \\d+$", "")), "under the suggested name: " + suggested + " / " + lab.brush().name());
        check(lab.savedEntry() != null && lab.savedEntry().id != entry.id && !lab.edited(), "Save now writes over the copy");
    }
    /** The owner's "duplicated names" (2026-10-29): a suggestion the library has is passed by; Save as New keeps names one a brush. */
    static void uniqueNames() {
        int passed = 0;
        for (int seed = 0; seed < 40; seed++) {
            ToolSlots slots = slots();
            BrushLab lab = new BrushLab(slots, seed % ToolSlots.COUNT, new Random(seed));
            lab.roll(PartDial.WEIGHT);
            lab.roll(PartDial.TOOTH);
            String s = lab.suggestion();
            if (s == null) continue;
            ModularBrush other = ModularStyle.named("Hake").roll(new Random(seed));
            other.name = s.toLowerCase(java.util.Locale.ROOT);
            slots.library.save(Brush.of(other));
            String next = lab.suggestion();
            if (next != null && !slots.library.has(next)) passed++;
            lab.save(true);
            int same = 0;
            for (BrushLibrary.Entry e : slots.library.entries()) if (BrushLibrary.sameName(e.brush.name(), lab.brush().name())) same++;
            check(same == 1, "Save as New adds no second " + lab.brush().name());
        }
        check(passed > 20, "A taken suggestion gives way to the generator's next: " + passed);
        try {
            String activity = DrawLayoutChecks.source("BrushLabActivity"), labLayout = DrawLayoutChecks.source("LabLayout");
            check(!activity.contains("About This Brush\"") && !labLayout.contains("\"About This Brush\"") && activity.contains("private View aboutHeader() { return actions = LabUi.row(this); }"),
                    "The header's label is gone, its buttons from the left (the owner: \"remove the text ABOUT THIS BRUSH\")");
            check(activity.contains("readout.show(look, lab.brush(), true);") && DrawLayoutChecks.source("SwatchBar").contains("readout.show(look, brush, hasGreys)"),
                    "Beside the picker the side bar's readout, one SwatchReadout.show for both");
            // The simpler lab (2026-11-03, the owner's mockup).
            check(activity.contains("enum Action { NEW, OPEN, SAVE, AS_NEW, CUT }") && activity.contains("settings.newMenu(v)") && activity.contains("lab.save(asNew)") && !activity.contains("restore()") && !activity.contains("START_FROM +"),
                    "The header: New ▾ (the styles), Open, Save with its As New box, Cut Stamp; no Save as New or Back to");
            check(activity.indexOf("across.addView(readout") < activity.indexOf("across.addView(picker()") && activity.contains("words.setVisibility(wordsOpen ? View.VISIBLE : View.INVISIBLE)")
                    && activity.contains("loop.setOnClickListener(v -> toggleWords())") && activity.contains("name.setOnClickListener(v -> toggleWords())"),
                    "The readout at the picker's left, the picker against the edge; the brush's words shown while its loop or name is tapped");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    /** An edited brush is offered a name said by what it is: Title Case, its part's word in it or a paint colour's words by what it has; none unedited or once named. */
    static void suggests() {
        int offered = 0;
        for (int seed = 0; seed < 60; seed++) {
            BrushLab lab = new BrushLab(slots(), seed % ToolSlots.COUNT, new Random(seed));
            check(lab.suggestion() == null, "Nothing offered unedited");
            lab.roll(PartDial.WEIGHT);
            lab.roll(PartDial.TOOTH);
            String s = lab.suggestion();
            if (s == null) continue;
            offered++;
            List<String> words = BrushNames.plainWords(lab.parts());
            String part = words.get(words.size() - 1);
            // A paint colour's name hints by its words instead, each of a [colour] or [noun] line the brush has.
            java.util.Set<String> keys = BrushNames.traitKeys(lab.parts());
            String low = s.toLowerCase(java.util.Locale.ROOT);
            boolean hints = low.contains(part.toLowerCase(java.util.Locale.ROOT)) || java.util.Arrays.stream(low.split(" ")).allMatch(w -> java.util.stream.Stream.of(BrushNames.COLOUR, BrushNames.NOUN)
                    .anyMatch(sec -> BrushNames.traits().get(sec).entrySet().stream().anyMatch(e -> keys.contains(e.getKey()) && e.getValue().contains(w))));
            check(hints && Character.isUpperCase(s.charAt(0)) && s.length() <= Brush.NAME_MAX, "The suggestion " + s + " says its part, " + part + ", or is a paint colour's by what it has");
            check(lab.takeSuggestion() && lab.brush().name().equals(s) && lab.suggestion() == null, "Taken, it's the name, and no other is offered");
        }
        check(offered > 30, "Most edited brushes are offered a name: " + offered);
    }
    /** BrushLibrary.update: the entry's id and place kept, its brush and stamp new, the others untouched; an unknown id changes nothing. */
    static void update() {
        ToolSlots slots = slots();
        List<BrushLibrary.Entry> all = slots.library.entries();
        BrushLibrary.Entry third = all.get(2);
        Brush other = Brush.of(ModularStyle.named("Hake").roll(new Random(5)));
        check(slots.library.update(third.id, other), "Updated");
        List<BrushLibrary.Entry> after = slots.library.entries();
        check(after.size() == all.size() && after.get(2).id == third.id && after.get(2).brush.sameAs(other) && after.get(2).used > third.used, "in place, stamped as used");
        for (int i = 0; i < all.size(); i++) if (i != 2) check(after.get(i).id == all.get(i).id && after.get(i).brush.sameAs(all.get(i).brush) && after.get(i).used == all.get(i).used, "the others untouched");
        check(!slots.library.update(-1, other) && slots.library.entries().size() == all.size(), "An unknown id changes nothing");
    }
    /** New: a new brush no entry holds, so Save adds it; the slot's own brush is added too. */
    static void newBrush() {
        ToolSlots slots = slots();
        BrushLab lab = new BrushLab(slots, 2, new Random(14));
        lab.open(slots.library.entries().get(0));
        lab.newBrush(lab.parts().style);
        check(lab.savedEntry() == null && !lab.edited() && !lab.saved() && lab.status.equals("New " + lab.parts().style.label + " brush"), "New: no entry, not edited: " + lab.status);
        int before = slots.library.entries().size();
        lab.save(false);
        check(slots.library.entries().size() == before + 1 && lab.savedEntry() != null && lab.saved(), "Save adds it, As New off or on");
        BrushLab own = new BrushLab(slots, 4, new Random(15));
        own.randomize();
        own.save();
        check(slots.library.entries().size() == before + 2, "A slot's own brush, edited, is added, not written over the library's");
    }
    private static List<Long> ids(ToolSlots slots) {
        List<Long> ids = new java.util.ArrayList<>();
        for (BrushLibrary.Entry e : slots.library.entries()) ids.add(e.id);
        return ids;
    }
    static void randomize() {
        BrushLab lab = new BrushLab(slots(), 2, new Random(5));
        lab.newBrush(ModularStyle.named("Print"));
        check(lab.parts().style.name.equals("Print") && lab.canUndo() && lab.status.equals("New Print brush"), "New in the brush's own style rolls a new one in it: " + lab.status);
        lab.newBrush(ModularStyle.named("Charcoal"));
        lab.newBrush(ModularStyle.named("Pencil"));
        check(lab.parts().style.name.equals("Pencil") && lab.parts().choice(BrushPart.MEDIUM) == PartChoice.GRAPHITE && lab.status.equals("New Pencil brush"), "Picking a style rolls a six-part brush in it: " + lab.status);
        lab.toggleLock(PartDial.GRADE);
        double grade = lab.parts().part(BrushPart.MEDIUM).get(PartDial.GRADE);
        for (int i = 0; i < 20; i++) {
            lab.randomize();
            check(lab.parts().part(BrushPart.MEDIUM).get(PartDial.GRADE) == grade && lab.parts().style.name.equals("Pencil"), "Randomize stays in the style and keeps what's locked");
        }
        check(lab.status.equals("Randomized " + lab.parts().name), "and says so: " + lab.status);
        lab.toggleLock(PartDial.GRADE);
        check(lab.partLocks.dials.isEmpty(), "Unlocking");
        lab.newBrush(ModularStyle.named("Watercolor"));
        check(lab.parts().style.name.equals("Watercolor") && !lab.brush().inks(), "A pen's slot can take a paint's style");
        lab.newBrush(ModularStyle.named("Anything goes"));
        check(lab.parts().style.wild, "Anything goes");
        lab.newBrush(ModularStyle.named("Pencil"));
        lab.anythingGoes();
        check(lab.parts().style.wild && lab.status.equals("Randomized " + lab.parts().name), "Anything Goes rolls an Anything goes brush: " + lab.status);
        String wild = lab.code();
        lab.anythingGoes();
        check(lab.parts().style.wild && !lab.code().equals(wild), "and again when it already is one");
        lab.undo();
        check(lab.code().equals(wild), "and Undo takes back one roll");
    }
    /** A double tap on a slider (the owner, 2026-10-24): its dial back to its choice's default, the first tap's jump taken back, one Undo step, a note. */
    static void resets() {
        BrushLab lab = new BrushLab(slots(), 2, new Random(7));
        int n = 0;
        for (BrushPart part : BrushPart.values()) {
            for (PartDial dial : lab.dials(part)) {
                if (dial.choice() || !lab.parts().part(part).has(dial)) continue;
                Brush before = BrushLab.copyOf(lab.brush());
                PartChoice choice = lab.parts().part(part).choice;
                double def = choice.defaultOf(dial), other = def == dial.max ? dial.min : dial.max;
                lab.startSlide(); lab.slide(dial, other); lab.endSlide(dial); // the first tap
                lab.resetDial(dial); // the second
                check(lab.parts().part(part).get(dial) == def && "Reset ".concat(choice.labelOf(dial)).equals(lab.note()), choice.label + " " + dial.label + " resets to " + def);
                lab.undo();
                check(lab.brush().sameAs(before), dial.label + ": one Undo step takes the reset and the tap back");
                n++;
            }
        }
        check(n > 20, "Every slider resets (" + n + ")");
        try {
            check(DrawLayoutChecks.source("BandSlider").contains("listener.reset()") && DrawLayoutChecks.source("LabSettings").contains("lab.resetDial((PartDial) slider.id)") && DrawLayoutChecks.source("LabSettings").contains("slider.id = dial"),
                    "A double tap on a dial's slider calls the reset");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    static void undo() {
        BrushLab lab = new BrushLab(slots(), 2, new Random(7));
        // Copies: the lab changes its brush in place.
        Brush start = BrushLab.copyOf(lab.brush());
        lab.roll(PartDial.TOOTH);
        Brush rolled = BrushLab.copyOf(lab.brush());
        lab.rename("changed");
        lab.undo();
        check(lab.brush().sameAs(rolled) && lab.status.equals("Undone"), "Undo takes back the latest change");
        lab.undo();
        check(lab.brush().sameAs(start) && !lab.canUndo(), "then the one before, until none are left");
        lab.undo();
        check(lab.brush().sameAs(start), "Undo with nothing to undo does nothing");
        for (int i = 0; i < BrushLab.HISTORY + 10; i++) lab.roll(PartDial.TOOTH);
        int taken = 0;
        while (lab.canUndo()) { lab.undo(); taken++; }
        check(taken == BrushLab.HISTORY, "Undo keeps the latest " + BrushLab.HISTORY);
        check(lab.rename("  Rey   McSriff ") && lab.brush().name().equals("Rey McSriff") && lab.status.equals("Renamed"), "Rename tidies the name");
        check(!lab.rename("   ") && !lab.rename("Rey McSriff"), "An empty or unchanged name changes nothing");
    }
    static void save() {
        ToolSlots slots = slots();
        BrushLab lab = new BrushLab(slots, 2, new Random(8));
        lab.save();
        check(lab.status.equals("Already saved"), "A first brush is in the library already: " + lab.status);
        lab.randomize();
        String name = lab.brush().name();
        lab.save();
        check(lab.status.equals(LabLayout.SAVED) && slots.library.contains(lab.brush()), "Save adds a new brush to the library, and says so");
        lab.save();
        check(lab.status.equals("Already saved"), "Saving it again says so");
    }
    static void load() {
        BrushLab lab = new BrushLab(slots(), 2, new Random(9));
        check(!lab.load("nonsense") && lab.status.equals("Not a brush code. Paste all of it.") && !lab.canUndo(), "A bad code says so and changes nothing");
        String m = "brave-onion.M24p6okati000ee00_0atzz9000000020000_0_277_1e0_6ee00ee0000we000000nz0.1u5hvr4";
        check(lab.load(m) && lab.brush().name().equals("brave-onion") && lab.code().equals(m) && lab.canUndo(), "An M code loads its brush");
        check(lab.status.equals("Loaded brave-onion"), lab.status);
        check(lab.load("dapper-fig.I610gavij3f7q16o0000006m00.e1") && lab.brush().name().equals("dapper-fig") && lab.parts().style.wild
                && lab.code().equals(LegacyConversionChecks.CODES[0][1]), "An old I code loads as its six parts: " + lab.code());
    }
    static void library() {
        ToolSlots slots = slots();
        Brush saved = Brush.of(ModularStyle.named("Marker").roll(new Random(42))), older = Brush.of(ModularStyle.named("Oils").roll(new Random(43)));
        slots.library.save(older);
        slots.library.save(saved);
        slots.use(3, saved);
        BrushLab lab = new BrushLab(slots, 3, new Random(10));
        List<BrushLab.Card> cards = lab.library();
        check(cards.size() == ToolSlots.COUNT + 2, "The six first brushes and the two saved: " + cards.size());
        check(cards.get(0).brush.sameAs(saved) && cards.get(1).brush.sameAs(older), "newest first");
        for (int i = 0; i < ToolSlots.COUNT; i++) check(cards.get(2 + i).brush.sameAs(ToolSlotsChecks.old(i)), "then the first brushes, in slot order");
        check(cards.get(0).slots.equals(java.util.Arrays.asList(3)) && cards.get(1).slots.isEmpty() && cards.get(2 + 3).slots.isEmpty() && cards.get(2).slots.equals(java.util.Arrays.asList(0)),
                "Each card knows the slots holding it");
        check(cards.get(0).deletable() && cards.get(2).deletable(), "Every card can be deleted");
        check(lab.now(cards.get(0)) && !lab.now(cards.get(2)), "The card in the lab shows it");
        lab.put(cards.get(5).brush);
        check(lab.brush().name().equals("Wax crayon") && lab.status.equals("Wax crayon in slot 4"), "Tapping a card puts it in the slot: " + lab.status);
        check(lab.delete(cards.get(1)) && !slots.library.contains(older) && lab.status.equals("Deleted"), "Deleting a card");
        check(lab.delete(cards.get(2 + 5)) && lab.library().size() == ToolSlots.COUNT, "a first brush too: it isn't brought back");
    }
    static void result() {
        ToolSlots slots = slots();
        BrushLab lab = new BrushLab(slots, 1, new Random(11));
        check(lab.results()[lab.selected()].sameAs(slots.brush(1)), "Back unchanged returns the slot's brush");
        lab.roll(PartDial.TAPER_IN);
        Brush result = lab.results()[lab.selected()];
        check(result.parts.style.name.equals("Print") && slots.brush(1).name().equals("Halftone brush") && slots.brush(1).sameAs(ToolSlotsChecks.old(1)),
                "Back with a change returns the changed brush; the slot changes only when the drawing takes it");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /**
     * The slot's first brush stays in reach without Back to (gone 2026-11-03, the owner: "Undo and Recent already cover
     * it"): Undo takes the lab back to it, and Recent holds it, or Saved (a library brush) or its kind (a Basic tool).
     */
    static void startReachable() {
        ToolSlots slots = slots();
        BrushLab lab = new BrushLab(slots, 2, new Random(9));
        Brush first = lab.brush();
        lab.randomize();
        Brush rolled = lab.brush();
        lab.newBrush(ModularStyle.named("Watercolor"));
        lab.newBrush(ModularStyle.named("Hake"));
        check(slots.recent().stream().anyMatch(b -> b.sameAs(rolled)), "An unsaved brush a change lets go is in Recent");
        while (lab.canUndo()) lab.undo();
        check(lab.brush().sameAs(first), "Undo, step by step, takes the slot back to its first brush");
        boolean reached = slots.library.contains(first) || ToolPresets.find(first) != null || slots.recent().stream().anyMatch(b -> b.sameAs(first));
        check(reached, "and the first brush is in Saved, its kind or Recent: " + first.name());
        // Opened from the Tools page: the start is the Tools page's, before either screen changed the slot.
        Brush pencil = slots.brush(2);
        ToolSlots.Start start = ToolSlots.Start.of(slots);
        slots.use(2, Brush.of(ModularStyle.named("Hake").roll(new Random(3))));
        BrushLab after = new BrushLab(slots, 2, new Random(9), start.write());
        check(after.start.brushes[2].sameAs(pencil), "The Tools page's start carried through, not the brush the Tools page put in");
        check(new BrushLab(slots, 2, new Random(9), null).start.brushes[2].sameAs(slots.brush(2)) && new BrushLab(slots, 2, new Random(9), new String[]{"x"}).start.brushes[2].sameAs(slots.brush(2)),
                "Opened from the drawing screen (or a start that doesn't read): the slots as they are");
        check(ToolSlots.Start.read(start.write(), slots).write().length == start.write().length && java.util.Arrays.equals(ToolSlots.Start.read(start.write(), slots).write(), start.write()), "The start round-trips");
    }
    /** Nothing unsaved is lost when a slot is replaced: kept in Recent (docs/archive/design-pass-2026-10-23.md). */
    static void keepsInRecent() {
        ToolSlots slots = slots();
        BrushLab lab = new BrushLab(slots, 0, new Random(5));
        lab.randomize();
        check(lab.note() == null, "A slot's own saved brush (the first launch's Fineliner) isn't kept: it's in the library");
        Brush made = lab.brush();
        lab.randomize();
        check(slots.recent().get(0).sameAs(made) && ToolSlots.keptNote(made).equals("Kept " + made.name() + " in Recent"), "Randomize lets an unsaved brush go: it's first in Recent");
        check(ToolSlots.keptNote(made).equals(lab.note()) && lab.note() == null, "and the note says so, once: " + ToolSlots.keptNote(made));
        Brush second = lab.brush();
        lab.newBrush(ModularStyle.named("Pencil"));
        lab.load(ModularCode.encode(ToolSlotsChecks.old(1).parts));
        check(slots.recent().get(0).parts.style == ModularStyle.named("Pencil") && slots.recent().get(1).sameAs(second), "A style and Load keep theirs too");
        lab.note();
        lab.save();
        Brush saved = lab.brush();
        check(lab.saved() && new BrushLab(slots, 1, new Random(1)).saved(), "Save: the lab's brush reads Saved (a first launch's too)");
        lab.randomize();
        check(lab.note() == null, "A saved brush isn't kept: it's in the library");
        for (Brush b : slots.recent()) check(!b.sameAs(saved), "not in Recent");
        int before = slots.recent().size();
        lab.put(ToolPresets.of(ModularStyle.named("Pencil")).get(0).brush());
        lab.note();
        lab.randomize();
        check(lab.note() == null, "Nor a Basic tool: it's never lost");
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 50; i++) lab.randomize();
        for (Brush b : slots.recent()) check(seen.add(b.identity()), "Recent holds each brush once");
        check(slots.recent().size() == ToolSlots.RECENT_MOST && ToolSlots.RECENT_MOST == 30, "Recent keeps the latest 30: " + slots.recent().size() + " (" + before + " before)");
        check(!slots.keep(slots.recent().get(3)) && slots.recent().size() == 30, "A brush already in Recent isn't kept twice");
    }
    /** New Brush: the new brush stays in its slot on Back, saved or not (the owner, 2026-10-23: no trap). */
    static void freshStays() {
        ToolSlots slots = slots();
        BrushLab lab = new BrushLab(slots, 3, new Random(6));
        lab.randomize();
        check(!lab.saved() && lab.results()[3].sameAs(lab.brush()) && !lab.results()[3].sameAs(slots.brush(3)), "The new brush goes back in its slot, unsaved");
    }
}
