// Live pages' probe, measured without hands (branch live-auto): the Manta over USB (adb reverse tcp:8766 tcp:8766,
// live-probe.txt saying http://127.0.0.1:8766), this relay restarted from here, probe.html in headless Chrome
// (puppeteer-core from ~/node_modules). The Manta's pen is PenStroke (pen.sh builds and pushes it): a stylus with
// pressure and tilt at real timing, pressing in, swelling and tapering, light or heavy; on the page and tool left open. Reports per stroke: the Manta's own timings (LiveProbe's log),
// relay → browser shown, browser sent → Manta laid; then the Manta's page (sent whole on reconnect) against the
// browser's canvas, as PNGs in the out folder.
// node scripts/live-probe/autotest.cjs [out folder] [--keep-relay] [--plain N] [--row K] [--browser M] [--brow K] [--brushes 0,4,5] [--cross]
//   [--compare 8,5]: those toolbar slots drawn alike on the Manta (pen), in the browser with a pen and with a mouse:
//   compare.png, a row a slot. [--slot N]: the Manta draws with toolbar slot N. Either leaves slot --leave (default 5,
//   as Elling left it) selected.
const { execSync, execFileSync, spawn } = require("child_process"), fs = require("fs"), path = require("path"), http = require("http"), zlib = require("zlib");
const puppeteer = require(path.join(require("os").homedir(), "node_modules", "puppeteer-core"));
const CHROME = process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const PORT = 8766, APP = "ing.ell.mybaddrawings", ACTIVITY = APP + "/io.github.mpdairy.monopaint.PaintActivity";
const args = process.argv.slice(2), opt = (k, d) => { const i = args.indexOf(k); return i < 0 ? d : args[i + 1]; };
const OUT = args[0] && !args[0].startsWith("--") ? args[0] : fs.mkdtempSync(path.join(require("os").tmpdir(), "live-auto-"));
const SLOT = opt("--slot", null);  // the Manta's toolbar slot to draw with (0 the top); then --leave's again at the end
const COMPARE = opt("--compare", "") ? opt("--compare").split(",").map(Number) : [], LEAVE = +opt("--leave", 5);
const BROW = +opt("--brow", 3);
const PLAIN = +opt("--plain", 4), ROW = +opt("--row", 4), BROWSER = +opt("--browser", 4), BRUSHES = opt("--brushes", "0,4,5,6").split(",").map(Number), CROSS = args.includes("--cross");
const sleep = ms => new Promise(r => setTimeout(r, ms));
const adb = cmd => execFileSync("adb", ["shell", cmd], { encoding: "utf8" });
// Pen strokes, one after another in one go: each [x0, y0, x1, y1, ms, heavy] (screen px), a line with pressure pressing
// in, swelling and tapering (light: a third as hard), tilted a little.
function pen(...strokes) {
  const run = strokes.map(([x0, y0, x1, y1, ms, heavy], k) => {
    const n = Math.max(2, Math.round(ms / 8)), lines = [];
    for (let i = 0; i <= n; i++) {
      const t = i / n, p = (heavy ? 1 : .35) * Math.min(1, t / .15) * (1 - .9 * Math.max(0, (t - .7) / .3)) * (.6 + .4 * Math.sin(Math.PI * t));
      lines.push(`${(x0 + (x1 - x0) * t).toFixed(1)} ${(y0 + (y1 - y0) * t).toFixed(1)} ${Math.max(p, .02).toFixed(3)} 0.5 ${Math.round(ms * t)}`);
    }
    fs.writeFileSync(path.join(OUT, `stroke${k}.txt`), lines.join("\n") + "\n");
    execFileSync("adb", ["push", "-q", path.join(OUT, `stroke${k}.txt`), `/data/local/tmp/stroke${k}.txt`]);
    return `CLASSPATH=/data/local/tmp/pen.dex app_process / PenStroke /data/local/tmp/stroke${k}.txt`;
  });
  adb(run.join("; "));
}
// The canvas on the screen (portrait, 100 %): page pixel = screen − this, read from the view tree.
let ORIGIN = [135, 75];

function offset() { // device clock − this clock, ms (adb's round trip halved)
  let best = null;
  for (let i = 0; i < 5; i++) { const t0 = Date.now(), d = +adb("date +%s%3N"), t1 = Date.now(); if (!best || t1 - t0 < best.rt) best = { rt: t1 - t0, off: d - (t0 + t1) / 2 }; }
  return best.off;
}
function canvasOrigin() {
  adb("uiautomator dump /data/local/tmp/live-auto-ui.xml >/dev/null 2>&1");
  const xml = adb("cat /data/local/tmp/live-auto-ui.xml; rm /data/local/tmp/live-auto-ui.xml");
  const m = xml.match(/content-desc="Painting canvas[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);
  const brush = (xml.match(/content-desc="([^".]+)\. [^"]*Tap for Brush Lab\."/) || [])[1];
  return m ? { origin: [+m[1], +m[2]], size: [m[3] - m[1], m[4] - m[2]], brush } : null;
}
function mantaLog() { // LiveProbe's lines with device epoch ms
  return execSync("adb logcat -d -v epoch -s LiveProbe", { encoding: "utf8", maxBuffer: 1 << 26 }).split("\n").map(l => {
    const m = l.match(/^\s*(\d+)\.(\d+)\s+\d+\s+\d+\s+\w\s+LiveProbe:\s(.*)$/); return m && { t: +m[1] * 1000 + +m[2], text: m[3] };
  }).filter(Boolean);
}
function restartRelay() {
  try { execSync(`pkill -f "relay.py ${PORT}"`); } catch (e) { /* none running */ }
  const log = fs.openSync(path.join(OUT, "relay.log"), "a");
  spawn("python3", ["-u", path.join(__dirname, "relay.py"), String(PORT), "lan"], { detached: true, stdio: ["ignore", log, log] }).unref();
}
function restartApp() { adb(`am force-stop ${APP}; input keyevent KEYCODE_WAKEUP; am start -n ${ACTIVITY} >/dev/null`); }
// Every change through the relay, as node sees it: number, sender, decoded change, when.
const changes = [];
function listen() {
  http.get(`http://127.0.0.1:${PORT}/listen`, res => {
    let buf = "";
    res.on("data", d => {
      buf += d; let i;
      while ((i = buf.indexOf("\n\n")) >= 0) {
        const block = buf.slice(0, i); buf = buf.slice(i + 2);
        const lines = block.split("\n"), data = lines.find(l => l.startsWith("data: ")), id = lines.find(l => l.startsWith("id: ")); if (!data) continue;
        const [number, from, b64] = data.slice(6).split(" ");
        // at: the relay's ms (its id); this process's own clock stalls while adb runs.
        changes.push({ number: +number, from, at: id ? +id.slice(4) : Date.now(), change: decode(zlib.inflateSync(require("./live-crypt.cjs").open(process.env.LIVE_KEY || "", Buffer.from(b64, "base64")))) });  // LIVE_KEY: after the # in live-probe.txt
      }
    });
  });
}
function decode(raw) { // as LiveChange.read
  const x = raw.readUInt16BE(0), y = raw.readUInt16BE(2), w = raw.readUInt16BE(4), h = raw.readUInt16BE(6), g = 8 + w * h;
  const n = raw.length > g ? raw.readUInt16BE(g) : 0, set = raw.length === g + 2 + 6 * n + 1;
  return { x, y, w, h, greys: raw.subarray(8, g), samples: n, set };
}
// PNG, grey, no dependencies.
function png(file, w, h, grey) {
  const crc = b => { let c, k, t = []; for (k = 0; k < 256; k++) { c = k; for (let j = 0; j < 8; j++) c = c & 1 ? 0xedb88320 ^ c >>> 1 : c >>> 1; t[k] = c >>> 0; } c = ~0; for (const v of b) c = t[(c ^ v) & 255] ^ c >>> 8; return ~c >>> 0; };
  const chunk = (type, data) => { const len = Buffer.alloc(4), body = Buffer.concat([Buffer.from(type), data]), c = Buffer.alloc(4); len.writeUInt32BE(data.length); c.writeUInt32BE(crc(body)); return Buffer.concat([len, body, c]); };
  const raw = Buffer.alloc((w + 1) * h); for (let y = 0; y < h; y++) { raw[y * (w + 1)] = 0; for (let x = 0; x < w; x++) raw[y * (w + 1) + 1 + x] = grey(x, y); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 0;
  fs.writeFileSync(file, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]));
}
const quiet = async (page, ms) => { // until the browser has laid nothing new for ms
  let n = -1; for (;;) { const m = await page.evaluate(() => window.liveLog ? window.liveLog.length : -1); if (m === n && m >= 0) return; n = m; await sleep(ms); }
};
const stats = a => a.length ? `${Math.min(...a)}–${Math.max(...a)} (median ${a.slice().sort((p, q) => p - q)[a.length >> 1]})` : "–";

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  execFileSync("bash", [path.join(__dirname, "pen.sh")], { stdio: "inherit" });
  if (!args.includes("--keep-relay")) { restartRelay(); await sleep(800); }
  execSync(`adb reverse tcp:${PORT} tcp:${PORT}`);
  listen();
  adb("input keyevent KEYCODE_WAKEUP");
  restartApp(); execSync("adb logcat -c");
  await sleep(7000);
  const ui = canvasOrigin(); if (ui) ORIGIN = ui.origin;
  const whole = changes.find(c => c.from.startsWith("manta") && c.change.x === 0 && c.change.y === 0);
  if (!whole) throw new Error("the Manta sent no page: is the app listening (live-probe.txt) and the reverse set?");
  const W = whole.change.w, H = whole.change.h;
  console.log(`page ${W}×${H}, canvas at ${ORIGIN}, Manta brush: ${ui && ui.brush}; out: ${OUT}`);
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: "new", args: ["--no-sandbox"] });
  const page = await browser.newPage(); await page.setViewport({ width: 900, height: 1300 });
  page.on("console", m => m.type() === "error" && console.log("browser:", m.text()));
  await page.goto(`http://127.0.0.1:${PORT}/?w=${W}&h=${H}`);
  await page.waitForFunction(() => window.liveLog && window.liveLog.length > 0, { timeout: 60000 });
  await quiet(page, 1000);
  const off = offset();
  if (SLOT != null) { adb(`input tap 64 ${171 + 64 * +SLOT}`); await sleep(800); }
  const t0 = Date.now();
  // Manta: plain strokes apart, light and heavy in turn, then a row in one go.
  const stroke = (i, row) => { const x = ORIGIN[0] + 200 + (i % 6) * 220, y = ORIGIN[1] + (row ? 900 : 300) + Math.floor(i / 6) * 300; return [x, y, x + 150, y + 180, 300, i % 2 === 1]; };
  for (let i = 0; i < PLAIN; i++) { pen(stroke(i, false)); await sleep(2500); }
  if (ROW) { pen(...Array.from({ length: ROW }, (_, i) => stroke(i, true))); await sleep(4000); }
  // Browser strokes, each brush its turn, laid on the Manta.
  const rect = await page.evaluate(() => { const r = document.getElementById("ink").getBoundingClientRect(); return [r.left, r.top, r.width, r.height]; });
  const toScreen = (x, y) => [rect[0] + x * rect[2] / W, rect[1] + y * rect[3] / H];
  const drawn = [];
  for (let i = 0; i < BROWSER; i++) {
    const b = BRUSHES[i % BRUSHES.length];
    await page.evaluate(b => document.querySelectorAll("#brushes button")[b].click(), b);
    const x = 200 + (i % 5) * 300, y = 1500 + Math.floor(i / 5) * 300;
    const [sx, sy] = toScreen(x, y), [ex, ey] = toScreen(x + 160, y + 200);
    await page.mouse.move(sx, sy); await page.mouse.down();
    for (let k = 1; k <= 25; k++) { await page.mouse.move(sx + (ex - sx) * k / 25, sy + (ey - sy) * k / 25 + Math.sin(k / 4) * 6); await sleep(12); }
    await page.mouse.up(); drawn.push({ brush: b, x, y });
    await sleep(3000);
  }
  // Browser strokes in a row, quick (each lands on the Manta while the one before draws itself on), Ink wash.
  if (BROW) await page.evaluate(() => document.querySelectorAll("#brushes button")[4].click());
  for (let i = 0; i < BROW; i++) {
    const x = 200 + i * 300, y = 1950, [sx, sy] = toScreen(x, y), [ex, ey] = toScreen(x + 200, y + 100);
    await page.mouse.move(sx, sy); await page.mouse.down();
    for (let k = 1; k <= 10; k++) { await page.mouse.move(sx + (ex - sx) * k / 10, sy + (ey - sy) * k / 10); await sleep(10); }
    await page.mouse.up(); drawn.push({ brush: "row", x, y });
  }
  if (BROW) await sleep(3000);
  // Smearing (problem 3): a Manta stroke across a browser mark, and across a Manta mark of its own, the same way.
  if (CROSS) {
    const bx = 300, by = 2000;
    await page.evaluate(() => document.querySelectorAll("#brushes button")[0].click());
    const [sx, sy] = toScreen(bx, by), [ex] = toScreen(bx + 300, by);
    await page.mouse.move(sx, sy); await page.mouse.down();
    for (let k = 1; k <= 25; k++) { await page.mouse.move(sx + (ex - sx) * k / 25, sy); await sleep(12); }
    await page.mouse.up(); await sleep(3000);
    pen([ORIGIN[0] + 1000, ORIGIN[1] + by, ORIGIN[0] + 1300, ORIGIN[1] + by, 300, true]); await sleep(2500);
    const before = await page.evaluate((y) => Array.from(document.getElementById("page").getContext("2d").getImageData(0, y - 150, document.getElementById("page").width, 300).data.filter((_, i) => i % 4 === 0)), by);
    pen([ORIGIN[0] + bx + 150, ORIGIN[1] + by - 120, ORIGIN[0] + bx + 150, ORIGIN[1] + by + 120, 300, true]); await sleep(2500);
    pen([ORIGIN[0] + 1150, ORIGIN[1] + by - 120, ORIGIN[0] + 1150, ORIGIN[1] + by + 120, 300, true]); await sleep(3500);
    await quiet(page, 1000);
    const after = await page.evaluate((y) => Array.from(document.getElementById("page").getContext("2d").getImageData(0, y - 150, document.getElementById("page").width, 300).data.filter((_, i) => i % 4 === 0)), by);
    const count = (x0, x1) => { let n = 0, sum = 0; for (let r = 0; r < 300; r++) for (let x = x0; x < x1; x++) { const d = after[r * W + x] - before[r * W + x]; if (d) { n++; sum += Math.abs(d); } } return { n, sum }; };
    const overBrowser = count(bx - 50, bx + 350), overManta = count(950, 1350);
    console.log(`crossing (pixels changed in a 400×300 box): over the browser's mark ${overBrowser.n} (Σ|Δ| ${overBrowser.sum}), over the Manta's own ${overManta.n} (Σ|Δ| ${overManta.sum})`);
    png(path.join(OUT, "cross-before.png"), W, 300, (x, y) => before[y * W + x]);
    png(path.join(OUT, "cross-after.png"), W, 300, (x, y) => after[y * W + x]);
  }
  // Brushes alike (Elling: Watercolour washy looked odd in the browser, Ben-Day thinner with a line in the middle).
  const compared = [];
  if (COMPARE.length) {
    const bar = adb(`cat '/sdcard/Document/Superdood/Superdood toolbar.txt'`).split("\n").filter(l => /^\d+\t/.test(l)).map(l => l.split("\t"));
    const pen = async (pts, pressure, ms) => page.evaluate(async (pts, pressure, ms) => {
      const c = document.getElementById("ink"), fire = (type, [x, y]) => c.dispatchEvent(new PointerEvent(type, { pointerType: "pen", pointerId: 7, pressure: type === "pointerup" ? 0 : pressure, clientX: x, clientY: y, bubbles: true, isPrimary: true }));
      fire("pointerdown", pts[0]);
      for (const p of pts.slice(1)) { fire("pointermove", p); await new Promise(r => setTimeout(r, ms)); }
      fire("pointerup", pts[pts.length - 1]);
    }, pts, pressure, ms);
    // Straight lines at the same speed both sides (a brush's width and darkness follow the pen's speed): fast, then slow.
    const DUR = [400, 1500], L = 420;
    for (let k = 0; k < COMPARE.length; k++) {
      const slot = COMPARE[k], row = bar[slot], y = 300 + k * 420;
      adb(`input tap 64 ${171 + 64 * slot}`); await sleep(800);
      await page.evaluate((slot, size, grey) => { window.BRUSHES[slot][2] = size; window.BRUSHES[slot][3] = grey; document.querySelectorAll("#brushes button")[slot].click(); }, slot, parseFloat(row[3]), parseFloat(row[4]));
      for (let d = 0; d < DUR.length; d++) {
        const yy = y + d * 180;
        pen([ORIGIN[0] + 100, ORIGIN[1] + yy, ORIGIN[0] + 100 + L, ORIGIN[1] + yy, DUR[d], true]); await sleep(2000);
        await pen(Array.from({ length: 41 }, (_, i) => toScreen(650 + L * i / 40, yy)), 1, DUR[d] / 40); await sleep(2000);
        const [sx, sy] = toScreen(1200, yy); await page.mouse.move(sx, sy); await page.mouse.down();
        for (let i = 1; i <= 40; i++) { const [px, py] = toScreen(1200 + L * i / 40, yy); await page.mouse.move(px, py); await sleep(DUR[d] / 40); }
        await page.mouse.up(); await sleep(2000);
      }
      compared.push({ slot, name: row[1], size: row[3], grey: row[4], y });
    }
  }
  if (COMPARE.length || SLOT != null) adb(`input tap 64 ${171 + 64 * LEAVE}`);
  await sleep(2000); await quiet(page, 1500);
  // The report.
  const log = mantaLog(), bl = await page.evaluate(() => window.liveLog);
  const sends = log.filter(l => l.text.startsWith("sent ") && l.t - off >= t0 && /after the pen lifted/.test(l.text));
  const fromManta = changes.filter(c => c.from.startsWith("manta") && c.at >= t0 && c.change.samples > 0);
  console.log("\nManta strokes → browser");
  console.log("#  size        bytes   main ms  lift→start  lift→sent  lift→relay  lift→browser shown");
  const lifts = [], mains = [], shownAll = [];
  sends.forEach((s, i) => {
    const m = s.text.match(/sent (\d+×\d+)(?: \(an Undo\))?, (\d+) bytes \(made in (\d+) ms on the main thread, (\d+) ms after the pen lifted; packed in (\d+) ms\): \d+, (\d+) ms after/);
    if (!m) return;
    const lift = s.t - +m[6] - off, c = fromManta[i], shown = c && bl.find(e => e.number === c.number);
    lifts.push(+m[6]); mains.push(+m[3]); if (shown) shownAll.push(shown.shown - lift);
    console.log(`${i + 1}  ${m[1].padEnd(10)}  ${m[2].padStart(6)}  ${m[3].padStart(7)}  ${m[4].padStart(10)}  ${m[6].padStart(9)}  ${c ? String(Math.round(c.at - lift)).padStart(10) : "         –"}  ${shown ? Math.round(shown.shown - lift) : "–"}`);
  });
  console.log(`lift→sent ${stats(lifts)}; main ${stats(mains)}; lift→shown in browser ${stats(shownAll)}`);
  const lays = log.filter(l => l.text.startsWith("change "));
  console.log("\nBrowser strokes → Manta");
  console.log("#  brush  number  size        set  posted→Manta laid  waited  reveal ms");
  const laidAll = [];
  bl.filter(e => e.sent).forEach((e, i) => {
    const l = lays.find(l => l.text.startsWith(`change ${e.sent}:`)), m = l && l.text.match(/(\d+×\d+)( \(an Undo\))?, waited (\d+) ms for the pen, laid in \d+ slices and shown in (\d+) ms/);
    const laid = l ? Math.round(l.t - off - e.at) : null; if (laid != null) laidAll.push(laid);
    console.log(`${i + 1}  ${String(drawn[i] ? drawn[i].brush : "x").padEnd(5)}  ${String(e.sent).padEnd(6)}  ${(m ? m[1] : e.w + "×" + e.h).padEnd(10)}  ${m && m[2] ? "yes" : "no "}  ${laid != null ? String(laid).padStart(17) : "                –"}  ${m ? m[3].padStart(6) : "     –"}  ${m ? m[4] : "–"}`);
  });
  console.log(`posted→laid ${stats(laidAll)}`);
  // The Manta's page against the browser's: it sends its page whole on reconnecting.
  const canvas = await page.evaluate(() => { const c = document.getElementById("page"); return Array.from(c.getContext("2d").getImageData(0, 0, c.width, c.height).data.filter((_, i) => i % 4 === 0)); });
  await sleep(4000);  // the Manta's recovery saved, so the restart loses nothing
  const n0 = changes.length; restartApp(); await sleep(8000);
  const again = changes.slice(n0).find(c => c.from.startsWith("manta") && c.change.w === W && c.change.h === H);
  if (again) {
    const g = again.change.greys, b = i => Math.min(254, canvas[i]);
    let diff = 0; const boxes = drawn.map(d => ({ ...d, n: 0, sum: 0 }));
    for (let i = 0; i < W * H; i++) if (g[i] !== b(i)) {
      diff++; const x = i % W, y = i / W | 0;
      for (const d of boxes) if (x >= d.x - 60 && x < d.x + 220 && y >= d.y - 60 && y < d.y + 260) { d.n++; d.sum += Math.abs(g[i] - b(i)); }
    }
    console.log(`\nManta page vs browser canvas: ${diff} of ${W * H} pixels differ; in the browser strokes' boxes: ` + boxes.map(d => `brush ${d.brush}: ${d.n} (mean |Δ| ${d.n ? (d.sum / d.n).toFixed(1) : 0})`).join(", "));
    png(path.join(OUT, "manta.png"), W, H, (x, y) => g[y * W + x]);
    png(path.join(OUT, "browser.png"), W, H, (x, y) => b(y * W + x));
    png(path.join(OUT, "diff.png"), W, H, (x, y) => 255 - Math.min(255, 4 * Math.abs(g[y * W + x] - b(y * W + x))));
    fs.writeFileSync(path.join(OUT, "drawn.json"), JSON.stringify(drawn));
    if (compared.length) {  // a row a slot: the Manta's pen, the browser's pen, the browser's mouse; from the Manta's page
      const cw = 1785, ch = 400;
      png(path.join(OUT, "compare.png"), cw, ch * compared.length, (x, y) => { const c = compared[y / ch | 0], py = c.y - 100 + y % ch; return py < H ? g[py * W + x] : 255; });
      console.log("compare.png: " + compared.map(c => `${c.name} (${c.size}, grey ${c.grey})`).join("; ") + " — left the Manta's pen, middle the browser's pen (pressure 1), right a mouse");
    }
  } else console.log("\nthe Manta's page didn't come again after the restart");
  fs.writeFileSync(path.join(OUT, "manta.log"), log.map(l => `${l.t} ${l.text}`).join("\n"));
  await browser.close();
  console.log(`PNGs and logs in ${OUT}`);
  process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
