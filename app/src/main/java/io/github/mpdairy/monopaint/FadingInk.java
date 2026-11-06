package io.github.mpdairy.monopaint;

/**
 * Fading and Lemon Juice (the mischief modules spec, Stage 3; {@code mischief-lab.html}'s {@code dab3} and
 * {@code shade3}): Drying choices whose mark changes by the clock after it is laid. Fading fades away unevenly, thin
 * parts and the paper's damp patches first, until nothing of it is left on the page or in the file; Lemon Juice goes on
 * as a faint trace and darkens in unevenly over its drying time, then is an ordinary dried mark. Later's media (Salt,
 * Plastic Wrap, Feathering, Foxing, 2026-10-31) go from as laid to the technique's finished state ({@link LaterMedia}),
 * worked out at the lift, the same way.
 *
 * <p>A mark ({@link Mark}) is its stroke's Undo step as laid: the tiles it changed, as they were before it (what it
 * fades back to) and as it laid them (what lemon juice darkens to), with when it was laid. Each pixel shows a share of
 * that change worked out from its age (its fade weight: how much the stroke changed it; and a mottle fixed to the page),
 * stepped as coarsely as e-ink looks fine ({@link #stepMs}). Only the clock decides, so a page closed mid-fade keeps
 * fading when it is opened again. The page writes the shares ({@link ToneDocument#fade}); here is what they are. Pure,
 * checked on the desktop ({@code FadingChecks}).
 */
final class FadingInk {
    private FadingInk() {}

    /**
     * A mark's kind, as its file holds it: Fading, Lemon Juice; Later's media (2026-10-31, {@link LaterMedia}), each
     * changing a mark from as laid to what the technique leaves: Salt, Plastic Wrap, Feathering, Foxing.
     */
    static final int FADE = 0, LEMON = 1, SALT = 2, WRAP = 3, FEATHER = 4, FOX = 5;
    /** And since 2026-11-01: Letterpress, Torn Paper, Cross-Stitch, Cracks, Tarnish (a version before them skips their marks). */
    static final int PRESS = 6, TORN = 7, STITCH = 8, CRACKS = 9, TARNISH = 10, NEWEST = TARNISH;
    /** Whether {@code kind} is one of Later's media: its mark changes from as laid to a finished state worked out at the lift. */
    static boolean media(int kind) { return kind >= SALT; }

    /**
     * What a brush with one of them lays: which, its time (Fades In, Dries In and the like), ms, its macro dial (0 to 1)
     * and its seed; and the paper it draws on, with its Texture dial (Feathering wicks along its grain).
     */
    static final class Spec {
        final int kind, ms;
        final double amount, tooth;
        final long seed;
        final BrushTexture paper;
        Spec(int kind, int ms, double amount, long seed, BrushTexture paper, double tooth) {
            this.kind = kind; this.ms = Math.max(1, ms); this.amount = amount; this.seed = seed; this.paper = paper == null ? BrushTexture.PLAIN : paper; this.tooth = tooth;
        }
    }

    /** The page's clock, wall time in ms: the same after the app is closed and opened again. The checks set their own. */
    static java.util.function.LongSupplier clock = System::currentTimeMillis;
    static long now() { return clock.getAsLong(); }

    /** The longest Fades In and Dries In a file may hold, ms. */
    static final int MOST_MS = 10 * 60 * 1000;
    /** Lemon juice goes on at this share of its darkness, of 255 (about 10%, the prototype's). */
    static final int TRACE = 26;

    /**
     * One mark changing by the clock, immutable ({@link #at}): the layer it is on and the page's size when it was laid
     * (its tiles' keys are that page's, {@link ToneDocument#TILE} square); its kind and its time; each tile it changes
     * as it is at its start ({@code before}) and at its end ({@code after}), tones then coverages as an Undo step's
     * tiles are: Fading and Lemon Juice before the stroke and as laid, Later's media as laid and as the technique leaves
     * it; when it was laid, and the clock time its pixels show ({@code shown}; -1: as laid, before any). {@code steps}:
     * Later's media's stroke's Undo steps on its layer (the page's, {@link ToneDocument}), oldest first; null for
     * Fading and Lemon Juice, a snapshot's and a mark read from a file.
     */
    static final class Mark {
        final ToneDocument.Layer layer;
        final int width, height, kind, ms;
        final long born, shown;
        final int[] keys;
        final byte[][] before, after;
        final java.util.List<?> steps;
        Mark(ToneDocument.Layer layer, int width, int height, int kind, int ms, long born, long shown, int[] keys, byte[][] before, byte[][] after, java.util.List<?> steps) {
            this.layer = layer; this.width = width; this.height = height; this.kind = kind; this.ms = ms; this.born = born; this.shown = shown;
            this.keys = keys; this.before = before; this.after = after; this.steps = steps;
        }
        /** This mark with its pixels showing clock time {@code t}. */
        Mark at(long t) { return new Mark(layer, width, height, kind, ms, born, t, keys, before, after, steps); }
        /** This mark on {@code other} (a copy of its layer, a snapshot's: no Undo there). */
        Mark on(ToneDocument.Layer other) { return new Mark(other, width, height, kind, ms, born, shown, keys, before, after, null); }
        /** When every pixel is done: lemon juice fully dark, a fading mark gone, a technique finished. */
        long end() { return born + (long) Math.ceil(ms * most(kind)); }
        /** The next clock time its pixels should show, at most its end. */
        long next() { return shown < 0 ? born : Math.min(end(), shown + stepMs(kind, ms)); }
        /** The bytes its tiles hold. */
        long bytes() { long n = 0; for (int i = 0; i < keys.length; i++) n += before[i].length + after[i].length; return n; }
        int columns() { return (width + ToneDocument.TILE - 1) / ToneDocument.TILE; }
        /** The share, 0 to 255, of its change pixel {@code p} of tile {@code j} shows at clock time {@code t}; -1 is as laid. */
        int share(int j, int p, long t) {
            // As laid: Fading's and Lemon Juice's end side, Later's media's start.
            if (t < 0) return media(kind) ? 0 : 255;
            int cols = columns(), x0 = keys[j] % cols * ToneDocument.TILE, y0 = keys[j] / cols * ToneDocument.TILE, w = Math.min(ToneDocument.TILE, width - x0);
            byte[] b = before[j], a = after[j];
            int n = b.length / 2;
            int dark = Math.abs(onWhite(a[p] & 255, a[n + p] & 255) - onWhite(b[p] & 255, b[n + p] & 255));
            return FadingInk.share(kind, ms, dark, mottle(x0 + p % w, y0 + p / w), t - born);
        }
    }

    /** A fading pixel's life is at most this many times Fades In (its darkest, slowest mottle), lemon juice's Dries In; Later's media's their time. */
    static final double FADE_MOST = 1.25, LEMON_MOST = 1.3, MEDIA_MOST = 1;
    static double most(int kind) { return kind == FADE ? FADE_MOST : kind == LEMON ? LEMON_MOST : MEDIA_MOST; }
    /**
     * Later's media's timing, shares of their time: a pixel starts after {@code [0]} of it by the mottle and {@code [1]}
     * by how little the technique changes it (so a starburst opens from its middle, a thread grows out to its tip, a
     * spot spreads from its core), and takes {@code [2]} of it; the three make 1 ({@link #MEDIA_MOST}). Salt, Plastic
     * Wrap, Feathering, Foxing; Letterpress (at once, nearly all together, as a press lifts), Torn Paper, Cross-Stitch
     * (stitch by stitch, by the mottle), Cracks (spreading from where they open most), Tarnish (unevenly, slowly).
     */
    private static final float[][] TIMING = {{.3f, .35f, .35f}, {.45f, .2f, .35f}, {.15f, .5f, .35f}, {.55f, .2f, .25f},
            {.15f, .05f, .8f}, {.25f, .25f, .5f}, {.75f, 0, .25f}, {.45f, .3f, .25f}, {.5f, .1f, .4f}};

    /**
     * The share, 0 to 255, of a mark's change a pixel shows {@code age} ms after it was laid (the prototype's
     * {@code shade3}), {@code dark} how much the stroke changed it (0 to 255) and {@code mottle} the page's at it. Fading:
     * its life is Fades In times .35 to 1 by its darkness (thin parts go first) and .75 to 1.25 by the mottle; it holds,
     * then thins out over the last 45% of it to nothing. Lemon Juice: from {@link #TRACE} to all of it over Dries In
     * times .7 to 1.3 by the mottle, eased.
     */
    static int share(int kind, int ms, int dark, int mottle, long age) {
        float m = mottle / 255f;
        if (media(kind)) {
            float[] k = TIMING[kind - SALT];
            float u = Math.max(0, Math.min(1, (age - ms * (k[0] * m + k[1] * (1 - dark / 255f))) / (ms * k[2])));
            return u >= 1 ? 255 : Math.round(255 * u * u * (3 - 2 * u));
        }
        if (kind == LEMON) {
            float u = Math.max(0, Math.min(1, age / (ms * (.7f + .6f * m))));
            return u >= 1 ? 255 : TRACE + Math.round((255 - TRACE) * u * u * (3 - 2 * u));
        }
        float life = ms * (.35f + .65f * dark / 255f) * (.75f + .5f * m), left = 1 - age / life;
        return left <= 0 ? 0 : Math.round(255 * Math.min(1, left * 2.2f));
    }

    /**
     * How often its tones step, ms: as coarsely as e-ink looks fine, a partial refresh each (the spec's; the prototype's
     * four a second is too many for the panel). Lemon Juice and Later's media a twentieth of their time, 1 to 15 s;
     * Fading a tenth of Fades In, 2 to 5 s.
     */
    static long stepMs(int kind, int ms) { return kind != FADE ? Math.max(1000, Math.min(15000, ms / 20)) : Math.max(2000, Math.min(5000, ms / 10)); }

    /**
     * The mottle fixed to the page at pixel {@code x, y}, 0 to 255 (the prototype's {@code mott}: soft value noise and a
     * pixel of grit, .7 and .3): the paper's 16 px blob ({@link PaperNoise#blob}), about the prototype's 7 CSS px.
     */
    static int mottle(int x, int y) {
        int h = x * 0x27d4eb2d ^ y * 0x165667b1 ^ 0x2b;
        h = (h ^ h >>> 15) * 0x85ebca6b; h = (h ^ h >>> 13) * 0xc2b2ae35; h ^= h >>> 16;
        return (7 * PaperNoise.blob(x, y) + 3 * (h & 255) + 5) / 10;
    }

    /**
     * A pixel {@code share} of the way from {@code t0, a0} (before) to {@code t1, a1} (as laid), a layer's tone and
     * coverage: coverage and the paint it holds (darkness times coverage) each that share of the way, so the pixel over
     * white is too. Exactly the one at 0 and the other at 255. Tone in the high byte, coverage in the low.
     */
    static int mix(int t0, int a0, int t1, int a1, int share) {
        if (share <= 0) return t0 << 8 | a0;
        if (share >= 255) return t1 << 8 | a1;
        int a = a0 + div255((a1 - a0) * share);
        if (a <= 0) return 255 << 8;
        int p0 = (255 - t0) * a0, p1 = (255 - t1) * a1, p = p0 + div255((p1 - p0) * share);
        int t = 255 - (p + a / 2) / a;
        return Math.max(0, Math.min(255, t)) << 8 | a;
    }
    private static int div255(int v) { return v >= 0 ? (v + 127) / 255 : -((-v + 127) / 255); }

    /** The most marks a page's file holds. */
    static final int MOST_MARKS = 4096;
    /**
     * A file's FADE section ({@link DocumentCodec}): how many marks, then each one's layer (its index in {@code layers}),
     * kind (1 Lemon Juice, 0 Fading), time (ms), when it was laid and the clock time its pixels show (wall time, ms), how
     * many tiles, and each tile's key and its pixels before and as laid (tones then coverages each), as a page's are.
     * Marks on a layer not in {@code layers} are left out. Inkwash: a mark on a kind plane names it by its layer and
     * slot ({@link ToneDocument#address}: {@code layer | slot << 4}). {@code media}: Later's media's marks instead, in a
     * section of their own (LATR, the same layout, kinds 2 to 5, each tile as laid and as finished), so a version before
     * them skips it and opens the page as it showed; Fading's and Lemon Juice's section stays as it was, byte for byte.
     */
    static byte[] encode(java.util.List<Mark> marks, java.util.List<ToneDocument.Layer> layers, boolean media) throws java.io.IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
        int count = 0;
        for (Mark m : marks) if (media(m.kind) == media && ToneDocument.address(layers, m.layer) >= 0) count++;
        out.writeInt(count);
        for (Mark m : marks) {
            int layer = ToneDocument.address(layers, m.layer);
            if (layer < 0 || media(m.kind) != media) continue;
            out.writeByte(layer); out.writeByte(m.kind); out.writeInt(m.ms); out.writeLong(m.born); out.writeLong(m.shown);
            out.writeInt(m.keys.length);
            for (int j = 0; j < m.keys.length; j++) { out.writeInt(m.keys[j]); out.write(m.before[j]); out.write(m.after[j]); }
        }
        out.flush();
        return bytes.toByteArray();
    }
    /**
     * {@link #encode}'s marks on a {@code width × height} page whose layers are {@code layers}; refuses a section that
     * isn't one. {@code media}: a LATR section, where a newer version's kind is read past and left out (its pixels stay
     * as saved).
     */
    static java.util.List<Mark> decode(byte[] section, int width, int height, java.util.List<ToneDocument.Layer> layers, boolean media) throws java.io.IOException {
        java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(section));
        java.util.List<Mark> marks = new java.util.ArrayList<>();
        int tile = ToneDocument.TILE, cols = (width + tile - 1) / tile, rows = (height + tile - 1) / tile;
        try {
            int count = in.readInt();
            if (count < 0 || count > MOST_MARKS) throw new java.io.IOException("Invalid fading section");
            for (int i = 0; i < count; i++) {
                int layer = in.readUnsignedByte(), kind = in.readUnsignedByte(), ms = in.readInt();
                long born = in.readLong(), shown = in.readLong();
                int n = in.readInt();
                ToneDocument.Layer on = ToneDocument.at(layers, layer);
                if (on == null || (media ? kind < SALT : kind > LEMON) || ms < 1 || ms > MOST_MS || shown < -1 || n < 1 || n > cols * rows) throw new java.io.IOException("Invalid fading section");
                int[] keys = new int[n];
                byte[][] before = new byte[n][], after = new byte[n][];
                java.util.HashSet<Integer> seen = new java.util.HashSet<>();
                for (int j = 0; j < n; j++) {
                    int key = in.readInt();
                    if (key < 0 || key >= cols * rows || !seen.add(key)) throw new java.io.IOException("Invalid fading section");
                    int x0 = key % cols * tile, y0 = key / cols * tile, size = 2 * Math.min(tile, width - x0) * Math.min(tile, height - y0);
                    keys[j] = key; before[j] = new byte[size]; after[j] = new byte[size];
                    in.readFully(before[j]); in.readFully(after[j]);
                }
                if (kind <= NEWEST) marks.add(new Mark(on, width, height, kind, ms, born, shown, keys, before, after, null));
            }
            if (in.read() != -1) throw new java.io.IOException("Invalid fading section");
        } catch (java.io.EOFException short_) { throw new java.io.IOException("Short fading section"); }
        return marks;
    }
    static int onWhite(int tone, int alpha) { return (tone * alpha + 255 * (255 - alpha) + 127) / 255; }
}
