package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The Fridge door (the owner, 2026-11-15: "lets figure out a cool place in the app to put the fridge.. optional.. check
 * the fridge?", then "a proper fridge-like presentation more like the site would be best"): the shelf's door opens
 * superdood.org/fridge/ itself, the wall as the site shows it, full screen in a web view. Off until Turn On (it uses the
 * internet). Only the wall's own pages open ({@link Fridge#onWall}); Report is the site's, but on a card this tablet
 * sent, Delete in its place ({@link #deleteButton}).
 * shortcut: the window is upright whatever the app's turn; turn it with the app if sideways use asks for it.
 */
final class FridgeDoor {
    /** Its words (docs/TEXT-STYLE.md): the tile, the Turn On screen, the note. */
    static final String TITLE = "The Fridge", TILE_ABOUT = "Everyone's pages",
            ABOUT = "See what everyone has put on the Fridge.", USES = "This uses the internet.",
            OPENS = "It opens superdood.org/fridge.", UNREACHABLE = "Can't reach the Fridge";
    /**
     * The door's own style for the site's pages (the wall's CSP allows inline styles): the links it has no use for hidden
     * ("Draw it in Superdood" and the header's link home; a card's "‹ The Fridge" stays); a card's page with the card as
     * tall as the screen leaves room for (the owner, 2026-11-20: "make the view a little bigger since we have the
     * room"), less space around it; Delete styled as the site's buttons.
     */
    static final String STYLE = "a.more[href=\"/install.html\"],header>a[href=\"/\"]{display:none!important}"
            + "header{margin-bottom:12px!important;padding-bottom:10px!important}footer{margin-top:20px!important}"
            + ".one{max-width:100%!important;width:fit-content}.one img{width:auto!important;max-width:100%;max-height:calc(100vh - 300px)}"
            + ".turn{max-width:40rem!important;margin-top:16px!important}button,a.delete{margin-top:16px!important}"
            + "a.delete{display:block;margin:16px auto 0;width:max-content;min-height:44px;padding:10px 18px;border:1px solid #000;"
            + "background:#fff;color:#000;font:700 15px \"Superdood Duo\",ui-monospace,monospace;text-decoration:none}";
    static final String HIDE = "(function(){var s=document.createElement('style');s.textContent=" + org.json.JSONObject.quote(STYLE)
            + ";document.head.appendChild(s);})()";
    /**
     * On a card this tablet sent, the site's Report becomes Delete (the owner, 2026-11-20: "move the take down to where
     * the 'report' button is ... change it to Delete"): a link to the card's {@link Fridge#deleting} address, which the
     * door takes ({@code shouldOverrideUrlLoading}) and never loads.
     */
    static String deleteButton(Fridge.Card card) {
        return "(function(){var f=document.querySelector('form[action=\"/fridge/api/report\"]');if(!f)return;"
                + "var a=document.createElement('a');a.className='delete';a.href=" + org.json.JSONObject.quote(Fridge.deleteUrl(card))
                + ";a.textContent=" + org.json.JSONObject.quote(Fridge.DELETE) + ";f.replaceWith(a);})()";
    }
    private final PaintActivity app;

    FridgeDoor(PaintActivity app) { this.app = app; }

    /** The door tapped: its Turn On screen the first time, then the wall. */
    void open() {
        if (app.prefs.fridgeDoorOn()) { show(); return; }
        app.promptsWindow.turnOn(TITLE, ABOUT, USES, new String[]{OPENS}, () -> { app.prefs.setFridgeDoorOn(true); show(); });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void show() {
        Dialog window = new Dialog(app, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        LinearLayout column = LabUi.column(app);
        column.setBackgroundColor(Theme.PAPER);
        LabUi.PageBar bar = new LabUi.PageBar(app, 0, Fridge.CLOSE, TITLE, "");
        bar.line.setVisibility(View.GONE);
        bar.back.setOnClickListener(v -> window.dismiss());
        column.addView(bar.view, new LinearLayout.LayoutParams(-1, app.dp(NotebookLayout.BAR)));
        WebView web = new WebView(app);
        boolean[] fresh = {false};
        WebSettings s = web.getSettings();
        // Only for HIDE: the wall has no scripts of its own (its CSP allows none).
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(false); s.setJavaScriptCanOpenWindowsAutomatically(false); s.setGeolocationEnabled(false);
        // The Supernote's 0.85 font scale shrinks a web view's text (the NYT Games app's lesson): never under 100%.
        if (s.getTextZoom() < 100) s.setTextZoom(100);
        TextView note = LabUi.text(app, UNREACHABLE, TypeStyle.BODY);
        note.setGravity(Gravity.CENTER);
        note.setVisibility(View.GONE);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String to = request.getUrl().toString();
                Fridge.Card deleting = Fridge.deleting(app.prefs.fridgeCards(), to);
                if (deleting != null) { app.fridge.confirmDown(deleting, () -> { fresh[0] = true; view.loadUrl(Fridge.WALL); }); return true; }
                return !Fridge.onWall(to);
            }
            @Override public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(HIDE, null);
                // After a delete: the wall again, the card's page gone from Back.
                if (fresh[0]) { fresh[0] = false; view.clearHistory(); }
                Fridge.Card mine = Fridge.cardAt(app.prefs.fridgeCards(), url);
                if (mine != null) view.evaluateJavascript(deleteButton(mine), null);
                Ui.flashWindow(column.getRootView(), "fridge page", () -> { }, SystemSettings.flashes(false, app.prefs.autoRefresh()), true);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) { view.setVisibility(View.GONE); note.setVisibility(View.VISIBLE); }
            }
        });
        LinearLayout.LayoutParams fill = new LinearLayout.LayoutParams(-1, 0); fill.weight = 1;
        column.addView(web, fill);
        column.addView(note, new LinearLayout.LayoutParams(fill));
        // Back: a page back in the wall (a card to the wall), then closed.
        window.setOnKeyListener((d, key, event) -> {
            if (key != KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) { if (web.canGoBack() && web.getVisibility() == View.VISIBLE) web.goBack(); else window.dismiss(); }
            return true;
        });
        window.setOnDismissListener(d -> web.destroy());
        window.setContentView(column);
        window.show();
        web.loadUrl(Fridge.WALL);
    }
}
