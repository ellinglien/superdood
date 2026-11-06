package io.github.mpdairy.monopaint;

import java.io.*;
import java.util.*;

/**
 * Live pages' probe ({@link LiveChange}): a change read back as written, clear pixels kept clear; a change larger than
 * a page, a short one and one with bytes after it refused.
 */
public final class LiveChangeChecks {
    public static void main(String[] args) throws IOException {
        if (args.length == 5 && args[0].equals("parts")) { parts(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4])); return; }
        roundTrip(); refused(); shown(); path(); set(); kinds(); areas(); live(); composite(); washSettles(); ticksReach(); hostParts(); layers();
        System.out.println("PASS: live change: written and read back the same, clear pixels clear, its path kept; the form without a path read as no path; larger than a page, cut short, too long a path or with bytes after it, refused; what the page shows read as a change, white kept white; a pen stroke's samples as a path; an Undo's change marked set, the rest not; only what differs from what was sent goes, cut to the box round it, set when lighter or not sent yet, a lost one sent again; a stroke's later part marked as joining the one before; its marks' kind kept, laid on its plane showing the grey sent, Show Kinds hiding it; areas apart sent as one change clear between them, cut to the page; the page's blocks written since last asked, runs joined; a block's tones as each pixel's; a Watercolour washy stroke and its settling sent change by change rebuild the page exactly; the owner's oil moving after the lift and a Fading mark's steps reach a guest as they go, batched, its page the host's in 16 greys; the Manta's own stroke sent in parts while drawn, of its kind, its page the guest's after each and after the lift, kind by kind; the layer a change was drawn on (1 and 3 round trip, 2 written as an older app writes it, none read as 2, bit 7 ignored) laid there, a one-pair page made three first, the guest's own layer in use kept, a layer it hides taking it all the same; a lightening change on one layer leaves the others' marks, the eraser's takes every layer; a layer's step never sent; the whole page for a guest joining a layer and a kind at a time, each mark on its layer; a change's layer its Undo steps' (Vector Ink's own pair), a part's the gesture's, and Vector Ink's change lands on the guest's top layer, under its own lines there");
    }

    static void path() throws IOException {
        int[] path = {0, 0, 0, 65535, 7, 12, 3, 4, 65535};
        LiveChange back = LiveChange.read(new LiveChange(5, 6, 2, 1, new int[]{-1, 9}, path).write());
        check(Arrays.equals(back.path, path) && Arrays.equals(back.greys, new int[]{-1, 9}), "path kept: " + Arrays.toString(back.path));
        check(LiveChange.read(deflated(new byte[]{0, 5, 0, 6, 0, 1, 0, 1, 9})).path.length == 0, "the form without a path: none");
        check(LiveChange.read(new LiveChange(0, 0, 1, 1, new int[]{9}).write()).path.length == 0, "none written as none");
        StrokeLog.Recorder r = new StrokeLog.Recorder("x", 4, 1);
        r.add(10.2f, 20.7f, .5f, Float.NaN, Float.NaN, 1000); r.add(-3, 30, .5f, Float.NaN, Float.NaN, 1016); r.add(12, 22, .5f, Float.NaN, Float.NaN, 1040);
        int[] samples = LiveChange.path(r.done(1));
        check(Arrays.equals(samples, new int[]{10, 21, 0, 0, 30, 16, 12, 22, 40}), "page pixels, ms since the first, off the page kept on it: " + Arrays.toString(samples));
        StrokeLog.Recorder longer = new StrokeLog.Recorder("x", 4, 1);
        for (int i = 0; i < LiveChange.MOST_SAMPLES * 2 + 1; i++) longer.add(i % 100, 0, .5f, Float.NaN, Float.NaN, 1000 + i);
        int[] kept = LiveChange.path(longer.done(1));
        check(kept.length / 3 <= LiveChange.MOST_SAMPLES && kept[5] == 3, "a long stroke: every third sample: " + kept.length / 3 + ", " + kept[5]);
    }
    static void set() throws IOException {
        LiveChange back = LiveChange.read(LiveChange.shown(0, 0, 2, 1, (x, y) -> x == 0 ? 255 : -1, LiveChange.NO_PATH, true).write());
        check(back.set && Arrays.equals(back.greys, new int[]{254, -1}), "set kept, white 254, −1 clear: " + Arrays.toString(back.greys));
        check(!LiveChange.read(new LiveChange(0, 0, 1, 1, new int[]{9}, new int[]{1, 2, 3}).write()).set, "a stroke's change isn't set");
        check(LiveChange.read(deflated(new byte[]{0, 0, 0, 0, 0, 1, 0, 1, 7, 0, 0, 1})).set, "a byte 1 after an empty path: set");
        check(!LiveChange.read(deflated(new byte[]{0, 0, 0, 0, 0, 1, 0, 1, 7})).set, "step 1's form: not set");
        refused(() -> LiveChange.read(deflated(new byte[]{0, 0, 0, 0, 0, 1, 0, 1, 7, 0, 0, 1, 1})), "bytes after the set byte");
        LiveChange part = LiveChange.read(new LiveChange(0, 0, 1, 1, new int[]{9}, LiveChange.NO_PATH, false, true).write());
        check(part.joins && !part.set, "a stroke's later part: joins, not set");
        LiveChange both = LiveChange.read(deflated(new byte[]{0, 0, 0, 0, 0, 1, 0, 1, 7, 0, 0, 3}));
        check(both.joins && both.set, "flags 3: a later part that lightens, joins and set");
        check(!LiveChange.read(deflated(new byte[]{0, 0, 0, 0, 0, 1, 0, 1, 7, 0, 0, 1})).joins, "an Undo's change doesn't join");
        LiveChange high = LiveChange.read(deflated(new byte[]{0, 0, 0, 0, 0, 1, 0, 1, 7, 0, 0, (byte) 0x80}));
        check(!high.set && !high.joins && high.kinds == 0 && high.layer == 2, "bit 7, none yet: ignored");
        since();
    }
    /** A change's marks' kind (the owner, 2026-11-05: Show Kinds did nothing to the Manta's marks in a browser): kept, and laid on its plane. */
    static void kinds() throws IOException {
        LiveChange pen = new LiveChange(0, 0, 1, 1, new int[]{9}, LiveChange.NO_PATH, false, true);
        pen.kinds = ToneDocument.KIND_PEN;
        LiveChange back = LiveChange.read(pen.write());
        check(back.kinds == ToneDocument.KIND_PEN && back.joins && !back.set, "a pen part's kind kept: " + back.kinds);
        check(LiveChange.read(deflated(new byte[]{0, 0, 0, 0, 0, 1, 0, 1, 7, 0, 0, 9})).kinds == ToneDocument.KIND_PAINT, "flags 9: set, Paint");
        LiveChange since = new LiveChange(0, 0, 1, 1, new int[]{9});
        since.kinds = ToneDocument.KIND_PENCIL;
        check(since.since(new byte[]{(byte) 255}, 1).kinds == ToneDocument.KIND_PENCIL, "kept going out");
        ToneDocument d = InkWashLayers.blank(4, 1);
        WebParity.lay(d, 0, 0, 3, 1, new int[]{128, 128, 128});                             // none: the base (as the browser and LiveProbe lay a change)
        WebParity.lay(d, 0, 0, 2, 1, new int[]{64, 64}, ToneDocument.KIND_PEN);             // over it, Pen
        WebParity.lay(d, 3, 0, 1, 1, new int[]{30}, ToneDocument.KIND_PAINT);               // Paint alone
        int[] all = new int[4], noPen = new int[4], noPaint = new int[4];
        d.compositeTones(0, 0, 4, 1, all, 0, 4);
        d.compositeTones(0, 0, 4, 1, noPen, 0, 4, EraseKind.withStep(0, 1, NotebookOptions.HIDDEN));
        d.compositeTones(0, 0, 4, 1, noPaint, 0, 4, EraseKind.withStep(0, 2, NotebookOptions.HIDDEN));
        check(Arrays.equals(all, new int[]{64, 64, 128, 30}), "laid on its plane, the grey sent shows: " + Arrays.toString(all));
        check(Arrays.equals(noPen, new int[]{128, 128, 128, 30}) && Arrays.equals(noPaint, new int[]{64, 64, 128, 255}), "Pen hidden: the base under it; Paint hidden: paper: " + Arrays.toString(noPen) + " " + Arrays.toString(noPaint));
    }
    /** What goes out: only what differs from what was sent, set when lighter (the eraser, Clear Page); laid ones taken in. */
    static void since() {
        byte[] room = new byte[3 * 2];
        Arrays.fill(room, (byte) 255);
        LiveChange first = new LiveChange(1, 0, 2, 2, new int[]{0, 254, 50, -1}).since(room, 3);
        check(first.set && Arrays.equals(first.greys, new int[]{0, 254, 50, -1}), "nothing sent yet: all of it, set (a guest may show darker)");
        check(new LiveChange(1, 0, 2, 2, new int[]{0, 254, 50, -1}).since(room, 3) == null, "again: nothing");
        LiveChange cleared = new LiveChange(1, 0, 2, 2, new int[]{254, 254, 254, 254}, new int[]{1, 2, 3}).since(room, 3);
        check(cleared.set && Arrays.equals(cleared.greys, new int[]{254, -1, 254, 254}) && cleared.path.length == 3, "cleared: what differs, set, its path kept: " + Arrays.toString(cleared.greys));
        check(new LiveChange(1, 0, 1, 1, new int[]{0}).since(room, 3).set == false && (room[1] & 255) == 0, "darker again: ink");
        new LiveChange(0, 1, 1, 1, new int[]{100}).into(room, 3);
        new LiveChange(1, 0, 1, 1, new int[]{100}).into(room, 3);
        check((room[3] & 255) == 100 && (room[1] & 255) == 0, "another's ink taken in only where darker");
        new LiveChange(1, 0, 1, 1, new int[]{200}, LiveChange.NO_PATH, true).into(room, 3);
        check((room[1] & 255) == 200, "another's set change taken in as it is");
        // A send the relay didn't take (review 2026-11-02): forgotten, so the next change there goes again, set.
        LiveChange erased = new LiveChange(0, 0, 2, 1, new int[]{254, 254}).since(room, 3);
        check(erased != null && erased.set && Arrays.equals(erased.greys, new int[]{254, 254}) && (room[1] & 255) == 254, "erased: sent, taken in");
        erased.forget(room, 3);
        check((room[0] & 255) == 255 && (room[1] & 255) == 255 && (room[2] & 255) == 254 && (room[3] & 255) == 100, "lost: its pixels not sent, the rest kept");
        LiveChange again = new LiveChange(0, 0, 3, 1, new int[]{254, 254, 254}).since(room, 3);
        check(again != null && again.set && again.x == 0 && again.w == 2 && Arrays.equals(again.greys, new int[]{254, 254}), "the next change there sends them again, set, cut to them: " + (again == null ? null : Arrays.toString(again.greys)));
    }
    static void areas() throws IOException {
        int[][] page = {{0, 255, 128, 9}, {255, 255, 3, 7}};
        LiveChange.Tones tones = (x, y, w, h, out, offset, stride) -> { for (int r = 0; r < h; r++) for (int c = 0; c < w; c++) out[offset + r * stride + c] = page[y + r][x + c]; };
        LiveChange c = LiveChange.shown(Arrays.asList(new int[]{0, 0, 1, 1}, new int[]{2, 1, 9, 9}), 4, 2, tones, new int[]{1, 2, 3});
        check(c.x == 0 && c.y == 0 && c.w == 4 && c.h == 2, "the box round them, cut to the page: " + c.w + "×" + c.h);
        check(Arrays.equals(c.greys, new int[]{0, -1, -1, -1, -1, -1, 3, 7}), "their tones, clear between: " + Arrays.toString(c.greys));
        check(Arrays.equals(LiveChange.read(c.write()).greys, c.greys) && c.path.length == 3, "read back so, its path kept");
        check(Arrays.equals(LiveChange.shown(Collections.singletonList(new int[]{1, 0, 2, 2}), 4, 2, tones, LiveChange.NO_PATH).greys, new int[]{254, 254}), "white 254");
        check(LiveChange.shown(Arrays.asList(new int[]{4, 0, 6, 2}, new int[]{1, 1, 1, 2}), 4, 2, tones, LiveChange.NO_PATH) == null, "none on the page: none");
    }
    /** {@link ToneDocument#takeLive}: nothing until asked once, then each block written, runs joined down the rows, then none. */
    static void live() {
        ToneDocument page = NotebookFixture.sketch(160, 120, 3);
        page.selectLayer(InkWashLayers.INK);
        page.begin(); page.setTone(5, 5, 0); page.finish();
        check(page.takeLive().isEmpty(), "not kept until asked");
        int B = ToneDocument.LIVE_BLOCK;
        page.begin(); for (int y = 20; y < 50; y++) for (int x = 40; x < 60; x++) page.setTone(x, y, 0); page.setTone(150, 110, 9); page.finish();
        List<int[]> areas = page.takeLive();
        check(areas.size() == 2 && Arrays.equals(areas.get(0), new int[]{2 * B, B, 4 * B, 4 * B}) && Arrays.equals(areas.get(1), new int[]{9 * B, 6 * B, 160, 7 * B}),
                "a run of blocks joined down its rows, a lone one cut to the page: " + Arrays.deepToString(areas.toArray()));
        check(page.takeLive().isEmpty(), "taken: none left");
    }
    /** {@link ToneDocument#compositeTones} as {@link ToneDocument#compositeTone} a pixel at a time: layers, opacity, a hidden one, guides, a shade. */
    static void composite() {
        ToneDocument page = NotebookFixture.sketch(160, 120, 3);
        page.selectLayer(InkWashLayers.INK); page.begin(); for (int x = 0; x < 160; x += 3) page.setTone(x, 60, x); page.finish();
        int w = 50, h = 40, stride = 60;
        for (int round = 0; round < 3; round++) {
            if (round == 1) { page.setLayerOpacity(InkWashLayers.WASH, 55); page.setPaper(230); }
            if (round == 2) { byte[] guides = new byte[160 * 120]; Arrays.fill(guides, (byte) 255); for (int i = 0; i < guides.length; i += 7) guides[i] = 100; page.placeGuides(guides); page.setLayerVisible(InkWashLayers.INK, false); }
            int[] out = new int[stride * h + 5];
            Arrays.fill(out, -9);
            page.compositeTones(100, 50, w, h, out, 5, stride);
            for (int r = 0; r < h; r++) for (int c = 0; c < stride; c++) {
                int o = 5 + r * stride + c, want = c < w ? page.compositeTone(100 + c, 50 + r) : -9;
                check(out[o] == want, "round " + round + " at " + c + "," + r + ": " + out[o] + ", not " + want);
            }
        }
    }
    /**
     * A Watercolour washy stroke with pressure and its wet paint settling after, sent as the Manta sends them (each
     * present, the lift and every tick a change of {@link ToneDocument#takeLive}'s blocks): laid in turn on the page
     * as it was, they make the page as it ends, to the pixel. (2026-10-31: a settling's change, clear where the settling
     * changed nothing, looked like holes when drawn alone; laid in turn, none.)
     */
    static void washSettles() {
        String[] slot = ToolSlots.FIRST[5];
        ToneDocument page = InkWashLayers.blank(600, 360);
        page.takeLive();
        int w = page.width, h = page.height;
        int[] laid = new int[w * h], now = new int[w * h];
        page.compositeTones(0, 0, w, h, laid, 0, w);
        int[] changes = {0};
        Runnable send = () -> {
            LiveChange c = LiveChange.shown(page.takeLive(), w, h, page::compositeTones, LiveChange.NO_PATH);
            if (c == null) return;
            changes[0]++;
            for (int i = 0; i < c.greys.length; i++) if (c.greys[i] >= 0) laid[(c.y + i / c.w) * w + c.x + i % c.w] = c.greys[i];
        };
        WatercolorSession s = new WatercolorSession();
        ModularBrush b = ModularCode.decode(slot[2]);
        s.settleBefore(page, b); s.dry("stroke");
        DrawingStroke st = QuickStroke.start(page, b, Double.parseDouble(slot[3]), Double.parseDouble(slot[4]), s);
        for (int i = 0; i <= 120; i++) {
            double u = i / 120.0;
            st.sample((float) (60 + 480 * u), (float) (180 + 90 * Math.sin(2 * Math.PI * u)), ToolSettings.pressure((float) (.1 + .9 * Math.sin(Math.PI * u))), 10, 20, 4 * i);
            if (i % 3 == 0) { st.present(); send.run(); }
        }
        st.finish(); send.run();
        int lift = changes[0];
        for (int k = 0; k < 2000 && s.tickDrying(page); k++) send.run();
        send.run();
        page.compositeTones(0, 0, w, h, now, 0, w);
        int off = 0;
        for (int i = 0; i < now.length; i++) if (Math.min(254, now[i]) != Math.min(254, laid[i])) off++;
        check(changes[0] > lift, "it settles after the lift: " + changes[0] + " changes, " + lift + " by the lift");
        check(off == 0, "the changes laid in turn are the page: " + off + " pixels off");
    }
    /**
     * The owner's case (2026-11-02, Rob boss over ink with a browser guest): what the page does on its own after the lift
     * reaches a guest while it goes on, as the drawing screen sends it: the lift, then what the ticks showed (DryingTicker's
     * {@code show}) at most every {@link LiveChange#TICKS_MS} of their time, then the rest; a Fading mark's steps so too. The
     * guest's page (laid as the browser lays: ink only where darker, set where lighter) is the host's within the guest's 16
     * greys at every send and at the end. Before, only the lift and the rest went: halfway through the oil's ticks the
     * guest's page was the lift's (thousands of pixels off).
     */
    static void ticksReach() {
        Assets.install(Assets.folder(Assets.FOLDER));
        ToneDocument host = InkWashLayers.blank(320, 360), guest = InkWashLayers.blank(320, 360);
        host.takeLive();
        byte[] room = new byte[host.width * host.height];
        Arrays.fill(room, (byte) 255);
        int[] sends = {0};
        Runnable send = () -> {
            LiveChange c = LiveChange.shown(host.takeLive(), host.width, host.height, host::compositeTones, LiveChange.NO_PATH);
            if (c != null) c = c.since(room, host.width);
            if (c == null) return;
            sends[0]++;
            PictureImport.lay(guest, c.x, c.y, c.w, c.h, c.greys.clone(), false, c.set);
        };
        WatercolorSession s = new WatercolorSession();
        String[] codes = {LiveUndoChecks.FELT, LiveUndoChecks.ROB_BOSS};
        double[] px = {7.5, 360};
        for (int k = 0; k < codes.length; k++) {
            DrawingStroke stroke = ReplayRun.stroke(host, codes[k], px[k], 1, s);
            for (int i = 0; i <= 120; i++) {
                double a = i * Math.PI / 60;
                stroke.sample((float) (160 + 20 * Math.sin(a) + (k == 0 ? 12 : 0)), (float) (180 - 60 * Math.cos(a)), .7f, Float.NaN, Float.NaN, 1000 + 8L * i);
                if (i % 3 == 2) stroke.present();
            }
            stroke.present(); stroke.finish();
            send.run();  // the lift (PaintActivity.recovery)
        }
        int lift = sends[0], worst = 0, ticks = 0, oldWay = -1;
        boolean[] shown = {false};
        long time = 0, sent = 0;
        int[] atLift = LiveUndoChecks.shown(guest);
        for (long next; (next = s.tick(host, () -> shown[0] = true)) >= 0 && ticks < 100_000; ticks++) {
            time += Math.max(1, next);
            if (ticks == 40) oldWay = off(LiveUndoChecks.shown(host), atLift);  // only the lift and the rest sent: the guest still at the lift
            if (shown[0] && time - sent >= LiveChange.TICKS_MS) { shown[0] = false; sent = time; send.run(); worst = Math.max(worst, off(LiveUndoChecks.shown(host), LiveUndoChecks.shown(guest))); }
        }
        send.run();  // at rest (DryingTicker's settled)
        int end = off(LiveUndoChecks.shown(host), LiveUndoChecks.shown(guest));
        check(ticks > 40 && oldWay > 1000, "the oil moves on after the lift: " + ticks + " ticks, " + oldWay + " px off the lift's page 40 ticks in");
        check(sends[0] > lift + 2 && worst == 0 && end == 0, "its ticks reach the guest as they go, batched (" + (sends[0] - lift) + " changes for " + ticks + " ticks): "
                + worst + " px off at worst, " + end + " at rest");
        // A Fading mark's steps (Later's, by the clock) reach the guest as they show.
        FadingChecks.clock = 1_000_000;
        ToneDocument faded = FadingChecks.drawn(FadingChecks.brush(PartChoice.FADE, 20)), copy = InkWashLayers.blank(faded.width, faded.height);
        int[] all = LiveUndoChecks.shown(faded);
        PictureImport.lay(copy, 0, 0, faded.width, faded.height, all, false, true);
        faded.takeLive();
        int[] steps = {0}, fadeOff = {0};
        for (long t = FadingChecks.clock; t <= FadingChecks.clock + 26_000; t += 1000) faded.fade(t, () -> {
            steps[0]++;
            LiveChange c = LiveChange.shown(faded.takeLive(), faded.width, faded.height, faded::compositeTones, LiveChange.NO_PATH);
            if (c != null) PictureImport.lay(copy, c.x, c.y, c.w, c.h, c.greys.clone(), false, true);
            fadeOff[0] = Math.max(fadeOff[0], off(LiveUndoChecks.shown(faded), LiveUndoChecks.shown(copy)));
        });
        check(steps[0] > 3 && fadeOff[0] == 0 && !faded.fading(), "a Fading mark's " + steps[0] + " steps reach the guest as they show: " + fadeOff[0] + " px off at worst");
    }
    /**
     * The Manta's own stroke reaches a guest while it is drawn (the owner, 2026-11-05: "FROM the manta, it's still just on
     * pen lift"), as LiveProbe.drawing sends it: every {@link LiveChange#PART_MS} of the pen's time what the page shows
     * where it was written since, a {@link LiveChange#part} (no path, the later ones joining), only what differs from what
     * was sent; the lift's rest its last part. A guest laying them as they arrive (written and read back; ink only where
     * darker, set as they are) shows the host's page after every part and after the lift, to the pixel; each part of the
     * stroke's kind, and laid on that kind's plane the guest's page is the host's with each kind hidden too: a pencil, a felt,
     * a watercolour wash and an oil. Prints what reading a part costs the drawing thread.
     */
    static void hostParts() throws IOException {
        Assets.install(Assets.folder(Assets.FOLDER));
        List<Long> cost = new ArrayList<>();
        for (int slot : new int[]{0, 2, 5, 6}) {
            int w = 640, h = 360;
            byte[] guest = new byte[w * h];
            Arrays.fill(guest, (byte) 254);  // the page as sent whole (LiveProbe.shown): blank, white as 254
            int[] parts = {0}, worst = {0}, kind = {-1};
            boolean[] form = {true};
            ToneDocument laid = InkWashLayers.blank(w, h);  // a guest's page, each change on its kind's plane (WebParity.lay, as LiveProbe and the browser lay one)
            ToneDocument host = hostStroke(ToolSlots.FIRST[slot], w, h, 80, 180, (c, page, lift) -> {
                LiveChange got = LiveChange.read(c.write());
                form[0] &= got.path.length == 0 && got.joins == (parts[0] > 0) && (kind[0] < 0 || got.kinds == kind[0]);
                kind[0] = got.kinds;
                got.into(guest, w);
                if (got.set) PictureImport.lay(laid, got.x, got.y, got.w, got.h, got.greys.clone(), false, true, false, got.kinds);
                else WebParity.lay(laid, got.x, got.y, got.w, got.h, got.greys.clone(), got.kinds);
                if (!lift) parts[0]++;
                worst[0] = Math.max(worst[0], differ(guest, LiveUndoChecks.shown(page)));
            }, cost);
            int end = differ(guest, LiveUndoChecks.shown(host)), planes = 0;
            // Kind by kind: each of Pen, Paint and Dry Media hidden (Show Kinds), the guest's page the host's.
            for (int step = 1; step <= 3; step++) {
                int hidden = EraseKind.withStep(0, step, NotebookOptions.HIDDEN);
                int[] a = new int[w * h], b = new int[w * h];
                host.compositeTones(0, 0, w, h, a, 0, w, hidden); laid.compositeTones(0, 0, w, h, b, 0, w, hidden);
                planes += off(a, b);
            }
            int lit = off(LiveUndoChecks.shown(host), LiveUndoChecks.shown(laid));
            check(parts[0] >= 5 && form[0] && kind[0] > 0 && worst[0] == 0 && end == 0 && lit == 0 && planes == 0, ToolSlots.FIRST[slot][1] + ": " + parts[0] + " parts before the lift (no path, the later joining, all of kind " + kind[0] + ": " + form[0]
                    + "), the guest " + worst[0] + " px off the host at worst while drawn, " + end + " after the lift; laid on its kind's plane " + lit + " px off, " + planes + " with a kind hidden");
        }
        Collections.sort(cost);
        System.out.printf("  a part read on the drawing thread (takeLive, the box's tones, what differs): median %.2f ms, worst %.2f ms over %d parts%n",
                cost.get(cost.size() / 2) / 1e6, cost.get(cost.size() - 1) / 1e6, cost.size());
    }
    /** Pixels of {@code guest} (bytes) not {@code tones} (white as 254). */
    private static int differ(byte[] guest, int[] tones) {
        int n = 0;
        for (int i = 0; i < tones.length; i++) if ((guest[i] & 255) != Math.min(254, tones[i])) n++;
        return n;
    }
    interface Sent { void sent(LiveChange c, ToneDocument host, boolean lift) throws IOException; }
    /**
     * A stroke of {@code slot} ({@link ToolSlots#FIRST}'s) drawn on a blank page {@code w}×{@code h} from {@code x0},
     * {@code y0}, a sample every 8 ms, shown every third, its changes as LiveProbe sends them (from a room of the page
     * sent whole): each part while drawn and the lift's rest to {@code sent}; the nanoseconds each part's reading took
     * into {@code cost}. The page as it ends.
     */
    static ToneDocument hostStroke(String[] slot, int w, int h, int x0, int y0, Sent sent, List<Long> cost) throws IOException {
        ToneDocument host = InkWashLayers.blank(w, h);
        host.takeLive();
        byte[] room = new byte[w * h];
        Arrays.fill(room, (byte) 254);
        WatercolorSession s = new WatercolorSession();
        DrawingStroke stroke = ReplayRun.stroke(host, slot[2], Double.parseDouble(slot[3]), Double.parseDouble(slot[4]), s);
        int parts = 0, kinds = new StrokeLog.Recorder(slot[2], Double.parseDouble(slot[3]), Double.parseDouble(slot[4])).medium();
        long partAt = -LiveChange.PART_MS;
        for (int i = 0; i <= 120; i++) {
            double u = i / 120.0;
            long ms = 8L * i;
            stroke.sample((float) (x0 + 480 * u), (float) (y0 + 60 * Math.sin(2 * Math.PI * u)), ToolSettings.pressure((float) (.2 + .7 * Math.sin(Math.PI * u))), Float.NaN, Float.NaN, 1000 + ms);
            if (i % 3 != 2 || ms - partAt < LiveChange.PART_MS) continue;
            stroke.present();
            partAt = ms;
            long t = System.nanoTime();
            LiveChange c = LiveChange.shown(host.takeLive(), w, h, host::compositeTones, LiveChange.NO_PATH);
            if (c != null) { c.kinds = kinds; c = c.part(parts > 0).since(room, w); }
            cost.add(System.nanoTime() - t);
            if (c != null) { parts++; sent.sent(c, host, false); }
        }
        stroke.present(); stroke.finish();
        LiveChange c = LiveChange.shown(host.takeLive(), w, h, host::compositeTones, LiveChange.NO_PATH);
        if (c != null) { c.kinds = kinds; c = c.part(parts > 0).since(room, w); }
        if (c != null) sent.sent(c, host, true);
        return host;
    }
    /**
     * live-check.cjs's stand-in Manta drawing (a Verve felt stroke on a blank page {@code w}×{@code h} from {@code x0},
     * {@code y0}): one line of JSON, its changes as sent (each written, base64) and the box they cover with the host's
     * greys there as a guest shows them (white as 254).
     */
    static void parts(int w, int h, int x0, int y0) throws IOException {
        Assets.install(Assets.folder(Assets.FOLDER));
        List<String> sent = new ArrayList<>();
        int[] box = {w, h, 0, 0};
        ToneDocument host = hostStroke(ToolSlots.FIRST[2], w, h, x0, y0, (c, page, lift) -> {
            sent.add("\"" + Base64.getEncoder().encodeToString(c.write()) + "\"");
            box[0] = Math.min(box[0], c.x); box[1] = Math.min(box[1], c.y); box[2] = Math.max(box[2], c.x + c.w); box[3] = Math.max(box[3], c.y + c.h);
        }, new ArrayList<>());
        int bw = box[2] - box[0], bh = box[3] - box[1];
        int[] tones = new int[bw * bh];
        host.compositeTones(box[0], box[1], bw, bh, tones, 0, bw);
        byte[] greys = new byte[tones.length];
        for (int i = 0; i < tones.length; i++) greys[i] = (byte) Math.min(254, tones[i]);
        System.out.println("{\"parts\":[" + String.join(",", sent) + "],\"box\":[" + box[0] + "," + box[1] + "," + bw + "," + bh + "],\"greys\":\"" + Base64.getEncoder().encodeToString(greys) + "\"}");
    }
    /** Pixels whose tones differ by more than the guest's 16 greys round away (a 17 step). */
    static int off(int[] a, int[] b) { int n = 0; for (int i = 0; i < a.length; i++) if (Math.abs(a[i] - b[i]) > 8 && Math.round(a[i] / 17f) != Math.round(b[i] / 17f)) n++; return n; }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /**
     * The three layers (2026-11-06): a change says the layer it was drawn on and lands there, wherever the guest draws.
     * Laid as the browser lays one (WebEngine.lay) and the Manta (LiveProbe.laySlice): {@link #layOn}.
     */
    static void layers() throws IOException {
        for (int l = 1; l <= 3; l++) {
            LiveChange c = new LiveChange(0, 0, 1, 1, new int[]{9}, LiveChange.NO_PATH, true, false);
            c.kinds = ToneDocument.KIND_PEN; c.layer = l;
            LiveChange back = LiveChange.read(c.write());
            check(back.layer == l && back.kinds == ToneDocument.KIND_PEN && back.set, "layer " + l + " read back: " + back.layer);
        }
        check(inflated(new LiveChange(0, 0, 1, 1, new int[]{9}).write()).length == 11, "layer 2 written as an older app writes it: no flags");
        LiveChange three = new LiveChange(0, 0, 1, 1, new int[]{9}); three.layer = 3;
        byte[] raw = inflated(three.write());
        check(raw.length == 12 && raw[11] == 3 << 5, "layer 3 in the flags' bits 5 and 6: " + raw[raw.length - 1]);
        check(LiveChange.read(deflated(new byte[]{0, 0, 0, 0, 0, 1, 0, 1, 7, 0, 0, 1})).layer == 2 && LiveChange.read(deflated(new byte[]{0, 0, 0, 0, 0, 1, 0, 1, 7})).layer == 2, "an older app's change: layer 2");
        check(three.part(true).layer == 3 && three.since(new byte[]{(byte) 255}, 1).layer == 3, "kept as a part and going out");

        // A one-pair page receiving a layer 3 change: three pairs first, the mark on the top one, its own layer 2 kept.
        ToneDocument guest = InkWashLayers.blank(6, 1);
        layOn(guest, change(3, false, -1, -1, -1, -1, -1, 0));
        check(InkWashLayers.pairs(guest) == 3 && InkWashLayers.layer(guest) == 2, "made three pairs, its layer in use kept: " + InkWashLayers.pairs(guest) + ", " + InkWashLayers.layer(guest));
        check(marked(guest, 2) && !marked(guest, 1) && !marked(guest, 0), "the mark on layer 3");
        // Its own layer 3 picked: an older app's change (none: layer 2) on layer 2, a layer 1 change on layer 1, its own 3 kept.
        InkWashLayers.pickLayer(guest, 3);
        layOn(guest, change(2, false, -1, -1, 60, -1, -1, -1));
        layOn(guest, change(1, false, 100, 100, -1, -1, -1, -1));
        check(marked(guest, 1) && marked(guest, 0) && InkWashLayers.layer(guest) == 3, "each on its layer, its own kept");
        int[] sees = PictureImport.liveTones(guest, 0, 0, 6, 1), one = tonesUpTo(guest, 0);
        check(Arrays.equals(sees, new int[]{100, 100, 60, 255, 255, 0}) && Arrays.equals(one, new int[]{100, 100, 255, 255, 255, 255}), "showing what was sent, layer 1 its own: " + Arrays.toString(sees) + " " + Arrays.toString(one));
        // A layer the guest hides takes a change all the same and stays hidden: a layer's step is each screen's own.
        InkWashLayers.setStep(guest, 1, NotebookOptions.HIDDEN);
        layOn(guest, change(1, false, -1, -1, -1, 30, -1, -1));
        check(tonesUpTo(guest, 0)[3] == 30 && InkWashLayers.stepOf(guest, 1) == NotebookOptions.HIDDEN, "laid on a hidden layer, still hidden");
        InkWashLayers.setStep(guest, 1, NotebookOptions.FULL);

        // A lightening change of layer 3's (its Lasso moved away, an Undo of its stroke): layer 1 under it stays; the
        // eraser's (white) takes every layer. Before, every layer went where it lightened, layer 1's grey moved to layer 3.
        layOn(guest, change(3, false, 0, -1, -1, -1, -1, -1));
        check(PictureImport.liveTones(guest, 0, 0, 6, 1)[0] == 0 && tonesUpTo(guest, 0)[0] == 100, "layer 3's black over layer 1's grey");
        layOn(guest, change(3, true, 100, 254, -1, -1, -1, -1));
        int[] after = PictureImport.liveTones(guest, 0, 0, 6, 1), under = tonesUpTo(guest, 0);
        check(after[0] == 100 && under[0] == 100 && after[1] == 255 && under[1] == 255, "layer 1 kept under a lightening of layer 3, the eraser everything: " + Arrays.toString(after) + " " + Arrays.toString(under));

        // A layer's step is never sent: hiding or fading a layer changes nothing that goes.
        ToneDocument host = InkWashLayers.blank(4, 1);
        InkWashLayers.pickLayer(host, 3);
        layOn(host, change(3, false, 0, -1, -1, -1));
        layOn(host, change(1, false, -1, 80, -1, -1));
        byte[] room = new byte[4];
        Arrays.fill(room, (byte) 255);
        LiveChange.shown(Collections.singletonList(new int[]{0, 0, 4, 1}), 4, 1, host::liveTones, LiveChange.NO_PATH).since(room, 4);
        InkWashLayers.setStep(host, 3, NotebookOptions.HIDDEN); InkWashLayers.setStep(host, 1, NotebookOptions.HALF);
        int[] screen = new int[4];
        host.compositeTones(0, 0, 4, 1, screen, 0, 4);
        check(screen[0] == 255 && screen[1] > 80, "the host's screen hides and fades them: " + Arrays.toString(screen));
        check(LiveChange.shown(Collections.singletonList(new int[]{0, 0, 4, 1}), 4, 1, host::liveTones, LiveChange.NO_PATH).since(room, 4) == null, "nothing goes for it");

        // A guest joining: the whole page a layer and a kind at a time, each mark on its layer, the page as the host's.
        ToneDocument joins = InkWashLayers.blank(4, 1);
        Arrays.fill(room, (byte) 255);
        List<Integer> sent = new ArrayList<>();
        LiveChange.whole(host, (c, last) -> { LiveChange d = c.since(room, 4); if (d != null) { sent.add(d.layer); layOn(joins, d); } });
        check(sent.contains(1) && sent.contains(3) && InkWashLayers.pairs(joins) == 3 && InkWashLayers.layer(joins) == 2, "layers 1 and 3 sent as such: " + sent);
        check(Arrays.equals(PictureImport.liveTones(joins, 0, 0, 4, 1), PictureImport.liveTones(host, 0, 0, 4, 1)) && marked(joins, 0) && marked(joins, 2) && !marked(joins, 1),
                "its page the host's, layers 1 and 3 its own: " + Arrays.toString(PictureImport.liveTones(joins, 0, 0, 4, 1)));

        // The layer a change was drawn on: its Undo steps' (a stroke on layer 1 while layer 3 is picked again after), else the
        // layer in use (a part while drawn: its gesture's). Vector Ink draws on its own pair, the top one, then the pair picked.
        WatercolorSession session = new WatercolorSession();
        ToneDocument vi = InkWashLayers.blank(200, 120);
        VectorInkChecks.draw(vi, session, VectorInkChecks.preset("Steady line"), 6, 1, new float[][]{{20, 100}, {30, 30}, {.5f, .5f}});
        long top = vi.top();
        VectorInkChecks.draw(vi, session, VectorInkChecks.vector(), 6, 1, new float[][]{{20, 100, 180}, {60, 60, 60}, {.5f, .5f, .5f}});
        int drawnOn = LiveChange.layerOf(vi, vi.stepsAfter(top));
        check(InkWashLayers.pairs(vi) == 2 && drawnOn == 3 && LiveChange.layerOf(vi, Collections.emptyList()) == 2, "Vector Ink on the pair put on top: layer 3, the layer in use 2: " + drawnOn);
        LiveChange line = LiveChange.shown(Collections.singletonList(new int[]{0, 40, 200, 80}), 200, 120, vi::liveTones, LiveChange.NO_PATH);
        line.layer = drawnOn;
        ToneDocument plain = InkWashLayers.blank(200, 120);
        layOn(plain, line);
        check(InkWashLayers.pairs(plain) == 3 && marked(plain, 2) && plain.compositeTone(100, 60) < 128, "a guest's top layer, as on the host");
        // A guest whose top layer holds Vector Ink lines of its own: the change goes under them, as the host's own pen's.
        ToneDocument lined = InkWashLayers.blank(200, 120);
        InkWashLayers.pickLayer(lined, 3);
        VectorInkChecks.draw(lined, session, VectorInkChecks.vector(), 6, 1, new float[][]{{20, 180}, {100, 100}, {.5f, .5f}});
        int lines = VectorInk.lined(lined);
        layOn(lined, line);
        check(lined.compositeTone(100, 60) < 128 && lined.lines(lines) != null && VectorInkChecks.drawn(lined, lines), "under the guest's own lines, their layer theirs alone");
    }
    /** Change {@code c} laid as WebEngine.lay and LiveProbe.laySlice lay one: on its layer ({@link InkWashLayers#drawOn}). */
    static void layOn(ToneDocument d, LiveChange c) {
        int[] greys = c.greys.clone();
        InkWashLayers.drawOn(d, c.layer, () -> {
            if (c.set) PictureImport.lay(d, c.x, c.y, c.w, c.h, greys, false, true, false, c.kinds);
            else WebParity.lay(d, c.x, c.y, c.w, c.h, greys, c.kinds);
        });
    }
    /** A change on {@code layer}, {@code set}, of these greys (−1 clear), one row from the page's top left. */
    static LiveChange change(int layer, boolean set, int... greys) { LiveChange c = new LiveChange(0, 0, greys.length, 1, greys, LiveChange.NO_PATH, set); c.layer = layer; return c; }
    static boolean marked(ToneDocument d, int pair) { return d.layerMarked(InkWashLayers.washOf(d, pair)) || d.layerMarked(InkWashLayers.inkOf(d, pair)); }
    /** A one-row page as every screen shares it up to pair {@code pair}. */
    static int[] tonesUpTo(ToneDocument d, int pair) { int[] out = new int[d.width]; d.liveTones(0, 0, d.width, 1, out, 0, d.width, pair, 0); return out; }
    static byte[] inflated(byte[] bytes) throws IOException {
        try (java.util.zip.InflaterInputStream in = new java.util.zip.InflaterInputStream(new ByteArrayInputStream(bytes))) { return in.readAllBytes(); }
    }

    static void roundTrip() throws IOException {
        int[] greys = {-1, 0, 128, 247, -1, 30};
        LiveChange back = LiveChange.read(new LiveChange(1900, 2550, 3, 2, greys).write());
        check(back.x == 1900 && back.y == 2550 && back.w == 3 && back.h == 2, "rectangle kept");
        check(Arrays.equals(back.greys, greys), "greys kept, clear clear: " + Arrays.toString(back.greys));
    }

    static void shown() throws IOException {
        int[][] page = {{0, 255, 128}, {255, 255, 3}};
        LiveChange c = LiveChange.shown(1, 0, 2, 2, (x, y) -> page[y][x], LiveChange.NO_PATH);
        check(c.x == 1 && c.y == 0 && c.w == 2 && c.h == 2, "the area");
        check(Arrays.equals(c.greys, new int[]{254, 128, 254, 3}), "white sent as 254, never clear: " + Arrays.toString(c.greys));
        check(Arrays.equals(LiveChange.read(c.write()).greys, c.greys), "and read back so");
    }

    static void refused() throws IOException {
        refused(() -> new LiveChange(0, 0, 1921, 2561, new int[1921 * 2561]), "larger than a page");
        refused(() -> new LiveChange(0, 0, 2, 2, new int[3]), "greys not w × h");
        byte[] good = new LiveChange(0, 0, 2, 2, new int[4]).write();
        refused(() -> LiveChange.read(Arrays.copyOf(good, good.length - 3)), "cut short");
        refused(() -> LiveChange.read(deflated(new byte[]{0, 0, 0, 0, 0, 1, 0, 1, 7, 9})), "bytes after it");
        refused(() -> LiveChange.read(deflated(new byte[]{0, 0, 0, 0, 0x7f, 0x7f, 0x7f, 0x7f})), "too large, before reading it");
        byte[] pathed = new LiveChange(0, 0, 1, 1, new int[]{7}, new int[]{1, 2, 3}).write();
        refused(() -> LiveChange.read(Arrays.copyOf(pathed, pathed.length - 3)), "path cut short");
        refused(() -> LiveChange.read(deflated(new byte[]{0, 0, 0, 0, 0, 1, 0, 1, 7, 0, 1, 0, 1, 0, 2})), "a sample cut short");
        refused(() -> LiveChange.read(deflated(new byte[]{0, 0, 0, 0, 0, 1, 0, 1, 7, 0, 1, 0, 1, 0, 2, 0, 3, 32, 1})), "bytes after the path");
        refused(() -> LiveChange.read(deflated(new byte[]{0, 0, 0, 0, 0, 1, 0, 1, 7, 0x4e, 0x21})), "more than 20000 samples");
        refused(() -> new LiveChange(0, 0, 1, 1, new int[1], new int[]{1, 2}), "not samples of three");
    }
    interface Throwing { void run() throws Exception; }
    static void refused(Throwing what, String why) {
        try { what.run(); } catch (IOException | IllegalArgumentException expected) { return; } catch (Exception e) { throw new AssertionError(why + ": " + e); }
        throw new AssertionError("not refused: " + why);
    }
    static byte[] deflated(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (java.util.zip.DeflaterOutputStream d = new java.util.zip.DeflaterOutputStream(out)) { d.write(raw); }
        return out.toByteArray();
    }
}
