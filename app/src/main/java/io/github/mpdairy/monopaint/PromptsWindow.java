package io.github.mpdairy.monopaint;

import android.app.AlertDialog;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Prompts notebook's window and its words ({@link Prompts}): the Turn On screen, shown instead of the notebook until
 * it is turned on (it uses the internet); then, at each open, the open words fetched on one worker, never the main
 * thread, and handed to {@link PaintActivity#promptsArrived}. A failed fetch (no Wi-Fi, the site down) says nothing.
 * The dialog the app's ({@link PaintActivity#showDialog}), as {@link FridgeWindow}'s.
 */
final class PromptsWindow {
    private static final String TAG = "Prompts";
    static final String ABOUT = "Draw along with #inktober52, a word a week.", USES = "This notebook uses the internet.",
            CHECKS = "It checks superdood.org for each week's word.", NOTHING = "Nothing you draw leaves the tablet unless you choose Send to the Fridge.",
            PRIVACY = "superdood.org/privacy.html", TURN_ON = "Turn On", NOT_NOW = "Not Now";
    private final PaintActivity app;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "Prompts"));

    PromptsWindow(PaintActivity app) { this.app = app; }

    /** The Turn On screen: Not Now leaves the shelf as it was; Turn On runs {@code on}. Nothing calls the site before. */
    void turnOn(Runnable on) {
        LinearLayout column = LabUi.column(app);
        column.setPadding(app.dp(24), app.dp(8), app.dp(24), app.dp(8));
        TextView about = LabUi.text(app, ABOUT, TypeStyle.BODY); about.setPadding(0, 0, 0, app.dp(12));
        line(column, about, 0);
        LabUi.rule(column, Theme.FINE, Theme.INK);
        TextView uses = LabUi.text(app, USES, TypeStyle.BODY); uses.setTypeface(Theme.face(700));
        line(column, uses, 12);
        line(column, LabUi.text(app, CHECKS, TypeStyle.BODY), 4);
        line(column, LabUi.text(app, NOTHING, TypeStyle.BODY), 4);
        line(column, LabUi.text(app, PRIVACY, TypeStyle.CAPTION), 12);
        app.showDialog(new AlertDialog.Builder(app).setTitle(Prompts.NOTEBOOK).setView(column)
                .setPositiveButton(TURN_ON, (d, w) -> on.run()).setNegativeButton(NOT_NOW, null));
    }
    private void line(LinearLayout column, TextView text, int gap) {
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(-1, -2);
        below.topMargin = app.dp(gap);
        column.addView(text, below);
    }

    /** The open words fetched for notebook {@code path} as {@code book}, then handed to the app on the main thread. */
    void words(String path, DrawingBook book) {
        worker.execute(() -> {
            List<Prompts.Word> words;
            try { words = Prompts.words(Fridge.words()); }
            catch (IOException | RuntimeException e) { Log.w(TAG, "words: " + e.getClass().getSimpleName()); return; }
            app.runOnUiThread(() -> { if (!app.destroyed) app.promptsArrived(path, book, words); });
        });
    }
}
