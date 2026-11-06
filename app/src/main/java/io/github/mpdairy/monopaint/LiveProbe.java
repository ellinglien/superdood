package io.github.mpdairy.monopaint;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Base64;

/**
 * Live pages' probe (docs/superpowers/specs/2026-10-31-live-pages-design.md, build step 1), on the live-probe branch
 * only: while sharing ({@link LiveShare}), to superdood.org's relay ({@link LiveHost#RELAY}) or the app's own on the Wi-Fi
 * (over loopback), with the key; else with
 * {@link #FILE} in the library folder (its first line the relay's address, http://127.0.0.1:8765 over
 * {@code adb reverse}, then after a # the key the browser's link has after its #: none, none), listens while the app
 * is open and lays each change on the page drawn on as Place lays a picture ({@link PictureImport#lay}, one Undo step), once the pen is up, as Fading's ticks wait ({@link DryingTicker}).
 * A change a stroke made draws itself on along it (step 2c, {@link LiveReveal}): {@link #SLICES} slices, all one Undo
 * step; a stroke sent in parts while it is drawn (a browser's) lays each at once, all one Undo step ({@link LiveChange#joins});
 * the pen landing, Undo, Redo or another page lays what is left at once first ({@link #layNow}). Sends the whole
 * page once connected and when another page is shown, and the pen's strokes in parts while drawn ({@link #drawing}).
 * While live ({@link #listening}), Undo takes back only this tablet's own changes and Redo puts back what its Undo took
 * ({@link LiveOwn}, {@link #undo}, {@link #redo}). Logs under {@link #TAG} each change's size, how long it waited for the pen and took to show, and
 * how long making a change took on the main thread and how long after the pen lifted that began and the stroke was sent.
 * Its status ({@link LivePresence#host}: connected and how many guests, or lost) is a note at the brush line when it
 * changes, never more often, and a line in Share on Wi-Fi's window. Everything sent is sealed with the key
 * ({@link LiveCrypt}, in {@link #post}) and everything heard opened with it ({@link #listen}); one that doesn't open is
 * dropped. The relay is given only the key's access hash.
 */
final class LiveProbe {
    static final String FILE = "live-probe.txt", TAG = "LiveProbe";
    /** WAIT_MS: how soon laying looks again while busy or the pen is down; the pen's lift lays at once ({@link #changed}). */
    private static final long WAIT_MS = 100, AGAIN_MS = 1000;
    /** How long the Manta stays awake for its guests after the last change from one: then it sleeps as ever. */
    private static final long AWAKE_MS = 5 * 60_000;
    private static final int SLICES = 6;
    private final PaintActivity app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Arrived> waiting = new ArrayDeque<>();
    private final Runnable drain = this::drain, slice = this::sliceDue;
    /** The step the change last laid made, and who sent it: a stroke's next part joins it ({@link LiveChange#joins}). */
    private Object partStep;
    private String partFrom;
    /** The change being drawn on, slices still to lay; null when none. */
    private Arrived revealing;
    /** The pen stroke last sent with its path: Undo and Redo bring it back to the newest step, but aren't it. */
    private StrokeLog.Entry sentStroke;
    private volatile Thread listening;
    /** Whether the relay's line is open now (not just tried): a stale {@link #FILE}'s dead address never is. */
    private volatile boolean connected;
    private volatile HttpURLConnection connection;
    /** Who this tablet is to the relay, a host's id new each session ({@link LiveCrypt#hostId}): its own changes coming back are skipped. */
    private volatile String me;
    /** One at a time, in order. */
    private final java.util.concurrent.ExecutorService sending = java.util.concurrent.Executors.newSingleThreadExecutor();
    /** A change from the relay being laid: what it renders and changes isn't sent back. */
    private boolean laying;
    /** The page last sent; another page is sent whole first. */
    private ToneDocument sentPage;
    /** What each pixel of {@link #sentPage} was last sent as, by anyone ({@link LiveChange#since}): a change goes as only what differs. */
    private byte[] room;
    /** Changes the relay didn't take ({@link LiveChange#forget}): their boxes, {left, top, right, bottom}, go again with the next. */
    private final java.util.ArrayList<int[]> lost = new java.util.ArrayList<>();
    private volatile String address;
    /** The link's key ({@link LiveHost}'s, or after the # in {@link #FILE}): its access goes with every request. */
    private volatile LiveCrypt crypt;
    /** This tablet's changes on {@link #sentPage}, for its live Undo and Redo. */
    private final LiveOwn own = new LiveOwn();
    /** The newest step ({@link ToneDocument#top}) counted, ours or laid; the steps laid since (others' changes, never ours). */
    private long seen;
    private final java.util.ArrayList<Object> laidSteps = new java.util.ArrayList<>();
    /** Pixels an Undo or Redo just took back: they go as one change, an Undo's set ({@link LiveChange#set}), a Redo's as {@link LiveChange#since} decides. */
    private java.util.BitSet takenBack;
    private boolean takenBackSet;
    /** The parts of the stroke being drawn sent so far ({@link #drawing}), when the last went, and whether it is still on its way. */
    private int parts, partKinds;
    private long partAt;
    private volatile boolean partOut;
    /** When the pen last lifted (DrawingPad), uptime ms. */
    volatile long lifted;
    /** The status as last shown ({@link LivePresence#host}); null until the relay says who is there, and once stopped. */
    private String status;

    private static final class Arrived {
        final int number; final LiveChange change; final long at;
        /** Who sent it (the relay's from): a stroke's later part joins only its sender's part before it. */
        final String from;
        /** Instead of a change, a guest's page message ({@link LivePage}: open a notebook, turn): kept in the relay's order with the changes. */
        final LivePage asked;
        /** Its slices ({@link LiveReveal#slices}) and when each is due, ms after the first: worked out as it arrives, off the main thread. */
        final int[][] slices; final long[] due;
        /** While it is laid: the page it goes on, the next slice, when the first was laid, and the step its slices made (null: none yet). */
        ToneDocument page; int next; long start; Object step;
        /** What the page showed over the change's rectangle when its first slice was laid ({@link #onLayer}). */
        int[] shown;
        Arrived(int number, LiveChange change, long at, String from) {
            this.number = number; this.change = change; this.at = at; this.from = from; asked = null;
            slices = LiveReveal.slices(change, SLICES); due = LiveReveal.at(change, SLICES);
        }
        Arrived(int number, LivePage asked, long at) { this.number = number; this.asked = asked; this.at = at; change = null; slices = null; due = null; from = null; }
    }

    LiveProbe(PaintActivity app) { this.app = app; }

    /**
     * Held while connected: the Wi-Fi kept out of power save, which held the Manta's round trip to an iPad at 15–123 ms
     * (57 on average; to the router 14) on 2026-11-01. Low latency mode from Android 10 (the Manta has 11), else high performance.
     */
    private WifiManager.WifiLock wifi;

    void start() {
        if (listening != null) return;
        LiveHost host = app.share.host();
        LiveCrypt relayed = app.share.relayed();
        String given = host != null ? "http://127.0.0.1:" + host.port : relayed != null ? LiveHost.RELAY : address();
        if (given == null) return;
        int hash = given.indexOf('#');
        String address = hash < 0 ? given : given.substring(0, hash);
        this.address = address; crypt = host != null ? host.crypt : relayed != null ? relayed : new LiveCrypt(hash < 0 ? "" : given.substring(hash + 1));
        me = crypt.hostId();
        if (wifi == null) {
            WifiManager m = (WifiManager) app.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (m != null) {
                wifi = m.createWifiLock(Build.VERSION.SDK_INT >= 29 ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY : WifiManager.WIFI_MODE_FULL_HIGH_PERF, TAG);
                wifi.setReferenceCounted(false);
            }
        }
        Thread thread = new Thread(() -> listen(address), TAG);
        listening = thread; thread.start();
    }

    void stop() {
        layNow();
        listening = null; connected = false;
        lock(false);
        sentPage = null; room = null; lost.clear(); parts = 0; sentStroke = null; own.clear(); laidSteps.clear(); takenBack = null; partStep = null;
        HttpURLConnection c = connection;
        if (c != null) new Thread(c::disconnect).start();  // disconnect does network work: never on the main thread
        main.removeCallbacks(drain); waiting.clear();
        main.removeCallbacks(ticksSend); ticksDue = false;
        status = null; guests = false;
        main.removeCallbacks(doze); awake(false);
        if (app.inkWashBar != null) app.inkWashBar.statusCorner(null);
    }

    private void lock(boolean on) {
        if (wifi == null) return;
        if (on) wifi.acquire(); else if (wifi.isHeld()) wifi.release();
    }

    /**
     * While a guest is on the page and drawing, the Manta doesn't fall asleep by itself: asleep, its Wi-Fi goes and every
     * guest with it. {@link #AWAKE_MS} after a guest's last change it sleeps as ever (the owner, 2026-11-02: "let it
     * sleep if there's no action"), and the guests wait for it to wake (LivePresence.LOST_NOTE).
     */
    private void awake(boolean on) {
        if (on) app.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else app.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /** Whether a guest is on the page now (the relay's word). */
    private boolean guests;
    private final Runnable doze = () -> awake(false);

    /** A guest came or drew: awake {@link #AWAKE_MS} more. */
    private void stir() {
        awake(true);
        main.removeCallbacks(doze);
        main.postDelayed(doze, AWAKE_MS);
    }

    /** The status as last shown, or null. */
    String status() { return status; }
    /** The status now, from the listening thread: shown (the foot's corner, Draw Together's window) only when it changed. */
    private void say(Thread self, boolean connected, String[] names) {
        String words = LivePresence.host(connected, names, me);
        main.post(() -> {
            // Lost is news only after connected; a relay that never says who is there says nothing (an older one).
            if (listening != self || words.equals(status) || !connected && status == null) return;
            status = words;
            boolean were = guests;
            guests = connected && LivePresence.guests(names, me) > 0;
            if (!guests) { main.removeCallbacks(doze); awake(false); } else if (!were) stir();
            if (app.inkWashBar != null) app.inkWashBar.statusCorner(words);
            app.share.status(words);
        });
    }

    private static String address() {
        File file = new File(DrawingStorage.shared(), FILE);
        if (!file.isFile()) return null;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = in.readLine();
            return line == null || line.trim().isEmpty() ? null : line.trim();
        } catch (IOException e) { Log.w(TAG, "reading " + FILE + ": " + e); return null; }
    }

    private void listen(String address) {
        Thread self = Thread.currentThread();
        while (listening == self) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(address + "/listen?key=" + crypt.access + "&here=1&from=" + me).openConnection();
                c.setReadTimeout(30000); connection = c;
                BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                Log.i(TAG, "listening to " + address);
                connected = true;
                main.post(() -> {
                    if (listening != self) return;
                    // The Wi-Fi lock only while connected (on the main thread, in order with stop): a dead address (a stale FILE) never holds it.
                    lock(true);
                    if (app.pad != null && app.pad.document != null) shown(app.pad.document);
                });
                String event = null;  // the event's name, until the blank line that ends it
                for (String line; listening == self && (line = in.readLine()) != null; ) {
                    if (line.isEmpty()) event = null;
                    if (line.startsWith("event: ")) event = line.substring(7);
                    if (!line.startsWith("data: ")) continue;
                    if (event != null) { if (event.equals(LivePresence.EVENT)) say(self, true, LivePresence.names(line.substring(6))); continue; }
                    String[] parts = line.substring(6).split(" ", 3);
                    if (me.equals(parts[1])) continue;
                    byte[] bytes;
                    try { bytes = crypt.open(Base64.getDecoder().decode(parts[2])); }
                    catch (IOException | IllegalArgumentException e) { Log.w(TAG, "not sealed with this key: " + parts[0]); continue; }
                    LivePage page = LivePage.read(bytes);
                    if (page != null) {
                        if (LivePage.SHOWN.equals(page.kind)) continue;  // another host's: this tablet's page is its own
                        Arrived asked = new Arrived(Integer.parseInt(parts[0]), page, SystemClock.uptimeMillis());
                        main.post(() -> take(asked));
                        continue;
                    }
                    // A host's tools (LiveTools) are a guest's: this tablet draws with its own.
                    if (LiveTools.isDeflated(bytes)) continue;
                    Arrived arrived = new Arrived(Integer.parseInt(parts[0]), LiveChange.read(bytes), SystemClock.uptimeMillis(), parts[1]);
                    main.post(() -> take(arrived));
                }
            } catch (IOException | RuntimeException e) {
                if (listening == self) Log.w(TAG, "listening: " + e);
            } finally {
                if (listening == self) { say(self, false, null); connected = false; main.post(() -> { if (listening == self) lock(false); }); }
                if (c != null) c.disconnect();
                connection = null;
            }
            if (listening == self) SystemClock.sleep(AGAIN_MS);
        }
    }

    /**
     * A change or a guest's page message arrived: in line; a change being drawn on lays the rest of it now ({@link
     * #sliceDue}, as when more wait), not at its next slice, so this one isn't kept waiting behind it.
     */
    private void take(Arrived a) {
        if (listening == null) return;
        if (guests) stir();
        waiting.add(a);
        if (revealing == null) { drain(); return; }
        main.removeCallbacks(slice);
        sliceDue();
    }

    /** Lays the changes waiting, in order, each when nothing is being drawn and the one before is drawn on. */
    private void drain() {
        main.removeCallbacks(drain);
        while (!waiting.isEmpty() && revealing == null) {
            if (!app.resumed || app.book == null || app.busy() || app.pad.stroking()) { main.postDelayed(drain, WAIT_MS); return; }
            Arrived arrived = waiting.poll();
            if (arrived.asked != null) { asked(arrived.asked); continue; }
            LiveChange c = arrived.change;
            if (c.x + c.w > app.book.width || c.y + c.h > app.book.height) { Log.w(TAG, "change " + arrived.number + " is off the page"); continue; }
            arrived.page = app.book.current(); arrived.start = SystemClock.uptimeMillis();
            revealing = arrived;
            laySlice(false);
        }
    }

    /**
     * A guest opened a notebook or turned the page ({@link PaintActivity#guestAsked}): its page goes to everyone as any
     * page shown does ({@link #shown}); nothing turned (the last page, the notebook already shown, this tablet in its
     * notebook screens), the page shown is sent again, so the guest waiting for it draws on.
     */
    private void asked(LivePage asked) {
        ToneDocument before = app.pad.document;
        Log.i(TAG, "a guest asked: " + asked.kind + " " + (LivePage.OPEN.equals(asked.kind) ? asked.path : String.valueOf(asked.place)));
        app.guestAsked(asked);
        if (!app.loading && app.pad.document == before && before != null) shown(before);
    }

    /** The next slice is due: laid, unless something is being drawn (the pen landing lays them all first, so rarely). */
    private void sliceDue() {
        if (app.busy() || app.pad.stroking()) { main.postDelayed(slice, WAIT_MS); return; }
        laySlice(false);
    }

    /**
     * Lays the next slice of the change being drawn on ({@code rest}, or another change waiting: all it has left at
     * once), joined to the step its slices made while that is still the page's newest (anything else's step between
     * them is never joined); then the next is due, or the next change.
     */
    private void laySlice(boolean rest) {
        Arrived a = revealing;
        LiveChange c = a.change;
        if (a.next == 0) {
            // What laying finishes first (layerChange: a stroke still lifting, wet paint dried, a settling wash laid) is
            // this tablet's change: sent now, since nothing laying writes is (the Manta and the browser drifted apart so).
            app.pad.finishStroke(); app.pad.settleDrying(); changed();
            if (a.page == sentPage) c.into(room, a.page.width);  // everyone lays it too
        }
        if (a.shown == null) a.shown = PictureImport.liveTones(a.page, c.x, c.y, c.w, c.h);
        // Strokes in a row each show as they come, not after the drawing on of every one before (Elling: "only the first one shows").
        int[] part = rest || !waiting.isEmpty() ? rest(a) : a.slices[a.next++];
        // A stroke's later part (sent while it is drawn) joins its sender's part before it, as a change's slices join.
        Object after = a.step != null ? a.step : c.joins && a.from.equals(partFrom) ? partStep : null;
        boolean joined = after != null && after == a.page.lastStep();
        laying = true;
        long top = a.page.top();  // a set change is a step a layer and plane it erased, then its own
        // On the layer it was drawn on (InkWashLayers.drawOn: made first on a page of one pair), this tablet's own kept in use.
        // Ink on Vector Ink's layer goes on a pair under it first, as the host's own pen's (VectorInk.under), its step the guest's too; then the greys as that layer takes them.
        try { app.layerChange(() -> InkWashLayers.drawOn(a.page, c.layer, () -> { if (darkens(part, a.shown)) VectorInk.under(a.page); if (PictureImport.lay(a.page, c.x, c.y, c.w, c.h, onLayer(a, part), joined, c.set, false, c.kinds)) { laidSteps.addAll(a.page.stepsAfter(top)); a.step = a.page.lastStep(); } })); }
        finally { laying = false; a.page.takeLive(); }
        if (a.step != null) { partFrom = a.from; partStep = a.step; }
        if (a.next < a.slices.length) { main.postAtTime(slice, a.start + a.due[a.next]); return; }
        revealing = null;
        long now = SystemClock.uptimeMillis();
        Log.i(TAG, "change " + a.number + ": " + c.w + "×" + c.h + (c.set ? " (an Undo)" : "") + ", waited " + (a.start - a.at) + " ms for the pen, laid in "
                + a.slices.length + " slices and shown in " + (now - a.start) + " ms: " + (now - a.at) + " ms from received to laid");
        // Posted, not at once: when the pen landing laid this, its stroke has begun before the next change looks.
        main.post(drain);
    }

    /**
     * {@code greys} (what the sender's page showed, over all its layers) as the layer drawn on here must take them so
     * this page shows them too ({@link PictureImport#inked}); a set change's go as they are, {@link PictureImport#lay}'s
     * lighter inking its darker pixels so itself. −1 where nothing is to change.
     */
    private static int[] onLayer(Arrived a, int[] greys) {
        LiveChange c = a.change;
        for (int i = 0; i < greys.length; i++) {
            if (greys[i] >= 0 && !c.set) greys[i] = PictureImport.inked(greys[i], a.shown[i], a.page.layerTone(c.x + i % c.w, c.y + i / c.w, c.kinds));
        }
        return greys;
    }

    /** Whether any of {@code greys} is darker than the page showed there ({@code shown}): ink to lay, a set change's too. */
    private static boolean darkens(int[] greys, int[] shown) {
        for (int i = 0; i < greys.length; i++) if (greys[i] >= 0 && greys[i] < shown[i]) return true;
        return false;
    }

    /** The slices of {@code a} not yet laid, as one. */
    private static int[] rest(Arrived a) {
        int[] greys = a.slices[a.next++].clone();
        for (; a.next < a.slices.length; a.next++) { int[] more = a.slices[a.next]; for (int i = 0; i < more.length; i++) if (more[i] >= 0) greys[i] = more[i]; }
        return greys;
    }

    /** The pen landing, Undo, Redo, another page: the change being drawn on is laid whole at once first. */
    void layNow() {
        if (revealing == null) return;
        main.removeCallbacks(slice);
        laySlice(true);
    }

    /** The tools last sent ({@link LiveTools#write}), so the same ones don't go again; written on {@link #sending}. */
    private byte[] toolsSent;
    /**
     * This tablet's tools changed mid-session (a slot picked, a tool taken up, a size or grey, the page's mirrors): they
     * go to the guests as they do when the session begins (the browser app's M4), only when they differ from the last
     * sent. A guest's own slots and its tool in use stay its own (live.html).
     */
    void toolsChanged() {
        ToneDocument page = sentPage;
        if (listening == null || laying || page == null || app.pad == null || app.pad.document != page) return;
        sendTools(page, false);
    }
    /** The tools as they are now, read here, written and sent on {@link #sending} when they differ from the last sent, or {@code again}. */
    private void sendTools(ToneDocument page, boolean again) {
        LiveTools tools = new LiveTools(page.width, page.height, app.inkWash.slots.toolbar(), app.inkWash.selected, app.inkWash.look(InkWash.ERASER).px, page.mirrors());
        String to = address;
        sending.execute(() -> {
            try {
                byte[] raw = tools.write();
                if (!again && java.util.Arrays.equals(raw, toolsSent)) return;
                toolsSent = raw;
                post(to, raw, false); Log.i(TAG, "sent the tools");
            } catch (IOException | RuntimeException e) { Log.w(TAG, "sending the tools: " + e); }
        });
    }

    /** The page message, which the relay's history starts again from ({@code start}): the page goes whole right after it. */
    private void sendPage(LivePage shown) {
        String to = address;
        sending.execute(() -> {
            try { post(to, shown.write(), true); } catch (IOException | RuntimeException e) { Log.w(TAG, "sending the page's place: " + e); }
        });
    }

    /** DrawingPad shows {@code page} (another page), or the session began: it goes to the relay whole. */
    void shown(ToneDocument page) {
        if (listening == null || laying) return;
        page.takeLive();  // kept from now on
        sentPage = page; sentStroke = page.newestStroke(); parts = 0;
        if (room == null || room.length != page.width * page.height) room = new byte[page.width * page.height];
        java.util.Arrays.fill(room, (byte) 255);  // nothing sent yet: the page goes whole
        lost.clear();
        own.clear(); laidSteps.clear(); takenBack = null; seen = page.top();
        long start = SystemClock.uptimeMillis();
        // First which notebook page it is (LivePage): a guest starts a blank page for another before it is laid whole.
        if (app.book != null) sendPage(LivePage.shown(app.drawingName, app.book));
        // Then this tablet's tools, so a browser that joins makes its page this size and draws with them (LiveTools):
        // always, as the relay's history may start again at the page message.
        sendTools(page, true);
        // A layer and a kind at a time (LiveChange.whole), each going as what it adds (LiveChange.since); the magnets with the last.
        LiveChange.whole(page, (c, last) -> send(last ? withMagnets(page, c) : c, 0, start));
    }

    /**
     * The page changed (PaintActivity.recovery): what it shows where it was written since the last change
     * ({@link ToneDocument#takeLive}: a mirrored stroke's copies, not the page between them nor the guides the screen
     * shows; a wash settling after the stroke), with the pen stroke's path when a stroke made it; a page not yet sent, whole.
     */
    void changed() {
        // The pen lifted (its stroke's change, PaintActivity.recovery): what waited for it is laid now, not at drain's next look.
        if (!waiting.isEmpty() && revealing == null) main.post(drain);
        ToneDocument page = app.pad == null ? null : app.pad.document;
        if (listening == null || laying || page == null) return;
        if (page != sentPage) { shown(page); return; }
        java.util.List<int[]> areas = page.takeLive();
        java.util.List<Object> steps = page.stepsAfter(seen);
        steps.removeAll(laidSteps); laidSteps.clear(); seen = page.top();
        if (takenBack != null) { sendTakenBack(page, steps); return; }
        own.made(steps);
        long start = SystemClock.uptimeMillis();
        // A stroke sent in parts while drawn: the lift's rest is its last part, laid at once; else it draws itself on along its path.
        boolean later = parts > 0;
        parts = 0;
        // shortcut: a stroke from before the session that an Undo brings back to the newest step goes with its path; fine for a probe.
        StrokeLog.Entry stroke = page.newestStroke();
        int[] path = later || stroke == null || stroke == sentStroke ? LiveChange.NO_PATH : LiveChange.path(stroke);
        areas.addAll(lost); lost.clear();
        LiveChange change = LiveChange.shown(areas, page.width, page.height, page::liveTones, path);
        if (change == null) return;
        change.layer = LiveChange.layerOf(page, steps);
        // The marks' kind, so a guest's Show Kinds fades them (LiveChange.kinds): the stroke's; on its own after the lift, wet paint's.
        change.kinds = later ? partKinds : path.length > 0 ? StrokeLog.medium(stroke.code, stroke.px, stroke.darkness) : ticking ? ToneDocument.KIND_PAINT : 0;
        if (stroke != null) sentStroke = stroke;
        change = withMagnets(page, change);
        send(later ? change.part(true) : change, path.length > 0 || later ? lifted : 0, start);
    }

    /**
     * The pen drew (DrawingPad, its samples shown): every {@link LiveChange#PART_MS}, what the page shows where it was
     * written since the part before goes as the stroke's next part ({@link LiveChange#part}), so guests watch the Manta's
     * stroke as it is drawn, as the Manta watches theirs (the owner, 2026-11-05: "FROM the manta, it's still just on pen
     * lift"). Only that small box is read here; packed and sent on {@link #sending}, and none while the part before is
     * still on its way (a slow link sends fewer, larger parts). The lift's rest goes from {@link #changed}.
     */
    void drawing() {
        ToneDocument page = sentPage;
        if (listening == null || laying || partOut || page == null || app.pad.document != page) return;
        long now = SystemClock.uptimeMillis();
        if (now - partAt < LiveChange.PART_MS) return;
        partAt = now;
        LiveChange c = LiveChange.shown(page.takeLive(), page.width, page.height, page::liveTones, LiveChange.NO_PATH);
        if (c == null) return;
        c.layer = LiveChange.layerOf(page, java.util.Collections.emptyList());  // the stroke's: the layer its gesture draws on
        // The stroke's kind, as the lift's (LiveChange.kinds): a guest lays every part on that kind's plane.
        if (parts == 0) { StrokeLog.Recorder r = app.pad.recording(); partKinds = r == null ? 0 : r.medium(); }
        c.kinds = partKinds;
        if (!send(withMagnets(page, c).part(parts > 0), 0, now)) return;
        parts++; partOut = true;
        sending.execute(() -> partOut = false);  // after the part's own send: the executor goes in order
    }

    // What the page does on its own after the lift (DryingTicker's ticks: wet paint moving, a wash settling, a bath,
    // halos, fading marks) goes while it goes on, batched. Before, only the lift and the rest went: a guest saw a big oil
    // as it was at the lift for as long as it moved, and a fading mark only once it ended.
    private boolean ticksDue;
    private final Runnable ticksSend = this::sendTicks;
    private boolean ticking;
    private void sendTicks() { ticksDue = false; if (app.pad == null || app.pad.drawingNow()) return; ticking = true; try { changed(); } finally { ticking = false; } }
    /** The page changed on its own (a tick shown): sent within {@link LiveChange#TICKS_MS} with whatever else changes meanwhile; a stroke down waits for its lift, which sends it all. */
    void ticked() {
        if (listening == null || ticksDue) return;
        ticksDue = true;
        main.postDelayed(ticksSend, LiveChange.TICKS_MS);
    }

    private Magnets.Tiles tiles;
    /**
     * {@code c} with the page's magnets drawn into it where it holds greys, as its PNG copy draws them
     * ({@link Magnets#stamp}): they lie over the page, not in it, so a change never held them (Elling: "magnetic
     * poetry doesn't work"). Others take them as marks.
     */
    private LiveChange withMagnets(ToneDocument page, LiveChange c) {
        Magnets magnets = page.magnets();
        if (magnets.isEmpty()) return c;
        if (tiles == null) tiles = WordsMode.exportTiles();
        int[] argb = new int[c.greys.length];
        for (int i = 0; i < argb.length; i++) argb[i] = c.greys[i] < 0 ? 0 : 0xff000000 | c.greys[i] * 0x010101;
        Magnets.stamp(argb, c.x, c.y, c.w, c.h, magnets, tiles);
        for (int i = 0; i < argb.length; i++) if (c.greys[i] >= 0) c.greys[i] = Math.min(254, argb[i] & 255);
        return c;
    }

    /**
     * Whether Undo and Redo are this tablet's own changes' ({@link #undo}, {@link #redo}): while Draw Together is on, or
     * while connected to a relay ({@link #FILE}'s). Not merely trying: a stale {@link #FILE} left from a USB probe kept
     * the Manta "live", Undo own-only and Redo off, with Draw Together off (the owner, 2026-11-02).
     */
    boolean listening() { return listening != null && (connected || app.share.on()); }

    /**
     * Undo while live: this tablet's newest change still on the page taken back where nothing was laid over it
     * since ({@link LiveOwn#undo}), one Undo step; the page's {@link #changed} then sends what it shows there,
     * set. Whether anything changed.
     */
    boolean undo(ToneDocument page) { return takeBack(page, false); }
    /** Redo while live: what this tablet's newest live Undo took back put back where the page still shows what it left ({@link LiveOwn#redo}). */
    boolean redo(ToneDocument page) { return takeBack(page, true); }
    private boolean takeBack(ToneDocument page, boolean redo) {
        if (page != sentPage) return false;
        java.util.BitSet bits = new java.util.BitSet();
        if (!(redo ? own.redo(page, bits) : own.undo(page, bits))) return false;
        takenBack = bits; takenBackSet = !redo;
        return true;
    }

    /** What the page shows where an Undo or Redo took pixels back (in {@code steps}), as one change. */
    private void sendTakenBack(ToneDocument page, java.util.List<Object> steps) {
        java.util.BitSet bits = takenBack;
        takenBack = null;
        int w = page.width, x0 = w, y0 = page.height, x1 = -1, y1 = -1;
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) { int x = i % w, y = i / w; x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
        if (x1 < 0) return;
        long start = SystemClock.uptimeMillis();
        int bw = x1 - x0 + 1, left = x0, top = y0;
        int[] tones = PictureImport.liveTones(page, x0, y0, bw, y1 - y0 + 1);
        LiveChange c = LiveChange.shown(x0, y0, bw, y1 - y0 + 1, (x, y) -> bits.get(y * w + x) ? tones[(y - top) * bw + x - left] : -1, LiveChange.NO_PATH, takenBackSet);
        c.layer = LiveChange.layerOf(page, steps);
        send(c, 0, start);
    }

    /**
     * {@code change} on {@link #sentPage} as it goes, only what differs from what was sent ({@link LiveChange#since}:
     * set when it lightens); nothing when nothing does. {@code lifted}: when the pen lifted for the stroke this is,
     * uptime ms (0: no stroke); {@code start}: when making it began on the main thread, which is logged with it.
     * Whether anything went.
     */
    private boolean send(LiveChange whole, long lifted, long start) {
        LiveChange change = whole.since(room, sentPage.width);
        if (change == null) return false;
        String to = address;
        long made = SystemClock.uptimeMillis();
        String timing = "made in " + (made - start) + " ms on the main thread" + (lifted > 0 ? ", " + (start - lifted) + " ms after the pen lifted" : "");
        byte[] into = room;
        int width = sentPage.width;
        sending.execute(() -> {
            // Lost: out of the room again, so the next change there sends it (on the main thread, as the room's every use).
            if (!send(to, change, lifted, timing)) main.post(() -> {
                if (room != into) return;
                change.forget(into, width);
                lost.add(new int[]{change.x, change.y, change.x + change.w, change.y + change.h});
                ticked();  // gone again within TICKS_MS, as the page's own ticks go: never a tight loop while the relay is away
            });
        });
        return true;
    }

    /** Whether the relay took it. */
    private boolean send(String to, LiveChange change, long lifted, String timing) {
        try {
            long packing = SystemClock.uptimeMillis();
            byte[] body = change.write();
            packing = SystemClock.uptimeMillis() - packing;
            int code = post(to, body, false);
            Log.i(TAG, "sent " + change.w + "×" + change.h + (change.set ? " (an Undo)" : "") + ", " + body.length + " bytes (" + timing + "; packed in " + packing + " ms): " + code
                    + (lifted > 0 ? ", " + (SystemClock.uptimeMillis() - lifted) + " ms after the pen lifted" : ""));
            return code == 200;
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "sending: " + e);
            return false;
        }
    }

    /** {@code plain} sealed and POSTed to the relay at {@code to}; its answer's code. {@code start}: the relay's history starts again with it (LiveHost). */
    private int post(String to, byte[] plain, boolean start) throws IOException {
        byte[] body = crypt.seal(plain);
        HttpURLConnection c = (HttpURLConnection) new URL(to + "/send?key=" + crypt.access + "&from=" + me + (start ? "&start=1" : "")).openConnection();
        try {
            c.setDoOutput(true); c.setRequestMethod("POST"); c.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = c.getOutputStream()) { out.write(body); }
            return c.getResponseCode();
        } finally { c.disconnect(); }
    }
}
