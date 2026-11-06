package io.github.mpdairy.monopaint;

import java.time.LocalDate;

/**
 * What moved into pure Java for the browser app's notebooks (M3): inside a notebook, the strip's slots and the turns
 * ({@link NotebookStrip}), Delete Page on a stack, a shelf tile's words; a name's signs told without a Unicode regex
 * ({@link DrawingFiles#nameSign}), the same as the regex they replace.
 */
public final class NotebookStripChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) throws Exception {
        DrawingBook book = new DrawingBook(InkWashLayers.blank(60, 80));
        book.addPage(); book.addPage();
        int card = NotebookStrip.CARD, front = DrawingBook.FRONT, back = DrawingBook.BACK;
        // The strip (2026-11-03: the covers back "on the list of pages", not sticky; the card is the bar's Notebook Settings').
        check(NotebookStrip.slots(book) == 6, "slots: front, 3 pages, +, back");
        int[] places = {front, 0, 1, 2, NotebookStrip.ADD, back};
        for (int s = 0; s < places.length; s++) {
            check(NotebookStrip.place(book, s) == places[s], "slot " + s);
            if (places[s] != NotebookStrip.ADD) check(NotebookStrip.slotOf(book, places[s]) == s, "slotOf " + s);
        }
        check(NotebookStrip.slotOf(book, card) == 0, "the card is inside the front cover");
        check(NotebookStrip.start(book, back, 3) == 3 && NotebookStrip.start(book, 2, 3) == 2 && NotebookStrip.start(book, front, 3) == 0, "the window scrolls the front cover away");
        // ‹ › step through the covers, ‹ greyed on the front cover, › on the back; from the card › to the front cover.
        check(NotebookStrip.turn(book, card, -1) == DrawingBook.NONE && NotebookStrip.turn(book, card, 1) == front, "from the card › to the front cover");
        check(NotebookStrip.turn(book, front, -1) == DrawingBook.NONE && NotebookStrip.turn(book, front, 1) == 0, "the front cover first, then page 1");
        check(NotebookStrip.turn(book, 0, -1) == front && NotebookStrip.turn(book, 2, 1) == back && NotebookStrip.turn(book, back, 1) == DrawingBook.NONE, "the back cover last");
        // Delete Page on the page drawn on: the page after it drawn on first; a blank page doesn't ask.
        book.select(1);
        check(!NotebookStrip.deleteAsks(book, 1), "a blank page is deleted without asking");
        NotebookStrip.deleteStack(book, 1);
        check(book.count() == 2 && book.index() == 1, "Delete Page: 2 pages left, the next drawn on (" + book.index() + ")");
        NotebookStrip.deleteStack(book, front);
        check(book.count() == 2, "a cover is never deleted");
        LocalDate today = LocalDate.of(2026, 11, 1);
        check(NotebookStrip.about(1, null, today).equals("1 page · today") && NotebookStrip.about(24, today.minusDays(1), today).equals("24 pages · yesterday")
                && NotebookStrip.about(3, LocalDate.of(2026, 10, 17), today).equals("3 pages · Oct 17"), "a tile's words");
        // nameSign is [\p{L}\p{N} _-] for every char of the Basic Multilingual Plane and some beyond.
        java.util.regex.Pattern sign = java.util.regex.Pattern.compile("[\\p{L}\\p{N} _-]");
        for (int c = 0; c < 0x20000; c++) {
            if (Character.isSurrogate((char) Math.min(c, 0xffff)) && c <= 0xffff) continue;
            check(DrawingFiles.nameSign(c) == sign.matcher(new String(Character.toChars(c))).matches(), "nameSign " + Integer.toHexString(c));
        }
        check(DrawingFiles.signsAsSpaces("Harbour, «Sketches»!").equals("Harbour, «Sketches»!".replaceAll("[^\\p{L}\\p{N} _-]+", " ")) && NotebookMigration.safeName("a,,b", "x").equals("a b"), "other signs as one space");
        System.out.println("PASS: inside a notebook, the strip's slots (the card, the covers, the pages, +) and turns; Delete Page on the page drawn on draws on the next, never a cover; a tile's \"24 pages · yesterday\"; a name's letters, numbers, spaces, - and _ told as the Unicode regex told them");
    }
}
