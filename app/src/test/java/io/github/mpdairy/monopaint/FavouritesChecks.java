package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Favourites (the owner, 2026-11-06): a page's star copies it, every layer, to the end of Favourites, and both carry the
 * same mark ({@link DrawingBook#fave}, the options block's PFAV); the mark follows its page, survives the file and the
 * recovery; unstar deletes the copy (never Favourites' last page: the notebook goes instead); a notebook without the
 * record opens with no page starred.
 */
public final class FavouritesChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) throws Exception {
        DrawingBook book = new DrawingBook(InkWashLayers.blank(60, 80));
        book.addPage(); book.addLayer(true); book.addPage();
        check(book.stacks().count() == 3 && book.stacks().size(1) == 2, "three pages, the second of two layers");
        book.setPageTemplate(1, NotebookTemplate.GRID);
        // Star page 2 (its two layers): copied to a new Favourites, both marked alike.
        DrawingBook favourites = DrawingBook.fresh(InkWashLayers.blank(60, 80));
        favourites.append(book.copyStack(1), 7);
        NotebookStrip.deleteStack(favourites, 0);
        book.setFave(book.stacks().first(1), 7);
        check(favourites.stacks().count() == 1 && favourites.count() == 2 && favourites.fave(0) == 7 && favourites.fave(1) == 7, "the copy: one page of two layers, marked");
        check(favourites.template(0).equals(NotebookTemplate.GRID) && favourites.surface(0) == book.surface(2), "the copy keeps its page's template and surface");
        check(book.fave(2) == 7 && book.fave(0) == 0 && book.fave(3) == 0 && book.fave(DrawingBook.FRONT) == 0, "the page starred, its layers with it, no other");
        // The mark goes with its page, through the file and the recovery.
        book.moveStack(1, 0);
        check(book.fave(0) == 7 && book.fave(2) == 0, "moved, the star goes with it");
        DrawingBook read = BookCodec.read(new ByteArrayInputStream(NotebookOptionsChecks.bytes(book)));
        check(read.fave(0) == 7 && read.fave(1) == 7 && read.fave(2) == 0, "the star read back from the file");
        ByteArrayOutputStream recovery = new ByteArrayOutputStream();
        RecoveryCodec.write(recovery, book.snapshot(), "");
        check(RecoveryCodec.read(new ByteArrayInputStream(recovery.toByteArray())).book.fave(0) == 7, "and from the recovery");
        // A second star, then unstar the first: its copy goes, the other stays.
        favourites.append(book.copyStack(2), 9);
        check(favourites.stacks().count() == 2 && favourites.fave(2) == 9, "a second copy after the first");
        DrawingBook saved = BookCodec.read(new ByteArrayInputStream(NotebookOptionsChecks.bytes(favourites)));
        check(NotebookStrip.unstar(saved, 7) && saved.stacks().count() == 1 && saved.count() == 1 && saved.fave(0) == 9, "unstar deletes the copy, both layers");
        // The star shows filled only while Favourites holds the copy: one deleted there leaves its page's star empty.
        java.util.Set<Integer> held = NotebookStrip.marks(BookCodec.read(new ByteArrayInputStream(NotebookOptionsChecks.bytes(saved))).pageFaves());
        check(held.equals(java.util.Collections.singleton(9)) && NotebookStrip.filled(9, held) && !NotebookStrip.filled(7, held) && !NotebookStrip.filled(0, held)
                && NotebookStrip.filled(7, null) && NotebookStrip.marks(null).isEmpty(), "filled only for a copy Favourites holds (the mark alone until it is read)");
        check(!NotebookStrip.unstar(saved, 9) && saved.count() == 1, "the last copy isn't deleted: Favourites goes instead");
        check(NotebookStrip.unstar(saved, 5) && saved.count() == 1, "a copy already gone: nothing to delete");
        // No star: no record, so an older notebook's file is byte for byte as before and opens with none.
        book.setFave(0, 0);
        byte[] plain = NotebookOptionsChecks.bytes(book);
        check(!new String(plain, java.nio.charset.StandardCharsets.ISO_8859_1).contains("PFAV"), "no star, no PFAV");
        DrawingBook old = BookCodec.read(new ByteArrayInputStream(NotebookOptionsChecks.bytes(new DrawingBook(InkWashLayers.blank(60, 80)))));
        check(old.fave(0) == 0 && old.pageFaves().length == 1, "a notebook without the record opens, nothing starred");
        check(BookOptions.pageFaves(BookOptions.pageFaveBytes(new int[]{0, 3}), 3) == null && BookOptions.pageFaveBytes(new int[2]) == null, "a record for another page count is passed by");
        check(NotebookStrip.favourites("Favourites") && !NotebookStrip.favourites("Folder/Favourites") && NotebookStrip.newFave(new java.util.Random(1)) != 0, "Favourites is the top folder's; a mark is never 0");
        NotebookNames names = NotebookNames.renaming("Harbour", java.time.LocalDate.of(2026, 11, 6));
        check(NotebookNames.TAKEN.equals(names.refused("Favourites", n -> false)) && NotebookNames.TAKEN.equals(names.refused("favourites", n -> false)) && names.refused("Favourite pages", n -> false) == null,
                "no other notebook may be named Favourites, in any case");
        System.out.println("PASS: Favourites: a star copies its page, every layer, its template and surface, to the end of Favourites, both marked alike; the mark follows its page through a move, the file and the recovery; unstar deletes the copy, never Favourites' last page; a star is filled only while Favourites holds its copy; no other notebook may be named Favourites; no star writes no PFAV, and a notebook without one opens unstarred");
    }
}
