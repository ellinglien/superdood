package io.github.mpdairy.monopaint;

/**
 * Vector Ink (docs/superpowers/specs/2026-11-04-vector-ink-design.md; the owner, 2026-11-04: "i just want to zoom in and
 * have it be clean"): a Basic tool whose lines stay sharp at every zoom. It draws on the top pair's ink half, kept as
 * lines ({@link VectorLines} on the layer, {@link ToneDocument.Layer#lines}) and as their pixels at 100%, so everything
 * that reads pixels (thumbnails, washes grooving along the lines, Replay, Draw Together, PSD, older versions) reads them
 * as any ink. The zoomed view draws the lines again at its scale ({@code ViewportBitmap}). The eraser cuts them
 * ({@link Cut}). That half takes no other mark ({@link #refusal}). Pure, checked on the desktop ({@code VectorInkChecks}).
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
     * The notes (docs/TEXT-STYLE.md), said beside the tool ({@code PaintActivity.sayVector}): the Lasso, Paste or a picture
     * on its layer; no room for its layer (one made for it says nothing); the page's lines full; a shape or Make Ink in it.
     */
    static final String ONLY = "Vector ink only", LAYERS_FULL = "Layers full", FULL = "Vector ink full", NOT_IN = "Not in Vector ink";

    /** The selected pair's ink layer when it holds Vector Ink, else -1. */
    static int layer(ToneDocument document) {
        int pair = InkWashLayers.current(document), ink = InkWashLayers.inkOf(document, pair);
        return ink != InkWashLayers.washOf(document, pair) && document.lines(ink) != null ? ink : -1;
    }
    /**
     * The page's Vector Ink layers: the inks holding lines of the pairs on top, top first, down to the first pair whose ink
     * holds none (one filled, the next is made over it: {@link #room}). Empty for none.
     */
    static int[] linedLayers(ToneDocument document) {
        int[] out = new int[InkWashLayers.pairs(document)];
        int n = 0;
        for (int pair = InkWashLayers.pairs(document) - 1; pair >= 0; pair--) {
            int ink = InkWashLayers.inkOf(document, pair);
            if (ink == InkWashLayers.washOf(document, pair) || document.lines(ink) == null) break;
            out[n++] = ink;
        }
        return java.util.Arrays.copyOf(out, n);
    }
    /** The page's top Vector Ink layer ({@link #linedLayers}' first), else -1: where a Lasso piece's lines go. */
    static int lined(ToneDocument document) { int[] all = linedLayers(document); return all.length == 0 ? -1 : all[0]; }
    /**
     * Whether the page has old in-page layers for Merge Old: more than one pair besides Vector Ink's ({@link #linedLayers})
     * and the one put under them for other inks ({@link #under}). The pairs made for Vector Ink stay hidden.
     */
    static boolean oldLayers(ToneDocument document) { return InkWashLayers.pairs(document) - linedLayers(document).length > 1; }
    /** {@link #linedLayers} as bits, layer n's bit n. */
    static int linedMask(ToneDocument document) { int mask = 0; for (int ink : linedLayers(document)) mask |= 1 << ink; return mask; }
    /** {@link #room}'s answers besides a layer: a pair must be added on top for it; none can be. */
    static final int ADD = -1, NO_ROOM = -2;
    /**
     * Where Vector Ink draws on {@code document}: the top pair's ink when it holds lines short of {@link
     * VectorLines#MOST_POINTS} or nothing yet; else {@link #ADD} (a lower pair selected, ink with other marks, an old
     * one-layer page, the top's lines full: the owner, 2026-11-04, "after a long vector ink stroke just now i am unable to
     * make another mark"), a new pair to be put on top for it, or {@link #NO_ROOM} when the page has {@link
     * InkWashLayers#MAX_PAIRS} already. The owner, 2026-11-04: it just works.
     */
    static int room(ToneDocument document) {
        int top = InkWashLayers.pairs(document) - 1, ink = InkWashLayers.inkOf(document, top);
        VectorLines lines = document.lines(ink);
        if (ink != InkWashLayers.washOf(document, top) && (lines != null ? lines.points() < VectorLines.MOST_POINTS : !document.layerMarked(ink))) return ink;
        return InkWashLayers.pairs(document) < InkWashLayers.MAX_PAIRS ? ADD : NO_ROOM;
    }
    /**
     * Why {@code brush} (null: the eraser) may not draw on {@code document} now, or null when it may: Vector Ink with no
     * room for its pair ("Vector ink full" when the lines on top filled it, else "Layers full"); another ink on Vector Ink's
     * pairs with no room for one under them ({@link #under}). Nothing else is refused: each finds its place.
     */
    static String refusal(ToneDocument document, Brush brush) {
        if (brush == null) return null;
        int top = InkWashLayers.inkOf(document, InkWashLayers.pairs(document) - 1);
        if (is(brush)) return room(document) != NO_ROOM ? null : document.lines(top) != null ? FULL : LAYERS_FULL;
        int ink = layer(document);
        return ink >= 0 && ModularStroke.layerOf(document, brush.parts) == ink && lowest(document) < 0 && InkWashLayers.pairs(document) >= InkWashLayers.MAX_PAIRS ? LAYERS_FULL : null;
    }
    /** The highest pair under the selected one whose ink holds no lines, else -1. */
    private static int lowest(ToneDocument document) {
        for (int pair = InkWashLayers.current(document) - 1; pair >= 0; pair--) {
            int ink = InkWashLayers.inkOf(document, pair);
            if (document.lines(ink) == null) return pair;
        }
        return -1;
    }
    /**
     * Another ink on a Vector Ink pair goes on the first pair under it that holds no lines, a new one at the bottom when
     * there is none (one Undo step with the stroke, {@link ToneDocument#linkToNext}): Vector Ink's lines keep to their own
     * layers, on top. {@code brush} the stroke's; nothing for Vector Ink, a wash or the eraser.
     */
    static void under(ToneDocument document, Brush brush) {
        int ink = layer(document);
        if (ink < 0 || brush == null || is(brush) || ModularStroke.layerOf(document, brush.parts) != ink) return;
        below(document);
    }
    /**
     * As {@link #under}, for marks laid on the layer drawn on (a Draw Together guest's change, {@code LiveProbe}): when that
     * is Vector Ink's, they go on the pair under it.
     * shortcut: four pairs all holding lines leave them on the lowest's lines' layer (the host's pen is refused there), upgrade if a guest ever meets it.
     */
    static void under(ToneDocument document) {
        int ink = layer(document);
        if (ink >= 0 && document.activeLayer() == ink) below(document);
    }
    private static void below(ToneDocument document) {
        int pair = lowest(document);
        if (pair >= 0) InkWashLayers.select(document, pair);
        else { InkWashLayers.select(document, 0); if (InkWashLayers.add(document, false)) document.linkToNext(); }
    }
    /** Why a shape of {@code brush}, the Lasso, a placed picture or Make Ink ({@code brush} null) may not change the selected pair, or null. */
    static String shapeRefusal(ToneDocument document, Brush brush) {
        if (is(brush)) return NOT_IN;
        // Another brush's shape or Make Ink goes under Vector Ink's pair ({@link #under}).
        return brush == null && layer(document) >= 0 ? ONLY : null;
    }

    /**
     * The layers whose lines the zoomed view draws sharp over the page, as bits (layer n's bit n; 0 for none): the Vector
     * Ink layers ({@link #linedLayers}) that show in full (the layer and its Pen at 100%, Show Kinds' Pen and tracing
     * paper's step for its pair Full); {@code layerSteps} as {@link ToneDocument#visibleLayers} takes them, {@code stroke}
     * the pad's (as drawn): a first Vector Ink stroke's layer counts. The view leaves those layers out of the page it
     * scales and draws their lines in their place.
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
     * smoothed ({@link VectorLines#smoothed}), the line added to the layer's, one Undo step. It draws on the top pair's
     * ink ({@link #room}), a pair put on top for it first when that holds other marks (the same Undo step), then the pair
     * that was selected is selected again, so other tools and the eraser go on where they were. It goes on past its
     * layer's cap (the next stroke starts a layer of its own, {@link #room}), up to {@link VectorLines#MOST_POINTS} samples.
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
        /** The layer selected before it (-1: the pair it drew on stays selected); the Undo step before a pair added for it (null: none added). */
        private final int restore;
        private final Object added;
        Pen(ToneDocument document, ModularBrush parts, double px, double darkness) {
            this.document = document;
            int was = document.activeLayer(), ink = room(document);
            Object mark = document.lastStep();
            if (ink == ADD) { InkWashLayers.select(document, InkWashLayers.pairs(document) - 1); InkWashLayers.add(document, true); ink = InkWashLayers.inkOf(document, InkWashLayers.current(document)); }
            // No room (refused on the screens; a replay's copy): the selected pair's ink, as before.
            if (ink == NO_ROOM) ink = InkWashLayers.inkOf(document, InkWashLayers.current(document));
            added = ink == InkWashLayers.inkOf(document, InkWashLayers.current(document)) && document.lastStep() != mark ? (mark == null ? this : mark) : null;
            layer = ink;
            restore = InkWashLayers.pair(was) == InkWashLayers.pair(layer) ? -1 : was;
            shade = ToolSwatches.tone(darkness);
            ResolvedBrush r = ResolvedBrush.of(drawnAs(parts), Math.max(.5, px), 1);
            nib = r.pen; inkSize = r.inkSize;
            size = (float) Math.max(nib.width(inkSize, 0, NEUTRAL), nib.width(inkSize, 1, NEUTRAL));
            VectorLines had = document.lines(layer);
            before = had == null ? VectorLines.NONE : had;
            document.selectLayer(layer);
            since = document.top();
            document.begin(1, ToneDocument.KIND_PEN);
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
            if (line == null) {
                // Nothing drawn: the pair put on top for it goes again.
                if (added != null) document.undo();
                if (restore >= 0 && restore < document.layerCount()) document.selectLayer(restore);
                return false;
            }
            document.begin(1, ToneDocument.KIND_PEN);
            lay(document, line, 0, line.points() - 1);
            document.finish();
            document.changeLines(layer, before.with(line), since);
            // The pair added and the line: one Undo step.
            if (added != null) document.linkSince(added == this ? null : added);
            if (restore >= 0 && restore < document.layerCount()) document.selectLayer(restore);
            return true;
        }
    }

    /**
     * The eraser's cut through the page's Vector Ink lines ({@link EraseBothStroke}), whichever pair it erases: its path as
     * round dabs (each sample's, and between them every half radius), taken out together, when the zoomed view asks for
     * the lines or at the lift ({@link VectorLines#cut(java.util.List)}); {@link #done} draws the lines' pixels again where
     * it went and keeps the cut with the eraser's Undo step. Till then the screen shows those pixels gone where it went
     * ({@link EraseBothStroke#erasedTones}).
     */
    static final class Cut {
        final ToneDocument document;
        /** The Vector Ink layers it cuts ({@link #linedLayers}). */
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
        /** The cut for an eraser of {@code kinds} ({@link ToneDocument#eraseKinds}, 0: everything), before it begins; null where the page has no lines ({@link #linedLayers}) or it doesn't take Pen. */
        static Cut of(ToneDocument document, int kinds) {
            int[] lined = linedLayers(document);
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
     * lighter: {@code erased}, {@code w × h} at {@code x, y}, non-zero where it did), as the eraser cuts them ({@link Cut}):
     * each erased row's pixels a band, their pixels round there drawn again from them, as steps of their own
     * (the caller links them, {@link ToneDocument#linkSince}). Selects the layers it cuts. Whether any changed.
     */
    static boolean cut(ToneDocument document, int x, int y, int w, int h, byte[] erased) {
        int[] lined = linedLayers(document);
        if (lined.length == 0) return false;
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
        boolean changed = false;
        for (int layer : lined) {
            VectorLines now = document.lines(layer);
            if (!document.layerVisible(layer)) continue;
            double reach = 2;
            for (int k = 0; k < now.size(); k++) reach = Math.max(reach, now.get(k).size / 2 + 2);
            changed |= lines(document, layer, now.cutSwept(rows), new double[]{x - reach, y - reach, x + w + reach, y + h + reach}, changed);
        }
        return changed;
    }
    /**
     * Layer {@code layer}'s pixels in {@code l, t, r, b} (the tiles {@code mask} sets, if one) cleared and drawn again from
     * {@code lines}, a tile at a time, each stroke where it meets the tile: the layer holds the lines' pixels alone. Begins a
     * gesture on its Pen plane, selected, for the caller to finish; false (none begun) when the layer has no Pen plane.
     */
    static boolean relay(ToneDocument document, int layer, VectorLines lines, int l, int t, int r, int b, java.util.BitSet mask) {
        document.selectLayer(layer);
        if (!document.beginPlane(ToneDocument.PLANE_PEN, true)) return false;
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
