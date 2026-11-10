# Second Ink: one colour in the copies

The owner, 2026-11-09: "in my paper journal i have a set of tools so it's mostly black ink lines (fine liner and
fountain) and washes of grey - shades of one type of ink) and shades of a single blue ink diluted. we cant use color but
i'm wondering if we can in the types of mediums, choose one other shade for the exports and saves."

## Decisions

| Question | Answer |
|---|---|
| What makes a mark the second ink | **The brush.** Everything a second-ink brush draws is second ink. |
| How a brush becomes second ink | **A switch in Brush Lab**, "Second Ink". It's part of the brush, saved with it and carried in its share code. Brush Studio on the web gets the same switch. |
| Where the ink's colour is chosen | **Per notebook** (the owner, after first choosing one for the app: "actually by notebook, pick the shade per notebook"). A row in Notebook Settings, "Second Ink", with a few inks: Blue (the default), Sepia, Red, Green, Violet. A journal can be blue and a sketchbook sepia. |
| What shows it | **Only the copies.** The tablet stays black, white and grey, as always; PDF and PNG exports (Export Now, Export Page) and the Fridge show the second ink in colour. |

## How it looks

- **On the tablet:** nothing changes. A second-ink wash draws in greys, as any brush does.
- **In a copy:**
  - Second-ink marks take the second ink's colour at the same depth: a pale grey becomes a pale blue, black becomes the full ink.
  - Where marks overlap, the inks mix, like ink over ink. Black over blue stays black; a grey wash over a blue one gives a darker blue-grey.
  - Wet washes of the two inks bleed into each other, on the tablet (in greys) and in the copy (in colour).
  - Paper stays paper.
- **Where it already exists:** an older page, or a brush without the switch, exports exactly as today.

## The engine

The owner, on the first draft (which kept second ink on a plane of its own, so the two inks never bled): "the inks
should blend ideally but plan looks good". So the inks share their planes, and each pixel knows how much of its ink is
the second.

- **Planes today:** each layer pair keeps each kind of mark on a plane of its own (pen, wash, dry/pencil, opaque paint;
  `ToneDocument.PLANE_*`). A plane can already keep more than tones at each pixel: Erasable's share and Screen's print
  tone (`Layer.share`, `Layer.print`), each only where a brush that needs it has drawn.
- **The ink share:** one more of those, `Layer.ink`: at each pixel, how much of the ink there is second ink, 0 (none) to
  255 (all). A plane has it only once a second-ink brush has drawn on it.
- **Laying:** a stroke that darkens a pixel mixes its own ink in by the darkness it adds. Second ink over grey of the
  same depth leaves the pixel half and half; a grey wash over a blue one pulls it toward grey as it darkens it.
- **Wet paint:** both inks are one wash on the wash plane, so they bloom, run and bleed into each other as any two washes
  do. Ink moves with the paint: a pixel that moving paint darkens takes the ink of the paint around it.
  - This follows the tones the wet paint lays, not each grain of pigment, so it is close, not exact.
  - While a stroke is being drawn, the paint it moves counts as its own ink.
- **On the tablet:** nothing reads the ink share, so the screen's pixels are the same as today.
- **Erasing, the Lasso, Undo and Replay:** the ink share goes with the pixels, as Erasable's share and the print tone do.
  What an eraser leaves keeps its ink.
- **The cost:**
  - **Memory and size:** a plane with second ink carries one more byte a pixel, in memory and in the file. Pages
    without any stay as they are.
  - **One mix a pixel:** where the two inks overlap on one plane they are one mixed ink. An eraser can't take the blue
    off the grey beneath it. Inks on different planes (a black pen line over a blue wash) stay apart.
- **Copies:** they composite as the screen does, in colour: a pixel's tone shows between black and the notebook's ink
  by its ink share (the ink chosen in the notebook's options, `BookOptions`, as its template and page colour are). PDF
  pages and PNGs with second ink become RGB; a page without any exports as today.

## The brush

- **The switch:** "Second Ink" sits in Brush Lab with the brush's other settings, On or Off.
- **The share code:** the switch is a new field.
  - An older version reads a second-ink brush as a plain one, with the "Newer version" note, as earlier additions did (`docs/BRUSH-CODE.md`).
  - Brush Studio reads and writes the field, and shows the switch.

## Not in this step

- Draw Together's browser view and the live page in colour.
- More than one extra ink.
- Choosing an ink per page.
- Tinting the paper.

## Checks

- **Desktop:**
  - A second-ink stroke sets the ink share where it draws; a first-ink stroke over it lowers it by the darkness it adds.
  - A wet second-ink wash beside a wet first-ink one: the ink share grades between them where the paint has moved.
  - The screen composite is the same bytes with and without ink shares; the copy composite colours it.
  - Erase, Undo, Lasso and Replay keep the ink share with its pixels.
  - The share code round-trips; an older reader sees a plain brush.
  - A page without second ink exports byte for byte as before.
- **Manta:** draw with a second-ink wash next to black lines and grey washes, Export Now, and open the PDF on a colour screen.
