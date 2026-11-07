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
 * The Fridge (the owner, 2026-11-06): a page pinned on superdood.org's fridge door, {@code /fridge}, once the owner
 * approves it, and on the tablet's own unlisted page, {@code /fridge/u/<slug>}, at once ({@code FridgeWindow} shows
 * it). The page goes as Export Page's PNG ({@link PageExport}), nothing else; each card sent is kept here, so this
 * tablet can take it down. The server's API (branch {@code fridge-server}): {@code POST /api/send} (the PNG, a name
 * and a caption in the query, {@code X-Fridge-Key} for the own page, {@code shared=1} for the door too), {@code POST
 * /api/delete} ({id, del}) and {@code POST /api/own} ({slug, title}). Pure Java but for the network: FridgeChecks.
 */
final class Fridge {
    private Fridge() { }

    static final String SITE = "https://superdood.org/fridge";
    static final String SEND_API = SITE + "/api/send", DELETE_API = SITE + "/api/delete", OWN_API = SITE + "/api/own", KEY_HEADER = "X-Fridge-Key";
    /** The server's limits: a name's and a caption's characters, the PNG's bytes and pixels; a slug's letters. */
    static final int NAME_MOST = 40, CAPTION_MOST = 120, BYTES_MOST = 3 << 20, WIDTH_MOST = 2000, HEIGHT_MOST = 2700, SLUG_LEAST = 3, SLUG_MOST = 24;
    /** Wider than this, the page goes at Social ({@link ExportSize}), well under {@link #BYTES_MOST}. */
    static final int WIDE_MOST = 1600;

    /** The words (docs/TEXT-STYLE.md). */
    static final String ROW = "Send to the Fridge", MINE = "My Fridge", SHARED = "The Fridge", NAME = "Name", CAPTION = "Caption",
            SEND = "Send", SENDING = "Sending", CANCEL = "Cancel", NOT_NOW = "Not Now", CLOSE = "Close", COPY = "Copy Link", COPIED = "Copied",
            SENT_PAGES = "Sent Pages", SENT_LIST = "Sent to the Fridge", TAKE_DOWN = "Take Down", MAKE = "Make", SENT = "Sent";
    /** The first time's words: where it goes, who sees it, how it comes down, what else goes. */
    static final String ABOUT = "This page goes online, to superdood.org/fridge. On My Fridge at once, for anyone with its link. "
            + "On The Fridge once Elling approves it. Take it down any time, from this tablet. Nothing else is sent.";
    /** My Fridge's name screen: its caption, and the caption while the name can't be a link. */
    static final String OWN_ABOUT = "A short name for its link", OWN_SHORT = "3 to 24 letters or digits";
    /** Notes. */
    static final String FAILED = "Send failed", TOO_LARGE = "Too large", TAKEN_DOWN = "Taken down", DOWN_FAILED = "Take down failed", UNDONE = "This can't be undone.";

    /** The sent window's words: where the page is now. */
    static String sentWords(boolean mine, boolean shared) {
        return mine && shared ? "On My Fridge now. On The Fridge once approved." : mine ? "On My Fridge now." : "Waiting for approval.";
    }
    /** {@code slug}'s page, My Fridge's link. */
    static String ownLink(String slug) { return SITE + "/u/" + slug; }

    // The request.

    /** The size the page goes at: its own when it is no wider than {@link #WIDE_MOST} and fits the server, else Social. */
    static ExportSize size(int width, int height) { return width <= WIDE_MOST && height <= HEIGHT_MOST ? ExportSize.PAGE : ExportSize.SOCIAL; }
    /** Page {@code place}'s PNG as Export Page writes it at {@link #size}; Social when that is over {@link #BYTES_MOST}. */
    static byte[] png(String title, int place, int width, int height, List<PackedPage> pages, NotebookOptions options, List<String> pageTemplates, TracingPaper tracing, Magnets.Tiles tiles) throws IOException {
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
     * The send's address: the name and caption when given (cut to the server's limits, encoded), and {@code shared=1}
     * when it goes to My Fridge ({@code mine}, the key's page) and The Fridge both; The Fridge alone needs nothing.
     */
    static String sendUrl(String name, String caption, boolean mine, boolean shared) {
        StringBuilder q = new StringBuilder();
        query(q, "name", cut(name, NAME_MOST));
        query(q, "caption", cut(caption, CAPTION_MOST));
        if (mine && shared) query(q, "shared", "1");
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
    /** The slug {@code typed} makes: lower case, a dash for each run of anything else, cut to {@link #SLUG_MOST}; null when shorter than {@link #SLUG_LEAST}. */
    static String slug(String typed) {
        String s = (typed == null ? "" : typed).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.length() > SLUG_MOST) s = s.substring(0, SLUG_MOST).replaceAll("-+$", "");
        return s.length() < SLUG_LEAST ? null : s;
    }
    /** My Fridge's request: its slug and its title, the name as typed. */
    static String ownBody(String slug, String title) { return "{\"slug\":" + json(slug) + ",\"title\":" + json(cut(title, NAME_MOST)) + "}"; }
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
    /** A send's {@code answer} as the card kept: page {@code page} of {@code notebook}, sent on {@code day}, to {@code mine}'s page ("" none) and the door when {@code shared}. */
    static Card sent(Answer answer, String mine, boolean shared, String notebook, int page, long day) throws Failed {
        if (!answer.ok()) throw new Failed(said(answer, FAILED), answer.code);
        String id = field(answer.body, "id"), del = field(answer.body, "del"), url = field(answer.body, "url");
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}") || del == null || del.isEmpty()) throw new Failed(FAILED, answer.code);
        // Only a link on superdood.org's fridge is shown; anything else, the card's own.
        if (url == null || !url.startsWith(SITE + "/")) url = SITE + "/" + id;
        return new Card(id, del, url, mine, shared, notebook, page, day);
    }
    /** Whether a take-down's {@code answer} means the card is gone: done, or already gone (404, 410). */
    static boolean takenDown(Answer answer) throws Failed {
        if (answer.ok() || answer.code == 404 || answer.code == 410) return true;
        throw new Failed(said(answer, DOWN_FAILED), answer.code);
    }
    /** My Fridge as made: {slug, key}. */
    static String[] own(Answer answer) throws Failed {
        if (!answer.ok()) throw new Failed(said(answer, FAILED), answer.code);
        String slug = field(answer.body, "slug"), key = field(answer.body, "key");
        if (slug == null || slug(slug) == null || !slug(slug).equals(slug) || key == null || key.isEmpty()) throw new Failed(FAILED, answer.code);
        return new String[]{slug, key};
    }
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

    /** A card sent from this tablet: the server's id, its take-down secret and link, where it went, and what it was. */
    static final class Card {
        final String id, del, url, mine, notebook;
        final boolean shared;
        final int page;
        final long day;
        Card(String id, String del, String url, String mine, boolean shared, String notebook, int page, long day) {
            this.id = id; this.del = del; this.url = url; this.mine = mine == null ? "" : mine; this.shared = shared; this.notebook = notebook == null ? "" : notebook; this.page = page; this.day = day;
        }
        /** The Sent list's line: "Harbour, page 5, Nov 6, 2026". */
        String label() { return (notebook.isEmpty() ? "" : notebook + ", ") + "page " + page + ", " + LibraryCard.day(LocalDate.ofEpochDay(day)); }
        /** Its links, My Fridge's first. */
        List<String> links() {
            List<String> links = new ArrayList<>();
            if (!mine.isEmpty()) links.add(ownLink(mine));
            links.add(url);
            return links;
        }
    }
    /** {@code cards} as kept in the preferences: a line each, its fields URL-encoded, a space between. */
    static String encode(List<Card> cards) {
        StringBuilder s = new StringBuilder();
        for (Card c : cards)
            s.append(encode(c.id)).append(' ').append(encode(c.del)).append(' ').append(encode(c.url)).append(' ').append(c.mine.isEmpty() ? "-" : encode(c.mine))
                    .append(' ').append(c.shared ? 1 : 0).append(' ').append(encode(c.notebook)).append(' ').append(c.page).append(' ').append(c.day).append('\n');
        return s.toString();
    }
    /** The cards {@link #encode} kept, a line that can't be read left out. */
    static List<Card> cards(String kept) {
        List<Card> cards = new ArrayList<>();
        for (String line : (kept == null ? "" : kept).split("\n")) {
            String[] f = line.split(" ");
            if (f.length != 8) continue;
            try {
                cards.add(new Card(decode(f[0]), decode(f[1]), decode(f[2]), f[3].equals("-") ? "" : decode(f[3]), f[4].equals("1"), decode(f[5]), Integer.parseInt(f[6]), Long.parseLong(f[7])));
            } catch (RuntimeException ignored) { }
        }
        return cards;
    }
    private static String decode(String s) {
        try { return URLDecoder.decode(s, "UTF-8"); } catch (UnsupportedEncodingException e) { throw new IllegalStateException(e); }
    }
    /** {@code kept} with {@code card} first. */
    static String added(String kept, Card card) {
        List<Card> cards = cards(kept);
        cards.add(0, card);
        return encode(cards);
    }
    /** {@code kept} without the card {@code id}. */
    static String removed(String kept, String id) {
        List<Card> cards = cards(kept);
        cards.removeIf(c -> c.id.equals(id));
        return encode(cards);
    }

    // The network: off the main thread.

    /** Sends {@code png}; {@code key} (null: none) My Fridge's. */
    static Answer send(String url, byte[] png, String key) throws IOException { return post(url, "image/png", png, key); }
    static Answer delete(Card card) throws IOException { return post(DELETE_API, "application/json", deleteBody(card).getBytes(StandardCharsets.UTF_8), null); }
    static Answer own(String slug, String title) throws IOException { return post(OWN_API, "application/json", ownBody(slug, title).getBytes(StandardCharsets.UTF_8), null); }
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
