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
    /** Zen Mode's fan as the pen draws on the page (the owner, 2026-11-10: "close when a pen touches the rest of the page", "allows users to switch in the system settings"): Closes, or Stays Open as it did from 2026-11-06. */
    static final String ZEN_FAN = "Zen Fan", ZEN_FAN_NOTE = "When the pen draws on the page", CLOSES = "Closes", STAYS_OPEN = "Stays Open";
    /** The Prompts' switch (the notebook named #inktober52, 2026-11-13): On fetches the week's word; Off stops the words and hides Send to the Fridge, deleting nothing. */
    static final String PROMPTS = "#inktober52", PROMPTS_NOTE = "Uses the internet: the week's #inktober52 word.";
    /** Fridge Name (the owner, 2026-11-13: "they should be able to update that in settings"): the tablet's name on the Fridge, shown once it has a key; its one chip the name, or Add Name before one. */
    static final String FRIDGE_NAME = "Fridge Name", ADD_NAME = "Add Name";
    /** Read Handwriting (docs/superpowers/specs/2026-11-09-read-handwriting-design.md): its row, note and first-time words. */
    static final String READ_HANDWRITING = "Read Handwriting", READ_HANDWRITING_NOTE = "For Search on the shelf";
    static final String READ_HANDWRITING_ABOUT = "Handwriting is read on this tablet so the shelf can search it. "
        + "Google's reader is downloaded once, about 20 MB. The writing stays on the tablet. "
        + "Google gets usage stats: device and app details, timings and errors.";
    static final String TURN_ON = "Turn On";
    /** Nomad Size (the owner, 2026-11-13: "a mode where manta users could draw like it was a nomad"): the Nomad's panel at its real size, centred; the Manta's alone. */
    static final String NOMAD_SIZE = "Nomad Size", NOMAD_SIZE_NOTE = "Manta only";
    /**
     * The one gate for a flash (the firmware's full refresh, {@link Ui#flashWindow}), and for every clean redraw and forced grey pass
     * the app makes on its own: one {@code asked} for (Refresh, or a tap set to it) always flashes; one the app would make
     * on its own only while Automatic Refresh is on ({@code automatic}).
     */
    static boolean flashes(boolean asked, boolean automatic) { return asked || automatic; }
}
