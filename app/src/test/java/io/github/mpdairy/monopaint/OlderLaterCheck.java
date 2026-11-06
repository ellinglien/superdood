package io.github.mpdairy.monopaint;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Compiled against the version before Later (scripts/test_inkwash.sh): reads the codes {@code LaterChecks older} lists
 * and checks it reads each as docs/BRUSH-CODE.md says: an old-form code (a Later it holds in Drying) in full, written
 * back byte for byte; a new one's six parts and nib, Later left out, saying "Newer version, some parts differ".
 */
public final class OlderLaterCheck {
    public static void main(String[] args) throws Exception {
        int n = 0;
        String version = args.length > 1 ? args[1] : "the version before Later";
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            String[] t = line.split("\t");
            ModularBrush b = ModularCode.decode(t[0]);
            boolean newer = ModularCode.newer(t[0]);
            if (b == null || newer != t[2].equals("newer") || !ModularCode.encode(b).equals(t[1]))
                throw new AssertionError("FAIL: " + version + " reads " + t[0] + " as " + (b == null ? "nothing" : ModularCode.encode(b)) + (newer ? ", newer" : "") + ", not " + t[1] + " " + t[2]);
            n++;
        }
        System.out.println("PASS: " + version + " reads " + n + " codes: a Later it holds in full, byte for byte; a new one's six parts and nib, saying it is a newer version's");
    }
}
