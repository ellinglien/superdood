package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.jso.JSByRef;
import org.teavm.jso.JSExport;

/**
 * Pilot B: the app's stroke engine, as the parity harness drives it (WebParity: its brushes, its scene, its strokes
 * through QuickStroke and WatercolorSession, wet paint left to settle), compiled to JS. The scene loop is WebParity.scene's
 * with a clock around the strokes and around the settling; parity against WebParity's own PGMs shows it is the same.
 * The JVM runs it too (main), for the desktop's times.
 */
public final class PilotB {
    private static final Map<String, byte[]> ASSETS = new HashMap<>();
    private static final List<Object[]> BRUSHES = new ArrayList<>();
    static double strokeMs, settleMs;

    /** An asset's bytes, by its path under app/src/main/assets ("textures/felt.bin"), before the first render. */
    @JSExport public static void asset(String path, @JSByRef byte[] bytes) { ASSETS.put(path, bytes.clone()); }
    /** The harness's scene, web_parity.cjs's scene.txt as text. */
    @JSExport public static void scene(String text) {
        // Every stroke drawn live (ModularStroke), never quick: a QuickStroke renders on its own thread, waiting on the pen's
        // (wait/notify), which one JS thread can't. QuickStrokeChecks.sameAsLive: the page ends the same either way.
        QuickStroke.benchAlways = Boolean.FALSE;
        Assets.install(path -> { byte[] b = ASSETS.get(path); if (b == null) throw new IOException("no asset " + path); return new ByteArrayInputStream(b); });
        WebParity.marks.clear(); WebParity.strokes.clear(); WebParity.greys.clear();
        List<double[]> path = null;
        for (String line : text.split("\n")) {
            String[] t = line.trim().split("\\s+");
            switch (t[0]) {
                case "size": WebParity.W = Integer.parseInt(t[1]); WebParity.H = Integer.parseInt(t[2]); break;
                case "mark": WebParity.marks.add(new int[]{Integer.parseInt(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3]), Integer.parseInt(t[4]), Integer.parseInt(t[5])}); break;
                case "stroke": path = new ArrayList<>(); WebParity.greys.add(t.length > 1 ? Double.parseDouble(t[1]) : 1); break;
                case "end": WebParity.strokes.add(path.toArray(new double[0][])); path = null; break;
                default: if (path != null && t.length >= 6) path.add(new double[]{Double.parseDouble(t[0]), Double.parseDouble(t[1]), Double.parseDouble(t[2]), Double.parseDouble(t[3]), Double.parseDouble(t[4]), Double.parseDouble(t[5])});
            }
        }
        // WebParity.main's brushes, in its order: every preset at Medium, then the ten first-launch brushes.
        BRUSHES.clear();
        for (ToolPresets.Preset p : ToolPresets.all()) {
            ModularBrush b = p.brush().parts;
            BRUSHES.add(new Object[]{p.name, b, ModularSizes.size(b, InkWashSizes.Preset.MEDIUM), ModularSizes.sampleDarkness(b)});
        }
        for (int slot = 0; slot < ToolSlots.FIRST.length; slot++)
            BRUSHES.add(new Object[]{"first: " + ToolSlots.FIRST[slot][1], ToolSlots.first(slot).parts, ToolSlots.firstPx(slot), ToolSlots.firstGrey(slot)});
    }
    @JSExport public static int brushes() { return BRUSHES.size(); }
    /** Brush {@code i}'s name (not "name": WasmGC exports statics on a function, whose name is read-only). */
    @JSExport public static String label(int i) { return (String) BRUSHES.get(i)[0]; }
    /** Brush {@code i}'s code, size and darkness, as WebParity's brushes.tsv lists them (the JS copy draws from these). */
    @JSExport public static String code(int i) { return ModularCode.encode((ModularBrush) BRUSHES.get(i)[1]); }
    @JSExport public static double size(int i) { return (Double) BRUSHES.get(i)[2]; }
    @JSExport public static double dark(int i) { return (Double) BRUSHES.get(i)[3]; }
    /** Brush {@code i}'s page after the scene: the composite's tones, a byte a pixel, row by row. */
    @JSExport @JSByRef public static byte[] render(int i) {
        Object[] a = BRUSHES.get(i);
        ToneDocument d = scene((ModularBrush) a[1], (Double) a[2], (Double) a[3]);
        byte[] page = new byte[d.width * d.height];
        for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) page[y * d.width + x] = (byte) d.compositeTone(x, y);
        return page;
    }
    /** The last render's time drawing strokes, and settling wet paint after them, in ms. */
    @JSExport public static double strokeMs() { return strokeMs; }
    @JSExport public static double settleMs() { return settleMs; }

    /** WebParity.scene, timed: strokes (samples, present, finish) apart from the settling between and after them. */
    static ToneDocument scene(ModularBrush b, double size, double dark) {
        int W = WebParity.W, H = WebParity.H;
        ToneDocument d = InkWashLayers.blank(W, H);
        ResolvedBrush r = ResolvedBrush.of(b, size, dark);
        d.selectLayer(ModularStroke.layerOf(d, b));
        d.begin(1, r.kind);
        int[] row = new int[W];
        for (int y = 0; y < H; y++) { for (int x = 0; x < W; x++) { int t = WebParity.mark(x, y); row[x] = t >= 255 ? -1 : t; } d.setTones(0, y, W, 1, row); }
        d.finish();
        WatercolorSession session = new WatercolorSession();
        strokeMs = settleMs = 0;
        for (int n = 0; n < WebParity.strokes.size(); n++) {
            double[][] path = WebParity.strokes.get(n);
            long t0 = System.nanoTime();
            if (r.wet != null) session.settleBefore(d, b); else session.settleNow(d);
            long t1 = System.nanoTime();
            DrawingStroke s = QuickStroke.start(d, b, size, dark * WebParity.greys.get(n), session);
            for (int i = 0; i < path.length; i++) {
                s.sample((float) path[i][0], (float) path[i][1], ToolSettings.pressure((float) path[i][2]), (float) path[i][4], (float) path[i][5], (long) path[i][3]);
                if (i % 3 == 2) s.present();
            }
            s.present();
            s.finish();
            long t2 = System.nanoTime();
            for (int k = 0; k < 100_000 && session.tickDrying(d); k++) { }
            long t3 = System.nanoTime();
            strokeMs += (t2 - t1) / 1e6; settleMs += (t1 - t0 + t3 - t2) / 1e6;
        }
        long t4 = System.nanoTime();
        session.settleNow(d);
        long far = FadingInk.now() + 24L * 3600 * 1000;
        for (int k = 0; k < 1000 && d.fading(); k++) d.fade(far, null);
        settleMs += (System.nanoTime() - t4) / 1e6;
        return d;
    }
}
