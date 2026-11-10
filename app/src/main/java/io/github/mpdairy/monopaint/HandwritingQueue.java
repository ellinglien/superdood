package io.github.mpdairy.monopaint;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Read Handwriting's reading (docs/superpowers/specs/2026-11-09-read-handwriting-design.md, "When"): one thread at the
 * lowest priority reads, notebook by notebook, the pages whose CRC its transcript hasn't got, drops the pages the
 * notebook no longer has, and keeps the transcript (one file a notebook in {@link #folder}). A saved notebook is queued
 * ({@link #saved}); switching Read Handwriting on queues them all ({@link #readAll}); switching it off deletes every
 * transcript and the downloaded reader ({@link #off}). Results come back on the main thread. One queue lives per
 * activity and stops with it ({@link #close}). Urgent jobs ({@link #urgent}) go before the reading, which stops its
 * notebook between pages to let them by.
 */
final class HandwritingQueue {
    private static final String TAG = "ReadHandwriting";
    private final DocumentStore store;
    private final File folder;
    private final Context context;
    /** Made on the queue's thread the first time it's needed, so ML Kit never starts while Read Handwriting is off. */
    private HandwritingReader reader;
    private final Handler work, main = new Handler(Looper.getMainLooper());
    private final Set<String> waiting = new LinkedHashSet<>();
    private volatile boolean on;
    private static final int URGENT = 1;

    HandwritingQueue(Context context, DocumentStore store, boolean on) {
        this.context = context.getApplicationContext();
        this.store = store;
        this.folder = new File(context.getFilesDir(), "transcripts");
        this.on = on;
        HandlerThread thread = new HandlerThread("Read Handwriting", Process.THREAD_PRIORITY_LOWEST);
        thread.start();
        work = new Handler(thread.getLooper());
    }

    File transcriptFile(String path) { return Transcript.file(folder, path); }

    /** Switched on: the reader downloaded, then every notebook read, newest first; {@code failed} on the main thread if the download fails (also the start-up's, after a download cut short). */
    void on(Consumer<Exception> failed) {
        on = true;
        work.post(() -> {
            boolean[] had = {false}; // a reader already here is never wiped because an offline retry failed
            try { had[0] = reader().downloaded(); if (!had[0]) reader().download(); }
            catch (Exception e) {
                Log.w(TAG, "download", e);
                on = false;
                if (!had[0]) try { reader().delete(); } catch (Exception d) { Log.w(TAG, "delete", d); } // cancels a half-done stored download
                main.post(() -> failed.accept(e)); return; }
            readAll();
        });
    }
    /** Switched off: nothing more read; every transcript and the downloaded reader deleted. */
    void off() {
        on = false;
        work.removeCallbacksAndMessages(null);
        synchronized (waiting) { waiting.clear(); }
        work.post(() -> {
            File[] files = folder.listFiles();
            if (files != null) for (File f : files) f.delete();
            try { reader().delete(); } catch (Exception e) { Log.w(TAG, "delete", e); }
        });
    }
    /** The activity is going: nothing more read, the thread ends. */
    void close() { on = false; work.getLooper().quitSafely(); }
    /** {@code job} on the queue's thread before any reading; the reading stops between pages to let it by. */
    void urgent(Runnable job) {
        Message m = Message.obtain(work, job);
        m.what = URGENT;
        work.sendMessageAtFrontOfQueue(m);
    }
    /** Notebook {@code path} was saved: read what changed. */
    void saved(String path) { if (on) queue(path); }
    void readAll() {
        store.notebooks((list, error) -> {
            if (list == null) return;
            List<DocumentStore.Notebook> newest = new ArrayList<>(list);
            java.util.Collections.sort(newest, (a, b) -> Long.compare(b.modified, a.modified));
            for (DocumentStore.Notebook n : newest) queue(n.path);
        });
    }
    /** A notebook deleted or renamed: its transcript goes or follows. */
    void deleted(String path) { urgent(() -> transcriptFile(path).delete()); }
    void renamed(String from, String to) { urgent(() -> transcriptFile(from).renameTo(transcriptFile(to))); }

    /**
     * The shelf's {@code notebooks} (newest first) searched for {@code query}, and how many of their pages aren't read yet,
     * in one pass, on the main thread (the spec's "Search on the shelf"); given up between notebooks once {@code stale}.
     */
    void search(List<DocumentStore.Notebook> notebooks, String query, BooleanSupplier stale, BiConsumer<List<ShelfSearch.Found>, Integer> answer) {
        urgent(() -> {
            String[] words = Transcript.words(query);
            List<ShelfSearch.Found> out = new ArrayList<>();
            int unread = 0;
            for (DocumentStore.Notebook n : notebooks) {
                if (stale.getAsBoolean()) return;
                BookCodec.Pages pages = null;
                try {
                    Transcript t = Transcript.read(transcriptFile(n.path));
                    // Nothing read yet: its file not opened, its pages the shelf's count of them (a stack one page).
                    // shortcut: a stack's layers count one page here and each page once some are read; fine for a note.
                    if (t.pages().isEmpty()) { if (!n.unreadable) unread += n.pages; continue; }
                    pages = BookCodec.openPages(store.notebookFile(n.path));
                    // A page's number as the strip numbers it: its stack's.
                    PageStacks stacks = TracingPaper.stacks(pages.covers.tracing, pages.pages.size());
                    for (int i = 0; i < pages.pages.size(); i++) {
                        PackedPage p = pages.pages.get(i);
                        if (!t.has(p.crc)) { unread++; continue; }
                        if (words.length == 0) continue;
                        for (Transcript.Line l : t.lines(p.crc)) {
                            String g = Transcript.match(l, words);
                            if (g != null) out.add(new ShelfSearch.Found(n.path, i, p.crc, stacks.of(i) + 1, g, l.box));
                        }
                    }
                } catch (Exception e) { Log.w(TAG, "search " + n.path, e); }
                finally { release(pages); }
            }
            int count = unread;
            main.post(() -> { if (!stale.getAsBoolean()) answer.accept(out, count); });
        });
    }

    /** Closes the file handle all of {@code pages}' pages share. */
    private static void release(BookCodec.Pages pages) {
        if (pages != null && pages.pages != null && !pages.pages.isEmpty() && pages.pages.get(0).file() != null) pages.pages.get(0).file().close();
    }

    /** On the queue's thread only. */
    private HandwritingReader reader() { if (reader == null) reader = new HandwritingReader(context); return reader; }

    private void queue(String path) {
        synchronized (waiting) { if (!waiting.add(path)) return; }
        work.post(() -> {
            synchronized (waiting) { waiting.remove(path); }
            if (on) read(path);
        });
    }

    /** Runs on the queue's thread. */
    private void read(String path) {
        Cores.quiet(true);
        BookCodec.Pages pages = null;
        try {
            if (!reader().downloaded()) { Log.w(TAG, "reader not downloaded; not reading " + path); return; }
            pages = BookCodec.openPages(store.notebookFile(path));
            File file = transcriptFile(path);
            Transcript t = Transcript.read(file);
            Set<Long> crcs = new HashSet<>();
            boolean changed = false;
            for (int i = 0; i < pages.pages.size() && on; i++) {
                PackedPage page = pages.pages.get(i);
                crcs.add(page.crc);
                if (t.has(page.crc)) continue;
                if (work.hasMessages(URGENT)) { // let it by: keep what's read (no keepOnly: the rest isn't seen), come back
                    if (changed) { folder.mkdirs(); t.write(file); }
                    queue(path);
                    return;
                }
                try { // one bad page doesn't stop the rest
                    DocumentCodec.PageLog log;
                    // Not under ONE_PAGE: log() keeps no page's pixels, and a save waits on that lock (this thread is the lowest priority).
                    log = DocumentCodec.log(page.bytes());
                    PageWriting.Tones tones = tones(pages, i, log);
                    List<Transcript.Line> lines = new ArrayList<>();
                    for (WritingLines.Line line : WritingLines.of(PageWriting.strokes(log.log.strokes(), tones)))
                        lines.add(new Transcript.Line(line.box, reader().read(line)));
                    t.put(page.crc, lines);
                    changed = true;
                } catch (Exception | OutOfMemoryError e) { Log.w(TAG, "page " + i + " of " + path, e); }
            }
            if (!on) return;
            int before = t.pages().size();
            t.keepOnly(crcs);
            if (changed || t.pages().size() != before) { folder.mkdirs(); t.write(file); }
        } catch (Exception | OutOfMemoryError e) {
            Log.w(TAG, "read " + path, e);
        } finally {
            release(pages);
            Cores.quiet(false);
        }
    }

    /** Page {@code i}'s picture as tones in page px, its paper lifted to 255; null (no erase check) when the file has none for it. */
    private static PageWriting.Tones tones(BookCodec.Pages pages, int i, DocumentCodec.PageLog log) {
        // shortcut: without a page picture the erase check is skipped (an erased word stays findable until the page changes again).
        if (pages.found == null || i >= pages.found.pictures.length || pages.found.pictures[i] == null) return null;
        try {
            PagePicture p = PagePicture.decode(pages.found.pictures[i]);
            float sx = p.width / (float) log.width, sy = p.height / (float) log.height;
            int[] counts = new int[256];
            for (int y = 0; y < p.height; y++) for (int x = 0; x < p.width; x++) counts[p.tone(x, y)]++;
            int paper = 0;
            for (int g = 1; g < 256; g++) if (counts[g] > counts[paper]) paper = g;
            PageWriting.Tones raw = (x, y) -> {
                int px = (int) (x * sx), py = (int) (y * sy);
                return px < 0 || py < 0 || px >= p.width || py >= p.height ? 255 : p.tone(px, py);
            };
            return PageWriting.onPaper(raw, paper);
        } catch (java.io.IOException e) { return null; }
    }
}
