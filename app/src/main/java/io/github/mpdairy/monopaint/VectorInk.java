package io.github.mpdairy.monopaint;

/**
 * Vector Ink (docs/superpowers/specs/2026-11-04-vector-ink-design.md; the owner, 2026-11-04: "i just want to zoom in and
 * have it be clean"): a Basic tool whose lines stay sharp at every zoom. It draws on the layer in use, like any pen (the
 * owner, 2026-11-06: "Vector Ink follows the chosen layer"): on its pair's ink half, kept as lines ({@link VectorLines}
 * on the layer, {@link ToneDocument.Layer#lines}) and as their pixels at 100% on the half's own Vector Ink plane
 * ({@link ToneDocument#PLANE_LINES}, beside the other inks' marks), so everything that reads pixels (thumbnails, washes
 * grooving along the lines, Replay, Draw Together, PSD, older versions) reads them as any ink. The zoomed view draws
 * the lines again at its scale in their place in the page's order ({@link Stacked}). The eraser cuts them ({@link Cut}).
 * Pure, checked on the desktop ({@code VectorInkChecks}).
 */
final class VectorInk {
    private VectorInk() { }

    /**
     * The Basic tool's code: the Vector Ink medium ({@link PartChoice#VECTOR}) on Steady line's tip and hand, its pressure
     * to size .3 (0.8 of its size with no pressure, 1.2 pressed hard: a little thicker pressed).
     */
    static final String CODE = "vector-ink.M00il00000000at_8_0_000_0_8at0000000000000000nz0.2h0";
    /** The Basic tool's first code (2026-11-04, before the medium): Steady line's settings at its own seed; it reads as {@link #CODE} ({@code ModularCode}). */
    static final String FIRST = "vector-ink.M00il00000000at_000zz0000000000000_0_00r_1jz_84w0000000000000000nz0.2h0";
    /** Whether {@code brush} is Vector Ink: its medium is (any brush of it, a Brush Lab copy too). */
    static boolean is(Brush brush) { return brush != null && is(brush.parts); }
    static boolean is(ModularBrush parts) { return parts.choice(BrushPart.MEDIUM) == PartChoice.VECTOR; }

    /**
     * The parts Vector Ink sets aside, as Brush Lab shows them while it is the medium (no choices, no dials): what it
     * can't draw sharp. Tip keeps its Size (Round only), Control its pressure to size and Response; Medium has no dials.
     */
    static boolean setAside(BrushPart part) { return part == BrushPart.CARRY || part == BrushPart.PAPER || part == BrushPart.LIFE || part == BrushPart.LATER; }
    /**
     * {@code b} with what Vector Ink sets aside at its plain state, when its medium is Vector Ink (every brush's last
     * step, {@link ModularBrush#normalize}, so a roll, a swap or a pasted code never mixes them): a Round tip at its Size,
     * no smudge, Instant, no Later, no paper texture. Control is kept (only its pressure to size and Response draw).
     */
    static void tidy(ModularBrush b) {
        if (!is(b)) return;
        ModularBrush.Part tip = b.part(BrushPart.TIP), round = new ModularBrush.Part(PartChoice.ROUND);
        if (tip.has(PartDial.WEIGHT)) round.set(PartDial.WEIGHT, tip.get(PartDial.WEIGHT));
        if (!tip.sameAs(round)) b.set(BrushPart.TIP, round);
        if (b.choice(BrushPart.CARRY) != PartChoice.OFF) b.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
        if (b.choice(BrushPart.LIFE) != PartChoice.NONE) b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.NONE));
        if (b.choice(BrushPart.LATER) != PartChoice.LATER_OFF) b.set(BrushPart.LATER, new ModularBrush.Part(PartChoice.LATER_OFF));
        ModularBrush.Part paper = b.part(BrushPart.PAPER);
        if (paper.get(PartDial.TOOTH) != 0) paper.set(PartDial.TOOTH, 0);
    }
    /** How the pixel engine draws {@code b} (the Tools page's and Brush Lab's pictures, a fill): a plain Ink line of its tip and hand. */
    static ModularBrush drawnAs(ModularBrush b) {
        ModularBrush a = b.copy();
        a.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.INK));
        return a;
    }
    /** The pen speed at which {@link ResolvedBrush.Pen#width} neither widens nor thins, px/ms. */
    private static final double NEUTRAL = .75;

    /**
     * The notes (docs/TEXT-STYLE.md), said beside the tool ({@code PaintActivity.sayVector}): the layer in use's lines full;
     * a shape or Make Ink in it.
     */
    static final String FULL = "Vector ink full", NOT_IN = "Not in Vector ink";

    /** Where Vector Ink draws on {@code document}: the layer in use's ink half, as any pen ({@link InkWashLayers#inkOf}). */
    static int layer(ToneDocument document) { return InkWashLayers.inkOf(document, InkWashLayers.current(document)); }
    /** The page's layers holding Vector Ink's lines, as bits (layer n's bit n; 0 for none). */
    static int linedMask(ToneDocument document) {
        int mask = 0;
        for (int n = 0; n < document.layerCount(); n++) if (document.lines(n) != null) mask |= 1 << n;
        return mask;
    }
    /** Whether the layer in use can take no more Vector Ink: its lines at {@link VectorLines#MOST_POINTS}. */
    static boolean full(ToneDocument document) { VectorLines lines = document.lines(layer(document)); return lines != null && lines.points() >= VectorLines.MOST_POINTS; }
    /**
     * Whether the page has old in-page layers for Merge Old: more pairs than the three layers ({@link InkWashLayers#LAYERS}),
     * an old page's (in-page layers, or Vector Ink's own pair put on top before it followed the layer in use).
     */
    static boolean oldLayers(ToneDocument document) { return InkWashLayers.pairs(document) > InkWashLayers.LAYERS; }
    /** Why {@code brush} (null: the eraser) may not draw on {@code document} now, or null when it may: Vector Ink on a layer whose lines are full. */
    static String refusal(ToneDocument document, Brush brush) { return is(brush) && full(document) ? FULL : null; }
    /** Why a shape of {@code brush} or Make Ink with it may not be made, or null: none of Vector Ink. */
    static String shapeRefusal(ToneDocument document, Brush brush) { return is(brush) ? NOT_IN : null; }

    /**
     * The layers whose lines the zoomed view draws sharp, as bits (layer n's bit n; 0 for none): those holding lines
     * ({@link #linedMask}) that show in full (the layer at 100%, Show Kinds' Pen and tracing paper's step for its pair
     * Full); {@code layerSteps} as {@link ToneDocument#visibleLayers} takes them, {@code stroke} the pad's (as drawn): a
     * first Vector Ink stroke's layer counts. The view leaves their Vector Ink planes out of the page it scales and draws
     * their lines in their place ({@link Stacked}); a layer shown faded keeps its lines' pixels.
     */
    static int sharpLayers(ToneDocument document, int layerSteps, DrawingStroke stroke) {
        int mask = linedMask(document);
        if (stroke instanceof Pen && ((Pen) stroke).document == document) mask |= 1 << ((Pen) stroke).layer;
        if (document.kindOpacity(ToneDocument.PLANE_PEN) != 100 || NotebookOptions.GUIDE_STRENGTH[EraseKind.stepOf(layerSteps >>> ToneDocument.KIND_STEPS, ToneDocument.KIND_PEN)] != 100) return 0;
        for (int ink = 0; ink < document.layerCount(); ink++) {
            if ((mask >> ink & 1) == 0) continue;
            int pair = InkWashLayers.pair(ink);
            if (!document.layerVisible(ink) || document.layerOpacity(ink) != 100 || InkWashLayers.current(document) != pair && TracingPaper.layerStrength(layerSteps, pair) != 100) mask &= ~(1 << ink);
        }
        return mask;
    }
    /** What {@code stroke} (the pad's, as drawn) shows of the lines of the layers whose bits {@code layers} sets, bottom layer first: a Vector Ink stroke's with its line so far, an eraser's cut so far, else the layers'. */
    static VectorLines shown(ToneDocument document, int layers, DrawingStroke stroke) {
        VectorLines all = VectorLines.NONE;
        for (int ink = 0; ink < document.layerCount(); ink++) if ((layers >> ink & 1) != 0) { VectorLines own = shownOf(document, ink, stroke); if (own != null) all = all.with(own); }
        return all;
    }
    /** {@link #shown} of layer {@code index} alone. */
    static VectorLines shownOf(ToneDocument document, int index, DrawingStroke stroke) {
        if (stroke instanceof Pen && ((Pen) stroke).document == document && ((Pen) stroke).layer == index) return ((Pen) stroke).shown();
        if (stroke instanceof EraseBothStroke) { Cut cut = ((EraseBothStroke) stroke).cut(); if (cut != null && cut.document == document && (cut.mask() >> index & 1) != 0) return cut.lines(index); }
        return document.lines(index);
    }

    /**
     * The lines of the layers whose bits {@code layers} sets ({@link #sharpLayers}; {@code of} gives each one's) in their
     * place in {@code document}'s order as the screen shows it ({@code ghost}, {@code layerSteps}: {@link
     * ToneDocument#visibleLayers}'), and {@code over} (null: none) over everything: a Lasso piece floating. Null for none.
     */
    static Stacked stacked(ToneDocument document, int layers, int layerSteps, GhostPlane.Planes ghost, java.util.function.IntFunction<VectorLines> of, VectorLines over) {
        int size = ToneDocument.STACK + 3, k = 0;
        byte[][] tones = new byte[size][], alpha = new byte[size][];
        int[] opacity = new int[size], at = new int[ToneDocument.MAX_LAYERS];
        int count = document.visibleLayers(tones, alpha, opacity, ghost == null ? null : ghost.under, layerSteps, ghost == null ? null : ghost.overTones, ghost == null ? null : ghost.overAlpha, layers, at);
        VectorLines[] lines = new VectorLines[ToneDocument.MAX_LAYERS + 1];
        int[] under = new int[lines.length];
        // Bottom first: a higher layer's Vector Ink plane lies higher in the page's order.
        for (int n = 0; n < document.layerCount(); n++) {
            if ((layers >> n & 1) == 0 || !document.layerVisible(n)) continue;
            VectorLines own = of.apply(n);
            if (own != null && !own.isEmpty()) { lines[k] = own; under[k++] = at[n]; }
        }
        if (over != null && !over.isEmpty()) { lines[k] = over; under[k++] = count; }
        return k == 0 ? null : new Stacked(java.util.Arrays.copyOf(lines, k), java.util.Arrays.copyOf(under, k), tones, alpha, opacity, count, document.paperTone(), document.width, document.height);
    }
    /**
     * Vector Ink's lines in their place in the page's order (the zoomed view's, High Res's): each layer's lines ({@code
     * lines}, bottom first) over the first {@code under} of the page's composite places ({@code tones}, {@code alpha},
     * {@code opacity}, {@code count} of them on {@code paper}, the lines' own planes left out), so what lies over them (a
     * higher layer, opaque paint on their pair) covers them as the page shows. Where nothing lies over them, the lines go
     * over the target as it is (its sharp dry brushes' tiles kept).
     */
    static final class Stacked {
        private final VectorLines[] lines;
        private final int[] under, opacity;
        private final byte[][] tones, alpha;
        private final int count, paper, width, height;
        /** Each layer's lines' premultiplied greys and coverages, kept a thread's (the zoomed view makes a Stacked a frame). */
        private static final ThreadLocal<int[][][]> SCRATCH = new ThreadLocal<>();
        private int[][] greys, covers;
        private final int[] marked;
        Stacked(VectorLines[] lines, int[] under, byte[][] tones, byte[][] alpha, int[] opacity, int count, int paper, int width, int height) {
            this.lines = lines; this.under = under; this.tones = tones; this.alpha = alpha; this.opacity = opacity; this.count = count; this.paper = paper; this.width = width; this.height = height;
            marked = new int[count];
        }
        /** Whether some line's bounds meet page area {@code l, t, r, b}. */
        boolean meets(double l, double t, double r, double b) { for (VectorLines one : lines) if (one.meets(l, t, r, b)) return true; return false; }
        /**
         * The lines over {@code greys} as {@link VectorLines#over} draws them (the {@code w × h} target pixels at {@code left,
         * top}, page point (x, y) at ({@code x * scale + ox}, {@code y * scale + oy}), a grey in each low byte), each in its
         * place: a target pixel whose page pixel (as the zoomed view samples it) holds a mark over a line there is composed
         * again from the places with the lines in theirs. {@code coverage} is scratch, at least {@code w * h}.
         */
        void over(double scale, double ox, double oy, int left, int top, int w, int h, int[] greys, byte[] coverage) {
            int k = lines.length, n = w * h;
            int[][][] scratch = SCRATCH.get();
            if (scratch == null) SCRATCH.set(scratch = new int[2][0][]);
            if (scratch[0].length < k || scratch[0][0].length < n) { scratch[0] = new int[k][n]; scratch[1] = new int[k][n]; }
            this.greys = scratch[0]; covers = scratch[1];
            // Each layer's lines alone: premultiplied grey and coverage (over 0), so over v they show v × (255 − c) / 255 + g.
            for (int j = 0; j < k; j++) { java.util.Arrays.fill(this.greys[j], 0, n, 0); java.util.Arrays.fill(covers[j], 0, n, 0); lines[j].over(scale, ox, oy, left, top, w, h, this.greys[j], covers[j], coverage); }
            int last = -1, m = 0;
            for (int y = 0; y < h; y++) {
                int py = (int) Math.floor((top + y + .5 - oy) / scale);
                for (int x = 0, i = y * w; x < w; x++, i++) {
                    int lowest = Integer.MAX_VALUE;
                    for (int j = 0; j < k; j++) if (covers[j][i] != 0) { lowest = under[j]; break; }
                    if (lowest == Integer.MAX_VALUE) continue;
                    int px = (int) Math.floor((left + x + .5 - ox) / scale), p = py * width + px;
                    if (px < 0 || py < 0 || px >= width || py >= height) continue;
                    // The places marked at this page pixel, gathered once for the target pixels sampling it.
                    if (p != last) { last = p; m = 0; for (int q = 0; q < count; q++) if (alpha[q][p] != 0) marked[m++] = q; }
                    int v;
                    if (m == 0 || marked[m - 1] < lowest) {
                        // Nothing over the lines here: over the target as it is.
                        v = greys[i] & 255;
                        for (int j = 0; j < k; j++) v = line(v, j, i);
                    } else {
                        v = paper;
                        int j = 0;
                        for (int e = 0; e < m; e++) {
                            int q = marked[e];
                            for (; j < k && under[j] <= q; j++) v = line(v, j, i);
                            int a = ((alpha[q][p] & 255) * opacity[q] + 50) / 100;
                            v = ((tones[q][p] & 255) * a + v * (255 - a) + 127) / 255;
                        }
                        for (; j < k; j++) v = line(v, j, i);
                    }
                    greys[i] = greys[i] & ~255 | v;
                }
            }
        }
        /** Grey {@code v} under layer {@code j}'s lines at target pixel {@code i}. */
        private int line(int v, int j, int i) { int c = covers[j][i]; return c == 0 ? v : Math.min(255, (v * (255 - c) + 127) / 255 + greys[j][i]); }
    }

    /** {@code stroke}'s points {@code from} to {@code to} laid at 100% over the gesture's layer, each pixel its share of the outline. */
    private static void lay(ToneDocument document, VectorLines.Stroke stroke, int from, int to) {
        int[] b = VectorLines.bounds(stroke, from, to, 1, 0, 0);
        int l = Math.max(0, b[0]), t = Math.max(0, b[1]), r = Math.min(document.width, b[2]), d = Math.min(document.height, b[3]);
        if (l >= r || t >= d) return;
        int w = r - l, h = d - t;
        byte[] cover = new byte[w * h];
        VectorLines.cover(stroke, from, to, 1, 0, 0, l, t, w, h, cover);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) { int c = cover[y * w + x] & 255; if (c != 0) document.overTone(l + x, t + y, stroke.shade, c); }
    }

    /**
     * Vector Ink's stroke of brush {@code parts} at size {@code px} and grey: while the pen is down, the pen's
     * samples drawn as they come (the pad's fast path shows them as any stroke's); at the lift, taken back and drawn again
     * smoothed ({@link VectorLines#smoothed}), the line added to the layer's, one Undo step. It draws on the layer in
     * use's ink ({@link #layer}), its pixels on the half's Vector Ink plane. It goes on past its layer's cap (the next
     * stroke is refused: {@link #refusal}), up to {@link VectorLines#MOST_POINTS} samples.
     */
    static final class Pen implements DrawingStroke {
        final ToneDocument document;
        final int layer, shade;
        private final float size;
        /** Its Ink line's pen and size ({@link #drawnAs}): a sample's width as that pen sizes it (Tip Size, Control's pressure to size and Response; speed left out). */
        private final ResolvedBrush.Pen nib;
        private final double inkSize;
        private final long since;
        private final VectorLines before;
        private float[] xs = new float[64], ys = new float[64], ps = new float[64];
        private int n;
        Pen(ToneDocument document, ModularBrush parts, double px, double darkness) {
            this.document = document;
            layer = layer(document);
            shade = ToolSwatches.tone(darkness);
            ResolvedBrush r = ResolvedBrush.of(drawnAs(parts), Math.max(.5, px), 1);
            nib = r.pen; inkSize = r.inkSize;
            size = (float) Math.max(nib.width(inkSize, 0, NEUTRAL), nib.width(inkSize, 1, NEUTRAL));
            VectorLines had = document.lines(layer);
            before = had == null ? VectorLines.NONE : had;
            document.selectLayer(layer);
            since = document.top();
            document.beginPlane(ToneDocument.PLANE_LINES, true);
        }
        @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY) {
            if (!Float.isFinite(x) || !Float.isFinite(y)) return;
            if (n > 0 && Math.abs(x - xs[n - 1]) < .125f && Math.abs(y - ys[n - 1]) < .125f) return;
            // A stroke goes on past its layer's cap (the next starts a new layer, room); its own samples at most MOST_POINTS.
            if (n >= VectorLines.MOST_POINTS) return;
            if (n == xs.length) { xs = java.util.Arrays.copyOf(xs, 2 * n); ys = java.util.Arrays.copyOf(ys, 2 * n); ps = java.util.Arrays.copyOf(ps, 2 * n); }
            xs[n] = x; ys[n] = y; ps[n] = (float) (nib.width(inkSize, Float.isFinite(pressure) ? StrokeMath.clamp(pressure) : 0, NEUTRAL) / size); n++;
            // The newest piece of the rough line (where the last sample's round end overlaps it, its edge a shade darker until the lift).
            int from = Math.max(0, n - 2);
            lay(document, VectorLines.rough(shade, size, java.util.Arrays.copyOfRange(xs, from, n), java.util.Arrays.copyOfRange(ys, from, n), java.util.Arrays.copyOfRange(ps, from, n), n - from), 0, n - from - 1);
        }
        /** The layer's lines with this one so far, unsmoothed. */
        VectorLines shown() { return n == 0 ? before : before.with(VectorLines.rough(shade, size, xs, ys, ps, n)); }
        @Override public boolean finish() {
            document.cancel();
            VectorLines.Stroke line = VectorLines.smoothed(shade, size, xs, ys, ps, n);
            if (line == null) return false;
            document.beginPlane(ToneDocument.PLANE_LINES, true);
            lay(document, line, 0, line.points() - 1);
            document.finish();
            document.changeLines(layer, before.with(line), since);
            return true;
        }
    }

    /**
     * The eraser's cut through the page's Vector Ink lines ({@link EraseBothStroke}), on every shown layer as it erases: its path as
     * round dabs (each sample's, and between them every half radius), taken out together, when the zoomed view asks for
     * the lines or at the lift ({@link VectorLines#cut(java.util.List)}); {@link #done} draws the lines' pixels again where
     * it went and keeps the cut with the eraser's Undo step. Till then the screen shows those pixels gone where it went
     * ({@link EraseBothStroke#erasedTones}).
     */
    static final class Cut {
        final ToneDocument document;
        /** The Vector Ink layers it cuts: the shown ones ({@link #linedMask}). */
        private final int[] layers;
        private final long since;
        /** The page's last Undo step before the eraser's: its steps and the cut's after it go as one. */
        private final Object begun;
        private final VectorLines[] from, cut;
        /** How many dabs each layer's {@link #cut} has taken out ({@link #lines(int)}). */
        private final int[] applied;
        private final java.util.ArrayList<double[]> dabs = new java.util.ArrayList<>();
        /** Its path, sample to sample ({x0, y0, r0, x1, y1, r1}): the lift's last pass ({@link VectorLines#cutSwept}). */
        private final java.util.ArrayList<double[]> swept = new java.util.ArrayList<>();
        private double lastX = Double.NaN, lastY, lastR;
        private Cut(ToneDocument document, int[] layers) {
            this.document = document; this.layers = layers; since = document.top(); begun = document.lastStep();
            from = new VectorLines[layers.length]; cut = new VectorLines[layers.length]; applied = new int[layers.length];
            for (int k = 0; k < layers.length; k++) from[k] = cut[k] = document.lines(layers[k]);
        }
        /** The cut for an eraser of {@code kinds} ({@link ToneDocument#eraseKinds}, 0: everything), before it begins; null where no shown layer holds lines or it doesn't take Pen. */
        static Cut of(ToneDocument document, int kinds) {
            int[] lined = shownLined(document);
            return lined.length == 0 || kinds != 0 && (kinds & ToneDocument.KIND_PEN) == 0 ? null : new Cut(document, lined);
        }
        /** Its layers as bits. */
        int mask() { int mask = 0; for (int ink : layers) mask |= 1 << ink; return mask; }
        /** The eraser at page point {@code x, y}, {@code diameter} px across: its dabs kept, cut only when asked ({@link #lines(int)}, {@link #done}). */
        void to(double x, double y, double diameter) {
            double r = diameter / 2;
            if (!(r > 0)) return;
            swept.add(Double.isNaN(lastX) ? new double[]{x, y, r, x, y, r} : new double[]{lastX, lastY, lastR, x, y, r});
            if (Double.isNaN(lastX)) dabs.add(new double[]{x, y, r});
            else {
                // Every eighth of a radius: the dabs' union keeps within 0.2 % of its radius of the swept path's edge, so a line
                // along that edge isn't cut into a dotted row between dabs (the owner: "it leaves a bit on the screen").
                int steps = (int) Math.ceil(Math.hypot(x - lastX, y - lastY) / Math.max(.25, Math.min(r, lastR) / 8));
                for (int k = 1; k <= steps; k++) { double t = k / (double) steps; dabs.add(new double[]{lastX + (x - lastX) * t, lastY + (y - lastY) * t, lastR + (r - lastR) * t}); }
            }
            lastX = x; lastY = y; lastR = r;
        }
        /** Layer {@code layer}'s lines with every dab so far taken out (the zoomed view's, a frame at a time): the new dabs at once ({@link VectorLines#cut(java.util.List)}). */
        VectorLines lines(int layer) {
            for (int k = 0; k < layers.length; k++) if (layers[k] == layer) {
                if (applied[k] < dabs.size()) { cut[k] = cut[k].cut(dabs.subList(applied[k], dabs.size())); applied[k] = dabs.size(); }
                return cut[k];
            }
            return document.lines(layer);
        }
        /**
         * The tiles ({@link WashCoverage#TILE}) to draw again: those the eraser's pixels went over ({@code went}) and those a
         * dab reached, with the widest of {@code lines}' half widths round it: a line cut there lost pixels there too, where
         * the pixel eraser's footprint may not have gone (the cut's dabs and its footprint differ by a pixel or so).
         */
        private java.util.BitSet tiles(WashCoverage went, VectorLines lines) {
            int tile = WashCoverage.TILE, columns = (document.width + tile - 1) / tile, rows = (document.height + tile - 1) / tile;
            java.util.BitSet set = new java.util.BitSet(columns * rows);
            int[] b = went.bounds();
            if (b != null) went.forEachTile(b[0], b[1], b[2], b[3], (l, t, r, bt) -> set.set(t / tile * columns + l / tile));
            double reach = 2;
            for (int k = 0; k < lines.size(); k++) reach = Math.max(reach, lines.get(k).size / 2 + 2);
            for (double[] d : dabs) {
                int x0 = Math.max(0, (int) Math.floor((d[0] - d[2] - reach) / tile)), x1 = Math.min(columns - 1, (int) Math.floor((d[0] + d[2] + reach) / tile));
                int y0 = Math.max(0, (int) Math.floor((d[1] - d[2] - reach) / tile)), y1 = Math.min(rows - 1, (int) Math.floor((d[1] + d[2] + reach) / tile));
                for (int y = y0; y <= y1; y++) set.set(y * columns + x0, y * columns + x1 + 1);
            }
            return set;
        }
        /**
         * The eraser lifted, its own steps made ({@code changed}: any), {@code went} the pixels it went over: each layer's
         * lines cut (made again over the lines as they are now, should a mirrored copy have cut first), their pixels round
         * where it went (its tiles) cleared and drawn again from them ({@link #relay}), with its Undo step.
         */
        void done(boolean changed, WashCoverage went) {
            int was = document.activeLayer();
            boolean any = false;
            for (int k = 0; k < layers.length; k++) {
                int layer = layers[k];
                VectorLines now = document.lines(layer), next = now == from[k] ? lines(layer) : now == null ? null : now.cut(dabs);
                // The last pass: nothing left inside the path it swept, whatever the dabs missed.
                if (next != null) next = next.cutSwept(swept);
                if (next == now) continue;
                if (relay(document, layer, next, 0, 0, document.width, document.height, tiles(went, now))) { if (changed || any) document.finishWithPrevious(); else document.finish(); }
                document.changeLines(layer, next, since);
                any = true;
            }
            document.selectLayer(was);
            // Every layer's cut with the eraser's own steps: one Undo step.
            if (any) document.linkSince(begun);
        }
    }
    /** Layer {@code layer}'s lines made {@code next}, their pixels drawn again over {@code box} (page px), as a step joined to the one before when {@code joined}; whether any changed. */
    static boolean lines(ToneDocument document, int layer, VectorLines next, double[] box, boolean joined) {
        if (next == document.lines(layer)) return false;
        long since = document.top();
        if (relay(document, layer, next, (int) Math.floor(box[0]) - 1, (int) Math.floor(box[1]) - 1, (int) Math.ceil(box[2]) + 1, (int) Math.ceil(box[3]) + 1, null)) {
            if (joined) document.finishWithPrevious(); else document.finish();
        }
        document.changeLines(layer, next, since);
        return true;
    }
    /**
     * The shown Vector Ink layers' lines cut where a live page's change erased the page ({@code PictureImport#lay}'s
     * lighter, {@code w × h} at {@code x, y}, non-zero where it did: {@code own} on pair {@code pair}'s layers, {@code
     * rest} on the others'), as the eraser cuts them ({@link Cut}): each erased row's pixels a band, their pixels round
     * there drawn again from them, as steps of their own (the caller links them, {@link ToneDocument#linkSince}). Selects
     * the layers it cuts. Whether any changed.
     */
    static boolean cut(ToneDocument document, int x, int y, int w, int h, byte[] own, byte[] rest, int pair) {
        int[] lined = shownLined(document);
        boolean changed = false;
        for (int layer : lined) {
            VectorLines now = document.lines(layer);
            byte[] erased = InkWashLayers.pair(layer) == pair ? own : rest;
            java.util.ArrayList<double[]> rows = new java.util.ArrayList<>();
            for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
                if (erased[row * w + col] == 0) continue;
                int end = col;
                while (end + 1 < w && erased[row * w + end + 1] != 0) end++;
                // The run's pixels, edge to edge, as a band half a pixel each side of their row's middle.
                double mid = y + row + .5;
                rows.add(new double[]{x + col, mid, .5, x + end + 1, mid, .5});
                col = end;
            }
            if (rows.isEmpty()) continue;
            double reach = 2;
            for (int k = 0; k < now.size(); k++) reach = Math.max(reach, now.get(k).size / 2 + 2);
            changed |= lines(document, layer, now.cutSwept(rows), new double[]{x - reach, y - reach, x + w + reach, y + h + reach}, changed);
        }
        return changed;
    }
    /** The shown layers holding lines, bottom first. */
    private static int[] shownLined(ToneDocument document) {
        int mask = linedMask(document), n = 0;
        int[] out = new int[Integer.bitCount(mask)];
        for (int layer = 0; layer < document.layerCount(); layer++) if ((mask >> layer & 1) != 0 && document.layerVisible(layer)) out[n++] = layer;
        return java.util.Arrays.copyOf(out, n);
    }
    /**
     * Layer {@code layer}'s Vector Ink plane ({@link ToneDocument#PLANE_LINES}) in {@code l, t, r, b} (the tiles {@code
     * mask} sets, if one) cleared and drawn again from {@code lines}, a tile at a time, each stroke where it meets the
     * tile: the plane holds the lines' pixels alone. Begins a gesture on it, the layer selected, for the caller to finish;
     * true (the plane is made if missing).
     */
    static boolean relay(ToneDocument document, int layer, VectorLines lines, int l, int t, int r, int b, java.util.BitSet mask) {
        document.selectLayer(layer);
        if (!document.beginPlane(ToneDocument.PLANE_LINES, true)) return false;
        int tile = WashCoverage.TILE, columns = (document.width + tile - 1) / tile;
        byte[] cover = new byte[tile * tile];
        l = Math.max(0, l); t = Math.max(0, t); r = Math.min(document.width, r); b = Math.min(document.height, b);
        for (int y0 = t / tile * tile; y0 < b; y0 += tile) for (int x0 = l / tile * tile; x0 < r; x0 += tile) {
            int x = Math.max(l, x0), y = Math.max(t, y0), w = Math.min(r, x0 + tile) - x, h = Math.min(b, y0 + tile) - y;
            if (w <= 0 || h <= 0) continue;
            // A mask's tiles whole, past the area too.
            if (mask != null) { if (!mask.get(y0 / tile * columns + x0 / tile)) continue; x = x0; y = y0; w = Math.min(document.width, x0 + tile) - x0; h = Math.min(document.height, y0 + tile) - y0; }
            // The whole tile: a cut end's pixels past where the eraser went go too (the lines' pixels, laid in their order, as drawn).
            for (int row = 0; row < h; row++) document.paintSpan(x, x + w, y + row, ToneDocument.ERASE);
            for (int k = 0; k < lines.size(); k++) {
                VectorLines.Stroke s = lines.get(k);
                double[] box = s.bounds();
                if (box[0] > x + w || box[2] < x || box[1] > y + h || box[3] < y) continue;
                java.util.Arrays.fill(cover, 0, w * h, (byte) 0);
                VectorLines.cover(s, 1, 0, 0, x, y, w, h, cover);
                for (int i = 0; i < w * h; i++) if (cover[i] != 0) document.overTone(x + i % w, y + i / w, s.shade, cover[i] & 255);
            }
        }
        return true;
    }
}
