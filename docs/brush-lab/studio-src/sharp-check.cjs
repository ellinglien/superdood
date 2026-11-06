// Sharp dry brushes in the browser (the app's engine, WebEngine.sharpTile/sharpArea, engine.js page.sharp): a Basic
// tool the app keeps sharp draws its close-up finer on a page of Brush Studio's (page.stroke), a wash doesn't; and the
// words: staysSharp. Needs web/engine/out (bash web/engine/build.sh).
//   node docs/brush-lab/studio-src/sharp-check.cjs
"use strict";
const fs = require("fs"), path = require("path");
require("v8").setFlagsFromString("--liftoff-only");
const ENG = path.join(__dirname, "..", "..", "..", "web", "engine");
require(path.join(ENG, "out", "mbd-engine.wasm-runtime.js"));
const presets = fs.readFileSync(path.join(__dirname, "..", "..", "..", "app", "src", "main", "java", "io", "github", "mpdairy", "monopaint", "ToolPresets.java"), "utf8");
const code = name => { const m = presets.match(new RegExp('\\{"[^"]+", "' + name + '", "([^"]+)"')); if (!m) throw new Error("no " + name); return m[1]; };
let fail = 0;
const ok = (c, what) => { if (!c) { fail++; console.log("FAIL " + what); } };
require(path.join(ENG, "engine.js")).load(fs.readFileSync(path.join(ENG, "out", "mbd-engine.wasm")), fs.readFileSync(path.join(ENG, "out", "mbd-assets.bin"))).then(E => {
  const hb = code("HB pencil"), wash = code("Soft wash");
  ok(E.staysSharp(hb) && E.staysSharp(code("Messy stick")) && E.staysSharp(code("Stipple pen")) && E.staysSharp(code("Stamp")) && E.staysSharp(code("Bristle brush")), "the HB pencil, a charcoal stick, the Stipple pen, the Stamp and the Bristle brush stay sharp");
  ok(!E.staysSharp(wash) && !E.staysSharp(code("Blend")), "a wash and a smudge don't");
  for (const [name, c, sharp, px] of [["HB pencil", hb, true, 6], ["Stamp", code("Stamp"), true, 20], ["Bristle brush", code("Bristle brush"), true, 20], ["Soft wash", wash, false, 6]]) {
    const page = E.page(128, 96), st = page.stroke(c, px, 1), pts = [];
    for (let i = 0; i < 60; i++) pts.push([20 + i * 1.5, 40 + 12 * Math.sin(i / 9), .6, 1000 + 12 * i, NaN, NaN]);
    for (let i = 0; i < pts.length; i += 3) st.add(pts.slice(i, i + 3));
    st.end();
    const g = page.sharp(4, 16, 16, 96, 64);
    let finer = 0, laid = 0;
    for (const v of g) { if (v >= 0) finer++; if (v >= 0 && v < 255) laid++; }
    ok(g.length === 96 * 4 * 64 * 4, name + ": the close-up's points, 4 a pixel");
    if (sharp) ok(laid > 2000, name + " draws finer: " + laid + " points");
    else ok(finer === 0, name + " stays its pixels: " + finer + " points");
    page.free();
  }
  console.log(fail ? fail + " failed" : "PASS: sharp dry brushes in the browser: a kept brush's close-up drawn 4 points a pixel, a wash's left as its pixels; the words say which stays sharp");
  process.exit(fail ? 1 : 0);
});
