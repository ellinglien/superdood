// The Manta's notebooks on a live page (books.js, LiveBooks, LivePage; docs/superpowers/specs/2026-11-01-manta-host-design.md,
// "Notebooks from a computer or iPad") without the Manta: a stand-in Manta on the JVM (LiveHostChecks manta: LiveHost
// with its notebooks over a library of two, the Manta's side of a live page in plain Java), headless Chrome as an iPad
// (puppeteer-core from ~/node_modules). Checks: the shelf and downloads refused without the key; Menu → Notebook Explorer
// shows the Manta's two notebooks with their covers; a tile's hold downloads its PDF and file as the Manta serves them
// (LiveHostChecks checks those are its writers' bytes); a tile's tap opens it on the Manta and here; › turns the Manta to
// page 2; a stroke there is laid on the Manta's page and saved in its file; the Manta's own turn turns the browser;
// Export Page downloads page 2's PNG at Page and its Layers at High Res, each from its size menu; the Manta's own block drawn, half erased, its layer hidden and shown and Clear Page
// show in the browser as on the Manta; the Manta's wet oil moving after the lift shows here as it goes and at rest; the shelf's Import sends a notebook up as a new one ("Birds 2") and refuses a bad file;
// the Manta gone, the page is covered ("Not connected") and takes no stroke, then
// the Manta back, it draws again. With --shots, docs/archive/pictures/live/books-*.png.
//   bash web/engine/build.sh && node scripts/live-probe/books-check.cjs [port] [--shots]
"use strict";
const { spawn, execFileSync } = require("child_process"), path = require("path"), os = require("os"), fs = require("fs"), http = require("http"), readline = require("readline");
const puppeteer = require(path.join(os.homedir(), "node_modules", "puppeteer-core"));
const CHROME = process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const REPO = path.join(__dirname, "..", ".."), args = process.argv.slice(2), PORT = +(args.find(a => /^\d+$/.test(a)) || 8771), SHOTS = args.includes("--shots"), KEY = "k7qmw2xrh4tnpd9e";
const IPAD = [820, 1180, 2];
const sleep = ms => new Promise(r => setTimeout(r, ms));
let n = 0, fail = 0;
const ok = (c, what) => { n++; if (!c) fail++; console.log((c ? "ok   " : "FAIL ") + what); };
const shot = (p, name) => SHOTS ? p.screenshot({ path: path.join(REPO, "docs", "archive", "pictures", "live", "books-" + name + ".png") }) : null;
const java = p => path.join(process.env.JAVA_HOME || "/Applications/Android Studio.app/Contents/jbr/Contents/Home", "bin", p);
const work = fs.mkdtempSync(path.join(os.tmpdir(), "books-check-")), library = path.join(work, "library");
const get = url => new Promise((res, rej) => http.get(url, a => { const b = []; a.on("data", d => b.push(d)); a.on("end", () => res({ status: a.statusCode, bytes: Buffer.concat(b) })); }).on("error", rej));
// A /books route asked as books.js asks it (the query sealed with the link's key; live-crypt.cjs, Node's own AES-GCM),
// the answer opened: the name to save under, the bytes; and the answer as it travels (wire).
const LC = require("./live-crypt.cjs");
async function asked(at, route, query) {
  const r = await get(at + route + "?key=" + LC.access(KEY) + "&q=" + LC.query(KEY, query)), b = LC.open(KEY, r.bytes), nl = b.indexOf(10);
  return { name: b.subarray(0, nl).toString(), bytes: b.subarray(nl + 1), wire: r.bytes };
}

// The stand-in Manta: its lines (JSON) as they come; "state" asked on stdin.
let manta = null, lines = [];
function startManta() {
  manta = spawn(java("java"), ["-cp", work, "io.github.mpdairy.monopaint.LiveHostChecks", "manta", String(PORT), KEY, path.join(work, "web"), library], { stdio: ["pipe", "pipe", "inherit"], cwd: REPO });
  readline.createInterface({ input: manta.stdout }).on("line", l => { if (l.startsWith("{")) lines.push(JSON.parse(l)); });
}
async function state() {
  lines = lines.filter(l => !("path" in l));
  manta.stdin.write("state\n");
  for (let t = 0; t < 100; t++) { const s = lines.find(l => "path" in l); if (s) return s; await sleep(50); }
  throw new Error("no state");
}

(async () => {
  execFileSync(java("javac"), ["-nowarn", "-d", work, "-sourcepath", path.join(REPO, "app", "src", "main", "java"), path.join(REPO, "app", "src", "test", "java", "io", "github", "mpdairy", "monopaint", "LiveHostChecks.java")], { stdio: ["ignore", "ignore", "inherit"] });
  execFileSync("python3", [path.join(__dirname, "relay.py"), "pack", path.join(work, "web")]);
  startManta();
  await sleep(1500);
  const at = `http://127.0.0.1:${PORT}`;
  ok((await get(at + "/books")).status === 403 && (await get(at + "/books?key=wrong")).status === 403 && (await get(at + "/books/file?path=2026-10-30_Birds")).status === 403
    && (await get(at + "/books?key=" + KEY)).status === 403, "The shelf and a download refused without the key's access, or another, or the key itself");
  const b = await puppeteer.launch({ executablePath: CHROME, headless: "new", args: ["--disable-gpu", "--disable-background-timer-throttling", "--disable-renderer-backgrounding"], protocolTimeout: 120000 });
  const errs = [];
  try {
    const p = await b.newPage();
    p.on("console", m => { if ((m.type() === "error" || m.type() === "warning") && !/^Failed to load resource/.test(m.text())) errs.push(m.text()); });
    p.on("pageerror", e => errs.push("pageerror: " + e.message));
    await p.setViewport({ width: IPAD[0], height: IPAD[1], deviceScaleFactor: IPAD[2] });
    await p.goto(`${at}/live#${KEY}`, { waitUntil: "load" });
    await p.waitForFunction(() => window.guest && guest.ready && guest.BK && guest.BK.shown, { timeout: 60000 });
    // Downloads caught: the file's name (from the Manta) and bytes.
    await p.evaluate(() => {
      window.downloads = [];
      const click = HTMLAnchorElement.prototype.click;
      HTMLAnchorElement.prototype.click = function () { if (this.download !== undefined && this.hasAttribute("download")) { const name = this.download; fetch(this.href).then(async r => downloads.push({ name, bytes: [...new Uint8Array(await r.arrayBuffer())] })); } else click.call(this); };
    });
    const downloaded = async () => { await p.waitForFunction(() => downloads.length > 0, { timeout: 60000 }); return p.evaluate(() => downloads.shift()); };
    const centre = (sel, i = 0) => p.evaluate((s, i) => { const e = document.querySelectorAll(s)[i]; if (!e) return null; const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }, sel, i);
    const byText = (sel, t) => p.evaluate((s, t) => { const e = [...document.querySelectorAll(s)].find(e => e.textContent.trim() === t || e.getAttribute("aria-label") === t); if (!e) return null; const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }, sel, t);
    const tap = async (xy, wait = 300) => { if (!xy) throw new Error("nothing to tap"); await p.mouse.click(xy[0], xy[1]); await sleep(wait); };
    const hold = async xy => { await p.mouse.move(xy[0], xy[1]); await p.mouse.down(); await sleep(800); await p.mouse.up(); await sleep(300); };
    const dark = () => p.evaluate(() => { let k = 0; for (const g of guest.S) if (g < 250) k++; return k; });
    const shownPage = () => p.evaluate(() => ({ path: guest.BK.shown.path, place: guest.BK.shown.place, bar: document.getElementById("turn").textContent }));
    const waitShown = (path, place) => p.waitForFunction((a, b) => guest.BK.shown && guest.BK.shown.path === a && guest.BK.shown.place === b && !guest.BK.covered, { timeout: 20000 }, path, place);
    // The Pencil (CDP's pen, with force), as live-check.cjs draws, across the page at y (a share of its height) wherever the view puts it.
    const cdp = await p.target().createCDPSession();
    async function stroke(y) {
      const s = await p.evaluate(() => { const r = document.getElementById("view").getBoundingClientRect(), V = guest.V, d = devicePixelRatio; return [r.x + V.x / d, r.y + V.y / d, guest.W * V.s / d, guest.H * V.s / d]; }), t0 = Date.now() / 1000;
      const ev = (type, k, force) => cdp.send("Input.dispatchMouseEvent", { type, x: s[0] + s[2] * (.2 + .6 * k / 24), y: s[1] + s[3] * (y + .05 * Math.sin(k / 3)), button: "left", buttons: type === "mouseReleased" ? 0 : 1, clickCount: 1, pointerType: "pen", force, timestamp: t0 + k * .008 });
      await ev("mousePressed", 0, .3);
      for (let k = 1; k <= 24; k++) { await ev("mouseMoved", k, .7); await sleep(8); }
      await ev("mouseReleased", 24, 0); await sleep(600);
    }

    const first = await shownPage(), m0 = await state();
    ok(first.path === m0.path && first.place === m0.place && /\d \/ \d/.test(first.bar), "On joining, the Manta's notebook page and its bar: " + JSON.stringify(first));

    // Menu → Notebook Explorer: the Manta's shelf.
    await tap(await centre("#menu"));
    await tap(await byText(".pop .mrow", "Notebook Explorer"), 1500);
    const tiles = await p.evaluate(() => [...document.querySelectorAll("#books .tile")].map(t => t.getAttribute("aria-label")));
    ok(tiles.length === 2 && /Harbour Sketches, 3 pages/.test(tiles[0]) && /Birds, 2 pages/.test(tiles[1]), "The Manta's shelf: its two notebooks, newest first, no New Notebook: " + JSON.stringify(tiles));
    const covered = await p.evaluate(() => [...document.querySelectorAll("#books .tile canvas")].map(c => { const d = c.getContext("2d").getImageData(0, 0, c.width, c.height).data; let k = 0; for (let i = 0; i < d.length; i += 4) if (d[i] < 250) k++; return k; }));
    ok(covered.every(k => k > 1000), "Their covers drawn: " + covered.join(", ") + " px not white");
    await shot(p, "shelf");
    // A hold: PDF, Notebook File.
    await hold(await centre("#books .tile", 1));
    ok(await p.evaluate(() => [...document.querySelectorAll(".pop .mrow")].map(r => r.textContent.trim()).join(",")) === "PDF,Notebook File", "A tile's hold offers PDF and Notebook File");
    await shot(p, "download");
    await tap(await byText(".pop .mrow", "PDF"));
    let got = await downloaded(), want = await asked(at, "/books/pdf", "path=2026-10-30_Birds");
    ok(got.name === "Birds.pdf" && want.name === got.name && Buffer.compare(Buffer.from(got.bytes), want.bytes) === 0 && want.bytes.subarray(0, 5).toString() === "%PDF-", `Its PDF downloads as ${got.name} (${got.bytes.length} bytes), the Manta's bytes`);
    ok(!want.wire.includes("%PDF") && !want.wire.includes("Birds"), "and travels sealed: neither the PDF nor its name readable on the way");
    await hold(await centre("#books .tile", 1));
    await tap(await byText(".pop .mrow", "Notebook File"));
    got = await downloaded();
    ok(got.name === "2026-10-30_Birds.mpaint" && Buffer.compare(Buffer.from(got.bytes), fs.readFileSync(path.join(library, "2026-10-30_Birds.mpaint"))) === 0, "Its file downloads as " + got.name + ", the Manta's file");

    // A tap opens it on the Manta, and here.
    await tap(await centre("#books .tile", 1), 300);
    await waitShown("2026-10-30_Birds", (await state()).place);
    const m1 = await state();
    ok(m1.path === "2026-10-30_Birds" && (await shownPage()).path === m1.path && Math.abs(await dark() - m1.marks) < m1.marks * .1 + 50, `A tap opens it on the Manta and here, on its page ${m1.place + 1} (${m1.marks} marks there, ${await dark()} here)`);
    // To page 2 with the bar (‹ or ›), and draw there.
    for (let at = m1.place; at > 0; at--) { await tap(await byText("#turn button", "Previous page")); await waitShown("2026-10-30_Birds", at - 1); }
    const m15 = await state();
    ok(m15.place === 0 && /1 \/ 2/.test((await shownPage()).bar), "The bar's ‹ turns the Manta to page 1: " + JSON.stringify(await shownPage()));
    await tap(await byText("#turn button", "Next page")); await waitShown("2026-10-30_Birds", 1);
    const m2 = await state();
    ok(m2.place === 1 && /2 \/ 2/.test((await shownPage()).bar) && Math.abs(await dark() - m2.marks) < 60, "Its › turns the Manta to page 2, shown here as there: " + JSON.stringify(await shownPage()));
    await stroke(.75);
    let m3 = null; for (let t = 0; t < 40; t++) { m3 = await state(); if (m3.laid > m2.laid) break; await sleep(150); }
    ok(m3.laid > m2.laid && m3.marks > m2.marks + 200 && m3.saved === m3.marks, `A stroke on page 2 is laid on the Manta's page (${m2.marks} → ${m3.marks} marks) and saved in its file (${m3.saved})`);
    ok(Math.abs(await dark() - m3.marks) < 60, `The browser and the Manta show the same page (${await dark()} and ${m3.marks} marks)`);
    await shot(p, "page");
    // Export Page: page 2's PNG.
    await tap(await centre("#menu"));
    await tap(await byText(".pop .mrow", "Export Page"));
    ok(await p.evaluate(() => [...document.querySelectorAll(".pop .mrow")].map(r => r.textContent.trim()).join(",")) === "PDF,PNG,Layers", "Export Page offers PDF, PNG and Layers");
    await tap(await byText(".pop .mrow", "PNG"));
    const sizes = await p.evaluate(() => [...document.querySelectorAll(".pop .mrow")].map(r => r.textContent.trim()));
    ok(sizes.length === 3 && /^Social, \d+ × \d+$/.test(sizes[0]) && /^Page, \d+ × \d+$/.test(sizes[1]) && /^High Res, \d+ × \d+, sharp$/.test(sizes[2]), "Export Page's PNG offers its sizes: " + sizes.join("; "));
    await tap(await byText(".pop .mrow", sizes[1]));
    got = await downloaded(); want = await asked(at, "/books/page", "path=2026-10-30_Birds&place=1&as=1");
    ok(got.name === "Birds p2.png" && Buffer.compare(Buffer.from(got.bytes), want.bytes) === 0 && want.bytes[1] === 0x50, `Export Page's PNG at Page downloads as ${got.name}, the Manta's page 2 with the stroke`);
    // Layers at High Res: the Manta's bytes for size=2, twice the page.
    await tap(await centre("#menu"));
    await tap(await byText(".pop .mrow", "Export Page"));
    await tap(await byText(".pop .mrow", "Layers"));
    const big = await p.evaluate(() => [...document.querySelectorAll(".pop .mrow")].map(r => r.textContent.trim()).pop());
    ok(/^High Res, \d+ × \d+, sharp, large file$/.test(big), "Layers' High Res says its file is large: " + big);
    await tap(await byText(".pop .mrow", big));
    got = await downloaded(); want = await asked(at, "/books/page", "path=2026-10-30_Birds&place=1&as=2&size=2");
    ok(got.name === "Birds p2.psd" && Buffer.compare(Buffer.from(got.bytes), want.bytes) === 0 && want.bytes.subarray(0, 4).toString() === "8BPS"
      && want.bytes.readUInt32BE(18) + " × " + want.bytes.readUInt32BE(14) === big.match(/\d+ × \d+/)[0], `Export Page's Layers at High Res downloads as ${got.name} (${got.bytes.length} bytes), the Manta's, ${big.match(/\d+ × \d+/)[0]}`);
    // The Manta's own whole-page changes reach the browser over the marks of both (the owner, 2026-11-02: "i cleared the
    // page but the desktop version kept the art"): a guest's wash, then the Manta's block drawn, half erased, its layer
    // hidden and shown, Clear Page; after each the browser shows what the Manta shows.
    await p.evaluate(() => guest.choose(4)); await stroke(.55); await p.evaluate(() => guest.choose(2)); await sleep(2500);
    for (const what of ["draw", "erase", "hide", "show", "clear"]) {
      lines = lines.filter(l => !("edited" in l)); manta.stdin.write(what + "\n");
      for (let t = 0; t < 100 && !lines.some(l => "edited" in l); t++) await sleep(50);
      await sleep(1200);
      // Cleared: nothing on the screen (its 16 greys; a tone the browser sent as white, 247 to 249, stays in its engine).
      const m = await state(), here = await dark(), shown = await p.evaluate(() => guest.S.filter(g => Math.round(g / 17) * 17 < 255).length);
      ok(Math.abs(here - m.marks) < 60 && (what !== "clear" || shown === 0 && m.marks === 0), `The Manta's ${what} shows here as there (${here} marks here, ${m.marks} there${what === "clear" ? ", " + shown + " on the screen" : ""})`);
    }
    // The owner's oil (2026-11-02: Rob boss, wet, keeps moving after the lift): what its ticks change reaches the browser
    // as it goes, not only at the lift and at rest. Held 40 ticks in (still moving), then on to rest: each time the
    // browser's page is the Manta's within the 16 greys it shows.
    const oilOff = async (paper = null) => {
      lines = lines.filter(l => !("tones" in l)); manta.stdin.write("tones\n");
      let m = null; for (let t = 0; t < 100 && !(m = lines.find(l => "tones" in l)); t++) await sleep(50);
      const there = Buffer.from(m.tones, "base64"), [x, y, w, h] = m.box;
      const here = Buffer.from(await p.evaluate((x, y, w, h) => { const o = []; for (let r = y; r < y + h; r++) for (let c = x; c < x + w; c++) o.push(guest.S[r * guest.W + c]); return o; }, x, y, w, h));
      let off = 0, inked = 0, over = 0;
      for (let i = 0; i < there.length; i++) {
        if (there[i] < 250) inked++;
        if (Math.abs(there[i] - here[i]) > 8 && Math.round(there[i] / 17) !== Math.round(here[i] / 17)) { if (!paper || paper[i] >= 250) off++; else over++; }
      }
      return { off, inked, over, ticks: m.ticks, there };
    };
    const oilLine = async key => { for (let t = 0; t < 600 && !lines.some(l => key in l); t++) await sleep(50); return lines.find(l => key in l); };
    lines = lines.filter(l => !("held" in l || "rested" in l)); manta.stdin.write("oil 40\n");
    const held = await oilLine("held"); await sleep(1500);
    const mid = await oilOff();
    // (Sent only at the lift and at rest, as before 2026-11-02: 15496 px off here.)
    ok(held && mid.inked > 20000 && mid.off === 0, `The Manta's oil moving after the lift reaches the browser as it goes (held at ${mid.ticks} ticks): ${mid.off} px off of ${mid.inked} inked`);
    manta.stdin.write("go on\n");
    const rested = await oilLine("rested"); await sleep(1500);
    const end = await oilOff();
    ok(rested && end.off === 0, `and at rest (${end.ticks} ticks): ${end.off} px off`);
    // The other way: the browser's own oil (the Manta's Rob boss slot) moving after its lift reaches the Manta as it goes
    // (live.html's wetTick) and at rest, where the Manta's page was paper. Over the Manta's own oil its ticks' darker
    // pixels go on as ink, under its paint (near, as any guest's ink there), so the Manta's layers stay; exact there only
    // by flattening them, which a tick did until 2026-11-02 (LiveUndoChecks.lighter).
    const paper = (await oilOff()).there;
    await p.evaluate(() => guest.choose(6)); await stroke(.45);
    const liftLaid = (await state()).laid;
    await p.waitForFunction(() => !guest.P.settling, { timeout: 60000, polling: 100 }); await p.evaluate(() => guest.choose(2)); await sleep(2000);
    const guestOil = await oilOff(paper), ownLaid = (await state()).laid;
    ok(ownLaid > liftLaid + 1 && guestOil.off === 0, `The browser's own oil moving after its lift reaches the Manta as it goes (${ownLaid - liftLaid} changes after the lift) and at rest: ${guestOil.off} px off (${guestOil.over} near over the Manta's oil)`);
    await shot(p, "oil");
    // The Manta turns on its own: the browser follows.
    manta.stdin.write("turn -1\n");
    await waitShown("2026-10-30_Birds", 0);
    const m4 = await state();
    ok(m4.place === 0 && Math.abs(await dark() - m4.marks) < m4.marks * .1 + 50 && /1 \/ 2/.test((await shownPage()).bar), `The Manta's own turn turns the browser to page 1 (${await dark()} marks here, ${m4.marks} there)`);
    // The shelf's Import: a notebook's file sent up (its name taken: a new notebook beside it), and a bad one refused.
    const birds = fs.readFileSync(path.join(library, "2026-10-30_Birds.mpaint")), up = path.join(work, "up");
    fs.mkdirSync(up); fs.writeFileSync(path.join(up, "2026-10-30_Birds.mpaint"), birds); fs.writeFileSync(path.join(up, "junk.mpaint"), "not a notebook");
    await tap(await centre("#menu"));
    await tap(await byText(".pop .mrow", "Notebook Explorer"), 1500);
    // What goes up, as a listener on the Wi-Fi would read it.
    const ups = []; p.on("request", r => { if (/\/books\/add/.test(r.url())) ups.push({ url: r.url(), body: Buffer.from(r.postData() || "", "latin1") }); });
    const [chooser] = await Promise.all([p.waitForFileChooser(), p.click("#books .lb.boxed")]);
    await chooser.accept([path.join(up, "2026-10-30_Birds.mpaint"), path.join(up, "junk.mpaint")]);
    await p.waitForFunction(() => document.querySelectorAll("#books .tile").length === 3, { timeout: 20000 });
    const upTiles = await p.evaluate(() => [...document.querySelectorAll("#books .tile")].map(t => t.getAttribute("aria-label")));
    const sentUp = path.join(library, "2026-10-30_Birds 2.mpaint");
    ok(fs.existsSync(sentUp) && Buffer.compare(fs.readFileSync(sentUp), birds) === 0 && Buffer.compare(fs.readFileSync(path.join(library, "2026-10-30_Birds.mpaint")), birds) === 0,
      "Import sends a notebook's file up: a new notebook, \"Birds 2\", beside the one it is named after, both as sent");
    ok(upTiles.length === 3 && upTiles.some(t => /^Birds 2, 2 pages/.test(t)) && !fs.readdirSync(library).some(f => /junk|\.new$/.test(f)),
      "The shelf shows it; a file that isn't a notebook isn't kept: " + JSON.stringify(upTiles));
    ok(await p.evaluate(() => document.getElementById("note").textContent) === "Can't read it", "That one's note: Can't read it");
    ok(ups.length === 2 && ups.every(u => !/name=|Birds|junk|k7qmw2xrh4tnpd9e/.test(decodeURIComponent(u.url)) && u.body.length > 28 && !u.body.includes("PK\x03\x04") && !u.body.includes("not a notebook"))
      && LC.open(KEY, ups.find(u => u.body.length > 100).body).equals(birds),
      `The uploads travel sealed: no file name in the address, no file readable in the body, the body the file sealed with the link's key (${ups.length})`);
    // A notebook's file dropped on the shelf (a desktop's): Import's, a new notebook "Birds 3".
    await p.evaluate(bytes => {
      const dt = new DataTransfer(); dt.items.add(new File([new Uint8Array(bytes)], "2026-10-30_Birds.mpaint"));
      const shelf = document.getElementById("books"), ev = t => { const e = new DragEvent(t, { dataTransfer: dt, bubbles: true, cancelable: true }); shelf.dispatchEvent(e); return e.defaultPrevented; };
      window.dropTaken = ev("dragover") && document.querySelector(".drophint")?.textContent === "Drop to Import · PDFs, pictures" && ev("drop") && !document.querySelector(".drophint");
    }, [...birds]);
    await p.waitForFunction(() => document.querySelectorAll("#books .tile").length === 4, { timeout: 20000 }).catch(() => {});
    const dropped = path.join(library, "2026-10-30_Birds 3.mpaint");
    ok(await p.evaluate(() => window.dropTaken) && fs.existsSync(dropped) && Buffer.compare(fs.readFileSync(dropped), birds) === 0,
      "A notebook's file dropped on the shelf (its hint shown while over it) goes up as Import's: a new notebook, \"Birds 3\"");
    await tap(await byText("#books button", "Back"));

    // The Manta gone: covered, no strokes; back, drawing again.
    manta.kill(); await sleep(2500);
    const off = await p.evaluate(() => ({ covered: guest.BK.covered, words: document.getElementById("books-shield").textContent }));
    ok(off.covered && off.words === "Not connected", "The Manta gone: the page covered, \"Not connected\": " + JSON.stringify(off));
    await shot(p, "offline");
    const before = await dark();
    await stroke(.4);
    ok(await dark() === before, "No stroke while not connected");
    startManta();
    await p.waitForFunction(() => !guest.BK.covered, { timeout: 20000 });
    ok(true, "The Manta back: the page uncovered");
    ok(errs.length === 0, "No errors: " + errs.slice(0, 5).join(" | "));
  } catch (e) { fail++; console.log("FAIL " + (e.stack || e) + "\n" + errs.join("\n")); }
  finally { await b.close(); if (manta) manta.kill(); fs.rmSync(work, { recursive: true, force: true }); }
  console.log(fail ? `${fail} of ${n} failed` : `all ${n} passed`);
  process.exit(fail ? 1 : 0);
})();
