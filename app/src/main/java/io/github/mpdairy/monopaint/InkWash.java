package io.github.mpdairy.monopaint;

import android.content.SharedPreferences;
import android.util.Log;

/**
 * Which tool is selected (one of the nine slots, Fill in, the eraser or the smudge tool) and each one's size and shade, kept
 * between launches ({@link ToolSwatches}), what one finger on the page does ({@link FingerTool}), and the strokes they
 * draw. Every stroke on a page starts in {@link
 * #stroke(ToneDocument, Brush, ToolSwatches.Look, WatercolorSession)}: the drawing screen's and Brush Lab's test page.
 */
final class InkWash {
    /** The eraser's place after the nine slots: in the bar, and as {@link #selected}. */
    static final int ERASER = ToolSlots.ERASER;
    /** The smudge tool's place after the eraser. */
    static final int SMUDGE = ToolSlots.SMUDGE;
    /** The stamp tool's place after the smudge tool: a slot-like brush of its own ({@link ToolSlots#stamp}); Fill in's after it ({@link ToolSlots#FILL}). */
    static final int STAMP = ToolSlots.STAMP;
    private final SharedPreferences preferences;
    final WatercolorSession session = new WatercolorSession();
    /** What the nine slots hold; the dice is any 32 bits. */
    final ToolSlots slots;
    /** The selected tool: a slot, {@link #ERASER}, {@link #SMUDGE} or {@link #STAMP}. */
    int selected;
    /** The kinds of mark the eraser takes ({@link EraseKind}); 0: everything. Not kept between launches. */
    int eraseKinds;
    /** The kinds Show Kinds hides on the page drawn on ({@link EraseKind#hidden}), read at each eraser stroke; the drawing screen sets it. */
    java.util.function.IntSupplier hiddenKinds = () -> 0;
    /** The kinds the eraser takes now ({@link EraseKind#takes}): never a hidden one. */
    int erasing() { return EraseKind.takes(eraseKinds, hiddenKinds.getAsInt()); }
    /** The open drawing's strokes by brush, for its name ({@link DrawingNames#described}); the drawing screen clears it. */
    final DrawingNames.Tally drawn = new DrawingNames.Tally();

    InkWash(SharedPreferences preferences) {
        this.preferences = preferences;
        // Before the selection is read: a first launch with slots moves the old tools' sizes, shades and selection to theirs.
        slots = new ToolSlots(store(preferences), new java.util.Random());
        importBrushes();
        // Gradient's Cycle steps through the toolbar's greys as they are.
        session.swatches = slots.swatches;
        session.log = message -> Log.d(ProbeActivity.TAG, message);
        session.pace = new SettlePacer();
        session.worker = new SettleWorker();
        // A Wet brush's ink survives a restart (the owner, 2026-10-22), per tool, in the preferences beside its swatches.
        session.held.saveWetInkIn(preferenceStore(preferences));
        selected = parse(preferences.getString(ToolSlots.SELECTED, null));
        // The toolbar is also written to a text file (a backup only): at launch, then whenever any preference changes.
        backUpToolbar();
        preferences.registerOnSharedPreferenceChangeListener(backupListener);
    }
    /** Held here, as preferences keep their listeners weakly. */
    private final SharedPreferences.OnSharedPreferenceChangeListener backupListener = (p, key) -> backUpToolbar();
    /**
     * The backup's text names every slot's brush by its share code: about 36 ms on the Manta, run on every tool pick's
     * tap until 2026-10-25. Written once things are quiet instead (a backup only).
     */
    private void backUpToolbar() { later.removeCallbacks(backUpNow); later.postDelayed(backUpNow, BACKUP_MS); }
    private static final long BACKUP_MS = 2000;
    private final android.os.Handler later = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable backUpNow = this::backUpNow;
    private void backUpNow() {
        java.io.File shared = backup();
        if (shared != null) ToolBackup.save(new java.io.File(shared.getParentFile(), ToolBackup.NAME), slots, selected);
    }
    /** The slots' and the library's store: the app's preferences, shared by the drawing screen and Brush Lab; Stamp nibs are kept there too ({@link StampNib}). */
    static BrushLibrary.Store store(SharedPreferences preferences) {
        BrushLibrary.Store store = BrushBackup.backed(preferenceStore(preferences), backup());
        StampNib.storeIn(store);
        return store;
    }
    /** The app's preferences as a store, nothing backed up beside the drawings. */
    private static BrushLibrary.Store preferenceStore(SharedPreferences preferences) {
        return new BrushLibrary.Store() {
            @Override public String get(String key) { return preferences.getString(key, null); }
            @Override public void put(String key, String value) {
                if (value == null) preferences.edit().remove(key).apply(); else preferences.edit().putString(key, value).apply();
            }
        };
    }
    /** Adds the brushes in the import files beside the drawings (at launch, and when the drawing screen comes back). */
    void importBrushes() {
        java.io.File shared = backup();
        if (shared != null) BrushImport.runAll(slots.library, shared.getParentFile(), note -> importNote = note);
    }
    /** What the last import said, for the drawing screen to show once: a code from a newer version ({@link BrushImport#newerNote}); else null. */
    String importNote;
    /** Where saved brushes are kept for good ({@link BrushBackup}): beside the drawings, once the app may write there; else null. */
    private static java.io.File backup() {
        boolean shared = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && android.os.Environment.isExternalStorageManager();
        return shared ? new java.io.File(DrawingStorage.shared(), BrushBackup.NAME) : null;
    }
    /** What a tool's size, shade and selection are kept under; the eraser's are as they were before slots. */
    static String key(int tool) { return ToolSlots.toolKey(tool); }
    private static int parse(String saved) {
        for (int tool = 0; tool < ToolSlots.TOOLS; tool++) if (key(tool).equals(saved)) return tool;
        return 0;
    }
    /** Tool {@code tool}'s brush; null for the eraser. */
    Brush brush(int tool) { return tool == ERASER ? null : tool == SMUDGE ? slots.smudge() : slots.brush(tool); }
    /** What tool {@code tool} draws at: its own size and grey ({@link ToolSwatches#look}). */
    ToolSwatches.Look look(int tool) { return slots.swatches.look(key(tool), brush(tool)); }
    /** The size swatch nearest the size tool {@code tool} draws at. */
    InkWashSizes.Preset preset(int tool) { return look(tool).preset; }
    /** The kind of mark tool {@code tool} lays ({@link ResolvedBrush#medium}, as its layer plane takes it; {@link ToneDocument#KIND_PEN} and the like), 0 for the eraser. */
    int kindOf(int tool) {
        Brush brush = brush(tool);
        if (brush == null) return 0;
        ToolSwatches.Look look = look(tool);
        return ResolvedBrush.of(brush.parts, look.px, look.darkness).medium & ToneDocument.KIND_ALL;
    }
    void select(int tool) { selected = tool; preferences.edit().putString(ToolSlots.SELECTED, key(tool)).apply(); backUpToolbar(); }
    /** What one finger on the page does, as last chosen (the main menu's Finger row). */
    FingerTool.Choice finger() { return FingerTool.Choice.saved(preferences.getString(FingerTool.KEY, null)); }
    void finger(FingerTool.Choice choice) { preferences.edit().putString(FingerTool.KEY, choice.key).apply(); }

    /** Starts the selected tool's stroke on its half of the selected pair, or the eraser's for the pen's eraser end or side button. */
    DrawingStroke stroke(ToneDocument document, boolean eraserEnd) { return stroke(document, eraserEnd ? ERASER : selected); }
    /** Starts tool {@code chosen}'s stroke (a slot, the eraser or the smudge tool) at its size and grey, on its half of the selected pair. */
    DrawingStroke stroke(ToneDocument document, int chosen) { return stroke(document, chosen, false); }
    /** As {@link #stroke(ToneDocument, int)}, drawn by one finger ({@code finger}: the finger tool's, whose brush keeps paint of its own). */
    DrawingStroke stroke(ToneDocument document, int chosen, boolean finger) {
        // A dirty brush (Keeps Paint) starts with what this tool's (or the finger's) last stroke kept.
        session.holding(holder(chosen, finger));
        before(document, brush(chosen));
        Brush brush = brush(chosen);
        ToolSwatches.Look look = shifted(chosen, brush, holder(chosen, finger));
        // Record and Replay: the brush (none: the eraser, and the kinds it takes), size and grey this stroke was made with.
        int eraseKinds = erasing();
        recorder = StrokeLog.Recorder.of(brush, look, eraseKinds, holder(chosen, finger), chosen == SMUDGE, finger);
        // Sharp dry brushes: the pen's stroke of a brush kept sharp is fed as the log keeps it (DrawingPad.feed).
        if (brush != null && chosen != SMUDGE && !finger && SharpStrokes.takes(brush.parts, look.px, look.darkness)) SharpStrokes.keep(recorder, brush.parts, look.px, look.darkness, session);
        // Mirrors and Filled Shape as the browser's too (StrokeStart).
        return StrokeStart.start(document, brush, look, session, eraseKinds);
    }
    /**
     * Scribulor: a shape of the selected slot's ({@link DrawingPad#shapesOn}): what comes before its stroke, then its layer
     * the one the slot's strokes go to ({@link StrokeStart#shape}); the look it lays at, its size the outline's width and
     * its grey the shape's. Record and Replay: {@link #shapeCode} the slot's brush.
     */
    ToolSwatches.Look shape(ToneDocument document) {
        Brush brush = brush(selected);
        slots.pageChanged();
        drawn.add(brush);
        StrokeStart.shape(document, brush.parts, session);
        recorder = null;
        shapeCode = ModularCode.encode(brush.parts);
        return look(selected);
    }
    /** The brush code of the slot whose shape {@link #shape} just began ({@link StrokeLog.Entry#shape}). */
    String shapeCode;
    /**
     * Make Ink ({@link TextBoxes#ink}): {@code letters} laid by slot {@code slot}'s brush at its size and grey, as a fill
     * lays it ({@link FillTool#print}), after what comes before any stroke; the caller finishes it. Null for an empty slot.
     */
    ModularStroke ink(ToneDocument document, int slot, FillRegion.Room letters) {
        Brush brush = brush(slot);
        if (brush == null) return null;
        before(document, brush);
        recorder = null;
        ToolSwatches.Look look = look(slot);
        return FillTool.print(document, brush.parts, look.px, look.darkness, session, letters);
    }
    /** Record and Replay ({@link StrokeLog}): the recorder for the stroke {@link #stroke(ToneDocument, int, boolean)} or {@link #fingerEraser} just made, or null. */
    StrokeLog.Recorder recorder;
    /**
     * Grey Shift ({@link SwatchPicker}): tool {@code tool}'s look at the grey {@code brush} picks at this touch, in
     * {@code holder}'s turn; as it was for one that doesn't shift. The grey it picks is the stroke's alone: the
     * tool's own grey stays as it is.
     */
    private ToolSwatches.Look shifted(int tool, Brush brush, String holder) {
        return SwatchPicker.look(brush, look(tool), slots.swatches, session.held, holder);
    }
    /** What comes before any stroke or fill of {@code brush} (null: the eraser's) on the page. */
    private void before(ToneDocument document, Brush brush) {
        // A Drying wash still settling is laid where it is, and floating paint prints, before anything else changes the
        // page (a Float stroke on the bath's layer joins it; so does Clear Water, on the water).
        session.settleBefore(document, brush == null ? null : session.onWater(document, brush.parts));
        // Undo takes back the stroke now, not a brush rolled before it.
        slots.pageChanged();
        // Drawing on a hidden layer shows it rather than refusing (the Manta refuses a wholly hidden one first, PaintActivity.allowStroke).
        InkWashLayers.showSelected(document);
        drawn.add(brush);
    }
    /** {@code brush}'s stroke at {@code look}'s px and darkness, unmirrored ({@link StrokeStart#stroke}; null: the eraser). */
    static DrawingStroke stroke(ToneDocument document, Brush brush, ToolSwatches.Look look, WatercolorSession session) { return StrokeStart.stroke(document, brush, look, session, 0); }
    /** Whose brush a stroke of tool {@code tool} is, its paint kept apart ({@link HeldPaint}): the tool's, or the finger's with it. */
    static String holder(int tool, boolean finger) { return ToolSlots.holder(tool, finger); }
    /**
     * Whether tool {@code tool}'s brush (a slot's or the smudge tool's) keeps paint (Keeps Paint) and holds some now: its
     * own, or the finger's when one finger draws with it. The bar shows it in that paint's grey ({@link #heldGrey}), and
     * the tool's long press offers Clean Brush.
     */
    boolean dirty(int tool) { return ToolSlots.dirty(session.held, brush(tool), tool, finger().tool()); }
    /** The grey tool {@code tool}'s held paint shows in ({@link DrawLayout#heldGrey}), its pen's, else its finger's; -1 when it holds none. */
    int heldGrey(int tool) {
        if (!dirty(tool)) return -1;
        String pen = holder(tool, false);
        return DrawLayout.heldGrey(session.held.of(session.held.dirty(pen) ? pen : holder(tool, true)).darkness());
    }
    /**
     * Paint Load (the oil paint spec): a rub of {@code travel} screen px on a grey face loads the selected tool's brush
     * with paint ({@link LoadedPaint#rub}); whether it did (only a brush with Paint Load does).
     */
    boolean rub(double travel) { dip(); return LoadedPaint.rub(session.held, holder(selected, false), brush(selected), look(selected), travel); }
    /**
     * A tap or rub on a grey (the wet brush spec; MonoPaint: tap a colour to dip the wet brush pen): the selected tool's
     * Wet ink brush is dipped, its next stroke starting full of that grey ({@link HeldPaint#dip}); any other brush holds
     * no such ink, so nothing changes for it.
     */
    void dip() { session.held.dip(holder(selected, false)); }
    /** The share of a full load tool {@code tool}'s next stroke starts with ({@link LoadedPaint#shown}), or NaN when its brush never runs out: its tray row's gauge. */
    double paintLoad(int tool) { return LoadedPaint.shown(session.held, holder(tool, false), brush(tool), look(tool)); }
    /** Clean Brush: tool {@code tool}'s brush, and the finger's with it, start their next strokes clean. Whether either held paint. */
    boolean clean(int tool) { return ToolSlots.clean(session.held, tool); }
    /** The broadest a tile's loop draws, px: a fifth of its reach, so the loop still reads; a broader slot says its size in mm. */
    static float tileCap(int width, int height) { return WildSample.reach(width, height) * .2f; }
    /** A finger erases at one size, {@code diameter} page pixels; cancelled (a tap, a palm, the pen), the page is as it was. */
    DrawingStroke fingerEraser(ToneDocument document, float diameter) {
        // As the pen's eraser: what settles is laid first, wet paint stays wet.
        session.settleBefore(document, null);
        slots.pageChanged();
        InkWashLayers.showSelected(document);
        int eraseKinds = erasing();
        recorder = StrokeLog.Recorder.fingerEraser(diameter, eraseKinds);
        return StrokeStart.mirrored(document, new EraseBothStroke(document, diameter, eraseKinds), () -> { session.settleNow(document); return new EraseBothStroke(document, diameter, eraseKinds); }, () -> 2 * diameter, false);
    }
}
