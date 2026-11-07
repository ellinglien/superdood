package io.github.mpdairy.monopaint;

import java.util.Random;

/**
 * Not a check: the eraser on a busy Vector Ink page, timed on the desktop. A Manta-sized page (1920 × 2560) with 300
 * Vector ink strokes, about 50,000 points, then an eraser stroke of 400 samples (40 px) zigzagging across it: the time
 * its samples take (what the pen waits on; zoomed in, the lines asked for every fourth sample as the view does) and its
 * lift. {@code [runs]}.
 */
public final class VectorInkBench {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        Brush vector = null;
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals("Vector ink")) vector = p.brush();
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
        ToneDocument.Snapshot page = d.layerSnapshot();
        long bestSamples = Long.MAX_VALUE, bestLift = Long.MAX_VALUE, bestZoomed = Long.MAX_VALUE;
        for (int run = 0; run < 2 * runs; run++) {
            // Odd runs as zoomed in: the view asks for the lines every fourth sample (a frame), as DrawingPad.sharpLines.
            boolean zoomed = run % 2 == 1;
            ToneDocument e = new ToneDocument(page);
            DrawingStroke er = StrokeStart.stroke(e, null, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 40, 0), new WatercolorSession(), 0);
            long t0 = System.nanoTime();
            for (int i = 0; i < 400; i++) {
                double u = i / 399.0;
                er.sample((float) (100 + 1720 * (i % 40 < 20 ? (i % 20) / 20.0 : 1 - (i % 20) / 20.0)), (float) (100 + 2360 * u), 1, 0, 0);
                if (zoomed && i % 4 == 3) VectorInk.shown(e, 1 << VectorInk.layer(e), er);
            }
            long t1 = System.nanoTime();
            er.finish();
            long t2 = System.nanoTime();
            if (zoomed) bestZoomed = Math.min(bestZoomed, t1 - t0);
            else { bestSamples = Math.min(bestSamples, t1 - t0); bestLift = Math.min(bestLift, t2 - t1); }
        }
        System.out.printf("Vector ink eraser, %d strokes, %d points: 400 samples %.0f ms (zoomed, the lines asked a frame at a time: %.0f ms), lift %.0f ms (best of %d)%n",
                300, points, bestSamples / 1e6, bestZoomed / 1e6, bestLift / 1e6, runs);
    }
}
