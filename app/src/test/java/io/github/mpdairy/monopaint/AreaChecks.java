package io.github.mpdairy.monopaint;

import java.util.Locale;

/**
 * The size previews of the brushes that only rework the marks there (branch explore-area; the owner, 2026-11-01: "for
 * the effect brushes.. its hard to see what area is going to be affected theres no preview size"): Reprint (the
 * Photocopier, Newsprint), the Palette knife, Water, Clear water, the smudge tool's Blend and their kin. Each one's
 * hover ring is more than a dot and as wide as what a light touch of it changes, measured by drawing it over a field of
 * marks (the knife at the natural hold over a wet wash; clear water a tap's drop among floating paint); its bar row and
 * Tools page sample dot the area it works round the stroke; its readout shows the area, not a grey.
 */
public final class AreaChecks {
    static final int W = 400, H = 240, PX = 60;
    /** A change the page shows: one of the panel's 16 greys (a fainter one is a smudge's soft edge or Water's run, past its dab). */
    static final int SEEN = 16;

    public static void main(String[] args) throws java.io.IOException {
        Assets.Source folder = Assets.folder(Assets.FOLDER);
        Assets.install(path -> { if (path.startsWith("textures/")) TEXTURES_READ.incrementAndGet(); return folder.open(path); });
        String rings = rings(), samples = samples(), cost = startCost();
        readout();
        String names = names();
        System.out.println("PASS: " + names);
        System.out.println("PASS: a brush that only reworks the marks there rings what a light touch of it changes (" + rings + "); its bar row and samples dot that area round the stroke ("
                + samples + "); its readout shows the area, not a grey; hovering it rings as the eraser does and while the pen is down nothing covers its change (no trail); " + cost);
    }

    /** Every Basic tool that only reworks the marks there ({@link WildSample#reworks}). */
    static java.util.List<String> reworking() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (ToolPresets.Preset p : ToolPresets.all()) if (WildSample.reworks(p.brush().parts)) out.add(p.name);
        for (String name : new String[]{"Photocopier", "Newsprint", "Palette knife", "Water", "Clear water", "Blend"}) check(out.contains(name), name + " only reworks the marks there");
        return out;
    }

    /** A field of marks: a checker of greys on both layers, or (wet) a wet wash with the checker on the ink layer over it. */
    static ToneDocument field(WatercolorSession s, boolean wet) {
        ToneDocument d = InkWashLayers.blank(W, H);
        if (wet) for (int y = 40; y <= 200; y += 40) KnifePictures.stroke(d, s, HeldPaint.ANY, KnifePictures.wetWash(), 110, .6, KnifePictures.path(0, y, W, y, .7, 0, 0, 0, 0));
        int[] g = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) g[y * W + x] = (x / 6 + y / 6) % 2 == 0 ? 70 : 190;
        for (int layer : wet ? new int[]{InkWashLayers.INK} : new int[]{InkWashLayers.WASH, InkWashLayers.INK}) { d.selectLayer(layer); d.begin(); d.setTones(0, 0, W, H, g); d.finish(); }
        return d;
    }
    /** How many rows across the middle of a stroke of {@code b} along y = H / 2 at pressure travel {@code p} changed the page (tilted {@code tilt} degrees). */
    static int across(ModularBrush b, boolean wet, double p, double tilt) {
        WatercolorSession s0 = new WatercolorSession(), s1 = new WatercolorSession();
        ToneDocument was = field(s0, wet), d = field(s1, wet);
        KnifePictures.stroke(d, s1, "AREA", b, PX, 1, KnifePictures.path(60, H / 2.0, W - 60, H / 2.0, p, tilt, 0, tilt, 0));
        s0.settleNow(was); s1.settleNow(d);
        int top = -1, bottom = -1;
        for (int y = 0; y < H; y++) for (int x = W / 2 - 20; x <= W / 2 + 20; x++) if (Math.abs(was.compositeTone(x, y) - d.compositeTone(x, y)) >= SEEN) { if (top < 0) top = y; bottom = y; break; }
        return top < 0 ? 0 : bottom - top + 1;
    }
    /** Clear water's tap at pressure travel {@code p} among floating Sumi: the clear drop's diameter, from its ring's area. */
    static double drop(ModularBrush b, double p) {
        ToneDocument d = InkWashLayers.blank(W, H);
        WatercolorSession s = new WatercolorSession();
        FloatChecks.stroke(d, s, FloatChecks.sumi(), 8, FloatChecks.tap(W / 2.0, H / 2.0, 300));
        FloatChecks.stroke(d, s, b, PX, FloatChecks.tap(W / 2.0, H / 2.0, p, 120));
        java.util.List<FloatBath.Ring> rings = FloatChecks.bath(s).rings;
        return 2 * Math.sqrt(rings.get(rings.size() - 1).area() / Math.PI);
    }

    static String rings() {
        StringBuilder said = new StringBuilder();
        for (String name : reworking()) {
            ModularBrush b = FloatChecks.preset(name);
            HoverNib nib = HoverNib.of(ResolvedBrush.of(b, PX, 1));
            float r = HoverNib.radius(nib.width, 1);
            check(HoverNib.ticks(0, 0, r, r, 0).length > 2, name + ": its ring is more than one dot (" + nib.width + " px)");
            // Gouache water works only gouache, which the field has none of.
            if (name.equals("Gouache water")) continue;
            double changed;
            if (ModularWords.clearWater(b)) changed = drop(b, HoverNib.LIGHT);
            else {
                boolean knife = ResolvedBrush.of(b, PX, 1).carry.scrape();
                changed = across(b, knife, HoverNib.LIGHT, knife ? ResolvedBrush.NATURAL : 0);
            }
            // The ring is as far as it reaches: a dab's soft edge (its outer quarter or so) changes less than a grey, and a
            // drop's size varies ±14% with its place.
            check(changed <= 1.2 * nib.width + 2 && changed >= .6 * nib.width, name + ": its ring (" + fixed(nib.width) + " px) is as wide as what a light touch changes (" + fixed(changed) + " px)");
            said.append(said.length() == 0 ? "" : ", ").append(name).append(' ').append(fixed(nib.width)).append(" px ring, ").append(fixed(changed)).append(" changed");
        }
        // The knife upright scrapes a fine line, laid over its whole size: the ring is the natural hold's, between.
        ModularBrush knife = FloatChecks.preset("Palette knife");
        double upright = across(knife, true, HoverNib.LIGHT, 0), laid = across(knife, true, HoverNib.LIGHT, 70), ring = HoverNib.of(ResolvedBrush.of(knife, PX, 1)).width;
        check(upright < ring && ring < laid + 2, "the knife's ring (" + fixed(ring) + ") lies between its upright line (" + fixed(upright) + ") and its flat (" + fixed(laid) + ")");
        return said.toString();
    }

    /** The bar row's tray stroke and Brush Lab's loop: the area dotted round the stroke, the size it draws at; none round a brush that lays paint. */
    static String samples() {
        StringBuilder said = new StringBuilder();
        for (String name : new String[]{"Photocopier", "Palette knife", "Water", "Blend"}) {
            ModularBrush b = FloatChecks.preset(name);
            int w = 150, h = 60;
            float size = WildSample.trayCap(h);
            int[] tray = WildSample.modular(b, 1, WildSample.Shape.TRAY, size, w, h, false);
            // The outline's dots above and below the stroke: the rows of the outermost ink outside the band.
            int top = h, bottom = -1;
            for (int y = 0; y < h; y++) for (int x = 0; x < w * .4; x++) if ((tray[y * w + x] & 0xff) < 60) { top = Math.min(top, y); bottom = Math.max(bottom, y); }
            check(bottom > top, name + ": its row dots the area round its stroke");
            double ring = HoverNib.nib(ResolvedBrush.of(b, size, 1), WildSample.TYPICAL_PRESS)[0], spread = bottom - top + 1;
            // The tray stroke rises and falls a little (trayAmp) across the tile's left part, and the dots are 2 px.
            check(spread >= ring - 3 && spread <= ring + h * .25, name + ": the dots as far apart as its ring (" + fixed(spread) + " px across, ring " + fixed(ring) + ")");
            said.append(said.length() == 0 ? "" : ", ").append(name).append(' ').append(fixed(spread)).append(" px");
        }
        for (String name : new String[]{"Steady line", "Soft wash", "Wet oils"}) check(!WildSample.reworks(FloatChecks.preset(name)), name + " lays paint: no dots round it");
        return said.toString();
    }
    /** Surface scans read (a surface made, {@link BrushTexture#grain}): seconds each on the Manta. */
    static final java.util.concurrent.atomic.AtomicInteger TEXTURES_READ = new java.util.concurrent.atomic.AtomicInteger();
    /**
     * A Newsprint stroke's start after the surfaces kept have moved on (the owner, 2026-11-02: no grey band until "a split
     * second before the thing is converted", 3.4 s of each later stroke's start on the Manta): the drawing screen resolves
     * the brush as named and its ring (Newsprint names the Library paper), then the stroke on the page's surface;
     * none of that makes a surface. Only drawing reads one: none on a plain page, its own once on another.
     */
    static String startCost() {
        ModularBrush b = FloatChecks.preset("Newsprint");
        String said = "";
        for (BrushTexture surface : new BrushTexture[]{BrushTexture.PLAIN, BrushTexture.LAID}) {
            ToneDocument d = AreaChecks.field(new WatercolorSession(), false);
            d.surface = surface;
            WatercolorSession s = new WatercolorSession();
            for (int k = 0; k < 2; k++) {
                // Two other surfaces made since: the two kept, the Library paper and the page's not among them.
                BrushTexture.COLD_PRESS.grain(); BrushTexture.FELT.grain();
                TEXTURES_READ.set(0);
                long t0 = System.nanoTime();
                ResolvedBrush.of(b, PX, 1); ResolvedBrush.of(b, PX, 1);
                s.holding("REPRINT"); s.settleBefore(d, b); s.dry("stroke");
                DrawingStroke stroke = QuickStroke.start(d, b, PX, 1, s);
                double ms = (System.nanoTime() - t0) / 1e6;
                check(TEXTURES_READ.get() == 0, "Newsprint's start (stroke " + (k + 1) + ", on " + surface.label + ") makes no surface (" + TEXTURES_READ.get() + " read)");
                KnifePictures.feed(stroke, KnifePictures.path(40, 60 + 100 * k, W - 40, 60 + 100 * k, .85, 0, 0, 0, 0));
                int drawn = TEXTURES_READ.get();
                check(drawn == (surface == BrushTexture.PLAIN ? 0 : 1), "Newsprint drawn on " + surface.label + " reads " + drawn + " surfaces");
                if (k == 1) said += (said.isEmpty() ? "" : ", ") + surface.label + " " + String.format(Locale.ROOT, "%.1f", ms) + " ms";
            }
        }
        return "a later Newsprint stroke starts making no surface (" + said + ")";
    }
    /** The readout ({@link SwatchReadout}, Android's): a brush that lays no paint shows the area in an ink edge, as the smudge tool. */
    static void readout() throws java.io.IOException {
        String pad = DrawLayoutChecks.source("DrawingPad");
        check(pad.contains("return brush == null || WildSample.reworks(brush.parts) ? HoverNib.eraser(look.px)"), "an effect brush rings as the eraser does");
        check(!pad.contains("mirrorShow.trail("), "no effect brush leaves a trail: its change shows under the pen");
        check(DrawLayoutChecks.source("SwatchReadout").contains("greys && brush != null && !WildSample.reworks(brush.parts) ? ModularSizes.swatch(brush.parts, look.darkness) : -1"),
                "the readout shows a reworking brush's area, not a grey");
    }

    static String fixed(double v) { return String.format(Locale.ROOT, "%.0f", v); }
    /**
     * Their names say "effect" where shown (the owner, 2026-11-02: "the brushes that don't leave a mark ... should use the
     * word 'effect' in their name"; {@link WildSample#shownName}): in the name's case, once, display only; every Basic
     * tool's still fits its card's two lines and the tool picker's cell (Brush Lab's title: LabLayoutChecks).
     */
    static String names() {
        java.util.List<String> shown = new java.util.ArrayList<>(), effects = reworking();
        float cell = LabText.width(DrawLayout.PICKER_NAME_MOST, TypeStyle.CAPTION.sp, DrawLayout.PICKER_SPACING);
        for (ToolPresets.Preset p : ToolPresets.all()) {
            Brush b = p.brush();
            String s = p.shownName();
            check(s.equals(effects.contains(p.name) ? p.name + " Effect" : p.name) && b.name().equals(p.name) && s.equals(b.shownName()), p.name + " is shown " + s);
            check(BrushNames.twoLines(s), s + " wraps whole to a card's two lines");
            if (effects.contains(p.name)) shown.add(s);
        }
        // The drawn ones (the table was the drawn tools until 2026-11-06); a shown tool not drawn may be cut with … as any long name.
        for (String[] tool : DrawLayout.PICKER) {
            if (tool[1] == null) continue;
            String s = ToolPresets.named(tool[0]).shownName();
            check(LabText.width(s, TypeStyle.CAPTION.sp, DrawLayout.PICKER_SPACING) <= Math.max(cell, LabText.width(tool[0], TypeStyle.CAPTION.sp, DrawLayout.PICKER_SPACING)), s + " fits its picker cell whole as its name did");
        }
        ModularBrush water = ToolKits.preset("Water").brush().parts, line = ToolKits.preset("Steady line").brush().parts;
        check(WildSample.shownName("Polite Smear", water).equals("Polite Smear Effect") && WildSample.shownName("Smear of Mild Regret", water).equals("Smear of Mild Regret Effect"),
                "A Title Case name takes Effect");
        check(WildSample.shownName("Wobblefuzz", water).equals("Wobblefuzz Effect") && WildSample.shownName("my water", water).equals("my water Effect"), "Any other name takes effect");
        check(WildSample.shownName("Water Effect", water).equals("Water Effect") && WildSample.shownName("Ripple effects", water).equals("Ripple effects")
                && WildSample.shownName("", water).isEmpty(), "A name that says effect already says it once");
        check(WildSample.shownName("Steady line", line).equals("Steady line") && WildSample.shownName("Effective Pen", water).equals("Effective Pen Effect"), "Only a brush that reworks, and only the whole word");
        Brush kept = Brush.read("Water\t" + ToolKits.preset("Water").brush().write().split("\t")[1]);
        check(kept.name().equals("Water") && kept.write().startsWith("Water\t") && DrawLayout.lineWords(kept)[0].startsWith("Water Effect · ") && DrawLayout.lineWords(kept)[1].startsWith("Water Effect. "),
                "Kept and coded as Water, shown on the brush line as Water Effect: " + DrawLayout.lineWords(kept)[0]);
        ToolKits.Kit marbling = ToolKitsChecks.kit("Marbling");
        check(marbling != null && marbling.tool(1).equals("Clear water Effect") && marbling.tool(0).equals("Sumi"), "A kit's steps name their tools as shown");
        return "the brushes that only rework the marks there are shown with effect, their names kept: " + shown;
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError("FAIL: " + message); }
}
