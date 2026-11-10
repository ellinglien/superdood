package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;

/** Read Handwriting: strokes grouped into lines as the probe grouped the owner's pages (2026-11-09). */
public final class WritingLinesChecks {
    public static void main(String[] args) {
        List<PageWriting.Stroke> s = new ArrayList<>();
        // Line 1 at y≈100: three letters, a tall one (an l) sitting higher.
        s.add(letter(100, 100, 30)); s.add(letter(140, 100, 30)); s.add(letter(180, 85, 60));
        // Line 2 at y≈200, written next.
        s.add(letter(100, 200, 30)); s.add(letter(140, 200, 30));
        // A dot for line 1 added after line 2 was written.
        s.add(dot(150, 75));
        // An arrow across both lines, drawn last.
        s.add(line(300, 60, 320, 240));
        List<WritingLines.Line> lines = WritingLines.of(s);
        check(lines.size() == 3, "two lines of writing and the arrow: " + lines.size());
        check(lines.get(0).strokes.size() == 4, "line 1 keeps its tall letter and its late dot: " + lines.get(0).strokes.size());
        // The arrow's middle (150) sorts between the two lines, so line 2 is last.
        check(lines.get(1).strokes.size() == 1 && lines.get(2).strokes.size() == 2, "the arrow alone, then line 2: " + lines.get(1).strokes.size() + ", " + lines.get(2).strokes.size());
        check(lines.get(0).middle < lines.get(1).middle && lines.get(1).middle < lines.get(2).middle, "top to bottom");
        check(WritingLines.of(new ArrayList<>()).isEmpty(), "no strokes, no lines");
        System.out.println("PASS: strokes group into lines: a tall letter, a late dot and a stray arrow don't break them");
    }
    /** A small loop of height h whose middle is at y. */
    static PageWriting.Stroke letter(float x, float y, float h) {
        int n = 12; float[] xs = new float[n], ys = new float[n]; long[] t = new long[n];
        for (int i = 0; i < n; i++) { double a = 2 * Math.PI * i / n; xs[i] = (float) (x + 10 * Math.cos(a)); ys[i] = (float) (y + h / 2 * Math.sin(a)); t[i] = i * 8; }
        return new PageWriting.Stroke(xs, ys, t);
    }
    static PageWriting.Stroke dot(float x, float y) { return new PageWriting.Stroke(new float[]{x, x + 1}, new float[]{y, y}, new long[]{0, 8}); }
    static PageWriting.Stroke line(float x0, float y0, float x1, float y1) {
        int n = 20; float[] xs = new float[n], ys = new float[n]; long[] t = new long[n];
        for (int i = 0; i < n; i++) { xs[i] = x0 + (x1 - x0) * i / (n - 1); ys[i] = y0 + (y1 - y0) * i / (n - 1); t[i] = i * 8; }
        return new PageWriting.Stroke(xs, ys, t);
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
