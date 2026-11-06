package io.github.mpdairy.monopaint;

import java.util.Arrays;

/**
 * Live pages' probe, step 2c (docs/superpowers/plans/2026-10-31-live-draw-on.md): a change from someone else drawn on
 * along the stroke that made it. Each changed pixel is shown at the time of the path sample nearest it (squared
 * distance; ties: the earlier sample), over the path's duration made {@link #SHORTEST} to {@link #LONGEST} ms. A
 * change with no path shows at once. Pure, checked on the desktop ({@code LiveRevealChecks}).
 */
final class LiveReveal {
    static final int SHORTEST = 200, LONGEST = 600;  // a bit shorter than 300 to 1000 (Elling, 2026-10-31)

    private LiveReveal() {}

    /**
     * Each pixel of the change's rectangle its reveal ms (−1 where clear). The nearest sample by a two-pass sweep
     * (8SSEDT's, carrying each pixel's nearest sample from its neighbours), not pixels × samples: about the exact
     * nearest, a pixel or so out at most where two samples are near alike. A sample off the rectangle starts from the
     * edge pixel nearest it.
     */
    static int[] times(LiveChange c) {
        int w = c.w, h = c.h, n = c.path.length / 3;
        int[] times = new int[w * h];
        if (n == 0) { for (int i = 0; i < times.length; i++) times[i] = c.greys[i] < 0 ? -1 : 0; return times; }
        int[] near = new int[w * h];
        Arrays.fill(near, -1);
        for (int s = 0; s < n; s++) {
            int px = Math.max(0, Math.min(w - 1, c.path[3 * s] - c.x)), py = Math.max(0, Math.min(h - 1, c.path[3 * s + 1] - c.y));
            int i = py * w + px;
            if (near[i] < 0 || closer(c, s, near[i], px, py)) near[i] = s;
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                take(c, near, x, y, x - 1, y); take(c, near, x, y, x - 1, y - 1); take(c, near, x, y, x, y - 1); take(c, near, x, y, x + 1, y - 1);
            }
            for (int x = w - 1; x >= 0; x--) take(c, near, x, y, x + 1, y);
        }
        for (int y = h - 1; y >= 0; y--) {
            for (int x = w - 1; x >= 0; x--) {
                take(c, near, x, y, x + 1, y); take(c, near, x, y, x + 1, y + 1); take(c, near, x, y, x, y + 1); take(c, near, x, y, x - 1, y + 1);
            }
            for (int x = 0; x < w; x++) take(c, near, x, y, x - 1, y);
        }
        for (int i = 0; i < times.length; i++) times[i] = c.greys[i] < 0 ? -1 : c.path[3 * near[i] + 2];
        return times;
    }
    /** Pixel x, y takes the nearest sample of its neighbour nx, ny when that is nearer it. */
    private static void take(LiveChange c, int[] near, int x, int y, int nx, int ny) {
        if (nx < 0 || ny < 0 || nx >= c.w || ny >= c.h) return;
        int i = y * c.w + x, s = near[ny * c.w + nx];
        if (s >= 0 && s != near[i] && (near[i] < 0 || closer(c, s, near[i], x, y))) near[i] = s;
    }
    /** Whether sample {@code a} is nearer the rectangle's pixel x, y than sample {@code b}; the earlier when as near. */
    private static boolean closer(LiveChange c, int a, int b, int x, int y) {
        long da = distance(c, a, x, y), db = distance(c, b, x, y);
        return da < db || da == db && a < b;
    }
    private static long distance(LiveChange c, int s, int x, int y) {
        long dx = c.x + x - c.path[3 * s], dy = c.y + y - c.path[3 * s + 1];
        return dx * dx + dy * dy;
    }

    /** The path's duration, ms: its latest sample's. */
    private static int duration(LiveChange c) {
        int most = 0;
        for (int s = 2; s < c.path.length; s += 3) most = Math.max(most, c.path[s]);
        return most;
    }

    /**
     * The change in {@code n} parts by reveal time, each greys over the change's rectangle as {@link LiveChange#greys}
     * (its pixels, the rest clear): each changed pixel in one. With no path, one: the whole change.
     */
    static int[][] slices(LiveChange c, int n) {
        if (c.path.length == 0) return new int[][]{c.greys.clone()};
        int[] times = times(c);
        int span = duration(c);
        int[][] slices = new int[n][c.greys.length];
        for (int[] slice : slices) Arrays.fill(slice, -1);
        for (int i = 0; i < times.length; i++) if (times[i] >= 0)
            slices[span == 0 ? 0 : (int) Math.min(n - 1, (long) times[i] * n / span)][i] = c.greys[i];
        return slices;
    }

    /** When each of {@link #slices}' parts shows, ms after the first: evenly over the duration made {@link #SHORTEST} to {@link #LONGEST} ms. */
    static long[] at(LiveChange c, int n) {
        if (c.path.length == 0 || n == 1) return new long[]{0};
        long over = Math.max(SHORTEST, Math.min(LONGEST, duration(c)));
        long[] at = new long[n];
        for (int k = 0; k < n; k++) at[k] = k * over / (n - 1);
        return at;
    }
}
