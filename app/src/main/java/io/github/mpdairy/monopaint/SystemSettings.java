package io.github.mpdairy.monopaint;

/**
 * System Settings' words and rules (the owner, 2026-11-04: "can we add system settings? this would be a new section to
 * adjust a few things"; {@code SystemSettingsPanel} shows them, a popup): a short list, each row a few chips. Automatic
 * Refresh (On, Off), Finger Action ({@link FingerTool.Choice}; the menu's row until 2026-11-06), Two-Finger Tap and
 * Three-Finger Tap ({@link FingerTool.Tap}), Edge Strip Hold ({@link EdgeHold.Choice}). Kept in {@link PaintPreferences}. Pure, checked on the desktop ({@code SystemSettingsChecks}).
 */
final class SystemSettings {
    private SystemSettings() { }
    static final String TITLE = "System Settings";
    static final String AUTO_REFRESH = "Automatic Refresh", FINGER = "Finger Action", TWO_TAP = "Two-Finger Tap", THREE_TAP = "Three-Finger Tap", EDGE_HOLD = "Edge Strip Hold";
    static final String ON = "On", OFF = "Off";
    /** Automatic Refresh's caption: what it covers. */
    static final String AUTO_REFRESH_NOTE = "After erasing and screen changes";
    /** The edge strip is the Manta's alone (the owner, 2026-11-05: "(manta only) is enough"). */
    static final String EDGE_HOLD_NOTE = "Manta only";
    /**
     * The one gate for a flash (the firmware's full refresh, {@link Ui#flashWindow}), and for every clean redraw and forced grey pass
     * the app makes on its own: one {@code asked} for (Refresh, or a tap set to it) always flashes; one the app would make
     * on its own only while Automatic Refresh is on ({@code automatic}).
     */
    static boolean flashes(boolean asked, boolean automatic) { return asked || automatic; }
}
