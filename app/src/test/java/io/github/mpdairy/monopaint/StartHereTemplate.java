package io.github.mpdairy.monopaint;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A "Start Here" notebook for the owner to draw their own guide in (scripts/start_here.sh): the front cover and ten pages
 * at a new Manta notebook's size, Blank, white. Each page is two layers (a stack, {@link PageStacks}): the bottom holds the
 * guide, light grey typed words, and the top is blank, where a turn lands and the owner draws. The guide goes when the
 * owner picks layer 1 (Page Settings' Layers) and uses Clear page; covers have no layers, so the cover's guide is on the
 * cover itself, on its label's blank top.
 * Written with the app's own codec, read back with its reader, each page rendered to a PNG preview.
 */
public final class StartHereTemplate {
    /** The guide's grey and its type sizes, px: the title's caps and the notes'. */
    static final int GREY = 200, TITLE = 60, NOTE = 32, MARGIN = 130;
    static final String NAME = "Start Here";
    static final String COVER = "START HERE";
    static final String[][] PAGES = {
            {"1  Hello", "This is a sketchbook: draw badly.", "It saves itself. No Save button.", "Try drawing anywhere on this page."},
            {"2  Your tools", "Tap a slot to pick it.", "Hold a slot to swap in another brush.", "Below: the smudge tool and the eraser."},
            {"3  Size & grey", "The triangle box: right half sets size, left half sets grey.", "The dot shows what you're holding.", "The dropper picks a grey off the page."},
            {"4  Fingers", "Two-finger tap: undo. Three-finger tap: redo.", "Two-finger double tap: Zen (hide everything).", "One finger erases (Menu → Finger Action to change)."},
            {"5  Wet stuff", "Washes stay wet a few seconds. The drop shows it; tap it to dry.", "Smudge tool: Blend and Water. Press harder to do more.", "Hold the eraser to erase only pen, paint or dry media."},
            {"6  Pages", "The strip at the top: tap a page to go there.", "+ adds a page. Hold a page for its settings."},
            {"7  Your notebook", "Menu → Notebook Settings: template, page colour, rules, self-portrait prompt, cover.", "Notebook Explorer goes to the shelf."},
            {"8  Mischief", "Confetti button: Mirrors, Magnetic Poetry, Replay Page.", "Shapes button.", "Odd brushes: Lemon juice, Vanishing ink, Spirograph; Mold ink in Library."},
            {"9  Brushes", "Hold a slot → brush picker → Brush Lab to make your own.", "Brush Studio on the web: superdood.org/studio"},
            {"10  Now go", "Hold this notebook on the shelf to delete it, or keep it.", "Scribble here."}};

    /**
     * A new notebook's page on the Manta upright: the pad, what the column and the page bar and the foot leave
     * ({@link DrawLayout}), at the Manta's 1920 px to 1024 dp, rounded as {@code Ui.dp}.
     */
    static int[] mantaPage() {
        DrawLayout d = new DrawLayout(1024, 1365);
        float density = 1920f / 1024;
        return new int[]{1920 - Math.round(d.side * density), 2560 - Math.round(d.pageBar() * density) - Math.round(d.footHeight() * density)};
    }

    /** args: the folder for the notebook, the app's font folder (res/font), the folder for the previews. */
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]), fonts = new File(args[1]), previews = new File(args[2]);
        out.mkdirs(); previews.mkdirs();
        Font bold = Font.createFont(Font.TRUETYPE_FONT, new File(fonts, "ia_writer_duo_bold.ttf"));
        Font regular = Font.createFont(Font.TRUETYPE_FONT, new File(fonts, "ia_writer_duo_regular.ttf"));
        int[] size = mantaPage();
        int w = size[0], h = size[1];
        String name = java.time.LocalDate.now() + "_" + NAME;
        File file = new File(out, name + DrawingFiles.EXTENSION);

        DrawingBook book = new DrawingBook(InkWashLayers.blank(w, h));
        book.name(name);
        book.setDesign(CoverDesign.CLASSIC);
        book.select(DrawingBook.FRONT);
        // On the label's blank top, above its writing lines (the name goes on the first): white there, so the grey shows.
        NotebookCovers.Label label = NotebookCovers.label(name, w, h);
        int in = label.inset + label.rule + label.gap + label.hairline;
        guide(book.current(), mask(w, h, sized(bold, TITLE), sized(regular, NOTE), new String[]{COVER}, label.x + 3 * in, label.y + 2 * in));
        book.select(0);
        for (int p = 0; p < PAGES.length; p++) {
            if (p > 0) book.addPage();
            guide(book.current(), mask(w, h, sized(bold, TITLE), sized(regular, NOTE), PAGES[p], MARGIN, MARGIN));
            check(book.addLayer(true), "A blank layer over page " + (p + 1));
        }
        book.select(book.stacks().last(0));
        try (OutputStream o = new BufferedOutputStream(new FileOutputStream(file))) { BookCodec.write(o, book.snapshot()); }

        // Read back with the app's reader: ten pages of two layers, each guide marked, each top blank, the cover drawn on.
        DrawingBook read = BookCodec.open(file);
        read.name(name);
        PageStacks stacks = read.stacks();
        check(read.width == w && read.height == h, "The page size kept");
        check(stacks.count() == PAGES.length && read.count() == 2 * PAGES.length, "Ten pages of two layers: " + stacks.count() + " pages, " + read.count() + " layers");
        check(read.coverDrawn(DrawingBook.FRONT), "The front cover's guide");
        for (int s = 0; s < stacks.count(); s++) {
            check(stacks.size(s) == 2, "Page " + (s + 1) + " has two layers");
            List<ToneDocument.Snapshot> layers = new ArrayList<>();
            for (int p = stacks.first(s); p <= stacks.last(s); p++) { read.select(p); layers.add(read.current().layerSnapshot()); }
            read.select(stacks.first(s));
            check(!read.current().unmarked(), "Page " + (s + 1) + "'s guide layer has its words");
            read.select(stacks.last(s));
            check(read.current().unmarked(), "Page " + (s + 1) + "'s top layer is blank to draw on");
            png(new ToneDocument(PageStacks.flatten(layers, null)), new File(previews, String.format("%02d.png", s + 1)));
        }
        read.select(DrawingBook.FRONT);
        png(read.current(), new File(previews, "00-cover.png"));
        System.out.println("Wrote " + file + " (" + w + " × " + h + ", " + file.length() / 1024 + " KB): the front cover and "
                + stacks.count() + " pages of 2 layers, read back; previews in " + previews);
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /** {@code font} at the size whose capitals stand {@code caps} px tall. */
    static Font sized(Font font, int caps) {
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY).createGraphics();
        double tall = font.deriveFont(100f).createGlyphVector(g.getFontRenderContext(), "H").getVisualBounds().getHeight();
        g.dispose();
        return font.deriveFont((float) (100 * caps / tall));
    }

    /** The words' coverage, 0 to 255 a pixel: the title then each note, wrapped, from {@code left}, {@code top} to the right margin. */
    static byte[] mask(int w, int h, Font title, Font note, String[] words, int left, int top) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(java.awt.Color.WHITE);
        int y = top, room = w - left - MARGIN;
        for (int i = 0; i < words.length; i++) {
            g.setFont(i == 0 ? title : note);
            FontMetrics m = g.getFontMetrics();
            if (i == 1) y += m.getHeight() / 2;
            for (String line : wrapped(words[i], m, room)) { y += m.getAscent(); g.drawString(line, left, y); y += m.getDescent() + m.getLeading(); }
            y += m.getHeight() / 2;
        }
        g.dispose();
        return ((java.awt.image.DataBufferByte) image.getRaster().getDataBuffer()).getData();
    }

    /** {@code text} broken at spaces into lines no wider than {@code room}, a following line indented. */
    static List<String> wrapped(String text, FontMetrics m, int room) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : text.split(" ")) {
            String longer = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && m.stringWidth(longer) > room) { lines.add(line); line = "  " + word; }
            else line = longer;
        }
        lines.add(line);
        return lines;
    }

    /** Lays {@code coverage} on {@code page} in {@link #GREY}, as pigment over the paper, one gesture. */
    static void guide(ToneDocument page, byte[] coverage) {
        page.begin();
        for (int y = 0; y < page.height; y++)
            for (int x = 0; x < page.width; x++) {
                int a = coverage[y * page.width + x] & 255;
                if (a > 0) page.setTone(x, y, 255 - Math.round((255 - GREY) * a / 255f));
            }
        page.finish();
    }

    /** {@code page} as it shows, in greys. */
    static void png(ToneDocument page, File to) throws IOException {
        BufferedImage image = new BufferedImage(page.width, page.height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] px = ((java.awt.image.DataBufferByte) image.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < page.height; y++) for (int x = 0; x < page.width; x++) px[y * page.width + x] = (byte) page.compositeTone(x, y);
        javax.imageio.ImageIO.write(image, "png", to);
    }
}
