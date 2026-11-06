package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A Photoshop file (PSD, version 1; Adobe's "Photoshop File Formats Specification") of grey layers, for a page's Layers
 * copy ({@link PageExport}; the owner, 2026-10-28: "possibly layers for photoshop? ie. the diff media layers"): Grayscale,
 * 8 bits, each layer a grey channel and its transparency, Normal at full opacity, bottom first, every channel packed
 * (PackBits, a row at a time); its size in dots an inch; and the merged image (what Photoshop shows before it reads the
 * layers, and what most viewers show) as the PNG copy's greys. Pure Java, checked on the desktop ({@code PageExportChecks}).
 */
final class PsdWriter {
    /** A layer row by row: row {@code y}'s greys and coverages (0 clear, 255 opaque) into {@code grey} and {@code alpha}. */
    interface Rows { void row(int y, byte[] grey, byte[] alpha); }

    /** A layer as the file holds it: its name, its two channels packed, and whether any pixel of it shows. */
    static final class Layer {
        final String name; final Channel grey, alpha; final boolean shows;
        private Layer(String name, Channel grey, Channel alpha, boolean shows) { this.name = name; this.grey = grey; this.alpha = alpha; this.shows = shows; }
    }
    /** {@code rows}, {@code width × height}, packed as layer {@code name}: read once, a row at a time, so a page's layer is never held whole. */
    static Layer layer(String name, int width, int height, Rows rows) {
        Channel grey = new Channel(height), alpha = new Channel(height);
        byte[] g = new byte[width], a = new byte[width];
        boolean shows = false;
        for (int y = 0; y < height; y++) {
            java.util.Arrays.fill(g, (byte) 0); java.util.Arrays.fill(a, (byte) 0);
            rows.row(y, g, a);
            for (int x = 0; x < width && !shows; x++) shows = a[x] != 0;
            grey.row(g); alpha.row(a);
        }
        return new Layer(name, grey, alpha, shows);
    }

    /**
     * {@code layers} (bottom first) over the merged image {@code merged} (packed as it was drawn), {@code width × height}
     * at {@code dpi}, streamed: nothing besides the packed channels is held.
     */
    static void write(OutputStream output, int width, int height, int dpi, List<Layer> layers, Channel merged) throws IOException {
        DataOutputStream out = new DataOutputStream(output);
        // Header: one channel (grey) at 8 bits, mode 1, Grayscale.
        out.writeBytes("8BPS"); out.writeShort(1); out.write(new byte[6]);
        out.writeShort(1); out.writeInt(height); out.writeInt(width); out.writeShort(8); out.writeShort(1);
        out.writeInt(0); // no colour mode data
        // Image resources: ResolutionInfo (1005), dots an inch as 16.16 fixed, shown in inches.
        ByteArrayOutputStream resolution = new ByteArrayOutputStream();
        DataOutputStream r = new DataOutputStream(resolution);
        for (int i = 0; i < 2; i++) { r.writeInt(dpi << 16); r.writeShort(1); r.writeShort(1); }
        out.writeInt(4 + 2 + 2 + 4 + resolution.size());
        out.writeBytes("8BIM"); out.writeShort(1005); out.writeShort(0); out.writeInt(resolution.size()); resolution.writeTo(out);
        // Layer and mask information: the layers' records, then their channels, in the same order.
        ByteArrayOutputStream records = new ByteArrayOutputStream();
        DataOutputStream l = new DataOutputStream(records);
        l.writeShort(layers.size());
        long channels = 0;
        for (Layer layer : layers) {
            l.writeInt(0); l.writeInt(0); l.writeInt(height); l.writeInt(width);
            l.writeShort(2);
            l.writeShort(-1); l.writeInt(layer.alpha.size());
            l.writeShort(0); l.writeInt(layer.grey.size());
            channels += layer.alpha.size() + layer.grey.size();
            l.writeBytes("8BIM"); l.writeBytes("norm");
            l.writeByte(255); l.writeByte(0); l.writeByte(0); l.writeByte(0);
            byte[] name = layer.name.getBytes(StandardCharsets.ISO_8859_1);
            int nameLength = Math.min(255, name.length), padded = (nameLength + 1 + 3) / 4 * 4;
            // No mask; blending ranges: the grey composite's and the grey channel's, each the whole range.
            l.writeInt(4 + 4 + 16 + padded);
            l.writeInt(0);
            l.writeInt(16); for (int i = 0; i < 4; i++) l.writeInt(0x0000ffff);
            l.writeByte(nameLength); l.write(name, 0, nameLength); l.write(new byte[padded - 1 - nameLength]);
        }
        long info = records.size() + channels, pad = (4 - info % 4) % 4;
        out.writeInt((int) (4 + info + pad + 4));
        out.writeInt((int) (info + pad)); records.writeTo(out);
        for (Layer layer : layers) { layer.alpha.writeTo(out); layer.grey.writeTo(out); }
        out.write(new byte[(int) pad]);
        out.writeInt(0); // no global layer mask
        // The merged image: PackBits, every row's length first.
        merged.writeTo(out);
        out.flush();
    }

    /** A channel packed as its rows come, top first: compression 1 (PackBits), each row's packed length, then the rows. */
    static final class Channel {
        private final int[] lengths;
        private int rows;
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();
        Channel(int height) { lengths = new int[height]; }
        /** The next row. */
        void row(byte[] row) { int before = data.size(); packBits(row, data); lengths[rows++] = data.size() - before; }
        /** Its bytes in the file. */
        int size() { return 2 + 2 * lengths.length + data.size(); }
        void writeTo(OutputStream out) throws IOException {
            out.write(0); out.write(1);
            byte[] n = new byte[2 * lengths.length];
            for (int i = 0; i < lengths.length; i++) { n[2 * i] = (byte) (lengths[i] >> 8); n[2 * i + 1] = (byte) lengths[i]; }
            out.write(n);
            data.writeTo(out);
        }
    }
    /** {@code row} PackBits-encoded into {@code out}: runs of 2 to 128 alike as (1 − n, byte), the rest as (n − 1, n bytes). */
    static void packBits(byte[] row, ByteArrayOutputStream out) {
        int n = row.length, i = 0;
        while (i < n) {
            int run = 1;
            while (i + run < n && run < 128 && row[i + run] == row[i]) run++;
            if (run >= 2) { out.write(1 - run); out.write(row[i]); i += run; continue; }
            // Literal bytes up to the next run of three (a run of two inside literals saves nothing).
            int start = i;
            while (i < n && i - start < 128 && !(i + 2 < n && row[i] == row[i + 1] && row[i] == row[i + 2])) i++;
            if (i == start) i++;
            out.write(i - start - 1); out.write(row, start, i - start);
        }
    }

    private PsdWriter() {}
}
