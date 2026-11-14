package io.github.mpdairy.monopaint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Fridge (the owner, 2026-11-06; the Prompts, 2026-11-13): a page sent to superdood.org/fridge, a Prompts page's
 * under its #inktober52 word ({@code FridgeWindow} shows it). The page goes as Export Page's PNG ({@link PageExport}), nothing
 * else; each card sent is kept here, so this tablet can take it down. The server's API
 * (superdood.org's server (server.py, not in this repo)): {@code POST /api/confirm} ({email}; a new tablet's key in the answer),
 * {@code GET /api/confirmed} ({confirmed, name}), {@code POST /api/send?word=} (the PNG, a name and a caption in the query;
 * {@code ?replace=<id>}, a card sent before updated in place), {@code POST /api/name} ({name}: the tablet's one name, the
 * owner's "username", 2026-11-13), {@code POST /api/leave}, each with the tablet's {@code X-Fridge-Key}; {@code POST
 * /api/delete} ({id, del}); {@code GET /api/words}.
 * Personal fridges ("My Fridge", {@code /api/own}) are gone. Pure Java but for the network: FridgeChecks.
 */
final class Fridge {
    private Fridge() { }

    static final String SITE = "https://superdood.org/fridge";
    static final String SEND_API = SITE + "/api/send", DELETE_API = SITE + "/api/delete", KEY_HEADER = "X-Fridge-Key";
    static final String CONFIRM_API = SITE + "/api/confirm", CONFIRMED_API = SITE + "/api/confirmed", LEAVE_API = SITE + "/api/leave", NAME_API = SITE + "/api/name";
    /** The server's limits: a name's and a caption's characters, the PNG's bytes and pixels. */
    static final int NAME_MOST = 40, CAPTION_MOST = 120, BYTES_MOST = 3 << 20, WIDTH_MOST = 2000, HEIGHT_MOST = 2700;
    /** Wider than this, the page goes at Social ({@link ExportSize}), well under {@link #BYTES_MOST}. */
    static final int WIDE_MOST = 1600;

    /** The words (docs/TEXT-STYLE.md). */
    static final String ROW = "Send to the Fridge", NAME = "Name", CAPTION = "Caption (optional)", EMAIL = "Email",
            SEND_LINK = "Send Link", SEND = "Send", SENDING = "Sending", CANCEL = "Cancel", NOT_NOW = "Not Now", CLOSE = "Close", COPY = "Copy Link", COPIED = "Copied",
            SENT_PAGES = "Sent Pages", SENT_LIST = "Sent to the Fridge", TAKE_DOWN = "Take Down", SENT = "Sent";
    /** Notes. */
    static final String FAILED = "Send failed", TOO_LARGE = "Too large", TAKEN_DOWN = "Taken down", DOWN_FAILED = "Take down failed", UNDONE = "This can't be undone.";
    /** The email step's words: the owner's (2026-11-13), the terms, the waiting line, the check before asking. */
    static final String EMAIL_FIRST = "We need to confirm your email address the first time you post. Superdood won't share it with anyone.",
            TERMS = "Privacy: superdood.org/privacy.html · Terms: superdood.org/terms.html",
            WAITING_LINK = "Waiting for you to tap the link in your email.", NOT_EMAIL = "That isn't an email address.";
    /** Sent: shown at once, or held for the owner (flagged words, a held tablet). The server's words for an unconfirmed tablet. */
    static final String ON_FRIDGE = "On the Fridge.", WAITING = "Waiting for a look.", CONFIRM_FIRST = "Confirm your email first.";
    /** A page sent again, its card updated (the owner, 2026-11-13: "have whatever image it is update instead of submitting a new thing"); a name left empty, the server's words. */
    static final String UPDATED = "Updated on the Fridge.", ADD_NAME = "Add a name.";
    /** Stop Taking Part (the Prompts notebook's Notebook Settings, and Delete's). */
    static final String STOP = "Stop Taking Part", STOP_NOTE = "Deletes your email and your pages on the Fridge. Your notebook stays.",
            STOP_ASK = "Stop taking part?", STOP_ABOUT = "Your email and your pages on the Fridge will be deleted. Your notebook stays on this tablet.",
            STOPPED = "You've stopped taking part.", UNREACHABLE = "Couldn't reach the Fridge. Try again with Wi-Fi.";

    // The request.

    /** The size the page goes at: its own when it is no wider than {@link #WIDE_MOST} and fits the server, else Social. */
    static ExportSize size(int width, int height) { return width <= WIDE_MOST && height <= HEIGHT_MOST ? ExportSize.PAGE : ExportSize.SOCIAL; }
    /**
     * Page {@code place}'s PNG as Export Page writes it at {@link #size}: its template in when {@code template} (a word's
     * post, its band), else on plain paper, as before the Template tick (2026-11-10); Social when that is over {@link #BYTES_MOST}.
     */
    static byte[] png(String title, int place, int width, int height, List<PackedPage> pages, NotebookOptions options, List<String> pageTemplates, TracingPaper tracing, Magnets.Tiles tiles, boolean template) throws IOException {
        if (!template) { options = options.withTemplate(NotebookTemplate.BLANK); pageTemplates = null; }
        for (ExportSize size = size(width, height); ; size = ExportSize.SOCIAL) {
            byte[] png = PageExport.bytes(title, place, PageExport.PNG, size, pages, options, pageTemplates, tracing, tiles, LocalDate.now().toEpochDay());
            if (png.length <= BYTES_MOST) return png;
            if (size == ExportSize.SOCIAL) throw new Failed(TOO_LARGE, 0);
        }
    }
    /** {@code s} as one line, trimmed, cut to {@code most} characters. */
    static String cut(String s, int most) {
        s = s == null ? "" : s.trim().replaceAll("\\s+", " ");
        return s.codePointCount(0, s.length()) <= most ? s : s.substring(0, s.offsetByCodePoints(0, most)).trim();
    }
    /**
     * The send's address: its word (the word's slug; null or "", a post with no word, no {@code word=}), or {@code replace}
     * (a card's id, null for none: that card updated, keeping its word), and the name and caption when given (cut to the server's limits, encoded).
     */
    static String sendUrl(String word, String name, String caption, String replace) {
        StringBuilder q = new StringBuilder();
        if (replace != null) query(q, "replace", replace); else query(q, "word", word == null ? "" : word);
        query(q, "name", cut(name, NAME_MOST));
        query(q, "caption", cut(caption, CAPTION_MOST));
        return SEND_API + (q.length() == 0 ? "" : "?" + q);
    }
    private static void query(StringBuilder q, String key, String value) {
        if (value.isEmpty()) return;
        q.append(q.length() == 0 ? "" : "&").append(key).append('=').append(encode(value));
    }
    /** {@code s} URL-encoded, a space as %20. */
    static String encode(String s) {
        try { return URLEncoder.encode(s, "UTF-8").replace("+", "%20"); } catch (UnsupportedEncodingException e) { throw new IllegalStateException(e); }
    }
    /** A rough check before asking the server (it checks again). */
    static boolean emailish(String s) { return s != null && s.trim().matches("[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s.]{2,}"); }
    /** Confirm's request: the email, trimmed. */
    static String confirmBody(String email) { return "{\"email\":" + json(email.trim()) + "}"; }
    /** Fridge Name's request: the name, as one line. */
    static String nameBody(String name) { return "{\"name\":" + json(cut(name, NAME_MOST)) + "}"; }
    /** A take-down's request. */
    static String deleteBody(Card card) { return "{\"id\":" + json(card.id) + ",\"del\":" + json(card.del) + "}"; }
    /** {@code s} as a JSON string. */
    static String json(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 0x20) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append('"').toString();
    }

    // The answer.

    /** An HTTP answer: its status and body. */
    static final class Answer {
        final int code; final String body;
        Answer(int code, String body) { this.code = code; this.body = body == null ? "" : body; }
        boolean ok() { return code / 100 == 2; }
    }
    /** A send, take-down or name the server refused, or one that never came back: the note it shows, and the status (0: none). */
    static final class Failed extends IOException {
        final int code;
        Failed(String note, int code) { super(note); this.code = code; }
    }
    /** The note for a refused {@code answer}: the server's own words ({error}) when short, else {@code fallback}. */
    static String said(Answer answer, String fallback) {
        String error = field(answer.body, "error");
        return error == null || error.trim().isEmpty() || error.length() > 60 ? fallback : error.trim();
    }
    /** A send's {@code answer} as the card kept: word {@code word}'s page {@code page} of {@code notebook}, sent on {@code day}. */
    static Card sent(Answer answer, String word, String notebook, int page, long day) throws Failed { return sent(answer, word, notebook, page, day, null); }
    /** The same, {@code old} the card it replaced (null: none): a replace's answer has no {@code del}, so old's is kept. */
    static Card sent(Answer answer, String word, String notebook, int page, long day, Card old) throws Failed {
        if (!answer.ok()) throw new Failed(answer.code == 409 ? Prompts.CLOSED : said(answer, FAILED), answer.code);
        String id = field(answer.body, "id"), del = field(answer.body, "del"), url = field(answer.body, "url");
        if (del == null && old != null && old.id.equals(id)) del = old.del;
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}") || del == null || del.isEmpty()) throw new Failed(FAILED, answer.code);
        // Only a link on superdood.org's fridge is shown; anything else, the card's own.
        if (url == null || !url.startsWith(SITE + "/")) url = SITE + "/" + id;
        return new Card(id, del, url, notebook, page, day, word);
    }
    /** What the sent window says: {@link #WAITING} when the server holds the page for a look, else {@link #UPDATED} for a card {@code replaced}, {@link #ON_FRIDGE} for a new one. */
    static String sentNote(Answer answer, boolean replaced) { return "pending".equals(field(answer.body, "status")) ? WAITING : replaced ? UPDATED : ON_FRIDGE; }
    /** Whether a replace's {@code answer} means its card is gone from the Fridge (404): forgotten, and the page sent as new. */
    static boolean replacedGone(Answer answer) { return answer.code == 404; }
    /** Whether api/confirmed's {@code answer} says this tablet's email is confirmed. */
    static boolean confirmed(Answer answer) { return answer.ok() && isTrue(answer.body, "confirmed"); }
    /** The name api/confirmed's {@code answer} gives this tablet ("" if none). */
    static String nameOf(Answer answer) { String name = answer.ok() ? field(answer.body, "name") : null; return name == null ? "" : cut(name, NAME_MOST); }
    /** The name kept after Fridge Name's {@code answer}: the server's, else {@code sent}; refused, its words, else {@link #UNREACHABLE}. */
    static String named(Answer answer, String sent) throws Failed {
        if (!answer.ok()) throw new Failed(said(answer, UNREACHABLE), answer.code);
        String name = field(answer.body, "name");
        return name == null || name.trim().isEmpty() ? cut(sent, NAME_MOST) : name;
    }
    /** Whether a send was refused for an email not confirmed yet (back to the email step). */
    static boolean unconfirmed(Answer answer) { return answer.code == 403 && CONFIRM_FIRST.equals(field(answer.body, "error")); }
    /** The tablet's key after confirm's {@code answer}: the new one it gives, else {@code key}, the one asked with; refused, its words. */
    static String tablet(Answer answer, String key) throws Failed {
        if (!answer.ok()) throw new Failed(said(answer, FAILED), answer.code);
        String given = field(answer.body, "key");
        if (given != null && given.matches("[A-Za-z0-9_-]{16,64}")) return given;
        if (key == null || key.isEmpty()) throw new Failed(FAILED, answer.code);
        return key;
    }
    /** Whether Stop Taking Part's {@code answer} means this tablet is gone: done, or already gone (403). */
    static boolean left(Answer answer) throws Failed {
        if (answer.ok() || answer.code == 403) return true;
        throw new Failed(said(answer, UNREACHABLE), answer.code);
    }
    /** Whether a take-down's {@code answer} means the card is gone: done, or already gone (404, 410). */
    static boolean takenDown(Answer answer) throws Failed {
        if (answer.ok() || answer.code == 404 || answer.code == 410) return true;
        throw new Failed(said(answer, DOWN_FAILED), answer.code);
    }
    /** A JSON boolean field is true. */
    static boolean isTrue(String json, String name) { return json != null && json.matches("(?s).*\"" + Pattern.quote(name) + "\"\\s*:\\s*true.*"); }
    private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    /** A JSON object's string field {@code name} (a flat object, as the server's), or null. */
    static String field(String json, String name) {
        Matcher m = STRING.matcher(json == null ? "" : json);
        while (m.find()) if (unescape(m.group(1)).equals(name)) return unescape(m.group(2));
        return null;
    }
    private static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) { out.append(c); continue; }
            char e = s.charAt(++i);
            switch (e) {
                case 'n': out.append('\n'); break;
                case 't': out.append('\t'); break;
                case 'r': out.append('\r'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'u':
                    if (i + 4 < s.length()) { try { out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; break; } catch (NumberFormatException ignored) { } }
                    out.append(e); break;
                default: out.append(e);
            }
        }
        return out.toString();
    }

    // The cards kept.

    /** A card sent from this tablet: the server's id, its take-down secret and link, and what it was (its word: "" before the Prompts). */
    static final class Card {
        final String id, del, url, notebook, word;
        final int page;
        final long day;
        Card(String id, String del, String url, String notebook, int page, long day) { this(id, del, url, notebook, page, day, ""); }
        Card(String id, String del, String url, String notebook, int page, long day, String word) {
            this.id = id; this.del = del; this.url = url; this.notebook = notebook == null ? "" : notebook; this.page = page; this.day = day; this.word = word == null ? "" : word;
        }
        /** The Sent list's line: "Harbour, page 5, Nov 6, 2026". */
        String label() { return (notebook.isEmpty() ? "" : notebook + ", ") + "page " + page + ", " + LibraryCard.day(LocalDate.ofEpochDay(day)); }
    }
    /** {@code cards} as kept in the preferences: a line each, its fields URL-encoded, a space between. */
    static String encode(List<Card> cards) {
        StringBuilder s = new StringBuilder();
        for (Card c : cards)
            s.append(encode(c.id)).append(' ').append(encode(c.del)).append(' ').append(encode(c.url))
                    .append(' ').append(encode(c.notebook)).append(' ').append(c.page).append(' ').append(c.day).append(' ').append(encode(c.word)).append('\n');
        return s.toString();
    }
    /**
     * The cards {@link #encode} kept (7 fields, the word last), a line that can't be read left out; lines from before the
     * Prompts (6 fields, no word) and 1.2.5's (8: My Fridge's slug and the door's tick after the link) read without those.
     */
    static List<Card> cards(String kept) {
        List<Card> cards = new ArrayList<>();
        for (String line : (kept == null ? "" : kept).split("\n")) {
            String[] f = line.split(" ", -1);
            if (f.length < 6 || f.length > 8) continue;
            int n = f.length == 8 ? 5 : 3;
            try {
                cards.add(new Card(decode(f[0]), decode(f[1]), decode(f[2]), decode(f[n]), Integer.parseInt(f[n + 1]), Long.parseLong(f[n + 2]), f.length == 7 ? decode(f[6]) : ""));
            } catch (RuntimeException ignored) { }
        }
        return cards;
    }
    private static String decode(String s) {
        try { return URLDecoder.decode(s, "UTF-8"); } catch (UnsupportedEncodingException e) { throw new IllegalStateException(e); }
    }
    /** {@code kept} with {@code card}: in its own card's place when it was an update (same id, as the server keeps it), else first, and without a card sent before for its word (the server replaced it). */
    static String added(String kept, Card card) {
        List<Card> cards = cards(kept);
        for (int i = 0; i < cards.size(); i++) if (cards.get(i).id.equals(card.id)) { cards.set(i, card); return encode(cards); }
        if (!card.word.isEmpty()) cards.removeIf(c -> c.word.equals(card.word));
        cards.add(0, card);
        return encode(cards);
    }
    /**
     * The card a send from page {@code page} of {@code notebook} updates (the owner, 2026-11-13: "when submitting the same
     * page again, let's have whatever image it is update"), or null: a word's post ({@code word} its slug), its word's card;
     * any other, the card with no word sent from the same notebook and page.
     */
    static Card replaces(String kept, String word, String notebook, int page) {
        String w = word == null ? "" : word, book = notebook == null ? "" : notebook;
        for (Card c : cards(kept))
            if (w.isEmpty() ? c.word.isEmpty() && c.notebook.equals(book) && c.page == page : c.word.equals(w)) return c;
        return null;
    }
    /** {@code kept} without the card {@code id}. */
    static String removed(String kept, String id) {
        List<Card> cards = cards(kept);
        cards.removeIf(c -> c.id.equals(id));
        return encode(cards);
    }

    // The network: off the main thread.

    /** Sends {@code png} with this tablet's {@code key}. */
    static Answer send(String url, byte[] png, String key) throws IOException { return post(url, "image/png", png, key); }
    static Answer delete(Card card) throws IOException { return post(DELETE_API, "application/json", deleteBody(card).getBytes(StandardCharsets.UTF_8), null); }
    /** Asks for the link that confirms {@code email}; with this tablet's {@code key}, or none (a new tablet: its key in the answer, 202 {key}). */
    static Answer confirm(String email, String key) throws IOException { return post(CONFIRM_API, "application/json", confirmBody(email).getBytes(StandardCharsets.UTF_8), key); }
    /** Whether this tablet's email is confirmed yet, and its name ({@link #confirmed(Answer)}, {@link #nameOf}). */
    static Answer confirmed(String key) throws IOException { return get(CONFIRMED_API, key); }
    /** Fridge Name: this tablet's name, on all its pages. */
    static Answer rename(String name, String key) throws IOException { return post(NAME_API, "application/json", nameBody(name).getBytes(StandardCharsets.UTF_8), key); }
    /** Stop Taking Part: this tablet, its email and its pages, gone from the site. */
    static Answer leave(String key) throws IOException { return post(LEAVE_API, "application/json", "{}".getBytes(StandardCharsets.UTF_8), key); }
    /** The open words, the current first ({@link Prompts#words} reads them). */
    static String words() throws IOException { Answer a = get(Prompts.WORDS_API, null); if (!a.ok()) throw new Failed(FAILED, a.code); return a.body; }
    /** A GET, as {@link #post} makes a POST. */
    private static Answer get(String url, String key) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(10_000); c.setReadTimeout(30_000);
            if (key != null) c.setRequestProperty(KEY_HEADER, key);
            int code = c.getResponseCode();
            return new Answer(code, read(code < 400 ? c.getInputStream() : c.getErrorStream()));
        } finally { c.disconnect(); }
    }
    private static Answer post(String url, String type, byte[] body, String key) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(10_000); c.setReadTimeout(30_000); c.setDoOutput(true); c.setFixedLengthStreamingMode(body.length);
            c.setRequestProperty("Content-Type", type);
            if (key != null) c.setRequestProperty(KEY_HEADER, key);
            try (OutputStream out = c.getOutputStream()) { out.write(body); }
            int code = c.getResponseCode();
            return new Answer(code, read(code < 400 ? c.getInputStream() : c.getErrorStream()));
        } finally { c.disconnect(); }
    }
    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream answer = new ByteArrayOutputStream();
        try (InputStream i = in) { byte[] b = new byte[1024]; for (int n; (n = i.read(b)) > 0 && answer.size() < 16384; ) answer.write(b, 0, n); }
        return answer.toString("UTF-8");
    }
}
