package io.github.mpdairy.monopaint;

import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.Consumer;

/**
 * System Settings (the owner, 2026-11-04: "a new section to adjust a few things"), the menu's row in its app group, a
 * popup over the drawing since 2026-11-06 (the owner: "it's too small it should just pop up over instead of being its
 * own page"; its own screen until then, {@code PaintActivity.systemSettings} places it as the menu): the title over its
 * job, then a short list, each row its name and its chips ({@link LabButton#chip}, the chosen one filled): Automatic
 * Refresh (On, Off; its caption under it), Finger Action (2026-11-06, the menu's row until then: Nothing, Eraser,
 * Smudge and the slots' brushes by name, three chips a line), Two-Finger Tap and Three-Finger Tap ({@link
 * FingerTool.Tap}), Edge Strip Hold ({@link EdgeHold.Choice}). Words are {@link SystemSettings}'; a tap saves at once in
 * {@link PaintPreferences}, which the drawing screen reads as it acts; a tap redraws only its row's chips (e-ink).
 */
final class SystemSettingsPanel {
    private SystemSettingsPanel() { }

    /** The rows, for {@code app}'s drawing screen; {@code fingers} Finger Action's chips' words in {@link FingerTool.Choice} order. */
    static LinearLayout rows(PaintActivity app, String[] fingers) {
        PaintPreferences prefs = app.prefs;
        LinearLayout list = LabUi.column(app);
        list.setPadding(app.dp(16), app.dp(12), app.dp(16), app.dp(16));
        list.addView(LabUi.barText(app, SystemSettings.TITLE), new LinearLayout.LayoutParams(-1, -2));
        String[] onOff = {SystemSettings.ON, SystemSettings.OFF};
        row(app, list, SystemSettings.AUTO_REFRESH, SystemSettings.AUTO_REFRESH_NOTE, onOff, prefs.autoRefresh() ? 0 : 1, 2, i -> prefs.setAutoRefresh(i == 0));
        // Finger Action (the menu's row until 2026-11-06), kept where the drawing screen reads it (InkWash.finger).
        FingerTool.Choice[] choices = FingerTool.Choice.values();
        row(app, list, SystemSettings.FINGER, null, fingers, app.inkWash.finger().ordinal(), 3, i -> app.inkWash.finger(choices[i]));
        FingerTool.Tap[] taps = FingerTool.Tap.values();
        row(app, list, SystemSettings.TWO_TAP, null, labels(taps), prefs.twoFingerTap().ordinal(), taps.length, i -> prefs.setFingerTap(false, taps[i]));
        row(app, list, SystemSettings.THREE_TAP, null, labels(taps), prefs.threeFingerTap().ordinal(), taps.length, i -> prefs.setFingerTap(true, taps[i]));
        EdgeHold.Choice[] holds = EdgeHold.Choice.values();
        String[] holdLabels = new String[holds.length];
        for (int i = 0; i < holds.length; i++) holdLabels[i] = holds[i].label;
        row(app, list, SystemSettings.EDGE_HOLD, SystemSettings.EDGE_HOLD_NOTE, holdLabels, prefs.edgeHold().ordinal(), holds.length, i -> prefs.setEdgeHold(holds[i]));
        row(app, list, SystemSettings.FRIDGE, SystemSettings.FRIDGE_NOTE, onOff, prefs.fridgeAgreed() ? 0 : 1, 2, i -> prefs.setFridgeAgreed(i == 0));
        return list;
    }
    private static String[] labels(FingerTool.Tap[] taps) {
        String[] labels = new String[taps.length];
        for (int i = 0; i < taps.length; i++) labels[i] = taps[i].label;
        return labels;
    }
    /** A row: {@code name} (a section header's words), {@code note} under it (null: none), then a chip each of {@code choices}, {@code perLine} a line, {@code chosen} filled; a tap fills it and calls {@code pick}. */
    private static void row(PaintActivity app, LinearLayout list, String name, String note, String[] choices, int chosen, int perLine, Consumer<Integer> pick) {
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, -2);
        place.topMargin = app.dp(16);
        list.addView(LabUi.barText(app, name), place);
        if (note != null) {
            TextView caption = LabUi.text(app, note, TypeStyle.CAPTION);
            caption.setTextColor(Theme.GREY_800);
            list.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout chips = null;
        LabButton[] buttons = new LabButton[choices.length];
        for (int i = 0; i < choices.length; i++) {
            if (i % perLine == 0) {
                chips = LabUi.row(app);
                LinearLayout.LayoutParams chipsPlace = new LinearLayout.LayoutParams(-1, -2);
                chipsPlace.topMargin = app.dp(i == 0 ? 6 : 8);
                list.addView(chips, chipsPlace);
            }
            int index = i;
            LabButton chip = buttons[i] = LabButton.chip(app, choices[i]);
            chip.setOn(i == chosen);
            chip.setContentDescription(name + ": " + choices[i]);
            chip.setOnClickListener(v -> {
                for (int c = 0; c < buttons.length; c++) buttons[c].setOn(c == index);
                pick.accept(index);
            });
            chips.addView(chip, LabUi.after(app, -2, 40, i % perLine == 0 ? 0 : 8));
        }
    }
}
