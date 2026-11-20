package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

/**
 * A PDF's outline (bookmarks) read on its own (Android's PdfRenderer has none): Open File's Contents for a PDF. Pure.
 * It finds objects by scanning for "N G obj" (the xref may be broken), opens object streams (Flate only, no predictor),
 * then walks catalog, outline and page tree. It reads what it can and gives up on the rest.
 */
final class PdfOutline {
    static final class Item {
        final String title; final int depth, page;
        Item(String t, int d, int p) { title = t; depth = d; page = p; }
    }

    /** The most it reads; a larger PDF has no Contents. shortcut: whole file in memory, read past it from the xref if big outlined PDFs turn up. */
    static final int MOST_BYTES = 64 << 20;
    private static final int MOST_ITEMS = 2000, MOST_INFLATED = 256 << 20;
    private static final Pattern OBJ = Pattern.compile("(?<![0-9])(\\d{1,9})\\s+(\\d{1,5})\\s+obj\\b");

    /** Its items, depth 0 and 1 only, in order, each with its 0-based page; empty when it has none, is encrypted or can't be read. Never throws. */
    static List<Item> read(byte[] pdf) {
        try {
            if (pdf == null || pdf.length > MOST_BYTES) return Collections.emptyList();
            return new PdfOutline(pdf).items();
        } catch (Throwable t) { return Collections.emptyList(); }
    }

    private static final class Name { final String s; Name(String s) { this.s = s; } }
    private static final class Ref { final int num; Ref(int n) { num = n; } }

    private final byte[] pdf;
    private final String text;
    private final Map<Integer, Object> objs = new HashMap<>();
    private Map<?, ?> lastXref;
    private final List<Item> out = new ArrayList<>();

    private PdfOutline(byte[] pdf) { this.pdf = pdf; text = new String(pdf, StandardCharsets.ISO_8859_1); }

    private List<Item> items() {
        scan();
        Map<?, ?> trailer = null;
        int t = text.lastIndexOf("trailer");
        if (t >= 0) try { Object v = new Parser(pdf, t + 7).value(); if (v instanceof Map) trailer = (Map<?, ?>) v; } catch (RuntimeException e) { }
        if (trailer != null && lastXref != null && trailer.get("Root") == null) trailer = lastXref;
        if (trailer == null) trailer = lastXref;
        if (trailer == null || trailer.containsKey("Encrypt") || (lastXref != null && lastXref.containsKey("Encrypt"))) return Collections.emptyList();
        Object c = resolve(trailer.get("Root"));
        if (!(c instanceof Map)) return Collections.emptyList();
        Map<?, ?> cat = (Map<?, ?>) c;
        Map<Integer, Integer> pageAt = new HashMap<>();
        pagesWalk(cat.get("Pages"), pageAt, new HashSet<Integer>());
        Object o = resolve(cat.get("Outlines"));
        if (o instanceof Map) walk(((Map<?, ?>) o).get("First"), 0, cat, pageAt, new HashSet<Integer>());
        return out;
    }

    // ---- objects

    private void scan() {
        Matcher m = OBJ.matcher(text);
        int from = 0;
        while (from < pdf.length && m.find(from)) {
            int num = Integer.parseInt(m.group(1));
            from = m.end();
            try {
                Parser p = new Parser(pdf, from);
                Object v = p.value();
                objs.put(num, v);
                from = p.pos;
                if (v instanceof Map) {
                    Map<?, ?> d = (Map<?, ?>) v;
                    p.skipWs();
                    if (text.startsWith("stream", p.pos)) {
                        int st = p.pos + 6;
                        if (st < pdf.length && pdf[st] == '\r') st++;
                        if (st < pdf.length && pdf[st] == '\n') st++;
                        int e = streamEnd(d, st);
                        from = e;
                        String type = nameOf(d.get("Type"));
                        if ("ObjStm".equals(type)) inObjStm(d, java.util.Arrays.copyOfRange(pdf, st, e));
                        else if ("XRef".equals(type)) lastXref = d;
                    }
                }
            } catch (RuntimeException e) { /* a damaged object: the next one may be fine */ }
        }
    }

    private int streamEnd(Map<?, ?> d, int st) {
        Object len = resolve(d.get("Length"));
        if (len instanceof Long && (Long) len >= 0 && st + (Long) len <= pdf.length) {
            int e = (int) (st + (Long) len), k = e;
            while (k < pdf.length && pdf[k] <= ' ') k++;
            if (text.startsWith("endstream", k)) return e;
        }
        int e = text.indexOf("endstream", st);
        return e < 0 ? pdf.length : e;
    }

    private void inObjStm(Map<?, ?> d, byte[] raw) {
        Object f = resolve(d.get("Filter"));
        if (f instanceof List && ((List<?>) f).size() == 1) f = ((List<?>) f).get(0);
        byte[] data = raw;
        if (f != null) { if (!"FlateDecode".equals(nameOf(f))) return; data = inflate(raw); }
        Object n = resolve(d.get("N")), first = resolve(d.get("First"));
        if (!(n instanceof Long) || !(first instanceof Long)) return;
        Parser h = new Parser(data, 0);
        int[] num = new int[(int) Math.min((Long) n, 100000)], off = new int[num.length];
        for (int i = 0; i < num.length; i++) { num[i] = ((Long) h.value()).intValue(); off[i] = ((Long) h.value()).intValue(); }
        for (int i = 0; i < num.length; i++) {
            try { objs.put(num[i], new Parser(data, (int) (long) (Long) first + off[i]).value()); } catch (RuntimeException e) { }
        }
    }

    private static byte[] inflate(byte[] in) {
        Inflater z = new Inflater();
        try {
            z.setInput(in);
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (!z.finished() && o.size() < MOST_INFLATED) {
                int k = z.inflate(buf);
                if (k == 0 && (z.needsInput() || z.needsDictionary())) break;
                o.write(buf, 0, k);
            }
            return o.toByteArray();
        } catch (java.util.zip.DataFormatException e) { throw new IllegalStateException(e); }
        finally { z.end(); }
    }

    private Object resolve(Object v) {
        for (int i = 0; i < 10 && v instanceof Ref; i++) v = objs.get(((Ref) v).num);
        return v instanceof Ref ? null : v;
    }
    private static String nameOf(Object v) { return v instanceof Name ? ((Name) v).s : null; }

    // ---- pages, destinations, outline

    private void pagesWalk(Object ref, Map<Integer, Integer> pageAt, Set<Integer> seen) {
        if (!(ref instanceof Ref) || !seen.add(((Ref) ref).num)) return;
        Object d = resolve(ref);
        if (!(d instanceof Map)) return;
        Object kids = resolve(((Map<?, ?>) d).get("Kids"));
        if (kids instanceof List) for (Object k : (List<?>) kids) pagesWalk(k, pageAt, seen);
        else if (!"Pages".equals(nameOf(((Map<?, ?>) d).get("Type")))) pageAt.put(((Ref) ref).num, pageAt.size());
    }

    private void walk(Object cur, int depth, Map<?, ?> cat, Map<Integer, Integer> pageAt, Set<Integer> seen) {
        while (cur instanceof Ref && seen.add(((Ref) cur).num) && out.size() < MOST_ITEMS) {
            Object d = resolve(cur);
            if (!(d instanceof Map)) return;
            Map<?, ?> it = (Map<?, ?>) d;
            String title = title(resolve(it.get("Title")));
            Object dest = it.get("Dest");
            if (dest == null) {
                Object a = resolve(it.get("A"));
                if (a instanceof Map && "GoTo".equals(nameOf(((Map<?, ?>) a).get("S")))) dest = ((Map<?, ?>) a).get("D");
            }
            int page = dest == null ? -1 : page(dest, 0, cat, pageAt);
            if (title != null && !title.isEmpty() && page >= 0) out.add(new Item(title, depth, page));
            if (depth == 0) walk(it.get("First"), 1, cat, pageAt, seen);
            cur = it.get("Next");
        }
    }

    private int page(Object dest, int hop, Map<?, ?> cat, Map<Integer, Integer> pageAt) {
        Object d = resolve(dest);
        if (d instanceof Map) return hop < 3 ? page(((Map<?, ?>) d).get("D"), hop + 1, cat, pageAt) : -1;
        if (d instanceof List) {
            List<?> a = (List<?>) d;
            Integer i = !a.isEmpty() && a.get(0) instanceof Ref ? pageAt.get(((Ref) a.get(0)).num) : null;
            return i == null ? -1 : i;
        }
        String key = d instanceof Name ? ((Name) d).s : d instanceof byte[] ? new String((byte[]) d, StandardCharsets.ISO_8859_1) : null;
        if (key == null || hop >= 3) return -1;
        Object dests = resolve(cat.get("Dests")), v = dests instanceof Map ? ((Map<?, ?>) dests).get(key) : null;
        if (v == null) {
            Object names = resolve(cat.get("Names"));
            Object tree = names instanceof Map ? ((Map<?, ?>) names).get("Dests") : null;
            v = tree == null ? null : treeFind(tree, key, 0);
        }
        return v == null ? -1 : page(v, hop + 1, cat, pageAt);
    }

    /** The name tree's value for key; /Limits are ignored, every branch is searched. */
    private Object treeFind(Object node, String key, int depth) {
        Object n = resolve(node);
        if (!(n instanceof Map) || depth > 12) return null;
        Object names = resolve(((Map<?, ?>) n).get("Names")), kids = resolve(((Map<?, ?>) n).get("Kids"));
        if (names instanceof List) {
            List<?> l = (List<?>) names;
            for (int i = 0; i + 1 < l.size(); i += 2) {
                Object k = resolve(l.get(i));
                if (k instanceof byte[] && new String((byte[]) k, StandardCharsets.ISO_8859_1).equals(key)) return l.get(i + 1);
            }
        }
        if (kids instanceof List) for (Object k : (List<?>) kids) { Object v = treeFind(k, key, depth + 1); if (v != null) return v; }
        return null;
    }

    /** UTF-16BE after FE FF, else PDFDocEncoding, read as Latin-1 (close enough). */
    private static String title(Object s) {
        if (!(s instanceof byte[])) return null;
        byte[] b = (byte[]) s;
        String t = b.length >= 2 && (b[0] & 255) == 0xFE && (b[1] & 255) == 0xFF
            ? new String(b, 2, b.length - 2, StandardCharsets.UTF_16BE) : new String(b, StandardCharsets.ISO_8859_1);
        return t.trim();
    }

    // ---- tokenizer: dictionaries (Map), arrays (List), names (Name), numbers (Long, Double), references (Ref), strings (byte[]), Boolean, null

    private static final class Parser {
        final byte[] b; int pos, depth;
        Parser(byte[] b, int pos) { this.b = b; this.pos = pos; }

        static boolean ws(int c) { return c == 0 || c == 9 || c == 10 || c == 12 || c == 13 || c == 32; }
        static boolean delim(int c) { return ws(c) || "()<>[]{}/%".indexOf(c) >= 0; }
        static IllegalStateException bad() { return new IllegalStateException("bad PDF syntax"); }

        void skipWs() {
            while (pos < b.length) {
                if (ws(b[pos])) pos++;
                else if (b[pos] == '%') while (pos < b.length && b[pos] != 10 && b[pos] != 13) pos++;
                else break;
            }
        }
        boolean at(String s) {
            if (pos + s.length() > b.length) return false;
            for (int i = 0; i < s.length(); i++) if (b[pos + i] != s.charAt(i)) return false;
            return true;
        }
        String word() {
            int s = pos;
            while (pos < b.length && !delim(b[pos])) pos++;
            return new String(b, s, pos - s, StandardCharsets.ISO_8859_1);
        }

        Object value() {
            skipWs();
            if (pos >= b.length || ++depth > 60) throw bad();
            try {
                int c = b[pos];
                if (c == '<' && at("<<")) {
                    pos += 2;
                    Map<String, Object> d = new HashMap<>();
                    while (true) {
                        skipWs();
                        if (at(">>")) { pos += 2; return d; }
                        Object k = value();
                        if (!(k instanceof Name)) throw bad();
                        d.put(((Name) k).s, value());
                    }
                }
                if (c == '<') return hex();
                if (c == '[') {
                    pos++;
                    List<Object> a = new ArrayList<>();
                    while (true) {
                        skipWs();
                        if (pos < b.length && b[pos] == ']') { pos++; return a; }
                        a.add(value());
                    }
                }
                if (c == '(') return literal();
                if (c == '/') return name();
                if (c == '+' || c == '-' || c == '.' || (c >= '0' && c <= '9')) return number();
                String w = word();
                if (w.equals("true")) return Boolean.TRUE;
                if (w.equals("false")) return Boolean.FALSE;
                if (w.equals("null")) return NULL;
                throw bad();
            } finally { depth--; }
        }
        static final Object NULL = new Object();

        Object number() {
            String w = word();
            if (w.indexOf('.') >= 0) return Double.valueOf(w);
            long n = Long.parseLong(w);
            if (n >= 0 && n <= Integer.MAX_VALUE && !w.startsWith("+")) {
                int save = pos;
                skipWs();
                if (pos < b.length && b[pos] >= '0' && b[pos] <= '9') {
                    String g = word();
                    skipWs();
                    if (g.matches("\\d{1,5}") && pos < b.length && b[pos] == 'R' && (pos + 1 == b.length || delim(b[pos + 1]))) { pos++; return new Ref((int) n); }
                }
                pos = save;
            }
            return n;
        }

        Name name() {
            pos++;
            StringBuilder s = new StringBuilder();
            while (pos < b.length && !delim(b[pos])) {
                int c = b[pos++] & 255;
                if (c == '#' && pos + 1 < b.length && hexVal(b[pos]) >= 0 && hexVal(b[pos + 1]) >= 0) { c = hexVal(b[pos]) * 16 + hexVal(b[pos + 1]); pos += 2; }
                s.append((char) c);
            }
            return new Name(s.toString());
        }

        static int hexVal(int c) { return c >= '0' && c <= '9' ? c - '0' : c >= 'a' && c <= 'f' ? c - 'a' + 10 : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1; }

        byte[] hex() {
            pos++;
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            int hi = -1;
            while (true) {
                if (pos >= b.length) throw bad();
                int c = b[pos++];
                if (c == '>') break;
                int v = hexVal(c);
                if (v < 0) { if (ws(c)) continue; throw bad(); }
                if (hi < 0) hi = v; else { o.write(hi * 16 + v); hi = -1; }
            }
            if (hi >= 0) o.write(hi * 16);
            return o.toByteArray();
        }

        byte[] literal() {
            pos++;
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            int open = 1;
            while (true) {
                if (pos >= b.length) throw bad();
                int c = b[pos++] & 255;
                if (c == '(') open++;
                else if (c == ')' && --open == 0) return o.toByteArray();
                else if (c == '\\') {
                    if (pos >= b.length) throw bad();
                    c = b[pos++] & 255;
                    switch (c) {
                        case 'n': c = 10; break;
                        case 'r': c = 13; break;
                        case 't': c = 9; break;
                        case 'b': c = 8; break;
                        case 'f': c = 12; break;
                        case 13: if (pos < b.length && b[pos] == 10) pos++; continue;
                        case 10: continue;
                        default:
                            if (c >= '0' && c <= '7') {
                                int v = c - '0';
                                for (int i = 0; i < 2 && pos < b.length && b[pos] >= '0' && b[pos] <= '7'; i++) v = v * 8 + b[pos++] - '0';
                                c = v & 255;
                            }
                    }
                }
                o.write(c);
            }
        }
    }
}
