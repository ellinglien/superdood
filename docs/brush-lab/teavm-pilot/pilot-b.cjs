// Pilot B: the app's stroke engine compiled by TeaVM (build/…/pilot-b.cjs) draws the parity harness's scene for every
// brush WebParity draws; each page is compared with the app's on the JVM (PilotBJvm, the same loop) pixel for pixel,
// and timed against the hand-kept JS copy (web_parity.cjs's render, timed the same way: strokes apart from settling).
//   node pilot-b.cjs [--wasm] <scene.txt> <jvmDir> [name regex]   jvmDir: PilotBJvm's NNN.raw, and its output as times.txt
// --wasm runs the WebAssembly (GC) build instead of the JS one.
"use strict";
const fs = require("fs"), path = require("path"), zlib = require("zlib");
const REPO = path.join(__dirname, "..", "..", "..");
const parity = require(path.join(REPO, "scripts", "web_parity.cjs"));
const { LAB, engineFiles } = require("../engine-files.cjs");
const OUT = path.join(__dirname, "build", "generated", "teavm", "js");

const args = process.argv.slice(2), wasm = args[0] === "--wasm";
const [sceneFile, jvmDir, filter] = wasm ? args.slice(1) : args;
(async () => {
let T;
if (wasm) {
  const W = path.join(__dirname, "build", "generated", "teavm", "wasm-gc");
  require(path.join(W, "pilot-b.wasm-runtime.js"));
  T = (await globalThis.TeaVM.wasmGC.load(path.join(W, "pilot-b.wasm"))).exports;
} else T = require(path.join(OUT, "pilot-b.cjs"));
const tex = path.join(REPO, "app", "src", "main", "assets", "textures");
for (const f of fs.readdirSync(tex)) T.asset("textures/" + f, new Int8Array(fs.readFileSync(path.join(tex, f))));
T.scene(fs.readFileSync(sceneFile, "utf8"));
const jvmTimes = {};
for (const l of fs.readFileSync(path.join(jvmDir, "times.txt"), "utf8").trim().split("\n")) { const [i, s, w] = l.split("\t"); jvmTimes[i] = [+s, +w]; }

// The JS copy, as web_parity.cjs draws (its render), with a clock around strokes and around the settling.
const M = parity.loadEngine();
function webTimed(code, size, dark) {
  const { W, H, MARKS, STROKES } = parity, B = M.readAnyCode(code).b, s = M.modSurface(W, H, 7);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { let t = 255; for (const m of MARKS) if (x >= m[0] && x <= m[2] && y >= m[1] && y <= m[3]) t = m[4]; s.page[y * W + x] = s.base[y * W + x] = Math.min(M.PAPER_TONE, t); }
  const host = { halos: true, fades: true, water: null, getWater() { return this.water ??= new M.Water(s, 11); }, bath: null, getBath() { if (!this.bath || !this.bath.wet) this.bath = new M.Bath(s); return this.bath; },
    wet: null, getWet() { return this.wet ??= new M.WetCanvas(s); } };
  if (!M.WetCanvas) delete host.getWet;
  const GREYS = [1, 1, 1, 1, 1, .3]; let stroke = 0, settle = 0;
  STROKES.forEach((p, k) => {
    const t0 = performance.now(), st = new M.Stroke(s, B, size, dark * GREYS[k], host);
    for (let i = 0; i < p.length; i += 3) st.add(p.slice(i, i + 3).map(q => [q[0], q[1], q[2], q[3], q[4], q[5]]));
    st.end();
    const t1 = performance.now();
    if (host.water && host.water.wet) host.water.dryAll();
    if (host.bath && host.bath.wet) host.bath.print();
    if (s.halos && s.halos.length) M.haloDryAll(s);
    if (host.wet) host.wet.settle();
    stroke += t1 - t0; settle += performance.now() - t1;
  });
  const t2 = performance.now();
  if (s.fades && s.fades.length) M.fadeAll(s, Date.now() + 864e5);
  return { page: s.page, stroke, settle: settle + performance.now() - t2 };
}
const q = v => Math.round(Math.max(0, Math.min(255, v)) / 17);

const rows = [];
let exact = 0, tot = { tv: [0, 0], js: [0, 0], jvm: [0, 0] };
for (let i = 0; i < T.brushes(); i++) {
  const name = T.label(i);
  if (filter && !new RegExp(filter, "i").test(name)) continue;
  const raw = path.join(jvmDir, String(i).padStart(3, "0") + ".raw");
  if (!fs.existsSync(raw)) continue;
  const app = fs.readFileSync(raw), page = new Uint8Array(T.render(i).buffer.slice(0));
  const tv = [T.strokeMs(), T.settleMs()];
  let off = 0, most = 0, tfar = 0;
  for (let k = 0; k < app.length; k++) { const d = Math.abs(app[k] - page[k]); if (d) { off++; most = Math.max(most, d); if (Math.abs(q(app[k]) - q(page[k])) > 1) tfar++; } }
  if (!off) exact++;
  let js, far = 0;
  try { js = webTimed(T.code(i), T.size(i), T.dark(i)); for (let k = 0; k < app.length; k++) if (Math.abs(q(app[k]) - q(js.page[k])) > 1) far++; }
  catch (e) { js = { stroke: NaN, settle: NaN }; far = NaN; }
  const jv = jvmTimes[i];
  for (const [k, v] of [["tv", tv], ["js", [js.stroke, js.settle]], ["jvm", jv]]) if (!isNaN(v[0])) { tot[k][0] += v[0]; tot[k][1] += v[1]; }
  rows.push(`${String(off).padStart(7)} px off (max ${String(most).padStart(3)}, ${(100 * tfar / app.length).toFixed(2)}% >1 grey)  JS copy ${(100 * far / app.length).toFixed(2).padStart(6)}% >1 grey  ` +
    `stroke+settle ms: TeaVM ${tv[0].toFixed(0)}+${tv[1].toFixed(0)}, JS copy ${js.stroke.toFixed(0)}+${js.settle.toFixed(0)}, JVM ${jv[0].toFixed(0)}+${jv[1].toFixed(0)}  ${name}`);
  process.stderr.write(".");
}
process.stderr.write("\n");
console.log(rows.join("\n"));
const strokes = parity.STROKES.length, n = rows.length;
const per = k => `${(tot[k][0] / n / strokes).toFixed(1)} ms a stroke, ${(tot[k][1] / n / strokes).toFixed(1)} ms settling a stroke`;
console.log(`\n${wasm ? "WasmGC" : "JS"}: ${exact} of ${n} brushes pixel for pixel the same as the app on the JVM`);
console.log(`TeaVM ${wasm ? "WasmGC" : "JS"} ${per("tv")}\nJS copy ${per("js")}\nJVM    ${per("jvm")}`);
const size = files => { const b = Buffer.concat(files.map(f => fs.readFileSync(f))); return `${(b.length / 1024).toFixed(0)} KB, ${(zlib.gzipSync(b, { level: 9 }).length / 1024).toFixed(0)} KB gzipped`; };
console.log(`\nsize: TeaVM pilot-b.cjs ${size([path.join(OUT, "pilot-b.cjs")])}; pilot-a.cjs ${size([path.join(OUT, "pilot-a.cjs")])}`);
console.log(`      JS copy engine.js+wet.js ${size(["engine.js", "wet.js"].map(f => path.join(LAB, f)))}; every engine file ${size(engineFiles().map(f => path.join(LAB, f)))}`);
if (wasm) console.log(`      WasmGC pilot-b.wasm + runtime ${size(["pilot-b.wasm", "pilot-b.wasm-runtime.js"].map(f => path.join(__dirname, "build", "generated", "teavm", "wasm-gc", f)))}`);
})().then(() => process.exit(0), e => { console.error(e); process.exit(1); });  // TeaVM's runtime keeps Node alive
