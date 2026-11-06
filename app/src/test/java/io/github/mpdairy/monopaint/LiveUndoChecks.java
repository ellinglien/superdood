package io.github.mpdairy.monopaint;

import java.util.*;

/**
 * Live pages, step 4 ({@link ToneDocument#takeBack}): your own stroke taken back where it is as you left it, a mark laid
 * over it since kept; one Undo step, which the page's own Undo and Redo travel as any; a change set lighter laid so.
 */
public final class LiveUndoChecks {
    public static void main(String[] args) {
        takeBack(); redo(); clear(); lighter(); wet();
        System.out.println("PASS: live undo: your stroke taken back but where another's mark lies over it, as one Undo step the page's Undo and Redo travel; the steps after a serial, oldest first; your Redo puts back exactly what your Undo took, never over a mark laid since, and a new change of yours clears it; your Clear page Undone puts back every layer's marks, kind planes and shares, theirs laid since kept, one page Undo step, and Redo clears again around theirs; a set change lightens, white to the paper, on every layer where lighter, its darker pixels inked over the layers kept (exact for your own Undo), one Undo step; "
                + "oil, a smear and a slow wash over ink and Water brush over Water pencil Undone while wet or after leave the page as before the stroke, live as the page's own Undo, and live Redo as the Undo found it");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static void takeBack() {
        ToneDocument page = new ToneDocument(300, 200);
        long seen = page.top();
        page.begin(); for (int x = 10; x < 290; x++) page.setTone(x, 50, 40); page.finish();   // yours, across tile edges
        List<Object> mine = page.stepsAfter(seen);
        check(mine.size() == 1, "one step of yours: " + mine.size());
        seen = page.top();
        PictureImport.lay(page, 100, 45, 10, 10, filled(10, 10));                              // theirs, darker, over part of it
        PictureImport.lay(page, 20, 80, 5, 5, filled(0, 5));                                   // theirs, elsewhere
        check(page.stepsAfter(seen).size() == 2 && page.stepsAfter(seen).get(0) != page.lastStep(), "their two steps after, oldest first");
        BitSet changed = new BitSet();
        check(page.takeBack(mine, changed), "taken back");
        check(page.tone(10, 50) == 255 && page.tone(289, 50) == 255 && page.tone(200, 50) == 255, "yours gone");
        check(page.tone(100, 50) == 10 && page.tone(109, 50) == 10, "under theirs, theirs stays");
        check(page.tone(100, 45) == 10 && page.tone(22, 82) == 0, "theirs kept");
        check(changed.get(50 * 300 + 10) && !changed.get(50 * 300 + 100) && changed.cardinality() == 270, "the pixels it changed: " + changed.cardinality());
        check(!page.takeBack(mine, new BitSet()), "again, nothing");
        check(page.undo() && page.tone(200, 50) == 40, "the page's Undo puts it back");
        check(page.redo() && page.tone(200, 50) == 255, "and Redo takes it again");
        check(page.undo() && page.undo() && page.undo() && page.undo() && page.inkless(), "and on back to blank");
    }

    /** Live Redo ({@link LiveOwn}): your Undo's change taken back where the page still shows what it left. */
    static void redo() {
        ToneDocument page = new ToneDocument(300, 200);
        LiveOwn own = new LiveOwn();
        long seen = page.top();
        page.begin(); for (int x = 10; x < 290; x++) page.setTone(x, 50, 40); page.finish();   // yours
        own.made(page.stepsAfter(seen));
        seen = page.top();
        page.begin(); for (int x = 10; x < 290; x++) page.setTone(x, 60, 80); page.finish();   // yours again
        own.made(page.stepsAfter(seen));
        PictureImport.lay(page, 100, 45, 10, 10, filled(10, 10));                              // theirs over the first
        int[] both = LiveUndoChecks.shown(page);
        BitSet bits = new BitSet();
        check(own.undo(page, bits) && page.tone(200, 60) == 255 && page.tone(200, 50) == 40, "Undo takes your newest");
        check(own.undo(page, new BitSet()) && page.tone(200, 50) == 255 && page.tone(105, 50) == 10, "and the one before, theirs kept");
        bits = new BitSet();
        check(own.redo(page, bits) && page.tone(200, 50) == 40 && page.tone(200, 60) == 255 && bits.cardinality() == 270, "Redo puts back the one before first: " + bits.cardinality());
        check(own.redo(page, new BitSet()) && Arrays.equals(both, LiveUndoChecks.shown(page)), "then the newest: the page exactly as before both Undos");
        check(!own.redo(page, new BitSet()), "nothing more to redo");
        check(own.undo(page, new BitSet()) && page.tone(200, 60) == 255, "Undo after Redo takes it again");
        // A guest's mark where your Undo left the paper: Redo puts back yours around it, never over it.
        PictureImport.lay(page, 150, 58, 10, 5, filled(200, 10));
        check(own.redo(page, new BitSet()), "Redo with theirs over part of it");
        check(page.tone(149, 60) == 80 && page.tone(160, 60) == 80 && page.tone(150, 60) == 200 && page.tone(159, 60) == 200 && page.tone(155, 58) == 200, "yours back around theirs, theirs kept: " + page.tone(155, 60));
        // A new change of yours clears Redo, a guest's doesn't.
        check(own.undo(page, new BitSet()), "Undo again");
        PictureImport.lay(page, 20, 120, 5, 5, filled(0, 5));
        int[] laid = LiveUndoChecks.shown(page);
        check(own.redo(page, new BitSet()) && page.tone(200, 60) == 80, "a guest's change leaves Redo");
        check(own.undo(page, new BitSet()), "Undo once more");
        seen = page.top();
        page.begin(); page.setTone(5, 150, 0); page.finish();
        own.made(page.stepsAfter(seen));
        int[] made = LiveUndoChecks.shown(page);
        check(!own.redo(page, new BitSet()) && Arrays.equals(made, LiveUndoChecks.shown(page)) && !Arrays.equals(laid, made), "a new change of yours clears Redo");
        check(own.undo(page, new BitSet()) && page.tone(5, 150) == 255, "and Undo takes the new change");
    }

    /**
     * The owner, 2026-11-03: "yes undo of a clear page should work". Live Undo of your Clear page puts back every layer's
     * marks, their kind planes and shares, a guest's mark laid since kept; Redo clears again, the guest's kept. Before, it
     * passed the clear by ({@link ToneDocument#takeBack} skipped a structural step) and nothing came back.
     */
    static void clear() {
        for (boolean whole : new boolean[]{false, true}) {
            ToneDocument page = InkWashLayers.blank(300, 200), plain = InkWashLayers.blank(300, 200);
            int ink = page.activeLayer();
            LiveOwn own = new LiveOwn();
            for (ToneDocument d : new ToneDocument[]{page, plain}) {
                long seen = d.top();
                d.selectLayer(0); d.begin(.4, ToneDocument.KIND_PAINT); for (int x = 10; x < 290; x++) for (int y = 40; y < 60; y++) d.setTone(x, y, 150); d.finish();   // a wash, partly permanent
                d.selectLayer(ink); d.begin(); for (int x = 10; x < 290; x++) d.setTone(x, 50, 40); d.finish();                                                         // ink over it
                d.begin(1, ToneDocument.KIND_PEN); for (int x = 10; x < 290; x++) d.setTone(x, 100, 0); d.finish();                                                   // pen on its plane
                if (d == page) own.made(d.stepsAfter(seen));
            }
            int[] drawn = shown(page);
            long seen = page.top();
            check(whole ? page.clearPage() : page.clearAllLayers(), "cleared");
            own.made(page.stepsAfter(seen));
            int[] cleared = shown(page);
            PictureImport.lay(page, 100, 45, 10, 10, filled(10, 10));   // theirs, after the clear, over your wash and ink
            PictureImport.lay(page, 20, 150, 5, 5, filled(0, 5));       // theirs, elsewhere
            int[] theirs = shown(page);
            BitSet bits = new BitSet();
            check(own.undo(page, bits), "live Undo of the clear");
            int[] back = shown(page);
            int off = 0;
            for (int y = 0; y < 200; y++) for (int x = 0; x < 300; x++) {
                boolean laid = x >= 100 && x < 110 && y >= 45 && y < 55 || x >= 20 && x < 25 && y >= 150 && y < 155;
                if (!laid && back[y * 300 + x] != drawn[y * 300 + x]) off++;
            }
            check(off == 0, "your marks back on every layer: " + off + " pixels off");
            check(page.compositeTone(105, 50) <= 10 && page.compositeTone(22, 152) == 0, "theirs kept");
            check(page.layerTone(105, 50) == 10 && page.activeLayer() == ink, "your ink not put back under theirs, the layer selected kept");
            check(bits.get(50 * 300 + 200) && bits.get(100 * 300 + 200) && !bits.get(150 * 300 + 22), "the pixels it changed: " + bits.cardinality());
            check(page.hasPlane(ink, ToneDocument.planeOf(ToneDocument.KIND_PEN)) && page.hasPlane(0, ToneDocument.planeOf(ToneDocument.KIND_PAINT)), "the kind planes back");
            // The wash's permanent share came back: erasing everything leaves what it leaves on the page never cleared.
            for (ToneDocument d : new ToneDocument[]{page, plain}) {
                d.selectLayer(0); d.beginPlane(ToneDocument.planeOf(ToneDocument.KIND_PAINT), false);
                byte[] all = new byte[300 * 200]; Arrays.fill(all, (byte) 1);
                d.eraseTones(0, 0, 300, 200, all); d.finish(); d.selectLayer(ink);
            }
            check(page.compositeTone(200, 45) == plain.compositeTone(200, 45) && page.compositeTone(200, 45) < 255, "the wash's share back: " + page.compositeTone(200, 45));
            check(page.undo() && Arrays.equals(back, shown(page)), "the erase undone");
            check(page.undo() && Arrays.equals(theirs, shown(page)), "the live Undo is one page Undo step");
            check(page.redo() && Arrays.equals(back, shown(page)), "and the page's Redo");
            check(own.redo(page, new BitSet()) && Arrays.equals(theirs, shown(page)), "live Redo clears again, theirs kept");
            check(own.undo(page, new BitSet()) && Arrays.equals(back, shown(page)), "and Undo again");
            check(!Arrays.equals(cleared, theirs), "theirs drawn");
        }
    }

    static void lighter() {
        ToneDocument page = new ToneDocument(40, 40);
        page.begin(); page.setTone(5, 5, 0); page.setTone(6, 5, 0); page.finish();
        PictureImport.lay(page, 5, 5, 2, 1, new int[]{254, -1}, false, true);
        check(page.tone(5, 5) == 255 && page.tone(6, 5) == 0, "set lighter, white the paper, clear left: " + page.tone(5, 5));
        PictureImport.lay(page, 6, 5, 1, 1, new int[]{254});
        check(page.tone(6, 5) == 0, "ink never lightens");
        // Marks on another layer than the one drawn on (a wash under ink): a set change makes the page show its greys.
        ToneDocument pair = InkWashLayers.blank(40, 40);
        int ink = pair.activeLayer();
        pair.selectLayer(0); pair.begin(); pair.setTone(5, 5, 0); pair.setTone(6, 5, 0); pair.setTone(7, 5, 0); pair.finish(); pair.selectLayer(ink);
        pair.begin(); pair.setTone(5, 5, 0); pair.finish();
        check(ink != 0 && PictureImport.lay(pair, 5, 5, 3, 1, new int[]{254, 128, -1}, false, true), "laid");
        check(pair.compositeTone(5, 5) == 255 && pair.compositeTone(6, 5) == 128 && pair.compositeTone(7, 5) == 0 && pair.activeLayer() == ink, "set on every layer, clear left, the layer drawn on kept: "
                + pair.compositeTone(5, 5) + " " + pair.compositeTone(6, 5));
        check(pair.undo() && pair.compositeTone(5, 5) == 0 && pair.compositeTone(6, 5) == 0, "one Undo step");
        // A set change's darker pixels (a guest's smudge: a few lighter, the rest darker; review 2026-11-02) go on as ink,
        // the wash under them kept; only the lighter ones erase every layer. Before, every pixel of it was erased.
        ToneDocument wash = InkWashLayers.blank(40, 40);
        wash.selectLayer(0); wash.begin(); for (int x = 10; x < 14; x++) wash.setTone(x, 5, 200); wash.finish(); wash.selectLayer(ink);
        check(PictureImport.lay(wash, 10, 5, 4, 1, new int[]{100, 254, 200, 0}, false, true), "laid");
        wash.selectLayer(0);
        int[] under = {wash.layerTone(10, 5), wash.layerTone(11, 5), wash.layerTone(12, 5), wash.layerTone(13, 5)};
        wash.selectLayer(ink);
        check(under[0] == 200 && under[2] == 200 && under[3] == 200 && under[1] == 255, "the wash kept under the darker and the same, erased under the lighter: " + Arrays.toString(under));
        check(wash.compositeTone(10, 5) < 200 && wash.compositeTone(11, 5) == 255 && wash.compositeTone(12, 5) == 200 && wash.compositeTone(13, 5) == 0
                && wash.layerTone(12, 5) == 255, "darker inked over it, lighter white, the same left: " + wash.compositeTone(10, 5));
        check(wash.undo() && wash.compositeTone(11, 5) == 200 && wash.compositeTone(10, 5) == 200 && wash.compositeTone(13, 5) == 200, "one Undo step");
        // Exact (the browser's own live Undo, WebEngine.picture): a darker grey erased and laid too, so it shows as it was.
        wash.selectLayer(ink);  // Undo selects the layer it changed
        check(PictureImport.lay(wash, 10, 5, 2, 1, new int[]{100, 200}, false, true, true), "laid exact");
        wash.selectLayer(0); int exactUnder = wash.layerTone(10, 5), keptUnder = wash.layerTone(11, 5); wash.selectLayer(ink);
        check(wash.compositeTone(10, 5) == 100 && exactUnder == 255 && keptUnder == 200, "exact: the darker shown as it is, the same left: " + wash.compositeTone(10, 5));
    }

    static int[] filled(int grey, int side) { int[] a = new int[side * side]; Arrays.fill(a, grey); return a; }

    /** The owner's Oil slot (Rob boss, ToolSlots), as on the Manta 2026-11-02, and the ink it went over (Verve felt). */
    static final String ROB_BOSS = "rob-boss.M99vleei0000000005e_2zg00_2llydzzn7_000_7ne_1ss0000000000000000nz0.2fu",
            FELT = "verve-felt.M30i0776o0q00u1_000zz0000920000000_0_d00_0_8i00000zz0000400000nz0.jjds6x";

    /**
     * The owner's case (2026-11-02, "it only undid the mark"): live Undo pressed while what a stroke left wet still
     * moves, or after it rests, leaves the page exactly as before the stroke, as one press of the page's own Undo does;
     * what was wet moving on after leaves it so. Before, wet paint's slices made the stroke's step anew, so live Undo took
     * back the stroke's first step, a ridge left where the paint had moved since.
     */
    static void wet() {
        Assets.install(Assets.folder(Assets.FOLDER));
        String oil = ROB_BOSS, smear = CarrySmudgeChecks.SMEAR, slow = FloatChecks.find("Slow ink wash").code,
                pencil = FloatChecks.find("Water pencil").code, water = FloatChecks.find("Water brush").code;
        for (int ticks : new int[]{0, 3, 2000}) {
            wet("oil over ink", ticks, new String[]{FELT, oil}, new double[]{7.5, 360});
            wet("a smear over ink", ticks, new String[]{FELT, smear}, new double[]{7.5, 30});
            wet("a slow wash over ink", ticks, new String[]{FELT, slow}, new double[]{7.5, 60});
            wet("Water brush over Water pencil", ticks, new String[]{pencil, water}, new double[]{7.5, 40});
        }
    }
    /** {@code codes} drawn at {@code px} as the drawing screen does, the last one Undone after {@code ticks} ticks, live and the page's own way. */
    static void wet(String what, int ticks, String[] codes, double[] px) {
        for (boolean live : new boolean[]{true, false}) {
            ToneDocument d = InkWashLayers.blank(320, 360);
            WatercolorSession s = new WatercolorSession();
            int[] before = null;
            LiveOwn own = new LiveOwn();   // as LiveProbe keeps them: the steps of each change
            long seen = d.top();
            for (int k = 0; k < codes.length; k++) {
                boolean last = k == codes.length - 1;
                if (last) { s.settleNow(d); before = shown(d); seen = d.top(); }
                DrawingStroke stroke = ReplayRun.stroke(d, codes[k], px[k], 1, s);
                // A loop down the page's middle, as the owner's: an ellipse, 20 by 60 px.
                for (int i = 0; i <= 120; i++) {
                    double a = i * Math.PI / 60;
                    stroke.sample((float) (160 + 20 * Math.sin(a) + (last ? 0 : 12 * (k + 1))), (float) (180 - 60 * Math.cos(a)), .7f, Float.NaN, Float.NaN, 1000 + 8L * i);
                    if (i % 3 == 2) stroke.present();
                }
                stroke.present(); stroke.finish();
            }
            own.made(d.stepsAfter(seen)); seen = d.top();                     // the change sent at the lift
            int n = 0; while (n < ticks && s.tickDrying(d)) n++;
            if (!s.settling()) own.made(d.stepsAfter(seen));                 // and when it settled
            int[] found = shown(d);
            check(!Arrays.equals(before, found), what + ": the stroke changed the page");
            if (live) check(own.undo(d, new BitSet()), what + ": live Undo took something back");
            else check(s.undo(d), what + ": Undo");
            for (int k = 0; k < 2000 && s.tickDrying(d); k++) { }
            int[] after = shown(d);
            int off = 0; for (int i = 0; i < after.length; i++) if (after[i] != before[i]) off++;
            check(off == 0, what + ", Undo after " + ticks + " ticks (" + (live ? "live" : "the page's own") + "): " + off + " pixels not as before the stroke");
            if (!live) continue;
            check(own.redo(d, new BitSet()), what + ": live Redo");
            for (int k = 0; k < 2000 && s.tickDrying(d); k++) { }
            after = shown(d);
            off = 0; for (int i = 0; i < after.length; i++) if (after[i] != found[i]) off++;
            check(off == 0, what + ", live Redo after " + ticks + " ticks: " + off + " pixels not as the Undo found them");
        }
    }
    static int[] shown(ToneDocument d) { int[] o = new int[d.width * d.height]; d.compositeTones(0, 0, d.width, d.height, o, 0, d.width); return o; }
}
