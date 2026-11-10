package io.github.mpdairy.monopaint;

/**
 * A tool's stroke as the drawing screen starts one: a brush's ({@link QuickStroke#start}) or the eraser's
 * ({@link EraseBothStroke#pen}), mirrored as the page's mirrors say ({@link MirrorStroke}), a Filled Shape filled at the
 * lift ({@link FilledShape}). The Manta's drawing screen ({@link InkWash}) and the browser's (web/engine's WebEngine, a
 * live page's guest) both start theirs here, so a stroke is the same on both. Pure: no Android.
 */
final class StrokeStart {
    private StrokeStart() { }

    /**
     * {@code brush}'s stroke (null: the eraser, taking only marks of {@code eraseKinds}, {@link EraseKind}; 0 everything) at
     * {@code look}'s px and darkness, mirrored on a page with mirrors that act: Copy Strokes' copies each its change
     * reflected where its brush allows ({@link ModularStroke#laysAgain}), else the same tool at the same size and grey,
     * the page's drying laid first as before any stroke.
     */
    static DrawingStroke start(ToneDocument document, Brush brush, ToolSwatches.Look look, WatercolorSession session, int eraseKinds) {
        DrawingStroke stroke = drawn(document, brush, look, session, eraseKinds);
        // Vector Ink isn't mirrored: a copy drawn by another stroke would be pixels on its lines' layer.
        if (stroke instanceof VectorInk.Pen) return stroke;
        ResolvedBrush resolved = brush == null || !document.mirrors().copies() ? null : ResolvedBrush.of(brush.parts, look.px, look.darkness);
        // Filled Shape: the outline as drawn and mirrored, then its area filled (FilledShape).
        return FilledShape.of(document, brush == null ? null : brush.parts, mirrored(document, stroke, () -> {
            session.settleBefore(document, brush == null ? null : brush.parts);
            if (brush == null) return EraseBothStroke.pen(document, (float) look.px, eraseKinds);
            session.dry(brush.name() + " mirror copy");
            return SteadyHand.of(brush.parts, ModularStroke.start(document, brush.parts, look.px, look.darkness, session));
        }, () -> brush == null ? 2 * look.px : 2 * ModularStroke.reach(resolved) + 8, resolved != null && ModularStroke.laysAgain(resolved)), look.px, look.darkness, session);
    }

    /**
     * What comes before a shape (Shapes) of a slot whose brush is {@code parts}, the Manta's ({@code InkWash.shape}), the
     * browser's ({@code shapeDown}) and a replay's: what settles laid first, a hidden pair shown, no masking fluid, a
     * Drying wash dropped, the layer that brush's strokes go to selected, and its ink the brush's (Second Ink).
     */
    static void shape(ToneDocument document, ModularBrush parts, WatercolorSession session) {
        session.settleBefore(document, session.onWater(document, parts));
        InkWashLayers.showSelected(document);
        document.paintMaskingFluid(false);
        session.dry("shape");
        ModularStroke.selectLayer(document, parts);
        document.shapeInk(ResolvedBrush.second(parts) ? 255 : 0);
    }

    /** As {@link #start}, unmirrored: the eraser's or {@code brush}'s stroke, a Filled Shape's outline filled at the lift. */
    static DrawingStroke stroke(ToneDocument document, Brush brush, ToolSwatches.Look look, WatercolorSession session, int eraseKinds) {
        DrawingStroke stroke = drawn(document, brush, look, session, eraseKinds);
        return brush == null ? stroke : FilledShape.of(document, brush.parts, stroke, look.px, look.darkness, session);
    }

    /**
     * {@code brush}'s stroke ({@link QuickStroke#start}: drawn live, or quick then rendered at lift when live would lag
     * the pen) at {@code look}'s px and darkness, or the eraser's for none, on the selected pair of {@code document}: on
     * its ink half for ink, charcoal and graphite, its wash half for wash and cover. A Drying wash still settling is laid
     * where it is first; Clear Water while paint floats on the pair works on the water ({@link WatercolorSession#onWater}).
     */
    static DrawingStroke drawn(ToneDocument document, Brush brush, ToolSwatches.Look look, WatercolorSession session, int eraseKinds) {
        ModularBrush parts = brush == null ? null : session.onWater(document, brush.parts);
        session.settleBefore(document, parts);
        session.dry(brush != null ? brush.name() + " stroke" : "eraser stroke");
        if (brush == null) return EraseBothStroke.pen(document, (float) look.px, eraseKinds);
        if (VectorInk.is(brush)) return new VectorInk.Pen(document, brush.parts, look.px, look.darkness);
        return QuickStroke.start(document, parts, look.px, look.darkness, session);
    }

    /**
     * {@code stroke} on a page with mirrors that act ({@link Mirrors}): Copy Strokes' copies after it, each laid as its
     * change reflected ({@code reflects}: {@link ModularStroke#laysAgain}) or started by {@code copies}, {@code reach}
     * the most its marks reach past its samples (page px, worked out only then), or Real Mirror's, folded into the kept
     * side ({@link MirrorStroke}; {@code copies} for a stretch past a jump); else the stroke itself.
     */
    static DrawingStroke mirrored(ToneDocument document, DrawingStroke stroke, MirrorStroke.Copies copies, java.util.function.DoubleSupplier reach, boolean reflects) {
        Mirrors mirrors = document.mirrors();
        if (mirrors.reflects()) return new MirrorStroke(document, stroke, mirrors.view(), copies);
        return mirrors.copies() ? new MirrorStroke(document, stroke, mirrors.group(), copies, reach.getAsDouble(), reflects) : stroke;
    }
}
