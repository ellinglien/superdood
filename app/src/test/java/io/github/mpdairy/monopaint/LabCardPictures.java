package io.github.mpdairy.monopaint;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * A picture of Brush Lab's card column on the Manta, laid out by {@link LabLayout} as {@link LabSettings} and
 * {@link PartCard} build it: the Adjust row, then the modules two to a row in {@link BrushPart#SHOWN_LATER}'s order, each
 * its name, what it holds, its choice's words (cut to one line) and its sample ({@link WildSample#part}), or what a
 * choice with no slider means. A stand-in for the device's views, as {@link ToolsPagePictures} (whose drawing it uses):
 * a plain slider, no glyph (ToolsPagePictures.art doesn't read the part icons' paths), no lock or die. One column a
 * brush, side by side: {@code java LabCardPictures out.png [preset...]} (the test sources, ToolsPagePictures too).
 */
public final class LabCardPictures {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        ToolsPagePictures.regular = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ukulele.ttf"));
        ToolsPagePictures.bold = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ukulele_bold.ttf"));
        ToolsPagePictures.fallback = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ia_writer_duo_regular.ttf"));
        ToolsPagePictures.fallbackBold = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/ia_writer_duo_bold.ttf"));
        String[] names = args.length > 1 ? java.util.Arrays.copyOfRange(args, 1, args.length) : new String[]{"Swell brush", "Vanishing ink"};
        LabLayout l = new LabLayout(LabLayoutChecks.MANTA[0], LabLayoutChecks.MANTA[1]);
        int gutter = 24, top = l.headRow + l.gap + 4 * l.module + 3 * l.moduleGap + 12;
        BufferedImage image = new BufferedImage(px(names.length * (l.column + gutter) + gutter), px(top + 24), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(ToolsPagePictures.PAPER); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int i = 0; i < names.length; i++) column(g, l, FloatChecks.preset(names[i]).copy().normalize(), gutter + i * (l.column + gutter), 12);
        File out = new File(args.length > 0 ? args[0] : "/tmp/lab-cards.png");
        out.getParentFile().mkdirs();
        ImageIO.write(image, "png", out);
        System.out.println(out);
    }

    static int px(double dp) { return ToolsPagePictures.px(dp); }

    static void column(Graphics2D g, LabLayout l, ModularBrush b, int x, int y) throws Exception {
        ToolsPagePictures.label(g, "Adjust", x, y + l.headRow / 2f, ToolsPagePictures.INK);
        ToolsPagePictures.words(g, b.name, false, TypeStyle.CAPTION.sp, x + 90, y + (l.headRow - LabText.line(TypeStyle.CAPTION.sp)) / 2, ToolsPagePictures.GREY_800);
        int my = y + l.headRow + l.gap;
        for (int row = 0; row < l.rows; row++) {
            java.util.List<BrushPart> parts = LabLayout.row(row);
            for (int i = 0; i < parts.size(); i++) module(g, l, b, parts.get(i), row, x + i * (l.moduleWidth + l.moduleGap), my);
            my += l.module + l.moduleGap;
        }
    }

    static void module(Graphics2D g, LabLayout l, ModularBrush b, BrushPart part, int row, int x, int y) throws Exception {
        int w = l.moduleWidth, rule = LabLayout.MODULE_RULE, pad = l.modulePad;
        ToolsPagePictures.box(g, x, y, w, l.module, rule, ToolsPagePictures.INK);
        int hx = x + rule + pad, hy = y + rule + pad;
        g.setFont(ToolsPagePictures.bold.deriveFont(px(LabLayout.NAME_SP * LabText.SCALE) * 1f));
        g.setColor(ToolsPagePictures.INK);
        java.awt.FontMetrics m = g.getFontMetrics();
        g.drawString(ToolsPagePictures.attributed(part.label.toUpperCase(Locale.ROOT), g.getFont(), true, .08f).getIterator(), px(hx), px(hy) + (px(l.glyph) + m.getAscent() - m.getDescent()) / 2f);
        float sy = hy + l.glyph + LabLayout.NAME_GAP;
        for (String line : LabText.wrap(ModularWords.inside(part), l.body, LabText.face(LabLayout.SMALL_SP, 0))) {
            ToolsPagePictures.words(g, line, false, LabLayout.SMALL_SP, hx, sy, ToolsPagePictures.INK);
            sy += LabText.line(LabLayout.SMALL_SP);
        }
        int lower = y + rule + l.rowHead(row) + LabLayout.HEAD_RULE + l.rowGap;
        String choice = ModularWords.choiceWords(b, part);
        float cw = ToolsPagePictures.width(g, choice, true, LabLayout.CHOICE_SP);
        ToolsPagePictures.words(g, ToolsPagePictures.cut(g, choice, true, LabLayout.CHOICE_SP, l.body), true, LabLayout.CHOICE_SP, x + rule + pad + Math.max(0, (l.body - cw) / 2), lower + (l.rowChoice(row) - LabText.line(LabLayout.CHOICE_SP)) / 2, ToolsPagePictures.INK);
        int sTop = lower + l.rowChoice(row) + l.rowGap, sHeight = y + l.module - rule - pad - sTop;
        if (b.part(part).choice.macro == null) {
            ToolsPagePictures.words(g, ModularWords.noMacro(b.part(part).choice), false, TypeStyle.CAPTION.sp, x + rule + pad, sTop + (sHeight - LabText.line(TypeStyle.CAPTION.sp)) / 2, ToolsPagePictures.GREY_800);
            return;
        }
        int sw = px(l.body), sh = px(Math.max(1, sHeight - l.rowMacro(row) - l.rowGap));
        ToolsPagePictures.sample(g, WildSample.part(b, part, sw, sh), sw, sh, px(x + rule + pad), px(sTop));
        // The slider's track and thumb, a stand-in.
        g.setColor(ToolsPagePictures.GREY_600);
        int track = sTop + sHeight - l.rowMacro(row) + LabLayout.LOCK / 2;
        g.fillRect(px(x + rule + pad), px(track), px(l.body), px(1));
        g.fillOval(px(x + rule + pad + l.body / 2 - 6), px(track - 6), px(12), px(12));
    }
}
