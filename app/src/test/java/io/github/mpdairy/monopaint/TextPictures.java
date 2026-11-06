package io.github.mpdairy.monopaint;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Text boxes and Make Ink drawn by the app's engine (scripts/text_pictures.sh): a few lines of journal text as a box,
 * then the same text made ink with plain ink, a Slow watercolour wash, a wet wash with Bleed full, Risograph, Halftone and
 * Photocopy Print Shading, charcoal, and two Laters (Letterpress, Foxing), each panel named under it. The page's own
 * pixels (the Manta's 300 dpi), the letters drawn by Java's rasterizer from the bundled iA Writer Duo ({@link
 * TextBoxChecks.DeskType}), the tablet's by Android's.
 */
public class TextPictures {
    static final int W = 760, H = 300, CAPTION = 34;
    static final String TEXT = "Sunday. Rain all morning, then the light came back over the harbour. Tea, one bad drawing.";

    public static void main(String[] a) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        TextBoxes.type = new TextBoxChecks.DeskType();
        File out = new File(a.length > 0 ? a[0] : "/tmp/text-pictures");
        out.mkdirs();
        ModularBrush ink = ScreenChecks.ink(), wash = LaterMediaPictures.slow("Soft wash");
        Object[][] panels = {
                {"Text box", null, 0.0, 0.0},
                {"Make Ink: ink", ink, 4.0, 1.0},
                {"Watercolour wash", wash, 40.0, .6},
                {"Wet wash, Bleed full", ScreenChecks.set(FloatChecks.preset("Wet blooms"), PartDial.BLEED, PartDial.BLEED.max), 40.0, .6},
                {"Risograph", ScreenChecks.screened(ink, ScreenChecks.RISO), 4.0, .85},
                {"Halftone", ScreenChecks.screened(ink, 1), 4.0, .6},
                {"Photocopy", ScreenChecks.screened(ink, ScreenChecks.PHOTOCOPY), 4.0, .9},
                {"Charcoal", FloatChecks.preset("Soft charcoal"), 10.0, 1.0},
                {"Letterpress", LaterMediaPictures.with(FloatChecks.preset("Swell brush"), PartChoice.LATER_PRESS, -1), 20.0, 1.0},
                {"Foxing", LaterMediaPictures.with(FloatChecks.preset("Fountain pen"), PartChoice.LATER_FOX, -1), 8.0, 1.0},
        };
        int cols = 2, rows = (panels.length + cols - 1) / cols, gap = 12;
        BufferedImage sheet = new BufferedImage(cols * W + (cols + 1) * gap, rows * (H + CAPTION) + (rows + 1) * gap, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(150, 150, 150)); g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ia_writer_duo_bold.ttf")).deriveFont(20f));
        for (int k = 0; k < panels.length; k++) {
            Object[] p = panels[k];
            int[][] tones = draw((ModularBrush) p[1], (Double) p[2], (Double) p[3]);
            int x0 = gap + (k % cols) * (W + gap), y0 = gap + (k / cols) * (H + CAPTION + gap);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) sheet.getRaster().setSample(x0 + x, y0 + y, 0, tones[y][x]);
            g.setColor(Color.WHITE); g.fillRect(x0, y0 + H, W, CAPTION);
            g.setColor(Color.BLACK); g.drawString((String) p[0], x0 + 12, y0 + H + 24);
        }
        g.dispose();
        File f = new File(out, "text-make-ink.png");
        javax.imageio.ImageIO.write(sheet, "png", f);
        System.out.println("wrote " + f);
    }

    /** The journal box on a page; with {@code brush}, made ink at {@code size} and {@code darkness}, dried and its Later run on to the end; the page as its copies show it. */
    static int[][] draw(ModularBrush brush, double size, double darkness) {
        long t0 = 50_000_000;
        FadingInk.clock = () -> t0;
        ToneDocument d = InkWashLayers.blank(W, H);
        d.setTexts(d.texts().with(new TextBoxes.Box(TEXT, 40, 24, 690, TextBoxes.MEDIUM, false, TextBoxes.LEFT)));
        if (brush != null) {
            WatercolorSession s = new WatercolorSession();
            TextBoxChecks.makeInk(d, 0, brush, size, darkness, s);
            s.settleNow(d);
            d.fade(t0 + FadingInk.MOST_MS * 2L, null);
        }
        FadingInk.clock = System::currentTimeMillis;
        PagePicture.Composite c = new PagePicture.Composite(d.layerSnapshot());
        int[][] o = new int[H][W];
        for (int y = 0; y < H; y++) c.row(y, o[y]);
        return o;
    }
}
