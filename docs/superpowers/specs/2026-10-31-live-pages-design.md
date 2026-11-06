# Live pages: drawing on one page together

2026-10-31. Agreed with Elling in conversation; build order and its first step still need trying by hand. Revised
the same day after looking at open-source whiteboards (below).

## What it is

One page, drawn on at the same time by people on Supernotes and in web browsers: for drawing together, and for
pencil-and-paper games (exquisite corpse, dots-and-boxes, Pictionary, battleship). The page is the game; there is no
game code.

Not in this version: a whole shared notebook, pages turning together, chat, others' cursors, two Supernotes on the
same Wi-Fi without the internet.

## How a session goes

- **Go Live** (Menu, on the Manta). The first time, it explains that the app connects to ell.ing only while live,
  and asks. Then it shows a short code and a link, like `ell.ing/live/K7QM#<key>`.
- **Join**: a browser opens the link; another Supernote picks Menu → Join and types the code. A Supernote that joins
  gets the page as a new page in the notebook it has open, and keeps it.
- The room lasts while anyone is in it.
- Leaving is Menu → Leave (or closing the app). The page stays as it was; nothing more comes in.

## What travels: the pixels a change made

Every change (a pen stroke, the eraser, fill, shapes, smudge, finger, mischief) travels as **the pixels it
changed**: the layer, and for each changed pixel its tone before and after (256 tones, as the page stores them),
deflated. A few KB for a stroke.

- Whoever makes a change draws it with their own engine; everyone else only puts the pixels in. So a stroke looks
  the same on every screen, browser included, whatever engine or version drew it, and taking one in costs the Manta
  no engine work. (Drawpile moved from sending pen input to sending what the brush made, for this reason.)
- Two people drawing at once only meet where their marks touch.
- Pixels from others are shown with an ordinary partial update; the pen's fast layer is only for the local pen.
- Mischief that changes the page later (Lemon Juice, Fading, Mold) runs on the drawer's tablet, and its changes
  travel when they happen. If the drawer has left, they don't reach the others.
- Replay on a live page holds only your own strokes.
- **The page itself** is sent once to each person who joins, by whoever started the room (the `.mpaint` page).

## Order: draw first, put right after

The relay numbers every change and every screen applies changes in that order. Your own change shows at once; if
the relay puts someone else's change before yours, your screen puts theirs in and then yours again over it, where
yours changed pixels. Every screen ends up the same. No engine runs again for this: it's pixels.

## Undo

Undo takes back only your own changes, about 10 back. It travels as a change too: each pixel back to its tone
before, but only where the pixel still holds the tone your change gave it, so marks others drew over it stay. Every
screen does the same check and ends up the same.

## The browser page

`ell.ing/live/<code>#<key>`: the page in the Manta's 16 greys; the starter's ten toolbar brushes, drawn with Brush
Studio's engine (the lab's, as `engine-files.cjs` lists it); mouse, finger, or Apple Pencil with pressure. The
browser sends the pixels its strokes changed, so the Manta shows exactly what the browser drew.

## The relay

A Python stdlib service beside the community server on ell.ing, behind nginx: `POST` to send, a server-sent-events
`GET` to listen (no WebSocket library needed on Android or in the browser). It numbers messages and passes them on;
it can't read them. Rooms in memory only; no accounts, no drawings stored, no requests logged; a code is 4 letters
from an unambiguous alphabet. Messages capped in size and rate per client.

**Secret key in the link** (as Excalidraw does): the part after `#` never leaves the browser, and changes and pages
are encrypted with it (AES-GCM: `LiveCrypt`, pure Java on both sides, the browser's through the wasm engine; WebCrypto
is missing on a plain-http LAN page; manta-host spec, "Encryption"). A Supernote that joins by code
gets the key with the code: the code shown is the room and the key together, short enough to type (the key is then
weak against a determined guesser; accepted for a drawing).

## Privacy and the app's promise

The app goes online only while live, after the one-time OK, and only to ell.ing. README's "never goes online"
becomes "only for live pages and brushes, only if you allow it" (with the Community tab's wording). Needs the
INTERNET permission.

## What we looked at

- **Drawpile** (GPL-3; Android, desktop, browser): shared painting, thin server, sends brush output not pen input,
  rolls back to put changes in the server's order. Ideas taken, no code (it's C++/Qt/Rust). Tried on the Manta
  2026-10-31: unusably slow, as an app without an e-ink pen path would be.
- **WBO** (AGPL): line whiteboard, no brushes or pressure. **excalidraw-room** (MIT): a relay for shapes; ours is as
  small; its key-in-the-link idea taken. **tldraw**: needs a paid licence key in production. No shared drawing app for
  Supernotes found.
- The app stays MIT: nothing copied. Elling is fine with GPL if code worth taking turns up; it would also keep the
  engine from going back into MIT MonoPaint.

## Build order

Each step is tried by hand on the Manta before the next (one e-ink change at a time).

1. **USB probe (no internet).** A lab build with INTERNET permission; the relay running on the Mac; `adb reverse`
   so the Manta reaches it over USB; a plain browser page on the Mac that sends changed pixels. Question it answers:
   can the Manta show others' changes while you're drawing, without upsetting the pen's fast layer, and how fast?
2. **Both ways**: the Manta's changes reach the browser; the browser draws with Studio's engine and sends its pixels.
3. **Order**: put right after a change arrives out of order.
4. **Undo** of your own changes.
5. **Go Live, Join, the OK**, the code and key; a second app in Android's emulator as the second Supernote (order
   and pixels only; no e-ink there).
6. **The relay on ell.ing** (with Elling's OK before it goes up).
7. **Two real Supernotes**, when a friend's is at hand. Until then, untried.

## Checks

The change format (encode, decode, order, Undo's check, encryption) is pure Java and checked on the desktop like
`StrokeLogChecks`; the browser's side by a Node check against the same bytes; the relay has a Python test like
`test_server.py`.

## Probe, 2026-10-31 (build step 1, branch `live-probe`)

The Manta over USB (`adb reverse`), the relay on the Mac, a script sending a doodle every 4 s while Elling drew:
46 changes, all laid, in order, none lost.

- **Size**: about 1 KB a change (average 985 bytes, at most 1.6 KB; 160 to 280 px square, deflated).
- **Laying one**: 5 to 70 ms on the Manta, mostly about 30 ms, with `layerChange` + `PictureImport.place` and the
  ordinary partial update.
- **Waiting for the pen**: most came between strokes and showed at once; 7 came mid-stroke and waited until the pen
  lifted (0.1 to 3.7 s).
- **Elling**: "no slower, seemed fine, they looked clean"; the pen unchanged. A two-finger tap undid a doodle (one
  Undo step each). (A spiral looked cut off: the script drew past its own rectangle, not the app.)
- So waiting for the pen is enough for now; laying changes mid-stroke isn't needed. Next: build step 2, both ways.

## Both ways, 2026-10-31 (step 2a, branch `live-probe`)

The Manta over USB, the relay on the Mac open to the Wi-Fi, Elling drawing on the Manta and on a phone at once.
"once it started working it's working great": Manta strokes 0.1 to 7 KB, the whole page first 66 KB; phone strokes
laid on the Manta in 4 to 160 ms (the 160 a stroke 994×1841). The first strokes took a few seconds to show: the whole
page is read on the main thread at the first change. Next: send the page as soon as the session connects and when a
page is shown; and Elling's idea: a mark from someone else draws itself on along its stroke instead of appearing
at once ("for effect"), then real brushes in the browser (2b).

## Drawn on, 2026-10-31 (step 2c, branch `live-probe`)

Same setup as 2a. Elling: "works quite well". A phone mark drew itself onto the Manta in 6 slices, shown over 410 to
1090 ms, after waiting 28 to 330 ms for the pen; the page went to the browser at connect (4 KB). Manta strokes 0.1 to
13 KB. Undo takes back any mark, the other person's or your own, one step each: expected until build step 4 (Undo of
your own changes only).

## Sending faster, 2026-10-31 (branch `live-fast`)

The Manta sent a mirrored stroke 4 to 6.6 s after the pen lifted (1785×1885, packed in 550 ms), the app laggy meanwhile:
the main thread read the page a pixel at a time over the box round every area rendered since the last change. Now
each area rendered is kept apart (overlapping ones joined), the change is still one rectangle (the box round them)
but clear between them, its tones read a block at a time (`ToneDocument.compositeTones`), and it is packed at
deflate's fastest. Same bytes' form, so the browser and older messages are untouched. Desktop, that stroke's box on a
sketched 1920×2560 page with four 600×700 reflections: reading 128 → 7 ms, packing 73 → 5 ms, 338 → 155 KB. The log
now says how long making a change took on the main thread and how long after the pen lifted it began. Untried on the
Manta.

## Measured without hands, 2026-10-31 (branch `live-auto`)

`node scripts/live-probe/autotest.cjs [out]` (the Manta over USB, live-probe.txt as for the probe): restarts the relay
and the app, draws on the Manta with `adb shell input stylus` (pressure fixed, no tilt; since: a real pen, below) and in probe.html in headless
Chrome, prints each stroke's timings and compares the Manta's page (sent whole on reconnect) with the browser's
canvas, PNGs in `out`. `--compare 8,5` draws toolbar slots alike on both sides. On a clear page, pencil:

| | before (e9196c85) | after |
|---|---|---|
| plain stroke, lift → sent | 18–28 ms | 21–26 ms |
| mirrored (kaleidoscope, 6 copies), lift → sent | 138–428 ms, median 371 | 121–290, median 211 |
| mirrored, main thread a change | 15–121 ms | 5–33 ms |
| mirrored, bytes | 65–296 KB | 21–30 KB |
| mirrored, lift → shown in the browser | 522–1012 ms | 470–704 ms |
| browser stroke → laid on the Manta (drawing on included) | 413–570 ms | 432–560 alone, 193–218 in a row |
| Watercolour washy mirrored, lift → sent | about 1060 ms (main 131, 418 KB) | 897–1082 (main 46, 120 KB) |

- A change is now the blocks the page was written in since the last (`ToneDocument.takeLive`), not every area
  rendered: a kaleidoscope's guides and its copies rendered the box round all of it.
- Watercolour's 0.8–1 s after lift is the app drawing wet paint's mirror copies again, the same without live.
- Strokes in a row: each goes out on its own; a receiver lays the rest of a change at once when the next waits,
  instead of drawing each on in turn (Elling: "only the first one shows").
- Drawing on: 200 to 600 ms (was 300 to 1000).
- The browser sent every stroke's whole box as set (an Undo's): the page's whites and tones shown again in 16 greys
  came out lighter, so the box overwrote what the Manta had there. Now only what the stroke changed.
- Laying another's change dried the Manta's wet paint and settled a wash without sending it: pages drifted (71,587
  pixels after one Ink wash). Sent first now.
- Smearing: without overlaps both pages are the same to the pixel. A browser mark over the Manta's marks on another
  layer came out darker on the Manta (laid again over them); now scaled to the layer drawn on (mean 13–21 greys off
  → 6–8). A browser stroke that lightens (Watercolour washy's lift) can't lighten the Manta's other layers.
- Brushes that look other in the browser (Elling: Watercolour washy "odd", Ben-Day "thinner with a line in the
  middle"), drawn alike: the web engine has no Wet paint (Watercolour washy draws Instant, never blooms or dries:
  lighter, flat) and lacks the app's even fill for screened brushes (e55d2ef9, 2026-10-21: `ResolvedBrush.screenTone`,
  a crisp body, `PrintScreen.printed`'s darker-of-two), so its Ben-Day screens the soft falloff: a dark core, a halo.
  Both are the engine port's (newmods.js's screen compose), not the probe's.
- Magnets now travel: stamped into the change (`Magnets.stamp`) where they lie.

## A pen with pressure; no holes, 2026-10-31 (branch `live-holes`)

- **The pen**: `scripts/live-probe/PenStroke.java` injects a stylus stroke with pressure and tilt at real timing, as
  the shell user (`input stylus` has none; /dev/input is closed to adb). `pen.sh` builds it and pushes
  /data/local/tmp/pen.dex; the autotest uses it, strokes pressing in, swelling and tapering, light or heavy
  (`--keep-relay` leaves a running relay alone). `DrawingPad.isPen` is as on inkwash-v2-lab again: these events are
  the stylus's.
- **"Holes" in a Watercolour washy stroke** were a settling's change drawn alone: it carries only the blocks the
  settling changed, clear (left as it is) elsewhere, and some blocks inside the wash had already come to rest at the
  lift. Laid in turn, the page before plus the stroke's change plus the settling's equals the Manta's page sent whole
  after, to the pixel (that stroke, and Watercolour washy, Ink wash and Another pencil strokes with pressure). Checked
  on the desktop too (`LiveChangeChecks.washSettles`).
## Later: others' pens (asked 2026-10-31, after speed and quality)

Elling asked whether live cursors (as Figma, Miro, Excalidraw, tldraw show; Drawpile shows brush outlines) would help;
"let's focus on speed and quality first and we can explore this later". Ideas, untried:
1. The browser shows the Manta's pen, hovering (the Wacom pen reports hover) and drawing: frequent, throwaway position
   messages apart from changes, never stored, fading when still.
2. The Manta shows a calm marker, not a live cursor: a small dot where another pen is, about twice a second, only while
   it hovers or draws, hidden while the Manta's own pen is down. Elling judges by eye whether it is calm enough.
3. Maybe: a laser pointer, a trail that fades by itself.
