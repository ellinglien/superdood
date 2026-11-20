package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;

/** {@link PdfOutline} on PDFs built here: classic and object-stream files, named destinations, odd strings, and files with no (or no readable) outline. */
public final class PdfOutlineChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static byte[] b(String s) { return s.getBytes(StandardCharsets.ISO_8859_1); }
    static byte[] cat(byte[]... parts) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.write(p);
        return o.toByteArray();
    }
    static byte[] deflate(byte[] d) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(o)) { z.write(d); }
        return o.toByteArray();
    }

    /** Objects 2 pages tree and 3-5 pages; the catalog is 1 and the outline 6-9. */
    static String pages() { return "2 0 obj\n<< /Type /Pages /Kids [3 0 R 4 0 R 5 0 R] /Count 3 >>\nendobj\n"
        + "3 0 obj\n<< /Type /Page /Parent 2 0 R >>\nendobj\n4 0 obj\n<< /Type /Page /Parent 2 0 R >>\nendobj\n5 0 obj\n<< /Type /Page /Parent 2 0 R >>\nendobj\n"; }
    static String catalog(String extra) { return "1 0 obj\n<< /Type /Catalog /Pages 2 0 R /Outlines 6 0 R " + extra + ">>\nendobj\n"; }
    static String outline(String oneDest, String twoDest, String twoADest) {
        return "6 0 obj\n<< /Type /Outlines /First 7 0 R /Last 8 0 R /Count 3 >>\nendobj\n"
            + "7 0 obj\n<< /Title (One) /Parent 6 0 R /Next 8 0 R " + oneDest + " >>\nendobj\n"
            + "8 0 obj\n<< /Title (Two) /Parent 6 0 R /Prev 7 0 R /First 9 0 R /Last 9 0 R " + twoDest + " >>\nendobj\n"
            + "9 0 obj\n<< /Title (Two A) /Parent 8 0 R " + twoADest + " >>\nendobj\n";
    }
    static byte[] classic(String body) throws Exception {
        return b("%PDF-1.4\n" + body + "xref\n0 1\n0000000000 65535 f \ntrailer\n<< /Size 10 /Root 1 0 R >>\nstartxref\n0\n%%EOF\n");
    }
    static void same(List<PdfOutline.Item> r, String what) {
        check(r.size() == 3, what + ": 3 items, got " + r.size());
        check(r.get(0).title.equals("One") && r.get(0).depth == 0 && r.get(0).page == 0, what + ": One");
        check(r.get(1).title.equals("Two") && r.get(1).depth == 0 && r.get(1).page == 2, what + ": Two");
        check(r.get(2).title.equals("Two A") && r.get(2).depth == 1 && r.get(2).page == 2, what + ": Two A");
    }

    public static void main(String[] args) throws Exception {
        String direct = outline("/Dest [3 0 R /Fit]", "/Dest [5 0 R /Fit]", "/Dest [5 0 R /XYZ 0 0 0]");
        same(PdfOutline.read(classic(catalog("") + pages() + direct)), "classic /Dest");

        String named = outline("/Dest [3 0 R /Fit]", "/A << /S /GoTo /D (two) >>", "/Dest (two)");
        same(PdfOutline.read(classic(catalog("/Names << /Dests << /Names [(two) [5 0 R /XYZ 0 0 0]] >> >> ") + pages() + named)), "name tree");
        same(PdfOutline.read(classic(catalog("/Names << /Dests << /Kids [10 0 R] >> >> ") + pages() + named
            + "10 0 obj\n<< /Kids [11 0 R] /Limits [(a) (z)] >>\nendobj\n11 0 obj\n<< /Names [(one) [3 0 R /Fit] (two) << /D [5 0 R /Fit] >>] >>\nendobj\n")), "kids level");
        same(PdfOutline.read(classic(catalog("/Dests << /two [5 0 R /Fit] >> ") + pages() + outline("/Dest [3 0 R /Fit]", "/Dest /two", "/A << /S /GoTo /D /two >>"))), "catalog /Dests");

        // Objects inside a deflated /ObjStm, an xref stream as the trailer.
        StringBuilder head = new StringBuilder(), data = new StringBuilder();
        Matcher m = Pattern.compile("(\\d+) 0 obj\n(.*?)\nendobj\n", Pattern.DOTALL).matcher(catalog("") + pages() + direct);
        int n = 0;
        while (m.find()) { head.append(m.group(1)).append(' ').append(data.length()).append(' '); data.append(m.group(2)).append('\n'); n++; }
        byte[] z = deflate(b(head + "\n" + data));
        byte[] os = cat(b("%PDF-1.5\n20 0 obj\n<< /Type /ObjStm /N " + n + " /First " + (head.length() + 1) + " /Filter /FlateDecode /Length " + z.length + " >>\nstream\n"), z, b("\nendstream\nendobj\n"),
            b("21 0 obj\n<< /Type /XRef /Size 22 /Root 1 0 R /W [1 2 1] /Length 0 >>\nstream\n\nendstream\nendobj\nstartxref\n0\n%%EOF\n"));
        same(PdfOutline.read(os), "object stream");

        // Strings.
        byte[] u = cat(b("%PDF-1.4\n" + catalog("") + pages() + "6 0 obj\n<< /First 7 0 R >>\nendobj\n7 0 obj\n<< /Title <FEFF00DC006E00EF> /Dest [3 0 R /Fit] /Next 8 0 R >>\nendobj\n"
            + "8 0 obj\n<< /Title (a\\(b\\)(c)\\101\\\n d\\\\) /Dest [4 0 R /Fit] /Next 9 0 R >>\nendobj\n9 0 obj\n<< /Title () /Dest [4 0 R /Fit] >>\nendobj\n"
            + "trailer\n<< /Root 1 0 R >>\n"));
        List<PdfOutline.Item> r = PdfOutline.read(u);
        check(r.size() == 2, "empty title skipped, got " + r.size());
        check(r.get(0).title.equals("Ünï"), "UTF-16BE title");
        check(r.get(1).title.equals("a(b)(c)A d\\") && r.get(1).page == 1, "literal escapes: " + r.get(1).title);

        check(PdfOutline.read(classic(catalog("").replace("/Outlines 6 0 R ", "") + pages())).isEmpty(), "no outlines");
        check(PdfOutline.read(b("%PDF-1.4\n" + catalog("") + pages() + direct + "trailer\n<< /Root 1 0 R /Encrypt 30 0 R >>\n")).isEmpty(), "encrypted");
        check(PdfOutline.read(new byte[0]).isEmpty() && PdfOutline.read(b("garbage obj << [ ( <FE stream")).isEmpty(), "garbage");
        byte[] bin = new byte[5000];
        new java.util.Random(1).nextBytes(bin);
        check(PdfOutline.read(bin).isEmpty(), "random bytes");
        check(PdfOutline.read(null).isEmpty(), "null");
        String loop = direct.replace("/Title (Two) /Parent 6 0 R /Prev 7 0 R", "/Title (Two) /Next 7 0 R /Parent 6 0 R /Prev 7 0 R");
        check(loop.contains("/Next 7 0 R /Parent") && PdfOutline.read(classic(catalog("") + pages() + loop)).size() == 3, "a /Next loop finishes");
        // No PDF with bookmarks is tracked in the repo, so only built ones are checked.
        System.out.println("PASS PdfOutlineChecks");
    }
}
