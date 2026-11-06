package io.github.mpdairy.monopaint;

import java.util.Random;

/**
 * Not a check: a long eraser stroke timed on the desktop (the owner, 2026-11-05: "a bit sluggish with a long erase
 * stroke"). A Manta page (1785 × 2387), three pairs each with HB pencil and a wash, Vector Ink on the middle one; an
 * eraser of 3000 samples down it, across or scrubbing, each sample a frame as {@code DrawingPad.renderDirty} does it (what it
 * newly erased and the page's changed area, its preview {@link EraseBothStroke#erasedTones}; zoomed, the lines asked
 * too, {@link VectorInk#shown}): ms per sample over the first and last 500, and its lift. {@code [runs] [samples] [samples a frame]}.
 */
public final class EraseBench {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        ToneDocument d = InkWashLayers.blank(1785, 2387);
        d.surface = BrushTexture.COLD_PRESS;
        WatercolorSession s = new WatercolorSession();
        Random r = new Random(5);
        Brush hb = SharpChecks.preset("HB pencil"), wash = SharpChecks.preset("Soft wash"), vector = VectorInkChecks.vector();
        for (int pair = 0; pair < 3; pair++) {
            if (pair > 0) InkWashLayers.add(d, true);
            for (int k = 0; k < 12; k++) SharpChecks.draw(d, s, wash, 60, .4, SharpChecks.hand(r, 200, 100 + r.nextInt(1585), 100 + r.nextInt(2187), 10, false), 1);
            for (int k = 0; k < 60; k++) SharpChecks.draw(d, s, hb, 6, 1, SharpChecks.hand(r, 150, 100 + r.nextInt(1585), 100 + r.nextInt(2187), 10, false), 1);
            if (pair == 1) for (int k = 0; k < 60; k++) VectorInkChecks.draw(d, s, vector, 6, 1, SharpChecks.hand(r, 150, 100 + r.nextInt(1585), 100 + r.nextInt(2187), 10, false));
        }
        // Each run from the file's bytes: a copy from a snapshot would share the lines the lift changes.
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        DocumentCodec.write(bytes, d.layerSnapshot());
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 3000, part = Math.min(500, n / 2), frame = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        int[] out = new int[0];
        for (int run = 0; run < 4 * runs; run++) {
            boolean zoomed = run % 2 == 1, scrub = run % 4 >= 2;
            ToneDocument e = DocumentCodec.read(new java.io.ByteArrayInputStream(bytes.toByteArray()));
            e.surface = d.surface;
            DrawingStroke er = StrokeStart.start(e, null, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 40, 1), new WatercolorSession(), 0);
            EraseBothStroke eraser = (EraseBothStroke) er;
            int lined = VectorInk.linedMask(e);
            long[] at = new long[n + 1];
            long area = 0, t = 5000, preview = 0, most = 0;
            int hash = 0;
            e.clearDirty();
            at[0] = System.nanoTime();
            for (int i = 0; i < n; i++) {
                // Across: the page's width and back every 60 samples, a little lower each pass; scrubbing: slower, wider loops down it.
                float x, y;
                if (scrub) { x = (float) (900 + 800 * Math.sin(i * .02)); y = (float) (100 + 2200 * i / (double) n + 30 * Math.sin(i * .37)); }
                else { double u = i % 120 < 60 ? (i % 60) / 59.0 : 1 - (i % 60) / 59.0; x = (float) (60 + 1665 * u); y = (float) (60 + 2267 * i / (double) n); }
                er.sample(x, y, 1, Float.NaN, Float.NaN, t += 8);
                // A frame every {@code frame} samples, as the pad renders once an event's samples are in.
                int[] also = i % frame == frame - 1 || i == n - 1 ? eraser.takeErased() : null, b = also == null ? null : e.dirty();
                if (also != null) b = b == null ? also : new int[]{Math.min(b[0], also[0]), Math.min(b[1], also[1]), Math.max(b[2], also[2]), Math.max(b[3], also[3])};
                if (b != null) {
                    int w = b[2] - b[0], h = b[3] - b[1];
                    if (out.length < w * h) out = new int[w * h];
                    long p0 = System.nanoTime();
                    eraser.erasedTones(b[0], b[1], w, h, out);
                    long took = System.nanoTime() - p0;
                    preview += took; most = Math.max(most, took);
                    for (int k = 0; k < w * h; k++) hash = 31 * hash + out[k];
                    area += (long) w * h;
                }
                if (also != null) e.clearDirty();
                // Zoomed: the lines as cut so far, drawn over the frame (ViewportBitmap.sharpen, at 1:1 here).
                if (zoomed && b != null) { VectorLines l = VectorInk.shown(e, lined, er); int wh = (b[2] - b[0]) * (b[3] - b[1]); if (l.meets(b[0], b[1], b[2], b[3])) l.over(1, 0, 0, b[0], b[1], b[2] - b[0], b[3] - b[1], new int[wh], new byte[wh]); }
                at[i + 1] = System.nanoTime();
            }
            long t0 = System.nanoTime();
            er.finish();
            long lift = System.nanoTime() - t0;
            System.out.printf("%s: first %d samples %.2f ms each, last %d %.2f ms each, preview %d px a frame at %.3f µs/px (most %.1f ms), lift %.0f ms, shown %08x%n",
                    (scrub ? "scrubbing, " : "across, ") + (zoomed ? "zoomed" : "100%"), part, (at[part] - at[0]) / 1e6 / part, part, (at[n] - at[n - part]) / 1e6 / part, area * frame / n, preview / 1e3 / Math.max(1, area), most / 1e6, lift / 1e6, hash);
        }
    }
}
