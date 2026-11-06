// Live pages' sealing in the browser (LiveCrypt in the app's engine, WebAssembly; engine.js crypt) against Node's own
// AES-256-GCM (live-crypt.cjs), on a plain-http page (no WebCrypto there): the access hash; what the browser seals Node
// opens and the other way, every length 0 to 300 and 4 MB; a bit changed or another key refused; nonces new each time.
// Reports how long a 4 MB change takes to seal and to open in the browser's WebAssembly. Headless Chrome (puppeteer-core
// from ~/node_modules), a relay of its own on another port.
//   bash web/engine/build.sh && node scripts/live-probe/crypt-check.cjs [port]
"use strict";
const { spawn } = require("child_process"), path = require("path"), LC = require("./live-crypt.cjs");
const puppeteer = require(path.join(require("os").homedir(), "node_modules", "puppeteer-core"));
const CHROME = process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const PORT = +(process.argv.slice(2).find(a => /^\d+$/.test(a)) || 8793), KEY = "k7qmw2xrh4tnpd9e";
const sleep = ms => new Promise(r => setTimeout(r, ms));
let n = 0, fail = 0;
const ok = (c, what) => { n++; if (!c) fail++; console.log((c ? "ok   " : "FAIL ") + what); };

(async () => {
  const relay = spawn("python3", [path.join(__dirname, "relay.py"), String(PORT)], { stdio: ["ignore", "ignore", "inherit"] });
  await sleep(600);
  const b = await puppeteer.launch({ executablePath: CHROME, headless: "new", args: ["--disable-gpu"] });
  try {
    const p = await b.newPage();
    // 127.0.0.1 is a secure context to Chrome; the Manta's 192.168.x.x isn't. Take WebCrypto away to stand in for it.
    await p.evaluateOnNewDocument(() => { Object.defineProperty(crypto, "subtle", { get: () => undefined }); });
    await p.goto(`http://127.0.0.1:${PORT}/live#${KEY}`, { waitUntil: "load" });
    await p.waitForFunction(() => window.guest && window.guest.ready, { timeout: 30000 });
    ok(await p.evaluate(() => crypto.subtle === undefined), "no WebCrypto on the page");
    await p.evaluate(k => { window.C = guest.E.crypt(k); }, KEY);
    ok(await p.evaluate(() => C.access) === LC.access(KEY), "the access hash, as Node's SHA-256 makes it");
    // Each length both ways.
    const lengths = [...Array(301).keys(), 4 << 20];
    const fromBrowser = await p.evaluate(ls => ls.filter(l => l < 1000).map(l => { const raw = new Uint8Array(l).map((_, i) => i * 7 + l); return Array.from(C.seal(raw)); }), lengths);
    ok(fromBrowser.every((s, l) => { try { const o = LC.open(KEY, Buffer.from(s)); return o.length === l && o.every((v, i) => v === ((i * 7 + l) & 255)); } catch (e) { return false; } }), "what the browser seals Node opens, 0 to 300 bytes");
    const fromNode = lengths.filter(l => l < 1000).map(l => Array.from(LC.seal(KEY, Buffer.from(new Uint8Array(l).map((_, i) => i * 5 + l)))));
    ok(await p.evaluate(ss => ss.every((s, l) => { try { const o = C.open(new Uint8Array(s)); return o.length === l && o.every((v, i) => v === ((i * 5 + l) & 255)); } catch (e) { return false; } }), fromNode), "what Node seals the browser opens, 0 to 300 bytes");
    const refused = await p.evaluate(() => {
      const s = C.seal(new TextEncoder().encode("a change")), other = guest.E.crypt("k7qmw2xrh4tnpd9f"), no = f => { try { f(); return false; } catch (e) { return true; } };
      const bits = [...Array(s.length * 8).keys()].every(bit => no(() => { const t = s.slice(); t[bit >> 3] ^= 1 << (bit & 7); C.open(t); }));
      return { bits, other: no(() => other.open(s)), back: new TextDecoder().decode(C.open(s)), short: no(() => C.open(s.subarray(0, 27))) };
    });
    ok(refused.bits && refused.other && refused.short && refused.back === "a change", "a changed bit, another key, cut short refused; opens again with the key");
    ok(await p.evaluate(() => new Set(Array.from({ length: 1000 }, () => Array.from(C.seal(new Uint8Array(1)).subarray(0, 12)).join())).size === 1000), "a new nonce each time (crypto.getRandomValues)");
    // 4 MB, as the relay's largest change: Node opens the browser's; the time it takes here.
    const big = await p.evaluate(() => {
      const raw = new Uint8Array(4 << 20); for (let i = 0; i < raw.length; i += 65536) crypto.getRandomValues(raw.subarray(i, i + 65536));
      let seal = Infinity, open = Infinity, s;
      for (let k = 0; k < 5; k++) { let t = performance.now(); s = C.seal(raw); seal = Math.min(seal, performance.now() - t); t = performance.now(); C.open(s); open = Math.min(open, performance.now() - t); }
      window.bigRaw = raw;
      return { seal: Math.round(seal), open: Math.round(open), sealed: btoa(Array.from(s, c => String.fromCharCode(c)).join("")) };
    });
    const opened = LC.open(KEY, Buffer.from(big.sealed, "base64")), want = Buffer.from(await p.evaluate(() => btoa(Array.from(bigRaw, c => String.fromCharCode(c)).join(""))), "base64");
    ok(opened.equals(want), `4 MB sealed in the browser, opened by Node: sealed in ${big.seal} ms, opened in ${big.open} ms (the browser's WebAssembly)`);
  } finally { await b.close(); relay.kill(); }
  console.log(`${n - fail} of ${n} passed`);
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
