# Hold to Snap: overnight exploration (branch `explore-snap`)

The module gaps spec's first (`2026-10-31-module-gaps.md`, top-10 item 1). Built and checked on the desktop only: no
Manta, nothing installed. Everything here is a test branch for the owner to try by hand.

## What it does

Draw a rough line, arc, circle or oval, box, triangle or polygon, or an open run of straight lines, with any slot's
brush. Hold the pen still at the end for 550 ms, pen still down: the stroke is replaced by the clean shape, drawn by the
same brush along the clean path. Each rough sample's pressure, tilt and time go to the point the same share of the way
along, so the taper, the speed and the grain come with it. Lift as usual.

- **Hold time 550 ms** (`HoldSnap.HOLD_MS`): past Android's long press (400–500), far past Fling's stop (45 ms) and
  Float's drop (150), so neither of those holds snaps. "Still" is Fling's 4 glass px.
- **Recognition** (`HoldSnap.recognize`, pure Java): the path resampled to 128 points; a line if no point is more than
  6% of the chord off it; closed if the end comes back within 16% of the box's diagonal of the path's first quarter
  (a gap or an overshoot); closed paths fit an ellipse (principal axes, least-squares radii) and a polygon (Douglas and
  Peucker, corners under 32° dropped, each corner sharp); open paths fit a circle (Kåsa) for an arc and the same
  corners for a run of lines. Straight sides win only when they fit 30% better than the curve. A box with corners
  within 20° of square is squared up; a near-round oval (axes within 12%) becomes a circle. Smaller than 90 glass px
  (about 8 mm): no snap. When unsure: no snap.
- **Undo: one step.** The clean shape is one Undo step; Undo after it leaves nothing. Why: the rough stroke is what the
  hold rejected, and a second step would need the rough stroke kept and later restored over the clean one, which no
  part of the page model does. To keep the rough stroke, lift without holding.
- **Mid-stroke pauses** shorter than 550 ms never ask. A longer pause asks once: over handwriting or a scribble nothing
  is recognised and the stroke draws on (a later rest asks again). A longer pause over a recognisable prefix (a
  straight run, then on into an L) does snap: there is no telling it from the final hold, as in Procreate.
- **After a snap** the rest of the touch draws nothing (the pen may wobble or wander); the lift finishes the clean
  stroke as any stroke (a quick stroke's render, wet paint starting to move).
- **E-ink: one update.** While the snap draws again (`DrawingPad.quietPanel`) nothing goes to the panel; the rough
  stroke's fast preview ends with everything the finish, the Undo and the clean stroke changed, through one
  `DirectEink.endFast` over their area. Not seen on the Manta yet.

## The seam

The pad's own take-back (`DrawingPad.cancelFingerStroke`): the rough stroke is finished (a quick stroke's render, wet
paint, mirrors' copies, a Filled Shape's fill: none can be dropped mid-way), then undone, then the same slot's stroke
is started again through `beginInkStroke` (so `InkWash.stroke`, `QuickStroke`, `ModularStroke`, mirrors, Fill in and
Record and Replay all work as for a drawn stroke) and fed the clean samples. Nothing in the six-part engine changed.

One wet paint fix was needed (`WatercolorSession.takeBack`, `WetCanvas.forget`, `WetWatercolor.rewet(wake, forget)`):
after a plain Undo, the undone stroke's pixels stay wet with no paint, so a Wet gouache shape snapped over its rough
stroke came out lighter (it mixed into "wet white", 2 greys a pixel off over the page), and Undo rests all wet paint,
so a wash laid a moment before stopped drying (stayed flat, no dark edge). The take-back makes the undone stroke's
bare-paper pixels and tiles dry and resting, and leaves the rest of the wet paint moving.

## What works, measured on the desktop (`HoldSnapChecks`, `scripts/snap_pictures.sh`)

- Synthetic rough shapes (wobble up to ~2% of the size, jitter, rounded corners, gaps and overshoots, 200 each):
  line 100%, circle 100%, oval 100%, triangle 100%, box 99%, pentagon 95% (4% become circles), arc 90% (8% no snap),
  open run of lines 86% (11% no snap). 96.4% right overall, 1.1% the wrong shape.
- Negatives, 200 each: cursive handwriting, scribbles, spirals, figure eights, waves, hatching, S curves: 0 snaps.
- Same brush: drawn as the pad draws it, the snapped page equals the clean stroke drawn alone, byte for byte for
  Swell brush, Compressed charcoal, 2B pencil; within a twentieth of a grey a pixel for Soft wash, Wet blooms and Wet
  gouache. One Undo leaves the page blank; nothing is left to redo.
- Wet paint laid just before, apart from the snap, dries as with no snap (0.00–0.02 grey a pixel).
- Desktop cost of the take-back and redraw: 30–80 ms for six shapes.

Pictures, rough left, snapped right: `docs/archive/pictures/explore/snap/snap-*.png` (Swell brush, Steady line,
Compressed, 2B pencil, Soft wash, Wet blooms, Wet gouache).

## What doesn't, or isn't known

- **Across a wash still wet**, where the rough stroke crossed it, its water there is gone after the Undo, so the clean
  stroke doesn't merge wet into wet at the crossing (0.2 grey a pixel off for the watercolours). A faint seam.
- **A big quick brush's rough stroke is rendered in full before it is undone** (`QuickStroke.finish` waits). On the
  Manta that may be most of a second for a large wash; measure before keeping.
- **Keeps Paint, the Wet brush's ink, Fling**: the rough stroke has already used them (the dirty brush picked up paint,
  the Wet brush ran drier, a Fling stop threw drops that the Undo takes back). The clean stroke starts from there.
- **Not snapping**: the eraser, the smudge tool and the stamp tool, a finger's stroke, Stipple's Build-up (holding
  builds dots), a Spiro's ring, the Lasso and placing a mirror. Brush Lab's test page doesn't snap.
- **Mirrors**: Copy Strokes and Real Mirror go through the same stroke factory, so the copies are taken back with the
  rough stroke (linked Undo) and laid again with the clean one. Checked by reading the code, not on the Manta.
- **Live pages** are not on this branch (`live-probe` and its siblings). They send a change's pixels at the pen's
  lift; a snap finishes and undoes the rough stroke while the pen is down, so a live page could send the rough stroke
  and its Undo before the clean one. When the two meet: either send only at the pointer's real lift (what the probe
  already waits for), or turn Hold to Snap off on a live page.
- **Not tried by hand.** The 550 ms, the 8 mm floor and the e-ink update need the owner's hand on the Manta.

## Should it replace Shapes?

Yes, after the owner has tried it. Shapes today is a mode (a column button, a menu of five shapes and Filled) that
draws crisp lines in the grey in use, not the brush. Hold to Snap draws any shape with the brush in hand, needs no
button and no mode, and the hand decides (PHILOSOPHY.md 1, 5 and "fewer buttons"). Shapes' two remaining strengths are
exact rubber-band sizing and filled shapes: Fill in already fills a loop (a snapped Fill in loop should fill too, not
checked), and the rubber band matters less in a sketchbook. Remove the Shapes button and `inkShape` once the snap has
been lived with; this branch leaves Shapes as it is.

## Checks on this branch

`HoldSnapChecks` passes; the Gradle release build and the instrumented checks' compile pass. `scripts/test_inkwash.sh`
stopped three runs out of three at `LaterMediaChecks fading-bytes` (Later's media against c360ed8e), unrelated to the
snap: the first Fading and Lemon Juice page a fresh JVM draws comes out unlike the next ones, in this version and in
c360ed8e alike, and the base commit 9a57ae48 fails that section too (1 run in 3 of it alone, as this branch did). It
looks like a first-run race in Later's work, worth its own look.
