package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

/**
 * The canvas. It renders the current page into a page-sized raster ({@link #display}),
 * or a screen-sized one while zoomed ({@link #viewportBitmap}), and presents changed
 * pixels straight to the e-ink panel through {@link DirectEink} when available.
 *
 * <p>Pen input runs one of these gestures at a time:
 * <ul>
 * <li>a live {@link ShapePreview} for Shapes, committed at pen-up;</li>
 * <li>while a {@link FloatingSelection} floats, pen drags move, scale and turn it, and it is placed
 * when the pen lands elsewhere or another action begins;</li>
 * <li>the Dropper's sampling while it is taken up.</li>
 * </ul>
 * Two fingers pinch and pan when navigation is unlocked. Wet paint keeps blending in
 * idle time between input events, except under a watercolor brush, which waits for pen-up.
 */
@SuppressLint("ViewConstructor")
final class DrawingPad extends View {
    private final PaintActivity app;
    /** See {@link Device#landscapeTilt}. */
    private final boolean landscapeTilt;
    ToneDocument document;
    private Bitmap display;
    private ViewportBitmap viewportBitmap;
    final CanvasViewport viewport = new CanvasViewport();
    final Matrix pageToView = new Matrix();
    private final Matrix viewToPage = new Matrix();
    private final float[] samplePoint = new float[2];
    private final PenSample penSample = new PenSample();
    private boolean unscaledPage = true;
    /** The app turn the page was last fitted for: a new turn fits it afresh. */
    private int fittedTurn = Surface.ROTATION_0;
    private int[] renderPixels = new int[0];
    /** What an area shows where the eraser went, -1 elsewhere; see {@link #showErased}. */
    private int[] erasedTones = new int[0];
    /** Changed presented pixels awaiting presentation, as separate patches. */
    private final DirtyRegions pending = new DirtyRegions(32);
    private final Rect presenting = new Rect();
    private DirectEink direct;
    /** Nomad's fast black/white session for moving previews; see {@link #beginFastPreview}. */
    private DirectEink previewDirect;
    /** Pixels {@link #previewDirect} showed, re-presented through {@link #direct} when the preview ends. */
    private final Rect previewArea = new Rect();
    /** A pen stroke's fast session that has stopped showing, until its end reaches the panel ({@link #endFast}), and what it is to show. */
    private DirectEink endingFast;
    private final Rect endingArea = new Rect();
    /**
     * Inkwash: a Spiro's ring ({@link #ringGuide}), shown at the touch-down through a fast session of its own, as the
     * lasso is, and what it showed; the stroke's own updates go through the gray session meanwhile and leave it be. At
     * the lift it ends as a fast preview does ({@link #endStrokePreview}).
     */
    private DirectEink ringDirect;
    private final Rect ringArea = new Rect();
    /** A setup's drag resized the ring since it was last shown ({@link MirrorShow#ringTo}). */
    private boolean ringMoved;
    /**
     * Scribulor: a Spiro's ring set up, waiting for the pen stroke of its tool that draws it ({@link RingSetup}); and,
     * that stroke begun, the setup still to go in before its first sample ({@link #feed}).
     */
    private RingSetup ringSet, ringPrefix;
    /** Panel area closed controls left stale (their fast ink, or a popup over the page), presented again through {@link #direct}; see {@link #eraseFastInk} and {@link #uncovered}. */
    private final RectF fastInk = new RectF();
    private int fastInkRefreshes;
    /** The last area {@link #eraseFastInk} blanked, panel pixels (PageNavigationChecks). */
    private final RectF blanked = new RectF();
    private final NativePen input;
    private int pointer = -1, retries;
    private final StrokeTiming timing = new StrokeTiming();
    /** Page pixels the last {@link #renderDirty()} rendered. */
    private long renderedArea;
    /** Fast display failed, connecting or while presenting: the page shows through Android drawing until it reconnects. */
    private boolean fellBack;
    private int drawCount;
    private int pagePresentCount;
    private final Runnable retry = () -> flush(true);
    /** Inkwash: a busy panel's wait, and connecting again after the fast display failed; see {@link DisplayRecovery}. */
    private final DisplayRecovery recovery = new DisplayRecovery();
    private final Runnable reconnect = this::reconnect;
    /** The last present's parts ({@link DirectEink#presentParts}), for the stroke's timing. */
    private final long[] presentParts = new long[4];
    /** How often the page presents: a stroke's presents spaced by their recent cost ({@link PresentPace}), Brush Lab's test page's too. */
    private final PresentPace pace = new PresentPace();

    // Strokes
    private DrawingStroke stroke;
    /** Inkwash: a pen stroke's samples gathered from input events, drawn together once the input queue is empty ({@link #drawBatch}). */
    private final PenBatch penBatch = new PenBatch();
    /** Whether Inkwash's pen strokes gather their samples ({@link #penBatch}); the live-stroke bench turns it off to compare. */
    static boolean penBatching = true;
    private final Runnable drawBatch = this::drawBatch;
    private boolean batchPosted;
    /** The stroke in progress is an Inkwash pen stroke (not a finger erasing), so it gathers its samples. */
    private boolean inkPen;
    /** A stroke's work after lift (a quick stroke's render, a Drying stroke's settle); the stroke stays {@link #stroke} until it is done. */
    private final QuickLift quickLift = new QuickLift(this, this::finishStroke);
    /**
     * Inkwash: what the canvas shows of the page's mirrors ({@link Mirrors}): guides, Real Mirror's reflection, Copy
     * Strokes' live copies and a placing stroke, over the page's render. The pen's next stroke places a mirror
     * ({@link Placing#LINE}) when set ({@link MirrorsMenu}), or is Cut Stamp's lasso ({@link Placing#CUT}, {@link
     * PaintActivity#cutStamp}), which cuts slot {@link #cutting}'s nib from the page.
     */
    final MirrorShow mirrorShow = new MirrorShow();
    enum Placing { NONE, LINE, CUT }
    Placing placing = Placing.NONE;
    /** Cut Stamp: the slot (or the stamp tool, {@link InkWash#STAMP}) whose Stamp the lasso cuts a nib for. */
    int cutting;

    /** A six-part Drying wash settling after lift; made with the first stroke that needs it. */
    private DryingTicker drying;

    // Shapes
    private ShapePreview shapeStroke;
    private float shapeX, shapeY;
    /** What a shape's preview went over, page px: drawn again from the page at its end ({@link #renderArea}), as its own raster leaves out the paper and what lies over the layers. */
    private final Rect shapeShown = new Rect();
    /**
     * Scribulor: Shapes taken up (the tool picker's {@link DrawLayout#SHAPES}): the pen draws {@link #shapes} in the
     * selected slot's grey, its outline the slot's size ({@link #inkShape}), until a tool is chosen ({@link InkWashBar#selectTool}).
     */
    boolean shapesOn;
    ToolSettings shapes = ToolSettings.defaults(ToolSettings.Tool.SHAPES);

    // The lasso and floating selections
    /** A piece floating over the page until it is placed, or null; see {@link #placeSelection}. */
    private FloatingSelection selection;
    /** Page pixels the piece covered when last shown; empty before its first showing, which shows its hole too. */
    private final Rect selectionShown = new Rect();
    /** Inkwash: where the piece was lifted, or null: put down there again, it changes nothing. */
    private FloatingSelection selectionLaid;
    /** Inkwash: the Lasso in use (the side column's button): the pen's strokes are loops that lift a piece ({@link LassoPlanes}). */
    boolean lassoOn;
    /**
     * Inkwash: the Lasso's loop deletes what it rings as it closes (Supernote's lasso erase) rather than lifting it, while
     * the edge strip is held ({@link EdgeHold}; the side column's Trash button's too until 2026-10-28).
     */
    boolean lassoDeletes;
    /**
     * Inkwash: the Lasso's bar did Cut, Delete or Duplicate (the owner, 2026-11-04: "switch back to the last used tool"): the
     * Lasso is put away, the tool in use drawing again, as soon as no piece floats, so a duplicate is first placed.
     */
    private boolean lassoBack;
    /** Inkwash: the piece last cut or copied (the Lasso's menu), lifted from nowhere, to paste on any page of its size; null before. */
    private FloatingSelection kept;
    /**
     * Inkwash: the Dropper taken up ({@link DrawLayout#DROPPER}): the pen on the page picks the grey it shows there
     * ({@link ToneDocument#shownTone}), a ring of it round the pen's tip, the lift sets it ({@link InkWashBar#dropped}) and puts
     * the Dropper down. Its gesture is MonoPaint's eyedropper's ({@link #sampleShadeGesture}).
     */
    boolean dropperOn;
    /** The tone the Dropper's pen is on, -1 for none (off the page). */
    private int dropTone = -1;
    /** The part of {@link #selection} the pen is dragging, or {@link FloatingSelection#NONE}. */
    private int selectionHandle = FloatingSelection.NONE;
    private float grabX, grabY, dragX, dragY;

    // Moving previews: a shape or a selection, drawn once per display frame
    private boolean previewFrameScheduled;
    private long lastPreviewFrame;
    private int previewFrameCount;
    private final Runnable previewFrame = this::drawPreviewFrame;

    // The Dropper's pen
    private int pickPointer = -1;
    /** A selection's frame, when shown. */
    private final CanvasGuide guide;

    // Finger taps and pinch/pan
    private final MultiFingerTap fingerTaps = new MultiFingerTap();
    /** Inkwash: two fingers, a tap until they move, then a pinch and pan while Zoom is unlocked. */
    private final PinchPan pinch = new PinchPan();
    boolean navigating, touchBlocked;
    private boolean navigationFrameScheduled;
    private long lastNavigationFrame, lastNavigationRasterNanos, lastNavigationPresentNanos;
    private int navigationFrameCount;
    private final Runnable navigationFrame = this::drawNavigationFrame;
    /** When the pen was last near the screen or on it (uptime ms): fingers are ignored 250 ms after, a two- or three-finger tap {@link FingerTool#PEN_PALM_MS}. */
    private long penSeen;
    /** The pen down, or at the screen within the last {@code ms} (the automatic turn waits on it, {@link RotationPrompt}). */
    boolean penWithin(long ms) { return pointer != -1 || SystemClock.uptimeMillis() < penSeen + ms; }

    /** Inkwash: the fridge magnets over the page, and the Words bin's pen ({@link WordsMode}). */
    final WordsMode words;
    /** Inkwash: a picture being placed on the page (Import Image, {@link PicturePlacer}). */
    final PicturePlacer placer;
    /** Inkwash: the Text tool and the page's text boxes over it ({@link TextMode}). */
    final TextMode text;

    DrawingPad(PaintActivity app) {
        super(app); this.app = app; landscapeTilt = app.device.landscapeTilt;
        // What the canvas lends what it shows over the page: the magnets, and a picture being placed.
        WordsMode.Host host = new WordsMode.Host() {
            @Override public ToneDocument page() { return document; }
            @Override public Bitmap raster() { return display; }
            @Override public float[] toPage(float x, float y) { pagePoint(x, y); return samplePoint.clone(); }
            @Override public void show(Rect pageArea) { queue(pageArea); unrefreshed = true; }
            @Override public void present() { flush(true); }
            @Override public void fastPreview(boolean on) { if (on) beginFastPreview(); else endFastPreview(); }
            @Override public void refresh(String reason, Rect pageArea) { refreshArea(reason, pageArea, 0); }
            @Override public boolean dots() { return viewportBitmap == null; }
        };
        words = new WordsMode(app, host);
        placer = new PicturePlacer(app, host);
        text = new TextMode(app, host);
        setContentDescription("Painting canvas; use the pen to paint. Unlock Zoom to pinch and pan with two fingers.");
        input = new NativePen(this, (bitmap, region) -> bitmap.recycle());
        guide = new CanvasGuide(dp(2), dp(5), dp(48));
    }
    private int dp(float value) { return app.dp(value); }
    private static boolean pointerUp(MotionEvent event, int pointer) {
        int action = event.getActionMasked();
        return (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                && event.getPointerId(event.getActionIndex()) == pointer;
    }
    /** The size a new blank page takes to fill this view in the current orientation, {width, height}. */
    int[] blankSize() { return new int[]{app.landscape ? getHeight() : getWidth(), app.landscape ? getWidth() : getHeight()}; }
    /**
     * A {@link #blankSize} taken earlier, for the next blank page alone (null: the view's now): New Notebook's name
     * prompt's keyboard squeezes the view until it goes (the owner, 2026-11-10: a new notebook's page 1785 × 1572).
     */
    int[] nextBlank;
    private ToneDocument blankPage() {
        int[] size = nextBlank != null && nextBlank[0] > 0 && nextBlank[1] > 0 ? nextBlank : blankSize();
        nextBlank = null;
        return InkWashLayers.blank(size[0], size[1]);
    }

    // Pages and rasters

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        finishStroke(); disconnectDisplay();
        if (w <= 0 || h <= 0) return;
        if (!app.loading && document == null) {
            document = blankPage();
            newBook(); app.updatePages();
        }
        ensureDisplay(); updateViewport();
        renderAll();
    }
    private void ensureDisplay() {
        int width = document != null ? document.width : app.landscape ? getHeight() : getWidth();
        int height = document != null ? document.height : app.landscape ? getWidth() : getHeight();
        if (width <= 0 || height <= 0) return;
        if (display != null && display.getWidth() == width && display.getHeight() == height) return;
        if (display != null) display.recycle();
        display = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        display.setHasAlpha(false);
    }
    /** Recomputes the page-to-view transform for the app rotation and zoom. */
    void updateViewport() {
        if (display == null || getWidth() <= 0 || getHeight() <= 0) return;
        // The presented raster may be replaced or redrawn: take the ring off it first.
        eraseOutline();
        int width = display.getWidth(), height = display.getHeight();
        // A turn opens the page afresh, so wait for the canvas's turned size: deciding on the
        // old one would spend the opening and fit the page instead of showing it at 100%.
        // The orientation frame updates the viewport again once the turned layout is done.
        if (fittedTurn != app.appRotation && isLayoutRequested()) return;
        if (fittedTurn != app.appRotation) viewport.reset();
        fittedTurn = app.appRotation;
        viewport.cover = app.zen;
        viewport.configure(getWidth(), getHeight(), width, height);
        float scale = viewport.scale();
        place(pageToView, scale, viewport.x, viewport.y);
        pageToView.invert(viewToPage);
        unscaledPage = pageToView.isIdentity();
        boolean wasScaled = viewportBitmap != null;
        // Keep one screen-sized raster and presenter throughout a pinch, including
        // its fitted endpoints. Changing format on each crossing stalls the gesture.
        boolean scaled = navigating || viewport.zoom != 1 || scale != 1 || viewport.x != 0 || viewport.y != 0;
        if (viewportBitmap != null && (!scaled || viewportBitmap.bitmap.getWidth() != getWidth()
                || viewportBitmap.bitmap.getHeight() != getHeight())) { viewportBitmap.close(); viewportBitmap = null; }
        if (scaled && viewportBitmap == null) viewportBitmap = new ViewportBitmap(getWidth(), getHeight());
        // Inkwash: any room the page leaves shows the owner's lines, at every zoom (the owner, 2026-10-27: they went as a
        // pinch or pan took the readout to 100%, though the page still left room). shortcut: the unscaled raster (100%,
        // no pan) has none, its room stays paper; give it lines if a page narrower than the screen shows there.
        if (viewportBitmap != null) viewportBitmap.backdrop = true;
        // Inkwash: Vector Ink's lines sharp or not (a pinch shows the page's pixels, as before; sharp again at its end).
        if (wasScaled != scaled || sharp() != composedSharp) renderAll();
        else if (viewportBitmap != null) { viewportBitmap.update(display, pageToView, null, sharpLines(), sharpTiles, sharpStep()); redrawGuide(); }
        pending.clear();
        wantSharpSoon();
        if (app.inkWashBar != null) app.inkWashBar.zoom.refresh();
    }
    private void place(Matrix transform, float scale, float x, float y) {
        // The page turns with the app (Rotate, the owner 2026-10-27), so it is upright in the canvas: no quarter turn of its own.
        transform.setValues(PageView.map(0, display.getWidth(), display.getHeight(), scale, x, y));
    }
    /** The page at 100%, where its artwork sat when the page began; see {@link #actualSize}. */
    Matrix homeToView() {
        Matrix home = new Matrix();
        place(home, 1, viewport.homeX(app.prefs.toolboxRight()), viewport.homeY());
        return home;
    }
    /** The whole screen, bars included, in this view's coordinates. */
    RectF screenArea() {
        RectF pad = Popups.bounds(app.root, this);
        return new RectF(-pad.left, -pad.top, app.root.getWidth() - pad.left, app.root.getHeight() - pad.top);
    }
    private void viewportChanged() {
        // A zoom always refreshes, whatever Automatic Refresh says (the owner, 2026-11-06: "force it").
        ghosted = unrefreshed = asked = true;
        disconnectDisplay(); updateViewport(); invalidate();
        if (!navigating) post(this::connectDisplay);
    }
    void zoomBy(float factor) {
        viewport.gesture(factor, getWidth()/2f, getHeight()/2f, getWidth()/2f, getHeight()/2f);
        viewportChanged();
    }
    /** The whole page, centred in the room the bars leave ({@link CanvasViewport#configure}). */
    void fitPage() { endNavigation(); viewport.reset(); viewportChanged(); }
    /** Reset scale and pan together, returning the artwork to where the page began (MonoPaint's Expand canvas). */
    void actualSize() { endNavigation(); viewport.actualSize(app.prefs.toolboxRight()); viewportChanged(); }
    /** A new notebook on the page shown ({@link DrawingBook#fresh}), Brush Lab's samples on its surface. */
    private void newBook() { app.book = DrawingBook.fresh(document); WildSample.show(document.surface); }
    /** Starts a new book from this page, or from a blank page when null. */
    void replace(ToneDocument replacement) {
        // Inkwash: a floating piece goes down on the page it floats over before another notebook shows.
        placeSelection();
        suspend();
        placing = Placing.NONE;
        viewport.reset();
        document = replacement; ghost = mirrorShow.ghost = null; layerSteps = 0;
        if (document == null && getWidth() > 0 && getHeight() > 0) document = blankPage();
        fadeToNow();
        if (document == null) app.book = null; else newBook();
        app.updatePages();
        ensureDisplay(); updateViewport();
        renderAll(); invalidate(); post(this::connectDisplay);
    }
    /**
     * A page just shown: its marks fading or darkening by the clock (Fading, Lemon Juice) as the clock has them now,
     * however long it was closed, and their ticks started.
     */
    private void fadeToNow() {
        if (document == null || !document.fading()) return;
        document.fade(FadingInk.now(), null);
        if (app.inkWash != null) post(() -> dryingTicker().start());
    }
    /** Shows another page of the current book, presenting it directly when possible. */
    void showPage(ToneDocument page) {
        // A two-finger tap's Undo still waiting is the page shown until now's.
        if (page != document) { layLiveNow(); app.live.shown(page); }
        // Inkwash: the page's surface, Brush Lab's samples' too (the surface spec, 2026-11-01).
        WildSample.show(page.surface);
        // Inkwash: a Lasso piece floats on over the next page.
        FloatingSelection carried = carrySelection(page);
        suspend(true);
        // Inkwash: a mirror waiting to be placed was this page's, as were the sections shown (the next look shows the new page's).
        placing = Placing.NONE;
        sectionRects = null; sectionActive = -1; sectionFlash = null;
        PageTurnDisplay turn = null;
        long started = SystemClock.uptimeMillis(), rendered = 0;
        // Same-sized fitted pages share a transform. Snapshot the old panel
        // background before rendering the next page, even with Pages open.
        if (document != null && display != null && page.width == document.width && page.height == document.height
                && viewport.untouched() && app.resumed && hasWindowFocus() && !isLayoutRequested() && !app.notebookShown()
                && !app.root.isLayoutRequested() && !app.previewFrame.isLayoutRequested()) {
            try { turn = new PageTurnDisplay(this, presented(), viewportBitmap == null ? pageToView : new Matrix(), app.pagePanel, zenFanKept()); }
            catch (RuntimeException | LinkageError error) { Log.w(ProbeActivity.TAG, "Page update uses Android display", error); }
        }
        boolean presented = false;
        try {
            document = page; ghost = mirrorShow.ghost = app.ghosts.plane(page); layerSteps = app.layerSteps(page);
            if (carried != null) { selection = carried; selectionShown.set(pageRect(carried.bounds())); }
            fadeToNow(); viewport.reset(); ensureDisplay(); updateViewport(); renderAll(); lassoBar();
            rendered = SystemClock.uptimeMillis();
            if (turn != null) {
                try { if (presented = turn.present(presented())) { wrote(new Rect(0, 0, presented().getWidth(), presented().getHeight())); zenFanRim(null); } }
                catch (RuntimeException error) { Log.w(ProbeActivity.TAG, "Page update uses Android display", error); }
            }
        } finally { if (turn != null) turn.close(); }
        Log.i(ProbeActivity.TAG, "Page shown: rendered " + (rendered - started) + " ms, on the panel " + (SystemClock.uptimeMillis() - rendered) + " ms" + (presented ? "" : " (Android redraw)"));
        if (presented) {
            pagePresentCount++;
            app.selectionFeedback.retainForNextDraw(this, new Rect(0, 0, getWidth(), getHeight()));
        } else invalidate();
        post(this::connectDisplay);
    }
    /**
     * Inkwash: tracing paper's ghosts under the page shown ({@link GhostPlane}; null: none), the screen's alone. Taken
     * at each page shown ({@link Ghosts#plane}); one ready later shows through {@link #showGhost}.
     */
    private GhostPlane.Planes ghost;
    /** Inkwash: tracing paper's layer steps on the page shown ({@link TracingPaper#layerBits}; 0: all Solid), the screen's alone. */
    private int layerSteps;
    /** Inkwash: the ghost plane for the page shown is {@code plane}: the whole page shows again with it, in one more full update. */
    void showGhost(GhostPlane.Planes plane) {
        if (plane == ghost || document == null || display == null) return;
        ghost = plane; mirrorShow.ghost = plane;
        showAgain("ghost");
    }
    /** Inkwash: the page shown's layer steps are {@code bits}, or another pair is drawn on ({@code always}): the whole page shows again. */
    void showSteps(int bits, boolean always) {
        if (bits == layerSteps && !(always && bits != 0) || document == null || display == null) return;
        layerSteps = bits;
        showAgain("layer steps");
    }
    private void showAgain(String why) {
        renderAll();
        Rect all = new Rect(0, 0, document.width, document.height);
        queue(all); unrefreshed = true; present();
        renderedArea = (long) all.width() * all.height();
        refreshAfterLargeChange(why);
    }
    /** The raster shown on screen: the page itself, or the zoomed view of it. */
    private Bitmap presented() { return viewportBitmap == null ? display : viewportBitmap.bitmap; }
    private void renderAll() {
        if (display == null) return;
        display.eraseColor(Color.WHITE);
        // Redrawn whole, the raster no longer holds the ring; it returns at the next hover or eraser move.
        outline.forget();
        // Another drawing or page, or the whole page changed: the layer thumbnails follow.
        if (app.inkWashBar != null) app.inkWashBar.pageChanged();
        if (document != null) {
            // Inkwash: the page's own pixels on the cores, as a changed area renders (no snapshot, so the next stroke
            // doesn't copy whole layers).
            Rect page = new Rect(0, 0, document.width, document.height);
            composedSharp = sharp();
            ViewportBitmap.compose(document, display, viewportBitmap == null, page, ghost, layerSteps, composedSharp);
            drawMask(page);
            mirrorShow.page(document.width, document.height); mirrorShow.show(document.mirrors()); mirrorShow.take(null, null);
            drawMirrors(page, null);
            drawSections(page);
            text.draw(display, page);
            words.drawAll(display);
            placer.drawAll();
            document.clearDirty(); document.takeMagnetAreas();
        }
        pending.clear();
        if (selection != null) showErased(selectionTones(), selectionArea());
        // Inkwash: sharp dry brushes' tiles go with what the page was; drawn again as wanted.
        sharpTiles.clear();
        if (viewportBitmap != null) viewportBitmap.update(display, pageToView, null, sharpLines(), sharpTiles, sharpStep());
        redrawGuide();
        wantSharpSoon();
    }
    /**
     * Inkwash: the layers whose Vector Ink lines the zoomed view draws sharp, as bits ({@link VectorInk#sharpLayers}), left out of
     * {@link #display}; 0 at 100% (the page's pixels are the lines there), while a pinch or pan moves the view (each frame
     * would draw them again). Over the layers shown above the page (tracing paper), the owner's call, 2026-11-04.
     */
    private int sharp() {
        return viewportBitmap == null || navigating || document == null ? 0 : VectorInk.sharpLayers(document, layerSteps, own(stroke));
    }
    /** What {@link #display} was composed without: when {@link #sharp} says otherwise, the whole page renders again. */
    private int composedSharp = 0;
    /**
     * The lines drawn sharp in the zoomed view, each in its layer's place: the sharp layers', as the stroke in progress
     * shows them; null for none. Over the layers stacked above the page (tracing paper) as before: they aren't a place.
     */
    private VectorInk.Stacked sharpLines() {
        int layers = sharp();
        if (layers == 0) return null;
        GhostPlane.Planes under = ghost == null ? null : new GhostPlane.Planes(ghost.under, null, null);
        // A Lasso piece floating: its lines where it lies (LassoPlanes.shownLines).
        return selection != null && selection.planes != null ? selection.planes.shownLines(selection, document, layers, own(stroke), layerSteps, under)
                : VectorInk.stacked(document, layers, layerSteps, under, n -> VectorInk.shownOf(document, n, own(stroke)), null);
    }
    /** Draws the masking fluid's hatching over the freshly composed {@code area} of the page. */
    private void drawMask(Rect area) { drawMask(document, display, area); }
    /** {@link #drawMask(Rect)} of {@code document} into {@code display}, its page-sized raster (Brush Lab's test page's too). */
    static void drawMask(ToneDocument document, Bitmap display, Rect area) {
        if (!document.hasMaskLayer()) return;
        int hatch = ToneDocument.HATCH;
        // Visit only the pixels on the lines: each row's lines start where x + y is a multiple of the spacing.
        for (int y = area.top; y < area.bottom; y++)
            for (int start = (hatch - y % hatch) % hatch - hatch; start < area.right; start += hatch)
                for (int x = Math.max(area.left, start); x < Math.min(area.right, start + ToneDocument.HATCH_LINE); x++) {
                    int line = document.maskHatch(x, y);
                    if (line >= 0) display.setPixel(x, y, line == 0 ? Color.BLACK : Color.WHITE);
                }
    }
    /** Rebuild size-dependent display resources after canvas expansion or its undo. */
    void canvasResized() {
        disconnectDisplay(); ensureDisplay(); updateViewport(); renderAll(); invalidate(); post(this::connectDisplay);
    }
    boolean blocksFingerGesture() { return pointer!=-1 || blocksTap(); }
    /** Anything but a stroke that keeps fingers off the page: the Dropper, or the pen nearby. */
    private boolean blocksTap() {
        return pickPointer!=-1 || SystemClock.uptimeMillis()<penSeen+250;
    }
    /** Re-renders the document's changed area and queues it for presentation, with what an eraser newly went over. */
    void renderDirty() {
        EraseBothStroke eraser = erasePreview();
        int[] also = eraser == null ? null : eraser.takeErased();
        // Inkwash: where a stump's other half changed, not yet on the page (DrawingStroke#unwritten).
        DrawingStroke.Unwritten shown = unwritten();
        int[] moved = shown == null ? null : shown.take();
        if (moved != null) also = around(also, moved);
        renderDirty(also);
    }
    /** {@code a} ({left, top, right, bottom}, exclusive; or null) grown to hold {@code inclusive} ({left, top, right, bottom}, inclusive). */
    private static int[] around(int[] a, int[] inclusive) {
        int[] b = {inclusive[0], inclusive[1], inclusive[2] + 1, inclusive[3] + 1};
        return a == null ? b : new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])};
    }
    /** {@link #renderDirty()}, re-rendering {@code also} ({left, top, right, bottom}, or null) as well. */
    private void renderDirty(int[] also) { renderDirty(also, null); }
    /**
     * {@link #renderDirty(int[])}, the page's changed area rendered as {@code apart} (null: one box round it all): areas
     * that hold every pixel changed since the last render (Copy Strokes' reflected copies, {@link MirrorStroke#areas}),
     * so the panel shows only those.
     */
    private void renderDirty(int[] also, java.util.List<int[]> apart) {
        renderedArea = 0;
        if (document == null || display == null) return;
        int[] bounds = document.dirty();
        // Inkwash: Vector Ink's lines turned sharp or back (the zoomed view, a first stroke, Undo): the whole page, without
        // their layer or with it.
        int sharp = sharp();
        // A layer with no lines yet (a first stroke's, an Undo of it) has an empty lines plane: the page is the same with or
        // without it, only the stroke's area renders (the whole page took ~520 ms on the Manta and the stroke lagged after).
        if (sharp != composedSharp) { if (((sharp ^ composedSharp) & VectorInk.linedMask(document)) != 0) bounds = new int[]{0, 0, document.width, document.height}; composedSharp = sharp; }
        if (also != null) bounds = bounds == null ? also : new int[]{Math.min(bounds[0], also[0]), Math.min(bounds[1], also[1]),
                Math.max(bounds[2], also[2]), Math.max(bounds[3], also[3])};
        // Inkwash: where the magnets changed (an Undo, a magnet laid), apart from the paint's area.
        java.util.List<int[]> magnetAreas = new java.util.ArrayList<>(document.takeMagnetAreas());
        // Inkwash: the mirrors' areas, apart too: a reflection or a live copy of what changed, a placing stroke.
        mirrorShow.page(document.width, document.height); mirrorShow.show(document.mirrors());
        magnetAreas.addAll(mirrorShow.take(bounds, mirrorShow.showing() && outline(stroke) instanceof MirrorStroke && ((MirrorStroke) outline(stroke)).live() ? marks() : null));
        if (bounds == null && magnetAreas.isEmpty()) return;
        // The guide's saved background must not cover the newly rendered artwork.
        // Overlays come off in reverse drawing order: each saved background predates the ones drawn over it.
        boolean guided = !guide.isEmpty();
        eraseOutline(); eraseGuide(); placer.overlayOff(); text.overlayOff(); words.overlayOff();
        // Apart only while they hold all that changed: anything past their box (nothing else draws while a stroke
        // lifts, but should it) renders the one box round it all.
        if (bounds != null && apart != null) {
            int[] around = also != null ? also.clone() : apart.isEmpty() ? null : apart.get(0).clone();
            for (int[] a : apart) { around[0] = Math.min(around[0], a[0]); around[1] = Math.min(around[1], a[1]); around[2] = Math.max(around[2], a[2]); around[3] = Math.max(around[3], a[3]); }
            if (around == null || bounds[0] < around[0] || bounds[1] < around[1] || bounds[2] > around[2] || bounds[3] > around[3]) apart = null;
        }
        if (bounds != null && apart != null) {
            magnetAreas.addAll(0, apart);
            if (also != null) magnetAreas.add(0, also);
        } else if (bounds != null) magnetAreas.add(0, bounds);
        for (int[] area : magnetAreas) {
            Rect dirty = new Rect(Math.max(0, area[0] - 2), Math.max(0, area[1] - 2),
                    Math.min(document.width, area[2] + 2), Math.min(document.height, area[3] + 2));
            if (dirty.intersect(0, 0, display.getWidth(), display.getHeight())) {
                renderedArea += (long)dirty.width() * dirty.height();
                render(dirty);
                sharpTiles.changed(new int[]{dirty.left, dirty.top, dirty.right, dirty.bottom});
                queue(dirty);
                unrefreshed = true;
            }
        }
        document.clearDirty();
        words.overlayOn();
        text.overlayOn();
        placer.overlayOn();
        if (guided) drawGuide();
        drawOutline();
        wantSharpSoon();
    }
    /** Queues page pixels that are already up to date in {@link #display}. */
    private void queue(Rect pageArea) {
        queueView(viewportBitmap == null ? pageArea : viewportBitmap.update(display, pageToView, pageArea, sharpLines(), sharpTiles, sharpStep()));
    }
    /**
     * Inkwash: sharp dry brushes (the HB pencil pilot, {@link SharpView}): zoomed in, the tiles of the page shown are drawn
     * finer off this thread, the page's pixels shown meanwhile, each tile shown as it is ready ({@link #sharpReady}) in
     * the panel's usual update, no flash of its own (Automatic Refresh does as it does).
     */
    private final SharpView sharpTiles = new SharpView(rect -> post(() -> sharpReady(rect)));
    /** The grid the zoomed view draws sharp tiles at ({@link SharpView#stepFor}); 0 at 100%, while a pinch or pan moves the view. */
    private int sharpStep() { return viewportBitmap == null || navigating || document == null ? 0 : SharpView.stepFor(viewport.scale()); }
    /** {@link #wantSharp} once what is under way is done (a stroke's end logs it first). */
    private void wantSharpSoon() { if (!sharpSoon) { sharpSoon = true; post(() -> { sharpSoon = false; wantSharp(); }); } }
    private boolean sharpSoon;
    /** Asks for the sharp tiles of the page the view shows; none while a stroke is drawn (they would be drawn again as it goes). */
    private void wantSharp() {
        int k = sharpStep();
        if (k == 0 || stroke != null || display == null) { sharpTiles.want(document, 0, 0, 0, 0, 0, null, 0, 0, null); return; }
        RectF seen = new RectF(0, 0, getWidth(), getHeight());
        viewToPage.mapRect(seen);
        sharpTiles.want(document, k, (int) Math.floor(seen.left), (int) Math.floor(seen.top), (int) Math.ceil(seen.right), (int) Math.ceil(seen.bottom),
                ghost, layerSteps, composedSharp, (x, y, w, h, out) -> { display.getPixels(out, 0, w, x, y, w, h); for (int i = 0; i < out.length; i++) out[i] &= 255; });
    }
    /** A sharp tile over page {@code rect} is ready: shown, if the view still draws them. */
    private void sharpReady(int[] rect) {
        if (sharpStep() == 0 || document == null) return;
        queue(new Rect(rect[0], rect[1], rect[2], rect[3]));
        unrefreshed = true;
        flush(true);
    }
    /**
     * Renders the page's {@code dirty} area into {@link #display}: natively, as {@link ToneDocument#render}
     * would (dots, or plain tones for the zoomed raster to scale), then any masking-fluid hatching, then
     * the page as an eraser in progress will leave it wherever it went.
     */
    private void render(Rect dirty) {
        ViewportBitmap.compose(document, display, viewportBitmap == null, dirty, ghost, layerSteps, composedSharp);
        drawMask(dirty);
        EraseBothStroke eraser = erasePreview();
        if (eraser != null) showErased(eraser::erasedTones, new Rect(dirty));
        // Inkwash: a Lasso piece floating over it, and the hole it was lifted from.
        if (selection != null) showErased(selectionTones(), new Rect(dirty));
        // Inkwash: a stump's other half as smudged so far, not yet on the page; a live copy darkens it as shown.
        MirrorShow.Marks shown = shownOnce(unwritten(), dirty);
        if (shown != null) showErased(shown, new Rect(dirty));
        drawMirrors(dirty, shown);
        drawSections(dirty);
        // Inkwash: the text boxes, then the fridge magnets over it all.
        text.draw(display, dirty);
        words.draw(display, dirty);
    }
    /** Inkwash: the page's timed sections as shown ({@link TimedSections#draw}): their boxes, the running one (−1: none), the box flashing (or null). */
    private int[][] sectionRects;
    private int sectionActive = -1;
    private int[] sectionFlash;
    /** The sections over the page's {@code area} of {@link #display}: never in the page or its copies. */
    private void drawSections(Rect area) {
        if (sectionRects == null || sectionActive < 0 && sectionFlash == null) return;
        int w = area.width(), rows = bandRows(area);
        if (renderPixels.length < w * rows) renderPixels = new int[w * rows];
        for (int top = area.top; top < area.bottom; top += rows) {
            int h = Math.min(rows, area.bottom - top);
            display.getPixels(renderPixels, 0, w, area.left, top, w, h);
            if (TimedSections.draw(renderPixels, area.left, top, w, h, viewportBitmap == null, sectionRects, sectionActive, sectionFlash, TimedSections.band(document.width)))
                display.setPixels(renderPixels, 0, w, area.left, top, w, h);
        }
    }
    /**
     * The rows of {@code area} drawn over at a time: a whole page's pixels at once took 18 MB more of the heap (an
     * OutOfMemoryError on the Manta, 2026-10-22); each pixel is drawn from its own place alone, so bands draw the same.
     */
    static int bandRows(Rect area) { return Math.max(1, Math.min(area.height(), BAND_PIXELS / Math.max(1, area.width()))); }
    private static final int BAND_PIXELS = 1 << 18;
    /** The page shows its sections {@code rects} (null: none), {@code active} outlined (−1: none), the boxes alone drawn again when that changes. */
    void showSections(int[][] rects, int active) {
        if (active == sectionActive && java.util.Arrays.deepEquals(rects, sectionRects)) return;
        int[] area = TimedSections.around(rects != null ? rects : sectionRects);
        sectionRects = rects; sectionActive = active;
        if (area != null && document != null) { renderDirty(area); present(); }
    }
    /** Box {@code rect}'s outline flashes, inverted, once; then {@code then} runs. */
    void flashSection(int[] rect, Runnable then) {
        sectionFlash = rect;
        renderDirty(rect.clone()); present();
        postDelayed(() -> {
            if (sectionFlash == rect) { sectionFlash = null; if (document != null) { renderDirty(rect.clone()); present(); } }
            then.run();
        }, FLASH_MS);
    }
    /** How long a section's flash shows, ms. */
    private static final long FLASH_MS = 500;
    /** The mirrors over the page's {@code area} of {@link #display}: Real Mirror's reflection, live copies, a placing stroke, the guides ({@link MirrorShow}). */
    /** {@link #shownOnce}'s tones, and the box ({left, top, right, bottom}, inclusive) they cover. */
    private int[] shownTones = new int[0];
    private final int[] shownBox = new int[4];
    /**
     * {@code shown}'s tones over {@code area} where the stroke changed anything ({@link DrawingStroke.Unwritten#all}),
     * worked out once for the page and a live copy over it (each would read them); null for none, or nothing there.
     * Past {@link #BAND_PIXELS} (a whole page drawn again mid-stroke) read as they are asked for instead.
     */
    private MirrorShow.Marks shownOnce(DrawingStroke.Unwritten shown, Rect area) {
        int[] all = shown == null ? null : shown.all();
        if (all == null) return null;
        int x0 = Math.max(area.left, all[0]), y0 = Math.max(area.top, all[1]), x1 = Math.min(area.right - 1, all[2]), y1 = Math.min(area.bottom - 1, all[3]);
        if (x0 > x1 || y0 > y1) return null;
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        if ((long) w * h > BAND_PIXELS) return shown;
        if (shownTones.length < w * h) shownTones = new int[w * h];
        int[] t = shownTones, box = shownBox;
        shown.tones(x0, y0, w, h, t);
        box[0] = x0; box[1] = y0; box[2] = x1; box[3] = y1;
        return (x, y, bw, bh, out) -> {
            for (int row = 0; row < bh; row++) for (int col = 0; col < bw; col++) {
                int px = x + col, py = y + row;
                out[row * bw + col] = px < box[0] || px > box[2] || py < box[1] || py > box[3] ? -1 : t[(py - box[1]) * w + px - box[0]];
            }
        };
    }
    private void drawMirrors(Rect area, MirrorShow.Marks shown) {
        Mirrors.Tones page = MirrorShow.tones(document);
        if (shown != null) {
            Mirrors.Tones found = page;
            page = new Mirrors.Tones() {
                private final int[] one = new int[1];
                private int[] row = new int[0];
                @Override public int tone(int x, int y) { shown.tones(x, y, 1, 1, one); return one[0] >= 0 ? one[0] : found.tone(x, y); }
                @Override public void tones(int x, int y, int w, int[] out) {
                    found.tones(x, y, w, out);
                    if (row.length < w) row = new int[w];
                    shown.tones(x, y, w, 1, row);
                    for (int i = 0; i < w; i++) if (row[i] >= 0) out[i] = row[i];
                }
            };
        }
        renderPixels = drawShow(mirrorShow, document, page, display, area, viewportBitmap == null, renderPixels);
    }
    /** The stroke's changes not yet on the page, shown over it ({@link DrawingStroke#unwritten}); null for none. */
    private DrawingStroke.Unwritten unwritten() { return stroke == null ? null : stroke.unwritten(); }
    /**
     * What {@code show} shows over {@code page}'s {@code area} of {@code display}, in dots (or plain greys), through
     * {@code pixels} (made bigger if need be; returned): the drawing screen's mirrors and Brush Lab's lasso.
     */
    static int[] drawShow(MirrorShow show, ToneDocument page, Bitmap display, Rect area, boolean dots, int[] pixels) {
        return drawShow(show, page, MirrorShow.tones(page), display, area, dots, pixels);
    }
    /** As above, the page's tones read from {@code tones}. */
    static int[] drawShow(MirrorShow show, ToneDocument page, Mirrors.Tones tones, Bitmap display, Rect area, boolean dots, int[] pixels) {
        if (!show.touches(area.left, area.top, area.right, area.bottom)) return pixels;
        int w = area.width(), rows = bandRows(area);
        if (pixels.length < w * rows) pixels = new int[w * rows];
        for (int top = area.top; top < area.bottom; top += rows) {
            int h = Math.min(rows, area.bottom - top);
            display.getPixels(pixels, 0, w, area.left, top, w, h);
            if (show.draw(pixels, area.left, top, w, h, dots, tones, page.paperTone()))
                display.setPixels(pixels, 0, w, area.left, top, w, h);
        }
        return pixels;
    }
    /** The eraser in progress, when the screen shows the page as it will leave it wherever it went (see {@link EraseBothStroke#previews}); otherwise null. */
    private EraseBothStroke erasePreview() {
        DrawingStroke own = own(stroke);
        return own instanceof EraseBothStroke && ((EraseBothStroke)own).previews() ? (EraseBothStroke)own : null;
    }
    /** The stroke as drawn: a mirrored one's own ({@link MirrorStroke#primary}), else the stroke. */
    private static DrawingStroke own(DrawingStroke stroke) { return stroke instanceof MirrorStroke ? ((MirrorStroke)stroke).primary : stroke; }
    /** A Filled Shape's outline as drawn, mirrored or not ({@link FilledShape#outline}), else the stroke. */
    private static DrawingStroke outline(DrawingStroke stroke) { return stroke instanceof FilledShape ? ((FilledShape)stroke).outline : stroke; }
    /** Copy Strokes' live copies: what the stroke shows on the page, the eraser's what it leaves. */
    private MirrorShow.Marks marks() {
        DrawingStroke own = own(stroke);
        if (own instanceof EraseBothStroke) return ((EraseBothStroke)own)::erasedTones;
        return document::gestureTones;
    }
    /**
     * Shows the page as {@code eraser} will leave it (in either raster: dots, or plain greys for the zoomed one)
     * wherever it went in {@code area}, every pair alike, though most is erased only when it lifts. The lift's
     * render then shows the same pixels from the page.
     */
    private void showErased(MirrorShow.Marks eraser, Rect area) {
        if (!area.intersect(0, 0, display.getWidth(), display.getHeight())) return;
        int rows = bandRows(area);
        for (int top = area.top; top < area.bottom; top += rows) showErasedRows(eraser, new Rect(area.left, top, area.right, Math.min(area.bottom, top + rows)));
    }
    /** {@link #showErased} over {@code area}, at most {@link #bandRows} of it: also what a floating Lasso piece shows ({@link LassoPlanes#shown}). */
    private void showErasedRows(MirrorShow.Marks eraser, Rect area) {
        int w = area.width(), h = area.height(), count = w * h;
        if (erasedTones.length < count) erasedTones = new int[count];
        eraser.tones(area.left, area.top, w, h, erasedTones);
        int first = 0;
        while (first < count && erasedTones[first] < 0) first++;
        if (first == count) return;
        if (renderPixels.length < count) renderPixels = new int[count];
        display.getPixels(renderPixels, 0, w, area.left, area.top, w, h);
        boolean dots = viewportBitmap == null;
        for (int i = first; i < count; i++) {
            int tone = erasedTones[i];
            // Inkwash: what is left shows on the ghost, as the lift's render will show it.
            if (tone >= 0 && ghost != null) tone = GhostPlane.over(tone, ghost, (area.top + i / w) * document.width + area.left + i % w);
            if (tone >= 0) renderPixels[i] = dots ? DotPattern.pixel(tone, area.left + i % w, area.top + i / w) : 0xff000000 | tone * 0x010101;
        }
        display.setPixels(renderPixels, 0, w, area.left, area.top, w, h);
    }
    /**
     * Inkwash, PickerBench's question (null: not asked): where each call since asking the page to be drawn again through
     * Android came from. A call from inside Android (a View flag) or the firmware with no call here shows as none.
     */
    java.util.List<Throwable> redrawAsks;
    private void asked() { java.util.List<Throwable> asks = redrawAsks; if (asks != null) asks.add(new Throwable("page redraw asked")); }
    @Override public void invalidate() { asked(); super.invalidate(); }
    @SuppressWarnings("deprecation") @Override public void invalidate(Rect dirty) { asked(); super.invalidate(dirty); }
    @SuppressWarnings("deprecation") @Override public void invalidate(int l, int t, int r, int b) { asked(); super.invalidate(l, t, r, b); }
    @Override protected void onDraw(Canvas canvas) {
        drawCount++;
        canvas.drawColor(Color.WHITE);
        if (viewportBitmap != null) canvas.drawBitmap(viewportBitmap.bitmap, 0, 0, null);
        else if (display != null) canvas.drawBitmap(display, pageToView, null);
    }
    /** Page points as presented: the unzoomed page raster is only rotated (never scaled) relative to the view. */
    private float[] presentedPoints(float[] page, int count) {
        float[] points = java.util.Arrays.copyOf(page, 2 * count);
        if (viewportBitmap != null) pageToView.mapPoints(points);
        return points;
    }
    /** Draws the guide the current gesture shows, if any, in place of whatever was shown. */
    private void drawGuide() {
        if (selection != null) drawSelectionFrame();
        else eraseGuide();
    }
    /** Restores the pixels under the guide and queues them. */
    private void eraseGuide() { if (presented() != null) guide.erase(presented(), this::queueView); }
    /** Draws the guide again after a re-render replaced the raster under it. */
    private void redrawGuide() { guide.forget(); drawGuide(); }

    // The nib's outline

    /*
     * The nib's ring, a few ink ticks: while the pen hovers, where any tool would lay its mark ({@link HoverNib}: a
     * typical touch's nib, a shaped tip's ellipse at its angle), and at the pen or fingertip while erasing, at the eraser's
     * live size. Up to {@link HoverNib#TICKS} dots {@link #OUTLINE_WIDTH} px square on the ring, one a {@link HoverNib#TICK_SPACING}
     * px of it, a lone dot where the nib lands for the finest (the owner, 2026-10-29: "at small sizes the 8 dots seems like
     * too many.. you could get away with even 1 at super-fine sizes. can it be relative?"; 8 always until then), ink alone (the owner,
     * 2026-10-24: "make hover indicators more subtle. right now they can disrupt the image"; a dashed ring of black and
     * white, and a second dotted ring for the firmest nib, until then). Like the gradient guide they are drawn over the
     * presented raster, never the page, each in a small tile whose pixels are saved first and put back to remove it; only
     * those tiles reach the panel. Black and white, for the panel's fast path.
     */
    /** The ticks' side, in raster pixels (the Dropper's rings' width too), and how many round the ring. */
    private static final float OUTLINE_WIDTH = HoverNib.TICK_SIDE;
    /** Hover redraws the ring at most once a frame, and only once it moved or resized by more than {@link #OUTLINE_STEP} px. */
    private static final int OUTLINE_FRAME_MS = 16;
    private static final float OUTLINE_STEP = 2;
    private final RasterOverlay outline = new RasterOverlay();
    /** Where the ring should be, page pixels, and its nib; null for nowhere. */
    private float outlineX, outlineY;
    private HoverNib outlineNib;
    /** Inkwash: the Dropper's ring filled with {@code outlineTone} in place of the nib's (-1: the nib's). */
    private int outlineTone = -1;
    /** Where it was last drawn. */
    private float drawnX, drawnY;
    private HoverNib drawnNib;
    private int drawnTone = -1;
    /** The hovering tool and its nib (null: it lays nothing here), worked out once a hover; -1 when they must be again. */
    private int hoverTool = -1;
    private HoverNib hoverNib;
    /**
     * The ring shows for {@link #NIB_SHOWN_MS} as the pen arrives, then goes (the owner, 2026-10-29: the dots "like a
     * little bug on the screen demanding my attention"; it followed the pen the whole hover until then). The pen arrives
     * once it has been away {@link #PEN_AWAY_MS}, or with a new tool or size; a stroke's lift isn't an arrival.
     */
    static final long NIB_SHOWN_MS = HoverNib.SHOWN_MS;
    private boolean penArrives = true;
    private long nibShownUntil;
    private final Runnable nibShown = () -> { if (stroke == null && !dropperOn) hideOutline(); };
    private boolean outlineFrameScheduled;
    private long lastOutlineFrame;
    private final Runnable outlineFrame = this::drawOutlineFrame;
    private final Paint outlineTick = new Paint();
    /** The Dropper's ring is a ring of a grey, not a nib: any nib stands for it. */
    private static final HoverNib DROP_NIB = HoverNib.round(0);
    {
        outlineTick.setStyle(Paint.Style.FILL); outlineTick.setColor(Color.BLACK);
    }

    /** Shows the ring of {@code nib} at page {@code x, y}, at the next frame unless it is already about there. */
    private void showOutline(float x, float y, HoverNib nib) { showOutline(x, y, nib, -1); }
    /** As {@link #showOutline(float, float, HoverNib)}: the Dropper's ring of {@code tone} (-1: the nib's ring). */
    private void showOutline(float x, float y, HoverNib nib, int tone) {
        outlineX = x; outlineY = y; outlineNib = nib; outlineTone = tone;
        if (outlineCurrent() || outlineFrameScheduled) return;
        outlineFrameScheduled = true;
        postDelayed(outlineFrame, Math.max(0, OUTLINE_FRAME_MS - (SystemClock.uptimeMillis() - lastOutlineFrame)));
    }
    /** Whether the ring on screen is within a step of where it should be. */
    private boolean outlineCurrent() {
        return !outline.isEmpty() && Math.abs(outlineX - drawnX) <= OUTLINE_STEP && Math.abs(outlineY - drawnY) <= OUTLINE_STEP
                && outlineNib != null && outlineNib.about(drawnNib, OUTLINE_STEP) && outlineTone == drawnTone;
    }
    private void drawOutlineFrame() {
        removeCallbacks(outlineFrame); outlineFrameScheduled = false;
        lastOutlineFrame = SystemClock.uptimeMillis();
        if (outlineNib == null || outlineCurrent()) return;
        eraseOutline(); drawOutline(); flush(true);
    }
    /** Takes the ring off the screen. */
    void hideOutline() {
        outlineNib = null; removeCallbacks(outlineFrame); outlineFrameScheduled = false;
        if (outline.isEmpty()) return;
        eraseOutline(); flush(true);
    }
    /** Puts back the pixels under the ring, queueing them; it stays wanted where it was. */
    private void eraseOutline() {
        if (outline.isEmpty()) return;
        outline.restore(presented(), this::queueView);
        // Fast updates leave ghosts: the page gets its refresh once the pen is away.
        unrefreshed = true;
    }
    /** Draws the ring where it is wanted, saving the pixels under it, and queues them. */
    private void drawOutline() {
        HoverNib nib = outlineNib;
        if (nib == null || display == null || !outline.isEmpty()) return;
        Bitmap target = presented();
        // On the zoomed view the ring follows the page's scale, turn and mirroring.
        float[] center = {outlineX, outlineY}, axis = {(float) Math.cos(nib.angle), (float) Math.sin(nib.angle)};
        float scale = 1;
        if (viewportBitmap != null) { pageToView.mapPoints(center); pageToView.mapVectors(axis); scale = pageToView.mapRadius(1); }
        float cx = center[0], cy = center[1], turn = (float) Math.atan2(axis[1], axis[0]);
        if (outlineTone >= 0) { drawDrop(cx, cy, target); return; }
        float a = HoverNib.radius(nib.width, scale), b = Math.min(a, HoverNib.radius(nib.height, scale));
        float[] ticks = HoverNib.ticks(cx, cy, a, b, turn);
        float half = OUTLINE_WIDTH / 2;
        for (int i = 0; i < ticks.length; i += 2)
            outline.add(new Rect((int) Math.floor(ticks[i] - half - 1), (int) Math.floor(ticks[i + 1] - half - 1), (int) Math.ceil(ticks[i] + half + 1), (int) Math.ceil(ticks[i + 1] + half + 1)), target);
        if (outline.isEmpty()) return;
        outline.save(target);
        Canvas canvas = new Canvas(target);
        for (int i = 0; i < ticks.length; i += 2) canvas.drawRect(Math.round(ticks[i] - half), Math.round(ticks[i + 1] - half), Math.round(ticks[i] + half), Math.round(ticks[i + 1] + half), outlineTick);
        outline.queue(this::queueView);
        drawnX = outlineX; drawnY = outlineY; drawnNib = nib; drawnTone = -1;
        unrefreshed = true;
    }
    /**
     * The Dropper's ring at raster {@code cx, cy}, the pen's tip (the owner, 2026-10-24: "dropper is confusing.. the
     * selector is offset"): a band of {@link #outlineTone} as the page shows a grey (its dots, or plain grey zoomed)
     * round a hole the page shows through, so what it reads is under the tip and in plain sight, ink rings edged in
     * white either side of the band, so it reads on any page. One small patch, put back as the hover ring is.
     */
    private void drawDrop(float cx, float cy, Bitmap target) {
        int r = dp(DrawLayout.DROPPER_RING), hole = dp(DrawLayout.DROPPER_HOLE), edge = (int) (2 * OUTLINE_WIDTH) + 2, tone = outlineTone;
        outline.add(new Rect((int) cx - r - edge, (int) cy - r - edge, (int) cx + r + edge + 1, (int) cy + r + edge + 1), target);
        if (outline.isEmpty()) return;
        outline.save(target);
        boolean dots = viewportBitmap == null;
        for (int y = (int) cy - r; y <= (int) cy + r; y++) for (int x = (int) cx - r; x <= (int) cx + r; x++) {
            float d = (x - cx) * (x - cx) + (y - cy) * (y - cy);
            if (x >= 0 && y >= 0 && x < target.getWidth() && y < target.getHeight() && d <= r * r && d > hole * hole)
                target.setPixel(x, y, dots ? DotPattern.pixel(tone, x, y) : 0xff000000 | tone * 0x010101);
        }
        Canvas canvas = new Canvas(target);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(OUTLINE_WIDTH);
        ring.setColor(Color.WHITE); canvas.drawCircle(cx, cy, r + OUTLINE_WIDTH, ring); canvas.drawCircle(cx, cy, hole - OUTLINE_WIDTH, ring);
        ring.setColor(Color.BLACK); canvas.drawCircle(cx, cy, r, ring); canvas.drawCircle(cx, cy, hole, ring);
        outline.queue(this::queueView);
        drawnX = outlineX; drawnY = outlineY; drawnNib = outlineNib; drawnTone = tone;
        unrefreshed = true;
    }
    /** The Dropper taken up or put down: no grey picked yet, no ring. */
    void dropper(boolean on) { dropperOn = on; dropTone = -1; hideOutline(); }
    /** The tone the page shows at view {@code x, y} (every layer and its planes, no guides), the Dropper's ring of it round the pen's tip; -1 off the page, no ring. */
    private int dropAt(float x, float y) {
        pagePoint(x, y);
        int px = (int) samplePoint[0], py = (int) samplePoint[1];
        if (samplePoint[0] < 0 || samplePoint[1] < 0 || px >= document.width || py >= document.height) { hideOutline(); return -1; }
        int tone = document.shownTone(px, py, ghost == null ? null : ghost.under, layerSteps, ghost == null ? null : ghost.overTones, ghost == null ? null : ghost.overAlpha);
        showOutline(samplePoint[0], samplePoint[1], DROP_NIB, tone);
        return tone;
    }
    /** The pen hovering over the page: the ring of the nib it would lay there, at a typical touch, if a stroke would start. */
    private void hoverOutline(MotionEvent event) {
        // Zen Mode's fan open (it stays open while drawing): the ring still shows, kept off the fan as the paint is (covered).
        // A finger erasing keeps its own ring.
        if (stroke != null || app.inkWash == null) return;
        int action = event.getActionMasked(), tool = eraserEnd(event, 0) ? InkWash.ERASER : app.inkWash.selected;
        // A new hover: the tool, its size or its brush may have changed while the pen was away.
        if (action == MotionEvent.ACTION_HOVER_ENTER) hoverTool = -1;
        // The Dropper: a ring of the grey under the pen, round its tip.
        if (dropperOn) {
            if (action == MotionEvent.ACTION_HOVER_EXIT || document == null || display == null || app.popupOpen() || app.canvasCovered()) hideOutline();
            else dropAt(event.getX(), event.getY());
            return;
        }
        // A placing stroke or Words take the pen: no nib lands. Nib Preview off: no ring.
        if (action == MotionEvent.ACTION_HOVER_EXIT || words.open() || text.on() || document == null || display == null || app.popupOpen() || app.canvasCovered()
                || placing != Placing.NONE || lassoOn || !app.rules().nibPreview || stroke != null) { hideOutline(); return; }
        if (tool != hoverTool) {
            HoverNib was = hoverNib; hoverTool = tool; hoverNib = hoverNib(tool);
            // A new tool or size shows its ring again, as an arrival (the owner, 2026-10-29); the same nib after a lift doesn't.
            if (hoverNib != null && !hoverNib.about(was, 0)) penArrives = true;
        }
        if (hoverNib == null) { hideOutline(); return; }
        if (penArrives) { penArrives = false; nibShownUntil = event.getEventTime() + NIB_SHOWN_MS; removeCallbacks(nibShown); postDelayed(nibShown, NIB_SHOWN_MS); }
        if (event.getEventTime() >= nibShownUntil) return;
        pagePoint(event.getX(), event.getY());
        showOutline(samplePoint[0], samplePoint[1], hoverNib);
    }
    /** Tool {@code tool}'s nib at a typical touch, or null where it may lay nothing (a locked page, the notebook's rules). */
    private HoverNib hoverNib(int tool) {
        Brush brush = app.inkWash.brush(tool);
        if (app.pageLocked() || NotebookRules.refuses(app.rules(), tool, brush)) return null;
        ToolSwatches.Look look = app.inkWash.look(tool);
        // An effect brush rings as the eraser does, at its own size: its change shows under the pen as it goes (the owner,
        // 2026-11-04, the smudge tool: "just like eraser"; "if the effect is instant no need to use the grey band").
        return brush == null || WildSample.reworks(brush.parts) ? HoverNib.eraser(look.px) : HoverNib.of(ResolvedBrush.of(brush.parts, look.px, look.darkness));
    }
    /**
     * While a finger erases: the ring follows it, at its live size, where the finger hides the page. Never the pen's: no
     * dots while the pen touches (the owner, 2026-10-29: "if there's a way to disable them when the pen is actually
     * touching the screen, i'd like that"; the pen's eraser had its ring until then). For {@link #NIB_SHOWN_MS} from the
     * touch, then gone, as the pen's (the owner, 2026-10-30: "eraser preview dots should follow the same pattern").
     */
    private void followEraser() {
        if (drawFinger < 0 || !(own(stroke) instanceof EraseBothStroke)) return;
        if (SystemClock.uptimeMillis() >= nibShownUntil) { hideOutline(); return; }
        float[] at = ((EraseBothStroke)own(stroke)).at();
        if (at != null) showOutline(at[0], at[1], HoverNib.round(at[2]));
    }

    // Direct e-ink presentation

    /** Starts direct presentation and native pen input once nothing covers the canvas. */
    void connectDisplay() {
        if (app.canvasCovered() || !app.resumed || !hasWindowFocus() || app.loading || display == null || direct != null) return;
        // Standard Android drawing leaves the firmware pen and e-ink interfaces untouched.
        if (DirectEink.androidDrawing()) return;
        if (isLayoutRequested() || app.root.isLayoutRequested() || app.orientationFrame.isLayoutRequested()
                || app.previewFrame.isLayoutRequested()) return;
        try {
            if (!input.prepareDocumentCanvas()) throw new IllegalStateException(input.status);
            direct = viewportBitmap == null ? DirectEink.forView(this, display, pageToView, 0, 7)
                    : DirectEink.forView(this, viewportBitmap.bitmap, new Matrix(), 0, 7);
            fellBack = false; recovery.restart();
            boolean afterSplash = app.takeSplashGhost();
            if (cleanWindow || afterSplash) {
                cleanWindow = false;
                eraseFastInk(windowArea());
                // The clean redraws the whole page too: no forced refresh after it (its Android redraw of the bar would blink again).
                // A page turned or ghosted meanwhile (a page opened from the notebook screens) flashes in it, once.
                Runnable restore = () -> {
                    if (direct != null) input.partialGrey();
                    app.inkWashBar.partialGrey();
                };
                // Always the clean redraw, its blink and no flash, even with Automatic Refresh off: a screen over this one (Page
                // Settings) otherwise leaves the column and the top bar ghosted (the owner, 2026-11-05: "a spot for an auto refresh").
                Ui.flashWindow(app.previewFrame.getRootView(), afterSplash ? "after the splash" : "back on the drawing screen", restore, true, afterSplash || ghosted && flashes());
                unrefreshed = ghosted = asked = false;
            }
            refreshFastInk();
            // A refresh the page was owed while fast display was paused.
            if (unrefreshed) refreshSoon("fast display connected");
        } catch (RuntimeException | LinkageError error) {
            Log.w(ProbeActivity.TAG, "Using Android drawing presentation", error);
            fellBack = true;
            // Connecting again after a failure (reconnectLater): another try, while tries are left.
            if (recovery.failures() > 0) reconnectLater();
        }
    }
    /**
     * Back from another screen (the Brush Lab, the Tools page, the notebook screens): what it showed straight to the panel
     * stays in the panel's shared plane, which the system's own refresh shows again, and the page's session only sends
     * what changed. So the next connect blanks the fast plane and gives the whole window one clean update ({@link
     * Ui#cleanWindow}). The owner, 2026-10-24: "a persistent eink ghost of the brush studio that is not going away even
     * after the supernote swipe refresh", then "lots of eink ghosts" from the forced grey refresh that replaced it.
     */
    private boolean cleanWindow;
    void returned() { cleanWindow = true; }
    void disconnectDisplay() {
        // Off the panel while it can still be presented; it never outlives the display it was drawn for.
        hideOutline();
        removeCallbacks(retry); removeCallbacks(reconnect); recovery.restart();
        if (previewDirect != null) { previewDirect.close(); previewDirect = null; previewArea.setEmpty(); fastCopy = null; }
        if (endingFast != null) { endingFast.close(); endingFast = null; endingArea.setEmpty(); }
        if (ringDirect != null) { ringDirect.close(); ringDirect = null; ringArea.setEmpty(); }
        if (direct != null) { direct.close(); direct = null; }
        input.disable();
    }
    void present() { flush(true); }
    /**
     * Removes fast ink a closed popup left over the canvas. The Nomad keeps pen-plane
     * (mode 9) pixels through Android redraws and panel refreshes, so blank that plane
     * here and show the canvas again through the gray session once it is connected.
     */
    void eraseFastInk(RectF panelArea) {
        if (panelArea.isEmpty()) return;
        blanked.set(panelArea);
        // Only a panel with a fast pen plane (mode 9, the Nomad's) has one to blank; elsewhere the refresh below is all.
        if (DirectEink.fastBinaryControls()) {
            Rect area = new Rect(); panelArea.roundOut(area);
            Bitmap white = Bitmap.createBitmap(area.width(), area.height(), Bitmap.Config.ARGB_8888);
            white.eraseColor(Color.WHITE);
            DirectEink blank = null;
            try {
                Matrix toPanel = new Matrix(); toPanel.setTranslate(area.left, area.top);
                blank = DirectEink.forPanel(white, toPanel, 1, 9);
                // Not over Zen's open fan, which the fast pen never draws under (its mask, ZenFan#mask).
                java.util.List<int[]> off = new java.util.ArrayList<>();
                java.util.List<RectF> fan = app.zenFanMask();
                if (fan != null) for (RectF m : fan) { Rect r = new Rect(); m.roundOut(r); r.offset(-area.left, -area.top); off.add(new int[]{r.left, r.top, r.right, r.bottom}); }
                for (int[] part : MagnetDrag.minus(new int[]{0, 0, area.width(), area.height()}, off))
                    blank.refresh(white, new Rect(part[0], part[1], part[2], part[3]));
            } catch (RuntimeException | LinkageError error) {
                Log.w(ProbeActivity.TAG, "Fast ink stays until the canvas redraws", error);
            } finally {
                if (blank != null) blank.close();
                white.recycle();
            }
        }
        fastInk.union(panelArea);
        refreshFastInk();
    }
    private void refreshFastInk() {
        if (fastInk.isEmpty() || direct == null) return;
        Matrix fromPanel = new Matrix();
        if (!rasterToPanel().invert(fromPanel)) return;
        RectF mapped = new RectF(fastInk); fromPanel.mapRect(mapped);
        Rect area = new Rect(); mapped.roundOut(area);
        Bitmap source = presented();
        if (!area.intersect(0, 0, source.getWidth(), source.getHeight())) { fastInk.setEmpty(); return; }
        flush(true);
        // Forced: the software pixels are unchanged, only the panel's are stale. Not under an open popup or Zen's fan (the
        // owner, 2026-11-07: the fan blinked away at every page turn; a turn's suspend refreshed the whole page over it).
        for (int[] part : MagnetDrag.minus(new int[]{area.left, area.top, area.right, area.bottom}, covered()))
            if (direct.refresh(source, new Rect(part[0], part[1], part[2], part[3])) < 0) { postDelayed(this::refreshFastInk, 8); return; }
        wrote(area);
        zenFanRim(area);
        fastInk.setEmpty(); fastInkRefreshes++;
    }
    /**
     * Presents a moving preview (a shape) through the Nomad's fast
     * black/white pen path, like the color bar. Gray-mode frames queue on the Nomad, so a
     * preview there trails the pen and leaves copies behind. The canvas raster is binary
     * dots throughout. Fast pixels do not survive a panel refresh, so
     * {@link #endFastPreview} presents the final pixels through {@link #direct}.
     */
    private void beginFastPreview() {
        if (direct == null || previewDirect != null || !DirectEink.fastBinaryControls()) return;
        flush(true);
        try { previewDirect = DirectEink.forView(this, presented(), viewportBitmap == null ? pageToView : new Matrix(), 1, 9); fastPlaneUsed = true; }
        catch (RuntimeException | LinkageError error) { Log.w(ProbeActivity.TAG, "Previews use gray display", error); }
    }
    /** A pen stroke shows through {@link StrokePanel}'s fast update while the pen is down; {@link #endStrokePreview} ends it. */
    private void beginStrokePreview() {
        if (direct == null || previewDirect != null || quietPanel) return;
        flush(true);
        previewDirect = StrokePanel.open(this, presented(), viewportBitmap == null ? pageToView : new Matrix());
        fastPlaneUsed = true;
    }
    /**
     * Ends a pen stroke's fast preview before its last render: the session stops showing, and once the page is rendered
     * ({@code then}) everything the preview showed and the stroke changed goes to the panel through the gray session, so
     * that every pixel the fast update changed is driven again ({@link #endFast}). Nothing to do without a fast preview.
     */
    private void endStrokePreview(Runnable then) {
        // A Spiro's ring went to the panel through its own fast session: it ends with the stroke's, or as one.
        if (ringDirect != null) {
            if (previewDirect == null) previewDirect = ringDirect; else ringDirect.close();
            previewArea.union(ringArea); ringDirect = null; ringArea.setEmpty();
        }
        if (previewDirect == null) { then.run(); flush(true); return; }
        endingFast = previewDirect; previewDirect = null; fastCopy = null;
        endingArea.union(previewArea); previewArea.setEmpty();
        then.run();
        flush(true);
    }
    /**
     * The fast session's end ({@link #endStrokePreview}): what it showed and everything pending, as one area, through
     * {@link DirectEink#endFast}, so the driver drives every pixel the fast update changed, where the final keeps what
     * it showed too (a plain gray update would pass those by, leaving the fast update's ghosts). Busy, it tries again.
     */
    private void endFast() {
        for (int[] p : pending.drain()) endingArea.union(p[0], p[1], p[2], p[3]);
        int result = 0;
        // Not under an open popup or Zen's fan: the area is one box round all the stroke showed, so a Lasso loop beside the
        // fan took its arc and ring with it (the owner, 2026-11-09: "missing chunk from the fan after a two finger delete lasso").
        try {
            if (!endingArea.isEmpty()) for (int[] part : MagnetDrag.minus(new int[]{endingArea.left, endingArea.top, endingArea.right, endingArea.bottom}, covered()))
                if ((result = direct.endFast(endingFast, presented(), new Rect(part[0], part[1], part[2], part[3]))) < 0) break;
        }
        catch (RuntimeException error) {
            Log.w(ProbeActivity.TAG, "Stroke preview's end shown unforced", error);
            queueView(endingArea);
        }
        pace.last = SystemClock.uptimeMillis();
        if (result < 0 && ++retries < 120) { postDelayed(retry, 8); return; }
        // Still busy: shown as any change, the rest left to the next refresh.
        if (result < 0) queueView(endingArea); else { wrote(endingArea); zenFanRim(new Rect(endingArea)); }
        retries = 0;
        endingFast.close(); endingFast = null; endingArea.setEmpty();
        if (!pending.isEmpty()) flush(true);
    }
    private void endFastPreview() {
        if (previewDirect == null) return;
        flush(true); closeFastPreview(); flush(true);
    }
    private void queueView(Rect r) { pending.add(r.left, r.top, r.right, r.bottom); }
    /** Returns to the gray session, queueing everything the fast one showed. */
    private void closeFastPreview() {
        previewDirect.close(); previewDirect = null; fastCopy = null;
        queueView(previewArea); previewArea.setEmpty();
    }
    /** The fast preview's copy of the canvas, black and white; held only while a preview shows. */
    private Bitmap fastCopy;
    private int[] fastRow;
    /**
     * {@code source}'s {@code r} as the fast plane takes it, each pixel its nearer of black and white: the plane refuses
     * a grey, and a refused frame put the rest of a preview on the slow gray mode (2026-11-12: Magnetic Poetry's tiles
     * have soft grey edges, so every drag's first frame failed and the outline trailed the pen). The gray session shows
     * the real greys when the preview ends ({@link #closeFastPreview} queues all it showed).
     */
    private Bitmap blackWhite(Bitmap source, Rect r) {
        if (fastCopy == null || fastCopy.getWidth() != source.getWidth() || fastCopy.getHeight() != source.getHeight())
            fastCopy = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        int left = Math.max(0, r.left), right = Math.min(source.getWidth(), r.right), w = right - left;
        if (w <= 0) return fastCopy;
        if (fastRow == null || fastRow.length < w) fastRow = new int[w];
        for (int y = Math.max(0, r.top), end = Math.min(source.getHeight(), r.bottom); y < end; y++) {
            source.getPixels(fastRow, 0, w, left, y, w, 1);
            for (int i = 0; i < w; i++) fastRow[i] = (fastRow[i] >> 8 & 255) < 128 ? 0xff000000 : 0xffffffff;
            fastCopy.setPixels(fastRow, 0, w, left, y, w, 1);
        }
        return fastCopy;
    }
    /**
     * Finishes the gesture, lays a Drying wash still settling and releases the panel, e.g. before a
     * panel covers the canvas or the app pauses.
     */
    void suspend() { suspend(false); }
    /**
     * {@code staying}: another page on the drawing screen ({@link #showPage}), so only the page's fast ink goes blank. The
     * whole window blanked left the column, the page bar and the Zen button white, as nothing redraws them unless a page
     * turn's flash follows (the owner, 2026-11-03, after the Template eye: "the toolbar disappeared").
     */
    /** The page's fast pen plane (mode 9) has drawn since {@link #suspend} last blanked it: a stroke's preview, a Spiro's ring. */
    private boolean fastPlaneUsed = true;
    private void suspend(boolean staying) {
        layLiveNow();
        words.cancel(); placer.cancel(); text.cancel(); finishStroke(); settleDrying();
        // Leaving the drawing screen: the fast pen plane (mode 9) survives the next screen's redraws, so it goes blank first.
        // Not yet on screen (a first start straight to the shelf): nothing to blank, and no display to map it with.
        // Going to sleep: no blank, or the sleep screen keeps a washed-out page (the owner, 2026-11-02: "much lighter... as if
        // there was a flash"); waking, connectDisplay blanks and redraws the whole window anyway.
        boolean sleeping = !app.getSystemService(android.os.PowerManager.class).isInteractive();
        // Staying (a page turned): only once the page's fast pen plane has drawn since it was last blanked; just turning
        // pages left the blank and its forced refresh to run over and over, ~95 panel writes a turn with Zen's fan open.
        if (DirectEink.fastBinaryControls() && app.previewFrame.isAttachedToWindow() && !sleeping && (fastPlaneUsed || !staying)) {
            eraseFastInk(staying ? pageArea() : windowArea());
            fastPlaneUsed = false;
        }
        disconnectDisplay();
    }
    /** This view, the page's room, in panel pixels. */
    private RectF pageArea() {
        RectF page = new RectF(0, 0, getWidth(), getHeight());
        PanelCoordinates.fromView(this).mapRect(page);
        return page;
    }
    /** The whole window in panel pixels. */
    private RectF windowArea() {
        RectF all = new RectF(0, 0, app.previewFrame.getWidth(), app.previewFrame.getHeight());
        PanelCoordinates.fromView(app.previewFrame).mapRect(all);
        return all;
    }
    private void flush(boolean force) {
        if (quietPanel) return;
        if (endingFast != null && direct != null) { removeCallbacks(retry); endFast(); return; }
        if (pending.isEmpty()) return;
        long wait = pace.wait(stroke != null, SystemClock.uptimeMillis());
        if (!force && wait > 0) {
            // A pressure-only event may be the last event for a while.
            // Flush its pixels when the coalescing window ends.
            removeCallbacks(retry);
            postDelayed(retry, wait);
            if (timing.active()) timing.deferred();
            return;
        }
        removeCallbacks(retry);
        if (direct == null) { invalidate(); pending.clear(); return; }
        DirectEink target = previewDirect != null ? previewDirect : direct;
        try {
            java.util.List<int[]> covered = covered();
            while (!pending.isEmpty()) {
                // Inkwash: never under Words' bin, which Android draws over the canvas.
                for (int[] patch : MagnetDrag.minus(pending.first(), covered)) {
                    presenting.set(patch[0], patch[1], patch[2], patch[3]);
                    long presentStart = timing.active() ? System.nanoTime() : 0;
                    long presentNanos = System.nanoTime();
                    int result;
                    try { result = target.present(presented(), presenting); }
                    catch (IllegalStateException grey) {
                        // The fast plane refuses greys: the patch again in black and white, rather than the slow mode for the rest of the preview.
                        if (target != previewDirect) throw grey;
                        result = target.present(blackWhite(presented(), presenting), presenting);
                    }
                    pace.presented(System.nanoTime() - presentNanos, SystemClock.uptimeMillis());
                    if (timing.active()) { timing.present(System.nanoTime() - presentStart, result); target.presentParts(presentParts); timing.presentParts(presentParts); }
                    if (result < 0) {
                        // Every caller's refused present counts (the eraser's ring tries at every frame): the wait is timed.
                        if (!recovery.busy(pace.last)) { postDelayed(retry, 8); return; }
                        throw new IllegalStateException("Display remained busy");
                    }
                    if (target == previewDirect) previewArea.union(presenting);
                    wrote(presenting);
                }
                int[] done = pending.first(); pending.removeFirst(); recovery.shown();
                if (covered != null) for (int[] c : covered) if (done[0] < c[2] && c[0] < done[2] && done[1] < c[3] && c[1] < done[3]) { rimDue.union(done[0], done[1], done[2], done[3]); break; }
            }
            // The patches that met the fan's box (or a popup's), their parts past Zen's fan's round paper skipped with it: put on the panel soon, at most every RIM_MS (the owner, 2026-11-07: "happens with any paint laid around the fan edge"; drying paint writes every frame, so waiting for a pause never came).
            if (!rimDue.isEmpty() && !rimPosted) { rimPosted = true; postDelayed(rimLater, RIM_MS); }
        } catch (RuntimeException error) {
            if (target == previewDirect) {
                // Keep the canvas on the gray session; only the preview loses its speed.
                Log.w(ProbeActivity.TAG, "Fast preview failed", error);
                closeFastPreview(); recovery.restart(); flush(true); return;
            }
            Log.e(ProbeActivity.TAG, "Direct display failed", error);
            disconnectDisplay(); invalidate(); pending.clear();
            // Taking the ring off while disconnecting presents too: a failure there was handled already.
            if (fellBack) return;
            // Android drawing meanwhile: the page gets its full refresh once fast display is back.
            fellBack = true; unrefreshed = true;
            reconnectLater();
        }
    }
    /**
     * Inkwash: the fast display failed; Android drawing (slow on e-ink) shows the page until it connects again by
     * itself, a few times in a row ({@link DisplayRecovery}); it no longer waits for a dialog to close or the app's focus
     * to come back. Said at once, not at the next autosave notice.
     */
    /**
     * Inkwash: one clean, forced refresh of {@code pageArea} of the page alone (a magnet laid, the area its outline
     * crossed), where Words' bin doesn't cover it; a busy display tries again shortly. The page's own refresh, once the
     * pen is away, still follows when anything else changed.
     */
    private void refreshArea(String reason, Rect pageArea, int tries) {
        if (direct == null || display == null || pageArea.isEmpty()) return;
        flush(true);
        RectF mapped = new RectF(pageArea);
        if (viewportBitmap != null) pageToView.mapRect(mapped);
        Rect area = new Rect(); mapped.roundOut(area);
        Bitmap source = presented();
        if (!area.intersect(0, 0, source.getWidth(), source.getHeight())) return;
        for (int[] part : MagnetDrag.minus(new int[]{area.left, area.top, area.right, area.bottom}, covered()))
            if (direct.refresh(source, new Rect(part[0], part[1], part[2], part[3])) < 0) {
                if (tries < 120) postDelayed(() -> refreshArea(reason, pageArea, tries + 1), 8);
                return;
            }
        wrote(area);
        zenFanRim(area);
        Log.i(ProbeActivity.TAG, "Area refresh (" + reason + "): " + area);
    }
    /**
     * Inkwash: what a page turn leaves alone on the panel, raster {left, top, right, bottom} each: Zen Mode's open fan's
     * shape ({@link ZenFan#mask}), so it stays as it is, no blink (the owner, 2026-11-07); null with the fan closed.
     */
    private java.util.List<int[]> zenFanKept() {
        java.util.List<RectF> fan = app.zenFanMask();
        Matrix fromPanel = new Matrix();
        if (fan == null || !rasterToPanel().invert(fromPanel)) return null;
        java.util.List<int[]> kept = new java.util.ArrayList<>();
        for (RectF mapped : fan) {
            fromPanel.mapRect(mapped);
            Rect area = new Rect(); mapped.roundOut(area);
            kept.add(new int[]{area.left, area.top, area.right, area.bottom});
        }
        return kept;
    }
    /**
     * Inkwash: after a page turn that kept off Zen's open fan ({@link #zenFanKept}), the page put on the panel where the
     * mask's steps reach past the fan's round paper (the owner, 2026-11-07: the old page's corners stayed there in steps
     * round the arc): each step's parts beyond the paper's disc, from the page with the fan and what lies over it drawn
     * in, so the fan's own pixels there go out unchanged. Always refreshed, every pixel driven: a session opened on {@code shown} takes it as already on the panel, so a plain present sent nothing (the owner, 2026-11-08: the steps after painting went only when the fan reopened); ghosts there go too. Only
     * within {@code within} (raster; null: anywhere).
     */
    private final Rect rimDue = new Rect();
    private static final long RIM_MS = 300;
    private boolean rimPosted;
    private final Runnable rimLater = () -> { rimPosted = false; Rect due = new Rect(rimDue); rimDue.setEmpty(); zenFanRim(due); };
    /** As {@link #zenFanRim(Rect)}, everywhere: after the fan opens, Android's update of its box puts its own stale page past the paper (the owner, 2026-11-07: "a blocky area around the fan edge"). */
    void zenFanRim() { zenFanRim(null); }
    private void zenFanRim(Rect within) {
        java.util.List<RectF> mask = app.zenFanMask();
        float[] paper = app.zenPaper();
        ZenFanView fan = app.zenFanView();
        Matrix fromPanel = new Matrix();
        if (mask == null || paper == null || fan == null || !rasterToPanel().invert(fromPanel)) return;
        float cx = paper[0], cy = paper[1], r = paper[2];
        java.util.List<Rect> parts = new java.util.ArrayList<>();
        for (RectF m : mask) {
            // The disc's width at the band's row nearest its edge: the band beyond that, either side, is past the paper.
            float dy = Math.max(Math.abs(m.top - cy), Math.abs(m.bottom - cy));
            float half = dy >= r ? 0 : (float) Math.sqrt(r * r - dy * dy);
            for (RectF side : new RectF[]{new RectF(m.left, m.top, Math.min(m.right, cx - half), m.bottom), new RectF(Math.max(m.left, cx + half), m.top, m.right, m.bottom)}) {
                if (side.width() <= 0 || side.height() <= 0) continue;
                fromPanel.mapRect(side);
                Rect area = new Rect(); side.roundOut(area);
                if (area.intersect(0, 0, presented().getWidth(), presented().getHeight()) && (within == null || area.intersect(within))) parts.add(area);
            }
        }
        if (parts.isEmpty()) return;
        Bitmap shown = presented().copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(shown);
        Matrix toRaster = PanelCoordinates.fromView(fan);
        toRaster.postConcat(fromPanel);
        canvas.concat(toRaster);
        canvas.clipRect(0, 0, fan.getWidth(), fan.getHeight());
        fan.drawWithOver(canvas);
        DirectEink rim = null;
        try {
            rim = DirectEink.forView(this, shown, viewportBitmap == null ? pageToView : new Matrix(), 0, 7);
            for (Rect area : parts) if (rim.refresh(shown, area) >= 0) wrote(area);
        } catch (RuntimeException | LinkageError error) { Log.w(ProbeActivity.TAG, "The fan's rim stays as it was", error); }
        finally { if (rim != null) rim.close(); shown.recycle(); }
    }
    /** Inkwash: what every open popup (a menu, the tray, Words' bin) and Zen's open fan cover in the presented raster, {left, top, right, bottom} each, or null. */
    private java.util.List<int[]> covered() {
        // The fast display writes under Android's views: off every open popup ({@link Popups#covering}), and off Zen's
        // open fan, its shape in steps, so strokes beside it still show (drying paint took bits of it); closing either
        // shows what it covered.
        java.util.List<RectF> panel = Popups.covering();
        java.util.List<RectF> fan = app.zenFanMask();
        if (fan != null) panel.addAll(fan);
        if (panel.isEmpty()) return null;
        Matrix fromPanel = new Matrix();
        if (!rasterToPanel().invert(fromPanel)) return null;
        java.util.List<int[]> covered = new java.util.ArrayList<>();
        for (RectF mapped : panel) {
            fromPanel.mapRect(mapped);
            Rect area = new Rect(); mapped.roundOut(area);
            covered.add(new int[]{area.left, area.top, area.right, area.bottom});
        }
        return covered;
    }
    /** The presented raster's pixels to panel pixels. */
    private Matrix rasterToPanel() {
        Matrix toPanel = new Matrix(viewportBitmap == null ? pageToView : new Matrix());
        toPanel.postConcat(PanelCoordinates.fromView(this));
        return toPanel;
    }
    /** Panel pixels the page wrote since Zen's button was last put back ({@link #zenBack}). */
    private final RectF zenWritten = new RectF();
    private final Runnable zenBack = this::zenBack;
    /**
     * Every write of the page's pixels to the panel ({@code area}, presented raster) comes here: in Zen Mode the button lies
     * over the page and the fast display writes under Android's views, so a stroke, the eraser's ring, wet paint, a sharp
     * tile or a cleaned popup took bits of the circle (the owner, 2026-11-05: "disappears a bit"). It is put back shortly.
     */
    private void wrote(Rect area) {
        if (!app.zen) return;
        RectF panel = new RectF(area); rasterToPanel().mapRect(panel);
        if (zenWritten.isEmpty()) postDelayed(zenBack, CLOSED_MS);
        zenWritten.union(panel);
    }
    /** Zen's button drawn again where the page wrote ({@link PaintActivity#redrawZen}: no flash); not under the pen's stroke or a popup, so later. */
    private void zenBack() {
        if (zenWritten.isEmpty()) return;
        if (stroke != null || previewDirect != null || endingFast != null || app.popupOpen()) { postDelayed(zenBack, CLOSED_MS); return; }
        app.redrawZen(zenWritten); zenWritten.setEmpty();
    }
    private void reconnectLater() {
        long delay = recovery.failed();
        if (delay < 0) { app.message("Screen stuck: open a menu"); return; }
        if (recovery.failures() == 1) Log.w(ProbeActivity.TAG, "Fast display paused; reconnecting");
        removeCallbacks(reconnect); postDelayed(reconnect, delay);
    }
    private void reconnect() {
        if (direct != null) return;
        // Never under the pen or mid-pinch: try again shortly.
        if (pointer != -1 || stroke != null || navigating) { postDelayed(reconnect, DisplayRecovery.RECONNECT_MS); return; }
        // Covered, paused or laid out meanwhile, the path that ends that connects it.
        connectDisplay();
    }

    // Auto-refresh

    /*
     * Fast partial updates leave ghosts on the panel. Instead of a refresh button, the page gets one
     * full refresh at natural pauses, only if it changed since the last one: when the pen has been
     * out of hover range for PEN_AWAY_MS after drawing, when a menu or the tool panel closes, after an
     * undo or redo of a large area, and when fast display reconnects with a refresh still owed.
     *
     * Which refresh: the driver interface the app knows (direct_eink.c, docs/DIRECT_GRAY.md) is the
     * /dev/ebc region update with mode 7 (gray, flags 0 or 1) and mode 9/flags 1 (binary pen plane).
     * No full-clean GC/GC16 waveform is exposed or identified; the one other mode tried, 4 (GL), drew
     * almost nothing on the Manta and was removed, and the docs rule out guessing new waveform
     * numbers. So this is the fallback: DirectEink.refresh over the whole page, a forced mode 7
     * update that redrives every pixel even where the software pixels are unchanged (as
     * refreshFastInk does for stale fast ink), plus an Android invalidate of the bar.
     */
    /** How long the pen stays out of hover range before the page refreshes. */
    static final long PEN_AWAY_MS = HoverNib.AWAY_MS;
    /** How long after a popup closes the page refreshes, once Android has redrawn under it. */
    static final long CLOSED_MS = 300;
    /** The pen landed on the page with Zen's fan open: it closes when the pen lifts. */
    private boolean fanClosesOnLift;
    /** An undo or redo re-rendering at least this share of the page refreshes it. */
    static final float LARGE_CHANGE = .1f;
    /** The page changed on the panel since its last full refresh. */
    private boolean unrefreshed;
    /**
     * Inkwash: a pinch, a pan or the eraser ran grey updates over pixels still changing, leaving them part-driven:
     * ghosts the forced grey refresh passes by (the driver skips a pixel asked for the value it records). The next
     * auto-refresh draws the page again in the firmware's own clean update instead ({@link Ui#cleanWindow}), its blink
     * once, after the gesture (the owner, 2026-10-25: ghosts while zooming, erased marks showing again).
     */
    private boolean ghosted;
    /**
     * How long after a pinch or pan ends (the last finger up) the page flashes, so a few quick adjustments flash once:
     * two fingers landing again restart the wait. The owner, 2026-10-26: "Can flash happen a bit sooner after zoom?",
     * then "a tad longer" after 500 ms.
     */
    static final long NAVIGATED_MS = 800;
    private int refreshRetries;
    private final Runnable penAwayRefresh = () -> {
        // No hover for a while, even without a hover exit: the pen has gone, and its ring with it.
        if (stroke == null) hideOutline();
        penArrives = true;
        // Only a ghosted page (the eraser's) is cleaned at this pause: the forced grey pass drives nothing on the Manta
        // and held the main thread and the panel 0.5-1.2 s, the stalls at the next stroke's start (the owner, 2026-10-25).
        if (ghosted) autoRefresh("pen out of range " + PEN_AWAY_MS + " ms");
    };
    private Runnable pendingRefresh;
    /**
     * Another page shown (turned to, added, opened): the forced grey pass once drawn, no flash, as with Automatic Refresh
     * off (the owner, 2026-11-05, trying it: flashes "remind you that you're using a piece of tech"; was "Flash on page
     * change", 2026-10-26). A page ghosted before the turn (the eraser, a pinch) still flashes. Refresh clears the rest.
     */
    void pageTurned() { unrefreshed = true; refreshSoon("page changed"); }
    /** One flash ({@link Ui#flashWindow}) once the page is drawn again: a page change, Zen Mode on or off. */
    void flashSoon(String reason) { ghosted = unrefreshed = true; refreshSoon(reason); }
    /** As {@link #flashSoon}, asked for (Refresh, or a finger tap set to it): it flashes with Automatic Refresh off too. */
    void refreshAsked(String reason) { asked = true; flashSoon(reason); }
    /** A flash asked for and not yet made. */
    private boolean asked;
    /** Whether the flash due may be made ({@link SystemSettings#flashes}): asked for, or Automatic Refresh on. */
    private boolean flashes() { return SystemSettings.flashes(asked, app.prefs.autoRefresh()); }

    /**
     * The pen hovering over the window or leaving it: every hover event restarts the pen-away wait,
     * so the page refreshes once hover events stop, even if the pen left without a hover exit.
     */
    void penHover(MotionEvent event) {
        if (!isPen(event, 0)) return;
        removeCallbacks(penAwayRefresh); postDelayed(penAwayRefresh, PEN_AWAY_MS);
    }
    /** A menu or list over the page closed: the pixels it covered changed on the panel. */
    void popupClosed(String name) { unrefreshed = true; refreshSoon(name + " closed"); }
    /**
     * A popup that left fast display running closed over {@code panelArea} (panel pixels). Besides the
     * refresh, the page is shown again where it was once Android has redrawn there, even if the pen is
     * already drawing and the full refresh must wait; with no popup open over it by then. Its area is
     * blanked to white first and then drawn again ({@link #eraseFastInk}, as the Layers panel's close
     * does), so its ink leaves no ghost (the owner, 2026-10-22: "popout menu for erase often leaves a
     * ghost after erasing that requires multiple system screen refreshes to clear").
     */
    void uncovered(String name, RectF panelArea) {
        popupClosed(name);
        cleanArea(panelArea);
    }
    /** As {@link #uncovered} without the page's full refresh after: {@code panelArea} alone is blanked and shown again. */
    void cleanArea(RectF panelArea) {
        fastInk.union(panelArea);
        RectF area = new RectF(panelArea);
        postDelayed(() -> {
            if (app.popupOpen()) return;
            // The page's pixels go over Zen's button there too: wrote() puts it back, whatever Automatic Refresh is.
            boolean cleans = SystemSettings.flashes(false, app.prefs.autoRefresh());
            // Automatic Refresh off: only the page's part is blanked, as no clean follows to draw the bars back (the owner,
            // 2026-11-10: the menu "cut away where it overlapped the toolbar").
            RectF blank = new RectF(area);
            if (direct != null && (cleans || blank.intersect(pageArea()))) eraseFastInk(blank); else refreshFastInk();
            // Automatic Refresh off (System Settings): the closed popup's patches are not cleaned either.
            if (!cleans) return;
            cleanOffPage(area);
        }, CLOSED_MS);
    }
    /**
     * Inkwash: what a closed popup covered beside the page (the side bar, the brush line, the page bar; all of it while
     * the page has no fast display, the notebook screens over it) gets the clean pass from the window as Android now
     * draws it ({@link SelectionFeedback#clean}), so a menu leaves no ghost anywhere (the owner, 2026-10-23).
     */
    private void cleanOffPage(RectF panelArea) {
        // The window's whole view: the notebook screens lie over the drawing screen's root, beside it.
        View window = app.previewFrame;
        Matrix fromPanel = new Matrix();
        if (!PanelCoordinates.fromView(window).invert(fromPanel)) return;
        Rect area = new Rect(), shown = new Rect(); panelArea.roundOut(area); pageArea().roundOut(shown);
        int[] covered = direct != null ? new int[]{shown.left, shown.top, shown.right, shown.bottom} : null;
        for (int[] part : MagnetDrag.minus(new int[]{area.left, area.top, area.right, area.bottom}, covered)) {
            RectF inRoot = new RectF(part[0], part[1], part[2], part[3]);
            fromPanel.mapRect(inRoot);
            Rect patch = new Rect(); inRoot.roundOut(patch);
            if (patch.intersect(0, 0, window.getWidth(), window.getHeight())) app.selectionFeedback.clean(window, patch);
        }
    }
    /** After an undo or redo: refreshes if it re-rendered a large part of the page. */
    void refreshAfterLargeChange(String reason) {
        if (document != null && renderedArea >= LARGE_CHANGE * document.width * document.height)
            refreshSoon(reason + " of " + renderedArea + " px");
    }
    private void refreshSoon(String reason) { refreshSoon(reason, CLOSED_MS); }
    private void refreshSoon(String reason, long delay) {
        if (pendingRefresh != null) removeCallbacks(pendingRefresh);
        pendingRefresh = () -> { pendingRefresh = null; autoRefresh(reason); };
        postDelayed(pendingRefresh, delay);
    }
    /** One full, clean refresh of the page and the bar, if the page changed since the last one. */
    private void autoRefresh(String reason) {
        // Never under the pen, mid-pinch, under an open popup or a dialog: the change stays pending for the next pause.
        if (!unrefreshed || pointer != -1 || stroke != null || words.dragging() || placer.dragging() || text.dragging() || navigating || display == null || app.popupOpen() || !hasWindowFocus()) {
            refreshRetries = 0; return;
        }
        // Automatic Refresh off (System Settings) and not asked for: no refresh of any kind, not even the forced grey pass.
        // Zen's button still put back: a page turn or a pinch presented the page's pixels over it (no refresh follows to redraw it).
        if (!flashes()) { refreshRetries = 0; unrefreshed = ghosted = false; app.redrawZen(pageArea()); return; }
        flush(true);
        // Fast display paused (a panel over the canvas, the app resuming): an Android redraw would not reach
        // the panel, so keep the refresh pending; connectDisplay asks for it again once fast display is back.
        // Fallen back to Android drawing, an Android redraw is the refresh.
        if (direct == null && !DirectEink.androidDrawing() && !fellBack) { refreshRetries = 0; return; }
        boolean clean = direct != null && ghosted;
        if (clean) {
            // The page drawn again in the firmware's default update, every pixel driven (not the forced grey pass, which
            // drives nothing here and held the main thread and the panel about 0.6 s); partial grey back after it.
            // With the firmware's full refresh too: the clean update alone left the big ghosts (the owner, 2026-10-25).
            Ui.flashWindow(this, reason, () -> { if (direct != null) input.partialGrey(); zenFanRim(null); });
            fastInk.setEmpty();
        } else if (direct != null) {
            Bitmap source = presented();
            // Inkwash: all of the page but what Words' bin covers.
            int refreshed = 0;
            for (int[] part : MagnetDrag.minus(new int[]{0, 0, source.getWidth(), source.getHeight()}, covered()))
                if ((refreshed = direct.refresh(source, new Rect(part[0], part[1], part[2], part[3]))) < 0) break;
            if (refreshed < 0) {
                // A busy display: retry shortly, or leave it for the next pause.
                if (++refreshRetries < 120) postDelayed(() -> autoRefresh(reason), 8); else refreshRetries = 0;
                return;
            }
            zenFanRim(null);
            // The whole page is current on the panel, closed popups' areas too.
            fastInk.setEmpty();
        } else invalidate();
        refreshRetries = 0; unrefreshed = ghosted = asked = false;
        app.inkWashBar.invalidate(); app.invalidateZen();
        Log.i(ProbeActivity.TAG, "Auto-refresh (" + reason + "): " + (clean ? "clean update of the page" : direct != null ? "forced mode 7 over the whole page" : "Android redraw") + " and the bar");
    }

    // Input

    @Override public boolean onTouchEvent(MotionEvent event) {
        // A replay plays on its own copy of the page; the pen or a finger landing ends it and puts the page back, a resting
        // hand doesn't (PageReplay.ends).
        if (app.replay.running()) { if (PageReplay.ends(event)) app.replay.stop(); return true; }
        if (app.loading || document == null
                || !app.resumed || !hasWindowFocus()) return true;
        // Inkwash: a picture being placed (Import Image) takes every touch, the pen's and the fingers', until Place or Cancel.
        if (placer.touch(event)) return true;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && isPen(event, 0)) {
            layLiveNow();
            // The pen on the page closes Zen's open fan (the owner, 2026-11-10; it stayed open while drawing from 2026-11-06)
            // once it lifts: closed as the stroke began, the clean of the fan's area broke the line still being drawn.
            // System Settings' Zen Fan, Stays Open, keeps it.
            fanClosesOnLift = app.zenFanOpen() && !app.prefs.zenFanStays();
        }
        if (fanClosesOnLift && (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
            fanClosesOnLift = false;
            post(app::closeZenFan);
        }
        // The pen on the page, landing or lifting: seen now (navigation, finger taps and the finger tool wait on it).
        if (!Ui.fingersOnly(event)) penSeen = SystemClock.uptimeMillis();
        if (historyTap(event)) return true;
        // Inkwash: with the Text tool taken up, the pen works the text boxes and draws nothing.
        if (text.touch(event)) return true;
        // Inkwash: the pen landing on a magnet moves it; with Words open the pen works only the magnets, and fingers draw nothing.
        if (words.touch(event, pointer == -1 && pickPointer == -1 && !dropperOn && shapeStroke == null)) return true;
        // One finger is the finger tool's; a second takes back what it drew, then two may pinch and pan (Zoom unlocked).
        boolean finger = fingerStroke(event);
        if (navigationGesture(event) || finger) return true;
        if (dropperOn || pickPointer != -1) { sampleShadeGesture(event); return true; }
        if (shapeStroke != null) { continueShape(event); return true; }
        if (selectionHandle != FloatingSelection.NONE) { continueSelection(event); return true; }
        long inputStart = System.nanoTime();
        long eventAge = Math.max(0, SystemClock.uptimeMillis() - event.getEventTime());
        boolean drawingInput = false;
        int action = event.getActionMasked(), index = event.getActionIndex();
        if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)
                && pointer == -1 && isPen(event, index)) {
            if (!penDown(event, index)) return true;
            drawingInput = stroke != null;
            if (!drawingInput) return true;
        } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_POINTER_UP) {
            int p = event.findPointerIndex(pointer);
            if (p >= 0 && stroke != null && (action == MotionEvent.ACTION_MOVE || event.getPointerId(index) == pointer)) {
                drawingInput = true;
                for (int h = 0; h < event.getHistorySize(); h++) sampleEvent(event, p, h);
                if (action == MotionEvent.ACTION_MOVE) sampleEvent(event, p, -1);
                else app.live.lifted = event.getEventTime();
            }
        }
        if (drawingInput && batching()) {
            // The samples wait in the batch: drawn once the events queued behind this one are in too.
            if (timing.active()) timing.event(eventAge);
            if (!batchPosted) { batchPosted = true; post(drawBatch); }
        } else {
            presentInput(drawingInput, eventAge);
            if (drawingInput && app.inkWash != null) app.inkWash.session.input(System.nanoTime() - inputStart, eventAge);
            if (drawingInput) app.live.drawing();
        }
        if (action == MotionEvent.ACTION_CANCEL || pointerUp(event, pointer) && !liftQuick()) finishStroke();
        return true;
    }
    /**
     * The pen lifted from a stroke whose work after lift runs off this thread ({@link LiftingStroke}: a quick stroke's
     * render, a Drying stroke's settle): its last samples shown, that work runs ({@link QuickLift}), ending in
     * {@link #finishStroke}. False for any other stroke.
     */
    private boolean liftQuick() {
        if (!(stroke instanceof LiftingStroke) || !((LiftingStroke)stroke).developsAfterLift()) return false;
        flushBatch(); inkPen = false;
        presentInput(false, -1);
        if (!quickLift.start(stroke)) return false;
        pointer = -1;
        getParent().requestDisallowInterceptTouchEvent(false);
        return true;
    }
    /** A resting pen's last sample, sent again to a stroke that builds while held ({@link HeldPen}). */
    private final HeldPen heldPen = new HeldPen();
    private final Runnable heldTick = this::heldTick;
    /** While the pen is down on a stroke that builds while held: a quiet pen's last sample again, then shown. */
    private void heldTick() {
        if (stroke == null || pointer == -1 || !stroke.buildsWhileHeld()) { heldPen.clear(); return; }
        long now = SystemClock.uptimeMillis();
        if (heldPen.due(stroke, now)) { flushBatch(); heldPen.resend(stroke, now); presentInput(false, -1); }
        postDelayed(heldTick, HeldPen.TICK_MS);
    }
    /** Hold to Snap ({@link HoldSnap}): the pen's slot stroke, watched for a hold at its end; null for a stroke that doesn't snap. */
    private HoldSnap holdSnap;
    /** The slot whose stroke {@link #holdSnap} watches. */
    private int snapTool;
    /** The stroke snapped: the rest of the touch draws nothing more, and the lift finishes the clean stroke. */
    private boolean snapped;
    /** A snapped shape the pen moved off ({@link #dragSnap}): the Shapes preview shows it following, the lift draws it ({@link #liftSnapDrag}). */
    private HoldSnap snapDrag;
    /** While a snap draws again, nothing goes to the panel: the rough stroke's end and Undo and the clean stroke show as one update. */
    private boolean quietPanel;
    private final Runnable snapTick = this::snapTick;
    /**
     * Whether the pen's stroke just begun may snap: a slot's brush or Fill in's, the pen's tip, a plain stroke (not a mirror being
     * placed, the lasso, a Spiro's ring or a refused touch), and not one whose hold already means something (Build-up).
     */
    private boolean snaps(boolean eraserEnd) {
        int tool = app.inkWash.selected;
        return !eraserEnd && (tool < ToolSlots.COUNT || tool == ToolSlots.FILL) && app.inkWash.brush(tool) != null && ringPrefix == null && stroke != null
                && stroke != REFUSED && !(stroke instanceof RingSetup) && !(stroke instanceof MirrorShow.Place) && !stroke.buildsWhileHeld();
    }
    /** While the pen is down on a stroke that may snap: held still {@link HoldSnap#HOLD_MS} over a clean shape, it snaps. */
    private void snapTick() {
        if (holdSnap == null || stroke == null || pointer == -1) return;
        double[][] clean = holdSnap.due(SystemClock.uptimeMillis()) ? holdSnap.snap() : null;
        if (clean == null) { postDelayed(snapTick, HoldSnap.TICK_MS); return; }
        snapTo(clean);
    }
    /**
     * The rough stroke given back and drawn again along {@code clean} with the same slot: finished, then undone, as a
     * finger's stroke is taken back ({@link #cancelFingerStroke}: a quick render or wet paint can't be dropped mid-way),
     * the wet paint forgetting it ({@link WatercolorSession#takeBack}), so the page keeps one Undo step, the clean
     * shape's. The pen stays down; its lift finishes the clean stroke as any. The panel sees one update, at the end.
     */
    private void snapTo(double[][] clean) {
        int id = pointer, tool = snapTool;
        HoldSnap snap = holdSnap;
        quietPanel = true;
        try {
            takeBackStroke();
            drawAgain(clean, tool, id);
        } finally { quietPanel = false; }
        flush(true);
        snapped = true;
        // The shape may yet follow the pen (dragSnap).
        holdSnap = snap;
    }
    /** The pen's stroke finished and taken back ({@link WatercolorSession#takeBack}), the page shown without it on the next update. */
    private void takeBackStroke() {
        finishStroke();
        if (finishedChanged) app.inkWash.session.takeBack(document);
        renderDirty();
    }
    /** {@code clean} ({@link HoldSnap}'s rows) drawn by slot {@code tool}'s brush under pointer {@code id}, the stroke left open for the lift. */
    private void drawAgain(double[][] clean, int tool, int id) {
        // Where the stroke began, which was allowed (a locked section's box is checked at the stroke's start).
        samplePoint[0] = (float) clean[0][0]; samplePoint[1] = (float) clean[0][1];
        beginInkStroke(tool, false, id, "pen");
        inkPen = true;
        for (double[] c : clean) feed((float) c[0], (float) c[1], (float) c[2], (float) c[3], (float) c[4], (long) c[5]);
        flushBatch();
        presentInput(false, -1);
    }
    /**
     * The pen moved off a snapped shape, still down: the brush's shape is taken back as the rough stroke was, and the
     * shape following the pen ({@link HoldSnap#follow}) shows as a Shapes shape does ({@link ShapePreview}, the panel's
     * fast update, the slot's size and grey; {@link #continueShape}). Redrawing the brush at every move is too slow for
     * the panel (a wet brush's whole stroke each time); the lift draws it once ({@link #liftSnapDrag}).
     */
    private void dragSnap() {
        HoldSnap snap = holdSnap;
        int id = pointer, tool = snapTool;
        float x = samplePoint[0], y = samplePoint[1];
        quietPanel = true;
        try { takeBackStroke(); } finally { quietPanel = false; }
        snapDrag = snap; pointer = id;
        getParent().requestDisallowInterceptTouchEvent(true);
        ToolSwatches.Look look = app.inkWash.look(tool);
        int width = DrawLayout.shapeOutline(shapes, look.px).outlineWidth, page = document.width;
        samplePoint[0] = x; samplePoint[1] = y;
        beginShape(new ShapePreview(document, ToolSwatches.tone(look.darkness), ShapeGeometry.PATH_ROW, (px, py, spans) -> {
            HoldSnap.Shape s = snap.follow(px, py);
            ShapeGeometry.path(s.x, s.y, width, page, spans);
        }));
    }
    /** The pen lifted from a dragged snapped shape ({@link #dragSnap}): the preview gone and the shape drawn by the brush where it lifted, one update and one Undo step, as a snap. */
    private void liftSnapDrag() {
        HoldSnap snap = snapDrag;
        int id = pointer;
        snapDrag = null;
        quietPanel = true;
        try {
            endFastPreview();
            renderShape(shapeStroke.cancel(display, viewportBitmap != null)); shapeStroke = null;
            renderShapeShown();
            drawAgain(snap.to(shapeX, shapeY), snapTool, id);
        } finally { quietPanel = false; }
        if (!liftQuick()) finishStroke();
    }
    /** Whether the pen stroke in progress gathers its samples in {@link #penBatch}: Inkwash's. */
    private boolean batching() { return penBatching && inkPen && stroke != null; }
    /**
     * Draws the gathered samples: the stroke takes them all, then the page is rendered and presented once. Posted by
     * the first event after a draw, so it runs after every input event already queued has added its samples.
     */
    private void drawBatch() {
        batchPosted = false;
        if (stroke == null || penBatch.isEmpty()) return;
        long start = System.nanoTime(), oldest = penBatch.oldest(), age = Math.max(0, SystemClock.uptimeMillis() - oldest);
        int n = penBatch.feed(stroke);
        if (timing.active()) timing.samples(n, System.nanoTime() - start);
        presentInput(true, -1);
        if (timing.active()) timing.batch(n, Math.max(0, SystemClock.uptimeMillis() - oldest));
        // How late the pen's input is drawn, and how long drawing took: what paces settling.
        app.inkWash.session.input(System.nanoTime() - start, age);
        app.live.drawing();  // Live pages: the stroke so far to the guests, every LiveChange.PART_MS; after the timing that paces settling
    }
    /** Hands every gathered sample to the stroke before it finishes or another gesture needs the page. */
    private void flushBatch() {
        removeCallbacks(drawBatch); batchPosted = false;
        if (stroke == null || penBatch.isEmpty()) { penBatch.clear(); return; }
        long start = System.nanoTime();
        int n = penBatch.feed(stroke);
        if (timing.active()) timing.samples(n, System.nanoTime() - start);
    }
    /** Renders and presents what the input changed, timing it as one event of the stroke in progress ({@code eventAge} −1: a batch, timed by {@link #drawBatch}). */
    private void presentInput(boolean counted, long eventAge) {
        if (stroke != null) stroke.present();
        followEraser();
        // A Spiro's ring pulled out goes through its own fast session, with anything the stroke laid meanwhile (its lift
        // drives those pixels again, endStrokePreview).
        if (ringMoved) { ringMoved = false; if (ringDirect != null && previewDirect == null) { presentRing(); return; } }
        if (!counted || !timing.active()) { renderDirty(); flush(false); return; }
        long start = System.nanoTime();
        renderDirty();
        long rendered = System.nanoTime();
        flush(false);
        long flushed = System.nanoTime();
        timing.render(rendered - start, renderedArea); timing.flush(flushed - rendered);
        if (eventAge >= 0) timing.event(eventAge);
    }
    /**
     * Starts the selected tool's gesture at a pen-down inside the page.
     * @return false if the pen-down was refused
     */
    private boolean penDown(MotionEvent event, int index) {
        pagePoint(event.getX(index), event.getY(index));
        if (selection != null) {
            float[] knob = selectionKnob();
            int handle = selection.hit(samplePoint[0], samplePoint[1], knob, dp(24) / pageScale());
            if (handle != FloatingSelection.NONE) { grabSelection(event, index, handle); return true; }
            placeSelection();
        }
        if (samplePoint[0] < 0 || samplePoint[1] < 0
                || samplePoint[0] >= document.width || samplePoint[1] >= document.height) return false;
        if (shapesOn && app.inkWash != null && app.inkWash.selected < ToolSlots.COUNT && placing == Placing.NONE && !lassoOn && !eraserEnd(event, index)) return inkShape(event.getPointerId(index));
        boolean end = eraserEnd(event, index);
        beginInkStroke(end ? InkWash.ERASER : app.inkWash.selected, end, event.getPointerId(index), "pen");
        timing.down(event.getEventTime());
        inkPen = true;
        holdSnap = snaps(end) ? new HoldSnap(viewport.scale()) : null; snapTool = app.inkWash.selected;
        if (holdSnap != null) { removeCallbacks(snapTick); postDelayed(snapTick, HoldSnap.TICK_MS); }
        // A ring set up is shown already.
        boolean set = ringPrefix != null;
        sampleEvent(event, index, -1);
        if (!set) ringGuide(penSample.pressure, penSample.tiltX, penSample.tiltY);
        if (stroke.buildsWhileHeld()) { removeCallbacks(heldTick); postDelayed(heldTick, HeldPen.TICK_MS); }
        return true;
    }
    /** Record and Replay ({@link StrokeLog}): the samples of the stroke being drawn (the pen's or a finger's, a brush's or the eraser's), or null. */
    private StrokeLog.Recorder recording;
    /** The stroke being drawn's log entry so far; null for none (Live pages: its parts' kind, LiveProbe.drawing). */
    StrokeLog.Recorder recording() { return recording; }
    /** Shows what a replayed stroke changed ({@link PageReplay}), at once. */
    void replayShow() { renderDirty(); flush(true); }
    /** Starts tool {@code tool}'s stroke (a slot, the eraser or the smudge tool) under pointer {@code id}, the pen ({@code eraserEnd}: its eraser end) or a finger ({@code kind}, for the timing log). */
    private void beginInkStroke(int tool, boolean eraserEnd, int id, String kind) {
        layLiveNow();
        // A stroke still working after lift finishes first.
        if (stroke != null) finishStroke();
        recording = null;
        // A locked section's box (TimedSections): the pen landing there marks nothing; a stroke crossing it is put back at its finish.
        if ("pen".equals(kind) && placing == Placing.NONE && !lassoOn && document.fenced(samplePoint[0], samplePoint[1])) { app.note(TimedSections.LOCKED); refuse(id); return; }
        // A locked page, No Erasing and One Brush (NotebookRules): the touch is followed to its end and marks nothing.
        if (!app.allowStroke(tool)) { refuse(id); return; }
        // No ring while the pen touches; a finger's eraser follows it from its first sample (followEraser).
        hideOutline();
        document.paintMaskingFluid(false);
        pointer = id;
        getParent().requestDisallowInterceptTouchEvent(true);
        long created = System.nanoTime();
        timing.begin("kind=" + kind + " tool=" + tool + " size=" + app.inkWash.preset(tool), created);
        // Inkwash: the pen's stroke places a mirror, or is Cut Stamp's lasso, rather than drawing. The lasso's marching
        // ants, black on the page's dots, show through the panel's fast update (StrokePanel).
        // The Lasso's loop too (lassoOn), in the same ants; the pen's eraser end still erases.
        if ((placing != Placing.NONE || DrawLayout.lassoLoop(lassoOn, eraserEnd)) && "pen".equals(kind)) {
            dropRing();
            stroke = new MirrorShow.Place(mirrorShow, placing != Placing.LINE);
            timing.cancel();
            if (StrokePanel.wants(stroke)) beginStrokePreview();
            return;
        }
        // A Spiro's ring set up serves the pen's next stroke with its tool; any other stroke drops it.
        RingSetup set = ringSet;
        if (set != null && !("pen".equals(kind) && set.tool == tool)) { dropRing(); set = null; }
        ringSet = null;
        // Scribulor: the pen's first stroke with a Spiro in Ring sets the ring up; the next draws it, the setup first.
        Brush brush = app.inkWash.brush(tool);
        if (set == null && "pen".equals(kind) && brush != null) {
            ToolSwatches.Look look = app.inkWash.look(tool);
            ResolvedBrush.Pattern ring = ResolvedBrush.of(brush.parts, look.px, look.darkness).pattern;
            if (SpiroStamper.ring(ring)) { stroke = new RingSetup(tool, id, ring); timing.cancel(); return; }
        }
        ringPrefix = set;
        // The finger's brush keeps paint of its own (Keeps Paint).
        stroke = app.inkWash.stroke(document, tool, "finger".equals(kind));
        recording = app.inkWash.recorder;
        // Copy Strokes: the copies shown live from the stroke (the smudge tool's only at lift: it moves what is there).
        // A Filled Shape's big fill shows flat at once, before it is laid.
        if (stroke instanceof FilledShape) ((FilledShape) stroke).preview = this::previewFill;
        if (outline(stroke) instanceof MirrorStroke && document.mirrors().copies() && tool != InkWash.SMUDGE)
            mirrorShow.beginCopies(document.mirrors().group(), !(own(stroke) instanceof EraseBothStroke));
        // Zoomed in, the pen's speed is still the hand's on the glass (sizes stay page pixels).
        stroke.zoom(viewport.scale());
        if (recording != null) recording.zoom = viewport.scale();
        timing.created(System.nanoTime() - created);
        // The stroke shows through the panel's fastest black-and-white update while it is drawn (StrokePanel), as
        // shapes' previews do: the page's dots are black or white already. Its end shows it all again through the
        // gray session (endStrokePreview).
        if (StrokePanel.wants(stroke)) beginStrokePreview();
    }

    /**
     * Inkwash: a Spiro in Ring shows its pinned ring in light grey over the page, round the touch-down ({@link
     * #samplePoint}, just sampled), sized by a setup's drag ({@link #ringMoved}) until the stroke that draws it lifts
     * ({@link MirrorShow#ring}, taken away by {@code mirrorShow.end()}), through the fast update ({@link #ringDirect}).
     */
    private void ringGuide(float pressure, float tiltX, float tiltY) {
        float radius = stroke.ringGuide(pressure, tiltX, tiltY);
        if (radius <= 0) return;
        mirrorShow.page(document.width, document.height);
        mirrorShow.ring(samplePoint[0], samplePoint[1], radius, stroke.ringMode());
        // At once through the fast update, as the lasso: a session of its own for the ring alone (the stroke's live
        // marks would be left part-driven by it), ended at the lift so the driver drives its pixels again. Without one
        // (no panel, Android drawing, the bench's fast stroke) the ring goes with the stroke's own updates.
        if (direct == null || previewDirect != null) return;
        flush(true);
        ringDirect = StrokePanel.open(this, presented(), viewportBitmap == null ? pageToView : new Matrix());
        fastPlaneUsed = true;
        if (ringDirect != null) presentRing();
    }
    /**
     * Scribulor: a Spiro in Ring's setup, the pen's first stroke with it (the owner, 2026-10-26, as a shape is drawn): its
     * touch-down pins the centre (an Oval's or Bar's first corner), the drag sizes the ring (the track, corner to corner), the guide following ({@link MirrorShow#ringTo}), and nothing
     * is inked. At the lift the ring is set ({@link #ringSet}) and its guide stays, for the next pen stroke of the tool,
     * which takes the centre and the rim (no pressure) first ({@link #feed}; {@link SpiroStamper#rim}). A setup that
     * never left the pin is a tap: the whole figure at the brush's size, at once ({@link #tapRing}).
     */
    private final class RingSetup implements DrawingStroke {
        final int tool, id;
        final ResolvedBrush.Pattern ring;
        /** The centre, its pressure, tilt and time; where the pen is (the rim) and its time; how far it has been. */
        float cx = Float.NaN, cy, p, tx, ty, x, y;
        long t;
        double far;
        RingSetup(int tool, int id, ResolvedBrush.Pattern ring) { this.tool = tool; this.id = id; this.ring = ring; }
        @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY) { sample(x, y, pressure, tiltX, tiltY, PenSpeed.UNTIMED); }
        @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY, long time) {
            if (Float.isNaN(cx)) { cx = x; cy = y; p = pressure; tx = tiltX; ty = tiltY; }
            this.x = x; this.y = y; t = time;
            far = Math.max(far, Math.hypot(x - cx, y - cy));
            if (mirrorShow.ringTo(x, y)) ringMoved = true;
        }
        @Override public boolean finish() { return false; }
        @Override public float ringGuide(float pressure, float tiltX, float tiltY) { return (float) SpiroStamper.guide(ring, ToolSettings.travel(pressure), tiltX, tiltY); }
        @Override public int ringMode() { return ring.spiro.mode; }
        boolean tap() { return far < SpiroStamper.PIN; }
    }
    /** A setup that never left the pin: its stroke drawn at once, from its centre to where it lifted, at its touch-down's pressure. */
    private void tapRing(RingSetup set) {
        ringSet = set;
        beginInkStroke(set.tool, false, set.id, "pen");
        if (ringPrefix == set) feed(set.x, set.y, set.p, set.tx, set.ty, set.t);
        // Refused (a rule changed meanwhile): nothing waits.
        ringSet = null;
        finishStroke();
    }
    /** Undo, a tool or page change, or another stroke: a ring set up goes, and its guide. Whether there was one. */
    boolean dropRing() {
        if (ringSet == null) return false;
        ringSet = null;
        if (mirrorShow.end()) { ghosted = unrefreshed = true; refreshSoon("Spiro ring dropped"); }
        renderDirty(); flush(true);
        return true;
    }
    /** What changed, rendered and presented through the ring's fast session ({@link #ringDirect}). */
    private void presentRing() {
        previewDirect = ringDirect;
        renderDirty(); flush(true);
        // A failed present closed it and went back to the gray session.
        if (previewDirect == null) { ringDirect = null; return; }
        ringArea.union(previewArea); previewArea.setEmpty(); previewDirect = null; fastCopy = null;
    }
    /** What a refused touch draws: nothing ({@link PaintActivity#allowStroke}). */
    private static final DrawingStroke REFUSED = new DrawingStroke() {
        @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY) { }
        @Override public boolean finish() { return false; }
    };
    /** The touch of pointer {@code id} is followed to its end and draws nothing. */
    private void refuse(int id) {
        hideOutline();
        pointer = id; stroke = REFUSED;
        getParent().requestDisallowInterceptTouchEvent(true);
    }

    /** The box a big fill's flat preview drew over ({@link #previewFill}), rendered again from the page with the fill; null for none. */
    private int[] fillShown;
    /**
     * A Filled Shape's big area ({@link FilledShape#PREVIEW_PX}) shown at once, flat in its grey, while it is laid and
     * settles (on the Manta, seconds for a wet marker's page): on the panel only, the page untouched, so the fill's own
     * render then replaces it ({@link #fillShown}). The retired bucket's preview.
     */
    private void previewFill(FillRegion.Room room, double darkness) {
        if (display == null) return;
        Rect area = new Rect(room.x0, room.y0, room.x1 + 1, room.y1 + 1);
        if (!area.intersect(0, 0, display.getWidth(), display.getHeight())) return;
        int tone = Math.max(0, Math.min(255, (int)Math.round(255 * (1 - darkness)))), w = area.width(), rows = bandRows(area);
        boolean dots = viewportBitmap == null;
        if (renderPixels.length < w * rows) renderPixels = new int[w * rows];
        for (int top = area.top; top < area.bottom; top += rows) {
            int h = Math.min(rows, area.bottom - top);
            display.getPixels(renderPixels, 0, w, area.left, top, w, h);
            for (int y = top, i = 0; y < top + h; y++) for (int x = area.left, o = (y - room.y0) * room.width + x - room.x0; x < area.right; x++, i++, o++)
                if (room.at[o] != FillRegion.OUT) renderPixels[i] = dots ? DotPattern.pixel(tone, x, y) : 0xff000000 | tone * 0x010101;
            display.setPixels(renderPixels, 0, w, area.left, top, w, h);
        }
        fillShown = new int[]{area.left, area.top, area.right - 1, area.bottom - 1};
        queue(area); flush(true);
    }
    /** A stylus's eraser end, or the pen with its side button held: it erases whatever tool is selected. */
    private static boolean eraserEnd(MotionEvent event, int index) {
        return event.getToolType(index) == MotionEvent.TOOL_TYPE_ERASER || (event.getButtonState() & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0;
    }
    /** The pointer is a stylus tip or eraser end. */
    static boolean isPen(MotionEvent event, int index) {
        int type = event.getToolType(index);
        return type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER;
    }
    /** A pen stroke, shape or fill drag is in progress. */
    boolean penActive() { return pointer != -1; }
    private void pagePoint(float x, float y) {
        samplePoint[0] = x; samplePoint[1] = y;
        if (!unscaledPage) viewToPage.mapPoints(samplePoint);
    }
    @Override public boolean onHoverEvent(MotionEvent event) {
        if (isPen(event, 0)) {
            penSeen = SystemClock.uptimeMillis();
            if (navigating) { touchBlocked = true; endNavigation(); }
            hoverOutline(event);
        }
        return true;
    }
    private void sampleEvent(MotionEvent event, int pointerIndex, int history) {
        // Snapped: the pen held on draws nothing more; moved off its rest, the shape follows it (dragSnap).
        if (snapped) {
            if (history < 0 && holdSnap != null) {
                pagePoint(event.getX(pointerIndex), event.getY(pointerIndex));
                if (holdSnap.moved(samplePoint[0], samplePoint[1])) dragSnap();
            }
            return;
        }
        penSample.read(event, app.physicalPenEvent, pointerIndex, history, landscapeTilt);
        // Positions come in the canvas's axes and are mapped; Supernote's signed tilt degrees stay the panel's, so they turn with the app.
        pagePoint(penSample.x, penSample.y);
        if (app.appRotation != Surface.ROTATION_0) {
            float[] tilt = PageView.turn(app.appRotation, penSample.tiltX, penSample.tiltY);
            penSample.tiltX = tilt[0]; penSample.tiltY = tilt[1];
        }
        heldPen.sampled(samplePoint[0], samplePoint[1], penSample.pressure, penSample.tiltX, penSample.tiltY, penSample.time);
        if (holdSnap != null) holdSnap.sample(samplePoint[0], samplePoint[1], penSample.pressure, penSample.tiltX, penSample.tiltY, penSample.time);
        feed(samplePoint[0], samplePoint[1], penSample.pressure, penSample.tiltX, penSample.tiltY, penSample.time);
    }
    /**
     * One pen sample to the stroke, and recorded. A Spiro's ring set up goes in first ({@link #ringPrefix}): its centre,
     * then its rim with no pressure, two and one {@link StrokeLog#MOST_PAUSE} before, so a replay draws the same.
     */
    private void feed(float x, float y, float pressure, float tiltX, float tiltY, long time) {
        RingSetup set = ringPrefix;
        if (set != null) {
            ringPrefix = null;
            boolean timed = time != PenSpeed.UNTIMED;
            feed(set.cx, set.cy, pressure, tiltX, tiltY, timed ? time - 2 * StrokeLog.MOST_PAUSE : time);
            feed(set.x, set.y, 0, tiltX, tiltY, timed ? time - StrokeLog.MOST_PAUSE : time);
        }
        if (recording != null) recording.add(x, y, pressure, tiltX, tiltY, time);
        // Inkwash: a stroke kept sharp draws the sample as the log keeps it, so drawn again it draws the same (SharpStrokes).
        if (recording != null && recording.keeps) {
            recording.kept(keptSample, keptTime);
            x = keptSample[0]; y = keptSample[1]; pressure = keptSample[2]; tiltX = keptSample[3]; tiltY = keptSample[4]; time = keptTime[0];
        }
        if (batching()) { penBatch.add(x, y, pressure, tiltX, tiltY, time); return; }
        long start = timing.active() ? System.nanoTime() : 0;
        stroke.sample(x, y, pressure, tiltX, tiltY, time);
        if (timing.active()) timing.sample(System.nanoTime() - start);
    }

    private final float[] keptSample = new float[5];
    private final long[] keptTime = new long[1];

    /** Ends or cancels whatever gesture is running, committing finished work. */
    void finishStroke() {
        holdSnap = null; snapDrag = null; snapped = false; removeCallbacks(snapTick);
        // The pen lifted, a tool or size changed, or a panel opens: hovering brings the ring back, worked out again.
        hideOutline(); hoverTool = -1;
        if (navigating) touchBlocked = true;
        endNavigation();
        // Inkwash: a floating piece stays up through menus, dialogs and the app pausing (the page is untouched until it is
        // put down); the pen landing off it, another tool, a page turn ({@link #carrySelection}) or Undo end it.
        if (selectionHandle != FloatingSelection.NONE) releaseSelection();
        if (shapeStroke != null) {
            removeCallbacks(previewFrame); previewFrameScheduled = false;
            renderShape(shapeStroke.cancel(display, viewportBitmap != null)); shapeStroke = null; pointer = -1;
            renderShapeShown(); endFastPreview(); getParent().requestDisallowInterceptTouchEvent(false);
        }
        if (pickPointer != -1) { pickPointer = -1; getParent().requestDisallowInterceptTouchEvent(false); }
        // No stroke to end: a tool or page change, a panel, Undo. A Spiro's ring set up goes.
        if (stroke == null) { dropRing(); return; }
        quickLift.stop();
        flushBatch(); inkPen = false;
        // A finger drawing when something else ends its stroke must not start again from where it landed.
        if (drawFinger != -1 && pointer == drawFinger) fingerRefused = true;
        boolean timed = timing.active();
        // An eraser showed what it leaves where it went; the page shows it now, the rest erased too.
        EraseBothStroke eraser = erasePreview();
        // Or a big fill's flat preview showed where it went: rendered again, the fill or not.
        int[] shownFirst = eraser == null ? fillShown : eraser.erasedBounds();
        fillShown = null;
        // A stump's other half shown over the page: drawn again from the page once the finish has laid it.
        DrawingStroke.Unwritten shown = stroke.unwritten();
        int[] smudged = shown == null ? null : shown.all();
        int[] erased = smudged == null ? shownFirst : around(shownFirst, smudged);
        long finishStart = System.nanoTime();
        DrawingStroke ended = stroke;
        boolean changed = finishedChanged = stroke.finish(); stroke = null; pointer = -1;
        if (own(ended) instanceof EraseBothStroke) ghosted = true;
        // Record and Replay: the stroke, kept with the page; one that changed nothing too (what it laid or picked up counts).
        if (recording != null && recording.keeps) recording.plane = document.selectedAddress();
        if (recording != null) document.logStroke(recording, changed);
        recording = null;
        // Inkwash: the live copies give way to the copies drawn; a placing stroke places. A Spiro's ring set up stays shown.
        RingSetup setup = ended instanceof RingSetup ? (RingSetup) ended : null;
        // The guide gone leaves faint rings where the setup's drag drew it (the owner, 2026-10-26): the pen-away pause flashes.
        if (setup != null && !setup.tap()) ringSet = setup; else if (mirrorShow.end()) ghosted = unrefreshed = true;
        if (ended instanceof MirrorShow.Place) {
            MirrorShow.Place loop = (MirrorShow.Place) ended;
            if (!loop.lasso) app.mirrors.placed(loop);
            else if (placing == Placing.CUT) app.stampCut(loop);
            // Once the loop's ants are off the page.
            else post(() -> { liftLasso(loop); app.edgeHoldAct(app.edgeHold.loopEnded()); });
        }
        long finished = System.nanoTime();
        long[] rendered = new long[1];
        java.util.List<int[]> apart = ended instanceof MirrorStroke ? ((MirrorStroke) ended).areas() : null;
        endStrokePreview(() -> { renderDirty(erased, apart); rendered[0] = System.nanoTime(); });
        long flushed = System.nanoTime();
        if (timed) Log.i(ProbeActivity.TAG, timing.end(flushed, finished - finishStart, rendered[0] - finished, flushed - rendered[0]));
        // A smudge brush that keeps paint may now hold some, or none.
        if (app.inkWashBar != null) app.inkWashBar.heldChanged();
        getParent().requestDisallowInterceptTouchEvent(false);
        if (changed) { drawn(); if (app.inkWashBar != null) app.inkWashBar.laid(); }
        if (app.inkWash != null) dryingTicker().start();
        // Drawing ended: the page refreshes once the pen has been away a while. Hovering cancels this.
        removeCallbacks(penAwayRefresh); postDelayed(penAwayRefresh, PEN_AWAY_MS);
        if (setup != null && setup.tap()) tapRing(setup);
        // No View invalidation or toolbar update at pen-up on the direct path.
    }
    /**
     * Inkwash: Make Ink ({@link TextMode}): text box {@code i} laid by the brush in use (the eraser's or the smudge tool's
     * turn: the first slot's) as one stroke, its letters a fill's room ({@link TextBoxes#ink}, {@link FillTool#print}),
     * the box gone with it: one Undo step that brings the box back. Finished as a stroke is, so a Slow wash dries on and a
     * Later starts its clock.
     */
    void makeInk(int i) {
        if (document == null || i < 0 || i >= document.texts().size()) return;
        if (app.pageLocked()) { app.note(NotebookRules.PAGE_LOCKED); return; }
        int slot = app.inkWash.selected < ToolSlots.COUNT ? app.inkWash.selected : 0;
        if (app.inkWash.brush(slot) == null || !app.allowStroke(slot)) return;
        String vector = VectorInk.shapeRefusal(document, app.inkWash.brush(slot));
        if (vector != null) { app.sayVector(vector); return; }
        finishStroke(); settleDrying();
        FillRegion.Room letters = TextBoxes.ink(document.texts().get(i), document.width, document.height);
        if (letters == null) return;
        ToneDocument page = document;
        Object mark = page.lastStep();
        page.setTexts(page.texts().without(i));
        ModularStroke laid = app.inkWash.ink(page, slot, letters);
        stroke = new DrawingStroke() {
            @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY) { }
            @Override public void present() { laid.present(); }
            @Override public boolean finish() { laid.finish(); page.linkSince(mark); return true; }
        };
        stroke.present();
        finishStroke();
    }
    private DryingTicker dryingTicker() {
        if (drying == null) drying = new DryingTicker(this, app.inkWash.session, new DryingTicker.Host() {
            @Override public ToneDocument page() { return document; }
            // Never under the pen, mid-pinch, paused or without the window; nor
            // while a replay plays its copy, a stroke of it open (2026-11-03: a fade's tick there crashed, "Finish the
            // current gesture first"): it plays with a session of its own, the page's ticks wait for the page.
            @Override public boolean busy() { return stroke != null || shapeStroke != null || !app.resumed || app.loading || navigating || !hasWindowFocus() || app.replay.running(); }
            // What a tick changed goes to a live page's guests too, batched (LiveProbe.ticked); settled sends the rest at once.
            @Override public void show() { renderDirty(); flush(true); app.live.ticked(); }
            @Override public void settled() { drySettled(); }
        });
        return drying;
    }
    /** As at the end of a stroke: saved, the layers' thumbnails redrawn, and the page refreshed once the pen is away. */
    private void drySettled() {
        unrefreshed = true; drawn();
        removeCallbacks(penAwayRefresh); postDelayed(penAwayRefresh, PEN_AWAY_MS);
    }
    /**
     * A Drying wash still settling is laid where it is at once, shown and kept, and nothing ticks: before the page is
     * put away (a pause, a panel over it, another page) or changed by anything but a stroke, so the page saved or
     * exported is the settled one and the wash isn't dropped half-settled. Undo doesn't: it takes the stroke and its
     * settling back as one.
     */
    void settleDrying() { if (quickLift.running()) finishStroke(); if (drying != null) drying.settleNow(); }
    /** Inkwash: the bar's wet indicator's tap ({@link WetIndicator}): what is wet dries as it would, only sooner ({@link DryingTicker#hurry}). */
    void dryNow() { if (quickLift.running()) finishStroke(); dryingTicker().hurry(); }
    /** Back from a pause: a Drying wash still settling ticks again. */
    void resumeDrying() { if (drying != null) drying.start(); }
    /** Paused: nothing ticks, not even a fading mark (the clock catches it up at {@link #resumeDrying}). */
    void pauseDrying() { if (drying != null) drying.stop(); }
    /**
     * The page changed by drawing: kept for recovery, and the layers' thumbnails follow. Not while a snap draws again
     * ({@link #quietPanel}): a Draw Together guest is sent the clean stroke alone, at the lift.
     */
    private void drawn() { if (quietPanel) return; app.recovery(); if (app.inkWashBar != null) app.inkWashBar.pageChanged(); }

    // Shapes

    /** Scribulor: the pen's shape under pointer {@code id} ({@link #shapesOn}), refused where a stroke of the slot would be; one Undo step. */
    private boolean inkShape(int id) {
        if (document.fenced(samplePoint[0], samplePoint[1])) { app.note(TimedSections.LOCKED); refuse(id); return true; }
        if (!app.allowStroke(app.inkWash.selected)) { refuse(id); return true; }
        // Vector Ink: no shapes of it, nor pixels on its layer.
        String vector = VectorInk.shapeRefusal(document, app.inkWash.brush(app.inkWash.selected));
        if (vector != null) { app.sayVector(vector); refuse(id); return true; }
        hideOutline(); document.paintMaskingFluid(false);
        ToolSwatches.Look look = app.inkWash.shape(document);
        pointer = id;
        getParent().requestDisallowInterceptTouchEvent(true);
        beginShape(new ShapePreview(document, DrawLayout.shapeOutline(shapes, look.px), ToolSwatches.tone(look.darkness), samplePoint[0], samplePoint[1]));
        return true;
    }
    /** A shape {@code preview} with the pen at {@link #samplePoint}, shown until the lift. */
    private void beginShape(ShapePreview preview) {
        renderDirty(); shapeShown.setEmpty();
        shapeX = samplePoint[0]; shapeY = samplePoint[1];
        shapeStroke = preview;
        beginFastPreview(); drawPreviewFrame();
    }
    private void continueShape(MotionEvent event) {
        int action = event.getActionMasked(), index = event.findPointerIndex(pointer);
        if (action == MotionEvent.ACTION_CANCEL || index < 0) { finishStroke(); return; }
        boolean up = pointerUp(event, pointer);
        if (action == MotionEvent.ACTION_MOVE || up) {
            pagePoint(event.getX(index), event.getY(index));
            shapeX = samplePoint[0]; shapeY = samplePoint[1];
            if (!up) schedulePreviewFrame();
        }
        if (!up) return;
        drawPreviewFrame();
        if (snapDrag != null) { liftSnapDrag(); return; }
        endFastPreview();
        StrokeLog.Shape logged = shapeStroke.logged(shapeX, shapeY);
        boolean changed = shapeStroke.finish(); shapeStroke = null; pointer = -1;
        // Record and Replay: a slot's shape (inkShape), on the layer its brush's strokes go to.
        // Kept sharp on the plane it was laid on (SharpView draws it finer when zoomed).
        if (app.inkWash != null && app.inkWash.shapeCode != null) document.logMade(StrokeLog.Entry.shape(0, app.inkWash.shapeCode, logged).sharp(document.selectedAddress(), 1, 1), changed);
        // All the preview went over drawn again from the page: its paper, guides, masking fluid and what lies over the layers.
        document.clearDirty(); renderShapeShown(); flush(true);
        getParent().requestDisallowInterceptTouchEvent(false);
        if (changed) drawn();
    }
    /** Coalesces queued moves into one preview frame per display frame. */
    private void schedulePreviewFrame() {
        if (previewFrameScheduled) return;
        previewFrameScheduled = true;
        postDelayed(previewFrame, Math.max(0, 16-(SystemClock.uptimeMillis()-lastPreviewFrame)));
    }
    private void drawPreviewFrame() {
        removeCallbacks(previewFrame); previewFrameScheduled = false;
        // Budget from frame start; rendering time must not add another full-frame delay.
        lastPreviewFrame = SystemClock.uptimeMillis();
        if (shapeStroke != null) renderShape(shapeStroke.preview(shapeX, shapeY, display, viewportBitmap != null));
        else if (selectionHandle != FloatingSelection.NONE) {
            selection.drag(selectionHandle, grabX, grabY, dragX, dragY); showSelection();
        } else return;
        flush(true); previewFrameCount++;
    }
    private void renderShape(Rect dirty) {
        if (dirty.isEmpty()) return;
        // Preview pixels are already rasterized natively. Submit one complete frame.
        dirty.inset(-2, -2);
        if (!dirty.intersect(0, 0, document.width, document.height)) return;
        shapeShown.union(dirty);
        queue(dirty);
    }
    private void renderShapeShown() { if (!shapeShown.isEmpty()) renderArea(new Rect(shapeShown)); shapeShown.setEmpty(); }

    // The lasso and floating selections

    /** Whether a lifted piece or imported picture is floating over the page. */
    boolean hasSelection() { return selection != null; }
    /** How many view pixels a page pixel spans. */
    private float pageScale() { return pageToView.mapRadius(1); }
    /** The selection's turning knob, beside the edge facing the top of the screen, and on it. */
    private float[] selectionKnob() {
        float[] up = {0, -1};
        viewToPage.mapVectors(up);
        // The page area on screen, less room for the knob's own dot.
        RectF visible = new RectF(0, 0, getWidth(), getHeight());
        viewToPage.mapRect(visible);
        if (!visible.intersect(0, 0, document.width, document.height)) visible.set(0, 0, document.width, document.height);
        float margin = dp(14) / pageScale();
        visible.inset(Math.min(margin, visible.width() / 2), Math.min(margin, visible.height() / 2));
        return selection.knob(up[0], up[1], dp(40) / pageScale(), new float[]{visible.left, visible.top, visible.right, visible.bottom});
    }
    /** Ends the pen's hold on the canvas after a selection drag. */
    private void endPenPreview() {
        removeCallbacks(previewFrame); previewFrameScheduled = false;
        pointer = -1; endFastPreview(); getParent().requestDisallowInterceptTouchEvent(false);
    }
    private void grabSelection(MotionEvent event, int index, int handle) {
        pointer = event.getPointerId(index);
        getParent().requestDisallowInterceptTouchEvent(true);
        selectionHandle = handle; grabX = dragX = samplePoint[0]; grabY = dragY = samplePoint[1];
        selection.grab(handle); lassoBar();
        beginFastPreview();
    }
    /** Follows the pen dragging the selection. */
    private void continueSelection(MotionEvent event) {
        int action = event.getActionMasked(), index = event.findPointerIndex(pointer);
        // Keep the piece where it got to.
        if (action == MotionEvent.ACTION_CANCEL || index < 0) { releaseSelection(); return; }
        boolean up = pointerUp(event, pointer);
        if (action == MotionEvent.ACTION_MOVE || up) {
            pagePoint(event.getX(index), event.getY(index));
            dragX = samplePoint[0]; dragY = samplePoint[1];
            if (!up) schedulePreviewFrame();
        }
        if (!up) return;
        drawPreviewFrame();
        releaseSelection();
    }
    /** Ends a selection drag, leaving the piece floating where it got to. */
    private void releaseSelection() {
        selection.release(); selectionHandle = FloatingSelection.NONE; endPenPreview();
        // A knob that would have left the screen during a turn moves inside now.
        eraseGuide(); drawSelectionFrame(); flush(true); lassoBar();
    }
    /** Floats {@code piece} over the page, placing any piece already floating first. */
    void floatSelection(FloatingSelection piece) {
        finishStroke();
        selection = piece; selectionLaid = piece.lifted ? piece.copy() : null;
        showSelection(); flush(true); lassoBar();
    }
    /**
     * Inkwash: the Lasso's bar ({@link InkWashBar#lassoBar}) over the floating piece where it lies now, its frame and knob
     * in the root's app coordinates; gone with none floating, or while the pen moves it.
     */
    private void lassoBar() {
        if (app.inkWashBar == null) return;
        if (selection == null || selectionHandle != FloatingSelection.NONE) { app.inkWashBar.lassoBar(null); return; }
        float[] corners = selection.corners(), knob = selectionKnob(), points = java.util.Arrays.copyOf(corners, 10);
        points[8] = knob[0]; points[9] = knob[1];
        pageToView.mapPoints(points);
        RectF frame = new RectF(points[0], points[1], points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) frame.union(points[i], points[i + 1]);
        frame.inset(-dp(8), -dp(8));  // the knob's dot
        app.inkWashBar.lassoBar(Popups.bounds(app.root, this, frame));
    }
    /** Redraws the piece where it lies now, with its frame: the page rendered again where it was and is ({@link #render}). */
    private void showSelection() {
        eraseGuide();
        Rect now = pageRect(selection.bounds()), dirty = new Rect(now);
        dirty.union(selectionShown);
        // The first showing opens the hole too.
        if (selectionShown.isEmpty()) dirty.union(pageRect(selection.cutBounds()));
        selectionShown.set(now);
        if (dirty.intersect(0, 0, display.getWidth(), display.getHeight())) { render(dirty); queue(dirty); }
        drawSelectionFrame();
    }
    private static Rect pageRect(int[] bounds) { return bounds == null ? new Rect() : new Rect(bounds[0], bounds[1], bounds[2], bounds[3]); }
    /** Everything the floating piece shows over: where it lies, where it was shown, the hole it was lifted from. */
    private Rect selectionArea() {
        Rect area = pageRect(selection.bounds()); area.union(pageRect(selection.cutBounds())); area.union(selectionShown);
        return area;
    }
    /** Inkwash: what the floating piece shows over the page ({@link LassoPlanes#shown}). */
    private MirrorShow.Marks selectionTones() {
        FloatingSelection piece = selection;
        ToneDocument page = document;
        return (x, y, w, h, out) -> {
            if (piece.planes != null) piece.planes.shown(page, piece, x, y, w, h, out); else java.util.Arrays.fill(out, 0, w * h, -1);
        };
    }
    /** Outlines the piece, with a dot on each corner to scale it and a knob to turn it. */
    private void drawSelectionFrame() {
        if (selection == null || presented() == null) return;
        float[] corners = presentedPoints(selection.corners(), 4), knob = selectionKnob();
        guide.add(presented(), corners, 4, true, corners, dp(5), this::queueView);
        float[] stem = presentedPoints(new float[]{knob[2], knob[3], knob[0], knob[1]}, 2);
        guide.add(presented(), stem, 2, false, new float[]{stem[2], stem[3]}, dp(8), this::queueView);
    }
    /** Lays the floating piece on the page as one undo step. */
    void placeSelection() {
        if (selection == null) return;
        // Inkwash: a copy floating just where one was laid, or a lift not moved, changes nothing.
        if (selectionLaid != null && selection.samePose(selectionLaid)) { cancelSelection(); return; }
        // A locked page takes none: a copy carried there just goes (the page it came from has it still).
        if (app.inkWash != null && app.pageLocked()) { cancelSelection(); app.note(NotebookRules.PAGE_LOCKED); return; }
        FloatingSelection piece = selection; Rect area = endSelection();
        boolean changed;
        try { changed = piece.place(document); }
        catch (IllegalStateException error) { changed = false; app.message(error.getMessage()); }
        // The preview showed nearest pixels; the page now holds them blended.
        renderArea(area); renderDirty(); flush(true);
        if (changed) app.recovery();
    }
    /** Drops the floating piece, leaving the page as it was; false when none floats. */
    boolean cancelSelection() {
        if (selection == null) return false;
        if (selectionHandle != FloatingSelection.NONE) { selectionHandle = FloatingSelection.NONE; endPenPreview(); }
        renderArea(endSelection()); flush(true);
        return true;
    }
    /** Forgets the floating piece and its frame; returns the page pixels its preview covered. */
    private Rect endSelection() {
        eraseGuide();
        Rect area = selectionArea();
        selection = null; selectionLaid = null; selectionShown.setEmpty(); lassoBar();
        if (lassoBack) { lassoBack = false; lasso(false); if (app.inkWashBar != null) app.inkWashBar.lassoChanged(); }
        return area;
    }
    /** Inkwash: the Lasso taken up or put away (the side column's button, another tool chosen); put away, a floating piece is put down. */
    void lasso(boolean on) {
        lassoBack = false;
        if (!on) placeSelection();
        lassoOn = on;
        if (on) hideOutline();
    }
    /**
     * Inkwash: the edge strip's Lasso let go with a piece floating (the owner, 2026-11-05): the piece and its bar stay, as the
     * side column's do, and the Lasso is put away once the piece is done ({@link #lassoBack}); false when none floats.
     */
    boolean lassoBackWhenDone() { return lassoBack = selection != null && lassoOn && !lassoDeletes; }
    /** Inkwash: the Lasso chosen by hand (the column's button): it stays once the piece floating is done, whatever set {@link #lassoBack}. */
    void keepLasso() { lassoBack = false; }
    /**
     * Inkwash: the Lasso's loop drawn ({@link Placing}'s marching ants): what the selected pair holds inside it floats
     * as a piece. A tap, as the one that put the last piece down, lifts nothing.
     */
    private void liftLasso(MirrorShow.Place loop) {
        if (document == null || !lassoOn || loop.count() < 3) return;
        float[] points = new float[2 * loop.count()];
        for (int i = 0; i < loop.count(); i++) { points[2 * i] = loop.xs()[i]; points[2 * i + 1] = loop.ys()[i]; }
        FloatingSelection piece = LassoPlanes.lift(document, points, loop.count());
        if (piece == null) { app.note(lassoDeletes ? DrawLayout.LASSO_NOTHING_DELETED : DrawLayout.LASSO_NOTHING); return; }
        // The way that deletes: what the loop rings goes at once, as Delete takes a piece, never floating.
        if (lassoDeletes) { if (app.pageLocked()) app.note(NotebookRules.PAGE_LOCKED); else erase(piece, pageRect(piece.cutBounds())); return; }
        floatSelection(piece);
    }
    /** Inkwash: Cut (the Lasso's menu): kept to paste, then deleted as Delete does, one Undo step. */
    void cutSelection() {
        FloatingSelection piece = selection;
        if (deleteSelection()) kept = piece.copy();
    }
    /**
     * Inkwash: Duplicate (the Lasso's bar; the owner, 2026-10-28: "it makes sense to have duplicate instead of copy"): the
     * piece goes down where it lies, and a copy of it floats {@link DrawLayout#DUPLICATE_OFFSET} px down and right, to drag
     * into place, onto another page too, as a carried piece does.
     */
    void duplicateSelection() {
        if (selection == null || document == null) return;
        FloatingSelection copy = LassoPieces.duplicated(selection);
        lassoBack = false;  // the piece going down is not the last: the Lasso stays for the copy
        placeSelection();
        floatSelection(copy);
        lassoBack = lassoOn && !lassoDeletes;
    }
    /** Inkwash: Make Stamp (the Lasso's bar, 2026-11-02): the piece's ink a Stamp's nib ({@link PaintActivity#makeStamp}). */
    void stampSelection() { if (selection != null && document != null) app.makeStamp(selection); }
    /** Inkwash: something cut or copied to paste ({@link #pasteSelection}). */
    boolean hasKept() { return kept != null; }
    /**
     * Inkwash: Paste (the Lasso's menu): a floating piece put down first, then a copy of the one kept floats where it was
     * cut or copied, on this page or any other of its size, to drag into place; it goes down as a moved piece does.
     */
    void pasteSelection() {
        if (kept == null || document == null) return;
        if (!kept.fits(document)) { app.note(DrawLayout.LASSO_OTHER_SIZE); return; }
        placeSelection();
        floatSelection(kept.copy());
    }
    /** Inkwash: Delete (the Lasso's menu): the floating piece goes, and what it was lifted from with it, one Undo step; false when refused or none floats. */
    boolean deleteSelection() {
        FloatingSelection piece = selection;
        if (piece == null || document == null) return false;
        if (app.pageLocked()) { app.note(NotebookRules.PAGE_LOCKED); return false; }
        lassoBack = lassoOn && !lassoDeletes;
        erase(piece, endSelection());
        return true;
    }
    /** Takes what {@code piece} was lifted from off the page, one Undo step, and shows {@code area} again. */
    private void erase(FloatingSelection piece, Rect area) {
        boolean changed = false;
        try { changed = LassoPieces.erase(document, piece); }
        catch (IllegalStateException error) { app.message(error.getMessage()); }
        renderArea(area); renderDirty(); flush(true);
        if (changed) app.recovery();
    }
    /**
     * Inkwash: another page about to show ({@link #showPage}): the floating piece goes along as a copy lifted from
     * nowhere, so the page left is as it was (it is never cut there: that page's Undo may be gone once it is packed);
     * the same page shown again keeps it as it is. A page of another size drops it. Returns the piece to show again.
     */
    private FloatingSelection carrySelection(ToneDocument next) {
        FloatingSelection piece = selection;
        if (piece == null) return null;
        if (selectionHandle != FloatingSelection.NONE) releaseSelection();
        FloatingSelection laid = selectionLaid;
        selection = null; selectionLaid = null; selectionShown.setEmpty(); eraseGuide();
        if (next == document) { selectionLaid = laid; return piece; }
        return piece.fits(next) ? piece.copy() : null;
    }
    /** Re-renders these page pixels from the document and queues them. */
    private void renderArea(Rect area) {
        if (area.intersect(0, 0, display.getWidth(), display.getHeight())) { render(area); queue(area); }
    }

    // The Dropper

    private void sampleShadeGesture(MotionEvent event) {
        int action = event.getActionMasked(), index = event.getActionIndex();
        if (action == MotionEvent.ACTION_CANCEL) { finishStroke(); return; }
        boolean down = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN;
        if (down && pickPointer == -1 && event.getToolType(index) == MotionEvent.TOOL_TYPE_STYLUS) {
            pickPointer = event.getPointerId(index);
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        int p = event.findPointerIndex(pickPointer);
        if (p < 0) return;
        boolean up = pointerUp(event, pickPointer);
        // Inkwash: the Dropper picks the grey the page shows, set on the lift ({@link InkWashBar#dropped}), then is put down.
        if (dropperOn && (down || action == MotionEvent.ACTION_MOVE || up)) dropTone = dropAt(event.getX(p), event.getY(p));
        if (up) {
            pickPointer = -1; getParent().requestDisallowInterceptTouchEvent(false);
            if (dropperOn) {
                int tone = dropTone;
                dropper(false);
                if (tone >= 0) app.inkWashBar.dropped(tone); else app.inkWashBar.lassoChanged();
            }
        }
    }

    // Finger taps

    /**
     * A live change still drawing itself on, laid now ({@link LiveProbe#layNow}), before anything else changes the page.
     * (A three-finger double tap was Zen Mode from 2026-10-30, its tap's Redo waiting for a second tap; gone 2026-11-02,
     * the owner: "remove the three finger double to zen mode .. i can't get three finger redo to work".)
     */
    private void layLiveNow() { app.live.layNow(); }

    /** A two-finger tap undoes and a three-finger tap redoes (at first: System Settings may set either to Refresh or Nothing), at once, like the header buttons. */
    private boolean historyTap(MotionEvent event) {
        // A finger's stroke may yet be a tap's first finger: the next finger takes back what it drew.
        if ((fingerDrawing() ? blocksTap() : blocksFingerGesture()) || !Ui.fingersOnly(event) || app.busy()) { fingerTaps.reset(); return false; }
        int count = event.getPointerCount();
        // A palm among the contacts: the hand, not a tap (the owner, 2026-11-05: a palm triggering Undo).
        for (int i = 0; i < count; i++) if (event.getTouchMajor(i) >= FingerTool.PALM_MAJOR) { fingerTaps.reset(); return false; }
        float[] x = new float[count], y = new float[count]; int[] ids = new int[count];
        for (int i = 0; i < count; i++) { x[i] = event.getX(i); y[i] = event.getY(i); ids[i] = event.getPointerId(i); }
        int fingers = fingerTaps.event(event.getActionMasked(), event.getEventTime(), ids, x, y, dp(PinchPan.SLOP_DP));
        // A palm landing and lifting as the pen draws passes for a tap: ignored while the pen is near and a second after.
        if (fingers == 0 || !FingerTool.tapCounts(SystemClock.uptimeMillis(), penSeen, pointer != -1)) return false;
        // What the tap does: System Settings' choice (Undo and Redo at first).
        FingerTool.Tap tap = FingerTool.tap(fingers, app.prefs.twoFingerTap(), app.prefs.threeFingerTap());
        if (tap == FingerTool.Tap.NOTHING) return false;
        endNavigation();
        layLiveNow();
        if (tap == FingerTool.Tap.UNDO) app.undo(); else if (tap == FingerTool.Tap.REDO) app.redo(); else refreshAsked(tap.label + " tap");
        return true;
    }

    // One finger: the finger tool

    /** The finger that is drawing, or may yet; -1 for none. */
    private int drawFinger = -1;
    private boolean fingerRefused;
    private float fingerDownX, fingerDownY;
    private long fingerDownTime;
    /** What the finger draws with, read as it lands ({@link FingerTool}, the menu's "Finger Action: …"). */
    private FingerTool.Choice fingerChoice = FingerTool.Choice.ERASER;
    /** Whether the stroke {@link #finishStroke} last finished changed the page (a finger's brush stroke taken back is then undone). */
    private boolean finishedChanged;

    /**
     * One finger on the page draws with the finger tool ({@link FingerTool}: nothing, the eraser, the smudge tool or a
     * slot's brush) once it moves, so a quick two- or three-finger tap still undoes or redoes. Refused while the pen is
     * down or near and for a palm-sized contact; a second finger, the pen or a contact swelling to a palm's takes back
     * what it drew.
     * @return true if the event belongs to the finger
     */
    private boolean fingerStroke(MotionEvent event) {
        if (app.inkWash == null) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            drawFinger = event.getPointerId(0); fingerDownX = event.getX(0); fingerDownY = event.getY(0); fingerDownTime = event.getEventTime();
            fingerChoice = app.inkWash.finger();
            float widest = Ui.widestFingerDp(event);
            fingerRefused = FingerTool.refused(fingerChoice, Ui.fingersOnly(event), blocksFingerGesture(), app.busy(), widest);
            // So the owner's real values can be read from logcat to tune the palm thresholds.
            if (widest > FingerTool.PALM_DP) Log.d(ProbeActivity.TAG, "finger refused as palm, " + widest + "dp");
        }
        if (drawFinger == -1) return false;
        boolean ended = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        if (!fingerRefused) {
            float widest = Ui.widestFingerDp(event);
            if (FingerTool.cancels(action == MotionEvent.ACTION_POINTER_DOWN, Ui.fingersOnly(event), widest, fingerDrawing())) {
                Log.d(ProbeActivity.TAG, "finger taken back, widest contact " + widest + "dp");
                cancelFingerStroke(); fingerRefused = true;
            }
        }
        // The pen takes over from here.
        if (!Ui.fingersOnly(event)) { if (ended) drawFinger = -1; return false; }
        if (fingerRefused) { if (ended) drawFinger = -1; return true; }
        int index = event.findPointerIndex(drawFinger);
        if (index < 0 || action == MotionEvent.ACTION_CANCEL) {
            if (action == MotionEvent.ACTION_CANCEL && fingerDrawing() && FingerTool.keepsOnCancel(event.getEventTime() - fingerDownTime)) { if (!liftQuick()) finishStroke(); }
            else cancelFingerStroke();
            drawFinger = -1; return true;
        }
        if (quickLift.running()) finishStroke();
        if (stroke == null) {
            // Drawing starts once the finger moves a little. A tap's first finger may drift further than
            // this before the next lands; that takes it back, and historyTap still counts the tap
            // within its own slop and time. The back-fill from the landing point keeps the line whole.
            float moved = (float)Math.hypot(event.getX(index) - fingerDownX, event.getY(index) - fingerDownY);
            if (!FingerTool.starts(moved / getResources().getDisplayMetrics().density)) {
                if (ended) drawFinger = -1;
                return true;
            }
            if (fingerChoice == FingerTool.Choice.TURN) return fingerTurn(event, index, ended);
            if (fingerChoice == FingerTool.Choice.ERASER && app.fingerPans()) return fingerPan(event, index, ended);
            int tool = fingerChoice.tool();
            if (tool == InkWash.ERASER && !app.allowStroke(tool)) refuse(drawFinger);
            else if (tool == InkWash.ERASER) {
                pointer = drawFinger;
                getParent().requestDisallowInterceptTouchEvent(true);
                // Sized once, by the contact as it starts to move: a stylus tip erases finely, a fingertip broadly,
                // the line not following the contact as it swells and shrinks. That size is a fast swipe's;
                // a slow finger narrows to a fine point (PenSpeed).
                float contact = event.getTouchMajor(index), page = unscaledPage ? contact : viewToPage.mapRadius(contact);
                float diameter = InkWashSizes.fingerEraser(page);
                Log.d(ProbeActivity.TAG, "finger erase starts, touchMajor=" + contact + "px = "
                        + contact / getResources().getDisplayMetrics().density + "dp, diameter=" + diameter + " page px");
                layLiveNow();
                long created = System.nanoTime();
                timing.begin("kind=finger tool=ERASER diameter=" + Math.round(diameter), created);
                stroke = app.inkWash.fingerEraser(document, diameter); recording = app.inkWash.recorder;
                nibShownUntil = SystemClock.uptimeMillis() + NIB_SHOWN_MS;
                stroke.zoom(viewport.scale());
                if (stroke instanceof MirrorStroke && document.mirrors().copies()) mirrorShow.beginCopies(document.mirrors().group(), false);
                timing.created(System.nanoTime() - created);
            } else beginInkStroke(tool, false, drawFinger, "finger");
            app.inkWashBar.closePanel();
            fingerSample(fingerDownX, fingerDownY, fingerDownTime);
            ringGuide(ToolSettings.pressure(FingerTool.PRESSURE_TRAVEL), Float.NaN, Float.NaN);
        }
        long eventAge = Math.max(0, SystemClock.uptimeMillis() - event.getEventTime());
        for (int h = 0; h < event.getHistorySize(); h++)
            fingerSample(event.getHistoricalX(index, h), event.getHistoricalY(index, h), event.getHistoricalEventTime(h));
        fingerSample(event.getX(index), event.getY(index), event.getEventTime());
        presentInput(true, eventAge);
        if (ended) { if (!liftQuick()) finishStroke(); drawFinger = -1; }
        return true;
    }
    /** Where the panning finger last was, view px. */
    private float panX, panY;
    /**
     * No Erasing's finger ({@link PaintActivity#fingerPans}): past a tap's slop it pans the page as two fingers do (the
     * same navigation and its frames), with Zoom unlocked; locked, nothing moves. Its lift ends it ({@link #navigationGesture}).
     */
    private boolean fingerPan(MotionEvent event, int index, boolean ended) {
        if (app.navigationLocked) { if (ended) drawFinger = -1; return true; }
        float x = event.getX(index), y = event.getY(index);
        if (!navigating) {
            fingerTaps.reset();
            disconnectDisplay(); navigating = true;
            updateViewport(); connectDisplay();
            getParent().requestDisallowInterceptTouchEvent(true);
            panX = fingerDownX; panY = fingerDownY;
        }
        viewport.gesture(1, panX, panY, x, y);
        panX = x; panY = y;
        if (!navigationFrameScheduled) {
            navigationFrameScheduled = true;
            postDelayed(navigationFrame, Math.max(0, 16-(SystemClock.uptimeMillis()-lastNavigationFrame)));
        }
        if (ended) drawFinger = -1;
        return true;
    }
    /** Turn pages' finger: on its lift a swipe across the page turns it, as inside a notebook ({@link NotebookLayout#swipe}). */
    private boolean fingerTurn(MotionEvent event, int index, boolean ended) {
        if (!ended) return true;
        drawFinger = -1;
        float density = getResources().getDisplayMetrics().density;
        int by = NotebookLayout.swipe((event.getX(index) - fingerDownX) / density, (event.getY(index) - fingerDownY) / density);
        if (by != 0) { app.inkWashBar.closePanel(); app.turnPage(by); }
        return true;
    }
    /** Whether the stroke in progress is the finger's. */
    private boolean fingerDrawing() { return stroke != null && drawFinger != -1 && pointer == drawFinger; }
    private void fingerSample(float x, float y, long time) {
        pagePoint(x, y);
        // A fingertip has no pressure or tilt: a brush draws at a medium press. The finger eraser's size is fixed when it
        // starts; pressure and tilt do not change it, speed does.
        long start = timing.active() ? System.nanoTime() : 0;
        float pressure = ToolSettings.pressure(FingerTool.PRESSURE_TRAVEL);
        stroke.sample(samplePoint[0], samplePoint[1], pressure, Float.NaN, Float.NaN, time);
        if (recording != null) recording.add(samplePoint[0], samplePoint[1], pressure, Float.NaN, Float.NaN, time);
        if (timing.active()) timing.sample(System.nanoTime() - start);
    }
    /**
     * Takes back the finger's stroke: the eraser's is dropped, the page as it was; a brush's (its quick render, its wet
     * paint can't be dropped mid-way) is finished, then undone, as Undo would.
     */
    private void cancelFingerStroke() {
        if (!fingerDrawing()) return;
        if (!(own(stroke) instanceof EraseBothStroke)) {
            finishStroke();
            if (finishedChanged) app.history(() -> app.inkWash.session.undo(document));
            return;
        }
        // The page is as it was: show it again wherever the eraser showed paper.
        EraseBothStroke eraser = erasePreview();
        int[] erased = eraser == null ? null : eraser.erasedBounds();
        hideOutline();
        ((EraseBothStroke)own(stroke)).cancel(); stroke = null; pointer = -1; timing.cancel();
        mirrorShow.end();
        renderDirty(erased); flush(true); ghosted = true;
        getParent().requestDisallowInterceptTouchEvent(false);
    }
    /** The broadest finger contact among the touches, dp ({@link FingerTool#PALM_DP}: a resting hand's is broader). */

    // Pinch and pan

    /**
     * Handles finger navigation; returns true if the event was consumed as navigation. Inkwash: two fingers are a tap
     * until one moves past a tap's slop ({@link PinchPan}), and only then navigate, so a quick two- or three-finger tap
     * still undoes or redoes with Zoom unlocked and nothing moves; once they navigate no tap fires. A finger's own
     * stroke doesn't keep them from it: the second finger took it back ({@link #fingerStroke}, first).
     */
    private boolean navigationGesture(MotionEvent event) {
        int action = event.getActionMasked(), index = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN) { touchBlocked = false; endNavigation(); }
        boolean pen = false;
        for (int i = 0; i < event.getPointerCount(); i++)
            if (isPen(event, i)) pen = true;
        if (pen || pointer != -1 && pointer != drawFinger || pickPointer != -1
                || SystemClock.uptimeMillis() < penSeen + 250) {
            touchBlocked = true; endNavigation();
            return false;
        }
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) { endNavigation(); return true; }
        if (touchBlocked) return true;
        if (action == MotionEvent.ACTION_POINTER_UP) {
            // Require a fresh two-finger gesture after either tracked finger lifts.
            if (event.getPointerId(index) == pinch.first() || event.getPointerId(index) == pinch.second()) {
                touchBlocked = true; endNavigation();
            }
            return true;
        }
        if (!pinch.tracking()) {
            if (action != MotionEvent.ACTION_POINTER_DOWN || event.getPointerCount() != 2
                    || event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER
                    || event.getToolType(1) != MotionEvent.TOOL_TYPE_FINGER) return true;
            // Locked (the default), or landing too close together: not this touch.
            if (!pinch.land(app.navigationLocked, event.getPointerId(0), event.getX(0), event.getY(0),
                    event.getPointerId(1), event.getX(1), event.getY(1), dp(PinchPan.LEAST_SPAN_DP))) touchBlocked = true;
            // Two fingers back before the flash: it waits for them, so it never comes between quick adjustments.
            else if (pendingRefresh != null) { removeCallbacks(pendingRefresh); postDelayed(pendingRefresh, NAVIGATED_MS); }
            return true;
        }
        // A third finger before the two moved: a three-finger tap, never a pinch.
        if (action == MotionEvent.ACTION_POINTER_DOWN) { if (!navigating) { touchBlocked = true; endNavigation(); } return true; }
        int a = event.findPointerIndex(pinch.first()), b = event.findPointerIndex(pinch.second());
        if (a < 0 || b < 0) { touchBlocked = true; endNavigation(); return true; }
        if (action != MotionEvent.ACTION_MOVE) return true;
        float ax = event.getX(a), ay = event.getY(a), bx = event.getX(b), by = event.getY(b);
        if (!navigating) {
            if (!pinch.starts(ax, ay, bx, by, dp(PinchPan.SLOP_DP))) return true;
            // Past a tap's slop: navigation, so no Undo or Redo at the lift.
            fingerTaps.reset();
            disconnectDisplay(); navigating = true;
            updateViewport(); connectDisplay();
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        float[] g = pinch.step(ax, ay, bx, by, dp(PinchPan.LEAST_SPAN_DP));
        if (g == null) return true;
        viewport.gesture(g[0], g[1], g[2], g[3], g[4]);
        if (!navigationFrameScheduled) {
            navigationFrameScheduled = true;
            postDelayed(navigationFrame, Math.max(0, 16-(SystemClock.uptimeMillis()-lastNavigationFrame)));
        }
        return true;
    }
    void endNavigation() {
        pinch.end();
        if (!navigating) return;
        if (navigationFrameScheduled) drawNavigationFrame();
        navigating = false; getParent().requestDisallowInterceptTouchEvent(false);
        // Lifted or cancelled alike (the firmware cancels many two-finger touches): the frames' grey updates overlapped,
        // so the page gets its clean update once the fingers have been still a moment, Automatic Refresh on or off.
        ghosted = unrefreshed = asked = true; refreshSoon("pinch or pan ended", NAVIGATED_MS);
        if (viewport.zoom == 1 && viewport.scale() == 1) {
            disconnectDisplay(); updateViewport(); invalidate();
        }
        // Inkwash: Vector Ink's lines, left out while the view moved, sharp again.
        else if (sharp() != composedSharp) { updateViewport(); pending.clear(); pending.add(0, 0, getWidth(), getHeight()); flush(true); }
        if (direct == null) post(this::connectDisplay);
    }
    private void drawNavigationFrame() {
        removeCallbacks(navigationFrame); navigationFrameScheduled = false;
        if (!navigating) return;
        lastNavigationFrame = SystemClock.uptimeMillis();
        long began = System.nanoTime();
        updateViewport();
        long rendered = System.nanoTime();
        pending.clear(); pending.add(0, 0, getWidth(), getHeight()); flush(true);
        // Keep Android's retained drawing commands current without requesting a
        // compositor frame for every finger movement on the direct display path.
        if (direct != null) app.selectionFeedback.retainForNextDraw(this, new Rect(0, 0, getWidth(), getHeight()));
        lastNavigationRasterNanos = rendered-began;
        lastNavigationPresentNanos = System.nanoTime()-rendered;
        navigationFrameCount++;
    }

    boolean stroking() { return (stroke != null && !quickLift.running()) || shapeStroke != null; }
    /** Inkwash: whether a stroke or shape is under way or still finishing after lift (the recovery save waits). */
    boolean drawingNow() { return stroke != null || shapeStroke != null; }
    void close() {
        finishStroke(); if (drying != null) drying.stop(); disconnectDisplay(); input.close();
        if (display != null) display.recycle();
        if (viewportBitmap != null) { viewportBitmap.close(); viewportBitmap = null; }
        display = null; renderPixels = new int[0]; document = null;
    }
}
