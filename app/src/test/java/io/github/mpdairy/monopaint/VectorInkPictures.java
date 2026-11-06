package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Not a check: the same hand's stroke at 400% as the zoomed view shows it, side by side: Steady line's pixels scaled up
 * (nearest, then the screen's dots, as {@code viewport_bitmap.c}) and Vector Ink's lines drawn at that scale (the page
 * without them scaled, the lines over it, then the dots, as {@code ViewportBitmap.sharpen}). {@code <out.png>}.
 */
public final class VectorInkPictures {
    static final int W = 200, H = 120, X = 40, Y = 30, CW = 90, CH = 60, ZOOM = 4;
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        float[][] hand = VectorInkChecks.hand(40, 21);
        WatercolorSession s = new WatercolorSession();
        ToneDocument pixels = InkWashLayers.blank(W, H), vector = InkWashLayers.blank(W, H);
        VectorInkChecks.draw(pixels, s, VectorInkChecks.preset("Steady line"), 5, 1, hand);
        VectorInkChecks.draw(vector, s, VectorInkChecks.vector(), 5, 1, hand);
        int cw = CW * ZOOM, ch = CH * ZOOM, gap = 16;
        BufferedImage out = new BufferedImage(2 * cw + gap, ch, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < ch; y++) for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, 0x808080);
        int[] tones = new int[W * H];
        pixels.compositeTones(0, 0, W, H, tones, 0, W, 0);
        int[] left = new int[cw * ch];
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) left[y * cw + x] = tones[(Y + y / ZOOM) * W + X + x / ZOOM] & 255;
        // Vector Ink: the page under its layer (here bare paper), the lines over it at 4×.
        int[] right = new int[cw * ch];
        java.util.Arrays.fill(right, 255);
        vector.lines(InkWashLayers.INK).over(ZOOM, -X * ZOOM, -Y * ZOOM, 0, 0, cw, ch, right, new byte[cw * ch]);
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) {
            out.setRGB(x, y, DotPattern.pixel(left[y * cw + x] & 255, x, y) & 0xffffff);
            out.setRGB(cw + gap + x, y, DotPattern.pixel(right[y * cw + x] & 255, x, y) & 0xffffff);
        }
        javax.imageio.ImageIO.write(out, "png", new File(args[0]));
        System.out.println("Wrote " + args[0] + ": Steady line's pixels at 400% (left), Vector ink at 400% (right)");
    }
}
