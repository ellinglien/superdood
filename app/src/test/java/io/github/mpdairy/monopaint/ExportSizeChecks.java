package io.github.mpdairy.monopaint;

import java.util.Arrays;

public final class ExportSizeChecks {
    public static void main(String[] args) {
        // A Manta notebook's page (the screen less its bars) and a Nomad's.
        check(Arrays.equals(ExportSize.SOCIAL.of(1785, 2387), new int[]{1080, 1444}), "Social: a Manta page 1080 wide");
        check(Arrays.equals(ExportSize.PAGE.of(1785, 2387), new int[]{1785, 2387}), "Page: the page's own size");
        check(Arrays.equals(ExportSize.HIGH.of(1785, 2387), new int[]{3570, 4774}), "High Res: twice the page");
        check(Arrays.equals(ExportSize.SOCIAL.of(2387, 1785), new int[]{1444, 1080}), "Social: a landscape page 1080 on its short side");
        check(Arrays.equals(ExportSize.HIGH.of(2387, 1785), new int[]{4774, 3570}), "High Res: a landscape page twice it");
        check(Arrays.equals(ExportSize.SOCIAL.of(1404, 1872), new int[]{1080, 1440}), "Social: a Nomad screen's page 1080 wide");
        check(Arrays.equals(ExportSize.SOCIAL.of(800, 600), new int[]{800, 600}), "Social: a small page stays its size");
        check(ExportSize.SOCIAL.words(1785, 2387).equals("Social, 1080 × 1444") && ExportSize.PAGE.words(1785, 2387).equals("Page, 1785 × 2387")
                && ExportSize.HIGH.words(1785, 2387).equals("High Res, 3570 × 4774, sharp"), "the PNG menu's rows");
        System.out.println("PASS: Export Page's PNG sizes: on a Manta page Social 1080 × 1444, Page 1785 × 2387, High Res 3570 × 4774, sharp; landscape turned; a small page stays");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
