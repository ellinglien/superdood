package io.github.mpdairy.monopaint;

import java.util.Random;

/**
 * Not a check: large Vector Ink, timed on the desktop (the owner, 2026-11-07: "vector ink is unusable past a certain
 * size"). A Manta-sized page with 15 lines at size {@code [px]} (default 120), then a new one of 600 samples 2 px apart:
 * its samples and a frame's present every fourth (what the pen waits on), the zoomed view's redraw (2×) round the newest piece every fourth sample, its
 * lift, and 30 drying ticks' redraws of a 400 px square over the lines. {@code [px] [runs]}.
 */
public final class VectorLargeBench {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        double px = args.length > 0 ? Double.parseDouble(args[0]) : 120;
        int runs = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        Brush vector = null;
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals("Vector ink")) vector = p.brush();
        Random r = new Random(9);
        ToneDocument d = InkWashLayers.blank(1920, 2560);
        WatercolorSession s = new WatercolorSession();
        for (int k = 0; k < 15; k++) line(d, vector, px, s, r);
        ToneDocument.Snapshot page = d.layerSnapshot();
        double scale = 2;
        long bestSamples = Long.MAX_VALUE, bestZoomed = Long.MAX_VALUE, bestLift = Long.MAX_VALUE, bestDrying = Long.MAX_VALUE;
        for (int run = 0; run < runs; run++) {
            for (boolean zoomed : new boolean[]{false, true}) {
                ToneDocument e = new ToneDocument(page);
                DrawingStroke st = StrokeStart.stroke(e, vector, new ToolSwatches.Look(InkWashSizes.Preset.LARGE, px, 1), new WatercolorSession(), 0);
                Random w = new Random(3);
                double x = 400, y = 600, a = .5;
                int layer = VectorInk.layer(e);
                long t0 = System.nanoTime();
                for (int i = 0; i < 600; i++) {
                    a += (w.nextDouble() - .5) * .2; x += 2 * Math.cos(a); y += 2 * Math.sin(a);
                    st.sample((float) x, (float) y, .7f, 0, 0);
                    // A frame every fourth sample, as the pad's batches: the page brought up to date, the zoomed view redrawn.
                    if (i % 4 == 3) st.present();
                    if (zoomed && i % 4 == 3) {
                        // The view's redraw round the newest piece, as the zoomed pad's dirty area.
                        int half = (int) Math.ceil(px * scale), cx = (int) (x * scale), cy = (int) (y * scale);
                        redraw(e, 1 << layer, st, scale, cx - half, cy - half, 2 * half, 2 * half);
                    }
                }
                long t1 = System.nanoTime();
                st.finish();
                long t2 = System.nanoTime();
                if (zoomed) {
                    bestZoomed = Math.min(bestZoomed, t1 - t0);
                    // Drying ticks after the lift: the view's redraw of a 400 px square over the lines, the lines unchanged.
                    long t3 = System.nanoTime();
                    for (int k = 0; k < 30; k++) redraw(e, 1 << layer, null, scale, 700, 1100, 400, 400);
                    bestDrying = Math.min(bestDrying, System.nanoTime() - t3);
                } else { bestSamples = Math.min(bestSamples, t1 - t0); bestLift = Math.min(bestLift, t2 - t1); }
            }
        }
        System.out.printf("Vector ink at %.0f px, 15 lines: 600 samples %.0f ms (zoomed 2x, redrawn a frame at a time: %.0f ms), lift %.0f ms, 30 drying redraws %.0f ms (best of %d)%n",
                px, bestSamples / 1e6, bestZoomed / 1e6, bestLift / 1e6, bestDrying / 1e6, runs);
    }
    static void line(ToneDocument d, Brush vector, double px, WatercolorSession s, Random r) {
        DrawingStroke st = StrokeStart.stroke(d, vector, new ToolSwatches.Look(InkWashSizes.Preset.LARGE, px, 1), s, 0);
        double x = 200 + r.nextDouble() * 1500, y = 200 + r.nextDouble() * 2100, a = r.nextDouble() * 6.3;
        for (int i = 0; i < 300; i++) { a += (r.nextDouble() - .5) * .2; x += 2 * Math.cos(a); y += 2 * Math.sin(a); st.sample((float) x, (float) y, .7f, 0, 0); }
        st.finish();
    }
    static void redraw(ToneDocument d, int layers, DrawingStroke stroke, double scale, int left, int top, int w, int h) {
        VectorInk.Stacked lines = VectorInk.stacked(d, layers, 0, null, n -> VectorInk.shownOf(d, n, stroke), null);
        if (lines != null) lines.over(scale, 0, 0, left, top, w, h, new int[w * h], new byte[w * h]);
    }
}
