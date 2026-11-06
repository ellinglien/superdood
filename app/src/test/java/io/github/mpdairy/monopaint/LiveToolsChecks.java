package io.github.mpdairy.monopaint;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Live pages, the browser app's M1 ({@link LiveTools}): the host's tools read back as written, told apart from a change,
 * damage refused; and a guest's stroke with them ({@link StrokeStart#start}) mirrored as the host's page is.
 */
public final class LiveToolsChecks {
    public static void main(String[] args) throws IOException {
        roundTrip(); apart(); refused(); mirrored(); nibs();
        System.out.println("PASS: live tools: the page's size, the ten slots at their sizes and greys, the tool in use, the eraser's size and the mirrors read back as written; told apart from a change by their bytes and deflated; damage, a later version and a short toolbar refused; a stroke started with them on a page with the host's kaleidoscope lays its six copies, without mirrors one; a Stamp's nib travels with the toolbar, none for a toolbar without one");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static ToolSlots.Toolbar first() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < ToolSlots.COUNT; i++) lines.add(ToolSlots.first(i).write() + "\t" + ToolSlots.firstPx(i) + "\t" + ToolSlots.firstGrey(i));
        return ToolSlots.Toolbar.parse(lines);
    }

    static void roundTrip() throws IOException {
        ToolSlots.Toolbar bar = first();
        bar.px[4] = 77.5; bar.darkness[4] = .3;
        Mirrors k = Mirrors.NONE.kaleidoscope(1920, 2560);
        LiveTools t = new LiveTools(1920, 2560, bar, ToolSlots.ERASER, 31, k);
        LiveTools back = LiveTools.parse(inflate(t.write()));
        check(back.width == 1920 && back.height == 2560 && back.selected == ToolSlots.ERASER && back.eraserPx == 31, "the page, the tool, the eraser");
        check(back.toolbar.sameAs(bar) && back.toolbar.px[4] == 77.5 && back.toolbar.darkness[4] == .3, "the toolbar at its sizes and greys");
        check(back.mirrors().equals(k) && back.mirrors().copies(), "the mirrors");
        check(LiveTools.parse(new LiveTools(800, 600, bar, 3, 12, Mirrors.NONE).raw()).mirrors().isEmpty(), "none: none");
    }

    static void apart() throws IOException {
        byte[] tools = new LiveTools(100, 100, first(), 0, 31, Mirrors.NONE).write(), change = new LiveChange(0, 0, 1, 1, new int[]{9}).write();
        check(LiveTools.isDeflated(tools) && !LiveTools.isDeflated(change) && !LiveTools.isDeflated(new byte[]{1, 2, 3}), "told apart deflated");
        check(LiveTools.is(inflate(tools)) && !LiveTools.is(inflate(change)), "and inflated");
        try { LiveChange.read(tools); check(false, "a change reader refuses the tools"); } catch (IOException expected) { }
    }

    static void refused() throws IOException {
        byte[] raw = new LiveTools(100, 100, first(), 0, 31, Mirrors.NONE).raw();
        for (String damage : new String[]{"MBD tools 2", "page 0 100", "selected 99", "eraser -1", "mirrors zz"}) {
            String text = new String(raw, 8, raw.length - 8, "UTF-8");
            String key = damage.split(" ")[0] + " ", line = text.lines().filter(l -> l.startsWith(key)).findFirst().orElseThrow();
            if (damage.startsWith("MBD")) line = LiveTools.HEAD;
            check(refuses(withText(text.replace(line, damage))), "refused: " + damage);
        }
        String text = new String(raw, 8, raw.length - 8, "UTF-8");
        String shortBar = String.join("\n", Arrays.copyOf(text.split("\n"), 5 + 3)) + "\n";
        check(refuses(withText(shortBar)), "a toolbar of three refused");
        check(refuses(new byte[]{0, 0, 0, 0, 0, 0, 0, 1, 'x'}), "not tools");
    }
    static boolean refuses(byte[] raw) { try { LiveTools.parse(raw); return false; } catch (IOException e) { return true; } }
    static byte[] withText(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[8]); out.write(text.getBytes("UTF-8"));
        return out.toByteArray();
    }

    /** A slot's stroke as the guest starts it, on the host's kaleidoscope: six copies; on a page without mirrors, one. */
    static void mirrored() throws IOException {
        QuickStroke.renderAtLift = true;  // as the browser draws: one thread
        int w = 600, h = 600;
        LiveTools t = LiveTools.parse(new LiveTools(w, h, first(), 1, 31, Mirrors.NONE.kaleidoscope(w, h)).raw());
        check(marks(t, true) >= 6 * marks(t, false) * 9 / 10, "six copies: " + marks(t, true) + " against " + marks(t, false));
    }
    static int marks(LiveTools t, boolean mirrors) {
        ToneDocument page = InkWashLayers.blank(t.width, t.height);
        if (mirrors) page.setMirrors(t.mirrors());
        Brush brush = t.toolbar.brushes[t.selected];
        WatercolorSession session = new WatercolorSession();
        DrawingStroke s = StrokeStart.start(page, brush, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, t.toolbar.px[t.selected], t.toolbar.darkness[t.selected]), session, 0);
        for (int i = 0; i <= 40; i++) s.sample(380 + i, 300 + i / 4f, .5f, Float.NaN, Float.NaN, 1000 + 8 * i);
        s.finish();
        session.settleNow(page);
        int n = 0;
        for (int y = 0; y < t.height; y++) for (int x = 0; x < t.width; x++) if (page.compositeTone(x, y) < 200) n++;
        return n;
    }

    /** A cut Stamp in the toolbar: its nib travels too, read back pixel for pixel; a toolbar without one writes no nib line. */
    static void nibs() throws IOException {
        StampNib.forget();
        try {
            ToolSlots.Toolbar bar = first();
            check(!new String(new LiveTools(100, 100, bar, 0, 31, Mirrors.NONE).raw(), "UTF-8").contains("\nnib "), "no Stamp cut: no nib");
            byte[] a = new byte[12 * 9];
            for (int i = 0; i < a.length; i++) a[i] = (byte) (i * 7);
            StampNib nib = new StampNib(12, 9, a);
            bar.brushes[2] = Brush.of(ToolSlots.firstStamp().parts.withNib(StampNib.keep(nib)));
            bar.brushes[5] = bar.brushes[2];
            LiveTools back = LiveTools.parse(new LiveTools(100, 100, bar, 2, 31, Mirrors.NONE).raw());
            check(back.nibs.size() == 1 && back.nibs.get(0).id == nib.id && back.nibs.get(0).write().equals(nib.write()) && back.toolbar.sameAs(bar), "a Stamp's nib travels once, with the toolbar");
        } finally { StampNib.forget(); }
    }

    static byte[] inflate(byte[] b) throws IOException {
        try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(b))) { return in.readAllBytes(); }
    }
}
