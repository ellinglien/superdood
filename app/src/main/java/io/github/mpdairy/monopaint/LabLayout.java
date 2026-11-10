package io.github.mpdairy.monopaint;

/**
 * Every dp budget of Brush Lab for a root of width × height dp (the design pass, docs/archive/design-pass-2026-10-23.md, "Brush
 * Lab"): the bar; then a band in a 1 dp ink frame: <b>About This Brush</b> (a paper block: the loop, the name, the words,
 * the picker box lying across, the brush's buttons), then the column (<b>Adjust</b>: the part modules) beside the right
 * side (the <b>try strip</b>, the Tools page's: the test page), both under a {@link #HEAD_ROW} so they start level, and <b>Randomize</b>
 * across the foot. No toolbar band (the owner, 2026-10-29: "whatever toolbar slot was selected at the beginning should
 * flow through. that will give the brush lab some room"), no Basic (always what Advanced was), no Tweak It. Nothing in
 * the frame scrolls, and no text in the column or on a card is cut or smaller than 12 sp: the budgets leave room for the
 * longest words wrapped ({@link LabText}; LabLayoutChecks tries them all). Pure, checked on the desktop.
 */
final class LabLayout {
    /** The bar (no rule under it); a head row; a lock or die; below WIDE dp wide, the Nomad's sizes; a section's label row. */
    static final int BAR = 56, HEAD_ROW = 44, LOCK = 28, WIDE = 900;
    /**
     * A part module (owner's B5e design): the room its border had (2 dp); the room the rule under its head had (3 dp); the gap between the
     * name and the description. (The pattern strip's room went to the words with the design pass, 2026-10-23.)
     */
    static final int MODULE_RULE = 2, HEAD_RULE = 3, NAME_GAP = 3;
    /** Its type: the name (bold caps, tracked .08), the description and end words (caption), the choice (16 sp, down to 13). */
    static final float NAME_SP = 14, SMALL_SP = 13, CHOICE_SP = 16;
    /**
     * The widest choice words each part's module shows ({@link ModularWords#choiceWords}), in em (duospace: an "m" or
     * a "w" 0.9, {@link LabText}): Medium's "Messy charcoal", Drying's "Slow 10.2 s", Carry's "Smear"; Control's ("tilt→turns nib, press→tines open", which may break after an arrow)
     * always take three lines, its one strongest response two on the Nomad ({@link #handResponses}). LabLayoutChecks
     * proves no brush's words are wider.
     */
    static double choiceEm(BrushPart part) {
        switch (part) {
            case MEDIUM: return 9;
            case LIFE: case LATER: return 7.8;
            case HAND: return Double.POSITIVE_INFINITY;
            case PAPER: return 6;
            case TIP: return 5.4;
            default: return 3.3;
        }
    }
    /**
     * Advanced's text, sp: dial labels, values and segments, the slim modules, the open card's head and the Control
     * grid. Never smaller (the owner: 12 sp at least; words wrap rather than shrink or cut; LabLayoutChecks).
     */
    static final float DENSE_SP = 12;
    /**
     * The open card's head row (its glyph, name, lock, die and ✕; the choice shows in its slim module above and its
     * swap strip beside, so the card repeats neither); a dial row's rule under it; a segment's height and its padding
     * either side of its word; the widest value (six characters, two of them wide: "9.8 mm"; LabLayoutChecks tries
     * them all).
     */
    static final int OPEN_HEAD = 30, ROW_RULE = 2, SEGMENT = 28, SEGMENT_PAD = 3;
    /**
     * Grey Shift's set ({@code GreySet}): a grey's width while the brush has the toolbar's four or fewer (Any's sixteen
     * share the row), the gap between greys, the room over and under each square, dp; a grey left out is a smaller
     * square, this share of its side in from each edge.
     */
    static final int GREY_WIDE = 44, GREY_GAP = 4, GREY_PAD = 3;
    static final float GREY_OUT = .3f;
    static final String VALUE_WIDEST = "9.8 mm";
    /**
     * "This brush": its line spacing; the longest first two sentences and the longest description, in characters
     * ({@link BrushVoice#about}; checked).
     */
    static final float WORDS_SPACING = 1;
    static final int TWO_SENTENCES = 150, DESCRIPTION = BrushVoice.ABOUT_MOST;
    /** A section's header row with buttons at its right (a part's choices): a 14 sp label, 7 dp over and under. */
    static final int SECTION_BAR = 36;
    final int width, height, main;
    /**
     * The column's rows of modules: three, two to a row ({@link BrushPart#SHOWN_LATER}: After's on every brush, no Paper since
     * its Texture went on Medium's card, 2026-11-01; four that morning); the modules, slim modules and open card share what's left between them.
     */
    final int rows;
    /** {@code compact}: the Nomad's smaller sizes. */
    final boolean compact;
    /** Whether a module's head shows what the part holds under its name (not on the Nomad: no room at 13 sp). */
    final boolean described;
    /** The band's padding at its top, sides and bottom; the gap between the column and the right side; the gap between blocks. */
    final int top, side, bottom, split, gap;
    final int column, right;
    /**
     * The column's and the right side's head rows (Adjust; "Draw here to try …" and Clear), one height, so the modules
     * and the test page start level.
     */
    final int headRow;
    /** About This Brush's header: its buttons, no label (the owner, 2026-10-29); 36 dp on the Nomad, whose column has no more room. */
    final int aboutHead;
    /**
     * About This Brush: its header ({@link #HEAD_ROW}: the brush's buttons from its left, no label) and its paper block, {@code about} tall: inside its padding ({@link #pad}) the loop ({@code loopWidth} wide),
     * the name and the words ({@code wordsWidth} wide, {@code words} tall for {@code wordsLines} lines, under the name's
     * {@link #nameHeight}; shown only while the brush is tapped, 2026-11-03, their room kept), the readout and the picker
     * box at its right ({@link #picker} long, {@link #pickerThick} tall, against the edge), a note's line under it.
     */
    final int about, loopWidth, wordsWidth, words, wordsLines, nameWidth;
    /** The line under the name: "Edited" and a suggested name, a caption's line. */
    final int mark;
    /** The Open sheet's library: its cards across and down a page, each {@link #cardHeight} tall. */
    final int shelfColumns, shelfRows;
    /** The gap between the loop and the name and words. */
    static final int LOOP_GAP = 8;
    /** The name's room: on the Manta two lines of {@link BrushLabActivity#TWO_LINE_SP} for a long one (the owner: "because i love the names"), on the Nomad a heading's line. */
    int nameHeight() { return (int) Math.ceil(compact ? LabText.line(TypeStyle.HEADING.sp) : Math.max(LabText.line(TypeStyle.HEADING.sp), 2 * LabText.line(16))); }
    /** Randomize, across the foot: big (the owner's), plain words, its dice. */
    final int randomize;
    /**
     * The column: the modules, two to a row, {@code moduleGap} apart; a part open, the slim modules and the
     * open card under them; {@code pad}, an open card's reach either side and a block's padding. {@code spare} is the
     * blank under the modules.
     */
    final int pad, moduleGap, spare;
    /** "This brush"'s words' size, sp. */
    final float wordsSp;
    /** A module's width; its body inside its padding. */
    final int inner, moduleWidth, modulePad, body;
    /**
     * A module: its white head (the name and the glyph at its right in a row, the fixed description across under them),
     * the tallest head and the most lines a description takes ({@link #rowHead}: a row's heads are as tall as its longer
     * description needs, and its choice's box as its wider words need, one line or two: {@link #rowChoice}; the sample
     * gets the rest); the slider and the end words under it; the sample's least and most, as the tallest head and
     * choice leave it.
     */
    final int glyph, glyphGap, descLines, head, choiceHeight, rowGap, sliderHeight, macro, sampleMin, sampleMax, module;
    /** The tallest head, choice and slider with its end words of any row together: what leaves the least sample. */
    final int rowMost;
    /** A module's sample, dp. */
    final int sampleWidth, sampleHeight;
    /**
     * A part open: a slim module (name, lock, its choice in {@code slimLines} lines of {@link #DENSE_SP}), the
     * open card's width and height.
     */
    final int slim, slimLines, slimChoice, slimPad, openWidth, open;
    /** A swap tile's least height's measure (a card's, as the library's were). */
    final int cardHeight;
    /** A page button's width (a swap strip's pages). */
    final int pageButton;
    /**
     * The test area and the test page in it (inside its 2 dp band); the room a part's swap panel has ({@code swap}: what
     * the shelf under the test page had with Tweak It, so its tiles keep their size).
     */
    final int testWidth, testHeight, padHeight, swap;
    /** Clear's padding either side (no wet drop beside it since 2026-11-03: the test page's paint dries by itself). */
    final int clearPad;
    /**
     * The drawing screen's picker box ({@link PickerBox}) lying across About This Brush's right, where the four sizes and
     * the grey bar were: {@code picker} long (their width), {@code pickerThick} across, two cells of {@code pickerCell}
     * (a 28 dp square, 24 on the Nomad, and its tab's room).
     */
    final int picker, pickerCell, pickerThick;
    /**
     * The drawing screen's readout ({@link SwatchReadout}) beside the picker box, at its left (the side bar's over it,
     * turned as the box is; the owner, 2026-10-29: "beside the picker show the preview size / shape as in the toolbar";
     * 2026-11-03: "put the size preview on the left instead of the right, so the picker can go against the side"):
     * a square as thick as the box.
     */
    final int readout;
    /** The try strip's head row ({@link TestArea#strip}) at the least: what it is for, a short name whole ("Draw here to try Ballpoint") and Clear. */
    int testHead() { return (int) Math.ceil(LabText.width(ToolsLayout.TRY + "Ballpoint", TypeStyle.CAPTION.sp, 0) + 8 + labelWidth("Clear") + 2 * clearPad); }
    /** A dial row: its label, value and slider; the Control grid's row names. */
    final int label, value, slider, handName;

    /** Three rows: six modules, After's on every brush, the only place a Later is chosen, and no Paper (its Texture on Medium's card; 2026-11-01). */
    LabLayout(int width, int height) { this(width, height, 3); }
    /** {@code rows} of modules, 3 or 4 ({@link #rows}). */
    LabLayout(int width, int height, int rows) {
        this.width = width; this.height = height; this.rows = rows;
        compact = width < WIDE;
        main = height - BAR;
        top = compact ? 6 : 8; side = compact ? 10 : 12; bottom = compact ? 6 : 8; split = compact ? 12 : 16; gap = compact ? 6 : 10;
        headRow = compact ? 28 : 36;
        aboutHead = compact ? 36 : HEAD_ROW;
        column = compact ? 354 : 404;
        right = width - 2 * side - column - split;
        pad = compact ? 6 : 8;
        inner = column;
        // About This Brush: the picker box at the right; the loop and the name and words beside it; at least the longest
        // first sentence (two sentences where they fit, ToolsLayout#fitWords).
        clearPad = compact ? 10 : 14;
        pickerCell = (compact ? 24 : DrawLayout.FACE_SQUARE) + DrawLayout.FACE_GAP / 2 + DrawLayout.sliderMarkRoom();
        pickerThick = 2 * pickerCell;
        readout = pickerThick;
        // On the Nomad the readout's room comes from the picker (264 until 2026-10-29), so the words keep theirs.
        picker = compact ? 264 - gap - readout : 344;
        wordsSp = compact ? DENSE_SP : TypeStyle.CAPTION.sp;
        loopWidth = compact ? 48 : 90;
        wordsWidth = width - 2 * side - 2 * pad - loopWidth - LOOP_GAP - gap - picker - gap - readout;
        nameWidth = wordsWidth - 40;
        wordsLines = 2;
        words = wordsHeight(wordsLines);
        mark = (int) Math.ceil(LabText.line(TypeStyle.CAPTION.sp));
        about = pad + Math.max(nameHeight() + mark + NAME_GAP + words, pickerThick + mark) + pad;
        shelfColumns = compact ? 4 : 6;
        shelfRows = 2;
        randomize = compact ? 40 : 48;
        // A module: the white head and its rule, the choice, the sample, the slider over its end words.
        moduleGap = 6;
        modulePad = compact ? 4 : 6;
        described = !compact;
        moduleWidth = (inner - moduleGap) / 2;
        body = moduleWidth - 2 * MODULE_RULE - 2 * modulePad;
        // The glyph is as tall as the name's line.
        glyph = (int) Math.ceil(LabText.line(NAME_SP));
        glyphGap = compact ? 6 : 8;
        int lines = 0;
        for (BrushPart part : BrushPart.values()) lines = Math.max(lines, descLines(part));
        descLines = lines;
        head = headOf(descLines);
        int lines2 = 1;
        for (BrushPart part : BrushPart.values()) lines2 = Math.max(lines2, choiceLines(part));
        choiceHeight = choiceHeight(lines2);
        rowGap = 4;
        sliderHeight = LOCK;
        macro = sliderHeight + (int) Math.ceil(LabText.line(SMALL_SP));
        sampleMin = compact ? 24 : 32;
        sampleMax = compact ? 80 : 120;
        int most = 0;
        for (int row = 0; row < rows; row++) most = Math.max(most, rowHead(row) + rowChoice(row) + rowMacro(row));
        rowMost = most;
        int fixed = MODULE_RULE + rowMost + HEAD_RULE + rowGap + rowGap + rowGap + modulePad + MODULE_RULE;
        // Control's words name its one strongest response on a slim module ({@link PartCard}): two lines. In the Nomad's
        // four rows the slim modules' padding over and under is 2 dp, not 4, for Ink's and Smear's open cards' rows.
        slimLines = 2;
        slimPad = compact && rows > 3 ? 2 : 4;
        slimChoice = (int) Math.ceil(slimLines * LabText.line(DENSE_SP));
        slim = MODULE_RULE + slimPad + LOCK + 2 + slimChoice + slimPad + MODULE_RULE;
        openWidth = inner + 2 * pad;
        // The column: under the bar, the band: About This Brush's header and block, the head row (Adjust, Start from), the
        // modules, Randomize, its padding.
        int rest = main - top - aboutHead - about - gap - headRow - gap - gap - randomize - bottom;
        // A part open: the six (seven) as slim modules, then the open card, across the column's padding too.
        int restOpen = rest - (rows * slim + (rows - 1) * moduleGap) - pad;
        module = clamp((rest - (rows - 1) * moduleGap) / rows, fixed + sampleMin, fixed + sampleMax);
        spare = rest - rows * module - (rows - 1) * moduleGap;
        open = restOpen;
        sampleWidth = body;
        sampleHeight = module - fixed;
        cardHeight = compact ? 128 : 148;
        pageButton = 44;
        // What the shelf under the test page had with Tweak It's six under it (gone 2026-10-29), so the tiles keep their size.
        swap = compact ? 312 : 289;
        testWidth = right;
        testHeight = rest;
        padHeight = testHeight - 2 * MODULE_RULE;
        label = compact ? 119 : 114;
        value = (int) Math.ceil(LabText.width(VALUE_WIDEST, DENSE_SP, 0));
        slider = openWidth - 16 - label - value - 8 - 2 * LOCK - 12;
        float names = 0;
        for (String name : BrushLab.HAND_ROWS) names = Math.max(names, LabText.width(name.toUpperCase(java.util.Locale.ROOT), DENSE_SP, LABEL_TRACKING));
        handName = (int) Math.ceil(names);
    }

    /** The parts of row {@code row} of the modules: two, or After alone in the fourth. */
    static java.util.List<BrushPart> row(int row) { return BrushPart.SHOWN_LATER.subList(2 * row, Math.min(2 * row + 2, BrushPart.SHOWN_LATER.size())); }

    /** How many lines {@code part}'s description takes in a module's head. */
    int descLines(BrushPart part) { return LabText.wrap(ModularWords.inside(part), body, LabText.face(SMALL_SP, 0)).size(); }
    /** A module's head with {@code lines} lines of description: its padding, the name's row, the gap, the lines (none on the Nomad). */
    private int headOf(int lines) { return modulePad + glyph + (described ? NAME_GAP + (int) Math.ceil(lines * LabText.line(SMALL_SP)) : 0) + modulePad; }
    /** The heads of the modules in row {@code row} (0–3) of {@link BrushPart#SHOWN_LATER}: as tall as the longer description needs. */
    int rowHead(int row) { int lines = 0; for (BrushPart p : row(row)) lines = Math.max(lines, descLines(p)); return headOf(lines); }
    /** How many lines {@code part}'s choice words take at the least, 13 sp: one, two, or Control's three. */
    int choiceLines(BrushPart part) {
        double em = choiceEm(part);
        return Double.isInfinite(em) ? handResponses() + 1 : em * LabText.em(SMALL_SP) <= body ? 1 : 2;
    }
    /** How many of the hand's strongest responses Control's module names ({@link ModularWords#handWords}): one on the Nomad. */
    int handResponses() { return compact ? 1 : 2; }
    /** The choice's box for {@code lines} lines: one line of 16 sp, or more of 13. */
    static int choiceHeight(int lines) { return (int) Math.ceil(lines == 1 ? LabText.line(CHOICE_SP) : lines * LabText.line(SMALL_SP)); }
    /**
     * How many lines a slider's end words take under it in {@code part}'s module: one, both at its ends, 6 dp apart at
     * least; or two (the Nomad's Control: "steady", then "responsive" at the right), never smaller.
     */
    int endLines(BrushPart part) {
        for (PartChoice c : PartChoice.values())
            if (c.part == part && c.words != null && LabText.width(c.words[0], SMALL_SP, 0) + 6 + LabText.width(c.words[1], SMALL_SP, 0) > body) return 2;
        return 1;
    }
    /** The slider (or the lock and die) and its end words under it, in row {@code row}. */
    int rowMacro(int row) {
        int lines = 0;
        for (BrushPart p : row(row)) lines = Math.max(lines, endLines(p));
        return sliderHeight + (int) Math.ceil(lines * LabText.line(SMALL_SP));
    }
    /** The choice's box in row {@code row}: as tall as the wider words of its two parts need. */
    int rowChoice(int row) { int lines = 0; for (BrushPart p : row(row)) lines = Math.max(lines, choiceLines(p)); return choiceHeight(lines); }

    /** "This brush" with {@code lines} lines of text. */
    int wordsHeight(int lines) { return (int) Math.ceil(lines * LabText.line(wordsSp) * WORDS_SPACING); }
    /** The width a module's name has in its head's first row, left of the glyph (the description has the whole body). */
    int nameRoom() { return body - glyph - glyphGap; }
    /** {@code part}'s name on its module's head: its label, or its short one where the label won't fit {@link #nameRoom}. */
    String name(BrushPart part) { return named(part, NAME_SP, .08f, nameRoom()); }
    /** As {@link #name}, on a slim module ({@link #slimRoom}). */
    String slimName(BrushPart part) { return named(part, DENSE_SP, .07f, slimRoom()); }
    private static String named(BrushPart part, float sp, float tracking, int room) {
        return LabText.width(part.label.toUpperCase(java.util.Locale.ROOT), sp, tracking) <= room ? part.label : part.shortLabel;
    }
    /** The width a slim module's name has, after its glyph and before its lock. */
    int slimRoom() { return moduleWidth - 2 * MODULE_RULE - 12 - 16 - 6 - 6 - LOCK; }
    /** The width a module's noMacro words have (in the sample's place too), beside the lock and die. */
    int macroRoom() { return body - 4 - 2 * LOCK; }
    /** Those words' size: {@link #DENSE_SP} beside the lock and die. */
    float macroSp() { return DENSE_SP; }
    /** The width of a {@link TypeStyle#LABEL} {@code text}, dp. */
    static float labelWidth(String text) { return LabText.width(text, TypeStyle.LABEL.sp, TypeStyle.LABEL.tracking); }
    /** An outlined label button {@code text} with {@code icon} dp of icon and its gap before it, 14 dp padding either side. */
    static int button(String text, int icon) { return button(text, icon, 14); }
    /** The same with {@code pad} dp either side. */
    static int button(String text, int icon, int pad) { return (int) Math.ceil(labelWidth(text) + icon + 2 * pad); }
    /**
     * Back, as Brush Lab's and the Tools page's bars have it ({@link LabButton#back}: the arrow and "Back", 12 dp either
     * side, in a 3 dp ink rule, 44 dp tall; the owner, 2026-10-19: "back button should be clearly visible"), always first
     * at the bar's left.
     */
    static int backWidth() { return button("Back", 22 + 8, 12); }
    /** The padding either side of a bar's or a block's button's words: 14 dp, or 10 on the Nomad. */
    static int barPad(boolean compact) { return compact ? 10 : 14; }
    /** The room between About This Brush's buttons, dp. */
    static final int ACTION_GAP = 6;
    /** About This Brush's header ({@link BrushLabActivity#ACTIONS}, from its left): New ▾, Open, Save (its disk; its widest words, "Already saved"), As New (its box) and Cut Stamp. */
    int aboutButtons() {
        int pad = barPad(compact), room = 0;
        for (String words : new String[]{NEW + " ▾", OPEN, StampNib.CUT})
            room += button(words.toUpperCase(java.util.Locale.ROOT), 0, pad) + ACTION_GAP;
        return room + button(ALREADY.toUpperCase(java.util.Locale.ROOT), 26, pad) + ACTION_GAP + button(AS_NEW.toUpperCase(java.util.Locale.ROOT), 26, pad);
    }
    /**
     * About This Brush's buttons' words: the owner's New, Open and Save (2026-10-29; Save as New became Save's As New, a
     * box beside it, 2026-11-03); the heading of New's styles (Start from ▾'s until then); the line under the name.
     */
    static final String NEW = "New", OPEN = "Open", SAVE = "Save", AS_NEW = "As New", START_FROM = "Start From", EDITED = "Edited", SUGGESTED = "Suggested: ";
    /**
     * Open's sheet (under About This Brush's header): its rows' labels, the pasted code's hint, its buttons, the library's
     * head, what Copy says after; what Open says to a screen reader, open or not, and its Open beside the field; This
     * brush's Brush Studio ({@link DrawLayout#STUDIO}) said in full (2026-11-06, the owner: "should be able to share to the web version").
     */
    static final String PASTE_CODE = "Paste code", CODE_HINT = "brave-onion.M…", THIS_BRUSH = "This brush", COPY = "Copy", CANCEL = "Cancel",
            LIBRARY = "Library", COPIED = "Copied", NO_COPY = "Select the code to copy", OPEN_SAID = "Open a brush", CLOSE = "Close", OPEN_CODE = "Open the code", TO_STUDIO = "Share to Brush Studio";
    /** Open's sheet's padding (across, down), its rows' height, the gap between its rows and between a row's parts, dp. */
    static final int SHEET_SIDE = 16, SHEET_TOP = 12, SHEET_ROW = 44, SHEET_GAP = 10, SHEET_BUTTON_GAP = 8;
    /** Open's sheet's labels' width, dp. */
    int sheetLabel() { return compact ? 120 : 140; }
    /** What Save's button says for a moment after it adds or writes over a brush, or when the library has it as it is (BrushLab's status). */
    static final String SAVED = "Saved", ALREADY = "Already saved";
    /**
     * The bar ({@link LabUi.PageBar}): Back ("Toolbar"), "Brush Lab" centred across the bar (2026-11-03, the
     * owner: "align section name 'BRUSH LAB' in the middle at the top"), so as much room either side as the wider of
     * Back and Undo (its arrow; icon only on the Nomad).
     */
    static int bar(boolean compact) {
        int undo = compact ? 44 : button("Undo", 26), ends = Math.max(button(ToolsLayout.TITLE, 22 + 8, 12), undo);
        return 2 * (ends + TITLE_GAP) + (int) Math.ceil(LabText.width(TITLE, TypeStyle.HEADING.sp, 0));
    }
    /** The room between a bar's words and the buttons either side, dp. */
    static final int TITLE_GAP = 10;
    /** The bar's title, alone: its job line under it (2026-11-03, as the Tools page's) went 2026-11-06 (the owner: "remove the subhead"). */
    static final String TITLE = "Brush Lab";

    /** A dial label's caps' tracking, em. */
    static final float LABEL_TRACKING = .03f;
    /** {@code name} as a dial row shows it: caps, a break allowed after an arrow (a zero-width space; a space when measured). */
    static String dialLabel(String name) { return name.toUpperCase(java.util.Locale.ROOT).replace("↔", "↔\u200b").replace("→", "→\u200b"); }
    /**
     * The label of choice {@code c}'s dial {@code d} in its open card's row: its own ({@link PartChoice#labelOf}), but
     * Print shading's short form ({@link PrintScreen#ROW_LABEL}), which keeps one line beside Ink's seventeen dials.
     */
    static String rowLabel(PartChoice c, PartDial d) { return d == PartDial.SCREEN ? PrintScreen.ROW_LABEL : c.labelOf(d); }
    /** The lines {@code name} takes in a dial row's label column. */
    int labelLines(String name) {
        return LabText.wrap(dialLabel(name).replace("\u200b", " "), label, LabText.face(DENSE_SP, LABEL_TRACKING)).size();
    }
    /** A dial row's least height with a label of {@code lines} lines: its lock and die, or the lines, and its rule. */
    static int rowLeast(int lines) { return Math.max(LOCK, (int) Math.ceil(lines * LabText.line(DENSE_SP))) + ROW_RULE; }
    /** Whether a choice dial's {@code options} fit as segments beside its label, in the slider's and the value's room. */
    boolean segmentsBeside(String[] options) {
        // Each segment's share goes by its word's length (LabSettings.segments), so each must hold its own word.
        float room = slider + 4 + value;
        int total = 0;
        for (String option : options) total += option.length();
        for (String option : options) if (room * option.length() / total - 2 < LabText.width(option, DENSE_SP, 0) + 2 * SEGMENT_PAD) return false;
        return true;
    }
    /**
     * Whether a choice dial shows as a menu button ("darken ▾"): on the Nomad, three options or more that don't fit
     * beside its label (Blend, Line Style), so the open card needn't stack them under it (Ink's eleven dials then fit
     * under About This Brush, 2026-10-23); anywhere, five or more that don't (Screen's: stacked, the shortest would be cut).
     */
    boolean menu(String[] options) { return options.length > (compact ? 2 : 4) && !segmentsBeside(options); }
    /** {@link #menu(String[])} for dial {@code d}, and a Control row's whenever its options don't fit beside it: the Control card's rows are one height. */
    boolean menu(PartDial d, String[] options) { return menu(options) || d.part == BrushPart.HAND && d != PartDial.DIR && !segmentsBeside(options); }
    /**
     * Whether the open card has a row for {@code choice}'s dial {@code d}: every dial that draws, but on the Nomad Screen's
     * own (Cell, Angle and the newsprint three), which its card has no room for beside a medium's own; there Screen picks
     * the print alone, where its row fits (not beside Ink's eleven), then Fling where its row fits too, then Dissolves, then Second Ink,
     * and Tone keeps its Cell and Angle.
     */
    boolean shows(PartChoice choice, PartDial d) {
        if (!ModularFeatures.shown(d)) return false;
        int k = SPARE.indexOf(d);
        if (!compact || choice == PartChoice.TONE || k < 0 && !d.underScreen()) return true;
        if (d.underScreen()) return false;
        // Screen's row first, then Fling's, Dissolves' and Second Ink's: each where the medium's own rows and those before it that show leave room.
        int dials = 0, stacked = 0;
        for (PartDial c : choice.dials) {
            int s = SPARE.indexOf(c);
            if (ModularFeatures.shown(c) && !c.underScreen() && (s < 0 || s == k || s < k && shows(choice, c))) { dials++; if (stacks(c, choice.optionsOf(c))) stacked++; }
        }
        // Medium's card holds Paper's Texture too (the surface spec, 2026-11-01).
        if (choice.part == BrushPart.MEDIUM) dials++;
        return OPEN_HEAD + 10 + stacked * stackedRow() + (dials - stacked) * dialRow(dials, stacked) <= open;
    }
    /**
     * {@code part}'s choice in words as its module says them ({@link PartCard}, the browser's lab): Control's strongest
     * responses, as many as {@link #handResponses} (a slim module's, {@code slim}, its one strongest); a break allowed after an arrow.
     */
    String moduleWords(ModularBrush b, BrushPart part, boolean slim) {
        String words = part == BrushPart.HAND ? ModularWords.handWords(b, slim ? 1 : handResponses()) : ModularWords.choiceWords(b, part);
        return words.replace("→", "→​");
    }
    /** The open card's dials for {@code part} of {@code lab}'s brush, in order ({@link BrushLab#dials} the card has rows for, {@link #shows}); Control's are its grid's. */
    java.util.List<PartDial> openDials(BrushLab lab, BrushPart part) {
        java.util.List<PartDial> dials = new java.util.ArrayList<>(lab.dials(part));
        dials.removeIf(d -> !shows(lab.parts().choice(part), d));
        return dials;
    }
    /** How an open card's dial row shows its dial ({@link LabSettings}): Fast is's switch, a menu button, segments under the label or beside it, a slider. */
    enum RowKind { SWITCH, MENU, STACKED, SEGMENTS, SLIDER }
    RowKind rowKind(PartDial d, String[] options) {
        if (d == PartDial.DIR) return RowKind.SWITCH;
        if (!d.choice()) return RowKind.SLIDER;
        return menu(d, options) ? RowKind.MENU : stacks(d, options) ? RowKind.STACKED : RowKind.SEGMENTS;
    }
    /** Whether {@code d}'s row has its lock and die: Erasable moves only by hand (no roll changes it, {@link ModularStyle#keepErasable}) but Resist's, wax or fluid, which rolls. */
    static boolean rolls(PartChoice c, PartDial d) { return d != PartDial.ERASABLE || c == PartChoice.RESIST; }
    /**
     * The lab for a {@code width × height} dp window where the screen's own would leave no room under its modules (a
     * landscape window: the Manta's lab is portrait only): laid out as tall as it needs, so the window scrolls it, never
     * squeezes it ({@link #spare} at least 0).
     */
    static LabLayout scrolled(int width, int height) {
        LabLayout l = new LabLayout(width, height);
        for (int h = height; l.spare < 0 && h < 4 * height; ) l = new LabLayout(width, h -= Math.min(-40, l.spare));
        return l;
    }
    /**
     * Whether Brush Lab and the Tools page fit a {@code width × height} dp screen (the browser's: a phone's doesn't,
     * the spec's numbers): the Tools page's cards at their least, and the lab's scratch pad at least half the column wide.
     */
    static boolean fits(int width, int height) {
        LabLayout lab = new LabLayout(width, height);
        return new ToolsLayout(width, height).cardWidth >= ToolsLayout.CARD_LEAST && lab.right >= lab.column / 2;
    }
    /** The Nomad's rows past a medium's own, each shown only where it fits, in this order ({@link #shows}). */
    private static final java.util.List<PartDial> SPARE = java.util.Arrays.asList(PartDial.SCREEN, PartDial.FLING, PartDial.SOLUBLE, PartDial.SECOND);
    /** Whether dial {@code d}, with {@code options}, lays its segments under its label: a choice dial whose segments don't fit beside it, and isn't a menu button. */
    boolean stacks(PartDial d, String[] options) { return d != PartDial.DIR && d.choice() && !segmentsBeside(options) && !menu(d, options); }
    /** A choice dial whose segments don't fit beside its label: the label on a line of its own, the segments under it, its rule. */
    static int stackedRow() { return (int) Math.ceil(LabText.line(DENSE_SP)) + 2 + SEGMENT + ROW_RULE; }
    /** The open card's dial rows' height: {@code dials} rows, {@code stacked} of them {@link #stackedRow}s (ui.js 300). */
    int dialRow(int dials, int stacked) {
        return clamp((open - OPEN_HEAD - 10 - stacked * stackedRow()) / Math.max(1, dials - stacked), rowLeast(1), 48);
    }
    /** The Control grid's column heads' room: two lines. */
    static int handHead() { return (int) Math.ceil(2 * LabText.line(DENSE_SP)); }
    /** A Control grid cell's height (ui.js 297): under the head, the grid's four rows, then Response and Fast is as rows of 44. */
    int handCell() { return handCell(2); }
    /** {@link #handCell()} with {@code rows} rows under the grid (a Spiro's drives too: five), each {@link #handRow} tall. */
    int handCell(int rows) { return clamp((open - OPEN_HEAD - 10 - handHead() - rows * handRow(rows)) / BrushLab.HAND_ROWS.length, 30, 46); }
    /** The Control card's rows under its grid, {@code rows} of them: 44, or what the grid's least cells leave each. */
    int handRow(int rows) { return Math.min(44, (open - OPEN_HEAD - 10 - handHead() - BrushLab.HAND_ROWS.length * 30) / rows); }
    /**
     * The Control card's rows under its grid for {@code b} ({@link BrushLab#handRows}; null, Grey Shift's set), when
     * they fit, each as tall as its label needs ({@link #handFits}). On the Nomad (and the Manta with a Spiro's drives and
     * Grey Shift's own) they may not: Gradient's own rows are left out first, its row kept, then its row, then Grey
     * Shift's, as other cards leave out what they have no room for ({@link #shows}).
     */
    java.util.List<PartDial> handRows(ModularBrush b) {
        java.util.List<PartDial> rows = BrushLab.handRows(b);
        if (handFits(rows)) return rows;
        int gradient = rows.indexOf(PartDial.GRADIENT);
        if (gradient >= 0 && handFits(rows.subList(0, gradient + 1))) return new java.util.ArrayList<>(rows.subList(0, gradient + 1));
        rows = BrushLab.handRows(b, false);
        if (!handFits(rows)) rows.removeIf(d -> d == null || java.util.Arrays.asList(PartDial.SHIFTS).contains(d));
        return rows;
    }
    /** Whether {@code rows} fit under the Control grid, each at least as tall as its label needs (Fast is a switch, the set swatches: one line's). */
    boolean handFits(java.util.List<PartDial> rows) {
        int least = 0;
        for (PartDial d : rows) least = Math.max(least, rowLeast(d == null || d == PartDial.DIR ? 1 : labelLines(d.label)));
        return handRow(rows.size()) >= least;
    }
    /**
     * The swap strip's columns for {@code n} tiles, so each is about 1.35 : 1 (ui.js 444–446), and no tile is much
     * shorter than a library card (7/10 of one at least), so its caption has the room (the Nomad's narrower test area
     * in Basic; its nine tiles go 3 × 3 in Advanced's shorter strip, LabLayoutChecks proving every caption whole).
     */
    int swapColumns(int n) { return swapColumns(null, n); }
    /** As {@link #swapColumns(int)}, for {@code part}'s strip: in the tall tray ({@link #tallSwap}) when it takes one. */
    int swapColumns(BrushPart part, int n) {
        int room = swapRoom(tallSwap(part, n)), columns = swapColumns(n, .7, 0, room);
        // Tiles too narrow for a tip's name ("Bristle", "Stipple": the Nomad's eleven Tip tiles in Basic, two rows of six):
        // shorter rows, three of them, each tile at least NARROWEST_TILE wide.
        return (right - 2 * MODULE_RULE) / columns >= NARROWEST_TILE ? columns : swapColumns(n, .55, NARROWEST_TILE, room);
    }
    /**
     * The most tiles a swap strip's tray holds: Advanced's thirteen Tip tiles; Later's {@link #LATER_MOST} (its names and
     * notes don't fit thirteen to a tray on the Nomad). More (the fourteen papers, Later's fourteen) take the tall tray,
     * twice as tall ({@link #tallSwap}; 2026-11-06, the owner: "all should be displayed, there's room on the page.. just
     * make the tray twice as tall"; two pages until then); more than it holds still turn in pages, as the library's
     * ({@link SwapStrip}, {@link LabPages}).
     */
    static final int SWAP_MOST = 13, LATER_MOST = 8;
    private static int swapMost(BrushPart part) { return part == BrushPart.LATER ? LATER_MOST : SWAP_MOST; }
    /** A swap strip's tiles a page for {@code n} choices: all of them up to two trays' worth (the tall tray), else as evenly as pages of at most {@link #SWAP_MOST} hold them. */
    static int swapPerPage(int n) { return swapPerPage(null, n); }
    /** As {@link #swapPerPage(int)} for {@code part}'s strip, Later's in {@link PartChoice#LATER_SHOWN}'s order. */
    static int swapPerPage(BrushPart part, int n) {
        int most = swapMost(part);
        if (n <= 2 * most) return n;
        int pages = LabPages.pages(n, most);
        return (n + pages - 1) / pages;
    }
    /** Whether a page of {@code n} of {@code part}'s tiles takes the tall tray, twice the room ({@link #swapRoom(boolean)}): more than one tray holds. */
    static boolean tallSwap(BrushPart part, int n) { return n > swapMost(part); }
    /** The narrowest swap tile whose caption holds every choice's name at the least size, dp ("Bristle" at 12 sp, inset 4). */
    static final int NARROWEST_TILE = 70;
    /** {@link #swapColumns(int)}'s columns in {@code room} dp, rows at least {@code least} of a card tall and tiles at least {@code narrowest} dp wide. */
    private int swapColumns(int n, double least, int narrowest, int room) {
        double pw = right - 2 * MODULE_RULE, ph = room, best = Double.MAX_VALUE;
        int columns = 1;
        for (int c = 1; c <= n; c++) {
            int rows = (n + c - 1) / c;
            if (rows > 1 && ph / rows < least * cardHeight || c > 1 && pw / c < narrowest) continue;
            double cost = Math.abs(Math.log((pw / c) / (ph / rows) / 1.35)) + (rows * c - n) * .08;
            if (cost < best) { best = cost; columns = c; }
        }
        return columns;
    }
    /** The room for a swap strip's tiles under its head: the strip's border, its head bar. */
    int swapRoom() { return swapRoom(false); }
    /** As {@link #swapRoom()}; {@code tall}: the tall tray's, twice the tiles' room and a line between ({@link #tallSwap}). */
    int swapRoom(boolean tall) { int room = swap - 2 * MODULE_RULE - SECTION_BAR; return tall ? 2 * room + MODULE_RULE : room; }
    /**
     * A swap tile's caption's least size, sp: 13, or 12 on the Nomad, whose nine Control tiles in Basic are too narrow
     * for "Custom" at 13 (61 dp of a 59 dp line, its "m" wide).
     */
    float swapLeast() { return compact ? DENSE_SP : TypeStyle.CAPTION.sp; }
    /**
     * A swap tile's caption's inset either side, dp: a card's, or 4 on the Nomad, whose nine Tip tiles in Basic are a
     * hair too narrow for "Bristle" and "Stipple" at 12 sp inside a card's 5 (60.5 dp of a 59 dp line).
     */
    float swapInset() { return compact ? 4 : LabText.CAPTION_INSET; }
    /**
     * A swap strip tile's cell for {@code n} tiles, rule included: {width, height}, never taller than wide, or than a
     * library card when it is narrower than one (its caption needs a card's lines).
     */
    int[] swapCell(int n) { return swapCell(null, n); }
    /** As {@link #swapCell(int)}, for {@code part}'s strip ({@link #tallSwap}). */
    int[] swapCell(BrushPart part, int n) {
        int columns = swapColumns(part, n), rows = (n + columns - 1) / columns, w = (right - 2 * MODULE_RULE) / columns;
        return new int[]{w, Math.min(swapRoom(tallSwap(part, n)) / rows, Math.max(w, cardHeight))};
    }
    /**
     * The swap panel for {@code n} tiles, dp: {x, width, height}. Beside the column (just right of it, as the test area),
     * as wide as the test area, as tall as its head and tiles ({@link SwapStrip}: the head bar, rows of {@link #swapCell}
     * with a 2 dp line between, its 2 dp border under them): the tiles fill the room the old strip had under the test page.
     */
    int[] swapPanel(int n) { return swapPanel(null, n); }
    /** As {@link #swapPanel(int)}, for {@code part}'s strip ({@link #tallSwap}). */
    int[] swapPanel(BrushPart part, int n) {
        int rows = (n + swapColumns(part, n) - 1) / swapColumns(part, n);
        return new int[]{side + column + split, right, SECTION_BAR + rows * swapCell(part, n)[1] + (rows - 1) * MODULE_RULE + MODULE_RULE};
    }
    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
