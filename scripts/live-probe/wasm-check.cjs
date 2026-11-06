// The probe page on the app's engine (web/engine, WebAssembly), without the Manta: a relay of its own on another port,
// probe.html in headless Chrome (puppeteer-core from ~/node_modules), pen strokes (pressure and tilt, through CDP) with
// several brushes, wet ones among them, then a change received as the Manta sends one, then Undo. Checks the strokes
// draw while the pen moves (the ink canvas inked mid-stroke), go out at lift, wet paint's settling goes out too, the
// change is laid, Undo goes out as set and takes the stroke back; reports ms per stroke and frame times while drawing.
//   bash web/engine/build.sh && node scripts/live-probe/wasm-check.cjs [port] [--js]   (--js: the JS copy, to compare)
"use strict";
const { spawn } = require("child_process"), path = require("path"), http = require("http"), zlib = require("zlib");
const puppeteer = require(path.join(require("os").homedir(), "node_modules", "puppeteer-core"));
const CHROME = process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const args = process.argv.slice(2), JS = args.includes("--js"), PORT = +(args.find(a => /^\d+$/.test(a)) || 8791), URL = `http://127.0.0.1:${PORT}`;
const sleep = ms => new Promise(r => setTimeout(r, ms));
let n = 0, fail = 0;
const ok = (c, what) => { n++; if (!c) fail++; console.log((c ? "ok   " : "FAIL ") + what); };
const pct = (a, q) => { const s = [...a].sort((x, y) => x - y); return s.length ? s[Math.min(s.length - 1, Math.floor(q * s.length))] : NaN; };

(async () => {
  const relay = spawn("python3", [path.join(__dirname, "relay.py"), String(PORT)], { stdio: ["ignore", "pipe", "inherit"] });
  const sent = [];  // what reaches the relay: [number, sender, bytes]
  relay.stdout.on("data", d => { for (const l of String(d).split("\n")) { const m = l.match(/^(\d+) (\S+) (\d+) bytes/); if (m) sent.push([+m[1], m[2], +m[3]]); } });
  await sleep(600);
  const b = await puppeteer.launch({ executablePath: CHROME, headless: "new", args: ["--disable-gpu"] });
  try {
    const p = await b.newPage(), errs = [];
    // A missing file shows as its own error (the browser's favicon.ico asked for aside).
    p.on("console", m => { if ((m.type() === "error" || m.type() === "warning") && !/^Failed to load resource/.test(m.text())) errs.push(m.text()); });
    p.on("response", r => { if (r.status() >= 400 && !/favicon\.ico$/.test(r.url())) errs.push(r.status() + " " + r.url()); });
    p.on("pageerror", e => errs.push("pageerror: " + e.message));
    await p.setViewport({ width: 900, height: 1200 });
    await p.goto(URL + "/" + (JS ? "?js&" : "?") + "w=960&h=1280", { waitUntil: "load" });
    await p.waitForFunction(() => window.probeEngine && document.querySelectorAll("#brushes button").length === 10, { timeout: 30000 });
    const engine = await p.evaluate(() => window.probeEngine);
    ok(engine === (JS ? "js" : "wasm"), "draws with " + (engine === "wasm" ? "the app's engine (WebAssembly)" : "the JS copy"));
    const cdp = await p.target().createCDPSession();
    const box = await p.evaluate(() => { const r = document.getElementById("ink").getBoundingClientRect(); return [r.x, r.y, r.width, r.height]; });
    const pen = (type, x, y, force, t) => cdp.send("Input.dispatchMouseEvent", { type, x: box[0] + x * box[2], y: box[1] + y * box[3], button: "left", buttons: type === "mouseReleased" ? 0 : 1,
      clickCount: 1, pointerType: "pen", force, tiltX: 25, tiltY: -15, timestamp: t });
    const inked = () => p.evaluate(() => { const c = document.getElementById("ink"), d = c.getContext("2d").getImageData(0, 0, c.width, c.height).data; let k = 0; for (let i = 3; i < d.length; i += 16) if (d[i] && d[i - 1] < 250) k++; return k; });
    // A pen stroke along a wave at row y, 90 moves 8 ms apart, pressing in and tapering; frame times while it draws.
    async function stroke(y, amp) {
      await p.evaluate(() => { window.frames_ = []; let last = performance.now(); const f = t => { window.frames_.push(t - last); last = t; if (window.framing) requestAnimationFrame(f); }; window.framing = true; requestAnimationFrame(f); window.drawMs.length = 0; });
      const t0 = Date.now() / 1000, N = 90;
      let mid = 0;
      await pen("mousePressed", .1, y, .1, t0);
      for (let i = 1; i <= N; i++) {
        const u = i / N, force = Math.max(.05, Math.min(1, .7 * Math.min(1, u / .15) * (1 - .8 * Math.max(0, (u - .75) / .25))));
        await pen("mouseMoved", .1 + .8 * u, y + amp * Math.sin(u * 2 * Math.PI), force, t0 + i * .008);
        await sleep(8);
        if (i === N / 2) mid = await inked();
      }
      const t1 = performance.now();
      await pen("mouseReleased", .9, y, 0, t0 + (N + 1) * .008);
      const r = await p.evaluate(() => { window.framing = false; return { frames: window.frames_.slice(1), draw: window.drawMs.slice(), lift: window.liftMs }; });
      return { mid, ...r };
    }
    const results = [];
    for (const [slot, y] of [[0, .12], [2, .22], [3, .3], [4, .42], [5, .56], [6, .7], [9, .84]]) {
      await p.evaluate(i => document.querySelectorAll("#brushes button")[i].click(), slot);
      const name = await p.evaluate(i => window.BRUSHES[i][0], slot), before = sent.length;
      const r = await stroke(y, .03);
      await sleep(slot >= 4 && slot <= 6 ? 3000 : 600);  // wet paint settles and sends
      const outs = sent.slice(before).filter(s => s[1] !== "-").length;
      ok(r.mid > 0, `${name}: drawn while the pen moves (${r.mid} inked mid-stroke)`);
      ok(outs >= 1, `${name}: sent at lift${outs > 1 ? `, and ${outs - 1} more as it settled` : ""}`);
      results.push({ name, ...r });
    }
    // A change as the Manta sends one: a black disc, deflated LiveChange bytes (x, y, w, h, greys; no path).
    const [cx, cy, cw, ch] = [300, 560, 120, 120], raw = Buffer.alloc(8 + cw * ch, 255);
    [cx, cy, cw, ch].forEach((v, i) => raw.writeUInt16BE(v, 2 * i));
    for (let j = 0; j < ch; j++) for (let i = 0; i < cw; i++) if (Math.hypot(i - cw / 2, j - ch / 2) < 50) raw[8 + j * cw + i] = 0;
    await new Promise((res, rej) => { const q = http.request(URL + "/send", { method: "POST" }, a => { a.resume(); a.on("end", res); }); q.on("error", rej); q.end(require("./live-crypt.cjs").seal("", zlib.deflateSync(raw))); });  // sealed as the Manta's, no key in the link
    await p.waitForFunction(() => /Received/.test(document.getElementById("status").textContent), { timeout: 10000 }).catch(() => {});
    const disc = await p.evaluate((x, y) => document.getElementById("page").getContext("2d").getImageData(x, y, 1, 1).data[0], cx + cw / 2, cy + ch / 2);
    ok(disc === 0, "a change received is laid (" + disc + " at its middle)");
    // Undo takes back the last own stroke (High lighter), sent as set.
    const beforeUndo = sent.length, y = Math.round(.84 * 1280), xs = Math.round(.5 * 960);
    const was = await p.evaluate((x, y) => { const d = document.getElementById("page").getContext("2d").getImageData(x - 40, y - 40, 80, 80).data; let k = 0; for (let i = 0; i < d.length; i += 4) if (d[i] < 250) k++; return k; }, xs, y);
    await p.click("#undo"); await sleep(800);
    const now = await p.evaluate((x, y) => { const d = document.getElementById("page").getContext("2d").getImageData(x - 40, y - 40, 80, 80).data; let k = 0; for (let i = 0; i < d.length; i += 4) if (d[i] < 250) k++; return k; }, xs, y);
    ok(was > 0 && now < was / 10 && sent.length > beforeUndo, `Undo takes the last stroke back and sends it (${was} marked px, then ${now})`);
    // A stroke after the Undo and the change: the engine has both (a smudge over them draws).
    await p.evaluate(() => document.querySelectorAll("#brushes button")[2].click());
    ok((await stroke(.5, .02)).mid > 0, "drawing on after them");
    // The Pencil curves page: one pen stroke drawn in all three panes, each its own curve.
    await p.goto(URL + "/pencil", { waitUntil: "load" });
    await p.waitForFunction(() => document.querySelectorAll("canvas").length === 3, { timeout: 30000 }); await sleep(500);
    const c0 = await p.evaluate(() => { const r = document.querySelectorAll("canvas")[1].getBoundingClientRect(); return [r.x, r.y, r.width, r.height]; });
    const at = (u, v) => ({ x: c0[0] + u * c0[2], y: c0[1] + v * c0[3] });
    const t0 = Date.now() / 1000;
    await cdp.send("Input.dispatchMouseEvent", { type: "mousePressed", ...at(.1, .5), button: "left", buttons: 1, clickCount: 1, pointerType: "pen", force: .1, timestamp: t0 });
    for (let i = 1; i <= 40; i++) { await cdp.send("Input.dispatchMouseEvent", { type: "mouseMoved", ...at(.1 + .02 * i, .5 + .1 * Math.sin(i / 6)), button: "left", buttons: 1, pointerType: "pen", force: .05 + .02 * i, timestamp: t0 + i * .008 }); await sleep(8); }
    await cdp.send("Input.dispatchMouseEvent", { type: "mouseReleased", ...at(.9, .5), button: "left", buttons: 0, clickCount: 1, pointerType: "pen", force: 0, timestamp: t0 + .4 });
    await sleep(300);
    const marks = await p.evaluate(() => [...document.querySelectorAll("canvas")].map(c => { const d = c.getContext("2d").getImageData(0, 0, c.width, c.height).data; let k = 0; for (let i = 0; i < d.length; i += 4) if (d[i] < 200) k++; return k; }));
    ok(marks.every(m => m > 100) && new Set(marks).size === 3, "the Pencil curves page draws the stroke three ways (" + marks.join(", ") + " dark px)");
    ok(!errs.length, "no console errors" + (errs.length ? ": " + errs.slice(0, 3).join(" | ") : ""));
    console.log("\nms an input event (samples drawn and shown), frame interval while drawing (ms): median, 95th, worst");
    for (const r of results) console.log(`  ${r.name.padEnd(18)} event ${pct(r.draw, .5).toFixed(1)} / ${pct(r.draw, .95).toFixed(1)} / ${Math.max(...r.draw).toFixed(1)}   ` +
      `frames ${pct(r.frames, .5).toFixed(1)} / ${pct(r.frames, .95).toFixed(1)} / ${Math.max(...r.frames).toFixed(1)}   stroke ${r.draw.reduce((a, b) => a + b, 0).toFixed(0)} ms + lift ${r.lift.toFixed(0)} ms`);
  } finally { await b.close(); relay.kill(); }
  console.log(`\n${n - fail} of ${n} passed`);
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
