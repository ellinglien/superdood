package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Second Ink in the copies (docs/superpowers/specs/2026-11-09-second-ink-design.md, "Copies"): a page without second ink
 * exports the bytes it did before the copies had colour ({@link #BEFORE}); one with it exports an RGB PNG, its second-ink
 * marks in the notebook's ink at their depth, first ink and letters over them black, paper and magnets grey, at every
 * {@link ExportSize}, and its PDF page {@code /DeviceRGB}. With {@code hash}, prints {@link #plain}'s.
 */
public final class SecondInkCopyChecks {
    static int checks;
    static void check(boolean ok, String what) { checks++; if (!ok) throw new AssertionError(what); }

    static final int W = 240, H = 320;
    static final long TODAY = 20000;
    /** {@link #plain} as the version before the copies' colour (583462fc) wrote it. */
    static final String BEFORE = "cb2902014ab9bcced604db794635d8cc20495d205f7e05bc256c0a8b2fb91499";

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length == 1 && args[0].equals("hash")) { System.out.println(plain()); return; }
        check(plain().equals(BEFORE), "A page without second ink exports the PNG and PDF bytes it did before, at every size: " + plain());
        // Letters from here on: the hash above is of pages whose text boxes have none, whatever the machine's fonts.
        TextBoxes.type = new TextBoxChecks.DeskType();
        colour();
        inks();
        sizes();
        shown();
        notebook();
        System.out.println("PASS: Second Ink's copies (" + checks + " checks)");
    }

    /**
     * The checks' page, {@code k} times {@link #W} × {@link #H}: washes whose second ink is {@code share} (0 to 255; -1:
     * none declared) black and half deep, one under a black pen line, one under a text box and one under a magnet, and a
     * first-ink wash of a hundred greys.
     */
    static ToneDocument page(int share, int k) {
        ToneDocument d = new ToneDocument(W * k, H * k);
        SecondInkChecks.wash(d, share, 20 * k, 20 * k, 60 * k, 40 * k, 0);
        SecondInkChecks.wash(d, share, 80 * k, 20 * k, 120 * k, 40 * k, 128);
        d.begin(1, ToneDocument.KIND_PAINT);
        for (int y = 20 * k; y < 40 * k; y++) for (int x = 130 * k; x < 230 * k; x++) d.setTone(x, y, x / k);
        d.finish();
        SecondInkChecks.wash(d, share, 20 * k, 60 * k, 120 * k, 100 * k, 128);
        d.begin(1, ToneDocument.KIND_PEN);
        for (int y = 75 * k; y < 80 * k; y++) for (int x = 30 * k; x < 110 * k; x++) d.setTone(x, y, 0);
        d.finish();
        SecondInkChecks.wash(d, share, 20 * k, 120 * k, 220 * k, 200 * k, 128);
        d.placeTexts(TextBoxes.of(Collections.singletonList(new TextBoxes.Box("MM", 30 * k, 125 * k, 180 * k, 0, true, TextBoxes.LEFT))));
        SecondInkChecks.wash(d, share, 100 * k, 230 * k, 230 * k, 300 * k, 100);
        d.placeMagnets(Magnets.of(Collections.singletonList(MAGNET)));
        return d;
    }
    static final Magnets.Magnet MAGNET = new Magnets.Magnet("ink", 165, 265, 0, 0);
    /** A page High Res draws sharp, as {@code PngRenderChecks} draws it: a kept pencil stroke, Vector Ink's lines over it. */
    static ToneDocument sharp() {
        ToneDocument d = InkWashLayers.blank(200, 120);
        d.surface = BrushTexture.COLD_PRESS;
        WatercolorSession s = new WatercolorSession();
        SharpChecks.draw(d, s, SharpChecks.preset("HB pencil"), 4, 1, SharpChecks.hand(new Random(3), 40, 30, 90, 10, false), 1);
        VectorInkChecks.draw(d, s, VectorInkChecks.vector(), 6, 1, VectorInkChecks.hand(50, 11));
        return d;
    }
    static List<PackedPage> packed(ToneDocument d) throws Exception { return Collections.singletonList(NotebookPdfChecks.packed(d)); }
    static byte[] copy(ToneDocument d, String choice, ExportSize size, NotebookOptions options) throws Exception {
        return PageExport.bytes("Ink", 0, choice, size, packed(d), options, null, null, PageExportChecks.tiles(), TODAY);
    }

    static final NotebookOptions BLUE = NotebookOptions.DEFAULT, SEPIA = BLUE.withInk(1);
    static final int[][] BOXES = {{20, 20, 60, 40}, {80, 20, 120, 40}, {20, 60, 120, 100}, {20, 120, 220, 200}, {100, 230, 230, 300}};
    static boolean colour(byte[] png) { return png[25] == 2; }
    /** A copy's pixels, 0xRRGGBB: an RGB PNG's as they are, a grey one's greys in each channel. */
    static int[] pixels(byte[] png) throws Exception {
        int[] size = new int[2];
        if (!colour(png)) { int[] greys = PngRenderChecks.read(png, size); for (int i = 0; i < greys.length; i++) greys[i] *= 0x010101; return greys; }
        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new ByteArrayInputStream(png));
        int[] out = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        for (int i = 0; i < out.length; i++) out[i] &= 0xffffff;
        return out;
    }
    /** {@code grey} (a pixel of the page without second ink) wholly in ink {@code ink}. */
    static int full(int grey, int ink) {
        int out = 0;
        for (int c = 16; c >= 0; c -= 8) out |= NotebookExport.channel(grey & 255, 255, SecondInk.rgb(ink) >> c & 255) << c;
        return out;
    }
    static boolean near(int a, int b, int by) { for (int c = 0; c <= 16; c += 8) if (Math.abs((a >> c & 255) - (b >> c & 255)) > by) return false; return true; }
    /** Whether every pixel of {@code got} is {@code plain}'s grey with some of ink {@code ink} in it: never darker, never past {@link #full}. */
    static boolean tinted(int[] got, int[] plain, int ink) {
        if (got.length != plain.length) return false;
        for (int i = 0; i < got.length; i++) for (int c = 0; c <= 16; c += 8) {
            int v = got[i] >> c & 255;
            if (v < (plain[i] & 255) || v > (full(plain[i], ink) >> c & 255)) return false;
        }
        return true;
    }
    static int count(int[] pixels, int w, int[] box, int value) {
        int n = 0;
        for (int y = box[1]; y < box[3]; y++) for (int x = box[0]; x < box[2]; x++) if (pixels[y * w + x] == value) n++;
        return n;
    }

    /** The page's copy: an RGB PNG its PDF page shows as it is, second ink in the notebook's ink at its depth, everything else as without. */
    static void colour() throws Exception {
        ToneDocument inked = page(255, 1), none = page(-1, 1);
        byte[] png = copy(inked, PageExport.PNG, ExportSize.PAGE, BLUE), grey = copy(none, PageExport.PNG, ExportSize.PAGE, BLUE);
        int[] got = pixels(png), plain = pixels(grey);
        int ink = SecondInk.rgb(0), half = full(plain[30 * W + 100], 0);
        check(colour(png) && !colour(grey), "a page with second ink is an RGB PNG, one without the grey one");
        NotebookExport.Png read = NotebookExport.read(png);
        check(read != null && read.colour && read.width == W && read.height == H && read.crc == packed(inked).get(0).crc, "the app reads its RGB PNG, its page's key in it");
        java.util.zip.Inflater inflater = new java.util.zip.Inflater();
        inflater.setInput(read.data);
        check(inflater.inflate(new byte[H * (1 + 3 * W) + 1]) == H * (1 + 3 * W) && inflater.finished(), "its rows hold three bytes a pixel");
        check(got[30 * W + 30] == ink, "black second ink is the ink itself: " + Integer.toHexString(got[30 * W + 30]));
        check(got[30 * W + 100] == half && half != ink && half != 0xffffff && half != plain[30 * W + 100], "a half-deep wash is the ink at that depth: " + Integer.toHexString(got[30 * W + 100]));
        check(got[77 * W + 50] == 0 && got[65 * W + 50] == half, "a black pen line over a second-ink wash stays black, the wash beside it in colour");
        check(tinted(got, plain, 0), "every pixel is its grey with some ink in it, never darker");
        boolean same = true;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            boolean in = false;
            for (int[] b : BOXES) in |= x >= b[0] && x < b[2] && y >= b[1] && y < b[3];
            same &= in || got[y * W + x] == plain[y * W + x];
        }
        check(same && got[5 * W + 5] == 0xffffff && got[30 * W + 180] == 0x010101 * DotPattern.exportGray(180), "paper and first ink stay as they were, a grey wash grey");
        // Letters over the wash are black (all but an edge pixel or two, a shade off it), the wash between them the ink.
        int letters = count(plain, W, BOXES[3], 0);
        check(letters > 0 && count(got, W, BOXES[3], 0) > letters * 9 / 10 && count(got, W, BOXES[3], half) == count(plain, W, BOXES[3], plain[30 * W + 100]), "a text box's letters stay black over second ink: " + letters);
        // A magnet over it: its face as it was, its soft edge over the ink's colour.
        int[] m = Magnets.bounds(MAGNET);
        check(m[0] >= BOXES[4][0] && m[1] >= BOXES[4][1] && m[2] <= BOXES[4][2] && m[3] <= BOXES[4][3], "the checks' magnet sits on its wash");
        int under = full(plain[235 * W + 105], 0), edge = 0;
        for (int c = 0; c <= 16; c += 8) edge |= (0x40 * 0x80 + (under >> c & 255) * (255 - 0x80) + 127) / 255 << c;
        check(got[265 * W + 165] == 0x202020 && got[235 * W + 105] == under && near(got[(m[1] + 1) * W + m[0] + 1], edge, 1), "a magnet shows over second ink as over any colour: " + Integer.toHexString(got[(m[1] + 1) * W + m[0] + 1]) + " for " + Integer.toHexString(edge));
        String pdf = new String(copy(inked, PageExport.PDF, ExportSize.PAGE, BLUE), StandardCharsets.ISO_8859_1), greyPdf = new String(copy(none, PageExport.PDF, ExportSize.PAGE, BLUE), StandardCharsets.ISO_8859_1);
        check(pdf.contains("/ColorSpace /DeviceRGB") && pdf.contains("/Colors 3") && !greyPdf.contains("DeviceRGB") && !greyPdf.contains("/Colors 3"), "its PDF page's picture is RGB, a page's without second ink grey");
        // The shares alone make the colour: without them the page is the grey copy, byte for byte (a snapshot shares its page's, so a page of its own).
        ToneDocument.Snapshot page = NotebookExport.flatten(Collections.singletonList(page(255, 1)), new boolean[1], 0);
        byte[] before = NotebookExport.png(NotebookExport.flatten(Collections.singletonList(none), new boolean[1], 0), 1, PageExportChecks.tiles());
        for (ToneDocument.Layer layer : page.layers) for (int p = 0; p < ToneDocument.PLANES; p++) if (layer.plane(p) != null && layer.plane(p).ink != null) Arrays.fill(layer.plane(p).ink, (byte) 0);
        check(Arrays.equals(NotebookExport.png(page, 1, PageExportChecks.tiles()), before), "second-ink shares all at none: the grey PNG");
        for (ToneDocument.Layer layer : page.layers) for (int p = 0; p < ToneDocument.PLANES; p++) if (layer.plane(p) != null) layer.plane(p).ink = null;
        check(Arrays.equals(NotebookExport.png(page, 1, PageExportChecks.tiles()), before), "no second-ink shares: the grey PNG");
        // Tinted paper stays its grey.
        inked.setPaper(200); none.setPaper(200);
        got = pixels(copy(inked, PageExport.PNG, ExportSize.PAGE, BLUE)); plain = pixels(copy(none, PageExport.PNG, ExportSize.PAGE, BLUE));
        check(got[5 * W + 5] == plain[5 * W + 5] && plain[5 * W + 5] != 0xffffff && got[30 * W + 30] == ink && tinted(got, plain, 0), "grey paper stays grey around the ink");
        check(got[30 * W + 100] != plain[30 * W + 100] && got[30 * W + 100] != full(plain[30 * W + 100], 0), "the paper's grey under a wash is no ink: the wash's share of the darkness in colour");
    }

    /** The notebook's ink: each copy in its own, a copy in another ink known for it by its key; a page without second ink the same under any. */
    static void inks() throws Exception {
        ToneDocument inked = page(255, 1), none = page(-1, 1);
        long crc = packed(inked).get(0).crc;
        byte[] blue = copy(inked, PageExport.PNG, ExportSize.PAGE, BLUE), sepia = copy(inked, PageExport.PNG, ExportSize.PAGE, SEPIA);
        check(pixels(sepia)[30 * W + 30] == SecondInk.rgb(1) && SecondInk.rgb(1) != SecondInk.rgb(0), "Sepia's copy is sepia");
        check(tinted(pixels(sepia), pixels(copy(none, PageExport.PNG, ExportSize.PAGE, BLUE)), 1), "and the same greys under it");
        NotebookExport.Png b = NotebookExport.read(blue), s = NotebookExport.read(sepia);
        check(b.key(0) == crc && s.key(1) == crc && b.key(1) != crc && s.key(0) != crc && s.crc != crc, "a copy's key is its page's in its own ink only");
        for (ExportSize size : ExportSize.values()) for (String choice : new String[]{PageExport.PNG, PageExport.PDF})
            check(Arrays.equals(copy(none, choice, size, BLUE), copy(none, choice, size, SEPIA)), "a page without second ink is the same copy in any ink: " + choice + " " + size);
        byte[] fridge = Fridge.png("Ink", 0, W, H, packed(inked), SEPIA, null, null, PageExportChecks.tiles(), false);
        check(colour(fridge) && pixels(fridge)[30 * W + 30] == SecondInk.rgb(1), "the Fridge's page is in the notebook's ink");
    }

    /** Social and High Res: the same colours at their sizes; High Res's sharp lines black over the ink. */
    static void sizes() throws Exception {
        ToneDocument inked = page(255, 1), none = page(-1, 1);
        int ink = SecondInk.rgb(0);
        byte[] high = copy(inked, PageExport.PNG, ExportSize.HIGH, BLUE);
        int[] got = pixels(high), plain = pixels(copy(none, PageExport.PNG, ExportSize.HIGH, BLUE));
        NotebookExport.Png read = NotebookExport.read(high);
        check(colour(high) && read.colour && read.width == 2 * W && read.height == 2 * H && read.key(0) == packed(inked).get(0).crc, "High Res's PNG is RGB at twice the page");
        check(got[60 * 2 * W + 60] == ink && got[154 * 2 * W + 100] == 0 && got[10 * 2 * W + 10] == 0xffffff && got[60 * 2 * W + 200] == full(plain[60 * 2 * W + 200], 0) && tinted(got, plain, 0), "its ink, pen line and paper as the page's");
        ToneDocument large = page(255, 5), largeNone = page(-1, 5);
        byte[] social = copy(large, PageExport.PNG, ExportSize.SOCIAL, BLUE);
        got = pixels(social); plain = pixels(copy(largeNone, PageExport.PNG, ExportSize.SOCIAL, BLUE));
        read = NotebookExport.read(social);
        check(colour(social) && read.width == 1080 && read.height == 1440, "Social's PNG is RGB at its size");
        check(got[135 * 1080 + 180] == ink && got[135 * 1080 + 450] == full(plain[135 * 1080 + 450], 0) && got[5 * 1080 + 5] == 0xffffff && tinted(got, plain, 0), "its ink shrunk with its greys: " + Integer.toHexString(got[135 * 1080 + 450]));
        // An edge between ink and paper shrinks to the ink's colour, paler: no grey fringe.
        check((PngRender.shrink(new byte[]{(byte) 255, 0}, new byte[]{0, (byte) 255}, 2, 1, 1, 1)[0] & 255) == 255, "ink beside paper shrinks to all ink, half as deep");
        // A sharp page: a second-ink wash under kept pencil and Vector Ink's lines.
        ToneDocument sharp = sharp(), sharpNone = sharp();
        SecondInkChecks.wash(sharp, 255, 0, 0, 200, 120, 128); SecondInkChecks.wash(sharpNone, -1, 0, 0, 200, 120, 128);
        high = copy(sharp, PageExport.PNG, ExportSize.HIGH, BLUE);
        got = pixels(high); plain = pixels(copy(sharpNone, PageExport.PNG, ExportSize.HIGH, BLUE));
        int black = 0, blackNone = 0, washed = 0, wash = full(0x010101 * DotPattern.exportGray(128), 0);
        for (int i = 0; i < got.length; i++) { if (got[i] == 0) black++; if (plain[i] == 0) blackNone++; if (got[i] == wash) washed++; }
        check(colour(high) && tinted(got, plain, 0) && blackNone > 0 && black > blackNone * 9 / 10 && washed > got.length / 2, "High Res's sharp lines are black over a second-ink wash: " + black + " of " + blackNone + ", wash " + washed);
        check(new String(copy(sharp, PageExport.PDF, ExportSize.PAGE, BLUE), StandardCharsets.ISO_8859_1).contains("/ColorSpace /DeviceRGB"), "a sharp page's PDF picture is RGB too");
        // Layers stays grey: its file is the same with and without the shares' colour.
        check(Arrays.equals(copy(inked, PageExport.LAYERS, ExportSize.HIGH, BLUE), copy(inked, PageExport.LAYERS, ExportSize.HIGH, SEPIA)), "the Layers copy knows no ink");
    }

    /** Whether {@code inked}'s copies are grey, their pixels {@code none}'s: the page's PNG, High Res's and the PDF's picture. */
    static boolean grey(ToneDocument inked, ToneDocument none) throws Exception {
        for (ExportSize size : new ExportSize[]{ExportSize.PAGE, ExportSize.HIGH}) {
            byte[] png = copy(inked, PageExport.PNG, size, SEPIA);
            if (colour(png) || !Arrays.equals(pixels(png), pixels(copy(none, PageExport.PNG, size, SEPIA)))) return false;
        }
        return !new String(copy(inked, PageExport.PDF, ExportSize.PAGE, SEPIA), StandardCharsets.ISO_8859_1).contains("DeviceRGB");
    }
    /** Second ink shows as its layer does: as deep as a part-opacity layer shows it, none from a layer at no opacity or a hidden one; and in a Real Mirror's reflection. */
    static void shown() throws Exception {
        ToneDocument inked = page(255, 1), none = page(-1, 1);
        int layer = inked.activeLayer(), ink = SecondInk.rgb(0);
        inked.setLayerOpacity(layer, 50); none.setLayerOpacity(layer, 50);
        byte[] png = copy(inked, PageExport.PNG, ExportSize.PAGE, BLUE);
        int[] got = pixels(png), plain = pixels(copy(none, PageExport.PNG, ExportSize.PAGE, BLUE));
        check(colour(png) && plain[30 * W + 30] != 0 && got[30 * W + 30] == full(plain[30 * W + 30], 0) && tinted(got, plain, 0), "a layer at half opacity: its second ink the ink at the depth it shows: " + Integer.toHexString(got[30 * W + 30]));
        inked.setLayerOpacity(layer, 0); none.setLayerOpacity(layer, 0);
        check(grey(inked, none), "second ink on a layer at no opacity: grey copies");
        inked.setLayerOpacity(layer, 100); none.setLayerOpacity(layer, 100);
        inked.setLayerVisible(layer, false); none.setLayerVisible(layer, false);
        check(grey(inked, none), "second ink on a hidden layer: grey copies");
        ToneDocument mirrored = page(255, 1), mirroredNone = page(-1, 1);
        Mirrors mirrors = Mirrors.NONE.add(new Mirrors.Line(W / 2.0, H / 2.0, Math.PI / 2)).styled(Mirrors.Style.REAL).keeping(30, 30);
        mirrored.setMirrors(mirrors); mirroredNone.setMirrors(mirrors);
        for (int k = 1; k <= 2; k++) {
            ExportSize size = k == 1 ? ExportSize.PAGE : ExportSize.HIGH;
            png = copy(mirrored, PageExport.PNG, size, BLUE);
            got = pixels(png); plain = pixels(copy(mirroredNone, PageExport.PNG, size, BLUE));
            int here = 30 * k * W * k + 30 * k, there = 30 * k * W * k + (W - 1 - 30) * k;
            check(colour(png) && plain[there] == 0 && got[here] == ink && got[there] == ink && tinted(got, plain, 0), "Real Mirror: second ink's reflection is in the ink too: " + size + " " + Integer.toHexString(got[there]));
        }
    }

    /** Export Now: another ink draws the pages with second ink again, and the PDF with them; the others keep their PNGs. */
    static void notebook() throws Exception {
        File folder = Files.createTempDirectory("second-ink").toFile();
        List<PackedPage> pages = Arrays.asList(NotebookPdfChecks.packed(page(255, 1)), NotebookPdfChecks.packed(page(-1, 1)));
        NotebookExport.Done done = NotebookExport.update(folder, "Ink", pages, PageExportChecks.tiles(), null, BLUE);
        File first = new File(new File(folder, "Ink"), "001.png"), second = new File(new File(folder, "Ink"), "002.png");
        byte[] pdf = Files.readAllBytes(done.pdf.toPath()), plain = Files.readAllBytes(second.toPath());
        check(done.drawn == 2 && colour(Files.readAllBytes(first.toPath())) && !colour(plain) && new String(pdf, StandardCharsets.ISO_8859_1).contains("/DeviceRGB"), "Export Now: the page with second ink in colour, the other grey");
        done = NotebookExport.update(folder, "Ink", pages, PageExportChecks.tiles(), null, BLUE);
        check(done.drawn == 0 && done.kept == 2 && Arrays.equals(Files.readAllBytes(done.pdf.toPath()), pdf), "nothing changed: nothing drawn");
        done = NotebookExport.update(folder, "Ink", pages, PageExportChecks.tiles(), null, SEPIA);
        byte[] sepia = Files.readAllBytes(done.pdf.toPath());
        check(done.drawn == 1 && done.kept == 1 && pixels(Files.readAllBytes(first.toPath()))[30 * W + 30] == SecondInk.rgb(1) && Arrays.equals(Files.readAllBytes(second.toPath()), plain), "another ink: the page with second ink drawn again, the other kept");
        check(!Arrays.equals(sepia, pdf), "and the PDF written again with it");
        done = NotebookExport.update(folder, "Ink", pages, PageExportChecks.tiles(), null, SEPIA);
        check(done.drawn == 0 && done.kept == 2 && Arrays.equals(Files.readAllBytes(done.pdf.toPath()), sepia), "then nothing more to draw");
        // The PDF's writing fails once the copies are in another ink (a folder where it is written first): the next time
        // writes it, though every copy is kept. To the first ink, whose record says nothing of it, and from it.
        byte[] was = sepia;
        for (NotebookOptions next : new NotebookOptions[]{BLUE, SEPIA}) {
            File temporary = new File(done.pdf.getParentFile(), "." + done.pdf.getName() + ".tmp");
            check(temporary.mkdir(), "(a folder in the PDF's way)");
            boolean failed = false;
            try { NotebookExport.update(folder, "Ink", pages, PageExportChecks.tiles(), null, next); } catch (java.io.IOException e) { failed = true; }
            temporary.delete();
            check(failed && pixels(Files.readAllBytes(first.toPath()))[30 * W + 30] == SecondInk.rgb(next.ink) && Arrays.equals(Files.readAllBytes(done.pdf.toPath()), was), "the PDF's writing fails: the copies in the new ink, the PDF as it was");
            done = NotebookExport.update(folder, "Ink", pages, PageExportChecks.tiles(), null, next);
            byte[] now = Files.readAllBytes(done.pdf.toPath());
            check(done.drawn == 0 && done.kept == 2 && !Arrays.equals(now, was) && Arrays.equals(now, next == BLUE ? pdf : sepia), "the next Export Now writes the PDF in the new ink, every copy kept");
            was = now;
        }
        for (File f : new File(folder, "Ink").listFiles()) f.delete();
        for (File f : folder.listFiles()) f.delete();
        folder.delete();
    }

    /** A hash of pages without second ink as every copy writes them: each size's PNG, plain and Lined, the PDFs, a page shrunk for Social, a sharp one and one of two greys (a palette PNG). */
    static String plain() throws Exception {
        java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
        ToneDocument small = page(-1, 1);
        NotebookOptions lined = NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.LINED);
        for (ExportSize size : ExportSize.values()) for (NotebookOptions o : new NotebookOptions[]{NotebookOptions.DEFAULT, lined}) sha.update(copy(small, PageExport.PNG, size, o));
        sha.update(copy(small, PageExport.PDF, ExportSize.PAGE, lined));
        ByteArrayOutputStream now = new ByteArrayOutputStream();
        NotebookExport.pdf(now, "Ink", "Ink", packed(small), PageExportChecks.tiles(), null, NotebookOptions.DEFAULT, null, null, TODAY);
        sha.update(now.toByteArray());
        sha.update(copy(page(-1, 5), PageExport.PNG, ExportSize.SOCIAL, NotebookOptions.DEFAULT));
        sha.update(copy(sharp(), PageExport.PNG, ExportSize.HIGH, NotebookOptions.DEFAULT));
        sha.update(copy(sharp(), PageExport.PDF, ExportSize.PAGE, NotebookOptions.DEFAULT));
        // A page of two greys: the palette PNG.
        ToneDocument few = new ToneDocument(W, H);
        few.begin(1, ToneDocument.KIND_PEN);
        for (int y = 75; y < 80; y++) for (int x = 30; x < 110; x++) few.setTone(x, y, 0);
        few.finish();
        byte[] palette = copy(few, PageExport.PNG, ExportSize.PAGE, NotebookOptions.DEFAULT);
        if (palette[25] != 3) throw new AssertionError("(a page of two greys is a palette PNG)");
        sha.update(palette);
        StringBuilder hex = new StringBuilder();
        for (byte b : sha.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
