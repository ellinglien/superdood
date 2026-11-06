package io.github.mpdairy.monopaint;

import java.util.HashSet;
import java.util.Set;

public final class ToolPresetsChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (System.getProperty("pinned") != null) { for (String[] row : rows()) System.out.println("            {\"" + row[2] + "\", \"" + drawn(row[2]) + "\"},"); return; }
        table(); codes(); pinned(); words(); draws(); tissue(); String hb = hbPencil(); String pared = shownAndShelf();
        System.out.println("PASS: every code a Basic tool had on 2026-10-31 still known and drawing as it did; 124 presets, five for each of the 13 rolled styles, Print's twelve (five Screentone, seven through Print Shading), the everyday pens (Ballpoint, Gel pen, Fountain pen, Highlighter), Vector ink (Fineliner), the owner's seven Brush Studio pens (two Fineliner, one Brush pen, three Marker, one Pencil), the seven showing the engine's choices no other preset did and the Stamp, the four gravity ones (three Watercolor, one Charcoal; Drips retired) that move with the page's tilt, Charcoal's Blend and Blur,Ink wash's Sumi and Clear water (Float), Watercolor's Water and Dirty brush (the wet smudges), Gouache's and Oils' six Wet ones (Gouache water, Loaded oils) and Brush pen's Wet brush (ink in water), the old Gouache and Oils, the old Wet brush, Filled shape and the four older smudges retired but known, a bristle brush (Dry brush), Pencil's Sketch pencil (erases clean) and the web lab's Stipple pen, Hatch pen, Hand hatch, Neat hatch, Loose hatch (Pencil), White gel pen (Fineliner), Stipple sponge, Wash behind, Masking fluid and Wax resist (Watercolor) and Mold ink (Ink wash); each erasing clean (Erasable 1; Resist's wax or fluid its own); every share code decodes to its style and round-trips, names are unique, names (but the owner's) and descriptions fit one line of the Nomad's card, and every preset draws; the retired Damp tissue lifts the wash and darkens nothing, paper or paint; " + hb + "; the release list: " + pared);
    }

    /** Watercolor's washes made Wet (the wet paint spec, section 9), under their old names. */
    static final java.util.List<String> WET_WASHES = java.util.Arrays.asList("Soft wash", "Wet blooms", "Backrun", "Watercolour backrun", "Granulating", "Slow bloom", "Pooling wash", "Tilt blend", "Sinking grain");
    /** Backrun shows as Watercolour backrun since 2026-11-06 (its code name and seed kept); the retired one keeps the old name. */
    static String oldName(String name) { return name.equals("Watercolour backrun") ? "Backrun" : name; }
    /** Ink wash's washes made Wet (the ink wash note, 2026-10-22), under their old names. */
    static final java.util.List<String> WET_INK_WASHES = java.util.Arrays.asList("Line and wash", "Wet ink", "Grey wash", "Flat wash", "Slow ink wash");
    /** A wet wash's style and name: Watercolor's or Ink wash's. */
    static boolean wetWash(ToolPresets.Preset p) { return p.style.name.equals("Watercolor") && WET_WASHES.contains(p.name) || p.style.name.equals("Ink wash") && WET_INK_WASHES.contains(p.name); }
    /** The owner's pens (2026-10-27): Brush Studio saves kept as made, under the owner's names. */
    /** explore-media's (2026-11-01): White chalk, Dip nib, Water pencil. */
    static final java.util.List<String> MEDIA = java.util.Arrays.asList("White chalk", "Dip nib", "Water pencil");
    /** The owner's round of 2026-11-06: Soft pencil. */
    static final java.util.List<String> ASKED = java.util.Arrays.asList("Soft pencil");
    static final java.util.List<String> OWNERS = java.util.Arrays.asList("Cheap ballpoint", "Smooth fountain", "Ink wash brush", "Permanent marker", "Acrylic paint marker", "Ms. Sketch", "Another pencil",
            // The owner's Brush Lab saves of 2026-11-06, kept as made: Watercolour splatter (Watercolor), Opaque flat (Anything goes)
            "Watercolour splatter", "Opaque flat");
    /**
     * The release pare-down (2026-11-05, docs/superpowers/plans/2026-11-05-release-pare-down.md, section 1 and the owner's
     * answers): the shown tools are the plan's list, the main kit then the picks, each once; More Brushes holds every other
     * Basic tool, each once, none of them shown; every row is in one of the two (but the Highlighter, the bar's High lighter).
     */
    static String shownAndShelf() {
        java.util.List<String> plan = java.util.Arrays.asList(
                // The main kit (ToolSlots.FIRST), in its order (Fill in, the bar's own tool, after it until the owner's library round, 2026-11-06)
                "Another pencil", "Steady line", "Verve felt", "Fude fountain", "Ink wash", "Watercolour wash", "Rob boss", "Ben-Day brush", "High lighter",
                // Pens (Steady line in the kit; the Fude pen is Fude fountain, 2026-11-06)
                "Cheap ballpoint", "Hatch pen", "Stipple pen", "Fountain pen", "Split nib", "Ink wash brush",
                // Markers (Highlighter is High lighter, Fill in the bar's own, Filled shape merged into it)
                "Ms. Sketch",
                // Pencil: Another pencil, in the kit (HB pencil dropped). Charcoal and pastels
                "Soft pencil", "Soft charcoal", "Soft pastel", "Wax crayon",
                // Wet (Wet gouache took Opaque round's place; Water, the smudge tool's, out 2026-11-06), with the owner's library
                // round (2026-11-06): Watercolour backrun, Granulating and Loaded oils from the shelf, the owner's Watercolour splatter and Opaque flat
                "Soft wash", "Slow bloom", "Watercolour backrun", "Granulating", "Watercolour splatter", "Flat wash", "Wet gouache", "Opaque flat",
                "Cracking paint", "Wet oils", "Loaded oils", "Dry round", "Flat brush",
                // Print (Ben-Day brush in the kit)
                "Gradient tone", "Rosette ink", "Halftone brush", "Newsprint", "Riso", "Photocopier",
                // The textures (2026-11-06): each Print Shading no shown tool had, the shelf's presets then the new markers; of
                // them the owner kept these two in the library round, the other eleven back on the shelf
                "Engraving pen", "Wavy-line pen",
                // Print Shading's Pixels and Stitches (the owner, 2026-11-06)
                "Pixel pen", "Stitch pen");
        java.util.List<String> shown = new java.util.ArrayList<>();
        for (ToolPresets.Preset p : ToolPresets.shown()) { check(p != null, "Every shown name is a Basic tool or a first-launch brush"); shown.add(p.name); }
        check(shown.equals(plan), "The shown tools are the plan's, in its order: " + shown);
        check(new HashSet<>(shown).size() == shown.size(), "Each shown once");
        for (int i = 0; i < ToolSlots.FIRST.length; i++) check(ToolPresets.shown().get(i).brush().sameAs(ToolSlots.first(i)), "The bar's first-launch brush shown as it is: " + ToolSlots.FIRST[i][1]);
        Set<String> shelf = new HashSet<>();
        for (ToolPresets.Preset p : ToolPresets.shelf()) {
            check(shelf.add(p.name) && !shown.contains(p.name), "On the shelf once, not shown: " + p.name);
            check(ToolPresets.find(p.brush()) == p, p.name + " on the shelf is a Basic tool");
        }
        int left = 0;
        // The Highlighter is the bar's High lighter; the Fude pen the bar's Fude fountain (the owner, 2026-11-06: "fude pen is a duplicate");
        // Fill in and Water the bar's own (Fill in beside the eraser, Water in the smudge tool's chooser; 2026-11-06).
        java.util.List<String> elsewhere = java.util.Arrays.asList("Highlighter", "Fude pen", "Fill in", "Water", "Blend", "Blur");
        for (ToolPresets.Preset p : ToolPresets.all()) if (!shown.contains(p.name) && !shelf.contains(p.name)) { left++; check(elsewhere.contains(p.name), "Every Basic tool shown or on the shelf: " + p.name); }
        check(left == 6 && shelf.contains("HB pencil") && shelf.contains("Maze") && ToolPresets.shelf().size() == 111 && shown.size() == 43,
                "43 shown, 111 on the shelf, the Highlighter and the Fude pen once (as High lighter and Fude fountain), Fill in, Water, Blend and Blur on the bar alone, HB pencil on the shelf: " + ToolPresets.shelf().size());
        return shown.size() + " shown, " + ToolPresets.shelf().size() + " in More Brushes";
    }

    static void table() {
        for (String[] row : ToolSlots.FIRST) check((row[3] + "," + row[4]).equals(ToolPresets.look(Brush.read(row[1] + "\t" + row[2]))) || ToolPresets.find(Brush.read(row[1] + "\t" + row[2])) != null, row[1] + ": a bar brush with no preset row starts at the bar's size and grey");
        check(ToolPresets.styles().size() == 14 && !ToolPresets.styles().contains(ModularStyle.named(ModularStyle.ANYTHING_GOES)) && ToolPresets.styles().get(13) == ModularStyle.named("Print"), "The chips' 14 styles, Print last, not Anything goes");
        check(ToolPresets.all().size() == 154 && ToolPresets.of(null).size() == 154, "154 presets; All shows them all");
        java.util.List<String> everyday = java.util.Arrays.asList("Ballpoint", "Gel pen", "Fountain pen", "Highlighter");
        for (ModularStyle s : ToolPresets.styles()) {
            if (s.name.equals("Print")) { prints(); continue; }
            int pens = 0, owned = 0, vectors = 0;
            int engine = 0;
            int media = 0, asked = 0;
            int gravity = 0, stump = 0, floats = 0, bristles = 0, sketch = 0, patterns = 0, blends = 0, resists = 0, molds = 0, wets = 0, oils = 0;
            for (ToolPresets.Preset p : ToolPresets.of(s)) {
                ModularBrush b = ModularCode.decode(p.code);
                // Vector ink (Fineliner): Steady line's pen drawn as lines, counted on its own.
                if (VectorInk.is(p.brush())) { check(s.name.equals("Fineliner"), "Vector ink under Fineliner"); vectors++; continue; }
                // A resist's Erasable says which it is: masking fluid (1) or wax (0).
                if (b.choice(BrushPart.MEDIUM) == PartChoice.RESIST) { resists++; continue; }
                check(b.part(BrushPart.MEDIUM).get(PartDial.ERASABLE, 1) == 1, p.name + " erases clean (Erasable 1, 2026-10-22)");
                // The engine's choices no preset showed (Crayon, Spiro, Stay in Lines, Filled Shape, Lift, Fading, Lemon Juice, Springy, the nibs): counted on their own.
                if (engine(b)) { engine++; continue; }
                if (MEDIA.contains(p.name)) { media++; continue; }
                if (ASKED.contains(p.name)) { asked++; continue; }
                // The everyday pens, built by hand: counted on their own.
                if (everyday.contains(p.name)) { pens++; continue; }
                if (OWNERS.contains(p.name)) { owned++; continue; }
                // The wet smudges (Water, Dirty brush, Palette knife): counted on their own.
                if (b.part(BrushPart.CARRY).get(PartDial.KEEPS, 0) > 0 || b.choice(BrushPart.CARRY) == PartChoice.CLEAR_WATER || b.choice(BrushPart.CARRY) == PartChoice.SCRAPE) { wets++; continue; }
                // Oil paint (the oil paint spec): Paint Load, counted on its own.
                if (b.part(BrushPart.CARRY).get(PartDial.PLOAD, 0) > 0) { oils++; continue; }
                // The Gravity Lab's (a Wet brush's Runs Downhill is the wet paint's own; but the three wet watercolours that
                // replaced its Pooling wash, Tilt blend and Sinking grain are still its).
                boolean wetLife = b.choice(BrushPart.LIFE) == PartChoice.WET_PAINT;
                if (GravityChecks.usesGravity(b) && !wetLife || wetLife && s.name.equals("Watercolor") && b.part(BrushPart.LIFE).get(PartDial.RUNS) > 0) gravity++;
                if (p.name.equals("Sketch pencil")) sketch++;
                // The smudges that lay nothing on the page (Blend, Wash comb; Float's are Float's).
                if (b.mischief() != PartChoice.LATER_FLOAT && b.choice(BrushPart.CARRY) != PartChoice.OFF && b.part(BrushPart.CARRY).get(PartDial.LOAD, 1) == 0) stump++;
                if (b.mischief() == PartChoice.LATER_FLOAT) floats++;
                if (b.mischief() == PartChoice.LATER_MOLD) molds++;
                if (b.choice(BrushPart.TIP) == PartChoice.BRISTLES) bristles++;
                if (b.choice(BrushPart.TIP) == PartChoice.STIPPLE || b.choice(BrushPart.TIP) == PartChoice.HATCH || b.choice(BrushPart.TIP) == PartChoice.HAND_HATCH) patterns++;
                if (b.part(BrushPart.MEDIUM).get(PartDial.M_BLEND) != 0) blends++;
            }
            check(pens == (s.name.equals("Fineliner") ? 3 : s.name.equals("Marker") ? 1 : 0), "Ballpoint, Gel pen and Fountain pen under Fineliner, Highlighter under Marker");
            check(owned == (s.name.equals("Fineliner") ? 2 : s.name.equals("Marker") ? 3 : s.name.equals("Brush pen") || s.name.equals("Pencil") || s.name.equals("Watercolor") ? 1 : 0), "Cheap ballpoint and Smooth fountain under Fineliner, Ink wash brush under Brush pen, Permanent marker, Acrylic paint marker and Mr. Sketch under Marker, Another pencil under Pencil, Watercolour splatter under Watercolor");
            check(ToolPresets.of(s).size() - vectors - engine - media - asked - pens - owned - gravity - stump - floats - bristles - sketch - patterns - blends - resists - molds - wets - oils == 5, "Five presets for " + s.name + " besides gravity's, the smudges, Float's, bristles', Sketch pencil, the stipples and hatching, Blend's, Resist's, Mold's, the wet smudges and Oil paint");
            check(engine == (s.name.equals("Fineliner") || s.name.equals("Dip pen") ? 3 : s.name.equals("Marker") || s.name.equals("Pastels") ? 2 : s.name.equals("Brush pen") || s.name.equals("Charcoal") || s.name.equals("Pencil") || s.name.equals("Dry brush") || s.name.equals("Print") || s.name.equals("Gouache") ? 1 : 0),
                    "Spirograph, Vanishing ink and Stamp under Fineliner, Springy brush under Brush pen, Lemon juice, Ruling pen and Twig pen under Dip pen, Colouring pen and Fill in under Marker, Wax crayon and Crayon scratch under Pastels, Long stick under Charcoal, Metalpoint (Tarnish) under Pencil, Fan brush under Dry brush, Brayer under Print, Cracks under Gouache");
            check(oils == (s.name.equals("Oils") ? 1 : 0), "Loaded oils under Oils");
            check(asked == (s.name.equals("Pencil") ? 1 : 0), "Soft pencil under Pencil");
            check(media == (s.name.equals("Charcoal") || s.name.equals("Dip pen") || s.name.equals("Pencil") ? 1 : 0), "White chalk under Charcoal, Dip nib under Dip pen, Water pencil under Pencil");
            check(wets == (s.name.equals("Watercolor") ? 3 : s.name.equals("Gouache") ? 1 : 0), "Water, Dirty brush and Palette knife under Watercolor, Gouache water under Gouache");
            check(molds == (s.name.equals("Ink wash") ? 1 : 0), "Mold ink under Ink wash");
            check(resists == (s.name.equals("Watercolor") ? 2 : 0), "Masking fluid and Wax resist under Watercolor");
            check(blends == (s.name.equals("Fineliner") || s.name.equals("Watercolor") ? 1 : 0), "White gel pen under Fineliner, Wash behind under Watercolor");
            check(patterns == (s.name.equals("Fineliner") ? 4 : s.name.equals("Watercolor") || s.name.equals("Pencil") ? 1 : 0), "Stipple pen, Hatch pen, Hand hatch and Neat hatch under Fineliner, Loose hatch under Pencil, Stipple sponge under Watercolor");
            check(sketch == (s.name.equals("Pencil") ? 1 : 0), "One Sketch pencil, under Pencil");
            check(floats == (s.name.equals("Ink wash") ? 3 : 0), "Float's three (Sumi, Clear water, Comb), under Ink wash");
            check(stump == (s.name.equals("Charcoal") ? 2 : s.name.equals("Watercolor") ? 1 : 0), "Blend and Blur under Charcoal, Wash comb under Watercolor");
            check(bristles == (s.name.equals("Dry brush") ? 1 : 0), "Bristles' preset: one Dry brush (Hog bristle retired with the old Oils)");
            // Gouache and Oils are Wet (the wet paint spec; the owner, 2026-10-22), and Watercolor's washes (section 9), every other style's none;
            // but Cracks (Gouache), Slow, as Later's Cracks goes only with Instant, Quick or Slow.
            for (ToolPresets.Preset p : ToolPresets.of(s))
                check((p.brush().parts.choice(BrushPart.LIFE) == PartChoice.WET_PAINT) == ((s.name.equals("Gouache") || s.name.equals("Oils")) && !p.name.equals("Cracking paint") || p.name.equals("Wet brush")
                        || wetWash(p)), p.name + ": Gouache (but Cracks), Oils, the Wet brush and Watercolor's and Ink wash's washes are Wet, no other");
            check(gravity == (s.name.equals("Watercolor") ? 3 : s.name.equals("Charcoal") ? 1 : 0), "Gravity's presets: three Watercolor, one Charcoal (Drips retired)");
        }
        Set<String> names = new HashSet<>();
        for (ToolPresets.Preset p : ToolPresets.all()) check(names.add(p.name.toLowerCase(java.util.Locale.ROOT)), "Preset names are unique: " + p.name);
        // The old Gouache and Oils (retired when Wet replaced them): known, not offered, none of their names reused.
        check(ToolPresets.retired().size() == 35, "35 retired presets, the old Ink wash washes' five, the old Watercolor washes' eight, Drips, the old Wet brush, Filled shape (merged into Fill in), the smudge tool's older four (Blend and Water took their place), the old Gouache's five and Oils' seven, the old HB pencil, the old Gradient tone, the old Mezzotint");
        for (ToolPresets.Preset p : ToolPresets.retired()) {
            // The old Wet brush's name goes on: the wet canvas one replaced it under the same name (the wet brush spec), and
            // so do the Watercolor washes' (section 9) and the HB pencil's (Pressure Dark 0, 2026-11-05).
            boolean hb = p.name.equals("HB pencil") && p.style.name.equals("Pencil") || (p.name.equals("Gradient tone") || p.name.equals("Mezzotint")) && p.style.name.equals("Print"), wetBrush = p.name.equals("Wet brush"), drips = p.name.equals("Drips") && p.style.name.equals("Watercolor"), wash = wetWash(p), shape = p.name.equals("Filled shape"), smudge = false;
            for (String[] move : ToolSlots.SMUDGE_MOVES) smudge |= p.name.equals(move[0]);
            // Backrun's name went with it to Watercolour backrun (2026-11-06).
            check(names.contains(p.name.toLowerCase(java.util.Locale.ROOT)) == (wetBrush || wash && !p.name.equals(oldName("Watercolour backrun")) || hb) && ToolPresets.find(p.brush()) == null, p.name + " is retired: not offered, its name not reused (but the Wet brush's, the washes', the HB pencil's, Gradient tone's and Mezzotint's; the HB pencil's)");
            check(p.brush().parts.choice(BrushPart.LIFE) != PartChoice.WET_PAINT && (p.style.name.equals("Gouache") || p.style.name.equals("Oils") || wetBrush && p.style.name.equals("Brush pen") || wash || drips || shape || smudge || hb),
                    p.name + " is an old Gouache or Oils brush, the old Wet brush, an old Watercolor or Ink wash wash, Drips, Filled shape, an older smudge, the old HB pencil, the old Gradient tone or the old Mezzotint");
        }
        // The wet watercolours (section 9): the old wash's tip, medium, paper and Control, Wet; a transparent wash on the wet canvas.
        for (ToolPresets.Preset p : ToolPresets.all()) if (wetWash(p)) {
            ModularBrush b = p.brush().parts, old = null;
            for (ToolPresets.Preset q : ToolPresets.retired()) if (q.name.equals(oldName(p.name)) && q.style == p.style) old = q.brush().parts;
            check(old != null, p.name + " replaced an old one");
            for (BrushPart part : BrushPart.values()) if (part != BrushPart.LIFE && part.ordinal() < BrushPart.SIX)
                check(ModularCode.encode(b).split("_")[part.ordinal()].replaceAll("^.*\\.M[67]", "").equals(ModularCode.encode(old).split("_")[part.ordinal()].replaceAll("^.*\\.M[67]", "")),
                        p.name + ": its " + part + " is the old one's");
            ResolvedBrush r = ResolvedBrush.of(b, 60, .7);
            boolean ink = p.style.name.equals("Ink wash");
            check((b.choice(BrushPart.MEDIUM) == PartChoice.WASH || ink && b.choice(BrushPart.MEDIUM) == PartChoice.INK) && r.wet != null && r.wet.wash != null && !r.wet.inks && !r.wet.rewets
                    && (!ink || r.wet.runs == 0), p.name + ": a wet wash, dried paint glazed over (Ink wash's still on a tilt)");
            // Ink wash: on the wash half, so its washes merge with the others there; Line and wash and Wet ink keep their pen line.
            check(ModularStroke.layerOf(InkWashLayers.blank(10, 10), b) == InkWashLayers.WASH, p.name + ": wet on the wash half");
            check(r.wet.line == (p.name.equals("Line and wash") || p.name.equals("Wet ink")), p.name + ": a pen line only where it is the brush's");
        }
        // The Wet brush: Wet ink in water on a Wash (the wash half), its old one's tip and Control.
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals("Wet brush")) {
            ModularBrush b = p.brush().parts;
            ResolvedBrush r = ResolvedBrush.of(b, 60, 1);
            check(b.choice(BrushPart.MEDIUM) == PartChoice.WASH && r.wet != null && r.wet.inks && r.wet.rewets && r.wet.runs > 0, "Wet brush: Wet ink in water on a Wash, lifting dry paint, running downhill");
        }
    }
    /** Print's seventeen: Screentone's five patterns (Dots to Crosshatch), eight through a Print Shading, each a different one (Riso's Risograph the eighth), the Brayer, the Photocopier and Newsprint, which reprint, and Letterpress (Later's). */
    static void prints() {
        java.util.List<ToolPresets.Preset> print = ToolPresets.of(ModularStyle.named("Print"));
        check(print.size() == 28, "Twenty-eight Print presets");
        Set<Integer> patterns = new HashSet<>(), screens = new HashSet<>();
        for (ToolPresets.Preset p : print) {
            ModularBrush.Part m = p.brush().parts.part(BrushPart.MEDIUM);
            if (m.has(PartDial.ERASABLE)) check(m.get(PartDial.ERASABLE) == 1, p.name + " erases clean");
            if (p.brush().parts.choice(BrushPart.TIP) == PartChoice.ROLLER || p.brush().parts.choice(BrushPart.CARRY) == PartChoice.REPRINT || p.brush().parts.later()) continue;
            if (m.choice == PartChoice.TONE) patterns.add((int) m.get(PartDial.PATTERN));
            else screens.add((int) m.get(PartDial.SCREEN, 0));
        }
        check(patterns.size() == 5 && !patterns.contains(5) && !patterns.contains(6), "Five Screentone patterns: " + patterns);
        check(screens.size() == 19 && !screens.contains(0) && screens.containsAll(java.util.Arrays.asList(1, 2, 3, 5, 6, 8, 9, 10, 12, 13, ScreenChecks.RISO, 18, 19, 20, 21, 22, 23, ScreenChecks.PIXELS, ScreenChecks.STITCHES)), "Nineteen Print Shadings, all different: " + screens);
        // The texture markers (2026-11-06) and Mezzotint: the pattern fills in as the pen presses (full Pressure Dark).
        for (String name : new String[]{"Mezzotint", "Woodcut", "Diamond dots", "Scribble", "Maze", "Brick", "Basketweave", "Waves", "Honeycomb", "Rings", "Pixel pen", "Stitch pen"})
            check(ToolKits.preset(name).brush().parts.part(BrushPart.HAND).get(PartDial.PD) == 1, name + ": full Pressure Dark");
        // The highlighter goes behind the lines (Blend Mode's Behind) in a light wash.
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals("Highlighter")) {
            ModularBrush b = p.brush().parts;
            check(b.choice(BrushPart.MEDIUM) == PartChoice.WASH && b.part(BrushPart.MEDIUM).get(PartDial.M_BLEND) == 2 && b.choice(BrushPart.TIP) == PartChoice.CHISEL
                    && ModularSizes.size(b, InkWashSizes.Preset.MEDIUM) >= 40, "Highlighter: a broad chisel of wash, behind");
        }
        // Every preset's code is PresetPictures' (the rows are what it prints).
        for (PresetPictures.Made m : PresetPictures.made()) {
            ToolPresets.Preset p = null;
            for (ToolPresets.Preset q : ToolPresets.all()) if (q.name.equals(m.name)) p = q;
            check(p != null && p.code.equals(m.code()) && p.line.equals(m.line) && p.style.name.equals(m.style), m.name + " is PresetPictures' row");
        }
    }
    /** A preset that shows a choice of the engine's no other preset does: Crayon, Spiro, Stay in Lines, Filled Shape, Lift, Fading, Lemon Juice or Control's Springy. */
    static boolean engine(ModularBrush b) {
        return b.choice(BrushPart.MEDIUM) == PartChoice.CRAYON || b.choice(BrushPart.TIP) == PartChoice.SPIRO || b.choice(BrushPart.CARRY) == PartChoice.STAY_IN || b.choice(BrushPart.CARRY) == PartChoice.SHAPE
                || b.choice(BrushPart.CARRY) == PartChoice.LIFT || b.mischief() == PartChoice.LATER_FADE || b.mischief() == PartChoice.LATER_LEMON || b.mischief() == PartChoice.LATER_TARNISH || b.mischief() == PartChoice.LATER_CRACKS
                || b.choice(BrushPart.HAND) == PartChoice.BRUSH_HAND || b.choice(BrushPart.TIP) == PartChoice.STAMP || b.part(BrushPart.HAND).get(PartDial.LONG_STICK, 0) > 0 || ResolvedBrush.nib(b.choice(BrushPart.TIP));
    }
    /**
     * Damp tissue lifts and lays nothing (the owner, 2026-10-22: "damp tissue lifts paint but if it touches any
     * light/paper area it leaves a dark mark"): Clear Water, Lay Down 0. Across a page half washed (tone 120, its wash
     * layer), live and quick, at two sizes: no pixel of the paper darker, none of the wash darker, the wash lifted.
     */
    static void tissue() {
        ModularBrush b = FloatChecks.preset("Damp tissue");
        check(b.choice(BrushPart.CARRY) == PartChoice.CLEAR_WATER && b.part(BrushPart.CARRY).get(PartDial.LAY) == 0 && ModularBrush.ownPaint(b.part(BrushPart.CARRY)) == 0, "Damp tissue: Clear Water, no paint of its own, Lay Down 0");
        int W = 600, H = 200;
        for (double size : new double[]{30, 80}) for (Boolean quick : new Boolean[]{false, true}) {
            ToneDocument d = InkWashLayers.blank(W, H);
            d.selectLayer(InkWashLayers.WASH);
            d.begin(1);
            int[] row = new int[W];
            for (int y = 0; y < H; y++) { for (int x = 0; x < W; x++) row[x] = x < W / 2 ? 120 : -1; d.setTones(0, y, W, 1, row); }
            d.finish(); d.clearDirty();
            int[] before = new int[W * H];
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) before[y * W + x] = d.compositeTone(x, y);
            WatercolorSession session = new WatercolorSession();
            QuickStroke.benchAlways = quick;
            try {
                DrawingStroke s = QuickStroke.start(d, b.copy(), size, 1, session);
                // From the paper into the wash and out onto the paper again, carrying what it lifted.
                for (int i = 0; i < 400; i++) { float u = i / 399f; s.sample(40 + 520 * (u < .5f ? 2 * u : 2 - 2 * u) , 100 + (float) (40 * Math.sin(i / 40.0)) + (u < .5f ? -20 : 20), ToolSettings.pressure(.6f), 0, 30, 100_000 + i * 2L); if (i % 8 == 7) s.present(); }
                s.present(); s.finish();
                for (int t = 0; t < 5000 && session.settling(); t++) session.tickDrying(d);
            } finally { QuickStroke.benchAlways = null; }
            int darker = 0, lifted = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int a = before[y * W + x], c = d.compositeTone(x, y);
                if (c < a) darker++;
                if (x < W / 2 && c > a + 40) lifted++;
            }
            check(darker == 0, "Damp tissue at " + size + (quick ? " quick" : " live") + " darkens nothing, paper or wash (" + darker + " pixels darker)");
            check(lifted > size * 100, "Damp tissue at " + size + (quick ? " quick" : " live") + " lifts the wash (" + lifted + " pixels lighter)");
        }
    }
    static void codes() {
        java.util.List<String[]> rows = new java.util.ArrayList<>(java.util.Arrays.asList(ToolPresets.ROWS));
        rows.addAll(java.util.Arrays.asList(ToolPresets.RETIRED));
        for (String[] row : rows) {
            ModularBrush b = ModularCode.decode(row[2]);
            check(b != null, row[1] + "'s code decodes");
            check(b.style == ModularStyle.named(row[0]), row[1] + " is a " + row[0] + " brush");
            // But Hand Hatch's (cut, 2026-10-24), which reads as Hatch: HandHatchChecks.cut.
            check(ModularCode.encode(b).equals(row[2]) || HandHatchChecks.handHatch(row[2]), row[1] + "'s code round-trips");
            check(Long.parseLong(row[4]) > 0, row[1] + " keeps the seed it was rolled with");
            // A Wet preset is built by hand (PresetPictures): its seed is its code's.
            if (b.choice(BrushPart.LIFE) == PartChoice.WET_PAINT) check(Long.toString(Long.parseLong(row[4]), 36).equals(row[2].substring(row[2].lastIndexOf('.') + 1)), row[1] + "'s seed is its code's");
        }
        for (ToolPresets.Preset p : ToolPresets.all()) {
            Brush brush = p.brush();
            check(brush.name().equals(p.name) && brush.parts.style == p.style, p.name + " as a brush keeps its name and style");
            check(brush != p.brush() && brush.sameAs(p.brush()), p.name + " is a fresh copy each time, the same brush");
            check(Brush.read(brush.write()).sameAs(brush), p.name + " is stored and read back as itself");
        }
    }
    static void words() {
        // The narrowest card that shows a line: the finger's chooser's (InkWashBar), CARD_LEAST wide.
        int room = (int) (ToolsLayout.CARD_LEAST - 2 * LabText.CAPTION_INSET);
        for (ToolPresets.Preset p : ToolPresets.all()) {
            // The owner's names stay the owner's (Watercolour backrun the owner's too, 2026-11-06): a card cuts a long one with "…" (CardArt), the brush line and This Brush say it whole.
            check(LabText.width(p.line, LabText.CAPTION_SP, 0) <= room && (LabText.width(p.name, LabText.CAPTION_SP, 0) <= room || OWNERS.contains(p.name) || p.name.equals("Watercolour backrun")), p.name + " / " + p.line + " fit one line of the Nomad's card (" + room + " dp)");
            check(!p.line.isEmpty() && p.line.equals(p.line.toLowerCase(java.util.Locale.ROOT)), p.name + " has a short lower-case description");
        }
    }
    static void draws() {
        ModularBrush stump = FloatChecks.preset("Stump");
        ModularBrush.Part carry = stump.part(BrushPart.CARRY);
        check(carry.choice == PartChoice.SMUDGE && carry.get(PartDial.LOAD) == 0 && Math.abs(carry.get(PartDial.PICK) - .7) < .001 && carry.get(PartDial.LAY) == .6
                && stump.choice(BrushPart.MEDIUM) == PartChoice.CHARCOAL && WildSample.smearsMostly(stump), "Stump: Charcoal, Smear, Own paint 0, Pick up .7, Lay down .6; its loop shows over a grey band");
        ModularBrush sumi = null, clear = null;
        for (ToolPresets.Preset p : ToolPresets.all()) { if (p.name.equals("Sumi")) sumi = p.brush().parts; if (p.name.equals("Clear water")) clear = p.brush().parts; }
        check(sumi.mischief() == PartChoice.LATER_FLOAT && sumi.choice(BrushPart.CARRY) == PartChoice.OFF && sumi.choice(BrushPart.MEDIUM) == PartChoice.INK
                && sumi.choice(BrushPart.HAND) == PartChoice.PRESS_DARKER && !ModularWords.clearWater(sumi), "Sumi: Round, Ink, Smudge Off, Float, Press → darker");
        check(clear.choice(BrushPart.CARRY) == PartChoice.SMUDGE && clear.part(BrushPart.CARRY).get(PartDial.LOAD) == 0 && Math.abs(clear.part(BrushPart.CARRY).get(PartDial.PICK) - .9) < .001
                && ModularWords.clearWater(clear) && clear.part(BrushPart.LIFE).sameAs(sumi.part(BrushPart.LIFE)), "Clear water: Sumi with Smear, Own paint 0, Pick up .9");
        for (ToolPresets.Preset p : ToolPresets.all()) {
            int marked = 0;
            for (int px : WildSample.modular(p.brush().parts, 1, WildSample.Shape.LOOP, 120, 80, false)) if (px != 0xffffffff) marked++;
            // Loose hatch, the faintest, 41 then 40 since the paper's grain shows a little less (StrokePaper.SHOWS, 2026-11-02).
            check(marked >= 40, p.name + " draws its loop (" + marked + " pixels)");
        }
    }
    /** Every Basic tool's row and every retired one's. */
    static java.util.List<String[]> rows() {
        java.util.List<String[]> rows = new java.util.ArrayList<>(java.util.Arrays.asList(ToolPresets.ROWS));
        rows.addAll(java.util.Arrays.asList(ToolPresets.RETIRED));
        return rows;
    }
    /** {@code code}'s loop as the Tools page draws it, hashed. */
    static String drawn(String code) { return Integer.toHexString(java.util.Arrays.hashCode(WildSample.modular(ModularCode.decode(code), 1, WildSample.Shape.LOOP, 120, 80, false))); }
    /**
     * Every code a Basic tool had on 2026-10-31 ({@link #PINNED}, ROWS and RETIRED then; -Dpinned prints today's) is still
     * a Basic tool's or a retired one's, and draws its loop pixel for pixel as it did: retiring a Basic tool leaves a
     * slot, the library or a share code holding it drawing as before. The smudges' and waters' loops took their area's
     * dots on 2026-11-01 (explore-area, {@code WildSample.outlined}): those nine hashes are that day's.
     */
    static void pinned() {
        Set<String> now = new HashSet<>();
        for (String[] row : rows()) now.add(row[2]);
        for (String[] pin : PINNED) check(now.contains(pin[0]) && drawn(pin[0]).equals(pin[1]), pin[0] + " is still known and draws as it did");
    }
    // 49 re-pinned 2026-11-01: those on a textured paper drawn on their surfaces made whole, and the wet washes on Smooth pooling to their rim (the surface spec).
    // 61 re-pinned 2026-11-02: the paper's grain a little quieter under every mark (StrokePaper.SHOWS).
    static final String[][] PINNED = {
            {"steady-line.M00il00000000at_000zz0000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud", "b875ebbf"},
            {"press-liner.M00fy2p000000at_000zz0000000000000_0_000_1jz_85t0000000000000000nz0.1t996q7", "ae1f7a46"},
            {"quick-sketch.M00kj4z000000at_000zz8h00000000000_0_000_1jz_86200000000005q0000nz0.1s13pn4", "c8f5adaa"},
            {"toothy-line.M00fb00390000at_000zz7k00000000000_0_13x_1jz_80u0000000000000000nz0.16ebhgk", "9dcc843a"},
            {"lively-liner.M00l700000000at_000zz0000000000000_0_100_1jz_88z0000000000600000nz0.pg5zxt", "19c8fc6b"},
            {"stipple-pen.M07i0clcli0_000zz0000000000000_0_000_1jz_0000000000000000000nz0.h6q", "ec758b1f"},
            {"hatch-pen.M08at7x901zzi0_000zz0000000000000_0_000_1jz_0000000000000000000nz0.x3", "e59549c2"},
            {"hand-hatch-pen.M0ceeat901i0i0p6i0_000zz0000000000000_0_000_1jz_0000000000000000000nz0.x3", "66679f20"},
            {"neat-hatch.M0ceeat901i05ecli0_000zz0000000000000_0_000_1jz_0000000000000000000nz0.x3", "66679f20"},
            {"white-gel-pen.M00i000000000at_000zz0000000000000zz1_0_000_1jz_0000000000000000000nz0.1y9", "65036de1"},
            {"ballpoint.M00at00005e003m_000zz0000000000077_0_177_0_85ei000000000000000nz0.2ez", "f53ce89c"},
            {"gel-pen.M00wd0000000000_000zz5e00000000000_0_000_0_0000000000000000000nz0.2f0", "64252db5"},
            {"fountain-pen.M00i000at00003m_03mzzwe00000000000_0_000_0_8qz0000000000000000nz0.2f1", "9b299689"},
            {"cheap-ballpoint.M00i00000002g4l_000zzi00000000003l_0_000_1jz_8h77a00000000000000nz0.ir1", "cb0e7f56"},
            {"smooth-fountain.M00i00000000h3p_000zz1u00000000000_0_000_1jz_8ag00000000007h0000nz0.ir1", "42412168"},
            {"spirograph.M0bi010gj08huu801i08r_000zz0000000000000_0_000_0_0000000000000000000nz0.2fb", "99f547a1"},
            {"vanishing-ink.M00i000000000at_000zz0000000000000_0_000_53a_0000000000000000000nz0.2fe", "fd5c6ac8"},
            {"stamp.M0agx1r0qe_000zz0000000000000_0_000_0_8cl0000000000ee0000nz0.2gd", "47b296c1"},
            {"swell-brush.M10i0q6f40000at_000t7008h000000000_0_000_1jz_8wgft00dr0000ey0000nz0.1s13pn4", "36e87e1e"},
            {"chisel-brush.M11iteei0fxp50000at_000ur0000000000000_0_04n_1jz_8r8dj00000000ah0000nz0.1t996q7", "4ea71b05"},
            {"lean-brush.M10i0rsw80000at_000v50000000000000_0_000_1jz_8wded00jl0000ck0000nz0.69opxg", "26c4de1e"},
            {"wet-brush.M10i0d2o50000at_1ul0000000000_0_000_7i0ee_8yrfo00000000ny0000nz0.2g3", "13b2f7dc"},
            {"pooling-brush.M10i0mwmc0000at_000u5d87f000000000_0_000_1jz_8qp0000fh0000he0000nz0.ib8b3u", "c8a06d12"},
            {"springy-brush.M10ssati000003m_000zz0000000000000_0_000_0_4weatg7000000ee0000nz0.2fg", "6b8e2bba"},
            {"ink-wash-brush.M10i06f0000003t_1w85i000s0000zz2_0_d6e_2clcl000_8t00000000000000000nz0.ir1", "d1bf70dd"},
            {"split-nib.M24m0okjmi0000y00_000zz0000000000000_0_000_1jz_8lw00wk0000fq000000nz0.51j8ud", "1026b2b"},
            {"broad-nib.M20ss007700003m_000zzat00000000000_0_000_0_8at0000i000ll000000nz01.1s13pn4", "56f1fa08"},
            {"flick-nib.M225a9xi02ce60000at_000zzg6bf000000000_0_100_1jz_8sr00lj000000000000nz0.16ebhgk", "51cc2161"},
            {"chisel-nib.M21oklpi03v000000at_000zzk58o000000000_0_000_1jz_8go00u10000j2000000nz0.ib8b3u", "54402da3"},
            {"copperplate.M24g4okdci0970000_000zz0000000000000_0_24n_1jz_8hg00q4000000000000nz0.1t996q7", "3232ca65"},
            {"lemon-juice.M20i000at0000at_000zz0000000000000_0_000_6hp_1ss0000000000000000nz0.2ff", "43c899e6"},
            {"chisel-marker.M31j2eei000000000at_000zzdj005z0000000_0_000_1jz_82o00000000005u0000nz0.t4mf73", "5806606a"},
            {"round-marker.M30i000000000at_000zzde00000000000_0_071_1jz_83j00000000008b0000nz0.1ifv2mx", "43f8de62"},
            {"bleed-marker.M30i000000000at_05ezzq500000000000_0_300_1jz_83u00000000004w0000nz0.1t996q7", "399b2b6e"},
            {"felt-marker.M31j2eei000000000at_000zzbq00920000000_0_300_1jz_84u0000000000400000nz0.jjds6x", "a592a089"},
            {"wet-chisel.M31smeei000000000at_03mzzc700000000000_0_000_1jz_82h00000000003c0000nz0.1uhentb", "1ae82fac"},
            {"highlighter.M31e1zzp60000000000_1007700000000zz2_0_000_0_0000000000000000000nz0.2f2", "ed57338d"},
            {"permanent-marker.M30i0231u10003i_1hx1l1k420000_0_d1g_2clcl0js_81x0000000000000000dk0.ir1", "fb73a501"},
            {"acrylic-paint-marker.M31uomci00000000000_2zz00_0_000_1jz_00000000000000000001r0.ir1", "ce025381"},
            {"mr-sketch.M31pfzzi00000001n37_2y200_0_000_1jz_0000000000000000000nz0.ir1", "ff685a53"},
            {"colouring-pen.M30wd00000000at_1atat00000000_5cl_000_0_0000000000000000000nz0.2fc", "cecb2ec7"},
            {"fill-in.M30wd00000000at_1atat00000000_5zz_000_0_0000000000000000000nz0.2fh", "a7d240d"},
            {"h-pencil.M40i000000000at_48n00_0_2s8_1jz_87nge00000000000000nz000000000530000005000c099zz.51j8ud", "bd916894"},
            {"hb-pencil.M40i000000000at_4e551_0_1v5_1jz_8arsr00000000000000nz000000000530000005000c099zz.pg5zxt", "6e2376f0"},
            {"b-pencil.M40i0007r0000at_4g100_0_1rf_1jz_89lrc00000000000000nz000000000530000005000c099zz.vkxdda", "163e13c8"},
            {"b-pencil.M40i0006b0000at_4j700_0_3j5_1jz_8fng400000000000000nz000000000530000005000c099zz.2l8ao6", "13f00c87"},
            {"b-pencil.M40i03u000000at_4sh00_0_2m5_1jz_8h4mv00000000000000nz000000000530000005000c099zz.1pksrgx", "3bb6ae5c"},
            {"sketch-pencil.M40i000000000at_4at00_0_000_1jz_25ess00000000000000nz000000000530000005000c099zz.inw", "f90b21d2"},
            {"loose-hatch.M4ceeat901p6weuli0_4at00_0_000_1jz_25ess00000000000000nz0.inw", "6f5203b9"},
            {"another-pencil.M40i000000000at_4ip2c_0_di0_1jz_800000000zzzz000000zz000000000530000005000c099zz.inw", "5848b769"},
            {"messy-stick.M51eueei000005bd4at_3nbcw_0_2tz_1jz_8syjs00000000000000nz000000000530000005000c099zz.t4mf73", "3dd036e0"},
            {"compressed.M51wseei09z000000at_36lnd_0_1z9_1jz_8rpfm00000000000000nz000000000530000005000c099zz.1s13pn4", "729e34f6"},
            {"crisp-charcoal.M51yseei0000000c3at_315bs_0_1v3_1jz_8kvkd00000000000000nz000000000530000005000c099zz.69opxg", "6fcc9285"},
            {"soft-charcoal.M50i00000000yat_3eqlj_0_1nl_1jz_8pthh00000000000000nz000000000530000005000c099zz.51j8ud", "b99697c2"},
            {"dark-stick.M51useei06x7mc800at_3lylx_0_2xk_1jz_8lcm100000000000000nz000000000530000005000c099zz.15660dh", "cc3032a6"},
            {"blend.M50i00000000yat_3eqlj_3ssll00zz000090zz_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", "ffbf47ac"},
            {"blend-stump.M50i00000000yat_3eqlj_3ssll00zz_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", "5f75987"},
            {"stump.M50i00000000yat_3eqlj_3p6ll00_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", "42a254ef"},
            {"charcoal-dust.M51r7eei000007700at_3at90vb_0_1qz_1jz_8p6g700000000000000nz0000i0000530000005000c099zz.2yl8n", "b644bda5"},
            {"soft-wash.M60i0000000007q_1wva200000000_0_0fk_7_8la0000000000000000nz0.t4mf73", "401f671e"},
            {"wet-blooms.M60i000000000ji_1z5gk00f6e400_0_000_7js_8mx0000000000000000nz0.1s13pn4", "aa8b06fe"},
            {"backrun.M60i000000000gv_1t10000c8gj00_0_13f_7_8sl0000000000000000nz0.ib8b3u", "50d81316"},
            {"granulating.M60i000000000ac_1uk0000fh0000_0_186_7_8me0000000000000000nz0.1t996q7", "b00bb8bf"},
            {"slow-bloom.M60i000000000i6_1yh0000fc2a00_0_1g7_7ss_8j90000000000000000nz0.vkxdda", "a93c5680"},
            {"pooling-wash.M60i000000000g7_1x3at00770000_0_190_7qzll_8g70000000000000000nz0.373k3", "db30b099"},
            {"tilt-blend.M60i000000000js_1y6at00900000_0_05e_7ulee_8g70000000000000000nz0.4t449", "819b551c"},
            {"sinking-grain.M60i000000000cl_1x37700770000_0_1qz_7p65e_8g70000000000000000nz0.146as", "b391fd3d"},
            {"stipple-sponge.M67g7eewei0_1cl0000770000_0_000_1jz_0000000000000000000nz0.h6p", "b643b1e6"},
            {"wash-behind.M60i000000000at_1ulat00770000zz2_0_000_1jz_0000000000000000000nz0.11kx", "f2d5718b"},
            {"wash-comb.M65zzlli000000000_1ulat00770000_3zzll00zz_000_1jz_0000000000000000000nz0.7", "1e601f9a"},
            {"dirty-brush.M60i000000000at_1ulat00770000_3qzi00000we_000_1jz_0000000000000000000nz0.20j", "d75205f7"},
            {"water.M60i000000000at_1ulat00770000_4i0llzz_000_2g7ll0jsee00at_0000000000000000000nz0.20k", "9c9a6c8"},
            {"water-brush.M60i000000000at_1ulat00770000_4i0ll_000_2g7ll0jsee00at_0000000000000000000nz0.20k", "2957aea1"},
            {"masking-fluid.M60i000000000at_5_0_000_1jz_0000000000000000000nz0.px", "9682d650"},
            {"wax-resist.M60i00000cl00at_500_0_1i0_1jz_0000000000000000000nz0.py", "e9bf112c"},
            {"damp-tissue.M60i000000000ll_1atat00000000_4i000_000_0_0000000000000000000nz0.2fd", "cb615c1f"},
            {"line-and-wash.M70i07pmj0000wd_0pipk0000ke0000000_0_000_7_8ls0000000000000000nz0.1t996q7", "8dfd2000"},
            {"wet-ink.M70i000000000xb_0h9pe9c00nl0000000_0_000_7_8o50000000000000000nz0.51j8ud", "af0535c8"},
            {"grey-wash.M70i000000000po_1ls00j4000000_0_000_7_8q90000000000000000nz0.69opxg", "5867f600"},
            {"flat-wash.M70i08u860000vx_1gv00jp860000_0_000_7_8re0000000000000000nz0.demerg", "7d9156d3"},
            {"slow-ink-wash.M70i000nt0000q5_0ts000000cs0000000_0_000_7ss_8qi0000000000000000nz0.1s13pn4", "a082c891"},
            {"sumi.M70i000000000at_000zz0000000000000_0_000_3lli0i0_25ess00000000000000nz0.15t", "ae363def"},
            {"clear-water.M70i000000000at_000zz0000000000000_3wejs00_000_3lli0i0_25ess00000000000000nz0.12ay", "e1f91392"},
            {"comb.M75zzlli000000000_000zz0000000000000_3wejs00_000_3lli0i0_25ess00000000000000nz0.12ay", "f693a4ca"},
            {"mold-ink.M70i077at0000at_000zz0000000000000_0_000_4hsjszz_25ess00000000000000nz0.wx", "b7f5c4ad"},
            {"wet-gouache.M80b70000000054_2zz00_0_000_7_83z0000000000000000nz0.2fj", "97388cd6"},
            {"runny-gouache.M80b70000000054_2zz00_0_000_7ulll_83z0000000000000000nz0.2fk", "97388cd6"},
            {"wet-flat.M825aeeh2000000000j_2zz00_0_000_7_83p0000000000000000nz0.2fl", "35b5ad9a"},
            {"gouache-hake.M83h30zz0065_2zz00_0_000_7_80g0000000000000000nz0.2fm", "45ea36e3"},
            {"tacky-gouache.M80g30000000043_2zz00_0_000_75e_86d0000000000000000nz0.2fn", "65fb22c"},
            {"gouache-water.M80b70000000054_2zz00_4i0ll_000_7llat_83z0000000000000000nz0.2fo", "313d5364"},
            {"wet-oils.M90i0000000003k_2zz00_2llp6zz_000_7ne_8d30000ty0000000000nz0.2ft", "a6c69892"},
            {"loaded-oils.M99vleei0000000005e_2zz00_2llp6zz00js_000_7ne_1ss0000000000000000nz0.2fu", "5c6be232"},
            {"flat-oils.M925aeei0000000004h_2zz00_2llp6zz_000_7ne_8fh0000k30000000000nz0.2fv", "d647fe37"},
            {"broad-oils.M93i00zz0034_2zz00_2llp6zz_000_7ne_8j30000gw0000000000nz0.2fw", "938798ee"},
            {"tilt-oils.M90i00000000020_2zz00_0_000_7ll_8kp0000ht0000000000nz0.2fx", "5a84d8b0"},
            {"oil-glaze.M90i0000000003k_2js00_0_000_7ne_8d30000ty0000000000nz0.2fy", "4721b0bf"},
            {"chisel-pastel.Ma1j2eei000008c00at_34z5w_0_2sl_1jz_89i0000000000000000nz000000000530000005000c099zz.t4mf73", "41a07950"},
            {"soft-pastel.Ma0i000007s00at_3b98o_0_3nj_1jz_8hg0000000000000000nz000000000530000005000c099zz.51j8ud", "36ade1e2"},
            {"crumpled.Ma1r1eei000008y00at_3c678_0_4n6_1jz_8hv0000000000000000nz000000000530000005000c099zz.1h7plju", "4937586a"},
            {"crisp-pastel.Ma1vueei000005100at_343dr_0_2x2_1jz_8kv0000000000000000nz000000000530000005000c099zz.15660dh", "1ea2048c"},
            {"side-pastel.Ma0i000009r00at_34w8o_0_1ls_1jz_8hl0000000000000000nz000000000530000005000c099zz.1ifv2mx", "a59e227d"},
            {"wax-crayon.Ma0p600000000at_7ll0_0_1i0_0_0000000000000000000nz0.2f9", "d51b41d0"},
            {"crayon-scratch.Ma0p600000000at_7ll1_0_1i0_0_0000000000000000000nz0.2fa", "e330e8a5"},
            {"dry-round.Mb0i00000in0074_2k700_0_2r6_1jz_8ny0000000000000000nz0.t4mf73", "68eb7931"},
            {"scumble.Mb0i00000xd000d_2oi00_0_4f9_1jz_8ce0000000000000000nz0.1s13pn4", "6d2843"},
            {"flat-dry.Mb25aeei00000sg0067_2n200_0_2d8_1jz_8l10000000000000000nz0.51j8ud", "8ed5ec92"},
            {"dry-glaze.Mb25aeei00000lx0033_1caat00770000_0_1l5_1jz_8l40000000000000000nz0.1t996q7", "9d1825de"},
            {"grainy-wash.Mb0i00000vq0026_10rat00770000_0_1p6_1jz_8i70000000000000000nz0.15660dh", "e6f594ed"},
            {"bristle-brush.Mb6rolljsqzi0_000zz0000000000000_0_1ll_1jz_8i00000000000000000nz0.209", "5f8c9602"},
            {"wet-hake.Mc3i00zzhf59_1orat00770000_0_000_1jz_85b0000000000000000nz0.t4mf73", "5062e427"},
            {"rough-hake.Mc3i01zzkh8h_1vyat00770000_0_1i0_1jz_8c30000000000000000nz0.1s13pn4", "4ca37c6c"},
            {"cover-hake.Mc3i00mb00e8_2t52w_0_000_2clcl0js_80000zz000000000000zz0.1t996q7", "a1ed9c36"},
            {"flat-hake.Mc3i01zz9a7e_1dxat00770000_0_1i0_1jz_88w0000000000000000nz0.1ifv2mx", "4625afbb"},
            {"big-sky.Mc3i00zz896c_1sbat00770000_0_000_1jz_86n0000000000000000nz0.1uhentb", "be13dc58"},
            {"dot-tone.Me0i00000000000_60e0_0_000_0_0000000000000000000nz0.2e5", "570cd97b"},
            {"line-tone.Me0i00000000000_61_0_000_0_0000000000000000000nz0.2e6", "42b20165"},
            {"sand-tone.Me0i00000000000_62d9_0_000_0_0000000000000000000nz0.2e7", "cbb9f0e2"},
            {"gradient-tone.Me0i00000000000_63kub2_0_000_0_0000000000000000000nz0.2e8", "cb80ce42"},
            {"hatched-tone.Me0i00000000000_64qidu_0_000_0_0000000000000000000nz0.2e9", "9e849c9c"},
            {"ben-day-brush.Me1zzeezz00000000ll_0i0000000000000000zz065ji000at_0_000_0_25ess00000000000000nz0.2ef", "971af43a"},
            {"rosette-ink.Me0zz77at0000ss_0p6000000000000000zz05gm_0_000_0_3p6ll00000000000000nz0.2eg", "1339bb3a"},
            {"halftone-brush.Me0zzati00000ss_0p6000000000000000zz01b2i00090_0_000_0_3p6ll00000000000000nz0.2eh", "c3497257"},
            {"mezzotint.Me0i000000000ee_390g700zz09b2c000005e_0_1i0_0_5ee0000ssi000000000nz0.2ep", "359fd31a"},
            {"dither-pencil.Me0ll00000000at_4zz00zz03_0_1ee_0_3p6ll00000000000000nz0.2eq", "3f7157bb"},
            {"engraving-pen.Me0wdatee0000ll_0p6000000000000000zz028bc0_0_000_0_3p6ll00000000000000nz0.2er", "7632c63f"},
            {"wavy-line-pen.Me0wdatee0000ll_0p6000000000000000zz08b200_0_000_0_3p6ll00000000000000nz0.2es", "a62e9df0"},
            {"line-and-wash.M70i07pmj0000wd_0pipk0000ke0000000_0_000_1jz_8ls0000000000000000nz0.1t996q7", "69d2fef"},
            {"wet-ink.M70i000000000xb_0h9pe9c00nl0000000_0_000_1jz_8o50000000000000000nz0.51j8ud", "e06d9a05"},
            {"grey-wash.M70i000000000po_1ls00j4000000_0_000_1jz_8q90000000000000000nz0.69opxg", "3278f2b"},
            {"flat-wash.M70i08u860000vx_1gv00jp860000_0_000_1jz_8re0000000000000000nz0.demerg", "c7143f01"},
            {"slow-ink-wash.M70i000nt0000q5_0ts000000cs0000000_0_000_2picl0js_8qi0000000000000000nz0.1s13pn4", "2fe80016"},
            {"soft-wash.M60i0000000007q_1wva200000000_0_0fk_1jz_8la0000000000000000nz0.t4mf73", "87ef6bb4"},
            {"wet-blooms.M60i000000000ji_1z5gk00f6e400_0_000_1jz_8mx0000000000000000nz0.1s13pn4", "ed9ca9e"},
            {"backrun.M60i000000000gv_1t10000c8gj00_0_13f_1jz_8sl0000000000000000nz0.ib8b3u", "48f1d736"},
            {"granular.M60i000000000ac_1uk0000fh0000_0_186_1jz_8me0000000000000000nz0.1t996q7", "c4840120"},
            {"slow-bloom.M60i000000000i6_1yh0000fc2a00_0_1g7_2i7cl0js_8j90000000000000000nz0.vkxdda", "7c5e7e31"},
            {"pooling-wash.M60i000000000g7_1x3at00770000_0_190_2s29q0jsi000ul64_8g70000000000000000nz0.373k3", "47eefdfe"},
            {"tilt-blend.M60i000000000js_1y6at00900000_0_05e_2voat0js5e006400x3_8g70000000000000000nz0.4t449", "d9f5119e"},
            {"sinking-grain.M60i000000000cl_1x37700770000_0_1qz_2tii00js5e0000x3_8g70000000000000000nz0.146as", "142219ab"},
            {"drips.M60i000000000at_1y67700770000_0_05e_2ti7x0jsclllat_8g70000000000000000nz0.1rcs7", "380b3365"},
            {"wet-brush.M10i0d2o54w00at_09ls2009s000000000_0_100_1jz_8yrfo00000000ny0000nz0.16ebhgk", "948d8098"},
            {"opaque-round.M80b70000000054_2zz00_0_000_1jz_83z0000000000000000nz0.t4mf73", "97388cd6"},
            {"wide-gouache.M83h30zz0065_2zz00_0_100_1jz_80g0000000000000000nz0.51j8ud", "45ea36e3"},
            {"flat-gouache.M825aeeh2000000000j_2zz00_0_000_1jz_83p0000000000000000nz0.1uhentb", "35b5ad9a"},
            {"swell-gouache.M80g30000000043_2zz00_0_05c_1jz_86d0000000000000000nz0.1h7plju", "786f680e"},
            {"felt-gouache.M80g3000000002i_2zz00_0_300_1jz_8490000000000000000nz0.1qsy8k0", "36a480a"},
            {"impasto.M90i00000il003k_2zzh9_0_500_1jz_8d30000ty0000000000nz0.t4mf73", "e63c0f29"},
            {"oil-hake.M93i00zzrf34_2zzfl_0_500_1jz_8j30000gw0000000000nz0.51j8ud", "43878be0"},
            {"flat-bristle.M925aeei00000xa004h_2zzkm_0_500_1jz_8fh0000k30000000000nz0.69opxg", "92b8b379"},
            {"dry-oils.M90i00000px0040_2zziy_0_519_1jz_8kt0000000000000000nz0.1h7plju", "e0e686f4"},
            {"lean-oils.M90i00000sv0020_2zzug_0_59x_1jz_8kp0000ht0000000000nz0.ib8b3u", "21be8351"},
            {"hog-bristle.M96x7i0lli0i0p6_2zzll_0_5i0_1jz_8i00000000000000000nz0.20a", "9d33febd"},
            {"filled-shape.M30wd00000000at_1atat00000000_6_000_0_0000000000000000000nz0.2fi", "a7d240d"},
            {"oil-paint.M99vleei0000090005e_2zza3_2llp6zz00js_5at_1jz_1ss0000000000000000nz0.20l", "356143d2"},
    };
    /**
     * The HB pencil without Pressure Dark (the owner, 2026-11-05: "need to remove the pressure - dark adjustment so it
     * doesn't require pressure to be a normal dark shade"): a straight line at pressure .3 and .6 lays near the darkness
     * of one at full pressure, along its middle; the one before (RETIRED, Pressure Dark .8) laid half of it at .3. Still
     * kept sharp, its grain and Pressure Size kept. The PASS line's words.
     */
    static String hbPencil() {
        Brush now = ToolKits.preset("HB pencil").brush(), was = null;
        for (ToolPresets.Preset p : ToolPresets.retired()) if (p.name.equals("HB pencil")) was = p.brush();
        ModularBrush.Part hand = now.parts.part(BrushPart.HAND), before = was.parts.part(BrushPart.HAND);
        check(hand.get(PartDial.PD) == 0 && before.get(PartDial.PD) > .7, "the HB pencil's Pressure Dark 0, .8 before");
        hand.set(PartDial.PD, before.get(PartDial.PD));
        check(now.parts.key().equals(was.parts.key()) && now.parts.seed == was.parts.seed, "but for Pressure Dark, the HB pencil as before: its Pressure Size, graphite, grain and seed");
        hand.set(PartDial.PD, 0);
        check(SharpStrokes.takes(now.parts, 5, 1) && SharpStrokes.takes(now.parts, 5, .4), "the HB pencil is still kept sharp");
        double full = hbLine(now, 1), low = hbLine(now, .3), mid = hbLine(now, .6), wasFull = hbLine(was, 1), wasLow = hbLine(was, .3), wasMid = hbLine(was, .6);
        check(low >= .85 * full && mid >= .92 * full, "the HB pencil lays near its full darkness at .3 and .6: " + low + ", " + mid + " of " + full);
        check(wasLow < .6 * wasFull, "the one before laid much less at .3: " + wasLow + " of " + wasFull);
        return String.format("the HB pencil along a line lays %.0f, %.0f and %.0f (of 255) at pressure .3, .6 and 1 (%.0f, %.0f and %.0f before, Pressure Dark .8), still kept sharp", low, mid, full, wasLow, wasMid, wasFull);
    }
    /** Mean darkness (255 − tone) along the middle of a straight 5 px line of {@code b} at pressure {@code pr}, black, upright. */
    static double hbLine(Brush b, double pr) {
        ToneDocument d = InkWashLayers.blank(300, 60);
        float[][] h = new float[6][120];
        for (int i = 0; i < 120; i++) { h[0][i] = 30 + 2 * i; h[1][i] = 30; h[2][i] = (float) pr; h[3][i] = Float.NaN; h[4][i] = Float.NaN; h[5][i] = 5000 + 10 * i; }
        SharpChecks.draw(d, new WatercolorSession(), b, 5, 1, h, 1);
        double sum = 0;
        for (int y = 29; y <= 30; y++) for (int x = 60; x < 240; x++) sum += 255 - d.compositeTone(x, y);
        return sum / (2 * 180);
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
