package io.github.mpdairy.monopaint;

/**
 * How far High Res has come ({@link PngRender#high}, {@link PageExport}'s Layers), for Export Page's note (the owner,
 * 2026-11-24: "is there a way to give an estimate of the time and tell the user when exporting?"): its strips' work,
 * each weighed by the kept strokes it draws again ({@link SharpView#work}), added before they are drawn and counted as
 * each is. One export at a time: {@link AutoExport}'s worker writes it, the note reads it. Pure.
 */
final class ExportProgress {
    static final String EXPORTING = "Exporting";
    private static volatile long total, done;
    /** Whether a page copy is being made (the notebooks' copies on the same worker count too, but aren't told). */
    private static volatile boolean active;
    /** Strips that went out as their pixels doubled, their strokes' drawing having failed (out of memory, say): the copy still made. */
    static volatile int soft;
    /** A page copy starts: nothing to do yet. */
    static void begin() { total = 0; done = 0; soft = 0; active = true; }
    /** It ended. */
    static void end() { active = false; }
    /** Work about to be done. */
    static void add(long work) { total += work; }
    /** Work done. */
    static void step(long work) { done += work; }
    /** The share done, 0 to 1 (0 before any work is known). */
    static double fraction() { long t = total; return !active || t <= 0 ? 0 : Math.min(1, (double) done / t); }
    /**
     * The note after {@code ms} of an export {@code fraction} done: "Exporting", then once a few seconds and a little of
     * it have passed, the time left as Timed Pages says times, "Exporting, 2 min left" (whole minutes from 90 s),
     * "Exporting, 40 s left" (tens of seconds).
     */
    static String note(long ms, double fraction) {
        if (ms < 5000 || fraction < .02 || fraction >= 1) return EXPORTING;
        long left = Math.round(ms * (1 - fraction) / fraction / 1000.0);
        if (left < 10) return EXPORTING;
        return EXPORTING + ", " + (left >= 90 ? Math.round(left / 60.0) + " min" : Math.max(10, Math.round(left / 10.0) * 10) + " s") + " left";
    }
    private ExportProgress() {}
}
