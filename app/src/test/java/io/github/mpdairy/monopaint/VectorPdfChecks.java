package io.github.mpdairy.monopaint;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.zip.Inflater;

/**
 * Vector Ink in the PDF copies ({@link NotebookExport#vectorPng}, {@link VectorLines#pdf}): a line's PDF outline covers
 * what its outline covers, within a page pixel, its cut ends flat too; a page with Vector Ink's PDF page has its picture
 * without the lines' pixels and the lines over it as vectors, in Export Page's PDF, Export Now's and the notebook's (kept
 * by its key beside the PNG copy, which keeps the lines' pixels; a record from before reads the page once; gone with the
 * page); a page without them is written as before; lines under another page's marks stay pixels.
 */
public final class VectorPdfChecks {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        outline();
        pages();
        stacked();
        File dir = Files.createTempDirectory("vectorpdf").toFile();
        try { notebook(dir); } finally { NotebookMigrationChecks.delete(dir); }
        String size = size();
        System.out.println("PASS: Vector Ink in PDFs: a line's PDF outline covers its outline within a pixel, pressure and flat cut ends too; Export Page, Export Now and the notebook's PDF draw "
                + "a page's lines as vectors over its picture without their pixels, the PNG copy keeping them, the notebook's kept by its key, a record from before read once, gone with the page; "
                + "a page without lines written as before; lines under another page's marks stay pixels, and under a higher layer's, a layer's over its own pen's marks as vectors (its other marks in the picture), a hidden layer's neither; " + size);
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    // The outline

    /** A line with pressure, and a thick one cut in pieces by a dab (flat ends): the PDF's outline as {@link VectorLines#cover}'s, at 4×. */
    static void outline() {
        float[][] h = VectorInkChecks.hand(60, 7);
        VectorLines thin = VectorLines.NONE.with(VectorLines.smoothed(0, 6, h[0], h[1], h[2], 60));
        VectorLines thick = VectorLines.NONE.with(VectorLines.smoothed(80, 24, h[0], h[1], h[2], 60)).cut(100, 62, 14);
        check(thick.size() == 2 && thick.get(0).caps == VectorLines.END_CUT && thick.get(1).caps == VectorLines.START_CUT, "the dab cuts the thick line in two, ends flat");
        VectorLines dot = VectorLines.NONE.with(new VectorLines.Stroke(0, 9, 0, new int[]{50 * VectorLines.SUB, 50 * VectorLines.SUB}, new byte[]{(byte) 255}));
        for (VectorLines lines : Arrays.asList(thin, thick, dot)) same(lines);
        StringBuilder ops = new StringBuilder();
        thick.pdf(ops, g -> g);
        check(ops.indexOf("0.314 G") >= 0 && ops.indexOf("W* n") > 0 && ops.indexOf(" re\n") > 0, "its grey and its cut ends' clips: " + ops.substring(0, 80));
    }
    /** {@code lines}' PDF outline against their coverage at 4×: every pixel alike but within a page pixel of the edge. */
    static void same(VectorLines lines) {
        int s = 4, l = 0, t = 0, w = 220 * s, hh = 130 * s;
        byte[] cover = new byte[w * hh], one = new byte[w * hh];
        for (int k = 0; k < lines.size(); k++) {
            Arrays.fill(one, (byte) 0);
            VectorLines.cover(lines.get(k), s, 0, 0, l, t, w, hh, one);
            for (int i = 0; i < one.length; i++) cover[i] = (byte) Math.max(cover[i] & 255, one[i] & 255);
        }
        StringBuilder ops = new StringBuilder();
        lines.pdf(ops, g -> g);
        Painted painted = new Painted(ops.toString());
        boolean[] in = new boolean[w * hh];
        int inside = 0;
        for (int y = 0; y < hh; y++) for (int x = 0; x < w; x++) if (in[y * w + x] = painted.covers((x + .5) / s, (y + .5) / s)) inside++;
        check(inside > 50, "the PDF outline covers something: " + inside);
        int off = 0;
        for (int y = 0; y < hh; y++) for (int x = 0; x < w; x++) {
            boolean pixel = (cover[y * w + x] & 255) >= 128;
            if (pixel == in[y * w + x]) continue;
            off++;
            // Only at the edge: within a page pixel there is both outline and none.
            boolean on = false, offIt = false;
            for (int dy = -s; dy <= s; dy++) for (int dx = -s; dx <= s; dx++) {
                int X = x + dx, Y = y + dy;
                if (X < 0 || Y < 0 || X >= w || Y >= hh) continue;
                if ((cover[Y * w + X] & 255) >= 128) on = true; else offIt = true;
            }
            check(on && offIt, "the PDF outline differs from the outline only at its edge: " + x / (double) s + "," + y / (double) s);
        }
        check(off < inside / 10, "few edge pixels differ: " + off + " of " + inside);
    }
    /** What {@link VectorLines#pdf}'s operators paint: round-capped, round-joined lines, each within the clips in force. */
    static final class Painted {
        final List<double[]> segments = new ArrayList<>();
        final List<List<List<List<double[]>>>> clipsOf = new ArrayList<>();
        Painted(String ops) {
            List<String> stack = new ArrayList<>();
            List<List<double[]>> path = new ArrayList<>();
            List<List<List<double[]>>> clips = new ArrayList<>();
            List<Integer> saved = new ArrayList<>();
            double width = 1;
            boolean clip = false;
            for (String tok : ops.trim().split("\\s+")) {
                switch (tok) {
                    case "J": case "j": case "G": stack.clear(); break;
                    case "q": saved.add(clips.size()); break;
                    case "Q": while (clips.size() > saved.get(saved.size() - 1)) clips.remove(clips.size() - 1); saved.remove(saved.size() - 1); break;
                    case "w": width = d(stack, 0); stack.clear(); break;
                    case "m": path.add(new ArrayList<>(Collections.singletonList(new double[]{d(stack, 0), d(stack, 1)}))); stack.clear(); break;
                    case "l": path.get(path.size() - 1).add(new double[]{d(stack, 0), d(stack, 1)}); stack.clear(); break;
                    case "c": {
                        List<double[]> sub = path.get(path.size() - 1);
                        double[] p0 = sub.get(sub.size() - 1);
                        for (int k = 1; k <= 32; k++) {
                            double u = k / 32.0, a = (1 - u) * (1 - u) * (1 - u), b = 3 * u * (1 - u) * (1 - u), c = 3 * u * u * (1 - u), e = u * u * u;
                            sub.add(new double[]{a * p0[0] + b * d(stack, 0) + c * d(stack, 2) + e * d(stack, 4), a * p0[1] + b * d(stack, 1) + c * d(stack, 3) + e * d(stack, 5)});
                        }
                        stack.clear(); break;
                    }
                    case "re": {
                        double x = d(stack, 0), y = d(stack, 1), rw = d(stack, 2), rh = d(stack, 3);
                        path.add(new ArrayList<>(Arrays.asList(new double[]{x, y}, new double[]{x + rw, y}, new double[]{x + rw, y + rh}, new double[]{x, y + rh})));
                        stack.clear(); break;
                    }
                    case "h": break;
                    case "W*": clip = true; break;
                    case "n": if (clip) clips.add(new ArrayList<>(path)); clip = false; path.clear(); break;
                    case "S":
                        for (List<double[]> sub : path) for (int k = 0; k < Math.max(1, sub.size() - 1); k++) {
                            double[] a = sub.get(k), b = sub.get(Math.min(k + 1, sub.size() - 1));
                            segments.add(new double[]{a[0], a[1], b[0], b[1], width / 2});
                            clipsOf.add(new ArrayList<>(clips));
                        }
                        path.clear(); break;
                    default: stack.add(tok);
                }
            }
        }
        private static double d(List<String> stack, int k) { return Double.parseDouble(stack.get(k)); }
        boolean covers(double x, double y) {
            for (int k = 0; k < segments.size(); k++) {
                double[] s = segments.get(k);
                if (VectorInkChecks.toSegment(x, y, s[0], s[1], s[2], s[3]) > s[4]) continue;
                boolean kept = true;
                for (List<List<double[]>> c : clipsOf.get(k)) if (!evenOdd(c, x, y)) { kept = false; break; }
                if (kept) return true;
            }
            return false;
        }
        private static boolean evenOdd(List<List<double[]>> polygons, double x, double y) {
            boolean in = false;
            for (List<double[]> p : polygons) for (int i = 0, j = p.size() - 1; i < p.size(); j = i++) {
                double[] a = p.get(i), b = p.get(j);
                if ((a[1] > y) != (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) in = !in;
            }
            return in;
        }
    }

    // Pages

    static final long TODAY = 20_400;

    /** Export Page's PDF of a page with Vector Ink over a wash: the lines as vectors over the wash alone; its PNG as before; a page without lines as before. */
    static void pages() throws Exception {
        ToneDocument d = VectorInkChecks.page();
        List<PackedPage> pages = Collections.singletonList(NotebookPdfChecks.packed(d));
        NotebookPdfChecks.Pdf pdf = new NotebookPdfChecks.Pdf(PageExport.bytes("V", 0, PageExport.PDF, pages, NotebookOptions.DEFAULT, null, null, null, TODAY));
        int page = pdf.pages().get(0);
        lines(pdf, page, d);
        // The PNG keeps the lines' pixels and nothing else.
        byte[] png = PageExport.bytes("V", 0, PageExport.PNG, pages, NotebookOptions.DEFAULT, null, null, null, TODAY);
        check(NotebookExport.read(png).vectors == null && Arrays.equals(png, NotebookExport.png(d.layerSnapshot(), pages.get(0).crc)), "Export Page's PNG as before, the lines' pixels in it");
        // Export Now's PDF the same.
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        NotebookExport.pdf(out, "V", "V", pages, null, null, NotebookOptions.DEFAULT, null, null, TODAY);
        NotebookPdfChecks.Pdf now = new NotebookPdfChecks.Pdf(out.toByteArray());
        lines(now, now.pages().get(0), d);
        // Without lines: no form, three objects a page as ever, its picture the PNG copy's.
        ToneDocument plain = NotebookPdfChecks.page(1, NotebookPdfChecks.PEN);
        check(NotebookExport.vectorPng(plain.layerSnapshot(), 1, null, false) == null, "no vector picture without lines");
        List<PackedPage> plainPages = Collections.singletonList(NotebookPdfChecks.packed(plain));
        byte[] bytes = PageExport.bytes("V", 0, PageExport.PDF, plainPages, NotebookOptions.DEFAULT, null, null, null, TODAY);
        NotebookPdfChecks.Pdf without = new NotebookPdfChecks.Pdf(bytes);
        check(!without.text.contains("/Lines") && without.text.contains("/Size 11 ") && without.content(without.pages().get(0)).startsWith("q 72.00 0 0 96.00 0 0 cm /Page Do Q\nBT 3 Tr\n"), "a page without lines as before");
        java.io.ByteArrayOutputStream old = new java.io.ByteArrayOutputStream();
        byte[] plainPng = NotebookExport.png(plain.layerSnapshot(), plainPages.get(0).crc);
        NotebookPdf.write(old, "V", "V", "", Collections.singletonList(NotebookExport.said(plainPages.get(0).crc, "", Collections.singletonList(plain)).days(TODAY, TODAY)), k -> NotebookExport.read(plainPng), 1);
        check(Arrays.equals(bytes, old.toByteArray()), "a page without lines: the same bytes as its PNG copy's PDF page");
    }
    /** {@code pdf}'s page {@code page} is {@code d}'s: its picture {@code d} without its Vector Ink layer, its lines drawn over it from a form. */
    static void lines(NotebookPdfChecks.Pdf pdf, int page, ToneDocument d) throws Exception {
        String dict = pdf.dict(page);
        check(dict.contains("/Lines ") && pdf.content(page).contains("/Page Do Q\n/Lines Do\n"), "the page draws its lines' form over its picture: " + dict);
        int form = pdf.ref(dict, "/Lines");
        String fd = pdf.dict(form);
        check(fd.contains("/Subtype /Form") && fd.contains("/BBox [0 0 200 120]") && fd.contains("/Matrix [0.24 0 0 -0.24 0 28.80]") && fd.contains("/FlateDecode"), "a form in page pixels: " + fd);
        byte[] z = pdf.stream(form);
        Inflater inflater = new Inflater(); inflater.setInput(z);
        byte[] raw = new byte[1 << 20]; int n = inflater.inflate(raw); inflater.end();
        String ops = new String(raw, 0, n, StandardCharsets.ISO_8859_1);
        StringBuilder expected = new StringBuilder();
        d.lines(InkWashLayers.INK).pdf(expected, DotPattern::exportGray);
        check(ops.equals(expected.toString()) && ops.contains(" m\n") && ops.contains(" l\n") && ops.contains("S\n"), "the form holds the lines' paths");
        ToneDocument.Snapshot s = d.layerSnapshot();
        s.layers.get(InkWashLayers.INK).visible = false;
        ToneDocument under = new ToneDocument(s);
        int[] shown = pdf.image(page);
        boolean washed = false;
        for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) {
            check(shown[y * d.width + x] == DotPattern.exportGray(under.compositeTone(x, y)), "its picture without the lines' pixels at " + x + "," + y);
            washed |= shown[y * d.width + x] != 255 && shown[y * d.width + x] != DotPattern.exportGray(d.compositeTone(x, y));
        }
        check(washed, "the wash under the lines shows where they were");
    }

    /** A stack: lines on the top page drawn as vectors; lines under another page's marks stay pixels. */
    static void stacked() throws Exception {
        ToneDocument marks = InkWashLayers.blank(200, 120);
        marks.selectLayer(InkWashLayers.INK); marks.begin();
        for (int x = 40; x < 160; x++) marks.setTone(x, 60, 0);
        marks.finish();
        ToneDocument lined = VectorInkChecks.page();
        check(NotebookExport.vectorLayers(NotebookExport.flatten(Arrays.asList(marks, lined), new boolean[2]), null).size() == 1, "lines on the top page as vectors");
        check(NotebookExport.vectorLayers(NotebookExport.flatten(Arrays.asList(lined, marks), new boolean[2]), null).isEmpty(), "lines under another page's marks stay pixels");
        check(NotebookExport.vectorLayers(NotebookExport.flatten(Arrays.asList(lined, marks), new boolean[]{false, true}), null).size() == 1, "a hidden page over them doesn't count");
        // Three layers (2026-11-06, Vector Ink on the layer in use): layer 2's lines, over its own pen's marks, as vectors;
        // layer 1's, under layer 2's marks, stay pixels; a hidden layer's are neither, and leave the layer under free.
        ToneDocument three = VectorInkChecks.mixed();
        List<ToneDocument.Layer> v = NotebookExport.vectorLayers(NotebookExport.flatten(Collections.singletonList(three), new boolean[1]), null);
        check(v.size() == 1 && VectorInkChecks.same(v.get(0).lines, three.lines(3)), "layer 2's lines over its pen's marks as vectors, layer 1's under them pixels");
        // Its PDF page: the picture keeps layer 2's pen's marks and layer 1's lines' pixels, without layer 2's lines' pixels.
        List<PackedPage> pages = Collections.singletonList(NotebookPdfChecks.packed(three));
        NotebookPdfChecks.Pdf pdf = new NotebookPdfChecks.Pdf(PageExport.bytes("V", 0, PageExport.PDF, pages, NotebookOptions.DEFAULT, null, null, null, TODAY));
        int[] shown = pdf.image(pdf.pages().get(0));
        ToneDocument.Snapshot s = three.layerSnapshot();
        s.layers.get(3).planes[ToneDocument.PLANE_LINES] = null;
        ToneDocument under = new ToneDocument(s);
        for (int y = 0; y < three.height; y++) for (int x = 0; x < three.width; x++) check(shown[y * three.width + x] == DotPattern.exportGray(under.compositeTone(x, y)), "its picture without layer 2's lines' pixels alone at " + x + "," + y);
        check(pdf.text.contains("/Lines "), "layer 2's lines drawn over it");
        InkWashLayers.setStep(three, 2, NotebookOptions.HIDDEN);
        v = NotebookExport.vectorLayers(NotebookExport.flatten(Collections.singletonList(three), new boolean[1]), null);
        check(v.size() == 1 && VectorInkChecks.same(v.get(0).lines, three.lines(1)), "layer 2 hidden: its lines neither, layer 1's as vectors");
    }

    /** The notebook's PDF: the lines' picture kept by its key beside the PNG copy; a record from before reads the page once; gone with the page. */
    static void notebook(File dir) throws Exception {
        File export = new File(dir, "EXPORT"), docs = new File(dir, "Document");
        String name = "2026-11-05_Lines";
        ToneDocument d = VectorInkChecks.page();
        List<PackedPage> pages = new ArrayList<>(Arrays.asList(NotebookPdfChecks.packed(d), NotebookPdfChecks.packed(blankLike(d))));
        NotebookExport.Done done = update(export, docs, name, pages);
        File twin = new File(export, name + "/" + NotebookExport.vectorName(pages.get(0).crc));
        check(twin.isFile() && NotebookExport.read(Files.readAllBytes(twin.toPath())).vectors != null, "the page's lines' picture kept by its key: " + twin);
        File copy = new File(export, name + "/001.png");
        check(Arrays.equals(Files.readAllBytes(copy.toPath()), NotebookExport.png(d.layerSnapshot(), pages.get(0).crc)), "the PNG copy keeps the lines' pixels");
        NotebookPdfChecks.Pdf pdf = new NotebookPdfChecks.Pdf(Files.readAllBytes(done.pdf.toPath()));
        List<Integer> kids = pdf.pages();
        lines(pdf, kids.get(0), d);
        check(!pdf.dict(kids.get(1)).contains("/Lines"), "the page without lines has none");
        // Nothing changed: nothing drawn, nothing written.
        done.pdf.setLastModified(1000_000L);
        done = update(export, docs, name, pages);
        check(done.drawn == 0 && done.pdf.lastModified() == 1000_000L, "nothing changed, nothing drawn or written");
        // A record from before (none of the lines): the page read once, not drawn, its lines as vectors, the PDF written again once.
        new File(export, name + "/" + NotebookExport.LINES_STATE).delete();
        twin.delete();
        done = update(export, docs, name, pages);
        pdf = new NotebookPdfChecks.Pdf(Files.readAllBytes(done.pdf.toPath()));
        check(done.drawn == 0 && twin.isFile() && done.pdf.lastModified() != 1000_000L && pdf.dict(pdf.pages().get(0)).contains("/Lines"), "a record from before: the page read once, not drawn, its lines as vectors");
        // The page changed to one without lines: its lines' picture goes.
        pages.set(0, NotebookPdfChecks.packed(blankLike(d)));
        update(export, docs, name, pages);
        check(!twin.exists(), "the lines' picture goes with its page");
    }
    static ToneDocument blankLike(ToneDocument d) {
        ToneDocument p = InkWashLayers.blank(d.width, d.height);
        p.selectLayer(InkWashLayers.INK); p.begin();
        for (int x = 20; x < 120; x++) p.setTone(x, 30, 0);
        p.finish();
        return p;
    }
    static NotebookExport.Done update(File export, File docs, String name, List<PackedPage> pages) throws java.io.IOException {
        return NotebookExport.update(export, name, pages, null, null, NotebookOptions.DEFAULT, null, null, new NotebookExport.Documents(docs, "Lines", java.time.LocalDate.of(2026, 11, 5)));
    }

    /** A Manta page of 300 Vector ink strokes (as {@link VectorInkBench}): its Export Page PDF's size with the lines as vectors, and as pixels. */
    static String size() throws Exception {
        Brush vector = VectorInkChecks.vector();
        Random r = new Random(4);
        ToneDocument d = InkWashLayers.blank(1920, 2560);
        WatercolorSession s = new WatercolorSession();
        for (int k = 0; k < 300; k++) {
            DrawingStroke st = StrokeStart.stroke(d, vector, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 4, 1), s, 0);
            double x = 100 + r.nextDouble() * 1720, y = 100 + r.nextDouble() * 2360, a = r.nextDouble() * 6.3;
            for (int i = 0; i < 170; i++) {
                a += (r.nextDouble() - .5) * .3; x += 1.5 * Math.cos(a); y += 1.5 * Math.sin(a);
                st.sample((float) x, (float) y, .6f, 0, 0);
            }
            st.finish();
        }
        int points = 0;
        for (int i = 0; i < d.layerCount(); i++) if (d.lines(i) != null) points += d.lines(i).points();
        List<PackedPage> pages = Collections.singletonList(NotebookPdfChecks.packed(d));
        long t0 = System.nanoTime();
        int vectors = PageExport.bytes("V", 0, PageExport.PDF, pages, NotebookOptions.DEFAULT, null, null, null, TODAY).length;
        long ms = (System.nanoTime() - t0) / 1_000_000;
        java.io.ByteArrayOutputStream old = new java.io.ByteArrayOutputStream();
        byte[] png = NotebookExport.png(d.layerSnapshot(), pages.get(0).crc);
        NotebookPdf.write(old, "V", "V", "", Collections.singletonList(NotebookExport.said(pages.get(0).crc, "", Collections.singletonList(d)).days(TODAY, TODAY)), k -> NotebookExport.read(png), 1);
        check(vectors < 4 * old.size() + 400_000, "the vectors' PDF not much bigger: " + vectors + " against " + old.size());
        return String.format(java.util.Locale.ROOT, "a Manta page of 300 strokes (%d points): PDF %d KB with vectors (%d ms), %d KB as pixels", points, vectors / 1024, ms, old.size() / 1024);
    }
}
