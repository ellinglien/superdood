package io.github.mpdairy.monopaint;

import java.io.File;
import java.util.Random;

/**
 * Hold to Snap's pictures ({@link HoldSnap}): for each of several brushes, six rough shapes as drawn, beside the same
 * strokes snapped the way the pad does it (each rough stroke finished, undone, and drawn again along its clean shape
 * with its own pressure, tilt and time). Composites at full grey. scripts/snap_pictures.sh.
 */
public class HoldSnapPictures {
    static final int W = 960, H = 640;
    static final String[] KINDS = {"line", "oval", "box", "triangle", "arc", "polyline"};
    public static void main(String[] a) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(a.length > 0 ? a[0] : "/tmp/snap-pictures");
        out.mkdirs();
        String[][] brushes = {{"Swell brush", "14", ".8"}, {"Steady line", "6", ".8"}, {"Compressed", "26", ".8"}, {"2B pencil", "10", ".8"}, {"Soft wash", "40", ".45"}, {"Wet blooms", "40", ".5"}, {"Wet gouache", "34", ".6"}};
        for (String[] b : brushes) {
            Brush brush = HoldSnapChecks.current(b[0]);
            double size = Double.parseDouble(b[1]), dark = Double.parseDouble(b[2]);
            ToneDocument rough = InkWashLayers.blank(W, H), snapped = InkWashLayers.blank(W, H);
            WatercolorSession rs = new WatercolorSession(), ss = new WatercolorSession();
            Random r = new Random(21);
            long redraw = 0;
            for (int k = 0; k < KINDS.length; k++) {
                double[][] rows = place(HoldSnapChecks.shape(KINDS[k], r), k);
                HoldSnapChecks.draw(rough, rs, brush, size, dark, rows);
                HoldSnap h = new HoldSnap(1);
                for (double[] p : rows) h.sample(p[0], p[1], p[2], p[3], p[4], (long) p[5]);
                double[][] clean = h.snap();
                HoldSnapChecks.draw(snapped, ss, brush, size, dark, rows);
                if (clean == null) { System.out.println(b[0] + ": " + KINDS[k] + " did not snap"); continue; }
                long t0 = System.nanoTime();
                ss.takeBack(snapped);
                HoldSnapChecks.draw(snapped, ss, brush, size, dark, clean);
                redraw += System.nanoTime() - t0;
            }
            HoldSnapChecks.settle(rs, rough); HoldSnapChecks.settle(ss, snapped);
            String name = b[0].toLowerCase().replace(' ', '-');
            WetSmudgePictures.save(new File(out, "snap-" + name + ".png"), WetSmudgePictures.grab(rough), WetSmudgePictures.grab(snapped));
            System.out.println(String.format("%s at %s px: Undo and the clean strokes took %.0f ms for six shapes on the desktop", b[0], b[1], redraw / 1e6));
        }
        System.out.println("wrote " + out);
    }
    /** Rows of a shape (centred about 500, 400) fitted into cell {@code k} of a 3 by 2 grid. */
    static double[][] place(double[][] rows, int k) {
        double x0 = 1e9, x1 = -1e9, y0 = 1e9, y1 = -1e9;
        for (double[] p : rows) { x0 = Math.min(x0, p[0]); x1 = Math.max(x1, p[0]); y0 = Math.min(y0, p[1]); y1 = Math.max(y1, p[1]); }
        double cw = W / 3., ch = H / 2., s = Math.min((cw - 70) / (x1 - x0), (ch - 70) / (y1 - y0));
        s = Math.min(s, 1.4);
        double cx = cw * (k % 3 + .5), cy = ch * (k / 3 + .5);
        double[][] o = new double[rows.length][];
        for (int i = 0; i < rows.length; i++) { o[i] = rows[i].clone(); o[i][0] = cx + (rows[i][0] - (x0 + x1) / 2) * s; o[i][1] = cy + (rows[i][1] - (y0 + y1) / 2) * s; }
        return o;
    }
}
