package io.github.mpdairy.monopaint;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Open File: an EPUB's spine, chapters and lock, read from small zips built here. */
public final class EpubBookChecks {
    static final String CONTAINER = "<?xml version=\"1.0\"?><container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\" version=\"1.0\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>";
    static final String OPF3 = "<?xml version=\"1.0\"?><package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\"><manifest>"
        + "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"
        + "<item id=\"a\" href=\"text/c1.xhtml\" media-type=\"application/xhtml+xml\"/>"
        + "<item id=\"b\" href=\"text/c2.xhtml\" media-type=\"application/xhtml+xml\"/>"
        + "<item id=\"c\" href=\"text/my%20c3.xhtml\" media-type=\"application/xhtml+xml\"/>"
        + "<item id=\"d\" href=\"text/skip.xhtml\" media-type=\"application/xhtml+xml\"/>"
        + "<item id=\"img\" href=\"cover.png\" media-type=\"image/png\"/></manifest>"
        + "<spine><itemref idref=\"a\"/><itemref idref=\"d\" linear=\"no\"/><itemref idref=\"img\"/><itemref idref=\"b\"/><itemref idref=\"c\"/></spine></package>";
    static final String NAV = "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\"><body>"
        + "<nav epub:type=\"landmarks\"><ol><li><a href=\"text/c2.xhtml\">Landmark</a></li></ol></nav>"
        + "<nav epub:type=\"toc\"><ol><li><a href=\"text/c1.xhtml\">  One\n </a></li>"
        + "<li><a href=\"text/c2.xhtml#s1\">Two</a><ol><li><a href=\"text/c2.xhtml#s2\">Two A</a><ol><li><a href=\"text/c2.xhtml#s3\">Deep</a></li></ol></li></ol></li>"
        + "<li><a href=\"text/my%20c3.xhtml\">Three</a></li><li><a href=\"text/skip.xhtml\">Not in spine</a></li><li><a href=\"text/c1.xhtml\"> </a></li></ol></nav></body></html>";
    static final String OPF2 = "<?xml version=\"1.0\"?><package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\"><manifest>"
        + "<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>"
        + "<item id=\"a\" href=\"c1.html\" media-type=\"application/xhtml+xml\"/><item id=\"b\" href=\"c2.html\" media-type=\"text/html\"/></manifest>"
        + "<spine toc=\"ncx\"><itemref idref=\"a\"/><itemref idref=\"b\"/></spine></package>";
    static final String NCX = "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\"><navMap>"
        + "<navPoint id=\"1\"><navLabel><text>Part</text></navLabel><content src=\"c1.html\"/>"
        + "<navPoint id=\"2\"><navLabel><text>Sub</text></navLabel><content src=\"c2.html#x\"/></navPoint></navPoint></navMap></ncx>";

    public static void main(String[] args) throws Exception {
        EpubBook b = read(CONTAINER, OPF3, "OEBPS/nav.xhtml", NAV, null, null);
        check(b.spine.toString().equals("[OEBPS/text/c1.xhtml, OEBPS/text/c2.xhtml, OEBPS/text/my c3.xhtml]"), "spine: " + b.spine);
        check(describe(b).equals("One/0/0;Two/0/1;Two A/1/1;Three/0/2;"), "nav chapters: " + describe(b));
        check(fragments(b).equals("[null, s1, s2, null]"), "nav chapters' fragments: " + fragments(b));

        EpubBook e2 = read(CONTAINER, OPF2, "OEBPS/toc.ncx", NCX, null, null);
        check(e2.spine.size() == 2 && describe(e2).equals("Part/0/0;Sub/1/1;"), "ncx chapters: " + describe(e2));
        check(fragments(e2).equals("[null, x]"), "ncx chapters' fragments: " + fragments(e2));

        String opfBoth = OPF2.replace("<item id=\"ncx\"", "<item id=\"nv\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/><item id=\"ncx\"");
        EpubBook fb = read(CONTAINER, opfBoth, "OEBPS/toc.ncx", NCX, "OEBPS/nav.xhtml",
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><nav><ol><li><a href=\"c1.html\">A&nbsp;B</a></li></ol></nav></body></html>");
        check(describe(fb).equals("Part/0/0;Sub/1/1;"), "a nav that won't parse falls back to the NCX: " + describe(fb));
        EpubBook none = read(CONTAINER, OPF2, "OEBPS/x.txt", "", null, null);
        check(none.chapters.isEmpty() && none.spine.size() == 2, "no nav, no NCX file: no chapters");

        String enc = "<encryption xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\" xmlns:enc=\"http://www.w3.org/2001/04/xmlenc#\"><enc:EncryptedData><enc:EncryptionMethod Algorithm=\"%s\"/></enc:EncryptedData></encryption>";
        check(read(CONTAINER, OPF3, "OEBPS/nav.xhtml", NAV, "META-INF/encryption.xml", String.format(enc, "http://www.idpf.org/2008/embedding")).spine.size() == 3, "font obfuscation reads");
        check(EpubBook.LOCKED.equals(message("META-INF/encryption.xml", String.format(enc, "http://www.w3.org/2001/04/xmlenc#aes128-cbc"))), "an encrypted book is locked");
        check(EpubBook.LOCKED.equals(message("META-INF/rights.xml", "<rights/>")), "rights.xml is locked");
        File f = File.createTempFile("epub", ".epub");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) { put(z, "a.txt", "x"); }
        try (ZipFile zf = new ZipFile(f)) { EpubBook.read(zf); check(false, "no container.xml throws"); }
        catch (IOException x) { check(!EpubBook.LOCKED.equals(x.getMessage()), "no container.xml is not a lock: " + x.getMessage()); }
        System.out.println("PASS: an EPUB's spine, chapters (nav, NCX, fallback) and their fragments, and lock are read");
    }

    static String describe(EpubBook b) {
        StringBuilder s = new StringBuilder();
        for (EpubBook.Chapter c : b.chapters) s.append(c.title).append('/').append(c.depth).append('/').append(c.spine).append(';');
        return s.toString();
    }

    static String fragments(EpubBook b) {
        java.util.List<String> f = new java.util.ArrayList<>();
        for (EpubBook.Chapter c : b.chapters) f.add(c.fragment);
        return f.toString();
    }

    /** A zip with the container, the OPF, and up to two more files. */
    static EpubBook read(String container, String opf, String p1, String t1, String p2, String t2) throws IOException {
        File f = File.createTempFile("epub", ".epub");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            put(z, "META-INF/container.xml", container); put(z, "OEBPS/content.opf", opf);
            put(z, p1, t1); if (p2 != null) put(z, p2, t2);
        }
        try (ZipFile zf = new ZipFile(f)) { return EpubBook.read(zf); }
    }
    static String message(String path, String text) {
        try { read(CONTAINER, OPF3, "OEBPS/nav.xhtml", NAV, path, text); return "read"; } catch (IOException x) { return x.getMessage(); }
    }
    static void put(ZipOutputStream z, String name, String text) throws IOException { z.putNextEntry(new ZipEntry(name)); z.write(text.getBytes("UTF-8")); z.closeEntry(); }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
