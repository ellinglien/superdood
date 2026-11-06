# Release pare-down — plan (draft for the owner, 2026-11-05)

Goal: a focused Superdood for the open-source release (github.com/ellinglien/superdood): "a sketchbook that feels like
paper and occasionally misbehaves". Nothing anyone made stops working; fewer things are in front of you.

Source: the owner's marks in the inventory (a private page), 2026-11-05, plus
"aside from the ones in the main kit (Rob boss and Another pencil is in there)".

## 0. Safety first

- Tag explore-live `pre-pare-down` before any change, so every cut thing is one checkout away.
- Old notebooks, toolbar slots and share codes keep working: a brush is its own code, so a dropped preset still draws
  wherever it is already used. Nothing is migrated away.

## 1. Brushes: about 150 presets to about 40

Kept (the Tools page / tool picker list, in this order of families):

- Main kit (ToolSlots.FIRST, the bar's first-launch brushes): Another pencil, Steady line, Verve felt, Fude fountain,
  Ink wash, Watercolour washy, Rob boss, Fill in, Ben-Day brush, High lighter.
- Pens: Steady line, Cheap ballpoint, Hatch pen, Stipple pen, Fountain pen, Fude pen, Split nib, Ink wash brush.
- Markers: Ms. Sketch, Highlighter, Fill in, Filled shape.
- Pencil: Another pencil.
- Charcoal and pastels: Soft charcoal, Blend, Blur, Soft pastel, Wax crayon.
- Wet: Soft wash, Slow bloom, Water brush, Line and wash, Flat wash, Opaque round, Cracks, Wet oils, Dry round, Big sky.
- Print: Gradient tone, Ben-Day brush, Rosette ink, Halftone brush, Newsprint, Riso, Photocopier.

The rest (about 110, effect brushes included) leave the in-app list and live in **More Brushes**: Brush Studio's
library at superdood.org/studio, where any of them can be sent to the tablet. (Option: a "More Brushes" shelf in the
app instead — owner to choose, see questions.)

Mechanics: ToolPresets keeps every row (codes, pictures, tests rely on them) with a `shown` flag; the Tools page, tool
picker and search list only shown ones; LibraryCleanup / Recent untouched. Kits (assets/brushes/kits.txt) that name a
dropped brush: see questions.

## 2. Features

- **Cut (out of the release; code stays in git at `pre-pare-down`)**: Daily prompt; Notebooks in the browser (/app).
- **Hide (still there, one step back)**:
  - Brush Lab: reached from a slot's long press ("Edit Brush"), not from the main menu.
  - Dirty brushes, mischief effects (Later/After), masking fluid and wax resist: stay as brush abilities; they simply
    aren't in the kept presets, so you meet them through Brush Studio or Brush Lab.
  - Community library: inside Brush Studio only.
  - Page colour, see-through pages: rows inside Page Settings only (not the main menu).
  - Magnetic Poetry, Replay, Spirograph, Shapes, Text: one Play / "More" menu (the sparkle button), not on the column.
- **Keep (front and centre)**: eraser, smudge, Lasso, Dropper, Fill, Undo/Redo, Vector Ink, sharp zoom, wet paint,
  screentone and print, paper, notebooks, Page Settings, templates, covers, timed pages, rules, mirrors, Zen Mode,
  toolbar slots, System Settings, edge strip, hand, Export Page, Export Now, Send, Draw Together, Import, Brush Studio,
  superdood.org.
- Unmarked: brush share codes (proposal: keep, inside Brush Studio and a slot's long press).

## 3. Then the drawings (owner's idea)

Each kept tool gets a hand-drawn picture by the owner (a notebook template with one labelled box per tool, drawn on
the Manta, cut out and wired in); brushes people make themselves show their own stroke picture (already rendered).

## 4. Order of work

1. Tag; brushes list (shown flag, Tools page, picker) + tests.
2. Hide moves (menus) + tests; cut the two features.
3. Owner tries it on the Manta; adjust.
4. Public release clean-up (scribulor-public-audit.md), README with docs/images/superdood-manta-screen.png, push to
   ellinglien/superdood.
5. Tool drawings, when the owner has drawn them.

## Questions for the owner

1. More Brushes: only in Brush Studio on the web, or also a shelf in the app?
2. Kits (Marbling, Sketch and Ink, Watercolour with Masking, Colouring In, New Pens and Pencils, New Charcoal and
   Print, New Print and Age, Default Tools): keep kits at all? If yes, which; a kit's brushes stay available.
3. HB pencil: drop it (only Another pencil kept)?
4. "High lighter" (kit) and "Highlighter" (preset): one of them.

## Answers (owner, 2026-11-05)

1. More Brushes: a **shelf in the app** ("i think a shelf in the app").
2. Kits: remove all but **Default Tools**; maybe build at least one more kit later from the kept tools.
3. HB pencil: drop it ("another pencil is tuned how i like").
4. Highlighter: just one (the kit's "High lighter" and the preset are the same code).
