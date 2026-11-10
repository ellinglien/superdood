package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Second Ink, the page file ({@link DocumentCodec}'s INKS section): ink shares round-trip on a plane, on a base layer and
 * beside Vector Ink; a page without ink writes as it always did; a section of the wrong length, bits, or a second one is
 * refused. {@code older-write <out>} / {@code older-page -} serve the older-reader check in scripts/test_inkwash.sh.
 */
public final class SecondInkFileChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    static final int W = 200, H = 140, PIXELS = W * H, INKS = 0x494e4b53, END = 0x454e4421;
    /** CRC of the inflated body of {@link #plain()} written before this section existed (git 40a6c59c). */
    static final long PLAIN_BODY = 1168365793L;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("older-write")) { java.nio.file.Files.write(java.nio.file.Paths.get(args[1]), write(older().layerSnapshot())); return; }
        if (args.length > 0 && args[0].equals("older-page")) { System.out.println(olderHash(DocumentCodec.read(new ByteArrayInputStream(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[1])))))); return; }
        roundTrips(); plainBytes(); refused();
        System.out.println("PASS: the page file keeps second ink (plane, base layer, Vector Ink's layer), writes a page without it as before, and refuses a bad section");
    }

    // ---- pages ----
    static ToneDocument plain() {
        ToneDocument d = new ToneDocument(W, H);
        SecondInkChecks.wash(d, -1, 20, 20, 100, 40, 128);
        SecondInkChecks.wash(d, -1, 60, 30, 140, 90, 60);
        return d;
    }
    static ToneDocument inked() {
        ToneDocument d = new ToneDocument(W, H);
        SecondInkChecks.wash(d, 255, 20, 20, 100, 40, 128);
        SecondInkChecks.wash(d, 100, 60, 30, 140, 90, 60);
        return d;
    }
    static ToneDocument older() {
        ToneDocument d = new ToneDocument(400, 240);
        SecondInkChecks.wash(d, 255, 20, 20, 300, 80, 128);
        return d;
    }
    static int olderHash(ToneDocument d) {
        int[] page = new int[400 * 240];
        for (int y = 0; y < 240; y++) for (int x = 0; x < 400; x++) page[y * 400 + x] = d.compositeTone(x, y);
        return Arrays.hashCode(page);
    }
    static byte[] fill(int value, int from, int to) { byte[] b = new byte[PIXELS]; Arrays.fill(b, from, to, (byte) value); return b; }
    static byte[] write(ToneDocument.Snapshot s) throws IOException { ByteArrayOutputStream o = new ByteArrayOutputStream(); DocumentCodec.write(o, s); return o.toByteArray(); }
    static ToneDocument read(byte[] b) throws IOException { return DocumentCodec.read(new ByteArrayInputStream(b)); }
    /** The same arrays, nothing but zeros as none (what a file reads back as). */
    static byte[] norm(byte[] ink) { if (ink != null) for (byte b : ink) if (b != 0) return ink; return null; }
    static boolean same(ToneDocument.Layer a, ToneDocument.Layer b) {
        if (!Arrays.equals(norm(a.ink), norm(b.ink))) return false;
        for (int p = 0; p < ToneDocument.PLANES; p++) {
            ToneDocument.Layer x = a.plane(p), y = b.plane(p);
            if ((x == null) != (y == null) || x != null && !Arrays.equals(norm(x.ink), norm(y.ink))) return false;
        }
        return true;
    }
    static boolean same(ToneDocument.Snapshot a, ToneDocument.Snapshot b) {
        if (a.layers.size() != b.layers.size()) return false;
        for (int i = 0; i < a.layers.size(); i++) if (!same(a.layers.get(i), b.layers.get(i))) return false;
        return true;
    }

    // ---- the payload of a file, so a section can be put in by hand ----
    static byte[] inflated(byte[] file) throws IOException {
        InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(file, 12, file.length - 12), new Inflater());
        return in.readAllBytes();
    }
    /** The file with {@code sections} (each tag, length, bytes) put before its end tag, its checksum made again. */
    static byte[] with(byte[] file, byte[]... sections) throws IOException {
        byte[] body = inflated(file);
        int end = body.length - 8 - 4;
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(body, 0, end);
        for (byte[] s : sections) o.write(s);
        o.write(body, end, 4);
        CRC32 crc = new CRC32(); crc.update(o.toByteArray());
        new DataOutputStream(o).writeLong(crc.getValue());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(file, 0, 12);
        DeflaterOutputStream d = new DeflaterOutputStream(out, new Deflater());
        d.write(o.toByteArray()); d.finish();
        return out.toByteArray();
    }
    static byte[] section(int declared, byte[] bytes) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(o); d.writeInt(INKS); d.writeInt(declared); d.write(bytes);
        return o.toByteArray();
    }
    static byte[] section(byte[] bytes) throws IOException { return section(bytes.length, bytes); }
    static byte[] masks(int count, int mask, byte[] pixels) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (int i = 0; i < count; i++) { o.write(i == 0 ? mask : 0); if (i == 0 && pixels != null) o.write(pixels); }
        return o.toByteArray();
    }
    static boolean refuses(byte[] file) { try { read(file); return false; } catch (IOException e) { return true; } }

    // ---- checks ----
    static void roundTrips() throws Exception {
        // A plane (wash strokes).
        ToneDocument d = inked();
        ToneDocument.Snapshot s = d.layerSnapshot();
        check(s.layers.get(0).plane(ToneDocument.PLANE_WASH).inked(), "the page holds ink on a plane");
        ToneDocument back = read(write(s));
        check(same(back.layerSnapshot(), s) && back.layerSnapshot().layers.get(0).plane(ToneDocument.PLANE_WASH).ink != null, "ink on a plane reads back as written");
        // A base layer, and a plane beside it.
        s = inked().layerSnapshot();
        s.layers.get(0).ink = fill(77, 500, 900);
        back = read(write(s));
        check(Arrays.equals(back.layerSnapshot().layers.get(0).ink, s.layers.get(0).ink) && same(back.layerSnapshot(), s), "ink on a base layer reads back, beside a plane's");
        // A layer that holds Vector Ink's lines beside a pen's marks: ink on the base and on each plane in memory.
        ToneDocument mixed = VectorInkChecks.mixed();
        s = mixed.layerSnapshot();
        int inkedPlanes = 0;
        for (ToneDocument.Layer layer : s.layers) {
            if (layer.planes != null) for (ToneDocument.Layer p : layer.planes) if (p != null && p.covers()) { p.ink = new byte[p.alpha.length]; for (int i = 0; i < p.alpha.length; i++) if (p.alpha[i] != 0) p.ink[i] = (byte) (1 + i % 250); inkedPlanes++; }
        }
        check(inkedPlanes >= 4 && s.layers.get(1).plane(ToneDocument.PLANE_LINES) != null && s.layers.get(3).plane(ToneDocument.PLANE_PEN) != null, "the mixed page has lines and pen planes to ink (" + inkedPlanes + ")");
        back = read(write(s));
        check(same(back.layerSnapshot(), s), "ink on Vector Ink's plane and the Pen plane beside it comes back on each, as in memory");
        // All zeros is none.
        s = inked().layerSnapshot();
        s.layers.get(0).ink = new byte[PIXELS];
        for (ToneDocument.Layer p : s.layers.get(0).planes) if (p != null) p.ink = new byte[PIXELS];
        byte[] zeros = write(s);
        check(Arrays.equals(inflated(zeros), inflated(write(withoutInk(inked().layerSnapshot())))), "ink of all zeros writes no section");
        check(read(zeros).layerSnapshot().layers.get(0).ink == null, "and reads back as none");
    }
    static ToneDocument.Snapshot withoutInk(ToneDocument.Snapshot s) {
        for (ToneDocument.Layer l : s.layers) { l.ink = null; if (l.planes != null) for (ToneDocument.Layer p : l.planes) if (p != null) p.ink = null; }
        return s;
    }
    static void plainBytes() throws Exception {
        byte[] bytes = inflated(write(plain().layerSnapshot()));
        CRC32 crc = new CRC32(); crc.update(bytes);
        check(crc.getValue() == PLAIN_BODY, "a page without ink writes the bytes it always wrote (" + crc.getValue() + ")");
        byte[] nulled = inflated(write(withoutInk(inked().layerSnapshot())));
        byte[] with = inflated(write(inked().layerSnapshot()));
        check(with.length > nulled.length && contains(with, INKS) && !contains(nulled, INKS), "a page with ink has the section, the same page with its ink nulled has none");
        check(Arrays.equals(nulled, inflated(write(plain().layerSnapshot()))), "and writes the bytes of the page drawn without ink");
    }
    static boolean contains(byte[] b, int tag) { for (int i = 0; i + 4 <= b.length; i++) if ((b[i] & 255) == (tag >>> 24) && (b[i + 1] & 255) == (tag >> 16 & 255) && (b[i + 2] & 255) == (tag >> 8 & 255) && (b[i + 3] & 255) == (tag & 255)) return true; return false; }
    static void refused() throws Exception {
        byte[] file = write(plain().layerSnapshot());
        int count = plain().layerSnapshot().layers.size();
        byte[] good = masks(count, 1, new byte[PIXELS]);
        check(!refuses(with(file, section(good))), "(a section of the right length reads)");
        check(read(with(file, section(masks(count, 1, fill(9, 0, 5))))).layerSnapshot().layers.get(0).ink[0] == 9, "(and gives the layer its ink)");
        check(refuses(with(file, section(Arrays.copyOf(good, good.length - 1)))), "a truncated section is refused");
        check(refuses(with(file, section(Arrays.copyOf(good, good.length + 1)))), "an oversized section is refused");
        check(refuses(with(file, section(good), section(good))), "a repeated section is refused");
        check(refuses(with(file, section(masks(count, 1 << 6, new byte[PIXELS])))), "a bit beyond the planes is refused");
        check(SecondInkChecks.plain(read(with(file, section(masks(count, 1 << 4, fill(9, 0, 5)))))), "a bit for a plane the layer lacks reads, its ink dropped");
        check(refuses(with(file, section(Integer.MAX_VALUE, new byte[0]))), "a length beyond any page's is refused before anything is allocated");
        check(refuses(with(file, section(count + PIXELS, masks(count, 1, new byte[PIXELS - 1])))), "a declared length longer than its bytes is refused");
        check(refuses(with(file, section(masks(count, 3, new byte[PIXELS])))), "a set bit with no pixels behind it is refused");
    }
}
