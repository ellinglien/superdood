package io.github.mpdairy.monopaint;

import java.util.function.IntFunction;

/**
 * What one finger does on the page, chosen from the main menu's Finger row and kept: nothing, the eraser
 * (what a finger always did, sized by the fingertip), the smudge tool, Fill in, or a slot's brush at the slot's size and grey. Two and
 * three fingers keep their taps (Undo, Redo). And when a finger may draw at all: never while the pen is down or near
 * (a palm resting with the pen), never a palm-sized contact, and only once it moves, so a two- or three-finger tap's
 * first finger lays nothing; a second finger, the pen arriving or a contact swelling to a palm's takes back what it
 * drew. Pure, checked on the desktop ({@code FingerToolChecks}).
 */
final class FingerTool {
    /** Where the choice is kept (the app's preferences). */
    static final String KEY = "inkwash_finger";
    /**
     * Palm thresholds, dp, from the owner's logcat: fingertips land at 51 to 77 dp and swell to 85 dp as they rub; resting
     * hands land at 94 to 128 dp. Once drawing only a contact as broad as a whole hand counts as a palm, or a pressing
     * fingertip's stroke would come undone mid-way.
     */
    static final float PALM_DP = 90, PALM_DRAWING_DP = 115;
    /** How far a finger moves before it draws, dp: a tap's first finger stays within it. */
    static final float SLOP_DP = 8;
    /** The pressure a finger draws a brush at (a fingertip has none): a medium press's ({@link ToolSettings#pressure}). */
    static final float PRESSURE_TRAVEL = .5f;

    /**
     * The choices, in the menu's order. Keys are kept: never change them. A slot has no label of its own: the slots
     * have no numbers (the owner, 2026-10-19), so it is shown as its brush, by name ({@link #label(IntFunction)}) and
     * by its tray stroke beside it.
     */
    enum Choice {
        NOTHING("nothing", "Nothing"), ERASER("eraser", "Eraser"), SMUDGE("smudge", "Smudge"), FILL("fill", ToolSlots.FILL_TOOL),
        // A swipe across the page turns it, as inside a notebook (the owner, 2026-11-13).
        TURN("turn", "Turn pages"),
        SLOT_1("slot1", null), SLOT_2("slot2", null), SLOT_3("slot3", null),
        SLOT_4("slot4", null), SLOT_5("slot5", null), SLOT_6("slot6", null), SLOT_7("slot7", null), SLOT_8("slot8", null),
        SLOT_9("slot9", null);
        final String key;
        /** Nothing's, Eraser's, Smudge's, Fill in's and Turn pages' label; null for a slot. */
        private final String label;
        Choice(String key, String label) { this.key = key; this.label = label; }
        /** Whether it draws with a slot's brush. */
        boolean slot() { return label == null; }
        /** What it is called: its label, or a slot's brush's name, {@code brushName} of the slot. */
        String label(IntFunction<String> brushName) { return slot() ? brushName.apply(tool()) : label; }
        /** What Finger Action says, and its menu row: "Finger Action: Eraser", "Finger Action: Brush pen" ("Finger" until the owner, 2026-10-29). */
        String row(IntFunction<String> brushName) { return "Finger Action: " + label(brushName); }
        /** The bar's tool the finger draws with ({@link ToolSlots#ERASER}, {@link ToolSlots#SMUDGE}, {@link ToolSlots#FILL} or a slot); -1 for nothing. */
        int tool() {
            switch (this) {
                case NOTHING: case TURN: return -1;
                case ERASER: return ToolSlots.ERASER;
                case SMUDGE: return ToolSlots.SMUDGE;
                case FILL: return ToolSlots.FILL;
                default: return ordinal() - SLOT_1.ordinal();
            }
        }
        /** The choice that draws with the bar's tool {@code tool} (a slot, the eraser or the smudge tool); Nothing for any other. */
        static Choice of(int tool) {
            for (Choice c : values()) if (tool >= 0 && c.tool() == tool) return c;
            return NOTHING;
        }
        /** The choice kept as {@code key}; the eraser (what a finger always did) for none or one that doesn't read. */
        static Choice saved(String key) {
            for (Choice c : values()) if (c.key.equals(key)) return c;
            return ERASER;
        }
    }

    private FingerTool() { }

    /**
     * Whether a finger landing is refused for the whole touch: the choice is Nothing, a pen is among the touches, the pen
     * is down or was near a moment ago or something else holds the page ({@code blocked}), the app is busy, or a contact
     * is {@code widestDp} across, a palm's.
     */
    static boolean refused(Choice choice, boolean fingersOnly, boolean blocked, boolean busy, float widestDp) {
        return choice == Choice.NOTHING || !fingersOnly || blocked || busy || widestDp > PALM_DP;
    }
    /**
     * Whether this event takes back the finger's touch (what it drew, if it is {@code drawing}): another finger landing
     * (a tap's), the pen landing among the touches (a palm that was there first), or a contact grown to a palm's.
     */
    static boolean cancels(boolean anotherFinger, boolean fingersOnly, float widestDp, boolean drawing) {
        return anotherFinger || !fingersOnly || widestDp > (drawing ? PALM_DRAWING_DP : PALM_DP);
    }
    /**
     * Whether a system cancel keeps what the finger drew: the Supernote cancels every touch when the pen comes near
     * (the owner, 2026-10-23: "had some stuff reappear on screen after erasing with my finger"), so a stroke that has
     * gone on {@link #KEEP_MS} or more is kept, and only a shorter one, a brush of a palm, is taken back.
     */
    static boolean keepsOnCancel(long heldMs) { return heldMs >= KEEP_MS; }
    static final long KEEP_MS = 400;
    /**
     * Whether a two- or three-finger tap (Undo, Redo) at {@code now} counts: not while the pen is down, nor within
     * {@link #PEN_PALM_MS} of {@code penSeen}, when the pen was last seen hovering, touching or lifting; then it is the
     * hand resting as it draws (the owner, 2026-11-03: palm landings undoing and redoing).
     */
    static boolean tapCounts(long now, long penSeen, boolean penDown) { return !penDown && now - penSeen >= PEN_PALM_MS; }
    /**
     * What a two- or three-finger tap does, chosen in System Settings (the owner, 2026-11-04: "customize the touch
     * controls (2 finger tap, etc)"): the app's own Undo, Redo and Refresh, or Nothing. Keys are kept: never change them.
     */
    enum Tap {
        UNDO("undo", "Undo"), REDO("redo", "Redo"), REFRESH("refresh", DrawLayout.REFRESH), NOTHING("nothing", "Nothing");
        final String key, label;
        Tap(String key, String label) { this.key = key; this.label = label; }
        /** The tap kept as {@code key}; {@code fallback} for none or one that doesn't read. */
        static Tap saved(String key, Tap fallback) {
            for (Tap t : values()) if (t.key.equals(key)) return t;
            return fallback;
        }
    }
    /** Where the taps' choices are kept, and what they do at first (Undo and Redo, as always). */
    static final String TWO_TAP = "two_finger_tap", THREE_TAP = "three_finger_tap";
    static final Tap TWO_FIRST = Tap.UNDO, THREE_FIRST = Tap.REDO;
    /** What a tap of {@code fingers} does, {@code two} and {@code three} the choices; Nothing for any other count. */
    static Tap tap(int fingers, Tap two, Tap three) { return fingers == 2 ? two : fingers == 3 ? three : Tap.NOTHING; }
    static final long PEN_PALM_MS = 300;
    /** A contact this wide, px, is a palm: the Manta's panel reads a finger 96-128 and a palm 240 (getevent, 2026-11-05). */
    static final float PALM_MAJOR = 192;
    /** Whether a finger {@code movedDp} from where it landed starts drawing. */
    static boolean starts(float movedDp) { return movedDp > SLOP_DP; }
}
