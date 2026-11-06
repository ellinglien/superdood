package io.github.mpdairy.monopaint;

/**
 * The paper noise a six-part brush's stroke reads, 0 to 1 (spec "Stampers and buffers"): the prototype's
 * {@code surf.fine} and {@code surf.mid} are the brush texture's grain widened ({@link PaperGrain#widened}), its
 * {@code surf.low} and {@code surf.blob} the app's paper blooms and blobs ({@link PaperNoise}). All fixed to the page. Pure.
 *
 * <p>A texture's grain is made the first time a mark reads it, not when a brush is resolved: making a surface takes
 * seconds on the Manta (only the last two are kept, {@link BrushTexture#grain}), and the drawing screen resolves a
 * brush as named for its ring and trail at every pen-down (2026-11-02: 3.4 s of a Newsprint stroke's start).
 *
 * <p>On a {@link FineGrid} ({@link #on}), the same paper read at a finer grid's points (sharp dry brushes): each
 * reading is the page's grain at the point's place, between the page's pixels bilinearly, so the grain is the page's
 * own, bigger and smooth when zoomed, as paper under a magnifier (the owner, 2026-11-04). Its readers take grid points.
 */
final class StrokePaper {
    private final BrushTexture texture;
    /** Made on first read; a race makes it twice at worst, the same grain ({@link PaperGrain}'s fields are final). */
    private PaperGrain grain;
    /** On a fine grid: the grid, and the paper itself (whose readers take page pixels); else null and this. */
    private final FineGrid fine;
    private final StrokePaper page;

    StrokePaper(BrushTexture texture) { this.texture = texture; fine = null; page = this; }
    /** On {@code grain} (the checks give a paper with no texture). */
    StrokePaper(PaperGrain grain) { this.texture = null; this.grain = grain; fine = null; page = this; }
    private StrokePaper(StrokePaper page, FineGrid fine) { this.texture = page.texture; this.grain = page.grain; this.page = page; this.fine = fine; }
    private PaperGrain grain() { PaperGrain g = grain; return g != null ? g : (grain = page != this ? page.grain() : texture.grain()); }

    /** The page pixel point {@code x} (a column; {@link #pageY}: a row) lies in: itself off a grid. For maps fixed to page pixels (a crayon's). */
    int pageX(int x) { return fine == null ? x : fine.pageOf(x); }
    /** Where point {@code x} (a column; {@link #indexY}: a row) falls among the page's pixel centres (pixel {@code i}'s at {@code i}): itself off a grid. */
    double indexX(int x) { return fine == null ? x : fine.pixelX(x); }
    double indexY(int y) { return fine == null ? y : fine.pixelY(y); }
    int pageY(int y) { return fine == null ? y : fine.pageOfY(y); }
    /** The page's height, {@code h} off a grid. */
    int pageHeight(int h) { return fine == null ? h : fine.pageHeight; }
    /** This paper read at {@code grid}'s points (a grid of 1 to a pixel at no offset reads as the page). */
    StrokePaper on(FineGrid grid) { return new StrokePaper(page, grid); }
    /** The paper as the page's pixels read it (itself, off a grid). */
    StrokePaper page() { return page; }

    /**
     * How much of the paper's grain every mark shows, its fine and mid noises drawn this far toward their middle: every
     * brush on every surface keeps its look, the grain a little quieter (the owner, 2026-11-02: "tone texture down
     * systemwide a tiny bit").
     */
    static final float SHOWS = .85f;
    float fine(int x, int y) { return fine == null ? fineAt(x, y) : read(0, x, y); }
    float mid(int x, int y) { return fine == null ? midAt(x, y) : read(1, x, y); }
    float low(int x, int y) { return fine == null ? PaperNoise.low(x, y) / 255f : read(2, x, y); }
    float blob(int x, int y) { return fine == null ? PaperNoise.blob(x, y) / 255f : read(3, x, y); }
    private float fineAt(int x, int y) { PaperGrain g = grain(); return .5f + SHOWS * (g.widened(g.fine(x, y) / 255f) - .5f); }
    private float midAt(int x, int y) { PaperGrain g = grain(); return .5f + SHOWS * (g.widened(g.mid(x, y) / 255f) - .5f); }
    /** On a grid: noise {@code which} (fine, mid, low, blob) at point {@code x, y}, as {@link #fine} and the rest read the page at its place. */
    private float at(int which, int x, int y) {
        double px = fine.pixelX(x), py = fine.pixelY(y);
        if (which >= 2) return between(null, which, px, py) / 255f;
        PaperGrain g = grain();
        return .5f + SHOWS * (g.widened(between(g, which, px, py) / 255f) - .5f);
    }
    /**
     * The grid's readings kept as they are read, NaN until then (fine, mid, low, blob; a stick's fine, mid, low): the
     * stampers read each point many times over (a dab's overlap). Made before the cores read them ({@link #ready}); two
     * cores reading a point at once work out the same value.
     */
    private float[][] kept;
    private float read(int which, int x, int y) {
        float[][] k = kept;
        float[] t = k == null ? null : k[which];
        if (t == null || x < 0 || y < 0 || x >= fine.width || y >= fine.height) return at(which, x, y);
        int i = y * fine.width + x;
        float v = t[i];
        if (v != v) t[i] = v = at(which, x, y);
        return v;
    }
    /**
     * On a grid, before a stroke's marks are laid on several cores: where its readings are kept, its stick's grain
     * {@code s} (or none) the one kept. Returns itself.
     */
    StrokePaper ready(StickGrain s) {
        if (fine == null) return this;
        // The soft noises (low, blob) are read once a point: not kept.
        if (kept == null) { float[][] k = new float[7][]; k[0] = unread(); k[1] = unread(); kept = k; }
        if (s != stuck) { stuck = s; for (int w = 4; w < 7; w++) kept[w] = s == null ? null : unread(); }
        return this;
    }
    /** The paper's height at {@code x, y}, 0 to 1: its fine and mid noises, as the line rules ({@link ModularCompose}) and Reprint ({@link Carry}) read it. */
    double height(int x, int y) { return fine(x, y) * .65 + mid(x, y) * .35; }

    /** A charcoal stick's own grain ({@link StickGrain}) at {@code x, y}: its fine (0), mid (1) or low (2) noise, as this paper reads its points. */
    float stick(StickGrain s, int which, int x, int y) {
        if (fine == null) return stickAt(s, which, x, y);
        float[][] k = kept;
        float[] t = k == null || s != stuck ? null : k[4 + which];
        if (t == null || x < 0 || y < 0 || x >= fine.width || y >= fine.height) return stickBetween(s, which, x, y);
        int i = y * fine.width + x;
        float v = t[i];
        if (v != v) t[i] = v = stickBetween(s, which, x, y);
        return v;
    }
    private float[] unread() { float[] t = new float[fine.width * fine.height]; java.util.Arrays.fill(t, Float.NaN); return t; }
    /** The stick whose grain {@link #kept} keeps ({@link #ready}). */
    private StickGrain stuck;
    private float stickBetween(StickGrain s, int which, int x, int y) {
        double px = fine.pixelX(x), py = fine.pixelY(y);
        int x0 = (int) Math.floor(px), y0 = (int) Math.floor(py);
        float tx = (float) (px - x0), ty = (float) (py - y0);
        float a = stickAt(s, which, x0, y0), b = stickAt(s, which, x0 + 1, y0), c = stickAt(s, which, x0, y0 + 1), d = stickAt(s, which, x0 + 1, y0 + 1);
        return mix(mix(a, b, tx), mix(c, d, tx), ty);
    }
    private static float stickAt(StickGrain s, int which, int x, int y) { return which == 0 ? s.fine(x, y) : which == 1 ? s.mid(x, y) : s.low(x, y); }

    /** Table {@code which} (fine, mid, low, blob) at page pixel position {@code px, py} (pixel centres at whole numbers), bilinearly. */
    private static float between(PaperGrain g, int which, double px, double py) {
        int x0 = (int) Math.floor(px), y0 = (int) Math.floor(py);
        float tx = (float) (px - x0), ty = (float) (py - y0);
        float a = at(g, which, x0, y0), b = at(g, which, x0 + 1, y0), c = at(g, which, x0, y0 + 1), d = at(g, which, x0 + 1, y0 + 1);
        return mix(mix(a, b, tx), mix(c, d, tx), ty);
    }
    private static float at(PaperGrain g, int which, int x, int y) {
        return which == 0 ? g.fine(x, y) : which == 1 ? g.mid(x, y) : which == 2 ? PaperNoise.low(x, y) : PaperNoise.blob(x, y);
    }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
