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
 * the panel's dots, each outlined, its marker beside its edge; the thin arc, and outside it the column's tools in its
 * order, each its row's picture ({@link InkWashBar#rowPicture}) turned to point outward, its butt on the arc, the one in
 * use with a bar just past its tip. A tap on a tool picks it as its row does, a long press holds it as its row does
 * ({@link InkWashBar#hold}: their menus open beside the fan), and the fan stays open, drawing on the page too; a tap or drag on a wedge sets the
 * size or grey on the lift, as the column's picker box does ({@link SwatchBar#setSize}, {@link SwatchBar#setGrey}), its
 * marker following the pen through the fast display. A touch outside it goes on to the page; only Zen's circle closes it.
 * Paper under its line, where the column's buttons are lent while it is open ({@link ZenFan#cell}). Drawn by
 * Android in the column's partial grey ({@link InkWashBar#partialGrey(View...)}), no flash of its own.
 */
@SuppressLint("ViewConstructor")
final class ZenFanView extends View {
    private final PaintActivity app;
    private ZenFan fan;
    /** The fan's bounds on the stage, dp ({@link ZenFan#bounds}). */
    private float[] bounds;
    /** The outlines, smooth; the markers and the selected tool's bar ink alone, no anti-aliasing, so their changes go through the panel's binary update. */
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG), marks = new Paint(), paper = new Paint();
    private final Path sizeWedge = new Path(), greyWedge = new Path();
    /** The grey wedge's dots, px, from the fan's point (unmirrored); made once. */
    private Bitmap greys;
    /** What the pen went down on ({@link ZenFan#hit}), and the size's or grey's place while it drags (NaN: none). */
    private int item = ZenFan.NONE;
    private double dragSize = Double.NaN, dragGrey = Double.NaN;
    private boolean held;
    /** A long press: a tool held as its row is; on Zen's circle, as the circle's hold (Zen Mode left). */
    private final Runnable hold = () -> { held = true; if (item == ZenFan.LEAVE) app().zenHeld(); else app().inkWashBar.hold(item); };
    private PaintActivity app() { return app; }

    ZenFanView(PaintActivity app) {
        super(app);
        this.app = app;
        setContentDescription(DrawLayout.ZEN_TOOLS);
        paper.setColor(Theme.PAPER);
        marks.setColor(Theme.INK);
        ink.setColor(Theme.INK); ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(ZenFan.STROKE);
        // The wedges, from the point (0, 0), out along +x (the foot) and up along -y (the side edge).
        float c = ZenFan.wedgeStart(), w = ZenFan.WEDGE;
        float[] end = ZenFan.wedgeEnd();
        RectF circle = new RectF(-w, -w, w, w);
        float meet = (float) Math.toDegrees(Math.atan2(-end[1], end[0]));
        sizeWedge.moveTo(0, -c); sizeWedge.lineTo(0, -w); sizeWedge.arcTo(circle, -90, meet + 90); sizeWedge.close();
        greyWedge.moveTo(c, 0); greyWedge.lineTo(w, 0); greyWedge.arcTo(circle, 0, -90 - meet); greyWedge.close();
    }

    /** Laid over the stage, {@code width × height} px, for Zen's icon's middle at ({@code x}, {@code y}) px, {@code slots} slots shown. */
    void place(float x, float y, int slots, boolean right, int width, int height) {
        float d = density();
        fan = new ZenFan(x / d, y / d, slots, right, width / d, height / d);
        bounds = fan.bounds();
        android.widget.FrameLayout.LayoutParams at = new android.widget.FrameLayout.LayoutParams(Math.round((bounds[2] - bounds[0]) * d), Math.round((bounds[3] - bounds[1]) * d));
        at.leftMargin = Math.round(bounds[0] * d); at.topMargin = Math.round(bounds[1] * d);
        setLayoutParams(at);
    }
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

    @Override protected void onDraw(Canvas canvas) {
        if (fan == null) return;
        float d = density();
        // Paper under the lent buttons, which have none of their own.
        for (int i = 0; i < ZenFan.CELLS; i++) {
            float[] c = fan.cell(i);
            canvas.drawRect((c[0] - bounds[0]) * d, (c[1] - bounds[1]) * d, (c[2] - bounds[0]) * d, (c[3] - bounds[1]) * d, paper);
        }
        canvas.save();
        // The fan's own frame, dp: its point at the origin, out along the foot +x, up -y; mirrored with the column on the right.
        canvas.scale(d, d);
        canvas.translate(fan.px - bounds[0], fan.py - bounds[1]);
        if (fan.right) canvas.scale(-1, 1);
        // Paper over the page, a disc round the X (ZenFan.backdrop): the drawing hidden under the fan while it is open.
        canvas.drawCircle(-fan.corner, fan.corner, ZenFan.backdrop(), paper);
        int tool = selected();
        boolean hasGreys = DrawLayout.greys(tool);
        if (hasGreys) {
            if (greys == null) greys = greys(d);
            canvas.save(); canvas.clipPath(greyWedge);
            canvas.translate(0, -ZenFan.WEDGE); canvas.scale(1 / d, 1 / d);
            canvas.drawBitmap(greys, 0, 0, null);
            canvas.restore();
        }
        canvas.drawPath(sizeWedge, ink); canvas.drawPath(greyWedge, ink);
        // The markers beside the wedges' edges, the column's tabs, clear of the outlines: the size up the side edge, the grey along the foot.
        ToolSwatches.Look look = app.inkWash.look(tool);
        float s = Double.isNaN(dragSize) ? ZenFan.sizeUp(look.px) : (float) dragSize, gap = ZenFan.STROKE, mark = ZenFan.TAB, half = ZenFan.TAB_LONG / 2;
        canvas.drawRect(-gap - mark, -s - half, -gap, -s + half, marks);
        if (hasGreys) {
            float g = Double.isNaN(dragGrey) ? fan.shadeRadius(look.darkness) : (float) dragGrey;
            canvas.drawRect(g - half, gap, g + half, gap + mark, marks);
        }
        for (int i = 0; i < fan.items(); i++) tool(canvas, fan.tool(i), d);
        // The thin arc over the pictures' paper, their butts on it.
        float l = ZenFan.LINE;
        canvas.drawArc(new RectF(-l, -l, l, l), -90, 90, false, ink);
        canvas.restore();
    }

    /** Tool {@code tool}'s picture turned outward on the arc, its butt on it; the one in use, its bar just past its tip. */
    private void tool(Canvas canvas, int tool, float d) {
        Drawable picture = app.inkWashBar.rowPicture(tool);
        int[] row = app.inkWashBar.rowSize(tool);
        if (picture == null || row[0] <= 0) return;
        // The row's picture lies across its width but its bar's room, its tip toward the page (SlotTile.row).
        float content = row[0] - DrawLayout.barRoom(), k = ZenFan.PICTURE / content, tall = row[1] * k;
        int saved = canvas.save();
        canvas.rotate((float) fan.angle(tool) - 90);
        int turned = canvas.save();
        canvas.translate(fan.base(tool), 0);
        canvas.scale(k / d, k / d);
        int w = Math.round(row[0] * d), h = Math.round(row[1] * d);
        // Mirrored rows (the column on the right) turned back: the frame is mirrored already.
        if (app.inkWashBar.mirrored()) { canvas.translate(w, 0); canvas.scale(-1, 1); }
        picture.setBounds(0, -h / 2, w, h - h / 2);
        // A tool this notebook has off, greyed as its row is.
        if (!app.inkWashBar.enabled(tool)) canvas.saveLayerAlpha(0, -h / 2f, w, h / 2f, 77);
        picture.draw(canvas);
        canvas.restoreToCount(turned);
        // Over the picture's paper: the bar just past its tip.
        if (tool == selected()) canvas.drawRect(fan.mark(tool) - ZenFan.BAR / 2, -tall / 2, fan.mark(tool) + ZenFan.BAR / 2, tall / 2, marks);
        canvas.restoreToCount(saved);
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
                if ((at == ZenFan.SIZE || at == ZenFan.SHADE && DrawLayout.greys(selected())) && !app.inkWashBar.swatches.ready()) item = ZenFan.NONE;
                else if (at >= 0 || at == ZenFan.LEAVE) postDelayed(hold, ViewConfiguration.getLongPressTimeout());
                follow(sx, sy);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (at != item) removeCallbacks(hold);
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
        app.selectionFeedback.updateParts(this, () -> { dragSize = dragGrey = Double.NaN; change.run(); });
    }
    /** A marker's strip beside its wedge's edge, px in the view, clear of the outline: the size's up the side edge, else the grey's along the foot. */
    private Rect strip(boolean size) {
        float d = density(), room = ZenFan.MARK_ROOM, gap = ZenFan.STROKE, sign = fan.right ? -1 : 1, x = fan.px - bounds[0], y = fan.py - bounds[1];
        RectF strip = size ? new RectF(x - sign * room, y - ZenFan.WEDGE - ZenFan.TAB_LONG, x - sign * gap, y)
                : new RectF(x, y + gap, x + sign * (ZenFan.WEDGE + ZenFan.TAB_LONG), y + room);
        strip.sort();
        Rect px = new Rect(Math.round(strip.left * d), Math.round(strip.top * d), Math.round(strip.right * d), Math.round(strip.bottom * d));
        px.intersect(0, 0, getWidth(), getHeight());
        return px;
    }
    @Override protected void onDetachedFromWindow() { removeCallbacks(hold); super.onDetachedFromWindow(); }
}
