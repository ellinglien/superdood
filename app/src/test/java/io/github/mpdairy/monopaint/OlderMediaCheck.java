package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Compiled against the version before Later's media (scripts/test_inkwash.sh, from git {@code c360ed8e}), using only
 * what that version has: {@code read <page> <hash>} opens a page holding a Feathering mark mid-way ({@code
 * LaterMediaChecks older-write}) without trouble, showing it as saved (its pixels' hash, {@code older-page}), and saves
 * it again ({@code <version> <mark>}, optional: the words for another version and mark, as the version before Later's
 * print, crafts and ageing, 9a57ae48, opening a Cross-Stitch mark); {@code fading <out>} writes a page with a Fading and a Lemon Juice mark half way, which this version must
 * write byte for byte ({@code LaterMediaChecks fading-bytes}).
 */
public final class OlderMediaCheck {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args[0].equals("fading")) { Files.write(Paths.get(args[1]), fading()); return; }
        ToneDocument d = DocumentCodec.read(new ByteArrayInputStream(Files.readAllBytes(Paths.get(args[1]))));
        int[] page = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) page[y * W + x] = d.compositeTone(x, y);
        String version = args.length > 3 ? args[3] : "the version before Later's media (c360ed8e)", mark = args.length > 4 ? args[4] : "a Feathering mark";
        if (Arrays.hashCode(page) != Integer.parseInt(args[2])) throw new AssertionError("FAIL: " + version + " shows a page with " + mark + " as saved");
        if (d.fading()) throw new AssertionError("FAIL: " + version + " leaves " + mark + " as it was, changing nothing by the clock");
        ByteArrayOutputStream again = new ByteArrayOutputStream();
        DocumentCodec.write(again, d.layerSnapshot());
        DocumentCodec.read(new ByteArrayInputStream(again.toByteArray()));
        System.out.println("PASS: " + version + " opens a page with " + mark + " mid-way as saved, the mark staying so, and saves it again");
    }

    static final int W = 400, H = 240;
    static long clock;
    /** LaterMediaChecks.fadingPage, by this version's own engine. */
    static byte[] fading() throws Exception {
        FadingInk.clock = () -> clock;
        // That version's Undo kept a stroke's tiles in the order the cores met them (fixed since, ToneDocument.closeBands):
        // in turn, it writes them in the order this version does on any number of cores.
        Cores.shared = false;
        clock = 40_000_000;
        ToneDocument d = InkWashLayers.blank(W, H);
        draw(d, brush(PartChoice.FADE, PartDial.FADE_IN, 60), line(40, 360, 120));
        draw(d, brush(PartChoice.LEMON, PartDial.DRY_IN, 120), line(40, 360, 60));
        d.fade(clock + 20_000, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocumentCodec.write(out, d.layerSnapshot());
        return out.toByteArray();
    }
    static ModularBrush brush(PartChoice life, PartDial dial, double time) {
        ModularBrush b = null;
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals("Swell brush")) b = p.brush().parts.copy();
        ModularBrush.Part part = new ModularBrush.Part(life);
        part.set(dial, time);
        b.set(BrushPart.LIFE, part);
        return b.normalize();
    }
    /** FadingChecks.line. */
    static double[][] line(double x0, double x1, double y) {
        int n = 60;
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) o[i] = new double[]{x0 + (x1 - x0) * i / n, y + 30 * Math.sin(i / 9.0), .3 + .6 * i / n, 1000 + 8 * i};
        return o;
    }
    /** CarrySmudgeChecks.feed, with FadingChecks.draw's brush size and session. */
    static void draw(ToneDocument d, ModularBrush b, double[][] path) {
        DrawingStroke s = ModularStroke.start(d, b, 14, 1, new WatercolorSession());
        for (int i = 0; i < path.length; i++) {
            double[] p = path[i];
            s.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, (long) p[3]);
            if (i % 3 == 2) s.present();
        }
        s.present();
        s.finish();
    }
}
