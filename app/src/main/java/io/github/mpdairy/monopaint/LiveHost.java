package io.github.mpdairy.monopaint;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * The Manta's own live relay on the Wi-Fi (Share on Wi-Fi, {@link LiveShare};
 * docs/superpowers/specs/2026-11-01-manta-host-design.md): scripts/live-probe/relay.py in pure Java, so it runs on the
 * JVM too (LiveHostChecks). Serves the packed web app ({@code names}, read with {@code files}: nothing from a request
 * reaches a path that isn't one of them; /live is live.html), GET /listen as server-sent events and POST /send, with
 * the relay's size limit and history (from the host's last page sent whole: a send with {@code start=1}, LiveProbe's
 * page message, drops all before it, as that page is set over every pixel). Every request but the web app's files needs
 * {@code ?key=} the link's key's access hash ({@link LiveCrypt#access}; the key itself, after the link's #, never comes
 * here); without it, 403. Changes go through sealed ({@link LiveCrypt}), unread. One request a connection (HTTP/1.0). Who listens, by
 * opaque id ({@link LivePresence#ID}), to each listener that asks ({@link LivePresence}), as relay.py does. With {@code books},
 * GET /books... too: the Manta's notebooks ({@link LiveBooks}), behind the key as the rest, asked and answered sealed
 * ({@link #book}); and POST /books/add, a file sent up made a new notebook (at most {@link LiveBooks#MOST_ADDED}),
 * sealed too ({@link #added}). And Send's files ({@link #send}), while it has some.
 */
final class LiveHost {
    static final int PORT = 8080, MOST = 4 << 20, MOST_HEAD = 8192, MOST_CONNECTIONS = 32;
    private static final String KEY_LETTERS = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final byte[] STILL = LivePresence.STILL_LINE.getBytes(StandardCharsets.US_ASCII);
    final String key;
    final LiveCrypt crypt;
    final int port;
    private final ServerSocket server;
    private final Set<String> names;
    private final Function<String, InputStream> files;
    private final LiveBooks books;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Set<Socket> open = ConcurrentHashMap.newKeySet();
    /** Each listener's queue and its name; the ones that asked who listens. */
    private final Map<BlockingQueue<byte[]>, String> listeners = new LinkedHashMap<>();
    private final Set<BlockingQueue<byte[]>> asking = new HashSet<>();
    private final List<byte[]> history = new ArrayList<>();
    private int count;
    /** Send's files while its window is open ({@link LiveSend}), else null: {@code GET /get/<token>...}, by its token alone. */
    volatile LiveSend send;

    /** Listening on every address at {@code port}, or any free port when it is taken. */
    LiveHost(int port, String key, Set<String> names, Function<String, InputStream> files) throws IOException { this(port, key, names, files, null); }
    /** As above, the notebooks in {@code books} served too (null: none). */
    LiveHost(int port, String key, Set<String> names, Function<String, InputStream> files, LiveBooks books) throws IOException {
        this.books = books;
        ServerSocket s;
        // Bound again at once after a stop (the app back from sleep): its last connections' TIME_WAIT must not move the port.
        s = new ServerSocket();
        s.setReuseAddress(true);
        try { s.bind(new InetSocketAddress(port)); } catch (BindException e) { s.close(); s = new ServerSocket(0); }
        server = s; this.port = s.getLocalPort(); this.key = key; crypt = new LiveCrypt(key); this.names = names; this.files = files;
        Thread accepting = new Thread(this::accept, "LiveHost");
        accepting.setDaemon(true); accepting.start();
    }

    /**
     * A new key: 4 letters (about 20 bits), lower case and unambiguous, quick to type (the owner's choice, 2026-11-03; it was
     * 10). Guessing here is slowed: {@link #MOST_WRONG} different wrong keys in {@link #WRONG_MS}, then none. shortcut: someone who can read the
     * Wi-Fi's traffic sees the access hash and can try all keys offline in minutes; back to 10 letters for a shared Wi-Fi.
     */
    static String newKey() { return newKey(4); }
    /** A new key of {@code letters} from the same letters ({@link #RELAY_KEY} for {@link #RELAY}'s). */
    static String newKey(int letters) {
        SecureRandom random = new SecureRandom();
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < letters; i++) s.append(KEY_LETTERS.charAt(random.nextInt(KEY_LETTERS.length())));
        return s.toString();
    }

    /**
     * Draw Together off the Wi-Fi (2026-11-03): scripts/live-probe/relay.py public at superdood.org/live/, which the Manta
     * and its guests both reach on any network; the guest page is served there too. The one place its address is written
     * in the app (the pickup's {@link #PICKUP} and its server's TO_RE name superdood.org too).
     */
    static final String RELAY = "https://superdood.org/live";
    /**
     * A key through {@link #RELAY}: 10 letters (about 50 bits), as {@link LiveCrypt#ROUNDS} is weighed for: its relay sees
     * the access hash, and a 4-letter key would fall offline in seconds. Never typed: the QR code or the pickup's code.
     */
    static final int RELAY_KEY = 10;
    /** The link a guest opens for a key through {@link #RELAY} (the QR code's, the pickup's). */
    static String relayLink(String key) { return RELAY + "/#" + key; }
    /** Whether {@code at} answers its page (200) within a few seconds: the internet and the relay there. Off the main thread. */
    static boolean reachable(String at) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(at + "/").openConnection();
            c.setConnectTimeout(4000); c.setReadTimeout(4000);
            try { return c.getResponseCode() == 200; } finally { c.disconnect(); }
        } catch (IOException | RuntimeException e) { return false; }
    }

    /** This device's address on the Wi-Fi (a private IPv4 one, wlan first), or null with none. */
    static String lanAddress() {
        String found = null;
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback()) continue;
                for (InetAddress a : Collections.list(n.getInetAddresses()))
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        if (n.getName().startsWith("wlan")) return a.getHostAddress();
                        if (found == null) found = a.getHostAddress();
                    }
            }
        } catch (SocketException | RuntimeException ignored) {}
        return found;
    }

    /** The link a browser on the Wi-Fi opens (the QR code's). */
    String link(String address) { return "http://" + address + ":" + port + "/live#" + key; }
    /** The same, short to type: the address alone opens the page, which asks for the code (live.html). */
    String typed(String address) { return address + ":" + port; }
    /** The key as the page's code box takes it, in fives (spaces are dropped there). */
    String code() { return key.replaceAll("(.{5})(?=.)", "$1 "); }

    /** Where a short code takes a browser to the link (superdood.org's code box; superdood.org's server code (not in this repo)). */
    static final String PICKUP = "https://superdood.org/code", PICKUP_SHOWN = "superdood.org";
    /**
     * A 4-letter code for {@code link} from {@code at} ({@link #PICKUP}): typed at superdood.org within ten minutes, once,
     * it opens the link. Null offline or refused. The link's key goes to superdood.org for those minutes, in memory only.
     */
    static String pickup(String at, String link) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(at).openConnection();
            c.setConnectTimeout(5000); c.setReadTimeout(5000); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) { out.write(("{\"to\":\"" + link + "\"}").getBytes(StandardCharsets.UTF_8)); }
            if (c.getResponseCode() != 200) return null;
            ByteArrayOutputStream answer = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) { byte[] b = new byte[512]; for (int n; (n = in.read(b)) > 0 && answer.size() < 4096; ) answer.write(b, 0, n); }
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"code\":\"([A-Z0-9]{4})\"").matcher(answer.toString("UTF-8"));
            return m.find() ? m.group(1) : null;
        } catch (IOException | RuntimeException e) { return null; }
    }

    void stop() {
        try { server.close(); } catch (IOException ignored) {}
        for (Socket s : open) close(s);
        pool.shutdownNow();
        synchronized (this) { listeners.clear(); asking.clear(); history.clear(); }
    }

    private void accept() {
        while (!server.isClosed()) {
            Socket s;
            try { s = server.accept(); } catch (IOException e) { return; }
            if (open.size() >= MOST_CONNECTIONS) { close(s); continue; }
            open.add(s);
            try {
                pool.execute(() -> {
                    try { handle(s); } catch (IOException | RuntimeException ignored) {}
                    finally { open.remove(s); close(s); }
                });
            } catch (RejectedExecutionException e) { open.remove(s); close(s); }
        }
    }

    private void handle(Socket s) throws IOException {
        s.setSoTimeout(20000);
        InputStream in = new BufferedInputStream(s.getInputStream());
        while (request(s, in)) {}
    }

    /**
     * One request on the connection; true when it was a /send answered with the connection kept open for the next
     * (HTTP/1.1's keep-alive): a stroke's parts then pay no new connection's round trip on the Wi-Fi each. An idle one
     * ends at the socket's timeout.
     */
    private boolean request(Socket s, InputStream in) throws IOException {
        String head = head(in);
        if (head == null) return false;
        String[] lines = head.split("\r\n"), request = lines[0].split(" ");
        if (request.length < 3) { answer(s, in, 400); return false; }
        String method = request[0], target = request[1];
        int q = target.indexOf('?');
        String path = q < 0 ? target : target.substring(0, q);
        Map<String, String> query = query(q < 0 ? "" : target.substring(q + 1));
        long length = 0;
        for (String line : lines) if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
            try { length = Long.parseLong(line.substring(15).trim()); } catch (NumberFormatException e) { length = -1; }
        }
        LiveSend sending = send;
        if (method.equals("GET") && path.startsWith(LiveSend.PATH)) {
            if (sending == null || !sending.serve(path, s.getOutputStream(), System.currentTimeMillis())) answer(s, in, 404);
            return false;
        }
        String file = path.equals("/") || path.equals("/live") ? "live.html" : path.substring(1);
        if (method.equals("GET") && names.contains(file)) { serve(s, in, file); return false; }
        if (!keyed(query.get("key"))) { answer(s, in, 403); return false; }
        if (method.equals("GET") && path.equals("/listen")) { listen(s, "1".equals(query.get("history")), "1".equals(query.get("here")), name(query.get("from"))); return false; }
        if (method.equals("GET") && books != null && (path.equals("/books") || path.startsWith("/books/"))) { book(s, in, path, query); return false; }
        if (method.equals("POST") && books != null && path.equals("/books/add")) { added(s, in, length, query); return false; }
        if (!method.equals("POST") || !path.equals("/send")) { answer(s, in, 404); return false; }
        if (length <= 0 || length > MOST) { answer(s, in, 413); return false; }
        byte[] body = new byte[(int) length];
        new DataInputStream(in).readFully(body);
        String from = name(query.get("from"));
        int number;
        synchronized (this) {
            number = ++count;
            byte[] line = ("data: " + number + " " + from + " " + Base64.getEncoder().encodeToString(body) + "\nid: " + System.currentTimeMillis() + "\n\n").getBytes(StandardCharsets.US_ASCII);
            if ("1".equals(query.get("start"))) history.clear();
            history.add(line);
            for (BlockingQueue<byte[]> l : listeners.keySet()) l.add(line);
        }
        byte[] answer = String.valueOf(number).getBytes(StandardCharsets.US_ASCII);
        if (!request[2].equals("HTTP/1.1") || head.toLowerCase(java.util.Locale.ROOT).contains("\r\nconnection: close")) { answer(s, in, 200, "text/plain", answer); return false; }
        OutputStream out = s.getOutputStream();
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + answer.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(answer); out.flush();
        return true;
    }

    /** Different wrong keys this host takes in {@link #WRONG_MS} before it refuses every key till that time is up (a page left
     * open with an old key counts once): all 4-letter keys would take months. */
    static final int MOST_WRONG = 50;
    static final long WRONG_MS = 10 * 60_000;
    private final Set<String> wrong = ConcurrentHashMap.newKeySet();
    private volatile long wrongSince = System.currentTimeMillis();

    private boolean keyed(String given) {
        long now = System.currentTimeMillis();
        if (now - wrongSince > WRONG_MS) { wrong.clear(); wrongSince = now; }
        if (given == null || wrong.size() >= MOST_WRONG) return false;
        if (MessageDigest.isEqual(crypt.access.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) return true;
        if (given.length() <= 64) wrong.add(given);
        return false;
    }

    /** A sender's or a listener's id ({@link LivePresence#ID}: 16 hex digits, random, never a name); else "-". */
    private static String name(String from) { return from == null || !from.matches(LivePresence.ID) ? "-" : from; }

    /** Who listens now, to each listener that asks; called holding the lock. */
    private void present() {
        byte[] line = LivePresence.line(listeners.values()).getBytes(StandardCharsets.US_ASCII);
        for (BlockingQueue<byte[]> l : asking) l.add(line);
    }

    private void listen(Socket s, boolean withHistory, boolean here, String name) throws IOException {
        BlockingQueue<byte[]> q = new LinkedBlockingQueue<>();
        synchronized (this) { if (withHistory) q.addAll(history); listeners.put(q, name); if (here) asking.add(q); present(); }
        try {
            OutputStream out = s.getOutputStream();
            out.write("HTTP/1.0 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\n\r\n: hello\n\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            while (!server.isClosed()) {
                byte[] line = q.poll(LivePresence.STILL_MS, TimeUnit.MILLISECONDS);
                out.write(line == null ? STILL : line); out.flush();
            }
        } catch (InterruptedException ignored) {
        } finally { synchronized (this) { listeners.remove(q); asking.remove(q); present(); } }
    }

    private void serve(Socket s, InputStream in, String file) throws IOException {
        InputStream stream = files.apply(file);
        if (stream == null) { answer(s, in, 404); return; }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream from = stream) { byte[] b = new byte[65536]; for (int n; (n = from.read(b)) > 0; ) bytes.write(b, 0, n); }
        answer(s, in, 200, type(file), bytes.toByteArray());
    }

    /**
     * The notebooks, sealed both ways: what is asked (a notebook's name, a page) comes as {@code q=}, the query sealed and
     * base64url; the answer is sealed, its plain bytes the file's name to save under (empty: not a download), a line
     * feed, then the bytes. A {@code q} that doesn't open is refused (403); nothing is read from the plain query.
     */
    private void book(Socket s, InputStream in, String path, Map<String, String> query) throws IOException {
        Map<String, String> asked = query.containsKey("q") ? asked(query) : Collections.emptyMap();
        if (asked == null) { answer(s, in, 403); return; }
        LiveBooks.Answer a;
        try { a = books.answer(path, asked); } catch (IOException | RuntimeException | OutOfMemoryError e) { a = null; }
        if (a == null) { answer(s, in, 404); return; }
        byte[] name = ((a.name == null ? "" : a.name.replace('\n', ' ')) + "\n").getBytes(StandardCharsets.UTF_8), plain = new byte[name.length + a.bytes.length];
        System.arraycopy(name, 0, plain, 0, name.length); System.arraycopy(a.bytes, 0, plain, name.length, a.bytes.length);
        answer(s, in, 200, "application/octet-stream", crypt.seal(plain));
    }

    /** {@code q=} opened: the query asked, sealed; null when it doesn't open. */
    private Map<String, String> asked(Map<String, String> query) {
        String q = query.get("q");
        try { return q == null ? null : query(new String(crypt.open(Base64.getUrlDecoder().decode(q)), StandardCharsets.UTF_8)); }
        catch (IOException | IllegalArgumentException e) { return null; }
    }

    /**
     * A file sent up made a new notebook ({@link LiveBooks#add}), sealed as {@link #book}'s asks: its name in the sealed
     * query ({@code q=}, name=), the file sealed, the answer its new path sealed. Not sealed with the key 403, too large
     * 413, not one or unreadable 415, failed 500; nothing written unless it opens.
     */
    private void added(Socket s, InputStream in, long length, Map<String, String> query) throws IOException {
        if (length <= 0 || length > LiveBooks.MOST_ADDED + LiveCrypt.NONCE + LiveCrypt.TAG) { answer(s, in, 413); return; }
        Map<String, String> asked = asked(query);
        if (asked == null) { answer(s, in, 403); return; }
        byte[] body = new byte[(int) length];
        new DataInputStream(in).readFully(body);
        try { body = crypt.open(body); } catch (IOException e) { answer(s, in, 403); return; }
        String path;
        try { path = books.add(asked.get("name"), body); } catch (IOException | RuntimeException | OutOfMemoryError e) { answer(s, in, 500); return; }
        if (path == null) answer(s, in, 415);
        else answer(s, in, 200, "application/octet-stream", crypt.seal(path.getBytes(StandardCharsets.UTF_8)));
    }

    static String type(String file) {
        if (file.endsWith(".html")) return "text/html; charset=utf-8";
        if (file.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (file.endsWith(".wasm")) return "application/wasm";
        if (file.endsWith(".ttf")) return "font/ttf";
        if (file.endsWith(".xml")) return "application/xml";
        if (file.endsWith(".png")) return "image/png";
        if (file.endsWith(".pdf")) return "application/pdf";
        if (file.endsWith(".psd")) return "image/vnd.adobe.photoshop";
        return "application/octet-stream";
    }

    private static void answer(Socket s, InputStream in, int code) throws IOException {
        answer(s, in, code, "text/plain", words(code).getBytes(StandardCharsets.US_ASCII));
    }
    private static String words(int code) {
        return code == 200 ? "OK" : code == 400 ? "Bad Request" : code == 403 ? "Forbidden" : code == 413 ? "Payload Too Large"
                : code == 415 ? "Unsupported Media Type" : code == 500 ? "Internal Server Error" : "Not Found";
    }

    /**
     * Writes the answer, then reads what the client still sends (a refused body, at most 64 MB) until it closes: closing
     * with it unread resets the connection, and the client sees no answer (relay.py drains for the same reason).
     */
    private static void answer(Socket s, InputStream in, int code, String type, byte[] body) throws IOException {
        OutputStream out = s.getOutputStream();
        out.write(("HTTP/1.0 " + code + " " + words(code) + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(body); out.flush();
        s.shutdownOutput();
        s.setSoTimeout(5000);
        byte[] b = new byte[65536];
        try { for (long left = 64 << 20, n; left > 0 && (n = in.read(b)) > 0; ) left -= n; } catch (IOException ignored) {}
    }

    /** The request line and headers, up to the blank line; null when the client closed or sent too much first. */
    private static String head(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int last4 = 0;
        for (int c; (c = in.read()) >= 0; ) {
            b.write(c);
            last4 = last4 << 8 | c;
            if (last4 == 0x0d0a0d0a) return new String(b.toByteArray(), 0, b.size() - 4, StandardCharsets.ISO_8859_1);
            if (b.size() > MOST_HEAD) return null;
        }
        return null;
    }

    private static Map<String, String> query(String q) {
        Map<String, String> m = new HashMap<>();
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            try { m.put(URLDecoder.decode(part.substring(0, eq), "UTF-8"), URLDecoder.decode(part.substring(eq + 1), "UTF-8")); }
            catch (UnsupportedEncodingException | IllegalArgumentException ignored) {}
        }
        return m;
    }

    private static void close(Socket s) { try { s.close(); } catch (IOException ignored) {} }
}
