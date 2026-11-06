package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Compiled against an older version (scripts/test_inkwash.sh), using only what that version has: {@code <page> <hash>
 * [<version>]} opens a page whose log holds entries that version doesn't know as saved (its pixels' hash), without a
 * replay, and saves it again. From git {@code 56dad77f}, the version before every edit was logged: eraser, smudge and
 * Lasso entries ({@code StrokeLogChecks older-write}, version 2); from {@code 1886cebf}, the version before shapes,
 * pictures and merges were ({@code older-write-more}).
 */
public final class OlderReplayCheck {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        String version = args.length > 2 ? args[2] : "the version before every edit was logged (56dad77f)";
        ToneDocument d = DocumentCodec.read(new ByteArrayInputStream(Files.readAllBytes(Paths.get(args[0]))));
        int[] page = new int[d.width * d.height];
        for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) page[y * d.width + x] = d.compositeTone(x, y);
        if (Arrays.hashCode(page) != Integer.parseInt(args[1])) throw new AssertionError("FAIL: " + version + " shows the page otherwise");
        if (!d.replay().isEmpty()) throw new AssertionError("FAIL: " + version + " reads a log it doesn't know");
        ByteArrayOutputStream again = new ByteArrayOutputStream();
        DocumentCodec.write(again, d.layerSnapshot());
        DocumentCodec.read(new ByteArrayInputStream(again.toByteArray()));
        System.out.println("PASS: " + version + " opens a page whose log it doesn't know as saved, without a replay, and saves it again");
    }
}
