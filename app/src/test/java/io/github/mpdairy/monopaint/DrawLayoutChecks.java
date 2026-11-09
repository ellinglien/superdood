package io.github.mpdairy.monopaint;

public final class DrawLayoutChecks {
    /** The four screens, dp: the Manta and the Nomad, portrait and landscape. */
    static final int[][] SCREENS = {{1024, 1365}, {1365, 1024}, {749, 998}, {998, 749}};
    /** The column's items and what is left at their fitted heights on the four screens, dp ({@link #order}). */
    // The Nomad's eight slots (2026-10-28), their rows sharing the ten's room: 854 and 8, 611 and 2 with ten.
    // The picker box half as long again from the room to spare (2026-10-29): 980 and 235, 870 and 4, 850 and 12, 609 and 4 before.
    // Shapes and Play's small row back (2026-10-30): 1040 and 175, 874 and 0, 859 and 3, 613 and 0 before.
    // The layer button's row on the Manta (2026-11-06; none on the Nomad): 1084 and 131, 874 and 0 before.
    // The sand timer, Show Kinds and Play to the menu, two small rows fewer, and the layer button (58 dp) under the picker box on
    // the Nomad too (2026-11-06): 1084 and 65, 808 and 0, 858 and 4, 613 and 0 before.
    // The book's row under the layer button, before the picker box grows (2026-11-06; 30 dp on the Manta turned, none on the Nomad turned): 996 and 161, 816 and 0, 800 and 4, 555 and 0 before.
    // The book's row from 7b99f5c0 to the menu's Page Bar row the same day: 996 and 117, 786 and 0, 760 and 0, 555 and 0 then.
    // The Nomad upright 804 and 0 with nine slots and Fill in a tool row (2026-11-06; 800 and 4 with ten): its eight shown share twelve rows' room among eleven.
    static final int[][] NOW = {{996, 161}, {816, 0}, {804, 0}, {555, 0}};

    public static void main(String[] args) throws java.io.IOException {
        leaveClean(); fingerTaps(); fits(); manta(); tray(); longPress(); order(); shorter(); side(); chooser(); taps(); fun(); funLists(); zoom(); finger(); pageBar(); layers(); zen(); brushLine(); screen(); column(); extras(); lasso(); allSlots(); nomadEight(); picker(); pickerRows(); pickerBox(); tops();
        System.out.println("PASS: the page bar along the page's top from the column, the page under it, the brush line along the foot from 14 dp past Zen Mode's button, Zen on the column's axis in the foot corner, nothing overlapping, every screen and either hand; Zen Mode's way out a pen target over 26 dp, inside the smallest screen's corner, its words Title Case; the drawing screen's one side toolbar fits the Manta and the Nomad in either turn without scrolling, no layers in it but the layer button over Refresh (2026-11-06; Page Settings' Layers row since the refocus, 2026-10-24), every item at least its least, no pen target under 26 dp; Menu first, centred on the page previews, Template Visibility (its step Full, Half, Faint, Hidden) beside it; the tray (nine slots, a row each, 24 to 34 dp, no numbers, the stroke the row's width less its side bar), straight under it the smudge tool and Finger, then the eraser, the picker box (2026-10-27, the owner's drawing: the grey strip its lower-left triangle, white at the top, the size slider its upper-right one, broad at the top, two squares and the gap wide, a pen driving the triangle it lands in, a drag redrawing its tab's strip alone), one dot of the size in the grey under them, no swatches, and the Dropper and the Lasso under that, Refresh alone in the row under them (the whole row its target), then Show Kinds and the sand timer, Shapes and Play (2026-10-30), the wet indicator, the brush line a Basic tool's description after its name, what is in use marked by a side bar, the Manta at its preferred heights either way up with white space to spare; no Edit buttons (a long press customizes) and no page turner in the column; the page bar along the page's top, 40 dp (36 on the Nomad), Notebook at the end away from the column, either hand, then Zoom, \"100 / 100\" between ‹ and ›, then 22 x 30 dp previews (19 x 26 on the Nomad, 21 to 40 of them, a + tile of the same size after the last page) 4 dp clear of the edge, a touch going to the nearest, its buttons over 26 dp, the page that much shorter and the column's width narrower; a tap on a tray row only selects it, the selected smudge tool its chooser (as big as its cards need, beside the column, paged past six), the selected eraser does nothing, no paint bucket and no Fill mode; no undo, redo, Brush Lab or Zoom in the column (2026-10-28); Cut, Copy and Delete in the bar over a floating piece, no Trash button; Play's menu beside its button (Magnetic Poetry, Mirrors, Replay Page; no Shapes, Self-Portrait Prompt, Stamp, Surprise Me or Fling), its side bar while a toy is on, fitting every screen; Export Now and Delete Notebook at Notebook Settings' foot; Zoom on the page bar, its padlock beside its percentage up to 800%; the Mirrors list (rows 52 dp, its caption) fitting every screen's height, their labels Title Case, two words at most; Finger the column's button showing what a finger does, its menu fitting the height; the menu in two groups, a grey-400 line between; a slot's long press the tool picker, the owner's drawn tools, fitting every screen, grouped by kind, Brush Lab and Advanced under them, Reset to Default over them for an edited Basic tool alone");
    }

    /**
     * Leaving the drawing screen for the notebook screens (Settings, the shelf, Page Settings) gives the window one clean
     * update, as coming back does: under the page's and the column's partial grey, ink sent straight to the panel stayed
     * over Notebook Settings (the owner, 2026-10-30: "some ghosting from the previous page").
     */
    /**
     * A two-finger double tap is Zen Mode (the owner, 2026-10-30): a two-finger tap's Undo waits for a second tap, which
     * takes it back and toggles Zen instead; the pen, a finger's stroke, a page turn or leaving does the waiting Undo first.
     */
    static void fingerTaps() throws java.io.IOException {
        String pad = source("DrawingPad");
        check(!pad.contains("ZEN_TAP_MS") && !pad.contains("redoWaiting") && pad.contains("FingerTool.Tap tap = FingerTool.tap(fingers, app.prefs.twoFingerTap(), app.prefs.threeFingerTap());") && pad.contains("if (tap == FingerTool.Tap.UNDO) app.undo(); else if (tap == FingerTool.Tap.REDO) app.redo(); else refreshAsked(")
                && pad.contains("if (event.getActionMasked() == MotionEvent.ACTION_DOWN && isPen(event, 0)) layLiveNow();")
                && pad.contains("private void suspend(boolean staying) {\n        layLiveNow();") && pad.contains("if (page != document) { layLiveNow(); app.live.shown(page); }"),
                "A two- and a three-finger tap do System Settings' choices (Undo and Redo at first), both at once (no three-finger double tap for Zen Mode since 2026-11-02)");
        // Another page shown stays on the drawing screen: only the page's fast ink goes blank, or the column, the page bar
        // and the Zen button go white with nothing to redraw them (the owner, 2026-11-03, after the Template eye).
        int show = pad.indexOf("void showPage(ToneDocument page) {");
        check(pad.indexOf("suspend(true);", show) == pad.indexOf("suspend(", show) && pad.contains("void suspend() { suspend(false); }")
                && pad.contains("eraseFastInk(staying ? pageArea() : windowArea())"), "Showing a page blanks the page's fast ink alone");
    }
    static void leaveClean() throws java.io.IOException {
        String screens = source("NotebookScreens");
        int cover = screens.indexOf("private void cover(Mode next)"), suspend = screens.indexOf("app.pad.suspend();", cover),
                shown = screens.indexOf("view.setVisibility(View.VISIBLE);", cover), clean = screens.indexOf("if (leaving) view.postDelayed(() -> Ui.flashWindow(", cover);
        check(cover > 0 && suspend > cover && shown > suspend && clean > shown && clean < screens.indexOf("void hide()"),
                "Every way from the drawing screen to the notebook screens cleans the window once they show and a menu that opened them is gone");
    }
    /**
     * The Lasso's state machine (the owner, 2026-10-24: "sometimes selecting the lasso doesnt work, i have to click to
     * another tool (stamp say) and return"), as InkWashBar runs it: taken up from every tool, the eraser too, its next
     * pen stroke is a loop; a second tap keeps it (a piece up has its bar, Cut, Duplicate and Delete); Undo and a page turn keep it; another
     * tool puts it down; only the pen's eraser end erases meanwhile.
     */
    static void lasso() {
        for (int tool = 0; tool < ToolSlots.TOOLS; tool++) {
            boolean on = false, floating = false;
            check(DrawLayout.lassoTap(on, floating, false) == DrawLayout.LassoTap.TAKE_UP, "From tool " + tool + ", a tap takes the Lasso up");
            on = true;
            check(DrawLayout.lassoLoop(on, false), "From tool " + tool + ", the pen draws the Lasso's loop");
            check(!DrawLayout.lassoLoop(on, true), "The pen's eraser end still erases");
            check(DrawLayout.lassoTap(on, floating, false) == DrawLayout.LassoTap.NOTHING && DrawLayout.lassoLoop(on, false), "A second tap keeps it");
            floating = true;
            check(DrawLayout.lassoTap(on, floating, false) == DrawLayout.LassoTap.NOTHING && DrawLayout.lassoTap(on, floating, true) == DrawLayout.LassoTap.NOTHING,
                    "With a piece up, a tap opens no menu: the bar over the piece has its choices");
            floating = false; // Undo drops the piece (DrawingPad.cancelSelection); a page turn carries it, the Lasso stays
            check(DrawLayout.lassoLoop(on, false), "After Undo or a page turn, the pen still loops");
            on = false; // another tool chosen (InkWashBar.choose)
            check(!DrawLayout.lassoLoop(on, false) && DrawLayout.lassoTap(on, false, false) == DrawLayout.LassoTap.TAKE_UP, "Another tool puts it down; a tap takes it up again");
        }
        // Cut, Copy, Delete (the owner, 2026-10-25: "there should be options for lasso"), in the bar over the piece since
        // 2026-10-28 ("above it is a toolbar offering choices for what to do with it"); Paste the Lasso's tap.
        check(java.util.Arrays.equals(DrawLayout.LASSO_BAR, new String[]{"Cut", "Duplicate", "Make Stamp", "Delete"}),
                "A piece up: Cut, Duplicate, Make Stamp, Delete (Duplicate in Copy's place, 2026-10-28; Make Stamp 2026-11-02)");
        // The bar, one row of its words (InkWashBar.lightRow: each LIGHT_PAD each side, the row's fine outline), fits the
        // narrowest page beside the column: the Nomad's, portrait.
        float row = 2;  // Theme.FINE each side
        for (String w : DrawLayout.LASSO_BAR) row += LabText.width(w, TypeStyle.MENU.sp, 0) + 2 * DrawLayout.LIGHT_PAD;
        DrawLayout nomad = new DrawLayout(749, 998);
        check(row <= nomad.width - nomad.side, "The Lasso's bar fits the Nomad's page: " + row + " dp of " + (nomad.width - nomad.side));
        String bar, pad;
        try { bar = source("InkWashBar"); pad = source("DrawingPad"); } catch (java.io.IOException e) { throw new AssertionError(e); }
        check(bar.contains("app.pad.cutSelection(); else if (choice == 1) app.pad.duplicateSelection(); else if (choice == 2) app.pad.stampSelection(); else app.pad.deleteSelection();")
                        && bar.contains("labelContent == lassoContent) && !Ui.outside(labelContent, event, index)")
                        && pad.contains("selection.grab(handle); lassoBar();") && pad.contains("drawSelectionFrame(); flush(true); lassoBar();")
                        && pad.contains("selectionShown.setEmpty(); lassoBar();") && pad.contains("showSelection(); flush(true); lassoBar();"),
                "The bar's words run the piece's own; the pen on it keeps it; gone while the piece moves, back where it lands, gone with it");
        check(DrawLayout.lassoTap(true, false, true) == DrawLayout.LassoTap.MENU && DrawLayout.lassoTap(false, false, true) == DrawLayout.LassoTap.TAKE_UP,
                "In use with something kept, a tap offers Paste; not in use, it takes the Lasso up");
        // The column's Lasso tapped while the edge strip's piece floats (the owner, 2026-11-05: "regular lasso selected from the
        // toolbar had some issues with the frame options not showing up"): the tap leaves the piece, but the Lasso is the hand's
        // now, so placing the piece leaves it up and the next loop lifts a piece with its frame and bar. The edge strip's
        // letting go still puts it away once the piece is done (e0656f2e).
        int tap = bar.indexOf("private void tapLasso()"), keep = bar.indexOf("app.pad.keepLasso();", tap);
        check(tap > 0 && keep > tap && keep < bar.indexOf("DrawLayout.lassoTap(", tap) && pad.contains("void keepLasso() { lassoBack = false; }")
                        && pad.contains("boolean lassoBackWhenDone() { return lassoBack = selection != null && lassoOn && !lassoDeletes; }")
                        && bar.contains("else if (!app.pad.lassoBackWhenDone()) { app.pad.lasso(false);"),
                "A tap on the column's Lasso keeps it after the floating piece is done; the edge strip's lift still puts it away after");
    }

    /**
     * The column's extras (the owner, 2026-10-23): Template Visibility in the tool grid beside the Lasso (the menu's
     * Template row cut at the refocus, 2026-10-24); the menu's Left-Handed row; the menu in four groups; a dirty brush's
     * held paint shown in one of the six greys on the smudge tool, Clean Brush in its chooser and behind a dirty slot's
     * long press (the slots' dab and gauge went, 2026-10-24).
     */
    static void extras() {
        // Template Visibility: four taps go Full, Half, Faint, Hidden and back to Full, each said so.
        NotebookOptions o = NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.LINED);
        StringBuilder said = new StringBuilder();
        for (int tap = 0; tap < 4; tap++) { said.append(DrawLayout.templateSaid(o.guidesShown)).append("; "); o = o.withNextGuidesStep(); }
        check(o.guidesShown == NotebookOptions.FULL && said.toString().equals("Template Visibility: Full; Template Visibility: Half; Template Visibility: Faint; Template Visibility: Hidden; "),
                "Template Visibility steps Full, Half, Faint, Hidden, Full: " + said);
        // The menu (2026-10-30: Settings, Notebook Explorer, Import; Finger, Left-Handed; one group line; Export Page since
        // 2026-11-02, Draw Together on the live branch) fits the Nomad in landscape unscrolled.
        // The column's Sand Timer, Show Kinds and Play too since 2026-11-06, Finger Action out (System Settings'), four groups.
        java.util.List<DrawLayout.Row> manta = DrawLayout.fileRows(true, null, false, "Draw Together", false, true, true, false);
        int lines = java.util.Collections.frequency(manta, DrawLayout.Row.LINE), menuRows = manta.size() - lines;
        check(menuRows == 11 && lines == 3 && menuRows * DrawLayout.MENU_ROW + lines * 9 + 2 <= 749 - 2 * DrawLayout.PAD && DrawLayout.LEFT_HANDED.equals("Left-Handed"), "The menu with Export Page, Send to the Fridge, the column's three, Left-Handed and Draw Together fits every screen");
        try {
            String bar = source("InkWashBar"), app = source("PaintActivity"), ink = source("InkWash");
            check(bar.contains("app.showGuides(app.book.options().withNextGuidesStep().guidesShown)") && !app.contains("names.add(NotebookLayout.TEMPLATE)")
                            && !app.contains("stepsMenu(") && bar.contains("eyeCell(DrawLayout.kindsName(choice), true, false,")
                            && bar.contains("for (int c = DrawLayout.kindsFirst(guides()); c < EraseKind.LABELS.length; c++)")
                            && DrawLayout.kindsFirst(-1) == 1 && DrawLayout.kindsFirst(0) == 0 && DrawLayout.kindsName(0).equals(NotebookLayout.TEMPLATE),
                    "Template Visibility is Show Kinds' popup's first eye, only while the page has guides; one state (NotebookOptions.guidesShown); no Template row in the menu");
            // The menu's groups (the owner, 2026-10-24: "it's chaotic"), most used first; two since Play went back to the column, 2026-10-30; the owner's four, 2026-11-06.
            String menu = app.substring(app.indexOf("void fileMenu(View anchor)"), app.indexOf("menu(anchor, \"file menu\""));
            // The rows are DrawLayout.fileRows' (the browser's too, M4).
            java.util.List<String> rows = new java.util.ArrayList<>();
            for (DrawLayout.Row r : manta) rows.add(r.words == null ? "|" : r.words);
            check(rows.equals(java.util.Arrays.asList(NotebookLayout.OPEN_NOTEBOOK, DrawLayout.NOTEBOOK_SETTINGS, "|", LibraryCard.EXPORT_PAGE, Fridge.ROW, PictureImport.IMPORT, EraseKind.SHOW_KINDS, "|",
                                    "Draw Together", SandTimer.NAME, DrawLayout.PLAY, "|", DrawLayout.LEFT_HANDED, SystemSettings.TITLE))
                            && menu.contains("DrawLayout.fileRows(true, null, toolboxRight, LiveShare.SHARE, share.on(), true, inkWashBar.sandShown, playing)")
                            && menu.contains("exportMenu(anchor, book.stacks().of(book.index()))") && menu.contains("DrawingBook.isCover(book.index())) rows.removeIf(r -> \"exportPage\".equals(r.key) || \"fridge\".equals(r.key))")
                            && source("NotebookScreens").contains("app.exportMenu(export, place);") && app.split("\"export menu\"", -1).length == 2
                            && !app.contains("coverRows(") && app.contains("line.setBackgroundColor(Theme.GREY_400)"),
                    "The menu (the owner's order, 2026-11-06): Notebook Explorer and Notebook Settings; Export Page (the page shown's, Page Settings' one menu, none on a cover, 2026-11-02), Import (pictures and PDFs, one row since 2026-10-29) and Show Kinds; Draw Together, the Sand Timer while it can be set and Play, marked while a toy is on; Left-Handed and System Settings; a grey-400 line between each; no Finger Action (System Settings'), Export Now, Rename, Delete or Replay Page rows: " + rows);
            // Play the menu's row since 2026-11-06 (the column's button 2026-10-30 to 2026-11-06): its menu beside Menu, marked while a toy is on, read as the menu opens.
            check(menu.contains("case \"play\": actions[i] = () -> { if (!busy()) playMenu(anchor); };")
                            && menu.contains("DrawLayout.playOn(pad != null && (pad.words.open() || pad.text.on() || pad.shapesOn && !pad.lassoOn && !pad.dropperOn), mirrors.on())")
                            && !bar.contains("ic_ph_shapes") && !bar.contains("tapShapes") && !bar.contains("ic_ph_confetti") && !bar.contains("modesChanged")
                            && bar.contains("void sayMirrors(String text, long ms) { say(text, ms, menu); }"),
                    "Play the menu's row, its menu beside Menu, marked while a toy is on; no Play or Shapes button in the column");
            // Notebook Settings' foot (2026-10-28): Export Now, then Delete Notebook last, the same actions and confirmation.
            String screens = source("NotebookScreens");
            int exportAt = screens.indexOf("app.exportNow();"), deleteAt = screens.indexOf("app.deleteNotebook(app.drawingName);"), footAt = screens.indexOf("TextView foot = text(LibraryCard.foot(");
            check(exportAt > 0 && exportAt < deleteAt && deleteAt < footAt && screens.contains("new LabButton[]{export, delete}") && LibraryCard.DELETE_NOTEBOOK.equals("Delete Notebook")
                            && app.contains(".setTitle(\"Delete this notebook?\")") && screens.contains("LibraryCard.settingsLayout("),
                    "Notebook Settings' foot: Export Now, then Delete Notebook, its confirmation as before; Rename the name's button");
            check(bar.contains("PageTurner.STEP_ICONS[at + 1]") && bar.contains("book.template(book.index()).equals(NotebookTemplate.BLANK) ? -1")
                            && bar.contains("row.setMeasureWithLargestChildEnabled(true);"),
                    "Its eye the steps' eyes; no Template eye while the page has no guides; each column as wide as the widest name");
            // Layout v3 (the owner's mockup, 2026-10-26), its rows since 2026-10-28: Menu alone; the block the Dropper and the
            // Lasso, Refresh; then the sand timer, Show Kinds and Play (2026-10-30: "move fun button beside it"; Shapes beside
            // the timer until 2026-11-05, Play's row since), the wet indicator; no drop on the brush line; no undo or
            // redo in the column (two and three fingers undo and redo).
            int block = bar.indexOf("case BLOCK:"), play = bar.indexOf("case PLAY:");
            check(bar.contains("lasso = app.iconButton(DrawLayout.LASSO, R.drawable.ic_ph_lasso") && bar.indexOf("pickRow.addView(swatches.dropper)") < bar.indexOf("pickRow.addView(lasso)")
                            && bar.contains("for (View row : new View[]{pickRow, templateRow}) view.addView(row,") && block > 0 && bar.indexOf("ghostRow.addView(ghost, item(fitted.icon))") > 0
                            && !bar.contains("templateRow.addView(template,") && bar.indexOf("templateRow.addView(wet,") > 0
                            && !bar.contains("sandTimer") && !bar.contains("playRow") && !bar.contains("wetRow") && !bar.contains("R.drawable.ic_ph_eye_fill")
                            && bar.contains("wet.setLayoutParams(tile(at.half(), at.wet));")
                            && menu.contains("case \"sand\": actions[i] = this::countdownTapped;") && menu.contains("case \"kinds\": actions[i] = inkWashBar::kindsPopup;")
                            && bar.contains("case PLAY: view.addView(ghostRow, ghostPlace(fitted));") && play > block
                            && bar.contains("menuRow.addView(menu[0]") && !bar.contains("menuRow.addView(template") && !bar.contains("brushLine.addView(wet")
                            && !bar.contains("ic_ph_undo") && !bar.contains("ic_ph_redo") && !bar.contains("tapShapes")
                            && bar.contains("app.showKind(choice,") && !bar.contains("ic_ph_eye_closed")
                            && source("MultiFingerTap").length() > 0 && app.contains("void undo()") && app.contains("void redo()"),
                    "Menu alone; the Dropper and the Lasso, the wet indicator, then Refresh after a gap (2026-10-29); the sand timer and Show Kinds the menu's rows (2026-11-06); no undo or redo buttons");
            // Fill in (2026-11-06), the smudge tool and the eraser under the tray, a tray row each, the owner's drawings (Fill in's tip as its slot's was, the spudger, the block eraser).
            check(bar.contains("case TOOLS: for (int t : DrawLayout.AFTER_TRAY) view.addView(tools[t], trayTile(fitted));") && DrawLayout.AFTER_TRAY.length == DrawLayout.TOOL_ROWS
                            && java.util.Arrays.equals(DrawLayout.AFTER_TRAY, new int[]{ToolSlots.FILL, ToolSlots.SMUDGE, ToolSlots.ERASER})
                            && bar.contains("R.drawable.tool_smudge : R.drawable.tool_eraser") && bar.contains("if (slot >= ToolSlots.COUNT && slot != ToolSlots.FILL) { showIcon(slot); return; }")
                            && "tip_fill_in".equals(DrawLayout.drawing(ToolSlots.fill())) && bar.indexOf("case TOOLS:") < block,
                    "Under the tray Fill in, the smudge tool and the eraser, a row each, their drawings; then the block");
            // The owner (2026-10-28): "we could merge the lasso trash and the lasso": no Trash button, the edge strip's hold
            // still the loop that deletes; Refresh alone in its row, the row its target ("set apart so a finger can press it").
            check(!bar.contains("ic_ph_recycle") && !bar.contains("tapTrash") && bar.contains("takeUpLasso(heldDeletes)")
                            && !bar.contains("lassoWays") && !bar.contains("lasso.setOnLongClickListener") && bar.contains("lasso.mark(on);")
                            && bar.contains("ghost = app.iconButton(DrawLayout.REFRESH, R.drawable.ic_ph_ghost") && bar.contains("ghost.setLayoutParams(item(at.icon))")
                            && new DrawLayout(SCREENS[0][0], SCREENS[0][1]).icon >= 48 && new DrawLayout(SCREENS[0][0], SCREENS[0][1]).inner >= 48,
                    "No Trash button, the hold's loop deletes; the Lasso only selects, no hold; Refresh a whole row, 48 dp tall at least on the Manta");
            // The status corner (the notebook's name until 2026-11-02) says Draw Together's status, takes no touch, goes with the foot row in Zen Mode.
            check(app.contains("inkWashBar.statusCorner(live.status())") && !app.contains("statusCorner(DrawingFiles.name(")
                            && bar.contains("statusCorner.setClickable(false)") && !bar.contains("statusCorner.setOnClickListener") && app.contains("footRow.addView(inkWashBar.statusCorner")
                            && app.contains("footRow.setVisibility(zen ? View.GONE : View.VISIBLE)"),
                    "The status corner: the live status, not tappable, hidden with the foot row in Zen Mode");
            check(DrawLayout.LINE_NAME >= 200 && DrawLayout.LINE_NAME + DrawLayout.NOTE_GAP <= 600 - DrawLayout.LINE_GAP,
                    "The name's room fits the narrowest brush line with the note's gap");
            DrawLayout.Row left = DrawLayout.fileRows(false, "", false, null, false, false, false, false).get(3), right = DrawLayout.fileRows(false, "", true, null, false, false, false, false).get(3);  // after Import, its line and the browser's Finger Action
            check(left.words.equals(DrawLayout.LEFT_HANDED) && left.icon.equals("ic_ph_hand_pointing") && right.words.equals(DrawLayout.RIGHT_HANDED) && right.icon.equals("ic_ph_hand_pointing_mirrored")
                            && app.contains("prefs.setToolboxRight(!prefs.toolboxRight()); applyToolboxSide();") && DrawLayout.RIGHT_HANDED.equals("Right-Handed"),
                    "The menu's hand row names the other hand (Left-Handed, or Right-Handed with its hand mirrored), applied at once");
            // A dirty brush: Clean Brush in the smudge tool's chooser; no dab or gauge on any row. The drawn tip's row shows its side
            // bar alone (the owner, 2026-10-26: the held paint's dab "looks like a square at the end, shouldn't be there").
            check(!bar.contains("helds[") && !bar.contains("loads[") && !bar.contains("Double.NaN, held)") && bar.contains("button.iconOnly(face(drawn, null, true, right), face(drawn, null, false, right));")
                            && bar.contains("app.inkWash.dirty(InkWash.SMUDGE)") && bar.contains(", holds paint")
                            && bar.contains("if (app.inkWash.dirty(tool)) cleanMenu(tool); else app.toolPicker(tool, anchor(tool))") && ink.contains("DrawLayout.heldGrey(session.held.of("),
                    "The smudge tool's and the eraser's rows their drawing and side bar alone, no dab (held paint said, not drawn), Clean Brush in its chooser; the slots no dab, no gauge, Clean Brush behind a dirty slot's long press");
            // The Dropper always in its cell (the owner, 2026-10-26: "Dropper | Lasso" showed the Lasso alone with the smudge tool in
            // use: it was drawn as paper with the strip); with the eraser or the smudge tool in use, its pick is the brushes' grey.
            String swatchBar = source("SwatchBar");
            check(!swatchBar.contains("dropper.overlay") && !swatchBar.contains("dropperShown") && !bar.contains("dropperShown") && !bar.contains("dropper.setVisibility")
                            && bar.contains("Brush brush = DrawLayout.greys(tool) ? app.inkWash.brush(tool) : null;") && source("ToolSwatches").contains("brush == null ? StrokeMath.clamp(1 - tone / 255.0)"),
                    "The Dropper shown whatever the tool, beside the Lasso; a pick with the eraser or the smudge tool sets the brushes' grey");
            // Their long presses kept: the smudge tool's chooser, the eraser's menu.
            // One long-press path for the rows and Zen Mode's fan (InkWashBar.hold, 2026-11-04).
            check(bar.contains("button.setOnLongClickListener(v -> { hold(tool); return true; });")
                            && bar.contains("if (tool == InkWash.ERASER) { eraserMenu(); return; }")
                            && bar.contains("if (tool == InkWash.SMUDGE) { if (app.inkWash.selected != InkWash.SMUDGE) choose(InkWash.SMUDGE); tapSmudge(); return; }"),
                    "The smudge tool's long press its chooser, the eraser's its menu");
            // The tray's rows to the screen's edge (the owner, 2026-10-27: "move tools slightly left, touching edge"),
            // mirrored with the column on the right: the row over the column's outer margin, its drawing flush there, its
            // side bar on the page side.
            String slotTile = source("SlotTile");
            check(bar.contains("place.gravity = right ? Gravity.RIGHT : Gravity.LEFT;") && bar.contains("if (right) place.rightMargin = -app.dp(at.edge); else place.leftMargin = -app.dp(at.edge);")
                            && bar.contains("view.setClipToPadding(false);") && bar.contains("if (right) canvas.scale(-1, 1, width / 2f, 0);") && bar.contains("left = -cut * scale")
                            && bar.contains("button.faces(face(face, null, true, right), face(face, null, false, right));")
                            && slotTile.contains("int margin = flush ? 0 : Ui.dp(context, MARGIN)") && slotTile.contains("row(context, width, height, barLeft, icon == null)"),
                    "The tray's rows reach the screen's edge, their drawings against it, mirrored with the column on the right, the side bar on the page side");
            // The picker box (the owner's drawing, 2026-10-27): the strip its lower-left triangle under the slider's upper-right
            // one, each tab on the box's outer side, a pen going to the triangle it lands in (DrawLayout.boxShade, pickerBox);
            // the nib's dot on plain paper, no square round it (the owner, 2026-10-27: "remove square frame from toolbar (no outline)").
            // The box is PickerBox, built by the side column's SwatchBar and by Brush Lab (2026-10-29).
            String strip = source("GreyStrip"), slider = source("SizeSlider"), readout = source("SwatchReadout"), box = source("PickerBox");
            check(box.indexOf("addView(strip,") > 0 && box.indexOf("addView(strip,") < box.indexOf("addView(slider,") && swatchBar.contains("new PickerBox(")
                            && source("BrushLabActivity").contains("new PickerBox(") && box.contains("gesture = !strip.hidden && slider.shade(event.getX(), event.getY()) ? strip : slider;")
                            && strip.contains("R.drawable.shape_box_shade :") && !strip.contains("shape_box_shade_line") && slider.contains("R.drawable.shape_box_size")
                            && strip.contains("float x = left - dp(DrawLayout.FACE_MARK_GAP) - dp(DrawLayout.FACE_MARK);") && slider.contains("x = right + dp(DrawLayout.FACE_MARK_GAP);")
                            && !readout.contains("shape_readout_frame") && !readout.contains("Bitmap frame"),
                    "The picker box: the grey strip's triangle under the size slider's, the tabs outside it; the readout's dot on paper, no frame");
            // The size tab moves as the strip's (the owner, 2026-10-27: "ghosting glitch with the size selector ... the
            // shade picker doesn't have this"): the same marker, not its binary path (mode 5, then a redrive), the lift
            // putting the tab back where the size is, and nothing on a drag laid out or drawn through Android.
            check(slider.contains("pen = new StripMarker(this, feedback, this::markArea);") && strip.contains("pen = new StripMarker(this, feedback, this::squareArea);")
                            && !slider.contains("pen.binary") && !strip.contains("pen.binary")
                            && slider.contains("if (pen.lifted) listener.set(at(pen.y));\n            // As the strip") && slider.contains("            follow(inUse);\n        }")
                            && !slider.contains("requestLayout") && !slider.contains("invalidate(") && !strip.contains("requestLayout") && !strip.contains("invalidate("),
                    "A size drag redraws only its tab's strip, as a grey drag does: the strip's marker path, no binary update or redrive, no relayout");
        } catch (java.io.IOException e) { throw new AssertionError("the sources: " + e); }
        // The held paint's grey: one of the six, never paper (that would read as clean), the nearest to its darkness.
        java.util.List<Integer> six = new java.util.ArrayList<>();
        for (int g : NotebookOptions.PAPERS) six.add(g);
        int last = 256;
        for (int k = 0; k <= 20; k++) {
            int g = DrawLayout.heldGrey(k / 20f);
            check(six.contains(g) && g != 255 && g <= last, "Held paint at " + k * 5 + "% shows as one of the six greys, darker paint never lighter: " + g);
            last = g;
        }
        check(DrawLayout.heldGrey(0) == 0xdd && DrawLayout.heldGrey(.5f) == 0x88 && DrawLayout.heldGrey(1) == 0 && DrawLayout.heldGrey(.33f) == 0xbb,
                "Faint paint grey-200, half grey-600, black ink");
        check(DrawLayout.CLEAN.equals("Clean Brush") && DrawLayout.TOOLS.equals("Tools"), "Clean Brush and Tools, the chooser's and Brush Lab's words");
    }
    /** The brush line (the owner, 2026-10-22): its note, its preview, its words. */
    static void brushLine() {
        // The owner (2026-10-22): notes at the brush line's right, the name keeping a third at least.
        check(DrawLayout.noteWidth(600) == 400 && DrawLayout.noteWidth(0) == 0, "The brush line: a note two thirds of it at most");
        // No preview of the stroke (the owner, 2026-10-27: "remove stroke from bottom line brush info"), and no tap on the
        // words (the release pare-down, 2026-11-05: Brush Lab is a slot's long press's Edit Brush alone).
        try {
            String bar = source("InkWashBar");
            check(!bar.contains("brushPreview") && !bar.contains("linePreview") && !bar.contains("brushName.setOnClickListener") && !bar.contains("openBrushLab"),
                    "The brush line has no stroke preview and opens no Brush Lab");
        } catch (java.io.IOException e) { throw new AssertionError("the sources: " + e); }
        check(DrawLayout.brushWords("Wet gouache", null, java.util.Arrays.asList("Gouache", "paint stays wet")).equals("Wet gouache · Gouache, paint stays wet"), "The brush line: name · its card's words");
        check(DrawLayout.brushWords("Fineliner", null, java.util.Arrays.asList("Fineliner")).equals("Fineliner"), "The style left out when it is the name");
        // The owner (2026-10-26): "add description to brush info on bottom of screen": a Basic tool's own line, first.
        ToolPresets.Preset swell = ToolKits.preset("Swell brush");
        check(swell != null && DrawLayout.brushWords(swell.name, swell.line, java.util.Arrays.asList("Brush pen", "pressure")).equals("Swell brush · presses darker, Brush pen, pressure")
                        && DrawLayout.brushWords("Fineliner", "Fineliner", java.util.Arrays.asList("Fineliner")).equals("Fineliner"),
                "The brush line: name · description, card words, nothing that says the name again");
    }
    static void zen() {
        // The owner (2026-10-23): "keep the zen in the bottom left (align a bit better pls)": in the foot corner under the
        // column, centred on its icon axis, Menu's width, its icon's foot as far from the edge as the brush line's mark's.
        for (int[] s : SCREENS) for (boolean right : new boolean[]{false, true}) {
            DrawLayout d = new DrawLayout(s[0], s[1]);
            int[] cell = DrawLayout.zenCell(s[0], s[1], d.side, d.half(), d.zenHeight(), right);
            int columnLeft = right ? s[0] - d.side : 0;
            check(d.half() >= 26 && d.zenHeight() >= 26 && cell[1] == d.columnHeight() && cell[1] + d.zenHeight() == s[1]
                    && cell[0] >= columnLeft && cell[0] + d.half() <= columnLeft + d.side
                    && (right ? columnLeft + d.side - cell[0] - d.half() : cell[0] - columnLeft) == (d.side - d.half()) / 2,
                    "Zen Mode's button a pen target in the foot corner under the column, on its axis, Menu's width, on " + s[0] + " x " + s[1] + (right ? ", column right" : ""));
            // The icon's gap to the screen's foot is its gap to the side edge (the owner, 2026-10-23: left and right space equal).
            int iconFoot = (d.zenHeight() - DrawLayout.ZEN_ICON) / 2, inset = (d.half() - DrawLayout.ZEN_ICON) / 2;
            int iconSide = (right ? s[0] - cell[0] - d.half() : cell[0]) + inset;
            check(Math.abs(iconFoot - iconSide) <= 1 && iconFoot >= 2, "Zen Mode's icon the same " + iconFoot + " dp off the screen's foot as off its side edge, on " + s[0] + " x " + s[1]);
        }
        check(DrawLayout.ZEN.equals("Zen Mode") && DrawLayout.ZEN_LEAVE.equals("Leave Zen Mode") && DrawLayout.ZEN_HOLD.equals("Hold for Zen Mode"), "Zen Mode's words, Title Case");
    }
    /**
     * The drawing screen's whole layout (the owner, 2026-10-23: "moving the pages bar to the top. keep the zen in the
     * bottom left ... put the selected brush indicator to the right along the bottom"), as {@code PaintActivity.showInkWashBar}
     * builds it, on the Manta and the Nomad, portrait and landscape, either hand, rectangles in dp {left, top, right, bottom}:
     * the column from the top down to Zen Mode's cell; the page bar over the page, the page's width, from the column; the
     * canvas under it down to the brush line; the brush line along the foot from just past Zen Mode's cell to the far
     * edge. Nothing overlaps, and the canvas is as big as before the move (the bar and the line both off it, as they were).
     */
    static void screen() {
        for (int[] s : SCREENS) for (boolean right : new boolean[]{false, true}) {
            DrawLayout d = new DrawLayout(s[0], s[1]);
            String at = s[0] + "x" + s[1] + (right ? ", column right" : "");
            int w = s[0], h = s[1], columnLeft = right ? w - d.side : 0, pageLeft = right ? 0 : d.side;
            int[] column = {columnLeft, 0, columnLeft + d.side, d.columnHeight()};
            int[] bar = {pageLeft, 0, pageLeft + d.pageWidth(), d.pageBar()};
            int[] canvas = {pageLeft, d.pageBar(), pageLeft + d.pageWidth(), d.pageBar() + d.canvasHeight()};
            int[] z = DrawLayout.zenCell(w, h, d.side, d.half(), d.zenHeight(), right);
            int[] zen = {z[0], z[1], z[0] + d.half(), z[1] + d.zenHeight()};
            int top = h - d.footHeight();
            int[] line = right ? new int[]{0, top, w - d.lineStart(), top + DrawLayout.BRUSH_LINE} : new int[]{d.lineStart(), top, w, top + DrawLayout.BRUSH_LINE};
            check(bar[1] == 0 && bar[3] == canvas[1] && bar[0] == canvas[0] && bar[2] == canvas[2] && (right ? bar[2] == column[0] : bar[0] == column[2]),
                    "The page bar along the page's top, the page's width, from just past the column, the page under it, at " + at);
            check(canvas[3] == line[1] && line[3] <= h && zen[3] == h && line[1] + line[3] == zen[1] + zen[3] && column[3] == zen[1] && column[1] == 0,
                    "The canvas down to the brush line, centred on Zen Mode's cell, along the foot, the column from the top down to Zen Mode's cell, at " + at);
            // A clear gap after Zen Mode's cell (the owner, 2026-10-23: "move the active tool info over a bit").
            check(right ? line[2] == zen[0] - DrawLayout.LINE_GAP && line[0] == 0 : line[0] == zen[2] + DrawLayout.LINE_GAP && line[2] == w && DrawLayout.LINE_GAP >= 12,
                    "The brush line from " + DrawLayout.LINE_GAP + " dp past Zen Mode's button to the far edge, at " + at);
            int[][] parts = {column, bar, canvas, zen, line};
            for (int a = 0; a < parts.length; a++) for (int b = a + 1; b < parts.length; b++)
                check(!overlap(parts[a], parts[b]), "Nothing overlaps (" + a + " and " + b + ") at " + at);
            // The notebook's name in the foot's far corner (the owner, 2026-10-27): the corner Zen Mode's button is not in; the
            // line ends where it starts. At its widest ("bottom right name is cut off": 90 dp until then) it takes the room
            // the line's preview had and more, the line keeping its words' room and a note's gap.
            int[] name = right ? new int[]{0, top, d.statusCorner(), top + DrawLayout.BRUSH_LINE} : new int[]{w - d.statusCorner(), top, w, top + DrawLayout.BRUSH_LINE};
            int[] shortLine = right ? new int[]{name[2], line[1], line[2], line[3]} : new int[]{line[0], line[1], name[0], line[3]};
            check(!overlap(name, zen) && !overlap(name, shortLine) && !overlap(name, column) && !overlap(name, canvas) && name[3] <= h,
                    "The notebook's name clear of Zen Mode's button, the brush line, the column and the canvas at " + at);
            check(shortLine[2] - shortLine[0] == DrawLayout.lineLeast() && DrawLayout.lineLeast() == DrawLayout.LINE_PAD + DrawLayout.LINE_NAME + DrawLayout.NOTE_GAP + DrawLayout.LINE_END
                            && d.statusCorner() >= 90 + 86 && (d.compact || LabText.width("A Brief Inventory of Lint", TypeStyle.CAPTION.sp, 0) <= d.statusCorner()),
                    "The brush line still holds its words and a note's gap beside the name, the name up to " + d.statusCorner() + " dp (a long notebook name whole on the Manta) at " + at);
            // Zen Mode's icon sits in the column's middle: its axis the column's.
            check(Math.abs(2 * zen[0] + d.half() - (2 * columnLeft + d.side)) <= 1, "Zen Mode's button on the column's axis at " + at);
            // The canvas as big as with the bar at the foot: the screen less the column, the bar and the line.
            check(d.canvasHeight() == h - d.pageBar() - d.footHeight() && d.pageWidth() == w - d.side, "The canvas no smaller than before at " + at);
            // The column gained the bar's height and the line's, less Zen Mode's cell.
            check(d.columnHeight() > h - d.pageBar() - d.footHeight(), "The column taller than with the bar at the foot at " + at);
        }
    }
    /**
     * The column pass (the owner, 2026-10-23: "some things are a bit off with it.. layers section is a bit janky..
     * inconsistencies"): one inner margin each side and one centre axis for every row, two-up rows in two equal cells
     * meeting on it; the gaps between groups the same everywhere on a screen (no layers in the column since the refocus,
     * 2026-10-24).
     */
    static void column() {
        for (int[] s : SCREENS) {
            String at = s[0] + "x" + s[1];
            DrawLayout d = new DrawLayout(s[0], s[1]);
            check(d.edge >= 2 && d.inner + 2 * d.edge + DrawLayout.BAR_RULE == d.side && 2 * d.half() == d.inner,
                    "One margin each side, the two cells meeting on the axis, at " + at + ": " + d.edge + " dp, cells " + d.half());
            check(d.gap >= d.preferred()[DrawLayout.Item.GAP.ordinal()] * (d.fit < 1 ? 0 : 1) && d.gap <= DrawLayout.GAP_MOST * d.preferred()[DrawLayout.Item.GAP.ordinal()],
                    "The gaps between groups at most " + DrawLayout.GAP_MOST + " times their preferred at " + at + ": " + d.gap);
            // Menu level with the page bar's previews (the owner, 2026-10-23: "make the menu icon in the upper right in line
            // with the page previews"): its icon's middle the previews' middle, its row still a pen target.
            int menuMiddle = DrawLayout.PAD + d.menuRow() / 2, previews = DrawLayout.STRIP_EDGE + d.previewHeight() / 2;
            check(menuMiddle == previews && d.menuRow() >= 26 && java.util.Arrays.asList(DrawLayout.COLUMN).indexOf(DrawLayout.Part.MENU) == 0,
                    "Menu first in the column, centred on the page previews (" + menuMiddle + " and " + previews + " dp down) at " + at);
            // One "in use" mark, a side bar (the owner, 2026-10-23: "it should be a side line, not an underline"), 5 dp, 2 clear.
            check(DrawLayout.SIDE_BAR >= 5 && DrawLayout.BAR_GAP >= 1 && d.half() - DrawLayout.barRoom() - 2 >= 18,
                    "A side bar " + DrawLayout.SIDE_BAR + " dp wide beside a tool's icon, " + (d.half() - DrawLayout.barRoom() - 2) + " dp, at " + at);
            // A tray row's bar a little shorter than its row (the owner, 2026-10-27: "make it slightly shorter"), still as tall
            // as the stroke's place, so the bars of rows next to each other stand apart.
            int barTall = d.slot - 2 * DrawLayout.BAR_INSET;
            check(DrawLayout.BAR_INSET > 0 && barTall < d.slot && barTall >= d.trayStroke() && barTall * 4 >= d.slot * 3,
                    "A tray row's side bar " + barTall + " dp of its " + d.slot + " at " + at);
        }
    }
    static boolean overlap(int[] a, int[] b) { return a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]; }
    /** The column's items, rules, padding and stretch, added up, dp. */
    static int used(DrawLayout d) {
        return 2 * DrawLayout.PAD + d.menuRow() + DrawLayout.TIMER + d.layers + DrawLayout.total(d.heights(), d.trays) + d.spare;
    }
    static void fits() {
        for (int[] s : SCREENS) {
                DrawLayout d = new DrawLayout(s[0], s[1]);
                String at = s[0] + "x" + s[1];
                check(d.spare >= 0, "The column fits " + at + " without scrolling (" + d.spare + " left)");
                check(used(d) == d.columnHeight() && d.columnHeight() == s[1] - d.zenHeight(), "The column's items, rules, padding and stretch add up to its height at " + at);
                int[] h = d.heights(), most = d.preferred();
                for (int i = 0; i < h.length; i++)
                    // A tray row may be taller where fewer slots show (the Nomad's eight) and share the ten's room.
                    check(h[i] >= d.least()[i] && h[i] <= (i == DrawLayout.Item.BOX.ordinal() ? d.boxMost() : most[i] * (i == DrawLayout.Item.GAP.ordinal() ? DrawLayout.GAP_MOST : 1))
                            * (i == DrawLayout.Item.SLOT.ordinal() ? (DrawLayout.TRAY_ROWS + DrawLayout.TOOL_ROWS) / (double) (d.trays + DrawLayout.TOOL_ROWS) : 1), "Item " + DrawLayout.Item.values()[i] + " within its least and preferred at " + at);
                // The owner's pen targets (2026-10-19): a tray row about 26 dp, a face 26 to 28 dp square; nothing smaller. Tray
                // rows 24 at the least since the ten slots (2026-10-27), the Nomad in landscape's.
                check(d.icon >= 30 && d.slot >= 24 && d.face >= 27 && d.faceSide() >= 23 && d.half() >= 29 && d.wet >= 28,
                        "Targets at least 30 dp (tray rows 24, faces 27 tall and 23 square, the tools 29 wide, the small rows 28) at " + at);
                check(d.slot % 2 == 0, "Tray rows in whole halves at " + at);
            }
    }
    static void manta() {
        DrawLayout d = new DrawLayout(1024, 1365);
        check(!d.compact && d.fit == 1 && d.icon == 52 && d.slot == 34 && d.faceSide() == 26 && d.wet == 44,
                "The Manta in portrait: every item at its preferred height (icons 52, tray rows 34, faces 26 square, small rows 44: room to breathe since 2026-10-28) (fit " + d.fit + ")");
        // 175 before Shapes and Play's row (2026-10-30); the layer button's row (2026-11-06) takes its 66 from it.
        check(d.spare + d.layers > 120 && d.spare > 50, "and the room the tray and the pairs free is white space before the wet indicator, the ten slots, Zoom and Fun in too: " + d.spare + " dp");
        // In landscape, since the Edit buttons and the page turner left the column (2026-10-19), at its preferred heights
        // too (with them: icons 48 dp, tray rows 28, faces 27 square).
        DrawLayout landscape = new DrawLayout(1365, 1024);
        // With the brush line under the column (2026-10-22) a little under them: icons 52 or more, tray rows 28, faces 27 square.
        // The layer button's row (2026-11-06) takes its 66 dp from them: tray rows 30 (32 before).
        check(landscape.fit > .75 && landscape.spare >= 0 && landscape.icon >= 42 && landscape.slot >= 30 && landscape.faceSide() >= 25,
                "The Manta in landscape near its preferred heights (fit " + landscape.fit + ", icons " + landscape.icon + ", tray rows " + landscape.slot
                        + ", faces " + landscape.faceSide() + ", " + landscape.spare + " dp to spare)");
        int nomad = new DrawLayout(749, 998).faceSide(), nomadLandscape = new DrawLayout(998, 749).faceSide();
        check(d.faceSide() == 26 && nomad == 23 && nomadLandscape == 23, "Faces 26 dp square on the Manta, 23 on the Nomad, whose column holds two, their side bars and the gap: "
                + d.faceSide() + ", " + nomad + ", " + nomadLandscape);
    }
    /**
     * The tray and the pairs (the owner's mockups "B Pen tray" and "B Pairs", 2026-10-19): the eight slots (the owner's
     * "two more brush slots", 2026-10-19) a row each; four rows of faces, a size beside a grey; the smudge tool and the
     * eraser tool buttons under them.
     */
    static void tray() {
        int[] count = DrawLayout.count(DrawLayout.TRAY_ROWS);
        check(ToolSlots.COUNT == 9 && DrawLayout.TRAY_ROWS == ToolSlots.COUNT && DrawLayout.TOOL_ROWS == 3 && count[DrawLayout.Item.SLOT.ordinal()] == DrawLayout.TRAY_ROWS + DrawLayout.TOOL_ROWS && ToolSlots.TOOLS == ToolSlots.COUNT + 4,
                "Nine tray rows, the slots', and Fill in's, the smudge tool's and the eraser's under them, twelve rows as with ten slots; the stamp tool off the column (the refocus, 2026-10-24)");
        check(DrawLayout.greys(ToolSlots.FILL) && DrawLayout.greys(0) && !DrawLayout.greys(ToolSlots.SMUDGE) && !DrawLayout.greys(ToolSlots.ERASER), "Fill in has a grey, as a slot; the smudge tool and the eraser none");
        // The owner's drawings of the smudge tool and the eraser (2026-10-26; traced the same day): at most a Manta tray
        // row's stroke tall, so drawn 1:1 there.
        DrawLayout manta = new DrawLayout(1024, 1365);
        for (String name : new String[]{"tool_smudge", "tool_eraser"}) drawn(name, mantaPx(manta.trayStroke()));
        // The size slider (the owner, 2026-10-24) in the sizes' and the swatches' room: the column's height as it was.
        // The readout under the slider and the strip (the owner, 2026-10-24: "put the size selected and the current shade below")
        // in the Dropper's two rows, the Dropper beside the wet indicator: the column as tall as it was.
        // The box half as long again where the column has room (the owner, 2026-10-29: "maybe 1.5x the height"), four rows at the least.
        int box = DrawLayout.Item.BOX.ordinal(), face = DrawLayout.Item.FACE.ordinal();
        check(count[face] == DrawLayout.READOUT_ROWS && count[box] == 1 && DrawLayout.FACE_ROWS == 4 && DrawLayout.FACE_ROWS == ToolSwatches.COUNT
                && DrawLayout.READOUT_ROWS == 2 && DrawLayout.BOX_ROWS == 6 && DrawLayout.LEAST[box] == DrawLayout.FACE_ROWS * DrawLayout.LEAST[face]
                && DrawLayout.ROOMY[box] == DrawLayout.FACE_ROWS * DrawLayout.ROOMY[face] && DrawLayout.COMPACT[box] == DrawLayout.FACE_ROWS * DrawLayout.COMPACT[face],
                "The slider and the strip four rows of faces long as the four sizes were, up to six from the room to spare; the readout two over them");
        check(DrawLayout.FACE_GAP + 26 == DrawLayout.ROOMY[DrawLayout.Item.FACE.ordinal()]
                && DrawLayout.FACE_GAP + 23 == DrawLayout.COMPACT[DrawLayout.Item.FACE.ordinal()] && DrawLayout.FACE_GAP + 23 == DrawLayout.LEAST[DrawLayout.Item.FACE.ordinal()],
                "A row of faces: its 26 dp squares (23 on the Nomad and at the least), the gap to the next");
        // The strip's tab 3 dp a dp clear; the slider and the strip a gap apart across the axis.
        check(DrawLayout.FACE_MARK == 3 && DrawLayout.FACE_MARK_GAP >= 1 && DrawLayout.PAIR_PAD * 2 == DrawLayout.FACE_GAP, "The tab 3 dp a dp clear, the slider and the strip " + DrawLayout.FACE_GAP + " dp apart");
        for (int[] s : SCREENS) {
            DrawLayout at = new DrawLayout(s[0], s[1]);
            int pair = 2 * at.faceSide() + DrawLayout.FACE_GAP;
            check(pair + 2 * DrawLayout.faceMarkRoom() <= at.inner && 2 * at.half() <= at.inner && at.box / DrawLayout.FACE_ROWS - at.faceSide() >= DrawLayout.FACE_GAP,
                    "Two faces " + DrawLayout.FACE_GAP + " dp apart both ways inside the column at " + s[0] + "x" + s[1] + ": " + pair + " of " + at.inner);
            // The grey strip as long as the four sizes' squares were, exactly a square wide (the owner, 2026-10-23: "make the
            // shade boxes the same width and aligned with the grey picker"), its tab in the room right of it; the readout
            // under it and the slider; the pen takes the slider's whole cell.
            int strip = at.faceSide(), length = at.box - DrawLayout.FACE_GAP;
            check(strip >= ShadeStrip.SIDE_WIDTH + 2 * ShadeStrip.SIDE_REACH && length == DrawLayout.trackFoot(at.box) - DrawLayout.trackTop()
                    && DrawLayout.FACE_GAP / 2 + strip + DrawLayout.faceMarkRoom() <= at.half()
                    && DrawLayout.STRIP_MARK >= 2 * DrawLayout.FACE_MARK, "The grey strip " + strip + " dp wide, " + length + " long, as the four sizes' squares, its tab legible, at " + s[0] + "x" + s[1]);
            // The slider (2026-10-24, "a right triangle along the right side ... with dot along the right side similar to
            // the grey picker"): the triangle a square wide and its tab's room right of it fill the left cell, the axis's
            // gap included, as the strip and its tab fill the right one.
            int slider = at.box, readout = DrawLayout.READOUT_ROWS * at.face;
            check(DrawLayout.sliderMarkRoom() >= DrawLayout.FACE_MARK_GAP + DrawLayout.FACE_MARK + DrawLayout.FACE_GAP / 2
                    && at.faceSide() + DrawLayout.sliderMarkRoom() <= at.half() && slider >= 100,
                    "The slider " + at.faceSide() + " dp wide and its tab's " + DrawLayout.sliderMarkRoom() + " in the " + at.half() + " dp cell, " + slider + " long, at " + s[0] + "x" + s[1]);
            // A drag's redraw (the owner: "the redraw is blinky and slow"): before, every move put the slider's whole cell on
            // the panel in grey (the thumb across the grey wedge) and the nib's preview on the page; now the tab's strip alone,
            // ink and paper, which the fast mode takes. Measured here in dp squared.
            int before = at.half() * (slider + readout), now = DrawLayout.sliderMarkRoom() * slider;
            check(now * 5 <= before, "A drag puts " + now + " dp\u00b2 on the panel, " + before + " before and the page's preview, at " + s[0] + "x" + s[1]);
            // The readout: one dot of the size in the grey (the owner: "it can be one thing"), the whole width; the Dropper
            // and the Lasso under it, each half the column, the drop's old row's height.
            check(Math.min(at.inner, readout) - DrawLayout.FACE_GAP >= 2 * at.faceSide() - DrawLayout.FACE_GAP && at.half() >= 26 && at.wet >= 28,
                    "The readout " + at.inner + " x " + readout + " dp, the Dropper and the Lasso " + at.half() + " x " + at.wet + " each, at " + s[0] + "x" + s[1]);
        }
        // Before (faf7e9f), on the Manta in portrait: eight 54 dp tool tiles (432 dp) and six 56 dp faces (336 dp). The
        // mockup's tray was 220 dp for six rows; eight at 30 dp are 240. Eight rows measured, as then (ten since 2026-10-27).
        DrawLayout d = new DrawLayout(1024, 1365);
        int tray = 8 * d.slot, faces = d.box;  // the box's; the four sizes' were 120
        // A tray row 4 dp taller since the side bar (the owner, 2026-10-23: "a tad more vertical spacing between the brushes");
        // the box half as long again as the pairs' 120 since 2026-10-29.
        check(tray <= 432 * 2 / 3 && tray <= 220 * 8 / 6 && faces <= 120 * 3 / 2,
                "The tray about the mockup's 220 dp for six (" + tray + " for eight), the box at most half again its 120 for the pairs (" + faces + " dp, the eraser's row " + d.icon + ")");
        for (int[] s : SCREENS) {
            DrawLayout at = new DrawLayout(s[0], s[1]);
            // The strokes their size again with no underline's room, 8 dp between them (the owner, 2026-10-23: "a tad more
            // vertical spacing between the brushes"); 16 dp at the least since the ten slots (2026-10-27), the Nomad turned.
            check(at.trayStroke() >= 16 && at.trayStroke() + 2 * DrawLayout.TRAY_TOP == at.slot && 2 * DrawLayout.TRAY_TOP >= 8,
                    "A tray row's stroke has 16 dp at least, 8 between strokes, at " + s[0] + "x" + s[1] + " (" + at.slot + " dp row)");
            // No number (the owner, 2026-10-19): the stroke the row's width but its side bar's room, from the screen's edge
            // (the owner, 2026-10-27: "move tools slightly left, touching edge"): the row reaches over the column's margin.
            int stroke = at.inner + at.edge - DrawLayout.barRoom();
            check(stroke >= at.inner - 5, "A tray stroke as long as the row less its side bar, at " + s[0] + "x" + s[1] + ": " + stroke + " of " + at.inner + " dp");
        }
    }
    /**
     * Long press to customize (the owner, 2026-10-19: "instead of edit buttons, we use long press as a way to
     * customize"): no Edit button anywhere in the column. A long press on a tray row opens the tool picker on it ({@link #picker}), on a size
     * or grey face the Sizes and Greys window on that face, on the eraser Clear Page: one each, on different controls
     * (InkWashBar, SwatchFaces), so nothing collides; a tap on the smudge tool still selects it and again opens its
     * chooser (it has no long press). A rub on a grey is movement, which calls the long press off (SwatchFace).
     */
    static void longPress() {
        for (DrawLayout.Part part : DrawLayout.COLUMN) check(!part.name().startsWith("EDIT"), "No Edit button in the column: " + part);
        check(DrawLayout.tap(ToolSlots.SMUDGE, ToolSlots.SMUDGE) == DrawLayout.Tap.SMUDGES, "A second tap on the smudge tool is its own case (it closes the chooser; a long press opens it)");
        check(LoadedPaint.RUB_DP > 0, "A rub is movement past " + LoadedPaint.RUB_DP + " dp, so a still long press stays a long press");
        // A pen held still on a grey jitters a pixel or two for the half second of a long press; that must not add up to a rub.
        float dp = 2.6f, x = 0, y = 0, travel = 0;
        for (int n = 0; n < 200; n++) {
            float nx = (n % 2 == 0 ? 1 : -1) * 1.5f, ny = (n % 3 == 0 ? 1 : -1) * 1.5f;
            if (LoadedPaint.travels((float) Math.hypot(nx - x, ny - y), dp)) { travel += (float) Math.hypot(nx - x, ny - y); x = nx; y = ny; }
        }
        check(!LoadedPaint.rub(travel, dp), "Two hundred samples of tremor on a held grey are no rub, so its long press opens the window: " + travel + " px");
        check(LoadedPaint.travels(5 * dp, dp) && LoadedPaint.rub(3 * 5 * dp, dp), "A real rub (steps of 5 dp) still counts");
    }
    /**
     * The column's order (the owner, 2026-10-19: "move [the smudge tool] and the eraser below the swatches and sizes",
     * then "fill button beside the smudger ... four tools that would stack nicely"): menu, Brush Lab, undo and redo; the
     * eight slots; the pairs; the four tool buttons two by two; then Fun and the wet indicator. Its rules and gaps are the
     * ones the heights count.
     */
    static void order() {
        java.util.List<DrawLayout.Part> column = java.util.Arrays.asList(DrawLayout.COLUMN);
        int tray = column.indexOf(DrawLayout.Part.TRAY);
        // Layout v3, the owner's mockup (2026-10-26): Menu alone; the brushes; a gap, the smudge tool and the eraser; undo,
        // redo, the Dropper, the Lasso and the Trash; the dot, the slider and the strip; the wet indicator, Finger,
        // Template Visibility and Play; the countdown under them; white space at the foot.
        // Layout v4 (the owner, 2026-10-26: "move undo redo eye drop lasso and recycle to below picker"): the block under the
        // dot, the slider and the strip, a gap either side of it.
        check(column.equals(java.util.Arrays.asList(DrawLayout.Part.MENU, DrawLayout.Part.GAP, DrawLayout.Part.TRAY, DrawLayout.Part.GAP, DrawLayout.Part.TOOLS,
                        DrawLayout.Part.GAP, DrawLayout.Part.FACES, DrawLayout.Part.LAYERS, DrawLayout.Part.GAP, DrawLayout.Part.BLOCK, DrawLayout.Part.GAP,
                        DrawLayout.Part.TIMER, DrawLayout.Part.STRETCH, DrawLayout.Part.PLAY)),
                "Top to bottom: Menu, the nine slots, Fill in, the smudge tool and the eraser, the sizes and greys, the layer button (2026-11-06), the pickers, the wet indicator, the countdown: " + column);
        for (DrawLayout.Part part : DrawLayout.COLUMN) check(!part.name().startsWith("SWATCH"), "No swatches in the column: " + part);
        // The owner (2026-10-28): undo and redo, Shapes, Play and Refresh out, two rows freed; the sand timer beside the drop.
        // The owner (2026-10-30): Shapes and Play back, a small row of their own. The owner (2026-11-06): the sand timer, Show
        // Kinds and Play to the menu, the drop the one small row.
        check(DrawLayout.TOOL_ROWS == 3 && DrawLayout.BLOCK_ROWS == 2 && DrawLayout.PLAY_ROWS == 1 && DrawLayout.GRID_COLUMNS == 2,
                "The rows past the tray: Fill in's (2026-11-06), the smudge tool's and the eraser's; the Dropper and the Lasso, Refresh; the drop");
        // The countdown at the top (the owner, 2026-10-21): its row kept on every screen, its widest words inside the column.
        check(DrawLayout.TIMER >= 20 && TypeStyle.LABEL.sp >= 12, "The countdown's row 20 dp or more, its words 12 sp or more");
        for (int[] s : SCREENS) {
            double wide = LabText.width(DrawLayout.WIDEST_COUNTDOWN, TypeStyle.LABEL.sp, TypeStyle.LABEL.tracking);
            check(wide <= new DrawLayout(s[0], s[1]).inner, "\"60:00\" fits the column at " + s[0] + "x" + s[1] + ": " + wide + " dp");
            // The wet indicator a small row's cell, two cells in the column's inside.
            DrawLayout d = new DrawLayout(s[0], s[1]);
            check(DrawLayout.GRID_COLUMNS * d.half() <= d.inner && d.wet >= 28, "The wet indicator's " + d.half() + " x " + d.wet + " dp at " + s[0] + "x" + s[1]);
        }
        // The owner (2026-10-22): "the menu on top should be phosphor list and not have a line underneath".
        check(column.subList(0, tray).equals(java.util.Arrays.asList(DrawLayout.Part.MENU, DrawLayout.Part.GAP))
                && column.subList(column.indexOf(DrawLayout.Part.TIMER), column.size()).equals(java.util.Arrays.asList(DrawLayout.Part.TIMER, DrawLayout.Part.STRETCH, DrawLayout.Part.PLAY)),
                "Menu alone over the tray (no Brush Lab, no Zoom: 2026-10-21), white space under the countdown (no layers since the refocus, 2026-10-24; the layer button under the picker box, 2026-11-06), Refresh at the foot (2026-10-29)");
        for (int[] s : SCREENS) {
            DrawLayout d = new DrawLayout(s[0], s[1]);
            int apart = d.ghostMargin() + (d.icon - DrawLayout.ZEN_ICON) / 2 + (d.zenHeight() - DrawLayout.ZEN_ICON) / 2;
            check(d.ghostMargin() <= d.spare && (apart == DrawLayout.ZEN_ROOM || d.ghostMargin() == d.spare || d.ghostMargin() == 0),
                    "Refresh's icon about a centimetre over Zen Mode's at " + s[0] + "x" + s[1] + ": " + apart + " dp");
        }
        for (DrawLayout.Part part : DrawLayout.Part.values())
            if (part != DrawLayout.Part.GAP) check(java.util.Collections.frequency(column, part) == 1, part + " once in the column");
        int gaps = java.util.Collections.frequency(column, DrawLayout.Part.GAP);
        // No rules: the groups set apart by space alone (the column pass, 2026-10-23: no ink divider).
        check(gaps == DrawLayout.GAPS && gaps == DrawLayout.count(DrawLayout.TRAY_ROWS)[DrawLayout.Item.GAP.ordinal()],
                "No rules in the column, and its " + gaps + " gaps between groups, as the heights count");
        int icons = 0, small = 0, rows = 0;
        for (DrawLayout.Part part : column) {
            icons += part == DrawLayout.Part.BLOCK ? DrawLayout.BLOCK_ROWS : 0;
            small += part == DrawLayout.Part.PLAY ? DrawLayout.PLAY_ROWS : 0;
            rows += part == DrawLayout.Part.TRAY ? DrawLayout.TRAY_ROWS : part == DrawLayout.Part.TOOLS ? DrawLayout.TOOL_ROWS : 0;
        }
        int[] count = DrawLayout.count(DrawLayout.TRAY_ROWS);
        check(icons == count[DrawLayout.Item.ICON.ordinal()] && small == count[DrawLayout.Item.WET.ordinal()] && rows == count[DrawLayout.Item.SLOT.ordinal()],
                "and its icon-high rows, its small rows and its tray-high rows (the smudge tool's and the eraser's too)");
        // The items' total at their fitted heights and what is left, dp, on the four screens: with three rows of faces
        // (66f67ff) 952 and 393, 952 and 52, 842 and 136, 722 and 7 (Words in the column there); with four, the tool
        // buttons two by two (Fill's cell counted) and the faces' bars, 1006 and 341, 1006 and 0, 888 and 92, 722 and 11
        // (eca20e8); with Zoom under redo, 1062 and 283, 987 and 17, 936 and 42, 722 and 11 (69f99de); with the two Edit
        // buttons too, 1126 and 219, 990 and 14, 962 and 16, 729 and 2, the Nomad's page turner folded in landscape
        // (36b49b4); with eight slots, no Edit buttons and no page turner (2026-10-19), Zoom and Fun in on all four, 902 and
        // 445, 902 and 104, 800 and 180, 724 and 7 (2f41cb5); with undo and redo side by side, no Brush Lab and no Zoom (2026-10-21),
        // 734 and 619, 734 and 278, 656 and 330, 656 and 81; with the stamp tool's row under them (2026-10-21), 790 and 563,
        // 790 and 222, 704 and 282, 704 and 33; with Fun beside the stamp tool (2026-10-21), 734 and 621, 734 and 280, 656 and
        // 332, 656 and 83; with the countdown's 24 dp at the top (2026-10-21), 734 and 597, 734 and 256, 656 and 308, 656 and 59;
        // with it two lines tall, 40 dp, for Timed Sections (2026-10-21), 734 and 581, 734 and 240, 656 and 292, 656 and 43;
        // with the layers back in the column, a page alone (2026-10-21, folded on the Nomad in landscape), 902 and 407, 902
        // and 66, 800 and 142, 678 and 19; with MonoPaint's four swatches two by two under the faces and the wet indicator
        // under them, 44 dp on the Manta, Zen Mode beside Menu (2026-10-22; the Nomad in landscape under its preferred heights),
        // 966 and 343, 966 and 2, 864 and 78, 692 and 5; with the droplet always shown and its row 36 dp on the Manta, no
        // blank band under the swatches (2026-10-22), 958 and 351, 958 and 10, 858 and 84, 689 and 8; with Zen Mode's row
        // at the foot, not beside Menu (2026-10-22: "it should be in the same spot it is when it's active"), 1014 and 295,
        // 946 and 22, 906 and 36, 696 and 1; with Zen Mode on the brush line, which runs the screen's width under the
        // column (2026-10-22: "can the bottom bar overlap and it be all the way to the left?"), 8 dp of foot room, 958 and
        // 329, 924 and 22, 858 and 62, 668 and 7; with the brush line back over the page bar, under the column's foot too,
        // Zen Mode in the corner under it, no rule under Menu or between the layers' add buttons (2026-10-22: "the same
        // vertical position, just shifted over to the left"), the Nomad in landscape at its TIGHT icons (35 dp), 958 and 299,
        // 896 and 20, 858 and 36, 643 and 2; with the page bar at the top and the column down to Zen Mode's cell, the
        // brush line beside it (2026-10-23: "moving the pages bar to the top"), the Nomad in landscape's icons 37 dp, 958
        // and 325, 916 and 26, 858 and 58, 658 and 9; with the column pass (2026-10-23: one inner margin, no rule, a gap
        // before the layers, the gaps sharing what a full stack leaves, the layers' label and one +):
        // With Zen Mode's cell as tall as the column is wide (2026-10-23), the Nomad in landscape's icons 33 dp: 1032 and 193, 859 and 25, 818 and 50, 637 and 2.
        // With the side bars (2026-10-23: tray rows 4 dp taller, faces 4 dp shorter, no LAYERS label, Menu level with the
        // page bar's previews, its own row): 984 and 231, 864 and 10, 772 and 90, 610 and 3.
        // With a page alone the layers icon, no thumbnail (2026-10-23: "just have it as a layers icon to start"): 928 and
        // 287, 808 and 66, 724 and 138, 610 and 3 (folded, the same).
        // With a fourth row of tools, Template Visibility and the Lasso's cell (2026-10-23), the Nomad in landscape's icons
        // 28 dp: 984 and 231, 864 and 10, 772 and 90, 610 and 3.
        // With the refocus (2026-10-24: the stamp tool, Finger, Magnetic Poetry and Mirrors out of the grid, Play in, three
        // rows; no layers, no fold): 832 and 383, 832 and 42, 740 and 122, 611 and 2 (the Nomad in landscape at its least, 40 dp icons).
        // With the Lasso beside the Dropper in the drop's old row and the drop on the brush line (2026-10-24), two rows of
        // tools: 776 and 439, 776 and 98, 692 and 170, 612 and 1.
        // With the eraser on its own row (2026-10-24: "place eraser button on its own"), three rows of tools again: 832 and
        // 383, 832 and 42, 740 and 122, 611 and 2.
        // With the smudge tool and the eraser straight under the tray, Finger beside the smudge tool, the drop beside Play
        // under the Dropper and Template Visibility beside Menu (2026-10-26): the same rows, two gaps, not three: 792 and
        // 423, 792 and 82, 708 and 154, 613 and 0 (the Nomad in landscape still at its least, nothing under it).
        // Layout v3 (the owner's mockup, 2026-10-26): Menu alone, the smudge tool and the eraser a tray row each with their
        // drawings, undo, redo, the pickers and the Trash in one block of 44 dp rows (56 until then, 30 the least, 40
        // before), the drop, Finger, Template Visibility and Play two small rows, the countdown under them, five gaps:
        // With Refresh on its own row (2026-10-27: "put flash ghost on its own line in bar"; 924 and 291, 874 and 0, 830 and 32,
        // 613 and 0 before), the Nomad in landscape keeping it beside the wet indicator: 960 and 255, 870 and 4, 859 and 3,
        // 613 and 0. With ten slots (2026-10-27), the tray rows 24 dp at the least: 1028 and 187, 872 and 2, 858 and 4, 611 and 2.
        // With undo and redo, Shapes, Play and Refresh out of the column, an icon row and a small row fewer (2026-10-28), the
        // Manta's icon rows 52 dp and small rows 44 (44 and 36 until then):
        int[][] now = NOW;
        StringBuilder all = new StringBuilder();
        for (int[] sc : SCREENS) { DrawLayout d = new DrawLayout(sc[0], sc[1]); all.append(DrawLayout.total(d.heights(), d.trays)).append(" and ").append(d.spare).append(", "); }
        for (int i = 0; i < SCREENS.length; i++) {
            DrawLayout d = new DrawLayout(SCREENS[i][0], SCREENS[i][1]);
            int items = DrawLayout.total(d.heights(), d.trays);
            check(items == now[i][0] && d.spare == now[i][1], "The column at " + SCREENS[i][0] + "x" + SCREENS[i][1] + ": " + items + " dp of items, " + d.spare + " left (all four: " + all + ")");
            // The tool buttons two by two: each half the column and an icon button tall, at least 29 x 40.
            check(DrawLayout.GRID_COLUMNS * d.half() <= d.inner && d.half() >= 29 && d.icon >= d.least()[0] && d.icon >= 30 && d.spare >= 0,
                    "The tool cells fit at " + SCREENS[i][0] + "x" + SCREENS[i][1] + ": " + d.half() + " x " + d.icon + " dp each");
        }
    }
    static void shorter() {
        // Shorter than any of the four (a window with bars): everything shrinks alike rather than run off the end.
        DrawLayout d = new DrawLayout(998, 500);
        check(d.spare >= 0 && d.fit < 0 && used(d) == d.columnHeight(), "A shorter screen still fits (" + d.spare + " left)");
    }
    static void side() {
        for (int[] s : SCREENS) {
            DrawLayout d = new DrawLayout(s[0], s[1]);
            String at = s[0] + "x" + s[1];
            check(d.side >= 56 && d.side <= 84 && d.side == (d.compact ? 62 : 72) && d.inner == d.side - 2 * d.edge && d.edge >= 2,
                    "The column 72 dp wide on the Manta, 62 on the Nomad (58 before the faces' bars), at " + at);
            check(2 * d.half() <= d.inner, "The eraser and Finger side by side inside the column at " + at);
            check(d.pageWidth() == s[0] - d.side && d.pageHeight() == s[1] - d.pageBar(), "The page gets what the column and the page bar leave at " + at);
            // Design C: a bar 76 (Manta) / 56 (Nomad) tall, a side bar 76 / 62 wide.
            int before = (s[0] - (d.compact ? 62 : 76)) * (s[1] - (d.compact ? 56 : 76));
            check(d.pageWidth() * d.pageHeight() > before, "More page than with the bar on top at " + at);
        }
    }
    static void chooser() {
        for (int[] s : SCREENS) {
            DrawLayout d = new DrawLayout(s[0], s[1]);
            String at = s[0] + "x" + s[1];
            check(d.chooserGrid(1)[0] == 1 && d.chooserGrid(1)[1] == 1 && d.chooserGrid(2)[0] == 2 && d.chooserGrid(2)[1] == 1 && d.chooserGrid(5)[1] == 3,
                    "The smudge chooser is as big as its cards need: one card, one cell; two, a row; five, three rows at " + at);
            for (int count = 1; count <= 40; count++) {
                int[] grid = d.chooserGrid(count);
                check(d.chooserWidth(count) <= d.pageWidth() && d.chooserHeight(count) <= s[1] - 2 * DrawLayout.PAD, "The chooser of " + count + " fits beside the column at " + at);
                check(grid[0] <= DrawLayout.CHOOSER_COLUMNS && grid[1] <= DrawLayout.CHOOSER_MOST_ROWS && d.chooserPaged(count) == (count > grid[0] * grid[1]), "and pages past " + grid[0] * grid[1] + " cards at " + at);
            }
            check(d.chooserRows() == DrawLayout.CHOOSER_MOST_ROWS, "Three rows of cards on every screen at " + at);
        }
    }
    static void taps() {
        int eraser = ToolSlots.ERASER, smudge = ToolSlots.SMUDGE;
        check(DrawLayout.tap(0, 3) == DrawLayout.Tap.SELECT && DrawLayout.tap(3, eraser) == DrawLayout.Tap.SELECT, "Another tool: select it");
        // The owner (2026-10-19): a tray row only selects; its brush is changed by a long press on it.
        for (int slot = 0; slot < ToolSlots.COUNT; slot++) check(DrawLayout.tap(slot, slot) == DrawLayout.Tap.NOTHING, "The selected slot again: nothing, no panel");
        check(DrawLayout.tap(eraser, eraser) == DrawLayout.Tap.NOTHING, "The selected eraser again: nothing");
        check(DrawLayout.tap(2, smudge) == DrawLayout.Tap.SELECT && DrawLayout.tap(smudge, eraser) == DrawLayout.Tap.SELECT && DrawLayout.tap(smudge, smudge) == DrawLayout.Tap.SMUDGES,
                "The smudge tool: a tap selects it; again, its chooser");
        check(DrawLayout.greys(0) && DrawLayout.greys(7) && !DrawLayout.greys(eraser) && !DrawLayout.greys(smudge), "Greys for a slot (the eighth too), sizes only for the eraser and the smudge tool");
        // Fill's paint bucket is retired (the owner, 2026-10-23: "just a brush with fill in the lines"): no button in its cell
        // and no Fill mode for the pen, which was never kept between launches, so the slot it filled with is what stays selected.
        try {
            String bar = source("InkWashBar"), ink = source("InkWash"), pad = source("DrawingPad");
            check(!bar.contains("paint_bucket") && !bar.contains("FillTool") && !ink.contains("filling") && !pad.contains("fillToolTap") && !pad.contains("inkWash.fill("),
                    "No paint bucket on the bar and no Fill mode for the pen");
        } catch (java.io.IOException e) { throw new AssertionError("the bar's source: " + e); }
    }
    /**
     * The owner's drawing {@code name}, at most {@code tallest} px tall: traced, a vector in res/drawable sized in px, black
     * on nothing (no tint anywhere); or, kept for its grey (2026-10-26), a PNG in res/drawable-nodpi of the panel's 16 greys.
     */
    static void drawn(String name, int tallest) {
        java.io.File vector = new java.io.File("app/src/main/res/drawable/" + name + ".xml"), png = new java.io.File("app/src/main/res/drawable-nodpi/" + name + ".png");
        check(vector.isFile() != png.isFile(), name + ": one drawing, a vector or a PNG");
        try {
            if (vector.isFile()) {
                String xml = new String(java.nio.file.Files.readAllBytes(vector.toPath()));
                java.util.regex.Matcher height = java.util.regex.Pattern.compile("android:height=\"(\\d+)px\"").matcher(xml);
                check(height.find() && Integer.parseInt(height.group(1)) <= tallest && xml.contains("android:width=\"") && !xml.contains("tint")
                        && xml.split("android:fillColor=").length == 2 && xml.contains("android:fillColor=\"#000000\""), name + ": a black vector in px, at most " + tallest + " px tall");
                return;
            }
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(png);
            check(image.getHeight() <= tallest && image.getType() == java.awt.image.BufferedImage.TYPE_BYTE_GRAY, name + ": grey, at most " + tallest + " px tall (" + image.getHeight() + " px)");
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                check(image.getRaster().getSample(x, y, 0) % 17 == 0, name + ": the panel's 16 greys only, at " + x + "," + y);
        } catch (java.io.IOException e) { throw new AssertionError(name + ": " + e); }
    }
    /**
     * The tool picker (the owner, 2026-10-25: "long press on any slot to open. single tap selects the tool"): every Basic
     * tool it lists is one, with its drawing ({@link #drawn}, at most 116 px tall); it fits beside the
     * column in three columns or more on all four screens without scrolling; Brush Lab and Advanced Title Case, two words
     * at most. A slot's long press opens it (a dirty one's Tools too); a tap on the selected slot still does nothing.
     */
    static void picker() {
        // The owner's list (2026-11-06): a row for every shown tool, once, each a drawing in res or null (its stroke).
        // Then the bar's own tools out of the picker since 2026-11-06, Fill in and the smudge tool's Water, keeping their drawings.
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.List<String> bars = java.util.Arrays.asList(ToolSlots.FILL_TOOL, "Water");
        for (String[] tool : DrawLayout.PICKER) {
            check(seen.add(tool[0]), "Listed once: " + tool[0]);
            check(java.util.Arrays.asList(ToolPresets.SHOWN).contains(tool[0]) != bars.contains(tool[0]), "A shown tool or the bar's own: " + tool[0]);
            if (tool[1] != null) drawn(tool[1], 116);
            check(java.util.Objects.equals(tool[1], DrawLayout.drawing(ToolPresets.named(tool[0]).brush())), tool[0] + " shows " + tool[1]);
        }
        check(seen.size() == ToolPresets.SHOWN.length + bars.size() && seen.containsAll(bars), "Every shown tool has its row, and Fill in and Water theirs: " + seen.size());
        check(!ToolPresets.shown().contains(ToolKits.preset("Fill in")) && !ToolPresets.shown().contains(ToolKits.preset("Water")), "Fill in and Water out of the picker (the bar's own)");
        check(!seen.contains(DrawLayout.SHAPES), "Shapes is on the side bar, not in the picker");
        // The release pare-down (2026-11-05): the picker shows the shown tools; grouped by kind since 2026-11-06 (pickerGroups).
        java.util.List<ToolPresets.Preset> cells = new DrawLayout.Picker(null, false).tools;
        check(cells.size() == ToolPresets.shown().size() && cells.containsAll(ToolPresets.shown()), "The picker shows the release's list: " + cells.size());
        pickerGroups();
        // Sumi and Comb, shelf tools, show their strokes (Comb's drawing is Big sky's since 2026-11-06).
        for (String name : new String[]{"Sumi", "Comb", "Fude pen"}) check(DrawLayout.drawing(ToolKits.preset(name).brush()) == null, name + " on the shelf shows its stroke");
        // A slot still holding the Water brush (retired 2026-10-31) shows its drawing, Water's.
        check(DrawLayout.drawing(FloatChecks.find("Water brush").brush()) == null, "The retired Water brush shows its stroke: its drawing is Water's now");
        // Ms. Sketch was Mr. Sketch: a brush kept under the old name still comes from it.
        Brush sketch = ToolKits.preset("Ms. Sketch").brush();
        check(ToolPresets.origin(Brush.read("Mr. Sketch\t" + sketch.write().split("\t")[1])) == ToolKits.preset("Ms. Sketch")
                && DrawLayout.edited(Brush.read("Mr. Sketch\t" + sketch.write().split("\t")[1])) == null, "Mr. Sketch, kept, is Ms. Sketch unchanged");
        // Renamed 2026-11-06, their codes and seeds kept: a slot holding one under its old name still comes from it.
        for (String[] renamed : new String[][]{{"Cracks", "Cracking paint"}, {"Big sky", "Flat brush"}, {"Backrun", "Watercolour backrun"}}) {
            Brush kept = Brush.read(renamed[0] + "\t" + ToolKits.preset(renamed[1]).brush().write().split("\t")[1]);
            check(ToolPresets.origin(kept) == ToolKits.preset(renamed[1]) && DrawLayout.edited(kept) == null, renamed[0] + ", kept, is " + renamed[1] + " unchanged");
        }
        // Watercolour washy, the bar's, shows as Watercolour wash (2026-11-06), its code kept: a slot holding it under the old
        // name keeps its drawing and offers no reset; changed, it resets to the bar's own.
        int wash = java.util.Arrays.asList(ToolSlotsChecks.NAMES).indexOf("Watercolour wash");
        Brush washy = Brush.read("Watercolour washy\t" + ToolSlots.FIRST[wash][2]);
        check(ToolSlots.first(wash).name().equals("Watercolour wash") && "tip_soft_wash".equals(DrawLayout.drawing(washy)) && DrawLayout.edited(washy) == null
                && "tip_soft_wash".equals(DrawLayout.drawing(ToolSlots.first(wash))) && DrawLayout.drawing(Brush.read("Watercolour washy 2\t" + ToolSlots.FIRST[wash][2])) == null,
                "Watercolour washy, kept, is Watercolour wash: its drawing, no reset; a copy its stroke");
        ModularBrush tunedWashy = washy.parts.copy(); tunedWashy.part(BrushPart.TIP).set(PartDial.WEIGHT, 1.3);
        ToolPresets.Preset back = DrawLayout.edited(Brush.of(tunedWashy));
        check(back != null && back.brush().sameAs(ToolSlots.first(wash)), "Watercolour washy changed resets to Watercolour wash");
        check(DrawLayout.RESET.equals("Reset to Default"), "Reset to Default, Title Case");
        reset();
        shapes();
        // The foot's buttons two words, Reset to Default a full row's three; the checkbox the owner's own four ("hold opens full
        // library", 2026-11-06).
        for (String label : new String[]{DrawLayout.BRUSH_LAB, DrawLayout.ADVANCED, DrawLayout.STUDIO, DrawLayout.TOOLS_FIRST, DrawLayout.RESET}) {
            check(label.split(" ").length <= (label.equals(DrawLayout.RESET) ? 3 : label.equals(DrawLayout.TOOLS_FIRST) ? 4 : 2), label + ": two words at most, a full row three");
            if (label.equals(DrawLayout.RESET)) continue;
            for (String w : label.split(" ")) check(Character.isUpperCase(w.charAt(0)), label + ": Title Case");
        }
        // The foot one row on every tablet (2026-11-06): the browser's four (the app's three since Brush Studio's link went) in menu words, FOOT_PAD in each, the checkbox's 18 dp square
        // and its 8 dp gap, within the picker's width (its fine edge aside), as wide on every screen (four columns).
        float foot = 18 + 8;
        for (String label : new String[]{DrawLayout.BRUSH_LAB, DrawLayout.ADVANCED, DrawLayout.TOOLS_FIRST, DrawLayout.STUDIO})
            foot += LabText.width(label, TypeStyle.MENU.sp, DrawLayout.PICKER_SPACING) + 2 * DrawLayout.FOOT_PAD;
        check(foot <= DrawLayout.PICKER_COLUMNS * DrawLayout.pickerCellWidth(), "The picker's foot fits one row: " + foot + " dp of " + DrawLayout.PICKER_COLUMNS * DrawLayout.pickerCellWidth());
        check(DrawLayout.STUDIO_LINK.equals("https://superdood.org/studio/") && DrawLayout.NO_BROWSER.equals("No browser"), "Brush Studio's link, and its note without a browser");
        for (int[] s : SCREENS) {
            DrawLayout d = new DrawLayout(s[0], s[1]);
            // Four wide (the owner, 2026-11-06: "make it four wide"), on every tablet either way up; it fits the Manta's height
            // upright, the drawings smaller (pickerTip, 27 dp); the rest scroll at full size. Since the texture tools (2026-11-06,
            // Print five rows) the Manta turned and the Nomad upright scroll too: they fit until then, at 18 dp. Since the
            // library round (2026-11-06: ten textures back on the shelf, the foot one row) they fit again: the Manta upright at
            // full size, turned at 19 dp, the Nomad upright at 18; the Nomad turned scrolls.
            int columns = DrawLayout.pickerColumns(s[0] - d.side), tip = DrawLayout.pickerTip(columns, s[1]);
            boolean fits = !(s[0] == 998 && s[1] == 749);
            check(columns == 4 && (!fits || DrawLayout.pickerHeight(columns, tip) <= s[1] - 2 * DrawLayout.PAD), "The picker four wide, fitting the height at " + s[0] + "x" + s[1] + ": drawings " + tip + " dp");
            check(tip == DrawLayout.PICKER_TIP || tip >= DrawLayout.PICKER_TIP_LEAST && fits, "Drawings full size unless smaller ones fit: " + tip);
            check(columns * DrawLayout.pickerCellWidth() + 2 <= s[0] - d.side, "The picker fits beside the column at " + s[0] + "x" + s[1]);
            check(DrawLayout.pickerCellHeight(tip) >= 26, "No picker target under 26 dp");
        }
        for (int slot = 0; slot < ToolSlots.COUNT; slot++) check(DrawLayout.tap(slot, slot) == DrawLayout.Tap.NOTHING, "A tap on the selected slot still does nothing");
        try {
            String bar = source("InkWashBar"), app = source("PaintActivity");
            check(bar.contains("cleanMenu(tool); else app.toolPicker(tool, anchor(tool));") && bar.contains("if (choice == 1) app.toolPicker(slot, anchor(slot));") && !bar.contains("app.openTools("),
                    "A slot's long press opens the picker, a dirty one's Tools too; the bar opens the Tools page nowhere else");
            // Brush Studio no longer opens the tablet's browser (2026-11-06, the owner: "it shouldn't be a browser, it should be
            // the local app version"): Edit Brush is the app's own; Brush Lab's Open sheet shares a brush to the web one.
            check(app.contains("Runnable[] acts = {() -> openBrushLab(slot), () -> openTools(slot)};") && !app.contains("DrawLayout.STUDIO") && DrawLayout.BRUSH_LAB.equals("Edit Brush"),
                    "Edit Brush (Brush Lab) and Full Library act on the slot; no Brush Studio in the app's foot");
            String labPage = source("BrushLabActivity");
            check(labPage.contains("new LabButton(this, R.drawable.ic_ph_export, DrawLayout.STUDIO, false);") && labPage.contains("studio.setContentDescription(LabLayout.TO_STUDIO);")
                    && labPage.contains("new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(DrawLayout.STUDIO_LINK + \"#\" + android.net.Uri.encode(lab.code())))")
                    && labPage.contains("catch (android.content.ActivityNotFoundException error) { say(DrawLayout.NO_BROWSER); }") && LabLayout.TO_STUDIO.equals("Share to Brush Studio"),
                    "Brush Lab's Open sheet: Brush Studio (said Share to Brush Studio) opens this brush's code after the link's #, in the browser, else No browser");
            // Tools First (the owner, 2026-10-28; Hold Opens Library since 2026-11-06): one app-wide preference, off at first; on, the long press (a dirty slot's
            // Tools too, both through toolPicker) opens the Tools page on the slot instead; switched in the picker's foot
            // (on the Tools page always until 2026-11-03, the owner: "also the tools first selector.. huh"); while it is on the
            // picker never opens, so the Tools page's bar shows it then, the one way to turn it off.
            String prefs = source("PaintPreferences"), tools = source("ToolsActivity");
            check(prefs.contains("boolean toolsFirst() { return flag(TOOLS_FIRST, false); }") && prefs.contains("TOOLS_FIRST = \"tools_first\""), "Tools First is off at first, kept with the app's preferences");
            check(app.contains("void toolPicker(int slot, View anchor) {\n        if (prefs.toolsFirst()) { openTools(slot); return; }"), "With Tools First on, a slot's long press opens the Tools page, not the picker");
            // A checkbox saying what it does since 2026-11-06 (Brush Lab's As New's box, LabButton.ticked).
            check(DrawLayout.TOOLS_FIRST.equals("Hold Opens Full Library") && DrawLayout.ADVANCED.equals("Full Library"), "Hold Opens Full Library names the picker's Full Library, the page a hold opens");
            check(app.contains("new LabButton(this, 0, DrawLayout.TOOLS_FIRST, false).menu().ticked(false);") && app.contains("prefs.setToolsFirst(on);") && app.contains("first.ticked(on);") && app.contains("last.addView(first, 2,")
                    && tools.contains("if (prefs.toolsFirst()) {\n            LabButton first = new LabButton(this, 0, DrawLayout.TOOLS_FIRST, false).ticked(true);") && tools.contains("prefs.setToolsFirst(on);")
                    && source("BrushLabActivity").contains("new LabButton(this, 0, LabLayout.AS_NEW, false).ticked(asNew);"),
                    "Hold Opens Full Library ticked in the picker's foot, its one row; on the Tools page only while ticked (the picker skipped); As New's box");
            check(app.contains("ToolPresets.Preset original = picker.reset;") && app.contains("pickBrush(slot, original.brush())"),
                    "Reset to Default shows for an edited Basic tool and puts it back with the slot's pick (an Undo step)");
            String picker = app.substring(app.indexOf("void toolPicker(int slot, View anchor) {"));
            check(picker.indexOf("reset.setText(DrawLayout.RESET)") < picker.indexOf("for (int t = 0; t < picker.tools.size(); t++)"),
                    "Reset to Default is the picker's top row (the owner, 2026-10-28)");
            stamp(picker, bar);
        } catch (java.io.IOException e) { throw new AssertionError("the sources: " + e); }
        slotDrawings();
    }
    /**
     * The Stamp (the owner, 2026-10-28: "just create a tool that is a stamp"): a Basic tool, the stamp tool's first brush
     * at its own seed, under Pens and in the picker with the owner's drawing; a slot holding a Stamp offers Stamp ▾ in its
     * picker, the stamp menu beside the slot, whose Cut Stamp, Turns, Spacing and Ink Fade change that slot's brush.
     */
    static void stamp(String picker, String bar) {
        ToolPresets.Preset stamp = ToolKits.preset("Stamp");
        check(stamp != null && stamp.brush().parts.stamps() && ToolsPage.Chip.of(stamp.brush().parts) == ToolsPage.Chip.ODD_ONES, "Stamp is a Basic tool, a Stamp, under Odd Ones");
        check(ModularCode.encode(stamp.brush().parts).equals(ModularCode.encode(ToolSlots.firstStamp().parts.copy(stamp.brush().parts.seed))),
                "The Stamp is the stamp tool's first brush, at its own seed");
        check(DrawLayout.edited(ToolSlots.first(0)) == null && !"tip_stamp".equals(DrawLayout.drawing(ToolSlots.first(0))), "The first slot's Fineliner is no edited Stamp (another seed)");
        check(DrawLayout.STAMP_MENU.equals("Stamp ▾"), "The picker's stamp row");
        check(picker.contains("if (picker.stamp) {") && picker.contains("inkWashBar.stampMenu(slot); });"), "A Stamp's picker offers its stamp menu, on that slot");
        check(bar.contains("void stampMenu(int slot) {\n        View owner = tools[slot];") && bar.contains("Brush brush = app.inkWash.slots.brush(slot);")
                && bar.contains("app.cutStamp(slot); });") && bar.contains("for (DrawLayout.StampRow row : DrawLayout.stampRows(brush)) stampRow(content, slot, row);")
                && bar.contains("if (!app.inkWash.slots.use(slot, DrawLayout.stamped(app.inkWash.slots.brush(slot), dial, value))) return;") && !bar.contains("void stamp() {"),
                "The stamp menu acts on the slot's brush: its card, Cut Stamp and its rows; the stamp tool's own path is gone");
    }
    /** Reset to Default (2026-10-27): offered only for a Basic tool whose settings changed, never an unchanged one or a brush of its own. */
    /**
     * The picker's order (the owner, 2026-11-06: "in the tray, group alphabetically but divide by type"): the Tools page's
     * kinds in the chips' order, each kind by name, every shown tool once; a line before each kind but the first.
     */
    static void pickerGroups() {
        java.util.List<String> order = new java.util.ArrayList<>();
        int last = -1;
        for (java.util.List<ToolPresets.Preset> group : DrawLayout.pickerGroups()) {
            ToolsPage.Chip kind = ToolsPage.Chip.of(group.get(0));
            check(kind.kind() && kind.ordinal() > last, "The kinds in the chips' order: " + kind);
            last = kind.ordinal();
            for (int i = 0; i < group.size(); i++) {
                check(ToolsPage.Chip.of(group.get(i)) == kind, group.get(i).name + " under " + kind);
                check(i == 0 || group.get(i - 1).shownName().compareToIgnoreCase(group.get(i).shownName()) < 0, kind + " by name: " + group.get(i).shownName());
                order.add(group.get(i).name);
            }
        }
        check(order.size() == ToolPresets.shown().size() && new java.util.HashSet<>(order).size() == order.size(), "Every shown tool once: " + order.size());
        DrawLayout.Picker p = new DrawLayout.Picker(null, false);
        int lines = 0;
        for (int i = 0; i < p.tools.size(); i++) {
            check(p.tools.get(i).name.equals(order.get(i)), "The picker in the groups' order: " + p.tools.get(i).name);
            if (p.divided.get(i)) lines++;
        }
        check(!p.divided.get(0) && lines == DrawLayout.pickerGroups().size() - 1, "A line between kinds: " + lines);
        // Fixed points of today's list: Pens first, alphabetical (Cheap ballpoint before Steady line).
        check(order.indexOf("Cheap ballpoint") < order.indexOf("Steady line") && ToolsPage.Chip.of(DrawLayout.pickerGroups().get(0).get(0)) == ToolsPage.Chip.PENS,
                "Pens first, by name");
        // Effects last (the owner, 2026-11-06: "effect brushes should move to their own section"): the tools that rework
        // the marks there, each named "… Effect"; Cracks with the paints ("cracks... it's more of a paint").
        java.util.List<java.util.List<ToolPresets.Preset>> groups = DrawLayout.pickerGroups();
        java.util.List<String> effects = new java.util.ArrayList<>();
        for (ToolPresets.Preset e : groups.get(groups.size() - 1)) effects.add(e.name);
        check(ToolsPage.Chip.of(groups.get(groups.size() - 1).get(0)) == ToolsPage.Chip.EFFECTS
                && effects.equals(java.util.Arrays.asList("Newsprint", "Photocopier")), "Effects last (Water, Blend and Blur the smudge tool's alone since 2026-11-06): " + effects);
        for (ToolPresets.Preset t : ToolPresets.shown()) check(effects.contains(t.name) == t.shownName().endsWith(" Effect"), t.shownName() + ": Effects holds the Effect tools alone");
        check(ToolsPage.Chip.of(ToolKits.preset("Cracking paint")) == ToolsPage.Chip.PAINT, "Cracking paint with the paints");
        check(ToolsPage.Chip.of(ToolKits.preset("Opaque flat")) == ToolsPage.Chip.PAINT && DrawLayout.drawn("Opaque flat").equals("tip_loaded_oils"), "Opaque flat with the oils, and their drawing");
        System.out.println("PASS: the tool picker's kinds: " + order);
    }
    /** The picker's rows as PaintActivity and the browser draw them (DrawLayout.Picker), and the long press's timing. */
    static void pickerRows() {
        ToolPresets.Preset wax = ToolKits.preset("Wax crayon");
        DrawLayout.Picker p = new DrawLayout.Picker(wax.brush(), false);
        check(p.tools.size() == ToolPresets.shown().size() && p.reset == null && !p.stamp, "Every shown tool a cell, no reset for an unchanged one");
        int marked = 0;
        for (int i = 0; i < p.tools.size(); i++) { check(java.util.Objects.equals(p.drawings.get(i), DrawLayout.drawing(p.tools.get(i).brush())), "Its drawing, its own: " + p.tools.get(i).name); if (p.marked.get(i)) marked++; }
        check(marked == 1 && p.marked.get(p.tools.indexOf(wax)), "The slot's tool marked, alone");
        check(!new DrawLayout.Picker(wax.brush(), true).marked.contains(true), "Shapes up on the slot: nothing marked");
        ModularBrush changed = wax.brush().parts.copy(); changed.part(BrushPart.TIP).set(PartDial.WEIGHT, 1.3);
        check(new DrawLayout.Picker(Brush.of(changed), false).reset == wax, "An edited Basic tool offers its reset");
        check(new DrawLayout.Picker(ToolKits.preset("Stamp").brush(), false).stamp, "A Stamp has its menu row");
        check(DrawLayout.LONG_PRESS_MS == 500 && DrawLayout.LONG_PRESS_SLOP == 8, "The long press is Android 11's: 500 ms, 8 dp");
        check(DrawLayout.pickerColumns(1024) == DrawLayout.PICKER_COLUMNS && DrawLayout.pickerColumns(390) * DrawLayout.pickerCellWidth() <= 390
                        && DrawLayout.pickerTip(DrawLayout.pickerColumns(390), 844) == DrawLayout.PICKER_TIP,
                "The picker's columns as the Manta's, no more than fit a phone's width (full-size drawings, scrolling)");
    }
    static void reset() {
        ToolPresets.Preset wax = ToolKits.preset("Wax crayon");
        ModularBrush changed = wax.brush().parts.copy(); changed.part(BrushPart.TIP).set(PartDial.WEIGHT, 1.3); Brush edited = Brush.of(changed);
        check(DrawLayout.edited(wax.brush()) == null, "An unchanged Basic tool offers no reset");
        check(DrawLayout.edited(edited) == wax, "A Basic tool changed in Brush Lab offers its reset");
        String code = wax.brush().write().split("\t")[1];
        check(DrawLayout.edited(Brush.read("Crayon for skies\t" + code)) == null, "A renamed one, its settings the tool's, offers none");
        check(DrawLayout.edited(Brush.read("sloppy-waxy-drag\t" + code.replaceAll("\\.[0-9a-z]+$", ".1zzzzz"))) == null, "A brush of its own offers none");
        check(DrawLayout.edited(null) == null, "No brush, no reset");
        // The owner's first ten (2026-10-28): unchanged, no reset; changed, named in any case, a reset to FIRST's own, never a Basic tool sharing its seed.
        for (int i = 0; i < ToolSlots.FIRST.length; i++) {
            Brush first = ToolSlots.first(i);
            check(DrawLayout.edited(first) == null && DrawLayout.edited(Brush.read(first.name().toLowerCase() + "\t" + ToolSlots.FIRST[i][2])) == null, first.name() + " unchanged: no reset");
            ModularBrush tuned = first.parts.copy(); tuned.name = first.name().toLowerCase();
            for (BrushPart part : BrushPart.values()) for (PartDial d : PartDial.values())
                if (tuned.part(part) != null && tuned.part(part).has(d)) { tuned.part(part).set(d, tuned.part(part).get(d) == .5 ? .6 : .5); break; }
            ToolPresets.Preset back = DrawLayout.edited(Brush.of(tuned));
            check(back != null && back.brush().parts.key().equals(first.parts.key()), first.name() + " changed resets to FIRST's own: " + (back == null ? null : back.brush().write()));
        }
    }
    /**
     * A tray row shows its brush's picker drawing (the owner, 2026-10-26: "you can add the brush drawings now"): every drawn
     * Basic tool its own, and a copy or an edit of one (ToolPresets.origin); a tool not drawn its style's one drawing; a
     * brush of its own (Brush Lab's), or a tool whose style has several drawings or none (Mold ink), none, so its stroke.
     */
    static void slotDrawings() {
        for (String[] tool : DrawLayout.PICKER) {
            Brush brush = ToolPresets.named(tool[0]).brush();
            check(java.util.Objects.equals(tool[1], DrawLayout.drawing(brush)), tool[0] + "'s slot shows its picker drawing, or its stroke: " + DrawLayout.drawing(brush));
        }
        // The owner's Manta, 2026-10-26: three slots showed strokes. Saved copies the library numbered, Brush Lab's dials changed.
        String[][] manta = {
                {"Sketch pencil\tsketch-pencil.M40i000000000at_47q00_0_000_1jz_25ess00000000000000nz000000000530000005000c099zz.inw", null},
                // 2026-11-01 (the owner: "only limited in house designed tools should have art"): copies show their strokes.
                {"Wax crayon 3\twax-crayon.Ma0p600000000at_7zz0_0_ci0_0_0000000000000000000nz0.2f9", null},
                {"Ben-Day brush 2\tben-day-brush.Me0ij00000000ll_0i0000000000000000zz265ji000at_0_000_0_25ess00000000000000nz0.2ef", null},
                // 2026-10-28: the owner's slot 1, named in lower case, showed none.
                {"another pencil\t" + ToolSlots.FIRST[0][2], "tip_hb_pencil"},
                {ToolSlots.first(0).write(), "tip_hb_pencil"}};
        for (String[] m : manta) check(java.util.Objects.equals(m[1], DrawLayout.drawing(Brush.read(m[0]))), m[0].split("\t")[0] + " shows " + m[1] + ": " + DrawLayout.drawing(Brush.read(m[0])));
        check(DrawLayout.edited(Brush.read("another pencil\t" + ToolSlots.FIRST[0][2])) == null && DrawLayout.edited(ToolSlots.first(0)) == null,
                "The owner's pencil is the Basic tool Another pencil: no Reset to Default");
        String wax = ToolKits.preset("Wax crayon").brush().write().split("\t")[1];
        check(DrawLayout.drawing(Brush.read("Crayon for skies\t" + wax)) == null, "A renamed Wax crayon shows its stroke: only the tool as it is has its drawing");
        check(DrawLayout.drawing(Brush.read("sloppy-waxy-drag\t" + wax.replaceAll("\\.[0-9a-z]+$", ".1zzzzz"))) == null, "A Brush Lab brush of its own (another seed and name) keeps its stroke");
        check(DrawLayout.drawing(ToolKits.preset("Sketch pencil").brush()) == null, "Sketch pencil, not drawn, shows its stroke: no borrowing (2026-11-01)");
        for (int i = 0; i < ToolSlots.FIRST.length; i++) {
            String d = DrawLayout.drawn(ToolSlots.FIRST[i][1]);
            check(d != null && d.equals(DrawLayout.drawing(ToolSlots.first(i))), ToolSlots.FIRST[i][1] + ", on the default bar, shows " + d);
        }
        // Shared since the owner's list (2026-11-06): a drawing only on shown tools; a shelf tool shows its stroke.
        for (ToolPresets.Preset p : ToolPresets.shelf()) check(DrawLayout.drawing(p.brush()) == null, p.name + " on the shelf shows its stroke");
        for (String[] taken : new String[][]{{"HB pencil", "Another pencil"}, {"Felt marker", "Verve felt"}})
            check(DrawLayout.drawing(ToolKits.preset(taken[0]).brush()) == null, taken[0] + "'s drawing is " + taken[1] + "'s now");
        check(DrawLayout.drawing(ToolKits.preset("Mold ink").brush()) == null, "Mold ink, not drawn, Ink wash's drawings two, keeps its stroke");
        check(DrawLayout.drawing(null) == null, "No brush, no drawing");
        check(DrawLayout.TIP_TRIM > 0 && DrawLayout.TIP_TRIM < .25f, "The row's edge cuts only the handle's end");
    }
    /**
     * Shapes (the owner, 2026-10-26: "yes shapes, lets bring them back"): its words Title Case, one word each, said
     * "Rectangle" or "Filled oval" (a line never filled); its outline the slot's size in whole pixels, 1 to 128; the
     * Play's Shapes row (since the release pare-down, 2026-11-05; the column's button from 2026-10-30) opens its menu
     * beside Play, which has the side bar while on, in place of the slot's; a pick takes it up on the slot, and any tool
     * chosen puts it down.
     */
    static void shapes() {
        for (String label : new String[]{DrawLayout.SHAPES, DrawLayout.FILLED}) check(label.split(" ").length == 1 && Character.isUpperCase(label.charAt(0)), label + ": one Title Case word");
        ToolSettings rect = ToolSettings.defaults(ToolSettings.Tool.SHAPES).shape(ToolSettings.Shape.RECTANGLE);
        check(DrawLayout.shapeWords(rect.filled(false)).equals("Rectangle") && DrawLayout.shapeWords(rect.shape(ToolSettings.Shape.OVAL).filled(true)).equals("Filled oval")
                && DrawLayout.shapeWords(rect.shape(ToolSettings.Shape.LINE).filled(true)).equals("Line"), "Shapes says its pick");
        check(DrawLayout.shapeOutline(rect, 1.5).outlineWidth == 2 && DrawLayout.shapeOutline(rect, 7.4).outlineWidth == 7 && DrawLayout.shapeOutline(rect, 360).outlineWidth == 128
                && DrawLayout.shapeOutline(rect, .2).outlineWidth == 1, "A shape's outline is the slot's size");
        try {
            String bar = source("InkWashBar"), app = source("PaintActivity"), pad = source("DrawingPad"), ink = source("InkWash");
            check(new java.io.File("app/src/main/res/drawable/ic_ph_shapes.xml").isFile(), "Shapes has its icon");
            check(app.contains("int slot = inkWash.selected < ToolSlots.COUNT ? inkWash.selected : 0;") && app.contains("actions[i] = () -> shapesMenu(slot, anchor);")
                    && app.contains("pad.shapes = shapes; pad.shapesOn = true;\n        inkWashBar.lassoChanged();"),
                    "Play's Shapes opens its menu beside Play's; a pick takes Shapes up (the menu's Play row marked while on, 2026-11-06)");
            check(bar.contains("tools[app.inkWash.selected].mark(!on && !dropping && !shaping);"), "The slot gives its mark to Play's Shapes while Shapes draws with it");
            check(bar.contains("if (app.pad.lassoOn || app.pad.dropperOn || app.pad.shapesOn) { app.pad.shapesOn = false;")
                    && bar.contains("if (app.pad != null && (app.pad.lassoOn || app.pad.dropperOn || app.pad.shapesOn)) { app.pad.shapesOn = false;"), "Any tool chosen puts Shapes down");
            check(pad.contains("ToolSwatches.Look look = app.inkWash.shape(document);") && pad.contains("beginShape(new ShapePreview(document, DrawLayout.shapeOutline(shapes, look.px), ToolSwatches.tone(look.darkness), samplePoint[0], samplePoint[1]));")
                    && ink.contains("StrokeStart.shape(document, brush.parts, session);") && source("StrokeStart").contains("ModularStroke.selectLayer(document, parts);"), "A shape lays the slot's grey at its size, on its layer");
        } catch (java.io.IOException e) { throw new AssertionError("the sources: " + e); }
    }
    /** A picker box drawable's numbers, in order (its viewport's dp: 56 x 176). */
    static double[] boxPath(String name) {
        try {
            String xml = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("app/src/main/res/drawable/" + name + ".xml")));
            check(xml.contains("android:viewportWidth=\"56\" android:viewportHeight=\"176\""), name + ": in the box's dp");
            java.util.regex.Matcher data = java.util.regex.Pattern.compile("android:pathData=\"([^\"]*)\"").matcher(xml);
            check(data.find(), name + ": a path");
            return java.util.Arrays.stream(data.group(1).split("[^0-9.]+")).filter(s -> !s.isEmpty()).mapToDouble(Double::parseDouble).toArray();
        } catch (java.io.IOException e) { throw new AssertionError(name, e); }
    }
    static String source(String name) throws java.io.IOException {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("app/src/main/java/io/github/mpdairy/monopaint/" + name + ".java")), java.nio.charset.StandardCharsets.UTF_8);
    }
    /**
     * No Fun button (the owner, 2026-10-23: "move magnetic poetry and mirrors to the toolbar, removing the confetti"):
     * Magnetic Poetry and Mirrors share the tool grid's last row, no row or rule of their own; the bar has no confetti.
     * Zen Mode dotted while off, a circle while on (the owner, 2026-10-23); the wet indicator's dry drop dashed, so it never
     * reads as on (the owner, 2026-10-23: "the wet icon seems like it's always on now").
     */
    static void fun() {
        check(!java.util.Arrays.asList(DrawLayout.COLUMN).toString().contains("FUN") && DrawLayout.count(DrawLayout.TRAY_ROWS)[DrawLayout.Item.WET.ordinal()] == DrawLayout.PLAY_ROWS,
                "The small rows, no rule");
        try {
            String bar = source("InkWashBar"), app = source("PaintActivity"), wet = source("WetIndicator");
            check(!app.contains("names.add(DrawLayout.PLAY)") && !bar.contains("R.drawable.ic_ph_confetti") && !bar.contains("ic_ph_magnet") && !bar.contains("ic_ph_flip_horizontal")
                            && !bar.contains("stampRow.addView") && !bar.contains("LayerColumn"),
                    "Play (Phosphor confetti) the menu's row (2026-11-06; the column's button from 2026-10-30); no Magnetic Poetry, Mirrors, stamp tool or layers buttons in the column");
            check(app.contains("zenButton.iconOnly(zen ? R.drawable.ic_ph_circle : R.drawable.ic_ph_circle_dashed);") && !app.contains("ic_ph_x : zen") && app.contains("if (!zen) message(DrawLayout.ZEN_HOLD);\n        else if (zenFanOpen()) closeZenFan();\n        else openZenFan();") && app.contains("button.setOnLongClickListener(v -> { zenHeld(); return true; });") && app.contains("void zenHeld() { if (!busy()) setZen(!zen); }") && !app.contains("setZen(true);\n        else") && app.contains("DrawLayout.ZEN, R.drawable.ic_ph_circle_dashed")
                            && source("DrawingPad").contains("if (!flashes()) { refreshRetries = 0; unrefreshed = ghosted = false; app.redrawZen(pageArea()); return; }"),
                    "Zen Mode: the dashed circle while off, the circle while on, its fan open too (no X, 2026-11-05); only a hold turns it on or off, a tap off says to hold, on opens or closes the fan; the button put back after a page turn or pinch, with Automatic Refresh off too (2026-11-05)");
            // Every write of the page's pixels to the panel goes through wrote(), which puts Zen's button back, no flash (2026-11-05: "disappears a bit").
            String pad = source("DrawingPad");
            check(pad.contains("if (target == previewDirect) previewArea.union(presenting);\n                    wrote(presenting);")
                            && pad.contains("if (result < 0) queueView(endingArea); else { wrote(endingArea); zenFanRim(new Rect(endingArea)); }")
                            && pad.contains("if (direct.refresh(source, new Rect(part[0], part[1], part[2], part[3])) < 0) { postDelayed(this::refreshFastInk, 8); return; }\n        wrote(area);")
                            && pad.contains("            }\n        wrote(area);\n        zenFanRim(area);\n        Log.i(ProbeActivity.TAG, \"Area refresh (\"")
                            && pad.contains("app.pagePanel, zenFanKept()); }") && pad.contains("if (presented = turn.present(presented())) { wrote(") && pad.contains("for (Rect area : parts) if (rim.refresh(shown, area) >= 0) wrote(area);")
                            && pad.contains("if (zenWritten.isEmpty()) postDelayed(zenBack, CLOSED_MS);")
                            && pad.contains("app.redrawZen(zenWritten); zenWritten.setEmpty();")
                            && pad.split("app\\.redrawZen\\(").length == 3 && pad.split("direct\\.refresh\\(").length == 4
                            && pad.split("\\.present\\(presented\\(\\)").length == 3 && pad.split("\\.endFast\\(").length == 2,
                    "Zen Mode: strokes, the eraser's ring, wet paint, sharp tiles, a stroke's end, cleaned popups, area refreshes and page turns all put the button back after writing over it (wrote), no extra flash; no other panel write left out");
            check(pad.contains("java.util.List<RectF> fan = app.zenFanMask();\n        if (fan != null) panel.addAll(fan);") && !pad.contains("app.zenFanPanel()")
                            && source("PaintActivity").contains("java.util.List<RectF> mask = zenFan.mask();") && pad.split("MagnetDrag\\.minus\\(").length == 8 && pad.split(", covered\\(\\)\\)").length == 5
                            && source("PaintActivity").contains("RectF area = zenFanPanel();\n        if (area == null) return;\n        zenFan.setVisibility(View.GONE);"),
                    "Zen Mode's open fan: the fast display keeps off its shape, in steps (drying paint took bits of it, the owner 2026-11-06; a Lasso loop's end its arc, 2026-11-09), every write through the mask; closing it shows what it covered");
            // Every open popup too (2026-11-06: wet paint drew over the menu, Play, the tray...): one path, Popups'.
            String popups = source("Popups");
            check(pad.contains("java.util.List<RectF> panel = Popups.covering();\n        java.util.List<RectF> fan = app.zenFanMask();")
                            && popups.contains("if (Color.alpha(background) != 0) opaque.put(popup, new RectF());")
                            && popups.contains("RectF window = new RectF(bounds); PanelCoordinates.fromView(root).mapRect(window);\n        RectF covers = opaque.get(popup);\n        if (covers != null) covers.set(window);")
                            && popups.contains("if (open.getKey().isShowing() && !open.getValue().isEmpty()) areas.add(new RectF(open.getValue()));")
                            && !pad.contains("coveredPanel") && !source("WordsMode").contains("void cover(") && !source("TextMode").contains("host.cover(") && !source("PicturePlacer").contains("host.cover("),
                    "Every open opaque popup (the menu, Play, System Settings, the tray, Show Kinds, the Lasso's bar, Words' bin, Text, a picture's bar) keeps the fast display off it, through Popups alone; closing one cleans its area as before");
            // Under the fan's wedges (the owner, 2026-11-07: "a row of tools: eye drop, lasso, droplet and layers all in a
            // row, and the zoom level above it"; in the gap between them from 2026-11-06): the column's own buttons lent, in ZenFan.cell's order, laid over its cells inside the fan's view (its area, the fast
            // display's mask), the layer button's icon sized to its cell, and home as it closes; no Menu or page turner in it.
            String fanView = source("ZenFanView");
            check(bar.contains("private View[] zenStrip() { return new View[]{menu, swatches.dropper, lasso, wet, layers, brushLine}; }") && !source("PageTurner").contains("turnParts")
                            && bar.contains("sizeLayers(Ui.dpOf(app, Math.round(cells[ZenFan.LAYERS].height())));") && bar.contains("sizeLayers(fitted.layers);\n        for (View button : buttons) grey(button, false);\n        menu.iconHalf = DrawLayout.ICON_SIDE / 2;") && bar.contains("showModes();\n        wet.changed();")
                            && bar.contains("stage.addView(button, index + i, at);") && bar.contains("lentFrom[i].addView(buttons[i], lentAt[i], lentPlace[i]);")
                            && app.contains("for (int i = 0; i < cells.length; i++) cells[i] = zenFan.cell(i);\n        inkWashBar.lend((ViewGroup) stage, ((ViewGroup) stage).indexOfChild(zenFan) + 1, cells);")
                            && app.contains("zenFan.setVisibility(View.GONE);\n        inkWashBar.giveBack();")
                            && fanView.contains("RectF area() { return px(bounds); }") && fanView.contains("RectF cell(int cell) { return px(fan.cell(cell)); }")
                            && source("ZenFan").contains("for (int i = 0; i < CELLS; i++) {\n            float[] c = cell(i);\n            box[0] = Math.min(box[0], c[0]);"),
                    "Zen Mode's fan: Menu, the Dropper, the Lasso and the droplet in a row under its wedges, the layer button over them, lent while it is open, inside the fan's masked area, home as it closes");
            // The Lasso's menu opens beside the fan, not over it; the hand's change lays the column out again, the buttons home first.
            check(bar.contains("() -> app.fileMenu(menu[0])") && bar.contains("app.menu(anchor(lasso), \"lasso menu\"")
                            && bar.contains("private View anchor(View button) { return zenAnchor != null ? zenAnchor : button; }")
                            && app.contains("void applyToolboxSide() {\n        // Its line holds the column's buttons, laid out again here: home first.\n        closeZenFan();"),
                    "The Lasso's menu beside Zen Mode's fan while it is open; the buttons home before the column is laid out again");
            // The brush's dot over Zen's circle only while the fan is open (the owner, 2026-11-06: "change back to nib + shade preview (but only when fan is open)").
            check(app.contains("zenDot.setVisibility(open ? View.VISIBLE : View.GONE);") && app.contains("if (zenDot == null || inkWashBar == null || !zenFanOpen()) return;")
                            && app.contains("zenDot.show(inkWash.look(tool), inkWash.brush(tool), DrawLayout.greys(tool));") && source("SwatchBar").contains("app.showZenDot();"),
                    "Zen Mode: the brush's nib and grey over the circle while the fan is open, the plain circle while it is closed");
            // The fan stays open while drawing (the owner, 2026-11-06: "Keep tray open when drawing - press to close"): nothing
            // closes it on a touch outside; Zen's circle's tap does, and round the circle too; a hold there leaves Zen Mode.
            check(!app.contains("Ui.outside(zenFan") && !fanView.contains("app.closeZenFan()") && fanView.contains("if (at == ZenFan.OUTSIDE) return false;")
                            && fanView.contains("else if (was == ZenFan.LEAVE && at == ZenFan.LEAVE && !held) app.zenTapped();")
                            && fanView.contains("if (item == ZenFan.LEAVE) app().zenHeld();") && !source("ZenFan").contains("closes(")
                            && !pad.contains("app.zenFanOpen()"),
                    "Zen Mode's fan: drawing keeps it open, the nib's ring still shows; only a tap on Zen's circle closes it, a hold leaves Zen Mode");
            check(wet.contains("shown == WetLook.State.DRY ? R.drawable.ic_drop_dashed") && !wet.contains("DashPathEffect") && WetLook.icon(WetLook.State.DRY).equals("ic_drop_dashed"),
                    "The dry drop the owner's dashed drop (2026-10-27), the wet ones the owner's half and full drops");
        } catch (java.io.IOException e) { throw new AssertionError("the sources: " + e); }
    }
    /**
     * Play's menu (the refocus, 2026-10-24) and the Mirrors list (its rows and the caption under them), as {@code
     * PaintActivity.menu} lays them out: rows 52 dp, nothing under 26 dp, fitting the height on all four screens without
     * scrolling; labels Title Case, a button's two words at most (docs/TEXT-STYLE.md). Play holds the toys: Magnetic
     * Poetry and Mirrors, marked while on, then Replay Page; no Shapes (the column's) or Self-Portrait Prompt (Notebook
     * Settings') since 2026-10-30, no Stamp, Surprise Me or Fling (the owner, 2026-10-28). No Diary Page or ready brushes
     * (the owner, 2026-10-27).
     */
    static void funLists() {
        java.util.List<String> labels = new java.util.ArrayList<>(java.util.Arrays.asList(DrawLayout.POETRY, DrawLayout.SHAPES, DrawLayout.SPIROGRAPH, DrawLayout.MIRRORS));
        check(labels.equals(java.util.Arrays.asList("Magnetic Poetry", "Shapes", "Spirograph", "Mirrors")), "Play's toys: Magnetic Poetry, Shapes, Spirograph, Mirrors");
        java.util.List<String> play = new java.util.ArrayList<>(java.util.Arrays.asList(DrawLayout.TEXT, DrawLayout.POETRY, DrawLayout.SHAPES, DrawLayout.SPIROGRAPH, DrawLayout.MIRRORS, "Replay Page"));  // PageReplay.REPLAY_PAGE (an Android class), checked in its source below
        check(String.join(", ", play).equals("Text, Magnetic Poetry, Shapes, Spirograph, Mirrors, Replay Page"), "Play's rows: " + play);
        // The toys that were Play's ready brushes are still Basic tools, in the tool picker (the owner, 2026-10-27).
        for (String toy : new String[]{"Spirograph", "Sumi", "Mold ink", "Vanishing ink", "Lemon juice", "Crayon scratch"})
            check(ToolKits.preset(toy) != null, toy + " is still a Basic tool");
        labels.addAll(play);
        for (DrawLayout.MirrorRow r : DrawLayout.MirrorRow.values()) labels.add(r.label);
        for (String label : labels) {
            String[] words = label.split(" ");
            check(words.length <= 2, label + ": two words at most");
            for (String w : words) check(Character.isUpperCase(w.charAt(0)), label + ": Title Case");
        }
        check(DrawLayout.MENU_ROW >= 26 && DrawLayout.MENU_NOTE >= 26, "No row under 26 dp");
        for (int[] s : SCREENS) {
            check(DrawLayout.MirrorRow.values().length * DrawLayout.MENU_ROW + DrawLayout.MENU_NOTE <= s[1] - 2 * DrawLayout.PAD, "The Mirrors list and its caption fit the height at " + s[0] + "x" + s[1]);
            // Its rows and a group line (9 dp, Theme.FINE and its margins).
            check(play.size() * DrawLayout.MENU_ROW + 9 + 2 <= s[1] - 2 * DrawLayout.PAD, "Play's menu fits the height at " + s[0] + "x" + s[1]);
        }
        // Play's side bar while a toy is on: the magnets' bin, Text, Shapes, mirrors.
        check(!DrawLayout.playOn(false, false) && DrawLayout.playOn(true, false) && DrawLayout.playOn(false, true), "Play marked while a toy is on");
        // The shade strip's Opacity (2026-10-27): opaque paint alone; a pick is that slot's brush's own Opacity dial, kept
        // per slot, read by the stroke.
        for (String name : new String[]{"Wet gouache", "Flat oils", "Acrylic paint marker", "Ms. Sketch"})
            check(DrawLayout.opacity(ToolKits.preset(name).brush()), name + " has Opacity");
        for (String name : new String[]{"Soft wash", "Line and wash", "Round marker", "Steady line", "HB pencil", "Soft charcoal"})
            check(!DrawLayout.opacity(ToolKits.preset(name).brush()), name + " has no Opacity");
        check(!DrawLayout.opacity(null), "The eraser has no Opacity");
        for (ToolPresets.Preset p : ToolPresets.all())
            check(DrawLayout.opacity(p.brush()) == (p.brush().parts.choice(BrushPart.MEDIUM) == PartChoice.COVER), p.name + ": Opacity only on Opaque");
        check(DrawLayout.opacitySaid(.4).equals("Opacity, thin to opaque: 40%"), "Opacity's screen-reader words");
        Brush gouache = ToolKits.preset("Wet gouache").brush();
        ToolSlots slots = BrushLabChecks.slots();
        Brush before = slots.brush(1);
        check(slots.use(0, DrawLayout.opacity(gouache, .3)) && slots.use(1, DrawLayout.opacity(gouache, .8)), "Opacity picked on two slots");
        ToolSlots again = new ToolSlots(BrushLabChecks.store(), new java.util.Random(1));
        double first = again.brush(0).parts.part(BrushPart.MEDIUM).get(PartDial.COVER), second = again.brush(1).parts.part(BrushPart.MEDIUM).get(PartDial.COVER);
        check(Math.abs(first - .3) < .02 && Math.abs(second - .8) < .02, "Each slot keeps its own Opacity: " + first + ", " + second);
        check(again.brush(0).name().equals(gouache.name()), "Opacity keeps the brush's name");
        check(ResolvedBrush.of(again.brush(0).parts, 44, 1).cover < ResolvedBrush.of(again.brush(1).parts, 44, 1).cover,
                "The stroke reads the Opacity dial");
        check(slots.undo() == 1 && slots.brush(1).sameAs(before), "Undo takes an Opacity pick back");
        try {
            String app = source("PaintActivity");
            String menu = app.substring(app.indexOf("void playMenu(View anchor)"), app.indexOf("menu(anchor, \"play menu\""));
            check(menu.contains("mirrors.mirrors(anchor)") && menu.contains("pad.words.toggle()") && menu.contains("shapesMenu(slot, anchor)") && !menu.contains("DailyPrompt") && !menu.contains("dailyPrompt(")
                    && menu.contains("replay.choose(anchor)") && DrawLayout.REPLAY_PAGE.equals("Replay Page") && menu.contains("DrawLayout.playRows(") && !menu.contains("SURPRISE") && !menu.contains("STAMP") && !menu.contains("Fling") && !app.contains("toggleFling")
                    && !menu.contains("DIARY_PAGE") && menu.contains("ToolKits.preset(DrawLayout.SPIROGRAPH)") && app.contains("inkWash.slots.pick(slot, brush)") && source("CardSelector").contains("app.chooseTools(NotebookOptions.Tools.values()[choice])"),
                    "Play's rows do what they say: the magnets, Shapes, the Spirograph, the mirrors, Replay Page; no Self-Portrait Prompt, Stamp, Surprise Me (still Notebook Settings' Tools) or Fling");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    /**
     * The Zoom button (the owner, 2026-10-19: "can we add zoom and lock zoom like in monopaint?"; 2026-10-21: on the
     * bottom bar): on the page bar beside the number, its padlock beside its percentage in captions (12 sp or more), up
     * to 800%, inside its button on every screen; not in the column.
     */
    static void zoom() {
        for (int[] s : SCREENS) {
            DrawLayout d = new DrawLayout(s[0], s[1]);
            float text = LabText.width(PinchPan.caption(800), TypeStyle.CAPTION.sp, 0), line = LabText.line(TypeStyle.CAPTION.sp);
            int tall = d.pageBar() - DrawLayout.PAGE_RULE;
            check(TypeStyle.CAPTION.sp >= 12 && DrawLayout.ZOOM_LOCK + DrawLayout.ZOOM_GAP + text + 2 * DrawLayout.ZOOM_PAD <= DrawLayout.zoomButton()
                            && Math.max(line, DrawLayout.ZOOM_LOCK) <= tall,
                    "Zoom's padlock beside its percentage, 800% the widest (" + text + " dp), fits its " + DrawLayout.zoomButton() + " x " + tall + " dp at " + s[0] + "x" + s[1]);
        }
        for (DrawLayout.Part part : DrawLayout.COLUMN) check(!part.name().equals("ZOOM") && !part.name().equals("LAB"), "No Zoom or Brush Lab in the column: " + part);
    }
    /**
     * Finger (what one finger does, {@link FingerTool}): the column's button again (the owner, 2026-10-26: "selecting
     * finger/eraser in dropdown menus is not the right way to do it"; "finger switch to toolbar. single tap to change"),
     * no menu row; a tap its choices beside it. Its menu's rows (52 dp each: Nothing, Eraser, Smudge, Fill in and the nine slots) fit the height.
     * System Settings' Finger Action since 2026-11-06 (the owner: "move Finger Action out of this menu into System
     * Settings"): its choices chips, three a line, the slots by their brushes' names; the browser's menu keeps its row.
     */
    static void finger() {
        check(FingerTool.Choice.values().length == 4 + ToolSlots.COUNT, "The finger's choices: Nothing, Eraser, Smudge, Fill in and the nine slots");
        for (int[] s : SCREENS) check(FingerTool.Choice.values().length * 52 <= s[1] - 2 * DrawLayout.PAD, "The finger menu's rows fit the height at " + s[0] + "x" + s[1]);
        try {
            String app = source("PaintActivity");
            String bar = source("InkWashBar");
            // The owner, 2026-10-27: "the ghost should go where the finger select is now... that can go in the dropdown main
            // menu"; the ghost on the page bar that morning, back in the column since 2026-10-28.
            String turner = source("PageTurner");
            String system = source("SystemSettingsPanel");
            check(!app.contains("fingerMenu(") && !app.contains("case \"finger\"") && DrawLayout.fileRows(false, "", false, null, false, false, false, false).stream().anyMatch(r -> "finger".equals(r.key) && "ic_finger".equals(r.icon))
                            && app.contains("fingers[c.ordinal()] = inkWashBar.fingerName(c);") && app.contains("SystemSettingsPanel.rows(this, fingers)")
                            && system.contains("row(app, list, SystemSettings.FINGER, null, fingers, app.inkWash.finger().ordinal(), 3, i -> app.inkWash.finger(choices[i]));") && SystemSettings.FINGER.equals("Finger Action")
                            && bar.contains("app.iconButton(DrawLayout.REFRESH, R.drawable.ic_ph_ghost, () -> { if (app.pad != null) app.pad.refreshAsked(\"Refresh button\"); })")
                            && !turner.contains("ic_ph_ghost") && turner.contains("View[] parts = {zoom.button, turn, new View(app), strip};"),
                    "Finger Action is System Settings' row (the browser's menu's row still), its choices the drawing screen's; Refresh, the ghost, in the column, not the page bar");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    /**
     * The page bar (the owner, 2026-10-19: "use the supernote style and put it on a thin bottom bar, same style as the
     * document reader"; docs/archive/superpowers/specs/2026-10-18-notebooks-design.md): along the page's foot, the page's width,
     * 36 to 44 dp tall under 1 dp of paper; never over the drawing: the page that much shorter. On the drawing screen
     * (docs/archive/superpowers/specs/2026-10-20-tracing-paper-design.md, "The bottom strip") Zoom at the end away
     * from the column, for either hand, then ‹ the page and its count in one line ("100 / 100" the widest, a cover's word,
     * 14 sp), then the previews toward the column, 22 × 30 dp (19 × 26 on the Nomad), about 30 of them in portrait and
     * more in landscape, a tab under them and 4 dp clear of the screen's edge, inside the bar; inside a notebook ‹ and ›
     * either side of the number. Its buttons at least 26 dp tall and as wide as a column's icon button.
     */
    static void pageBar() {
        for (int[] s : SCREENS) {
            DrawLayout d = new DrawLayout(s[0], s[1]);
            String at = s[0] + "x" + s[1];
            int bar = d.pageBar(), button = bar - DrawLayout.PAGE_RULE, width = d.pageWidth();
            check(bar >= 36 && bar <= 44 && bar == (d.compact ? 36 : 40) && DrawLayout.PAGE_RULE == 1, "A thin bar, " + bar + " dp, under 1 dp of paper at " + at);
            check(button >= 26 && d.pageButton() >= 40, "Its buttons " + d.pageButton() + " x " + button + " dp, over the 26 dp pen targets at " + at);
            check(d.pageHeight() + bar == s[1] && d.canvasHeight() + d.footHeight() == d.pageHeight() && d.pageWidth() + d.side == s[0], "The page shrinks by the bar's height, the canvas by the brush line's too, as by the column's width, at " + at);
            // The brush line (the owner, 2026-10-22): one caption line, 32 dp (layout v3's preview's height, kept when the
            // preview went, 2026-10-27; 18 until layout v3), the page keeping nearly all its height.
            check(DrawLayout.BRUSH_LINE >= Math.ceil(TypeStyle.CAPTION.sp * 1.3) && DrawLayout.BRUSH_LINE == 32 && d.footHeight() <= 56,
                    "The brush line 32 dp, the foot row " + d.footHeight() + " dp, at " + at);
            float number = DrawLayout.pageNumber() - 2 * DrawLayout.PAGE_NUMBER_PAD;
            check(TypeStyle.LABEL.sp >= 12, "The number at 12 sp or more");
            for (String said : new String[]{DrawLayout.WIDEST_PAGE, DrawLayout.pageWords(DrawingBook.FRONT, 100), DrawLayout.pageWords(DrawingBook.BACK, 100), DrawLayout.pageWords(99, 100)})
                check(LabText.width(said.toUpperCase(java.util.Locale.ROOT), TypeStyle.LABEL.sp, TypeStyle.LABEL.tracking) <= number && LabText.line(TypeStyle.LABEL.sp) <= button,
                        "\"" + said + "\" in one line, unclipped, at " + at);
            // The previews: their size, a tab under the drawing page's, 4 dp clear of the screen's edge, inside the bar.
            check(d.previewWidth() == (d.compact ? 19 : 22) && d.previewHeight() == (d.compact ? 26 : 30) && d.previewGap() == (d.compact ? 3 : 4), "Previews 22 x 30 dp (19 x 26 on the Nomad) at " + at);
            check(DrawLayout.STRIP_EDGE + d.previewHeight() + DrawLayout.STRIP_TAB_GAP + DrawLayout.STRIP_TAB + DrawLayout.PAGE_RULE <= bar, "4 dp clear of the top edge, a preview, its tab along the bar's foot, the paper gap over the page, in the bar at " + at);
            int n = d.previews(width), used = n * (d.previewWidth() + d.previewGap()) - d.previewGap();
            boolean landscape = s[0] > s[1];
            check(n >= (d.compact ? 15 : 21) && (!landscape || n > d.previews(new DrawLayout(s[1], s[0]).pageWidth())), n + " previews at " + at + ", more in landscape");
            int[] w = d.stripWidths(width);
            // The + is the strip's last tile (2026-10-24), a preview's size, not a button: ‹ the number › alone are the part, over the pen's 26 dp.
            check(DrawLayout.numberPart() == DrawLayout.pageNumber() + 2 * DrawLayout.PAGE_ARROW && DrawLayout.PAGE_ARROW >= 26, "‹ the number › in one part at " + at);
            check(2 * DrawLayout.CARET_HALF < 2 * 14 && 2 * DrawLayout.CARET_HALF <= DrawLayout.PAGE_ARROW, "The carets' icons smaller than a bar icon's 28 dp, in 26 dp targets at least at " + at);
            // Zoom at the far end (Refresh there that morning, the column's again since 2026-10-28).
            check(w.length == 4 && w[0] == DrawLayout.zoomButton() && w[1] == DrawLayout.numberPart() && w[2] == DrawLayout.CARET_GAP && DrawLayout.CARET_GAP == 8 && w[3] == used
                    && w[0] + w[1] + w[2] + used + DrawLayout.STRIP_END <= width, "Zoom, the number, 8 dp of space and " + n + " previews across the page's " + width + " dp at " + at);
            for (boolean right : new boolean[]{false, true}) {
                int[] x = d.stripPlaces(width, right);
                // Zoom at the end away from the column (the column is the drawing hand's side), the number beside it, 8 dp, the previews toward the column.
                boolean ok = right ? x[0] == 0 && x[3] + used <= width : x[0] + w[0] == width && x[3] >= 0;
                for (int k = 1; k < x.length; k++) ok &= right ? x[k] == x[k - 1] + w[k - 1] : x[k] + w[k] == x[k - 1];
                check(ok, "Zoom at the far end from the column, the number, 8 dp, then the previews (" + (right ? "right" : "left") + "-hand column) at " + at);
            }
            // The strip is one target: a touch goes to the nearest preview, the gap's halves to either side.
            int step = d.previewWidth() + d.previewGap();
            check(d.previewAt(-30, n) == 0 && d.previewAt(0, n) == 0 && d.previewAt(step * 3 + 1, n) == 3 && d.previewAt(step * 3 - d.previewGap() / 2f + 0.5f, n) == 3
                    && d.previewAt(step * 3 - d.previewGap() - 0.5f, n) == 2 && d.previewAt(width * 2, n) == n - 1, "A touch goes to the nearest preview at " + at);
            System.out.println("Page bar at " + at + ": " + n + " previews");
        }
        check(DrawLayout.pageWords(2, 24).equals("3 / 24") && DrawLayout.pageWords(DrawingBook.FRONT, 24).equals(NotebookCovers.FRONT_WORD) && DrawLayout.pageSaid(2, 24).equals("Page 3 of 24"),
                "The page and its count: \"3 / 24\", \"Cover\"; said \"Page 3 of 24\"");
        for (DrawLayout.Part part : DrawLayout.COLUMN) check(!part.name().equals("TURNER"), "No page turner in the column");
        try {
            String turner = source("PageTurner");
            check(!turner.contains("ic_ph_notebook") && !turner.contains("host::notebook") && !source("InkWashBar").contains("showNotebook"),
                    "No Notebook button on the page bar (2026-10-27: the menu's Settings and Notebook Explorer; the pages grid is reached by the back gesture)");
            check(turner.contains("private float edge() { return 1 + app.dp(1); }") && turner.contains("frame.setStrokeWidth(w)") && !turner.contains("frame.setStrokeWidth(1)"),
                    "A preview's right and foot lines a pixel and 1 dp thick (3 px on the Manta), not a bare pixel");
            check(turner.contains("previous.iconHalf = next.iconHalf = DrawLayout.CARET_HALF"), "The carets' icons smaller, in their 32 dp targets");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    /**
     * The layers left the column (the refocus, 2026-10-24; the owner: "layers are not important i'm finding"): no group, no
     * fold. A page that has layers keeps them, and Page Settings (a hold on a page) has a Layers row: a chip a layer to draw
     * on, Add for a new one on top ({@code LibraryCardChecks.layers}). The three layers' trial (the owner, 2026-11-06): one
     * button straight under the picker box (over Refresh in its first try), its row {@link DrawLayout#LAYERS_ROW} tall, a
     * tap's third its layer; its icons outlined in the picker box's 1.5 dp lines at the size the column draws them.
     */
    static void layers() {
        for (DrawLayout.Part part : DrawLayout.Part.values()) check(!part.name().contains("LAYER") || part == DrawLayout.Part.LAYERS, "No layers in the column but the layer button: " + part);
        java.util.List<DrawLayout.Part> column = java.util.Arrays.asList(DrawLayout.COLUMN);
        check(column.indexOf(DrawLayout.Part.LAYERS) == column.indexOf(DrawLayout.Part.FACES) + 1, "The layer button straight under the picker box (the owner, 2026-11-06: \"right below the picker\")");
        // About 36 px a third on the Manta and the Nomad (300 dpi, 160 dp an inch; 40 with the first try's 66 dp), its icon whole inside the row's pad.
        check(DrawLayout.LAYERS_ROW * 300 / 160 / InkWashLayers.LAYERS >= 36 && DrawLayout.LAYERS_ROW < 66 && DrawLayout.LAYERS_ICON <= DrawLayout.LAYERS_ROW - 2 * DrawLayout.TRAY_TOP,
                "The layer button's thirds a finger's target, a little smaller than the first try's");
        for (int[] s : SCREENS) check(new DrawLayout(s[0], s[1]).layers == DrawLayout.LAYERS_ROW, "The layer button its whole row on the Manta and the Nomad, either way up, at " + s[0] + "x" + s[1]);
        for (String name : new String[]{"layer_top", "layer_middle", "layer_bottom"}) {
            String icon;
            try { icon = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("app/src/main/res/drawable/" + name + ".xml"))); } catch (java.io.IOException e) { throw new AssertionError(e); }
            check(icon.contains("android:height=\"" + DrawLayout.LAYERS_ICON + "dp\"") && icon.contains("android:viewportHeight=\"" + DrawLayout.LAYERS_ICON + "\"")
                            && icon.split("android:strokeWidth=\"1.5\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\"", -1).length == 4 && icon.split("android:fillColor", -1).length == 2,
                    name + ": three diamonds in 1.5 dp lines, round caps and joins, drawn at its own size in dp, one filled");
        }
        for (int[] s : SCREENS) { int row = new DrawLayout(s[0], s[1]).layers; check(row == 0 || row >= DrawLayout.LEAST[DrawLayout.Item.ICON.ordinal()], "The layer button an icon row at the least, or none, at " + s[0] + "x" + s[1] + ": " + row); System.out.println("Layer button at " + s[0] + "x" + s[1] + ": " + row + " dp"); }
        try {
            String screens = source("NotebookScreens"), bar = source("InkWashBar");
            check(screens.contains("case LAYERS: {") && screens.contains("app.drawOn(page);") && screens.contains("app.newLayer(true)") && !bar.contains("LayerColumn")
                            && bar.contains("case LAYERS: view.addView(layers, item(fitted.layers));") && bar.contains("app.pickLayer(DrawLayout.layerAt("),
                    "Page Settings' Layers row draws on a layer or adds one; the column has the layer button alone");
            // Its hold (the owner, 2026-11-06: "long press should bring up the visibility options"): Layer 3 over 2 over 1, each
            // name picking its layer and its eye stepping it as Show Kinds' do (one cell for both), beside the button, in the fan too
            // (the button itself is lent); a stroke on a hidden layer refused with its note.
            String app = source("PaintActivity");
            check(bar.contains("layers.setOnLongClickListener(v -> { layersPopup(); return true; });") && bar.contains("for (int l = InkWashLayers.LAYERS; l >= 1; l--) {")
                            && bar.contains("step -> app.layerStep(layer, step), () -> { hideLabel(); app.pickLayer(layer); })") && bar.contains("showLabel(list, true, 0, layers);")
                            && bar.contains("ACTION_LONG_CLICK, DrawLayout.LAYER_VISIBILITY)") && bar.split("private LinearLayout eyeCell\\(", -1).length == 2
                            && app.contains("layerChange(() -> InkWashLayers.setStep(page, layer, step));")
                            && app.contains("int layer = erased > 0 ? erased : Math.max(1, InkWashLayers.layer(pad.document))") && app.contains("hidden = DrawLayout.layerHiddenNote(layer);"),
                    "The layer button's hold: each layer's eye and name beside it, Show Kinds' steps on its pair, an Undo step each; a hidden layer refuses strokes (the eraser's own layer when set to one)");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        book();
    }
    /** No Page Bar row (the owner, 2026-11-07: "remove the option to show or hide the page bar (this is unnecessary now)"): the page bar shows but in Zen Mode. */
    static void book() {
        check(!java.util.Arrays.toString(DrawLayout.Part.values()).contains("BOOK"), "No book in the column");
        check(DrawLayout.fileRows(true, null, false, null, false, true, false, false).stream().noneMatch(r -> "pageBar".equals(r.key)), "No Page Bar row");
        try {
            String app = source("PaintActivity");
            check(!app.contains("togglePageBar") && !app.contains("barHidden") && app.contains("setVisibility(!zen ? View.VISIBLE : View.GONE)"), "The page bar hidden by Zen Mode alone");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    /**
     * The owner (2026-10-24): "the toolbar may become 10 slots"; ten from 2026-10-27 ("we're at 8 now might as well have
     * 10"; {@link DrawLayout#TRAY_ROWS} is the one number), nine since 2026-11-06 with Fill in a tool row of its own, so the
     * Manta's column holds twelve rows still. The Manta and the Nomad either way up fit them: nothing under
     * its least, nothing past the foot. The tray's rows give way, evenly, the rest as with eight: 34 dp on the Manta upright
     * (as with eight), 32 turned (34), 32 on the Nomad upright (32), 24 turned (30), where the icons, the faces and the
     * small rows stay at their least as with eight.
     */
    static void allSlots() {
        check(DrawLayout.TRAY_ROWS == ToolSlots.COUNT && ToolSlots.COUNT == 9 && new DrawLayout(1024, 1365).trays == ToolSlots.COUNT, "The tray's rows are the slot count, nine");
        // With eight (before 2026-10-27), each screen's icon row, face row, small row and how many small rows.
        // The Manta turned 34, the Nomad turned 26, since rows left the column (2026-10-28; 32 and 24 until then); the Nomad
        // 38 and 30 since it shows eight (2026-10-28; 32 and 26 with ten).
        // The Nomad turned 28 since Shapes and Play's small row (2026-10-30; 30 until then).
        // The Manta turned 30 with the layer button's first row (2026-11-06), 34 again once the sand timer, Show Kinds and Play left (that day).
        // The Nomad's eight with three tool rows (2026-11-06): 34 upright, 26 turned (38 and 28 with two).
        int[] rows = {34, 34, 34, 26};
        int[][] eight = {{44, 30, 36, 3}, {44, 30, 36, 3}, {40, 27, 34, 3}, {30, 27, 28, 2}};
        for (int i = 0; i < SCREENS.length; i++) {
            int[] s = SCREENS[i];
            DrawLayout d = new DrawLayout(s[0], s[1]);
            check(d.fit >= 0 && d.spare >= 0 && d.slot >= DrawLayout.LEAST[DrawLayout.Item.SLOT.ordinal()] && d.trayStroke() >= 16 && d.slot == rows[i],
                    "Nine slots and three tool rows fit " + s[0] + "x" + s[1] + ": fit " + d.fit + ", spare " + d.spare + ", rows " + d.slot + " dp");
            check(d.icon >= eight[i][0] - 1 && d.face >= eight[i][1] - 1 && d.wet >= eight[i][2] - 1,
                    "The other rows as with eight, at most a dp less, at " + s[0] + "x" + s[1] + ": icons " + d.icon + ", faces " + d.face + ", small rows " + d.wet);
        }
        check(DrawLayout.TRAY_TOP >= 2, "The rows' strokes clear of each other");
        try {
            // No lines between the tray's rows (the owner, 2026-10-24: "remove lines between preview strokes").
            check(!source("InkWashBar").contains("button.overlay = (canvas, b) -> canvas.drawRect(0, 0"), "No hairline between the tray's rows");
        } catch (java.io.IOException e) { throw new AssertionError("the sources: " + e); }
    }
    /**
     * The owner (2026-10-28): "eight slots for the nomad it's fine. Just cut off the bottom two." The Nomad either way up
     * shows slots 1 to 8, the Manta all nine, the Tools page's Toolbar as many; slot 9 stays stored (the toolbar is
     * the same), and selected gives way to the first on the Nomad.
     */
    static void nomadEight() {
        for (int[] s : SCREENS) {
            DrawLayout d = new DrawLayout(s[0], s[1]);
            int want = d.compact ? 8 : ToolSlots.COUNT;
            check(d.trays == want && new ToolsLayout(s[0], s[1]).slotCount == want, "The column and the Toolbar show " + want + " at " + s[0] + "x" + s[1]);
            for (int slot = 0; slot < ToolSlots.COUNT; slot++)
                check(DrawLayout.slotHidden(slot, d.trays) == (slot >= want), "Slot " + (slot + 1) + (slot >= want ? " gives way to the first" : " stays selected") + " at " + s[0] + "x" + s[1]);
            for (int tool : new int[]{ToolSlots.ERASER, ToolSlots.SMUDGE, ToolSlots.STAMP, ToolSlots.FILL}) check(!DrawLayout.slotHidden(tool, d.trays), "The other tools stay selected: " + tool);
        }
        try {
            String bar = source("InkWashBar");
            check(bar.contains("int shown = Math.min(rules.slotsShown(), fitted.trays);") && bar.contains("DrawLayout.slotHidden(selected, shown)) selectTool(0);")
                    && !source("ToolSlots").contains("trays"), "The bar hides slot 9 on the Nomad and selects the first; the toolbar keeps all nine");
        } catch (java.io.IOException e) { throw new AssertionError("the sources: " + e); }
    }
    /** {@code dp} in the Manta's pixels, 1920 across its 1024 dp. */
    static int mantaPx(double dp) { return (int) Math.round(dp * 1920 / 1024); }
    /**
     * The picker box (the owner's drawing, 2026-10-27: "i'd like to try using it to replace the shade picker and size
     * select"): on every screen it fills the room the strip and the slider took side by side, top and foot at the
     * strip's and the track's ends, its tabs' rooms outside it; a pen goes to the triangle it lands in (the grey strip's
     * lower left, the slider's upper right), on the diagonal's gap to the nearer, in a tab's room to that tab's, mirrored
     * for the left hand; and the top, the middle and the foot give the old strip's greys and the old wedge's sizes.
     */
    static void pickerBox() {
        // Drawn clean as Iconoir-weight outlines (the owner, 2026-10-29: "b 164 looks good ... have the triangle tip hit the
        // furthest corner. they need to be thin"): the shade's white tip in the box's top-left corner and the slider's fine
        // tip in its foot-right one, exactly; their long sides parallel, an even 1.5 dp gap square to them; each outline
        // the triangle less one 1.5 dp in, so a tip is a mitred sliver closing to a point, never a cut-off end.
        // The shade side lost its outline (2026-10-29: "so it's easier to see the right shade"): its filled triangle, the slider's outline.
        double[] shade = boxPath("shape_box_shade"), size = boxPath("shape_box_size"), fill = shade;
        double dx = shade[10] - shade[0], dy = shade[11] - shade[1], sx = size[10] - size[0], sy = size[11] - size[1];
        double apart = Math.abs((size[0] - shade[0]) * dy - (size[1] - shade[1]) * dx) / Math.hypot(dx, dy);
        check(shade[0] == 0 && shade[1] == 0 && size[0] == 56 && size[1] == 176 && Math.abs(dx * sy - dy * sx) < 1e-6
                        && Math.abs(apart - 1.5) < .05 && java.util.Arrays.equals(java.util.Arrays.copyOf(fill, 12), java.util.Arrays.copyOf(shade, 12)),
                "The tips in their corners, the long sides parallel " + apart + " dp apart");
        for (double[] ring : new double[][]{size}) {
            // The inner triangle (its last three points): every side 1.5 dp in from the outer's.
            double[] tip = {ring[0], ring[1]}, in = {ring[12], ring[13]};
            double far = Math.hypot(in[0] - tip[0], in[1] - tip[1]);
            check(Math.abs(Math.abs(ring[12] - ring[0]) - 1.5) < 1e-3 && far > 6, "Each outline 1.5 dp, its tip a sliver " + far + " dp long");
        }
        double density = 1920 / 1024.0;  // the Manta's px a dp (mantaPx)
        for (int[] s : SCREENS) {
            DrawLayout at = new DrawLayout(s[0], s[1]);
            float half = at.half(), side = at.faceSide(), gap = DrawLayout.FACE_GAP / 2f, width = 2 * half, tall = at.box;
            float left = half - gap - side, right = half + gap + side, top = DrawLayout.sliderY(1, tall), foot = DrawLayout.sliderY(0, tall);
            int length = at.box - DrawLayout.FACE_GAP;
            String where = " at " + s[0] + "x" + s[1];
            // Half as long again as the four sizes' squares were (the owner, 2026-10-29: "stretch the shade selector and size
            // selector a bit to allow for more fine control? maybe 1.5x the height"), only into the room to spare ("grow only
            // into spare room"): its four rows as the faces' fit, then all it can of the spare before the gaps grow; on the
            // Manta and the Nomad upright 1.5x; turned, its four rows, no spare left to grow into since Shapes and Play's row
            // (2026-10-30; the Manta's over 1.35x, the Nomad's a little until then).
            int was = DrawLayout.FACE_ROWS * at.preferred()[DrawLayout.Item.FACE.ordinal()], rows = DrawLayout.FACE_ROWS * at.face;
            check(at.box >= rows && at.box <= at.boxMost() && (at.box == at.boxMost() || at.spare == 0 && at.gap <= at.preferred()[DrawLayout.Item.GAP.ordinal()])
                            && (s[0] > s[1] || at.box * 2 == was * 3),
                    "The box " + at.box + " dp long, " + was + " before" + where);
            check(top == DrawLayout.PAIR_PAD && foot == DrawLayout.PAIR_PAD + length && right - left == 2 * side + DrawLayout.FACE_GAP
                            && left - DrawLayout.faceMarkRoom() >= 0 && right + DrawLayout.faceMarkRoom() <= width,
                    "The box " + (right - left) + " x " + (foot - top) + " dp, the strip's length and the pair's width, its tabs' rooms inside the column" + where);
            float w = right - left, h = foot - top;
            check(DrawLayout.boxShade(left + .2f * w, top + .8f * h, left, top, right, foot) && !DrawLayout.boxShade(left + .8f * w, top + .2f * h, left, top, right, foot),
                    "Lower left the shade, upper right the size" + where);
            check(DrawLayout.boxShade(left - 2, top + h / 2, left, top, right, foot) && DrawLayout.boxShade(left - 2, top, left, top, right, foot)
                            && !DrawLayout.boxShade(right + 2, top + h / 2, left, top, right, foot) && !DrawLayout.boxShade(width - 1, foot - 1, left, top, right, foot),
                    "The shade's tab room the shade's, the size's the size's" + where);
            for (float t = .1f; t < 1; t += .2f) {
                float x = left + t * w, y = top + t * h;  // on the diagonal, the gap's middle
                check(DrawLayout.boxShade(x, y + 1, left, top, right, foot) && DrawLayout.boxShade(x - 1, y, left, top, right, foot)
                                && !DrawLayout.boxShade(x, y - 1, left, top, right, foot) && !DrawLayout.boxShade(x + 1, y, left, top, right, foot),
                        "On the gap, a dp below or left the shade's, above or right the size's" + where);
            }
            // The left hand: drawn mirrored, the shade lower right; the caller turns x (SizeSlider.shade).
            check(DrawLayout.boxShade(width - (right - .2f * w), top + .8f * h, left, top, right, foot) && !DrawLayout.boxShade(width - (left + .2f * w), top + .2f * h, left, top, right, foot),
                    "Mirrored, the shade lower right and the size upper left" + where);
            // The values: the slider's track and the strip run from the box's top to its foot, as before.
            check(DrawLayout.sliderAt(top, tall) == 1 && Math.abs(DrawLayout.sliderAt((top + foot) / 2, tall) - .5) < 1e-6 && DrawLayout.sliderAt(foot, tall) == 0,
                    "The size broadest at the box's top, halfway at its middle, finest at its foot" + where);
            int inset = (int) Math.round(DrawLayout.PAIR_PAD * density), px = (int) Math.round(length * density);
            int endpoint = ShadeStrip.endpoint(px, (int) Math.round(ShadeStrip.END * density));
            for (float y : new float[]{inset, inset + px / 2f, inset + px - 1}) {
                // GreyStrip.at until the box, inline.
                double old = ToolSwatches.darkness(ShadeStrip.toneAt(px - (y - inset), px, endpoint)), now = DrawLayout.shadeAt(y, inset, px, endpoint);
                check(old == now, "The grey at " + y + " px the old strip's: " + now + ", " + old + where);
            }
            double mid = DrawLayout.shadeAt(inset + px / 2f, inset, px, endpoint);
            check(DrawLayout.shadeAt(inset, inset, px, endpoint) == 0 && DrawLayout.shadeAt(inset + px - 1, inset, px, endpoint) == 1 && mid > 0 && mid < 1,
                    "White at the box's top, a grey at its middle (" + mid + "), black at its foot" + where);
        }
    }
    /**
     * The column's parts' tops ({@link DrawLayout#tops}, the browser's column): stacked from the top's pad down to the foot
     * over Zen Mode's cell, each its height, the stretch what is left (the spare less the snug gaps' and Refresh's margin
     * room); the gaps InkWashBar gives, the picker box's two at half.
     */
    static void tops() {
        int[][] screens = {{1404, 1872}, {1024, 1366}, {820, 1180}, {1180, 820}, {390, 844}, {749, 998}, {998, 749}};
        for (int[] s : screens) {
            DrawLayout d = new DrawLayout(s[0], s[1]);
            int[] top = d.tops();
            for (int i = 0; i < DrawLayout.COLUMN.length; i++)
                check(top[i + 1] - top[i] == (DrawLayout.COLUMN[i] == DrawLayout.Part.STRETCH ? top[i + 1] - top[i] : d.partHeight(i)) && top[i + 1] >= top[i], s[0] + "x" + s[1] + ": part " + i);
            check(top[0] == DrawLayout.PAD && top[DrawLayout.COLUMN.length] == d.columnHeight() - DrawLayout.PAD || d.spare < 0, s[0] + "x" + s[1] + ": foot " + top[DrawLayout.COLUMN.length]);
            int stretch = java.util.Arrays.asList(DrawLayout.COLUMN).indexOf(DrawLayout.Part.STRETCH);
            check(d.spare < 0 || top[stretch + 1] - top[stretch] == d.spare + 2 * d.gap - d.gap / 2 * 2 - d.ghostMargin(), s[0] + "x" + s[1] + ": stretch " + (top[stretch + 1] - top[stretch]) + ", spare " + d.spare);
            int gaps = 0;
            for (int g = 0; g < DrawLayout.GAPS; g++) gaps += d.gapHeight(g);
            check(gaps == (DrawLayout.GAPS - 2) * d.gap + d.gap / 2 * 2, "the picker box's gaps at half");
        }
        try {
            String bar = source("InkWashBar");
            check(bar.contains("app.dp(at.gapHeight(g))") && !bar.contains("gapIndex"), "InkWashBar sizes its gaps by DrawLayout.gapHeight");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
