// The guest's long presses, Tools page and Brush Lab (live.html, the browser app's M2) without the Manta: a relay of its
// own on another port, headless Chrome (puppeteer-core from ~/node_modules) as an iPad in portrait and a phone.
// Checks: a long press on a slot opens the tool picker beside it and a pick changes the slot (kept in this browser);
// a tap stays a tap; the eraser's long press picks what it takes; the Tools page opens from the picker's Full Library and
// Load Kit fills the bar; Brush Lab opens from the picker, a dial changes how the scratch pad draws, Back puts the brush
// in the slot; a phone's picker has no Brush Lab or Library. With --shots, docs/archive/pictures/live/m2-*.png.
//   bash web/engine/build.sh && node scripts/live-probe/m2-check.cjs [port] [--shots]
"use strict";
const { spawn } = require("child_process"), path = require("path"), os = require("os");
const puppeteer = require(path.join(os.homedir(), "node_modules", "puppeteer-core"));
const CHROME = process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const REPO = path.join(__dirname, "..", ".."), args = process.argv.slice(2), PORT = +(args.find(a => /^\d+$/.test(a)) || 8768), SHOTS = args.includes("--shots");
const IPAD = [820, 1180, 2], IPAD_WIDE = [1180, 820, 2], DESKTOP = [1440, 900, 1], PHONE = [390, 844, 3];
const sleep = ms => new Promise(r => setTimeout(r, ms));
let n = 0, fail = 0;
const ok = (c, what) => { n++; if (!c) fail++; console.log((c ? "ok   " : "FAIL ") + what); };
const shot = (p, name) => SHOTS ? p.screenshot({ path: path.join(REPO, "docs", "archive", "pictures", "live", "m2-" + name + ".png") }) : null;

(async () => {
  const relay = spawn("python3", [path.join(__dirname, "relay.py"), String(PORT)], { stdio: ["ignore", "ignore", "inherit"] });
  await sleep(700);
  const b = await puppeteer.launch({ executablePath: CHROME, headless: "new", args: ["--disable-gpu"], protocolTimeout: 120000 });
  const errs = [];
  async function guest([w, h, scale], touch) {
    const p = await b.newPage();
    p.on("console", m => { if ((m.type() === "error" || m.type() === "warning") && !/^Failed to load resource/.test(m.text())) errs.push(m.text()); });
    // Springy brush's drawing is a PNG (res/drawable-nodpi), not served: its picker cell shows its stroke, as on M1's bar.
    p.on("response", r => { if (r.status() >= 400 && !/favicon\.ico$|tip_[a-z0-9_]+\.xml$/.test(r.url())) errs.push(r.status() + " " + r.url()); });
    p.on("pageerror", e => errs.push("pageerror: " + e.message));
    await p.setViewport({ width: w, height: h, deviceScaleFactor: scale, hasTouch: !!touch, isMobile: !!touch });
    await p.goto(`http://127.0.0.1:${PORT}/live`, { waitUntil: "load" });
    await p.waitForFunction(() => window.guest && window.guest.ready, { timeout: 30000 });
    return p;
  }
  const centre = async (p, sel, i = 0) => p.evaluate((s, i) => { const r = document.querySelectorAll(s)[i].getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }, sel, i);
  const tap = async (p, xy) => { await p.mouse.click(xy[0], xy[1]); await sleep(120); };
  const hold = async (p, xy, ms = 650) => { await p.mouse.move(xy[0], xy[1]); await p.mouse.down(); await sleep(ms); await p.mouse.up(); await sleep(150); };
  // Scrolled into view first: the picker four wide (2026-11-06) scrolls on a short screen (the iPad turned).
  const byText = async (p, sel, text) => p.evaluate((s, t) => { const e = [...document.querySelectorAll(s)].find(e => e.textContent.trim() === t || e.getAttribute("aria-label") === t); if (!e) return null; e.scrollIntoView({ block: "nearest" }); const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }, sel, text);
  // A stroke across the scratch pad; the greys it laid.
  async function scratch(p) {
    const r = await p.evaluate(() => { const r = document.querySelector("#screen canvas.scratch").getBoundingClientRect(); return [r.left, r.top, r.width, r.height]; });
    await p.mouse.move(r[0] + r[2] * .2, r[1] + r[3] * .5); await p.mouse.down();
    for (let k = 1; k <= 20; k++) await p.mouse.move(r[0] + r[2] * (.2 + .6 * k / 20), r[1] + r[3] * (.5 + .15 * Math.sin(k / 3)));
    await p.mouse.up(); await sleep(300);
    return p.evaluate(() => { const P = guest.pad.P, t = P.tones(0, 0, guest.pad.pw, guest.pad.ph); let ink = 0, sum = 0; for (const g of t) if (g < 250) { ink++; sum += 255 - g; } return [ink, sum]; });
  }
  // The lab's Tip module's slider, dragged to its far end; the slider's [left, middle, width, module's label].
  async function slideTip(p) {
    const sliders = await p.evaluate(() => [...document.querySelectorAll("#screen .module")].map(m => { const s = m.querySelector(".slider"); if (!s) return null; const r = s.getBoundingClientRect(); return [r.left, r.top + r.height / 2, r.width, m.getAttribute("aria-label")]; }));
    const tip = sliders.find(s => s && /^Tip/.test(s[3])) || sliders.find(s => s);
    await p.mouse.move(tip[0] + tip[2] * .5, tip[1]); await p.mouse.down(); await p.mouse.move(tip[0] + tip[2] * .98, tip[1], { steps: 5 }); await p.mouse.up(); await sleep(300);
    return tip;
  }
  try {
    const p = await guest(IPAD);
    const rows = await p.evaluate(() => [...document.querySelectorAll("#column > button")].length);
    ok(rows >= 9, "The column's rows: " + rows);
    // A tap selects; it never opens the picker.
    await tap(p, await centre(p, "#column > button", 3));
    ok(await p.evaluate(() => guest.sel === 3 && !document.querySelector(".pop")), "A tap on a slot selects it, no picker");
    // A long press opens the picker beside the slot.
    await hold(p, await centre(p, "#column > button", 1));
    const pk = await p.evaluate(() => { const pop = document.querySelector(".pop"), row = document.querySelectorAll("#column > button")[1].getBoundingClientRect(); if (!pop) return null; const r = pop.getBoundingClientRect(); return { slot: pop.dataset.picker, cells: pop.querySelectorAll(".cell").length, left: r.left, right: row.right, words: pop.textContent }; });
    ok(pk && pk.slot === "1" && pk.cells >= 30 && Math.abs(pk.left - pk.right) < 2, "A long press opens the tool picker beside slot 2: " + JSON.stringify(pk && { slot: pk.slot, cells: pk.cells, left: pk.left, right: pk.right }));
    ok(pk && /Edit Brush/.test(pk.words) && /Full Library/.test(pk.words) && /Hold Opens Full Library/.test(pk.words) && /Brush Studio/.test(pk.words), "Its foot: Edit Brush (Brush Lab), Full Library, Hold Opens Full Library, Brush Studio (the browser keeps its link)");
    // Four wide (2026-11-06), kind by kind, Effects last.
    const grid = await p.evaluate(() => { const g = [...document.querySelectorAll(".pop .cells")]; return { widest: Math.max(...g.map(e => e.children.length)), cols: getComputedStyle(g[0]).gridTemplateColumns.split(" ").length, last: [...g[g.length - 1].querySelectorAll(".name")].map(e => e.textContent) }; });
    ok(grid.cols === 4 && grid.widest > 4 && grid.last.every(n => / Effect$/.test(n)), "The picker four wide, the Effect tools last: " + JSON.stringify(grid));
    await shot(p, "picker-ipad");
    const before = await p.evaluate(() => guest.tools.slots[1].name);
    await tap(p, await byText(p, ".pop .cell", "Wax crayon"));
    const after = await p.evaluate(() => ({ name: guest.tools.slots[1].name, sel: guest.sel, pop: !!document.querySelector(".pop"), kept: guest.stored().tool_slots || "" }));
    ok(after.name === "Wax crayon" && before !== after.name && after.sel === 1 && !after.pop, "A pick puts Wax crayon in slot 2 and takes it up: " + before + " → " + after.name);
    ok(/Wax crayon/.test(after.kept), "The guest's bar is kept in this browser (localStorage)");
    // Kept across a reload.
    await p.reload({ waitUntil: "load" }); await p.waitForFunction(() => window.guest && window.guest.ready, { timeout: 30000 });
    ok(await p.evaluate(() => guest.tools.slots[1].name) === "Wax crayon", "The guest's own slot is there again after a reload");
    // The eraser's long press: what it takes; a pick takes the eraser up.
    await hold(p, await byText(p, "#column > button", "Eraser"));
    ok(await p.evaluate(() => !!document.querySelector(".pop.across") && /Dry Media/.test(document.querySelector(".pop").textContent) && !/Clear page/.test(document.querySelector(".pop").textContent)), "The eraser's long press offers what it takes (no Clear page for a guest)");
    await tap(p, await byText(p, ".pop .mrow", "Pen"));
    ok(await p.evaluate(() => guest.eraseKinds !== 0 && guest.sel === guest.L.c.ERASER), "Pen picked: the eraser takes pen only and is in use");
    // The Tools page from the picker's Full Library; Load Kit fills the bar.
    await hold(p, await centre(p, "#column > button", 0));
    await tap(p, await byText(p, ".pop .mrow", "Full Library"));
    ok(await p.evaluate(() => guest.shown === "tools" && document.querySelectorAll("#screen .slots .tcard").length >= 8), "Full Library opens the Tools page on the slot");
    await shot(p, "tools-ipad-portrait");
    ok(await p.evaluate(() => /Toolbar/.test(document.querySelector("#screen .bar .title").textContent) && document.querySelector("#screen .bar .job").textContent === ""
        && !document.querySelector("#screen .bar [aria-label=Undo]") && [...document.querySelectorAll("#screen .chip")].map(c => c.textContent).join() === "More Brushes,Recent,Saved,Pens,Markers,Brushes,Washes,Paint,Dry Media,Print,Effects,Kits"
        && /Draw here to try /.test(document.querySelector("#screen .strip .head").textContent) && /Edit in Brush Lab/.test(document.querySelector("#screen .named").textContent)),
      "The Tools page: Toolbar (its job line empty, ToolsLayout.JOB), no Undo, one filter row beside More Brushes (no Odd Ones: no shown tool is one), the try strip, Edit in Brush Lab");
    // The slots in one row, the selected one an ink tile; the name row under them; the try strip at the foot, under the grid.
    ok(await p.evaluate(() => {
      const tiles = [...document.querySelectorAll("#screen .slots .tile")], tops = new Set(tiles.map(t => Math.round(t.getBoundingClientRect().top)));
      const on = document.querySelectorAll("#screen .slots .tile.on"), r = s => document.querySelector(s).getBoundingClientRect();
      return tiles.length === guest.L.trays && tops.size === 1 && on.length === 1 && getComputedStyle(on[0].querySelector(".face")).backgroundColor === "rgb(0, 0, 0)"
        && !document.querySelector("#screen .slots .under") && r("#screen .slots").bottom <= r("#screen .named").top && r("#screen .grid").bottom <= r("#screen .strip").top;
    }), "The slots one row, the selected one inverted; the name row under them, the try strip under the grid");
    // A chip filters by its own kind, the row leaving Odd Ones out (WebEngine sent the row's place, Effects for Kits, until 2026-11-06).
    for (const c of ["Effects", "Kits"]) {
      await tap(p, await byText(p, "#screen .chip", c));
      ok(await p.evaluate(c => document.querySelector("#screen .chip.on").textContent === c, c), "The " + c + " chip filters by " + c);
    }
    const kitCard = await centre(p, "#screen .grid .tcard", 0);
    await tap(p, kitCard);
    ok(await p.evaluate(() => !!document.querySelector(".sheet")), "A kit's card opens its sheet");
    await shot(p, "kit-ipad");
    const kit = await p.evaluate(() => [...document.querySelectorAll(".sheet .step .small")].map(e => e.textContent));
    await tap(p, await byText(p, ".sheet .lb", "Load Kit"));
    await tap(p, await byText(p, "#screen .bar .lb", "Back"));
    const bar = await p.evaluate(() => guest.tools.slots.map(s => s.name));
    const named = kit.filter(t => !/^Eraser/.test(t));
    ok(named.length > 0 && named.every((t, i) => bar[i] === t || bar[i].startsWith(t)), "Load Kit, then Back: the bar holds the kit: " + named.join(", ") + " | " + bar.slice(0, named.length).join(", "));
    ok(await p.evaluate(() => guest.shown === null && !document.querySelector("#screen:not([hidden])")), "Back closes the Tools page");
    // Brush Lab from the picker; a dial changes how the scratch pad draws; Back puts the brush in the slot.
    await hold(p, await centre(p, "#column > button", 0));
    await tap(p, await byText(p, ".pop .mrow", "Edit Brush"));
    ok(await p.evaluate(() => guest.shown === "lab" && document.querySelectorAll("#screen .module").length === 6), "Brush Lab opens on the slot: six modules (Control, Tip, Medium, Affects Page, Drying, After)");
    await sleep(500);
    await shot(p, "lab-ipad-portrait");
    const first = await scratch(p);
    await tap(p, await byText(p, "#screen .lb", "Clear"));
    const tip = await slideTip(p);
    const second = await scratch(p);
    ok(first[0] > 50 && (first[0] !== second[0] || first[1] !== second[1]), "A dial changed (" + tip[3] + "): the scratch pad draws differently: " + first + " → " + second);
    ok(await p.evaluate(() => guest.lp.canUndo && guest.lp.edited), "The slide is an Undo step; the brush says Edited");
    // The app's naming (BrushNames, through the engine): a Title Case name that fits a card.
    const suggested = await p.evaluate(() => guest.lp.suggestion);
    ok(/^[A-Z][a-z]+( ((Ma?c)?[A-Z][a-z]+|of|for|on|a|in|with|and)){1,3}$/.test(suggested || "") && suggested.length <= 32, "The edited brush is offered a new name: " + suggested);
    // A module tapped opens the part: slim modules, the open card, its choices beside the column.
    await tap(p, await centre(p, "#screen .module .choice", 2));
    ok(await p.evaluate(() => !!document.querySelector(".opencard") && !!document.querySelector(".pop.swaps") && document.querySelectorAll(".pop.swaps .tile").length > 1), "A module tapped: the part opens, its choices beside the column");
    await sleep(400);
    await shot(p, "lab-open-ipad");
    await tap(p, await centre(p, ".pop.swaps .tile:not(.picked)", 0));
    ok(await p.evaluate(() => !document.querySelector(".pop.swaps") && !!document.querySelector(".opencard .drow, .opencard .hand, .opencard div")), "A choice picked: the panel closes, the card stays");
    const labName = await p.evaluate(() => guest.lp.name);
    await tap(p, await byText(p, "#screen .bar .lb", "Back"));
    ok(await p.evaluate(n => guest.shown === null && guest.tools.slots[0].name === n, labName), "Back: the lab's brush is in slot 1: " + labName);
    // Reset to Default on the Tools page (2026-11-03), as the picker's: Wax crayon changed in Brush Lab, then put back.
    await hold(p, await centre(p, "#column > button", 2));
    await tap(p, await byText(p, ".pop .cell", "Wax crayon"));
    await hold(p, await centre(p, "#column > button", 2));
    ok(await p.evaluate(() => !/Reset to Default/.test(document.querySelector(".pop").textContent)), "An unchanged Basic tool: no Reset to Default in the picker");
    await tap(p, await byText(p, ".pop .mrow", "Full Library"));
    ok(await p.evaluate(() => !/Reset to Default/.test(document.querySelector("#screen .named").textContent)), "nor on the Tools page");
    await tap(p, await byText(p, "#screen .bar .lb", "Back"));
    await hold(p, await centre(p, "#column > button", 2));
    await tap(p, await byText(p, ".pop .mrow", "Edit Brush"));
    await sleep(500);
    await slideTip(p);
    await tap(p, await byText(p, "#screen .bar .lb", "Back"));
    await hold(p, await centre(p, "#column > button", 2));
    ok(await p.evaluate(() => /Reset to Default/.test(document.querySelector(".pop").textContent)), "Wax crayon changed in Brush Lab: the picker offers Reset to Default");
    await tap(p, await byText(p, ".pop .mrow", "Full Library"));
    const resetAt = await byText(p, "#screen .named .lb", "Reset to Default");
    ok(resetAt && await p.evaluate(() => { const a = document.querySelector("#screen .named .lb.quiet").getBoundingClientRect(), b = document.querySelector("#screen .named .lb.boxed").getBoundingClientRect(), n = document.querySelector("#screen .named").getBoundingClientRect();
      return a.right <= b.left && b.right <= n.right + 1 && a.left >= n.left; }), "and the Tools page shows it before Edit in Brush Lab, both whole in the row");
    await shot(p, "tools-reset-ipad");
    const editedName = await p.evaluate(() => document.querySelector("#screen .named b").textContent);
    await tap(p, resetAt);
    ok(await p.evaluate(() => /^Wax crayon/.test(document.querySelector("#screen .named b").textContent) && !/Reset to Default/.test(document.querySelector("#screen .named").textContent)),
      "A tap: Wax crayon back, the reset gone");
    // The changed one is in Recent (put there when Brush Lab's Back put it in the slot; a pick never keeps it twice).
    await tap(p, await byText(p, "#screen .chip", "Recent"));
    ok(await p.evaluate(n => [...document.querySelectorAll("#screen .grid .tname")].some(e => e.textContent === n), editedName), "The changed one is in Recent: " + editedName);
    await tap(p, await byText(p, "#screen .bar .lb", "Back"));
    ok(await p.evaluate(() => guest.tools.slots[2].name === "Wax crayon"), "Back: Wax crayon in slot 3");
    // Landscape and a desktop window: the Tools page and the lab (scrolling where LabLayout needs the height).
    for (const [size, name] of [[IPAD_WIDE, "ipad-landscape"], [DESKTOP, "desktop"]]) {
      const q = await guest(size);
      await hold(q, await centre(q, "#column > button", 2));
      await tap(q, await byText(q, ".pop .mrow", "Full Library"));
      ok(await q.evaluate(() => guest.shown === "tools"), "The Tools page opens, " + name);
      await shot(q, "tools-" + name);
      await tap(q, await byText(q, "#screen .bar .lb", "Back"));
      await hold(q, await centre(q, "#column > button", 2));
      await tap(q, await byText(q, ".pop .mrow", "Edit Brush"));
      await sleep(500);
      ok(await q.evaluate(() => guest.shown === "lab" && document.querySelectorAll("#screen .module").length === 6), "Brush Lab opens, " + name);
      await shot(q, "lab-" + name);
      await q.close();
    }
    // A phone: the picker without Brush Lab and Library; long press by touch.
    const ph = await guest(PHONE, true);
    const xy = await centre(ph, "#column > button", 1);
    const cdp = await ph.target().createCDPSession();
    await cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x: xy[0], y: xy[1], id: 1 }] });
    await sleep(650);
    await cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
    await sleep(200);
    const phone = await ph.evaluate(() => { const pop = document.querySelector(".pop"); return pop && { cells: pop.querySelectorAll(".cell").length, words: pop.textContent }; });
    ok(phone && phone.cells >= 30 && !/Edit Brush|Library/.test(phone.words), "A phone's long press (a finger) opens the picker, without Edit Brush, Full Library or Hold Opens Full Library");
    await shot(ph, "picker-phone");
    ok(errs.length === 0, "No errors: " + errs.slice(0, 5).join(" | "));
  } catch (e) { fail++; console.log("FAIL " + (e.stack || e) + "\n" + errs.join("\n")); }
  finally { await b.close(); relay.kill(); }
  console.log(fail ? `${fail} of ${n} failed` : `all ${n} passed`);
  process.exit(fail ? 1 : 0);
})();
