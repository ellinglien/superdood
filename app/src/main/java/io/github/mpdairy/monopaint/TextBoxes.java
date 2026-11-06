package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A page's text boxes (2026-11-01, branch {@code explore-text}; the practical base Supernote's own text boxes give): typed
 * words lying over the drawing, never in its layers, in iA Writer Duo 400 or 700, crisp black with antialiased edges,
 * wrapped at the box's width. Immutable, as {@link Magnets} are: every change makes another list, so an Undo step holds
 * the boxes before and after by reference ({@link ToneDocument#setTexts}) and a file keeps them in TSM5's TEXT section
 * ({@link DocumentCodec}, {@link #encode}). The layout (lines, where each starts, its baseline) is worked out here from
 * the face's own advances ({@link #advance}: a duospace, 0.6 em, 0.9 for the wide letters), so the page, the copies and
 * Make Ink wrap alike; the letters themselves come from {@link #type} (the app's {@code TextType} on the tablet, the
 * checks' own on the desktop). Every copy of the page shows them ({@link #stamp}, {@link Over}, through
 * {@code PagePicture.Composite}). Make Ink turns a box into a stroke of the brush in use ({@link #letters}, {@link
 * ModularStroke#print}). Pure, checked on the desktop ({@code TextBoxChecks}).
 */
final class TextBoxes {
    /** Alignments. */
    static final int LEFT = 0, CENTRE = 1, RIGHT = 2;
    /** The sizes' em, page px (300 ppi: 3, 4, 6 and 10 mm), and their names (docs/TEXT-STYLE.md); a new box is {@link #MEDIUM}. */
    static final int[] EM = {36, 48, 72, 120};
    static final String[] SIZES = {"Small", "Medium", "Large", "Huge"};
    static final int MEDIUM = 1;
    /** A line's height, em (the face's own ascent and descent, 1.3 em, and a little air). */
    static final float LINE = 1.4f;
    /** iA Writer Duo's ascent and descent, em (its hhea). */
    static final float ASCENT = 1.025f, DESCENT = .275f;
    /** A new box's width, page px (about 85 mm), and the least any box is (in em of its size). */
    static final int NEW_WIDTH = 1000, LEAST_EMS = 4;
    /** The most boxes a page holds, characters a box holds, and bytes the section takes. */
    static final int MOST = 200, MOST_CHARS = 4000, MOST_BYTES = 1 << 20;
    /** How near the box's right edge a pen picks up its width, page px; how far round it a tap still lands on it. */
    static final int EDGE = 24, SLACK = 12;

    /** One box: its text (lines apart at {@code \n}), its top left on the page, its width (page px), its size ({@link #EM}'s index), bold, its alignment. */
    static final class Box {
        final String text;
        final int x, y, width, size, align;
        final boolean bold;
        Box(String text, int x, int y, int width, int size, boolean bold, int align) {
            this.text = text; this.x = x; this.y = y; this.width = width; this.size = size; this.bold = bold; this.align = align;
        }
        Box withText(String t) { return new Box(t, x, y, width, size, bold, align); }
        Box at(int nx, int ny) { return new Box(text, nx, ny, width, size, bold, align); }
        Box widened(int w) { return new Box(text, x, y, Math.max(least(size), w), size, bold, align); }
        Box sized(int s) { return new Box(text, x, y, Math.max(least(s), width), s, bold, align); }
        Box bolded(boolean b) { return new Box(text, x, y, width, size, b, align); }
        Box aligned(int a) { return new Box(text, x, y, width, size, bold, a); }
        int em() { return EM[size]; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Box)) return false;
            Box b = (Box) o;
            return b.x == x && b.y == y && b.width == width && b.size == size && b.bold == bold && b.align == align && b.text.equals(text);
        }
        @Override public int hashCode() { return ((((text.hashCode() * 31 + x) * 31 + y) * 31 + width) * 31 + size) * 8 + align * 2 + (bold ? 1 : 0); }
        @Override public String toString() { return "\"" + text + "\"@" + x + "," + y; }
    }
    /** The least width a box of size {@code size} is, page px. */
    static int least(int size) { return LEAST_EMS * EM[size]; }
    /** A box's menu's words (docs/TEXT-STYLE.md). */
    static final String BOLD = "Bold", MAKE_INK = "Make Ink", DELETE = "Delete";
    static final String[] ALIGN = {"Left", "Centre", "Right"};
    /**
     * A box's menu (its long press, {@code TextMode}, and the browser's): the sizes, Bold, the alignments, each group
     * under a line, the ones {@code b} has marked; then Make Ink and Delete. Keys: "size" or "align" and its index, "bold", "ink", "delete".
     */
    static java.util.List<DrawLayout.Row> menu(Box b) {
        java.util.List<DrawLayout.Row> rows = new ArrayList<>();
        for (int s = 0; s < SIZES.length; s++) rows.add(new DrawLayout.Row("size" + s, SIZES[s], null, b.size == s));
        rows.add(DrawLayout.Row.LINE);
        rows.add(new DrawLayout.Row("bold", BOLD, null, b.bold));
        rows.add(DrawLayout.Row.LINE);
        for (int a = 0; a < ALIGN.length; a++) rows.add(new DrawLayout.Row("align" + a, ALIGN[a], null, b.align == a));
        rows.add(DrawLayout.Row.LINE);
        rows.add(new DrawLayout.Row("ink", MAKE_INK, "ic_brush_pen", false));
        rows.add(new DrawLayout.Row("delete", DELETE, "ic_ph_trash", false));
        return rows;
    }
    /** {@code b} after its menu's row {@code key} ({@link #menu}): sized, bolded or aligned; null for Make Ink and Delete. */
    static Box restyled(Box b, String key) {
        if (key.startsWith("size")) return b.sized(Integer.parseInt(key.substring(4)));
        if (key.startsWith("align")) return b.aligned(Integer.parseInt(key.substring(5)));
        return "bold".equals(key) ? b.bolded(!b.bold) : null;
    }
    /**
     * {@code start} dragged from page point {@code downX, downY} to {@code x, y} on a {@code width × height} page: its
     * width set ({@code widen}, a drag from its right edge, {@link #onEdge}), or moved, inside the page.
     */
    static Box dragged(Box start, boolean widen, float downX, float downY, float x, float y, int width, int height) {
        return widen ? start.widened(Math.min(width - start.x, Math.round(start.width + x - downX)))
                : inside(start.at(Math.round(start.x + x - downX), Math.round(start.y + y - downY)), width, height);
    }

    static final TextBoxes NONE = new TextBoxes(Collections.<Box>emptyList());
    private final List<Box> list;
    private TextBoxes(List<Box> list) { this.list = list; }
    static TextBoxes of(List<Box> boxes) { return boxes.isEmpty() ? NONE : new TextBoxes(Collections.unmodifiableList(new ArrayList<>(boxes))); }

    int size() { return list.size(); }
    boolean isEmpty() { return list.isEmpty(); }
    Box get(int i) { return list.get(i); }
    List<Box> list() { return list; }
    /** With {@code b} on top; these when the page holds {@link #MOST} already, or {@code b} holds nothing. */
    TextBoxes with(Box b) {
        if (list.size() >= MOST || b.text.isEmpty()) return this;
        ArrayList<Box> next = new ArrayList<>(list); next.add(b); return of(next);
    }
    /** Without box {@code i}. */
    TextBoxes without(int i) { ArrayList<Box> next = new ArrayList<>(list); next.remove(i); return of(next); }
    /** Box {@code i} as {@code b}, in its place; without it when {@code b} holds nothing. */
    TextBoxes replaced(int i, Box b) {
        if (b.text.isEmpty()) return without(i);
        ArrayList<Box> next = new ArrayList<>(list); next.set(i, b); return of(next);
    }
    /** The topmost box page point {@code x, y} is on (or within {@link #SLACK} of); -1 for none. */
    int hit(float x, float y) {
        for (int i = list.size() - 1; i >= 0; i--) {
            int[] r = rect(list.get(i));
            if (x >= r[0] - SLACK && x <= r[2] + SLACK && y >= r[1] - SLACK && y <= r[3] + SLACK) return i;
        }
        return -1;
    }
    /** Whether page point {@code x, y} is on box {@code b}'s right edge, where a drag changes its width. */
    static boolean onEdge(Box b, float x, float y) {
        int[] r = rect(b);
        return Math.abs(x - r[2]) <= EDGE && y >= r[1] - SLACK && y <= r[3] + SLACK;
    }
    @Override public boolean equals(Object o) { return o instanceof TextBoxes && ((TextBoxes) o).list.equals(list); }
    @Override public int hashCode() { return list.hashCode(); }

    /** A new, empty box placed for a tap at page point {@code x, y} on a {@code width × height} page: its first line round the tap, inside the page. */
    static Box placed(float x, float y, int width, int height) {
        int em = EM[MEDIUM], w = Math.min(NEW_WIDTH, Math.max(least(MEDIUM), width - Math.round(x)));
        Box b = new Box("", Math.round(x), Math.round(y - lineHeight(em) / 2f), Math.min(w, width), MEDIUM, false, LEFT);
        return inside(b, width, height);
    }
    /** {@code b} moved as little as needed to lie on a {@code width × height} page (and no wider than it). */
    static Box inside(Box b, int width, int height) {
        Box n = b.width > width ? new Box(b.text, b.x, b.y, width, b.size, b.bold, b.align) : b;
        int h = height(n);
        int x = Math.max(0, Math.min(width - n.width, n.x)), y = Math.max(0, Math.min(height - h, n.y));
        return x == n.x && y == n.y ? n : n.at(x, y);
    }

    // The layout

    /**
     * A character's advance in iA Writer Duo, em (its hmtx: 600 units, 900 for the wide letters, 300 for some Greek iotas,
     * 0 for combining marks); anything it doesn't have comes from a fallback face, given 1 em.
     */
    static float advance(char c) {
        if (c >= 0x300 && c < 0x370 || c == '​') return 0;
        if (WIDE.indexOf(c) >= 0) return .9f;
        if (NARROW.indexOf(c) >= 0) return .3f;
        if (c == '‰') return 1.2f;
        return c < 0x530 || c >= 0x1e00 && c < 0x2000 || c >= 0x2000 && c < 0x2270 || c >= 0xfb00 && c <= 0xfb02 ? .6f : 1;
    }
    private static final String WIDE = "%@MWmw©ÆæŒœŴŵǼǽΜΦΨφψωώЉЊДЖМФШЩЫЮжфшщыюљњҖҗҤӁӂӔӕӜӝӸӹẀẁẂẃẄẅ—…";
    private static final String NARROW = "ͺΊΐΙΪίιϊ";
    /** {@code s}'s width, em. */
    static float ems(String s) { float w = 0; for (int i = 0; i < s.length(); i++) w += advance(s.charAt(i)); return w; }
    /** A line's height at em {@code em}, page px. */
    static int lineHeight(int em) { return Math.round(em * LINE); }

    /** One line as drawn: its text, where it starts (page px, from the page's left) and its baseline (page px). */
    static final class Line {
        final String text;
        final float x;
        final int baseline;
        Line(String text, float x, int baseline) { this.text = text; this.x = x; this.baseline = baseline; }
    }
    /** {@code b}'s lines: each paragraph wrapped at spaces to its width (a word wider than it broken where it reaches it), aligned. */
    static List<Line> lines(Box b) {
        int em = b.em(), lh = lineHeight(em);
        float room = b.width / (float) em;
        List<String> wrapped = new ArrayList<>();
        for (String para : b.text.split("\n", -1)) wrap(para, room, wrapped);
        List<Line> lines = new ArrayList<>(wrapped.size());
        int top = Math.round((lh - (ASCENT + DESCENT) * em) / 2 + ASCENT * em);
        for (int i = 0; i < wrapped.size(); i++) {
            String t = wrapped.get(i);
            float w = ems(t) * em, x = b.align == CENTRE ? (b.width - w) / 2 : b.align == RIGHT ? b.width - w : 0;
            lines.add(new Line(t, b.x + x, b.y + i * lh + top));
        }
        return lines;
    }
    /** {@code para} wrapped at {@code room} em into {@code out}: greedy, at spaces, its trailing spaces dropped. */
    private static void wrap(String para, float room, List<String> out) {
        StringBuilder line = new StringBuilder();
        float w = 0;
        int n = para.length();
        for (int at = 0; at < n; ) {
            int end = at; while (end < n && para.charAt(end) != ' ') end++;
            int next = end; while (next < n && para.charAt(next) == ' ') next++;
            String word = para.substring(at, end);
            float ww = ems(word);
            if (line.length() > 0 && w + ww > room) { out.add(trimmed(line)); line.setLength(0); w = 0; }
            if (ww > room) {
                for (int i = 0; i < word.length(); i++) {
                    float a = advance(word.charAt(i));
                    if (line.length() > 0 && w + a > room) { out.add(trimmed(line)); line.setLength(0); w = 0; }
                    line.append(word.charAt(i)); w += a;
                }
            } else { line.append(word); w += ww; }
            line.append(para, end, next); w += next - end == 0 ? 0 : ems(para.substring(end, next));
            at = next;
        }
        out.add(trimmed(line));
    }
    private static String trimmed(StringBuilder s) { int e = s.length(); while (e > 0 && s.charAt(e - 1) == ' ') e--; return s.substring(0, e); }
    /** {@code b}'s height, page px: its lines' (at least one). */
    static int height(Box b) { return Math.max(1, lines(b).size()) * lineHeight(b.em()); }
    /** {@code b}'s box on the page, {left, top, right, bottom}. */
    static int[] rect(Box b) { return new int[]{b.x, b.y, b.x + b.width, b.y + height(b)}; }
    /** What {@code b}'s letters may cover, {left, top, right, bottom}: its box and a quarter em each side (a letter's overhang, a line a little long). */
    static int[] bounds(Box b) { int[] r = rect(b); int m = b.em() / 4; return new int[]{r[0] - m, r[1], r[2] + m, r[3]}; }
    /** Where {@code a} and {@code b} look different: the bounds of every box one has that the other hasn't. */
    static List<int[]> changed(TextBoxes a, TextBoxes b) {
        List<int[]> areas = new ArrayList<>();
        for (Box x : a.list) if (!b.list.contains(x)) areas.add(bounds(x));
        for (Box x : b.list) if (!a.list.contains(x)) areas.add(bounds(x));
        return areas;
    }

    // The letters

    /** Draws a box's letters: coverage, 0 none to 255 full, over its {@link #bounds}, row by row (its lines as {@link #lines} lays them). */
    interface Type { byte[] letters(Box b); }
    /**
     * What draws the letters in this process (the app's on the tablet, set as it starts; the checks' on the desktop); null:
     * none, and the boxes show nowhere but in their file. shortcut: one face for the process, not passed down the copies'
     * calls as {@link Magnets.Tiles} is; pass it if a second face ever comes.
     */
    static volatile Type type;
    /** {@code b}'s letters ({@link Type#letters}), or null without a {@link #type}. */
    static byte[] letters(Box b) { Type t = type; return t == null ? null : t.letters(b); }

    /**
     * Make Ink's area: {@code b}'s letters on a {@code width × height} page as a fill's room, each pixel's share of the
     * press its coverage ({@link FillRegion.Room#cover}); null when nothing of it is on the page or no {@link #type}.
     */
    static FillRegion.Room ink(Box b, int width, int height) {
        byte[] a = letters(b);
        if (a == null) return null;
        int[] r = bounds(b);
        int bw = r[2] - r[0], x0 = Math.max(0, r[0]), y0 = Math.max(0, r[1]), x1 = Math.min(width, r[2]) - 1, y1 = Math.min(height, r[3]) - 1;
        if (x1 < x0 || y1 < y0) return null;
        int w = x1 - x0 + 1, h = y1 - y0 + 1, area = 0;
        byte[] at = new byte[w * h], cover = new byte[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            byte c = a[(y0 - r[1] + y) * bw + x0 - r[0] + x];
            if (c == 0) continue;
            at[y * w + x] = FillRegion.OPEN; cover[y * w + x] = c; area++;
        }
        return area == 0 ? null : new FillRegion.Room(x0, y0, x1, y1, at, area, cover);
    }

    /** A page's boxes' letters, drawn once, to lay over its rows ({@code PagePicture.Composite}) or a picture ({@link #stamp}); read on one thread at a time, as a composite is. */
    static final class Over {
        private final int[][] at;
        private final byte[][] letters;
        private int[] row = new int[0];
        private Over(int[][] at, byte[][] letters) { this.at = at; this.letters = letters; }
        /** Row {@code y}'s coverage by the letters, {@code width} pixels, into {@code out} (0 none to 255); false, {@code out} untouched, where none reach the row. */
        boolean cover(int y, int[] out, int width) {
            boolean any = false;
            for (int k = 0; k < at.length; k++) {
                int[] r = at[k];
                if (y < r[1] || y >= r[3]) continue;
                if (!any) { java.util.Arrays.fill(out, 0, width, 0); any = true; }
                int o = (y - r[1]) * (r[2] - r[0]) - r[0];
                for (int x = Math.max(0, r[0]), end = Math.min(width, r[2]); x < end; x++) {
                    int c = letters[k][o + x] & 255;
                    if (c != 0) out[x] = c + (out[x] * (255 - c) + 127) / 255;
                }
            }
            return any;
        }
        private int[] row(int width) { if (row.length < width) row = new int[width]; return row; }
        /** Row {@code y}'s tones (0 black, 255 white), {@code width} of them, with the letters' black over them. */
        void row(int y, int[] tones, int width) {
            int[] c = row(width);
            if (cover(y, c, width)) for (int x = 0; x < width; x++) tones[x] = (tones[x] * (255 - c[x]) + 127) / 255;
        }
        /** As {@link #row} for marks alone ({@code PagePicture.Composite#marks}): the letters' black over them, their coverage added. */
        void marks(int y, int[] inked, int[] cover, int width) {
            int[] c = row(width);
            if (cover(y, c, width)) for (int x = 0; x < width; x++) {
                if (c[x] == 0) continue;
                inked[x] = (inked[x] * (255 - c[x]) + 127) / 255; cover[x] = c[x] + (cover[x] * (255 - c[x]) + 127) / 255;
            }
        }
    }
    /** {@code texts}' letters to lay over a page, or null for none (no boxes, or no {@link #type}). */
    static Over over(TextBoxes texts) {
        if (texts == null || texts.isEmpty() || type == null) return null;
        int[][] at = new int[texts.size()][];
        byte[][] letters = new byte[texts.size()][];
        for (int i = 0; i < at.length; i++) {
            at[i] = bounds(texts.get(i)); letters[i] = letters(texts.get(i));
            if (letters[i] == null) return null;
        }
        return new Over(at, letters);
    }
    /** Draws {@code texts} over {@code argb}, an opaque {@code width × height} picture of the page (its PNG copy): black letters over what is under them. */
    static void stamp(int[] argb, int width, int height, TextBoxes texts) {
        Over over = over(texts);
        if (over == null) return;
        int[] c = new int[width];
        for (int y = 0; y < height; y++) {
            if (!over.cover(y, c, width)) continue;
            for (int x = 0, i = y * width; x < width; x++, i++) {
                int k = 255 - c[x];
                if (k != 255) argb[i] = 0xff000000 | scale(argb[i] >> 16 & 255, k) << 16 | scale(argb[i] >> 8 & 255, k) << 8 | scale(argb[i] & 255, k);
            }
        }
    }
    private static int scale(int v, int k) { return (v * k + 127) / 255; }

    // In a file: TSM5's TEXT section

    /** The section's layout; a reader skips a version it doesn't know (the drawing opens without its boxes). */
    static final int VERSION = 1;
    /** The boxes as a TEXT section's bytes: the version, how many, then each one's text, top left, width, size and a byte of bold (bit 0) and alignment (bits 1 and 2). */
    byte[] encode() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(VERSION); out.writeInt(list.size());
            for (Box b : list) { out.writeUTF(b.text); out.writeInt(b.x); out.writeInt(b.y); out.writeInt(b.width); out.writeByte(b.size); out.writeByte((b.bold ? 1 : 0) | b.align << 1); }
            out.flush();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return bytes.toByteArray();
    }
    /** A TEXT section's boxes for a {@code width × height} page; {@link #NONE} for a version this reader doesn't know; anything else that doesn't read as boxes on that page is refused, as damage. */
    static TextBoxes decode(byte[] section, int width, int height) throws IOException {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(section));
            if (in.readUnsignedByte() != VERSION) return NONE;
            int count = in.readInt();
            if (count < 0 || count > MOST) throw new IOException("Invalid text count");
            ArrayList<Box> boxes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String text = in.readUTF();
                int x = in.readInt(), y = in.readInt(), w = in.readInt(), size = in.readUnsignedByte(), flags = in.readUnsignedByte();
                if (!validText(text) || size >= EM.length || flags > 5 || w < 1 || w > width || x < 0 || y < 0 || x > width || y > height)
                    throw new IOException("Invalid text box");
                boxes.add(new Box(text, x, y, w, size, (flags & 1) != 0, flags >> 1));
            }
            if (in.read() != -1) throw new IOException("Extra text data");
            return of(boxes);
        } catch (java.io.EOFException | java.io.UTFDataFormatException e) { throw new IOException("Short text section", e); }
    }
    /** Typed text as a box keeps it ({@link #validText}, but empty): tabs as spaces, no other control but the line break, no trailing blank lines, at most {@link #MOST_CHARS}. */
    static String clean(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\t') b.append(' ');
            else if (c == '\n' || !Character.isISOControl(c)) b.append(c);
        }
        int e = b.length();
        while (e > 0 && (b.charAt(e - 1) == '\n' || b.charAt(e - 1) == ' ')) e--;
        return b.substring(0, Math.min(e, MOST_CHARS));
    }
    /** Text a box can hold: 1 to {@link #MOST_CHARS} characters, no control but the line break. */
    static boolean validText(String text) {
        if (text.isEmpty() || text.length() > MOST_CHARS) return false;
        for (int i = 0; i < text.length(); i++) { char c = text.charAt(i); if (c != '\n' && Character.isISOControl(c)) return false; }
        return true;
    }
}
