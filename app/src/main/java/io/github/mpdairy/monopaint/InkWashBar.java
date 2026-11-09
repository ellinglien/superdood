package io.github.mpdairy.monopaint;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The drawing screen's one toolbar: a narrow column down the drawing hand's side, beside the page with no rule between
 * them (the owner's Scribbler Style, {@link Theme}). Top to bottom (layout v3, the owner's mockup, 2026-10-26): Menu
 * alone; the tray (the owner's "B Pen tray", 2026-10-19): the nine tool slots as {@link SlotTile} rows, each its
 * brush's stroke lying across it, like a pen in a tray, no number, the brush at its Medium size whatever the slot's
 * ({@link ToolSwatches#picture}), or, for a Basic tool the owner drew, its picker drawing, its tip out of the row's edge
 * ({@link DrawLayout#drawing}, 2026-10-26); a gap, then the smudge tool and the eraser, a row each, the owner's drawings of them
 * ({@link #showIcon}); the selected tool's size slider beside its grey strip, its size and grey in one dot over them
 * ({@link SwatchBar}, 2026-10-24); the icon block under them (icons alone, no grey behind them; layout v4, 2026-10-26):
 * the Dropper and the Lasso (the owner, 2026-10-24: "lasso can move beside picker"), Refresh ({@link #ghost}); the wet indicator ({@link #wet});
 * a Timed Pages page's countdown ({@link #countdown}); the layer button under the picker box ({@link #layers}). The sand timer,
 * Show Kinds ({@link #kindsPopup}) and Play are the menu's rows since 2026-11-06 ({@code PaintActivity.fileMenu}). The refocus, 2026-10-24: the stamp tool, Magnetic
 * Poetry, Mirrors and the layers left the column; 2026-10-28 undo and redo (two and three fingers), Shapes and Play (the
 * menu's Play row; both back 2026-10-30), and the Trash (the Lasso's bar deletes, {@link #lassoBar}). In the order {@link DrawLayout#COLUMN} gives, all on one axis inside one margin ({@link
 * DrawLayout#edge}). What is in use or on has an ink bar down its side ({@link DrawLayout#SIDE_BAR}): the slot, the tool,
 * the Dropper, the Lasso or Shapes while taken up, the grey in use one beside the strip. Space parts the groups, no rules. The items share the column's height ({@link DrawLayout}): shorter
 * together when it is short. Tapping a tool
 * selects it, and a tray row never does more (the owner, 2026-10-19); a long press is how the bar is changed (the
 * owner, 2026-10-19: "instead of edit buttons, we use long press as a way to customize"): on a row it opens the tool
 * picker on its slot ({@code PaintActivity.toolPicker}, its Advanced the Tools page, {@link ToolsActivity}), on the eraser Clear page ({@link PageClear}); a long press on the smudge tool opens a chooser of
 * its two, Blend and Water, beside it ({@link ToolSlots#smudgeChoices}). The page bar under the page ({@link #turner}) is built
 * here and placed by the drawing screen. A short label beside the column names a brush when it changes. See
 * PHILOSOPHY.md.
 */
final class InkWashBar {
    /** How long after the page last changed its previews are drawn again (the page bar's, {@link PageTurner}): after strokes, never during one. */
    static final long THUMBNAILS_MS = 800;
    final LinearLayout view;
    private final PaintActivity app;
    /** The column's tools: the nine slots, then the eraser, the smudge tool, the stamp tool and Fill in (see {@link InkWash#ERASER}, {@link InkWash#SMUDGE}, {@link ToolSlots#FILL}). */
    private final ToolButton[] tools = new ToolButton[ToolSlots.TOOLS];
    /** The column's three gaps. */
    private final List<View> gaps = new ArrayList<>();
    /** Menu, whose file menu is made ahead ({@link PaintActivity#prepareFileMenu}). Zen Mode's button is the drawing screen's, under the column's foot ({@link DrawLayout#ZEN}). */
    final ToolButton menu;
    /** A Timed Pages page's time left, m:ss, at the column's top ({@link NotebookRules#countdown}); gone with Timed Pages off. */
    private final TextView clock;
    /**
     * The countdown's row: a Timed Pages page's countdown as before, or the sand timer's ({@link SandTimer}) while it
     * runs. Its icon (the owner, 2026-10-27: "just have a sand timer icon, press it to have it open a menu and you can
     * set a timer") the menu's Sand Timer row since 2026-11-06 ({@link #sandShown}); its tap, or its countdown's, opens {@link #timerMenu}.
     */
    private final LinearLayout timerRow;
    /** Whether the sand timer can be set now (the countdown's row is its and idle): the menu shows its row. */
    boolean sandShown;
    private final WetIndicator wet;
    /** The selected tool's size slider, grey strip and Dropper. */
    final SwatchBar swatches;
    /** The sizes the column's items have now. */
    private DrawLayout fitted;
    /** Whether the column's ink rule is on its left (the column on the page's right). */
    private boolean right;
    /** The open panel (the smudge chooser) and the control it is beside, or null. */
    private PopupWindow panel;
    private View panelOwner;
    /** How long the label beside the column names a brush. */
    static final long LABEL_MS = 2000;
    /** The label beside the column, its text and the panel area it has covered, or null (see {@link #say}). */
    private PopupWindow label;
    private TextView labelText;
    /** What the label shows: its text, or {@link #offer}'s button. */
    private View labelContent;
    private final RectF labelArea = new RectF();
    private final Runnable hideLabel = this::hideLabel;
    /** The slots' tray strokes and the smudge chooser's loops, drawn off the main thread, so a change never holds up the pen. */
    private final SampleQueue samples = new SampleQueue();
    /** What each slot's button shows: its brush and its row's size ({@link #showBrush(int, boolean)}); null until drawn. */
    private final String[] trayShown = new String[ToolSlots.TOOLS];
    /** The smudge chooser's page. */
    private int smudgePage;
    /**
     * The Lasso (Phosphor lasso, MonoPaint's Lasso, 2026-10-23), the icon block's second row's right cell: a tap takes it up, the
     * pen's strokes then loops that lift a piece to move, size and turn ({@link FloatingSelection}); its side bar while in
     * use; a piece floating has its bar over it, Cut, Duplicate and Delete ({@link #lassoBar}); with nothing floating, a tap
     * opens Paste once one is cut or copied. Another tool puts it down. The Trash beside it (2026-10-25) went into that bar
     * (the owner, 2026-10-28: "we could merge the lasso trash and the lasso"); the edge strip's hold still loops to
     * delete ({@link #edgeTrash}).
     */
    final ToolButton lasso;
    /**
     * Refresh (Phosphor ghost: one full e-ink flash of the screen), alone in the icon block's last row, where the Trash
     * was, the whole row its target for a finger (the owner, 2026-10-28: "move the refresh ghost to a spot on the
     * toolbar, set apart so a finger can press it easily"; on the page bar that morning).
     */
    final ToolButton ghost;
    /** Refresh's row, at the column's foot over Zen Mode ({@link DrawLayout#ghostMargin}). */
    private final LinearLayout ghostRow;
    /** The layer button under the picker box ({@link DrawLayout#LAYERS_ROW}): a tap's third of it picks the layer ({@link PaintActivity#pickLayer}). */
    final ToolButton layers;
    /** The layer it shows and whether its pair is hidden ({@link #showLayer}); -1 before the first page. */
    private int layerShown = -1;
    private boolean layerHidden;
    /** The column's last small row: the wet indicator (the sand timer, Show Kinds and Play over it until 2026-11-06). */
    private final LinearLayout templateRow;
    /** The eraser's mark's paint ({@link #drawEraserMark}). */
    private final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Zoom on the page bar ({@link ZoomButton}, {@link PageTurner}): its padlock beside its percentage. */
    final ZoomButton zoom;
    /**
     * The brush line along the screen's foot, from just past Zen Mode's button ({@link DrawLayout#BRUSH_LINE}): the slot
     * in use's name and words in a fixed room ({@link DrawLayout#LINE_NAME}; a tap on them opens Brush Lab on it; no
     * preview of its stroke since 2026-10-27), and the app's notes at its far end ({@link #note}); the drawing screen puts it there. It reads from the column's side, mirrored
     * when the column is on the right.
     */
    final LinearLayout brushLine;
    /** The lasting status ({@link #statusCorner(String)}), very small in the foot's far corner, as wide as its words ({@link DrawLayout#statusCorner}); touches go through it. */
    final TextView statusCorner;
    private final Caption brushName;
    private final TextView noteText;
    /** The brush line's words for a brush, by {@link Brush#identity}: {its name and caption, what the screen reader hears}. Main thread only. */
    private final java.util.Map<String, String[]> lineWords = new java.util.LinkedHashMap<String, String[]>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, String[]> eldest) { return size() > 3 * ToolSlots.COUNT; }
    };
    /** {@code brush}'s line words ({@link #lineWords}, {@link DrawLayout#lineWords}): they resolve the brush (its textures the first time), so a tap reads them made. */
    private static String[] lineWordsOf(Brush brush) { return DrawLayout.lineWords(brush); }
    /**
     * Tool {@code slot}'s line words made in the background, so picking it puts the line on the
     * panel at once in one update, with nothing worked out on the tap (the owner, 2026-10-25: "make Scribulor's as snappy").
     */
    private void warmLine(int slot) {
        Brush brush = app.inkWash.brush(slot);
        if (brush == null) return;
        String id = brush.identity();
        if (!lineWords.containsKey(id)) {
            Brush copy = BrushLab.copyOf(brush);
            samples.request("words " + slot, () -> lineWordsOf(copy), words -> lineWords.put(id, words));
        }
    }
    private final Runnable clearNote = () -> showNote(null);
    /** The page bar along the page's top ({@link PageTurner}), the drawing screen puts it over the page. */
    final PageTurner turner;
    /** Each slot's tray stroke as its row shows it, for the finger's choices in the menu; null until drawn. */
    private final Bitmap[] trays = new Bitmap[ToolSlots.TOOLS];

    InkWashBar(PaintActivity app) {
        this.app = app;
        view = LabUi.column(app);
        // The tray's rows reach over the column's outer margin to the screen's edge (trayTile).
        view.setClipToPadding(false);
        fitted = layout();
        ToolButton[] menu = new ToolButton[1];
        // Phosphor list, Bold as the other icons (the owner, 2026-10-22: "the menu on top should be phosphor list").
        menu[0] = app.iconButton("Menu", R.drawable.ic_ph_list_bold, () -> app.fileMenu(menu[0])).slow();
        this.menu = menu[0];
        this.menu.iconHalf = DrawLayout.ICON_SIDE / 2;
        // Icons alone: no button grey behind them (the owner, 2026-10-21); a press still draws its outline.
        menu[0].setBackground(null);
        clock = Theme.style(new TextView(app), TypeStyle.LABEL);
        clock.setGravity(Gravity.CENTER);
        clock.setVisibility(View.GONE);
        // Timed Sections: two lines, the section over its time, smaller to fit a long name; a tap starts or skips ahead.
        clock.setMaxLines(2);
        clock.setAutoSizeTextTypeUniformWithConfiguration(8, Math.round(TypeStyle.LABEL.sp), 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        clock.setOnClickListener(v -> app.countdownTapped());
        timerRow = LabUi.row(app);
        timerRow.setGravity(Gravity.CENTER);
        timerRow.addView(clock, new LinearLayout.LayoutParams(-1, -1));
        // Menu alone at the top (layout v3, 2026-10-26), in the screen's corner (cornered).
        LinearLayout menuRow = LabUi.row(app);
        menuRow.addView(menu[0], tile(fitted.menuRow(), fitted.menuRow()));
        for (int t = 0; t < ToolSlots.TOOLS; t++) {
            int tool = t;
            ToolButton button = app.iconButton(DrawLayout.toolName(tool), R.drawable.ic_eraser_block, () -> choose(tool)).bar();
            // No Button background: its pressed ripple drew the row again through Android, frame after frame, on every tap;
            // and no press outline: its box on the pen's down and off 200 ms after were two more grey updates, queued
            // before and after the mark's on the panel (2026-10-24). The press is the new side bar itself, in the binary
            // update on the down (ToolButton.bar), and the click's update sends only the old bar (2026-10-25).
            button.setBackground(null);
            button.marked = tool == app.inkWash.selected;
            // A tray row's long press: the tool picker beside it (a tap only selects it; the owner, 2026-10-25: "long press on any
            // slot to open. single tap selects the tool"); while its brush holds paint, Clean Brush first (the refocus, 2026-10-24: behind the long press alone, the slots' paint dab and gauge gone).
            button.setOnLongClickListener(v -> { hold(tool); return true; });
            tools[tool] = button;
        }
        // Layout v3 (the owner's mockup, 2026-10-26, DrawLayout.COLUMN): under the tray the smudge tool and the eraser, a
        // row each, their drawings; the dot, the slider and the grey strip; under them the icon block (layout v4), the
        // Dropper and the Lasso, Refresh; then the wet indicator's small row.
        ghostRow = LabUi.row(app);
        templateRow = LabUi.row(app);
        for (LinearLayout row : new LinearLayout[]{ghostRow, templateRow}) row.setGravity(Gravity.CENTER_HORIZONTAL);
        tools[InkWash.ERASER].overlay = this::drawEraserMark;
        // The stamp tool has no cell (the Tools page picks it); its button keeps its state, off the column.
        // The Dropper (2026-10-24), beside the Lasso: a tap takes it up, or puts it down.
        ToolButton dropper = app.iconButton(DrawLayout.DROPPER, R.drawable.ic_ph_eyedropper, this::tapDropper).bar();
        dropper.setBackground(null);
        swatches = new SwatchBar(app, new SwatchFaces.Host() {
            @Override public boolean ready() {
                if (app.busy()) return false;
                app.pad.finishStroke();
                return true;
            }
            @Override public void changed(boolean picked) { swatchChanged(app.inkWash.selected); }
            // A grey's tap dips a Wet ink brush in it (the wet brush spec), and may offer to shade the page.
            @Override public void greyPicked(int i) { app.inkWash.dip(); }
            // A rub on a grey loads a brush with Paint Load (the oil paint spec); its row's gauge fills.
            @Override public boolean rubbed(double travel) { return app.inkWash.rub(travel); }
            @Override public void covered(RectF area) { }
        }, dropper, this::opacityMenu);
        lasso = app.iconButton(DrawLayout.LASSO, R.drawable.ic_ph_lasso, this::tapLasso).bar();
        ghost = app.iconButton(DrawLayout.REFRESH, R.drawable.ic_ph_ghost, () -> { if (app.pad != null) app.pad.refreshAsked("Refresh button"); });
        ghost.iconHalf = DrawLayout.ICON_SIDE / 2;
        // A menu's button: an icon alone, no button grey (the owner, 2026-10-21); the Lasso and Refresh too.
        for (View plain : new View[]{lasso, ghost}) plain.setBackground(null);
        ghostRow.addView(ghost, item(fitted.icon));
        // The layer button: where the pen lifts on it picks the layer (DrawLayout.layerAt), its whole height in thirds.
        float[] liftY = {0};
        ToolButton[] layer = new ToolButton[1];
        layer[0] = app.iconButton(DrawLayout.layerSaid(2, false), R.drawable.layer_middle, () -> app.pickLayer(DrawLayout.layerAt(liftY[0], 0, layer[0].getHeight())));
        layers = layer[0];
        layers.setBackground(null);
        layers.setOnTouchListener((v, e) -> { liftY[0] = e.getY(); return false; });
        // Its hold: each layer's eye (the owner, 2026-11-06: "long press should bring up the visibility options"), in the fan too.
        layers.setOnLongClickListener(v -> { layersPopup(); return true; });
        layerActions(layers, app::pickLayer);
        // The droplet while a Slow wash is still settling; a tap lays it now.
        wet = new WetIndicator(app, new WetIndicator.Host() {
            @Override public boolean wet() { return app.inkWash.session.wet(); }
            @Override public boolean moving() { return app.inkWash.session.moving(); }
            @Override public boolean stroking() { return app.pad != null && app.pad.stroking(); }
            @Override public boolean onlyRendering() { return app.inkWash.session.onlyRendering(); }
            @Override public void dry() { if (!app.busy()) app.pad.dryNow(); }
            @Override public boolean hurrying() { return app.inkWash.session.hurrying(); }
            @Override public void update(View indicator, Runnable change) { app.selectionFeedback.update(indicator, change); }
        });
        app.inkWash.session.wetChanged = wet::changed;
        // Under the icon block the wet indicator on its own line (the owner, 2026-10-29: "move the wet indicator to its own line").
        // The sand timer, Show Kinds and Play the menu's rows since 2026-11-06 (the owner: "move the timer, visibility, and mischief tools to the dropdown").
        templateRow.addView(wet, tile(fitted.half(), fitted.wet));
        showModes();
        // The page bar (notebooks), over the page.
        turner = new PageTurner(app, new PageTurner.Host() {
            @Override public void previous() { closePanel(); app.turnPage(-1); }
            @Override public void next() { closePanel(); app.turnPage(1); }
        }, true);
        zoom = turner.zoom;
        // The brush line: the name at its left, a note at its right, all in captions.
        brushLine = LabUi.row(app);
        brushLine.setGravity(Gravity.CENTER_VERTICAL);
        brushLine.setPaddingRelative(app.dp(DrawLayout.LINE_PAD), 0, app.dp(DrawLayout.LINE_END), 0);
        brushLine.setBackgroundColor(Theme.PAPER);
        brushName = captionLine(new Caption(app));
        // A fixed room for the words, so a new name needs no layout (its words put on the panel through the fast display
        // alone, updateLine).
        brushLine.addView(brushName, new LinearLayout.LayoutParams(app.dp(DrawLayout.LINE_NAME), -1));
        brushLine.addView(new View(app), new LinearLayout.LayoutParams(0, 1, 1));
        noteText = captionLine(new TextView(app));
        noteText.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        noteText.setVisibility(View.GONE);
        LinearLayout.LayoutParams notePlace = new LinearLayout.LayoutParams(-2, -1);
        notePlace.setMarginStart(app.dp(DrawLayout.NOTE_GAP));
        brushLine.addView(noteText, notePlace);
        // No tap on the name (the release pare-down, 2026-11-05): Brush Lab is a slot's long press's Edit Brush alone.
        statusCorner = captionLine(new TextView(app));
        statusCorner.setClickable(false);
        showBrushLine();
        // The column, top to bottom (DrawLayout.COLUMN).
        for (DrawLayout.Part part : DrawLayout.COLUMN) {
            switch (part) {
                case TIMER: view.addView(timerRow, item(DrawLayout.TIMER)); break;
                case MENU: view.addView(menuRow, new LinearLayout.LayoutParams(-1, -2)); break;
                case GAP: gap(); break;
                // The tray: the nine slots.
                case TRAY: for (int t = 0; t < ToolSlots.COUNT; t++) view.addView(tools[t], trayTile(fitted)); break;
                // Fill in, the smudge tool and the eraser, a tray row each, their drawings on it (Fill in's its slot's was, 2026-11-06).
                case TOOLS: for (int t : DrawLayout.AFTER_TRAY) view.addView(tools[t], trayTile(fitted)); break;
                case BLOCK: {
                    // The Dropper and the Lasso beside it (the owner, 2026-10-24: "lasso can move beside picker"), Refresh.
                    LinearLayout pickRow = LabUi.row(app);
                    pickRow.setGravity(Gravity.CENTER_HORIZONTAL);
                    pickRow.addView(swatches.dropper);
                    pickRow.addView(lasso);
                    // The wet indicator under them (the owner, 2026-10-29: "move the wet droplet icon ... above the ghost button"); Refresh at the column's foot (PLAY).
                    for (View row : new View[]{pickRow, templateRow}) view.addView(row, new LinearLayout.LayoutParams(-1, -2));
                    break;
                }
                case FACES: view.addView(swatches.view, new LinearLayout.LayoutParams(-1, -2)); break;
                case PLAY: view.addView(ghostRow, ghostPlace(fitted)); break;
                case LAYERS: view.addView(layers, item(fitted.layers)); break;
                case STRETCH: view.addView(new View(app), new LinearLayout.LayoutParams(1, 0, 1)); break;
                default: break;
            }
        }
        for (int t = 0; t < ToolSlots.TOOLS; t++) showBrush(t, false);
        // A height of its own: the items fitted anew.
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> { if (b - t != ob - ot) fit(); });
        // Gravity: the page's tilt from the accelerometer, only while something reads it and the screen is up.
        new TiltFeed(wet, app.inkWash.session);
        swatches.show(app.inkWash.selected);
        place(fitted);
    }
    /** Refresh's row's place: the column's width, about a centimetre over Zen Mode's icon. */
    private LinearLayout.LayoutParams ghostPlace(DrawLayout at) {
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, -2);
        place.bottomMargin = app.dp(at.ghostMargin());
        return place;
    }
    /** Adds a gap, {@link DrawLayout#gap} tall. */
    private void gap() { View gap = new View(app); gaps.add(gap); view.addView(gap); }
    /** A place in the column {@code height} dp tall, as wide as the column's inside. */
    private LinearLayout.LayoutParams item(int height) { return new LinearLayout.LayoutParams(-1, app.dp(height)); }
    /** A tray row's or a tool cell's place, {@code width × height} dp in whole halves, so its row fills its button exactly. */
    private LinearLayout.LayoutParams tile(int width, int height) {
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(2 * app.dp(width / 2), 2 * app.dp(height / 2));
        place.gravity = Gravity.CENTER_HORIZONTAL;
        return place;
    }
    /**
     * A tray row's place (a slot's, the smudge tool's, the eraser's): the column's inside and its outer margin, which it
     * reaches over to the screen's edge, so its drawing touches it (the owner, 2026-10-27: "move tools slightly left,
     * touching edge"), on the right with the column on the right.
     */
    private LinearLayout.LayoutParams trayTile(DrawLayout at) {
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(2 * app.dp(trayHalf(at)), 2 * app.dp(at.slot / 2));
        place.gravity = right ? Gravity.RIGHT : Gravity.LEFT;
        if (right) place.rightMargin = -app.dp(at.edge); else place.leftMargin = -app.dp(at.edge);
        return place;
    }
    /** Half a tray row's width, dp: the column's inside and its outer margin. */
    private static int trayHalf(DrawLayout at) { return (at.inner + at.edge) / 2; }
    /** The chrome for the screen as it is now ({@link DrawLayout}): the root's size once laid out, else the display's, turned with the app. */
    DrawLayout layout() {
        int width = app.root.getWidth(), height = app.root.getHeight();
        if (width <= 0 || height <= 0) {
            android.util.DisplayMetrics screen = app.getResources().getDisplayMetrics();
            width = app.landscape ? screen.heightPixels : screen.widthPixels;
            height = app.landscape ? screen.widthPixels : screen.heightPixels;
        }
        return new DrawLayout(Ui.dpOf(app, width), Ui.dpOf(app, height));
    }
    /**
     * Fits the column's items to its height ({@link DrawLayout}); tools whose size changed draw their row at once.
     * Nothing changes when the sizes are the same: false.
     */
    boolean fit() {
        DrawLayout now = layout();
        if (now.same(fitted)) return false;
        boolean slots = now.slot != fitted.slot || now.inner != fitted.inner || now.icon != fitted.icon || now.wet != fitted.wet;
        fitted = now;
        place(now);
        if (slots) { for (int tool = 0; tool < ToolSlots.TOOLS; tool++) showBrush(tool, true); showModes(); }
        return true;
    }
    /**
     * Menu in a square cell at the screen's corner, the column's outer side ({@code right}: the column on the right), so
     * its icon sits about as far from the side as from the top (the owner, 2026-10-30: "left padding and top padding
     * should agree a bit more"; on the column's axis until then, about twice as far from the side).
     */
    void cornered(boolean right) {
        LinearLayout row = (LinearLayout) menu.getParent();
        row.setGravity(right ? Gravity.END : Gravity.START);
        // The column's PAD at the side as at the top: the icon's gaps to the side and the top within a few pixels.
        int pad = app.dp(DrawLayout.PAD);
        row.setPadding(right ? 0 : pad, 0, right ? pad : 0, 0);
    }
    /** Every item at {@code at}'s size. */
    private void place(DrawLayout at) {
        menu.setLayoutParams(tile(at.menuRow(), at.menuRow()));
        // The gaps, the picker box's snug (DrawLayout.gapHeight).
        for (int g = 0; g < gaps.size(); g++) gaps.get(g).setLayoutParams(new LinearLayout.LayoutParams(1, app.dp(at.gapHeight(g))));
        // A slot's row for the slots, the smudge tool and the eraser (the stamp tool has no place in the column).
        for (int t = 0; t < ToolSlots.TOOLS; t++) tools[t].setLayoutParams(t == InkWash.STAMP ? tile(at.half(), at.icon) : trayTile(at));
        wet.setLayoutParams(tile(at.half(), at.wet));
        swatches.apply(at, right);
        for (View cell : new View[]{swatches.dropper, lasso}) cell.setLayoutParams(tile(at.half(), at.icon));
        // Refresh the whole row, a finger's target (52 dp tall on the Manta, the column's inside wide).
        ghost.setLayoutParams(item(at.icon));
        ghostRow.setLayoutParams(ghostPlace(at));
        // The layer button: its row, its icon as tall as it holds up to LAYERS_ICON.
        layers.setLayoutParams(item(at.layers));
        layers.setVisibility(at.layers > 0 ? View.VISIBLE : View.GONE);
        sizeLayers(at.layers);
        turner.place(at, right);
    }
    /** The layer button's icon for a row {@code tall} dp tall: as tall as it holds, up to {@link DrawLayout#LAYERS_ICON}. */
    private void sizeLayers(int tall) {
        layers.iconHalf = Math.min(DrawLayout.LAYERS_ICON, tall - 2 * DrawLayout.TRAY_TOP) / 2;
        layers.iconHalfWidth = layerHalfWidth(app, layers.iconHalf);
    }
    /**
     * The Lasso's and the Dropper's buttons as cells of the tool grid ({@link SlotTile} rows, icon in place of a
     * stroke, as {@link #showIcon} draws a tool's): each side bar on the column's edge side of its cell.
     */
    private void showModes() {
        int halfWidth = fitted.half() / 2;
        // Every half-row cell's icon one square (DrawLayout.ICON_SIDE), its row the square and SlotTile's pad above and below
        // it; the left cell's bar at its left, the right's at its right, so each pair sits evenly about the column's axis.
        int iconHalf = DrawLayout.ICON_SIDE / 2 + DrawLayout.TRAY_TOP;
        for (ToolButton b : new ToolButton[]{lasso, swatches.dropper}) { b.iconHalf = iconHalf; b.iconHalfWidth = halfWidth; }
        // The Dropper and the Lasso in the icon block, the Dropper's left: the Dropper's bar at its left, the Lasso's right.
        lasso.iconOnly(face(null, app.getDrawable(R.drawable.ic_ph_lasso), true, false), face(null, app.getDrawable(R.drawable.ic_ph_lasso), false, false));
        swatches.dropper.iconOnly(face(null, app.getDrawable(R.drawable.ic_ph_eyedropper), true, true), face(null, app.getDrawable(R.drawable.ic_ph_eyedropper), false, true));
    }
    /**
     * A tap on the menu's Show Kinds (the column's eye until 2026-11-06; the owner, 2026-10-28: "just one popup where you can adjust the visibility of each type at the
     * same time. use the eyes as the button below the names"): Pen, Paint and Dry Media side by side, light as the
     * eraser's ({@link #lightRow}), each kind's step's eye ({@link PageTurner#STEP_ICONS}) under its name; a tap on an eye
     * steps that kind on, Full, Half, Faint, Hidden. Template first while the page has guides (the owner, 2026-10-28:
     * "add the template visibility there whenever there is one"), its eye the notebook's guides' step. Open while its eyes are tapped
     * ("keep it open until the person has set things like they want"), until the pen goes down elsewhere or a second
     * tap on Show Kinds.
     */
    void kindsPopup() {
        if (app.busy() || app.pad.document == null) return;
        if (label != null && labelContent == kindsContent) { hideLabel(); return; }
        app.pad.finishStroke();
        LinearLayout row = new LinearLayout(app);
        row.setBackground(Theme.menu(app));
        int fine = app.dp(Theme.FINE);
        row.setPadding(fine, fine, fine, fine);
        // Each column as wide as the widest name, so none is cut off (the owner, 2026-10-29: "some words are cut off").
        row.setMeasureWithLargestChildEnabled(true);
        for (int c = DrawLayout.kindsFirst(guides()); c < EraseKind.LABELS.length; c++) {
            int choice = c;
            LinearLayout column = eyeCell(DrawLayout.kindsName(choice), true, false, () -> DrawLayout.kindsStep(choice, app.shownKinds(), guides()),
                    step -> DrawLayout.kindsSaid(choice, step), step -> {
                        if (choice == 0) { if (app.book != null) app.showGuides(app.book.options().withNextGuidesStep().guidesShown); }
                        else app.showKind(choice, step);
                    }, null);
            row.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
        }
        kindsContent = row;
        showLabel(row, true, 0, templateRow);
    }
    /**
     * The layer button's hold (the owner, 2026-11-06: "long press should bring up the visibility options"): its three
     * layers, Layer 3 over 2 over 1 as the button stacks them, light as Show Kinds' popup, each its name, the one drawn on
     * marked, and its eye ({@link InkWashLayers#stepOf}); a tap on an eye steps that layer Full, Half, Faint, Hidden
     * ({@link PaintActivity#layerStep}), a tap on a name picks that layer ({@link PaintActivity#pickLayer}) and closes it. A
     * layer the page has yet to make shows Full. Beside the button, or Zen Mode's fan while it holds the button; a second
     * hold closes it, as the pen elsewhere does.
     */
    void layersPopup() {
        if (app.busy() || app.pad == null || app.pad.document == null) return;
        if (label != null && labelContent == layersContent) { hideLabel(); return; }
        app.pad.finishStroke();
        LinearLayout list = new LinearLayout(app);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackground(Theme.menu(app));
        int fine = app.dp(Theme.FINE);
        list.setPadding(fine, fine, fine, fine);
        int drawn = layerInUse();
        for (int l = InkWashLayers.LAYERS; l >= 1; l--) {
            int layer = l;
            list.addView(eyeCell(DrawLayout.layerNote(layer), false, layer == drawn, () -> InkWashLayers.stepOf(app.pad.document, layer),
                    step -> DrawLayout.layerStepNote(layer, step), step -> app.layerStep(layer, step), () -> { hideLabel(); app.pickLayer(layer); }),
                    new LinearLayout.LayoutParams(-1, -2));
        }
        layersContent = list;
        showLabel(list, true, 0, layers);
    }
    /** The layer button's hold's popup, while it shows. */
    private View layersContent;
    /**
     * A light popup's cell (Show Kinds', the layer button's): {@code name} over ({@code over}) or beside its eye, the step's
     * ({@link PageTurner#STEP_ICONS}), {@code chosen} marked; a tap on the eye {@code next} the step after {@code step}'s
     * (Hidden going back to Full), the eye then showing it, said by {@code said}; a tap on the name {@code picked} (null: none).
     */
    private LinearLayout eyeCell(String name, boolean over, boolean chosen, java.util.function.IntSupplier step, java.util.function.IntFunction<String> said,
                                 java.util.function.IntConsumer next, Runnable picked) {
        LinearLayout cell = new LinearLayout(app);
        cell.setOrientation(over ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        cell.setGravity(over ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        int pad = app.dp(DrawLayout.LIGHT_PAD), eyeSide = app.dp(DrawLayout.KINDS_EYE);
        TextView words = Theme.menuRow(new TextView(app), chosen);
        words.setText(name); words.setSingleLine(true); words.setGravity(over ? Gravity.CENTER : Gravity.CENTER_VERTICAL | Gravity.START);
        words.setPadding(pad, 0, pad, 0);
        if (picked != null) words.setOnClickListener(v -> picked.run());
        cell.addView(words, over ? new LinearLayout.LayoutParams(-2, app.dp(DrawLayout.LIGHT_ROW)) : new LinearLayout.LayoutParams(0, eyeSide, 1));
        android.widget.ImageView eye = new android.widget.ImageView(app);
        eye.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        Runnable shown = () -> {
            int at = step.getAsInt();
            eye.setImageDrawable(app.getDrawable(PageTurner.STEP_ICONS[at + 1]));
            eye.setContentDescription(said.apply(at));
        };
        shown.run();
        eye.setOnClickListener(v -> { next.accept((step.getAsInt() + 1) % NotebookOptions.GUIDE_STEPS.length); shown.run(); });
        cell.addView(eye, over ? new LinearLayout.LayoutParams(-1, eyeSide) : new LinearLayout.LayoutParams(eyeSide, eyeSide));
        return cell;
    }
    /** Show Kinds' popup, while it shows. */
    private View kindsContent;
    /** The page drawn on's guides' step ({@link NotebookOptions#guidesShown}), -1 while it has none. */
    private int guides() {
        DrawingBook book = app.book;
        return book == null || book.template(book.index()).equals(NotebookTemplate.BLANK) ? -1 : book.options().guidesShown;
    }
    /** A tap on the Lasso: taken up (from the edge strip's hold too); in use with nothing floating and one kept, Paste beside it ({@link DrawLayout#lassoTap}). */
    private void tapLasso() {
        if (app.busy() || !lasso.isEnabled()) return;
        // A piece the edge strip (or Duplicate) left floating would put the Lasso away once done, so the pen's next loop drew ink (the owner, 2026-11-05).
        app.pad.keepLasso();
        DrawLayout.LassoTap tap = DrawLayout.lassoTap(app.pad.lassoOn && !app.pad.lassoDeletes, app.pad.hasSelection(), app.pad.hasKept());
        if (tap == DrawLayout.LassoTap.TAKE_UP) takeUpLasso(false);
        else if (tap == DrawLayout.LassoTap.MENU)
            app.menu(anchor(lasso), "lasso menu", new String[]{DrawLayout.LASSO_PASTE}, new int[]{R.drawable.ic_ph_clipboard}, new Runnable[]{app.pad::pasteSelection}, 160);
    }
    /**
     * The Lasso's bar over the floating piece (the owner, 2026-10-28: "in supernote there is just one lasso tool...
     * select something and then when the bounding box appears, above it is a toolbar offering choices for what to do with
     * it"): Cut, Duplicate ("duplicate instead of copy", later that day), Make Stamp (2026-11-02) and Delete ({@link DrawLayout#LASSO_BAR}) in a light row ({@link #lightRow}), just over {@code
     * piece}, the piece's frame and knob in the root's app coordinates ({@link DrawingPad#lassoBar}), under it with no room
     * over it, on the page; null: gone (the piece put down, cut or deleted, or the pen moving it). The pen on it keeps it
     * ({@link #penDown}); a locked page refuses Cut and Delete as the pieces' own ({@link DrawingPad#deleteSelection}).
     */
    void lassoBar(RectF piece) {
        boolean shown = label != null && labelContent == lassoContent;
        if (piece == null) { if (shown) hideLabel(); return; }
        if (!shown) lassoContent = lightRow(DrawLayout.LASSO_BAR, -1, DrawLayout.LASSO_BAR.length, false, choice -> {
            if (app.busy()) return;
            if (choice == 0) app.pad.cutSelection(); else if (choice == 1) app.pad.duplicateSelection(); else if (choice == 2) app.pad.stampSelection(); else app.pad.deleteSelection();
        });
        int[] size = labelSize(lassoContent);
        RectF page = Popups.bounds(app.root, app.pad);
        float over = piece.top - app.dp(8) - size[1];
        showLabel(lassoContent, true, 0, Popups.rect(Popups.clamp(piece.centerX() - size[0] / 2f, page.left, page.right - size[0]),
                Popups.clamp(over >= page.top ? over : piece.bottom + app.dp(8), page.top, page.bottom - size[1]), size[0], size[1]));
    }
    /** The Lasso's bar, while it shows. */
    private View lassoContent;
    /**
     * What was in use when the edge strip's hold began: the hold's own loop already, or a hold that does nothing
     * ({@code heldTrash}), the other loop, the Dropper; and whether the hold's loop deletes.
     */
    private boolean heldTrash, heldLasso, heldDropper, heldDeletes;
    /**
     * The edge strip's two-finger hold ({@link EdgeHold}): System Settings' Edge Strip Hold ({@link EdgeHold.Choice}), the
     * Lasso's loop that deletes (the Trash, at first) or the Lasso's own while held (left on if it was on), then what was
     * in use, a piece it lifted keeping the Lasso until the piece is done ({@link DrawingPad#lassoBackWhenDone}); Nothing
     * leaves the pen as it is.
     */
    void edgeTrash(EdgeHold.Act act) {
        if (app.pad == null) return;
        if (act == EdgeHold.Act.ENTER) {
            EdgeHold.Choice choice = app.prefs.edgeHold();
            heldDeletes = choice == EdgeHold.Choice.TRASH;
            heldTrash = choice == EdgeHold.Choice.NOTHING || app.pad.lassoOn && app.pad.lassoDeletes == heldDeletes || !lasso.isEnabled();
            heldLasso = app.pad.lassoOn && app.pad.lassoDeletes != heldDeletes; heldDropper = app.pad.dropperOn;
            if (!heldTrash) takeUpLasso(heldDeletes);
        } else if (act == EdgeHold.Act.LEAVE && !heldTrash) {
            if (heldLasso) takeUpLasso(!heldDeletes);
            else if (!app.pad.lassoBackWhenDone()) { app.pad.lasso(false); if (heldDropper) app.pad.dropper(true); lassoChanged(); }
        }
    }
    /** The Lasso's loop taken up, lifting a piece or deleting what it rings ({@code deletes}, the edge strip's hold): a piece floating is put down first. */
    private void takeUpLasso(boolean deletes) {
        closePanel(); app.pad.words.close(); app.pad.text.close(); app.pad.dropper(false);
        if (app.pad.lassoOn) app.pad.lasso(false);
        app.pad.lassoDeletes = deletes;
        app.pad.lasso(true);
        lassoChanged();
    }
    /**
     * The Lasso, the Dropper or Shapes taken up or put down: its side bar, in place of the tool's it gives way to (Shapes
     * draws with the slot's grey and size; a tap on the slot puts it down).
     */
    void lassoChanged() {
        boolean on = app.pad != null && app.pad.lassoOn, dropping = app.pad != null && app.pad.dropperOn;
        boolean shaping = app.pad != null && app.pad.shapesOn && !on && !dropping;
        // The side bars that move in one update of the column, binary where they are ink and paper, as a tool pick's.
        app.selectionFeedback.updateParts(view, () -> {
            // The edge strip's hold marks the Lasso too: its loop deletes while held.
            lasso.mark(on); swatches.dropper.mark(dropping);
            tools[app.inkWash.selected].mark(!on && !dropping && !shaping);
        });
        if (lasso.isEnabled()) lasso.setContentDescription(DrawLayout.LASSO + (on ? ", in use" : ""));
        swatches.dropper.setContentDescription(DrawLayout.DROPPER + (dropping ? ", in use" : ""));
    }
    /**
     * A tap on the Dropper ({@link DrawLayout#DROPPER}): taken up, the Lasso and Words put away; taken up already, put
     * down with nothing picked. The tool in use stays selected all along, so it draws again once a grey is picked.
     */
    private void tapDropper() {
        if (app.busy()) return;
        boolean on = !app.pad.dropperOn;
        closePanel(); app.pad.words.close(); app.pad.text.close();
        if (on && app.pad.lassoOn) app.pad.lasso(false);
        app.pad.dropper(on);
        lassoChanged();
    }
    /**
     * The Dropper picked {@code tone} on the page (0 to 255, {@link ToneDocument#shownTone}): the tool in use's grey is
     * the one it lays it at ({@link ToolSwatches#drop}), as a tap on the strip picks one, said beside the strip; the Dropper is down again.
     * The eraser and the smudge tool lay no grey: the brushes' grey, as the page shows it.
     */
    void dropped(int tone) {
        lassoChanged();
        int tool = app.inkWash.selected;
        Brush brush = DrawLayout.greys(tool) ? app.inkWash.brush(tool) : null;
        double darkness = app.inkWash.slots.swatches.drop(InkWash.key(tool), brush, tone);
        swatchChanged(tool);
        if (brush != null) app.inkWash.dip();
        say(ToolSwatches.greyWords(darkness, brush == null ? tone : ModularSizes.swatch(brush.parts, darkness)), LABEL_MS, swatches.strip());
    }
    /**
     * Says the lasting status in the foot's corner (the owner, 2026-11-02: "remove the notebook name from the bottom
     * right... use it for system messages (like connected to remote, etc)", as the browser's line does): Draw Together's
     * "Connected, 1 guest" ({@link LiveProbe}); null, nothing. A plain redraw of the small words; nothing if unchanged.
     */
    void statusCorner(String words) {
        status = words;
        String shown = hint != null ? hint : words == null ? "" : words;
        if (!shown.contentEquals(statusCorner.getText())) statusCorner.setText(shown);
    }
    /** The slots the column shows: the notebook's Brushes on Bar, at most the screen's ({@link DrawLayout#trays}). */
    int slotsShown() { return Math.min(app.rules().slotsShown(), fitted.trays); }
    /** The lasting status as last said, and the stamp hint shown in its place while there is one ({@link DrawLayout#stampHint}). */
    private String status, hint;
    /** The stamp hint for the tool in use now: shown in the foot's corner, or gone. */
    private void stampHint() {
        String now = DrawLayout.stampHint(app.inkWash.brush(app.inkWash.selected), app.prefs.stampPlaced());
        if (!java.util.Objects.equals(now, hint)) { hint = now; statusCorner(status); }
    }
    /**
     * A stroke laid: with the hint showing (a Stamp in use), the first stamp is printed, and the hint goes for good.
     * shortcut: any stroke laid while a Stamp is in use counts (a finger's too), fine for a one-time hint.
     */
    void laid() { if (hint != null) { app.prefs.setStampPlaced(true); stampHint(); } }
    /** Says {@code text} beside Menu, whose Play row holds Mirrors (the mirrors' notes). */
    void sayMirrors(String text, long ms) { say(text, ms, menu); }
    /** Lays the column out, its ink rule on the page's side: its right, or its left when {@code right} (the column on the right), and fits it, for the screen as laid out now. */
    void arrange(boolean right) {
        this.right = right;
        closePanel();
        partialGrey();
        LayerDrawable ruled = new LayerDrawable(new Drawable[]{new ColorDrawable(Theme.INK), new ColorDrawable(Theme.PAPER)});
        int line = app.dp(DrawLayout.BAR_RULE);
        ruled.setLayerInset(1, right ? line : 0, 0, right ? 0 : line, 0);
        view.setBackground(ruled);
        // Its one inner margin each side (DrawLayout.edge), every row on its axis; its foot clear of Zen Mode's cell, which
        // reaches over the brush line under it (DrawLayout.columnHeight).
        // The far margin takes what rounding leaves, so the rows' whole cells (tile) fit the inside exactly: a cell a pixel
        // wider than it was clipped, and a clipped control can't use the fast display (every change an Android redraw).
        int edge = app.dp(fitted.edge), far = app.dp(fitted.side) - line - edge - 2 * app.dp(fitted.inner / 2);
        view.setPadding((right ? line + far : edge), app.dp(DrawLayout.PAD), (right ? edge : far + line), app.dp(DrawLayout.PAD + fitted.zenHeight() - fitted.footHeight()));
        // The brush line reads from Zen Mode's button outward, the far corner left to the palm.
        brushLine.setLayoutDirection(right ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        // The page bar's Zoom at the end away from the column, whichever side that is (place); the tray's rows to the
        // screen's edge on the column's side, their drawings mirrored.
        if (!fit()) { place(fitted); for (int tool = 0; tool < ToolSlots.TOOLS; tool++) showBrush(tool, true); }
    }
    /**
     * The column and the page bar drawn again through Android (after a full e-ink refresh of the page), and the column
     * and the brush line refreshed on the panel, every pixel driven again: under partial grey ({@link #partialGrey}) an
     * Android redraw of what is unchanged drives nothing, so it no longer cleans them.
     */
    void invalidate() {
        Ui.invalidateTree(view); Ui.invalidateTree(turner.view); Ui.invalidateTree(brushLine);
        for (View part : new View[]{view, brushLine, turner.view}) app.selectionFeedback.clean(part, new Rect(0, 0, part.getWidth(), part.getHeight()));
    }
    /**
     * The firmware's own update for Android's frames over the column and the brush line set to partial grey (mode 7, data mode 3, as the
     * page's, {@link NativePen}): a redraw of unchanged pixels drives nothing, a change one grey pass with no flash. Its
     * default drove the panel twice, about 500 ms with a blink, for every redraw, even of unchanged pixels, so each change
     * the fast display had already shown blinked again once Android caught up (PickerBench, 2026-10-24: 34 frames before,
     * 0 after; a changed slot mark 21). The page bar too (2026-10-25): its arrows, number and previews are drawn through
     * Android, each redraw two passes with the blink until then. Settings' Standard Android drawing leaves the firmware's
     * default. Off a Supernote the method is missing: nothing.
     */
    void partialGrey() { partialGrey(view, brushLine, turner.view, statusCorner); }
    /**
     * As {@link #partialGrey()}, for {@code parts}: a popup's content too, so it opens in one grey pass, no flash. Every
     * view inside each part too: a mode is a view's own ({@link Ui#cleanWindow} resets each), and a view inside one, as the
     * page bar's previews, is redrawn on its own (the owner, 2026-11-05: after a page turn, "the tiny thumbnails blink on
     * and off briefly": the previews' redraws were the firmware's default, its blink, even unchanged).
     */
    static void partialGrey(View... parts) {
        for (View part : parts) {
            try {
                if (DirectEink.androidDrawing()) View.class.getMethod("resetEinkUpdateMode").invoke(part);
                else View.class.getMethod("setEinkUpdateMode", int.class, int.class).invoke(part, 3, 7);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
            if (part instanceof android.view.ViewGroup)
                for (int i = 0; i < ((android.view.ViewGroup) part).getChildCount(); i++) partialGrey(((android.view.ViewGroup) part).getChildAt(i));
        }
    }

    // Tools

    /** A tap on {@code tool}: selects it; the selected smudge tool again opens its chooser ({@link DrawLayout#tap}); a selected slot again, nothing. */
    private void choose(int tool) {
        if (app.busy()) return;
        // A tool chosen: the pen draws again, so Words closes, and the Lasso and Shapes are put down.
        app.pad.words.close(); app.pad.text.close();
        if (app.pad.lassoOn || app.pad.dropperOn || app.pad.shapesOn) { app.pad.shapesOn = false; if (app.pad.lassoOn) app.pad.lasso(false); app.pad.dropper(false); lassoChanged(); }
        switch (DrawLayout.tap(app.inkWash.selected, tool)) {
            case SELECT: selectTool(tool); break;
            case SMUDGES: closePanel(); break; // its chooser is the long press's: a tap only picks the tool
            default: break;
        }
    }
    /** A tap on {@code tool} elsewhere than its row (Zen Mode's fan, {@link ZenFanView}): as its row's tap. */
    void pick(int tool) { choose(tool); }
    /**
     * A long press on {@code tool}'s row, or on it in Zen Mode's fan: a slot's tool picker beside it (a tap only selects
     * it; the owner, 2026-10-25: "long press on any slot to open. single tap selects the tool"), Clean Brush first while
     * its brush holds paint (the refocus, 2026-10-24); the eraser's kinds and Clear page; the smudge tool picked and its
     * chooser of smudge brushes (the owner: quick to pick, options on hold).
     */
    void hold(int tool) {
        if (tool == InkWash.ERASER) { eraserMenu(); return; }
        if (app.busy()) return;
        if (tool == InkWash.SMUDGE) { if (app.inkWash.selected != InkWash.SMUDGE) choose(InkWash.SMUDGE); tapSmudge(); return; }
        if (tool >= ToolSlots.COUNT) return;
        app.pad.words.close(); app.pad.text.close();
        if (app.inkWash.dirty(tool)) cleanMenu(tool); else app.toolPicker(tool, anchor(tool));
    }
    /** Zen Mode's fan while open ({@link ZenFanView}): the column is gone, so the menus open beside the fan instead; else null. */
    View zenAnchor;
    /** What {@code tool}'s menus open beside: its row, or Zen Mode's fan. */
    private View anchor(int tool) { return anchor(tools[tool]); }
    /** What {@code button}'s menus open beside: the button, or Zen Mode's fan, so they never cover it. */
    private View anchor(View button) { return zenAnchor != null ? zenAnchor : button; }
    /** The brush line's words' size, px: Zen Mode's fan's strip's too (the owner, 2026-11-07: "use the same font size along the left edge as the brush info"). */
    float lineTextSize() { return brushName.getTextSize(); }
    /** What Zen Mode's fan shows of the column's, in {@link ZenFan#cell}'s order: Menu, the Dropper, the Lasso, the droplet, the layer button, the brush line. */
    private View[] zenStrip() { return new View[]{menu, swatches.dropper, lasso, wet, layers, brushLine}; }
    /** Where each lent button was ({@link #lend}): its row, its place in it, its layout; null while home. */
    private final android.view.ViewGroup[] lentFrom = new android.view.ViewGroup[ZenFan.CELLS];
    private final int[] lentAt = new int[ZenFan.CELLS];
    private final android.view.ViewGroup.LayoutParams[] lentPlace = new android.view.ViewGroup.LayoutParams[ZenFan.CELLS];
    /**
     * Zen Mode's fan open: the column's own buttons lent to its line (the owner, 2026-11-06), laid on {@code stage} from
     * child {@code index} over {@code cells} (px, {@link ZenFan#cell}'s order), so each does there just what it does in
     * the column, its marks and changes included; the layer button's icon the others' size ("tiny layers"), its thirds
     * still picking. {@link #giveBack} puts them home.
     */
    void lend(android.view.ViewGroup stage, int index, RectF[] cells) {
        View[] buttons = zenStrip();
        for (int i = 0; i < buttons.length; i++) {
            View button = buttons[i];
            lentFrom[i] = (android.view.ViewGroup) button.getParent();
            lentAt[i] = lentFrom[i].indexOfChild(button); lentPlace[i] = button.getLayoutParams();
            lentFrom[i].removeView(button);
            android.widget.FrameLayout.LayoutParams at = new android.widget.FrameLayout.LayoutParams(Math.round(cells[i].width()), Math.round(cells[i].height()), Gravity.TOP | Gravity.START);
            at.leftMargin = Math.round(cells[i].left); at.topMargin = Math.round(cells[i].top);
            stage.addView(button, index + i, at);
        }
        sizeLayers(Ui.dpOf(app, Math.round(cells[ZenFan.LAYERS].height())));
        // The Dropper's and the Lasso's faces their cells' size, smaller than the column's (the owner, 2026-11-07).
        for (int i : new int[]{ZenFan.DROPPER, ZenFan.LASSO}) {
            ToolButton b = (ToolButton) buttons[i];
            b.iconHalf = b.iconHalfWidth = Ui.dpOf(app, Math.round(cells[i].height())) / 2;
        }
        for (View button : buttons) grey(button, true);
        // The brush line shown in the fan (Zen Mode hides it), its name the line's room.
        brushLine.setVisibility(View.VISIBLE);
        brushName.getLayoutParams().width = Math.round(cells[ZenFan.TOOL_LINE].width()) - brushLine.getPaddingLeft() - brushLine.getPaddingRight();
        brushName.requestLayout();
        brushName.setTextColor(ZenFanView.WORDS); noteText.setTextColor(ZenFanView.WORDS);
        // Menu's three lines the Lasso's size beside it.
        menu.iconHalf = DrawLayout.ICON_SIDE * Ui.dpOf(app, Math.round(cells[ZenFan.MENU].height())) / (2 * (DrawLayout.ICON_SIDE + 2 * DrawLayout.TRAY_TOP));
        // Moved, the droplet dropped its next look (WetIndicator.onDetachedFromWindow): it looks again.
        wet.changed();
    }
    /** A lent button drawn in the fan's grey, or not ({@link ZenFanView#greyed}). */
    private static void grey(View button, boolean greyed) {
        if (button instanceof ToolButton) ((ToolButton) button).greyed = greyed;
        else if (button instanceof WetIndicator) ((WetIndicator) button).greyed = greyed;
        else return;
        button.invalidate();
    }
    /** The lent buttons home again, each where it was, last first (should two share a row). */
    void giveBack() {
        View[] buttons = zenStrip();
        for (int i = buttons.length - 1; i >= 0; i--) {
            if (lentFrom[i] == null) continue;
            Ui.detach(buttons[i]);
            lentFrom[i].addView(buttons[i], lentAt[i], lentPlace[i]);
            lentFrom[i] = null; lentPlace[i] = null;
        }
        sizeLayers(fitted.layers);
        for (View button : buttons) grey(button, false);
        menu.iconHalf = DrawLayout.ICON_SIDE / 2;
        brushName.getLayoutParams().width = app.dp(DrawLayout.LINE_NAME);
        brushName.setVisibility(View.VISIBLE);
        brushName.setTextColor(Theme.INK); noteText.setTextColor(Theme.INK);
        brushLine.setVisibility(app.zen ? View.GONE : View.VISIBLE);
        showModes();
        wet.changed();
    }
    /** Whether {@code tool} is on in this notebook ({@link #rulesChanged}). */
    boolean enabled(int tool) { return tools[tool].isEnabled(); }
    /** {@code tool}'s row's picture without its mark, and its size, dp {width, height} ({@link #showBrush}): Zen Mode's fan draws it; mirrored with the column on the right. */
    android.graphics.drawable.Drawable rowPicture(int tool) { return tools[tool].plainIcon(); }
    int[] rowSize(int tool) { return new int[]{2 * tools[tool].iconHalfWidth, 2 * tools[tool].iconHalf}; }
    boolean mirrored() { return right; }
    /**
     * The open notebook's rules changed (a notebook opened or left, its options changed; {@link NotebookRules}): No Erasing shows
     * the eraser's tile disabled (Undo and Redo give a note, {@code PaintActivity.undo}), Brushes the smudge tool's and only its first slots; a tool selected that is off gives way
     * to the first slot.
     */
    void rulesChanged(NotebookOptions rules) {
        boolean noErasing = NotebookRules.eraserOff(rules);
        // The notebook's Brushes on Bar, and the screen's: the Nomad shows eight (DrawLayout.trays).
        int shown = Math.min(rules.slotsShown(), fitted.trays);
        boolean noSmudge = NotebookRules.smudgeOff(rules, app.inkWash.slots.smudge()), noStamp = NotebookRules.smudgeOff(rules, app.inkWash.slots.stamp()),
                noFill = NotebookRules.smudgeOff(rules, ToolSlots.fill());
        off(tools[InkWash.ERASER], noErasing, "Eraser");
        off(tools[InkWash.SMUDGE], noSmudge, "Smudge");
        off(tools[InkWash.STAMP], noStamp, "Stamp");
        off(tools[ToolSlots.FILL], noFill, ToolSlots.FILL_TOOL);
        // Moving paint lifts it: the Lasso is off where erasing is (the edge strip's hold too, edgeTrash).
        if (noErasing && app.pad != null && app.pad.lassoOn) app.pad.lasso(false);
        off(lasso, noErasing, DrawLayout.LASSO);
        lassoChanged();
        for (int slot = 1; slot < ToolSlots.COUNT; slot++) tools[slot].setVisibility(slot < shown ? View.VISIBLE : View.GONE);
        int selected = app.inkWash.selected;
        if (selected == InkWash.ERASER && noErasing || selected == InkWash.SMUDGE && noSmudge || selected == InkWash.STAMP && noStamp || selected == ToolSlots.FILL && noFill || DrawLayout.slotHidden(selected, shown)) selectTool(0);
    }
    /** {@code button} disabled and greyed (a tool this notebook has off), or as it was. */
    private void off(ToolButton button, boolean off, String name) {
        button.setEnabled(!off); button.setAlpha(off ? .3f : 1f);
        if (off) button.setContentDescription(name + ". Off in this notebook");
        else if (name.equals("Eraser")) button.setContentDescription(name);
    }
    /** A tool's name before its brush shows: "Brush" (a slot, no number), "Eraser", "Smudge", "Stamp". */
    /** Selects {@code tool}, a slot, the eraser or the smudge tool, closing any panel; the faces show its sizes and greys. */
    void selectTool(int tool) {
        if (app.pad != null && (app.pad.lassoOn || app.pad.dropperOn || app.pad.shapesOn)) { app.pad.shapesOn = false; if (app.pad.lassoOn) app.pad.lasso(false); app.pad.dropper(false); lassoChanged(); }
        if (tool == app.inkWash.selected) return;
        closePanel();
        // Scribulor: a Spiro's ring set up goes with its tool.
        if (app.pad != null) app.pad.dropRing();
        // The marks, the size and the grey in one update over the column (SelectionFeedback gathers them), the panel's
        // binary one when what changed is ink and paper (the marks alone); the brush line and its note in one more.
        app.selectionFeedback.updateParts(view, () -> {
            tools[app.inkWash.selected].mark(false);
            app.inkWash.select(tool); tools[tool].mark(true);
            swatches.show(tool);
        });
        updateLine(() -> {
            showBrushLine();
            // An eraser that takes one layer says so, so it is never a surprise.
            if (tool == InkWash.ERASER && app.inkWash.eraseKinds != 0) app.message(EraseKind.note(EraseKind.choice(app.inkWash.eraseKinds)));
            // Show Kinds: a brush whose kind isn't Full says so, so nothing is drawn unseen by surprise.
            app.sayKind(tool);
        });
        // Live: the guests' bars follow this tablet's (LiveProbe.toolsChanged).
        app.live.toolsChanged();
    }
    /**
     * Slot {@code slot}'s button shows its row ({@link SlotTile#row}): its brush's picker drawing ({@link
     * DrawLayout#drawing}) or else its tray stroke, no broader than {@link WildSample#trayCap}; its side bar while selected. Drawn in the background, the
     * button keeping what it showed until it arrives; only a button's first, when the bar is built, is drawn at once, so
     * a slot never shows another icon. The selected slot's sizes and greys follow (Brush Lab may have set them).
     */
    /** {@code change} gives {@code button} its new face: on the panel over the row; with Zen Mode's fan open, the fan's copy of it too (the column is hidden). */
    private void faceChanged(View button, Runnable change) {
        ZenFanView fan = app.zenFanView();
        if (fan != null) fan.toolsChanged(change); else app.selectionFeedback.update(button, change);
    }
    void showBrush(int slot) {
        showBrush(slot, false);
        if (slot == app.inkWash.selected) { swatches.show(slot); showBrushLine(); }
    }
    /** One line of captions for the brush line, cut with … to fit. */
    private <T extends TextView> T captionLine(T view) {
        T line = Theme.style(view, TypeStyle.CAPTION);
        line.setSingleLine(true);
        line.setIncludeFontPadding(false);
        line.setEllipsize(android.text.TextUtils.TruncateAt.END);
        line.setGravity(Gravity.CENTER_VERTICAL);
        return line;
    }
    /**
     * Applies {@code change} to the brush line, put on the panel over the line alone once it is shown, the name's words
     * asking Android for no frame (an Android redraw of the line drove the panel about 500 ms, 2026-10-24).
     */
    private void updateLine(Runnable change) {
        if (brushLine.getWidth() <= 0 || !brushLine.isShown()) { change.run(); return; }
        app.selectionFeedback.update(brushLine, () -> {
            boolean was = brushName.quiet;
            brushName.quiet = true;
            try { change.run(); } finally { brushName.quiet = was; }
            // Anything that asks for a layout (a note shown or gone): laid out now, as Android will lay it out, so the update
            // shows it where it ends up and Android's own frame changes nothing more.
            if (brushLine.isLayoutRequested()) {
                brushLine.measure(View.MeasureSpec.makeMeasureSpec(brushLine.getWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(brushLine.getHeight(), View.MeasureSpec.EXACTLY));
                brushLine.layout(brushLine.getLeft(), brushLine.getTop(), brushLine.getRight(), brushLine.getBottom());
            }
        });
    }
    /**
     * The brush line's name: its words change on the panel through {@link SelectionFeedback} alone ({@link #updateLine}),
     * as a marker does. Its room is fixed ({@link DrawLayout#LINE_NAME}), so new words need no layout, only a frame.
     */
    private static final class Caption extends TextView {
        boolean quiet;
        Caption(android.content.Context context) { super(context); }
        @Override public void invalidate() { if (!quiet) super.invalidate(); }
    }
    /**
     * The brush line says the tool in use: a slot's brush's name and words; the eraser's, the smudge tool's or the stamp's
     * name alone. Put on the panel over the line alone, and only when it changed.
     */
    private void showBrushLine() {
        stampHint();
        int tool = app.inkWash.selected;
        Brush brush = app.inkWash.brush(tool);
        String[] said = brush == null ? null : lineWords.get(brush.identity());
        if (brush != null && said == null) lineWords.put(brush.identity(), said = lineWordsOf(brush));
        String words = brush == null ? DrawLayout.toolName(tool) : said[0];
        if (words.contentEquals(brushName.getText())) return;
        updateLine(() -> brushName.setText(words));
        // The screen reader hears the brush in full ({@link BrushVoice#about}), not just its card's words.
        brushName.setContentDescription(brush == null ? words : said[1]);
    }
    /**
     * Shows {@code text} at the brush line's right for {@link DrawLayout#NOTE_MS} (the owner, 2026-10-22: "put it in a
     * dedicated spot... small stuff to the right, same height as the name of the current brush"), so the canvas never
     * covers it: in captions as the name, at most {@link DrawLayout#noteWidth} of the line, cut with … past that; the
     * name gives way first. False when the line isn't showing (Zen Mode): the caller says it another way.
     */
    boolean note(String text) {
        if (brushLine.getVisibility() != View.VISIBLE) return false;
        showNote(text);
        brushLine.removeCallbacks(clearNote);
        brushLine.postDelayed(clearNote, DrawLayout.NOTE_MS);
        brushLine.announceForAccessibility(text);
        return true;
    }
    /** The note at the line's right, or none ({@code null}); put on the panel over the line alone. */
    private void showNote(String text) {
        if (text == null && noteText.getVisibility() != View.VISIBLE) return;
        int line = brushLine.getWidth() - brushLine.getPaddingLeft() - brushLine.getPaddingRight();
        updateLine(() -> {
            // In Zen Mode's fan the note takes the line over, the name gone while it shows (the owner, 2026-11-07: "it takes over the space when there is a message").
            boolean fan = zenAnchor != null;
            if (fan) brushName.setVisibility(text == null ? View.VISIBLE : View.GONE);
            if (line > 0) noteText.setMaxWidth(fan ? line : DrawLayout.noteWidth(line));
            noteText.setText(text);
            noteText.setVisibility(text == null ? View.GONE : View.VISIBLE);
        });
    }
    /**
     * As {@link #showBrush(int)}, for any of the bar's tools: the eraser and the smudge tool show their icon ({@link
     * #showIcon}); {@code now}: drawn at once (the button changed size, so what it shows would be stale).
     */
    private void showBrush(int slot, boolean now) {
        warmLine(slot);
        // Fill in's row is drawn as a slot holding it was: the owner's drawing of it.
        if (slot >= ToolSlots.COUNT && slot != ToolSlots.FILL) { showIcon(slot); return; }
        Brush brush = BrushLab.copyOf(app.inkWash.brush(slot));
        ToolSwatches.Look look = ToolSwatches.picture(brush);
        int halfWidth = trayHalf(fitted), halfHeight = fitted.slot / 2;
        // The whole row's width but its side bar's room, from the screen's edge.
        Rect row = SlotTile.row(app, 2 * app.dp(halfWidth), 2 * app.dp(halfHeight), right, true);
        int width = row.width(), height = row.height();
        ToolButton button = tools[slot];
        button.setContentDescription(brush.shownName());
        // Drawn again only when what it shows changed (the owner, 2026-10-25: "the brush slots.. they dont need to update
        // so often"): a Tools page or Brush Lab return, a notebook's toolbar, an Undo showed every slot again.
        String shows = brush.identity() + " " + width + "x" + height + (right ? " right" : "");
        if (shows.equals(trayShown[slot])) return;
        boolean first = trayShown[slot] == null;
        trayShown[slot] = shows;
        boolean mirror = right;
        Supplier<int[]> stroke = () -> {
            int[] tray = WildSample.slotTray(brush.parts, look, width, height);
            if (!mirror) return tray;
            // Mirrored as the drawings are ({@link #drawing}): in from the screen's edge on the right. A copy: samples are kept.
            int[] mirrored = new int[tray.length];
            for (int i = 0; i < tray.length; i++) mirrored[i] = tray[i - i % width + width - 1 - i % width];
            return mirrored;
        };
        Consumer<Bitmap> show = face -> {
            button.iconHalf = halfHeight; button.iconHalfWidth = halfWidth;
            button.faces(face(face, null, true, right), face(face, null, false, right));
        };
        // A Basic tool the owner drew shows its drawing, the picker's, its tip out of the row's edge (2026-10-26); Finger's
        // menu keeps the stroke (trays), drawn in the background.
        String drawn = DrawLayout.drawing(brush);
        int art = drawn == null ? 0 : app.getResources().getIdentifier(drawn, "drawable", app.getPackageName());
        if (art != 0) {
            Bitmap face = drawing(art, width, height, DrawLayout.TIP_TRIM);
            if (first || now) { show.accept(face); button.invalidate(); }
            else faceChanged(button, () -> show.accept(face));
            samples.request(button, stroke, tray -> { trays[slot] = Bitmap.createBitmap(tray, width, height, Bitmap.Config.ARGB_8888); });
            return;
        }
        Consumer<int[]> shown = tray -> { trays[slot] = Bitmap.createBitmap(tray, width, height, Bitmap.Config.ARGB_8888); show.accept(trays[slot]); };
        // A new size or grey: the row's stroke put on the panel over the row alone, with no Android frame (2026-10-24).
        if (!first && !now) samples.request(button, stroke, tray -> faceChanged(button, () -> shown.accept(tray)));
        else { shown.accept(stroke.get()); button.invalidate(); }
    }
    /**
     * The eraser's or the smudge tool's row under the tray (layout v3, the owner's mockup, 2026-10-26): the owner's drawing
     * of it (the block eraser, the spudger) lying across the row as a slot's stroke does, whole, at most its own size,
     * against the screen's edge ({@link #drawing}; centred until 2026-10-27), its side bar on the page side while selected, nothing else (a held
     * paint's dab beside the tip read as a second mark, the owner, 2026-10-26). The stamp tool, off the column, keeps
     * its icon.
     */
    private void showIcon(int tool) {
        ToolButton button = tools[tool];
        if (tool == InkWash.STAMP) {
            button.iconHalf = fitted.icon / 2; button.iconHalfWidth = fitted.half() / 2;
            button.iconOnly(face(null, app.getDrawable(R.drawable.ic_ph_stamp), true, true), face(null, app.getDrawable(R.drawable.ic_ph_stamp), false, true));
            return;
        }
        if (tool == InkWash.SMUDGE) describeSmudge();
        int halfWidth = trayHalf(fitted), halfHeight = fitted.slot / 2;
        Rect row = SlotTile.row(app, 2 * app.dp(halfWidth), 2 * app.dp(halfHeight), right, true);
        Bitmap drawn = drawing(tool == InkWash.SMUDGE ? R.drawable.tool_smudge : R.drawable.tool_eraser, row.width(), row.height(), 0);
        button.iconHalf = halfHeight; button.iconHalfWidth = halfWidth;
        // The side bar alone, no held paint's dab: beside the drawn tip it read as a second mark (the owner, 2026-10-26).
        button.iconOnly(face(drawn, null, true, right), face(drawn, null, false, right));
    }
    /** The smudge tool's words, its brush and whether it holds paint (its row shows no dab since 2026-10-26). */
    private void describeSmudge() {
        smudgeHeld = app.inkWash.heldGrey(InkWash.SMUDGE);
        tools[InkWash.SMUDGE].setContentDescription(DrawLayout.toolName(InkWash.SMUDGE) + ": " + app.inkWash.brush(InkWash.SMUDGE).name() + (smudgeHeld >= 0 ? ", holds paint" : ""));
    }
    /**
     * The owner's drawing {@code resource} (a PNG at the Manta's pixels or a vector) on paper, {@code width × height} px,
     * never enlarged, flush against the screen's edge (the owner, 2026-10-27: "touching edge"; the eraser's and the smudge
     * tool's centred until then): whole, or with {@code trim} of its width cut at its left (a tool's handle end), as a tray
     * row shows a picker drawing ({@link DrawLayout#TIP_TRIM}). Mirrored with the column on the right, its handle at that edge.
     */
    private Bitmap drawing(int resource, int width, int height, float trim) {
        Drawable art = app.getDrawable(resource);
        float aw = art.getIntrinsicWidth(), ah = art.getIntrinsicHeight(), cut = aw * trim;
        float scale = Math.min(1, Math.min(width / (aw - cut), height / ah));
        float w = aw * scale, h = ah * scale, left = -cut * scale, top = (height - h) / 2;
        Bitmap face = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(face);
        canvas.drawColor(Theme.PAPER);
        if (right) canvas.scale(-1, 1, width / 2f, 0);
        art.setBounds(Math.round(left), Math.round(top), Math.round(left + w), Math.round(top + h));
        art.draw(canvas);
        return face;
    }
    /**
     * A row's look, with its side bar or not ({@code selected}), at its left when {@code barLeft}: the button shows the one
     * for its selection, so no dot. No paint gauge or dab on the drawing screen (the refocus, 2026-10-24: Brush Lab's back room).
     */
    private SlotTile.Face face(Bitmap stroke, Drawable icon, boolean selected, boolean barLeft) {
        return new SlotTile.Face(app, stroke, icon, app.dp(DrawLayout.ICON_SIDE), selected, barLeft, Double.NaN, -1);
    }
    /**
     * A long press on slot {@code slot} while its dirty brush holds paint: beside it, in a light column, Clean Brush (its
     * pen's and its finger's start clean) and Tools (the tool picker on it, as the long press opens otherwise).
     */
    private void cleanMenu(int slot) {
        app.pad.finishStroke();
        View row = lightRow(new String[]{DrawLayout.CLEAN, DrawLayout.TOOLS}, -1, 2, true, choice -> {
            hideLabel();
            if (app.busy()) return;
            if (choice == 1) app.toolPicker(slot, anchor(slot));
            else if (app.inkWash.clean(slot)) heldChanged();
        });
        showLabel(row, true, PageClear.OFFER_MS, anchor(slot));
    }
    /**
     * The smudge tool's icon for {@code brush} in the finger's menu ({@link ToolSlots#smudgeKind}, after Photoshop's Blur
     * and Smudge tools; the bar shows the owner's spudger since layout v3, 2026-10-26): the smearing finger for one that
     * pushes, a brush with a drop for clear water, a brush with a dab of paint for one that keeps its paint, else the
     * blending stump.
     */
    static int smudgeIcon(Brush brush) {
        switch (ToolSlots.smudgeKind(brush)) {
            // A blurring brush shows MonoPaint's blending stump, the rest's too: the drop is the wet indicator's alone
            // (the column pass, 2026-10-23: two drops stacked, no telling them apart).
            case PUSH: return R.drawable.ic_ph_hand_smudge;
            case WATER: return R.drawable.ic_water_brush;
            case DIRTY: return R.drawable.ic_dirty_brush;
            default: return R.drawable.ic_blending_stump;
        }
    }
    /** The grey the smudge tool's icon shows its brush's held paint in, -1 for none ({@link #heldChanged}). */
    private int smudgeHeld = -1;
    /**
     * After a stroke or Clean Brush: the smudge tool's icon again if its brush now holds other paint or none; the size
     * and grey shown again, only if they changed.
     */
    void heldChanged() {
        if (app.inkWash.heldGrey(InkWash.SMUDGE) != smudgeHeld) describeSmudge();
        swatches.show(app.inkWash.selected);
    }
    /** The strokes and loops are drawn no more. */
    void close() { samples.close(); }
    /**
     * {@code tool}'s size or grey changed: the slider's tab and the dot alone. The slot's row and the brush line show the
     * brush, not its size or grey ({@link ToolSwatches#picture}), so a pick draws nothing else (the owner, 2026-10-24: "size
     * picker is still blinking ... why is it redrawing?": the row's stroke and the line's preview were two more grey
     * updates after every lift).
     */
    private void swatchChanged(int tool) { swatches.show(tool); app.live.toolsChanged(); }

    // Clear page

    /**
     * A long press on the eraser's tile: a light row beside it for a few seconds ({@link #lightRow}): what it takes
     * ({@link EraseKind}), the chosen one dotted, and, on a page with something on it, Clear page ({@link PageClear}). A kind's tap picks it
     * (said in a note). Clear page's tap clears every layer, the masking fluid and the magnets as one Undo step, with
     * no question asked, then the page is refreshed once, cleanly.
     */
    private void eraserMenu() {
        if (app.busy() || app.erasingOff()) return;
        app.pad.finishStroke();
        boolean clears = PageClear.offered(app.pad.document);
        String[] texts = java.util.Arrays.copyOf(EraseKind.CHOICES, EraseKind.CHOICES.length + (clears ? 1 : 0));
        if (clears) texts[texts.length - 1] = PageClear.CLEAR;
        View row = lightRow(texts, EraseKind.choice(app.inkWash.eraseKinds), EraseKind.CHOICES.length, true, choice -> {
            hideLabel();
            if (choice == EraseKind.CHOICES.length) {
                if (app.layerEdit(() -> app.pad.document.clearPage())) app.message(PageClear.CLEARED);
                return;
            }
            app.inkWash.eraseKinds = EraseKind.TARGETS[choice]; app.message(EraseKind.note(choice));
            eraserMarked();
            // A kind picked takes the eraser up too (the owner, 2026-10-29: "I'd like the eraser to be selected after a
            // menu selection other than clear page").
            selectTool(InkWash.ERASER);
        });
        showLabel(row, true, PageClear.OFFER_MS, anchor(InkWash.ERASER));
    }

    /**
     * A tap on the sand timer or its countdown: a light column beside it, as the eraser's ({@link #lightRow}), of Off and
     * Timed Pages' steps ({@link SandTimer#SECONDS}), the time it was set to, {@code now} (0: Off), marked; a pick sets it
     * ({@link PaintActivity#setSandTimer}). Timed Pages and the page are left as they are.
     */
    void timerMenu(int now) {
        app.pad.finishStroke();
        String[] texts = SandTimer.labels();
        View row = lightRow(texts, SandTimer.chosen(now), texts.length, true, choice -> { hideLabel(); app.setSandTimer(SandTimer.SECONDS[choice]); });
        showLabel(row, true, PageClear.OFFER_MS, timerRow);
    }
    /**
     * The sand timer or a timed page at zero: its words and Done, light as the eraser's, beside the row until a tap, anywhere
     * ({@link #touchDown}); a timed page's with Next Page between them, which runs {@code next} (null: none).
     */
    void timeUp(Runnable next) {
        String[] words = next == null ? new String[]{SandTimer.TIME_UP, SandTimer.DONE} : new String[]{SandTimer.TIME_UP, NotebookRules.NEXT_PAGE, SandTimer.DONE};
        timeUpRow = lightRow(words, -1, 1, true, choice -> { hideLabel(); if (next != null && choice == 1) next.run(); });
        showLabel(timeUpRow, true, 0, timerRow);
    }
    /** The time-up popup, while it shows. */
    private View timeUpRow;
    /** A touch going down in the window, the pen's or a finger's: the time-up popup closes (the pen closes every label, {@link #penDown}). */
    void touchDown() { if (label != null && labelContent == timeUpRow) hideLabel(); }

    /** The eraser's layer set elsewhere (a tool kit's eraser step, {@link ToolKits}): its note, and its tile's mark. */
    void eraserChanged() {
        int choice = EraseKind.choice(app.inkWash.eraseKinds);
        app.message(EraseKind.note(choice));
        eraserMarked();
    }
    /** The Eraser tile drawn again, its mark ({@link EraseKind#MARKS}) following its layer. */
    private void eraserMarked() { app.selectionFeedback.update(tools[InkWash.ERASER], () -> tools[InkWash.ERASER].invalidate()); }

    // Finger

    /** What a finger choice is called, System Settings' Finger Action chips (2026-11-06; the menu's Finger row's choices until then): Nothing, Eraser, Smudge, or a slot's brush's name (the slots have no numbers). */
    String fingerName(FingerTool.Choice choice) { return pans(choice) ? PAN : choice.label(slot -> app.inkWash.brush(slot).shownName()); }
    /** The eraser's choice where No Erasing is on: it pans ({@link PaintActivity#fingerPans}). */
    static final String PAN = "Pan";
    private boolean pans(FingerTool.Choice choice) { return choice == FingerTool.Choice.ERASER && NotebookRules.keeps(app.rules(), true); }
    /**
     * The Eraser tile's mark while it takes one layer ({@link EraseKind#MARKS}: its number, ink on a paper patch at the
     * row's lower corner on the page side, clear of its side bar); nothing for Everything.
     */
    private void drawEraserMark(Canvas canvas, ToolButton button) {
        String mark = EraseKind.MARKS[EraseKind.choice(app.inkWash.eraseKinds)];
        if (mark.isEmpty()) return;
        float line = app.dp(Theme.FINE), w = app.dp(15), h = app.dp(11), bottom = button.getHeight() - app.dp(1);
        float right = this.right ? app.dp(DrawLayout.barRoom()) + w : button.getWidth() - app.dp(DrawLayout.barRoom());
        markPaint.setStyle(Paint.Style.FILL); markPaint.setColor(Theme.PAPER);
        canvas.drawRect(right - w, bottom - h, right, bottom, markPaint);
        // Its edge grey-400, a rule and not a frame (Scribbler Style, "shade before line").
        markPaint.setColor(Theme.GREY_400); markPaint.setStyle(Paint.Style.STROKE); markPaint.setStrokeWidth(line);
        canvas.drawRect(right - w + line / 2, bottom - h + line / 2, right - line / 2, bottom - line / 2, markPaint);
        markPaint.setColor(Theme.INK); markPaint.setStyle(Paint.Style.FILL); markPaint.setTextSize(app.dp(9)); markPaint.setTextAlign(Paint.Align.CENTER); markPaint.setTypeface(Theme.face(700));
        canvas.drawText(mark, right - w / 2, bottom - h / 2 + app.dp(3), markPaint);
        markPaint.setTextAlign(Paint.Align.LEFT);
    }
    /** Slot {@code slot}'s (or the smudge or stamp tool's) brush changed: {@code said} beside its tile, its button's sample, and the faces if it is selected. */
    /** {@code text} beside tool {@code tool}'s button for {@code ms} (Cut Stamp's words). */
    void sayAt(int tool, String text, long ms) { say(text, ms, tools[tool]); }
    /**
     * What slot {@code slot}'s tile says when picked: its step while a tool kit fills the first slots ("3 Comb rings",
     * {@link ToolKits#said}), else its brush's name.
     */
    String said(int slot) {
        Brush[] bar = new Brush[ToolSlots.COUNT];
        for (int i = 0; i < bar.length; i++) bar[i] = app.inkWash.slots.brush(i);
        String step = ToolKits.said(bar, slot);
        return step != null ? step : app.inkWash.slots.brush(slot).shownName();
    }
    void brushChanged(int slot, String said) {
        app.live.toolsChanged();
        say(said, LABEL_MS, tools[slot]);
        showBrush(slot);
        if (slot == app.inkWash.selected) app.sayKind(slot);
    }

    // The smudge tool

    /** A long press on the smudge tool: its chooser beside it, on the page holding its brush; or closed, if open. */
    private void tapSmudge() {
        View owner = anchor(InkWash.SMUDGE);
        boolean open = panel != null && panelOwner == owner;
        closePanel();
        if (open) return;
        app.pad.finishStroke();
        List<Brush> choices = app.inkWash.slots.smudgeChoices();
        Brush now = app.inkWash.slots.smudge();
        int[] grid = fitted.chooserGrid(choices.size());
        for (int i = 0; i < choices.size(); i++) if (choices.get(i).sameAs(now)) smudgePage = LabPages.of(i, grid[0] * grid[1]);
        LinearLayout content = LabUi.column(app);
        fillSmudges(content, choices);
        openPanel(owner, content, app.dp(fitted.chooserWidth(choices.size())));
    }
    /**
     * The chooser's page of smudge brushes ({@link ToolSlots#smudgeChoices}: Blend and Water, the tool's own first when
     * it is neither), as the Tools page's cards, the tool's own tabbed; page buttons under them when there are more. A card
     * puts its brush in the smudge tool and closes the chooser.
     */
    private void fillSmudges(LinearLayout content, List<Brush> choices) {
        content.removeAllViews();
        int[] grid = fitted.chooserGrid(choices.size());
        int columns = grid[0], rows = grid[1], perPage = columns * rows;
        int pages = LabPages.pages(choices.size(), perPage);
        smudgePage = LabPages.clamp(smudgePage, choices.size(), perPage);
        int card = app.dp(fitted.chooserCard()), width = app.dp(ToolsLayout.CARD_LEAST), line = app.dp(DrawLayout.RULE);
        float lineRoom = ToolsLayout.CARD_LEAST - 2 * LabText.CAPTION_INSET;
        Brush now = app.inkWash.slots.smudge();
        // A menu: the cards on its paper, no grey (their words too), the tool's own tabbed.
        LinearLayout cards = LabUi.column(app);
        for (int r = 0; r < rows; r++) {
            LinearLayout row = LabUi.row(app);
            for (int c = 0; c < columns; c++) {
                int index = smudgePage * perPage + r * columns + c;
                View cell;
                if (index < choices.size()) {
                    ToolsPage.Card shown = ToolsPage.card(choices.get(index), lineRoom);
                    // Its kept name, no "effect": every choice of the smudge tool is one (WildSample.shownName).
                    String name = shown.brush.name();
                    LoopCard view = new LoopCard(app, samples, Collections.singletonList(shown.line), name);
                    view.onPaper = true;
                    view.setSample(shown.brush.identity(), LabLibrary.sampler(shown.brush));
                    view.setNow(shown.brush.sameAs(now));
                    view.setContentDescription(name + ": " + BrushVoice.about(shown.brush.parts));
                    view.setOnClickListener(v -> pickSmudge(shown.brush));
                    cell = view;
                } else { cell = new View(app); cell.setBackgroundColor(Theme.PAPER); }
                LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(width, card);
                if (c < columns - 1) place.rightMargin = line;
                row.addView(cell, place);
            }
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-2, -2);
            if (r < rows - 1) place.bottomMargin = line;
            cards.addView(row, place);
        }
        content.addView(cards, new LinearLayout.LayoutParams(-2, -2));
        // A brush holding paint (Keeps Paint): Clean Brush under the cards empties it, and the chooser closes.
        if (app.inkWash.dirty(InkWash.SMUDGE)) {
            content.addView(new View(app), new LinearLayout.LayoutParams(-1, line));
            LabButton clean = new LabButton(app, 0, DrawLayout.CLEAN, false).menu();
            clean.setOnClickListener(v -> { closePanel(); if (app.inkWash.clean(InkWash.SMUDGE)) heldChanged(); });
            content.addView(clean, new LinearLayout.LayoutParams(-1, app.dp(ToolsLayout.PAGE_BUTTON)));
        }
        if (pages < 2) return;
        // A gap (no grey in a menu), then ‹ n/m ›.
        content.addView(new View(app), new LinearLayout.LayoutParams(-1, line));
        LinearLayout pager = LabUi.row(app);
        pager.setGravity(Gravity.CENTER);
        int button = app.dp(ToolsLayout.PAGE_BUTTON);
        LabButton previous = new LabButton(app, R.drawable.ic_ph_caret_left, null, false);
        previous.setContentDescription("Previous page");
        previous.setOnClickListener(v -> turnSmudges(content, choices, -1));
        pager.addView(previous, new LinearLayout.LayoutParams(button, button));
        TextView count = LabUi.text(app, (smudgePage + 1) + "/" + pages, TypeStyle.MENU);
        count.setSingleLine(true);
        count.setGravity(Gravity.CENTER);
        count.setPadding(app.dp(4), 0, app.dp(4), 0);
        pager.addView(count, new LinearLayout.LayoutParams(-2, -1));
        LabButton next = new LabButton(app, R.drawable.ic_ph_caret_right, null, false);
        next.setContentDescription("Next page");
        next.setOnClickListener(v -> turnSmudges(content, choices, 1));
        pager.addView(next, new LinearLayout.LayoutParams(button, button));
        content.addView(pager, new LinearLayout.LayoutParams(-1, button));
    }
    /** Turns the chooser by {@code delta} pages, drawn again in place over its own area (e-ink). */
    private void turnSmudges(LinearLayout content, List<Brush> choices, int delta) {
        int[] grid = fitted.chooserGrid(choices.size());
        int perPage = grid[0] * grid[1];
        int page = LabPages.clamp(smudgePage + delta, choices.size(), perPage);
        if (page == smudgePage) return;
        smudgePage = page;
        app.selectionFeedback.update(content, () -> fillSmudges(content, choices));
    }
    /** A chooser card: its brush is the smudge tool's now (named beside its tile), and the chooser closes. */
    private void pickSmudge(Brush brush) {
        closePanel();
        if (app.busy()) return;
        app.pad.finishStroke();
        if (app.inkWash.slots.useSmudge(brush)) brushChanged(InkWash.SMUDGE, app.inkWash.slots.smudge().name());
    }

    // The stamp tool

    /**
     * Slot {@code slot}'s stamp menu (a slot holding a Stamp, from its picker's Stamp row; the stamp tool's, beside Menu,
     * until Play's Stamp row went, 2026-10-28): beside its tile, its brush's card (the loop prints its nib), Cut Stamp (the
     * pen's next stroke on the page is a lasso, {@link PaintActivity#cutStamp}), and a few presets for Turns, Spacing and
     * Ink Fade, each a change of the slot's brush Undo takes back; or closed, if open.
     */
    void stampMenu(int slot) {
        View owner = tools[slot];
        boolean open = panel != null && panelOwner == owner;
        closePanel();
        if (open) return;
        app.pad.finishStroke();
        LinearLayout content = LabUi.column(app);
        fillStamp(content, slot);
        // Two cards wide, as the smudge chooser is: at one, the chips' words were cut (12%, 50%, 100%; None, 22%, Strong).
        openPanel(owner, content, app.dp(fitted.chooserWidth(2)));
    }
    private void fillStamp(LinearLayout content, int slot) {
        content.removeAllViews();
        Brush brush = app.inkWash.slots.brush(slot);
        ToolsPage.Card shown = ToolsPage.card(brush, ToolsLayout.CARD_LEAST - 2 * LabText.CAPTION_INSET);
        LoopCard card = new LoopCard(app, samples, Collections.singletonList(shown.line), shown.name);
        card.onPaper = true;
        card.setSample(brush.identity(), LabLibrary.sampler(brush));
        card.setContentDescription(shown.name + ": " + BrushVoice.about(brush.parts));
        content.addView(card, new LinearLayout.LayoutParams(-1, app.dp(fitted.chooserCard())));
        LabButton cut = new LabButton(app, 0, StampNib.CUT, false).menu();
        cut.setOnClickListener(v -> { closePanel(); app.cutStamp(slot); });
        content.addView(cut, new LinearLayout.LayoutParams(-1, app.dp(ToolsLayout.PAGE_BUTTON)));
        for (DrawLayout.StampRow row : DrawLayout.stampRows(brush)) stampRow(content, slot, row);
    }
    /** A dial's label, then a chip for each preset in the dial's words ({@link DrawLayout#stampRows}), the nearest solid; a tap sets it. */
    private void stampRow(LinearLayout content, int slot, DrawLayout.StampRow stamp) {
        TextView label = LabUi.text(app, stamp.label, TypeStyle.MENU);
        label.setTypeface(Theme.face(700));
        // In from the panel's outline, as the card's name is.
        label.setPadding(app.dp(LabText.CAPTION_INSET), label.getPaddingTop(), app.dp(LabText.CAPTION_INSET), label.getPaddingBottom());
        content.addView(label, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row = LabUi.row(app);
        for (int i = 0; i < stamp.values.length; i++) {
            double value = stamp.values[i];
            LabButton chip = new LabButton(app, 0, stamp.words[i], i == stamp.marked).menu();
            chip.setOnClickListener(v -> setStamp(content, slot, stamp.dial, value));
            row.addView(chip, new LinearLayout.LayoutParams(0, app.dp(ToolsLayout.PAGE_BUTTON), 1));
        }
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }
    /** Slot {@code slot}'s brush with {@code dial} at {@code value}: its tile drawn again, the menu drawn again in place (e-ink). */
    private void setStamp(LinearLayout content, int slot, PartDial dial, double value) {
        if (app.busy() || app.toolbarFixed()) return;
        if (!app.inkWash.slots.use(slot, DrawLayout.stamped(app.inkWash.slots.brush(slot), dial, value))) return;
        app.recovery();
        showBrush(slot);
        app.selectionFeedback.update(content, () -> fillStamp(content, slot));
    }

    // Opacity

    /**
     * The shade strip's long press ({@link DrawLayout#OPACITY}): for opaque paint, the tool's Opacity beside the strip, as
     * a strip of its own (mirrored with the column on the right), thin at the top to opaque at the foot, a pick setting
     * the brush's own Opacity dial, an Undo step as Fling's; for any other brush a note; or closed, if open.
     */
    private void opacityMenu() {
        View owner = swatches.strip();
        boolean open = ownsPanel(owner);
        closePanel();
        if (open || app.busy()) return;
        int tool = app.inkWash.selected;
        Brush brush = app.inkWash.brush(tool);
        if (!DrawLayout.opacity(brush)) { app.note(DrawLayout.OPACITY_ONLY); return; }
        if (app.toolbarFixed()) return;
        app.pad.finishStroke();
        LinearLayout content = LabUi.column(app);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView label = LabUi.text(app, DrawLayout.OPACITY, TypeStyle.MENU);
        label.setTypeface(Theme.face(700));
        label.setSingleLine(true);
        label.setPadding(app.dp(LabText.CAPTION_INSET), label.getPaddingTop(), app.dp(LabText.CAPTION_INSET), label.getPaddingBottom());
        content.addView(label, new LinearLayout.LayoutParams(-2, -2));
        double now = brush.parts.part(BrushPart.MEDIUM).get(PartDial.COVER);
        GreyStrip[] strip = new GreyStrip[1];
        strip[0] = new GreyStrip(app, app.selectionFeedback, value -> {
            if (app.busy() || !app.inkWash.slots.use(tool, DrawLayout.opacity(app.inkWash.brush(tool), value))) return;
            strip[0].show(value);
            strip[0].setContentDescription(DrawLayout.opacitySaid(value));
            strip[0].announceForAccessibility(DrawLayout.opacitySaid(value));
            app.recovery();
            showBrush(tool);
        }, null, null);
        strip[0].mirrored = right;
        strip[0].show(now);
        strip[0].setContentDescription(DrawLayout.opacitySaid(now));
        // A cell wide, as the strip was before the picker box, which is two.
        int cell = owner.getWidth() / 2;
        content.addView(strip[0], new LinearLayout.LayoutParams(cell, owner.getHeight()));
        label.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        openPanel(owner, content, Math.max(cell, label.getMeasuredWidth()) + 2 * app.dp(Theme.FINE));
    }

    // The label

    /**
     * Shows {@code text}, the words alone, no box round them and nothing behind them, beside the column, level with {@code beside}, for {@code ms}, or until
     * hidden if 0. It takes no touches, so the page beneath stays live; the pen going down hides it.
     */
    void say(String text, long ms, View beside) {
        if (label == null || labelContent != labelText) {
            labelText = Theme.style(new TextView(app), TypeStyle.MENU);
            // The words alone, no outlined box and nothing behind them (the owner, 2026-10-24: "for brush name tooltip, don't
            // use a box with a stroke, just the text", then the label "fully transparent"; on paper until then).
            labelText.setTypeface(Theme.face(700)); labelText.setBackground(null);
            // A long brush name wraps to two lines (the owner, 2026-10-22), cut with … past them.
            labelText.setMaxLines(2); labelText.setEllipsize(android.text.TextUtils.TruncateAt.END); labelText.setMaxWidth(app.dp(DrawLayout.LABEL_MOST));
            labelText.setPadding(app.dp(14), app.dp(8), app.dp(14), app.dp(8));
        }
        labelText.setText(text);
        showLabel(labelText, false, ms, beside);
    }
    /**
     * A button reading {@code text} in the label's place ({@link #say}), level with {@code beside}, for {@code ms}: a tap
     * hides it and runs {@code tap}. The pen going down hides it, as it does the label.
     */
    void offer(String text, long ms, View beside, Runnable tap) {
        showLabel(lightRow(new String[]{text}, -1, 1, false, index -> { hideLabel(); tap.run(); }), true, ms, beside);
    }
    /**
     * Light words beside the column (the owner, 2026-10-22, of the eraser's: "make it smaller and lighter so it doesn't
     * leave ghosts on the screen after"), in Inkwash's one menu ({@link Theme#menu}, {@link Theme#menuRow}): paper, a fine
     * ink edge, no grey, the words {@link TypeStyle#MENU}, {@link DrawLayout#LIGHT_ROW} dp tall; a column of rows when
     * {@code column} (the eraser's), else a row; {@code chosen} (-1: none) bold with the bar's ink bar at its right; a
     * gap before word {@code gapBefore} (its length: none). A tap runs {@code pick} with the word's index.
     */
    private View lightRow(String[] texts, int chosen, int gapBefore, boolean column, java.util.function.IntConsumer pick) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(app);
        row.setOrientation(column ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Theme.menu(app));
        int fine = app.dp(Theme.FINE), pad = app.dp(DrawLayout.LIGHT_PAD);
        row.setPadding(fine, fine, fine, fine);
        for (int i = 0; i < texts.length; i++) {
            int index = i;
            TextView word = Theme.menuRow(new TextView(app), i == chosen);
            word.setText(texts[i]); word.setContentDescription(i == chosen ? texts[i] + ", in use" : texts[i]);
            word.setSingleLine(true); word.setGravity(column ? Gravity.CENTER_VERTICAL | Gravity.START : Gravity.CENTER);
            // A column's words keep room at their right for the chosen one's bar.
            word.setPadding(pad, 0, column ? pad + app.dp(Theme.HEAVY + Theme.BASE) : pad, 0);
            word.setOnClickListener(v -> pick.accept(index));
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(column ? -1 : -2, app.dp(DrawLayout.LIGHT_ROW));
            if (i == gapBefore && i > 0) { if (column) place.topMargin = pad; else place.leftMargin = pad; }
            row.addView(word, place);
        }
        return row;
    }
    /** Shows {@code content} beside the column, level with {@code beside}, taking touches or not, for {@code ms} (0: until hidden). */
    private void showLabel(View content, boolean touchable, long ms, View beside) {
        int[] size = labelSize(content);
        int width = size[0], height = size[1];
        RectF bar = Popups.bounds(app.root, zenAnchor != null ? zenAnchor : view), level = Popups.bounds(app.root, beside);
        float left = right ? bar.left - app.dp(8) - width : bar.right + app.dp(8);
        showLabel(content, touchable, ms, Popups.rect(Popups.clamp(left, 0, app.root.getWidth() - width),
                Popups.clamp(level.centerY() - height / 2f, 0, app.root.getHeight() - height), width, height));
    }
    /** {@code content}'s width and height as the label shows it, px: as it wants, at most the screen's width. */
    private int[] labelSize(View content) {
        content.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return new int[]{Math.min(content.getMeasuredWidth(), app.root.getWidth()), content.getMeasuredHeight()};
    }
    /** As above, over {@code bounds} (the root's app coordinates). */
    private void showLabel(View content, boolean touchable, long ms, RectF bounds) {
        view.removeCallbacks(hideLabel);
        if (label != null && labelContent != content) { PopupWindow was = label; label = null; was.dismiss(); }
        if (label == null) {
            // The label's words with nothing behind them, the page showing through; the light rows on paper.
            PopupWindow popup = Popups.create(content, app.appTurn(), false, content == labelText ? android.graphics.Color.TRANSPARENT : Theme.PAPER);
            popup.setTouchable(touchable);
            // Drawn by the firmware in partial grey too: one pass, no flash, when it opens (partialGrey).
            partialGrey(content);
            popup.setOnDismissListener(() -> {
                if (label == popup) label = null;
                RectF area = new RectF(labelArea); labelArea.setEmpty();
                // What it covered alone is cleaned: a slot's name or a shade's offer closing refreshed the whole page and
                // drew the whole column again through Android (2026-10-24, the slot taps' blink).
                app.pad.cleanArea(area);
            });
            label = popup; labelContent = content;
        }
        labelArea.union(onPanel(bounds));
        Popups.show(label, app.root, bounds);
        if (ms > 0) view.postDelayed(hideLabel, ms);
    }
    void hideLabel() {
        view.removeCallbacks(hideLabel);
        if (label != null) label.dismiss();
    }
    boolean labelShown() { return label != null; }


    /** The page changed (another page, Undo, a stroke): its preview in the page bar is drawn again once it has been still a moment. */
    void pageChanged() {
        showLayer();
        turner.pageChanged();
    }
    /** The owner's layer icons, the layer in use filled: 3 the top, 2 the middle, 1 (and 0, under the three) the bottom. */
    static int layerIcon(int layer) { return layer >= 3 ? R.drawable.layer_top : layer == 2 ? R.drawable.layer_middle : R.drawable.layer_bottom; }
    /** The layer icon's half width for its half height {@code half}, dp: the owner's drawing a little wider than tall. */
    static int layerHalfWidth(Context context, int half) {
        Drawable stack = context.getDrawable(R.drawable.layer_middle);
        return Math.round(half * (float) stack.getIntrinsicWidth() / stack.getIntrinsicHeight());
    }
    /** The layer the page drawn on is on, for the button: 1 to 3 ({@link InkWashLayers#layer}, a four-pair page's bottom pair shown as layer 1). */
    int layerInUse() { return app.pad == null || app.pad.document == null ? 2 : Math.max(1, InkWashLayers.layer(app.pad.document)); }
    /** Whether the pair drawn on is hidden (an old page's; the button hides none). */
    boolean layerHidden() { return app.pad != null && app.pad.document != null && !InkWashLayers.visible(app.pad.document, InkWashLayers.current(app.pad.document)); }
    /** The layer button follows the page (another page, Undo and Redo, a stroke that moved the selection, a pick): its icon put on the panel, its words. */
    void showLayer() {
        if (app.pad == null || app.pad.document == null) return;
        int layer = layerInUse();
        boolean hidden = layerHidden();
        if (layer == layerShown && hidden == layerHidden) return;
        layerShown = layer; layerHidden = hidden;
        layers.setContentDescription(DrawLayout.layerSaid(layer, hidden));
        Drawable icon = app.getDrawable(layerIcon(layer));
        if (layers.isShown() && layers.getWidth() > 0) app.selectionFeedback.update(layers, () -> layers.faces(icon, null));
        else layers.iconOnly(icon, null);
    }
    /** Screen-reader actions on a layer button, Layer 1 to Layer 3, each picking its layer as a tap on its third does. */
    static void layerActions(View button, java.util.function.IntConsumer pick) {
        button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                for (int layer = 1; layer <= InkWashLayers.LAYERS; layer++) info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(LAYER_ACTIONS[layer - 1], DrawLayout.layerNote(layer)));
                info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK, DrawLayout.LAYER_VISIBILITY));
            }
            @Override public boolean performAccessibilityAction(View host, int action, android.os.Bundle arguments) {
                for (int layer = 1; layer <= InkWashLayers.LAYERS; layer++) if (action == LAYER_ACTIONS[layer - 1]) { pick.accept(layer); return true; }
                return super.performAccessibilityAction(host, action, arguments);
            }
        });
    }
    private static final int[] LAYER_ACTIONS = {View.generateViewId(), View.generateViewId(), View.generateViewId()};

    // Panels

    /**
     * The countdown says {@code text} (null: gone, Timed Pages off, the sand timer in its place when {@code sand}; a page's timed sections' "Saw" over "1:42", {@link TimedSections#countdown}): only its own row is drawn again, in the fast mode, as the
     * page bar's blink is; false, changing nothing, when it would change but must {@code wait} (the pen is down).
     */
    boolean countdown(String text, boolean sand, boolean wait) {
        int shown = text == null ? View.GONE : View.VISIBLE;
        boolean icon = text == null && sand;
        if (icon == sandShown && shown == clock.getVisibility() && (text == null || text.contentEquals(clock.getText()))) return true;
        if (wait) return false;
        sandShown = icon;
        clock.setContentDescription(text == null ? null : NotebookRules.countdownSaid(text));
        if (shown != clock.getVisibility() || !clock.isShown() || clock.getWidth() == 0) { clock.setText(text); clock.setVisibility(shown); }
        else app.selectionFeedback.update(clock, () -> clock.setText(text));
        return true;
    }

    /** Page {@code index} of {@code count} is shown: the page turner says so. */
    void pagesChanged(int index, int count, boolean turned) { turner.show(index, count, turned); }

    boolean panelOpen() { return panel != null; }
    /** Closes the smudge chooser or the stamp menu, whichever is open. */
    void closePanel() { if (panel != null) panel.dismiss(); }
    /** Whether {@code owner}'s panel is the one open. */
    boolean ownsPanel(View owner) { return panel != null && panelOwner == owner; }
    /**
     * The pen touching the window outside the open panel closes it, except on the control that owns it, which
     * keeps it open (its own tap closes it). {@code index} is the pen's pointer: a resting palm may be the event's first.
     */
    void penDown(MotionEvent event, int index) {
        // Show Kinds stays while its eyes are tapped, until the person has set each kind (the owner, 2026-10-28).
        // So does the Lasso's bar, so a tap on it lands (lassoBar).
        if (!(label != null && (labelContent == kindsContent || labelContent == lassoContent) && !Ui.outside(labelContent, event, index))) hideLabel();
        if (panel != null && panelOwner != null && Ui.outside(panelOwner, event, index)) closePanel();
    }

    /** The smudge chooser beside its tile, {@code width} px wide. */
    void openPanel(View owner, LinearLayout content, int width) {
        panel = beside(owner, content, width, popup -> {
            if (panel == popup) { panel = null; app.selectionFeedback.update(owner, () -> panelOwner = null); }
        });
        app.selectionFeedback.update(owner, () -> panelOwner = owner);
    }
    /**
     * A panel holding {@code content} beside {@code owner}, toward the page, {@code width} px wide (the smudge chooser's;
     * 0: the owner's) and level with it (moved up when it would run off the foot), its outline running on around the owner's top, bottom and far side
     * like a folder's tab, with no line between them. It is not focusable, so the pen or a finger can start drawing at
     * once (that closes it too) and the column's other controls stay live. {@code closed} runs as it closes.
     */
    PopupWindow beside(View owner, LinearLayout content, int width, Consumer<PopupWindow> closed) {
        int stroke = app.dp(Theme.FINE);
        // Inside the outline all round: where the owner joins, a paper strip leaves the join open.
        content.setPadding(stroke, stroke, stroke, stroke);
        PopupWindow popup = Popups.create(content, app.appTurn(), false, Theme.PAPER);
        RectF button = Popups.bounds(app.root, owner);
        if (width <= 0) width = Math.round(button.width());
        int height = Math.min(Popups.measuredHeight(content, width, 0), app.root.getHeight());
        float left = right ? button.left - width : button.right;
        RectF bounds = Popups.rect(Popups.clamp(left, 0, app.root.getWidth() - width),
                Popups.clamp(button.top, 0, app.root.getHeight() - height), width, height);
        content.setBackground(new TabPanel(stroke, right, button.top - bounds.top, button.bottom - bounds.top));
        RectF area = onPanel(bounds);
        // One grey pass as it opens, and only what it covered cleaned as it closes, as the label: the page's whole
        // refresh and the column, page bar and brush line drawn again after every close until 2026-10-25.
        partialGrey(content);
        popup.setOnDismissListener(() -> {
            closed.accept(popup);
            app.pad.cleanArea(area);
        });
        Popups.show(popup, app.root, bounds);
        return popup;
    }
    /** {@code bounds}, in the root's app coordinates, on the panel: what a popup there covers, for {@link DrawingPad#cleanArea}. */
    private RectF onPanel(RectF bounds) {
        RectF area = new RectF(bounds); PanelCoordinates.fromView(app.root).mapRect(area);
        return area;
    }

    /**
     * A menu's panel ({@link Theme#menu}: paper, a fine ink edge, no grey), open along the side toward its owner (its left, or its right when {@code
     * right}: the column on the right) from {@code gapTop} to {@code gapBottom}, where the owner's tab joins it.
     */
    private static final class TabPanel extends Drawable {
        static final Paint INK = new Paint();
        static { INK.setColor(Theme.INK); }
        private final int stroke;
        private final boolean right;
        private final float gapTop, gapBottom;
        TabPanel(int stroke, boolean right, float gapTop, float gapBottom) { this.stroke = stroke; this.right = right; this.gapTop = gapTop; this.gapBottom = gapBottom; }
        @Override public void draw(Canvas canvas) {
            int w = getBounds().width(), h = getBounds().height();
            canvas.drawColor(Theme.PAPER);
            canvas.drawRect(0, 0, w, stroke, INK);
            canvas.drawRect(0, h - stroke, w, h, INK);
            int far = right ? 0 : w - stroke, near = right ? w - stroke : 0;
            canvas.drawRect(far, 0, far + stroke, h, INK);
            // The near side reaches into the owner's top and bottom lines, so the corners join.
            canvas.drawRect(near, 0, near + stroke, Math.max(0, Math.round(gapTop) + stroke), INK);
            canvas.drawRect(near, Math.min(h, Math.round(gapBottom) - stroke), near + stroke, h, INK);
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
