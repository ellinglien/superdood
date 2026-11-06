package io.github.mpdairy.monopaint;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Draw Together (the menu's row; docs/superpowers/specs/2026-11-01-manta-host-design.md): through superdood.org's relay
 * ({@link LiveHost#RELAY}) when it answers, so a browser on any network (a phone on cell data) draws on the page with the
 * Manta; else the app's own live relay ({@link LiveHost}) on the Wi-Fi, for a browser on it (an iPad, a computer). The
 * way is picked once, as sharing starts, and kept till it stops: one row, no choice to make. Through superdood.org the key is
 * {@link LiveHost#RELAY_KEY} letters and the window shows the pickup's code and the QR code, never the key; picking
 * waits up to 4 s for superdood.org to answer before the window opens. On only while the app is open (one of its screens
 * started): off while it is away and on again as it comes
 * back, with the same key and port; off for good when the drawing screen closes or Stop Sharing is tapped, and on again
 * with the same key when the app stopped otherwise (an install, a crash) and starts again soon ({@link #restore}). Its window shows the link large and as a QR
 * code, and under them the live page's status ({@link LiveProbe#status}), changed as it changes. Starting and stopping
 * the server are off the main thread, as {@link LiveProbe}'s network work is.
 */
final class LiveShare implements Application.ActivityLifecycleCallbacks {
    static final String SHARE = "Draw Together", STOP = "Stop Sharing", CLOSE = "Close", WIFI_OFF = "Wi-Fi off",
            SHARING_OFF = "Sharing off", FAILED = "Sharing failed", WHERE = "On this Wi-Fi, while the app is open.",
            ONLINE = "Online, while the app is open.";
    private static final String TAG = "LiveShare";
    /**
     * Sharing on when the app stopped without turning it off (an install, a crash, the system's) is on again, same key and
     * port, if the app starts within this: the guests' open pages reconnect. Kept in the app's own preferences.
     */
    static final long KEPT_MS = 2 * 3600_000L;
    private static final String KEPT_KEY = "live_share_key", KEPT_PORT = "live_share_port", KEPT_AT = "live_share_at", KEPT_RELAY = "live_share_relay";
    private final PaintActivity app;
    /** The row's mark: sharing asked for. The server runs while it is and a screen of the app is started. */
    private boolean on;
    private String key;
    /** Sharing through {@link LiveHost#RELAY} (picked at the first start), not this Wi-Fi's own host. */
    private boolean relay;
    /** The port asked for: the first start's again on each start after, so a guest's link keeps working. */
    private int port = LiveHost.PORT;
    private int started, generation;
    private volatile LiveHost host;
    /** Through the relay, while started: the key's cipher (stretched off the main thread), as {@link #host} is the Wi-Fi's. */
    private volatile LiveCrypt relayed;
    /** Starts and stops one at a time, in order: a quick away and back never binds before the last server let go. */
    private final java.util.concurrent.ExecutorService serial = java.util.concurrent.Executors.newSingleThreadExecutor();
    /** Send's files while its window is open, else null ({@link #send}). */
    private volatile LiveSend sending;
    /** Send's window while open: closed with the drawing screen ({@link #off}), which ends its link. */
    private AlertDialog sendDialog;
    /** The open window's status line, or null. */
    private TextView statusLine;

    LiveShare(PaintActivity app) { this.app = app; }

    boolean on() { return on; }
    /** The server running, or null: this tablet's own live connection talks to it ({@link LiveProbe#start}). */
    LiveHost host() { return host; }
    /** Sharing through {@link LiveHost#RELAY} and started: its key's cipher, else null ({@link LiveProbe#start}). */
    LiveCrypt relayed() { return relayed; }

    /** The menu's row: on, with its window once the server is up; on already, the window again. */
    void tapped() {
        if (on) { window(); return; }
        if (LiveHost.lanAddress() == null) { app.message(WIFI_OFF); return; }
        on = true; key = null; started = 1;  // the drawing screen, whose menu this is; the first start makes the key
        app.getApplication().registerActivityLifecycleCallbacks(this);
        startHost(this::window);
    }

    /** Off for good (Stop Sharing, the drawing screen closing). */
    void off() {
        if (sendDialog != null) sendDialog.dismiss();
        if (!on) return;
        on = false; key = null; relay = false; started = 0; port = LiveHost.PORT;
        app.preferences.edit().remove(KEPT_KEY).remove(KEPT_PORT).remove(KEPT_AT).remove(KEPT_RELAY).apply();
        app.getApplication().unregisterActivityLifecycleCallbacks(this);
        stopHost();
        if (app.resumed) { app.live.stop(); app.live.start(); }
    }

    /**
     * The drawing screen made (before it starts): sharing on again with its key and port when the app stopped with it on
     * less than {@link #KEPT_MS} ago; the screen's start starts the server.
     */
    void restore() {
        android.content.SharedPreferences p = app.preferences;
        String k = p.getString(KEPT_KEY, null);
        long at = p.getLong(KEPT_AT, 0), now = System.currentTimeMillis();
        if (on || k == null || now < at || now - at > KEPT_MS) return;
        on = true; key = k; relay = p.getBoolean(KEPT_RELAY, false); port = p.getInt(KEPT_PORT, LiveHost.PORT); started = 0;
        app.getApplication().registerActivityLifecycleCallbacks(this);
        Log.i(TAG, "sharing again with the key of " + (now - at) / 60_000 + " min ago");
    }
    /** Sharing kept for {@link #restore}, as of now. */
    private void keep() {
        if (on) app.preferences.edit().putString(KEPT_KEY, key).putInt(KEPT_PORT, port).putBoolean(KEPT_RELAY, relay).putLong(KEPT_AT, System.currentTimeMillis()).apply();
    }

    private void startHost(Runnable then) {
        int made = ++generation;
        String k = key;
        boolean r = relay;
        int asked = port;
        serial.execute(() -> {
            // The first start picks the way: superdood.org's relay when it answers (any network), else this Wi-Fi's own host.
            boolean through = k == null ? LiveHost.reachable(LiveHost.RELAY) : r;
            String useKey = k != null ? k : LiveHost.newKey(through ? LiveHost.RELAY_KEY : 4);
            LiveHost h = null;
            LiveCrypt c = through ? new LiveCrypt(useKey) : null;
            if (!through) {
                try { h = new LiveHost(asked, useKey, webFiles(), name -> { try { return app.getAssets().open("web/" + name); } catch (IOException e) { return null; } }, books()); }
                catch (IOException e) { Log.w(TAG, "starting: " + e); }
            }
            LiveHost up = h;
            app.runOnUiThread(() -> {
                if (made != generation) { if (up != null) serial.execute(up::stop); return; }
                if (up == null && c == null) { app.message(FAILED); off(); return; }
                key = useKey; relay = through; host = up; relayed = c;
                if (up != null) up.send = sending;  // back from sleep with Send's window open: its link works again
                if (up != null) port = up.port;
                keep();
                Log.i(TAG, through ? "sharing through " + LiveHost.RELAY : "sharing on port " + port);
                if (app.resumed) { app.live.stop(); app.live.start(); }
                if (then != null) then.run();
            });
        });
    }

    private void stopHost() {
        generation++;
        LiveHost h = host;
        host = null; relayed = null;
        if (h != null) serial.execute(h::stop);
    }

    /**
     * The notebooks to a browser ({@link LiveBooks}): the library through the store's thread, the open notebook saved
     * first (its autosave brought forward), so a download holds what this screen shows.
     */
    private LiveBooks books() {
        LiveBooks.Library library = app.store.liveLibrary();
        return new LiveBooks(new LiveBooks.Library() {
            @Override public java.util.List<DrawingFiles.Entry> notebooks() throws IOException { saved(); return library.notebooks(); }
            @Override public BookPictures.Cover cover(String path) throws IOException { return library.cover(path); }
            @Override public byte[] file(String path) throws IOException { saved(); return library.file(path); }
            @Override public String add(String name, byte[] file) throws IOException { return onShelf(library.add(name, file)); }
            @Override public String imported(String name, byte[] file, boolean pdf) throws IOException { return onShelf(importedFile(name, file, pdf)); }
        }, WordsMode::exportTiles);
    }
    /**
     * A picture or PDF sent up made a new notebook as Import's New Notebook makes one ({@link PdfImport}), at the open
     * notebook's page size, in a cover design picked at random; null when it doesn't read (Import's "Can't read it").
     * Waited for: a server's thread.
     */
    private String importedFile(String name, byte[] file, boolean pdf) throws IOException {
        DrawingBook open = app.book;
        if (open == null) return null;
        java.io.File sent = java.io.File.createTempFile("sent", pdf ? ".pdf" : ".picture", app.getCacheDir());
        try {
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(sent)) { out.write(file); }
            java.util.concurrent.CompletableFuture<String> made = new java.util.concurrent.CompletableFuture<>();
            CoverDesign[] designs = CoverDesign.values();
            new PdfImport().run(app, android.net.Uri.fromFile(sent), name, pdf, open.width, open.height, designs[new java.util.Random().nextInt(designs.length)],
                    java.time.LocalDate.now().toEpochDay(), app.store, (done, of) -> {}, (m, error) -> made.complete(m == null ? null : m.path));
            return made.get();
        } catch (java.util.concurrent.ExecutionException e) { return null; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException(); }
        finally { sent.delete(); }
    }
    /** A notebook made from here: the Manta's own shelf, when it shows, read again. */
    private String onShelf(String path) {
        if (path != null) app.runOnUiThread(() -> { if (app.notebooks != null && app.notebooks.onShelf()) app.notebooks.showShelf(); });
        return path;
    }
    /** The open notebook's save queued (before the read that follows, on the store's thread); waited for a moment at most. */
    private void saved() {
        java.util.concurrent.CountDownLatch queued = new java.util.concurrent.CountDownLatch(1);
        app.runOnUiThread(() -> { app.autosaveNow(); queued.countDown(); });
        try { queued.await(2, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** The web app's files packed in assets/web (app/build.gradle's packWeb), as paths under it. */
    private Set<String> webFiles() {
        Set<String> names = new HashSet<>();
        list("", names);
        return names;
    }
    private void list(String dir, Set<String> names) {
        try {
            String[] here = app.getAssets().list(dir.isEmpty() ? "web" : "web/" + dir);
            if (here == null) return;
            for (String name : here) {
                String path = dir.isEmpty() ? name : dir + "/" + name;
                String[] inside = app.getAssets().list("web/" + path);
                if (inside == null || inside.length == 0) names.add(path); else list(path, names);
            }
        } catch (IOException e) { Log.w(TAG, "listing " + dir + ": " + e); }
    }

    /**
     * Each way in, its place over its code in large type (superdood.org's once it comes, online; on the Wi-Fi the address's
     * always), the QR code, where it reaches and the status; Close, the window's main button, and Stop Sharing plain beside it.
     */
    private void window() {
        LiveHost h = host;
        if (h == null && relayed == null) return;
        String address = h == null ? null : LiveHost.lanAddress(), link = h == null ? LiveHost.relayLink(key) : address == null ? null : h.link(address);
        View lan = null;
        if (h != null) {  // through the relay the key is long and never typed: the pickup's code, or the QR code
            LinearLayout box = LabUi.column(app);
            TextView lanCode = way(box, link == null ? WIFI_OFF : h.typed(address));
            if (link == null) lanCode.setVisibility(View.GONE); else lanCode.setText(h.code());
            lan = box;
        }
        LinearLayout column = ways(link, lan);
        TextView where = Theme.style(new TextView(app), TypeStyle.BODY);
        where.setText(h == null ? ONLINE : WHERE);
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(-1, -2);
        below.topMargin = app.dp(12);
        column.addView(where, below);
        statusLine = Theme.style(new TextView(app), TypeStyle.BODY);
        status(app.live.status());
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-1, -2);
        under.topMargin = app.dp(4);
        column.addView(statusLine, under);
        AlertDialog dialog = new AlertDialog.Builder(app).setTitle(SHARE).setView(column).setPositiveButton(CLOSE, null)
                .setNeutralButton(STOP, (d, w) -> { off(); app.message(SHARING_OFF); }).create();
        dialog.show();
        app.compactDialog(dialog, 600);
    }

    /**
     * A window's column: superdood.org over the pickup's code for {@code link} once it comes (a fresh one after nine
     * minutes while the window stays open), {@code lan} under it (null: none), then {@code link}'s QR code (null: none).
     * Draw Together's and Send's.
     */
    private LinearLayout ways(String link, View lan) {
        LinearLayout column = LabUi.column(app);
        column.setPadding(app.dp(24), app.dp(8), app.dp(24), app.dp(8));
        LinearLayout online = LabUi.column(app);
        online.setVisibility(View.GONE);
        TextView onlineCode = way(online, LiveHost.PICKUP_SHOWN);
        column.addView(online, new LinearLayout.LayoutParams(-1, -2));
        // A code lasts ten minutes: a fresh one after nine while the window stays open. Wall time, checked every 30 s:
        // the Manta's sleep stops postDelayed's clock, and a code shown on waking would long be gone.
        long[] got = {0};
        Runnable[] fetch = new Runnable[1];
        fetch[0] = () -> {
            if (System.currentTimeMillis() - got[0] >= 9 * 60_000L) {
                got[0] = System.currentTimeMillis();
                online.setVisibility(View.GONE);
                new Thread(() -> {
                    String c = LiveHost.pickup(LiveHost.PICKUP, link);
                    app.runOnUiThread(() -> {
                        if (c == null) { got[0] = 0; return; }
                        onlineCode.setText(c);
                        online.setVisibility(View.VISIBLE);
                    });
                }, "LivePickup").start();
            }
            online.postDelayed(() -> { if (online.isAttachedToWindow()) fetch[0].run(); }, 30_000L);
        };
        if (link != null) online.post(fetch[0]);
        if (lan != null) {
            LinearLayout.LayoutParams apart = new LinearLayout.LayoutParams(-1, -2);
            apart.topMargin = app.dp(16);
            column.addView(lan, apart);
        }
        boolean[][] qr = link == null ? null : QrCode.encode(link);
        if (qr != null) {
            ImageView picture = new ImageView(app);
            picture.setImageBitmap(bitmap(qr, Math.max(1, app.dp(260) / (qr.length + 8))));
            LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(-2, -2);
            at.topMargin = app.dp(16); at.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            column.addView(picture, at);
        }
        return column;
    }

    /**
     * Send (Export Page's row, 2026-11-05): page {@code place}'s copies and the notebook's PDF from {@code book} (a
     * snapshot, wet paint laid; released here), named {@code name}, to a browser on this Wi-Fi ({@link LiveSend}),
     * through Draw Together's host when it runs on the Wi-Fi, else one of its own (no key, no web app, no notebooks: only
     * Send's files). Its window shows the link as a QR code and superdood.org's code for it; closing it, or its ten
     * minutes, ends the link and stops that host.
     */
    void send(DrawingBook.Snapshot book, String name, int place) {
        String address = LiveHost.lanAddress();
        if (address == null) { book.release(); app.message(WIFI_OFF); return; }
        LiveHost shared = host;
        serial.execute(() -> {
            LiveSend files; LiveHost own = null;
            try {
                files = LiveSend.of(book, name, place, WordsMode::exportTiles, System.currentTimeMillis());
                if (shared == null) own = new LiveHost(LiveHost.PORT, LiveHost.newKey(), java.util.Collections.emptySet(), n -> null);
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                Log.w(TAG, "send: " + e);
                app.runOnUiThread(() -> app.message(FAILED));
                return;
            }
            LiveHost h = shared != null ? shared : own, stop = own;
            h.send = files;
            app.runOnUiThread(() -> sendWindow(files, h, stop, address));
        });
    }
    private void sendWindow(LiveSend files, LiveHost h, LiveHost own, String address) {
        if (app.destroyed) { h.send = null; if (own != null) serial.execute(own::stop); return; }
        sending = files;
        LinearLayout column = ways(files.link(address, h.port), null);
        TextView where = Theme.style(new TextView(app), TypeStyle.BODY);
        where.setText(LiveSend.WHERE);
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(-1, -2);
        below.topMargin = app.dp(12);
        column.addView(where, below);
        AlertDialog dialog = new AlertDialog.Builder(app).setTitle(LiveSend.SEND).setView(column).setPositiveButton(CLOSE, null).create();
        dialog.setOnDismissListener(d -> {
            sending = null; sendDialog = null;
            for (LiveHost x : new LiveHost[]{h, host}) if (x != null && x.send == files) x.send = null;
            if (own != null) serial.execute(own::stop);
        });
        // Closed when its time is up: wall time, checked every 30 s (the Manta's sleep stops postDelayed's clock).
        Runnable[] late = new Runnable[1];
        late[0] = () -> { if (!dialog.isShowing()) return; if (System.currentTimeMillis() >= files.until) dialog.dismiss(); else column.postDelayed(late[0], 30_000L); };
        sendDialog = dialog;
        dialog.show();
        app.compactDialog(dialog, 600);
        column.postDelayed(late[0], 30_000L);
    }

    /** {@code place} in heading size, then its code's line, large, to fill: both added to {@code box}. */
    private TextView way(LinearLayout box, String place) {
        TextView where = Theme.style(new TextView(app), TypeStyle.HEADING), code = Theme.style(new TextView(app), TypeStyle.TITLE);
        where.setText(place);
        // Addresses and codes in iA Writer Duo: Ukulele's letters and digits are hard to tell apart at a glance.
        where.setTypeface(Theme.textFace(true)); code.setTypeface(Theme.textFace(true));
        box.addView(where, new LinearLayout.LayoutParams(-1, -2));
        box.addView(code, new LinearLayout.LayoutParams(-1, -2));
        return code;
    }

    /** The live page's status changed ({@link LiveProbe}): the window's line follows (a closed window's, unseen). */
    void status(String words) {
        TextView line = statusLine;
        if (line == null) return;
        line.setText(words == null ? "" : words);
    }

    /** {@code dark}'s modules {@code scale} pixels square, black on white, inside the four-module quiet zone a scanner needs. */
    private static Bitmap bitmap(boolean[][] dark, int scale) {
        int n = dark.length, side = (n + 8) * scale;
        int[] pixels = new int[side * side];
        for (int y = 0; y < side; y++) for (int x = 0; x < side; x++) {
            int mx = x / scale - 4, my = y / scale - 4;
            pixels[y * side + x] = mx >= 0 && my >= 0 && mx < n && my < n && dark[my][mx] ? 0xff000000 : 0xffffffff;
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888);
    }

    @Override public void onActivityStarted(Activity a) { if (started++ == 0 && on) startHost(null); }
    @Override public void onActivityStopped(Activity a) { if (--started == 0 && on) { stopHost(); keep(); } }
    @Override public void onActivityCreated(Activity a, Bundle b) {}
    @Override public void onActivityResumed(Activity a) {}
    @Override public void onActivityPaused(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    @Override public void onActivityDestroyed(Activity a) {}
}
