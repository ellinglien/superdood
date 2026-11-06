package io.github.mpdairy.monopaint;

import java.io.*;
import java.util.zip.*;

/**
 * Live pages' probe (docs/superpowers/specs/2026-10-31-live-pages-design.md, build step 1): the pixels a change made,
 * as they travel. A rectangle of the page and its greys as {@link PictureImport#lay} takes them (−1: clear, left as it
 * is), and the path of the stroke that made it, if one did (step 2c: the receiver draws it on along it, {@link LiveReveal}).
 * Bytes, deflated (zlib, as a browser's CompressionStream("deflate") makes): x, y, w, h as big-endian unsigned shorts,
 * then a byte a pixel, 255 clear; then n, the path's samples (0: none), and n samples of x, y (page pixels) and ms since
 * the first, each an unsigned short; then, only when one is, a byte of flags: 1, a change that lightens (an Undo's, step
 * 4; the eraser's, Clear Page's: {@link #since}), its greys set, lighter too, not laid as ink; 2, a later part of a
 * stroke sent while it is drawn, one Undo step with the part before; {@link #kinds} {@code << 2}. Bytes ending after the greys (step 1–2a's form) are a change with no path. Pure, checked on the
 * desktop ({@code LiveChangeChecks}).
 */
final class LiveChange {
    /** The most pixels one change holds: a whole Manta page. */
    static final int MOST = 1920 * 2560;
    /** The most samples a path holds. */
    static final int MOST_SAMPLES = 20000;
    static final int[] NO_PATH = {};
    /**
     * How soon what a page does on its own after the lift (wet paint moving, a wash settling, fading marks) goes while it
     * goes on, ms: the changes since as one, the Manta's ({@code LiveProbe.ticked}) as the browser's own wet paint's
     * (live.html's wetTick, WebEngine's LIVE_TICKS_MS).
     */
    static final long TICKS_MS = 400;
    /**
     * How often a pen stroke being drawn on the Manta goes to the guests, ms ({@code LiveProbe.drawing}): what its page
     * shows where the stroke wrote since the part before, as {@link #part}s; the browser sends its own every 80.
     */
    static final long PART_MS = 120;
    final int x, y, w, h;
    final int[] greys;
    /** The stroke's samples: x, y (page pixels), ms since the first, each 0 to 65535; empty: none. */
    final int[] path;
    /** A change that lightens (an Undo's, the eraser's, Clear Page's): its greys set as they are, lighter too; else laid as ink, only darkening. */
    final boolean set;
    /**
     * A later part of a stroke sent while it is drawn (its parts have no path: streaming draws them on): one Undo step
     * with the sender's part before it, while that is still the page's newest.
     */
    final boolean joins;
    /**
     * The marks' kind ({@code ToneDocument.KIND_} bits; 0: none), set by the sender before it goes: the receiver lays them
     * on that kind's plane ({@link PictureImport#lay}), so its Show Kinds fades them as the sender's own (the owner,
     * 2026-11-05: "visibility settings don't seem to affect anything on the browser"). A change of more kinds goes as none.
     */
    int kinds;

    LiveChange(int x, int y, int w, int h, int[] greys) { this(x, y, w, h, greys, NO_PATH); }
    LiveChange(int x, int y, int w, int h, int[] greys, int[] path) { this(x, y, w, h, greys, path, false); }
    LiveChange(int x, int y, int w, int h, int[] greys, int[] path, boolean set) { this(x, y, w, h, greys, path, set, false); }
    LiveChange(int x, int y, int w, int h, int[] greys, int[] path, boolean set, boolean joins) {
        if (x < 0 || y < 0 || x > 0xffff || y > 0xffff || w <= 0 || h <= 0 || (long) w * h > MOST || greys.length != w * h
                || path.length % 3 != 0 || path.length > 3 * MOST_SAMPLES)
            throw new IllegalArgumentException("Not a change: " + w + "×" + h + " at " + x + "," + y + ", " + path.length / 3 + " samples");
        for (int v : path) if (v < 0 || v > 0xffff) throw new IllegalArgumentException("Not a sample: " + v);
        this.x = x; this.y = y; this.w = w; this.h = h; this.greys = greys; this.path = path; this.set = set; this.joins = joins;
    }

    /**
     * A pen stroke's samples ({@link StrokeLog.Entry}: half pixels, ms since the one before) as a change's path: page
     * pixels, ms since the first; every k-th sample of one longer than {@link #MOST_SAMPLES}.
     */
    static int[] path(StrokeLog.Entry stroke) {
        int n = stroke.size(), k = Math.max(1, (n + MOST_SAMPLES - 1) / MOST_SAMPLES);
        int[] path = new int[(n + k - 1) / k * 3];
        long ms = 0;
        for (int i = 0, j = 0; i < n; i++) {
            ms += stroke.dt[i];
            if (i % k != 0) continue;
            path[j++] = sample(Math.round(stroke.px(i))); path[j++] = sample(Math.round(stroke.py(i))); path[j++] = sample(ms);
        }
        return path;
    }
    private static int sample(long v) { return (int) Math.max(0, Math.min(0xffff, v)); }

    /**
     * What the page shows over an area, {@code tone} giving each pixel's (0 black to 255 white), as a change: white
     * goes as 254, since 255 means clear (left as it is) and a white pixel must whiten what the receiver has there.
     */
    static LiveChange shown(int x, int y, int w, int h, java.util.function.IntBinaryOperator tone, int[] path) { return shown(x, y, w, h, tone, path, false); }
    /** As {@link #shown}, {@code set} as {@link #set}; a {@code tone} of −1 is clear. */
    static LiveChange shown(int x, int y, int w, int h, java.util.function.IntBinaryOperator tone, int[] path, boolean set) {
        int[] greys = new int[w * h];
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) greys[row * w + col] = Math.min(254, tone.applyAsInt(x + col, y + row));
        return new LiveChange(x, y, w, h, greys, path, set);
    }

    /** A block's tones, as {@link ToneDocument#compositeTones} gives them: row by row into {@code out} from {@code offset}, {@code stride} apart. */
    interface Tones { void block(int x, int y, int w, int h, int[] out, int offset, int stride); }

    /**
     * What the page ({@code width} by {@code height}) shows over {@code areas} ({left, top, right, bottom}, exclusive,
     * page pixels; cut to the page), as one change over the box round them, clear elsewhere in it: a mirrored stroke's
     * reflections and not the page between them. White goes as 254, as {@link #shown}. Null when none is on the page.
     */
    static LiveChange shown(java.util.List<int[]> areas, int width, int height, Tones tones, int[] path) {
        java.util.List<int[]> on = new java.util.ArrayList<>();
        int x0 = width, y0 = height, x1 = 0, y1 = 0;
        for (int[] a : areas) {
            int l = Math.max(0, a[0]), t = Math.max(0, a[1]), r = Math.min(width, a[2]), b = Math.min(height, a[3]);
            if (l >= r || t >= b) continue;
            on.add(new int[]{l, t, r, b});
            x0 = Math.min(x0, l); y0 = Math.min(y0, t); x1 = Math.max(x1, r); y1 = Math.max(y1, b);
        }
        if (on.isEmpty()) return null;
        int w = x1 - x0, h = y1 - y0;
        int[] greys = new int[w * h];
        java.util.Arrays.fill(greys, -1);
        for (int[] a : on) tones.block(a[0], a[1], a[2] - a[0], a[3] - a[1], greys, (a[1] - y0) * w + a[0] - x0, w);
        for (int i = 0; i < greys.length; i++) if (greys[i] > 254) greys[i] = 254;
        return new LiveChange(x0, y0, w, h, greys, path);
    }

    /** This change as a part of a stroke sent while it is drawn: no path (laid at once), joining the part before unless the first; its kind kept. */
    LiveChange part(boolean later) { LiveChange c = new LiveChange(x, y, w, h, greys, NO_PATH, set, later); c.kinds = kinds; return c; }

    /**
     * This change as it goes out: only the pixels that differ from {@code room} (what each pixel of a {@code width} wide
     * page was last sent as, a byte each, 255 for nothing yet), and set when one is lighter, since ink only darkens where
     * it is laid: the eraser, Clear Page, a layer hidden (the owner, 2026-11-02: "i cleared the page but the desktop
     * version kept the art"). A pixel not sent yet, or lost ({@link #forget}), goes set too: what a guest shows there is
     * not known, so it may be darker. Its greys cut in place (a whole Manta page's are 20 MB); taken into the room
     * ({@link #into}). Null when nothing differs.
     */
    LiveChange since(byte[] room, int width) {
        int[] g = greys;
        boolean lighter = false, any = false;
        for (int i = 0; i < g.length; i++) {
            if (g[i] < 0) continue;
            int was = room[(y + i / w) * width + x + i % w] & 255;
            if (g[i] == was) g[i] = -1; else { any = true; lighter |= g[i] > was || was == 255; }
        }
        if (!any) return null;
        LiveChange c = new LiveChange(x, y, w, h, g, path, set || lighter, joins);
        c.kinds = kinds;
        c.into(room, width);
        return c;
    }

    /** This change into {@code room} ({@link #since}) as everyone lays it: set, as it is; else only where darker. */
    void into(byte[] room, int width) {
        for (int i = 0; i < greys.length; i++) {
            int g = greys[i], j = (y + i / w) * width + x + i % w;
            if (g >= 0 && (set || g < (room[j] & 255))) room[j] = (byte) g;
        }
    }

    /**
     * This change, sent but lost (its POST failed), out of {@code room} ({@link #since}): its pixels as not sent, so the
     * next change over them sends them again, set. Before, they counted as sent and guests kept stale marks there until
     * a page turn or a reconnect.
     */
    void forget(byte[] room, int width) {
        for (int i = 0; i < greys.length; i++) if (greys[i] >= 0) room[(y + i / w) * width + x + i % w] = (byte) 255;
    }

    byte[] write() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // Fastest: a mirrored stroke's 3 M pixels packed 4 times as fast, 11 % larger (desktop, 2026-10-31).
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        // Buffered: a byte at a time into the deflater took seconds on the Manta for a big stroke.
        try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(new DeflaterOutputStream(bytes, deflater), 1 << 16))) {
            out.writeShort(x); out.writeShort(y); out.writeShort(w); out.writeShort(h);
            // An array at once, not a pixel at a time through the streams: a third of the time (desktop).
            byte[] raw = new byte[greys.length];
            for (int i = 0; i < raw.length; i++) raw[i] = (byte) (greys[i] < 0 ? 255 : greys[i]);
            out.write(raw);
            out.writeShort(path.length / 3);
            for (int v : path) out.writeShort(v);
            int flags = (set ? 1 : 0) | (joins ? 2 : 0) | (kinds & ToneDocument.KIND_ALL) << 2;
            if (flags != 0) out.write(flags);
        } finally { deflater.end(); }
        return bytes.toByteArray();
    }

    /** The change in {@code bytes}; throws on anything else: from the network, so never trusted. */
    static LiveChange read(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes)))) {
            int x = in.readUnsignedShort(), y = in.readUnsignedShort(), w = in.readUnsignedShort(), h = in.readUnsignedShort();
            if (w <= 0 || h <= 0 || (long) w * h > MOST) throw new IOException("Not a change: " + w + "×" + h);
            byte[] raw = new byte[w * h];
            in.readFully(raw);
            int[] greys = new int[raw.length];
            for (int i = 0; i < raw.length; i++) greys[i] = (raw[i] & 255) == 255 ? -1 : raw[i] & 255;
            int high = in.read();
            if (high == -1) return new LiveChange(x, y, w, h, greys);
            int n = high << 8 | in.readUnsignedByte();
            if (n > MOST_SAMPLES) throw new IOException("Too long a path: " + n);
            int[] path = new int[3 * n];
            for (int i = 0; i < path.length; i++) path[i] = in.readUnsignedShort();
            int flags = in.read();
            if (flags != -1 && (flags < 1 || flags > 31 || in.read() != -1)) throw new IOException("Bytes after the change");
            LiveChange c = new LiveChange(x, y, w, h, greys, path, flags > 0 && (flags & 1) != 0, flags > 0 && (flags & 2) != 0);
            if (flags > 0) c.kinds = flags >> 2;
            return c;
        } catch (ZipException e) { throw new IOException("Not deflated", e); }
    }
}
