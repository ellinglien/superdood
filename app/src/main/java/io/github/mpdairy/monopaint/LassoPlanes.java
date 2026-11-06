package io.github.mpdairy.monopaint;

/**
 * Inkwash: the Lasso's piece plane by plane ({@link FloatingSelection#planes}): what it lifted of the selected pair's
 * wash and ink ({@link InkWashLayers}) and of the masking fluid over them, each pixel's tone, coverage, the share the
 * eraser takes, print tone and kinds ({@link ToneDocument#copyPlanes}), so the piece placed anywhere, another page too,
 * looks and erases as it did. MonoPaint's {@link FloatingSelection} keeps the pose; its {@code tones} and {@code alpha}
 * are the piece's look, its ink over its wash, which the screen shows while it floats ({@link #shown}). Inkwash: each
 * half's kind planes too ({@link ToneDocument.Layer#planes}), each laid back on its plane.
 */
final class LassoPlanes {
    /**
     * The planes' roles, in the page's order ({@link ToneDocument#stacked}): {@code slot × 2 + half}, slot 0 a layer's base
     * and 1 to 4 its kind planes, half 0 the pair's wash and 1 its ink (a one-layer page's only layer); then the masking fluid.
     */
    static final int WASH = 0, INK = 1, FLUID = 2 * (1 + ToneDocument.PLANES), ROLES = FLUID + 1;
    /** Role {@code role}'s kind plane ({@link ToneDocument#PLANES}; -1: the layer's base). */
    private static int plane(int role) { return role / 2 - 1; }
    /** Each role's planes over the piece's pixels, row by row; null where it lifted nothing (no wash apart, no fluid). */
    final byte[][] tones = new byte[ROLES][], alpha = new byte[ROLES][], share = new byte[ROLES][], print = new byte[ROLES][], kind = new byte[ROLES][];
    /** Each role's kinds laid, all together, and the one kinds byte every covered pixel holds (-1: they differ), for {@link ToneDocument#layKinds}. */
    private final int[] kinds = new int[ROLES], uniform = new int[ROLES];
    /** The engine layers lifted from, by role (-1: the fluid). */
    private final int[] from = new int[ROLES];
    /** Record and Replay: the lift's entry in its page's log ({@link ToneDocument#logLift}); its placings name it. */
    private StrokeLog.Entry logged;
    /**
     * Vector Ink (the owner, 2026-11-04: "lasso not recognizing that vector ink is an image"): the page's lines layers
     * ({@link VectorInk#linedLayers}) the loop held lines of, as bits (0: none), and each one's lines outside the loop
     * (by layer); the loop (half pixels); the lines inside it as lifted, page px, each line the loop crosses cut flat there
     * ({@link VectorLines#split}); and their pixels inside the loop, for the piece's look. Placed, the lines inside go where
     * the piece lies, sharp ({@link VectorLines#posed}), on the top one, and their pixels are drawn from them ({@link VectorInk#relay}).
     */
    private int lineMask;
    private final VectorLines[] outside = new VectorLines[ToneDocument.MAX_LAYERS];
    private float[] loop;
    private int loopCount;
    private VectorLines inside;
    private byte[] lineAlpha;

    /**
     * Lifts what the selected pair and the masking fluid hold inside the loop through {@code count} points ({@code x, y}
     * pairs, page px, taken on half pixels as the page's log keeps them, {@link StrokeLog}); null when nothing. A lift is logged.
     */
    static FloatingSelection lift(ToneDocument document, float[] points, int count) {
        points = StrokeLog.Entry.halfPixels(points, count);
        int[] box = new int[4];
        byte[] inside = FloatingSelection.inside(document.width, document.height, points, count, box);
        if (inside == null) return null;
        LassoPlanes planes = new LassoPlanes();
        int pair = InkWashLayers.current(document), w = box[2], h = box[3], n = w * h;
        int wash = InkWashLayers.washOf(document, pair), ink = InkWashLayers.inkOf(document, pair);
        // Vector Ink's lines in the loop, and their pixels: the lines carry them (none of their layers lifted as pixels).
        int[] lined = VectorInk.linedLayers(document);
        int linedMask = VectorInk.linedMask(document);
        byte[] lineTones = null, lineAlpha = null;
        VectorLines in = VectorLines.NONE;
        // Bottom layer first, as the page shows them.
        for (int k = lined.length - 1; k >= 0; k--) {
            VectorLines[] parts = document.lines(lined[k]).split(points, count);
            if (parts[1].isEmpty()) continue;
            planes.lineMask |= 1 << lined[k]; planes.outside[lined[k]] = parts[0]; in = in.with(parts[1]);
            if (lineTones == null) { lineTones = new byte[n]; lineAlpha = new byte[n]; java.util.Arrays.fill(lineTones, (byte) 255); }
            if (!document.hasPlane(lined[k], ToneDocument.PLANE_PEN)) continue;
            byte[] t = new byte[n], a = new byte[n];
            document.copyPlanes(lined[k], ToneDocument.PLANE_PEN, box[0], box[1], w, h, t, a, new byte[n], new byte[n], new byte[n]);
            for (int i = 0; i < n; i++) {
                if (inside[i] == 0) continue;
                int laid = ToneDocument.sheet(t[i] & 255, a[i] & 255, lineTones[i] & 255, lineAlpha[i] & 255);
                if (laid != 0) { lineTones[i] = (byte) (laid >>> 8); lineAlpha[i] = (byte) laid; }
            }
        }
        if (planes.lineMask != 0) { planes.loop = points; planes.loopCount = count; planes.inside = in; }
        for (int role = 0; role < ROLES; role++) planes.from[role] = role == FLUID ? -1 : role % 2 == 0 ? wash : ink;
        for (int role = 0; role < ROLES; role++) {
            if (role != FLUID && role % 2 == 0 && wash == ink || role == FLUID && !document.hasMask()) continue;
            if ((linedMask >> planes.from[role] & 1) != 0) continue;
            if (role != FLUID && plane(role) >= 0 && !document.hasPlane(planes.from[role], plane(role))) continue;
            byte[] t = new byte[n], a = new byte[n], s = new byte[n], p = new byte[n], k = new byte[n];
            document.copyPlanes(planes.from[role], role == FLUID ? -1 : plane(role), box[0], box[1], w, h, t, a, s, p, k);
            boolean any = false;
            for (int i = 0; i < n; i++) if (inside[i] == 0) { t[i] = s[i] = p[i] = (byte) 255; a[i] = k[i] = 0; } else any |= a[i] != 0;
            // A plane with nothing in the loop is left out; the base roles stay as ever.
            if (!any && role != FLUID && plane(role) >= 0) continue;
            planes.tones[role] = t; planes.alpha[role] = a; planes.share[role] = s; planes.print[role] = p; planes.kind[role] = k;
        }
        byte[][] alphas = java.util.Arrays.copyOf(planes.alpha, ROLES + 1);
        alphas[ROLES] = lineAlpha;
        int[] found = FloatingSelection.found(alphas, w, h);
        if (found == null) return null;
        if (lineAlpha != null) { planes.lineAlpha = FloatingSelection.crop(lineAlpha, w, found); lineTones = FloatingSelection.crop(lineTones, w, found); }
        int pieceWidth = found[2], pieceHeight = found[3];
        for (int role = 0; role < ROLES; role++) {
            if (planes.alpha[role] == null) continue;
            planes.tones[role] = FloatingSelection.crop(planes.tones[role], w, found); planes.alpha[role] = FloatingSelection.crop(planes.alpha[role], w, found);
            planes.share[role] = FloatingSelection.crop(planes.share[role], w, found); planes.print[role] = FloatingSelection.crop(planes.print[role], w, found);
            planes.kind[role] = FloatingSelection.crop(planes.kind[role], w, found);
            int all = 0, one = -2;
            for (int i = 0; i < planes.alpha[role].length; i++) {
                if (planes.alpha[role][i] == 0) continue;
                int k = planes.kind[role][i] & 255;
                all |= k; one = one == -2 || one == k ? k : -1;
            }
            planes.kinds[role] = all & ToneDocument.KIND_ALL; planes.uniform[role] = one == -2 ? -1 : one;
        }
        // The look: its roles in the page's order, its ink over its wash.
        byte[] look = new byte[pieceWidth * pieceHeight], cover = new byte[look.length];
        java.util.Arrays.fill(look, (byte) 255);
        for (int role = 0; role <= FLUID; role++) {
            // Vector Ink's pixels last, over the pair (its layer is the top one).
            byte[] t = role == FLUID ? lineTones : planes.tones[role], a = role == FLUID ? planes.lineAlpha : planes.alpha[role];
            if (a != null) for (int i = 0; i < look.length; i++) {
                int laid = ToneDocument.sheet(t[i] & 255, a[i] & 255, look[i] & 255, cover[i] & 255);
                if (laid != 0) { look[i] = (byte) (laid >>> 8); cover[i] = (byte) laid; }
            }
        }
        FloatingSelection piece = FloatingSelection.piece(document, pieceWidth, pieceHeight, look, cover, box[0] + found[0], box[1] + found[1]);
        piece.planes = planes;
        planes.logged = document.logLift(points, count);
        return piece;
    }

    /**
     * Lays {@code piece} on {@code document} as one Undo step: with {@code cut}, first takes what it lifted off the layers
     * it came from; with {@code drop}, lays its planes where it lies now on the selected pair's (the fluid on the page's
     * fluid). Nearest pixels, as the screen showed it. Whether the page changed.
     */
    boolean place(ToneDocument document, FloatingSelection piece, boolean drop, boolean cut) {
        Object mark = document.lastStep();
        int restore = document.activeLayer(), pair = InkWashLayers.current(document);
        int[] to = new int[ROLES];
        for (int role = 0; role < ROLES; role++) to[role] = role == FLUID ? -1 : role % 2 == 0 ? InkWashLayers.washOf(document, pair) : InkWashLayers.inkOf(document, pair);
        // A one-layer page's wash and ink are the one layer: the wash laid first, the ink over it.
        boolean changed = false;
        try {
            if (cut) for (int role = 0; role < ROLES; role++) {
                byte[] a = alpha[role];
                if (a == null) continue;
                begin(document, from[role], role);
                for (int row = 0; row < piece.height; row++) for (int col = 0; col < piece.width; col++)
                    if (a[row * piece.width + col] != 0) document.liftPixel(piece.cutX + col, piece.cutY + row);
                changed |= changed ? document.finishWithPrevious() : document.finish();
            }
            if (drop) {
                float[] m = piece.inverse();
                int[] area = piece.bounds();
                for (int role = 0; role < ROLES; role++) {
                    byte[] a = alpha[role];
                    if (a == null) continue;
                    begin(document, to[role], role);
                    if (role != FLUID && plane(role) < 0) document.layKinds(kinds[role], uniform[role]);
                    for (int y = area[1]; y < area[3]; y++) for (int x = area[0]; x < area[2]; x++) {
                        int i = piece.at(m, x, y);
                        if (i >= 0 && a[i] != 0) document.layPixel(x, y, tones[role][i] & 255, a[i] & 255, share[role][i] & 255, print[role][i] & 255, kind[role][i] & 255);
                    }
                    changed |= changed ? document.finishWithPrevious() : document.finish();
                }
            }
            // Vector Ink: its lines out of the loop, and put where the piece lies, their pixels drawn from them; one Undo step with the rest.
            if (inside != null) {
                if (cut) for (int layer = 0; layer < document.layerCount(); layer++)
                    if ((lineMask >> layer & 1) != 0 && document.lines(layer) != null) changed |= VectorInk.lines(document, layer, document.lines(layer).split(loop, loopCount)[0], inside.bounds(), changed);
                if (drop) {
                    int top = InkWashLayers.pairs(document) - 1, vi = VectorInk.room(document);
                    if (vi == VectorInk.ADD) { InkWashLayers.select(document, top); InkWashLayers.add(document, true); vi = InkWashLayers.inkOf(document, InkWashLayers.current(document)); changed = true; }
                    // shortcut: four layers with others on top (no room): the lines aren't laid; give them the pair's ink as pixels if that is ever missed.
                    if (vi >= 0) {
                        VectorLines posed = posed(piece), had = document.lines(vi);
                        changed |= VectorInk.lines(document, vi, (had == null ? VectorLines.NONE : had).with(posed), posed.bounds(), changed);
                    }
                }
                if (changed) document.linkSince(mark);
            }
        } finally { document.selectLayer(restore); }
        if (changed) document.logPlace(logged, drop, cut, piece);
        return changed;
    }
    /** The lines lifted as the piece lies now. */
    private VectorLines posed(FloatingSelection piece) { return inside.posed(piece.cutX, piece.cutY, piece.width, piece.height, piece.centerX, piece.centerY, piece.scale, piece.angle); }
    /**
     * What the zoomed view draws sharp of the lines of the layers whose bits {@code layers} sets while {@code piece}
     * floats over {@code document} (as {@code stroke} shows them, {@link VectorInk#shown}): the lines left outside the loop
     * (a piece lifted from this page) or all of them, and the lifted ones where the piece lies now.
     */
    VectorLines shownLines(FloatingSelection piece, ToneDocument document, int layers, DrawingStroke stroke) {
        if (inside == null) return VectorInk.shown(document, layers, stroke);
        VectorLines all = VectorLines.NONE;
        for (int layer = 0; layer < document.layerCount(); layer++) {
            if ((layers >> layer & 1) == 0) continue;
            VectorLines own = piece.lifted && (lineMask >> layer & 1) != 0 ? outside[layer] : VectorInk.shownOf(document, layer, stroke);
            if (own != null) all = all.with(own);
        }
        return all.with(posed(piece));
    }
    private static void begin(ToneDocument document, int layer, int role) {
        if (layer < 0) document.beginFluid(1);
        else { document.selectLayer(layer); document.beginPlane(plane(role), true); }
    }

    /**
     * For the {@code w × h} block at {@code left, top}, row by row into {@code out}: the tone the page shows with {@code
     * piece} floating where it lies (bare paper of the pair where it was lifted, its look over that), -1 where the page
     * shows as it is ({@link MirrorShow.Marks}, as the eraser's preview).
     */
    void shown(ToneDocument document, FloatingSelection piece, int left, int top, int w, int h, int[] out) {
        float[] m = piece.inverse();
        int low = Math.min(from[WASH], from[INK]), high = Math.max(from[WASH], from[INK]);
        boolean washes = false;
        for (int role = 0; role < FLUID; role += 2) washes |= alpha[role] != null;
        if (!washes) low = high = from[INK];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int px = left + x, py = top + y, i = piece.at(m, px, py), a = i < 0 ? 0 : piece.alpha[i] & 255;
            boolean hole = piece.lifted && lifted(piece, px - piece.cutX, py - piece.cutY);
            if (!hole && a == 0) { out[y * w + x] = -1; continue; }
            int tone = hole ? document.compositeToneWithout(px, py, low, high, lineMask) : document.compositeTone(px, py);
            out[y * w + x] = a == 0 ? tone : ((piece.tones[i] & 255) * a + tone * (255 - a) + 127) / 255;
        }
    }
    /** Whether the pair lost piece pixel {@code col, row} to the lift. */
    private boolean lifted(FloatingSelection piece, int col, int row) {
        if (col < 0 || row < 0 || col >= piece.width || row >= piece.height) return false;
        int i = row * piece.width + col;
        for (int role = 0; role < FLUID; role++) if (alpha[role] != null && alpha[role][i] != 0) return true;
        return lineAlpha != null && lineAlpha[i] != 0;
    }
}
