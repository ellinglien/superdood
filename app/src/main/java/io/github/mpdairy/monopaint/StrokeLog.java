package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Record and Replay (docs/archive/brush-lab/prototypes/mischief-lab-3.NOTES.md, section 1): what changed a page, in order,
 * as the pen (or a finger) sent it, kept in the page's file ({@link DocumentCodec}'s {@code RPLY} section) so the page can
 * draw itself again: brush strokes (the pen's, a finger's, the smudge tool's) with their brush code, size and darkness;
 * eraser strokes with their size and the kinds they take ({@link EraseKind}); the Lasso's lifts (the loop) and what was
 * done with each piece (put down where it lies now, or deleted: {@link #PLACE}). Each sample keeps its position (half
 * pixels), pressure, tilt and the ms since the sample before. Replay works because the engine is deterministic: the same
 * samples, brush and grey make the same marks, and the Lasso's own code ({@link LassoPlanes}) lifts and lays the same
 * piece. What plays is what was stored, so positions are rounded when recorded (a Lasso's loop before it lifts).
 * Shapes ({@link #SHAPE}), placed pictures ({@link #PICTURE}) and Merge Old Layers and Merge Down ({@link #MERGE}) are
 * logged too, and replay through the code that made them ({@link ShapeStroke#lay}, {@link PictureImport#lay},
 * {@link InkWashLayers#merge}); a picture's or a merged layer's pixels are kept deflated ({@link Pixels}).
 *
 * <p>Immutable. An entry knows the Undo step that made it ({@code serial}, {@link ToneDocument}'s), so Undo and Redo
 * show and hide entries with their steps; a Clear page leaves a marker that hides the entries before it. Fill is a
 * brush's (Fill in, Filled Shape), so its strokes are logged. Not logged: Make Ink, text, a live page's changes from
 * another tablet, layers added, deleted or shown otherwise (each is a page of its own; Show Through is the notebook's).
 * Pure, checked on the desktop ({@code StrokeLogChecks}).
 */
final class StrokeLog {
    static final StrokeLog NONE = new StrokeLog(Collections.emptyList(), 0);
    /**
     * The most samples a page keeps (eraser strokes and Lasso loops count too; about 600 KB in the file at most); past it
     * the oldest entries are dropped, so a replay starts part-way.
     */
    static final int MOST_SAMPLES = 100000;
    /** The most bytes of the file's section: an older reader refuses a longer one, so {@link #encode} never writes past it. */
    static final int MOST_BYTES = 1 << 20;
    /** The most drying ticks an entry keeps ({@link Entry#ticks}), and what it keeps for "settled to its end": a replay ticks until nothing settles. */
    static final int MOST_TICKS = 2000;
    /**
     * The most bytes a picture's or a merged layer's pixels keep ({@link Pixels}), deflated: a quarter of the section, so
     * the entries before it still fit; past it they are kept at half the size, a quarter, and so on, until they fit.
     */
    static final int MOST_PIXELS = MOST_BYTES / 4;
    /** The longest pause inside a stroke that replay keeps, ms. */
    static final int MOST_PAUSE = 2000;
    /**
     * 1: pen strokes only (float size and grey); 2: each entry its kind ({@link #PEN} and the rest). A version 2 reader
     * that doesn't know a kind ({@link #SHAPE} and on, 2026-11-03) reads no log: the page opens without a replay.
     */
    private static final int VERSION = 2;

    /**
     * What an entry is: a brush stroke by the pen, the smudge tool or a finger; the eraser's stroke by the pen (its size
     * by pressure) or a finger (one size); a Lasso loop's lift; a lifted piece put down or deleted; a Clear page; a
     * shape ({@link Shape}); a placed picture ({@link Picture}); the page's layers merged ({@link Merge}).
     */
    static final int PEN = 0, SMUDGE = 1, FINGER = 2, ERASE = 3, ERASE_FINGER = 4, LIFT = 5, PLACE = 6, CLEAR = 7, SHAPE = 8, PICTURE = 9, MERGE = 10;

    /** One entry: a stroke ({@code code} its brush's, null for the eraser's), a Lasso's lift or placing, a Clear page marker, a shape, a picture or a merge. */
    static final class Entry {
        final int kind;
        final long serial;
        /** A brush stroke's brush code; a shape's, the brush whose layer it went on. */
        final String code;
        /** A brush stroke's paint holder ({@link ToolSlots#holder}: Keeps Paint's, the tool's or the finger's), or null. */
        final String holder;
        /**
         * The drying ticks ({@link WatercolorSession#tickDrying}) the page took since the entry before: what settled
         * meanwhile; {@link #MOST_TICKS}: all of it, to its end (the Manta settles on a worker and in slices, whose ticks
         * aren't these, so it logs only that; one stroke drawn on a wash still settling there replays it laid unsettled).
         */
        final int ticks;
        /** A brush stroke's size and grey; the eraser's size at its broadest (a finger's: its one size), px. */
        final double px, darkness;
        /** The kinds the eraser takes ({@link EraseKind}; 0: everything). */
        final int kinds;
        /** Per sample: half pixels, ms since the one before (0 for the first), pressure and tilt bytes ({@link #NO_TILT}: none). A lift's: its loop. */
        final int[] x, y, dt;
        final byte[] pressure, tiltX, tiltY;
        /** A {@link #PLACE}'s piece and pose, else null. */
        final Place place;
        /** A {@link #SHAPE}'s {@link Shape}, a {@link #PICTURE}'s {@link Picture} or a {@link #MERGE}'s {@link Merge}, else null. */
        final Object made;
        /**
         * Whether it made the Undo step it is tied to. A stroke that changed nothing is logged too (it may have laid what
         * was settling, or picked up paint), tied to the step before; kept in memory only, entries read from a file are steps.
         */
        final boolean stepped;
        /**
         * Sharp dry brushes ({@link SharpStrokes}): the plane a sharp stroke was laid on ({@link ToneDocument#address}),
         * -1 for every other entry; and the zoom it was drawn at ({@link StrokePoints#zoom}: its speed is the hand's on the glass).
         */
        final int plane;
        final float zoom;
        /** A sharp Stamp stroke's starting ink ({@link StampStamper#startLoad}: carried on from its holder's last stroke); 1 for every other entry. */
        final double ink;
        Entry(int kind, long serial, String code, String holder, int ticks, double px, double darkness, int kinds, int[] x, int[] y, int[] dt, byte[] pressure, byte[] tiltX, byte[] tiltY, Place place) {
            this(kind, serial, code, holder, ticks, px, darkness, kinds, x, y, dt, pressure, tiltX, tiltY, place, null, true, -1, 1, 1);
        }
        private Entry(int kind, long serial, String code, String holder, int ticks, double px, double darkness, int kinds, int[] x, int[] y, int[] dt, byte[] pressure, byte[] tiltX, byte[] tiltY, Place place, Object made, boolean stepped, int plane, float zoom, double ink) {
            this.stepped = stepped; this.made = made; this.plane = plane; this.zoom = zoom; this.ink = ink;
            this.kind = kind; this.serial = serial; this.code = code; this.holder = holder; this.ticks = ticks; this.px = px; this.darkness = darkness; this.kinds = kinds;
            this.x = x; this.y = y; this.dt = dt; this.pressure = pressure; this.tiltX = tiltX; this.tiltY = tiltY; this.place = place;
        }
        int size() { return x.length; }
        /** What it counts against {@link #MOST_SAMPLES}: its samples, and a picture's or a merged layer's bytes as samples (6 bytes each). */
        int weight() {
            Pixels p = made instanceof Picture ? ((Picture) made).greys : made instanceof Merge ? ((Merge) made).above : null;
            return size() + (p == null ? 0 : p.packed.length / 6);
        }
        Entry at(long s) { return new Entry(kind, s, code, holder, ticks, px, darkness, kinds, x, y, dt, pressure, tiltX, tiltY, place, made, stepped, plane, zoom, ink); }
        /** The same stroke kept sharp on plane {@code plane} ({@link ToneDocument#address}), drawn at {@code zoom}, a Stamp's starting with {@code ink}. */
        Entry sharp(int plane, float zoom, double ink) { return new Entry(kind, serial, code, holder, ticks, px, darkness, kinds, x, y, dt, pressure, tiltX, tiltY, place, made, stepped, plane, zoom, ink); }
        /** Whether it is a sharp dry brush's stroke ({@link #plane}). */
        boolean sharp() { return plane >= 0; }
        /** The same entry after {@code n} drying ticks; {@code step}: whether it made its step ({@link #stepped}). */
        Entry after(int n, boolean step) { return n == ticks && step == stepped ? this : new Entry(kind, serial, code, holder, n, px, darkness, kinds, x, y, dt, pressure, tiltX, tiltY, place, made, step, plane, zoom, ink); }
        static Entry marker(long serial) { return bare(CLEAR, serial, null); }
        private static Entry bare(int kind, long serial, Place place) { return made(kind, serial, null, place, null); }
        private static Entry made(int kind, long serial, String code, Place place, Object made) {
            return new Entry(kind, serial, code, null, 0, 0, 0, 0, new int[0], new int[0], new int[0], new byte[0], new byte[0], new byte[0], place, made, true, -1, 1, 1);
        }
        /** Shape {@code s} on the layer the strokes of the brush coded {@code code} go to, made at Undo step {@code serial}. */
        static Entry shape(long serial, String code, Shape s) { return made(SHAPE, serial, code, null, s); }
        static Entry picture(long serial, Picture p) { return made(PICTURE, serial, null, null, p); }
        static Entry merge(long serial, Merge m) { return made(MERGE, serial, null, null, m); }
        Shape shape() { return (Shape) made; }
        Picture picture() { return (Picture) made; }
        Merge merge() { return (Merge) made; }
        /** A brush stroke (the pen's, a finger's or the smudge tool's): drawn again with its brush. */
        boolean brushed() { return kind == PEN || kind == SMUDGE || kind == FINGER; }
        float px(int i) { return x[i] / 2f; }
        float py(int i) { return y[i] / 2f; }
        float pressure(int i) { return (pressure[i] & 255) / 255f; }
        float tiltX(int i) { return tiltX[i] == NO_TILT ? Float.NaN : tiltX[i]; }
        float tiltY(int i) { return tiltY[i] == NO_TILT ? Float.NaN : tiltY[i]; }
        /** A lift's loop, {@code x, y} pairs, page px. */
        float[] loop() {
            float[] points = new float[2 * size()];
            for (int i = 0; i < size(); i++) { points[2 * i] = px(i); points[2 * i + 1] = py(i); }
            return points;
        }
        /** A Lasso's {@code count}-point loop ({@code x, y} pairs, page px) as it lifts and is logged: on half pixels. */
        static float[] halfPixels(float[] points, int count) {
            float[] at = new float[2 * count];
            for (int i = 0; i < at.length; i++) at[i] = Math.round(points[i] * 2) / 2f;
            return at;
        }
        /** The lift by the loop {@code points} ({@link #halfPixels} already), made at Undo step {@code serial}. */
        static Entry lift(long serial, float[] points, int count) {
            Recorder r = new Recorder(LIFT, null, null, 0, 0, 0);
            for (int i = 0; i < count; i++) r.add(points[2 * i], points[2 * i + 1], 0, Float.NaN, Float.NaN, -1);
            return r.done(serial);
        }
    }
    static final byte NO_TILT = Byte.MIN_VALUE;

    /**
     * What the Lasso did with a lifted piece ({@link LassoPlanes#place}): the lift it came from, {@code back} entries
     * before (0: not kept, as one cut from a page left behind); with {@code drop}, laid where it lies ({@code centerX},
     * {@code centerY}, {@code scale}, {@code angle}, {@link FloatingSelection}'s pose); with {@code cut}, first taken from
     * where it was lifted (a moved piece, Delete, Cut; a copy, Duplicate's or Paste's, only lays).
     */
    static final class Place {
        final int back;
        final boolean drop, cut;
        final float centerX, centerY, scale;
        final double angle;
        Place(int back, boolean drop, boolean cut, float centerX, float centerY, float scale, double angle) {
            this.back = back; this.drop = drop; this.cut = cut; this.centerX = centerX; this.centerY = centerY; this.scale = scale; this.angle = angle;
        }
    }

    /**
     * A shape (Shapes: {@code DrawingPad.inkShape}, the browser's {@code shapeDown}): its {@link ToolSettings.Shape},
     * filled or not, its outline's width px and its grey (a tone), dragged from {@code x0, y0} to {@code x1, y1}, page px
     * as the pen sent them, which {@link ShapeGeometry} rasters.
     */
    static final class Shape {
        final ToolSettings.Shape shape;
        final boolean filled;
        final int width, shade;
        final float x0, y0, x1, y1;
        Shape(ToolSettings.Shape shape, boolean filled, int width, int shade, float x0, float y0, float x1, float y1) {
            this.shape = shape; this.filled = filled; this.width = width; this.shade = shade; this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
        }
        /** The shape {@code outline} (Shapes' settings, its outline's width set) draws in {@code shade}. */
        static Shape of(ToolSettings outline, int shade, float x0, float y0, float x1, float y1) { return new Shape(outline.shape, outline.filled, outline.outlineWidth, shade, x0, y0, x1, y1); }
        ToolSettings settings() { return ToolSettings.defaults(ToolSettings.Tool.SHAPES).shape(shape).filled(filled).outlineWidth(width); }
        /** Its spans on a {@code width × height} page, two intervals a row, as the screen showed it last ({@link ShapeGeometry#raster}). */
        int[] spans(int width, int height) { int[] spans = new int[4 * height]; new ShapeGeometry(settings(), width, height, x0, y0).raster(x1, y1, spans); return spans; }
    }

    /** A picture placed ({@link PictureImport#lay}): its greys ({@link #greys}) over {@code x, y, w × h}, on engine layer {@code layer}. */
    static final class Picture {
        final int x, y, w, h, layer;
        final Pixels greys;
        Picture(int x, int y, int w, int h, int layer, Pixels greys) { this.x = x; this.y = y; this.w = w; this.h = h; this.layer = layer; this.greys = greys; }
        /** {@code greys} ({@link PictureImport#greys}: −1 clear) laid at {@code x, y, w × h} on {@code layer}. */
        static Picture of(int x, int y, int w, int h, int layer, int[] greys) {
            byte[] g = new byte[w * h];
            // A grey of 255 never darkens a layer (PictureImport.lay), so it stands for clear.
            for (int i = 0; i < g.length; i++) g[i] = (byte) (greys[i] < 0 ? 255 : greys[i]);
            return new Picture(x, y, w, h, layer, Pixels.of(w, h, g));
        }
        /** Its greys as {@link PictureImport#lay} takes them, {@code w × h} (each kept pixel standing for its step's square); null if they don't read. */
        int[] greys() {
            byte[][] g = greys.planes();
            if (g == null) return null;
            int[] out = new int[w * h];
            for (int i = 0; i < out.length; i++) out[i] = g[0][i] == (byte) 255 ? -1 : g[0][i] & 255;
            return out;
        }
    }

    /**
     * The page's layers merged into one pair ({@link InkWashLayers#merge}): Merge Old Layers, or Merge Down, which brings
     * in the layer stacked above, kept flattened ({@code above}: its tones and coverage, and its {@code kinds}; null for
     * Merge Old Layers). With the page's layers just before ({@code layers}: each one's opacity, plus 128 when hidden;
     * {@code active} selected), so a replay starts on those.
     */
    static final class Merge {
        final int active, kinds;
        final byte[] layers;
        final Pixels above;
        Merge(int active, byte[] layers, int kinds, Pixels above) { this.active = active; this.layers = layers; this.kinds = kinds; this.above = above; }
        /** {@code document}'s layers as they are, about to merge with {@code above} (flattened; null: none). */
        static Merge of(ToneDocument document, ToneDocument.Layer above) {
            byte[] layers = new byte[document.layerCount()];
            for (int i = 0; i < layers.length; i++) layers[i] = (byte) (document.layerOpacity(i) | (document.layerVisible(i) ? 0 : 128));
            return new Merge(document.activeLayer(), layers, above == null ? 0 : above.kinds & 255, above == null ? null : Pixels.of(document.width, document.height, above.tones, above.alpha));
        }
        /** The layer above as kept, one flattened layer of a {@code width × height} page; null for none, or when it doesn't read. */
        ToneDocument.Layer above(int width, int height) {
            byte[][] p = above == null || above.width != width || above.height != height ? null : above.planes();
            if (p == null) return null;
            ToneDocument.Layer layer = InkWashLayers.empty("Ink", width, height);
            System.arraycopy(p[0], 0, layer.tones, 0, p[0].length); System.arraycopy(p[1], 0, layer.alpha, 0, p[1].length);
            layer.kinds = (byte) kinds;
            return layer;
        }
        /** {@code document}'s layers made blank ones like those just before, {@code active} selected: a replay's copy before its first entry. */
        void before(ToneDocument document) {
            List<ToneDocument.Layer> next = new ArrayList<>();
            for (int i = 0; i < layers.length; i++) {
                ToneDocument.Layer layer = InkWashLayers.empty(i % 2 == 0 ? "Wash" : "Ink", document.width, document.height);
                layer.opacity = layers[i] & 127; layer.visible = (layers[i] & 128) == 0;
                next.add(layer);
            }
            document.replaceLayers(next, active);
        }
    }

    /**
     * Planes of {@code width × height} bytes kept deflated, every {@code step}th pixel each way (1: every pixel), the
     * smallest step whose bytes fit {@link #MOST_PIXELS}; read back at full size, each kept pixel standing for the
     * {@code step × step} square it was taken from.
     */
    static final class Pixels {
        final int width, height, step, planes;
        final byte[] packed;
        Pixels(int width, int height, int step, int planes, byte[] packed) { this.width = width; this.height = height; this.step = step; this.planes = planes; this.packed = packed; }
        static Pixels of(int width, int height, byte[]... planes) {
            for (int step = 1; ; step *= 2) {
                int w = (width + step - 1) / step, h = (height + step - 1) / step;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                java.util.zip.Deflater deflater = new java.util.zip.Deflater();
                try (java.util.zip.DeflaterOutputStream out = new java.util.zip.DeflaterOutputStream(bytes, deflater)) {
                    byte[] row = new byte[w];
                    for (byte[] plane : planes) for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) row[x] = plane[y * step * width + x * step];
                        out.write(row);
                    }
                } catch (IOException never) { throw new IllegalStateException(never); }
                finally { deflater.end(); }
                if (bytes.size() <= MOST_PIXELS || w == 1 && h == 1) return new Pixels(width, height, step, planes.length, bytes.toByteArray());
            }
        }
        /** The planes at full size; null when the bytes don't read. */
        byte[][] planes() {
            int w = (width + step - 1) / step, h = (height + step - 1) / step;
            byte[] kept = new byte[planes * w * h];
            try (java.util.zip.InflaterInputStream in = new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(packed))) {
                for (int at = 0, n; at < kept.length; at += n) if ((n = in.read(kept, at, kept.length - at)) < 0) return null;
            } catch (IOException bad) { return null; }
            byte[][] out = new byte[planes][width * height];
            for (int p = 0; p < planes; p++) for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                out[p][y * width + x] = kept[p * w * h + y / step * w + x / step];
            return out;
        }
    }

    /** Gathers one stroke's samples as the pen sends them. */
    /** The kind of mark brush {@code code} lays at {@code px} and {@code darkness} ({@link ResolvedBrush#medium}'s {@code ToneDocument.KIND_} bits; 0: none, the eraser's). */
    static int medium(String code, double px, double darkness) { return code == null ? 0 : ResolvedBrush.of(ModularCode.decode(code), px, darkness).medium & ToneDocument.KIND_ALL; }
    static final class Recorder {
        private final int kind, kinds;
        private final String code, holder;
        private final double px, darkness;
        private final List<int[]> rows = new ArrayList<>();
        private long last = -1;
        /**
         * The time a kept stroke is fed the newest sample at: the first sample's own (1000 untimed) and each sample's kept
         * ms since the last, as a replay feeds them from 1000 ({@link Player}); a stroke reads only the times between, but
         * a Stamp's rest since its holder's last print reads the clock across strokes.
         */
        private long clock = 1000, first;
        /**
         * Sharp dry brushes ({@link SharpStrokes}): whether its stroke is fed the samples as kept ({@link #keeps}), so drawn
         * again it draws the same; the zoom it is drawn at; and, set at its end, the plane it was laid on (-1: not kept sharp).
         */
        boolean keeps;
        float zoom = 1;
        int plane = -1;
        /** A kept Stamp stroke's: the ink its holder's last stroke left ({@link HeldPaint#stampInk}), as the stroke starts with it; null for none. */
        StampStamper.Ink stampInk;
        /** A pen stroke with brush {@code code}. */
        Recorder(String code, double px, double darkness) { this(PEN, code, null, px, darkness, 0); }
        Recorder(int kind, String code, String holder, double px, double darkness, int kinds) { this.kind = kind; this.code = code; this.holder = holder; this.px = px; this.darkness = darkness; this.kinds = kinds; }
        /** The kind of mark the stroke lays ({@link #medium}), while it is drawn: a live stroke's parts' (LiveProbe.drawing). */
        int medium() { return StrokeLog.medium(code, px, darkness); }
        /**
         * The recorder for a stroke the drawing screen starts ({@link StrokeStart#start}), the Manta's and the browser's:
         * {@code brush} at {@code look} (null: the eraser, taking {@code eraseKinds}), its paint {@code holder}'s
         * ({@link ToolSlots#holder}), {@code smudge} the smudge tool's, {@code finger} drawn by one finger.
         */
        static Recorder of(Brush brush, ToolSwatches.Look look, int eraseKinds, String holder, boolean smudge, boolean finger) {
            if (brush == null) return new Recorder(ERASE, null, null, look.px, 0, eraseKinds);
            return new Recorder(smudge ? SMUDGE : finger ? FINGER : PEN, ModularCode.encode(brush.parts), holder, look.px, look.darkness, 0);
        }
        /** A finger's eraser ({@code InkWash.fingerEraser}): {@code diameter} px, taking {@code eraseKinds}. */
        static Recorder fingerEraser(float diameter, int eraseKinds) { return new Recorder(ERASE_FINGER, null, null, diameter, 0, eraseKinds); }
        /** One sample: page px, pressure 0 to 1, tilt degrees (NaN: none), the input's clock in ms (or a negative for none). */
        void add(float x, float y, float pressure, float tiltX, float tiltY, long time) {
            int dt = last < 0 || time < 0 ? 0 : (int) Math.max(0, Math.min(MOST_PAUSE, time - last));
            if (time >= 0) last = time;
            if (rows.isEmpty()) { if (time >= 0) clock = time; first = clock; }
            clock += dt;
            rows.add(new int[]{Math.round(x * 2), Math.round(y * 2), dt, Math.round(Math.max(0, Math.min(1, pressure)) * 255), tilt(tiltX), tilt(tiltY)});
        }
        /**
         * Sharp dry brushes: the newest sample ({@link #add}) as kept and as a replay feeds it, into {@code out}: x, y,
         * pressure, tilts and the time (ms, as {@link Player}'s clock), for its stroke to draw exactly what the log keeps.
         */
        void kept(float[] out, long[] time) {
            int[] r = rows.get(rows.size() - 1);
            out[0] = r[0] / 2f; out[1] = r[1] / 2f; out[2] = (r[3] & 255) / 255f;
            out[3] = r[4] == NO_TILT ? Float.NaN : r[4]; out[4] = r[5] == NO_TILT ? Float.NaN : r[5];
            time[0] = clock;
        }
        private static int tilt(float t) { return Float.isNaN(t) ? NO_TILT : Math.max(-90, Math.min(90, Math.round(t))); }
        int size() { return rows.size(); }
        /** The stroke as an entry made by Undo step {@code serial}; null for none. */
        Entry done(long serial) {
            int n = rows.size();
            if (n == 0) return null;
            int[] x = new int[n], y = new int[n], dt = new int[n];
            byte[] p = new byte[n], tx = new byte[n], ty = new byte[n];
            for (int i = 0; i < n; i++) {
                int[] r = rows.get(i);
                x[i] = r[0]; y[i] = r[1]; dt[i] = r[2]; p[i] = (byte) r[3]; tx[i] = (byte) r[4]; ty[i] = (byte) r[5];
            }
            Entry e = new Entry(kind, serial, code, holder, 0, px, darkness, kinds, x, y, dt, p, tx, ty, null);
            return keeps && plane >= 0 ? e.sharp(plane, zoom, stampInk == null ? 1 : StampStamper.startLoad(stampInk, first)) : e;
        }
    }

    private final List<Entry> entries;
    private final int samples;
    private StrokeLog(List<Entry> entries, int samples) { this.entries = entries; this.samples = samples; }

    /** The entries since the last visible Clear page marker. */
    List<Entry> strokes() {
        int from = 0;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).kind == CLEAR) from = i + 1;
        return entries.subList(from, entries.size());
    }
    boolean isEmpty() { return strokes().isEmpty(); }
    int samples() { return samples; }

    /** The log with the entries of Undo steps up to {@code top} (the rest were undone). */
    StrokeLog upTo(long top) {
        List<Entry> kept = new ArrayList<>();
        int n = 0;
        for (Entry e : entries) if (e.serial <= top) { kept.add(e); n += e.weight(); }
        return kept.size() == entries.size() ? this : new StrokeLog(kept, n);
    }
    /** {@code entry} after the steps up to {@code top}; the oldest entries go while it would pass {@link #MOST_SAMPLES} (an entry that alone does is not kept). */
    StrokeLog added(Entry entry, long top) {
        StrokeLog base = upTo(top);
        if (entry == null || entry.weight() > MOST_SAMPLES) return base;
        List<Entry> next = new ArrayList<>(base.entries); next.add(entry);
        int n = base.samples + entry.weight();
        while (n > MOST_SAMPLES) n -= next.remove(0).weight();
        return new StrokeLog(next, n);
    }
    /**
     * The Lasso put down or deleted the piece lifted by {@code lift} (an entry of this log, else it isn't logged: a piece
     * carried from another page, or one whose lift is gone), at Undo step {@code serial}, after {@code ticks}: {@link Place}'s.
     */
    StrokeLog placed(Entry lift, long serial, int ticks, boolean drop, boolean cut, FloatingSelection piece) {
        for (int i = entries.size() - 1; i >= 0; i--) if (entries.get(i) == lift)
            return added(Entry.bare(PLACE, serial, new Place(entries.size() - i, drop, cut, piece.centerX, piece.centerY, piece.scale, piece.angle)).after(ticks, true), Long.MAX_VALUE);
        return this;
    }
    /** What the file keeps at step {@code top}: the visible entries, numbered from the start of a new session. */
    StrokeLog saved(long top) {
        List<Entry> kept = new ArrayList<>();
        int n = 0;
        for (Entry e : upTo(top).strokes()) { kept.add(e.at(0)); n += e.weight(); }
        return kept.isEmpty() ? NONE : new StrokeLog(kept, n);
    }

    // ---- File format: version byte, entry count, then per entry its kind and what that kind keeps ----
    // Version 2, per entry its drying ticks, then per kind: a brush stroke its code, paint holder, px and darkness (doubles) and samples; the eraser's px (double), kinds
    // and samples; a lift its loop as samples; a placing its lift (entries back), drop and cut, and pose; a shape its brush
    // code, its shape's name, filled, width, grey and two points (floats); a picture its layer, place and greys (pixels); a
    // merge its layer selected, each layer's opacity and hidden, and the layer above (kinds, pixels) if any. Pixels: width,
    // height, step, planes, then the deflated bytes. Version 1 (pen strokes only, px and darkness floats) still reads.

    /**
     * The bytes: the newest entries that fit {@link #MOST_BYTES} (an older reader refuses a longer section); a placing
     * whose lift is not among them (dropped, or before a Clear page) is written with none, so it plays nothing.
     */
    byte[] encode() {
        List<Entry> list = strokes();
        int from = written(list);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(VERSION);
        varint(out, list.size() - from);
        for (int k = from; k < list.size(); k++) { byte[] part = encode(list.get(k), k - from); out.write(part, 0, part.length); }
        return out.toByteArray();
    }
    /** The first of {@code list} that {@link #encode} writes: the newest that fit {@link #MOST_BYTES}. */
    private static int written(List<Entry> list) {
        int from = list.size(), total = 16;
        for (int size; from > 0 && total + (size = encode(list.get(from - 1), from - 1).length) <= MOST_BYTES; from--) total += size;
        return from;
    }

    /**
     * Sharp dry brushes: which of the entries {@link #encode} writes are sharp strokes ({@link Entry#plane}), as the
     * file's SHRP section keeps them: a version byte, how many, then each one's place among those written, its plane and
     * its zoom, and in version 2 (written only when a Stamp's starting ink isn't all of it) its ink; version 3, when a
     * shape is kept ({@link #SHAPE}, sharp-more), its ink too; null when none is.
     */
    byte[] encodeSharp() {
        List<Entry> list = strokes();
        boolean any = false;
        for (Entry e : list) any |= e.sharp();
        if (!any) return null;
        int from = written(list), n = 0;
        boolean inked = false, shapes = false;
        for (int k = from; k < list.size(); k++) { inked |= list.get(k).sharp() && list.get(k).ink != 1; shapes |= list.get(k).sharp() && list.get(k).kind == SHAPE; }
        // Version 1 while no Stamp carries ink on and no shape is kept, 2 with a Stamp's ink: the versions before read what they can.
        int version = shapes ? SHARP_SHAPES : inked ? SHARP_INKED : SHARP_VERSION;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (int k = from; k < list.size(); k++) {
            Entry e = list.get(k);
            if (!e.sharp()) continue;
            varint(body, k - from); varint(body, e.plane); int32(body, Float.floatToIntBits(e.zoom));
            if (version != SHARP_VERSION) int64(body, Double.doubleToLongBits(e.ink));
            n++;
        }
        if (n == 0) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(version); varint(out, n);
        byte[] b = body.toByteArray(); out.write(b, 0, b.length);
        return out.toByteArray();
    }
    /** {@link #encodeSharp}'s versions: 1, then each stroke's ink (2), then kept shapes ({@link #SHAPE}) too, inks written (3). */
    private static final int SHARP_VERSION = 1, SHARP_INKED = 2, SHARP_SHAPES = 3;
    /**
     * This log, as read, with the sharp strokes {@code bytes} ({@link #encodeSharp}) name marked; as it is when they
     * don't read (its strokes then show as pixels when zoomed), or name an entry that isn't a brush stroke.
     */
    StrokeLog withSharp(byte[] bytes) {
        try {
            int[] at = {0};
            int version = bytes.length < 2 ? 0 : bytes[at[0]++];
            if (version != SHARP_VERSION && version != SHARP_INKED && version != SHARP_SHAPES) return this;
            List<Entry> next = new ArrayList<>(entries);
            int n = (int) read(bytes, at, next.size());
            for (int i = 0; i < n; i++) {
                int index = (int) read(bytes, at, next.size() - 1), plane = (int) read(bytes, at, 255);
                float zoom = Float.intBitsToFloat(int32(bytes, at));
                double ink = version != SHARP_VERSION ? Double.longBitsToDouble(int64(bytes, at)) : 1;
                Entry e = next.get(index);
                if (!(e.brushed() || e.kind == SHAPE && version == SHARP_SHAPES) || !(zoom > 0 && zoom <= 64) || !(ink >= StampStamper.FLOOR && ink <= 1)) return this;
                next.set(index, e.sharp(plane, zoom, ink));
            }
            return at[0] == bytes.length ? new StrokeLog(next, samples) : this;
        } catch (IOException | RuntimeException bad) { return this; }
    }
    /** Entry {@code e}, the {@code index}th written. */
    private static byte[] encode(Entry e, int index) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(e.kind);
        varint(out, e.ticks);
        if (e.brushed()) {
            text(out, e.code); text(out, e.holder == null ? "" : e.holder);
            int64(out, Double.doubleToLongBits(e.px)); int64(out, Double.doubleToLongBits(e.darkness));
        } else if (e.kind == ERASE || e.kind == ERASE_FINGER) {
            int64(out, Double.doubleToLongBits(e.px)); varint(out, e.kinds);
        } else if (e.kind == SHAPE) {
            Shape s = e.shape();
            text(out, e.code); text(out, s.shape.name()); out.write(s.filled ? 1 : 0); varint(out, s.width); varint(out, s.shade);
            for (float f : new float[]{s.x0, s.y0, s.x1, s.y1}) int32(out, Float.floatToIntBits(f));
            return out.toByteArray();
        } else if (e.kind == PICTURE) {
            Picture p = e.picture();
            varint(out, p.layer); varint(out, zigzag(p.x)); varint(out, zigzag(p.y)); pixels(out, p.greys);
            return out.toByteArray();
        } else if (e.kind == MERGE) {
            Merge m = e.merge();
            varint(out, m.active); varint(out, m.layers.length); out.write(m.layers, 0, m.layers.length);
            out.write(m.above == null ? 0 : 1);
            if (m.above != null) { out.write(m.kinds); pixels(out, m.above); }
            return out.toByteArray();
        } else if (e.kind == PLACE) {
            Place p = e.place;
            varint(out, p.back <= index ? p.back : 0); out.write((p.drop ? 1 : 0) | (p.cut ? 2 : 0));
            int32(out, Float.floatToIntBits(p.centerX)); int32(out, Float.floatToIntBits(p.centerY)); int32(out, Float.floatToIntBits(p.scale));
            int64(out, Double.doubleToLongBits(p.angle));
            return out.toByteArray();
        }
        varint(out, e.size());
        int px = 0, py = 0;
        for (int i = 0; i < e.size(); i++) {
            varint(out, zigzag(e.x[i] - px)); varint(out, zigzag(e.y[i] - py)); px = e.x[i]; py = e.y[i];
            varint(out, e.dt[i]); out.write(e.pressure[i]); out.write(e.tiltX[i]); out.write(e.tiltY[i]);
        }
        return out.toByteArray();
    }
    /** The log {@code bytes} keep; null when they don't read (a version or a kind this reader doesn't know too: the page opens without a replay). */
    static StrokeLog decode(byte[] bytes) {
        try {
            int[] at = {0};
            if (bytes.length < 2) return null;
            int version = bytes[at[0]++];
            if (version != 1 && version != VERSION) return null;
            int count = (int) read(bytes, at, MOST_SAMPLES), total = 0, weight = 0;
            List<Entry> list = new ArrayList<>();
            for (int k = 0; k < count; k++) {
                int kind = version == 1 ? PEN : bytes[at[0]++], ticks = version == 1 ? 0 : (int) read(bytes, at, MOST_TICKS);
                String code = null, holder = null;
                double px = 0, dark = 0;
                int kinds = 0;
                if (kind == PEN || kind == SMUDGE || kind == FINGER) {
                    code = text(bytes, at);
                    if (version != 1) { holder = text(bytes, at); if (holder.isEmpty()) holder = null; }
                    if (version == 1) { px = Float.intBitsToFloat(int32(bytes, at)); dark = Float.intBitsToFloat(int32(bytes, at)); }
                    else { px = Double.longBitsToDouble(int64(bytes, at)); dark = Double.longBitsToDouble(int64(bytes, at)); }
                    if (!(px > 0 && px < 4096) || !(dark >= 0 && dark <= 1)) return null;
                } else if (kind == ERASE || kind == ERASE_FINGER) {
                    px = Double.longBitsToDouble(int64(bytes, at)); kinds = (int) read(bytes, at, ToneDocument.KIND_ALL);
                    if (!(px > 0 && px < 4096)) return null;
                } else if (kind == PLACE) {
                    int back = (int) read(bytes, at, k);
                    if (at[0] >= bytes.length) return null;
                    int flags = bytes[at[0]++];
                    float cx = Float.intBitsToFloat(int32(bytes, at)), cy = Float.intBitsToFloat(int32(bytes, at)), scale = Float.intBitsToFloat(int32(bytes, at));
                    double angle = Double.longBitsToDouble(int64(bytes, at));
                    if ((flags & ~3) != 0 || !Float.isFinite(cx) || !Float.isFinite(cy) || !(scale > 0 && scale < 4096) || !Double.isFinite(angle)) return null;
                    list.add(Entry.bare(PLACE, 0, new Place(back, (flags & 1) != 0, (flags & 2) != 0, cx, cy, scale, angle)).after(ticks, true));
                    continue;
                } else if (kind == SHAPE && version != 1) {
                    code = text(bytes, at);
                    ToolSettings.Shape shape = ToolSettings.Shape.valueOf(text(bytes, at));
                    if (at[0] >= bytes.length) return null;
                    int filled = bytes[at[0]++], width = (int) read(bytes, at, 4096), shade = (int) read(bytes, at, 255);
                    float[] f = new float[4];
                    for (int i = 0; i < 4; i++) if (!Float.isFinite(f[i] = Float.intBitsToFloat(int32(bytes, at)))) return null;
                    if ((filled & ~1) != 0) return null;
                    list.add(Entry.shape(0, code, new Shape(shape, filled == 1, width, shade, f[0], f[1], f[2], f[3])).after(ticks, true));
                    continue;
                } else if (kind == PICTURE && version != 1) {
                    int layer = (int) read(bytes, at, ToneDocument.MAX_LAYERS - 1), x = unzigzag(read(bytes, at, 0xffffffffL)), y = unzigzag(read(bytes, at, 0xffffffffL));
                    Pixels p = pixels(bytes, at, 1);
                    if (p == null) return null;
                    Entry e = Entry.picture(0, new Picture(x, y, p.width, p.height, layer, p)).after(ticks, true);
                    list.add(e); weight += e.weight();
                    continue;
                } else if (kind == MERGE && version != 1) {
                    int active = (int) read(bytes, at, ToneDocument.MAX_LAYERS - 1), n = (int) read(bytes, at, ToneDocument.MAX_LAYERS);
                    if (n < 1 || active >= n || at[0] + n + 1 > bytes.length) return null;
                    byte[] layers = java.util.Arrays.copyOfRange(bytes, at[0], at[0] + n); at[0] += n;
                    for (byte b : layers) if ((b & 127) > 100) return null;
                    int above = bytes[at[0]++], aboveKinds = 0;
                    Pixels p = null;
                    if (above == 1) {
                        if (at[0] >= bytes.length) return null;
                        aboveKinds = bytes[at[0]++] & 255;
                        if ((p = pixels(bytes, at, 2)) == null) return null;
                    } else if (above != 0) return null;
                    Entry e = Entry.merge(0, new Merge(active, layers, aboveKinds, p)).after(ticks, true);
                    list.add(e); weight += e.weight();
                    continue;
                } else if (kind != LIFT) return null;
                int n = (int) read(bytes, at, MOST_SAMPLES - total);
                if (n < (kind == LIFT ? 3 : 1)) return null;
                total += n; weight += n;
                int[] x = new int[n], y = new int[n], dt = new int[n];
                byte[] p = new byte[n], tx = new byte[n], ty = new byte[n];
                int cx = 0, cy = 0;
                for (int i = 0; i < n; i++) {
                    cx += unzigzag(read(bytes, at, 1 << 20)); cy += unzigzag(read(bytes, at, 1 << 20));
                    x[i] = cx; y[i] = cy; dt[i] = (int) read(bytes, at, MOST_PAUSE);
                    if (at[0] + 3 > bytes.length) return null;
                    p[i] = bytes[at[0]++]; tx[i] = bytes[at[0]++]; ty[i] = bytes[at[0]++];
                }
                list.add(new Entry(kind, 0, code, holder, ticks, px, dark, kinds, x, y, dt, p, tx, ty, null));
            }
            return at[0] == bytes.length && weight <= MOST_SAMPLES ? (list.isEmpty() ? NONE : new StrokeLog(list, weight)) : null;
        } catch (IOException | RuntimeException bad) { return null; }
    }

    private static void text(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        varint(out, bytes.length); out.write(bytes, 0, bytes.length);
    }
    private static void pixels(ByteArrayOutputStream out, Pixels p) {
        varint(out, p.width); varint(out, p.height); varint(out, p.step); varint(out, p.planes); varint(out, p.packed.length); out.write(p.packed, 0, p.packed.length);
    }
    /** Pixels of {@code planes} planes; null when they don't read (their deflated bytes are read only when replayed). */
    private static Pixels pixels(byte[] bytes, int[] at, int planes) throws IOException {
        int w = (int) read(bytes, at, 1 << 14), h = (int) read(bytes, at, 1 << 14), step = (int) read(bytes, at, 1 << 14), n = (int) read(bytes, at, 2), length = (int) read(bytes, at, MOST_PIXELS);
        if (w < 1 || h < 1 || Integer.bitCount(step) != 1 || n != planes || at[0] + length > bytes.length) return null;
        byte[] packed = java.util.Arrays.copyOfRange(bytes, at[0], at[0] + length); at[0] += length;
        return new Pixels(w, h, step, n, packed);
    }

    private static String text(byte[] bytes, int[] at) throws IOException {
        int length = (int) read(bytes, at, 4096);
        if (at[0] + length > bytes.length) throw new IOException("short");
        String text = new String(bytes, at[0], length, StandardCharsets.UTF_8); at[0] += length;
        return text;
    }
    private static int zigzag(int v) { return (v << 1) ^ (v >> 31); }
    private static int unzigzag(long v) { return (int) (v >>> 1) ^ -(int) (v & 1); }
    private static void varint(ByteArrayOutputStream out, int v) {
        while ((v & ~0x7f) != 0) { out.write((v & 0x7f) | 0x80); v >>>= 7; }
        out.write(v);
    }
    private static long read(byte[] b, int[] at, long most) throws IOException {
        long v = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            if (at[0] >= b.length) throw new IOException("short");
            int c = b[at[0]++] & 255;
            v |= (long) (c & 0x7f) << shift;
            if ((c & 0x80) == 0) { if (v > most) throw new IOException("big"); return v; }
        }
        throw new IOException("long");
    }
    private static void int32(ByteArrayOutputStream out, int v) { for (int s = 24; s >= 0; s -= 8) out.write(v >>> s); }
    private static void int64(ByteArrayOutputStream out, long v) { int32(out, (int) (v >>> 32)); int32(out, (int) v); }
    private static long int64(byte[] b, int[] at) throws IOException { return (long) int32(b, at) << 32 | int32(b, at) & 0xffffffffL; }
    private static int int32(byte[] b, int[] at) throws IOException {
        if (at[0] + 4 > b.length) throw new IOException("short");
        int v = (b[at[0]] & 255) << 24 | (b[at[0] + 1] & 255) << 16 | (b[at[0] + 2] & 255) << 8 | (b[at[0] + 3] & 255);
        at[0] += 4; return v;
    }

    // ---- Replay: the strokes fed back in chunks of pen time ----

    /** Where a {@link Player} puts what it plays. {@code time} is ms from the stroke's start, plus 1000 (never 0). */
    interface Sink {
        void begin(Entry e);
        void sample(Entry e, int i, long time);
        void end(Entry e);
    }

    /**
     * Plays strokes one after another in steps: each {@link #step} feeds the samples of the next {@code ms} of pen time
     * (a stroke's pauses count, the time between strokes doesn't), so a replay shows a chunk at a time, as e-ink can.
     */
    static final class Player {
        private final List<Entry> list;
        private int index, at;
        private long clock;
        Player(List<Entry> strokes) { list = strokes; }
        boolean done() { return index >= list.size(); }
        /** The strokes not played yet, a started one counted. */
        int left() { return list.size() - index; }
        void step(long ms, Sink sink) {
            long budget = ms;
            while (!done() && budget > 0) {
                Entry e = list.get(index);
                if (at == 0) { clock = 1000; sink.begin(e); }
                while (at < e.size() && budget > 0) { clock += e.dt[at]; budget -= e.dt[at]; sink.sample(e, at, clock); at++; }
                if (at >= e.size()) { sink.end(e); index++; at = 0; }
            }
        }
    }
    /**
     * Whether a touch on the page ends a replay: a {@code press} (a pointer landing) of the {@code pen}, or of a finger no
     * broader than a palm ({@link FingerTool#PALM_DP}; {@code contactDp} across). A hand resting on the page, its moves and
     * lifts, never does: it was there as the replay began (2026-10-25: it ended every replay at once).
     */
    static boolean endsReplay(boolean press, boolean pen, float contactDp) { return press && (pen || contactDp <= FingerTool.PALM_DP); }
}
