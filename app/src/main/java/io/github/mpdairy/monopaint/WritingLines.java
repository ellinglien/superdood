package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Read Handwriting: a page's strokes as lines of writing, each read on its own (the probe, 2026-11-09: a page read as one
 * block gave "Hellman"; line by line, the owner's seven lines came out nearly word for word). Follows the order of
 * writing: a stroke starts a new piece when it begins more than {@link #BACK} letters left of the stroke before's right
 * edge, or its middle is more than {@link #JUMP} letters above or below that stroke's; then a piece joins an earlier line
 * whose middle is within a letter (the nearest such line; a dot or crossing added later, a line interrupted). A letter is the median height of
 * the strokes of more than two points. (Height alone split a line whose small letters were 31 px and wandered more.)
 * Pure; checked on the desktop ({@code WritingLinesChecks}).
 */
final class WritingLines {
    static final float BACK = 4, JUMP = 2;
    /** Without strokes to measure, a letter's height in page px. */
    static final float LETTER = 50;

    static final class Line {
        final List<PageWriting.Stroke> strokes = new ArrayList<>();
        /** left, top, right, bottom, page px. */
        final float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        float middle;
        void add(List<PageWriting.Stroke> more) {
            strokes.addAll(more);
            float[] b = PageWriting.box(more);
            box[0] = Math.min(box[0], b[0]); box[1] = Math.min(box[1], b[1]); box[2] = Math.max(box[2], b[2]); box[3] = Math.max(box[3], b[3]);
            middle = middle(strokes);
        }
    }

    static List<Line> of(List<PageWriting.Stroke> all) {
        List<PageWriting.Stroke> strokes = new ArrayList<>();
        for (PageWriting.Stroke s : all) if (s.size() > 0) strokes.add(s);
        List<Float> heights = new ArrayList<>();
        for (PageWriting.Stroke s : strokes) if (s.size() > 2) { float[] b = PageWriting.box(Collections.singletonList(s)); heights.add(b[3] - b[1]); }
        float letter = Math.max(1, heights.isEmpty() ? LETTER : median(heights)); // floor: flat strokes (dashes) must not make it 0
        List<List<PageWriting.Stroke>> pieces = new ArrayList<>();
        float[] last = null;
        float lastMiddle = 0;
        for (PageWriting.Stroke s : strokes) {
            float[] b = PageWriting.box(Collections.singletonList(s));
            float m = middle(Collections.singletonList(s));
            if (last == null || b[0] < last[2] - BACK * letter || Math.abs(m - lastMiddle) > JUMP * letter) pieces.add(new ArrayList<>());
            pieces.get(pieces.size() - 1).add(s);
            last = b;
            lastMiddle = m;
        }
        List<Line> lines = new ArrayList<>();
        for (List<PageWriting.Stroke> piece : pieces) {
            float m = middle(piece);
            Line home = null;
            float best = letter;
            for (Line l : lines) if (Math.abs(m - l.middle) < best) { best = Math.abs(m - l.middle); home = l; }
            if (home == null) { home = new Line(); lines.add(home); }
            home.add(piece);
        }
        Collections.sort(lines, (a, b) -> Float.compare(a.middle, b.middle));
        return lines;
    }

    /** The median height of the strokes' points. */
    static float middle(List<PageWriting.Stroke> strokes) {
        int n = 0;
        for (PageWriting.Stroke s : strokes) n += s.size();
        float[] ys = new float[n];
        n = 0;
        for (PageWriting.Stroke s : strokes) for (int k = 0; k < s.size(); k++) ys[n++] = s.y[k];
        Arrays.sort(ys);
        return ys[n / 2];
    }
    static float median(List<Float> v) { float[] c = new float[v.size()]; for (int i = 0; i < c.length; i++) c[i] = v.get(i); Arrays.sort(c); return c[c.length / 2]; }

    private WritingLines() {}
}
