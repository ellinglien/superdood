package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/** A book's page, its layers only (no template guides, no ghost), as the Manta's dots, 1-bit, cropped with a margin. */
public final class SplashPage {
    public static void main(String[] a) throws Exception {
        DrawingBook book;
        try (InputStream in = new FileInputStream(a[0])) { book = BookCodec.read(in); }
        book.select(Integer.parseInt(a[1]) - 1);
        ToneDocument page = book.current();
        page.placeGuides(null);
        int w = page.width, h = page.height, margin = Integer.parseInt(a[3]);
        int[] px = new int[w * h];
        page.render(px, 0, 0, w, h);
        int l = w, t = h, r = -1, b = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if ((px[y * w + x] & 0xff) < 128) {
            l = Math.min(l, x); t = Math.min(t, y); r = Math.max(r, x); b = Math.max(b, y);
        }
        l = Math.max(0, l - margin); t = Math.max(0, t - margin); r = Math.min(w - 1, r + margin); b = Math.min(h - 1, b + margin);
        BufferedImage img = new BufferedImage(r - l + 1, b - t + 1, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) img.setRGB(x - l, y - t, (px[y * w + x] & 0xff) < 128 ? 0xff000000 : 0xffffffff);
        ImageIO.write(img, "png", new File(a[2]));
        System.out.println("page " + a[1] + " " + w + "x" + h + " -> crop " + l + "," + t + " " + img.getWidth() + "x" + img.getHeight()
                + " layers " + page.layerCount());
    }
}
