package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every effect brush ({@link WildSample#reworks}: the Basic tools that only rework the marks there) shows its effect
 * while the pen is down, not only at lift (the owner, 2026-11-04: "make them all live if it's cheap"; the grey band is
 * gone, so one that waited for the lift would show nothing until then). Each is drawn as the drawing screen draws it
 * (InkWash.stroke, {@link StrokeStart#stroke}: live or quick by the rule) over a page of marks on every plane, and the
 * clear-water ones over floating ink too, at a small and a big size. Before lift, what the screen shows (the page and
 * the stroke's unwritten part over it, {@link DrawingStroke#unwritten}) behind the pen must have gone at least halfway
 * to what the lift leaves at most of the pixels the stroke changes there. {@code java EffectsLiveChecks v} prints the
 * table: brush, size, on the bath or not, live or quick, the pixels changed, the share shown before lift, and the pen's
 * and the render's work per update (4 samples and a present), ms on this machine.
 * <p>On floating ink a quick stroke's frames of the bath ({@link ModularStroke#floatFrame}) were shown on the stroke's
 * own plane, not the bath's ({@link ModularStroke#kind}): the rings showed twice as dark and the marks on the other
 * planes went from their tiles until lift (a third of the pixels shown; now three quarters and more). What remains
 * is the lift's own: clear water's run-out on dry paint ({@link ClearWater#lift}), a drag's far pushes on the bath.
 */
public final class EffectsLiveChecks {
    static final int W = 800, H = 400, Y = 200, X0 = 60, X1 = 700;
    static final double[] SIZES = {60, 240};
    /** The least share of a stroke's changed pixels behind the pen the screen shows before lift. */
    static final double SHOWN = .5;
    static boolean verbose;

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        verbose = args.length > 0;
        List<String> names = new ArrayList<>(), late = new ArrayList<>();
        for (ToolPresets.Preset p : ToolPresets.all()) if (WildSample.reworks(p.brush().parts)) names.add(p.name);
        for (String name : new String[]{"Blend", "Blur", "Water", "Palette knife", "Photocopier", "Newsprint", "Clear water", "Comb"}) check(names.contains(name), name + " is an effect brush");
        note("| brush | px | bath | stroke | changed | shown | ms/update |");
        for (String name : names) {
            ModularBrush b = FloatChecks.preset(name);
            boolean onWater = b.choice(BrushPart.CARRY) == PartChoice.CLEAR_WATER || b.floats();
            for (boolean bath : onWater ? new boolean[]{false, true} : new boolean[]{false})
                for (double size : SIZES) if (!shows(name, size, bath)) late.add(name + " " + (int) size + " px" + (bath ? " on floating ink" : ""));
        }
        check(late.isEmpty(), "every effect brush shows its effect before lift; only at lift: " + late);
        System.out.println("PASS: every effect brush (" + names.size() + ": " + String.join(", ", names) + ") shows its effect on the screen while the pen is down, at "
                + (int) SIZES[0] + " and " + (int) SIZES[1] + " px, live or quick, the clear-water ones on floating ink too: behind the pen, most pixels it changes have gone at least halfway to what the lift leaves");
    }

    /** {@code name}'s stroke at {@code size} along the page (over floating ink: {@code bath}): whether the screen before lift shows its effect behind the pen. */
    static boolean shows(String name, double size, boolean bath) {
        ToneDocument d = page();
        WatercolorSession s = new WatercolorSession();
        if (bath) for (int x = X0 + 40; x < X1; x += 90) FloatChecks.stroke(d, s, FloatChecks.sumi(), 30, FloatChecks.tap(x, Y + (x / 90 % 2 == 0 ? 20 : -20), 300));
        Brush brush = FloatChecks.find(name).brush();
        // As InkWash.stroke: the smudge tool's holder, the pair shown, then the stroke as the screen starts it.
        s.holding("SMUDGE");
        InkWashLayers.showSelected(d);
        int n = W * H;
        int[] found = new int[n], shown = new int[n], over = new int[n], after = new int[n];
        // As found once what floats or settles first has (StrokeStart.drawn does it again: nothing left then).
        s.settleBefore(d, s.onWater(d, brush.parts));
        d.compositeTones(0, 0, W, H, found, 0, W);
        DrawingStroke st = StrokeStart.stroke(d, brush, new ToolSwatches.Look(InkWashSizes.Preset.values()[0], size, 1), s, 0);
        QuickStroke q = quick(st);
        double[][] path = FloatChecks.line(X0, Y, X1, Y, .6);
        long work = 0;
        int presents = 0;
        for (int i = 0; i < path.length; i++) {
            long t0 = System.nanoTime();
            st.sample((float) path[i][0], (float) path[i][1], ToolSettings.pressure((float) path[i][2]), 0, 0, (long) path[i][3]);
            boolean batch = i % 4 == 3;
            if (batch) { st.present(); presents++; }
            work += System.nanoTime() - t0;
            // The pen moving on: a quick render keeps up, as on the desktop; at the stroke's middle composeMs passes, as
            // it does many times over a stroke on the Manta.
            if (batch && q != null) settle(q);
            if (i == path.length / 2) sleep(QuickStroke.composeMs + 20);
        }
        if (q != null) settle(q);
        for (int k = 0; k < 3; k++) { st.present(); sleep(2); }
        long render = q == null ? 0 : q.renderNanos;
        d.compositeTones(0, 0, W, H, shown, 0, W);
        DrawingStroke.Unwritten u = st.unwritten();
        java.util.Arrays.fill(over, -1);
        if (u != null) u.tones(0, 0, W, H, over);
        for (int i = 0; i < n; i++) if (over[i] >= 0) shown[i] = over[i];
        st.finish();
        d.compositeTones(0, 0, W, H, after, 0, W);
        // Behind the pen: past the stroke's reach (and a tile) from the newest sample.
        int behind = (int) (X1 - size - 64);
        long changed = 0, close = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < behind; x++) {
            int i = y * W + x, by = Math.abs(after[i] - found[i]);
            if (by < 4) continue;
            changed++;
            if (Math.abs(shown[i] - after[i]) <= Math.max(4, by / 2)) close++;
        }
        double share = changed == 0 ? 1 : (double) close / changed;
        note(String.format(Locale.ROOT, "| %s | %.0f | %s | %s | %d | %.0f%% | %.2f |", name, size, bath ? "yes" : "no", q == null ? "live" : "quick", changed, 100 * share,
                (work + render) / 1e6 / Math.max(1, presents)));
        return changed < 200 || share >= SHOWN;
    }
    /** The quick stroke under {@code st}'s wrappers (Steady Hand, Filled Shape), or null for a live one. */
    static QuickStroke quick(DrawingStroke st) {
        for (int k = 0; k < 8 && st != null; k++) {
            if (st instanceof QuickStroke) return (QuickStroke) st;
            st = inner(st);
        }
        return null;
    }
    private static DrawingStroke inner(DrawingStroke st) {
        for (Class<?> c = st.getClass(); c != null; c = c.getSuperclass())
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (!DrawingStroke.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                try { return (DrawingStroke) f.get(st); } catch (IllegalAccessException e) { throw new RuntimeException(e); }
            }
        return null;
    }
    private static void settle(QuickStroke q) { for (int k = 0; k < 200 && q.rendered() < q.samples(); k++) sleep(1); sleep(1); }
    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { throw new RuntimeException(e); } }
    /** The page: pen lines on the ink, pencil hatching on its pencil plane, wash bands, paint bands on the wash's paint plane. */
    static ToneDocument page() {
        ToneDocument d = InkWashLayers.blank(W, H);
        CarrySmudgeChecks.lay(d, InkWashLayers.INK, (x, y) -> Math.floorMod(x + y / 3, 24) < 3 ? 40 : 255);
        CarrySmudgeChecks.lay(d, InkWashLayers.INK, ToneDocument.KIND_PENCIL, (x, y) -> Math.floorMod(x - y, 30) < 4 ? 80 : 255);
        CarrySmudgeChecks.lay(d, InkWashLayers.WASH, (x, y) -> x / 60 % 3 == 1 ? 150 : 255);
        CarrySmudgeChecks.lay(d, InkWashLayers.WASH, ToneDocument.KIND_PAINT, (x, y) -> y / 40 % 3 == 1 ? 110 : 255);
        return d;
    }
    static void note(String s) { if (verbose) System.out.println(s); }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
