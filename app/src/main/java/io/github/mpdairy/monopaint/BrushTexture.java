package io.github.mpdairy.monopaint;

import java.io.IOException;

/**
 * The surfaces (the surface spec, docs/superpowers/specs/2026-11-01-surface-design.md; the owner, 2026-11-01: "Surface
 * moves to the notebook"): the paper a notebook's pages, or one page, are on, which every brush on them meets ({@link
 * ModularBrush#onSurface}); a brush's Paper part keeps only its Texture, how much of the surface shows through it. In
 * order: plain (Smooth), the app's paper; then cold press, laid, felt, crumpled and canvas (once CC0 height maps from
 * ambientCG); then eight Unsplash photos the owner picked (2026-10-22: watercolour, rough watercolour, unryu, old library
 * paper, kraft, old buff, blue fibre, fine speck; credits in THIRD_PARTY_NOTICES.md), stored in assets/textures as 256 ×
 * 256 raw 8-bit, normalised to mean 128, spread 52. Each is made whole, wider than a page, by {@link SurfaceGrain}: cold
 * press, laid, canvas, kraft and unryu from nothing, the rest from their scans. Never reorder: share codes and notebooks
 * carry the index. Append only.
 *
 * <p>Each has physical properties every effect reads rather than tuning itself paper by paper: its grain ({@link
 * #grain}), how readily it drinks ({@link #absorbency}: feathering's reach, a wash's edge) and its fibres (how long,
 * which way, and a map where the grain has one, {@link PaperGrain#fibre}).
 */
enum BrushTexture {
    PLAIN("plain", null, .35f, 3, Float.NaN), COLD_PRESS("cold press", "cold-press", .9f, 4, Float.NaN), LAID("laid", "laid", .85f, 6, 0),
    FELT("felt", "felt", 1.3f, 14, Float.NaN), CRUMPLED("crumpled", "crumpled", .9f, 4, Float.NaN), CANVAS("canvas", "canvas", .85f, 8, Float.NaN),
    WATERCOLOUR("watercolour", "watercolour", 1.25f, 6, Float.NaN), ROUGH("rough", "rough", 1.2f, 6, Float.NaN), UNRYU("unryu", "unryu", 1.45f, 250, Float.NaN),
    LIBRARY("library", "nypl-old", 1.15f, 8, Float.NaN), KRAFT("kraft", "kraft", .95f, 25, 0), OLD_BUFF("old buff", "old-buff", 1.05f, 8, Float.NaN),
    BLUE_FIBRE("blue fibre", "blue-fibre", 1.25f, 60, 0), FINE_SPECK("fine speck", "fine-speck", .75f, 3, Float.NaN);

    /** How many an old Wild brush's I/P code may name (plain to canvas): the eight after came with six-part brushes only. */
    static final int LEGACY = 6;
    /** A notebook's surface when it names none: every notebook before surfaces (the owner: no legacy mode). */
    static final BrushTexture DEFAULT = PLAIN;

    final String label;
    private final String file;
    /**
     * How readily it drinks ink and water, at its Texture's default: plain (Smooth) barely; the soft and fibrous ones
     * (felt, unryu, blue fibre, rough) most. Cold press, .9, is the paper the washes were tuned on ({@link
     * ResolvedBrush.Wet#wash}). Feathering's per-paper drink until 2026-11-01.
     */
    final float absorbency;
    /** Its fibres: about how long, px, and the way most run, radians (NaN: any way). */
    final float fibreLength, fibreAngle;

    BrushTexture(String label, String file, float absorbency, float fibreLength, float fibreAngle) {
        this.label = label; this.file = file; this.absorbency = absorbency; this.fibreLength = fibreLength; this.fibreAngle = fibreAngle;
    }

    /** The surface at {@code index} (a file's or a code's), or {@link #DEFAULT} for one this version doesn't know. */
    static BrushTexture of(int index) { return index >= 0 && index < values().length ? values()[index] : DEFAULT; }

    /** The surfaces made, the most recently used last: {@link #KEPT} at most besides plain (each about 8 MB, 12 with fibres). */
    private static final java.util.LinkedHashMap<BrushTexture, PaperGrain> made = new java.util.LinkedHashMap<>(4, .75f, true);
    private static final int KEPT = 2;

    /**
     * The grain: the paper's for plain, else made whole ({@link SurfaceGrain}) the first time and kept while it is one of
     * the last two used (making one takes a moment on the tablet, so the drawing screen asks for a page's when it opens).
     * A scan that can't be read fails loudly rather than drawing plain: looks and samples are kept by the brush's
     * settings, texture included, so a plain stand-in would be kept as this texture's look. The scans ship inside the
     * app, so it means a broken build.
     */
    /**
     * The grain a Surface tile shows: its 256 px scan as the brushes read it before 2026-11-01 (plain's own grain).
     * shortcut: the scan stands in for the whole surface, which takes seconds a surface to make on the tablet (fourteen
     * tiles at once); make small whole ones if the tiles mislead.
     */
    PaperGrain swatch() {
        if (file == null) return PaperGrain.PLAIN;
        try { return PaperGrain.scan(Assets.bytes("textures/" + file + ".bin")); }
        catch (IOException e) { throw new IllegalStateException("Can't read the " + label + " texture", e); }
    }
    /** Makes the grain now, out of the way (a page on it opened), so the first stroke on it doesn't wait. */
    void warm() { if (file != null) WARMING.execute(this::grain); }
    private static final java.util.concurrent.Executor WARMING = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "surface"); t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY); return t;
    });

    PaperGrain grain() {
        if (file == null) return PaperGrain.PLAIN;
        synchronized (made) {
            PaperGrain g = made.get(this);
            if (g != null) return g;
            try { g = SurfaceGrain.of(this, Assets.bytes("textures/" + file + ".bin")); }
            catch (IOException | IllegalArgumentException e) { throw new IllegalStateException("Can't read the " + label + " texture", e); }
            made.put(this, g);
            if (made.size() > KEPT) made.remove(made.keySet().iterator().next());
            return g;
        }
    }
}
