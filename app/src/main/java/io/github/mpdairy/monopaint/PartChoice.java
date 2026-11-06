package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.PartDial.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Every choice of every part of a modular brush, in code order (parts.js 79–120): its dials, range, default and
 * label overrides (display words: docs/TEXT-STYLE.md; the key is the code's), macro dial and words, and a hand preset's cells. Never reorder a part's choices or a
 * choice's dials: share codes are written in this order. Append only. Pure, checked on the desktop.
 */
enum PartChoice {
    // Tip
    ROUND(BrushPart.TIP, "round", "Round", WEIGHT, "fine", "broad",
            WEIGHT, TAPER_IN, TAPER_OUT, DRY, WOBBLE, SOFT, FLICK, WEAR),
    CHISEL(BrushPart.TIP, "chisel", "Chisel", ASPECT, "fine", "broad",
            ASPECT, ANGLE, WEIGHT, TAPER_IN, TAPER_OUT, DRY, WOBBLE, SOFT, FLICK, WEAR),
    FLAT_NIB(BrushPart.TIP, "flat", "Flat Nib", ASPECT, "fine", "broad",
            ASPECT, ANGLE, WEIGHT, TAPER_IN, TAPER_OUT, DRY, WOBBLE, SOFT, FLICK),
    /** A wide flat head swept as a ribbon; it pivots on tight turns. */
    HAKE(BrushPart.TIP, "hake", "Hake", WEIGHT, "fine", "broad",
            WEIGHT, HEAD_TURN, ANGLE, DRY, SOFT, FLICK),
    SPLIT_NIB(BrushPart.TIP, "split", "Split Nib", GAP, "closed", "open",
            GAP, ASPECT, ANGLE, WEIGHT, TAPER_IN, TAPER_OUT, DRY, FLICK),
    /** Two to five lines that spread with pressure (the prototype's Multi-line). */
    MULTI_LINE(BrushPart.TIP, "multi", "Rake", REACH, "close", "wide",
            COUNT, REACH, WEIGHT, TAPER_IN, TAPER_OUT, DRY, WOBBLE, FLICK),
    /** Hairs that follow each other's trails, split and rejoin. */
    BRISTLES(BrushPart.TIP, "bristles", "Bristle", SPLAY, "tight", "splayed",
            HAIRS, SPLAY, COHESION, DRYNESS, WEIGHT, INK_LOAD, FLICK),
    /** Dots scattered along the stroke; pressure packs them closer (the new modules spec, {@link StippleStamper}). */
    STIPPLE(BrushPart.TIP, "stipple", "Stipple", DOT, "fine", "coarse",
            DOT, SCATTER, JITTER, WEIGHT, BUILD_UP, FLICK),
    /** Parallel lines instead of a solid mark; pressure crosses them a second and a third way ({@link HatchStamper}). */
    HATCH(BrushPart.TIP, "hatch", "Hatch", PITCH, "tight", "open",
            PITCH, LINE_W, HANGLE, HEAD_TURN, LAYERS, WEIGHT, FLICK),
    /**
     * A filbert (the oil paint spec; MonoPaint's): a rounded oval whose broad side lies across the pen's lean, so it
     * follows tilt (Nib Angle without a reading). With Smudge's Paint Load on it is a crescent hollowed on the side it
     * moves toward, so a first touch is a thin sliver ({@link BodyStamper}). Tip choice 9, so older codes never hold it.
     */
    FILBERT(BrushPart.TIP, "filbert", "Filbert", ASPECT, "slim", "round",
            ASPECT, ANGLE, WEIGHT, TAPER_IN, TAPER_OUT, DRY, WOBBLE, SOFT, FLICK),
    /**
     * Stamp (mischief-lab-2.html's Stamp, the owner's "a regular brush … with a potato stamp nib"): its nib a piece of the
     * page cut with a lasso ({@link StampNib}, Cut Stamp), printed every Spacing along the line, potato-stamp inked
     * ({@link StampStamper}). Its Head Follows is Turns (On: the nib turns with the line). Tip choice 10, so older codes
     * never hold it; never rolled, as it needs a cut.
     */
    STAMP(BrushPart.TIP, "stamp", "Stamp", STAMP_SPACING, "trail", "prints",
            WEIGHT, STAMP_SPACING, HEAD_TURN, INK_FADE, FLICK),
    /**
     * Spiro (mischief-lab-2.html's Spirograph, the owner's "more like the real toy"): a curve from the toy's wheels and
     * its kin, riding the line or, in Ring, round a ring pinned where the stroke starts, its wheel turned by circling
     * the pen ({@link SpiroStamper}). Control drives its shape ({@link PartDial#DRIVES}). Tip choice 11, so older codes
     * never hold it; never rolled.
     */
    SPIRO(BrushPart.TIP, "spiro", "Spiro", WEIGHT, "small", "big",
            WEIGHT, SPIRO_MODE, CURVE, LOOPS, TEETH, WHEEL, HOLE, PETALS, RATIO, PHASE, FADES, FLICK),
    /**
     * Hand Hatch (the owner's "handmade crosshatch", 2026-10-21): hatch strokes laid one by one as a hand lays them, not a
     * lattice revealed ({@link HandHatchStamper}); pressure, going over a place again or holding the pen there adds a
     * direction, then tighter rows, up to three directions. Hatch's dials but Layers, and the stroke's own: Line Length, Wobble, Taper Out. Tip choice 12, so older codes never hold it; never rolled.
     */
    HAND_HATCH(BrushPart.TIP, "handhatch", "Hand Hatch", PITCH, "tight", "open",
            PITCH, LINE_W, HANGLE, HEAD_TURN, HLEN, HWOB, HENDS, WEIGHT, FLICK),
    // explore-nibs: four nibs that are each another object in the hand, Tip choices 13 to 16, so older codes never hold
    // them; never rolled.
    /** A ruling pen's blade and its ink between: a hairline upright, a broad, skippy band laid over; runs dry, blots ({@link RulingPen}). */
    RULING(BrushPart.TIP, "ruling", "Ruling Pen", INK_LOAD, "dry", "loaded",
            WEIGHT, INK_LOAD, FLICK),
    /** A stick dipped in ink: a gnarled end, a round start (a blob overloaded), then dry and scratchy ({@link TwigStick}). */
    TWIG(BrushPart.TIP, "twig", "Twig", INK_LOAD, "dry", "loaded",
            WEIGHT, INK_LOAD, WOBBLE, FLICK),
    /** A fan brush: hair tips in an arc, more of them down and splayed as you press ({@link FanBrush}). */
    FAN(BrushPart.TIP, "fan", "Fan", SPLAY, "tight", "splayed",
            HAIRS, SPLAY, WEIGHT, DRYNESS, FLICK),
    /** A printmaker's brayer: a rolled band whose ink, and what it picks up, comes round again ({@link Brayer}). */
    ROLLER(BrushPart.TIP, "roller", "Brayer", PICKUP, "clean", "reprints",
            WEIGHT, INK_LOAD, PICKUP, FLICK),
    // Medium
    /** A line; Wet loosens it into a wash around it. */
    INK(BrushPart.MEDIUM, "ink", "Ink", WET, "dry", "wet",
            WET, LINE, POOL, SPLATTER, BLEED, BLOOM, LINES, OFFSET, CHAOS, ERASABLE, M_BLEND, SCREEN, CELL, S_ANGLE, MIS, S_GAIN, UNEVEN, FLING, SOLUBLE),
    /** Transparent paint: it only darkens what's under it. */
    WASH(BrushPart.MEDIUM, "wash", "Wash", WET, "flat", "wet",
            WET, POOL, BLEED, BLOOM, BACKRUN, LINE, ERASABLE, M_BLEND, SCREEN, CELL, S_ANGLE, MIS, S_GAIN, UNEVEN, FLING),
    /** Opaque body colour (the prototype's Cover): light covers dark; thick paint (Impasto) is lit from the upper left. */
    COVER(BrushPart.MEDIUM, "cover", "Opaque", PartDial.COVER, "thin", "opaque",
            PartDial.COVER, IMPASTO, ERASABLE, M_BLEND, SCREEN, CELL, S_ANGLE, MIS, S_GAIN, UNEVEN, FLING),
    /** Dry dust: it catches the high points first; fuzz and specks from the stick, not the paper. */
    CHARCOAL(BrushPart.MEDIUM, "charcoal", "Charcoal", SPREAD, "crisp", "messy",
            SPREAD, HOLD, DUST, ERASABLE, M_BLEND, SCREEN, CELL, S_ANGLE, MIS, S_GAIN, UNEVEN, FLING, SOLUBLE),
    /** Pencil, 6H to 8B: fills the texture with each pass. */
    GRAPHITE(BrushPart.MEDIUM, "graphite", "Graphite", GRADE, "hard", "soft",
            GRADE, SPREAD, ERASABLE, M_BLEND, SCREEN, CELL, S_ANGLE, MIS, S_GAIN, UNEVEN, FLING, SOLUBLE),
    /**
     * Masking fluid or wax (the new modules spec): laid as a crisp ink line onto the page's masking fluid, which nothing
     * takes on; its one dial, Erasable, is how much of it the eraser lifts (1, masking fluid; off, wax, which stays as a
     * faint sheen and catches only the paper's tooth). No Blend of its own.
     */
    RESIST(BrushPart.MEDIUM, "resist", "Resist", ERASABLE, "wax", "fluid",
            ERASABLE),
    /**
     * Tone, shown as Screentone (the mischief modules spec, Stage 1): the brush lays a fill, and what shows is a
     * pattern at its Darkness ({@code ttone}) over it, in black dots on whatever is there ({@link PrintScreen}); the eraser scrapes it in
     * streaks. Laid as a crisp ink line on the ink layer, always erasable. Medium choice 6, so older codes never hold it.
     */
    TONE(BrushPart.MEDIUM, "tone", "Screentone", TTONE, "light", "dark",
            PATTERN, TTONE, CELL, S_ANGLE),
    /**
     * Crayon (mischief-lab-2.html's fourth pad): wax on the paper's tooth, a line medium beside Charcoal. Light pressure
     * catches only the grain's peaks, hard pressure goes solid; wax already there lifts the surface, so layers build
     * up; the edge is ragged, fixed to the page. Its wax goes on the page's masking fluid too, so washes slide off it
     * ({@link ModularStroke}); Scratch scrapes back to white. Medium choice 7, so older codes never hold it; never rolled.
     */
    CRAYON(BrushPart.MEDIUM, "crayon", "Crayon", WAX, "chalky", "waxy",
            WAX, SCRATCH),
    /**
     * Vector Ink (the owner, 2026-11-04: "yes add it to the medium list"): a smooth line kept as lines, sharp at any zoom
     * ({@link VectorInk}); its size, grey and Control's pressure to size alone, the parts it can't draw sharp set aside
     * ({@link VectorInk#tidy}). Medium choice 8, so older codes never hold it; never rolled. No dials.
     */
    VECTOR(BrushPart.MEDIUM, "vector", "Vector Ink", null, null, null),
    // Carry, shown as Smudge
    OFF(BrushPart.CARRY, "off", "Off", null, null, null),
    /** Takes paint off as it goes, like a damp brush or a tissue. */
    LIFT(BrushPart.CARRY, "lift", "Lift", PICK, "gentle", "strong",
            PICK, LAY),
    /** Drags the paint beneath along: wet into wet. */
    DRAG(BrushPart.CARRY, "drag", "Drag", PICK, "clean", "smeary",
            PICK, LAY, LOAD, KEEPS, PLOAD, PMIN),
    /** Mostly smears what's there, like a finger or a stump. */
    SMUDGE(BrushPart.CARRY, "smudge", "Smear", PICK, "clean", "smeary",
            PICK, LAY, LOAD, SOFTEN, KEEPS, PLOAD, PMIN, PRESS),
    /**
     * Clear water (the wet smudge spec): lays only water, no paint of its own. It lifts what it touches as Lift does
     * (Pick Up Paint), lets Lay Down Paint of what it lifted run out through the wet it leaves, and that wet dries as
     * its Drying says: a Slow one keeps settling, and runs downhill on a tilted page ({@link ClearWater}). On the
     * water (Float) it is Float's clear water. Smudge's fifth choice, so older codes never hold it.
     */
    CLEAR_WATER(BrushPart.CARRY, "water", "Clear Water", PICK, "gentle", "strong",
            PICK, LAY, PRESS),
    /**
     * Stay in Lines (mischief-lab-2.html's first pad, "Stay in the lines"): the wet paint treats the crisp ink lines as
     * walls, like a colouring book. It keeps to the shape it starts in, shut in by the selected pair's ink layer's lines
     * ({@link FillRegion}, Fill's), and floods on through it past the brush as far as Flood says ({@link StayIn}). Soft
     * marks and washes never hold it. Smudge's sixth choice, so older codes never hold it; never rolled, never a
     * variation's swap.
     */
    STAY_IN(BrushPart.CARRY, "stayin", "Stay in Lines", FLOOD, "brush", "fills",
            FLOOD),
    /**
     * Filled Shape (the owner, 2026-10-23: "draw a filled shape", as a brush): the stroke is the outline, drawn as the
     * brush draws; at the lift it closes, end to start, and the area inside fills with the same brush ({@link
     * FilledShape}). Smudge's, beside Stay in Lines, as both decide where the paint goes and neither moves paint along.
     * No dials. Smudge's seventh choice, so older codes never hold it; never rolled, never a variation's swap.
     */
    SHAPE(BrushPart.CARRY, "shape", "Filled Shape", null, null, null),
    /**
     * Scrape (the module gaps, 2026-10-31: a palette knife, sgraffito): a flat blade dragged edge first through the paint
     * already there, laying none of its own. How wet the paint is under it decides what it does ({@link Carry#scrape}):
     * wet paint is pushed aside into dark ridges at the blade's ends, its track left lighter; damp or settling paint is
     * scraped back to pale paper; dry marks barely change. The hand decides the blade: upright, its tip scratches a fine
     * line; laid over, its flat scrapes as wide as the brush; pressure sets how deep. Smudge's eighth choice, so older
     * codes never hold it; never rolled, never a variation's swap.
     */
    SCRAPE(BrushPart.CARRY, "scrape", "Scrape", PICK, "light", "deep",
            PICK),
    /**
     * Reprint (the owner's "every look lives in a brush", 2026-11-01: a brush meets the page drawing, making ink and
     * brushing over marks already there): wherever it passes, the marks already on the selected pair are laid again in
     * this brush's own print, its Print Shading and Paper, each pixel from what it was at pen-down, so going back and
     * forth never builds up ({@link Carry#reprint}). Pressure sets how much (a light touch half reprints); without a
     * Print Shading it reprints as toner does, a hard threshold. It lays no paint of its own. Smudge's ninth choice, so
     * older codes never hold it; no dials; never rolled, never a variation's swap.
     */
    REPRINT(BrushPart.CARRY, "reprint", "Reprint", null, null, null),
    // Paper: the index is BrushTexture's ordinal; Plain shows as Smooth
    PLAIN(BrushPart.PAPER, "plain", "Smooth"), COLD_PRESS(BrushPart.PAPER, "cold press", "Cold Press"), LAID(BrushPart.PAPER, "laid", "Ribbed"),
    FELT(BrushPart.PAPER, "felt", "Felt"), CRUMPLED(BrushPart.PAPER, "crumpled", "Crumpled"), CANVAS(BrushPart.PAPER, "canvas", "Canvas"),
    /**
     * The owner's eight photo papers (2026-10-22), Paper choices 6 to 13, so older codes never hold them; never rolled,
     * never a variation's swap ({@link ModularFeatures#seedOnly}): chosen in Brush Lab, kept by codes.
     */
    COTTON(BrushPart.PAPER, "watercolour", "Cotton"), BUMPY(BrushPart.PAPER, "rough", "Bumpy"), UNRYU(BrushPart.PAPER, "unryu", "Unryu"),
    LIBRARY(BrushPart.PAPER, "library", "Library"), KRAFT(BrushPart.PAPER, "kraft", "Kraft"), OLD_BUFF(BrushPart.PAPER, "old buff", "Vintage"),
    BLUE_FIBRE(BrushPart.PAPER, "blue fibre", "Craft"), FINE_SPECK(BrushPart.PAPER, "fine speck", "Fine"),
    // Life, shown as Drying: Instant (none), Quick (fast-wet), Slow (drying), Float, Mold (halo), Fading (fade), Lemon Juice (lemon) and Wet (wetpaint); the keys and names are the codes'
    // Float, Mold, Fading and Lemon Juice are retired (2026-10-31) into Later ({@link #later}): their codes still read, as
    // Drying and Later, and a brush that is one of them is written as it was (ModularBrush#joined).
    /** Dry the moment the nib passes. */
    NONE(BrushPart.LIFE, "none", "Instant", null, null, null),
    /** Only the last few mm behind the nib (its wet edge) change while the pen is down; nothing after lift. */
    FAST_WET(BrushPart.LIFE, "wet", "Quick", WINDOW, "short", "long",
            WINDOW, RUNS, G_POOL, SINK),
    /** Settles as you lift: tails re-taper, washes settle. A transparent wash with Settle keeps settling for its drying time after. */
    DRYING(BrushPart.LIFE, "dry", "Slow", PartDial.DRYING, "short", "long",
            PartDial.DRYING, SETTLE, TAKES, PLUME, RUNS, DRIPS, G_POOL, SINK, BLEND),
    /**
     * Suminagashi (the Float spec): the paint floats on water as crisp rings until its floating time has passed since
     * the last touch, then prints ({@link FloatBath}). Drying's fourth choice, so older codes never hold it.
     */
    FLOAT(BrushPart.LIFE, "float", "Float", F_SPREAD, "small", "large",
            PartDial.DRYING, F_SPREAD, DRIFT),
    /**
     * Halo, shown as Mold (the new modules spec; the owner's name for it, 2026-10-18): laid as Slow lays it, then veins
     * reach from its edge to nearby marks and bridge them while it is wet, and set ({@link HaloGrowth}). Drying's fifth
     * choice, so older codes never hold it; its key stays the codes' {@code halo}.
     */
    HALO(BrushPart.LIFE, "halo", "Mold", H_REACH, "short", "long",
            PartDial.DRYING, H_REACH, GROWTH),
    /**
     * Fading (the mischief modules spec, Stage 3): laid as Quick lays it, then the mark fades away unevenly, thin parts
     * first, over its Fades In, until it is gone from the page and the file ({@link FadingInk}). Drying's sixth choice,
     * so older codes never hold it.
     */
    FADE(BrushPart.LIFE, "fade", "Fading", FADE_IN, "fast", "slow",
            FADE_IN),
    /**
     * Lemon Juice: laid as Quick lays it, it shows a faint trace that darkens in unevenly over its Dries In, then is an
     * ordinary dried mark ({@link FadingInk}). Drying's seventh choice.
     */
    LEMON(BrushPart.LIFE, "lemon", "Lemon Juice", DRY_IN, "fast", "slow",
            DRY_IN),
    /**
     * Wet (the wet paint spec, docs/archive/superpowers/specs/2026-10-22-wet-paint-design.md; the owner's Option A, 2026-10-22):
     * opaque paint laid into MonoPaint's wet canvas ({@link WetWatercolor}, upstream 8743286): final under the pen, it
     * mixes into the wet paint under it, evens out with its wet neighbours and runs downhill on a tilted page, and stays
     * wet across strokes until the droplet's tap dries it or the page is put away ({@link WetCanvas}). Stays Wet and
     * Runs Downhill. Drying's eighth choice, so older codes never hold it; never rolled.
     */
    WET_PAINT(BrushPart.LIFE, "wetpaint", "Wet", STAYS_WET, "short", "long",
            STAYS_WET, RUNS),
    // Hand, shown as Control: every choice has the nine cells, gain and dir; all but Custom are presets of cells
    STEADY(BrushPart.HAND, "steady", "Steady"), PRESS_WIDER(BrushPart.HAND, "wider", "Press Wide"),
    PRESS_DARKER(BrushPart.HAND, "darker", "Press Dark"), PRESS_BOTH(BrushPart.HAND, "both", "Press Both"),
    BRUSH_HAND(BrushPart.HAND, "brush", "Springy"), TILT_SIDE(BrushPart.HAND, "side", "Tilt Shade"),
    TILT_NIB(BrushPart.HAND, "nib", "Tilt Turn"), QUICK_THIN(BrushPart.HAND, "quick", "Flick"), CUSTOM(BrushPart.HAND, "custom", "Custom"),
    // Later (roadmap step 7): the mischief, apart from how the paint dries, so it combines with Drying ({@link #goesWith}).
    // Drying's Float, Mold, Fading and Lemon Juice are retired into these: their codes read as Drying and Later
    // (ModularBrush#split) and are written as before (ModularBrush#joined). The keys are the old choices'.
    /** Nothing changes after the pen lifts: every brush's but these. */
    LATER_OFF(BrushPart.LATER, "off", "Off", null, null, null),
    /** Float: the paint floats on water as rings for its Floating Time, then prints ({@link FloatBath}). On Quick only. */
    LATER_FLOAT(BrushPart.LATER, "float", "Float", F_SPREAD, "small", "large",
            FLOAT_TIME, F_SPREAD, DRIFT),
    /** Mold: veins reach to nearby marks and bridge them while it is wet, for Slow's Drying Time ({@link HaloGrowth}). On Slow only. */
    LATER_MOLD(BrushPart.LATER, "halo", "Mold", H_REACH, "short", "long",
            H_REACH, GROWTH),
    /** Fading: the mark fades away unevenly over its Fades In ({@link FadingInk}). On Instant, Quick or Slow. */
    LATER_FADE(BrushPart.LATER, "fade", "Fading", FADE_IN, "fast", "slow",
            FADE_IN),
    /** Lemon Juice: a faint trace that darkens in unevenly over its Dries In ({@link FadingInk}). On Instant, Quick or Slow. */
    LATER_LEMON(BrushPart.LATER, "lemon", "Lemon Juice", DRY_IN, "fast", "slow",
            DRY_IN),
    // Later's media (2026-10-31, the owner's "useful creative tools, possibly replicating physical media"): each changes
    // the mark after the lift the way the real technique does, over its time ({@link LaterMedia}, {@link FadingInk}).
    // Appended, never retired Drying choices: an older version reads one as Off.
    /** Salt on a wet wash: pale starbursts open as it dries. On Quick or Slow (it needs wet paint). */
    LATER_SALT(BrushPart.LATER, "salt", "Salt", GRAINS, "few", "many",
            GRAINS, DRY_IN),
    /** Plastic wrap pressed on a wet wash: dark crinkled veins, paler facets, as it dries. On Quick or Slow. */
    LATER_WRAP(BrushPart.LATER, "wrap", "Plastic Wrap", CRINKLE, "fine", "coarse",
            CRINKLE, DRY_IN),
    /** Feathering, ink on soft paper: fine threads wick out of its edges along the paper's fibres. On Instant, Quick or Slow. */
    LATER_FEATHER(BrushPart.LATER, "feather", "Feathering", THREADS, "short", "long",
            THREADS, SPREAD_IN),
    /** Foxing, an old book's page: small grey spots creep in near and on the mark over minutes. On Instant, Quick or Slow. */
    LATER_FOX(BrushPart.LATER, "fox", "Foxing", SPOTS, "few", "many",
            SPOTS, DRY_IN),
    // Later's print, crafts and ageing (2026-11-01, the module gaps survey): appended the same way.
    /** Letterpress: the mark as type pressed into paper, a darker squeezed rim, a mottled middle, starved where the paper dips. Instant, Quick or Slow. */
    LATER_PRESS(BrushPart.LATER, "letterpress", "Letterpress", IMPRESSION, "light", "deep",
            IMPRESSION, SPREAD_IN),
    /** Torn Paper: the mark becomes a scrap of paper in its own grey, torn and glued down, a white fibrous edge round it. Instant, Quick or Slow. */
    LATER_TORN(BrushPart.LATER, "torn", "Torn Paper", TEAR, "neat", "ragged",
            TEAR, SPREAD_IN),
    /** Cross-Stitch: the mark becomes rows of little X stitches on a fabric grid fixed to the page, in its greys. Instant, Quick or Slow. */
    LATER_STITCH(BrushPart.LATER, "stitch", "Cross-Stitch", STITCH, "small", "large",
            STITCH, DRY_IN),
    /** Cracks: thick paint crazes as it ages, a fine network of pale cracks, more where it lies darker and thicker. Instant, Quick or Slow. */
    LATER_CRACKS(BrushPart.LATER, "cracks", "Cracks", CRACKS, "few", "many",
            CRACKS, DRY_IN),
    /** Tarnish: metalpoint's pale grey line darkens unevenly over minutes, to a mid grey at most. Instant, Quick or Slow. */
    LATER_TARNISH(BrushPart.LATER, "tarnish", "Tarnish", TARNISH, "light", "deep",
            TARNISH, DRY_IN);

    /**
     * Later's choices as Brush Lab shows them (2026-11-01, thirteen and Off): Off and the mischief, then by the kind of
     * technique, paint media (Salt, Plastic Wrap, Feathering), print (Letterpress), age (Foxing, Cracks, Tarnish) and
     * craft (Torn Paper, Cross-Stitch), all in the tall tray ({@link LabLayout#tallSwap}; two pages until 2026-11-06). Codes keep the enum's order.
     */
    static final List<PartChoice> LATER_SHOWN = Collections.unmodifiableList(Arrays.asList(LATER_OFF, LATER_FLOAT, LATER_MOLD, LATER_FADE, LATER_LEMON,
            LATER_SALT, LATER_WRAP, LATER_FEATHER, LATER_PRESS, LATER_FOX, LATER_CRACKS, LATER_TARNISH, LATER_TORN, LATER_STITCH));

    final BrushPart part;
    /** The prototype's key. */
    final String key, label;
    /** The dial a single macro slider moves, and its two ends' words (Stage 2); null for Off and None. Display labels: the key is the code's. */
    final PartDial macro;
    final String[] words;
    /** In code order. */
    final List<PartDial> dials;
    private final Map<PartDial, double[]> ranges = new EnumMap<>(PartDial.class);
    private final Map<PartDial, Double> defaults = new EnumMap<>(PartDial.class);
    private final Map<PartDial, String> labels = new EnumMap<>(PartDial.class);
    private final Map<PartDial, String[]> options = new EnumMap<>(PartDial.class);
    /** A hand preset's cells (unset cells are 0); null for Custom and for other parts. */
    private Map<PartDial, Double> cells;

    PartChoice(BrushPart part, String key, String label, PartDial macro, String low, String high, PartDial... dials) {
        this.part = part; this.key = key; this.label = label; this.macro = macro;
        this.words = low == null ? null : new String[]{low, high};
        this.dials = Collections.unmodifiableList(Arrays.asList(dials));
    }
    /** A paper (parts.js 102: tooth, smooth to rough) or a hand (parts.js 108–120: the nine cells, gain and dir; steady to responsive). */
    PartChoice(BrushPart part, String key, String label) {
        this(part, key, label, part == BrushPart.HAND ? GAIN : TOOTH, part == BrushPart.HAND ? "steady" : "smooth",
                part == BrushPart.HAND ? "responsive" : "rough",
                part == BrushPart.HAND ? handDials() : new PartDial[]{TOOTH});
    }
    /**
     * parts.js HAND_DIALS: the nine cells, gain and dir; then Tilt range, which the prototype doesn't have; then the
     * Gravity Lab's page-tilt cells (in its codes they follow dir: there a page-tilt code reads differently); then a
     * Spiro's drives; then Grey Shift and its own; then Gradient and its own; then Tilt Shading; then Steady Hand and Long Stick.
     */
    private static PartDial[] handDials() {
        PartDial[] dials = Arrays.copyOf(HAND_CELLS, HAND_CELLS.length + 6 + DRIVES.length + SHIFTS.length + GRADIENTS.length + 3);
        dials[HAND_CELLS.length] = GAIN; dials[HAND_CELLS.length + 1] = DIR; dials[HAND_CELLS.length + 2] = TILT_RANGE;
        System.arraycopy(PAGE_CELLS, 0, dials, HAND_CELLS.length + 3, PAGE_CELLS.length);
        // A Spiro's drives, last of all.
        System.arraycopy(DRIVES, 0, dials, HAND_CELLS.length + 6, DRIVES.length);
        // Grey Shift and its own, after them.
        System.arraycopy(SHIFTS, 0, dials, HAND_CELLS.length + 6 + DRIVES.length, SHIFTS.length);
        // Gradient and its own, after them.
        System.arraycopy(GRADIENTS, 0, dials, HAND_CELLS.length + 6 + DRIVES.length + SHIFTS.length, GRADIENTS.length);
        // Tilt Shading, after them; then Steady Hand and Long Stick.
        dials[dials.length - 3] = SIDE_SHADE;
        dials[dials.length - 2] = STEADY_HAND;
        dials[dials.length - 1] = LONG_STICK;
        return dials;
    }

    static {
        range(CHISEL, ASPECT, .3, .55); def(CHISEL, ASPECT, .42);
        range(FLAT_NIB, ASPECT, .14, .28); def(FLAT_NIB, ASPECT, .2);
        def(HAKE, ANGLE, 75); def(HAKE, DRY, .35); def(HAKE, SOFT, .2);
        // Hatch's head turn says whether its lines keep their angle on the page (fixed, the default) or follow the stroke.
        def(HATCH, HEAD_TURN, 1); label(HATCH, HEAD_TURN, "Lines Follow");
        def(HAND_HATCH, HEAD_TURN, 1); label(HAND_HATCH, HEAD_TURN, "Lines Follow");
        range(SPLIT_NIB, ASPECT, .3, .55); def(SPLIT_NIB, ASPECT, .42);
        def(TWIG, WOBBLE, .6); def(FAN, HAIRS, 14); def(FAN, DRYNESS, .3);
        range(FILBERT, ASPECT, .3, .55); def(FILBERT, ASPECT, .5); def(FILBERT, SOFT, .15);
        label(STAMP, HEAD_TURN, "Turns"); options(STAMP, HEAD_TURN, "On", "Off");
        def(WASH, WET, .85); def(WASH, LINE, 0); def(WASH, POOL, .3); def(WASH, BLOOM, .2);
        label(WASH, POOL, "Dark Edge");
        label(CHARCOAL, HOLD, "Density");
        // Blend's first option is the medium's own way: Opaque covers (its Opacity says how much), every other darkens.
        options(COVER, M_BLEND, "Cover", "White", "Behind", "Invert");
        def(GRAPHITE, SPREAD, 0);
        def(LIFT, PICK, .2); def(LIFT, LAY, .3);
        def(DRAG, PICK, .6); def(DRAG, LAY, .7); def(DRAG, LOAD, .85);
        def(FLOAT, PartDial.DRYING, .6); label(FLOAT, PartDial.DRYING, "Floating Time");
        def(HALO, PartDial.DRYING, .55);
        // Every Later's time starts at its least, so the change shows at once (the owner, 2026-11-06: "down to 1s"); Foxing's at 10 s.
        def(LATER_FOX, DRY_IN, 10); label(LATER_FOX, DRY_IN, "Ages In");
        label(LATER_PRESS, SPREAD_IN, "Prints In");
        label(LATER_TORN, SPREAD_IN, "Tears In");
        label(LATER_STITCH, DRY_IN, "Stitches In");
        label(LATER_CRACKS, DRY_IN, "Ages In");
        label(LATER_TARNISH, DRY_IN, "Tarnishes In");
        def(SMUDGE, PICK, .7); def(SMUDGE, LAY, .55); def(SMUDGE, LOAD, .3);
        def(CLEAR_WATER, PICK, .5); def(CLEAR_WATER, LAY, .6);
        label(CLEAR_WATER, PRESS, "Press to Lift");
        def(SCRAPE, PICK, .7); label(SCRAPE, PICK, "Depth");
        for (PartChoice paper : values()) if (paper.part == BrushPart.PAPER) def(paper, TOOTH, paper == PLAIN ? 0 : .5);
        cells(STEADY);
        cells(PRESS_WIDER, PS, .8);
        cells(PRESS_DARKER, PD, .8, PS, .15);
        cells(PRESS_BOTH, PS, .7, PD, .6);
        cells(BRUSH_HAND, PS, .9, PD, .3, PW, .45, SS, .4);
        cells(TILT_SIDE, PS, .4, TS, .8, TD, .5);
        cells(TILT_NIB, PS, .4, TW, .9, PW, .4);
        cells(QUICK_THIN, PS, .3, SS, .7, SD, .5, SW, .4);
    }
    private static void range(PartChoice c, PartDial d, double lo, double hi) { c.ranges.put(d, new double[]{lo, hi}); }
    private static void def(PartChoice c, PartDial d, double v) { c.defaults.put(d, v); }
    private static void label(PartChoice c, PartDial d, String s) { c.labels.put(d, s); }
    private static void options(PartChoice c, PartDial d, String... words) { c.options.put(d, words); }
    /** Pairs of a cell and its amount. */
    private static void cells(PartChoice c, Object... pairs) {
        c.cells = new EnumMap<>(PartDial.class);
        for (int i = 0; i < pairs.length; i += 2) c.cells.put((PartDial) pairs[i], (Double) pairs[i + 1]);
    }

    /** A retired Drying mischief's Later (Float, Mold, Fading, Lemon Juice), else null. */
    PartChoice later() {
        switch (this) { case FLOAT: return LATER_FLOAT; case HALO: return LATER_MOLD; case FADE: return LATER_FADE; case LEMON: return LATER_LEMON; default: return null; }
    }
    /** A Later's retired Drying choice, which older versions read; null for Off and every other choice. */
    PartChoice retired() { for (PartChoice c : BrushPart.LIFE.choices()) if (c.later() == this) return c; return null; }
    /**
     * A Later's own drying, which its retired Drying choice was laid as: Quick for Float, Fading and Lemon Juice, Slow for
     * Mold; null for Off. Later's media: Slow for Salt and Plastic Wrap (a wet wash) and Cracks (thick paint), Instant for
     * Tarnish (metalpoint is dry), Quick for the rest.
     */
    PartChoice drying() {
        return this == LATER_OFF ? null : this == LATER_MOLD || this == LATER_SALT || this == LATER_WRAP || this == LATER_CRACKS ? DRYING : this == LATER_TARNISH ? NONE : FAST_WET;
    }
    /**
     * Whether this Later goes with {@code drying}: Off with any; Fading, Lemon Juice and the media but Salt and Plastic Wrap
     * with Instant, Quick or Slow (Wet's paint stays wet in its own canvas across strokes, so no one stroke changes); Salt and Plastic
     * Wrap with Quick or Slow (they need wet paint); Float and Mold only with their own drying, as each is a way of
     * drying (Float floats for its own time; Mold grows for Slow's).
     */
    boolean goesWith(PartChoice drying) {
        if (this == LATER_OFF) return true;
        if (this == LATER_SALT || this == LATER_WRAP) return drying == FAST_WET || drying == DRYING;
        if (clocked() >= 0) return drying == NONE || drying == FAST_WET || drying == DRYING;
        return drying == drying();
    }
    /** The kind of mark a Later lays that the clock changes ({@link FadingInk#FADE} to {@link FadingInk#TARNISH}), or -1. */
    int clocked() {
        switch (this) {
            case LATER_FADE: return FadingInk.FADE; case LATER_LEMON: return FadingInk.LEMON; case LATER_SALT: return FadingInk.SALT;
            case LATER_WRAP: return FadingInk.WRAP; case LATER_FEATHER: return FadingInk.FEATHER; case LATER_FOX: return FadingInk.FOX;
            case LATER_PRESS: return FadingInk.PRESS; case LATER_TORN: return FadingInk.TORN; case LATER_STITCH: return FadingInk.STITCH;
            case LATER_CRACKS: return FadingInk.CRACKS; case LATER_TARNISH: return FadingInk.TARNISH;
            default: return -1;
        }
    }

    /** Its position within part.choices(), as codes carry it. */
    int index() { return ordinal() - part.choices().get(0).ordinal(); }

    /** The choice at {@code index} of {@code part}'s; null outside the list. */
    static PartChoice of(BrushPart part, int index) {
        List<PartChoice> list = part.choices();
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }
    /** The choice with the prototype's key; null if none. */
    static PartChoice of(BrushPart part, String key) {
        for (PartChoice c : part.choices()) if (c.key.equals(key)) return c;
        return null;
    }

    /** parts.js rangeOf: the choice's own range, else the dial's. */
    double[] range(PartDial d) { double[] r = ranges.get(d); return r != null ? r.clone() : new double[]{d.min, d.max}; }
    /** parts.js dialDefault: the choice's own default, else the dial's. */
    double defaultOf(PartDial d) { Double v = defaults.get(d); return v != null ? v : d.def; }
    /** parts.js dialLabel: the choice's own label, else the dial's. */
    String labelOf(PartDial d) { String s = labels.get(d); return s != null ? s : d.label; }
    /** A choice dial's options as this choice shows them (newmods.js optLabel): its own, else the dial's. Display words: the code holds the index. */
    String[] optionsOf(PartDial d) { String[] o = options.get(d); return o != null ? o : d.choices; }
    boolean has(PartDial d) { return dials.contains(d); }
    /** A hand choice other than Custom. */
    boolean preset() { return cells != null; }
    /** A preset's cell; 0 when it doesn't set it. */
    double cell(PartDial cell) { Double v = cells == null ? null : cells.get(cell); return v != null ? v : 0; }
    /**
     * A medium's Erasable where a brush is made (a style's roll, a medium chosen): 1, erases clean, for every medium. Until
     * 2026-10-22 it was the real medium's (dried ink permanent, a wash .4, gouache .3); the owner: "per-tool things should
     * be erasable unless the user specifies". Never where one is read. Resist's 1 is masking fluid (its dial is wax or fluid).
     */
    double erasable() { return 1; }
    /** A paper's texture (the same order); null for other parts. */
    BrushTexture texture() { return part == BrushPart.PAPER ? BrushTexture.values()[index()] : null; }
}
