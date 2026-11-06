package io.github.mpdairy.monopaint;

/**
 * The Lasso's pieces on the drawing screen ({@link DrawLayout#LASSO}): the one floating over the page (lifted by a loop,
 * {@link LassoPlanes#lift}, or pasted), how it lay when it began to float, the one kept to paste (Cut), and the pen's grab
 * on it; and what Cut, Duplicate, Delete and Paste do to them ({@link DrawLayout#LASSO_BAR}). The Manta's drawing screen
 * ({@code DrawingPad}) and the browser's (web/engine's WebEngine) keep their pieces here, so the two agree. Pure: the
 * caller shows the page again where {@link #area} says.
 */
final class LassoPieces {
    /** The piece floating, or null; how it lay when it began to float, for a lift or a copy (to tell an unmoved one), else null; the piece kept to paste, or null. */
    FloatingSelection floating, laid, kept;
    /** The part of {@link #floating} the pen is dragging ({@link FloatingSelection#NONE}: none), and where the drag began and is now, page px. */
    int handle = FloatingSelection.NONE;
    float grabX, grabY, dragX, dragY;

    /** {@code piece} floats over the page (any floating is put down first by the caller). */
    void floatPiece(FloatingSelection piece) { floating = piece; laid = piece.lifted ? piece.copy() : null; }
    /** Whether the pen at page point {@code x, y} grabs the floating piece ({@link FloatingSelection#hit}); then it drags it. */
    boolean grab(float x, float y, float[] knob, float reach) {
        int h = floating == null ? FloatingSelection.NONE : floating.hit(x, y, knob, reach);
        if (h == FloatingSelection.NONE) return false;
        handle = h; grabX = dragX = x; grabY = dragY = y;
        floating.grab(h);
        return true;
    }
    /** The pen dragging the piece is at page point {@code x, y}. */
    void drag(float x, float y) { dragX = x; dragY = y; floating.drag(handle, grabX, grabY, x, y); }
    /** The drag ends, the piece floating where it got to. */
    void release() { floating.release(); handle = FloatingSelection.NONE; }
    /** Whether a piece floats over the page; whether one is kept to paste. */
    boolean floats() { return floating != null; }
    boolean hasKept() { return kept != null; }
    /** The page pixels the floating piece shows over: where it lies and the hole it was lifted from, {left, top, right, bottom}; null for none. */
    int[] area() {
        if (floating == null) return null;
        int[] a = floating.bounds().clone(), cut = floating.cutBounds();
        if (cut != null) { a[0] = Math.min(a[0], cut[0]); a[1] = Math.min(a[1], cut[1]); a[2] = Math.max(a[2], cut[2]); a[3] = Math.max(a[3], cut[3]); }
        return a;
    }
    /** Lets the floating piece go; what it was. */
    FloatingSelection end() { FloatingSelection piece = floating; floating = null; laid = null; handle = FloatingSelection.NONE; return piece; }
    /** Whether the floating piece lies just as it began to float: a copy just where one was laid, or a lift not moved, changes nothing put down. */
    boolean unmoved() { return floating != null && laid != null && floating.samePose(laid); }
    /** The floating piece put down on {@code page} as one Undo step ({@link FloatingSelection#place}), none floating after; whether the page changed. */
    boolean place(ToneDocument page) {
        if (floating == null) return false;
        boolean same = unmoved();
        FloatingSelection piece = end();
        return !same && piece.place(page);
    }
    /** Delete: the floating piece goes, and what it was lifted from with it, one Undo step ({@link #erase}); whether the page changed. */
    boolean delete(ToneDocument page) { return floating != null && erase(page, end()); }
    /** Cut: kept to paste ({@link #paste}), then deleted as Delete does; whether the page changed. */
    boolean cut(ToneDocument page) {
        if (floating == null) return false;
        FloatingSelection piece = floating;
        kept = piece.copy();
        return delete(page);
    }
    /** What {@code piece}, lifted, rang taken off {@code page} ({@link LassoPlanes#place}, cut only), one Undo step; whether the page changed. */
    static boolean erase(ToneDocument page, FloatingSelection piece) { return piece.lifted && piece.planes != null && piece.planes.place(page, piece, false, true); }
    /**
     * Duplicate (the owner, 2026-10-28: "it makes sense to have duplicate instead of copy"): the piece goes down where it
     * lies, and a copy of it floats {@link DrawLayout#DUPLICATE_OFFSET} px down and right, to drag into place. The copy, or null with none floating.
     */
    FloatingSelection duplicate(ToneDocument page) {
        if (floating == null) return null;
        FloatingSelection copy = duplicated(floating);
        place(page);
        floatPiece(copy);
        return copy;
    }
    /** A copy of {@code piece} {@link DrawLayout#DUPLICATE_OFFSET} px down and right of it. */
    static FloatingSelection duplicated(FloatingSelection piece) {
        FloatingSelection copy = piece.copy();
        copy.centerX += DrawLayout.DUPLICATE_OFFSET; copy.centerY += DrawLayout.DUPLICATE_OFFSET;
        return copy;
    }
    /**
     * Make Stamp (the Lasso's bar): {@code piece}'s ink as a Stamp's nib, cut as Cut Stamp cuts the page ({@link
     * StampNib#cut}): its own pixels, unturned and at its own size, over white paper; null for none.
     */
    static StampNib nib(FloatingSelection piece) {
        int w = piece.width, h = piece.height;
        Mirrors.Tones look = (x, y) -> { int i = y * w + x; return 255 - (255 - (piece.tones[i] & 255)) * (piece.alpha[i] & 255) / 255; };
        return StampNib.cut(look, 255, new float[]{0, w, w, 0}, new float[]{0, 0, h, h}, 4, w, h);
    }
    /** Paste: what to float over {@code page}, a copy of the piece kept, after the floating one is put down; null with none kept or for a page of another size ({@link DrawLayout#LASSO_OTHER_SIZE}). */
    FloatingSelection pasted(ToneDocument page) { return kept == null || !kept.fits(page) ? null : kept.copy(); }
}
