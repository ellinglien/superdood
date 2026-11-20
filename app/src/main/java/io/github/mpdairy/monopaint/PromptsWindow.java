package io.github.mpdairy.monopaint;

import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A Turn On screen, before something uses the internet ({@link FridgeDoor}'s; #inktober52's too until it went, 2026-11-20).
 * The dialog the app's ({@link PaintActivity#showDialog}), as {@link FridgeWindow}'s.
 */
final class PromptsWindow {
    static final String PRIVACY = "superdood.org/privacy.html", TURN_ON = "Turn On", NOT_NOW = "Not Now";
    private final PaintActivity app;

    PromptsWindow(PaintActivity app) { this.app = app; }

    /** {@code title}: {@code about}, a rule, {@code uses} in bold, {@code more}, the privacy page; Turn On runs {@code on}, Not Now nothing. */
    void turnOn(String title, String about, String uses, String[] more, Runnable on) {
        LinearLayout column = LabUi.column(app);
        column.setPadding(app.dp(24), app.dp(8), app.dp(24), app.dp(8));
        TextView top = LabUi.text(app, about, TypeStyle.BODY); top.setPadding(0, 0, 0, app.dp(12));
        line(column, top, 0);
        LabUi.rule(column, Theme.FINE, Theme.INK);
        TextView bold = LabUi.text(app, uses, TypeStyle.BODY); bold.setTypeface(Theme.face(700));
        line(column, bold, 12);
        for (String m : more) line(column, LabUi.text(app, m, TypeStyle.BODY), 4);
        line(column, LabUi.text(app, PRIVACY, TypeStyle.CAPTION), 12);
        app.showDialog(new AlertDialog.Builder(app).setTitle(title).setView(column)
                .setPositiveButton(TURN_ON, (d, w) -> on.run()).setNegativeButton(NOT_NOW, null));
    }
    private void line(LinearLayout column, TextView text, int gap) {
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(-1, -2);
        below.topMargin = app.dp(gap);
        column.addView(text, below);
    }

}
