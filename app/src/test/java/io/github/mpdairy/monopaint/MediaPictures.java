package io.github.mpdairy.monopaint;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * explore-media's additions drawn by the app's engine (scripts/media_pictures.sh), each in its typical use beside the
 * nearest thing the app already had, at the page's own pixels (the Manta's 300 dpi):
 * <ul>
 * <li>white-chalk-toned.png: a toned page (Page Colour), charcoal shading lit with White chalk, then with the White gel pen;
 * white-chalk-board.png: writing on a black page, White chalk then the White gel pen.</li>
 * <li>dip-nib.png: three lines written in one stroke, the Dip nib (runs dry) then the Fountain pen.</li>
 * <li>water-pencil.png: hatching with the Water pencil, then the Water brush at a light touch across it, then the
 * Sketch pencil under the same water (it stays). water-ink.png: the Fountain pen with Dissolves on, before and after
 * the water, then as it comes.</li>
 * <li>dropped/: what was tried and left out (torn paper, oil pastel; the sanded paper and bleach pictures were drawn from
 * code since removed).</li>
 * </ul>
 */
public class MediaPictures {
    static final int W = 420, H = 300;
    public static void main(String[] a) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        File out = new File(a.length > 0 ? a[0] : "docs/archive/pictures/explore/media"), dropped = new File(out, "dropped");
        out.mkdirs(); dropped.mkdirs();
        ModularBrush chalk = preset("White chalk"), gel = preset("White gel pen"), charcoal = preset("Soft charcoal");
        WetSmudgePictures.save(new File(out, "white-chalk-toned.png"), toned(charcoal, chalk), toned(charcoal, gel));
        WetSmudgePictures.save(new File(out, "white-chalk-board.png"), board(chalk), board(gel));
        WetSmudgePictures.save(new File(out, "dip-nib.png"), writing(preset("Dip nib"), 8), writing(preset("Fountain pen"), 8));
        ModularBrush pencil = preset("Water pencil");
        WetSmudgePictures.save(new File(out, "water-pencil.png"), watered(pencil, false), watered(pencil, true), watered(preset("Sketch pencil"), true));
        ModularBrush ink = preset("Fountain pen").copy();
        ink.part(BrushPart.MEDIUM).set(PartDial.SOLUBLE, 1);
        ink.normalize();
        WetSmudgePictures.save(new File(out, "water-ink.png"), watered(ink, false), watered(ink, true), watered(preset("Fountain pen"), true));
        // Dropped: torn paper (Fill in, opaque, a wobbly edge on kraft) over writing; an oil pastel (Wax crayon dragging) over crayon.
        ModularBrush torn = PresetPictures.brush("Gouache", "Torn paper", 1, PartChoice.ROUND);
        PresetPictures.tip(torn).set(PartDial.WOBBLE, 1); PresetPictures.tip(torn).set(PartDial.DRY, .3);
        PresetPictures.medium(torn, PartChoice.COVER);
        ModularBrush.Part fill = new ModularBrush.Part(PartChoice.STAY_IN); fill.set(PartDial.FLOOD, 1);
        torn.set(BrushPart.CARRY, fill);
        PresetPictures.paper(torn, PartChoice.KRAFT, .8);
        WetSmudgePictures.save(new File(dropped, "torn-paper-vs-fill-in.png"), collage(torn.normalize()), collage(preset("Fill in")));
        ModularBrush pastel = preset("Wax crayon").copy();
        ModularBrush.Part drag = new ModularBrush.Part(PartChoice.DRAG); drag.set(PartDial.PICK, .5); drag.set(PartDial.LAY, .6);
        pastel.set(BrushPart.CARRY, drag);
        WetSmudgePictures.save(new File(dropped, "oil-pastel-vs-wax-crayon.png"), crossed(pastel.normalize()), crossed(preset("Wax crayon")));
        System.out.println("wrote " + out);
    }
    static ModularBrush preset(String name) { return FloatChecks.preset(name); }
    static double size(ModularBrush b) { return ModularSizes.size(b, InkWashSizes.Preset.MEDIUM); }

    /** A mid-grey page: a round form shaded in charcoal, its lit side picked out in white with {@code light}. */
    static int[][] toned(ModularBrush dark, ModularBrush light) {
        ToneDocument d = InkWashLayers.blank(W, H);
        d.placePaper(150);
        WatercolorSession s = new WatercolorSession();
        // The shadow side: hatching down the right of a circle; its outline.
        List<double[]> shade = new ArrayList<>();
        for (int i = 0; i <= 900; i++) { double u = i / 900.0, y = 70 + 160 * Math.abs(((u * 16) % 2) - 1), x = 200 + 70 * u + 30 * Math.sin(u * 3); shade.add(new double[]{x, y, .35 + .4 * u, i * 3}); }
        PresetPictures.stroke(d, s, dark, size(dark), 1, shade.toArray(new double[0][]));
        PresetPictures.stroke(d, s, dark, size(dark) * .5, 1, PresetPictures.ring(210, 150, 95, 95));
        // The light: a few strokes on the upper left, firmer at their middle.
        for (int k = 0; k < 3; k++) PresetPictures.stroke(d, s, light, size(light), 1, PresetPictures.sCurve(130 + k * 8, 200 + k * 6, 95 + k * 16, 12));
        s.settleNow(d);
        return WetSmudgePictures.grab(d);
    }
    /** A black page: loops written across it in white. */
    static int[][] board(ModularBrush b) {
        ToneDocument d = InkWashLayers.blank(W, H);
        d.placePaper(40);
        WatercolorSession s = new WatercolorSession();
        for (int k = 0; k < 3; k++) PresetPictures.stroke(d, s, b, size(b), 1, PresetPictures.scribble(30, W - 30, 60 + k * 90, 32, .6));
        s.settleNow(d);
        return WetSmudgePictures.grab(d);
    }
    /** Three rows of loops written as one stroke, as a hand writes on without lifting. */
    static int[][] writing(ModularBrush b, double size) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        List<double[]> all = new ArrayList<>();
        for (int k = 0; k < 3; k++) for (double[] p : PresetPictures.scribble(20, W - 20, 60 + k * 90, 22, .6)) all.add(new double[]{k % 2 == 0 ? p[0] : W - p[0], p[1], p[2], all.size() * 3});
        PresetPictures.stroke(d, s, b, size, 1, all.toArray(new double[0][]));
        s.settleNow(d);
        return WetSmudgePictures.grab(d);
    }
    /** Dense hatching with {@code b}; then, if {@code water}, the Water brush across its middle at a light touch. */
    static int[][] watered(ModularBrush b, boolean water) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        List<double[]> z = new ArrayList<>();
        for (int i = 0; i <= 3000; i++) { double u = i / 3000.0; z.add(new double[]{30 + 360 * u, 40 + 220 * Math.abs(((u * 70) % 2) - 1), .8, i * 2}); }
        PresetPictures.stroke(d, s, b, size(b), 1, z.toArray(new double[0][]));
        s.settleNow(d);
        if (water) {
            double[][] wp = PresetPictures.sCurve(40, W - 40, H / 2.0, 50);
            for (double[] q : wp) q[2] = .25;
            PresetPictures.stroke(d, s, preset("Water"), 50, 1, wp);
            s.settleNow(d);
        }
        return WetSmudgePictures.grab(d);
    }
    /** Pen writing, then a loop with {@code b} at a mid grey over it. */
    static int[][] collage(ModularBrush b) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        ModularBrush pen = PresetPictures.brush("Fineliner", "pen", 1, PartChoice.ROUND);
        for (int k = 0; k < 4; k++) PresetPictures.stroke(d, s, pen, 4, 1, PresetPictures.scribble(20, W - 20, 50 + k * 65, 12, .6));
        List<double[]> loop = new ArrayList<>();
        for (int i = 0; i <= 200; i++) { double t = 2 * Math.PI * i / 200; loop.add(new double[]{W / 2.0 + 130 * Math.cos(t) + 8 * Math.sin(5 * t), H / 2.0 + 80 * Math.sin(t), .8, i * 5}); }
        PresetPictures.stroke(d, s, b, 12, .45, loop.toArray(new double[0][]));
        s.settleNow(d);
        return WetSmudgePictures.grab(d);
    }
    /** Black crayon bands down, then {@code b} in a light grey across them. */
    static int[][] crossed(ModularBrush b) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        for (int k = 0; k < 5; k++) PresetPictures.stroke(d, s, preset("Wax crayon"), size(b), 1, FloatChecks.line(40 + k * 30, 40, 40 + k * 30, 260, .8));
        for (int k = 0; k < 5; k++) PresetPictures.stroke(d, s, b, size(b), .35, FloatChecks.line(20, 60 + k * 40, W - 20, 60 + k * 40, .8));
        s.settleNow(d);
        return WetSmudgePictures.grab(d);
    }
}
