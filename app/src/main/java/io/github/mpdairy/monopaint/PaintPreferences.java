package io.github.mpdairy.monopaint;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/**
 * Typed access to the app's saved settings. Every read goes to {@link SharedPreferences},
 * so values written elsewhere are always current. Key names are part of the installed
 * app's saved state; keep them unchanged.
 */
final class PaintPreferences {
    static final String FILE = "painting";
    final SharedPreferences store;
    PaintPreferences(SharedPreferences store) { this.store = store; }

    private boolean flag(String key, boolean fallback) { return store.getBoolean(key, fallback); }
    private void setFlag(String key, boolean value) { store.edit().putBoolean(key, value).apply(); }

    /** Left-handed layout: the toolbar sits on the right. */
    boolean toolboxRight() { return flag("toolbox_right", false); }
    void setToolboxRight(boolean value) { setFlag("toolbox_right", value); }
    boolean largeSettingsText() { return flag("large_settings_text", false); }
    void setLargeSettingsText(boolean value) { setFlag("large_settings_text", value); }
    /** Preview a smaller tablet's layout (on a Manta, the Nomad's). The key predates other tablets. */
    boolean simulateSmallerPanel() { return flag("nomad_mode", false); }
    void setSimulateSmallerPanel(boolean value) { setFlag("nomad_mode", value); }
    /**
     * Tools First ({@link DrawLayout#TOOLS_FIRST}; the owner, 2026-10-28): a slot's long press opens the Tools page straight
     * away, not the tool picker. Off at first; switched in the picker and on the Tools page.
     */
    boolean toolsFirst() { return flag(TOOLS_FIRST, false); }
    void setToolsFirst(boolean value) { setFlag(TOOLS_FIRST, value); }
    static final String TOOLS_FIRST = "tools_first";
    /** Wet paint runs toward whichever page edge the tablet tilts down. */
    boolean wetGravity() { return flag("wet_gravity", true); }
    void setWetGravity(boolean value) { setFlag("wet_gravity", value); }
    /**
     * Draw once (the wet paint spec's experiment, the owner's "one write per pixel", 2026-10-22): Pastels and Dry brush
     * drawn with no preview, each pixel written once ({@link QuickStroke#drawOnce}). Off unless the drawing screen is
     * launched with the extra {@link #DRAW_ONCE} (adb: {@code am start -S -n ing.ell.mybaddrawings/io.github.mpdairy.monopaint.PaintActivity --ez drawOnce true}).
     */
    boolean drawOnce() { return flag(DRAW_ONCE, false); }
    void setDrawOnce(boolean value) { setFlag(DRAW_ONCE, value); }
    static final String DRAW_ONCE = "drawOnce";
    /** Dry brush strokes anti-alias their edges, for cleaner exports. */
    boolean smoothEdges() { return flag("smooth_edges", false); }
    void setSmoothEdges(boolean value) { setFlag("smooth_edges", value); }
    /**
     * System Settings' Automatic Refresh (the owner, 2026-11-04: "the goal should be to prevent having to flash more than
     * relying on it"): whether the screen flashes on its own ({@link SystemSettings#flashes}). On at first, as before.
     */
    boolean autoRefresh() { return flag("auto_refresh", false); } // off at first (the owner, 2026-11-05: "it's seeming ok")
    void setAutoRefresh(boolean value) { setFlag("auto_refresh", value); }
    /** The Fridge ({@link Fridge}): its first time's words agreed to (System Settings' The Fridge, On); Off asks again. */
    /** Zen Mode as last left: a new install starts without it, in the side bar the Start Here guide shows (the owner, 2026-11-09). */
    boolean zen() { return flag("zen_on", false); }
    void setZen(boolean value) { setFlag("zen_on", value); }
    /** System Settings' Zen Fan: whether the fan stays open while the pen draws on the page (closes at first, 2026-11-10). */
    boolean zenFanStays() { return flag("zen_fan_stays", false); }
    void setZenFanStays(boolean value) { setFlag("zen_fan_stays", value); }
    boolean fridgeAgreed() { return flag("fridge_agreed", false); }
    void setFridgeAgreed(boolean value) { setFlag("fridge_agreed", value); }
    /** Read Handwriting (System Settings): On only once its first-time words are agreed to. */
    boolean readHandwriting() { return flag("read_handwriting", false); }
    void setReadHandwriting(boolean value) { setFlag("read_handwriting", value); }
    /** The cards sent from this tablet ({@link Fridge#encode}), so each can be taken down. */
    String fridgeCards() { return store.getString("fridge_cards", ""); }
    void setFridgeCards(String value) { store.edit().putString("fridge_cards", value).apply(); }
    /** My Fridge's slug and key ({@link Fridge#own}), or null before it is made. The key stays here alone: never shown or logged. */
    String[] fridgeOwn() {
        String slug = store.getString("fridge_slug", null), key = store.getString("fridge_key", null);
        return slug == null || key == null ? null : new String[]{slug, key};
    }
    void setFridgeOwn(String[] own) { store.edit().putString("fridge_slug", own[0]).putString("fridge_key", own[1]).apply(); }
    /** System Settings' Two-Finger Tap and Three-Finger Tap ({@link FingerTool.Tap}): Undo and Redo at first. */
    FingerTool.Tap twoFingerTap() { return FingerTool.Tap.saved(store.getString(FingerTool.TWO_TAP, ""), FingerTool.TWO_FIRST); }
    FingerTool.Tap threeFingerTap() { return FingerTool.Tap.saved(store.getString(FingerTool.THREE_TAP, ""), FingerTool.THREE_FIRST); }
    void setFingerTap(boolean three, FingerTool.Tap tap) { store.edit().putString(three ? FingerTool.THREE_TAP : FingerTool.TWO_TAP, tap.key).apply(); }
    /** System Settings' Edge Hold ({@link EdgeHold.Choice}): the Trash at first. */
    EdgeHold.Choice edgeHold() { return EdgeHold.Choice.saved(store.getString(EdgeHold.KEY, "")); }
    void setEdgeHold(EdgeHold.Choice choice) { store.edit().putString(EdgeHold.KEY, choice.key).apply(); }
    /** Draw through Android instead of MonoPaint's fast e-ink path, for firmware that breaks it. */
    boolean androidDrawing() { return flag("android_drawing", false); }
    void setAndroidDrawing(boolean value) { setFlag("android_drawing", value); }
    /** The untested firmware build the user was last told about; empty before any. */
    /** Whether a stamp has ever been printed: until then a Stamp in use shows its hint (2026-11-02, {@link DrawLayout#stampHint}). */
    boolean stampPlaced() { return flag("stamp_placed", false); }
    void setStampPlaced(boolean value) { setFlag("stamp_placed", value); }
    /** The shared-storage folder the last imported picture came from; Document at first (Scribulor: where the Supernote keeps PDFs). */
    String importFolder() { return store.getString("import_folder", "Document"); }
    void setImportFolder(String folder) { store.edit().putString("import_folder", folder).apply(); }
    /** Whether the last imported picture went on a new layer; the current layer at first. */
    boolean importNewLayer() { return flag("import_new_layer", false); }
    void setImportNewLayer(boolean value) { setFlag("import_new_layer", value); }
    /** Zoom and pan locked, and the app's turn with them (2026-11-10, {@link RotationPrompt}): unlocked at first (the owner, 2026-11-07: "have zoom unlocked by default"; a new key, so a lock kept from before goes). */
    boolean navigationLocked() { return flag("zoom_locked", false); }
    void setNavigationLocked(boolean value) { setFlag("zoom_locked", value); }
    /** Whether the user has picked a shape, so Shapes shows that shape's icon. */
    boolean shapeChosen(boolean fallback) { return flag("shape_chosen", fallback); }
    /** Whether the toolbar's Brush pen selects the wet brush pen; the dry one at first. */
    boolean wetBrushPen() { return flag("wet_brush_pen", false); }
    void setWetBrushPen(boolean value) { setFlag("wet_brush_pen", value); }
    void setShapeChosen() { setFlag("shape_chosen", true); }

    /** Toolbar entries are tool names plus {@code LAYERS}, {@code ZOOM} and {@code PALETTE}. */
    boolean toolbarItemVisible(String key) { return flag("tool_visible_" + key, true); }
    void setToolbarItemVisible(String key, boolean visible) { setFlag("tool_visible_" + key, visible); }
    /** Saved toolbar order, possibly missing newer entries or containing removed ones. */
    String[] toolbarOrder() { return store.getString("toolbar_order", "").split(","); }
    void setToolbarOrder(List<String> keys) { store.edit().putString("toolbar_order", String.join(",", keys)).apply(); }

    static final int MAX_PALETTE_SHADES = 16;
    /** Saved palette shades; never empty unless the user deleted every shade. */
    ArrayList<Integer> paletteShades() {
        ArrayList<Integer> shades = new ArrayList<>();
        String saved = store.getString("palette_shades", "0,162,225,255");
        if (saved.isEmpty()) return shades;
        for (String item : saved.split(",")) {
            try {
                int value = Integer.parseInt(item);
                if (value >= 0 && value <= 255 && shades.size() < MAX_PALETTE_SHADES) shades.add(value);
            } catch (NumberFormatException ignored) { }
        }
        if (shades.isEmpty()) shades.add(0);
        return shades;
    }
    void setPaletteShades(List<Integer> shades) {
        StringBuilder value = new StringBuilder();
        for (int shade : shades) { if (value.length() > 0) value.append(','); value.append(shade); }
        store.edit().putString("palette_shades", value.toString()).apply();
    }

    /** Restores the saved color and canvas modes. */
    void loadPaint(PaintState state) {
        state.wetCanvas = flag("wet_canvas", false);
        state.transparentPaint = flag("transparent_paint", false);
        state.watercolor = flag("watercolor", false);
        state.eraseMode = flag("erase_mode", false);
        state.maskingFluid = flag("masking_fluid", false);
        state.wetness = Math.max(0, Math.min(100, store.getInt("canvas_wetness", 65)));
        // The slider alone wets the canvas: a dry one shows it at zero.
        if (state.wetness == 0 || !state.wetCanvas) { state.wetCanvas = false; state.wetness = 0; }
        state.gray = Math.max(0, Math.min(255, store.getInt("gray", 0)));
        state.brushInk.restore(store.getFloat("brush_ink", 0), 0);
        state.brushLoad.restore(store.getFloat("brush_load_ink", 0), store.getFloat("brush_load_tint", 1 - state.gray / 255f));
    }

    /** Saved tools and favorites, or a fresh library. Unreadable data is kept in a backup key. */
    ToolLibrary toolLibrary(Runnable unreadable) {
        ToolLibrary library = new ToolLibrary();
        library.edit(library.current().size(Math.max(2, Math.min(128, store.getInt("diameter", 64)))));
        String saved = store.getString("tools", null);
        if (saved == null) return library;
        try { return ToolLibrary.decode(java.util.Base64.getDecoder().decode(saved)); }
        catch (Exception error) {
            store.edit().putString("tools_unreadable_backup", saved).apply();
            unreadable.run();
            return library;
        }
    }

    void savePaint(PaintState state, ToolLibrary library) throws java.io.IOException {
        // "diameter" also seeds the brush size if the tool library is ever missing.
        store.edit().putInt("gray", state.gray).putInt("diameter", library.current().maximum)
                .putBoolean("wet_canvas", state.wetCanvas).putBoolean("transparent_paint", state.transparentPaint).putBoolean("watercolor", state.watercolor)
                .putBoolean("erase_mode", state.eraseMode).putBoolean("masking_fluid", state.maskingFluid).putFloat("brush_ink", state.brushInk.fullness())
                .putFloat("brush_load_ink", state.brushLoad.fullness()).putFloat("brush_load_tint", state.brushLoad.tint()).putInt("canvas_wetness", state.wetness)
                .putString("tools", java.util.Base64.getEncoder().encodeToString(library.encode())).apply();
    }
}
