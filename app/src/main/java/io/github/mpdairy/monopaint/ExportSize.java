package io.github.mpdairy.monopaint;

/**
 * The pixel size of Export Page's PNG, its PNG menu's three (the owner, 2026-11-05): Social, 1080 on the page's short
 * side (never bigger than the page); Page, its own size, the notebook's copy's; High Res, twice it, drawn sharp ({@link
 * PngRender#png}). Sizes keep each page's proportions.
 */
enum ExportSize {
    SOCIAL("Social"), PAGE("Page"), HIGH("High Res");

    /** Social's short side, px. */
    static final int SOCIAL_SIDE = 1080;
    /** High Res's export pixels a page pixel, and its sharp grid ({@link SharpView}). */
    static final int HIGH_SCALE = 2;
    /** The menu's word (docs/TEXT-STYLE.md). */
    final String label;

    ExportSize(String label) { this.label = label; }

    /** {width, height} for a page of that size. */
    int[] of(int width, int height) {
        double factor = this == HIGH ? HIGH_SCALE : this == PAGE ? 1 : Math.min(1, (double)SOCIAL_SIDE / Math.min(width, height));
        return new int[]{Math.max(1, (int)Math.round(width * factor)), Math.max(1, (int)Math.round(height * factor))};
    }
    /** Its row in the PNG menu for a page of that size: "Social, 1080 × 1444", "High Res, 3570 × 4774, sharp". */
    String words(int width, int height) {
        int[] size = of(width, height);
        return label + ", " + size[0] + " × " + size[1] + (this == HIGH ? ", sharp" : "");
    }
}
