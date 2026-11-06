package io.github.mpdairy.monopaint;

/**
 * Later's media (2026-10-31, the owner's "useful creative tools, possibly replicating physical media"): what a mark
 * becomes once the technique has done its work, worked out once at the lift and then shown by the clock
 * ({@link FadingInk}, its kinds {@link FadingInk#SALT} to {@link FadingInk#FOX}):
 * <ul>
 * <li>Salt on a wet wash: pale starbursts, a darker crisp rim where the pigment was pushed, more in the darker, wetter
 * parts;</li>
 * <li>Plastic wrap pressed on a wet wash: a crinkled network of dark veins where pigment pooled in the folds, paler
 * facets between;</li>
 * <li>Feathering, ink on soft paper: a fuzzy halo and fine hairlines wicking out of the edge along the valleys of the
 * brush's own paper (its Paper part's grain, fixed to the page), further and more on rough or soft papers;</li>
 * <li>Foxing, an old book's page: small soft grey spots, mottled, a few larger with a darker rim, near and on the mark;</li>
 * <li>Letterpress (2026-11-01, as the rest below): ink squashed against paper by type, evened out, a darker squeezed rim
 * at its edges, a mottled lighter middle, now and then a starved patch where the paper's valleys took no ink;</li>
 * <li>Torn paper: the mark a scrap of paper in its own grey, torn and glued down: a ragged white edge where its core shows,
 * fibres across it, a faint shadow under its edge;</li>
 * <li>Cross-stitch: little X stitches on a fabric grid fixed to the page, in the cells the mark covers, each two twisted
 * threads in the mark's grey there, the top one over the bottom;</li>
 * <li>Cracks: thick paint crazing as it ages, a fine network of pale cracks, more where it lies darker and thicker;</li>
 * <li>Tarnish: metalpoint's pale grey line darkening unevenly, never past a mid-dark grey.</li>
 * </ul>
 * Everything comes from the brush's seed and the page position, so the same stroke finishes the same. Works on a region
 * of one layer: its tones and coverages (changed in place), and which pixels the stroke changed. Pure, checked on the
 * desktop ({@code LaterMediaChecks}).
 */
final class LaterMedia {
    private LaterMedia() {}

    /** How far past the stroke's tiles the technique reaches, px: feather threads at their longest, foxing's spots. */
    static int margin(FadingInk.Spec spec) {
        switch (spec.kind) {
            case FadingInk.FEATHER: return (int) Math.ceil(threadLength(spec.amount) * wicks(spec.paper, spec.tooth) * 1.6) + 6;
            case FadingInk.FOX: return FOX_NEAR + 8;
            case FadingInk.PRESS: return 2;
            case FadingInk.TORN: return TORN_OUT;
            case FadingInk.STITCH: return (int) Math.ceil(spec.amount) + 1;
            default: return 0;
        }
    }

    /**
     * {@code spec}'s technique (its macro dial, 0 to 1; its seed; its paper) on the region {@code x0, y0, w × h} of a
     * layer: {@code tones} and {@code alpha} (row by row, the region's) become its finished state; {@code mark} says
     * which pixels the stroke changed.
     */
    static void finish(FadingInk.Spec spec, int x0, int y0, int w, int h, byte[] tones, byte[] alpha, boolean[] mark) {
        int n = w * h;
        float[] p = new float[n];
        for (int i = 0; i < n; i++) p[i] = (255 - (tones[i] & 255)) * (alpha[i] & 255) / 255f;
        float[] q = p.clone();
        int[] ink = null;
        double amount = spec.amount;
        long seed = spec.seed;
        switch (spec.kind) {
            case FadingInk.SALT: salt(amount, seed, x0, y0, w, h, p, q, mark); break;
            case FadingInk.WRAP: wrap(amount, seed, x0, y0, w, h, p, q, mark); break;
            case FadingInk.FEATHER: ink = feather(amount, seed, spec.paper, spec.tooth, x0, y0, w, h, tones, p, q, mark); break;
            case FadingInk.FOX: fox(amount, seed, x0, y0, w, h, p, q, mark); break;
            case FadingInk.PRESS: press(amount, seed, spec.paper, x0, y0, w, h, p, q, mark); break;
            case FadingInk.TORN: torn(amount, seed, x0, y0, w, h, p, q, mark); break;
            case FadingInk.STITCH: ink = stitch(amount, seed, x0, y0, w, h, tones, p, q, mark); break;
            case FadingInk.CRACKS: cracks(amount, seed, x0, y0, w, h, p, q, mark); break;
            case FadingInk.TARNISH: tarnish(amount, seed, x0, y0, w, h, tones, alpha, p, mark); return;
            default: return;
        }
        for (int i = 0; i < n; i++) {
            if (Math.abs(q[i] - p[i]) < .5f) continue;
            paint(tones, alpha, i, q[i], ink != null && ink[i] >= 0 ? ink[i] : FOX_TONE);
        }
    }

    /** Pixel {@code i} holding paint {@code p} (its darkness over white, 0 to 255): its own tone, or {@code tone} on bare paper, more coverage first, then darker. */
    static void paint(byte[] tones, byte[] alpha, int i, float p, int tone) {
        int t = tones[i] & 255, a = alpha[i] & 255;
        if (a == 0 || t > 250) t = tone;
        p = Math.max(0, Math.min(255, p));
        int room = 255 - t;
        if (p < .5f) { tones[i] = (byte) 255; alpha[i] = 0; return; }
        if (p <= room) { alpha[i] = (byte) Math.min(255, Math.round(p * 255 / room)); tones[i] = (byte) t; }
        else { alpha[i] = (byte) 255; tones[i] = (byte) Math.round(255 - p); }
    }

    // ---- Salt ----

    /** Salt's grains a pixel of wash, few to many (the macro dial): about one a 2,600 to one a 260 px² at the darkest. */
    static double grainArea(double amount) { return 2600 * Math.pow(.1, amount); }

    private static void salt(double amount, long seed, int x0, int y0, int w, int h, float[] p, float[] q, boolean[] mark) {
        float most = 0;
        for (int i = 0; i < p.length; i++) if (mark[i]) most = Math.max(most, p[i]);
        if (most < 6) return;
        float[] f = new float[p.length];
        java.util.Arrays.fill(f, 1);
        int cell = (int) Math.max(6, Math.round(Math.sqrt(grainArea(amount))));
        int cx0 = Math.floorDiv(x0, cell), cy0 = Math.floorDiv(y0, cell), cx1 = Math.floorDiv(x0 + w - 1, cell), cy1 = Math.floorDiv(y0 + h - 1, cell);
        long s = seed ^ 0x5a17L;
        // Two chances a cell, so grains fall unevenly, some together, as sprinkled.
        for (int cy = cy0 - 1; cy <= cy1 + 1; cy++) for (int cx = cx0 - 1; cx <= cx1 + 1; cx++) for (int g = 0; g < 2; g++) {
            float gx = cx * cell + cell * unit(s, cx, cy, 1 + 8 * g), gy = cy * cell + cell * unit(s, cx, cy, 2 + 8 * g);
            int lx = Math.round(gx) - x0, ly = Math.round(gy) - y0;
            if (lx < 0 || ly < 0 || lx >= w || ly >= h || !mark[ly * w + lx]) continue;
            // Wetter (darker) wash takes more grains and blooms them wider.
            float wet = p[ly * w + lx] / most;
            if (wet < .08f || unit(s, cx, cy, 3 + 8 * g) > .5f * (.15f + .85f * wet)) continue;
            float u = unit(s, cx, cy, 4 + 8 * g), r = (3 + 12 * u * u * u) * (.7f + .5f * wet);
            burst(f, w, h, gx - x0, gy - y0, r, s * 31 + cx * 7919L + cy * 104729L + g, .58f + .27f * wet);
        }
        for (int i = 0; i < p.length; i++) if (mark[i] && f[i] != 1) q[i] = Math.min(255, p[i] * f[i]);
    }

    /**
     * One starburst into {@code f} (what a pixel's paint is multiplied by): pale round the grain, paler streaks out from
     * it, an irregular, finely ragged outline that softens into the wash, and a thin darker rim where the water pushed
     * the pigment.
     */
    private static void burst(float[] f, int w, int h, float cx, float cy, float r, long s, float lift) {
        int reach = (int) Math.ceil(r * 1.45f) + 3;
        int xa = Math.max(0, (int) cx - reach), xb = Math.min(w - 1, (int) cx + reach), ya = Math.max(0, (int) cy - reach), yb = Math.min(h - 1, (int) cy + reach);
        float[] coarse = ring(s, 5), fine = ring(s + 1, 23), finer = ring(s + 3, 53), rays = ring(s + 2, 97);
        for (int y = ya; y <= yb; y++) for (int x = xa; x <= xb; x++) {
            float dx = x + .5f - cx, dy = y + .5f - cy, d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > reach) continue;
            float a = (float) (Math.atan2(dy, dx) / (2 * Math.PI) + .5), fr = ring(finer, a);
            float rr = Math.max(1, r * (.72f + .4f * ring(coarse, a) + .38f * (ring(fine, a) - .5f) + .2f * (fr - .5f)));
            float t = Math.min(1, Math.max(0, (d / rr - .35f) / .7f));
            float pale = lift * (1 - t * t * (3 - 2 * t)) * (.55f + .45f * ring(rays, a));
            if (d < 1.6f) pale = Math.max(pale, Math.min(.92f, lift * 1.25f));
            // The rim, broken here and there.
            f[y * w + x] *= (1 - Math.min(.92f, pale)) * (1 + .32f * fr * bell((d - rr - .3f) / 1.1f));
        }
    }
    /** Smooth noise round a circle: {@code n} random values from the seed, at even steps of a turn. */
    private static float[] ring(long s, int n) { float[] v = new float[n]; for (int i = 0; i < n; i++) v[i] = unit(s, i, 0, 21); return v; }
    /** {@code ring} at {@code a} (0 to 1, a turn), eased between its values. */
    private static float ring(float[] ring, float a) {
        int n = ring.length;
        float v = a * n;
        int i = (int) v;
        float t = v - i;
        t = t * t * (3 - 2 * t);
        return lerp(ring[(i % n + n) % n], ring[((i + 1) % n + n) % n], t);
    }

    // ---- Plastic wrap ----

    /** Plastic wrap's facets across, px, fine to coarse (the macro dial). */
    static float facet(double amount) { return (float) (14 + 46 * amount); }

    /**
     * Pigment pools along the film's creases: soft-edged ridges where a gradient noise, stretched the way the film was
     * pulled, crosses zero (wide where it turns slowly, tapering where fast), fading in and out along their length, a
     * finer set branching off them; between, the film pressed the paint away, palest far from a crease.
     */
    private static void wrap(double amount, long seed, int x0, int y0, int w, int h, float[] p, float[] q, boolean[] mark) {
        float s = facet(amount), warp = .3f * s;
        long ws = seed ^ 0x7e3aL;
        // The film pulls one way more than the other: creases run long that way.
        float ang = 3.1416f * unit(ws, 0, 0, 1), ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        Perlin creases = new Perlin(ws + 11), fine = new Perlin(ws + 12);
        // The warp and where the creases hold, all smooth over 7 px or more: on a 4 px lattice, eased between.
        int gw = w / 4 + 2, gh = h / 4 + 2;
        float[][] lat = new float[4][gw * gh];
        for (int gy = 0; gy < gh; gy++) for (int gx = 0; gx < gw; gx++) {
            float px = x0 + gx * 4, py = y0 + gy * 4;
            int g = gy * gw + gx;
            lat[0][g] = warp * (noise(ws + 1, px / s, py / s) - .5f) * 2;
            lat[1][g] = warp * (noise(ws + 2, px / s, py / s) - .5f) * 2;
            lat[2][g] = (float) StrokeMath.smoothstep(.25, .7, noise(ws + 5, px / (1.1f * s), py / (1.1f * s)));
            lat[3][g] = (float) StrokeMath.smoothstep(.45, .8, noise(ws + 7, px / (.6f * s), py / (.6f * s)));
        }
        float[] at = new float[4];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            if (!mark[i] || p[i] < 3) continue;
            float fx = x / 4f, fy = y / 4f;
            int gx = (int) fx, gy = (int) fy;
            float ax = fx - gx, ay = fy - gy;
            int g = gy * gw + gx;
            for (int k = 0; k < 4; k++) { float[] l = lat[k]; at[k] = lerp(lerp(l[g], l[g + 1], ax), lerp(l[g + gw], l[g + gw + 1], ax), ay); }
            float px = x0 + x + at[0], py = y0 + y + at[1];
            // Along the pull slowly, across it fast: ridges run with the pull.
            float u = (px * ca + py * sa) / (2.6f * s), v = (-px * sa + py * ca) / (.9f * s);
            float n1 = creases.at(u, v), n2 = at[3] > 0 ? fine.at(u * 2.2f + 3.1f, v * 2.2f + 7.7f) : 1;
            float ridge = bell(n1 / .085f), pool = bell(n1 / .3f), branch = bell(n2 / .13f);
            float e = Math.min(1, Math.max(0, (Math.abs(n1) - .04f) / .4f)), pale = .26f * e * e * (3 - 2 * e);
            float k = (1 - pale) * (1 + at[2] * (.85f * ridge + .2f * pool) + .3f * at[3] * branch);
            // Pooled pigment darkens the wash, never past about twice its own.
            q[i] = Math.min(255, p[i] * Math.min(1.8f, k));
        }
    }

    /** Gradient noise (Perlin's), about -0.7 to 0.7 on a unit lattice, its gradients from the seed. */
    private static final class Perlin {
        final int[] perm = new int[512];
        final float[] gx = new float[256], gy = new float[256];
        Perlin(long s) {
            int[] p = new int[256];
            for (int i = 0; i < 256; i++) p[i] = i;
            for (int i = 255; i > 0; i--) { int j = (int) (unit(s, i, 0, 31) * (i + 1)); int t = p[i]; p[i] = p[j]; p[j] = t; }
            for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
            for (int i = 0; i < 256; i++) { double a = 2 * Math.PI * unit(s, i, 0, 32); gx[i] = (float) Math.cos(a); gy[i] = (float) Math.sin(a); }
        }
        float at(float x, float y) {
            int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
            float fx = x - xi, fy = y - yi;
            int a = xi & 255, b = yi & 255;
            int g00 = perm[perm[a] + b], g10 = perm[perm[a + 1] + b], g01 = perm[perm[a] + b + 1], g11 = perm[perm[a + 1] + b + 1];
            float d00 = gx[g00] * fx + gy[g00] * fy, d10 = gx[g10] * (fx - 1) + gy[g10] * fy;
            float d01 = gx[g01] * fx + gy[g01] * (fy - 1), d11 = gx[g11] * (fx - 1) + gy[g11] * (fy - 1);
            float u = fx * fx * fx * (fx * (fx * 6 - 15) + 10), v = fy * fy * fy * (fy * (fy * 6 - 15) + 10);
            return lerp(lerp(d00, d10, u), lerp(d01, d11, u), v);
        }
    }

    // ---- Feathering ----

    /** Feathering's longest hairlines on a paper of average drink, px, short to long (the macro dial). */
    static double threadLength(double amount) { return 3 + 14 * amount; }

    /** How far and how much ink wicks into {@code paper} at Texture {@code tooth}: its {@link BrushTexture#absorbency}, more with more texture (plain paper's alone). */
    static float wicks(BrushTexture paper, double tooth) {
        float d = paper.absorbency;
        return paper == BrushTexture.PLAIN ? d : d * (float) (.55 + .9 * tooth);
    }

    /** Plain paper's stand-in fibres: one lies across each cell this many px square, its way fixed to the page. */
    private static final int FIBRE = 3;
    private static final long PAGE = 0xf1b2e5L;

    /** The paper's height at a page pixel, 0 (a valley) to 1 (a ridge): its fine grain and its mid, as the stroke reads them. */
    static float height(PaperGrain g, int x, int y) { return (.35f * g.fine(x, y) + .65f * g.mid(x, y)) / 255; }

    /**
     * Ink on soft paper: the edge goes fuzzy, a halo of bleed creeping into the paper's valleys and stopping at its ridges,
     * and fine grey hairlines wick out of it, turning to run along the valleys (the grain's contours, so laid lines,
     * a canvas weave or a fibrous scan show in them), fading from the ink's tone to nothing, stopping where a ridge rises;
     * more where the ink lies heavier (darker, wider: pressure, the slow parts and the ends), and more and further the
     * more the paper drinks ({@link #wicks}). Plain paper, which has no grain to follow, feathers gently along stand-in
     * fibres fixed to the page.
     */
    private static int[] feather(double amount, long seed, BrushTexture paper, double tooth, int x0, int y0, int w, int h, byte[] tones, float[] p, float[] q, boolean[] mark) {
        int n = w * h;
        int[] ink = new int[n];
        java.util.Arrays.fill(ink, -1);
        float most = 0;
        for (int i = 0; i < n; i++) if (mark[i]) most = Math.max(most, p[i]);
        if (most < 12) return ink;
        PaperGrain g = paper == BrushTexture.PLAIN ? null : paper.grain();
        float wick = wicks(paper, tooth), len = (float) threadLength(amount) * wick * (g != null ? 1.5f : 1);
        long s = seed ^ 0xfea7L;
        float[] own = new float[n], heavy = new float[n], tmp = new float[n];
        for (int i = 0; i < n; i++) own[i] = mark[i] ? p[i] : 0;
        // How much ink lies near: the mark blurred over a few px.
        System.arraycopy(own, 0, heavy, 0, n);
        for (int pass = 0; pass < 2; pass++) { box(heavy, tmp, w, h, 3, true); box(tmp, heavy, w, h, 3, false); }
        float top = 0;
        for (float v : heavy) top = Math.max(top, v);
        // The halo: the edge blurred a pixel out (two on a paper that drinks), deeper in the valleys, none on the ridges.
        float[] halo = own.clone();
        int reach = wick > 1.1f ? 2 : 1;
        box(halo, tmp, w, h, reach, true); box(tmp, halo, w, h, reach, false);
        float soak = Math.min(1, .45f + .35f * wick);
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            int i = y * w + x;
            if (mark[i] || halo[i] < 4 || g != null) continue; // a paper with grain: its own creep, below
            float valley = g != null ? clamp01(1.7f - 2.2f * height(g, x0 + x, y0 + y)) : .5f + .4f * unit(PAGE, Math.floorDiv(x0 + x, FIBRE), Math.floorDiv(y0 + y, FIBRE), 5);
            float bleed = halo[i] * soak * valley;
            if (bleed <= q[i]) continue;
            q[i] = p[i] + bleed * (1 - p[i] / 255);
            int from = mark[i - 1] ? i - 1 : mark[i + 1] ? i + 1 : mark[i - w] ? i - w : mark[i + w] ? i + w : -1;
            ink[i] = from >= 0 ? tones[from] & 255 : tones[i] & 255;
        }
        // On a paper with grain the wet edge creeps on, pixel by pixel, only down its valleys: a ragged edge in its texture.
        if (g != null) {
            float[] wet = own.clone();
            int[] wetTone = new int[n];
            for (int i = 0; i < n; i++) wetTone[i] = tones[i] & 255;
            for (int ring = 0, rings = Math.round(1 + 2.5f * wick); ring < rings; ring++) {
                float[] next = wet.clone();
                for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
                    int i = y * w + x;
                    if (wet[i] > 0) continue;
                    int from = i - 1;
                    for (int j : new int[]{i + 1, i - w, i + w}) if (wet[j] > wet[from]) from = j;
                    if (wet[from] < 12) continue;
                    float v = wet[from] * .62f * clamp01(1.5f - 2.6f * height(g, x0 + x, y0 + y));
                    if (v < 4) continue;
                    next[i] = v; wetTone[i] = wetTone[from];
                }
                wet = next;
            }
            for (int i = 0; i < n; i++) {
                if (mark[i] || wet[i] <= 0) continue;
                float v = p[i] + wet[i] * soak * (1 - p[i] / 255);
                if (v > q[i]) { q[i] = v; ink[i] = wetTone[i]; }
            }
        }
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            int i = y * w + x;
            if (!mark[i] || p[i] < most * .3f) continue;
            // The mark's edge: outward is down the paint's slope.
            float gx = own[i + 1] - own[i - 1], gy = own[i + w] - own[i - w];
            float gm = (float) Math.sqrt(gx * gx + gy * gy);
            if (gm < most * .25f) continue;
            int px = x0 + x, py = y0 + y;
            // Ink finds the paper's channels where they meet the edge.
            float weight = (p[i] / most) * (.3f + .7f * heavy[i] / top), at = g != null ? 1.4f * clamp01(1.4f - 1.6f * height(g, px, py)) : 1;
            if (unit(s, px, py, 1) > .85f * weight * weight * Math.min(1.4f, wick) * at) continue;
            float nx = -gx / gm, ny = -gy / gm;
            float dx = nx, dy = ny;
            if (g == null) {
                // Straight out, bent toward the fibre it meets.
                float th = fibre(px, py), fx = (float) Math.cos(th), fy = (float) Math.sin(th);
                if (fx * nx + fy * ny < 0) { fx = -fx; fy = -fy; }
                dx = nx * .75f + fx * .45f; dy = ny * .75f + fy * .45f;
            }
            float jit = .5f * (unit(s, px, py, 2) - .5f), c = (float) Math.cos(jit), sn = (float) Math.sin(jit), rx = dx * c - dy * sn, ry = dx * sn + dy * c, d = (float) Math.sqrt(rx * rx + ry * ry);
            float u = unit(s, px, py, 4), l = len * (.15f + .85f * u * u);
            thread(q, ink, mark, g, w, h, x0, y0, x + .5f, y + .5f, rx / d, ry / d, l, p[i] * (.6f + .25f * unit(s, px, py, 5)), tones[i] & 255, s * 131 + px * 7919L + py, 0);
        }
        return ink;
    }
    private static float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    /** The way plain paper's stand-in fibre runs at a page pixel, radians (a fibre runs both ways): fixed to the page. */
    static float fibre(int x, int y) { return 3.1416f * unit(PAGE, Math.floorDiv(x, FIBRE), Math.floorDiv(y, FIBRE), 2); }

    /**
     * One hairline from {@code x, y} (in the region at {@code x0, y0} on the page) along {@code dx, dy} for {@code l}
     * px, its paint {@code p} at its root thinning to nothing at its tip. On a paper with grain {@code g} it turns to run
     * along the valley it is in (the way the grain's height doesn't change), drawn a little down into it, never back
     * on itself, and stops where the paper rises to a ridge; on plain paper it bends toward each stand-in fibre it
     * crosses. Now and then a shorter one forks off.
     */
    private static void thread(float[] q, int[] ink, boolean[] mark, PaperGrain g, int w, int h, int x0, int y0, float x, float y, float dx, float dy, float l, float p, int tone, long s, int depth) {
        float step = .5f;
        int steps = (int) (l / step), cell = Math.floorDiv(x0 + (int) x, FIBRE) * 7919 + Math.floorDiv(y0 + (int) y, FIBRE);
        float floor = g == null ? 0 : height(g, x0 + (int) x, y0 + (int) y);
        for (int k = 1; k <= steps; k++) {
            float t = k * step / l;
            x += dx * step; y += dy * step;
            int ix = (int) x, iy = (int) y;
            if (ix < 0 || iy < 0 || ix >= w || iy >= h) return;
            int px = x0 + ix, py = y0 + iy;
            if (g != null) {
                float here = height(g, px, py);
                // Ink doesn't climb: a ridge well above where it set out ends it, and it thins as the paper rises.
                if (here > floor + .3f) return;
                floor = Math.min(floor, here);
                float a = p * (1 - t) * clamp01(1.5f - 1.8f * here) * (.75f + .25f * unit(s, k, depth, 7));
                splat(q, ink, mark, w, h, x, y, a, tone);
                // The valley's way: along the grain's contour, the side nearer the way it was going.
                float gx = height(g, px + 2, py) - height(g, px - 2, py), gy = height(g, px, py + 2) - height(g, px, py - 2), gm = (float) Math.sqrt(gx * gx + gy * gy);
                if (gm < 1e-3f) continue;
                float ax = -gy / gm, ay = gx / gm;
                if (ax * dx + ay * dy < 0) { ax = -ax; ay = -ay; }
                float follow = Math.min(1, gm * 5), bx = dx * (1 - .55f * follow) + (ax * .55f - gx / gm * .15f) * follow, by = dy * (1 - .55f * follow) + (ay * .55f - gy / gm * .15f) * follow, b = (float) Math.sqrt(bx * bx + by * by);
                if (b < 1e-3f) return;
                bx /= b; by /= b;
                if (depth == 0 && k % 6 == 0 && unit(s, k, depth, 8) < .05f) thread(q, ink, mark, g, w, h, x0, y0, x, y, -ax, -ay, (l - k * step) * .5f, a * .8f, tone, s * 17 + k, 1);
                dx = bx; dy = by;
                continue;
            }
            splat(q, ink, mark, w, h, x, y, p * (1 - t) * (.75f + .25f * unit(s, k, depth, 7)), tone);
            int now = Math.floorDiv(px, FIBRE) * 7919 + Math.floorDiv(py, FIBRE);
            if (now == cell) continue;
            cell = now;
            float th = fibre(px, py), fx = (float) Math.cos(th), fy = (float) Math.sin(th);
            if (fx * dx + fy * dy < 0) { fx = -fx; fy = -fy; }
            float bx = dx * .7f + fx * .3f, by = dy * .7f + fy * .3f, b = (float) Math.sqrt(bx * bx + by * by);
            if (depth == 0 && unit(s, k, depth, 8) < .06f) thread(q, ink, mark, null, w, h, x0, y0, x, y, fx, fy, (l - k * step) * .5f, p * (1 - t) * .8f, tone, s * 17 + k, 1);
            dx = bx / b; dy = by / b;
        }
    }
    /** Paint {@code a} at {@code x, y} on the pixels it touches, a hairline's share of each (the darker of two crossing), never on the mark. */
    private static void splat(float[] q, int[] ink, boolean[] mark, int w, int h, float x, float y, float a, int tone) {
        int ix = (int) Math.floor(x - .5f), iy = (int) Math.floor(y - .5f);
        float fx = x - .5f - ix, fy = y - .5f - iy;
        for (int j = 0; j < 2; j++) for (int k = 0; k < 2; k++) {
            int px = ix + k, py = iy + j;
            if (px < 0 || py < 0 || px >= w || py >= h) continue;
            int i = py * w + px;
            if (mark[i]) continue;
            float v = a * Math.min(1, (k == 0 ? 1 - fx : fx) * (j == 0 ? 1 - fy : fy) * 1.8f);
            if (v > q[i]) { q[i] = v; if (ink[i] < 0) ink[i] = tone; }
        }
    }

    // ---- Foxing ----

    /** Foxing's spots come this near the mark, px; its tone, the grey of an old book's spots. */
    private static final int FOX_NEAR = 26, FOX_TONE = 150;
    /** Foxing's spots, few to many (the macro dial): a chance of one a 900 to one a 90 px² around the mark. */
    static double spotArea(double amount) { return 900 * Math.pow(.1, amount); }

    private static void fox(double amount, long seed, int x0, int y0, int w, int h, float[] p, float[] q, boolean[] mark) {
        int n = w * h;
        // How near the mark: its coverage blurred over FOX_NEAR, twice.
        float[] near = new float[n], tmp = new float[n];
        for (int i = 0; i < n; i++) near[i] = mark[i] ? 1 : 0;
        int r = FOX_NEAR / 2;
        for (int pass = 0; pass < 2; pass++) { box(near, tmp, w, h, r, true); box(tmp, near, w, h, r, false); }
        float top = 0;
        for (float v : near) top = Math.max(top, v);
        if (top <= 0) return;
        int cell = (int) Math.max(5, Math.round(Math.sqrt(spotArea(amount))));
        long s = seed ^ 0xf0c5L;
        float[] add = new float[n];
        int cx0 = Math.floorDiv(x0, cell), cy0 = Math.floorDiv(y0, cell), cx1 = Math.floorDiv(x0 + w - 1, cell), cy1 = Math.floorDiv(y0 + h - 1, cell);
        for (int cy = cy0; cy <= cy1; cy++) for (int cx = cx0; cx <= cx1; cx++) {
            float gx = cx * cell + cell * unit(s, cx, cy, 1), gy = cy * cell + cell * unit(s, cx, cy, 2);
            int lx = (int) gx - x0, ly = (int) gy - y0;
            if (lx < 0 || ly < 0 || lx >= w || ly >= h) continue;
            // Near the mark, and in clusters, as damp spots come.
            float wgt = Math.min(1, near[ly * w + lx] / top * 1.6f) * (.3f + 1.4f * noise(s + 1, gx / 45, gy / 45));
            if (unit(s, cx, cy, 3) > wgt) continue;
            float u = unit(s, cx, cy, 4), rad = 1 + 5 * (float) Math.pow(u, 1.8);
            spot(add, w, h, gx - x0, gy - y0, rad, s * 37 + cx * 7919L + cy, (55 + 55 * unit(s, cx, cy, 5)) * (.8f + .2f * Math.min(1, rad / 3)));
        }
        for (int i = 0; i < n; i++) if (add[i] > 0) q[i] = Math.min(255, p[i] + add[i] * (1 - p[i] / 510));
    }
    /**
     * One spot of foxing into {@code add}: a blotchy outline, soft, mottled, densest at its core; a larger one paler
     * inside a faint darker tide line, with a speck or two strayed from it.
     */
    private static void spot(float[] add, int w, int h, float cx, float cy, float r, long s, float dark) {
        boolean rim = r > 3.4f;
        int reach = (int) Math.ceil(r * 1.5f) + 2;
        int xa = Math.max(0, (int) cx - reach), xb = Math.min(w - 1, (int) cx + reach), ya = Math.max(0, (int) cy - reach), yb = Math.min(h - 1, (int) cy + reach);
        float[] blot = ring(s, 6), ragged = ring(s + 1, 17);
        for (int y = ya; y <= yb; y++) for (int x = xa; x <= xb; x++) {
            float dx = x + .5f - cx, dy = y + .5f - cy, d = (float) Math.sqrt(dx * dx + dy * dy);
            float a = (float) (Math.atan2(dy, dx) / (2 * Math.PI) + .5);
            float rr = r * (.72f + .5f * ring(blot, a) + .2f * (ring(ragged, a) - .5f));
            float t = d / rr;
            if (t >= 1.3f) continue;
            float v = 1 - (float) StrokeMath.smoothstep(0, 1.15, t);
            if (rim) { float e = (t - .88f) / .2f; v = .9f * v + .18f * bell(e) + .12f * bell(d / 1.5f); }
            // Mottled: blotches a couple of pixels across, and grit.
            v *= .6f + .3f * noise(s + 3, dx / 2.2f, dy / 2.2f) + .1f * unit(s, Math.round(dx * 2), Math.round(dy * 2), 8);
            int i = y * w + x;
            add[i] = Math.max(add[i], dark * v);
        }
        if (rim) for (int k = 0; k < 3; k++) {
            if (unit(s, k, 0, 30) > .6f) continue;
            float ang = 6.283f * unit(s, k, 0, 31), far = r * (1.4f + unit(s, k, 0, 32));
            spot(add, w, h, cx + far * (float) Math.cos(ang), cy + far * (float) Math.sin(ang), .7f + .6f * unit(s, k, 0, 33), s * 13 + k, dark * 1.1f);
        }
    }
    // ---- Letterpress ----

    /**
     * Type pressed into paper: the ink evened out to a solid, squeezed into a darker rim along the mark's edge (wider and
     * darker the deeper the impression, a hair of it out past the edge when deep), the middle lighter and mottled where
     * the ink sat thin on the fibres, and now and then a starved patch where the roller left less ink and the paper's
     * valleys (its Paper part's grain, fixed to the page; plain paper's stand-in fibres) took none, more of them the
     * lighter the impression.
     */
    private static void press(double amount, long seed, BrushTexture paper, int x0, int y0, int w, int h, float[] p, float[] q, boolean[] mark) {
        int n = w * h;
        float most = 0;
        for (int i = 0; i < n; i++) if (mark[i]) most = Math.max(most, p[i]);
        if (most < 6) return;
        boolean[] in = new boolean[n], out = new boolean[n];
        for (int i = 0; i < n; i++) { in[i] = mark[i] && p[i] >= .3f * most; out[i] = !in[i]; }
        float[] d = inward(in, w, h), past = inward(out, w, h);
        PaperGrain g = paper == BrushTexture.PLAIN ? null : paper.grain();
        long s = seed ^ 0x1e77L;
        float deep = (float) amount, solid = most * .95f;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x, px = x0 + x, py = y0 + y;
            if (!in[i]) {
                // A deep impression squeezes a hair of ink out past the type's edge.
                if (past[i] <= 2 && deep > .4f) q[i] = Math.max(q[i], solid * .3f * (deep - .4f) / .6f * bell((past[i] - .5f) / .9f));
                continue;
            }
            float v = lerp(p[i], solid, .7f);
            float rim = bell((d[i] - .8f) / (.9f + 1.2f * deep));
            v *= 1 + (.08f + .14f * deep) * rim;
            // The middle: ink thin on the fibres, a fine mottle with a coarser one under it, and the paper's low places
            // taking less of it, more so the lighter the impression.
            float mid = clamp01((d[i] - 1.2f) / 2.5f);
            float mott = .5f * noise(s + 1, px / 1.8f, py / 1.8f) + .3f * noise(s + 2, px / 5.5f, py / 5.5f) + .2f * unit(s, px, py, 3);
            float low = g != null ? height(g, px, py) : .6f * noise(PAGE, px / 1.6f, py / 1.6f) + .4f * unit(PAGE, px, py, 9);
            v *= 1 - mid * ((.05f + .1f * (1 - deep)) * (.3f + 1.4f * mott) + (.1f + .25f * (1 - deep)) * clamp01((.62f - low) / .4f));
            // Starved: where ink ran short (patches here and there), the valleys show the paper.
            float patch = (float) StrokeMath.smoothstep(.58 + .14 * deep, .78 + .1 * deep, noise(s + 4, px / 15f, py / 15f));
            float starve = patch * (float) StrokeMath.smoothstep(.55 - .15 * deep, .3 - .15 * deep, low);
            v *= 1 - .85f * starve * (1 - .6f * rim);
            q[i] = Math.min(255, v);
        }
    }

    // ---- Torn paper ----

    /** How far past the mark a torn scrap's edge and its shadow reach, px. */
    private static final int TORN_OUT = 9;

    /**
     * The mark a scrap of paper torn and glued down: its edge wanders in and out of the mark's at several scales, more the
     * more ragged; inside it, a band of the paper's off-white, fibrous core where the grey top layer tore back (wider
     * where the scrap is wide), a few grey fibres reaching into it and white ones past the edge; a faint contour and a
     * soft shadow under the edge, lit from the top left, so the white reads on white paper. Inside, the mark keeps its
     * own tone.
     */
    private static void torn(double amount, long seed, int x0, int y0, int w, int h, float[] p, float[] q, boolean[] mark) {
        int n = w * h;
        float most = 0;
        for (int i = 0; i < n; i++) if (mark[i]) most = Math.max(most, p[i]);
        if (most < 6) return;
        boolean[] in = new boolean[n], out = new boolean[n];
        for (int i = 0; i < n; i++) { in[i] = mark[i] && p[i] >= .3f * most; out[i] = !in[i]; }
        float[] din = inward(in, w, h), dout = inward(out, w, h), wide = widest(din, w, h, 7);
        long s = seed ^ 0x7042L;
        float rag = (float) amount;
        // The tear's edge, signed (inside below 0), and the white band's width.
        float[] edge = new float[n], band = new float[n];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x, px = x0 + x, py = y0 + y;
            float sd = in[i] ? .5f - din[i] : dout[i] - .5f;
            if (sd > TORN_OUT) { edge[i] = TORN_OUT; continue; }
            float wob = (1 + 2.2f * rag) * (noise(s + 1, px / 11f, py / 11f) - .5f) * 2 + (.5f + 1.3f * rag) * (noise(s + 2, px / 3.5f, py / 3.5f) - .5f) * 2
                    + .6f * rag * (unit(s, px, py, 3) - .5f);
            edge[i] = sd - wob;
            band[i] = Math.min(.45f * wide[i], (2.5f + 5 * rag) * (.4f + .9f * noise(s + 4, px / 6f, py / 6f)));
        }
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            int i = y * w + x, px = x0 + x, py = y0 + y;
            float e = edge[i];
            if (e >= TORN_OUT) continue;
            if (e < 0) {
                // In the scrap: its core, off-white and fibrous, then its grey top layer.
                float t = clamp01((-e - band[i] + .7f) / 1.4f), core = 10 + 22 * noise(s + 5, px / 1.4f, py / 1.4f);
                q[i] = lerp(Math.min(core, p[i]), p[i], t * t * (3 - 2 * t));
                continue;
            }
            // Past it: a contour and a shadow below and to the right of the edge; the mark's own paint torn away.
            float gx = edge[i + 1] - edge[i - 1], gy = edge[i + w] - edge[i - w], gm = (float) Math.sqrt(gx * gx + gy * gy) + 1e-3f;
            float lit = clamp01(.3f + .9f * (gx + gy) / gm * .7071f);
            float shade = 60 * lit * (float) Math.pow(clamp01(1 - e / 4.5f), 1.6) + 16 * bell((e - .4f) / .7f);
            q[i] = mark[i] ? shade : Math.max(q[i], shade);
        }
        // Fibres: grey from the top layer reaching out across the white, white ones past the edge breaking the contour.
        for (int y = 2; y < h - 2; y++) for (int x = 2; x < w - 2; x++) {
            int i = y * w + x, px = x0 + x, py = y0 + y;
            float e = edge[i];
            if (e >= 0 || e < -band[i] - 1.5f || -e < band[i] - .5f || unit(s, px, py, 5) > .1f + .15f * rag) continue;
            float gx = edge[i + 1] - edge[i - 1], gy = edge[i + w] - edge[i - w], gm = (float) Math.sqrt(gx * gx + gy * gy);
            if (gm < 1e-3f) continue;
            float a = .9f * (unit(s, px, py, 6) - .5f), c = (float) Math.cos(a), sn = (float) Math.sin(a);
            float dx = (gx * c - gy * sn) / gm, dy = (gx * sn + gy * c) / gm;
            float len = band[i] * (.2f + .5f * unit(s, px, py, 7)) + .5f;
            fibre(q, w, h, x + .5f, y + .5f, dx, dy, len, p[i] * .4f, true);
            if (unit(s, px, py, 8) < .45f) fibre(q, w, h, x + .5f + dx * band[i], y + .5f + dy * band[i], dx, dy, 1.5f + 3 * unit(s, px, py, 9), 0, false);
        }
    }
    /** A fibre from {@code x, y} along {@code dx, dy} for {@code l} px: grey ({@code over}, paint {@code a} thinning out) over what's there, or white, lifting it. */
    private static void fibre(float[] q, int w, int h, float x, float y, float dx, float dy, float l, float a, boolean over) {
        for (float k = 0; k <= l; k += .5f) {
            int ix = (int) (x + dx * k), iy = (int) (y + dy * k);
            if (ix < 0 || iy < 0 || ix >= w || iy >= h) return;
            int i = iy * w + ix;
            float t = k / l;
            if (over) q[i] = Math.max(q[i], a * (1 - .6f * t));
            else q[i] *= .15f + .6f * t;
        }
    }

    // ---- Cross-stitch ----

    /**
     * Cross-stitch on a fabric grid fixed to the page, {@code amount} px a cell: a cell the mark covers a fair share of is
     * stitched in its grey there (its darkest ink), the rest of the mark goes; each stitch two threads corner to
     * corner, the bottom one / and the top one \ over it, each round across with a sheen down its middle and its twist
     * showing as fine slanting bands, a shadow where the top crosses the bottom.
     */
    private static int[] stitch(double amount, long seed, int x0, int y0, int w, int h, byte[] tones, float[] p, float[] q, boolean[] mark) {
        int n = w * h;
        int[] ink = new int[n];
        java.util.Arrays.fill(ink, -1);
        float most = 0;
        for (int i = 0; i < n; i++) if (mark[i]) most = Math.max(most, p[i]);
        if (most < 6) return ink;
        int c = Math.max(4, (int) Math.round(amount));
        long s = seed ^ 0x5717L;
        for (int i = 0; i < n; i++) if (mark[i]) q[i] = 0;
        int cx0 = Math.floorDiv(x0, c), cy0 = Math.floorDiv(y0, c), cx1 = Math.floorDiv(x0 + w - 1, c), cy1 = Math.floorDiv(y0 + h - 1, c);
        float tw = .13f * c + .3f;
        for (int cy = cy0; cy <= cy1; cy++) for (int cx = cx0; cx <= cx1; cx++) {
            int lx = cx * c - x0, ly = cy * c - y0;
            float sum = 0; int covered = 0, cells = 0, dark = 255;
            for (int y = Math.max(0, ly); y < Math.min(h, ly + c); y++) for (int x = Math.max(0, lx); x < Math.min(w, lx + c); x++) {
                int i = y * w + x;
                cells++;
                if (!mark[i] || p[i] < .25f * most) continue;
                covered++; sum += p[i]; dark = Math.min(dark, tones[i] & 255);
            }
            if (cells < c * c || covered < .3f * c * c) continue;
            float tone = sum / covered;
            // Each thread from near one corner to near the other; the bottom first, the top over it.
            for (int leg = 0; leg < 2; leg++) {
                float ax = lx + .12f * c, bx = lx + .88f * c, ay = leg == 0 ? ly + .88f * c : ly + .12f * c, by = leg == 0 ? ly + .12f * c : ly + .88f * c;
                float jx = .5f * (unit(s, cx, cy, 1 + leg) - .5f), jy = .5f * (unit(s, cx, cy, 3 + leg) - .5f);
                ax += jx; bx += jx; ay += jy; by += jy;
                float ux = bx - ax, uy = by - ay, len = (float) Math.sqrt(ux * ux + uy * uy);
                ux /= len; uy /= len;
                float twist = unit(s, cx, cy, 5 + leg);
                for (int y = Math.max(0, ly - 1); y < Math.min(h, ly + c + 1); y++) for (int x = Math.max(0, lx - 1); x < Math.min(w, lx + c + 1); x++) {
                    float rx = x + .5f - ax, ry = y + .5f - ay, along = rx * ux + ry * uy, across = -rx * uy + ry * ux;
                    float cov = threadCover(along, across, len, tw);
                    if (cov <= 0) continue;
                    int i = y * w + x;
                    float v = Math.min(255, tone * threadShade(along, across, tw, twist)) * cov;
                    if (leg == 1) {
                        // The top thread covers the bottom one, which is shadowed along its edges.
                        float under = q[i] * (1 - cov) * threadUnder(across, tw);
                        q[i] = Math.min(255, v + under);
                    } else q[i] = Math.max(q[i], v);
                    ink[i] = dark;
                }
            }
        }
        // A stitch is one thread's grey through and through, not the mark's grain under it.
        for (int i = 0; i < n; i++) if (ink[i] >= 0) tones[i] = (byte) ink[i];
        return ink;
    }

    /** How much of a pixel a thread {@code len} long and {@code wide} half-wide covers, {@code along} it and {@code across} it from its start (Print Shading's Stitches too, {@link PrintScreen#dark}). */
    static float threadCover(float along, float across, float len, float wide) {
        return clamp01(Math.min(wide - Math.abs(across), Math.min(along, len - along) + .3f) + .5f);
    }
    /** A thread's shade there: round across, darker at its sides, a sheen just off its middle; its plies as slanting bands, {@code twist} their phase. */
    static float threadShade(float along, float across, float wide, float twist) {
        float u = across / wide;
        float shade = 1 + .25f * u * u - .3f * bell((u + .2f) / .35f);
        return shade * (.93f + .1f * (float) Math.sin(2 * Math.PI * ((along + 1.2f * across) / 3.6f + twist)));
    }
    /** The shadow the top thread casts on the bottom one along its edges: what the bottom's darkness is multiplied by. */
    static float threadUnder(float across, float wide) { return 1 + .5f * bell((Math.abs(across) - wide) / .9f); }

    // ---- Cracks ----

    /** Cracks' cells across where the paint lies thickest, px, few (wide) to many (small): the macro dial. */
    static float crackCell(double amount) { return (float) (40 - 26 * amount); }

    /**
     * Thick paint crazing: a network of fine pale cracks, the cells of a warped Voronoi tiling fixed to the page (a coarse
     * one where the paint lies thick, a finer one splitting its cells where thickest), each edge cracked or not, fully or
     * part way, by the seed; a crack paler down its line and a touch darker on its shoulders where the paint curls up.
     * Thin, pale paint doesn't crack.
     */
    private static void cracks(double amount, long seed, int x0, int y0, int w, int h, float[] p, float[] q, boolean[] mark) {
        int n = w * h;
        float most = 0;
        for (int i = 0; i < n; i++) if (mark[i]) most = Math.max(most, p[i]);
        if (most < 20) return;
        float[] heavy = new float[n], tmp = new float[n];
        for (int i = 0; i < n; i++) heavy[i] = mark[i] ? p[i] : 0;
        for (int pass = 0; pass < 2; pass++) { box(heavy, tmp, w, h, 2, true); box(tmp, heavy, w, h, 2, false); }
        long s = seed ^ 0xc7acL;
        float cell = crackCell(amount), reach = .2f * cell + .5f;
        // The cracks' wander, smooth over a cell, and their wobble, over 3 px: on a 2 px lattice, eased between.
        int gw = w / 2 + 2, gh = h / 2 + 2;
        float[] ox = new float[gw * gh], oy = new float[gw * gh];
        for (int gy = 0; gy < gh; gy++) for (int gx = 0; gx < gw; gx++) {
            float px = x0 + 2 * gx, py = y0 + 2 * gy;
            ox[gy * gw + gx] = .4f * cell * (noise(s + 1, px / cell, py / cell) - .5f) + .5f * (noise(s + 2, px / 3f, py / 3f) - .5f);
            oy[gy * gw + gx] = .4f * cell * (noise(s + 3, px / cell, py / cell) - .5f) + .5f * (noise(s + 4, px / 3f, py / 3f) - .5f);
        }
        Cells[] levels = new Cells[2];
        for (int level = 0; level < 2; level++) {
            float size = level == 0 ? cell : cell * .5f;
            levels[level] = new Cells(s + 10 + level, (x0 - reach) / size, (y0 - reach) / size, (x0 + w + reach) / size, (y0 + h + reach) / size);
        }
        long[] pair = new long[1];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            if (!mark[i] || p[i] < 20) continue;
            float thick = Math.min(heavy[i], p[i]) / 255;
            if (thick < .25f) continue;
            int g = (y / 2) * gw + x / 2;
            float ax = (x & 1) * .5f, ay = (y & 1) * .5f;
            float wx = x0 + x + lerp(lerp(ox[g], ox[g + 1], ax), lerp(ox[g + gw], ox[g + gw + 1], ax), ay);
            float wy = y0 + y + lerp(lerp(oy[g], oy[g + 1], ax), lerp(oy[g + gw], oy[g + gw + 1], ax), ay);
            float crack = 0;
            // The coarse network where thick, the fine one where thicker still (and more of it, the more cracks).
            for (int level = 0; level < 2; level++) {
                float need = level == 0 ? .25f : .55f - .15f * (float) amount, at = clamp01((thick - need) / .15f);
                if (at <= 0) continue;
                float size = level == 0 ? cell : cell * .5f;
                float e = edge(levels[level], wx / size, wy / size, size, pair);
                if (e < 0) continue;
                float strength = (level == 0 ? 1 : .75f) * at * cracked(s + 20 + level, pair[0], wx / size, wy / size);
                crack = Math.max(crack, strength * line(e));
            }
            if (crack <= 0) continue;
            q[i] = p[i] * (1 - .72f * crack);
        }
        // Shoulders: a touch darker beside a crack, where the paint curls up.
        float[] c = q.clone();
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            int i = y * w + x;
            if (!mark[i] || c[i] < p[i] - .5f) continue;
            float drop = Math.max(Math.max(p[i - 1] - c[i - 1], p[i + 1] - c[i + 1]), Math.max(p[i - w] - c[i - w], p[i + w] - c[i + w]));
            if (drop > 0) q[i] = Math.min(255, p[i] + .18f * drop);
        }
    }
    /** How dark a crack line is at {@code e} px from its edge: a pixel and a bit wide, its core paler. */
    private static float line(float e) { return e < .25f ? 1 : clamp01(1 - (e - .25f) / .6f); }
    /**
     * A jittered grid's points, one a cell, for the cells round a region ({@code x0, y0} to {@code x1, y1}, in cells),
     * and one over the distance from each to its eight neighbours ({@code inv}, nine a cell, row by row), worked out once
     * from the seed.
     */
    private static final class Cells {
        final int i0, j0, cols;
        final float[] x, y, inv;
        Cells(long s, float x0, float y0, float x1, float y1) {
            i0 = (int) Math.floor(x0) - 3; j0 = (int) Math.floor(y0) - 3; cols = (int) Math.floor(x1) - i0 + 4;
            int rows = (int) Math.floor(y1) - j0 + 4;
            x = new float[cols * rows]; y = new float[cols * rows];
            for (int j = 0; j < rows; j++) for (int i = 0; i < cols; i++) {
                x[j * cols + i] = i0 + i + .1f + .8f * unit(s, i0 + i, j0 + j, 1);
                y[j * cols + i] = j0 + j + .1f + .8f * unit(s, i0 + i, j0 + j, 2);
            }
            inv = new float[9 * cols * rows];
            for (int j = 1; j < rows - 1; j++) for (int i = 1; i < cols - 1; i++) for (int n = 0; n < 9; n++) {
                int k = j * cols + i, o = (j + n / 3 - 1) * cols + i + n % 3 - 1;
                float ex = x[o] - x[k], ey = y[o] - y[k];
                if (n != 4) inv[9 * k + n] = 1 / (float) Math.sqrt(ex * ex + ey * ey);
            }
        }
        int at(int i, int j) { return (j - j0) * cols + i - i0; }
    }
    /**
     * The distance, px (a tiling of {@code size} px cells, at {@code x, y} in cells), to the nearest edge between the
     * nearest cell's point and another's; -1 past {@code 1.5} px. {@code pair[0]}: the edge's two cells, as one key.
     */
    private static float edge(Cells c, float x, float y, float size, long[] pair) {
        int ix = x >= 0 ? (int) x : (int) x - 1, iy = y >= 0 ? (int) y : (int) y - 1;
        float best = Float.MAX_VALUE, second = Float.MAX_VALUE, bx = 0, by = 0;
        int bi = 0, bj = 0;
        for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
            int k = c.at(ix + i, iy + j);
            float cx = c.x[k], cy = c.y[k];
            float d = (cx - x) * (cx - x) + (cy - y) * (cy - y);
            if (d < best) { second = best; best = d; bx = cx; by = cy; bi = ix + i; bj = iy + j; } else if (d < second) second = d;
        }
        // No edge is nearer than half the gap between the nearest two points: most of a cell is past every crack.
        if (((float) Math.sqrt(second) - (float) Math.sqrt(best)) * .5f * size > 1.5f) return -1;
        float near = Float.MAX_VALUE;
        int ni = 0, nj = 0, b9 = 9 * c.at(bi, bj);
        for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
            int ci = bi + i, cj = bj + j;
            if (ci == bi && cj == bj) continue;
            int k = c.at(ci, cj);
            float cx = c.x[k], cy = c.y[k];
            float mx = (cx + bx) / 2 - x, my = (cy + by) / 2 - y, ex = cx - bx, ey = cy - by;
            float d = (mx * ex + my * ey) * c.inv[b9 + 3 * (j + 1) + i + 1];
            if (d < near) { near = d; ni = ci; nj = cj; }
        }
        float px = near * size;
        if (px > 1.5f) return -1;
        long a = (long) bi * 73856093L ^ (long) bj * 19349663L, b = (long) ni * 73856093L ^ (long) nj * 19349663L;
        pair[0] = Math.min(a, b) * 31 + Math.max(a, b);
        return px;
    }
    /** How much the edge {@code pair} ({@link #edge}'s) has cracked, 0 to 1: most fully, a few part way, some not at all; those fade along themselves. */
    private static float cracked(long s, long pair, float x, float y) {
        float u = unit(s, pair, 0, 1);
        if (u < .18f) return 0;
        return u > .45f ? 1 : clamp01(noise(s + pair, x * 2.5f, y * 2.5f) * 2 - .4f);
    }

    // ---- Tarnish ----

    /** The darkest a tarnished line gets, a mid-dark grey (its tone, of 255): never black. */
    static final int TARNISH_DARKEST = 96;

    /**
     * Metalpoint tarnishing: the line darker, its darkest part a quarter of the way to {@link #TARNISH_DARKEST} to all of
     * it ({@code amount}) where it tarnishes most, the rest in proportion; unevenly (soft blotches, with a grain), each
     * pixel's tone darkening before its coverage grows, so the line keeps its exact shape.
     */
    private static void tarnish(double amount, long seed, int x0, int y0, int w, int h, byte[] tones, byte[] alpha, float[] p, boolean[] mark) {
        long s = seed ^ 0x7a2eL;
        float most = 0;
        for (int i = 0; i < p.length; i++) if (mark[i]) most = Math.max(most, p[i]);
        float cap = 255 - TARNISH_DARKEST, far = (.25f + .75f * (float) amount) * Math.max(0, cap / Math.max(1, most) - 1);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x, a = alpha[i] & 255;
            if (!mark[i] || a == 0) continue;
            int px = x0 + x, py = y0 + y;
            float blotch = .55f * noise(s + 1, px / 22f, py / 22f) + .3f * noise(s + 2, px / 7f, py / 7f) + .15f * unit(s, px, py, 3);
            float k = 1 + far * (float) StrokeMath.smoothstep(.1, .8, blotch);
            // Its darkness over white, k times, never past the darkest (nor lighter): its tone first, at the same coverage,
            // then (a grainy line's thin pixels) its coverage.
            float dark = p[i], to = Math.max(dark, Math.min(cap, dark * k));
            if (to <= a) tones[i] = (byte) Math.round(255 - to * 255 / a);
            else { tones[i] = 0; alpha[i] = (byte) Math.round(to); }
        }
    }

    // ---- shared ----

    /** Each pixel's distance, px (chamfer, within about 5%), to the nearest one where {@code in} is false; 0 there. Past the region counts as in. */
    static float[] inward(boolean[] in, int w, int h) {
        int n = w * h;
        float[] d = new float[n];
        final float big = 1e6f, r2 = 1.4142f;
        for (int i = 0; i < n; i++) d[i] = in[i] ? big : 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            if (d[i] == 0) continue;
            float v = d[i];
            if (x > 0) v = Math.min(v, d[i - 1] + 1);
            if (y > 0) { v = Math.min(v, d[i - w] + 1); if (x > 0) v = Math.min(v, d[i - w - 1] + r2); if (x < w - 1) v = Math.min(v, d[i - w + 1] + r2); }
            d[i] = v;
        }
        for (int y = h - 1; y >= 0; y--) for (int x = w - 1; x >= 0; x--) {
            int i = y * w + x;
            if (d[i] == 0) continue;
            float v = d[i];
            if (x < w - 1) v = Math.min(v, d[i + 1] + 1);
            if (y < h - 1) { v = Math.min(v, d[i + w] + 1); if (x < w - 1) v = Math.min(v, d[i + w + 1] + r2); if (x > 0) v = Math.min(v, d[i + w - 1] + r2); }
            d[i] = v;
        }
        return d;
    }
    /** The largest of {@code d} within {@code r} px (a square), each pixel: how wide the mark is near it. */
    private static float[] widest(float[] d, int w, int h, int r) {
        float[] a = new float[d.length], b = new float[d.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) { float m = 0; for (int k = Math.max(0, x - r); k <= Math.min(w - 1, x + r); k++) m = Math.max(m, d[y * w + k]); a[y * w + x] = m; }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) { float m = 0; for (int k = Math.max(0, y - r); k <= Math.min(h - 1, y + r); k++) m = Math.max(m, a[k * w + x]); b[y * w + x] = m; }
        return b;
    }
    /** A running box blur of radius {@code r} along rows ({@code rows}) or columns, {@code from} into {@code to}. */
    private static void box(float[] from, float[] to, int w, int h, int r, boolean rows) {
        int lines = rows ? h : w, len = rows ? w : h, step = rows ? 1 : w;
        float norm = 1f / (2 * r + 1);
        for (int l = 0; l < lines; l++) {
            int base = rows ? l * w : l;
            float sum = 0;
            for (int k = -r; k <= r; k++) sum += from[base + Math.max(0, Math.min(len - 1, k)) * step];
            for (int k = 0; k < len; k++) {
                to[base + k * step] = sum * norm;
                sum += from[base + Math.min(len - 1, k + r + 1) * step] - from[base + Math.max(0, k - r) * step];
            }
        }
    }

    // ---- noise ----

    /** 0 to 1, from the seed and three numbers (a page position and a salt). */
    static float unit(long seed, long a, long b, int c) {
        long h = seed * 0x9E3779B97F4A7C15L + a * 0xC2B2AE3D27D4EB4FL + b * 0x165667B19E3779F9L + c * 0x27D4EB2F165667C5L;
        h ^= h >>> 31; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29; h *= 0x94D049BB133111EBL; h ^= h >>> 32;
        return (h >>> 40) / (float) (1 << 24);
    }
    /** Smooth value noise, 0 to 1, on a unit lattice. */
    static float noise(long seed, float x, float y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        return lerp(lerp(unit(seed, ix, iy, 0), unit(seed, ix + 1, iy, 0), fx), lerp(unit(seed, ix, iy + 1, 0), unit(seed, ix + 1, iy + 1, 0), fx), fy);
    }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    /** About e^(-z²), quickly: (1 - z²/4)⁴, 0 past |z| = 2 (within .03 of it). */
    private static float bell(float z) { float t = 1 - z * z * .25f; if (t <= 0) return 0; t *= t; return t * t; }
}
