package io.github.mpdairy.monopaint;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.List;

/**
 * Live pages: this tablet's own changes on the page shared, for its Undo and Redo ({@link LiveProbe}), the last
 * {@link #MOST} each way. Undo takes the newest back ({@link ToneDocument#takeBack}: only where the page still shows what
 * it left, so a mark laid over it since stays); Redo takes that Undo's own steps back the same way, so it puts back only
 * where the page still shows what the Undo left. A new change of this tablet's ({@link #made}) clears Redo, as the page's
 * own Redo is cleared; others' changes don't. Each list is the Undo steps one change made ({@link ToneDocument#stepsAfter}).
 */
final class LiveOwn {
    static final int MOST = 10;
    private final ArrayDeque<List<Object>> done = new ArrayDeque<>(), undone = new ArrayDeque<>();

    void clear() { done.clear(); undone.clear(); }

    /** A change this tablet made, as the steps it made (none: nothing). */
    void made(List<Object> steps) {
        if (steps.isEmpty()) return;
        keep(done, steps); undone.clear();
    }

    /** Undo: the newest own change still on the page taken back as one Undo step; the page pixels it changed set in {@code changed}. */
    boolean undo(ToneDocument page, BitSet changed) { return move(page, done, undone, changed); }
    /** Redo: the newest live Undo's change taken back, as {@link #undo}. */
    boolean redo(ToneDocument page, BitSet changed) { return move(page, undone, done, changed); }

    private static boolean move(ToneDocument page, ArrayDeque<List<Object>> from, ArrayDeque<List<Object>> to, BitSet changed) {
        long top = page.top();
        while (!from.isEmpty()) if (page.takeBack(from.removeLast(), changed)) { keep(to, page.stepsAfter(top)); return true; }
        return false;
    }

    private static void keep(ArrayDeque<List<Object>> to, List<Object> steps) {
        to.add(steps);
        if (to.size() > MOST) to.removeFirst();
    }
}
