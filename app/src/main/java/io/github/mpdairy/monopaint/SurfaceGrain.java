package io.github.mpdairy.monopaint;



/**
 * A surface's grain made whole ({@link BrushTexture#grain}; the surface spec,
 * docs/superpowers/specs/2026-11-01-surface-design.md): {@link #SIDE} square, wider than a page, so a page shows no
 * repeat, and tiling without a seam. The families that synthesise well are made from nothing, the same every time:
 * cold press bumps, laid lines with their chain lines, a canvas weave, kraft and unryu fibres (which also map their
 * fibres, {@link PaperGrain#fibre}). The other scans are laid as patches, each a piece of the scan placed
 * by chance (never turned), blended into one another ({@link #scanned}), the scan's own shading taken out first so its tiles' seams
 * and bands go. Fine is the surface itself; mid its height a few px smoothed (a scan's: the scan at three times the
 * scale). Both are normalised as the scans were: mean 128, spread 52. The row work is shared with spare cores
 * ({@link Cores}), each row the same whoever makes it. Pure, checked on the desktop ({@code SurfaceChecks}).
 */
final class SurfaceGrain {
    /** A surface grain's side: a power of two past a page's width (the Manta's 1920). */
    static final int SIDE = 2048;
    private static final int N = SIDE, MASK = SIDE - 1;
    /** The scans' normal: mean 128, spread 52. */
    static final float MEAN = 128, SPREAD = 52;

    private SurfaceGrain() {}

    /** {@code t}'s grain ({@code scan}: its 256-pixel scan, for a scanned one; else unused). */
    static PaperGrain of(BrushTexture t, byte[] scan) {
        switch (t) {
            case COLD_PRESS: return coldPress();
            case LAID: return laid();
            case CANVAS: return canvas();
            case KRAFT: return fibres(0x6b7a_f7L, SIDE * SIDE / 70, 10, 40, 1.2f, .2f, .07f, t.fibreAngle, SIDE * SIDE / 1400, 30, 70);
            case UNRYU: return fibres(0x0a7a_11L, SIDE * SIDE / 3000, 120, 480, 2.6f, .9f, .012f, t.fibreAngle, SIDE * SIDE / 450, 8, 24);
            default: return scanned(scan);
        }
    }

    // Cold press: rounded bumps of two sizes, a fine tooth over them, a slow undulation under them.

    private static PaperGrain coldPress() {
        float[] h = new float[N * N];
        domes(h, 8, 1.25f, .35f, 0xc01d01);
        domes(h, 4, 1.3f, .8f, 0xc01d02);
        octave(h, 4, .3f, 0xc01d03);
        octave(h, 2, .3f, 0xc01d04);
        octave(h, 128, .1f, 0xc01d05);
        return grain(h, null);
    }
    /**
     * Adds {@code weight} × a dome round each of a jittered grid of points {@code cell} px apart (one a cell), each
     * reaching {@code reach} cells: where domes meet, the higher one, so the bumps meet in valleys (a cellular F1).
     */
    private static void domes(float[] h, int cell, float reach, float weight, long seed) {
        Dice r = new Dice(seed);
        int cells = N / cell;
        float[] at = new float[3 * cells * cells];
        for (int c = 0, k = 0; c < cells * cells; c++) {
            at[k++] = (c % cells + (float) r.nextDouble()) * cell; at[k++] = (c / cells + (float) r.nextDouble()) * cell;
            at[k++] = .7f + .3f * (float) r.nextDouble();
        }
        float R = reach * cell;
        int ri = (int) Math.ceil(R), reachRows = ri / cell + 2;
        // Each share of rows lays every dome reaching them, on its own rows alone.
        bands((from, to) -> {
            float[] top = new float[(to - from) * N];
            for (int cy = from / cell - reachRows; cy <= (to - 1) / cell + reachRows; cy++) for (int cx = 0; cx < cells; cx++) {
                int k = 3 * (Math.floorMod(cy, cells) * cells + cx);
                float px = at[k], py = at[k + 1] + (cy - Math.floorMod(cy, cells)) * cell, tall = at[k + 2];
                int x0 = (int) Math.floor(px), y0 = (int) Math.floor(py);
                for (int dy = Math.max(-ri, from - y0); dy <= Math.min(ri, to - 1 - y0); dy++) for (int dx = -ri; dx <= ri; dx++) {
                    float ex = x0 + dx + .5f - px, ey = y0 + dy + .5f - py, d2 = (ex * ex + ey * ey) / (R * R);
                    if (d2 >= 1) continue;
                    float v = tall * (1 - d2);
                    int i = (y0 + dy - from) * N + (x0 + dx & MASK);
                    if (v > top[i]) top[i] = v;
                }
            }
            for (int i = 0; i < top.length; i++) h[from * N + i] += weight * top[i];
        });
    }

    // Laid: faint laid lines across, wavering and uneven, broken by chain lines down the sheet a finger's width apart.

    private static PaperGrain laid() {
        float[] h = new float[N * N];
        float[] wobble = new float[N * N], chainWobble = new float[N * N], strength = new float[N * N];
        octave(wobble, 256, 2.4f, 0x1a1d01);
        octave(chainWobble, 512, 6f, 0x1a1d02);
        octave(strength, 64, 1, 0x1a1d03);
        octave(strength, 16, .5f, 0x1a1d04);
        float[] lines = phases(11, .14f, 0x1a1d05), chains = phases(300, .18f, 0x1a1d06);
        float chainApart = (float) N / chains[N];
        bands((y0, y1) -> {
            for (int y = y0; y < y1; y++) for (int x = 0; x < N; x++) {
                int i = y * N + x;
                float u = frac(at(lines, y + wobble[i] - 1.2f)), ridge = .5f + .5f * (float) Math.cos(2 * Math.PI * u);
                float c = frac(at(chains, x + chainWobble[i] - 3f)), cd = Math.min(c, 1 - c) * chainApart;
                float chain = (float) Math.exp(-cd * cd / 16), near = (float) Math.exp(-cd * cd / 200);
                h[i] = ridge * (.12f + .2f * strength[i]) * (1 - .4f * near) + .12f * chain;
            }
        });
        octave(h, 8, .12f, 0x1a1d0a);
        octave(h, 4, .3f, 0x1a1d07);
        octave(h, 2, .26f, 0x1a1d08);
        octave(h, 128, .1f, 0x1a1d09);
        return grain(h, null);
    }

    // Canvas: a plain weave, threads over and under, each a little thicker or thinner, the rows wavering.

    private static PaperGrain canvas() {
        float[] h = new float[N * N];
        float[] wx = new float[N * N], wy = new float[N * N], slub = new float[N * N];
        octave(wx, 64, 4f, 0xca0a01);
        octave(wy, 64, 4f, 0xca0a02);
        octave(slub, 32, 1, 0xca0a03);
        float[] warp = phases(5, .4f, 0xca0a04), weft = phases(5, .4f, 0xca0a05);
        float[] warpThick = thickness(warp, 0xca0a06), weftThick = thickness(weft, 0xca0a07);
        bands((y0, y1) -> {
            for (int y = y0; y < y1; y++) for (int x = 0; x < N; x++) {
                int i = y * N + x;
                float a = at(warp, x + wx[i] - 2f), b = at(weft, y + wy[i] - 2f);
                int ia = (int) Math.floor(a), ib = (int) Math.floor(b);
                float u = a - ia, v = b - ib;
                float across = (float) Math.sin(Math.PI * u), along = (float) Math.sin(Math.PI * v);
                boolean warpOver = (ia + ib & 1) == 0;
                float thick = warpOver ? warpThick[Math.floorMod(ia, warpThick.length)] : weftThick[Math.floorMod(ib, weftThick.length)];
                float over = warpOver ? pow(across, thick) * (.55f + .45f * (float) Math.sqrt(along)) : pow(along, thick) * (.55f + .45f * (float) Math.sqrt(across));
                h[i] = .55f * over * (.8f + .35f * slub[i]);
            }
        });
        octave(h, 4, .2f, 0xca0a0a);
        octave(h, 2, .35f, 0xca0a08);
        octave(h, 128, .1f, 0xca0a09);
        return grain(h, null);
    }
    /** How round each thread stands, by thread: a power of its profile, .5 (flat, thick) to 1.1 (round, thin). */
    private static float[] thickness(float[] phases, long seed) {
        int threads = Math.round(phases[N]);
        float[] t = new float[threads];
        Dice r = new Dice(seed);
        for (int i = 0; i < threads; i++) t[i] = .5f + .6f * (float) r.nextDouble();
        return t;
    }
    private static float pow(float v, float p) { return v <= 0 ? 0 : (float) Math.pow(v, p); }

    // Kraft and unryu: a mottled pulp with fibres laid in it, mapped.

    /**
     * Fibres in a mottled pulp: {@code count} of {@code shortest} to {@code longest} px, about {@code wide} px across,
     * {@code tall} high, bending {@code bend} radians a px at most, lying at {@code angle} (± about 35°; NaN: any
     * way); then {@code fine} short ones of {@code fineShort} to {@code fineLong} px, a third as high, any way.
     */
    private static PaperGrain fibres(long seed, int count, int shortest, int longest, float wide, float tall, float bend, float angle,
                                     int fine, int fineShort, int fineLong) {
        float[] h = new float[N * N];
        octave(h, 64, .1f, (int) seed + 1);
        octave(h, 16, .12f, (int) seed + 2);
        octave(h, 4, .25f, (int) seed + 3);
        octave(h, 2, .2f, (int) seed + 4);
        byte[] map = new byte[N * N];
        float[] most = new float[N * N];
        Dice r = new Dice(seed);
        int n = count + fine;
        long[] seeds = new long[n];
        float[] rows = new float[2 * n];
        for (int k = 0; k < n; k++) {
            seeds[k] = r.nextLong();
            boolean big = k < count;
            fibre(h, most, map, new Dice(seeds[k]), big ? shortest : fineShort, big ? longest : fineLong, big ? wide : 1, big ? tall : tall / 3, tall, bend, big ? angle : Float.NaN, rows, k, 0, 0);
        }
        // Each share of rows lays every fibre crossing them, on its own rows alone, in the same order.
        bands((from, to) -> {
            for (int k = 0; k < n; k++) {
                if (!crosses(rows[2 * k], rows[2 * k + 1], from, to)) continue;
                boolean big = k < count;
                fibre(h, most, map, new Dice(seeds[k]), big ? shortest : fineShort, big ? longest : fineLong, big ? wide : 1, big ? tall : tall / 3, tall, bend, big ? angle : Float.NaN, null, k, from, to);
            }
        });
        return grain(h, map);
    }
    /** Whether rows {@code lo} to {@code hi} (unwrapped, less than a side apart) reach rows {@code from} to {@code to} once wrapped. */
    private static boolean crosses(float lo, float hi, int from, int to) {
        for (int m = Math.floorDiv((int) Math.floor(lo), N); m <= Math.floorDiv((int) Math.ceil(hi), N); m++)
            if (hi - m * N >= from && lo - m * N < to) return true;
        return false;
    }
    /**
     * One fibre from a place by chance ({@code r}): a soft ridge along a gently bending path, its way kept where it lies
     * highest ({@code mapAbove} of its paper's fibres' height, {@code tallest}). With {@code rows} it only finds which rows
     * it reaches (into {@code rows[2k]} and {@code rows[2k + 1]}); else it lays itself on rows {@code from} to {@code to}.
     */
    private static void fibre(float[] h, float[] most, byte[] map, Dice r, int shortest, int longest, float wide, float tall, float tallest, float bend, float angle,
                              float[] rows, int k, int from, int to) {
        float x = (float) (r.nextDouble() * N), y = (float) (r.nextDouble() * N);
        double th = angle != angle ? r.nextDouble() * Math.PI : angle + .6 * gauss(r);
        int length = shortest + (int) (r.nextDouble() * (longest - shortest));
        float w = wide * (.45f + (float) r.nextDouble()), half = w / 2 + 1, high = tall * (.5f + .5f * (float) r.nextDouble());
        double turn = 0;
        float lo = y, hi = y;
        for (int s = 0; s < length; s++) {
            turn = .9 * turn + bend * gauss(r) * .5;
            th += turn;
            float dx = (float) Math.cos(th), dy = (float) Math.sin(th);
            x += dx; y += dy;
            int y0 = (int) Math.floor(y - half), y1 = (int) Math.ceil(y + half);
            if (rows != null) { lo = Math.min(lo, y0); hi = Math.max(hi, y1); continue; }
            // Thinner toward its ends.
            float end = Math.min(1, Math.min(s, length - s) / 6f + .3f), ww = w * end;
            int x0 = (int) Math.floor(x - half), x1 = (int) Math.ceil(x + half);
            byte way = (byte) (1 + Math.round(254 * (float) ((th % Math.PI + Math.PI) % Math.PI / Math.PI)));
            for (int py = y0; py <= y1; py++) {
                int row = py & MASK;
                if (row < from || row >= to) continue;
                for (int px = x0; px <= x1; px++) {
                // Distance from the fibre's line, across it; only the slice by this step.
                float ex = px + .5f - x, ey = py + .5f - y, d = Math.abs(ex * dy - ey * dx), along = Math.abs(ex * dx + ey * dy);
                if (along > .75f || d >= ww / 2) continue;
                float q = 2 * d / ww, v = high * end * (1 - q * q);
                int i = (py & MASK) * N + (px & MASK);
                if (v > most[i]) {
                    h[i] += v - most[i];
                    most[i] = v;
                    if (v > .2f * tallest) map[i] = way;
                }
                }
            }
        }
        if (rows != null) { rows[2 * k] = lo; rows[2 * k + 1] = hi; }
    }

    // Scans laid as patches.

    /** Patches' cells, page px: fine's, and mid's (the scan at {@link PaperGrain#MID_SCALE} times). */
    private static final int FINE_CELL = 64, MID_CELL = 128;
    /** How far from a scan's edge a patch keeps, scan px: a scan's own seam never shows in a patch. */
    private static final int EDGE = 8;
    /** The scan's shading taken out: what a box this many px each way averages. */
    private static final int SHADING = 20;

    /**
     * A scan laid as patches: each cell of a grid a piece of the scan from a place by chance (never turned: a photo's light
     * keeps its side), each blending into its neighbours' over its cell (a tent's weights squared, kept to the scan's
     * spread).
     */
    static PaperGrain scanned(byte[] scan) {
        int s = PaperGrain.SIDE;
        if (scan == null || scan.length != s * s) throw new IllegalArgumentException("A texture is " + s + " pixels square");
        float[] flat = new float[s * s], soft = new float[s * s];
        for (int i = 0; i < flat.length; i++) flat[i] = scan[i] & 255;
        blur(flat, soft, s, SHADING);
        for (int i = 0; i < flat.length; i++) flat[i] -= soft[i];
        float[] fine = patches(flat, FINE_CELL, 1, 0x5ca9_f1);
        float[] mid = patches(flat, MID_CELL, PaperGrain.MID_SCALE, 0x5ca9_d1);
        return new PaperGrain(bytes(fine), N, bytes(mid), N, 1, null);
    }
    private static float[] patches(float[] scan, int cell, int scale, long seed) {
        int s = PaperGrain.SIDE, cells = N / cell, foot = 2 * cell / scale, room = s - 2 * EDGE - foot;
        int[] ox = new int[cells * cells], oy = new int[cells * cells];
        Dice r = new Dice(seed);
        for (int c = 0; c < ox.length; c++) {
            ox[c] = EDGE + r.below(room + 1); oy[c] = EDGE + r.below(room + 1);
        }
        float[] out = new float[N * N];
        float inv = 1f / scale;
        bands((from, to) -> {
            for (int y = from; y < to; y++) {
                float gy = (y + .5f) / cell - .5f;
                int r0 = (int) Math.floor(gy);
                float fy = gy - r0;
                for (int x = 0; x < N; x++) {
                    float gx = (x + .5f) / cell - .5f;
                    int c0 = (int) Math.floor(gx);
                    float fx = gx - c0, sum = 0, weights = 0;
                    for (int k = 0; k < 4; k++) {
                        int cr = r0 + (k >> 1), cc = c0 + (k & 1);
                        float w = ((k & 1) == 0 ? 1 - fx : fx) * ((k >> 1) == 0 ? 1 - fy : fy);
                        w *= w;
                        if (w <= 0) continue;
                        int c = Math.floorMod(cr, cells) * cells + Math.floorMod(cc, cells);
                        // Where in the patch, 0 to its foot: from the cell's middle less one cell each way.
                        float lx = (x + .5f - (cc + .5f) * cell + cell) * inv, ly = (y + .5f - (cr + .5f) * cell + cell) * inv;
                        // Never turned or mirrored: a photo's light comes from one side, so a turned patch shows as a patch.
                        sum += w * sample(scan, s, ox[c] + lx - .5f, oy[c] + ly - .5f);
                        weights += w * w;
                    }
                    out[y * N + x] = sum / (float) Math.sqrt(weights);
                }
            }
        });
        return out;
    }
    /** The scan at {@code x, y}, bilinear, inside it (the patches keep clear of its edge). */
    private static float sample(float[] scan, int s, float x, float y) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float tx = x - x0, ty = y - y0;
        int x1 = Math.min(s - 1, x0 + 1), y1 = Math.min(s - 1, y0 + 1);
        x0 = Math.max(0, x0); y0 = Math.max(0, y0);
        float top = scan[y0 * s + x0] + (scan[y0 * s + x1] - scan[y0 * s + x0]) * tx, bottom = scan[y1 * s + x0] + (scan[y1 * s + x1] - scan[y1 * s + x0]) * tx;
        return top + (bottom - top) * ty;
    }

    // Shared.

    /** The grain of height {@code h}: fine itself, mid smoothed over a few px; {@code fibre} its fibres' map or null. */
    private static PaperGrain grain(float[] h, byte[] fibre) {
        float[] mid = new float[N * N];
        blur(h, mid, N, 2);
        blur(mid, mid, N, 2);
        return new PaperGrain(bytes(h), N, bytes(mid), N, 1, fibre);
    }
    /** A share of rows, {@code from} to {@code to}. */
    private interface Band { void run(int from, int to); }
    private static final int BANDS = 16;
    /** Runs {@code band} over every row of a side {@link #N}, the rows shared out. */
    private static void bands(Band band) { bands(N, band); }
    private static void bands(int side, Band band) { Cores.each(BANDS, (piece, worker) -> band.run(piece * side / BANDS, (piece + 1) * side / BANDS)); }
    /** {@link PaperNoise#octave} over {@code h}, {@link #N} square, the rows shared out. */
    private static void octave(float[] h, int cell, float weight, int seed) { bands((from, to) -> PaperNoise.octave(h, N, cell, weight, seed, from, to)); }
    /**
     * Places along a line {@code SIDE} long, about {@code apart} px apart, each gap off by up to {@code uneven} of it,
     * stretched to fit the line exactly, so they tile: as a lookup of where page px {@code p} falls, {@link #at}
     * ({@code k} at place {@code k}; index {@code SIDE} holds how many there are).
     */
    private static float[] phases(float apart, float uneven, long seed) {
        Dice r = new Dice(seed);
        int count = Math.max(1, Math.round(N / apart));
        float[] gaps = new float[count];
        float total = 0;
        for (int k = 0; k < count; k++) { gaps[k] = apart * (1 + uneven * (float) (2 * r.nextDouble() - 1)); total += gaps[k]; }
        float[] place = new float[N + 1];
        float start = 0, scale = N / total;
        int k = 0;
        for (int p = 0; p < N; p++) {
            while (k < count - 1 && p >= start + gaps[k] * scale) { start += gaps[k] * scale; k++; }
            place[p] = k + (p - start) / (gaps[k] * scale);
        }
        place[N] = count;
        return place;
    }
    /** Where page position {@code p} (any, wrapping) falls among {@link #phases}' places, linear between px. */
    private static float at(float[] phases, float p) {
        float count = phases[N];
        int k = (int) Math.floor(p);
        float t = p - k;
        int wraps = Math.floorDiv(k, N), i = k - wraps * N;
        float a = phases[i], b = i + 1 < N ? phases[i + 1] : count;
        return wraps * count + a + (b - a) * t;
    }
    private static float frac(float v) { return v - (float) Math.floor(v); }
    /** The same numbers from the same seed everywhere (the tablet, the desktop, Brush Studio's WebAssembly): SplitMix64. */
    static final class Dice {
        private long s;
        Dice(long seed) { s = seed; }
        long nextLong() {
            long z = s += 0x9e3779b97f4a7c15L;
            z = (z ^ z >>> 30) * 0xbf58476d1ce4e5b9L; z = (z ^ z >>> 27) * 0x94d049bb133111ebL;
            return z ^ z >>> 31;
        }
        /** 0 (included) to 1 (not). */
        double nextDouble() { return (nextLong() >>> 11) * 0x1.0p-53; }
        /** 0 to {@code n} − 1. */
        int below(int n) { return (int) (nextDouble() * n); }
    }
    /** A normal number, mean 0, spread 1 (Box–Muller, one of the pair). */
    private static double gauss(Dice r) { return Math.sqrt(-2 * Math.log(1 - r.nextDouble())) * Math.cos(2 * Math.PI * r.nextDouble()); }
    /** {@code a} box-blurred {@code r} px each way, wrapping, into {@code out} (side {@code side}; may be {@code a}). */
    static void blur(float[] a, float[] out, int side, int r) {
        float[] tmp = new float[a.length];
        float n = 2 * r + 1;
        bands(side, (from, to) -> {
            for (int y = from; y < to; y++) {
                int row = y * side;
                float sum = 0;
                for (int k = -r; k <= r; k++) sum += a[row + Math.floorMod(k, side)];
                for (int x = 0; x < side; x++) {
                    tmp[row + x] = sum / n;
                    sum += a[row + Math.floorMod(x + r + 1, side)] - a[row + Math.floorMod(x - r, side)];
                }
            }
        });
        bands(side, (from, to) -> {
            for (int x = from; x < to; x++) {
                float sum = 0;
                for (int k = -r; k <= r; k++) sum += tmp[Math.floorMod(k, side) * side + x];
                for (int y = 0; y < side; y++) {
                    out[y * side + x] = sum / n;
                    sum += tmp[Math.floorMod(y + r + 1, side) * side + x] - tmp[Math.floorMod(y - r, side) * side + x];
                }
            }
        });
    }
    /** {@code h} normalised to the scans' mean 128 and spread 52, as bytes. */
    static byte[] bytes(float[] h) {
        double sum = 0, squares = 0;
        for (float v : h) { sum += v; squares += (double) v * v; }
        double mean = sum / h.length, sd = Math.sqrt(Math.max(1e-12, squares / h.length - mean * mean));
        byte[] out = new byte[h.length];
        float m = (float) mean, k = (float) (SPREAD / sd);
        bands((from, to) -> { for (int i = from * N; i < to * N; i++) out[i] = (byte) Math.max(0, Math.min(255, Math.round(MEAN + k * (h[i] - m)))); });
        return out;
    }
}
