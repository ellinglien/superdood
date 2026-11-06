package io.github.mpdairy.monopaint;

import android.view.View;

/**
 * The Mirrors button's list ({@link DrawLayout#MIRRORS}; in the Fun button's list until the owner, 2026-10-23: "move
 * magnetic poetry and mirrors to the toolbar, removing the confetti"): the mirrors' rows ({@link DrawLayout.MirrorRow})
 * with how many there are under them, the ones on marked by the menu's bar. The pen's next stroke places a mirror
 * ({@link DrawingPad#placing}), and {@link #placed} takes it; Real Mirror's side is Flip Side's, never a stroke's.
 * Magnetic Poetry's button opens or closes the magnets' bin ({@link WordsMode}) itself.
 */
final class MirrorsMenu {
    /** The notices (docs/TEXT-STYLE.md: the result in about three words). */
    static final String TOO_SHORT = Mirrors.TOO_SHORT;
    /** How long a notice shows, ms; the pen going down hides it. */
    static final long SAY_MS = 4000;
    private final PaintActivity app;

    MirrorsMenu(PaintActivity app) { this.app = app; }

    /** Whether the page's mirrors are on, or a mirror is being placed: the menu's Play row marked. */
    boolean on() { return app.pad != null && app.pad.document != null && (app.pad.document.mirrors().acts() || app.pad.placing != DrawingPad.Placing.NONE); }
    /** The live page's tools follow. */
    void changed() { app.live.toolsChanged(); }
    void say(String text) { if (app.inkWashBar != null) app.inkWashBar.sayMirrors(text, SAY_MS); }

    private int width() { return app.prefs.largeSettingsText() ? 296 : 280; }

    /** The mirrors' list beside {@code anchor}: each row ({@link DrawLayout.MirrorRow#rows}), the ones on marked, how many under them. */
    void mirrors(View anchor) {
        Mirrors m = app.pad.document.mirrors();
        java.util.List<DrawLayout.Row> rows = DrawLayout.MirrorRow.rows(m, app.pad.placing == DrawingPad.Placing.LINE, false);
        Runnable[] actions = new Runnable[rows.size()];
        for (int i = 0; i < actions.length; i++) { DrawLayout.MirrorRow row = DrawLayout.MirrorRow.valueOf(rows.get(i).key); actions[i] = () -> act(row); }
        app.menu(anchor, "mirrors menu", rows, actions, width(), m.caption());
    }

    /** A row of the mirrors' list tapped: Place Mirror's next stroke places one; any other changes the page's mirrors ({@link DrawLayout.MirrorRow#change}). */
    private void act(DrawLayout.MirrorRow row) {
        ToneDocument page = app.pad.document;
        if (row == DrawLayout.MirrorRow.PLACE) app.pad.placing = DrawingPad.Placing.LINE;
        else {
            app.layerChange(() -> row.change(page));
            if (row.stopsPlacing(page.mirrors())) app.pad.placing = DrawingPad.Placing.NONE;
        }
        changed();
    }

    /** The pen's placing stroke ended: a mirror placed from it ({@link Mirrors#fit}), or "Too short". */
    void placed(MirrorShow.Place place) {
        ToneDocument page = app.pad.document;
        Mirrors m = page.mirrors();
        Mirrors.Line line = m.fit(place.xs(), place.ys(), place.count(), page.width, page.height);
        if (line == null) { say(TOO_SHORT); return; }
        Mirrors next = DrawLayout.MirrorRow.kept(m.add(line), page);
        app.pad.placing = DrawingPad.Placing.NONE;
        app.layerChange(() -> page.setMirrors(next));
        changed();
    }
}
