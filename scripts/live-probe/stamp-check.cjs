// The Stamp, Cut Stamp and Import Image on the browser's drawing screen (live.html with stamp.js) without the Manta: a
// relay of its own, headless Chrome (puppeteer-core from ~/node_modules), an iPad in portrait drawing and a phone watching.
// A slot holding a Stamp offers Stamp ▾, its menu's presets set the brush; Cut Stamp (the menu's and Brush Lab's; the
// Tools page's went, 2026-11-03) cuts a nib from the page, kept in this browser, an empty lasso says so; the stamp draws and the watcher
// gets its pixels; Import places a picture from the file picker or a drop: moved, made Lighter, Darker, Less and More Contrast, Place lays it (the watcher gets it),
// Undo takes it back, Cancel lays nothing. With --shots, docs/archive/pictures/live/stamp-*.png.
//   bash web/engine/build.sh && node scripts/live-probe/stamp-check.cjs [port] [--shots]
"use strict";
const { spawn } = require("child_process"), path = require("path"), os = require("os"), fs = require("fs");
const puppeteer = require(path.join(os.homedir(), "node_modules", "puppeteer-core"));
const CHROME = process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const REPO = path.join(__dirname, "..", ".."), args = process.argv.slice(2), PORT = +(args.find(a => /^\d+$/.test(a)) || 8783), SHOTS = args.includes("--shots");
const IPAD = [820, 1180, 2], PHONE = [390, 844, 3];
const sleep = ms => new Promise(r => setTimeout(r, ms));
let n = 0, fail = 0;
const ok = (c, what) => { n++; if (!c) fail++; console.log((c ? "ok   " : "FAIL ") + what); };
const shot = (p, name) => SHOTS ? p.screenshot({ path: path.join(REPO, "docs", "archive", "pictures", "live", "stamp-" + name + ".png") }) : null;

(async () => {
  const relay = spawn("python3", [path.join(__dirname, "relay.py"), String(PORT)], { stdio: ["ignore", "ignore", "inherit"] });
  await sleep(700);
  const b = await puppeteer.launch({ executablePath: CHROME, headless: "new", args: ["--disable-gpu", "--disable-background-timer-throttling", "--disable-renderer-backgrounding", "--disable-backgrounding-occluded-windows"], protocolTimeout: 120000 });
  const errs = [], tmp = fs.mkdtempSync(path.join(os.tmpdir(), "stamp-check-"));
  async function open([w, h, scale], where, touch) {
    const p = await b.newPage();
    p.on("console", m => { if ((m.type() === "error" || m.type() === "warning") && !/^Failed to load resource/.test(m.text())) errs.push(m.text()); });
    p.on("response", r => { if (r.status() >= 400 && !/favicon\.ico$|tip_[a-z0-9_]+\.xml$/.test(r.url())) errs.push(r.status() + " " + r.url()); });
    p.on("pageerror", e => errs.push("pageerror: " + e.message));
    await p.setViewport({ width: w, height: h, deviceScaleFactor: scale, hasTouch: !!touch, isMobile: !!touch });
    await p.goto(`http://127.0.0.1:${PORT}/${where}`, { waitUntil: "load" });
    await p.waitForFunction(w => window.guest && window.guest.ready, { timeout: 60000 });
    return p;
  }
  const viewBox = p => p.evaluate(() => { const r = document.getElementById("view").getBoundingClientRect(); return [r.x, r.y, r.width, r.height]; });
  async function stroke(p, pts) {
    await p.mouse.move(pts[0][0], pts[0][1]); await p.mouse.down();
    for (const q of pts.slice(1)) { await p.mouse.move(q[0], q[1]); await sleep(8); }
    await p.mouse.up(); await sleep(300);
  }
  const across = async (p, u0, v0, u1, v1, k = 20) => { const [x, y, w, h] = await viewBox(p); return Array.from({ length: k + 1 }, (_, i) => [x + w * (u0 + (u1 - u0) * i / k), y + h * (v0 + (v1 - v0) * i / k)]); };
  // A loop round the view's fractions u0..u1, v0..v1.
  const round = async (p, u0, v0, u1, v1) => { const [x, y, w, h] = await viewBox(p); return [[u0, v0], [u1, v0], [u1, v1], [u0, v1], [u0, v0 + .01]].flatMap(([u, v], i, a) => i ? Array.from({ length: 6 }, (_, k) => [x + w * (a[i - 1][0] + (u - a[i - 1][0]) * (k + 1) / 6), y + h * (a[i - 1][1] + (v - a[i - 1][1]) * (k + 1) / 6)]) : [[x + w * u, y + h * v]]); };
  const tap = async (p, xy) => { await p.mouse.click(xy[0], xy[1]); await sleep(200); };
  const hold = async (p, xy, ms = 700) => { await p.mouse.move(xy[0], xy[1]); await p.mouse.down(); await sleep(ms); await p.mouse.up(); await sleep(250); };
  const centre = (p, sel, i = 0) => p.evaluate((s, i) => { const e = document.querySelectorAll(s)[i]; if (!e) return null; const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }, sel, i);
  const byText = (p, sel, text) => p.evaluate((s, t) => { const e = [...document.querySelectorAll(s)].find(e => e.textContent.trim() === t || e.getAttribute("aria-label") === t); if (!e) return null; const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }, sel, text);
  const dark = p => p.evaluate(() => { const S = guest.S; let k = 0; for (let i = 0; i < S.length; i++) if (S[i] < 200) k++; return k; });
  const note = p => p.evaluate(() => document.getElementById("note").textContent);
  const nibsKept = p => p.evaluate(() => Object.keys(guest.stored()).filter(k => k.startsWith("stamp_nib_")).length);
  const menuKey = (p, slot) => p.evaluate(s => JSON.parse(guest.E.T.stampMenu(s, innerWidth, innerHeight)).key, slot);
  // A picture made in the page: black on its left half, white on its right, as a PNG file on disk (for the file picker).
  async function png(p) {
    const b64 = await p.evaluate(async () => {
      const c = new OffscreenCanvas(200, 100), x = c.getContext("2d"); x.fillStyle = "#fff"; x.fillRect(0, 0, 200, 100); x.fillStyle = "#000"; x.fillRect(0, 0, 100, 100);
      const bytes = new Uint8Array(await (await c.convertToBlob({ type: "image/png" })).arrayBuffer()); let s = ""; for (const v of bytes) s += String.fromCharCode(v); return btoa(s);
    });
    const file = path.join(tmp, "half.png"); fs.writeFileSync(file, Buffer.from(b64, "base64")); return file;
  }
  async function importFile(p, file) {
    await tap(p, await centre(p, "[data-m4=menu]"));
    const [chooser] = await Promise.all([p.waitForFileChooser({ timeout: 5000 }), tap(p, await byText(p, ".pop .mrow", "Import"))]);
    await chooser.accept([file]);
    await p.waitForFunction(() => guest.ST.placing, { timeout: 5000 }).catch(() => {});
    await sleep(200);
  }
  try {
    const p = await open(IPAD, "live"), watcher = await open(PHONE, "live", true);
    await p.bringToFront();
    // ---- The Stamp in slot 3, its picker's Stamp ▾ and its menu ----
    // The Stamp is on the shelf, not in the picker (pare-down, 2026-11-05): Library, More Brushes, its card, Back.
    await hold(p, await centre(p, "#column > button", 2));
    await tap(p, await byText(p, ".pop .mrow", "Library"));
    await tap(p, await byText(p, "#screen .chip", "More Brushes"));
    for (let i = 0; i < 8 && !await byText(p, "#screen .grid .tname", "Stamp"); i++) await tap(p, await byText(p, "#screen button", "Next page"));
    await tap(p, await byText(p, "#screen .grid .tname", "Stamp"));
    await tap(p, await byText(p, "#screen .bar .lb", "Back"));
    await hold(p, await centre(p, "#column > button", 2));
    const pk = await p.evaluate(() => [...document.querySelectorAll(".pop .mrow")].map(r => r.textContent));
    ok(pk.includes("Stamp ▾") && pk.indexOf("Stamp ▾") < pk.indexOf("Edit Brush"), "A Stamp's picker offers Stamp ▾ over Edit Brush: " + pk.join(", "));
    await tap(p, await byText(p, ".pop .mrow", "Stamp ▾"));
    const menu = await p.evaluate(() => { const m = document.querySelector(".pop[data-stamp]"); return m && { slot: m.dataset.stamp, rows: [...m.querySelectorAll(".mrow")].map(r => r.textContent + (r.classList.contains("marked") ? "*" : "")), labels: [...m.querySelectorAll(".otitle")].map(t => t.textContent), card: !!m.querySelector(".cell canvas") }; });
    ok(menu && menu.slot === "2" && menu.card && menu.labels.join() === "Turns,Spacing,Ink Fade" && menu.rows.join() === "Make Stamp,On*,Off,12%*,50%,100%,None,22%*,Strong", "Stamp ▾: its card, Cut Stamp, Turns, Spacing and Ink Fade as the Manta's: " + JSON.stringify(menu));
    await shot(p, "menu");
    const before = await menuKey(p, 2);
    await tap(p, await byText(p, ".pop[data-stamp] .mrow", "50%"));
    const spaced = await p.evaluate(() => [...document.querySelectorAll(".pop[data-stamp] .mrow.marked")].map(r => r.textContent).join());
    ok(spaced === "On,50%,22%" && (await menuKey(p, 2)) !== before, "A chip sets the Stamp's Spacing, the menu drawn again in place: " + spaced);

    // ---- Cut Stamp: a mark drawn with slot 1, a lasso round it ----
    await p.mouse.click(5, 5); await sleep(150);  // the menu closed
    await tap(p, await centre(p, "#column > button", 0));
    await stroke(p, await across(p, .3, .3, .45, .35));
    const marked = await dark(p);
    await hold(p, await centre(p, "#column > button", 2));
    await tap(p, await byText(p, ".pop .mrow", "Stamp ▾"));
    await tap(p, await byText(p, ".pop[data-stamp] .mrow", "Make Stamp"));
    ok((await note(p)) === "Draw round the shape to cut" && (await p.evaluate(() => guest.ST.cutting)) === 2, "Cut Stamp: \"Draw round the shape to cut\", the pen's next stroke the lasso");
    const nibCode = await menuKey(p, 2), kept = await nibsKept(p);
    await stroke(p, await round(p, .25, .25, .5, .4));
    ok((await note(p)) === "Stamp cut" && (await menuKey(p, 2)) !== nibCode && (await nibsKept(p)) === kept + 1, "A lasso round the mark: \"Stamp cut\", the slot's Stamp prints it, the nib kept in this browser");
    ok((await dark(p)) === marked && (await p.evaluate(() => guest.ST.cutting)) === -1, "The lasso draws nothing on the page, and ends the cut");
    await hold(p, await centre(p, "#column > button", 2));
    await tap(p, await byText(p, ".pop .mrow", "Stamp ▾"));
    await tap(p, await byText(p, ".pop[data-stamp] .mrow", "Make Stamp"));
    await stroke(p, await round(p, .6, .7, .8, .8));
    ok((await note(p)) === "Nothing to cut", "An empty lasso: \"Nothing to cut\"");

    // ---- The stamp draws, and the watcher gets its pixels ----
    await tap(p, await centre(p, "#column > button", 2));
    // At the Stamp's own Medium size (the slot kept the fine pen's 4 px: a Basic tool brings no size of its own).
    await p.evaluate(() => { guest.E.T.look(2, 132, guest.dark[2]); guest.px[2] = 132; });
    const w0 = await dark(watcher), d0 = await dark(p);
    await stroke(p, await across(p, .2, .6, .8, .62, 30));
    await sleep(1200);
    const d1 = await dark(p), w1 = await dark(watcher);
    ok(d1 - d0 > 500, "The Stamp prints along the stroke: " + (d1 - d0) + " px");
    ok(Math.abs((w1 - w0) - (d1 - d0)) <= (d1 - d0) / 8, "The watcher (as the host, in 16 greys: a thin print's edges either side of the count's line) gets the stamp's pixels: " + (w1 - w0) + " against " + (d1 - d0));
    await shot(p, "stamped");

    // ---- Import: the menu's row, the picture placed, moved, Place ----
    const file = await png(p);
    await tap(p, await centre(p, "[data-m4=menu]"));
    const rows = await p.evaluate(() => [...document.querySelectorAll(".pop .mrow")].map(r => r.textContent));
    ok(rows.includes("Import") && rows.indexOf("Import") < rows.findIndex(r => /^Finger Action/.test(r)), "A guest's menu: Import, over the app's rows: " + rows.join(", "));
    await p.mouse.click(5, 5); await sleep(150);
    await importFile(p, file);
    const bar = await p.evaluate(() => { const b = document.querySelector("[data-placing]"), v = document.getElementById("view").getBoundingClientRect(); if (!b) return null; const r = b.getBoundingClientRect(); return { words: [...b.children].map(c => c.textContent), bottom: r.bottom, viewBottom: v.bottom, height: r.height, fits: b.scrollWidth <= b.clientWidth && b.firstChild.getBoundingClientRect().left >= r.left }; });
    ok((await p.evaluate(() => guest.ST.placing)) && bar && bar.words.join() === "Lighter,Darker,More Contrast,Less Contrast,Cancel,Place" && bar.fits && Math.abs(bar.bottom - bar.viewBottom) < 1 && bar.height === 64, "Import: the picture over the page, Lighter, Darker, More and Less Contrast, Cancel and Place along its foot, all in: " + JSON.stringify(bar));
    const f0 = await p.evaluate(() => [...guest.E.T.placeFrame()]);
    ok(Math.abs(f0[2] / f0[3] - 2) < .02 && f0[2] <= (await p.evaluate(() => guest.W)) - 112 + 1, "Fitted inside the page, its shape kept, the handle's room in: " + f0.slice(0, 4).map(Math.round).join(", "));
    const shownAt = () => p.evaluate(() => { const c = document.getElementById("page").getContext("2d"), f = guest.E.T.placeFrame(), d = c.getImageData(Math.round(f[0] + f[2] / 4), Math.round(f[1] + f[3] / 2), 1, 1).data; return d[0]; });
    const shownDark = await shownAt();
    ok(shownDark < 40 && (await dark(p)) === d1, "Shown as it will lie, the page itself unchanged until Place");
    // Lighter, three steps at most (PictureImport.Placing.step), its black shown greyer each; Darker back again.
    const shades = [];
    for (let i = 0; i < 4; i++) { await tap(p, await byText(p, "[data-placing] button", "Lighter")); shades.push(await shownAt()); }
    const offs = await p.evaluate(() => [...document.querySelectorAll("[data-placing] [data-step]")].map(k => k.disabled).join());
    ok(shades[0] > shownDark && shades[1] > shades[0] && shades[2] > shades[1] && shades[3] === shades[2] && offs === "true,false,false,false", "Lighter, a step a tap, three at most, then off: " + shades + " (" + offs + ")");
    for (let i = 0; i < 3; i++) await tap(p, await byText(p, "[data-placing] button", "Darker"));
    ok((await shownAt()) === shownDark && (await dark(p)) === d1, "Darker as many back: as it was, the page still unchanged");
    // Less Contrast draws its black toward the middle grey; More Contrast as many back. Shown as the Manta shows that
    // grey (WebEngine.shade): a few white dots in 64, so only a little lighter on screen.
    await tap(p, await byText(p, "[data-placing] button", "Less Contrast"));
    const flat = await shownAt();
    await tap(p, await byText(p, "[data-placing] button", "More Contrast"));
    ok(flat > shownDark && (await shownAt()) === shownDark && (await dark(p)) === d1, "Less Contrast greys its black (" + flat + "), More Contrast back: as it was");
    await shot(p, "placing");
    const [vx, vy] = await viewBox(p), dpr = 2, V = await p.evaluate(() => guest.V);
    const mid = [vx + (f0[0] + f0[2] / 2) * V.s / dpr + V.x / dpr, vy + (f0[1] + f0[3] / 2) * V.s / dpr + V.y / dpr];
    await stroke(p, Array.from({ length: 11 }, (_, i) => [mid[0], mid[1] - 8 * i]));
    const f1 = await p.evaluate(() => [...guest.E.T.placeFrame()]);
    ok(Math.abs((f0[1] - f1[1]) - 80 * dpr / V.s) < 3 && f1[2] === f0[2], "A drag moves it, its size kept: up " + Math.round(f0[1] - f1[1]) + " page px");
    const wBefore = await dark(watcher);
    await tap(p, await byText(p, "[data-placing] button", "Place"));
    await sleep(1200);
    const d2 = await dark(p), w2 = await dark(watcher), area = f1[2] / 2 * f1[3];
    ok(!(await p.evaluate(() => guest.ST.placing)) && d2 - d1 > area * .8, "Place lays it, its black half dark: " + (d2 - d1) + " px of " + Math.round(area));
    ok(Math.abs((w2 - wBefore) - (d2 - d1)) <= (d2 - d1) / 20, "The watcher (as the host) gets the picture: " + (w2 - wBefore) + " px");
    await shot(p, "placed");
    await p.evaluate(() => guest.undo()); await sleep(500);
    // Within the Manta's 16 greys a live Undo puts back (a few edge pixels either side of the count's line).
    ok(Math.abs((await dark(p)) - d1) <= 20, "Undo takes the picture back: " + (await dark(p)) + " against " + d1);
    const d3 = await dark(p);

    // ---- Cancel lays nothing; a drop places one too ----
    await importFile(p, file);
    await tap(p, await byText(p, "[data-placing] button", "Cancel"));
    ok(!(await p.evaluate(() => guest.ST.placing || !!document.querySelector("[data-placing]"))) && (await dark(p)) === d3, "Cancel: nothing laid, the bar gone");
    const shownBack = await p.evaluate(() => { const c = document.getElementById("page").getContext("2d"), W = guest.W, H = guest.H, d = c.getImageData(0, 0, W, H).data, S = guest.S, shade = Array.from({ length: 256 }, (_, t) => guest.E.T.shade(t)); let off = 0; for (let i = 0; i < S.length; i++) if (Math.abs(d[4 * i] - shade[S[i]]) > 1) off++; return off; });
    ok(shownBack === 0, "And the page shows as it is again: " + shownBack + " px differ");
    // Dragged over the page, the hint (live.js dropFiles): a dashed outline over the page, what a drop does; gone once it leaves.
    const hint = await p.evaluate(async () => {
      const dt = new DataTransfer(), view = document.getElementById("view"), at = () => document.querySelector(".drophint");
      dt.items.add(new File(["x"], "a.png", { type: "image/png" }));
      view.dispatchEvent(new DragEvent("dragover", { dataTransfer: dt, bubbles: true, cancelable: true }));
      const h = at(), r = h && h.getBoundingClientRect(), v = view.getBoundingClientRect(), shown = h && { words: h.textContent, border: getComputedStyle(h).borderStyle, fits: Math.abs(r.left - v.left) + Math.abs(r.width - v.width) < 1 };
      view.dispatchEvent(new DragEvent("dragleave", { dataTransfer: dt, bubbles: true, relatedTarget: document.body }));
      return { shown, gone: !at() };
    });
    ok(hint.shown && hint.shown.words === "Drop to Place · picture or text" && hint.shown.border === "dashed" && hint.shown.fits && hint.gone, "Dragged over the page, a dashed outline and its note; gone on leaving: " + JSON.stringify(hint));
    const dropped = await p.evaluate(async () => {
      const c = new OffscreenCanvas(60, 60), x = c.getContext("2d"); x.fillRect(0, 0, 60, 60);
      const dt = new DataTransfer(); dt.items.add(new File([await c.convertToBlob({ type: "image/png" })], "drop.png", { type: "image/png" }));
      document.getElementById("view").dispatchEvent(new DragEvent("dragover", { dataTransfer: dt, bubbles: true, cancelable: true }));
      document.getElementById("view").dispatchEvent(new DragEvent("drop", { dataTransfer: dt, bubbles: true, cancelable: true }));
      if (document.querySelector(".drophint")) return false;  // the hint goes with the drop
      for (let i = 0; i < 40 && !guest.ST.placing; i++) await new Promise(r => setTimeout(r, 50));
      return guest.ST.placing;
    });
    ok(dropped, "A picture dropped on the page is placed as Import's");
    // Less Contrast and Lighter once each, then Place: its black laid as PictureImport.toned(0, 1, -1) says (contrast first, 103).
    await tap(p, await byText(p, "[data-placing] button", "Less Contrast"));
    await tap(p, await byText(p, "[data-placing] button", "Lighter"));
    const fd = await p.evaluate(() => [...guest.E.T.placeFrame()]);
    await tap(p, await byText(p, "[data-placing] button", "Place"));
    await sleep(500);
    const laid = await p.evaluate(f => guest.S[Math.round(f[1] + f[3] / 2) * guest.W + Math.round(f[0] + f[2] / 2)], fd);
    ok(Math.abs(laid - 103) <= 9, "Less Contrast then Lighter is laid as shown: its black " + laid);
    await p.evaluate(() => guest.undo()); await sleep(500);

    // ---- Words dropped on the page: plain text, then a .txt file, each a text box where it lands (TextBoxes.placed) ----
    const dropWords = (u, v, file) => p.evaluate(async (u, v, file) => {
      const view = document.getElementById("view"), r = view.getBoundingClientRect(), cx = r.left + r.width * u, cy = r.top + r.height * v, dt = new DataTransfer();
      if (file) dt.items.add(new File([file], "words.txt", { type: "text/plain" })); else dt.setData("text/plain", "Hello there");
      const at = { dataTransfer: dt, bubbles: true, cancelable: true, clientX: cx, clientY: cy };
      view.dispatchEvent(new DragEvent("dragover", at));
      const hinted = document.querySelector(".drophint")?.textContent;
      const before = JSON.parse(guest.E.T.texts(guest.P.id)).length;
      view.dispatchEvent(new DragEvent("drop", at));
      for (let i = 0; i < 40 && JSON.parse(guest.E.T.texts(guest.P.id)).length === before; i++) await new Promise(q => setTimeout(q, 50));
      const boxes = JSON.parse(guest.E.T.texts(guest.P.id)), b = boxes[boxes.length - 1], V = guest.V, px = ((cx - r.left) * devicePixelRatio - V.x) / V.s, py = ((cy - r.top) * devicePixelRatio - V.y) / V.s;
      return { hinted, added: boxes.length - before, text: b && b.text, at: b && Math.abs(b.rect[0] - px) <= 4 && b.rect[1] <= py && py <= b.rect[3], placing: guest.ST.placing, rect: b && b.rect, px: Math.round(px), py: Math.round(py) };
    }, u, v, file);
    const said = await dropWords(.6, .7);
    ok(said.hinted === "Drop to Place · picture or text" && said.added === 1 && said.text === "Hello there" && said.at && !said.placing, "Plain text dropped on the page: a text box where it lands: " + JSON.stringify(said));
    const filed = await dropWords(.3, .85, "Line one\r\nLine two\n");
    ok(filed.added === 1 && filed.text === "Line one\nLine two" && filed.at, "A .txt file dropped: a text box of its lines where it lands: " + JSON.stringify(filed));

    // ---- The Tools page: no Cut Stamp of its own since 2026-11-03 (Stamp ▾ and Brush Lab have it) ----
    await hold(p, await centre(p, "#column > button", 2));
    await tap(p, await byText(p, ".pop .mrow", "Library"));
    ok(await p.evaluate(() => guest.shown === "tools") && !(await byText(p, "#screen .lb", "Make Stamp")), "The Tools page on a Stamp offers no Cut Stamp");
    await tap(p, await byText(p, "#screen .bar .lb", "Back"));

    // ---- Brush Lab's Cut Stamp, on the scratch pad ----
    await hold(p, await centre(p, "#column > button", 2));
    await tap(p, await byText(p, ".pop .mrow", "Edit Brush"));
    const labCut = await byText(p, "#screen .actions .lb", "Make Stamp");
    ok(!!labCut, "Brush Lab's bar offers Cut Stamp for a Stamp");
    const labKey = await p.evaluate(() => guest.lp.key);
    await tap(p, labCut);
    ok(await p.evaluate(() => guest.ST.labWaits && document.querySelector("#screen .actions .lb[aria-pressed=true]").textContent === "Make Stamp"), "Tapped, it waits, on");
    const pad = await p.evaluate(() => { const r = document.querySelector("#screen canvas.scratch").getBoundingClientRect(); return [r.x, r.y, r.width, r.height]; });
    const padLoop = [[.2, .2], [.6, .2], [.6, .7], [.2, .7], [.2, .21]].map(([u, v]) => [pad[0] + pad[2] * u, pad[1] + pad[3] * v]);
    await stroke(p, padLoop.flatMap((q, i) => i ? Array.from({ length: 5 }, (_, k) => [padLoop[i - 1][0] + (q[0] - padLoop[i - 1][0]) * (k + 1) / 5, padLoop[i - 1][1] + (q[1] - padLoop[i - 1][1]) * (k + 1) / 5]) : [q]));
    ok(await p.evaluate(() => !guest.ST.labWaits && document.querySelector("#screen .actions").textContent.includes("Nothing to cut")), "An empty pad's lasso: its button says \"Nothing to cut\"");
    await sleep(1400);
    ok(!!(await byText(p, "#screen .actions .lb", "Make Stamp")), "For a moment: then Cut Stamp again");
    // A mark on the pad, then the lasso round it.
    await stroke(p, Array.from({ length: 12 }, (_, i) => [pad[0] + pad[2] * (.3 + .02 * i), pad[1] + pad[3] * .45]));
    await tap(p, await byText(p, "#screen .actions .lb", "Make Stamp"));
    await stroke(p, padLoop.flatMap((q, i) => i ? Array.from({ length: 5 }, (_, k) => [padLoop[i - 1][0] + (q[0] - padLoop[i - 1][0]) * (k + 1) / 5, padLoop[i - 1][1] + (q[1] - padLoop[i - 1][1]) * (k + 1) / 5]) : [q]));
    ok((await note(p)) === "Stamp cut" && (await p.evaluate(() => guest.lp.key)) !== labKey, "A lasso round the pad's mark: \"Stamp cut\", the lab's Stamp prints it");
    await shot(p, "lab");

    ok(errs.length === 0, "No errors: " + errs.join(" | "));
  } catch (e) { fail++; console.log("FAIL " + (e.stack || e)); }
  finally { await b.close(); relay.kill(); fs.rmSync(tmp, { recursive: true, force: true }); }
  console.log(fail ? `${fail} of ${n} failed` : `all ${n} passed`);
  process.exit(fail ? 1 : 0);
})();
