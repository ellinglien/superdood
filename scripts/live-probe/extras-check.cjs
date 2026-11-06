// Brush Lab's Open, its picker box and Grey Shift's set; the column's Show Kinds, sand timer and wet indicator (extras.js,
// the browser app's M2 and M4) without the Manta: a relay of its own on another port, headless Chrome (puppeteer-core
// from ~/node_modules) as an iPad in portrait. Checks: the sand timer's menu sets a countdown in its row, Off its icon
// again, Time's up at zero; Show Kinds' popup steps Pen to Hidden, the screen leaves the pen's ink out while the page
// keeps it, a pen stroke is refused with its note, Full shows it again, the host's pen marks hidden too (its kind sent
// with them) and its marks of no kind kept; the wet indicator goes dry → moving after a
// wash and a tap dries it; Brush Lab's Open sheet (a bad code says so, this brush's code opens, Cancel), the picker box
// lying across sets the slot's size and grey, Grey Shift's set takes a grey out; no long press in Brush Lab.
//   bash web/engine/build.sh && node scripts/live-probe/extras-check.cjs [port]
"use strict";
const { spawn } = require("child_process"), path = require("path"), os = require("os"), http = require("http"), zlib = require("zlib");
const LC = require("./live-crypt.cjs"), Live = require("./live.js");
const puppeteer = require(path.join(os.homedir(), "node_modules", "puppeteer-core"));
const CHROME = process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const args = process.argv.slice(2), PORT = +(args.find(a => /^\d+$/.test(a)) || 8773);
const sleep = ms => new Promise(r => setTimeout(r, ms));
let n = 0, fail = 0;
const post = body => new Promise((res, rej) => { const q = http.request(`http://127.0.0.1:${PORT}/send?from=${LC.hostId("")}`, { method: "POST" }, a => { a.resume(); a.on("end", res); }); q.on("error", rej); q.end(LC.seal("", body)); });  // as the Manta's
const ok = (c, what) => { n++; if (!c) fail++; console.log((c ? "ok   " : "FAIL ") + what); };

(async () => {
  const relay = spawn("python3", [path.join(__dirname, "relay.py"), String(PORT)], { stdio: ["ignore", "ignore", "inherit"] });
  await sleep(700);
  const b = await puppeteer.launch({ executablePath: CHROME, headless: "new", args: ["--disable-gpu"], protocolTimeout: 120000 });
  const errs = [];
  try {
    const p = await b.newPage();
    p.on("console", m => { if ((m.type() === "error" || m.type() === "warning") && !/^Failed to load resource/.test(m.text())) errs.push(m.text()); });
    p.on("response", r => { if (r.status() >= 400 && !/favicon\.ico$|tip_[a-z0-9_]+\.xml$/.test(r.url())) errs.push(r.status() + " " + r.url()); });
    p.on("pageerror", e => errs.push("pageerror: " + (e.stack || e.message).split("\n").slice(0, 3).join(" ")));
    await p.setViewport({ width: 820, height: 1180, deviceScaleFactor: 2 });
    await p.goto(`http://127.0.0.1:${PORT}/live`, { waitUntil: "load" });
    await p.waitForFunction(() => window.guest && window.guest.ready, { timeout: 30000 });
    const centre = async sel => p.evaluate(s => { const e = document.querySelector(s); if (!e) return null; const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }, sel);
    const tap = async xy => { await p.mouse.click(xy[0], xy[1]); await sleep(150); };
    const byText = async (sel, text) => p.evaluate((s, t) => { const e = [...document.querySelectorAll(s)].find(e => e.textContent.trim() === t || e.getAttribute("aria-label") === t); if (!e) return null; const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }, sel, text);
    const note = () => p.evaluate(() => document.getElementById("note").textContent);
    // A stroke across the page with the tool in use; what the page holds there (S) and what the screen shows.
    async function stroke(y) {
      const r = await p.evaluate(() => { const r = document.getElementById("view").getBoundingClientRect(); return [r.left, r.top, r.width, r.height]; });
      await p.mouse.move(r[0] + r[2] * .2, r[1] + r[3] * y); await p.mouse.down();
      for (let k = 1; k <= 20; k++) await p.mouse.move(r[0] + r[2] * (.2 + .6 * k / 20), r[1] + r[3] * (y + .03 * Math.sin(k / 3)));
      await p.mouse.up(); await sleep(300);
    }
    const inked = () => p.evaluate(() => { let page = 0, screen = 0; const c = document.getElementById("page").getContext("2d"), d = c.getImageData(0, 0, guest.W, guest.H).data;
      for (let i = 0; i < guest.S.length; i++) { if (guest.S[i] < 200) page++; if (d[4 * i] < 200) screen++; } return [page, screen]; });

    // ---- The sand timer ----
    const cells = await p.evaluate(() => ["sand", "kinds", "wet", "countdown"].map(k => { const e = document.querySelector(`#column [data-x="${k}"]`); return e ? getComputedStyle(e).visibility : "none"; }));
    ok(cells[0] === "visible" && cells[1] === "visible" && cells[2] === "visible" && cells[3] === "hidden", "The column's sand timer, Show Kinds and wet indicator; no countdown: " + cells);
    await tap(await centre('#column [data-x="sand"]'));
    const menu = await p.evaluate(() => [...document.querySelectorAll(".pop .mrow")].map(e => e.textContent));
    ok(menu[0] === "Off" && menu.includes("1 min") && menu.length >= 5, "Its menu: Off and Timed Pages' times: " + menu.join(", "));
    await tap(await byText(".pop .mrow", "1 min"));
    const running = await p.evaluate(() => [document.querySelector('#column [data-x="countdown"]').textContent, getComputedStyle(document.querySelector('#column [data-x="sand"]')).visibility]);
    ok(/^[01]:\d\d$/.test(running[0]) && running[1] === "hidden", "A pick: its countdown in the row, the icon gone: " + running);
    await tap(await centre('#column [data-x="countdown"]'));
    ok(await p.evaluate(() => document.querySelector(".pop .mrow.marked") && document.querySelector(".pop .mrow.marked").textContent === "1 min"), "A tap on the countdown: its menu, the time it was set to marked");
    await tap(await byText(".pop .mrow", "Off"));
    ok(await p.evaluate(() => getComputedStyle(document.querySelector('#column [data-x="sand"]')).visibility === "visible"), "Off: the icon again");
    // A minute on: the page's clock moved past it.
    await p.evaluate(() => { const t = performance.now(), real = performance.now.bind(performance); guest.E.T.sandSet(1, t); performance.now = () => real() + 61000; guest.X.sandLook(); performance.now = real; });
    await sleep(200);
    ok(await p.evaluate(() => /Time's up/.test(document.querySelector(".pop") ? document.querySelector(".pop").textContent : "")), "At zero: Time's up beside the row");
    await tap(await byText(".pop .mrow", "Done"));

    // ---- Show Kinds ----
    // A slot that lays Pen (its stroke refused while Pen is hidden).
    const penSlot = await p.evaluate(() => { const T = guest.E.T, id = guest.P.id; T.kindsTap(id, 1); T.kindsTap(id, 1); T.kindsTap(id, 1);
      let s = -1; for (let i = 0; i < guest.tools.slots.length && s < 0; i++) if (T.hiddenNote(id, i, guest.tools.slots[i].px, guest.tools.slots[i].dark, 0)) s = i;
      T.kindsTap(id, 1); return s; });
    ok(penSlot >= 0, "A slot laying Pen: " + penSlot);
    await p.evaluate(s => guest.choose(s), penSlot);
    await stroke(.3);
    const drawn = await inked();
    ok(drawn[0] > 50 && Math.abs(drawn[0] - drawn[1]) < drawn[0] * .05, "A pen stroke on the page and the screen: " + drawn);
    await tap(await centre('#column [data-x="kinds"]'));
    const cols = await p.evaluate(() => [...document.querySelectorAll(".pop .xkinds > div")].map(c => c.querySelector(".mrow").textContent));
    ok(cols.join() === "Pen,Paint,Dry Media", "Show Kinds' popup: Pen, Paint, Dry Media (no Template: no guides): " + cols);
    const eye = '.pop .xkinds > div:first-child .eye';
    for (let k = 0; k < 3; k++) await tap(await centre(eye));
    const hidden = await p.evaluate(e => document.querySelector(e).getAttribute("aria-label"), eye);
    ok(hidden === "Pen hidden" && /Pen hidden/.test(await note()), "Three taps: Pen hidden, said: " + hidden);
    const after = await inked();
    ok(after[0] === drawn[0] && after[1] < drawn[1] * .1, "The screen leaves the pen's ink out, the page keeps it: " + after);
    await p.mouse.click(5, 1170); await sleep(150);  // the popup closed
    await stroke(.5);
    ok((await inked())[0] === drawn[0] && /Pen is hidden, see Show Kinds/.test(await note()), "A pen stroke refused: " + await note());
    await tap(await centre('#column [data-x="kinds"]'));
    await tap(await centre(eye));
    const back = await inked();
    ok(await p.evaluate(e => document.querySelector(e).getAttribute("aria-label"), eye) === "Pen full" && back[1] > drawn[1] * .9, "Full again: the ink shows: " + back);
    await p.mouse.click(5, 1170); await sleep(150);
    // The host's marks (the owner, 2026-11-05: "visibility settings don't seem to affect anything on the browser"): a
    // pen mark sent with its kind hides with Pen, one of no kind (an old page's) stays; the page keeps both.
    await post(zlib.deflateSync(Live.encode(20, 20, 60, 10, new Uint8Array(600).fill(0), [], false, false, 1)));
    await post(zlib.deflateSync(Live.encode(20, 40, 60, 10, new Uint8Array(600).fill(0), [])));
    await sleep(1500);
    const host = () => p.evaluate(() => { const d = document.getElementById("page").getContext("2d").getImageData(20, 20, 60, 30).data, n = [0, 0, 0];
      for (let i = 0; i < 60 * 30; i++) { const y = 20 + (i / 60 | 0); if (d[4 * i] < 128) n[y < 30 ? 0 : 1]++; if (guest.S[y * guest.W + 20 + i % 60] < 128) n[2]++; } return n; });
    const hostShown = await host();
    ok(hostShown[0] === 600 && hostShown[1] === 600, "The host's pen mark and its mark of no kind shown: " + hostShown);
    await tap(await centre('#column [data-x="kinds"]'));
    for (let k = 0; k < 3; k++) await tap(await centre(eye));
    const hostHidden = await host();
    ok(hostHidden[0] === 0 && hostHidden[1] === 600 && hostHidden[2] === 1200, "Pen hidden: the host's pen mark leaves the screen, its mark of no kind stays, the page keeps both: " + hostHidden);
    await tap(await centre(eye));
    ok((await host())[0] === 600, "Pen full: the host's pen mark back");
    await p.mouse.click(5, 1170); await sleep(150);

    // ---- The wet indicator ----
    const wetSaid = () => p.evaluate(() => document.querySelector('#column [data-x="wet"] canvas').getAttribute("aria-label"));
    ok(await wetSaid() === "Dry.", "Dry: " + await wetSaid());
    // A slow wash in a slot, through the Manta's own store, then a stroke.
    const wash = await p.evaluate(() => guest.tools.slots.findIndex(s => /wash/i.test(s.name)));
    if (wash >= 0) {
      await p.evaluate(s => guest.choose(s), wash);
      await stroke(.7);
      await sleep(400);
      const moving = await wetSaid();
      ok(/Paint moving|Wet/.test(moving), "A wash: " + moving);
      await tap(await centre('#column [data-x="wet"]'));
      await p.waitForFunction(() => document.querySelector('#column [data-x="wet"] canvas').getAttribute("aria-label") === "Dry.", { timeout: 15000 }).catch(() => {});
      ok(await wetSaid() === "Dry." && !(await p.evaluate(() => guest.P.settling)), "A tap dries it: " + await wetSaid());
    } else ok(false, "No wash in the bar: " + await p.evaluate(() => guest.tools.slots.map(s => s.name).join(", ")));

    // ---- Brush Lab: Open, the picker box, Grey Shift's set ----
    await p.evaluate(() => guest.choose(0));
    await p.mouse.move(...await centre("#column > button")); await p.mouse.down(); await sleep(650); await p.mouse.up(); await sleep(150);
    await tap(await byText(".pop .mrow", "Edit Brush"));
    ok(await p.evaluate(() => guest.shown === "lab"), "Brush Lab open");
    const acts = await p.evaluate(() => [...document.querySelectorAll("#screen .actions > *")].map(e => e.textContent.trim()).filter(Boolean));
    ok(acts[0] === "New ▾" && acts[1] === "Open" && !acts.some(a => /^Back to|Save as New/.test(a)), "About This Brush's header: New ▾, Open, …, no Back to or Save as New: " + acts.join(", "));
    // The simpler lab (2026-11-03): New ▾'s styles; the words only while the brush is tapped; the readout left of the
    // picker against the edge; the title centred in the bar.
    await tap(await byText("#screen .actions > *", "New ▾"));
    const styles = await p.evaluate(() => { const f = document.querySelector("#screen .actions .flyout"); return f && [f.firstChild.textContent, f.querySelectorAll(".chip").length, f.querySelectorAll(".chip.on").length]; });
    ok(styles && styles[0] === "Start From" && styles[1] > 5 && styles[2] === 1, "New ▾ opens the styles, the brush's own marked: " + styles);
    const style = await p.evaluate(() => guest.lp.styles.findIndex(s => !s.on)), named = await p.evaluate(() => guest.lp.name);
    await tap(await p.evaluate(i => { const r = document.querySelectorAll("#screen .actions .flyout .chip")[i].getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }, style));
    ok(await p.evaluate((i, n) => !document.querySelector("#screen .flyout") && guest.lp.styles[i].on && guest.lp.name !== n, style, named), "A style picked: a new brush in it, the menu closed");
    const said = () => p.evaluate(() => getComputedStyle(document.querySelector("#screen .about .words").children[2]).visibility);
    ok(await said() === "hidden", "The brush's words hidden");
    await tap(await centre("#screen .about canvas"));
    ok(await said() === "visible", "a tap on its loop shows them");
    await tap(await centre("#screen .about .words > div"));
    ok(await said() === "hidden", "a tap on its name hides them again");
    const across = await p.evaluate(() => { const b = document.querySelector(".xbox").getBoundingClientRect(), r = document.querySelector(".xbox").previousElementSibling.getBoundingClientRect(), a = document.querySelector("#screen .about").getBoundingClientRect(); return [r.right <= b.left, Math.round(a.right - b.right)]; });
    ok(across[0] && across[1] <= 16, "The readout left of the picker box, the box against the edge: " + across);
    const mid = await p.evaluate(() => { const t = document.querySelector("#screen .bar .title").getBoundingClientRect(), bar = document.querySelector("#screen .bar").getBoundingClientRect(); return Math.round((t.left + t.right) / 2 - (bar.left + bar.right) / 2); });
    ok(Math.abs(mid) <= 2, "The title centred in the bar: " + mid);
    const box_ = () => p.evaluate(() => { const b = [...document.querySelectorAll("#screen .actions > *")].find(e => /^As New/.test(e.getAttribute("aria-label") || "")); return b ? b.getAttribute("aria-label") : null; });
    ok(await box_() === null, "A new brush has no As New box: Save adds it");
    await tap(await byText("#screen .actions > *", "Save"));
    ok(await p.evaluate(() => guest.lp.entry != null && guest.lp.saved) && await box_() === "As New, off", "Saved, it has its As New box beside Save, off");
    await tap(await byText("#screen .actions > *", "As New, off"));
    ok(await box_() === "As New, on", "a tap ticks it");
    await tap(await byText("#screen .actions > *", "Open"));
    const sheet = await p.evaluate(() => { const s = document.querySelector(".xsheet"); return s && { words: s.textContent, code: s.querySelector(".code").textContent, key: guest.lp.key, tiles: s.querySelectorAll(".tile").length, room: JSON.parse(guest.E.T.labSheet(0)) }; });
    ok(sheet && /Paste code/.test(sheet.words) && /This brush/.test(sheet.words) && /Library/.test(sheet.words) && sheet.code.length > 8 && sheet.tiles === sheet.room.columns * sheet.room.rows, "Open's sheet: Paste code, This brush (its code), the library's page of " + (sheet && sheet.tiles));
    await p.type(".xsheet input", "not-a-code");
    await tap(await byText(".xsheet .lb", "Open"));
    const bad = await p.evaluate(() => document.querySelector(".xsheet") && document.querySelector(".xsheet > div:last-child").textContent);
    ok(bad && bad.length > 3, "A code that doesn't read says so on the sheet: " + bad);
    await p.evaluate(() => { const f = document.querySelector(".xsheet input"); f.value = ""; f.dispatchEvent(new Event("input")); });
    await p.type(".xsheet input", sheet.code);
    await p.keyboard.press("Enter"); await sleep(200);
    ok(await p.evaluate(() => !document.querySelector(".xsheet")), "This brush's own code opens: the sheet closes");
    await tap(await byText("#screen .actions > *", "Open"));
    await tap(await byText(".xsheet .lb", "Cancel"));
    ok(await p.evaluate(() => !document.querySelector(".xsheet")), "Cancel closes the sheet");
    // The picker box lying across: the greys black at the left over the sizes fine at the left, broad at the right.
    const box = await p.evaluate(() => { const r = document.querySelector(".xbox").getBoundingClientRect(); return [r.left, r.top, r.width, r.height]; });
    ok(box[2] > 2 * box[3], "The picker box lies across: " + box.map(Math.round));
    const was = await p.evaluate(() => guest.lp.px);
    await tap([box[0] + box[2] * .92, box[1] + box[3] * .85]);
    await tap([box[0] + box[2] * .85, box[1] + box[3] * .05]);
    const light = await p.evaluate(() => [guest.lp.px, guest.lp.dark]);
    await tap([box[0] + box[2] * .1, box[1] + box[3] * .15]);
    const dark = await p.evaluate(() => guest.lp.dark);
    ok(light[0] > was && light[1] < .3 && dark > .8, "Its sizes broad at the right, its greys white at the right, black at the left: " + [was, light[0], light[1], dark].map(v => +v.toFixed(2)));
    // Grey Shift on (Control's From a Set), its set under it; a tap takes a grey out.
    await p.evaluate(() => { const T = guest.E.T; const m = guest.lp.modules.find(m => m.key === "HAND"); guest.lab_(T.labPart(m.part)); });
    await p.mouse.click(5, 1170); await sleep(150);
    ok(await p.evaluate(() => !document.querySelector(".pop") && !guest.lp.swaps && !!guest.lp.open), "A tap outside the swap panel closes it, the part's card stays");
    const shift = await p.evaluate(() => { const r = guest.lp.open.rows.find(r => r.name === "Grey Shift"); return r ? r.dial : -1; });
    ok(shift >= 0, "Control's open card has Grey Shift");
    await p.evaluate(d => guest.lab_(guest.E.T.labOption(d, 2)), shift);
    const greys = await p.evaluate(() => [...document.querySelectorAll(".xgreys button")].map(b => b.getAttribute("aria-label")));
    ok(greys.length >= 2 && greys.every(g => !/left out/.test(g)), "Grey Shift's set: the brush's greys, all in: " + greys.length);
    await tap(await centre(".xgreys button"));
    ok(/left out/.test(await p.evaluate(() => document.querySelector(".xgreys button").getAttribute("aria-label"))), "A tap leaves the first grey out");
    // No wet indicator on the try strip (2026-11-03: its paint dries by itself), and no long presses in Brush Lab.
    ok(await p.evaluate(() => !document.querySelector("#screen .head canvas[role=button]")), "The try strip's head has no wet indicator");
    await p.mouse.move(...await centre(".xbox")); await p.mouse.down(); await sleep(700); await p.mouse.up(); await sleep(150);
    ok(await p.evaluate(() => !document.querySelector(".pop")), "A long press on the lab's picker box opens nothing");
    ok(errs.length === 0, "No errors" + (errs.length ? ": " + errs.slice(0, 5).join(" | ") : ""));
  } finally {
    await b.close(); relay.kill();
  }
  console.log(fail ? `${fail} of ${n} failed` : `all ${n} passed`);
  process.exit(fail ? 1 : 0);
})();
