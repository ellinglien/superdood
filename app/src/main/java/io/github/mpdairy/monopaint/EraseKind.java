package io.github.mpdairy.monopaint;

/**
 * What the eraser takes: a long press on its tile offers Everything (every mark, whole, permanent ink too: the owner,
 * 2026-10-22) or one of the three layers, every mark on it and none on the others (the owner, 2026-11-08: "rename them
 * just layers 1 etc instead of pen paint etc"; Pen, Paint and Dry Media from 2026-10-20 until then). What it takes is
 * one int, its target ({@link #TARGETS}): {@link ToneDocument#KIND_PEN} bits, which Show Kinds limits ({@link #takes}),
 * and above them the layer, 1 to 3 ({@link #layerOf}; 0: every layer). The kinds, each Medium laying one
 * ({@link ResolvedBrush#kind}), stay Show Kinds' ({@link #LABELS}). Pure Java; docs/archive/superpowers/specs/2026-10-20-erase-kind-design.md.
 */
final class EraseKind {
    /** Show Kinds' columns, a kind each after Everything (docs/TEXT-STYLE.md: Title Case, two words at most), and what each is. */
    static final String[] LABELS = {"Everything", "Pen", "Paint", "Dry Media"};
    static final int[] KINDS = {0, ToneDocument.KIND_PEN, ToneDocument.KIND_PAINT, ToneDocument.KIND_PENCIL};
    /**
     * The eraser's choices' words, and the target each sets: the layers top down, as the layer button and its hold show
     * them (the owner, 2026-11-10: "it's in the opposite order as the layers view").
     */
    static final String[] CHOICES = {"Everything", "Layer 3", "Layer 2", "Layer 1"};
    private static final int LAYER = 8;
    static final int[] TARGETS = {0, 3 << LAYER, 2 << LAYER, 1 << LAYER};
    /** The result in two words, said when a choice is made. */
    private static final String[] NOTES = {"Erasing everything", "Erasing layer 3", "Erasing layer 2", "Erasing layer 1"};

    /** The Eraser tile's mark for each choice (none for Everything). */
    static final String[] MARKS = {"", "3", "2", "1"};
    static String note(int choice) { return NOTES[choice]; }
    /** The choice that sets {@code target}, 0 (Everything) for any other. */
    static int choice(int target) { for (int i = 1; i < TARGETS.length; i++) if (TARGETS[i] == target) return i; return 0; }
    /** Kind {@code kinds}' column ({@link #LABELS}), 0 (Everything) for any other. */
    static int choiceOf(int kinds) { for (int i = 1; i < KINDS.length; i++) if (KINDS[i] == kinds) return i; return 0; }
    /** The layer {@code target} erases, 1 to 3 ({@link InkWashLayers#layer}); 0: every one. */
    static int layerOf(int target) { return target >>> LAYER; }

    /**
     * What an eraser set to {@code target} (0: Everything) takes while Show Kinds hides {@code hidden}
     * ({@link NotebookOptions#hiddenKinds}): never a hidden kind, which can't be seen, its layer kept. With none hidden,
     * {@code target} as set; else 0 means nothing is left to take, and the stroke is refused.
     */
    static int takes(int target, int hidden) {
        if (hidden == 0) return target;
        int kinds = target & ToneDocument.KIND_ALL, left = (kinds == 0 ? ToneDocument.KIND_ALL : kinds) & ~hidden;
        return left == 0 ? 0 : left | target & ~ToneDocument.KIND_ALL;
    }

    /**
     * Show Kinds (the owner, 2026-10-28): each kind's step on a page's screen ({@link NotebookOptions#GUIDE_STEPS}, Full to
     * Hidden), two bits a kind, Pen lowest, then Paint, then Dry Media ({@link #LABELS}' order); 0 all Full
     * ({@link ToneDocument#kindsShown}).
     */
    static final int STEPS = 63;
    /** The main menu's row (docs/TEXT-STYLE.md: two words, Title Case). */
    static final String SHOW_KINDS = "Show Kinds";
    /** The step kind {@code choice} (1 Pen, 2 Paint, 3 Dry Media) shows at in {@code shown}. */
    static int step(int shown, int choice) { return choice < 1 ? NotebookOptions.FULL : shown >> 2 * (choice - 1) & 3; }
    /** {@code shown} with kind {@code choice} at {@code step}. */
    static int withStep(int shown, int choice, int step) { int shift = 2 * (choice - 1); return shown & ~(3 << shift) | (step & 3) << shift; }
    /** The step marks of {@code kinds} ({@link ToneDocument#KIND_PEN} bits; a plane's, a brush's medium) show at: the faintest of them. */
    static int stepOf(int shown, int kinds) {
        int step = NotebookOptions.FULL;
        for (int c = 1; c < KINDS.length; c++) if ((kinds & KINDS[c]) != 0) step = Math.max(step, step(shown, c));
        return step;
    }
    /** The kinds ({@link ToneDocument#KIND_PEN} bits) {@code shown} hides: no stroke of them, no eraser taking them. */
    static int hidden(int shown) {
        int hidden = 0;
        for (int c = 1; c < KINDS.length; c++) if (step(shown, c) == NotebookOptions.HIDDEN) hidden |= KINDS[c];
        return hidden;
    }
    /** Why a stroke laying {@code kinds} is refused: the first of them hidden, with where to show it ("Pen is hidden, see Show Kinds"); null when none is. */
    static String refusal(int shown, int kinds) {
        for (int c = 1; c < KINDS.length; c++) if ((kinds & hidden(shown) & KINDS[c]) != 0) return said(c, NotebookOptions.HIDDEN) + ", see " + SHOW_KINDS;
        return null;
    }
    /**
     * Why a tool may not draw on a page whose Show Kinds is {@code shown}: the kind it lays ({@code kinds}) hidden, or for
     * the eraser ({@code eraser}, taking {@code erasing}, {@link #takes}) every kind it would take hidden: "Pen is hidden,
     * see Show Kinds"; null when it may.
     */
    static String hiddenNote(int shown, boolean eraser, int kinds, int erasing) {
        int hidden = hidden(shown);
        return refusal(shown, !eraser ? kinds : hidden != 0 && erasing == 0 ? hidden : 0);
    }
    /** A brush laying {@code kinds} taken up: what it says when its kind isn't Full ("Pen is hidden", "Dry media is faint"); null when it is. */
    static String takenUp(int shown, int kinds) {
        int choice = choiceOf(kinds), step = step(shown, choice);
        return choice > 0 && step != NotebookOptions.FULL ? said(choice, step) : null;
    }
    /** What a brush of a kind not Full says when taken up (docs/TEXT-STYLE.md, sentence case): "Pen is hidden", "Dry media is faint". */
    static String said(int choice, int step) { return sentence(choice) + " is " + NotebookOptions.GUIDE_STEPS[step].toLowerCase(java.util.Locale.ROOT); }
    /** The note a step picked gives, as Template Visibility's ("Template half"): "Pen half", "Dry media hidden". */
    static String stepNote(int choice, int step) { return sentence(choice) + " " + NotebookOptions.GUIDE_STEPS[step].toLowerCase(java.util.Locale.ROOT); }
    /** Kind {@code choice}'s label in sentence case: "Dry media". */
    private static String sentence(int choice) { String l = LABELS[choice]; return l.charAt(0) + l.substring(1).toLowerCase(java.util.Locale.ROOT); }

    private EraseKind() {}
}
