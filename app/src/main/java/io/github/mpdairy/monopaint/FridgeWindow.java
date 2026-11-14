package io.github.mpdairy.monopaint;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
 * Send to the Fridge's windows ({@link Fridge}; the menu's row on any notebook's page): the page as it will go (Export
 * Page's PNG: a word's post with its band, its word under the title; any other on plain paper), Name (required, the tablet's) and Caption, Send
 * and Cancel, and Sent Pages while any are kept; the first time, the email step before them (Send Link, then waiting for the link to be tapped); the sent window with the link and Copy Link;
 * the Sent list, each card with Take Down (asked first); Stop Taking Part's request; System Settings' Fridge Name. Each window the app's dialog ({@link
 * PaintActivity#compactDialog}), as Send's (LiveShare); the PNG and every request on one worker, never the main thread.
 */
final class FridgeWindow {
    private static final String TAG = "Fridge";
    /** While the window waits for the email's link: asked this often, for this long at most. */
    private static final long POLL_MS = 5_000, WAIT_MS = 10 * 60_000;
    private final PaintActivity app;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "Fridge"));

    FridgeWindow(PaintActivity app) { this.app = app; }

    /**
     * Page {@code place} of {@code book} (a snapshot, wet paint laid; released here), notebook {@code name}, posted under
     * {@code wordKey} ({@link Prompts#wordKey}: a prompt page's own key; null, no word): its PNG made and, with a key kept,
     * whether its email is confirmed yet; then the send window.
     */
    void open(DrawingBook.Snapshot book, String name, int place, String wordKey) {
        String key = app.prefs.fridgeTablet();
        worker.execute(() -> {
            byte[] png; Bitmap preview;
            try {
                List<PackedPage> pages;
                try { pages = book.packed(); } finally { book.release(); }
                png = Fridge.png(NotebookPdf.title(name), place, book.width, book.height, pages, book.options, book.pageTemplates, book.tracing, WordsMode.exportTiles(), wordKey != null);
                BitmapFactory.Options small = new BitmapFactory.Options();
                small.inSampleSize = 4;
                preview = BitmapFactory.decodeByteArray(png, 0, png.length, small);
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                Log.w(TAG, "page: " + e);
                app.runOnUiThread(() -> app.message(e instanceof Fridge.Failed ? e.getMessage() : PageExport.FAILED));
                return;
            }
            // Asked each time (the link may have been tapped since); unreachable, Send, and the send says what is wrong.
            // Its name too, kept when this app has none (a name sent before the app kept it).
            boolean confirmed;
            try {
                Fridge.Answer answer = key.isEmpty() ? null : Fridge.confirmed(key);
                confirmed = answer != null && Fridge.confirmed(answer);
                String given = answer == null ? "" : Fridge.nameOf(answer);
                if (app.prefs.fridgeName().isEmpty() && !given.isEmpty()) app.prefs.setFridgeName(given);
            } catch (IOException | RuntimeException e) { confirmed = true; }
            boolean ready = confirmed;
            app.runOnUiThread(() -> { if (!app.destroyed) new SendWindow(png, preview, name, place + 1, wordKey).show(ready); });
        });
    }

    /** The send window: the email step first while this tablet's email isn't confirmed, then Name, Caption and Send. */
    private final class SendWindow {
        final byte[] png; final String notebook, slug; final int page;
        final LinearLayout column = column(), ask = LabUi.column(app);
        final ImageView picture = new ImageView(app);
        final TextView waiting = LabUi.text(app, Fridge.WAITING_LINK, TypeStyle.BODY), terms = LabUi.text(app, Fridge.TERMS, TypeStyle.CAPTION);
        final EditText email, name, caption;
        AlertDialog dialog; Button go;
        long waitingSince;

        SendWindow(byte[] png, Bitmap preview, String notebook, int page, String wordKey) {
            this.png = png; this.notebook = notebook; this.page = page; this.slug = Prompts.slug(wordKey);
            // A small tilted preview, as a page pinned to a fridge (the mockup's).
            picture.setImageBitmap(preview);
            picture.setAdjustViewBounds(true);
            picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
            picture.setRotation(-3);
            picture.setContentDescription("Page " + page);
            column.addView(picture, new LinearLayout.LayoutParams(-1, app.dp(180)));
            // The word and its line only for a word's post.
            if (slug != null) {
                TextView word = LabUi.text(app, NotebookTemplate.of(wordKey).label, TypeStyle.BODY);
                word.setTypeface(Theme.face(700));
                line(column, word, 8);
                String about = Prompts.about(wordKey, app.openWords);
                if (!about.isEmpty()) line(column, LabUi.text(app, about, TypeStyle.CAPTION), 0);
            }
            line(ask, LabUi.text(app, Fridge.EMAIL_FIRST, TypeStyle.BODY), 0);
            email = field(ask, Fridge.EMAIL, 254);
            email.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            line(column, ask, 12);
            line(column, waiting, 12);
            name = field(column, Fridge.NAME, Fridge.NAME_MOST);
            name.setText(app.prefs.fridgeName());
            caption = field(column, Fridge.CAPTION, Fridge.CAPTION_MOST);
            line(column, terms, 8);
        }

        void show(boolean confirmed) {
            AlertDialog.Builder builder = new AlertDialog.Builder(app).setTitle(Fridge.ROW).setView(scroll(column))
                    .setPositiveButton(Fridge.SEND, null).setNegativeButton(Fridge.CANCEL, null);
            if (!Fridge.cards(app.prefs.fridgeCards()).isEmpty()) builder.setNeutralButton(Fridge.SENT_PAGES, (d, w) -> sentList());
            dialog = builder.create();
            dialog.setOnDismissListener(d -> picture.setImageBitmap(null));
            dialog.show();
            app.compactDialog(dialog, 600);
            go = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            go.setOnClickListener(v -> { if (ask.getVisibility() == View.VISIBLE) sendLink(); else send(); });
            // A name required (the owner, 2026-11-13): Send and Send Link greyed while it's empty, unless waiting or sending already.
            name.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence t, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence t, int a, int b, int c) { }
                @Override public void afterTextChanged(android.text.Editable t) {
                    if (waiting.getVisibility() != View.VISIBLE && !Fridge.SENDING.contentEquals(go.getText())) go.setEnabled(named());
                }
            });
            if (confirmed) sendStep(); else emailStep();
        }
        boolean named() { return !Fridge.cut(name.getText().toString(), Fridge.NAME_MOST).isEmpty(); }
        /** Email, Name, Caption, the terms, and Send Link. */
        void emailStep() {
            ask.setVisibility(View.VISIBLE); terms.setVisibility(View.VISIBLE); waiting.setVisibility(View.GONE);
            go.setText(Fridge.SEND_LINK); go.setEnabled(named());
        }
        /** Name, Caption and Send. */
        void sendStep() {
            ask.setVisibility(View.GONE); terms.setVisibility(View.GONE); waiting.setVisibility(View.GONE);
            go.setText(Fridge.SEND); go.setEnabled(named());
        }

        /** The link that confirms the email asked for (a key kept or none: a new tablet's comes back), then waiting for it. */
        void sendLink() {
            String address = email.getText().toString();
            if (!Fridge.emailish(address)) { app.message(Fridge.NOT_EMAIL); return; }
            go.setEnabled(false); go.setText(Fridge.SENDING);
            worker.execute(() -> {
                String note = null;
                try {
                    String key = app.prefs.fridgeTablet();
                    Fridge.Answer answer = Fridge.confirm(address, key.isEmpty() ? null : key);
                    // A key the Fridge no longer knows (a tablet deleted before it confirmed): this tablet starts again, as a new one.
                    if (answer.code == 403 && !key.isEmpty()) { app.prefs.setFridgeTablet(""); key = ""; answer = Fridge.confirm(address, null); }
                    app.prefs.setFridgeTablet(Fridge.tablet(answer, key));
                } catch (Fridge.Failed e) { note = e.getMessage(); }
                catch (IOException | RuntimeException e) { Log.w(TAG, "confirm: " + e.getClass().getSimpleName()); note = Fridge.FAILED; }
                String said = note;
                app.runOnUiThread(() -> {
                    if (app.destroyed || !dialog.isShowing()) return;
                    if (said != null) { emailStep(); app.message(said); return; }
                    ask.setVisibility(View.GONE); terms.setVisibility(View.GONE); waiting.setVisibility(View.VISIBLE);
                    go.setText(Fridge.SEND_LINK); go.setEnabled(false);
                    waitingSince = android.os.SystemClock.uptimeMillis();
                    column.postDelayed(this::poll, POLL_MS);
                });
            });
        }
        /** Asks whether the link was tapped, every {@link #POLL_MS} while the window is open: once it is, the send; after {@link #WAIT_MS}, the email step again. */
        void poll() {
            if (app.destroyed || !dialog.isShowing()) return;
            if (android.os.SystemClock.uptimeMillis() - waitingSince > WAIT_MS) { emailStep(); return; }
            String key = app.prefs.fridgeTablet();
            worker.execute(() -> {
                boolean yes;
                try { yes = Fridge.confirmed(Fridge.confirmed(key)); } catch (IOException | RuntimeException e) { yes = false; }
                boolean confirmed = yes;
                app.runOnUiThread(() -> {
                    if (app.destroyed || !dialog.isShowing()) return;
                    if (confirmed) send(); else column.postDelayed(this::poll, POLL_MS);
                });
            });
        }

        /**
         * Sends the page, under its word if it has one, updating its card when one was sent from it before ({@link
         * Fridge#replaces}; gone from the Fridge, forgotten and sent as new); the window stays, its button greyed, till the
         * answer: sent, the name kept and the sent window; an email not confirmed, the email step; refused, its note.
         */
        void send() {
            String named = Fridge.cut(name.getText().toString(), Fridge.NAME_MOST), captioned = caption.getText().toString();
            waiting.setVisibility(View.GONE);
            go.setEnabled(false); go.setText(Fridge.SENDING);
            worker.execute(() -> {
                Fridge.Card card = null; String note, failed = null; boolean unconfirmed = false;
                try {
                    String key = app.prefs.fridgeTablet();
                    Fridge.Card old = Fridge.replaces(app.prefs.fridgeCards(), slug, notebook, page);
                    Fridge.Answer answer = Fridge.send(Fridge.sendUrl(slug, named, captioned, old == null ? null : old.id), png, key);
                    if (old != null && Fridge.replacedGone(answer)) {
                        synchronized (FridgeWindow.this) { app.prefs.setFridgeCards(Fridge.removed(app.prefs.fridgeCards(), old.id)); }
                        old = null;
                        answer = Fridge.send(Fridge.sendUrl(slug, named, captioned, null), png, key);
                    }
                    unconfirmed = Fridge.unconfirmed(answer);
                    card = Fridge.sent(answer, slug, notebook, page, LocalDate.now().toEpochDay(), old);
                    note = Fridge.sentNote(answer, old != null);
                    app.prefs.setFridgeName(named);
                    synchronized (FridgeWindow.this) { app.prefs.setFridgeCards(Fridge.added(app.prefs.fridgeCards(), card)); }
                } catch (Fridge.Failed e) { note = null; failed = e.getMessage(); }
                catch (IOException | RuntimeException e) { Log.w(TAG, "send: " + e.getClass().getSimpleName()); note = null; failed = Fridge.FAILED; }
                Fridge.Card done = card; String said = note, refused = failed; boolean again = unconfirmed;
                app.runOnUiThread(() -> {
                    if (app.destroyed) return;
                    if (done != null) { dialog.dismiss(); sentWindow(done, said); return; }
                    if (!dialog.isShowing()) return;
                    if (again) emailStep(); else sendStep();
                    app.message(refused);
                });
            });
        }
    }

    /**
     * Stop Taking Part: this tablet, its email and its pages gone from the Fridge, then its key, its cards and the Prompts
     * forgotten here, and {@code after}; already gone (403) too. Unreachable: said, nothing forgotten, {@code after} not run.
     */
    void leave(Runnable after) {
        String key = app.prefs.fridgeTablet();
        worker.execute(() -> {
            String note = null;
            try { if (!key.isEmpty()) Fridge.left(Fridge.leave(key)); }
            catch (Fridge.Failed e) { note = e.getMessage(); }
            catch (IOException | RuntimeException e) { Log.w(TAG, "leave: " + e.getClass().getSimpleName()); note = Fridge.UNREACHABLE; }
            String said = note;
            app.runOnUiThread(() -> {
                if (app.destroyed) return;
                if (said != null) { app.message(said); return; }
                synchronized (FridgeWindow.this) { app.prefs.setFridgeTablet(""); app.prefs.setFridgeCards(""); app.prefs.setFridgeName(""); }
                app.prefs.setPromptsOn(false);
                app.message(Fridge.STOPPED);
                after.run();
            });
        });
    }

    /**
     * System Settings' Fridge Name (the owner, 2026-11-13: "they should be able to update that in settings"): the name
     * prompt ({@link NamePrompt}) holding the name, Add a name. while empty; the name sent (api/name) and, taken, kept and
     * {@code after} run; refused, the server's words; unreachable, {@link Fridge#UNREACHABLE}.
     */
    void rename(Runnable after) {
        NamePrompt.show(app, SystemSettings.FRIDGE_NAME, app.prefs.fridgeName(), typed -> Fridge.cut(typed, Fridge.NAME_MOST).isEmpty() ? Fridge.ADD_NAME : null, typed -> {
            String key = app.prefs.fridgeTablet();
            worker.execute(() -> {
                String note = null;
                try { app.prefs.setFridgeName(Fridge.named(Fridge.rename(typed, key), typed)); }
                catch (Fridge.Failed e) { note = e.getMessage(); }
                catch (IOException | RuntimeException e) { Log.w(TAG, "name: " + e.getClass().getSimpleName()); note = Fridge.UNREACHABLE; }
                String said = note;
                app.runOnUiThread(() -> {
                    if (app.destroyed) return;
                    if (said != null) app.message(said); else after.run();
                });
            });
        });
    }

    /** Sent: on the Fridge, or waiting for a look ({@code note}); its link, Copy Link and Close. */
    private void sentWindow(Fridge.Card card, String note) {
        LinearLayout column = column();
        line(column, LabUi.text(app, note, TypeStyle.BODY), 0);
        link(column, card);
        app.showDialog(new AlertDialog.Builder(app).setTitle(Fridge.SENT).setView(column)
                .setPositiveButton(Fridge.CLOSE, null).setNeutralButton(Fridge.COPY, (d, w) -> copy(card.url)));
    }

    /** Sent to the Fridge: each card kept, newest first, its line and links, and Take Down (asked first). */
    private void sentList() {
        LinearLayout column = column();
        for (Fridge.Card card : Fridge.cards(app.prefs.fridgeCards())) {
            LinearLayout entry = LabUi.column(app);
            entry.addView(LabUi.text(app, card.label(), TypeStyle.BODY), new LinearLayout.LayoutParams(-1, -2));
            link(entry, card);
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

    private void link(LinearLayout column, Fridge.Card card) {
        TextView line = LabUi.text(app, card.url.replace("https://", ""), TypeStyle.CAPTION);
        line.setTypeface(Theme.textFace(true));
        line.setTextIsSelectable(true);
        column.addView(line, new LinearLayout.LayoutParams(-1, -2));
    }
    private void copy(String link) {
        ClipboardManager clipboard = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(Fridge.ROW, link));
        app.message(Fridge.COPIED);
    }
    private void line(LinearLayout column, View view, int gap) {
        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(-1, -2);
        below.topMargin = app.dp(gap);
        column.addView(view, below);
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
