package io.github.mpdairy.monopaint;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read Handwriting's transcript of one notebook (docs/superpowers/specs/2026-11-09-read-handwriting-design.md, "The
 * transcripts"): each page's lines of writing, a line's box (page px) and the reader's three guesses, by the page's CRC
 * in the notebook file (pages have no other lasting identity: a page moved keeps its CRC, a page changed gets a new one
 * and is read again). A file of its own in the app's storage, never inside the notebook. Search matches every word of
 * the query in any one guess of a line, capitals and accents ignored. Pure; checked on the desktop ({@code TranscriptChecks}).
 *
 * <p>The file: a first line {@code superdood-transcript 1}; then {@code page <crc hex>} and, under it, a
 * {@code line <l> <t> <r> <b>} line followed by its three guesses, each a tab after it.
 */
final class Transcript {
    static final String HEAD = "superdood-transcript 1";

    static final class Line {
        final float[] box;
        final String[] guesses;
        Line(float[] box, String[] guesses) { this.box = box; this.guesses = guesses; }
    }

    private final Map<Long, List<Line>> pages = new LinkedHashMap<>();

    Collection<Long> pages() { return Collections.unmodifiableSet(pages.keySet()); }
    boolean has(long crc) { return pages.containsKey(crc); }
    List<Line> lines(long crc) { List<Line> l = pages.get(crc); return l == null ? Collections.<Line>emptyList() : l; }
    void put(long crc, List<Line> lines) { pages.put(crc, new ArrayList<>(lines)); }
    /** Drops pages whose CRC is no longer one of the notebook's. */
    void keepOnly(Collection<Long> crcs) { pages.keySet().retainAll(crcs); }

    /** Page {@code crc}'s lines matching {@code query}: every word of it in one guess, folded. */
    List<Line> find(long crc, String query) {
        String[] words = words(query);
        List<Line> found = new ArrayList<>();
        if (words.length == 0) return found;
        for (Line l : lines(crc)) if (match(l, words) != null) found.add(l);
        return found;
    }
    /** {@code query}'s words, folded; none for a query of no letters or digits. */
    static String[] words(String query) { String f = fold(query); return f.isEmpty() ? new String[0] : f.split(" "); }
    /** The first of {@code l}'s guesses holding every one of {@code words} (folded), else null: what a search shows of the line. */
    static String match(Line l, String[] words) {
        for (String g : l.guesses) {
            String folded = fold(g);
            boolean all = true;
            for (String w : words) if (!folded.contains(w)) { all = false; break; }
            if (all) return g;
        }
        return null;
    }

    private static final java.util.regex.Pattern MARKS = java.util.regex.Pattern.compile("\\p{M}+");
    private static final java.util.regex.Pattern NOT_WORD = java.util.regex.Pattern.compile("[^\\p{L}\\p{N}]+");

    /** Lower case, accents dropped, anything but letters and digits a space (runs one space), trimmed. */
    static String fold(String s) {
        String plain = MARKS.matcher(java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)).replaceAll("");
        return NOT_WORD.matcher(plain.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    void write(File file) throws IOException {
        StringBuilder s = new StringBuilder(HEAD).append('\n');
        for (Map.Entry<Long, List<Line>> p : pages.entrySet()) {
            s.append("page ").append(Long.toHexString(p.getKey())).append('\n');
            for (Line l : p.getValue()) {
                s.append("line ").append(l.box[0]).append(' ').append(l.box[1]).append(' ').append(l.box[2]).append(' ').append(l.box[3]);
                for (int i = 0; i < 3; i++) s.append('\t').append(i < l.guesses.length ? clean(l.guesses[i]) : "");
                s.append('\n');
            }
        }
        File temp = new File(file.getPath() + ".new");
        java.nio.file.Files.write(temp.toPath(), s.toString().getBytes(StandardCharsets.UTF_8));
        java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
    /** The file's transcript; empty when there is none or it doesn't read (it is made again). */
    static Transcript read(File file) {
        Transcript t = new Transcript();
        if (!file.isFile()) return t;
        try {
            List<String> rows = java.nio.file.Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            if (rows.isEmpty() || !rows.get(0).equals(HEAD)) return new Transcript();
            List<Line> page = null;
            for (String row : rows.subList(1, rows.size())) {
                if (row.startsWith("page ")) { page = new ArrayList<>(); t.pages.put(Long.parseUnsignedLong(row.substring(5), 16), page); }
                else if (row.startsWith("line ") && page != null) {
                    String[] parts = row.split("\t", -1);
                    String[] b = parts[0].substring(5).split(" ");
                    float[] box = {Float.parseFloat(b[0]), Float.parseFloat(b[1]), Float.parseFloat(b[2]), Float.parseFloat(b[3])};
                    String[] guesses = new String[3];
                    for (int i = 0; i < 3; i++) guesses[i] = i + 1 < parts.length ? parts[i + 1] : "";
                    page.add(new Line(box, guesses));
                }
            }
        } catch (IOException | RuntimeException e) { return new Transcript(); }
        return t;
    }
    private static String clean(String s) { return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' '); }

    /** Notebook {@code path}'s transcript file in {@code dir}: its path, every character a file name can't hold a code. */
    static File file(File dir, String path) {
        StringBuilder name = new StringBuilder();
        for (char c : path.toCharArray()) {
            if ((c < 128 && Character.isLetterOrDigit(c)) || c == ' ' || c == '-' || c == '_') name.append(c);
            else name.append('%').append(String.format(Locale.ROOT, "%04x", (int) c));
        }
        if (name.length() > 200) { // file names top out at 255 bytes; the CRC keeps two long paths apart
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(path.getBytes(StandardCharsets.UTF_8));
            name.setLength(180);
            name.append('~').append(Long.toHexString(crc.getValue()));
        }
        return new File(dir, name + ".txt");
    }
}
