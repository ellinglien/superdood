package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Surface (the surface spec, docs/superpowers/specs/2026-11-01-surface-design.md): the notebook's and a page's own,
 * kept in the file and the recovery; old notebooks Smooth; every brush meets the page's surface whatever paper its
 * code names, its code unchanged; washes soft on paper that drinks and hard on paper that doesn't; each surface whole,
 * no repeat across a page and no seam where it tiles. {@code java SurfaceChecks timing} prints what making each
 * surface and drawing on it costs.
 */
public final class SurfaceChecks {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length > 0 && args[0].equals("timing")) { timing(); return; }
        properties(); options(); notebook(); fresh(); follows(); washes(); grains(); samples();
        System.out.println("PASS: a notebook's Surface (SURF) and a page's own (PSRF) are kept in the file and the recovery, written only when set (an old notebook byte for byte, and Smooth), a new notebook on Fine (written, read back, recovered), a page's own wins and goes with the page; "
                + "every brush draws with the page's surface at its own Texture, its code unchanged (an old Cotton brush on a Cold press page draws as a Cold press one); a wash on Smooth keeps a harder edge than on Cotton; "
                + "each surface is made the same every time, 2048 px square, no repeat within a page's width and no seam where it tiles, the fibrous ones with a fibre map; Brush Lab's samples keep each surface's apart");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static void properties() {
        check(BrushTexture.PLAIN.absorbency == .35f && BrushTexture.COLD_PRESS.absorbency == .9f && BrushTexture.UNRYU.absorbency == 1.45f,
                "Absorbency moved from feathering's table: Smooth .35, Cold press .9, Unryu 1.45");
        check(LaterMedia.wicks(BrushTexture.UNRYU, .5) == 1.45f && LaterMedia.wicks(BrushTexture.PLAIN, .9) == .35f, "Feathering reads it");
        check(BrushTexture.of(99) == BrushTexture.DEFAULT && BrushTexture.of(8) == BrushTexture.UNRYU && BrushTexture.DEFAULT == BrushTexture.PLAIN, "An unknown surface is Smooth");
        for (BrushTexture t : BrushTexture.values()) check(PartChoice.of(BrushPart.PAPER, t.ordinal()).texture() == t, "Each surface is its Paper choice, index for index: " + t);
        // Its words and its screen (docs/TEXT-STYLE.md).
        NotebookOptions cold = NotebookOptions.DEFAULT.withSurface(BrushTexture.COLD_PRESS);
        String[] page = CardSheet.names(CardSheet.Screen.PAGE_SURFACE, java.util.Collections.emptyList(), cold);
        check(LibraryCard.value(LibraryCard.Part.SURFACE, cold, CoverDesign.DEFAULT).equals("Cold Press") && LibraryCard.pageSurface(BrushTexture.PLAIN, false).equals("Notebook's (Smooth)")
                && LibraryCard.pageSurface(BrushTexture.UNRYU, true).equals("Unryu") && page.length == BrushTexture.values().length && page[1].equals("Notebook's (Cold Press)") && page[0].equals("Smooth")
                && CardSheet.title(CardSheet.Screen.SURFACE).equals("Surface") && CardSheet.about(CardSheet.Screen.PAGE_SURFACE, 4).equals("The paper page 5 is on alone")
                && CardSheet.chosen(CardSheet.Screen.SURFACE, cold, null, null, null, null) == 1 && LibraryCard.screen(LibraryCard.Part.SURFACE) == CardSheet.Screen.SURFACE && LibraryCard.pageOpens(LibraryCard.Part.SURFACE),
                "The Surface rows and screens say the surface's name, the notebook's said so on a page's");
        byte[] tile = CardSheet.surfacePicture(BrushTexture.FELT, 120, 90);
        int dark = 0;
        for (byte b : tile) if ((b & 255) < 128) dark++;
        check(tile.length == 120 * 90 && dark > 120 * 90 / 8 && dark < 120 * 90 * 7 / 8, "A Surface tile's pencil shades its tooth: " + dark);
    }

    static void options() throws java.io.IOException {
        NotebookOptions d = NotebookOptions.DEFAULT;
        check(d.surface == BrushTexture.PLAIN && d.isDefault() && !d.withSurface(BrushTexture.LAID).isDefault() && d.withSurface(BrushTexture.LAID).withSurface(null).isDefault(), "Smooth by default");
        BookOptions.Covers covers = new BookOptions.Covers();
        covers.options = d.withSurface(BrushTexture.CANVAS);
        covers.pageSurfaces = new int[]{0, 1 + BrushTexture.UNRYU.ordinal(), 0};
        byte[] block = BookOptions.bytes(covers);
        BookOptions.Found found = BookOptions.find(BookOptions.of(block), 0, block.length);
        BookOptions.Covers read = BookOptions.covers(found, BookOptions.of(block), null, 3);
        check(found.get(BookOptions.SURFACE) != null && found.get(BookOptions.PAGE_SURFACES) != null && read.options.surface == BrushTexture.CANVAS
                && Arrays.equals(read.pageSurfaces, covers.pageSurfaces), "SURF and PSRF read back");
        check(BookOptions.covers(found, BookOptions.of(block), null, 4).pageSurfaces == null && BookOptions.pageSurfaceBytes(new int[3]) == null
                && d.readSurface(new byte[]{(byte) 200}).surface == BrushTexture.PLAIN && BookOptions.pageSurfaces(BookOptions.pageSurfaceBytes(new int[]{0, 99}), 2)[1] == 0,
                "Another page count, none set, a surface this version doesn't know: left out, Smooth or the notebook's");
    }

    /** A new notebook starts on Fine (the owner, 2026-11-03) and writes it; a file without SURF is still Smooth. */
    static void fresh() throws Exception {
        DrawingBook made = DrawingBook.fresh(InkWashLayers.blank(64, 48));
        check(made.options().equals(NotebookOptions.NEW) && made.options().surface == BrushTexture.FINE_SPECK && made.current().surface == BrushTexture.FINE_SPECK
                && LibraryCard.value(LibraryCard.Part.SURFACE, made.options(), CoverDesign.DEFAULT).equals("Fine") && NotebookOptions.DEFAULT.surface == BrushTexture.PLAIN,
                "A new notebook's options say Fine; the defaults (a missing record) stay Smooth");
        byte[] file = NotebookOptionsChecks.bytes(made);
        DrawingBook back = BookCodec.read(new ByteArrayInputStream(file));
        // Read back Fine: SURF was written (without it a file reads Smooth).
        check(back.options().surface == BrushTexture.FINE_SPECK && back.current().surface == BrushTexture.FINE_SPECK
                && Arrays.equals(NotebookOptionsChecks.bytes(back), file), "A Fine notebook writes SURF and round-trips byte for byte");
        ByteArrayOutputStream recovery = new ByteArrayOutputStream();
        RecoveryCodec.write(recovery, made.snapshot(), "");
        check(RecoveryCodec.read(new ByteArrayInputStream(recovery.toByteArray())).book.options().surface == BrushTexture.FINE_SPECK, "and its recovery keeps Fine");
        DrawingBook old = BookCodec.read(new ByteArrayInputStream(NotebookOptionsChecks.bytes(new DrawingBook(InkWashLayers.blank(64, 48)))));
        check(old.options().surface == BrushTexture.PLAIN && old.current().surface == BrushTexture.PLAIN, "A file without SURF still reads Smooth");
    }

    static void notebook() throws Exception {
        DrawingBook book = NotebookOptionsChecks.book();
        book.addPage(); book.select(0);
        byte[] old = NotebookOptionsChecks.bytes(book);
        DrawingBook opened = BookCodec.read(new ByteArrayInputStream(old));
        check(opened.options().surface == BrushTexture.PLAIN && opened.surface(1) == BrushTexture.PLAIN && opened.current().surface == BrushTexture.PLAIN,
                "An old notebook opens Smooth, its pages too");
        check(Arrays.equals(NotebookOptionsChecks.bytes(opened), old), "and is written byte for byte as before");
        book.setOptions(book.options().withSurface(BrushTexture.COLD_PRESS));
        book.setPageSurface(1, BrushTexture.WATERCOLOUR);
        check(book.surface(0) == BrushTexture.COLD_PRESS && book.surface(1) == BrushTexture.WATERCOLOUR && book.current().surface == BrushTexture.COLD_PRESS && book.unsaved(),
                "The notebook's on every page, a page's own over it, a change to save");
        book.select(1);
        check(book.current().surface == BrushTexture.WATERCOLOUR, "The page drawn on is on its own");
        book.setPageSurface(2, BrushTexture.COLD_PRESS);
        check(book.ownSurface(2) == 0, "The notebook's chosen for a page: its own goes");
        book.select(0);
        byte[] saved = NotebookOptionsChecks.bytes(book);
        File dir = Files.createTempDirectory("surface").toFile();
        try {
            File file = new File(dir, "surface.mpaint"); Files.write(file.toPath(), saved);
            for (DrawingBook b : new DrawingBook[]{BookCodec.read(new ByteArrayInputStream(saved)), BookCodec.open(file)})
                check(b.options().surface == BrushTexture.COLD_PRESS && b.surface(1) == BrushTexture.WATERCOLOUR && b.surface(2) == BrushTexture.COLD_PRESS && !b.unsaved(), "Read back, not a change");
            ByteArrayOutputStream recovery = new ByteArrayOutputStream();
            RecoveryCodec.write(recovery, book.snapshot(), "");
            DrawingBook recovered = RecoveryCodec.read(new ByteArrayInputStream(recovery.toByteArray())).book;
            check(recovered.options().surface == BrushTexture.COLD_PRESS && Arrays.equals(recovered.pageSurfaces(), book.pageSurfaces()), "The recovery keeps them");
        } finally { for (File f : dir.listFiles()) f.delete(); dir.delete(); }
        book.move(1, 2);
        check(book.surface(2) == BrushTexture.WATERCOLOUR && book.surface(1) == BrushTexture.COLD_PRESS, "Moved with the page");
        book.select(2);
        check(book.addLayer(true) && book.surface(3) == BrushTexture.WATERCOLOUR && book.current().surface == BrushTexture.WATERCOLOUR, "A layer is on its stack's surface");
        book.deletePage(2);
        check(book.surface(2) == BrushTexture.WATERCOLOUR, "The stack's bottom deleted: the layer on it keeps it");
        book.setOptions(book.options().withSurface(BrushTexture.PLAIN));
        book.setPageSurface(2, null);
        check(Arrays.equals(book.pageSurfaces(), new int[book.count()]) && book.surface(2) == BrushTexture.PLAIN, "Set back: Smooth everywhere");
    }

    /** A pencil with Texture .7 on {@code paper}. */
    static ModularBrush pencil(BrushTexture paper) {
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals("Sketch pencil")) {
            ModularBrush b = p.brush().parts.copy();
            ModularBrush.Part part = new ModularBrush.Part(PartChoice.of(BrushPart.PAPER, paper.ordinal()));
            part.set(PartDial.TOOTH, .7);
            b.set(BrushPart.PAPER, part);
            return b;
        }
        throw new IllegalStateException("No Sketch pencil");
    }
    /** {@code b} drawn across a page on {@code surface} (null: its own paper). */
    static ToneDocument drawn(ModularBrush b, BrushTexture surface) {
        ToneDocument d = InkWashLayers.blank(360, 160);
        d.surface = surface;
        CarrySmudgeChecks.feed(QuickStroke.start(d, b, 40, .8, null), FloatChecks.line(30, 80, 330, 80, .6));
        return d;
    }

    static void follows() {
        ModularBrush cotton = pencil(BrushTexture.WATERCOLOUR), cold = pencil(BrushTexture.COLD_PRESS);
        String code = ModularCode.encode(cotton);
        String onPage = WetPaintChecks.composite(drawn(cotton, BrushTexture.COLD_PRESS)), own = WetPaintChecks.composite(drawn(cold, null)), asCotton = WetPaintChecks.composite(drawn(cotton, null));
        check(onPage.equals(own) && !onPage.equals(asCotton), "An old Cotton brush on a Cold press page draws as a Cold press one, not as Cotton");
        check(ModularCode.encode(cotton).equals(code) && cotton.choice(BrushPart.PAPER) == PartChoice.COTTON, "and its code is unchanged: " + code);
        ModularBrush on = cotton.onSurface(BrushTexture.LAID);
        check(on.choice(BrushPart.PAPER) == PartChoice.LAID && on.part(BrushPart.PAPER).get(PartDial.TOOTH) == .7 && cotton.onSurface(null) == cotton && cotton.onSurface(BrushTexture.WATERCOLOUR) == cotton,
                "On a surface: that paper at its own Texture");
        check(WetPaintChecks.composite(drawn(cotton, BrushTexture.PLAIN)).equals(WetPaintChecks.composite(drawn(pencil(BrushTexture.PLAIN), null))), "On a Smooth page, Smooth");
    }

    /** A wet watercolour ({@code wash}, Soft wash or Wet blooms) laid on {@code surface} and dried back. */
    static ToneDocument washed(BrushTexture surface, String wash) {
        ToneDocument d = WetPaintChecks.page();
        d.surface = surface;
        WatercolorSession s = new WatercolorSession();
        KnifePictures.stroke(d, s, HeldPaint.ANY, WetPaintChecks.wash(wash), 70, .6, KnifePictures.path(60, 160, 420, 160, .7, 0, 0, 0, 0));
        WetPaintChecks.settle(s, d); s.settleNow(d);
        return d;
    }
    /**
     * How soft {@link #washed}'s upper edge is: the paint past its rim (its darkest row) out onto the paper, in rows of
     * the rim's darkness, each row's tone averaged along the wash. A hard edge has next to none past it; a soft one bleeds.
     */
    static double edge(BrushTexture surface) {
        ToneDocument d = washed(surface, "Soft wash");
        double[] row = new double[161];
        for (int y = 90; y <= 160; y++) { double sum = 0; for (int x = 150; x < 330; x++) sum += d.compositeTone(x, y); row[y] = sum / 180; }
        int rim = 160;
        for (int y = 160; y >= 90; y--) if (row[y] < row[rim]) rim = y;
        double past = 0;
        for (int y = rim - 1; y >= 90; y--) past += (255 - row[y]) / (255 - row[rim]);
        return past;
    }
    static void washes() {
        double smooth = edge(BrushTexture.PLAIN), cold = edge(BrushTexture.COLD_PRESS), cotton = edge(BrushTexture.WATERCOLOUR);
        System.out.println("  wash bleed past its rim: Smooth " + round(smooth) + " px, Cold press " + round(cold) + " px, Cotton " + round(cotton) + " px");
        check(smooth * 2 < cotton && smooth <= cold + .05, "A wash's edge is harder on Smooth than on Cotton: " + smooth + " against " + cotton);
        WetWatercolor.Wash a = ResolvedBrush.of(WetPaintChecks.wash("Soft wash").onSurface(BrushTexture.PLAIN), 70, .6).wet.wash,
                b = ResolvedBrush.of(WetPaintChecks.wash("Soft wash").onSurface(BrushTexture.WATERCOLOUR), 70, .6).wet.wash;
        check(a.edge > b.edge && a.bleed < b.bleed && a.feather < b.feather, "Smooth pools to a darker rim and bleeds less; Cotton soaks it in");
    }
    static String round(double v) { return String.format(java.util.Locale.ROOT, "%.1f", v); }

    static void grains() {
        check(SurfaceGrain.of(BrushTexture.COLD_PRESS, null).fine[12345] == BrushTexture.COLD_PRESS.grain().fine[12345]
                && Arrays.equals(SurfaceGrain.of(BrushTexture.FELT, scan(BrushTexture.FELT)).mid, BrushTexture.FELT.grain().mid), "Made the same every time");
        for (BrushTexture t : BrushTexture.values()) {
            if (t == BrushTexture.PLAIN) { check(t.grain() == PaperGrain.PLAIN, "Smooth is the paper's own grain, as before"); continue; }
            PaperGrain g = t.grain();
            check(g.fineSide == SurfaceGrain.SIDE && g.midSide == SurfaceGrain.SIDE, t.label + " is whole, 2048 px");
            check((g.fibre != null) == (t == BrushTexture.KRAFT || t == BrushTexture.UNRYU), t.label + ": a fibre map where its fibres are made");
            double[] ms = meanSpread(g.fine);
            check(Math.abs(ms[0] - 128) < 4 && Math.abs(ms[1] - 52) < 6, t.label + " normalised to mean 128, spread 52: " + ms[0] + ", " + ms[1]);
            double repeat = repeat(g.fine, t == BrushTexture.LAID || t == BrushTexture.CANVAS), seam = seam(g.fine);
            System.out.println("  " + t.label + ": most alike elsewhere on a page " + round(repeat) + ", seam " + round(seam));
            check(repeat < .6, t.label + " repeats within a page: " + repeat);
            check(seam <= 1, t.label + " has a seam where it tiles: " + seam);
        }
        if (BrushTexture.KRAFT.grain().fibre != null) {
            int lying = 0;
            for (byte v : BrushTexture.KRAFT.grain().fibre) if (v != 0) lying++;
            check(lying > SurfaceGrain.SIDE * SurfaceGrain.SIDE / 20 && !Float.isNaN(firstFibre(BrushTexture.KRAFT.grain())), "Kraft's fibres mapped, their way read back");
        }
        // The old 256-px scan, for comparison: it is itself again 256 px on.
        byte[] old = scan(BrushTexture.CANVAS), tiled = new byte[SurfaceGrain.SIDE * SurfaceGrain.SIDE];
        for (int y = 0; y < SurfaceGrain.SIDE; y++) for (int x = 0; x < SurfaceGrain.SIDE; x++) tiled[y * SurfaceGrain.SIDE + x] = old[(y & 255) * 256 + (x & 255)];
        check(repeat(tiled, false) > .95, "The old scans repeated (the check sees it): " + repeat(tiled, false));
    }
    static byte[] scan(BrushTexture t) {
        try { return Assets.bytes("textures/" + (t == BrushTexture.LIBRARY ? "nypl-old" : t.label.replace(' ', '-')) + ".bin"); }
        catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    static float firstFibre(PaperGrain g) {
        for (int y = 0; y < g.fineSide; y++) for (int x = 0; x < g.fineSide; x++) { float a = g.fibreAngle(x, y); if (!Float.isNaN(a)) return a; }
        return Float.NaN;
    }
    static double[] meanSpread(byte[] b) {
        double sum = 0, sq = 0;
        for (byte v : b) { int t = v & 255; sum += t; sq += t * t; }
        double m = sum / b.length;
        return new double[]{m, Math.sqrt(sq / b.length - m * m)};
    }
    /**
     * How alike a 256 px square of the grain is to the grain shifted by any step of 32 px up to a page's width (1920) in
     * both directions, leaving out shifts under 128 px both ways (a texture's own grain): the most, a correlation, 1 for
     * a repeat. {@code along}: a grain of lines across and down (laid, a weave), its shifts straight along them left out too (a line is like itself further on).
     */
    static double repeat(byte[] g, boolean along) {
        int side = SurfaceGrain.SIDE, box = 256;
        double most = 0;
        double[] a = window(g, side, 0, 0, box);
        for (int dy = 0; dy <= 1920; dy += 32) for (int dx = 0; dx <= 1920; dx += 32) {
            if (dx < 128 && dy < 128 || along && (dx == 0 || dy == 0)) continue;
            most = Math.max(most, correlate(a, window(g, side, dx, dy, box)));
        }
        return most;
    }
    static double[] window(byte[] g, int side, int x0, int y0, int box) {
        double[] w = new double[box * box];
        double sum = 0;
        for (int y = 0; y < box; y++) for (int x = 0; x < box; x++) { w[y * box + x] = g[((y0 + y) & side - 1) * side + ((x0 + x) & side - 1)] & 255; sum += w[y * box + x]; }
        double mean = sum / w.length, sq = 0;
        for (int i = 0; i < w.length; i++) { w[i] -= mean; sq += w[i] * w[i]; }
        double norm = Math.sqrt(sq);
        for (int i = 0; i < w.length; i++) w[i] /= norm;
        return w;
    }
    static double correlate(double[] a, double[] b) { double s = 0; for (int i = 0; i < a.length; i++) s += a[i] * b[i]; return s; }
    /** How much the grain changes across its tile's edge against the most it changes between any two neighbouring columns (or rows) inside it: past 1, a seam. */
    static double seam(byte[] g) {
        int side = SurfaceGrain.SIDE;
        // Each neighbouring pair of columns' (and of rows') change, down (across) the whole grain; the edge's against the most of any inside.
        double[] cols = new double[side], rows = new double[side];
        for (int a = 0; a < side; a++) {
            int b = (a + 1) & side - 1;
            for (int k = 0; k < side; k++) {
                cols[a] += Math.abs((g[k * side + a] & 255) - (g[k * side + b] & 255));
                rows[a] += Math.abs((g[a * side + k] & 255) - (g[b * side + k] & 255));
            }
        }
        double colMost = 0, rowMost = 0;
        for (int a = 0; a < side - 1; a++) { colMost = Math.max(colMost, cols[a]); rowMost = Math.max(rowMost, rows[a]); }
        return Math.max(cols[side - 1] / colMost, rows[side - 1] / rowMost);
    }

    /** Brush Lab's samples are kept apart by the surface they show (WildSample's surface). */
    static void samples() {
        BrushTexture was = WildSample.surface;
        try {
            ModularBrush b = pencil(BrushTexture.PLAIN);
            WildSample.surface = BrushTexture.PLAIN;
            int[] smooth = WildSample.modular(b, .8, WildSample.Shape.TRAY, 20f, 200, 80, true);
            WildSample.surface = BrushTexture.CANVAS;
            int[] canvas = WildSample.modular(b, .8, WildSample.Shape.TRAY, 20f, 200, 80, true);
            check(!Arrays.equals(smooth, canvas), "A sample on another surface is drawn on it, not the one kept");
        } finally { WildSample.surface = was; }
    }

    /** What making each surface costs, and a pencil stroke on Smooth and on each made one. */
    static void timing() {
        for (BrushTexture t : BrushTexture.values()) {
            if (t == BrushTexture.PLAIN) continue;
            long t0 = System.nanoTime();
            SurfaceGrain.of(t, t == BrushTexture.COLD_PRESS || t == BrushTexture.LAID || t == BrushTexture.CANVAS || t == BrushTexture.KRAFT || t == BrushTexture.UNRYU ? null : scan(t));
            System.out.println("make " + t.label + ": " + (System.nanoTime() - t0) / 1_000_000 + " ms");
        }
        for (BrushTexture t : new BrushTexture[]{BrushTexture.PLAIN, BrushTexture.COLD_PRESS, BrushTexture.WATERCOLOUR, BrushTexture.UNRYU}) {
            t.grain();
            for (int warm = 0; warm < 3; warm++) drawn(pencil(t), null);
            long t0 = System.nanoTime();
            for (int k = 0; k < 10; k++) drawn(pencil(t), null);
            System.out.println("pencil stroke on " + t.label + ": " + round((System.nanoTime() - t0) / 1e7) + " ms");
        }
    }
}
