package io.github.mpdairy.monopaint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * Pilot B on the JVM, the reference: {@code java [-Dquick=true] [-DoneCore=true] PilotBJvm scene.txt outDir [warm]} writes each brush's
 * page (PilotB's loop, the one the compiled JS runs) as outDir/NNN.raw and prints its stroke and settle ms, after a
 * warm-up pass when asked. -Dquick=true draws quick strokes quick, as the app does; -DoneCore=true without Cores' helpers, as JS runs. Run from the repository's root.
 */
public final class PilotBJvm {
    public static void main(String[] args) throws Exception {
        for (File f : new File("app/src/main/assets/textures").listFiles()) PilotB.asset("textures/" + f.getName(), Files.readAllBytes(f.toPath()));
        PilotB.scene(new String(Files.readAllBytes(new File(args[0]).toPath()), "UTF-8"));
        if (Boolean.getBoolean("quick")) QuickStroke.benchAlways = null;
        Cores.shared = !Boolean.getBoolean("oneCore");
        File out = new File(args[1]); out.mkdirs();
        for (int pass = args.length > 2 ? 0 : 1; pass < 2; pass++)
            for (int i = 0; i < PilotB.brushes(); i++) {
                byte[] page = PilotB.render(i);
                if (pass == 0) continue;
                try (OutputStream o = new FileOutputStream(new File(out, String.format("%03d.raw", i)))) { o.write(page); }
                System.out.printf("%d\t%.2f\t%.2f\t%s%n", i, PilotB.strokeMs, PilotB.settleMs, PilotB.label(i));
            }
    }
}
