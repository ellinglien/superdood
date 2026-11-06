package io.github.mpdairy.monopaint;

/**
 * Zen Mode's fan (the owner's sketch and mockup, 2026-11-04): a quarter circle whose point sits {@link #corner} up and in
 * from Zen Mode's icon ({@link DrawLayout#zenCell}), where the X that leaves Zen is ({@link #LEAVE}), just outside the
 * point. The quarter is split along its diagonal (the point to the arc's middle) by a white gap {@link #BAND} wide into
 * two wedges: along the side edge the size ({@link #SIZE}, white, broader further from the point, its marker on the
 * side edge where the wedge is as wide as the line, {@link #sizeUp}), along the foot the grey ({@link #SHADE}, white at the point to black at its arc, its marker on the foot).
 * Past the wedges a white band as wide as the gap, then a thin arc ({@link #LINE}); just outside it the column's tools
 * stand out in its order from the side edge (the slots, then Fill in, Smudge and Eraser), each its own sector. The gap
 * hits nothing ({@link #NONE}). Only a tap on Zen's circle ({@link #LEAVE}) closes it, a hold there leaves Zen Mode
 * (the owner, 2026-11-06: "Keep tray open when drawing - press to close"; picking a tool keeps it open, 2026-11-04);
 * the column's layer button, droplet and Lasso in the gap ({@link #cell}). Mirrored from the bottom-right corner with the column on the right. Sizes and greys are the
 * toolbar's own steps ({@link ToolSwatches#sizeAt}, {@link ShadeStrip}). All in dp, screen coordinates (y down). Pure,
 * checked on the desktop ({@code ZenFanChecks}); the browser's too, through the engine.
 */
final class ZenFan {
    /** What a point is over ({@link #hit}): these, or a tool ({@code 0..slots-1}, {@link ToolSlots#FILL}, {@link ToolSlots#SMUDGE}, {@link ToolSlots#ERASER}). */
    static final int OUTSIDE = -1, SIZE = -2, SHADE = -3, LEAVE = -4, NONE = -5;
    /**
     * dp (160 an inch, 63 a centimetre, as {@link DrawLayout#ZEN_ROOM}), the owner's mockup (2026-11-04) at 4.7 cm
     * across (3 cm, "the whole fan, tools included", then a quarter larger twice: "i think it can be enlarged a bit...
     * maybe by 25%?", "another 25% larger i think. they're a bit teeny for finger use"): the X's radius (a 56 dp target, not scaled); the wedges' radius; the white between the wedges and between
     * them and the thin arc (the owner: equidistant), as wide as the thin arc's top end is from the screen's edge on the
     * Manta, 60 dp (the owner, 2026-11-06: "i would like to increase it so that that space equals the width of the space
     * from the end of the top arc on the top left to the screen edge"; 31 until then, the wedges 212 dp, the tools kept
     * where they were; the room for the layer button, the droplet and the Lasso in the gap, {@link #cell}); the thin
     * arc's radius; the tools' outer reach; how far past it a tool still takes a tap; the room beside each wedge's edge
     * for its marker, which takes the pen too.
     */
    static final float LEAVE_R = 28, WEDGE = 183, BAND = 60, LINE = WEDGE + BAND, OUTER = 295, REACH = 19, MARK_ROOM = 19;
    /**
     * dp: a tool's picture's length out from the arc, its butt on the arc (the owner, 2026-11-04: no gap), the Eraser's
     * {@link #ERASER_GAP} out; the selected one's bar {@link #MARK_GAP} past its tip, as the column's side bar, {@link
     * #BAR} thick; a wedge's marker, the column's tab half again larger, {@link #TAB} by {@link #TAB_LONG}.
     */
    static final float PICTURE = 37, ERASER_GAP = 5, MARK_GAP = 6, BAR = 5, TAB = 5, TAB_LONG = 15.5f;

    /** One width for every line the fan draws, dp: its wedges' outlines, its thin arc, and the button's X and circles (Iconoir's 1.5 of 24 at {@link DrawLayout#ZEN_ICON}; the owner, 2026-11-04: "keep all strokes in that section the same width"). */
    static final float STROKE = 1.5f * DrawLayout.ZEN_ICON / 24;

    /** The X (Zen's icon's middle), and the fan's point, screen dp. */
    final float x, y, px, py;
    /**
     * The X's middle to the fan's point, dp, on the diagonal (the owner, 2026-11-04: "tucked in", liked after trying it;
     * set then as the corner dot's room and the band, 14 and 20 dp on the Manta, before the dot went; 62, 44 each way, until then).
     */
    static final float TUCK = 34;
    /** The point's offset from the X, up and in, dp each way. */
    final float corner;
    final boolean right;
    /** The slots on the arc; Fill in, Smudge and Eraser follow them. */
    final int slots;
    /** The screen, dp. */
    private final float width, height;

    /**
     * The fan for Zen's icon at ({@code x}, {@code y}) on a {@code width × height} dp screen, {@code slots} slots (at
     * least one), opening up and toward the screen's middle: right, or left when {@code right}.
     */
    ZenFan(float x, float y, int slots, boolean right, float width, float height) {
        this.x = x; this.y = y; this.right = right; this.slots = Math.max(1, slots); this.width = width; this.height = height;
        corner = TUCK / (float) Math.sqrt(2);
        px = right ? x - corner : x + corner; py = y - corner;
    }
    /** The fan on a {@code width × height} dp screen from Zen Mode's icon's middle ({@link DrawLayout#zenCell}), the column on the right or not. */
    static ZenFan at(int width, int height, int slots, boolean right) {
        DrawLayout d = new DrawLayout(width, height);
        int[] cell = DrawLayout.zenCell(width, height, d.side, d.half(), d.zenHeight(), right);
        return new ZenFan(cell[0] + d.half() / 2f, cell[1] + d.zenHeight() / 2f, slots, right, width, height);
    }

    /** How many tools the arc holds: the slots, Fill in, Smudge and Eraser. */
    int items() { return slots + DrawLayout.AFTER_TRAY.length; }
    /** The tool at place {@code item} on the arc from the side edge: the column's order. */
    int tool(int item) { return item < slots ? item : DrawLayout.AFTER_TRAY[item - slots]; }
    /** Tool {@code tool}'s middle on the arc, degrees from the side edge (0) toward the foot (90). */
    double angle(int tool) {
        int item = tool;
        for (int i = 0; i < DrawLayout.AFTER_TRAY.length; i++) if (DrawLayout.AFTER_TRAY[i] == tool) item = slots + i;
        return (item + .5) * 90 / items();
    }

    /** Where tool {@code tool}'s picture's butt sits, dp from the point: on the thin arc, the Eraser a little out. */
    float base(int tool) { return LINE + (tool == ToolSlots.ERASER ? ERASER_GAP : 0); }
    /** Where the selected tool's bar sits, dp from the point: just past its tip. */
    float mark(int tool) { return base(tool) + PICTURE + MARK_GAP; }

    /** ({@code sx}, {@code sy}) from the point: {out along the foot toward the screen's middle, up the side edge}. */
    private float[] local(float sx, float sy) { return new float[]{right ? px - sx : sx - px, py - sy}; }
    /** Each wedge's corner, dp from the point along its edge: where the gap's side meets it. */
    static float wedgeStart() { return BAND / (float) Math.sqrt(2); }
    /** Where the size wedge's arc meets the gap, dp from the point: {out, up}; the grey wedge's is the same turned, {up, out}. */
    static float[] wedgeEnd() {
        float c = wedgeStart(), out = (float) (-c + Math.sqrt(2 * WEDGE * WEDGE - c * c)) / 2;
        return new float[]{out, out + c};
    }

    /** What ({@code sx}, {@code sy}) is over: {@link #LEAVE}, {@link #SIZE}, {@link #SHADE}, a tool, {@link #NONE} or {@link #OUTSIDE}. */
    int hit(float sx, float sy) { return Math.hypot(sx - x, sy - y) < LEAVE_R ? LEAVE : fanHit(sx, sy); }
    /** As {@link #hit} but for the X: the fan's own parts. */
    int fanHit(float sx, float sy) {
        float[] p = local(sx, sy);
        float dx = p[0], dy = p[1];
        double r = Math.hypot(dx, dy);
        if (r > OUTER + REACH || dx < -MARK_ROOM || dy < -MARK_ROOM) return OUTSIDE;
        // Beside the quarter's edges, only the wedges' marker rooms; the tools take a tap a little past their pictures, the nearest by angle.
        boolean behind = dx < 0 || dy < 0;
        if (r >= LINE) return behind ? OUTSIDE : tool(Math.min(items() - 1, (int) (Math.toDegrees(Math.atan2(dx, dy)) * items() / 90)));
        if (r > WEDGE) return behind ? OUTSIDE : NONE;
        // Across the diagonal, dp: the gap's half each side of it hits nothing.
        double off = (dy - dx) / Math.sqrt(2);
        if (Math.abs(off) < BAND / 2) return NONE;
        if (off > 0) return dy < 0 ? OUTSIDE : SIZE;
        return dx < 0 ? OUTSIDE : SHADE;
    }

    /** Page px a dp: both tablets' 300 dpi panels at 160 dp an inch, so a line's page px at 100% are its screen px. */
    static final float PAGE_PX = 300f / 160;
    /**
     * The size wedge's width across at {@code up} dp up the side edge, dp: from the side edge to the gap's side, or to the
     * arc past its widest ({@link #wedgeEnd}). Its marker reads as the line's width there (the owner, 2026-11-04: "it
     * indicates the size of the tool... so it should be closer to the size actually shown").
     */
    static double width(double up) { return up <= wedgeStart() || up >= WEDGE ? 0 : Math.min(up - wedgeStart(), Math.sqrt(WEDGE * WEDGE - up * up)); }
    /** The broadest line the wedge shows true, page px: its width where widest. */
    static double truest() { return wedgeEnd()[0] * PAGE_PX; }
    /**
     * Where size {@code px}'s marker sits, dp from the point up the side edge: where the wedge is as wide as the line at
     * 100% ({@link #width}); a broader one, past the widest, on the rest of the edge on the toolbar's log scale up to its
     * broadest ({@link ModularSizes#MAX}).
     */
    static float sizeUp(double px) {
        double most = truest(), top = wedgeEnd()[1];
        if (px <= most) return (float) (wedgeStart() + Math.max(0, px) / PAGE_PX);
        return (float) (top + (WEDGE - top) * Math.min(1, Math.log(px / most) / Math.log(ModularSizes.MAX / most)));
    }
    /** The size at ({@code sx}, {@code sy}), page px: {@link #sizeUp}'s inverse at its height, to the toolbar slider's nearest step ({@link ToolSwatches#sizeAt}). */
    double size(float sx, float sy) {
        double up = Math.min(WEDGE, local(sx, sy)[1]), most = truest(), top = wedgeEnd()[1];
        double px = up <= top ? (up - wedgeStart()) * PAGE_PX : most * Math.pow(ModularSizes.MAX / most, (up - top) / (WEDGE - top));
        // At the wedge's corner and below it, the finest: a floor, so the finest sizes stay a tap away.
        return ToolSwatches.sizeAt(ToolSwatches.position(Math.max(ModularSizes.MIN, px)));
    }

    /** The grey wedge's length, dp, and its solid ends' ({@link ShadeStrip#endpoint}). */
    private int length() { return Math.round(WEDGE - wedgeStart()); }
    private int endpoint() { return ShadeStrip.endpoint(length(), ShadeStrip.END); }
    /** The grey at ({@code sx}, {@code sy}), darkness 0–1: the toolbar strip's {@link ShadeStrip#LEVELS} steps, white at the wedge's corner to black at its arc. */
    double darkness(float sx, float sy) {
        float[] p = local(sx, sy);
        return darknessAt(Math.hypot(p[0], p[1]));
    }
    /** The grey {@code r} dp from the point, darkness 0–1 (the grey wedge's fill). */
    double darknessAt(double r) { return DrawLayout.shadeAt((float) r, wedgeStart(), length(), endpoint()); }
    /** Where grey {@code darkness}'s marker sits, dp from the point along the foot. */
    float shadeRadius(double darkness) { return wedgeStart() + length() - ShadeStrip.markerAt(ToolSwatches.tone(darkness), length(), endpoint()); }

    /** The screen point {@code r} dp from the fan's point at {@code degrees} from the side edge toward the foot: {x, y}. */
    float[] point(float r, double degrees) {
        double a = Math.toRadians(degrees);
        return new float[]{px + (right ? -1 : 1) * (float) (r * Math.sin(a)), py - (float) (r * Math.cos(a))};
    }
    /**
     * The column's buttons in the gap between the wedges, lent while the fan is open ({@code InkWashBar.lend}; the owner,
     * 2026-11-06: "the key change was putting the layers, droplet and lasso between the two wedges, with the new space
     * you're making"; beside the dot or along the foot earlier that day, two strips that morning): out from the point,
     * the layer button, the droplet and the Lasso, each a square centred on the diagonal ({@link #side}), {@link #STEP}
     * apart, their corners inside the gap ({@link #BAND}), the Lasso's outer corner inside the wedges' radius (the owner,
     * 2026-11-06: "so it doesn't go out beyond where the arcs of the two wedges would connect"; 48, 114 and 180 dp out
     * until then).
     */
    static final int LAYERS = 0, WET = 1, LASSO = 2, CELLS = 3;
    /**
     * dp: a cell's side, what the gap holds with a little white each side ({@code CELL * sqrt 2 < BAND}); the layer
     * button's, 85% of it (the owner: "can the layers picker be a bit smaller, like 85% what it is now? to match the
     * droplet and lasso"); the first cell's middle out from the point, and the next ones'.
     */
    static final float CELL = 40, LAYERS_SIDE = .85f * CELL, FIRST = 40, STEP = 57;
    /** Cell {@code cell}'s side, dp. */
    static float side(int cell) { return cell == LAYERS ? LAYERS_SIDE : CELL; }
    /** Cell {@code cell}'s box on the screen, dp: {left, top, right, bottom}; mirrored with the column on the right. */
    float[] cell(int cell) {
        float[] middle = point(FIRST + cell * STEP, 45);
        float half = side(cell) / 2;
        return new float[]{middle[0] - half, middle[1] - half, middle[0] + half, middle[1] + half};
    }
    /** How many steps each side of the X's row the paper's disc is stepped into for the mask ({@link #mask}). */
    static final int STEPS = 8;
    /**
     * What the fast display keeps off while the fan is open, dp {left, top, right, bottom} each: the paper's disc in
     * {@link #STEPS} steps each side of the X's row, at equal angles round it (each step as wide as the disc at its
     * nearer edge to the X's row, so none of the disc is left out, about 32 dp past the arc at most), and the line's
     * cells; on the screen only. Strokes beside it, though inside {@link #bounds}, still show while it is open.
     */
    java.util.List<float[]> mask() {
        java.util.List<float[]> mask = new java.util.ArrayList<>();
        float r = backdrop();
        for (int k = 0; k < STEPS; k++) {
            double near = Math.toRadians(90.0 * (k + 1) / STEPS), far = Math.toRadians(90.0 * k / STEPS);
            float wide = (float) (r * Math.sin(near)), from = (float) (r * Math.cos(near)), to = (float) (r * Math.cos(far));
            for (int side = -1; side <= 1; side += 2) {
                float top = side < 0 ? y - to : y + from, bottom = side < 0 ? y - from : y + to;
                float[] step = {Math.max(0, x - wide), Math.max(0, top), Math.min(width, x + wide), Math.min(height, bottom)};
                if (step[0] < step[2] && step[1] < step[3]) mask.add(step);
            }
        }
        for (int i = 0; i < CELLS; i++) mask.add(cell(i));
        return mask;
    }
    /** The dot over the X while the fan is open ({@code PaintActivity.showZenDot}): the X's target's square, dp {left, top, right, bottom}. */
    float[] dot() { return new float[]{x - LEAVE_R, y - LEAVE_R, x + LEAVE_R, y + LEAVE_R}; }

    /** All it covers on the screen, dp: {left, top, right, bottom}: the paper's disc and the line (the fast display keeps off it all). */
    float[] bounds() {
        float r = backdrop();
        float[] box = {Math.max(0, x - r), Math.max(0, y - r), Math.min(width, x + r), Math.min(height, y + r)};
        for (int i = 0; i < CELLS; i++) {
            float[] c = cell(i);
            box[0] = Math.min(box[0], c[0]); box[1] = Math.min(box[1], c[1]); box[2] = Math.max(box[2], c[2]); box[3] = Math.max(box[3], c[3]);
        }
        return box;
    }
    /**
     * The paper under the open fan, a disc round the X out past the tools' reach, dp (the owner, 2026-11-04: "when fan is
     * open, maybe obscure outward from the circle, hide the drawings underneath temporarily"): the page under the fan,
     * between its tools and round the X hidden while it is open, shown again as it closes.
     */
    static float backdrop() { return TUCK + OUTER + REACH; }
}
