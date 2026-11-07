package io.github.mpdairy.monopaint;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One page's copy, asked for on its Page Settings (Export Page; the owner, 2026-10-28: "each page should have a way to
 * save page in various ways.. pdf png, and possibly layers for photoshop? ie. the diff media layers"): a PDF, a PNG or
 * Layers, a Photoshop file ({@link PsdWriter}), named for the notebook and the page ("Harbour p5.psd") beside the
 * notebooks' copies ({@code AutoExport}). The PNG and the PDF are the notebook's copies' own for that page ({@link
 * NotebookExport#png}, {@link NotebookPdf#write}, the PDF numbered as the page). Layers holds, bottom first: Paper (the
 * page's colour), Template (the guides as the copies draw them, where they show), then for each of the page's layers its
 * marks kind by kind as the page stacks them ({@link ToneDocument#stacked}: Old Paint, the base, under Dry Media,
 * Washes, Pen and Paint, opaque paint), "Layer 2 Pen" where the page has more than one, then Magnets; empty ones left
 * out. Each in the PNG's smooth greys, Normal: the page composites every mark over what is under it (no wash
 * multiplies), so Normal is exact where a mark covers fully; where it covers partly, its coverage is the one that shows
 * on the paper as the PNG shows it there (the greys' curve, {@link DotPattern#exportGray}, comes after the composite),
 * so the layers come within a few greys of the merged image, the PNG's own, only where partial marks overlap.
 * Left out: the masking fluid (never part of the artwork, nor of the PNG), Show Kinds' steps (the copies show every
 * kind whole) and a hidden layer. The PNG and Layers come at an {@link ExportSize} (the owner, 2026-11-05), Layers' High
 * Res drawn sharp as the PNG's ({@link #psd(OutputStream, ToneDocument.Snapshot, Magnets.Tiles, boolean, ExportSize)}).
 * Pure Java, checked on the desktop ({@code PageExportChecks}, {@code PngRenderChecks}); run on {@code AutoExport}'s worker.
 */
final class PageExport {
    /** The choices' words (docs/TEXT-STYLE.md) in the menu Export Page opens. */
    static final String PDF = "PDF", PNG = "PNG", LAYERS = "Layers";
    static final String[] CHOICES = {PDF, PNG, LAYERS};
    /** The note when a copy couldn't be made (Export Now's too). */
    static final String FAILED = "Export failed";
    /** A page's layers' kinds as the Layers copy names them, in {@link ToneDocument#stacked}'s order: the base, then each plane ({@link ToneDocument#STACK_PLANES}). */
    static final String[] KINDS = {"Old Paint", "Dry Media", "Washes", "Pen", "Vector Ink", "Paint"};
    static final String PAPER = "Paper", TEMPLATE = "Template", MAGNETS = "Magnets";

    /** {@code choice}'s file ending. */
    static String extension(String choice) { return choice.equals(LAYERS) ? "psd" : choice.toLowerCase(Locale.ROOT); }
    /** Page {@code place}'s (a stack's) copy's file: "p5.psd". */
    static String page(int place, String choice) { return "p" + (place + 1) + "." + extension(choice); }
    /** Its file's name in {@code title}'s notebook: "Harbour p5.psd". */
    static String name(String title, int place, String choice) { return NotebookPdf.base(title) + " " + page(place, choice); }

    /**
     * Writes page {@code place}'s (a stack's) {@code choice} copy into {@code folder} as {@link #name}, from the
     * notebook's {@code pages} (packed), its {@code options}, each page's template key ({@code pageTemplates}) and its
     * stacks ({@code tracing}), its magnets drawn by {@code tiles} (null: none), as {@link NotebookExport#update} draws
     * that page; the file written.
     */
    static File write(File folder, String title, int place, String choice, List<PackedPage> pages, NotebookOptions options, List<String> pageTemplates, TracingPaper tracing, Magnets.Tiles tiles) throws IOException {
        return write(folder, title, place, choice, ExportSize.PAGE, pages, options, pageTemplates, tracing, tiles);
    }
    /** As above, a PNG or Layers at {@code size} (Export Page's size menus; the PDF ignores it), streamed into the file. */
    static File write(File folder, String title, int place, String choice, ExportSize size, List<PackedPage> pages, NotebookOptions options, List<String> pageTemplates, TracingPaper tracing, Magnets.Tiles tiles) throws IOException {
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Could not make " + folder);
        File target = new File(folder, name(title, place, choice)), temporary = new File(folder, "." + target.getName() + ".tmp");
        boolean done = false;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(temporary), 1 << 16)) {
            write(out, title, place, choice, size, pages, options, pageTemplates, tracing, tiles, LocalDate.now().toEpochDay());
            done = true;
        } finally { if (!done) temporary.delete(); }
        NotebookExport.move(temporary, target);
        return target;
    }
    /** Notebook {@code name}'s ({@code book}, wet paint laid) page {@code place}'s {@code choice} copy, as Export Page makes it: the browser app's, and a browser's download from the Manta ({@link LiveBooks}). */
    static byte[] bytes(DrawingBook book, String name, int place, String choice, Magnets.Tiles tiles, long today) throws IOException {
        return bytes(book, name, place, choice, ExportSize.PAGE, tiles, today);
    }
    /** As above, a PNG or Layers at {@code size}. */
    static byte[] bytes(DrawingBook book, String name, int place, String choice, ExportSize size, Magnets.Tiles tiles, long today) throws IOException {
        DrawingBook.Snapshot s = book.snapshot();
        List<PackedPage> pages = s.packed();
        s.release();
        return bytes(NotebookPdf.title(name), place, choice, size, pages, s.options, s.pageTemplates, s.tracing, tiles, today);
    }
    /** Page {@code place}'s {@code choice} copy as {@link #write} writes it, its bytes ({@code today}, an epoch day: the PDF's days); the browser's Export Page too. */
    static byte[] bytes(String title, int place, String choice, List<PackedPage> pages, NotebookOptions options, List<String> pageTemplates, TracingPaper tracing, Magnets.Tiles tiles, long today) throws IOException {
        return bytes(title, place, choice, ExportSize.PAGE, pages, options, pageTemplates, tracing, tiles, today);
    }
    /** As above, a PNG or Layers at {@code size}. */
    static byte[] bytes(String title, int place, String choice, ExportSize size, List<PackedPage> pages, NotebookOptions options, List<String> pageTemplates, TracingPaper tracing, Magnets.Tiles tiles, long today) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
        write(out, title, place, choice, size, pages, options, pageTemplates, tracing, tiles, today);
        return out.toByteArray();
    }
    /** Whether {@code choice} comes in {@link ExportSize}s (a menu of them after it): the PNG and Layers. */
    static boolean sized(String choice) { return !choice.equals(PDF); }
    /** The size menu's rows for {@code choice} on a page of that size: the PNG's {@link ExportSize#words}; Layers' High Res says its file is large. */
    static String[] sizeWords(String choice, int width, int height) {
        ExportSize[] sizes = ExportSize.values();
        String[] words = new String[sizes.length];
        for (int i = 0; i < sizes.length; i++) words[i] = sizes[i].words(width, height) + (choice.equals(LAYERS) && sizes[i] == ExportSize.HIGH ? LARGE : "");
        return words;
    }
    /** Layers' High Res row's warning (docs/TEXT-STYLE.md; the owner, 2026-11-05: "just warn the user that the file will be large"). */
    static final String LARGE = ", large file";

    /** {@link #bytes}'s copy written into {@code out}: a PNG or PDF made whole, Layers streamed as it is drawn. */
    static void write(OutputStream out, String title, int place, String choice, ExportSize size, List<PackedPage> pages, NotebookOptions options, List<String> pageTemplates, TracingPaper tracing, Magnets.Tiles tiles, long today) throws IOException {
        PageStacks stacks = tracing != null && tracing.count() == pages.size() ? tracing.stacks() : new PageStacks(new boolean[pages.size()]);
        int bottom = stacks.first(place);
        NotebookTemplate template = options.pageTemplate(pageTemplates != null && bottom < pageTemplates.size() ? pageTemplates.get(bottom) : null);
        synchronized (DocumentCodec.ONE_PAGE) {
            List<ToneDocument> layers = NotebookExport.layers(pages, stacks, place, tracing, template);
            ToneDocument.Snapshot page = NotebookExport.flatten(layers, NotebookExport.hidden(stacks, place, tracing));
            boolean guides = template.shared(page.width, page.height) != null;
            if (choice.equals(LAYERS)) { psd(out, page, tiles, guides, size); return; }
            long key = NotebookExport.pageKey(NotebookExport.stackKey(pages, stacks, place, tracing), template);
            if (choice.equals(PNG)) { out.write(size != ExportSize.PAGE ? PngRender.png(page, size, tiles, guides, key) : NotebookExport.png(page, key, tiles, guides)); return; }
            // Its Vector Ink lines as vectors, where it has some.
            byte[] vectors = NotebookExport.vectorPng(page, key, tiles, guides), png = vectors != null ? vectors : NotebookExport.png(page, key, tiles, guides);
            NotebookPdf.Sheet sheet = NotebookExport.said(key, template.equals(NotebookTemplate.BLANK) ? "" : template.label, layers).days(today, today);
            NotebookPdf.write(out, title, title, options.template.equals(NotebookTemplate.BLANK) ? "" : options.template.label,
                    Collections.singletonList(sheet), k -> NotebookExport.read(png), place + 1);
        }
    }

    /** {@code page} (a stack flattened) as a Photoshop file of its layers ({@link PageExport}) at Page, its guides drawn when {@code guides}. */
    static byte[] psd(ToneDocument.Snapshot page, Magnets.Tiles tiles, boolean guides) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        psd(out, page, tiles, guides, ExportSize.PAGE);
        return out.toByteArray();
    }
    /**
     * As above at {@code size}, into {@code out}: Social each layer shrunk by area (its coverage with it); High Res each
     * layer twice the page, the planes' kept dry strokes drawn sharp in theirs ({@link Sharp}) and Vector Ink's lines in
     * their Pen as High Res's PNG draws them, the rest their pixels doubled, its merged image that PNG's. Layer by layer,
     * a row at a time: each held only packed.
     */
    static void psd(OutputStream output, ToneDocument.Snapshot page, Magnets.Tiles tiles, boolean guides, ExportSize size) throws IOException {
        int w = page.width, h = page.height;
        int[] out = size.of(w, h);
        int ow = out[0], oh = out[1], dpi = (int) Math.round(NotebookExport.DPI * (double) ow / w);
        int[] grey = new int[256];
        for (int g = 0; g < 256; g++) grey[g] = DotPattern.exportGray(g);
        int[] row = new int[w], cover = new int[w];
        List<PsdWriter.Layer> layers = new ArrayList<>();
        int paper = grey[page.paper];
        layers.add(PsdWriter.layer(PAPER, ow, oh, (y, g, a) -> { java.util.Arrays.fill(g, (byte) paper); java.util.Arrays.fill(a, (byte) 255); }));
        // The guides on the paper, as the copies draw them ({@link ToneDocument#guided}, the darker of the two): opaque where they show.
        if (guides) {
            PagePicture.Composite ground = PagePicture.Composite.printed(page, 0, 0);
            layers.add(PsdWriter.layer(TEMPLATE, ow, oh, sized(size, w, h, (y, g, a) -> {
                ground.row(y, row);
                for (int x = 0; x < w; x++) if (row[x] != page.paper) { g[x] = (byte) grey[row[x]]; a[x] = (byte) 255; }
            })));
        }
        // High Res: the kept dry strokes, and Vector Ink's layers drawn from their lines (their pixels left out).
        boolean high = size == ExportSize.HIGH;
        ToneDocument doc = high ? new ToneDocument(page) : null;
        int without = high ? VectorInk.sharpLayers(doc, 0, null) : 0;
        List<SharpView.Kept> kept = high ? SharpView.kept(doc, new java.util.HashMap<>()) : null;
        // Each pair (a layer of the page, InkWashLayers) kind by kind: its two halves' entries of a kind, side by side in the stack.
        int pairs = (page.layers.size() + 1) / 2, kinds = 1 + ToneDocument.PLANES;
        for (int p = 0; p < pairs; p++) for (int kind = 0; kind < kinds; kind++) {
            int from = p * 2 * kinds + 2 * kind;
            PagePicture.Composite marks = PagePicture.Composite.printed(page, from, from + 2, without);
            PsdWriter.Rows rows = high ? new Sharp(page, doc, marks, from, ToneDocument.slotPlane(kind) == ToneDocument.PLANE_LINES, without, kept, grey)
                    : sized(size, w, h, (y, g, a) -> { marks.marks(y, row, cover); for (int x = 0; x < w; x++) pixel(row[x], cover[x], page.paper, grey, g, a, x); });
            layers.add(PsdWriter.layer((pairs > 1 ? "Layer " + (p + 1) + " " : "") + KINDS[kind], ow, oh, rows));
        }
        if (tiles != null && page.magnets != null && !page.magnets.isEmpty()) {
            int[] stamped = new int[w * h];
            Magnets.stamp(stamped, w, h, page.magnets, tiles);
            layers.add(PsdWriter.layer(MAGNETS, ow, oh, sized(size, w, h, (y, g, a) -> {
                for (int x = 0, i = y * w; x < w; x++, i++) { g[x] = (byte) (stamped[i] >> 8); a[x] = (byte) (stamped[i] >>> 24); }
            })));
        }
        layers.removeIf(l -> !l.shows);
        // The merged image: the PNG's greys at that size (High Res's drawn a strip at a time).
        PsdWriter.Channel merged = new PsdWriter.Channel(oh);
        byte[] line = new byte[ow];
        if (high) PngRender.high(page, true, tiles, guides, (greys, count) -> {
            for (int r = 0; r < count; r++) { for (int x = 0; x < ow; x++) line[x] = (byte) greys[r * ow + x]; merged.row(line); }
        });
        else {
            byte[] greys = NotebookExport.greys(page, tiles, guides);
            if (ow != w || oh != h) greys = PngRender.shrink(greys, w, h, ow, oh);
            for (int y = 0; y < oh; y++) { System.arraycopy(greys, y * ow, line, 0, ow); merged.row(line); }
        }
        PsdWriter.write(output, ow, oh, dpi, layers, merged);
    }
    /**
     * A layer's marks over nothing ({@link PagePicture.Composite#marks}: {@code inked}, coverage {@code c}) as its pixel
     * {@code x} in the file: its own grey, at the coverage that shows on the paper as the PNG shows it there (the smooth
     * greys' curve is steep in the darks: half black shows far darker than half way); over other marks, near.
     */
    static void pixel(int inked, int c, int pagePaper, int[] grey, byte[] g, byte[] a, int x) {
        if (c == 0) return;
        int paper = grey[pagePaper], tone = Math.min(255, (inked * 255 + c / 2) / c), shown = grey[(tone * c + pagePaper * (255 - c) + 127) / 255];
        g[x] = (byte) grey[tone];
        a[x] = (byte) (grey[tone] == paper ? c : Math.max(0, Math.min(255, Math.round((paper - shown) * 255f / (paper - grey[tone])))));
    }
    /** A page-sized layer's {@code rows} ({@code width × height}) at {@code size}: Page as they are, Social shrunk by area ({@link PngRender#shrunk}), High Res each pixel laid 2 × 2. */
    static PsdWriter.Rows sized(ExportSize size, int width, int height, PsdWriter.Rows rows) {
        if (size == ExportSize.PAGE) return rows;
        int[] out = size.of(width, height);
        if (size == ExportSize.SOCIAL) return out[0] == width && out[1] == height ? rows : PngRender.shrunk(rows, width, height, out[0], out[1]);
        int k = ExportSize.HIGH_SCALE;
        byte[] g1 = new byte[width], a1 = new byte[width];
        int[] at = {-1};
        return (y, g, a) -> {
            if (y / k != at[0]) { at[0] = y / k; java.util.Arrays.fill(g1, (byte) 0); java.util.Arrays.fill(a1, (byte) 0); rows.row(at[0], g1, a1); }
            for (int x = 0; x < out[0]; x++) { g[x] = g1[x / k]; a[x] = a1[x / k]; }
        };
    }

    /**
     * High Res's layer of a kind of a pair (stack places {@code from} and {@code from + 1}, as {@code marks} composes
     * them): its pixels doubled, a tile row at a time ({@link SharpView#TILE}), except where a plane of it holds just
     * what its kept dry strokes lay ({@link SharpView#holds}) and the layer shows just those planes there (no reflection,
     * no other mark): there the strokes are drawn again at 2 × ({@link SharpView#drawn}), as High Res's PNG draws them.
     * A pair's Vector Ink ({@code pen}: its kind) takes its lines, drawn at 2 × ({@link VectorLines#over}), in place of their pixels.
     */
    private static final class Sharp implements PsdWriter.Rows {
        private final int k = ExportSize.HIGH_SCALE, w, h, ow, side = SharpView.TILE;
        private final ToneDocument.Snapshot page;
        private final PagePicture.Composite marks;
        private final int[] grey;
        private final ToneDocument.Layer[] entries = new ToneDocument.Layer[2];
        private final int[] opacity = new int[2];
        private final List<List<SharpView.Kept>> kept = new ArrayList<>();
        private final VectorLines lines;
        /** The tile row's marks at 2 × (inked, coverage) and at 1 ×; the tile row they hold. */
        private final int[] inked, cover, inked1, cover1;
        private final byte[] coverage;
        private int band = -1;

        Sharp(ToneDocument.Snapshot page, ToneDocument doc, PagePicture.Composite marks, int from, boolean pen, int without, List<SharpView.Kept> all, int[] grey) {
            this.page = page; this.marks = marks; this.grey = grey; w = page.width; h = page.height; ow = w * k;
            VectorLines drawn = VectorLines.NONE;
            for (int e = 0; e < 2; e++) {
                int place = from + e, owner = ToneDocument.stackOwner(place);
                ToneDocument.Layer entry = ToneDocument.stacked(page.layers, place);
                List<SharpView.Kept> on = new ArrayList<>();
                kept.add(on);
                if (entry == null) continue;
                ToneDocument.Layer layer = page.layers.get(owner);
                if (pen && (without >> owner & 1) != 0) { VectorLines own = doc.lines(owner); if (own != null) drawn = drawn.with(own); continue; }
                if (!layer.visible) continue;
                entries[e] = entry; opacity[e] = ToneDocument.stackOpacity(layer, entry);
                for (SharpView.Kept s : all) { ToneDocument.Layer plane = doc.addressed(s.e.plane); if (plane != null && plane.tones == entry.tones) on.add(s); }
            }
            lines = drawn.isEmpty() ? null : drawn;
            inked = new int[ow * side * k]; cover = new int[inked.length]; inked1 = new int[w * side]; cover1 = new int[inked1.length];
            coverage = lines == null ? null : new byte[inked.length];
        }

        @Override public void row(int y, byte[] g, byte[] a) {
            if (y / (side * k) != band) draw(band = y / (side * k));
            for (int x = 0, i = (y - band * side * k) * ow; x < ow; x++, i++) pixel(inked[i], cover[i], page.paper, grey, g, a, x);
        }

        /** Tile row {@code ty}. */
        private void draw(int ty) {
            int top = ty * side, rows = Math.min(side, h - top);
            int[] ri = new int[w], rc = new int[w];
            for (int r = 0; r < rows; r++) {
                marks.marks(top + r, ri, rc);
                System.arraycopy(ri, 0, inked1, r * w, w); System.arraycopy(rc, 0, cover1, r * w, w);
                for (int j = 0; j < k; j++) for (int x = 0, o = (r * k + j) * ow; x < ow; x++) { inked[o + x] = ri[x / k]; cover[o + x] = rc[x / k]; }
            }
            for (int tx = 0; tx * side < w; tx++) tile(tx * side, top, Math.min(side, w - tx * side), rows);
            if (lines != null) {
                java.util.Arrays.fill(coverage, (byte) 0);
                lines.over(k, 0, 0, 0, top * k, ow, rows * k, inked, cover, coverage);
            }
        }
        /** The tile at page px {@code x, top}, {@code tw × th}: its kept strokes drawn finer where the layer shows just them. */
        private void tile(int x, int top, int tw, int th) {
            SharpStrokes.GridPage[] once = new SharpStrokes.GridPage[2], fine = new SharpStrokes.GridPage[2];
            boolean any = false;
            for (int e = 0; e < 2; e++) {
                List<SharpView.Kept> here = new ArrayList<>();
                for (SharpView.Kept s : kept.get(e)) if (s.box[0] < x + tw && s.box[2] > x && s.box[1] < top + th && s.box[3] > top) here.add(s);
                if (here.isEmpty()) continue;
                once[e] = SharpView.drawn(x, top, tw, th, w, h, here, 1);
                boolean some = false;
                for (int p = 0; p < tw * th && !some; p++) some = once[e].alpha(p) > 0;
                if (!some) { once[e] = null; continue; }
                fine[e] = SharpView.drawn(x, top, tw, th, w, h, here, k);
                any = true;
            }
            if (!any) return;
            int fw = tw * k;
            boolean[] held = new boolean[2];
            for (int py = 0; py < th; py++) for (int px = 0; px < tw; px++) {
                int p = py * tw + px, at = (top + py) * w + x + px, one = py * w + x + px;
                boolean finer = false;
                for (int e = 0; e < 2; e++) {
                    held[e] = fine[e] != null && SharpView.holds(once[e], p, entries[e].tones[at] & 255, entries[e].alpha[at] & 255);
                    finer |= held[e];
                }
                if (!finer) continue;
                // Only where the layer shows just these planes' pixels there (no reflection over them).
                int ki = 0, ci = 0;
                for (int e = 0; e < 2; e++) if (entries[e] != null) { int c = (entries[e].alpha[at] & 255) * opacity[e]; c = (c + 50) / 100; ki = ((entries[e].tones[at] & 255) * c + ki * (255 - c) + 127) / 255; ci = c + (ci * (255 - c) + 127) / 255; }
                if (ki != inked1[one] || ci != cover1[one]) continue;
                for (int j = 0; j < k; j++) for (int q = 0; q < k; q++) {
                    int f = (py * k + j) * fw + px * k + q, fi = 0, fc = 0;
                    for (int e = 0; e < 2; e++) {
                        if (entries[e] == null) continue;
                        int tone = held[e] ? fine[e].raw(f) : entries[e].tones[at] & 255, c = held[e] ? fine[e].alpha(f) : entries[e].alpha[at] & 255;
                        c = (c * opacity[e] + 50) / 100;
                        fi = (tone * c + fi * (255 - c) + 127) / 255; fc = c + (fc * (255 - c) + 127) / 255;
                    }
                    int o = (py * k + j) * ow + (x + px) * k + q;
                    inked[o] = fi; cover[o] = fc;
                }
            }
        }
    }

    private PageExport() {}
}
