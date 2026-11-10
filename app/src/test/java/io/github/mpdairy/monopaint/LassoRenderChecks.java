package io.github.mpdairy.monopaint;

/** Lasso to Superdood ({@link LassoRender}): a note's strokes drawn again with the first toolbar's brushes, the marks cropped round them. */
public final class LassoRenderChecks {
    public static void main(String[] args) throws java.io.IOException {
        Assets.Source folder = Assets.folder(Assets.FOLDER);
        Assets.install(folder::open);
        float[][] strokes = {{100, 200, 160, 210, 220, 230, 300, 260}, {120, 300, 280, 320}}, travels = {{.3f, .6f, .8f, .5f}, {.7f, .7f}};
        StringBuilder said = new StringBuilder();
        for (String[] slot : ToolSlots.FIRST) {
            ModularBrush b = BrushImport.decode(slot[2]);
            check(b != null, slot[1] + "'s code reads");
            long start = System.nanoTime();
            LassoRender.Marks m = LassoRender.draw(1404, 1872, strokes, travels, b, Double.parseDouble(slot[3]), Double.parseDouble(slot[4]));
            long ms = (System.nanoTime() - start) / 1_000_000;
            check(m != null, slot[1] + " draws something");
            check(m.tones.length == m.w * m.h, slot[1] + "'s tones fill its box");
            // The box holds the strokes (a brush may spread past them, never miss them) and stays near them.
            check(m.x <= 100 && m.y <= 200 && m.x + m.w >= 280 && m.y + m.h >= 260, slot[1] + "'s box holds the strokes: " + m.x + "," + m.y + " " + m.w + "x" + m.h);
            check(m.x > 100 - 400 && m.x + m.w < 300 + 400, slot[1] + "'s box stays near them");
            int marked = 0;
            for (byte t : m.tones) if ((t & 255) < 255) marked++;
            check(marked > 0, slot[1] + " leaves marks");
            said.append(slot[1]).append(' ').append(ms).append(" ms; ");
        }
        check(LassoRender.draw(1404, 1872, new float[][]{{}}, new float[][]{{}}, BrushImport.decode(ToolSlots.FIRST[0][2]), 6, 1) == null, "no points draw nothing");
        // Highlighter fill: a 200 px square lasso round the strokes, filled; the strokes left clear 3 px each side.
        check(LassoRender.inside(1404, 1872, new float[]{10, 10, 30, 10, 30, 30, 10, 30}).area == 400, "a 20 px square lasso holds 400 px");
        check(LassoRender.inside(1404, 1872, new float[]{10, 10, 30, 10}) == null, "a lasso of two points holds nothing");
        float[] lasso = {80, 180, 320, 180, 320, 340, 80, 340};
        for (String[] slot : ToolSlots.FIRST) {
            long start = System.nanoTime();
            LassoRender.Marks m = LassoRender.fill(1404, 1872, lasso, strokes, new float[]{3, 3}, BrushImport.decode(slot[2]), Double.parseDouble(slot[3]), Double.parseDouble(slot[4]));
            long ms = (System.nanoTime() - start) / 1_000_000;
            check(m != null, slot[1] + " fills");
            check(m.x >= 78 && m.y >= 178 && m.x + m.w <= 323 && m.y + m.h <= 343, slot[1] + "'s fill stays in the lasso (2 px of soft edge): " + m.x + "," + m.y + " " + m.w + "x" + m.h);
            check((m.tones[(210 - m.y) * m.w + 160 - m.x] & 255) == 255, slot[1] + " leaves the stroke clear");
            int marked = 0;
            for (byte t : m.tones) if ((t & 255) < 255) marked++;
            check(marked > 240 * 160 / 2, slot[1] + " fills most of the lasso: " + marked);
            said.append(slot[1]).append(" fill ").append(ms).append(" ms; ");
        }
        System.out.println("PASS: a note's lassoed strokes draw again with each first-toolbar brush, cropped round its marks (" + said + ")");
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
