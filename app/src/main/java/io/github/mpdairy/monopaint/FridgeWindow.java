package io.github.mpdairy.monopaint;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Send to the Fridge's windows ({@link Fridge}; the menu's row): the first time's words, Send and Not Now; then the page
 * as it will go (Export Page's PNG), Name and Caption, My Fridge and The Fridge (at least one ticked), Send and Cancel,
 * and Sent Pages while any are kept; My Fridge's name the first time it is ticked; the sent window with the links and
 * Copy Link; the Sent list, each card with Take Down (asked first). Each window the app's dialog ({@link
 * PaintActivity#compactDialog}), as Send's (LiveShare); the PNG, the sends and take-downs on one worker, never the main thread.
 */
final class FridgeWindow {
    private static final String TAG = "Fridge";
    private final PaintActivity app;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "Fridge"));

    FridgeWindow(PaintActivity app) { this.app = app; }

    /** {@code go} once the first time's words are agreed to (at once after that; System Settings' The Fridge, Off, asks again). */
    void agreed(Runnable go) {
        if (app.prefs.fridgeAgreed()) { go.run(); return; }
        app.showDialog(new AlertDialog.Builder(app).setTitle(Fridge.ROW).setMessage(Fridge.ABOUT)
                .setPositiveButton(Fridge.SEND, (d, w) -> { app.prefs.setFridgeAgreed(true); go.run(); })
                .setNegativeButton(Fridge.NOT_NOW, null));
    }

    /** Page {@code place} of {@code book} (a snapshot, wet paint laid; released here), notebook {@code name}: its PNG made, then the send window. */
    void open(DrawingBook.Snapshot book, String name, int place) {
        worker.execute(() -> {
            byte[] png; Bitmap preview;
            try {
                List<PackedPage> pages;
                try { pages = book.packed(); } finally { book.release(); }
                png = Fridge.png(NotebookPdf.title(name), place, book.width, book.height, pages, book.options, book.pageTemplates, book.tracing, WordsMode.exportTiles());
                BitmapFactory.Options half = new BitmapFactory.Options();
                half.inSampleSize = 2;
                preview = BitmapFactory.decodeByteArray(png, 0, png.length, half);
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                Log.w(TAG, "page: " + e);
                app.runOnUiThread(() -> app.message(e instanceof Fridge.Failed ? e.getMessage() : PageExport.FAILED));
                return;
            }
            app.runOnUiThread(() -> { if (!app.destroyed) window(png, preview, name, place + 1); });
        });
    }

    private void window(byte[] png, Bitmap preview, String notebook, int page) {
        LinearLayout column = column();
        ImageView picture = new ImageView(app);
        picture.setImageBitmap(preview);
        picture.setAdjustViewBounds(true);
        picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        picture.setContentDescription("Page " + page);
        LinearLayout.LayoutParams shown = new LinearLayout.LayoutParams(-1, app.dp(320));
        column.addView(picture, shown);
        EditText name = field(column, Fridge.NAME, Fridge.NAME_MOST), caption = field(column, Fridge.CAPTION, Fridge.CAPTION_MOST);
        LinearLayout ticks = LabUi.row(app);
        LabButton mine = new LabButton(app, 0, Fridge.MINE, false), shared = new LabButton(app, 0, Fridge.SHARED, false);
        boolean[] on = {app.prefs.fridgeOwn() != null, true};
        mine.ticked(on[0]); shared.ticked(on[1]);
        ticks.addView(mine, new LinearLayout.LayoutParams(-2, -2));
        ticks.addView(shared, LabUi.after(app, -2, 44, 16));
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(-1, -2);
        below.topMargin = app.dp(8);
        column.addView(ticks, below);
        AlertDialog.Builder builder = new AlertDialog.Builder(app).setTitle(Fridge.ROW).setView(scroll(column))
                .setPositiveButton(Fridge.SEND, null).setNegativeButton(Fridge.CANCEL, null);
        if (!Fridge.cards(app.prefs.fridgeCards()).isEmpty()) builder.setNeutralButton(Fridge.SENT_PAGES, (d, w) -> sentList());
        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> { if (preview != null) picture.setImageBitmap(null); });
        dialog.show();
        app.compactDialog(dialog, 600);
        Button send = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        // At least one fridge ticked: Send greyed while neither is.
        View.OnClickListener tick = v -> {
            int i = v == mine ? 0 : 1;
            on[i] = !on[i];
            ((LabButton) v).ticked(on[i]);
            send.setEnabled(on[0] || on[1]);
        };
        mine.setOnClickListener(tick); shared.setOnClickListener(tick);
        send.setOnClickListener(v -> {
            Runnable go = () -> send(dialog, send, png, name.getText().toString(), caption.getText().toString(), on[0], on[1], notebook, page);
            if (on[0] && app.prefs.fridgeOwn() == null) ownName(go); else go.run();
        });
    }

    /** Sends {@code png}; the window stays, Send greyed, till the answer comes: sent, the sent window; refused, its note. */
    private void send(AlertDialog dialog, Button button, byte[] png, String name, String caption, boolean mine, boolean shared, String notebook, int page) {
        String[] own = mine ? app.prefs.fridgeOwn() : null;
        if (mine && own == null) return;
        button.setEnabled(false); button.setText(Fridge.SENDING);
        worker.execute(() -> {
            Fridge.Card card = null; String failed = null;
            try {
                card = Fridge.sent(Fridge.send(Fridge.sendUrl(name, caption, mine, shared), png, own == null ? null : own[1]),
                        own == null ? "" : own[0], shared, notebook, page, LocalDate.now().toEpochDay());
                synchronized (this) { app.prefs.setFridgeCards(Fridge.added(app.prefs.fridgeCards(), card)); }
            } catch (Fridge.Failed e) { failed = e.getMessage(); }
            catch (IOException | RuntimeException e) { Log.w(TAG, "send: " + e.getClass().getSimpleName()); failed = Fridge.FAILED; }
            Fridge.Card done = card; String note = failed;
            app.runOnUiThread(() -> {
                if (app.destroyed) return;
                if (done == null) { button.setText(Fridge.SEND); button.setEnabled(true); app.message(note); return; }
                dialog.dismiss();
                sentWindow(done);
            });
        });
    }

    /** Sent: where it is now, its links, Copy Link (My Fridge's link when it went there) and Close. */
    private void sentWindow(Fridge.Card card) {
        LinearLayout column = column();
        column.addView(LabUi.text(app, Fridge.sentWords(!card.mine.isEmpty(), card.shared), TypeStyle.BODY), new LinearLayout.LayoutParams(-1, -2));
        links(column, card);
        String link = card.links().get(0);
        app.showDialog(new AlertDialog.Builder(app).setTitle(Fridge.SENT).setView(column)
                .setPositiveButton(Fridge.CLOSE, null).setNeutralButton(Fridge.COPY, (d, w) -> copy(link)));
    }

    /** My Fridge's name, the first time it is ticked: made on superdood.org, its slug and key kept; then {@code go}. */
    private void ownName(Runnable go) {
        LinearLayout column = column();
        TextView about = LabUi.text(app, Fridge.OWN_ABOUT, TypeStyle.CAPTION);
        column.addView(about, new LinearLayout.LayoutParams(-1, -2));
        EditText name = field(column, Fridge.NAME, Fridge.NAME_MOST);
        TextView link = LabUi.text(app, Fridge.OWN_SHORT, TypeStyle.CAPTION);
        link.setTextColor(Theme.GREY_800);
        column.addView(link, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = app.showDialog(new AlertDialog.Builder(app).setTitle(Fridge.MINE).setView(column)
                .setPositiveButton(Fridge.MAKE, null).setNegativeButton(Fridge.CANCEL, null));
        Button make = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        make.setEnabled(false);
        // The link it makes shown as it is typed.
        name.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                String slug = Fridge.slug(s.toString());
                link.setText(slug == null ? Fridge.OWN_SHORT : Fridge.ownLink(slug).replace("https://", ""));
                make.setEnabled(slug != null);
            }
        });
        make.setOnClickListener(v -> {
            String typed = name.getText().toString(), slug = Fridge.slug(typed);
            if (slug == null) return;
            make.setEnabled(false);
            worker.execute(() -> {
                String[] own = null; String failed = null;
                try { own = Fridge.own(Fridge.own(slug, typed)); app.prefs.setFridgeOwn(own); }
                catch (Fridge.Failed e) { failed = e.getMessage(); }
                catch (IOException | RuntimeException e) { Log.w(TAG, "own: " + e.getClass().getSimpleName()); failed = Fridge.FAILED; }
                boolean made = own != null; String note = failed;
                app.runOnUiThread(() -> {
                    if (app.destroyed) return;
                    if (!made) { make.setEnabled(true); app.message(note); return; }
                    dialog.dismiss();
                    go.run();
                });
            });
        });
    }

    /** Sent to the Fridge: each card kept, newest first, its line and links, and Take Down (asked first). */
    private void sentList() {
        LinearLayout column = column();
        for (Fridge.Card card : Fridge.cards(app.prefs.fridgeCards())) {
            LinearLayout entry = LabUi.column(app);
            entry.addView(LabUi.text(app, card.label(), TypeStyle.BODY), new LinearLayout.LayoutParams(-1, -2));
            links(entry, card);
            LabButton down = new LabButton(app, 0, Fridge.TAKE_DOWN, false);
            down.setContentDescription(Fridge.TAKE_DOWN + ", " + card.label());
            down.setOnClickListener(v -> confirmDown(card, entry));
            entry.addView(down, new LinearLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams apart = new LinearLayout.LayoutParams(-1, -2);
            apart.bottomMargin = app.dp(12);
            column.addView(entry, apart);
        }
        app.showDialog(new AlertDialog.Builder(app).setTitle(Fridge.SENT_LIST).setView(scroll(column)).setPositiveButton(Fridge.CLOSE, null));
    }
    private void confirmDown(Fridge.Card card, View entry) {
        app.showDialog(new AlertDialog.Builder(app).setTitle(Fridge.TAKE_DOWN + "?").setMessage(Fridge.UNDONE)
                .setPositiveButton(Fridge.TAKE_DOWN, (d, w) -> takeDown(card, entry)).setNegativeButton(Fridge.CANCEL, null));
    }
    /** {@code card} taken down on superdood.org, then forgotten here; its entry gone from the list. */
    private void takeDown(Fridge.Card card, View entry) {
        worker.execute(() -> {
            String note;
            try {
                Fridge.takenDown(Fridge.delete(card));
                synchronized (this) { app.prefs.setFridgeCards(Fridge.removed(app.prefs.fridgeCards(), card.id)); }
                note = Fridge.TAKEN_DOWN;
            } catch (Fridge.Failed e) { note = e.getMessage(); }
            catch (IOException | RuntimeException e) { Log.w(TAG, "take down: " + e.getClass().getSimpleName()); note = Fridge.DOWN_FAILED; }
            String said = note;
            app.runOnUiThread(() -> {
                if (app.destroyed) return;
                if (Fridge.TAKEN_DOWN.equals(said)) entry.setVisibility(View.GONE);
                app.message(said);
            });
        });
    }

    private void links(LinearLayout column, Fridge.Card card) {
        for (String link : card.links()) {
            TextView line = LabUi.text(app, link.replace("https://", ""), TypeStyle.CAPTION);
            line.setTypeface(Theme.textFace(true));
            line.setTextIsSelectable(true);
            column.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }
    }
    private void copy(String link) {
        ClipboardManager clipboard = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(Fridge.SHARED, link));
        app.message(Fridge.COPIED);
    }
    /** A single-line field, {@code hint} its hint and words, at most {@code most} characters. */
    private EditText field(LinearLayout column, String hint, int most) {
        EditText field = new EditText(app);
        field.setSingleLine(true); field.setHint(hint); field.setContentDescription(hint);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(most)});
        Theme.style(field, TypeStyle.BODY);
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(-1, -2);
        below.topMargin = app.dp(8);
        column.addView(field, below);
        return field;
    }
    private LinearLayout column() {
        LinearLayout column = LabUi.column(app);
        column.setPadding(app.dp(24), app.dp(8), app.dp(24), app.dp(8));
        return column;
    }
    private ScrollView scroll(View content) {
        ScrollView scroll = new ScrollView(app);
        scroll.addView(content);
        return scroll;
    }
}
