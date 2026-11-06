package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Export Page's PNG sizes ({@link PageExport#bytes} at an {@link ExportSize}, {@link PngRender}): each of Export Page's
 * choices at Page is its copy as before; Social shrinks by area; High Res, written a strip at a time, reads back twice
 * the page, kept dry strokes' edges finer than the page's pixels doubled (lined paper too), a page of washes alone
 * exactly its pixels doubled, Vector Ink's lines drawn sharp. {@code bench}: a Manta page of handwriting, timed.
 */
public final class PngRenderChecks {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length > 0 && args[0].equals("bench")) { bench(); return; }
        choices(); shrink(); social(); sharp(); washes(); vector(); pdf(); pdfLines(); layers();
        java.io.File dir = java.nio.file.Files.createTempDirectory("sharppdf").toFile();
        try { pdfNotebook(dir); } finally { NotebookMigrationChecks.delete(dir); }
        System.out.println("PASS: Export Page's PNG sizes: PDF, PNG and Layers by their index as before, Page the notebook's copy; Social shrinks by area (a Manta page to 1080 × 1444); "
                + "High Res written a strip at a time reads back at twice the page, kept pencil strokes' edges finer than the page's pixels doubled on plain and lined paper and nothing else changed, "
                + "a page of washes alone exactly its pixels doubled, Vector Ink's lines sharp; "
                + "the PDFs (Export Page, Export Now, the notebook's) of a page with kept pencil strokes hold its High Res picture at the page's size, its words where they were, Vector Ink's lines "
                + "still vectors in page pixels over it, their pixels left out; a page of washes alone as before, byte for byte; a record from before reads a sharp page once; "
                + "Layers at Social and High Res read back as PSDs of that size and dpi, the same layers as at Page, each merged image that size's PNG, the layers laid Normal within a few greys of it, "
                + "at High Res the pencil's layer and Vector Ink's Pen finer than their pixels doubled, Paper and Template exactly theirs doubled; the size menus' rows");
    }

    /** A one-page PDF's page: its picture's {@code /Width} and {@code /Height}. */
    static int[] imageSize(NotebookPdfChecks.Pdf pdf, int page) {
        String d = pdf.dict(pdf.ref(pdf.dict(page), "/Page"));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("/Width (\\d+) /Height (\\d+)").matcher(d);
        check(m.find(), "an image's size: " + d);
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
    }
    /** {@code d}'s page alone as the PDF drew it before the sharp pictures: its PNG copy, at 300 dpi. */
    static byte[] pdfAsBefore(ToneDocument d, NotebookTemplate template) throws Exception {
        PackedPage packed = PackedPage.of(DrawingBook.encode(d));
        List<ToneDocument> layers = NotebookExport.layers(Arrays.asList(packed), new PageStacks(new boolean[1]), 0, null, template);
        ToneDocument.Snapshot page = NotebookExport.flatten(layers, new boolean[1]);
        long key = NotebookExport.pageKey(packed.crc, template);
        byte[] png = NotebookExport.png(page, key, PageExportChecks.tiles(), template.shared(d.width, d.height) != null);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        NotebookPdf.write(out, "Harbour", "Harbour", template.equals(NotebookTemplate.BLANK) ? "" : template.label,
                Arrays.asList(NotebookExport.said(key, template.equals(NotebookTemplate.BLANK) ? "" : template.label, layers).days(TODAY, TODAY)), k -> NotebookExport.read(png), 1);
        return out.toByteArray();
    }

    /** Kept pencil strokes: Export Page's and Export Now's PDF picture is High Res's, at the page's size; a page of washes alone as before. */
    static void pdf() throws Exception {
        ToneDocument d = SharpChecks.page(BrushTexture.COLD_PRESS);
        NotebookPdfChecks.Pdf before = new NotebookPdfChecks.Pdf(pdfAsBefore(d, NotebookTemplate.LINED));
        List<PackedPage> pages = new ArrayList<>(Arrays.asList(PackedPage.of(DrawingBook.encode(d))));
        java.io.ByteArrayOutputStream now = new java.io.ByteArrayOutputStream();
        NotebookExport.pdf(now, "Harbour", "Harbour", pages, PageExportChecks.tiles(), null, NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.LINED), null, null, TODAY);
        int[] highRes = high(d, NotebookTemplate.LINED);
        for (byte[] bytes : new byte[][]{copy(d, PageExport.PDF, ExportSize.PAGE, NotebookTemplate.LINED), now.toByteArray()}) {
            NotebookPdfChecks.Pdf pdf = new NotebookPdfChecks.Pdf(bytes);
            int page = pdf.pages().get(0), was = before.pages().get(0);
            int[] size = imageSize(pdf, page), old = imageSize(before, was);
            check(size[0] == 2 * d.width && size[1] == 2 * d.height && old[0] == d.width, "the PDF's picture is twice the page: " + size[0] + " × " + size[1]);
            check(pdf.dict(page).contains("/MediaBox [0 0 72.00 48.00]") && before.dict(was).contains("/MediaBox [0 0 72.00 48.00]"), "the page's size as before: " + pdf.dict(page));
            check(pdf.content(page).equals(before.content(was)), "placed and worded as before");
            int[] shown = pdf.image(page);
            check(Arrays.equals(shown, highRes), "the PDF's picture is High Res's PNG, its sharp edges too");
            check(finer(shown, d) > 1000, "its pencil edges finer than the page's pixels");
        }
        // Washes alone: nothing to draw sharp, the PDF as before, byte for byte.
        ToneDocument w = InkWashLayers.blank(300, 200);
        WatercolorSession s = new WatercolorSession();
        Random r = new Random(4);
        for (int i = 0; i < 3; i++) VectorInkChecks.draw(w, s, VectorInkChecks.preset("Soft wash"), 24, .6, wobble(r));
        check(!PngRender.sharp(w.layerSnapshot()) && PngRender.sharp(d.layerSnapshot()), "a wash isn't kept sharp, the pencil is");
        check(Arrays.equals(copy(w, PageExport.PDF, ExportSize.PAGE, NotebookTemplate.LINED), pdfAsBefore(w, NotebookTemplate.LINED)), "a page of washes alone: its PDF as before, byte for byte");
    }

    /** Kept pencil strokes and Vector Ink: the lines vectors in page pixels as before, over a picture twice the page without their pixels. */
    static void pdfLines() throws Exception {
        ToneDocument d = InkWashLayers.blank(200, 120);
        d.surface = BrushTexture.COLD_PRESS;
        WatercolorSession s = new WatercolorSession();
        SharpChecks.draw(d, s, SharpChecks.preset("HB pencil"), 4, 1, SharpChecks.hand(new Random(3), 40, 30, 90, 10, false), 1);
        // The lines over it (a pair of their own on top, as Vector Ink puts them over other marks).
        VectorInkChecks.draw(d, s, VectorInkChecks.vector(), 6, 1, VectorInkChecks.hand(50, 11));
        int ink = -1;
        for (int i = 0; i < d.layerCount(); i++) if (d.lines(i) != null && !d.lines(i).isEmpty()) ink = i;
        check(ink >= 0 && PngRender.sharp(d.layerSnapshot()), "the pencil stroke kept, the lines over it");
        List<PackedPage> pages = Arrays.asList(NotebookPdfChecks.packed(d));
        NotebookPdfChecks.Pdf pdf = new NotebookPdfChecks.Pdf(PageExport.bytes("V", 0, PageExport.PDF, pages, NotebookOptions.DEFAULT, null, null, null, TODAY));
        int page = pdf.pages().get(0);
        int[] size = imageSize(pdf, page);
        check(size[0] == 400 && size[1] == 240 && pdf.dict(page).contains("/MediaBox [0 0 48.00 28.80]"), "twice the page, at its size: " + size[0] + " × " + size[1]);
        // The form, its paths and its matrix as for a page at 1 × (VectorPdfChecks.lines): in page pixels, mapped at 300 ppi.
        String form = pdf.dict(pdf.ref(pdf.dict(page), "/Lines"));
        check(form.contains("/BBox [0 0 200 120]") && form.contains("/Matrix [0.24 0 0 -0.24 0 28.80]"), "the lines' form in page pixels: " + form);
        java.util.zip.Inflater inflater = new java.util.zip.Inflater(); inflater.setInput(pdf.stream(pdf.ref(pdf.dict(page), "/Lines")));
        byte[] raw = new byte[1 << 20]; int n = inflater.inflate(raw); inflater.end();
        StringBuilder expected = new StringBuilder();
        d.lines(ink).pdf(expected, DotPattern::exportGray);
        check(new String(raw, 0, n, java.nio.charset.StandardCharsets.ISO_8859_1).equals(expected.toString()), "the form holds the lines' paths");
        // The picture: High Res of the page without the lines' layer (neither their pixels nor their sharp lines).
        ToneDocument.Snapshot hidden = d.layerSnapshot();
        hidden.layers.get(ink).visible = false;
        int[] got = new int[2], without = read(PngRender.high(hidden, false, null, false, 1, null), got), shown = pdf.image(page), with = high(d, NotebookTemplate.BLANK);
        check(Arrays.equals(shown, without) && !Arrays.equals(shown, with), "the picture leaves the lines out");
        check(finer(shown, d) > 100, "its pencil edges finer than the page's pixels");
    }

    /** The notebook's PDF: a sharp page's picture kept by its key at twice the page; a record from before the sharp pictures reads it once. */
    static void pdfNotebook(java.io.File dir) throws Exception {
        java.io.File export = new java.io.File(dir, "EXPORT"), docs = new java.io.File(dir, "Document");
        String name = "2026-11-05_Sharp";
        ToneDocument d = SharpChecks.page(BrushTexture.COLD_PRESS);
        List<PackedPage> pages = new ArrayList<>(Arrays.asList(NotebookPdfChecks.packed(d), NotebookPdfChecks.packed(VectorPdfChecks.blankLike(d))));
        NotebookExport.Done done = VectorPdfChecks.update(export, docs, name, pages);
        java.io.File twin = new java.io.File(export, name + "/" + NotebookExport.vectorName(pages.get(0).crc));
        NotebookExport.Png picture = NotebookExport.read(java.nio.file.Files.readAllBytes(twin.toPath()));
        check(picture != null && picture.width == 600 && picture.dpi == 600 && picture.vectors == null, "the sharp page's picture kept by its key, twice the page at 600 dpi");
        NotebookPdfChecks.Pdf pdf = new NotebookPdfChecks.Pdf(java.nio.file.Files.readAllBytes(done.pdf.toPath()));
        check(imageSize(pdf, pdf.pages().get(0))[0] == 600 && imageSize(pdf, pdf.pages().get(1))[0] == 300 && pdf.dict(pdf.pages().get(0)).contains("/MediaBox [0 0 72.00 48.00]"),
                "the PDF's sharp page twice the page at its size, the pen-only page as before");
        // A record from before (no first line): the sharp page read once, not drawn, its picture made; then nothing again.
        java.io.File state = new java.io.File(export, name + "/" + NotebookExport.LINES_STATE);
        String lines = new String(java.nio.file.Files.readAllBytes(state.toPath()), java.nio.charset.StandardCharsets.ISO_8859_1);
        check(lines.startsWith(NotebookExport.LINES_MARK), "the record says it knows sharp pages");
        java.nio.file.Files.write(state.toPath(), (Long.toHexString(pages.get(0).crc) + "\t0\n" + Long.toHexString(pages.get(1).crc) + "\t0\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        twin.delete();
        done = VectorPdfChecks.update(export, docs, name, pages);
        check(done.drawn == 0 && twin.isFile(), "a record from before: the sharp page read once, not drawn, its picture made");
        done.pdf.setLastModified(1000_000L);
        done = VectorPdfChecks.update(export, docs, name, pages);
        check(done.drawn == 0 && done.pdf.lastModified() == 1000_000L, "then nothing drawn or written");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /** Whether layer {@code l} of {@code high} is that of {@code page} each pixel laid 2 × 2. */
    static boolean doubled(PageExportChecks.Psd high, PageExportChecks.Psd page, int l, ToneDocument d) {
        byte[] g = high.grey.get(l), a = high.alpha.get(l), pg = page.grey.get(l), pa = page.alpha.get(l);
        for (int i = 0; i < g.length; i++) { int j = i / high.width / 2 * d.width + i % high.width / 2; if (a[i] != pa[j] || a[i] != 0 && g[i] != pg[j]) return false; }
        return true;
    }
    /** Layers at each size (the owner, 2026-11-05): read back by the PSD reader, its merged image that size's PNG, its layers within a few greys of it; High Res sharp. */
    static void layers() throws Exception {
        ToneDocument d = SharpChecks.page(BrushTexture.COLD_PRESS);
        // A Vector Ink pair over the pencil, and a magnet, so every kind of layer is there.
        VectorInkChecks.draw(d, new WatercolorSession(), VectorInkChecks.vector(), 6, 1, VectorInkChecks.hand(50, 11));
        for (boolean magnet : new boolean[]{false, true}) {
            // A magnet's soft edge over a kept stroke: the PNG keeps that pixel doubled, the layers keep the stroke sharp under it.
            if (magnet) d.placeMagnets(Magnets.of(java.util.Collections.singletonList(new Magnets.Magnet("ink", 220, 150, 0, 0))));
            PageExportChecks.Psd page = PageExportChecks.read(copy(d, PageExport.LAYERS, ExportSize.PAGE, NotebookTemplate.LINED));
            check(page.names.contains("Layer 1 Dry Media") && page.names.contains("Layer 2 Pen") && page.names.contains(PageExport.MAGNETS) == magnet, "the page's layers: " + page.names);
            for (ExportSize size : new ExportSize[]{ExportSize.SOCIAL, ExportSize.HIGH}) {
                int[] wh = size.of(d.width, d.height), got = new int[2];
                PageExportChecks.Psd psd = PageExportChecks.read(copy(d, PageExport.LAYERS, size, NotebookTemplate.LINED), size == ExportSize.HIGH ? 600 : 300);
                check(psd.width == wh[0] && psd.height == wh[1], size.label + "'s Layers at its size: " + psd.width + " × " + psd.height);
                check(psd.names.equals(page.names), size.label + "'s layers as at Page: " + psd.names);
                int[] png = read(copy(d, PageExport.PNG, size, NotebookTemplate.LINED), got);
                check(Arrays.equals(psd.merged, png), size.label + "'s merged image is its PNG's greys");
                // High Res's finer edges overlap partly more often: the greys' curve takes a few more greys there.
                if (!magnet || size == ExportSize.SOCIAL) PageExportChecks.compare(psd, png, size.label, size == ExportSize.HIGH ? .001 : 0);
            }
        }
        PageExportChecks.Psd page = PageExportChecks.read(copy(d, PageExport.LAYERS, ExportSize.PAGE, NotebookTemplate.LINED));
        // A Manta page's Social: 1080 × 1444.
        ToneDocument manta = PageExportChecks.sample(1785, 2387, 5);
        // Its print size the page's, as the PNG's: 300 dpi × 1080 / 1785.
        PageExportChecks.Psd social = PageExportChecks.read(copy(manta, PageExport.LAYERS, ExportSize.SOCIAL, NotebookTemplate.LINED), 182);
        check(social.width == 1080 && social.height == 1444 && social.names.size() > 4, "a Manta page's Social Layers 1080 × 1444: " + social.names);
        PageExportChecks.compare(social, read(copy(manta, PageExport.PNG, ExportSize.SOCIAL, NotebookTemplate.LINED), new int[2]), "a Manta page's Social", .005);
        // High Res: the pencil's layer and the lines' Pen finer than their pixels doubled; a layer that isn't, exactly its pixels doubled.
        PageExportChecks.Psd high = PageExportChecks.read(copy(d, PageExport.LAYERS, ExportSize.HIGH, NotebookTemplate.LINED), 600);
        for (int l = 0; l < high.names.size(); l++) {
            String name = high.names.get(l);
            int[] a = new int[high.alpha.get(l).length];
            for (int i = 0; i < a.length; i++) a[i] = high.alpha.get(l)[i] & 255;
            int edges = finer(a, d);
            if (name.equals("Layer 1 Dry Media") || name.equals("Layer 2 Pen")) check(edges > 200, name + " finer than its pixels doubled at High Res: " + edges);
            else check(edges > 0 ? !name.equals(PageExport.PAPER) && !name.equals(PageExport.TEMPLATE) && !name.equals(PageExport.MAGNETS) : doubled(high, page, l, d), name + " at High Res is its pixels doubled");
        }
        check(Arrays.equals(PageExport.sizeWords(PageExport.LAYERS, 1785, 2387), new String[]{"Social, 1080 × 1444", "Page, 1785 × 2387", "High Res, 3570 × 4774, sharp, large file"})
                && Arrays.equals(PageExport.sizeWords(PageExport.PNG, 1785, 2387), new String[]{"Social, 1080 × 1444", "Page, 1785 × 2387", "High Res, 3570 × 4774, sharp"})
                && PageExport.sized(PageExport.PNG) && PageExport.sized(PageExport.LAYERS) && !PageExport.sized(PageExport.PDF), "the size menus' rows");
    }

    static final long TODAY = 20000;
    /** Page 1 of a notebook of {@code d} alone, its {@code choice} copy at {@code size}. */
    static byte[] copy(ToneDocument d, String choice, ExportSize size, NotebookTemplate template) throws Exception {
        List<PackedPage> pages = new ArrayList<>();
        pages.add(PackedPage.of(DrawingBook.encode(d)));
        return PageExport.bytes("Harbour", 0, choice, size, pages, NotebookOptions.DEFAULT.withTemplate(template), null, null, PageExportChecks.tiles(), TODAY);
    }
    static int[] read(byte[] png, int[] size) throws Exception {
        BufferedImage image = javax.imageio.ImageIO.read(new ByteArrayInputStream(png));
        size[0] = image.getWidth(); size[1] = image.getHeight();
        // An 8-bit grey PNG's samples as they are (getRGB would turn its grey into sRGB); a palette's colours.
        if (image.getType() == BufferedImage.TYPE_BYTE_GRAY) return image.getRaster().getPixels(0, 0, size[0], size[1], (int[]) null);
        int[] out = image.getRGB(0, 0, size[0], size[1], null, 0, size[0]);
        for (int i = 0; i < out.length; i++) out[i] &= 255;
        return out;
    }

    /** The menu's choices by index (the browser's as=k): each one's copy at Page as before the sizes. */
    static void choices() throws Exception {
        check(Arrays.equals(PageExport.CHOICES, new String[]{PageExport.PDF, PageExport.PNG, PageExport.LAYERS}), "Export Page's choices keep their order");
        ToneDocument d = SharpChecks.page(BrushTexture.COLD_PRESS);
        List<PackedPage> pages = new ArrayList<>();
        pages.add(PackedPage.of(DrawingBook.encode(d)));
        for (String choice : PageExport.CHOICES) {
            byte[] before = PageExport.bytes("Harbour", 0, choice, pages, NotebookOptions.DEFAULT, null, null, PageExportChecks.tiles(), TODAY);
            check(Arrays.equals(before, copy(d, choice, ExportSize.PAGE, NotebookTemplate.BLANK)), choice + " at Page is its copy as before");
        }
    }

    static void shrink() {
        byte[] board = new byte[4 * 4];
        for (int i = 0; i < board.length; i++) board[i] = (byte) ((i % 4 + i / 4) % 2 == 0 ? 0 : 255);
        for (byte p : PngRender.shrink(board, 4, 4, 2, 2)) check((p & 255) == 128, "a checkerboard halved is its mean grey: " + (p & 255));
        byte[] two = PngRender.shrink(new byte[]{0, (byte) 255, 0}, 3, 1, 2, 1);
        // Each of two pixels takes one and a half: black, then half of the white: 255 × .5 / 1.5 = 85.
        check((two[0] & 255) == 85 && (two[1] & 255) == 85, "a third's weight is its share of the area: " + (two[0] & 255));
        byte[] grey = new byte[1785 * 2387];
        Arrays.fill(grey, (byte) 122);
        for (byte p : PngRender.shrink(grey, 1785, 2387, 1080, 1444)) check(p == 122, "an even grey stays itself");
    }

    static void social() throws Exception {
        ToneDocument small = SharpChecks.page(BrushTexture.COLD_PRESS);
        check(Arrays.equals(copy(small, PageExport.PNG, ExportSize.SOCIAL, NotebookTemplate.BLANK), copy(small, PageExport.PNG, ExportSize.PAGE, NotebookTemplate.BLANK)), "Social of a page under 1080 is its Page copy");
        ToneDocument manta = InkWashLayers.blank(1785, 2387);
        VectorInkChecks.draw(manta, new WatercolorSession(), VectorInkChecks.preset("Soft wash"), 300, .6, new float[][]{{200, 1500}, {300, 2000}, {.8f, .8f}});
        int[] size = new int[2], page = new int[2];
        int[] shrunk = read(copy(manta, PageExport.PNG, ExportSize.SOCIAL, NotebookTemplate.LINED), size), whole = read(copy(manta, PageExport.PNG, ExportSize.PAGE, NotebookTemplate.LINED), page);
        check(size[0] == 1080 && size[1] == 1444, "Social of a Manta page is 1080 × 1444: " + size[0] + " × " + size[1]);
        double a = 0, b = 0;
        for (int v : shrunk) a += v;
        for (int v : whole) b += v;
        check(Math.abs(a / shrunk.length - b / whole.length) < .5, "Social keeps the page's mean grey: " + a / shrunk.length + ", " + b / whole.length);
    }

    /** The page's Page PNG, each pixel laid 2 × 2. */
    static int[] doubled(ToneDocument d, NotebookTemplate template) throws Exception {
        int[] size = new int[2], page = read(copy(d, PageExport.PNG, ExportSize.PAGE, template), size), out = new int[page.length * 4];
        int w = size[0] * 2;
        for (int y = 0; y < size[1] * 2; y++) for (int x = 0; x < w; x++) out[y * w + x] = page[y / 2 * size[0] + x / 2];
        return out;
    }
    static int[] high(ToneDocument d, NotebookTemplate template) throws Exception {
        int[] size = new int[2], out = read(copy(d, PageExport.PNG, ExportSize.HIGH, template), size);
        check(size[0] == d.width * 2 && size[1] == d.height * 2, "High Res is twice the page: " + size[0] + " × " + size[1]);
        return out;
    }
    /** Page pixels whose 2 × 2 points in {@code out} are not all one grey (none in a plain upscale). */
    static int finer(int[] out, ToneDocument d) {
        int w = d.width * 2, n = 0;
        for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) {
            int i = 2 * y * w + 2 * x, a = out[i];
            if (out[i + 1] != a || out[i + w] != a || out[i + w + 1] != a) n++;
        }
        return n;
    }

    static void sharp() throws Exception {
        for (NotebookTemplate template : new NotebookTemplate[]{NotebookTemplate.BLANK, NotebookTemplate.LINED}) {
            ToneDocument d = SharpChecks.page(BrushTexture.COLD_PRESS);
            int[] out = high(d, template), plain = doubled(d, template);
            check(finer(plain, d) == 0, "a plain 2 × upscale has no finer edges");
            // The page's pixels doubled where nothing is kept sharp: in tiles no kept stroke's box meets.
            List<SharpView.Kept> kept = new SharpView(rect -> { }).kept(d);
            int tile = SharpView.TILE, changed = 0, far = 0, edges = finer(out, d);
            for (int i = 0; i < out.length; i++) {
                if (out[i] == plain[i]) continue;
                changed++;
                int x = i % (d.width * 2) / 2 / tile * tile, y = i / (d.width * 2) / 2 / tile * tile;
                boolean near = false;
                for (SharpView.Kept s : kept) near |= s.box[0] < x + tile && s.box[2] > x && s.box[1] < y + tile && s.box[3] > y;
                if (!near) far++;
            }
            check(edges > 1000, "at 2 × the pencil's edges are finer than its pixels doubled on " + template.label + ": " + edges + " page pixels");
            check(changed > 0 && far == 0, "only kept strokes are drawn again on " + template.label + ": " + far + " of " + changed + " changed points outside them");
        }
    }

    static void washes() throws Exception {
        ToneDocument d = InkWashLayers.blank(300, 200);
        d.surface = BrushTexture.COLD_PRESS;
        WatercolorSession s = new WatercolorSession();
        Random r = new Random(4);
        for (int i = 0; i < 4; i++) VectorInkChecks.draw(d, s, VectorInkChecks.preset("Soft wash"), 24, .6, wobble(r));
        VectorInkChecks.draw(d, s, VectorInkChecks.preset("Blend"), 20, 1, wobble(r));
        check(Arrays.equals(high(d, NotebookTemplate.LINED), doubled(d, NotebookTemplate.LINED)), "a page of washes alone is exactly its pixels doubled");
    }

    static void vector() throws Exception {
        ToneDocument d = InkWashLayers.blank(300, 200);
        VectorInkChecks.draw(d, new WatercolorSession(), VectorInkChecks.vector(), 4, 1, wobble(new Random(8)));
        check(VectorInk.sharpLayers(d, 0, null) != 0, "the Vector Ink layer is drawn sharp");
        check(finer(high(d, NotebookTemplate.BLANK), d) > 200, "at 2 × Vector Ink's lines are finer than its pixels doubled");
    }
    /** x, y, pressure across the page. */
    static float[][] wobble(Random r) {
        float[][] h = new float[3][60];
        double x = 30 + r.nextInt(60), y = 40 + r.nextInt(120);
        for (int i = 0; i < 60; i++) { x += 4; y += (r.nextDouble() - .5) * 6; h[0][i] = (float) x; h[1][i] = (float) y; h[2][i] = (float) (.4 + .5 * r.nextDouble()); }
        return h;
    }

    /** A Manta page of HB pencil handwriting (300 strokes of 170 samples over it, as SharpBench's), its High Res copy timed, desktop. */
    static void bench() throws Exception {
        ToneDocument d = InkWashLayers.blank(1785, 2387);
        d.surface = BrushTexture.COLD_PRESS;
        WatercolorSession s = new WatercolorSession();
        Random r = new Random(1);
        Brush hb = SharpChecks.preset("HB pencil");
        for (int i = 0; i < 300; i++) SharpChecks.draw(d, s, hb, 5, 1, SharpChecks.hand(r, 170, 100 + r.nextInt(1580), 100 + r.nextInt(2180), 10, false), 1);
        List<PackedPage> pages = new ArrayList<>();
        pages.add(PackedPage.of(DrawingBook.encode(d)));
        for (String choice : new String[]{PageExport.PNG, PageExport.LAYERS}) for (ExportSize size : ExportSize.values()) {
            long t0 = System.nanoTime();
            byte[] png = PageExport.bytes("Harbour", 0, choice, size, pages, NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.LINED), null, null, null, TODAY);
            System.out.printf("%s %s, a Manta page of handwriting: %.1f s desktop, %d KB%n", choice, size.label, (System.nanoTime() - t0) / 1e9, png.length >> 10);
        }
        // Its PDF as before (the PNG copy, 1 ×) and now (sharp, 2 ×); the time the page's lock is held for it.
        long t0 = System.nanoTime();
        byte[] before = pdfAsBefore(d, NotebookTemplate.LINED);
        long t1 = System.nanoTime();
        byte[] now = PageExport.bytes("Harbour", 0, PageExport.PDF, pages, NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.LINED), null, null, null, TODAY);
        long t2 = System.nanoTime();
        System.out.printf("PDF, a Manta page of handwriting: 1 × %.1f s desktop, %d KB; 2 × sharp %.1f s desktop, %d KB%n", (t1 - t0) / 1e9, before.length >> 10, (t2 - t1) / 1e9, now.length >> 10);
    }
}
