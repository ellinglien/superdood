# Read Handwriting: search the writing on Superdood's pages

Approved by the owner 2026-11-09 ("looks perfect"). The ask: "adding text transcription to superdood... so i can search
through pages for written text"; "an app systemwide feature that users can enable optionally"; and "a text recognition
'affects page' thing that would convert written text to editable text? but disabled by default and only enabled if the
user confirms it's ok".

## Decisions

| Question | Answer |
|---|---|
| Which reader | Google's on-device handwriting reader, ML Kit Digital Ink (the owner: "1 is maybe the most promising"). |
| On or off | One System Settings row, **Read Handwriting**, Off by default; On only once its first-time words are agreed to. |
| Online model | Not now; parked under someday maybe ("read this page again online"). |
| Writing into text | Both: **Make Text** in the Lasso menu first, then a **Typeset** Later choice. |
| Where search is | On the shelf, searching every notebook. |

## What the probe showed (2026-11-09)

A separate test app (`~/Claudecode/Supernote/inkprobe`, local git) on the Manta, which has no Google services:

- The English model downloaded in 26 s and loads again in about 0.1 s.
- The owner's handwriting, two pages and seven lines, about 0.25 s a line:

  | Written | Read |
  |---|---|
  | My name is Elling. | My name is Elling |
  | I have ten potatoes. | I have ton potatoes. |
  | Welcome to the / party. | Welcome to the / party. |
  | When I was a little kid I wanted | When I was a the bed I would (2nd guess: "kid") |
  | to be an astronaut. | to be an astronaut. |
  | When is breakfast? | When is breakfast? |

- What made the difference: reading line by line, each line with its own box as the writing area, and only the
  pen's strokes. A whole page read as one block gave "Hellman". Grouping lines by height alone split a line whose
  small letters were 31 px tall while the line wandered more.

## Read Handwriting (System Settings)

A row like the Fridge's, which already asks first:

- **Off** (the default): nothing is read, and Search and Make Text are not shown. Typeset brushes draw plain ink.
- **Turning it On** shows the first-time words once, then downloads the reader:
  - Handwriting is read on the tablet, so it can be searched.
  - Google's reader is downloaded once, about 20 MB.
  - The writing never leaves the tablet.
  - Google gets usage stats: device and app details, timings and errors.
- **Turning it Off** deletes every transcript and the downloaded reader.
- **Language:** English. More languages later, as a few chips on the row if they are wanted.

Exact words go through `docs/TEXT-STYLE.md`. Its rules on notes and buttons apply, and the first-time words are a
dialog's, kept short.

### What Google gets (developers.google.com/ml-kit/android-data-disclosure and /terms, read 2026-11-09)

- **Stays on the tablet:** the ink and the text read from it. Google: processing "fully happens on-device" and "ML
  Kit does not send that data and the resultant outputs to Google servers".
- **Sent over HTTPS, for diagnostics and usage analytics:**
  - device information and app information
  - per-installation identifiers
  - performance (such as latency) and API configuration
  - feature input and output size, and feature version
  - event types and error codes
  - for Digital Ink, the configured languages
- **No opt-out** is documented. The terms make the app responsible for telling its users.
- **Downloads:** about 20 MB a language, from Google, and the reader may check for updated models from time to time.
- **Usage stats go out through Google's data-transport library.** An upload already queued before Read Handwriting is
  switched Off can still go out once afterwards.
- **The model download is a WorkManager job** (ML Kit's), which `HandwritingReader.delete()` cancels.
- **WorkManager starts at app start** (pulled in by ML Kit), local only.
- **Permissions the app gains from it:** ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE.

## What is read

**Strokes.** Each page's stroke log (`StrokeLog`, the page file's RPLY section) already holds every brush stroke's
samples: position, pressure and ms since the sample before. No new storage is needed for the ink.

- **Moved strokes:** writing moved with the Lasso is read where it lies now. A stroke inside a `LIFT`'s loop takes
  its `PLACE`'s pose: centre, scale and angle.
- **Erased strokes:** when most of a stroke's points now land on blank paper on the page, it is not read.
- **Washes:** brush strokes wider than writing are not read. The cut-off is fixed, set from real pages.
- **Not read:** finger strokes, smudges, shapes, pictures and merges. A page's text boxes are already text;
  searching them as they are is for a later step (step 1 searches handwriting only).

**Lines.** The rule that worked on the owner's pages:

1. A **letter** is the median height of the page's strokes (strokes of more than two points).
2. Follow the order of writing. A stroke starts a new piece when it begins more than 4 letters left of the stroke
   before's right edge, or its middle (the median of its points' heights) is more than 2 letters above or below
   that stroke's.
3. A piece joins an earlier line whose middle is within a letter of its own. This catches dots and crossings added
   later, and a line interrupted by other marks.
4. Each line is read on its own, with its box as the writing area.

**Kept per line:** the top three guesses and the line's box on the page.

**When**
- **After a page is left** (turned away from, or the app is put away): in the background, only if its stroke log
  changed since it was last read.
- **Turning Read Handwriting On:** every page of every notebook is read in the background, newest notebooks first.
- **While pages are still unread,** search says how many.

## The transcripts

- **Where:** one small file per notebook in the app's own storage, beside the notebooks but never inside a notebook
  file. Turning Read Handwriting Off deletes them cleanly, and a notebook copied elsewhere carries no transcript.
- **What:** for each page, its identity, a mark of its stroke log's state (to tell when it changed), and its lines:
  each line's box and its three guesses.
- **When a notebook goes:** deleting a notebook deletes its transcript, and deleting a page drops that page's lines.

## Search on the shelf

- **Search** sits at the right of the shelf's bar (New notebook is a tile, not a button), only while Read Handwriting is On. It is the Tools page library's search field
  and words: **Search**, its × **Close search**, and **Nothing found**. Typing uses the tablet's keyboard.
- **Matching:** ignores capitals and accents, finds the words anywhere in a line, and checks all three guesses.
- **Results:** one row a matching line, with the notebook's name, the page number and the line's text (the guess
  that matched), newest notebooks first.
- **A tap on a result** opens that page with the matching lines boxed. The boxes go at the next tap.
- **Unread pages:** a note under the results says how many pages are still to be read.

## Make Text (Lasso menu)

- **The reverse of Make Ink:** lassoed writing becomes a text box in its place. Each written line is a line of the
  box, at a text size matched to the letter height.
- **Undo:** one Undo step brings the ink back.
- **Read Handwriting Off:** the row is not shown.
- **Nothing readable** in the lasso: "Nothing found", nothing changes.

## Typeset (a Later choice; after Make Text)

- **What it does:** a brush with Typeset in its Later part turns its writing into typed text a couple of seconds
  after the pen stops, the way Fading ink fades. The ink written since the last pause becomes one text box in its
  place, as Make Text makes it. Undo brings the ink back.
- **Read Handwriting Off:** it draws plain ink, and its "This brush" words say it needs Read Handwriting.
- **Share codes:** the key is new. An older version reads it as its Later Off, the way earlier Later choices arrived.

## Inside the app

**Android, the reader alone.** One small class owns ML Kit: download, delete, and read a line (strokes in, three
guesses out). Nothing else knows ML Kit.

**Pure Java, checked on the desktop with generated strokes** (the owner's pages stay out of the repo):
- the strokes from a page's log, Lasso moves and erasing applied
- the line grouping
- the transcript file, written and read
- search matching

**The rest:** the System Settings row, the reading in the background, Search on the shelf, Make Text and Typeset
use existing pieces where they exist:
- `SystemSettings` and the Fridge's first-time words for the row
- the Tools page library's search field
- `TextBoxes` (Make Ink's box) for Make Text
- `PartChoice`'s Later list and `FadingInk`'s timing for Typeset

**Engine boundary.** The engine stays pure and separate (`docs/INTEGRATION.md`). Reading is app code, not engine
code.

## Costs and risks

- **App size:** ML Kit adds about 20 MB. Keeping only the Manta's processor type (arm64) cuts that.
- **Open source:** a closed-source part from Google. Superdood's MIT license allows it, and the README and About say so.
- **Accuracy:** only two of the owner's pages so far. Messier pages, other hands, and drawings mixed with words are
  untried. Search checking three guesses a line makes up for some of it.
- **Battery and time:** reading a whole library takes a while on first switch-on. It runs in the background and only
  while the app is open.

## Build order

1. Read Handwriting, the transcripts and Search. Check it on the owner's real pages before going on.
2. Make Text.
3. Typeset.

Parked: an online model, to read a page again when the tablet's reading falls short.

## Checks

- **Desktop:** the pure parts above, with generated strokes: two lines with a dot added later, a tall stroke across
  lines, a moved piece, an erased word, a wash.
- **Manta:** the reader downloads and reads lines, the transcripts follow page edits, and Search finds a word on a
  page in another notebook. Read Handwriting Off deletes everything. Tested with the .bench app or scratch notebooks,
  never the owner's notebook.
