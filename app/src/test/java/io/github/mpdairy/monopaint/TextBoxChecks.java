package io.github.mpdairy.monopaint;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;

/**
 * Text boxes ({@link TextBoxes}) and Make Ink: the layout, the file's TEXT section, Undo of every change, Make Ink's one
 * step and its bytes, the copies showing the letters. {@code write FILE} writes a page for the older reader's check
 * ({@code TextOlderReaderCheck}), printing where its ink is.
 */
public class TextBoxChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /** The checks' letters: the bundled iA Writer Duo through Java's own rasterizer, antialiased, grey coverage. */
    static final class DeskType implements TextBoxes.Type {
        private final Font regular, bold;
        DeskType() {
            try {
                regular = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ia_writer_duo_regular.ttf"));
                bold = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ia_writer_duo_bold.ttf"));
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
        @Override public byte[] letters(TextBoxes.Box b) {
            int[] r = TextBoxes.bounds(b);
            int w = r[2] - r[0], h = r[3] - r[1];
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setColor(java.awt.Color.WHITE);
            g.setFont((b.bold ? bold : regular).deriveFont((float) b.em()));
            for (TextBoxes.Line line : TextBoxes.lines(b)) g.drawString(line.text, line.x - r[0], line.baseline - r[1]);
            g.dispose();
            byte[] a = new byte[w * h];
            img.getRaster().getDataElements(0, 0, w, h, a);
            return a;
        }
    }

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        TextBoxes.type = new DeskType();
        if (args.length > 0 && args[0].equals("write")) { write(new File(args[1])); return; }
        layout();
        file();
        undo();
        makeInk();
        copies();
        System.out.println("PASS: text boxes wrap at their width (a long word broken, centre and right aligned), keep their place, size, bold and alignment in the TEXT section (a later version's skipped, damage refused, a page without boxes written as before), each change one Undo step and Clear page takes them too; Make Ink lays the letters in the brush as one step that brings the box back, the same bytes every time; the copies show the letters");
    }

    static final String JOURNAL = "Sunday. Rain all morning, then the light came back over the harbour.\nTea, two biscuits, one bad drawing.";

    static void layout() {
        TextBoxes.Box b = new TextBoxes.Box("hello world", 10, 20, TextBoxes.least(0) + 10, 0, false, TextBoxes.LEFT);
        // 11 characters at 0.6 em is 6.6 em: on a 4 em box, two lines.
        check(TextBoxes.lines(b).size() == 2 && TextBoxes.lines(b).get(1).text.equals("world"), "two lines: " + TextBoxes.lines(b).size());
        check(TextBoxes.height(b) == 2 * TextBoxes.lineHeight(36), "two lines tall");
        TextBoxes.Box long1 = b.withText("abcdefghijklmnop");
        check(TextBoxes.lines(long1).size() == 3, "a long word broken where it reaches the width");
        TextBoxes.Box c = b.withText("hi").aligned(TextBoxes.CENTRE), r = b.withText("hi").aligned(TextBoxes.RIGHT);
        float w = 1.2f * 36;
        check(Math.abs(TextBoxes.lines(c).get(0).x - (10 + (b.width - w) / 2)) < .01 && Math.abs(TextBoxes.lines(r).get(0).x - (10 + b.width - w)) < .01, "centre and right");
        check(TextBoxes.lines(b.withText("a\n\nb")).size() == 3, "an empty line kept");
        check(TextBoxes.ems("mW") == 1.8f, "wide letters");
        TextBoxes.Box placed = TextBoxes.placed(1900, 2000, 1920, 2560);
        check(placed.x + placed.width <= 1920 && placed.width >= TextBoxes.least(TextBoxes.MEDIUM), "a box placed near the edge lies inside the page");
        check(b.sized(3).width == TextBoxes.least(3), "a bigger size widens a box narrower than its least");
    }

    static ToneDocument page() { return InkWashLayers.blank(600, 400); }
    static TextBoxes.Box box() { return new TextBoxes.Box(JOURNAL, 30, 40, 520, TextBoxes.MEDIUM, false, TextBoxes.LEFT); }
    static byte[] bytes(ToneDocument d) throws Exception { ByteArrayOutputStream o = new ByteArrayOutputStream(); DocumentCodec.write(o, d.layerSnapshot()); return o.toByteArray(); }
    static ToneDocument read(byte[] b) throws Exception { return DocumentCodec.read(new ByteArrayInputStream(b)); }

    static void file() throws Exception {
        ToneDocument d = page();
        byte[] plain = bytes(d);
        TextBoxes.Box a = box(), b = new TextBoxes.Box("Ünïcödé — ok", 100, 300, 300, 3, true, TextBoxes.RIGHT);
        d.setTexts(TextBoxes.NONE.with(a).with(b));
        ToneDocument back = read(bytes(d));
        check(back.texts().equals(d.texts()), "the boxes come back from the file: " + back.texts().list());
        d.setTexts(TextBoxes.NONE);
        check(Arrays.equals(bytes(d), plain), "a page without boxes writes as before, byte for byte");
        // A later version's section: skipped, the page opens without its boxes.
        byte[] later = TextBoxes.NONE.with(a).encode(); later[0] = 2;
        check(TextBoxes.decode(later, 600, 400).isEmpty(), "a later version's section skipped");
        boolean refused = false;
        try { TextBoxes.decode(TextBoxes.NONE.with(a.at(900, 40)).encode(), 600, 400); } catch (java.io.IOException e) { refused = true; }
        check(refused, "a box off the page is damage");
        check(!TextBoxes.validText("") && !TextBoxes.validText("a\u0000b") && TextBoxes.validText("a\nb"), "valid text");
        check(TextBoxes.clean("a\tb\r\nc\n\n ").equals("a b\nc") && TextBoxes.validText(TextBoxes.clean("x\u0007y")), "typed text cleaned");
    }

    static void undo() {
        ToneDocument d = page();
        TextBoxes.Box a = box();
        check(d.setTexts(d.texts().with(a)), "placed");
        check(!d.setTexts(d.texts()), "no step when nothing changed");
        check(!d.unmarked(), "a page with a box isn't blank");
        d.setTexts(d.texts().replaced(0, a.at(50, 60)));
        d.setTexts(d.texts().replaced(0, a.at(50, 60).sized(2).bolded(true).aligned(TextBoxes.CENTRE)));
        d.setTexts(d.texts().replaced(0, a.at(50, 60).sized(2).bolded(true).aligned(TextBoxes.CENTRE).withText("edited")));
        d.undo(); check(d.texts().get(0).text.equals(JOURNAL), "Undo takes back the edit");
        d.undo(); check(!d.texts().get(0).bold, "and the style");
        d.undo(); check(d.texts().get(0).x == 30, "and the move");
        d.undo(); check(d.texts().isEmpty(), "and the placing");
        d.redo(); d.redo(); d.redo(); d.redo(); check(d.texts().get(0).text.equals("edited"), "Redo brings them back");
        d.setTexts(d.texts().replaced(0, d.texts().get(0).withText("")));
        check(d.texts().isEmpty(), "emptied, the box goes");
        d.undo();
        check(d.clearPage() && d.texts().isEmpty(), "Clear page takes the boxes");
        d.undo(); check(d.texts().size() == 1, "and Undo brings them back");
    }

    /** Make Ink as the app does it ({@code DrawingPad.makeInk}): the box off the page and its letters laid by {@code brush}, one Undo step. */
    static boolean makeInk(ToneDocument d, int i, ModularBrush brush, double size, double darkness, WatercolorSession session) {
        TextBoxes.Box b = d.texts().get(i);
        FillRegion.Room letters = TextBoxes.ink(b, d.width, d.height);
        if (letters == null) return false;
        Object mark = d.lastStep();
        d.setTexts(d.texts().without(i));
        ModularStroke s = FillTool.print(d, brush, size, darkness, session, letters);
        s.present();
        s.finish();
        d.linkSince(mark);
        return true;
    }

    static void makeInk() throws Exception {
        ToneDocument d = page();
        d.setTexts(d.texts().with(box()));
        ModularBrush pen = ScreenChecks.ink();
        check(makeInk(d, 0, pen, 4, 1, null), "made ink");
        check(d.texts().isEmpty(), "the box is gone");
        int dark = 0;
        for (int y = 40; y < 200; y++) for (int x = 30; x < 560; x++) if (d.compositeTone(x, y) < 128) dark++;
        check(dark > 2000, "the letters are ink: " + dark);
        d.undo();
        check(d.texts().size() == 1, "one Undo brings the box back");
        int left = 0;
        for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) if (d.compositeTone(x, y) != 255) left++;
        check(left == 0, "and takes the ink: " + left);
        d.redo();
        check(d.texts().isEmpty() && d.compositeTone(0, 0) == 255, "Redo makes it ink again");
        // The same letters, the same brush: the same bytes, a wash too.
        for (ModularBrush b : new ModularBrush[]{pen, ScreenChecks.wash(), ScreenChecks.screened(pen, 1)}) {
            byte[] one = null;
            for (int k = 0; k < 2; k++) {
                ToneDocument p = page();
                p.setTexts(p.texts().with(box()));
                makeInk(p, 0, b, 20, .8, null);
                byte[] now = bytes(p);
                check(one == null || Arrays.equals(one, now), "Make Ink writes the same bytes twice");
                one = now;
            }
        }
    }

    static void copies() {
        ToneDocument d = page();
        d.setTexts(d.texts().with(box()));
        ToneDocument.Snapshot s = d.layerSnapshot();
        PagePicture.Composite c = new PagePicture.Composite(s);
        int[] row = new int[d.width];
        int dark = 0;
        for (int y = 40; y < 120; y++) { c.row(y, row); for (int v : row) if (v < 128) dark++; }
        check(dark > 500, "the copies' composite shows the letters: " + dark);
        int[] argb = d.exportPixels(true);
        TextBoxes.stamp(argb, d.width, d.height, d.texts());
        int stamped = 0;
        for (int v : argb) if ((v & 255) < 128) stamped++;
        check(stamped > 1000, "the PNG copy shows them");
        check(!PagePicture.of(s).white(), "and the page's picture");
    }

    /** A page for the older reader: one box made ink, one left a box; prints an ink pixel's place and tone. */
    static void write(File f) throws Exception {
        ToneDocument d = page();
        d.setTexts(d.texts().with(box()).with(new TextBoxes.Box("still a box", 30, 300, 400, 2, true, TextBoxes.LEFT)));
        makeInk(d, 0, ScreenChecks.ink(), 4, 1, null);
        check(d.texts().size() == 1, "one box left");
        try (java.io.OutputStream o = new java.io.FileOutputStream(f)) { DocumentCodec.write(o, d.layerSnapshot()); }
        for (int y = 40; y < 200; y++) for (int x = 30; x < 560; x++) {
            int t = d.compositeTone(x, y);
            if (t < 64) { System.out.println(x + " " + y + " " + t); return; }
        }
        throw new AssertionError("no ink");
    }
}
