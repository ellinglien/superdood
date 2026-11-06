package io.github.mpdairy.monopaint;

/**
 * A raster finer than the page over part of it (sharp dry brushes, docs/superpowers/specs/2026-11-04-vector-dry-brushes-design.md
 * section 3): {@link #k} points across each page pixel, {@link #width} × {@link #height} of them from fine point
 * ({@link #ox}, {@link #oy}), on a page {@link #pageWidth} × {@link #pageHeight}. A stroke's geometry stays in page
 * units; only where its marks are laid is this grid: fine point {@code x}'s centre is page position
 * {@code (x + ox + .5) / k}. With {@code k} 1 and no offset the points are the page's pixels. Pure.
 */
final class FineGrid {
    /** Fine points a page pixel holds each way: 1, 2, 4 or 8. */
    final int k;
    /** The first point, in fine points from the page's corner; the grid's size in points; the page's in pixels. */
    final int ox, oy, width, height, pageWidth, pageHeight;

    FineGrid(int k, int ox, int oy, int width, int height, int pageWidth, int pageHeight) {
        if (k < 1 || k > 8 || Integer.bitCount(k) != 1 || width < 1 || height < 1) throw new IllegalArgumentException("A fine grid is 1, 2, 4 or 8 to a pixel");
        this.k = k; this.ox = ox; this.oy = oy; this.width = width; this.height = height; this.pageWidth = pageWidth; this.pageHeight = pageHeight;
    }
    /** Page pixels {@code x, y, w × h} at {@code k} points to the pixel. */
    static FineGrid over(int k, int x, int y, int w, int h, int pageWidth, int pageHeight) { return new FineGrid(k, x * k, y * k, w * k, h * k, pageWidth, pageHeight); }

    /** A page position (continuous, px) as a grid one. */
    double gridX(double pageX) { return pageX * k - ox; }
    double gridY(double pageY) { return pageY * k - oy; }
    /** Where grid point {@code x}'s centre falls among the page's pixel centres: pixel {@code i}'s centre is {@code i}. */
    double pixelX(int x) { return (x + ox + .5) / k - .5; }
    double pixelY(int y) { return (y + oy + .5) / k - .5; }
    /** The page pixel grid point {@code x} lies in. */
    int pageOf(int x) { return Math.floorDiv(x + ox, k); }
    int pageOfY(int y) { return Math.floorDiv(y + oy, k); }
    /** The page pixels the grid covers, {left, top, right, bottom} inclusive. */
    int[] pageRect() { return pageRectOf(new int[]{0, 0, width - 1, height - 1}); }
    /** The page pixels grid points {@code r} ({left, top, right, bottom} inclusive) lie in. */
    int[] pageRectOf(int[] r) { return new int[]{Math.floorDiv(r[0] + ox, k), Math.floorDiv(r[1] + oy, k), Math.floorDiv(r[2] + ox, k), Math.floorDiv(r[3] + oy, k)}; }
}
