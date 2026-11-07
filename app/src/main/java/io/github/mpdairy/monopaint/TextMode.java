package io.github.mpdairy.monopaint;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupWindow;

/**
 * The Text tool on the drawing screen (2026-11-01, branch {@code explore-text}; {@link TextBoxes}), a row of Play's menu
 * as Magnetic Poetry is, its side bar while it is taken up. While it is, the pen works the page's text boxes and draws
 * nothing: a tap on paper places a box there and a tap on a box opens it, in a box over the page holding Android's own
 * text field, so the keyboard (on screen, or a Bluetooth one) types into it; a tap outside it is done. A drag moves a box,
 * a drag from its right edge sets its width, a long press opens its menu: the sizes, Bold, the alignments, Make Ink
 * (the box laid as one stroke of the brush in use, {@link DrawingPad#makeInk}) and Delete. Each change is one Undo step
 * ({@link ToneDocument#setTexts}). The letters always lie over the drawing ({@link #draw}), as the magnets do, and every
 * copy shows them. E-ink, as a magnet moves: a moving box stays where it was and only its outline follows the pen,
 * through the fast update; at lift it is laid, and the area gets one clean refresh.
 */
final class TextMode {

    private final PaintActivity app;
    private final WordsMode.Host host;
    private final RasterOverlay overlay = new RasterOverlay();
    private final Paint ink = new Paint(), paper = new Paint();
    private boolean on;
    /** What a touch does: nothing yet, moves a box, sets its width, opened its menu; and the box under it (−1: paper). */
    private enum Gesture { NONE, PENDING, MOVE, WIDTH, MENU }
    private Gesture gesture = Gesture.NONE;
    private int pointer = -1, box = -1;
    private TextBoxes.Box start, now;
    private float downX, downY, downViewX, downViewY;
    private final Rect travelled = new Rect();
    private final Runnable longPress = this::longPress;
    private PopupWindow editor;
    private Canvas canvas;
    private Bitmap canvasOf;

    TextMode(PaintActivity app, WordsMode.Host host) {
        this.app = app; this.host = host;
        ink.setColor(Theme.INK); paper.setColor(Theme.PAPER);
    }

    /** Whether the Text tool is taken up. */
    boolean on() { return on; }
    /** Whether the pen is moving a box or setting its width. */
    boolean dragging() { return gesture == Gesture.MOVE || gesture == Gesture.WIDTH; }
    /** Play's Text row: taken up, or put down. */
    void toggle() {
        if (on) { close(); return; }
        if (app.busy() || app.pad == null) return;
        app.pad.words.close(); app.pad.finishStroke();
        on = true;
    }
    /** Put down (another tool, a menu, leaving the screen): a box being typed is kept, a drag dropped. */
    void close() {
        cancel();
        if (editor != null) editor.dismiss();
        if (!on) return;
        on = false;
    }
    /** A drag dropped (the canvas suspended): the box stays where it was. */
    void cancel() {
        app.pad.removeCallbacks(longPress);
        if (dragging()) { overlayOff(); host.fastPreview(false); host.present(); }
        gesture = Gesture.NONE; pointer = -1;
    }

    // The canvas's hooks

    /** Draws the page's boxes into {@code area} of {@code raster}, freshly composed: black letters over the paint, in the panel's dots (or greys on a zoomed raster). */
    void draw(Bitmap raster, Rect area) {
        ToneDocument page = host.page();
        if (page == null || page.texts().isEmpty() || TextBoxes.type == null) return;
        boolean dots = host.dots();
        for (TextBoxes.Box b : page.texts().list()) {
            int[] r = TextBoxes.bounds(b);
            Rect at = new Rect(r[0], r[1], r[2], r[3]);
            if (!at.intersect(area) || !at.intersect(0, 0, raster.getWidth(), raster.getHeight())) continue;
            byte[] a = TextBoxes.letters(b);
            int w = at.width(), bw = r[2] - r[0];
            int[] px = new int[w * at.height()];
            raster.getPixels(px, 0, w, at.left, at.top, w, at.height());
            for (int y = 0; y < at.height(); y++) for (int x = 0; x < w; x++) {
                int c = a[(at.top - r[1] + y) * bw + at.left - r[0] + x] & 255;
                if (c == 0) continue;
                int i = y * w + x, under = px[i] & 255, g = under * (255 - c) / 255;
                px[i] = dots ? Math.min(px[i] & 0xffffff, DotPattern.pixel(g, at.left + x, at.top + y) & 0xffffff) | 0xff000000 : 0xff000000 | g << 16 | g << 8 | g;
            }
            raster.setPixels(px, 0, w, at.left, at.top, w, at.height());
        }
    }
    /** Takes the moving outline off the raster before the page under it is composed again. */
    void overlayOff() { if (!overlay.isEmpty()) overlay.restore(host.raster(), host::show); }
    /** And puts it back after. */
    void overlayOn() { if (dragging() && overlay.isEmpty()) drawOutline(); }

    // Touches

    /** A touch on the page; true while the Text tool is taken up (every one-pointer touch is its). */
    boolean touch(MotionEvent event) {
        if (!on) return false;
        ToneDocument page = host.page();
        if (page == null) return true;
        int action = event.getActionMasked(), index = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN) {
            pointer = event.getPointerId(index);
            float[] p = host.toPage(event.getX(index), event.getY(index));
            downX = p[0]; downY = p[1]; downViewX = event.getX(index); downViewY = event.getY(index);
            box = page.texts().hit(p[0], p[1]);
            start = now = box >= 0 ? page.texts().get(box) : null;
            gesture = Gesture.PENDING;
            if (box >= 0) app.pad.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
            return true;
        }
        int i = event.findPointerIndex(pointer);
        if (i < 0 || gesture == Gesture.NONE || gesture == Gesture.MENU) { if (action == MotionEvent.ACTION_UP) gesture = Gesture.NONE; return true; }
        float[] p = host.toPage(event.getX(i), event.getY(i));
        if (action == MotionEvent.ACTION_MOVE) {
            if (gesture == Gesture.PENDING) {
                if (Math.hypot(event.getX(i) - downViewX, event.getY(i) - downViewY) < ViewConfiguration.get(app).getScaledTouchSlop()) return true;
                app.pad.removeCallbacks(longPress);
                if (box < 0) { gesture = Gesture.NONE; return true; }
                gesture = TextBoxes.onEdge(start, downX, downY) ? Gesture.WIDTH : Gesture.MOVE;
                int[] r = TextBoxes.rect(start);
                travelled.set(r[0], r[1], r[2], r[3]);
                host.fastPreview(true);
            }
            moved(p[0], p[1]);
            overlayOff(); drawOutline(); host.present();
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            app.pad.removeCallbacks(longPress);
            Gesture was = gesture;
            gesture = Gesture.NONE; pointer = -1;
            if (was == Gesture.PENDING && action == MotionEvent.ACTION_UP) edit(box, downX, downY);
            else if (dragging(was)) {
                moved(p[0], p[1]);
                overlayOff(); host.fastPreview(false);
                int[] r = TextBoxes.rect(now);
                travelled.union(r[0], r[1], r[2], r[3]);
                change(page, page.texts().replaced(box, now));
                host.refresh("text box moved", new Rect(travelled));
            }
        }
        return true;
    }
    private static boolean dragging(Gesture g) { return g == Gesture.MOVE || g == Gesture.WIDTH; }
    private void moved(float x, float y) {
        ToneDocument page = host.page();
        now = TextBoxes.dragged(start, gesture == Gesture.WIDTH, downX, downY, x, y, page.width, page.height);
    }
    /** One Undo step: the page's boxes as {@code next}, the page shown again. */
    private void change(ToneDocument page, TextBoxes next) {
        if (host.page() == page) app.layerEdit(() -> page.setTexts(next));
    }

    /** A box's outline where it is now, over the raster (ink outside, a line of paper in it, as a magnet's), queued. */
    private void drawOutline() {
        Bitmap raster = host.raster();
        if (raster == null || now == null) return;
        int[] r = TextBoxes.rect(now);
        int t = MagnetDrag.OUTLINE;
        for (int[] band : new int[][]{{r[0] - t, r[1] - t, r[2] + t, r[1]}, {r[0] - t, r[3], r[2] + t, r[3] + t}, {r[0] - t, r[1], r[0], r[3]}, {r[2], r[1], r[2] + t, r[3]}})
            overlay.add(new Rect(band[0], band[1], band[2], band[3]), raster);
        if (overlay.isEmpty()) return;
        overlay.save(raster);
        Canvas c = canvas(raster);
        PicturePlacer.ring(c, r[0] - t, r[1] - t, r[2] + t, r[3] + t, t - 1, ink); PicturePlacer.ring(c, r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, 1, paper);
        overlay.queue(host::show);
        travelled.union(r[0] - t, r[1] - t, r[2] + t, r[3] + t);
    }
    private Canvas canvas(Bitmap raster) {
        if (canvasOf != raster) { canvas = new Canvas(raster); canvasOf = raster; }
        return canvas;
    }

    // The menu

    private void longPress() {
        ToneDocument page = host.page();
        if (gesture != Gesture.PENDING || page == null || box < 0 || box >= page.texts().size()) return;
        gesture = Gesture.MENU;
        int i = box;
        TextBoxes.Box b = page.texts().get(i);
        java.util.List<DrawLayout.Row> rows = TextBoxes.menu(b);
        String[] names = new String[rows.size()];
        Runnable[] actions = new Runnable[names.length];
        boolean[] marked = new boolean[names.length];
        android.graphics.drawable.Drawable[] icons = new android.graphics.drawable.Drawable[names.length];
        for (int k = 0; k < names.length; k++) {
            DrawLayout.Row row = rows.get(k);
            names[k] = row.words; marked[k] = row.marked;
            if (row.icon != null) icons[k] = app.getDrawable(app.getResources().getIdentifier(row.icon, "drawable", app.getPackageName()));
            if (row.key == null) continue;
            if ("ink".equals(row.key)) actions[k] = () -> { if (host.page() == page) app.pad.makeInk(i); };
            else if ("delete".equals(row.key)) actions[k] = () -> change(page, page.texts().without(i));
            else actions[k] = () -> restyle(page, i, TextBoxes.restyled(b, row.key));
        }
        app.menuAt(bounds(TextBoxes.rect(b)), "text menu", names, icons, actions, app.prefs.largeSettingsText() ? 216 : 200, marked);
    }
    private void restyle(ToneDocument page, int i, TextBoxes.Box b) {
        if (host.page() == page && i < page.texts().size()) change(page, page.texts().replaced(i, TextBoxes.inside(b, page.width, page.height)));
    }
    /** Page rectangle {@code r} in the root's app coordinates. */
    private RectF bounds(int[] r) {
        RectF view = new RectF(r[0], r[1], r[2], r[3]);
        app.pad.pageToView.mapRect(view);
        return Popups.bounds(app.root, app.pad, view);
    }

    // Typing

    /**
     * Box {@code i} open for typing (−1: a new one, placed for a tap at {@code x, y}): Android's text field over it, in
     * its face, size and alignment at the page's zoom, the keyboard shown; done when it closes (a tap outside, Back),
     * as one Undo step. Emptied, the box goes.
     */
    private void edit(int i, float x, float y) {
        ToneDocument page = host.page();
        if (page == null || editor != null) return;
        if (app.pageLocked()) { app.note(NotebookRules.PAGE_LOCKED); return; }
        TextBoxes.Box b = i >= 0 ? page.texts().get(i) : TextBoxes.placed(x, y, page.width, page.height);
        if (i < 0 && page.texts().size() >= TextBoxes.MOST) return;
        int fine = app.dp(Theme.FINE);
        RectF at = bounds(TextBoxes.rect(b)), pageArea = Popups.bounds(app.root, app.pad);
        float scale = at.width() / b.width;
        EditText field = new EditText(app);
        field.setTypeface(Theme.textFace(b.bold));
        field.setTextSize(TypedValue.COMPLEX_UNIT_PX, b.em() * scale);
        field.setIncludeFontPadding(false);
        Paint.FontMetricsInt fm = field.getPaint().getFontMetricsInt();
        field.setLineSpacing(TextBoxes.lineHeight(b.em()) * scale - (fm.descent - fm.ascent), 1);
        field.setGravity(Gravity.TOP | (b.align == TextBoxes.CENTRE ? Gravity.CENTER_HORIZONTAL : b.align == TextBoxes.RIGHT ? Gravity.RIGHT : Gravity.LEFT));
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setHorizontallyScrolling(false);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(TextBoxes.MOST_CHARS)});
        field.setTextColor(Theme.INK);
        field.setBackground(Ui.outline(Theme.PAPER, fine, Theme.INK, 0));
        field.setPadding(fine, fine, fine, fine);
        field.setText(b.text);
        field.setSelection(b.text.length());
        int least = Math.round(2 * TextBoxes.lineHeight(b.em()) * scale) + 2 * fine;
        RectF shown = new RectF(at.left - fine, at.top - fine, at.right + fine, Math.min(pageArea.bottom, Math.max(at.bottom + fine, at.top - fine + least)));
        PopupWindow popup = Popups.create(field, app.appTurn(), true, Theme.PAPER);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        RectF panel = new RectF();
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            // The box grows as lines come, never shrinks while open (what it uncovered would wait for a refresh).
            @Override public void afterTextChanged(Editable s) {
                field.post(() -> {
                    float need = Math.min(pageArea.bottom, shown.top + field.getLineCount() * field.getLineHeight() + 2 * fine + field.getLineHeight());
                    if (need <= shown.bottom || editor != popup) return;
                    shown.bottom = need;
                    panel.set(shown); PanelCoordinates.fromView(app.root).mapRect(panel);
                    Popups.show(popup, app.root, shown);
                });
            }
        });
        popup.setOnDismissListener(() -> {
            editor = null;
            app.pad.uncovered("text", new RectF(panel));
            if (host.page() != page) return;
            TextBoxes.Box typed = b.withText(TextBoxes.clean(field.getText().toString()));
            TextBoxes next = i >= 0 ? page.texts().replaced(i, TextBoxes.inside(typed, page.width, page.height)) : page.texts().with(TextBoxes.inside(typed, page.width, page.height));
            change(page, next);
        });
        editor = popup;
        panel.set(shown); PanelCoordinates.fromView(app.root).mapRect(panel);
        Popups.show(popup, app.root, shown);
        field.requestFocus();
        field.postDelayed(() -> {
            InputMethodManager keyboard = (InputMethodManager) app.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        }, 100);
    }
}
