package io.github.mpdairy.monopaint;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * The new print and age tools (explore-presets: Riso, Letterpress, Cracks) drawn by the app's engine
 * (scripts/new_presets_pictures.sh): per tool, a few words of looped handwriting and a small drawing (a house, its roof
 * scribbled in, and a sun) at a journal's size ({@link #SIZES}), then the journal text made ink with it
 * ({@link TextPictures}), each after its Later has run to the end. The page's own pixels (the Manta's 300 dpi).
 */
public class NewPresetPictures {
    static final String[] TOOLS = {"Riso", "Letterpress", "Cracking paint"};
    /** The size each is drawn at, px: as the size slider might be set to write with it. */
    static final double[] SIZES = {14, 8, 12};
    static final int W = TextPictures.W, H = TextPictures.H, CAPTION = TextPictures.CAPTION;

    public static void main(String[] a) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        TextBoxes.type = new TextBoxChecks.DeskType();
        File out = new File(a.length > 0 ? a[0] : "/tmp/new-presets");
        out.mkdirs();
        ModularBrush[] brushes = new ModularBrush[TOOLS.length];
        for (int k = 0; k < TOOLS.length; k++) brushes[k] = FloatChecks.preset(TOOLS[k]);
        sheet(new File(out, "new-presets.png"), TOOLS, brushes, SIZES);
    }

    static void sheet(File f, String[] names, ModularBrush[] brushes, double[] sizes) throws Exception {
        int cols = 2, gap = 12;
        BufferedImage sheet = new BufferedImage(cols * W + (cols + 1) * gap, names.length * (H + CAPTION + gap) + gap, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(150, 150, 150)); g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ia_writer_duo_bold.ttf")).deriveFont(20f));
        for (int k = 0; k < names.length; k++) {
            int[][][] panels = {drawn(brushes[k], sizes[k]), TextPictures.draw(brushes[k], sizes[k], 1)};
            String[] captions = {names[k] + ", " + ToolSwatches.mm(sizes[k]), names[k] + ": Make Ink"};
            for (int c = 0; c < cols; c++) {
                int x0 = gap + c * (W + gap), y0 = gap + k * (H + CAPTION + gap);
                for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) sheet.getRaster().setSample(x0 + x, y0 + y, 0, panels[c][y][x]);
                g.setColor(Color.WHITE); g.fillRect(x0, y0 + H, W, CAPTION);
                g.setColor(Color.BLACK); g.drawString(captions[c], x0 + 12, y0 + H + 24);
            }
        }
        g.dispose();
        javax.imageio.ImageIO.write(sheet, "png", f);
        System.out.println("wrote " + f);
    }

    /** Handwriting and a small drawing with {@code b} at {@code size}, black, settled and its Later run to the end. */
    static int[][] drawn(ModularBrush b, double size) {
        long t0 = 50_000_000;
        FadingInk.clock = () -> t0;
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        // Two lines of words in looped cursive ("lleelle" loops, a prolate cycloid), the loops as wide as the brush needs.
        double step = 14 + 1.6 * size;
        for (int line = 0; line < 2; line++) {
            double y0 = 75 + line * (60 + 2.5 * size);
            for (int w = 0, xw = 30; w < 3; w++) {
                int loops = 3 + (w + line) % 3;
                final double left = xw, base = y0;
                final int word = w;
                KnifePictures.stroke(d, s, HeldPaint.ANY, b, size, 1, ReprintPictures.curve(t -> {
                    double u = t * loops;
                    return left + u * step - .45 * step * Math.sin(u * 2 * Math.PI);
                }, t -> {
                    double u = t * loops, tall = ((int) u + word) % 3 == 1 ? 1.9 : 1;
                    return base - (20 + size) * tall * (.5 - .5 * Math.cos(u * 2 * Math.PI));
                }, .7));
                xw += (int) (loops * step + 30);
            }
        }
        // A house: walls, a roof scribbled in, a door; and a sun.
        double hx = 560, hy = 280;
        KnifePictures.stroke(d, s, HeldPaint.ANY, b, size, 1, poly(.7, hx, hy - 80, hx, hy, hx + 120, hy, hx + 120, hy - 80));
        KnifePictures.stroke(d, s, HeldPaint.ANY, b, size, 1, poly(.8, hx - 10, hy - 78, hx + 60, hy - 135, hx + 130, hy - 78, hx - 10, hy - 78));
        KnifePictures.stroke(d, s, HeldPaint.ANY, b, size, 1, ReprintPictures.curve(t -> {
            double v = t * 7 % 1, half = 55 * (1 - t);
            return hx + 60 + (v < .5 ? -half + 4 * half * v : 3 * half - 4 * half * v);
        }, t -> hy - 85 - 42 * t, .9));
        KnifePictures.stroke(d, s, HeldPaint.ANY, b, size, 1, poly(.7, hx + 45, hy, hx + 45, hy - 45, hx + 75, hy - 45, hx + 75, hy));
        KnifePictures.stroke(d, s, HeldPaint.ANY, b, size, 1, ReprintPictures.curve(t -> 690 + 30 * Math.cos(t * 2.1 * Math.PI), t -> 55 + 30 * Math.sin(t * 2.1 * Math.PI), .6));
        WetPaintChecks.settle(s, d); s.settleNow(d);
        d.fade(t0 + FadingInk.MOST_MS * 2L, null);
        FadingInk.clock = System::currentTimeMillis;
        PagePicture.Composite c = new PagePicture.Composite(d.layerSnapshot());
        int[][] o = new int[H][W];
        for (int y = 0; y < H; y++) c.row(y, o[y]);
        return o;
    }
    /** A path along the corners {@code xy} at pressure {@code p}. */
    static double[][] poly(double p, double... xy) {
        int legs = xy.length / 2 - 1;
        return ReprintPictures.curve(t -> along(xy, 0, legs, t), t -> along(xy, 1, legs, t), p);
    }
    static double along(double[] xy, int axis, int legs, double t) {
        int k = Math.min(legs - 1, (int) (t * legs));
        double u = t * legs - k;
        return xy[2 * k + axis] + (xy[2 * k + 2 + axis] - xy[2 * k + axis]) * u;
    }
}
