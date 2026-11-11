package io.github.mpdairy.monopaint;

/**
 * Zen Mode's fan ({@link ZenFan}) on the four screens, both hands, one to nine slots: the X where Zen's icon is, just
 * outside the fan's point; the size wedge along the side edge and the grey wedge beside it in the quarter's upper part,
 * a white gap between them hitting nothing; the ring of page previews past them, as far from them as from the arc; the
 * row of the column's buttons along the foot under them, Zoom over it; the column's tools round the arc in its order (the slots, Fill in, Smudge, Eraser),
 * each its own sector's middle, their pictures' butts on the arc but the Eraser's; the size and grey the toolbar's own
 * steps, round-tripping through their markers; mirrored exactly; 3 cm at most, inside the screen; its line of the
 * column's buttons beside it, clear of it and of the X, inside its bounds (the fast display's mask).
 */
public final class ZenFanChecks {
    public static void main(String[] args) {
        for (int[] s : DrawLayoutChecks.SCREENS)
            for (int slots = 1; slots <= ToolSlots.COUNT; slots++) {
                ZenFan fan = ZenFan.at(s[0], s[1], slots, false), mirror = ZenFan.at(s[0], s[1], slots, true);
                String at = s[0] + "×" + s[1] + ", " + slots + " slots";
                parts(fan, at); parts(mirror, at + ", mirrored");
                fits(fan, s, at); fits(mirror, s, at + ", mirrored");
                mirrored(fan, mirror, s[0], at);
                tucked(fan, at); tucked(mirror, at + ", mirrored");
                row(fan, s, at); row(mirror, s, at + ", mirrored");
                ring(fan, at); ring(mirror, at + ", mirrored");
                lineAndStrip(fan, s, at); lineAndStrip(mirror, s, at + ", mirrored");
                mask(fan, s, at); mask(mirror, s, at + ", mirrored");
            }
        steps();
        // One line width for the fan: its outlines, its arc and the X (Iconoir's 1.5 of 24 at Zen's icon).
        check(Math.abs(ZenFan.STROKE - 1.5 * DrawLayout.ZEN_ICON / 24) < 1e-6, "one stroke width, the X's");
        // At most 3 cm from its point, tools and their bars included (the owner, 2026-11-04); the X a 48 dp target at least.
        // The drawn wedges' arc ends where the gap's side meets it (ZenFanView's outlines).
        float[] end = ZenFan.wedgeEnd(), greyEnd = ZenFan.mirror(end[0], end[1]), back = ZenFan.mirror(greyEnd[0], greyEnd[1]), low0 = ZenFan.mirror(0, 0), low1 = ZenFan.mirror(0, 100);
        // The grey wedge the size's mirror image (the owner, 2026-11-07: "identical shapes"): mirrored twice, back; its lower edge at LOW.
        check(Math.abs(back[0] - end[0]) < .01 && Math.abs(back[1] - end[1]) < .01 && Math.abs(Math.toDegrees(Math.atan2(low1[0] - low0[0], low1[1] - low0[1])) - ZenFan.LOW) < .01, "the grey wedge the size's mirror image");
        // Their outer edge round the X, as the arc (2026-11-07), {@link ZenFan#INNER} from it; the side edge's top on it too.
        check(Math.abs(ZenFan.fromX(end[0], end[1]) - ZenFan.INNER) < .01 && Math.abs(below(fromX(end), ZenFan.SPLIT) + ZenFan.CLEAR / 2) < .01
                && Math.abs(ZenFan.fromX(greyEnd[0], greyEnd[1]) - ZenFan.INNER) < .01 && Math.abs(below(fromX(greyEnd), ZenFan.SPLIT) - ZenFan.CLEAR / 2) < .01
                && Math.abs(ZenFan.fromX(0, ZenFan.WEDGE) - ZenFan.INNER) < .01, "the wedges' arcs round the X, ending on the gap's sides");
        ZenFan manta = ZenFan.at(1024, 1365, ToolSlots.COUNT, false);
        // The arc's top end as far from the Manta's edge as it was (the owner, 2026-11-06), the tools where they were.
        // Round the nib preview's middle, the X's (the owner, 2026-11-07): the arc's top end over it, the sweep short of the foot.
        check(Math.abs(manta.point(ZenFan.LINE, ZenFan.START)[0] - manta.x - ZenFan.STRIP / 2 - ZenFan.CLEAR) < .01 && ZenFan.SWEEP < 90, "the arc round the X from " + ZenFan.CLEAR + " dp past the strip to " + ZenFan.SWEEP + " degrees");
        // Equidistant (the owner, 2026-11-07): the gap between the wedges, the white between them and the previews' tabs, and between the previews and the arc.
        check(ZenFan.RING + ZenFan.PREVIEW_H / 2 + ZenFan.CLEAR == ZenFan.LINE && ZenFan.INNER + ZenFan.CLEAR + ZenFan.PREVIEW_TAB + ZenFan.PREVIEW_H / 2 == ZenFan.RING, "one white space between the wedges, the ring and the arc");
        check(ZenFan.PREVIEW_W == new DrawLayout(1024, 1365).previewWidth() && ZenFan.PREVIEW_H == new DrawLayout(1024, 1365).previewHeight(), "the ring's previews the Manta's page bar's");
        // All 13 previews round the X (the owner, 2026-11-07), the circle as large as that takes; the X a 48 dp target at least.
        check(ZenFan.previews() == 13 && 2 * ZenFan.LEAVE_R >= 48, "13 previews in the ring (" + ZenFan.previews() + ")");
        System.out.println("PASS: Zen Mode's fan on the four screens, both hands, 1 to " + ToolSlots.COUNT + " slots, " + mm(ZenFan.OUTER) + " mm out: the X at Zen's icon, the fan's point tucked " + Math.round(ZenFan.TUCK) + " dp from it, the X's target clear of the fan's; one stroke width; size along the side edge (the slider's log scale) and grey along the foot "
                + "in the quarter's upper part, a white gap " + Math.round(ZenFan.CLEAR) + " dp wide hitting nothing; " + ZenFan.previews() + " page previews in a ring as far from them as from the arc; the column's tools round the arc in its order (slots, Fill in, Smudge, Eraser; "
                + mm(Math.PI / 2 * (ZenFan.LINE + ZenFan.OUTER) / 2 / manta.items()) + " by " + mm(ZenFan.OUTER + ZenFan.REACH - ZenFan.LINE) + " mm each on the Manta), their butts on the arc but the Eraser's; the toolbar's size and grey steps, "
                + "round-tripping through their markers; mirrored exactly; inside the screen; the Dropper, the Lasso and the droplet in a row along the foot under the wedges, the layer button over it, clear of the wedges, the ring and the X, inside its mask; the mask " + manta.mask().size() + " rectangles hugging its paper, strokes beside it showing");
    }

    /** What each part's middle hits, and the order along the arc. */
    static void parts(ZenFan fan, String at) {
        check(fan.hit(fan.x, fan.y) == ZenFan.LEAVE && fan.hit(fan.px, fan.py) == ZenFan.NONE, at + ": the X at Zen's icon, outside the fan's point");
        float mid = (ZenFan.wedgeStart() + ZenFan.WEDGE) / 2;
        double third = Math.toRadians(ZenFan.SPLIT / 3);
        float[] mirrored = ZenFan.mirror(mid * Math.sin(third), mid * Math.cos(third));
        float[] size = fan.apex(mid, ZenFan.SPLIT / 3), shade = local(fan, mirrored[0], mirrored[1]), gap = fan.point(150, ZenFan.SPLIT),
                band = fan.point(ZenFan.INNER + ZenFan.CLEAR / 4, 30), ring = fan.point(ZenFan.RING, 30), row = fan.apex(100, 75);
        check(fan.hit(size[0], size[1]) == ZenFan.SIZE, at + ": the side edge's wedge is the size");
        check(fan.hit(shade[0], shade[1]) == ZenFan.SHADE, at + ": the next wedge is the grey");
        check(fan.hit(gap[0], gap[1]) == ZenFan.NONE && fan.hit(band[0], band[1]) == ZenFan.NONE && fan.hit(row[0], row[1]) == ZenFan.NONE, at + ": the gap, the white past the wedges and under them hit nothing");
        check(fan.hit(ring[0], ring[1]) == ZenFan.PAGES, at + ": the ring is the previews");
        // The gap's width: just past each of its sides, each wedge.
        // The gap's middle a line out from the X (2026-11-07): across it, 140 dp out from the X.
        float half = ZenFan.CLEAR / 2, r = 140, t = ZenFan.TUCKED;
        double s = Math.toRadians(ZenFan.SPLIT);
        float[] up = local(fan, (float) (r * Math.sin(s) - (half + 1) * Math.cos(s)) - t, (float) (r * Math.cos(s) + (half + 1) * Math.sin(s)) - t);
        float[] in = local(fan, (float) (r * Math.sin(s) - (half - 1) * Math.cos(s)) - t, (float) (r * Math.cos(s) + (half - 1) * Math.sin(s)) - t);
        float[] down = local(fan, (float) (r * Math.sin(s) + (half + 1) * Math.cos(s)) - t, (float) (r * Math.cos(s) - (half + 1) * Math.sin(s)) - t);
        check(fan.hit(up[0], up[1]) == ZenFan.SIZE && fan.hit(in[0], in[1]) == ZenFan.NONE && fan.hit(down[0], down[1]) == ZenFan.SHADE, at + ": the gap " + ZenFan.CLEAR + " dp wide");
        // The markers' rooms beside the edges take the pen: the size's beside the side edge, the grey's beside its lower edge.
        float[] beside = ZenFan.mirror(-ZenFan.MARK_ROOM / 2, 100);
        float[] sizeMark = local(fan, -ZenFan.MARK_ROOM / 2, 100), shadeMark = local(fan, beside[0], beside[1]);
        check(fan.hit(sizeMark[0], sizeMark[1]) == ZenFan.SIZE && fan.hit(shadeMark[0], shadeMark[1]) == ZenFan.SHADE, at + ": each marker's room beside its edge");
        double last = 0;
        check(fan.items() == fan.slots + 3 && fan.tool(fan.slots) == ToolSlots.FILL && fan.tool(fan.slots + 1) == ToolSlots.SMUDGE && fan.tool(fan.slots + 2) == ToolSlots.ERASER, at + ": Fill in, Smudge and Eraser last");
        for (int item = 0; item < fan.items(); item++) {
            int tool = fan.tool(item);
            double angle = fan.angle(tool);
            check(angle > last && angle < ZenFan.SWEEP, at + ": tool " + tool + " further from the side edge than the one before");
            last = angle;
            float[] tip = fan.point((ZenFan.LINE + ZenFan.OUTER) / 2, angle);
            check(fan.hit(tip[0], tip[1]) == tool, at + ": tool " + tool + "'s picture hits it");
            double step = (ZenFan.SWEEP - ZenFan.START) / fan.items();
            float[] near = fan.point(ZenFan.LINE + 1, ZenFan.START + item * step + .3), far = fan.point(ZenFan.OUTER - 1, ZenFan.START + (item + 1) * step - .3);
            check(fan.hit(near[0], near[1]) == tool && fan.hit(far[0], far[1]) == tool, at + ": tool " + tool + "'s whole sector");
            check(fan.base(tool) == ZenFan.LINE + (tool == ToolSlots.ERASER ? ZenFan.ERASER_GAP : 0) && fan.mark(tool) < ZenFan.OUTER, at + ": tool " + tool + "'s butt on the arc, its bar inside the fan");
        }
        float[] reach = fan.point(ZenFan.OUTER + ZenFan.REACH - 1, fan.angle(0));
        check(fan.hit(reach[0], reach[1]) == 0, at + ": a tool takes a tap a little past its picture");
        float[] beyond = fan.point(ZenFan.OUTER + ZenFan.REACH + 2, 30), behind = local(fan, -(fan.corner + ZenFan.STRIP / 2 + 2), 100);
        check(fan.hit(beyond[0], beyond[1]) == ZenFan.OUTSIDE && fan.hit(behind[0], behind[1]) == ZenFan.OUTSIDE, at + ": past the arc and behind the side edge is outside");
    }
    /** The screen point {@code out} dp along the foot and {@code up} dp up the side edge from the fan's point. */
    static float[] local(ZenFan fan, float out, float up) { return new float[]{fan.right ? fan.px - out : fan.px + out, fan.py - up}; }

    /**
     * Tucked in (the owner, 2026-11-04): the point {@link ZenFan#TUCK} up and in from the X's middle; the X's whole target
     * clear of the fan's own (nothing but the white there).
     */
    static void tucked(ZenFan fan, String at) {
        double reach = Math.hypot(fan.px - fan.x, fan.py - fan.y);
        check(Math.abs(reach - ZenFan.TUCK) < .01 && fan.py < fan.y && (fan.right ? fan.px < fan.x : fan.px > fan.x), at + ": the point " + reach + " dp up and in from the X");
        for (float a = 0; a < 360; a += 3) for (float r = 0; r < ZenFan.LEAVE_R; r += 1) {
            float sx = fan.x + (float) (r * Math.cos(Math.toRadians(a))), sy = fan.y + (float) (r * Math.sin(Math.toRadians(a)));
            int under = fan.fanHit(sx, sy);
            check(fan.hit(sx, sy) == ZenFan.LEAVE && (under == ZenFan.OUTSIDE || under == ZenFan.NONE), at + ": the X's target clear of the fan's at " + sx + "," + sy + " (" + under + ")");
        }
    }

    /**
     * The row (the owner, 2026-11-07: "below, aligned in a line aligned with the end of the outer arc, a row of tools: eye
     * drop, lasso, droplet..."): the Dropper, the Lasso and the droplet out along the foot, their feet on it, the layer
     * button over the droplet (where Zoom was, "remove the lock and zoom level and place the layers picker where that
     * was"); every cell clear
     * of the wedges (past the grey's lower edge), the ring, the X's target, the dot's square and one another, inside
     * {@link ZenFan#bounds} (the fast display's mask), on the screen.
     */
    static void row(ZenFan fan, int[] s, String at) {
        float[] b = fan.bounds(), dot = fan.dot();
        float[][] cells = new float[ZenFan.TOOL_LINE][];
        for (int i = 0; i < ZenFan.TOOL_LINE; i++) cells[i] = fan.cell(i);
        double last = 0;
        for (int i = 0; i < ZenFan.TOOL_LINE; i++) {
            float[] c = cells[i], side = ZenFan.side(i);
            check(c[0] >= 0 && c[1] >= 0 && c[2] <= s[0] && c[3] <= s[1] && Math.abs(c[2] - c[0] - side[0]) < .01 && Math.abs(c[3] - c[1] - side[1]) < .01, at + ": cell " + i + " on the screen " + java.util.Arrays.toString(c));
            check(c[0] >= b[0] && c[1] >= b[1] && c[2] <= b[2] && c[3] <= b[3], at + ": cell " + i + " inside the fan's mask");
            check(!overlap(c, dot), at + ": cell " + i + " clear of the dot over the X");
            for (int j = 0; j < i; j++) check(!overlap(c, cells[j]), at + ": cells " + j + " and " + i + " apart");
            float out = Math.abs((c[0] + c[2]) / 2 - fan.px);
            if (i != ZenFan.LAYERS) {
                // Its foot on the foot's line, its middle further out than the one before.
                check(Math.abs(c[3] - fan.py) < .01 && out > last, at + ": cell " + i + " in the row along the foot, out from the point");
                last = out;
            }
            for (float x = c[0] + .25f; x < c[2]; x += .5f) for (float y = c[1] + .25f; y < c[3]; y += .5f) {
                int hit = fan.hit(x, y);
                float[] p = {Math.abs(x - fan.px), fan.py - y};
                check((hit == ZenFan.NONE || hit == ZenFan.SHADE) && ZenFan.mirror(p[0], p[1])[0] <= .01, at + ": cell " + i + " clear of the wedges and the ring at " + x + "," + y + " (" + hit + ")");
            }
        }
        float[] wet = cells[ZenFan.WET], lasso = cells[ZenFan.LASSO], layers = cells[ZenFan.LAYERS];
        // Midway between the Lasso and the droplet, or as near it as keeps it inside the ring (round the X since 2026-11-07).
        float layersMiddle = (layers[0] + layers[2]) / 2, lassoMiddle = (lasso[0] + lasso[2]) / 2, wetMiddle = (wet[0] + wet[2]) / 2;
        check(layers[3] < Math.min(wet[1], lasso[1]) && layersMiddle >= Math.min(lassoMiddle, wetMiddle) && layersMiddle <= Math.max(lassoMiddle, wetMiddle), at + ": the layer button over the row, between the Lasso and the droplet");
        // As far inside the ring as the ring is inside the arc (the owner, 2026-11-07): the droplet's far top corner on the wedges' radius, the rest inside it.
        for (int i = 0; i < ZenFan.TOOL_LINE; i++) {
            float[] c = cells[i];
            double far = Math.hypot(Math.max(Math.abs(c[0] - fan.x), Math.abs(c[2] - fan.x)), fan.y - c[1]);
            check(i == ZenFan.WET ? Math.abs(far - ZenFan.INNER) < .01 : far < ZenFan.INNER, at + ": cell " + i + "'s far corner " + far + " dp out, the ring " + ZenFan.CLEAR + " dp past the wedges' radius");
        }
        check(ZenFan.LAYERS_SIDE > ZenFan.PICKER && ZenFan.PICKER > ZenFan.CELL && ZenFan.side(ZenFan.DROPPER)[0] == ZenFan.PICKER && ZenFan.side(ZenFan.LASSO)[0] == ZenFan.PICKER, "the layer button larger than the row's cells, the Dropper and the Lasso a little larger than the droplet");
    }
    /**
     * The brush line right of the X's middle, as far as the arc's foot end, clear of the row and the X's target, inside the
     * fan's mask; the page strip along the side edge, the X's middle its middle, from clear of the X's target to the arc's
     * top end, its parts in order and fitting, each hit as itself, clear of the X's target and the dot.
     */
    static void lineAndStrip(ZenFan fan, int[] s, String at) {
        float[] line = fan.cell(ZenFan.TOOL_LINE), b = fan.bounds(), menu = fan.cell(ZenFan.MENU);
        float near = fan.right ? line[2] : line[0], far = fan.right ? line[0] : line[2];
        check(Math.abs((line[1] + line[3]) / 2 - fan.y) < .01 && Math.abs(Math.abs(near - fan.x) - ZenFan.LEAVE_R) < .01 && Math.abs(Math.abs(far - fan.x) - ZenFan.LINE * Math.sin(Math.toRadians(ZenFan.SWEEP))) < .01
                && line[1] > menu[3] && line[3] <= s[1] && line[0] >= b[0] && line[2] <= b[2] && line[3] <= b[3], at + ": the brush line right of the X's middle to under the arc's foot end " + java.util.Arrays.toString(line));
        float[] strip = fan.strip(), parts = ZenFan.stripParts(fan.pages);
        float length = strip[3] - strip[1];
        check(Math.abs((strip[0] + strip[2]) / 2 - fan.x) < .01 && Math.abs(strip[1] - (fan.y - ZenFan.LINE + ZenFan.CLEAR)) < .01 && strip[0] >= 0 && strip[1] >= 0
                && fan.y - strip[3] >= ZenFan.LEAVE_R && parts[0] + parts[1] + parts[2] + parts[3] <= length, at + ": the strip along the side edge, its parts fitting " + length + " dp");
        float mid = fan.x;
        // The sweep stops short of the foot, the brush line beside the X clear of the ring's last preview and the last tool.
        double last = Math.toRadians(ZenFan.previewAngle(ZenFan.previews() - 1)), tool = Math.toRadians(fan.angle(fan.tool(fan.items() - 1)));
        double previewFoot = ZenFan.RING * Math.cos(last) - (ZenFan.PREVIEW_W / 2 * Math.sin(last) + (ZenFan.PREVIEW_H / 2 + ZenFan.PREVIEW_TAB) * Math.cos(last));
        double toolFoot = ZenFan.LINE * Math.cos(tool) - 12;
        check(fan.y - previewFoot <= line[1] - ZenFan.CLEAR / 2 && fan.y - toolFoot <= line[1], at + ": the brush line clear of the ring's last preview (" + previewFoot + " dp up) and the last tool (" + toolFoot + ")");
        check(fan.hit(mid, strip[3] - parts[0] / 2) == ZenFan.PREVIOUS && fan.hit(mid, strip[3] - parts[0] - parts[1] / 2) == ZenFan.NONE
                && fan.hit(mid, strip[3] - parts[0] - parts[1] - parts[2] / 2) == ZenFan.NEXT && fan.hit(mid, strip[1] + parts[3] / 2) == ZenFan.ZOOM, at + ": ‹, the number, › and Zoom up the strip");
    }
    /** The ring of previews: each slot's middle hits the ring and is that slot, apart as the bar's, between the white round the wedges and the arc's. */
    static void ring(ZenFan fan, String at) {
        int n = ZenFan.previews();
        check(n >= 10, at + ": " + n + " previews");
        double pitch = Math.toRadians((ZenFan.SWEEP - ZenFan.START) / n) * ZenFan.RING;
        // The bar's spacing at their middles, near enough; never touching, even at their inner corners.
        check(Math.abs(pitch - ZenFan.PREVIEW_W - ZenFan.PREVIEW_GAP) < ZenFan.PREVIEW_GAP && pitch * (ZenFan.RING - ZenFan.PREVIEW_H / 2) / ZenFan.RING > ZenFan.PREVIEW_W, at + ": the previews " + pitch + " dp apart, the bar's spacing");
        for (int k = 0; k < n; k++) {
            float[] m = fan.point(ZenFan.RING, ZenFan.previewAngle(k));
            check(fan.hit(m[0], m[1]) == ZenFan.PAGES && fan.preview(m[0], m[1]) == k, at + ": preview " + k + " hits the ring, itself");
        }
        // A preview's outer corners inside the white under the arc, its tab clear of the white round the wedges.
        double outer = Math.hypot(ZenFan.RING + ZenFan.PREVIEW_H / 2, ZenFan.PREVIEW_W / 2), inner = ZenFan.RING - ZenFan.PREVIEW_H / 2 - ZenFan.PREVIEW_TAB;
        check(outer < ZenFan.LINE - ZenFan.CLEAR / 2 && inner >= ZenFan.INNER + ZenFan.CLEAR - .01, at + ": the ring between the white round the wedges and the arc");
    }
    /** How far {out, up} is from the ray {@code degrees} from the side edge, dp, toward the foot. */
    /** A point {out, up} from the wedges' point, as {out, up} from the X. */
    /** The screen point {@code t} dp along the grey wedge's lower edge: the side edge's, mirrored. */
    static float[] lower(ZenFan fan, float t) { float[] m = ZenFan.mirror(0, t); return local(fan, m[0], m[1]); }
    static float[] fromX(float[] p) { return new float[]{p[0] + ZenFan.TUCKED, p[1] + ZenFan.TUCKED}; }
    static double below(float[] p, double degrees) {
        double a = Math.toRadians(degrees);
        return p[0] * Math.cos(a) - p[1] * Math.sin(a);
    }
    /**
     * The fast display's mask while open ({@link ZenFan#mask}): a few rectangles holding all the fan paints (its paper's
     * disc, its gap's buttons) and little else, within 33 dp of the disc, so a stroke beside the arc shows; a rectangle cut by
     * them all ({@code MagnetDrag.minus}) leaves just what is outside every one.
     */
    static void mask(ZenFan fan, int[] s, String at) {
        java.util.List<float[]> mask = fan.mask();
        float r = ZenFan.paper();
        check(mask.size() <= 2 * ZenFan.STEPS + ZenFan.CELLS + 2, at + ": the mask " + mask.size() + " rectangles");
        java.util.List<float[]> parts = new java.util.ArrayList<>();
        for (int i = 0; i < ZenFan.CELLS; i++) parts.add(fan.cell(i));
        parts.add(fan.strip()); parts.add(fan.dot());
        float[] b = fan.bounds();
        for (float px = b[0] + .5f; px < b[2]; px += 3) for (float py = b[1] + .5f; py < b[3]; py += 3) {
            boolean painted = Math.hypot(px - fan.x, py - fan.y) <= r, masked = false, near = Math.hypot(px - fan.x, py - fan.y) <= r + 33;
            for (float[] c : parts) { painted |= in(c, px, py); near |= in(c, px, py); }
            for (float[] m : mask) masked |= in(m, px, py);
            check(!painted || masked, at + ": the fan's paper or a cell at " + px + "," + py + " in the mask");
            check(!masked || near, at + ": the mask at " + px + "," + py + " within 33 dp of the fan's paper or a part");
        }
        // Past the paper, up and out: off the mask, so strokes there show.
        float out = r + 34;
        float[] beside = {fan.x + (fan.right ? -1 : 1) * out * (float) Math.sqrt(.5), fan.y - out * (float) Math.sqrt(.5)};
        boolean free = true;
        for (float[] m : mask) free &= !in(m, beside[0], beside[1]);
        check(free, at + ": a stroke 34 dp past the paper shows");
        // A patch cut by the whole mask: its parts are exactly its points outside every rectangle.
        java.util.List<int[]> rects = new java.util.ArrayList<>();
        for (float[] m : mask) rects.add(new int[]{Math.round(m[0]), Math.round(m[1]), Math.round(m[2]), Math.round(m[3])});
        int[] patch = {Math.round(b[0]) - 20, Math.round(b[1]) - 20, Math.round(b[2]) + 20, Math.round(b[3]) + 20};
        java.util.List<int[]> pieces = MagnetDrag.minus(patch, rects);
        for (int px = patch[0]; px < patch[2]; px += 4) for (int py = patch[1]; py < patch[3]; py += 4) {
            boolean outside = true;
            for (int[] m : rects) outside &= !(px >= m[0] && px < m[2] && py >= m[1] && py < m[3]);
            int holders = 0;
            for (int[] p : pieces) if (px >= p[0] && px < p[2] && py >= p[1] && py < p[3]) holders++;
            check(holders == (outside ? 1 : 0), at + ": the patch cut by the mask at " + px + "," + py + " (" + holders + ")");
        }
    }
    static boolean in(float[] box, float px, float py) { return px >= box[0] && px < box[2] && py >= box[1] && py < box[3]; }
    static boolean overlap(float[] a, float[] b) { return a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]; }

    /** Inside the screen; everything it hits in {@link ZenFan#bounds}. */
    static void fits(ZenFan fan, int[] s, String at) {
        float[] b = fan.bounds();
        // The fan whole on the screen, its tools' reach too (the Nomad and either turn).
        float reach = ZenFan.OUTER + ZenFan.REACH;
        check(fan.y - reach >= 0 && (fan.right ? fan.x - reach >= 0 : fan.x + reach <= s[0]), at + ": the whole fan on the screen");
        check(b[0] >= 0 && b[1] >= 0 && b[2] <= s[0] && b[3] <= s[1], at + ": inside the screen " + java.util.Arrays.toString(b));
        for (float px = 0; px < s[0]; px += 7) for (float py = 0; py < s[1]; py += 7)
            if (fan.hit(px, py) != ZenFan.OUTSIDE) check(px >= b[0] && px <= b[2] && py >= b[1] && py <= b[3] && Math.hypot(px - fan.x, py - fan.y) <= ZenFan.OUTER + ZenFan.REACH,
                    at + ": a hit at " + px + "," + py + " outside bounds() or its paper");
    }

    /** The column on the right: the same fan mirrored across the screen, what it hits, its size and its grey. */
    static void mirrored(ZenFan fan, ZenFan mirror, int width, String at) {
        check(Math.abs(mirror.x - (width - fan.x)) < .01 && mirror.y == fan.y && true, at + ": mirrored X");
        for (float px = 0; px < width; px += 5) for (float py = fan.y - ZenFan.OUTER - ZenFan.REACH - 10; py < fan.y + 40; py += 5) {
            float qx = width - px;
            check(fan.hit(px, py) == mirror.hit(qx, py) && fan.size(px, py) == mirror.size(qx, py) && fan.darkness(px, py) == mirror.darkness(qx, py) && fan.preview(px, py) == mirror.preview(qx, py), at + ": mirrored at " + px + "," + py);
        }
    }

    /** The size and grey are the toolbar's: its slider's sizes, its strip's greys, each through its marker and back. */
    static void steps() {
        ZenFan fan = ZenFan.at(1024, 1365, ToolSlots.COUNT, false);
        float[] corner = local(fan, 0, ZenFan.wedgeStart()), tips = local(fan, 0, ZenFan.WEDGE);
        check(fan.size(corner[0], corner[1]) == ToolSwatches.sizeAt(0) && fan.size(tips[0], tips[1]) == ToolSwatches.sizeAt(1), "size: finest at the wedge's corner, broadest at its top");
        // Each of the toolbar's steps: its marker on the slider's log scale up the wedge, and back.
        double was = 0, wasUp = 0;
        for (int i = 0; i <= 400; i++) {
            double px = ToolSwatches.sizeAt(i / 400.0);
            float up = ZenFan.sizeUp(px);
            check(up >= wasUp && up <= ZenFan.WEDGE, "size " + px + "'s marker no lower than a finer one's");
            wasUp = up;
            float[] at = local(fan, 0, up);
            check(fan.size(at[0], at[1]) == px, "size " + px + " through its marker and back (" + fan.size(at[0], at[1]) + ")");
        }
        // The fine pens' room (the owner, 2026-11-11: "precise small sizes"): 1.5 to 10 px about a third of the wedge, 7 mm or more.
        float fine = ZenFan.sizeUp(10) - ZenFan.sizeUp(ModularSizes.MIN);
        check(ZenFan.sizeUp(ModularSizes.MIN) == ZenFan.wedgeStart() && ZenFan.sizeUp(ModularSizes.MAX) == ZenFan.WEDGE && fine * 25.4 / 160 >= 7,
                "sizes 1.5 to 10 px " + mm(fine) + " mm of the wedge, the finest at its corner, the broadest at its top");
        for (float up = ZenFan.wedgeStart(); up <= ZenFan.WEDGE; up += .5f) {
            float[] p = local(fan, 0, up);
            double px = fan.size(p[0], p[1]);
            check(px >= was, "size grows up the side edge");
            was = px;
        }
        float[] white = lower(fan, ZenFan.wedgeStart()), black = lower(fan, ZenFan.WEDGE);
        check(fan.darkness(white[0], white[1]) == 0 && fan.darkness(black[0], black[1]) == 1, "grey: white at the wedge's corner, black at its arc");
        java.util.Set<Double> greys = new java.util.TreeSet<>();
        was = -1;
        for (float r = ZenFan.wedgeStart(); r <= ZenFan.WEDGE; r += .25f) {
            float[] p = lower(fan, r);
            double d = fan.darkness(p[0], p[1]);
            check(d >= was, "grey darkens outward");
            was = d;
            greys.add(d);
        }
        check(greys.size() >= ShadeStrip.LEVELS - 1, "grey: the strip's steps (" + greys.size() + " of " + ShadeStrip.LEVELS + ")");
        for (double d : greys) {
            float[] p = lower(fan, fan.shadeRadius(d));
            check(fan.darkness(p[0], p[1]) == d, "grey " + d + " through its marker and back");
        }
    }

    /** {@code dp} in mm, one decimal (160 dp an inch). */
    static String mm(double dp) { return String.format(java.util.Locale.ROOT, "%.1f", dp * 25.4 / 160); }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
