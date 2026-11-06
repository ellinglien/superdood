# Live pages, step 2a: both ways — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Manta's own changes show in the browser, and the browser's strokes on the Manta (as in step 1), so
Elling can draw on the Manta and see it on a laptop, phone or iPad, and back.

**Architecture:** On branch `live-probe` (step 1's). Every message carries who sent it, so no one lays their own
change again. The Manta sends what each change showed: the page's composite tones over the area the change rendered
(`DrawingPad.renderDirty`), once the change is done (`PaintActivity.recovery`, "the page changed"), and the whole
page first when the page is new to the session. The browser keeps two canvases: the page as received, and the
stroke being drawn (sent alone, so the Manta never gets back its own marks or other layers' marks), then merged.
The relay replays what came before to a browser that joins late, and can listen on the Wi-Fi for a phone or iPad.

**Step 2b (later, its own plan):** the browser draws with Brush Studio's engine instead of plain lines.

**Tech Stack:** Java (app), Python 3 stdlib (relay), plain HTML/JS, adb.

**Limits (accepted for the probe):** the whole page first is read on the main thread (about 5 M pixels: a hitch the
first time); a page turned isn't sent until its first change; pure white is sent as 254 (255 means "clear" in
`LiveChange`); no order fixing (step 3).

---

### Task 1: The relay: who sent it, what came before, the Wi-Fi

**Files:** Modify `scripts/live-probe/relay.py`, `scripts/live-probe/test_relay.py`

- [ ] **Step 1: Tests first.** In `test_relay.py`, replace `test_passes_a_change_on` and add two tests:

```python
    def send(self, body, sender="manta-1"):
        return urllib.request.urlopen(urllib.request.Request(self.url + "/send?from=" + sender, data=body, method="POST"), timeout=5).read()

    def listen(self, query=""):
        listen = urllib.request.urlopen(self.url + "/listen" + query, timeout=5)
        self.assertEqual(listen.readline(), b": hello\n")
        listen.readline()
        return listen

    def test_passes_a_change_on_with_its_sender(self):
        listen = self.listen()
        self.assertEqual(self.send(b"\x01\x02\x03"), b"1")
        self.assertEqual(listen.readline(), b"data: 1 manta-1 " + base64.b64encode(b"\x01\x02\x03") + b"\n")

    def test_a_sender_not_a_plain_name_is_dash(self):
        listen = self.listen()
        self.send(b"\x09", sender="x%20y")
        self.assertEqual(listen.readline(), b"data: 1 - " + base64.b64encode(b"\x09") + b"\n")

    def test_history_for_who_asks(self):
        self.send(b"\x01"); self.send(b"\x02", sender="browser-a")
        late = self.listen("?history=1")
        self.assertEqual(late.readline(), b"data: 1 manta-1 " + base64.b64encode(b"\x01") + b"\n")
        late.readline()
        self.assertEqual(late.readline(), b"data: 2 browser-a " + base64.b64encode(b"\x02") + b"\n")
```

  The relay's state is module-level, so `setUp` must reset it: add `relay.reset()` as `setUp`'s first line.

- [ ] **Step 2:** `cd scripts/live-probe && python3 -m unittest test_relay -v`: the new tests fail.

- [ ] **Step 3: The relay.** In `relay.py`:
  - Docstring: `POST /send?from=<name>` and `GET /listen[?history=1]`, the line `data: <number> <sender> <base64>`
    (a sender that isn't letters, digits and `-`, at most 40, is `-`); `python3 relay.py [port] [lan]` (`lan`: listen
    on every address, for a phone or iPad on the same Wi-Fi).
  - Module state `listeners, history, lock, count`; `def reset()` clears them under the lock (`count[0] = 0`).
  - `do_POST`: `from urllib.parse import urlsplit, parse_qs`; `query = urlsplit(self.path)`; route on `query.path`;
    `sender = (parse_qs(query.query).get("from") or ["-"])[0]`; `if not re.fullmatch(r"[A-Za-z0-9-]{1,40}", sender): sender = "-"`;
    the line `b"data: %d %s %s\n\n" % (number, sender.encode(), base64.b64encode(body))`, appended to `history` and put
    on every listener, under the lock.
  - `do_GET`: route on `urlsplit(self.path).path`; for `/listen`, under the lock: add the queue, and if the query has
    `history=1`, put every line of `history` on it first (so nothing sent meanwhile is missed or doubled).
  - `serve(port=8765, host="127.0.0.1")`; `__main__`: `host = "0.0.0.0" if "lan" in sys.argv[2:] else "127.0.0.1"`,
    print the address(es) to open.

- [ ] **Step 4:** Tests: 4 OK (with `test_too_large_refused`).
- [ ] **Step 5:** Commit `Live probe: the relay names who sent a change, replays to late browsers, can listen on the Wi-Fi`.

### Task 2: The page in the browser, both ways

**Files:** Modify `scripts/live-probe/probe.html`

- [ ] **Step 1:** Two canvases stacked in a wrapper (`position: relative`; the second `position: absolute; inset: 0`,
  both the same CSS size): `page` (white, what the session holds) and `ink` (transparent: the stroke being drawn).
  Pointer events on `ink`. Remove the Clear button.
- [ ] **Step 2:** `const me = "browser-" + Math.random().toString(36).slice(2, 10);`. Sending: as now, but read the
  pixels from `ink` (its grey formula already handles alpha), POST to `"/send?from=" + me`, then
  `pageCtx.drawImage(ink, 0, 0); inkCtx.clearRect(0, 0, W, H);`.
- [ ] **Step 3: Receiving, in order:**

```js
let incoming = Promise.resolve(), received = 0;
const events = new EventSource("/listen?history=1");
events.onmessage = e => { incoming = incoming.then(() => lay(e.data)).catch(err => status.textContent = "Couldn't lay a change: " + err); };
async function lay(data) {
  const [number, from, b64] = data.split(" ");
  if (from === me) return;
  const bytes = Uint8Array.from(atob(b64), c => c.charCodeAt(0));
  const raw = new Uint8Array(await new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream("deflate"))).arrayBuffer());
  const v = new DataView(raw.buffer), x = v.getUint16(0), y = v.getUint16(2), w = v.getUint16(4), h = v.getUint16(6);
  if (raw.length !== 8 + w * h || x + w > W || y + h > H) throw new Error("not a change on this page");
  const img = pageCtx.getImageData(x, y, w, h);
  for (let i = 0; i < w * h; i++) { const g = raw[8 + i]; if (g === 255) continue; img.data[4 * i] = img.data[4 * i + 1] = img.data[4 * i + 2] = g; img.data[4 * i + 3] = 255; }
  pageCtx.putImageData(img, x, y);
  received++; status.textContent = `Received ${received} (number ${number}, from ${from}): ${w}×${h}`;
}
events.onerror = () => status.textContent = "Lost the relay; trying again…";
```

- [ ] **Step 4:** Check without a browser: relay on a spare port, POST a change `from=manta-x` with Python, then
  `curl -sN ".../listen?history=1"` shows it; and `node -e` can't run the page, so say the page is untried in a browser.
- [ ] **Step 5:** Commit `Live probe: the browser shows what the Manta sends, and sends only its own stroke`.

### Task 3: The Manta sends its changes

**Files:** Modify `LiveChange.java`, `LiveChangeChecks.java`, `LiveProbe.java`, `DrawingPad.java` (in
`renderDirty(int[], List)`, after `render(dirty);`, about line 519), `PaintActivity.java` (`live` field; `recovery()`)

- [ ] **Step 1: Check first** (`LiveChangeChecks`): add to `main` and the PASS line ("what the page shows read as a
  change, white kept white"):

```java
    static void shown() throws IOException {
        int[][] page = {{0, 255, 128}, {255, 255, 3}};
        LiveChange c = LiveChange.shown(1, 0, 2, 2, (x, y) -> page[y][x]);
        check(c.x == 1 && c.y == 0 && c.w == 2 && c.h == 2, "the area");
        check(Arrays.equals(c.greys, new int[]{254, 128, 254, 3}), "white sent as 254, never clear: " + Arrays.toString(c.greys));
        check(Arrays.equals(LiveChange.read(c.write()).greys, c.greys), "and read back so");
    }
```

- [ ] **Step 2:** `bash scripts/test_inkwash.sh`: fails to compile (`shown` missing).
- [ ] **Step 3:** In `LiveChange`:

```java
    /**
     * What the page shows over an area, {@code tone} giving each pixel's (0 black to 255 white), as a change: white
     * goes as 254, since 255 means clear (left as it is) and a white pixel must whiten what the receiver has there.
     */
    static LiveChange shown(int x, int y, int w, int h, java.util.function.IntBinaryOperator tone) {
        int[] greys = new int[w * h];
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) greys[row * w + col] = Math.min(254, tone.applyAsInt(x + col, y + row));
        return new LiveChange(x, y, w, h, greys);
    }
```

- [ ] **Step 4:** Checks pass.
- [ ] **Step 5: `LiveProbe` sends.** Add:

```java
    /** Who this tablet is to the relay: its own changes coming back are skipped. */
    private final String me = "manta-" + Long.toHexString(System.nanoTime());
    /** One at a time, in order. */
    private final java.util.concurrent.ExecutorService sending = java.util.concurrent.Executors.newSingleThreadExecutor();
    /** The page area rendered since the last change was sent, page pixels; only on the main thread. */
    private final android.graphics.Rect changed = new android.graphics.Rect();
    /** A change from the relay being laid: what it renders and changes isn't sent back. */
    private boolean laying;
    /** The page last sent; another page is sent whole first. */
    private ToneDocument sentPage;
    private volatile String address;
```

  - `start()`: `this.address = address;` before the thread starts. `stop()`: `changed.setEmpty(); sentPage = null;`.
  - `listen`: the line is now `data: <number> <sender> <base64>`: `split(" ", 3)`; `if (me.equals(parts[1])) continue;`
    the change is `parts[2]`.
  - `drain`: `laying = true; try { app.layerChange(...); } finally { laying = false; }`.
  - New methods:

```java
    /** DrawingPad rendered {@code area} (page pixels) after a change. */
    void rendered(android.graphics.Rect area) { if (listening != null && !laying) changed.union(area); }

    /** The page changed (PaintActivity.recovery): what it shows where it changed goes to the relay; a page new to the session, whole. */
    void changed() {
        ToneDocument page = app.pad == null ? null : app.pad.document;
        if (listening == null || laying || page == null) return;
        android.graphics.Rect area = page != sentPage ? new android.graphics.Rect(0, 0, page.width, page.height) : new android.graphics.Rect(changed);
        changed.setEmpty();
        if (!area.intersect(0, 0, page.width, page.height)) return;
        sentPage = page;
        LiveChange change = LiveChange.shown(area.left, area.top, area.width(), area.height(), page::compositeTone);
        String to = address;
        sending.execute(() -> send(to, change));
    }

    private void send(String to, LiveChange change) {
        HttpURLConnection c = null;
        try {
            byte[] body = change.write();
            c = (HttpURLConnection) new URL(to + "/send?from=" + me).openConnection();
            c.setDoOutput(true); c.setRequestMethod("POST"); c.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = c.getOutputStream()) { out.write(body); }
            Log.i(TAG, "sent " + change.w + "×" + change.h + ", " + body.length + " bytes: " + c.getResponseCode());
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "sending: " + e);
        } finally {
            if (c != null) c.disconnect();
        }
    }
```

- [ ] **Step 6: Hooks.** `PaintActivity`: the field loses `private` (`final LiveProbe live = ...`, DrawingPad calls it);
  in `recovery()`, after its first line (`if (loading || pad.document == null) return;`): `live.changed();`.
  `DrawingPad.renderDirty(int[], List)`: after `render(dirty);`: `app.live.rendered(dirty);`.
- [ ] **Step 7:** `./gradlew -q :app:assembleRelease :app:compileDebugAndroidTestJavaWithJavac` (copy
  `keystore.properties` from the main checkout if signing needs it; never commit or print it) and
  `bash scripts/test_inkwash.sh`: all PASS.
- [ ] **Step 8:** Commit `Live probe: the Manta sends what its changes show`.

### Task 4: Try it with Elling

- [ ] **Step 1:** Install the live-probe build (the app restarts; say so). `adb reverse tcp:8766 tcp:8766`; push
  `live-probe.txt` holding `http://127.0.0.1:8766` (quote the path inside `adb shell '...'`: it has spaces); relay:
  `python3 scripts/live-probe/relay.py 8766 lan` (ask Elling before `lan`: it opens the port to the Wi-Fi while it runs).
- [ ] **Step 2:** Elling on a scratch page: draws on the Manta and watches the browser (Mac, phone or iPad), draws in
  the browser and watches the Manta; erases on the Manta. Note: delay each way, what's missing or doubled, hitches.
- [ ] **Step 3:** Clean up as step 1 did (test file, `adb reverse --remove-all`, relay stopped, the normal build back);
  findings into the spec.
