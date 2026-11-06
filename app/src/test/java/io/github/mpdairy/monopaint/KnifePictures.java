package io.github.mpdairy.monopaint;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Scrape and Wears Down (branch explore-knife), drawn by the app's engine. The Palette knife through a wet wash (the wet
 * paint's Soft wash just laid, and a Slow wash), through one settling, and over one dried: three strokes each, the pen
 * upright, at a usual lean and laid over. And a long pencil line wearing down, the pen's lean turned part way along it.
 * scripts/knife_pictures.sh.
 */
public class KnifePictures {
    static final int W = 520, H = 340;
    /** The wet paint's Soft wash (a Basic tool's) and the Palette knife. */
    static final String WASH = "soft-wash.M60i0000000007q_1wva200000000_0_0fk_7_8la0000000000000000nz0.t4mf73";
    static ModularBrush knife() {
        ModularBrush b = ModularCode.decode("water.M60i000000000at_1ulat00770000_4i0llzz_000_2g7ll0jsee00at_0000000000000000000nz0.20k");
        b.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.SCRAPE));
        b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.NONE));
        return b;
    }
    static ModularBrush wetWash() { return ModularCode.decode(WASH); }
    static ModularBrush slowWash() {
        ModularBrush b = ModularCode.decode(WASH);
        b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.DRYING));
        return b;
    }

    /** A sample path: x, y, pressure travel, ms, tilt x, tilt y (degrees). */
    static double[][] path(double x0, double y0, double x1, double y1, double p, double tiltX0, double tiltY0, double tiltX1, double tiltY1) {
        int n = (int) Math.max(2, Math.ceil(Math.hypot(x1 - x0, y1 - y0) / 2.5));
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) { double u = (double) i / n; o[i] = new double[]{x0 + (x1 - x0) * u, y0 + (y1 - y0) * u, p, i * 4, tiltX0 + (tiltX1 - tiltX0) * u, tiltY0 + (tiltY1 - tiltY0) * u}; }
        return o;
    }
    static void feed(DrawingStroke s, double[][] path) {
        for (int i = 0; i < path.length; i++) {
            double[] p = path[i];
            s.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), (float) p[4], (float) p[5], (long) p[3]);
            if (i % 3 == 2) s.present();
        }
        s.present();
        s.finish();
    }
    static void stroke(ToneDocument d, WatercolorSession s, String holder, ModularBrush b, double size, double darkness, double[][] path) {
        s.holding(holder);
        s.settleBefore(d, b); s.dry("stroke");
        feed(QuickStroke.start(d, b, size, darkness, s), path);
    }

    /** A wash over the middle of the page, rows of {@code wash}, left as wet as {@code ticks} of drying leave it (−1: dried). */
    static ToneDocument washed(WatercolorSession s, ModularBrush wash, int ticks) {
        ToneDocument d = InkWashLayers.blank(W, H);
        // A Slow wash is still wet only for its last stroke (the ones before it are laid settled as the next begins): one broad one.
        if (wash.choice(BrushPart.LIFE) == PartChoice.DRYING) stroke(d, s, HeldPaint.ANY, wash, 240, .6, path(50, 170, 470, 170, .7, 0, 0, 0, 0));
        else for (int y = 70; y <= 270; y += 40) stroke(d, s, HeldPaint.ANY, wash, 110, .6, path(50, y, 470, y, .7, 0, 0, 0, 0));
        if (ticks < 0) { WetPaintChecks.settle(s, d); s.settleNow(d); s.dry("dried"); }
        else for (int i = 0; i < ticks && s.tickDrying(d); i++) { }
        return d;
    }
    /** The knife down the page three times: upright, at a usual lean (40 degrees) and laid over (70). */
    static void knifed(ToneDocument d, WatercolorSession s, double size) {
        double[] tilts = {0, 40, 70};
        for (int k = 0; k < 3; k++) stroke(d, s, "KNIFE", knife(), size, .6, path(130 + 130 * k, 30, 130 + 130 * k + 20, 310, .75, tilts[k], 0, tilts[k], 0));
    }
    static int[][] grab(ToneDocument d) { int[][] g = new int[d.height][d.width]; for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) g[y][x] = d.compositeTone(x, y); return g; }

    static void sheet(File f, String title, String[] heads, String[] rows, int[][][][] panels, int w, int h) throws Exception {
        int gap = 12, label = 130, top = 46, cols = panels[0].length;
        BufferedImage img = new BufferedImage(label + cols * (w + gap), top + rows.length * (h + gap), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(150, 150, 150)); g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int r = 0; r < rows.length; r++) for (int c = 0; c < cols; c++) {
            int[][] p = panels[r][c];
            if (p == null) continue;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.getRaster().setSample(label + c * (w + gap) + x, top + r * (h + gap) + y, 0, p[y][x]);
        }
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString(title, 8, 24);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        for (int c = 0; c < cols; c++) g.drawString(heads[c], label + c * (w + gap) + 6, top - 6);
        for (int r = 0; r < rows.length; r++) {
            int y = top + r * (h + gap) + 22;
            for (String line : rows[r].split("\n")) { g.drawString(line, 6, y); y += 18; }
        }
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", f);
    }

    public static void main(String[] a) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(a.length > 0 ? a[0] : "/tmp/knife-pictures");
        out.mkdirs();
        knife(out);
        wear(out);
        System.out.println("wrote " + out);
    }

    static void knife(File out) throws Exception {
        // Wet paint: just laid, its water part dried back, at rest (damp), dried. A Slow wash: just laid, half, most of its drying, dry.
        int[] canvas = {0, 12, -2, -1}, slow = {0, 15, 25, -1};
        int[][][][] panels = new int[2][4][][];
        for (int c = 0; c < 4; c++) {
            WatercolorSession s = new WatercolorSession();
            ToneDocument d = canvas[c] == -2 ? restWash(s) : washed(s, wetWash(), canvas[c]);
            knifed(d, s, 60);
            panels[0][c] = grab(d);
            s = new WatercolorSession();
            d = washed(s, slowWash(), slow[c]);
            knifed(d, s, 60);
            panels[1][c] = grab(d);
        }
        sheet(new File(out, "knife-washes.png"), "Palette knife (Scrape): upright, 40 and 70 degrees lean, left to right in each",
                new String[]{"wet (just laid)", "settling", "settling, damp", "dry"},
                new String[]{"Wet paint\n(Soft wash)", "Slow wash\n(drying 3 s)"}, panels, W, H);
        // Close-ups of the middle stroke, twice the size.
        int[][][][] close = new int[2][4][][];
        for (int r = 0; r < 2; r++) for (int c = 0; c < 4; c++) close[r][c] = zoom(panels[r][c], 200, 100, 130, 120);
        sheet(new File(out, "knife-close.png"), "Palette knife, the 40 degree stroke, twice the size", new String[]{"wet", "settling", "damp", "dry"},
                new String[]{"Wet paint", "Slow wash"}, close, 260, 240);
    }
    /** Wet paint left until its water rests (damp). */
    static ToneDocument restWash(WatercolorSession s) {
        ToneDocument d = washed(s, wetWash(), 0);
        WetPaintChecks.settle(s, d);
        return d;
    }
    static int[][] zoom(int[][] p, int x0, int y0, int w, int h) {
        int[][] z = new int[2 * h][2 * w];
        for (int y = 0; y < 2 * h; y++) for (int x = 0; x < 2 * w; x++) z[y][x] = p[y0 + y / 2][x0 + x / 2];
        return z;
    }

    static void wear(File out) throws Exception {
        String[] names = {"HB pencil", "Soft charcoal", "Soft pastel", "Wax crayon"};
        int lw = 900, lh = 220;
        int[][][][] panels = new int[names.length][2][][];
        for (int r = 0; r < names.length; r++) for (int c = 0; c < 2; c++) {
            ModularBrush b = FloatChecks.preset(names[r]);
            if (c == 1) b.part(BrushPart.TIP).set(PartDial.WEAR, 1);
            ToneDocument d = InkWashLayers.blank(lw, lh);
            WatercolorSession s = new WatercolorSession();
            // A long gently wavy line, the pen leaning 45 degrees down the page (across the line), turned to lean along it
            // (to the right) from 60% along.
            int n = 340;
            double[][] p = new double[n + 1][];
            for (int i = 0; i <= n; i++) {
                double u = (double) i / n, turn = StrokeMath.smoothstep(.55, .65, u), ang = (1 - turn) * Math.PI / 2;
                p[i] = new double[]{40 + 820 * u, 110 + 35 * Math.sin(u * 7), .7, i * 4, 45 * Math.cos(ang), 45 * Math.sin(ang)};
            }
            stroke(d, s, "PENCIL", b, 7, 1, p);
            panels[r][c] = grab(d);
        }
        sheet(new File(out, "wear-line.png"), "Wears Down: a long line drawn left to right, the pen leaning across it, turned to lean along it from 60% (left: off, right: Wears Down 1)",
                new String[]{"off", "Wears Down 1"}, names, panels, lw, lh);
    }
}
