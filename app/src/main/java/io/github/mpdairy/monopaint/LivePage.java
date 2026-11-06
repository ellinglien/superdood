package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

/**
 * Live pages: which notebook page the room draws on (docs/superpowers/specs/2026-11-01-manta-host-design.md, "Editing
 * the Manta's copy"). The Manta sends {@link #SHOWN} before each page it sends whole ({@code LiveProbe.shown}): the
 * notebook's path, the place ({@link DrawingBook#FRONT}, a stack, {@link DrawingBook#BACK}), the page bar's words and
 * where ‹ and › go. A browser asks with {@link #OPEN} (a notebook by its path) or {@link #TURN} (‹ −1, › 1); the Manta
 * opens or turns as its own shelf and page bar do, then answers with {@link #SHOWN}.
 * Travels beside the changes, deflated as {@link LiveTools} is: eight zero bytes (no change is 0×0), then UTF-8 lines:
 * {@link #HEAD}, the kind, the place (or the turn), "1" or "0" for ‹ and ›, the words, the words said, the path. Pure,
 * checked on the desktop ({@code LiveHostChecks}); the browser reads it in live.js.
 */
final class LivePage {
    static final String HEAD = "MBD page 1", SHOWN = "shown", OPEN = "open", TURN = "turn";
    static final int MOST = 4096;
    final String kind, path, words, said;
    /** {@link #SHOWN}: the place shown; {@link #TURN}: −1 or 1; {@link #OPEN}: 0. */
    final int place;
    final boolean previous, next;

    private LivePage(String kind, String path, int place, boolean previous, boolean next, String words, String said) {
        this.kind = kind; this.path = path; this.place = place; this.previous = previous; this.next = next; this.words = words; this.said = said;
    }
    /** What the Manta shows: {@code book}'s page drawn on, in notebook {@code path} (as the page bar shows it, {@code WebBooks.bar}). */
    static LivePage shown(String path, DrawingBook book) {
        PageStacks s = book.stacks();
        int at = DrawingBook.isCover(book.index()) ? book.index() : s.of(book.index()), n = s.count();
        return new LivePage(SHOWN, path, at, at != DrawingBook.FRONT, at != DrawingBook.BACK, DrawLayout.pageWords(at, n), DrawLayout.pageSaid(at, n));
    }
    static LivePage open(String path) { return new LivePage(OPEN, path, 0, false, false, "", ""); }
    static LivePage turn(int by) { return new LivePage(TURN, "", by < 0 ? -1 : 1, false, false, "", ""); }

    /** Deflated, as it travels. */
    byte[] write() throws IOException {
        String text = HEAD + "\n" + kind + "\n" + place + "\n" + (previous ? 1 : 0) + "\n" + (next ? 1 : 0) + "\n" + line(words) + "\n" + line(said) + "\n" + line(path) + "\n";
        byte[] t = text.getBytes(StandardCharsets.UTF_8), raw = new byte[8 + t.length];
        System.arraycopy(t, 0, raw, 8, t.length);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(bytes)) { out.write(raw); }
        return bytes.toByteArray();
    }
    private static String line(String s) { return s.replace('\n', ' '); }

    /** {@code deflated}, as it travels, as a page message; null for anything else (a change, the tools). From the network: never trusted. */
    static LivePage read(byte[] deflated) {
        byte[] raw;
        try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(deflated))) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            for (int n; (n = in.read(buf)) > 0; ) { b.write(buf, 0, n); if (b.size() > MOST) return null; }
            raw = b.toByteArray();
        } catch (IOException e) { return null; }
        if (!LiveTools.is(raw)) return null;
        String[] l = new String(raw, 8, raw.length - 8, StandardCharsets.UTF_8).split("\n", -1);
        if (l.length < 8 || !HEAD.equals(l[0]) || !(SHOWN.equals(l[1]) || OPEN.equals(l[1]) || TURN.equals(l[1]))) return null;
        try { return new LivePage(l[1], l[7], Integer.parseInt(l[2]), "1".equals(l[3]), "1".equals(l[4]), l[5], l[6]); }
        catch (NumberFormatException e) { return null; }
    }
}
