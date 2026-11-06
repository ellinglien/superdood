package io.github.mpdairy.monopaint;

import java.util.ArrayList;

/**
 * Every Inkwash layer is a pair of engine layers: its wash below and its ink above, so ink is
 * never covered by its own wash whatever order they are drawn in, and a pair above holds both
 * over everything below. Pair {@code k} is wash {@code 2k} and ink {@code 2k + 1}. Each tool picks
 * its half of the selected pair, the one holding the active engine layer.
 *
 * <p>A drawing from elsewhere may not pair up: a one-layer MonoPaint drawing is one pair whose
 * halves are that layer, and in an odd count the top pair's ink is its wash.
 */
final class InkWashLayers {
    /** Pair 0's halves: a new page's only layer. */
    static final int WASH = 0, INK = 1;
    /** Up to four pairs: the engine's eight layers. */
    static final int MAX_PAIRS = ToneDocument.MAX_LAYERS / 2;

    /** A blank page with one pair, starting on its ink; not an undoable edit. */
    static ToneDocument blank(int width, int height) {
        ArrayList<ToneDocument.Layer> layers = new ArrayList<>();
        layers.add(empty("Wash", width, height));
        layers.add(empty("Ink", width, height));
        return new ToneDocument(width, height, layers, INK);
    }

    /** How many pairs the page has: an odd last layer is a pair of its own. */
    static int pairs(ToneDocument document) { return (document.layerCount() + 1) / 2; }
    /** The pair engine layer {@code layer} belongs to. */
    static int pair(int layer) { return layer / 2; }
    /** The selected pair: the one holding the active layer. */
    static int current(ToneDocument document) { return pair(document.activeLayer()); }
    /** Pair {@code pair}'s wash layer. */
    static int washOf(ToneDocument document, int pair) { return Math.min(2 * pair, document.layerCount() - 1); }
    /** Pair {@code pair}'s ink layer; its wash where the page has no separate one. */
    static int inkOf(ToneDocument document, int pair) { return Math.min(2 * pair + 1, document.layerCount() - 1); }

    /** The ink layer paired with wash layer {@code wash}, whose lines groove its watercolor; -1 when the page has no separate one. */
    static int inkOver(ToneDocument document, int wash) {
        int ink = inkOf(document, pair(wash));
        return ink == wash ? -1 : ink;
    }

    /** Whether pair {@code pair} shows: either half shown. */
    static boolean visible(ToneDocument document, int pair) {
        return document.layerVisible(washOf(document, pair)) || document.layerVisible(inkOf(document, pair));
    }
    /** Selects pair {@code pair}, on its ink. */
    static void select(ToneDocument document, int pair) { document.selectLayer(inkOf(document, pair)); }

    // Each change below is one undoable step, which undo restores exactly, selection included.

    /** Adds a blank pair just above or below the selected one and selects it; false at {@link #MAX_PAIRS}. */
    static boolean add(ToneDocument document, boolean above) {
        if (pairs(document) >= MAX_PAIRS) return false;
        Stack stack = new Stack(document);
        int at = 2 * (current(document) + (above ? 1 : 0));
        stack.order.add(at, -1); stack.order.add(at + 1, -1);
        return stack.apply(at + 1);
    }
    /** Removes pair {@code pair}, selecting the one that takes its place, or the one below the top; false for the last pair. */
    static boolean remove(ToneDocument document, int pair) {
        if (pairs(document) <= 1 || pair < 0 || pair >= pairs(document)) return false;
        Stack stack = new Stack(document);
        stack.order.remove(2 * pair); stack.order.remove(2 * pair);
        return stack.apply(2 * Math.min(pair, stack.order.size() / 2 - 1) + 1);
    }
    /** Moves pair {@code pair} one higher in the stack, or lower, keeping it selected; false at the top or bottom. */
    static boolean move(ToneDocument document, int pair, boolean higher) { return moveTo(document, pair, pair + (higher ? 1 : -1)); }
    /** Moves pair {@code pair} to place {@code to}, the pairs between shifting one place, keeping it selected; false when it is there or either is outside. */
    static boolean moveTo(ToneDocument document, int pair, int to) {
        if (pair < 0 || to < 0 || pair >= pairs(document) || to >= pairs(document) || pair == to) return false;
        Stack stack = new Stack(document);
        int wash = stack.order.remove(2 * pair), ink = stack.order.remove(2 * pair);
        stack.order.add(2 * to, ink); stack.order.add(2 * to, wash);
        return stack.apply(2 * to + 1);
    }
    /**
     * Merge Old Layers (the Layers panel, layers being pages): a page drawn with more than one pair becomes one, its ink
     * every layer flattened bottom to top as the page shows them (MonoPaint's opacity folded in), its wash blank, as one
     * undoable step. Hidden layers are merged too, and so show: nothing is lost. False with one pair.
     */
    static boolean merge(ToneDocument document) { return pairs(document) > 1 && mergeAbove(document, null); }
    /**
     * Merge Down (the side column's layers, a layer being a page stacked on the one below): {@code document}'s layers and
     * then {@code above}'s (the layer stacked on it; null: none) flattened as {@link #merge(ToneDocument)} does, into
     * {@code document} as one undoable step. Hidden layers merge too. Logged for Record and Replay ({@link StrokeLog.Merge}:
     * the layer above kept flattened).
     */
    static boolean merge(ToneDocument document, ToneDocument.Snapshot above) {
        boolean fits = above != null && above.width == document.width && above.height == document.height;
        try { return mergeAbove(document, fits ? above.layers : null); }
        finally { if (above != null) above.release(); }
    }
    /** As {@link #merge(ToneDocument, ToneDocument.Snapshot)}, the layers stacked above being {@code above} (null: none): a replay merges the one it kept. */
    static boolean mergeAbove(ToneDocument document, java.util.List<ToneDocument.Layer> above) {
        StrokeLog.Merge logged = StrokeLog.Merge.of(document, above == null ? null : flattened(above, document.width, document.height));
        ToneDocument.Snapshot was = document.layerSnapshot();
        java.util.List<ToneDocument.Layer> all = new ArrayList<>(was.layers);
        if (above != null) all.addAll(above);
        ToneDocument.Layer ink = flattened(all, was.width, was.height);
        was.release();
        java.util.ArrayList<ToneDocument.Layer> one = new java.util.ArrayList<>();
        one.add(empty("Wash", was.width, was.height)); one.add(ink);
        boolean changed = document.replaceLayers(one, INK);
        document.logMade(StrokeLog.Entry.merge(0, logged), changed);
        return changed;
    }
    /** {@code all} ({@code width × height}, bottom first) flattened as the page shows them into one layer, hidden ones too. */
    private static ToneDocument.Layer flattened(java.util.List<ToneDocument.Layer> all, int width, int height) {
        int n = width * height;
        ToneDocument.Layer ink = empty("Ink", width, height);
        byte kinds = 0;
        // Inkwash: every layer's base and kind planes, in the page's order (ToneDocument.stacked).
        int places = ToneDocument.stackSize(all.size());
        ToneDocument.Layer[] entries = new ToneDocument.Layer[places];
        int[] opacity = new int[places];
        for (int k = 0; k < places; k++) {
            ToneDocument.Layer entry = ToneDocument.stacked(all, k);
            if (entry == null) continue;
            entries[k] = entry; opacity[k] = ToneDocument.stackOpacity(all.get(ToneDocument.stackOwner(k)), entry);
            if (entry.covers() || entry == all.get(ToneDocument.stackOwner(k))) kinds |= entry.kinds;
        }
        for (int i = 0; i < n; i++) {
            // Source-over without paper: coverage a and premultiplied tone p, both 0 to 1.
            double a = 0, p = 0;
            for (int k = 0; k < places; k++) {
                ToneDocument.Layer layer = entries[k];
                if (layer == null) continue;
                double c = (layer.alpha[i] & 255) * opacity[k] / 100.0 / 255;
                p = (layer.tones[i] & 255) / 255.0 * c + p * (1 - c);
                a = c + a * (1 - c);
            }
            if (a <= 0) continue;
            ink.alpha[i] = (byte) Math.round(a * 255);
            ink.tones[i] = (byte) Math.min(255, Math.round(p / a * 255));
        }
        // shortcut: what the eraser may take (share), the print tone and the kinds' plane start afresh (all erasable, every
        // kind laid); refine if Merge is used on pages whose permanent paint matters.
        ink.kinds = kinds;
        return ink;
    }
    /** Shows or hides pair {@code pair}, both halves, leaving the selection; false when it already is. */
    static boolean show(ToneDocument document, int pair, boolean visible) {
        return document.setLayersVisible(washOf(document, pair), inkOf(document, pair), visible);
    }
    /**
     * The three layers (the owner's layer button, 2026-11-06: "tap on the layer area to switch to another layer"), 1 the
     * bottom: three pairs, made on the first switch, so a page nobody switches stays one pair, its layer 2.
     */
    static final int LAYERS = 3;
    /**
     * The layer in use, 1 to 3: a one-pair page is layer 2, a two-pair page layers 2 and 3; on four pairs (old pages, left
     * as they are) the top three, 0 the pair under them.
     */
    static int layer(ToneDocument document) { return layerOfPair(document, current(document)); }
    /** Pair {@code pair}'s layer, as {@link #layer} numbers them. */
    static int layerOfPair(ToneDocument document, int pair) {
        int pairs = pairs(document);
        return Math.max(0, pair + (pairs < LAYERS ? 2 : LAYERS + 1 - pairs));
    }
    /**
     * Layer {@code layer}'s pair ({@link #layer}'s inverse): on a page of three pairs or more the top three's; on fewer, a
     * pair the page has yet to make is outside 0 to {@link #pairs} - 1.
     */
    static int pairOfLayer(ToneDocument document, int layer) {
        int pairs = pairs(document);
        return layer - (pairs < LAYERS ? 2 : LAYERS + 1 - pairs);
    }
    /**
     * Picks layer {@code layer} (1 to 3); false when it is in use. On a page of three pairs or more the pair is selected,
     * no undo step; on fewer, the blank pairs come first, under it and over a one-pair page (the drawing stays layer 2),
     * the layer picked with them as one undoable step.
     */
    static boolean pickLayer(ToneDocument document, int layer) {
        if (layer < 1 || layer > LAYERS || layer(document) == layer) return false;
        if (pairs(document) >= LAYERS) { select(document, pairOfLayer(document, layer)); return true; }
        return make(document, layer);
    }
    /**
     * Draw Together: {@code action} (another screen's change laid, {@code PictureImport.lay}) on layer {@code layer} (1 to
     * 3, the one it was drawn on, {@code LiveChange.layer}): a page without it made three pairs first, as the first switch
     * makes them ({@link #make}, its own Undo step, the layer in use kept); every layer shown meanwhile, a layer's step
     * being each screen's own ({@link ToneDocument#withEveryLayer}); then the layer in use picked again, no step.
     */
    static void drawOn(ToneDocument document, int layer, Runnable action) {
        int mine = layer(document), pair = pairOfLayer(document, layer);
        if (pair < 0 || pair >= pairs(document)) { make(document, Math.max(1, mine)); pair = pairOfLayer(document, layer); }
        int on = pair;
        document.withEveryLayer(() -> {
            select(document, on);
            try { action.run(); } finally { select(document, pairOfLayer(document, mine)); }
        });
    }
    /** A page of fewer than three pairs made three, the blank ones under it and over a one-pair page, layer {@code layer} selected: one undoable step. */
    private static boolean make(ToneDocument document, int layer) {
        int pairs = pairs(document);
        Stack stack = new Stack(document);
        for (int i = 0; i < 2; i++) stack.order.add(0, -1);
        if (pairs == 1) { stack.order.add(-1); stack.order.add(-1); }
        return stack.apply(2 * (layer - 1) + 1);
    }
    /**
     * A layer's step (the owner, 2026-11-06, the layer button's hold: "long press should bring up the visibility options"):
     * Show Kinds' Full, Half, Faint and Hidden ({@link NotebookOptions#GUIDE_STEPS}) as its pair's own visibility and
     * opacity, both halves ({@link NotebookOptions#GUIDE_STRENGTH}: 100, 44, 18), kept with the page. A pair shown at
     * another opacity (an old page's) takes the nearest step.
     */
    static int step(boolean visible, int opacity) {
        if (!visible) return NotebookOptions.HIDDEN;
        int best = NotebookOptions.FULL;
        for (int s = NotebookOptions.FULL; s < NotebookOptions.HIDDEN; s++)
            if (Math.abs(opacity - NotebookOptions.GUIDE_STRENGTH[s]) < Math.abs(opacity - NotebookOptions.GUIDE_STRENGTH[best])) best = s;
        return best;
    }
    /** Layer {@code layer}'s step (1 to 3); Full for one the page has yet to make. */
    static int stepOf(ToneDocument document, int layer) {
        int pair = pairOfLayer(document, layer);
        if (pair < 0 || pair >= pairs(document)) return NotebookOptions.FULL;
        return step(visible(document, pair), document.layerOpacity(inkOf(document, pair)));
    }
    /**
     * Layer {@code layer} (1 to 3) shown at {@code step}, both halves of its pair, one undoable step, the layer drawn on
     * kept; false when it already is. A layer the page has yet to make is made first, as a tap makes it (its own undo step).
     */
    static boolean setStep(ToneDocument document, int layer, int step) {
        if (layer < 1 || layer > LAYERS || step < NotebookOptions.FULL || step > NotebookOptions.HIDDEN || stepOf(document, layer) == step) return false;
        int pair = pairOfLayer(document, layer);
        if (pair < 0 || pair >= pairs(document)) { make(document, layer(document)); pair = pairOfLayer(document, layer); }
        return document.setLayersShown(washOf(document, pair), inkOf(document, pair), step != NotebookOptions.HIDDEN,
                step == NotebookOptions.HIDDEN ? -1 : NotebookOptions.GUIDE_STRENGTH[step]);
    }
    /** Drawing on a hidden pair shows it: true if it was hidden, wholly or in half. */
    static boolean showSelected(ToneDocument document) {
        int pair = current(document);
        return !(document.layerVisible(washOf(document, pair)) && document.layerVisible(inkOf(document, pair))) && show(document, pair, true);
    }

    /**
     * The page's layers as a list of their indices to rearrange: an odd count first gains a blank ink
     * over its top layer, so every pair is whole and the pairing rule still holds after the change.
     */
    private static final class Stack {
        final ToneDocument document;
        final ArrayList<Integer> order = new ArrayList<>();
        Stack(ToneDocument document) {
            this.document = document;
            for (int i = 0; i < document.layerCount(); i++) order.add(i);
            if (order.size() % 2 == 1) order.add(-1);
        }
        /** Applies the order, naming new layers by their half, and selects {@code active}. */
        boolean apply(int active) {
            int[] indices = new int[order.size()];
            String[] names = new String[indices.length];
            for (int i = 0; i < indices.length; i++) { indices[i] = order.get(i); names[i] = i % 2 == 0 ? "Wash" : "Ink"; }
            return document.restack(indices, names, active);
        }
    }

    /**
     * A {@code width × height} page holding what {@code page} holds, its layers and its selected layer, anchored at the
     * top left: cut off where the new page is smaller, blank where it is larger. Brush Lab's test page keeps what is
     * drawn when its size changes. Not an undoable edit.
     */
    static ToneDocument resized(ToneDocument page, int width, int height) { return resized(page.layerSnapshot(), width, height, 0, 0); }
    /**
     * As {@link #resized(ToneDocument, int, int)}, {@code was}'s pixel ({@code left}, {@code top}) at the new page's top
     * left: what lies left of or above it is cut off (a notebook's old drawing losing a blank strip, NotebookMigration).
     */
    static ToneDocument resized(ToneDocument.Snapshot was, int width, int height, int left, int top) {
        ArrayList<ToneDocument.Layer> layers = new ArrayList<>();
        for (ToneDocument.Layer layer : was.layers) layers.add(cropped(layer, was.width, was.height, width, height, left, top));
        // Masking fluid and wax (Resist) go with it, and the paper's shade.
        ToneDocument page = new ToneDocument(width, height, layers, was.active, was.mask == null ? null : cropped(was.mask, was.width, was.height, width, height, left, top));
        page.placePaper(was.paper);
        return page;
    }
    /** {@code layer} ({@code fromWidth × fromHeight}) on a {@code width × height} page, its pixel ({@code left}, {@code top}) at the top left; its kind planes too. */
    private static ToneDocument.Layer cropped(ToneDocument.Layer layer, int fromWidth, int fromHeight, int width, int height, int left, int top) {
        return layer.each(l -> croppedOne(l, fromWidth, fromHeight, width, height, left, top));
    }
    private static ToneDocument.Layer croppedOne(ToneDocument.Layer layer, int fromWidth, int fromHeight, int width, int height, int left, int top) {
        ToneDocument.Layer copy = empty(layer.name, width, height);
        copy.visible = layer.visible; copy.opacity = layer.opacity;
        // Erasable: what the eraser can't take goes with the paint (paper's share, 255, elsewhere).
        if (layer.share != null) copy.share = ToneDocument.paper(width, height);
        // So does the print tone a screened brush builds on (none, 255, elsewhere).
        if (layer.print != null) copy.print = ToneDocument.paper(width, height);
        // And the kinds of mark it holds (0 elsewhere).
        copy.kinds = layer.kinds; copy.kindBase = layer.kindBase;
        if (layer.kind != null) copy.kind = new byte[width * height];
        int w = Math.min(width, fromWidth - left), h = Math.min(height, fromHeight - top);
        for (int y = 0; y < h; y++) {
            int from = (y + top) * fromWidth + left, to = y * width;
            System.arraycopy(layer.tones, from, copy.tones, to, w);
            System.arraycopy(layer.alpha, from, copy.alpha, to, w);
            if (layer.share != null) System.arraycopy(layer.share, from, copy.share, to, w);
            if (layer.print != null) System.arraycopy(layer.print, from, copy.print, to, w);
            if (layer.kind != null) System.arraycopy(layer.kind, from, copy.kind, to, w);
        }
        return copy;
    }

    static ToneDocument.Layer empty(String name, int width, int height) {
        return new ToneDocument.Layer(name, true, ToneDocument.paper(width, height), new byte[width * height]);
    }

    private InkWashLayers() {}
}
