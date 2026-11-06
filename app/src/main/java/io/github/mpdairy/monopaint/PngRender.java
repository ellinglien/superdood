package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Export Page's PNG at Social or High Res ({@link ExportSize}; Page is the notebook's copy's, {@link NotebookExport#png}):
 * Social the page's greys shrunk by area; High Res drawn sharp a strip at a time and deflated as it comes ({@link Png}),
 * so a page twice the Manta's never needs a whole picture of ints. Pure Java.
 */
final class PngRender {
    private PngRender() { }

    /**
     * {@code page}'s (a stack flattened, {@link NotebookExport#flatten}) PNG at {@code size}, as Export Page writes it: its
     * guides when {@code guides}, its magnets by {@code tiles} (null: none), its page's {@code key} ({@link NotebookExport#KEY}); its print size the page's (the dpi scaled).
     */
    static byte[] png(ToneDocument.Snapshot page, ExportSize size, Magnets.Tiles tiles, boolean guides, long key) throws IOException {
        int[] out = size.of(page.width, page.height);
        int dpi = (int) Math.round(NotebookExport.DPI * (double) out[0] / page.width);
        if (size != ExportSize.HIGH) {
            byte[] greys = NotebookExport.greys(page, tiles, guides);
            if (out[0] != page.width || out[1] != page.height) greys = shrink(greys, page.width, page.height, out[0], out[1]);
            return NotebookExport.png(greys, out[0], out[1], key, dpi);
        }
        return high(page, true, tiles, guides, key, null);
    }
    /**
     * {@code page}'s High Res PNG ({@link #high}, twice the page at 600 dpi): its Vector Ink lines drawn sharp in it when
     * {@code lines}, else their layers as their pixels (the PDF's picture, {@link NotebookExport#vectorPng}, hides its
     * vector layers on {@code page} first and draws them over it), carrying {@code vectors} (null: none) as {@link NotebookExport#png} does.
     */
    static byte[] high(ToneDocument.Snapshot page, boolean lines, Magnets.Tiles tiles, boolean guides, long key, byte[] vectors) throws IOException {
        int k = ExportSize.HIGH_SCALE;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Png png = new Png(bytes, page.width * k, page.height * k, NotebookExport.DPI * k, key, vectors);
        high(page, lines, tiles, guides, png);
        png.finish();
        return bytes.toByteArray();
    }
    /** {@code page}'s High Res greys, as {@link #high(ToneDocument.Snapshot, boolean, Magnets.Tiles, boolean, long, byte[])} draws them, into {@code out} a strip at a time: the PNG's rows, or the Layers copy's merged image. */
    static void high(ToneDocument.Snapshot page, boolean lines, Magnets.Tiles tiles, boolean guides, Strips out) throws IOException {
        // The page as a ToneDocument: its kept strokes (its Replay log) and Vector Ink's lines, read in place.
        ToneDocument doc = new ToneDocument(page);
        int without = lines ? VectorInk.sharpLayers(doc, 0, null) : 0, w = page.width, h = page.height;
        PagePicture.Composite under = PagePicture.Composite.of(page, guides, without);
        byte[] raw = new byte[w * h];
        int[] row = new int[w];
        for (int y = 0; y < h; y++) { under.row(y, row); for (int x = 0; x < w; x++) raw[y * w + x] = (byte) row[x]; }
        byte[] shown = NotebookExport.greys(under, page, tiles);
        high(doc, ExportSize.HIGH_SCALE, without, raw, shown, out);
    }
    /** Where High Res's rows go as they are drawn. */
    interface Strips {
        /** The first {@code count} rows of {@code greys} (the picture's width each, the grey in the low byte). */
        void rows(int[] greys, int count) throws IOException;
    }
    /** Whether {@code page} holds a kept dry stroke High Res draws sharp ({@link SharpView#kept}); else at 2 × it is only its pixels doubled. */
    static boolean sharp(ToneDocument.Snapshot page) { return !SharpView.kept(new ToneDocument(page), new java.util.HashMap<>()).isEmpty(); }

    /** {@code greys} ({@code width × height}, a byte a pixel) shrunk to {@code w × h}: each pixel the mean of the page area under it. */
    static byte[] shrink(byte[] greys, int width, int height, int w, int h) {
        PsdWriter.Rows shrunk = shrunk((y, g, a) -> { System.arraycopy(greys, y * width, g, 0, width); java.util.Arrays.fill(a, (byte) 255); }, width, height, w, h);
        byte[] out = new byte[w * h], g = new byte[w], a = new byte[w];
        for (int oy = 0; oy < h; oy++) { shrunk.row(oy, g, a); System.arraycopy(g, 0, out, oy * w, w); }
        return out;
    }
    /**
     * {@code rows} (a {@code width × height} layer, greys and coverages) shrunk to {@code w × h} a row at a time (Social's
     * Layers copy, and its PNG): each pixel's coverage the mean of the page area under it, its grey the mean of the greys
     * there weighed by their coverage. Rows asked in order, each page row read about once.
     */
    static PsdWriter.Rows shrunk(PsdWriter.Rows rows, int width, int height, int w, int h) {
        double sx = (double) width / w, sy = (double) height / h;
        double[] inked = new double[width], covered = new double[width];
        byte[] g1 = new byte[width], a1 = new byte[width];
        int[] at = {-1};
        return (oy, g, a) -> {
            java.util.Arrays.fill(inked, 0); java.util.Arrays.fill(covered, 0);
            double y0 = oy * sy, y1 = (oy + 1) * sy;
            for (int y = (int) y0; y < Math.min(height, (int) Math.ceil(y1)); y++) {
                double wy = Math.min(y + 1, y1) - Math.max(y, y0);
                if (wy <= 0) continue;
                if (y != at[0]) { at[0] = y; java.util.Arrays.fill(g1, (byte) 0); java.util.Arrays.fill(a1, (byte) 0); rows.row(y, g1, a1); }
                for (int x = 0; x < width; x++) { double c = wy * (a1[x] & 255); covered[x] += c; inked[x] += c * (g1[x] & 255); }
            }
            for (int ox = 0; ox < w; ox++) {
                double x0 = ox * sx, x1 = (ox + 1) * sx, v = 0, c = 0;
                for (int x = (int) x0; x < Math.min(width, (int) Math.ceil(x1)); x++) {
                    double wx = Math.min(x + 1, x1) - Math.max(x, x0);
                    if (wx > 0) { v += wx * inked[x]; c += wx * covered[x]; }
                }
                a[ox] = (byte) Math.max(0, Math.min(255, (int) Math.round(c / ((x1 - x0) * (y1 - y0)))));
                g[ox] = c > 0 ? (byte) Math.max(0, Math.min(255, (int) Math.round(v / c))) : 0;
            }
        };
    }

    /** Each tone as the copies' grey ({@link DotPattern#exportGray}). */
    private static final int[] CALIBRATED = new int[256];
    static { for (int g = 0; g < 256; g++) CALIBRATED[g] = DotPattern.exportGray(g); }

    /**
     * High Res: {@code page} at {@code k} to a pixel as the zoomed screen shows it, in greys, into {@code png} (its size
     * the page's times {@code k}): each page pixel of {@code raw} (the page's composite, its tones uncalibrated, without
     * the layers {@code without} sets) laid k × k, sharp dry brushes' tiles over it ({@link SharpView#drawRow}, {@link
     * SharpView#over}), then those layers' Vector Ink lines ({@link VectorInk#shown}), then calibrated; where {@code
     * shown} (the copy's greys of that composite, magnets on) holds something over it, that pixel as it is there. A tile
     * row at a time: about {@code page.width × k × TILE × k} ints at once.
     */
    static void high(ToneDocument page, int k, int without, byte[] raw, byte[] shown, Strips png) throws IOException {
        int pw = page.width, ph = page.height, w = pw * k;
        SharpView view = new SharpView(rect -> { });
        VectorLines lines = without == 0 ? null : VectorInk.shown(page, without, null);
        if (lines != null && lines.isEmpty()) lines = null;
        int[] band = new int[w * SharpView.TILE * k];
        byte[] cover = lines == null ? null : new byte[band.length];
        for (int ty = 0; ty * SharpView.TILE < ph; ty++) {
            int top = ty * SharpView.TILE, h = Math.min(SharpView.TILE, ph - top) * k;
            view.drawRow(page, k, ty, without);
            for (int r = 0; r < h; r++) for (int c = 0, row = (top + r / k) * pw; c < w; c++) band[r * w + c] = raw[row + c / k] & 255;
            view.over(k, k, 0, 0, 0, top * k, w, h, band);
            if (lines != null) lines.over(k, 0, 0, 0, top * k, w, h, band, cover);
            for (int r = 0; r < h; r++) for (int c = 0, row = (top + r / k) * pw; c < w; c++) {
                int i = row + c / k, s = shown[i] & 255;
                band[r * w + c] = s == CALIBRATED[raw[i] & 255] ? CALIBRATED[band[r * w + c] & 255] : s;
            }
            png.rows(band, h);
        }
        view.clear();
    }

    /** An 8-bit grey PNG written row by row as the rows come (a page's pHYs and tEXt, {@link NotebookExport#about}), its rows deflated into IDAT chunks of 64 KB. */
    static final class Png implements Strips {
        private final OutputStream out;
        private final int width;
        private final Deflater deflater = new Deflater();
        private final DeflaterOutputStream rows;
        private final byte[] line;

        /** With {@code vectors} (null: none) in its {@link NotebookExport#VECTORS} chunk. */
        Png(OutputStream out, int width, int height, int dpi, long crc, byte[] vectors) throws IOException {
            this.out = out; this.width = width; line = new byte[1 + width];
            out.write(NotebookExport.SIGNATURE);
            byte[] header = new byte[13];
            put(header, 0, width); put(header, 4, height);
            header[8] = 8;  // 8-bit grey
            NotebookExport.chunk(out, "IHDR", header);
            NotebookExport.about(out, dpi, crc);
            if (vectors != null) NotebookExport.chunk(out, NotebookExport.VECTORS, vectors);
            rows = new DeflaterOutputStream(new Idat(out), deflater, 1 << 16);
        }
        /** The first {@code count} rows of {@code greys} ({@code width} each, the grey in the low byte), each filtered by its left neighbour (Sub). */
        @Override public void rows(int[] greys, int count) throws IOException {
            line[0] = 1;
            for (int r = 0; r < count; r++) {
                int was = 0;
                for (int x = 0, i = r * width; x < width; x++, i++) { int g = greys[i] & 255; line[x + 1] = (byte) (g - was); was = g; }
                rows.write(line, 0, line.length);
            }
        }
        /** The last IDAT and the end; {@code out} is left open. */
        void finish() throws IOException {
            rows.finish(); rows.flush(); deflater.end();
            NotebookExport.chunk(out, "IEND", new byte[0]);
        }
        private static void put(byte[] b, int at, int v) { b[at] = (byte) (v >>> 24); b[at + 1] = (byte) (v >>> 16); b[at + 2] = (byte) (v >>> 8); b[at + 3] = (byte) v; }
        /** The deflated rows as IDAT chunks, one each time its buffer fills, the rest at {@link #flush}. */
        private static final class Idat extends OutputStream {
            private final OutputStream out;
            private final byte[] buffer = new byte[1 << 16];
            private int used;
            Idat(OutputStream out) { this.out = out; }
            @Override public void write(int b) throws IOException { write(new byte[]{(byte) b}, 0, 1); }
            @Override public void write(byte[] b, int off, int len) throws IOException {
                while (len > 0) {
                    int n = Math.min(len, buffer.length - used);
                    System.arraycopy(b, off, buffer, used, n);
                    used += n; off += n; len -= n;
                    if (used == buffer.length) flush();
                }
            }
            @Override public void flush() throws IOException { if (used > 0) { NotebookExport.chunk(out, "IDAT", java.util.Arrays.copyOf(buffer, used)); used = 0; } }
        }
    }
}
