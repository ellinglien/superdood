package io.github.mpdairy.monopaint;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

/**
 * Replay page (Record and Replay, {@link StrokeLog}): the page's recorded strokes and Lasso edits done again on a blank
 * copy of the page ({@link ReplayRun}, with a session of its own), a chunk of pen time at a time, so e-ink shows steps
 * rather than a smear. The page itself is never touched: when the strokes are done (after a pause to look), or when the
 * pen or a finger lands ({@link #ends}), it is shown again as it was. A log of pen strokes alone (one from before the
 * eraser and the Lasso were logged) shows under a veil of paper wherever the page shows no mark ({@link ReplayRun#veiled}),
 * so a stroke since erased or moved doesn't come back as a blob. While it plays, the page's drying ticks wait
 * ({@code DrawingPad}'s ticker is busy): the copy has a stroke open between steps.
 */
final class PageReplay {
    static final int STEP_MS = ReplayRun.STEP_MS, MENU_DP = ReplayRun.MENU_DP;
    static final int[] SPEEDS = ReplayRun.SPEEDS;
    static final long HOLD_MS = ReplayRun.HOLD_MS;
    static final String NOTHING = ReplayRun.NOTHING;
    /** The drawing screen menu's row for it. */
    static final String REPLAY_PAGE = DrawLayout.REPLAY_PAGE;

    private final PaintActivity app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ReplayRun run;

    PageReplay(PaintActivity app) { this.app = app; }

    boolean running() { return run != null; }
    /** The plane over {@code page} as it shows ({@link Ghosts#plane}): the veil over the replay's copy, else null. */
    GhostPlane.Planes veil(ToneDocument page) { return run != null && page != null && page == run.copy ? run.veil : null; }
    /** Whether the current page has strokes to replay. */
    boolean available() { return app.pad.document != null && !app.pad.document.replay().isEmpty(); }

    /**
     * Replay page: the speeds in a menu where the menu it was picked from stood, beside {@code anchor}, as Play's Mirrors
     * opens (the owner, 2026-10-25, of the chips beside the eraser: "why would it appear beside the eraser way far away
     * from the menu where you clicked?"); a tap on one starts the replay.
     */
    void choose(View anchor) {
        if (running() || app.busy()) return;
        if (!available()) { app.message(NOTHING); return; }
        String[] texts = new String[SPEEDS.length];
        Runnable[] actions = new Runnable[SPEEDS.length];
        for (int i = 0; i < texts.length; i++) { int speed = SPEEDS[i]; texts[i] = ReplayRun.speedWords(speed); actions[i] = () -> start(speed); }
        app.menu(anchor, "replay menu", texts, new int[texts.length], actions, MENU_DP);
    }
    /**
     * Whether {@code event} on the page ends a replay ({@link StrokeLog#endsReplay}): a pen or a finger landing, not a hand
     * resting (2026-10-25: a palm's touches, already down as the replay began, ended it at once, the page put back before
     * e-ink showed the blank copy, so the owner "havent been able to get replay to work").
     */
    static boolean ends(MotionEvent event) {
        int action = event.getActionMasked(), index = event.getActionIndex();
        return StrokeLog.endsReplay(action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN, DrawingPad.isPen(event, index),
                event.getTouchMajor(index) / android.content.res.Resources.getSystem().getDisplayMetrics().density);
    }

    void start(int speed) {
        if (running() || app.busy()) return;
        app.pad.finishStroke(); app.pad.settleDrying();
        ReplayRun made = ReplayRun.of(app.pad.document, speed, ReplayRun.session(app.inkWash.slots.swatches));
        if (made == null) { app.message(NOTHING); return; }
        run = made;
        app.pad.showPage(run.copy);
        handler.postDelayed(this::step, STEP_MS);
    }

    private void step() {
        if (!running()) return;
        // The page was changed from under it (a page turn, a new notebook): nothing to put back.
        if (app.pad.document != run.copy) { forget(); return; }
        boolean done = run.step();
        app.pad.replayShow();
        handler.postDelayed(done ? this::stop : this::step, done ? HOLD_MS : STEP_MS);
    }

    /** Ends the replay and shows the page as it was. */
    void stop() {
        if (!running()) return;
        handler.removeCallbacksAndMessages(null);
        run.finish();
        app.pad.settleDrying();
        ToneDocument page = run.original;
        forget();
        app.pad.showPage(page);
    }
    private void forget() { handler.removeCallbacksAndMessages(null); run = null; }
}
