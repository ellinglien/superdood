package io.github.mpdairy.monopaint;

/**
 * The paper grain six-part brushes read ({@link StrokePaper}): a fine noise (about 2 px) and a mid noise (about 6
 * px), each 0 to 255 and fixed to the page. {@link #PLAIN} is the app's paper, {@link PaperNoise}'s FINE and MID. A
 * surface (see {@link BrushTexture}) replaces both with its own, {@link SurfaceGrain#SIDE} square (2026-11-01: wider
 * than a page, so a page shows no repeat), made by {@link SurfaceGrain}; a surface with fibres keeps where they lie
 * ({@link #fibre}). {@link #scan} is the 256-pixel scan as the Brush Lab mockup's useTexture reads it: fine the scan
 * itself, mid the scan at three times the scale, smoothed (bilinear). Pure, checked on the desktop.
 */
final class PaperGrain {
    /** A scan's side and the plain fine table's: a power of two, so a page position masks into it. */
    static final int SIDE = PaperNoise.GRAIN_SIDE;
    /** How much larger a scan's mid noise is than its fine. */
    static final int MID_SCALE = 3;
    /**
     * How far tooth and wobble widen the plain paper's noise about its middle: its noises are two octaves
     * stretched to 0–255, so they bunch nearer the middle than the roll tester's one-octave value noise
     * (tooth's blend has a standard deviation of .13 against the tester's .16), which left edges half as
     * ragged. A scan is used as it is, as the mockup uses it.
     */
    static final float PLAIN_SPREAD = 1.22f;
    static final PaperGrain PLAIN = new PaperGrain(PaperNoise.FINE, PaperNoise.MID, SIDE, PLAIN_SPREAD);

    /** {@link #fineSide} square (a power of two), row by row. */
    final byte[] fine;
    final int fineSide;
    /** {@link #midSide} square, row by row. */
    final byte[] mid;
    final int midSide;
    /** How far {@link #widened} spreads this grain's noise about its middle. */
    final float spread;
    /**
     * Where the surface's fibres lie, {@link #fineSide} square: 0 where none, else the way the fibre runs, 1 + 254 ×
     * angle / π (a fibre runs both ways); null for a surface with no fibres to map ({@link #fibreAngle}).
     */
    final byte[] fibre;

    private PaperGrain(byte[] fine, byte[] mid, int midSide, float spread) { this(fine, SIDE, mid, midSide, spread, null); }
    PaperGrain(byte[] fine, int fineSide, byte[] mid, int midSide, float spread, byte[] fibre) {
        if (Integer.bitCount(fineSide) != 1 || fine.length != fineSide * fineSide || mid.length != midSide * midSide || fibre != null && fibre.length != fine.length)
            throw new IllegalArgumentException("A grain is square, its fine side a power of two");
        this.fine = fine; this.fineSide = fineSide; this.mid = mid; this.midSide = midSide; this.spread = spread; this.fibre = fibre;
    }

    /** The grain of a {@link #SIDE}-square scan, values 0 to 255 row by row. */
    static PaperGrain scan(byte[] scan) {
        if (scan.length != SIDE * SIDE) throw new IllegalArgumentException("A texture is " + SIDE + " pixels square, not " + scan.length + " bytes");
        int side = SIDE * MID_SCALE;
        byte[] mid = new byte[side * side];
        for (int y = 0; y < side; y++) {
            int iy = y / MID_SCALE;
            float ty = (float)(y % MID_SCALE) / MID_SCALE;
            for (int x = 0; x < side; x++) {
                int ix = x / MID_SCALE;
                float tx = (float)(x % MID_SCALE) / MID_SCALE;
                float top = mix(at(scan, ix, iy), at(scan, ix + 1, iy), tx), bottom = mix(at(scan, ix, iy + 1), at(scan, ix + 1, iy + 1), tx);
                mid[y * side + x] = (byte)Math.round(mix(top, bottom, ty));
            }
        }
        return new PaperGrain(scan, mid, side, 1);
    }

    int fine(int x, int y) { return fine[(y & fineSide - 1) * fineSide + (x & fineSide - 1)] & 255; }
    /** Where page row {@code y} starts in {@link #mid}. */
    int midRow(int y) { return Math.floorMod(y, midSide) * midSide; }
    int mid(int x, int y) { return mid[midRow(y) + Math.floorMod(x, midSide)] & 255; }

    /** The way the fibre at page pixel {@code x, y} runs, radians 0 to π; NaN where none lies or the surface has none mapped. */
    float fibreAngle(int x, int y) {
        if (fibre == null) return Float.NaN;
        int v = fibre[(y & fineSide - 1) * fineSide + (x & fineSide - 1)] & 255;
        return v == 0 ? Float.NaN : (v - 1) * (float) Math.PI / 254;
    }

    /** A noise, 0 to 1, widened about its middle by {@link #spread}, kept within 0 to 1. */
    float widened(float noise) { float v = .5f + spread * (noise - .5f); return v < 0 ? 0 : v > 1 ? 1 : v; }

    private static int at(byte[] scan, int x, int y) { return scan[(y & SIDE - 1) * SIDE + (x & SIDE - 1)] & 255; }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
