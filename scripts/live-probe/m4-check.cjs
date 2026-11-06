// The rest of a guest's drawing screen (live.html with m4.js, the browser app's M4) without the Manta: a relay of its
// own, headless Chrome (puppeteer-core from ~/node_modules), an iPad in portrait drawing and a phone watching.
// Each feature opens, does its thing and, where it changes the page, Undo takes it back: the menu (Finger Action,
// Left-Handed), the smudge tool's row and its chooser, Shapes (and Filled), the Lasso (move, Delete, Cut and Paste), the
// Dropper, Text and Make Ink, Mirrors (Kaleidoscope, its guides), Zen Mode, Hold to Snap, Springy brush's drawing, and a
// host's tools arriving mid-session (the guest's own slots and tool in use stay). With --shots, docs/archive/pictures/live/m4-*.png.
//   bash web/engine/build.sh && node scripts/live-probe/m4-check.cjs [port] [--shots]
"use strict";
const { spawn } = require("child_process"), path = require("path"), os = require("os"), http = require("http"), zlib = require("zlib");
const puppeteer = require(path.join(os.homedir(), "node_modules", "puppeteer-core"));
const CHROME = process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const REPO = path.join(__dirname, "..", ".."), args = process.argv.slice(2), PORT = +(args.find(a => /^\d+$/.test(a)) || 8770), SHOTS = args.includes("--shots");
const IPAD = [820, 1180, 2], PHONE = [390, 844, 3];
const sleep = ms => new Promise(r => setTimeout(r, ms));
let n = 0, fail = 0;
const ok = (c, what) => { n++; if (!c) fail++; console.log((c ? "ok   " : "FAIL ") + what); };
const shot = (p, name) => SHOTS ? p.screenshot({ path: path.join(REPO, "docs", "archive", "pictures", "live", "m4-" + name + ".png") }) : null;
const post = body => new Promise((res, rej) => { const q = http.request(`http://127.0.0.1:${PORT}/send?from=${require("./live-crypt.cjs").hostId("")}`, { method: "POST" }, a => { a.resume(); a.on("end", res); }); q.on("error", rej); q.end(require("./live-crypt.cjs").seal("", body)); });  // sealed as the Manta's, no key in the link

(async () => {
  const relay = spawn("python3", [path.join(__dirname, "relay.py"), String(PORT)], { stdio: ["ignore", "ignore", "inherit"] });
  await sleep(700);
  const b = await puppeteer.launch({ executablePath: CHROME, headless: "new", args: ["--disable-gpu", "--disable-background-timer-throttling", "--disable-renderer-backgrounding", "--disable-backgrounding-occluded-windows"], protocolTimeout: 120000 });
  const errs = [], pngs = [];
  async function guest([w, h, scale], touch) {
    const p = await b.newPage();
    p.on("console", m => { if ((m.type() === "error" || m.type() === "warning") && !/^Failed to load resource/.test(m.text())) errs.push(m.text()); });
    p.on("response", r => {
      if (/\.png$/.test(r.url())) pngs.push(r.status() + " " + r.url());
      if (r.status() >= 400 && !/favicon\.ico$|tip_[a-z0-9_]+\.xml$/.test(r.url())) errs.push(r.status() + " " + r.url());
    });
    p.on("pageerror", e => errs.push("pageerror: " + e.message));
    await p.setViewport({ width: w, height: h, deviceScaleFactor: scale, hasTouch: !!touch, isMobile: !!touch });
    await p.goto(`http://127.0.0.1:${PORT}/live`, { waitUntil: "load" });
    await p.waitForFunction(() => window.guest && window.guest.ready, { timeout: 30000 });
    return p;
  }
  // Page points at fractions of the view; the view's screen point for a page point.
  const viewBox = p => p.evaluate(() => { const r = document.getElementById("view").getBoundingClientRect(); return [r.x, r.y, r.width, r.height]; });
  const toScreen = (p, x, y) => p.evaluate((x, y) => { const r = document.getElementById("view").getBoundingClientRect(), V = guest.V, d = devicePixelRatio; return [r.x + (x * V.s + V.x) / d, r.y + (y * V.s + V.y) / d]; }, x, y);
  const toPage = (p, sx, sy) => p.evaluate((sx, sy) => { const r = document.getElementById("view").getBoundingClientRect(), V = guest.V, d = devicePixelRatio; return [((sx - r.x) * d - V.x) / V.s, ((sy - r.y) * d - V.y) / V.s]; }, sx, sy);
  // A pen (the mouse) along screen points [x, y], held still {@code hold} ms at the end before the lift.
  async function stroke(p, pts, hold = 0) {
    await p.mouse.move(pts[0][0], pts[0][1]); await p.mouse.down();
    for (const q of pts.slice(1)) { await p.mouse.move(q[0], q[1]); await sleep(8); }
    if (hold) await sleep(hold);
    await p.mouse.up(); await sleep(250);
  }
  const across = async (p, u0, v0, u1, v1, k = 20) => { const [x, y, w, h] = await viewBox(p); return Array.from({ length: k + 1 }, (_, i) => [x + w * (u0 + (u1 - u0) * i / k), y + h * (v0 + (v1 - v0) * i / k)]); };
  const tap = async (p, xy) => { await p.mouse.click(xy[0], xy[1]); await sleep(150); };
  const hold = async (p, xy, ms = 700) => { await p.mouse.move(xy[0], xy[1]); await p.mouse.down(); await sleep(ms); await p.mouse.up(); await sleep(200); };
  const centre = (p, sel) => p.evaluate(s => { const e = document.querySelector(s); if (!e) return null; const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }, sel);
  const byText = (p, sel, text) => p.evaluate((s, t) => { const e = [...document.querySelectorAll(s)].find(e => e.textContent.trim() === t || e.getAttribute("aria-label") === t); if (!e) return null; const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }, sel, text);
  const popText = p => p.evaluate(() => { const e = document.querySelector(".pop"); return e ? e.textContent : null; });
  // Dark pixels of the page (what it shows, S) over a page rect, all of it by default.
  const dark = (p, r) => p.evaluate(r => { const S = guest.S, W = guest.W, [x0, y0, x1, y1] = r || [0, 0, guest.W, guest.H]; let k = 0; for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) if (S[y * W + x] < 200) k++; return k; }, r);
  const undo = async p => { await p.evaluate(() => guest.undo()); await sleep(400); };
  const clearAll = async p => { for (let i = 0; i < 20 && (await p.evaluate(() => guest.own.length)); i++) await undo(p); };
  const m4 = (p, f) => p.evaluate(f);
  try {
    const p = await guest(IPAD), watcher = await guest(PHONE, true);
    await p.bringToFront();
    // ---- The column: Menu, the smudge tool's row, the Dropper, the Lasso, Play, Zen Mode ----
    const parts = await p.evaluate(() => [...document.querySelectorAll("[data-m4]")].map(e => e.dataset.m4).join(","));
    ok(parts === "menu,dropper,lasso,play,zen", "The column's buttons (Shapes in Play): " + parts);
    const smudgeRow = await p.evaluate(() => [...document.querySelectorAll("#column > button:not([data-m4]):not([data-x]):not(#layers)")].map(b => b.getAttribute("aria-label"))), trays = await p.evaluate(() => guest.L.trays);
    ok(smudgeRow.length === trays + 3 && smudgeRow[smudgeRow.length - 3] === "Fill in" && smudgeRow[smudgeRow.length - 2] === "Blend" && smudgeRow[smudgeRow.length - 1] === "Eraser", "Fill in's row, the smudge tool's, the eraser's: " + smudgeRow.slice(-3).join(", "));
    await shot(p, "column");

    // ---- Undo and Redo on the page bar: a guest's own changes alone, as its finger taps (live Undo, LiveOwn) ----
    {
      const undoState = () => p.evaluate(() => [...document.querySelectorAll("#undos button")].map(b => b.getAttribute("aria-label") + (b.disabled ? " off" : " on")).join(", "));
      ok(await undoState() === "Undo off, Redo off", "Undo and Redo greyed before anything is drawn: " + await undoState());
      await stroke(p, await across(p, .3, .15, .7, .15)); await sleep(600);
      const mine = await dark(p), wm = await dark(watcher), afterStroke = await undoState();
      await tap(p, await centre(p, "[data-undo=undo]")); await sleep(600);
      const gone = await dark(p), wGone = await dark(watcher), afterUndo = await undoState();
      await tap(p, await centre(p, "[data-undo=redo]")); await sleep(600);
      const back = await dark(p), wBack = await dark(watcher), afterRedo = await undoState();
      ok(mine > 200 && wm > 200 && afterStroke === "Undo on, Redo off" && gone < mine / 20 && wGone < wm / 20 && afterUndo === "Undo off, Redo on"
        && Math.abs(back - mine) < mine / 20 && Math.abs(wBack - wm) < wm / 20 && afterRedo === "Undo on, Redo off",
        `A guest's Undo click takes its stroke back on both (${mine} → ${gone}, watcher ${wm} → ${wGone}), Redo puts it back (${back}, ${wBack}); greyed as they have nothing to do`);
      await p.keyboard.down("Control"); await p.keyboard.press("z"); await p.keyboard.up("Control"); await sleep(600);
      ok(await dark(p) < mine / 20 && await undoState() === "Undo off, Redo on", "Ctrl+Z is the same Undo");
      await p.keyboard.down("Control"); await p.keyboard.press("y"); await p.keyboard.up("Control"); await sleep(600);
      ok(Math.abs(await dark(p) - mine) < mine / 20, "Ctrl+Y the same Redo");
    await clearAll(p);
    }

    // ---- Menu: Finger Action and Left-Handed ----
    await tap(p, await centre(p, "[data-m4=menu]"));
    const menu = await popText(p);
    ok(/Finger Action: /.test(menu) && /Left-Handed/.test(menu) && !/Notebook Settings/.test(menu), "Menu: Finger Action and Left-Handed, no notebook rows: " + menu);
    await shot(p, "menu");
    await tap(p, await byText(p, ".pop .mrow", "Left-Handed"));
    const lh = await p.evaluate(() => { const c = document.getElementById("column").getBoundingClientRect(), v = document.getElementById("view").getBoundingClientRect(); return { right: document.body.classList.contains("right"), col: c.left, view: v.left, w: innerWidth }; });
    ok(lh.right && lh.col > lh.w / 2 && lh.view === 0, "Left-Handed: the column on the right, the page at the left " + JSON.stringify(lh));
    await shot(p, "left-handed");
    // The grey strip still reads its own side, mirrored: a tap low on the strip picks a dark grey.
    await tap(p, await centre(p, "[data-m4=menu]"));
    ok(/Right-Handed/.test(await popText(p)), "The row says Right-Handed now");
    await tap(p, await byText(p, ".pop .mrow", "Right-Handed"));
    ok(await p.evaluate(() => !document.body.classList.contains("right") && document.getElementById("column").getBoundingClientRect().left === 0), "Right-Handed: the column back on the left");
    await tap(p, await centre(p, "[data-m4=menu]"));
    await tap(p, await p.evaluate(() => { const e = [...document.querySelectorAll(".pop .mrow")].find(e => /Finger Action/.test(e.textContent)); const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }));
    const fingers = await popText(p);
    ok(/Nothing/.test(fingers) && /Eraser/.test(fingers) && /Smudge/.test(fingers), "Finger Action's choices: Nothing, Eraser, Smudge and the slots");
    await tap(p, await byText(p, ".pop .mrow", "Eraser"));
    ok(await p.evaluate(() => guest.E.T.fingerTool(guest.sel) === guest.L.c.ERASER), "Finger Action: Eraser, one finger erases");

    // ---- Shapes and Filled: Play's row ----
    const shapesMenu = async () => { await tap(p, await centre(p, "[data-m4=play]")); await tap(p, await byText(p, ".pop .mrow", "Shapes")); };
    await shapesMenu();
    ok(/Line.*Rectangle.*Square.*Oval.*Circle.*Filled/.test(await popText(p)), "Shapes' menu: Line, Rectangle, Square, Oval, Circle, Filled");
    await shot(p, "shapes");
    await tap(p, await byText(p, ".pop .mrow", "Rectangle"));
    ok(await m4(p, () => guest.M4.mode === "shapes" && guest.sel < guest.L.c.COUNT), "Rectangle: Shapes taken up on the slot in use");
    const [rx0, ry0] = await toPage(p, ...(await across(p, .2, .15, .2, .15, 1))[0]), [rx1, ry1] = await toPage(p, ...(await across(p, .5, .3, .5, .3, 1))[0]);
    await stroke(p, await across(p, .2, .15, .5, .3));
    const rect = [Math.floor(rx0) - 20, Math.floor(ry0) - 20, Math.ceil(rx1) + 20, Math.ceil(ry1) + 20], inner = [Math.ceil(rx0) + 20, Math.ceil(ry0) + 20, Math.floor(rx1) - 20, Math.floor(ry1) - 20];
    const outline = await dark(p, rect), inside = await dark(p, inner);
    ok(outline > 400 && inside === 0, `A rectangle drawn: its outline (${outline} px), nothing inside`);
    await undo(p);
    ok(await dark(p, rect) === 0, "Undo takes the rectangle back");
    await shapesMenu(); await tap(p, await byText(p, ".pop .mrow", "Filled"));
    await shapesMenu(); await tap(p, await byText(p, ".pop .mrow", "Oval"));
    await stroke(p, await across(p, .2, .15, .5, .3));
    const filled = await dark(p, inner);
    ok(filled > (inner[2] - inner[0]) * (inner[3] - inner[1]) * .5, `Filled oval: its inside filled (${filled} px)`);
    await sleep(800);
    ok(await dark(watcher, inner) > filled * .9, "The phone sees the filled oval");
    await shot(p, "filled-oval");
    await undo(p);
    ok(await dark(p, rect) === 0, "Undo takes the oval back");
    await shapesMenu(); await tap(p, await byText(p, ".pop .mrow", "Filled"));
    // A slot chosen puts Shapes down.
    await p.evaluate(() => guest.choose(0));
    ok(await m4(p, () => guest.M4.mode === null), "A slot chosen puts Shapes down");

    // ---- The Lasso: a loop lifts a piece; dragged, put down; Delete; Cut and Paste ----
    await stroke(p, await across(p, .3, .5, .5, .52));
    const mark = [0, 0, 0, 0];
    { const a = await toPage(p, ...(await across(p, .25, .45, .25, .45, 1))[0]), c = await toPage(p, ...(await across(p, .55, .57, .55, .57, 1))[0]); mark.splice(0, 4, Math.floor(a[0]), Math.floor(a[1]), Math.ceil(c[0]), Math.ceil(c[1])); }
    const marked = await dark(p, mark);
    await tap(p, await centre(p, "[data-m4=lasso]"));
    ok(await m4(p, () => guest.M4.mode === "lasso"), "The Lasso taken up");
    const loop = [[.25, .45], [.55, .45], [.55, .57], [.25, .57], [.25, .46]];
    const loopPts = async () => { const [x, y, w, h] = await viewBox(p), pts = []; for (let i = 0; i < loop.length - 1; i++) for (let k = 0; k < 10; k++) { const u = k / 10; pts.push([x + w * (loop[i][0] + (loop[i + 1][0] - loop[i][0]) * u), y + h * (loop[i][1] + (loop[i + 1][1] - loop[i][1]) * u)]); } return pts; };
    await stroke(p, await loopPts());
    const bar = await p.evaluate(() => { const e = document.querySelector("[data-lasso-bar]"); return e ? e.textContent : null; });
    ok(await m4(p, () => !!guest.M4.piece) && /Cut.*Duplicate.*Delete/.test(bar || ""), "A loop lifts the mark: it floats, the bar over it says " + bar);
    await shot(p, "lasso");
    // Drag it from its middle down the page, then put it down with a tap elsewhere.
    await stroke(p, await across(p, .4, .51, .4, .71, 10));
    await tap(p, (await across(p, .9, .9, .9, .9, 1))[0]);
    const movedTo = [mark[0], mark[1] + (mark[3] - mark[1]) + 10, mark[2], mark[3] + (mark[3] - mark[1]) * 2 + 60];
    const left = await dark(p, mark);
    ok(await m4(p, () => !guest.M4.piece) && left < marked * .1, `Moved and put down: the old place empty (${marked} → ${left} px)`);
    await sleep(800);
    ok(await dark(watcher, mark) < marked * .1, "The phone sees it moved");
    await undo(p);
    ok(await dark(p, mark) > marked * .9, "Undo puts it back where it was");
    await stroke(p, await loopPts());
    await tap(p, await byText(p, "[data-lasso-bar] .mrow", "Delete"));
    ok(await dark(p, mark) < marked * .1 && await m4(p, () => !guest.M4.piece), "Delete: the mark gone");
    await undo(p);
    ok(await dark(p, mark) > marked * .9, "Undo brings it back");
    await stroke(p, await loopPts());
    await tap(p, await byText(p, "[data-lasso-bar] .mrow", "Cut"));
    ok(await dark(p, mark) < marked * .1, "Cut: the mark gone");
    await tap(p, await centre(p, "[data-m4=lasso]"));
    ok(/Paste/.test(await popText(p) || ""), "The Lasso's tap with a piece kept: Paste");
    await tap(p, await byText(p, ".pop .mrow", "Paste"));
    ok(await m4(p, () => !!guest.M4.piece), "Paste: the piece floats where it was cut");
    await tap(p, (await across(p, .9, .9, .9, .9, 1))[0]);
    ok(await dark(p, mark) > marked * .9, "Put down: the mark back");
    // Make Stamp (2026-11-02): the piece's ink a Stamp's nib, that slot in use, the Lasso put away, the mark still there.
    // Slot 8 in use, so the later checks' slot 1 keeps its brush.
    await p.evaluate(() => guest.choose(7));
    await tap(p, await centre(p, "[data-m4=lasso]"));
    await stroke(p, await loopPts());
    ok(/Cut.*Duplicate.*Make Stamp.*Delete/.test(await p.evaluate(() => document.querySelector("[data-lasso-bar]")?.textContent || "")), "The bar: Cut, Duplicate, Make Stamp, Delete");
    await tap(p, await byText(p, "[data-lasso-bar] .mrow", "Make Stamp"));
    ok(await m4(p, () => !guest.M4.piece && guest.M4.mode === null) && await p.evaluate(() => guest.sel === 7 && guest.tools.slots[7].name === "Stamp") && await dark(p, mark) > marked * .9,
        "Make Stamp: the slot in use a Stamp, the Lasso put away, the mark where it was");
    await p.evaluate(() => guest.choose(0));
    await clearAll(p);
    ok(await dark(p) === 0, "Undo, again and again: the page blank");

    // ---- The Dropper ----
    await stroke(p, await across(p, .3, .5, .6, .5));
    const before = await p.evaluate(() => guest.dark[guest.sel]);
    await p.evaluate(() => { guest.dark[guest.sel] = .1; });
    await tap(p, await centre(p, "[data-m4=dropper]"));
    ok(await m4(p, () => guest.M4.mode === "dropper"), "The Dropper taken up");
    const onMark = await p.evaluate(() => { const S = guest.S, W = guest.W; let at = 0; for (let i = 0; i < S.length; i++) if (S[i] < S[at]) at = i; return [at % W, Math.floor(at / W), S[at]]; });
    await tap(p, await toScreen(p, onMark[0] + .5, onMark[1] + .5));
    const picked = await p.evaluate(() => guest.dark[guest.sel]);
    ok(picked > .3 && await m4(p, () => guest.M4.mode === null), `The Dropper picks the mark's grey (tone ${onMark[2]}: ${picked.toFixed(2)}, the slot's ${before.toFixed(2)}) and is put down`);
    await p.evaluate(b => { guest.dark[guest.sel] = b; }, before);
    await clearAll(p);

    // ---- Text and Make Ink ----
    await tap(p, await centre(p, "[data-m4=play]"));
    const play = await popText(p);
    ok(/Text/.test(play) && /Shapes/.test(play) && /Mirrors/.test(play) && !/Magnetic Poetry/.test(play) && !/Spirograph/.test(play), "Play: Text, Shapes and Mirrors (no Magnetic Poetry or Spirograph for a guest): " + play);
    await shot(p, "play");
    await tap(p, await byText(p, ".pop .mrow", "Text"));
    ok(await m4(p, () => guest.M4.textOn), "Text taken up");
    await tap(p, (await across(p, .3, .3, .3, .3, 1))[0]);
    ok(await p.evaluate(() => !!document.querySelector("textarea")), "A tap on paper opens a box to type in");
    await p.keyboard.type("Hello there");
    await tap(p, (await across(p, .8, .8, .8, .8, 1))[0]);
    const boxes = await p.evaluate(() => JSON.parse(guest.E.T.texts(guest.P.id)));
    ok(boxes.length === 1 && boxes[0].text === "Hello there" && await dark(p) === 0, "Typed: the page holds one box, \"Hello there\", over the drawing, not in it");
    await shot(p, "text");
    const boxAt = await toScreen(p, (boxes[0].rect[0] + boxes[0].rect[2]) / 2 - 300, (boxes[0].rect[1] + boxes[0].rect[3]) / 2);
    await hold(p, boxAt);
    const tm = await popText(p);
    ok(/Small.*Medium.*Large.*Huge.*Bold.*Left.*Centre.*Right.*Make Ink.*Delete/.test(tm || ""), "A box's long press: its menu");
    await tap(p, await byText(p, ".pop .mrow", "Make Ink"));
    const inked = await dark(p);
    ok(inked > 300 && (await p.evaluate(() => JSON.parse(guest.E.T.texts(guest.P.id)).length)) === 0, `Make Ink: the letters laid as ink (${inked} px), the box gone`);
    await sleep(800);
    ok(await dark(watcher) > inked * .9, "The phone sees the inked words");
    await shot(p, "make-ink");
    await undo(p);
    ok(await dark(p) === 0, "Undo takes the ink back");
    await tap(p, await centre(p, "[data-m4=play]")); await tap(p, await byText(p, ".pop .mrow", "Text"));
    ok(await m4(p, () => !guest.M4.textOn), "Text put down");

    // ---- Mirrors: Kaleidoscope and its guides ----
    await tap(p, await centre(p, "[data-m4=play]")); await tap(p, await byText(p, ".pop .mrow", "Mirrors"));
    const mm = await popText(p);
    ok(/Copy Strokes/.test(mm) && /Kaleidoscope/.test(mm) && !/Real Mirror/.test(mm), "Mirrors' list (no Real Mirror for a guest): " + mm);
    await tap(p, await byText(p, ".pop .mrow", "Kaleidoscope"));
    const guides = await p.evaluate(() => { const c = document.getElementById("over"), d = c.getContext("2d").getImageData(0, 0, c.width, c.height).data; let k = 0; for (let i = 3; i < d.length; i += 4) if (d[i]) k++; return k; });
    ok(guides > 1000, `Kaleidoscope: its guides over the page (${guides} px)`);
    await stroke(p, await across(p, .15, .2, .3, .22));
    const W2 = await p.evaluate(() => [guest.W, guest.H]);
    const quads = await Promise.all([[0, 0], [1, 0], [0, 1], [1, 1]].map(([i, j]) => dark(p, [i * W2[0] / 2, j * W2[1] / 2, (i + 1) * W2[0] / 2, (j + 1) * W2[1] / 2])));
    ok(quads.every(q => q > 50), "A stroke laid six times round the centre: " + quads.join(", "));
    await shot(p, "kaleidoscope");
    await undo(p);
    ok(await dark(p) === 0, "Undo takes all the copies back");
    await tap(p, await centre(p, "[data-m4=play]")); await tap(p, await byText(p, ".pop .mrow", "Mirrors"));
    await tap(p, await byText(p, ".pop .mrow", "Remove Mirrors"));
    ok(await p.evaluate(() => !JSON.parse(guest.E.T.guides(guest.P.id)).acts), "Remove Mirrors");

    // ---- The smudge tool: its chooser, a smudge, Undo ----
    await stroke(p, await across(p, .3, .5, .6, .5));
    await p.evaluate(() => { window.before = guest.S.slice(); });
    const differ = () => p.evaluate(() => { let k = 0; for (let i = 0; i < before.length; i++) if (guest.S[i] !== before[i]) k++; return k; });
    const smudge = await byText(p, "#column > button", "Blend");
    await hold(p, smudge);
    const sm = await p.evaluate(() => { const e = document.querySelector(".pop[data-smudge]"); return e ? [...e.querySelectorAll(".cell .name:not(.small)")].map(x => x.textContent) : null; });
    ok(sm && sm.includes("Blend") && sm.includes("Water") && await p.evaluate(() => guest.sel === guest.L.c.SMUDGE), "The smudge tool's long press: it is in use, its chooser offers " + (sm || []).join(", "));
    await shot(p, "smudge");
    await tap(p, await byText(p, ".pop .cell .name", "Blend"));
    await stroke(p, await across(p, .45, .4, .45, .6, 20));
    const smudged = await differ();
    ok(smudged > 50, `Blend across the mark moves its paint (${smudged} px changed)`);
    await undo(p);
    const back = await differ();
    ok(back < smudged / 10, `Undo takes the smudge back (${back} px differ)`);
    await p.evaluate(() => guest.choose(0));
    await clearAll(p);

    // ---- Hold to Snap: a rough loop held still becomes a clean shape, one Undo step ----
    { const [x, y, w, h] = await viewBox(p), pts = [];
      for (let k = 0; k <= 40; k++) { const a = 2 * Math.PI * k / 40, r = 1 + .06 * Math.sin(5 * a); pts.push([x + w * (.5 + .15 * r * Math.cos(a)), y + h * (.5 + .1 * r * Math.sin(a))]); }
      await stroke(p, pts, 900); }
    ok(await m4(p, () => guest.M4.snaps) === 1 && await dark(p) > 200, "Held still at its end, the rough loop snaps to a clean one");
    await shot(p, "snap");
    await undo(p);
    ok(await dark(p) === 0, "One Undo takes the snapped shape back");

    // ---- Zen Mode: its button, in and out ----
    await tap(p, await centre(p, "[data-m4=zen]"));
    const zen = await p.evaluate(() => ({ col: getComputedStyle(document.getElementById("column")).display, v: document.getElementById("view").getBoundingClientRect().width, w: innerWidth }));
    ok(zen.col === "none" && zen.v === zen.w, "Zen Mode: the column gone, the page the whole screen");
    await shot(p, "zen");
    await tap(p, await centre(p, "[data-m4=zen]"));
    ok(await p.evaluate(() => getComputedStyle(document.getElementById("column")).display !== "none"), "Zen Mode's way out: the bars back (no three-finger double tap since 2026-11-02)");

    // ---- A host's tools mid-session: a slot the guest hasn't changed takes the host's; the guest's tool in use stays ----
    const W = await p.evaluate(() => [guest.W, guest.H]);
    const raw = Buffer.from(await p.evaluate((w, h) => [...guest.E.T.hostTools(w, h, false)].map(v => v & 255), ...W));
    const hostWith = sel => zlib.deflateSync(Buffer.concat([raw.subarray(0, 8), Buffer.from(raw.subarray(8).toString("utf8").replace("selected 0", "selected " + sel), "utf8")]));
    await post(hostWith(7)); await sleep(1000);
    ok(await p.evaluate(() => guest.sel) === 7, "The host's tools arrive: its tool in use with them (slot 8)");
    await p.evaluate(() => guest.choose(4));
    await post(hostWith(2)); await sleep(1000);
    ok(await p.evaluate(() => guest.sel) === 4, "Mid-session, the host's tools again: the guest's own tool in use stays (slot 5)");

    // ---- Springy brush's drawing: a picture, served ----
    await p.evaluate(() => fetch("drawable/tip_springy_brush.png").then(r => r.ok ? 1 : 0)).then(v => ok(v === 1, "Springy brush's drawing is served (res/drawable-nodpi)"));
    ok(errs.length === 0, "No errors: " + errs.join(" | "));
  } catch (e) { ok(false, e.stack); }
  finally { await b.close(); relay.kill(); }
  console.log(fail ? `${fail} of ${n} failed` : `all ${n} passed`);
  process.exit(fail ? 1 : 0);
})();
