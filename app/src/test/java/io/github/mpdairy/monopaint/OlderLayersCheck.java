package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Compiled against the version before Vector Ink followed the layer in use (scripts/test_inkwash.sh, from git {@code
 * 1f0d16e8}), using only what that version has. {@code write <page>}: that version draws a page of four pairs, pen marks
 * under and its own pair on top for Vector Ink, saves it and prints how it shows ({@code VectorInkChecks old-layers}
 * opens it). {@code read <page> <hash>}: it opens a page this version wrote with Vector Ink beside a pen's marks on one
 * layer ({@code VectorInkChecks mixed-write}), showing it as saved, and saves it again.
 */
public final class OlderLayersCheck {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args[0].equals("write")) {
            ToneDocument d = InkWashLayers.blank(200, 120);
            WatercolorSession s = new WatercolorSession();
            draw(d, s, "Steady line", 20, 30);
            InkWashLayers.add(d, true); InkWashLayers.add(d, true);
            draw(d, s, "Steady line", 40, 50);
            // Vector Ink on a marked top pair: that version puts a pair of its own on top, the fourth.
            draw(d, s, "Vector ink", 60, 70);
            draw(d, s, "Vector ink", 80, 90);
            if (InkWashLayers.pairs(d) != 4 || d.lines(7) == null) throw new AssertionError("FAIL: the older version's page: " + InkWashLayers.pairs(d) + " pairs");
            try (FileOutputStream out = new FileOutputStream(args[1])) { DocumentCodec.write(out, d.layerSnapshot()); }
            System.out.println(shown(d));
            return;
        }
        ToneDocument d = DocumentCodec.read(new ByteArrayInputStream(Files.readAllBytes(Paths.get(args[1]))));
        if (shown(d) != Integer.parseInt(args[2])) throw new AssertionError("FAIL: the version before Vector Ink followed the layer shows a page of Vector Ink beside a pen's marks as saved");
        ByteArrayOutputStream again = new ByteArrayOutputStream();
        DocumentCodec.write(again, d.layerSnapshot());
        DocumentCodec.read(new ByteArrayInputStream(again.toByteArray()));
        System.out.println("PASS: the version before Vector Ink followed the layer (1f0d16e8) opens a page of Vector Ink beside a pen's marks on one layer as saved (the LPEN section passed by), and saves it again");
    }
    /** A stroke of preset {@code name} from (20, y0) to (180, y1). */
    static void draw(ToneDocument d, WatercolorSession s, String name, float y0, float y1) {
        Brush b = null;
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) b = p.brush();
        DrawingStroke st = StrokeStart.stroke(d, b, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, 6, 1), s, 0);
        for (int i = 0; i <= 20; i++) st.sample(20 + 8 * i, y0 + (y1 - y0) * i / 20f, .5f, 0, 0);
        st.finish();
        s.settleNow(d);
    }
    static int shown(ToneDocument d) {
        int[] out = new int[d.width * d.height];
        d.compositeTones(0, 0, d.width, d.height, out, 0, d.width, 0);
        return Arrays.hashCode(out);
    }
}
