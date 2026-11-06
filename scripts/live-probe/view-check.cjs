// The browser's view and a live page's status (2026-11-01, the owner on an iPad: "the lower part is obscured.. full
// canvas view on the web?", "also add connection status?"), without the Manta: a relay of its own, headless Chrome
// (puppeteer-core from ~/node_modules), a stand-in host (its tools for a Manta page, 1920×2560, and a listener named as a
// Manta names itself). Checks: the whole page shows beside the column, letterboxed, on an iPad both ways, a phone and
// desktops, and again after a turn; two fingers pinch and pan, a pinch out and a tap on Zoom bring
// back the whole page; a desktop's window shows the page across its width, ctrl with the wheel zooms about the pointer,
// the wheel and the scroll bar scroll, and a stroke lands under the pointer at several zooms and scrolls (a wheel turned
// while drawing scrolls nothing); the status says connected and how many others, host gone, reconnecting and lost, and connected again
// once the relay is back. With --shots, docs/archive/pictures/live/view-*.png.
//   bash web/engine/build.sh && node scripts/live-probe/view-check.cjs [port] [--shots]
"use strict";
const { spawn } = require("child_process"), path = require("path"), os = require("os"), http = require("http"), zlib = require("zlib");
const puppeteer = require(path.join(os.homedir(), "node_modules", "puppeteer-core"));
const CHROME = process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const REPO = path.join(__dirname, "..", ".."), args = process.argv.slice(2), PORT = +(args.find(a => /^\d+$/.test(a)) || 8772), SHOTS = args.includes("--shots");
const SCREENS = { "ipad-portrait": [820, 1180, 2, true], "ipad-landscape": [1180, 820, 2, true], "phone": [390, 844, 3, true], "desktop": [1440, 900, 1, false], "desktop-large": [2560, 1440, 2, false] };
const sleep = ms => new Promise(r => setTimeout(r, ms));
let n = 0, fail = 0;
const ok = (c, what) => { n++; if (!c) fail++; console.log((c ? "ok   " : "FAIL ") + what); };
const shot = (p, name) => SHOTS ? p.screenshot({ path: path.join(REPO, "docs", "archive", "pictures", "live", "view-" + name + ".png") }) : null;
const post = body => new Promise((res, rej) => { const q = http.request(`http://127.0.0.1:${PORT}/send?from=${require("./live-crypt.cjs").hostId("")}`, { method: "POST" }, a => { a.resume(); a.on("end", res); }); q.on("error", rej); q.end(require("./live-crypt.cjs").seal("", body)); });  // sealed as the Manta's, no key in the link
const relay = () => spawn("python3", [path.join(__dirname, "relay.py"), String(PORT)], { stdio: ["ignore", "ignore", "inherit"] });
// The stand-in Manta's listener (LiveProbe's: here=1, its id marked as a host's), open until destroyed.
const hostListens = () => new Promise((res, rej) => http.get(`http://127.0.0.1:${PORT}/listen?here=1&from=${require("./live-crypt.cjs").hostId("")}`, a => res(a)).on("error", rej));

(async () => {
  let r = relay(), tools = null, host = null;
  await sleep(700);
  const b = await puppeteer.launch({ executablePath: CHROME, headless: "new", args: ["--disable-gpu", "--disable-background-timer-throttling", "--disable-renderer-backgrounding", "--disable-backgrounding-occluded-windows"], protocolTimeout: 120000 });
  const errs = [];
  async function open([w, h, scale, touch], where = "live") {
    const p = await b.newPage();
    p.on("console", m => { if ((m.type() === "error" || m.type() === "warning") && !/^Failed to load resource/.test(m.text())) errs.push(m.text()); });
    p.on("pageerror", e => errs.push("pageerror: " + e.message));
    await p.setViewport({ width: w, height: h, deviceScaleFactor: scale, hasTouch: touch, isMobile: touch });
    await p.goto(`http://127.0.0.1:${PORT}/${where}`, { waitUntil: "load" });
    await p.waitForFunction(() => window.guest && window.guest.ready, { timeout: 30000 });
    if (where === "live") await p.waitForFunction(() => guest.W === 1920, { timeout: 10000 });
    p.cdp = await p.target().createCDPSession();
    return p;
  }
  // The page as it shows (its canvas's box on screen), the view's, the column's and the window's.
  const boxes = p => p.evaluate(() => {
    const r = id => { const b = document.getElementById(id).getBoundingClientRect(); return [b.left, b.top, b.right, b.bottom]; };
    return { page: r("page"), view: r("view"), column: r("column"), w: innerWidth, h: innerHeight, V: guest.V, ratio: devicePixelRatio, zoom: document.getElementById("zoom").getAttribute("aria-label") };
  });
  // The whole page inside the view (half a pixel's slack), filling it one way, centred the other, beside the column.
  function whole(g) {
    const [px0, py0, px1, py1] = g.page, [vx0, vy0, vx1, vy1] = g.view, e = .6;
    const inside = px0 >= vx0 - e && py0 >= vy0 - e && px1 <= vx1 + e && py1 <= vy1 + e && vy1 <= g.h + e && vx1 <= g.w + e;
    const fills = Math.abs(px1 - px0 - (vx1 - vx0)) < 1.5 || Math.abs(py1 - py0 - (vy1 - vy0)) < 1.5;
    const centred = Math.abs((px0 - vx0) - (vx1 - px1)) < 1.5 && Math.abs((py0 - vy0) - (vy1 - py1)) < 1.5;
    return inside && fills && centred && g.column[2] <= vx0 + e;
  }
  // A desktop (2026-11-05, the owner: "zoomed to full width (for desktop), with maybe a way to scroll down"): the page across
  // the view, its top at the view's, beside the column.
  const across = g => Math.abs(g.page[0] - g.view[0]) < 1 && Math.abs(g.page[2] - g.view[2]) < 1 && Math.abs(g.page[1] - g.view[1]) < 1 && g.page[3] > g.view[3] && g.column[2] <= g.view[0] + .6;
  const say = g => `page ${g.page.map(Math.round).join(",")} in view ${g.view.map(Math.round).join(",")}, ${g.zoom}`;
  async function fingers(p, tracks, steps = 12, ms = 20) {
    await p.bringToFront();
    const [x0, y0, x1, y1] = (await boxes(p)).view, at = (u, v, id) => ({ x: x0 + u * (x1 - x0), y: y0 + v * (y1 - y0), id, radiusX: 8, radiusY: 8, force: .5 });
    const pts = k => tracks.map((tr, id) => at(...tr(k / Math.max(1, steps)), id));
    await p.cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: pts(0) });
    for (let k = 1; k <= steps; k++) { await sleep(ms); await p.cdp.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: pts(k) }); }
    await sleep(ms); await p.cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
    await sleep(100);
  }
  const note = p => p.evaluate(() => document.getElementById("note").textContent);
  const waitNote = (p, text, ms = 10000) => p.waitForFunction(t => document.getElementById("note").textContent === t, { timeout: ms, polling: 200 }, text).then(() => true, () => false);
  try {
    // The stand-in host: its tools for a page the Manta's size, then its listener.
    const first = await open(SCREENS.desktop, "live#");  // not waiting for a host's page: there is none yet
    tools = Buffer.from(await first.evaluate(() => [...guest.E.T.hostTools(1920, 2560, false)].map(v => v & 255)));
    await first.close();
    await post(zlib.deflateSync(tools));
    host = await hostListens();

    // /live: the whole page on each screen, a Manta page beside the column.
    const pages = {};
    for (const [name, s] of Object.entries(SCREENS)) {
      const p = pages[name] = await open(s);
      const g = await boxes(p);
      ok((s[3] ? whole : across)(g), `${name} ${s[0]}×${s[1]}: ${s[3] ? "the whole page" : "the width filled"} beside the column (${say(g)})`);
      await shot(p, name);
    }
    // A turn: the iPad portrait turned sideways and back fits the whole page again.
    const ip = pages["ipad-portrait"]; await ip.bringToFront();
    await ip.setViewport({ width: 1180, height: 820, deviceScaleFactor: 2, hasTouch: true, isMobile: true }); await sleep(400);
    const turned = await boxes(ip);
    await ip.setViewport({ width: 820, height: 1180, deviceScaleFactor: 2, hasTouch: true, isMobile: true }); await sleep(400);
    const back = await boxes(ip);
    ok(whole(turned) && whole(back), `a turn: the whole page sideways (${say(turned)}) and upright again`);

    // The status, on the iPad: four guests and the host there; then the others go.
    ok(await waitNote(ip, "Connected, 4 other guests"), `the status counts the others: "${await note(ip)}"`);
    for (const name of ["ipad-landscape", "desktop-large"]) { await pages[name].close(); delete pages[name]; }
    for (let i = 0; i < 2; i++) { await post(zlib.deflateSync(tools)); await sleep(300); }  // the relay notices one gone when writing to it fails
    ok(await waitNote(ip, "Connected, 2 other guests"), `and again as two leave: "${await note(ip)}"`);
    host.destroy();
    for (let i = 0; i < 2; i++) { await post(zlib.deflateSync(tools)); await sleep(300); }
    ok(await waitNote(ip, "Host gone"), `the host's listener gone: "${await note(ip)}"`);
    await shot(ip, "status-host-gone");
    r.kill(); await sleep(300);
    ok(await waitNote(ip, "Reconnecting…"), `the relay stopped: "${await note(ip)}"`);
    ok(await waitNote(ip, "Connection lost", 20000), `still away after 15 s: "${await note(ip)}"`);
    r = relay(); await sleep(700);
    await post(zlib.deflateSync(tools)); host = await hostListens();
    ok(await waitNote(ip, "Connected, 2 other guests", 15000), `the relay back: "${await note(ip)}"`);
    await shot(ip, "status-connected");

    // Two fingers on the iPad: a pinch zooms about them, a drag pans, a pinch out comes back to the whole page.
    const fit = (await boxes(ip)).V;
    await fingers(ip, [u => [.45 - .2 * u, .5], u => [.55 + .2 * u, .5]]);
    const pinched = (await boxes(ip)).V;
    await fingers(ip, [u => [.4, .4 + .2 * u], u => [.6, .4 + .2 * u]]);
    const panned = (await boxes(ip)).V;
    ok(pinched.s > fit.s * 2 && Math.abs(panned.s - pinched.s) < 1e-6 && panned.y > pinched.y + 50, `pinch: ${fit.percent}% to ${pinched.percent}%; a two-finger drag pans (y ${Math.round(pinched.y)} to ${Math.round(panned.y)})`);
    await shot(ip, "ipad-portrait-zoomed");
    await fingers(ip, [u => [.2 + .25 * u, .5], u => [.8 - .25 * u, .5]]);
    await fingers(ip, [u => [.2 + .25 * u, .5], u => [.8 - .25 * u, .5]]);
    const out = await boxes(ip);
    ok(whole(out) && out.V.s === fit.s, `a pinch out: the whole page again (${say(out)})`);
    await fingers(ip, [u => [.45 - .2 * u, .5], u => [.55 + .2 * u, .5]]);
    const before = (await boxes(ip)).V;
    await ip.tap("#zoom"); await sleep(200);
    const tapped = await boxes(ip);
    ok(before.s > fit.s && whole(tapped) && tapped.V.s === fit.s && /^Zoom \d+%$/.test(tapped.zoom), `a tap on Zoom: the whole page again ("${tapped.zoom}", no padlock)`);

    // The desktop: ctrl with the wheel zooms 2.5 times about the pointer, the wheel and the scroll bar scroll; a stroke lands
    // under the pointer at every zoom and scroll, and a wheel while drawing scrolls nothing.
    // A new tab: headless Chrome's own scrolling (not the page's code) skips a tab that has been behind others.
    for (const name of Object.keys(pages)) { await pages[name].close(); delete pages[name]; }
    const d = pages.desktop = await open(SCREENS.desktop); await d.bringToFront();
    // Back on the relay too (Connection lost covers the page until then).
    await d.waitForFunction(() => !document.getElementById("cut") || document.getElementById("cut").hidden, { timeout: 20000, polling: 200 }).catch(() => {});
    const dg = await boxes(d), cx = (dg.view[0] + dg.view[2]) / 2, cy = (dg.view[1] + dg.view[3]) / 2 - 80;
    const under = (p, x, y) => p.evaluate((x, y) => { const r = document.getElementById("page").getBoundingClientRect(); return [(x - r.left) * guest.W / r.width, (y - r.top) * guest.H / r.height]; }, x, y);
    const scrolled = p => p.evaluate(() => { const s = document.getElementById("scroll"); return [s.scrollLeft, s.scrollTop, s.scrollWidth - s.clientWidth, s.scrollHeight - s.clientHeight]; });
    const scrollTo = (p, u, v) => p.evaluate((u, v) => { const s = document.getElementById("scroll"); s.scrollLeft = u * (s.scrollWidth - s.clientWidth); s.scrollTop = v * (s.scrollHeight - s.clientHeight); }, u, v).then(() => sleep(200));
    const ctrlWheel = async (x, y, dy, times) => { await d.mouse.move(x, y); await d.keyboard.down("Control"); for (let i = 0; i < times; i++) { await d.mouse.wheel({ deltaY: dy }); await sleep(100); } await d.keyboard.up("Control"); await sleep(100); };
    // A 100 px stroke across x, y (then undone); where the page got its ink, against the page point under its ends.
    async function stroke(x, y, mid) {
      const xs = Array.from({ length: 21 }, (_, i) => x - 50 + 5 * i), from = await under(d, xs[0], y), to = await under(d, xs[20], y);
      await d.mouse.move(xs[0], y); await d.mouse.down();
      for (const [i, x1] of xs.slice(1).entries()) { await d.mouse.move(x1, y); if (mid && i === 10) await mid(); await sleep(8); }
      await d.mouse.up(); await sleep(400);
      const ink = await d.evaluate((x0, x1, y) => {
        const S = guest.S, W = guest.W; let k = 0, sy = 0, lo = 1e9, hi = -1;
        for (let py = Math.floor(y) - 60; py < y + 60; py++) for (let px = Math.floor(x0) - 60; px < x1 + 60; px++) if (S[py * W + px] < 200) { k++; sy += py; lo = Math.min(lo, px); hi = Math.max(hi, px); }
        return { k, y: sy / k, lo, hi };
      }, from[0], to[0], from[1]);
      const lands = ink.k > 30 && Math.abs(ink.y - from[1]) < 1.5 && Math.abs(ink.lo - from[0]) < 12 && Math.abs(ink.hi - to[0]) < 12;
      return { lands, said: `page y ${ink.y.toFixed(1)} for ${from[1].toFixed(1)}, x ${ink.lo} to ${ink.hi} for ${from[0].toFixed(0)} to ${to[0].toFixed(0)}` };
    }
    const aim = await under(d, cx, cy);
    await ctrlWheel(cx, cy, -50 * Math.log(2.5), 2);
    const zg = await boxes(d), still = await under(d, cx, cy);
    ok(Math.abs(zg.V.s / dg.V.s - 2.5) < .01 && Math.hypot(still[0] - aim[0], still[1] - aim[1]) < 1, `ctrl and the wheel: 2.5 times (${dg.V.percent}% to ${zg.V.percent}%), the page point under the pointer kept`);
    await sleep(1000);  // a new scroll, not the zoom's wheel turns (the browser ties a quick next turn to them)
    const s0 = await scrolled(d);
    await d.mouse.wheel({ deltaY: 120 }); await sleep(400);
    const wheeled = (await boxes(d)).V, s1 = await scrolled(d);
    ok(Math.abs(wheeled.y - (zg.V.y - 120)) < 1 && wheeled.s === zg.V.s && Math.abs(s1[1] - s0[1] - 120) < 1, `the wheel scrolls (y ${Math.round(zg.V.y)} to ${Math.round(wheeled.y)}, the scroll bar ${Math.round(s0[1])} to ${Math.round(s1[1])})`);
    await d.mouse.wheel({ deltaY: -120 }); await sleep(400);
    const at25 = await stroke(cx, cy);
    ok(at25.lands, `a stroke at 2.5 times lands under the pointer (${at25.said})`);
    // And on screen: the pixels under the pointer's path are dark.
    const png = await d.screenshot({ clip: { x: cx - 20, y: cy - 3, width: 40, height: 7 }, encoding: "binary" });
    const shownDark = await d.evaluate(async bytes => {
      const img = await createImageBitmap(new Blob([new Uint8Array(bytes)], { type: "image/png" })), c = new OffscreenCanvas(img.width, img.height), x = c.getContext("2d");
      x.drawImage(img, 0, 0); const px = x.getImageData(0, Math.floor(img.height / 2), img.width, 1).data; let k = 0;
      for (let i = 0; i < px.length; i += 4) if (px[i] < 128) k++; return k / (px.length / 4);
    }, [...png]);
    ok(shownDark > .8, `the screen under the pointer's path shows the ink (${Math.round(shownDark * 100)}% dark)`);
    await shot(d, "desktop-zoomed");
    await d.evaluate(() => guest.undo()); await sleep(200);
    // The scroll bar to the foot: the page's foot at the view's.
    await scrollTo(d, 0, 1);
    const foot = await boxes(d);
    ok(Math.abs(foot.page[3] - foot.view[3]) < 1.5, `the scroll bar to its end: the page's foot at the view's (${say(foot)})`);
    // The coordinates through the zoom and the scroll: a stroke under the pointer at each, undone after.
    const places = [
      ["2.5 times, scrolled to the foot", async () => {}],
      ["5 times, scrolled right and halfway down", async () => { await ctrlWheel(cx, cy, -100 * Math.log(2), 1); await scrollTo(d, 1, .5); }],
      ["5 times, scrolled to the top left", () => scrollTo(d, 0, 0)],
      ["the fit again (a tap on Zoom), scrolled a third down", async () => { await d.click("#zoom"); await sleep(200); await scrollTo(d, 0, 1 / 3); }],
      ["zoomed out to the whole page", () => ctrlWheel(cx, cy, 300, 4)],
    ];
    for (const [what, go] of places) {
      await go();
      const g = await boxes(d), at = await stroke(cx + 37, cy + 23);
      ok(at.lands, `${what} (${g.V.percent}%): a stroke lands under the pointer (${at.said})`);
      await d.evaluate(() => guest.undo()); await sleep(200);
    }
    const out2 = await boxes(d);
    ok(whole(out2), `zoomed out, the whole page (${say(out2)})`);
    // A wheel while drawing scrolls nothing: the fit again, a stroke, the wheel turned halfway along it.
    await d.click("#zoom"); await sleep(200);
    await sleep(1000);
    const vBefore = (await boxes(d)).V, sBefore = await scrolled(d);
    const held = await stroke(cx, cy, () => d.mouse.wheel({ deltaY: 200 }));
    const vAfter = (await boxes(d)).V, sAfter = await scrolled(d);
    ok(held.lands && vAfter.y === vBefore.y && Math.abs(sAfter[1] - sBefore[1]) < 1, `a wheel while drawing: the page stays (y ${Math.round(vBefore.y)} to ${Math.round(vAfter.y)}), the stroke under the pointer (${held.said})`);
    await sleep(1000); await d.mouse.wheel({ deltaY: 200 }); await sleep(400);
    const vFree = (await boxes(d)).V;
    ok(Math.abs(vFree.y - (vBefore.y - 200)) < 1, `the pen up, the same wheel scrolls (y ${Math.round(vBefore.y)} to ${Math.round(vFree.y)})`);
    ok(!errs.length, "no console errors" + (errs.length ? ": " + errs.slice(0, 3).join(" | ") : ""));
  } finally { if (host) host.destroy(); await b.close(); r.kill(); }
  console.log(`${n - fail} of ${n} passed`);
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
