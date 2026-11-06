package io.github.mpdairy.monopaint;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Share on Wi-Fi's relay ({@link LiveHost}) on the JVM: the web app's files without the key, nothing else without it;
 * changes numbered and passed to every listener, history to one that asks; the size limit; the QR code's shape.
 *   java LiveHostChecks                       the checks
 *   java LiveHostChecks serve <port> <key> <dir>   a relay serving <dir> (relay.py pack's), for live-check.cjs --host
 *   java LiveHostChecks qr <text> <out.pgm>     the text's QR code as a picture, for a decoder to read back
 *   java LiveHostChecks manta <port> <key> <dir> <library>   a stand-in Manta for books-check.cjs ({@link BooksManta}); on
 *     its stdin: state, turn ±1, draw, erase, hide, show, clear, oil <ticks before it holds>, go on, tones
 *   java LiveHostChecks relayed <relay> <key> <library>   the same through a relay at <relay> (relay.py public, as
 *     superdood.org's; relay-check.cjs), no host of its own and no notebooks served
 * And the Manta's notebooks ({@link LiveBooks}): the shelf and the downloads only with the key, the downloads the bytes
 * the Manta's writers give; the page messages ({@link LivePage}) read back.
 */
public final class LiveHostChecks {
    static final String KEY = "k7qmw2xrh4tnpd9e";
    /** What the relay is given for the key: its hash ({@link LiveCrypt#access}), never the key. */
    static final LiveCrypt CRYPT = new LiveCrypt(KEY);
    static final String ACCESS = CRYPT.access;
    /** Ids as the relays take them (LivePresence.ID): the Manta's marked with the key, guests' random. */
    static final String MANTA = CRYPT.hostId("0a1b2c3d"), GUEST = "9f8e7d6c5b4a3921", GUEST2 = "1234567890abcdef";

    public static void main(String[] args) throws Exception {
        if (args.length == 4 && args[0].equals("serve")) { serve(Integer.parseInt(args[1]), args[2], Paths.get(args[3])); return; }
        if (args.length == 3 && args[0].equals("qr")) { pgm(QrCode.encode(args[1]), args[2]); return; }
        if (args.length == 5 && args[0].equals("manta")) { BooksManta.run(Integer.parseInt(args[1]), args[2], Paths.get(args[3]), new File(args[4])); return; }
        if (args.length == 4 && args[0].equals("relayed")) { BooksManta.run(args[1], args[2], new File(args[3])); return; }
        Path web = Files.createTempDirectory("live-host");
        Files.write(web.resolve("live.html"), "<p>live".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(web.resolve("fonts"));
        Files.write(web.resolve("fonts/a.ttf"), new byte[]{1, 2, 3});
        LiveHost host = host(0, KEY, web);
        try {
            checks("http://127.0.0.1:" + host.port);
            check(host.typed("192.168.1.2").equals("192.168.1.2:" + host.port) && host.code().replace(" ", "").equals(KEY)
                    && host.code().matches("(\\S{5} )*\\S{1,5}"), "short to type: the address alone, the code in fives");
        } finally { host.stop(); }
        qr(); words(); pickup(); reachable(web); again(web); guesses(web);
        books(web);
        System.out.println("PASS: live host: the web app's files served without the key; listen, send and anything else refused without its hash or with another or with the key itself; changes numbered to every listener, senders by opaque id (anything else, an old page's name too, '-'; the host's told only with the key), /send's HTTP/1.1 connections kept open, all of them first to one asking history (from the host's last start), a bad sender '-'; empty and over 4 MB refused, 4 MB taken; who listens to those that ask; the status's words; a link's QR code version 3 with its finders; the Manta's notebooks listed and downloaded only with the key, asked and answered sealed (a plain or wrongly sealed query refused), each download the bytes its writers give (PDF, page PNG, .mpaint) under its file's name; an upload only with the key, too large, not a notebook or a damaged one refused with nothing written, a name taken a new notebook beside it, folders dropped; a picture or PDF to Import only when its bytes say so; page messages read back");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static LiveHost host(int port, String key, Path dir) throws IOException { return host(port, key, dir, null); }
    static LiveHost host(int port, String key, Path dir, LiveBooks books) throws IOException {
        Set<String> names;
        try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
            names = files.filter(Files::isRegularFile).map(p -> dir.relativize(p).toString().replace(File.separatorChar, '/')).collect(Collectors.toSet());
        }
        return new LiveHost(port, key, names, name -> { try { return Files.newInputStream(dir.resolve(name)); } catch (IOException e) { return null; } }, books);
    }

    static void serve(int port, String key, Path dir) throws Exception {
        LiveHost host = host(port, key, dir);
        System.out.println("live host: http://127.0.0.1:" + host.port + "/live#" + key);
        Thread.sleep(Long.MAX_VALUE);
    }

    static void checks(String at) throws Exception {
        check(get(at + "/live").equals("200 <p>live") && get(at + "/").equals("200 <p>live"), "the page without the key, at /live and at the address alone");
        check(get(at + "/fonts/a.ttf").startsWith("200"), "a font without the key");
        check(get(at + "/../live.html").startsWith("403") && get(at + "/nothing?key=" + ACCESS).startsWith("404"), "nothing else: a path the files don't hold");
        check(get(at + "/listen").startsWith("403") && get(at + "/listen?key=x" + ACCESS).startsWith("403"), "listen without the key, or another");
        check(get(at + "/listen?key=" + KEY).startsWith("403") && post(at + "/send?key=" + KEY + "&from=a", new byte[10]).startsWith("403"), "the key itself refused: only its hash opens the relay");
        check(post(at + "/send?from=a", new byte[10]).startsWith("403") && post(at + "/send?key=" + ACCESS.substring(1) + "&from=a", new byte[10]).startsWith("403"), "send without the key, or another");

        BlockingQueue<String> one = listen(at + "/listen?key=" + ACCESS), two = listen(at + "/listen?key=" + ACCESS);
        check(post(at + "/send?key=" + ACCESS + "&from=" + MANTA, "hi".getBytes(StandardCharsets.UTF_8)).equals("200 1"), "numbered 1");
        check(post(at + "/send?key=" + ACCESS + "&from=browser-a1b2c3d4", new byte[]{7}).equals("200 2"), "numbered 2: an old page's name");
        for (BlockingQueue<String> q : Arrays.asList(one, two)) {
            check(("data: 1 " + MANTA + " " + Base64.getEncoder().encodeToString("hi".getBytes(StandardCharsets.UTF_8))).equals(q.poll(5, TimeUnit.SECONDS)), "each listener has the first");
            check("data: 2 - Bw==".equals(q.poll(5, TimeUnit.SECONDS)), "and the second, a bad sender '-'");
        }
        BlockingQueue<String> late = listen(at + "/listen?history=1&key=" + ACCESS), fresh = listen(at + "/listen?key=" + ACCESS);
        check(late.poll(5, TimeUnit.SECONDS).startsWith("data: 1 ") && late.poll(5, TimeUnit.SECONDS).startsWith("data: 2 "), "history to one that asks");
        check(fresh.poll(300, TimeUnit.MILLISECONDS) == null, "none to one that doesn't");

        check(post(at + "/send?key=" + ACCESS, new byte[0]).startsWith("413"), "empty refused");
        check(post(at + "/send?key=" + ACCESS, new byte[LiveHost.MOST + 1]).startsWith("413"), "over 4 MB refused");
        check(post(at + "/send?key=" + ACCESS, new byte[LiveHost.MOST]).equals("200 3"), "4 MB taken");
        check(fresh.poll(5, TimeUnit.SECONDS).startsWith("data: 3 - "), "and passed on");
        check(kept(at, "HTTP/1.1").equals("4 5 kept") && kept(at, "HTTP/1.0").equals("6 closed"), "/send keeps an HTTP/1.1 connection open for the next, HTTP/1.0's closed");
        for (int n = 4; n <= 6; n++) check(fresh.poll(5, TimeUnit.SECONDS).startsWith("data: " + n + " "), "and passed on: " + n);

        // Who listens (LivePresence), by name, to each listener that asks, as one comes; the four above have no name.
        BlockingQueue<String> manta = listen(at + "/listen?here=1&key=" + ACCESS + "&from=" + MANTA);
        check(("data: - - - - " + MANTA).equals(manta.poll(5, TimeUnit.SECONDS)), "who listens, to one that asks");
        BlockingQueue<String> guest = listen(at + "/listen?here=1&key=" + ACCESS + "&from=" + GUEST);
        for (BlockingQueue<String> q : Arrays.asList(manta, guest))
            check(("data: - - - - " + MANTA + " " + GUEST).equals(q.poll(5, TimeUnit.SECONDS)), "and again as another comes");
        check(fresh.poll(300, TimeUnit.MILLISECONDS) == null, "never to one that doesn't ask");

        // The host's page message before its page whole (start=1, LiveProbe.shown): the history starts again there.
        check(post(at + "/send?key=" + ACCESS + "&from=" + MANTA + "&start=1", new byte[]{8}).equals("200 7") && post(at + "/send?key=" + ACCESS + "&from=" + GUEST, new byte[]{9}).equals("200 8"), "a start taken");
        BlockingQueue<String> since = listen(at + "/listen?history=1&key=" + ACCESS);
        check(("data: 7 " + MANTA + " CA==").equals(since.poll(5, TimeUnit.SECONDS)) && ("data: 8 " + GUEST + " CQ==").equals(since.poll(5, TimeUnit.SECONDS)) && since.poll(300, TimeUnit.MILLISECONDS) == null,
                "history from the last start only");
    }

    /** The status's words (docs/TEXT-STYLE.md), the Manta's and a guest's. */
    static void words() {
        String[] none = {MANTA}, two = {GUEST, MANTA, GUEST2}, noHost = {GUEST, GUEST2};
        check(LivePresence.host(true, none, MANTA).equals("Connected, no guests") && LivePresence.host(true, two, MANTA).equals("Connected, 2 guests")
                && LivePresence.host(true, new String[]{MANTA, "-"}, MANTA).equals("Connected, 1 guest"), "the Manta's guests");
        check(LivePresence.host(true, null, MANTA).equals("Connected") && LivePresence.host(false, two, MANTA).equals("Connection lost"), "the Manta: not yet said, lost");
        check(LivePresence.guest(true, 0, two, GUEST, CRYPT).equals("Connected, 1 other guest") && LivePresence.guest(true, 0, new String[]{GUEST, MANTA}, GUEST, CRYPT).equals("Connected")
                && LivePresence.guest(true, 0, new String[]{MANTA, "a", "b", GUEST}, GUEST, CRYPT).equals("Connected, 2 other guests"), "a guest's others");
        check(LivePresence.guest(true, 0, noHost, GUEST, CRYPT).equals("Host gone") && LivePresence.guest(true, 0, null, GUEST, CRYPT).equals("Connected"), "a guest: no host, not yet said");
        check(LivePresence.cut("Host gone") && LivePresence.cut(LivePresence.guest(false, LivePresence.LOST_MS, null, "a", CRYPT))
                && !LivePresence.cut("Connected") && !LivePresence.cut(LivePresence.RECONNECTING), "a guest's drawing stops when the host is gone or the connection lost");
        check(LivePresence.guest(false, LivePresence.LOST_MS - 1, two, GUEST, CRYPT).equals("Reconnecting…") && LivePresence.guest(false, LivePresence.LOST_MS, two, GUEST, CRYPT).equals("Connection lost"), "a guest reconnecting, then lost");
        check(LivePresence.names(" ").length == 0 && LivePresence.names("a b").length == 2 && LivePresence.line(Arrays.asList("a", "b")).equals("event: here\ndata: a b\n\n"), "the event's line");
        check(LivePresence.cutNote("Host gone").equals(LivePresence.CUT_NOTE) && LivePresence.cutNote("Connection lost").equals("Waiting for the Manta, maybe asleep. Back within a minute or two of waking.")
                && LivePresence.cutNote(LivePresence.RECONNECTING) == null && LivePresence.cutNote("Connected") == null, "a cut-off guest's note: lost promises nothing");
        // The ids (the owner, 2026-11-03: the relay learns no one's name): only those with the key tell the host's.
        String fresh = CRYPT.hostId(), other = new LiveCrypt("another").hostId("0a1b2c3d");
        check(MANTA.matches(LivePresence.ID) && fresh.matches(LivePresence.ID) && !fresh.equals(CRYPT.hostId()) && MANTA.startsWith("0a1b2c3d"), "a host's id: 16 hex digits, new each time");
        check(CRYPT.isHost(MANTA) && CRYPT.isHost(fresh) && !CRYPT.isHost(GUEST) && !CRYPT.isHost(other) && !CRYPT.isHost("-") && !CRYPT.isHost("manta-1"),
                "the host told by the key: not a guest's, not another key's, not an old name");
        check(LivePresence.guest(true, 0, new String[]{other, GUEST}, GUEST, CRYPT).equals("Host gone") && LivePresence.guest(true, 0, new String[]{"manta-1", "-", GUEST}, GUEST, CRYPT).equals("Host gone"),
                "another key's host, or an old Manta's name, isn't this page's host");
        check(LivePresence.guests(null, "m") == 0 && LivePresence.guests(new String[]{"m", "a", "-"}, "m") == 2, "the Manta's guests counted (its keeping awake)");
        check(LivePresence.STILL_LINE.equals("event: still\ndata\n\n") && LivePresence.SILENT_MS >= 2 * LivePresence.STILL_MS, "the heartbeat: no \"data: \" line; two missed before a guest starts again");
    }

    /**
     * The app back from sleep: its server stopped with a guest's connections just closed (TIME_WAIT on its side) and
     * started again at once on the same port, so the guest's link still works.
     */
    // A short key: one page with a wrong key, asking again and again, counts once; MOST_WRONG different ones close the host to every key for a while.
    static void guesses(Path web) throws Exception {
        LiveHost h = host(0, KEY, web);
        String at = "http://127.0.0.1:" + h.port + "/send?from=a&key=";
        try {
            for (int i = 0; i < 2 * LiveHost.MOST_WRONG; i++) post(at + "stale", new byte[]{1});
            check(post(at + ACCESS, new byte[]{1}).startsWith("200"), "one old key asked often still leaves the right one");
            for (int i = 0; i < LiveHost.MOST_WRONG; i++) post(at + "guess" + i, new byte[]{1});
            check(post(at + ACCESS, new byte[]{1}).startsWith("403"), "after enough different wrong keys, even the right one is refused");
        } finally { h.stop(); }
    }

    static void again(Path web) throws Exception {
        LiveHost first = host(0, KEY, web);
        String at = "http://127.0.0.1:" + first.port;
        listen(at + "/listen?key=" + ACCESS);
        check(post(at + "/send?key=" + ACCESS + "&from=a", new byte[]{1}).equals("200 1"), "a change before the stop");
        first.stop();
        LiveHost second = host(first.port, KEY, web);
        try { check(second.port == first.port && post(at + "/send?key=" + ACCESS + "&from=a", new byte[]{1}).equals("200 1"), "the same port at once after a stop, numbered from 1 again"); }
        finally { second.stop(); }
    }

    /** A notebook as the Manta writes one: {@code pages} pages of marks, a cover design, the last page on the front cover. */
    static void notebook(File file, String name, int pages, long seed) throws IOException {
        DrawingBook book = new DrawingBook(InkWashLayers.blank(600, 800));
        book.name(name);
        book.setDesign(CoverDesign.values()[(int) (seed % CoverDesign.values().length)]);
        Random r = new Random(seed);
        for (int p = 0; p < pages; p++) {
            if (p > 0) book.addPage();
            ToneDocument page = book.current();
            page.begin();
            for (int i = 0; i < 3000; i++) page.setTone(80 + p * 100 + r.nextInt(200), 120 + r.nextInt(400), r.nextInt(100));
            page.finish();
        }
        book.setCover(pages - 1);
        Files.createDirectories(file.getParentFile().toPath());
        try (FileOutputStream out = new FileOutputStream(file)) { BookCodec.write(out, book.snapshot()); }
    }

    static void books(Path web) throws Exception {
        File library = Files.createTempDirectory("live-books").toFile();
        DrawingFiles files = new DrawingFiles(library);
        String name = "2026-10-31_Harbour Sketches";
        notebook(files.drawing(name), name, 3, 7);
        LiveHost host = new LiveHost(0, KEY, Collections.singleton("live.html"), n -> null, new LiveBooks(LiveBooks.folder(files), () -> null));
        try {
            String at = "http://127.0.0.1:" + host.port, path = "path=" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20");
            LiveCrypt c = new LiveCrypt(KEY);
            for (String q : new String[]{"/books", "/books/file?" + path, "/books/pdf?" + path, "/books/page?place=0&as=1&" + path, "/books/cover?w=60&h=80&" + path}) {
                check(get(at + q).startsWith("403") && get(at + q + (q.contains("?") ? "&" : "?") + "key=x" + ACCESS).startsWith("403") && get(at + q + (q.contains("?") ? "&" : "?") + "key=" + KEY).startsWith("403"), "without the key's access, or another, or the key itself: " + q);
            }
            check(new String(book(c, at, "/books", null)[1], StandardCharsets.UTF_8).matches(java.util.regex.Pattern.quote(name) + "\\t\\d+\\t3\\t0\\n"), "the shelf: its path, last change, 3 pages, readable");
            check(get(at + "/books/file?key=" + ACCESS + "&" + path).startsWith("404"), "a plain query isn't read: the notebook's name only sealed");
            check(get(at + "/books/file?key=" + ACCESS + "&q=" + Base64.getUrlEncoder().encodeToString(new LiveCrypt("other").seal(path.getBytes(StandardCharsets.UTF_8)))).startsWith("403")
                    && get(at + "/books/file?key=" + ACCESS + "&q=AAAA").startsWith("403"), "a query sealed with another key, or not sealed, refused");
            byte[] sealedShelf = bytes(at + "/books?key=" + ACCESS);
            check(!new String(sealedShelf, StandardCharsets.ISO_8859_1).contains("Harbour") && c.open(sealedShelf).length > 0, "the shelf travels sealed: no name readable on the way");
            DrawingBook read = BookCodec.read(new FileInputStream(files.drawing(name)));
            try { BlankPages.opened(read); } catch (IOException kept) { }
            byte[] file = Files.readAllBytes(files.drawing(name).toPath());
            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            NotebookExport.pdf(pdf, read, name, null, java.time.LocalDate.now().toEpochDay());
            byte[] png = PageExport.bytes(read, name, 1, PageExport.CHOICES[1], null, java.time.LocalDate.now().toEpochDay());
            check(saved(book(c, at, "/books/file", path), name + ".mpaint", file), "the .mpaint: the file's bytes, under its name");
            check(saved(book(c, at, "/books/pdf", path), NotebookPdf.base(NotebookPdf.title(name)) + ".pdf", pdf.toByteArray()), "the PDF: Export Now's bytes, under its name");
            check(saved(book(c, at, "/books/page", "place=1&as=1&" + path), PageExport.name(NotebookPdf.title(name), 1, PageExport.CHOICES[1]), png), "page 2's PNG: Export Page's bytes, under its name");
            // A size asked (size=: an ExportSize's index): Layers at Social, the browser's Export Page's bytes.
            byte[] layers = PageExport.bytes(read, name, 1, PageExport.LAYERS, ExportSize.SOCIAL, null, java.time.LocalDate.now().toEpochDay());
            check(saved(book(c, at, "/books/page", "place=1&as=2&size=0&" + path), PageExport.name(NotebookPdf.title(name), 1, PageExport.LAYERS), layers)
                    && saved(book(c, at, "/books/page", "place=1&as=1&size=1&" + path), PageExport.name(NotebookPdf.title(name), 1, PageExport.PNG), png), "page 2's Layers at Social and PNG at Page, as asked");
            byte[][] cover = book(c, at, "/books/cover", "w=60&h=80&" + path);
            check(cover[0].length == 0 && cover[1].length == 60 * 80, "the cover, a grey a pixel, no name");
            for (String q : new String[]{"/books/file?path=..%2Fx", "/books/file?path=Nothing", "/books/page?place=3&as=1&" + path, "/books/page?place=0&as=9&" + path, "/books/page?place=0&as=1&size=3&" + path, "/books/cover?w=0&h=80&" + path, "/books/else?" + path}) {
                int at_ = q.indexOf('?');
                check(get(at + q.substring(0, at_) + "?key=" + ACCESS + "&q=" + Base64.getUrlEncoder().encodeToString(c.seal(q.substring(at_ + 1).getBytes(StandardCharsets.UTF_8)))).startsWith("404"), "nothing there: " + q);
            }
            added(c, at, files, name, file);
        } finally { host.stop(); }
        imports();
        LiveHost plain = host(0, KEY, web);
        try { check(get("http://127.0.0.1:" + plain.port + "/books?key=" + ACCESS).startsWith("404"), "a host without notebooks has none"); } finally { plain.stop(); }

        DrawingBook book = BookCodec.read(new FileInputStream(files.drawing(name)));
        book.select(book.pageOf(1));
        LivePage shown = LivePage.read(LivePage.shown(name, book).write()), open = LivePage.read(LivePage.open(name).write()), turn = LivePage.read(LivePage.turn(-3).write());
        check(shown != null && LivePage.SHOWN.equals(shown.kind) && shown.place == 1 && shown.previous && shown.next && name.equals(shown.path) && shown.words.equals(DrawLayout.pageWords(1, 3)), "a page shown read back");
        check(open != null && LivePage.OPEN.equals(open.kind) && name.equals(open.path) && turn != null && LivePage.TURN.equals(turn.kind) && turn.place == -1, "open and turn read back");
        check(LivePage.read(new LiveChange(0, 0, 1, 1, new int[]{0}).write()) == null && LivePage.read(new byte[]{1, 2, 3}) == null, "a change, or noise, isn't one");
        check(LiveTools.isDeflated(LivePage.turn(1).write()), "the tools' readers pass over one (eight zero bytes), as the Manta reads page messages first");
    }

    /**
     * POST /books/add, sealed as books.js sends it: the file's name in the sealed query (q=), the file sealed, the
     * answer (the new notebook's path) sealed. Only with the key's access; a body or name sealed with another key, or
     * not sealed, refused (403) with nothing written; too large, not a notebook, picture or PDF, or a notebook that
     * doesn't read refused with nothing written; a name the shelf has makes a new notebook beside it, never over it; a
     * name's folders dropped. {@code name}: a notebook there, {@code file} its bytes.
     */
    static void added(LiveCrypt c, String at, DrawingFiles files, String name, byte[] file) throws Exception {
        java.util.function.Supplier<Set<String>> shelf = () -> { try { return files.notebooks().stream().map(e -> e.name).collect(Collectors.toSet()); } catch (IOException e) { throw new UncheckedIOException(e); } };
        Set<String> before = shelf.get();
        LiveCrypt other = new LiveCrypt("other");
        String plainName = at + "/books/add?key=" + ACCESS + "&q=" + sealedQuery(c, "name=a.mpaint");
        check(post(at + "/books/add?" + "q=" + sealedQuery(c, "name=a.mpaint"), c.seal(file)).startsWith("403") && post(at + "/books/add?key=x" + ACCESS + "&q=" + sealedQuery(c, "name=a.mpaint"), c.seal(file)).startsWith("403")
                && post(at + "/books/add?key=" + KEY + "&q=" + sealedQuery(c, "name=a.mpaint"), c.seal(file)).startsWith("403"), "an upload without the key's access, or another, or the key itself");
        check(post(plainName, file).startsWith("403") && post(plainName, other.seal(file)).startsWith("403") && post(at + "/books/add?key=" + ACCESS + "&q=" + sealedQuery(other, "name=a.mpaint"), c.seal(file)).startsWith("403")
                && post(at + "/books/add?key=" + ACCESS + "&name=a.mpaint", c.seal(file)).startsWith("403"), "an upload not sealed, or its file or name sealed with another key, or its name plain, refused");
        byte[] cut = c.seal(file); cut = Arrays.copyOf(cut, cut.length - 1);
        check(post(plainName, cut).startsWith("403"), "a sealed upload cut short refused");
        check(head(at, "POST /books/add?key=" + ACCESS + "&q=" + sealedQuery(c, "name=a.mpaint"), LiveBooks.MOST_ADDED + LiveCrypt.NONCE + LiveCrypt.TAG + 1).startsWith("HTTP/1.0 413"), "an upload over " + LiveBooks.MOST_ADDED + " bytes");
        byte[] half = Arrays.copyOf(file, file.length / 2), png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13};
        for (Object[] bad : new Object[][]{{"a.mpaint", "not a notebook".getBytes(StandardCharsets.UTF_8)}, {"a.mpaint", half}, {"a.txt", file}, {"a.png", file}, {"a.pdf", png}, {"a.png", new byte[0]}})
            check(upload(c, at, (String) bad[0], (byte[]) bad[1]).matches("41[35] .*"), "refused: " + bad[0] + ", " + ((byte[]) bad[1]).length + " bytes");
        check(shelf.get().equals(before) && Objects.requireNonNull(files.folder("").list((d, n) -> n.endsWith(".new"))).length == 0, "nothing written by those: " + shelf.get());
        check(upload(c, at, name + ".mpaint", file).equals("200 " + name + " 2"), "a name the shelf has: a new notebook, \"" + name + " 2\"");
        check(Arrays.equals(Files.readAllBytes(files.drawing(name).toPath()), file) && Arrays.equals(Files.readAllBytes(files.drawing(name + " 2").toPath()), file), "the one there unchanged, the new one the bytes sent");
        check(upload(c, at, "../../somewhere/Birds.mpaint", file).equals("200 Birds") && files.drawing("Birds").isFile(), "a name's folders dropped: Birds in the library's top folder");
        check(new String(book(c, at, "/books", null)[1], StandardCharsets.UTF_8).contains(name + " 2\t") && shelf.get().size() == before.size() + 2, "the shelf has both");
    }
    static String sealedQuery(LiveCrypt c, String query) { return Base64.getUrlEncoder().withoutPadding().encodeToString(c.seal(query.getBytes(StandardCharsets.UTF_8))); }
    /** {@code file} sent up as books.js sends it: "200 " and the new notebook's path (its answer opened), or the refusal's code. */
    static String upload(LiveCrypt c, String at, String file, byte[] bytes) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(at + "/books/add?key=" + c.access + "&q=" + sealedQuery(c, "name=" + java.net.URLEncoder.encode(file, "UTF-8"))).openConnection();
        try {
            byte[] body = c.seal(bytes);
            h.setDoOutput(true); h.setRequestMethod("POST"); h.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = h.getOutputStream()) { out.write(body); }
            if (h.getResponseCode() != 200) return h.getResponseCode() + " " + read(h);
            try (InputStream in = h.getInputStream()) { return "200 " + new String(c.open(in.readAllBytes()), StandardCharsets.UTF_8); }
        } finally { h.disconnect(); }
    }
    /** A picture or PDF reaches the library's Import ({@link LiveBooks.Library#imported}) only when its bytes are what its name says. */
    static void imports() throws IOException {
        File library = Files.createTempDirectory("live-imports").toFile();
        List<String> asked = new ArrayList<>();
        LiveBooks.Library folder = LiveBooks.folder(new DrawingFiles(library));
        LiveBooks books = new LiveBooks(new LiveBooks.Library() {
            @Override public List<DrawingFiles.Entry> notebooks() throws IOException { return folder.notebooks(); }
            @Override public BookPictures.Cover cover(String path) throws IOException { return folder.cover(path); }
            @Override public byte[] file(String path) throws IOException { return folder.file(path); }
            @Override public String add(String name, byte[] file) throws IOException { return folder.add(name, file); }
            @Override public String imported(String name, byte[] file, boolean pdf) { asked.add(name + (pdf ? " pdf" : " picture")); return name; }
        }, () -> null);
        byte[] pdf = "%PDF-1.4\n%nothing more".getBytes(StandardCharsets.ISO_8859_1), jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 0, 0, 0, 0, 0, 0, 0};
        check("Harbour Map".equals(books.add("Harbour Map.PDF", pdf)) && "IMG_2041".equals(books.add("IMG_2041.jpg", jpeg)), "a PDF and a JPEG to Import, named after their files: " + asked);
        check(books.add("x.jpg", pdf) == null && books.add("x.webp", jpeg) == null && books.add("x.heic", jpeg) == null, "a PDF named .jpg, a JPEG named .webp, a kind Import doesn't take");
        check(asked.equals(Arrays.asList("Harbour Map pdf", "IMG_2041 picture")), "only those two asked: " + asked);
    }
    /** Only a request's head sent ({@code line}, Content-Length {@code length}), no body: the answer's first line. */
    static String head(String at, String line, long length) throws IOException {
        URL u = new URL(at);
        try (java.net.Socket s = new java.net.Socket(u.getHost(), u.getPort())) {
            s.setSoTimeout(5000);
            s.getOutputStream().write((line + " HTTP/1.0\r\nContent-Length: " + length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            return new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII)).readLine();
        }
    }

    /**
     * Two /send POSTs on one connection, {@code version}'s: their numbers, then "kept" when the second was answered on it
     * too; "closed" when the first's answer closed it.
     */
    static String kept(String at, String version) throws IOException {
        URL u = new URL(at);
        try (java.net.Socket s = new java.net.Socket(u.getHost(), u.getPort())) {
            s.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            StringBuilder got = new StringBuilder();
            for (int i = 0; i < 2; i++) {
                try { s.getOutputStream().write(("POST /send?key=" + ACCESS + "&from=k " + version + "\r\nContent-Length: 1\r\n\r\nx").getBytes(StandardCharsets.US_ASCII)); }
                catch (IOException e) { return got.append("closed").toString(); }
                int length = -1;
                for (String line; (line = readLine(in)) != null && !line.isEmpty(); ) if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) length = Integer.parseInt(line.substring(15).trim());
                if (length < 0) return got.append("closed").toString();
                byte[] b = new byte[length]; in.readFully(b);
                got.append(new String(b, StandardCharsets.US_ASCII)).append(' ');
                if (version.equals("HTTP/1.0")) return got.append(in.read() < 0 ? "closed" : "open").toString();
            }
            return got.append("kept").toString();
        }
    }
    private static String readLine(DataInputStream in) throws IOException {
        StringBuilder b = new StringBuilder();
        for (int c; (c = in.read()) >= 0; ) { if (c == '\n') return b.toString(); if (c != '\r') b.append((char) c); }
        return b.length() == 0 ? null : b.toString();
    }

    /** {@code url}'s bytes (200 only). */
    static byte[] bytes(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            check(c.getResponseCode() == 200, url + ": " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) { ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[65536]; for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n); return b.toByteArray(); }
        } finally { c.disconnect(); }
    }
    /** A /books route asked as books.js asks it, {@code query} sealed (null: none), the answer opened: {name, bytes}. */
    static byte[][] book(LiveCrypt c, String at, String route, String query) throws IOException {
        byte[] plain = c.open(bytes(at + route + "?key=" + c.access + (query == null ? "" : "&q=" + Base64.getUrlEncoder().withoutPadding().encodeToString(c.seal(query.getBytes(StandardCharsets.UTF_8))))));
        int nl = 0; while (plain[nl] != '\n') nl++;
        return new byte[][]{Arrays.copyOf(plain, nl), Arrays.copyOfRange(plain, nl + 1, plain.length)};
    }
    static boolean saved(byte[][] got, String name, byte[] bytes) { return new String(got[0], StandardCharsets.UTF_8).equals(name) && Arrays.equals(got[1], bytes); }

    static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try { return c.getResponseCode() + " " + read(c); } finally { c.disconnect(); }
    }
    static String post(String url, byte[] body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setDoOutput(true); c.setRequestMethod("POST"); c.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = c.getOutputStream()) { out.write(body); }
            return c.getResponseCode() + " " + read(c);
        } finally { c.disconnect(); }
    }
    static String read(HttpURLConnection c) throws IOException {
        InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
        if (in == null) return "";
        try (InputStream s = in) { ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; for (int n; (n = s.read(buf)) > 0; ) b.write(buf, 0, n); return b.toString("UTF-8"); }
    }
    /** The data lines a listener gets, as they come; the connection is left open (the relay's stop closes it). */
    static BlockingQueue<String> listen(String url) throws Exception {
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        CountDownLatch open = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                open.countDown();
                for (String line; (line = in.readLine()) != null; ) if (line.startsWith("data: ")) lines.add(line);
            } catch (IOException ignored) { open.countDown(); }
        });
        t.setDaemon(true); t.start();
        check(open.await(5, TimeUnit.SECONDS), "listening");
        Thread.sleep(100);  // registered before anything is sent
        return lines;
    }

    /**
     * A stand-in Manta for books-check.cjs: Share on Wi-Fi's server ({@link LiveHost}, its notebooks {@link LiveBooks})
     * over {@code library} (two notebooks made when it has none), and the Manta's side of a live page in plain Java as
     * LiveProbe and PaintActivity do it: on connect, and at each page shown, the page message, the tools and the page
     * whole; a guest's change laid on the page shown (one Undo step, {@link PictureImport#lay}) and the notebook saved;
     * a guest's open and turn done as the shelf and the page bar do them. Reads lines on stdin: "turn -1|1" (the Manta's
     * own page bar), "state" (prints the notebook, the place, and the marks on the page and in its saved file); "draw",
     * "erase", "hide", "show", "clear" (a block drawn on the layer drawn on and half of it erased, that layer hidden and
     * shown, Clear Page), each sent as LiveProbe.changed sends the page's change.
     * shortcut: LiveProbe's and PaintActivity's own code is Android's and runs only on the Manta; this follows them.
     */
    static final class BooksManta {
        final DrawingFiles files;
        final String at, me;  // a host's id, as LiveProbe's (a guest sees its host there)
        final LiveCrypt crypt;
        final java.util.concurrent.ExecutorService sending = java.util.concurrent.Executors.newSingleThreadExecutor();
        DrawingBook book;
        String path = "";
        int laid;

        BooksManta(DrawingFiles files, String at, String key) { this.files = files; this.at = at; crypt = new LiveCrypt(key); me = crypt.hostId(); }

        static void run(int port, String key, Path web, File library) throws Exception {
            DrawingFiles files = new DrawingFiles(library);
            if (files.notebooks().isEmpty()) {
                notebook(files.drawing("2026-10-30_Birds"), "2026-10-30_Birds", 2, 3);
                notebook(files.drawing("2026-10-31_Harbour Sketches"), "2026-10-31_Harbour Sketches", 3, 7);
            }
            LiveHost host = host(port, key, web, new LiveBooks(LiveBooks.folder(files), () -> null));
            run(files, "http://127.0.0.1:" + host.port, key);
            host.stop();
        }
        /** Through the relay at {@code at} (LiveShare through superdood.org): the Manta's side only. */
        static void run(String at, String key, File library) throws Exception {
            DrawingFiles files = new DrawingFiles(library);
            if (files.notebooks().isEmpty()) notebook(files.drawing("2026-10-30_Birds"), "2026-10-30_Birds", 2, 3);
            run(files, at, key);
        }
        static void run(DrawingFiles files, String at, String key) throws Exception {
            BooksManta m = new BooksManta(files, at, key);
            m.open(files.notebooks().get(0).name);
            Thread listening = new Thread(m::listen, "manta");
            listening.setDaemon(true); listening.start();
            System.out.println("live host: " + m.at + "/live#" + key);
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            for (String line; (line = in.readLine()) != null; ) {
                if (line.startsWith("turn ")) m.turn(Integer.parseInt(line.substring(5).trim()));
                else if (line.equals("state")) System.out.println(m.state());
                else if (line.startsWith("oil ")) m.oil(Integer.parseInt(line.substring(4).trim()));
                else if (line.equals("go on")) m.goOn();
                else if (line.equals("tones")) System.out.println(m.tones());
                else m.edit(line.trim());
            }
        }

        void listen() {
            while (true) {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(at + "/listen?key=" + crypt.access + "&here=1&from=" + me).openConnection();
                    BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                    // Its id as LiveProbe's (a guest sees its host there); who listens (an event's data) skipped.
                    boolean named = false;
                    for (String line; (line = in.readLine()) != null; ) {
                        if (line.isEmpty()) named = false;
                        if (line.startsWith("event: ")) named = true;
                        if (named || !line.startsWith("data: ")) continue;
                        String[] parts = line.substring(6).split(" ", 3);
                        if (me.equals(parts[1])) continue;
                        byte[] bytes = crypt.open(Base64.getDecoder().decode(parts[2]));
                        LivePage page = LivePage.read(bytes);
                        if (page != null) { asked(page); continue; }
                        if (LiveTools.isDeflated(bytes)) continue;
                        lay(LiveChange.read(bytes));
                    }
                } catch (IOException e) { System.err.println("manta: " + e); }
                try { Thread.sleep(500); } catch (InterruptedException e) { return; }
            }
        }

        synchronized void asked(LivePage asked) throws IOException {
            if (LivePage.OPEN.equals(asked.kind)) { if (!asked.path.equals(path)) open(asked.path); else shown(); }
            else if (LivePage.TURN.equals(asked.kind)) turn(asked.place);
        }
        synchronized void open(String to) throws IOException {
            DrawingBook read = BookCodec.read(new ByteArrayInputStream(LiveBooks.folder(files).file(to)));
            try { BlankPages.opened(read); } catch (IOException kept) { }
            read.name(DrawingFiles.name(to));
            book = read; path = to;
            System.out.println("{\"opened\":\"" + to + "\"}");
            shown();
        }
        synchronized void turn(int by) throws IOException {
            PageStacks s = book.stacks();
            int here = DrawingBook.isCover(book.index()) ? book.index() : s.of(book.index()), to = by < 0 ? s.before(here) : s.after(here);
            if (to != DrawingBook.NONE) book.select(book.pageOf(to));
            shown();
        }
        /** As LiveProbe.shown: the page message (the relay's history starts again there), the tools, the page whole. */
        synchronized void shown() throws IOException {
            ToneDocument page = book.current();
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < ToolSlots.COUNT; i++) lines.add(ToolSlots.first(i).write() + "\t" + ToolSlots.firstPx(i) + "\t" + ToolSlots.firstGrey(i));
            LiveTools tools = new LiveTools(page.width, page.height, ToolSlots.Toolbar.parse(lines), 2, ToolSlots.FIRST_ERASER_PX, Mirrors.NONE);  // the third slot, as live-check.cjs's host
            page.takeLive();  // kept from now on
            room = new byte[page.width * page.height];
            Arrays.fill(room, (byte) 255);
            LiveChange whole = LiveChange.shown(Collections.singletonList(new int[]{0, 0, page.width, page.height}), page.width, page.height, page::compositeTones, LiveChange.NO_PATH).since(room, page.width);
            send(LivePage.shown(path, book).write(), true);
            for (byte[] b : new byte[][]{tools.write(), whole.write()}) send(b);
        }
        /** As LiveProbe's: what each pixel of the page shown was last sent as. */
        byte[] room;
        void send(byte[] b) { send(b, false); }
        void send(byte[] b, boolean start) { sending.execute(() -> { try { post(at + "/send?key=" + crypt.access + "&from=" + me + (start ? "&start=1" : ""), crypt.seal(b)); } catch (IOException e) { System.err.println("manta: " + e); } }); }
        /** The Manta's own edit of the page shown (the commands above), its change sent as LiveProbe.changed sends it. */
        synchronized void edit(String what) throws IOException {
            ToneDocument page = book.current();
            byte[] half = new byte[200 * 100];
            Arrays.fill(half, 0, half.length / 2, (byte) 1);
            switch (what) {
                case "draw": page.begin(); page.setTones(50, 50, 200, 100, new int[200 * 100]); page.finish(); break;
                case "erase": page.begin(); page.eraseTones(50, 50, 200, 100, half); page.finish(); break;
                case "hide": case "show": page.setLayerVisible(page.activeLayer(), what.equals("show")); break;
                case "clear": page.clearPage(); break;
                default: return;
            }
            changed();
            System.out.println("{\"edited\":\"" + what + "\"}");
        }
        /** As LiveProbe.changed: what the page shows where it was written since, only what differs from what was sent. */
        synchronized void changed() throws IOException {
            ToneDocument page = book.current();
            LiveChange c = LiveChange.shown(page.takeLive(), page.width, page.height, page::compositeTones, LiveChange.NO_PATH);
            if (c != null) c = c.since(room, page.width);
            if (c != null) send(c.write());
        }

        /** The owner's oil's wet paint ({@link #oil}) and its ticks, as the drawing screen's {@link DryingTicker} ticks them. */
        WatercolorSession wet;
        final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "ticks"); t.setDaemon(true); return t; });
        int ticks, holdAt = -1;
        boolean ticksDue;
        /**
         * The owner's case (2026-11-02): Rob boss (Oils, wet, Drag, keeps paint) at 360 px over a Verve felt loop on the page
         * shown, sent at the lift (LiveProbe.changed); then its ticks as DryingTicker's, what each shows sent within
         * {@link LiveChange#TICKS_MS} (LiveProbe.ticked), held after {@code hold} ticks (as under a pen) or run on to rest,
         * sent then (settled); {"held":n} or {"rested":n}.
         */
        synchronized void oil(int hold) throws IOException {
            Assets.install(Assets.folder(Assets.FOLDER));
            ToneDocument page = book.current();
            wet = new WatercolorSession();
            String[] codes = {ToolSlots.FIRST[2][2], ToolSlots.FIRST[6][2]};  // Verve felt, Rob boss
            double[] px = {7.5, 360};
            for (int k = 0; k < codes.length; k++) {
                DrawingStroke stroke = ReplayRun.stroke(page, codes[k], px[k], 1, wet);
                for (int i = 0; i <= 120; i++) {
                    double a = i * Math.PI / 60;
                    stroke.sample((float) (300 + 20 * Math.sin(a) + (k == 0 ? 12 : 0)), (float) (400 - 60 * Math.cos(a)), .7f, Float.NaN, Float.NaN, 1000 + 8L * i);
                    if (i % 3 == 2) stroke.present();
                }
                stroke.present(); stroke.finish();
                changed();
            }
            ticks = 0; holdAt = hold;
            ticker.schedule(this::tick, 100, TimeUnit.MILLISECONDS);
        }
        synchronized void goOn() { holdAt = -1; ticker.execute(this::tick); }
        private synchronized void tick() {
            try {
                if (wet == null) return;
                if (holdAt >= 0 && ticks >= holdAt) { System.out.println("{\"held\":" + ticks + "}"); return; }
                long next = wet.tick(book.current(), this::ticked);
                ticks++;
                if (next < 0) { wet = null; changed(); System.out.println("{\"rested\":" + ticks + "}"); return; }
                ticker.schedule(this::tick, Math.max(1, next), TimeUnit.MILLISECONDS);
            } catch (IOException e) { System.err.println("manta: " + e); }
        }
        private void ticked() {
            if (ticksDue) return;
            ticksDue = true;
            ticker.schedule(() -> { synchronized (this) { ticksDue = false; try { changed(); } catch (IOException e) { System.err.println("manta: " + e); } } }, LiveChange.TICKS_MS, TimeUnit.MILLISECONDS);
        }
        /** What the page shows over {@link #OIL_BOX} ({x, y, w, h}), a byte a pixel in base64, and the ticks so far. */
        static final int[] OIL_BOX = {0, 100, 600, 600};
        synchronized String tones() {
            int[] b = OIL_BOX, t = new int[b[2] * b[3]];
            book.current().compositeTones(b[0], b[1], b[2], b[3], t, 0, b[2]);
            byte[] raw = new byte[t.length];
            for (int i = 0; i < t.length; i++) raw[i] = (byte) t[i];
            return "{\"box\":[" + b[0] + "," + b[1] + "," + b[2] + "," + b[3] + "],\"ticks\":" + ticks + ",\"tones\":\"" + Base64.getEncoder().encodeToString(raw) + "\"}";
        }
        /** A guest's change laid on the page shown, then the notebook saved (whole, then swapped in, as AtomicFile). */
        synchronized void lay(LiveChange c) throws IOException {
            boolean changed = PictureImport.lay(book.current(), c.x, c.y, c.w, c.h, c.greys.clone(), false, c.set);
            book.current().takeLive();  // what was laid isn't sent back (LiveProbe.laySlice)
            c.into(room, book.current().width);
            if (!changed) return;
            laid++;
            File file = files.drawing(path), fresh = new File(file.getPath() + ".new");
            DrawingBook.Snapshot s = book.snapshot();
            try (FileOutputStream out = new FileOutputStream(fresh)) { BookCodec.write(out, s); }
            book.savedPages(s); book.markSaved(s);
            Files.move(fresh.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("{\"laid\":" + laid + "}");
        }
        synchronized String state() throws IOException {
            int place = DrawingBook.isCover(book.index()) ? book.index() : book.stacks().of(book.index());
            DrawingBook saved = BookCodec.read(new ByteArrayInputStream(LiveBooks.folder(files).file(path)));
            saved.select(saved.pageOf(place));
            return "{\"path\":\"" + path + "\",\"place\":" + place + ",\"laid\":" + laid + ",\"marks\":" + marks(book.current()) + ",\"saved\":" + marks(saved.current()) + "}";
        }
        static int marks(ToneDocument page) {
            int[] t = new int[page.width * page.height];
            page.compositeTones(0, 0, page.width, page.height, t, 0, page.width);
            int n = 0; for (int v : t) if (v < 250) n++;
            return n;
        }
    }

    /** superdood.org's code asked for as the server takes it (a stand-in here; its own tests: test_server.py). */
    static void pickup() throws Exception {
        com.sun.net.httpserver.HttpServer at = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        String[] sent = {null};
        at.createContext("/code", x -> {
            sent[0] = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8) + " " + x.getRequestHeaders().getFirst("Content-Type");
            byte[] b = "{\"code\":\"K7QM\",\"seconds\":600}".getBytes(StandardCharsets.UTF_8);
            x.sendResponseHeaders(200, b.length); x.getResponseBody().write(b); x.close();
        });
        at.start();
        String base = "http://127.0.0.1:" + at.getAddress().getPort(), link = "http://192.168.1.23:8080/live#k7qmw2xrh4";
        try {
            check("K7QM".equals(LiveHost.pickup(base + "/code", link)) && ("{\"to\":\"" + link + "\"} application/json").equals(sent[0]), "superdood.org's code: the link sent, its code read");
            check(LiveHost.pickup(base + "/nothing", link) == null, "superdood.org's code: refused, no code");
        } finally { at.stop(0); }
        check(LiveHost.pickup("http://127.0.0.1:1/code", link) == null, "superdood.org's code: offline, no code");
    }

    /** Draw Together's way picked (LiveShare.startHost): the relay when its page answers, else the Wi-Fi's own host. */
    static void reachable(Path web) throws Exception {
        LiveHost h = host(0, KEY, web);
        try { check(LiveHost.reachable("http://127.0.0.1:" + h.port), "a relay that answers its page: reachable"); }
        finally { h.stop(); }
        check(!LiveHost.reachable("http://127.0.0.1:1") && !LiveHost.reachable("http://127.0.0.1:" + h.port + "/nothing"), "offline, or no page there: not");
    }

    static void qr() {
        boolean[][] q = QrCode.encode("http://192.168.100.200:8080/live#" + KEY);
        check(q != null && q.length == 29, "a link: version 3");
        String key = LiveHost.newKey();
        check(key.length() == 4 && key.matches("[a-hjkmnp-z2-9]+") && !key.equals(LiveHost.newKey()), "a new key: 4 unambiguous letters, new each time");
        boolean[][] longest = QrCode.encode("http://255.255.255.255:65535/live#" + key);
        check(longest != null && longest.length == 29, "the longest link (any address, any port, a 10-letter key): version 3 still");
        String long_ = LiveHost.newKey(LiveHost.RELAY_KEY);
        check(long_.length() == 10 && long_.matches("[a-hjkmnp-z2-9]+") && LiveHost.relayLink(long_).equals("https://superdood.org/live/#" + long_),
                "a key through superdood.org's relay: 10 letters, its link there");
        check(LiveHost.PICKUP_SHOWN.equals("superdood.org") && LiveHost.PICKUP.equals("https://superdood.org/code") && LiveHost.RELAY.equals("https://superdood.org/live"),
                "superdood.org: the code box shown, its code asked there, the relay under /live (superdood.nginx, server.py TO_RE)");
        boolean[][] relayed = QrCode.encode(LiveHost.relayLink(long_));
        check(relayed != null && relayed.length == 29, "the relay's link: version 3");
        for (int[] c : new int[][]{{0, 0}, {q.length - 7, 0}, {0, q.length - 7}})
            check(q[c[1]][c[0]] && q[c[1] + 6][c[0] + 6] && !q[c[1] + 1][c[0] + 1] && q[c[1] + 3][c[0] + 3], "a finder at each corner but one");
        check(QrCode.encode(new String(new char[107]).replace('\0', 'a')) == null, "107 bytes too long");
    }

    static void pgm(boolean[][] q, String out) throws IOException {
        int scale = 8, side = (q.length + 8) * scale;
        try (OutputStream o = new BufferedOutputStream(new FileOutputStream(out))) {
            o.write(("P5\n" + side + " " + side + "\n255\n").getBytes(StandardCharsets.US_ASCII));
            for (int y = 0; y < side; y++) for (int x = 0; x < side; x++) {
                int mx = x / scale - 4, my = y / scale - 4;
                o.write(mx >= 0 && my >= 0 && mx < q.length && my < q.length && q[my][mx] ? 0 : 255);
            }
        }
    }
}
