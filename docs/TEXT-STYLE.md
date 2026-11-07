# Text style

Superdood's text should read like a good art-supply label, not like a chatbot. Follow these rules for any words the
app shows: labels, buttons, captions, "This brush", notes, dialogs and screen-reader descriptions. The web lab
(`docs/brush-lab/prototypes/modular-lab-src/parts.js`) says the same as the app (`ModularWords`), with the same labels;
its part descriptions (`ROLE`, `ERASE_ROLE`) are Brush Studio's too, and its choice blurbs keep these rules (no "you").

## Rules

1. **Name it, don't explain it.** Labels are nouns; card words and captions are short fragments with no "It …". A
   brush's words ("This brush", screen-reader text) are the exception: complete, human sentences (see **Brush words**).
2. **Painter's words.** Round, flat, chisel, hake, nib, rake, bristle; wash, line and wash, glaze, gouache, sumi; cold
   press, canvas; bloom, backrun, bleed, granulates, dry brush, lift, wet edge. A painter's word only where a newcomer
   would guess it from the sample; otherwise name the effect, in up to three words ("Thick Paint", "Texture", "Runs
   Downhill"). A dial that acts only on a tilted page says so: downhill or when tilted (Runs Downhill, Pools Downhill,
   Drift Downhill, Dust When Tilted, Rock to Blend). Never show engine words (body, window, settle behind the nib,
   carry, life, hand, prints, chaos, reach, spread, hold, settle, cell, gain, jitter, misregister, screen).
3. **One term per concept.** Use the word list below everywhere: modules, sentences, captions, screen-reader text. A
   dial's label and its "This brush" word are the same word: Spatter / spatters, Bleed / bleeds, Double Print /
   doubled, Patchy Ink / patchy ink, Line Shift / line shifted.
4. **No symbols as grammar.** No → ↔ = or ; in text a person reads. Allowed: units (4 s, 6 mm, 2B, ×1.2, 40°),
   "·" between a name and its style in a title, and ▾ on a menu button.
5. **No "you" or "your", and no narrating the pen** ("as you press", "after you lift", "the moment the nib passes"),
   except in a brush's words, which talk to the person drawing ("Lift quickly and it flicks."), and the Self-Portrait Prompt's
   cards until its cut, 2026-11-05 ("Draw yourself riding a whale.", the owner, 2026-10-27).
6. **Say only what's unusual.** Leave out the defaults (a round tip, smooth paper, Quick drying, a steady Control)
   and anything already on screen (the style's name, the part's own name).
7. **Status messages are the result in about three words, or nothing.** No hints like "Undo takes it back".
8. **Title Case for labels, sentence case for the rest.** Every dial label, choice label, option and Control preset is
   Title Case in the source (the owner's, 2026-10-19): "Print Shading", "Pick Up Paint", "Dust When Tilted", "Split
   Nib", "Halftone"; a, and, of, on, to and the like stay lower case ("Rock to Blend"), When and While don't ("Flow
   While Wet").
   Sentences, card words, captions, notes, end words and module descriptions are sentence case, in the label's words
   ("Thick paint", "Split nib" as a variation's swap, "patchy ink"). Caps come from the LABEL type style, not from
   typing caps (the owner's "THIS BRUSH" header is the exception); a row shown in caps keeps its Title Case source.
9. **Lengths.** An m or a w (either case) counts one and a half: the face is duospace (`docs/DESIGN.md`).
   - A button: two words at most.
   - A bar: three words at most.
   - A card line: 17 characters (14 on the Nomad's preset cards), two tags at most.
   - A dial label: two lines of 12 characters on the Manta, 10 on the Nomad; one line on Ink's open card, whose
     seventeen rows hold one line each (hence Print Shading's row there says Shading).
   - A word in a slider's label: at most 8 characters, for the Manta's variation card ("more texture").
   - A Tip choice: 9 characters; a Surface: 10.
   - A Nomad swap tile: one word of about 9 characters, about 7 for Control (hence Screentone's "Screen" / "Tone").
   - "This brush" (`BrushVoice.about`): at most 260 characters, the first two sentences under 150; a
     written opener at most 70 characters (a special tool's own words one or two sentences); how it behaves at most
     four sentences and 230 characters (2 or 3 typical).
   - Notes: about 30 characters.
10. **Two traits at most.** No lists of three, no "and … and".
11. **Canadian spelling in what the app shows** (colour, centre, grey, Watercolour). Codes and keys keep theirs.
12. **Never show code keys, internal state or unfinished features** (a choice's key, "tentative", plumes).

**Keys never change.** `PartChoice.key`, `ModularStyle.name`, the dials' keys and everything in a share code are the
codes' and never change. A new wording is a display label: `PartChoice.label`, `PartDial.label`,
`ModularStyle.label`.

## Word list

**App objects**
- **brush**: any drawing tool. The eraser is just Eraser.
- **slot**: one of the eight places on the bar (six until 2026-10-19). Slots have no numbers on screen (the owner,
  2026-10-19): a slot is shown and named by its brush (its stroke or loop, and the brush's name: "Finger Action: Brush pen"),
  never "Slot 3".
- **grey**: the one grey every brush paints with (since 2026-10-24, the owner's "procreate method"): "the grey in use",
  "47% grey". A brush has no grey of its own, so never "this brush's grey" or "the slot's grey", and never "colour".
- **size**: the one size every brush draws at (since 2026-10-25, the owner's "use only the current setting"): "Size:
  4.2 mm". Never "the slot's size". The eraser and the smudge tool have their own, as in Procreate ("the eraser's
  size"). (The Tip's **Size** dial is the brush's own.)
- **No Edit button**: the side bar is changed by a long press (the owner, 2026-10-19), so no label says Edit. The
  window a size's long press opens on the Tools page is **Sizes** (**Sizes and Greys** until the one
  grey, 2026-10-24; Brush Lab's too until its picker box, 2026-10-29). The drawing screen's column has none since 2026-10-24. No **New Brush** since the refocus (2026-10-24):
  Brush Lab's **Randomize** makes one, and its **New** (2026-10-29). The Tools page (the redesign, 2026-11-03, the
  owner: "it's not clear what to do once you're there"): its bar **‹ Back**, the title **Toolbar** ("Tools" until then)
  (no job line since 2026-11-06, "Tap a slot, then a brush for it." gone; a change's note in its place for a moment); the selected slot's tool named ("Fill in · fills shapes") beside **Edit in Brush Lab** (a place's
  name after a verb, three words but one place; **Brush Lab** in the bar's corner until then, Tweak in Brush Lab before);
  the try strip's head "Draw here to try Fill in" and **Clear** (Brush Lab's too, its Scratch Pad until then). Its
  library: **Search** (the field's hint too), its × **Close search**, no finds "Nothing found" (2026-10-28), then one
  filter row (tabs over chips until 2026-11-03; the **All Styles ▾** menu until 2026-10-28): **Recent**, **Saved**,
  **Pens**, **Markers**, **Brushes**, **Washes**, **Paint**, **Dry Media**, **Print**, **Odd Ones**, **Effects** (2026-11-06: the tools that rework the marks there, each "… Effect"; a Basic tool under
  what it does, as the tool picker groups, since 2026-10-31: the Spirograph, Stamp, Fill in, Lemon juice, Sumi and the
  like under Odd Ones, a print under Print (the Stipple and Hatch tips too, 2026-11-06), the rest by their style's kind; no style shown), **Kits**; beside Search,
  **More Brushes** (the release pare-down, 2026-11-05: the kinds and the tool picker show the release's tools, the
  other Basic tools are on this shelf, kind by kind); no **All**, no
  **Tools** tab; empty, "Nothing in Saved yet". No Undo, Load Kit, Make Stamp or Back to on the page; the checkbox **Hold
  Opens Full Library** (the tool picker's foot row: ticked, a slot's long press opens the Tools page, the picker's Full
  Library; **Hold Opens Library** until Library became Full Library, 2026-11-06; the switch **Tools First** from
  2026-10-28 until 2026-11-06, the owner: "even i don't understand what that means")
  shows in its bar only while ticked, said ", on" or ", off" as As New. A checkbox is Brush Lab's As New box: Phosphor's
  square before its words, ticked while on; a full row's words may run to three, as Reset to Default (Hold Opens Full
  Library four, the owner's own words).
- **The layer button** (2026-11-06, a first try of three layers; the owner's three stacked diamonds, under the picker box
  since that day's second try, over Refresh before):
  no label; said "Layer 2 of 3" (", hidden" while its pair is hidden), its
  screen-reader actions **Layer 1**, **Layer 2**, **Layer 3**; a switch's note "Layer 3". Layer 1 the bottom. Its hold
  (2026-11-06, screen-reader action **Layer Visibility**): **Layer 3**, **Layer 2**, **Layer 1** top to bottom, the one
  drawn on marked, each with Show Kinds' eye (**Full**, **Half**, **Faint**, **Hidden**), said and noted "Layer 1 half";
  a stroke on a hidden layer is refused, "Layer 1 is hidden".
- **Send to the Fridge** (2026-11-06, Iconoir post; `Fridge`, `FridgeWindow`): the menu's row after Export Page, the
  Manta's alone, none on a cover. The first time a window headed **Send to the Fridge**, its words `Fridge.ABOUT`
  (where, who sees it, Take it down, Nothing else is sent), **Send** and **Not Now**; System Settings' row **The
  Fridge** (**On**, **Off**, "Off asks before sending"). Its window, headed the same: the page's preview, the fields
  **Name** and **Caption**, the ticks **My Fridge** (the tablet's own unlisted page) and **The Fridge** (the shared
  door), **Send** ("Sending" while it goes), **Cancel**, **Sent Pages** while any are kept. My Fridge's first time: headed
  **My Fridge**, "A short name for its link", **Name**, its link under it as typed (else "3 to 24 letters or digits"),
  **Make**, **Cancel**. Sent: headed **Sent**, "On My Fridge now.", "On My Fridge now. On The Fridge once approved." or
  "Waiting for approval.", the links, **Copy Link** ("Copied"), **Close**. The list headed **Sent to the Fridge**, a card
  "Harbour, page 5, Nov 6, 2026" over its links, **Take Down** (asked "Take Down?", "This can't be undone."). Notes "Send
  failed", "Too large", "Taken down", "Take down failed", or the server's own short words ("Try again later", "Name taken").
- **The page bar**: the page and its count as "3 / 24", a cover as **Cover** or **Back**; screen-reader text "Page 3 of
  24", "Front cover", "Back cover". Its buttons **Previous page**, **Next page**, **Notebook** and **New page** (the + tile after the last page,
  2026-10-24, its note in a full notebook "Notebook full, 250 pages"). On the
  drawing screen its previews say "Page 3 of 24" (the page drawn on "Page 3 of 24, drawing"), "Front cover", "Back
  cover"; a page's screen-reader actions **Move Page Up** (toward the last page, the top of the stack), **Move Page Down**
  and **Page Settings** (was Page Options).
- **Page Settings** (a hold on a page, 2026-10-23): headed "Page 5" with the notebook's name under it, **‹ Back**. Its
  rows **Template**, **Page Colour**, **Surface** (2026-11-01: "Notebook's (Smooth)" or its own, a tap its screen, "The paper page 5 is on alone"), **Show Through** (the page's step, **Solid**, **Half**, **Faint**, **Hidden**, and
  **Ghost**), **No Erasing**, **Timed Pages**, **Layers** (2026-10-24: a chip a layer, **1** the bottom, then **Add**, and
  **Merge Old** on a page with the old in-page layers; said "Layer 2 of 3", "3 layers", "One") and **Move** (**Earlier**,
  **Later**; the page bar's hold no longer lifts a page, it opens Page Settings at once). **Notebook's** is what a page takes from its notebook, said with what that
  is: "Notebook's (Lined)", "Notebook's (Off)", "Notebook's (5 min)". A rule's other chips **Off** and **On**. Timed
  Pages On has a slider under it, the page's time in words beside it ("30 s" to "60 min", as Timed Pages' screen says
  them: "12 min", "90 s"), said "Timed Pages: 12 min". At the foot two plain-words buttons, **Export Page** and
  **Delete** (Clear page is the eraser's hold's, the cover picture Settings' Cover Picture, Replay Page the menu's).
  Export Page (2026-10-28; the drawing screen's Menu too, for the page shown, 2026-11-02) opens the usual menu: **PDF**, **PNG** (2026-11-05: its sizes beside it, each with the page's numbers, **Social,
  1080 × 1444**, **Page, 1785 × 2387**, **High Res, 3570 × 4774, sharp**), **Layers** (2026-11-05: the same sizes beside it, its High Res
  **High Res, 3570 × 4774, sharp, large file**, the owner: "just warn the user that the file will be large"; the browser's and a guest's too; a Photoshop file, its layers **Paper**,
  **Template**, **Old Paint**, **Dry Media**, **Washes**, **Pen**, **Paint**, **Magnets**, "Layer 2 Pen" on a page of
  several layers); done, the note says the file and where, as Export Now's: "p5.psd in EXPORT/Superdood" (the file
  "Harbour p5.psd"); else "Export failed". Its last row **Send** (2026-11-05, the owner: "a lightweight
  quick share option"; `LiveSend`): a window headed **Send**, "superdood.org" over its four letters (as Draw Together's),
  the QR code, the caption "On this Wi-Fi, for ten minutes." and **Close**; notes "Wi-Fi off", "Sharing failed". The
  browser's page: the notebook's title, then **Page 5** and **Notebook** over their files, **PDF**, **PNG** with its
  size's words beside it, **Layers**, and the same caption; superdood.org's Found page "Opening the files".
- **Tracing paper** (a layer is a page): **See-Through Pages** (was Page Transparency, and before that Pages Show Through; one name with its notebook-settings row, 2026-10-23), **Off**,
  **Page Before** or **Nearby Pages**, and **Faint** (the default strength, else Half), in the settings' **See-Through Pages** row (Off and On;
  a long press opens its screen: **Off** "Only the page drawn on", **Page Before** "The page before, at half",
  **Page Before, Faint** "The page before, faintly", **Nearby Pages** "Four nearest pages, at half", **Nearby Pages,
  Faint** "Four nearest pages, faintly") and on the strip's button (said "See-Through Pages: Off"). A page's steps **Solid**, **Half**, **Faint**, **Hidden**, as words, and **Ghost** (pinned to show through); a
  preview adds ", ghost" or ", hidden" to "Page 3 of 24". The **layers** (a layer is a clear page stacked on the page
  below): Page Settings' **Layers** row since the refocus (2026-10-24; the side column's group, its layer menu with New
  Layer Above and Below, Duplicate, Merge Down, Delete and the steps, until then). A page of layers is one page
  everywhere else ("3 / 24", "Page 3 of 24").
- **The drawing screen's column, 2026-10-22**: **Menu** is Phosphor list (Bold), no rule under it; no rules between
  the layers' add buttons and their thumbnails (the owner: "the menu on top should be phosphor list and not have a line
  underneath.. lines dividing layer add buttons: remove"). **Zen Mode** (Phosphor circle, Menu's size, in the corner
  under the brush line beside the page bar, under the column, always there; on, Phosphor circle-dashed in the very same
  spot, said "Leave Zen Mode"; no menu row. The owner: "it should be in the same spot it is when it's active"; "i meant
  the same vertical position, just shifted over to the left. and the circle can go below it. it can be the same size
  circle as the menu on top"; "for zen mode ... not enabled use phosphor circle and when enabled use
  circle-dashed", swapped 2026-10-23, "Make the zen circle dotted when zen mode is not active, and a circle when it
  is": off circle-dashed, on circle). The **size slider** (2026-10-24, "a quick size slider") is said "Size: 4.2 mm",
  its size in millimetres as Brush Lab writes them ("1.4 mm" under 10, "12 mm" above), and the same words show in a box
  beside its nib on the page while the pen is on it. No label, no Fine or Broad: its wedge says which end is which. The
  grey strip is said "Greys, black to white: 47% grey in
  use", the grey in use marked by its square dot alone ("no need to have a line in the gradient picker, just the dot on
  the side is fine"). Its long press, with Opaque paint in use (2026-10-27), opens **Opacity** beside it, a strip of
  its own said "Opacity, thin to opaque: 40%" (the Opaque medium's end words); on any other brush the note "Opaque
  paint only". The **Dropper** under it (the owner's word, "add a dropper tool", Phosphor eyedropper) is said
  "Dropper", ", in use" while taken up, and the grey it picks is said beside the strip as the strip says one ("47%
  grey"). No swatches (they went 2026-10-24). The drop (Phosphor's: the
  owner, "for drop icon use phosphor drop") "Dry." (outline, dashed since 2026-10-23: "the wet icon seems like it's always on now"), "Paint moving. Tap to dry." (half full), "Wet. Tap to dry."
  (full); it sits under the icon block, alone in its row since 2026-11-06 (the sand timer, Show Kinds and Play over
  it until then, now the menu's rows). **Refresh** (the ghost, said "Refresh") is alone in the column's row under the Dropper and the Lasso
  (2026-10-28; on the page bar that morning). The brush line between the page and the page bar, the screen's whole width, under the column's foot too:
  "Name · description, card words"
  (`DrawLayout.brushWords`: the brush's name, "·", a Basic tool's one-line description as the Tools page and the tool
  picker show it, then its card's words, comma-joined, anything that only says the name again left out: "Swell brush ·
  presses darker, Brush pen"; the owner, 2026-10-26: "add description to brush info on bottom of screen"),
  cut with … to fit; the eraser "Eraser"; a tap on the name does nothing (it opened Brush Lab, said "Tap for Brush
  Lab", until the release pare-down, 2026-11-05: Brush Lab is the picker's Edit Brush alone; no stroke preview before the
  name since 2026-10-27, "remove stroke from bottom line brush info"). Nothing else names a slot
  picked by a tap (2026-10-25). The foot's far corner holds the lasting status, as wide as its words (the open
  notebook's name until 2026-11-02, the owner: "use it for system messages").
- **Finger Action** (System Settings' row since 2026-11-06; the main menu's row from 2026-10-27, "Finger Action: Eraser",
  "Finger Action: Pan" in a No Erasing notebook; the side column's button from 2026-10-26): its chips **Nothing**,
  **Eraser** (**Pan** in a No Erasing notebook), **Smudge**, the slots' brushes by name, three a line. The browser's menu
  keeps its "Finger Action: Eraser" row. No button in the column.
- **Draw Together** (the menu's last row, live branch `live-host` only, 2026-11-01, Share on Wi-Fi until 2026-11-02; marked while on): its window is
  headed **Draw Together**, each way in as its place in heading type over its code in title type (superdood.org and its
  four letters once they come; on the Wi-Fi the address and the key in fives), its QR code, the caption "On this Wi-Fi, while the app is
  open." (through superdood.org's relay, 2026-11-03: "Online, while the app is open.", and no address or key), buttons **Close** (the main one) and **Stop Sharing** (plain, 2026-11-02). Notes: "Wi-Fi off" (no Wi-Fi address; also in place of the link),
  "Sharing off", "Sharing failed". The link is the **link** (never URL or address on screen); its part after # the
  **key** (docs/superpowers/specs/2026-11-01-manta-host-design.md).
  The four letters are typed at superdood.org (docs/site/index.html, 2026-11-05): its box headed **Draw Together**, the
  label "The four letters shown on the Manta", **Join**; notes "No such code. Codes last ten minutes and work once.",
  "Too many tries. Wait a few minutes."; found, "Found!" over "Opening the drawing" and **Open**.
  In the browser on the Manta's link (branch `live-books`): Menu's **Notebook Explorer** (the Manta's word) opens the
  Manta's shelf, headed **Notebooks**, with **Back**; a tile's hold **PDF** and **Notebook File** (as Export Now);
  Menu's **Export Page** **PDF**, **PNG**, **Layers** (as Page Settings). Over the page while the Wi-Fi is gone: "Not
  connected". The Manta's shelf there has **Import** (branch `live-upload`: a notebook's file, a picture or a PDF sent up, each a new notebook). Notes "Can't read it", "Too large".
  A file dragged over a place that takes it (a desktop's, branch `live-import`) shows a dashed outline there and its
  note, what a drop does · what it takes: "Drop to Place · picture or text" on the page, "Drop to Import · PDFs, pictures" on
  the Manta's shelf (a notebook's file goes without saying there). Text dropped on
  the page (a .txt file or plain words) is a **text box** where it lands.
- **A live page's status** (live branches, 2026-11-01, the owner: "also add connection status?"; `LivePresence`): the
  Manta's, in the foot's far corner while it lasts and a caption line under Draw Together's: "Connected, no guests", "Connected, 1
  guest", "Connected, 2 guests", "Connection lost". A browser guest's, at the brush line's right while no other note
  shows: "Connected", "Connected, 1 other guest", "Connected, 2 other guests", "Host gone" (no Manta on the relay),
  "Reconnecting…", after 15 s "Connection lost". A **guest** is anyone on the page but its Manta, the **host**.
  Host gone and Connection lost cover the page, the status as its title over a note: "Drawing waits here until the
  host is back." and, lost, "Waiting for the Manta, maybe asleep. Back within a minute or two of waking." (2026-11-02, the owner: it "says it will be able to
  reconnect but it doesn't"; the page keeps trying on its own while open, so no **Reconnect** button).
- **Zoom in the browser** (2026-11-01): the percentage alone, said "Zoom 79%" (no padlock: two fingers always pinch and
  pan there); a tap shows the whole page.
- **Undo and Redo in the browser** (2026-11-03): icons alone on the page bar at the column's end, said and shown on
  hover as **Undo** and **Redo**; greyed with nothing to do. None on the Manta (its two- and three-finger taps).
- **The column's fourth row, 2026-10-23** (beside Play, under the drop, since layout v3, 2026-10-26): **Template Visibility** (its eye alone, said "Template Visibility: Faint"),
  a tap the next step with the menu's notes ("Template half"), a hold its menu **Full**, **Half**, **Faint**, **Hidden**
  (the one in use marked). The **Lasso** beside it (said "Lasso", ", in use"
  while taken up, and while the edge strip's hold makes its loop delete): its bar over a floating piece **Cut**,
  **Duplicate**, **Make Stamp**, **Delete** (2026-10-28; Duplicate in Copy's place; Make Stamp 2026-11-02, before Delete, which stays last), and its tap's menu **Paste** alone, once something is cut and nothing
  floats. No Trash button (2026-10-25 to 2026-10-28; the edge strip's hold deletes what the loop rings); notes:
  an empty loop "Nothing to move" (the hold's "Nothing to delete"), Paste on a page of
  another size "Page size differs", a locked page "Page locked". A dirty brush holding paint: the smudge tool is
  said "Smudge: Dirty brush, holds paint", and a slot's hold opens **Clean Brush** and **Tools** (the tool picker).
- **The tool picker, 2026-10-25** (a slot's hold): each drawn Basic tool's name as the Tools page shows it ("Steady
  line", sentence case, said the same), grouped by kind with no headers (2026-10-27; the release's tools in their order
  2026-11-05; since 2026-11-06, the owner: "group alphabetically but divide by type", the Tools page's kinds in its chips'
  order, each kind A to Z, a grey line between kinds, still no headers); under them one foot row in menu words, all one size (2026-11-06, the owner: "put edit brush, full library, and hold
  opens full library on the same line, and same size font... also include link to open brush studio"): **Edit Brush**
  (Brush Lab; said Brush Lab until 2026-11-05), **Full Library** (the Tools page; **Library** from 2026-10-28 until
  2026-11-06, Advanced before), the checkbox **Hold Opens Full Library** and **Brush Studio** (the web page; in
  the browser's foot only since 2026-11-06, the owner: "it shouldn't be a browser, it should be the local app version": the app's Edit Brush is it, and Brush Lab shares to it); over those **Stamp ▾** for a Stamp; at the top, for a Basic tool changed in Brush Lab only, **Reset to Default** (the owner's words, 2026-10-27; at the top since 2026-10-28; the Tools page's too, plain words before Edit in Brush Lab, 2026-11-03).

- **Light popouts beside the column, 2026-10-22** (the owner: "long press eraser menu, make it smaller and lighter so
  it doesn't leave ghosts on the screen after"): the eraser's kinds (**Everything**, **Pen**, **Paint**, **Dry Media**)
  and **Clear page**, an offer, a short label: words in captions on white, a fine grey outline, no boxes; the one in use
  a small dot before it and bold (said ", in use"), never ink-filled.
- **Notes on the drawing screen, 2026-10-22** (the owner: "the system update alerts... got cut off by the canvas. maybe
  put it in a dedicated spot... small stuff to the right, same height as the name of the current brush"): every status
  message ("Superdood hasn't been tested on this firmware yet.", "Erasing pen", "Guides half", …) shows at the brush line's
  right, in captions as the brush's name, for 4 s; it takes at most two thirds of the line, the name giving way first,
  and a longer note is cut with …, so keep notes short (rule 7). Off the drawing screen, under a dialog and in Zen Mode
  it is the boxed toast as before. Notes that need a tap stay offers or dialogs.
- **Fill, retired 2026-10-23** (the owner: "maybe this app doesn't use a bucket, just a brush with fill in the lines"):
  the paint bucket and its notes are gone; the Basic tool **Fill in**, "fills shapes" (a shape it starts in, or a loop on
  open paper, since 2026-10-31), and Page Colour do its work.
- **Fill, 2026-10-22**: open paper fills up to the page's edges, a blank page whole (the owner: "i just used the paint
  bucket on a blank page and it said no closed area.. it should fill the page ideally"); "No closed area" is gone, and
  a tap on a thick line with no room near says "Nothing to fill here". A tap that would fill nearly the whole page says "Hold to fill the whole page" (the owner,
  2026-10-22: "i also accidentally filled a blank page"); a fill the tablet has no memory for, "Not enough memory to fill".
- **The drawing screen's menu** (four groups since the refocus, 2026-10-24, the owner: "it's chaotic"; a grey-400 line
  between them; its rows since 2026-10-28): **Settings**, **Notebook Explorer** (to the shelf), **Import PDF**; **Import
  Image**; **Play** (Phosphor confetti, marked while a toy is on); **Finger Action: …** ("Finger" until 2026-10-29) (above), **Left-Handed** (marked
- **The drawing screen's menu** (groups since the refocus, 2026-10-24, the owner: "it's chaotic"; a grey-400 line
  between them; the owner's four since 2026-11-06): **Notebook Explorer** (to the shelf), **Notebook Settings**; **Export Page** (2026-11-02, the
  owner: "it'd be good to have an export in the dropdown menu"; the page shown's, Page Settings' menu, none on a cover; the
  browser's Menu the same), **Import** (Import PDF and Import Image until 2026-10-29), **Show Kinds** (**Page Bar** after it until 2026-11-07); **Draw Together**
  (live branch), **Sand Timer** (while it can be set), **Play**; **Left-Handed** (marked
  while on: the column on the right), **System Settings** (2026-11-04, the owner's name for it; Iconoir settings) last: a popup where the menu was
  (2026-11-06; its own page with a bar ‹ **Back** until then), the title **System Settings** (no job line, "For every notebook." gone, 2026-11-06); its rows **Automatic Refresh** (**On**, **Off**; under it "After
  page turns, zoom and erasing"), **Finger Action** (above, 2026-11-06), **Two-Finger Tap** and **Three-Finger Tap** (**Undo**, **Redo**, **Refresh**,
  **Nothing**), **Edge Strip Hold** (**Trash**, **Lasso**, **Nothing**). Refresh is the column's word for a flash; the
  owner's "flashes" stays out of the app's words. **Export Now** (the one way the PDF and PNGs are made) and **Delete Notebook**
  are Notebook Settings' foot, Rename its name's button. **Show Kinds** (2026-10-28, the column's eye until 2026-11-06,
  the menu's row since, its popup beside the column; the page drawn on's: **Pen**, **Paint**, **Dry Media** side by side, each eye
  under it stepping **Full**, **Half**, **Faint**, **Hidden**; notes "Pen half", taken up "Dry media is faint", refused "Pen is hidden, see Show
  Kinds"). No Template row (Template Visibility's notes "Template full", "Template half", "Template faint", "Template
  hidden" stay) and no Guides in PDF (cut: the guides always print). **Play** (Phosphor confetti, marked while a toy or
  Shapes is on) is the menu's row again since 2026-11-06 (the column's button from 2026-10-30, said "Play", "Play, on";
  Shapes its own button beside the timer from 2026-10-30 until the release pare-down, 2026-11-05). **Play**'s menu:
  **Text** (explore-text, 2026-11-01), **Magnetic Poetry**, **Shapes** (its menu beside Play), **Spirograph** (the Basic
  tool on the slot in use), **Mirrors**, each marked while on, then **Replay Page** (on a page it can replay). No
  Self-Portrait Prompt (cut 2026-11-05), no Stamp, Surprise Me or Fling since 2026-10-28. Import opens the app's own picker (2026-10-26, the owner: "there's no way to
  cancel"; pictures and PDFs, a folder at a time, 2026-10-29: "more like supernote file explorer"), headed **Import**:
  the folder's crumbs, **Storage** then each folder's name with "/" between ("Storage / Document / Superdood"); each
  row a folder's or file's name, a file's day under it ("Oct 23, 2026"); ‹ and › with "2 / 5" between pages (said
  "Previous page", "Next page"), and **Cancel**; an empty folder "Nothing found". A PDF picked: its page shown, ‹ "3 /
  12" ›, then **New Notebook** (the whole PDF), **This Page** (the page shown, placed as a picture) and **Cancel**. A picture being placed has **Lighter** and **Darker** (a step a tap, three each way, 2026-11-01), **More Contrast** and **Less Contrast** (the same, 2026-11-02),
  **Cancel** and **Place** along the page's foot; notes
  "Can't read it", "No pages", "Importing 3 / 12" (New Notebook's box, headed Import, with **Cancel**), "First 49 pages", "PDF in
  Document/Superdood" (2026-10-24: the PDF lives in Documents now), "Export failed",
  no **Self-Portrait Prompt** (the Daily Prompt until 2026-10-30; cut at the release pare-down, 2026-11-05, in git
  at `pre-pare-down`). A cover is never timed: no countdown there.
- **A notebook's PDF** (2026-10-24, Document/Inkwash/<title>.pdf, the app's name, not "Inkwash", in code): its outline
  says **Cover**, then a day as "Oct 23, 2026" with its pages under it, "Page 3" (a card's words after it, "Page 3: A self-portrait as a
  knight…", until the Self-Portrait Prompt's cut, 2026-11-05). Its page labels **Cover**, 1, 2, …. Its hidden words (search only,
  never seen): "<title>, page 3", "Oct 23, 2026" or "Oct 23, 2026, changed Oct 24, 2026", the template's name, the
  card's words, each magnet's word. A second PDF of one title is "<title> 2.pdf".
- **Inside a notebook**: the bar as the Tools page's (2026-11-03, the owner: "make it consistent across the app"):
  **‹ Notebooks** (back to the shelf: the place it goes, as Brush Lab's ‹ Toolbar, since ‹ Back would read as back to
  the page), the notebook's name centred (over its job, "Tap a page to open it.", until 2026-11-06, the owner: "it's not
  necessary"), **Settings** (2026-11-06, the owner: "instead of an icon only for notebook settings we should write
  SETTINGS", then "Settings in normal case is fine": its word in its own case, not the label's caps; said **Notebook
  Settings**; the menus' Settings icon alone from 2026-11-03, and still on the Nomad in portrait), **Open** (the owner,
  2026-10-21: it may be a journal too; the page's screen-reader text "Tap to open it"), **Use on Cover** (**No Picture**
  on the front cover), **Delete Page**; said the same where they are icons alone; ‹ and › either side of the page,
  said "Previous page", "Next page". A page's preview in the strip has **Favourites**' star in its upper right corner
  (2026-11-06, the owner: "tap a star on the upper right of a page preview to save it to a special favourites notebook";
  Iconoir star, filled once starred; none on a cover, nor in Favourites itself), said **Add to Favourites** and
  **Remove from Favourites** (the page shown's screen-reader action); starred, the note "Copied to Favourites";
  unstarred, first "Remove from Favourites?", "This can't be undone.", **Remove** and **Cancel**; notes "Copy failed: …",
  "Remove failed: …". **Favourites** (Canadian spelling, rule 11) is the notebook the star copies a page into, as it is
  then: made at the first star, first on the shelf, no Rename or Delete there (its tile's hold **Settings** alone); another
  notebook named Favourites, in any case, is refused "Name taken". A star is filled only while Favourites holds its copy. The strip holds the front cover, the pages, + and the back cover, scrolling with
  the pages (no settings tile since 2026-11-03; the covers back that evening, the owner: "i just asked that the cover
  not be sticky on the left"). The shelf's bar **Notebooks** (over "Tap a notebook to open it." until 2026-11-06). On the drawing screen's
  page bar a tap on the front cover's preview opens the notebook's settings (2026-11-03). The notebook's **settings** (a plain list since the design pass, 2026-10-23, the owner: "replace it with the list";
  once the library card; the bar's **Notebook Settings** opens it (the strip's **Settings** tile until 2026-11-03), as do the shelf tile's long press row **Settings** and the
  file menu's row; the one place a notebook's settings are set): the bar's **Notebooks** (no Lock since the
  refocus, 2026-10-24); the notebook's name, **Rename**
  under it; then a row each, its key in caps and what it is set to in one line, in three groups (no **More** since
  2026-10-27: "just include everything on that page"): **Template** (its thumbnail and name, then "· Faint" and so on when not Full), **Random Template** (**Off**, or **All**, a template's
  name, "3 templates"; its screen Random From), **Page Colour** (six swatches, the six greys, said "White", "13% grey" to "67% grey", "Black", ", in
  use"; an older notebook's grey from the slider stays, said as it is, "30% grey", no swatch chosen), **Surface** (2026-11-01, the owner: "Surface moves to the notebook": the surface's name, the Paper choice's label, "Smooth ›"; its screen "The paper every page is on", a tile each, its picture and name), **See-Through Pages** (**Off** and
  **On**; On is Page Before; a long press opens its five choices), **Nib Preview** (2026-10-29, **Off** and **On**, the hover ring's dots; "nib" as in the Tip's words, where the mark lands), **Self-Portrait Prompt** (2026-10-30, **Off** and **On**, a card dealt each new page); **No Erasing** (**Off** and **On**; set on, the note "No eraser, no undo": no
  eraser, no Undo or Redo, no Clear or Delete of marks), **Timed Pages** ("5 min", Off), **Lock Pages** (2026-10-27, **Off** and **On**, greyed while no page is timed), **Tools** (the choice and the bar's brushes as small strokes; Brushes on
  Bar on its screen); **Cover** (the design's name), **Cover Picture** ("Page 3" or **No Picture**; chips **Use on Cover**, the page drawn on, and **No
  Picture**); "›" where a row opens a screen; the foot "Made Oct 23, 2026 · 2 pages", or from then to its last edit "Oct 23 – 30, 2026 · 2 pages" ("Oct 23 – Nov 2, 2026", "Dec 30, 2026 – Jan 4, 2027"; 2026-10-30) ("1 page"; the pages alone when
  the day isn't known). A row's screen:
  **‹ Settings** (**Back** from a hold in the strip), its title and a sentence-case line saying what it does, its choices
  as grey tiles (a name and what it does, or a picture and a name in up to two lines, a long file name broken rather
  than cut), the one in use with the ink tab, said "…, in use". Template's ("The guides on every page": **Blank**, **Lined**, **Wide Lined**, **Grid**, **Dot Grid**, **Isometric**, **Hex**, **Music**, **Cornell**,
  **Storyboard**, **Diary**, **Diary Page**, **Comics**, then each of the owner's own Supernote templates by its file name
  without the extension ("3D Regular"), then **Random** only in a notebook that is Random (the Random Template row sets
  it); a long press on Random, or the Random Template row, opens **Random From**, "The templates Random picks from",
  tabbed, and **Done**); a page's own, from its Page Settings ("The guides on page 5 alone", the notebook's own template named "Notebook's (Lined)",
  Random only in a Random notebook); Tools' ("Where the bar's brushes come from": **My Toolbar** "The app's toolbar, free
  choice" (the way out of a fixed toolbar, the owner, 2026-10-22), **Surprise Me** "Eight brushes by chance", in use "Tap
  again to roll new ones", **Library** "Picked from the library", **Each Page** "New every page, can't change"; under
  them **Brushes on Bar**, a slider, **1** to **8** then **All**: how many slots show, not where they come from); Timed Pages'
  ("A countdown on every page": a slider, **Off**, "30 s" to "60 min", whole minutes as "5 min", the rest as
  "90 s", marked Off, 5, 30 and 60 min) and Cover Design's, from the Cover
  row (a hold on the front cover in the strip until 2026-11-03) ("The pattern on both covers": the designs' names); **Template Visibility**
  (a row of four chips on the Template screen, not a row of its own, hidden while Blank: **Full**, **Half**, **Faint**, **Hidden**, the one in use ink-filled). The PDF always prints the guides (Guides in PDF cut, 2026-10-24). A new notebook's name says its choices at its first save:
  its title stays as it is (no template word, 2026-10-29), then
  the rules where they fit, "- no erasing - 5 min" ("- 90 s"), "- one brush", "- 3 brushes" (an older name: "Oct 20
  bashful-cherry diary - no erasing - 5 min"). A Timed Pages page's
  countdown at the top of the side bar: "4:30" (screen-reader "Time left 4:30"). Elsewhere that row is the **Sand
  Timer**'s (2026-10-27; its icon beside the wet indicator until 2026-11-06, the menu's **Sand Timer** row since, while it can be set): its choices **Off**, "1 min", "3 min", "5 min", "10 min", "30 min", its
  countdown as Timed Pages' ("Time left 4:30"), at zero "Time's up" and **Done** (a Timed Pages page's the same, with
  **Next Page** between them). A Diary Page's **timed sections** (its boxes
  **Did**, **Saw**, **Heard** and **Picture**, Title Case, shown in caps) put the section over its time there, "Saw" over
  "1:42" (screen-reader "Saw, time left 1:42"), then **Done**; notes "Next: Heard", "Done", "Section locked". The times are
  the template's own (the prompts file's line "Diary Page times | ..." set them until the Self-Portrait Prompt's cut, 2026-11-05). Notes: "Erasing is off", "Undo is off" (Undo or Redo under No Erasing), "Page
  locked" (a PDF's page, or a page run out under Lock Pages; "Time is up" and its popup "This page is locked. Start the next
  one?" with **Stay** went, 2026-10-27: the sand timer's popup says it). The Tools page of a notebook whose
  toolbar can't change says why in its bar, in place of "Tools": "Notebook picks tools each page" (Each Page), its
  library greyed, and **Notebook Settings** opens the settings' Tools screen; a Brush Lab change such a toolbar can't keep
  gives the same note. (No "Notebook locked" or "Notebook sealed": Lock Settings was cut, 2026-10-24.)
  The page's printed lines are its **guides** (never "grid" or "lines").
- **library**: brushes you saved (the Tools page's Saved tab). **Basic tools**: the built-in presets.
- **Vector ink** (the owner's Vector Ink, 2026-11-04; a Basic tool under Pens, "stays sharp", named as the other Basic
  tools are; the medium **Vector Ink**, Title Case as a label, the same day: its card's words "Sharp at any zoom." (also
  the last sentence Brush Studio's words give any brush kept sharp when zoomed, sharp-brushes, 2026-11-04), its
  blurb "A smooth line that stays sharp however far you zoom in.", its personality "A smooth pen whose lines stay sharp
  however far you zoom in."): its layer has no name of its own on screen; a layer made for it on top says nothing (2026-11-04, the owner:
  it just works). Its notes show beside the tool in use (the label, as Cut Stamp's words), never at the brush line's
  right, which is Draw Together's connection status's (the owner, 2026-11-04): "Vector ink full" (the layer in use's
  lines at their cap), "Not in Vector ink" (a shape or Make Ink with it). "Top layer only" and "Needs a fresh layer"
  went the same day; "Layers full" and "Vector ink only" on 2026-11-06, when it began drawing on the layer in use.
- **effect** (the owner, 2026-11-02: "the brushes that don't leave a mark, an initial mark, they should use the word
  'effect' in their name to make it clear they affect stuff on the page already"): a brush that only reworks the marks
  there (`WildSample.reworks`: a smudge laying little paint of its own, or clear water) is shown with "effect" after its
  name, in the name's case: a Basic tool's sentence case "Water effect", "Palette knife effect", "Clear water effect", a
  Title Case name "Polite Smear Effect" (`WildSample.shownName`, the browser's through WebEngine). Today's Basic tools:
  Blend, Blur, Wash comb, Dirty brush, Water, Palette knife, Clear water, Comb, Gouache water, Photocopier, Newsprint. Any
  brush that reworks takes it, a library brush or one of the person's own, since the word says what it does; a name that
  says "effect" already keeps it once. Where a brush is named as itself: cards (Tools page, library, Recent, Saved, This
  Brush), the tool picker, the bar's slots and their said words, Finger Action's choices, the
  brush line, Brush Lab's title and Open's cards, a kit's steps. Display only: the kept name (codes, the library, the
  rename field, kits.txt) never changes, and words that quote a brush in passing keep it as kept ("Back to Water",
  "Opened Water", "Kept Water in Recent"). The smudge tool, whose every choice is one, keeps its own words: its
  chooser's cards "Blend", "Water", "Blur", its button "Smudge: Water" (the brush line names its brush as any other's,
  "Water effect · press to lift"). Never "· Effect" (rule 4), never "Effect Effect".
- **Tweak it**: the six variations of the brush in the web lab (Brush Lab in the app until 2026-10-29); each one is a whole brush, drawn as a loop.
- **style**: Fineliner and the rest. **Anything goes** keeps its name. **Print** (2026-10-21; its key "Print", after Anything goes in
  codes, before it on screen): the printed brushes. Its Basic tools are named by their print and their tool, as the rest
  ("Dot tone", "Halftone brush", "Dither pencil", "Engraving pen"; "Mezzotint" alone, as "Mezzotint charcoal" passes a
  name's room); the texture markers (2026-11-06) by their pattern alone ("Maze", "Brick", "Diamond dots"), but **Pixel pen** and **Stitch pen** (the same day; "Stitch pen", not "Cross-stitch", After's Cross-Stitch); captions say what the print looks like ("even dots", "dots swell", "velvet dashes", "banknote style").
  The everyday pens are named as sold: **Ballpoint**, **Gel pen**, **Fountain pen** (Fineliner), **Highlighter** (Marker,
  "behind marks").
- **brush code**: the code you copy and paste.
- **drawing**: the document. Never "painting".
- **Words**: the fridge magnets' bin (WORDS, **More words**, **Done**); a word tile on the page is a **magnet**; a
  bundled or imported list is a **pack** (the bundled one is **Classic**), its page shown as "Classic 3/14". Play's
  row that opens and closes the bin is **Magnetic Poetry** (Phosphor magnet, marked while open).
- **Text** (explore-text, 2026-11-01): Play's row (a drawn T, marked while taken up); typed words on the page are a
  **text box**, never "label" or "note". A box's long press menu: the sizes **Small**, **Medium**, **Large**,
  **Huge**, **Bold**, the alignments **Left**, **Centre**, **Right** (each marked when it is the box's), **Make Ink**
  (the box as one stroke of the brush in use) and **Delete**.
- **Mirrors**: Play's row (Phosphor flip), its tap the mirrors' list, marked while the page's mirrors are on. **Play**
  (the refocus, 2026-10-24; Phosphor confetti, the owner's pick) is the side column's one button for the toys. "Fun" is
  not a word the app shows.
- **mirror**: a line that copies or reflects the page (never "axis" or "symmetry line"); its dashed line is its
  **guide**. Its list's rows: **Mirrors** (on or off), **Show Guides** (on or off: the dashes only, the mirrors act all
  the same), **Copy Strokes**, **Real Mirror**, **Place Mirror**, **Kaleidoscope**, **Flip Side**, **Keep Reflection**
  (Real Mirror: what is seen becomes the drawing, and the mirrors go), **Remove Mirrors** (the mirrors go, the drawing
  stays as it is; with Real Mirror the page shows as it really is again), never "Clear Mirrors".
  Its caption "3 mirrors, 6 copies" (Copy Strokes, the page itself counted; "24 copies max" at the cap) or "3 mirrors, 5
  reflections" (Real Mirror), "No mirrors". Notice: "Too short".
- **Tool kits** (2026-10-22, docs/archive/superpowers/specs/2026-10-22-tool-kits-design.md): a **kit** is the Basic tools one way
  of working needs, from `assets/brushes/kits.txt`. The library's tab **Kits** (a card a kit: its name, "3 brushes");
  This Brush's button **Load Kit** (said "Load the Marbling kit"); the kit's sheet: its title "Marbling Kit", its intro
  (one whole sentence, the brush words' rules: no semicolons), its steps "1 Drop ink" (a bar's words: a number, then
  three words at most, sentence case) with the Basic tool's name beside them ("Sumi"; the eraser's step "Eraser, Dry
  Media"), where it goes ("Fills the first 3 slots", "Fills and shows the first 3 slots" when Brushes on Bar rises),
  **Cancel** and **Load Kit**; refused, the Tools page's fixed note. A kit's slot picked says
  its step ("3 Comb rings") in the name's place. Marbling's notes: "No ink floating" (Clear water or Comb with nothing
  afloat), "Rings printed" (another brush printed the bath).
- **Randomize** and **Anything Goes** (Title Case, as buttons) in Brush Lab (New's styles, headed **Start From**, keep "Anything goes" in sentence case); **Surprise me** keeps its name wherever it appears. (The Tools page's Roll another went,
  2026-10-19, and New Brush 2026-10-24: Randomize makes a new one.)
- **The Tools page's sections** (the design pass, 2026-10-23, to 2026-10-28): **Active in Toolbar**, **This Brush**, **Scratch Pad**,
  **Library** (shown in caps by the LABEL style); This Brush's button **Tweak in Brush Lab**. **Brush Lab's** (2026-10-29):
  its bar "Brush Lab" alone, centred (its job line "Tap a part to change it, then try the brush.", 2026-11-03, the Tools
  page's pattern, went 2026-11-06, the owner: "remove the subhead"; the Tools page's "Toolbar" is centred too); **Adjust** (its **Start from
  Watercolour ▾** until 2026-11-03), the try strip "Draw here to try Polite Smear" (**Scratch Pad** until 2026-11-03)
  and **Clear** (no wet drop since 2026-11-03), **Randomize**; no label over the brush's buttons
  (**About This Brush** on the Manta until 2026-10-29, the owner: "you can remove the text ABOUT THIS BRUSH"), which
  are **New ▾** (said "New brush"; its menu of styles headed **Start From**, Start from ▾'s until 2026-11-03),
  **Open**, **Save** ("Saved" once it is; for a moment after a tap "Saved" or "Already saved"; said "Save over
  Fineliner" when it writes over a library brush, "Save to library" when it adds one) and beside it the box **As New**
  (said "As New, on" or "As New, off"; only for a library brush; **Save as New** from 2026-10-29 to 2026-11-03, Load
  became Open then). The brush's description shows only while its picture or name is tapped (a tap again hides it,
  2026-11-03). Under the name **Edited** once the brush changed since it was opened or saved,
  and **Suggested: Polite Smear**, a tap to take it (said "Use the name Polite Smear"). Open's sheet: **Paste code**,
  **Open**, **Cancel**, **This brush**, **Copy** and **Brush Studio** (said "Share to Brush Studio", 2026-11-06: the
  brush opened in the web Brush Studio, in the tablet's browser; with none, the note "No browser"), the **Library** with its pages; notes "Opened Fineliner", "Loaded
  brave-onion", "New Watercolour brush"; a code from a newer version (docs/BRUSH-CODE.md) "Newer version, some parts
  differ" (the drawing screen's note too, for one sent from Brush Studio), one of a shape this version can't read "Needs
  a newer version". **Back** always says its word beside
  its arrow; Brush Lab opened from the Tools page says where it goes, **Toolbar** (the page's title; **Tools** until 2026-11-03). No
  **Back to Fineliner** since 2026-11-03 (the slot's first brush's name; Undo and Recent hold it). A
  brush a slot let go: the note "Kept Fineliner in Recent" (several: "Kept 3 brushes in Recent"). A saved card's long
  press: "Delete Name?", **Delete**, **Keep**.
- **A new brush's name** (the owner, 2026-10-22: "the name gen can be a bit more reflective of what the brush is";
  "i like the y or ish words"; then 2026-10-29: "mostly deadpan"; "have the brush names hint at what the brush does
  for sure"; "not too bad, the name of the app is tongue in cheek"; 2026-11-02: "it's ok but seems to repeat quite
  often"): said by what the brush is, from `names/traits.txt` and `names/folks.txt`, kind and gently funny, never
  mean or rude. Always Title Case (small joining words lower case: of, for, on, a, in, with, and); Canadian spelling.
  Three kinds, picked by the brush's seed:
  - **Paint colour** (about 55 in 100, the main one): two words, sometimes three, as a paint chip's, from [colour] and
    [noun] by what the brush has, so they hint at it: a grey wash "Harbour Fog", "Wet Cardigan"; charcoal "Burnt
    Toast", "Chimney Sweep"; a smudge "Thumb Print", "Rainy Window"; "Rainy Harbour Fog".
  - **Deadpan** (about 30 in 100): one of 24 forms, each saying the part, the word for what makes it (pen, wash,
    smear, hatch, charcoal…; never "stick" for charcoal, the owner, 2026-10-29): "Polite Smear", "Regrettable Inky
    Hake", "Dusty Aunt Charcoal", "Inky Pen Junior", "Smear of Mild Regret", "Wash for Beginners", "Inky Pen
    Situation", "Budget Dusty Charcoal", "Allegedly Pen", "Pen with Opinions", "Wash on a Tuesday", "Second Inky Pen",
    "Inky Pen Society", "Pencil on Holiday", "Charcoal in Disguise", "Inky Pen Anyway", "Smear Revisited", "Pen and
    Sons", "Smidge of Pencil", "Cousin Wash"… About one in six of these has a made-up word, Edward Lear's way: the
    describer's stem or the part with a nonsense ending ("Wobblefuzz", "Inkery", "Smudgekins").
  - **A part and a name** (about 15 in 100): "Wash McGrath", "Pencil Delores", "Smear Bartholomew": ordinary,
    old-fashioned and Newfoundland names, never a real person's whole name.
  A new name repeats none of the library's ten latest in its form or a word (the part's and describer's aside), and
  none it has. -y and -ish words are welcome. Every new name wraps whole to two lines of the narrowest card; drawings
  stay adjective-produce ("dapper-fig"); saved names never change (older ones such as "Weeping Hake of Blooms" stay).
- **A new notebook's title** (the owner, 2026-10-29: "mostly deadpan"; the template in it "not necessary"): from
  `names/traits.txt`'s [title], Title Case, in fourteen forms ("Field Notes on Scones", "Letters to a Walrus" and): "Several Opinions About Soup", "A Brief Inventory of
  Lint", "Notes Toward a Better Spoon", "The Pamphlet of Mild Concerns", "Doodles for a Goose", "Mistakes Volume
  Three", "Probably Fine", "The Lukewarm Pamphlet", "Spoons I Have Known", "In Defence of Fog", "Advice from a
  Puffin", "Minutes of the Pigeon Committee"; about one in six with a made-up word ("The Flimwhistle Papers",
  "Doodles for a Pobblewick", "Wobblefuzz and Other Concerns"). Its day at the end, "26-10-28" (the cover's date line
  and the settings' "Made Oct 28, 2026" say it in full). Names may be whimsical; instructions, buttons and notes stay
  plain. Only letters, numbers, spaces and - (a file name: no apostrophes or commas, so "Mistakes Volume Three"),
  whole on every shelf tile; names given before never change (still "Oct 19 brisk-bean notebook", "Tome of the Damp
  Wanderer").
- **A long name wraps** (the owner: "maybe we extend the cards to accomodate"): two lines on Tools page and library
  cards (on a card too short for both, the caption's line gives way to a whole two-line name), in Brush Lab's bar (at
  16 sp) and in the label beside the column when a slot's brush changes; cut with … only past two lines, and on the one-line brush line.

**Parts and choices**
- **Parts**, in Brush Lab in the order a mark is made (the owner, 2026-11-01): **Control**, Tip, Medium, **Affects Page** (Smudge until 2026-11-01, Rework until 2026-11-02, the owner: what the brush does to the marks already there; **Affects** on a module too narrow for it, the Nomad's;
  the owner's name for it once it held Reprint; the smudge tool, Finger's Smudge and their notes keep Smudge), Drying,
  and **After** (Later until 2026-11-01: the mischief, what changes the page after the pen lifts; its line "fades ·
  spreads · ages", what its dozen choices do, "fading · mold · float" until they were more; its card on every brush
  since then, Off "Stays put."). No Paper card (Surface that morning): the paper is the notebook's and the page's
  **Surface** (the surface spec, 2026-11-01), and the brush's one dial for it, **Texture**, is a row of Medium's card
  (its line "ink · paint · pencil · texture · fling"). Never Life, Hand, Carry, Paper or Later on screen for a part.
- **Tip:** Round, Chisel, Flat Nib, Hake, Split Nib, Rake, Bristle, Stipple, Hatch, Filbert (MonoPaint's word for the
  rounded oval brush, the oil paint spec's; its noun "filbert"), Stamp (its nib cut from the page; noun "stamp"), Spiro
  (the spirograph toy's wheels; noun "spiro"), and the nibs (explore-nibs, 2026-11-01; never rolled): **Ruling Pen**
  (noun "ruling pen"; key `ruling`), **Twig** (noun "twig"), **Fan** (noun "fan brush"), **Brayer** (noun "brayer"; key
  `roller`, the gaps spec's Roller; never "roller" on screen). Hand Hatch was cut (2026-10-24): its key `handhatch` still reads, as
  Hatch, and is never shown. Brush Lab offers Stipple, Hatch, Stamp and Spiro (and, on After, Float, Mold, Fading,
  Lemon Juice) wherever rolls may pick them (Advanced's only until Basic went, 2026-10-29).

- **Medium:** Ink, Wash, Opaque, Charcoal, Graphite, Resist, Screentone (the owner's name, 2026-10-19; its key is `tone`;
  "Screen" / "Tone" where a Nomad swap tile breaks it), Crayon (wax; its card "Scratch" with Scratch on).
- **Affects Page:** Off ("Moves nothing.", "No smudge." until 2026-11-01), Lift (gentle to strong), Drag, Smear (clean to smeary), Clear Water (gentle to strong; its module says
  "Water", its Basic tool is **Water**, the Water brush until 2026-10-31, as Float's Clear water preset has the other name), Stay in Lines (its
  module says "Stay in"; key `stayin`), Filled Shape (its module says "Shape", its card under the blob "Closes and
  fills."; key `shape`; the owner, 2026-10-23: "draw a filled shape"; the Basic tool **Filled shape**, "fills outlines", until 2026-10-31, merged into **Fill in**; since then its chip shows
  only on a brush that holds it, as Hand Hatch never shows), **Scrape** (key `scrape`, branch explore-knife: a palette
  knife; "This brush" says "Pushes wet paint aside, scrapes damp paint pale."; its Basic tool **Palette knife**,
  "scrapes paint"; never "sgraffito" on screen, a word a newcomer wouldn't guess), **Reprint** (key `reprint`, branch
  explore-reprint, no dials: brushed over marks, it lays them again in its Print Shading and Paper; its card "Reprints
  marks."; "This brush" says "Reprints the marks under it.", or with no Print Shading "Reprints the marks under it as
  toner."; captions tag "reprint"; its Basic tools **Photocopier**, "photocopies", and **Newsprint**, "halftone dots",
  under Print. The Brayer's "reprints" is kin: it prints again what it picked up).
- **Surface** (the notebook's and a page's, 2026-11-01; once the brush's Paper choices): Smooth, Cold Press, Ribbed (key `laid`), Felt, Crumpled, Canvas; the photo papers (2026-10-22) Cotton (key
  `watercolour`: not "Watercolour", the style), Bumpy (key `rough`: not "Rough", Texture's end word), Unryu, Library,
  Kraft, Vintage, Craft, Fine (Vintage, Craft and Fine renamed by the owner, 2026-10-22). "This brush" says "on cotton paper", "on bumpy paper" and so on.
- **Drying:** Instant, Quick, Slow, **Wet** (MonoPaint's wet canvas, the wet paint
  spec, 2026-10-22; its key is `wetpaint`; never "Wet canvas" or "wetness" on screen). Only how long the mark stays
  wet (2026-11-01): Float, Mold, Fading and Lemon Juice are After's chips, no longer Drying's too.
- **After:** Off ("Stays put."), Float, Mold (the owner's name for it, 2026-10-18, spelled so; its key is `halo`),
  Fading, Lemon Juice (the owner's name, 2026-10-19; its key is `lemon`), and the physical media (the owner's four,
  2026-10-31): **Salt**, **Plastic Wrap** (key `wrap`; never "cling film" or "Saran"), **Feathering** (key `feather`),
  **Foxing** (key `fox`; an old book's spots); and print, crafts and ageing (2026-11-01): **Letterpress** (key
  `letterpress`), **Torn Paper** (key `torn`), **Cross-Stitch** (key `stitch`; the hyphen kept, "Cross-stitch" in a
  sentence), **Cracks** (key `cracks`; never "craquelure" on screen), **Tarnish** (key `tarnish`). The After card shows
  them grouped, not in key order: Off and the mischief, then paint media, print, age, craft, in pages of at most eight.
  Basic tools named for theirs (explore-presets, 2026-11-01): **Letterpress** (Print, "pressed type") and **Cracks**
  (Gouache, "ages, cracks"; **Cracking paint** since 2026-11-06, the owner: "maybe rename it cracking paint", key `cracks` kept), with Risograph's **Riso** (Print, "grainy print"), the Medium card's word for it.
  "This brush" says the drying, then After: "Dries at once. Fades away in
  20 s."
- **The eraser's choices** (long press on the Eraser, the owner, 2026-10-20): **Everything**, **Pen**, **Paint**, **Dry Media** (pencil, charcoal and
  crayon; the owner's name, replacing "Pencil, Charcoal"), the Eraser tile's mark while one is chosen (**Pe**, **Pa**, **Dr**), and the note "Erasing pen" / "Erasing paint" / "Erasing dry media" / "Erasing everything".
- **Control:** Steady, Press Wide, Press Dark, Press Both, Springy, Tilt Shade, Tilt Turn, Flick, Custom.

**Dials** (Title Case, rule 8):
- **Tip:** Size (every tip's overall size, key `weight`), Nib Width, Nib Angle, Taper In, Taper Out, Dry Brush (all of a
  tip's dryness), Wobble, Soft Edge; Hake's **Head Follows** and Hatch's **Lines Follow** (Stroke, Page); Split Nib's
  Tine Gap; Rake's Lines and **Line Spacing**; Bristle's Hairs, Splay, Clump and **Paint Load**; Stipple's Dot Size, Scatter,
  **Uneven Dots**, Build-Up (Off, Slow, Fast); Hatch's **Line Spacing**, Line Weight, **Line Angle**, Layers; Stamp's Size,
  Spacing (a share of its size, "12%"), **Turns** (On, Off; its key is Head Follows' `headTurn`) and **Ink Fade** (None,
  "22%", Strong); Spiro's Size, **Mode** (Line, Ring, Oval, Bar), **Curve** (Spirograph, Epi, Rose, Lissajous, Harmonograph, Coil),
  **Loops per cm**, the toy's **Ring Teeth** (96, 105), **Wheel** (its teeth, "40") and **Hole** ("80%"), **Petals**,
  **Ratio** (1:2, 2:3, 3:4, 3:5), **Phase** ("45°") and **Fade Turns**, each shown only for the curves that have it;
  the nibs' **Paint Load** (Ruling Pen's, Twig's and Brayer's ink, dry to loaded: Bristle's word, the same concept), Twig's
  Wobble (smooth to gnarled), Fan's Hairs, Splay (tight to splayed) and Dry Brush (Bristle's words), Brayer's **Pick Up
  Paint** (clean to reprints; Affects Page's word, the same concept: what it takes off the page, to print again a turn later);
  Hand Hatch's Line Spacing, Line Weight, Line Angle and Lines Follow (Hatch's), and its stroke's **Line Length**, **Wobble**
  (neat to loose) and **Taper Out** (blunt to a flick); every tip's **Flick**; Round's and Chisel's **Wears Down**
  (last, branch explore-knife; key `wear`: the tip grows a flat side as it draws). One term a concept (the design
  review, 2026-10-31; `PartModelChecks.oneTerm`): **Line Spacing** is how far apart lines laid side by side are (a
  Rake's, Hatch's; "Spacing" until then), a Stamp's **Spacing** how far apart its prints land along the stroke; Dry
  Brush is all of a tip's dryness (Round's to Bristle's), Paint Load how far a brush's paint lasts (Bristle's and the
  Affects Page's), Hand Hatch's Wobble and Taper Out a tip's own (never shown).
- **Medium:** Wet, Pen Line, Pooling (Wash's Dark Edge), **Spatter**, Bleed, Bloom, Backruns, Line Style (Centre,
  Outline, Squiggle), **Line Shift**, Breaks, Opacity, **Thick Paint**, **Messy Edge**, Density, Grade, **Dust When
  Tilted**, Erasable, **Blend Mode** (Darken, White, Behind, Invert; Opaque's first is Cover); **Fling** (last of every
  medium with Blend Mode, Spatter's kin); Crayon's **Wax** (chalky to waxy) and **Scratch** (Off, On); last of Ink's,
  Charcoal's and Graphite's, **Dissolves** (Off, On; key `soluble`, explore-media 2026-11-01: a water-soluble pencil or
  ink that isn't waterproof, which the Water brush melts into a wash; never "Water-Soluble", too long for the Nomad's
  row). Its sentence "Dissolves into a wash with water.", its caption tag "water melts it" ("dissolves" until 2026-11-02, the owner: say to use Water over it); the Water pencil's line "water over it". A Paint Load on a line (no body)
  says "Runs dry. Rub a grey to dip." and is tagged "runs dry" (the Dip nib), not "runs out".
- **Medium, print:** **Print Shading** (None, Halftone, Engraving, Dither, Grain, Rosette, Ben-Day, Crosshatch, Wavy Lines, Mezzotint, Diamond Dots, Stipple, Woodcut, Scribble, Hatching, Risograph, Photocopy, Soft Dither, Maze, Brick, Basketweave, Waves, Honeycomb, Rings, Pixels, Stitches; Risograph to Soft Dither named "Riso", "Photocopied", "Soft-dithered" on the Medium card, Waves "Wave", Rings "Ringed", Pixels "Pixelated" and Stitches "Stitched"; Waves, never Seigaiha, a word a newcomer wouldn't guess; Stitches, never "Cross-Stitch", After's choice) with its **Pattern Size**, Angle,
  **Double Print**, **Dot Swell** and **Patchy Ink**; Screentone's Pattern (Dots, Lines, Sand, Gradient, Crosshatch, Wavy Lines, Mezzotint, Stipple, Woodcut, Scribble, Hatching) and
  **Darkness**, Pattern Size and Angle.
- **Affects Page:** **Pick Up Paint**, **Lay Down Paint**, Own Paint, Soften (Smear's), **Keeps Paint**, **Paint Load** and
  **Starting Load** (Drag's and Smear's; Paint Load is Bristle's word too, the same concept: how far a brush's paint
  lasts), and last **Press to Push** (Smear's) and **Press to Lift** (Clear Water's; one dial, key `press`, the owner's
  two smudge tools, 2026-10-30: what a firm press does, the light touch being the brush's own). The Affects Page module's line: "lift · drag · smear · load". Stay in Lines' **Flood** (brush to fills; shown
  Brush, "4 mm", Fills; the prototype's Spread, an engine word on screen). Scrape's **Depth** (light to deep; its key is
  Pick Up Paint's `pick`).
- **Texture** (Paper's one dial, on Medium's card since 2026-11-01): smooth to rough.
- **Drying:** Wet Edge, Drying Time, **Flow While Wet**, **Runs Downhill**, Drips, **Pools Downhill**, Grainy, **Rock to
  Blend**; Wet's **Stays Wet** (short to long; the owner's "one Stays Wet dial", 2026-10-22) and Runs Downhill.
- **After:** Float's Floating Time, Ring Size (small to large) and **Drift Downhill**; Mold's **Mold Length** (short to
  long) and **Mold Growth** (its time is Slow's Drying Time); Fading's **Fades In** and Lemon Juice's **Dries In** (fast
  to slow). Salt's **Grains** (few to many) and **Dries In**; Plastic Wrap's **Crinkle** (fine to coarse) and **Dries
  In**; Feathering's **Thread Length** (short to long; never "Reach", an engine word) and **Spreads In**; Foxing's
  **Spots** (few to many) and **Ages In** (Dries In's dial, key `dryIn`, shown so for an old book). Letterpress's
  **Impression** (light to deep) and **Prints In** (Spreads In's dial); Torn Paper's **Torn Edge** (neat to ragged) and
  **Tears In** (Spreads In's); Cross-Stitch's **Stitch Size** (small to large, "8 px") and **Stitches In**; Cracks'
  **Cracks** (few to many) and **Ages In**; Tarnish's **Tarnish** (light to deep) and **Tarnishes In** (the last three
  Dries In's dial).
- **Control:** Response, Fast Strokes (Thinner, Thicker), Tilt Range (Normal, Wide), **Tilt Shading** (a nib line laid low: its side); its cells row + column, "Pressure
  Size", "Tilt Nib", "Page Tilt Dark"; the grid's rows Pressure, Tilt, Speed and Page Tilt; with a Spiro tip, **Pressure
  Sets**, **Speed Sets** and **Tilt Sets** (Size, Loops, Wheel, Hole, Petals, Nothing); **Grey Shift** (Off, Random,
  From a Set, Pattern), a Pattern's **Order** (In Order, Ping-Pong) and **Greys** (Toolbar, Any); **Gradient** (Off,
  Fade, To Grey, Cycle), a To Grey's **End Grey** (White, "33%", Black) and its **Length** ("10 cm").

Paint Load (the oil paint spec) says plainly what it does, and how to load it, in the smudge's sentence's place: "Runs
out of paint, then only smudges. Rub a grey to load." (the owner asked for it plainly: the one instruction in "This
brush"); captions tag "runs out" in the smudge's tag's place ("Oils, filbert, runs out"). The Basic tool is **Oil
paint**, "runs out". Keeps Paint (a dirty brush) adds "Keeps paint for the next stroke." after the smudge's sentence, and captions tag
"keeps paint"; the smudge chooser's button is **Clean Brush**. Clear Water is named by what it lays: "This brush" starts
"Clear water." (with the tip and paper as usual), its smudge "Lifts paint, lets it run.", and its Slow drying says what
the wet does when tilted ("Wet for 5.0 s, runs and pools when tilted."); captions tag "clear water". On Float it is
Float's clear water and says so. Smear's Soften is MonoPaint's blending stump: at .5 and over "This brush" says "Softens.", a Rake's or Split nib's
"Combs.", captions tag "stump" or "comb" in place of "smear". The smudge tool's two Basic tools (the owner, 2026-10-30) are named for what they are, **Blend**
and **Water**, their one-line descriptions what a firm press does, in the dial's words: "press to push", "press to
lift" (the light touch is the name's); "This brush" says both ends ("A light touch softens marks, and pressing harder
pushes them along."), and their captions tag "stump" and "clear water" as before. The third, **Blur** (the owner,
2026-11-01), has no press: its description says what it reaches, "any mark", its own words "A soft blur for any mark, pen
lines too.", its caption tags "stump". A stipple or hatching is named by what it lays: "Ink
stipple.", "Sponged wash.", "Ink hatching.", "Pencil hatching."; Build-Up says "Builds up where it lingers."; captions
tag "stipple" or "hatch" after the style. Hand Hatch: "Ink hatched by hand." then "Crosses where gone over or held."
(pressure crosses it too, as Hatch's, unsaid); captions tag "hand hatch". Thick Paint names Opaque by it past .3: the Medium card "Thick paint", "This
brush" "Thick paint." (with "raised ridges"), the caption tag "thick paint". Blend Mode names the medium by what it
lays: the Medium card says "White ink", "White wash", "White paint", "Chalk", "White pencil"; "Ink behind", "Wash
behind", "Paint behind"; "Invert ink"; "This brush" says "White ink line.", "White chalk.", then "Goes behind marks."
or "Inverts what's under it."; captions tag "white", "behind" or "invert" first after the style. Resist is named by
what it is: the Medium card says "Masking fluid" or "Wax resist" (Erasable under .5), "This brush" "Masking fluid."
then "Nothing takes on it until rubbed off." (wax: "Nothing takes on it."), with no Erasable word; captions tag
"resist" or "wax" first after the style ("Watercolour, resist"). Mold says how long it grows: the After card "Mold
6.6 s", the swap tile "mold 6.6 s", "This brush" "Veins bridge nearby marks for 6.6 s." in the drying's place; captions
tag "mold" after the style, after resist, Blend Mode and a stipple or hatch tag ("Ink wash, mold"). **Print Shading**
(the owner's name, 2026-10-19, for the dial keyed `screen`: one constant, `PrintScreen.LABEL`; its row in Brush Lab's
open Medium card says **Shading**, as PRINT SHADING would take two lines there) names the medium by its print: the
Medium card "Halftone ink", "Engraved ink", "Dithered wash", "Grained pencil", "Rosette ink", "Ben-Day ink", "Wavy-line ink", "Mezzotint ink", "Stippled ink", "Woodcut ink", "Scribbled ink", "Hatched ink" (the print alone where the two pass the
module's line: "Halftone" for charcoal); "This brush" "Halftone ink line.", then the newsprint dials' two strongest,
"Doubled, dots swell." ("patchy ink"); captions tag "halftone", "engraved", "dithered", "grained", "rosette", "ben-day", "crosshatch", "wavy-line", "mezzotint", "diamond-dot", "stippled", "woodcut", "scribbled" or "hatched" after Blend Mode's
("Marker, halftone"). Screentone's Pattern and Darkness (light to dark; key `ttone`) name it: the card "Dot
screentone", "Line screentone", "Sand screentone" ("Screentone" alone where the pattern's name would pass the module's
line: the gradient's); its swap tile "Screentone" over the pattern ("dots"); "This brush" "Dot screentone." then
"Scrapes off in streaks.", with no Erasable or smudge word; captions tag "dot screentone" first after the style. The
Control grid's columns are Size, Dark and Nib (Dots in place of Dark for a brush that prints dots: Print Shading or
Screentone); its levels off, some, full and max.

A Stamp is named by what it lays, as a stipple is: "Ink stamp.", "Wash stamp.", then "Stamps a shape cut from the
page." and, with Ink Fade on, "Fades as it prints."; captions tag "stamp" after the style. Its action is **Make Stamp** (Cut Stamp until 2026-11-02, the owner unsure how to use it)
(Brush Lab's bar, and This Brush's header on the Tools page), shown only for a Stamp; the drawing screen then says "Draw
round the shape to cut" beside the slot, an empty lasso "Nothing to cut" (on Brush Lab's button, for a moment), and a cut
"Stamp cut". The Basic tool **Stamp** ("prints a shape", 2026-10-28; the bar's **stamp tool** until then) is one in a slot;
the slot's long press offers **Stamp ▾** (a Stamp only), its menu beside the slot: its card, **Make Stamp**, then **Turns** (On, Off), **Spacing** ("12%", "50%", "100%") and **Ink Fade** (None, "22%",
Strong), as the dials say them. The Lasso's bar has **Make Stamp** too (2026-11-02): the piece's ink is the Stamp's nib
(the Stamp in use, else the bar's first, else the slot in use becomes one), that slot then in use, "Stamp cut" beside it.
Until the first stamp is printed, a Stamp in use keeps a hint in the foot's corner, the one hint rule 7 allows (the
owner's, 2026-11-02): "Lasso a shape, then Make Stamp" while it has no nib of its own, then "Draw to print the stamp".

Stay in Lines says how far it floods, in the smudge's place: "Floods inside ink lines." (Flood off: "Stays inside ink
lines."; Fills: "Fills the shape it starts in."); captions tag "stay in lines".
Filled Shape says what it does at the lift, in the smudge's place: "Fills the shape it outlines." ("When you lift, it
closes the outline and fills it in."); captions tag "filled shape".
A Crayon is named for itself, whatever its tip: "Wax crayon." then "Builds up, resists washes." (Wax off: "Builds
up."); with Scratch on, "Crayon scratch." then "Scrapes back to white." Its card says "Crayon" or "Scratch", captions tag
"crayon" or "scratch", and it has no Erasable word and no smudge words (it smears nothing).
A Spiro is named by what it lays, as a stamp is: "Ink spiro.", "Wash spiro.", then where its loops go: "Loops along the
line.", "Loops round a ring pulled out from the start.", "Loops round an oval drawn first." or "Loops round a bar drawn
first."; captions tag "spiro" after the style. The Tip card says its curve, and "ring", "oval" or "bar" after it:
"Spirograph", "Rose ring", "Spirograph bar". Its Control words are the drives that change its curve: "pressure sets
size, tilt sets hole"; two setting one thing, "pressure and tilt set size".

The nibs (explore-nibs) are named by their tip, as a chisel is: "Ink line, ruling pen.", "Ink line, twig.", "Ink line, fan
brush.", "Ink line, brayer.", then their own trick right after: "Runs dry, blots where it slows.", "Blots, then scratches
dry.", "Hairs part, more touch when pressed.", "Reprints what it rolls over." (Pick Up Paint off: "Rolls an even
band."). The Tip card says the name ("Ruling Pen", "Brayer"). Their Basic tools: **Ruling pen** "dries, blots", **Twig
pen** "blots, dries" (Dip pen), **Fan brush** "hair lines" (Dry brush), **Brayer** "reprints" (Print).

Grey Shift (mischief-lab-2.html's Shifting swatches) says how the grey shifts, right after what the brush is (after a
Stamp's, a Crayon's or a Spiro's own sentence): "Greys shift each stroke at random.", "Greys shift each stroke,
shuffled.", "Greys shift each stroke, in order.", "Greys shift each stroke, there and back." Its label is one line in
the Control card (not "Shifting Greys", two); its set is a row of swatches, a swatch left out read "47% grey, left out";
Brush Lab's note after a tap "3 of 4 greys".

Gradient says how the grey changes along the stroke and over how far, right after Grey Shift's sentence: "Fades to
paper over 10 cm.", "Shifts to 33% grey over 10 cm." ("Shifts to black over 10 cm."; an End Grey of White is a fade),
"Cycles through its greys every 10 cm." Fade and Cycle are the effect's own words; "gradient" is the dial's name only.

Flick and Fling (the mischief modules spec, Stage 2) say plainly what they do, right after what the brush is: "Flicks
on a quick lift.", "Flings drops on a hard stop.", or both in one sentence ("Flicks on a quick lift, flings drops on a
hard stop."). The Medium card adds ", fling" where the module's line has room ("Ink, fling"); the Tip card has no room for
Flick, so the Tip's module says it holds it ("shape · taper · angle · flick"; the Medium's "… · fling"). Open: Control's
preset Flick (speed thins a quick stroke) and Dip pen's Flick nib (spatters) share the word.

Wet (the wet paint spec) says plainly what it does, in the drying's place: "Stays wet until dried." and, with Runs
Downhill, "Stays wet until dried, runs when tilted." Its swap tile's note "stays wet"; captions tag "wet" after the style,
as Mold's ("Gouache, wet"). Dry, as a verb, is the droplet's: "Wet. Tap to dry." The Basic tools' one-line descriptions
say what each does in a painter's words: "mixes wet", "runs downhill", "flat, wet", "broad, wet", "barely mixes",
"lifts paint" (Gouache water); "drags, mixes", "runs out" (Loaded oils), "flat, drags", "broad, drags", "tilt to widen",
"thin, mixes"; Brush pen's Wet brush "ink in water" (the wet brush spec). Wet on Ink or Wash (the wet brush pen's ink)
says what its brush holds and how to dip it, in the smudge's place: "Holds ink between strokes. Tap a grey to dip." (as
Paint Load's "Rub a grey to load."). The launch extra Draw once is a test switch, never on screen. A wet watercolour (Wet on a
Watercolor Wash, the wet paint spec section 9) adds what wet into wet does: "Stays wet until dried. Merges while wet,
blooms when damp." ("blooms when damp" only with Bloom or Backruns); its Basic tools keep their old one-line
descriptions ("even, pale", "blooms", "pale backruns", "grainy blooms", "slow-drying", "runs downhill", "rock to blend",
"granulates").

Fading and Lemon Juice (the mischief modules spec, Stage 3) say plainly what they do, in the drying's place: "Fades
away in 20 s.", "Darkens in as it dries." The After card "Fading 20 s" and "Lemon juice" (its time would pass the
module's line); the swap tile's note "fades 20 s", "darkens 4.0 s"; captions tag "fading" and "lemon juice" after the
style, as Mold's ("Anything goes, fading"). A rolled one's name says fade or juice ("fade-plum"; lemon is a produce word).

After's media (2026-10-31) say what the technique does, in a painter's words, in the same place: "Salt opens pale
starbursts as it dries.", "Plastic wrap leaves crinkled veins as it dries.", "Feathers into the paper's fibres.",
"Foxes like an old book." The After card says only the name ("Salt", "Plastic wrap", "Feathering", "Foxing"); a swap
tile names Feathering "Feathers" (After's tiles are too narrow for the whole word); the swap tile's note "salt
30 s", "crinkles 30 s", "wicks 4 s", "ages 3:00"; captions tag the name after the style
("Watercolour, salt"). Never rolled, so never in a rolled name. The print, crafts and ageing (2026-11-01) the same way:
"Prints like letterpress, darker at the edges.", "Turns into torn paper, glued down.", "Turns into cross-stitch on a
fabric grid.", "Cracks finely as it ages.", "Tarnishes darker, like silverpoint."; their notes "prints 2 s", "tears 3 s",
"stitches 30 s", "cracks 2:00", "tarnishes 5:00".

**Control words**, the same on the module, the swap tile and in "This brush":
- Pressure: pressure widens / darkens / opens tines / spreads lines / widens nib.
- Tilt: tilt widens (broadens with a wide Tilt Range) / lightens / turns nib.
- Speed: speed thins (thickens) / lightens / turns nib.
- Page tilt: downhill widens, uphill lightens, downhill opens nib.
- Merge them: "tilt widens and lightens"; "pressure and tilt widen".
- A brush that prints dots (Print Shading, Screentone) says what its darkness does to them: "pressure grows dots",
  "tilt shrinks dots", "speed shrinks dots", "uphill shrinks dots"; "tilt and speed shrink dots".

## Brush words

The owner, 2026-10-22: "right now they are too curt. explain what makes the brush unique, what to expect", "i do like
complete sentences, but make them human sounding?", "don't use semicolons". Shown in Brush Lab's THIS BRUSH box and the
Tools page's This Brush (whole sentences, as many as fit), and said by the screen reader on brush cards and the brush
line. The brush line itself keeps `Name · description, card words`.

- **Every brush** opens with one **written sentence** (`BrushVoice.opener`; the owner, 2026-10-22: "i actually prefer
  the algorithmic ones written in a human voice"): what it is and the one or two things that stand out most about it,
  at most 70 characters, in one of four shapes its facts pick ("A stick of vine charcoal that leaves dust in its
  wake.", "Here's gouache with a hake that stays wet until you dry it.", "This is a wet wash, blooming as it dries.",
  "Meet an HB pencil on cold-press paper, catching the paper's grain."). What stands out comes from
  `BrushVoice.OPENERS`, most telling first.
- **The special tools** (the owner: "except for the specific tools ones... like damp tissue or wax resist", "or
  sumi") open with their own **hand-written** words instead, from `app/src/main/assets/brushes/personalities.txt` (a
  line a tool, `Name: words`; the owner can add a line to override any Basic tool): Damp tissue, Wax resist, Masking
  fluid, Sumi, Clear water, Comb, the stumps and the like. For example:
  - Damp tissue: "Blot paint back up, like dabbing it with a damp tissue."
  - Sumi: "Japanese marbling. Ink floats on water and spreads into rings."
- **Every brush** then says **how it behaves**, made from its facts by `BrushVoice` (the web lab's `voice.js` has the
  same rules, and says every brush the same, checked): complete sentences, what matters most first (its own trick,
  then wet and the smudge, then pressure, tilt and speed, then its marks, then drying at once or the eraser), never
  again what the opener said. For example:
  - "It stays wet until you tap the drop to dry it. Tilt the page and it runs downhill."
  - "Pressing harder makes the dots bigger. It dries as soon as you lift the pen."
  - "Pressing harder makes it wider, and moving faster makes it thinner. It tapers to a fine point."
- **Rules:** every sentence complete, starting with a capital and ending with a full stop. No semicolons (checked), no
  em dashes, no lists of three. Two things "it" does share a sentence ("It has a soft, speckled edge and catches the
  paper's grain."), never more. "You" is fine here. Erasable goes unsaid when a brush erases clean; "The eraser can't
  touch it." or "The eraser only lightens it." when it doesn't. Times in whole seconds ("for about 6 seconds").
- A new fact in `ModularWords.plainWords` needs its words in `BrushVoice.RULES` and `voice.js` (VoiceChecks finds any
  rolled brush's fact without words).

## Templates

- **The facts** (`ModularWords.plainWords`, checked, never shown as they are; **Brush words** says them):
  `{Medium}{, tip}{ on paper}.` then `{Trait}, {trait}.` then the smudge (`Lifts paint.`,
  `Drags wet paint.`, `Smears.`, `Softens.`, `Combs.`, `Lifts paint, lets it run.`; with Press, `Softens, pushes when
  pressed.`, `Re-wets paint, lifts it when pressed.`; then `Keeps paint for the next stroke.`
  when it does), then the drying when it isn't Quick (`Dries at once.`, `Wet for 5.6 s, drips and runs
  when tilted.`, `Floats 8 s. Taps drop rings.`, `Veins bridge nearby marks for 6.6 s.`), then `{Control words}.` After the smudge, the
  Erasable dial when it isn't 1 (every medium's own since 2026-10-22): `Permanent.`, `Part erasable.` (caption tags the
  same, in lower case). For example: "Line and wash on felt paper.
  Pools when slow. Speed thins." The marks say the dials' words: "Thick paint.", "picks up the texture", "bleeds",
  "line shifted".
- **Caption:** the style, then what is unusual for that style, in display words: "Charcoal, smear", "Pencil, 4B,
  ribbed", "Watercolour, slow".
- **Variation:** the swapped choice in sentence case ("Split nib", "Instant drying"), then "more X" / "less X" in
