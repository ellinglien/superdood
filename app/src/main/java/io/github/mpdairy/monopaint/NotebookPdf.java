package io.github.mpdairy.monopaint;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A notebook's PDF in the Supernote's Documents (Document/<app folder>, beside the notebooks, "<title>.pdf"), so it shows
 * in Documents and syncs, made from its PNG copies ({@link NotebookExport}): each page the size it is on the tablet (300
 * ppi, so it opens one to one), its picture as the PNG holds it, under an invisible text layer (render mode 3, Courier,
 * not embedded) of what the app knows: the title, the page's number, its days (made, changed), its template and its
 * fridge magnets' words where they sit, so a search lands on the word, and, with Read Handwriting on, the best guess
 * of each line of handwriting where it sits ({@link Sheet#hand}). An
 * outline (the cover, then a day at a time, its pages under it), page labels (Cover, 1, 2, …) and the document's info.
 *
 * <p>Handwriting: the Supernote keeps a PDF's handwriting beside it ("<name>.pdf.mark", by file name and page number)
 * and makes one as soon as the PDF is opened. A PDF is rewritten in place only while every page keeps its number (pages
 * changed where they are, or added at the end; the back cover is left out for that reason, it would move with every new
 * page). When a page moved or went, and a mark is there, the PDF stays as it is with its mark and the notebook's PDF
 * goes on as "<title> 2.pdf". Nothing the app didn't write is ever replaced or deleted: what it wrote is recorded
 * ({@link #STATE}: the file's name and length), and a name taken by any other file, or by a mark, is passed over.
 * Pure Java, checked on the desktop ({@code NotebookPdfChecks}).
 */
final class NotebookPdf {
    /** What the app wrote, in the notebook's PNG folder (it moves with a rename): the PDF's name and length, each page's days and words. */
    static final String STATE = ".pdf.txt";
    private static final String MARKER = "notebook pdf 1";
    private static final double POINTS = 72.0 / NotebookExport.DPI;
    /** {@link #POINTS} as a PDF number (0.24). */
    private static final String POINTS_TEXT = VectorLines.num(new StringBuilder(), POINTS).toString();

    /** Where the Supernote keeps {@code pdf}'s handwriting. */
    static File mark(File pdf) { return new File(pdf.getParentFile(), pdf.getName() + ".mark"); }

    /** One page of the PDF beyond its picture: its PNG's key, the days it was made and last changed (epoch days), and its words. */
    static final class Sheet {
        final long key, made, changed;
        final boolean cover;
        /** The page's template's name ("" for none), its magnets. */
        final String template;
        final List<Magnets.Magnet> magnets;
        /** The read handwriting's lines (page px; only the best guess kept, as the state records it), none by default. */
        final List<Transcript.Line> hand;
        Sheet(long key, long made, long changed, boolean cover, String template, List<Magnets.Magnet> magnets) { this(key, made, changed, cover, template, magnets, Collections.emptyList()); }
        Sheet(long key, long made, long changed, boolean cover, String template, List<Magnets.Magnet> magnets, List<Transcript.Line> hand) {
            this.key = key; this.made = made; this.changed = changed; this.cover = cover;
            this.template = clean(template); this.magnets = magnets; this.hand = hand;
        }
        Sheet days(long made, long changed) { return new Sheet(key, made, changed, cover, template, magnets, hand); }
        Sheet hand(List<Transcript.Line> hand) { return new Sheet(key, made, changed, cover, template, magnets, hand); }
        String line() {
            StringBuilder words = new StringBuilder();
            for (Magnets.Magnet m : magnets) {
                if (words.length() > 0) words.append('\u001e');
                words.append(m.x).append('\u001f').append(m.y).append('\u001f').append(m.tilt).append('\u001f').append(m.turn).append('\u001f').append(clean(m.word));
            }
            // The empty field: a Self-Portrait Prompt card's, until 2026-11-05. A ninth, only with handwriting: so a transcript made later writes the PDF again.
            StringBuilder said = new StringBuilder();
            for (Transcript.Line l : hand) {
                if (guess(l) == null) continue;
                if (said.length() > 0) said.append('\u001e');
                said.append(l.box[0]).append('\u001f').append(l.box[1]).append('\u001f').append(l.box[2]).append('\u001f').append(l.box[3]).append('\u001f').append(clean(guess(l)));
            }
            return "sheet\t" + Long.toHexString(key) + "\t" + made + "\t" + changed + "\t" + (cover ? 1 : 0) + "\t" + template + "\t\t" + words + (said.length() == 0 ? "" : "\t" + said);
        }
        static Sheet parse(String line) {
            String[] f = line.split("\t", -1);
            if (f.length != 8 && f.length != 9 || !f[0].equals("sheet")) return null;
            try {
                List<Magnets.Magnet> magnets = new ArrayList<>();
                if (!f[7].isEmpty()) for (String m : f[7].split("\u001e")) {
                    String[] p = m.split("\u001f", 5);
                    magnets.add(new Magnets.Magnet(p[4], Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
                }
                List<Transcript.Line> hand = new ArrayList<>();
                if (f.length == 9) for (String l : f[8].split("\u001e")) {
                    String[] p = l.split("\u001f", 5);
                    hand.add(new Transcript.Line(new float[]{Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])}, new String[]{p[4]}));
                }
                return new Sheet(Long.parseLong(f[1], 16), Long.parseLong(f[2]), Long.parseLong(f[3]), f[4].equals("1"), f[5], magnets, hand);
            } catch (RuntimeException bad) { return null; }
        }
    }
    /** {@code l}'s best guess when it is worth writing: not blank, in a box with a height and a width (NaN, infinite, flat or turned over: none). */
    private static String guess(Transcript.Line l) {
        if (l.guesses.length == 0 || l.guesses[0] == null || l.guesses[0].trim().isEmpty()) return null;
        float w = l.box[2] - l.box[0], h = l.box[3] - l.box[1];
        return w > 0 && h > 0 && !Float.isInfinite(w) && !Float.isInfinite(h) ? l.guesses[0] : null;
    }
    private static String clean(String s) { return s == null ? "" : s.replaceAll("[\\t\\r\\n\\u001e\\u001f]", " "); }

    /** What the app last wrote: the PDF's file name and length (null, 0: none), the book's title and subject, its pages. */
    static final class State {
        final String file, title, subject; final long length; final List<Sheet> sheets;
        State(String file, long length, String title, String subject, List<Sheet> sheets) {
            this.file = file; this.length = length; this.title = clean(title); this.subject = clean(subject); this.sheets = sheets;
        }
        /** The book as written, without where: the same, the same PDF. */
        String book() {
            StringBuilder s = new StringBuilder("title\t" + title + "\nsubject\t" + subject + "\n");
            for (Sheet sheet : sheets) s.append(sheet.line()).append('\n');
            return s.toString();
        }
        void write(File state) throws IOException {
            NotebookExport.write(state, (MARKER + "\nfile\t" + clean(file) + "\nlength\t" + length + "\n" + book()).getBytes(StandardCharsets.UTF_8));
        }
        /** The state in {@code state}; null when there is none or it doesn't read. */
        static State read(File state) {
            if (!state.isFile()) return null;
            try {
                List<String> lines = Files.readAllLines(state.toPath(), StandardCharsets.UTF_8);
                if (lines.size() < 5 || !lines.get(0).equals(MARKER)) return null;
                String file = value(lines.get(1), "file"), length = value(lines.get(2), "length"), title = value(lines.get(3), "title"), subject = value(lines.get(4), "subject");
                if (file == null || length == null || title == null || subject == null) return null;
                List<Sheet> sheets = new ArrayList<>();
                for (String line : lines.subList(5, lines.size())) {
                    if (line.isEmpty()) continue;
                    Sheet sheet = Sheet.parse(line);
                    if (sheet == null) return null;
                    sheets.add(sheet);
                }
                return new State(file, Long.parseLong(length), title, subject, sheets);
            } catch (IOException | RuntimeException unreadable) { return null; }
        }
        private static String value(String line, String key) { return line.startsWith(key + "\t") ? line.substring(key.length() + 1) : null; }
    }

    /** The PDF the app wrote and {@code old} records, still as it wrote it (its length); null when none is. */
    static File ours(File folder, State old) {
        if (old == null || old.file.isEmpty()) return null;
        File f = new File(folder, old.file);
        return f.isFile() && f.length() == old.length ? f : null;
    }
    /** Without a record: the PDF of {@code base}'s names whose info says it is notebook {@code name}'s (it comes first in the file), or null. */
    static File found(File folder, String base, String name) {
        File[] files = folder.listFiles();
        if (files != null) for (File f : files) {
            if (!f.isFile() || !named(f.getName(), base)) continue;
            byte[] head = new byte[4096];
            int n;
            try (java.io.InputStream in = new java.io.FileInputStream(f)) { n = in.read(head); } catch (IOException e) { continue; }
            if (n > 0 && new String(head, 0, n, StandardCharsets.ISO_8859_1).contains(notebook(name))) return f;
        }
        return null;
    }
    /** The info's record of whose PDF it is: the notebook's name as stored. */
    private static String notebook(String name) { return "/Notebook " + hex(name) + " "; }
    /** A notebook named {@code name}'s PDF title: its name without a title's hidden date ({@link DrawingNames#titled}). */
    static String title(String name) { return DrawingNames.titled(name) ? name.substring(11) : name; }
    /** A title as a file name: what a name may hold ("Notebook" for nothing). */
    static String base(String title) {
        String b = title.replaceAll("[/\\\\\\x00-\\x1f]", " ").trim();
        while (b.startsWith(".")) b = b.substring(1).trim();
        return b.isEmpty() ? "Notebook" : b;
    }
    /** Whether {@code name} is one of {@code base}'s: "base.pdf" or "base 2.pdf" …. */
    static boolean named(String name, String base) { return name.equals(base + ".pdf") || name.matches(Pattern.quote(base) + " \\d+\\.pdf"); }
    /** The first of "base.pdf", "base 2.pdf", … with neither a file nor a mark of that name. */
    static File free(File folder, String base) {
        for (int k = 1; ; k++) {
            File f = new File(folder, k == 1 ? base + ".pdf" : base + " " + k + ".pdf");
            if (!f.exists() && !mark(f).exists()) return f;
        }
    }
    /** Whether every page of {@code was} keeps its number in {@code now}: changed where it is, or pages added at the end; none moved or gone. */
    static boolean keepsNumbers(List<Long> was, List<Long> now) {
        if (now.size() < was.size()) return false;
        for (int i = 0; i < was.size(); i++) {
            Long k = was.get(i);
            // A page whose key is still there as often as it was moved; one there fewer times changed in place.
            if (!k.equals(now.get(i)) && Collections.frequency(now, k) >= Collections.frequency(was, k)) return false;
        }
        return true;
    }
    static List<Long> keys(List<Sheet> sheets) { List<Long> k = new ArrayList<>(); for (Sheet s : sheets) k.add(s.key); return k; }

    /**
     * Brings the notebook's PDF in {@code folder} in step: {@code sheets} with their PNGs ({@code pngs}), titled
     * {@code title}, {@code subject} its template, what was written recorded in {@code state}. Not written again when it
     * is as it would be, unless {@code pictures} (a page's picture changed though its key didn't). Returns the PDF.
     */
    static File update(File folder, File state, String name, String title, String subject, List<Sheet> sheets, NotebookExport.PngAt pngs, boolean pictures) throws IOException {
        State old = State.read(state), next = new State(null, 0, title, subject, sheets);
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Could not make " + folder);
        String base = base(title);
        File ours = old != null ? ours(folder, old) : found(folder, base, name);
        // No record (the PNGs' folder cleared): which pages its handwriting is on isn't known, so a marked one is left.
        boolean inPlace = ours != null && named(ours.getName(), base) && (!mark(ours).exists() || old != null && keepsNumbers(keys(old.sheets), keys(sheets)));
        File target = inPlace ? ours : free(folder, base);
        if (inPlace && old != null && !pictures && old.book().equals(next.book())) return target;
        File temporary = new File(folder, "." + target.getName() + ".tmp");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(temporary), 1 << 16)) { write(out, name, title, subject, sheets, pngs); }
        catch (IOException | RuntimeException | Error e) { temporary.delete(); throw e; }
        NotebookExport.move(temporary, target);
        new State(target.getName(), target.length(), title, subject, sheets).write(state);
        // The PDF written before under another name (renamed, or pages moved under handwriting): gone, unless it has handwriting.
        if (ours != null && !ours.equals(target) && !mark(ours).exists()) ours.delete();
        return target;
    }
    /** The notebook renamed to {@code title}: its PDF, and its handwriting with it, take the new title's name (its content follows at the next update). */
    static void rename(File folder, File state, String title) {
        State old = State.read(state);
        File ours = ours(folder, old);
        String base = base(title);
        if (ours == null || named(ours.getName(), base)) return;
        File plain = new File(folder, base + ".pdf");
        // Only the case changed: the same name on the Supernote's storage.
        File target = ours.getName().equalsIgnoreCase(plain.getName()) ? plain : free(folder, base), mark = mark(ours);
        try {
            if (mark.exists()) NotebookExport.move(mark, mark(target));
            try { NotebookExport.move(ours, target); }
            catch (IOException e) { if (mark(target).exists()) NotebookExport.move(mark(target), mark); throw e; }
            new State(target.getName(), old.length, old.title, old.subject, old.sheets).write(state);
        } catch (IOException e) { /* stays under its old name: the next update writes the new one */ }
    }
    /** The notebook deleted: its PDF goes, unless it has handwriting (then it stays, the reader's). */
    static void delete(File folder, File state) {
        File ours = ours(folder, State.read(state));
        if (ours != null && !mark(ours).exists()) ours.delete();
    }

    // Writing

    /** The PDF of {@code sheets}, each over its PNG ({@code pngs}, read one at a time as each is written). */
    static void write(OutputStream output, String name, String title, String subject, List<Sheet> sheets, NotebookExport.PngAt pngs) throws IOException { write(output, name, title, subject, sheets, pngs, 1); }
    /** As above, the first page numbered {@code from} (a page's own copy, Export Page: its number in the notebook). */
    static void write(OutputStream output, String name, String title, String subject, List<Sheet> sheets, NotebookExport.PngAt pngs, int from) throws IOException {
        NotebookExport.Counting out = new NotebookExport.Counting(output);
        int n = sheets.size(), first = 6;
        // The outline: the cover, then each run of pages made on one day, its pages under it (closed).
        List<Item> items = new ArrayList<>(), top = new ArrayList<>();
        int number = from - 1;
        for (int i = 0; i < n; i++) {
            Sheet s = sheets.get(i);
            if (s.cover) { Item c = new Item("Cover", i, null); items.add(c); top.add(c); continue; }
            number++;
            Item day = top.isEmpty() ? null : top.get(top.size() - 1);
            if (day == null || day.parent != null || day.kids == null || sheets.get(day.sheet).made != s.made) {
                day = new Item(LibraryCard.day(LocalDate.ofEpochDay(s.made)), i, null); day.kids = new ArrayList<>();
                items.add(day); top.add(day);
            }
            Item page = new Item("Page " + number, i, day);
            items.add(page); day.kids.add(page);
        }
        int outline = first + 3 * n;
        for (int k = 0; k < items.size(); k++) items.get(k).number = outline + k;
        // Each page's Vector Ink lines ({@link NotebookExport#vectorPng}) a form after the outline's items, numbered as they come.
        int forms = outline + items.size();
        long[] offsets = new long[forms + n];
        out.text("%PDF-1.4\n%âãÏÓ\n");
        long made = Long.MAX_VALUE, changed = Long.MIN_VALUE;
        for (Sheet s : sheets) { made = Math.min(made, s.made); changed = Math.max(changed, s.changed); }
        offsets[5] = out.count;
        // The info first, so whose PDF it is reads from its first bytes ({@link #found}).
        out.text("5 0 obj\n<< /Title " + hex(title) + " /Author " + hex(AppName.NAME) + " /Subject " + hex(subject) + " /Keywords " + hex(AppName.NAME + ", notebook" + (subject.isEmpty() ? "" : ", " + subject))
                + " " + notebook(name).trim() + " /Creator " + hex(AppName.NAME) + " /Producer " + hex(AppName.NAME)
                + (n == 0 ? "" : " /CreationDate " + date(made) + " /ModDate " + date(changed)) + " >>\nendobj\n");
        boolean cover = n > 0 && sheets.get(0).cover;
        offsets[1] = out.count;
        out.text("1 0 obj\n<< /Type /Catalog /Pages 2 0 R /Outlines 3 0 R /PageLabels << /Nums [" + (cover ? "0 << /P (Cover) >> 1 << /S /D >>" : from == 1 ? "0 << /S /D >>" : "0 << /S /D /St " + from + " >>")
                + "] >> /ViewerPreferences << /DisplayDocTitle true >> >>\nendobj\n");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < n; i++) kids.append(first + 3 * i).append(" 0 R ");
        offsets[2] = out.count;
        out.text("2 0 obj\n<< /Type /Pages /Count " + n + " /Kids [ " + kids + "] >>\nendobj\n");
        offsets[3] = out.count;
        out.text("3 0 obj\n<< /Type /Outlines" + (top.isEmpty() ? "" : " /First " + top.get(0).number + " 0 R /Last " + top.get(top.size() - 1).number + " 0 R") + " /Count " + top.size() + " >>\nendobj\n");
        offsets[4] = out.count;
        out.text("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>\nendobj\n");
        number = from - 1;
        for (int i = 0; i < n; i++) {
            Sheet s = sheets.get(i);
            if (!s.cover) number++;
            NotebookExport.Png png = pngs.get(i);
            int page = first + 3 * i, image = page + 1, content = page + 2;
            // Its size in page pixels: a sharp page's picture is twice them (600 dpi), placed at the same size.
            int pw = png.pageWidth(), ph = png.pageHeight();
            String w = NotebookExport.points(pw), h = NotebookExport.points(ph);
            int form = png.vectors == null ? 0 : forms++;
            offsets[page] = out.count;
            out.text(page + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + w + " " + h + "] /Resources << /XObject << /Page " + image
                    + " 0 R" + (form == 0 ? "" : " /Lines " + form + " 0 R") + " >> /Font << /F 4 0 R >> >> /Contents " + content + " 0 R >>\nendobj\n");
            offsets[image] = out.count;
            out.text(image + " 0 obj\n" + NotebookExport.imageDictionary(png));
            out.write(png.data, 0, png.data.length);
            out.text("\nendstream\nendobj\n");
            String draw = "q " + w + " 0 0 " + h + " 0 0 cm /Page Do Q\n" + (form == 0 ? "" : "/Lines Do\n") + text(title, s, number, pw, ph);
            offsets[content] = out.count;
            out.text(content + " 0 obj\n<< /Length " + draw.length() + " >>\nstream\n" + draw + "endstream\nendobj\n");
            if (form != 0) {
                // Its lines in page pixels, y down: mapped onto the page at 300 ppi.
                offsets[form] = out.count;
                out.text(form + " 0 obj\n<< /Type /XObject /Subtype /Form /BBox [0 0 " + pw + " " + ph + "] /Matrix [" + POINTS_TEXT + " 0 0 -" + POINTS_TEXT + " 0 " + h
                        + "] /Filter /FlateDecode /Length " + png.vectors.length + " >>\nstream\n");
                out.write(png.vectors, 0, png.vectors.length);
                out.text("\nendstream\nendobj\n");
            }
        }
        for (int k = 0; k < items.size(); k++) {
            Item it = items.get(k);
            List<Item> siblings = it.parent == null ? top : it.parent.kids;
            int at = siblings.indexOf(it);
            StringBuilder o = new StringBuilder(it.number + " 0 obj\n<< /Title " + hex(it.title) + " /Parent " + (it.parent == null ? 3 : it.parent.number) + " 0 R");
            if (at > 0) o.append(" /Prev ").append(siblings.get(at - 1).number).append(" 0 R");
            if (at < siblings.size() - 1) o.append(" /Next ").append(siblings.get(at + 1).number).append(" 0 R");
            if (it.kids != null && !it.kids.isEmpty())
                o.append(" /First ").append(it.kids.get(0).number).append(" 0 R /Last ").append(it.kids.get(it.kids.size() - 1).number).append(" 0 R /Count -").append(it.kids.size());
            o.append(" /Dest [").append(first + 3 * it.sheet).append(" 0 R /Fit] >>\nendobj\n");
            offsets[it.number] = out.count;
            out.text(o.toString());
        }
        long xref = out.count;
        StringBuilder table = new StringBuilder("xref\n0 " + forms + "\n0000000000 65535 f \n");
        for (int k = 1; k < forms; k++) table.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[k]));
        table.append("trailer\n<< /Size ").append(forms).append(" /Root 1 0 R /Info 5 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        out.text(table.toString());
        out.flush();
    }
    /** A day as a PDF date, at noon so no reader's time zone moves it to another day. */
    private static String date(long day) { return "(D:" + LocalDate.ofEpochDay(day).toString().replace("-", "") + "120000)"; }
    private static final class Item {
        final String title; final int sheet; final Item parent; List<Item> kids; int number;
        Item(String title, int sheet, Item parent) { this.title = title; this.sheet = sheet; this.parent = parent; }
    }

    /** A page's invisible words: at its top left the title and page, its days, its template and its card; each magnet's word on its tile. */
    static String text(String title, Sheet s, int number, int width, int height) {
        double w = width * POINTS, h = height * POINTS, margin = 7.2;
        StringBuilder c = new StringBuilder("BT 3 Tr\n");
        List<String> lines = new ArrayList<>();
        lines.add(s.cover ? title : title + ", page " + number);
        LocalDate made = LocalDate.ofEpochDay(s.made), changed = LocalDate.ofEpochDay(s.changed);
        lines.add(LibraryCard.day(made) + (s.changed != s.made ? ", changed " + LibraryCard.day(changed) : ""));
        if (!s.template.isEmpty()) lines.add(s.template);
        double y = h - margin;
        for (String line : lines) {
            // Courier is 0.6 em a letter: as large as fits the page's width, 8 pt at most.
            double size = Math.max(1, Math.min(8, (w - 2 * margin) / (.6 * Math.max(1, line.length()))));
            y -= size * 1.25;
            c.append(String.format(Locale.ROOT, "/F %.2f Tf 100 Tz 1 0 0 1 %.2f %.2f Tm ", size, margin, y)).append(literal(line)).append(" Tj\n");
        }
        for (Magnets.Magnet m : s.magnets) {
            double ems = 0;
            for (int i = 0; i < m.word.length(); i++) ems += Magnets.advance(m.word.charAt(i));
            if (ems == 0) continue;
            double a = Math.toRadians(Magnets.degrees(m.tilt, m.turn)), cos = Math.cos(a), sin = Math.sin(a);
            // The word's baseline start, from the tile's centre, untilted (page pixels, y down), then turned as the tile is.
            double lx = -ems * Magnets.EM / 2, ly = Magnets.BASELINE - Magnets.HEIGHT / 2.0;
            double px = m.x + lx * cos - ly * sin, py = m.y + lx * sin + ly * cos;
            c.append(String.format(Locale.ROOT, "/F %.2f Tf %.2f Tz %.4f %.4f %.4f %.4f %.2f %.2f Tm ", Magnets.EM * POINTS, 100 * ems / (.6 * m.word.length()),
                    cos, -sin, sin, cos, px * POINTS, h - py * POINTS)).append(literal(m.word)).append(" Tj\n");
        }
        // A search lands on the handwriting and copying a line gives its best reading; the page looks the same.
        for (Transcript.Line l : s.hand) {
            String guess = guess(l);
            double size = Math.max(1, (l.box[3] - l.box[1]) * POINTS * .8), across = (l.box[2] - l.box[0]) * POINTS;
            if (guess == null) continue;
            c.append(String.format(Locale.ROOT, "/F %.2f Tf %.2f Tz 1 0 0 1 %.2f %.2f Tm ", size, 100 * across / (.6 * size * guess.length()), l.box[0] * POINTS, h - l.box[3] * POINTS))
                    .append(literal(guess)).append(" Tj\n");
        }
        return c.append("ET\n").toString();
    }

    /** A text string any reader shows in full: UTF-16 with its byte order mark, in hex. */
    static String hex(String s) {
        StringBuilder b = new StringBuilder("<FEFF");
        for (int i = 0; i < s.length(); i++) b.append(String.format(Locale.ROOT, "%04X", (int) s.charAt(i)));
        return b.append('>').toString();
    }
    /** {@code s} in WinAnsi as a literal string for Courier: a character it hasn't is "?". */
    // shortcut: Greek, Cyrillic and other words outside WinAnsi become "?" and aren't found; a ToUnicode CMap if word packs in them are used.
    static String literal(String s) {
        StringBuilder b = new StringBuilder("(");
        for (int i = 0; i < s.length(); i++) {
            int v = ansi(s.charAt(i));
            if (v == '(' || v == ')' || v == '\\') b.append('\\').append((char) v);
            else if (v < 32 || v > 126) b.append(String.format(Locale.ROOT, "\\%03o", v));
            else b.append((char) v);
        }
        return b.append(')').toString();
    }
    private static final String ANSI_HIGH = "€\u0000‚ƒ„…†‡ˆ‰Š‹Œ\u0000Ž\u0000\u0000‘’“”•–—˜™š›œ\u0000žŸ";
    /** {@code c}'s WinAnsi code ('?' for none). */
    static int ansi(char c) {
        if (c >= 32 && c < 127 || c >= 0xa0 && c <= 0xff) return c;
        int k = c == 0 ? -1 : ANSI_HIGH.indexOf(c);
        return k >= 0 ? 0x80 + k : '?';
    }

    private NotebookPdf() {}
}
