package io.github.mpdairy.monopaint;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Pictures of the nibs (explore-nibs: Ruling Pen, Twig, Fan, Brayer), each its preset at its Medium, beside Round, drawn by the app's engine: a
 * pressure ramp, a tilt ramp, handwriting, a slow-then-fast stroke and a stroke over ink already there. One sheet a
 * nib into the folder given (default docs/archive/pictures/explore/nibs). scripts/nib_pictures.sh runs it.
 */
public final class NibPictures {
    static final int CELL_W = 440, CELL_H = 260, LABEL = 150, GAP = 10;
    static final String[] COLUMNS = {"pressure .1 to 1", "tilt 0 to 65 deg", "handwriting", "slow, then fast", "over ink"};
    /** A writing hand's tilt: 40° from upright, leaning down and right on the page. */
    static final double LEAN = 40, AZ = 45;

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File dir = new File(args.length > 0 ? args[0] : "docs/archive/pictures/explore/nibs");
        dir.mkdirs();
        ModularBrush round = StippleChecks.mk(PartChoice.ROUND, PartChoice.PRESS_BOTH);
        // Each nib's preset, at its Medium.
        Object[][] nibs = {{"ruling", "Ruling pen"}, {"twig", "Twig pen"}, {"fan", "Fan brush"}, {"brayer", "Brayer"}};
        for (Object[] n : nibs) {
            if (args.length > 1 && !args[1].equals(n[0])) continue;
            ModularBrush b = BristleSwarmChecks.preset((String) n[1]);
            double size = ModularSizes.nib(b.choice(BrushPart.TIP));
            List<BufferedImage[]> rows = new ArrayList<>();
            rows.add(row(b, size));
            rows.add(row(round, 12));
            File out = new File(dir, "nib-" + n[0] + ".png");
            sheet(new String[]{n[1] + " " + Math.round(size) + " px", "Round 12 px, Press Both"}, rows, out);
            System.out.println("Wrote " + out);
        }
    }

    static BufferedImage[] row(ModularBrush b, double size) {
        return new BufferedImage[]{draw(b, size, pressureRamp()), draw(b, size, tiltRamp()), draw(b, size, handwriting()), draw(b, size, slowFast()), overInk(b, size)};
    }

    /** Samples {x, y, travel, time, tiltX, tiltY}. */
    static final class Path {
        final List<List<double[]>> strokes = new ArrayList<>();
        long t = 1000;
        List<double[]> cur;
        Path begin() { cur = new ArrayList<>(); strokes.add(cur); t += 300; return this; }
        Path to(double x, double y, double p, double lean, double az, int ms) {
            double tn = Math.tan(Math.toRadians(lean)), a = Math.toRadians(az);
            cur.add(new double[]{x, y, p, t, Math.toDegrees(Math.atan(tn * Math.cos(a))), Math.toDegrees(Math.atan(tn * Math.sin(a)))});
            t += ms; return this;
        }
        Path to(double x, double y, double p) { return to(x, y, p, LEAN, AZ, 6); }
    }
    /** Five S-strokes across, pressure .1, .3, .55, .8, 1. */
    static Path pressureRamp() {
        Path P = new Path();
        double[] ps = {.1, .3, .55, .8, 1};
        for (int i = 0; i < 5; i++) {
            P.begin();
            for (int k = 0; k <= 80; k++) { double u = k / 80.0; P.to(30 + i * 82 + 22 * Math.sin(u * 2 * Math.PI), 25 + u * 210, ps[i]); }
        }
        return P;
    }
    /** At pressure .6, leaning 0, 15, 30, 45 and 65 degrees: each a vertical stroke, a horizontal and a diagonal. */
    static Path tiltRamp() {
        Path P = new Path();
        double[] leans = {0, 15, 30, 45, 65};
        for (int i = 0; i < 5; i++) {
            double x0 = 20 + i * 84;
            P.begin(); for (int k = 0; k <= 40; k++) P.to(x0 + 30, 20 + k * 2.2, .6, leans[i], AZ, 6);
            P.begin(); for (int k = 0; k <= 30; k++) P.to(x0 + k * 2, 125, .6, leans[i], AZ, 6);
            P.begin(); for (int k = 0; k <= 30; k++) P.to(x0 + k * 2, 240 - k * 2, .6, leans[i], AZ, 6);
        }
        return P;
    }
    /** Two lines of looped script ("lleelle" loops, a prolate cycloid), the pressure rising on downstrokes as a hand's does. */
    static Path handwriting() {
        Path P = new Path();
        for (int line = 0; line < 2; line++) {
            P.begin();
            double y0 = 105 + line * 120;
            int loops = 11;
            for (int k = 0; k <= loops * 48; k++) {
                double u = k / 48.0, a = u * 2 * Math.PI, tall = ((int) u % 3 == 1) ? 1.9 : 1;
                double x = 30 + u * 34 - 15 * Math.sin(a), y = y0 - 26 * tall * (.5 - .5 * Math.cos(a));
                double down = Math.cos(a) < 0 && Math.sin(a) < 0 ? .4 : 0;
                P.to(x, y, .3 + down + .1 * Math.sin(u * 1.7), LEAN, AZ, 5);
            }
        }
        return P;
    }
    /** One wave, slow (0.12 px/ms) for its first half, then fast (1.6 px/ms). */
    static Path slowFast() {
        Path P = new Path().begin();
        for (double x = 25; x <= 220; x += 1) P.to(x, 130 - 70 * Math.sin((x - 25) / 195 * Math.PI), .6, LEAN, AZ, 8);
        for (double x = 222; x <= 420; x += 9) P.to(x, 130 + 70 * Math.sin((x - 222) / 198 * Math.PI), .6, LEAN, AZ, 6);
        return P;
    }
    /** Round ink bars and a word-like scribble, then the brush rolled across them twice. */
    static BufferedImage overInk(ModularBrush b, double size) {
        ToneDocument d = InkWashLayers.blank(CELL_W, CELL_H);
        Path marks = new Path();
        for (int i = 0; i < 6; i++) { marks.begin(); for (int k = 0; k <= 30; k++) marks.to(50 + i * 22, 40 + k * 6, 1); }
        marks.begin(); for (int k = 0; k <= 200; k++) { double u = k / 200.0; marks.to(220 + u * 180, 70 + 25 * Math.sin(u * 30), 1); }
        lay(d, StippleChecks.mk(PartChoice.ROUND, PartChoice.STEADY), 6, marks);
        Path over = new Path();
        over.begin(); for (double x = 20; x <= 420; x += 3) over.to(x, 150, .8);
        over.begin(); for (double x = 20; x <= 420; x += 3) over.to(x, 215, .4);
        lay(d, b, size, over);
        return HandHatchPictures.image(d);
    }

    static BufferedImage draw(ModularBrush b, double size, Path path) {
        ToneDocument d = InkWashLayers.blank(CELL_W, CELL_H);
        lay(d, b, size, path);
        return HandHatchPictures.image(d);
    }
    static void lay(ToneDocument d, ModularBrush b, double size, Path path) {
        for (List<double[]> s : path.strokes) {
            ModularStroke st = ModularStroke.start(d, b, size, 1, null);
            for (int i = 0; i < s.size(); i++) {
                double[] q = s.get(i);
                st.sample((float) q[0], (float) q[1], ToolSettings.pressure((float) q[2]), (float) q[4], (float) q[5], (long) q[3]);
                if (i % 4 == 3) st.present();
            }
            st.present(); st.finish();
        }
    }

    static void sheet(String[] names, List<BufferedImage[]> rows, File out) throws Exception {
        int cols = rows.get(0).length, w = LABEL + cols * (CELL_W + GAP), head = 24, h = head + rows.size() * (CELL_H + GAP);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.setColor(Color.BLACK);
        for (int c = 0; c < cols; c++) g.drawString(COLUMNS[c], LABEL + c * (CELL_W + GAP) + 4, 17);
        for (int r = 0; r < rows.size(); r++) {
            int y = head + r * (CELL_H + GAP);
            g.setColor(Color.BLACK);
            g.drawString(names[r], 6, y + 30);
            for (int c = 0; c < cols; c++) {
                g.drawImage(rows.get(r)[c], LABEL + c * (CELL_W + GAP), y, null);
                g.setColor(new Color(200, 200, 200)); g.drawRect(LABEL + c * (CELL_W + GAP), y, CELL_W - 1, CELL_H - 1);
            }
        }
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
    }
}
