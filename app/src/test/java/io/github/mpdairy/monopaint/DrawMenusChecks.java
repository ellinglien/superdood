package io.github.mpdairy.monopaint;

import java.util.List;

/**
 * What the drawing screen's menus, Text's box menu, the Lasso and the tools' held paint decide, in pure Java so the
 * Manta and the browser (web/engine's WebEngine, the browser app's M4) agree: {@link DrawLayout#fileRows}, {@link
 * DrawLayout#playRows}, {@link DrawLayout#shapeRows}, {@link DrawLayout.MirrorRow}, {@link TextBoxes#menu}, {@link
 * LassoPieces}, {@link ToolSlots#holder} and {@link ToolSlots#clean}.
 */
public final class DrawMenusChecks {
    public static void main(String[] args) throws java.io.IOException {
        menus(); mirrors(); text(); lasso(); held(); menuFromMenu();
        System.out.println("Drawing screen menu checks passed");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    private static String words(List<DrawLayout.Row> rows) {
        StringBuilder s = new StringBuilder();
        for (DrawLayout.Row r : rows) s.append(r.words == null ? "|" : r.words + (r.marked ? "*" : "")).append(';');
        return s.toString();
    }

    private static void menus() {
        // The owner's order (2026-11-06): the notebook's; the page's with Show Kinds and Page Bar; Draw Together, the Sand Timer and Play; the hand and System Settings. Finger Action System Settings' on the Manta.
        check(words(DrawLayout.fileRows(true, null, false, "Draw Together", true, true, true, true, true)).equals("Notebook Explorer;Notebook Settings;|;Export Page;Import;Show Kinds;Page Bar*;|;Draw Together*;Sand Timer;Play*;|;Left-Handed;System Settings;"),
                "The Manta's menu: the notebook's rows, the page's, the shared and the toys, the app's, a line between each");
        check(words(DrawLayout.fileRows(true, null, false, null, false, true, false, false, false)).equals("Notebook Explorer;Notebook Settings;|;Export Page;Import;Show Kinds;Page Bar;|;Play;|;Left-Handed;System Settings;"), "No Sand Timer row while it can't be set");
        check(words(DrawLayout.fileRows(true, "Finger Action: Eraser", false, null, false, false, false, false, true)).equals("Notebook Explorer;Notebook Settings;|;Export Page;Import;|;Finger Action: Eraser;Left-Handed;System Settings;"), "The browser's menu with the Manta's notebook: its Finger Action row, no column rows");
        check(words(DrawLayout.fileRows(false, "Finger Action: Eraser", true, null, false, false, false, false, true)).equals("Import;|;Finger Action: Eraser;Right-Handed;System Settings;"), "A guest's menu: Import, no notebook rows; the other hand named");
        check(words(DrawLayout.playRows(true, false, true, true, true, false)).equals("Text*;Magnetic Poetry;Shapes*;Spirograph;Mirrors*;|;Replay Page;"), "Play: Text, Magnetic Poetry, Shapes, Spirograph, Mirrors, then Replay Page");
        check(words(DrawLayout.playRows(false, false, false, false, false, true)).equals("Text;Shapes;Mirrors;"), "A guest's Play: no Magnetic Poetry, Spirograph or Replay");
        // The release pare-down (2026-11-05): Play holds the five toys (Text, Magnetic Poetry, Shapes, Spirograph, Replay
        // Page), the column has no Shapes button, and the menu lists none of the hidden things.
        String play = words(DrawLayout.playRows(false, false, false, false, true, false));
        for (String toy : new String[]{DrawLayout.TEXT, DrawLayout.POETRY, DrawLayout.SHAPES, DrawLayout.SPIROGRAPH, DrawLayout.REPLAY_PAGE})
            check(play.contains(toy + ";"), "Play holds " + toy);
        check(ToolKits.preset(DrawLayout.SPIROGRAPH) != null, "Play's Spirograph is a Basic tool");
        String menu = words(DrawLayout.fileRows(true, null, false, "Draw Together", false, true, true, false, true));
        for (String hidden : new String[]{"Brush Lab", DrawLayout.BRUSH_LAB, "Community", "Library", LibraryCard.PAGE_COLOUR, LibraryCard.SEE_THROUGH,
                DrawLayout.TEXT, DrawLayout.POETRY, DrawLayout.SHAPES, DrawLayout.SPIROGRAPH, DrawLayout.REPLAY_PAGE, "Finger Action", "Prompt"})
            check(!menu.contains(hidden), "The menu has no " + hidden);
        check(!LibraryCard.rows().contains(LibraryCard.Part.PAGE_COLOUR) && !LibraryCard.rows().contains(LibraryCard.Part.TRANSPARENCY),
                "Notebook Settings has no Page Colour or See-Through Pages");
        java.util.List<LibraryCard.Part> page = LibraryCard.pageRows(TracingPaper.NONE, true);
        check(page.contains(LibraryCard.Part.PAGE_COLOUR) && page.contains(LibraryCard.Part.TRANSPARENCY), "Page Settings has Page Colour and See-Through Pages");
        ToolSettings shapes = ToolSettings.defaults(ToolSettings.Tool.SHAPES);
        shapes = DrawLayout.shapePicked(shapes, "OVAL");
        check(words(DrawLayout.shapeRows(shapes, true)).equals("Line;Rectangle;Square;Oval*;Circle;Filled" + (shapes.filled ? "*" : "") + ";"), "Shapes: the one drawn marked");
        boolean filled = shapes.filled;
        shapes = DrawLayout.shapePicked(shapes, "filled");
        check(shapes.filled != filled && shapes.shape == ToolSettings.Shape.OVAL, "Filled turns over, the shape kept");
        for (DrawLayout.Row r : DrawLayout.shapeRows(shapes, false)) check(!r.marked || r.key.equals("filled"), "Shapes put down: no shape marked");
    }

    private static void mirrors() {
        ToneDocument page = InkWashLayers.blank(200, 160);
        check(words(DrawLayout.MirrorRow.rows(page.mirrors(), false, false)).equals("Mirrors;Show Guides*;Copy Strokes*;Real Mirror;Place Mirror;Kaleidoscope;Remove Mirrors;"), "Mirrors' list, none yet: no Flip Side or Keep Reflection");
        DrawLayout.MirrorRow.KALEIDOSCOPE.change(page);
        check(page.mirrors().acts() && page.mirrors().size() == 3, "Kaleidoscope: three mirrors through the centre");
        check(page.undo() && !page.mirrors().acts(), "One Undo step");
        page.redo();
        DrawLayout.MirrorRow.REAL.change(page);
        check(page.mirrors().reflects(), "Real Mirror reflects");
        String real = words(DrawLayout.MirrorRow.rows(page.mirrors(), false, false));
        check(real.contains("Flip Side") && real.contains("Keep Reflection"), "Reflecting: Flip Side and Keep Reflection");
        check(!words(DrawLayout.MirrorRow.rows(page.mirrors(), true, true)).contains("Real Mirror") && words(DrawLayout.MirrorRow.rows(page.mirrors(), true, true)).contains("Place Mirror*"), "A guest's list: no Real Mirror; Place Mirror marked while placing");
        check(DrawLayout.MirrorRow.REAL.stopsPlacing(page.mirrors()) && !DrawLayout.MirrorRow.COPY.stopsPlacing(page.mirrors()), "Real Mirror stops a mirror being placed, Copy Strokes doesn't");
        DrawLayout.MirrorRow.MIRRORS.change(page);
        check(!page.mirrors().on && DrawLayout.MirrorRow.MIRRORS.stopsPlacing(page.mirrors()), "Mirrors off stops placing");
        DrawLayout.MirrorRow.CLEAR.change(page);
        check(page.mirrors().isEmpty(), "Remove Mirrors");
    }

    private static void text() {
        TextBoxes.Box b = TextBoxes.placed(300, 300, 3000, 3000).withText("Hello");
        check(words(TextBoxes.menu(b)).equals("Small;Medium*;Large;Huge;|;Bold;|;Left*;Centre;Right;|;Make Ink;Delete;"), "A box's menu");
        check(TextBoxes.restyled(b, "size3").size == 3 && TextBoxes.restyled(b, "bold").bold && TextBoxes.restyled(b, "align2").align == 2, "Its rows restyle it");
        check(TextBoxes.restyled(b, "ink") == null && TextBoxes.restyled(b, "delete") == null, "Make Ink and Delete are the caller's");
        TextBoxes.Box moved = TextBoxes.dragged(b, false, 310, 310, 360, 330, 3000, 3000);
        check(moved.x == b.x + 50 && moved.y == b.y + 20 && moved.width == b.width, "A drag moves it");
        TextBoxes.Box wide = TextBoxes.dragged(b, true, 0, 0, -10000, 0, 3000, 3000);
        check(wide.width == TextBoxes.least(b.size) && wide.x == b.x, "A drag from its edge sets its width, never under the least");
    }

    private static void lasso() {
        ToneDocument page = InkWashLayers.blank(100, 80);
        page.selectLayer(InkWashLayers.INK);
        page.begin(1, ToneDocument.KIND_PEN);
        for (int y = 20; y < 30; y++) for (int x = 20; x < 30; x++) page.paintTone(x, y, 0);
        page.finish();
        float[] loop = {10, 10, 40, 10, 40, 40, 10, 40};
        LassoPieces lasso = new LassoPieces();
        lasso.floatPiece(LassoPlanes.lift(page, loop, 4));
        check(lasso.floats() && lasso.unmoved() && !lasso.place(page) && !lasso.floats(), "A lift put down unmoved changes nothing");
        lasso.floatPiece(LassoPlanes.lift(page, loop, 4));
        check(lasso.grab(25, 25, new float[]{-100, -100, -100, -100}, 2), "The pen on the piece grabs it");
        lasso.drag(65, 45); lasso.release();
        check(lasso.place(page) && page.compositeTone(25, 25) == 255 && page.compositeTone(65, 45) < 64, "Moved and put down");
        page.undo();
        check(page.compositeTone(25, 25) < 64, "One Undo step");
        lasso.floatPiece(LassoPlanes.lift(page, loop, 4));
        check(lasso.cut(page) && lasso.hasKept() && !lasso.floats() && page.compositeTone(25, 25) == 255, "Cut: kept, and gone from the page");
        FloatingSelection copy = lasso.pasted(page);
        check(copy != null && lasso.pasted(InkWashLayers.blank(90, 80)) == null, "Paste on a page of its size only");
        lasso.floatPiece(copy);
        check(lasso.place(page) && page.compositeTone(25, 25) < 64, "Pasted where it was cut");
        lasso.floatPiece(LassoPlanes.lift(page, loop, 4));
        FloatingSelection dup = lasso.duplicate(page);
        check(dup != null && lasso.floating == dup && dup.centerX == copy.centerX + DrawLayout.DUPLICATE_OFFSET, "Duplicate: put down, a copy floating down and right");
        lasso.end();
        lasso.floatPiece(LassoPlanes.lift(page, loop, 4));
        check(lasso.delete(page) && page.compositeTone(25, 25) == 255, "Delete takes what it rang");
    }

    /**
     * A popup opened from a menu's row (Play, Mirrors from Play, System Settings) waits for that menu to leave the panel
     * and get its clean, in the one shared show (the owner, 2026-11-06: "play dropdown... impossible to read").
     */
    private static void menuFromMenu() throws java.io.IOException {
        String app = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("app/src/main/java/io/github/mpdairy/monopaint/PaintActivity.java")));
        check(app.contains("menuLeaving = android.os.SystemClock.uptimeMillis() + NotebookScreens.MENU_GONE_MS;\n                menu.dismiss();"), "A row's tap marks its menu leaving");
        check(app.contains("long wait = menuLeaving - android.os.SystemClock.uptimeMillis();\n        if (wait > 0)"), "The shared show waits while a menu leaves");
        check(app.contains("case \"play\": actions[i] = () -> { if (!busy()) playMenu(anchor); };") && app.contains("menu(anchor, \"play menu\", rows, actions,"), "Play opens through the shared menu");
        check(!app.contains("shown.covered.union("), "System Settings waits as Play does, no clean of its own");
    }
    private static void held() {
        check(ToolSlots.toolKey(ToolSlots.ERASER).equals(ToolSwatches.ERASER_KEY) && ToolSlots.toolKey(ToolSlots.SMUDGE).equals(ToolSlots.SMUDGE_KEY) && ToolSlots.toolKey(3).equals("slot3"), "The tools' keys");
        check(ToolSlots.holder(3, true).equals("slot3/finger") && ToolSlots.holder(3, false).equals("slot3"), "A finger's paint apart from the pen's");
        HeldPaint held = new HeldPaint();
        check(!ToolSlots.clean(held, 3), "Clean Brush on a clean brush: nothing held");
    }
}
