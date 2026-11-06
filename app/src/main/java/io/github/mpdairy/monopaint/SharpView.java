package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;

/**
 * Sharp dry brushes when zoomed (docs/superpowers/specs/2026-11-04-vector-dry-brushes-design.md sections 5 and 6): the
 * page in tiles of {@link #TILE} page pixels, each drawn {@code k} points to a pixel on a thread of its own at the
 * lowest priority, kept until the page changes there. A tile reads what it needs from the page when asked
 * ({@link #want}, on the page's thread) and draws off it. The screen shows the page's pixels until a tile is ready
 * (the host shows it then, {@code ready}); the owner, 2026-11-04: "1 yes".
 *
 * <p>The match (section 5): for each plane holding kept strokes ({@link SharpStrokes}), its kept strokes drawn again at
 * 1 to a pixel over white; a page pixel is drawn finer only where every such plane holds there exactly what that gives,
 * and the screen showed the page's own composite there ({@code shown}: no Lasso piece, guide or mark over it). There the
 * tile is the page composed again point by point (the {@link ToneDocument#visibleLayers} the screen composes, by its
 * arithmetic), each kept plane at its finer drawing; elsewhere it leaves the screen as it is (−1). Whatever else touched
 * a plane (a smudge, white chalk, light over dark, another tablet's marks, the Lasso, the eraser's bite) shows as pixels,
 * in the order it was drawn. Pure: no Android.
 */
final class SharpView {
    /** A tile's side, page px. */
    static final int TILE = 64;
    /** The most points the tiles kept hold together, any grid (two bytes each: 24 MB), the least used going first: a screen's at any zoom fits. */
    static final int MOST_POINTS = 12 << 20;
    private long points;
    /** A tile that leaves the screen as it is (nothing kept sharp there). */
    static final short[] NONE = new short[0];

    /**
     * Where a tile drawn at grid {@code k} keeps page pixel ({@code x}, {@code y})'s composite as the screen showed it
     * (−1: not drawn finer): a tile is laid on the screen only where the page's pixel still shows that, so whatever is
     * shown over the page since (the pen's ring, a Lasso piece) stays.
     */
    static int whole(int k, int x, int y) { return TILE * k * TILE * k + y * TILE + x; }

    /** The grid a view at {@code scale} screen px to a page px draws at: 0 at 125% and under (the page's pixels), else 2, 4 or 8 (from 400%). */
    static int stepFor(double scale) { return scale <= 1.25 ? 0 : scale <= 2 ? 2 : scale <= 4 ? 4 : 8; }

    /** Reads the screen's raster (plain greys) over page px {@code x, y, w × h} into {@code out}, row by row, on the page's thread. */
    interface Shown { void greys(int x, int y, int w, int h, int[] out); }

    private final java.util.function.Consumer<int[]> ready;
    /** Tiles drawn, by key ({@link #key}): the newest used last. */
    private final java.util.LinkedHashMap<Long, short[]> tiles = new java.util.LinkedHashMap<>(64, .75f, true);
    /** Each tile's count of changes, its key's: a tile drawn before the latest is dropped. */
    private final java.util.HashMap<Long, Integer> changes = new java.util.HashMap<>();
    private final java.util.HashSet<Long> asked = new java.util.HashSet<>();
    private int epoch;
    /** The grid the view draws at now; tiles of another wait or are dropped. */
    private volatile int now;
    private final java.util.concurrent.LinkedBlockingDeque<Job> jobs = new java.util.concurrent.LinkedBlockingDeque<>();
    private final java.util.Map<StrokeLog.Entry, Object> laid = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    private Thread worker;

    /** {@code ready} hears of each tile drawn, its page rectangle {left, top, right, bottom} exclusive, on the drawing thread. */
    SharpView(java.util.function.Consumer<int[]> ready) { this.ready = ready; }

    private static long key(int k, int tx, int ty) { return (long) k << 48 | (long) (ty & 0xffffff) << 24 | (tx & 0xffffff); }

    /** The tile at grid {@code k} holding page pixel column {@code tx}, row {@code ty} (in tiles), or null not drawn yet ({@link #NONE}: nothing sharp). */
    synchronized short[] tile(int k, int tx, int ty) { return tiles.get(key(k, tx, ty)); }
    /** Whether any tile of grid {@code k} over page px {@code x0..x1, y0..y1} (exclusive) holds something sharp. */
    synchronized boolean shows(int k, int x0, int y0, int x1, int y1) {
        for (int ty = Math.floorDiv(y0, TILE); ty * TILE < y1; ty++) for (int tx = Math.floorDiv(x0, TILE); tx * TILE < x1; tx++) {
            short[] t = tiles.get(key(k, tx, ty));
            if (t != null && t != NONE) return true;
        }
        return false;
    }

    /** The page changed over {@code rect} (page px, {left, top, right, bottom}, exclusive): its tiles are drawn again when next wanted. */
    synchronized void changed(int[] rect) {
        if (rect == null) return;
        for (int ty = Math.floorDiv(rect[1] - 1, TILE); ty * TILE <= rect[3]; ty++) for (int tx = Math.floorDiv(rect[0] - 1, TILE); tx * TILE <= rect[2]; tx++)
            for (int k = 2; k <= 8; k <<= 1) {
                long key = key(k, tx, ty);
                short[] t = tiles.remove(key);
                if (t != null) points -= t.length;
                asked.remove(key); changes.merge(key, 1, Integer::sum);
            }
    }
    /** Another page, or the whole page changed: every tile goes. */
    synchronized void clear() { tiles.clear(); points = 0; asked.clear(); changes.clear(); jobs.clear(); epoch++; }

    /**
     * The view shows page px {@code x0..x1, y0..y1} (exclusive) at grid {@code k} ({@link #stepFor}; 0: none): each tile
     * there not drawn nor on its way is read from {@code page} now and drawn on the worker, the screen as {@code shown}
     * gives it, the layers as the screen composes them ({@code ghost}, {@code layerSteps}, {@code without}: {@link
     * ToneDocument#visibleLayers}). Tiles with no kept stroke over them are {@link #NONE} at once. On the page's thread.
     */
    void want(ToneDocument page, int k, int x0, int y0, int x1, int y1, GhostPlane.Planes ghost, int layerSteps, int without, Shown shown) {
        now = k;
        if (k == 0) { jobs.clear(); return; }
        List<Kept> kept = kept(page);
        x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(page.width, x1); y1 = Math.min(page.height, y1);
        for (int ty = y0 / TILE; ty * TILE < y1; ty++) for (int tx = x0 / TILE; tx * TILE < x1; tx++) {
            long key = key(k, tx, ty);
            int stamp;
            synchronized (this) {
                if (tiles.containsKey(key) || asked.contains(key)) continue;
                stamp = changes.getOrDefault(key, 0);
            }
            Area area = area(page, kept, k, tx, ty, ghost, layerSteps, without, shown);
            if (area == null) { synchronized (this) { put(key, NONE); } continue; }
            synchronized (this) { asked.add(key); }
            jobs.addLast(new Job(key, stamp, epoch, area));
            start();
        }
    }
    /** Tile {@code tx, ty} at grid {@code k} as {@link #want} reads it, or null where no kept stroke lies over it. */
    private static Area area(ToneDocument page, List<Kept> kept, int k, int tx, int ty, GhostPlane.Planes ghost, int layerSteps, int without, Shown shown) {
        int x = tx * TILE, y = ty * TILE, w = Math.min(TILE, page.width - x), h = Math.min(TILE, page.height - y);
        List<Kept> here = new ArrayList<>();
        for (Kept s : kept) if (s.box[0] < x + w && s.box[2] > x && s.box[1] < y + h && s.box[3] > y) here.add(s);
        return here.isEmpty() ? null : Area.of(page, k, x, y, w, h, here, ghost, layerSteps, without, shown);
    }
    /**
     * High Res's PNG ({@link PngRender#high}): the tiles of tile row {@code ty} at grid {@code k} drawn now, on this
     * thread, the rows before dropped: a page drawn finer a strip at a time, {@code without} as {@link #want} takes it.
     */
    void drawRow(ToneDocument page, int k, int ty, int without) {
        clear();
        now = k;
        List<Kept> kept = kept(page);
        for (int tx = 0; tx * TILE < page.width; tx++) {
            Area area = area(page, kept, k, tx, ty, null, 0, without, null);
            short[] t = area == null ? NONE : render(area);
            synchronized (this) { put(key(k, tx, ty), t); }
        }
    }
    /**
     * The tiles at grid {@code k} over {@code inner}, raster px {@code x, y, w × h} (plain greys), page point (px, py) at
     * ({@code px * scale + ox}, {@code py * scale + oy}): each raster pixel the grid point its centre falls on, where a
     * tile holds one and the raster's pixel there shows what the tile was drawn over ({@link #whole}); else as it is. The
     * zoomed screen's ({@code ViewportBitmap}) and High Res's.
     */
    void over(int k, double scale, double ox, double oy, int x, int y, int w, int h, int[] inner) {
        int side = TILE * k; short[] tile = null; int at = Integer.MIN_VALUE;
        for (int row = 0; row < h; row++) {
            int fy = (int) Math.floor((y + row + .5 - oy) / scale * k), ty = Math.floorDiv(fy, side);
            for (int col = 0; col < w; col++) {
                int fx = (int) Math.floor((x + col + .5 - ox) / scale * k), tx = Math.floorDiv(fx, side);
                int key = ty * 65536 + tx;
                if (key != at) { at = key; tile = tile(k, tx, ty); }
                if (tile == null || tile == NONE) continue;
                int lx = fx - tx * side, ly = fy - ty * side, v = tile[ly * side + lx];
                // Only where the raster's pixel shows what the tile was drawn over (not the pen's ring, a Lasso piece, a magnet).
                if (v >= 0 && tile[whole(k, lx / k, ly / k)] == (inner[row * w + col] & 255)) inner[row * w + col] = 0xff000000 | v * 0x010101;
            }
        }
    }
    private void put(long key, short[] t) {
        short[] was = tiles.put(key, t);
        points += t.length - (was == null ? 0 : was.length);
        for (java.util.Iterator<short[]> it = tiles.values().iterator(); points > MOST_POINTS && it.hasNext(); ) { points -= it.next().length; it.remove(); }
    }
    private synchronized void start() {
        if (worker != null) return;
        worker = new Thread(this::work, "Inkwash sharp tiles");
        worker.setDaemon(true); worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }
    private void work() {
        Cores.quiet(true);
        try {
            while (true) {
                // The newest asked first: what the view shows now.
                Job j = jobs.takeLast();
                if (j.area.k != now) { synchronized (this) { asked.remove(j.key); } continue; }
                short[] t = render(j.area);
                boolean fresh;
                synchronized (this) {
                    asked.remove(j.key);
                    fresh = j.epoch == epoch && changes.getOrDefault(j.key, 0) == j.stamp;
                    if (fresh) put(j.key, t);
                }
                if (fresh && t != NONE) ready.accept(new int[]{j.area.x, j.area.y, j.area.x + j.area.w, j.area.y + j.area.h});
            }
        } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
    }
    private static final class Job {
        final long key; final int stamp, epoch; final Area area;
        Job(long key, int stamp, int epoch, Area area) { this.key = key; this.stamp = stamp; this.epoch = epoch; this.area = area; }
    }

    /** A kept stroke on the page shown: its entry, its brush as laid, where it can lay anything. */
    static final class Kept {
        final StrokeLog.Entry e; final SharpStrokes.Laid laid; final int[] box;
        /** Its widest reach, page px, once drawn ({@link SharpStrokes#cull}); −1 before. */
        volatile int widest = -1;
        Kept(StrokeLog.Entry e, SharpStrokes.Laid laid, int[] box) { this.e = e; this.laid = laid; this.box = box; }
    }
    /** The page's kept strokes as its Undo step stands, in the order drawn; their brushes worked out once. */
    List<Kept> kept(ToneDocument page) { return kept(page, laid); }
    /** As above, the brushes worked out kept in {@code laid} (a host without the view's thread: the browser's). */
    static List<Kept> kept(ToneDocument page, java.util.Map<StrokeLog.Entry, Object> laid) {
        List<Kept> out = new ArrayList<>();
        for (StrokeLog.Entry e : page.replay()) {
            if (!e.sharp()) continue;
            Object k = laid.get(e);
            if (k == null) {
                SharpStrokes.Laid l = SharpStrokes.Laid.of(e, page.surface);
                k = l == null ? Boolean.FALSE : new Kept(e, l, SharpStrokes.box(e, l.r));
                laid.put(e, k);
            }
            if (k instanceof Kept) out.add((Kept) k);
        }
        return out;
    }

    /**
     * What a tile is drawn from, read from the page on its thread: page px {@code x, y, w × h} at grid {@code k}; the
     * layers the screen composes there, each its tones and coverage copied, its opacity, and which are planes with kept
     * strokes (their strokes over the tile, in the order drawn); the screen's greys there (null: not compared); the paper.
     */
    static final class Area {
        final int k, x, y, w, h, pageWidth, pageHeight, paper, count;
        final byte[][] tones, alpha; final int[] opacity;
        /** Per layer: its kept strokes over the tile, or null for a layer that has none. */
        final List<List<Kept>> strokes;
        final int[] shown;
        private Area(ToneDocument page, int k, int x, int y, int w, int h, List<Kept> here, GhostPlane.Planes ghost, int layerSteps, int without, Shown screen) {
            this.k = k; this.x = x; this.y = y; this.w = w; this.h = h; pageWidth = page.width; pageHeight = page.height; paper = page.paperTone();
            byte[][] t = new byte[ToneDocument.STACK + 3][], a = new byte[ToneDocument.STACK + 3][];
            int[] o = new int[ToneDocument.STACK + 3];
            count = page.visibleLayers(t, a, o, ghost == null ? null : ghost.under, layerSteps, ghost == null ? null : ghost.overTones, ghost == null ? null : ghost.overAlpha, without);
            tones = new byte[count][]; alpha = new byte[count][]; opacity = java.util.Arrays.copyOf(o, count);
            strokes = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                tones[i] = new byte[w * h]; alpha[i] = new byte[w * h];
                for (int r = 0; r < h; r++) { System.arraycopy(t[i], (y + r) * page.width + x, tones[i], r * w, w); System.arraycopy(a[i], (y + r) * page.width + x, alpha[i], r * w, w); }
                List<Kept> on = null;
                for (Kept s : here) {
                    ToneDocument.Layer plane = page.addressed(s.e.plane);
                    if (plane != null && plane.tones == t[i]) { if (on == null) on = new ArrayList<>(); on.add(s); }
                }
                strokes.add(on);
            }
            if (screen == null) shown = null;
            else { shown = new int[w * h]; screen.greys(x, y, w, h, shown); }
        }
        static Area of(ToneDocument page, int k, int x, int y, int w, int h, List<Kept> here, GhostPlane.Planes ghost, int layerSteps, int without, Shown shown) {
            return new Area(page, k, x, y, w, h, here, ghost, layerSteps, without, shown);
        }
    }

    /**
     * Tile {@code a} drawn: {@code (TILE k)²} greys, row by row, −1 where the screen is left as it is, then ({@link #whole})
     * each page pixel's composite where it is drawn finer; {@link #NONE} where nothing in it is.
     */
    static short[] render(Area a) {
        int w = a.w, h = a.h, k = a.k, n = w * h;
        boolean[] matched = new boolean[n];
        java.util.Arrays.fill(matched, true);
        SharpStrokes.GridPage[] fine = new SharpStrokes.GridPage[a.count];
        boolean any = false;
        for (int i = 0; i < a.count; i++) {
            List<Kept> on = a.strokes.get(i);
            if (on == null) continue;
            SharpStrokes.GridPage once = drawn(a, on, 1);
            boolean some = false;
            byte[] t = a.tones[i], al = a.alpha[i];
            for (int p = 0; p < n; p++) {
                if (!holds(once, p, t[p] & 255, al[p] & 255)) matched[p] = false; else if (once.alpha(p) > 0) some = true;
            }
            if (!some) continue;
            fine[i] = drawn(a, on, k);
            any = true;
        }
        if (!any) return NONE;
        // The tile's points row by row, a whole tile's side (a tile at the page's edge leaves the rest −1); the planes' drawings the grid's.
        // Then each page pixel's composite as the screen showed it ({@link #WHOLE}).
        int side = TILE * k, gw = w * k;
        short[] out = new short[side * side + TILE * TILE];
        java.util.Arrays.fill(out, (short) -1);
        boolean laid = false;
        for (int py = 0; py < h; py++) for (int px = 0; px < w; px++) {
            int p = py * w + px;
            if (!matched[p]) continue;
            // The pixel as the screen composed it: where it shows something else, it stays.
            int whole = a.paper;
            for (int i = 0; i < a.count; i++) whole = over(whole, a.tones[i][p] & 255, a.alpha[i][p] & 255, a.opacity[i]);
            if (a.shown != null && a.shown[p] != whole) continue;
            if (!finer(fine, a, p, px, py, gw)) continue;
            out[side * side + py * TILE + px] = (short) whole;
            for (int j = 0; j < k; j++) for (int q = 0; q < k; q++) {
                int f = (py * k + j) * gw + px * k + q, v = a.paper;
                for (int i = 0; i < a.count; i++) {
                    if (fine[i] != null) v = over(v, fine[i].raw(f), fine[i].alpha(f), a.opacity[i]);
                    else v = over(v, a.tones[i][p] & 255, a.alpha[i][p] & 255, a.opacity[i]);
                }
                out[(py * k + j) * side + px * k + q] = (short) v;
            }
            laid = true;
        }
        return laid ? out : NONE;
    }
    /** Whether page pixel {@code p} (column {@code px}, row {@code py}) of {@code a} has a kept stroke's mark at 1 to a pixel or at any of its points. */
    private static boolean finer(SharpStrokes.GridPage[] fine, Area a, int p, int px, int py, int gw) {
        int k = a.k;
        for (int i = 0; i < a.count; i++) {
            if (fine[i] == null) continue;
            if ((a.alpha[i][p] & 255) > 0) return true;
            for (int j = 0; j < k; j++) for (int q = 0; q < k; q++) if (fine[i].alpha((py * k + j) * gw + px * k + q) > 0) return true;
        }
        return false;
    }
    /** The native composite's step ({@code layers.h} over, {@link ToneDocument#compositeTones}): {@code tone} at {@code alpha}, {@code opacity} percent, over {@code value}. */
    private static int over(int value, int tone, int alpha, int opacity) {
        int c = (alpha * opacity + 50) / 100;
        return (tone * c + value * (255 - c) + 127) / 255;
    }
    /** Whether a plane pixel of {@code tone} at {@code alpha} holds just what its kept strokes lay over white there ({@code once}'s point {@code p}, drawn at 1 to a pixel): its coverage, and its raw tone where it has any. */
    static boolean holds(SharpStrokes.GridPage once, int p, int tone, int alpha) { int cover = once.alpha(p); return alpha == cover && (cover == 0 || tone == once.raw(p)); }
    /** Area {@code a}'s kept strokes {@code on} (one plane's) drawn again at {@code k} to a pixel over white: its points row by row. */
    static SharpStrokes.GridPage drawn(Area a, List<Kept> on, int k) { return drawn(a.x, a.y, a.w, a.h, a.pageWidth, a.pageHeight, on, k); }
    /** As above, over page px {@code x, y, w × h} of a {@code pageWidth × pageHeight} page: High Res's Layers copy too. */
    static SharpStrokes.GridPage drawn(int x, int y, int w, int h, int pageWidth, int pageHeight, List<Kept> on, int k) {
        FineGrid grid = FineGrid.over(k, x, y, w, h, pageWidth, pageHeight);
        SharpStrokes.GridPage page = new SharpStrokes.GridPage(grid.width, grid.height);
        // Each stroke's paper (the page's surface, or with none the brush's own), read at the grid's points, kept across the strokes.
        java.util.Map<BrushTexture, StrokePaper> papers = new java.util.EnumMap<>(BrushTexture.class);
        for (Kept s : on) {
            StrokePaper paper = s.laid.r == null ? null : papers.computeIfAbsent(s.laid.r.paper, t -> new StrokePaper(t).on(grid));
            int wide = SharpStrokes.draw(s.e, s.laid, paper, grid, page, SharpStrokes.cull(s.widest));
            if (s.widest < 0) s.widest = wide;
        }
        return page;
    }
}
