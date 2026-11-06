package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.PartDial.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The six parts of a modular brush, in share-code order (parts.js 6–14), then the optional seventh, Later (roadmap step
 * 7, 2026-10-31): the mischief, what changes the page after the pen lifts (Float, Mold, Fading, Lemon Juice), apart from
 * Drying's how paint dries, so the two combine. Never reorder. The labels are what the lab shows (Life, Hand and Carry
 * read Drying, Control and Affects Page: Smudge until 2026-11-01, then Rework, Affects Page the owner's, 2026-11-02; Later reads
 * After, the owner, 2026-11-01; Paper reads Texture, its one dial, shown on Medium's card since the paper went to the page:
 * Surface is the notebook's and the page's word); the constant names are the codes' and stay. Pure, checked on the desktop.
 */
enum BrushPart {
    TIP("Tip"), MEDIUM("Medium"), CARRY("Affects Page", "Affects"), PAPER("Texture"), LIFE("Drying"), HAND("Control"), LATER("After");

    /** The parts every code holds, the six before Later ({@link ModularCode}). */
    static final int SIX = 6;

    /**
     * The order the lab shows the parts in, reading left to right, top to bottom, two to a row: the order a mark is made
     * (the owner, 2026-11-01), Control, Tip, Medium, Affects Page, Drying. No Paper: its Texture is a dial on Medium's card,
     * the paper the page's Surface (the surface spec, 2026-11-01). Only what the lab shows; the codes keep {@link
     * #values()}'s order.
     */
    static final List<BrushPart> SHOWN = Collections.unmodifiableList(Arrays.asList(HAND, TIP, MEDIUM, CARRY, LIFE));
    /** {@link #SHOWN}, then After: the lab's six modules, two to a row, After's on every brush since Drying stopped offering it (2026-11-01). */
    static final List<BrushPart> SHOWN_LATER = Collections.unmodifiableList(Arrays.asList(HAND, TIP, MEDIUM, CARRY, LIFE, LATER));

    /** Its name, and the shorter one a module too narrow for it shows ({@link LabLayout#name}; the Nomad's, the owner, 2026-11-02). */
    final String label, shortLabel;

    BrushPart(String label) { this(label, label); }
    BrushPart(String label, String shortLabel) { this.label = label; this.shortLabel = shortLabel; }

    /** This part's choices, in code order. */
    List<PartChoice> choices() { return Tables.CHOICES.get(this); }

    /** The dials a swap keeps (parts.js 142 KEEPS). */
    Set<PartDial> keeps() { return Tables.KEEPS.get(this); }

    /** Built on first use, so the enums that refer to each other are all made first. */
    private static final class Tables {
        static final Map<BrushPart, List<PartChoice>> CHOICES = new EnumMap<>(BrushPart.class);
        static final Map<BrushPart, Set<PartDial>> KEEPS = new EnumMap<>(BrushPart.class);
        static {
            for (BrushPart p : BrushPart.values()) {
                List<PartChoice> list = new ArrayList<>();
                for (PartChoice c : PartChoice.values()) if (c.part == p) list.add(c);
                CHOICES.put(p, Collections.unmodifiableList(list));
            }
            keep(TIP, EnumSet.of(WEIGHT, TAPER_IN, TAPER_OUT, DRY, WOBBLE, SOFT, ANGLE, FLICK));
            keep(MEDIUM, EnumSet.of(POOL, BLEED, BLOOM, SPLATTER, FLING));
            keep(CARRY, EnumSet.of(PLOAD, PMIN));
            keep(PAPER, EnumSet.of(TOOTH));
            keep(LIFE, EnumSet.of(PartDial.DRYING, RUNS, G_POOL, SINK));
            keep(LATER, EnumSet.noneOf(PartDial.class));
            keep(HAND, EnumSet.of(GAIN, DIR, P_DRIVE, S_DRIVE, T_DRIVE, SHIFT, SHIFT_ORDER, SHIFT_GREYS, SHIFT_BAR, SHIFT_ANY, SHIFT_ANY2, GRADIENT, GRAD_TO, GRAD_LEN, SIDE_SHADE, STEADY_HAND, LONG_STICK));
        }
        private static void keep(BrushPart p, Set<PartDial> dials) { KEEPS.put(p, Collections.unmodifiableSet(dials)); }
    }
}
