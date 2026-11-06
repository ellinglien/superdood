package io.github.mpdairy.monopaint;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The Tools page, the full-screen page the tool picker's Library opens (a long press on a slot opens the picker; Tools
 * First opens this page at once) (spec docs/archive/superpowers/specs/2026-10-15-tools-page-design.md; the redesign,
 * 2026-11-03, the owner: "it's not clear what to do once you're there and it seems like it should just be simpler"),
 * all in the app's face (Ukulele, {@link Theme#face}), on paper with no frames or grid lines.
 * The bar ({@link LabUi.PageBar}): <b>Back</b> (the caret and its word), the title <b>Toolbar</b> over the page's job,
 * "Tap a slot, then a brush for it." (a change's note, "Kept Fineliner in Recent", in its place for a moment). Then what
 * is changed: the slots, one row of {@link ToolCard} tiles across the page (2026-11-03, the owner: "all on one line";
 * each its drawing alone, as the side bar shows it; the selected one inverted, ink with its drawing in paper, a slot
 * changed on this visit a dot under it); the selected slot's tool, its name (bold) and its one line, with <b>Edit in
 * Brush Lab</b> boxed at the row's right. Then the Library: <b>Search</b> (its field and × in its place while open)
 * with the pages ("‹ 1 / 2 ›") at the row's right; the filter row's chips ({@link ToolsPage.Chip}: Recent, Saved, the
 * kinds, Kits; the chosen one filled); and one grid of {@link ToolCard}s. Last, at the foot, the <b>try strip</b>
 * ({@link TestArea#strip}, Brush Lab's too: "Draw here to try Fill in" and Clear over the test page, drawing with the
 * selected slot's tool at its size and grey). Stacked in portrait; in landscape the slots' row spans the page, the name
 * row over the try strip a column at the left under it. Every size is {@link ToolsLayout}'s. No Undo (the drawing screen's takes each change
 * back), no Load Kit (Kits in the filter row); Tools First (the picker's foot row) in the bar only while it is on, the
 * one way to turn it off then.
 * <p>
 * Tap a slot, then a card (the owner: "click a slot and then click an option from library"): the card's brush is the
 * slot's here (its card underlined, its slot showing it, the page staying to try it), its size following it; an unsaved
 * brush it replaces is kept in Recent, said in the bar ({@link ToolsPage#note}). Back takes each slot its tool. A long
 * press on a saved card deletes it, asked in the search's place. The drawing screen makes the changes ({@link #BRUSHES}:
 * an Undo step each there), selects the slot selected here, and opens Brush Lab after when asked ({@link #LAB}); Brush
 * Lab's Back comes here again, the slots' start carried through ({@link #START}). Turned as the drawing is ({@link
 * #TURN}). Everything it does is {@link ToolsPage}'s; nothing scrolls, and only what changed is drawn again (e-ink).
 */
public final class ToolsActivity extends Activity {
    /**
     * Extras: the slot it opens on, and the drawing screen's turn in degrees; back, the slot selected, the brush to put
     * in each slot (as {@link Brush#write}; null: unchanged), and whether to open Brush Lab on the selected slot. Both
     * ways, the slots' start ({@link ToolSlots.Start#write}; in: null when opened from the drawing screen).
     */
    static final String SLOT = "slot", TURN = "turn", BRUSHES = "brushes", LAB = "lab", START = "start";
    /** In: when the drawing screen opened it ({@link SystemClock#uptimeMillis}), for the log's "Tools page shown in N ms". */
    static final String OPENED = "opened";
    /**
     * In: the note when the notebook's toolbar can't change ({@link NotebookRules#toolsFixed}; null: it can), and the
     * library card's screen that frees it ({@link NotebookRules#toolsSettings}, a {@link CardSheet.Screen} name; null:
     * none, sealed). With a note the bar says it in place of the title, its line empty, the library's cards are greyed
     * and take no tap, and <b>Notebook Settings</b> shows in the bar. Back, SETTINGS says to open that screen.
     */
    static final String FIXED = "fixed", SETTINGS = "settings";
    /**
     * Tool kits ({@link ToolKits}). In: how many slots the bar shows ({@link NotebookOptions#slotsShown}). Back: the count
     * a kit raised it to (-1: unchanged), the eraser's kind a kit set (-1: unchanged), and whether the slots' changes are
     * one Undo step there (a kit was loaded).
     */
    static final String BAR = "bar", ERASER = "eraser", TOGETHER = "together";
    /** The note, or null, and the screen Notebook Settings opens, or null. */
    private String fixed, settings;

    private ToolsPage page;
    private ToolsLayout layout;
    private SampleQueue samples;
    private FrameLayout root;
    /** When the page was opened, until its first frame is drawn (then 0). */
    private long opened;
    /** The try strip's test page, drawing with the tool as it stands. */
    private TestArea test;
    /** The bar's line: the page's job, or a change's note for a moment. */
    private TextView job;
    /** The slots' tiles. */
    private final ToolCard[] slotCards = new ToolCard[ToolSlots.COUNT];
    /** The name row's words: the tool's name and line; and what they show, to draw them again only when that changes. */
    private TextView toolName, toolLine;
    private LabButton reset;
    private String toolShown;
    /** The kit sheet over the page while open, else null. */
    private View sheet;
    // The search's row (Search, or its field, or a delete's question), the pages, the chips, the grid and its cards, and what they show.
    private LabButton find;
    private LinearLayout pager, question;
    private LabUi.Wrap chips;
    /** More Brushes, the shelf's chip, beside Search (the release pare-down, 2026-11-05). */
    private TextView more;
    private TextView empty;
    private LinearLayout grid;
    private String chipsShown, findShown, gridShown, pagesShown;
    private List<ToolsPage.Card> shown = Collections.emptyList();
    private ToolCard[] cards = new ToolCard[0];
    /** The saved card whose delete waits for an answer in the search's place, or null. */
    private ToolsPage.Card confirming;
    /** The search's field and ×, in Search's place, and whether it is open. */
    private LinearLayout searchRow;
    private EditText searchField;
    private boolean searchOpen;
    /** The grid follows the typing once per pause, not each letter (e-ink). */
    static final int SEARCH_MS = 250;
    private final Runnable searchNow = () -> { page.search(searchField.getText().toString()); render(); };

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(Ui.largerText(base)); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Assets.install(getApplicationContext().getAssets()::open);
        Theme.fonts(this);
        SharedPreferences stored = getSharedPreferences(PaintPreferences.FILE, MODE_PRIVATE);
        int slot = Math.max(0, Math.min(ToolSlots.COUNT - 1, getIntent().getIntExtra(SLOT, 0)));
        fixed = getIntent().getStringExtra(FIXED);
        settings = fixed == null ? null : getIntent().getStringExtra(SETTINGS);
        BrushLibrary.Store store = InkWash.store(stored);
        page = new ToolsPage(new ToolSlots(store, new Random()), store, slot, getIntent().getStringArrayExtra(START));
        page.bar(getIntent().getIntExtra(BAR, ToolSlots.COUNT));
        samples = new SampleQueue();
        test = new TestArea(this, samples, new TestArea.Host() {
            @Override public Brush brush() { return page.current(); }
            @Override public ToolSwatches.Look look() { return page.look(); }
            @Override public ToolSwatches swatches() { return page.slots.swatches; }
        });
        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.PAPER);
        opened = getIntent().getLongExtra(OPENED, SystemClock.uptimeMillis());
        // Laid out for the screen before a frame is drawn: no blank page first on the e-ink panel.
        root.getViewTreeObserver().addOnPreDrawListener(() -> {
            if (layout == null || layout.width != dpOf(root.getWidth()) || layout.height != dpOf(root.getHeight())) return !build();
            if (opened > 0) { long since = opened; root.post(() -> Log.i(ProbeActivity.TAG, "Tools page shown in " + (SystemClock.uptimeMillis() - since) + " ms")); opened = 0; }
            return true;
        });
        // Turned as the drawing screen is, while Android stays in portrait.
        QuarterTurnLayout turned = new QuarterTurnLayout(this);
        turned.setTurn(getIntent().getIntExtra(TURN, 0));
        turned.addView(root);
        turned.setBackgroundColor(Theme.PAPER);
        setContentView(turned);
    }

    /**
     * Lays the page out for the screen: the bar, the slots' row, then the name row, the Library and the try strip
     * (portrait: stacked; landscape: side by side). The try strip keeps what is drawn on it. Whether it did (not with no
     * size yet).
     */
    private boolean build() {
        int width = dpOf(root.getWidth()), height = dpOf(root.getHeight());
        if (width <= 0 || height <= 0 || isFinishing()) return false;
        layout = new ToolsLayout(width, height);
        page.layout(layout.perPage(), layout.cardWidth);
        test.pad.disconnect();
        root.removeAllViews();
        sheet = null;
        toolShown = null; chipsShown = null; findShown = null; gridShown = null; pagesShown = null;
        LinearLayout column = LabUi.column(this);
        column.addView(bar(), new LinearLayout.LayoutParams(-1, dp(ToolsLayout.BAR)));
        int side = dp(layout.side);
        LinearLayout.LayoutParams slotsPlace = new LinearLayout.LayoutParams(-1, dp(layout.slotCard));
        slotsPlace.setMargins(side, dp(layout.gap), side, dp(layout.gap));
        column.addView(slots(), slotsPlace);
        LinearLayout main = layout.wide ? LabUi.row(this) : LabUi.column(this);
        main.setGravity(Gravity.TOP);
        main.setPadding(side, 0, side, side);
        View named = named(), strip = test.strip(ToolsLayout.TRY_HEAD, 0, 8);
        LinearLayout.LayoutParams place = layout.wide ? new LinearLayout.LayoutParams(0, -1, 1) : new LinearLayout.LayoutParams(-1, 0, 1);
        if (layout.wide) {
            // Landscape: the name row over the try strip at the left, the Library beside them.
            LinearLayout left = LabUi.column(this);
            left.addView(named, new LinearLayout.LayoutParams(-1, dp(ToolsLayout.NAME_ROW)));
            left.addView(strip, new LinearLayout.LayoutParams(-1, dp(ToolsLayout.TRY_HEAD + layout.testHeight)));
            main.addView(left, new LinearLayout.LayoutParams(dp(layout.top), -1));
            place.leftMargin = dp(layout.apart);
            main.addView(library(), place);
        } else {
            // Portrait: the name row, the Library, then the try strip at the foot (the owner: "move the preview area to the bottom").
            main.addView(named, new LinearLayout.LayoutParams(-1, dp(ToolsLayout.NAME_ROW)));
            place.topMargin = dp(layout.apart);
            main.addView(library(), place);
            LinearLayout.LayoutParams stripPlace = new LinearLayout.LayoutParams(-1, dp(ToolsLayout.TRY_HEAD + layout.testHeight));
            stripPlace.topMargin = dp(layout.gap);
            main.addView(strip, stripPlace);
        }
        column.addView(main, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));
        render();
        // The pad may keep its size and place, so nothing else reconnects it after the disconnect above.
        test.pad.post(test.pad::connect);
        return true;
    }
    /** The slots, one row of tiles across the page (the owner, 2026-11-03: "all on one line"). */
    private View slots() {
        LinearLayout row = LabUi.row(this);
        for (int slot = 0; slot < layout.slotCount; slot++) {
            int i = slot;
            ToolCard card = new ToolCard(this, samples).tile();
            card.setOnClickListener(v -> { test.pad.finishStroke(); page.select(i); render(); });
            slotCards[slot] = card;
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1);
            if (slot > 0) cell.leftMargin = dp(layout.slotGap);
            row.addView(card, cell);
        }
        return row;
    }
    /** The selected slot's tool named, with Edit in Brush Lab at the row's right (Reset to Default before it, for an edited Basic tool). */
    private View named() {
        // The tool: its name bold, then its one line, grey ("Fill in · fills shapes"); Edit in Brush Lab at the right.
        LinearLayout named = LabUi.row(this);
        toolName = LabUi.text(this, "", TypeStyle.CAPTION);
        toolName.setTypeface(Theme.face(700));
        toolName.setSingleLine(true);
        toolName.setEllipsize(TextUtils.TruncateAt.END);
        named.addView(toolName, new LinearLayout.LayoutParams(-2, -2));
        toolLine = LabUi.text(this, "", TypeStyle.CAPTION);
        toolLine.setTextColor(Theme.GREY_800);
        toolLine.setSingleLine(true);
        toolLine.setEllipsize(TextUtils.TruncateAt.END);
        named.addView(toolLine, new LinearLayout.LayoutParams(0, -2, 1));
        // Reset to Default, plain words, while the tool is a Basic tool changed in Brush Lab (the tool picker's top row).
        reset = new LabButton(this, 0, DrawLayout.RESET, false).quiet();
        reset.setOnClickListener(v -> { ToolPresets.Preset p = page.edited(); if (p != null) pick(p.brush()); });
        named.addView(reset, new LinearLayout.LayoutParams(-2, -1));
        // Brush Lab on the slot's brush, boxed, saying what it does (the owner: "brush lab button on top right (if you're not familiar)").
        LabButton lab = new LabButton(this, R.drawable.ic_ph_flask, ToolsLayout.LAB, false);
        lab.setBackground(Theme.rounded(this, Theme.PAPER, Theme.BASE, Theme.INK, ToolsLayout.PAD_RADIUS));
        lab.setOnClickListener(v -> leave(true));
        named.addView(lab, LabUi.after(this, -2, 40, layout.gap));
        return named;
    }
    /** Slot {@code i}'s tile as the page stands: its picture (kept by its brush, its size and the art box) and its name. */
    static void showSlot(ToolCard card, ToolsLayout layout, ToolsPage page, int i) {
        Brush brush = page.current(i);
        ToolSwatches.Look look = page.look(i);
        card.setArt(layout.slotArt(), layout.slotArtHeight());
        card.show(brush.identity() + " tray " + look.px, brush, LabLibrary.traySampler(brush, look), ToolsPage.name(brush));
    }
    /** A Library card's tile: its picture (kept by its brush and the art box) and its name. */
    static void showCard(ToolCard view, ToolsLayout layout, ToolsPage.Card card) {
        view.setArt(layout.cardArt(), layout.cardArtHeight());
        view.show(card.brush.identity() + " tray", card.brush, LabLibrary.traySampler(card.brush, null), card.name);
    }
    /**
     * Draws ahead, on {@code samples}' thread, the pictures the page would open with on slot {@code slot} on a {@code width
     * × height} dp screen: the nine slots and the first page of the chip last chosen, kept for it ({@link ToolCard});
     * those kept already cost nothing. The owner, 2026-10-28: slow "just on the first load", because of the pictures.
     */
    static void warm(Context context, SampleQueue samples, ToolSlots slots, BrushLibrary.Store store, int slot, int width, int height) {
        ToolsLayout layout = new ToolsLayout(width, height);
        ToolsPage page = new ToolsPage(slots, store, Math.max(0, Math.min(ToolSlots.COUNT - 1, slot)));
        page.layout(layout.perPage(), layout.cardWidth);
        for (int i = 0; i < layout.slotCount; i++) showSlot(new ToolCard(context, samples), layout, page, i);
        for (ToolsPage.Card card : page.cards()) showCard(new ToolCard(context, samples), layout, card);
    }
    /** Small grey words ({@link TypeStyle#CAPTION}, grey-800, one line): the pages' count. */
    private TextView small(String words) {
        TextView view = LabUi.text(this, words, TypeStyle.CAPTION);
        view.setTextColor(Theme.GREY_800);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    /** Shows the page as it now stands, drawing again only what changed. */
    private void render() {
        if (layout == null) return;
        int selected = page.slot();
        for (int i = 0; i < layout.slotCount; i++) {
            Brush brush = page.current(i);
            ToolCard card = slotCards[i];
            showSlot(card, layout, page, i);
            card.setChosen(i == selected);
            card.setChanged(page.changed(i));
            card.setContentDescription(brush.shownName() + (i == selected ? ", selected" : "") + (page.changed(i) ? ", changed" : ""));
        }
        renderTool();
        renderFind();
        renderChips();
        renderPages();
        renderGrid();
    }

    // The bar

    /** The page's bar: Back, the title over its job (a fixed toolbar's note in the title's place), then Notebook Settings for a fixed one. */
    private View bar() {
        // A fixed toolbar: its note in the title's place, so the page never seems to take a pick it won't keep.
        LabUi.PageBar bar = new LabUi.PageBar(this, layout.side, "Back", fixed != null ? fixed : ToolsLayout.TITLE, fixed != null ? "" : ToolsLayout.JOB);
        if (fixed != null) Theme.style(bar.title, TypeStyle.BODY);
        bar.back.setOnClickListener(v -> leave(false));
        job = bar.line;
        if (settings != null) {
            LabButton open = new LabButton(this, 0, NotebookRules.NOTEBOOK_SETTINGS, false);
            open.setOnClickListener(v -> leave(false, true));
            bar.view.addView(open, LabUi.after(this, -2, 40, 10));
        }
        // Hold Opens Full Library is the picker's foot row's checkbox; while it is ticked the long press skips the picker, so
        // only here, and only then, can it be unticked again (Brush Lab's As New's box).
        PaintPreferences prefs = new PaintPreferences(getSharedPreferences(PaintPreferences.FILE, MODE_PRIVATE));
        if (prefs.toolsFirst()) {
            LabButton first = new LabButton(this, 0, DrawLayout.TOOLS_FIRST, false).ticked(true);
            first.setOnClickListener(v -> {
                boolean on = !prefs.toolsFirst();
                prefs.setToolsFirst(on);
                first.ticked(on);
            });
            bar.view.addView(first, LabUi.after(this, -2, 40, 10));
        }
        return bar.view;
    }
    private final Runnable noteGone = () -> job.setText(fixed != null ? "" : ToolsLayout.JOB);
    /** The note the page's last change left, if any, in the job's line for {@link DrawLayout#NOTE_MS}; no animation. */
    private void sayNote() {
        String said = page.note();
        if (said == null) return;
        job.setText(said);
        root.removeCallbacks(noteGone);
        root.postDelayed(noteGone, DrawLayout.NOTE_MS);
        root.announceForAccessibility(said);
    }

    // The name row and the try strip

    /** The name row's words and the try strip's: the tool's name and line, drawn again only when the tool changed. */
    private void renderTool() {
        Brush brush = page.current();
        if (brush.identity().equals(toolShown)) return;
        toolShown = brush.identity();
        boolean edited = page.edited() != null;
        reset.setVisibility(edited ? View.VISIBLE : View.GONE);
        // While Reset to Default shows, the line gives way first (the Nomad turned has room for a short name alone).
        toolName.setMaxWidth(dp(edited ? layout.nameRoom(true) : layout.nameRoom() * 2 / 3f));
        ToolsPage.Card card = ToolsPage.card(brush, layout.nameRoom() / 2f);
        toolName.setText(card.name);
        // Its line, unless it only says the name again (a style's own brush: "Fineliner", "Fineliner").
        boolean line = !card.line.isEmpty() && !card.line.equalsIgnoreCase(card.name);
        toolLine.setText(line ? " · " + card.line : "");
        toolName.setContentDescription(card.name + ": " + BrushVoice.about(brush.parts));
        test.label(card.name);
    }

    // Library

    /** The Library: Search (or its field, or a delete's question) with the pages at the row's right, the chips, and the grid. */
    private View library() {
        LinearLayout box = LabUi.column(this);
        LinearLayout head = LabUi.row(this);
        FrameLayout swap = new FrameLayout(this);
        find = new LabButton(this, R.drawable.ic_ph_magnifying_glass, ToolsLayout.SEARCH, false);
        find.setOnClickListener(v -> openSearch());
        swap.addView(find, new FrameLayout.LayoutParams(-2, -1, Gravity.START | Gravity.CENTER_VERTICAL));
        question = LabUi.row(this);
        swap.addView(question, new FrameLayout.LayoutParams(-1, -1));
        // The search: one line in a chip's rounded edge, then × to close it.
        searchRow = LabUi.row(this);
        searchField = Theme.style(new EditText(this), TypeStyle.CAPTION);
        searchField.setSingleLine(true);
        searchField.setHint(ToolsLayout.SEARCH);
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        // The keyboard's Search key: the finds now, the keyboard down to see the grid.
        searchField.setOnEditorActionListener((v, action, event) -> { root.removeCallbacks(searchNow); searchNow.run(); keyboard(false); return true; });
        searchField.setPadding(dp(layout.chipPad()), 0, dp(layout.chipPad()), 0);
        searchField.setBackground(Theme.rounded(this, Theme.PAPER, Theme.FINE, Theme.INK, ToolsLayout.CHIP / 2f));
        // Kept through a new layout (the screen turned) while open; cleared on closing.
        searchField.setText(page.search());
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { root.removeCallbacks(searchNow); root.postDelayed(searchNow, SEARCH_MS); }
        });
        searchRow.addView(searchField, new LinearLayout.LayoutParams(0, dp(ToolsLayout.CHIP), 1));
        LabButton close = LabButton.icon(this, R.drawable.ic_ph_x, "Close search");
        close.setOnClickListener(v -> { closeSearch(); render(); });
        searchRow.addView(close, LabUi.after(this, ToolsLayout.CHIP, ToolsLayout.CHIP, 6));
        swap.addView(searchRow, new FrameLayout.LayoutParams(-1, -1));
        head.addView(swap, new LinearLayout.LayoutParams(0, -1, 1));
        more = chip(ToolsPage.Chip.MORE.label, false);
        LinearLayout.LayoutParams morePlace = new LinearLayout.LayoutParams(-2, dp(ToolsLayout.CHIP));
        morePlace.gravity = Gravity.CENTER_VERTICAL;
        morePlace.leftMargin = dp(layout.chipGap());
        head.addView(more, morePlace);
        pager = LabUi.row(this);
        head.addView(pager, new LinearLayout.LayoutParams(-2, -1));
        box.addView(head, new LinearLayout.LayoutParams(-1, dp(ToolsLayout.FIND)));
        chips = new LabUi.Wrap(this, dp(layout.chipGap()));
        box.addView(chips, new LinearLayout.LayoutParams(-1, dp(layout.chips())));
        FrameLayout gridBox = new FrameLayout(this);
        grid = LabUi.column(this);
        gridBox.addView(grid, new FrameLayout.LayoutParams(-1, -2));
        // Greyed while the toolbar is fixed: to look through, not to pick from (a kit's sheet still opens, to read).
        if (fixed != null) grid.setAlpha(.4f);
        empty = LabUi.text(this, "", TypeStyle.BODY);
        empty.setTextColor(Theme.GREY_800);
        empty.setGravity(Gravity.CENTER);
        gridBox.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams gridPlace = new LinearLayout.LayoutParams(-1, 0, 1);
        gridPlace.topMargin = dp(layout.gap);
        // The grid takes what is left, so a chip row more than budgeted squeezes it, never the page.
        box.addView(gridBox, gridPlace);
        return box;
    }
    /** The search's row: Search; its field while open; or, while a saved card's delete waits, "Delete Name?", Delete and Keep. Drawn again only when that changes. */
    private void renderFind() {
        String key = confirming != null ? "delete " + confirming.brush.identity() : searchOpen ? "search" : "find";
        if (key.equals(findShown)) return;
        findShown = key;
        find.setVisibility(confirming == null && !searchOpen ? View.VISIBLE : View.GONE);
        more.setVisibility(confirming == null && !searchOpen ? View.VISIBLE : View.GONE);
        searchRow.setVisibility(confirming == null && searchOpen ? View.VISIBLE : View.GONE);
        question.setVisibility(confirming == null ? View.GONE : View.VISIBLE);
        question.removeAllViews();
        if (confirming == null) return;
        TextView asked = LabUi.text(this, "Delete " + confirming.name + "?", TypeStyle.BODY);
        asked.setTypeface(Theme.face(700));
        asked.setSingleLine(true); asked.setEllipsize(TextUtils.TruncateAt.END);
        question.addView(asked, new LinearLayout.LayoutParams(0, -2, 1));
        LabButton delete = new LabButton(this, R.drawable.ic_ph_trash, "Delete", false);
        delete.setMinHeight(0); delete.setMinimumHeight(0);
        delete.setOnClickListener(v -> { ToolsPage.Card card = confirming; confirming = null; page.delete(card); render(); });
        question.addView(delete, LabUi.after(this, -2, ToolsLayout.CHIP, 10));
        LabButton keep = new LabButton(this, 0, "Keep", false);
        keep.setMinHeight(0); keep.setMinimumHeight(0);
        keep.setOnClickListener(v -> { confirming = null; render(); });
        question.addView(keep, LabUi.after(this, -2, ToolsLayout.CHIP, 6));
    }
    /** The filter row's chips and More Brushes, rounded, the chosen one filled (none while searching). Drawn again only when that changes. */
    private void renderChips() {
        String key = page.filter() + " " + page.searching();
        if (key.equals(chipsShown)) return;
        chipsShown = key;
        chips.removeAllViews();
        for (ToolsPage.Chip chip : ToolsLayout.rowChips()) chips.addView(chip(chip(chip.label, false), chip));
        chip(more, ToolsPage.Chip.MORE);
    }
    /** {@code view} as {@code chip}'s: filled while chosen (not while searching), said so, a tap its cards. */
    private TextView chip(TextView view, ToolsPage.Chip chip) {
        boolean on = chip == page.filter() && !page.searching();
        chip(view, on);
        view.setContentDescription(chip.label + (on ? ", chosen" : ""));
        view.setOnClickListener(v -> { test.pad.finishStroke(); confirming = null; closeSearch(); page.filter(chip); render(); });
        return view;
    }
    /** A chip: {@code label} (13 sp) in a rounded fine ink edge, filled while {@code on}. */
    private TextView chip(String label, boolean on) {
        TextView view = LabUi.text(this, label, TypeStyle.CAPTION);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER);
        int pad = dp(layout.chipPad());
        view.setPadding(pad, 0, pad, 0);
        view.setHeight(dp(ToolsLayout.CHIP));
        return chip(view, on);
    }
    /** {@code view} as a chip on or off. */
    private TextView chip(TextView view, boolean on) {
        view.setTextColor(on ? Theme.ON_INK : Theme.INK);
        view.setBackground(Theme.rounded(this, on ? Theme.INK : Theme.PAPER, Theme.FINE, Theme.INK, ToolsLayout.CHIP / 2f));
        return view;
    }
    /** At the search's right: "‹ 2 / 5 ›" (nothing while there is one page), drawn again only when that changes. */
    private void renderPages() {
        int pages = page.pages(), at = page.page();
        String key = pages > 1 ? (at + 1) + " / " + pages : "";
        if (key.equals(pagesShown)) return;
        pagesShown = key;
        pager.removeAllViews();
        if (pages <= 1) return;
        LabButton previous = LabButton.headIcon(this, R.drawable.ic_ph_caret_left, "Previous page");
        previous.setEnabled(at > 0);
        previous.setOnClickListener(v -> { test.pad.finishStroke(); page.turn(-1); render(); });
        pager.addView(previous, new LinearLayout.LayoutParams(dp(ToolsLayout.PAGE_BUTTON), -1));
        TextView count = small(key);
        count.setGravity(Gravity.CENTER);
        count.setPadding(dp(4), 0, dp(4), 0);
        count.setContentDescription("Page " + (at + 1) + " of " + pages);
        pager.addView(count, new LinearLayout.LayoutParams(-2, -1));
        LabButton next = LabButton.headIcon(this, R.drawable.ic_ph_caret_right, "Next page");
        next.setEnabled(at < pages - 1);
        next.setOnClickListener(v -> { test.pad.finishStroke(); page.turn(1); render(); });
        pager.addView(next, new LinearLayout.LayoutParams(dp(ToolsLayout.PAGE_BUTTON), -1));
    }
    /** The page of cards, row by row; or, with none, a short line. */
    private void renderGrid() {
        List<ToolsPage.Card> now = page.cards();
        StringBuilder key = new StringBuilder();
        for (ToolsPage.Card c : now) key.append(c.brush.identity()).append('\n');
        if (!key.toString().equals(gridShown)) {
            gridShown = key.toString();
            shown = now;
            grid.removeAllViews();
            cards = new ToolCard[shown.size()];
            boolean none = shown.isEmpty();
            grid.setVisibility(none ? View.INVISIBLE : View.VISIBLE);
            empty.setVisibility(none ? View.VISIBLE : View.GONE);
            empty.setText(none ? page.empty() : "");
            if (!none) fillGrid();
        }
        for (int c = 0; c < shown.size(); c++) cards[c].setNow(page.now(shown.get(c)));
    }
    private void fillGrid() {
        int columns = layout.columns;
        for (int r = 0; r * columns < shown.size(); r++) {
            LinearLayout row = LabUi.row(this);
            for (int c = 0; c < columns; c++) {
                int index = r * columns + c;
                ToolsPage.Card card = index < shown.size() ? shown.get(index) : null;
                View cell;
                if (card != null) {
                    ToolCard view = new ToolCard(this, samples);
                    showCard(view, layout, card);
                    view.describe(() -> card.kit != null ? card.kit.title() + ": " + card.kit.intro : card.name + ": " + BrushVoice.about(card.brush.parts));
                    // A kit's card opens its sheet, which says why it won't load in a fixed toolbar.
                    if (card.kit != null) view.setOnClickListener(v -> openKit(card.kit));
                    else if (fixed == null) view.setOnClickListener(v -> pick(card.brush));
                    if (fixed == null && page.deletable(card)) view.setOnLongClickListener(v -> { closeSearch(); confirming = card; render(); return true; });
                    cards[index] = view;
                    cell = view;
                } else cell = new View(this);
                row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
            }
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, dp(layout.cardHeight));
            if (r > 0) place.topMargin = dp(ToolsLayout.ROW_GAP);
            grid.addView(row, place);
        }
    }

    // Kits

    /**
     * The kit's sheet over the page (the owner, 2026-10-22: "yes to kits"): its name, its intro, its steps in order
     * ("1 Drop ink", the Basic tool's name beside it), where it goes ("Fills the first 3 slots"), then Cancel and Load
     * Kit; in a fixed toolbar the page's note in Load Kit's place.
     * A tap outside closes it. The test page stops taking the pen while it is open.
     */
    private void openKit(ToolKits.Kit kit) {
        test.pad.finishStroke();
        closeKit();
        test.pad.disconnect();
        FrameLayout scrim = new FrameLayout(this);
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> closeKit());
        LinearLayout box = LabUi.column(this);
        // Over the page, so a menu's look: paper, a fine ink edge, no grey.
        box.setBackground(Theme.menu(this));
        box.setClickable(true);
        int pad = dp(ToolsLayout.SHEET_PAD);
        box.setPadding(pad, pad, pad, pad);
        TextView title = LabUi.text(this, kit.title(), TypeStyle.HEADING);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView intro = LabUi.text(this, kit.intro, TypeStyle.BODY);
        LinearLayout.LayoutParams introPlace = new LinearLayout.LayoutParams(-1, -2);
        introPlace.topMargin = dp(ToolsLayout.WORDS_GAP);
        box.addView(intro, introPlace);
        for (int i = 0; i < kit.steps(); i++) {
            LinearLayout row = LabUi.row(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView job = LabUi.text(this, kit.said(i), TypeStyle.LABEL);
            job.setSingleLine(true);
            row.addView(job, new LinearLayout.LayoutParams(0, -2, 1));
            TextView tool = LabUi.text(this, kit.tool(i), TypeStyle.CAPTION);
            tool.setTextColor(Theme.GREY_800);
            tool.setSingleLine(true);
            row.addView(tool, new LinearLayout.LayoutParams(-2, -2));
            row.setContentDescription(kit.said(i) + ", " + kit.tool(i));
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, dp(ToolsLayout.SHEET_ROW));
            if (i == 0) place.topMargin = dp(layout.gap);
            box.addView(row, place);
        }
        String refused = fixed;
        TextView where = LabUi.text(this, refused != null ? refused : ToolsLayout.kitWhere(kit.size(), page.raises(kit)), TypeStyle.CAPTION);
        where.setTextColor(refused != null ? Theme.INK : Theme.GREY_800);
        LinearLayout.LayoutParams wherePlace = new LinearLayout.LayoutParams(-1, -2);
        wherePlace.topMargin = dp(layout.gap);
        box.addView(where, wherePlace);
        LinearLayout buttons = LabUi.row(this);
        LabUi.spring(buttons);
        LabButton cancel = new LabButton(this, 0, ToolKits.CANCEL, false);
        cancel.setOnClickListener(v -> closeKit());
        buttons.addView(cancel, LabUi.after(this, -2, 40, 0));
        if (refused == null) {
            LabButton load = new LabButton(this, 0, ToolKits.LOAD, true);
            load.setContentDescription("Load the " + kit.name + " kit");
            load.setOnClickListener(v -> { closeKit(); page.loadKit(kit); sayNote(); render(); });
            buttons.addView(load, LabUi.after(this, -2, 40, 10));
        }
        LinearLayout.LayoutParams buttonsPlace = new LinearLayout.LayoutParams(-1, -2);
        buttonsPlace.topMargin = dp(layout.gap);
        box.addView(buttons, buttonsPlace);
        scrim.addView(box, new FrameLayout.LayoutParams(dp(layout.sheetWidth()), -2, Gravity.CENTER));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        sheet = scrim;
    }
    /** The kit sheet closed, if open; the test page takes the pen again. */
    private void closeKit() {
        if (sheet == null) return;
        root.removeView(sheet);
        sheet = null;
        test.pad.post(test.pad::connect);
    }

    // The search

    /** The search's field in Search's place, the keyboard up for it. */
    private void openSearch() {
        test.pad.finishStroke();
        confirming = null;
        searchOpen = true;
        render();
        searchField.requestFocus();
        keyboard(true);
    }
    /** The search closed and cleared, the keyboard down: the chip's cards again. */
    private void closeSearch() {
        if (!searchOpen) return;
        searchOpen = false;
        keyboard(false);
        searchField.setText("");
        root.removeCallbacks(searchNow);
        page.search("");
    }
    private void keyboard(boolean up) {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard == null) return;
        if (up) keyboard.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
        else keyboard.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
    }

    // Leaving

    /** A card is the selected slot's tool now: underlined, its slot and the try strip showing it; the page stays. */
    private void pick(Brush brush) {
        test.pad.finishStroke();
        confirming = null;
        page.put(brush);
        sayNote();
        render();
    }
    /** Back to the drawing with each slot's tool as it stands (null when unchanged) and the slot selected; then Brush Lab on it if {@code lab}. */
    private void leave(boolean lab) { leave(lab, false); }
    /** As {@link #leave(boolean)}; {@code open}: to the library card's screen that frees the toolbar ({@link #SETTINGS}). */
    private void leave(boolean lab, boolean open) {
        test.pad.finishStroke();
        Brush[] results = page.results();
        String[] written = new String[results.length];
        for (int i = 0; i < results.length; i++) written[i] = results[i] == null ? null : results[i].write();
        setResult(RESULT_OK, new Intent().putExtra(SLOT, page.slot()).putExtra(BRUSHES, written).putExtra(LAB, lab).putExtra(START, page.start.write())
                .putExtra(SETTINGS, open ? settings : null).putExtra(BAR, page.barResult()).putExtra(ERASER, page.eraser()).putExtra(TOGETHER, page.together()));
        finish();
        overridePendingTransition(0, 0);
    }
    @Override public void onBackPressed() { if (sheet != null) closeKit(); else leave(false); }
    @Override protected void onResume() { super.onResume(); test.pad.opened(); if (sheet == null) test.pad.post(test.pad::connect); }
    @Override protected void onPause() { test.pad.disconnect(); super.onPause(); }
    @Override protected void onDestroy() { samples.close(); test.pad.close(); super.onDestroy(); }

    private int dp(float value) { return Ui.dp(this, value); }
    private int dpOf(int pixels) { return Ui.dpOf(this, pixels); }
}
