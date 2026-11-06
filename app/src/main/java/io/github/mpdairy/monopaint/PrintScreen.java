package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Screen and Tone (the mischief modules spec, Stage 1; docs/archive/brush-lab/prototypes/mischief-lab.html's
 * {@code buildScreen} and {@code bit1}): a threshold at every page pixel, fixed to the page, that turns a mark's darkness
 * into ink or none. A screened brush lays its mark crisp and even ({@link ResolvedBrush#screenTone}), and
 * {@link ModularCompose} writes each pixel of its mark pure black (White's white, Invert's opposite) where its tone
 * passes the threshold there, else
 * leaves the page as it found it (opaque paint lays white there). Two strokes of one screen land on the same dots, and
 * a stroke drawn again lays the same pixels. Tone lays the pattern at its Tone over the brush's fill, in black
 * dots ({@link #DOT_TONE} at {@link #DOT_COVERAGE}, so the eraser knows to scrape them, {@link EraseBothStroke}).
 * Misregister is the prototype's faint second pass, laid by the compose as the mark's line and body again at an offset
 * ({@link #misX}, {@link #misY}); Ink gain and Uneven are its second pass, worked out at each pixel from its neighbours'
 * thresholds. Pure, checked on the desktop ({@code ScreenChecks}).
 */
final class PrintScreen {
    /**
     * The Screen dial's name wherever the app shows it (its key stays {@code screen}, and its dials' {@code cell},
     * {@code sangle}, {@code mis}, {@code gain}, {@code uneven}): the owner's pick, 2026-10-19. A rename is this line
     * (and the web twins' {@code SCREEN_LABEL}, newmods.js).
     */
    static final String LABEL = "Print Shading";
    /**
     * Its short form, for its row in Brush Lab's open Medium card only ({@link LabLayout#rowLabel}): "PRINT SHADING" in
     * the row's caps takes two lines there, and Ink's seventeen dial rows have room for one. Its menu's panel says {@link #LABEL}.
     */
    static final String ROW_LABEL = "Shading";
    /**
     * Screen's ten (halftone dots, an engraver's lines, Bayer dither, grain; then rosette, Ben-Day, crosshatch, wavy lines,
     * mezzotint, diamond dots; then stipple, woodcut, scribble and hatching, whose look changes as they darken) in
     * {@link PartDial#SCREEN}'s order, and Tone's eleven patterns ({@link PartDial#PATTERN}'s order)
     * after them: new ones are appended, so every old code reads as before. Then Print Shading's three after Hatching
     * (riso, photocopy, blue noise: 2026-10-31, branch explore-screen), after Tone's so no Kind moves ({@link #print}); then
     * six patterns whose lines thicken with the grey (maze, brick, basketweave, waves, honeycomb, rings: 2026-11-06);
     * then pixels and stitches, a grey a cell ({@link #cells}, the same day).
     */
    enum Kind {
        HALFTONE, LINES, DITHER, GRAIN, ROSETTE, BENDAY, CROSSHATCH, WAVY, MEZZOTINT, DIAMOND, STIPPLE, WOODCUT, SCRIBBLE, HATCHING,
        DOTS, TONE_LINES, SAND, GRADIENT, TONE_CROSS, TONE_WAVY, TONE_MEZZO, TONE_STIPPLE, TONE_WOODCUT, TONE_SCRIBBLE, TONE_HATCHING,
        RISO, PHOTOCOPY, BLUE_NOISE,
        MAZE, BRICK, BASKET, WAVES, HONEYCOMB, RINGS,
        PIXELS, STITCHES
    }
    /** Print Shading's option {@code screen} (1 on, {@link PartDial#SCREEN}'s order) as its Kind: Halftone to Hatching, then those after Tone's. */
    static Kind print(int screen) {
        int first = Kind.HATCHING.ordinal() + 1;
        return Kind.values()[screen <= first ? screen - 1 : Kind.RISO.ordinal() + screen - 1 - first];
    }
    /** A Tone dot on its layer: tone 1 (black on the panel's 16 greys) at coverage 254, which no other brush lays. */
    static final int DOT_TONE = 1, DOT_COVERAGE = 254;
    /** How strong the misregistered second pass is (the prototype's faint .45). */
    static final double GHOST = .45;

    final Kind kind;
    /** The cell, page px, the angle, degrees; Ink gain and Uneven, 0 to 1; Tone's Tone. */
    final double cell, angle, gain, uneven, level;
    /** Misregister's offset, page px (the prototype's .8 and .6 of it); 0 for none. */
    final int misX, misY;
    /** Tone: the pattern at {@link #level} over the fill, in dots. */
    final boolean tone;
    /**
     * Pixels and Stitches (2026-11-06, the owner: "a pixel texture brush", "have them be able to be different shades", "maybe
     * also a cross-stitch texture"): not black dots but a grey a cell of a grid fixed to the page, {@link #cell} px, turned
     * Angle − 45 (square to the page at the default 45). The compose reads the mark at each cell's centre
     * ({@link #centre}, {@code ModularCompose.composeCells}), so a cell is laid whole or not at all, in one of
     * {@link #CELL_STEPS} greys ({@link #dark}): a solid square, or one cross-stitch with the fabric white round it.
     */
    final boolean cells;
    private final double ca, sa, inv;
    /** Rosette's four screens' angles (15, 75, 0 and 45 degrees at the default 45), as cosine and sine. */
    private final double[] rc = new double[4], rs = new double[4];
    /** Grain's grain, page px (riso's, the photocopy's and soft dither's texel too). */
    private final int grain;
    /** Riso's, the photocopy's or soft dither's tile ({@link Stochastic}); null for the rest. */
    private final float[] tile;

    private PrintScreen(Kind kind, double cell, double angle, double mis, double gain, double uneven, double level) {
        this.kind = kind; this.cell = cell; this.angle = angle; this.gain = gain; this.uneven = uneven; this.level = level;
        tone = kind.ordinal() >= Kind.DOTS.ordinal() && kind.ordinal() <= Kind.TONE_HATCHING.ordinal();
        cells = kind == Kind.PIXELS || kind == Kind.STITCHES;
        misX = (int) Math.round(mis * .8); misY = (int) Math.round(mis * .6);
        double a = Math.toRadians(cells ? angle - 45 : angle);
        ca = Math.cos(a); sa = Math.sin(a); inv = 1 / Math.max(1, cell);
        grain = Math.max(1, (int) Math.round(cell / 6));
        if (kind == Kind.ROSETTE) for (int i = 0; i < 4; i++) { double r = Math.toRadians(angle + ROSETTE[i]); rc[i] = Math.cos(r); rs[i] = Math.sin(r); }
        evens = family(kind) >= 0 || kind == Kind.RISO || kind == Kind.PHOTOCOPY;
        // Its stochastic tile, made when the brush resolves, not at its first pixel (a stroke's start).
        tile = kind == Kind.RISO ? Stochastic.grain() : kind == Kind.PHOTOCOPY ? Stochastic.toner() : kind == Kind.BLUE_NOISE ? Stochastic.blue() : null;
        kept = keeps(kind) ? new AtomicReferenceArray<>(TILES * TILES) : null;
    }

    /** The screen {@code medium} lays through: its Screen, or Tone's pattern; null for none (every other brush). */
    static PrintScreen of(ModularBrush.Part medium) {
        double cell = medium.get(PartDial.CELL, PartDial.CELL.def), angle = medium.get(PartDial.S_ANGLE, PartDial.S_ANGLE.def);
        if (medium.choice == PartChoice.TONE)
            return new PrintScreen(Kind.values()[Kind.DOTS.ordinal() + (int) medium.get(PartDial.PATTERN)], cell, angle, 0, 0, 0, medium.get(PartDial.TTONE));
        int screen = (int) medium.get(PartDial.SCREEN, 0);
        if (screen <= 0) return null;
        return new PrintScreen(print(screen), cell, angle, medium.get(PartDial.MIS), medium.get(PartDial.S_GAIN), medium.get(PartDial.UNEVEN), 1);
    }

    /**
     * The print tone (0 to 255; {@link ToneDocument.Layer#print}) a screened mark of darkness {@code a} over white leaves
     * over print tone {@code v}: the darker of the two, so strokes over strokes at one tone print the same dots and an
     * area scribbled over fills evenly (the owner, 2026-10-21), and a darker stroke darkens it; opaque paint, covering
     * it by {@code cover} (0 to 1), moves it toward its own.
     */
    static double printed(double v, double a, double cover) {
        double t = Math.min(v, 255 * (1 - a));
        return cover > 0 ? t + (255 * (1 - a) - t) * cover : t;
    }

    /** Whether its second pass is laid at an offset (Misregister). */
    boolean misregisters() { return misX != 0 || misY != 0; }

    /** Light over dark ({@link ResolvedBrush#laysLight}): whether a light print inks white here, {@code share} of the page the pixels whose thresholds are highest (the complement of its dots). */
    boolean white(double share, int x, int y) { return share > 0 && threshold(x, y) >= 1 - share; }
    /**
     * Whether a mark of darkness {@code a} over white (0 to 1) lays ink at page pixel {@code x, y} of a page
     * {@code height} tall (Tone's gradient runs down it): mischief-lab.html's {@code bit1} and its render's
     * second pass. Ink gain swells a dot by a ragged pixel where a neighbour's threshold would ink; Uneven thins the ink
     * in soft blots and drops specks where it ran thin.
     *
     * <p>{@code x, y} may lie between pixels (pixel {@code i}'s centre at {@code i}): a kept stroke's point on a
     * {@link FineGrid} (sharp dry brushes, "Screentone as built"). A screen worked out from the page's position
     * ({@link #between}) reads its threshold there, so a dot is the same dot, rounder; the rest, and Ink gain's and
     * Uneven's specks, read the page pixel the point lies in. At a pixel's centre, as ever.
     */
    boolean ink(double a, double x, double y, int height) {
        if (!(a > 0)) return false;
        if (cells) return dark(a, (int) Math.floor(x + .5), (int) Math.floor(y + .5)) > 0;
        if (tone) return a * (kind == Kind.GRADIENT ? clamp(level * 2 * (y + .5) / height) : level) > threshold(x, y);
        int px = (int) Math.floor(x + .5), py = (int) Math.floor(y + .5);
        double blot = uneven > 0 ? PaperNoise.low(px, py) / 255.0 : 0, t = a * (1 - uneven * .55 * blot);
        boolean b = t > threshold(x, y);
        if (!b && gain > 0) {
            int n = (t > threshold(x - 1, y) ? 1 : 0) + (t > threshold(x + 1, y) ? 1 : 0) + (t > threshold(x, y - 1) ? 1 : 0) + (t > threshold(x, y + 1) ? 1 : 0);
            if (n > 0 && hash(px, py, 17) < gain * (.22 + .18 * n)) b = true;
        }
        if (b && uneven > 0 && hash(px, py, 23) < uneven * (.04 + .14 * blot)) b = false;
        return b;
    }
    /** The threshold at page position {@code x, y} ({@link #ink}): a pixel's {@link #threshold(int, int)}; between pixels, a screen's worked out there ({@link #between}). */
    private double threshold(double x, double y) {
        int ix = (int) x, iy = (int) y;
        if (ix == x && iy == y) return threshold(ix, iy);
        return between(kind) ? worked(x, y) : threshold((int) Math.floor(x + .5), (int) Math.floor(y + .5));
    }
    /** Whether {@code kind}'s threshold is worked out from the page's position (its dots and lines finer between pixels), not a pixel's own (a hash, a tile, Bayer's). */
    static boolean between(Kind kind) {
        switch (kind) {
            case DITHER: case GRAIN: case SAND: case BLUE_NOISE: case RISO: case PHOTOCOPY: return false;
            default: return true;
        }
    }

    /**
     * The thresholds as worked out ({@link #worked}), in {@link #TILE}-px tiles of the page, NaN until asked for: the
     * four evened kinds' as floats (40 to 150 ns a pixel here, 20 times that on the Manta), Ben-Day's and Rosette's as
     * the doubles worked out, so no dot moves (10 to 30 ns, and Ink gain asks a pixel's four neighbours too); null for
     * the rest, as quick to work out as to look up (kept, they were slower: 2026-10-22). A stroke composes and previews
     * the same pixels at every present: worked out once a stroke instead. Any core may fill a pixel (the same value); a
     * tile is published whole, its NaNs set before it is seen.
     */
    private final AtomicReferenceArray<Object> kept;
    private final boolean evens;
    private static final int TILE = 32, TILES = 128;
    /** Whether {@code kind}'s thresholds are {@link #kept}. */
    private static boolean keeps(Kind kind) {
        switch (kind) {
            case ROSETTE: case BENDAY: case RISO: case PHOTOCOPY: return true;
            default: return family(kind) >= 0;
        }
    }

    /** The threshold at page pixel {@code x, y}, 0 to 1: ink where the darkness is above it (buildScreen). */
    double threshold(int x, int y) {
        return kept != null && x >= 0 && y >= 0 && x < TILE * TILES && y < TILE * TILES ? keptAt(x, y) : worked(x, y);
    }
    /** {@link #threshold} from {@link #kept}, worked out there first if it isn't yet. */
    private double keptAt(int x, int y) {
        int i = (y / TILE) * TILES + x / TILE, at = (y % TILE) * TILE + x % TILE;
        Object t = kept.get(i);
        if (t == null) {
            Object fresh;
            if (evens) { float[] f = new float[TILE * TILE]; java.util.Arrays.fill(f, Float.NaN); fresh = f; }
            else { double[] d = new double[TILE * TILE]; java.util.Arrays.fill(d, Double.NaN); fresh = d; }
            t = kept.compareAndSet(i, null, fresh) ? fresh : kept.get(i);
        }
        if (evens) {
            float[] f = (float[]) t;
            float v = f[at];
            if (v != v) f[at] = v = (float) worked(x, y);
            return v;
        }
        double[] d = (double[]) t;
        double v = d[at];
        if (v != v) d[at] = v = worked(x, y);
        return v;
    }

    /** The threshold at page pixel {@code x, y} worked out afresh (the four evened kinds' to a float's precision). */
    double worked(int x, int y) {
        switch (kind) {
            case DITHER: return (BAYER[(y & 7) << 3 | (x & 7)] + .5) / 64;
            case GRAIN: return hash(Math.floorDiv(x, grain), Math.floorDiv(y, grain), 3);
            case SAND: return hash(x, y, 5);
            case BLUE_NOISE: return tile[Stochastic.at(Math.floorDiv(x, grain), Math.floorDiv(y, grain), Stochastic.SIDE)];
            case RISO: return riso(x, y);
            case PHOTOCOPY: return photocopy(x, y);
            default: return worked((double) x, y);
        }
    }
    /** The threshold of a screen worked out from the page's position ({@link #between}) at {@code x, y}, between pixels too. */
    private double worked(double x, double y) {
        switch (kind) {
            case MEZZOTINT: case TONE_MEZZO: return mezzotint(x, y);
            case STIPPLE: case TONE_STIPPLE: case WOODCUT: case TONE_WOODCUT: case SCRIBBLE: case TONE_SCRIBBLE: case HATCHING: case TONE_HATCHING:
            case MAZE: case BRICK: case BASKET: case WAVES: case HONEYCOMB: case RINGS:
                return (float) evened(kind, (x * ca + y * sa) * inv, (-x * sa + y * ca) * inv, cell);
            default: break;
        }
        double u = (x * ca + y * sa) * inv, v = (-x * sa + y * ca) * inv;
        switch (kind) {
            // An engraver's lines, with a slight swell along them.
            case LINES: { double vv = v + .22 * sin1(u / 9); return Math.abs(vv - Math.floor(vv) - .5) * 2; }
            // Banknote waves: the same lines, swinging most of a line's pitch.
            case WAVY: case TONE_WAVY: return tri(v + .7 * sin1(u / 8));
            // Etching: one line set up to half grey, the crossing set from there to black.
            case CROSSHATCH: case TONE_CROSS: { double t1 = tri(v + .22 * sin1(u / 9)), t2 = tri(u + .22 * sin1(v / 9)); return Math.min(t1 < .5 ? t1 : 2, (1 + t2) / 2); }
            // Square dots in the screen's frame, which the default 45 degrees stands on a corner.
            case DIAMOND: { double d = 2 * Math.max(Math.abs(u - Math.floor(u) - .5), Math.abs(v - Math.floor(v) - .5)); return d * d; }
            // Four halftones at the process-colour angles, all black: a pixel inks where any of them would.
            case ROSETTE: {
                double m = 1;
                for (int i = 0; i < 4; i++) m = Math.min(m, .5 - (cos1((x * rc[i] + y * rs[i]) * inv) + cos1((-x * rs[i] + y * rc[i]) * inv)) / 4);
                double q = (1 - m) * (1 - m);
                return 1 - q * q;
            }
            // Comic Ben-Day: a round dot at every site of an offset lattice, all one size, growing with the tone.
            case BENDAY: {
                double d = benDayDistance(u / BENDAY_PITCH, v / BENDAY_PITCH);
                double[] share = BenDay.SHARE;
                return share[(int) Math.min(share.length - 1, Math.max(0, d * (share.length - 1) / BENDAY_REACH))];
            }
            case TONE_LINES: return Math.abs(v - Math.floor(v) - .5) * 2;
            // Round dots: the tone sets their size.
            default: return .5 - (cos1(u) + cos1(v)) / 4;
        }
    }

    /** Rosette's screen angles, off the Angle dial: 15, 75, 0 and 45 degrees at its default 45. */
    private static final double[] ROSETTE = {-30, 30, -45, 0};
    /**
     * Ben-Day: its sites on an offset (hexagonal) lattice {@code BENDAY_PITCH} cells apart, in the angle's frame (the
     * default 45 degrees sets the rows on the diagonal), its dots growing smoothly with the tone: a pixel inks once the
     * tone passes the share of the page its site's dot covers when it reaches it, solid just short of full darkness.
     * It stepped in a printer's six tints (10, 20, 30, 50, 70%, solid) until the owner, 2026-10-22: "pressure changes
     * with the print brushes: the ben day one, it doesn't seem to blend between, it seems to jump between shades".
     */
    private static final double BENDAY_PITCH = 1.5, BENDAY_SOLID = .997;
    /** How far from its site a pixel can be, lattice units (the hexagon's corner), and each distance's threshold: the lattice's own share within it. */
    private static final double BENDAY_REACH = 1 / Math.sqrt(3) + 1e-9;
    /** Made on Ben-Day's first use, not with the class (startup: every brush's resolve meets this class). */
    private static final class BenDay {
        static final double[] SHARE = new double[1024];
        static {
            // The share of the page within each distance of its site (so a dot reaching it covers that share).
            int[] within = new int[SHARE.length];
            int n = 256;
            double hh = Math.sqrt(3) / 2;
            for (int a = 0; a < n; a++) for (int b = 0; b < n; b++) {
                double d = benDayDistance((a + .5) / n, (b + .5) / n * 2 * hh);
                within[(int) Math.min(within.length - 1, d * (within.length - 1) / BENDAY_REACH)]++;
            }
            double sum = 0;
            for (int i = 0; i < SHARE.length; i++) {
                sum += within[i];
                SHARE[i] = BENDAY_SOLID * sum / (n * n);
            }
        }
    }
    /** The distance from {@code x, y} (lattice units) to its nearest site: rows {@code √3/2} apart, every other one shifted half a site. */
    private static double benDayDistance(double x, double y) {
        double h = Math.sqrt(3) / 2, best = 9;
        int j0 = (int) Math.floor(y / h);
        for (int j = j0; j <= j0 + 1; j++) {
            double shift = (j & 1) * .5, sx = Math.rint(x - shift) + shift, dx = x - sx, dy = y - j * h;
            best = Math.min(best, dx * dx + dy * dy);
        }
        return Math.sqrt(best);
    }
    /** A triangle wave: 0 on a line's centre, 1 midway between lines. */
    private static double tri(double t) { return Math.abs(t - Math.floor(t) - .5) * 2; }
    /** Mezzotint: dashes lying along the angle in rows a third of a cell wide, each row its own dash length and shift, each dash its own rank. */
    private double mezzotint(double x, double y) {
        double w = Math.max(1, cell / 3), px = x * ca + y * sa, py = -x * sa + y * ca;
        int j = (int) Math.floor(py / w);
        double len = cell * (.6 + 1.2 * hash(j, 0, 51));
        return hash((int) Math.floor((px + hash(j, 0, 52) * len) / len), j, 53);
    }

    // ---- Pixels and Stitches: a grey a cell ----

    /**
     * A cell's greys: its darkness rounded up to one of eight steps, spaced evenly in the panel's dots
     * ({@link DotPattern#pickerTone}): a block reads as one clear shade and the next step as clearly darker, where every
     * grey between would leave neighbouring blocks a dot or two apart, reading as noise.
     */
    static final int CELL_STEPS = 8;
    private static final int[] STEP_DARK = new int[CELL_STEPS + 1];
    static { for (int k = 0; k <= CELL_STEPS; k++) STEP_DARK[k] = 255 - DotPattern.pickerTone((int) Math.round(255.0 * (CELL_STEPS - k) / CELL_STEPS)); }

    /** The page pixel at the centre of the cell holding page pixel {@code x, y}, into {@code at} (x, then y): where the mark is read for the whole cell. */
    void centre(int x, int y, int[] at) {
        double u = (Math.floor((x * ca + y * sa) * inv) + .5) * cell, v = (Math.floor((-x * sa + y * ca) * inv) + .5) * cell;
        at[0] = (int) Math.round(u * ca - v * sa); at[1] = (int) Math.round(u * sa + v * ca);
    }
    /**
     * How dark (0 to 255) a cell whose mark is {@code a} dark (0 to 1, at its centre) leaves page pixel {@code x, y} in it;
     * 0 where it lays nothing. Pixels: the whole cell its step's grey. Stitches: one cross-stitch in that grey, its two
     * threads round and twisted as Cross-Stitch's ({@link LaterMedia#threadCover}), the top one over the bottom, the
     * fabric round them white.
     */
    int dark(double a, int x, int y) {
        if (!(a > 0)) return 0;
        int t = STEP_DARK[(int) Math.max(1, Math.ceil(Math.min(1, a) * CELL_STEPS - 1e-9))];
        if (kind == Kind.PIXELS) return t;
        double u = (x * ca + y * sa) * inv, v = (-x * sa + y * ca) * inv;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        float c = (float) cell, lu = (float) ((u - i) * cell) + .5f, lv = (float) ((v - j) * cell) + .5f, wide = .13f * c + .3f, q = 0;
        for (int leg = 0; leg < 2; leg++) {
            // Corner to corner, a little in: the bottom thread up, the top one down over it.
            float ax = .12f * c, ay = leg == 0 ? .88f * c : .12f * c, len = .76f * c * (float) Math.sqrt(2), ux = (float) Math.sqrt(.5), uy = leg == 0 ? -ux : ux;
            float rx = lu - ax, ry = lv - ay, along = rx * ux + ry * uy, across = -rx * uy + ry * ux;
            float cov = LaterMedia.threadCover(along, across, len, wide);
            if (cov <= 0) continue;
            float s = Math.min(255, t * LaterMedia.threadShade(along, across, wide, (float) unit(mix(i, j, 191 + leg)))) * cov;
            q = leg == 1 ? Math.min(255, s + q * (1 - cov) * LaterMedia.threadUnder(across, wide)) : Math.max(q, s);
        }
        return Math.round(q);
    }

    // ---- The four whose look changes as they darken: a field ranked, then evened so the share inked is the darkness ----

    /** Stipple, woodcut, scribble or hatching at {@code u, v} (cells, in the angle's frame), evened ({@link #EVEN}). */
    private static double evened(Kind kind, double u, double v, double cell) {
        int k = family(kind);
        double r = raw(k, u, v, cell);
        double[] e = even(k);
        double f = r * (EVEN_BINS - 1) / RAW_MOST;
        int i = (int) Math.max(0, Math.min(EVEN_BINS - 2, Math.floor(f)));
        return e[i] + (e[i + 1] - e[i]) * Math.max(0, Math.min(1, f - i));
    }
    /** Which field: 0 stipple, 1 woodcut, 2 scribble, 3 hatching (a Print Shading and its Screentone twin alike), 4 to 9 maze to rings; -1 for every other kind. */
    private static int family(Kind kind) {
        switch (kind) {
            case STIPPLE: case TONE_STIPPLE: return 0;
            case WOODCUT: case TONE_WOODCUT: return 1;
            case SCRIBBLE: case TONE_SCRIBBLE: return 2;
            case HATCHING: case TONE_HATCHING: return 3;
            case MAZE: return 4;
            case BRICK: return 5;
            case BASKET: return 6;
            case WAVES: return 7;
            case HONEYCOMB: return 8;
            case RINGS: return 9;
            default: return -1;
        }
    }
    /** The raw fields run 0 to about this; the even tables span it. */
    private static final double RAW_MOST = 2.5;
    private static final int EVEN_BINS = 1024;
    /** Each field's share of a page at or under each raw value, to .999 (so full darkness inks every pixel): its rank, evened. */
    private static final AtomicReferenceArray<double[]> EVEN = new AtomicReferenceArray<>(10);

    /**
     * The raw field at {@code u, v} (cells): lower inks first. Stipple: dots on a jittered lattice appear one by one in
     * their own order, small, then (all there) grow and merge. Woodcut: lines that waver, broken at first, thicken until
     * only broken white lines are left. Scribble: three looping scribbles, each laid over the last as it darkens.
     * Hatching: hatch strokes, short and a little askew, one direction, then a second and a third, then each again
     * between its rows. Maze to rings (4 to 9): the distance to their lines, scaled to run 0 to 2, so a line thickens with
     * the grey until the page is black; each continuous across its tiles (no seam).
     */
    static double raw(int family, double u, double v, double cell) {
        switch (family) {
            case 4: {
                // Maze: Truchet tiles two cells across, each a pair of quarter arcs on opposite corners, which pair a hash of the tile.
                double x = u / 2, y = v / 2;
                int i = (int) Math.floor(x), j = (int) Math.floor(y);
                double fx = x - i, fy = y - j;
                if ((mix(i, j, 97) & 1) != 0) fx = 1 - fx;
                return 4 * Math.min(Math.abs(Math.sqrt(fx * fx + fy * fy) - .5), Math.abs(Math.sqrt((1 - fx) * (1 - fx) + (1 - fy) * (1 - fy)) - .5));
            }
            case 5: {
                // Brick: running bond, courses two cells tall, bricks twice as long, every other course shifted half a brick.
                double y = v / 2;
                int r = (int) Math.floor(y);
                double fy = y - r, x = u / 4 + (r & 1) * .5, fx = x - Math.floor(x);
                return 4 * Math.min(Math.min(fy, 1 - fy), 2 * Math.min(fx, 1 - fx));
            }
            case 6: {
                // Basketweave: blocks of three strands a cell wide, across and down in turn; a strand's end draws back from the
                // block's edge (white there till last), so it meets the next block's without a seam.
                double x = u / 3, y = v / 3;
                int i = (int) Math.floor(x), j = (int) Math.floor(y);
                boolean flat = ((i + j) & 1) == 0;
                double across = flat ? y - j : x - i, along = flat ? x - i : y - j;
                return 2 * Math.max(tri(3 * across), 1 - 6 * Math.min(along, 1 - along));
            }
            case 7: return 6 * waves(u / 3, v / 3);
            case 8: {
                // Honeycomb: hexagons two cells across, the distance to the nearest wall.
                double x = u / 2, y = v / 2, h = Math.sqrt(3) / 2, best = 9, dx = 0, dy = 0;
                int j0 = (int) Math.floor(y / h);
                for (int j = j0; j <= j0 + 1; j++) {
                    double shift = (j & 1) * .5, sx = Math.rint(x - shift) + shift, ex = x - sx, ey = y - j * h, d = ex * ex + ey * ey;
                    if (d < best) { best = d; dx = ex; dy = ey; }
                }
                return 4 * (.5 - Math.max(Math.abs(dx), Math.max(Math.abs(.5 * dx + h * dy), Math.abs(-.5 * dx + h * dy))));
            }
            case 9: {
                // Rings: circles a cell apart round the nearest of centres seven cells apart, each nudged by a hash.
                double best = 1e9;
                int i0 = (int) Math.floor(u / RING_GAP), j0 = (int) Math.floor(v / RING_GAP);
                for (int i = i0 - 1; i <= i0 + 1; i++) for (int j = j0 - 1; j <= j0 + 1; j++) {
                    int h = mix(i, j, 101);
                    double cx = (i + .5 + (unit(h) - .5) * .5) * RING_GAP, cy = (j + .5 + (unit(h * 0x9e3779b1) - .5) * .5) * RING_GAP;
                    best = Math.min(best, (u - cx) * (u - cx) + (v - cy) * (v - cy));
                }
                best = Math.sqrt(best);
                return 4 * Math.abs(best - Math.rint(best));
            }
            case 0: {
                // Dots a cell apart; a dot's own size, at least a pixel and a half across.
                double r0 = Math.max(.75, cell * .2) / cell, best = 9;
                int i0 = (int) Math.floor(u - .5), j0 = (int) Math.floor(v - .5);
                for (int i = i0; i <= i0 + 1; i++) for (int j = j0; j <= j0 + 1; j++) {
                    int h = mix(i, j, 61);
                    double sx = i + .5 + (unit(h) - .5) * .6, sy = j + .5 + (unit(h * 0x9e3779b1) - .5) * .6;
                    double d = Math.sqrt((u - sx) * (u - sx) + (v - sy) * (v - sy));
                    best = Math.min(best, .9 * unit(h * 0x85ebca6b) + 1.6 * Math.max(0, d - r0));
                }
                return best;
            }
            case 1: {
                // A line a cell; its wander and breaks its own.
                int j = (int) Math.floor(v);
                double wav = 0;
                for (int pass = 0; pass < 2; pass++) {
                    int h = mix(j, 0, 71);
                    wav = .2 * sin1(u * .05 + unit(h)) + .07 * sin1(u * .17 + unit(h * 0x85ebca6b));
                    int jj = (int) Math.floor(v + wav);
                    if (jj == j) break;
                    j = jj;
                }
                int h = mix(j, 0, 73);
                double b = .5 + .3 * sin1(u * .06 + unit(h)) + .2 * sin1(u * .19 + unit(h * 0x9e3779b1));
                return tri(v + wav) + .55 * (b - .5) + .3;
            }
            case 2: {
                // Three scribbles: each the loops where a smooth field crosses its levels, as far off them as the field's
                // slope says (so a loop is one width all round, not a blot where the field lies flat).
                double best = 9;
                for (int l = 0; l < 3; l++) {
                    double uu = l == 1 ? v : u, vv = l == 1 ? -u : v, ph = l * .37, a = .07 + .015 * l, tau = 2 * Math.PI;
                    // F and its slope, the chain rule through each wave (cos1 is the slope of sin1, over 2π).
                    double p1 = a * 1.3 * vv + ph, A = a * uu + .45 * sin1(p1), p2 = a * .9 * uu + 2 * ph, B = a * 1.1 * vv + .45 * sin1(p2), C = a * .7 * (uu + vv) + ph * 3;
                    double cA = tau * cos1(A), cB = tau * cos1(B), cC = .6 * tau * cos1(C) * a * .7;
                    double F = sin1(A) + sin1(B) + .6 * sin1(C);
                    double fu = cA * a + cB * .45 * tau * cos1(p2) * a * .9 + cC, fv = cA * .45 * tau * cos1(p1) * a * 1.3 + cB * a * 1.1 + cC;
                    double k = F * SCRIBBLE_K, off = Math.abs(k - Math.rint(k)) / Math.max(.12, SCRIBBLE_K * Math.sqrt(fu * fu + fv * fv));
                    best = Math.min(best, SCRIBBLE_AT[l] + 1.8 * off);
                }
                return best;
            }
            default: {
                double best = 9;
                // The sets in order of where they start: one that starts past the best so far can't be nearer.
                for (int s = 0; s < 6 && HATCH_AT[s] < best; s++) {
                    int L = s % 3, odd = s / 3;
                    double c = TURN_COS[L], sn = TURN_SIN[L];
                    double hu = u * c + v * sn, hv = -u * sn + v * c;
                    // Rows two cells apart, the odd ones between them.
                    double t = (hv - odd) / 2;
                    int k = (int) Math.floor(t + .5);
                    int row = mix(k, s, 83);
                    double seg = 6, shift = unit(row) * seg;
                    int m = (int) Math.floor((hu + shift) / seg);
                    int h = mix(row, m, 89);
                    double mid = (m + .5) * seg - shift + (unit(h) - .5) * 1.2, x = hu - mid, half = seg * (.36 + .1 * unit(h * 0x27d4eb2d));
                    double centre = 2 * k + odd + (unit(h * 0x9e3779b1) - .5) * .5 + (unit(h * 0x85ebca6b) - .5) * .16 * x;
                    // Thinner toward its ends, as a flicked stroke.
                    double e = Math.abs(x) / half, d = Math.abs(hv - centre) * (1 + 2 * Math.max(0, e - .55)) + 2 * Math.max(0, Math.abs(x) - half);
                    best = Math.min(best, HATCH_AT[s] + 1.4 * d);
                }
                return best;
            }
        }
    }
    private static final double RING_GAP = 7;
    /**
     * Seigaiha at {@code x, y} (scales of radius 1): rows of circles one apart, each two apart along its row and every
     * other row shifted one, a lower row over the one above it; each scale three arcs. The distance to the nearest line:
     * the front scale's arcs, or the rim of a scale over it.
     */
    private static double waves(double x, double y) {
        int top = (int) Math.floor(y), row = top;
        double cx = 0;
        for (int j = top + 1; j >= top; j--) {
            row = j;
            cx = 2 * Math.rint((x - (j & 1)) / 2) + (j & 1);
            if ((x - cx) * (x - cx) + (y - j) * (y - j) < 1) break;
        }
        double r = Math.sqrt((x - cx) * (x - cx) + (y - row) * (y - row)), k = Math.max(1, Math.rint(3 * r)), d = Math.abs(3 * r - k) / 3;
        for (int j = row + 1; j <= row + 2; j++) {
            double i0 = Math.floor((x - (j & 1)) / 2);
            for (int i = 0; i < 2; i++) { double ex = x - (2 * (i0 + i) + (j & 1)); d = Math.min(d, Math.abs(Math.sqrt(ex * ex + (y - j) * (y - j)) - 1)); }
        }
        return d;
    }
    /** How many of a scribble's field's levels a unit holds. */
    private static final double SCRIBBLE_K = .55;
    /** Hatching's three directions off the angle (Hatch's turns), as cosine and sine. */
    private static final double[] TURN_COS = new double[3], TURN_SIN = new double[3];
    static { for (int L = 0; L < 3; L++) { TURN_COS[L] = Math.cos(Math.toRadians(HatchStamper.TURN[L])); TURN_SIN[L] = Math.sin(Math.toRadians(HatchStamper.TURN[L])); } }
    /** Where each scribble starts, and each hatching set (each direction, then each between its rows). */
    private static final double[] SCRIBBLE_AT = {0, .45, .8}, HATCH_AT = {0, .3, .55, .8, .95, 1.1};
    private static int mix(int a, int b, int c) {
        int h = a * 0x27d4eb2d ^ b * 0x165667b1 ^ c * 0x9e3779b1;
        h = (h ^ (h >>> 15)) * 0x85ebca6b; h = (h ^ (h >>> 13)) * 0xc2b2ae35;
        return h ^ (h >>> 16);
    }
    private static double unit(int h) { int g = (h ^ (h >>> 16)) * 0x45d9f3b; g ^= g >>> 16; return (g & 0xffffffffL) / 4294967296.0; }
    /**
     * Field {@code k}'s even table, from a page of its default size (Pattern Size 6) at 45 degrees: made on its first use,
     * not with the class (over 20 s for the four on the Manta, and every brush's resolve meets this class at startup).
     */
    private static double[] even(int k) {
        double[] made = EVEN.get(k);
        if (made != null) return made;
        synchronized (EVEN) {
            if ((made = EVEN.get(k)) != null) return made;
            int n = 384;
            double cell = PartDial.CELL.def, inv = 1 / cell, ca = Math.cos(Math.PI / 4), sa = ca;
            int[] count = new int[EVEN_BINS];
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
                double r = raw(k, (x * ca + y * sa) * inv, (-x * sa + y * ca) * inv, cell);
                count[(int) Math.max(0, Math.min(EVEN_BINS - 1, Math.round(r * (EVEN_BINS - 1) / RAW_MOST)))]++;
            }
            double[] e = new double[EVEN_BINS];
            double sum = 0;
            for (int i = 0; i < EVEN_BINS; i++) { sum += count[i]; e[i] = .998 * (sum - count[i] / 2.0) / (n * n) + 1e-6 * i; }
            EVEN.set(k, e);
            return e;
        }
    }

    // ---- The stochastic three: riso, photocopy, blue noise (Soft Dither), from tiles made once ----

    /**
     * Riso: the duplicator's grain screen, a fine clumpy stochastic dither ({@link Stochastic#grain}), through soy ink
     * that lies unevenly: thinner in soft blots, so solids print grainy with specks of paper, and now and then a faint
     * roller streak across the feed (along the page's rows), fixed to the page. A threshold over 1 never inks.
     */
    private double riso(int x, int y) {
        double t = grained(x, y);
        double blot = PaperNoise.blob(x, y) / 255.0, low = PaperNoise.low(x, y) / 255.0;
        double ink = 1 - .16 * cube(blot) - .06 * low - streak(x, y);
        return (float) ((.02 + .96 * t) / ink);
    }
    /** A roller streak's share of ink taken at page pixel {@code x, y}: a band of rows, one in {@link #STREAK_ROWS} on about half the bands, fading along its length. */
    private static double streak(int x, int y) {
        int band = Math.floorDiv(y, STREAK_ROWS);
        if (hash(band, 0, 131) > .5) return 0;
        double at = band * STREAK_ROWS + 30 + hash(band, 0, 132) * (STREAK_ROWS - 60), width = 4 + 5 * hash(band, 0, 133);
        double d = Math.abs(y - at) / width;
        if (d >= 1) return 0;
        return (.18 + .14 * hash(band, 0, 134)) * (1 - d * d) * (.55 + .45 * sin1(x / 700.0 + hash(band, 0, 135)));
    }
    private static final int STREAK_ROWS = 260;
    /**
     * Photocopy: greys snap toward black or white at a threshold near the middle that drifts across the page (the
     * copier's uneven lamp, {@link PaperNoise#low} stretched four times), broken by clumpy toner so edges go ragged;
     * a scattered speck of toner wherever a mark lies at all, and in large blacks a faint mottle of flecks where none
     * took.
     */
    private double photocopy(int x, int y) {
        if (hash(x, y, 141) < .05 * cube(PaperNoise.blob(x, y) / 255.0)) return (float) 1.02;
        if (hash(x, y, 142) < .012) return (float) (.08 + .14 * hash(x, y, 143));
        double lamp = PaperNoise.low(Math.floorDiv(x, 4), Math.floorDiv(y, 4)) / 255.0 - .5;
        double t = grained(x, y);
        return (float) (.5 + .28 * lamp + .24 * (t - .5));
    }
    private static double cube(double v) { return v * v * v; }
    /**
     * A grain tile's threshold at page pixel {@code x, y}, {@link #grain} px a texel: each tile's copy shifted by its own
     * hashed offset, so no row or column of the tile lines up across the page (a clumpy tile's rows differ in mean, and
     * repeated they read as faint stripes); the seams cut clumps a pixel or two across, unseen.
     */
    private double grained(int x, int y) {
        int gx = Math.floorDiv(x, grain), gy = Math.floorDiv(y, grain), h = mix(Math.floorDiv(gx, Stochastic.WIDE), Math.floorDiv(gy, Stochastic.WIDE), 181);
        return tile[Stochastic.at(gx + h, gy + (h >>> 8), Stochastic.WIDE)];
    }

    /**
     * The tiles, each made on its first use (a brush's resolve, {@link #tile}), the same every time: blue noise 64 px,
     * the grains 128 (a 64-px clump repeats visibly).
     */
    static final class Stochastic {
        static final int SIDE = 64, N = SIDE * SIDE, WIDE = 128;
        /**
         * Blue noise: Ulichney's void-and-cluster ranks, each pixel's threshold its rank's share, so any grey is that
         * share of pixels spread as evenly as can be, with no lattice to see (Bayer's) and no clumps (Grain's).
         */
        static float[] blue() { return Blue.TILE; }
        /** Riso's grain: white noise softened a little, ranked: clumps of a pixel or two. */
        static float[] grain() { return Grain.TILE; }
        /** The copier's toner: softened more, so its clumps are bigger. */
        static float[] toner() { return Toner.TILE; }
        private static final class Blue { static final float[] TILE = voidAndCluster(); }
        private static final class Grain { static final float[] TILE = clumpy(.9, .2, 161); }
        private static final class Toner { static final float[] TILE = clumpy(1.1, .12, 171); }
        static int at(int x, int y, int side) { return (y & side - 1) * side + (x & side - 1); }
        private static int at(int x, int y) { return at(x, y, SIDE); }

        private static float[] voidAndCluster() {
            int r = 6;
            double sigma = 1.5;
            double[] g = new double[(2 * r + 1) * (2 * r + 1)];
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) g[(dy + r) * (2 * r + 1) + dx + r] = Math.exp(-(dx * dx + dy * dy) / (2 * sigma * sigma));
            boolean[] on = new boolean[N];
            double[] e = new double[N];
            int ones = 0;
            for (int i = 0; i < N; i++) if (hash(i % SIDE, i / SIDE, 151) < .1) { on[i] = true; ones++; spread(e, g, r, i, 1); }
            // Move the tightest cluster to the largest void until it would land where it was.
            for (int k = 0; k < N; k++) {
                int c = most(e, on, true);
                on[c] = false; spread(e, g, r, c, -1);
                int v = most(e, on, false);
                on[v] = true; spread(e, g, r, v, 1);
                if (v == c) break;
            }
            boolean[] start = on.clone();
            double[] e0 = e.clone();
            int[] rank = new int[N];
            for (int k = ones - 1; k >= 0; k--) { int c = most(e, on, true); on[c] = false; spread(e, g, r, c, -1); rank[c] = k; }
            on = start; e = e0;
            for (int k = ones; k < N; k++) { int v = most(e, on, false); on[v] = true; spread(e, g, r, v, 1); rank[v] = k; }
            float[] t = new float[N];
            for (int i = 0; i < N; i++) t[i] = (rank[i] + .5f) / N;
            return t;
        }
        /** The set pixel ({@code set}) with the most energy (the tightest cluster), or the clear one with the least (the largest void). */
        private static int most(double[] e, boolean[] on, boolean set) {
            int best = -1;
            for (int i = 0; i < N; i++) if (on[i] == set && (best < 0 || (set ? e[i] > e[best] : e[i] < e[best]))) best = i;
            return best;
        }
        /** Adds {@code sign} of the Gaussian {@code g} round pixel {@code i} to {@code e}, wrapping (the tile tiles). */
        private static void spread(double[] e, double[] g, int r, int i, int sign) {
            int x = i % SIDE, y = i / SIDE, w = 2 * r + 1;
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) e[at(x + dx, y + dy)] += sign * g[(dy + r) * w + dx + r];
        }
        /** White noise ({@code salt}) on a {@link #WIDE} tile, softened by a Gaussian {@code sigma} px wide, {@code white} of it added back, ranked. */
        private static float[] clumpy(double sigma, double white, int salt) {
            int n = WIDE * WIDE, r = (int) Math.ceil(3 * sigma);
            double[] w = new double[n], soft = new double[n], g = new double[(2 * r + 1) * (2 * r + 1)];
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) g[(dy + r) * (2 * r + 1) + dx + r] = Math.exp(-(dx * dx + dy * dy) / (2 * sigma * sigma));
            for (int i = 0; i < n; i++) w[i] = hash(i % WIDE, i / WIDE, salt);
            for (int i = 0; i < n; i++) {
                int x = i % WIDE, y = i / WIDE;
                double s = 0, sum = 0;
                for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) { double k = g[(dy + r) * (2 * r + 1) + dx + r]; s += k * w[at(x + dx, y + dy, WIDE)]; sum += k; }
                soft[i] = s / sum + white * (w[i] - .5);
            }
            return ranked(soft);
        }
        /** Each value's rank among them, as its share: a threshold that inks exactly a grey's share. */
        private static float[] ranked(double[] v) {
            int n = v.length;
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            java.util.Arrays.sort(order, (p, q) -> Double.compare(v[p], v[q]) != 0 ? Double.compare(v[p], v[q]) : Integer.compare(p, q));
            float[] t = new float[n];
            for (int k = 0; k < n; k++) t[order[k]] = (k + .5f) / n;
            return t;
        }
    }

    /** {@code cos(2πt)} from a table: the screen needs its shape, not its last decimal, and two a pixel must be cheap. */
    private static double cos1(double t) { return COS[(int) ((t - Math.floor(t)) * TABLE) & (TABLE - 1)]; }
    private static double sin1(double t) { return cos1(t - .25); }
    private static final int TABLE = 4096;
    private static final double[] COS = new double[TABLE];
    /** The 8 × 8 Bayer matrix, row by row (mischief-lab.html's bayer(8)). */
    private static final int[] BAYER = new int[64];
    static {
        for (int i = 0; i < TABLE; i++) COS[i] = Math.cos(2 * Math.PI * i / TABLE);
        int[][] m = {{0}};
        while (m.length < 8) {
            int k = m.length;
            int[][] q = {{0, 2}, {3, 1}}, out = new int[2 * k][2 * k];
            for (int y = 0; y < 2 * k; y++) for (int x = 0; x < 2 * k; x++) out[y][x] = 4 * m[y % k][x % k] + q[y / k][x / k];
            m = out;
        }
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) BAYER[y * 8 + x] = m[y][x];
    }
    /** mischief-lab.html's hash: page pixel {@code x, y} and salt {@code s}, 0 to 1. */
    static double hash(int x, int y, int s) {
        int h = x * 0x27d4eb2d ^ y * 0x165667b1 ^ s * 0x9e3779b1;
        h = (h ^ (h >>> 15)) * 0x85ebca6b; h = (h ^ (h >>> 13)) * 0xc2b2ae35;
        return ((h ^ (h >>> 16)) & 0xffffffffL) / 4294967296.0;
    }
}
