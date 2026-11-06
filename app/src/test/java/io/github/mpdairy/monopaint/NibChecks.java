package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;

/**
 * The nibs (explore-nibs: {@link RulingPen}, {@link TwigStick}, {@link FanBrush}, {@link Brayer}): the same samples lay
 * the same marks however they are batched, live too; each answers pressure and tilt as the real tool does; the ruling
 * pen and the twig run dry and blot; the brayer reprints what it rolls over a turn later; their codes round-trip and
 * every older preset's code is as it was; their words; their cost a sample beside Round's.
 */
public final class NibChecks {
    static final int W = 480, H = 320;
    static final PartChoice[] NIBS = {PartChoice.RULING, PartChoice.TWIG, PartChoice.FAN, PartChoice.ROLLER};

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        deterministic(); ruling(); twig(); fan(); brayer(); codes(); words();
        String starts = starts(), bench = bench();
        System.out.println("PASS: the four nibs (Ruling Pen, Twig, Fan, Brayer) lay the same marks from the same samples batched 1, 3 or 1000 at a time and live; a ruling pen draws a hairline upright and a band laid over, broad across its blade and thin along it, and runs dry; a twig dries to a scratch and, overloaded, blots where it lands; at their usual load each lands at most a slight swell (" + starts + "); a fan brings down more hairs as it presses and lays them on one line along its breadth; a brayer prints solider pressed and reprints the marks it rolls over a turn later; their codes and presets round-trip and every older preset's code is as it was; their words. " + bench);
    }

    static ModularBrush nib(PartChoice tip) { return StippleChecks.mk(tip, PartChoice.STEADY); }

    /** {x, y, travel, time, tiltX, tiltY} along a line, every 6 ms, leaning {@code lean}° toward azimuth {@code az}° (page, y down). */
    static List<double[]> line(double x0, double y0, double x1, double y1, int n, double p, double lean, double az) {
        double tn = Math.tan(Math.toRadians(lean)), a = Math.toRadians(az);
        double tx = Math.toDegrees(Math.atan(tn * Math.cos(a))), ty = Math.toDegrees(Math.atan(tn * Math.sin(a)));
        List<double[]> o = new ArrayList<>();
        for (int k = 0; k <= n; k++) o.add(new double[]{x0 + (x1 - x0) * k / n, y0 + (y1 - y0) * k / n, p, 1000 + 6 * k, tx, ty});
        return o;
    }
    /** A wavy line across the page, slowing in its middle, pressure rising. */
    static List<double[]> wave() {
        List<double[]> o = new ArrayList<>();
        long t = 1000;
        for (int k = 0; k <= 300; k++) {
            double u = k / 300.0;
            o.add(new double[]{40 + u * 400, 160 + 80 * Math.sin(u * 9), .2 + .7 * u, t, 25, 30});
            t += Math.abs(u - .5) < .1 ? 20 : 5;
        }
        return o;
    }

    /** {@code s} through the nib's stamper alone, {@code per} samples a time (a brayer over a blank page). */
    static FloatTiles stamp(ModularBrush b, double size, List<double[]> s, int per) {
        ResolvedBrush r = ResolvedBrush.of(b, size, 1);
        FloatTiles t = new FloatTiles(W, H, 0);
        StrokePoints pts = new StrokePoints();
        LineStamper.Meta meta = new LineStamper.Meta();
        StrokePaper paper = new StrokePaper(BrushTexture.PLAIN);
        int done = 1;
        for (int i = 0; i < s.size(); i += per) {
            for (int j = i; j < Math.min(s.size(), i + per); j++) { double[] q = s.get(j); pts.add((float) q[0], (float) q[1], (float) q[2], (long) q[3], (float) q[4], (float) q[5]); }
            switch (b.choice(BrushPart.TIP)) {
                case RULING: RulingPen.stamp(t, null, paper, r.pattern, pts, r.inkSize, done, meta); break;
                case TWIG: TwigStick.stamp(t, null, paper, r.pattern, pts, r.inkSize, done, meta); break;
                case FAN: FanBrush.stamp(t, null, paper, r.pattern, pts, r.inkSize, done, meta); break;
                default: Brayer.stamp(t, null, paper, null, r.pattern, pts, r.inkSize, done, meta);
            }
            done = pts.count;
        }
        return t;
    }
    /** {@code s} drawn live onto {@code d}, presented every {@code per} samples. */
    static ToneDocument draw(ToneDocument d, ModularBrush b, double size, List<double[]> s, int per) {
        ModularStroke st = ModularStroke.start(d, b, size, 1, null);
        for (int i = 0; i < s.size(); i++) {
            double[] q = s.get(i);
            st.sample((float) q[0], (float) q[1], ToolSettings.pressure((float) q[2]), (float) q[4], (float) q[5], (long) q[3]);
            if (i % per == per - 1) st.present();
        }
        st.present(); st.finish();
        return d;
    }
    static ToneDocument page(ModularBrush b, double size, List<double[]> s, int per) { return draw(InkWashLayers.blank(W, H), b, size, s, per); }
    /** Ink laid (coverage summed) in columns {@code x0..x1}. */
    static double ink(FloatTiles t, int x0, int x1) { double s = 0; for (int y = 0; y < H; y++) for (int x = x0; x <= x1; x++) s += t.get(x, y); return s; }
    static double ink(FloatTiles t) { return ink(t, 0, W - 1); }
    /** Pixels darker than mid grey in columns {@code x0..x1}. */
    static int dark(ToneDocument d, int x0, int x1) { int n = 0; for (int y = 0; y < H; y++) for (int x = x0; x <= x1; x++) if (d.compositeTone(x, y) < 128) n++; return n; }
    static boolean same(ToneDocument a, ToneDocument b) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (a.compositeTone(x, y) != b.compositeTone(x, y)) return false;
        return true;
    }
    /** The rows {@code x} crosses ink in, at least {@code at} coverage: how broad a vertical band is there. */
    static int across(FloatTiles t, int x, double at) { int n = 0; for (int y = 0; y < H; y++) if (t.get(x, y) >= at) n++; return n; }
    static int alongX(FloatTiles t, int y, double at) { int n = 0; for (int x = 0; x < W; x++) if (t.get(x, y) >= at) n++; return n; }

    static void deterministic() {
        for (PartChoice tip : NIBS) {
            ModularBrush b = nib(tip);
            List<double[]> s = wave();
            FloatTiles a = stamp(b, 30, s, 3);
            check(StippleChecks.same(a, stamp(b, 30, s, 1)) && StippleChecks.same(a, stamp(b, 30, s, 1000)) && ink(a) > 500, tip.label + ": the same samples lay the same marks, however they're batched");
            ToneDocument live = page(b, 30, s, 1);
            check(same(live, page(b, 30, s, 4)) && same(live, page(b, 30, s, 1000)) && dark(live, 0, W - 1) > 300, tip.label + ": live, the same marks however the samples are presented");
            // A second stroke elsewhere is its own, not a copy shifted.
            List<double[]> moved = new ArrayList<>();
            for (double[] q : s) moved.add(new double[]{q[0] + 1, q[1] + 3, q[2], q[3], q[4], q[5]});
            FloatTiles c = stamp(b, 30, moved, 3);
            check(!StippleChecks.same(a, c), tip.label + ": each stroke its own");
        }
    }

    static void ruling() {
        ModularBrush b = nib(PartChoice.RULING);
        // Upright: a hairline. Laid over (lean 50° toward the page's right, the blade lying across a vertical stroke): a band.
        FloatTiles up = stamp(b, 30, line(240, 20, 240, 300, 140, .6, 0, 0), 3), over = stamp(b, 30, line(240, 20, 240, 300, 140, .6, 50, 0), 3);
        int thin = alongX(up, 160, .5), broad = alongX(over, 160, .5);
        check(thin >= 1 && thin <= 4 && broad >= 15, "Ruling Pen: a hairline upright (" + thin + " px), a band laid over (" + broad + " px)");
        // Laid over, along its blade (a horizontal stroke, the blade lying along it) it is thin again.
        FloatTiles along = stamp(b, 30, line(20, 160, 460, 160, 220, .6, 50, 0), 3);
        check(across(along, 240, .5) <= 5, "Ruling Pen: thin along its blade (" + across(along, 240, .5) + " px)");
        // A long broad stroke runs dry: less ink in its last stretch than its first.
        FloatTiles run = stamp(b, 30, line(20, 160, 460, 160, 220, .6, 50, 90), 3);
        check(ink(run, 380, 450) < .6 * ink(run, 40, 110), "Ruling Pen: runs dry along a broad stroke");
        // Pressed harder, it squeezes out more: more ink over the first stretch.
        FloatTiles light = stamp(b, 30, line(20, 160, 460, 160, 220, .2, 50, 90), 3), hard = stamp(b, 30, line(20, 160, 460, 160, 220, .95, 50, 90), 3);
        check(ink(hard, 40, 110) > 1.15 * ink(light, 40, 110), "Ruling Pen: pressing squeezes out more ink");
        // Paint Load: a loaded pen lasts longer.
        FloatTiles dry = stamp(with(b, PartDial.INK_LOAD, 0), 30, line(20, 160, 460, 160, 220, .6, 50, 90), 3), full = stamp(with(b, PartDial.INK_LOAD, 1), 30, line(20, 160, 460, 160, 220, .6, 50, 90), 3);
        check(ink(full, 300, 450) > 1.5 * ink(dry, 300, 450), "Ruling Pen: Paint Load lasts longer");
    }

    static void twig() {
        ModularBrush b = nib(PartChoice.TWIG);
        FloatTiles t = stamp(b, 24, line(20, 160, 460, 160, 220, .6, 0, 0), 3);
        // Overloaded (Paint Load full) a blob where it lands; at its usual load a round start (starts()). Then dry.
        FloatTiles full = stamp(with(b, PartDial.INK_LOAD, 1), 24, line(20, 160, 460, 160, 220, .6, 0, 0), 3);
        check(across(full, 22, .5) > across(full, 120, .5), "Twig: overloaded, a blob where it lands (" + across(full, 22, .5) + " rows, then " + across(full, 120, .5) + ")");
        check(ink(t, 400, 460) < .5 * ink(t, 30, 90), "Twig: dries to a scratch");
        FloatTiles light = stamp(b, 24, line(20, 160, 460, 160, 220, .2, 0, 0), 3), hard = stamp(b, 24, line(20, 160, 460, 160, 220, .95, 0, 0), 3);
        check(ink(hard, 40, 200) > 1.3 * ink(light, 40, 200), "Twig: pressure squashes it wider and squeezes out more");
        // Laid on its side (tilt) it scrapes broader.
        FloatTiles upright = stamp(b, 24, line(240, 20, 240, 300, 140, .6, 0, 0), 3), side = stamp(b, 24, line(240, 20, 240, 300, 140, .6, 60, 0), 3);
        int u = 0, s = 0;
        for (int y = 40; y < 140; y += 10) { u += alongX(upright, y, .3); s += alongX(side, y, .3); }
        check(s > 1.3 * u, "Twig: laid on its side, a broader scrape (" + s + " against " + u + ")");
    }

    /**
     * Where each nib's preset lands (the owner, 2026-11-02: the twig "leaves a blob often at the beginning of the mark"): at
     * its usual Paint Load, landing firm at any pressure, upright or leaning, the first stretch (its size) is at most a slight
     * swell over the line after it, in width and in ink; only an overloaded ruling pen still blots where it lands. Returns
     * the worst start over the line after, width and ink.
     */
    static String starts() {
        double worstW = 0, worstInk = 0;
        for (String name : new String[]{"Ruling pen", "Twig pen", "Fan brush", "Brayer"}) {
            ModularBrush b = BristleSwarmChecks.preset(name);
            double size = ModularSizes.nib(b.choice(BrushPart.TIP)), L = size;
            for (double p : new double[]{.3, .6, .9}) for (double lean : new double[]{0, 40}) for (int i = 0; i < 8; i++) {
                int x0 = 40 + 3 * i, y0 = 160 + i;
                FloatTiles t = stamp(b, size, line(x0, y0, 440, y0, (440 - x0) / 3, p, lean, 45), 3);
                double[] s = start(t, x0, L);
                worstW = Math.max(worstW, s[0] / s[1]); worstInk = Math.max(worstInk, s[2] / s[3]);
                check(s[0] <= 1.25 * s[1] + 2, name + " at " + p + ", leaning " + lean + "°: lands at most a slight swell, " + s[0] + " px wide against " + s[1] + " after");
                check(s[2] <= 1.35 * s[3] + 2 * L, name + " at " + p + ", leaning " + lean + "°: the first " + L + " px hold " + Math.round(s[2]) + " ink against " + Math.round(s[3]) + " after");
            }
        }
        // Overloaded, a ruling pen blots where it lands, mostly.
        ModularBrush full = with(BristleSwarmChecks.preset("Ruling pen"), PartDial.INK_LOAD, 1);
        int blots = 0;
        for (int i = 0; i < 8; i++) { double[] s = start(stamp(full, 30, line(40 + 3 * i, 160 + i, 440, 160 + i, (400 - 3 * i) / 3, .6, 0, 0), 3), 40 + 3 * i, 30); if (s[0] > s[1] + 3) blots++; }
        check(blots >= 4, "Ruling pen: overloaded, it blots where it lands (" + blots + " of 8)");
        return String.format("starts at most %.2f times the line's width, %.2f its ink", worstW, worstInk);
    }
    /**
     * A horizontal stroke from {@code x0}: how broad it lands (its band over its first {@code L}, from a blob's reach behind
     * the pen) and how broad the line after it is (L to 4L), and the ink in its first L and in the next.
     */
    static double[] start(FloatTiles t, int x0, double L) {
        int a = (int) Math.round(x0 + L);
        return new double[]{band(t, Math.max(0, x0 - (int) L), a - 1), band(t, a, (int) Math.round(a + 3 * L) - 1), ink(t, Math.max(0, x0 - (int) L), a - 1), ink(t, a, (int) Math.round(a + L) - 1)};
    }
    /** How broad a horizontal band is over columns {@code x0..x1}: the rows holding at least a tenth of the fullest row's ink, first to last. */
    static int band(FloatTiles t, int x0, int x1) {
        double[] row = new double[H];
        double most = 0;
        for (int y = 0; y < H; y++) { for (int x = x0; x <= x1; x++) row[y] += t.get(x, y); most = Math.max(most, row[y]); }
        int a = -1, b = -1;
        for (int y = 0; y < H; y++) if (most > 0 && row[y] >= .1 * most) { if (a < 0) a = y; b = y; }
        return a < 0 ? 0 : b - a + 1;
    }

    static void fan() {
        ModularBrush b = nib(PartChoice.FAN);
        // Upright (no lean): its breadth across the page; a vertical stroke combs a band, a horizontal one lays one line.
        FloatTiles light = stamp(b, 60, line(240, 20, 240, 300, 140, .15, 0, 0), 3), hard = stamp(b, 60, line(240, 20, 240, 300, 140, .9, 0, 0), 3);
        int few = hairs(light, 160), many = hairs(hard, 160);
        check(many >= 8 && many > few + 3, "Fan: a light touch only its middle hairs (" + few + "), pressing brings more down (" + many + ")");
        check(extent(hard, 160) > 1.5 * extent(light, 160) && extent(hard, 160) > 40, "Fan: splayed wide pressed (" + extent(hard, 160) + " px, light " + extent(light, 160) + ")");
        FloatTiles along = stamp(b, 60, line(20, 160, 460, 160, 220, .9, 0, 0), 3);
        check(across(along, 240, .2) < 10, "Fan: along its breadth its hairs lie on one line (" + across(along, 240, .2) + " rows)");
    }
    /** From the first inked pixel of row {@code y} to its last. */
    static int extent(FloatTiles t, int y) { int a = -1, b = -1; for (int x = 0; x < W; x++) if (t.get(x, y) > .2) { if (a < 0) a = x; b = x; } return a < 0 ? 0 : b - a + 1; }
    /** Separate hair lines crossing row {@code y}: runs of ink with paper between. */
    static int hairs(FloatTiles t, int y) {
        int n = 0; boolean in = false;
        for (int x = 0; x < W; x++) { boolean on = t.get(x, y) > .3; if (on && !in) n++; in = on; }
        return n;
    }

    static void brayer() {
        ModularBrush b = with(nib(PartChoice.ROLLER), PartDial.PICKUP, .8);
        FloatTiles light = stamp(b, 60, line(20, 160, 460, 160, 220, .15, 0, 0), 3), hard = stamp(b, 60, line(20, 160, 460, 160, 220, .9, 0, 0), 3);
        check(ink(hard) > 1.4 * ink(light), "Brayer: prints solider pressed");
        // Square to the way it rolls, as wide as its tip.
        check(Math.abs(across(hard, 240, .05) - 60) <= 3, "Brayer: a band as wide as the tip (" + across(hard, 240, .05) + ")");
        // Over a black bar it picks up ink and prints it again a turn later: darker there than the same roll over blank paper.
        ToneDocument blank = page(b, 60, line(20, 160, 460, 160, 220, .9, 0, 0), 4);
        ToneDocument marked = InkWashLayers.blank(W, H);
        draw(marked, StippleChecks.mk(PartChoice.ROUND, PartChoice.STEADY), 10, line(100, 60, 100, 260, 60, 1, 0, 0), 4);
        draw(marked, b, 60, line(20, 160, 460, 160, 220, .9, 0, 0), 4);
        int turn = (int) Math.round(60 * Brayer.ROUND);
        double ghost = 0, none = 0;
        for (int y = 140; y < 180; y++) for (int x = 100 + turn - 3; x <= 100 + turn + 3; x++) { ghost += 255 - marked.compositeTone(x, y); none += 255 - blank.compositeTone(x, y); }
        check(ghost > none * 1.1 + 200, "Brayer: reprints a bar it rolled over one turn later (" + Math.round(ghost) + " against " + Math.round(none) + ")");
        // Pick Up Paint off: the same as over blank paper there.
        ModularBrush clean = with(b, PartDial.PICKUP, 0);
        ToneDocument cleanMarked = InkWashLayers.blank(W, H), cleanBlank = page(clean, 60, line(20, 160, 460, 160, 220, .9, 0, 0), 4);
        draw(cleanMarked, StippleChecks.mk(PartChoice.ROUND, PartChoice.STEADY), 10, line(100, 60, 100, 260, 60, 1, 0, 0), 4);
        draw(cleanMarked, clean, 60, line(20, 160, 460, 160, 220, .9, 0, 0), 4);
        boolean sameThere = true;
        for (int y = 140; y < 180; y++) for (int x = 100 + turn - 3; x <= 100 + turn + 3; x++) sameThere &= cleanMarked.compositeTone(x, y) == cleanBlank.compositeTone(x, y);
        check(sameThere, "Brayer: Pick Up Paint off reprints nothing");
    }

    static ModularBrush with(ModularBrush b, PartDial d, double v) { ModularBrush c = b.copy(); c.part(d.part).set(d, v); return c.normalize(); }

    static void codes() {
        // Tip choices 13 to 16, after Hand Hatch: every older choice keeps its number.
        check(PartChoice.RULING.index() == 13 && PartChoice.TWIG.index() == 14 && PartChoice.FAN.index() == 15 && PartChoice.ROLLER.index() == 16
                && PartChoice.HAND_HATCH.index() == 12 && BrushPart.TIP.choices().size() == 17, "The nibs are Tip choices 13 to 16");
        for (PartChoice tip : NIBS) {
            ModularBrush b = nib(tip);
            for (PartDial d : tip.dials) if (!d.choice()) b.part(BrushPart.TIP).set(d, d.min + .37 * (d.max - d.min));
            b.normalize();
            String code = ModularCode.encode(b);
            ModularBrush back = ModularCode.decode(code);
            check(back != null && ModularCode.encode(back).equals(code) && back.choice(BrushPart.TIP) == tip && !ModularCode.newer(code), tip.label + " round-trips: " + code);
            check(!ModularFeatures.varies(tip) && ModularFeatures.offered(tip), tip.label + " is offered in Brush Lab, never rolled or swapped to");
        }
        // Every preset's code is the one its row holds, the older ones byte for byte as before.
        int nibs = 0;
        for (ToolPresets.Preset p : ToolPresets.all()) {
            // (Hand Hatch, retired, reads as Hatch: its presets are written as Hatch, as before.)
            check(ModularCode.encode(ModularCode.decode(p.code)).equals(p.code) || p.code.matches("[a-z-]+\\.M.c.*"), p.name + "'s code round-trips");
            if (ResolvedBrush.nib(p.brush().parts.choice(BrushPart.TIP))) nibs++;
        }
        check(nibs == 4, "One preset a nib");
    }

    static void words() {
        String[][] want = {{"Ruling pen", "ruling pen", ModularWords.RULES_DRY}, {"Twig pen", "twig", ModularWords.TWIG_BLOTS},
                {"Fan brush", "fan brush", ModularWords.FAN_HAIRS}, {"Brayer", "brayer", ModularWords.REPRINTS}};
        for (String[] w : want) {
            ModularBrush b = BristleSwarmChecks.preset(w[0]);
            String plain = ModularWords.plainWords(b);
            check(plain.contains(", " + w[1]) && plain.contains(w[2]), w[0] + " says what it is: " + plain);
            BrushVoice.MISSED.clear();
            String about = BrushVoice.about(b);
            check(BrushVoice.MISSED.isEmpty() && !about.isEmpty(), w[0] + " has words for every fact: " + about + " " + BrushVoice.MISSED);
        }
        check(ModularWords.plainWords(with(nib(PartChoice.ROLLER), PartDial.PICKUP, 0)).contains(ModularWords.ROLLS), "A brayer that picks up nothing only rolls");
    }

    /** ms a sample live, each nib at its Medium beside a Round of the same size, on the wave. */
    static String bench() {
        StringBuilder said = new StringBuilder("Timings (this Mac), ms a sample live, beside Round at the same size:");
        List<double[]> path = wave();
        ModularBrush round = StippleChecks.mk(PartChoice.ROUND, PartChoice.PRESS_BOTH);
        for (PartChoice tip : NIBS) {
            double size = ModularSizes.nib(tip), n = perSample(nib(tip), size, path), r = perSample(round, size, path);
            said.append(String.format(java.util.Locale.ROOT, " %s %d px %.4f (Round %.4f);", tip.label, (int) size, n, r));
            // A twig's knob, stretched on its side, with its blobs, covers several times a round line's pixels, each traced to its outline.
            Timing.limit(n <= Math.max(.05, (tip == PartChoice.TWIG ? 4 : 2.5) * r), tip.label + " costs a sample about what Round does: " + said);
        }
        return said.toString();
    }
    static double perSample(ModularBrush b, double size, List<double[]> path) {
        double best = Double.MAX_VALUE;
        for (int run = 0; run < 5; run++) {
            ToneDocument d = InkWashLayers.blank(W, H);
            long t0 = System.nanoTime();
            draw(d, b, size, path, 4);
            best = Math.min(best, (System.nanoTime() - t0) / 1e6 / path.size());
        }
        return best;
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
