"""Live pages' probe relay (docs/superpowers/specs/2026-10-31-live-pages-design.md, build step 1). On the Mac only,
reached by the Manta over USB (adb reverse tcp:8765 tcp:8765).
GET /live    the drawing screen a live page's guest opens (live.html: the Manta's column, its tools, its gestures), with
             /live.js (what every live page shares), /m4.js (the rest of the drawing screen: menus, Play, Shapes, the Lasso), /stamp.js (the Stamp, Cut Stamp, Import Image), /extras.js (Brush Lab's Open and picker box, Grey Shift's set, Show Kinds, the sand timer, the wet indicator), /fonts/<face>.ttf (the app's faces) and /drawable/<name>.xml (the
             app's drawings and shapes, res/drawable: the tray's tips, the eraser, the picker box, the icons, the parts' glyphs;
             /drawable/<name>.png, a drawing that is a picture, res/drawable-nodpi: Springy brush's and the loading screen's)
GET /        the probe page (probe.html); /pencil the Pencil curves side by side (pencil.html); /engine.js and /textures.js, Brush Studio's engine and paper scans (step 2b);
             /mbd-engine.js, /mbd-engine.wasm, /mbd-engine.wasm-runtime.js, /mbd-assets.bin: the app's engine as
             WebAssembly (web/engine; bash web/engine/build.sh builds the last three, 404 until then)
POST /send?from=<id>  the body, a deflated LiveChange, numbered and passed to every listener; answers its number
             (&start=1: the history starts again here, the host's page message before its page sent whole, as LiveHost)
GET /listen[?history=1] server-sent events: one `data: <number> <sender> <base64>` line per change, then `id: <ms>`,
             when the relay had it, ms since the epoch (an id: listeners that read only data lines read on as before)
             (history=1: all earlier ones first, from the last start=1). A sender's id is 16 hex digits (LivePresence.ID: random,
             new each session, never a name; the host's is marked with the link's key, which only guests can check); any other is `-`.
             After 10 s with nothing else, `event: still` and a bare `data` line (LivePresence.STILL_LINE): a guest
             hearing nothing for 25 s listens again (a line gone silent).
GET /listen?here=1&from=<id>  the same, and who listens (LivePresence.java): `event: here` then `data: <ids>`, every
             listener's id space apart (`-` for one without one), whenever one comes or goes; as LiveHost
Prints a line per change: its number, sender, bytes and ms since the epoch.
python3 relay.py [port] [lan]   lan: listen on every address, for a phone or iPad on the same Wi-Fi
python3 relay.py pack <dir>     the live page's files written to <dir> at the paths it asks for (/live as live.html):
             what the app's own relay serves (LiveHost, Share on Wi-Fi), packed into its assets by app/build.gradle
python3 relay.py <port> public <dir>   the relay at superdood.org/live/ (Draw Together off the Wi-Fi; superdood.org's server notes (not in this repo)): on 127.0.0.1
             behind nginx, serving only <dir> (pack's files; / is live.html). Each link's key is a room of its own, by
             its access hash (?key=, 64 hex, else 403), with the limits below; a room asked for that isn't there is made
             and counts as a try for the client (X-Real-IP, hashed with a salt new each start, in memory only). Nothing
             is printed: no addresses, no changes. Copied alone to the server: it reads nothing else of the repo there.
             Its page goes with a Content-Security-Policy (CSP) naming its inline script by hash, made from the file served.
"""
import base64, collections, hashlib, http.server, os, queue, re, socket, sys, threading, time
from urllib.parse import urlsplit, parse_qs

MOST = 4 << 20
# The public relay's limits (public): rooms at once, and made by one client; listeners in a room; bytes of history a room
# keeps (from its last start=1; at most MOST_ROOMS of them, 400 MB); a room no one listens to goes this long after its last
# change (LiveShare.KEPT_MS: a Manta back within it finds its room); rooms asked for that weren't there (a wrong key's, a
# guess's), a client's and everyone's, in TRIES_S.
MOST_ROOMS, MOST_ROOMS_EACH, MOST_LISTENERS, MOST_KEPT, IDLE_S = 50, 5, 16, 8 << 20, 2 * 3600
MOST_TRIES, MOST_TRIES_ALL, TRIES_S = 20, 1000, 600
PUBLIC = False
STILL_S = 10  # LivePresence.STILL_MS: the heartbeat (STILL_LINE) after this long with nothing else
HERE = os.path.dirname(os.path.abspath(__file__))
LAB = os.path.join(HERE, "..", "..", "docs", "brush-lab", "prototypes")
WEB = os.path.join(HERE, "..", "..", "web", "engine")
RES = os.path.join(HERE, "..", "..", "app", "src", "main", "res")
# Brush Studio's engine files in engine-files.cjs's order (test_relay checks they agree), read as they stand, as Studio's build.
ENGINE = ["port.js", "oldmodel.js", "engine.js", "stroke.js", "float.js", "parts.js", "checks.js", "newmods.js", "voice.js", "wet.js", "planes.js"]

def read(*path):
    with open(os.path.join(*path), encoding="utf-8") as f: return f.read()
def raw(*path): return lambda: open(os.path.join(*path), "rb").read()
def engine(): return ("\"use strict\";\n" + "\n;\n".join(read(LAB, "modular-lab-src", f) for f in ENGINE)).encode()
def textures(): return re.search(r"const TEXTURE_SRC = \{[^\n]*\};", read(LAB, "modular-lab.html")).group(0).encode()
# The only files served: nothing from the request reaches a path.
FILES = {"/": ("text/html; charset=utf-8", lambda: read(HERE, "probe.html").encode()),
         "/pencil": ("text/html; charset=utf-8", lambda: read(HERE, "pencil.html").encode()),
         "/engine.js": ("text/javascript; charset=utf-8", engine), "/textures.js": ("text/javascript; charset=utf-8", textures),
         "/mbd-engine.js": ("text/javascript; charset=utf-8", raw(WEB, "engine.js")),
         "/mbd-engine.wasm": ("application/wasm", raw(WEB, "out", "mbd-engine.wasm")),
         "/mbd-engine.wasm-runtime.js": ("text/javascript; charset=utf-8", raw(WEB, "out", "mbd-engine.wasm-runtime.js")),
         "/mbd-assets.bin": ("application/octet-stream", raw(WEB, "out", "mbd-assets.bin")),
         "/live": ("text/html; charset=utf-8", raw(HERE, "live.html")), "/live.js": ("text/javascript; charset=utf-8", raw(HERE, "live.js")),
         "/m4.js": ("text/javascript; charset=utf-8", raw(HERE, "m4.js")), "/books.js": ("text/javascript; charset=utf-8", raw(HERE, "books.js")),
         "/extras.js": ("text/javascript; charset=utf-8", raw(HERE, "extras.js")),
         "/stamp.js": ("text/javascript; charset=utf-8", raw(HERE, "stamp.js"))}
# The app's faces and drawings, listed once here from the names on disk, never the request's.
if os.path.isdir(RES):  # not on the server (public), where only relay.py and pack's files are
    FILES.update({"/fonts/" + f: ("font/ttf", raw(RES, "font", f)) for f in os.listdir(os.path.join(RES, "font")) if f.endswith(".ttf")})
    FILES.update({"/drawable/" + f: ("application/xml", raw(RES, "drawable", f)) for f in os.listdir(os.path.join(RES, "drawable"))
                  if re.fullmatch(r"((tip_|tool_|shape_box_|ic_ph_|ic_part_|ic_cover_|layer_)[a-z0-9_]*|ic_finger|ic_shape_oval|ic_brush_pen|ic_sand_timer|ic_drop_dashed|ic_hair_dryer)\.xml", f)})
    FILES.update({"/drawable/" + f: ("image/png", raw(RES, "drawable-nodpi", f)) for f in os.listdir(os.path.join(RES, "drawable-nodpi")) if re.fullmatch(r"(tip_[a-z0-9_]*|splash_drawing)\.png", f)})
TYPES = {".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".wasm": "application/wasm", ".ttf": "font/ttf",
         ".xml": "application/xml", ".png": "image/png"}  # LiveHost.type's

def packed(out):
    """Only pack's files in <out>, listed once from the names on disk (the public relay's; / and /live are live.html)."""
    FILES.clear()
    for d, _, names in os.walk(out):
        for f in names:
            path = os.path.join(d, f)
            FILES["/" + os.path.relpath(path, out).replace(os.sep, "/")] = (TYPES.get(os.path.splitext(f)[1], "application/octet-stream"), raw(path))
    FILES["/"] = FILES["/live"] = FILES.pop("/live.html")
    global CSP
    CSP = csp(FILES["/"][1]().decode("utf-8"))

CSP = None  # the public page's policy (packed): only its own files, its inline script by hash, nothing framing it
def csp(page):
    hashes = " ".join("'sha256-%s'" % base64.b64encode(hashlib.sha256(m.encode()).digest()).decode()
                      for m in re.findall(r"<script(?![^>]*\bsrc=)[^>]*>(.*?)</script>", page, re.S))
    # 'unsafe-eval': TeaVM's runtime (mbd-engine.wasm-runtime.js) makes its JS glue with new Function.
    return ("default-src 'self'; script-src 'self' %s 'unsafe-eval' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; "
            "worker-src blob:; connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'" % hashes)

class Room:
    def __init__(self, key, who):  # who made it; listeners: queue -> name; kept: the history's bytes; used: its last change, s
        self.key, self.who, self.listeners, self.asking, self.history, self.kept, self.count, self.used = key, who, {}, set(), [], 0, 0, time.time()

rooms, lock = {}, threading.Lock()  # access hash -> Room; the Mac's relay (not public) has the one, ""
tries = collections.defaultdict(collections.deque)  # client's hash -> when it asked for a room that wasn't there
SALT = os.urandom(16)

def client(handler):
    """Who asks, for the tries' limit: nginx's X-Real-IP (an IPv6 address by its /64), salted and hashed, never kept plain."""
    ip = handler.headers.get("X-Real-IP") or handler.client_address[0]
    if ":" in ip: ip = ":".join(ip.split(":")[:4])
    return hashlib.sha256(SALT + ip.encode()).digest()[:12]

def room(key, who):
    """The room for key, made when it isn't there (a try for who); else the refusal's code. Holding the lock."""
    if not PUBLIC: return rooms.get("") or rooms.setdefault("", Room("", who))
    if not re.fullmatch(r"[0-9a-f]{64}", key): return 403
    if key in rooms: return rooms[key]
    now = time.time()
    for k in [k for k, r in rooms.items() if not r.listeners and r.used < now - IDLE_S]: del rooms[k]
    for w in list(tries):
        while tries[w] and tries[w][0] < now - TRIES_S: tries[w].popleft()
        if not tries[w]: del tries[w]
    if len(tries.get(who, ())) >= MOST_TRIES or sum(map(len, tries.values())) >= MOST_TRIES_ALL: return 429
    tries[who].append(now)
    if len(rooms) >= MOST_ROOMS or sum(r.who == who for r in rooms.values()) >= MOST_ROOMS_EACH: return 503
    rooms[key] = r = Room(key, who)
    return r

def name(query):  # a sender's or listener's id, LivePresence.ID; an old page's "browser-…" is "-"
    given = (parse_qs(query).get("from") or ["-"])[0]
    return given if re.fullmatch(r"[0-9a-f]{16}", given) else "-"

def present(r):  # who listens, to each that asked; holding the lock
    line = b"event: here\ndata: %s\n\n" % " ".join(r.listeners.values()).encode()
    for q in r.asking: q.put(line)

def reset():
    with lock: rooms.clear(); tries.clear()

class Relay(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"

    def do_GET(self):
        query = urlsplit(self.path)
        if query.path in FILES:
            try: kind, body = FILES[query.path][0], FILES[query.path][1]()
            except FileNotFoundError: self.send_error(404); return
            self.send_response(200); self.send_header("Content-Type", kind)
            if CSP and kind.startswith("text/html"): self.send_header("Content-Security-Policy", CSP)
            self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body); return
        if query.path != "/listen": self.send_error(404); return
        q, asked = queue.Queue(), parse_qs(query.query)
        with lock:
            r = room((asked.get("key") or [""])[0], client(self))
            refused = r if isinstance(r, int) else 503 if len(r.listeners) >= MOST_LISTENERS else None
            if not refused:
                if asked.get("history") == ["1"]:
                    for line in r.history: q.put(line)
                r.listeners[q] = name(query.query)
                if asked.get("here") == ["1"]: r.asking.add(q)
                present(r)
        if refused: self.send_error(refused); return
        self.send_response(200); self.send_header("Content-Type", "text/event-stream"); self.send_header("Cache-Control", "no-cache")
        self.send_header("X-Accel-Buffering", "no"); self.end_headers()  # nginx passes each line on at once
        try:
            self.wfile.write(b": hello\n\n"); self.wfile.flush()
            while True:
                try: line = q.get(timeout=STILL_S)
                except queue.Empty: line = b"event: still\ndata\n\n"
                self.wfile.write(line); self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError): pass
        finally:
            with lock:
                r.listeners.pop(q, None); r.asking.discard(q); present(r)
                if PUBLIC and not r.listeners and not r.history and rooms.get(r.key) is r: del rooms[r.key]  # a guess's, left empty

    def do_POST(self):
        query = urlsplit(self.path)
        if query.path != "/send": self.send_error(404); return
        n = int(self.headers.get("Content-Length") or 0)
        if not 0 < n <= MOST:
            left = min(n, 64 << 20)  # drain, or the client sees a reset, not 413
            while left > 0: left -= len(self.rfile.read(min(left, 65536)) or b"\0" * left)
            self.send_error(413); return
        body = self.rfile.read(n)
        sender, asked = name(query.query), parse_qs(query.query)
        with lock:
            r = room((asked.get("key") or [""])[0], client(self))
            if isinstance(r, Room):
                r.count += 1
                number, r.used = r.count, time.time()
                ms = int(r.used * 1000)
                line = b"data: %d %s %s\nid: %d\n\n" % (number, sender.encode(), base64.b64encode(body), ms)
                if asked.get("start") == ["1"]: r.history.clear(); r.kept = 0
                # shortcut: past MOST_KEPT a change still reaches every listener but isn't kept, so a guest joining misses
                # it until the Manta's next page whole (a turn); upgrade if a long session on one page shows it.
                if r.kept + len(line) <= MOST_KEPT: r.history.append(line); r.kept += len(line)
                for q in r.listeners: q.put(line)
        if not isinstance(r, Room): self.send_error(r); return
        if not PUBLIC: print("%d %s %d bytes %d" % (number, sender, n, ms), flush=True)
        self.send_response(200); self.send_header("Content-Type", "text/plain"); self.end_headers(); self.wfile.write(str(number).encode())

    def log_message(self, *args): pass

def pack(out):
    for url, (kind, body) in FILES.items():
        if url in ("/", "/pencil", "/textures.js"): continue  # the probe's pages, not the live page's
        name = os.path.join(out, "live.html" if url == "/live" else url[1:])
        os.makedirs(os.path.dirname(name), exist_ok=True)
        with open(name, "wb") as f: f.write(body())

def serve(port=8765, host="127.0.0.1"):
    server = http.server.ThreadingHTTPServer((host, port), Relay)
    server.daemon_threads = True
    return server

if __name__ == "__main__":
    if sys.argv[1:2] == ["pack"]: pack(sys.argv[2]); sys.exit()
    if sys.argv[2:3] == ["public"]: PUBLIC = True; packed(sys.argv[3])
    host = "0.0.0.0" if "lan" in sys.argv[2:] else "127.0.0.1"
    server = serve(int(sys.argv[1]) if len(sys.argv) > 1 else 8765, host)
    port = server.server_address[1]
    print("relay: http://127.0.0.1:%d" % port, flush=True)
    if host != "127.0.0.1":
        try: print("on the Wi-Fi: http://%s:%d" % (socket.gethostbyname(socket.gethostname()), port))
        except OSError: pass
    server.serve_forever()
