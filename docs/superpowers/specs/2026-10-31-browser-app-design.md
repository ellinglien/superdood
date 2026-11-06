# My Bad Drawings in the browser

2026-10-31. A design to agree on; no app code changed. Goal: the whole app in a browser (iPad, phone, desktop); first
the drawing screen for live-page guests. Order agreed with Elling: the engine as WebAssembly (branch `web-wasm`), the
drawing screen on the Manta's own layouts, then the rest. Java files are under `app/src/main/java/io/github/mpdairy/monopaint/`.

## 1. What carries over

Measured on `inkwash-v2-lab` (5fbd9584): 314 files, 76,246 lines.

| | Lines | Share | What |
|---|---|---|---|
| **Pure Java now** (`scripts/test_inkwash.sh`'s `sources`, compiled on the desktop without Android) | 49,822 | 65% | the engine; every layout (`DrawLayout`, `ToolsLayout`, `LabLayout`, `NotebookLayout`…); the screens' models (`ToolSlots`, `ToolsPage`, `BrushLab`, `BrushVoice`); the gestures (`MultiFingerTap`, `PinchPan`, `FingerTool`, `HoverNib`); the page and notebook (`ToneDocument`, `DrawingBook`, `BookCodec`); exports (`NotebookPdf`, `PsdWriter`); text measuring (`LabText`) |
| **Thin seam** (no more than 2 Android imports) | 2,425 | 3% | `DocumentStore` (only `AtomicFile`), `PaintPreferences`, `InkWash`, `ShapeStroke`, `DryingTicker`… |
| **Android UI, redrawn on the web** | 22,233 | 29% | `PaintActivity` 3,040, `DrawingPad` 2,709, `InkWashBar` 1,583, `NotebookScreens` 1,577, `BrushLabActivity`, `ToolsActivity`, `PageTurner`, `SizeSlider`… |
| **E-ink only, left out** | 1,766 | 2% | `NativePen`, `DirectEink`, `StrokePanel`, `Device`, `PageTurnDisplay`, `PanelPreviewLayout`, `ProbeActivity`… |

**Reuse: about 68% of the app's code runs in the browser as it is or behind a small seam.** The 29% to redraw is
mostly Android plumbing that a browser does itself, so its web side should be well under its size.

**Tried** (`docs/brush-lab/teavm-pilot/ui-probe/run.sh`): `PilotC` lays out ten screens (the Manta, the Nomad, iPads
and a phone both ways, two desktop windows) with `DrawLayout`, `ToolsLayout`, `LabLayout` and `NotebookLayout`, and
their words. TeaVM's JS and WasmGC give **every number the same as the JVM on all ten**, with no new shims. 529 KB of
WasmGC (the layouts reach far into the brush model); about 10 ms a layout, once per resize.

What the numbers say:
- iPad portrait: the Manta's layout, with room to spare. iPad landscape and desktop windows: fits.
- Phone portrait: the drawing screen fits (8 slots), Brush Lab doesn't (its right column 4 dp wide).
- Phone landscape: the column shrinks below its least (rows of 12 dp). Not usable.

So a phone gets the drawing screen in portrait only; Brush Lab and the Tools page are for iPads and desktops.

Found on the way: the pilot's build doesn't compile on `inkwash-v2-lab` today: `WebParity` calls
`ToneDocument.compositeTones` and an eight-argument `PictureImport.lay`, which only the live branches have. The probe
has its own build for that; `web-wasm` will meet it too.

## 2. The web shell: three ways

**(a) Wasm core, thin web shell (recommended).** WebAssembly holds the engine, the layouts, the models, the gestures
and the notebook. JavaScript asks a layout for its numbers and words, puts plain HTML buttons where they say, and
draws the page and the brush pictures (`WildSample.legibleTray`, `WildSample.modular`) on canvases.
- Single source: everything that decides (what a tap does, which size, the words, the rows' heights) is the Manta's
  Java. Only the look of each widget is written twice (`InkWashBar`'s drawing, `SizeSlider`, `GreyStrip`).
- A Manta change reaches the web at the next build: the web build compiles the same `sources` list, so a moved row
  or a new word shows up with no web change. A new widget needs its web drawing too.
- One rule keeps it single-source: when web code would copy a decision from an Android class, move that decision
  into a pure class first, as `DrawLayout.tap` and `FingerTool` already do. The Manta gains a desktop check from it.
- The browser gives text, scrolling, popups and screen-reader text (`aria-label` from the same words) for free.
- Cost M per screen; risk low; no Android file changes.

**(b) A small View layer in Java, implemented by Android and by the web** (rectangles, text, bitmaps, touch), with
`InkWashBar` and friends rewritten on it. Nearly all single-source, but it rewrites the Manta's UI, upstream files
included (against `docs/INTEGRATION.md`), every screen tried again on e-ink, text fields and popups rebuilt by hand.
Cost L–XL; risk high on the Manta. Only if (a)'s second copy of the widgets starts to drift.

**(c) A hand-built web UI over the wasm engine**, as `probe.html` and Brush Studio are now. Quickest first page, but
layouts, words and tap rules drift, as the engine did (`2026-10-31-brush-system-roadmap.md`, (c)). Probes only.

**Threading.** The web has one thread for the engine (the pilot's shims run `Cores`' work in place). Start on the main
thread: wasm draws a stroke about 1.5 times the Manta-on-JVM time (`NOTES.md`, 7.5 ms against 5.1). Settling a
wash (52 ms a stroke) must be cut into ticks, which `WatercolorSession.tick` and `SettlePacer` already do, paced by
`requestAnimationFrame`. Move the engine to a Web Worker only if Elling feels the pen lag. `QuickStroke` needs the
seam `NOTES.md` names: render at lift on the caller's thread.

## 3. Storage

- **Notebooks in the browser: the Origin Private File System**, each notebook the same `.mpaint` file the Manta
  writes (`BookCodec`, `DocumentCodec`, `RecoveryCodec` compile as they are). `DocumentStore`'s seam is its file:
  write a whole new file then swap it in, as `AtomicFile` does. Settings and the toolbar through a small key–value
  store, the way `BrushLibrary.Store` already works (localStorage on the web). No IndexedDB needed.
- **Safari deletes a site's storage after 7 days without a visit**, unless the page is added to the Home Screen.
  So: the app asks for persistent storage, suggests Add to Home Screen once (on an iPad), and the notebook screen
  offers Export.
- **Import and export:** the same `.mpaint` files through the file picker and downloads (Files, AirDrop). PDF and
  Layers from the pure writers; PNG through the canvas.
- **To and from the tablet**, three ways:
  1. **Files** (recommended first): export on one, import on the other. Nothing online.
  2. **Send to My Tablet** (planned for brushes): pair once, then a notebook goes through the ell.ing relay,
     encrypted as live pages are. Needs the Manta's app open and online.
  3. Live pages already carry a page each way. Not a sync, but enough for a guest.
- **Privacy, the app's promise**: the web app is static files. Drawings stay in the browser. It goes online only for
  Live, Community and Send to My Tablet, each asked once in the app's words ("only if you allow it"). No accounts, no
  analytics, no logs. A guest's live page asks nothing more: the link is the permission.

## 4. Input

- **Pen**: Pointer Events; pressure through the Manta's curve and tilt (`tiltX/tiltY` or Safari's altitude and
  azimuth), as `engine.js`'s `pointerSample` already does for `probe.html`. The Pencil's 240 Hz samples from
  `getCoalescedEvents()`, gathered by `PenBatch`.
- **Hover**: an Apple Pencil hovering (newer iPads) sends moves with no buttons: `HoverNib` outlines the nib as on
  the Manta. With a mouse, the nib outline is the cursor.
- **Fingers**: `MultiFingerTap` (two fingers Undo, three Redo), `PinchPan` (zoom and pan; no padlock in the browser since 2026-11-01, the whole page fitted by default, `CanvasViewport.whole`),
  `FingerTool` (what one finger does; a finger never draws while a pen is in use). The page takes
  `touch-action: none` so Safari's own zoom, double-tap and text selection stay out.
- **Keyboard** (desktop), only what a gesture already does: Cmd/Ctrl+Z Undo, Shift+Cmd/Ctrl+Z Redo, `[` `]` size,
  E eraser, 1–0 slots, space-drag to pan. No web-only commands.
- **What differs from e-ink**: no flashes or cleaning (`Ui.cleanWindow`, `Ui.flashWindow`), no fast pen layer, no
  Refresh: the page redraws its changed 64 px tiles at 60 fps, wet paint each tick as it settles. The page shows in the Manta's 16 greys, as `probe.html` does, so a guest sees what
  the host sees (PHILOSOPHY 6). Page tilt (gravity) waits: iPadOS asks permission for motion, desktops have none.

## 5. Milestones

| | What | Size | Reuses | Elling tries |
|---|---|---|---|---|
| **M1** | **The drawing screen for live guests.** The link opens the page; the host's ten slots, size and grey arrive with it (the join message gains the `ToolSlots` codes); the column as `DrawLayout` lays it out, the size slider (`DrawLayout.sliderAt`), Undo and Redo (two and three fingers, and the page's Undo), the eraser (`EraseBothStroke`), mirrors as the host set them (the page's MIRR section, `Mirrors`, `MirrorStroke`), zoom (`PinchPan`, `CanvasViewport`). Changes travel as live pixels (`LiveChange`, from the live branches). No tool picker, notebook, menu or Play. | L (M once `web-wasm` exports a stroke) | engine, `DrawLayout`, `ToolSlots`, gestures, `ToneDocument` | the link on the iPad with the Pencil and on a phone, beside the Manta: same strokes, same column, two-finger Undo, pinch, mirrors |
| | **M1 done (branch `live-m1`, 2026-11-01), untried by hand.** `scripts/live-probe/live.html` at the relay's `/live`. The host's tools travel as `LiveTools` (the page's size, `ToolSlots.Toolbar`'s lines, the tool in use, the eraser's size, the MIRR section; eight zero bytes then text, so no change reader takes it), sent by `LiveProbe.shown` before the page. Moved into pure Java and used by both sides: `StrokeStart` (a tool's stroke as `InkWash` starts it: mirrors, Filled Shape), `DrawLayout.tops`/`gapHeight` (the column stacked as `InkWashBar` stacks it), `DrawLayout.lineWords`/`toolName`, `ToolSwatches.ownPx`, `FingerTool.Choice.of`, `HoverNib.SHOWN_MS`/`AWAY_MS`/`TICK_SIDE`. `WebEngine` exports the layout (JSON), the tools, the tray strokes, the nib, the picker box's sums, `MultiFingerTap`, `PinchPan`, `CanvasViewport` and `FingerTool`. What every live page shares (bytes, Undo of your own, what a stroke changed, drawing on) is `live.js`, used by `probe.html` too, which stays at `/`. `live-check.cjs`: two headless guests (iPad portrait with a pen, phone portrait with a finger) on a relay of their own; see CHANGELOG for what differs from the Manta. | | | |
| **M2** | Brush Lab and the Tools page, iPad and desktop | L | `BrushLab`, `LabLayout`, `LabPages`, `ToolsPage`, `ToolsLayout`, `ToolPresets`, `ToolKits`, `BrushVoice`, `WildSample` | make a brush on the iPad, put it in a slot, draw with it live |
| | **M2 done (branch `live-m2`, 2026-11-01), untried by hand.** On `/live`: a long press (`DrawLayout.LONG_PRESS_MS`, Android 11's) on a slot opens the tool picker (`DrawLayout.Picker`, moved from `PaintActivity`), on the eraser its kinds, on the grey strip Opacity; the Tools page and Brush Lab on an iPad or desktop (`LabLayout.fits`; the lab `LabLayout.scrolled` in landscape), every state and word `ToolsPage`'s, `BrushLab`'s and their layouts' through `WebEngine` (JSON and taps). The guest's own `ToolSlots` over a store kept in localStorage (`WebEngine.keep`/`kept`); a host's tools fill the slots the guest hasn't changed. Moved into pure Java besides: `ToolSlots.pick`/`take`, `LabLayout.moduleWords`/`openDials`/`rowKind`/`rolls`, `BrushLab.Swap.caption`/`cellText`/`saveWords`. The Manta's Brush Lab has no long presses (a dial's double tap resets it; the web does the same). Missing: Open, Cut Stamp, Clean Brush, Grey Shift's set, the lab's picker box. `m2-check.cjs`. | | | |
| **M3** | Notebooks and storage: the shelf, pages, covers, templates, Page Settings, export and import, recovery | L | `DrawingBook`, `BookCodec`, `NotebookLayout`, `NotebookCovers`, `NotebookTemplate`, `PageStacks`, the exports | a notebook from the Manta opens in the browser and goes back unchanged |
| | **M3 done (branch `live-m3`, 2026-11-01), untried by hand.** `/app` on the relay: `live.html` on its own (no live page; a guest's `/live` as before), its notebooks drawn by `notebooks.js` from `WebBooks` (web/engine), every layout, word and rule the Manta's: the shelf (`NotebookLayout`; Import added, the browser's only way in), inside a notebook (its page, the strip with Settings first and +, ‹ 3 / 24 ›, Open, Use on Cover, Delete Page), the settings list (`LibraryCard`: Template, Page Colour, Surface, Cover, Cover Picture set here; the rest shown as set, greyed), the Template, Surface and Cover screens (`CardSheet`), Page Settings (Template, Page Colour, Surface, Layers, Move; Export Page). Export Now offers the PDF and the notebook's file; Export Page PDF, PNG and Layers; all are the bytes the Manta's writers give for the same file (`m3-check.cjs`). Storage as section 3: the Origin Private File System, each notebook its `.mpaint` written whole then swapped in; persistent storage asked at the first save; Add to Home Screen suggested once on an iPad. The drawing screen gets Menu (Settings, Notebook Explorer) and the page bar's ‹ 3 / 24 ›; Undo and Redo are the page's own. Moved into pure Java: `NotebookStrip`, `CardSheet.offered`/`chosen`/`picture`, `PageExport.bytes`, `NotebookExport.Plan`/`pdf`, `NotebookPdf.title`. Missing: Random From, See-Through Pages, Nib Preview, Self-Portrait Prompt, No Erasing, Timed Pages, Lock Pages and Tools (shown, not set), the covers' label words (the Manta letters them with its own fonts), magnets in copies, the page bar's previews, a swipe to turn, recovery of unsaved strokes after a crash (the last save stays), Import Image (M4). | | | |
| **M4** | The rest: menu, Play's toys (magnets, mirrors), Shapes, Import Image, Timed Pages, Replay, Go Live as host, Community, Send to My Tablet | L | `Magnets`, `WordPacks`, `PictureImport`, `TimedPages`, `StrokeLog`… | one thing at a time, as on the Manta |
| | **M4's drawing screen done (branch `live-m4`, 2026-11-01), untried by hand.** `scripts/live-probe/m4.js`, a module of its own over live.html's parts: Menu (Finger Action, Left-Handed: the column on the right, mirrored), the smudge tool's row and its chooser (Blend, Water; Clean Brush), Clean Brush on a dirty slot's long press, the Dropper, the Lasso (a loop lifts; drag, turn by the knob, scale by a corner; Cut, Duplicate, Delete, Paste), Shapes and Filled, Play (Text with Make Ink; Mirrors: Copy Strokes, Place Mirror, Kaleidoscope, guides), Zen Mode (its button, two quick three-finger taps), Hold to Snap, Grey Shift's grey per stroke and Keeps Paint per tool (as `InkWash.stroke`), Springy brush's drawing (a PNG the relay serves). The Manta sends its tools again mid-session when they change (`LiveProbe.toolsChanged`, only when they differ); a guest's own slots, tool in use and mirrors stay its own. Moved into pure Java and used by both sides: the menus' rows (`DrawLayout.Row`, `fileRows`, `playRows`, `shapeRows`, `shapePicked`, `MirrorRow.rows`/`change`/`stopsPlacing`/`kept`), a text box's menu and drag (`TextBoxes.menu`/`restyled`/`dragged`), the Lasso's pieces (`LassoPieces`; `DrawingPad` uses its `duplicated` and `erase`), held paint (`ToolSlots.toolKey`/`holder`/`dirty`/`clean`), `MultiFingerTap.ZEN_TAP_MS`, `Mirrors.TOO_SHORT`; `ShapeStroke` (MonoPaint's, unused on the Manta, whose `ShapePreview` is native) draws a guest's shapes. Desktop check `DrawMenusChecks`; browser check `m4-check.cjs`. Left out for a guest, as live pages carry pixels: Magnetic Poetry and Real Mirror (the page's own and the screen's own; the host would never see them); text boxes reach the host only as Make Ink. Not yet: Brush Lab's Open and its size and grey box, Grey Shift's set, the Stamp tool and Cut Stamp, the page strip (one host page; notebooks are M3's), Show Kinds, the sand timer and the wet indicator, Import Image, Timed Pages, Replay. | | | |
| **Fast LAN** | **Less delay on live pages**, the Manta sharing on its Wi-Fi with an iPad guest | S | `LiveChange`, `LiveProbe`, `LiveReveal` | draw on the iPad while the Manta shares: the stroke drawing itself on the Manta as you draw; Undo on both; the Manta's round trip with the Wi-Fi lock |
| | **Done (branch `live-fast-lan`, 2026-11-01), untried by hand.** A guest's stroke goes in parts while it is drawn, every 80 ms (one send in flight, in order; a slow link sends fewer, larger ones), the rest at the lift; a part has no path, so the others lay it at once, and a later part carries `LiveChange.joins` (the flags byte's 2) so the Manta joins it to the sender's part before: one Undo step, as a change's slices; the guest's Undo takes back the whole stroke. A change arriving while another draws itself on lays the rest of that one at once (Manta and browser); the Manta lays what waited for the pen at its lift. The Manta holds a low-latency `WifiLock` while listening (untried: no device). `live-check.cjs --host`, iPad pen to phone, before → after: pen down to first pixels 1028 → 95 ms (1 s stroke), 3021 → 93 ms (3 s); lift to all shown 611 → 6, 614 → 17. LiveHost keeps `/send`'s HTTP/1.1 connections open, so a part pays no new connection's round trip on the Wi-Fi (Chrome's fetch reuses it: 1 of 68 sends on a new one; localhost 0.29 → 0.11 ms a send). Parts are sealed as every change (`LiveCrypt`). Left out: the Manta's own strokes in parts (its pen feel first; it would take `LiveProbe` sending `takeLive`'s blocks every 80 ms while the pen is down, read and packed off the main thread so the pen keeps its feel; the receivers already lay parts at once). | | | |
| | **A notebook's settings and its page's tools done (branch `live-time`, 2026-11-01), untried by hand.** On `/app`, every row of a notebook's settings is set as on the Manta (none greyed): See-Through Pages (Off/On, its five by a long press), Nib Preview, the Self-Portrait Prompt, No Erasing ("No eraser, no undo"), Lock Pages (greyed while nothing is timed), Random Template's Random From (ticks, Done; a long press on Random), Timed Pages' slider, Tools (My Toolbar, Surprise Me, Library opening the Tools page, Each Page) with Brushes on Bar; Page Settings' own No Erasing and Timed Pages (its time's slider), Show Through and Ghost. All kept in the `.mpaint` by the Manta's own codec. On the drawing screen (`scripts/live-probe/pages.js`, its own module): a stroke, Undo and Redo refused with the Manta's notes ("Erasing is off", "Undo is off", "Page locked"), the eraser's and smudge tool's tiles and the Lasso off, the slots past Brushes on Bar gone, a notebook's own toolbar in the slots (the app's back on the shelf); Nib Preview off hides the ring; tracing paper's ghosts under the page (`GhostPlane`, as `Ghosts` builds them); Timed Pages' countdown in the column's TIMER row (the sand timer's while none shows), the number's blink, the padlock, Time's up with Next Page, the page locked under Lock Pages; the Self-Portrait Prompt's card for a page added (Deal Another, Close, Start handing a timed card's time to the sand timer) and again on opening the page; Replay Page in Play (1×, 2×, 4×; a pen or finger landing ends it; the browser's pen strokes are now recorded as the Manta's); the page bar's previews (a window round the page drawn on, ghost and hidden marks, a tap draws on a page, a hold opens Page Settings, + adds a page); inside a notebook a swipe across the page turns it; Delete Page refused for a marked page under No Erasing. Moved into pure Java and used by both sides: `NotebookRules.refusal`/`shownCountdown`, `ReplayRun` (from `PageReplay` and `InkWash.replay`), `ToolSlots.withTools` (from `PaintActivity.chooseTools`), `StripGesture.plusSlot`/`round` and `NotebookStrip.ghosting`/`hidden` (from `PageTurner`'s Strip), `NotebookLayout.swipe` (from `NotebookScreens`). Browser check `pages-check.cjs`. Missing: the previews' riffle (a drag across them) and their window sliding at the ends, Timed Sections (a Diary Page's timed boxes), No Erasing's finger panning in place of the eraser, the Tools row's slot marks, the Tools page's "Notebook picks tools each page" note, Left-Handed mirroring of the page bar (as before), the covers' label words. Timed Pages counts on the browser's monotonic clock (`performance.now`), as the Manta's on its uptime. | | | |
| | **M2 and M4's gaps: Brush Lab's Open, its picker box and Grey Shift's set; Show Kinds, the sand timer and the wet indicator (branch `live-lab`, 2026-11-01), untried by hand.** `scripts/live-probe/extras.js`, a module of its own. Brush Lab: Open in About This Brush's header (on while its sheet is open), its sheet under the header (Paste code: a code that doesn't read says so on its last line; This brush's code and Copy; the library a page at a time, the one Save writes over with its tab; Cancel); the drawing screen's picker box turned a quarter, lying across, the readout beside it (a grey picked dips the scratch pad's Wet ink brush); Grey Shift's set in Control's card; the scratch pad's wet indicator beside Clear. No long presses in the lab, as on the Manta. The column: the sand timer beside Shapes (Off and Timed Pages' times; its countdown in the countdown's row; Time's up and Done at zero, once the pen lifts; paused while the page is out of sight or Brush Lab or the Tools page is open), Show Kinds under it (Pen, Paint, Dry Media, each eye stepping Full, Half, Faint, Hidden; the screen only: what a live page sends stays the page's own; a stroke of a hidden kind refused with the Manta's note, the eraser never taking a hidden kind, a tool taken up saying when its kind isn't Full), the wet indicator under them (dashed, half, full; a tap dries the paint as it would dry, only sooner, MonoPaint's blow dryer blowing). Moved into pure Java and used by both sides: `WetLook`, `BrushLab.greys`, Open's sheet's words and sizes (`LabLayout`), Show Kinds' popup (`DrawLayout.kinds*`), `EraseKind.hiddenNote`/`takenUp`, `SandTimer.labels`/`chosen`/`waits`/`countdown`, `DrawLayout.boxSide`, `ToneDocument.compositeTones` with Show Kinds' steps. Desktop check `LabColumnChecks`; browser check `extras-check.cjs`. Still missing: Show Kinds' Template column (the browser's pages show no guides' step yet), the sand timer with Timed Pages (not in the browser yet), the shapes tool refused under Show Kinds (its stroke isn't checked yet), the code's middle cut (the browser cuts its end), Clean Brush in the lab. | | | |
| | **Upload over Share on Wi-Fi done (branch `live-upload`, 2026-11-01), untried by hand.** The Manta's shelf in the browser gets Import: a `.mpaint`, a picture or a PDF sent up (`POST /books/add`, key, 64 MB), each a new notebook on the Manta, never over one (a name taken: "name 2"); a notebook read whole before anything is written, then written whole and swapped in by the store; pictures and PDFs checked by name and signature, made notebooks by `PdfImport` (a picture: one page). `/app` on the Manta's link: Export Now's Send to My Tablet. Moved into pure Java: `NotebookNames.imported` (from `WebBooks.importName`), `PictureImport.importable`. Checks: `LiveHostChecks.added`/`imports`, `books-check.cjs`. Missing: progress for a long PDF, "First 49 pages" told to the browser, HEIC photos. Spec: manta-host, step 3. | | | |
| | **The Stamp, Cut Stamp and Import Image done (branch `live-stamp`, 2026-11-01), untried by hand.** `scripts/live-probe/stamp.js`, a module of its own, on `/live` and `/app`: a slot holding a Stamp offers **Stamp ▾** in its picker, its menu beside the slot (its card, Cut Stamp, Turns, Spacing, Ink Fade); **Cut Stamp** from that menu, the Tools page's corner and Brush Lab's bar (there on the scratch pad, its button "Nothing to cut" for a moment), the lasso in marching ants, "Draw round the shape to cut", "Stamp cut", "Nothing to cut"; the nib kept in the guest's store (localStorage) as the Manta keeps it in its preferences. **Import** in the menu (a guest's too: `DrawLayout.fileRows` gives it Import, a picture reaching the host as pixels) takes a picture from the browser's file picker, or one dropped on the page: fitted inside what shows, moved, resized from its handle, pinched, Cancel and Place along the page's foot, its outline only while it moves, as on the Manta; Place lays it on the layer drawn on, one Undo step; a locked page says "Page locked". A guest's stamps and pictures go to the host as live pixels. The host's cut Stamps' nibs now travel in `LiveTools` ("nib" lines, only for a cut Stamp), so a guest prints the host's stamp, not a round one. Moved into pure Java and used by both sides: `ToolSlots.cutStamp` (`PaintActivity.stampCut`), `DrawLayout.StampRow`/`stampRows`/`stamped` (`InkWashBar.stampMenu`), `PictureImport.Placing` (the placing gestures, `PicturePlacer`'s), `Frame.handle`, `PictureImport.BAR`/`BUTTON`. Desktop checks in `StampChecks`, `PictureImportChecks`, `LiveToolsChecks`, `DrawMenusChecks`; browser check `stamp-check.cjs`. Left out: Import PDF (the browser has no PDF reader without a new library; pictures only), the drawing screen's stamp tool off the column (the Manta's has no place in the column since 2026-10-28). Differs: a picture is resampled by the browser (its decode and smoothing), so its greys can differ from the Manta's by a tone at edges; a host's change arriving while a picture is placed wipes its preview there until it next moves. An older guest can't read a host's tools that carry a nib (it says "newer version"). | | | |
| **Sealed** | **Encryption before any release** (manta-host spec, (d) 1) | M | `LiveCrypt` on both sides | a live page and a download on the iPad, as before |
| | **Done (branch `live-crypt`, 2026-11-01), untried by hand.** `LiveCrypt`, pure Java: AES-256-GCM and SHA-256 written out (no javax.crypto), so the Manta and the browser run the same code; the browser through the wasm engine (`engine.js` `E.crypt(key)`), as plain http on a LAN address has no WebCrypto. The key after the link's # never leaves either side; the relay gets `?key=` its access hash (SHA-256 of "MBD live access\n" and the key; the cipher's key is SHA-256 with another prefix). Every change, the tools, page messages (open, turn, shown) are sealed once before `/send` and opened once after `/listen` (`LiveProbe.post`/`listen`; `live.html` `post`/`lay`, books.js `ask`); one that doesn't open is dropped. `/books`: the query sealed as `q=` (the notebook's name in it), the answer sealed, its plain bytes the file's name, a line feed, the bytes (`LiveHost.book`; books.js `got`, which saves a download from a blob under that name; no `Content-Disposition`). Nonces: 12 random bytes a message (SecureRandom; `crypto.getRandomValues`, which plain http has). The Mac relay passes sealed bytes as it passed plain ones; `live-probe.txt` takes the key after a # on its line (none: sealed with the empty key, as `/live` with no #). Checks: `LiveCryptChecks` (FIPS 180-4, the GCM spec's AES-256 cases 13 to 15, javax.crypto over 0 to 300 bytes and 4 MB, every changed bit refused), `crypt-check.cjs` (the browser's against Node's AES-GCM, with WebCrypto taken away), `live-check.cjs` (every change through the relay sealed), `books-check.cjs` and `LiveHostChecks` (sealed both ways, the key itself refused). A 4 MB change: 42 ms to seal on the JVM (this Mac), 85 ms in Chrome's WebAssembly; the Manta untimed. Uploads (`POST /books/add`, the shelf's Import and /app's Send to My Tablet) sealed the same way: the file's name in the sealed `q=`, the file sealed, the answer (its new path) sealed; not sealed with the key 403, nothing written (`LiveHostChecks`, `books-check.cjs` reads what goes up). Keys are 16 letters now (about 79 bits; 8 could be tried offline from the access hash), a version 3 QR code still. Not yet: no replay guard (a sniffer with the access hash can resend a sealed change, ask or upload, unread); a 64 MB upload is sealed whole in the browser (about 1.4 s at Chrome's speed, more memory). |

The web builds from the same commit as the APK; the ui-probe check in the build fails a layout that differs.

## 6. Risks and what stays out

**Risks**
- **WasmGC needs Safari 18.2** (iPadOS 18.2, December 2024). An older iPad gets the JS build, whose wet paint is
  off by a tone or more (48 of 137 brushes exact, `NOTES.md`).
- **Phone memory**: a whole-page surface for the JS engine was about 200 MB (`probe.html`'s note); the Java page's
  tiles should be less, to be measured on a phone in M1.
- **One thread**: settling and quick strokes must be sliced (section 2), or the pen stutters.
- **Text width**: the layouts measure with the face's own widths (`LabText`), so the web must load the same fonts
  (iA Writer Duo and Ukulele, SIL OFL) or rows don't fit.
- **Brush Studio and the web Brush Lab** would be two web brush editors after M2 (decision 2).

**Left out**: everything e-ink (the fast pen layer, `NativePen`, `DirectEink`, flashes, Refresh, display recovery,
`PageTurnDisplay`), the Manta's edge strip (`EdgeHold`: no hardware), the Nomad simulation, the bench, Android's
folders and permissions (`DrawingStorage`), `ImportPicker` (the browser's picker has Cancel), the export folder
(`AutoExport`: a download instead), gravity until asked.

**Few clear tools**: the web adds no tool, mode or setting. Same column, same words (`docs/TEXT-STYLE.md`), the
keyboard only mirrors gestures. Anything web-only must pass PHILOSOPHY 7 on its own. Leaving out Refresh and the
edge strip takes buttons away.

## Decisions for Elling

1. **The shell**: (a) wasm core and a thin web shell, with the rule that a decision moves into Java before the web
   copies it (recommended), or (b) one View layer shared with the Manta.
2. **Brush Studio after M2**: Studio becomes the web app's Brush Lab plus its extras (export, community; one editor,
   recommended), or the two stay apart.
3. **Notebooks between the Manta and the browser in M3**: files only (recommended first), or Send to My Tablet
   through the relay from the start.

## Decided, 2026-10-31

Elling chose the recommended option in all three: the thin web layer over the Java layouts (a decision the web would
copy moves into Java first); Brush Studio becomes the web Brush Lab after M2; notebooks move as files first.
