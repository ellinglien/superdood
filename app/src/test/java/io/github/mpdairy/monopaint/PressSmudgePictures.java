package io.github.mpdairy.monopaint;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * The smudge tool's two (the owner, 2026-10-30), drawn by the app's engine: Blend and Water, a light stroke and a firm
 * one, over hatching and over a wash, each a page of its own; beside them the older Basic tools each end stands for
 * (the Blend stump and the Stump; the Water brush and the Damp tissue). Composites at full grey, labelled.
 * scripts/presssmudge_pictures.sh.
 */
public class PressSmudgePictures {
    static final int W = 420, H = 300;
    /** Pressure travel: the owner's light strokes (HoverNib.LIGHT), a usual press (WildSample.TYPICAL_PRESS), a hard one (docs/TILT_INPUT.md). */
    static final double LIGHT = .12, USUAL = WildSample.TYPICAL_PRESS, FIRM = .95;
    static ModularBrush preset(String name) { return FloatChecks.preset(name); }

    /** Hatching on the tool's own layer, drawn as a hand would: Soft charcoal for Blend, a small Soft wash for Water; dried. */
    static ToneDocument hatched(boolean wash) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        ModularBrush b = preset(wash ? "Soft wash" : "Soft charcoal");
        for (int x = 70; x <= 330; x += 26) WetSmudgeChecks.stroke(d, s, HeldPaint.ANY, b, wash ? 14 : 5, 1, FloatChecks.line(x, 40, x + 30, 260, .7));
        s.settleNow(d); s.dry("dried");
        return d;
    }
    /** A wash over the left of the page, Soft wash rows, dried: the stroke runs out of it onto paper. */
    static ToneDocument washed() {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        for (int y = 50; y <= 250; y += 36) WetSmudgeChecks.stroke(d, s, HeldPaint.ANY, preset("Soft wash"), 90, .5, FloatChecks.line(40, y, 230, y, .7));
        s.settleNow(d); s.dry("dried");
        return d;
    }
    /** {@code tool} across a fresh {@code page}, left to right: across the hatching, or out of the wash onto paper; dried after. */
    static ToneDocument stroked(java.util.function.Supplier<ToneDocument> page, boolean hatching, ModularBrush tool, double travel) {
        ToneDocument d = page.get();
        WatercolorSession s = new WatercolorSession();
        double[][] path = hatching ? FloatChecks.line(40, 150, 300, 150, travel) : FloatChecks.line(120, 150, 380, 150, travel);
        WetSmudgeChecks.stroke(d, s, "SMUDGE", tool, 48, .5, path);
        while (s.tickDrying(d)) { }
        return d;
    }
    static int[][] grab(ToneDocument d) { int[][] g = new int[d.height][d.width]; for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) g[y][x] = d.compositeTone(x, y); return g; }

    /** One sheet: a row a tool (its name, then the page before, light, firm), labelled. */
    static void sheet(File f, String title, String[] rows, int[][][][] panels) throws Exception {
        int gap = 12, label = 120, top = 46, cols = panels[0].length;
        BufferedImage img = new BufferedImage(label + cols * (W + gap), top + rows.length * (H + gap), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(150, 150, 150)); g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int r = 0; r < rows.length; r++) for (int c = 0; c < cols; c++) {
            int[][] p = panels[r][c];
            if (p == null) continue;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) img.getRaster().setSample(label + c * (W + gap) + x, top + r * (H + gap) + y, 0, p[y][x]);
        }
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString(title, 8, 24);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        String[] heads = {"before", "light (" + LIGHT + ")", "usual (" + USUAL + ")", "firm (" + FIRM + ")"};
        for (int c = 0; c < cols; c++) g.drawString(heads[c], label + c * (W + gap) + 6, top - 6);
        for (int r = 0; r < rows.length; r++) {
            int y = top + r * (H + gap) + 22;
            for (String line : rows[r].split("\n")) { g.drawString(line, 6, y); y += 18; }
        }
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", f);
    }

    public static void main(String[] a) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(a.length > 0 ? a[0] : "/tmp/presssmudge-pictures");
        out.mkdirs();
        // Blend, over charcoal hatching and over a wash: the Blend stump softens in place, the Stump pushes.
        tool(out, "blend", "Blend", new String[]{"Blend", "Blend stump", "Stump"});
        tool(out, "water", "Water", new String[]{"Water", "Water brush", "Damp tissue"});
        System.out.println("wrote " + out);
    }
    static void tool(File out, String file, String name, String[] tools) throws Exception {
        boolean water = name.equals("Water");
        for (boolean hatching : new boolean[]{true, false}) {
            java.util.function.Supplier<ToneDocument> page = hatching ? () -> hatched(water) : PressSmudgePictures::washed;
            int[][] before = grab(page.get());
            int[][][][] panels = new int[tools.length][][][];
            String[] rows = new String[tools.length];
            for (int r = 0; r < tools.length; r++) {
                ModularBrush b = preset(tools[r]);
                // The older tools are each one end: the row shows what the new one should be near at that end.
                panels[r] = new int[][][]{r == 0 ? before : null, grab(stroked(page, hatching, b, LIGHT)), grab(stroked(page, hatching, b, USUAL)), grab(stroked(page, hatching, b, FIRM))};
                rows[r] = tools[r] + (r == 0 ? "\n(new)" : "\n(older, for\nreference)") + (!hatching && tools[r].equals("Stump") ? "\n\nsmears pencil\nand charcoal\nonly, not\nwashes" : "");
            }
            sheet(new File(out, file + "-" + (hatching ? "hatching" : "wash") + ".png"), name + (hatching ? " over hatching" : " out of a wash") + ": light, usual and firm strokes", rows, panels);
        }
    }
}
