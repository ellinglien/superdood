package io.github.mpdairy.monopaint;

/**
 * Compiled against the document classes from before text boxes (scripts/test_inkwash.sh): a page with a text box and a
 * box made ink (TextBoxChecks write) opens there, its TEXT section skipped, with the ink it holds ({@code tone} at
 * {@code x, y}) and nothing where the box lay.
 */
public final class TextOlderReaderCheck {
    public static void main(String[] args) throws Exception {
        ToneDocument page;
        try (java.io.InputStream in = new java.io.FileInputStream(args[0])) { page = DocumentCodec.read(in); }
        int x = Integer.parseInt(args[1]), y = Integer.parseInt(args[2]), tone = Integer.parseInt(args[3]);
        if (page.compositeTone(x, y) != tone || page.compositeTone(60, 330) != 255) throw new AssertionError("The older reader opens the page with text boxes as drawn");
        System.out.println("PASS: a page with text boxes opens in the version before them, its TEXT section skipped, the ink made from one as drawn");
    }
}
