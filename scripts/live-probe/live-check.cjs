// The guest's drawing screen (live.html, the browser app's M1) without the Manta: relays of its own on other ports,
// a stand-in host's tools (WebEngine.hostTools, the selected slot and page size changed in its plain lines), two guests in
// headless Chrome (puppeteer-core from ~/node_modules): an iPad portrait with a pen, a phone portrait with a finger.
// Checks: the host's tools and page size arrive; the column lies where DrawLayout (on the JVM, compiled here) puts it on
// both screens; each guest's stroke reaches the other, sent in parts while drawn (pen down and lift to shown timed), one Undo step; a change arriving while another draws itself on laid at once; a two-finger tap takes back your own, three fingers put it back; the eraser; the hover ring;
// pinch, and Zoom's tap back to the whole page; the host's kaleidoscope mirrors a guest's stroke; the Manta's own stroke (the JVM's
// parts, LiveChangeChecks) shown on the phone while drawn and the host's page after the lift. Prints stroke timings; with --shots, the
// screenshots in docs/archive/pictures/live/m1-*.png. With --host, the same through the Manta's own relay (LiveHost, Share on
// Wi-Fi) on the JVM, serving relay.py pack's files, the link's key after its #; and a request without the key refused.
//   bash web/engine/build.sh && node scripts/live-probe/live-check.cjs [port] [--shots] [--host]
"use strict";
const { spawn, execFileSync } = require("child_process"), path = require("path"), fs = require("fs"), os = require("os"), http = require("http"), zlib = require("zlib");
const puppeteer = require(path.join(os.homedir(), "node_modules", "puppeteer-core"));
const CHROME = process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const REPO = path.join(__dirname, "..", ".."), args = process.argv.slice(2), PORT = +(args.find(a => /^\d+$/.test(a)) || 8767), SHOTS = args.includes("--shots"), HOST = args.includes("--host"), KEY = HOST ? "k7qmw2xrh4tnpd9e" : "";
const IPAD = [820, 1180, 2], IPAD_WIDE = [1180, 820, 2], PHONE = [390, 844, 3], PAGE = [1600, 2000];
const sleep = ms => new Promise(r => setTimeout(r, ms));
let n = 0, fail = 0;
const ok = (c, what) => { n++; if (!c) fail++; console.log((c ? "ok   " : "FAIL ") + what); };
const pct = (a, q) => { const s = [...a].sort((x, y) => x - y); return s.length ? s[Math.min(s.length - 1, Math.floor(q * s.length))] : NaN; };

// DrawLayout's numbers on the JVM, from the app's own sources (javac pulls in only what it needs).
function jvmLayout(screens) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "live-check-")), pkg = path.join(dir, "io", "github", "mpdairy", "monopaint");
  fs.mkdirSync(pkg, { recursive: true });
  fs.writeFileSync(path.join(pkg, "LayoutNumbers.java"), `package io.github.mpdairy.monopaint;
public final class LayoutNumbers { public static void main(String[] a) { for (int i = 0; i < a.length; i += 2) { DrawLayout d = new DrawLayout(Integer.parseInt(a[i]), Integer.parseInt(a[i + 1]));
  StringBuilder s = new StringBuilder("{\\"side\\":" + d.side + ",\\"slot\\":" + d.slot + ",\\"trays\\":" + d.trays + ",\\"pageBar\\":" + d.pageBar() + ",\\"pageWidth\\":" + d.pageWidth() + ",\\"canvasHeight\\":" + d.canvasHeight() + ",\\"box\\":" + d.box + ",\\"layers\\":" + d.layers + ",\\"tops\\":[");
  int[] t = d.tops(); for (int k = 0; k < t.length; k++) s.append(k > 0 ? "," : "").append(t[k]); System.out.println(s.append("]}")); } } }`);
  const java = p => path.join(process.env.JAVA_HOME || "/Applications/Android Studio.app/Contents/jbr/Contents/Home", "bin", p);
  execFileSync(java("javac"), ["-nowarn", "-d", dir, "-sourcepath", path.join(REPO, "app", "src", "main", "java"), path.join(pkg, "LayoutNumbers.java")], { stdio: ["ignore", "ignore", "inherit"] });
  const out = execFileSync(java("java"), ["-cp", dir, "io.github.mpdairy.monopaint.LayoutNumbers", ...screens.flatMap(s => [String(s[0]), String(s[1])])], { encoding: "utf8" });
  return out.trim().split("\n").map(l => JSON.parse(l));
}

// The Manta's own stroke as LiveProbe sends it while drawn (LiveChangeChecks parts: a Verve felt stroke on a blank page
// w×h from x0, y0, on the JVM from the app's sources): {parts: base64 changes, box: [x, y, w, h], greys: base64, the host's there}.
function jvmParts(w, h, x0, y0) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "live-parts-")), java = p => path.join(process.env.JAVA_HOME || "/Applications/Android Studio.app/Contents/jbr/Contents/Home", "bin", p);
  const src = d => path.join(REPO, "app", "src", d, "java");
  try {
    execFileSync(java("javac"), ["-nowarn", "-d", dir, "-sourcepath", src("main") + path.delimiter + src("test"), path.join(src("test"), "io", "github", "mpdairy", "monopaint", "LiveChangeChecks.java")], { stdio: ["ignore", "ignore", "inherit"] });
    return JSON.parse(execFileSync(java("java"), ["-cp", dir, "io.github.mpdairy.monopaint.LiveChangeChecks", "parts", w, h, x0, y0].map(String), { cwd: REPO, encoding: "utf8", maxBuffer: 1 << 26 }));
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
}

// A stand-in host's change, sealed with the link's key (live-crypt.cjs: Node's own AES-GCM) as the Manta seals it.
const LC = require("./live-crypt.cjs"), MANTA = LC.hostId(KEY);  // its id, marked with the key as LiveProbe's
const post = (port, body, start) => new Promise((res, rej) => { const q = http.request(`http://127.0.0.1:${port}/send?key=${LC.access(KEY)}&from=${MANTA}${start ? "&start=1" : ""}`, { method: "POST" }, a => { a.resume(); a.on("end", res); }); q.on("error", rej); q.end(LC.seal(KEY, body)); });
// Every change the relay holds (history), as anyone listening on the Wi-Fi would read it: the bodies, base64 undone.
const heard = port => new Promise((res, rej) => http.get(`http://127.0.0.1:${port}/listen?history=1&key=${LC.access(KEY)}`, a => {
  let text = ""; a.on("data", d => text += d); setTimeout(() => { a.destroy(); res(text.split("\n").filter(l => l.startsWith("data: ") && l.split(" ").length === 4).map(l => Buffer.from(l.split(" ")[3], "base64"))); }, 700);
}).on("error", rej));
function relay(port) { if (HOST) return hostRelay(port); const r = spawn("python3", [path.join(__dirname, "relay.py"), String(port)], { stdio: ["ignore", "ignore", "inherit"] }); return r; }

// LiveHost on the JVM (LiveHostChecks serve), compiled from the app's sources, serving the packed web app.
let hostClasses;
function hostRelay(port) {
  const java = p => path.join(process.env.JAVA_HOME || "/Applications/Android Studio.app/Contents/jbr/Contents/Home", "bin", p);
  if (!hostClasses) {
    hostClasses = fs.mkdtempSync(path.join(os.tmpdir(), "live-host-"));
    execFileSync(java("javac"), ["-nowarn", "-d", hostClasses, "-sourcepath", path.join(REPO, "app", "src", "main", "java"), path.join(REPO, "app", "src", "test", "java", "io", "github", "mpdairy", "monopaint", "LiveHostChecks.java")], { stdio: ["ignore", "ignore", "inherit"] });
    execFileSync("python3", [path.join(__dirname, "relay.py"), "pack", path.join(hostClasses, "web")]);
  }
  return spawn(java("java"), ["-cp", hostClasses, "io.github.mpdairy.monopaint.LiveHostChecks", "serve", String(port), KEY, path.join(hostClasses, "web")], { stdio: ["ignore", "ignore", "inherit"] });
}
// The stand-in host stays on the relay as the Manta does (a guest without one shows Host gone over its page).
const hostListens = port => new Promise((res, rej) => http.get(`http://127.0.0.1:${port}/listen?here=1&key=${LC.access(KEY)}&from=${MANTA}`, a => res(a)).on("error", rej));
const status = url => new Promise((res, rej) => http.get(url, a => { a.resume(); res(a.statusCode); }).on("error", rej));

(async () => {
  const jvm = jvmLayout([IPAD, PHONE, IPAD_WIDE]);
  const relays = [relay(PORT), relay(PORT + 1)];
  await sleep(HOST ? 1500 : 700);
  if (HOST) {
    const at = `http://127.0.0.1:${PORT}`;
    ok(await status(at + "/live") === 200 && await status(at + "/mbd-engine.wasm") === 200, "host: the web app's files without the key");
    ok(await status(at + "/listen") === 403 && await status(at + "/listen?key=wrong") === 403 && await status(at + "/send?from=x") === 403
      && await status(at + "/listen?key=" + KEY) === 403, "host: listen and send without the key's access, or another, or the key itself, refused");
  }
  const b = await puppeteer.launch({ executablePath: CHROME, headless: "new", args: ["--disable-gpu", "--disable-background-timer-throttling", "--disable-renderer-backgrounding", "--disable-backgrounding-occluded-windows"], protocolTimeout: 60000 });
  const errs = [];
  async function guest([w, h, scale], port, touch) {
    const p = await b.newPage();
    p.on("console", m => { if ((m.type() === "error" || m.type() === "warning") && !/^Failed to load resource/.test(m.text())) errs.push(m.text()); });
    p.on("response", r => { if (r.status() >= 400 && !/favicon\.ico$/.test(r.url())) errs.push(r.status() + " " + r.url()); });
    p.on("pageerror", e => errs.push("pageerror: " + e.message));
    await p.setViewport({ width: w, height: h, deviceScaleFactor: scale, hasTouch: !!touch, isMobile: !!touch });
    // How many times the page has listened (opened an EventSource): the drops' checks.
    await p.evaluateOnNewDocument(() => { const ES = EventSource; window.listens = 0; window.EventSource = class extends ES { constructor(...a) { super(...a); window.listens++; } }; });
    await p.goto(`http://127.0.0.1:${port}/live#${KEY}`, { waitUntil: "load" });
    await p.waitForFunction(() => window.guest && window.guest.ready, { timeout: 30000 });
    p.cdp = await p.target().createCDPSession();
    return p;
  }
  try {
    // A stand-in host: the first toolbar, its third slot in use, on a page of another size; the Manta sends these first.
    const host = await guest(IPAD, PORT);
    const raw = Buffer.from(await host.evaluate((w, h) => [...guest.E.T.hostTools(w, h, false)].map(v => v & 255), ...PAGE));
    const text = raw.subarray(8).toString("utf8").replace("selected 0", "selected 2");
    await post(PORT, zlib.deflateSync(Buffer.concat([raw.subarray(0, 8), Buffer.from(text)])));
    const hosts = [await hostListens(PORT)];
    await host.close();
    if (HOST) {  // the address alone, typed: the page asks for the code, given in fives as the Manta shows it
      const t = await b.newPage();
      await t.goto(`http://127.0.0.1:${PORT}/`, { waitUntil: "load" });
      await t.waitForSelector("#cut input");
      await t.type("#cut input", KEY.match(/.{1,5}/g).join(" ").toUpperCase());
      await t.click("#cut button");
      await t.waitForFunction(() => window.guest && window.guest.ready, { timeout: 30000 });
      ok(await t.evaluate(k => location.hash === "#" + k, KEY), "host: the address alone asks for the code, and the code opens the page");
      await t.close();
    }
    const A = await guest(IPAD, PORT), B = await guest(PHONE, PORT, true);
    await sleep(800);
    for (const [g, name] of [[A, "iPad"], [B, "phone"]]) {
      const s = await g.evaluate(() => ({ W: guest.W, H: guest.H, sel: guest.sel }));
      ok(s.W === PAGE[0] && s.H === PAGE[1] && s.sel === 2, `${name}: the host's page size and tool in use arrive (${s.W}×${s.H}, slot ${s.sel})`);
    }
    // The column where DrawLayout puts it (the JVM's numbers), on both screens.
    for (const [g, name, J] of [[A, "iPad portrait 820×1180", jvm[0]], [B, "phone portrait 390×844", jvm[1]]]) {
      const dom = await g.evaluate(() => {
        const r = el => { const b = el.getBoundingClientRect(); return [b.x, b.y, b.width, b.height]; };
        return { rows: [...document.querySelectorAll("#column > button:not([data-m4]):not([data-x]):not(#layers)")].map(r), box: r(document.getElementById("box")), view: r(document.getElementById("view")), parts: guest.L.parts, layers: r(document.getElementById("layers")) };
      });
      const tray = J.tops[dom.parts.indexOf("TRAY")], tools = J.tops[dom.parts.indexOf("TOOLS")], faces = J.tops[dom.parts.indexOf("FACES")], layers = J.tops[dom.parts.indexOf("LAYERS")];
      // The slots, then Fill in's row, the smudge tool's and the eraser's (M4).
      const rowsOk = dom.rows.length === J.trays + 3 && dom.rows.slice(0, J.trays).every((r, i) => r[1] === tray + i * J.slot && r[3] === J.slot) && [0, 1, 2].every(k => dom.rows[J.trays + k][1] === tools + k * J.slot);
      const rest = dom.view[0] === J.side && dom.view[1] === J.pageBar && dom.view[2] === J.pageWidth && dom.view[3] === J.canvasHeight && dom.box[3] === J.box && dom.box[1] > faces;
      const layerRow = dom.layers[1] === layers && dom.layers[3] === J.layers && J.layers > 0;
      ok(rowsOk && rest && layerRow, (rowsOk ? "" : "rows off ") + (rest ? "" : "rest off ") + (layerRow ? "" : "layers at " + dom.layers + " ") + `${name}: the column as DrawLayout lays it on the JVM (${J.trays} slots of ${J.slot} dp from ${tray}, the eraser at ${tools + 2 * J.slot}, the box ${J.box} dp, the layer button ${J.layers} dp at ${layers}, the page ${J.pageWidth}×${J.canvasHeight} under a ${J.pageBar} dp bar)`);
    }
    // Strokes: the iPad's pen, the phone's finger.
    const box = p => p.evaluate(() => { const r = document.getElementById("view").getBoundingClientRect(); return [r.x, r.y, r.width, r.height]; });
    const dark = (p, x0, y0, x1, y1) => p.evaluate((x0, y0, x1, y1) => { const S = guest.S, W = guest.W; let k = 0; for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) if (S[y * W + x] < 200) k++; return k; }, x0, y0, x1, y1);
    const toPage = async (p, u, v) => p.evaluate((u, v) => { const r = document.getElementById("view").getBoundingClientRect(), V = guest.V, d = devicePixelRatio; return [((u * r.width) * d - V.x) / V.s, ((v * r.height) * d - V.y) / V.s]; }, u, v);
    async function pen(p, pts, hover) {
      await p.bringToFront();
      const [x, y, w, h] = await box(p), t0 = Date.now() / 1000, at = ([u, v]) => ({ x: x + u * w, y: y + v * h });
      const ev = (type, pt, i, force) => p.cdp.send("Input.dispatchMouseEvent", { type, ...at(pt), button: type === "mouseMoved" && hover ? "none" : "left", buttons: hover || type === "mouseReleased" ? 0 : 1, clickCount: 1, pointerType: "pen", force, tiltX: 20, tiltY: -10, timestamp: t0 + i * .008 });
      if (hover) { for (let i = 0; i < pts.length; i++) { await ev("mouseMoved", pts[i], i, 0); await sleep(8); } return; }
      await ev("mousePressed", pts[0], 0, .2);
      for (let i = 1; i < pts.length; i++) { await ev("mouseMoved", pts[i], i, Math.min(1, .2 + i / pts.length)); await sleep(8); }
      await ev("mouseReleased", pts[pts.length - 1], pts.length, 0);
    }
    async function fingers(p, tracks, steps = 1, ms = 30) {
      await p.bringToFront();
      const [x, y, w, h] = await box(p), at = (u, v, id) => ({ x: x + u * w, y: y + v * h, id, radiusX: 8, radiusY: 8, force: .5 });
      const pts = k => tracks.map((tr, id) => at(...tr(k / Math.max(1, steps)), id));
      await p.cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: pts(0) });
      for (let k = 1; k <= steps; k++) { await sleep(ms); await p.cdp.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: pts(k) }); }
      await sleep(ms); await p.cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
    }
    const line = (u0, v0, u1, v1, k = 60) => Array.from({ length: k + 1 }, (_, i) => [u0 + (u1 - u0) * i / k, v0 + (v1 - v0) * i / k + .01 * Math.sin(i / 5)]);
    const [ax0, ay0] = await toPage(A, .2, .25), [ax1, ay1] = await toPage(A, .8, .3);
    const region = [Math.floor(Math.min(ax0, ax1)) - 30, Math.floor(Math.min(ay0, ay1)) - 30, Math.ceil(Math.max(ax0, ax1)) + 30, Math.ceil(Math.max(ay0, ay1)) + 30];
    await pen(A, line(.2, .25, .8, .3));
    await B.waitForFunction(m => guest.liveLog.some(l => l.from && l.from !== m), { timeout: 10000 }, MANTA).catch(() => {});
    await sleep(900);
    const aMarks = await dark(A, ...region), bMarks = await dark(B, ...region);
    ok(aMarks > 200 && bMarks > aMarks * .9, `the iPad's pen stroke shows on the phone (${aMarks} dark px there, ${bMarks} on the phone)`);
    // The phone's finger: a stroke below.
    const [bx0, by0] = await toPage(B, .2, .6), [bx1, by1] = await toPage(B, .8, .62);
    const bRegion = [Math.floor(Math.min(bx0, bx1)) - 40, Math.floor(Math.min(by0, by1)) - 40, Math.ceil(Math.max(bx0, bx1)) + 40, Math.ceil(Math.max(by0, by1)) + 40];
    await fingers(B, [u => [.2 + .6 * u, .6 + .02 * u]], 30, 16);
    await sleep(1500);
    const fB = await dark(B, ...bRegion), fA = await dark(A, ...bRegion);
    ok(fB > 100 && fA > fB * .9, `the phone's finger stroke shows on the iPad (${fB} dark px, ${fA} on the iPad)`);
    // A two-finger tap on the phone: its own stroke taken back, on both; the iPad's stays.
    await fingers(B, [() => [.4, .8], () => [.6, .8]], 0, 40);
    await sleep(1500);
    const uB = await dark(B, ...bRegion), uA = await dark(A, ...bRegion), keep = await dark(B, ...region);
    ok(uB < fB / 10 && uA < fA / 10 && keep > bMarks * .9, `a two-finger tap takes back the phone's own stroke on both (${fB} → ${uB}, iPad ${fA} → ${uA}) and leaves the iPad's (${keep})`);
    // A three-finger tap: Redo puts it back on both; Undo again (live Redo, LiveOwn's in the browser).
    await fingers(B, [() => [.3, .8], () => [.5, .8], () => [.7, .8]], 0, 40);
    await sleep(1500);
    const rB = await dark(B, ...bRegion), rA = await dark(A, ...bRegion);
    await fingers(B, [() => [.4, .8], () => [.6, .8]], 0, 40);
    await sleep(1500);
    const vB = await dark(B, ...bRegion), vA = await dark(A, ...bRegion);
    ok(Math.abs(rB - fB) < fB / 20 && rA > fA * .9 && vB < fB / 10 && vA < fA / 10, `a three-finger tap redoes it on both (phone ${uB} → ${rB}, iPad ${uA} → ${rA}), two fingers take it back again (${vB}, ${vA})`);
    // The eraser on the iPad, across its own stroke: lighter on both.
    await A.evaluate(() => guest.choose(guest.L.c.ERASER));
    await pen(A, line(.2, .27, .8, .29));
    await sleep(1500);
    const eA = await dark(A, ...region), eB = await dark(B, ...region);
    ok(eA < aMarks * .8 && Math.abs(eB - eA) < aMarks * .1, `the eraser takes marks off on both (iPad ${aMarks} → ${eA}, phone ${eB})`);
    await A.evaluate(() => guest.choose(0));
    // The hover ring: a pen moving with no button outlines the nib.
    await pen(A, line(.5, .5, .52, .5, 6), true);
    const ringDots = await A.evaluate(() => { const c = document.getElementById("ring"), d = c.getContext("2d").getImageData(0, 0, c.width, c.height).data; let k = 0; for (let i = 3; i < d.length; i += 4) if (d[i]) k++; return k; });
    ok(ringDots > 0, `a hovering pen shows the ring (${ringDots} px of dots)`);
    // Pinch on the phone (no padlock in the browser): two fingers apart zoom in; a tap on Zoom, the whole page again.
    const before = await B.evaluate(() => guest.V.percent);
    await fingers(B, [u => [.4 - .15 * u, .5], u => [.6 + .15 * u, .5]], 12, 20);
    const after = await B.evaluate(() => guest.V.percent);
    await B.tap("#zoom"); await sleep(500);
    const whole = await B.evaluate(() => guest.V.percent);
    ok(after > before * 1.3 && whole === before, `pinch: ${before}% to ${after}%, a tap on Zoom back to ${whole}%`);
    // Latency, the iPad's pen to the phone: pen down to the first pixels laid there, the lift to the last (a 1 s and a 3 s stroke); set
    // changes left out.
    await A.evaluate(() => guest.choose(0));
    await B.evaluate(() => { const P = guest.P, lay = P.lay; guest.lays = []; P.lay = (...a) => (a[5] || guest.lays.push(Date.now()), lay.apply(P, a)); });
    const lag = [];
    for (const [dur, v] of [[1000, .1], [3000, .17]]) {
      await B.evaluate(() => { guest.lays.length = 0; });
      const [x, y, w, h] = await box(A), at = u => ({ x: x + (.15 + .7 * u) * w, y: y + (v + .01 * Math.sin(u * 9)) * h });
      const ev = (type, u, force) => A.cdp.send("Input.dispatchMouseEvent", { type, ...at(u), button: "left", buttons: type === "mouseReleased" ? 0 : 1, clickCount: 1, pointerType: "pen", force });
      await A.bringToFront();
      const down = Date.now();
      await ev("mousePressed", 0, .5);
      for (let u; (u = (Date.now() - down) / dur) < 1; ) { await ev("mouseMoved", u, .6); await sleep(8); }
      await ev("mouseMoved", 1, .6);
      const up = Date.now();
      await ev("mouseReleased", 1, 0);
      await sleep(1500);
      const lays = await B.evaluate(() => guest.lays.slice());
      const [, y0] = await toPage(A, .5, v - .025), [, y1] = await toPage(A, .5, v + .025);
      lag.push({ dur, first: lays[0] - down, done: lays[lays.length - 1] - up, lays: lays.length, rows: [0, Math.floor(y0), PAGE[0], Math.ceil(y1)] });
    }
    // Through LiveHost the sends keep one connection (HTTP/1.1 keep-alive): a part pays no new connection's round trip.
    const conns = await A.evaluate(() => performance.getEntriesByType("resource").filter(e => /\/send\?/.test(e.name)).map(e => e.connectEnd > e.connectStart));
    console.log(`  the iPad's sends: ${conns.length}, ${conns.filter(Boolean).length} on a new connection`);
    if (HOST) ok(conns.length > 40 && conns.filter(Boolean).length < conns.length / 4, `the iPad's sends keep their connection (${conns.filter(Boolean).length} of ${conns.length} on a new one)`);
    for (const l of lag) ok(l.first < l.dur / 2 && l.done < 300, `a ${l.dur / 1000} s stroke shows on the phone while it is drawn, in parts (${l.lays}), the first ${l.first} ms after the pen lands, all ${l.done} ms after the lift`);
    // Its parts are one Undo step: the iPad's Undo takes back the whole 3 s stroke on the phone, not its last part.
    const drawn = await dark(B, ...lag[1].rows);
    await A.evaluate(() => guest.undo()); await sleep(800);
    const undone = await dark(B, ...lag[1].rows), still = await dark(B, ...lag[0].rows);
    ok(drawn > 300 && undone < drawn / 20 && still > 300, `Undo takes back a stroke sent in parts whole on the phone (${drawn} → ${undone} dark px), the one before stays (${still})`);
    console.log("latency, iPad pen → phone (ms): " + lag.map(l => `${l.dur / 1000} s stroke: pen down → first pixels ${l.first}, lift → all shown ${l.done} (${l.lays} lays)`).join("; ") + "\n");
    // A change arriving while another draws itself on (a Manta's stroke, 600 ms) isn't kept waiting: the rest is laid at once.
    const Live = require("./live.js"), stroke = Live.encode(100, 1850, 300, 20, new Uint8Array(6000).fill(0), Array.from({ length: 31 }, (_, i) => [100 + 10 * i, 1860, 100 * i]).flat());
    await B.evaluate(() => { guest.lays.length = 0; });
    await post(PORT, zlib.deflateSync(stroke)); await sleep(60);
    const next = Date.now();
    await post(PORT, zlib.deflateSync(Live.encode(100, 1880, 50, 5, new Uint8Array(250).fill(0), [])));
    await sleep(900);
    const behind = await B.evaluate(() => guest.lays.slice());
    ok(behind.length >= 3 && behind[behind.length - 1] - next < 50, `a change arriving while another draws itself on is laid ${behind[behind.length - 1] - next} ms after it is sent, the rest of the other at once (${behind.length} lays)`);
    // The Manta's own stroke reaches the phone while it is drawn (LiveProbe.drawing; the owner, 2026-11-05: "FROM the manta,
    // it's still just on pen lift"): its parts a PART_MS (120 ms) apart as the JVM makes them, then the lift's; the phone lays
    // them as they come, before the lift, and its page there is the host's after it, as is the iPad's.
    const HP = jvmParts(...PAGE, 560, 760), [hx, hy, hw, hh] = HP.box, hostGreys = Buffer.from(HP.greys, "base64");
    const blank = await dark(B, hx, hy, hx + hw, hy + hh);
    await B.evaluate(() => { guest.lays.length = 0; });
    for (const part of HP.parts.slice(0, -1)) { await post(PORT, Buffer.from(part, "base64")); await sleep(120); }
    await sleep(200);
    const beforeLift = await B.evaluate(() => guest.lays.length), darkBefore = await dark(B, hx, hy, hx + hw, hy + hh);
    await post(PORT, Buffer.from(HP.parts[HP.parts.length - 1], "base64"));
    await sleep(900);
    const sameAsHost = p => p.evaluate((hx, hy, hw, hh, g) => { const S = guest.S, W = guest.W, want = Uint8Array.from(atob(g), c => c.charCodeAt(0)); let k = 0;
      for (let r = 0; r < hh; r++) for (let c = 0; c < hw; c++) if (Math.min(254, S[(hy + r) * W + hx + c]) !== want[r * hw + c]) k++; return k; }, hx, hy, hw, hh, HP.greys);
    const offB = await sameAsHost(B), offA = await sameAsHost(A), darkAfter = await dark(B, hx, hy, hx + hw, hy + hh);
    const partKinds = [...new Set(HP.parts.map(p => Live.parse(zlib.inflateSync(Buffer.from(p, "base64"))).kinds))];  // each part of the stroke's kind (LiveChange.kinds)
    ok(blank === 0 && HP.parts.length >= 6 && beforeLift >= HP.parts.length - 2 && darkBefore > darkAfter / 2 && offB === 0 && offA === 0 && hostGreys.some(v => v < 200) && partKinds.length === 1 && partKinds[0] > 0,
      `the Manta's stroke shows on the phone while drawn: ${HP.parts.length - 1} parts of kind ${partKinds} laid as they came (${beforeLift} lays, ${darkBefore} of ${darkAfter} dark px before the lift); after it the phone's page there is the host's (${offB} px differ, the iPad's ${offA})`);
    // Greys show as on the Manta (WebEngine.shade, its dots as a smooth grey): a mid grey about half black, as dark as
    // there, not 16 plain greys' 187; a grey of 250, sparse dots there, faint but not white. The page keeps the tones.
    await post(PORT, zlib.deflateSync(Live.encode(400, 1900, 2, 1, Uint8Array.of(180, 250), [])));
    await sleep(900);
    const toned = await B.evaluate(() => { const d = document.getElementById("page").getContext("2d").getImageData(400, 1900, 2, 1).data, i = 1900 * guest.W + 400;
      return { tones: [guest.S[i], guest.S[i + 1]], shown: [d[0], d[4]], shade: [guest.E.T.shade(180), guest.E.T.shade(250)] }; });
    ok(toned.tones.join() === "180,250" && toned.shown.join() === toned.shade.join() && toned.shown[0] < 140 && toned.shown[1] < 255,
      `greys show as on the Manta: tones ${toned.tones} on the page shown ${toned.shown} (its curve: ${toned.shade})`);
    // The three layers (2026-11-06): the column's layer button, a tap's top third layer 3 on the iPad alone (each person picks
    // a layer for themselves); the iPad's stroke goes saying layer 3 and shows on the phone, whose own layer stays 2; the
    // Manta's change on layer 1 and an older app's (no layer: 2) show too; the browser's own Undo goes on its stroke's layer.
    const layerOf = g => g.evaluate(() => ({ layer: guest.E.T.layer(guest.P.id), said: document.getElementById("layers").getAttribute("aria-label") }));
    const third = async (g, k) => { await g.bringToFront(); const r = await g.evaluate(() => { const b = document.getElementById("layers").getBoundingClientRect(); return [b.x + b.width / 2, b.y, b.height]; }); await g.mouse.click(r[0], r[1] + r[2] * (k + .5) / 3); await sleep(200); };
    const l0 = [await layerOf(A), await layerOf(B)];
    await third(A, 0);
    const l1 = [await layerOf(A), await layerOf(B)];
    ok(l0.every(l => l.layer === 2 && l.said === "Layer 2 of 3") && l1[0].layer === 3 && l1[0].said === "Layer 3 of 3" && l1[1].layer === 2,
      `the layer button: layer 2 at first, a tap on its top third picks layer 3 on the iPad (${l1[0].said}), the phone's stays ${l1[1].layer}`);
    await A.evaluate(() => guest.choose(0));
    const sentBefore = (await heard(PORT)).length;
    const [lx0, ly0] = await toPage(A, .2, .38), [lx1, ly1] = await toPage(A, .45, .4), lRegion = [Math.floor(lx0) - 30, Math.floor(ly0) - 30, Math.ceil(lx1) + 30, Math.ceil(ly1) + 30];
    const blankL = [await dark(A, ...lRegion), await dark(B, ...lRegion)];
    await pen(A, line(.2, .38, .45, .4));
    await sleep(1200);
    const fromA = (await heard(PORT)).slice(sentBefore).map(b => { try { return Live.parse(zlib.inflateSync(LC.open(KEY, b))); } catch (e) { return null; } }).filter(c => c && c.w);
    const onB = await dark(B, ...lRegion), onA = await dark(A, ...lRegion), lB = await layerOf(B);
    ok(fromA.length > 0 && fromA.every(c => c.layer === 3) && onA > 100 && onB > onA * .9 && lB.layer === 2,
      `the iPad's stroke on layer 3 goes saying so (${fromA.map(c => c.layer)}) and shows on the phone (${onA} dark px, ${onB} there), the phone still on layer ${lB.layer}`);
    await A.evaluate(() => guest.undo()); await sleep(900);
    const undoneB = await dark(B, ...lRegion), undoneA = await dark(A, ...lRegion), undoLayer = (await heard(PORT)).slice(-1).map(b => Live.parse(zlib.inflateSync(LC.open(KEY, b))).layer)[0];
    ok(undoneA - blankL[0] < onA / 10 && undoneB - blankL[1] < onB / 10 && undoLayer === 3, `its Undo takes it back on both (${blankL} before it, ${onA} → ${undoneA}, phone ${onB} → ${undoneB}), on layer ${undoLayer}`);
    await post(PORT, zlib.deflateSync(Live.encode(1300, 120, 60, 20, new Uint8Array(1200).fill(0), [], false, false, 1, 1)));
    await post(PORT, zlib.deflateSync(Live.encode(1300, 160, 60, 20, new Uint8Array(1200).fill(0), [])));
    await sleep(900);
    const hostOn = [await dark(A, 1300, 120, 1360, 140), await dark(B, 1300, 120, 1360, 140), await dark(A, 1300, 160, 1360, 180), await dark(B, 1300, 160, 1360, 180)], own = [await layerOf(A), await layerOf(B)];
    ok(hostOn.every(k => k === 1200) && own[0].layer === 3 && own[1].layer === 2, `the Manta's change on layer 1 and an older app's show on both (${hostOn}), each guest's own layer kept (${own.map(l => l.layer)})`);
    await third(A, 1);
    // Timing on the iPad, a stroke each with a few of the host's slots: an input event's samples drawn and shown, the lift;
    // then relay → shown on the phone (drawing on, 200 to 600 ms, included).
    console.log("\ntiming (this Mac's headless Chrome; ms): slot, an input event drawn and shown median / 95th / worst, lift");
    for (const [slot, v] of [[0, .45], [3, .5], [4, .6], [5, .7], [6, .8]]) {
      await A.evaluate(i => { guest.choose(i); guest.drawMs.length = 0; }, slot);
      await pen(A, line(.2, v, .8, v + .03));
      await sleep(slot >= 4 && slot <= 6 ? 2500 : 600);
      const t = await A.evaluate(() => ({ draw: guest.drawMs.slice(), lift: guest.strokeLog[guest.strokeLog.length - 1].lift, name: guest.tools.slots[guest.sel].name }));
      console.log(`  ${t.name.padEnd(18)} ${pct(t.draw, .5).toFixed(1)} / ${pct(t.draw, .95).toFixed(1)} / ${Math.max(...t.draw).toFixed(1)}, lift ${t.lift.toFixed(0)}`);
    }
    const shown = await B.evaluate(m => guest.liveLog.filter(l => l.relay && l.from !== m).map(l => l.shown - l.relay), MANTA);
    console.log(`  relay → shown on the phone: ${shown.join(", ")}\n`);
    if (SHOTS) {
      const out = path.join(REPO, "docs", "archive", "pictures", "live");
      fs.mkdirSync(out, { recursive: true });
      await A.screenshot({ path: path.join(out, "m1-ipad-portrait.png") });
      await B.evaluate(() => { guest.E.T.viewReset(); dispatchEvent(new Event("resize")); }); await sleep(300);
      await B.screenshot({ path: path.join(out, "m1-phone-portrait.png") });
      const C = await guest(IPAD_WIDE, PORT); await sleep(1500);
      await C.screenshot({ path: path.join(out, "m1-ipad-landscape.png") });
      const dom = await C.evaluate(() => [...document.querySelectorAll("#column button")].map(b => b.getBoundingClientRect().y));
      ok(dom[0] === jvm[2].tops[2], `iPad landscape: the tray from ${dom[0]} dp, as on the JVM`);
      await C.close();
    }
    // The host's kaleidoscope: a guest's stroke lays six copies (a page of its own on the second relay).
    const kraw = Buffer.from(await A.evaluate((w, h) => [...guest.E.T.hostTools(w, h, true)].map(v => v & 255), ...PAGE));
    await post(PORT + 1, zlib.deflateSync(kraw));
    hosts.push(await hostListens(PORT + 1));
    const K = await guest(IPAD, PORT + 1); await sleep(800);
    const [kx0, ky0] = await toPage(K, .55, .2);
    await pen(K, line(.55, .2, .62, .24, 20));
    await sleep(800);
    const all = await dark(K, 0, 0, PAGE[0], PAGE[1]), mine = await dark(K, Math.floor(kx0) - 40, Math.floor(ky0) - 40, Math.ceil(kx0) + 200, Math.ceil(ky0) + 200);
    ok(mine > 50 && all >= 5 * mine, `the host's kaleidoscope mirrors a guest's stroke (${all} dark px on the page, ${mine} round the stroke itself)`);
    await K.close();
    // Drops. Both guests' pages the same (in the 16 greys shown: a guest's own strokes keep the engine's tones, others'
    // arrive in the 16), once both have laid all there is: how many pixels differ, after waiting up to 15 s for none.
    const shownS = g => g.evaluate(() => [...guest.S].map(v => Math.round(v / 17)));
    const differ = async (one = A, other = B) => {
      let d = -1;
      for (const t = Date.now(); d !== 0 && Date.now() - t < 15000; await sleep(500)) {
        const [a, b] = [await shownS(one), await shownS(other)]; d = 0;
        for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) d++;
      }
      return d;
    };
    const said = (g, words, ms) => g.waitForFunction(w => guest.status === w, { timeout: ms, polling: 200 }, words).then(() => true, () => false);
    const others = "Connected, 1 other guest", SILENT = JSON.parse(await A.evaluate(() => guest.E.T.layout(800, 800))).c.LIVE_SILENT_MS;
    // A silent drop: the relay stops answering without closing (a Wi-Fi gone without a word, the Manta asleep mid-line);
    // the guest notices within LIVE_SILENT_MS, keeps trying, and once the relay answers again is Connected, the page the same.
    // Until then (over a minute), each guest has listened once: the relay's heartbeat (LivePresence.STILL) heard, idle or not.
    const listens = await Promise.all([A, B].map(g => g.evaluate(() => window.listens)));
    ok(listens.every(k => k === 1), `a guest keeps one connection while the relay is there (${listens} listens)`);
    process.kill(relays[0].pid, "SIGSTOP");
    const noticed = await said(B, "Reconnecting…", SILENT + 8000) && await said(A, "Reconnecting…", 5000);
    process.kill(relays[0].pid, "SIGCONT");
    const back = await said(B, others, 15000) && await said(A, others, 15000), d0 = await differ();
    ok(noticed && back && d0 === 0, `a relay gone silent: both guests say Reconnecting… (${noticed}), then Connected again (${back}), both pages the same (${d0} px differ)`);
    // The relay gone and started again empty, same key and port (the Manta asleep and awake: LiveHost comes back with
    // nothing; the Mac's relay restarted). The phone's stroke drawn as it goes, unsent (the relay silent first, as the
    // Wi-Fi going); meanwhile the host erases the iPad's first stroke and makes a mark. Back, the stand-in host listens
    // again and sends its tools and its page whole, as LiveProbe does on connecting. Both guests reconnect on their own
    // and end with the host's page: the erased stroke gone, the host's mark there, the phone's stroke there too (sent
    // again once the host is back), both pages the same.
    const hostPage = Uint8Array.from(await A.evaluate(() => [...guest.S]), v => v === 255 ? 255 : Math.min(254, Math.round(v / 17) * 17));
    for (let y = region[1]; y < region[3]; y++) hostPage.fill(255, y * PAGE[0] + region[0], y * PAGE[0] + region[2]);
    for (let y = 1700; y < 1760; y++) hostPage.fill(0, y * PAGE[0] + 1300, y * PAGE[0] + 1500);
    const before0 = await dark(B, ...region);
    const fromPage = (p, x, y) => p.evaluate((x, y) => { const r = document.getElementById("view").getBoundingClientRect(), V = guest.V, d = devicePixelRatio; return [(x * V.s + V.x) / d / r.width, (y * V.s + V.y) / d / r.height]; }, x, y);
    const [pu0, pv0] = await fromPage(B, 1000, 1400), [pu1, pv1] = await fromPage(B, 1200, 1450), pRegion = [960, 1360, 1240, 1490];
    await B.evaluate(() => guest.choose(0));
    const phoneBefore = await dark(A, ...pRegion);
    process.kill(relays[0].pid, "SIGSTOP");
    await fingers(B, [u => [pu0 + (pu1 - pu0) * u, pv0 + (pv1 - pv0) * u]], 30, 16);
    await sleep(500);
    const drawnAway = await dark(B, ...pRegion);
    hosts[0].destroy(); relays[0].kill("SIGKILL");
    await sleep(1500);
    const awaySaid = await B.evaluate(() => guest.status);
    relays[0] = relay(PORT);
    await sleep(HOST ? 1500 : 700);
    hosts[0] = await hostListens(PORT);
    await post(PORT, zlib.deflateSync(Buffer.concat([raw.subarray(0, 8), Buffer.from(text)])));
    await post(PORT, zlib.deflateSync(Live.encode(0, 0, PAGE[0], PAGE[1], hostPage, [])));
    const again = await said(B, others, 20000) && await said(A, others, 20000), d1 = await differ();
    const erased = [await dark(A, ...region), await dark(B, ...region)], hostMark = [await dark(A, 1300, 1700, 1500, 1760), await dark(B, 1300, 1700, 1500, 1760)];
    const phoneMark = [await dark(A, ...pRegion), await dark(B, ...pRegion)];
    ok(awaySaid === "Reconnecting…" && again && before0 > 200 && erased.every(k => k < 20) && hostMark.every(k => k === 200 * 60) && drawnAway > phoneBefore + 100 && phoneMark.every(k => k > phoneBefore + 100) && d1 === 0,
      `the relay gone and started again empty: the phone said ${awaySaid}, then both Connected (${again}); the host's page whole on both (its erased stroke ${before0} → ${erased} dark px, its mark ${hostMark}); the phone's stroke drawn as it went, unsent (${phoneBefore} → ${drawnAway}), on both (${phoneMark}); both pages the same (${d1} px differ)`);
    // What the relay passed on, as a listener on the Wi-Fi reads it: none of it plain (deflated), all of it opens with the key.
    const bodies = await heard(PORT), plain = b => { try { zlib.inflateSync(b); return true; } catch (e) { return false; } };
    const opens = b => { try { zlib.inflateSync(LC.open(KEY, b)); return true; } catch (e) { return false; } };
    ok(bodies.length > 3 && !bodies.some(plain) && bodies.every(opens) && !bodies.some(b => opens(Buffer.concat([b.subarray(0, 20), Buffer.from([b[20] ^ 1]), b.subarray(21)]))),
      `every change through the relay sealed with the link's key (${bodies.length}: none reads plain, all open with Node's AES-GCM, none with a bit changed)`);
    // The host's page whole again (as LiveProbe.shown: the tools marked start=1, then the page): the relay's history
    // starts again there, so a guest joining (or listening again) replays two changes, not all since the relay started,
    // and shows the page as the others do.
    const mantaPage = Uint8Array.from(await A.evaluate(() => [...guest.S]), v => v === 255 ? 255 : Math.min(254, Math.round(v / 17) * 17));
    await post(PORT, zlib.deflateSync(Buffer.concat([raw.subarray(0, 8), Buffer.from(text)])), true);
    await post(PORT, zlib.deflateSync(Live.encode(0, 0, PAGE[0], PAGE[1], mantaPage, [])));
    await sleep(900);
    const kept = (await heard(PORT)).length, C = await guest(IPAD, PORT), dC = await differ(A, C);
    ok(kept === 2 && dC === 0, `the relay's history from the host's last page whole: ${bodies.length} changes before, ${kept} after; a guest joining then shows the page as the others (${dC} px differ)`);
    await C.close();
    // A change the relay refuses (a 4xx) is let go with a note and the phone's next mark goes; one meeting a network
    // failure waits and goes again. The phone's sends answered by the browser itself (CDP Fetch) as each of meet says.
    const meet = [];
    await B.cdp.send("Fetch.enable", { patterns: [{ urlPattern: "*/send?*" }] });
    B.cdp.on("Fetch.requestPaused", e => {
      const m = meet.length ? meet.shift() : "through", id = { requestId: e.requestId };
      B.cdp.send(...(m === "refuse" ? ["Fetch.fulfillRequest", { ...id, responseCode: 403, body: "" }] : m === "fail" ? ["Fetch.failRequest", { ...id, errorReason: "ConnectionRefused" }] : ["Fetch.continueRequest", id])).catch(() => {});
    });
    const strokeAt = async (x0, y0, x1, y1) => { const [u0, v0] = await fromPage(B, x0, y0), [u1, v1] = await fromPage(B, x1, y1); await fingers(B, [u => [u0 + (u1 - u0) * u, v0 + (v1 - v0) * u]], 30, 16); };
    const rows = [[500, 1930, 800, 1960], [900, 1930, 1150, 1960], [1250, 1930, 1550, 1960]], pad = r => [r[0] - 40, r[1] - 40, r[2] + 40, r[3] + 40];
    const empty = (await Promise.all(rows.map(r => dark(A, ...pad(r))))).every(k => k === 0);
    meet.push(...Array(40).fill("refuse"));
    await strokeAt(...rows[0]);
    const noted = await B.waitForFunction(() => document.getElementById("note").textContent === "Couldn't send a mark", { timeout: 5000, polling: 50 }).then(() => true, () => false);
    await sleep(1500); meet.length = 0;
    await strokeAt(...rows[1]);
    await sleep(1500);
    meet.push("fail", "fail");
    await strokeAt(...rows[2]);
    await sleep(4000);
    const [r0, r1, r2] = await Promise.all(rows.map(async r => [await dark(B, ...pad(r)), await dark(A, ...pad(r))]));
    ok(empty && noted && r0[0] > 100 && r0[1] === 0 && r1[0] > 100 && r1[1] > r1[0] * .9 && r2[0] > 100 && r2[1] > r2[0] * .9 && !meet.length,
      `a refused change let go with a note (${noted}; ${r0[0]} dark px on the phone, ${r0[1]} on the iPad), the next goes (${r1}); one meeting a network failure goes again (${r2})`);
    await B.cdp.send("Fetch.disable");
    errs.splice(0, errs.length, ...errs.filter(e => !/^403 .*\/send\?/.test(e) && !/^40[34] .*\/drawable\/tip_[a-z0-9_]*\.xml$/.test(e)));  // the refusals above; a picture tip asked as a drawing first (vector; LiveHost refuses what it hasn't)
    ok(!errs.length, "no console errors" + (errs.length ? ": " + errs.slice(0, 3).join(" | ") : ""));
  } finally { await b.close(); relays.forEach(r => r.kill()); }
  console.log(`${n - fail} of ${n} passed`);
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
