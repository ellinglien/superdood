package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.*;

/**
 * Live pages: the host's tools as a guest joins (docs/superpowers/specs/2026-10-31-browser-app-design.md, M1): the page's
 * size, the nine slots as a notebook keeps its toolbar ({@link ToolSlots.Toolbar#lines}: each brush at its size and grey),
 * the tool in use, the eraser's size, and the page's mirrors ({@link Mirrors#encode}, the MIRR section). The host sends it
 * after the whole page ({@code LiveProbe.shown}); a guest's browser draws with them ({@code WebEngine.tools}).
 * Travels beside the changes, deflated as {@link LiveChange} is: eight zero bytes (a change's x, y, w, h; no change is
 * 0×0, so a reader of changes refuses it and {@link #is} tells it apart), then UTF-8 lines: {@link #HEAD}; "page w h";
 * "selected" and the tool ({@link ToolSlots#ERASER} for the eraser); "eraser" and its px; "mirrors" and the section in
 * hex, or "-"; then the toolbar's lines; then, for each Stamp of the toolbar whose nib this tablet holds, "nib" and the nib
 * as the nib store keeps it ({@link StampNib#write}), so a guest's stamp prints the host's nib, not a round one (none
 * for a toolbar without a cut Stamp: the bytes as before). Pure, checked on the desktop ({@code LiveToolsChecks}).
 */
final class LiveTools {
    static final String HEAD = "MBD tools 1";
    private static final String NIB = "nib ";
    /** The most bytes a message holds (ten long brush codes, the most mirrors and ten of the largest nibs, with room). */
    static final int MOST = 1 << 20;
    final int width, height, selected;
    final double eraserPx;
    final ToolSlots.Toolbar toolbar;
    /** The MIRR section, or empty for none. */
    final byte[] mirrors;
    /** The toolbar's Stamps' nibs: the host's own as it writes them; a guest's those that read (each checked against its id by {@link StampNib#of} once kept). */
    final List<StampNib> nibs;

    LiveTools(int width, int height, ToolSlots.Toolbar toolbar, int selected, double eraserPx, Mirrors mirrors) {
        this(width, height, toolbar, selected, eraserPx, mirrors.isEmpty() ? new byte[0] : mirrors.encode(), nibs(toolbar));
    }
    private LiveTools(int width, int height, ToolSlots.Toolbar toolbar, int selected, double eraserPx, byte[] mirrors, List<StampNib> nibs) {
        if (width <= 0 || height <= 0 || width > 0xffff || height > 0xffff || selected < 0 || selected >= ToolSlots.TOOLS || !(eraserPx > 0) || Double.isInfinite(eraserPx))
            throw new IllegalArgumentException("Not a host's tools: " + width + "×" + height + ", tool " + selected + ", eraser " + eraserPx);
        this.width = width; this.height = height; this.toolbar = toolbar; this.selected = selected; this.eraserPx = eraserPx; this.mirrors = mirrors; this.nibs = nibs;
    }
    /** The nibs {@code toolbar}'s Stamps print that the nib store holds, each once. */
    private static List<StampNib> nibs(ToolSlots.Toolbar toolbar) {
        List<StampNib> nibs = new ArrayList<>();
        for (Brush b : toolbar.brushes) {
            StampNib nib = b != null && b.parts.stamps() ? StampNib.of(b.parts.nib) : null;
            if (nib != null && !nibs.contains(nib)) nibs.add(nib);
        }
        return nibs;
    }

    /** The page's mirrors, as the host's page has them; none when the section doesn't read here. */
    Mirrors mirrors() {
        if (mirrors.length == 0) return Mirrors.NONE;
        try { return Mirrors.decode(mirrors, width, height); } catch (IOException e) { return Mirrors.NONE; }
    }

    /** The bytes before they are deflated. */
    byte[] raw() {
        StringBuilder s = new StringBuilder(HEAD).append('\n')
                .append("page ").append(width).append(' ').append(height).append('\n')
                .append("selected ").append(selected).append('\n')
                .append("eraser ").append(eraserPx).append('\n')
                .append("mirrors ").append(mirrors.length == 0 ? "-" : hex(mirrors)).append('\n');
        for (String line : toolbar.lines()) s.append(line).append('\n');
        for (StampNib nib : nibs) s.append(NIB).append(nib.write()).append('\n');
        byte[] text = s.toString().getBytes(StandardCharsets.UTF_8), raw = new byte[8 + text.length];
        System.arraycopy(text, 0, raw, 8, text.length);
        return raw;
    }

    /** Deflated, as it travels. */
    byte[] write() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(bytes)) { out.write(raw()); }
        return bytes.toByteArray();
    }

    /** Whether {@code raw} (inflated) is a host's tools, not a change: its first eight bytes zero. */
    static boolean is(byte[] raw) {
        if (raw.length < 8) return false;
        for (int i = 0; i < 8; i++) if (raw[i] != 0) return false;
        return true;
    }
    /** Whether {@code deflated}, as it travels, is a host's tools: only its first bytes inflated. */
    static boolean isDeflated(byte[] deflated) {
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(deflated)))) {
            byte[] head = new byte[8];
            in.readFully(head);
            return is(head);
        } catch (IOException e) { return false; }
    }

    /** The host's tools in {@code raw} (inflated); throws on anything else: from the network, so never trusted. */
    static LiveTools parse(byte[] raw) throws IOException {
        if (!is(raw) || raw.length > MOST) throw new IOException("Not a host's tools");
        String[] lines = new String(raw, 8, raw.length - 8, StandardCharsets.UTF_8).split("\n", -1);
        if (lines.length < 6 || !HEAD.equals(lines[0])) throw new IOException("Not a host's tools (or a later version's)");
        try {
            String[] page = field(lines[1], "page").split(" ");
            if (page.length != 2) throw new IOException("Not a page size: " + lines[1]);
            int width = Integer.parseInt(page[0]), height = Integer.parseInt(page[1]);
            int selected = Integer.parseInt(field(lines[2], "selected"));
            double eraser = Double.parseDouble(field(lines[3], "eraser"));
            String m = field(lines[4], "mirrors");
            byte[] mirrors = m.equals("-") ? new byte[0] : unhex(m);
            List<String> bar = new ArrayList<>();
            List<StampNib> nibs = new ArrayList<>();
            for (int i = 5; i < lines.length; i++) {
                if (lines[i].startsWith(NIB)) { StampNib nib = StampNib.read(lines[i].substring(NIB.length())); if (nib != null) nibs.add(nib); }
                else if (!lines[i].isEmpty()) bar.add(lines[i]);
            }
            ToolSlots.Toolbar toolbar = ToolSlots.Toolbar.parse(bar);
            if (toolbar == null) throw new IOException("Not a toolbar");
            return new LiveTools(width, height, toolbar, selected, eraser, mirrors, nibs);
        } catch (RuntimeException e) { throw new IOException("Not a host's tools: " + e.getMessage(), e); }
    }

    private static String field(String line, String name) throws IOException {
        if (!line.startsWith(name + " ")) throw new IOException("No " + name);
        return line.substring(name.length() + 1);
    }
    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte v : b) s.append(Character.forDigit((v >> 4) & 15, 16)).append(Character.forDigit(v & 15, 16));
        return s.toString();
    }
    private static byte[] unhex(String s) throws IOException {
        if (s.length() % 2 != 0 || s.length() > 2 * Mirrors.MOST_BYTES) throw new IOException("Not mirrors");
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) {
            int hi = Character.digit(s.charAt(2 * i), 16), lo = Character.digit(s.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) throw new IOException("Not mirrors");
            b[i] = (byte) (hi << 4 | lo);
        }
        return b;
    }
}
