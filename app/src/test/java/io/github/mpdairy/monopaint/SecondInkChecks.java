package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * Second Ink, the engine (spec docs/superpowers/specs/2026-11-09-second-ink-design.md): each pixel's ink share
 * ({@link ToneDocument.Layer#ink}), set once a gesture at its finish from the darkness the gesture added; erasing, Undo,
 * Redo, live Undo, a larger page and Keep Reflection keep it with its pixels; paint that moves takes the ink round it;
 * the screen never reads it.
 */
public final class SecondInkChecks {
    public static void main(String[] args) {
        laying(); erasing(); undo(); snapshots(); flowing(); opaque(); clearing(); liveUndo(); screen(); edge(); transforms();
        Assets.install(Assets.folder(Assets.FOLDER));
        strokes(); wetBeside(); smudge(); later(); lasso(); crop(); merge(); sinks(); copies(); shapes();
        if (args.length > 0 && args[0].equals("cost")) cost();
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static final int W = 200, H = 140, WASH = ToneDocument.PLANE_WASH;

    // ---- helpers ----

    /** Plane {@code p} of the page's first layer (-1: its base) as a snapshot holds it, or null. */
    static ToneDocument.Layer plane(ToneDocument d, int p) {
        ToneDocument.Layer layer = d.layerSnapshot().layers.get(0);
        return p < 0 ? layer : layer.plane(p);
    }
    /** That plane's ink shares, a missing plane or array as all 0 (what null means). */
    static byte[] inks(ToneDocument d, int p) {
        ToneDocument.Layer layer = plane(d, p);
        return layer == null || layer.ink == null ? new byte[W * H] : layer.ink.clone();
    }
    static int ink(ToneDocument d, int p, int x, int y) { return inks(d, p)[y * W + x] & 255; }
    /** That plane's tone over white there, 255 with no plane ({@link ToneDocument#tone} reads only the plane a gesture bound last). */
    static int tone(ToneDocument d, int p, int x, int y) {
        ToneDocument.Layer layer = plane(d, p);
        int i = y * W + x;
        return layer == null ? 255 : ToneDocument.onWhite(layer.tones[i] & 255, layer.alpha[i] & 255);
    }
    /** A wash stroke: the box {@code x0..x1-1, y0..y1-1} left at tone {@code gray}; {@code share} its second ink, -1 declaring none, -2 paint that flows. */
    static void wash(ToneDocument d, int share, int x0, int y0, int x1, int y1, int gray) {
        d.begin(1, ToneDocument.KIND_PAINT);
        if (share >= 0) d.inking(share);
        if (share == -2) d.inkFlows();
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) d.setTone(x, y, gray);
        d.finish();
    }
    static int[] shown(ToneDocument d) { int[] out = new int[W * H]; d.render(out, 0, 0, W, H); return out; }

    // ---- checks ----

    static void laying() {
        ToneDocument d = new ToneDocument(W, H), plain = new ToneDocument(W, H), none = new ToneDocument(W, H);
        wash(d, 255, 20, 20, 100, 40, 128);   // across a tile edge
        wash(plain, -1, 20, 20, 100, 40, 128);
        wash(none, 0, 20, 20, 100, 40, 128);
        byte[] ink = inks(d, WASH);
        int set = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            boolean in = x >= 20 && x < 100 && y >= 20 && y < 40;
            check((ink[y * W + x] & 255) == (in ? 255 : 0), "second ink where it drew, none elsewhere: " + x + "," + y + " " + (ink[y * W + x] & 255));
            if (in) set++;
        }
        check(set == 80 * 20 && plane(d, -1).inked() && plane(d, WASH).inked(), "the layer is inked");
        check(plane(plain, WASH).ink == null && !plane(plain, -1).inked(), "a stroke that declares no ink leaves the plane without ink shares");
        check(plane(none, WASH).ink == null, "so does one that declares first ink");
        // First ink over second: by the darkness each added.
        wash(d, 64, 20, 60, 60, 80, 191);
        check(ink(d, WASH, 30, 70) == 64, "a brush's own share on clear paper: " + ink(d, WASH, 30, 70));
        wash(d, 255, 20, 60, 60, 80, 191);
        check(ink(d, WASH, 30, 70) == 64, "a stroke that adds no darkness leaves the ink: " + ink(d, WASH, 30, 70));
        wash(d, 255, 100, 60, 180, 80, 191);                 // second ink, darkness 64
        wash(d, -1, 100, 60, 140, 80, 127);                  // first ink adds as much
        wash(d, 0, 140, 60, 180, 80, 0);                     // first ink adds three times as much
        int even = ink(d, WASH, 120, 70), more = ink(d, WASH, 160, 70);
        check(Math.abs(even - 128) <= 2, "first ink over second at equal darkness: near half, " + even);
        check(Math.abs(more - 64) <= 2 && more < even, "more darkness pulls it further down: " + more);
        check(ink(d, WASH, 30, 30) == 255, "the first stroke's ink as it was");
        System.out.println("PASS: Second Ink: a second-ink stroke sets the ink share where it draws and its layer is inked, one that declares none or first ink leaves the plane without shares; "
                + "first ink over second mixes by the darkness it adds (equal: " + even + ", three times: " + more + "), a stroke that darkens nothing leaves the ink");
    }

    static void erasing() {
        ToneDocument d = new ToneDocument(W, H);
        wash(d, 255, 20, 20, 100, 40, 100);
        check(d.beginPlane(WASH, false), "the wash plane");
        for (int y = 20; y < 40; y++) for (int x = 20; x < 100; x++) if (x < 40) d.eraseTone(x, y, 1); else if (x < 70) d.eraseTone(x, y, .5f);
        check(d.finish(), "erased");
        check(d.tone(30, 30) == 255 && d.tone(50, 30) > 100 && d.tone(50, 30) < 255, "all of one part gone, half of the next");
        check(ink(d, WASH, 30, 30) == 0, "no paint, no ink: " + ink(d, WASH, 30, 30));
        check(ink(d, WASH, 50, 30) == 255 && ink(d, WASH, 80, 30) == 255, "what the eraser leaves keeps its ink: " + ink(d, WASH, 50, 30));
        System.out.println("PASS: Second Ink: an eraser leaves the ink share where paint stays and none where none stays");
    }

    static void undo() {
        ToneDocument d = new ToneDocument(W, H);
        wash(d, -1, 10, 10, 150, 30, 200);
        byte[] blank = inks(d, WASH);
        wash(d, 255, 20, 20, 100, 40, 128);
        byte[] first = inks(d, WASH);
        wash(d, 0, 60, 10, 80, 60, 40);
        byte[] second = inks(d, WASH);
        check(!Arrays.equals(blank, first) && !Arrays.equals(first, second), "each stroke changed the ink shares");
        check(d.undo() && Arrays.equals(first, inks(d, WASH)), "Undo: the ink shares as before the stroke, byte for byte");
        check(d.undo() && Arrays.equals(blank, inks(d, WASH)) && !plane(d, -1).inked(), "Undo of the first second-ink stroke: none");
        check(d.redo() && Arrays.equals(first, inks(d, WASH)), "Redo: back");
        check(d.redo() && Arrays.equals(second, inks(d, WASH)), "Redo: the mixed shares back");
        // A stroke and its continuation are one step: the oldest ink before, the newest after.
        d.begin(1, ToneDocument.KIND_PAINT); d.inking(255); for (int x = 120; x < 140; x++) d.setTone(x, 100, 90); d.finish();
        d.begin(1, ToneDocument.KIND_PAINT); d.inking(255); for (int x = 120; x < 140; x++) d.setTone(x, 101, 90); d.finishContinuation();
        byte[] both = inks(d, WASH);
        check((both[100 * W + 130] & 255) == 255 && (both[101 * W + 130] & 255) == 255, "a continuation's ink");
        check(d.undo() && Arrays.equals(second, inks(d, WASH)), "Undo takes the stroke and its continuation's ink together");
        check(d.redo() && Arrays.equals(both, inks(d, WASH)), "Redo brings both back");
        // A cancelled gesture leaves no ink.
        d.begin(1, ToneDocument.KIND_PAINT); d.inking(255); d.setTone(5, 120, 0); d.cancel();
        check(Arrays.equals(both, inks(d, WASH)), "a cancelled gesture lays no ink");
        System.out.println("PASS: Second Ink: Undo and Redo bring the ink shares back byte for byte, a continuation's with its stroke's; a cancelled gesture lays none");
    }

    static void snapshots() {
        ToneDocument d = new ToneDocument(W, H);
        wash(d, -1, 10, 10, 150, 30, 200);
        ToneDocument.Snapshot before = d.layerSnapshot();
        wash(d, 255, 20, 20, 100, 40, 128);
        check(before.layers.get(0).plane(WASH).ink == null && !before.layers.get(0).inked(), "a snapshot from before the stroke has no ink");
        check(plane(d, WASH).ink != null, "the page has");
        // A snapshot holding the ink array, taken before a gesture and in the middle of one: neither sees the gesture's shares.
        ToneDocument.Snapshot held = d.layerSnapshot();
        byte[] was = held.layers.get(0).plane(WASH).ink.clone();
        d.begin(1, ToneDocument.KIND_PAINT);
        for (int y = 20; y < 40; y++) for (int x = 20; x < 100; x++) d.setTone(x, y, 20);
        ToneDocument.Snapshot mid = d.layerSnapshot();
        d.finish();
        check(Arrays.equals(was, held.layers.get(0).plane(WASH).ink), "a snapshot's ink shares stay as taken");
        check(Arrays.equals(was, mid.layers.get(0).plane(WASH).ink), "and those of one taken while the gesture was open");
        check(ink(d, WASH, 30, 30) < 255 && ink(d, WASH, 30, 30) > 0, "the page's are mixed: " + ink(d, WASH, 30, 30));
        held.release(); mid.release(); before.release();
        wash(d, 255, 20, 100, 40, 110, 128);
        check(ink(d, WASH, 30, 105) == 255, "and the page goes on after they are released");
        System.out.println("PASS: Second Ink: a snapshot taken before a stroke, or while it is open, never sees the stroke's ink shares");
    }

    static void flowing() {
        ToneDocument d = new ToneDocument(W, H);
        wash(d, 255, 20, 20, 40, 60, 100);    // second ink
        wash(d, 0, 43, 20, 63, 60, 100);      // first ink, three pixels on
        // Paint runs onto the clear paper between them, right of the first ink, and far out from the second.
        d.begin(1, ToneDocument.KIND_PAINT); d.inkFlows();
        for (int x = 40; x < 43; x++) d.setTone(x, 30, 100);
        d.setTone(63, 30, 100); d.setTone(64, 30, 100);
        for (int x = 40; x < 43; x++) d.setTone(x, 50, 100);
        for (int y = 60; y < 90; y++) d.setTone(30, y, 100);
        d.setTone(150, 120, 100);
        // It darkens second-ink paint too: that stays second ink.
        d.setTone(25, 40, 20);
        d.finish();
        check(ink(d, WASH, 40, 30) == 255, "beside second ink, its ink: " + ink(d, WASH, 40, 30));
        int between = ink(d, WASH, 41, 30);
        check(between > 0 && between < 255, "between the two, a share between: " + between);
        check(ink(d, WASH, 64, 30) == 0 && ink(d, WASH, 63, 30) == 0, "beside first ink, none: " + ink(d, WASH, 64, 30));
        for (int y = 60; y < 90; y++) check(ink(d, WASH, 30, y) == 255, "paint that ran far onto clear paper carries its ink: row " + y + " " + ink(d, WASH, 30, y));
        check(ink(d, WASH, 150, 120) == 0, "paint from nowhere is first ink");
        check(ink(d, WASH, 25, 40) == 255 && ink(d, WASH, 50, 40) == 0, "darkened and untouched paint keep their inks");
        byte[] flowed = inks(d, WASH);
        check(d.undo() && ink(d, WASH, 30, 80) == 0 && ink(d, WASH, 41, 30) == 0 && d.redo() && Arrays.equals(flowed, inks(d, WASH)), "Undo and Redo of the flow");
        // A whole patch of paint run 25 pixels out from second ink: every pass takes it 2 further.
        wash(d, 255, 100, 100, 110, 120, 100);
        wash(d, -2, 110, 100, 135, 120, 100);
        for (int y = 100; y < 120; y++) for (int x = 110; x < 135; x++) check(ink(d, WASH, x, y) == 255, "a patch run far from its ink carries it: " + x + "," + y + " " + ink(d, WASH, x, y));
        // A plane without ink shares gets none from paint that flows.
        ToneDocument plain = new ToneDocument(W, H);
        wash(plain, -1, 20, 20, 40, 60, 100);
        wash(plain, -2, 40, 20, 50, 60, 100);
        check(plane(plain, WASH).ink == null, "flowing paint on a page without second ink makes no ink shares");
        System.out.println("PASS: Second Ink: paint that moves takes the ink round it: second ink's beside second (255), a share between the two (" + between
                + "), none beside first; run far onto clear paper it carries its ink along; a page without second ink gets no shares");
    }

    static void opaque() {
        ToneDocument d = new ToneDocument(W, H);
        int plane = ToneDocument.PLANE_OPAQUE, kinds = ToneDocument.KIND_PAINT | ToneDocument.KIND_OPAQUE;
        d.begin(1, kinds); d.inking(0); for (int x = 20; x < 60; x++) d.paintTone(x, 30, 0, 255); d.finish();
        d.begin(1, kinds); d.inking(255); for (int x = 20; x < 40; x++) d.paintTone(x, 30, 128, 255); d.finish();
        int over = ink(d, plane, 30, 30);
        check(Math.abs(over - 128) <= 2, "lighter cover paint over darker takes the ink over by what it lightens: " + over);
        check(ink(d, plane, 50, 30) == 0, "the rest stays first ink");
        d.begin(1, kinds); d.inking(0); for (int x = 20; x < 40; x++) d.paintTone(x, 30, 255, 255); d.finish();
        check(ink(d, plane, 30, 30) == 0, "white paint holds no ink");
        // A gesture that declares no ink and lightens (a lift, an eraser) leaves what stays its ink.
        d.begin(1, kinds); d.inking(255); for (int x = 70; x < 90; x++) d.paintTone(x, 30, 0, 255); d.finish();
        d.begin(1, kinds); for (int x = 70; x < 80; x++) d.paintTone(x, 30, 128, 255); d.finish();
        check(d.tone(75, 30) == 128 && ink(d, plane, 75, 30) == 255 && ink(d, plane, 85, 30) == 255, "lightened with no ink declared, the ink stays: " + ink(d, plane, 75, 30));
        System.out.println("PASS: Second Ink: lighter opaque paint over darker takes the ink over by what it lightens (" + over + "), one that declares no ink leaves it; white paint holds none");
    }

    static void clearing() {
        ToneDocument d = new ToneDocument(W, H);
        d.begin(); d.inking(255); for (int x = 20; x < 100; x++) d.setTone(x, 30, 60); d.finish();   // on the layer's base
        byte[] drawn = inks(d, -1);
        check((drawn[30 * W + 50] & 255) == 255, "second ink on a layer's base");
        check(d.clear() && !plane(d, -1).inked(), "Clear takes the ink with the paint");
        check(d.undo() && Arrays.equals(drawn, inks(d, -1)), "and Undo brings it back");
        System.out.println("PASS: Second Ink: clearing a layer takes its ink shares, Undo brings them back");
    }

    /** Live pages' own Undo ({@link ToneDocument#takeBack}) puts the ink back with the pixels. */
    static void liveUndo() {
        ToneDocument d = new ToneDocument(W, H);
        wash(d, 255, 20, 20, 100, 40, 100);
        byte[] drawn = inks(d, WASH);
        long seen = d.top();
        d.beginPlane(WASH, false);
        for (int y = 20; y < 40; y++) for (int x = 30; x < 60; x++) d.eraseTone(x, y, 1);
        d.finish();
        List<Object> erase = d.stepsAfter(seen);
        check(ink(d, WASH, 40, 30) == 0, "erased");
        check(d.takeBack(erase, new BitSet()) && d.tone(40, 30) == 100, "the erase taken back");
        check(Arrays.equals(drawn, inks(d, WASH)), "its ink back with the paint: " + ink(d, WASH, 40, 30));
        check(d.undo() && ink(d, WASH, 40, 30) == 0 && d.redo() && Arrays.equals(drawn, inks(d, WASH)), "as one Undo step");
        // A Clear page taken back: the ink with the marks, on the base and on a plane.
        d.begin(); d.inking(200); for (int x = 120; x < 190; x++) d.setTone(x, 100, 60); d.finish();
        byte[] base = inks(d, -1);
        seen = d.top();
        check(d.clearPage() && !plane(d, -1).inked(), "cleared");
        check(d.takeBack(d.stepsAfter(seen), new BitSet()) && tone(d, WASH, 40, 30) == 100 && tone(d, -1, 150, 100) == 60, "the clear taken back");
        check(Arrays.equals(drawn, inks(d, WASH)) && Arrays.equals(base, inks(d, -1)) && (base[100 * W + 150] & 255) == 200, "its ink back on each plane");
        check(d.undo() && !plane(d, -1).inked() && d.redo() && Arrays.equals(drawn, inks(d, WASH)) && Arrays.equals(base, inks(d, -1)), "as one Undo step too");
        System.out.println("PASS: Second Ink: a live Undo puts the ink share back with its pixels, a stroke's and a Clear page's");
    }

    static void screen() {
        ToneDocument d = new ToneDocument(W, H), plain = new ToneDocument(W, H);
        for (ToneDocument page : new ToneDocument[]{d, plain}) {
            boolean ink = page == d;
            wash(page, ink ? 255 : -1, 20, 20, 100, 40, 128);
            wash(page, ink ? 0 : -1, 60, 10, 80, 60, 40);
            wash(page, ink ? 128 : -1, 90, 30, 150, 90, 90);
            page.begin(1, ToneDocument.KIND_PEN); if (ink) page.inking(255); for (int x = 10; x < 190; x++) page.setTone(x, 35, 0); page.finish();
            page.beginPlane(WASH, false); for (int y = 25; y < 50; y++) for (int x = 70; x < 120; x++) page.eraseTone(x, y, .5f); page.finish();
            page.begin(1, ToneDocument.KIND_PAINT); if (ink) page.inkFlows(); for (int x = 150; x < 160; x++) page.setTone(x, 50, 90); page.finish();
        }
        check(plane(d, -1).inked() && !plane(plain, -1).inked(), "one page with ink shares, one without");
        check(Arrays.equals(shown(d), shown(plain)), "the screen shows the same pixels with and without ink shares");
        ToneDocument.Layer with = plane(d, WASH), without = plane(plain, WASH);
        check(Arrays.equals(with.tones, without.tones) && Arrays.equals(with.alpha, without.alpha), "and the plane holds the same tones and coverage");
        System.out.println("PASS: Second Ink: the screen's pixels and the plane's tones are byte for byte the same with and without ink shares");
    }

    /** The page's last tiles, narrower and shorter than the rest ({@code W}, {@code H} aren't whole tiles). */
    static void edge() {
        check(W % ToneDocument.TILE != 0 && H % ToneDocument.TILE != 0, "a page whose last tiles are cut");
        ToneDocument d = new ToneDocument(W, H);
        wash(d, 255, 180, 120, W, H, 191);
        byte[] laid = inks(d, WASH);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) check((laid[y * W + x] & 255) == (x >= 180 && y >= 120 ? 255 : 0), "second ink into the page's corner: " + x + "," + y);
        wash(d, 0, 190, 130, W, H, 127);
        int mixed = ink(d, WASH, W - 1, H - 1);
        check(Math.abs(mixed - 128) <= 2 && ink(d, WASH, 185, 125) == 255, "mixed in the corner tile: " + mixed);
        check(d.undo() && Arrays.equals(laid, inks(d, WASH)) && d.redo() && ink(d, WASH, W - 1, H - 1) == mixed, "Undo and Redo there");
        // Paint that flows along the page's edges takes the ink beside it.
        ToneDocument f = new ToneDocument(W, H);
        wash(f, 255, 180, 100, W, 120, 100);
        wash(f, -2, 180, 120, W, H, 100);
        for (int y = 120; y < H; y++) for (int x = 180; x < W; x++) check(ink(f, WASH, x, y) == 255, "flowing paint in the corner: " + x + "," + y + " " + ink(f, WASH, x, y));
        wash(f, 255, 0, 0, 10, 10, 100);
        wash(f, -2, 0, 10, 10, 14, 100);
        check(ink(f, WASH, 0, 13) == 255 && ink(f, WASH, 9, 10) == 255, "and at the page's top left");
        System.out.println("PASS: Second Ink: tiles cut by the page's edge hold, mix (" + mixed + "), undo and flow their ink as whole ones do");
    }

    /** A larger page ({@link ToneDocument#expand}) and Keep Reflection carry the ink with the pixels. */
    static void transforms() {
        ToneDocument d = new ToneDocument(W, H);
        wash(d, 255, 20, 20, 90, 40, 128);
        wash(d, 0, 60, 30, 95, 50, 60);        // mixed shares too
        wash(d, 255, 150, 100, 180, 120, 128); // right of the mirror to come
        byte[] was = inks(d, WASH);
        check((was[35 * W + 70] & 255) > 0 && (was[35 * W + 70] & 255) < 255, "a mixed share");
        int left = 10, top = 7, w = W + left + 3, h = H + top + 5;
        d.expand(left, top, 3, 5);
        byte[] grown = plane(d, WASH).ink;
        check(grown != null && grown.length == w * h, "the larger page's plane has ink shares");
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean old = x >= left && x < left + W && y >= top && y < top + H;
            check(grown[y * w + x] == (old ? was[(y - top) * W + x - left] : 0), "each pixel's ink moved with it, none on the new paper: " + x + "," + y);
        }
        check(d.undo() && Arrays.equals(was, inks(d, WASH)), "Undo: the page and its ink as they were");
        // Keep Reflection: the left of a mirror down x = 100 laid over the right.
        d.setMirrors(Mirrors.NONE.add(new Mirrors.Line(100, 70, Math.toRadians(90))).styled(Mirrors.Style.REAL).keeping(30, 30));
        check(d.keepReflection(), "Keep Reflection");
        check(tone(d, WASH, 170, 30) == 128 && ink(d, WASH, 170, 30) == 255, "the reflection has its source's ink: " + ink(d, WASH, 170, 30));
        check(ink(d, WASH, 130, 35) == (was[35 * W + 70] & 255) || ink(d, WASH, 129, 35) == (was[35 * W + 70] & 255), "a mixed share too");
        check(tone(d, WASH, 165, 110) == 255 && ink(d, WASH, 165, 110) == 0, "the marks it covers leave no ink");
        check(ink(d, WASH, 30, 30) == 255, "the kept side as it was");
        check(d.undo() && Arrays.equals(was, inks(d, WASH)), "Undo puts the ink back");
        System.out.println("PASS: Second Ink: a larger page and Keep Reflection carry each pixel's ink share with it");
    }

    // ---- the brushes' strokes and what carries their ink (Task 4) ----

    /** {@code b} with Second Ink on. */
    /** The Basic tool in the first ink: some come with Second Ink On (the mix, 2026-11-10; Grey wash is one). */
    static ModularBrush first(String name) { ModularBrush b = FloatChecks.preset(name); if (ResolvedBrush.second(b)) b.part(BrushPart.MEDIUM).set(PartDial.SECOND, 0); return b; }
    static ModularBrush second(ModularBrush b) { ModularBrush c = b.copy(); c.part(BrushPart.MEDIUM).set(PartDial.SECOND, 1); return c; }
    /** Every plane the page holds: each layer's base, then its kind planes. */
    static List<ToneDocument.Layer> planes(ToneDocument d) {
        List<ToneDocument.Layer> all = new java.util.ArrayList<>();
        for (ToneDocument.Layer layer : d.layerSnapshot().layers) {
            all.add(layer);
            if (layer.planes != null) for (ToneDocument.Layer p : layer.planes) if (p != null) all.add(p);
        }
        return all;
    }
    /** Whether no plane of the page has ink shares: the page before Second Ink, byte for byte (the file writes none, Task 5). */
    static boolean plain(ToneDocument d) { for (ToneDocument.Layer p : planes(d)) if (p.ink != null) return false; return true; }
    /** Over the box, every plane: the paint's darkness, and that times its ink share. */
    static long[] paint(ToneDocument d, int x0, int y0, int x1, int y1) {
        long dark = 0, inked = 0;
        for (ToneDocument.Layer p : planes(d)) for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            int i = y * d.width + x, k = 255 - ToneDocument.onWhite(p.tones[i] & 255, p.alpha[i] & 255);
            dark += k; if (p.ink != null) inked += k * (p.ink[i] & 255);
        }
        return new long[]{dark, inked};
    }
    /** The second ink's share of the box's paint, 0 to 255; -1 with no paint. */
    static int share(ToneDocument d, int x0, int y0, int x1, int y1) { long[] p = paint(d, x0, y0, x1, y1); return p[0] == 0 ? -1 : (int) ((p[1] + p[0] / 2) / p[0]); }
    static int share(ToneDocument d) { return share(d, 0, 0, d.width, d.height); }
    /** How many pixels hold paint with a share strictly between the two inks. */
    static int between(ToneDocument d) {
        int n = 0;
        for (ToneDocument.Layer p : planes(d)) if (p.ink != null) for (int i = 0; i < p.ink.length; i++) if (p.alpha[i] != 0 && (p.ink[i] & 255) > 8 && (p.ink[i] & 255) < 247) n++;
        return n;
    }
    /** Every plane's ink shares (no plane, all 0), for Undo's byte for byte. */
    static byte[][] allInks(ToneDocument d) {
        ToneDocument.Snapshot s = d.layerSnapshot();
        byte[][] out = new byte[s.layers.size() * (1 + ToneDocument.PLANES)][];
        for (int l = 0; l < s.layers.size(); l++) for (int p = -1; p < ToneDocument.PLANES; p++) {
            ToneDocument.Layer plane = p < 0 ? s.layers.get(l) : s.layers.get(l).plane(p);
            out[l * (1 + ToneDocument.PLANES) + p + 1] = plane == null || plane.ink == null ? new byte[d.width * d.height] : plane.ink.clone();
        }
        return out;
    }
    static void draw(ToneDocument d, WatercolorSession s, ModularBrush b, double size, double[][] path) { PresetPictures.stroke(d, s, b, size, .9, path); s.settleNow(d); }

    /** A brush's stroke as the screen starts it: Second Ink on, all its paint is second ink; off, the page has no ink shares at all. */
    static void strokes() {
        StringBuilder names = new StringBuilder();
        for (String name : new String[]{"Steady line", "Swell brush", "Soft charcoal", "Permanent marker", "Fill in", "Sumi", "Grey wash", "Wet gouache", "Soft wash", "White chalk"}) {
            ModularBrush b = first(name);
            for (double size : new double[]{14, 70}) {  // drawn live, and past the quick preview's reach
                ToneDocument on = InkWashLayers.blank(360, 200), off = InkWashLayers.blank(360, 200);
                draw(on, new WatercolorSession(), second(b), size, FloatChecks.line(60, 100, 300, 100, .8));
                draw(off, new WatercolorSession(), b, size, FloatChecks.line(60, 100, 300, 100, .8));
                long[] laid = paint(on, 0, 0, 360, 200);
                check(plain(off), name + " " + size + ", Second Ink off: no plane has ink shares");
                if (name.equals("White chalk")) { check(laid[0] == 0 && share(on) == -1, name + ": white paint on white holds no ink"); continue; }
                check(laid[0] > 20000 && laid[1] == laid[0] * 255, name + " " + size + ", Second Ink on: every pixel of its paint is second ink: " + share(on) + " of " + laid[0]);
                // First ink over it mixes; Undo takes the stroke's ink with it.
                byte[][] was = allInks(on);
                draw(on, new WatercolorSession(), b, size, FloatChecks.line(180, 40, 180, 160, .8));
                int mixed = share(on);
                check(mixed > 0 && mixed < 255, name + " " + size + ": first ink across it leaves a share between: " + mixed);
                while (!Arrays.deepEquals(was, allInks(on))) check(on.undo(), name + " " + size + ": Undo brings the ink back as it was");
            }
            names.append(name).append(", ");
        }
        // A fill and Make Ink lay with their slot's brush, so through the same begin (FillTool.brush): one direct stroke as they start it.
        ToneDocument d = InkWashLayers.blank(360, 200);
        CarrySmudgeChecks.feed(ModularStroke.start(d, second(first("Swell brush")), 14, 1, new WatercolorSession()), FloatChecks.line(60, 100, 300, 100, .8));
        check(share(d) == 255, "a stroke not started quick: " + share(d));
        System.out.println("PASS: Second Ink: strokes of " + names + "live and past the quick preview: on, all their paint is second ink; off, no plane has ink shares; first ink across mixes, Undo takes it back");
    }

    /** A wet second-ink wash beside a wet first-ink one, dried: each its own ink far from the other, graded where they met. */
    static void wetBeside() {
        StringBuilder out = new StringBuilder();
        for (String name : new String[]{"Soft wash", "Grey wash", "Wet gouache", "Wet oils"}) {
            ModularBrush b = first(name);
            ToneDocument d = InkWashLayers.blank(480, 240);
            WatercolorSession s = new WatercolorSession();
            // Both laid while the first is wet (PresetPictures.stroke settles only dry brushes between), then dried.
            PresetPictures.stroke(d, s, second(b), 50, .8, FloatChecks.line(60, 100, 420, 100, .8));
            PresetPictures.stroke(d, s, b, 50, .8, FloatChecks.line(60, 140, 420, 140, .8));
            s.settleNow(d);
            int top = share(d, 100, 60, 380, 80), bottom = share(d, 100, 160, 380, 180), middle = share(d, 100, 112, 380, 128), graded = between(d);
            check(top >= 250, name + ": the second-ink wash's far side is second ink: " + top);
            check(bottom >= 0 && bottom <= 5, name + ": the first-ink wash's far side is first ink: " + bottom);
            check(middle > 40 && middle < 215 && graded > 200, name + ": where they met the share grades: " + middle + ", " + graded + " px between");
            out.append(name).append(' ').append(top).append('/').append(middle).append('/').append(bottom).append("; ");
        }
        // Drying (Slow) and Float: paint that settles by itself keeps its stroke's ink.
        ModularBrush slow = second(LaterChecks.with("Soft wash", PartChoice.DRYING, PartChoice.LATER_OFF, 1));
        ToneDocument d = InkWashLayers.blank(480, 240);
        WatercolorSession s = new WatercolorSession();
        PresetPictures.stroke(d, s, slow, 50, .8, FloatChecks.line(60, 120, 420, 120, .8));
        int ticks = 0; while (s.tickDrying(d) && ticks < 5000) ticks++;
        s.settleNow(d);
        check(share(d) == 255 && paint(d, 0, 0, 480, 240)[0] > 50000, "a Slow wash settled is all second ink: " + share(d));
        System.out.println("PASS: Second Ink: a wet second-ink wash beside a wet first-ink one dries graded where they met (far side/between/far side: " + out + "); a Slow wash settles all second ink");
    }

    /** A smudge that lays no paint of its own, dragged from second-ink paint onto clear paper, carries the ink there. */
    static void smudge() {
        ModularBrush ink = second(first("Sumi")), finger = CarrySmudgeChecks.load(CarrySmudgeChecks.brush(CarrySmudgeChecks.SMEAR), 0);
        check(ResolvedBrush.of(finger, 30, 1).carry != null && ResolvedBrush.of(finger, 30, 1).load == 0, "the smudge lays none of its own");
        ToneDocument d = InkWashLayers.blank(360, 200);
        WatercolorSession s = new WatercolorSession();
        for (int y = 70; y <= 130; y += 6) draw(d, s, ink, 14, FloatChecks.line(40, y, 140, y, .8));
        check(paint(d, 160, 60, 300, 140)[0] == 0, "clear paper right of the block");
        for (int y = 80; y <= 120; y += 10) draw(d, s, finger, 30, FloatChecks.line(90, y, 260, y, .8));
        long[] dragged = paint(d, 160, 60, 300, 140);
        check(dragged[0] > 5000, "the smudge dragged paint onto the clear paper: " + dragged[0]);
        check(share(d, 160, 60, 300, 140) >= 250 && share(d) >= 250, "and it is second ink there: " + share(d, 160, 60, 300, 140));
        // The same from first ink, on a page that holds second ink elsewhere: first ink.
        ToneDocument plain = InkWashLayers.blank(360, 200);
        draw(plain, s, ink, 14, FloatChecks.line(40, 180, 300, 180, .8));
        for (int y = 70; y <= 130; y += 6) draw(plain, s, first("Sumi"), 14, FloatChecks.line(40, y, 140, y, .8));
        for (int y = 80; y <= 120; y += 10) draw(plain, s, finger, 30, FloatChecks.line(90, y, 260, y, .8));
        check(paint(plain, 160, 60, 300, 140)[0] > 5000 && share(plain, 160, 60, 300, 140) == 0, "first ink smudged stays first: " + share(plain, 160, 60, 300, 140));
        // A smudge with any paint of its own lays its brush's ink (the paint a stroke moves counts as its own).
        for (double load : new double[]{.2, .8}) {
            ToneDocument own = InkWashLayers.blank(360, 200);
            draw(own, s, second(CarrySmudgeChecks.load(CarrySmudgeChecks.brush(CarrySmudgeChecks.SMEAR), load)), 30, FloatChecks.line(90, 100, 260, 100, .8));
            check(paint(own, 0, 0, 360, 200)[0] > 2000 && share(own) == 255, "a smudge with Own paint " + load + " lays its brush's ink: " + share(own) + " (dark " + paint(own, 0, 0, 360, 200)[0] + ")");
        }
        System.out.println("PASS: Second Ink: a smudge dragged from second-ink paint onto clear paper carries the ink (" + share(d, 160, 60, 300, 140) + "), from first ink none; a loaded one lays its brush's ink");
    }

    /** Marks that change later (Fading, Lemon Juice, Later's media) keep their pixels' ink as they do. */
    static void later() {
        StringBuilder out = new StringBuilder();
        FadingChecks.clock = 50_000_000;
        for (PartChoice life : new PartChoice[]{PartChoice.FADE, PartChoice.LEMON}) {
            ToneDocument d = FadingChecks.drawn(second(FadingChecks.brush(life, 20)));
            check(d.fading() && share(d) == 255, life + ": laid as second ink: " + share(d));
            for (long t = 2000; t <= (life == PartChoice.LEMON ? 40_000 : 10_000); t += 2000) {
                d.fade(FadingChecks.clock + t, null);
                long[] p = paint(d, 0, 0, d.width, d.height);
                check(p[0] == 0 || p[1] == p[0] * 255, life + " at " + t + " ms: every pixel of it still second ink: " + share(d));
            }
            check(paint(d, 0, 0, d.width, d.height)[0] > 20000, life + ": paint left to look at");
            out.append(life).append(", ");
        }
        for (PartChoice later : LaterMediaChecks.MEDIA) {
            FadingChecks.clock += 10_000_000;
            ToneDocument d = LaterMediaChecks.drawn(second(LaterMediaChecks.brush(later)), LaterMediaChecks.size(later));
            check(share(d) == 255, later + ": laid as second ink: " + share(d));
            long time = LaterMediaChecks.time(later);
            for (int step = 1; step <= 4; step++) d.fade(FadingChecks.clock + time * step / 4 + 1000, null);
            long[] p = paint(d, 0, 0, d.width, d.height);
            check(p[0] > 20000 && p[1] == p[0] * 255, later + ": changed, every pixel of it still second ink: " + share(d));
            byte[][] done = allInks(d);
            check(d.undo() && paint(d, 0, 0, d.width, d.height)[0] == 0 && Arrays.deepEquals(allInks(d), allInks(InkWashLayers.blank(d.width, d.height))), later + ": Undo takes the mark and its ink, every share");
            check(d.redo() && Arrays.deepEquals(allInks(d), done), later + ": Redo gives the ink back as it was");
            ToneDocument off = LaterMediaChecks.drawn(LaterMediaChecks.brush(later), LaterMediaChecks.size(later));
            for (int step = 1; step <= 4; step++) off.fade(FadingChecks.clock + time * step / 4 + 1000, null);
            check(plain(off), later + ", Second Ink off: no plane has ink shares");
            out.append(later).append(", ");
        }
        // What a Later mark grows onto clear paper is its own ink, whatever ink lies under the mark: over a line of the
        // other ink (0, 1), and a first-ink mark beside a second-ink one of its kind that has grown (2).
        for (PartChoice later : new PartChoice[]{PartChoice.LATER_FEATHER, PartChoice.LATER_FOX, PartChoice.LATER_PRESS, PartChoice.LATER_TORN, PartChoice.LATER_STITCH}) for (int way = 0; way < 3; way++) {
            FadingChecks.clock += 10_000_000;
            long time = LaterMediaChecks.time(later);
            ToneDocument d = InkWashLayers.blank(FadingChecks.W, FadingChecks.H);
            ModularBrush under = way == 2 ? LaterMediaChecks.brush(later) : first("Swell brush"), over = LaterMediaChecks.brush(later);
            if (way == 1) over = second(over); else under = second(under);
            CarrySmudgeChecks.feed(ModularStroke.start(d, under, way == 2 ? 14 : 10, .8, new WatercolorSession()), FadingChecks.line(40, 360, 120));
            if (way == 2) { for (int step = 1; step <= 4; step++) d.fade(FadingChecks.clock + time * step / 4 + 1000, null); FadingChecks.clock += 10_000_000; }
            // The paint on pixels clear, every plane, before the Later stroke: its darkness, and that times its ink share.
            List<ToneDocument.Layer> before = planes(d);
            CarrySmudgeChecks.feed(ModularStroke.start(d, over, 14, .8, new WatercolorSession()), FadingChecks.line(40, 360, way == 2 ? 126 : 120));
            for (int step = 1; step <= 4; step++) d.fade(FadingChecks.clock + time * step / 4 + 1000, null);
            long dark = 0, inked = 0;
            pixels:
            for (int i = 0; i < d.width * d.height; i++) {
                for (ToneDocument.Layer p : before) if (p.alpha[i] != 0) continue pixels;
                for (ToneDocument.Layer p : planes(d)) { int k = 255 - ToneDocument.onWhite(p.tones[i] & 255, p.alpha[i] & 255); dark += k; if (p.ink != null) inked += k * (p.ink[i] & 255); }
            }
            check(dark > 5000 && inked == (way == 1 ? dark * 255 : 0), later + " way " + way + ": its paint on paper clear before it is all its own ink: " + inked / dark + " of 255 (dark " + dark + ")");
        }
        System.out.println("PASS: Second Ink: marks that change later keep their ink as they do (" + out + "all second ink to the end), Undo takes it with them; off, no ink shares; what a Later mark grows is its own ink over the other ink");
    }

    /** Copy Strokes' copies are their stroke's ink, drawn again or laid as its change reflected ({@link ToneDocument#layAgain}). */
    static void copies() {
        ModularBrush pen = QuickStrokeChecks.brush("Fineliner", PartChoice.NONE);
        for (int k = 0; k < 4; k++) {
            ToneDocument d = InkWashLayers.blank(600, 600);
            d.setMirrors(d.mirrors().kaleidoscope(600, 600));
            MirrorStroke s = MirrorChecks.mirrored(d, k < 2 ? second(pen) : pen, 6, null, k % 2 == 1);
            MirrorChecks.stroke(s, 340, 200, 420, 160, 40);
            s.lift(); while (s.develop(0)) { } s.finish();
            check(s.drawn >= 5 && paint(d, 0, 0, 600, 600)[0] > 20000, "a kaleidoscope's copies drawn: " + s.drawn);
            check(k < 2 ? share(d) == 255 : plain(d), (k % 2 == 1 ? "reflected" : "drawn again") + (k < 2 ? ": every copy second ink: " + share(d) : ", Second Ink off: no ink shares"));
        }
        System.out.println("PASS: Second Ink: Copy Strokes' copies are their stroke's ink, drawn again or reflected; off, no ink shares");
    }

    /** A shape is the ink of its slot's brush ({@link StrokeStart#shape}): dragged as the browser draws one, laid as the Manta and a replay do. */
    static void shapes() {
        ModularBrush pen = first("Sumi");
        ToneDocument d = InkWashLayers.blank(300, 200);
        WatercolorSession s = new WatercolorSession();
        StrokeStart.shape(d, second(pen), s);
        ShapeStroke live = new ShapeStroke(d, ToolSettings.defaults(ToolSettings.Tool.SHAPES).shape(ToolSettings.Shape.RECTANGLE).filled(true), 0, 20, 20);
        live.preview(80, 80);
        check(live.finish() && paint(d, 0, 0, 100, 200)[0] > 100000 && share(d, 0, 0, 100, 200) == 255, "a second-ink slot's dragged shape is second ink: " + share(d, 0, 0, 100, 200));
        for (int k = 1; k <= 2; k++) {
            int[] spans = new int[d.height * 4];
            for (int y = 20; y < 80; y++) { spans[y * 4] = k * 100 + 20; spans[y * 4 + 1] = k * 100 + 80; }
            StrokeStart.shape(d, k == 1 ? second(pen) : pen, s);
            check(ShapeStroke.lay(d, spans, 0) && share(d, k * 100, 0, k * 100 + 100, 200) == (k == 1 ? 255 : 0), "a shape laid from a " + (k == 1 ? "second" : "first") + "-ink slot: " + share(d, k * 100, 0, k * 100 + 100, 200));
        }
        // Replay starts a stroke from its logged code alone (ReplayRun.stroke): the switch rides in the code.
        ToneDocument again = InkWashLayers.blank(300, 200);
        DrawingStroke replayed = ReplayRun.stroke(again, ModularCode.encode(second(pen)), 12, .9, ReplayRun.session(null));
        for (double[] p : FloatChecks.line(40, 100, 260, 100, .8)) replayed.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, (long) p[3]);
        replayed.finish();
        check(share(again) == 255, "a replayed second-ink stroke is second ink: " + share(again));
        ToneDocument off = InkWashLayers.blank(300, 200);
        StrokeStart.shape(off, pen, s);
        ShapeStroke first = new ShapeStroke(off, ToolSettings.defaults(ToolSettings.Tool.SHAPES).shape(ToolSettings.Shape.RECTANGLE).filled(true), 0, 20, 20);
        first.preview(80, 80);
        check(first.finish() && plain(off), "Second Ink off: a shape leaves no ink shares");
        System.out.println("PASS: Second Ink: a shape is its slot's brush's ink, dragged or laid; a first-ink slot's after it first ink; off, no ink shares");
    }

    private static float[] box(float l, float t, float r, float b) { return new float[]{l, t, r, t, r, b, l, b}; }
    /** Every plane's ink shares over a box, one after another. */
    static byte[] inksIn(ToneDocument d, int x, int y, int w, int h) {
        byte[][] all = allInks(d);
        byte[] out = new byte[all.length * w * h];
        for (int k = 0; k < all.length; k++) for (int row = 0; row < h; row++) System.arraycopy(all[k], (y + row) * d.width + x, out, (k * h + row) * w, w);
        return out;
    }
    /** The Lasso lifts, moves and lays each pixel's ink with it; laid over other paint the inks mix by darkness; Undo restores. */
    static void lasso() {
        ToneDocument d = InkWashLayers.blank(240, 160);
        WatercolorSession s = new WatercolorSession();
        ModularBrush pen = first("Swell brush"), wash = first("Grey wash");
        draw(d, s, second(wash), 30, FloatChecks.line(20, 40, 60, 40, .8));
        draw(d, s, second(pen), 10, FloatChecks.line(20, 30, 60, 50, .8));
        draw(d, s, pen, 10, FloatChecks.line(20, 50, 60, 30, .8));          // first ink across: mixed shares in the piece
        draw(d, s, pen, 16, FloatChecks.line(150, 110, 220, 110, .8));      // first ink where a copy will land
        byte[][] before = allInks(d);
        byte[] piece = inksIn(d, 10, 15, 60, 50);
        check(between(d) > 20 && share(d, 10, 15, 70, 65) > 100, "a piece with second ink and mixed shares");
        FloatingSelection lifted = LassoPlanes.lift(d, box(10, 15, 70, 65), 4);
        check(Arrays.deepEquals(before, allInks(d)), "lifting leaves the page's ink");
        lifted.grab(FloatingSelection.MOVE); lifted.drag(FloatingSelection.MOVE, 0, 0, 100, 5); lifted.release();
        check(lifted.place(d), "the moved piece is laid");
        check(Arrays.equals(inksIn(d, 110, 20, 60, 50), piece), "every plane's ink shares land with their pixels, byte for byte");
        check(share(d, 10, 15, 70, 65) == -1 && Arrays.equals(inksIn(d, 10, 15, 60, 50), new byte[piece.length]), "where it was holds no paint and no ink");
        byte[][] moved = allInks(d);
        check(d.undo() && Arrays.deepEquals(before, allInks(d)), "Undo puts the ink back with the paint");
        check(d.redo() && Arrays.deepEquals(moved, allInks(d)), "Redo moves it again");
        // Laid over first ink: the share between, by the darkness each shows.
        FloatingSelection again = LassoPlanes.lift(d, box(110, 20, 170, 70), 4);
        long[] own = paint(d, 110, 20, 170, 70);
        again.grab(FloatingSelection.MOVE); again.drag(FloatingSelection.MOVE, 0, 0, 45, 65); again.release();
        long[] under = paint(d, 155, 85, 215, 135);
        check(under[0] > 5000 && under[1] == 0, "first ink where it lands");
        check(again.place(d), "laid over it");
        int mixed = share(d, 155, 85, 215, 135);
        check(mixed > 20 && mixed < own[1] / own[0] - 5, "over first ink the piece's share mixes down by the darkness under it: " + mixed + " for " + own[1] / own[0]);
        // A piece turned and scaled lays its ink where its paint goes.
        ToneDocument t = InkWashLayers.blank(240, 160);
        draw(t, s, second(pen), 12, FloatChecks.line(30, 40, 90, 40, .8));
        FloatingSelection turned = LassoPlanes.lift(t, box(20, 25, 100, 55), 4);
        turned.grab(FloatingSelection.MOVE); turned.drag(FloatingSelection.MOVE, 0, 0, 80, 60); turned.release();
        turned.angle = (float) Math.toRadians(40); turned.scale = 1.3f;
        check(turned.place(t) && paint(t, 0, 0, 240, 160)[0] > 5000 && share(t) == 255, "turned and scaled, all its paint is still second ink: " + share(t));
        // A plain page's piece lays no ink plane.
        ToneDocument p = InkWashLayers.blank(240, 160);
        draw(p, s, pen, 12, FloatChecks.line(30, 40, 90, 40, .8));
        FloatingSelection plainPiece = LassoPlanes.lift(p, box(20, 25, 100, 55), 4);
        plainPiece.grab(FloatingSelection.MOVE); plainPiece.drag(FloatingSelection.MOVE, 0, 0, 80, 60); plainPiece.release();
        check(plainPiece.place(p) && plain(p), "a page without second ink: the Lasso leaves it without ink shares");
        System.out.println("PASS: Second Ink: the Lasso lifts, moves and lays each pixel's ink share with it, every plane byte for byte, Undo and Redo too; laid over first ink it mixes by darkness (" + mixed + "); turned and scaled it stays second ink; a plain page stays plain");
    }

    /** A smaller page (crop) keeps each pixel's ink. */
    static void crop() {
        ToneDocument d = InkWashLayers.blank(240, 160);
        draw(d, new WatercolorSession(), second(first("Swell brush")), 12, FloatChecks.line(30, 40, 200, 120, .8));
        draw(d, new WatercolorSession(), first("Swell brush"), 12, FloatChecks.line(30, 120, 200, 40, .8));
        ToneDocument small = InkWashLayers.resized(d.layerSnapshot(), 120, 90, 60, 40), large = InkWashLayers.resized(d, 300, 200);
        byte[][] was = allInks(d), cut = allInks(small), grown = allInks(large);
        int set = 0;
        for (int k = 0; k < was.length; k++) {
            for (int y = 0; y < 90; y++) for (int x = 0; x < 120; x++) { check(cut[k][y * 120 + x] == was[k][(y + 40) * 240 + x + 60], "cropped: each pixel's ink as it was"); if (cut[k][y * 120 + x] != 0) set++; }
            for (int y = 0; y < 200; y++) for (int x = 0; x < 300; x++) check(grown[k][y * 300 + x] == (x < 240 && y < 160 ? was[k][y * 240 + x] : 0), "resized larger: each pixel's ink as it was, none on new paper");
        }
        check(set > 300 && between(small) > 0, "the crop holds ink, mixed shares too: " + set);
        check(plain(InkWashLayers.resized(InkWashLayers.blank(240, 160), 120, 90)), "a plain page cropped stays plain");
        System.out.println("PASS: Second Ink: a cropped and a resized page keep each pixel's ink share");
    }

    /** Merge mixes two layers' inks at each pixel by the darkness each shows. */
    static void merge() {
        ToneDocument d = InkWashLayers.blank(W, H);
        InkWashLayers.select(d, 0);
        wash(d, 255, 20, 20, 120, 60, 191);            // below: second ink, darkness 64
        wash(d, 255, 20, 80, 120, 120, 191);
        check(InkWashLayers.add(d, true), "a pair above");
        InkWashLayers.select(d, 1);
        wash(d, 0, 70, 20, 170, 60, 191);              // above: first ink, the same darkness, sheer
        d.begin(1, ToneDocument.KIND_PAINT); d.inking(0);
        for (int y = 80; y < 120; y++) for (int x = 70; x < 170; x++) d.paintTone(x, y, 0);   // above: opaque black
        d.finish();
        int[] shown = new int[W * H];
        for (int i = 0; i < W * H; i++) shown[i] = d.compositeTone(i % W, i / W);
        check(InkWashLayers.merge(d), "Merge");
        for (int i = 0; i < W * H; i++) check(Math.abs(d.compositeTone(i % W, i / W) - shown[i]) <= 1, "the page looks as it did");
        int below = share(d, 30, 30, 60, 50), both = share(d, 80, 30, 110, 50), above = share(d, 130, 30, 160, 50), covered = share(d, 80, 90, 110, 110);
        check(below == 255 && above == 0, "each layer's own paint keeps its ink: " + below + ", " + above);
        // Below shows through the sheer one above: 64 of darkness under a quarter cover against 64 over it.
        check(both > 95 && both < 125, "where both lie the share is between, by the darkness each shows: " + both);
        check(covered == 0, "under opaque first ink no second ink shows: " + covered);
        check(d.undo() && share(d, 30, 30, 60, 50) == 255, "Undo: the layers and their ink as they were");
        ToneDocument p = InkWashLayers.blank(W, H);
        wash(p, 0, 20, 20, 120, 60, 191); InkWashLayers.add(p, true); InkWashLayers.select(p, 1); wash(p, -1, 70, 20, 170, 60, 191);
        check(InkWashLayers.merge(p) && plain(p), "plain layers merge plain");
        System.out.println("PASS: Second Ink: Merge mixes the layers' inks at each pixel by the darkness each shows (" + both + " where equal darkness meets), none under opaque first ink; plain layers merge plain");
    }

    /** Light paint laid over a darker kind sinks that under it (ToneDocument.sinkUnder): what sinks keeps its ink. */
    static void sinks() {
        WatercolorSession s = new WatercolorSession();
        ToneDocument d = InkWashLayers.blank(LightOverDarkChecks.W, LightOverDarkChecks.H);
        InkWashLayers.select(d, 0);
        for (int y = LightOverDarkChecks.BY0; y <= LightOverDarkChecks.BY1; y += 6)
            PresetPictures.stroke(d, s, second(first("Steady line")), 14, 1, FloatChecks.line(LightOverDarkChecks.BX0, y, LightOverDarkChecks.BX1, y, .8));
        s.settleNow(d);
        check(share(d) == 255, "a second-ink block");
        byte[][] was = allInks(d);
        LightOverDarkChecks.across(d, s, "White chalk", .5);
        check(LightOverDarkChecks.band(d) > 60, "the chalk shows over it");
        long[] band = paint(d, 80, 95, 280, 105);
        check(band[0] > 0 && band[1] == band[0] * 255 && share(d) == 255, "what sank under the chalk is second ink still: " + share(d, 80, 95, 280, 105));
        check(d.undo() && Arrays.deepEquals(was, allInks(d)), "Undo: the ink where it was, every plane");
        System.out.println("PASS: Second Ink: paint that sinks under a light mark laid over it keeps its ink, Undo puts it back");
    }

    /**
     * What the finish rule costs paint that moves ({@code java SecondInkChecks cost}, not a check): a wash across a
     * 1404 × 1872 page, its stroke and whole drying timed, the best of 3: first ink on a plain page (no rule runs),
     * second ink, and first ink on a plane that holds second ink elsewhere.
     */
    static void cost() {
        ModularBrush[] brushes = {first("Soft wash"), first("Grey wash"), first("Wet gouache"), LaterChecks.with("Soft wash", PartChoice.DRYING, PartChoice.LATER_OFF, 1)};
        String[] names = {"Soft wash", "Grey wash", "Wet gouache", "Soft wash on Slow"};
        for (int k = 0; k < brushes.length; k++) {
            StringBuilder out = new StringBuilder("COST: " + names[k] + ", stroke + drying ms (ticks):");
            for (int mode = 0; mode < 3; mode++) {
                long best = Long.MAX_VALUE, bestStroke = 0; int ticks = 0;
                for (int run = 0; run < 4; run++) {
                    ToneDocument d = InkWashLayers.blank(1404, 1872);
                    WatercolorSession s = new WatercolorSession();
                    if (mode == 2) { draw(d, s, second(brushes[k]), 60, FloatChecks.line(200, 1700, 1200, 1700, .8)); s = new WatercolorSession(); }
                    ModularBrush b = mode == 1 ? second(brushes[k]) : brushes[k];
                    long t0 = System.nanoTime();
                    s.settleBefore(d, b);
                    CarrySmudgeChecks.feed(QuickStroke.start(d, b, 60, .8, s), FloatChecks.line(200, 300, 1200, 900, .8));
                    long t1 = System.nanoTime();
                    ticks = 0; while (s.tickDrying(d)) ticks++;
                    s.settleNow(d);
                    long t2 = System.nanoTime();
                    if (run > 0 && t2 - t1 < best) { best = t2 - t1; bestStroke = t1 - t0; }
                }
                out.append(new String[]{" plain ", "; second ink ", "; first ink on an inked plane "}[mode]).append(bestStroke / 1_000_000).append(" + ").append(best / 1_000_000).append(" (").append(ticks).append(')');
            }
            System.out.println(out);
        }
    }
}
