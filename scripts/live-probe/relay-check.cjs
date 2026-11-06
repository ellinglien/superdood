// Draw Together through superdood.org's relay, on this Mac: relay.py public serving pack's files, behind a stand-in for
// superdood.org's nginx (superdood.nginx: /live/ to the relay, the prefix dropped, X-Real-IP set, /live to /live/),
// a stand-in Manta on the JVM (LiveHostChecks relayed: the Manta's side of a live page in plain Java, no host of its own)
// and a guest in headless Chrome (puppeteer-core from ~/node_modules) at /live/#<10-letter key>, as the QR code opens
// it. Checks: the page and its files from the relay's prefix; the Manta's page on joining; the Manta's mark reaches the
// guest, the guest's stroke the Manta; a typed /live keeps the key; the page under its CSP (no violations); a request without the key's access refused; no
// request carries a name (every one the relay gets is kept: each from= an opaque id, the host's told only with the key).
//   bash web/engine/build.sh && node scripts/live-probe/relay-check.cjs [port]   (the relay on port, nginx's on port + 1)
"use strict";
const { spawn, execFileSync } = require("child_process"), path = require("path"), os = require("os"), fs = require("fs"), http = require("http"), readline = require("readline");
const puppeteer = require(path.join(os.homedir(), "node_modules", "puppeteer-core"));
const CHROME = process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const REPO = path.join(__dirname, "..", ".."), PORT = +(process.argv.slice(2).find(a => /^\d+$/.test(a)) || 8900), KEY = "k7qmw2xrh4";
const sleep = ms => new Promise(r => setTimeout(r, ms));
let n = 0, fail = 0;
const ok = (c, what) => { n++; if (!c) fail++; console.log((c ? "ok   " : "FAIL ") + what); };
const java = p => path.join(process.env.JAVA_HOME || "/Applications/Android Studio.app/Contents/jbr/Contents/Home", "bin", p);
const work = fs.mkdtempSync(path.join(os.tmpdir(), "relay-check-"));
const get = url => new Promise((res, rej) => http.get(url, a => { const b = []; a.on("data", d => b.push(d)); a.on("end", () => res({ status: a.statusCode, headers: a.headers, body: Buffer.concat(b) })); }).on("error", rej));

// superdood.org's nginx, as superdood.nginx has it: streamed both ways (proxy_buffering off), the client's address as X-Real-IP.
const asked = [];  // every request's path and query, as the relay gets them
const nginx = http.createServer((q, a) => {
  asked.push(q.url);
  if (q.url === "/live") { a.writeHead(301, { Location: "/live/" }); a.end(); return; }
  if (!q.url.startsWith("/live/")) { a.writeHead(404); a.end(); return; }
  const up = http.request({ host: "127.0.0.1", port: PORT, method: q.method, path: q.url.slice("/live".length),
    headers: { ...q.headers, "x-real-ip": q.socket.remoteAddress } }, r => { a.writeHead(r.statusCode, r.headers); r.pipe(a); });
  up.on("error", () => { a.writeHead(502); a.end(); });
  q.pipe(up);
  a.on("close", () => up.destroy());
});

(async () => {
  execFileSync(java("javac"), ["-nowarn", "-d", work, "-sourcepath", path.join(REPO, "app", "src", "main", "java"), path.join(REPO, "app", "src", "test", "java", "io", "github", "mpdairy", "monopaint", "LiveHostChecks.java")], { stdio: ["ignore", "ignore", "inherit"] });
  execFileSync("python3", [path.join(__dirname, "relay.py"), "pack", path.join(work, "web")]);
  const relay = spawn("python3", [path.join(__dirname, "relay.py"), String(PORT), "public", path.join(work, "web")], { stdio: ["ignore", "pipe", "inherit"] });
  let relayOut = "";
  relay.stdout.on("data", d => relayOut += d);
  await new Promise(r => nginx.listen(PORT + 1, "127.0.0.1", r));
  const at = `http://127.0.0.1:${PORT + 1}/live`;
  await sleep(700);
  let manta = null, lines = [];
  const b = await puppeteer.launch({ executablePath: CHROME, headless: "new", args: ["--disable-gpu", "--disable-background-timer-throttling", "--disable-renderer-backgrounding"], protocolTimeout: 120000 });
  const errs = [];
  try {
    const page = await get(at + "/"), wasm = await get(at + "/mbd-engine.wasm");
    ok(page.status === 200 && /WebEngine/.test(page.body) && wasm.headers["content-type"] === "application/wasm" && (await get(at + "/relay.py")).status === 404,
      "the page and its engine from /live/ (wasm as application/wasm), nothing else");
    ok(/script-src 'self' 'sha256-[A-Za-z0-9+/]+=' 'unsafe-eval' 'wasm-unsafe-eval';.*frame-ancestors 'none'/.test(page.headers["content-security-policy"] || ""), "the page with its CSP: its inline script by hash, never framed");
    ok((await get(at + "/listen")).status === 403 && (await get(at + "/listen?key=" + KEY)).status === 403, "listen without the key's access, or with the key itself, refused");
    const typed = await get(at);
    ok(typed.status === 301 && typed.headers.location === "/live/", "a typed /live goes to /live/");

    const clients = asked.length;  // the requests above are this check's own
    manta = spawn(java("java"), ["-cp", work, "io.github.mpdairy.monopaint.LiveHostChecks", "relayed", at, KEY, path.join(work, "library")], { stdio: ["pipe", "pipe", "inherit"], cwd: REPO });
    readline.createInterface({ input: manta.stdout }).on("line", l => { if (l.startsWith("{")) lines.push(JSON.parse(l)); });
    const state = async () => {
      lines = lines.filter(l => !("path" in l)); manta.stdin.write("state\n");
      for (let t = 0; t < 100; t++) { const s = lines.find(l => "path" in l); if (s) return s; await sleep(50); }
      throw new Error("no state");
    };
    await sleep(2500);  // the stretch, then the Manta's page whole

    const p = await b.newPage();
    p.on("console", m => { if ((m.type() === "error" || m.type() === "warning") && !/^Failed to load resource/.test(m.text())) errs.push(m.text()); });
    p.on("pageerror", e => errs.push("pageerror: " + e.message));
    await p.setViewport({ width: 390, height: 844, deviceScaleFactor: 3 });
    await p.goto(`${at}#${KEY}`, { waitUntil: "load" });  // typed without the slash: the browser keeps the key over the 301
    await p.waitForFunction(() => window.guest && guest.ready && /^Connected/.test(document.getElementById("note").textContent), { timeout: 60000 });
    ok(await p.evaluate(k => location.pathname === "/live/" && location.hash === "#" + k, KEY), "the guest at /live/ with the key after its #, Connected");
    const dark = () => p.evaluate(() => { let k = 0; for (const g of guest.S) if (g < 250) k++; return k; });
    const m0 = await state();
    for (let t = 0; t < 40 && Math.abs(await dark() - m0.marks) >= 60; t++) await sleep(150);
    ok(m0.marks > 1000 && Math.abs(await dark() - m0.marks) < 60, `on joining, the Manta's page (${m0.marks} marks there, ${await dark()} here)`);

    lines = lines.filter(l => !("edited" in l)); manta.stdin.write("draw\n");
    const m1 = await (async () => { for (let t = 0; t < 100 && !lines.some(l => "edited" in l); t++) await sleep(50); return state(); })();
    for (let t = 0; t < 40 && Math.abs(await dark() - m1.marks) >= 60; t++) await sleep(150);
    ok(m1.marks > m0.marks + 200 && Math.abs(await dark() - m1.marks) < 60, `the Manta's mark reaches the guest (${m0.marks} → ${m1.marks} there, ${await dark()} here)`);

    const cdp = await p.target().createCDPSession();
    const s = await p.evaluate(() => { const r = document.getElementById("view").getBoundingClientRect(), V = guest.V, d = devicePixelRatio; return [r.x + V.x / d, r.y + V.y / d, guest.W * V.s / d, guest.H * V.s / d]; }), t0 = Date.now() / 1000;
    const ev = (type, k, force) => cdp.send("Input.dispatchMouseEvent", { type, x: s[0] + s[2] * (.2 + .6 * k / 24), y: s[1] + s[3] * (.75 + .05 * Math.sin(k / 3)), button: "left", buttons: type === "mouseReleased" ? 0 : 1, clickCount: 1, pointerType: "pen", force, timestamp: t0 + k * .008 });
    await ev("mousePressed", 0, .3);
    for (let k = 1; k <= 24; k++) { await ev("mouseMoved", k, .7); await sleep(8); }
    await ev("mouseReleased", 24, 0);
    let m2 = null; for (let t = 0; t < 60; t++) { m2 = await state(); if (m2.laid > m1.laid) break; await sleep(150); }
    ok(m2.laid > m1.laid && m2.marks > m1.marks + 200, `the guest's stroke reaches the Manta (${m1.marks} → ${m2.marks} marks)`);
    // What the relay learns of who is here (the owner, 2026-11-03): ids, random each session, never a name.
    const LC = require("./live-crypt.cjs"), live = asked.slice(clients).filter(u => /^\/live\/(send|listen)\?/.test(u));
    const froms = live.map(u => new URLSearchParams(u.split("?")[1]).get("from")), ids = [...new Set(froms)];
    const hosts = ids.filter(i => i && LC.hostId(KEY, i.slice(0, 8)) === i);
    ok(live.length >= 3 && froms.every(f => /^[0-9a-f]{16}$/.test(f)) && !asked.slice(clients).some(u => /manta|browser|guest/i.test(u)),
      `every request the relay got carries an opaque id, no name (${live.length} sends and listens: ${ids.join(", ")})`);
    ok(ids.length === 2 && hosts.length === 1, "two ids, the Manta's and the guest's; only the key tells which is the host's");
    ok(relayOut.trim() === "relay: http://127.0.0.1:" + PORT, "the relay printed nothing but its start: " + JSON.stringify(relayOut.trim()));
    ok(errs.length === 0, "no errors in the page" + (errs.length ? ": " + errs.join(" | ") : ""));
  } finally {
    await b.close(); if (manta) manta.kill(); relay.kill(); nginx.close(); nginx.closeAllConnections && nginx.closeAllConnections();
    fs.rmSync(work, { recursive: true, force: true });
  }
  console.log(fail ? `${fail} of ${n} FAILED` : `${n} of ${n} ok`);
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
