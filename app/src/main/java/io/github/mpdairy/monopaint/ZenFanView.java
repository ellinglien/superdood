package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Zen Mode's fan on the drawing screen ({@link ZenFan}, the owner's sketch and mockup, 2026-11-04), opened by a tap on
 * Zen's corner ({@link PaintActivity#zenTapped}): over the page, from that corner, its own view in the screen's stage,
 * the size of the fan alone. Paper under it; the size wedge white and the grey wedge in the toolbar strip's greys in
 * the panel's dots, each outlined, its marker beside its edge; the ring of the page bar's previews ({@link
 * PageTurner#drawRing}), each turned to point outward, the page drawn on with its tab, a tap going to it and a hold
 * opening its Page Settings as on the bar; the thin arc, and outside it the column's tools in its
 * order, each its row's picture ({@link InkWashBar#rowPicture}) turned to point outward, its butt on the arc, the one in
 * use with a bar just past its tip. A tap on a tool picks it as its row does, a long press holds it as its row does
 * ({@link InkWashBar#hold}: their menus open beside the fan), and the fan stays open, drawing on the page too; a tap or drag on a wedge sets the
 * size or grey on the lift, as the column's picker box does ({@link SwatchBar#setSize}, {@link SwatchBar#setGrey}), its
 * marker following the pen through the fast display. A touch outside it goes on to the page; only Zen's circle closes it.
 * Paper under its row and Zoom, the column's and the bar's buttons lent while it is open ({@link ZenFan#cell}). Drawn by
 * Android in the column's partial grey ({@link InkWashBar#partialGrey(View...)}), no flash of its own.
 */
@SuppressLint("ViewConstructor")
final class ZenFanView extends View {
    /** The strip's words and the brush line's in the fan, grey (the owner, 2026-11-07: "a lighter grey for the text instead of full black? maybe 50%"). */
    static final int WORDS = Theme.GREY_600;
    /** Ink to {@link #WORDS}'s grey, paper kept: each channel {@code c} to {@code c * (1 - g) + 255 g}, g the grey's lightness. */
    private static final Paint GREY = new Paint();
    static {
        float g = (WORDS & 0xff) / 255f, k = 1 - g, o = 255 * g;
        GREY.setColorFilter(new android.graphics.ColorMatrixColorFilter(new float[]{k, 0, 0, 0, o, 0, k, 0, 0, o, 0, 0, k, 0, o, 0, 0, 0, 1, 0}));
    }
    /**
     * {@code draw} into {@code canvas}, through the grey when {@code greyed}: the fan's row's buttons, so their icons are
     * the strip's and the line's grey (the owner, 2026-11-07: "make the icons in the tray grey as well"). Inside the
     * view's own draw, so the panel's updates (which capture the view) show the grey too.
     */
    static void greyed(Canvas canvas, boolean greyed, java.util.function.Consumer<Canvas> draw) {
        if (!greyed) { draw.accept(canvas); return; }
        int saved = canvas.saveLayer(null, GREY);
        try { draw.accept(canvas); } finally { canvas.restoreToCount(saved); }
    }
    private final PaintActivity app;
    private ZenFan fan;
    /** The fan's bounds on the stage, dp ({@link ZenFan#bounds}). */
    private float[] bounds;
    /** The outlines, smooth; the markers and the selected tool's bar ink alone, no anti-aliasing, so their changes go through the panel's binary update. */
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG), marks = new Paint(), paper = new Paint();
    private final Path sizeWedge = new Path(), greyWedge = new Path();
    /** The grey wedge's dots, px, from the fan's point (unmirrored); made once. */
    private Bitmap greys;
    /** What the pen went down on ({@link ZenFan#hit}), the ring's slot if a preview, and the size's or grey's place while it drags (NaN: none). */
    private int item = ZenFan.NONE, slot = -1;
    private double dragSize = Double.NaN, dragGrey = Double.NaN;
    private boolean held;
    /** A long press: a tool held as its row is; on Zen's circle, as the circle's hold (Zen Mode left). */
    private final Runnable hold = () -> {
        held = true;
        if (item == ZenFan.LEAVE) app().zenHeld();
        else if (item == ZenFan.PAGES) turner().ringHold(turner().ringPlace(slot, ZenFan.previews(), this.ringStart));
        else app().inkWashBar.hold(item);
    };
    private PageTurner turner() { return app.inkWashBar.turner; }
    private PaintActivity app() { return app; }

    ZenFanView(PaintActivity app) {
        super(app);
        this.app = app;
        setContentDescription(DrawLayout.ZEN_TOOLS);
        paper.setColor(Theme.PAPER);
        marks.setColor(Theme.INK);
        ink.setColor(Theme.INK); ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(ZenFan.STROKE);
        // The wedges, from the point (0, 0), out along +x (the foot) and up along -y (the side edge): the size up the side
        // edge to the gap, the grey from the gap to its lower edge, each closed along the gap's side back to its corner.
        float c = ZenFan.wedgeStart(), g = ZenFan.greyStart(), w = ZenFan.WEDGE;
        double low = Math.toRadians(ZenFan.LOW);
        RectF circle = new RectF(-w, -w, w, w);
        float meet = angle(ZenFan.wedgeEnd()), greyMeet = angle(ZenFan.greyEnd()), lowAt = (float) ZenFan.LOW - 90;
        sizeWedge.moveTo(0, -c); sizeWedge.lineTo(0, -w); sizeWedge.arcTo(circle, -90, meet + 90); sizeWedge.close();
        greyWedge.moveTo((float) (g * Math.sin(low)), (float) (-g * Math.cos(low))); greyWedge.lineTo((float) (w * Math.sin(low)), (float) (-w * Math.cos(low)));
        greyWedge.arcTo(circle, lowAt, greyMeet - lowAt); greyWedge.close();
    }

    /**
     * The fan and what lies over it on the stage (its lent buttons, the brush line, Zen's button and dot), as the panel
     * shows them: a panel update's capture of the fan ({@code SelectionFeedback.capture}), whose patches over the row
     * otherwise put the fan's paper over the buttons (the owner, 2026-11-07: "some of the icons (especially the layers)
     * get grey... lighter and lighter, and sometimes bits of them disappear").
     */
    void drawWithOver(Canvas canvas) {
        draw(canvas);
        android.view.ViewGroup stage = (android.view.ViewGroup) getParent();
        if (stage == null) return;
        for (int i = stage.indexOfChild(this) + 1; i < stage.getChildCount(); i++) {
            View over = stage.getChildAt(i);
            if (over.getVisibility() != VISIBLE || over == app.notebooksView() || !android.graphics.Rect.intersects(new Rect(getLeft(), getTop(), getRight(), getBottom()), new Rect(over.getLeft(), over.getTop(), over.getRight(), over.getBottom()))) continue;
            int saved = canvas.save();
            canvas.translate(over.getLeft() - getLeft(), over.getTop() - getTop());
            canvas.clipRect(0, 0, over.getWidth(), over.getHeight());
            over.draw(canvas);
            canvas.restoreToCount(saved);
        }
    }
    /** A point {out, up} from the fan's point, as a canvas angle, degrees (0 along the foot, -90 up the side edge). */
    private static float angle(float[] p) { return (float) Math.toDegrees(Math.atan2(-p[1], p[0])); }

    /** Laid over the stage, {@code width × height} px, for Zen's icon's middle at ({@code x}, {@code y}) px, {@code slots} slots shown. */
    void place(float x, float y, int slots, boolean right, int width, int height) {
        float d = density();
        fan = new ZenFan(x / d, y / d, slots, right, width / d, height / d);
        stillKey = null; ringStart = -1;
        bounds = fan.bounds();
        android.widget.FrameLayout.LayoutParams at = new android.widget.FrameLayout.LayoutParams(Math.round((bounds[2] - bounds[0]) * d), Math.round((bounds[3] - bounds[1]) * d));
        at.leftMargin = Math.round(bounds[0] * d); at.topMargin = Math.round(bounds[1] * d);
        setLayoutParams(at);
    }
    /** Its round paper on the stage, px: {middle x, middle y, radius} ({@link ZenFan#paper}). */
    float[] paper() { float d = density(); return new float[]{fan.px * d, fan.py * d, ZenFan.paper() * d}; }
    /** What it covers on the stage, px: the fan, its paper and its line. */
    RectF area() { return px(bounds); }
    /** What the fast display keeps off on the stage, px ({@link ZenFan#mask}): the stepped disc and the line. */
    java.util.List<RectF> mask() {
        java.util.List<RectF> mask = new java.util.ArrayList<>();
        for (float[] box : fan.mask()) mask.add(px(box));
        return mask;
    }
    /** Strip cell {@code cell}'s box on the stage, px ({@link ZenFan#cell}), where its lent button goes. */
    RectF cell(int cell) { return px(fan.cell(cell)); }
    /** The dot's square over Zen's circle on the stage, px ({@link ZenFan#dot}). */
    RectF dot() { return px(fan.dot()); }
    private RectF px(float[] box) {
        float d = density();
        return new RectF(box[0] * d, box[1] * d, box[2] * d, box[3] * d);
    }

    private float density() { return getResources().getDisplayMetrics().density; }
    private int selected() { return app.inkWash.selected; }

    /**
     * What changes only with the tools (their pictures, which are on, the grey wedge coming or going with the eraser),
     * drawn once into {@link #still} and laid down whole after: the paper, the wedges, the tools' pictures and the arc. A
     * redraw, and a panel update's two captures of the fan, then draw only the marks, the ring and the strip (the owner,
     * 2026-11-07: "find the best way to speed up and clean up the fan").
     */
    private Bitmap still;
    private java.util.List<Object> stillKey;
    private java.util.List<Object> stillKey(boolean hasGreys) {
        java.util.List<Object> key = new java.util.ArrayList<>();
        key.add(hasGreys); key.add(app.inkWashBar.mirrored());
        for (int i = 0; i < fan.items(); i++) { int t = fan.tool(i); key.add(app.inkWashBar.rowPicture(t)); key.add(app.inkWashBar.enabled(t)); }
        return key;
    }
    private void paintStill(Canvas canvas, boolean hasGreys, float d) {
        // Paper under the lent buttons, which have none of their own.
        for (int i = 0; i < ZenFan.CELLS; i++) {
            float[] c = fan.cell(i);
            canvas.drawRect((c[0] - bounds[0]) * d, (c[1] - bounds[1]) * d, (c[2] - bounds[0]) * d, (c[3] - bounds[1]) * d, paper);
        }
        // Paper under the page strip, its top end a little past the arc's paper.
        float[] st = fan.strip();
        canvas.drawRect((st[0] - bounds[0]) * d, (st[1] - bounds[1]) * d, (st[2] - bounds[0]) * d, (st[3] - bounds[1]) * d, paper);
        frame(canvas, d);
        // Paper over the page round the fan's point out past its tools (ZenFan.paper): the drawing hidden under the fan while it is open.
        canvas.drawCircle(0, 0, ZenFan.paper(), paper);
        if (hasGreys) {
            if (greys == null) greys = greys(d);
            canvas.save(); canvas.clipPath(greyWedge);
            canvas.translate(0, -ZenFan.WEDGE); canvas.scale(1 / d, 1 / d);
            canvas.drawBitmap(greys, 0, 0, null);
            canvas.restore();
        }
        canvas.drawPath(sizeWedge, ink); canvas.drawPath(greyWedge, ink);
        for (int i = 0; i < fan.items(); i++) tool(canvas, fan.tool(i), d, false);
        // The thin arc over the pictures' paper, their butts on it.
        float l = ZenFan.LINE;
        canvas.drawArc(new RectF(-l, -l, l, l), -90, 90, false, ink);
    }
    /** The fan's own frame, dp: its point at the origin, out along the foot +x, up -y; mirrored with the column on the right. */
    private void frame(Canvas canvas, float d) {
        canvas.scale(d, d);
        canvas.translate(fan.px - bounds[0], fan.py - bounds[1]);
        if (fan.right) canvas.scale(-1, 1);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (fan == null || getWidth() <= 0) return;
        float d = density();
        int tool = selected();
        boolean hasGreys = DrawLayout.greys(tool);
        java.util.List<Object> key = stillKey(hasGreys);
        if (still == null || still.getWidth() != getWidth() || still.getHeight() != getHeight() || !key.equals(stillKey)) {
            if (still == null || still.getWidth() != getWidth() || still.getHeight() != getHeight()) still = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            still.eraseColor(android.graphics.Color.TRANSPARENT);
            paintStill(new Canvas(still), hasGreys, d);
            stillKey = key;
        }
        canvas.drawBitmap(still, 0, 0, null);
        canvas.save();
        frame(canvas, d);
        // The markers beside the wedges' edges, the column's tabs, clear of the outlines: the size up the side edge, the grey along the foot.
        ToolSwatches.Look look = app.inkWash.look(tool);
        float s = Double.isNaN(dragSize) ? ZenFan.sizeUp(look.px) : (float) dragSize, gap = ZenFan.STROKE, mark = ZenFan.TAB, half = ZenFan.TAB_LONG / 2;
        canvas.drawRect(-gap - mark, -s - half, -gap, -s + half, marks);
        if (hasGreys) {
            float g = Double.isNaN(dragGrey) ? fan.shadeRadius(look.darkness) : (float) dragGrey;
            // Turned to the grey wedge's lower edge: beside it, toward the foot.
            canvas.save(); canvas.rotate((float) ZenFan.LOW);
            canvas.drawRect(gap, -g - half, gap + mark, -g + half, marks);
            canvas.restore();
        }
        tool(canvas, tool, d, true);
        canvas.restore();
        // The ring's previews, unmirrored (the pages read as they are), each turned to point outward, its tab toward the point;
        // its window kept while the page drawn on stays in it (PageTurner#ringStart).
        ringStart = turner().ringStart(ZenFan.previews(), ringStart);
        for (int k = 0; k < ZenFan.previews(); k++) {
            int place = turner().ringPlace(k, ZenFan.previews(), ringStart);
            missing[k] = false;
            if (place == DrawingBook.NONE) continue;
            double a = ZenFan.previewAngle(k);
            float[] at = fan.point(ZenFan.RING, a);
            int saved = canvas.save();
            canvas.translate((at[0] - bounds[0]) * d, (at[1] - bounds[1]) * d);
            canvas.rotate((float) (fan.right ? -a : a));
            // The bar's own previews' pixel size, so the pictures it has made already serve here.
            int w = app.dp(Math.round(ZenFan.PREVIEW_W)), h = app.dp(Math.round(ZenFan.PREVIEW_H));
            missing[k] = !turner().drawRing(canvas, place, new RectF(-w / 2, -h / 2, w - w / 2, h - h / 2));
            canvas.restoreToCount(saved);
        }
        strip(canvas, d);
    }
    /**
     * The page strip along the side edge ({@link ZenFan#strip}), read upward: ‹, the bar's number, ›, and at its top end
     * Zoom's padlock and percentage, each drawn as the bar draws it; an arrow that can't turn grey, as the bar's.
     */
    private void strip(Canvas canvas, float d) {
        float[] s = fan.strip(), parts = ZenFan.stripParts();
        float across = (s[2] - s[0]) * d;
        int saved = canvas.save();
        // Turned: along the strip from its foot is +x, its middle line y = 0.
        canvas.translate(((s[0] + s[2]) / 2 - bounds[0]) * d, (s[3] - bounds[1]) * d);
        canvas.rotate(-90);
        caret(canvas, R.drawable.ic_ph_caret_left, parts[0] / 2 * d, turner().turns(false), d);
        canvas.save(); canvas.translate(parts[0] * d, -across / 2);
        float size = app.inkWashBar.lineTextSize();
        turner().drawNumber(canvas, parts[1] * d, across, size, WORDS);
        canvas.restore();
        caret(canvas, R.drawable.ic_ph_caret_right, (parts[0] + parts[1] + parts[2] / 2) * d, turner().turns(true), d);
        canvas.translate((s[3] - s[1] - parts[3]) * d, -across / 2);
        app.inkWashBar.zoom.draw(canvas, parts[3] * d, across, size, WORDS);
        canvas.restoreToCount(saved);
    }
    /** An arrow's icon, the bar's size, its middle {@code at} px along the strip; grey when it can't turn. */
    private void caret(Canvas canvas, int icon, float at, boolean turns, float d) {
        android.graphics.drawable.Drawable caret = app.getDrawable(icon).mutate();
        caret.setTint(turns ? WORDS : Theme.GREY_400);
        int half = Math.round(DrawLayout.CARET_HALF * d);
        caret.setBounds(Math.round(at) - half, -half, Math.round(at) + half, half);
        caret.draw(canvas);
    }
    /** The ring's first tile ({@link PageTurner#ringStart}; -1 until drawn), and which slots drew with a picture still being made. */
    private int ringStart = -1;
    private final boolean[] missing = new boolean[ZenFan.previews()];
    /**
     * Pictures made since the ring was drawn: the previews that drew without theirs put on the panel again, each its own
     * small box (the whole fan through Android blinked it all, 2026-11-07).
     */
    void picturesArrived() {
        float d = density();
        for (int k = 0; k < missing.length; k++) {
            if (!missing[k]) continue;
            float[] at = fan.point(ZenFan.RING, ZenFan.previewAngle(k));
            float r = (float) Math.hypot(ZenFan.PREVIEW_W, ZenFan.PREVIEW_H) / 2 + 1;
            Rect box = new Rect(Math.round((at[0] - r - bounds[0]) * d), Math.round((at[1] - r - bounds[1]) * d), Math.round((at[0] + r - bounds[0]) * d), Math.round((at[1] + r - bounds[1]) * d));
            if (box.intersect(0, 0, getWidth(), getHeight())) app.selectionFeedback.clean(this, box);
        }
    }
    /** {@code change} turns the page, changes the pages or the zoom: the ring and the strip captured before it, only what it changes there put on the panel. */
    void pagesChanged(Runnable change) {
        long started = android.os.SystemClock.uptimeMillis();
        float d = density(), outer = ZenFan.LINE - ZenFan.CLEAR / 2;
        float[] s = fan.strip();
        // The ring's and the strip's box, view px.
        RectF box = new RectF(Math.min(fan.px, fan.point(outer, 90)[0]), fan.py - outer, Math.max(fan.px, fan.point(outer, 90)[0]), fan.py);
        box.union(s[0], s[1], s[2], s[3]);
        Rect area = new Rect(Math.round((box.left - bounds[0]) * d) - 2, Math.round((box.top - bounds[1]) * d) - 2, Math.round((box.right - bounds[0]) * d) + 2, Math.round((box.bottom - bounds[1]) * d) + 2);
        app.selectionFeedback.updateParts(this, area, change);
        android.util.Log.i(ProbeActivity.TAG, "Zen fan's pages updated: " + (android.os.SystemClock.uptimeMillis() - started) + " ms");
    }

    /** Tool {@code tool}'s picture turned outward on the arc, its butt on it; or ({@code bar}) the one in use's bar just past its tip. */
    private void tool(Canvas canvas, int tool, float d, boolean bar) {
        Bitmap picture = trimmed(tool, d);
        if (picture == null) return;
        // The picture's drawn part, its paper clear, {@link ZenFan#PICTURE} long out from its butt on the arc (the owner,
        // 2026-11-07: "the texture brush image, enlarge it to fit and align it with the arc line"); as thick as that makes it.
        // At most a sector's breadth thick (24 dp), so a stubby picture doesn't swell into its neighbours.
        float k = Math.min(ZenFan.PICTURE / picture.getWidth(), 24f / picture.getHeight()), tall = picture.getHeight() * k;
        int saved = canvas.save();
        canvas.rotate((float) fan.angle(tool) - 90);
        // Just past its tip: the bar of the one in use.
        if (bar) { canvas.drawRect(fan.mark(tool) - ZenFan.BAR / 2, -tall / 2, fan.mark(tool) + ZenFan.BAR / 2, tall / 2, marks); canvas.restoreToCount(saved); return; }
        canvas.translate(fan.base(tool), 0);
        canvas.scale(k, k);
        // A tool this notebook has off, greyed as its row is.
        Paint off = app.inkWashBar.enabled(tool) ? null : faded;
        canvas.drawBitmap(picture, 0, -picture.getHeight() / 2f, off);
        canvas.restoreToCount(saved);
    }
    private final Paint faded = new Paint(Paint.FILTER_BITMAP_FLAG);
    { faded.setAlpha(77); }
    /**
     * Tool {@code tool}'s row picture drawn alone ({@link InkWashBar#rowPicture}, its tip toward +x, mirrored rows turned
     * back), cut to its drawn pixels, its near-white paper made clear, so it lies on the page; null with none.
     */
    private final java.util.Map<Drawable, Bitmap> trims = new java.util.IdentityHashMap<>();
    private Bitmap trimmed(int tool, float d) {
        Drawable picture = app.inkWashBar.rowPicture(tool);
        int[] row = app.inkWashBar.rowSize(tool);
        if (picture == null || row[0] <= 0) return null;
        if (trims.containsKey(picture)) return trims.get(picture);
        Bitmap made = trim(picture, row, d);
        // Kept per picture (a slot's new brush is a new picture); the ones gone let go.
        if (trims.size() > 4 * fan.items()) trims.clear();
        trims.put(picture, made);
        return made;
    }
    private Bitmap trim(Drawable picture, int[] row, float d) {
        int w = Math.round(row[0] * d), h = Math.round(row[1] * d);
        Bitmap whole = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(whole);
        if (app.inkWashBar.mirrored()) { c.translate(w, 0); c.scale(-1, 1); }
        picture.setBounds(0, 0, w, h);
        picture.draw(c);
        int[] px = new int[w * h];
        whole.getPixels(px, 0, w, 0, 0, w, h);
        int left = w, top = h, right = -1, bottom = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = px[y * w + x], a = p >>> 24, lightest = Math.min((p >> 16) & 0xff, Math.min((p >> 8) & 0xff, p & 0xff));
            if (a == 0 || lightest > 245) { px[y * w + x] = 0; continue; }
            left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y);
        }
        if (right < left) return null;
        whole.setPixels(px, 0, w, 0, 0, w, h);
        return Bitmap.createBitmap(whole, left, top, right - left + 1, bottom - top + 1);
    }
    /** The grey wedge's square, {@link ZenFan#WEDGE} dp a side above the foot from the point: each pixel its grey in the panel's dots. */
    private Bitmap greys(float d) {
        int side = Math.round(ZenFan.WEDGE * d);
        int[] pixels = new int[side * side];
        for (int y = 0; y < side; y++)
            for (int x = 0; x < side; x++)
                pixels[y * side + x] = DotPattern.pixel(ToolSwatches.tone(fan.darknessAt(Math.hypot(x / d, ZenFan.WEDGE - y / d))), x, y);
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (fan == null) return false;
        float d = density(), sx = bounds[0] + event.getX() / d, sy = bounds[1] + event.getY() / d;
        int at = fan.hit(sx, sy);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Outside the fan, though in its box: the touch goes on to the page, the fan staying open (the owner, 2026-11-06).
                if (at == ZenFan.OUTSIDE) return false;
                item = at; held = false;
                slot = at == ZenFan.PAGES ? fan.preview(sx, sy) : -1;
                if ((at == ZenFan.SIZE || at == ZenFan.SHADE && DrawLayout.greys(selected())) && !app.inkWashBar.swatches.ready()) item = ZenFan.NONE;
                else if (at >= 0 || at == ZenFan.LEAVE || at == ZenFan.PAGES) postDelayed(hold, ViewConfiguration.getLongPressTimeout());
                follow(sx, sy);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (at != item || item == ZenFan.PAGES && fan.preview(sx, sy) != slot) removeCallbacks(hold);
                follow(sx, sy);
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(hold);
                int was = item;
                item = ZenFan.NONE;
                if (was == ZenFan.SIZE) { double t = ToolSwatches.position(fan.size(sx, sy)); release(() -> app.inkWashBar.swatches.setSize(t)); }
                else if (was == ZenFan.SHADE && DrawLayout.greys(selected())) { double g = fan.darkness(sx, sy); release(() -> app.inkWashBar.swatches.setGrey(g)); }
                // Round Zen's circle, past its button's cell: as a tap on the circle, the fan closed.
                else if (was == ZenFan.LEAVE && at == ZenFan.LEAVE && !held) app.zenTapped();
                // A tool picked: the fan stays open (the owner, 2026-11-04), its mark and markers drawn again.
                else if (was >= 0 && at == was && !held) release(() -> app.inkWashBar.pick(was));
                // The strip's ‹ and › and Zoom, as the bar's (Zoom's tap locks or unlocks).
                else if ((was == ZenFan.PREVIOUS || was == ZenFan.NEXT) && at == was && !held) turner().turn(was == ZenFan.NEXT);
                else if (was == ZenFan.ZOOM && at == was && !held) app.toggleNavigationLock();
                // A preview tapped: as on the bar, the page turned to (the ring follows, pagesChanged), the + a page added.
                else if (was == ZenFan.PAGES && at == was && fan.preview(sx, sy) == slot && !held) turner().ringTap(turner().ringPlace(slot, ZenFan.previews(), ringStart));
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(hold);
                item = ZenFan.NONE;
                release(() -> { });
                return true;
            default: return true;
        }
    }
    /** While a wedge is held, its marker follows the pen (its edge's strip alone put on the panel); the value is set on the lift. */
    private void follow(float sx, float sy) {
        double size = item == ZenFan.SIZE ? ZenFan.sizeUp(fan.size(sx, sy)) : Double.NaN;
        double grey = item == ZenFan.SHADE && DrawLayout.greys(selected()) ? fan.shadeRadius(fan.darkness(sx, sy)) : Double.NaN;
        if (Double.compare(size, dragSize) == 0 && Double.compare(grey, dragGrey) == 0) return;
        // Only the dragged wedge's marker strip, ink and paper: the panel's binary update, as the column's slider tab (StripMarker).
        app.selectionFeedback.updateBinary(this, strip(item == ZenFan.SIZE), () -> { dragSize = size; dragGrey = grey; });
    }
    /**
     * {@code change} made and the fan put on the panel again, the drag's markers settled where the values now are: only
     * the boxes that changed (the old and new bar, the markers), ink and paper ones through the binary update, as a pick
     * on the column ({@link SelectionFeedback#updateParts}); the grey wedge, coming or going with the eraser, one grey pass.
     */
    private void release(Runnable change) {
        long started = android.os.SystemClock.uptimeMillis();
        app.selectionFeedback.updateParts(this, () -> { dragSize = dragGrey = Double.NaN; change.run(); });
        android.util.Log.i(ProbeActivity.TAG, "Zen fan updated: " + (android.os.SystemClock.uptimeMillis() - started) + " ms");
    }
    /** A marker's strip beside its wedge's edge, px in the view, clear of the outline: the size's up the side edge, else the box round the grey's beside its lower edge. */
    private Rect strip(boolean size) {
        float d = density(), room = ZenFan.MARK_ROOM, gap = ZenFan.STROKE;
        // As far out as a marker goes, short of the ring of previews (greys a binary update would harden).
        float top = -ZenFan.WEDGE - ZenFan.TAB_LONG / 2;
        RectF strip = size ? new RectF(-room, top, -gap, 0) : new RectF(gap, top, room, 0);
        // The fan's frame, dp, as onDraw's: its point, mirrored with the column on the right, the grey's turned to its edge.
        android.graphics.Matrix frame = new android.graphics.Matrix();
        if (!size) frame.postRotate((float) ZenFan.LOW);
        if (fan.right) frame.postScale(-1, 1);
        frame.postTranslate(fan.px - bounds[0], fan.py - bounds[1]);
        frame.mapRect(strip);
        Rect px = new Rect(Math.round(strip.left * d), Math.round(strip.top * d), Math.round(strip.right * d), Math.round(strip.bottom * d));
        px.intersect(0, 0, getWidth(), getHeight());
        return px;
    }
    @Override protected void onDetachedFromWindow() { removeCallbacks(hold); super.onDetachedFromWindow(); }
}
