package io.github.mpdairy.monopaint;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * The app's half of the web parity harness (scripts/web_parity.sh; scripts/web_parity.cjs draws the web half and
 * compares): every {@link ToolPresets} preset at its Medium size and sample darkness, then the ten {@link ToolSlots#FIRST}
 * at theirs, each drawing the scene's strokes over its marks (laid on the brush's own layer and kind) as the drawing
 * screen does (PresetPictures.stroke: wet paint stays wet, each stroke then left to settle, every tick). Writes
 * {@code brushes.tsv} (index, name, the brush's code, size, darkness) and one 8-bit PGM a brush of the page's
 * composite. {@code java WebParity scene.txt outDir [name regex]}. For looking and comparing, not a check.
 */
public final class WebParity {
    /** The scene (web_parity.cjs writes it): its size, rects laid first, strokes of {x, y, travel, ms, tiltX, tiltY}, and when to snap mid-flow. */
    static int W, H;
    static final List<int[]> marks = new ArrayList<>();
    static final List<double[][]> strokes = new ArrayList<>();
    /** Each stroke's share of the brush's darkness. */
    static final List<Double> greys = new ArrayList<>();
    /**
     * The plane cases: several brushes and erasers on one page (the page's layers and kind planes at work), each drawn
     * to {@code case-NN.pgm}. An op is a brush ({@code code}, size, darkness) or, {@code code} null, an eraser of
     * {@code kinds} ({@link EraseKind#KINDS}) at {@code size} px its broadest; its path as a stroke's.
     */
    static final class Op { String code; double size, dark; int kinds; double[][] path; int[] lay; int[] greys; }
    static final List<String> caseNames = new ArrayList<>();
    static final List<List<Op>> cases = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        read(new File(args[0]));
        File out = new File(args[1]);
        java.util.regex.Pattern only = args.length > 2 ? java.util.regex.Pattern.compile(args[2], java.util.regex.Pattern.CASE_INSENSITIVE) : null;
        out.mkdirs();
        List<Object[]> all = new ArrayList<>();
        for (ToolPresets.Preset p : ToolPresets.all()) {
            ModularBrush b = p.brush().parts;
            all.add(new Object[]{p.name, b, ModularSizes.size(b, InkWashSizes.Preset.MEDIUM), ModularSizes.sampleDarkness(b)});
        }
        for (int slot = 0; slot < ToolSlots.FIRST.length; slot++)
            all.add(new Object[]{"first: " + ToolSlots.FIRST[slot][1], ToolSlots.first(slot).parts, ToolSlots.firstPx(slot), ToolSlots.firstGrey(slot)});
        for (int k = 0; k < cases.size(); k++) {
            if (only != null && !only.matcher("case: " + caseNames.get(k)).find()) continue;
            pgm(play(cases.get(k)), new File(out, String.format("case-%02d.pgm", k)));
        }
        try (PrintWriter tsv = new PrintWriter(new File(out, "brushes.tsv"), "UTF-8")) {
            for (int i = 0; i < all.size(); i++) {
                Object[] a = all.get(i);
                String name = (String) a[0];
                ModularBrush b = (ModularBrush) a[1];
                double size = (Double) a[2], dark = (Double) a[3];
                tsv.println(i + "\t" + name + "\t" + ModularCode.encode(b) + "\t" + size + "\t" + dark);
                if (only != null && !only.matcher(name).find()) continue;
                pgm(scene(b, size, dark), new File(out, String.format("%03d.pgm", i)));
            }
        }
    }

    static void read(File f) throws IOException {
        try (BufferedReader in = new BufferedReader(new FileReader(f))) {
            List<double[]> path = null;
            List<Op> ops = null;
            Op op = null;
            for (String line; (line = in.readLine()) != null; ) {
                String[] t = line.trim().split("\\s+");
                if (ops != null) {
                    switch (t[0]) {
                        case "brush": op = new Op(); op.size = Double.parseDouble(t[1]); op.dark = Double.parseDouble(t[2]); op.code = t[3]; path = new ArrayList<>(); continue;
                        case "erase": op = new Op(); op.kinds = Integer.parseInt(t[1]); op.size = Double.parseDouble(t[2]); path = new ArrayList<>(); continue;
                        case "lay": op = new Op(); op.lay = new int[]{Integer.parseInt(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3]), Integer.parseInt(t[4])};
                            String[] g = in.readLine().trim().split("\\s+"); op.greys = new int[g.length];
                            for (int i = 0; i < g.length; i++) op.greys[i] = Integer.parseInt(g[i]);
                            ops.add(op); continue;
                        case "end": op.path = path.toArray(new double[0][]); ops.add(op); path = null; continue;
                        case "endcase": cases.add(ops); ops = null; continue;
                        default: if (path != null && t.length >= 6) path.add(new double[]{Double.parseDouble(t[0]), Double.parseDouble(t[1]), Double.parseDouble(t[2]), Double.parseDouble(t[3]), Double.parseDouble(t[4]), Double.parseDouble(t[5])});
                            continue;
                    }
                }
                switch (t[0]) {
                    case "case": caseNames.add(line.trim().substring(5)); ops = new ArrayList<>(); break;
                    case "size": W = Integer.parseInt(t[1]); H = Integer.parseInt(t[2]); break;
                    case "mark": marks.add(new int[]{Integer.parseInt(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3]), Integer.parseInt(t[4]), Integer.parseInt(t[5])}); break;
                    case "stroke": path = new ArrayList<>(); greys.add(t.length > 1 ? Double.parseDouble(t[1]) : 1); break;
                    case "end": strokes.add(path.toArray(new double[0][])); path = null; break;
                    default: if (path != null && t.length >= 6) path.add(new double[]{Double.parseDouble(t[0]), Double.parseDouble(t[1]), Double.parseDouble(t[2]), Double.parseDouble(t[3]), Double.parseDouble(t[4]), Double.parseDouble(t[5])});
                }
            }
        }
    }
    /** The marks' tone at x, y: the last rect holding it, else paper. */
    static int mark(int x, int y) {
        int tone = 255;
        for (int[] m : marks) if (x >= m[0] && x <= m[2] && y >= m[1] && y <= m[3]) tone = m[4];
        return tone;
    }

    static ToneDocument scene(ModularBrush b, double size, double dark) {
        ToneDocument d = InkWashLayers.blank(W, H);
        ResolvedBrush r = ResolvedBrush.of(b, size, dark);
        // The marks on the brush's own layer and kind (CarrySmudgeChecks.lay), as the web engine's one page holds them.
        d.selectLayer(ModularStroke.layerOf(d, b));
        d.begin(1, r.kind);
        int[] row = new int[W];
        for (int y = 0; y < H; y++) { for (int x = 0; x < W; x++) { int t = mark(x, y); row[x] = t >= 255 ? -1 : t; } d.setTones(0, y, W, 1, row); }
        d.finish();
        WatercolorSession session = new WatercolorSession();
        // PARITY_STROKES=n: only the first n (for finding where two drawings part).
        String first = System.getenv("PARITY_STROKES");
        int count = first == null ? strokes.size() : Math.min(strokes.size(), Integer.parseInt(first));
        for (int n = 0; n < count; n++) {
            double[][] path = strokes.get(n);
            boolean wet = r.wet != null;
            if (wet) session.settleBefore(d, b); else session.settleNow(d);
            DrawingStroke s = QuickStroke.start(d, b, size, dark * greys.get(n), session);
            // Three samples an event (CarrySmudgeChecks.feed), as the web half adds them.
            for (int i = 0; i < path.length; i++) {
                s.sample((float) path[i][0], (float) path[i][1], ToolSettings.pressure((float) path[i][2]), (float) path[i][4], (float) path[i][5], (long) path[i][3]);
                if (i % 3 == 2) s.present();
            }
            s.present();
            s.finish();
            // Left to settle before the next, as a hand pauses between strokes (bounded: a bath floats its time out).
            for (int k = 0; k < 100_000 && session.tickDrying(d); k++) { }
        }
        session.settleNow(d);
        // Fading and Lemon Juice run to their end by the page's clock (Lemon Juice as Quick lays it, Fading to nothing).
        long far = FadingInk.now() + 24L * 3600 * 1000;
        for (int k = 0; k < 1000 && d.fading(); k++) d.fade(far, null);
        return d;
    }
    /** A plane case: its ops in turn on a blank page, each left to settle as {@link #scene}'s strokes; an eraser untimed (its pressure's size). */
    static ToneDocument play(List<Op> ops) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession session = new WatercolorSession();
        for (Op op : ops) {
            if (op.lay != null) { session.settleNow(d); lay(d, op.lay[0], op.lay[1], op.lay[2], op.lay[3], op.greys.clone()); continue; }
            if (op.code == null) {
                session.settleNow(d);
                EraseBothStroke e = EraseBothStroke.pen(d, (float) op.size, op.kinds);
                for (double[] p : op.path) e.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), Float.NaN, Float.NaN);
                e.finish();
                continue;
            }
            ModularBrush b = ModularCode.decode(op.code);
            if (ResolvedBrush.of(b, op.size, op.dark).wet != null) session.settleBefore(d, b); else session.settleNow(d);
            DrawingStroke s = QuickStroke.start(d, b, op.size, op.dark, session);
            for (int i = 0; i < op.path.length; i++) {
                double[] p = op.path[i];
                s.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), (float) p[4], (float) p[5], (long) p[3]);
                if (i % 3 == 2) s.present();
            }
            s.present();
            s.finish();
            for (int k = 0; k < 100_000 && session.tickDrying(d); k++) { }
        }
        session.settleNow(d);
        long far = FadingInk.now() + 24L * 3600 * 1000;
        for (int k = 0; k < 1000 && d.fading(); k++) d.fade(far, null);
        return d;
    }
    /**
     * A change received from another page laid as the Manta lays it (LiveProbe.laySlice, Android-only: onLayer, then
     * {@link PictureImport#lay}): each grey its layer's look scaled by how much darker the page must get there.
     */
    static void lay(ToneDocument d, int x, int y, int w, int h, int[] greys) { lay(d, x, y, w, h, greys, 0); }
    /** As above, a mark of {@code kinds} ({@link PictureImport#lay}'s): on its kind's plane. */
    static void lay(ToneDocument d, int x, int y, int w, int h, int[] greys, int kinds) {
        // The stack looked up once, as PictureImport.lay's lighter: a pixel at a time slowed a guest's catch-up (planes);
        // every layer Full, as every screen shares the page (a layer's step is each screen's own).
        int[] shown = PictureImport.liveTones(d, x, y, w, h);
        for (int i = 0; i < greys.length; i++) if (greys[i] >= 0) greys[i] = PictureImport.inked(greys[i], shown[i], d.layerTone(x + i % w, y + i / w, kinds));
        PictureImport.lay(d, x, y, w, h, greys, false, false, false, kinds);
    }
    static void pgm(ToneDocument d, File f) throws IOException {
        try (OutputStream o = new FileOutputStream(f)) {
            o.write(("P5\n" + d.width + " " + d.height + "\n255\n").getBytes("US-ASCII"));
            byte[] row = new byte[d.width];
            for (int y = 0; y < d.height; y++) { for (int x = 0; x < d.width; x++) row[x] = (byte) d.compositeTone(x, y); o.write(row); }
        }
    }
}
