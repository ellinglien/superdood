# Live pages, step 1: the USB probe — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Find out whether the Manta can take in another person's marks while its owner draws, and how fast, before
anything else of live pages is built (spec: `docs/superpowers/specs/2026-10-31-live-pages-design.md`, build step 1).

**Architecture:** A browser page on the Mac draws and sends the pixels each stroke changed (a deflated
`LiveChange`) to a stdlib Python relay on the Mac; the app, reaching the Mac over USB (`adb reverse`), listens by
server-sent events and lays each change on the page drawn on the way Place lays a picture
(`PaintActivity.layerChange` + `PictureImport.place`): one Undo step, darkening only, shown with the ordinary partial
update. Like Fading's ticks (`DryingTicker`), a change waits while the pen is down. One-way; no encryption, no order
fixing, no Undo yet.

**Tech Stack:** Java (app, desktop checks), Python 3 stdlib (relay), plain HTML/JS (`CompressionStream`), adb.

**Lives on branch `live-probe` only.** It adds the INTERNET permission and cleartext HTTP; never merged into
`inkwash-v2-lab` or released. It runs only when `Document/My Bad Drawings/live-probe.txt` exists.

---

### Task 1: Branch

- [ ] **Step 1:** In `~/Claudecode/Supernote/scribulor`: `git switch -c live-probe inkwash-v2-lab` (or a worktree
  agent's branch of that name).

### Task 2: `LiveChange`, the change as it travels

**Files:**
- Create: `app/src/main/java/io/github/mpdairy/monopaint/LiveChange.java`
- Create: `app/src/test/java/io/github/mpdairy/monopaint/LiveChangeChecks.java`
- Modify: `scripts/test_inkwash.sh:13-14` (add `LiveChange` to `sources`, `LiveChangeChecks` to `checks`)

- [ ] **Step 1: Write the failing check**

```java
package io.github.mpdairy.monopaint;

import java.io.*;
import java.util.*;

/**
 * Live pages' probe ({@link LiveChange}): a change read back as written, clear pixels kept clear; a change larger than
 * a page, a short one and one with bytes after it refused.
 */
public final class LiveChangeChecks {
    public static void main(String[] args) throws IOException {
        roundTrip(); refused();
        System.out.println("PASS: live change: written and read back the same, clear pixels clear; larger than a page, cut short or with bytes after it, refused");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static void roundTrip() throws IOException {
        int[] greys = {-1, 0, 128, 247, -1, 30};
        LiveChange back = LiveChange.read(new LiveChange(1900, 2550, 3, 2, greys).write());
        check(back.x == 1900 && back.y == 2550 && back.w == 3 && back.h == 2, "rectangle kept");
        check(Arrays.equals(back.greys, greys), "greys kept, clear clear: " + Arrays.toString(back.greys));
    }

    static void refused() throws IOException {
        refused(() -> new LiveChange(0, 0, 1921, 2561, new int[1921 * 2561]), "larger than a page");
        refused(() -> new LiveChange(0, 0, 2, 2, new int[3]), "greys not w × h");
        byte[] good = new LiveChange(0, 0, 2, 2, new int[4]).write();
        refused(() -> LiveChange.read(Arrays.copyOf(good, good.length - 3)), "cut short");
        refused(() -> LiveChange.read(deflated(new byte[]{0, 0, 0, 0, 0, 1, 0, 1, 7, 9})), "bytes after it");
        refused(() -> LiveChange.read(deflated(new byte[]{0, 0, 0, 0, 0x7f, 0x7f, 0x7f, 0x7f})), "too large, before reading it");
    }
    interface Throwing { void run() throws Exception; }
    static void refused(Throwing what, String why) {
        try { what.run(); } catch (IOException | IllegalArgumentException expected) { return; } catch (Exception e) { throw new AssertionError(why + ": " + e); }
        throw new AssertionError("not refused: " + why);
    }
    static byte[] deflated(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (java.util.zip.DeflaterOutputStream d = new java.util.zip.DeflaterOutputStream(out)) { d.write(raw); }
        return out.toByteArray();
    }
}
```

- [ ] **Step 2:** Add `LiveChange` to `sources` and `LiveChangeChecks` to `checks` in `scripts/test_inkwash.sh`. Run
  `bash ~/Claudecode/Supernote/tools/checks.sh` (JAVA_HOME exported). Expected: a compile error, `LiveChange` not found.

- [ ] **Step 3: Write `LiveChange`**

```java
package io.github.mpdairy.monopaint;

import java.io.*;
import java.util.zip.*;

/**
 * Live pages' probe (docs/superpowers/specs/2026-10-31-live-pages-design.md, build step 1): the pixels a change made,
 * as they travel. A rectangle of the page and its greys as {@link PictureImport#lay} takes them (−1: clear, left as it
 * is). Bytes, deflated (zlib, as a browser's CompressionStream("deflate") makes): x, y, w, h as big-endian unsigned
 * shorts, then a byte a pixel, 255 clear. Pure, checked on the desktop ({@code LiveChangeChecks}).
 */
final class LiveChange {
    /** The most pixels one change holds: a whole Manta page. */
    static final int MOST = 1920 * 2560;
    final int x, y, w, h;
    final int[] greys;

    LiveChange(int x, int y, int w, int h, int[] greys) {
        if (x < 0 || y < 0 || x > 0xffff || y > 0xffff || w <= 0 || h <= 0 || (long) w * h > MOST || greys.length != w * h)
            throw new IllegalArgumentException("Not a change: " + w + "×" + h + " at " + x + "," + y);
        this.x = x; this.y = y; this.w = w; this.h = h; this.greys = greys;
    }

    byte[] write() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            out.writeShort(x); out.writeShort(y); out.writeShort(w); out.writeShort(h);
            for (int g : greys) out.write(g < 0 ? 255 : g);
        }
        return bytes.toByteArray();
    }

    /** The change in {@code bytes}; throws on anything else: from the network, so never trusted. */
    static LiveChange read(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes)))) {
            int x = in.readUnsignedShort(), y = in.readUnsignedShort(), w = in.readUnsignedShort(), h = in.readUnsignedShort();
            if (w <= 0 || h <= 0 || (long) w * h > MOST) throw new IOException("Not a change: " + w + "×" + h);
            byte[] raw = new byte[w * h];
            in.readFully(raw);
            if (in.read() != -1) throw new IOException("Bytes after the change");
            int[] greys = new int[raw.length];
            for (int i = 0; i < raw.length; i++) greys[i] = (raw[i] & 255) == 255 ? -1 : raw[i] & 255;
            return new LiveChange(x, y, w, h, greys);
        } catch (ZipException e) { throw new IOException("Not deflated", e); }
    }
}
```

- [ ] **Step 4:** Run `bash ~/Claudecode/Supernote/tools/checks.sh`. Expected: `RAN 140`, no `FAILED`.
- [ ] **Step 5:** Commit: `git add -A app/src scripts/test_inkwash.sh && git commit -m "Live probe: a change as it travels"`

### Task 3: The relay (Mac only)

**Files:**
- Create: `scripts/live-probe/relay.py`
- Create: `scripts/live-probe/test_relay.py`

- [ ] **Step 1: Write the failing test** (`scripts/live-probe/test_relay.py`)

```python
"""The probe relay: a change sent reaches a listener, numbered, as base64; too large refused."""
import base64, threading, unittest, urllib.error, urllib.request
import relay

class RelayTest(unittest.TestCase):
    def setUp(self):
        self.server = relay.serve(0)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.url = "http://127.0.0.1:%d" % self.server.server_address[1]
    def tearDown(self):
        self.server.shutdown()

    def test_passes_a_change_on(self):
        listen = urllib.request.urlopen(self.url + "/listen", timeout=5)
        self.assertEqual(listen.readline(), b": hello\n")
        listen.readline()
        sent = urllib.request.urlopen(urllib.request.Request(self.url + "/send", data=b"\x01\x02\x03", method="POST"), timeout=5).read()
        self.assertEqual(sent, b"1")
        self.assertEqual(listen.readline(), b"data: 1 " + base64.b64encode(b"\x01\x02\x03") + b"\n")

    def test_too_large_refused(self):
        with self.assertRaises(urllib.error.HTTPError) as e:
            urllib.request.urlopen(urllib.request.Request(self.url + "/send", data=b"x" * (relay.MOST + 1), method="POST"), timeout=5)
        self.assertEqual(e.exception.code, 413)

if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2:** `cd scripts/live-probe && python3 -m unittest test_relay -v`. Expected: `ModuleNotFoundError: relay`.

- [ ] **Step 3: Write the relay** (`scripts/live-probe/relay.py`)

```python
"""Live pages' probe relay (docs/superpowers/specs/2026-10-31-live-pages-design.md, build step 1). On the Mac only,
reached by the Manta over USB (adb reverse tcp:8765 tcp:8765).
GET /        the probe page (probe.html)
POST /send   the body, a deflated LiveChange, numbered and passed to every listener; answers its number
GET /listen  server-sent events: one `data: <number> <base64>` line per change
"""
import base64, http.server, os, queue, sys, threading

MOST = 4 << 20
listeners, lock, count = set(), threading.Lock(), [0]

class Relay(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"

    def do_GET(self):
        if self.path.split("?")[0] == "/":
            with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "probe.html"), "rb") as f: body = f.read()
            self.send_response(200); self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body); return
        if self.path != "/listen": self.send_error(404); return
        self.send_response(200); self.send_header("Content-Type", "text/event-stream"); self.send_header("Cache-Control", "no-cache"); self.end_headers()
        q = queue.Queue()
        with lock: listeners.add(q)
        try:
            self.wfile.write(b": hello\n\n"); self.wfile.flush()
            while True:
                try: line = q.get(timeout=15)
                except queue.Empty: line = b": still here\n\n"
                self.wfile.write(line); self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError): pass
        finally:
            with lock: listeners.discard(q)

    def do_POST(self):
        if self.path != "/send": self.send_error(404); return
        n = int(self.headers.get("Content-Length") or 0)
        if not 0 < n <= MOST: self.send_error(413); return
        body = self.rfile.read(n)
        with lock:
            count[0] += 1
            number = count[0]
            line = b"data: %d %s\n\n" % (number, base64.b64encode(body))
            for q in listeners: q.put(line)
        self.send_response(200); self.send_header("Content-Type", "text/plain"); self.end_headers(); self.wfile.write(str(number).encode())

    def log_message(self, *args): pass

def serve(port=8765):
    server = http.server.ThreadingHTTPServer(("127.0.0.1", port), Relay)
    server.daemon_threads = True
    return server

if __name__ == "__main__":
    server = serve(int(sys.argv[1]) if len(sys.argv) > 1 else 8765)
    print("relay: http://127.0.0.1:%d" % server.server_address[1])
    server.serve_forever()
```

- [ ] **Step 4:** `python3 -m unittest test_relay -v`. Expected: 2 tests OK. (The `/` route fails until Task 4 adds `probe.html`; not tested.)
- [ ] **Step 5:** Commit: `git add scripts/live-probe && git commit -m "Live probe: the relay on the Mac"`

### Task 4: The probe page

**Files:** Create: `scripts/live-probe/probe.html`

- [ ] **Step 1: Write it**

```html
<!doctype html>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Live probe</title>
<style>
  body { margin: 0; font: 14px system-ui, sans-serif; background: #ddd; }
  header { padding: 8px 12px; display: flex; gap: 12px; align-items: center; }
  canvas { display: block; margin: 0 auto; background: #fff; touch-action: none; height: calc(100vh - 44px); }
</style>
<header><button id="clear">Clear</button><span id="status">Draw. Each stroke goes to the Manta when you lift.</span></header>
<canvas id="page"></canvas>
<script>
// Live pages' probe (docs/superpowers/specs/2026-10-31-live-pages-design.md, step 1): each stroke's changed
// rectangle as LiveChange bytes (x, y, w, h big-endian, a grey a pixel, 255 clear), deflated, POSTed to /send.
const q = new URLSearchParams(location.search), W = +q.get("w") || 1920, H = +q.get("h") || 2560, PAPER = 248;
const canvas = document.getElementById("page"), ctx = canvas.getContext("2d", { willReadFrequently: true }), status = document.getElementById("status");
canvas.width = W; canvas.height = H; ctx.lineCap = ctx.lineJoin = "round";
let last = null, box = null, sent = 0;
function page(e) { const r = canvas.getBoundingClientRect(); return [(e.clientX - r.left) * W / r.width, (e.clientY - r.top) * H / r.height]; }
function grow(x, y, pad) { box = box ? [Math.min(box[0], x - pad), Math.min(box[1], y - pad), Math.max(box[2], x + pad), Math.max(box[3], y + pad)] : [x - pad, y - pad, x + pad, y + pad]; }
canvas.addEventListener("pointerdown", e => { canvas.setPointerCapture(e.pointerId); last = page(e); box = null; });
canvas.addEventListener("pointermove", e => {
  if (!last) return;
  const p = page(e), width = 2 + 10 * (e.pressure || 0.5);
  ctx.lineWidth = width; ctx.beginPath(); ctx.moveTo(...last); ctx.lineTo(...p); ctx.stroke();
  grow(...last, width); grow(...p, width); last = p;
});
canvas.addEventListener("pointerup", () => { last = null; if (box) send(box); box = null; });
document.getElementById("clear").onclick = () => ctx.clearRect(0, 0, W, H);
async function send(b) {
  const x = Math.max(0, Math.floor(b[0])), y = Math.max(0, Math.floor(b[1])), w = Math.min(W, Math.ceil(b[2])) - x, h = Math.min(H, Math.ceil(b[3])) - y;
  if (w <= 0 || h <= 0) return;
  const rgba = ctx.getImageData(x, y, w, h).data, raw = new Uint8Array(8 + w * h), head = new DataView(raw.buffer);
  head.setUint16(0, x); head.setUint16(2, y); head.setUint16(4, w); head.setUint16(6, h);
  for (let i = 0; i < w * h; i++) {
    const a = rgba[4 * i + 3] / 255, grey = Math.round(255 - a * (255 - rgba[4 * i])); // black ink over white paper
    raw[8 + i] = grey >= PAPER ? 255 : grey;
  }
  const deflated = new Uint8Array(await new Response(new Blob([raw]).stream().pipeThrough(new CompressionStream("deflate"))).arrayBuffer());
  const started = performance.now(), answer = await fetch("/send", { method: "POST", body: deflated });
  sent++; status.textContent = `Sent ${sent} (number ${await answer.text()}): ${w}×${h}, ${deflated.length} bytes, ${Math.round(performance.now() - started)} ms to the relay`;
}
</script>
```

- [ ] **Step 2:** `python3 scripts/live-probe/relay.py`, open `http://127.0.0.1:8765/`, draw a stroke. Expected: the
  status line shows `Sent 1 (number 1)`, a size and a few KB.
- [ ] **Step 3:** Commit: `git add scripts/live-probe/probe.html && git commit -m "Live probe: the page that sends strokes"`

### Task 5: The app listens

**Files:**
- Create: `app/src/main/java/io/github/mpdairy/monopaint/LiveProbe.java`
- Modify: `app/src/main/java/io/github/mpdairy/monopaint/PaintActivity.java` (a field; `onResume`, `onPause` at about lines 2959-2985)
- Modify: `app/src/main/AndroidManifest.xml` (INTERNET; `usesCleartextTraffic` on `<application>`, line 9)

- [ ] **Step 1: Write `LiveProbe`**

```java
package io.github.mpdairy.monopaint;

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
 * only: with {@link #FILE} in the library folder (its first line the relay's address, http://127.0.0.1:8765 over
 * {@code adb reverse}), listens while the app is open and lays each change on the page drawn on as Place lays a
 * picture ({@link PictureImport#place}, one Undo step), once the pen is up, as Fading's ticks wait ({@link DryingTicker}).
 * Logs under {@link #TAG} each change's size, how long it waited for the pen and how long it took to show.
 */
final class LiveProbe {
    static final String FILE = "live-probe.txt", TAG = "LiveProbe";
    private static final long WAIT_MS = 100, AGAIN_MS = 1000;
    private final PaintActivity app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Arrived> waiting = new ArrayDeque<>();
    private final Runnable drain = this::drain;
    private volatile Thread listening;
    private volatile HttpURLConnection connection;

    private static final class Arrived {
        final int number; final LiveChange change; final long at;
        Arrived(int number, LiveChange change, long at) { this.number = number; this.change = change; this.at = at; }
    }

    LiveProbe(PaintActivity app) { this.app = app; }

    void start() {
        if (listening != null) return;
        String address = address();
        if (address == null) return;
        Thread thread = new Thread(() -> listen(address), TAG);
        listening = thread; thread.start();
    }

    void stop() {
        listening = null;
        HttpURLConnection c = connection;
        if (c != null) new Thread(c::disconnect).start();  // disconnect does network work: never on the main thread
        main.removeCallbacks(drain); waiting.clear();
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
                c = (HttpURLConnection) new URL(address + "/listen").openConnection();
                c.setReadTimeout(30000); connection = c;
                BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                Log.i(TAG, "listening to " + address);
                for (String line; listening == self && (line = in.readLine()) != null; ) {
                    if (!line.startsWith("data: ")) continue;
                    String[] parts = line.substring(6).split(" ", 2);
                    Arrived arrived = new Arrived(Integer.parseInt(parts[0]), LiveChange.read(Base64.getDecoder().decode(parts[1])), SystemClock.uptimeMillis());
                    main.post(() -> { if (listening != null) { waiting.add(arrived); drain(); } });
                }
            } catch (IOException | RuntimeException e) {
                if (listening == self) Log.w(TAG, "listening: " + e);
            } finally {
                if (c != null) c.disconnect();
                connection = null;
            }
            if (listening == self) SystemClock.sleep(AGAIN_MS);
        }
    }

    /** Lays the changes waiting, in order, each when nothing is being drawn. */
    private void drain() {
        main.removeCallbacks(drain);
        while (!waiting.isEmpty()) {
            if (!app.resumed || app.book == null || app.busy() || app.pad.stroking()) { main.postDelayed(drain, WAIT_MS); return; }
            Arrived arrived = waiting.poll();
            LiveChange c = arrived.change;
            if (c.x + c.w > app.book.width || c.y + c.h > app.book.height) { Log.w(TAG, "change " + arrived.number + " is off the page"); continue; }
            long start = SystemClock.uptimeMillis();
            app.layerChange(() -> PictureImport.place(app.book, c.x, c.y, c.w, c.h, c.greys.clone()));
            Log.i(TAG, "change " + arrived.number + ": " + c.w + "×" + c.h + ", waited " + (start - arrived.at) + " ms for the pen, laid and shown in " + (SystemClock.uptimeMillis() - start) + " ms");
        }
    }
}
```

- [ ] **Step 2: Hook it to the activity.** In `PaintActivity.java`, beside `DrawingPad pad;` (line 101):

```java
    /** Live pages' probe: listens only with live-probe.txt in the library folder (live-probe branch only). */
    private final LiveProbe live = new LiveProbe(this);
```

  At the end of `onResume()`, after `timedLook();`: `live.start();`. First line of `onPause()`, before `replay.stop();`: `live.stop();`.

- [ ] **Step 3: Manifest.** After the `MANAGE_EXTERNAL_STORAGE` permission:
  `<uses-permission android:name="android.permission.INTERNET" />`; on `<application`, add
  `android:usesCleartextTraffic="true"` (the relay is plain HTTP on 127.0.0.1).

- [ ] **Step 4: Build.** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; ./gradlew -q :app:assembleRelease :app:compileDebugAndroidTestJavaWithJavac`. Expected: no errors. Then `bash ~/Claudecode/Supernote/tools/checks.sh`: `RAN 140`.
- [ ] **Step 5:** Commit: `git add -A app && git commit -m "Live probe: the app lays changes from the relay once the pen is up"`

### Task 6: On the Manta, with Elling

- [ ] **Step 1:** Install (the app restarts; tell Elling):

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell cmd package compile -m speed -f ing.ell.mybaddrawings
adb reverse tcp:8765 tcp:8765
echo http://127.0.0.1:8765 > /tmp/live-probe.txt && adb push /tmp/live-probe.txt "/sdcard/Document/My Bad Drawings/live-probe.txt"
python3 scripts/live-probe/relay.py &
adb shell am start -n ing.ell.mybaddrawings/io.github.mpdairy.monopaint.PaintActivity
adb logcat -s LiveProbe
```

  Expected in logcat: `listening to http://127.0.0.1:8765`.
- [ ] **Step 2:** On a scratch page, Elling draws on the Manta while someone draws in `http://127.0.0.1:8765/` on the
  Mac. Watch and note: how soon the other marks appear (logcat's "waited … for the pen", "shown in …"), whether they
  ghost, flash or upset the pen, the bytes per stroke (the page's status line), and whether Undo takes one back.
- [ ] **Step 3:** Afterwards: `adb shell rm "/sdcard/Document/My Bad Drawings/live-probe.txt"; adb reverse --remove-all`;
  stop the relay; install `inkwash-v2-lab`'s build again (the app restarts).
- [ ] **Step 4:** Write what was found under a new "Probe, 2026-10-31" heading in the spec; commit on `inkwash-v2-lab`.

## Known limits of the probe

- A change dries wet paint on the page (`layerChange` settles a wash first), as Place does.
- Marks only darken (`PictureImport.lay`); the browser draws black on white over a blank page, not over the Manta's.
- Changes wait while the pen is down. If that feels too slow, a second probe tries laying them mid-stroke.
