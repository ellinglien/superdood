package io.github.mpdairy.monopaint;

/**
 * Lasso to Superdood (the Supernote plugin, the owner 2026-11-08: "lasso to superdood brush is intriguing. go for it!"):
 * a Supernote note's lassoed pen strokes drawn again with a Superdood brush on a blank page of the note's size, the
 * marks cropped out ({@link LassoProvider} hands them to the plugin as a PNG). The note keeps a stroke's points and
 * pressures but no times or tilt, so its samples go in untimed and upright, as the web engine's do. Pure; checked on
 * the desktop ({@code LassoRenderChecks}).
 */
final class LassoRender {
    /** Wet paint's drying ticks run before what is left is laid where it is (a Slow wash spreads a while first). */
    static final int TICKS = 600;

    /** The marks: their box on the page and its tones, a byte a pixel (255 paper). */
    static final class Marks {
        final int x, y, w, h;
        final byte[] tones;
        Marks(int x, int y, int w, int h, byte[] tones) { this.x = x; this.y = y; this.w = w; this.h = h; this.tones = tones; }
    }

    /**
     * {@code strokes[i]} is stroke i's points, {x0, y0, x1, y1, ...} in page px; {@code travels[i]} the pen's travel at
     * each, 0 to 1 (the note's pressure over its most). Null when the brush draws nothing there.
     */
    static Marks draw(int width, int height, float[][] strokes, float[][] travels, ModularBrush brush, double px, double dark) {
        ToneDocument d = paint(width, height, strokes, travels, brush, px, dark);
        return crop(tones(d), d.width, d.height);
    }

    /** A blank page of the note's size with the strokes drawn on it, dried. */
    private static ToneDocument paint(int width, int height, float[][] strokes, float[][] travels, ModularBrush brush, double px, double dark) {
        ToneDocument d = InkWashLayers.blank(width, height);
        WatercolorSession session = new WatercolorSession();
        boolean wet = ResolvedBrush.of(brush, px, dark).wet != null;
        for (int i = 0; i < strokes.length; i++) {
            float[] xy = strokes[i], travel = travels[i];
            if (xy.length < 2) continue;
            // As the drawing screen: wet paint stays wet under a wet stroke; anything else settling is laid where it is.
            if (wet) session.settleBefore(d, brush); else session.settleNow(d);
            DrawingStroke s = QuickStroke.start(d, brush, px, dark, session);
            for (int k = 0; k < xy.length / 2; k++)
                s.sample(xy[2 * k], xy[2 * k + 1], ToolSettings.pressure(k < travel.length ? travel[k] : 1), Float.NaN, Float.NaN, PenSpeed.UNTIMED);
            s.present();
            s.finish();
        }
        for (int k = 0; k < TICKS && session.tickDrying(d); k++) { }
        session.dryAll(d);
        return d;
    }

    /** A fill's rows: this share of the brush's size apart, so a wet brush's rows run into each other. */
    static final double ROWS = .45;
    /** A fill's press, pen travel. */
    static final float PRESS = .7f;
    /**
     * The most pixels a fill paints at the page's size: a larger lasso is painted smaller, as many times smaller each way
     * as brings it under this, and enlarged smoothly (a Slow wash's drying over 790,000 px took 36 s on the Manta, 2026-11-09).
     */
    static final int WORK = 100_000;

    /**
     * Highlighter fill (the owner, 2026-11-09: "like a 'highlighter fill'"): the inside of the note's lasso,
     * {@code lasso} = {x0, y0, x1, y1, ...} in page px, scrubbed with the brush in rows across it, back and forth, as a
     * highlighter is, and kept to it (a wet brush's rows bloom into each other inside, and stop sharp at the lasso). The
     * lassoed strokes are left clear, {@code clear[i]} px each side of stroke i's line, so the note's own writing shows
     * through the picture laid over it. Null when the lasso holds no pixel.
     */
    static Marks fill(int width, int height, float[] lasso, float[][] strokes, float[] clear, ModularBrush brush, double px, double dark) {
        FillRegion.Room room = inside(width, height, lasso);
        if (room == null) return null;
        int scale = (int) Math.max(1, Math.ceil(Math.sqrt((double) room.area / WORK)));
        int sw = (width + scale - 1) / scale, sh = (height + scale - 1) / scale;
        float[] small = lasso.clone();
        for (int i = 0; i < small.length; i++) small[i] /= scale;
        FillRegion.Room r = scale == 1 ? room : inside(sw, sh, small);
        if (r == null) return null;
        java.util.List<float[]> rows = new java.util.ArrayList<>();
        int step = (int) Math.max(1, Math.round(px / scale * ROWS)), n = 0;
        for (int y = r.y0 + 1; y <= r.y1; y += step, n++) {
            // Each run of the row inside the lasso, from its first pixel to its last; every other row drawn back.
            for (int x = r.x0; x <= r.x1; x++) {
                if (!r.inside(x, y)) continue;
                int e = x;
                while (e < r.x1 && r.inside(e + 1, y)) e++;
                float[] row = new float[2 * (e - x + 1)];
                for (int k = 0; k <= e - x; k++) { row[2 * k] = n % 2 == 0 ? x + k : e - k; row[2 * k + 1] = y; }
                rows.add(row);
                x = e;
            }
        }
        float[][] xy = rows.toArray(new float[0][]), travel = new float[xy.length][];
        for (int i = 0; i < xy.length; i++) { travel[i] = new float[xy[i].length / 2]; java.util.Arrays.fill(travel[i], PRESS); }
        int[] t = tones(paint(sw, sh, xy, travel, brush, px / scale, dark));
        if (scale > 1) t = enlarge(t, sw, sh, scale, width, height, room);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) if (!room.inside(x, y)) t[y * width + x] = 255;
        for (int i = 0; i < strokes.length; i++) for (int k = 0; k + 1 < strokes[i].length; k += 2) {
            float x0 = strokes[i][k], y0 = strokes[i][k + 1];
            float x1 = k + 3 < strokes[i].length ? strokes[i][k + 2] : x0, y1 = k + 3 < strokes[i].length ? strokes[i][k + 3] : y0;
            int steps = (int) Math.ceil(Math.hypot(x1 - x0, y1 - y0));
            for (int j = 0; j <= steps; j++) { float f = steps == 0 ? 0 : (float) j / steps; disc(t, width, height, x0 + f * (x1 - x0), y0 + f * (y1 - y0), clear[i]); }
        }
        return crop(t, width, height);
    }

    /** {@code small} ({@code sw × sh}) enlarged {@code scale} times to {@code width × height}, smoothly (bilinear), over {@code room}'s box; paper elsewhere. */
    private static int[] enlarge(int[] small, int sw, int sh, int scale, int width, int height, FillRegion.Room room) {
        int[] t = new int[width * height];
        java.util.Arrays.fill(t, 255);
        for (int y = room.y0; y <= room.y1; y++) {
            float fy = Math.max(0, (y + .5f) / scale - .5f);
            int ya = Math.min(sh - 1, (int) fy), yb = Math.min(sh - 1, ya + 1);
            float v = fy - ya;
            for (int x = room.x0; x <= room.x1; x++) {
                float fx = Math.max(0, (x + .5f) / scale - .5f);
                int xa = Math.min(sw - 1, (int) fx), xb = Math.min(sw - 1, xa + 1);
                float u = fx - xa;
                float top = small[ya * sw + xa] * (1 - u) + small[ya * sw + xb] * u, bottom = small[yb * sw + xa] * (1 - u) + small[yb * sw + xb] * u;
                t[y * width + x] = Math.round(top * (1 - v) + bottom * v);
            }
        }
        return t;
    }

    /** The pixels whose centres lie inside polygon {@code xy} = {x0, y0, x1, y1, ...} (Filled Shape's, non-zero winding), as a Fill region; null for none on the page. */
    static FillRegion.Room inside(int width, int height, float[] xy) {
        int n = xy.length / 2;
        if (n < 3) return null;
        float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) { xs[i] = xy[2 * i]; ys[i] = xy[2 * i + 1]; }
        return FilledShape.room(xs, ys, n, new double[][]{Mirrors.Group.IDENTITY}, width, height);
    }

    private static void disc(int[] t, int width, int height, float cx, float cy, float r) {
        int xa = Math.max(0, (int) Math.floor(cx - r)), xb = Math.min(width - 1, (int) Math.ceil(cx + r));
        int ya = Math.max(0, (int) Math.floor(cy - r)), yb = Math.min(height - 1, (int) Math.ceil(cy + r));
        for (int y = ya; y <= yb; y++) for (int x = xa; x <= xb; x++) {
            float dx = x + .5f - cx, dy = y + .5f - cy;
            if (dx * dx + dy * dy <= r * r) t[y * width + x] = 255;
        }
    }

    /** The page's tones, a pixel each, row by row. */
    private static int[] tones(ToneDocument d) {
        int[] t = new int[d.width * d.height];
        d.compositeTones(0, 0, d.width, d.height, t, 0, d.width);
        return t;
    }

    /** Tones {@code t} of a {@code width × height} page cropped to its marks; null for none. */
    private static Marks crop(int[] t, int width, int height) {
        int x0 = width, y0 = height, x1 = -1, y1 = -1;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) if (t[y * width + x] < 255) {
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
        }
        if (x1 < 0) return null;
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        byte[] out = new byte[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out[y * w + x] = (byte) t[(y0 + y) * width + x0 + x];
        return new Marks(x0, y0, w, h, out);
    }
}
