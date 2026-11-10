package io.github.mpdairy.monopaint;

import java.util.List;
import java.util.Locale;

/**
 * Every size on the Tools page, in dp, from the screen (the redesign, 2026-11-03: what is changed first, the page's job
 * said in its bar): the bar ({@link LabUi.PageBar}: Back, labelled, at its left; the title "Toolbar" over the one line
 * saying what to do), then on paper with no frames: the <b>slots</b> in one row across the page (2026-11-03, the owner:
 * "with two rows it's not clear what that does"), each a tile of its drawing alone, as the side bar shows it, the
 * selected one inverted ({@link #SLOT_PAD}); the selected slot's tool named in a row with <b>Edit in Brush Lab</b> at its
 * right; the Library: the search and the pages in a row, the filter row's chips under it ({@link ToolsPage.Chip}; a row,
 * or more where the width runs out) and its grid, {@link #COLUMNS} across at most, each card its drawing over its name,
 * both at its left; last, at the foot, the <b>try strip</b> ({@link TestArea#strip}: "Draw here to try Fill in" and
 * Clear over the test page; the owner: "move the preview area to the bottom"). Every drawing is cut at its handle end
 * ({@link DrawLayout#TIP_TRIM}) and scaled to one width, in an art box of one height ({@link #art}): a slot's {@link
 * #slotArt} × {@link #slotArtHeight}, a card's {@link #cardArt} × {@link #cardArtHeight}; a brush with no drawing shows
 * its stroke at that size. In portrait it is all stacked, the rows the Library gets first and the try strip the rest
 * (at least {@link #testLeast}); in landscape the slots' row spans the page, under it the name row over the try strip
 * a column at the left, the Library beside it. Nothing scrolls: the grid is paged. Measures text as {@link LabText}
 * does. Pure, checked on the desktop.
 */
final class ToolsLayout {
    /** The bar; a card's least width (the finger's chooser's cards too). */
    static final int BAR = 56, CARD_LEAST = 142;
    /** The Library's most columns (the owner's mockup: four). */
    static final int COLUMNS = 4;
    /** A line's width ({@link Theme#FINE}): a chip's edge. */
    static final int LINE = 1;
    /** The search's row (the search, or its field, and the pages at its right); a page button's width ("‹", "›"). */
    static final int FIND = 40, PAGE_BUTTON = 32;
    /** A chip's height. */
    static final int CHIP = 30;
    /** An art box's height for its width: the drawings (116 px tall, trimmed about 270 wide) fill it. */
    static final float ART_TALL = .42f;
    /** A card's art of its width (the mockup's 300 px of 456). */
    static final float CARD_ART = .66f;
    /**
     * A slot's tile: its padding round its art, dp, where the selected one's ink fill shows (inverted as a chosen chip,
     * its drawing in paper; the owner, 2026-11-03: "maybe invert it so it's dark? to make it obvious it's selected"),
     * its corners {@link #PAD_RADIUS}; and the row under the tiles for a changed slot's dot.
     */
    static final int SLOT_PAD = 4, DOT_ROW = 10;
    /** The blank between a drawing and its name; between the grid's rows. */
    static final int NAME_GAP = 4, ROW_GAP = 4;
    /** The blank a name keeps at its card's right, so it never runs into the next card's. */
    static final int NAME_END = 8;
    /** Edit in Brush Lab's box's corners' radius. */
    static final int PAD_RADIUS = 5;
    /** The selected slot's tool's row (its name and line, Edit in Brush Lab); the try strip's head row (what it is for, Clear). */
    static final int NAME_ROW = 44, TRY_HEAD = 32;
    /** The brush's name (heading, 20 sp) and its words (caption, 13 sp, grey), and the gap between them: Brush Lab's and the kit sheet's. */
    static final float NAME_SP = TypeStyle.HEADING.sp, WORDS_SP = TypeStyle.CAPTION.sp;
    static final int WORDS_GAP = 4;
    /** The longest first sentence of a brush's words, in characters ({@link BrushVoice#about}; checked): Brush Lab shows it whole. */
    static final int FIRST_SENTENCE = 80;
    /**
     * The words: the bar's title (the page's job, "Tools" until 2026-11-03; Brush Lab's Back from here says it too) and
     * its line; the button opening Brush Lab on the selected slot (Brush Lab in the bar's corner until 2026-11-03); the
     * try strip's head ("Draw here to try Fill in", Brush Lab's too); the search's button; the bar's line while a slot is
     * picked up to move ({@link ToolsPage#tap}).
     */
    static final String TITLE = "Toolbar", JOB = "", MOVE = "Tap where it goes.", LAB = "Edit in Brush Lab", TRY = "Draw here to try ",
            SEARCH = "Search";

    final int width, height;
    /** {@code compact}: the Nomad's sizes; {@code wide}: landscape, the name row and the try strip a column at the left. */
    final boolean compact, wide;
    /** The chips spaced as the Nomad's ({@link #chipPad(boolean)}): there, or where that keeps the filter row one row (the Manta upright since Effects, 2026-11-06). */
    private final boolean tightChips;
    /** The page's margin at its sides and foot; the blank between things; the blank before the Library (portrait) or beside it (landscape). */
    final int side, gap, apart;
    /** The left column's width (the name row, the try strip), and the Library's; the same in portrait. */
    final int top, sections;
    /** The try strip's test page. */
    final int testWidth, testHeight;
    /** A slot's width and height (its tile, the dot's row under it), and the blank between two. */
    final int slotWidth, slotCard, slotGap;
    /** The slots shown, all in the one row: as many as the side column shows ({@link DrawLayout#trays}). */
    final int slotCount;
    /** The chips' rows (all of them shown). */
    final int chipRows;
    /** The grid: columns, a card's width and height, rows, and the room left under it. */
    final int columns, cardWidth, cardHeight, rows, spare;

    ToolsLayout(int width, int height) {
        this.width = width; this.height = height;
        compact = Math.min(width, height) < DrawLayout.WIDE;
        wide = width > height;
        side = compact ? 12 : 24;
        gap = compact ? 10 : 14;
        apart = compact ? 16 : 24;
        if (wide) {
            // Side by side, the paper between: on the Nomad the Library three cards across at their least.
            sections = compact ? 3 * CARD_LEAST + 4 : (width - 2 * side - apart) / 2;
            top = width - 2 * side - apart - sections;
        } else {
            top = width - 2 * side;
            sections = top;
        }
        // Across the whole page, in landscape too: beside the Library ten would be too small to tell apart.
        slotCount = DrawLayout.trays(compact);
        slotGap = compact ? 4 : 6;
        slotWidth = (width - 2 * side - (slotCount - 1) * slotGap) / slotCount;
        slotCard = slotTile() + DOT_ROW;
        testWidth = top;
        columns = LabLayout.clamp(sections / CARD_LEAST, 3, COLUMNS);
        cardWidth = sections / columns;
        cardHeight = cardArtHeight() + NAME_GAP + nameLine() + DrawLayout.MARK;
        tightChips = compact || chipRows(chipLabels(), sections, false) > 1 && chipRows(chipLabels(), sections, true) == 1;
        chipRows = chipRows(chipLabels(), sections, tightChips);
        int below = height - BAR - slotsRow();
        if (wide) {
            // Right: the Library's rows, as many as fit. Left: the try strip takes what the name row leaves.
            int rest = below - libraryFixed() - side;
            rows = Math.max(1, (rest + ROW_GAP) / (cardHeight + ROW_GAP));
            spare = rest - grid(rows);
            testHeight = below - NAME_ROW - TRY_HEAD - side;
        } else {
            // Stacked, the try strip at the foot: at least testLeast; the rows get the rest, and the strip what they leave.
            int rest = below - NAME_ROW - apart - libraryFixed() - gap - TRY_HEAD - side;
            rows = Math.max(1, (rest - testLeast() + ROW_GAP) / (cardHeight + ROW_GAP));
            spare = 0;
            testHeight = rest - grid(rows);
        }
    }
    /** The try strip's test page's least height: a slim strip, 140 dp on the Manta, {@link #TEST_LEAST_COMPACT} on the Nomad. */
    int testLeast() { return compact ? TEST_LEAST_COMPACT : 140; }
    static final int TEST_LEAST_COMPACT = 90;
    /** The slots' row with the blank over and under it, across the page under the bar. */
    int slotsRow() { return gap + slotCard + gap; }
    /** A name's line (13 sp). */
    static int nameLine() { return (int) Math.ceil(LabText.line(TypeStyle.CAPTION.sp)); }
    /** A slot's art box (its tile inside its padding) and a card's (a share of its width), {@link #ART_TALL} of that tall. */
    int slotArt() { return slotWidth - 2 * SLOT_PAD; }
    int slotArtHeight() { return Math.round(slotArt() * ART_TALL); }
    /** A slot's tile: its art box in its padding. */
    int slotTile() { return slotArtHeight() + 2 * SLOT_PAD; }
    int cardArt() { return Math.round(cardWidth * CARD_ART); }
    int cardArtHeight() { return Math.round(cardArt() * ART_TALL); }
    /** The grid's height for {@code rows} rows. */
    int grid(int rows) { return rows * cardHeight + (rows - 1) * ROW_GAP; }
    /** The Library but its grid: the search's row, the chips' rows, the blank over the grid. */
    int libraryFixed() { return FIND + chips() + gap; }
    /** The chips' rows' height. */
    int chips() { return chipRows * CHIP + (chipRows - 1) * chipGap(); }
    /** How many cards a page holds. */
    int perPage() { return columns * rows; }

    // The filter row and the search

    /** A chip's padding either side of its word (the twelve in one row on the Manta upright, {@link #tightChips}); the blank between chips (and their rows). */
    int chipPad() { return chipPad(tightChips); }
    int chipGap() { return chipGap(tightChips); }
    static int chipPad(boolean compact) { return compact ? 8 : 10; }
    static int chipGap(boolean compact) { return compact ? 5 : 7; }
    /** Every chip's label in the filter row, in its order (More Brushes is beside Search: {@link #rowChips}). */
    static String[] chipLabels() {
        ToolsPage.Chip[] chips = rowChips();
        String[] labels = new String[chips.length];
        for (int i = 0; i < chips.length; i++) labels[i] = chips[i].label;
        return labels;
    }
    /** The filter row's chips: every one but More Brushes, the last, which sits beside Search, and a kind no shown tool is under (Odd Ones since 2026-11-06). */
    static ToolsPage.Chip[] rowChips() {
        java.util.List<ToolsPage.Chip> out = new java.util.ArrayList<>();
        for (ToolsPage.Chip c : ToolsPage.Chip.values())
            if (c != ToolsPage.Chip.MORE && (!c.kind() || ToolPresets.shown().stream().anyMatch(p -> ToolsPage.Chip.of(p) == c))) out.add(c);
        return out.toArray(new ToolsPage.Chip[0]);
    }
    /** A chip's width: its word (13 sp, the chosen one's 400 too: it is filled, not bolder), its padding, its edge. */
    static int chipWidth(String label, int pad) { return (int) Math.ceil(LabText.width(label, TypeStyle.CAPTION.sp, 0)) + 2 * pad + 2 * LINE; }
    /** How many rows {@code labels} take as chips across {@code room} dp, each row as many as fit (as {@link LabUi.Wrap}). */
    static int chipRows(String[] labels, int room, boolean compact) {
        int pad = chipPad(compact), gap = chipGap(compact), rows = 1, x = 0;
        for (String label : labels) {
            int w = chipWidth(label, pad);
            if (x > 0 && x + w > room) { rows++; x = 0; }
            x += w + gap;
        }
        return rows;
    }
    /** At the search's right: a page button's width either side of the pages, "‹ 12 / 12 ›". */
    static int pagerWidth() { return 2 * PAGE_BUTTON + (int) Math.ceil(LabText.width("99 / 99", TypeStyle.CAPTION.sp, 0)) + 2 * 4; }

    // The drawings

    /**
     * Where the owner's drawing, {@code aw × ah}, goes in an art box {@code w × h} (any unit): {@link DrawLayout#TIP_TRIM}
     * of its width cut at its handle end and the rest exactly {@code w} wide, so every drawing ends one length; centred
     * top to bottom, the box clipping what runs past it. {left, top, width, height} of the whole drawing, its left past
     * the box's edge by the trim.
     */
    static float[] art(float aw, float ah, float w, float h) {
        float scale = w / (aw * (1 - DrawLayout.TIP_TRIM)), dw = aw * scale, dh = ah * scale;
        return new float[]{w - dw, (h - dh) / 2, dw, dh};
    }

    // The kit sheet (ToolsActivity#openKit)

    /** The sheet's padding, and a step's row. */
    static final int SHEET_PAD = 20, SHEET_ROW = 36;
    /** The sheet's width: 520 dp at most, the page's width inside its sides at least. */
    int sheetWidth() { return Math.min(520, width - 2 * side); }
    /** Where a kit of {@code n} brushes goes, said on its sheet: "Fills the first 3 slots", and "shows" too when it raises the bar's count. */
    static String kitWhere(int n, boolean raises) { return (raises ? "Fills and shows the first " : "Fills the first ") + (n == 1 ? "slot" : n + " slots"); }
    /** The sheet's room for words: its width inside its padding. */
    int sheetRoom() { return sheetWidth() - 2 * SHEET_PAD - 2 * LINE; }
    /** The sheet's height for {@code kit}, dp: the title, the intro wrapped, the steps, the note and the buttons, as laid out. */
    int sheetHeight(ToolKits.Kit kit) {
        float room = sheetRoom();
        int title = LabText.wrap(kit.title(), room, LabText.face(NAME_SP, 0)).size() * (int) Math.ceil(LabText.line(NAME_SP));
        int intro = LabText.wrap(kit.intro, room, LabText.face(TypeStyle.BODY.sp, 0)).size() * (int) Math.ceil(LabText.line(TypeStyle.BODY.sp));
        int note = (int) Math.ceil(LabText.line(WORDS_SP));
        return 2 * SHEET_PAD + 2 * LINE + title + WORDS_GAP + intro + gap + kit.steps() * SHEET_ROW + gap + note + gap + 40;
    }
    /** Whether step {@code i}'s words and its tool's name share its row in {@code room} dp. */
    static boolean stepFits(ToolKits.Kit kit, int i, float room) {
        float job = LabLayout.labelWidth(kit.said(i).toUpperCase(Locale.ROOT)), tool = LabText.width(kit.tool(i), WORDS_SP, 0);
        return job + 12 + tool <= room;
    }

    // The bar, the name row and the try strip

    /** A labelled button's width: an icon, its words (a label, caps, tracked) and its padding (as {@link LabButton}). */
    static int buttonWidth(String label) { return (int) Math.ceil(LabText.width(label.toUpperCase(Locale.ROOT), TypeStyle.LABEL.sp, TypeStyle.LABEL.tracking)) + 22 + 8 + 2 * 12 + 2 * LINE; }
    /** The bar's room for its title and line, centred (2026-11-03, as Brush Lab's): what Back leaves on both sides. */
    int titleRoom() { return width - 2 * side - 2 * (LabLayout.backWidth() + LabLayout.TITLE_GAP); }
    /** The name row's room for the tool's name and line: what Edit in Brush Lab leaves. */
    int nameRoom() { return top - buttonWidth(LAB) - gap; }
    /** As {@link #nameRoom()}, less Reset to Default before Edit in Brush Lab while it shows ({@code reset}): plain words, as Clear ({@code LabButton.quiet}). */
    int nameRoom(boolean reset) { return nameRoom() - (reset ? (int) Math.ceil(LabText.width(DrawLayout.RESET, TypeStyle.CAPTION.sp, 0)) + 16 : 0); }
    /**
     * {@code plain} (a brush's {@link BrushVoice#about}) as Brush Lab shows it: whole sentences while they fit
     * {@code lines} lines of {@code room} dp, at least the first.
     */
    static String fitWords(String plain, float room, int lines) {
        List<String> sentences = ModularWords.sentences(plain);
        String shown = "";
        for (String s : sentences) {
            String more = shown.isEmpty() ? s : shown + " " + s;
            if (!shown.isEmpty() && !LabText.fits(more, room, LabText.face(WORDS_SP, 0), lines)) break;
            shown = more;
        }
        return shown;
    }
}
