// The web half of the parity harness (scripts/web_parity.sh runs both halves): the same scene as the app's
// (WebParity.java draws it), drawn by Brush Studio's engine (docs/brush-lab/engine-files.cjs) in Node with the app's paper
// scans, compared per brush at the Manta's 16 greys. Writes report.txt (worst first) and grid.png (app | web | diff).
//   node scripts/web_parity.cjs scene <scene.txt>      writes the scene
//   node scripts/web_parity.cjs compare <dir> [regex]   reads <dir>/brushes.tsv and the app's PGMs there
//   node scripts/web_parity.cjs compare-wasm <dir> [regex]   the same with the app's engine as WebAssembly (web/engine)
"use strict";
const fs = require("fs"), path = require("path");
const { LAB, engineFiles } = require("../docs/brush-lab/engine-files.cjs");
const { writePNG } = require("../docs/brush-lab/match-lab-src/testkit.cjs");

// ---------- the scene: a grey block and a black line laid first; five strokes with a hand's pressure ----------
const W = 720, H = 420;
const MARKS = [[470, 30, 700, 390, 153], [420, 30, 425, 390, 0]];
// A hand's pressure along a stroke (travel 0 to 1): ramps in over the first 30 px, wobbles, tapers over the last 45.
function hand(peak, d, total, k) {
  const ramp = Math.min(1, d / 30), taper = Math.min(1, (total - d) / 45), wob = 1 + .06 * Math.sin(d / 23 + k) + .03 * Math.sin(d / 7.3 + 2 * k);
  return Math.max(.02, Math.min(1, peak * Math.sqrt(Math.max(0, ramp)) * Math.pow(Math.max(0, taper), .7) * wob));
}
// A stroke along f(u) (u 0 to 1), a sample every 5 ms at `speed` px/ms, slower off the mark as a hand starts.
function along(f, peak, speed, k) {
  const fine = [];
  for (let i = 0; i <= 2000; i++) fine.push(f(i / 2000));
  const arc = [0];
  for (let i = 1; i < fine.length; i++) arc.push(arc[i - 1] + Math.hypot(fine[i][0] - fine[i - 1][0], fine[i][1] - fine[i - 1][1]));
  const total = arc[arc.length - 1], out = [];
  let d = 0, t = 0, j = 0;
  while (true) {
    while (j < arc.length - 1 && arc[j + 1] < d) j++;
    const u = j < arc.length - 1 ? (d - arc[j]) / Math.max(1e-9, arc[j + 1] - arc[j]) : 0, a = fine[j], b = fine[Math.min(j + 1, fine.length - 1)];
    // The pen held as a hand holds it, about 35 degrees off upright, leaning right and toward the writer, swaying a little.
    out.push([+(a[0] + (b[0] - a[0]) * u).toFixed(2), +(a[1] + (b[1] - a[1]) * u).toFixed(2), +hand(peak, d, total, k).toFixed(4), t, +(28 + 5 * Math.sin(d / 60 + k)).toFixed(2), -20]);
    if (d >= total) break;
    d = Math.min(total, d + 5 * speed * (d < 20 ? .4 : 1)); t += 5;
  }
  return out;
}
const STROKES = [
  // heavy and slow: an S across the left
  along(u => [40 + 360 * u, 140 - 70 * Math.sin(u * 2 * Math.PI)], .85, .45, 0),
  // light and fast: a long diagonal
  along(u => [50 + 340 * u, 360 - 110 * u], .28, 2, 1),
  // medium: a loop scribbled over the S
  along(u => [120 + 160 * u + 45 * Math.cos(u * 14), 150 + 55 * Math.sin(u * 14)], .55, .9, 2),
  // heavy over the grey block and the black line
  along(u => [390 + 320 * u, 300 + 12 * Math.sin(u * 5)], .9, .7, 3),
  // light over the grey block
  along(u => [400 + 300 * u, 110 - 20 * u], .32, 1.2, 4),
  // medium, at a light grey, over the black line and the grey block
  along(u => [380 + 330 * u, 205 + 25 * Math.sin(u * 4)], .6, .8, 5)
];
// Each stroke's share of the brush's darkness: the last a light grey (light over dark).
const GREYS = [1, 1, 1, 1, 1, .3];
// ---------- the plane cases: several brushes and erasers on one page, as the app's planes keep them (planes.js) ----------
// Each op: a brush [code, size, darkness, path] or an eraser ["erase", kinds (EraseKind.KINDS: 0 Everything, 1 Pen,
// 2 Paint, 4 Dry Media), its broadest px, path], drawn in turn on a blank page and left to settle.
const PEN = "steady-line.M00il00000000at_000zz0000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud",
  PRINT = "ben-day-brush.Me1zzeezz00000000ll_0i0000000000000000zz065ji000at_0_000_0_25ess00000000000000nz0.2ef",
  WASH = "grey-wash.M70i000000000po_1ls00j4000000_0_000_7_8q90000000000000000nz0.69opxg",
  PENCIL = "sketch-pencil.M40i000000000at_4at00_0_000_1jz_25ess00000000000000nz000000000530000005000c099zz.inw",
  OPAQUE = "acrylic-paint-marker.M31uomci00000000000_2zz00_0_000_1jz_00000000000000000001r0.ir1",
  WHITE = "white-gel-pen.M00i000000000at_000zz0000000000000zz1_0_000_1jz_0000000000000000000nz0.1y9",
  STUMP = "stump.M50i00000000yat_3eqlj_3p6ll00_1nl_1jz_8pthh00x9is00000000nz0.51j8ud";
// A change received from another page (a live page's LiveChange: what it showed): ["lay", x, y, w, h, greys (-1 clear)].
const received = (x, y, w, h) => { const g = []; for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) { const r = Math.hypot(i - w / 2, j - h / 2); g.push(r > w / 2 ? -1 : Math.abs(j - h / 2) < 8 ? 0 : 136); } return ["lay", x, y, w, h, g]; };
const flat = (x0, y0, x1, y1, peak = .8, speed = .9, k = 0) => along(u => [x0 + (x1 - x0) * u, y0 + (y1 - y0) * u], peak, speed, k);
const wave = (x0, x1, y, amp, peak = .8, speed = .9, k = 0) => along(u => [x0 + (x1 - x0) * u, y + amp * Math.sin(u * 4 * Math.PI)], peak, speed, k);
const CASES = [
  // A print over a print over a pen line: the line stays (it is on Pen, the prints on Paint), the lighter print leaves the darker.
  ["print over prints over a pen line", [[PEN, 4, 1, flat(40, 210, 680, 214)], [PRINT, 40, 1, wave(80, 460, 200, 40, .85)], [PRINT, 40, .45, wave(280, 660, 225, 35, .85, .9, 2)]]],
  // Erase kinds: a wash, pen lines and pencil, then an eraser of each kind down its own column.
  ["erase kinds", [[WASH, 120, .35, flat(30, 210, 690, 205, .8, .7)], [PEN, 4, 1, flat(30, 170, 690, 176)], [PEN, 4, 1, wave(30, 690, 250, 12, .8, .9, 1)],
    [PENCIL, 4, 1, wave(30, 690, 215, 30, .9, .9, 3)], ["erase", 1, 60, flat(130, 40, 135, 380, 1)], ["erase", 2, 60, flat(300, 40, 305, 380, 1)],
    ["erase", 4, 60, flat(470, 40, 475, 380, 1)], ["erase", 0, 60, flat(630, 40, 635, 380, 1)]]],
  // Mixed media: opaque paint over pen and under a later pen line, white and light grey ink and pencil over a wash.
  ["mixed media overlaps", [[WASH, 120, .4, flat(30, 140, 690, 150, .8, .7)], [PEN, 4, 1, flat(40, 40, 680, 380)], [OPAQUE, 40, .3, flat(30, 280, 690, 270)],
    [PEN, 4, 1, flat(500, 30, 510, 400, .8, .9, 4)], [WHITE, 4, 1, wave(40, 680, 150, 14, .8, .9, 5)], [PEN, 4, .3, flat(40, 115, 680, 120, .8, .9, 6)],
    [PENCIL, 4, 1, wave(40, 680, 175, 10, .9, .9, 7)]]],
  // A received change (a grey disc with a black bar, over a pen line), then a stump, a print and a pen line over it: laid as
  // the Manta lays it (planes.js planesLay), on the base of the layer last drawn on, so the stump moves it and the print leaves it.
  ["received change under later strokes", [[PEN, 4, 1, flat(40, 200, 680, 204)], received(200, 60, 300, 300), [STUMP, 30, 1, flat(150, 150, 600, 260, .9, .6)],
    [PRINT, 40, .6, wave(120, 600, 300, 25, .85, .9, 8)], [PEN, 4, 1, flat(360, 30, 380, 400, .8, .9, 9)]]]
];
function writeScene(file) {
  const lines = [`size ${W} ${H}`, ...MARKS.map(m => "mark " + m.join(" "))];
  STROKES.forEach((s, k) => { lines.push("stroke " + GREYS[k]); for (const p of s) lines.push(p.join(" ")); lines.push("end"); });
  for (const [name, ops] of CASES) {
    lines.push("case " + name);
    for (const op of ops) {
      if (op[0] === "lay") { lines.push(`lay ${op[1]} ${op[2]} ${op[3]} ${op[4]}`, op[5].join(" ")); continue; }
      lines.push(op[0] === "erase" ? `erase ${op[1]} ${op[2]}` : `brush ${op[1]} ${op[2]} ${op[0]}`); for (const p of op[3]) lines.push(p.join(" ")); lines.push("end");
    }
    lines.push("endcase");
  }
  fs.writeFileSync(file, lines.join("\n") + "\n");
}

// ---------- the web engine in Node, with the app's paper scans (BrushTexture's files) ----------
function loadEngine() {
  const code = engineFiles().map(f => fs.readFileSync(path.join(LAB, f), "utf8")).join("\n;\n");
  const M = new Function(code + "\nreturn { readAnyCode, resolve, Stroke, Eraser, modSurface, Water, Bath, TEXTURES, haloDryAll, fadeAll, PAPER_TONE, WetCanvas: typeof WetCanvas === 'function' ? WetCanvas : null, planeBind, markPlane, shownPage, planesLay };")();
  const A = path.join(__dirname, "..", "app", "src", "main", "assets", "textures");
  const FILES = { "cold press": "cold-press", laid: "laid", felt: "felt", crumpled: "crumpled", canvas: "canvas", watercolour: "watercolour", rough: "rough", unryu: "unryu", library: "nypl-old", kraft: "kraft", "old buff": "old-buff", "blue fibre": "blue-fibre", "fine speck": "fine-speck" };
  for (const [k, f] of Object.entries(FILES)) M.TEXTURES[k] = Float32Array.from(fs.readFileSync(path.join(A, f + ".bin")), v => v / 255);
  return M;
}
// The scene drawn by the web engine: marks as the page, then each stroke, three samples an event, left to settle.
function render(M, code, size, dark) {
  const read = M.readAnyCode(code);
  if (!read) throw new Error("the web engine can't read " + code);
  const B = read.b, s = M.modSurface(W, H, 7);
  // The marks on the brush's own plane (planes.js markPlane), as WebParity lays them.
  M.planeBind(s, M.markPlane(s, B, M.resolve(B, size, dark)));
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { let t = 255; for (const m of MARKS) if (x >= m[0] && x <= m[2] && y >= m[1] && y <= m[3]) t = m[4]; s.page[y * W + x] = s.base[y * W + x] = Math.min(M.PAPER_TONE, t); }
  const host = hostOf(M, s);
  STROKES.slice(0, +process.env.PARITY_STROKES || STROKES.length).forEach((path, k) => layOp(M, s, host, B, size, dark * GREYS[k], path));
  return finished(M, s);
}
function hostOf(M, s) {
  const host = { halos: true, fades: true, water: null, getWater() { return this.water ??= new M.Water(s, 11); }, bath: null, getBath() { if (!this.bath || !this.bath.wet) this.bath = new M.Bath(s); return this.bath; },
    wet: null, getWet() { return this.wet ??= new M.WetCanvas(s); } };
  if (!M.WetCanvas) delete host.getWet;
  return host;
}
// One stroke, three samples an event, then left to settle (as WebParity's).
function layOp(M, s, host, B, size, dark, path) {
  const st = new M.Stroke(s, B, size, dark, host);
  for (let i = 0; i < path.length; i += 3) st.add(path.slice(i, i + 3).map(p => [p[0], p[1], p[2], p[3], p[4], p[5]]));
  st.end();
  settle(M, s, host);
}
function settle(M, s, host) {
  if (host.water && host.water.wet) host.water.dryAll();
  if (host.bath && host.bath.wet) host.bath.print();
  if (s.halos && s.halos.length) M.haloDryAll(s);
  if (host.wet) host.wet.settle();
}
// Fading and Lemon Juice run to their end (newmods.js fadeAll), as the app's page clock takes them; the page as it shows.
function finished(M, s) {
  if (s.fades && s.fades.length) M.fadeAll(s, Date.now() + 864e5);
  return M.shownPage(s);
}
// A plane case: its ops in turn on a blank page; an eraser as the app's (EraseBothStroke.pen, untimed: its pressure's size).
function renderCase(M, ops) {
  const s = M.modSurface(W, H, 7), host = hostOf(M, s);
  for (const op of ops) {
    if (op[0] === "lay") { M.planesLay(s, op[1], op[2], op[3], op[4], op[5].map(g => g < 0 ? 255 : g), false); continue; }
    if (op[0] !== "erase") { const read = M.readAnyCode(op[0]); if (!read) throw new Error("the web engine can't read " + op[0]); layOp(M, s, host, read.b, op[1], op[2], op[3]); continue; }
    const er = new M.Eraser(s, op[2]); er.kinds = op[1];
    for (let i = 0; i < op[3].length; i += 3) er.add(op[3].slice(i, i + 3).map(p => [p[0], p[1], p[2], p[3], NaN, NaN]));
  }
  return finished(M, s);
}
// The same scene drawn by the app's engine compiled to WebAssembly (web/engine: engine.js over WebEngine.java), through the
// API Brush Studio and the live probe use: marks laid on the brush's own layer and kind (ground), each stroke three
// samples an event, then ticked until settled, as WebParity's.
async function loadWasm() {
  const E = path.join(__dirname, "..", "web", "engine");
  require(path.join(E, "out", "mbd-engine.wasm-runtime.js"));
  return require(path.join(E, "engine.js")).load(fs.readFileSync(path.join(E, "out", "mbd-engine.wasm")), fs.readFileSync(path.join(E, "out", "mbd-assets.bin")));
}
function wasmOp(p, code, size, dark, path, erase) {
  const s = erase ? p.eraser(size, code) : p.stroke(code, size, dark);
  if (!s) throw new Error("the app can't read " + code);
  for (let i = 0; i < path.length; i += 3) s.add(path.slice(i, i + 3).map(q => erase ? [q[0], q[1], q[2], NaN, NaN, NaN] : [q[0], q[1], q[2], q[3], q[4], q[5]]));
  s.end();
  if (erase) return;
  let k = 0;
  do p.tick(); while (p.settling && ++k < 100000);
}
function wasmRender(E, code, size, dark) {
  const p = E.page(W, H), g = new Uint8Array(W * H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { let t = 255; for (const m of MARKS) if (x >= m[0] && x <= m[2] && y >= m[1] && y <= m[3]) t = m[4]; g[y * W + x] = t; }
  p.ground(code, size, dark, 0, 0, W, H, g);
  STROKES.slice(0, +process.env.PARITY_STROKES || STROKES.length).forEach((path, k) => wasmOp(p, code, size, dark * GREYS[k], path));
  return wasmDone(p);
}
function wasmCase(E, ops) {
  const p = E.page(W, H);
  for (const op of ops) {
    if (op[0] === "lay") p.lay(op[1], op[2], op[3], op[4], Uint8Array.from(op[5], g => g < 0 ? 255 : g), false);
    else if (op[0] === "erase") wasmOp(p, op[1], op[2], 1, op[3], true);
    else wasmOp(p, op[0], op[1], op[2], op[3]);
  }
  return wasmDone(p);
}
function wasmDone(p) { p.dry(); const t = p.tones(0, 0, W, H); p.free(); return t; }
function readPGM(file) {
  const b = fs.readFileSync(file), head = b.toString("latin1", 0, 64).match(/^P5\s+(\d+)\s+(\d+)\s+255\s/);
  return { w: +head[1], h: +head[2], g: b.subarray(head[0].length) };
}
const q = v => Math.round(Math.max(0, Math.min(255, v)) / 17);

// wasm: the WebAssembly build instead of the JS copy (report-wasm.txt, grid-wasm.png).
async function compare(dir, filter, wasm) {
  const M = wasm ? await loadWasm() : loadEngine(), rows = fs.readFileSync(path.join(dir, "brushes.tsv"), "utf8").trim().split("\n").map(l => l.split("\t"));
  const results = [], S = 2, cw = W / S, ch = H / S, LABEL = 14;
  // The plane cases after the brushes (WebParity's case-NN.pgm), by their names.
  const jobs = rows.map(([i, name, code, size, dark]) => ({ i, name, file: String(i).padStart(3, "0") + ".pgm", draw: () => wasm ? wasmRender(M, code, +size, +dark) : render(M, code, +size, +dark), code, size, dark }))
    .concat(CASES.map(([name, ops], k) => ({ i: rows.length + k, name: "case: " + name, file: "case-" + String(k).padStart(2, "0") + ".pgm", draw: () => wasm ? wasmCase(M, ops) : renderCase(M, ops), code: "", size: 0, dark: 0 })));
  for (const { i, name, file, draw, code, size, dark } of jobs) {
    if (filter && !new RegExp(filter, "i").test(name)) continue;
    const f = path.join(dir, file);
    if (!fs.existsSync(f)) continue;
    const app = readPGM(f).g, t0 = Date.now();
    let web, err = null;
    try { web = draw(); } catch (e) { err = e.message; web = new Float32Array(W * H).fill(255); }
    let far = 0, off = 0, marked = 0;
    const diff = new Uint8Array(W * H);
    for (let k = 0; k < W * H; k++) {
      const a = q(app[k]), b = q(web[k]), d = Math.abs(a - b);
      if (a !== 15 || b !== 15) marked++;
      if (d > 1) { far++; diff[k] = 2; } else if (d === 1) { off++; diff[k] = 1; }
    }
    results.push({ i: +i, name, code, size: +size, dark: +dark, far: 100 * far / (W * H), off: 100 * off / (W * H), ofMarked: marked ? 100 * far / marked : 0, ms: Date.now() - t0, err, app, web, diff });
    process.stderr.write(".");
  }
  process.stderr.write("\n");
  results.sort((a, b) => b.far - a.far);
  const exact = results.filter(r => !r.err && r.app.every((v, k) => v === r.web[k])).length;
  const lines = [(wasm ? "The app's engine as WebAssembly" : "The JS copy") + " against the app on the JVM. " +
    "% of pixels more than 1 grey step apart (of the page; of the marked pixels), then 1 step apart; worst first", ""];
  for (const r of results) lines.push(`${r.far.toFixed(2).padStart(6)}%  ${r.ofMarked.toFixed(1).padStart(5)}% marked  ${r.off.toFixed(2).padStart(6)}% 1-step  ${r.name}${r.err ? "  ERROR " + r.err : ""}`);
  const mean = results.reduce((a, r) => a + r.far, 0) / Math.max(1, results.length);
  lines.push("", `${results.length} brushes and cases, mean ${mean.toFixed(2)}%, within 1%: ${results.filter(r => r.far <= 1).length}, pixel for pixel: ${exact}`);
  fs.writeFileSync(path.join(dir, wasm ? "report-wasm.txt" : "report.txt"), lines.join("\n") + "\n");
  console.log(lines.join("\n"));
  // The grid: per brush the app's page, the web's and where they differ (grey one step, red more), half size, worst first.
  const cols = 2, gw = cols * (3 * cw + 8), gh = Math.ceil(results.length / cols) * (ch + LABEL + 6), px = new Uint8Array(gw * gh * 3).fill(255);
  const put = (x, y, r, g, b) => { const o = (y * gw + x) * 3; px[o] = r; px[o + 1] = g; px[o + 2] = b; };
  results.forEach((r, n) => {
    const ox = (n % cols) * (3 * cw + 8), oy = Math.floor(n / cols) * (ch + LABEL + 6) + LABEL;
    label(put, ox + 2, oy - LABEL + 3, `${r.far.toFixed(2)}% ${r.name}`.slice(0, 60));
    for (let y = 0; y < ch; y++) for (let x = 0; x < cw; x++) {
      let a = 0, b = 0, dd = 0;
      for (let j = 0; j < S; j++) for (let k = 0; k < S; k++) { const i = (y * S + j) * W + x * S + k; a += q(r.app[i]) * 17; b += q(r.web[i]) * 17; dd = Math.max(dd, r.diff[i]); }
      a /= S * S; b /= S * S;
      put(ox + x, oy + y, a, a, a); put(ox + cw + x, oy + y, b, b, b);
      if (dd === 2) put(ox + 2 * cw + x, oy + y, 220, 30, 30); else if (dd === 1) put(ox + 2 * cw + x, oy + y, 170, 170, 170);
    }
    for (let y = 0; y < ch; y++) { put(ox + cw - 1, oy + y, 120, 160, 220); put(ox + 2 * cw - 1, oy + y, 120, 160, 220); }
  });
  const grid = path.join(dir, wasm ? "grid-wasm.png" : "grid.png");
  writePNG(grid, gw, gh, px, 3);
  console.log("grid: " + grid);
}
// A tiny 3x5 pixel font for the grid's labels (upper case, digits, a few marks).
const FONT = { A: "7d7d5", B: "6d6d6", C: "74447", D: "6555 6", E: "74647", F: "74644", G: "74557", H: "5575 5", I: "72227", J: "11157", K: "55655", L: "44447", M: "57755", N: "57775", O: "75557", P: "75744", Q: "7557 1", R: "75765", S: "74717", T: "72222", U: "55557", V: "55552", W: "55775", X: "55255", Y: "55222", Z: "71247", 0: "75557", 1: "26227", 2: "71747", 3: "71717", 4: "55711", 5: "74717", 6: "74757", 7: "71111", 8: "75757", 9: "75711", ".": "00002", "%": "51245", ":": "02020", "-": "00700", " ": "00000" };
function label(put, x, y, text) {
  let cx = x;
  for (const c of text.toUpperCase()) {
    const g = (FONT[c] || "00000").replace(/ /g, "");
    for (let row = 0; row < 5; row++) { const bits = parseInt(g[row] || "0", 16); for (let col = 0; col < 3; col++) if (bits & (4 >> col)) { put(cx + col * 2, y + row * 2, 0, 0, 0); put(cx + col * 2 + 1, y + row * 2, 0, 0, 0); put(cx + col * 2, y + row * 2 + 1, 0, 0, 0); put(cx + col * 2 + 1, y + row * 2 + 1, 0, 0, 0); } }
    cx += 8;
  }
}

if (require.main === module) {
  const [cmd, a, b] = process.argv.slice(2);
  if (cmd === "scene") writeScene(a);
  else if (cmd === "compare" || cmd === "compare-wasm") compare(a, b, cmd === "compare-wasm").then(() => process.exit(0), e => { console.error(e); process.exit(1); });
  else { console.error("node scripts/web_parity.cjs scene <file> | compare|compare-wasm <dir> [filter]"); process.exit(2); }
}
module.exports = { STROKES, MARKS, CASES, W, H, loadEngine, render, renderCase };
