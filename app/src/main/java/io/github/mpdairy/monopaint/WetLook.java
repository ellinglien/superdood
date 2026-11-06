package io.github.mpdairy.monopaint;

/**
 * What the wet indicator ({@link WetIndicator}) shows and when it looks again, in pure Java so the Manta and the
 * browser (web/engine's WebEngine) agree: the drop dashed while the page is dry, half full while paint still moves
 * ({@link WatercolorSession#moving}), full while paint lies wet at rest ({@link WatercolorSession#wet}); a quick
 * stroke's render after lift alone shows it only once it has lasted {@link #RENDERING_MS}; never a change while the
 * pen is down. Checked on the desktop ({@code LabColumnChecks}).
 */
final class WetLook {
    /** What it shows: dry, paint moving, wet paint at rest. */
    enum State { DRY, MOVING, WET }
    /** How often it looks again while a stroke is down, ms. */
    static final long STROKING_MS = 100;
    /**
     * A quick stroke's render after lift alone shows the droplet only once it has lasted this long, ms. Most end well
     * before. On the Manta the panel runs one update at a time where they don't overlap: a droplet drawn at the lift
     * (while the stroke's fast updates still run) held the stroke's own last update back until the droplet's was done,
     * about 0.3 s, and hiding it again cost as much (engine spec, "Quick then render", the wait after lift).
     */
    static final long RENDERING_MS = 1000;
    /** How often it looks again while wet, for paint coming to rest (no event says so), ms. */
    static final long WET_MS = 1000;

    /** The drop's box, dp: about MonoPaint's droplet's height (29 dp), Phosphor's drop filling most of its box. */
    // The other small icons' size, Menu's (the owner, 2026-10-29: "the droplet looks a bit big in the toolbar... try to keep them all proportional"; 30 until then).
    static final float ICON_DP = DrawLayout.ICON_FULL;
    /** The blow dryer's room after a tap, dp: MonoPaint's droplet button's width, which its dryer and air fit ({@code BlowDryer.draw}). */
    static final float DRYER_DP = 32;
    /** The blow dryer's frames and each one's time, ms: MonoPaint's ({@code BlowDryer.FRAMES}, {@code FRAME_MS}). */
    static final int BLOW_FRAMES = 6, BLOW_MS = 140;
    /** When a quick stroke's render alone began to keep the page wet (the caller's ms), or −1. */
    private long renderingSince = -1;
    /** The last look's result: what to show (null: as it is, waiting), and the ms until the next look (−1: none). */
    State state;
    long next;

    /** Looks at {@code now} while it shows {@code shown}: {@link #state} and {@link #next} set. */
    void look(State shown, long now, boolean stroking, boolean wet, boolean moving, boolean onlyRendering) {
        state = null;
        if (stroking) { next = STROKING_MS; return; }
        if (wet && shown == State.DRY && onlyRendering) {
            if (renderingSince < 0) renderingSince = now;
            if (now - renderingSince < RENDERING_MS) { next = renderingSince + RENDERING_MS - now; return; }
        } else if (!onlyRendering) renderingSince = -1;
        state = !wet ? State.DRY : moving ? State.MOVING : State.WET;
        next = wet ? WET_MS : -1;
    }
    /** What a screen reader says of {@code state}. */
    static String said(State state) { return state == State.DRY ? "Dry." : state == State.MOVING ? "Paint moving. Tap to dry." : "Wet. Tap to dry."; }
    /** Its drawing for {@code state} (res/drawable). */
    static String icon(State state) { return state == State.DRY ? "ic_drop_dashed" : state == State.MOVING ? "ic_ph_drop_half_bottom" : "ic_ph_drop_fill"; }
}
