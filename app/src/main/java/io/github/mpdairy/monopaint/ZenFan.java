package io.github.mpdairy.monopaint;

/**
 * Zen Mode's fan (the owner's sketch and mockup, 2026-11-04): a quarter circle whose point sits {@link #corner} up and in
 * from Zen Mode's icon ({@link DrawLayout#zenCell}), where the X that leaves Zen is ({@link #LEAVE}), just outside the
 * point. Its two wedges lie in the quarter's upper part, from the side edge to {@link #LOW} degrees (the owner's sketch,
 * 2026-11-07: "the two wedges are limited to the first quarter of the space they both occupy now"), split along {@link
 * #SPLIT} degrees by a white gap {@link #CLEAR} wide: along the side edge the size ({@link #SIZE}, white, broader further
 * from the point, its marker on the side edge where the wedge is as wide as the line, {@link #sizeUp}), then the grey
 * ({@link #SHADE}, white at the point to black at its arc, its marker beside its lower edge). Past the wedges, as far
 * from them as they are from each other ("equidistant"), a ring of the page bar's previews, the page drawn on with its
 * tab ({@link #PAGES}, {@link #preview}; "the same size page previews from the top bar, with the same marker"), then as
 * far again the thin arc ({@link #LINE}); under the wedges, along the foot, the column's Dropper, Lasso, droplet and
 * layer button in a row, and Zoom over them ({@link #cell}). Just outside the arc the column's tools
 * stand out in its order from the side edge (the slots, then Fill in, Smudge and Eraser), each its own sector. The gap
 * hits nothing ({@link #NONE}). Only a tap on Zen's circle ({@link #LEAVE}) closes it, a hold there leaves Zen Mode
 * (the owner, 2026-11-06: "Keep tray open when drawing - press to close"; picking a tool keeps it open, 2026-11-04).
 * Mirrored from the bottom-right corner with the column on the right. Sizes and greys are the
 * toolbar's own steps ({@link ToolSwatches#sizeAt}, {@link ShadeStrip}). All in dp, screen coordinates (y down). Pure,
 * checked on the desktop ({@code ZenFanChecks}); the browser's too, through the engine.
 */
final class ZenFan {
    /** What a point is over ({@link #hit}): these, the ring's previews ({@link #PAGES}, which one {@link #preview}), or a tool ({@code 0..slots-1}, {@link ToolSlots#FILL}, {@link ToolSlots#SMUDGE}, {@link ToolSlots#ERASER}). */
    static final int OUTSIDE = -1, SIZE = -2, SHADE = -3, LEAVE = -4, NONE = -5, PAGES = -6, PREVIOUS = -7, NEXT = -8, ZOOM = -9;
    /**
     * dp (160 an inch, 63 a centimetre, as {@link DrawLayout#ZEN_ROOM}), the owner's mockup (2026-11-04) at 4.7 cm
     * across (3 cm, "the whole fan, tools included", then a quarter larger twice: "i think it can be enlarged a bit...
     * maybe by 25%?", "another 25% larger i think. they're a bit teeny for finger use"): the X's radius (a 56 dp target,
     * not scaled); the thin arc's radius, as far from the screen's edge on the Manta as its top end (60 dp, the owner,
     * 2026-11-06); the one white space the fan keeps between its wedges, between them and the ring of previews, and
     * between the ring and the arc (the owner, 2026-11-07: "wedges are equidistant from each other and the outer arc");
     * the tools' outer reach; how far past it a tool still takes a tap; the room beside each wedge's edge for its marker,
     * which takes the pen too.
     */
    static final float LEAVE_R = 28, LINE = 243, CLEAR = 14, OUTER = 295, REACH = 19, MARK_ROOM = 19;
    /** A preview in the ring, dp: the page bar's on the Manta ({@link DrawLayout#ROOMY_PREVIEW_WIDTH}), its gap, and its tab's room under it. */
    static final float PREVIEW_W = DrawLayout.ROOMY_PREVIEW_WIDTH, PREVIEW_H = DrawLayout.ROOMY_PREVIEW_HEIGHT, PREVIEW_GAP = DrawLayout.ROOMY_PREVIEW_GAP,
            PREVIEW_TAB = DrawLayout.STRIP_TAB_GAP + DrawLayout.STRIP_TAB;
    /** The ring's previews' middles' radius, dp, their tops {@link #CLEAR} inside the arc; the wedges' radius, {@link #CLEAR} inside the tabs; the band between them and the arc. */
    static final float RING = LINE - CLEAR - PREVIEW_H / 2, WEDGE = RING - PREVIEW_H / 2 - PREVIEW_TAB - CLEAR, BAND = LINE - WEDGE;
    /** Degrees from the side edge: the gap's middle between the wedges, and the grey wedge's lower edge, the row and Zoom under it (the owner's sketch). */
    static final double SPLIT = 26, LOW = 52;
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
    /** The gap's half width, dp, and its middle's direction's sine and cosine. */
    private static final double HALF = CLEAR / 2, SIN = Math.sin(Math.toRadians(SPLIT)), COS = Math.cos(Math.toRadians(SPLIT));
    /** The size wedge's corner, dp up the side edge from the point: where the gap's side meets it. */
    static float wedgeStart() { return (float) (HALF / SIN); }
    /** The grey wedge's corner, dp out along its lower edge from the point. */
    static float greyStart() { return (float) (HALF / Math.sin(Math.toRadians(LOW - SPLIT))); }
    /** Where the size wedge's arc meets the gap, dp from the point: {out, up}. */
    static float[] wedgeEnd() {
        double t = Math.sqrt(WEDGE * WEDGE - HALF * HALF);
        return new float[]{(float) (t * SIN - HALF * COS), (float) (t * COS + HALF * SIN)};
    }
    /** Where the grey wedge's arc meets the gap, dp from the point: {out, up}. */
    static float[] greyEnd() {
        double t = Math.sqrt(WEDGE * WEDGE - HALF * HALF);
        return new float[]{(float) (t * SIN + HALF * COS), (float) (t * COS - HALF * SIN)};
    }
    /** How far ({@code out}, {@code up}) is from the ray {@code degrees} from the side edge, dp, toward the foot. */
    private static double below(double out, double up, double degrees) {
        double a = Math.toRadians(degrees);
        return out * Math.cos(a) - up * Math.sin(a);
    }

    /** What ({@code sx}, {@code sy}) is over: {@link #LEAVE}, {@link #SIZE}, {@link #SHADE}, a tool, {@link #NONE} or {@link #OUTSIDE}. */
    int hit(float sx, float sy) {
        if (Math.hypot(sx - x, sy - y) < LEAVE_R) return LEAVE;
        int strip = stripHit(sx, sy);
        return strip != OUTSIDE ? strip : fanHit(sx, sy);
    }
    /** As {@link #hit} but for the X: the fan's own parts. */
    int fanHit(float sx, float sy) {
        float[] p = local(sx, sy);
        float dx = p[0], dy = p[1];
        double r = Math.hypot(dx, dy);
        if (r > OUTER + REACH || dx < -MARK_ROOM || dy < -MARK_ROOM) return OUTSIDE;
        // Beside the quarter's edges, only the wedges' marker rooms; the tools take a tap a little past their pictures, the nearest by angle.
        boolean behind = dx < 0 || dy < 0;
        if (r >= LINE) return behind ? OUTSIDE : tool(Math.min(items() - 1, (int) (Math.toDegrees(Math.atan2(dx, dy)) * items() / 90)));
        // The ring: its previews, their tabs and a little white each side.
        if (r > WEDGE) return behind ? OUTSIDE : r >= WEDGE + CLEAR / 2 ? PAGES : NONE;
        // Across the gap's middle, dp: its half each side hits nothing.
        double off = below(dx, dy, SPLIT);
        if (Math.abs(off) < HALF) return NONE;
        // Short of each wedge's corner, nothing (the X's target is there).
        if (off < 0) return dy < 0 ? OUTSIDE : dy < wedgeStart() ? NONE : SIZE;
        if (dy < 0) return OUTSIDE;
        // Under the grey wedge: its marker's room beside its lower edge, then the row's (its buttons take their own touches).
        return r >= greyStart() && below(dx, dy, LOW) < MARK_ROOM ? SHADE : NONE;
    }
    /** How many previews the ring holds: as many as the page bar's spacing fits round it. */
    static int previews() { return (int) ((Math.PI / 2 * RING + PREVIEW_GAP) / (PREVIEW_W + PREVIEW_GAP)); }
    /** Preview {@code slot}'s middle, degrees from the side edge: the window's first next to it. */
    static double previewAngle(int slot) { return (slot + .5) * 90 / previews(); }
    /** The ring's preview nearest ({@code sx}, {@code sy}) by angle: the ring is one target, as the bar's strip is. */
    int preview(float sx, float sy) {
        float[] p = local(sx, sy);
        return Math.max(0, Math.min(previews() - 1, (int) (Math.toDegrees(Math.atan2(p[0], p[1])) * previews() / 90)));
    }

    /** Page px a dp: both tablets' 300 dpi panels at 160 dp an inch, so a line's page px at 100% are its screen px. */
    static final float PAGE_PX = 300f / 160;
    /**
     * The size wedge's width across at {@code up} dp up the side edge, dp: from the side edge to the gap's side, or to the
     * arc past its widest ({@link #wedgeEnd}). Its marker reads as the line's width there (the owner, 2026-11-04: "it
     * indicates the size of the tool... so it should be closer to the size actually shown").
     */
    static double width(double up) { return up <= wedgeStart() || up >= WEDGE ? 0 : Math.min((up - wedgeStart()) * SIN / COS, Math.sqrt(WEDGE * WEDGE - up * up)); }
    /** The broadest line the wedge shows true, page px: its width where widest. */
    static double truest() { return wedgeEnd()[0] * PAGE_PX; }
    /**
     * Where size {@code px}'s marker sits, dp from the point up the side edge: where the wedge is as wide as the line at
     * 100% ({@link #width}); a broader one, past the widest, on the rest of the edge on the toolbar's log scale up to its
     * broadest ({@link ModularSizes#MAX}).
     */
    static float sizeUp(double px) {
        double most = truest(), top = wedgeEnd()[1];
        if (px <= most) return (float) (wedgeStart() + Math.max(0, px) / PAGE_PX * COS / SIN);
        return (float) (top + (WEDGE - top) * Math.min(1, Math.log(px / most) / Math.log(ModularSizes.MAX / most)));
    }
    /** The size at ({@code sx}, {@code sy}), page px: {@link #sizeUp}'s inverse at its height, to the toolbar slider's nearest step ({@link ToolSwatches#sizeAt}). */
    double size(float sx, float sy) {
        double up = Math.min(WEDGE, local(sx, sy)[1]), most = truest(), top = wedgeEnd()[1];
        double px = up <= top ? (up - wedgeStart()) * SIN / COS * PAGE_PX : most * Math.pow(ModularSizes.MAX / most, (up - top) / (WEDGE - top));
        // At the wedge's corner and below it, the finest: a floor, so the finest sizes stay a tap away.
        return ToolSwatches.sizeAt(ToolSwatches.position(Math.max(ModularSizes.MIN, px)));
    }

    /** The grey wedge's length, dp, and its solid ends' ({@link ShadeStrip#endpoint}). */
    private int length() { return Math.round(WEDGE - greyStart()); }
    private int endpoint() { return ShadeStrip.endpoint(length(), ShadeStrip.END); }
    /** The grey at ({@code sx}, {@code sy}), darkness 0–1: the toolbar strip's {@link ShadeStrip#LEVELS} steps, white at the wedge's corner to black at its arc. */
    double darkness(float sx, float sy) {
        float[] p = local(sx, sy);
        return darknessAt(Math.hypot(p[0], p[1]));
    }
    /** The grey {@code r} dp from the point, darkness 0–1 (the grey wedge's fill). */
    double darknessAt(double r) { return DrawLayout.shadeAt((float) r, greyStart(), length(), endpoint()); }
    /** Where grey {@code darkness}'s marker sits, dp from the point along the wedge's lower edge. */
    float shadeRadius(double darkness) { return greyStart() + length() - ShadeStrip.markerAt(ToolSwatches.tone(darkness), length(), endpoint()); }

    /** The screen point {@code r} dp from the fan's point at {@code degrees} from the side edge toward the foot: {x, y}. */
    float[] point(float r, double degrees) {
        double a = Math.toRadians(degrees);
        return new float[]{px + (right ? -1 : 1) * (float) (r * Math.sin(a)), py - (float) (r * Math.cos(a))};
    }
    /**
     * The column's buttons lent while the fan is open ({@code InkWashBar.lend}): under the grey wedge, a row along the
     * foot, the arc's end's line, Menu, the Dropper, the Lasso and the droplet (the owner, 2026-11-07: "below, aligned in a
     * line aligned with the end of the outer arc, a row of tools"; Menu first, "the same icon (three lines) as the top
     * dropdown menu... to the left of the eye dropper", the fan the app's way in; in the gap between the wedges from 2026-11-06), the layer
     * button over its outer end (the owner, 2026-11-07: "remove the lock and zoom level and place the layers picker where
     * that was. spread out the other icons below").
     */
    static final int MENU = 0, DROPPER = 1, LASSO = 2, WET = 3, LAYERS = 4, TOOL_LINE = 5, CELLS = 6;
    /**
     * dp: the droplet's cell's side, the icons smaller than the column's (the owner, 2026-11-07: "the icons need to be
     * smaller"); Menu's, the Dropper's and the Lasso's, a little larger ("SLIGHTLY larger as well, but very tiny amount"); the
     * layer button's ("the layers is a little too small", then "a tiny bit bigger"); the first cell's middle out along
     * the foot; the layer button's foot up from the foot, just over the row.
     */
    static final float CELL = 26, PICKER = 28, LAYERS_SIDE = 38, FIRST = 58, LAYERS_FOOT = PICKER + 2;
    /** Cell {@code cell}'s size, dp: {wide, high}. */
    static float[] side(int cell) {
        float side = cell == LAYERS ? LAYERS_SIDE : cell == WET ? CELL : PICKER;
        return new float[]{side, side};
    }
    /** How far out a box {@code wide} by {@code high} dp, its foot {@code foot} dp up, has its middle when its far top corner is at the wedges' radius. */
    private static float reaching(float wide, float high, float foot) { return (float) Math.sqrt(WEDGE * WEDGE - (foot + high) * (foot + high)) - wide / 2; }
    /**
     * Cell {@code cell}'s middle, dp from the point: {out, up}. The row's feet on the foot's line, its last with its far
     * corner on the wedges' radius, so as far inside the ring as the ring is inside the arc (the owner, 2026-11-07: "equal
     * distance from the top of the page previews to the outer arc as to the icons nestled underneath the pages"), the
     * row's others evenly between; the layer button over the row midway between the Lasso and the droplet ("roughly in
     * the middle of the lasso and droplet").
     */
    static float[] middle(int cell) {
        if (cell == LAYERS) return new float[]{(middle(LASSO)[0] + middle(WET)[0]) / 2, LAYERS_FOOT + LAYERS_SIDE / 2};
        float last = reaching(CELL, CELL, 0);
        return new float[]{FIRST + cell * (last - FIRST) / WET, side(cell)[1] / 2};
    }
    /**
     * The brush line's place while the fan is open ({@link #TOOL_LINE}; the owner, 2026-11-07: "the space directly to the
     * right of the middle of the stroke preview for text about the current tool... also where any alerts or updates
     * should show"): level with the X's middle, from the X's target to under the arc's foot end, the line's height; screen
     * dp {left, top, right, bottom}, mirrored.
     */
    float[] line() {
        float near = LEAVE_R, far = corner + LINE, half = DrawLayout.BRUSH_LINE / 2f;
        return right ? new float[]{x - far, y - half, x - near, y + half} : new float[]{x + near, y - half, x + far, y + half};
    }
    /**
     * The page strip along the fan's side edge (the owner, 2026-11-07: "rotated -90 degrees along the left side of the fan,
     * aligned with the preview stroke, put in the page controls, lock and zoom amount"): {@link #STRIP} dp across, its
     * middle the X's, from {@link #CLEAR} past the X's target up to the arc's top end; read upward, the page bar's ‹, the
     * page and its count, ›, and at its top end Zoom (its padlock and percentage). Screen dp {left, top, right, bottom}.
     */
    static final float STRIP = 28;
    float[] strip() { return new float[]{x - STRIP / 2, py - LINE, x + STRIP / 2, y - LEAVE_R - CLEAR}; }
    /** An arrow's room along the strip, dp: the bar's caret and a little each side (the bar's 32 dp don't fit the strip). */
    static final float STRIP_ARROW = 28;
    /** The strip's parts' lengths along it from its foot, dp: ‹, the page and its count, ›, then Zoom at its top end, the number and Zoom without the bar's padding. */
    static float[] stripParts() {
        return new float[]{STRIP_ARROW, DrawLayout.pageNumber() - 2 * DrawLayout.PAGE_NUMBER_PAD, STRIP_ARROW, DrawLayout.zoomButton() - 2 * DrawLayout.ZOOM_PAD};
    }
    /** What ({@code sx}, {@code sy}) is over on the strip: {@link #PREVIOUS}, {@link #NEXT}, {@link #ZOOM}, {@link #NONE} (the page's words), or {@link #OUTSIDE}. */
    int stripHit(float sx, float sy) {
        float[] s = strip(), parts = stripParts();
        if (sx < s[0] || sx > s[2] || sy < s[1] || sy > s[3]) return OUTSIDE;
        float along = s[3] - sy;
        if (along < parts[0]) return PREVIOUS;
        if (along < parts[0] + parts[1]) return NONE;
        if (along < parts[0] + parts[1] + parts[2]) return NEXT;
        return along >= s[3] - s[1] - parts[3] ? ZOOM : NONE;
    }

    /** Cell {@code cell}'s box on the screen, dp: {left, top, right, bottom}; mirrored with the column on the right. */
    float[] cell(int cell) {
        if (cell == TOOL_LINE) return line();
        float[] m = middle(cell), size = side(cell);
        float cx = right ? px - m[0] : px + m[0], cy = py - m[1];
        return new float[]{cx - size[0] / 2, cy - size[1] / 2, cx + size[0] / 2, cy + size[1] / 2};
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
        float r = paper();
        for (int k = 0; k < STEPS; k++) {
            double near = Math.toRadians(90.0 * (k + 1) / STEPS), far = Math.toRadians(90.0 * k / STEPS);
            float wide = (float) (r * Math.sin(near)), from = (float) (r * Math.cos(near)), to = (float) (r * Math.cos(far));
            for (int side = -1; side <= 1; side += 2) {
                float top = side < 0 ? py - to : py + from, bottom = side < 0 ? py - from : py + to;
                float[] step = {Math.max(0, px - wide), Math.max(0, top), Math.min(width, px + wide), Math.min(height, bottom)};
                if (step[0] < step[2] && step[1] < step[3]) mask.add(step);
            }
        }
        for (int i = 0; i < CELLS; i++) mask.add(cell(i));
        mask.add(strip()); mask.add(dot());
        return mask;
    }
    /** The dot over the X while the fan is open ({@code PaintActivity.showZenDot}): the X's target's square, dp {left, top, right, bottom}. */
    float[] dot() { return new float[]{x - LEAVE_R, y - LEAVE_R, x + LEAVE_R, y + LEAVE_R}; }

    /** All it draws or takes a touch on, dp: {left, top, right, bottom}: round its point out to its tools' reach, the line and its cells. */
    float[] bounds() {
        float r = OUTER + REACH;
        float[] box = {Math.max(0, px - r), Math.max(0, py - r), Math.min(width, px + r), Math.min(height, py + r)};
        for (int i = 0; i < CELLS; i++) {
            float[] c = cell(i);
            box[0] = Math.min(box[0], c[0]); box[1] = Math.min(box[1], c[1]); box[2] = Math.max(box[2], c[2]); box[3] = Math.max(box[3], c[3]);
        }
        return box;
    }
    /**
     * The paper under the open fan, a disc round its point out past the tools' reach, dp: its edge an arc beside the fan's
     * (the owner, 2026-11-07: "let's use a rounded arc of white instead of the chunky blocky white padding around the
     * images"; the page behind the pictures that morning, a disc round the X from 2026-11-04): the page under the fan hidden
     * while it is open, shown again as it closes.
     */
    static float paper() { return OUTER + REACH; }
}
