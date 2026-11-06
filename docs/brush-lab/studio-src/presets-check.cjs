// Every Basic tool's code (ToolPresets.ROWS) as Studio reads and writes it (render.js appRead, appCode): read it, write
// it again, compare. Then each the lab's model can't hold all of (a newer tip, After, a print) edited, a Control dial,
// through the app's engine (web/engine, WebEngine.keptCode): the edit is there and the rest of the code is the app's.
//   node docs/brush-lab/studio-src/build.cjs && node docs/brush-lab/studio-src/presets-check.cjs [app.txt]
// A code must come back as written, but a Hand Hatch tip (cut from the app, Tip choice 12), which the app itself reads
// as Hatch and writes so (ModularCode.asHatch). With app.txt (the app's StudioCodeEcho over `presets-check.cjs list`,
// one line a code) each must come back exactly as the app writes it. Exits 1 on any that doesn't.
"use strict";
const fs = require("fs"), path = require("path"), E = require("./out/node-engine.cjs");
const java = path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "io", "github", "mpdairy", "monopaint", "ToolPresets.java");
const src = fs.readFileSync(java, "utf8"), rows = src.slice(src.indexOf("ROWS = {"), src.indexOf("};", src.indexOf("ROWS = {")));
const codes = [...rows.matchAll(/^\s*\{"[^"]*", "[^"]*", "([^"]+)"/gm)].map(m => m[1]);
if (codes.length < 100) throw new Error("only " + codes.length + " codes found in ToolPresets.ROWS");
if (process.argv[2] === "list") { process.stdout.write(codes.join("\n") + "\n"); process.exit(0); }
const app = process.argv[2] ? fs.readFileSync(process.argv[2], "utf8").trim().split("\n") : null;
if (app && app.length !== codes.length) throw new Error(app.length + " lines in " + process.argv[2] + ", " + codes.length + " codes");
let read = 0, same = 0, hatch = 0; const bad = [];
codes.forEach((c, i) => {
  const r = E.appRead(c), back = r && E.appCode(r.b);
  if (r) read++;
  if (back === c) same++;
  const cut = /\.M[0-9a-z]c/.test(c) && r && r.b.tip.k === "hatch";
  if (cut && back !== c) hatch++;
  if (app ? back !== app[i] : back !== c && !cut) bad.push(c + "\n   web: " + back + (app ? "\n   app: " + app[i] : ""));
});
console.log(`${codes.length} Basic tools: ${read} read, ${same} written back the same, ${hatch} Hand Hatch written as Hatch` +
  (app ? ", " + (codes.length - bad.length) + " as the app writes them" : "") + (bad.length ? "; " + bad.length + " wrong\n" + bad.join("\n") : ""));
// The app's engine, as Studio loads it (web_parity.cjs's way). Liftoff only: Node otherwise spends minutes at exit
// optimizing the module.
require("v8").setFlagsFromString("--liftoff-only");
const ENG = path.join(__dirname, "..", "..", "..", "web", "engine");
require(path.join(ENG, "out", "mbd-engine.wasm-runtime.js"));
require(path.join(ENG, "engine.js")).load(fs.readFileSync(path.join(ENG, "out", "mbd-engine.wasm")), fs.readFileSync(path.join(ENG, "out", "mbd-assets.bin"))).then(W => {
  E.useWasm(W);
  const segs = c => c.split(".")[1].slice(2).split("_"), lost = [];
  let kept = 0;
  for (const c of codes) {
    const B = E.appRead(c).b;
    // A Hand Hatch the app itself writes as Hatch (above).
    if (!B.app || /\.M[0-9a-z]c/.test(c) && B.tip.k === "hatch") continue;
    B.hand.d.gain = B.hand.d.gain > 1 ? .8 : 1.3;
    const out = E.appCode(E.normBrush(B)), a = segs(c), o = segs(out);
    if (o.length === a.length && o[5] !== a[5] && a.every((s, i) => i === 5 || s === o[i])) kept++;
    else lost.push(c + "\n   edited: " + out);
  }
  console.log(`${kept} the lab's model can't hold all of, edited, keep the rest as the app has it` + (lost.length ? "; " + lost.length + " lose it\n" + lost.join("\n") : ""));
  // Each of those on Studio's app cards (render.js appParts, appEdit): every part the lab's model can't hold (After
  // always when on), its choice changed and back, a slider moved; each code, as saved, reads back the same, the edit
  // there and the other parts as they were.
  const parts = B => { const A = E.appParts(B); for (const p of Object.values(A)) delete p.held; return A; };
  const others = (A, k) => JSON.stringify(Object.assign({}, A, { [k]: null }));
  let edited = 0; const unsaved = [];
  for (const c of codes) {
    const B = E.appRead(c).b;
    if (!B.app || /\.M[0-9a-z]c/.test(c) && B.tip.k === "hatch") continue;
    const A = E.appParts(B), held = Object.keys(A).filter(k => A[k].held && k !== "later" || k === "later" && !A.later.choices[0].on);
    let fine = held.length > 0;
    for (const k of held) {
      const was = parts(B), on = A[k].choices.find(x => x.on), other = A[k].choices.find(x => !x.on);
      const steps = [[other.k], [on.k]], slider = A[k].dials.find(d => !d.options && d.part === k);
      if (slider) steps.push([null, slider.key, slider.v === slider.hi ? slider.lo : slider.hi]);
      let b = B;
      for (const [choice, key, v] of steps) {
        b = E.appEdit(b, k, choice, key, v);
        const code = E.appCode(b), back = E.appRead(code).b, now = parts(back);
        const there = choice ? now[k].choices.find(x => x.on).k === choice : now[k].dials.find(d => d.key === key).v === v;
        if (E.appCode(back) !== code || !there || others(now, k) !== others(was, k)) { fine = false; unsaved.push(c + "\n   " + k + " " + (choice || key) + ": " + code); }
      }
    }
    if (fine) edited++;
  }
  console.log(`${edited} of them edited on Studio's app cards (${unsaved.length ? unsaved.length + " not kept\n" + unsaved.join("\n") : "each part changed, saved and read back"})`);
  process.exit(bad.length || lost.length || !kept || unsaved.length || edited !== kept ? 1 : 0);
});
