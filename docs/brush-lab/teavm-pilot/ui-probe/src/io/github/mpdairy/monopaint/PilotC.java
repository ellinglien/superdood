package io.github.mpdairy.monopaint;

import org.teavm.jso.JSExport;

/**
 * The browser app's probe (docs/superpowers/specs/2026-10-31-browser-app-design.md): the app's pure layout classes,
 * compiled by TeaVM, laying out a screen of any size in dp; main prints the same on the JVM, so the two compare line for line.
 */
public final class PilotC {
    /** Every layout's numbers for a {@code w × h} dp screen, one line. */
    @JSExport public static String layouts(int w, int h) {
        DrawLayout d = new DrawLayout(w, h);
        ToolsLayout t = new ToolsLayout(w, h);
        LabLayout l = new LabLayout(w, h);
        NotebookLayout n = new NotebookLayout(w, h, 1404.0 / 1872);
        StringBuilder s = new StringBuilder(w + "x" + h + " draw");
        for (int v : d.heights()) s.append(' ').append(v);
        s.append(" spare ").append(d.spare).append(" fit ").append(Math.round(d.fit * 1e6)).append(" page ").append(d.pageWidth())
         .append('x').append(d.canvasHeight()).append(" bar ").append(d.pageBar()).append(" trays ").append(d.trays)
         .append(" | tools ").append(t.columns).append('x').append(t.rows).append(' ').append(t.cardWidth).append('x').append(t.cardHeight)
         .append(" slots ").append(t.slotColumns).append(" chips ").append(t.chipRows).append(" test ").append(t.testWidth).append('x').append(t.testHeight)
         .append(" | lab ").append(l.column).append(' ').append(l.right).append(' ').append(l.words).append(" shelf ").append(l.shelfColumns).append('x').append(l.shelfRows)
         .append(" | book ").append(n.columns).append('x').append(n.rows).append(' ').append(n.tileWidth).append('x').append(n.tileHeight)
         .append(" page ").append(n.pageWidth).append('x').append(n.pageHeight).append(" visible ").append(n.visible)
         .append(" | words ").append(DrawLayout.pageWords(2, 24)).append(" / ").append(DrawLayout.sizeSaid(31));
        return s.toString();
    }

    /** Screens in dp: the Manta, the Nomad, iPad Pro 12.9 and iPad 10.9 (portrait, landscape), a phone, a laptop window. */
    static final int[][] SCREENS = {{1404, 1872}, {1404 * 160 / 300, 1872 * 160 / 300}, {1024, 1366}, {1366, 1024}, {820, 1180},
            {1180, 820}, {390, 844}, {844, 390}, {1440, 900}, {1920, 1080}};

    @JSExport public static String all() {
        StringBuilder s = new StringBuilder();
        for (int[] x : SCREENS) s.append(layouts(x[0], x[1])).append('\n');
        return s.toString();
    }

    public static void main(String[] args) { System.out.print(all()); }
}
