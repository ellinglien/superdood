# Brush Studio: notes

Page: `studio.html` (one file, about 4 MB with the app's engine; the only thing it loads is the Google Fonts stylesheet for the fallback
Roboto Mono, and at superdood.org/studio/ the Community library's `/studio/api/`, same origin). Built by
`node docs/brush-lab/studio-src/build.cjs`, which also builds the library's admin page (`community-admin.html`).

## How it's built
- **The engine is the lab's**, read from `prototypes/modular-lab-src/` at build time and never copied: `port.js`,
  `oldmodel.js`, `engine.js`, `stroke.js`, `float.js`, `parts.js`, `checks.js` in the lab's order, then every other
  engine file there, sorted (the list in `engine-files.cjs`, which Brush Scan's build uses too; today `newmods.js`: Blend, Stipple, Hatch, Resist, Halo, shown as Mold; `voice.js`; `wet.js`, Wet paint), then `planes.js` last (the page's layers and kind planes, as the app's). `ui.js` is the lab's page and is
  left out. A new choice or dial in the lab's tables shows up in Studio's cards, sweeps and grids when Studio is
  rebuilt; nothing in Studio lists the choices.
- **The app's own engine draws** (step 10, 2026-10-31): `web/engine/` (the Java compiled to WebAssembly GC by TeaVM),
  built by `build.cjs` (it runs `web/engine/build.sh`; Gradle skips it when no Java changed) and put in the page
  gzipped (`#wasm`, `#wasmassets`: the app's paper scans), with TeaVM's runtime (`#teavm`) and its API (`#mbdengine`,
  `web/engine/engine.js`). Not committed: a built `.wasm` would go stale at the next engine change, and the page stays
  one file as it is published. Where the browser has WebAssembly GC (Chrome 119+, Firefox 120+, Safari 18.2+) every
  picture (`render.js`: `studioLay`, a page of the app's, its bands laid under the first stroke on that brush's own
  layer and kind, each stroke by its code, ticked to rest) and the test page (`TP.page`; `TP.surf` the greys it shows)
  draw with it, in the workers too (each loads it). Elsewhere, with `?js`, or for a brush the app doesn't read, the
  lab's JS copy draws, as before, with a console note. Wet paint on the test page settles ten ticks a second, as on
  the Manta. The Guide's tiles (Brush Lab Dials' shared code) and the admin page still draw with the JS copy. On the
  app's engine a Float drop's held pen is its sample again every 50 ms; a resting pen's Stipple Build-up isn't ticked
  on the test page.
- The paper scans come from the lab's page (`TEXTURE_SRC` in `modular-lab.html`); iA Writer Duo 400 and 700 from
  `app/src/main/res/font/`, embedded as data URIs, with the OFL notice in the footer.
- `studio-src/`: `page.html` and `studio.css` (the Scribbler Style), `render.js` (one picture from a job; no DOM),
  `studio.js` (the page), `qr.js` (a small QR encoder after Nayuki's, byte mode, level M, versions 1–10).
- **Pictures** run in up to four Web Workers made from blob URLs of the engine and `render.js`, or on the page when
  workers can't start (`?inpage` forces it). A job is plain data; the answer is the page at the Manta's 16 greys. A
  newer request for the same place on the page replaces one still waiting; answers are cached by brush key and size.
  The test page and the live stroke always run on the page.
- Every picture is drawn at the Manta's scale (1.875 device pixels a CSS pixel, as the lab's test area).
- **Loop and wave** (`docs/DESIGN.md`): anything that is a whole brush is the loop (the name's chip, sweep steps, the
  Mix grid and Roll, history rows, the library, shared brushes); part cards' choices are waves.

## Screens
- **The brush bar** (under the tabs, on every tab): the brush's loop and name (click to rename), "Edited" and a
  suggested name under it, the "This brush" sentence, the app's row **New**, **Open**, **Save**, **Save as New**, then
  Undo, Revert, Share; Style and Randomize on Adjust. Why there: Save, Revert and the rest act on the brush,
  so they sit beside its name and loop rather than in the page header's far corner, where a wide window leaves them a
  long way from what they save; top left is where reading starts and the first thing under the tabs. The bar stays in
  view when a narrow window scrolls. The header keeps only the Studio's name and the tabs.
  - Share opens a small panel under it: the brush code with Copy code, its QR code (for a phone: the
    tablets have no camera), and Share to Community (see "Community library" below). Open (Load until 2026-10-30, the
    app's word) opens the library, with Paste code at its top (any code the lab reads: M, B, the app's I and P). A newer version's
    code (docs/BRUSH-CODE.md) reads what Studio knows and says "Newer version, some parts differ"; a shape it doesn't
    know, "Needs a newer version", as the app's Open sheet. Names keep digits after the first letter (`polite-smear-2`).
  - **Naming and saving, as the app's Brush Lab** (the owner, 2026-10-30: "i like how it is in the brush lab in the
    app... save as, new ... conditional save, rename alert, etc..."; `BrushLab`, `BrushLibrary`, `BrushNames`):
    - A brush not opened from the library (the one Studio opened with, New's, a pasted code or link, a tried roll,
      example or shared brush, a Basic tool) shows only **Save as New**, solid; one opened from the library (`ST.from`,
      that entry's name: names are unique) shows **Save** (writes over it) and **Save as New** (a copy), plain.
    - **Save** writes over the entry in its place, keeping the entry's name unless one was given (typed or the
      suggestion taken; `ST.named`); "Already saved." when unchanged. **Save as New** adds a copy, newest, under the name
      given, else the suggestion, else its own; the same name and settings already there are only moved first ("Already
      saved."). Either way the brush is then that entry's (Revert's base, not Edited, no name given).
    - **One brush a name**: names match with case and spaces round them aside; a taken name gets the first free number
      ("Polite Smear 2", a number it ends in replaced, 32 characters at most; `freeName`, the app's
      `BrushLibrary.freeName`). A library remembered from before with names alike keeps the newest of each, once.
    - **Edited** under the name once the brush differs from how it was opened or saved (`canRevert`), and
      **Suggested: Polite Smear** (`suggestion`: `appName(B)`, the app's `BrushNames.name` through its engine
      (`WebEngine.brushName`, since 2026-11-02: paint colours, 24 deadpan forms, a part and a name), passing by the
      library's names and repeats of its latest ten; where the engine isn't loaded, `brushNameOf(B, taken)`, the lab's
      JS copy in the older eight forms; none when a name was given or it is the name already). Randomize names its
      brush the same way (`rollBrush`). A
      click takes it (a name given). The kin names (`kinOf`) are the app's Tweak It variations' and Studio no longer
      uses them: the app's Save as New takes the suggestion.
    - **Rename**: a click on the name opens a field on the suggestion (else the name), all selected, and **Done**;
      Enter, Done or clicking away takes it, Escape keeps the name. An empty or unchanged name changes nothing.
    - **New**: a fresh brush in this one's style, keeping the locks (Randomize's roll), not from the library.
    - Where the web differs: no Back to (Revert is Studio's), Save's moment of "Saved"/"Already saved" is the status
      line, and Undo moves the brush back but not which library entry Save writes over or whether a name was given.
  - A link opens a brush: `studio.html#<share code>` loads it as This brush over what the page remembered, and a new
    hash later does too (codes are letters, digits, `.`, `_` and `-`, which the artifact host passes through). No Copy
    link: inside the artifact host the page runs in a frame that can't see its own claude.ai address, so the link is
    the page's link with `#` and the code (Copy code) added by hand.
  - Export (in Share, under Download for tablet) downloads `Scribulor.brushset`, a Procreate set of This brush and the
    library's Saved brushes, made in the page by `export.js` (the brush export spec,
    `docs/superpowers/specs/2026-10-20-brush-export-design.md`).
  - Save (when shown) is solid while the brush has changes the library doesn't hold and reads "Saved" with a check,
    outlined, once it does (as the app's Brush Lab), so its state shows at a glance.
  - Revert returns the brush to its last saved version, or to how it was when loaded or opened (the library, a code,
    a tried roll or shared brush, or the brush the Studio opened with); off when there's nothing to revert. It is a
    history step, so Undo takes it back.
  - Randomize rolls the whole brush again in its style (`rollStyle`), keeping every locked part and dial (Erasable's
    lock across media too); Style lists every style, Anything goes among them, and picking one rolls a brush in it.
    Each roll is one history step; Undo goes back one step (to the step's parent, as clicking it there). A part's die
    rolls that part in the style (its choice from the style's weights, so a style with one choice for a part keeps
    it; then its dials), keeping its locked dials; a dial's die rolls that dial only. The app's model's cards (After, a newer tip or print) lock and roll the
    same, through the app's model (`appPartAs`; a part's die picks any of its choices); after a roll of the lab's,
    `appRolled` puts back a locked one as it was and keeps After on its choice (its dials rolled); Random's Anything goes
    picks among the app's choices too (After Off half the time).
- **Tabs**, in order: Community library (brushes people shared, newest first, each with its loop, name, "by …",
  its note and Try, which makes it This brush and opens Adjust, and Report; away from the website, eight example rolls
  labelled Examples), Random (12 random brushes made from this one and its locks; not
  people's brushes; Randomize 12), Guide (below), Lab (every part live: the online lab; the tab on first open), Close-up (no Style
  or Randomize there) and Mix (two parts crossed, the rest held still). A saved "understand" or "share" tab opens Lab,
  a saved "explore" Mix.
- **The Lab's layout** (`buildCards`): the cards (in the word list's order, Erasable after Medium) are measured off
  screen and cut into the fewest columns whose tallest fits the window's height, so they stack to fill it, as wide as
  leaves the test page column between 380 px and 1.1 × the height; in order where it can be, else the tallest card
  first into the shortest column. Labels show under the swatches when the window is 1440 px or wider and they still
  fit; otherwise the header names the swatch under the pointer. Each card says in one grey line what its part does.
- **Favourites**: `docs/brush-lab/favourites.txt` (the owner's picks, one code a line) goes into the page at build
  time, and each code is added to the library once, the first time the Studio opens after it was added (deleting one
  keeps it gone). zealous-shallot uses Stipple, so the app can't read it yet.
- **Sizes**: every tab fits the window with no page scroll on a laptop (1280 × 800, 1440 × 900) and a wide screen
  (1560 × 970, 1920 × 1080, 2240 × 1060), checked by `shots.cjs`, also with the brushes that have the most dials. The
  page uses the whole width; the test page, sweep and before/after are a column the window's height, the test page
  taking what the others leave. Text is 13 px, labels
  and values 12 px, never smaller; a dial is one row (name, value, lock, die, the slider along its foot). Choice
  swatches have no captions: the card's header names the swatch under the pointer (in grey), and each has its name
  as its label and tooltip. Narrower than 1180 px (or shorter than 620 px) the columns stack and the page scrolls.
1. **Lab**: every part (Tip, Medium, Paper, Drying, Control, Smudge) and Erasable at once, each with its choices
   as waves and every dial as a slider, with a lock and a die. Dials at their default are grey. Beside them: the test
   page (mouse, pen with pressure and tilt, touch; Eraser, Clear; three sizes from the style's own and three greys),
   the sweep and before/after. Every stroke on the test page is drawn again with the brush whenever it changes (live
   while dragging if a redraw took under 140 ms). A pen's pressure goes through the Manta's curve and a mouse or finger
   gets one from its pace (engine.js `pointerSample`); Wet paint stays wet across strokes and moves after each lift, as
   on the Manta (`tickWet`; a redraw settles it). Dragging a dial or clicking its name focuses it: the sweep shows it
   in seven steps (or each option), the rest held still; a step is taken by clicking it. Before/after is the last
   change, or, for a dial just focused, that dial at its default (or its far end) against now, with the changed
   pixels in ink and the share of the marked area changed.
2. **Close-up** (Fine-tune until 2026-10-30, the owner: "it's not really a way to fine tune, is it? it's more of a closeup view"; its magnified view is headed Magnified): the test stroke with its pressure, tilt and speed as curves (dragging pulls the curve toward the pen
   around that point; the stroke is drawn again as it moves); "Use my last stroke" takes the test page's last stroke,
   its shape and its curves. The close-up shows the stroke's own pixels at 1, 4 or 16 screen pixels a Manta pixel
   (a brush the app keeps sharp when zoomed, sharp-brushes 2026-11-04: at 4× and 16× its part drawn again 4 and 8
   points a pixel by the app's engine, `closeSharp`, the pixels until it is ready; its words end "Sharp at any zoom.")
   around the dashed cursor. History: every change a node; a change made after jumping back starts a branch (shown
   indented); Compare two side by side with the settings that differ; Save as New (the brush bar's).
3. **Mix** and **Roll**: Cross two parts (any two, every choice of each, the rest held still); Anything goes: 12 rolls over
   every choice and the whole range of every dial (about four dials in ten left at their default), past the styles'
   bands, keeping the locked parts (the same locks as the cards).
4. **Community library** and the **Share** panel: as under Tabs and the brush bar above.
5. **Guide** (2026-10-30, the owner: "the visual guide we created as a tab in the brush studio, but limited to what's in
   the app currently"; top tab after Random): Brush Lab Dials' tiles (`dial-demo-src`), one live tile a choice (its
   Basic slider swept) and a dial (low to high), sections Tip, Medium, Paper, Drying, Control, Smudge (the app's
   `BrushPart.SHOWN` and labels), choices then dials. `guide.js` (on the end of `studio.js`, as `community.js`) and
   `guide.css`; the shared code is Brush Lab Dials' `tiles.js`, `render.js`, `view.js` (`dialGuide`: the cards, the
   lazy drawing, the clock) and `view.css` (classes `dg-`), read at build time into `<script id="guide">`, never
   copied. Built the first time the tab opens; its own few workers (engine, `render.js` and the guide script) draw only
   tiles on screen or 600 px from it, inside the tab's own scroll. Clicking a tile's picture puts that example's part
   (its choice and the step shown) into This brush, focused on the dial, and opens Adjust: one history step.
   - **Limited to the app**: `docs/brush-lab/app-features.json` (parts, choices and dials with the app's labels) is
     written from the Java enums by `app/src/test/.../StudioGuideChecks.java` (`studio-src/app-features.sh`): choices
     `ModularFeatures.offered` (so Hand Hatch, cut, is out), dials `ModularFeatures.shown` on each choice, Control's
     without Tilt Range (Tilt Size's fourth step) and the swatch sets. `StudioGuideChecks` is a desktop check
     (`scripts/test_inkwash.sh`): it fails when the file and the enums differ. `demoTiles(APP_FEATURES)` keeps only
     those keys and names them with the app's labels. Today that leaves out Takes Ink and Plume (the app hides them)
     and Tilt Range: 169 tiles of the lab's 172. Dials the app has but the web engine doesn't draw (the gravity ones,
     Dust When Tilted, Page Tilt, Bristle's Paint Load) have no tile, as in Brush Lab Dials.
   - **ⓘ** (2026-11-03, the owner: "i info buttons for the different sections and sliders that links to the page on the
     visual demo"): a small quiet button ("About Tip", "About Size") by each card's name and each dial's name opens the
     Guide at the dial's tile (its stable id `g-<part>.<dial>`, `g-<part>-<choice>` for a choice, from `demoTiles`'
     `anchor`), else at the part's section (`g-<part>`; Texture is Medium's), scrolled to, focused and outlined for a
     moment; After has no section, so no ⓘ. **Back to Size** in the Guide's bar returns to Adjust on that ⓘ; it shows only
     after an ⓘ (any other tab clears it).
   - Brush Lab Dials (`dial-demo.html`) is kept, every choice and dial of the lab, as before.
- Load and Save: a library in this browser (`localStorage`, key `inkwash.studio.v1`, wrapped in try/catch), with the
  basic tools (the app's four, Sumi and Clear water, Sketch pencil, Steady line, Soft wash). The current brush, the
  history (240 nodes), the locks, the test stroke and the tab are remembered too.

## Checks (`studio-src/`)
- `check.cjs`: the click-through in headless Chrome (puppeteer-core from `~/node_modules`), 133 checks (a link's code among them): the tabs, the Share panel (its Export too) and the Community library (from a file: examples and the website's link, Share off; then the page served at a fake `http://studio.test/brush-studio/` with its `/brush-studio/api/` answered by the check: listing, bad rows left out, names escaped, anonymous, the note, Show More, Report, Try, Share's form (the name first, selected) with its small print, Cancel, a refused share, what is sent, a taken name coming back with " 2", Shared, a share that waits for review; nothing else loaded), the cards' lines and packing, the favourite, the brush bar (only Save as New for a brush not from the library, Save and Save as New for one opened from it, Save's state, Edited and the suggestion, the rename field, a taken name getting " 2", Save over in place, New, Revert, Undo), Randomize, Style and the dice with locks, workers start
- `check.cjs`: the click-through in headless Chrome (puppeteer-core from `~/node_modules`), 127 checks (a link's code among them): the tabs, the Share panel (its Export too) and the Community library (from a file: examples and the website's link, Share off; then the page served at a fake `http://studio.test/brush-studio/` with its `/brush-studio/api/` answered by the check: listing, bad rows left out, names escaped, anonymous, the note, Show More, Report, Try, Share's form with its small print, Cancel, a refused share, what is sent, Shared, a share that waits for review; nothing else loaded), the cards' lines and packing, the favourite, the brush bar (Save's state, Revert, Undo), Randomize, Style and the dice with locks, workers start
  and draw, a dial changed moves the sweep and before/after, the test page, Close-up's curves, zoom, last stroke,
  history branch and compare, Mix, Roll, the Guide (its six sections and links, only the tiles near the screen drawn,
  a tile drawn in each section, every tile a choice or dial of `app-features.json` under its label, no Takes Ink, Plume,
  Tilt Range or Hand Hatch, Press to Push and Press to Lift there, a tile tried), codes, QR, load, the library, remembering, drawing without workers, no sideways
  scroll at 1600, 1280, 1024, 768 and 390 px, no console errors, nothing from the network but the fonts (and, on the
  website, the page's own `/brush-studio/api/`). The test page's "drawing on the test page" fails now and then
  (timing); a second run passes.
- `export-check.cjs`: Export's Procreate set made in Node from 14 example brushes and read back (zip, PNG, binary plist,
  `unzip -t`, Python's `plistlib`): see the brush export spec's "Built".
- `java-codes.sh`: 604 Studio codes (every style rolled, and rolls over whole ranges) read and written again by the
  app's `ModularCode`. None wrong. Codes using the lab's newer Erasable and Blend dials come back without them, and
  Stipple, Hatch, Resist and Halo don't read, until the app has them. Since the app's Halo (2026-10-18): 604 of 604;
  since the catch-up (2026-10-27, Print, Wet and the rest): 623 of 623.
- `presets-check.cjs`: every Basic tool's code (`ToolPresets.ROWS`) read and written again by the web engine. Before
  the catch-up 77 of 118 read; now all 118, 115 byte for byte and the three Hand Hatch ones written as Hatch, as the app
  itself writes them (with the app's own writing of each, from `StudioCodeEcho`, as its argument: 118 of 118).
- `shots.cjs`: screenshots of every tab at 1440 × 900, 1280 × 800, 1560 × 970, 1920 × 1080, 2240 × 1060, 900 and 390 px wide, saying
  whether each laptop-sized tab fits without scrolling.

## Community library (as built, 2026-10-30)
The library lives on superdood.org (ell.ing until 2026-11-05), not in the artifact's db (that version, one db document a person, was replaced on
2026-10-30): superdood.org's server code (not in this repo) (its README: the service, moderating, the word filter, where the data is,
backup). The owner's rules: no accounts, emails or stored addresses; the sharer's name is optional ("anonymous"); a
share whose words pass the filter appears at once, one with flagged words waits for the owner, one with a slur is
refused; three reports hide a brush until the owner looks. The app stays offline; only the web Studio talks to it.
- **The page** (`studio-src/community.js`, built onto the end of `studio.js`, so the smudge work in `studio.js` and
  this stay apart): the API is `/brush-studio/api/` only when the page is served from `/brush-studio/` over http(s);
  anywhere else (the file, the artifact, a saved copy) the tab shows the eight examples with "Shared brushes need the
  website: superdood.org/studio" (a link) and Share to Community is off ("Sharing needs the website."). On the website
  one `GET brushes?limit=48` when the page is ready (again after a share that appears); Show More asks for older ones
  (`before=` the last one's time). "Library unavailable." when it doesn't answer.
- **Untrusted rows**: each must have a string id and a code that `readAnyCode` reads (240 characters at most); name
  and maker cut to 40, note to 200; anything else is left out. Everything is escaped (`escH`) or set as text. A card:
  the loop, the name, "by" the maker or "anonymous", the note (else the brush's caption), Try and Report (Reported
  after, for the visit).
- **Share to Community** (Share panel): opens a form under it: Name first (the brush's name, all selected to type
  over, as the rename field; 2026-10-30), Shared By (placeholder "anonymous"), Note (optional),
  a hidden honeypot field, the line "Shared brushes appear right away and are free for anyone to use. Names are
  checked." and Share / Cancel. Sends `{code, name, maker, note, website}` (an empty Name sends the brush's). The
  server numbers a name a shared brush has, published or waiting, case aside ("Polite Smear 2", the app's library rule)
  and answers with it: "Shared as Polite Smear 2." (and the library reloads) or "Sent as Polite Smear 2. Waiting for
  review."; the brush here keeps its own name. The button reads Shared for that code. A refusal shows the server's words under the form
  ("Please choose another name.", "Already in the library.", "Too many at once. Try again later.").
- **Admin page** (the admin page's source, not in this repo, built with the engine, textures and Studio's CSS into
  `community-admin.html`; at superdood.org/studio/admin/): the token once a tab (sessionStorage), then Waiting (with
  the matched words) and Published, each drawn by the engine's loop, with Approve / Reject / Delete and Open (in
  Studio by its link).

## MonoPaint users, later
- The share code is the whole brush in about 60 to 110 characters of plain text (`name.M…`), so it travels anywhere text
  does: a forum post, a message, a note on the tablet. The app's Tools page already loads codes.
- The Supernotes have no camera: the QR code is for a phone (scan, then paste the text on the tablet, or send it to
  the tablet). A later app version could take a code from a link or a shared text file.
- A public Studio link (or a second, read-only artifact listing the shared rows) would let MonoPaint users without
  Scribulor browse and try brushes in the browser; the app's codes would need the M format in MonoPaint itself to load
  there, which is a MonoPaint change, not Studio's.

## Known gaps
- Wet (Drying's eighth choice) draws as Instant, as the app does where it has no wet canvas: no mixing into wet paint
  under it, evening out or staying wet across strokes. Its Stays Wet is kept in the code.
- Gravity (Quick's and Slow's Runs, Drips, Pools, Grainy, Rock to Blend; Charcoal's Dust; the hand's page tilt) needs a
  tilted page: hidden, kept in codes, not drawn.
- Light ink over dark (the app's `ResolvedBrush.lightOver`: a pen or marker lighter than mid grey covering darker
  marks) isn't ported: a light grey only darkens here.
- Hand Hatch (cut from the app) reads as Hatch, as the app reads it.
- The engine draws on one tone page, as the lab's (not the app's ink and wash layers).
- Before/after and the swatches use a sample's size (sized to the tile); the test page, the stroke and the close-up
  use the real size.
- The test page's replay draws every stroke with the current brush, at the size and grey it was drawn with; Slow
  drying, Float and Halo are finished at once on a redraw (they animate only for a stroke just drawn).
- Halo (shown as Mold) needs other marks near it to bridge; alone on a tile it shows little.
- This brush is a persistent left sidebar (loop, name, words, Undo/Revert/Save/Load/Share, then Adjust / Close-up / Mix); top tabs are only Community library, Random and Guide (none of the three filled there); Style and Randomize sit at the top of Adjust only, Random has its own "Generate other random brushes" at the foot of its grid.
- Share has Download for tablet: saves `<brush name>.inkwash.txt` (the brush code) via the claude.ai `downloads` capability (`window.claude.use("downloads")`, as Brush Scan), else an `<a download>` link. The published page needs `capabilities: {downloads: true}`. The tablet (`BrushImport.runAll`) reads such files from Document/Scribulor at launch and on the drawing screen's resume.
- New names say what stands out (the lab's `describedName`, from `names/traits.txt`): a style's roll, the Random tab's
  rolls (`wildRoll`, named last) and Save as new (`kinOf`, keeping the trait's word under a new produce).
