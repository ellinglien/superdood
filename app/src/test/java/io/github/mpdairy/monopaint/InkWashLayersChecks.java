package io.github.mpdairy.monopaint;

public final class InkWashLayersChecks {
    public static void main(String[] args) {
        ToneDocument doc = InkWashLayers.blank(16, 12);
        check(doc.layerCount() == 2 && doc.layerName(InkWashLayers.WASH).equals("Wash")
                && doc.layerName(InkWashLayers.INK).equals("Ink"), "Wash below, ink above");
        check(doc.activeLayer() == InkWashLayers.INK, "A new page starts on the ink layer");
        check(!doc.canUndo(), "The layers are not an undoable edit");
        for (int y = 0; y < 12; y++) for (int x = 0; x < 16; x++) check(doc.compositeTone(x, y) == 255, "Blank paper");
        // Ink is never covered by wash, whichever is drawn first.
        paint(doc, InkWashLayers.WASH, 3, 3, InkWashShades.WASH_DARK); paint(doc, InkWashLayers.INK, 3, 3, 0);
        paint(doc, InkWashLayers.INK, 5, 5, 0); paint(doc, InkWashLayers.WASH, 5, 5, InkWashShades.WASH_DARK);
        paint(doc, InkWashLayers.WASH, 7, 7, InkWashShades.WASH_DARK);
        check(doc.compositeTone(3, 3) == 0 && doc.compositeTone(5, 5) == 0, "Ink stays black over wash");
        check(Math.abs(doc.compositeTone(7, 7) - InkWashShades.WASH_DARK) <= 1, "Wash shows where there is no ink");
        pairs();
        threeLayers();
        layerSteps();
        merges();
        resized(doc);
        System.out.println("PASS: a new page is one pair, ink over wash in either order, blank paper; pair k is wash 2k and ink 2k+1, "
                + "the selected pair holds the active layer, one-layer and odd-count drawings fall back, grooves use the pair's own layers, the eraser every shown pair; "
                + "a resized page keeps what it holds, top-left anchored; "
                + "the layer button's three layers (a one- or two-pair page made three in one undo step, its drawing layer 2; three pairs selected; four the top three); "
                + "each layer's step Full, Half, Faint or Hidden its pair's visibility and opacity, both halves, one undo step each, a layer not made yet Full until a step makes it; "
                + "Merge Old Layers makes every pair one as the page shows it, hidden pairs kept, one undo step that restores them exactly");
    }
    /** Brush Lab's test page keeps what is drawn when its size changes: cut off or extended blank, top-left anchored. */
    private static void resized(ToneDocument doc) {
        ToneDocument smaller = InkWashLayers.resized(doc, 6, 6), larger = InkWashLayers.resized(doc, 20, 15);
        check(smaller.width == 6 && smaller.height == 6 && smaller.layerCount() == 2 && smaller.activeLayer() == doc.activeLayer(), "Resized: its size, layers and selection");
        check(smaller.compositeTone(3, 3) == 0 && smaller.compositeTone(5, 5) == 0, "Smaller: what was drawn stays where it was");
        check(larger.compositeTone(3, 3) == 0 && Math.abs(larger.compositeTone(7, 7) - InkWashShades.WASH_DARK) <= 1, "Larger: what was drawn stays too");
        check(larger.compositeTone(19, 14) == 255 && larger.compositeTone(16, 0) == 255 && !larger.canUndo(), "Larger: blank beyond, not an undoable edit");
    }
    private static void pairs() {
        ToneDocument doc = InkWashLayers.blank(16, 12);
        check(InkWashLayers.pairs(doc) == 1 && InkWashLayers.current(doc) == 0, "A new page is one pair, selected");
        check(InkWashLayers.MAX_PAIRS == 4, "Up to four pairs");
        java.util.ArrayList<ToneDocument.Layer> layers = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) layers.add(InkWashLayers.empty(i % 2 == 0 ? "Wash" : "Ink", 64, 64));
        ToneDocument three = new ToneDocument(64, 64, layers, 3);
        check(InkWashLayers.pairs(three) == 3, "Six layers are three pairs");
        for (int layer = 0; layer < 6; layer++) check(InkWashLayers.pair(layer) == layer / 2, "Layer " + layer + "'s pair");
        check(InkWashLayers.current(three) == 1, "The active layer's pair is selected");
        for (int pair = 0; pair < 3; pair++)
            check(InkWashLayers.washOf(three, pair) == 2 * pair && InkWashLayers.inkOf(three, pair) == 2 * pair + 1, "Pair " + pair + " is wash 2k, ink 2k+1");
        check(InkWashLayers.inkOver(three, 2) == 3 && InkWashLayers.inkOver(three, 4) == 5, "Each wash grooves under its own pair's ink");
        three.selectLayer(2);
        check(InkWashLayers.current(three) == 1, "Its wash selected, the pair stays the same");

        ToneDocument single = new ToneDocument(4, 4);
        check(InkWashLayers.pairs(single) == 1 && InkWashLayers.inkOf(single, 0) == 0 && InkWashLayers.washOf(single, 0) == 0
                && InkWashLayers.inkOver(single, 0) == -1, "A one-layer drawing from elsewhere takes everything on its only layer");
        java.util.ArrayList<ToneDocument.Layer> odd = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) odd.add(InkWashLayers.empty("Layer", 4, 4));
        ToneDocument triple = new ToneDocument(4, 4, odd, 2);
        check(InkWashLayers.pairs(triple) == 2 && InkWashLayers.current(triple) == 1 && InkWashLayers.washOf(triple, 1) == 2
                && InkWashLayers.inkOf(triple, 1) == 2 && InkWashLayers.inkOver(triple, 2) == -1, "An odd top layer is a pair of its own");

        // The eraser clears every pair the screen shows under it, both halves, as one undo step (the owner, 2026-11-05); a hidden pair keeps its marks.
        for (int layer = 0; layer < 6; layer++) paint(three, layer, 20, 20, layer % 2 == 0 ? InkWashShades.WASH_DARK : 0);
        InkWashLayers.show(three, 2, false);
        three.selectLayer(3);
        EraseBothStroke eraser = new EraseBothStroke(three, InkWashSizes.Preset.SMALL);
        eraser.sample(20, 20, .25f, 0, 0);
        check(eraser.finish(), "Erasing changes the page");
        check(three.activeLayer() == 3, "The eraser leaves the selection as it was");
        for (int layer = 0; layer < 6; layer++) check((alpha(three, layer, 20, 20) == 0) == (layer < 4), "Every shown pair is erased, the hidden one kept: layer " + layer);
        check(three.undo(), "Undo");
        for (int layer = 0; layer < 6; layer++) check(alpha(three, layer, 20, 20) != 0, "One undo restores every pair: layer " + layer);
        InkWashLayers.show(three, 2, true);
    }
    /** Merge Old Layers (the Layers panel, layers being pages): every pair made one as the page shows it, hidden ones too, one undo step. */
    private static void merges() {
        java.util.ArrayList<ToneDocument.Layer> layers = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) layers.add(InkWashLayers.empty(i % 2 == 0 ? "Wash" : "Ink", 8, 8));
        ToneDocument doc = new ToneDocument(8, 8, layers, 1);
        // Overlapping greys on every layer at x = y, a wash at 50 % opacity, and pair 2 hidden with a mark of its own at (7, 0).
        for (int layer = 0; layer < 6; layer++) for (int k = 0; k <= layer; k++) paint(doc, layer, k, k, 40 * layer);
        paint(doc, 4, 7, 0, 0);
        doc.setLayerOpacity(2, 50);
        check(InkWashLayers.show(doc, 2, false), "Pair 2 hidden");
        doc.selectLayer(3);
        int[][] before = shown(doc);
        int[] tones = new int[64];
        check(InkWashLayers.show(doc, 2, true), "(shown, to read the page with it)");
        for (int i = 0; i < 64; i++) tones[i] = doc.compositeTone(i % 8, i / 8);
        check(doc.undo(), "(hidden again)");
        check(!InkWashLayers.merge(InkWashLayers.blank(8, 8)), "One pair: nothing to merge");
        check(InkWashLayers.merge(doc), "Merge");
        check(InkWashLayers.pairs(doc) == 1 && doc.activeLayer() == InkWashLayers.INK && InkWashLayers.visible(doc, 0), "One pair, on its ink, shown");
        for (int i = 0; i < 64; i++) check(Math.abs(doc.compositeTone(i % 8, i / 8) - tones[i]) <= 1, "Each pixel as the page showed it, the hidden pair's marks kept: " + i);
        check(doc.compositeTone(7, 0) == 0, "The hidden pair's own mark shows: not lost");
        check(doc.undo() && java.util.Arrays.deepEquals(shown(doc), before) && !InkWashLayers.visible(doc, 2), "One undo brings the pairs back as they were, pair 2 hidden");
    }
    /** Every layer's alpha, in order, and the selected layer. */
    private static int[][] shown(ToneDocument doc) {
        int[][] out = new int[doc.layerCount() + 1][64];
        int active = doc.activeLayer();
        for (int layer = 0; layer < doc.layerCount(); layer++) for (int i = 0; i < 64; i++) out[layer][i] = alpha(doc, layer, i % 8, i / 8);
        doc.selectLayer(active);
        out[doc.layerCount()][0] = active;
        return out;
    }
    /** The layer button (the owner, 2026-11-06): layer 1 the bottom; a tap's third picks it; fewer than three pairs made three in one step. */
    /**
     * The layer button's hold (2026-11-06): a layer's step is its pair's visibility and opacity, Show Kinds' steps
     * (Full 100, Half 44, Faint 18, Hidden), both halves; another opacity the nearest step.
     */
    private static void layerSteps() {
        check(InkWashLayers.step(true, 100) == NotebookOptions.FULL && InkWashLayers.step(true, 44) == NotebookOptions.HALF
                && InkWashLayers.step(true, 18) == NotebookOptions.FAINT && InkWashLayers.step(false, 100) == NotebookOptions.HIDDEN
                && InkWashLayers.step(false, 44) == NotebookOptions.HIDDEN && InkWashLayers.step(true, 60) == NotebookOptions.HALF
                && InkWashLayers.step(true, 80) == NotebookOptions.FULL && InkWashLayers.step(true, 5) == NotebookOptions.FAINT,
                "Shown at 100, 44 and 18 is Full, Half and Faint; hidden is Hidden; another opacity the nearest");
        ToneDocument one = InkWashLayers.blank(8, 8);
        for (int layer = 1; layer <= InkWashLayers.LAYERS; layer++) check(InkWashLayers.stepOf(one, layer) == NotebookOptions.FULL, "A one-pair page's layers all Full, " + layer);
        check(!InkWashLayers.setStep(one, 2, NotebookOptions.FULL) && InkWashLayers.pairs(one) == 1 && !one.canUndo(), "Full again makes nothing");
        // Layer 1 not made yet: made first, as a tap makes it, then faded; the page still drawn on layer 2.
        check(InkWashLayers.setStep(one, 1, NotebookOptions.FAINT) && InkWashLayers.pairs(one) == 3 && InkWashLayers.layer(one) == 2
                && InkWashLayers.stepOf(one, 1) == NotebookOptions.FAINT && InkWashLayers.stepOf(one, 2) == NotebookOptions.FULL
                && one.layerOpacity(0) == 18 && one.layerOpacity(1) == 18 && one.layerVisible(0) && one.layerVisible(1), "Layer 1 made and Faint, both halves at 18, layer 2 still drawn on");
        check(InkWashLayers.setStep(one, 1, NotebookOptions.HIDDEN) && !one.layerVisible(0) && !one.layerVisible(1) && !InkWashLayers.visible(one, 0)
                && InkWashLayers.stepOf(one, 1) == NotebookOptions.HIDDEN, "Layer 1 hidden, both halves");
        check(one.undo() && InkWashLayers.stepOf(one, 1) == NotebookOptions.FAINT, "One Undo: Faint again");
        check(one.undo() && InkWashLayers.stepOf(one, 1) == NotebookOptions.FULL && InkWashLayers.pairs(one) == 3, "Undo: Full, the layers still made");
        check(one.undo() && InkWashLayers.pairs(one) == 1 && InkWashLayers.layer(one) == 2, "Undo: one pair again");
        check(InkWashLayers.setStep(one, 2, NotebookOptions.HALF) && InkWashLayers.pairs(one) == 1 && InkWashLayers.stepOf(one, 2) == NotebookOptions.HALF
                && one.layerOpacity(0) == 44 && one.layerOpacity(1) == 44, "The layer drawn on, made already: Half with no more pairs");
        check(InkWashLayers.setStep(one, 2, NotebookOptions.FULL) && one.layerOpacity(1) == 100 && InkWashLayers.setStep(one, 2, NotebookOptions.HIDDEN)
                && InkWashLayers.setStep(one, 2, NotebookOptions.FULL) && one.layerVisible(1) && one.layerOpacity(1) == 100, "Hidden back to Full: shown at 100");
        check(DrawLayout.layerStepNote(1, NotebookOptions.HALF).equals("Layer 1 half") && DrawLayout.layerHiddenNote(3).equals("Layer 3 is hidden")
                && DrawLayout.LAYER_VISIBILITY.equals("Layer Visibility"), "Its words: \"Layer 1 half\", \"Layer 3 is hidden\", Layer Visibility");
    }
    private static void threeLayers() {
        check(DrawLayout.layerAt(5, 0, 66) == 3 && DrawLayout.layerAt(33, 0, 66) == 2 && DrawLayout.layerAt(60, 0, 66) == 1
                && DrawLayout.layerAt(-4, 0, 66) == 3 && DrawLayout.layerAt(80, 0, 66) == 1 && DrawLayout.layerAt(103, 100, 22) == 3 && DrawLayout.layerAt(119, 100, 22) == 1, "The top third layer 3, the middle 2, the foot 1; past it the nearest");
        check(DrawLayout.layerSaid(2, false).equals("Layer 2 of 3") && DrawLayout.layerSaid(1, true).equals("Layer 1 of 3, hidden") && DrawLayout.layerNote(3).equals("Layer 3"), "Its words");
        // One pair: layer 2; the first switch adds a blank pair under and over it, one undo step.
        ToneDocument one = InkWashLayers.blank(8, 8);
        paint(one, InkWashLayers.INK, 2, 2, 0);
        check(InkWashLayers.layer(one) == 2 && !InkWashLayers.pickLayer(one, 2) && InkWashLayers.pairs(one) == 1, "One pair is layer 2; picking it again makes nothing");
        check(InkWashLayers.pickLayer(one, 3) && InkWashLayers.pairs(one) == 3 && InkWashLayers.layer(one) == 3 && InkWashLayers.current(one) == 2, "Layer 3 picked: three pairs, the top one drawn on");
        check(one.compositeTone(2, 2) == 0 && alpha(one, InkWashLayers.inkOf(one, 1), 2, 2) > 0, "The drawing is layer 2, the middle pair");
        check(one.undo() && InkWashLayers.pairs(one) == 1 && InkWashLayers.layer(one) == 2 && one.compositeTone(2, 2) == 0, "One Undo: one pair again, the drawing kept");
        check(one.redo() && InkWashLayers.pairs(one) == 3 && InkWashLayers.layer(one) == 3, "Redo: three again, on layer 3");
        // Two pairs (Vector Ink's: other inks under, its own on top): layers 2 and 3, a pair added under them.
        ToneDocument two = pages(4, 3);
        check(InkWashLayers.layer(two) == 3 && InkWashLayers.pickLayer(two, 1) && InkWashLayers.pairs(two) == 3 && InkWashLayers.current(two) == 0, "Two pairs are 2 and 3; layer 1 made under them");
        check(two.undo() && InkWashLayers.pairs(two) == 2 && two.activeLayer() == 3 && !two.canUndo(), "One Undo puts it back, selection too");
        // Three pairs: layer n is pair n - 1, a selection, no undo step.
        ToneDocument three = pages(6, 3);
        check(InkWashLayers.layer(three) == 2 && InkWashLayers.pickLayer(three, 1) && InkWashLayers.current(three) == 0 && InkWashLayers.layer(three) == 1
                && InkWashLayers.pickLayer(three, 3) && InkWashLayers.current(three) == 2 && !three.canUndo() && InkWashLayers.pairs(three) == 3, "Three pairs: picked, no undo step");
        // Four pairs (old pages): the top three are the layers, pair 0 left under them.
        ToneDocument four = pages(8, 1);
        check(InkWashLayers.layer(four) == 0 && InkWashLayers.pickLayer(four, 1) && InkWashLayers.current(four) == 1
                && InkWashLayers.pickLayer(four, 3) && InkWashLayers.current(four) == 3 && InkWashLayers.pairs(four) == 4 && !four.canUndo(), "Four pairs: layers 1 to 3 the top three pairs, nothing rewritten");
    }
    /** A page of {@code count} blank layers, {@code active} selected. */
    private static ToneDocument pages(int count, int active) {
        java.util.ArrayList<ToneDocument.Layer> layers = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) layers.add(InkWashLayers.empty(i % 2 == 0 ? "Wash" : "Ink", 8, 8));
        return new ToneDocument(8, 8, layers, active);
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    private static int alpha(ToneDocument doc, int layer, int x, int y) {
        int active = doc.activeLayer(); doc.selectLayer(layer);
        int a = doc.opacity(x, y); doc.selectLayer(active); return a;
    }
    private static void paint(ToneDocument doc, int layer, int x, int y, int tone) {
        doc.selectLayer(layer); doc.begin(); doc.setTone(x, y, tone); doc.finish();
    }
}
