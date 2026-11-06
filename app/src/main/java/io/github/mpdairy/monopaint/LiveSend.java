package io.github.mpdairy.monopaint;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Send (Export Page's row; the owner, 2026-11-05: "a lightweight quick share option that doesn't require server space ...
 * export a page or a notebook to a computer or a phone easily"): one page's copies and its notebook's PDF, straight from
 * the Manta to a browser on its Wi-Fi, through {@link LiveHost}'s {@code GET /get/<token>} (the Draw Together host when
 * it runs on the Wi-Fi, else one started for the Send window alone). The token is 128 random bits, compared in constant
 * time; it opens a plain page listing the files and, under it, each file ({@code /get/<token>/<n>}), made when asked
 * by Export Page's and Export Now's writers ({@link PageExport#bytes}, {@link NotebookExport#pdf}) from the notebook as
 * it was when Send was tapped, and streamed. Nothing else: no listing, no other page or notebook, nothing without the
 * token, nothing after {@link #LIFE_MS} (the window closes then, and closing it ends it sooner). Pure: LiveSendChecks.
 */
final class LiveSend {
    /** The row's word, the window's title, its caption (docs/TEXT-STYLE.md). */
    static final String SEND = "Send", WHERE = "On this Wi-Fi, for ten minutes.";
    static final String PATH = "/get/";
    static final long LIFE_MS = 10 * 60_000L;
    /** One file on the page: its row's words, the file's name and type, and its maker. */
    static final class Item {
        final String label, size, name, type; final Writer writer;
        Item(String label, String size, String name, String type, Writer writer) { this.label = label; this.size = size; this.name = name; this.type = type; this.writer = writer; }
    }
    interface Writer { void write(OutputStream out) throws IOException; }

    final String token, title, page;
    final long until;
    final List<Item> items, notebook;

    LiveSend(String title, String page, List<Item> items, List<Item> notebook, long now) {
        byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        this.title = title; this.page = page; this.items = items; this.notebook = notebook; until = now + LIFE_MS;
    }

    /**
     * Page {@code place}'s (a stack's) copies of {@code book} (a snapshot, released here), named {@code name}, as Export
     * Page offers them (PDF, PNG at each size, Layers), and the notebook's PDF as Export Now writes it. Packs the page
     * drawn on: off the main thread.
     */
    static LiveSend of(DrawingBook.Snapshot book, String name, int place, Supplier<Magnets.Tiles> tiles, long now) throws IOException {
        List<PackedPage> pages;
        PackedPage[] covers;
        try { pages = book.packed(); covers = book.packedCovers(); } finally { book.release(); }
        String title = NotebookPdf.title(name);
        NotebookExport.Covers drawn = new NotebookExport.Covers(name, book.design, book.width, book.height, covers[0], covers[1], book.cover);
        List<Item> items = new ArrayList<>();
        for (String choice : PageExport.CHOICES) {
            ExportSize[] sizes = choice.equals(PageExport.PNG) ? ExportSize.values() : new ExportSize[]{ExportSize.PAGE};
            for (ExportSize size : sizes) items.add(new Item(choice, choice.equals(PageExport.PNG) ? size.words(book.width, book.height) : "",
                    PageExport.name(title, place, choice), LiveHost.type("." + PageExport.extension(choice)),
                    out -> out.write(PageExport.bytes(title, place, choice, size, pages, book.options, book.pageTemplates, book.tracing, tiles.get(), LocalDate.now().toEpochDay()))));
        }
        Item pdf = new Item(PageExport.PDF, "", NotebookPdf.base(title) + ".pdf", LiveHost.type(".pdf"),
                out -> NotebookExport.pdf(out, name, title, pages, tiles.get(), drawn, book.options, book.pageTemplates, book.tracing, LocalDate.now().toEpochDay()));
        return new LiveSend(title, "Page " + (place + 1), items, Collections.singletonList(pdf), now);
    }

    /** The link a browser on the Wi-Fi opens (the QR code's, the pickup's). */
    String link(String address, int port) { return "http://" + address + ":" + port + PATH + token; }

    /** Whether {@code given} is the token, in time: compared in constant time, so its letters can't be found one by one. */
    boolean opens(String given, long now) {
        return now < until && given != null && MessageDigest.isEqual(token.getBytes(StandardCharsets.US_ASCII), given.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * {@code GET path} ({@link #PATH}...) answered on {@code out}: the token's page, or its file {@code n} streamed; false
     * when the path isn't one of them (or the token is wrong or late), nothing written (the host's 404, the same for all).
     */
    boolean serve(String path, OutputStream out, long now) throws IOException {
        if (!path.startsWith(PATH)) return false;
        String rest = path.substring(PATH.length());
        int slash = rest.indexOf('/');
        if (!opens(slash < 0 ? rest : rest.substring(0, slash), now)) return false;
        if (slash < 0) {
            byte[] html = page().getBytes(StandardCharsets.UTF_8);
            out.write((head(200, "text/html; charset=utf-8") + "Content-Length: " + html.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(html); out.flush();
            return true;
        }
        Item item = item(rest.substring(slash + 1));
        if (item == null) return false;
        // One copy made at a time: each holds a page or the notebook's pictures in memory.
        synchronized (this) { stream(item, out); }
        return true;
    }

    private Item item(String n) {
        int i;
        try { i = n.matches("\\d{1,3}") ? Integer.parseInt(n) : -1; } catch (NumberFormatException e) { return null; }
        return i < 0 ? null : i < items.size() ? items.get(i) : i - items.size() < notebook.size() ? notebook.get(i - items.size()) : null;
    }

    /**
     * {@code item} made and sent chunked (HTTP/1.1), its head only once its first bytes are made: one that fails before
     * then is a 500, and one cut off after has no last chunk, so the browser says the download failed rather than keep
     * part of it.
     */
    private static void stream(Item item, OutputStream raw) throws IOException {
        boolean[] begun = {false};
        byte[] crlf = {'\r', '\n'};
        OutputStream chunks = new OutputStream() {
            void begin() throws IOException {
                if (begun[0]) return;
                begun[0] = true;
                raw.write((head(200, item.type) + "Transfer-Encoding: chunked\r\nContent-Disposition: " + attachment(item.name) + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            }
            @Override public void write(int b) throws IOException { write(new byte[]{(byte) b}, 0, 1); }
            @Override public void write(byte[] b, int off, int len) throws IOException {
                begin();
                if (len == 0) return;
                raw.write((Integer.toHexString(len) + "\r\n").getBytes(StandardCharsets.US_ASCII)); raw.write(b, off, len); raw.write(crlf);
            }
        };
        OutputStream out = new BufferedOutputStream(chunks, 65536);
        try {
            item.writer.write(out);
            out.flush(); chunks.write(new byte[0], 0, 0);
            raw.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)); raw.flush();
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            if (begun[0]) return;
            byte[] words = PageExport.FAILED.getBytes(StandardCharsets.US_ASCII);
            raw.write((head(500, "text/plain") + "Content-Length: " + words.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            raw.write(words); raw.flush();
        }
    }

    /** A status line and the headers every answer here has: never cached, sniffed, framed or referred on; no script. */
    private static String head(int code, String type) {
        return "HTTP/1.1 " + code + (code == 200 ? " OK" : " Internal Server Error") + "\r\nContent-Type: " + type + "\r\nConnection: close\r\nCache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'\r\n";
    }

    /** A download's Content-Disposition: its name in plain letters, and whole in UTF-8 (RFC 6266). */
    static String attachment(String name) throws UnsupportedEncodingException {
        return "attachment; filename=\"" + name.replaceAll("[^A-Za-z0-9 ._-]", "_") + "\"; filename*=UTF-8''" + URLEncoder.encode(name, "UTF-8").replace("+", "%20");
    }

    /** The page a browser opens: the notebook's name, the page's files, the notebook's, as superdood.org looks (docs/site/index.html). */
    String page() {
        StringBuilder s = new StringBuilder("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>").append(escape(title)).append("</title><style>"
                + "*{box-sizing:border-box}html{background:#fff;color:#000}"
                + "body{margin:0 auto;max-width:30rem;padding:40px 16px 24px;font:18px/1.45 Georgia,\"Times New Roman\",serif}"
                + "h1{font:700 36px/1.1 ui-rounded,\"Comic Sans MS\",\"Chalkboard SE\",system-ui,sans-serif;margin:0 0 20px;overflow-wrap:anywhere}"
                + "h2{font:700 20px system-ui,sans-serif;margin:24px 0 8px}"
                + "a{display:flex;justify-content:space-between;gap:12px;align-items:baseline;min-height:48px;padding:10px 14px;margin:0 0 8px;border:2px solid #000;border-radius:6px 3px 5px 4px;color:#000;text-decoration:none;font:700 18px system-ui,sans-serif}"
                + "a span{font-weight:400;font-size:15px;color:#444}a:focus-visible{outline:3px dashed #000;outline-offset:3px}"
                + "small{display:block;font:13px/1.4 system-ui,sans-serif;color:#444;margin-top:24px}"
                + "</style></head><body>\n<h1>").append(escape(title)).append("</h1>\n");
        rows(s, page, items, 0);
        rows(s, "Notebook", notebook, items.size());
        return s.append("<small>").append(WHERE).append("</small>\n</body></html>\n").toString();
    }
    private void rows(StringBuilder s, String heading, List<Item> list, int first) {
        s.append("<h2>").append(escape(heading)).append("</h2>\n");
        for (int i = 0; i < list.size(); i++) {
            Item item = list.get(i);
            s.append("<a href=\"").append(PATH).append(token).append('/').append(first + i).append("\" download=\"").append(escape(item.name)).append("\">")
                    .append(escape(item.label)).append(item.size.isEmpty() ? "" : " <span>" + escape(item.size) + "</span>").append("</a>\n");
        }
    }
    static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
}
