package io.github.mpdairy.monopaint;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;

/**
 * Send ({@link LiveSend}) on the JVM, through a {@link LiveHost} as the Manta runs it: the page and each file only with
 * the token, in time; each file the bytes Export Page's and Export Now's writers give, under its name; nothing else
 * (no listing, a wrong, short or late token, another number, a path around it, the host's other routes); a maker that
 * fails before its first bytes a 500, one cut off after no last chunk; the page's words escaped.
 */
public final class LiveSendChecks {
    public static void main(String[] args) throws Exception {
        File library = Files.createTempDirectory("live-send").toFile();
        DrawingFiles files = new DrawingFiles(library);
        String name = "2026-11-05_Harbour Sketches";
        LiveHostChecks.notebook(files.drawing(name), name, 3, 11);
        DrawingBook book = BookCodec.read(new FileInputStream(files.drawing(name)));
        try { BlankPages.opened(book); } catch (IOException kept) { }
        long now = System.currentTimeMillis(), today = LocalDate.now().toEpochDay();
        LiveSend send = LiveSend.of(book.snapshot(), name, 1, () -> null, now);
        check(send.token.matches("[A-Za-z0-9_-]{22}") && !send.token.equals(LiveSend.of(book.snapshot(), name, 1, () -> null, now).token), "a token: 22 base64url letters (128 bits), new each time");
        check(send.until == now + LiveSend.LIFE_MS && LiveSend.LIFE_MS == 10 * 60_000L, "ten minutes");
        check(send.link("192.168.1.5", 8080).equals("http://192.168.1.5:8080/get/" + send.token)
                && send.link("192.168.1.5", 8080).matches("http://192\\.168\\.\\d+\\.\\d+:\\d+/get/[A-Za-z0-9_-]{22}"), "the link: as superdood.org's pickup takes it (server.py TO_RE)");

        LiveHost host = new LiveHost(0, LiveHost.newKey(), Collections.emptySet(), n -> null);
        try {
            String at = "http://127.0.0.1:" + host.port, get = at + LiveSend.PATH + send.token;
            check(LiveHostChecks.get(get).startsWith("404"), "nothing before Send's window opens");
            host.send = send;
            HttpURLConnection page = open(get);
            String html = LiveHostChecks.read(page);
            check(page.getResponseCode() == 200 && page.getContentType().startsWith("text/html") && "no-store".equals(page.getHeaderField("Cache-Control"))
                    && "no-referrer".equals(page.getHeaderField("Referrer-Policy")) && page.getHeaderField("Content-Security-Policy").startsWith("default-src 'none'"), "the page: HTML, never cached, no referrer, no script");
            check(html.contains("<h1>Harbour Sketches</h1>") && html.contains("<h2>Page 2</h2>") && html.contains("<h2>Notebook</h2>") && html.contains(LiveSend.WHERE)
                    && html.contains("PNG <span>Social, 600 × 800</span>") && html.contains("PNG <span>High Res, 1200 × 1600, sharp</span>") && !html.contains("<script"), "the page: the notebook's title, the page's files with the PNG's sizes, the notebook's PDF");
            String[] made = new String[6];
            byte[][] want = new byte[6][];
            int n = 0;
            for (String choice : PageExport.CHOICES) for (ExportSize size : choice.equals(PageExport.PNG) ? ExportSize.values() : new ExportSize[]{ExportSize.PAGE}) {
                DrawingBook.Snapshot s = book.snapshot();
                List<PackedPage> pages = s.packed(); s.release();
                want[n] = PageExport.bytes(NotebookPdf.title(name), 1, choice, size, pages, s.options, s.pageTemplates, s.tracing, null, today);
                made[n++] = PageExport.name(NotebookPdf.title(name), 1, choice);
            }
            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            NotebookExport.pdf(pdf, book, name, null, today);
            want[n] = pdf.toByteArray(); made[n] = NotebookPdf.base(NotebookPdf.title(name)) + ".pdf";
            check(html.split("<a href=", -1).length == 7, "six files: PDF, three PNGs, Layers, the notebook's PDF");
            for (int i = 0; i < 6; i++) {
                HttpURLConnection c = open(get + "/" + i);
                byte[] got = all(c);
                check(c.getResponseCode() == 200 && Arrays.equals(got, want[i]) && c.getHeaderField("Content-Disposition").contains("filename=\"" + made[i] + "\"")
                        && "chunked".equals(c.getHeaderField("Transfer-Encoding")) && html.contains("href=\"/get/" + send.token + "/" + i + "\""), "file " + i + ": its writer's bytes, under its name, streamed");
            }
            String wrong = send.token.substring(0, 21) + (send.token.charAt(21) == 'A' ? 'B' : 'A');
            for (String p : new String[]{"/get/", "/get/" + wrong, "/get/" + wrong + "/0", "/get/" + send.token.substring(1), "/get/" + send.token + "x",
                    "/get/" + send.token + "/6", "/get/" + send.token + "/-1", "/get/" + send.token + "/0x", "/get/" + send.token + "/", "/get/" + send.token + "/../0",
                    "/get/" + send.token + "/0/0", "/get/" + send.token + "/0000"})
                check(LiveHostChecks.get(at + p).startsWith("404"), "nothing there: " + p);
            for (String p : new String[]{"/", "/live", "/books", "/listen", "/live.html"}) check(LiveHostChecks.get(at + p).startsWith("403"), "the host's other routes still need its key: " + p);
            host.send = null;
            check(LiveHostChecks.get(get).startsWith("404") && LiveHostChecks.get(get + "/0").startsWith("404"), "nothing once the window closes");
        } finally { host.stop(); }

        // Late: nothing at its ten minutes, the same answer as a wrong token.
        check(send.opens(send.token, now) && !send.opens(send.token, send.until) && !send.opens(null, now) && !send.opens("", now), "the token only, in time");
        check(!send.serve(LiveSend.PATH + send.token, new ByteArrayOutputStream(), send.until), "late: nothing written");

        // A maker that fails: before its first bytes a 500 with Export's note; after them no last chunk.
        LiveSend failing = new LiveSend("<b>\"&'", "Page 1", Arrays.asList(
                new LiveSend.Item("PDF", "", "x.pdf", "application/pdf", out -> { throw new IOException("no"); }),
                new LiveSend.Item("PNG", "", "Hä\"r.png", "image/png", out -> { out.write(new byte[200_000]); throw new IOException("cut"); })), Collections.emptyList(), now);
        ByteArrayOutputStream before = new ByteArrayOutputStream(), after = new ByteArrayOutputStream();
        check(failing.serve(LiveSend.PATH + failing.token + "/0", before, now) && before.toString("US-ASCII").startsWith("HTTP/1.1 500") && before.toString("US-ASCII").endsWith(PageExport.FAILED), "failed before its bytes: a 500");
        check(failing.serve(LiveSend.PATH + failing.token + "/1", after, now) && after.toString("US-ASCII").startsWith("HTTP/1.1 200") && !after.toString("US-ASCII").endsWith("0\r\n\r\n"), "cut off after: no last chunk, so the browser doesn't keep part of it");
        check(after.toString("UTF-8").contains("filename=\"H__r.png\"; filename*=UTF-8''H%C3%A4%22r.png"), "a file's name: plain letters, and whole in UTF-8");
        String escaped = failing.page();
        check(escaped.contains("<h1>&lt;b&gt;&quot;&amp;&#39;</h1>") && !escaped.contains("<b>"), "the page's words escaped");
        System.out.println("PASS: Send: the page and each file only with its 128-bit token, in its ten minutes, compared in constant time; each file its writer's bytes under its name, streamed chunked; no listing, no wrong or late token, no other number or path, the host's other routes still keyed; a maker's failure a 500 before its bytes, no last chunk after; names escaped");
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    static HttpURLConnection open(String url) throws IOException { return (HttpURLConnection) new URL(url).openConnection(); }
    static byte[] all(HttpURLConnection c) throws IOException {
        try (InputStream in = c.getInputStream()) { return in.readAllBytes(); } finally { c.disconnect(); }
    }
}
