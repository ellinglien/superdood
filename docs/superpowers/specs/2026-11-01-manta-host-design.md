# The Manta hosts: Share on Wi-Fi

2026-11-01, branch `live-host` (from `explore-live`; never released: INTERNET permission). Step 1 built and checked on
the desktop; untried on the Manta. The next steps are Elling's to decide (below, each with a recommendation).

Elling: "i am wondering if the supernote app could act as a server for easy lan connection with this", "easy way to
load onto a computer, easy to edit on ipad". So no Mac: the Manta app serves the web app and the live relay itself.

## Step 1: what it does

- **Menu → Share on Wi-Fi** (the app's group, under Left-Handed; marked while on). A tap starts the app's own relay on
  the Wi-Fi and opens its window: the link in heading type (`http://192.168.1.23:8080/live#k7qmw2xrh4tnpd9e`), its QR code
  (an iPad's camera reads it off the e-ink), "On this Wi-Fi, while the app is open." Buttons **Close** and **Stop
  Sharing**. A tap while on opens the window again. Wi-Fi off: the note "Wi-Fi off" and nothing starts.
- **Port 8080** (any free port if it is taken); **the key**: 16 letters from an unambiguous lower-case alphabet (about
  79 bits; 8 until `live-crypt`, see "Encryption"), new each time sharing is turned on, kept while the app stays open.
- **The Manta's own live connection** (`LiveProbe`) talks to its own relay over loopback (127.0.0.1) with the key:
  no adb, no Mac. `live-probe.txt` is read only while not sharing, so the Mac relay keeps working for testing.
- **On only while the app is open**: while any of its screens is started (drawing screen, Tools page, Brush Lab).
  Away (Home, another app), the server stops; back, it starts again with the same key and port, and an open browser
  page reconnects by itself (EventSource). The drawing screen closing turns it off for good.
- **Asleep, and back** (2026-11-02, branch `live-reconnect`): asleep, the Manta is away (its Wi-Fi goes: a reload in
  the browser then fails, the address unreachable, until it wakes); the open page keeps trying, its status
  "Connection lost" over "Waiting for the Manta, maybe asleep. Back within a minute or two of waking.", and once the Manta is awake it reconnects and shows
  the Manta's page as it is (its marks and erasing meanwhile), and the guest's own marks not yet sent go then. While
  a guest is on the page the Manta doesn't fall asleep by itself (its power button still sleeps it; with no guest it
  sleeps as ever). Not chosen: serving through sleep (a wake lock and the Wi-Fi held while asleep): the battery spent
  while it looks off, likely refused by the firmware's own Wi-Fi off in sleep, and the Manta's own page would miss what
  guests drew meanwhile (its probe hears only what comes while it listens).
- **An install or a crash**: the app stopped with sharing on and started within 2 hours shares again with the same
  key and port (kept in the app's own preferences), so open pages reconnect. Stop Sharing and the drawing screen
  closing still turn it off for good.
- Someone who never taps the row: no socket is opened, nothing else changes.

## How

- **`LiveHost`** (pure Java, `java.net.ServerSocket` + a cached thread pool, at most 32 connections; no new
  dependency): `scripts/live-probe/relay.py` again. HTTP/1.0, one request a connection. The web app's files; `GET
  /listen` as server-sent events (`: still here` every 15 s); `POST /send`. Same rules as relay.py: 4 MB a change,
  empty refused (413), every change kept from the start and sent first to a listener asking `history=1`, a sender
  that isn't letters, digits and `-` (at most 40) is `-`.
- **Access**: every request but the web app's files needs `?key=` the link's key (compared in constant time), or 403.
  The files hold no drawings. Their names are listed once from the APK's `assets/web` at start; no request path
  reaches anything else.
- **The guest page** (`live.html`) sends the key after the link's `#` with listen and send; the Mac relay ignores it.
- **The web app in the APK**: `app/build.gradle`'s `packWeb` runs `web/engine/build.sh` (its Gradle skips the engine
  when no Java changed) and `relay.py pack` into generated `assets/web` at every build, so the APK carries the current
  web build. Building the APK now needs node and python3 too.
- **APK size**: 10.3 → 12.6 MB (+2.3 MB compressed; 5.3 MB raw: the wasm engine 2.8 MB, its assets 0.87 MB, which are
  the app's paper scans and lists again, Brush Studio's `engine.js` 0.76 MB, the faces, the drawables).
- **QR code**: `QrCode`, about 100 lines: byte mode, level L, versions 1 to 5 (one block each), mask 0 always. A link
  is version 3.
- **`LiveShare`** (Android): the row, the window, the lifecycle (`Application.ActivityLifecycleCallbacks`). Starting
  and stopping the server run off the main thread.

## Checks

- `LiveHostChecks` in `scripts/test_inkwash.sh`: files without the key; listen, send and other paths refused without
  it or with another; changes numbered to every listener, history to one asking; empty and over 4 MB refused, 4 MB
  taken; the QR code's shape, and OpenCV reads it back as the link (where python3 has cv2).
- `node scripts/live-probe/live-check.cjs <port> --host`: the guest checks (two headless Chrome guests, an iPad with a
  pen and a phone with a finger, drawing and seeing each other, Undo, eraser, kaleidoscope) through `LiveHost` on the
  JVM serving `relay.py pack`'s files: 14 of 14. Without `--host`, the Mac relay as before: 12 of 12.

## Trying it on the Manta

Install the `live-host` build. Wi-Fi on, the iPad or computer on the same network. Menu → Share on Wi-Fi; scan the
QR code with the iPad's camera or type the link; draw on both. Logcat tags `LiveShare` and `LiveProbe`. A Wi-Fi with
client isolation (many guest and café networks) blocks it: a home network is the test.

## Next steps (to decide)

### (a) The notebooks from a computer or iPad: download, upload

Through the same server and key: a page listing the notebooks; per notebook PDF, per page PNG, and the `.mpaint` file;
upload a picture, PDF or `.mpaint` as Import does.

**Recommendation: yes, next, download first.** Downloads made on request by the exporters the app already has
(`NotebookPdf`, `PageExport`, `PsdWriter`), off the main thread, key-protected like `/listen`. Upload second, and only
ever as a new notebook or a new page (never over an existing file: no data lost to a stale copy).

### (b) "Edit on iPad": one source of truth, or download, edit, upload

- **One source of truth**: the browser opens a Manta notebook's page and draws on it live; the changes land in the
  Manta's notebook as live changes do now (its Undo steps, saved as any stroke). New: turning pages from the browser.
- **Download, edit, upload**: works with the Manta asleep, but makes copies that drift, and putting one back needs a
  merge or overwrites.

**Recommendation: one source of truth, built on what works today.** The live page already is the edit; add page
turns that travel both ways (the page shown is the room's page, whoever turned last). Download-edit-upload only as
(a)'s "upload as new", for a notebook taken away for a while.

### (c) Battery and e-ink cost

The server waiting costs nothing (a thread blocked in `accept`); each guest is a thread and a few bytes every 15 s.
The real cost is the Wi-Fi radio kept on, and laying others' changes (partial updates, measured in the live spec: 5
to 70 ms each, waits for the pen).

**Recommendation: measure before adding anything.** An hour's session with and without sharing, the battery's
percent before and after. Only if it costs: stop by itself after a while with no guest (says so in the window).

### (d) A release: INTERNET permission, the privacy promise

Android has no LAN-only permission: any socket, even 127.0.0.1, needs INTERNET, and the store lists it. Sharing on
Wi-Fi never reaches the internet and nothing leaves the network the Manta is on.

**Recommendation: Share on Wi-Fi can be the first live feature released, before ell.ing Live** (no server of ours,
no account). README's "never goes online" becomes "goes on the network only for Share on Wi-Fi, only while it is on".
Before release:
1. **Encrypt as the live spec says** (AES-GCM with the key after `#`) and send only a hash of the key for access: today
   the key and the drawing go as plain HTTP, readable by anyone sniffing an open Wi-Fi. **Done** (branch `live-crypt`,
   "Encryption" below). Left to decide: the key's length (below).
2. **Cleartext only to loopback**: a network security config allowing plain HTTP to 127.0.0.1 alone (the app's own
   client); browsers reach the Manta, not the other way.
3. **The history kept in memory** grows with the session (a whole page each time one is shown, 4 to 66 KB): fine for
   an evening, worth a cap (drop history before the newest whole page) before release.

### Smaller choices made (say if they should change)

- The key is new each time sharing is turned on: a bookmarked link stops working next time. A key kept until Stop
  Sharing is a few lines more.
- Port 8080, fixed so the link looks the same each time.
- Turned off while the app is away, on again on return: the server never runs with the app out of sight.

## Notebooks from a computer or iPad (decided 2026-11-01, branch `live-books`)

Elling's two choices: **"Download first"** for (a), **"Edit the Manta's copy"** for (b). Built on Share on Wi-Fi: the
same server, the same key, nothing new to turn on.

### Step 1: the Manta's notebooks, downloaded

- **Where**: on the live page (`/live#key`), Menu → **Notebook Explorer** (the Manta's own row) shows the Manta's shelf:
  its covers and names, newest first, laid out by `NotebookLayout` as the browser app's shelf is (`WebBooks.shelf`), no
  New Notebook tile (no upload yet). A tile's tap opens it (step 2); its hold offers **PDF** and **Notebook File**.
  While a Manta notebook is open, Menu → **Export Page** offers **PDF**, **PNG** and **Layers** of the page shown, as
  the Manta's Page Settings does.
- **The server**: `LiveHost` answers `GET /books/...` (key required, as `/listen`) through `LiveBooks`, pure Java:
  `/books` the shelf (a line a notebook: path, last change, pages, unreadable), `/books/cover?path=&w=&h=` its front
  cover as the Manta's shelf draws it (`NotebookCovers.of`, greys, a byte a pixel), `/books/file?path=` the `.mpaint`,
  `/books/pdf?path=` (`NotebookExport.pdf`, as Export Now), `/books/page?path=&place=&as=` (`PageExport.bytes`, as
  Export Page). Downloads carry their file's name (`Content-Disposition`), so a plain link downloads them; on an iPad
  Safari's download goes to Files.
- **Same writers**: the PDF and page copies are made on the Manta by the writers its Export Now and Export Page use,
  moved to take a whole notebook (`NotebookExport.pdf(out, book, …)`, `PageExport.bytes(book, …)`); the browser app's
  `WebBooks` calls the same two. The browser makes nothing: older iPads stay quick, the bytes are the Manta's.
- **Saved first**: a request reads the notebook's file on the store's own thread, after the saves queued before it
  (`DocumentStore.liveLibrary`), and the open notebook is saved first (autosave brought forward), so a download holds
  what is on the Manta's screen, not the last minute's autosave.
- **Not yet**: upload (step 3); notebooks in folders show as the Manta lists them.

### Step 2: editing the Manta's copy

**One page for the room, the Manta's.** The browser always draws on the page the Manta shows; turning on either side
turns both. Picked over "each side its own page of the same notebook", which would have the Manta lay changes into a
page it isn't showing (decoded off-screen, its Undo, its wet paint, its save) and two pages to keep in step: more code
and a second way for the copy to drift. One page keeps one source of truth with what live pages already do.

- **Opening**: a shelf tile's tap sends a page message (`LivePage`, "open" and the notebook's path) through the relay.
  The Manta opens that notebook as its own shelf would (the open one saved first), on the page it was last drawn on,
  and leaves the notebook screens for the drawing screen. Opening the one already open only shows it.
- **Turning**: while the Manta has a notebook open, the live page shows the page bar, ‹ 3 / 24 ›. Its ‹ and › send
  "turn" −1 or 1; the Manta turns as its own bar does (`PaintActivity.turnPage`). The browser draws nothing until the
  Manta answers (4 s at most), so no mark lands on the page being left.
- **The answer, and the Manta's own turns**: whenever the Manta shows another page (`LiveProbe.shown`) it sends first
  a page message ("shown": the notebook's path, the place, the bar's words), then its tools and the page whole, as
  now. A browser seeing another place or notebook starts a blank page before the whole page is laid (a whole page
  laid over the last one would only darken it).
- **Saving**: the browser's marks reach the Manta as live changes do (one Undo step each, the Manta's own laying);
  the Manta saves them as it saves its own strokes. The browser keeps nothing.
- **Order**: the page messages travel in the relay's numbering with the changes, and the Manta lays them in that
  order, waiting while it is busy or its pen is down, so a turn never jumps ahead of marks sent before it.
- **The Manta stays usable**: it is a live page; its pen, menus and screens work as before.
- **Wi-Fi drops**: the live page covers its page with "Not connected" and takes no strokes until the connection is
  back (it listens again by itself, also after a line gone silent; the page starts over from the relay's history,
  which holds the Manta's page whole). No offline copy.

### Step 3: sending files up (branch `live-upload`, 2026-11-01; untried on the Manta)

Only ever as a new notebook, never over one (no copy lost to a stale one).

- **Where**: the Manta's shelf in the browser gets **Import** in its head, as the browser app's shelf has: a
  notebook's file, a picture or a PDF, several at once; the shelf is read again after, and the Manta's own shelf too
  when it shows. The browser app opened on the Manta's link (`/app#key`, the Manta answering `/books`) gets **Send to
  My Tablet** under Export Now: its notebook's file, as kept, sent the same way. Notes: "Can't read it", "Too large",
  "Sent".
- **The server**: `POST /books/add` (key required; sealed since `live-crypt`: the name in the sealed query, the file and the answer sealed), at most 64 MB (`LiveBooks.MOST_ADDED`; 413). `LiveBooks.add`:
  the name's folders dropped and its ending; a `.mpaint` read whole (`BookCodec.read`) before anything is written,
  then written whole and swapped in (`DocumentStore.create`'s `AtomicFile`, on the store's thread); a picture or PDF
  only when its first bytes are what its name says (`PictureImport.importable`; Import's own kinds), then made a
  notebook by `PdfImport` as Import's New Notebook (a picture: one page, as a one-page PDF), at the open notebook's
  page size; a name the shelf has gets the store's "name 2" (`DrawingFiles.unusedName`). Anything else 415, nothing
  written. One at a time.
- **Not yet**: no progress while a long PDF is made (the browser waits for the answer); "First 49 pages" isn't said
  to the browser; a photo from an iPad's library in HEIC is refused, as Import refuses it.

### Risks

- **Privacy**: plain HTTP on the LAN. Anyone on the same Wi-Fi who sees the key (sniffed from any request) can list,
  download and draw. Fine at home; encryption before any release, as (d) says. Downloads and pages travel in the clear.
- **Concurrent edits**: both sides drawing on one page is live pages' own case (pixels, the relay's order). A mark
  made in the second the Manta turns (its pen or another guest) can land on the new page: the page message and the
  mark cross. Rare; the browser waits for the answer to its own turns.
- **Load**: a notebook's PDF is made on the Manta (as Export Now, seconds for a long notebook), one request at a time
  a connection; covers read each file whole (no cache yet).

## Off the Wi-Fi: ell.ing's relay (2026-11-03, branch `live-relay`)

Many Wi-Fis (guest, café, school, some homes) keep devices from reaching each other, so a guest often couldn't reach
the Manta. Approved by the owner: a relay on ell.ing that the Manta and every guest connect out to, on any network.

- **One row, no new choice**: Draw Together's first start asks whether https://ell.ing/mbd/live/ answers (4 s). If
  so it shares there; if not (no internet), on the Wi-Fi as before. The way is kept till sharing stops (and through
  an install or a crash, as the key is). The address is one constant, `LiveHost.RELAY`.
- **The key**: through ell.ing, 10 letters: the relay sees the access hash, and a 4-letter key would be tried offline in
  seconds; 10 letters with `LiveCrypt`'s stretching are out of reach. Nobody types it: the window shows ell.ing/mbd's
  four-letter code over the QR code (`https://ell.ing/mbd/live/#k7qmw2xrh4`, version 3), and "Online, while the app is
  open." On the Wi-Fi: the 4-letter key, as before.
- **The relay**: `relay.py` itself, run `public` on the server (its own service on 127.0.0.1:7897, behind nginx), the
  guest page from the same pack the APK carries. Rooms by access hash; limits on rooms, listeners, history, change size
  and idle time; a room asked for that isn't there counts as a try, 20 a client in 10 minutes (the owner's ask), 1000
  in all. No logs. Details and deploy: superdood.org's server notes (not in this repo).
- **Not through the relay (yet)**: the Manta's notebooks (Notebook Explorer, downloads, Import); they need the Manta's own
  host. A guest's Notebook Explorer there says "Not connected".

## Encryption (2026-11-01, branch `live-crypt`)

(d) 1 built; untried on the Manta. The browser-app spec's **Sealed** row has the details and checks.

- **One cipher, both sides**: `LiveCrypt`, pure Java (AES-256-GCM, SHA-256; no javax.crypto), on the Manta and, through
  the wasm engine, in the browser: the page is plain http from a LAN address, not a secure context, so no WebCrypto.
- **The key never travels**: the relay is given its access hash; the cipher's key is another hash of it. Every change,
  page message, the shelf, covers, downloads and uploads go sealed, and the notebook's or file's name in a `/books`
  ask or upload too (`q=`). One that doesn't open is dropped (a change) or refused (403, a `/books` ask or upload,
  nothing written).
- **No names** (2026-11-03, branch `live-names`, approved by the owner): what a relay (relay.py, on ell.ing or the Mac,
  and `LiveHost`) is told of who is here is an opaque id, `from=` on every send and listen: 16 hex digits
  (`LivePresence.ID`), random and new each session (the Manta's at each start, a guest's at each page load), never a
  name or anything of the device. Each relay takes only that (anything else is `-`). It still routes by it: a
  sender's own changes skipped when they come back, who listens counted (the Manta's guests, its awake and doze). The
  Manta's id is half random, half a hash of that half keyed with the link's key (`LiveCrypt.hostId`), so a guest tells
  the host from the others (Host gone otherwise) and a relay can't. Before, the ids were `manta-` and the Manta's
  uptime, `browser-` and a random part: the relay learned who was the host and roughly when it started. The relay still
  sees which id sends `start=1` (the host's page message, its history's start), and how many listen; neither names
  anyone. An old guest tab on a new relay: its `browser-…` id is `-`, its own changes come back to it laid again, and it
  sees no host (Host gone) until reloaded; nothing on the relay or the Manta breaks. An old Manta on a new relay counts
  itself a guest and lays its own changes again: install the app and the relay together.
- **Cost**: a 4 MB change (the relay's largest) sealed in 42 ms on the Mac's JVM, 85 ms in Chrome's WebAssembly. On the
  Manta, to be measured (logcat's "packed in" time does not include it).

**The key's length: 16 letters** (decided 2026-11-01). 8 letters was about 40 bits; anyone on the Wi-Fi sees the access
hash, so could try every key offline (hours on a laptop, minutes on a gaming GPU). 16 is about 79 bits, out of reach.
The link grows by 8 characters and stays a version 3 QR code (the longest, `http://255.255.255.255:65535/live#` and 16
letters, is 50 of its 53 bytes; `LiveHostChecks`); only typing it by hand is longer. Untried: how the longer link sits
in Share on Wi-Fi's window on the Manta.
