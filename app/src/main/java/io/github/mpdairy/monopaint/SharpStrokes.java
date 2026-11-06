package io.github.mpdairy.monopaint;

/**
 * Sharp dry brushes (docs/superpowers/specs/2026-11-04-vector-dry-brushes-design.md): which strokes are kept to be drawn
 * again finer when zoomed, and that drawing. A kept stroke is its Replay log entry ({@link StrokeLog.Entry#plane}), fed
 * to the stroke as kept ({@link StrokeLog.Recorder#kept}), so drawn again from the entry at 1 to a pixel it lays what it
 * laid, byte for byte; on a {@link FineGrid} it lays the same marks at the grid's points ({@link LineStamper}'s dabs on
 * the page, laid at the grid; the paper read between its pixels, {@link StrokePaper#on}). Its pixels on the page stay
 * the truth: {@link SharpView} shows the finer drawing only where drawing it again at 1 to a pixel gives them.
 *
 * <p>Step 1 (the owner, 2026-11-04, after the pencil pilot: "looks good!"): every brush the spec's rule takes
 * ({@link #qualifies}), the Basic tools and any brush of Brush Lab's alike, as far as the drawing on a grid covers its
 * tip and body so far ({@link #drawn}): the rest stay pixels. Pure, checked on the desktop ({@code SharpChecks}).
 */
final class SharpStrokes {
    private SharpStrokes() {}

    /** Whether a stroke of {@code b} at {@code px} and {@code darkness} is kept sharp: the spec's rule takes it and the grid draws its marks. */
    static boolean takes(ModularBrush b, double px, double darkness) {
        if (b == null || !qualifies(b)) return false;
        ModularBrush laid = QuickStroke.laidAs(b);
        return drawn(laid, ResolvedBrush.of(laid, px, darkness));
    }
    /**
     * A host's stroke of {@code b} at {@code px} and {@code darkness}, recorded by {@code rec}, kept sharp ({@link #takes}): fed
     * as kept, and a Stamp's starting ink kept as its stroke reads it from {@code session} ({@link ModularStroke#inkedBy}).
     */
    static void keep(StrokeLog.Recorder rec, ModularBrush b, double px, double darkness, WatercolorSession session) {
        rec.keeps = true;
        String holder = ModularStroke.inkedBy(ResolvedBrush.of(b, px, darkness), session);
        rec.stampInk = holder != null ? session.held.stampInk(holder) : null;
    }
    /** Whether a brush's strokes stay sharp when zoomed at its usual size and black (Brush Studio's words: {@code WebEngine}). */
    static boolean staysSharp(ModularBrush b) { return takes(b, 6, 1); }

    /**
     * The spec's rule (section 1): a brush whose marks lie on a pair's ink half, darken as laid and stay as laid: Ink,
     * Charcoal, Graphite, Crayon or Screentone, a screened print too (the owner, 2026-11-05: "screentone dots are my top
     * thing"), not a wet wash, Water-Soluble or Resist; Affects Page Off; Instant, or Quick without a body; After Off; its own Blend, no Opaque or Thick Paint; no page-tilt dials;
     * not a Brayer that picks up. Light over dark and charcoal's contact rule are left to the match ({@link SharpView}).
     */
    static boolean qualifies(ModularBrush b) {
        if (VectorInk.is(b) || !ModularSizes.inks(b) || ResolvedBrush.washes(b) || ResolvedBrush.soluble(b)) return false;
        PartChoice m = b.choice(BrushPart.MEDIUM), life = b.choice(BrushPart.LIFE);
        if (m == PartChoice.RESIST || b.choice(BrushPart.CARRY) != PartChoice.OFF || b.later()) return false;
        if (life != PartChoice.NONE && life != PartChoice.FAST_WET) return false;
        ResolvedBrush r = ResolvedBrush.of(b, 10, 1);
        if (life == PartChoice.FAST_WET && r.body != null) return false;
        if (r.blendMode != ResolvedBrush.BlendMode.OWN || r.cover > 0 || r.look.impasto > 0) return false;
        if (ModularStroke.readsTilt(r)) return false;
        return !(r.lineTip == ResolvedBrush.LineTip.ROLLER && r.pattern != null && r.pattern.pick > 0);
    }

    /**
     * Whether {@link #draw} draws {@code r}'s strokes ({@code b} laid, {@link QuickStroke#laidAs}) as the page's stroke laid
     * them: a tip the grid lays ({@link #tipOnGrid}), a body only of a tip it lays too, worked out on every pixel (no
     * lattice); nothing the stroke reads off the page or a session (smudge, a fill's shape, Paint Load, a grey gradient,
     * a flick's or fling's drops timed past the samples, the page's tilt). A screen inks its threshold at each point
     * ({@link PrintScreen#ink}), but not Misregister's second pass (offset in page pixels on the stroke's buffers), nor
     * Pixels' and Stitches' cells (each read at its centre, in page pixels).
     */
    static boolean drawn(ModularBrush b, ResolvedBrush r) {
        return r.line && tipOnGrid(r) && (r.body == null || bodyOnGrid(r)) && (r.life == ResolvedBrush.Life.NONE || r.life == ResolvedBrush.Life.WET)
                && FlickFling.of(r) == null && r.carry == null && r.loaded == null && !r.staysIn() && r.flt == null
                && r.fade == null && r.halo == null && r.wet == null && (r.screen == null || !r.screen.misregisters() && !r.screen.cells) && !r.resist
                && r.blendMode == ResolvedBrush.BlendMode.OWN && !(r.cover > 0) && !(r.look.impasto > 0) && !ModularStroke.readsTilt(r)
                && ModularStroke.reach(r) <= ModularStroke.latticeFrom(b) && !GreyGradient.on(b);
    }
    /**
     * The line tips whose stampers lay on a {@link FineGrid}; a Stamp's ink carried on from its holder's last stroke is
     * kept with the stroke ({@link StrokeLog.Entry#ink}); the bristle swarm walks the page and lays its hairs on the grid. Not
     * yet: the hake, the Brayer (it reads the page).
     */
    static boolean tipOnGrid(ResolvedBrush r) {
        ResolvedBrush.LineTip t = r.lineTip;
        return t == ResolvedBrush.LineTip.NIB || t == ResolvedBrush.LineTip.STIPPLE || t == ResolvedBrush.LineTip.HATCH || t == ResolvedBrush.LineTip.SPIRO
                || t == ResolvedBrush.LineTip.RULING || t == ResolvedBrush.LineTip.TWIG || t == ResolvedBrush.LineTip.FAN || t == ResolvedBrush.LineTip.STAMP
                || t == ResolvedBrush.LineTip.BRISTLES;
    }
    /** The bodies whose stampers lay on a {@link FineGrid}: a round or nib-shaped body of dabs ({@link BodyStamper}). */
    static boolean bodyOnGrid(ResolvedBrush r) { return r.bodyTip == ResolvedBrush.BodyTip.BODY && !r.body.flatHead; }

    /** A kept stroke's brush as its stroke laid it on {@code surface}, and resolved at its size and grey; null when its code doesn't read. A kept shape's: {@link #SHAPE}. */
    static final class Laid {
        final ModularBrush brush; final ResolvedBrush r;
        Laid(ModularBrush brush, ResolvedBrush r) { this.brush = brush; this.r = r; }
        /** A kept shape ({@link StrokeLog#SHAPE}): no brush, its spans laid in its grey ({@link #lay}). */
        static final Laid SHAPE = new Laid(null, null);
        static Laid of(StrokeLog.Entry e, BrushTexture surface) {
            if (e.kind == StrokeLog.SHAPE) return SHAPE;
            ModularBrush b = e.code == null ? null : ModularCode.decode(e.code);
            if (b == null) return null;
            b = QuickStroke.laidAs(b.onSurface(surface));
            ResolvedBrush r = ResolvedBrush.of(b, e.px, e.darkness);
            return drawn(b, r) ? new Laid(b, r) : null;
        }
    }

    /** Where kept stroke {@code e} can lay anything, page px {left, top, right, bottom}. */
    static int[] box(StrokeLog.Entry e, ResolvedBrush r) {
        if (e.kind == StrokeLog.SHAPE) {
            // As far from its first corner as a circle's radius or a square's side reaches, and its outline past that.
            StrokeLog.Shape s = e.shape();
            double dx = Math.abs(s.x1 - s.x0), dy = Math.abs(s.y1 - s.y0), reach = Math.hypot(dx, dy) + s.width + 2;
            return new int[]{(int) Math.floor(s.x0 - reach), (int) Math.floor(s.y0 - reach), (int) Math.ceil(s.x0 + reach + 1), (int) Math.ceil(s.y0 + reach + 1)};
        }
        double reach = Math.max(ModularStroke.reach(r), r.inkSize) * 1.5 + 4;
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int i = 0; i < e.size(); i++) { x0 = Math.min(x0, e.px(i)); x1 = Math.max(x1, e.px(i)); y0 = Math.min(y0, e.py(i)); y1 = Math.max(y1, e.py(i)); }
        return new int[]{(int) Math.floor(x0 - reach), (int) Math.floor(y0 - reach), (int) Math.ceil(x1 + reach + 1), (int) Math.ceil(y1 + reach + 1)};
    }

    /**
     * Kept stroke {@code e} drawn again onto {@code page}, {@code grid}'s points over {@code paper} (the page's paper, or
     * already on the grid): the page's own stroke ({@link ModularStroke#onGrid}, through its spring when it has one) fed
     * its samples as a replay feeds them, over what {@code page} holds. Nothing reads the page under it but its plane.
     * {@code cull}: how far from the grid a step's marks can land (page px, {@link LineStamper.Meta#cull}), NaN unknown.
     * Returns the stroke's widest reach, for {@code cull} next time ({@link #cull}).
     */
    static int draw(StrokeLog.Entry e, Laid laid, StrokePaper paper, FineGrid grid, GridPage page, double cull) {
        if (e.kind == StrokeLog.SHAPE) { lay(e.shape(), grid, page); return 0; }
        ModularStroke stroke = ModularStroke.onGrid(grid, page, paper, laid.brush, e.px, e.darkness);
        stroke.cull(cull);
        stroke.stampInk(e.ink);
        DrawingStroke pen = SteadyHand.of(laid.brush, stroke);
        pen.zoom(e.zoom);
        page.begin();
        long clock = 1000;
        for (int i = 0; i < e.size(); i++) { clock += e.dt[i]; pen.sample(e.px(i), e.py(i), e.pressure(i), e.tiltX(i), e.tiltY(i), clock); }
        pen.finish();
        return stroke.widest();
    }
    /**
     * Kept shape {@code s} (Shapes, {@link ShapeStroke#lay}: its spans opaque in its grey) laid onto {@code page} at
     * {@code grid}'s points: at 1 to a pixel the page's own spans, to the byte; finer, the same shape rastered at the
     * points ({@link ShapeGeometry#on}), its edges a point wide.
     */
    static void lay(StrokeLog.Shape s, FineGrid grid, GridPage page) {
        int[] spans;
        int dx = 0, dy = 0;
        if (grid.k == 1) { spans = s.spans(grid.pageWidth, grid.pageHeight); dx = grid.ox; dy = grid.oy; }
        else ShapeGeometry.on(grid, s.settings(), s.x0, s.y0).raster(s.x1, s.y1, spans = new int[4 * grid.height]);
        for (int y = 0; y < grid.height; y++) {
            int at = (y + dy) * 4;
            if (at < 0 || at >= spans.length) continue;
            for (int part = 0; part < 4; part += 2) page.paintSpan(spans[at + part] - dx, spans[at + part + 1] - dx, y, s.shade);
        }
    }
    /** How far from the clip a step of a stroke whose widest reach is {@code widest} can lay anything: its dabs, and its dust (to 2.32 of a nib away). */
    static double cull(int widest) { return widest < 0 ? Double.NaN : 2.5 * widest + 4; }

    /**
     * What a kept stroke is drawn again onto ({@link #draw}): a plane's tones over white, a grid's points of it, as
     * {@link ToneDocument} keeps a plane that holds darkening marks alone (each tone over white is the tone, its coverage
     * {@code 255 − tone}). Tones written a stroke at a time; what the stroke found is what was there when it began. A kept
     * shape lays opaque grey ({@link #paintSpan}): from then each point's coverage is kept too ({@link #alpha}).
     */
    static final class GridPage implements ModularCompose.Page {
        final int width, height;
        /** Tones over white, row by row; 255 where nothing lies. */
        final int[] tones;
        private final int[] found;
        /** The tiles ({@link FloatTiles#SIDE} square) the stroke has written, as {@link ToneDocument#touched} tells a gesture's. */
        private final boolean[] written;
        private final int columns;
        GridPage(int width, int height) {
            this.width = width; this.height = height;
            tones = new int[width * height]; found = new int[width * height];
            columns = (width + FloatTiles.MASK) >> FloatTiles.SHIFT;
            written = new boolean[columns * ((height + FloatTiles.MASK) >> FloatTiles.SHIFT)];
            java.util.Arrays.fill(tones, 255);
        }
        /** Each point's coverage once a shape lays opaque grey ({@link #paintSpan}), and as the stroke found it; null till then (255 − tone). */
        private int[] alpha, foundAlpha;
        /** Point {@code i}'s coverage, 0 to 255. */
        int alpha(int i) { return alpha == null ? 255 - tones[i] : alpha[i]; }
        /** Point {@code i}'s raw tone as the plane would keep it ({@link ToneDocument#rawTone}): 0, ink, where its coverage is its darkness. */
        int raw(int i) { return alpha == null ? 0 : ToneDocument.rawTone(tones[i], alpha[i]); }
        /** {@link ToneDocument#paintSpan}: points {@code x .. end − 1} of row {@code y} opaque {@code gray}. */
        void paintSpan(int x, int end, int y, int gray) {
            x = Math.max(0, x); end = Math.min(width, end);
            if (y < 0 || y >= height || x >= end) return;
            if (alpha == null) {
                alpha = new int[tones.length]; foundAlpha = new int[tones.length];
                for (int i = 0; i < tones.length; i++) { alpha[i] = 255 - tones[i]; foundAlpha[i] = 255 - found[i]; }
            }
            java.util.Arrays.fill(tones, y * width + x, y * width + end, gray);
            java.util.Arrays.fill(alpha, y * width + x, y * width + end, 255);
        }
        /** A stroke begins: what it finds is what is there now. */
        void begin() {
            System.arraycopy(tones, 0, found, 0, tones.length); java.util.Arrays.fill(written, false);
            if (alpha != null) System.arraycopy(alpha, 0, foundAlpha, 0, alpha.length);
            if (prints != null) System.arraycopy(prints, 0, printsFound, 0, prints.length);
        }
        @Override public int width() { return width; }
        @Override public int height() { return height; }
        @Override public void strokeBaseTones(int x, int y, int w, int h, int[] out) {
            for (int r = 0; r < h; r++) System.arraycopy(found, (y + r) * width + x, out, r * w, w);
        }
        @Override public void strokeBasePixels(int x, int y, int w, int h, int[] tones, int[] coverages) {
            strokeBaseTones(x, y, w, h, tones);
            for (int r = 0; r < h; r++) for (int c = 0; c < w; c++) coverages[r * w + c] = foundAlpha == null ? 255 - tones[r * w + c] : foundAlpha[(y + r) * width + x + c];
        }
        @Override public boolean touched(int x, int y) { return written[(y >> FloatTiles.SHIFT) * columns + (x >> FloatTiles.SHIFT)]; }
        @Override public boolean differs(int x, int y, int was) { return tones[y * width + x] != was; }
        @Override public void restoreSpan(int x, int end, int y) {
            System.arraycopy(found, y * width + x, tones, y * width + x, end - x);
            if (alpha != null) System.arraycopy(foundAlpha, y * width + x, alpha, y * width + x, end - x);
        }
        @Override public void setTones(int x, int y, int w, int h, int[] grays, int[] owns) {
            for (int r = 0; r < h; r++) for (int c = 0; c < w; c++) {
                int g = grays[r * w + c];
                if (g >= 0) { int i = (y + r) * width + x + c; tones[i] = g; if (alpha != null) alpha[i] = Math.max(alpha[i], 255 - g); written[((y + r) >> FloatTiles.SHIFT) * columns + ((x + c) >> FloatTiles.SHIFT)] = true; }
            }
        }
        /**
         * A screened stroke's dots (Screentone's tone 1 at coverage 254, a print's black): tones over white, as
         * {@link #setTones}. Any coverage past a tone's own (raw paint lighter than black) isn't kept here: the plane's
         * pixel differs, and the match shows it.
         */
        @Override public void coverTones(int x, int y, int w, int h, int[] grays, int[] coverages, int[] owns) { setTones(x, y, w, h, grays, owns); }
        @Override public void smearTones(int x, int y, int w, int h, int[] grays, int[] coverages, int[] owns) { throw new UnsupportedOperationException("A kept stroke only darkens"); }
        @Override public void openBands() { }
        @Override public void closeBands() { }
        /** Print Shading's print tones ({@link ResolvedBrush#prints}), 255 where none, as the stroke found them and as written; made on the first write. */
        private int[] prints, printsFound;
        @Override public void strokeBasePrints(int x, int y, int w, int[] out) {
            if (printsFound == null) java.util.Arrays.fill(out, 0, w, 255); else System.arraycopy(printsFound, y * width + x, out, 0, w);
        }
        @Override public void setPrints(int x, int y, int w, int[] p) {
            if (prints == null) { prints = new int[width * height]; java.util.Arrays.fill(prints, 255); printsFound = prints.clone(); }
            for (int i = 0; i < w; i++) if (p[i] >= 0) prints[y * width + x + i] = p[i];
        }
    }
}
