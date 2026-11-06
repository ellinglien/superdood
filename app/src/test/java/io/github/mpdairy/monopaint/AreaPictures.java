package io.github.mpdairy.monopaint;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * The size previews of the brushes that only rework the marks there (branch explore-area), drawn by the app's engine:
 * for each, the hover ring's dots over what a usual stroke changes, at the natural hold (down a page of dry marks over a wet
 * wash; clear water a light tap among floating rings), the bar's row (its tray stroke) and the side column's readout dot.
 * {@code strip <out.png>} draws these for the code it is compiled with, {@code join <before.png> <after.png> <out.png>}
 * puts two side by side. Compiled against an older tree too (scripts/area_pictures.sh), so it calls only what both have.
 */
public final class AreaPictures {
    static final String[] NAMES = {"Photocopier", "Newsprint", "Palette knife", "Water", "Clear water", "Blend"};
    static final String[] LABELS = {"Photocopier", "Newsprint", "Palette knife", "Water", "Clear water", "Smudge tool"};
    static final int PX = 40, AW = 200, AH = 140, TW = 150, TH = 60, RW = 60, GAP = 10, LABEL = 110, TOP = 24;
    static final String SOFT_WASH = "soft-wash.M60i0000000007q_1wva200000000_0_0fk_7_8la0000000000000000nz0.t4mf73";

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args[0].equals("join")) { join(args[1], args[2], args[3]); return; }
        int w = AW + TW + RW + 4 * GAP, h = NAMES.length * (AH + GAP);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(170, 170, 170)); g.fillRect(0, 0, w, h);
        for (int i = 0; i < NAMES.length; i++) {
            ModularBrush b = preset(NAMES[i]);
            int y = i * (AH + GAP);
            put(img, area(b), AW, AH, GAP, y);
            put(img, WildSample.slotTray(b, look(), TW, TH), TW, TH, AW + 2 * GAP, y + (AH - TH) / 2);
            put(img, readout(b, i < NAMES.length - 1), RW, RW, AW + TW + 3 * GAP, y + (AH - RW) / 2);
        }
        javax.imageio.ImageIO.write(img, "png", new File(args[1]));
    }

    static ToolSwatches.Look look() { return new ToolSwatches.Look(InkWashSizes.Preset.values()[0], PX, .6); }
    static ModularBrush preset(String name) {
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) return p.brush().parts;
        throw new AssertionError("no preset " + name);
    }

    private static long clock;
    static void stroke(ToneDocument d, WatercolorSession s, ModularBrush b, double size, double darkness, double[][] path) {
        s.settleBefore(d, b); s.dry("stroke");
        DrawingStroke st = QuickStroke.start(d, b, size, darkness, s);
        // The pen at the natural hold, as a hand holds it.
        for (double[] p : path) { st.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), (float) ResolvedBrush.NATURAL, 0, clock + (long) p[3]); st.present(); }
        st.finish();
        clock += 10_000;
    }
    static double[][] line(double x0, double y0, double x1, double y1, double p) {
        int n = (int) Math.max(2, Math.ceil(Math.hypot(x1 - x0, y1 - y0) / 2.5));
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) { double u = (double) i / n; o[i] = new double[]{x0 + (x1 - x0) * u, y0 + (y1 - y0) * u, p, i * 4}; }
        return o;
    }
    static double[][] tap(double x, double y, double p, int ms) {
        int n = Math.max(1, ms / 8);
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) o[i] = new double[]{x + .2 * Math.sin(i), y + .2 * Math.cos(i * 1.3), p, i * 8};
        return o;
    }

    /**
     * What a usual stroke of {@code b} changes, with the hover ring's dots on it: down a page of dry
     * marks (a checker of greys on the ink layer) over a wet wash in its lower half; clear water a tap among rings.
     */
    static int[] area(ModularBrush b) {
        ToneDocument d = InkWashLayers.blank(AW, AH);
        WatercolorSession s = new WatercolorSession();
        boolean clear = ModularWords.clearWater(b);
        double cx = AW / 2.0, cy = clear ? AH / 2.0 : AH * .25;
        if (clear) {
            ModularBrush sumi = preset("Sumi");
            for (int k = 0; k < 6; k++) { stroke(d, s, sumi, 8, 1, tap(cx + 30 * Math.cos(k), cy + 20 * Math.sin(k * 1.7), .7, 300)); }
            stroke(d, s, b, PX, 1, tap(cx, cy, HoverNib.LIGHT, 200));
            s.settleNow(d);
        } else {
            stroke(d, s, ModularCode.decode(SOFT_WASH), 70, .6, line(0, AH * .75, AW, AH * .75, .7));
            int[] g = new int[AW * AH];
            for (int y = 0; y < AH; y++) for (int x = 0; x < AW; x++) g[y * AW + x] = (x / 6 + y / 6) % 2 == 0 ? 190 : 250;
            d.selectLayer(InkWashLayers.INK); d.begin(); d.setTones(0, 0, AW, AH / 2, g); d.finish();
            stroke(d, s, b, PX, 1, line(cx, 8, cx, AH - 8, HoverNib.TYPICAL));
        }
        int[] px = new int[AW * AH];
        d.render(px, 0, 0, AW, AH);
        // The hover ring as DrawingPad draws it: 2 px ink squares on the ring.
        HoverNib nib = HoverNib.of(ResolvedBrush.of(b, PX, 1));
        float a = HoverNib.radius(nib.width, 1), bb = Math.min(a, HoverNib.radius(nib.height, 1));
        float[] t = HoverNib.ticks((float) cx, (float) cy, a, bb, (float) nib.angle);
        for (int i = 0; i < t.length; i += 2)
            for (int y = Math.round(t[i + 1] - 1); y < Math.round(t[i + 1] + 1); y++) for (int x = Math.round(t[i] - 1); x < Math.round(t[i] + 1); x++)
                if (x >= 0 && y >= 0 && x < AW && y < AH) px[y * AW + x] = 0xff000000;
        return px;
    }

    /** The side column's readout ({@link SwatchReadout}): the dot at the size, the grey in use's dots in it, an ink edge where it has none or a light one. */
    static int[] readout(ModularBrush b, boolean greys) {
        int tone = greys && !reworks(b) ? ModularSizes.swatch(b, .6) : -1;
        int[] px = new int[RW * RW];
        double r = Math.min(PX, RW - 8) / 2.0, c = RW / 2.0;
        for (int y = 0; y < RW; y++) for (int x = 0; x < RW; x++) {
            double d = Math.hypot(x + .5 - c, y + .5 - c);
            int v = 255;
            if (d <= r && tone >= 0) v = DotPattern.pixel(tone, x, y) & 0xff;
            if (Math.abs(d - r) < .75 && (tone < 0 || tone > 235)) v = 0;
            px[y * RW + x] = 0xff000000 | v * 0x010101;
        }
        return px;
    }
    /** WildSample.reworks where this tree has it (none before branch explore-area: every slot's dot was its grey). */
    static boolean reworks(ModularBrush b) {
        try { return (Boolean) WildSample.class.getDeclaredMethod("reworks", ModularBrush.class).invoke(null, b); }
        catch (NoSuchMethodException e) { return false; }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    static void put(BufferedImage img, int[] argb, int w, int h, int x0, int y0) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.getRaster().setSample(x0 + x, y0 + y, 0, argb[y * w + x] & 0xff);
    }

    static void join(String before, String after, String out) throws Exception {
        BufferedImage a = javax.imageio.ImageIO.read(new File(before)), b = javax.imageio.ImageIO.read(new File(after));
        // Twice the panel's pixels, each a 2 × 2 block, so the ring's 2 px dots show.
        int s = 2, sw = s * a.getWidth(), top = s * TOP + 18;
        int w = LABEL + sw + 2 * GAP + s * b.getWidth(), h = top + s * Math.max(a.getHeight(), b.getHeight());
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(170, 170, 170)); g.fillRect(0, 0, w, h);
        g.drawImage(a, LABEL, top, sw, s * a.getHeight(), null);
        g.drawImage(b, LABEL + sw + 2 * GAP, top, s * b.getWidth(), s * b.getHeight(), null);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.drawString("Before", LABEL + s * GAP, 24); g.drawString("After", LABEL + sw + 2 * GAP + s * GAP, 24);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        for (int k = 0; k < 2; k++) {
            int x = LABEL + k * (sw + 2 * GAP);
            g.drawString("hover ring over a usual stroke", x + s * GAP, top - 10);
            g.drawString("bar row", x + s * (AW + 2 * GAP), top - 10);
            g.drawString("readout", x + s * (AW + TW + 3 * GAP), top - 10);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        for (int i = 0; i < LABELS.length; i++) g.drawString(LABELS[i], 8, top + s * (i * (AH + GAP) + AH / 2) + 6);
        javax.imageio.ImageIO.write(img, "png", new File(out));
    }
}
