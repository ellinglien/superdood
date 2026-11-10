package io.github.mpdairy.monopaint;

import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The shelf's Search (Read Handwriting; docs/superpowers/specs/2026-11-09-read-handwriting-design.md, "Search on the
 * shelf"), for {@link NotebookScreens}: Search at the shelf bar's right while Read Handwriting is On; open, its field
 * ({@link LabUi.SearchField}, the Tools page library's) in the bar's place and the finds in the grid's, a page at a time
 * with the shelf's page buttons, "Nothing found" and "3 pages unread" under them; a find's tap opens its page
 * ({@link PaintActivity#openFound}). The field is made once an open, so the text stays through a new layout; the finds'
 * list and page buttons are filled again alone as answers come ({@link #fill}), so the field keeps its focus and the
 * keyboard while typing.
 */
final class ShelfSearch {
    /** A matching line: its notebook, its page in the file and that page's CRC, the page's number as the strip numbers it (its stack's), the guess that matched and its box (page px). */
    static final class Found {
        final String path; final int page; final long crc; final int number; final String words; final float[] box;
        Found(String path, int page, long crc, int number, String words, float[] box) { this.path = path; this.page = page; this.crc = crc; this.number = number; this.words = words; this.box = box; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Found)) return false;
            Found f = (Found) o;
            return path.equals(f.path) && page == f.page && crc == f.crc && number == f.number && words.equals(f.words) && Arrays.equals(box, f.box);
        }
        @Override public int hashCode() { return Objects.hash(path, page, crc, words) * 31 + Arrays.hashCode(box); }
    }

    private final PaintActivity app;
    private final Supplier<List<DocumentStore.Notebook>> shelf;
    private final Runnable rebuild;
    private boolean searching, shelfShows;
    /** The query the finds are for, the finds, the shelf's pages still unread, the page of finds shown. */
    private String query = "";
    private final List<Found> found = new ArrayList<>();
    private int unread, page;
    /** Each search asked counts one, so an answer overtaken by typing is dropped (read on the queue's thread too). */
    private volatile int asks;
    private LabUi.SearchField field;
    private LinearLayout list, buttons;
    private NotebookLayout layout;
    /** Whether the window pans under the keyboard now, and its own way before. */
    private boolean panned;
    private int before;

    ShelfSearch(PaintActivity app, Supplier<List<DocumentStore.Notebook>> shelf, Runnable rebuild) { this.app = app; this.shelf = shelf; this.rebuild = rebuild; }

    boolean open() { return searching; }

    /** The bar's place: the field while open; else {@code bar}, with Search at its right while Read Handwriting is On. */
    View bar(LabUi.PageBar bar, NotebookLayout l) {
        layout = l;
        if (searching) {
            if (field.row.getParent() != null) ((ViewGroup) field.row.getParent()).removeView(field.row);
            return field.row;
        }
        if (app.prefs.readHandwriting()) {
            LabButton find = new LabButton(app, R.drawable.ic_ph_magnifying_glass, ToolsLayout.SEARCH, false);
            find.setOnClickListener(v -> openSearch());
            bar.view.addView(find, LabUi.after(app, -2, NotebookLayout.BUTTON, NotebookLayout.BAR_GAP));
        }
        return bar.view;
    }
    /** While open, in the grid's place: the finds' list in {@code gridPlace}, the page buttons in {@code buttonsPlace}. */
    void results(LinearLayout column, LinearLayout.LayoutParams gridPlace, LinearLayout.LayoutParams buttonsPlace) {
        list = LabUi.column(app);
        column.addView(list, gridPlace);
        buttons = LabUi.row(app);
        column.addView(buttons, buttonsPlace);
        fill();
    }
    /** The shelf shown or not: the keyboard's pan follows. */
    void shelfShown(boolean shows) { shelfShows = shows; pan(); }
    /** Back on the shelf: the query asked again, its pages may have changed. */
    void again() { if (searching && !query.isEmpty()) find(query); }
    /** Read Handwriting went on or off: Off, an open search closes. */
    void changed() { if (!app.prefs.readHandwriting()) close(); }
    /** Find {@code f}'s boxes: its own first, then the other finds on its stack (its layers are pages of the same size). */
    List<float[]> boxes(Found f) {
        List<float[]> boxes = new ArrayList<>();
        boxes.add(f.box);
        for (Found o : found) if (!o.equals(f) && o.path.equals(f.path) && o.number == f.number) boxes.add(o.box);
        return boxes;
    }

    private void openSearch() {
        if (app.shelfBusy()) return;
        searching = true;
        pan();
        field = new LabUi.SearchField(app, ToolsLayout.CHIP, ToolsLayout.chipPad(layout.compact), ToolsActivity.SEARCH_MS, "", this::find, () -> { close(); rebuild.run(); });
        rebuild.run();
        field.open();
    }
    private void close() {
        if (field != null) field.clear();
        searching = false; field = null; query = ""; found.clear(); unread = 0; page = 0; asks++;
        pan();
    }
    /**
     * The window panned, not resized, under the keyboard while the shelf shows the search (a new size lays the shelf out
     * again, which would take the field's focus); its own way back otherwise. Untouched until a search opens.
     */
    private void pan() {
        boolean want = searching && shelfShows;
        if (want == panned) return;
        android.view.Window window = app.getWindow();
        WindowManager.LayoutParams a = new WindowManager.LayoutParams();
        a.copyFrom(window.getAttributes());
        // setSoftInputMode(0) wouldn't do: it leaves the mode as it is.
        if (want) { before = a.softInputMode; a.softInputMode = before & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN; }
        else a.softInputMode = before;
        window.setAttributes(a);
        panned = want;
    }
    /** The shelf's notebooks searched for {@code q} and their unread pages counted, in one pass: the list filled once (one e-ink update), and not again when nothing changed. */
    private void find(String q) {
        int ask = ++asks;
        app.handwriting.search(new ArrayList<>(shelf.get()), q, () -> ask != asks, (finds, n) -> {
            boolean same = q.equals(query) && finds.equals(found) && n == unread;
            if (!q.equals(query)) page = 0;
            query = q; found.clear(); found.addAll(finds); unread = n;
            if (!same) fill();
        });
    }
    /** This page of finds (the name and page, the guess under it), "Nothing found", the unread note, and the page buttons. */
    private void fill() {
        if (list == null || !searching || !shelfShows) return;
        int per = layout.foundPerPage(), pages = Math.max(1, (found.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        list.removeAllViews();
        LocalDate today = LocalDate.now();
        for (int i = page * per; i < Math.min(found.size(), (page + 1) * per); i++) {
            Found f = found.get(i);
            LinearLayout row = LabUi.column(app);
            TextView where = LabUi.text(app, DrawingNames.shown(DrawingFiles.name(f.path), today) + " · page " + f.number, TypeStyle.BODY);
            TextView words = LabUi.text(app, f.words, TypeStyle.CAPTION);
            words.setTextColor(Theme.GREY_800);
            for (TextView t : new TextView[]{where, words}) {
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(t, new LinearLayout.LayoutParams(-1, -2));
            }
            row.setContentDescription(where.getText() + ", " + f.words);
            row.setOnClickListener(v -> {
                if (app.shelfBusy()) return;
                field.keyboard(false);
                app.openFound(f);
            });
            list.addView(row, new LinearLayout.LayoutParams(-1, app.dp(layout.foundRow())));
        }
        if (found.isEmpty() && Transcript.words(query).length > 0) note(PictureImport.NOTHING_FOUND, TypeStyle.BODY);
        if (unread > 0) note(unread == 1 ? "1 page unread" : unread + " pages unread", TypeStyle.CAPTION);
        NotebookScreens.pageButtons(app, buttons, page, pages, "Newer results", "Older results", by -> { page += by; fill(); });
    }
    private void note(String words, TypeStyle type) {
        TextView note = LabUi.text(app, words, type);
        note.setTextColor(Theme.GREY_800);
        list.addView(note, new LinearLayout.LayoutParams(-1, -2));
    }
}
