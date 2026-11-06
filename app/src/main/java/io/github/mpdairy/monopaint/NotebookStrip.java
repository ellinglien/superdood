package io.github.mpdairy.monopaint;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Inside a notebook ({@code NotebookScreens}): the places it turns through and its strip's slots (the owner, 2026-11-03:
 * the covers on the list of pages, not "sticky on the left"): the front cover, the pages (each a stack of layers,
 * {@link PageStacks}), + while there is room ({@link #ADD}), the back cover, a window of them scrolling round the place
 * shown; the settings ({@link #CARD}) are the bar's Notebook Settings', before the front cover. What Delete Page does to
 * a stack; a shelf tile's words. Pure, so the browser app's notebooks (web/engine's WebBooks) take the same decisions;
 * checked on the desktop ({@code NotebookStripChecks}).
 */
final class NotebookStrip {
    /** The notebook's settings ({@link LibraryCard}) and the strip's + tile: places, not pages ({@link DrawingBook#NONE} is neither). */
    static final int CARD = Integer.MIN_VALUE + 2, ADD = Integer.MIN_VALUE + 1;

    /** How many pages {@code book} says it has: its stacks. */
    static int count(DrawingBook book) { return book.stacks().count(); }
    /** Whether {@code place} is a stack or a cover of {@code book}. */
    static boolean valid(DrawingBook book, int place) { return DrawingBook.isCover(place) || place >= 0 && place < count(book); }
    static int after(DrawingBook book, int place) { return book.stacks().after(place); }
    static int before(DrawingBook book, int place) { return book.stacks().before(place); }
    /** The place a turn {@code by} (−1 or 1) from {@code viewing} shows: the covers either end; from the card › to the front cover; {@link DrawingBook#NONE} past either end. */
    static int turn(DrawingBook book, int viewing, int by) {
        if (viewing == CARD) return by > 0 ? DrawingBook.FRONT : DrawingBook.NONE;
        return by < 0 ? before(book, viewing) : after(book, viewing);
    }
    /** The strip's slots: the front cover, the pages, + (while there is room), the back cover. */
    static int slots(DrawingBook book) { return count(book) + 2 + (book.count() < DrawingBook.MAX_PAGES ? 1 : 0); }
    /** Slot {@code slot}'s place: a cover, a stack, or {@link #ADD}. */
    static int place(DrawingBook book, int slot) {
        if (slot == 0) return DrawingBook.FRONT;
        if (slot <= count(book)) return slot - 1;
        return slot == slots(book) - 1 ? DrawingBook.BACK : ADD;
    }
    /** Place {@code place}'s slot; the card's is the front cover's (it is inside it). */
    static int slotOf(DrawingBook book, int place) { return place == CARD || place == DrawingBook.FRONT ? 0 : place == DrawingBook.BACK ? slots(book) - 1 : place + 1; }
    /** The first slot a strip of {@code visible} shows: a window round {@code viewing}'s, nothing pinned. */
    static int start(DrawingBook book, int viewing, int visible) { return NotebookLayout.window(slotOf(book, viewing), slots(book), visible); }
    /** Whether stack {@code place} shows through page {@code drawn} (the page drawn on as the marks show it; another stack). */
    static boolean ghosting(DrawingBook book, int drawn, int place) {
        PageStacks s = book.stacks();
        if (DrawingBook.isCover(place) || place == s.of(drawn)) return false;
        for (int[] g : book.tracing().ghosts(drawn)) if (s.of(g[0]) == place) return true;
        return false;
    }
    /** Whether every layer of stack {@code place} is Hidden (a cover never is). */
    static boolean hidden(DrawingBook book, int place) {
        if (DrawingBook.isCover(place) || place >= count(book)) return false;
        PageStacks s = book.stacks();
        for (int p = s.first(place); p <= s.last(place); p++) if (book.tracing().step(p) != TracingPaper.HIDDEN) return false;
        return true;
    }

    /** Whether Delete Page asks first for stack {@code place}: a layer of it has marks ({@link BlankPages#deleteAsks}). */
    static boolean deleteAsks(DrawingBook book, int place) {
        PageStacks s = book.stacks();
        boolean asks = false;
        for (int p = s.first(place); p <= s.last(place); p++) asks |= BlankPages.deleteAsks(book, p);
        return asks;
    }
    /**
     * Delete Page: stack {@code place}, every layer of it (never a cover, nor the last page). The page drawn on goes too:
     * the page after it is drawn on first (the one before, for the last).
     */
    static void deleteStack(DrawingBook book, int place) throws IOException {
        PageStacks s = book.stacks();
        if (DrawingBook.isCover(place) || s.count() <= 1 || place < 0 || place >= s.count()) return;
        int from = s.first(place), to = s.last(place), at = book.index();
        if (at >= from && at <= to) book.select(book.pageOf(to + 1 < book.count() ? place + 1 : place - 1));
        for (int p = to; p >= from; p--) book.deletePage(p);
    }


    /**
     * Favourites (the owner, 2026-11-06: "tap a star on the upper right of a page preview to save it to a special
     * favourites notebook"): the notebook a page's star copies it into, as it is then ({@link DrawingBook#copyStack}),
     * in the library's top folder; made at the first star, first on the shelf, never renamed or deleted, its own pages
     * without a star. A starred page and its copy carry the same mark ({@link DrawingBook#fave}).
     */
    static final String FAVOURITES = "Favourites";
    /** The star's screen-reader words, and the note once a page is copied. */
    static final String STAR = "Add to Favourites", UNSTAR = "Remove from Favourites", STARRED = "Copied to Favourites";
    /** Whether notebook {@code path} is Favourites. */
    static boolean favourites(String path) { return FAVOURITES.equals(path); }
    /** The marks Favourites' pages carry ({@link DrawingBook#pageFaves}, its file's; null: none). */
    static java.util.Set<Integer> marks(int[] pageFaves) {
        java.util.Set<Integer> marks = new java.util.HashSet<>();
        if (pageFaves != null) for (int f : pageFaves) if (f != 0) marks.add(f);
        return marks;
    }
    /** Whether a page marked {@code fave} shows its star filled: Favourites holds its copy ({@code held}, null while not read yet: the mark alone says). */
    static boolean filled(int fave, java.util.Set<Integer> held) { return fave != 0 && (held == null || held.contains(fave)); }
    /** A new star mark: any int but 0 (none). */
    static int newFave(java.util.Random dice) { int fave; do fave = dice.nextInt(); while (fave == 0); return fave; }
    /**
     * Unstar: every page of {@code favourites} marked {@code fave} deleted, the last first; false, deleting nothing, when
     * they are every page it has (the notebook goes instead).
     */
    static boolean unstar(DrawingBook favourites, int fave) throws IOException {
        PageStacks s = favourites.stacks();
        boolean others = false;
        for (int k = 0; k < s.count(); k++) others |= favourites.fave(s.first(k)) != fave;
        if (!others) return false;
        for (int k = s.count() - 1; k >= 0; k--) if (favourites.fave(favourites.stacks().first(k)) == fave) deleteStack(favourites, k);
        return true;
    }

    /** A shelf tile's "24 pages · Oct 17": its pages, and the day it last changed ({@code null}: today, unsaved). */
    static String about(int pages, LocalDate changed, LocalDate today) {
        String count = pages == 1 ? "1 page" : pages + " pages";
        LocalDate day = changed == null ? today : changed;
        String when = day.equals(today) ? "today" : day.equals(today.minusDays(1)) ? "yesterday" : DrawingNames.day(day, today);
        return count + " · " + when;
    }

    private NotebookStrip() {}
}
