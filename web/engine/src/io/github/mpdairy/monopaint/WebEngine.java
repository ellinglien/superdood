package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.jso.JSByRef;
import org.teavm.jso.JSExport;

/**
 * The app's engine for the browser (web/engine, compiled to WebAssembly by TeaVM; engine.js is its JS face). A page is
 * the drawing screen's: {@link InkWashLayers#blank} with a {@link WatercolorSession}, strokes started as the drawing screen
 * starts them ({@link QuickStroke#start}), wet paint settled a frame at a time ({@link WatercolorSession#tickDrying}).
 * Pages are numbers; greys are a byte a pixel, row by row, 255 clear; rects are x0, y0, x1, y1 (x1, y1 exclusive).
 * One JS thread: quick strokes render at lift ({@link QuickStroke#renderAtLift}).
 */
public final class WebEngine {
    private static final Map<String, byte[]> ASSETS = new HashMap<>();
    static { QuickStroke.renderAtLift = true; Assets.install(WebEngine::open); }
    private static ByteArrayInputStream open(String path) throws IOException {
        byte[] b = ASSETS.get(path);
        if (b == null) throw new IOException("no asset " + path);
        return new ByteArrayInputStream(b);
    }
    private static final class Page {
        final ToneDocument d; final WatercolorSession session; DrawingStroke stroke;
        /** Record and Replay: the stroke being drawn's samples ({@link StrokeLog}), or null. */
        StrokeLog.Recorder recorder;
        /** What shows over and under the page (tracing paper's ghosts, Replay's veil; null: none) and its layers' steps, as the drawing screen shows them ({@link ToneDocument#shownTone}). */
        GhostPlane.Planes ghost;
        int layerSteps;
        Page(int w, int h) { this(InkWashLayers.blank(w, h), new WatercolorSession()); }
        Page(ToneDocument d, WatercolorSession session) { this.d = d; this.session = session; }
    }
    private static final List<Page> PAGES = new ArrayList<>();
    private static Page at(int p) { return PAGES.get(p); }

    /** The Manta's notebooks on a live page (WebBooks: books.js's shelf, words and Export Page's sizes). */
    @JSExport public static WebBooks books() { return WebBooks.ONE; }

    /** A live page's sealing with its link's key ({@link LiveCrypt}, as the Manta's), by its number. */
    private static final List<LiveCrypt> CRYPTS = new ArrayList<>();
    @JSExport public static int crypt(String key) { CRYPTS.add(new LiveCrypt(key)); return CRYPTS.size() - 1; }
    @JSExport public static String access(int c) { return CRYPTS.get(c).access; }
    /** {@code plain} sealed under {@code nonce}, 12 bytes from crypto.getRandomValues (no SecureRandom here). */
    @JSExport @JSByRef public static byte[] seal(int c, @JSByRef byte[] nonce, @JSByRef byte[] plain) { return CRYPTS.get(c).seal(nonce, plain); }
    /** What was sealed with this key; null when it wasn't (another key, any byte changed). */
    @JSExport @JSByRef public static byte[] open(int c, @JSByRef byte[] sealed) { try { return CRYPTS.get(c).open(sealed); } catch (IOException e) { return null; } }
    /** An asset's bytes by its path under app/src/main/assets ("textures/felt.bin"), before the first stroke. */
    @JSExport public static void asset(String path, @JSByRef byte[] bytes) { ASSETS.put(path, bytes.clone()); }
    /** A blank page (the Wash and Ink layers); its number. */
    @JSExport public static int page(int w, int h) { PAGES.add(new Page(w, h)); return PAGES.size() - 1; }
    /** Lets page {@code p} go (its number isn't used again). */
    @JSExport public static void free(int p) { PAGES.set(p, null); }
    /** Whether the app reads {@code code} (an M code, or an old I or P code). */
    @JSExport public static boolean reads(String code) { return BrushImport.decode(code) != null; }

    // ---- Sharp dry brushes (SharpView, as the Manta's): a kept stroke drawn again finer when zoomed ----
    /** Whether {@code code}'s brush stays sharp when zoomed ({@link SharpStrokes#staysSharp}): Brush Studio's words. */
    @JSExport public static boolean staysSharp(String code) { ModularBrush b = BrushImport.decode(code); return b != null && SharpStrokes.staysSharp(b); }
    /** The grid a view at {@code scale} screen px a page px draws sharp at ({@link SharpView#stepFor}; 0: none). */
    @JSExport public static int sharpStep(double scale) { return SharpView.stepFor(scale); }
    /** The kept strokes' brushes worked out ({@link SharpView#kept}); no view of its own (its thread is the Manta's). */
    private static final java.util.Map<StrokeLog.Entry, Object> SHARP_LAID = new java.util.IdentityHashMap<>();
    /**
     * Page {@code p}'s tile at grid {@code k}, column {@code tx}, row {@code ty} (of {@link SharpView#TILE} page px) drawn
     * finer: {@code (TILE k)²} greys row by row, −1 where the page shows as it is; empty where nothing is drawn finer.
     * The page's own composite (its ghosts and layer steps): a host shows it over the page's pixels.
     */
    @JSExport @JSByRef public static int[] sharpTile(int p, int k, int tx, int ty) {
        Page g = at(p);
        int x = tx * SharpView.TILE, y = ty * SharpView.TILE, w = Math.min(SharpView.TILE, g.d.width - x), h = Math.min(SharpView.TILE, g.d.height - y);
        if (k < 1 || w < 1 || h < 1) return new int[0];
        java.util.List<SharpView.Kept> here = new ArrayList<>();
        for (SharpView.Kept e : SharpView.kept(g.d, SHARP_LAID)) if (e.box[0] < x + w && e.box[2] > x && e.box[1] < y + h && e.box[3] > y) here.add(e);
        if (here.isEmpty()) return new int[0];
        short[] t = SharpView.render(SharpView.Area.of(g.d, k, x, y, w, h, here, g.ghost, g.layerSteps, 0, null));
        if (t == SharpView.NONE) return new int[0];
        int n = SharpView.whole(k, 0, 0);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = t[i];
        return out;
    }
    /**
     * Page {@code p}'s page px {@code x, y, w × h} at grid {@code k} ({@link #sharpTile} stitched): {@code (w k) × (h k)}
     * greys, −1 where the page shows as it is (Brush Studio's close-up).
     */
    @JSExport @JSByRef public static int[] sharpArea(int p, int k, int x, int y, int w, int h) {
        int fw = w * k, fh = h * k, side = SharpView.TILE * k;
        int[] out = new int[Math.max(0, fw * fh)];
        java.util.Arrays.fill(out, -1);
        for (int ty = Math.floorDiv(y, SharpView.TILE); ty * SharpView.TILE < y + h; ty++) for (int tx = Math.floorDiv(x, SharpView.TILE); tx * SharpView.TILE < x + w; tx++) {
            if (tx < 0 || ty < 0) continue;
            int[] t = sharpTile(p, k, tx, ty);
            if (t.length == 0) continue;
            for (int fy = 0; fy < side; fy++) {
                int oy = (ty * SharpView.TILE - y) * k + fy;
                if (oy < 0 || oy >= fh) continue;
                for (int fx = 0; fx < side; fx++) {
                    int ox = (tx * SharpView.TILE - x) * k + fx;
                    if (ox >= 0 && ox < fw) out[oy * fw + ox] = t[fy * side + fx];
                }
            }
        }
        return out;
    }
    /**
     * A new name for the brush {@code code}, as Brush Lab gives one ({@link BrushNames#name}): passing by the names in
     * {@code library} (a line each, newest first) and repeats of its latest; empty when the code doesn't read. Brush Studio's.
     */
    @JSExport public static String brushName(String code, String library) {
        ModularBrush b = BrushImport.decode(code);
        if (b == null) return "";
        List<String> names = new ArrayList<>();
        for (String n : library.split("\n")) if (!n.isEmpty()) names.add(n);
        String name = BrushNames.name(b, n -> { for (String m : names) if (BrushLibrary.sameName(m, n)) return true; return false; }, names);
        return name == null ? "" : name;
    }
    /**
     * Brush Studio's edit of a code its JS model can't hold all of (a newer tip, After, a print): {@code before} is the
     * model's own code for {@code app}, {@code after} that code edited. What the edit changed is {@code after}'s; the rest,
     * the style, seed, nib, each part's choice and each dial, stays as {@code app} has it. {@code after} when one doesn't read.
     */
    @JSExport public static String keptCode(String app, String before, String after) {
        ModularBrush a = ModularCode.decode(app), b = ModularCode.decode(before), c = ModularCode.decode(after);
        if (a == null || b == null || c == null) return after;
        ModularBrush out = new ModularBrush(c.name, c.seed == b.seed ? a.seed : c.seed, c.style == b.style ? a.style : c.style);
        out.nib = c.nib == b.nib ? a.nib : c.nib;
        for (BrushPart p : BrushPart.values()) {
            ModularBrush.Part was = b.part(p), now = c.part(p);
            if (now.choice != was.choice) { out.set(p, now.copy()); continue; }
            ModularBrush.Part q = a.part(p).copy();
            for (PartDial d : now.choice.dials) if (now.get(d) != was.get(d) && q.has(d)) q.set(d, now.get(d));
            out.set(p, q);
        }
        return ModularCode.encode(out.normalize());
    }
    /**
     * Brush Studio's tile of {@code code}, ARGB, as the app's cards draw it ({@link WildSample}): its loop at {@code dark}
     * ({@code role} "loop"), else the card wave of the part named {@code role} ("tip", "medium", …). Null when the code doesn't read.
     */
    @JSExport @JSByRef public static int[] studioSample(String code, String role, int w, int h, double dark) {
        ModularBrush b = ModularCode.decode(code);
        if (b == null) return null;
        if ("loop".equals(role)) return WildSample.modular(b, ModularSizes.sinceBlack(b, dark), WildSample.Shape.LOOP, w, h, true);
        for (BrushPart p : BrushPart.values()) if (p.name().equalsIgnoreCase(role)) return WildSample.part(b, p, w, h);
        return null;
    }
    /**
     * Brush Studio's part cards for {@code app} as the app's Brush Lab has them, as JSON keyed by part ("tip", …, "later",
     * in {@link BrushPart#SHOWN_LATER}'s order): its label, choice and words, the choices offered ({@link BrushLab#chips}),
     * the dials ({@link BrushLab#dials}: each one's part, key, label, value, range, default and options); {@code held}:
     * {@code own} (Studio's JS model's code for it) differs there, or Vector Ink sets the part aside ({@link VectorInk#setAside}, its
     * Tip too), so the card is the app's, with what Brush Lab shows of it. Null when {@code app} doesn't read.
     */
    @JSExport public static String studioParts(String app, String own) {
        ModularBrush a = ModularCode.decode(app), o = ModularCode.decode(own);
        if (a == null) return null;
        J j = new J().o();
        for (BrushPart part : BrushPart.SHOWN_LATER) {
            ModularBrush.Part p = a.part(part);
            j.k(part.name().toLowerCase(java.util.Locale.ROOT)).o().k("held").v(o == null || !p.sameAs(o.part(part)) || VectorInk.is(a) && (VectorInk.setAside(part) || part == BrushPart.TIP)).k("label").v(part.label)
             .k("name").v(part == BrushPart.HAND ? p.choice.label : ModularWords.choiceWords(a, part)).k("choices").a();
            for (PartChoice c : BrushLab.chips(a, part)) j.o().k("k").v(c.key).k("label").v(c.label).k("on").v(c == p.choice).e();
            j.e().k("dials").a();
            for (PartDial d : BrushLab.dials(a, part)) {
                ModularBrush.Part q = a.part(d.part);
                double[] range = q.choice.range(d);
                double v = q.get(d);
                j.o().k("part").v(d.part.name().toLowerCase(java.util.Locale.ROOT)).k("key").v(d.key).k("label").v(q.choice.labelOf(d)).k("v").v(v)
                 .k("lo").v(range[0]).k("hi").v(range[1]).k("def").v(q.choice.defaultOf(d)).k("int").v(d.integer).k("shown").v(d.shown(v, q)).k("options");
                if (d.choice()) { j.a(); for (String s : q.choice.optionsOf(d)) j.v(s); j.e(); } else j.raw("null");
                j.e();
            }
            j.e().e();
        }
        return j.e().toString();
    }
    /**
     * {@code app} edited on one of Brush Studio's app cards ({@link #studioParts}): {@code part}'s choice switched to
     * {@code choice} as Brush Lab's chip does ({@link BrushLab#switched}), or with no choice, {@code part}'s dial
     * {@code dial} set to {@code value} as its slider does ({@link BrushLab#slide}, {@link BrushLab#settle}). {@code app}
     * as it was when nothing matches.
     */
    @JSExport public static String studioEdit(String app, String part, String choice, String dial, double value) {
        ModularBrush b = ModularCode.decode(app);
        if (b == null) return app;
        for (BrushPart p : BrushPart.values()) {
            if (!p.name().equalsIgnoreCase(part)) continue;
            if (choice != null && !choice.isEmpty()) {
                for (PartChoice c : p.choices()) if (c.key.equals(choice)) { if (c != b.choice(p)) BrushLab.switched(b, c); return ModularCode.encode(b); }
                return app;
            }
            for (PartDial d : b.part(p).choice.dials) if (d.key.equals(dial)) { BrushLab.slide(b, d, value); BrushLab.settle(b, d); return ModularCode.encode(b); }
        }
        return app;
    }

    /**
     * Marks laid before any stroke, on the layer and kind {@code code}'s brush draws on (as the parity harness lays its
     * scene, WebParity.scene, and as the web engine's one page holds them): a picture's grey band, a test page's ink.
     */
    @JSExport public static boolean ground(int p, String code, double size, double dark, int x, int y, int w, int h, @JSByRef byte[] greys) {
        ModularBrush b = BrushImport.decode(code);
        if (b == null) return false;
        ToneDocument d = at(p).d;
        at(p).session.settleNow(d);
        ResolvedBrush r = ResolvedBrush.of(b, size, dark);
        d.selectLayer(ModularStroke.layerOf(d, b));
        d.begin(1, r.kind);
        try { d.setTones(x, y, w, h, ints(greys)); } finally { d.finish(); }
        return true;
    }

    /** Pen down with {@code code}'s brush at {@code size} px and {@code dark}; false (and no stroke) if the code doesn't read. */
    @JSExport public static boolean down(int p, String code, double size, double dark) {
        ModularBrush b = BrushImport.decode(code);
        if (b == null) return false;
        Page g = at(p);
        // As the drawing screen: wet paint stays wet under a wet stroke; anything else settling is laid where it is.
        if (ResolvedBrush.of(b, size, dark).wet != null) g.session.settleBefore(g.d, b); else g.session.settleNow(g.d);
        g.stroke = QuickStroke.start(g.d, b, size, dark, g.session);
        // Sharp dry brushes: a brush kept sharp is logged, so its stroke draws again finer when zoomed (Brush Studio's close-up).
        if (SharpStrokes.takes(b, size, dark)) { g.recorder = new StrokeLog.Recorder(ModularCode.encode(b), size, dark); SharpStrokes.keep(g.recorder, b, size, dark, g.session); }
        return true;
    }
    /** Pen down with the eraser: {@code size} px at its broadest, taking {@code kinds} ({@link ToneDocument#KIND_PEN} and the rest; 0 everything). */
    @JSExport public static void eraser(int p, double size, int kinds) {
        Page g = at(p);
        g.session.settleNow(g.d);
        g.stroke = EraseBothStroke.pen(g.d, (float) size, kinds);
    }
    /** A sample: page px, the pen's travel 0 to 1 (the web's pressure, after the Manta's curve), its time in ms (NaN untimed), tilt in degrees (NaN upright). */
    @JSExport public static void sample(int p, double x, double y, double travel, double ms, double tiltX, double tiltY) {
        Page g = at(p);
        float pressure = ToolSettings.pressure((float) travel);
        if (g.recorder != null) g.recorder.add((float) x, (float) y, pressure, (float) tiltX, (float) tiltY, Double.isNaN(ms) ? -1 : (long) ms);
        // Sharp dry brushes: a stroke kept sharp draws the sample as the log keeps it (as DrawingPad.feed).
        if (g.recorder != null && g.recorder.keeps) {
            g.recorder.kept(SHARP_SAMPLE, SHARP_TIME);
            g.stroke.sample(SHARP_SAMPLE[0], SHARP_SAMPLE[1], SHARP_SAMPLE[2], SHARP_SAMPLE[3], SHARP_SAMPLE[4], SHARP_TIME[0]);
        } else g.stroke.sample((float) x, (float) y, pressure, (float) tiltX, (float) tiltY, Double.isNaN(ms) ? PenSpeed.UNTIMED : (long) ms);
    }
    private static final float[] SHARP_SAMPLE = new float[5];
    private static final long[] SHARP_TIME = new long[1];
    /** The samples so far onto the page (once an input event's samples are in). */
    @JSExport public static void present(int p) { at(p).stroke.present(); }
    /** Lift: the stroke finished, one Undo step; whether it changed the page. */
    @JSExport public static boolean lift(int p) {
        Page g = at(p);
        DrawingStroke s = g.stroke;
        StrokeLog.Recorder recorder = g.recorder;
        g.stroke = null; g.recorder = null;
        boolean changed = s != null && s.finish();
        if (recorder != null && recorder.keeps) recorder.plane = g.d.selectedAddress();
        // Record and Replay: the stroke, kept with the page; one that changed nothing too (what it laid or picked up counts).
        if (recorder != null) g.d.logStroke(recorder, changed);
        return changed;
    }
    /** One frame of whatever still settles (wet paint, a Slow wash, a bath, halos); whether more is left. */
    @JSExport public static boolean tick(int p) { Page g = at(p); return g.session.tickDrying(g.d); }
    /** Whether anything still settles (ticks would change the page). */
    @JSExport public static boolean settling(int p) { return at(p).session.settling(); }
    /** Everything still settling laid where it is, and fading ink run to its end: a picture's last step. */
    @JSExport public static void dry(int p) {
        Page g = at(p);
        g.session.dryAll(g.d);
    }
    /** What changed since the last call, {x0, y0, x1, y1} (exclusive), or four zeros. */
    @JSExport @JSByRef public static int[] dirty(int p) {
        ToneDocument d = at(p).d;
        int[] r = d.dirty();
        d.clearDirty();
        if (r == null) return new int[4];
        return new int[]{Math.max(0, r[0]), Math.max(0, r[1]), Math.min(d.width, r[2]), Math.min(d.height, r[3])};
    }
    /** The page as it shows (every layer and plane stacked) over the rect, a byte a pixel. */
    @JSExport @JSByRef public static byte[] tones(int p, int x, int y, int w, int h) {
        Page g = at(p);
        ToneDocument d = g.d;
        byte[] out = new byte[w * h];
        // The stack looked up once (ToneDocument.compositeTones), not a pixel at a time: since received marks lie on
        // their kind's planes, a guest's catch-up after a reconnect pulled each change seconds slower a pixel at a time.
        if (g.ghost == null && g.layerSteps == 0 && x >= 0 && y >= 0 && x + w <= d.width && y + h <= d.height) {
            int[] t = new int[w * h];
            d.compositeTones(x, y, w, h, t, 0, w);
            for (int i = 0; i < t.length; i++) out[i] = (byte) t[i];
            return out;
        }
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            int px = x + col, py = y + row;
            out[row * w + col] = (byte) (px < 0 || py < 0 || px >= d.width || py >= d.height ? 255 : shown(g, px, py));
        }
        return out;
    }
    private static int shown(Page g, int x, int y) {
        GhostPlane.Planes p = g.ghost;
        return p == null && g.layerSteps == 0 ? g.d.compositeTone(x, y) : g.d.shownTone(x, y, p == null ? null : p.under, g.layerSteps, p == null ? null : p.overTones, p == null ? null : p.overAlpha);
    }
    /**
     * Greys from another page (a live page's change: what that page showed) laid as the Manta lays them (WebParity.lay:
     * LiveProbe.onLayer, then {@link PictureImport#lay}), only where they darken; {@code set} (one that lightens): the
     * page made to show them, lighter too ({@link PictureImport#lay}'s {@code lighter}). One Undo step. {@code kinds}: the
     * marks' kind (a live change's), laid on its plane so Show Kinds fades them; 0 none, on the layer's base. {@code layer}:
     * the layer it was drawn on, 1 to 3 (LiveChange.layer), as the Manta lays it ({@link InkWashLayers#drawOn}): the
     * three pairs made first on a page of one, this page's own layer in use kept.
     */
    @JSExport public static void lay(int p, int x, int y, int w, int h, @JSByRef byte[] greys, boolean set, int kinds, int layer) {
        Page g = at(p);
        g.session.settleNow(g.d);
        int[] all = ints(greys);
        InkWashLayers.drawOn(g.d, layer < 1 || layer > InkWashLayers.LAYERS ? 2 : layer, () -> {
            if (!set) WebParity.lay(g.d, x, y, w, h, all, kinds);
            else PictureImport.lay(g.d, x, y, w, h, all, false, true, false, kinds);
        });
    }
    /**
     * The page made to show {@code greys} over the whole rect (255 paper), as a set change is laid ({@link #lay}) but exact
     * ({@link PictureImport#lay}'s {@code exact}): a live page's own Undo, its pixels must show as they were, those it
     * shows already left as they are. One Undo step. On {@code layer} (1 to 3), the one the change taken back was drawn
     * on ({@link InkWashLayers#drawOn}); else the layer in use.
     */
    @JSExport public static void picture(int p, int x, int y, int w, int h, @JSByRef byte[] greys, int layer) {
        Page g = at(p);
        g.session.settleNow(g.d);
        int[] all = new int[greys.length];
        for (int i = 0; i < all.length; i++) all[i] = Math.min(254, greys[i] & 255);
        InkWashLayers.drawOn(g.d, layer < 1 || layer > InkWashLayers.LAYERS ? layer(p) : layer, () -> PictureImport.lay(g.d, x, y, w, h, all, false, true, true));
    }
    /** The layer button ({@link DrawLayout#LAYERS_ROW}): page {@code p}'s layer in use, 1 to 3 ({@link InkWashLayers#layer}; 1 for an old four-pair page's lowest). */
    @JSExport public static int layer(int p) { return Math.max(1, InkWashLayers.layer(at(p).d)); }
    /** What the layer button says, "Layer 2 of 3" ({@link DrawLayout#layerSaid}; this page hides none). */
    @JSExport public static String layerSaid(int p) { return DrawLayout.layerSaid(layer(p), false); }
    /** The layer a tap {@code y} down the layer button {@code height} tall picks ({@link DrawLayout#layerAt}): its top third 3. */
    @JSExport public static int layerAt(double y, double height) { return DrawLayout.layerAt((float) y, 0, (float) height); }
    /**
     * The layer button's tap, as the Manta's ({@code PaintActivity.pickLayer}): layer {@code layer} drawn on, a page of fewer
     * than three pairs made three first, one Undo step, a wash still settling laid first; this browser's alone (each person
     * picks a layer for themselves). Its note, "Layer 3"; "" when it is in use.
     */
    @JSExport public static String pickLayer(int p, int layer) {
        Page g = at(p);
        if (InkWashLayers.layer(g.d) == layer) return "";
        if (InkWashLayers.pairs(g.d) < InkWashLayers.LAYERS) g.session.settleNow(g.d);
        return InkWashLayers.pickLayer(g.d, layer) ? DrawLayout.layerNote(layer) : "";
    }
    /** The drawing screen's Undo (a floating bath's last stroke first) and Redo; whether anything changed. */
    @JSExport public static boolean undo(int p) { Page g = at(p); return g.session.undo(g.d); }
    @JSExport public static boolean redo(int p) { Page g = at(p); g.session.settleNow(g.d); return g.d.redo(); }
    /** Whether Undo and Redo have anything to take back or put back: the browser's Undo and Redo buttons, greyed when not. */
    @JSExport public static boolean canUndo(int p) { Page g = at(p); return g.d.canUndo() || g.session.floating() != null; }
    @JSExport public static boolean canRedo(int p) { return at(p).d.canRedo(); }

    // ---- The drawing screen for a live page's guest (the browser app's M1): the Manta's layout, tools and gestures. ----

    /**
     * The drawing screen's chrome for a {@code w × h} dp (CSS px) screen ({@link DrawLayout}), as JSON: the column's
     * sizes, its parts' tops ({@link DrawLayout#tops}) and names, the page bar, the canvas, the brush line, the picker box,
     * the constants its widgets are drawn by, and the words they say.
     */
    @JSExport public static String layout(int w, int h) {
        DrawLayout d = new DrawLayout(w, h);
        J j = new J().o();
        j.k("compact").v(d.compact).k("side").v(d.side).k("edge").v(d.edge).k("inner").v(d.inner).k("half").v(d.half()).k("trays").v(d.trays)
         .k("icon").v(d.icon).k("gap").v(d.gap).k("slot").v(d.slot).k("face").v(d.face).k("wet").v(d.wet).k("box").v(d.box).k("spare").v(d.spare)
         .k("fit").v(d.fit).k("menuRow").v(d.menuRow()).k("pageBar").v(d.pageBar()).k("pageWidth").v(d.pageWidth()).k("pageHeight").v(d.pageHeight())
         .k("canvasHeight").v(d.canvasHeight()).k("footHeight").v(d.footHeight()).k("columnHeight").v(d.columnHeight()).k("lineStart").v(d.lineStart())
         .k("faceSide").v(d.faceSide()).k("layers").v(d.layers).k("zoomButton").v(DrawLayout.zoomButton()).k("trackTop").v(DrawLayout.trackTop()).k("trackFoot").v(DrawLayout.trackFoot(d.box));
        j.k("tops").a(); for (int t : d.tops()) j.v(t); j.e();
        j.k("parts").a(); for (DrawLayout.Part p : DrawLayout.COLUMN) j.v(p.name()); j.e();
        j.k("strip").a(); for (int x : d.stripPlaces(d.pageWidth(), false)) j.v(x); j.e();
        j.k("c").o().k("PAD").v(DrawLayout.PAD).k("TRAY_TOP").v(DrawLayout.TRAY_TOP).k("SIDE_BAR").v(DrawLayout.SIDE_BAR).k("BAR_GAP").v(DrawLayout.BAR_GAP)
         .k("BAR_INSET").v(DrawLayout.BAR_INSET).k("FACE_GAP").v(DrawLayout.FACE_GAP).k("PAIR_PAD").v(DrawLayout.PAIR_PAD).k("FACE_MARK").v(DrawLayout.FACE_MARK)
         .k("FACE_MARK_GAP").v(DrawLayout.FACE_MARK_GAP).k("STRIP_MARK").v(DrawLayout.STRIP_MARK).k("READOUT_ROWS").v(DrawLayout.READOUT_ROWS)
         .k("TIP_TRIM").v(DrawLayout.TIP_TRIM).k("ZOOM_LOCK").v(DrawLayout.ZOOM_LOCK).k("ZOOM_GAP").v(DrawLayout.ZOOM_GAP).k("ZOOM_PAD").v(DrawLayout.ZOOM_PAD)
         .k("LINE_PAD").v(DrawLayout.LINE_PAD).k("LINE_NAME").v(DrawLayout.LINE_NAME).k("BRUSH_LINE").v(DrawLayout.BRUSH_LINE).k("PAGE_RULE").v(DrawLayout.PAGE_RULE).k("UNDO_BUTTON").v(UNDO_BUTTON).k("LAYERS_ICON").v(DrawLayout.LAYERS_ICON)
         .k("SHADE_END").v(ShadeStrip.END).k("CAPTION").v(TypeStyle.CAPTION.sp).k("LABEL").v(TypeStyle.LABEL.sp).k("ERASER").v(ToolSlots.ERASER).k("COUNT").v(ToolSlots.COUNT).k("SMUDGE").v(ToolSlots.SMUDGE).k("STAMP").v(ToolSlots.STAMP).k("FILL").v(ToolSlots.FILL)
         .k("PINCH_SLOP").v(PinchPan.SLOP_DP).k("LEAST_SPAN").v(PinchPan.LEAST_SPAN_DP).k("FINGER_TRAVEL").v(FingerTool.PRESSURE_TRAVEL)
         .k("HOVER_MIN").v(HoverNib.MIN_RADIUS).k("HOVER_SHOWN").v(HoverNib.SHOWN_MS).k("HOVER_AWAY").v(HoverNib.AWAY_MS).k("TICK_SIDE").v(HoverNib.TICK_SIDE).k("ERASER_LEAST").v(ToolSwatches.ERASER_LEAST)
         .k("LIVE_EVENT").v(LivePresence.EVENT).k("LIVE_LOST_MS").v(LivePresence.LOST_MS).k("LIVE_STILL").v(LivePresence.STILL).k("LIVE_SILENT_MS").v(LivePresence.SILENT_MS).k("LIVE_TICKS_MS").v(LiveChange.TICKS_MS).e();
        j.k("w").o().k("eraser").v(DrawLayout.toolName(ToolSlots.ERASER)).k("zoom").v("Zoom").k("undo").v("Undo").k("redo").v("Redo").e();
        return j.e().toString();
    }
    /** The slider's position (0 fine to 1 broad) at {@code y} dp in a box {@code tall} dp ({@link DrawLayout#sliderAt}), and where its tab is at {@code t}. */
    @JSExport public static double sliderAt(double y, double tall) { return DrawLayout.sliderAt((float) y, (float) tall); }
    @JSExport public static double sliderY(double t, double tall) { return DrawLayout.sliderY(t, (float) tall); }
    /** Whether a pen landing at x, y in the picker box drives the grey strip ({@link DrawLayout#boxShade}), not the size. */
    @JSExport public static boolean boxShade(double x, double y, double left, double top, double right, double foot) { return DrawLayout.boxShade((float) x, (float) y, (float) left, (float) top, (float) right, (float) foot); }
    /** The grey strip's darkness at {@code y} ({@link DrawLayout#shadeAt}), px of a strip {@code length} px from {@code top}. */
    @JSExport public static double shadeAt(double y, double top, int length, int endpoint) { return DrawLayout.shadeAt((float) y, (float) top, length, endpoint); }
    /** The grey strip's end zones, px ({@link ShadeStrip#endpoint}); its pixels lying along x, in the panel's dots ({@link ShadeStrip#pixels}); where its tab is for {@code darkness}. */
    @JSExport public static int stripEnd(int length, int most) { return ShadeStrip.endpoint(length, most); }
    @JSExport @JSByRef public static int[] stripPixels(int length, int height, int endpoint) { return ShadeStrip.pixels(length, height, endpoint); }
    @JSExport public static double stripMark(double darkness, int length, int endpoint) { return ShadeStrip.markerAt(ToolSwatches.tone(darkness), length, endpoint); }
    /** A tool's size at slider position {@code t}, as it draws at it ({@link ToolSwatches#sizeAt}, {@link ToolSwatches#ownPx}); its position for {@code px}. */
    @JSExport public static double sizeAt(int tool, double t) { return ToolSwatches.ownPx(tool == ToolSlots.ERASER, ToolSwatches.sizeAt(t)); }
    @JSExport public static double position(double px) { return ToolSwatches.position(px); }
    /** What a tap on {@code tool} does while {@code selected} is in use ({@link DrawLayout#tap}): 0 select, 1 the smudge brushes, 2 nothing. */
    @JSExport public static int tap(int selected, int tool) { return DrawLayout.tap(selected, tool).ordinal(); }
    /** The size slider's and the grey strip's words ({@link DrawLayout#sizeSaid}, {@link ToolSwatches#greyWords}). */
    @JSExport public static String sizeSaid(double px) { return DrawLayout.sizeSaid(px); }
    @JSExport public static String greyWords(int tool, double darkness) { Brush b = brush(tool); return b == null ? "" : ToolSwatches.greyWords(darkness, ModularSizes.swatch(b.parts, darkness)); }

    /** The host's tools ({@link LiveTools}) a guest draws with; the first launch's toolbar until a host's arrive. */
    private static LiveTools tools;
    private static LiveTools tools() { return tools != null ? tools : (tools = first(1920, 2560, false)); }
    private static LiveTools first(int w, int h, boolean kaleidoscope) {
        java.util.List<String> lines = new ArrayList<>();
        for (int i = 0; i < ToolSlots.COUNT; i++) lines.add(ToolSlots.first(i).write() + "\t" + ToolSlots.firstPx(i) + "\t" + ToolSlots.firstGrey(i));
        return new LiveTools(w, h, ToolSlots.Toolbar.parse(lines), 0, ToolSlots.FIRST_ERASER_PX, kaleidoscope ? Mirrors.NONE.kaleidoscope(w, h) : Mirrors.NONE);
    }
    /** The bar's tool {@code tool}: the guest's own slot ({@link #slots}) or smudge tool, or null for the eraser. */
    /** The bar's tool {@code tool}'s brush; {@link #LAB}: Brush Lab's, as it now stands. */
    static Brush brush(int tool) { return tool == LAB ? lab.brush() : tool < ToolSlots.COUNT || tool == ToolSlots.FILL ? slots().brush(tool) : tool == ToolSlots.SMUDGE ? slots().smudge() : null; }
    /**
     * The browser's Undo and Redo, dp wide each, on the page bar at the column's end (the Manta has its two- and
     * three-finger taps and no buttons): as wide as ‹ and ›.
     */
    static final int UNDO_BUTTON = DrawLayout.PAGE_ARROW;
    /** Brush Lab's brush as a tool ({@link #brush}): its picker box's readout and words. */
    static final int LAB = -1;
    /**
     * A host's tools ({@code raw}: inflated, as they travel less the deflate), kept for the strokes; page {@code p} takes
     * their mirrors; the guest's slots take the host's but those the guest changed ({@link #own}). As JSON ({@link
     * #toolsJson}), or "" when they don't read.
     */
    @JSExport public static String tools(int p, @JSByRef byte[] raw) {
        try { tools = LiveTools.parse(raw.clone()); } catch (IOException e) { return ""; }
        // The host's Stamps' nibs, so its stamps print here as there.
        slots();
        for (StampNib nib : tools.nibs) StampNib.keep(nib);
        // The guest's own mirrors (Play's, M4) stay theirs.
        if (p >= 0 && !ownMirrors) at(p).d.setMirrors(tools.mirrors());
        ToolSlots.Toolbar bar = slots().toolbar();
        String own = "," + KEPT.get(OWN) + ",";
        for (int i = 0; i < ToolSlots.COUNT; i++)
            if (!own.contains("," + i + ",")) { bar.brushes[i] = tools.toolbar.brushes[i]; bar.px[i] = tools.toolbar.px[i]; bar.darkness[i] = tools.toolbar.darkness[i]; }
        slots().load(bar);
        return toolsJson();
    }
    /** The tools in use as JSON: the page's size, the tool in use, the eraser's size, whether mirrors act, each of the guest's slots' name, words, size, grey and drawing ({@link DrawLayout#drawing}), the smudge tool's and Fill in's. */
    @JSExport public static String toolsJson() {
        LiveTools t = tools();
        ToolSlots.Toolbar bar = slots().toolbar();
        J j = new J().o().k("width").v(t.width).k("height").v(t.height).k("selected").v(t.selected).k("eraserPx").v(t.eraserPx)
                .k("mirrors").v(t.mirrors().acts()).k("slots").a();
        for (int i = 0; i < ToolSlots.COUNT; i++) {
            Brush b = bar.brushes[i];
            String[] line = DrawLayout.lineWords(b);
            j.o().k("name").v(b.shownName()).k("words").v(line[0]).k("said").v(line[1]).k("px").v(bar.px[i]).k("dark").v(bar.darkness[i]);
            String drawn = DrawLayout.drawing(b);
            j.k("drawing"); if (drawn == null) j.raw("null"); else j.v(drawn);
            j.e();
        }
        // The smudge tool's (M4): its own brush, size and grey, its drawing the owner's spudger (InkWashBar.showIcon).
        Brush s = slots().smudge();
        ToolSwatches.Look sl = slots().swatches.look(ToolSlots.SMUDGE_KEY, s);
        String[] line = DrawLayout.lineWords(s);
        j.e().k("smudge").o().k("name").v(s.name()).k("words").v(line[0]).k("said").v(line[1]).k("px").v(sl.px).k("dark").v(sl.darkness).k("drawing").v("tool_smudge").e();
        // Fill in's (2026-11-06, a slot before): the Basic tool, its own size and grey, its drawing as a slot's was.
        Brush f = ToolSlots.fill();
        ToolSwatches.Look fl = slots().swatches.look(ToolSlots.FILL_KEY, f);
        line = DrawLayout.lineWords(f);
        j.k("fill").o().k("name").v(f.shownName()).k("words").v(line[0]).k("said").v(line[1]).k("px").v(fl.px).k("dark").v(fl.darkness).k("drawing").v(DrawLayout.drawing(f)).e();
        return j.e().toString();
    }
    /** A stand-in host's tools for the checks (no Go Live in the browser until M4): the first launch's toolbar on a {@code w × h} page, a kaleidoscope's mirrors or none; raw, as {@link #tools} takes them. */
    @JSExport @JSByRef public static byte[] hostTools(int w, int h, boolean kaleidoscope) { return first(w, h, kaleidoscope).raw(); }
    /** Page {@code p} takes the tools' mirrors (a page made after they came). */
    @JSExport public static void mirrors(int p) { at(p).d.setMirrors(tools().mirrors()); }

    /**
     * Pen down with the bar's tool {@code tool} (a slot of the tools, {@link ToolSlots#ERASER} or {@link ToolSlots#SMUDGE})
     * at {@code px} and {@code dark}, drawn by the pen or one finger ({@code finger}), as the drawing screen starts it
     * ({@code InkWash.stroke}, {@link StrokeStart#start}: mirrored as the page's mirrors are).
     */
    @JSExport public static void pen(int p, int tool, double px, double dark, int eraseKinds, boolean finger) {
        Page g = at(p);
        drawn = g;
        Brush b = brush(tool);
        // As InkWash.stroke: the tool's own paint (Keeps Paint), or the finger's with it; Grey Shift's grey for this stroke alone.
        String holder = ToolSlots.holder(tool, finger);
        g.session.holding(holder);
        g.session.swatches = slots().swatches;
        ToolSwatches.Look look = new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, px, dark);
        if (b != null) look = SwatchPicker.look(b, look, slots().swatches, g.session.held, holder);
        // Show Kinds: the eraser never takes a hidden kind (InkWash.erasing).
        int kinds = EraseKind.takes(eraseKinds, EraseKind.hidden(g.d.kindsShown()));
        g.recorder = StrokeLog.Recorder.of(b, look, kinds, holder, tool == ToolSlots.SMUDGE, finger);
        // Sharp dry brushes: the pen's stroke of a brush kept sharp is fed as the log keeps it (sample).
        if (b != null && tool != ToolSlots.SMUDGE && !finger && SharpStrokes.takes(b.parts, look.px, look.darkness)) SharpStrokes.keep(g.recorder, b.parts, look.px, look.darkness, g.session);
        g.stroke = StrokeStart.start(g.d, b, look, g.session, kinds);
    }
    /** Slot {@code slot}'s tray row ({@link WildSample#slotTray}): its brush's stroke lying across {@code w × h} px, ARGB. */
    @JSExport @JSByRef public static int[] tray(int slot, int w, int h) {
        Brush b = brush(slot);
        return WildSample.slotTray(b.parts, ToolSwatches.picture(b), w, h);
    }
    /** The ring a hovering pen shows for {@code tool} at {@code px} and {@code dark} ({@link HoverNib}): its long side, short side, angle (page px, radians). */
    @JSExport @JSByRef public static double[] nib(int tool, double px, double dark) {
        Brush b = brush(tool);
        HoverNib n = b == null ? HoverNib.eraser(px) : HoverNib.of(ResolvedBrush.of(b.parts, px, dark));
        return new double[]{n.width, n.height, n.angle};
    }
    /** The ring's radius on screen for {@code length} page px across at {@code scale} ({@link HoverNib#radius}). */
    @JSExport public static double ring(double length, double scale) { return HoverNib.radius(length, (float) scale); }
    /** The ring's dots on screen ({@link HoverNib#ticks}): x, y pairs. */
    @JSExport @JSByRef public static float[] ticks(double cx, double cy, double a, double b, double turn) { return HoverNib.ticks((float) cx, (float) cy, (float) a, (float) b, (float) turn); }
    /** The readout's grey for {@code tool} at {@code dark}: the tone it lays ({@link ModularSizes#swatch}), −1 for none (the eraser; a brush that only reworks). */
    @JSExport public static int swatch(int tool, double dark) {
        Brush b = brush(tool);
        return b == null || WildSample.reworks(b.parts) ? -1 : ModularSizes.swatch(b.parts, dark);
    }
    /**
     * The grey page tone {@code tone} shows as in the browser: the Manta's dot density for it as a smooth grey
     * ({@link DotPattern#exportGray}, as the PNG copy and the notebooks' pictures), not dots, which shimmer when scaled.
     */
    @JSExport public static int shade(int tone) { return DotPattern.exportGray(tone); }

    // The fingers ({@link MultiFingerTap}, {@link PinchPan}, {@link FingerTool}) and the view ({@link CanvasViewport}), one each: one guest a page.
    private static final MultiFingerTap TAPS = new MultiFingerTap();
    private static final PinchPan PINCH = new PinchPan();
    private static final CanvasViewport VIEW = new CanvasViewport();
    static { VIEW.whole = true; }  // the whole page on any screen (CanvasViewport.whole)
    /** A touch event for the two- and three-finger taps (Android's actions: down 0, up 1, move 2, cancel 3, a finger down 5, up 6); 2 or 3 on the tap's last lift, else 0. */
    @JSExport public static int fingers(int action, double time, @JSByRef int[] ids, @JSByRef double[] xs, @JSByRef double[] ys, double slop) {
        float[] x = new float[xs.length], y = new float[ys.length];
        for (int i = 0; i < x.length; i++) { x[i] = (float) xs[i]; y[i] = (float) ys[i]; }
        return TAPS.event(action, (long) time, ids.clone(), x, y, (float) slop);
    }
    @JSExport public static void fingersReset() { TAPS.reset(); }
    @JSExport public static boolean pinchLand(boolean locked, int a, double ax, double ay, int b, double bx, double by, double leastSpan) { return PINCH.land(locked, a, (float) ax, (float) ay, b, (float) bx, (float) by, (float) leastSpan); }
    @JSExport public static boolean pinchTracking() { return PINCH.tracking(); }
    @JSExport public static boolean pinchMoving() { return PINCH.moving(); }
    @JSExport public static int pinchFirst() { return PINCH.first(); }
    @JSExport public static int pinchSecond() { return PINCH.second(); }
    @JSExport public static boolean pinchStarts(double ax, double ay, double bx, double by, double slop) { return PINCH.starts((float) ax, (float) ay, (float) bx, (float) by, (float) slop); }
    /** A pinch's step onto the view ({@link CanvasViewport#gesture}); whether it moved. */
    @JSExport public static boolean pinchStep(double ax, double ay, double bx, double by, double leastSpan) {
        float[] g = PINCH.step((float) ax, (float) ay, (float) bx, (float) by, (float) leastSpan);
        if (g == null) return false;
        VIEW.gesture(g[0], g[1], g[2], g[3], g[4]);
        return true;
    }
    @JSExport public static void pinchEnd() { PINCH.end(); }
    /** The view's room and the page's size ({@link CanvasViewport#configure}); then where the page is: x, y, scale (view px), its percentage. */
    @JSExport public static void view(double w, double h, double pw, double ph) { VIEW.configure((float) w, (float) h, (float) pw, (float) ph); }
    @JSExport @JSByRef public static double[] viewport() { return new double[]{VIEW.x, VIEW.y, VIEW.scale(), VIEW.percent()}; }
    /** A tap on Zoom: the whole page fitted again. */
    @JSExport public static void viewReset() { VIEW.reset(); }
    /** A desktop's window ({@link CanvasViewport#wide}): wider than tall, the page fills its width; set before {@link #view}. */
    @JSExport public static void viewWide(boolean on) { VIEW.wide = on; }
    /** A trackpad's pinch or a wheel ({@link CanvasViewport#gesture}): {@code factor} about the view point {@code ox, oy}, then moved to {@code nx, ny}, view px. */
    @JSExport public static void viewGesture(double factor, double ox, double oy, double nx, double ny) { VIEW.gesture((float) factor, (float) ox, (float) oy, (float) nx, (float) ny); }
    /** The Zoom button's percentage and its screen-reader words ({@link PinchPan#caption}, {@link PinchPan#describe}: no padlock here). */
    @JSExport public static String zoomCaption(int percent) { return PinchPan.caption(percent); }
    @JSExport public static String zoomSaid(int percent) { return PinchPan.describe(percent); }
    /**
     * A guest's status ({@link LivePresence#guest}): connected, or reconnecting {@code lostMs}; {@code names} the relay's
     * listeners' ids, space apart, when {@code known}; {@code me} this guest's id; the host's told by crypt {@code c}'s key.
     */
    @JSExport public static String liveSaid(int c, boolean connected, double lostMs, boolean known, String names, String me) {
        return LivePresence.guest(connected, (long) lostMs, known ? LivePresence.names(names) : null, me, CRYPTS.get(c));
    }
    /** The guest's status {@code said} stops drawing ({@link LivePresence#cut}): its note, else null. */
    @JSExport public static String liveCut(String said) { return LivePresence.cutNote(said); }
    /** One finger drawing with {@code tool} ({@link FingerTool}): refused for the whole touch; taken back; starts once moved {@code movedDp}. */
    @JSExport public static boolean fingerRefused(int tool, boolean fingersOnly, boolean blocked, double widestDp) { return FingerTool.refused(FingerTool.Choice.of(tool), fingersOnly, blocked, false, (float) widestDp); }
    @JSExport public static boolean fingerCancels(boolean anotherFinger, boolean fingersOnly, double widestDp, boolean drawing) { return FingerTool.cancels(anotherFinger, fingersOnly, (float) widestDp, drawing); }
    @JSExport public static boolean fingerStarts(double movedDp) { return FingerTool.starts((float) movedDp); }
    @JSExport public static boolean fingerKeeps(double heldMs) { return FingerTool.keepsOnCancel((long) heldMs); }

    // ---- The guest's own bar, its long presses, the Tools page and Brush Lab (the browser app's M2) ----

    /**
     * The guest's own store, as the Manta's preferences are its ({@link BrushLibrary.Store}: the slots, their sizes and
     * greys, the library, Recent, Tools First): the page reads it in from localStorage ({@link #keep}) and writes back
     * what changed ({@link #kept}). {@link #OWN}: the slots the guest changed, which a host's tools leave alone.
     */
    private static final Map<String, String> STORE = new HashMap<>();
    private static final java.util.Set<String> CHANGED = new java.util.TreeSet<>();
    private static final BrushLibrary.Store KEPT = new BrushLibrary.Store() {
        public String get(String key) { return STORE.get(key); }
        public void put(String key, String value) { if (value == null) STORE.remove(key); else STORE.put(key, value); CHANGED.add(key); }
    };
    private static final String OWN = "live_own", TOOLS_FIRST = "tools_first";  // the second PaintPreferences.TOOLS_FIRST
    private static ToolSlots slots;
    static ToolSlots slots() {
        if (slots == null) { StampNib.storeIn(KEPT); slots = new ToolSlots(KEPT, new java.util.Random()); }
        return slots;
    }
    /** A key the page kept, read in before anything else. */
    @JSExport public static void keep(String key, String value) { STORE.put(key, value); }
    /** What changed since the last call, as JSON: each key's value, null when it went. */
    @JSExport public static String kept() {
        J j = new J().o();
        for (String k : CHANGED) { j.k(k); String v = STORE.get(k); if (v == null) j.raw("null"); else j.v(v); }
        CHANGED.clear();
        return j.e().toString();
    }
    /** Slot {@code slot} is the guest's own now: a host's tools leave it as it is. */
    private static void own(int slot) {
        String was = KEPT.get(OWN), now = was == null || was.isEmpty() ? "" + slot : was;
        if (!("," + now + ",").contains("," + slot + ",")) now = was + "," + slot;
        KEPT.put(OWN, now);
    }
    /** Each slot whose brush changed is the guest's own. */
    private static void own(boolean[] changed) { for (int i = 0; i < changed.length; i++) if (changed[i]) own(i); }
    /** The slot's size and grey set on the drawing screen (on the lift, as ToolSwatches.useSize and useGrey). */
    @JSExport public static void look(int slot, double px, double dark) {
        slots().swatches.useSize(ToolSlots.toolKey(slot), px); slots().swatches.useGrey(ToolSlots.toolKey(slot), dark);
        if (slot < ToolSlots.COUNT) own(slot);
    }
    /** Hold Opens Full Library ({@link DrawLayout#TOOLS_FIRST}): a slot's long press opens the Tools page, not the picker. */
    @JSExport public static boolean toolsFirst() { return "1".equals(KEPT.get(TOOLS_FIRST)); }
    @JSExport public static void setToolsFirst(boolean on) { KEPT.put(TOOLS_FIRST, on ? "1" : "0"); }
    /** Whether Brush Lab and the Tools page fit the screen ({@link LabLayout#fits}); a phone's has the drawing screen only. */
    @JSExport public static boolean screensFit(int w, int h) { return LabLayout.fits(w, h); }

    /**
     * The long presses' timing and words, as JSON: {@link DrawLayout#LONG_PRESS_MS} and its slop; the eraser's choices
     * ({@link EraseKind#TARGETS} as "kinds", what the eraser takes), the one in use marked by the page; the grey strip's Opacity ({@link DrawLayout#OPACITY}).
     */
    @JSExport public static String holds() {
        J j = new J().o().k("ms").v(DrawLayout.LONG_PRESS_MS).k("slop").v(DrawLayout.LONG_PRESS_SLOP).k("row").v(DrawLayout.MENU_ROW).k("kinds").a();
        for (int k : EraseKind.TARGETS) j.v(k);
        j.e().k("labels").a(); for (String l : EraseKind.CHOICES) j.v(l);
        j.e().k("notes").a(); for (int i = 0; i < EraseKind.CHOICES.length; i++) j.v(EraseKind.note(i));
        j.e().k("type").o(); for (TypeStyle t : TypeStyle.values()) j.k(t.name()).a().v(t.sp).v(t.weight).v(t.tracking).e();
        j.e().k("opacity").v(DrawLayout.OPACITY).k("opacityOnly").v(DrawLayout.OPACITY_ONLY).k("noteMs").v(DrawLayout.NOTE_MS).k("offerMs").v(PageClear.OFFER_MS);
        return j.e().toString();
    }
    /** Slot {@code tool}'s Opacity (its Opaque medium's own dial), or −1 for a brush that hasn't one ({@link DrawLayout#opacity}). */
    @JSExport public static double opacity(int tool) { Brush b = brush(tool); return DrawLayout.opacity(b) ? b.parts.part(BrushPart.MEDIUM).get(PartDial.COVER) : -1; }
    /** The slot's Opacity set to {@code value}, the guest's own ({@link DrawLayout#opacity(Brush, double)}); its words. */
    @JSExport public static String setOpacity(int tool, double value) {
        if (slots().use(tool, DrawLayout.opacity(brush(tool), value))) own(tool);
        return DrawLayout.opacitySaid(value);
    }

    /** The tool picker on slot {@code slot} for a {@code w × h} dp screen, as JSON ({@link DrawLayout.Picker}): its rows, its cells' sizes and its words. */
    @JSExport public static String picker(int slot, int w, int h) {
        DrawLayout.Picker p = new DrawLayout.Picker(slots().brush(slot), false);
        J j = new J().o().k("reset"); if (p.reset == null) j.raw("null"); else j.v(DrawLayout.RESET);
        j.k("stamp"); if (p.stamp) j.v(DrawLayout.STAMP_MENU); else j.raw("null");
        int columns = DrawLayout.pickerColumns(w), tip = DrawLayout.pickerTip(columns, h);
        j.k("columns").v(columns).k("cellWidth").v(DrawLayout.pickerCellWidth()).k("cellHeight").v(DrawLayout.pickerCellHeight(tip))
         .k("TIP").v(tip).k("ROOM").v(DrawLayout.pickerRoom(tip)).k("PAD").v(DrawLayout.PICKER_PAD).k("MARK").v(DrawLayout.PICKER_MARK).k("ROW").v(DrawLayout.MENU_ROW).k("LINE").v(DrawLayout.GROUP_LINE).k("LINE_ROOM").v(DrawLayout.GROUP_LINE_ROOM)
         .k("lab").v(DrawLayout.BRUSH_LAB).k("library").v(DrawLayout.ADVANCED).k("first").v(DrawLayout.TOOLS_FIRST).k("studio").v(DrawLayout.STUDIO).k("link").v(DrawLayout.STUDIO_LINK).k("FOOT_PAD").v(DrawLayout.FOOT_PAD).k("on").v(toolsFirst()).k("cells").a();
        for (int i = 0; i < p.tools.size(); i++) {
            j.o().k("name").v(p.tools.get(i).shownName()).k("on").v(p.marked.get(i)).k("line").v(p.divided.get(i)).k("drawing");
            if (p.drawings.get(i) == null) j.raw("null"); else j.v(p.drawings.get(i));
            j.e();
        }
        return j.e().e().toString();
    }
    /** The picker's cell {@code cell} put in slot {@code slot} (−1: Reset to Default), as the drawing screen's pick ({@link ToolSlots#pick}); whether it changed. */
    @JSExport public static boolean pick(int slot, int cell) {
        DrawLayout.Picker p = new DrawLayout.Picker(slots().brush(slot), false);
        ToolPresets.Preset preset = cell < 0 ? p.reset : p.tools.get(cell);
        if (preset == null || !slots().pick(slot, preset.brush())) return false;
        own(slot);
        return true;
    }
    /** Cell {@code cell}'s stroke, for a picker cell with no drawing ({@code PaintActivity.toolPicker}: the Tools page's look), ARGB. */
    @JSExport @JSByRef public static int[] pickerTray(int cell, int w, int h) { return tray(new DrawLayout.Picker(null, false).tools.get(cell).brush(), null, w, h); }
    /** {@code brush}'s tray stroke as the Tools page draws its pictures ({@link LabLibrary#traySampler}): at {@code look}'s size, or the size that fits. */
    private static int[] tray(Brush brush, ToolSwatches.Look look, int w, int h) {
        ModularBrush b = brush.parts.copy();
        return look != null ? WildSample.slotTray(b, look, w, h) : WildSample.legibleTray(b, WildSample.PREVIEW, ModularSizes.sampleSize(b, h), w, h, true);
    }

    // The Tools page (ToolsActivity): ToolsPage's state, ToolsLayout's sizes.
    private static ToolsPage page;
    private static ToolsLayout pageLayout;
    /** The slots' start carried Tools page → Brush Lab → Tools page ({@link ToolSlots.Start#write}), or null. */
    private static String[] start;
    /** The Tools page on slot {@code slot} for a {@code w × h} dp screen, its bar showing the drawing screen's slots; as {@link #toolsPage}. */
    @JSExport public static String toolsOpen(int slot, int w, int h) {
        page = new ToolsPage(slots(), KEPT, slot, start);
        start = null;
        pageLayout = new ToolsLayout(w, h);
        page.bar(new DrawLayout(w, h).trays);
        page.layout(pageLayout.perPage(), pageLayout.cardWidth);
        return toolsPage();
    }
    /** The Tools page as it stands, as JSON: ToolsLayout's sizes, its words, the slots, the selected slot's tool, the chips, pages and cards, and the note its last change left. */
    @JSExport public static String toolsPage() {
        ToolsLayout l = pageLayout;
        J j = new J().o().k("L").o();
        j.k("width").v(l.width).k("height").v(l.height).k("compact").v(l.compact).k("wide").v(l.wide).k("side").v(l.side).k("gap").v(l.gap)
         .k("apart").v(l.apart).k("top").v(l.top).k("sections").v(l.sections).k("testWidth").v(l.testWidth).k("testHeight").v(l.testHeight).k("slotWidth").v(l.slotWidth)
         .k("slotCard").v(l.slotCard).k("slotGap").v(l.slotGap).k("slotCount").v(l.slotCount).k("chips").v(l.chips()).k("columns").v(l.columns)
         .k("cardWidth").v(l.cardWidth).k("cardHeight").v(l.cardHeight).k("rows").v(l.rows).k("slotArt").v(l.slotArt()).k("slotArtHeight").v(l.slotArtHeight())
         .k("cardArt").v(l.cardArt()).k("cardArtHeight").v(l.cardArtHeight()).k("chipPad").v(l.chipPad()).k("chipGap").v(l.chipGap()).k("nameRoom").v(l.nameRoom())
         .k("sheetWidth").v(l.sheetWidth()).k("BAR").v(ToolsLayout.BAR).k("FIND").v(ToolsLayout.FIND).k("PAGE_BUTTON").v(ToolsLayout.PAGE_BUTTON).k("CHIP").v(ToolsLayout.CHIP)
         .k("NAME_GAP").v(ToolsLayout.NAME_GAP).k("ROW_GAP").v(ToolsLayout.ROW_GAP).k("PAD_RADIUS").v(ToolsLayout.PAD_RADIUS).k("NAME_ROW").v(ToolsLayout.NAME_ROW)
         .k("TRY_HEAD").v(ToolsLayout.TRY_HEAD).k("SHEET_PAD").v(ToolsLayout.SHEET_PAD).k("SHEET_ROW").v(ToolsLayout.SHEET_ROW).k("MARK").v(DrawLayout.MARK)
         .k("UNDERLINE").v(DrawLayout.UNDERLINE).k("MARK_CLEAR").v(DrawLayout.MARK_CLEAR).k("NAME_END").v(ToolsLayout.NAME_END).k("SLOT_PAD").v(ToolsLayout.SLOT_PAD).k("DOT_ROW").v(ToolsLayout.DOT_ROW).e();
        j.k("w").o().k("title").v(ToolsLayout.TITLE).k("job").v(ToolsLayout.JOB).k("first").v(DrawLayout.TOOLS_FIRST).k("lab").v(ToolsLayout.LAB).k("search").v(ToolsLayout.SEARCH)
         .k("load").v(ToolKits.LOAD).k("cancel").v(ToolKits.CANCEL).k("clear").v("Clear").e();
        j.k("slot").v(page.slot()).k("slots").a();
        for (int i = 0; i < l.slotCount; i++) {
            Brush b = page.current(i);
            j.o().k("name").v(ToolsPage.name(b)).k("said").v(b.shownName()).k("changed").v(page.changed(i)).k("key").v(b.identity() + " " + page.look(i).px).k("drawing"); drawing(j, b); j.e();
        }
        Brush b = page.current();
        ToolsPage.Card card = ToolsPage.card(b, l.nameRoom() / 2f);
        j.e().k("tool").o().k("name").v(card.name).k("line").v(!card.line.isEmpty() && !card.line.equalsIgnoreCase(card.name) ? " · " + card.line : "")
         .k("about").v(card.name + ": " + BrushVoice.about(b.parts)).k("try").v(ToolsLayout.TRY + card.name).k("reset");
        if (page.edited() == null) j.raw("null"); else j.v(DrawLayout.RESET);
        j.k("room").v(page.edited() != null ? l.nameRoom(true) : l.nameRoom() * 2 / 3);
        j.e().k("searching").v(page.searching()).k("search").v(page.search()).k("chips").a();
        // Each chip's index for toolsChip is its own: the row leaves out a kind no shown tool is under (Odd Ones).
        for (ToolsPage.Chip c : ToolsLayout.rowChips()) j.o().k("label").v(c.label).k("on").v(c == page.filter() && !page.searching()).k("i").v(c.ordinal()).e();
        // More Brushes, beside Search (its chip's index for toolsChip: the last).
        ToolsPage.Chip more = ToolsPage.Chip.MORE;
        j.e().k("more").o().k("label").v(more.label).k("on").v(more == page.filter() && !page.searching()).k("i").v(more.ordinal()).e();
        j.k("page").v(page.page()).k("pages").v(page.pages()).k("empty").v(page.empty()).k("cards").a();
        for (ToolsPage.Card c : page.cards()) {
            j.o().k("name").v(c.name).k("kit").v(c.kit != null).k("now").v(page.now(c)).k("deletable").v(page.deletable(c))
             .k("said").v(c.kit != null ? c.kit.title() + ": " + c.kit.intro : c.name + ": " + BrushVoice.about(c.brush.parts)).k("key").v(c.brush.identity()).k("drawing"); drawing(j, c.brush); j.e();
        }
        String note = page.note();
        j.e().k("note"); if (note == null) j.raw("null"); else j.v(note);
        return j.e().toString();
    }
    /** Where a drawing {@code aw × ah} goes in an art box {@code w × h} ({@link ToolsLayout#art}): left, top, width, height. */
    @JSExport @JSByRef public static double[] art(double aw, double ah, double w, double h) {
        float[] a = ToolsLayout.art((float) aw, (float) ah, (float) w, (float) h);
        return new double[]{a[0], a[1], a[2], a[3]};
    }
    private static void drawing(J j, Brush b) { String d = DrawLayout.drawing(b); if (d == null) j.raw("null"); else j.v(d); }
    /** The Tools page's taps, each as ToolsActivity's, then the page as it stands ({@link #toolsPage}). */
    @JSExport public static String toolsSelect(int slot) { page.select(slot); return toolsPage(); }
    @JSExport public static String toolsChip(int chip) { page.search(""); page.filter(ToolsPage.Chip.values()[chip]); return toolsPage(); }
    @JSExport public static String toolsTurn(int delta) { page.turn(delta); return toolsPage(); }
    @JSExport public static String toolsSearch(String words) { page.search(words); return toolsPage(); }
    @JSExport public static String toolsCard(int card) { page.put(page.cards().get(card).brush); return toolsPage(); }
    /** Reset to Default (the name row's, for an edited Basic tool): its Basic tool put as a card is ({@link ToolsPage#edited}). */
    @JSExport public static String toolsReset() { ToolPresets.Preset p = page.edited(); if (p != null) page.put(p.brush()); return toolsPage(); }
    @JSExport public static String toolsDelete(int card) { page.delete(page.cards().get(card)); return toolsPage(); }
    /** Card {@code card}'s kit, its sheet as JSON: its title, intro, steps and where it goes ({@link ToolsLayout#kitWhere}). */
    @JSExport public static String toolsKit(int card) {
        ToolKits.Kit k = page.cards().get(card).kit;
        J j = new J().o().k("title").v(k.title()).k("intro").v(k.intro).k("where").v(ToolsLayout.kitWhere(k.size(), page.raises(k))).k("said").v("Load the " + k.name + " kit").k("steps").a();
        for (int i = 0; i < k.steps(); i++) j.o().k("said").v(k.said(i)).k("tool").v(k.tool(i)).e();
        return j.e().e().toString();
    }
    @JSExport public static String toolsLoadKit(int card) { page.loadKit(page.cards().get(card).kit); return toolsPage(); }
    /** Slot {@code i}'s picture (its tool at its size), or card {@code i}'s (at the size that fits), ARGB ({@link LabLibrary#traySampler}). */
    @JSExport @JSByRef public static int[] toolsTray(boolean slot, int i, int w, int h) { return slot ? tray(page.current(i), page.look(i), w, h) : tray(page.cards().get(i).brush, null, w, h); }
    /**
     * Back to the drawing screen: each slot the page changed takes its tool ({@link ToolSlots#take}: an Undo step each, a
     * kit's as one), the guest's own now. As JSON: the slot selected, the eraser's kind a kit set (−1: unchanged).
     * {@code lab}: Brush Lab opens next on it, and its Back comes here again with the page's start ({@link #start}).
     */
    @JSExport public static String toolsLeave(boolean lab) {
        own(slots().take(page.results(), page.together()));
        if (lab) start = page.start.write();
        String said = new J().o().k("slot").v(page.slot()).k("eraser").v(page.eraser() < 0 ? -1 : EraseKind.TARGETS[page.eraser()]).e().toString();
        page = null;
        return said;
    }

    // Brush Lab (BrushLabActivity): BrushLab's state, LabLayout's sizes, on the slot it opened on.
    private static BrushLab lab;
    private static LabLayout labLayout;
    /** The part open in the column, and the part whose choices (the swap panel) are open beside it, or null. */
    private static BrushPart opened, swapping;
    /** Whether the lab was opened from the Tools page (its Back goes there, "‹ Tools"). */
    private static boolean STARTED;
    /** Brush Lab on slot {@code slot} for a {@code w × h} dp screen ({@link LabLayout#scrolled}), opened from the Tools page when the page's start waits ({@link #start}); as {@link #lab}. */
    @JSExport public static String labOpen(int slot, int w, int h) {
        STARTED = start != null;
        lab = new BrushLab(slots(), slot, new java.util.Random(), start);
        start = null;
        labLayout = LabLayout.scrolled(w, h);
        opened = swapping = null;
        return lab();
    }
    private static ToolSwatches.Look labLook() { return lab.slots.swatches.look(ToolSlots.key(lab.selected()), lab.brush()); }
    /** Brush Lab as it stands, as JSON: LabLayout's sizes, the words, About This Brush, the modules, the open card and the swap panel ({@link LabSettings}, {@link PartCard}, {@link SwapStrip}, {@link HandGrid}). */
    @JSExport public static String lab() {
        LabLayout l = labLayout;
        ModularBrush b = lab.parts();
        J j = new J().o().k("L").o();
        j.k("width").v(l.width).k("height").v(l.height).k("compact").v(l.compact).k("described").v(l.described).k("rows").v(l.rows).k("top").v(l.top).k("side").v(l.side)
         .k("bottom").v(l.bottom).k("split").v(l.split).k("gap").v(l.gap).k("headRow").v(l.headRow).k("aboutHead").v(l.aboutHead).k("column").v(l.column).k("right").v(l.right)
         .k("pad").v(l.pad).k("about").v(l.about).k("loopWidth").v(l.loopWidth).k("wordsWidth").v(l.wordsWidth).k("words").v(l.words).k("nameHeight").v(l.nameHeight())
         .k("mark").v(l.mark).k("randomize").v(l.randomize).k("moduleGap").v(l.moduleGap).k("modulePad").v(l.modulePad).k("moduleWidth").v(l.moduleWidth).k("module").v(l.module)
         .k("glyph").v(l.glyph).k("glyphGap").v(l.glyphGap).k("slim").v(l.slim).k("slimChoice").v(l.slimChoice).k("slimPad").v(l.slimPad).k("open").v(l.open)
         .k("label").v(l.label).k("value").v(l.value).k("sliderHeight").v(l.sliderHeight).k("rowGap").v(l.rowGap).k("handName").v(l.handName).k("wordsSp").v(l.wordsSp)
         .k("testWidth").v(l.testWidth).k("BAR").v(LabLayout.BAR).k("LOCK").v(LabLayout.LOCK).k("OPEN_HEAD").v(LabLayout.OPEN_HEAD).k("SEGMENT").v(LabLayout.SEGMENT)
         .k("SEGMENT_PAD").v(LabLayout.SEGMENT_PAD).k("DENSE").v(LabLayout.DENSE_SP).k("NAME_SP").v(LabLayout.NAME_SP).k("CHOICE_SP").v(LabLayout.CHOICE_SP)
         .k("SMALL_SP").v(LabLayout.SMALL_SP).k("MODULE_RULE").v(LabLayout.MODULE_RULE).k("HEAD_RULE").v(LabLayout.HEAD_RULE).k("handHead").v(LabLayout.handHead())
         .k("stackedRow").v(LabLayout.stackedRow()).k("swap").v(l.swap)
         .k("picker").v(l.picker).k("pickerThick").v(l.pickerThick).k("readout").v(l.readout).k("boxSide").v(DrawLayout.boxSide(l.pickerThick, l.picker))
         .k("GREY_WIDE").v(LabLayout.GREY_WIDE).k("GREY_GAP").v(LabLayout.GREY_GAP).k("GREY_PAD").v(LabLayout.GREY_PAD).k("GREY_OUT").v(LabLayout.GREY_OUT).k("ROW_RULE").v(LabLayout.ROW_RULE)
         .k("GREYS_FEW").v(ToolSwatches.COUNT);
        j.k("rowHead").a(); for (int r = 0; r < l.rows; r++) j.v(l.rowHead(r)); j.e();
        j.k("rowChoice").a(); for (int r = 0; r < l.rows; r++) j.v(l.rowChoice(r)); j.e();
        j.k("rowMacro").a(); for (int r = 0; r < l.rows; r++) j.v(l.rowMacro(r)); j.e();
        j.e();
        BrushLibrary.Entry entry = lab.savedEntry();
        j.k("w").o().k("title").v(LabLayout.TITLE).k("back").v(STARTED ? ToolsLayout.TITLE : "Back").k("startFrom").v(LabLayout.START_FROM)
         .k("try").v(ToolsLayout.TRY + lab.brush().shownName()).e();
        j.k("name").v(lab.brush().shownName()).k("about").v(ToolsLayout.fitWords(BrushVoice.about(b), l.wordsWidth, l.wordsLines)).k("said").v(BrushVoice.about(b))
         .k("edited").v(lab.edited()).k("suggestion"); String s = lab.suggestion(); if (s == null) j.raw("null"); else j.v(s);
        j.k("canUndo").v(lab.canUndo()).k("saved").v(lab.saved()).k("entry"); if (entry == null) j.raw("null"); else j.v(entry.brush.name());
        j.k("new").v(LabLayout.NEW + " ▾").k("asNew").v(LabLayout.AS_NEW).k("save").v(lab.saveWords());
        j.k("key").v(b.key());
        // The slot's size and grey (the picker box lying across, and its readout).
        ToolSwatches.Look look = labLook();
        j.k("slot").v(lab.selected()).k("px").v(look.px).k("dark").v(look.darkness);
        j.k("cut"); if (b.stamps()) j.v(StampNib.CUT); else j.raw("null");
        j.k("styles").a(); for (ModularStyle st : ModularStyle.shown()) j.o().k("label").v(st.label).k("on").v(st == b.style).e(); j.e();
        j.k("modules").a();
        for (int r = 0; r < l.rows; r++) for (BrushPart part : LabLayout.row(r)) {
            ModularBrush.Part p = b.part(part);
            PartChoice c = p.choice;
            j.o().k("part").v(part.ordinal()).k("key").v(part.name()).k("row").v(r).k("label").v(opened != null ? l.slimName(part) : l.name(part)).k("inside").v(ModularWords.inside(part)).k("words").v(l.moduleWords(b, part, opened != null))
             .k("locked").v(lab.locked(part)).k("picked").v(part == opened).k("lines").v(l.choiceLines(part)).k("ends").v(l.endLines(part)).k("macro");
            if (c.macro == null) j.raw("null");
            else { double[] range = c.range(c.macro); j.o().k("dial").v(c.macro.ordinal()).k("value").v(p.get(c.macro)).k("lo").v(range[0]).k("hi").v(range[1]).k("locked").v(lab.locked(c.macro)).k("a").v(c.words[0]).k("b").v(c.words[1]).e(); }
            j.k("means"); if (c.macro == null) j.v(ModularWords.noMacro(c)); else j.raw("null");
            j.k("sampled").v(c.macro != null || c == PartChoice.SHAPE).e();
        }
        j.e().k("open");
        if (opened == null) j.raw("null"); else openCard(j, opened);
        j.k("swaps");
        if (swapping == null) j.raw("null");
        else {
            int n = lab.swaps(swapping).size(), per = LabLayout.swapPerPage(swapping, n);
            int[] cell = l.swapCell(swapping, per), panel = l.swapPanel(swapping, per);
            j.o().k("part").v(swapping.ordinal()).k("key").v(swapping.name()).k("label").v(swapping.label).k("perPage").v(per).k("columns").v(l.swapColumns(swapping, per))
             .k("cellWidth").v(cell[0]).k("cellHeight").v(cell[1]).k("panelWidth").v(panel[1]).k("panelHeight").v(panel[2]).k("tiles").a();
            for (BrushLab.Swap sw : lab.swaps(swapping)) { j.o().k("now").v(sw.now).k("key").v(sw.brush.parts.key()).k("caption").a(); for (String c : sw.caption()) j.v(c); j.e().e(); }
            j.e().e();
        }
        String note = lab.note();
        j.k("note"); if (note == null) j.raw("null"); else j.v(note);
        return j.e().toString();
    }
    /** The open part's card ({@link LabSettings#showOpen}): its head, its dials' rows, or Control's grid and the rows under it. */
    private static void openCard(J j, BrushPart part) {
        LabLayout l = labLayout;
        ModularBrush b = lab.parts();
        ModularBrush.Part p = b.part(part);
        j.o().k("part").v(part.ordinal()).k("key").v(part.name()).k("label").v(part.label).k("locked").v(lab.locked(part));
        java.util.List<PartDial> rows = part == BrushPart.HAND ? l.handRows(b) : l.openDials(lab, part);
        j.k("means"); if (part != BrushPart.HAND && rows.isEmpty()) j.v(ModularWords.noMacro(p.choice)); else j.raw("null");
        int stacked = 0; for (PartDial d : rows) if (d != null && l.rowKind(d, p.choice.optionsOf(d)) == LabLayout.RowKind.STACKED) stacked++;
        j.k("rowHeight").v(part == BrushPart.HAND ? l.handRow(rows.size()) : l.dialRow(rows.size(), stacked));
        j.k("rows").a();
        for (PartDial d : rows) {
            if (d == null) {
                // Grey Shift's set (LabSettings.greySet, GreySet): no label, each grey across the row, in or out, its words.
                j.o().k("kind").v("GREYS").k("name").v(PartDial.SHIFT_GREYS.label).k("enabled").v(!lab.locked(PartDial.SHIFT)).k("greys").a();
                for (BrushLab.Grey g : lab.greys()) j.o().k("tone").v(g.tone).k("in").v(g.in).k("said").v(g.said).e();
                j.e().e();
                continue;
            }
            ModularBrush.Part q = b.part(d.part);
            String[] options = q.choice.optionsOf(d);
            LabLayout.RowKind kind = l.rowKind(d, options);
            double[] range = q.choice.range(d);
            double v = q.get(d);
            j.o().k("dial").v(d.ordinal()).k("kind").v(kind.name()).k("name").v(q.choice.labelOf(d))
             .k("label").v(LabLayout.dialLabel(kind == LabLayout.RowKind.MENU ? LabLayout.rowLabel(q.choice, d) : q.choice.labelOf(d)))
             .k("value").v(v).k("lo").v(range[0]).k("hi").v(range[1]).k("active").v(lab.active(d)).k("locked").v(lab.locked(d))
             .k("lock").v(LabLayout.rolls(q.choice, d) && !lab.locked(d.part)).k("rolls").v(LabLayout.rolls(q.choice, d)).k("shown");
            if (kind == LabLayout.RowKind.SLIDER) j.v(d.shown(v, q)); else j.raw("null");
            j.k("options").a(); if (d.choice() && options != null) for (String o : options) j.v(o); j.e().e();
        }
        j.e();
        if (part == BrushPart.HAND) {
            j.k("hand").o().k("cell").v(l.handCell(rows.size())).k("heads").a().v("Size").v(ModularWords.darkHead(b)).v("Nib").e().k("rows").a();
            for (int r = 0; r < BrushLab.HAND_ROWS.length; r++) {
                j.o().k("name").v(BrushLab.HAND_ROWS[r]).k("locked").v(lab.rowLocked(r)).k("lock").v(!lab.locked(BrushPart.HAND)).k("cells").a();
                for (PartDial c : BrushLab.handRow(r)) j.o().k("dial").v(c.ordinal()).k("value").v(p.get(c)).k("text").v(BrushLab.cellText(p, c)).k("locked").v(lab.locked(c)).k("said").v(c.label + ": " + BrushLab.level(p, c)).e();
                j.e().e();
            }
            j.e().e();
        }
        j.e();
    }
    /** Brush Lab's taps, each as BrushLabActivity's, LabSettings', PartCard's, SwapStrip's and HandGrid's, then the lab as it stands ({@link #lab}). */
    @JSExport public static String labPart(int part) { opened = swapping = part < 0 ? null : BrushPart.values()[part]; return lab(); }
    @JSExport public static String labCloseSwap() { swapping = null; return lab(); }
    @JSExport public static String labSwap(int tile) { BrushLab.Swap sw = lab.swaps(swapping).get(tile); lab.choose(swapping, sw.choice); swapping = null; return lab(); }
    /** A slider: pressed ({@code phase} 0, {@link BrushLab#startSlide}), moved (1), let go (2): one Undo step. */
    @JSExport public static String labSlide(int dial, double value, int phase) {
        PartDial d = PartDial.values()[dial];
        if (phase == 0) lab.startSlide();
        lab.slide(d, value);
        if (phase == 2) lab.endSlide(d);
        return lab();
    }
    /** A slider's double tap ({@code BandSlider.DOUBLE_TAP_MS}): its dial back as the style has it. */
    @JSExport public static String labResetDial(int dial) { lab.resetDial(PartDial.values()[dial]); return lab(); }
    @JSExport public static String labRoll(int dial) { lab.roll(PartDial.values()[dial]); return lab(); }
    @JSExport public static String labLock(int dial) { lab.toggleLock(PartDial.values()[dial]); return lab(); }
    @JSExport public static String labRollPart(int part) { lab.rollPart(BrushPart.values()[part]); return lab(); }
    @JSExport public static String labLockPart(int part) { lab.toggleLock(BrushPart.values()[part]); return lab(); }
    @JSExport public static String labOption(int dial, int option) { lab.choose(PartDial.values()[dial], option); return lab(); }
    @JSExport public static String labFast() { lab.flipFast(); return lab(); }
    @JSExport public static String labCell(int dial) { lab.cycleCell(PartDial.values()[dial]); return lab(); }
    @JSExport public static String labRowLock(int row) { lab.toggleRowLock(row); return lab(); }
    @JSExport public static String labRowRoll(int row) { lab.rollRow(row); return lab(); }
    @JSExport public static String labRandomize(boolean anything) { if (anything) lab.anythingGoes(); else lab.randomize(); return lab(); }
    @JSExport public static String labNew(int style) { lab.newBrush(ModularStyle.shown().get(style)); return lab(); }
    @JSExport public static String labUndo() { lab.undo(); return lab(); }
    @JSExport public static String labSave(boolean asNew) { lab.save(asNew); return lab(); }
    /** Part {@code part}'s module sample ({@link WildSample#part}), or swap tile {@code tile}'s (−1: the module's), ARGB. */
    @JSExport @JSByRef public static int[] labSample(int part, int tile, int w, int h) {
        BrushPart role = BrushPart.values()[part];
        ModularBrush b = tile < 0 ? lab.parts().copy() : lab.swaps(role).get(tile).brush.parts;
        return WildSample.part(b, role, w, h);
    }
    /** About This Brush's loop ({@link LabLibrary#sampler}: the brush's loop, at black a wash at the old black), ARGB. */
    @JSExport @JSByRef public static int[] labLoop(int w, int h) {
        ModularBrush b = lab.parts().copy();
        return WildSample.modular(b, ModularSizes.sampleDarkness(b), WildSample.Shape.LOOP, w, h, true);
    }
    /** The window resized: the open Tools page and lab laid out anew, their state kept (ToolsActivity.build, BrushLabActivity.build). */
    @JSExport public static void screens(int w, int h) {
        if (page != null) { pageLayout = new ToolsLayout(w, h); page.layout(pageLayout.perPage(), pageLayout.cardWidth); }
        if (lab != null) labLayout = LabLayout.scrolled(w, h);
    }
    /**
     * Back to the drawing screen: each slot the lab changed takes its brush, the guest's own now ({@link ToolSlots#take}).
     * As JSON: the slot, and the Tools page's start when the lab was opened from it (its Back goes there again).
     */
    @JSExport public static String labLeave() {
        own(slots().take(lab.results(), false));
        boolean tools = STARTED;
        if (tools) start = lab.start.write();
        String said = new J().o().k("slot").v(lab.selected()).k("tools").v(tools).e().toString();
        lab = null; STARTED = false;
        return said;
    }
    /** The scratch pad's stroke on page {@code p} (not live): the Tools page's tool ({@code lab} false) or the lab's, at the slot's size and grey, as the test page starts it. */
    @JSExport public static void scratch(int p, boolean inLab) {
        Page g = at(p);
        Brush b = inLab ? lab.brush() : page.current();
        g.stroke = StrokeStart.stroke(g.d, b, inLab ? labLook() : page.look(), g.session, 0);
    }

    // ---- The rest of the drawing screen (the browser app's M4): the menus, Play, Shapes, the smudge tool, the Lasso, ----
    // ---- Text, the Dropper, Hold to Snap. Each decision is the Manta's own (DrawLayout's rows, Mirrors, TextBoxes,      ----
    // ---- LassoPieces, ShapeStroke, ToolSlots, SwatchPicker, HoldSnap); live.html's m4.js draws them.                   ----

    /** Rows ({@link DrawLayout.Row}) as JSON: each one's key, words, icon and mark; a line between groups as a null key. */
    private static J rows(J j, List<DrawLayout.Row> rows) {
        j.a();
        for (DrawLayout.Row r : rows) {
            j.o().k("key"); if (r.key == null) j.raw("null"); else j.v(r.key);
            j.k("words"); if (r.words == null) j.raw("null"); else j.v(r.words);
            j.k("icon"); if (r.icon == null) j.raw("null"); else j.v(r.icon);
            j.k("on").v(r.marked).e();
        }
        return j.e();
    }
    private static String rowsJson(List<DrawLayout.Row> rows) { return rows(new J(), rows).toString(); }

    /**
     * What one finger does ({@link FingerTool.Choice}), kept in the guest's store as the Manta keeps it; until the guest
     * picks one, the tool in use, {@code sel} (M1: a phone has no pen, so its finger draws).
     */
    private static FingerTool.Choice finger(int sel) { String k = KEPT.get(FingerTool.KEY); return k == null ? FingerTool.Choice.of(sel) : FingerTool.Choice.saved(k); }
    private static String brushName(int slot) { return slots().brush(slot).shownName(); }
    /** The bar's tool one finger draws with ({@link FingerTool.Choice#tool}), −1 for Nothing. */
    @JSExport public static int fingerTool(int sel) { return finger(sel).tool(); }
    /** The guest's drawing hand: the column on the right ({@code PaintPreferences.toolboxRight}, the menu's Left-Handed). */
    @JSExport public static boolean columnRight() { return "1".equals(KEPT.get(TOOLBOX_RIGHT)); }
    private static final String TOOLBOX_RIGHT = "toolbox_right";
    /** The menu's rows ({@link DrawLayout#fileRows}): a guest's, with the Manta's notebook rows while the Manta shares them ({@code notebook}, books.js). As JSON. */
    @JSExport public static String fileMenu(int sel, boolean notebook) { return rowsJson(DrawLayout.fileRows(notebook, finger(sel).row(WebEngine::brushName), columnRight(), null, false, false, false, false)); }
    /** The menu's row {@code key} tapped: the drawing hand turned over ("hand"); the Finger row opens its menu (the page's). */
    @JSExport public static void fileAct(String key) { if ("hand".equals(key)) KEPT.put(TOOLBOX_RIGHT, columnRight() ? "0" : "1"); }
    /** Finger Action's menu ({@code PaintActivity.fingerMenu}): each choice's words and the bar's tool it draws with (−1 Nothing), the one chosen marked. */
    @JSExport public static String fingerMenu(int sel) {
        J j = new J().a();
        for (FingerTool.Choice c : FingerTool.Choice.values())
            j.o().k("key").v(c.ordinal()).k("words").v(c.label(WebEngine::brushName)).k("tool").v(c.tool()).k("on").v(c == finger(sel)).e();
        return j.e().toString();
    }
    @JSExport public static void setFinger(int choice) { KEPT.put(FingerTool.KEY, FingerTool.Choice.values()[choice].key); }

    /** Whether the guest set the page's mirrors: a host's tools no longer change them. */
    private static boolean ownMirrors;
    /** Play's rows ({@link DrawLayout#playRows}) for a guest: Text or Shapes taken up ({@code text}, {@code shapes}), the page's mirrors on or one being placed ({@code placing}). */
    @JSExport public static String playMenu(int p, boolean text, boolean shapes, boolean placing) {
        return rowsJson(DrawLayout.playRows(text, false, shapes, at(p).d.mirrors().acts() || placing, false, true));
    }

    /** The mirrors' list ({@link DrawLayout.MirrorRow#rows}, a guest's) and how many under it ({@link Mirrors#caption}). */
    @JSExport public static String mirrorMenu(int p, boolean placing) {
        Mirrors m = at(p).d.mirrors();
        J j = new J().o().k("rows"); rows(j, DrawLayout.MirrorRow.rows(m, placing, true));
        j.k("caption"); if (m.caption() == null) j.raw("null"); else j.v(m.caption());
        return j.e().toString();
    }
    /** A mirrors' row tapped but Place Mirror ({@link DrawLayout.MirrorRow#change}); whether a mirror being placed stops. */
    @JSExport public static boolean mirrorAct(int p, String key) {
        Page g = at(p);
        g.session.settleNow(g.d);
        DrawLayout.MirrorRow row = DrawLayout.MirrorRow.valueOf(key);
        row.change(g.d);
        ownMirrors = true;
        return row.stopsPlacing(g.d.mirrors());
    }
    /** Place Mirror's stroke ({@code xs}, {@code ys}, page px): the mirror it places ({@link Mirrors#fit}), or {@link MirrorsMenu#TOO_SHORT}'s words. */
    @JSExport public static String mirrorPlace(int p, @JSByRef float[] xs, @JSByRef float[] ys) {
        ToneDocument d = at(p).d;
        Mirrors m = d.mirrors();
        Mirrors.Line line = m.fit(xs.clone(), ys.clone(), xs.length, d.width, d.height);
        if (line == null) return Mirrors.TOO_SHORT;
        d.setMirrors(DrawLayout.MirrorRow.kept(m.add(line), d));
        ownMirrors = true;
        return "";
    }
    /** The page's guides as JSON ({@link Mirrors#guidesShown}): each mirror's point and direction, the dash, half the line's width and its grey ({@link Mirrors#DASH}, {@link Mirrors#GUIDE_TONE}); the placing stroke's grey. */
    @JSExport public static String guides(int p) {
        Mirrors m = at(p).d.mirrors();
        J j = new J().o().k("shown").v(m.guidesShown()).k("dash").v(Mirrors.DASH).k("half").v(Mirrors.HALF_WIDTH).k("tone").v(Mirrors.GUIDE_TONE)
                .k("placing").v(Mirrors.PLACING_TONE).k("acts").v(m.acts()).k("lines").a();
        for (Mirrors.Line l : m.lines) j.a().v(l.x).v(l.y).v(l.dx).v(l.dy).e();
        return j.e().e().toString();
    }

    /** Shapes ({@link DrawLayout#SHAPES}): what the pen draws while they are taken up; one for the guest. */
    private static ToolSettings shapes = ToolSettings.defaults(ToolSettings.Tool.SHAPES);
    private static ShapeStroke shape;
    /** Shapes' rows ({@link DrawLayout#shapeRows}), the shape marked while Shapes is taken up ({@code on}). */
    @JSExport public static String shapeMenu(boolean on) { return rowsJson(DrawLayout.shapeRows(shapes, on)); }
    /** Shapes' row {@code key} tapped ({@link DrawLayout#shapePicked}): what it says beside Shapes ({@link DrawLayout#shapeWords}). */
    @JSExport public static String shapePick(String key) { shapes = DrawLayout.shapePicked(shapes, key); return DrawLayout.shapeWords(shapes); }
    /**
     * The pen down at {@code x, y} with Shapes on slot {@code tool} at {@code px} and {@code dark}, as the drawing screen
     * starts one ({@code DrawingPad.inkShape}, {@code InkWash.shape}): what comes before a stroke, the slot's layer, its
     * outline the slot's size ({@link DrawLayout#shapeOutline}), its grey the slot's. A drag draws it ({@link ShapeStroke}).
     */
    @JSExport public static void shapeDown(int p, int tool, double px, double dark, double x, double y) {
        Page g = at(p);
        Brush b = brush(tool);
        StrokeStart.shape(g.d, b.parts, g.session);
        shape = new ShapeStroke(g.d, DrawLayout.shapeOutline(shapes, px), ToolSwatches.tone(dark), (float) x, (float) y);
        shapeCode = ModularCode.encode(b.parts);
    }
    /** Record and Replay: the brush code of the slot whose shape is being drawn ({@link StrokeLog.Entry#shape}). */
    private static String shapeCode;
    @JSExport public static void shapeMove(int p, double x, double y) { if (shape != null) { shape.preview((float) x, (float) y); shape.drainDirty(); } }
    /** The lift: the shape laid, one Undo step, and logged (Record and Replay); whether it changed the page. */
    @JSExport public static boolean shapeEnd(int p) {
        ShapeStroke s = shape; shape = null;
        if (s == null) return false;
        StrokeLog.Shape logged = s.logged();
        boolean changed = s.finish();
        // Kept sharp on the plane it was laid on (SharpView draws it finer when zoomed).
        if (logged != null) at(p).d.logMade(StrokeLog.Entry.shape(0, shapeCode, logged).sharp(at(p).d.selectedAddress(), 1, 1), changed);
        return changed;
    }

    /** The smudge tool's chooser ({@link ToolSlots#smudgeChoices}) on a {@code w × h} dp screen as JSON: its cards' size and columns ({@link DrawLayout#chooserGrid}), each card's name and line, the tool's own marked; Clean Brush while it holds paint. */
    @JSExport public static String smudgeMenu(int w, int h) {
        Brush now = slots().smudge();
        DrawLayout d = new DrawLayout(w, h);
        int[] grid = d.chooserGrid(slots().smudgeChoices().size());
        J j = new J().o().k("card").v(d.chooserCard()).k("width").v(ToolsLayout.CARD_LEAST).k("columns").v(grid[0]).k("clean").v(held(ToolSlots.SMUDGE)).k("cleanWords").v(DrawLayout.CLEAN).k("cards").a();
        for (Brush b : slots().smudgeChoices()) {
            ToolsPage.Card c = ToolsPage.card(b, ToolsLayout.CARD_LEAST - 2 * LabText.CAPTION_INSET);
            // Its kept name, no "effect": every choice of the smudge tool is one (WildSample.shownName).
            j.o().k("name").v(b.name()).k("line").v(c.line).k("on").v(b.sameAs(now)).k("said").v(b.name() + ": " + BrushVoice.about(b.parts)).e();
        }
        return j.e().e().toString();
    }
    /** Card {@code card}'s loop ({@code LabLibrary.sampler}'s: the brush's loop at its sample grey), ARGB. */
    @JSExport @JSByRef public static int[] smudgeLoop(int card, int w, int h) {
        ModularBrush b = slots().smudgeChoices().get(card).parts.copy();
        return WildSample.modular(b, ModularSizes.sampleDarkness(b), WildSample.Shape.LOOP, w, h, true);
    }
    /** Card {@code card} put in the smudge tool ({@link ToolSlots#useSmudge}); its name, or "" when it held it already. */
    @JSExport public static String smudgePick(int card) {
        Brush b = slots().smudgeChoices().get(card);
        return slots().useSmudge(b) ? slots().smudge().name() : "";
    }
    /** Whether tool {@code tool}'s brush holds paint (Keeps Paint, {@link ToolSlots#dirty}): its long press offers Clean Brush. */
    @JSExport public static boolean held(int tool) { return !PAGES.isEmpty() && held() != null && ToolSlots.dirty(held(), brush(tool), tool, finger(tool).tool()); }
    /** Clean Brush ({@link ToolSlots#clean}); whether it held paint. */
    @JSExport public static boolean clean(int tool) { return held() != null && ToolSlots.clean(held(), tool); }
    /** The paint the guest's brushes hold: the drawing page's (the last page made that isn't a scratch pad's is the one drawn on). */
    private static HeldPaint held() { Page g = drawn; return g == null ? null : g.session.held; }
    /** The page the bar's tools last drew on ({@link #pen}). */
    private static Page drawn;
    /** The Clean Brush and Tools rows of a dirty slot's long press ({@code InkWashBar.cleanMenu}). */
    @JSExport public static String cleanMenu() { return new J().a().v(DrawLayout.CLEAN).v(DrawLayout.TOOLS).e().toString(); }

    /** The tone page {@code p} shows at page px {@code x, y} (every layer and its planes), −1 off the page: the Dropper's ({@link DrawLayout#DROPPER}). */
    @JSExport public static int toneAt(int p, double x, double y) {
        ToneDocument d = at(p).d;
        int px = (int) Math.floor(x), py = (int) Math.floor(y);
        return px < 0 || py < 0 || px >= d.width || py >= d.height ? -1 : d.compositeTone(px, py);
    }
    /** The grey tool {@code tool} lays {@code tone} at ({@link ToolSwatches#drop}'s), kept as its own; the eraser and the smudge tool lay none: as the page shows it. */
    @JSExport public static double drop(int tool, int tone) {
        Brush b = DrawLayout.greys(tool) ? brush(tool) : null;
        double darkness = slots().swatches.drop(ToolSlots.toolKey(tool), b, tone);
        if (tool < ToolSlots.COUNT) own(tool);
        return darkness;
    }
    /** What the Dropper's pick says beside the strip ({@code InkWashBar.dropped}). */
    @JSExport public static String dropWords(int tool, double darkness, int tone) {
        Brush b = DrawLayout.greys(tool) ? brush(tool) : null;
        return ToolSwatches.greyWords(darkness, b == null ? tone : ModularSizes.swatch(b.parts, darkness));
    }
    /** The Dropper's ring and hole, dp ({@link DrawLayout#DROPPER_RING}). */
    @JSExport public static String dropper() { return new J().o().k("ring").v(DrawLayout.DROPPER_RING).k("hole").v(DrawLayout.DROPPER_HOLE).k("words").v(DrawLayout.DROPPER).e().toString(); }

    /** The Lasso's pieces ({@link LassoPieces}): one guest, one page. */
    private static final LassoPieces LASSO = new LassoPieces();
    /** The Lasso's words ({@link DrawLayout#LASSO_BAR} and the rest). */
    @JSExport public static String lassoWords() {
        J j = new J().o().k("lasso").v(DrawLayout.LASSO).k("bar").a();
        for (String w : DrawLayout.LASSO_BAR) j.v(w);
        return j.e().k("paste").v(DrawLayout.LASSO_PASTE).k("nothing").v(DrawLayout.LASSO_NOTHING).k("otherSize").v(DrawLayout.LASSO_OTHER_SIZE)
                .k("stampDone").v(StampNib.DONE).k("stampNothing").v(StampNib.NOTHING).e().toString();
    }
    /** What a tap on the Lasso does ({@link DrawLayout#lassoTap}): 0 take it up, 1 Paste's menu, 2 nothing. */
    @JSExport public static int lassoTap(boolean on) { return DrawLayout.lassoTap(on, LASSO.floats(), LASSO.hasKept()).ordinal(); }
    /** The loop's points ({@code x, y} pairs, page px) lift what the drawn pair holds inside them ({@link LassoPlanes#lift}), floating; whether anything floats. */
    @JSExport public static boolean lassoLift(int p, @JSByRef float[] points) {
        Page g = at(p);
        if (points.length < 6) return false;
        g.session.settleNow(g.d);
        FloatingSelection piece = LassoPlanes.lift(g.d, points.clone(), points.length / 2);
        if (piece == null) return false;
        LASSO.floatPiece(piece);
        return true;
    }
    /**
     * The floating piece as JSON, null with none: its corners (x, y pairs clockwise, page px), its knob and the middle of
     * its edge ({@link FloatingSelection#knob}, {@code upX, upY} the screen's up on the page, {@code distance} page px,
     * {@code visible} the page on screen), the area it shows over ({@link LassoPieces#area}), and whether a piece is kept.
     */
    @JSExport public static String lasso(double upX, double upY, double distance, double l, double t, double r, double b) {
        J j = new J().o().k("kept").v(LASSO.hasKept()).k("piece");
        if (!LASSO.floats()) return j.raw("null").e().toString();
        j.o().k("corners").a(); for (float c : LASSO.floating.corners()) j.v(c); j.e();
        j.k("knob").a(); for (float c : knob(upX, upY, distance, l, t, r, b)) j.v(c); j.e();
        j.k("area").a(); for (int c : LASSO.area()) j.v(c); j.e();
        return j.k("dragging").v(LASSO.handle != FloatingSelection.NONE).e().e().toString();
    }
    private static float[] knob(double upX, double upY, double distance, double l, double t, double r, double b) {
        return LASSO.floating.knob((float) upX, (float) upY, (float) distance, new float[]{(float) l, (float) t, (float) r, (float) b});
    }
    /** Whether the pen at page px {@code x, y} grabs the piece (within {@code reach} page px of a corner or the knob); then it drags it. */
    @JSExport public static boolean lassoGrab(double x, double y, double reach, double upX, double upY, double distance, double l, double t, double r, double b) {
        return LASSO.floats() && LASSO.grab((float) x, (float) y, knob(upX, upY, distance, l, t, r, b), (float) reach);
    }
    @JSExport public static void lassoDrag(double x, double y) { if (LASSO.handle != FloatingSelection.NONE) LASSO.drag((float) x, (float) y); }
    @JSExport public static void lassoRelease() { if (LASSO.handle != FloatingSelection.NONE) LASSO.release(); }
    /** What page {@code p} shows over the rect with the piece floating ({@link LassoPlanes#shown}): a tone a pixel, −1 where the page shows as it is. */
    @JSExport @JSByRef public static int[] lassoShown(int p, int x, int y, int w, int h) {
        int[] out = new int[w * h];
        java.util.Arrays.fill(out, -1);
        FloatingSelection piece = LASSO.floating;
        if (piece != null && piece.planes != null) piece.planes.shown(at(p).d, piece, x, y, w, h, out);
        return out;
    }
    /** The floating piece put down ({@link LassoPieces#place}), Cut, Duplicate, Delete ({@code bar}: 0, 1, 3; Make Stamp's 2 is {@link #lassoStamp}) or Paste (4); whether the page changed. */
    @JSExport public static boolean lassoAct(int p, int act) {
        Page g = at(p);
        g.session.settleNow(g.d);
        switch (act) {
            case 0: return LASSO.cut(g.d);
            case 1: { boolean was = !LASSO.unmoved(); LASSO.duplicate(g.d); return was; }
            case 3: return LASSO.delete(g.d);
            case 4: {
                FloatingSelection copy = LASSO.pasted(g.d);
                if (copy == null) return false;
                boolean changed = LASSO.place(g.d);
                LASSO.floatPiece(copy);
                return changed;
            }
            default: return LASSO.place(g.d);
        }
    }

    /**
     * Make Stamp (the Lasso's bar, {@link ToolSlots#makeStamp}): the floating piece's ink a Stamp's nib, the slot the
     * guest's own, the piece put down where it lies; the slot ({@code selected} the guest's in use, {@code shown} its
     * bar's slots), or −1 for nothing to cut, the piece still up.
     */
    @JSExport public static int lassoStamp(int p, int selected, int shown) {
        Page g = at(p);
        g.session.settleNow(g.d);
        int slot = LASSO.floats() ? slots().makeStamp(LASSO.floating, selected, shown) : -1;
        if (slot >= 0) { own(slot); LASSO.place(g.d); }
        return slot;
    }

    /** Page {@code p}'s text boxes ({@link TextBoxes}) as JSON: each one's rect, size's em, bold, and its lines as drawn ({@link TextBoxes#lines}). */
    @JSExport public static String texts(int p) {
        J j = new J().a();
        for (TextBoxes.Box b : at(p).d.texts().list()) {
            j.o().k("text").v(b.text).k("rect").a(); for (int v : TextBoxes.rect(b)) j.v(v); j.e();
            j.k("bounds").a(); for (int v : TextBoxes.bounds(b)) j.v(v); j.e();
            j.k("em").v(b.em()).k("bold").v(b.bold).k("align").v(b.align).k("lines").a();
            for (TextBoxes.Line l : TextBoxes.lines(b)) j.a().v(l.text).v(l.x).v(l.baseline).e();
            j.e().e();
        }
        return j.e().toString();
    }
    /** The topmost box at page px {@code x, y} ({@link TextBoxes#hit}), −1 for none; whether it is on its right edge ({@link TextBoxes#onEdge}). */
    @JSExport public static int textHit(int p, double x, double y) { return at(p).d.texts().hit((float) x, (float) y); }
    @JSExport public static boolean textEdge(int p, int i, double x, double y) { return TextBoxes.onEdge(at(p).d.texts().get(i), (float) x, (float) y); }
    /** Box {@code i}'s words typed as {@code text} (−1: a new box placed for a tap at {@code x, y}, {@link TextBoxes#placed}), one Undo step; emptied, the box goes. Its index after, −1 when it went. */
    @JSExport public static int textSet(int p, int i, String text, double x, double y) {
        ToneDocument d = at(p).d;
        TextBoxes now = d.texts();
        if (i < 0 && now.size() >= TextBoxes.MOST) return -1;
        TextBoxes.Box b = i >= 0 ? now.get(i) : TextBoxes.placed((float) x, (float) y, d.width, d.height);
        String clean = TextBoxes.clean(text);
        TextBoxes next = clean.isEmpty() ? (i >= 0 ? now.without(i) : now) : i >= 0 ? now.replaced(i, b.withText(clean)) : now.with(b.withText(clean));
        if (next != now) d.setTexts(next);
        return clean.isEmpty() ? -1 : i >= 0 ? i : next.size() - 1;
    }
    /** Where box {@code i} would be, a new one for a tap at {@code x, y} (−1), as JSON as {@link #texts} gives it, for the field it is typed in. */
    @JSExport public static String textBox(int p, int i, double x, double y) {
        ToneDocument d = at(p).d;
        TextBoxes.Box b = i >= 0 ? d.texts().get(i) : TextBoxes.placed((float) x, (float) y, d.width, d.height);
        J j = new J().o().k("text").v(b.text).k("rect").a(); for (int v : TextBoxes.rect(b)) j.v(v); j.e();
        return j.k("em").v(b.em()).k("bold").v(b.bold).k("align").v(b.align).k("line").v(TextBoxes.lineHeight(b.em())).e().toString();
    }
    /** Box {@code i} dragged ({@link TextBoxes#dragged}): its rect while the pen moves it; put down ({@code done}), one Undo step. */
    @JSExport @JSByRef public static int[] textDrag(int p, int i, boolean widen, double downX, double downY, double x, double y, boolean done) {
        ToneDocument d = at(p).d;
        TextBoxes.Box b = TextBoxes.dragged(d.texts().get(i), widen, (float) downX, (float) downY, (float) x, (float) y, d.width, d.height);
        if (done) d.setTexts(d.texts().replaced(i, b));
        return TextBoxes.rect(b);
    }
    /** Box {@code i}'s menu ({@link TextBoxes#menu}). */
    @JSExport public static String textMenu(int p, int i) { return rowsJson(TextBoxes.menu(at(p).d.texts().get(i))); }
    /** Box {@code i}'s menu row {@code key} but Make Ink: restyled ({@link TextBoxes#restyled}) or deleted, one Undo step. */
    @JSExport public static void textAct(int p, int i, String key) {
        ToneDocument d = at(p).d;
        TextBoxes.Box b = d.texts().get(i), next = TextBoxes.restyled(b, key);
        d.setTexts(next == null ? d.texts().without(i) : d.texts().replaced(i, TextBoxes.inside(next, d.width, d.height)));
    }
    /** The letters of the box the page is about to ink, drawn by the page in the face ({@link TextBoxes.Type}), coverage over its {@link TextBoxes#bounds}. */
    private static byte[] letters;
    static { TextBoxes.type = b -> letters; }
    /**
     * Make Ink ({@code DrawingPad.makeInk}): box {@code i}'s letters ({@code coverage}, over its bounds) laid as one stroke of
     * slot {@code tool}'s brush at {@code px} and {@code dark} ({@link FillTool#print}), the box gone with it, one Undo
     * step; whether it laid anything.
     */
    @JSExport public static boolean textInk(int p, int i, int tool, double px, double dark, @JSByRef byte[] coverage) {
        Page g = at(p);
        ToneDocument d = g.d;
        Brush b = brush(tool < ToolSlots.COUNT ? tool : 0);
        g.session.settleNow(d);
        letters = coverage.clone();
        FillRegion.Room room;
        try { room = TextBoxes.ink(d.texts().get(i), d.width, d.height); } finally { letters = null; }
        if (room == null) return false;
        Object mark = d.lastStep();
        d.setTexts(d.texts().without(i));
        ModularStroke laid = FillTool.print(d, b.parts, px, dark, g.session, room);
        laid.present(); laid.finish(); d.linkSince(mark);
        return true;
    }

    /** Hold to Snap ({@link HoldSnap}): the stroke the pen holds still over, drawn again as the clean shape. One a guest. */
    private static HoldSnap snap;
    /** The snapped shape follows the pen: it moved off its rest ({@link HoldSnap#moved}). */
    private static boolean following;
    /** A stroke begins that may snap ({@code DrawingPad.snaps}: a slot's brush that doesn't build while held), at the view's {@code zoom}; whether it may. */
    @JSExport public static boolean snapStart(int p, double zoom) { Page g = at(p); snap = g.stroke != null && !g.stroke.buildsWhileHeld() ? new HoldSnap(zoom) : null; following = false; return snap != null; }
    @JSExport public static void snapSample(double x, double y, double travel, double tiltX, double tiltY, double ms) {
        if (snap != null) snap.sample(x, y, ToolSettings.pressure((float) travel), tiltX, tiltY, (long) ms);
    }
    /** At {@code now} ms: the clean shape's samples (x, y, pressure, tilt x, tilt y, ms, six a sample) once held long enough over one ({@link HoldSnap#due}, {@link HoldSnap#snap}), else none. */
    @JSExport @JSByRef public static double[] snapDue(double now) {
        return rows(snap != null && snap.due((long) now) ? snap.snap() : null);
    }
    /** After a snap, the pen at {@code x, y}: once it has moved off its rest, the shape's samples following it ({@link HoldSnap#to}), as {@link #snapDue}'s; else none. */
    @JSExport @JSByRef public static double[] snapTo(double x, double y) {
        following |= snap != null && snap.snapped != null && snap.moved(x, y);
        return rows(following ? snap.to(x, y) : null);
    }
    private static double[] rows(double[][] clean) {
        if (clean == null) return new double[0];
        double[] out = new double[6 * clean.length];
        for (int k = 0; k < clean.length; k++) System.arraycopy(clean[k], 0, out, 6 * k, 6);
        return out;
    }
    @JSExport public static void snapEnd() { snap = null; }
    /** The rough stroke, just finished, taken back for the clean one ({@link WatercolorSession#takeBack}: the wet paint forgets it), so the page keeps one Undo step. */
    @JSExport public static boolean snapBack(int p) { Page g = at(p); return g.session.takeBack(g.d); }
    /** A sample of a snapped stroke, its pressure as {@link HoldSnap} keeps it (not the pen's travel). */
    @JSExport public static void sampleAt(int p, double x, double y, double pressure, double ms, double tiltX, double tiltY) {
        at(p).stroke.sample((float) x, (float) y, (float) pressure, (float) tiltX, (float) tiltY, (long) ms);
    }
    /** The drawing screen's words and timing for M4 (Zen Mode, the column's buttons). */
    @JSExport public static String m4Words() {
        return new J().o().k("zen").v(DrawLayout.ZEN).k("zenLeave").v(DrawLayout.ZEN_LEAVE)
                .k("menu").v("Menu").k("play").v(DrawLayout.PLAY).k("shapes").v(DrawLayout.SHAPES).k("lasso").v(DrawLayout.LASSO).k("dropper").v(DrawLayout.DROPPER)
                .k("smudge").v(DrawLayout.toolName(ToolSlots.SMUDGE)).k("holdMs").v(HoldSnap.HOLD_MS).k("tickMs").v(HoldSnap.TICK_MS).k("menuRow").v(DrawLayout.MENU_ROW)
                .k("zenIcon").v(DrawLayout.ZEN_ICON).k("iconSide").v(DrawLayout.ICON_SIDE).k("offerMs").v(PageClear.OFFER_MS).k("dupOffset").v(DrawLayout.DUPLICATE_OFFSET).e().toString();
    }
    /** Zen Mode's cell, dp ({@link DrawLayout#zenCell}): left, top, width, height, for a {@code w × h} screen, the column on the right or not. */
    @JSExport @JSByRef public static int[] zenCell(int w, int h, boolean right) {
        DrawLayout d = new DrawLayout(w, h);
        int[] c = DrawLayout.zenCell(w, h, d.side, d.half(), d.zenHeight(), right);
        return new int[]{c[0], c[1], d.half(), d.zenHeight()};
    }

    // ---- Brush Lab's Open sheet and Grey Shift's set; the drawing screen's Show Kinds, sand timer and wet indicator.  ----
    // ---- Each the Manta's own (BrushLab, LabLayout, DrawLayout, EraseKind, SandTimer, WetLook); extras.js draws them. ----

    /**
     * Open's sheet ({@code BrushLabActivity.sheet}): its words and sizes, this brush's code, the library's brushes ({@link
     * BrushLab#library}, newest first), the one Save writes over marked.
     */
    @JSExport public static String labSheet(int page) {
        LabLayout l = labLayout;
        BrushLibrary.Entry entry = lab.savedEntry();
        List<BrushLab.Card> cards = lab.library();
        int per = l.shelfColumns * l.shelfRows;
        page = LabPages.clamp(page, cards.size(), per);
        J j = new J().o().k("w").o().k("paste").v(LabLayout.PASTE_CODE).k("hint").v(LabLayout.CODE_HINT).k("open").v(LabLayout.OPEN).k("openCode").v(LabLayout.OPEN_CODE)
                .k("cancel").v(LabLayout.CANCEL).k("thisBrush").v(LabLayout.THIS_BRUSH).k("copy").v(LabLayout.COPY).k("copied").v(LabLayout.COPIED).k("noCopy").v(LabLayout.NO_COPY)
                .k("library").v(LabLayout.LIBRARY).k("openSaid").v(LabLayout.OPEN_SAID).k("close").v(LabLayout.CLOSE).e();
        j.k("label").v(l.sheetLabel()).k("side").v(LabLayout.SHEET_SIDE).k("top").v(LabLayout.SHEET_TOP).k("row").v(LabLayout.SHEET_ROW).k("gap").v(LabLayout.SHEET_GAP)
         .k("buttonGap").v(LabLayout.SHEET_BUTTON_GAP).k("columns").v(l.shelfColumns).k("rows").v(l.shelfRows).k("card").v(l.cardHeight).k("pageButton").v(l.pageButton)
         .k("bar").v(LabLayout.SECTION_BAR).k("sheetTop").v(l.top + l.aboutHead);
        j.k("code").v(lab.code()).k("page").v(page).k("pages").v(LabPages.pages(cards.size(), per)).k("cards").a();
        for (int i = page * per; i < Math.min(cards.size(), (page + 1) * per); i++) {
            BrushLab.Card c = cards.get(i);
            j.o().k("card").v(i).k("name").v(c.brush.shownName()).k("key").v(c.brush.identity()).k("now").v(entry != null && c.entry.id == entry.id).e();
        }
        return j.e().e().toString();
    }
    /** The library's brush {@code card} put in the slot ({@link BrushLab#open}). */
    @JSExport public static String labOpenCard(int card) { lab.open(lab.library().get(card).entry); return lab(); }
    /** A pasted code's brush put in the slot ({@link BrushLab#load}); false, with {@link #labStatus} saying why, when it doesn't read. */
    @JSExport public static boolean labLoad(String code) { return lab.load(code); }
    @JSExport public static String labStatus() { return lab.status == null ? "" : lab.status; }
    /** Library card {@code card}'s tray stroke ({@code LabLibrary.traySampler}, no look: at the size that fits), ARGB. */
    @JSExport @JSByRef public static int[] labCardTray(int card, int w, int h) { return tray(lab.library().get(card).brush, null, w, h); }
    /** Grey Shift's set: grey {@code k} taken in or left out ({@link BrushLab#toggleGrey}). */
    @JSExport public static String labGrey(int k) { lab.toggleGrey(k); return lab(); }
    /** A grey picked in Brush Lab's picker box: the scratch pad's Wet ink brush dipped in it, as {@code BrushLabActivity.picker}. */
    @JSExport public static void dip(int p) { at(p).session.held.dip(HeldPaint.ANY); }

    /** Show Kinds' steps on page {@code p} ({@link ToneDocument#kindsShown}; 0 all Full). */
    @JSExport public static int kinds(int p) { return at(p).d.kindsShown(); }
    /** The page as the screen shows it with Show Kinds' steps over the rect ({@link ToneDocument#compositeTones}), a byte a pixel; {@link #tones} stays the page's own. */
    @JSExport @JSByRef public static byte[] shown(int p, int x, int y, int w, int h) {
        ToneDocument d = at(p).d;
        int[] t = new int[w * h];
        d.compositeTones(x, y, w, h, t, 0, w, d.kindsShown());
        byte[] out = new byte[w * h];
        for (int i = 0; i < out.length; i++) out[i] = (byte) t[i];
        return out;
    }
    /** Show Kinds' popup ({@code InkWashBar.kindsPopup}): each column's name, step, eye and words; no Template's here (the browser's pages show no guides' step yet). */
    @JSExport public static String kindsMenu(int p) {
        int shown = at(p).d.kindsShown(), guides = -1;
        J j = new J().o().k("row").v(DrawLayout.LIGHT_ROW).k("pad").v(DrawLayout.LIGHT_PAD).k("eye").v(DrawLayout.KINDS_EYE).k("words").v(EraseKind.SHOW_KINDS).k("columns").a();
        for (int c = DrawLayout.kindsFirst(guides); c < EraseKind.LABELS.length; c++) {
            int step = DrawLayout.kindsStep(c, shown, guides);
            j.o().k("column").v(c).k("name").v(DrawLayout.kindsName(c)).k("eye").v(DrawLayout.KINDS_EYES[step]).k("said").v(DrawLayout.kindsSaid(c, step)).e();
        }
        return j.e().e().toString();
    }
    /** A tap on column {@code column}'s eye ({@code PaintActivity.showKind}): its kind's next step, a change to the page; its note ("Pen half"). */
    @JSExport public static String kindsTap(int p, int column) {
        ToneDocument d = at(p).d;
        int step = (EraseKind.step(d.kindsShown(), column) + 1) % NotebookOptions.GUIDE_STEPS.length;
        d.setKindsShown(EraseKind.withStep(d.kindsShown(), column, step));
        return EraseKind.stepNote(column, step);
    }
    /** The kind tool {@code tool} lays at {@code px} and {@code dark} ({@code InkWash.kindOf}), 0 for the eraser. */
    @JSExport public static int kindOf(int tool, double px, double dark) { Brush b = brush(tool); return b == null ? 0 : ResolvedBrush.of(b.parts, px, dark).medium & ToneDocument.KIND_ALL; }
    /** Why tool {@code tool} may not draw on page {@code p} ({@code PaintActivity.hiddenNote}): "Pen is hidden, see Show Kinds"; "" when it may. */
    @JSExport public static String hiddenNote(int p, int tool, double px, double dark, int eraseKinds) {
        int shown = at(p).d.kindsShown();
        boolean eraser = tool == ToolSlots.ERASER;
        String note = EraseKind.hiddenNote(shown, eraser, eraser ? 0 : kindOf(tool, px, dark), eraser ? EraseKind.takes(eraseKinds, EraseKind.hidden(shown)) : 0);
        return note == null ? "" : note;
    }
    /** Tool {@code tool} taken up ({@code PaintActivity.sayKind}): "Pen is hidden" while its kind isn't Full; "" else. */
    @JSExport public static String takenUp(int p, int tool, double px, double dark) {
        String said = tool == ToolSlots.ERASER ? null : EraseKind.takenUp(at(p).d.kindsShown(), kindOf(tool, px, dark));
        return said == null ? "" : said;
    }

    /** The column's Show Kinds, sand timer and countdown's row: their words and sizes, and how soon a refusal's note may come again. */
    @JSExport public static String columnWords() {
        return new J().o().k("kinds").v(EraseKind.SHOW_KINDS).k("sand").v(SandTimer.NAME).k("timer").v(DrawLayout.TIMER).k("iconFull").v(DrawLayout.ICON_FULL)
                .k("noteAgain").v(DrawLayout.NOTE_AGAIN_MS).e().toString();
    }
    /** The sand timer ({@link SandTimer}): the app's, across pages; time is the page's clock, ms. */
    private static final SandTimer SAND = new SandTimer();
    /** Its words and its menu ({@code InkWashBar.timerMenu}): each time, the one set marked. */
    @JSExport public static String sandMenu() {
        J j = new J().o().k("name").v(SandTimer.NAME).k("timeUp").v(SandTimer.TIME_UP).k("done").v(SandTimer.DONE).k("offerMs").v(PageClear.OFFER_MS).k("chosen").v(SandTimer.chosen(SAND.seconds())).k("labels").a();
        for (String t : SandTimer.labels()) j.v(t);
        return j.e().e().toString();
    }
    /** Its pick: {@link SandTimer#SECONDS}' {@code choice} from {@code now}. */
    @JSExport public static void sandSet(int choice, double now) { SAND.set(SandTimer.SECONDS[choice], (long) now); }
    /**
     * Its look at {@code now} ({@code PaintActivity.timedLook}'s): counting while the page shows ({@code showing}); at zero,
     * once the pen is up ({@code stroking}), Time's up. As JSON: the countdown's words (null: its icon), whether to show Time's up, and the next look, ms (−1: none).
     */
    @JSExport public static String sandLook(double now, boolean showing, boolean stroking) {
        long t = (long) now;
        if (showing) SAND.resume(t); else SAND.pause(t);
        boolean waits = SAND.waits(t, stroking), up = !waits && SAND.ended(t);
        String countdown = SAND.countdown(t);
        J j = new J().o().k("countdown"); if (countdown == null) j.raw("null"); else j.v(countdown).k("said").v(NotebookRules.countdownSaid(countdown));
        return j.k("icon").v(countdown == null).k("timeUp").v(up).k("next").v(waits ? 150 : SAND.nextLook(t)).e().toString();
    }

    /** The wet indicator's look ({@link WetLook}): the drawing screen's (the scratch pad's went 2026-11-03). */
    private static final WetLook WET = new WetLook();
    /**
     * The wet indicator on page {@code p} (−1: no page, dry) looks at {@code now} while it shows {@code shown}
     * ({@link WetLook.State}'s ordinal): what to show now (−1: as it is), the next look (ms, −1: none), and whether a tap's
     * drying still runs ({@link WatercolorSession#hurrying}).
     */
    @JSExport @JSByRef public static double[] wetLook(int p, int shown, double now, boolean stroking) {
        WatercolorSession s = p < 0 || at(p) == null ? null : at(p).session;
        WetLook w = WET;
        w.look(WetLook.State.values()[shown], (long) now, stroking, s != null && s.wet(), s != null && s.moving(), s != null && s.onlyRendering());
        return new double[]{w.state == null ? -1 : w.state.ordinal(), w.next, s != null && s.hurrying() ? 1 : 0};
    }
    /** Its words and drawings by state, and the blow dryer's frames ({@link WetLook#BLOW_FRAMES}). */
    @JSExport public static String wetWords() {
        J j = new J().o().k("icon").v(WetLook.ICON_DP).k("dryer").v(WetLook.DRYER_DP).k("frames").v(WetLook.BLOW_FRAMES).k("frameMs").v(WetLook.BLOW_MS).k("said").a();
        for (WetLook.State s : WetLook.State.values()) j.v(WetLook.said(s));
        j.e().k("icons").a(); for (WetLook.State s : WetLook.State.values()) j.v(WetLook.icon(s));
        return j.e().e().toString();
    }
    /** A tap on the wet indicator ({@code DryingTicker.hurry}): what is wet runs on to its end as fast as its ticks go; whether ticks remain ({@link #hurryTick}). */
    @JSExport public static boolean hurry(int p) { Page g = at(p); return g.session.wet() && g.session.hurry(g.d); }
    /** Whether a tap's drying still runs on page {@code p} ({@link WatercolorSession#hurrying}): its ticks are {@link #hurryTick}'s. */
    @JSExport public static boolean hurrying(int p) { return at(p).session.hurrying(); }
    /** One of a tap's ticks ({@link WatercolorSession#tick}, unpaced): the ms to the next, −1 once done. */
    @JSExport public static double hurryTick(int p) { Page g = at(p); return g.session.tick(g.d, () -> { }); }
    // ---- The Stamp, Cut Stamp and Import Image (stamp.js): the Manta's DrawLayout.stampRows, ToolSlots.cutStamp, ----
    // ---- StampNib and PictureImport (its Frame and Placing), as InkWashBar, PaintActivity and PicturePlacer use them. ----

    /** Their words and numbers, as JSON. */
    @JSExport public static String stampWords() {
        J j = new J().o().k("cut").v(StampNib.CUT).k("lasso").v(StampNib.LASSO).k("lassoMs").v(StampNib.LASSO_MS).k("nothing").v(StampNib.NOTHING)
                .k("nothingMs").v(StampNib.NOTHING_MS).k("done").v(StampNib.DONE).k("place").v(PictureImport.PLACE).k("cancel").v(PictureImport.CANCEL).k("steps").a();
        for (String w : PictureImport.STEPS) j.v(w);
        return j.e()
                .k("unreadable").v(PictureImport.UNREADABLE).k("bar").v(PictureImport.BAR).k("button").v(PictureImport.BUTTON).k("gap").v(WordBinLayout.BUTTON_GAP)
                .k("outline").v(MagnetDrag.OUTLINE).k("dropHint").v(WebBooks.DROP_PLACE).e().toString();
    }
    /**
     * Slot {@code slot}'s stamp menu ({@code InkWashBar.stampMenu}) on a {@code w × h} dp screen, as JSON: two cards wide
     * as the smudge chooser, its card (name, line, words, height), Cut Stamp, then Turns, Spacing and Ink Fade
     * ({@link DrawLayout#stampRows}), each chip's words and the one marked.
     */
    @JSExport public static String stampMenu(int slot, int w, int h) {
        Brush b = slots().brush(slot);
        DrawLayout d = new DrawLayout(w, h);
        ToolsPage.Card c = ToolsPage.card(b, ToolsLayout.CARD_LEAST - 2 * LabText.CAPTION_INSET);
        J j = new J().o().k("width").v(d.chooserWidth(2)).k("card").v(d.chooserCard()).k("chip").v(ToolsLayout.PAGE_BUTTON).k("inset").v(LabText.CAPTION_INSET)
                .k("name").v(c.name).k("line").v(c.line).k("said").v(c.name + ": " + BrushVoice.about(b.parts)).k("key").v(b.identity()).k("cut").v(StampNib.CUT).k("rows").a();
        for (DrawLayout.StampRow row : DrawLayout.stampRows(b)) {
            j.o().k("label").v(row.label).k("marked").v(row.marked).k("words").a();
            for (String word : row.words) j.v(word);
            j.e().e();
        }
        return j.e().e().toString();
    }
    /** The stamp menu's card's loop ({@code LabLibrary.sampler}: the brush's loop at its sample grey), ARGB. */
    @JSExport @JSByRef public static int[] stampLoop(int slot, int w, int h) {
        ModularBrush b = BrushLab.copyOf(slots().brush(slot)).parts;
        return WildSample.modular(b, ModularSizes.sampleDarkness(b), WildSample.Shape.LOOP, w, h, true);
    }
    /** The stamp menu's row {@code row}, chip {@code chip} tapped ({@code InkWashBar.setStamp}): the slot's brush with it, the guest's own; whether it changed. */
    @JSExport public static boolean stampSet(int slot, int row, int chip) {
        DrawLayout.StampRow r = DrawLayout.stampRows(slots().brush(slot)).get(row);
        if (!slots().use(slot, DrawLayout.stamped(slots().brush(slot), r.dial, r.values[chip]))) return false;
        own(slot);
        return true;
    }
    /** x, y pairs as two lists. */
    private static float[][] apart(float[] points) {
        int n = points.length / 2;
        float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) { xs[i] = points[2 * i]; ys[i] = points[2 * i + 1]; }
        return new float[][]{xs, ys};
    }
    /** Cut Stamp's lasso ({@code points}, x, y pairs) lifted on page {@code p}, for slot {@code slot} ({@link ToolSlots#cutStamp}); its words. */
    @JSExport public static String stampCut(int p, int slot, @JSByRef float[] points) {
        float[][] xy = apart(points);
        String said = slots().cutStamp(slot, at(p).d, xy[0], xy[1], xy[0].length);
        if (StampNib.DONE.equals(said)) own(slot);
        return said;
    }
    /** Brush Lab's Cut Stamp ({@code BrushLabActivity.cutStamp}): the lasso on the scratch pad, page {@code p}; the lab's Stamp prints the ink in it ({@link BrushLab#cutNib}). Its words. */
    @JSExport public static String labCut(int p, @JSByRef float[] points) {
        float[][] xy = apart(points);
        ToneDocument d = at(p).d;
        StampNib nib = StampNib.cut(d::compositeTone, d.paperTone(), xy[0], xy[1], xy[0].length, d.width, d.height);
        if (nib == null) return StampNib.NOTHING;
        lab.cutNib(StampNib.keep(nib));
        return StampNib.DONE;
    }

    /** The size a {@code w × h} picture is read at for a {@code pageW × pageH} page ({@code PicturePlacer.read}): no larger than fits it. */
    @JSExport @JSByRef public static int[] pictureSize(int w, int h, int pageW, int pageH) {
        int[] f = PictureImport.fit(w, h, pageW, pageH);
        return f[2] < w ? new int[]{f[2], f[3]} : new int[]{w, h};
    }
    private static PictureImport.Placing placing;
    /**
     * Import Image's picture, {@code w × h}, placed on page {@code p} fitted inside what shows of it ({@code l, t, r, b},
     * page px; {@link PictureImport.Frame#fitted}); "" or, on a locked page, its note (and nothing placed).
     */
    @JSExport public static String placeBegin(int p, int w, int h, double l, double t, double r, double b) {
        ToneDocument d = at(p).d;
        placing = null;
        if (d.locked()) return NotebookRules.PAGE_LOCKED;
        int[] shown = {(int) Math.round(l), (int) Math.round(t), (int) Math.round(r), (int) Math.round(b)};
        placing = new PictureImport.Placing(PictureImport.Frame.fitted(w, h, shown, d.width, d.height));
        return "";
    }
    /** Where it lies: x, y, w, h (page px), its rect and its handle rounded (left, top, right, bottom each), and 1 while a pointer moves it. */
    @JSExport @JSByRef public static double[] placeFrame() {
        PictureImport.Frame f = placing.frame;
        int[] r = f.rect(), h = f.handle();
        return new double[]{f.x, f.y, f.w, f.h, r[0], r[1], r[2], r[3], h[0], h[1], h[2], h[3], placing.dragging() ? 1 : 0};
    }
    /** The pointers on it ({@link PictureImport.Placing}): the first down, a second (a pinch; whether it is), a move (whether it changed), a pinch's lift, every pointer up. */
    @JSExport public static void placeDown(double x, double y) { placing.down((float) x, (float) y); }
    @JSExport public static boolean placePinch(double ax, double ay, double bx, double by) { return placing.pinch((float) ax, (float) ay, (float) bx, (float) by); }
    @JSExport public static boolean placeMove(double ax, double ay, double bx, double by) { return placing.moved((float) ax, (float) ay, (float) bx, (float) by); }
    @JSExport public static boolean placePinching() { return placing.pinching(); }
    @JSExport public static void placeLifting() { placing.lifting(); }
    @JSExport public static void placeUp() { placing.up(); }
    /** Button {@code step} of {@link PictureImport#STEPS} (Lighter, Darker, More or Less Contrast), one step ({@link PictureImport.Placing#step}): whether it took it; whether it has one left. */
    @JSExport public static boolean placeStep(int step) { return placing.step(step); }
    @JSExport public static boolean placeCanStep(int step) { return placing.canStep(step); }
    /** {@code argb} as Place lays it ({@link PictureImport#greys}, its steps as it is being placed): a grey each, −1 clear. */
    @JSExport @JSByRef public static int[] pictureGreys(@JSByRef int[] argb) { return PictureImport.greys(argb.clone(), placing.shade(), placing.contrast()); }
    /**
     * Place: {@code argb} (the picture as it covers the page's {@code x, y, w × h}) laid on page {@code p}'s layer drawn on
     * ({@link PictureImport#lay}, one Undo step), as any mark; whether it changed the page.
     */
    @JSExport public static boolean placeLay(int p, int x, int y, int w, int h, @JSByRef int[] argb) {
        Page g = at(p);
        int shade = placing.shade(), contrast = placing.contrast();
        placing = null;
        g.session.settleNow(g.d);
        return PictureImport.place(g.d, x, y, w, h, PictureImport.greys(argb.clone(), shade, contrast));
    }
    /** Cancel: nothing laid. */
    @JSExport public static void placeCancel() { placing = null; }

    /** A little JSON, written in order. */
    static final class J {
        private final StringBuilder s = new StringBuilder();
        private boolean first = true;
        private J sep() { if (!first) s.append(','); first = false; return this; }
        private final java.util.ArrayDeque<Character> open = new java.util.ArrayDeque<>();
        J o() { sep(); s.append('{'); open.push('}'); first = true; return this; }
        J a() { sep(); s.append('['); open.push(']'); first = true; return this; }
        J e() { s.append(open.pop()); first = false; return this; }
        J k(String key) { sep(); quote(key); s.append(':'); first = true; return this; }
        J v(String v) { sep(); quote(v); return this; }
        J v(double v) { sep(); s.append(Double.isFinite(v) ? (v == Math.rint(v) && Math.abs(v) < 1e15 ? Long.toString((long) v) : Double.toString(v)) : "null"); return this; }
        J v(boolean v) { sep(); s.append(v); return this; }
        J raw(String v) { sep(); s.append(v); return this; }
        private void quote(String v) {
            s.append('"');
            for (int i = 0; i < v.length(); i++) {
                char c = v.charAt(i);
                if (c == '"' || c == '\\') s.append('\\').append(c);
                else if (c < 0x20) s.append("\\u00").append(Character.forDigit(c >> 4, 16)).append(Character.forDigit(c & 15, 16));
                else s.append(c);
            }
            s.append('"');
        }
        @Override public String toString() { return s.toString(); }
    }

    /** Greys as the page's writes take them: 255 (clear) as −1. */
    private static int[] ints(byte[] greys) {
        int[] out = new int[greys.length];
        for (int i = 0; i < out.length; i++) { int v = greys[i] & 255; out[i] = v == 255 ? -1 : v; }
        return out;
    }
}
