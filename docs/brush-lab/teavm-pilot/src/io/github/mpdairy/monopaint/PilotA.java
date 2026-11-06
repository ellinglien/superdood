package io.github.mpdairy.monopaint;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.teavm.jso.JSExport;

/**
 * Pilot A: the app's code reading and "This brush" words, as Brush Studio would call them. In JS (TeaVM) through the
 * exports; on the JVM, main echoes the same for each code on stdin, so the two can be compared line for line.
 */
public final class PilotA {
    /** {@code code} read and written again by the app, "null" when it isn't a code; then "newer" when a newer version's. */
    @JSExport public static String read(String code) {
        ModularBrush b = ModularCode.decode(code);
        return (b == null ? "null" : ModularCode.encode(b)) + (ModularCode.newer(code) ? " newer" : "");
    }
    /** The "This brush" words without a Basic tool's own personality (the web's brushWords): what it is and how it behaves. */
    @JSExport public static String words(String code) {
        ModularBrush b = ModularCode.decode(code);
        return b == null ? "" : BrushVoice.about(ModularWords.plainWords(b), "");
    }
    /** docs/brush-code.json as the app writes it (CodeSpecChecks.spec): its tables, and every test code read. */
    @JSExport public static String spec() { return CodeSpecChecks.spec(); }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("spec")) { System.out.print(spec()); return; }
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        StringBuilder out = new StringBuilder();
        for (String line; (line = in.readLine()) != null; ) out.append(read(line)).append('\t').append(words(line)).append('\n');
        System.out.print(out);
    }
}
