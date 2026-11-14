package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.github.mpdairy.monopaint.NotebookStrip.*;

/**
 * The shelf of notebooks and the inside of one (docs/archive/superpowers/specs/2026-10-18-notebooks-design.md, "The screens"),
 * over the drawing screen, turned with it. The shelf: every notebook, the last changed first, its front cover each
 * (its design's pattern, tape and label, the cover picture and what is drawn on it, from the pictures read from its
 * file's end, {@link BookPictures}; {@link NotebookCovers}; a plain placeholder until it is made) with its name and
 * "24 pages · Oct 17", New notebook first, page buttons
 * when they don't all fit. Inside a notebook (the open one, {@link PaintActivity#book}; the owner, 2026-10-19:
 * "consolidate these to one bar"; Brush Lab's and the Tools page's {@link LabUi.PageBar} since 2026-11-03): ‹ Notebooks,
 * the notebook's name (a tap renames it) over the screen's job, Notebook Settings (its settings, {@link LibraryCard}),
 * Open (or a tap on the page, or on the strip's selected preview), Use on Cover (No Picture on the front cover) and
 * Delete Page (not on a cover); the page it shows as big as fits under it, big ‹ and › either side and a finger swipe
 * turning it; a strip of the front cover, its pages, + and the back cover, scrolling round it (the owner, 2026-11-03:
 * the covers in the list, not sticky; no settings tile);
 * and along the foot the drawing screen's page bar ({@link PageTurner}, without its Notebook button) to turn it. Every page is
 * shown by its picture ({@link DrawingBook#picture}), never by decoding it; a page without one (changed since, or from
 * an older file) has it made in the background, once. Sizes are {@link NotebookLayout}'s; nothing scrolls. A stack of
 * layers ({@link PageStacks}) is one page here: one tile, one number, its layers' pictures over each other, Delete Page
 * deleting it whole.
 */
final class NotebookScreens {
    /** The preference noting the library's old drawings are in notebooks ({@link NotebookMigration}). */
    static final String MIGRATED = "notebooks migrated";
    final FrameLayout view;
    private final PaintActivity app;
    private enum Mode { HIDDEN, SHELF, PAGES }
    /** Volatile: the worker reads it and {@link #viewing}, to pass by a page no longer shown ({@link #crisp}). */
    private volatile Mode mode = Mode.HIDDEN;
    private NotebookLayout layout;
    private int builtWidth, builtHeight;
    /** The shelf's notebooks, the open one among them as it is now; the shelf page shown. */
    private List<DocumentStore.Notebook> shelf = new ArrayList<>();
    private int shelfPage;
    /** While the old drawings become notebooks: what the shelf says instead, else null. */
    private String status;
    /** The page shown inside the notebook, a stack of layers ({@link PageStacks}, or a cover, or the card): not decoded, and not drawn on until Open. */
    private volatile int viewing;
    /** Pictures made and shown off the main thread, at the lowest priority. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(work -> {
        // Covers' patterns share their rows with the quiet helpers (Cores), at the lowest priority too.
        Thread thread = new Thread(() -> { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST); Cores.quiet(true); work.run(); }, "page pictures");
        thread.setDaemon(true);
        return thread;
    });
    /** Pictures as alpha masks (ink's darkness), by their bytes; about 290 KB each. */
    private final android.util.LruCache<byte[], Bitmap> bitmaps = new android.util.LruCache<byte[], Bitmap>(16 << 20) {
        @Override protected int sizeOf(byte[] key, Bitmap value) { return value.getByteCount(); }
    };
    /** Covers made for files that have no picture of their cover, by path and when it changed (only the latest kept). */
    private final HashMap<String, byte[]> made = new HashMap<>();
    private final HashSet<Object> pending = new HashSet<>(), failed = new HashSet<>();
    // Inside a notebook, the parts that change as it turns.
    private PageView page;
    private StripView strip;
    private LabButton settingsButton, open, cover, delete, previous, next;
    /** The name in the bar (hidden on the settings, which head their list with it). */
    private TextView title;
    /** The settings list ({@link LibraryCard}), in the page's place while {@link #viewing} is {@link #CARD}. */
    private LinearLayout settings;
    /** Round {@link #settings}: it scrolls only where its rows can't fit (the Nomad turned). */
    private android.widget.ScrollView settingsScroll;
    /** What the list shows now, so it is made again only when that changes (an e-ink update each time). */
    private Object settingsShown;
    /** A card row's own screen while it shows ({@link CardSelector}), else null. */
    private CardSelector selector;
    /** The page bar along the foot, the drawing screen's own ({@link PageTurner}), without its Notebook button. */
    private PageTurner turner;
    /**
     * Page Settings ({@link LibraryCard#pageRows}) over the screen while it shows, else null; the page (a stack) it is for
     * ({@link DrawingBook#NONE}: none), whether the drawing screen opened it (Back goes back there) and what it shows.
     */
    private LinearLayout pageList;
    private int pagePlace = DrawingBook.NONE;
    private boolean pageFromDrawing;
    private Object pageShown;
    /** The shelf's Search (Read Handwriting). */
    private final ShelfSearch search;
    /** A find's boxes over page {@code markPage} (a layer: shown over its stack), until the next tap on the page. */
    private int markPage = DrawingBook.NONE;
    private final List<float[]> marks = new ArrayList<>();
    private final Paint markInk = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** The last page whose paper tone was read from its bytes, and the tone (a page not decoded is read once). */
    private PackedPage tonePacked;
    private int toneRead;

    NotebookScreens(PaintActivity app) {
        this.app = app;
        search = new ShelfSearch(app, () -> shelf, this::build);
        view = new FrameLayout(app);
        view.setBackgroundColor(Theme.PAPER);
        view.setVisibility(View.GONE);
        view.setClickable(true);
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (mode != Mode.HIDDEN && (r - l != builtWidth || b - t != builtHeight)) view.post(this::build);
        });
    }

    boolean shown() { return mode != Mode.HIDDEN; }
    boolean onShelf() { return mode == Mode.SHELF; }
    boolean onPages() { return mode == Mode.PAGES; }

    /** Shows the shelf, and reads it again from the library. */
    void showShelf() {
        pagePlace = DrawingBook.NONE;
        unmark();
        cover(Mode.SHELF);
        build();
        load();
        search.again();
    }
    /** Shows the open notebook at page {@code at} (a layer: its stack). */
    void showPages(int at) {
        if (app.book == null) return;
        pagePlace = DrawingBook.NONE;
        viewing = app.book.valid(at) ? app.book.stacks().of(at) : Math.max(0, Math.min(at, count(app.book) - 1));
        cover(Mode.PAGES);
        build();
        readFavourites();
    }
    /** Shows the open notebook's settings list. */
    void showCard() {
        if (app.book == null) return;
        pagePlace = DrawingBook.NONE;
        viewing = CARD;
        cover(Mode.PAGES);
        build();
        readFavourites();
    }
    /** As {@link #showCard()}, then card screen {@code screen} over it (the Tools page's Notebook Settings). */
    void showCard(CardSheet.Screen screen) {
        showCard();
        if (app.book != null && screen != null) view.post(() -> { if (viewing == CARD && selector == null) choose(screen, 0); });
    }
    /** How long after it opens the notebook screen gets its clean window: past a closing menu's last frame on the panel. */
    static final long MENU_GONE_MS = 400;
    /** Covers the drawing screen: the canvas stops showing itself directly ({@link PaintActivity#canvasCovered}). */
    private void cover(Mode next) {
        boolean leaving = mode == Mode.HIDDEN;
        if (leaving) { app.dismissPanels(); if (app.pad != null) app.pad.suspend(); }
        if (next != Mode.PAGES) crisps.evictAll();
        mode = next;
        search.shelfShown(next == Mode.SHELF);
        view.setVisibility(View.VISIBLE);
        // The page and the column are under partial grey: ink sent straight to the panel stayed over paper here (the owner, 2026-10-30). Their partial grey comes back on return (DrawingPad.returned).
        // Once a menu that opened it has left the panel too (the firmware shows a window's change about 350 ms late): cleaned
        // at once, its patch ghosted on, the menu in the top corner (the owner, 2026-10-30). With the firmware's flash: the
        // clean update alone still left it (the owner, 2026-10-31), as it left big ghosts on the page (DrawingPad.autoRefresh).
        // Automatic Refresh off (System Settings): no redraw at all.
        boolean afterSplash = app.takeSplashGhost();
        if (leaving || afterSplash) view.postDelayed(() -> Ui.flashWindow(app.previewFrame.getRootView(), afterSplash ? "after the splash" : "notebook screen opened", () -> { }, afterSplash || SystemSettings.flashes(false, app.prefs.autoRefresh()), true), MENU_GONE_MS);
        app.syncToolbar();
    }
    /** Back to drawing. */
    void hide() {
        if (mode == Mode.HIDDEN) return;
        mode = Mode.HIDDEN;
        search.shelfShown(false);
        view.setVisibility(View.GONE);
        view.removeAllViews(); builtWidth = 0; selector = null;
        pageList = null; pagePlace = DrawingBook.NONE; pageShown = null;
        unmark();
        crisps.evictAll();
        // The shelf's cover sheets (up to 40 MB) go: drawing wants the room. A cover's are made again ahead when near one.
        CoverPattern.forget(); CoverTexture.forget();
        app.syncToolbar();
        Ui.invalidateTree(app.root);
        if (app.pad != null) { app.pad.returned(); app.pad.invalidate(); app.pad.post(app.pad::connectDisplay); }
    }
    /** Android's back: from a settings screen, to the list; inside a notebook, to the shelf; on the shelf, nothing to go back to (false). */
    boolean back() {
        if (selector != null) { selector.close(); return true; }
        if (pageList != null) { closePageSettings(); return true; }
        if (mode == Mode.PAGES) { app.leaveNotebook(); return true; }
        return false;
    }

    /** While the old drawings are gathered: the shelf says so, the count of pages done. */
    void migrating(int done, int total) {
        status = total <= 0 ? "Making notebooks" : "Making notebooks: " + done + " of " + total;
        if (mode == Mode.SHELF) build();
    }
    void migrated() { status = null; }

    /** The open notebook's pages changed (turned, added, deleted, its cover): inside it, what it shows follows. */
    void pagesChanged() {
        if (mode != Mode.PAGES || app.book == null) return;
        if (viewing != CARD && !valid(app.book, viewing)) viewing = Math.max(0, Math.min(viewing, count(app.book) - 1));
        updatePages();
    }
    /** The page being drawn on is about to be left: its picture made from it now, while it is still decoded. */
    void leavingPage() { if (app.book != null) picture(app.book, app.book.index()); }

    // Reading the shelf

    private void load() {
        app.store.notebooks((found, error) -> app.runOnUiThread(() -> {
            if (error != null) { app.message("Couldn't read the notebooks: " + error.getMessage()); return; }
            List<DocumentStore.Notebook> read = merged(found);
            // Unchanged since the shelf was built from it (a return to it): nothing to build again, nor show again.
            boolean same = same(read, shelf);
            shelf = read;
            if (mode == Mode.SHELF && !same) build();
        }));
    }
    /** Whether the shelf shows {@code a} as it shows {@code b}: the same notebooks in the same order, each as it was. */
    static boolean same(List<DocumentStore.Notebook> a, List<DocumentStore.Notebook> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            DocumentStore.Notebook x = a.get(i), y = b.get(i);
            if (!x.path.equals(y.path) || x.modified != y.modified || x.pages != y.pages || x.cover != y.cover || x.unreadable != y.unreadable || x.width != y.width
                    || x.height != y.height || x.frontDrawn != y.frontDrawn || x.design != y.design || !x.options.equals(y.options)
                    || NotebookCovers.print(x.picture) != NotebookCovers.print(y.picture) || NotebookCovers.print(x.front) != NotebookCovers.print(y.front)) return false;
        }
        return true;
    }
    /** The notebooks found, the open one as it is now: its pages and cover, first (after Favourites) while it has unsaved changes; one never saved and blank isn't shown. */
    private List<DocumentStore.Notebook> merged(List<DocumentStore.Notebook> found) {
        List<DocumentStore.Notebook> all = new ArrayList<>();
        DrawingBook book = app.book;
        String open = app.drawingName;
        boolean saved = false;
        for (DocumentStore.Notebook n : found) if (n.path.equals(open)) saved = true;
        if (book != null && !open.isEmpty() && (saved || book.unsaved())) {
            long when = Long.MAX_VALUE;
            // Its count is the open notebook's: an old notebook's blank page at its end goes on opening, unsaved (BlankPages.opened).
            for (DocumentStore.Notebook n : found) if (n.path.equals(open) && !book.unsaved()) when = n.modified;
            all.add(new DocumentStore.Notebook(open, when, count(book), book.cover(), null, false, book.width, book.height, book.coverDrawn(DrawingBook.FRONT), null, book.design(), book.options()));
        }
        for (DocumentStore.Notebook n : found) if (!n.path.equals(open)) all.add(n);
        // Favourites first (the owner, 2026-11-06), then the last changed first.
        all.sort((a, b) -> NotebookStrip.favourites(a.path) != NotebookStrip.favourites(b.path) ? NotebookStrip.favourites(a.path) ? -1 : 1 : Long.compare(b.modified, a.modified));
        return all;
    }

    // Building

    private int dp(float v) { return app.dp(v); }

    private void build() {
        if (mode == Mode.HIDDEN) return;
        int w = view.getWidth(), h = view.getHeight();
        if (w <= 0 || h <= 0) { view.post(this::build); return; }
        builtWidth = w; builtHeight = h;
        // A card's screen open as the screen turns is closed (a slider's stop set) and opened again in the new size.
        CardSelector was = selector;
        CardSheet.Screen reopen = was != null && was.shown() ? was.screen : null;
        if (was != null) was.close();
        double aspect = mode == Mode.PAGES && app.book != null ? app.book.height / (double) app.book.width : pageAspect();
        layout = new NotebookLayout(Ui.dpOf(app, w), Ui.dpOf(app, h), aspect);
        view.removeAllViews();
        turner = null; page = null; strip = null; selector = null; pageList = null; pageShown = null;
        view.addView(mode == Mode.SHELF ? shelf() : pages(), new FrameLayout.LayoutParams(-1, -1));
        // Page Settings open as the screen turns is made again in the new size.
        if (mode == Mode.PAGES) fillPageSettings();
        if (reopen != null && mode == Mode.PAGES) choose(reopen, was.page);
    }
    /** A page's height over its width on this screen: the drawing screen's page beside its bar. */
    private double pageAspect() {
        DrawLayout bar = app.inkWashBar.layout();
        int pw = Math.min(bar.pageWidth(), bar.pageHeight()), ph = Math.max(bar.pageWidth(), bar.pageHeight());
        return ph / (double) Math.max(1, pw);
    }
    private TextView text(String words, TypeStyle type) {
        TextView text = Theme.style(new TextView(app), type);
        text.setText(words);
        return text;
    }
    private LinearLayout.LayoutParams sized(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }

    /** The shelf: the bar, the grid of this page's tiles, the page buttons. */
    private View shelf() {
        NotebookLayout l = layout;
        LinearLayout column = LabUi.column(app);
        column.setPadding(dp(l.margin), dp(l.margin), dp(l.margin), dp(l.margin));
        // The page bar of Brush Lab and the Tools page (2026-11-03), no Back: the shelf is the first screen.
        // No job line under Notebooks since 2026-11-06 (the owner: "it's not necessary").
        LabUi.PageBar shelfBar = new LabUi.PageBar(app, 0, null, NotebookLayout.BACK, "");
        shelfBar.line.setVisibility(View.GONE);
        // Search at the bar's right, only while Read Handwriting is On; while open, its field in the bar's place.
        View bar = search.bar(shelfBar, l);
        // System Settings from the shelf too (the owner, 2026-11-10: "a way to get to app settings from the notebook explorer view").
        if (!search.open()) {
            LabButton gear = new LabButton(app, R.drawable.ic_ph_gear, null, false);
            gear.setContentDescription(SystemSettings.TITLE);
            gear.setOnClickListener(v -> { if (!app.shelfBusy()) app.systemSettings(gear); });
            shelfBar.view.addView(gear, LabUi.after(app, -2, NotebookLayout.BUTTON, NotebookLayout.BAR_GAP));
        }
        column.addView(bar, sized(-1, NotebookLayout.BAR));
        LinearLayout.LayoutParams gridPlace = sized(-1, 0); gridPlace.weight = 1; gridPlace.topMargin = dp(l.gap);
        LinearLayout.LayoutParams buttonsPlace = sized(-1, NotebookLayout.BAR); buttonsPlace.topMargin = dp(l.gap);
        if (status != null) {
            TextView words = text(status, TypeStyle.BODY);
            words.setGravity(Gravity.CENTER);
            column.addView(words, gridPlace);
            return column;
        }
        // The finds in the grid's place, a page at a time with the shelf's page buttons.
        if (search.open()) {
            search.results(column, gridPlace, buttonsPlace);
            return column;
        }
        int pages = l.shelfPages(shelf.size());
        shelfPage = Math.max(0, Math.min(shelfPage, pages - 1));
        int[] range = l.shelfPage(shelfPage, shelf.size());
        List<View> tiles = new ArrayList<>();
        if (shelfPage == 0) tiles.add(tile(null));
        for (int i = range[0]; i < range[0] + range[1]; i++) tiles.add(tile(shelf.get(i)));
        LinearLayout grid = LabUi.column(app);
        for (int r = 0, k = 0; r < l.rows; r++) {
            LinearLayout row = LabUi.row(app);
            for (int c = 0; c < l.columns; c++, k++) {
                LinearLayout.LayoutParams place = sized(l.tileWidth, l.tileHeight);
                if (c > 0) place.leftMargin = dp(l.columnGap);
                row.addView(k < tiles.size() ? tiles.get(k) : new View(app), place);
            }
            LinearLayout.LayoutParams rowPlace = sized(-1, l.tileHeight);
            if (r > 0) rowPlace.topMargin = dp(l.rowGap);
            grid.addView(row, rowPlace);
        }
        column.addView(grid, gridPlace);
        LinearLayout buttons = LabUi.row(app);
        pageButtons(app, buttons, shelfPage, pages, "Newer notebooks", "Older notebooks", by -> { shelfPage += by; build(); });
        column.addView(buttons, buttonsPlace);
        return column;
    }
    /** {@code buttons} as the shelf's page buttons: ‹, "2 / 5" and › (said {@code newer} and {@code older}), a tap {@code turn}ing −1 or 1; hidden for one page. */
    static void pageButtons(PaintActivity app, LinearLayout buttons, int at, int pages, String newer, String older, java.util.function.IntConsumer turn) {
        buttons.removeAllViews();
        buttons.setGravity(Gravity.CENTER);
        LabButton back = new LabButton(app, R.drawable.ic_ph_caret_left, null, false), on = new LabButton(app, R.drawable.ic_ph_caret_right, null, false);
        back.setContentDescription(newer); on.setContentDescription(older);
        back.setEnabled(at > 0); on.setEnabled(at < pages - 1);
        back.setOnClickListener(v -> turn.accept(-1));
        on.setOnClickListener(v -> turn.accept(1));
        TextView number = LabUi.text(app, (at + 1) + " / " + pages, TypeStyle.LABEL);
        number.setGravity(Gravity.CENTER);
        int button = app.dp(NotebookLayout.BUTTON);
        buttons.addView(back, new LinearLayout.LayoutParams(button, button));
        buttons.addView(number, new LinearLayout.LayoutParams(app.dp(96), button));
        buttons.addView(on, new LinearLayout.LayoutParams(button, button));
        buttons.setVisibility(pages > 1 ? View.VISIBLE : View.INVISIBLE);
    }

    /** Find {@code f}'s page shown inside its notebook (the open one), its lines boxed: the page with the find's CRC (pages may have moved since the search), else its place unboxed. */
    void showFound(ShelfSearch.Found f) {
        if (app.book == null) return;
        // Its own place first: a duplicated page shares its CRC with the one it was copied from.
        PackedPage there = f.page >= 0 && f.page < app.book.count() ? app.book.packed(f.page) : null;
        int at = there != null && there.crc == f.crc ? f.page : DrawingBook.NONE;
        for (int i = 0; i < app.book.count() && at == DrawingBook.NONE; i++) { PackedPage p = app.book.packed(i); if (p != null && p.crc == f.crc) at = i; }
        if (at != DrawingBook.NONE) { markPage = at; marks.clear(); marks.addAll(search.boxes(f)); }
        showPages(at != DrawingBook.NONE ? at : f.page);
    }
    private void unmark() { marks.clear(); markPage = DrawingBook.NONE; }

    /** A tile: a notebook's cover, name and count; null: New notebook. */
    private View tile(DocumentStore.Notebook notebook) {
        NotebookLayout l = layout;
        LinearLayout tile = LabUi.column(app);
        CoverView face = new CoverView(notebook);
        tile.addView(face, sized(l.tileWidth, l.coverHeight));
        LocalDate today = LocalDate.now();
        String name = notebook == null ? "New Notebook" : DrawingNames.shown(DrawingFiles.name(notebook.path), today);
        TextView words = text(name, TypeStyle.BODY);
        words.setMaxLines(l.nameLines);
        words.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams under = sized(-1, -2); under.topMargin = dp(NotebookLayout.UNDER_COVER);
        tile.addView(words, under);
        if (notebook != null) {
            TextView about = text(about(notebook, today), TypeStyle.CAPTION);
            about.setTextColor(Theme.GREY_800); about.setMaxLines(NotebookLayout.COUNT_LINES);
            tile.addView(about, sized(-1, -2));
        }
        tile.setContentDescription(notebook == null ? "New Notebook" : name + ", " + about(notebook, today));
        tile.setOnClickListener(v -> {
            if (app.shelfBusy()) return;
            if (notebook == null) app.newNotebook(); else app.openNotebook(notebook.path);
        });
        // A long press: Rename, Settings (its library card) and Delete.
        if (notebook != null) tile.setOnLongClickListener(v -> { if (!app.shelfBusy()) app.notebookMenu(face, notebook); return true; });
        return tile;
    }
    /** "24 pages · Oct 17": its pages, and the day it last changed ({@link NotebookStrip#about}). */
    static String about(DocumentStore.Notebook notebook, LocalDate today) {
        if (notebook.unreadable) return "Can't be read";
        return NotebookStrip.about(notebook.pages, notebook.modified == Long.MAX_VALUE ? null : Instant.ofEpochMilli(notebook.modified).atZone(ZoneId.systemDefault()).toLocalDate(), today);
    }

    /**
     * Inside the open notebook: the bar (‹ Notebooks, the name over its job, Notebook Settings, Open, Use on Cover, Delete Page; the last two their icons
     * alone where its words don't fit, {@link NotebookLayout#labelled}), the page, the strip, and the page bar along the
     * foot, the margin round all but it.
     */
    private View pages() {
        NotebookLayout l = layout;
        LinearLayout screen = LabUi.column(app);
        LinearLayout column = LabUi.column(app);
        column.setPadding(dp(l.margin), dp(l.pagesTop), dp(l.margin), 0);
        // The page bar of Brush Lab and the Tools page (2026-11-03: "make it consistent across the app"): ‹ Notebooks, the
        // name centred, the actions at the right.
        LabUi.PageBar pageBar = new LabUi.PageBar(app, 0, NotebookLayout.BACK, DrawingNames.shown(DrawingFiles.name(app.drawingName), LocalDate.now()), "");
        // No "Tap a page to open it." under the name since 2026-11-06 (the owner: "it's not necessary").
        pageBar.line.setVisibility(View.GONE);
        LinearLayout bar = pageBar.view;
        pageBar.back.setOnClickListener(v -> { if (!app.busy()) app.leaveNotebook(); });
        // The name in its lines at most (heading size, down to the body size a long name was measured at, {@link
        // NotebookLayout#fits}, so it is never cut); a tap renames it.
        title = pageBar.title;
        title.setSingleLine(false);
        title.setMaxLines(NotebookLayout.TITLE_LINES);
        title.setAutoSizeTextTypeUniformWithConfiguration(Math.round(TypeStyle.BODY.sp), Math.round(TypeStyle.HEADING.sp), 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        if (!NotebookStrip.favourites(app.drawingName)) {
            title.setContentDescription(title.getText() + ". Rename notebook");
            title.setOnClickListener(v -> { if (!app.busy()) app.renameNotebook(); });
        }
        // Notebook Settings, left of Open (the owner, 2026-11-03), where the strip's settings tile was: the list in the page's place.
        // Its word, Settings, since 2026-11-06 (the owner: "instead of an icon only ... we should write SETTINGS"), said Notebook Settings.
        settingsButton = action(NotebookLayout.SETTINGS, R.drawable.ic_ph_page_star, l.labelled);
        settingsButton.setContentDescription(DrawLayout.NOTEBOOK_SETTINGS);
        // In its own case, not the label's caps (the owner: "Settings in normal case is fine").
        settingsButton.setAllCaps(false);
        settingsButton.setOnClickListener(v -> { if (!app.busy() && app.book != null) { viewing = CARD; updatePages(); } });
        open = new LabButton(app, 0, NotebookLayout.OPEN, true);
        open.setOnClickListener(v -> draw());
        // On a page: Use on Cover (greyed when it is the cover picture); on the front cover: No Picture.
        cover = action(NotebookCovers.USE_BUTTON, R.drawable.ic_cover_picture, l.labelled);
        // A stack's picture on the cover is its bottom page's.
        cover.setOnClickListener(v -> { if (!app.busy() && app.book != null && viewing != DrawingBook.BACK && viewing != CARD) app.setCoverPicture(viewing == DrawingBook.FRONT ? -1 : app.book.stacks().first(viewing)); });
        delete = action(NotebookLayout.DELETE, R.drawable.ic_ph_trash, l.labelled);
        delete.setOnClickListener(v -> { if (!app.busy()) app.deleteStack(viewing); });
        bar.addView(settingsButton, after(NotebookLayout.settingsWidth(l.labelled)));
        bar.addView(open, after(NotebookLayout.actionWidth(NotebookLayout.OPEN, true)));
        bar.addView(cover, after(NotebookLayout.coverWidth(l.labelled)));
        bar.addView(delete, after(NotebookLayout.actionWidth(NotebookLayout.DELETE, l.labelled)));
        column.addView(bar, sized(-1, NotebookLayout.PAGES_BAR));
        LinearLayout spread = LabUi.row(app);
        spread.setGravity(Gravity.CENTER);
        page = new PageView();
        // ‹ and › either side of the page (the owner, 2026-11-03), big for a finger ("the page turn carets should be
        // bigger"): the page's height and CARET wide, their carets CARET_ICON; greyed at the covers.
        previous = new LabButton(app, R.drawable.ic_ph_caret_left, null, false); next = new LabButton(app, R.drawable.ic_ph_caret_right, null, false);
        previous.setIcon(R.drawable.ic_ph_caret_left, NotebookLayout.CARET_ICON); next.setIcon(R.drawable.ic_ph_caret_right, NotebookLayout.CARET_ICON);
        previous.setContentDescription("Previous page"); next.setContentDescription("Next page");
        previous.setOnClickListener(v -> turn(-1)); next.setOnClickListener(v -> turn(1));
        spread.addView(previous, sized(NotebookLayout.CARET, l.pageHeight));
        spread.addView(page, sized(l.pageWidth, l.pageHeight));
        spread.addView(next, sized(NotebookLayout.CARET, l.pageHeight));
        settings = LabUi.column(app); settingsShown = null;
        settingsScroll = new android.widget.ScrollView(app);
        settingsScroll.addView(settings);
        spread.addView(settingsScroll, sized(Math.round(LibraryCard.Layout.width(l.width - 2 * l.margin)), l.spreadHeight));
        LinearLayout.LayoutParams spreadPlace = sized(-1, l.spreadHeight); spreadPlace.topMargin = dp(l.gap);
        column.addView(spread, spreadPlace);
        strip = new StripView();
        LinearLayout.LayoutParams stripPlace = sized(-1, l.thumbHeight + NotebookLayout.TAB + NotebookLayout.TAB_GAP); stripPlace.topMargin = dp(l.gap);
        column.addView(strip, stripPlace);
        LinearLayout.LayoutParams columnPlace = sized(-1, 0); columnPlace.weight = 1;
        screen.addView(column, columnPlace);
        // The drawing screen's page bar: ‹, the page and its count, ›.
        turner = new PageTurner(app, new PageTurner.Host() {
            @Override public void previous() { turn(-1); }
            @Override public void next() { turn(1); }
            @Override public void first() { go(0); }
            @Override public void last() { if (app.book != null) go(app.book.stacks().count() - 1); }
        }, false);
        turner.place(l.pageButton, false);
        LinearLayout.LayoutParams barPlace = sized(-1, l.pageBar); barPlace.topMargin = dp(l.gap);
        screen.addView(turner.view, barPlace);
        updatePages();
        return screen;
    }
    /** A bar's action: {@code label} in a 2 dp outline, or, not {@code labelled}, {@code icon} alone, {@code label} said to a screen reader. */
    private LabButton action(String label, int icon, boolean labelled) {
        LabButton button = new LabButton(app, labelled ? 0 : icon, labelled ? label : null, false);
        button.setContentDescription(label);
        button.setPadding(dp(labelled ? NotebookLayout.ACTION_PAD : 0), 0, dp(labelled ? NotebookLayout.ACTION_PAD : 0), 0);
        return button;
    }
    /** A place in the bar {@code width} dp wide, {@link NotebookLayout#BAR_GAP} after what comes before. */
    private LinearLayout.LayoutParams after(int width) {
        LinearLayout.LayoutParams place = sized(width, NotebookLayout.BUTTON);
        place.leftMargin = dp(NotebookLayout.BAR_GAP);
        return place;
    }
    /**
     * What inside the notebook says of the page shown: the page bar (the page and its count, a cover's word; ‹ and ›), the
     * page's ‹ and › (greyed at the covers), the bar's actions (none on the settings; the cover picture's Use on Cover on a
     * page, No Picture on the front cover, none on the back; Delete Page never on a cover), the page, the strip.
     */
    private void updatePages() {
        DrawingBook book = app.book;
        if (turner == null || book == null) return;
        PageStacks stacks = book.stacks();
        int n = stacks.count();
        boolean card = viewing == CARD, front = viewing == DrawingBook.FRONT, back = viewing == DrawingBook.BACK;
        // The card is inside the front cover: the page bar says Cover.
        turner.show(card ? DrawingBook.FRONT : viewing, n);
        // On the settings the list takes the page's place, headed by the name; the bar keeps ‹ Notebooks.
        page.setVisibility(card ? View.GONE : View.VISIBLE);
        previous.setVisibility(card ? View.GONE : View.VISIBLE); next.setVisibility(card ? View.GONE : View.VISIBLE);
        previous.setEnabled(NotebookStrip.turn(book, viewing, -1) != DrawingBook.NONE); next.setEnabled(NotebookStrip.turn(book, viewing, 1) != DrawingBook.NONE);
        settingsScroll.setVisibility(card ? View.VISIBLE : View.GONE);
        ((View) title.getParent()).setVisibility(card ? View.INVISIBLE : View.VISIBLE);
        if (card) fillSettings(book);
        for (View action : new View[]{settingsButton, open}) action.setVisibility(card ? View.GONE : View.VISIBLE);
        String coverWords = front ? NotebookCovers.NO_PICTURE_BUTTON : NotebookCovers.USE_BUTTON;
        if (layout.labelled) cover.setText(coverWords); else cover.setIcon(front ? R.drawable.ic_cover_picture_off : R.drawable.ic_cover_picture, 24);
        cover.setContentDescription(coverWords);
        cover.setVisibility(card ? View.GONE : back ? View.INVISIBLE : View.VISIBLE);
        cover.setEnabled(front ? book.cover() >= 0 : book.cover() < 0 || stacks.of(book.cover()) != viewing);
        delete.setVisibility(card ? View.GONE : DrawingBook.isCover(viewing) ? View.INVISIBLE : View.VISIBLE);
        delete.setEnabled(n > 1);
        page.invalidate(); strip.invalidate();
        if (pageList != null) fillPageSettings();
        // The neighbours' pictures ready for the next turn.
        for (int i = Math.max(0, viewing - 2); i <= Math.min(n - 1, viewing + 2); i++) for (int p = stacks.first(i); p <= stacks.last(i); p++) pagePicture(book, p);
    }
    /** To page {@code place} (|‹ and ›|). */
    private void go(int place) {
        if (app.book == null || place < 0 || viewing == place) return;
        viewing = place; updatePages();
    }
    private void turn(int by) {
        if (app.book == null) return;
        int to = NotebookStrip.turn(app.book, viewing, by);
        if (to == DrawingBook.NONE) return;
        viewing = to; updatePages();
    }
    private void draw() {
        if (app.busy() || app.book == null || viewing == CARD) return;
        int at = viewing;
        hide();
        app.drawOnStack(at);
    }
    /** The strip's slot {@code slot} tapped: a page or a cover shown (the one shown opened), or + adding a page. */
    private void tapStrip(int slot) {
        DrawingBook book = app.book;
        if (book == null || app.busy() || slot < 0 || slot >= slots(book)) return;
        int place = place(book, slot);
        if (place == ADD) { app.addNotebookPage(); return; }
        if (place == viewing) { draw(); return; }
        viewing = place; updatePages();
    }
    /** The strip's slot {@code slot} held: the front cover opens Cover Design (the owner, 2026-10-21), a page its Page Settings. */
    private void holdStrip(int slot) {
        DrawingBook book = app.book;
        if (book == null || app.busy() || slot < 0 || slot >= slots(book)) return;
        int place = place(book, slot);
        if (place == DrawingBook.FRONT) choose(CardSheet.Screen.COVER, 0);
        else if (place >= 0 && valid(book, place)) showPageSettings(place, false);
    }
    /** The open notebook was renamed: what shows its name follows. */
    void renamed() { if (mode == Mode.PAGES) build(); else if (mode == Mode.SHELF) load(); }
    /** The page shown, after a page was added at the end: the new one. */
    void showAdded() { if (app.book != null) { viewing = count(app.book) - 1; updatePages(); } }

    // Pictures

    /** Page {@code i}'s picture as a mask, or null while it is made or read (the views are drawn again when it arrives). */
    private Bitmap pagePicture(DrawingBook book, int i) {
        byte[] bytes = book.picture(i);
        if (bytes != null) return bitmap(bytes);
        picture(book, i);
        return null;
    }
    /** Makes page {@code i}'s picture in the background, if it has none: from the decoded page, or its packed bytes. */
    private void picture(DrawingBook book, int i) {
        if (!book.valid(i) || book.picture(i) != null) return;
        DrawingBook.PictureKey key = book.pictureKey(i);
        Object token = key.page != null ? key.page + ":" + key.revision : key.packed;
        if (token == null || failed.contains(token) || !pending.add(token)) return;
        ToneDocument decoded = book.cachedPage(i);
        ToneDocument.Snapshot snapshot = decoded != null ? decoded.layerSnapshot() : null;
        PackedPage packed = snapshot == null ? book.packed(i) : null;
        if (snapshot == null && packed == null) { pending.remove(token); return; }
        worker.execute(() -> {
            byte[] picture = null;
            try {
                synchronized (DocumentCodec.ONE_PAGE) {
                    ToneDocument.Snapshot page = snapshot != null ? snapshot : DocumentCodec.read(new java.io.ByteArrayInputStream(packed.bytes())).layerSnapshot();
                    // A cover's of its marks alone, to lay on its paper when shown.
                    picture = (DrawingBook.isCover(i) ? PagePicture.marks(page, WordsMode.exportTiles()) : PagePicture.of(page, WordsMode.exportTiles())).encode();
                }
            } catch (Exception | OutOfMemoryError e) { android.util.Log.w(ProbeActivity.TAG, "Could not make page " + (i + 1) + "'s picture", e); }
            byte[] made = picture;
            app.runOnUiThread(() -> {
                pending.remove(token);
                if (made == null) failed.add(token);
                else if (app.book == book) book.offerPicture(key, made);
                redraw();
            });
        });
    }
    /**
     * The guides of page {@code i}'s template as a mask the size of its pages' pictures, drawn under them (they are a
     * page's paper, not in its picture); null for Blank. Made when first wanted, a few ms, then kept.
     */
    private Bitmap guides(DrawingBook book, int i) {
        NotebookTemplate template = book.template(i);
        int[] size = PagePicture.size(book.width, book.height);
        String key = "guides " + template.key + " " + size[0] + "x" + size[1];
        Bitmap ready = covers.get(key);
        if (ready != null) return ready;
        byte[] tones = template.shared(size[0], size[1]);
        if (tones != null) covers.put(key, ready = mask(tones, size[0], size[1]));
        return ready;
    }
    /** {@code picture}'s mask, or null while it is decoded in the background. */
    private Bitmap bitmap(byte[] picture) {
        Bitmap ready = bitmaps.get(picture);
        if (ready != null || failed.contains(picture) || !pending.add(picture)) return ready;
        worker.execute(() -> {
            Bitmap mask = null;
            try {
                mask = mask(PagePicture.decode(picture));
            } catch (Exception | OutOfMemoryError e) { android.util.Log.w(ProbeActivity.TAG, "Could not show a page picture", e); }
            Bitmap shown = mask;
            app.runOnUiThread(() -> {
                pending.remove(picture);
                if (shown == null) failed.add(picture); else bitmaps.put(picture, shown);
                redraw();
            });
        });
        return null;
    }
    /**
     * A shelf notebook's front cover in {@code box} (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md,
     * "Covers"): the open one's from it; a file's from what its end keeps (the cover picture's page's picture, the front
     * cover's picture of its marks), either made from its pages when the file has none for it; null while it is made.
     */
    private Bitmap coverPicture(DocumentStore.Notebook notebook, RectF box) {
        DrawingBook book = app.book;
        if (book != null && notebook.path.equals(app.drawingName)) return coverPicture(book, DrawingBook.FRONT, box);
        if (notebook.unreadable) return null;
        byte[] pictured = notebook.cover < 0 ? null : notebook.picture != null ? notebook.picture : made(notebook, notebook.cover);
        byte[] marks = !notebook.frontDrawn ? null : notebook.front != null ? notebook.front : made(notebook, DrawingBook.FRONT);
        if (notebook.cover >= 0 && pictured == null || notebook.frontDrawn && marks == null) return null;
        return composed(true, notebook.design, notebook.path, notebook.width, notebook.height, pictured, marks, box);
    }
    /** A shelf notebook's page {@code page}'s picture (its front cover's, of its marks: {@link DrawingBook#FRONT}) made from its file, or null while it is made. */
    private byte[] made(DocumentStore.Notebook notebook, int page) {
        String key = notebook.path + "@" + notebook.modified + "#" + page;
        byte[] ready = made.get(key);
        if (ready != null || failed.contains(key) || !pending.add(key)) return ready;
        app.store.page(notebook.path, page, (decoded, error) -> {
            byte[] picture = decoded == null ? null : page == DrawingBook.FRONT ? PagePicture.marks(decoded.layerSnapshot(), WordsMode.exportTiles()).encode()
                    : PagePicture.of(decoded.layerSnapshot(), WordsMode.exportTiles()).encode();
            app.runOnUiThread(() -> {
                pending.remove(key);
                if (picture == null) failed.add(key);
                else {
                    // Pictures of the file as it was before go: one entry a notebook and page, not one a change.
                    made.keySet().removeIf(k -> k.startsWith(notebook.path + "@") && !k.startsWith(notebook.path + "@" + notebook.modified + "#"));
                    made.put(key, picture);
                }
                redraw();
            });
        });
        return null;
    }
    /** The open notebook's cover {@code side} ({@link DrawingBook#FRONT} or {@link DrawingBook#BACK}) in {@code box}, from its pictures; null while one is made. */
    private Bitmap coverPicture(DrawingBook book, int side, RectF box) {
        boolean front = side == DrawingBook.FRONT;
        byte[] pictured = null, marks = null;
        if (front && book.cover() >= 0) { pictured = book.picture(book.cover()); if (pictured == null) { picture(book, book.cover()); return null; } }
        if (book.coverDrawn(side)) { marks = book.picture(side); if (marks == null) { picture(book, side); return null; } }
        return composed(front, book.design(), app.drawingName, book.width, book.height, pictured, marks, box);
    }
    /** Covers made as masks, by what they are made of and their size. */
    private final android.util.LruCache<String, Bitmap> covers = new android.util.LruCache<String, Bitmap>(8 << 20) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    /**
     * A cover of the notebook at {@code path} in {@code design} fitted in {@code box}, as a mask ({@link NotebookCovers#shown}):
     * made in the background the first time for what it is made of, then kept; null while it is made.
     */
    private Bitmap composed(boolean front, CoverDesign design, String path, int pageWidth, int pageHeight, byte[] pictured, byte[] marks, RectF box) {
        if (pageWidth <= 0 || pageHeight <= 0) { pageWidth = Math.max(1, Math.round(box.width())); pageHeight = Math.max(1, Math.round(box.height())); }
        float scale = Math.min(box.width() / pageWidth, box.height() / pageHeight);
        int w = Math.max(1, Math.round(pageWidth * scale)), h = Math.max(1, Math.round(pageHeight * scale));
        String name = DrawingFiles.name(path);
        String key = NotebookCovers.shownKey(front, design, name, w, h, pictured, marks);
        Bitmap ready = covers.get(key);
        if (ready != null || failed.contains(key) || !pending.add(key)) return ready;
        worker.execute(() -> {
            Bitmap mask = null;
            try {
                byte[] tones = NotebookCovers.shown(front, design, name, w, h, pictured == null ? null : PagePicture.decode(pictured), marks == null ? null : PagePicture.decode(marks));
                mask = mask(tones, w, h);
            } catch (Exception | OutOfMemoryError e) { android.util.Log.w(ProbeActivity.TAG, "Could not show a cover", e); }
            Bitmap shownMask = mask;
            app.runOnUiThread(() -> {
                pending.remove(key);
                if (shownMask == null) failed.add(key); else covers.put(key, shownMask);
                redraw();
            });
        });
        return null;
    }
    /** {@code picture} as an alpha mask of ink's darkness ({@link PagePicture#darkness}). */
    private static Bitmap mask(PagePicture picture) {
        Bitmap mask = Bitmap.createBitmap(picture.width, picture.height, Bitmap.Config.ALPHA_8);
        byte[] dark = picture.darkness(), rows = new byte[mask.getRowBytes() * picture.height];
        for (int y = 0; y < picture.height; y++) System.arraycopy(dark, y * picture.width, rows, y * mask.getRowBytes(), picture.width);
        mask.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(rows));
        return mask;
    }
    /** The page shown big, made from the page itself at its box's own pixels ({@link PagePicture#shown}): the current page and its neighbours, about 2.5 MB each, let go on leaving. */
    private final android.util.LruCache<Object, Bitmap> crisps = new android.util.LruCache<Object, Bitmap>(12 << 20) {
        @Override protected int sizeOf(Object key, Bitmap value) { return value.getByteCount(); }
    };
    /**
     * Stack {@code place} at exactly {@code w × h} px, its bottom page's guides in it, its layers flattened (a Hidden one
     * left out, {@link PageStacks#flatten}), or null while it is made in the background (its small picture shows meanwhile).
     */
    private Bitmap crisp(DrawingBook book, int place, int w, int h) {
        if (DrawingBook.isCover(place) || !valid(book, place)) return null;
        PageStacks stacks = book.stacks();
        TracingPaper tracing = book.tracing();
        int from = stacks.first(place), to = stacks.last(place), n = to - from + 1;
        NotebookTemplate template = book.template(from);
        // The guides as the notebook shows them (Template's step, 2026-10-22).
        int guidesShown = book.options().guidesShown;
        List<Object> id = new ArrayList<>(java.util.Arrays.asList("crisp", template, guidesShown, w, h));
        for (int p = from; p <= to; p++) {
            DrawingBook.PictureKey key = book.pictureKey(p);
            id.add(key.page != null ? key.page : key.packed); id.add(key.revision); id.add(n > 1 && tracing.step(p) == TracingPaper.HIDDEN);
        }
        Bitmap ready = crisps.get(id);
        if (ready != null || failed.contains(id) || !pending.add(id)) return ready;
        ToneDocument.Snapshot[] snapshots = new ToneDocument.Snapshot[n];
        PackedPage[] packs = new PackedPage[n];
        boolean[] hidden = new boolean[n];
        for (int k = 0; k < n; k++) {
            ToneDocument decoded = book.cachedPage(from + k);
            snapshots[k] = decoded != null ? decoded.layerSnapshot() : null;
            packs[k] = snapshots[k] == null ? book.packed(from + k) : null;
            hidden[k] = n > 1 && tracing.step(from + k) == TracingPaper.HIDDEN;
            if (snapshots[k] == null && packs[k] == null) { pending.remove(id); return null; }
        }
        int pageWidth = book.width, pageHeight = book.height, shownAt = viewing;
        worker.execute(() -> {
            // Turned past meanwhile (pages flicked through queue theirs and their neighbours'): not made, asked for again if shown again.
            if (mode != Mode.PAGES || viewing != shownAt) { app.runOnUiThread(() -> pending.remove(id)); return; }
            Bitmap mask = null;
            try {
                synchronized (DocumentCodec.ONE_PAGE) {
                    List<ToneDocument.Snapshot> layers = new ArrayList<>();
                    for (int k = 0; k < n; k++) {
                        ToneDocument.Snapshot page = snapshots[k];
                        if (page == null) {
                            // A page's file holds no guides: they go under the bottom one as when it is decoded to draw on.
                            ToneDocument read = DocumentCodec.read(new java.io.ByteArrayInputStream(packs[k].bytes()));
                            if (k == 0) read.placeGuides(template.shown(pageWidth, pageHeight, guidesShown));
                            page = read.layerSnapshot();
                        }
                        layers.add(page);
                    }
                    mask = mask(PagePicture.shown(PageStacks.flatten(layers, hidden), WordsMode.exportTiles(), w, h));
                }
            } catch (Exception | OutOfMemoryError e) { android.util.Log.w(ProbeActivity.TAG, "Could not show page " + (place + 1) + " at screen size", e); }
            Bitmap shown = mask;
            app.runOnUiThread(() -> {
                pending.remove(id);
                if (shown == null) failed.add(id); else if (mode == Mode.PAGES) crisps.put(id, shown);
                redraw();
            });
        });
        return null;
    }
    /** Tones {@code w × h} as the screens show them: an alpha mask of ink's darkness, in the export's greys. */
    static Bitmap mask(byte[] tones, int w, int h) {
        Bitmap mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        byte[] rows = new byte[mask.getRowBytes() * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) rows[y * mask.getRowBytes() + x] = (byte) (255 - DotPattern.exportGray(tones[y * w + x] & 255));
        mask.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(rows));
        return mask;
    }
    private void redraw() {
        if (mode == Mode.PAGES) { if (page != null) page.invalidate(); if (strip != null) strip.invalidate(); if (settings != null) Ui.invalidateTree(settings); }
        else if (mode == Mode.SHELF) Ui.invalidateTree(view);
        else if (app.inkWashBar != null) app.inkWashBar.turner.picturesArrived();
    }

    // Drawing pictures

    private final Paint ink = new Paint(Paint.FILTER_BITMAP_FLAG), line = new Paint();
    {
        ink.setColor(Theme.INK);
        line.setStyle(Paint.Style.STROKE);
    }
    /**
     * A cover's {@code mask} drawn into {@code box} as {@link #drawPicture} does; while it is made (null), a plain
     * placeholder: paper with its tape in ink, on the left of the front, the right of the {@code back}.
     */
    static void drawCover(Canvas canvas, Bitmap mask, RectF box, boolean back, Paint ink) {
        if (mask != null) { drawPicture(canvas, mask, box, ink); return; }
        float tape = CoverPattern.spine(Math.round(box.width()));
        canvas.drawRect(back ? box.right - tape : box.left, box.top, back ? box.right : box.left + tape, box.bottom, ink);
    }
    private void drawPicture(Canvas canvas, Bitmap mask, RectF box) { drawPicture(canvas, mask, box, ink); }
    /** {@code mask} drawn into {@code box}, its proportions kept, centred, on paper. */
    static void drawPicture(Canvas canvas, Bitmap mask, RectF box, Paint ink) {
        if (mask == null) return;
        float scale = Math.min(box.width() / mask.getWidth(), box.height() / mask.getHeight());
        float w = mask.getWidth() * scale, h = mask.getHeight() * scale;
        RectF to = new RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2);
        canvas.drawBitmap(mask, null, to, ink);
    }
    /** Where a page of {@code book} goes in {@code box}: its proportions kept, centred. */
    static RectF fitted(DrawingBook book, RectF box) {
        float scale = Math.min(box.width() / book.width, box.height() / book.height), w = book.width * scale, h = book.height * scale;
        return new RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2);
    }
    private void outline(Canvas canvas, RectF box, float stroke, int color, boolean dashed) {
        line.setStrokeWidth(stroke); line.setColor(color);
        line.setPathEffect(dashed ? Theme.dashes(app) : null);
        canvas.drawRect(box.left + stroke / 2, box.top + stroke / 2, box.right - stroke / 2, box.bottom - stroke / 2, line);
    }
    private void plus(Canvas canvas, RectF box) {
        Drawable plus = Theme.tinted(app, R.drawable.ic_ph_plus, Theme.GREY_800);
        int size = (int) Math.min(dp(40), Math.min(box.width(), box.height()) / 2);
        plus.setBounds((int) box.centerX() - size / 2, (int) box.centerY() - size / 2, (int) box.centerX() + size / 2, (int) box.centerY() + size / 2);
        plus.draw(canvas);
    }

    /**
     * Stack or cover {@code place} of {@code book} big in {@code inside}, by its pictures (the bottom page's guides under its
     * layers', a Hidden layer left out): the page shown inside the notebook, and the drawing screen's riffle ({@link
     * PageTurner}). Nothing yet while it is made.
     */
    void drawPlace(Canvas canvas, DrawingBook book, int place, RectF inside) { drawPlace(canvas, book, place, inside, true); }
    /** As {@link #drawPlace(Canvas, DrawingBook, int, RectF)}; a missing picture of a cover or of the page drawn on is made only when {@code make}. */
    void drawPlace(Canvas canvas, DrawingBook book, int place, RectF inside, boolean make) {
        if (!valid(book, place)) return;
        if (DrawingBook.isCover(place)) { drawCover(canvas, coverMask(book, place, inside, make), fitted(book, inside), place == DrawingBook.BACK, ink); return; }
        PageStacks stacks = book.stacks();
        TracingPaper tracing = book.tracing();
        drawPicture(canvas, guides(book, stacks.first(place)), inside);
        for (int p = stacks.first(place); p <= stacks.last(place); p++) {
            if (stacks.size(place) > 1 && tracing.step(p) == TracingPaper.HIDDEN) continue;
            if (!make && p == book.index() && book.picture(p) == null) continue;
            drawPicture(canvas, pagePicture(book, p), inside);
        }
    }
    /** Cover {@code i}'s mask, as {@link #coverPicture}; when not {@code make}, none while its marks have no picture (the cover drawn on is never copied for one). */
    private Bitmap coverMask(DrawingBook book, int i, RectF inside, boolean make) {
        return make || !book.coverDrawn(i) || book.picture(i) != null ? coverPicture(book, i, inside) : null;
    }
    /** Previews for the drawing screen's page bar, in the page's dots, by their pictures' bytes; about 9 KB each. */
    private final android.util.LruCache<Object, Bitmap> previews = new android.util.LruCache<Object, Bitmap>(2 << 20) {
        @Override protected int sizeOf(Object key, Bitmap value) { return value.getByteCount(); }
    };
    /**
     * Stack or cover {@code place} of {@code book} small in {@code inside}, for the drawing screen's page bar ({@link
     * PageTurner}): its layers' pictures over each other ({@link PagePicture#stacked}), a Hidden layer and {@code except}
     * (the page drawn on, which the bar draws from the page itself; {@link DrawingBook#NONE}: none) left out, made in the
     * background and kept; a cover as {@link #drawPreview}. A missing picture is made, but never of {@code except}.
     * Whether it is all drawn (false: a picture or its preview is still being made).
     */
    boolean drawStackPreview(Canvas canvas, DrawingBook book, int place, RectF inside, int except) {
        if (!valid(book, place)) return true;
        if (DrawingBook.isCover(place)) { drawPreview(canvas, book, place, inside, place != except); return true; }
        PageStacks stacks = book.stacks();
        TracingPaper tracing = book.tracing();
        List<byte[]> layers = new ArrayList<>();
        boolean missing = false;
        for (int p = stacks.first(place); p <= stacks.last(place); p++) {
            if (p == except || stacks.size(place) > 1 && tracing.step(p) == TracingPaper.HIDDEN) continue;
            byte[] bytes = book.picture(p);
            if (bytes == null) { picture(book, p); missing = true; } else layers.add(bytes);
        }
        if (missing) return false;
        if (layers.isEmpty()) return true;
        if (layers.size() == 1) return drawPicturePreview(canvas, book, layers.get(0), inside);
        RectF to = fitted(book, inside);
        int w = Math.max(1, Math.round(to.width())), h = Math.max(1, Math.round(to.height()));
        List<Object> key = new ArrayList<>(java.util.Arrays.asList("stack", w, h));
        key.addAll(layers);
        Bitmap ready = previews.get(key);
        if (ready != null) { canvas.drawBitmap(ready, to.left, to.top, null); return true; }
        if (failed.contains(key) || !pending.add(key)) return false;
        worker.execute(() -> {
            Bitmap made = null;
            try {
                List<PagePicture> pictures = new ArrayList<>();
                for (byte[] bytes : layers) pictures.add(PagePicture.decode(bytes));
                made = Bitmap.createBitmap(PagePicture.stacked(pictures).dots(w, h), w, h, Bitmap.Config.ARGB_8888);
            } catch (Exception | OutOfMemoryError e) { android.util.Log.w(ProbeActivity.TAG, "Could not make a stack's preview", e); }
            Bitmap shown = made;
            app.runOnUiThread(() -> {
                pending.remove(key);
                if (shown == null) failed.add(key); else previews.put(key, shown);
                redraw();
            });
        });
        return false;
    }
    /**
     * Page or cover {@code i} of {@code book} small in {@code inside}, for the drawing screen's page bar ({@link
     * PageTurner}): a cover as the strip's, a page its picture's dots ({@link PagePicture#dots}), made in the background
     * and kept. A page without a picture gets one made, unless not {@code make} (the page drawn on: never copied for it).
     */
    void drawPreview(Canvas canvas, DrawingBook book, int i, RectF inside, boolean make) {
        if (!book.valid(i)) return;
        if (DrawingBook.isCover(i)) { drawCover(canvas, coverMask(book, i, inside, make), fitted(book, inside), i == DrawingBook.BACK, ink); return; }
        byte[] bytes = book.picture(i);
        if (bytes == null) { if (make) picture(book, i); return; }
        drawPicturePreview(canvas, book, bytes, inside);
    }
    /** A page picture's {@code bytes} small in {@code inside}, in the page's dots, made in the background and kept; whether it was ready. */
    private boolean drawPicturePreview(Canvas canvas, DrawingBook book, byte[] bytes, RectF inside) {
        RectF to = fitted(book, inside);
        int w = Math.max(1, Math.round(to.width())), h = Math.max(1, Math.round(to.height()));
        // Keyed by size too: two sizes kept under one key (the strip's and the Layers panel's) remade each other, a decode and
        // a panel update after another for as long as both showed (the owner's "sluggish while the panel is open").
        Object key = java.util.Arrays.asList("preview", bytes, w, h);
        Bitmap ready = previews.get(key);
        if (ready != null) { canvas.drawBitmap(ready, to.left, to.top, null); return true; }
        if (failed.contains(key) || !pending.add(key)) return false;
        worker.execute(() -> {
            Bitmap made = null;
            try { made = Bitmap.createBitmap(PagePicture.decode(bytes).dots(w, h), w, h, Bitmap.Config.ARGB_8888); }
            catch (Exception | OutOfMemoryError e) { android.util.Log.w(ProbeActivity.TAG, "Could not make a page preview", e); }
            Bitmap shown = made;
            app.runOnUiThread(() -> {
                pending.remove(key);
                if (shown == null) failed.add(key); else previews.put(key, shown);
                redraw();
            });
        });
        return false;
    }

    /** A shelf tile's cover: its front cover inside an ink outline; New notebook's dashed, with a +. */
    @SuppressLint("ViewConstructor")
    private final class CoverView extends View {
        private final DocumentStore.Notebook notebook;
        CoverView(DocumentStore.Notebook notebook) { super(app); this.notebook = notebook; }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(Theme.PAPER);
            RectF box = new RectF(0, 0, getWidth(), getHeight());
            if (notebook == null) { outline(canvas, box, dp(Theme.BASE), Theme.GREY_600, true); plus(canvas, box); return; }
            // The front cover, its pattern and tape to the edges, inside an ink outline; its tape alone while it is made.
            drawCover(canvas, coverPicture(notebook, box), box, false, ink);
            outline(canvas, box, dp(Theme.BASE), Theme.INK, false);
        }
    }

    /** The page shown inside the notebook, by its picture; a tap draws on it, a swipe turns it. */
    @SuppressLint("ViewConstructor")
    private final class PageView extends View {
        private final GestureDetector gestures = new GestureDetector(app, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) { NotebookScreens.this.draw(); return true; }
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null) return false;
                float density = getResources().getDisplayMetrics().density;
                int by = NotebookLayout.swipe((b.getX() - a.getX()) / density, (b.getY() - a.getY()) / density);
                if (by == 0) return false;
                turn(by);
                return true;
            }
        });
        PageView() { super(app); setContentDescription("The page. Tap to open it"); }
        /** A find's boxes went at this touch's start; the page drawn again at its end if still shown (a tap leaves the screen: no update for nothing). */
        private boolean unmarked;
        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN && !marks.isEmpty()) { unmark(); unmarked = true; }
            boolean used = gestures.onTouchEvent(event) || super.onTouchEvent(event);
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && unmarked) {
                unmarked = false;
                if (mode == Mode.PAGES && page == this) invalidate();
            }
            return used;
        }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(Theme.PAPER);
            DrawingBook book = app.book;
            float stroke = dp(Theme.BASE);
            RectF box = new RectF(0, 0, getWidth(), getHeight());
            RectF inside = new RectF(stroke, stroke, getWidth() - stroke, getHeight() - stroke);
            if (book != null && viewing != CARD) drawBig(canvas, book, inside);
            outline(canvas, box, stroke, Theme.INK, false);
        }
    }

    /** The page shown big: made at the box's own pixels and drawn 1:1, its small picture stretched while it is made; its neighbours made next. */
    private void drawBig(Canvas canvas, DrawingBook book, RectF inside) {
        RectF to = fitted(book, inside);
        int w = Math.round(to.width()), h = Math.round(to.height());
        Bitmap crisp = crisp(book, viewing, w, h);
        if (crisp != null) canvas.drawBitmap(crisp, Math.round(to.left), Math.round(to.top), ink);
        else drawPlace(canvas, book, viewing, inside);
        if (crisp != null && !DrawingBook.isCover(viewing)) for (int near : new int[]{NotebookStrip.before(book, viewing), NotebookStrip.after(book, viewing)}) if (near != DrawingBook.NONE) crisp(book, near, w, h);
        // A find's lines boxed in ink, a little outside the writing (Read Handwriting).
        if (!marks.isEmpty() && book.valid(markPage) && book.stacks().of(markPage) == viewing) {
            float sx = to.width() / book.width, sy = to.height() / book.height, pad = dp(4);
            markInk.setStyle(Paint.Style.STROKE); markInk.setStrokeWidth(dp(Theme.BASE)); markInk.setColor(Theme.INK);
            for (float[] m : marks) canvas.drawRect(to.left + m[0] * sx - pad, to.top + m[1] * sy - pad, to.left + m[2] * sx + pad, to.top + m[3] * sy + pad, markInk);
        }
    }

    // The settings list (LibraryCard)

    private final Paint grey = new Paint();
    /** The template's thumbnail in its row, at its box's pixels; null while it is made (and for Blank and Random). */
    private Bitmap swatch(NotebookTemplate template, int w, int h) {
        if (template == NotebookTemplate.BLANK || template.random()) return null;
        Object id = java.util.Arrays.asList("swatch", template, w, h);
        Bitmap ready = crisps.get(id);
        if (ready != null || failed.contains(id) || !pending.add(id)) return ready;
        worker.execute(() -> {
            Bitmap mask = null;
            try { byte[] tones = template.shared(w, h); if (tones != null) mask = mask(tones, w, h); }
            catch (RuntimeException | OutOfMemoryError e) { android.util.Log.w(ProbeActivity.TAG, "Could not make the template's swatch", e); }
            Bitmap made = mask;
            app.runOnUiThread(() -> { pending.remove(id); if (made == null) failed.add(id); else crisps.put(id, made); redraw(); });
        });
        return null;
    }
    /**
     * The Tools row's marks, slot by slot, at a mark's pixels: the Tools page's library cards' tray stroke
     * ({@link LabLibrary#traySampler}, its pixels kept by {@link WildSample} for both), as an ink mask. Those not yet
     * made are made together in the background and the row drawn again once, all of them in (one e-ink update); null
     * entries meanwhile.
     */
    private List<Object> previews(List<Brush> brushes, int w, int h) {
        List<Object> shown = new ArrayList<>();
        List<Brush> missing = new ArrayList<>();
        for (Brush b : brushes) {
            Bitmap ready = crisps.get(java.util.Arrays.asList("preview", b.identity(), w, h));
            shown.add(ready);
            if (ready == null) missing.add(b);
        }
        Object id = java.util.Arrays.asList("previews", shown.size(), missing.isEmpty() ? null : missing.get(0).identity(), w, h);
        if (missing.isEmpty() || w <= 0 || h <= 0 || failed.contains(id) || !pending.add(id)) return shown;
        List<LoopCard.Sample> samples = new ArrayList<>();
        for (Brush b : missing) samples.add(LabLibrary.traySampler(b, null).get());
        worker.execute(() -> {
            List<Bitmap> made = new ArrayList<>();
            boolean ok = true;
            for (LoopCard.Sample sample : samples) {
                Bitmap m = null;
                try { m = darkness(sample.pixels(w, h), w, h); }
                catch (RuntimeException | OutOfMemoryError e) { android.util.Log.w(ProbeActivity.TAG, "Could not draw a brush's stroke for the settings", e); ok = false; }
                made.add(m);
            }
            boolean all = ok;
            app.runOnUiThread(() -> {
                pending.remove(id);
                if (!all) failed.add(id);
                for (int i = 0; i < made.size(); i++) if (made.get(i) != null) crisps.put(java.util.Arrays.asList("preview", missing.get(i).identity(), w, h), made.get(i));
                redraw();
            });
        });
        return shown;
    }
    /** ARGB greys (a sample's, ink on paper) as an alpha mask of their darkness. */
    private static Bitmap darkness(int[] argb, int w, int h) {
        Bitmap mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        byte[] rows = new byte[mask.getRowBytes() * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) rows[y * mask.getRowBytes() + x] = (byte) (255 - (argb[y * w + x] & 0xff));
        mask.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(rows));
        return mask;
    }
    /**
     * The settings list for {@code book} ({@link LibraryCard}): the name (a tap renames it) and Rename, a grey-200 row a
     * setting, Export Now and Delete Notebook ({@link LibraryCard#DELETE_NOTEBOOK}), the foot. Made again whenever the
     * notebook's options change (one e-ink update).
     */
    private void fillSettings(DrawingBook book) {
        NotebookOptions o = book.options();
        TracingPaper t = book.tracing();
        // Stop Taking Part's row in the Prompts notebook while this tablet takes part.
        boolean takingPart = app.prompts(app.drawingName) && !app.prefs.fridgeTablet().isEmpty();
        Object shown = java.util.Arrays.asList(o, t.paper, t.strength, book.design(), app.drawingName, count(book), book.cover(), book.stacks().of(book.index()), takingPart);
        if (shown.equals(settingsShown)) return;
        settingsShown = shown;
        settings.removeAllViews();
        String name = DrawingNames.shown(DrawingFiles.name(app.drawingName), LocalDate.now());
        LibraryCard.Layout l = LibraryCard.settingsLayout(layout.width - 2 * layout.margin, layout.spreadHeight, name);
        TextView named = text(name, TypeStyle.TITLE);
        named.setTextSize(l.titleSp); named.setMaxLines(l.titleLines);
        // Favourites is never renamed or deleted (NotebookStrip.FAVOURITES): no Rename, no Delete Notebook. The Prompts
        // notebook is never renamed (its path is how it is found), but may be deleted.
        boolean favourites = NotebookStrip.favourites(app.drawingName), kept = favourites || app.prompts(app.drawingName);
        if (!kept) named.setContentDescription(name + ". " + LibraryCard.RENAME);
        if (!kept) named.setOnClickListener(v -> tapSetting(LibraryCard.Part.NAME));
        settings.addView(named, sized(-1, -2));
        LabButton rename = new LabButton(app, 0, LibraryCard.RENAME, false);
        rename.setVisibility(kept ? View.GONE : View.VISIBLE);
        rename.setPadding(0, 0, dp(12), 0);
        rename.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        rename.setOnClickListener(v -> tapSetting(LibraryCard.Part.NAME));
        settings.addView(rename, sized(-2, Math.round(LibraryCard.Layout.RENAME)));
        for (int g = 0; g < LibraryCard.GROUPS.size(); g++) {
            List<LibraryCard.Part> group = LibraryCard.GROUPS.get(g);
            if (g > 0) groupLine();
            for (int i = 0; i < group.size(); i++) {
                LinearLayout.LayoutParams place = sized(-1, Math.round(l.rowHeight));
                place.topMargin = i > 0 ? dp(LibraryCard.Layout.ROW_GAP) : g == 0 ? dp(LibraryCard.Layout.GAP) : 0;
                settings.addView(settingRow(group.get(i), book), place);
            }
        }
        // The notebook's own actions after a group's line (2026-10-28, from the drawing screen's menu): Export Now, then
        // Delete Notebook last, each with the confirmation it had there.
        groupLine();
        LinearLayout actions = LabUi.row(app);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LabButton export = new LabButton(app, 0, PictureImport.EXPORT_NOW, false), delete = new LabButton(app, 0, LibraryCard.DELETE_NOTEBOOK, false);
        export.setOnClickListener(v -> { if (app.book == book && selector == null) app.exportNow(export); });
        delete.setOnClickListener(v -> { if (app.book == book && !app.busy() && selector == null) app.deleteNotebook(app.drawingName); });
        delete.setVisibility(favourites ? View.GONE : View.VISIBLE);
        for (LabButton b : new LabButton[]{export, delete}) {
            b.setMinHeight(0); b.setMinimumHeight(0);
            LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(-2, dp(LibraryCard.actionHeight(l.rowHeight)));
            at.setMarginEnd(dp(LibraryCard.Layout.GAP));
            actions.addView(b, at);
        }
        settings.addView(actions, sized(-1, Math.round(l.rowHeight)));
        // The Prompts notebook while this tablet takes part (the Prompts, 2026-11-13): Stop Taking Part, asked first.
        if (takingPart) {
            LabButton stop = new LabButton(app, 0, Fridge.STOP, false);
            stop.setMinHeight(0); stop.setMinimumHeight(0);
            stop.setOnClickListener(v -> {
                if (app.book != book || app.busy() || selector != null) return;
                app.showDialog(new AlertDialog.Builder(app).setTitle(Fridge.STOP_ASK).setMessage(Fridge.STOP_ABOUT)
                        .setPositiveButton(Fridge.STOP, (d, w) -> app.stopTakingPart(() -> { if (app.book == book) fillSettings(book); }))
                        .setNegativeButton(Fridge.CANCEL, null));
            });
            settings.addView(stop, sized(-2, dp(LibraryCard.actionHeight(l.rowHeight))));
            TextView note = text(Fridge.STOP_NOTE, TypeStyle.CAPTION);
            note.setTextColor(Theme.GREY_800);
            settings.addView(note, sized(-1, -2));
        }
        long edited = app.store.modified(app.drawingName);
        TextView foot = text(LibraryCard.foot(DrawingNames.isoDate(DrawingFiles.name(app.drawingName)),
                edited <= 0 ? null : java.time.Instant.ofEpochMilli(edited).atZone(java.time.ZoneId.systemDefault()).toLocalDate(), count(book)), TypeStyle.CAPTION);
        foot.setTextColor(Theme.GREY_800);
        LinearLayout.LayoutParams footPlace = sized(-1, -2); footPlace.topMargin = dp(LibraryCard.Layout.GAP);
        settings.addView(foot, footPlace);
    }
    /** A grey-400 line between the settings' groups, in the middle of their gap (the drawing screen menu's). */
    private void groupLine() {
        View line = new View(app); line.setBackgroundColor(Theme.GREY_400);
        int room = dp(LibraryCard.Layout.BREAK) - dp(Theme.FINE);
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(-1, dp(Theme.FINE));
        at.setMargins(0, room / 2, 0, room - room / 2);
        settings.addView(line, at);
    }
    /** A setting's row: its key, what it is set to (or its buttons), "›" where it opens a screen. */
    private View settingRow(LibraryCard.Part part, DrawingBook book) {
        NotebookOptions o = book.options();
        String key = LibraryCard.key(part), value = LibraryCard.value(part, o, book.design());
        LinearLayout row = row(key, false);
        switch (part) {
            case NIB_PREVIEW:
                chips(row, OFF_ON, o.nibPreview ? 1 : 0, i -> { if (app.book == book && !app.busy() && (i == 1) != book.options().nibPreview) app.setNotebookOptions(book.options().withNibPreview(i == 1)); });
                value = o.nibPreview ? NotebookRules.ON : NotebookRules.OFF;
                break;
            case SECOND_INK:
                chips(row, SecondInk.NAMES, o.ink, i -> { if (app.book == book && !app.busy() && i != book.options().ink) app.setNotebookOptions(book.options().withInk(i)); });
                value = SecondInk.NAMES[o.ink] + ". " + LibraryCard.SECOND_INK_NOTE;
                break;
            case NO_ERASING:
                chips(row, OFF_ON, o.noErasing ? 1 : 0, i -> { if ((i == 1) != book.options().noErasing) tapSetting(part); });
                value = o.noErasing ? NotebookRules.ON : NotebookRules.OFF;
                break;
            case LOCK_PAGES:
                chips(row, OFF_ON, o.lockPages ? 1 : 0, i -> { if (app.book == book && !app.busy() && (i == 1) != book.options().lockPages) app.setNotebookOptions(book.options().withLockPages(i == 1)); });
                // Greyed while nothing is timed: it applies only then (the chips stay as set).
                if (!LibraryCard.locksApply(o)) for (int c = 1; c < row.getChildCount(); c++) row.getChildAt(c).setEnabled(false);
                value = o.lockPages ? NotebookRules.ON : NotebookRules.OFF;
                break;
            case COVER_PICTURE: {
                // The page drawn on (a stack: its bottom page's picture) on the front cover, or none (the menu's Use This
                // Page on the Cover until the refocus, 2026-10-24).
                PageStacks s = book.stacks();
                int drawn = DrawingBook.isCover(book.index()) ? -1 : s.of(book.index()), on = book.cover() < 0 ? -1 : s.of(book.cover());
                String[] words = drawn < 0 ? new String[]{NotebookCovers.NO_PICTURE_BUTTON} : new String[]{NotebookCovers.USE_BUTTON, NotebookCovers.NO_PICTURE_BUTTON};
                int chosen = on < 0 ? words.length - 1 : on == drawn ? 0 : -1;
                chips(row, words, chosen, i -> { if (app.book == book && !app.busy()) app.setCoverPicture(i == words.length - 1 ? -1 : s.first(drawn)); });
                value = LibraryCard.coverPicture(on);
                break;
            }
            default: {
                if (part == LibraryCard.Part.TEMPLATE) row.addView(new Thumb(o), thumbSize());
                words(row, value);
                if (part == LibraryCard.Part.TOOLS) {
                    LinearLayout.LayoutParams marks = sized(Math.round(8 * LibraryCard.Layout.MARK + 7 * LibraryCard.Layout.MARK_GAP), Math.round(LibraryCard.Layout.MARK * .6f));
                    marks.leftMargin = dp(8);
                    row.addView(new Marks(o), marks);
                }
            }
        }
        rowEnd(row, key, value, LibraryCard.opens(part), () -> tapSetting(part));
        return row;
    }
    private static final String[] OFF_ON = {NotebookRules.OFF, NotebookRules.ON};
    /** A row's start: its key in caps at the left, then room; grey-200 in the notebook's list, paper in a page's (ruled apart in ink). */
    private LinearLayout row(String key, boolean paper) {
        LinearLayout row = LabUi.row(app);
        row.setBackgroundColor(paper ? Theme.PAPER : Theme.GREY_200);
        int pad = dp(LibraryCard.Layout.PAD);
        row.setPadding(pad, 0, pad, 0);
        row.addView(LabUi.barText(app, key), new LinearLayout.LayoutParams(-2, -2));
        LabUi.spring(row);
        return row;
    }
    /** A row's end: "›" where it opens a screen, {@code tap} on it, and what a screen reader says. */
    private void rowEnd(LinearLayout row, String key, String value, boolean opens, Runnable tap) {
        if (opens) {
            android.widget.ImageView more = new android.widget.ImageView(app);
            more.setImageDrawable(Theme.tinted(app, R.drawable.ic_ph_caret_right, Theme.GREY_800));
            LinearLayout.LayoutParams at = sized(18, 18); at.leftMargin = dp(8);
            row.addView(more, at);
        }
        if (opens) row.setOnClickListener(v -> tap.run());
        row.setContentDescription(key + ", " + value);
    }
    /**
     * What a row is set to, in words at its right: one line, never a word broken (the owner, 2026-10-27: "some text is
     * cut off"; two autosized lines broke "Off" as "Of / f"), as wide as it needs of the row's free room, cut with … only
     * past it (an imported template's long file name). Measured before the row's spring, so it takes the room first.
     */
    private void words(LinearLayout row, String value) {
        TextView words = text(value, TypeStyle.BODY);
        words.setTypeface(Theme.face(700)); words.setTextColor(Theme.INK);
        words.setSingleLine(true); words.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(-2, -1); at.leftMargin = dp(8);
        row.addView(words, at);
    }
    /** Template's thumbnail's size: a page's shape. */
    private LinearLayout.LayoutParams thumbSize() { return sized(Math.round(LibraryCard.Layout.THUMB), (int) Math.round(LibraryCard.Layout.THUMB * layout.aspect)); }
    /** Page Colour's six swatches, the one of {@code chosen} tone tabbed (none for another grey); a tap sets its tone ({@code set}). */
    private void swatches(LinearLayout row, int chosen, java.util.function.IntConsumer set) {
        for (int i = 0; i < NotebookOptions.PAPERS.length; i++) {
            int tone = NotebookOptions.PAPERS[i];
            Swatch swatch = new Swatch(tone, tone == chosen);
            swatch.setOnClickListener(v -> set.accept(tone));
            LinearLayout.LayoutParams at = sized(Math.round(LibraryCard.Layout.SWATCH), Math.round(LibraryCard.Layout.SWATCH + CardArt.TAB + 3));
            at.leftMargin = dp(LibraryCard.Layout.SWATCH_GAP);
            row.addView(swatch, at);
        }
    }
    /** Chips in a row, {@code chosen} ink-filled; a tap sets its index ({@code set}). */
    private void chips(LinearLayout row, String[] words, int chosen, java.util.function.IntConsumer set) {
        for (int i = 0; i < words.length; i++) {
            int which = i;
            LabButton chip = LabButton.chip(app, words[i]);
            chip.setOn(i == chosen);
            chip.setOnClickListener(v -> set.accept(which));
            row.addView(chip, new LinearLayout.LayoutParams(-2, dp(32)));
        }
    }
    /** A tap on a setting's row or the name: renamed, No Erasing set on or off, its screen opened. */
    private void tapSetting(LibraryCard.Part part) {
        DrawingBook book = app.book;
        if (book == null || app.busy() || selector != null) return;
        switch (LibraryCard.tap(part, book.options())) {
            case RENAME: app.renameNotebook(); return;
            case TOGGLE:
                boolean on = !book.options().noErasing;
                app.setNotebookOptions(book.options().withNoErasing(on));
                // What it means, said as it is set: no eraser, no undo.
                if (on) app.note(NotebookRules.NO_ERASING_SAID);
                return;
            case CHOOSE: choose(LibraryCard.screen(part), 0); return;
            default:
        }
    }
    /** A Page Colour swatch: its grey square, a grey-400 edge where it would vanish on the row or the paper, the ink tab under the one chosen. */
    @SuppressLint("ViewConstructor")
    private final class Swatch extends View {
        private final int tone;
        private final boolean chosen;
        Swatch(int tone, boolean chosen) {
            super(app); this.tone = tone; this.chosen = chosen;
            setContentDescription(NotebookOptions.paperWord(tone) + (chosen ? ", in use" : ""));
        }
        @Override protected void onDraw(Canvas canvas) {
            int side = getWidth();
            grey.setColor(0xff000000 | tone * 0x010101);
            canvas.drawRect(0, 0, side, side, grey);
            if (tone >= (Theme.GREY_200 & 0xff)) outline(canvas, new RectF(0, 0, side, side), dp(Theme.BASE), Theme.GREY_400, false);
            if (chosen) canvas.drawRect(dp(CardArt.TAB_INSET), getHeight() - dp(CardArt.TAB), side - dp(CardArt.TAB_INSET), getHeight(), ink);
        }
    }
    /** Template's thumbnail: a page on the notebook's Page Colour, its guides in ink (Random's dice), in a page preview's grey-400 frame. */
    @SuppressLint("ViewConstructor")
    private final class Thumb extends View {
        private final NotebookOptions o;
        Thumb(NotebookOptions o) { super(app); this.o = o; }
        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            canvas.drawColor(0xff000000 | o.paper * 0x010101);
            RectF box = new RectF(0, 0, w, h);
            if (o.template.random()) {
                Drawable dice = Theme.tinted(app, R.drawable.ic_ph_dice, o.paper < 128 ? Theme.ON_INK : Theme.INK);
                int d = Math.min(w, h) * 3 / 4;
                dice.setBounds((w - d) / 2, (h - d) / 2, (w + d) / 2, (h + d) / 2);
                dice.draw(canvas);
            } else drawPicture(canvas, swatch(o.template, w, h), box);
            outline(canvas, box, dp(Theme.BASE), Theme.GREY_400, false);
        }
    }
    /** The Tools row's marks: the bar's brushes as small tray strokes, as many as it shows. */
    @SuppressLint("ViewConstructor")
    private final class Marks extends View {
        private final NotebookOptions o;
        Marks(NotebookOptions o) { super(app); this.o = o; }
        @Override protected void onDraw(Canvas canvas) {
            // The notebook's brushes: its own toolbar, else the app's.
            ToolSlots.Toolbar toolbar = ToolSlots.of(o);
            if (toolbar == null && app.inkWash != null) toolbar = app.inkWash.slots.appWide();
            if (toolbar == null) return;
            List<Brush> brushes = java.util.Arrays.asList(toolbar.brushes).subList(0, Math.min(o.slotsShown(), toolbar.brushes.length));
            int w = dp(LibraryCard.Layout.MARK), h = getHeight(), step = dp(LibraryCard.Layout.MARK + LibraryCard.Layout.MARK_GAP);
            List<Object> shown = previews(brushes, w, h);
            for (int i = 0; i < shown.size(); i++) if (shown.get(i) != null) canvas.drawBitmap((Bitmap) shown.get(i), i * step, 0, ink);
        }
    }
    /** Opens a settings screen ({@code page}: the page a page's own template is for); from the list, Back says ‹ Settings. */
    private void choose(CardSheet.Screen screen, int page) {
        selector = new CardSelector(app, view, layout, worker, () -> { selector = null; updatePages(); }, pageList != null ? LibraryCard.PAGE_SETTINGS : viewing == CARD ? LibraryCard.SETTINGS : "Back");
        selector.open(screen, page);
    }

    // Page Settings (the owner, 2026-10-23: "long press on a page will bring up a panel to adjust the settings for that page")

    /**
     * Page {@code place}'s (a stack's) Page Settings, a screen over the notebook screen ({@link LibraryCard#pageRows}):
     * from the drawing screen ({@code fromDrawing}: a hold on a page's preview, the Pages grid) Back goes back to drawing,
     * else to the notebook's pages.
     */
    void showPageSettings(int place, boolean fromDrawing) {
        DrawingBook book = app.book;
        if (book == null || app.busy() || place < 0 || place >= count(book)) return;
        pagePlace = place; pageFromDrawing = fromDrawing && mode != Mode.PAGES; pageShown = null;
        if (mode != Mode.PAGES) { viewing = place; cover(Mode.PAGES); build(); }
        else fillPageSettings();
    }
    /** Back from Page Settings: to drawing when the drawing screen opened it, else to the notebook's pages. */
    private void closePageSettings() {
        if (pageFromDrawing) { hide(); return; }
        if (pageList != null) view.removeView(pageList);
        pageList = null; pagePlace = DrawingBook.NONE; pageShown = null;
        updatePages();
    }
    /** Page {@code bottom}'s paper tone: read from the page itself, once for a page not decoded (the notebook's for one that can't be read). */
    private int tone(DrawingBook book, int bottom) {
        ToneDocument page = book.cachedPage(bottom);
        if (page != null) return page.paperTone();
        PackedPage packed = book.packed(bottom);
        if (packed != null && packed == tonePacked) return toneRead;
        try { toneRead = book.paperTone(bottom); tonePacked = packed; return toneRead; }
        catch (java.io.IOException damaged) { return book.options().paper; }
    }
    /**
     * Page Settings for {@link #pagePlace}, made again whenever what it shows changes (one e-ink update; the screen's
     * first is a whole one): Back in the bar, the page's number and the notebook's name, a paper row a setting with a 1 dp
     * ink line between (Timed Pages On for the page alone: its time's slider under it), Export Page and Delete at the foot. Nothing scrolls
     * ({@link LibraryCard.Layout}).
     */
    private void fillPageSettings() {
        DrawingBook book = app.book;
        if (book == null || pagePlace == DrawingBook.NONE || layout == null) return;
        PageStacks stacks = book.stacks();
        if (pagePlace >= stacks.count()) { closePageSettings(); return; }
        int place = pagePlace, bottom = stacks.first(place), tone = tone(book, bottom);
        List<LibraryCard.Part> parts = LibraryCard.pageRows(book.tracing(), stacks.size(place) == 1);
        boolean timeSlider = LibraryCard.timeSlider(book.pageRules(bottom));
        Object shown = java.util.Arrays.asList(place, book.options(), book.tracing(), tone, book.template(bottom), book.ownTemplate(bottom), book.pageRules(bottom), book.pageSeconds(bottom), book.surface(bottom),
                stacks.count(), stacks.size(place), book.cover(), stacks.of(book.index()), book.index(), book.count(), app.drawingName);
        if (pageList != null && shown.equals(pageShown)) return;
        pageShown = shown;
        if (pageList != null) view.removeView(pageList);
        NotebookLayout n = layout;
        LinearLayout screen = LabUi.column(app);
        screen.setBackgroundColor(Theme.PAPER);
        screen.setClickable(true);
        screen.setPadding(dp(n.margin), dp(n.margin), dp(n.margin), dp(n.margin));
        LinearLayout bar = LabUi.row(app);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        LabButton back = LabButton.back(app);
        back.setOnClickListener(v -> closePageSettings());
        bar.addView(back, new LinearLayout.LayoutParams(-2, dp(NotebookLayout.BUTTON)));
        screen.addView(bar, sized(-1, NotebookLayout.BAR));
        String title = LibraryCard.pageTitle(place), name = DrawingNames.shown(DrawingFiles.name(app.drawingName), LocalDate.now());
        LibraryCard.Layout l = new LibraryCard.Layout(n.width - 2 * n.margin, n.height - 2 * n.margin - NotebookLayout.BAR - n.gap, parts.size() + (timeSlider ? 1 : 0) + LibraryCard.ACTION_ROWS, 0, title);
        LinearLayout list = LabUi.column(app);
        TextView heading = text(title, TypeStyle.TITLE);
        heading.setTextSize(l.titleSp); heading.setMaxLines(l.titleLines);
        list.addView(heading, sized(-1, -2));
        TextView notebook = text(name, TypeStyle.BODY);
        notebook.setTextColor(Theme.GREY_800); notebook.setSingleLine(true); notebook.setEllipsize(android.text.TextUtils.TruncateAt.END);
        notebook.setGravity(Gravity.CENTER_VERTICAL);
        list.addView(notebook, sized(-1, Math.round(LibraryCard.Layout.RENAME)));
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) LabUi.line(list, 0, dp(LibraryCard.Layout.ROW_GAP));
            LinearLayout.LayoutParams at = sized(-1, Math.round(l.rowHeight));
            if (i == 0) at.topMargin = dp(LibraryCard.Layout.GAP);
            list.addView(pageRow(parts.get(i), book, place, bottom, tone), at);
            if (parts.get(i) == LibraryCard.Part.TIMED && timeSlider) list.addView(timeRow(book, bottom), sized(-1, Math.round(l.rowHeight)));
        }
        LabUi.line(list, 0, dp(LibraryCard.Layout.ROW_GAP));
        // The foot (the owner, 2026-10-23: "remove some of the other stuff on the bottom"; moving and the layers are rows
        // above it since the refocus, 2026-10-24, Clear page the eraser's hold): Export Page, whose menu offers the page's
        // copies (2026-10-28, PageExport), then Delete, as the notebook's settings end with Export Now and Delete Notebook.
        LinearLayout actions = LabUi.row(app);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LabButton export = new LabButton(app, 0, LibraryCard.EXPORT_PAGE, false), delete = new LabButton(app, 0, LibraryCard.DELETE, false);
        delete.setEnabled(stacks.count() > 1);
        delete.setOnClickListener(v -> { if (!app.busy() && selector == null && place < count(book)) { closePageSettings(); app.deleteStack(place); } });
        export.setOnClickListener(v -> {
            if (app.busy() || selector != null || place >= count(book)) return;
            app.exportMenu(export, place);
        });
        for (LabButton b : new LabButton[]{export, delete}) {
            b.setMinHeight(0); b.setMinimumHeight(0);
            LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(-2, dp(LibraryCard.actionHeight(l.rowHeight)));
            at.setMarginEnd(dp(LibraryCard.Layout.GAP));
            actions.addView(b, at);
        }
        list.addView(actions, sized(-1, Math.round(l.rowHeight)));
        LinearLayout.LayoutParams listPlace = sized(Math.round(l.width), -2);
        listPlace.gravity = Gravity.CENTER_HORIZONTAL; listPlace.topMargin = dp(n.gap);
        screen.addView(list, listPlace);
        pageList = screen;
        // Under a settings screen opened from it.
        view.addView(screen, selector != null && selector.shown() ? view.getChildCount() - 1 : view.getChildCount(), new FrameLayout.LayoutParams(-1, -1));
    }
    /** A page's row: as a setting's, on paper, for page {@code place} (a stack; its settings its bottom page's, {@code bottom}), its paper {@code tone}. */
    private View pageRow(LibraryCard.Part part, DrawingBook book, int place, int bottom, int tone) {
        NotebookOptions o = book.options();
        TracingPaper t = book.tracing();
        int rules = book.pageRules(bottom), step = t.step(bottom);
        boolean ghost = t.ghost >= 0 && book.stacks().of(t.ghost) == place;
        NotebookTemplate template = book.template(bottom);
        String key = LibraryCard.key(part), value = LibraryCard.pageValue(part, o, template, book.ownTemplate(bottom), tone, rules, step, ghost);
        LinearLayout row = row(key, true);
        switch (part) {
            case TEMPLATE:
                row.addView(new Thumb(o.withTemplate(template).withPaper(tone)), thumbSize());
                words(row, value);
                break;
            case SURFACE:
                value = LibraryCard.pageSurface(book.surface(bottom), book.ownSurface(bottom) != 0);
                words(row, value);
                break;
            case PAGE_COLOUR:
                // The notebook's, or a swatch (an older grey: none chosen, said as it is); one Undo step on the page, as Fill page.
                chips(row, new String[]{LibraryCard.NOTEBOOKS}, tone == o.paper ? 0 : -1, i -> app.shadeStack(place, book.options().paper));
                swatches(row, tone == o.paper ? -1 : tone, to -> app.shadeStack(place, to));
                break;
            case TRANSPARENCY: {
                boolean on = book.tracing().paper != TracingPaper.PLAIN;
                chips(row, OFF_ON, on ? 1 : 0, i -> { if (app.book == book && !app.busy()) { app.setTracing(LibraryCard.seeThrough(book.tracing(), i == 1)); updatePages(); } });
                value = String.join(", ", LibraryCard.transparency(book.tracing()));
                // Page Transparency's five (Page Before or Nearby Pages, Half or Faint): a long press, as elsewhere.
                row.setOnLongClickListener(v -> { tapSetting(part); return true; });
                break;
            }
            case SHOW_THROUGH:
                chips(row, LibraryCard.SHOW_STEPS, LibraryCard.stepChip(step), i -> { app.setTracing(book.tracing().withStep(bottom, LibraryCard.chipStep(i))); updatePages(); });
                // Ghost: this page shows through the page drawn on, in place of the page before (Page Before only; never the page drawn on).
                if (t.paper == TracingPaper.TRACING && place != book.stacks().of(book.index())) {
                    LabButton pin = LabButton.iconChip(app, R.drawable.ic_ph_ghost, LibraryCard.GHOST, 32);
                    pin.setOn(ghost);
                    pin.setOnClickListener(v -> { app.setTracing(book.tracing().withGhost(ghost ? -1 : bottom)); updatePages(); });
                    LinearLayout.LayoutParams at = sized(32, 32); at.leftMargin = dp(8);
                    row.addView(pin, at);
                }
                break;
            case LAYERS: {
                PageStacks st = book.stacks();
                int first = st.first(place), n = st.size(place);
                boolean full = n >= PageStacks.MOST || book.count() >= DrawingBook.MAX_PAGES;
                int drawnOn = !DrawingBook.isCover(book.index()) && st.of(book.index()) == place ? book.index() - first : -1;
                boolean old = drawnOn >= 0 && app.pad != null && app.pad.document == book.current() && VectorInk.oldLayers(app.pad.document);
                String[] words = LibraryCard.layerChips(n, full, old);
                int layers = n > 1 ? n : 0;
                chips(row, words, layers > 0 ? drawnOn : -1, i -> layerTapped(place, i, layers, words[i]));
                value = LibraryCard.layersValue(n, drawnOn);
                break;
            }
            case MOVE:
                chips(row, LibraryCard.MOVE_CHIPS, -1, i -> movePage(place, i == 0 ? -1 : 1));
                value = DrawLayout.pageSaid(place, book.stacks().count());
                break;
            default: {
                // No Erasing, Timed Pages: the notebook's, or Off or On for this page alone.
                int at = LibraryCard.ruleBits(part);
                chips(row, LibraryCard.ruleChoices(part, o), NotebookRules.own(rules, at), i -> app.setPageRules(bottom, NotebookRules.withOwn(book.pageRules(bottom), at, i)));
            }
        }
        rowEnd(row, key, value, LibraryCard.pageOpens(part), () -> {
            if (!app.busy() && selector == null) choose(part == LibraryCard.Part.SURFACE ? CardSheet.Screen.PAGE_SURFACE : CardSheet.Screen.PAGE_TEMPLATE, bottom);
        });
        return row;
    }
    /**
     * Layers' chip {@code i} on page {@code place} (the refocus, 2026-10-24): a layer's number draws on that layer; Add
     * puts a new layer on top and draws on it; Merge Old makes the page drawn on's old in-page layers one. The drawing
     * screen shows again for the first two.
     */
    private void layerTapped(int place, int i, int layers, String word) {
        DrawingBook book = app.book;
        if (book == null || app.busy() || selector != null || place >= count(book)) return;
        if (word.equals(LibraryCard.MERGE_OLD)) { app.mergeLayers(); return; }
        PageStacks st = book.stacks();
        int page = i < layers ? st.first(place) + i : st.last(place);
        hide();
        app.drawOn(page);
        if (i >= layers && app.book != null && app.book.index() == page) app.newLayer(true);
    }
    /** Move: page {@code place} one place earlier or later ({@code by}), Page Settings following it. */
    private void movePage(int place, int by) {
        DrawingBook book = app.book;
        int to = place + by;
        if (book == null || app.busy() || selector != null || to < 0 || to >= book.stacks().count()) return;
        pagePlace = to;
        app.moveStack(place, to);
        fillPageSettings();
    }
    /**
     * Timed Pages On for page {@code bottom} alone: its time on a slider (the owner, 2026-10-23: "it only has 5 minutes
     * displayed. ideally it should be a slider"), the notebook's Timed Pages' stops, 30 s to 60 min, the time in words
     * beside it; set as the slider is let go. Grey and still while the notebook is locked.
     */
    private View timeRow(DrawingBook book, int bottom) {
        int[] stops = NotebookOptions.SECONDS;
        int stop = Math.max(1, NotebookOptions.nearest(stops, book.rules(bottom).seconds));
        LinearLayout row = LabUi.row(app);
        row.setBackgroundColor(Theme.PAPER);
        int pad = dp(LibraryCard.Layout.PAD);
        row.setPadding(pad, 0, pad, 0);
        TextView words = text(NotebookRules.timeLabel(stops[stop]), TypeStyle.BODY);
        words.setTypeface(Theme.face(700)); words.setTextColor(Theme.INK);
        words.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        BandSlider slider = new BandSlider(app, 1, stops.length - 1, stop, null);
        slider.setContentDescription(NotebookRules.TIMED_PAGES + ": " + words.getText());
        slider.listener = new BandSlider.Listener() {
            int at = stop;
            @Override public void started() { }
            @Override public void moved(double value) { at = (int) Math.round(value); words.setText(NotebookRules.timeLabel(stops[at])); }
            @Override public void stopped() { app.setPageSeconds(bottom, stops[at]); }
        };
        row.addView(slider, new LinearLayout.LayoutParams(0, dp(32), 1));
        // As wide as the widest time, so the slider doesn't move as the words change.
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(dp(LabText.width(NotebookRules.timeLabel(stops[stops.length - 1]), TypeStyle.BODY.sp, 0) + 8), -1);
        row.addView(words, at);
        return row;
    }

    /** The strip ({@link NotebookStrip#slots}): the front cover, the pages, + and the back cover, a window scrolling round the one shown, framed in ink with a tab under it. */
    @SuppressLint("ViewConstructor")
    private final class StripView extends View {
        StripView() {
            super(app); setContentDescription("Pages");
            // The page shown's star, to a screen reader: Add to Favourites, or Remove from Favourites.
            setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    Boolean on = starred(viewing);
                    if (on != null) info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(STAR_ACTION, on ? NotebookStrip.UNSTAR : NotebookStrip.STAR));
                }
                @Override public boolean performAccessibilityAction(View host, int action, android.os.Bundle arguments) {
                    if (action == STAR_ACTION && starred(viewing) != null) { app.star(viewing); return true; }
                    return super.performAccessibilityAction(host, action, arguments);
                }
            });
        }
        /** Whether {@code x}, {@code y} is on the star's corner of the preview under it. */
        private boolean onStar(float x, float y) {
            float left = x - start(), across = dp(layout.thumbWidth + layout.stripGap), target = dp(NotebookLayout.starTarget(layout.thumbWidth));
            float in = left - (int) (left / across) * across;
            return y < target && in > dp(layout.thumbWidth) - target;
        }
        /** The strip's slot at place {@code k}: a window round the place shown, nothing pinned (the owner, 2026-11-03). */
        private int slotAt(int k) { return NotebookStrip.start(app.book, viewing, layout.visible) + k; }
        /** A tap opens the slot's page or cover (or + adds a page); a hold on the front cover opens Cover Design, on a page its Page Settings. */
        private final GestureDetector gestures = new GestureDetector(app, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                int at = slotAt(e.getX());
                if (at < 0) return true;
                // The star's corner of a page's preview stars it (Favourites), the rest of it opens it.
                int place = place(app.book, at);
                if (starred(place) != null && onStar(e.getX(), e.getY())) app.star(place); else tapStrip(at);
                return true;
            }
            @Override public void onLongPress(MotionEvent e) { int at = slotAt(e.getX()); if (at >= 0) holdStrip(at); }
        });
        /** The slot under {@code x}, or −1 between slots and past the last. */
        private int slotAt(float x) {
            x -= start();
            int slot = (int) (x / dp(layout.thumbWidth + layout.stripGap));
            if (slot < 0 || slot >= layout.visible || x - slot * dp(layout.thumbWidth + layout.stripGap) > dp(layout.thumbWidth) || app.book == null) return -1;
            int at = slotAt(slot);
            return at < slots(app.book) ? at : -1;
        }
        /** The first slot's left: in landscape the strip under the page is centred as the page is (the owner, 2026-10-21); in portrait it fills from the left. */
        private float start() {
            if (app.book == null || layout.width <= layout.height) return 0;
            int shown = Math.min(layout.visible, slots(app.book));
            return Math.max(0, (getWidth() - (shown * dp(layout.thumbWidth + layout.stripGap) - dp(layout.stripGap))) / 2f);
        }
        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent event) { return gestures.onTouchEvent(event) || true; }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(Theme.PAPER);
            DrawingBook book = app.book;
            if (book == null) return;
            int w = dp(layout.thumbWidth), h = dp(layout.thumbHeight);
            for (int k = 0; k < layout.visible; k++) {
                int slot = slotAt(k);
                if (slot >= slots(book)) break;
                int i = place(book, slot);
                float left = start() + k * dp(layout.thumbWidth + layout.stripGap);
                RectF box = new RectF(left, 0, left + w, h);
                if (i == ADD) { outline(canvas, box, dp(Theme.BASE), Theme.GREY_600, true); plus(canvas, box); continue; }
                boolean current = i == viewing;
                float stroke = dp(current ? Theme.RULE : Theme.BASE);
                RectF inside = new RectF(box.left + stroke, box.top + stroke, box.right - stroke, box.bottom - stroke);
                drawPlace(canvas, book, i, inside);
                outline(canvas, box, stroke, current ? Theme.INK : Theme.GREY_400, false);
                Boolean on = starred(i);
                if (on != null) drawStar(canvas, box, on);
                if (current) canvas.drawRect(box.left, h + dp(NotebookLayout.TAB_GAP), box.right, h + dp(NotebookLayout.TAB_GAP + NotebookLayout.TAB), ink);
            }
        }
    }

    /** Whether page {@code place}'s preview shows its star filled (null: no star, on a cover or in Favourites itself). */
    private Boolean starred(int place) {
        DrawingBook book = app.book;
        if (book == null || place < 0 || place >= count(book) || NotebookStrip.favourites(app.drawingName)) return null;
        return held(book.fave(book.stacks().first(place)));
    }
    /**
     * The marks Favourites holds, read once each time a notebook's pages show (null until read), then kept as this
     * screen's stars change them: a copy deleted inside Favourites leaves its page's star empty.
     */
    private java.util.Set<Integer> favourites;
    /** Whether a page marked {@code fave} has its copy in Favourites. */
    boolean held(int fave) { return NotebookStrip.filled(fave, favourites); }
    /** Favourites now holds the copy marked {@code fave}, or no longer does. */
    void held(int fave, boolean in) { if (favourites != null) { if (in) favourites.add(fave); else favourites.remove(fave); } }
    private void readFavourites() {
        favourites = null;
        if (NotebookStrip.favourites(app.drawingName)) return;
        app.store.favouriteMarks((marks, error) -> app.runOnUiThread(() -> {
            if (marks == null || mode != Mode.PAGES) return;
            boolean changed = app.book != null && !marks.equals(favourites);
            favourites = marks;
            if (changed) starChanged();
        }));
    }
    /** Favourites' star in {@code box}'s upper right corner, on a paper patch so it shows over a drawing: filled when {@code on}. */
    private void drawStar(Canvas canvas, RectF box, boolean on) {
        int icon = dp(NotebookLayout.STAR_ICON), inset = (dp(NotebookLayout.starTarget(layout.thumbWidth)) - icon) / 2;
        int right = Math.round(box.right) - inset, top = Math.round(box.top) + inset;
        starPaper.setColor(Theme.PAPER);
        canvas.drawRect(right - icon - dp(2), top - dp(2), right + dp(2), top + icon + dp(2), starPaper);
        if (stars[0] == null) { stars[0] = app.getDrawable(R.drawable.ic_ph_star); stars[1] = app.getDrawable(R.drawable.ic_ph_star_fill); }
        Drawable star = stars[on ? 1 : 0];
        if (star == null) return;
        star.setBounds(right - icon, top, right, top + icon);
        star.draw(canvas);
    }
    private final Paint starPaper = new Paint();
    /** The star's icons, outline and filled, once loaded. */
    private final Drawable[] stars = new Drawable[2];
    private static final int STAR_ACTION = View.generateViewId();
    /** A page's star changed: its preview's star drawn again, once (the strip alone; nothing else on the screen changed). */
    void starChanged() { if (mode == Mode.PAGES && strip != null) strip.invalidate(); }
    /** Asks before Remove from Favourites: its copy there goes, and that can't be undone. */
    void confirmUnstar(Runnable remove) {
        app.showDialog(new AlertDialog.Builder(app).setTitle(NotebookStrip.UNSTAR + "?").setMessage("This can't be undone.")
                .setPositiveButton("Remove", (d, w) -> { if (!app.busy()) remove.run(); })
                .setNegativeButton("Cancel", null));
    }
    /** Asks before deleting page {@code index}: it can't be undone. */
    void confirmDelete(int index, Runnable delete) {
        app.showDialog(new AlertDialog.Builder(app).setTitle("Delete page " + (index + 1) + "?").setMessage("This can't be undone.")
                .setPositiveButton("Delete", (d, w) -> { if (!app.busy()) delete.run(); })
                .setNegativeButton("Cancel", null));
    }

    /** Read Handwriting went on or off (or its download failed): the shelf's Search shown or not, an open search closed. */
    void searchChanged() {
        search.changed();
        if (mode == Mode.SHELF) build();
    }
    void close() { worker.shutdownNow(); bitmaps.evictAll(); crisps.evictAll(); covers.evictAll(); previews.evictAll(); }
}
