package io.github.mpdairy.monopaint;

import java.io.File;

/**
 * Surface (branch explore-surface), drawn by the app's engine: every surface across a page's width with a pencil pass
 * and a wash; the same wash on Smooth, Cold press and Cotton; and each surface's old 256 px scan against the new whole
 * one, under the same pencil. scripts/surface_pictures.sh.
 */
public class SurfacePictures {
    static final int PAGE = 1920;

    public static void main(String[] a) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(a.length > 0 ? a[0] : "/tmp/surface-pictures");
        out.mkdirs();
        BrushTexture[] all = BrushTexture.values();
        // Every surface across a page's width: a pencil pass over the top, a wash along the bottom.
        int h = 150;
        String[] rows = new String[all.length];
        int[][][][] panels = new int[all.length][1][][];
        for (int k = 0; k < all.length; k++) {
            rows[k] = PartChoice.of(BrushPart.PAPER, k).label + "\n" + (k == 0 ? "as before" : made(all[k]) ? "made" : "scan, laid\nas patches");
            ToneDocument d = InkWashLayers.blank(PAGE, h);
            d.surface = all[k];
            WatercolorSession s = new WatercolorSession();
            pencil(d, s, null, 0, 80);
            KnifePictures.stroke(d, s, HeldPaint.ANY, WetPaintChecks.wash("Soft wash"), 56, .45, KnifePictures.path(20, 118, PAGE - 20, 116, .7, 0, 0, 0, 0));
            WetPaintChecks.settle(s, d); s.settleNow(d);
            panels[k][0] = KnifePictures.grab(d);
        }
        KnifePictures.sheet(new File(out, "surfaces-page.png"), "Every surface across a page's width (1920 px): a pencil at Texture .7, then a Soft wash", new String[]{""}, rows, panels, PAGE, h);

        // The same wash on Smooth, Cold press and Cotton.
        BrushTexture[] three = {BrushTexture.PLAIN, BrushTexture.COLD_PRESS, BrushTexture.WATERCOLOUR};
        int[][][][] washes = new int[1][3][][];
        for (int k = 0; k < 3; k++) {
            ToneDocument d = WetPaintChecks.page();
            d.surface = three[k];
            WatercolorSession s = new WatercolorSession();
            KnifePictures.stroke(d, s, HeldPaint.ANY, WetPaintChecks.wash("Soft wash"), 70, .6, KnifePictures.path(60, 120, 420, 120, .7, 0, 0, 0, 0));
            KnifePictures.stroke(d, s, HeldPaint.ANY, WetPaintChecks.wash("Wet blooms"), 60, .6, KnifePictures.path(60, 230, 420, 220, .7, 0, 0, 0, 0));
            WetPaintChecks.settle(s, d); s.settleNow(d);
            washes[0][k] = KnifePictures.grab(d);
        }
        KnifePictures.sheet(new File(out, "wash-smooth-cold-cotton.png"), "The same washes (Soft wash, Wet blooms) on Smooth, Cold press, Cotton: harder edges and pooling on Smooth, softer bleed on Cotton",
                new String[]{"Smooth (.35)", "Cold press (.9)", "Cotton (1.25)"}, new String[]{"Soft wash\nWet blooms"}, washes, WetPaintChecks.W, WetPaintChecks.H);

        // Old against new: each surface's 256 px scan as the brushes read it before, then the whole new surface, the same pencil over both.
        int ow = 900, oh = 150;
        String[] names = new String[all.length - 1];
        int[][][][] pairs = new int[all.length - 1][2][][];
        for (int k = 1; k < all.length; k++) {
            names[k - 1] = PartChoice.of(BrushPart.PAPER, k).label;
            for (int n = 0; n < 2; n++) {
                ToneDocument d = InkWashLayers.blank(ow, oh);
                d.surface = all[k];
                pencil(d, null, n == 0 ? new StrokePaper(PaperGrain.scan(SurfaceChecks.scan(all[k]))) : null, 0, oh - 10);
                pairs[k - 1][n] = KnifePictures.grab(d);
            }
        }
        KnifePictures.sheet(new File(out, "old-vs-new.png"), "Old 256 px scans (left) against the new surfaces (right), the same pencil at Texture .7",
                new String[]{"old: the scan, tiled", "new"}, names, pairs, ow, oh);
    }
    static boolean made(BrushTexture t) { return t == BrushTexture.COLD_PRESS || t == BrushTexture.LAID || t == BrushTexture.CANVAS || t == BrushTexture.KRAFT || t == BrushTexture.UNRYU; }

    /** A pencil shading pass, zigzag down and across the page from {@code top} to {@code bottom}, on {@code paper} (null: the page's surface). */
    static void pencil(ToneDocument d, WatercolorSession s, StrokePaper paper, int top, int bottom) {
        ModularBrush b = SurfaceChecks.pencil(BrushTexture.PLAIN).onSurface(d.surface);
        double size = 22;
        java.util.List<double[]> path = new java.util.ArrayList<>();
        int t = 0;
        for (double x = 10; x < d.width - 10; x += 9) {
            boolean up = path.size() / 12 % 2 == 1;
            for (int i = 0; i <= 11; i++) {
                double u = i / 11.0, y = up ? bottom - size / 2 - (bottom - top - size) * u : top + size / 2 + (bottom - top - size) * u;
                path.add(new double[]{x + 9 * u, y, .55, t += 4, 0, 0});
            }
        }
        double[][] p = path.toArray(new double[0][]);
        DrawingStroke st;
        if (paper == null) st = QuickStroke.start(d, b, size, .9, s);
        else { ModularStroke.selectLayer(d, b); ModularStroke.prepare(d, ResolvedBrush.of(b, size, .9)); st = new ModularStroke(d, b, size, .9, null, paper); }
        KnifePictures.feed(st, p);
    }
}
