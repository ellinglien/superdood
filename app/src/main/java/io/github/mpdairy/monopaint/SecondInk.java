package io.github.mpdairy.monopaint;

/** The notebook's second ink (2026-11-09): the colours a Second Ink brush's marks show in PDF and PNG copies; the screen stays grey. */
final class SecondInk {
    private SecondInk() {}
    /** Blue, the first, is the default: a notebook that keeps it writes no record. */
    static final int DEFAULT = 0;
    static final String[] NAMES = {"Blue", "Sepia", "Red", "Green", "Violet"};
    /** The owner's, tuned by eye on a sample page (2026-11-10): Blue a blue-black, after Quink's. */
    private static final int[] RGB = {0x25354c, 0x573a1e, 0x7a1f24, 0x4a6b2a, 0x4a2a55};
    /** Ink {@code index}'s colour, 0xRRGGBB; an index out of range reads as {@link #DEFAULT}. */
    static int rgb(int index) { return RGB[index >= 0 && index < RGB.length ? index : DEFAULT]; }
}
