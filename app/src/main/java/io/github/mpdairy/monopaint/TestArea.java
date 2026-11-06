package io.github.mpdairy.monopaint;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;

/**
 * Brush Lab's test area, shared with the Tools page: the test page ({@link BrushTestPad}, in its own
 * {@link WatercolorSession} paced as the drawing screen's, its settling on a {@link SettleWorker}; the tilt feed listens
 * through it, so Gravity runs on it) in a {@link Theme#FINE} dp ink frame, nothing over it; the <b>try strip</b> both
 * pages show ({@link #strip}: "Draw here to try Fill in" and Clear over the framed page; no wet drop since 2026-11-03,
 * the page's paint drying by itself); and the brush's loop in the look's grey, which Brush Lab puts right beside the
 * brush's name ({@link #blockLoop}), so the name and what it draws always show together. The host says which brush and
 * look the strokes use; they are the drawing screen's strokes ({@link InkWash#stroke}).
 */
final class TestArea {
    interface Host {
        /** The brush the pen draws with. */
        Brush brush();
        /** Its size and grey. */
        ToolSwatches.Look look();
        /** The toolbar's swatches, whose greys a brush with Grey Shift picks from ({@link SwatchPicker}); null for their first. */
        default ToolSwatches swatches() { return null; }
    }

    final WatercolorSession session = new WatercolorSession();
    final BrushTestPad pad;
    /**
     * The brush's loop, About This Brush's in Brush Lab ({@link LabLayout#loopWidth}), directly left of its name (the
     * Tools page shows a tray stroke instead). No frame; drawn in the background ({@link #showLoop}).
     */
    final LoopCard blockLoop;
    private final Context context;
    private final Host host;
    /** Below this share of its grey's ink a Wet brush on the test page counts as run dry. */
    private static final float DRY = .25f;
    /** The last brush the test page drew with ({@link Brush#identity}), or null. */
    private String lastBrush;

    TestArea(Context context, SampleQueue samples, Host host) {
        this.context = context; this.host = host;
        // No page shown yet (Brush Lab or Tools brought back alone): a new notebook's surface, Fine, not each brush's own paper.
        if (WildSample.surface == null) WildSample.show(NotebookOptions.NEW.surface);
        // The name beside it says what it is.
        blockLoop = new LoopCard(context, samples, Collections.emptyList(), null);
        blockLoop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        session.pace = new SettlePacer();
        session.worker = new SettleWorker();
        // A brush with Grey Shift picks its grey at each touch, as on the drawing screen.
        pad = new BrushTestPad(context, Device.current(context).landscapeTilt, session, (page, eraserEnd) -> {
            Brush brush = eraserEnd ? null : host.brush();
            // Another brush on the test page starts dipped (a Wet brush in its grey), as a brush never dipped does.
            String identity = brush == null ? null : brush.identity();
            if (identity != null && !identity.equals(lastBrush)) { session.held.dip(session.holder()); lastBrush = identity; }
            // A Wet brush run nearly dry dips itself again: the Tools page has no greys to dip it in, and a dry brush on a
            // fresh test page lays nothing (Brush Lab's greys dip it too).
            ToolSwatches.Look look = host.look();
            if (session.held.wetInk(session.holder()) < DRY * look.darkness) session.held.dip(session.holder());
            // And Gradient's Cycle steps through the toolbar's greys.
            session.swatches = host.swatches();
            return InkWash.stroke(page, brush, SwatchPicker.look(brush, look, host.swatches(), session.held, session.holder()), session);
        });
        new TiltFeed(pad, session);
    }

    /** The try strip's words: "Draw here to try Fill in", what the test page is for, named after the brush it draws with. */
    private TextView tryLabel;
    /**
     * The try strip, the Tools page's and Brush Lab's one piece: a head row {@code head} dp tall, what it is for ("Draw
     * here to try Fill in", grey, cut with … to fit; {@link #label}) and Clear at its right ({@code clearPad} dp either
     * side of its word), then {@code gap} dp, then the test page in its frame, taking the rest.
     */
    LinearLayout strip(int head, int gap, int clearPad) {
        LinearLayout strip = LabUi.column(context);
        LinearLayout row = LabUi.row(context);
        tryLabel = LabUi.text(context, tryLabel == null ? "" : tryLabel.getText().toString(), TypeStyle.CAPTION);
        tryLabel.setTextColor(Theme.GREY_800);
        tryLabel.setSingleLine(true);
        tryLabel.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(tryLabel, new LinearLayout.LayoutParams(0, -2, 1));
        LabButton clear = clear(clearPad);
        clear.setMinHeight(0); clear.setMinimumHeight(0);
        row.addView(clear, new LinearLayout.LayoutParams(-2, -1));
        strip.addView(row, new LinearLayout.LayoutParams(-1, dp(head)));
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, 0, 1);
        place.topMargin = dp(gap);
        strip.addView(frame(), place);
        return strip;
    }
    /** The try strip names {@code name}, the brush the test page draws with ({@link ToolsLayout#TRY}). */
    void label(String name) {
        String said = ToolsLayout.TRY + name;
        if (tryLabel != null && !said.contentEquals(tryLabel.getText())) tryLabel.setText(said);
    }

    /** Clear, plain words, {@code padding} dp either side of its word: clears the test page. */
    LabButton clear(int padding) {
        LabButton clear = new LabButton(context, 0, "Clear", false);
        clear.setPadding(dp(padding), 0, dp(padding), 0);
        clear.setOnClickListener(v -> pad.clear());
        return clear;
    }
    /** The test page in a {@link Theme#FINE} dp ink frame (no grey: e-ink); nothing lies over the page. */
    private View frame() {
        LinearLayout test = LabUi.column(context);
        test.setBackgroundColor(Theme.INK);
        int rule = dp(Theme.FINE);
        test.setPadding(rule, rule, rule, rule);
        Ui.detach(pad);
        test.addView(pad, new LinearLayout.LayoutParams(-1, 0, 1));
        return test;
    }
    /** {@link #blockLoop}, taken from where it was, in {@code place}. */
    View blockLoop(LinearLayout.LayoutParams place) {
        Ui.detach(blockLoop);
        blockLoop.setLayoutParams(place);
        return blockLoop;
    }
    /**
     * The brush's loop at its own full darkness, whatever the current grey, at the size that fits the tile (as a library
     * card's), drawn in the background when the brush changed.
     */
    void showLoop() {
        Brush brush = host.brush();
        blockLoop.setSample(brush.identity(), LabLibrary.sampler(brush));
    }

    private int dp(float value) { return Ui.dp(context, value); }
}
