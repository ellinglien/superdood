"""The probe relay: a change sent reaches a listener, numbered, as base64; too large refused; only its own files served.
The public relay (superdood.org/live/): a room a key, pack's files only, each limit, nothing printed."""
import base64, hashlib, contextlib, io, json, os, re, shutil, subprocess, tempfile, threading, time, unittest, urllib.error, urllib.request
import relay
REPO = os.path.join(relay.HERE, "..", "..")

class RelayTest(unittest.TestCase):
    def setUp(self):
        relay.reset()
        self.server = relay.serve(0)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.url = "http://127.0.0.1:%d" % self.server.server_address[1]
    def tearDown(self):
        self.server.shutdown()

    def send(self, body, sender="0a1b2c3d4e5f6071", more=""):
        return urllib.request.urlopen(urllib.request.Request(self.url + "/send?from=" + sender + more, data=body, method="POST"), timeout=5).read()

    def listen(self, query=""):
        listen = urllib.request.urlopen(self.url + "/listen" + query, timeout=5)
        self.assertEqual(listen.readline(), b": hello\n")
        listen.readline()
        return listen

    def test_passes_a_change_on_with_its_sender(self):
        listen = self.listen()
        self.assertEqual(self.send(b"\x01\x02\x03"), b"1")
        self.assertEqual(listen.readline(), b"data: 1 0a1b2c3d4e5f6071 " + base64.b64encode(b"\x01\x02\x03") + b"\n")
        ms = re.fullmatch(rb"id: (\d+)\n", listen.readline())
        self.assertTrue(ms and abs(int(ms.group(1)) - time.time() * 1000) < 5000, "stamped with the relay's ms")

    def test_a_sender_not_an_id_is_dash(self):
        """Only LivePresence.ID, 16 hex digits: a name, an old page's "browser-…", upper case or one digit more is "-"."""
        listen = self.listen()
        for n, sender in enumerate(["x%20y", "browser-a1b2c3d4", "manta-18f2a", "0A1B2C3D4E5F6071", "0a1b2c3d4e5f60712", "0a1b2c3d4e5f607"], 1):
            self.send(b"\x09", sender=sender)
            self.assertEqual(listen.readline(), b"data: %d - " % n + base64.b64encode(b"\x09") + b"\n", sender)
            listen.readline(); listen.readline()

    def test_history_for_who_asks(self):
        self.send(b"\x01"); self.send(b"\x02", sender="9f8e7d6c5b4a3921")
        late = self.listen("?history=1")
        self.assertEqual(late.readline(), b"data: 1 0a1b2c3d4e5f6071 " + base64.b64encode(b"\x01") + b"\n")
        late.readline(); late.readline()
        self.assertEqual(late.readline(), b"data: 2 9f8e7d6c5b4a3921 " + base64.b64encode(b"\x02") + b"\n")

    def test_history_from_the_last_start(self):
        self.send(b"\x01"); self.send(b"\x02", sender="9f8e7d6c5b4a3921"); self.send(b"\x03", more="&start=1"); self.send(b"\x04", sender="9f8e7d6c5b4a3921")
        late = self.listen("?history=1")
        self.assertEqual(late.readline(), b"data: 3 0a1b2c3d4e5f6071 " + base64.b64encode(b"\x03") + b"\n", "the host's page message starts it again")
        late.readline(); late.readline()
        self.assertEqual(late.readline(), b"data: 4 9f8e7d6c5b4a3921 " + base64.b64encode(b"\x04") + b"\n")

    def test_who_listens_to_who_asks(self):
        quiet = self.listen()
        host = self.listen("?here=1&from=0a1b2c3d4e5f6071")
        self.assertEqual(host.readline(), b"event: here\n"); self.assertEqual(host.readline(), b"data: - 0a1b2c3d4e5f6071\n"); host.readline()
        guest = self.listen("?here=1&from=x%20y")
        for got in (host, guest):
            self.assertEqual(got.readline(), b"event: here\n"); self.assertEqual(got.readline(), b"data: - 0a1b2c3d4e5f6071 -\n"); got.readline()
        guest.close(); quiet.close()
        for b in (b"\x01", b"\x02"): self.send(b); time.sleep(.2)  # the relay notices one gone when writing to it fails
        lines = [host.readline() for _ in range(12)]
        self.assertIn(b"data: 0a1b2c3d4e5f6071\n", lines, "those gone leave")

    def test_too_large_refused(self):
        with self.assertRaises(urllib.error.HTTPError) as e:
            urllib.request.urlopen(urllib.request.Request(self.url + "/send", data=b"x" * (relay.MOST + 1), method="POST"), timeout=5)
        self.assertEqual(e.exception.code, 413)

    def test_serves_the_page_engine_and_scans_only(self):
        for path, has in [("/", b"BRUSHES"), ("/engine.js", b"class Stroke"), ("/textures.js", b"const TEXTURE_SRC")]:
            self.assertIn(has, urllib.request.urlopen(self.url + path, timeout=5).read())
        for path in ["/relay.py", "/probe.html", "/engine.js/../relay.py", "/modular-lab-src/port.js", "/out/mbd-engine.wasm", "/mbd-engine.wasm/../relay.py"]:
            with self.assertRaises(urllib.error.HTTPError) as e: urllib.request.urlopen(self.url + path, timeout=5)
            self.assertEqual(e.exception.code, 404)

    def test_serves_the_apps_engine_as_webassembly(self):
        self.assertIn(b"MBDEngine", urllib.request.urlopen(self.url + "/mbd-engine.js", timeout=5).read())
        if not os.path.exists(os.path.join(relay.WEB, "out", "mbd-engine.wasm")): self.skipTest("bash web/engine/build.sh first")
        wasm = urllib.request.urlopen(self.url + "/mbd-engine.wasm", timeout=5)
        self.assertEqual(wasm.headers["Content-Type"], "application/wasm")
        self.assertEqual(wasm.read(4), b"\0asm")
        for path in ["/mbd-engine.wasm-runtime.js", "/mbd-assets.bin"]: self.assertTrue(urllib.request.urlopen(self.url + path, timeout=5).read())

    def test_serves_the_guests_drawing_screen_its_faces_and_drawings(self):
        for path, has in [("/live", b"WebEngine.layout"), ("/live.js", b"G.Live"), ("/drawable/tool_eraser.xml", b"<vector"),
                          ("/drawable/shape_box_size.xml", b"<vector"), ("/drawable/ic_ph_lock.xml", b"<vector"), ("/drawable/ic_ph_dice.xml", b"<vector"), ("/drawable/ic_part_tip.xml", b"<vector"),
                          ("/m4.js", b"export function install"), ("/extras.js", b"export function install"), ("/drawable/ic_shape_oval.xml", b"<vector"), ("/drawable/tip_springy_brush.png", b"PNG")]:
            self.assertIn(has, urllib.request.urlopen(self.url + path, timeout=5).read())
        self.assertEqual(urllib.request.urlopen(self.url + "/fonts/ukulele.ttf", timeout=5).headers["Content-Type"], "font/ttf")
        for path in ["/drawable/../../AndroidManifest.xml", "/drawable/ic_eraser_block.xml", "/fonts/../font/ukulele_family.xml", "/live.html"]:
            with self.assertRaises(urllib.error.HTTPError) as e: urllib.request.urlopen(self.url + path, timeout=5)
            self.assertEqual(e.exception.code, 404)

    @unittest.skipUnless(shutil.which("node"), "needs node")
    def test_engine_files_as_studio_lists_them(self):
        listed = subprocess.run(["node", "-e", "console.log(JSON.stringify(require(process.argv[1]).engineFiles()))",
                                 os.path.join(REPO, "docs", "brush-lab", "engine-files.cjs")], capture_output=True, text=True, check=True).stdout
        self.assertEqual(json.loads(listed), relay.ENGINE)

    def test_page_brushes_are_the_first_toolbar(self):
        java = relay.read(REPO, "app", "src", "main", "java", "io", "github", "mpdairy", "monopaint", "ToolSlots.java")
        first = re.search(r"FIRST = \{(.*?)\};", java, re.S).group(1)
        want = [(n, c, float(px), float(g)) for n, c, px, g in re.findall(r'\{"\w+", "([^"]+)", "([^"]+)", "([\d.]+)", "([\d.]+)"\}', first)]
        page = re.search(r"const BRUSHES = \[(.*?)\];", relay.read(relay.HERE, "probe.html"), re.S).group(1)
        got = [(n, c, float(px), float(g)) for n, c, px, g in re.findall(r'\["([^"]+)", "([^"]+)", ([\d.]+), ([\d.]+)\]', page)]
        self.assertEqual(len(want), 10); self.assertEqual(got, want)

class PublicTest(unittest.TestCase):
    """relay.py <port> public <dir>, as at superdood.org/live/: the limits made small here."""
    A, B = "a" * 64, "b" * 64
    LIMITS = ("MOST_ROOMS", "MOST_ROOMS_EACH", "MOST_LISTENERS", "MOST_KEPT", "MOST_TRIES", "MOST_TRIES_ALL")

    def setUp(self):
        self.kept = dict(relay.FILES), {k: getattr(relay, k) for k in self.LIMITS}
        self.web = tempfile.mkdtemp()
        os.makedirs(os.path.join(self.web, "fonts"))
        for name, body in [("live.html", b"<p>live"), ("live.js", b"G.Live"), ("mbd-engine.wasm", b"\0asm"), ("fonts/a.ttf", b"\1")]:
            with open(os.path.join(self.web, name), "wb") as f: f.write(body)
        relay.reset(); relay.PUBLIC = True; relay.packed(self.web)
        self.server = relay.serve(0)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.url = "http://127.0.0.1:%d" % self.server.server_address[1]
    def tearDown(self):
        self.server.shutdown(); relay.PUBLIC = False; relay.reset()
        relay.FILES.clear(); relay.FILES.update(self.kept[0])
        for k, v in self.kept[1].items(): setattr(relay, k, v)
        shutil.rmtree(self.web)

    def send(self, key, body=b"\1", ip="1.1.1.1", more=""):
        q = urllib.request.Request(self.url + "/send?from=0a1b2c3d4e5f6071&key=" + key + more, data=body, method="POST", headers={"X-Real-IP": ip})
        try: return urllib.request.urlopen(q, timeout=5).read()
        except urllib.error.HTTPError as e: return e.code
    def listen(self, key, ip="1.1.1.1", query=""):
        try: listen = urllib.request.urlopen(urllib.request.Request(self.url + "/listen?key=" + key + query, headers={"X-Real-IP": ip}), timeout=5)
        except urllib.error.HTTPError as e: return e.code
        self.assertEqual(listen.readline(), b": hello\n"); listen.readline()
        return listen

    def test_serves_packs_files_only(self):
        for path, has, kind in [("/", b"<p>live", "text/html"), ("/live", b"<p>live", "text/html"), ("/live.js", b"G.Live", "text/javascript"),
                                ("/mbd-engine.wasm", b"\0asm", "application/wasm"), ("/fonts/a.ttf", b"\1", "font/ttf")]:
            got = urllib.request.urlopen(self.url + path, timeout=5)
            self.assertEqual((got.read(), got.headers["Content-Type"].split(";")[0]), (has, kind), path)
        for path in ["/live.html", "/engine.js", "/relay.py", "/pencil", "/fonts/../live.html"]:
            with self.assertRaises(urllib.error.HTTPError) as e: urllib.request.urlopen(self.url + path, timeout=5)
            self.assertEqual(e.exception.code, 404, path)

    def test_csp(self):
        """The page's policy: its inline scripts by hash (not one with src), never framed; only on the page."""
        page = '<script src="live.js"></script><script type="module">go()</script><script>\nx</script>'
        want = ["'sha256-%s'" % base64.b64encode(hashlib.sha256(t).digest()).decode() for t in [b"go()", b"\nx"]]
        self.assertIn("script-src 'self' %s %s 'unsafe-eval' 'wasm-unsafe-eval';" % tuple(want), relay.csp(page))
        self.assertNotIn("unsafe-inline' 'wasm", relay.csp(page))
        got = urllib.request.urlopen(self.url + "/", timeout=5).headers["Content-Security-Policy"]
        self.assertIn("frame-ancestors 'none'", got); self.assertIn("default-src 'self'", got)
        self.assertIsNone(urllib.request.urlopen(self.url + "/live.js", timeout=5).headers["Content-Security-Policy"])

    def test_a_room_a_key(self):
        a = self.listen(self.A)
        self.assertEqual(self.send(self.B, b"\2"), b"1")
        self.assertEqual(self.send(self.A, b"\3"), b"1", "numbered in its own room")
        self.assertEqual(a.readline(), b"data: 1 0a1b2c3d4e5f6071 " + base64.b64encode(b"\3") + b"\n", "only its own room's")
        for key in ["", "k7qm", "A" * 64, "a" * 63, "a" * 65]:
            self.assertEqual(self.send(key), 403, key); self.assertEqual(self.listen(key), 403, key)

    def test_tries_per_client(self):
        relay.MOST_TRIES, relay.MOST_ROOMS_EACH = 3, 100
        self.send(self.A)
        for i in range(3): self.assertEqual(self.send("%064x" % i, ip="6.6.6.6"), b"1")
        self.assertEqual(self.send("%064x" % 9, ip="6.6.6.6"), 429, "a 4th room that wasn't there, refused")
        self.assertEqual(self.listen("%064x" % 9, ip="6.6.6.6"), 429)
        self.assertEqual(self.send(self.A, ip="6.6.6.6"), b"2", "a room that is there still taken")
        self.assertEqual(self.send("%064x" % 9, ip="7.7.7.7"), b"1", "another client's own")
        self.assertEqual(self.send("%064x" % 8, ip="2001:db8:1:2:3::1"), b"1")
        for i in range(2): self.send("%064x" % (20 + i), ip="2001:db8:1:2:4::%d" % i)
        self.assertEqual(self.send("%064x" % 30, ip="2001:db8:1:2:ffff::1"), 429, "an IPv6 address by its /64")
        self.assertNotIn(b"6.6.6.6", repr(relay.tries).encode(), "hashed")

    def test_tries_in_all(self):
        relay.MOST_TRIES_ALL, relay.MOST_ROOMS_EACH = 4, 100
        for i in range(4): self.send("%064x" % i, ip="9.9.9.%d" % i)
        self.assertEqual(self.send("%064x" % 5, ip="9.9.9.200"), 429)

    def test_rooms_capped(self):
        relay.MOST_ROOMS, relay.MOST_ROOMS_EACH = 3, 2
        self.assertEqual([self.send(k) for k in ("%064x" % 1, "%064x" % 2, "%064x" % 3)], [b"1", b"1", 503], "2 rooms a client")
        self.assertEqual([self.send("%064x" % 4, ip="2.2.2.2"), self.send("%064x" % 5, ip="3.3.3.3")], [b"1", 503], "3 in all")

    def test_listeners_capped(self):
        relay.MOST_LISTENERS = 2
        held = [self.listen(self.A), self.listen(self.A)]
        self.assertEqual(self.listen(self.A), 503)
        self.assertNotEqual(self.listen(self.B), 503, "another room's own")

    def test_history_capped(self):
        relay.MOST_KEPT = 300
        self.send(self.A, b"x" * 100, more="&start=1"); self.send(self.A, b"y" * 100)
        live = self.listen(self.A)
        self.send(self.A, b"z" * 100)
        self.assertIn(base64.b64encode(b"z" * 100), live.readline(), "past the cap still passed on")
        self.assertEqual(relay.rooms[self.A].kept, len(relay.rooms[self.A].history[0]), "but not kept")
        self.send(self.A, b"w" * 100, more="&start=1")
        self.assertEqual(len(relay.rooms[self.A].history), 1, "a start keeps again")
        self.assertEqual(self.send(self.A, b"x" * (relay.MOST + 1)), 413)

    def test_idle_rooms_go(self):
        self.send(self.A); self.send(self.B)
        relay.rooms[self.A].used -= relay.IDLE_S + 1
        self.send("c" * 64)
        self.assertEqual(sorted(relay.rooms), [self.B, "c" * 64])

    def test_a_guess_left_empty_goes(self):
        relay.STILL_S = .1  # writing to a closed one fails at its next line or so: its heartbeat, soon
        try:
            guess = self.listen(self.A); guess.close()
            for _ in range(50):
                if self.A not in relay.rooms: break
                time.sleep(.1)
        finally: relay.STILL_S = 10
        self.assertNotIn(self.A, relay.rooms)

    def test_nothing_printed(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.listen(self.A).close(); self.send(self.A); self.send("%064x" % 1, ip="4.4.4.4")
        self.assertEqual(out.getvalue(), "")

if __name__ == "__main__":
    unittest.main()
