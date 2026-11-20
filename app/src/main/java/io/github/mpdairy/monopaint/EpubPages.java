package io.github.mpdairy.monopaint;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * An EPUB's pages for Open File ({@link PdfImport#runEpub}; docs/superpowers/specs/2026-11-17-open-file-design.md): the
 * book ({@link EpubBook}) unzipped into the cache, each spine file laid out by a {@link WebView} that is never on the
 * screen, in CSS columns a page wide, its own fonts and pictures, one reading size, black text; each column drawn as a
 * page with a margin round it. {@link PdfImport} draws them into greys and packs them as a PDF's.
 * <p>Threads: {@link #open}, {@link #draw}, {@link #chapters} and {@link #close} run on the import's worker thread. Every
 * WebView call is posted to the main thread ({@link #onMain}), the worker waiting at most {@link #WAIT_SECONDS} a step: a
 * spine file that doesn't load in time has no pages. Android only (not in the desktop checks).
 * <p>The first pass ({@link #layOut}) loads every spine file once to count its pages and find where its chapters start,
 * saying how far it is ({@link PdfImport.Progress#layingOut}) and stopping at a file's end once cancelled;
 * drawing loads each again, in order, and shows one column at a time by moving the body left (a CSS transform, so the
 * WebView itself never scrolls and its drawing needs no scroll offset).
 */
final class EpubPages implements PdfImport.Pages {
    /**
     * The text size in CSS px: the WebView's default 16 px × 1.15, the one reading size (the owner, 2026-11-17: "looks
     * good" to the design's "one reading size"; a choice of text size left out of it).
     */
    static final float READING_PX = 16 * 1.15f;
    /** The page's margin all round, a part of its width. */
    static final float MARGIN = 0.06f;
    /** The longest a step on the main thread is waited for (a load and its first frame, a page shown and drawn). */
    static final int WAIT_SECONDS = 20;
    /** The most an EPUB unzips to; past it, not read (a zip bomb). */
    static final long MOST_UNZIPPED = 1L << 30;

    private final Activity activity;
    private final File temp, dir;
    private final EpubBook book;
    /** The page's margin and the WebView's size, in px; the WebView's size in CSS px. */
    private final int margin, viewWidth, viewHeight;
    private final float cssWidth, cssHeight;
    /**
     * The gap between columns, CSS px (a page's margin), and so a page's step: without it a line or a glyph reaching a
     * column's edge showed as slivers down the next page's left edge (the owner, 2026-11-20: "a bit of a strange
     * artifact ... along the left edge where the text would be").
     */
    private final float gap, pageStep;
    /** Each spine file's first page and its count (0: it didn't load); each chapter's page (-1: none). */
    private final int[] first, counts, chapterPages;
    private int total;
    /** The spine file the WebView holds, styled; -1 for none. Worker thread. */
    private int current = -1;
    /** Main thread only. */
    private WebView web;
    /** The file loading, what follows its load, and whether it failed. Set on the main thread before each load. */
    private volatile String loading;
    private volatile Runnable finished;
    private volatile boolean failed;

    private EpubPages(Activity activity, File temp, File dir, EpubBook book, int width, int height) {
        this.activity = activity; this.temp = temp; this.dir = dir; this.book = book;
        margin = Math.round(MARGIN * width);
        viewWidth = width - 2 * margin; viewHeight = height - 2 * margin;
        float density = activity.getResources().getDisplayMetrics().density;
        cssWidth = viewWidth / density; cssHeight = viewHeight / density;
        gap = margin / density; pageStep = cssWidth + gap;
        first = new int[book.spine.size()]; counts = new int[book.spine.size()];
        chapterPages = new int[book.chapters.size()];
        Arrays.fill(chapterPages, -1);
    }

    /**
     * The EPUB at {@code uri} as {@code width × height} pages, laid out in {@code activity}, {@code progress} hearing its
     * first pass; cut short there once {@code cancelled}. Throws IOException with {@link EpubBook#LOCKED} for DRM, another
     * for anything unreadable. On the worker thread, never the main one.
     */
    static PdfImport.Pages open(Activity activity, Uri uri, int width, int height, PdfImport.Progress progress, java.util.function.BooleanSupplier cancelled) throws IOException {
        File temp = File.createTempFile("open", ".epub", activity.getCacheDir());
        File dir = new File(activity.getCacheDir(), "epub-" + UUID.randomUUID());
        EpubPages pages = null;
        try {
            try (InputStream in = activity.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(temp)) {
                if (in == null) throw new IOException(PictureImport.UNREADABLE);
                byte[] buffer = new byte[1 << 16];
                for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
            }
            EpubBook book;
            try (ZipFile zip = new ZipFile(temp)) { book = EpubBook.read(zip); unzip(zip, dir); }
            pages = new EpubPages(activity, temp, dir, book, width, height);
            pages.layOut(progress, cancelled);
            return pages;
        } catch (IOException | RuntimeException failed) {
            if (pages != null) pages.close(); else { delete(dir); temp.delete(); }
            throw failed;
        }
    }

    /** Unzips {@code zip} into {@code dir}, refusing an entry whose path leaves it (zip slip) and more than {@link #MOST_UNZIPPED}. */
    private static void unzip(ZipFile zip, File dir) throws IOException {
        String inside = dir.getCanonicalPath() + File.separator;
        long left = MOST_UNZIPPED;
        byte[] buffer = new byte[1 << 16];
        for (Enumeration<? extends ZipEntry> all = zip.entries(); all.hasMoreElements(); ) {
            ZipEntry entry = all.nextElement();
            File to = new File(dir, entry.getName());
            if (!to.getCanonicalPath().startsWith(inside)) throw new IOException(PictureImport.UNREADABLE);
            if (entry.isDirectory()) { to.mkdirs(); continue; }
            to.getParentFile().mkdirs();
            try (InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(to)) {
                for (int n; (n = in.read(buffer)) > 0; ) {
                    if ((left -= n) < 0) throw new IOException(PictureImport.UNREADABLE);
                    out.write(buffer, 0, n);
                }
            }
        }
    }

    /** The first pass: the WebView made, then each spine file loaded to count its pages and place its chapters. */
    private void layOut(PdfImport.Progress progress, java.util.function.BooleanSupplier cancelled) throws IOException {
        if (!onMain(this::create)) throw new IOException(PictureImport.UNREADABLE);
        for (int s = 0; s < book.spine.size(); s++) {
            if (cancelled.getAsBoolean()) return;
            progress.layingOut(s, book.spine.size());
            List<Integer> here = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (int c = 0; c < book.chapters.size(); c++) if (book.chapters.get(c).spine == s) { here.add(c); ids.add(book.chapters.get(c).fragment); }
            String js = measure(new JSONArray(ids));
            String[] read = {null};
            int spine = s;
            boolean ok = onMain(done -> load(spine, () -> web.evaluateJavascript(js, result -> { read[0] = result; done.run(); }))) && !failed;
            // [its pages, then each chapter's page in it]; null when it didn't load or measure.
            int[] at = ok ? numbers(read[0], 1 + here.size()) : null;
            first[s] = total;
            counts[s] = at == null ? 0 : at[0];
            for (int k = 0; at != null && k < here.size(); k++) chapterPages[here.get(k)] = total + at[1 + k];
            total += counts[s];
            current = at == null ? -1 : s;
            Log.i(ProbeActivity.TAG, "EPUB " + book.spine.get(s) + ": " + (at == null ? "not laid out (" + (ok ? "unmeasured" : "timed out or failed") + ")" : counts[s] + " pages"));
            // Past a notebook's file pages the rest is never drawn: no need to lay it out.
            if (total > PictureImport.MOST_PDF_PAGES) break;
        }
    }

    /** Makes the WebView, on the main thread: in the activity's content, laid out and drawn as any view but never seen. */
    private void create(Runnable done) {
        WebView w = new WebView(activity);
        WebSettings settings = w.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true); // the book's own files, unzipped in the cache
        settings.setBlockNetworkLoads(true); // a book never reaches the network
        settings.setSupportZoom(false); settings.setBuiltInZoomControls(false); settings.setDisplayZoomControls(false);
        settings.setTextZoom(100); // the system's font scale left out: READING_PX is the size
        w.setBackgroundColor(Color.WHITE);
        w.setVerticalScrollBarEnabled(false); w.setHorizontalScrollBarEnabled(false);
        w.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                Runnable then = finished;
                if (then != null && Uri.parse(url).getPath() != null && Uri.parse(url).getPath().equals(loading)) { finished = null; then.run(); }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) failed = true;
            }
            // Only our loadUrl goes anywhere: a book's link, script or refresh can't take it to another page.
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
        });
        // Ten screens to the left: laid out and drawn (its frames come, its page lays out) but never on the e-ink panel.
        w.setTranslationX(-10f * (viewWidth + 2 * margin));
        ((ViewGroup) activity.findViewById(android.R.id.content)).addView(w, new FrameLayout.LayoutParams(viewWidth, viewHeight));
        // Its size now, not at the next layout pass, so the first page loaded is laid out at it; the pass gives the same.
        w.measure(View.MeasureSpec.makeMeasureSpec(viewWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(viewHeight, View.MeasureSpec.EXACTLY));
        w.layout(0, 0, viewWidth, viewHeight);
        web = w;
        done.run();
    }

    /** Loads spine file {@code s} on the main thread, styled as pages ({@link #style}), then {@code then} once that has reached a frame. */
    private void load(int s, Runnable then) {
        File file = new File(dir, book.spine.get(s));
        failed = false;
        loading = file.getPath();
        finished = () -> web.evaluateJavascript(style(), styled -> frame(then));
        web.loadUrl(Uri.fromFile(file).toString());
    }

    /** {@code then} once what the page holds now has reached a frame (WebView's visual state), on the main thread. */
    private void frame(Runnable then) {
        web.postVisualStateCallback(0, new WebView.VisualStateCallback() { @Override public void onComplete(long id) { then.run(); } });
    }

    /**
     * The page style: columns a page wide and a page high, so the text runs on sideways a page at a time; the reading
     * size; pictures no larger than a page; black text on white whatever the book's own style says.
     */
    private String style() {
        String css = "html,body{margin:0!important;padding:0!important;background:#fff!important}"
                + "body{column-width:" + cssWidth + "px!important;column-gap:" + gap + "px!important;column-fill:auto!important;height:" + cssHeight + "px!important;"
                + "overflow:visible!important;overflow-wrap:break-word!important}html,body{font-size:" + READING_PX + "px!important}"
                + "img,svg{max-width:100%!important;max-height:" + cssHeight + "px!important;object-fit:contain}"
                + "*{color:#000!important;-webkit-text-fill-color:#000!important}";
        return "(function(){var s=document.createElement('style');s.textContent=" + JSONObject.quote(css) + ";(document.head||document.documentElement).appendChild(s);})()";
    }

    /**
     * The script that measures a loaded file: [its pages (at least 1), then for each id in {@code ids} the page its
     * element lands on (an id missing or null: the first)]. Off by a pixel or two of rounding is not another page.
     */
    private String measure(JSONArray ids) {
        return "(function(W,ids,G){var b=document.body;if(!b)return[1];"
                + "var n=Math.max(1,Math.ceil((Math.max(b.scrollWidth,document.documentElement.scrollWidth)+G-2)/W)),out=[n];"
                + "for(var i=0;i<ids.length;i++){var e=ids[i]==null?null:(document.getElementById(ids[i])||document.getElementsByName(ids[i])[0]);"
                + "var p=e?Math.floor((e.getBoundingClientRect().left+window.scrollX+1)/W):0;out.push(Math.max(0,Math.min(n-1,p)));}"
                + "return out;})(" + pageStep + "," + ids + "," + gap + ")";
    }

    /** {@code n} whole numbers from a script's JSON result; null when it isn't that. */
    private static int[] numbers(String json, int n) {
        try {
            JSONArray read = new JSONArray(json);
            if (read.length() != n) return null;
            int[] out = new int[n];
            for (int i = 0; i < n; i++) out[i] = read.getInt(i);
            return out[0] > 0 ? out : null;
        } catch (Exception unread) { return null; }
    }

    @Override public int count() { return total; }

    /** Page {@code i}: its spine file loaded if it isn't already, its column moved into view, the WebView drawn inside the margin. */
    @Override public void draw(int i, Bitmap drawn) throws IOException {
        drawn.eraseColor(Color.WHITE);
        int s = book.spine.size() - 1;
        while (s >= 0 && (counts[s] == 0 || first[s] > i)) s--;
        if (s < 0) return;
        if (s != current) {
            int spine = s;
            current = -1;
            // A step that timed out would leave a white page to be saved: the import fails, nothing is kept.
            if (!onMain(done -> load(spine, done)) || failed) throw new IOException(PictureImport.UNREADABLE);
            current = s;
        }
        String show = "document.body.style.transform='translateX(-" + (i - first[s]) * pageStep + "px)'";
        // Drawn on the main thread into the worker's bitmap: once the wait is over (in time or not), never again.
        boolean[] open = {true};
        boolean drawnInTime = onMain(done -> web.evaluateJavascript(show, shown -> frame(() -> {
            synchronized (open) {
                if (open[0]) {
                    Canvas canvas = new Canvas(drawn);
                    canvas.translate(margin, margin);
                    canvas.clipRect(0, 0, viewWidth, viewHeight);
                    // A software canvas: WebView draws what is in its view (the column moved there), at no scroll. Should
                    // pages come out blank on the Manta, try WebView.enableSlowWholeDocumentDraw() before the first WebView.
                    web.draw(canvas);
                }
            }
            done.run();
        })));
        synchronized (open) { open[0] = false; }
        if (!drawnInTime) throw new IOException(PictureImport.UNREADABLE); // not a white page saved
    }

    /** Each chapter at the page it starts on, from the first pass; one on a page not written is left out. */
    @Override public List<NotebookOptions.Contents.Chapter> chapters(List<long[]> written) {
        List<NotebookOptions.Contents.Chapter> out = new ArrayList<>();
        for (int c = 0; c < book.chapters.size(); c++) {
            int page = chapterPages[c];
            EpubBook.Chapter chapter = book.chapters.get(c);
            if (page >= 0 && page < written.size()) out.add(new NotebookOptions.Contents.Chapter(written.get(page)[2], chapter.depth, chapter.title));
        }
        return out;
    }

    /** The WebView taken away and destroyed (main thread), the unzipped book and its copy deleted. */
    @Override public void close() {
        try {
            onMain(done -> {
                if (web != null) {
                    if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
                    web.destroy();
                    web = null;
                }
                done.run();
            });
        } catch (IOException interrupted) { /* the files go all the same */ }
        finally { delete(dir); temp.delete(); }
    }

    /**
     * Runs {@code step} on the main thread, handing it a {@code done} to call when it has finished (maybe later, from a
     * callback); waits at most {@link #WAIT_SECONDS}. False when it timed out or threw.
     */
    private boolean onMain(Consumer<Runnable> step) throws IOException {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] threw = {false};
        activity.runOnUiThread(() -> {
            try { step.accept(latch::countDown); }
            catch (RuntimeException error) { Log.e(ProbeActivity.TAG, "EPUB step failed", error); threw[0] = true; latch.countDown(); }
        });
        try {
            boolean inTime = latch.await(WAIT_SECONDS, TimeUnit.SECONDS);
            if (!inTime) Log.w(ProbeActivity.TAG, "EPUB step timed out");
            return inTime && !threw[0];
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new InterruptedIOException(); }
    }

    private static void delete(File file) {
        File[] in = file.listFiles();
        if (in != null) for (File f : in) delete(f);
        file.delete();
    }
}
