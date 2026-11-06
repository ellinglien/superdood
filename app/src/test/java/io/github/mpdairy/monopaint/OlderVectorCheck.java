package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Compiled against the version before Vector Ink (scripts/test_inkwash.sh, from git {@code 04c30e6f}), using only what that
 * version has: {@code <page> <hash>} opens a page with Vector Ink lines ({@code VectorInkChecks older-write}), its LINE
 * section passed by, showing it as this version does ({@code older-page}: the lines as their pixels), and saves it again.
 */
public final class OlderVectorCheck {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        ToneDocument d = DocumentCodec.read(new ByteArrayInputStream(Files.readAllBytes(Paths.get(args[0]))));
        int[] out = new int[d.width * d.height];
        d.compositeTones(0, 0, d.width, d.height, out, 0, d.width, 0);
        if (Arrays.hashCode(out) != Integer.parseInt(args[1])) throw new AssertionError("FAIL: the version before Vector Ink shows a page with its lines as saved");
        ByteArrayOutputStream again = new ByteArrayOutputStream();
        DocumentCodec.write(again, d.layerSnapshot());
        DocumentCodec.read(new ByteArrayInputStream(again.toByteArray()));
        System.out.println("PASS: the version before Vector Ink (04c30e6f) opens a page with Vector Ink lines as saved, its LINE section passed by, and saves it again");
    }
}
