package io.github.mpdairy.monopaint;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.JSExport;

/**
 * The Manta's notebooks on a live page (scripts/live-probe/books.js, Share on Wi-Fi) through {@link WebEngine#books}: the
 * shelf laid out as the Manta's ({@link NotebookLayout}, {@link NotebookStrip}), Export Page's sizes ({@link PageExport})
 * and the words books.js shows. The browser keeps no notebooks of its own (the browser app's /app, cut 2026-11-05).
 */
public final class WebBooks {
    static final WebBooks ONE = new WebBooks();
    private WebBooks() {}

    /** Words the browser adds (docs/TEXT-STYLE.md): the shelf's Import, Export Now's two copies, its notes. */
    static final String IMPORT = "Import", NOTEBOOK_FILE = "Notebook File", UNREADABLE = "Can't read it", TOO_LARGE = "Too large";
    /** A desktop's drop hint (live.js dropFiles), what a drop does · what it takes: on the drawing page, the Manta's shelf. */
    static final String DROP_PLACE = "Drop to Place · picture or text", DROP_IMPORT = "Drop to Import · PDFs, pictures";

    private static LocalDate day(double epochDay) { return epochDay < 0 ? null : LocalDate.ofEpochDay((long) epochDay); }

    // ---- The shelf ----

    /** A page's height over its width on this {@code w × h} screen: the drawing screen's page beside its bar ({@code NotebookScreens.pageAspect}). */
    private static double aspect(int w, int h) { DrawLayout d = new DrawLayout(w, h); return d.pageHeight() / (double) d.pageWidth(); }
    private static WebEngine.J sizes(WebEngine.J j, NotebookLayout l) {
        return j.k("l").o().k("margin").v(l.margin).k("gap").v(l.gap).k("bar").v(NotebookLayout.BAR).k("button").v(NotebookLayout.BUTTON).k("compact").v(l.compact)
                .k("columns").v(l.columns).k("rows").v(l.rows).k("tileWidth").v(l.tileWidth).k("coverHeight").v(l.coverHeight).k("tileHeight").v(l.tileHeight)
                .k("columnGap").v(l.columnGap).k("rowGap").v(l.rowGap).k("nameLines").v(l.nameLines).k("countLines").v(NotebookLayout.COUNT_LINES).k("underCover").v(NotebookLayout.UNDER_COVER)
                .k("pageWidth").v(l.pageWidth).k("pageHeight").v(l.pageHeight).k("spreadHeight").v(l.spreadHeight).k("thumbWidth").v(l.thumbWidth).k("thumbHeight").v(l.thumbHeight)
                .k("stripGap").v(l.stripGap).k("visible").v(l.visible).k("tab").v(NotebookLayout.TAB).k("tabGap").v(NotebookLayout.TAB_GAP).k("pageBar").v(l.pageBar).k("pageButton").v(l.pageButton)
                .k("pageNumber").v(DrawLayout.pageNumber()).k("labelled").v(l.labelled).k("backWidth").v(NotebookLayout.backWidth()).k("barGap").v(NotebookLayout.BAR_GAP)
                .k("open").v(NotebookLayout.actionWidth(NotebookLayout.OPEN, true)).k("coverButton").v(NotebookLayout.coverWidth(l.labelled))
                .k("delete").v(NotebookLayout.actionWidth(NotebookLayout.DELETE, l.labelled)).k("actionPad").v(NotebookLayout.ACTION_PAD)
                .k("pagesBar").v(NotebookLayout.PAGES_BAR).k("pagesTop").v(l.pagesTop).k("caret").v(NotebookLayout.CARET).k("caretIcon").v(NotebookLayout.CARET_ICON).k("titleLines").v(NotebookLayout.TITLE_LINES).e();
    }
    /**
     * The shelf on a {@code w × h} dp screen, as JSON: its layout, page {@code at} of its pages, and its tiles (New
     * Notebook first, on the first page), each with its name as shown and "24 pages · Oct 17". {@code list}: a line a
     * notebook, the last changed first: its name, its file's last change (epoch day), its pages, 1 when it didn't read.
     */
    @JSExport public String shelf(int w, int h, String list, int at, double today) {
        NotebookLayout l = new NotebookLayout(w, h, aspect(w, h));
        List<String[]> all = new ArrayList<>();
        for (String line : list.split("\n")) if (!line.isEmpty()) all.add(line.split("\t", -1));
        int pages = l.shelfPages(all.size()), page = Math.max(0, Math.min(at, pages - 1));
        int[] range = l.shelfPage(page, all.size());
        LocalDate now = day(today);
        WebEngine.J j = sizes(new WebEngine.J().o(), l).k("page").v(page).k("pages").v(pages).k("pageWords").v((page + 1) + " / " + pages);
        j.k("w").o().k("heading").v("Notebooks").k("new").v(NotebookNames.NEW).k("newer").v("Newer notebooks").k("older").v("Older notebooks").k("import").v(IMPORT).k("job").v("").e();
        j.k("tiles").a();
        if (page == 0) j.o().k("name").v(NotebookNames.NEW).k("said").v(NotebookNames.NEW).e();
        for (int i = range[0]; i < range[0] + range[1]; i++) {
            String[] n = all.get(i);
            String shown = DrawingNames.shown(DrawingFiles.name(n[0]), now);
            String about = "1".equals(n[3]) ? "Can't be read" : NotebookStrip.about(Integer.parseInt(n[2]), day(Double.parseDouble(n[1])), now);
            j.o().k("path").v(n[0]).k("name").v(shown).k("about").v(about).k("said").v(shown + ", " + about).e();
        }
        return j.e().e().toString();
    }
    /** The size menu after Export Page's {@code choice} for a {@code width × height} page, as the Manta's ({@link PageExport#sizeWords}): a row an {@link ExportSize}, by index; none for the PDF. */
    @JSExport public String sizeWords(int choice, int width, int height) {
        WebEngine.J j = new WebEngine.J().a();
        String c = PageExport.CHOICES[choice];
        if (PageExport.sized(c)) for (String words : PageExport.sizeWords(c, width, height)) j.v(words);
        return j.e().toString();
    }
    /** The words books.js shows: the Manta's shelf, its page bar, Export Page's copies, a file sent up and its notes. */
    @JSExport public String menuWords() {
        WebEngine.J j = new WebEngine.J().o().k("importable").a();
        for (String ending : PictureImport.IMPORTABLE) j.v(ending);
        return j.e().k("unreadable").v(UNREADABLE).k("back").v("Back").k("previous").v("Previous page").k("next").v("Next page").k("saveFailed").v("Save failed")
                .k("exportPage").v(LibraryCard.EXPORT_PAGE).k("pageChoices").a().v(PageExport.CHOICES[0]).v(PageExport.CHOICES[1]).v(PageExport.CHOICES[2]).e()
                .k("bookChoices").a().v(PageExport.PDF).v(NOTEBOOK_FILE).e().k("notConnected").v("Not connected")
                // A file sent up to the Manta (books.js; LiveBooks.add): the shelf's Import.
                .k("import").v(IMPORT).k("dropImport").v(DROP_IMPORT).k("tooLarge").v(TOO_LARGE).k("mostAdded").v(LiveBooks.MOST_ADDED).e().toString();
    }
}
